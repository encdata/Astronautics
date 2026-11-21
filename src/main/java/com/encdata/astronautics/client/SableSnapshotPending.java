package com.encdata.astronautics.client;

import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.packets.ClientboundSableSnapshotDualPacket;
import dev.ryanhcode.sable.network.packets.PacketReceiveMode;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class SableSnapshotPending {
    private static final long MAX_AGE_NANOS = 5_000_000_000L;
    private static final Map<Level, Map<Long, PendingSnapshot>> PENDING = new WeakHashMap<>();

    private SableSnapshotPending() {}

    public static synchronized void store(Level level, int interpolationTick,
                                          ClientboundSableSnapshotDualPacket.Entry entry,
                                          PacketReceiveMode mode) {
        long now = System.nanoTime();
        Map<Long, PendingSnapshot> snapshots = PENDING.computeIfAbsent(level, ignored -> new HashMap<>());
        snapshots.values().removeIf(snapshot -> now - snapshot.receivedAt() > MAX_AGE_NANOS);
        snapshots.put(entry.plotCoordinate(), new PendingSnapshot(interpolationTick, entry, mode, now));
    }

    public static synchronized void apply(Level level, long plotCoordinate, int initialTick,
                                          ClientSubLevel subLevel) {
        Map<Long, PendingSnapshot> snapshots = PENDING.get(level);
        if (snapshots == null) return;
        PendingSnapshot snapshot = snapshots.remove(plotCoordinate);
        if (snapshots.isEmpty()) PENDING.remove(level);
        if (snapshot == null || snapshot.interpolationTick() <= initialTick
                || System.nanoTime() - snapshot.receivedAt() > MAX_AGE_NANOS) return;

        ClientSableInterpolationState interpolation =
                ((dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer)
                        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)).getInterpolation();
        interpolation.receiveSnapshot(subLevel, snapshot.interpolationTick(),
                snapshot.entry().pose(), snapshot.mode());
    }

    private record PendingSnapshot(int interpolationTick, ClientboundSableSnapshotDualPacket.Entry entry,
                                   PacketReceiveMode mode, long receivedAt) {}
}
