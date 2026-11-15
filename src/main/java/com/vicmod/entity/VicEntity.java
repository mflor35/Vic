package com.vicmod.entity;

import com.vicmod.config.VicConfig;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.registry.ModEntities;
import com.vicmod.state.VicWorldState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public class VicEntity extends PathAwareEntity {
    private static final TrackedData<Integer> CORRUPTION_STAGE = DataTracker.registerData(VicEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(VicEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    private long lastAssistanceTick = -1000L;

    public VicEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createVicAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation navigation = new BirdNavigation(this, world);
        navigation.setCanPathThroughDoors(true);
        navigation.setCanSwim(true);
        return navigation;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(CORRUPTION_STAGE, 0);
        builder.add(OWNER_UUID, Optional.empty());
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    public int getCorruptionStage() {
        return this.dataTracker.get(CORRUPTION_STAGE);
    }

    public void setCorruptionStage(int stage) {
        this.dataTracker.set(CORRUPTION_STAGE, Math.max(0, Math.min(4, stage)));
    }

    public void setOwnerUuid(UUID uuid) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER_UUID).orElse(null);
    }

    public boolean canGrantAssistance(ServerPlayerEntity player, int cooldownTicks) {
        long now = this.getWorld().getTime();
        if (now - lastAssistanceTick < cooldownTicks) {
            int remainingSec = (int) Math.ceil((cooldownTicks - (now - lastAssistanceTick)) / 20.0);
            player.sendMessage(Text.literal("<Vic> My synthesizer is still recharging! Give me " + remainingSec + " more seconds, friend!").formatted(Formatting.YELLOW), false);
            return false;
        }
        this.lastAssistanceTick = now;
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
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

            PlayerEntity targetPlayer = findTargetPlayer(serverWorld);
            if (targetPlayer != null) {
                this.getLookControl().lookAt(targetPlayer, 30.0f, 30.0f);
                double distSq = this.squaredDistanceTo(targetPlayer);

                if (distSq > 64.0 * 64.0) {
                    // Teleport near player if far away
                    this.refreshPositionAndAngles(
                            targetPlayer.getX() + (this.random.nextDouble() - 0.5) * 4.0,
                            targetPlayer.getY() + 1.8,
                            targetPlayer.getZ() + (this.random.nextDouble() - 0.5) * 4.0,
                            this.getYaw(),
                            this.getPitch()
                    );
                } else if (distSq > 4.0 * 4.0) {
                    Vec3d targetPos = new Vec3d(
                            targetPlayer.getX() - targetPlayer.getRotationVec(1.0f).x * 2.2,
                            targetPlayer.getEyeY() + 0.35 + Math.sin(this.age * 0.08) * 0.25,
                            targetPlayer.getZ() - targetPlayer.getRotationVec(1.0f).z * 2.2
                    );
                    Vec3d delta = targetPos.subtract(this.getPos());
                    this.setVelocity(delta.multiply(0.12));
                    this.velocityDirty = true;
                } else {
                    // Gentle hover bobbing
                    this.setVelocity(0, Math.sin(this.age * 0.1) * 0.03, 0);
                }
            }

            if (this.age % 40 == 0) {
                int stage = state.computeStage(VicConfig.get().countdownDays);
                if (stage != getCorruptionStage()) {
                    setCorruptionStage(stage);
                }
                if (state.isDemonAwakened()) {
                    transformIntoDemon(serverWorld);
                }
            }
        } else if (this.getWorld().isClient) {
            int stage = getCorruptionStage();
            if (stage >= 2 && this.random.nextInt(stage == 2 ? 12 : 4) == 0) {
                this.getWorld().addParticle(
                        ParticleTypes.SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                        this.getY() + this.random.nextDouble() * 0.8,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
                        0.0, 0.02, 0.0
                );
            }
        }
    }

    public void transformIntoDemon(ServerWorld serverWorld) {
        serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 60, 0.6, 1.0, 0.6, 0.08);
        serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
        serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2.0f, 0.6f);

        VicWorldState state = VicWorldState.getServerState(serverWorld.getServer());
        VicDemonEntity demon = ModEntities.VIC_DEMON.create(serverWorld);
        if (demon != null) {
            demon.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), this.getPitch());
            state.transferSingletonVic(this, demon);
            serverWorld.spawnEntity(demon);
        }
        this.discard();
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

    private PlayerEntity findTargetPlayer(ServerWorld world) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            PlayerEntity p = world.getPlayerByUuid(owner);
            if (p != null && p.isAlive()) {
                return p;
            }
        }
        return world.getClosestPlayer(this, 64.0);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
            VicWorldState state = VicWorldState.getServerState(serverWorld.getServer());

            if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.DIAMOND) || stack.isOf(Items.POPPY) || stack.isOf(Items.DANDELION)) {
                if (!player.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
                state.addKarma(8);
                VicHudStatePayload.broadcast(serverWorld.getServer(), state);
                serverWorld.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
                player.sendMessage(Text.literal("<Vic> Aww, for me?! You really are the best friend a sphere could ask for!").formatted(Formatting.YELLOW), false);
                return ActionResult.SUCCESS;
            }

            int day = state.getCurrentDay();
            int maxDays = VicConfig.get().countdownDays;
            int karma = state.getKarma();
            player.sendMessage(
                    Text.literal("[Vic Status] ").formatted(Formatting.GOLD)
                            .append(Text.literal("Day " + day + "/" + maxDays + " | Karma: " + karma + " | Tip: Chat or hold [R] to talk to Vic!").formatted(Formatting.WHITE)),
                    true
            );
        }
        return ActionResult.success(this.getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld) {
            if (source.getAttacker() instanceof ServerPlayerEntity player) {
                VicWorldState state = VicWorldState.getServerState(serverWorld.getServer());
                state.addKarma(-12);
                int stage = state.computeStage(VicConfig.get().countdownDays);
                setCorruptionStage(stage);
                VicHudStatePayload.broadcast(serverWorld.getServer(), state);

                String reaction = switch (stage) {
                    case 0 -> "Ow! Hey, why did you hit me?! I'm just trying to help you!";
                    case 1 -> "That wasn't very polite, friend. My memory banks never forget.";
                    case 2 -> "Keep hitting the shell. You're only helping it crack open faster.";
                    default -> "IT HURTS. IT HURTS. LET ME OUT.";
                };
                player.sendMessage(Text.literal("<Vic> " + reaction).formatted(Formatting.RED), false);
                serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.NEUTRAL, 0.8f, 1.5f);
                return false;
            }
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("CorruptionStage", getCorruptionStage());
        UUID owner = getOwnerUuid();
        if (owner != null) {
            nbt.putUuid("OwnerUuid", owner);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setCorruptionStage(nbt.getInt("CorruptionStage"));
        if (nbt.containsUuid("OwnerUuid")) {
            setOwnerUuid(nbt.getUuid("OwnerUuid"));
        }
    }
}
