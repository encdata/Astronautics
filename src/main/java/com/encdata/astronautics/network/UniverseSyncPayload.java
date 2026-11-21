package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record UniverseSyncPayload(long catalogRevision, long serverGameTime, List<String> galaxies)
        implements CustomPacketPayload {
    private static final int MAX_GALAXIES = 256;
    private static final int MAX_GALAXY_JSON_LENGTH = 1_000_000;
    public static final Type<UniverseSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "universe_sync"));

    public static final StreamCodec<FriendlyByteBuf, UniverseSyncPayload> STREAM_CODEC =
            StreamCodec.of(UniverseSyncPayload::write, UniverseSyncPayload::read);

    private static void write(FriendlyByteBuf buffer, UniverseSyncPayload payload) {
        if (payload.galaxies().size() > MAX_GALAXIES) {
            throw new IllegalArgumentException("Universe sync has too many galaxies: " + payload.galaxies().size());
        }
        buffer.writeVarLong(payload.catalogRevision());
        buffer.writeLong(payload.serverGameTime());
        buffer.writeVarInt(payload.galaxies().size());
        for (String galaxy : payload.galaxies()) {
            buffer.writeUtf(galaxy, MAX_GALAXY_JSON_LENGTH);
        }
    }

    private static UniverseSyncPayload read(FriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        long gameTime = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_GALAXIES) {
            throw new IllegalArgumentException("Invalid galaxy count: " + count);
        }
        List<String> galaxies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            galaxies.add(buffer.readUtf(MAX_GALAXY_JSON_LENGTH));
        }
        return new UniverseSyncPayload(revision, gameTime, galaxies);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
