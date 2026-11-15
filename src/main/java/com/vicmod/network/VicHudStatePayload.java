package com.vicmod.network;

import com.vicmod.VicMod;
import com.vicmod.config.VicConfig;
import com.vicmod.state.VicWorldState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public record VicHudStatePayload(
        boolean countdownActive,
        int currentDay,
        int maxDays,
        int karma,
        int stage,
        boolean demonAwakened
) implements CustomPayload {
    public static final Id<VicHudStatePayload> ID = new Id<>(Identifier.of(VicMod.MOD_ID, "hud_state"));

    public static final PacketCodec<RegistryByteBuf, VicHudStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, VicHudStatePayload::countdownActive,
            PacketCodecs.VAR_INT, VicHudStatePayload::currentDay,
            PacketCodecs.VAR_INT, VicHudStatePayload::maxDays,
            PacketCodecs.VAR_INT, VicHudStatePayload::karma,
            PacketCodecs.VAR_INT, VicHudStatePayload::stage,
            PacketCodecs.BOOL, VicHudStatePayload::demonAwakened,
            VicHudStatePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
    }

    public static VicHudStatePayload fromState(VicWorldState state) {
        int maxDays = Math.max(1, VicConfig.get().countdownDays);
        int stage = state.computeStage(maxDays);
        return new VicHudStatePayload(
                state.isCountdownActive(),
                state.getCurrentDay(),
                maxDays,
                state.getKarma(),
                stage,
                state.isDemonAwakened()
        );
    }

    public static void sendTo(ServerPlayerEntity player, VicWorldState state) {
        if (player != null && state != null && ServerPlayNetworking.canSend(player, ID)) {
            ServerPlayNetworking.send(player, fromState(state));
        }
    }

    public static void broadcast(MinecraftServer server, VicWorldState state) {
        if (server == null || state == null) return;
        VicHudStatePayload payload = fromState(state);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, ID)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
