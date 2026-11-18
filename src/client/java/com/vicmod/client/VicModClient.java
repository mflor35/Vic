package com.vicmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.vicmod.VicMod;
import com.vicmod.client.gui.VicConfigScreen;
import com.vicmod.client.gui.VicHudOverlay;
import com.vicmod.client.input.VicControllerService;
import com.vicmod.client.model.VicBoxEntityModel;
import com.vicmod.client.model.VicDemonEntityModel;
import com.vicmod.client.model.VicEntityModel;
import com.vicmod.client.render.VicBoxEntityRenderer;
import com.vicmod.client.render.VicDemonEntityRenderer;
import com.vicmod.client.render.VicEntityRenderer;
import com.vicmod.config.VicConfig;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.network.VicSpeechPayload;
import com.vicmod.registry.ModEntities;
import com.vicmod.voice.VicTtsService;
import com.vicmod.voice.VicVoiceInputService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class VicModClient implements ClientModInitializer {
    public static final ModelLayerLocation VIC_BOX_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "vic_box"), "main");
    public static final ModelLayerLocation VIC_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "vic"), "main");
    public static final ModelLayerLocation VIC_DEMON_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "vic_demon"), "main");
    public static final ModelLayerLocation SHADOW_WISP_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "shadow_wisp"), "main");

    private static KeyMapping openConfigKey;
    private static KeyMapping voicePttKey;
    private static KeyMapping toggleFlashlightKey;
    private static boolean wasVoiceKeyHeld = false;

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(VIC_BOX_LAYER, VicBoxEntityModel::getTexturedModelData);
        ModelLayerRegistry.registerModelLayer(VIC_LAYER, VicEntityModel::getTexturedModelData);
        ModelLayerRegistry.registerModelLayer(VIC_DEMON_LAYER, VicDemonEntityModel::getTexturedModelData);
        ModelLayerRegistry.registerModelLayer(SHADOW_WISP_LAYER, com.vicmod.client.model.ShadowWispModel::getTexturedModelData);

        EntityRendererRegistry.register(ModEntities.VIC_BOX, VicBoxEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.VIC, VicEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.VIC_DEMON, VicDemonEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHADOW_WISP, com.vicmod.client.render.ShadowWispRenderer::new);

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vic.open_config",
                InputConstants.KEY_V,
                KeyMapping.Category.MISC
        ));

        voicePttKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vic.voice_ptt",
                InputConstants.KEY_R,
                KeyMapping.Category.MISC
        ));

        toggleFlashlightKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vic.toggle_flashlight",
                InputConstants.KEY_G,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.gui.setScreen(new VicConfigScreen(client.gui.screen()));
            }
            while (toggleFlashlightKey.consumeClick()) {
                VicControllerService.quickToggleFlashlight(client);
            }

            VicControllerService.tick(client);
            handlePushToTalkTick(client);
        });

        LevelRenderEvents.START_MAIN.register(context -> {
            VicControllerService.renderTick(Minecraft.getInstance());
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("vicconfig")
                    .executes(ctx -> {
                        Minecraft client = ctx.getSource().getClient();
                        client.execute(() -> client.gui.setScreen(new VicConfigScreen(client.gui.screen())));
                        return 1;
                    }));
            dispatcher.register(ClientCommands.literal("viclisten")
                    .executes(ctx -> {
                        startVoiceListen(ctx.getSource().getClient(), 4);
                        return 1;
                    })
                    .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(2, 12))
                            .executes(ctx -> {
                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                startVoiceListen(ctx.getSource().getClient(), seconds);
                                return 1;
                            })));
        });

        ClientPlayNetworking.registerGlobalReceiver(VicSpeechPayload.TYPE, (payload, context) ->
            VicTtsService.speak(payload.text(), payload.stage()));

        ClientPlayNetworking.registerGlobalReceiver(VicHudStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> VicHudOverlay.updateState(payload)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            client.execute(VicHudOverlay::reset));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "hud"), new VicHudOverlay());
    }

    private void handlePushToTalkTick(Minecraft client) {
        if (client.player == null || client.gui.screen() != null) {
            if (wasVoiceKeyHeld) {
                wasVoiceKeyHeld = false;
                finishVoiceRecording(client);
            }
            return;
        }

        boolean pressed = voicePttKey.isDown() || VicControllerService.isDpadVoiceHeld();
        if (pressed && !wasVoiceKeyHeld) {
            wasVoiceKeyHeld = true;
            if ("OFF".equals(VicConfig.get().getEffectiveSttMode())) {
                client.player.sendOverlayMessage(
                        Component.literal("[Vic Voice] ").withStyle(ChatFormatting.RED)
                                .append(Component.literal("Voice input is disabled. Press [V] to enable it.").withStyle(ChatFormatting.WHITE))
                );
                return;
            }
            if (VicVoiceInputService.startRecording()) {
                client.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5f, 1.5f);
            }
        } else if (pressed && VicVoiceInputService.isRecording()) {
            String releaseHint = VicControllerService.isControllerConnected() ? "[R / D-Pad Up]" : "[R]";
            client.player.sendOverlayMessage(
                    Component.literal("● REC ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.literal("Listening... Release " + releaseHint + " to send to Vic").withStyle(ChatFormatting.YELLOW))
            );
        } else if (!pressed && wasVoiceKeyHeld) {
            wasVoiceKeyHeld = false;
            finishVoiceRecording(client);
        }
    }

    private void finishVoiceRecording(Minecraft client) {
        if (!VicVoiceInputService.isRecording()) {
            return;
        }
        if (client.player != null) {
            client.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5f, 0.9f);
            client.player.sendOverlayMessage(
                    Component.literal("[Vic Voice] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                            .append(Component.literal("Transcribing speech...").withStyle(ChatFormatting.WHITE))
            );
        }

        VicVoiceInputService.stopRecordingAndTranscribe(result -> {
            client.execute(() -> {
                if (client.player == null) return;
                if (result.success() && !result.text().isBlank()) {
                    client.player.sendOverlayMessage(
                            Component.literal("[Voice Sent] ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                                    .append(Component.literal("\"" + result.text() + "\"").withStyle(ChatFormatting.WHITE))
                    );
                    if (client.player.connection != null) {
                        client.player.connection.sendCommand("vic say " + result.text().replace('\n', ' ').replace('\r', ' ').trim());
                    }
                } else {
                    client.player.sendSystemMessage(
                            Component.literal("[Vic Voice] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                    .append(Component.literal(result.errorMessage()).withStyle(ChatFormatting.YELLOW))
                    );
                }
            });
        });
    }

    private void startVoiceListen(Minecraft client, int seconds) {
        if (client.player == null) {
            return;
        }
        if ("OFF".equals(VicConfig.get().getEffectiveSttMode())) {
            client.player.sendSystemMessage(
                    Component.literal("[Vic Voice] ").withStyle(ChatFormatting.RED)
                            .append(Component.literal("Voice input is disabled. Press [V] to enable it.").withStyle(ChatFormatting.WHITE))
            );
            return;
        }

        client.player.sendOverlayMessage(
                Component.literal("[Vic Voice] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("Listening for " + seconds + " seconds... Speak your request to Vic.").withStyle(ChatFormatting.WHITE))
        );
        VicVoiceInputService.recordFixedDurationAndTranscribe(seconds, result -> client.execute(() -> {
            if (client.player == null) {
                return;
            }
            if (result.success() && !result.text().isBlank()) {
                client.player.sendOverlayMessage(
                        Component.literal("[Voice Sent] ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                                .append(Component.literal("\"" + result.text() + "\"").withStyle(ChatFormatting.WHITE))
                );
                if (client.player.connection != null) {
                    String commandText = result.text().replace('\n', ' ').replace('\r', ' ').trim();
                    client.player.connection.sendCommand("vic say " + commandText);
                }
            } else {
                client.player.sendSystemMessage(
                        Component.literal("[Vic Voice] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .append(Component.literal(result.errorMessage()).withStyle(ChatFormatting.YELLOW))
                );
            }
        }));
    }
}
