package com.vicmod.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class ShadowWispModel extends EntityModel<LivingEntityRenderState> {
    private final ModelPart core;

    public ShadowWispModel(ModelPart root) {
        super(root);
        this.core = root.getChild("core");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("core",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f)
                        .texOffs(0, 16)
                        .addBox(-3.0f, 0.0f, -3.0f, 6.0f, 8.0f, 6.0f),
                PartPose.offset(0.0f, 16.0f, 0.0f));

        return LayerDefinition.create(mesh, 32, 32);
    }
}
