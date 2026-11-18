package com.vicmod.client.model;

import com.vicmod.client.render.VicBoxRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class VicBoxEntityModel extends EntityModel<VicBoxRenderState> {
    private final ModelPart base;
    private final ModelPart lid;

    public VicBoxEntityModel(ModelPart root) {
        super(root);
        this.base = root.getChild("base");
        this.lid = root.getChild("lid");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("base",
                CubeListBuilder.create().texOffs(0, 0).addBox(-7.0f, 10.0f, -7.0f, 14.0f, 14.0f, 14.0f),
                PartPose.ZERO);

        root.addOrReplaceChild("lid",
                CubeListBuilder.create().texOffs(0, 28).addBox(-7.5f, -3.0f, -14.5f, 15.0f, 3.0f, 15.0f),
                PartPose.offset(0.0f, 10.0f, 7.0f));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(VicBoxRenderState state) {
        super.setupAnim(state);
        if (state.isUnboxing) {
            float progress = Math.min(1.0f, state.unboxTicks / 50.0f);
            this.lid.xRot = -progress * 1.35f;
            this.base.yRot = (float) Math.sin(state.ageInTicks * 1.4f) * 0.08f * (1.0f - progress * 0.5f);
        } else {
            this.lid.xRot = 0.0f;
            this.base.yRot = 0.0f;
        }
    }
}
