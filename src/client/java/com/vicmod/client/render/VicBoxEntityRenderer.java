package com.vicmod.client.render;

import com.vicmod.VicMod;
import com.vicmod.client.VicModClient;
import com.vicmod.client.model.VicBoxEntityModel;
import com.vicmod.entity.VicBoxEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class VicBoxEntityRenderer extends MobEntityRenderer<VicBoxEntity, VicBoxEntityModel> {
    private static final Identifier TEXTURE = Identifier.of(VicMod.MOD_ID, "textures/entity/vic_box.png");

    public VicBoxEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new VicBoxEntityModel(context.getPart(VicModClient.VIC_BOX_LAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(VicBoxEntity entity) {
        return TEXTURE;
    }
}
