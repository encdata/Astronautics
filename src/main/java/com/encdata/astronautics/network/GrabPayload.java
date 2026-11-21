package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record GrabPayload(int playerId, Vec3 hold, Direction face, boolean handle) implements CustomPacketPayload {
    public static final Type<GrabPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "grab"));
    public static final StreamCodec<FriendlyByteBuf, GrabPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.playerId());
                buffer.writeBoolean(payload.hold() != null);
                if (payload.hold() == null) return;
                buffer.writeVec3(payload.hold());
                buffer.writeEnum(payload.face());
                buffer.writeBoolean(payload.handle());
            },
            buffer -> {
                int playerId = buffer.readVarInt();
                return buffer.readBoolean()
                        ? new GrabPayload(playerId, buffer.readVec3(), buffer.readEnum(Direction.class), buffer.readBoolean())
                        : release(playerId);
            });

    public static GrabPayload release(int playerId) {
        return new GrabPayload(playerId, null, null, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
