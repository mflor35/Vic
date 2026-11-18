package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.VicEntityModel;
import com.vicmod.config.VicPersona;
import com.vicmod.entity.VicEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

public class VicEntityRenderer extends MobRenderer<VicEntity, VicRenderState, VicEntityModel> {
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
                Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage0.png"),
                Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage1.png"),
                Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage2.png"),
                Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "textures/entity/vic_" + prefix + "stage3.png")
        };
    }

    public VicEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new VicEntityModel(context.bakeLayer(VicModClient.VIC_LAYER)), 0.45f);
    }

    @Override
    public VicRenderState createRenderState() {
        return new VicRenderState();
    }

    @Override
    public void extractRenderState(VicEntity entity, VicRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.persona = entity.getPersona();
        state.corruptionStage = entity.getCorruptionStage();
    }

    @Override
    public Identifier getTextureLocation(VicRenderState state) {
        Identifier[] stages = PERSONA_STAGE_TEXTURES.getOrDefault(state.persona, PERSONA_STAGE_TEXTURES.get(VicPersona.YELLOW));
        int stage = Math.max(0, Math.min(stages.length - 1, state.corruptionStage));
        return stages[stage];
    }
}
