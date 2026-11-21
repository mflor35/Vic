package com.vicmod.client.model;

import com.vicmod.client.render.VicRenderState;
import com.vicmod.config.VicPersona;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class VicEntityModel extends EntityModel<VicRenderState> {
    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    // Persona-specific 3D sculpted accessories & tails
    private final ModelPart axolotlLeftGills;
    private final ModelPart axolotlRightGills;
    private final ModelPart axolotlTail;
    private final ModelPart topHat;
    private final ModelPart bearEars;
    private final ModelPart microphone;
    private final ModelPart foxEars;
    private final ModelPart foxSnout;
    private final ModelPart pirateHook;
    private final ModelPart foxTail;
    private final ModelPart bunnyEars;
    private final ModelPart bunnyTail;

    public VicEntityModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.torso = root.getChild("torso");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");

        this.axolotlLeftGills = this.head.getChild("axolotl_left_gills");
        this.axolotlRightGills = this.head.getChild("axolotl_right_gills");
        this.topHat = this.head.getChild("top_hat");
        this.bearEars = this.head.getChild("bear_ears");
        this.foxEars = this.head.getChild("fox_ears");
        this.foxSnout = this.head.getChild("fox_snout");
        this.bunnyEars = this.head.getChild("bunny_ears");

        this.axolotlTail = this.torso.getChild("axolotl_tail");
        this.foxTail = this.torso.getChild("fox_tail");
        this.bunnyTail = this.torso.getChild("bunny_tail");

        this.microphone = this.rightArm.getChild("microphone");
        this.pirateHook = this.rightArm.getChild("pirate_hook");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();

        // 1. Head & Neck (Y = 0)
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f)
                        .texOffs(16, 20).addBox(-2.0f, 0.0f, -1.5f, 4.0f, 2.0f, 3.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        // Persona head accessories
        head.addOrReplaceChild("axolotl_left_gills", CubeListBuilder.create()
                .texOffs(48, 0).addBox(4.0f, -6.0f, 0.0f, 4.0f, 8.0f, 0.0f), PartPose.ZERO);
        head.addOrReplaceChild("axolotl_right_gills", CubeListBuilder.create()
                .texOffs(48, 0).mirror().addBox(-8.0f, -6.0f, 0.0f, 4.0f, 8.0f, 0.0f), PartPose.ZERO);

        head.addOrReplaceChild("top_hat", CubeListBuilder.create()
                .texOffs(40, 24).addBox(-3.0f, -13.0f, -3.0f, 6.0f, 5.0f, 6.0f)
                .texOffs(36, 35).addBox(-4.5f, -8.5f, -4.5f, 9.0f, 1.0f, 9.0f), PartPose.ZERO);
        head.addOrReplaceChild("bear_ears", CubeListBuilder.create()
                .texOffs(48, 44).addBox(-5.5f, -10.5f, -1.0f, 3.0f, 3.0f, 2.0f)
                .texOffs(48, 44).mirror().addBox(2.5f, -10.5f, -1.0f, 3.0f, 3.0f, 2.0f), PartPose.ZERO);

        head.addOrReplaceChild("fox_ears", CubeListBuilder.create()
                .texOffs(48, 50).addBox(-5.0f, -12.0f, -1.0f, 3.0f, 5.0f, 2.0f)
                .texOffs(48, 50).mirror().addBox(2.0f, -12.0f, -1.0f, 3.0f, 5.0f, 2.0f), PartPose.ZERO);
        head.addOrReplaceChild("fox_snout", CubeListBuilder.create()
                .texOffs(44, 58).addBox(-2.5f, -3.0f, -7.0f, 5.0f, 3.0f, 3.0f), PartPose.ZERO);

        head.addOrReplaceChild("bunny_ears", CubeListBuilder.create()
                .texOffs(48, 10).addBox(-3.5f, -16.0f, -1.0f, 3.0f, 9.0f, 2.0f)
                .texOffs(48, 10).mirror().addBox(0.5f, -16.0f, -1.0f, 3.0f, 9.0f, 2.0f), PartPose.ZERO);

        // 2. Torso with chest, waist, and coat trim (Y = 0 to 12)
        PartDefinition torso = root.addOrReplaceChild("torso",
                CubeListBuilder.create()
                        .texOffs(16, 16).addBox(-4.0f, 0.0f, -2.5f, 8.0f, 7.0f, 5.0f)
                        .texOffs(16, 28).addBox(-3.5f, 7.0f, -2.0f, 7.0f, 5.0f, 4.0f)
                        .texOffs(0, 48).addBox(-1.5f, 1.0f, -2.8f, 3.0f, 5.0f, 1.0f),
                PartPose.offset(0.0f, 0.0f, 0.0f));

        torso.addOrReplaceChild("axolotl_tail", CubeListBuilder.create()
                .texOffs(24, 46).addBox(-1.0f, 0.0f, 0.0f, 2.0f, 8.0f, 4.0f),
                PartPose.offset(0.0f, 10.0f, 2.0f));
        torso.addOrReplaceChild("fox_tail", CubeListBuilder.create()
                .texOffs(24, 46).addBox(-1.5f, 0.0f, 0.0f, 3.0f, 9.0f, 3.0f),
                PartPose.offset(0.0f, 10.0f, 2.0f));
        torso.addOrReplaceChild("bunny_tail", CubeListBuilder.create()
                .texOffs(24, 46).addBox(-1.5f, -1.0f, 0.0f, 3.0f, 3.0f, 3.0f),
                PartPose.offset(0.0f, 10.0f, 2.0f));

        // 3. Left Arm with realistic shoulder & forearm cuff
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(32, 48).addBox(-1.0f, -1.0f, -1.5f, 3.0f, 12.0f, 3.0f)
                        .texOffs(32, 58).addBox(-1.2f, 8.0f, -1.7f, 3.4f, 2.5f, 3.4f),
                PartPose.offset(5.0f, 2.0f, 0.0f));

        // 4. Right Arm with props
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 16).addBox(-2.0f, -1.0f, -1.5f, 3.0f, 12.0f, 3.0f)
                        .texOffs(40, 26).addBox(-2.2f, 8.0f, -1.7f, 3.4f, 2.5f, 3.4f),
                PartPose.offset(-5.0f, 2.0f, 0.0f));

        rightArm.addOrReplaceChild("microphone", CubeListBuilder.create()
                .texOffs(52, 28).addBox(-1.5f, 9.0f, -3.5f, 2.0f, 5.0f, 2.0f), PartPose.ZERO);
        rightArm.addOrReplaceChild("pirate_hook", CubeListBuilder.create()
                .texOffs(52, 36).addBox(-1.5f, 10.5f, -1.0f, 2.0f, 4.0f, 2.0f), PartPose.ZERO);

        // 5. Left Leg with sculpted knee & shoe
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create()
                        .texOffs(16, 48).addBox(-1.5f, 0.0f, -1.8f, 3.2f, 10.0f, 3.6f)
                        .texOffs(0, 32).addBox(-1.5f, 10.0f, -2.8f, 3.2f, 2.0f, 4.8f),
                PartPose.offset(1.9f, 12.0f, 0.0f));

        // 6. Right Leg with sculpted knee & shoe
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16).addBox(-1.7f, 0.0f, -1.8f, 3.2f, 10.0f, 3.6f)
                        .texOffs(0, 32).mirror().addBox(-1.7f, 10.0f, -2.8f, 3.2f, 2.0f, 4.8f),
                PartPose.offset(-1.9f, 12.0f, 0.0f));

        return LayerDefinition.create(modelData, 64, 64);
    }

    @Override
    public void setupAnim(VicRenderState state) {
        super.setupAnim(state);

        // 1. Head Tracking
        this.head.yRot = state.yRot * ((float) Math.PI / 180f);
        this.head.xRot = state.xRot * ((float) Math.PI / 180f);

        // 2. Walking Cycle Animation
        float legSwing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.35f * state.walkAnimationSpeed;
        this.leftLeg.xRot = -legSwing;
        this.rightLeg.xRot = legSwing;
        this.leftLeg.yRot = 0.0f;
        this.rightLeg.yRot = 0.0f;

        float armSwing = Mth.cos(state.walkAnimationPos * 0.6662f) * 1.15f * state.walkAnimationSpeed;
        this.leftArm.xRot = armSwing;
        this.rightArm.xRot = -armSwing;
        this.leftArm.zRot = 0.08f + Mth.abs(Mth.cos(state.walkAnimationPos * 0.3331f)) * 0.06f * state.walkAnimationSpeed;
        this.rightArm.zRot = -0.08f - Mth.abs(Mth.cos(state.walkAnimationPos * 0.3331f)) * 0.06f * state.walkAnimationSpeed;

        // Torso natural walking bob and counter-sway
        this.torso.y = Mth.abs(Mth.sin(state.walkAnimationPos * 0.6662f)) * 0.6f * state.walkAnimationSpeed;
        this.torso.zRot = Mth.cos(state.walkAnimationPos * 0.6662f) * 0.04f * state.walkAnimationSpeed;
        this.head.y = this.torso.y;

        // 3. Idle subtle breathing & natural stance
        float breath = Mth.sin(state.ageInTicks * 0.08f) * 0.025f;
        this.torso.xRot = breath;
        this.leftArm.xRot += breath * 0.5f;
        this.rightArm.xRot += breath * 0.5f;

        // 4. Persona Visibility
        boolean isAxolotl = (state.persona == VicPersona.AXOLOTL);
        boolean isFreddy = (state.persona == VicPersona.FREDDY);
        boolean isFoxy = (state.persona == VicPersona.FOXY);
        boolean isBonnie = (state.persona == VicPersona.BONNIE);
        boolean isSpringtrap = (state.persona == VicPersona.SPRINGTRAP);

        this.axolotlLeftGills.visible = isAxolotl;
        this.axolotlRightGills.visible = isAxolotl;
        this.axolotlTail.visible = isAxolotl;

        this.topHat.visible = isFreddy;
        this.bearEars.visible = isFreddy;
        this.microphone.visible = isFreddy;

        this.foxEars.visible = isFoxy;
        this.foxSnout.visible = isFoxy;
        this.pirateHook.visible = isFoxy;
        this.foxTail.visible = isFoxy;

        this.bunnyEars.visible = (isBonnie || isSpringtrap);
        this.bunnyTail.visible = (isBonnie || isSpringtrap);

        // 5. Tail and Accessory Dynamics
        if (isAxolotl) {
            float gillFlap = Mth.sin(state.ageInTicks * 0.16f) * 0.22f;
            this.axolotlLeftGills.zRot = gillFlap;
            this.axolotlRightGills.zRot = -gillFlap;
            this.axolotlTail.yRot = Mth.sin(state.ageInTicks * 0.12f) * 0.25f + Mth.cos(state.walkAnimationPos * 0.6662f) * 0.35f * state.walkAnimationSpeed;
            this.axolotlTail.xRot = 0.2f;
        }

        if (isFoxy) {
            this.foxTail.yRot = Mth.cos(state.walkAnimationPos * 0.6662f) * 0.4f * state.walkAnimationSpeed + Mth.sin(state.ageInTicks * 0.06f) * 0.12f;
            this.foxTail.xRot = 0.3f + Mth.sin(state.walkAnimationPos * 1.3324f) * 0.1f * state.walkAnimationSpeed;
            this.rightArm.xRot -= 0.3f;
        }

        if (isFreddy) {
            this.rightArm.xRot -= 0.35f;
            this.rightArm.yRot -= 0.12f;
        }

        if (isBonnie || isSpringtrap) {
            float earBounce = Mth.abs(Mth.sin(state.walkAnimationPos * 0.6662f)) * 0.15f * state.walkAnimationSpeed;
            this.bunnyEars.xRot = -0.05f + earBounce;
            this.bunnyTail.xRot = 0.2f;
        }

        // 6. Horror Glitch / Psychological Breakdown
        if (state.corruptionStage == 2) {
            this.head.zRot = (Mth.sin(state.ageInTicks * 0.8f) > 0.85f) ? 0.18f : 0.0f;
        } else if (state.corruptionStage >= 3) {
            this.head.zRot = Mth.sin(state.ageInTicks * 2.8f) * 0.16f;
            this.torso.xRot += 0.18f;
            this.leftArm.xRot += Mth.sin(state.ageInTicks * 3.2f) * 0.15f;
            this.rightArm.xRot += Mth.cos(state.ageInTicks * 3.2f) * 0.15f;
        } else {
            this.head.zRot = 0.0f;
        }
    }
}
