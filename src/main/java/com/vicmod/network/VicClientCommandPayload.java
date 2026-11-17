package com.vicmod.network;

import com.vicmod.VicMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VicClientCommandPayload(String command) implements CustomPayload {
    public static final Id<VicClientCommandPayload> ID = new Id<>(Identifier.of(VicMod.MOD_ID, "client_command"));
    public static final PacketCodec<RegistryByteBuf, VicClientCommandPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, VicClientCommandPayload::command,
            VicClientCommandPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
    }
}
