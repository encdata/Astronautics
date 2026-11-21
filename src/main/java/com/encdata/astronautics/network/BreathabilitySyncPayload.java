package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BreathabilitySyncPayload(boolean breathable) implements CustomPacketPayload {
    public static final Type<BreathabilitySyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "breathability"));
    public static final StreamCodec<FriendlyByteBuf, BreathabilitySyncPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeBoolean(payload.breathable()),
                    buffer -> new BreathabilitySyncPayload(buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
