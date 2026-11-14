package com.vicmod.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.vicmod.VicMod;
import com.vicmod.client.gui.VicConfigScreen;
import com.vicmod.client.model.VicBoxEntityModel;
import com.vicmod.client.model.VicDemonEntityModel;
import com.vicmod.client.model.VicEntityModel;
import com.vicmod.client.render.VicBoxEntityRenderer;
import com.vicmod.client.render.VicDemonEntityRenderer;
import com.vicmod.client.render.VicEntityRenderer;
import com.vicmod.config.VicConfig;
import com.vicmod.registry.ModEntities;
import com.vicmod.voice.VicVoiceInputService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VicModClient implements ClientModInitializer {
    public static final EntityModelLayer VIC_BOX_LAYER = new EntityModelLayer(Identifier.of(VicMod.MOD_ID, "vic_box"), "main");
    public static final EntityModelLayer VIC_LAYER = new EntityModelLayer(Identifier.of(VicMod.MOD_ID, "vic"), "main");
    public static final EntityModelLayer VIC_DEMON_LAYER = new EntityModelLayer(Identifier.of(VicMod.MOD_ID, "vic_demon"), "main");

    private static KeyBinding openConfigKey;
    private static KeyBinding voicePttKey;
    private static boolean wasVoiceKeyHeld = false;

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(VIC_BOX_LAYER, VicBoxEntityModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(VIC_LAYER, VicEntityModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(VIC_DEMON_LAYER, VicDemonEntityModel::getTexturedModelData);

        EntityRendererRegistry.register(ModEntities.VIC_BOX, VicBoxEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.VIC, VicEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.VIC_DEMON, VicDemonEntityRenderer::new);

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vic.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.vic"
        ));

        voicePttKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vic.voice_ptt",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.vic"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                client.setScreen(new VicConfigScreen(client.currentScreen));
            }

            handlePushToTalkTick(client);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("vicconfig")
                    .executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        client.send(() -> client.setScreen(new VicConfigScreen(client.currentScreen)));
                        return 1;
                    }));
            dispatcher.register(ClientCommandManager.literal("viclisten")
                    .executes(ctx -> {
                        startVoiceListen(ctx.getSource().getClient(), 4);
                        return 1;
                    })
                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(2, 12))
                            .executes(ctx -> {
                                int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                startVoiceListen(ctx.getSource().getClient(), seconds);
                                return 1;
                            })));
        });
    }

    private void handlePushToTalkTick(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            if (wasVoiceKeyHeld) {
                wasVoiceKeyHeld = false;
                finishVoiceRecording(client);
            }
            return;
        }

        boolean pressed = voicePttKey.isPressed();
        if (pressed && !wasVoiceKeyHeld) {
            wasVoiceKeyHeld = true;
            if ("OFF".equals(VicConfig.get().getEffectiveSttMode())) {
                client.player.sendMessage(
                        Text.literal("[Vic Voice] ").formatted(Formatting.RED)
                                .append(Text.literal("Voice input is disabled. Press [V] to enable it.").formatted(Formatting.WHITE)),
                        true
                );
                return;
            }
            if (VicVoiceInputService.startRecording()) {
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.5f, 1.5f);
            }
        } else if (pressed && VicVoiceInputService.isRecording()) {
            client.player.sendMessage(
                    Text.literal("● REC ").formatted(Formatting.RED, Formatting.BOLD)
                            .append(Text.literal("Listening... Release [R] to send to Vic").formatted(Formatting.YELLOW)),
                    true
            );
        } else if (!pressed && wasVoiceKeyHeld) {
            wasVoiceKeyHeld = false;
            finishVoiceRecording(client);
        }
    }

    private void finishVoiceRecording(MinecraftClient client) {
        if (!VicVoiceInputService.isRecording()) {
            return;
        }
        if (client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.5f, 0.9f);
            client.player.sendMessage(
                    Text.literal("[Vic Voice] ").formatted(Formatting.AQUA, Formatting.BOLD)
                            .append(Text.literal("Transcribing speech...").formatted(Formatting.WHITE)),
                    true
            );
        }

        VicVoiceInputService.stopRecordingAndTranscribe(result -> {
            client.execute(() -> {
                if (client.player == null) return;
                if (result.success() && !result.text().isBlank()) {
                    client.player.sendMessage(
                            Text.literal("[Voice Sent] ").formatted(Formatting.GREEN, Formatting.BOLD)
                                    .append(Text.literal("\"" + result.text() + "\"").formatted(Formatting.WHITE)),
                            true
                    );
                    if (client.player.networkHandler != null) {
                        client.player.networkHandler.sendChatCommand("vic say " + result.text().replace('\n', ' ').replace('\r', ' ').trim());
                    }
                } else {
                    client.player.sendMessage(
                            Text.literal("[Vic Voice] ").formatted(Formatting.RED, Formatting.BOLD)
                                    .append(Text.literal(result.errorMessage()).formatted(Formatting.YELLOW)),
                            false
                    );
                }
            });
        });
    }

    private void startVoiceListen(MinecraftClient client, int seconds) {
        if (client.player == null) {
            return;
        }
        if ("OFF".equals(VicConfig.get().getEffectiveSttMode())) {
            client.player.sendMessage(
                    Text.literal("[Vic Voice] ").formatted(Formatting.RED)
                            .append(Text.literal("Voice input is disabled. Press [V] to enable it.").formatted(Formatting.WHITE)),
                    false
            );
            return;
        }

        client.player.sendMessage(
                Text.literal("[Vic Voice] ").formatted(Formatting.AQUA, Formatting.BOLD)
                        .append(Text.literal("Listening for " + seconds + " seconds... Speak your request to Vic.").formatted(Formatting.WHITE)),
                true
        );
        VicVoiceInputService.recordFixedDurationAndTranscribe(seconds, result -> client.execute(() -> {
            if (client.player == null) {
                return;
            }
            if (result.success() && !result.text().isBlank()) {
                client.player.sendMessage(
                        Text.literal("[Voice Sent] ").formatted(Formatting.GREEN, Formatting.BOLD)
                                .append(Text.literal("\"" + result.text() + "\"").formatted(Formatting.WHITE)),
                        true
                );
                if (client.player.networkHandler != null) {
                    String commandText = result.text().replace('\n', ' ').replace('\r', ' ').trim();
                    client.player.networkHandler.sendChatCommand("vic say " + commandText);
                }
            } else {
                client.player.sendMessage(
                        Text.literal("[Vic Voice] ").formatted(Formatting.RED, Formatting.BOLD)
                                .append(Text.literal(result.errorMessage()).formatted(Formatting.YELLOW)),
                        false
                );
            }
        }));
    }
}
