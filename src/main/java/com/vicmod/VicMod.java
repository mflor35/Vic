package com.vicmod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import com.vicmod.entity.VicBoxEntity;
import com.vicmod.entity.VicDemonEntity;
import com.vicmod.entity.VicEntity;
import com.vicmod.item.FlashlightItem;
import com.vicmod.registry.ModEntities;
import com.vicmod.registry.ModItems;
import com.vicmod.state.VicWorldState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
        ModEntities.register();
        ModItems.register();

        registerChatListener();
        registerFirstJoinDelivery();
        registerWorldProgressionEvents();
        registerCommands();

        LOGGER.info("Vic Mod initialized! Don't forget to smile :)");
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

                if (VicConfig.get().autoSpawnDeliveryBoxOnFirstJoin) {
                    VicBoxEntity box = ModEntities.VIC_BOX.create(overworld);
                    if (box != null) {
                        double bx = player.getX() + player.getRotationVec(1.0f).x * 2.5;
                        double bz = player.getZ() + player.getRotationVec(1.0f).z * 2.5;
                        box.refreshPositionAndAngles(bx, player.getY(), bz, player.getYaw() + 180.0f, 0.0f);
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
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(
                    Text.literal("<Vic> ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                            .append(Text.literal("TRIAL PERIOD EXPIRED. THANK YOU FOR USING VIC. DO NOT HIDE ON THE SURFACE.").formatted(Formatting.RED)),
                    false
            );
            overworld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 0.5f);

            List<VicEntity> vics = overworld.getEntitiesByClass(VicEntity.class, player.getBoundingBox().expand(96.0), Entity -> true);
            if (!vics.isEmpty()) {
                for (VicEntity vic : vics) {
                    vic.transformIntoDemon(overworld);
                }
            } else {
                VicDemonEntity demon = ModEntities.VIC_DEMON.create(overworld);
                if (demon != null) {
                    double angle = overworld.getRandom().nextDouble() * Math.PI * 2.0;
                    double dx = Math.cos(angle) * 16.0;
                    double dz = Math.sin(angle) * 16.0;
                    demon.refreshPositionAndAngles(player.getX() + dx, player.getY(), player.getZ() + dz, 0.0f, 0.0f);
                    overworld.spawnEntity(demon);
                }
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
                                ctx.getSource().sendFeedback(() -> Text.literal(
                                        "=== Vic Mod Status ===\n" +
                                                "Countdown Active: " + state.isCountdownActive() + "\n" +
                                                "Day: " + state.getCurrentDay() + " / " + cfg.countdownDays + "\n" +
                                                "Karma: " + state.getKarma() + " (Stage " + stage + ")\n" +
                                                "Demon Awakened: " + state.isDemonAwakened() + "\n" +
                                                "External AI Enabled: " + cfg.enableAiApi + " (" + cfg.apiModel + ")"
                                ).formatted(Formatting.YELLOW), false);
                                return 1;
                            }))
                    .then(CommandManager.literal("say")
                            .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                        String msg = StringArgumentType.getString(ctx, "message");
                                        VicAiService.handlePlayerMessage(player, msg);
                                        return 1;
                                    })))
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
                                        cfg.enableAiApi = !key.isEmpty();
                                        VicConfig.save();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Vic AI API key updated and enabled! Endpoint: " + cfg.apiEndpoint).formatted(Formatting.GREEN), false);
                                        return 1;
                                    })))
            );
        });
    }
}
