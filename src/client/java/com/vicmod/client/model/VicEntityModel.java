package com.vicmod.client.model;

import com.vicmod.client.render.VicRenderState;
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

    public VicEntityModel(ModelPart root) {
        super(root);
        this.sphere = root.getChild("sphere");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();

        CubeListBuilder builder = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0f, -6.0f, -6.0f, 12.0f, 12.0f, 12.0f)
                .texOffs(0, 24).addBox(-5.0f, -7.0f, -5.0f, 10.0f, 14.0f, 10.0f)
                .texOffs(0, 48).addBox(-7.0f, -5.0f, -5.0f, 14.0f, 10.0f, 10.0f);

        root.addOrReplaceChild("sphere", builder, PartPose.offset(0.0f, 16.0f, 0.0f));
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
    }
}
