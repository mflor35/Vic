package com.vicmod.client.model;

import com.vicmod.entity.VicBoxEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;

public class VicBoxEntityModel extends EntityModel<VicBoxEntity> {
    private final ModelPart base;
    private final ModelPart lid;

    public VicBoxEntityModel(ModelPart root) {
        this.base = root.getChild("base");
        this.lid = root.getChild("lid");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        root.addChild("base",
                ModelPartBuilder.create().uv(0, 0).cuboid(-7.0f, 10.0f, -7.0f, 14.0f, 14.0f, 14.0f),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));

        root.addChild("lid",
                ModelPartBuilder.create().uv(0, 28).cuboid(-7.5f, -3.0f, -14.5f, 15.0f, 3.0f, 15.0f),
                ModelTransform.pivot(0.0f, 10.0f, 7.0f));

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setAngles(VicBoxEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        if (entity.isUnboxing()) {
            int ticks = entity.getUnboxTicks();
            float progress = Math.min(1.0f, ticks / 50.0f);
            this.lid.pitch = -progress * 1.35f;
            this.base.yaw = (float) Math.sin(animationProgress * 1.4f) * 0.08f * (1.0f - progress * 0.5f);
        } else {
            this.lid.pitch = 0.0f;
            this.base.yaw = 0.0f;
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        this.base.render(matrices, vertices, light, overlay, color);
        this.lid.render(matrices, vertices, light, overlay, color);
    }
}
