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
    private final ModelPart sphere;
    private final ModelPart axolotlLeftGills;
    private final ModelPart axolotlRightGills;
    private final ModelPart topHat;
    private final ModelPart bearEars;
    private final ModelPart foxEars;
    private final ModelPart foxSnout;
    private final ModelPart bunnyEars;

    public VicEntityModel(ModelPart root) {
        super(root);
        this.sphere = root.getChild("sphere");
        this.axolotlLeftGills = this.sphere.getChild("axolotl_left_gills");
        this.axolotlRightGills = this.sphere.getChild("axolotl_right_gills");
        this.topHat = this.sphere.getChild("top_hat");
        this.bearEars = this.sphere.getChild("bear_ears");
        this.foxEars = this.sphere.getChild("fox_ears");
        this.foxSnout = this.sphere.getChild("fox_snout");
        this.bunnyEars = this.sphere.getChild("bunny_ears");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();

        CubeListBuilder builder = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0f, -6.0f, -6.0f, 12.0f, 12.0f, 12.0f)
                .texOffs(0, 24).addBox(-5.0f, -7.0f, -5.0f, 10.0f, 14.0f, 10.0f)
                .texOffs(0, 48).addBox(-7.0f, -5.0f, -5.0f, 14.0f, 10.0f, 10.0f);

        PartDefinition sphere = root.addOrReplaceChild("sphere", builder, PartPose.offset(0.0f, 16.0f, 0.0f));

        // Axolotl Gills (left & right frills)
        sphere.addOrReplaceChild("axolotl_left_gills", CubeListBuilder.create()
                .texOffs(48, 0).addBox(6.0f, -4.0f, 0.0f, 4.0f, 8.0f, 0.0f), PartPose.ZERO);
        sphere.addOrReplaceChild("axolotl_right_gills", CubeListBuilder.create()
                .texOffs(48, 0).mirror().addBox(-10.0f, -4.0f, 0.0f, 4.0f, 8.0f, 0.0f), PartPose.ZERO);

        // Freddy Bear Ears & Top Hat
        sphere.addOrReplaceChild("top_hat", CubeListBuilder.create()
                .texOffs(40, 24).addBox(-3.0f, -11.0f, -3.0f, 6.0f, 4.0f, 6.0f)
                .texOffs(36, 34).addBox(-4.5f, -7.0f, -4.5f, 9.0f, 1.0f, 9.0f), PartPose.ZERO);
        sphere.addOrReplaceChild("bear_ears", CubeListBuilder.create()
                .texOffs(48, 44).addBox(-6.0f, -9.0f, -1.0f, 3.0f, 3.0f, 2.0f)
                .texOffs(48, 44).mirror().addBox(3.0f, -9.0f, -1.0f, 3.0f, 3.0f, 2.0f), PartPose.ZERO);

        // Foxy Ears & Snout
        sphere.addOrReplaceChild("fox_ears", CubeListBuilder.create()
                .texOffs(48, 50).addBox(-5.5f, -10.5f, -1.0f, 3.0f, 5.0f, 2.0f)
                .texOffs(48, 50).mirror().addBox(2.5f, -10.5f, -1.0f, 3.0f, 5.0f, 2.0f), PartPose.ZERO);
        sphere.addOrReplaceChild("fox_snout", CubeListBuilder.create()
                .texOffs(44, 58).addBox(-2.5f, -1.0f, -8.0f, 5.0f, 3.0f, 3.0f), PartPose.ZERO);

        // Bonnie / Springtrap Bunny Ears
        sphere.addOrReplaceChild("bunny_ears", CubeListBuilder.create()
                .texOffs(48, 10).addBox(-4.0f, -14.0f, -1.0f, 3.0f, 8.0f, 2.0f)
                .texOffs(48, 10).mirror().addBox(1.0f, -14.0f, -1.0f, 3.0f, 8.0f, 2.0f), PartPose.ZERO);

        return LayerDefinition.create(modelData, 64, 64);
    }

    @Override
    public void setupAnim(VicRenderState state) {
        super.setupAnim(state);
        this.sphere.yRot = state.yRot * ((float) Math.PI / 180f);
        this.sphere.xRot = state.xRot * ((float) Math.PI / 180f);
        this.sphere.y = 16.0f + Mth.sin(state.ageInTicks * 0.12f) * 1.5f;

        if (state.corruptionStage >= 3) {
            this.sphere.zRot = Mth.sin(state.ageInTicks * 2.1f) * 0.14f;
        } else {
            this.sphere.zRot = 0.0f;
        }

        boolean isAxolotl = (state.persona == VicPersona.AXOLOTL);
        boolean isFreddy = (state.persona == VicPersona.FREDDY);
        boolean isFoxy = (state.persona == VicPersona.FOXY);
        boolean isBonnie = (state.persona == VicPersona.BONNIE);
        boolean isSpringtrap = (state.persona == VicPersona.SPRINGTRAP);

        this.axolotlLeftGills.visible = isAxolotl;
        this.axolotlRightGills.visible = isAxolotl;
        this.topHat.visible = isFreddy;
        this.bearEars.visible = isFreddy;
        this.foxEars.visible = isFoxy;
        this.foxSnout.visible = isFoxy;
        this.bunnyEars.visible = (isBonnie || isSpringtrap);

        if (isAxolotl) {
            float gillFlap = Mth.sin(state.ageInTicks * 0.18f) * 0.2f;
            this.axolotlLeftGills.zRot = gillFlap;
            this.axolotlRightGills.zRot = -gillFlap;
        } else {
            this.axolotlLeftGills.zRot = 0.0f;
            this.axolotlRightGills.zRot = 0.0f;
        }

        if (isBonnie || isSpringtrap) {
            if (state.corruptionStage >= 2) {
                this.bunnyEars.xRot = Mth.sin(state.ageInTicks * 1.8f) * 0.1f;
            } else {
                this.bunnyEars.xRot = 0.0f;
            }
        }
    }
}
