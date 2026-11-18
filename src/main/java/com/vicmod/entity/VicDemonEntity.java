package com.vicmod.entity;

import com.vicmod.item.FlashlightItem;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.network.VicSpeechPayload;
import com.vicmod.registry.ModItems;
import com.vicmod.state.VicWorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class VicDemonEntity extends Monster {
    private static final EntityDataAccessor<Byte> CLIMBING_FLAG = SynchedEntityData.defineId(VicDemonEntity.class, EntityDataSerializers.BYTE);

    private final ServerBossEvent bossBar = new ServerBossEvent(
            UUID.randomUUID(),
            Component.literal("THE VIC DEMON — TRIAL EXPIRED").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_10
    );

    private int beamChargeTicks = 0;
    private int beamCooldownTicks = 100;
    private int slamCooldownTicks = 140;

    public VicDemonEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.xpReward = 250;
    }

    public static AttributeSupplier.Builder createDemonAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0)
                .add(Attributes.MOVEMENT_SPEED, 0.38)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.FOLLOW_RANGE, 72.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CLIMBING_FLAG, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.18, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.85));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 48.0f));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.setClimbingWall(this.horizontalCollision);

            if (this.level() instanceof ServerLevel serverLevel) {
                VicWorldState state = VicWorldState.getServerState(serverLevel.getServer());
                if (!state.tryClaimSingletonVic(serverLevel.getServer(), this)) {
                    Player nearest = serverLevel.getNearestPlayer(this, 16.0);
                    if (nearest != null) {
                        nearest.sendOverlayMessage(
                                Component.literal("[Vic System] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                        .append(Component.literal("Only one Vic character is allowed per world!").withStyle(ChatFormatting.YELLOW))
                        );
                    }
                    this.discard();
                    return;
                }

                this.bossBar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));
                boolean isStunned = this.hasEffect(MobEffects.WEAKNESS) && this.hasEffect(MobEffects.GLOWING);
                if (isStunned) {
                    this.bossBar.setColor(BossEvent.BossBarColor.YELLOW);
                    this.bossBar.setName(Component.literal("THE VIC DEMON [BLINDED BY FLASHLIGHT]").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                    if (this.beamChargeTicks > 0) {
                        this.beamChargeTicks = 0;
                        this.beamCooldownTicks = 100;
                    }
                } else if (this.beamChargeTicks > 0) {
                    this.bossBar.setColor(BossEvent.BossBarColor.PURPLE);
                    this.bossBar.setName(Component.literal("THE VIC DEMON [CHARGING CORRUPTION BEAM]").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                } else {
                    this.bossBar.setColor(BossEvent.BossBarColor.RED);
                    this.bossBar.setName(Component.literal("THE VIC DEMON — TRIAL EXPIRED").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                }

                LivingEntity target = this.getTarget();
                if (target != null && target.isAlive()) {
                    if (this.tickCount % 20 == 0) {
                        breakNearbyDoors(serverLevel);
                    }

                    if (!isStunned) {
                        tickBossAbilities(serverLevel, target);
                    }
                } else {
                    this.beamChargeTicks = 0;
                }

                if (this.tickCount % 60 == 0) {
                    for (ServerPlayer player : serverLevel.players()) {
                        if (player.distanceToSqr(this) <= 28.0 * 28.0 && !player.isCreative() && !player.isSpectator()) {
                            boolean hasFlashlightOn = (player.getMainHandItem().is(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getMainHandItem()))
                                    || (player.getOffhandItem().is(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getOffhandItem()));
                            if (!hasFlashlightOn) {
                                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, true));
                            }
                            serverLevel.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0f, 0.85f);
                        }
                    }
                }
            }
        } else {
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(
                        ParticleTypes.LARGE_SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.9,
                        this.getY() + this.random.nextDouble() * 3.4,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.9,
                        0.0, 0.02, 0.0
                );
            }
        }
    }

    private void tickBossAbilities(ServerLevel serverLevel, LivingEntity target) {
        double distSq = this.distanceToSqr(target);

        if (this.beamCooldownTicks > 0) {
            this.beamCooldownTicks--;
        }
        if (this.slamCooldownTicks > 0) {
            this.slamCooldownTicks--;
        }

        if (this.beamChargeTicks > 0) {
            this.beamChargeTicks++;
            this.getLookControl().setLookAt(target, 30.0f, 30.0f);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getEyeY(), this.getZ(), 4, 0.35, 0.35, 0.35, 0.04);

            if (this.beamChargeTicks >= 30) {
                fireCorruptionBeam(serverLevel, target);
                this.beamChargeTicks = 0;
                this.beamCooldownTicks = 130;
            }
            return;
        }

        if (this.beamCooldownTicks <= 0 && distSq >= 5.0 * 5.0 && distSq <= 24.0 * 24.0 && this.hasLineOfSight(target)) {
            this.beamChargeTicks = 1;
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0f, 0.75f);
            if (target instanceof ServerPlayer player) {
                player.sendOverlayMessage(
                        Component.literal("[!] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                                .append(Component.literal("The Vic Demon is charging a Corruption Beam! Aim your Flashlight or take cover!").withStyle(ChatFormatting.RED))
                );
            }
            return;
        }

        if (this.slamCooldownTicks <= 0 && distSq >= 3.5 * 3.5 && distSq <= 11.0 * 11.0 && this.onGround()) {
            this.slamCooldownTicks = 160;
            Vec3 dir = target.position().subtract(this.position()).normalize();
            this.setDeltaMovement(dir.x * 0.95, 0.52, dir.z * 0.95);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.5f, 0.6f);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 3, 0.5, 0.1, 0.5, 0.02);

            for (ServerPlayer nearby : serverLevel.players()) {
                if (nearby.distanceToSqr(this) <= 5.0 * 5.0 && nearby.isAlive()) {
                    Vec3 kb = nearby.position().subtract(this.position()).normalize();
                    nearby.push(kb.x * 0.75, 0.38, kb.z * 0.75);
                }
            }
        }
    }

    private void fireCorruptionBeam(ServerLevel serverLevel, LivingEntity target) {
        Vec3 start = new Vec3(this.getX(), this.getEyeY() - 0.2, this.getZ());
        Vec3 end = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 delta = end.subtract(start);
        int steps = Math.max(1, (int) (delta.length() * 2.0));
        Vec3 stepVec = delta.scale(1.0 / steps);

        for (int i = 0; i <= steps; i++) {
            Vec3 pt = start.add(stepVec.scale(i));
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0f, 0.85f);

        if (this.hasLineOfSight(target) && this.distanceToSqr(target) <= 28.0 * 28.0) {
            target.hurtServer(serverLevel, serverLevel.damageSources().sonicBoom(this), 6.0f);
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            this.bossBar.removeAllPlayers();

            this.spawnAtLocation(serverLevel, new ItemStack(ModItems.VIC_CORE, 1));

            serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 1.5, this.getZ(), 80, 0.8, 1.2, 0.8, 0.25);
            serverLevel.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.5, this.getZ(), 50, 0.6, 1.0, 0.6, 0.15);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.5f, 1.0f);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.6f);

            VicWorldState state = VicWorldState.getServerState(serverLevel.getServer());
            state.setDemonAwakened(false);
            state.setDemonDefeated(true);
            state.setCountdownActive(false);
            state.releaseSingletonVic(this);
            VicHudStatePayload.broadcast(serverLevel.getServer(), state);

            String finalSpeech = "Shell shattered... System offline. You survived the trial, bestie. Thank you for playing with Vic.";
            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                player.removeEffect(MobEffects.DARKNESS);
                player.removeEffect(MobEffects.WITHER);
                player.sendSystemMessage(
                        Component.literal("[TRIAL SURVIVED] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                .append(Component.literal("The Vic Demon has been defeated! Obtained ").withStyle(ChatFormatting.GREEN))
                                .append(Component.literal("[Vic's Core]").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                );
                player.sendSystemMessage(
                        Component.literal("<Vic> ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal(finalSpeech).withStyle(ChatFormatting.WHITE))
                );
                VicSpeechPayload.sendTo(player, finalSpeech, 0);
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        this.bossBar.removeAllPlayers();
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
                VicWorldState.getServerState(serverLevel.getServer()).releaseSingletonVic(this);
            }
        }
    }

    private void breakNearbyDoors(ServerLevel world) {
        BlockPos base = this.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = base.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(check);
                    if (state.getBlock() instanceof DoorBlock) {
                        world.destroyBlock(check, true, this, 512);
                        world.playSound(null, check, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.2f, 0.7f);
                    }
                }
            }
        }
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbingWall();
    }

    public boolean isClimbingWall() {
        return (this.entityData.get(CLIMBING_FLAG) & 1) != 0;
    }

    public void setClimbingWall(boolean climbing) {
        byte b = this.entityData.get(CLIMBING_FLAG);
        if (climbing) {
            b = (byte) (b | 1);
        } else {
            b = (byte) (b & ~1);
        }
        this.entityData.set(CLIMBING_FLAG, b);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }
}
