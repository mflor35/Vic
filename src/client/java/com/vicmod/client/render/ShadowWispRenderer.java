package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.ShadowWispModel;
import com.vicmod.entity.ShadowWispEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class ShadowWispRenderer extends MobRenderer<ShadowWispEntity, LivingEntityRenderState, ShadowWispModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "textures/entity/shadow_wisp.png");

    public ShadowWispRenderer(EntityRendererProvider.Context context) {
        super(context, new ShadowWispModel(context.bakeLayer(VicModClient.SHADOW_WISP_LAYER)), 0.35f);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }
}
