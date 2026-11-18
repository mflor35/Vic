package com.vicmod.network;

import com.vicmod.VicMod;
import com.vicmod.config.VicConfig;
import com.vicmod.state.VicWorldState;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public record VicHudStatePayload(
        boolean countdownActive,
        int currentDay,
        int maxDays,
        int karma,
        int stage,
        boolean demonAwakened
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VicHudStatePayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(VicMod.MOD_ID, "hud_state")
    );

    public static final StreamCodec<ByteBuf, VicHudStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, VicHudStatePayload::countdownActive,
            ByteBufCodecs.VAR_INT, VicHudStatePayload::currentDay,
            ByteBufCodecs.VAR_INT, VicHudStatePayload::maxDays,
            ByteBufCodecs.VAR_INT, VicHudStatePayload::karma,
            ByteBufCodecs.VAR_INT, VicHudStatePayload::stage,
            ByteBufCodecs.BOOL, VicHudStatePayload::demonAwakened,
            VicHudStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
    }

    public static VicHudStatePayload fromState(VicWorldState state) {
        int maxDays = Math.max(1, VicConfig.get().countdownDays);
        int stage = state.isDemonDefeated() ? 5 : state.getCorruptionStage();
        return new VicHudStatePayload(
                state.isCountdownActive(),
                state.getCurrentDay(),
                maxDays,
                state.getKarma(),
                stage,
                state.isDemonAwakened()
        );
    }

    public static void sendTo(ServerPlayer player, VicWorldState state) {
        if (player != null && state != null && ServerPlayNetworking.canSend(player, TYPE)) {
            ServerPlayNetworking.send(player, fromState(state));
        }
    }

    public static void broadcast(MinecraftServer server, VicWorldState state) {
        if (server == null || state == null) return;
        VicHudStatePayload payload = fromState(state);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
