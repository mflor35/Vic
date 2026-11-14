package com.vicmod.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VicWorldState extends PersistentState {
    private static final String KEY = "vic_world_state";

    private int karma = 25;
    private int currentDay = 1;
    private boolean countdownActive = false;
    private long activationTimeTick = 0L;
    private boolean demonAwakened = false;
    private final Set<UUID> welcomedPlayers = new HashSet<>();

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

        if (nbt.contains("welcomedPlayers", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("welcomedPlayers", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                try {
                    state.welcomedPlayers.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                }
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

        NbtList list = new NbtList();
        for (UUID uuid : welcomedPlayers) {
            list.add(NbtString.of(uuid.toString()));
        }
        nbt.put("welcomedPlayers", list);
        return nbt;
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

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public void activateCountdown(long currentWorldTick) {
        if (!this.countdownActive) {
            this.countdownActive = true;
            this.activationTimeTick = currentWorldTick;
            this.currentDay = 1;
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

    public boolean hasWelcomedPlayer(UUID uuid) {
        return welcomedPlayers.contains(uuid);
    }

    public void markPlayerWelcomed(UUID uuid) {
        welcomedPlayers.add(uuid);
        markDirty();
    }

    public int computeStage(int maxDays) {
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
