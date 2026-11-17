package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.VicEntityModel;
import com.vicmod.config.VicPersona;
import com.vicmod.entity.VicEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

public class VicEntityRenderer extends MobEntityRenderer<VicEntity, VicEntityModel> {
    private static final Map<VicPersona, Identifier[]> PERSONA_STAGE_TEXTURES = new EnumMap<>(VicPersona.class);

    static {
        PERSONA_STAGE_TEXTURES.put(VicPersona.YELLOW, buildStageTextures(""));
        PERSONA_STAGE_TEXTURES.put(VicPersona.BLUE, buildStageTextures("blue_"));
        PERSONA_STAGE_TEXTURES.put(VicPersona.GREEN, buildStageTextures("green_"));
        PERSONA_STAGE_TEXTURES.put(VicPersona.PINK, buildStageTextures("pink_"));
        PERSONA_STAGE_TEXTURES.put(VicPersona.PURPLE, buildStageTextures("purple_"));
    }

    private static Identifier[] buildStageTextures(String prefix) {
        return new Identifier[]{
                Identifier.of(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage0.png"),
                Identifier.of(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage1.png"),
                Identifier.of(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage2.png"),
                Identifier.of(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage3.png")
        };
    }

    public VicEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new VicEntityModel(context.getPart(VicModClient.VIC_LAYER)), 0.45f);
    }

    @Override
    public Identifier getTexture(VicEntity entity) {
        VicPersona persona = entity.getPersona();
        Identifier[] stages = PERSONA_STAGE_TEXTURES.getOrDefault(persona, PERSONA_STAGE_TEXTURES.get(VicPersona.YELLOW));
        int stage = Math.max(0, Math.min(stages.length - 1, entity.getCorruptionStage()));
        return stages[stage];
    }
}
