package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.SpaceGridAddress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpaceGridPoseRebasePayload(long revision, SpaceGridAddress address,
                                         double x, double y, double z) implements CustomPacketPayload {
    public static final Type<SpaceGridPoseRebasePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "space_grid_pose_rebase"));
    public static final StreamCodec<FriendlyByteBuf, SpaceGridPoseRebasePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarLong(payload.revision());
                        buffer.writeUtf(payload.address().x().toString(), 4096);
                        buffer.writeUtf(payload.address().y().toString(), 4096);
                        buffer.writeUtf(payload.address().z().toString(), 4096);
                        buffer.writeDouble(payload.x());
                        buffer.writeDouble(payload.y());
                        buffer.writeDouble(payload.z());
                    },
                    buffer -> new SpaceGridPoseRebasePayload(buffer.readVarLong(),
                            new SpaceGridAddress(new java.math.BigInteger(buffer.readUtf(4096)),
                                    new java.math.BigInteger(buffer.readUtf(4096)),
                                    new java.math.BigInteger(buffer.readUtf(4096))),
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
