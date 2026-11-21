package com.encdata.astronautics.network;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.ReentryState;
import com.encdata.astronautics.space.ReentrySurfaceSample;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;

public record ReentryStatePayload(UUID shipId, ReentryState state) implements CustomPacketPayload {
    private static final int MAX_STRING_LENGTH = 256;
    private static final int MAX_SURFACE_SAMPLES = 128;

    public static final Type<ReentryStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "reentry_state"));
    public static final StreamCodec<FriendlyByteBuf, ReentryStatePayload> STREAM_CODEC =
            StreamCodec.of(ReentryStatePayload::write, ReentryStatePayload::read);

    private static void write(FriendlyByteBuf b, ReentryStatePayload p) {
        b.writeUUID(p.shipId());
        ReentryState s = p.state();
        b.writeBoolean(s.active());
        b.writeBoolean(s.planetDimension());
        writeVec(b, s.position());
        b.writeDouble(s.altitude());
        b.writeDouble(s.distanceToAtmosphere());
        b.writeDouble(s.inwardSpeed());
        b.writeDouble(s.speed());
        b.writeDouble(s.density());
        writeVec(b, s.planetDirection());
        writeVec(b, s.velocity());
        b.writeUtf(s.dimensionId(), MAX_STRING_LENGTH);
        b.writeUtf(s.planetId(), MAX_STRING_LENGTH);
        int count = Math.min(MAX_SURFACE_SAMPLES, s.surfaceSamples().size());
        b.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ReentrySurfaceSample sample = s.surfaceSamples().get(i);
            writeVec(b, sample.position());
            writeVec(b, sample.normal());
            b.writeFloat(sample.exposure());
        }
    }

    private static ReentryStatePayload read(FriendlyByteBuf b) {
        UUID id = b.readUUID();
        boolean active = b.readBoolean();
        boolean planet = b.readBoolean();
        Vec3 position = readVec(b);
        double altitude = b.readDouble();
        double distance = b.readDouble();
        double inward = b.readDouble();
        double speed = b.readDouble();
        double density = b.readDouble();
        Vec3 direction = readVec(b);
        Vec3 velocity = readVec(b);
        String dimensionId = b.readUtf(MAX_STRING_LENGTH);
        String planetId = b.readUtf(MAX_STRING_LENGTH);
        int count = b.readVarInt();
        if (count < 0 || count > MAX_SURFACE_SAMPLES) {
            throw new IllegalArgumentException("Invalid reentry surface count: " + count);
        }
        ArrayList<ReentrySurfaceSample> surfaceSamples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            surfaceSamples.add(new ReentrySurfaceSample(readVec(b), readVec(b), b.readFloat()));
        }
        return new ReentryStatePayload(id, new ReentryState(active, planet, position, altitude, distance,
                inward, speed, density, direction, velocity, dimensionId, planetId, surfaceSamples));
    }

    private static void writeVec(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
