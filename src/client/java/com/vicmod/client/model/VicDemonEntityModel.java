package com.vicmod.client.model;

import com.vicmod.entity.VicDemonEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

public class VicDemonEntityModel extends EntityModel<VicDemonEntity> {
    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public VicDemonEntityModel(ModelPart root) {
        this.head = root.getChild("head");
        this.torso = root.getChild("torso");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // Towering 4-block nightmare proportions with Vic's cracked yellow head
        root.addChild("head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-6.0f, -12.0f, -6.0f, 12.0f, 12.0f, 12.0f),
                ModelTransform.pivot(0.0f, -26.0f, 0.0f));

        root.addChild("torso",
                ModelPartBuilder.create().uv(0, 24).cuboid(-5.0f, 0.0f, -3.0f, 10.0f, 24.0f, 6.0f),
                ModelTransform.pivot(0.0f, -26.0f, 0.0f));

        root.addChild("left_arm",
                ModelPartBuilder.create().uv(32, 24).cuboid(-1.0f, -2.0f, -1.5f, 3.0f, 34.0f, 3.0f),
                ModelTransform.pivot(6.0f, -24.0f, 0.0f));

        root.addChild("right_arm",
                ModelPartBuilder.create().uv(32, 24).cuboid(-2.0f, -2.0f, -1.5f, 3.0f, 34.0f, 3.0f),
                ModelTransform.pivot(-6.0f, -24.0f, 0.0f));

        root.addChild("left_leg",
                ModelPartBuilder.create().uv(44, 24).cuboid(-1.5f, 0.0f, -1.5f, 3.0f, 26.0f, 3.0f),
                ModelTransform.pivot(2.5f, -2.0f, 0.0f));

        root.addChild("right_leg",
                ModelPartBuilder.create().uv(44, 24).cuboid(-1.5f, 0.0f, -1.5f, 3.0f, 26.0f, 3.0f),
                ModelTransform.pivot(-2.5f, -2.0f, 0.0f));

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setAngles(VicDemonEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        this.head.yaw = headYaw * ((float) Math.PI / 180f);
        this.head.pitch = headPitch * ((float) Math.PI / 180f);
        this.head.roll = MathHelper.sin(animationProgress * 0.45f) * 0.12f;

        this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.6662f) * 1.2f * limbDistance;
        this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * 1.2f * limbDistance;

        this.leftArm.pitch = -0.35f + MathHelper.cos(limbAngle * 0.6662f + (float) Math.PI) * 1.1f * limbDistance;
        this.rightArm.pitch = -0.35f + MathHelper.cos(limbAngle * 0.6662f) * 1.1f * limbDistance;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        this.head.render(matrices, vertices, light, overlay, color);
        this.torso.render(matrices, vertices, light, overlay, color);
        this.leftArm.render(matrices, vertices, light, overlay, color);
        this.rightArm.render(matrices, vertices, light, overlay, color);
        this.leftLeg.render(matrices, vertices, light, overlay, color);
        this.rightLeg.render(matrices, vertices, light, overlay, color);
    }
}
