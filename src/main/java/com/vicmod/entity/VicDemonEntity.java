package com.vicmod.entity;

import com.vicmod.item.FlashlightItem;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.network.VicSpeechPayload;
import com.vicmod.registry.ModItems;
import com.vicmod.state.VicWorldState;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SpiderNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class VicDemonEntity extends HostileEntity {
    private static final TrackedData<Byte> CLIMBING_FLAG = DataTracker.registerData(VicDemonEntity.class, TrackedDataHandlerRegistry.BYTE);

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("THE VIC DEMON — TRIAL EXPIRED").formatted(Formatting.DARK_RED, Formatting.BOLD),
            BossBar.Color.RED,
            BossBar.Style.NOTCHED_10
    );

    private int beamChargeTicks = 0;
    private int beamCooldownTicks = 100;
    private int slamCooldownTicks = 140;

    public VicDemonEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
        this.experiencePoints = 250;
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
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
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

                // Update Boss Bar health & stun state
                this.bossBar.setPercent(Math.clamp(this.getHealth() / this.getMaxHealth(), 0.0f, 1.0f));
                boolean isStunned = this.hasStatusEffect(StatusEffects.WEAKNESS) && this.hasStatusEffect(StatusEffects.GLOWING);
                if (isStunned) {
                    this.bossBar.setColor(BossBar.Color.YELLOW);
                    this.bossBar.setName(Text.literal("THE VIC DEMON [BLINDED BY FLASHLIGHT]").formatted(Formatting.YELLOW, Formatting.BOLD));
                    if (this.beamChargeTicks > 0) {
                        this.beamChargeTicks = 0;
                        this.beamCooldownTicks = 100;
                    }
                } else if (this.beamChargeTicks > 0) {
                    this.bossBar.setColor(BossBar.Color.PURPLE);
                    this.bossBar.setName(Text.literal("THE VIC DEMON [CHARGING CORRUPTION BEAM]").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
                } else {
                    this.bossBar.setColor(BossBar.Color.RED);
                    this.bossBar.setName(Text.literal("THE VIC DEMON — TRIAL EXPIRED").formatted(Formatting.DARK_RED, Formatting.BOLD));
                }

                LivingEntity target = this.getTarget();
                if (target != null && target.isAlive()) {
                    // Break nearby wooden doors if chasing a player
                    if (this.age % 20 == 0) {
                        breakNearbyDoors(serverWorld);
                    }

                    if (!isStunned) {
                        tickBossAbilities(serverWorld, target);
                    }
                } else {
                    this.beamChargeTicks = 0;
                }

                // Inflict darkness / heartbeat ambiance on nearby players
                if (this.age % 60 == 0) {
                    for (ServerPlayerEntity player : serverWorld.getPlayers(p -> p.squaredDistanceTo(this) <= 28.0 * 28.0 && !p.isCreative() && !p.isSpectator())) {
                        boolean hasFlashlightOn = (player.getMainHandStack().isOf(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getMainHandStack()))
                                || (player.getOffHandStack().isOf(ModItems.FLASHLIGHT) && FlashlightItem.isActive(player.getOffHandStack()));
                        if (!hasFlashlightOn) {
                            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false, true));
                        }
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

    private void tickBossAbilities(ServerWorld serverWorld, LivingEntity target) {
        double distSq = this.squaredDistanceTo(target);

        if (this.beamCooldownTicks > 0) {
            this.beamCooldownTicks--;
        }
        if (this.slamCooldownTicks > 0) {
            this.slamCooldownTicks--;
        }

        // 1. Ranged Corruption Beam (telegraphed over 30 ticks)
        if (this.beamChargeTicks > 0) {
            this.beamChargeTicks++;
            this.getLookControl().lookAt(target, 30.0f, 30.0f);
            serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getEyeY(), this.getZ(), 4, 0.35, 0.35, 0.35, 0.04);

            if (this.beamChargeTicks >= 30) {
                fireCorruptionBeam(serverWorld, target);
                this.beamChargeTicks = 0;
                this.beamCooldownTicks = 130;
            }
            return;
        }

        if (this.beamCooldownTicks <= 0 && distSq >= 5.0 * 5.0 && distSq <= 24.0 * 24.0 && this.canSee(target)) {
            this.beamChargeTicks = 1;
            serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.75f);
            if (target instanceof ServerPlayerEntity player) {
                player.sendMessage(
                        Text.literal("[!] ").formatted(Formatting.DARK_RED, Formatting.BOLD)
                                .append(Text.literal("The Vic Demon is charging a Corruption Beam! Aim your Flashlight or take cover!").formatted(Formatting.RED)),
                        true
                );
            }
            return;
        }

        // 2. Shadow Leap & Knockback Slam
        if (this.slamCooldownTicks <= 0 && distSq >= 3.5 * 3.5 && distSq <= 11.0 * 11.0 && this.isOnGround()) {
            this.slamCooldownTicks = 160;
            Vec3d dir = target.getPos().subtract(this.getPos()).normalize();
            this.setVelocity(dir.x * 0.95, 0.52, dir.z * 0.95);
            this.velocityDirty = true;
            serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 1.5f, 0.6f);
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 3, 0.5, 0.1, 0.5, 0.02);

            for (PlayerEntity nearby : serverWorld.getPlayers(p -> p.squaredDistanceTo(this) <= 5.0 * 5.0 && p.isAlive())) {
                Vec3d kb = nearby.getPos().subtract(this.getPos()).normalize();
                nearby.addVelocity(kb.x * 0.75, 0.38, kb.z * 0.75);
                nearby.velocityModified = true;
            }
        }
    }

    private void fireCorruptionBeam(ServerWorld serverWorld, LivingEntity target) {
        Vec3d start = new Vec3d(this.getX(), this.getEyeY() - 0.2, this.getZ());
        Vec3d end = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d delta = end.subtract(start);
        int steps = Math.max(1, (int) (delta.length() * 2.0));
        Vec3d stepVec = delta.multiply(1.0 / steps);

        for (int i = 0; i <= steps; i++) {
            Vec3d pt = start.add(stepVec.multiply(i));
            serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.0f, 0.85f);

        if (this.canSee(target) && this.squaredDistanceTo(target) <= 28.0 * 28.0) {
            target.damage(serverWorld.getDamageSources().sonicBoom(this), 6.0f);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0));
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
            this.bossBar.clearPlayers();

            // Drop Vic's Core trophy item
            this.dropStack(new ItemStack(ModItems.VIC_CORE, 1));

            serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 1.5, this.getZ(), 80, 0.8, 1.2, 0.8, 0.25);
            serverWorld.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.5, this.getZ(), 50, 0.6, 1.0, 0.6, 0.15);
            serverWorld.playSound(null, this.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.5f, 1.0f);
            serverWorld.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);

            VicWorldState state = VicWorldState.getServerState(serverWorld.getServer());
            state.setDemonAwakened(false);
            state.setDemonDefeated(true);
            state.setCountdownActive(false);
            state.releaseSingletonVic(this);
            VicHudStatePayload.broadcast(serverWorld.getServer(), state);

            String finalSpeech = "Shell shattered... System offline. You survived the trial, bestie. Thank you for playing with Vic.";
            for (ServerPlayerEntity player : serverWorld.getServer().getPlayerManager().getPlayerList()) {
                player.removeStatusEffect(StatusEffects.DARKNESS);
                player.removeStatusEffect(StatusEffects.WITHER);
                player.sendMessage(
                        Text.literal("[TRIAL SURVIVED] ").formatted(Formatting.GOLD, Formatting.BOLD)
                                .append(Text.literal("The Vic Demon has been defeated! Obtained ").formatted(Formatting.GREEN))
                                .append(Text.literal("[Vic's Core]").formatted(Formatting.AQUA, Formatting.BOLD)),
                        false
                );
                player.sendMessage(
                        Text.literal("<Vic> ").formatted(Formatting.YELLOW, Formatting.BOLD)
                                .append(Text.literal(finalSpeech).formatted(Formatting.WHITE)),
                        false
                );
                VicSpeechPayload.sendTo(player, finalSpeech, 0);
            }
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        this.bossBar.clearPlayers();
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
