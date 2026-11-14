package com.vicmod.client.model;

import com.vicmod.entity.VicEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

public class VicEntityModel extends EntityModel<VicEntity> {
    private final ModelPart sphere;

    public VicEntityModel(ModelPart root) {
        this.sphere = root.getChild("sphere");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // Rounded 3D sphere composite: core + outer shell caps
        ModelPartBuilder builder = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-6.0f, -6.0f, -6.0f, 12.0f, 12.0f, 12.0f)
                .uv(0, 24).cuboid(-5.0f, -7.0f, -5.0f, 10.0f, 14.0f, 10.0f)
                .uv(0, 48).cuboid(-7.0f, -5.0f, -5.0f, 14.0f, 10.0f, 10.0f);

        root.addChild("sphere", builder, ModelTransform.pivot(0.0f, 16.0f, 0.0f));
        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setAngles(VicEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        this.sphere.yaw = headYaw * ((float) Math.PI / 180f);
        this.sphere.pitch = headPitch * ((float) Math.PI / 180f);
        this.sphere.pivotY = 16.0f + MathHelper.sin(animationProgress * 0.12f) * 1.5f;

        if (entity.getCorruptionStage() >= 3) {
            // Unsettling head twitch in late countdown stage
            this.sphere.roll = MathHelper.sin(animationProgress * 2.1f) * 0.14f;
        } else {
            this.sphere.roll = 0.0f;
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        this.sphere.render(matrices, vertices, light, overlay, color);
    }
}
