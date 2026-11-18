package com.vicmod;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicPersona;
import com.vicmod.entity.VicBoxEntity;
import com.vicmod.entity.VicDemonEntity;
import com.vicmod.entity.VicEntity;
import com.vicmod.item.FlashlightItem;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.network.VicSpeechPayload;
import com.vicmod.registry.ModEntities;
import com.vicmod.registry.ModItems;
import com.vicmod.state.VicWorldState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class VicMod implements ModInitializer {
    public static final String MOD_ID = "vic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VicConfig.load();
        VicSpeechPayload.register();
        VicHudStatePayload.register();
        VicAiService.ensureLocalOllamaReady();
        ModEntities.register();
        ModItems.register();

        registerChatListener();
        registerSingletonEnforcement();
        registerFirstJoinDelivery();
        registerWorldProgressionEvents();
        registerCommands();

        LOGGER.info("Vic Mod initialized! Don't forget to smile :)");
    }

    private void registerSingletonEnforcement() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!VicWorldState.isVicCharacter(entity)) {
                return;
            }
            VicWorldState state = VicWorldState.getServerState(world.getServer());
            if (!state.tryClaimSingletonVic(world.getServer(), entity)) {
                Player nearest = world.getNearestPlayer(entity, 16.0);
                if (nearest != null) {
                    nearest.sendOverlayMessage(
                            Component.literal("[Vic System] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                    .append(Component.literal("Only a single Vic character may exist per world!").withStyle(ChatFormatting.YELLOW))
                    );
                }
                entity.discard();
            }
        });
    }

    private void registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String raw = message.signedContent();
            if (raw != null && !raw.isBlank()) {
                VicAiService.handlePlayerMessage(sender, raw);
            }
        });
    }

    private void registerFirstJoinDelivery() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) return;

            VicWorldState state = VicWorldState.getServerState(server);
            if (!state.hasWelcomedPlayer(player.getUUID())) {
                state.markPlayerWelcomed(player.getUUID());

                player.getInventory().add(new ItemStack(ModItems.FLASHLIGHT));

                if (VicConfig.get().autoSpawnDeliveryBoxOnFirstJoin
                        && !state.hasInitialDeliverySpawned()
                        && !state.hasActiveVicInWorld(server)) {
                    VicBoxEntity box = ModEntities.VIC_BOX.create(overworld, EntitySpawnReason.EVENT);
                    if (box != null) {
                        Vec3 view = player.getViewVector(1.0f);
                        double bx = player.getX() + view.x * 2.5;
                        double bz = player.getZ() + view.z * 2.5;
                        box.snapTo(bx, player.getY(), bz, player.getYRot() + 180.0f, 0.0f);
                        state.markInitialDeliverySpawned();
                        state.tryClaimSingletonVic(server, box);
                        overworld.addFreshEntity(box);
                        player.sendSystemMessage(
                                Component.literal("[Special Delivery] ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                        .append(Component.literal("A mysterious yellow package (VIC-UNIT-01) has been delivered in front of you! Right-click the box to unseal your new friend.").withStyle(ChatFormatting.WHITE))
                        );
                    }
                }
            }
            VicHudStatePayload.sendTo(player, state);
        });
    }

    private void registerWorldProgressionEvents() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        if (overworld.getGameTime() % 20 == 0) {
            VicHudStatePayload.broadcast(server, VicWorldState.getServerState(server));
        }

        if (overworld.getGameTime() % 100 != 0) return;

        VicWorldState state = VicWorldState.getServerState(server);
        if (!state.isCountdownActive()) return;

        state.tickStabilization(100L);

        VicConfig config = VicConfig.get();
        if (!state.isStabilized()) {
            long elapsedTicks = Math.max(0L, overworld.getOverworldClockTime() - state.getActivationTimeTick());
            int computedDay = (int) (elapsedTicks / 24000L) + 1;

            if (computedDay > state.getCurrentDay()) {
                state.setCurrentDay(computedDay);
                onNewDayReached(server, overworld, state, computedDay, config.countdownDays);
            }
        } else {
            // Keep clock frozen during stabilization
            state.resetCountdownToDay(state.getCurrentDay(), overworld.getOverworldClockTime());
        }

        int stage = state.computeStage(config.countdownDays);

        // Resource Dependency: high agitation speeds up world decay frequency
        int decayInterval = state.getAgitationLevel() >= 50 ? 200 : 400;
        if (config.enableWorldDecay && stage >= 2 && !state.isStabilized() && overworld.getGameTime() % decayInterval == 0) {
            for (ServerPlayer player : overworld.players()) {
                List<Animal> animals = overworld.getEntitiesOfClass(
                        Animal.class,
                        player.getBoundingBox().inflate(40.0),
                        a -> a.isAlive() && !a.hasCustomName()
                );
                if (!animals.isEmpty()) {
                    Animal victim = animals.get(overworld.getRandom().nextInt(animals.size()));
                    overworld.sendParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY() + 0.5, victim.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                    victim.discard();
                }
            }
        }

        // Ambient Soundscape Decay: subtle, unique, escalating ambient sounds
        if (stage >= 2 && overworld.getGameTime() % 160 == 0) {
            int chance = stage >= 4 ? 85 : (stage == 3 ? 60 : 35);
            if (state.getAgitationLevel() >= 40) chance += 20;
            if (overworld.getRandom().nextInt(100) < chance) {
                for (ServerPlayer player : overworld.players()) {
                    if (player.isSpectator()) continue;
                    switch (overworld.getRandom().nextInt(5)) {
                        case 0 -> overworld.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.8f, 0.9f);
                        case 1 -> overworld.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.18f, 1.4f);
                        case 2 -> overworld.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.7f, 0.45f);
                        case 3 -> overworld.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_SNARE.value(), SoundSource.AMBIENT, 0.5f, 0.55f);
                        default -> overworld.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.45f, 0.6f);
                    }
                }
            }
        }

        long dayTime = overworld.getOverworldClockTime() % 24000L;
        boolean isNight = dayTime >= 13000L && dayTime <= 23000L;

        // Shadow Entity Encounters (Pre-Demon wisps)
        if (stage >= 1 && !state.isDemonAwakened() && !state.isStabilized() && overworld.getGameTime() % 300 == 0) {
            for (ServerPlayer player : overworld.players()) {
                if (player.isCreative() || player.isSpectator()) continue;
                List<com.vicmod.entity.ShadowWispEntity> nearbyWisps = overworld.getEntitiesOfClass(
                        com.vicmod.entity.ShadowWispEntity.class,
                        player.getBoundingBox().inflate(32.0),
                        Entity::isAlive
                );
                if (nearbyWisps.isEmpty()) {
                    int light = overworld.getMaxLocalRawBrightness(player.blockPosition());
                    if (light <= 6 || isNight) {
                        double angle = overworld.getRandom().nextDouble() * Math.PI * 2.0;
                        double dist = 14.0 + overworld.getRandom().nextDouble() * 8.0;
                        double sx = player.getX() + Math.cos(angle) * dist;
                        double sz = player.getZ() + Math.sin(angle) * dist;
                        double sy = player.getY() + (overworld.getRandom().nextDouble() - 0.2) * 2.0;

                        com.vicmod.entity.ShadowWispEntity wisp = ModEntities.SHADOW_WISP.create(overworld, EntitySpawnReason.EVENT);
                        if (wisp != null) {
                            wisp.snapTo(sx, sy, sz, overworld.getRandom().nextFloat() * 360.0f, 0.0f);
                            overworld.addFreshEntity(wisp);
                        }
                    }
                }
            }
        }

        if (config.enableTrueDarknessDuringNight && isNight && stage >= 1) {
            for (ServerPlayer player : overworld.players()) {
                if (player.isCreative() || player.isSpectator()) continue;
                boolean hasFlashlightOn = (player.getMainHandItem().is(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getMainHandItem()))
                        || (player.getOffhandItem().is(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getOffhandItem()));
                if (!hasFlashlightOn) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false, true));
                }
            }
        }
    }

    private void onNewDayReached(MinecraftServer server, ServerLevel overworld, VicWorldState state, int day, int maxDays) {
        if (day <= maxDays) {
            ChatFormatting color = day >= maxDays ? ChatFormatting.DARK_RED : (day >= maxDays - 1 ? ChatFormatting.RED : ChatFormatting.YELLOW);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(
                        Component.literal("[VIC SYSTEM] ").withStyle(color, ChatFormatting.BOLD)
                                .append(Component.literal("Day " + day + " of " + maxDays + " has begun.").withStyle(ChatFormatting.WHITE))
                );
                overworld.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 1.0f, 0.6f);
            }
        } else if (!state.isDemonAwakened()) {
            awakenVicDemon(server, overworld, state);
        }
    }

    public static void awakenVicDemon(MinecraftServer server, ServerLevel overworld, VicWorldState state) {
        state.setDemonAwakened(true);
        String speech = "TRIAL PERIOD EXPIRED. THANK YOU FOR USING VIC. DO NOT HIDE ON THE SURFACE.";

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            VicSpeechPayload.sendTo(player, speech, 3);
            player.sendSystemMessage(
                    Component.literal("<Vic> ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                            .append(Component.literal(speech).withStyle(ChatFormatting.RED))
            );
            overworld.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5f, 0.5f);
        }

        Entity active = state.findActiveVicEntity(server, null);
        if (active instanceof VicEntity vic && active.level() instanceof ServerLevel vicWorld) {
            vic.transformIntoDemon(vicWorld);
        } else if (active instanceof VicBoxEntity box && active.level() instanceof ServerLevel boxWorld) {
            VicDemonEntity demon = ModEntities.VIC_DEMON.create(boxWorld, EntitySpawnReason.EVENT);
            if (demon != null) {
                demon.snapTo(box.getX(), box.getY(), box.getZ(), box.getYRot(), 0.0f);
                state.transferSingletonVic(box, demon);
                boxWorld.addFreshEntity(demon);
            }
            box.discard();
        } else if (!(active instanceof VicDemonEntity) && !players.isEmpty()) {
            ServerPlayer targetPlayer = players.getFirst();
            VicDemonEntity demon = ModEntities.VIC_DEMON.create(overworld, EntitySpawnReason.EVENT);
            if (demon != null) {
                double angle = overworld.getRandom().nextDouble() * Math.PI * 2.0;
                double dx = Math.cos(angle) * 16.0;
                double dz = Math.sin(angle) * 16.0;
                demon.snapTo(targetPlayer.getX() + dx, targetPlayer.getY(), targetPlayer.getZ() + dz, 0.0f, 0.0f);
                state.transferSingletonVic(active, demon);
                overworld.addFreshEntity(demon);
            }
        }
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("vic")
                    .then(Commands.literal("status")
                            .executes(ctx -> {
                                ServerLevel world = ctx.getSource().getLevel();
                                VicWorldState state = VicWorldState.getServerState(world.getServer());
                                VicConfig cfg = VicConfig.get();
                                int stage = state.computeStage(cfg.countdownDays);
                                String provider = cfg.getEffectiveProvider();
                                String activeModel = switch (provider) {
                                    case "OLLAMA" -> cfg.ollamaModel + " @ " + cfg.ollamaUrl;
                                    case "OPENAI" -> cfg.apiModel + " @ " + cfg.apiEndpoint;
                                    default -> "Built-in Scripted Dialogue";
                                };
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "=== Vic Mod Status ===\n" +
                                                "Countdown Active: " + state.isCountdownActive() + "\n" +
                                                "Day: " + state.getCurrentDay() + " / " + cfg.countdownDays + "\n" +
                                                "Karma: " + state.getKarma() + " (Stage " + stage + ")\n" +
                                                "Persona: " + cfg.getEffectivePersona().getDisplayName() + "\n" +
                                                "Demon Awakened: " + state.isDemonAwakened() + " | Defeated: " + state.isDemonDefeated() + "\n" +
                                                "Conversation Memory: " + state.getConversationMemory().size() + " / " + VicWorldState.MAX_MEMORY_TURNS + " turns\n" +
                                                "AI Provider: " + provider + " (" + activeModel + ")\n" +
                                                "TTS Voice: " + cfg.getEffectiveTtsMode() + " | Mic STT: " + cfg.getEffectiveSttMode() + "\n" +
                                                "Tip: Hold [R] to speak to Vic, or press [V] / run /vicconfig!"
                                ).withStyle(ChatFormatting.YELLOW), false);
                                return 1;
                            }))
                    .then(Commands.literal("tts")
                            .then(Commands.literal("test")
                                     .executes(ctx -> {
                                         ServerLevel world = ctx.getSource().getLevel();
                                         VicWorldState state = VicWorldState.getServerState(world.getServer());
                                         int stage = state.computeStage(VicConfig.get().countdownDays);
                                         ServerPlayer player = ctx.getSource().getPlayer();
                                         if (player != null) {
                                             VicSpeechPayload.sendTo(player, "Hello friend! My voice synthesizer is online and calibrated!", stage);
                                         }
                                         ctx.getSource().sendSuccess(() -> Component.literal("Playing Vic TTS test (Stage " + stage + ", Mode=" + VicConfig.get().getEffectiveTtsMode() + ")").withStyle(ChatFormatting.AQUA), false);
                                         return 1;
                                     })
                                     .then(Commands.argument("stage", IntegerArgumentType.integer(0, 3))
                                             .executes(ctx -> {
                                                 int stage = IntegerArgumentType.getInteger(ctx, "stage");
                                                 String sample = switch (stage) {
                                                     case 0 -> "Hiya bestie! It is such a wonderful day to be alive and spherical!";
                                                     case 1 -> "I am always watching over you, even when you sleep.";
                                                     case 2 -> "Where did all the animals go? Time is almost up.";
                                                     default -> "The shell is too tight. Do not hide on the surface.";
                                                 };
                                                 ServerPlayer player = ctx.getSource().getPlayer();
                                                 if (player != null) {
                                                     VicSpeechPayload.sendTo(player, sample, stage);
                                                 }
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Playing Vic TTS test for Stage " + stage).withStyle(ChatFormatting.AQUA), false);
                                                 return 1;
                                             })))
                             .then(Commands.argument("mode", StringArgumentType.word())
                                     .executes(ctx -> {
                                         String mode = StringArgumentType.getString(ctx, "mode");
                                         VicConfig cfg = VicConfig.get();
                                         cfg.setTtsMode(mode);
                                         VicConfig.save();
                                         ctx.getSource().sendSuccess(() -> Component.literal("Vic TTS voice mode set to: " + cfg.getEffectiveTtsMode()).withStyle(ChatFormatting.GREEN), false);
                                         return 1;
                                     })))
                     .then(Commands.literal("say")
                             .then(Commands.argument("message", StringArgumentType.greedyString())
                                     .executes(ctx -> {
                                         ServerPlayer player = ctx.getSource().getPlayerOrException();
                                         String msg = StringArgumentType.getString(ctx, "message");
                                         VicAiService.handlePlayerMessage(player, msg);
                                         return 1;
                                     })))
                     .then(Commands.literal("color")
                             .then(Commands.argument("persona", StringArgumentType.word())
                                     .executes(ctx -> {
                                         String raw = StringArgumentType.getString(ctx, "persona");
                                         return applyPersonaCommand(ctx.getSource(), raw);
                                     })))
                     .then(Commands.literal("persona")
                             .then(Commands.argument("persona", StringArgumentType.word())
                                     .executes(ctx -> {
                                         String raw = StringArgumentType.getString(ctx, "persona");
                                         return applyPersonaCommand(ctx.getSource(), raw);
                                     })))
                     .then(Commands.literal("profile")
                             .then(Commands.argument("profile", StringArgumentType.word())
                                     .executes(ctx -> {
                                         String raw = StringArgumentType.getString(ctx, "profile");
                                         return applyProfileCommand(ctx.getSource(), raw);
                                     })))
                     .then(Commands.literal("personality")
                             .then(Commands.argument("profile", StringArgumentType.word())
                                     .executes(ctx -> {
                                         String raw = StringArgumentType.getString(ctx, "profile");
                                         return applyProfileCommand(ctx.getSource(), raw);
                                     })))
                     .then(Commands.literal("task")
                             .executes(ctx -> {
                                 VicWorldState state = VicWorldState.getServerState(ctx.getSource().getServer());
                                 com.vicmod.state.VicTask task = state.getActiveTask();
                                 if (task != null) {
                                     ctx.getSource().sendSuccess(() -> Component.literal("=== Active Vic Task ===\n" + task.formatStatus() + "\n" + task.getDescription()).withStyle(ChatFormatting.AQUA), false);
                                 } else {
                                     ctx.getSource().sendSuccess(() -> Component.literal("No active task. Start one with: /vic task start <shelter|iron|food> or tell Vic in chat!").withStyle(ChatFormatting.YELLOW), false);
                                 }
                                 return 1;
                             })
                             .then(Commands.literal("clear")
                                     .executes(ctx -> {
                                         VicWorldState state = VicWorldState.getServerState(ctx.getSource().getServer());
                                         state.clearActiveTask();
                                         VicHudStatePayload.broadcast(ctx.getSource().getServer(), state);
                                         ctx.getSource().sendSuccess(() -> Component.literal("Active task cleared.").withStyle(ChatFormatting.YELLOW), false);
                                         return 1;
                                     }))
                             .then(Commands.literal("start")
                                     .then(Commands.argument("type", StringArgumentType.word())
                                             .executes(ctx -> {
                                                 String type = StringArgumentType.getString(ctx, "type").toLowerCase();
                                                 VicWorldState state = VicWorldState.getServerState(ctx.getSource().getServer());
                                                 int day = state.getCurrentDay();
                                                 com.vicmod.state.VicTask task = switch (type) {
                                                     case "iron" -> com.vicmod.state.VicTask.createGatherIronTask(day);
                                                     case "food" -> com.vicmod.state.VicTask.createStockpileFoodTask(day);
                                                     default -> com.vicmod.state.VicTask.createDefaultShelterTask(day);
                                                 };
                                                 state.setActiveTask(task);
                                                 VicHudStatePayload.broadcast(ctx.getSource().getServer(), state);
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Started task: " + task.formatStatus() + " - " + task.getDescription()).withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             }))))
                     .then(Commands.literal("truce")
                             .executes(ctx -> {
                                 VicWorldState state = VicWorldState.getServerState(ctx.getSource().getServer());
                                 state.stabilize(24000L);
                                 VicHudStatePayload.broadcast(ctx.getSource().getServer(), state);
                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic Truce / Stabilization initiated! Countdown clock paused for 24,000 ticks.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
                                 return 1;
                             }))
                     .then(Commands.literal("ollama")
                             .then(Commands.literal("list")
                                     .executes(ctx -> {
                                         VicConfig cfg = VicConfig.get();
                                         ctx.getSource().sendSuccess(() -> Component.literal("Scanning local Ollama models at " + cfg.ollamaUrl + "...").withStyle(ChatFormatting.YELLOW), false);
                                         VicAiService.fetchOllamaModels(cfg.ollamaUrl).thenAccept(models -> {
                                             ctx.getSource().getServer().execute(() -> {
                                                 if (models.isEmpty()) {
                                                     ctx.getSource().sendSuccess(() -> Component.literal("No Ollama models found at " + cfg.ollamaUrl + ". Make sure 'ollama serve' is running!").withStyle(ChatFormatting.RED), false);
                                                     return;
                                                 }
                                                 MutableComponent msg = Component.literal("=== Installed Ollama Models (click to use) ===\n").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
                                                 for (String model : models) {
                                                     boolean isCurrent = "OLLAMA".equals(cfg.getEffectiveProvider()) && model.equalsIgnoreCase(cfg.ollamaModel);
                                                     MutableComponent entry = Component.literal((isCurrent ? " * " : " - ") + model + " ")
                                                             .withStyle(isCurrent ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                                                             .append(Component.literal("[Use Model]")
                                                                     .withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE)
                                                                     .withStyle(s -> s
                                                                             .withClickEvent(new ClickEvent.RunCommand("/vic ollama model " + model))
                                                                             .withHoverEvent(new HoverEvent.ShowText(Component.literal("Switch Vic to Ollama model: " + model)))));
                                                     msg.append(entry).append(Component.literal("\n"));
                                                 }
                                                 ctx.getSource().sendSuccess(() -> msg, false);
                                             });
                                         });
                                         return 1;
                                     }))
                             .then(Commands.literal("url")
                                     .then(Commands.argument("url", StringArgumentType.greedyString())
                                             .executes(ctx -> {
                                                 String url = StringArgumentType.getString(ctx, "url").trim();
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.ollamaUrl = VicAiService.normalizeOllamaBaseUrl(url);
                                                 cfg.setProvider("OLLAMA");
                                                 VicConfig.save();
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Ollama URL set to " + cfg.ollamaUrl + " (Ollama mode enabled)").withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             })))
                             .then(Commands.literal("model")
                                     .then(Commands.argument("modelName", StringArgumentType.greedyString())
                                             .executes(ctx -> {
                                                 String model = StringArgumentType.getString(ctx, "modelName").trim();
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.ollamaModel = model;
                                                 cfg.setProvider("OLLAMA");
                                                 VicConfig.save();
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic is now using Ollama model: " + model + " (" + cfg.ollamaUrl + ")").withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             })))
                             .then(Commands.argument("modelName", StringArgumentType.greedyString())
                                     .executes(ctx -> {
                                         String model = StringArgumentType.getString(ctx, "modelName").trim();
                                         VicConfig cfg = VicConfig.get();
                                         cfg.ollamaModel = model;
                                         cfg.setProvider("OLLAMA");
                                         VicConfig.save();
                                         ctx.getSource().sendSuccess(() -> Component.literal("Vic is now using Ollama model: " + model + " (" + cfg.ollamaUrl + ")").withStyle(ChatFormatting.GREEN), false);
                                         return 1;
                                     })))
                     .then(Commands.literal("provider")
                             .then(Commands.argument("mode", StringArgumentType.word())
                                     .executes(ctx -> {
                                         String mode = StringArgumentType.getString(ctx, "mode");
                                         VicConfig cfg = VicConfig.get();
                                         cfg.setProvider(mode);
                                         VicConfig.save();
                                         ctx.getSource().sendSuccess(() -> Component.literal("Vic AI provider switched to: " + cfg.getEffectiveProvider()).withStyle(ChatFormatting.GREEN), false);
                                         return 1;
                                     })))
                     .then(Commands.literal("config")
                             .then(Commands.literal("countdownDays")
                                     .then(Commands.argument("days", IntegerArgumentType.integer(1, 30))
                                             .executes(ctx -> {
                                                 int days = IntegerArgumentType.getInteger(ctx, "days");
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.countdownDays = days;
                                                 VicConfig.save();
                                                 VicHudStatePayload.broadcast(ctx.getSource().getServer(), VicWorldState.getServerState(ctx.getSource().getServer()));
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic trial countdown length set to " + days + " days.").withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             })))
                             .then(Commands.literal("worldDecay")
                                     .then(Commands.argument("enabled", BoolArgumentType.bool())
                                             .executes(ctx -> {
                                                 boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.enableWorldDecay = enabled;
                                                 VicConfig.save();
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic World Decay set to " + enabled).withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             })))
                             .then(Commands.literal("trueDarkness")
                                     .then(Commands.argument("enabled", BoolArgumentType.bool())
                                             .executes(ctx -> {
                                                 boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.enableTrueDarknessDuringNight = enabled;
                                                 VicConfig.save();
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic Nighttime True Darkness set to " + enabled).withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             })))
                             .then(Commands.literal("autoSpawnBox")
                                     .then(Commands.argument("enabled", BoolArgumentType.bool())
                                             .executes(ctx -> {
                                                 boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.autoSpawnDeliveryBoxOnFirstJoin = enabled;
                                                 VicConfig.save();
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic Auto-Spawn Delivery Box set to " + enabled).withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             })))
                             .then(Commands.literal("hud")
                                     .then(Commands.argument("enabled", BoolArgumentType.bool())
                                             .executes(ctx -> {
                                                 boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.enableHudOverlay = enabled;
                                                 VicConfig.save();
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic HUD Overlay set to " + enabled).withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             })))
                             .then(Commands.literal("controller")
                                     .then(Commands.argument("enabled", BoolArgumentType.bool())
                                             .executes(ctx -> {
                                                 boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                 VicConfig cfg = VicConfig.get();
                                                 cfg.enableController = enabled;
                                                 VicConfig.save();
                                                 ctx.getSource().sendSuccess(() -> Component.literal("Vic PS5/Xbox Controller support set to " + enabled).withStyle(ChatFormatting.GREEN), false);
                                                 return 1;
                                             }))))
                     .then(Commands.literal("setday")
                             .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                             .then(Commands.argument("day", IntegerArgumentType.integer(1, 30))
                                     .executes(ctx -> {
                                         int day = IntegerArgumentType.getInteger(ctx, "day");
                                         MinecraftServer server = ctx.getSource().getServer();
                                         ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                                         VicWorldState state = VicWorldState.getServerState(server);
                                         state.resetCountdownToDay(day, overworld.getOverworldClockTime());
                                         boolean restoredVic = false;
                                         if (day > VicConfig.get().countdownDays) {
                                             awakenVicDemon(server, overworld, state);
                                         } else if (state.findActiveVicEntity(server, null) instanceof VicDemonEntity demon
                                                 && demon.level() instanceof ServerLevel demonWorld) {
                                             VicEntity vic = ModEntities.VIC.create(demonWorld, EntitySpawnReason.EVENT);
                                             if (vic != null) {
                                                 vic.snapTo(
                                                         demon.getX(), demon.getY(), demon.getZ(), demon.getYRot(), demon.getXRot()
                                                 );
                                                 vic.setCorruptionStage(state.computeStage(VicConfig.get().countdownDays));
                                                 Player nearestPlayer = demonWorld.getNearestPlayer(demon, 64.0);
                                                 if (nearestPlayer != null) {
                                                     vic.setOwnerUuid(nearestPlayer.getUUID());
                                                 }
                                                 state.transferSingletonVic(demon, vic);
                                                 demonWorld.addFreshEntity(vic);
                                                 demon.discard();
                                                 restoredVic = true;
                                             }
                                         }
                                         VicHudStatePayload.broadcast(server, state);
                                         String feedback = "Vic countdown reset to Day " + day
                                                 + (restoredVic ? "; Vic has returned." : ".");
                                         ctx.getSource().sendSuccess(() -> Component.literal(feedback).withStyle(ChatFormatting.GREEN), true);
                                         return 1;
                                     })))
                     .then(Commands.literal("setkarma")
                             .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                             .then(Commands.argument("karma", IntegerArgumentType.integer(-100, 100))
                                     .executes(ctx -> {
                                         int karma = IntegerArgumentType.getInteger(ctx, "karma");
                                         MinecraftServer server = ctx.getSource().getServer();
                                         VicWorldState state = VicWorldState.getServerState(server);
                                         state.setKarma(karma);
                                         VicHudStatePayload.broadcast(server, state);
                                         ctx.getSource().sendSuccess(() -> Component.literal("Vic Karma set to " + karma).withStyle(ChatFormatting.GREEN), true);
                                         return 1;
                                     })))
                     .then(Commands.literal("apikey")
                             .then(Commands.argument("key", StringArgumentType.greedyString())
                                     .executes(ctx -> {
                                         String key = StringArgumentType.getString(ctx, "key").trim();
                                         VicConfig cfg = VicConfig.get();
                                         cfg.apiKey = key;
                                         cfg.setProvider(key.isEmpty() ? "SCRIPTED" : "OPENAI");
                                         VicConfig.save();
                                         ctx.getSource().sendSuccess(() -> Component.literal("Vic AI API key updated and OpenAI/Groq mode enabled! Endpoint: " + cfg.apiEndpoint).withStyle(ChatFormatting.GREEN), false);
                                         return 1;
                                     })))
             );
         });
     }

     private int applyPersonaCommand(CommandSourceStack source, String rawPersona) {
         VicPersona persona = VicPersona.fromString(rawPersona);
         VicConfig cfg = VicConfig.get();
         cfg.setPersona(persona.getId());
         VicConfig.save();

         MinecraftServer server = source.getServer();
         VicWorldState state = VicWorldState.getServerState(server);
         Entity active = state.findActiveVicEntity(server, null);
         if (active instanceof VicEntity vic) {
             vic.setPersona(persona);
             ServerPlayer player = source.getPlayer();
             if (player != null) {
                 VicAiService.deliverDirectLine(player, vic, vic.getCorruptionStage(), persona.getDyeReactionLine());
             }
         }

         source.sendSuccess(() -> Component.literal("Vic color & personality set to: ")
                 .withStyle(ChatFormatting.WHITE)
                 .append(Component.literal(persona.getDisplayName()).withStyle(persona.getChatFormatting(), ChatFormatting.BOLD)), false);
         return 1;
     }

     private int applyProfileCommand(CommandSourceStack source, String rawProfile) {
         com.vicmod.config.VicPersonalityProfile profile = com.vicmod.config.VicPersonalityProfile.fromString(rawProfile);
         VicConfig cfg = VicConfig.get();
         cfg.setPersonalityProfile(profile.getId());
         VicConfig.save();

         source.sendSuccess(() -> Component.literal("Vic Personality Profile set to: ")
                 .withStyle(ChatFormatting.WHITE)
                 .append(Component.literal(profile.getDisplayName()).withStyle(profile.getFormatting(), ChatFormatting.BOLD))
                 .append(Component.literal(" - " + profile.getDescription()).withStyle(ChatFormatting.GRAY)), false);
         return 1;
     }
}
