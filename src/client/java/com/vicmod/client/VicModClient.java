package com.vicmod.client;

import com.vicmod.VicMod;
import com.vicmod.client.gui.VicConfigScreen;
import com.vicmod.client.model.VicBoxEntityModel;
import com.vicmod.client.model.VicDemonEntityModel;
import com.vicmod.client.model.VicEntityModel;
import com.vicmod.client.render.VicBoxEntityRenderer;
import com.vicmod.client.render.VicDemonEntityRenderer;
import com.vicmod.client.render.VicEntityRenderer;
import com.vicmod.registry.ModEntities;
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
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VicModClient implements ClientModInitializer {
    public static final EntityModelLayer VIC_BOX_LAYER = new EntityModelLayer(Identifier.of(VicMod.MOD_ID, "vic_box"), "main");
    public static final EntityModelLayer VIC_LAYER = new EntityModelLayer(Identifier.of(VicMod.MOD_ID, "vic"), "main");
    public static final EntityModelLayer VIC_DEMON_LAYER = new EntityModelLayer(Identifier.of(VicMod.MOD_ID, "vic_demon"), "main");

    private static KeyBinding openConfigKey;

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.wasPressed()) {
                client.setScreen(new VicConfigScreen(client.currentScreen));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("vicconfig")
                    .executes(ctx -> {
                        MinecraftClient client = ctx.getSource().getClient();
                        client.send(() -> client.setScreen(new VicConfigScreen(client.currentScreen)));
                        return 1;
                    }));
        });
    }
}
