package com.vicmod.state;

import com.vicmod.entity.VicBoxEntity;
import com.vicmod.entity.VicDemonEntity;
import com.vicmod.entity.VicEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VicWorldState extends PersistentState {
    private static final String KEY = "vic_world_state";
    public static final int MAX_MEMORY_TURNS = 8;

    public record ConversationTurn(String playerName, String playerMessage, String vicResponse, int day) {
    }

    private int karma = 25;
    private int currentDay = 1;
    private boolean countdownActive = false;
    private long activationTimeTick = 0L;
    private boolean demonAwakened = false;
    private boolean demonDefeated = false;
    private boolean initialDeliverySpawned = false;
    private UUID activeVicUuid = null;
    private final Set<UUID> welcomedPlayers = new HashSet<>();
    private final Deque<ConversationTurn> conversationMemory = new ArrayDeque<>();

    public static Type<VicWorldState> getPersistentStateType() {
        return new Type<>(
                VicWorldState::new,
                VicWorldState::createFromNbt,
                null
        );
    }

    public static VicWorldState getServerState(MinecraftServer server) {
        ServerWorldHolder holder = new ServerWorldHolder(server);
        PersistentStateManager manager = holder.getStateManager();
        VicWorldState state = manager.getOrCreate(getPersistentStateType(), KEY);
        state.markDirty();
        return state;
    }

    private record ServerWorldHolder(MinecraftServer server) {
        PersistentStateManager getStateManager() {
            return server.getWorld(World.OVERWORLD).getPersistentStateManager();
        }
    }

    public static VicWorldState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        VicWorldState state = new VicWorldState();
        state.karma = nbt.getInt("karma");
        state.currentDay = Math.max(1, nbt.getInt("currentDay"));
        state.countdownActive = nbt.getBoolean("countdownActive");
        state.activationTimeTick = nbt.getLong("activationTimeTick");
        state.demonAwakened = nbt.getBoolean("demonAwakened");
        state.demonDefeated = nbt.getBoolean("demonDefeated");
        state.initialDeliverySpawned = nbt.getBoolean("initialDeliverySpawned");
        if (nbt.containsUuid("activeVicUuid")) {
            state.activeVicUuid = nbt.getUuid("activeVicUuid");
        }

        if (nbt.contains("welcomedPlayers", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("welcomedPlayers", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                try {
                    state.welcomedPlayers.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (nbt.contains("conversationMemory", NbtElement.LIST_TYPE)) {
            NbtList memoryList = nbt.getList("conversationMemory", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < memoryList.size(); i++) {
                NbtCompound turnNbt = memoryList.getCompound(i);
                String player = turnNbt.getString("player");
                String msg = turnNbt.getString("user");
                String reply = turnNbt.getString("vic");
                int day = Math.max(1, turnNbt.getInt("day"));
                if (!msg.isBlank() || !reply.isBlank()) {
                    state.conversationMemory.addLast(new ConversationTurn(player, msg, reply, day));
                }
            }
            while (state.conversationMemory.size() > MAX_MEMORY_TURNS) {
                state.conversationMemory.removeFirst();
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putInt("karma", karma);
        nbt.putInt("currentDay", currentDay);
        nbt.putBoolean("countdownActive", countdownActive);
        nbt.putLong("activationTimeTick", activationTimeTick);
        nbt.putBoolean("demonAwakened", demonAwakened);
        nbt.putBoolean("demonDefeated", demonDefeated);
        nbt.putBoolean("initialDeliverySpawned", initialDeliverySpawned);
        if (activeVicUuid != null) {
            nbt.putUuid("activeVicUuid", activeVicUuid);
        }

        NbtList list = new NbtList();
        for (UUID uuid : welcomedPlayers) {
            list.add(NbtString.of(uuid.toString()));
        }
        nbt.put("welcomedPlayers", list);

        NbtList memoryList = new NbtList();
        for (ConversationTurn turn : conversationMemory) {
            NbtCompound turnNbt = new NbtCompound();
            turnNbt.putString("player", turn.playerName() != null ? turn.playerName() : "Player");
            turnNbt.putString("user", turn.playerMessage() != null ? turn.playerMessage() : "");
            turnNbt.putString("vic", turn.vicResponse() != null ? turn.vicResponse() : "");
            turnNbt.putInt("day", turn.day());
            memoryList.add(turnNbt);
        }
        nbt.put("conversationMemory", memoryList);
        return nbt;
    }

    public void recordConversationTurn(String playerName, String playerMessage, String vicResponse) {
        if ((playerMessage == null || playerMessage.isBlank()) && (vicResponse == null || vicResponse.isBlank())) {
            return;
        }
        while (conversationMemory.size() >= MAX_MEMORY_TURNS) {
            conversationMemory.removeFirst();
        }
        conversationMemory.addLast(new ConversationTurn(
                playerName != null ? playerName : "Player",
                playerMessage != null ? playerMessage.trim() : "",
                vicResponse != null ? vicResponse.trim() : "",
                this.currentDay
        ));
        markDirty();
    }

    public List<ConversationTurn> getConversationMemory() {
        return new ArrayList<>(conversationMemory);
    }

    public ConversationTurn getLastConversationTurn() {
        return conversationMemory.peekLast();
    }

    public void clearConversationMemory() {
        conversationMemory.clear();
        markDirty();
    }

    public static boolean isVicCharacter(Entity entity) {
        return entity instanceof VicBoxEntity || entity instanceof VicEntity || entity instanceof VicDemonEntity;
    }

    /**
     * Checks if this entity is allowed to exist as the single Vic character in the world.
     * Returns true if registered as the sole active Vic, or false if another active Vic already exists.
     */
    public boolean tryClaimSingletonVic(MinecraftServer server, Entity candidate) {
        if (candidate == null || !candidate.isAlive()) {
            return false;
        }
        UUID candidateId = candidate.getUuid();
        if (candidateId.equals(this.activeVicUuid)) {
            return true;
        }

        Entity existing = findActiveVicEntity(server, candidateId);
        if (existing != null) {
            return false;
        }

        this.activeVicUuid = candidateId;
        markDirty();
        return true;
    }

    /**
     * Transfers the singleton slot from oldEntity (e.g. Box or Vic) to newEntity (e.g. Vic or Demon).
     */
    public void transferSingletonVic(Entity oldEntity, Entity newEntity) {
        if (newEntity != null) {
            this.activeVicUuid = newEntity.getUuid();
            markDirty();
        } else if (oldEntity != null && oldEntity.getUuid().equals(this.activeVicUuid)) {
            this.activeVicUuid = null;
            markDirty();
        }
    }

    public void releaseSingletonVic(Entity entity) {
        if (entity != null && entity.getUuid().equals(this.activeVicUuid)) {
            this.activeVicUuid = null;
            markDirty();
        }
    }

    public Entity findActiveVicEntity(MinecraftServer server, UUID ignoreUuid) {
        if (server == null) return null;

        if (this.activeVicUuid != null && !this.activeVicUuid.equals(ignoreUuid)) {
            for (ServerWorld world : server.getWorlds()) {
                Entity e = world.getEntity(this.activeVicUuid);
                if (e != null && e.isAlive() && !e.isRemoved() && isVicCharacter(e)) {
                    return e;
                }
            }
        }

        for (ServerWorld world : server.getWorlds()) {
            List<? extends Entity> found = world.getEntitiesByType(
                    TypeFilter.instanceOf(Entity.class),
                    e -> e.isAlive() && !e.isRemoved() && isVicCharacter(e) && !e.getUuid().equals(ignoreUuid)
            );
            if (!found.isEmpty()) {
                Entity first = found.getFirst();
                this.activeVicUuid = first.getUuid();
                markDirty();
                return first;
            }
        }
        return null;
    }

    public boolean hasActiveVicInWorld(MinecraftServer server) {
        return findActiveVicEntity(server, null) != null;
    }

    public boolean hasInitialDeliverySpawned() {
        return initialDeliverySpawned;
    }

    public void markInitialDeliverySpawned() {
        this.initialDeliverySpawned = true;
        markDirty();
    }

    public UUID getActiveVicUuid() {
        return activeVicUuid;
    }

    public int getKarma() {
        return karma;
    }

    public void addKarma(int delta) {
        this.karma = Math.max(-100, Math.min(100, this.karma + delta));
        markDirty();
    }

    public void setKarma(int karma) {
        this.karma = Math.max(-100, Math.min(100, karma));
        markDirty();
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public void setCurrentDay(int day) {
        this.currentDay = Math.max(1, day);
        markDirty();
    }

    public void resetCountdownToDay(int day, long currentWorldTick) {
        this.currentDay = Math.max(1, day);
        this.countdownActive = true;
        this.activationTimeTick = currentWorldTick - (this.currentDay - 1L) * 24000L;
        this.demonAwakened = false;
        this.demonDefeated = false;
        markDirty();
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public void setCountdownActive(boolean active) {
        this.countdownActive = active;
        markDirty();
    }

    public void activateCountdown(long currentWorldTick) {
        if (!this.countdownActive) {
            this.countdownActive = true;
            this.activationTimeTick = currentWorldTick;
            this.currentDay = 1;
            this.demonDefeated = false;
            markDirty();
        }
    }

    public long getActivationTimeTick() {
        return activationTimeTick;
    }

    public boolean isDemonAwakened() {
        return demonAwakened;
    }

    public void setDemonAwakened(boolean awakened) {
        this.demonAwakened = awakened;
        markDirty();
    }

    public boolean isDemonDefeated() {
        return demonDefeated;
    }

    public void setDemonDefeated(boolean defeated) {
        this.demonDefeated = defeated;
        markDirty();
    }

    public boolean hasWelcomedPlayer(UUID uuid) {
        return welcomedPlayers.contains(uuid);
    }

    public void markPlayerWelcomed(UUID uuid) {
        welcomedPlayers.add(uuid);
        markDirty();
    }

    public int computeStage(int maxDays) {
        if (demonDefeated) {
            return 0;
        }
        if (demonAwakened || currentDay > maxDays) {
            return 4;
        }
        double ratio = (double) (currentDay - 1) / Math.max(1, maxDays - 1);
        if (karma < -35) {
            ratio += 0.35;
        } else if (karma < 0) {
            ratio += 0.15;
        }
        if (ratio >= 0.85) return 3;
        if (ratio >= 0.55) return 2;
        if (ratio >= 0.25) return 1;
        return 0;
    }
}
