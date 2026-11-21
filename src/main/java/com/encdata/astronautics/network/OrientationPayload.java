package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

public record OrientationPayload(int playerId, Quaternionf orientation) implements CustomPacketPayload {
    public static final Type<OrientationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "orientation"));
    public static final StreamCodec<FriendlyByteBuf, OrientationPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.playerId());
                buffer.writeQuaternion(payload.orientation());
            },
            buffer -> new OrientationPayload(buffer.readVarInt(), buffer.readQuaternion()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
