package com.vicmod.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class VicCoreItem extends Item {
    public VicCoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            user.removeStatusEffect(StatusEffects.DARKNESS);
            user.removeStatusEffect(StatusEffects.WITHER);
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 20 * 60, 0, false, false, true));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 15, 1, false, true, true));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 30, 1, false, true, true));
            user.getItemCooldownManager().set(this, 20 * 25);

            serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, user.getX(), user.getY() + 1.0, user.getZ(), 25, 0.4, 0.5, 0.4, 0.12);
            serverWorld.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.35f);
            user.sendMessage(
                    Text.literal("[Vic's Core] ").formatted(Formatting.GOLD, Formatting.BOLD)
                            .append(Text.literal("The stabilized core pulses warmly, banishing the darkness!").formatted(Formatting.YELLOW)),
                    true
            );
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Trophy — Recovered from The Vic Demon").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Right-click to purge Darkness and surge with light.").formatted(Formatting.GRAY));
    }
}
