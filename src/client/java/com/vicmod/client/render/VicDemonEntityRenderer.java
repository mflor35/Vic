package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.VicDemonEntityModel;
import com.vicmod.entity.VicDemonEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class VicDemonEntityRenderer extends MobEntityRenderer<VicDemonEntity, VicDemonEntityModel> {
    private static final Identifier TEXTURE = Identifier.of(VicMod.MOD_ID, "textures/entity/vic_demon.png");

    public VicDemonEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new VicDemonEntityModel(context.getPart(VicModClient.VIC_DEMON_LAYER)), 0.7f);
    }

    @Override
    public Identifier getTexture(VicDemonEntity entity) {
        return TEXTURE;
    }
}
