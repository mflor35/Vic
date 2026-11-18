package com.vicmod.entity;

import com.vicmod.item.FlashlightItem;
import com.vicmod.registry.ModItems;
import com.vicmod.state.VicWorldState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ShadowWispEntity extends PathfinderMob {
    private int lifetimeTicks = 0;

    public ShadowWispEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl<>(this, 15, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createWispAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 28.0f));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        dissolve(level, true);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        this.lifetimeTicks++;

        if (this.level().isClientSide()) {
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(
                        ParticleTypes.SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.6,
                        this.getY() + this.random.nextDouble() * 0.8,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.6,
                        0.0, 0.01, 0.0
                );
            }
            if (this.random.nextInt(6) == 0) {
                this.level().addParticle(
                        ParticleTypes.SCULK_SOUL,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.4,
                        this.getY() + this.random.nextDouble() * 0.6,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.4,
                        0.0, 0.02, 0.0
                );
            }
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.lifetimeTicks > 1200) {
            dissolve(serverLevel, false);
            return;
        }

        // Gentle floating bob
        this.setDeltaMovement(this.getDeltaMovement().x * 0.8, Math.sin(this.tickCount * 0.08) * 0.02, this.getDeltaMovement().z * 0.8);

        // React to nearest player
        Player player = serverLevel.getNearestPlayer(this, 32.0);
        if (player != null) {
            double distSq = this.distanceToSqr(player);
            VicWorldState state = VicWorldState.getServerState(serverLevel.getServer());
            int karma = state.getKarma();

            boolean hasFlashlight = (player.getMainHandItem().is(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getMainHandItem()))
                    || (player.getOffhandItem().is(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getOffhandItem()));

            // Dissolve if player approaches closely, shines flashlight, or has high karma
            if (hasFlashlight && distSq < 20.0 * 20.0) {
                dissolve(serverLevel, true);
                return;
            }

            if (distSq < 5.0 * 5.0 || karma >= 30 && distSq < 14.0 * 14.0) {
                dissolve(serverLevel, true);
                return;
            }

            // Stare and stay at a distance (12 to 20 blocks away)
            if (distSq < 10.0 * 10.0) {
                Vec3 away = this.position().subtract(player.position()).normalize().scale(0.15);
                this.setDeltaMovement(away.x, 0.02, away.z);
            }
        }
    }

    private void dissolve(ServerLevel world, boolean startled) {
        world.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 18, 0.4, 0.4, 0.4, 0.03);
        world.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        if (startled) {
            world.playSound(null, this.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.7f, 1.4f);
        }
        this.discard();
    }
}
