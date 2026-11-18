package com.vicmod.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public record VicSpeechPayload(String text, int stage) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VicSpeechPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("vic", "speech")
    );

    public static final StreamCodec<ByteBuf, VicSpeechPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VicSpeechPayload::text,
            ByteBufCodecs.VAR_INT, VicSpeechPayload::stage,
            VicSpeechPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, STREAM_CODEC);
    }

    public static void sendTo(ServerPlayer player, String text, int stage) {
        if (text != null && !text.isBlank() && ServerPlayNetworking.canSend(player, TYPE)) {
            ServerPlayNetworking.send(player, new VicSpeechPayload(text, Math.clamp(stage, 0, 3)));
        }
    }
}
