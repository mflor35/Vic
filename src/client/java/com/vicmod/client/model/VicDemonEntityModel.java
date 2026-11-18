package com.vicmod.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class VicDemonEntityModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public VicDemonEntityModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.torso = root.getChild("torso");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();

        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-6.0f, -12.0f, -6.0f, 12.0f, 12.0f, 12.0f),
                PartPose.offset(0.0f, -26.0f, 0.0f));

        root.addOrReplaceChild("torso",
                CubeListBuilder.create().texOffs(0, 24).addBox(-5.0f, 0.0f, -3.0f, 10.0f, 24.0f, 6.0f),
                PartPose.offset(0.0f, -26.0f, 0.0f));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(32, 24).addBox(-1.0f, -2.0f, -1.5f, 3.0f, 34.0f, 3.0f),
                PartPose.offset(6.0f, -24.0f, 0.0f));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(32, 24).addBox(-2.0f, -2.0f, -1.5f, 3.0f, 34.0f, 3.0f),
                PartPose.offset(-6.0f, -24.0f, 0.0f));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(44, 24).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 26.0f, 3.0f),
                PartPose.offset(2.5f, -2.0f, 0.0f));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(44, 24).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 26.0f, 3.0f),
                PartPose.offset(-2.5f, -2.0f, 0.0f));

        return LayerDefinition.create(modelData, 64, 64);
    }

    @Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        this.head.yRot = state.yRot * ((float) Math.PI / 180f);
        this.head.xRot = state.xRot * ((float) Math.PI / 180f);
        this.head.zRot = Mth.sin(state.ageInTicks * 0.45f) * 0.12f;

        this.leftLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.2f * state.walkAnimationSpeed;
        this.rightLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662f + (float) Math.PI) * 1.2f * state.walkAnimationSpeed;

        this.leftArm.xRot = -0.35f + Mth.cos(state.walkAnimationPos * 0.6662f + (float) Math.PI) * 1.1f * state.walkAnimationSpeed;
        this.rightArm.xRot = -0.35f + Mth.cos(state.walkAnimationPos * 0.6662f) * 1.1f * state.walkAnimationSpeed;
    }
}
