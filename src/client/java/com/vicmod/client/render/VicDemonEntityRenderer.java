package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.VicDemonEntityModel;
import com.vicmod.entity.VicDemonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class VicDemonEntityRenderer extends MobRenderer<VicDemonEntity, LivingEntityRenderState, VicDemonEntityModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "textures/entity/vic_demon.png");

    public VicDemonEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new VicDemonEntityModel(context.bakeLayer(VicModClient.VIC_DEMON_LAYER)), 0.7f);
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
