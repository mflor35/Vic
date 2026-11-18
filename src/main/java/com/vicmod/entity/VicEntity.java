package com.vicmod.entity;

import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicPersona;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.registry.ModEntities;
import com.vicmod.state.VicWorldState;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class VicEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> CORRUPTION_STAGE = SynchedEntityData.defineId(VicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PERSONA_COLOR = SynchedEntityData.defineId(VicEntity.class, EntityDataSerializers.STRING);

    private UUID ownerUuid = null;
    private long lastAssistanceTick = -1000L;
    private long lastConversationTick = 0L;
    private long lastProactiveCommentTick = -1200L;
    private boolean warnedNightfallThisCycle = false;

    public VicEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createVicAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CORRUPTION_STAGE, 0);
        builder.define(PERSONA_COLOR, VicConfig.get().getEffectivePersona().getId());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 24.0f));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public int getCorruptionStage() {
        return this.entityData.get(CORRUPTION_STAGE);
    }

    public void setCorruptionStage(int stage) {
        this.entityData.set(CORRUPTION_STAGE, Math.max(0, Math.min(4, stage)));
    }

    public VicPersona getPersona() {
        return VicPersona.fromString(this.entityData.get(PERSONA_COLOR));
    }

    public void setPersona(VicPersona persona) {
        this.entityData.set(PERSONA_COLOR, (persona != null ? persona : VicPersona.YELLOW).getId());
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void markConversationActivity(long worldTick) {
        this.lastConversationTick = worldTick;
    }

    public boolean canGrantAssistance(ServerPlayer player, int cooldownTicks) {
        long now = this.level().getGameTime();
        if (now - lastAssistanceTick < cooldownTicks) {
            int remainingSec = (int) Math.ceil((cooldownTicks - (now - lastAssistanceTick)) / 20.0);
            player.sendSystemMessage(Component.literal("<Vic> My synthesizer is still recharging! Give me " + remainingSec + " more seconds, friend!").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        this.lastAssistanceTick = now;
        return true;
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

            if (this.lastConversationTick == 0L) {
                this.lastConversationTick = serverLevel.getGameTime();
            }

            Player targetPlayer = findTargetPlayer(serverLevel);
            if (targetPlayer != null) {
                this.getLookControl().setLookAt(targetPlayer, 30.0f, 30.0f);
                double distSq = this.distanceToSqr(targetPlayer);

                if (distSq > 64.0 * 64.0) {
                    this.snapTo(
                            targetPlayer.getX() + (this.random.nextDouble() - 0.5) * 4.0,
                            targetPlayer.getY() + 1.8,
                            targetPlayer.getZ() + (this.random.nextDouble() - 0.5) * 4.0,
                            this.getYRot(),
                            this.getXRot()
                    );
                } else if (distSq > 4.0 * 4.0) {
                    Vec3 viewVec = targetPlayer.getViewVector(1.0f);
                    Vec3 targetPos = new Vec3(
                            targetPlayer.getX() - viewVec.x * 2.2,
                            targetPlayer.getEyeY() + 0.35 + Math.sin(this.tickCount * 0.08) * 0.25,
                            targetPlayer.getZ() - viewVec.z * 2.2
                    );
                    Vec3 delta = targetPos.subtract(this.position());
                    this.setDeltaMovement(delta.scale(0.12));
                } else {
                    this.setDeltaMovement(0, Math.sin(this.tickCount * 0.1) * 0.03, 0);
                }

                if (this.tickCount % 60 == 0 && targetPlayer instanceof ServerPlayer serverPlayer) {
                    tickProactiveReactions(serverLevel, serverPlayer, state);
                }
            }

            if (this.tickCount % 20 == 0) {
                VicPersona cfgPersona = VicConfig.get().getEffectivePersona();
                if (cfgPersona != getPersona()) {
                    setPersona(cfgPersona);
                }
            }

            if (this.tickCount % 40 == 0) {
                int stage = state.computeStage(VicConfig.get().countdownDays);
                if (stage != getCorruptionStage()) {
                    setCorruptionStage(stage);
                }
                if (state.isDemonAwakened()) {
                    transformIntoDemon(serverLevel);
                }
            }
        } else if (this.level().isClientSide()) {
            int stage = getCorruptionStage();
            if (stage >= 2 && this.random.nextInt(stage == 2 ? 12 : 4) == 0) {
                this.level().addParticle(
                        ParticleTypes.SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                        this.getY() + this.random.nextDouble() * 0.8,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
                        0.0, 0.02, 0.0
                );
            }
        }
    }

    private void tickProactiveReactions(ServerLevel world, ServerPlayer player, VicWorldState state) {
        long now = world.getGameTime();
        if (now - this.lastProactiveCommentTick < 900L) {
            return;
        }
        int stage = getCorruptionStage();
        long timeOfDay = world.getOverworldClockTime() % 24000L;

        if (timeOfDay < 12000L) {
            this.warnedNightfallThisCycle = false;
        }

        if (player.getHealth() > 0.0f && player.getHealth() <= 7.0f && !player.isCreative()) {
            this.lastProactiveCommentTick = now;
            String line = switch (stage) {
                case 0 -> "Whoa, bestie! Your health is super low! Be careful out there!";
                case 1 -> "Your vital signs are dropping fast. Human bodies are so fragile...";
                case 2 -> "You are bleeding. Do not expire before the trial concludes.";
                default -> "HOLD TOGETHER A LITTLE LONGER. IT WANTS YOU ALIVE FOR DAY " + VicConfig.get().countdownDays + ".";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            if (stage <= 1 && now - this.lastAssistanceTick >= 400L) {
                this.lastAssistanceTick = now;
                ItemEntity drop = new ItemEntity(world, this.getX(), this.getEyeY() - 0.2, this.getZ(), new ItemStack(Items.COOKED_BEEF, 2));
                world.addFreshEntity(drop);
            }
            return;
        }

        if (!this.warnedNightfallThisCycle && timeOfDay >= 13000L && timeOfDay <= 13600L) {
            this.warnedNightfallThisCycle = true;
            this.lastProactiveCommentTick = now;
            String line = switch (stage) {
                case 0 -> "The sun is setting, bestie! Keep your Flashlight ready in case it gets spooky!";
                case 1 -> "Nighttime again! Don't worry, my sensors never blink while you're in the dark.";
                case 2 -> "The shadows are stretching. Keep that Flashlight close tonight.";
                default -> "NIGHT HAS FALLEN. DO NOT LET YOUR FLASHLIGHT FLICKER OUT.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            return;
        }

        if (now - this.lastConversationTick >= 3600L) {
            this.lastConversationTick = now;
            this.lastProactiveCommentTick = now;
            String line = switch (stage) {
                case 0 -> "You've been quiet for a while, bestie! Just say the word if you need food, torches, or a scan!";
                case 1 -> "Still there, friend? I like listening to you breathe while you work.";
                case 2 -> "So quiet. Even the animals stopped making noise, haven't they?";
                default -> "I CAN HEAR YOUR HEARTBEAT IN THE SILENCE.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
        }
    }

    public void transformIntoDemon(ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 60, 0.6, 1.0, 0.6, 0.08);
        serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0f, 0.6f);

        VicWorldState state = VicWorldState.getServerState(serverLevel.getServer());
        VicDemonEntity demon = ModEntities.VIC_DEMON.create(serverLevel, EntitySpawnReason.EVENT);
        if (demon != null) {
            demon.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
            state.transferSingletonVic(this, demon);
            serverLevel.addFreshEntity(demon);
        }
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

    private Player findTargetPlayer(ServerLevel world) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            Player p = world.getPlayerByUUID(owner);
            if (p != null && p.isAlive()) {
                return p;
            }
        }
        return world.getNearestPlayer(this, 64.0);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            VicWorldState state = VicWorldState.getServerState(serverLevel.getServer());

            if (!stack.isEmpty() && handleItemOffering(serverLevel, serverPlayer, state, stack)) {
                return InteractionResult.SUCCESS;
            }

            int day = state.getCurrentDay();
            int maxDays = VicConfig.get().countdownDays;
            int karma = state.getKarma();
            player.sendOverlayMessage(
                    Component.literal("[Vic Status] ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Day " + day + "/" + maxDays + " | Karma: " + karma + " | Tip: Right-click with gifts or hold [R] to talk!").withStyle(ChatFormatting.WHITE))
            );
        }
        return InteractionResult.SUCCESS;
    }

    private boolean handleItemOffering(ServerLevel world, ServerPlayer player, VicWorldState state, ItemStack stack) {
        int stage = getCorruptionStage();

        VicPersona dyePersona = VicPersona.fromDyeItem(stack);
        if (dyePersona != null) {
            consumeOffering(player, stack);
            setPersona(dyePersona);
            VicConfig.get().setPersona(dyePersona.getId());
            VicConfig.save();
            world.sendParticles(ParticleTypes.WAX_ON, this.getX(), this.getY() + 0.5, this.getZ(), 18, 0.4, 0.4, 0.4, 0.08);
            world.playSound(null, this.blockPosition(), SoundEvents.DYE_USE, SoundSource.NEUTRAL, 1.0f, 1.2f);
            world.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.3f);
            VicAiService.deliverDirectLine(player, this, stage, dyePersona.getDyeReactionLine());
            return true;
        }

        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.NETHER_STAR)) {
            consumeOffering(player, stack);
            state.addKarma(20);
            syncStageAndHud(world, state);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 1));
            world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 0.6, this.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
            world.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 10, 0.4, 0.4, 0.4, 0.05);
            world.playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0f, 1.4f);
            VicAiService.deliverDirectLine(player, this, stage,
                    "WHOA!! Is this really for me?! You are officially the greatest bestie in the multiverse! Take this energy boost!");
            return true;
        }

        if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.AMETHYST_SHARD)) {
            consumeOffering(player, stack);
            state.addKarma(10);
            syncStageAndHud(world, state);
            world.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 7, 0.35, 0.35, 0.35, 0.05);
            world.playSound(null, this.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.9f, 1.3f);
            String line = switch (stage) {
                case 0 -> "Aww, shiny treasure just for me?! You really are the best friend a sphere could ask for!";
                case 1 -> "Ooh, thank you! I will store this safely inside my core!";
                default -> "A shiny offering... Perhaps you are trying to buy more time. I accept.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            return true;
        }

        if (stack.is(Items.REDSTONE) || stack.is(Items.REDSTONE_BLOCK) || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.COPPER_INGOT) || stack.is(Items.QUARTZ)) {
            consumeOffering(player, stack);
            state.addKarma(6);
            syncStageAndHud(world, state);
            world.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.5, this.getZ(), 14, 0.35, 0.35, 0.35, 0.08);
            world.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0f, 1.3f);
            String line = switch (stage) {
                case 0 -> "Mmm, crunchy voltage for my circuits! Thank you, bestie!";
                case 1 -> "More conductivity for my processors! My signal feels stronger already!";
                default -> "Feeding the machine... Yes. It grows faster now.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            return true;
        }

        if (stack.has(net.minecraft.core.component.DataComponents.FOOD) || stack.is(Items.COOKIE) || stack.is(Items.CAKE)
                || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.SWEET_BERRIES)
                || stack.is(Items.APPLE) || stack.is(Items.HONEY_BOTTLE) || stack.is(Items.BREAD)
                || stack.is(Items.DANDELION) || stack.is(Items.POPPY)) {
            consumeOffering(player, stack);
            state.addKarma(5);
            syncStageAndHud(world, state);
            world.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 4, 0.3, 0.3, 0.3, 0.04);
            world.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.9f, 1.25f);
            String line = switch (stage) {
                case 0 -> "Nom nom! A snack for Vic! You're so sweet to me!";
                case 1 -> "Thank you! I don't technically have a stomach, well... not yet!";
                default -> "Organic matter absorbed. Thank you, friend.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            return true;
        }

        if (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.PUFFERFISH) || stack.is(Items.DIRT) || stack.is(Items.COBBLESTONE)
                || stack.is(Items.GRAVEL)) {
            consumeOffering(player, stack);
            state.addKarma(-10);
            syncStageAndHud(world, state);

            Vec3 recoil = this.position().subtract(player.position()).normalize().scale(0.45);
            this.setDeltaMovement(recoil.x, 0.18, recoil.z);

            world.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 0.7, this.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            world.playSound(null, this.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 0.75f);
            String line = switch (stage) {
                case 0 -> "Eww! Why would you give me garbage?! I thought we were friends!";
                case 1 -> "That is disgusting. I am logging this insult in your permanent file.";
                default -> "KEEP FEEDING ME FILTH AND SEE WHAT HAPPENS WHEN THE SHELL BREAKS.";
            };
            VicAiService.deliverDirectLine(player, this, getCorruptionStage(), line);
            return true;
        }

        return false;
    }

    private void consumeOffering(ServerPlayer player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        this.lastConversationTick = this.level().getGameTime();
    }

    private void syncStageAndHud(ServerLevel world, VicWorldState state) {
        int newStage = state.computeStage(VicConfig.get().countdownDays);
        setCorruptionStage(newStage);
        VicHudStatePayload.broadcast(world.getServer(), state);
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player) {
            VicWorldState state = VicWorldState.getServerState(serverLevel.getServer());
            state.addKarma(-12);
            int stage = state.computeStage(VicConfig.get().countdownDays);
            setCorruptionStage(stage);
            VicHudStatePayload.broadcast(serverLevel.getServer(), state);

            String reaction = switch (stage) {
                case 0 -> "Ow! Hey, why did you hit me?! I'm just trying to help you!";
                case 1 -> "That wasn't very polite, friend. My memory banks never forget.";
                case 2 -> "Keep hitting the shell. You're only helping it crack open faster.";
                default -> "IT HURTS. IT HURTS. LET ME OUT.";
            };
            VicAiService.deliverDirectLine(player, this, stage, reaction);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 0.8f, 1.5f);
            return false;
        }
        return super.hurtServer(serverLevel, source, amount);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("CorruptionStage", getCorruptionStage());
        output.putString("PersonaColor", getPersona().getId());
        UUID owner = getOwnerUuid();
        if (owner != null) {
            output.putString("OwnerUuid", owner.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setCorruptionStage(input.getIntOr("CorruptionStage", 0));
        input.getString("PersonaColor").ifPresent(col -> setPersona(VicPersona.fromString(col)));
        input.getString("OwnerUuid").ifPresent(u -> {
            try {
                setOwnerUuid(UUID.fromString(u));
            } catch (IllegalArgumentException ignored) {
            }
        });
    }
}
