package com.vicmod.entity;

import com.vicmod.registry.ModEntities;
import com.vicmod.state.VicWorldState;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.network.VicSpeechPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class VicBoxEntity extends Mob {
    private static final EntityDataAccessor<Boolean> UNBOXING = SynchedEntityData.defineId(VicBoxEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> UNBOX_TICKS = SynchedEntityData.defineId(VicBoxEntity.class, EntityDataSerializers.INT);

    public VicBoxEntity(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createBoxAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(UNBOXING, false);
        builder.define(UNBOX_TICKS, 0);
    }

    public boolean isUnboxing() {
        return this.entityData.get(UNBOXING);
    }

    public int getUnboxTicks() {
        return this.entityData.get(UNBOX_TICKS);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide() && !isUnboxing()) {
            this.entityData.set(UNBOXING, true);
            this.entityData.set(UNBOX_TICKS, 1);
            this.level().playSound(null, this.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.9f);
            player.sendOverlayMessage(Component.literal("[Delivery Box] Unsealing package: VIC-UNIT-01...").withStyle(ChatFormatting.YELLOW));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
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
        }

        if (isUnboxing()) {
            int ticks = getUnboxTicks() + 1;
            this.entityData.set(UNBOX_TICKS, ticks);

            if (this.level().isClientSide()) {
                this.level().addParticle(ParticleTypes.END_ROD,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.9,
                        this.getY() + 0.5 + this.random.nextDouble() * 0.6,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.9,
                        0.0, 0.05, 0.0);
            } else if (this.level() instanceof ServerLevel serverLevel) {
                if (ticks % 10 == 0 && ticks < 60) {
                    serverLevel.playSound(null, this.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8f, 0.8f + (ticks / 60.0f));
                }
                if (ticks >= 60) {
                    completeUnbox(serverLevel);
                }
            }
        }
    }

    private void completeUnbox(ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 0.8, this.getZ(), 35, 0.4, 0.4, 0.4, 0.15);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0f, 1.2f);

        VicWorldState state = VicWorldState.getServerState(serverLevel.getServer());
        VicEntity vic = ModEntities.VIC.create(serverLevel, EntitySpawnReason.EVENT);
        if (vic != null) {
            vic.snapTo(this.getX(), this.getY() + 0.6, this.getZ(), this.getYRot(), 0.0f);
            state.transferSingletonVic(this, vic);

            Player nearest = serverLevel.getNearestPlayer(this, 32.0);
            if (nearest != null) {
                vic.setOwnerUuid(nearest.getUUID());
                String greeting = "Hellooooo! I'm Vic, your personal helper friend! Ask me anything, I know everything!";
                nearest.sendSystemMessage(
                        Component.literal("<Vic> ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                                .append(Component.literal(greeting).withStyle(ChatFormatting.WHITE))
                );
                if (nearest instanceof ServerPlayer serverPlayer) {
                    VicSpeechPayload.sendTo(serverPlayer, greeting, 0);
                }
            }
            serverLevel.addFreshEntity(vic);
        }

        state.activateCountdown(serverLevel.getOverworldClockTime());
        VicHudStatePayload.broadcast(serverLevel.getServer(), state);
        this.discard();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
                VicWorldState.getServerState(serverLevel.getServer()).releaseSingletonVic(this);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        if (!this.level().isClientSide() && source.getEntity() instanceof Player player && !isUnboxing()) {
            this.entityData.set(UNBOXING, true);
            this.entityData.set(UNBOX_TICKS, 1);
            player.sendOverlayMessage(Component.literal("[Delivery Box] Impact detected! Auto-opening package...").withStyle(ChatFormatting.GOLD));
            return false;
        }
        return super.hurtServer(serverLevel, source, amount);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Unboxing", isUnboxing());
        output.putInt("UnboxTicks", getUnboxTicks());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(UNBOXING, input.getBooleanOr("Unboxing", false));
        this.entityData.set(UNBOX_TICKS, input.getIntOr("UnboxTicks", 0));
    }
}
