package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.VicBoxEntityModel;
import com.vicmod.entity.VicBoxEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class VicBoxEntityRenderer extends MobRenderer<VicBoxEntity, VicBoxRenderState, VicBoxEntityModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "textures/entity/vic_box.png");

    public VicBoxEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new VicBoxEntityModel(context.bakeLayer(VicModClient.VIC_BOX_LAYER)), 0.5f);
    }

    @Override
    public VicBoxRenderState createRenderState() {
        return new VicBoxRenderState();
    }

    @Override
    public void extractRenderState(VicBoxEntity entity, VicBoxRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isUnboxing = entity.isUnboxing();
        state.unboxTicks = entity.getUnboxTicks();
    }

    @Override
    public Identifier getTextureLocation(VicBoxRenderState state) {
        return TEXTURE;
    }
}
