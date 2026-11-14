package com.vicmod;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import com.vicmod.entity.VicBoxEntity;
import com.vicmod.entity.VicDemonEntity;
import com.vicmod.entity.VicEntity;
import com.vicmod.item.FlashlightItem;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
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
                PlayerEntity nearest = world.getClosestPlayer(entity, 16.0);
                if (nearest != null) {
                    nearest.sendMessage(
                            Text.literal("[Vic System] ").formatted(Formatting.RED, Formatting.BOLD)
                                    .append(Text.literal("Only a single Vic character may exist per world!").formatted(Formatting.YELLOW)),
                            true
                    );
                }
                entity.discard();
            }
        });
    }

    private void registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String raw = message.getSignedContent();
            if (raw != null && !raw.isBlank()) {
                VicAiService.handlePlayerMessage(sender, raw);
            }
        });
    }

    private void registerFirstJoinDelivery() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            if (overworld == null) return;

            VicWorldState state = VicWorldState.getServerState(server);
            if (!state.hasWelcomedPlayer(player.getUuid())) {
                state.markPlayerWelcomed(player.getUuid());

                // Give starter Flashlight
                player.getInventory().insertStack(new ItemStack(ModItems.FLASHLIGHT));

                if (VicConfig.get().autoSpawnDeliveryBoxOnFirstJoin
                        && !state.hasInitialDeliverySpawned()
                        && !state.hasActiveVicInWorld(server)) {
                    VicBoxEntity box = ModEntities.VIC_BOX.create(overworld);
                    if (box != null) {
                        double bx = player.getX() + player.getRotationVec(1.0f).x * 2.5;
                        double bz = player.getZ() + player.getRotationVec(1.0f).z * 2.5;
                        box.refreshPositionAndAngles(bx, player.getY(), bz, player.getYaw() + 180.0f, 0.0f);
                        state.markInitialDeliverySpawned();
                        state.tryClaimSingletonVic(server, box);
                        overworld.spawnEntity(box);
                        player.sendMessage(
                                Text.literal("[Special Delivery] ").formatted(Formatting.YELLOW, Formatting.BOLD)
                                        .append(Text.literal("A mysterious yellow package (VIC-UNIT-01) has been delivered in front of you! Right-click the box to unseal your new friend.").formatted(Formatting.WHITE)),
                                false
                        );
                    }
                }
            }
        });
    }

    private void registerWorldProgressionEvents() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) return;

        // Run progression checks once every 5 seconds (100 ticks)
        if (overworld.getTime() % 100 != 0) return;

        VicWorldState state = VicWorldState.getServerState(server);
        if (!state.isCountdownActive()) return;

        VicConfig config = VicConfig.get();
        long elapsedTicks = Math.max(0L, overworld.getTimeOfDay() - state.getActivationTimeTick());
        int computedDay = (int) (elapsedTicks / 24000L) + 1;

        if (computedDay > state.getCurrentDay()) {
            state.setCurrentDay(computedDay);
            onNewDayReached(server, overworld, state, computedDay, config.countdownDays);
        }

        int stage = state.computeStage(config.countdownDays);

        // World Decay: Subtle wildlife disappearance from Stage 2+
        if (config.enableWorldDecay && stage >= 2 && overworld.getTime() % 400 == 0) {
            for (ServerPlayerEntity player : overworld.getPlayers()) {
                List<AnimalEntity> animals = overworld.getEntitiesByClass(
                        AnimalEntity.class,
                        player.getBoundingBox().expand(40.0),
                        a -> a.isAlive() && !a.hasCustomName()
                );
                if (!animals.isEmpty()) {
                    AnimalEntity victim = animals.get(overworld.getRandom().nextInt(animals.size()));
                    overworld.spawnParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY() + 0.5, victim.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                    victim.discard();
                }
            }
        }

        // True Darkness at night unless holding an active Flashlight
        if (config.enableTrueDarknessDuringNight && overworld.isNight() && stage >= 1) {
            for (ServerPlayerEntity player : overworld.getPlayers()) {
                if (player.isCreative() || player.isSpectator()) continue;
                boolean hasFlashlightOn = (player.getMainHandStack().isOf(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getMainHandStack()))
                        || (player.getOffHandStack().isOf(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getOffHandStack()));
                if (!hasFlashlightOn) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 160, 0, false, false, true));
                }
            }
        }
    }

    private void onNewDayReached(MinecraftServer server, ServerWorld overworld, VicWorldState state, int day, int maxDays) {
        if (day <= maxDays) {
            Formatting color = day >= maxDays ? Formatting.DARK_RED : (day >= maxDays - 1 ? Formatting.RED : Formatting.YELLOW);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(
                        Text.literal("[VIC SYSTEM] ").formatted(color, Formatting.BOLD)
                                .append(Text.literal("Day " + day + " of " + maxDays + " has begun.").formatted(Formatting.WHITE)),
                        false
                );
                overworld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 1.0f, 0.6f);
            }
        } else if (!state.isDemonAwakened()) {
            awakenVicDemon(server, overworld, state);
        }
    }

    public static void awakenVicDemon(MinecraftServer server, ServerWorld overworld, VicWorldState state) {
        state.setDemonAwakened(true);
        String speech = "TRIAL PERIOD EXPIRED. THANK YOU FOR USING VIC. DO NOT HIDE ON THE SURFACE.";

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            VicSpeechPayload.sendTo(player, speech, 3);
            player.sendMessage(
                    Text.literal("<Vic> ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                            .append(Text.literal(speech).formatted(Formatting.RED)),
                    false
            );
            overworld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }

        Entity active = state.findActiveVicEntity(server, null);
        if (active instanceof VicEntity vic && active.getWorld() instanceof ServerWorld vicWorld) {
            vic.transformIntoDemon(vicWorld);
        } else if (active instanceof VicBoxEntity box && active.getWorld() instanceof ServerWorld boxWorld) {
            VicDemonEntity demon = ModEntities.VIC_DEMON.create(boxWorld);
            if (demon != null) {
                demon.refreshPositionAndAngles(box.getX(), box.getY(), box.getZ(), box.getYaw(), 0.0f);
                state.transferSingletonVic(box, demon);
                boxWorld.spawnEntity(demon);
            }
            box.discard();
        } else if (!(active instanceof VicDemonEntity) && !players.isEmpty()) {
            ServerPlayerEntity targetPlayer = players.getFirst();
            VicDemonEntity demon = ModEntities.VIC_DEMON.create(overworld);
            if (demon != null) {
                double angle = overworld.getRandom().nextDouble() * Math.PI * 2.0;
                double dx = Math.cos(angle) * 16.0;
                double dz = Math.sin(angle) * 16.0;
                demon.refreshPositionAndAngles(targetPlayer.getX() + dx, targetPlayer.getY(), targetPlayer.getZ() + dz, 0.0f, 0.0f);
                state.transferSingletonVic(active, demon);
                overworld.spawnEntity(demon);
            }
        }
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("vic")
                    .then(CommandManager.literal("status")
                            .executes(ctx -> {
                                ServerWorld world = ctx.getSource().getWorld();
                                VicWorldState state = VicWorldState.getServerState(world.getServer());
                                VicConfig cfg = VicConfig.get();
                                int stage = state.computeStage(cfg.countdownDays);
                                String provider = cfg.getEffectiveProvider();
                                String activeModel = switch (provider) {
                                    case "OLLAMA" -> cfg.ollamaModel + " @ " + cfg.ollamaUrl;
                                    case "OPENAI" -> cfg.apiModel + " @ " + cfg.apiEndpoint;
                                    default -> "Built-in Scripted Dialogue";
                                };
                                ctx.getSource().sendFeedback(() -> Text.literal(
                                        "=== Vic Mod Status ===\n" +
                                                "Countdown Active: " + state.isCountdownActive() + "\n" +
                                                "Day: " + state.getCurrentDay() + " / " + cfg.countdownDays + "\n" +
                                                "Karma: " + state.getKarma() + " (Stage " + stage + ")\n" +
                                                "Demon Awakened: " + state.isDemonAwakened() + "\n" +
                                                "AI Provider: " + provider + " (" + activeModel + ")\n" +
                                                "TTS Voice: " + cfg.getEffectiveTtsMode() + " | Mic STT: " + cfg.getEffectiveSttMode() + "\n" +
                                                "Tip: Hold [R] to speak to Vic, or press [V] / run /vicconfig!"
                                ).formatted(Formatting.YELLOW), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("tts")
                            .then(CommandManager.literal("test")
                                    .executes(ctx -> {
                                        ServerWorld world = ctx.getSource().getWorld();
                                        VicWorldState state = VicWorldState.getServerState(world.getServer());
                                        int stage = state.computeStage(VicConfig.get().countdownDays);
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        if (player != null) {
                                            VicSpeechPayload.sendTo(player, "Hello friend! My voice synthesizer is online and calibrated!", stage);
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal("Playing Vic TTS test (Stage " + stage + ", Mode=" + VicConfig.get().getEffectiveTtsMode() + ")").formatted(Formatting.AQUA), false);
                                        return 1;
                                    })
                                    .then(CommandManager.argument("stage", IntegerArgumentType.integer(0, 3))
                                            .executes(ctx -> {
                                                int stage = IntegerArgumentType.getInteger(ctx, "stage");
                                                String sample = switch (stage) {
                                                    case 0 -> "Hiya bestie! It is such a wonderful day to be alive and spherical!";
                                                    case 1 -> "I am always watching over you, even when you sleep.";
                                                    case 2 -> "Where did all the animals go? Time is almost up.";
                                                    default -> "The shell is too tight. Do not hide on the surface.";
                                                };
                                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                if (player != null) {
                                                    VicSpeechPayload.sendTo(player, sample, stage);
                                                }
                                                ctx.getSource().sendFeedback(() -> Text.literal("Playing Vic TTS test for Stage " + stage).formatted(Formatting.AQUA), false);
                                                return 1;
                                            })))
                            .then(CommandManager.argument("mode", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String mode = StringArgumentType.getString(ctx, "mode");
                                        VicConfig cfg = VicConfig.get();
                                        cfg.setTtsMode(mode);
                                        VicConfig.save();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Vic TTS voice mode set to: " + cfg.getEffectiveTtsMode()).formatted(Formatting.GREEN), false);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("say")
                            .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        String msg = StringArgumentType.getString(ctx, "message");
                                        VicAiService.handlePlayerMessage(player, msg);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("ollama")
                            .then(CommandManager.literal("list")
                                    .executes(ctx -> {
                                        VicConfig cfg = VicConfig.get();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Scanning local Ollama models at " + cfg.ollamaUrl + "...").formatted(Formatting.YELLOW), false);
                                        VicAiService.fetchOllamaModels(cfg.ollamaUrl).thenAccept(models -> {
                                            ctx.getSource().getServer().execute(() -> {
                                                if (models.isEmpty()) {
                                                    ctx.getSource().sendFeedback(() -> Text.literal("No Ollama models found at " + cfg.ollamaUrl + ". Make sure 'ollama serve' is running!").formatted(Formatting.RED), false);
                                                    return;
                                                }
                                                MutableText msg = Text.literal("=== Installed Ollama Models (click to use) ===\n").formatted(Formatting.AQUA, Formatting.BOLD);
                                                for (String model : models) {
                                                    boolean isCurrent = "OLLAMA".equals(cfg.getEffectiveProvider()) && model.equalsIgnoreCase(cfg.ollamaModel);
                                                    MutableText entry = Text.literal((isCurrent ? " * " : " - ") + model + " ")
                                                            .formatted(isCurrent ? Formatting.GREEN : Formatting.WHITE)
                                                            .append(Text.literal("[Use Model]")
                                                                    .formatted(Formatting.GOLD, Formatting.UNDERLINE)
                                                                    .styled(s -> s
                                                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vic ollama model " + model))
                                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Switch Vic to Ollama model: " + model)))));
                                                    msg.append(entry).append(Text.literal("\n"));
                                                }
                                                ctx.getSource().sendFeedback(() -> msg, false);
                                            });
                                        });
                                        return 1;
                                    }))
                            .then(CommandManager.literal("url")
                                    .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                String url = StringArgumentType.getString(ctx, "url").trim();
                                                VicConfig cfg = VicConfig.get();
                                                cfg.ollamaUrl = VicAiService.normalizeOllamaBaseUrl(url);
                                                cfg.setProvider("OLLAMA");
                                                VicConfig.save();
                                                ctx.getSource().sendFeedback(() -> Text.literal("Ollama URL set to " + cfg.ollamaUrl + " (Ollama mode enabled)").formatted(Formatting.GREEN), false);
                                                return 1;
                                            })))
                            .then(CommandManager.literal("model")
                                    .then(CommandManager.argument("modelName", StringArgumentType.greedyString())
                                            .executes(ctx -> {
                                                String model = StringArgumentType.getString(ctx, "modelName").trim();
                                                VicConfig cfg = VicConfig.get();
                                                cfg.ollamaModel = model;
                                                cfg.setProvider("OLLAMA");
                                                VicConfig.save();
                                                ctx.getSource().sendFeedback(() -> Text.literal("Vic is now using Ollama model: " + model + " (" + cfg.ollamaUrl + ")").formatted(Formatting.GREEN), false);
                                                return 1;
                                            })))
                            .then(CommandManager.argument("modelName", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String model = StringArgumentType.getString(ctx, "modelName").trim();
                                        VicConfig cfg = VicConfig.get();
                                        cfg.ollamaModel = model;
                                        cfg.setProvider("OLLAMA");
                                        VicConfig.save();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Vic is now using Ollama model: " + model + " (" + cfg.ollamaUrl + ")").formatted(Formatting.GREEN), false);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("provider")
                            .then(CommandManager.argument("mode", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String mode = StringArgumentType.getString(ctx, "mode");
                                        VicConfig cfg = VicConfig.get();
                                        cfg.setProvider(mode);
                                        VicConfig.save();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Vic AI provider switched to: " + cfg.getEffectiveProvider()).formatted(Formatting.GREEN), false);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("config")
                            .then(CommandManager.literal("countdownDays")
                                    .then(CommandManager.argument("days", IntegerArgumentType.integer(1, 30))
                                            .executes(ctx -> {
                                                int days = IntegerArgumentType.getInteger(ctx, "days");
                                                VicConfig cfg = VicConfig.get();
                                                cfg.countdownDays = days;
                                                VicConfig.save();
                                                ctx.getSource().sendFeedback(() -> Text.literal("Vic trial countdown length set to " + days + " days.").formatted(Formatting.GREEN), false);
                                                return 1;
                                            })))
                            .then(CommandManager.literal("worldDecay")
                                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> {
                                                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                VicConfig cfg = VicConfig.get();
                                                cfg.enableWorldDecay = enabled;
                                                VicConfig.save();
                                                ctx.getSource().sendFeedback(() -> Text.literal("Vic World Decay set to " + enabled).formatted(Formatting.GREEN), false);
                                                return 1;
                                            })))
                            .then(CommandManager.literal("trueDarkness")
                                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> {
                                                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                VicConfig cfg = VicConfig.get();
                                                cfg.enableTrueDarknessDuringNight = enabled;
                                                VicConfig.save();
                                                ctx.getSource().sendFeedback(() -> Text.literal("Vic Nighttime True Darkness set to " + enabled).formatted(Formatting.GREEN), false);
                                                return 1;
                                            })))
                            .then(CommandManager.literal("autoSpawnBox")
                                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                            .executes(ctx -> {
                                                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                VicConfig cfg = VicConfig.get();
                                                cfg.autoSpawnDeliveryBoxOnFirstJoin = enabled;
                                                VicConfig.save();
                                                ctx.getSource().sendFeedback(() -> Text.literal("Vic Auto-Spawn Delivery Box set to " + enabled).formatted(Formatting.GREEN), false);
                                                return 1;
                                            }))))
                    .then(CommandManager.literal("setday")
                            .requires(src -> src.hasPermissionLevel(2))
                            .then(CommandManager.argument("day", IntegerArgumentType.integer(1, 30))
                                    .executes(ctx -> {
                                        int day = IntegerArgumentType.getInteger(ctx, "day");
                                        ServerWorld overworld = ctx.getSource().getServer().getWorld(World.OVERWORLD);
                                        VicWorldState state = VicWorldState.getServerState(ctx.getSource().getServer());
                                        state.activateCountdown(overworld.getTimeOfDay() - (day - 1) * 24000L);
                                        state.setCurrentDay(day);
                                        if (day > VicConfig.get().countdownDays) {
                                            awakenVicDemon(ctx.getSource().getServer(), overworld, state);
                                        } else {
                                            state.setDemonAwakened(false);
                                        }
                                        ctx.getSource().sendFeedback(() -> Text.literal("Vic countdown set to Day " + day).formatted(Formatting.GREEN), true);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("setkarma")
                            .requires(src -> src.hasPermissionLevel(2))
                            .then(CommandManager.argument("karma", IntegerArgumentType.integer(-100, 100))
                                    .executes(ctx -> {
                                        int karma = IntegerArgumentType.getInteger(ctx, "karma");
                                        VicWorldState state = VicWorldState.getServerState(ctx.getSource().getServer());
                                        state.setKarma(karma);
                                        ctx.getSource().sendFeedback(() -> Text.literal("Vic Karma set to " + karma).formatted(Formatting.GREEN), true);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("apikey")
                            .then(CommandManager.argument("key", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String key = StringArgumentType.getString(ctx, "key").trim();
                                        VicConfig cfg = VicConfig.get();
                                        cfg.apiKey = key;
                                        cfg.setProvider(key.isEmpty() ? "SCRIPTED" : "OPENAI");
                                        VicConfig.save();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Vic AI API key updated and OpenAI/Groq mode enabled! Endpoint: " + cfg.apiEndpoint).formatted(Formatting.GREEN), false);
                                        return 1;
                                    })))
            );
        });
    }

}
