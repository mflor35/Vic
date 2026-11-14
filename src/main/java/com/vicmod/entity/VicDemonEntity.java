package com.vicmod.entity;

import com.vicmod.state.VicWorldState;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SpiderNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class VicDemonEntity extends HostileEntity {
    private static final TrackedData<Byte> CLIMBING_FLAG = DataTracker.registerData(VicDemonEntity.class, TrackedDataHandlerRegistry.BYTE);

    public VicDemonEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
        this.experiencePoints = 50;
    }

    public static DefaultAttributeContainer.Builder createDemonAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 160.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.38)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 11.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 72.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.75)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.5);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new SpiderNavigation(this, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(CLIMBING_FLAG, (byte) 0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.18, true));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.85));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 48.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            this.setClimbingWall(this.horizontalCollision);

            if (this.getWorld() instanceof ServerWorld serverWorld) {
                VicWorldState state = VicWorldState.getServerState(serverWorld.getServer());
                if (!state.tryClaimSingletonVic(serverWorld.getServer(), this)) {
                    PlayerEntity nearest = serverWorld.getClosestPlayer(this, 16.0);
                    if (nearest != null) {
                        nearest.sendMessage(
                                Text.literal("[Vic System] ").formatted(Formatting.RED, Formatting.BOLD)
                                        .append(Text.literal("Only one Vic character is allowed per world!").formatted(Formatting.YELLOW)),
                                true
                        );
                    }
                    this.discard();
                    return;
                }

                // Break nearby wooden doors if chasing a player
                if (this.getTarget() != null && this.age % 20 == 0) {
                    breakNearbyDoors(serverWorld);
                }

                // Inflict darkness / heartbeat ambiance on nearby players
                if (this.age % 60 == 0) {
                    for (ServerPlayerEntity player : serverWorld.getPlayers(p -> p.squaredDistanceTo(this) <= 28.0 * 28.0 && !p.isCreative() && !p.isSpectator())) {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false, true));
                        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.0f, 0.85f);
                    }
                }
            }
        } else {
            if (this.random.nextInt(3) == 0) {
                this.getWorld().addParticle(
                        ParticleTypes.LARGE_SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.9,
                        this.getY() + this.random.nextDouble() * 3.4,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.9,
                        0.0, 0.02, 0.0
                );
            }
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
            RemovalReason reason = this.getRemovalReason();
            if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
                VicWorldState.getServerState(serverWorld.getServer()).releaseSingletonVic(this);
            }
        }
    }

    private void breakNearbyDoors(ServerWorld world) {
        BlockPos base = this.getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = base.add(dx, dy, dz);
                    BlockState state = world.getBlockState(check);
                    if (state.getBlock() instanceof DoorBlock) {
                        world.breakBlock(check, true, this);
                        world.playSound(null, check, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.HOSTILE, 1.2f, 0.7f);
                    }
                }
            }
        }
    }

    @Override
    public boolean isClimbing() {
        return this.isClimbingWall();
    }

    public boolean isClimbingWall() {
        return (this.dataTracker.get(CLIMBING_FLAG) & 1) != 0;
    }

    public void setClimbingWall(boolean climbing) {
        byte b = this.dataTracker.get(CLIMBING_FLAG);
        if (climbing) {
            b = (byte) (b | 1);
        } else {
            b = (byte) (b & ~1);
        }
        this.dataTracker.set(CLIMBING_FLAG, b);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WARDEN_DEATH;
    }
}
