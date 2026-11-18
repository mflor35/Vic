package com.vicmod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class VicCoreItem extends Item {
    public VicCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            user.removeEffect(MobEffects.DARKNESS);
            user.removeEffect(MobEffects.WITHER);
            user.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60, 0, false, false, true));
            user.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 15, 1, false, true, true));
            user.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 30, 1, false, true, true));
            user.getCooldowns().addCooldown(stack, 20 * 25);

            serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, user.getX(), user.getY() + 1.0, user.getZ(), 25, 0.4, 0.5, 0.4, 0.12);
            serverLevel.playSound(null, user.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.35f);
            user.sendSystemMessage(
                    Component.literal("[Vic's Core] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("The stabilized core pulses warmly, banishing the darkness!").withStyle(ChatFormatting.YELLOW))
            );
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("The purified remnant of Vic-Unit-01.")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        tooltip.accept(Component.literal("Right-click to purge darkness and gain powerful boons.")
                .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("Permanent proof that your companion was saved.")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
