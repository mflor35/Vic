package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.VicEntityModel;
import com.vicmod.entity.VicEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class VicEntityRenderer extends MobEntityRenderer<VicEntity, VicEntityModel> {
    private static final Identifier[] STAGE_TEXTURES = new Identifier[]{
            Identifier.of(VicMod.MOD_ID, "textures/entity/vic_stage0.png"),
            Identifier.of(VicMod.MOD_ID, "textures/entity/vic_stage1.png"),
            Identifier.of(VicMod.MOD_ID, "textures/entity/vic_stage2.png"),
            Identifier.of(VicMod.MOD_ID, "textures/entity/vic_stage3.png")
    };

    public VicEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new VicEntityModel(context.getPart(VicModClient.VIC_LAYER)), 0.45f);
    }

    @Override
    public Identifier getTexture(VicEntity entity) {
        int stage = Math.max(0, Math.min(STAGE_TEXTURES.length - 1, entity.getCorruptionStage()));
        return STAGE_TEXTURES[stage];
    }
}
