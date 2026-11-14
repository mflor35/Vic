package com.vicmod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public record VicSpeechPayload(String text, int stage) implements CustomPayload {
    public static final Id<VicSpeechPayload> ID = new Id<>(Identifier.of("vic", "speech"));
    public static final PacketCodec<RegistryByteBuf, VicSpeechPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, VicSpeechPayload::text,
            PacketCodecs.VAR_INT, VicSpeechPayload::stage,
            VicSpeechPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ID, CODEC);
    }

    public static void sendTo(ServerPlayerEntity player, String text, int stage) {
        if (text != null && !text.isBlank() && ServerPlayNetworking.canSend(player, ID)) {
            ServerPlayNetworking.send(player, new VicSpeechPayload(text, Math.clamp(stage, 0, 3)));
        }
    }
}