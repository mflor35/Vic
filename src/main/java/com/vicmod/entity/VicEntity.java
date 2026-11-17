package com.vicmod.entity;

import com.vicmod.ai.VicAiService;
import com.vicmod.config.VicConfig;
import com.vicmod.config.VicPersona;
import com.vicmod.network.VicHudStatePayload;
import com.vicmod.registry.ModEntities;
import com.vicmod.state.VicWorldState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
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
    private static final TrackedData<String> PERSONA_COLOR = DataTracker.registerData(VicEntity.class, TrackedDataHandlerRegistry.STRING);

    private long lastAssistanceTick = -1000L;
    private long lastConversationTick = 0L;
    private long lastProactiveCommentTick = -1200L;
    private boolean warnedNightfallThisCycle = false;

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
        builder.add(PERSONA_COLOR, VicConfig.get().getEffectivePersona().getId());
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

    public VicPersona getPersona() {
        return VicPersona.fromString(this.dataTracker.get(PERSONA_COLOR));
    }

    public void setPersona(VicPersona persona) {
        this.dataTracker.set(PERSONA_COLOR, (persona != null ? persona : VicPersona.YELLOW).getId());
    }

    public void setOwnerUuid(UUID uuid) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER_UUID).orElse(null);
    }

    public void markConversationActivity(long worldTick) {
        this.lastConversationTick = worldTick;
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

            if (this.lastConversationTick == 0L) {
                this.lastConversationTick = serverWorld.getTime();
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

                if (this.age % 60 == 0 && targetPlayer instanceof ServerPlayerEntity serverPlayer) {
                    tickProactiveReactions(serverWorld, serverPlayer, state);
                }
            }

            if (this.age % 20 == 0) {
                VicPersona cfgPersona = VicConfig.get().getEffectivePersona();
                if (cfgPersona != getPersona()) {
                    setPersona(cfgPersona);
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

    private void tickProactiveReactions(ServerWorld world, ServerPlayerEntity player, VicWorldState state) {
        long now = world.getTime();
        if (now - this.lastProactiveCommentTick < 900L) {
            return;
        }
        int stage = getCorruptionStage();
        long timeOfDay = world.getTimeOfDay() % 24000L;

        if (timeOfDay < 12000L) {
            this.warnedNightfallThisCycle = false;
        }

        // 1. Critical health reaction
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
                world.spawnEntity(drop);
            }
            return;
        }

        // 2. Nightfall warning
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

        // 3. Long silence reaction (if player hasn't spoken in ~3 minutes)
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
        if (!this.getWorld().isClient && this.getWorld() instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
            VicWorldState state = VicWorldState.getServerState(serverWorld.getServer());

            if (!stack.isEmpty() && handleItemOffering(serverWorld, serverPlayer, state, stack)) {
                return ActionResult.SUCCESS;
            }

            int day = state.getCurrentDay();
            int maxDays = VicConfig.get().countdownDays;
            int karma = state.getKarma();
            player.sendMessage(
                    Text.literal("[Vic Status] ").formatted(Formatting.GOLD)
                            .append(Text.literal("Day " + day + "/" + maxDays + " | Karma: " + karma + " | Tip: Right-click with gifts or hold [R] to talk!").formatted(Formatting.WHITE)),
                    true
            );
        }
        return ActionResult.success(this.getWorld().isClient);
    }

    private boolean handleItemOffering(ServerWorld world, ServerPlayerEntity player, VicWorldState state, ItemStack stack) {
        int stage = getCorruptionStage();

        // 0. Dye Offering — Changes Vic's Color & Personality!
        VicPersona dyePersona = VicPersona.fromDyeItem(stack);
        if (dyePersona != null) {
            consumeOffering(player, stack);
            setPersona(dyePersona);
            VicConfig.get().setPersona(dyePersona.getId());
            VicConfig.save();
            world.spawnParticles(ParticleTypes.WAX_ON, this.getX(), this.getY() + 0.5, this.getZ(), 18, 0.4, 0.4, 0.4, 0.08);
            world.playSound(null, this.getBlockPos(), SoundEvents.ITEM_DYE_USE, SoundCategory.NEUTRAL, 1.0f, 1.2f);
            world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1.0f, 1.3f);
            VicAiService.deliverDirectLine(player, this, stage, dyePersona.getDyeReactionLine());
            return true;
        }

        // 1. Legendary Offerings (+20 Karma + Buffs)
        if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE) || stack.isOf(Items.NETHER_STAR)) {
            consumeOffering(player, stack);
            state.addKarma(20);
            syncStageAndHud(world, state);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 20, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 60, 1));
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, this.getX(), this.getY() + 0.6, this.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 10, 0.4, 0.4, 0.4, 0.05);
            world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 1.0f, 1.4f);
            VicAiService.deliverDirectLine(player, this, stage,
                    "WHOA!! Is this really for me?! You are officially the greatest bestie in the multiverse! Take this energy boost!");
            return true;
        }

        // 2. Precious Gifts (+10 Karma)
        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.DIAMOND) || stack.isOf(Items.EMERALD) || stack.isOf(Items.AMETHYST_SHARD)) {
            consumeOffering(player, stack);
            state.addKarma(10);
            syncStageAndHud(world, state);
            world.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 7, 0.35, 0.35, 0.35, 0.05);
            world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.9f, 1.3f);
            String line = switch (stage) {
                case 0 -> "Aww, shiny treasure just for me?! You really are the best friend a sphere could ask for!";
                case 1 -> "Ooh, thank you! I will store this safely inside my core!";
                default -> "A shiny offering... Perhaps you are trying to buy more time. I accept.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            return true;
        }

        // 3. Circuit Snacks (+6 Karma)
        if (stack.isOf(Items.REDSTONE) || stack.isOf(Items.REDSTONE_BLOCK) || stack.isOf(Items.GOLD_INGOT)
                || stack.isOf(Items.COPPER_INGOT) || stack.isOf(Items.QUARTZ)) {
            consumeOffering(player, stack);
            state.addKarma(6);
            syncStageAndHud(world, state);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.5, this.getZ(), 14, 0.35, 0.35, 0.35, 0.08);
            world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1.0f, 1.3f);
            String line = switch (stage) {
                case 0 -> "Mmm, crunchy voltage for my circuits! Thank you, bestie!";
                case 1 -> "More conductivity for my processors! My signal feels stronger already!";
                default -> "Feeding the machine... Yes. It grows faster now.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            return true;
        }

        // 4. Sweet Treats & Flowers (+5 Karma)
        if (stack.isIn(ItemTags.FLOWERS) || stack.isOf(Items.COOKIE) || stack.isOf(Items.CAKE)
                || stack.isOf(Items.PUMPKIN_PIE) || stack.isOf(Items.GLOW_BERRIES) || stack.isOf(Items.SWEET_BERRIES)
                || stack.isOf(Items.APPLE) || stack.isOf(Items.HONEY_BOTTLE) || stack.isOf(Items.BREAD)) {
            consumeOffering(player, stack);
            state.addKarma(5);
            syncStageAndHud(world, state);
            world.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 0.8, this.getZ(), 4, 0.3, 0.3, 0.3, 0.04);
            world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.9f, 1.25f);
            String line = switch (stage) {
                case 0 -> "Nom nom! A snack for Vic! You're so sweet to me!";
                case 1 -> "Thank you! I don't technically have a stomach, well... not yet!";
                default -> "Organic matter absorbed. Thank you, friend.";
            };
            VicAiService.deliverDirectLine(player, this, stage, line);
            return true;
        }

        // 5. Insulting / Trash Offerings (-10 Karma & Recoil)
        if (stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.SPIDER_EYE) || stack.isOf(Items.POISONOUS_POTATO)
                || stack.isOf(Items.PUFFERFISH) || stack.isOf(Items.DIRT) || stack.isOf(Items.COBBLESTONE)
                || stack.isOf(Items.GRAVEL)) {
            consumeOffering(player, stack);
            state.addKarma(-10);
            syncStageAndHud(world, state);

            Vec3d recoil = this.getPos().subtract(player.getPos()).normalize().multiply(0.45);
            this.setVelocity(recoil.x, 0.18, recoil.z);
            this.velocityDirty = true;

            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 0.7, this.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 0.75f);
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

    private void consumeOffering(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        this.lastConversationTick = this.getWorld().getTime();
    }

    private void syncStageAndHud(ServerWorld world, VicWorldState state) {
        int newStage = state.computeStage(VicConfig.get().countdownDays);
        setCorruptionStage(newStage);
        VicHudStatePayload.broadcast(world.getServer(), state);
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
                VicAiService.deliverDirectLine(player, this, stage, reaction);
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
        nbt.putString("PersonaColor", getPersona().getId());
        UUID owner = getOwnerUuid();
        if (owner != null) {
            nbt.putUuid("OwnerUuid", owner);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setCorruptionStage(nbt.getInt("CorruptionStage"));
        if (nbt.contains("PersonaColor")) {
            setPersona(VicPersona.fromString(nbt.getString("PersonaColor")));
        }
        if (nbt.containsUuid("OwnerUuid")) {
            setOwnerUuid(nbt.getUuid("OwnerUuid"));
        }
    }
}
