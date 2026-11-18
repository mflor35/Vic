package com.vicmod.item;

import com.vicmod.entity.VicDemonEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

public class FlashlightItem extends Item {
    private static final String ACTIVE_KEY = "FlashlightActive";

    public FlashlightItem(Properties properties) {
        super(properties);
    }

    public static boolean isActive(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getBooleanOr(ACTIVE_KEY, false);
    }

    public static void setActive(ItemStack stack, boolean active) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ACTIVE_KEY, active));
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!level.isClientSide()) {
            boolean nextState = !isActive(stack);
            setActive(stack, nextState);
            level.playSound(null, user.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.7f, nextState ? 1.4f : 0.9f);
            user.sendSystemMessage(
                    Component.literal("[Flashlight] ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(nextState ? "ON" : "OFF").withStyle(nextState ? ChatFormatting.GREEN : ChatFormatting.RED))
            );
            if (!nextState) {
                user.removeEffect(MobEffects.NIGHT_VISION);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel serverLevel, Entity entity, EquipmentSlot slot) {
        if (entity instanceof Player player) {
            boolean holding = player.getMainHandItem() == stack || player.getOffhandItem() == stack;
            if (holding && isActive(stack)) {
                player.removeEffect(MobEffects.DARKNESS);
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, false));

                // Check if the player's flashlight beam is aimed at The Vic Demon
                if (serverLevel.getGameTime() % 5 == 0) {
                    stunTargetedDemon(serverLevel, player);
                }
            }
        }
    }

    private void stunTargetedDemon(ServerLevel level, Player player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f).normalize();

        List<VicDemonEntity> demons = level.getEntitiesOfClass(
                VicDemonEntity.class,
                player.getBoundingBox().inflate(22.0),
                demon -> demon.isAlive() && player.hasLineOfSight(demon)
        );

        for (VicDemonEntity demon : demons) {
            Vec3 toDemon = demon.position().add(0, demon.getBbHeight() * 0.65, 0).subtract(eyePos);
            double dist = toDemon.length();
            if (dist > 0.1 && dist <= 22.0) {
                double dot = lookVec.dot(toDemon.normalize());
                if (dot >= 0.84) {
                    demon.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3, false, false, true));
                    demon.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 1, false, false, true));
                    demon.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false, true));

                    Vec3 push = toDemon.normalize().scale(0.18);
                    demon.push(push.x, 0.05, push.z);

                    level.sendParticles(ParticleTypes.END_ROD, demon.getX(), demon.getEyeY(), demon.getZ(), 5, 0.25, 0.25, 0.25, 0.03);
                    if (level.getGameTime() % 20 == 0) {
                        level.playSound(null, demon.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8f, 1.4f);
                    }
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        boolean on = isActive(stack);
        tooltip.accept(Component.literal("Status: " + (on ? "ON" : "OFF")).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Right-click to toggle beam & pierce True Darkness").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("Aim beam at The Vic Demon to blind and interrupt it.").withStyle(ChatFormatting.YELLOW));
    }
}
