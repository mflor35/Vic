package com.vicmod.state;

import com.mojang.serialization.Codec;
import com.vicmod.VicMod;
import com.vicmod.entity.VicBoxEntity;
import com.vicmod.entity.VicDemonEntity;
import com.vicmod.entity.VicEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class VicWorldState extends SavedData {
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

    public static final Codec<VicWorldState> CODEC = CompoundTag.CODEC.xmap(
            VicWorldState::loadFromNbt,
            VicWorldState::saveToNbt
    );

    public static final SavedDataType<VicWorldState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "vic_world_state"),
            VicWorldState::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    public static VicWorldState getServerState(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public static VicWorldState loadFromNbt(CompoundTag tag) {
        VicWorldState state = new VicWorldState();
        state.karma = tag.getIntOr("karma", 25);
        state.currentDay = tag.getIntOr("currentDay", 1);
        state.countdownActive = tag.getBooleanOr("countdownActive", false);
        state.activationTimeTick = tag.getLongOr("activationTimeTick", 0L);
        state.demonAwakened = tag.getBooleanOr("demonAwakened", false);
        state.demonDefeated = tag.getBooleanOr("demonDefeated", false);
        state.initialDeliverySpawned = tag.getBooleanOr("initialDeliverySpawned", false);

        String uuidStr = tag.getStringOr("activeVicUuid", "");
        if (!uuidStr.isEmpty()) {
            try {
                state.activeVicUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                state.activeVicUuid = null;
            }
        }

        ListTag welcomedList = tag.getListOrEmpty("welcomedPlayers");
        state.welcomedPlayers.clear();
        for (int i = 0; i < welcomedList.size(); i++) {
            try {
                state.welcomedPlayers.add(UUID.fromString(welcomedList.getString(i).orElse("")));
            } catch (IllegalArgumentException ignored) {
            }
        }

        ListTag memoryList = tag.getListOrEmpty("conversationMemory");
        state.conversationMemory.clear();
        for (int i = 0; i < memoryList.size(); i++) {
            if (memoryList.get(i) instanceof CompoundTag turnTag) {
                String pName = turnTag.getStringOr("player", "Player");
                String pMsg = turnTag.getStringOr("message", "");
                String vResp = turnTag.getStringOr("response", "");
                int d = turnTag.getIntOr("day", 1);
                state.conversationMemory.addLast(new ConversationTurn(pName, pMsg, vResp, d));
            }
        }

        return state;
    }

    public CompoundTag saveToNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("karma", this.karma);
        tag.putInt("currentDay", this.currentDay);
        tag.putBoolean("countdownActive", this.countdownActive);
        tag.putLong("activationTimeTick", this.activationTimeTick);
        tag.putBoolean("demonAwakened", this.demonAwakened);
        tag.putBoolean("demonDefeated", this.demonDefeated);
        tag.putBoolean("initialDeliverySpawned", this.initialDeliverySpawned);

        if (this.activeVicUuid != null) {
            tag.putString("activeVicUuid", this.activeVicUuid.toString());
        }

        ListTag welcomedList = new ListTag();
        for (UUID uuid : this.welcomedPlayers) {
            welcomedList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("welcomedPlayers", welcomedList);

        ListTag memoryList = new ListTag();
        for (ConversationTurn turn : this.conversationMemory) {
            CompoundTag turnTag = new CompoundTag();
            turnTag.putString("player", turn.playerName());
            turnTag.putString("message", turn.playerMessage());
            turnTag.putString("response", turn.vicResponse());
            turnTag.putInt("day", turn.day());
            memoryList.add(turnTag);
        }
        tag.put("conversationMemory", memoryList);

        return tag;
    }

    public void addConversationTurn(String playerName, String playerMessage, String vicResponse, int day) {
        if (playerMessage == null || playerMessage.isBlank()) return;
        if (vicResponse == null || vicResponse.isBlank()) return;

        this.conversationMemory.addLast(new ConversationTurn(
                playerName != null ? playerName : "Player",
                playerMessage.trim(),
                vicResponse.trim(),
                day
        ));
        while (this.conversationMemory.size() > MAX_MEMORY_TURNS) {
            this.conversationMemory.removeFirst();
        }
        setDirty();
    }

    public List<ConversationTurn> getRecentConversationMemory() {
        return new ArrayList<>(this.conversationMemory);
    }

    public List<ConversationTurn> getConversationMemory() {
        return getRecentConversationMemory();
    }

    public void recordConversationTurn(String playerName, String playerMessage, String vicResponse) {
        addConversationTurn(playerName, playerMessage, vicResponse, this.currentDay);
    }

    public String formatMemoryForPrompt() {
        if (this.conversationMemory.isEmpty()) {
            return "No prior conversation history in this world.";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (ConversationTurn turn : this.conversationMemory) {
            sb.append(idx++).append(". [Day ").append(turn.day()).append("] ")
                    .append(turn.playerName()).append(": \"").append(turn.playerMessage()).append("\"\n")
                    .append("   Vic: \"").append(turn.vicResponse()).append("\"\n");
        }
        return sb.toString().trim();
    }

    public boolean hasWelcomedPlayer(UUID uuid) {
        return uuid != null && welcomedPlayers.contains(uuid);
    }

    public void addWelcomedPlayer(UUID uuid) {
        if (uuid != null && welcomedPlayers.add(uuid)) {
            setDirty();
        }
    }

    public void markPlayerWelcomed(UUID uuid) {
        addWelcomedPlayer(uuid);
    }

    public static boolean isVicCharacter(Entity entity) {
        return entity instanceof VicBoxEntity
                || entity instanceof VicEntity
                || entity instanceof VicDemonEntity;
    }

    public boolean tryClaimSingletonVic(MinecraftServer server, Entity candidate) {
        return tryClaimSingletonVic(candidate, server);
    }

    public boolean tryClaimSingletonVic(Entity candidate, MinecraftServer server) {
        if (candidate == null) return false;
        UUID candidateId = candidate.getUUID();

        if (this.activeVicUuid == null || this.activeVicUuid.equals(candidateId)) {
            this.activeVicUuid = candidateId;
            setDirty();
            return true;
        }

        Entity existing = findActiveVicEntity(server, candidateId);
        if (existing != null && existing.isAlive() && !existing.isRemoved()) {
            this.activeVicUuid = existing.getUUID();
            setDirty();
            return false;
        }

        this.activeVicUuid = candidateId;
        setDirty();
        return true;
    }

    public void transferSingletonVic(Entity oldEntity, Entity newEntity) {
        if (newEntity != null) {
            this.activeVicUuid = newEntity.getUUID();
            setDirty();
        } else if (oldEntity != null && oldEntity.getUUID().equals(this.activeVicUuid)) {
            this.activeVicUuid = null;
            setDirty();
        }
    }

    public void releaseSingletonVic(Entity entity) {
        if (entity != null && entity.getUUID().equals(this.activeVicUuid)) {
            this.activeVicUuid = null;
            setDirty();
        }
    }

    public Entity findActiveVicEntity(MinecraftServer server, UUID ignoreUuid) {
        if (server == null) return null;

        if (this.activeVicUuid != null && !this.activeVicUuid.equals(ignoreUuid)) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntityInAnyDimension(this.activeVicUuid);
                if (e != null && e.isAlive() && !e.isRemoved() && isVicCharacter(e)) {
                    return e;
                }
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            List<? extends Entity> found = level.getEntities(
                    EntityTypeTest.forClass(Entity.class),
                    e -> e.isAlive() && !e.isRemoved() && isVicCharacter(e) && !e.getUUID().equals(ignoreUuid)
            );
            if (!found.isEmpty()) {
                Entity first = found.getFirst();
                this.activeVicUuid = first.getUUID();
                setDirty();
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
        setDirty();
    }

    public UUID getActiveVicUuid() {
        return activeVicUuid;
    }

    public int getKarma() {
        return karma;
    }

    public void addKarma(int delta) {
        this.karma = Math.max(-100, Math.min(100, this.karma + delta));
        setDirty();
    }

    public void setKarma(int karma) {
        this.karma = Math.max(-100, Math.min(100, karma));
        setDirty();
    }

    public int getCurrentDay() {
        return currentDay;
    }

    public void setCurrentDay(int day) {
        this.currentDay = Math.max(1, day);
        setDirty();
    }

    public void resetCountdownToDay(int day, long currentWorldTick) {
        this.currentDay = Math.max(1, day);
        this.countdownActive = true;
        this.activationTimeTick = currentWorldTick - (this.currentDay - 1L) * 24000L;
        this.demonAwakened = false;
        this.demonDefeated = false;
        setDirty();
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public void setCountdownActive(boolean active) {
        this.countdownActive = active;
        setDirty();
    }

    public void activateCountdown(long currentWorldTick) {
        if (!this.countdownActive) {
            this.countdownActive = true;
            this.activationTimeTick = currentWorldTick;
            this.currentDay = 1;
            this.demonDefeated = false;
            setDirty();
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
        setDirty();
    }

    public boolean isDemonDefeated() {
        return demonDefeated;
    }

    public void setDemonDefeated(boolean defeated) {
        this.demonDefeated = defeated;
        setDirty();
    }

    public int computeStage(int countdownDays) {
        if (demonDefeated) {
            return 0;
        }
        if (demonAwakened || countdownDays <= 0) {
            return 4;
        }
        float progress = (float) (currentDay - 1) / (float) countdownDays;
        int baseStage = (int) Math.floor(progress * 4.0f);
        if (karma <= -30) {
            baseStage = Math.min(4, baseStage + 1);
        } else if (karma >= 40) {
            baseStage = Math.max(0, baseStage - 1);
        }
        return Math.clamp(baseStage, 0, 4);
    }

    public int getCorruptionStage() {
        if (demonDefeated) {
            return 0;
        }
        if (demonAwakened || currentDay >= 5) {
            return 4;
        }
        return switch (currentDay) {
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            default -> 4;
        };
    }
}
