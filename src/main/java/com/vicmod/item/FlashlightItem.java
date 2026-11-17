package com.vicmod.item;

import com.vicmod.entity.VicDemonEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class FlashlightItem extends Item {
    private static final String ACTIVE_KEY = "FlashlightActive";

    public FlashlightItem(Settings settings) {
        super(settings);
    }

    public static boolean isActive(ItemStack stack) {
        NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        return customData.copyNbt().getBoolean(ACTIVE_KEY);
    }

    public static void setActive(ItemStack stack, boolean active) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        nbt.putBoolean(ACTIVE_KEY, active);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            boolean nextState = !isActive(stack);
            setActive(stack, nextState);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.7f, nextState ? 1.4f : 0.9f);
            user.sendMessage(
                    Text.literal("[Flashlight] ").formatted(Formatting.YELLOW)
                            .append(Text.literal(nextState ? "ON" : "OFF").formatted(nextState ? Formatting.GREEN : Formatting.RED)),
                    true
            );
            if (!nextState) {
                user.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && world instanceof ServerWorld serverWorld && entity instanceof PlayerEntity player) {
            boolean holding = selected || player.getOffHandStack() == stack;
            if (holding && isActive(stack)) {
                player.removeStatusEffect(StatusEffects.DARKNESS);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, false, false, false));

                // Check if the player's flashlight beam is aimed at The Vic Demon
                if (serverWorld.getTime() % 5 == 0) {
                    stunTargetedDemon(serverWorld, player);
                }
            }
        }
    }

    private void stunTargetedDemon(ServerWorld world, PlayerEntity player) {
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f).normalize();

        List<VicDemonEntity> demons = world.getEntitiesByClass(
                VicDemonEntity.class,
                player.getBoundingBox().expand(22.0),
                demon -> demon.isAlive() && player.canSee(demon)
        );

        for (VicDemonEntity demon : demons) {
            Vec3d toDemon = demon.getPos().add(0, demon.getHeight() * 0.65, 0).subtract(eyePos);
            double dist = toDemon.length();
            if (dist > 0.1 && dist <= 22.0) {
                double dot = lookVec.dotProduct(toDemon.normalize());
                if (dot >= 0.84) {
                    demon.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 3, false, false, true));
                    demon.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 50, 1, false, false, true));
                    demon.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 50, 0, false, false, true));

                    Vec3d push = toDemon.normalize().multiply(0.18);
                    demon.addVelocity(push.x, 0.05, push.z);
                    demon.velocityModified = true;

                    world.spawnParticles(ParticleTypes.END_ROD, demon.getX(), demon.getEyeY(), demon.getZ(), 5, 0.25, 0.25, 0.25, 0.03);
                    if (world.getTime() % 20 == 0) {
                        world.playSound(null, demon.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.8f, 1.4f);
                    }
                }
            }
        }
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return isActive(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        boolean on = isActive(stack);
        tooltip.add(Text.literal("Status: " + (on ? "ON" : "OFF")).formatted(on ? Formatting.GREEN : Formatting.GRAY));
        tooltip.add(Text.literal("Right-click to toggle beam & pierce True Darkness").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Aim beam at The Vic Demon to blind and interrupt it.").formatted(Formatting.YELLOW));
    }
}
