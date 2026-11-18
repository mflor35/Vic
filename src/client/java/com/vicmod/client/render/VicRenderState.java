package com.vicmod.client.render;

import com.vicmod.config.VicPersona;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class VicRenderState extends LivingEntityRenderState {
    public VicPersona persona = VicPersona.YELLOW;
    public int corruptionStage = 0;
}
