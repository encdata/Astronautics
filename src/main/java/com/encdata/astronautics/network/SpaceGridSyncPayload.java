package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.SpaceGridAddress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;

public record SpaceGridSyncPayload(long revision, SpaceGridAddress address) implements CustomPacketPayload {
    private static final int MAX_INDEX_LENGTH = 4096;
    public static final Type<SpaceGridSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "space_grid"));
    public static final StreamCodec<FriendlyByteBuf, SpaceGridSyncPayload> STREAM_CODEC =
            StreamCodec.of(SpaceGridSyncPayload::write, SpaceGridSyncPayload::read);

    private static void write(FriendlyByteBuf buffer, SpaceGridSyncPayload payload) {
        buffer.writeVarLong(payload.revision());
        buffer.writeUtf(payload.address().x().toString(), MAX_INDEX_LENGTH);
        buffer.writeUtf(payload.address().y().toString(), MAX_INDEX_LENGTH);
        buffer.writeUtf(payload.address().z().toString(), MAX_INDEX_LENGTH);
    }

    private static SpaceGridSyncPayload read(FriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        return new SpaceGridSyncPayload(revision, new SpaceGridAddress(
                readIndex(buffer), readIndex(buffer), readIndex(buffer)));
    }

    private static BigInteger readIndex(FriendlyByteBuf buffer) {
        String value = buffer.readUtf(MAX_INDEX_LENGTH);
        if (value.length() > MAX_INDEX_LENGTH) throw new IllegalArgumentException("space grid index is too long");
        BigInteger index = new BigInteger(value);
        if (!index.toString().equals(value)) throw new IllegalArgumentException("space grid index is not canonical");
        return index;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
