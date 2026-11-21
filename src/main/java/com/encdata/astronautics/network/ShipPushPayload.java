package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record ShipPushPayload(Vec3 hold, Vec3 velocityChange) implements CustomPacketPayload {
    public static final Type<ShipPushPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "ship_push"));
    public static final StreamCodec<FriendlyByteBuf, ShipPushPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVec3(payload.hold());
                buffer.writeVec3(payload.velocityChange());
            },
            buffer -> new ShipPushPayload(buffer.readVec3(), buffer.readVec3()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
