package com.encdata.astronautics.space;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundStopTrackingSubLevelPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class DimensionTransferTracking {
    private static final List<PendingSync> PENDING = new ArrayList<>();

    private DimensionTransferTracking() {}

    static void clear() {
        PENDING.clear();
    }

    static void schedule(ServerLevel destination, Set<UUID> playerIds) {
        PENDING.add(new PendingSync(destination, 2, playerIds));
    }

    static void tick() {
        Iterator<PendingSync> pendingSyncs = PENDING.iterator();
        while (pendingSyncs.hasNext()) {
            PendingSync pending = pendingSyncs.next();
            if (pending.ticksRemaining > 0) {
                pending.ticksRemaining--;
                continue;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(pending.destination);
            if (container != null) {
                resetDestinationTracking(pending.destination, container, pending.playerIds);
                container.trackingSystem().tick(container);
            }
            pendingSyncs.remove();
        }
    }

    private static void resetDestinationTracking(ServerLevel destination, ServerSubLevelContainer container,
                                                 Set<UUID> playerIds) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID playerId : playerIds) {
            ServerPlayer player = destination.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) players.add(player);
        }

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            int plotX = subLevel.getPlot().plotPos.x - container.getOrigin().x;
            int plotZ = subLevel.getPlot().plotPos.z - container.getOrigin().y;
            long plotCoordinate = ChunkPos.asLong(plotX, plotZ);
            for (ServerPlayer player : players) {
                player.connection.send(new ClientboundCustomPayloadPacket(
                        new ClientboundStopTrackingSubLevelPacket(plotCoordinate)));
            }
            subLevel.getTrackingPlayers().removeAll(playerIds);
        }
    }

    private static final class PendingSync {
        private final ServerLevel destination;
        private final Set<UUID> playerIds;
        private int ticksRemaining;

        private PendingSync(ServerLevel destination, int ticksRemaining, Set<UUID> playerIds) {
            this.destination = destination;
            this.ticksRemaining = ticksRemaining;
            this.playerIds = playerIds;
        }
    }
}
