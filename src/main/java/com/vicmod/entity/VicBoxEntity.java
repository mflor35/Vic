package com.vicmod.entity;

import com.vicmod.registry.ModEntities;
import com.vicmod.state.VicWorldState;
import com.vicmod.voice.VicTtsService;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class VicBoxEntity extends MobEntity {
    private static final TrackedData<Boolean> UNBOXING = DataTracker.registerData(VicBoxEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> UNBOX_TICKS = DataTracker.registerData(VicBoxEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public VicBoxEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createBoxAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(UNBOXING, false);
        builder.add(UNBOX_TICKS, 0);
    }

    public boolean isUnboxing() {
        return this.dataTracker.get(UNBOXING);
    }

    public int getUnboxTicks() {
        return this.dataTracker.get(UNBOX_TICKS);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!this.getWorld().isClient && !isUnboxing()) {
            this.dataTracker.set(UNBOXING, true);
            this.dataTracker.set(UNBOX_TICKS, 1);
            this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 0.9f);
            player.sendMessage(Text.literal("[Delivery Box] Unsealing package: VIC-UNIT-01...").formatted(Formatting.YELLOW), true);
        }
        return ActionResult.success(this.getWorld().isClient);
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
        }

        if (isUnboxing()) {
            int ticks = getUnboxTicks() + 1;
            this.dataTracker.set(UNBOX_TICKS, ticks);

            if (this.getWorld().isClient) {
                this.getWorld().addParticle(ParticleTypes.END_ROD,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.9,
                        this.getY() + 0.5 + this.random.nextDouble() * 0.6,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.9,
                        0.0, 0.05, 0.0);
            } else if (this.getWorld() instanceof ServerWorld serverWorld) {
                if (ticks % 10 == 0 && ticks < 60) {
                    serverWorld.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.8f, 0.8f + (ticks / 60.0f));
                }
                if (ticks >= 60) {
                    completeUnbox(serverWorld);
                }
            }
        }
    }

    private void completeUnbox(ServerWorld serverWorld) {
        serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 0.8, this.getZ(), 35, 0.4, 0.4, 0.4, 0.15);
        serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 1.0f, 1.2f);

        VicWorldState state = VicWorldState.getServerState(serverWorld.getServer());
        VicEntity vic = ModEntities.VIC.create(serverWorld);
        if (vic != null) {
            vic.refreshPositionAndAngles(this.getX(), this.getY() + 0.6, this.getZ(), this.getYaw(), 0.0f);
            state.transferSingletonVic(this, vic);

            PlayerEntity nearest = serverWorld.getClosestPlayer(this, 32.0);
            if (nearest != null) {
                vic.setOwnerUuid(nearest.getUuid());
                String greeting = "Hiya bestie! I'm Vic, your brand-new spherical virtual assistant! Just type in chat or hold R to talk to me!";
                nearest.sendMessage(
                        Text.literal("<Vic> ").formatted(Formatting.YELLOW, Formatting.BOLD)
                                .append(Text.literal(greeting).formatted(Formatting.WHITE)),
                        false
                );
                VicTtsService.speak(greeting, 0);
            }
            serverWorld.spawnEntity(vic);
        }

        state.activateCountdown(serverWorld.getTimeOfDay());
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

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!this.getWorld().isClient && source.getAttacker() instanceof PlayerEntity player && !isUnboxing()) {
            this.dataTracker.set(UNBOXING, true);
            this.dataTracker.set(UNBOX_TICKS, 1);
            player.sendMessage(Text.literal("[Delivery Box] Impact detected! Auto-opening package...").formatted(Formatting.GOLD), true);
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Unboxing", isUnboxing());
        nbt.putInt("UnboxTicks", getUnboxTicks());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(UNBOXING, nbt.getBoolean("Unboxing"));
        this.dataTracker.set(UNBOX_TICKS, nbt.getInt("UnboxTicks"));
    }
}
