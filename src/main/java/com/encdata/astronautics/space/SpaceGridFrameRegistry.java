package com.encdata.astronautics.space;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class SpaceGridFrameRegistry {
    private static final double JOIN_DISTANCE = SpacePhysicsFrameGrid.SPACING * 0.1875;
    private static final int HOST_SEARCH = 64;

    private final ServerLevel spaceLevel;

    SpaceGridFrameRegistry(ServerLevel spaceLevel) {
        this.spaceLevel = spaceLevel;
    }

    Map<UUID, ServerSubLevel> loadedShips() {
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container == null) return Map.of();
        Map<UUID, ServerSubLevel> ships = new HashMap<>();
        for (Object object : container.getAllSubLevels()) {
            if (object instanceof ServerSubLevel ship) ships.put(ship.getUniqueId(), ship);
        }
        return ships;
    }

    Optional<FrameLocation> existingFrame(UUID shipId, SpaceGridAddress frameOwner) {
        if (frameOwner == null) return Optional.empty();
        ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), frameOwner);
        return SpaceGridFrameData.get(level).frameForShip(shipId)
                .map(frame -> new FrameLocation(level, frame));
    }

    Optional<FrameLocation> findFrame(SpacePosition position, SpaceGridAddress projection,
                                     UUID excludedFrame) {
        FrameLocation nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ServerLevel level : spaceLevel.getServer().getAllLevels()) {
            if (!SpaceConstants.isGridLevel(level.dimension())) continue;
            for (SpaceGridFrameData.Frame frame : SpaceGridFrameData.get(level).frames()) {
                if (frame.id().equals(excludedFrame) || !frame.projectionGrid().equals(projection)) continue;
                Optional<SpaceVec3> relative = frame.origin().deltaWithin(position,
                        Math.sqrt(3.0) * JOIN_DISTANCE);
                if (relative.isEmpty()) continue;
                SpaceVec3 delta = relative.get();
                if (Math.abs(delta.x()) > JOIN_DISTANCE
                        || Math.abs(delta.y()) > JOIN_DISTANCE
                        || Math.abs(delta.z()) > JOIN_DISTANCE) continue;
                double distance = delta.x() * delta.x() + delta.y() * delta.y() + delta.z() * delta.z();
                if (distance < nearestDistance) {
                    nearest = new FrameLocation(level, frame);
                    nearestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    List<FrameLocation> activeFrames(Set<UUID> activeShips) {
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        Map<UUID, FrameLocation> frames = new HashMap<>();
        for (UUID shipId : activeShips) {
            SpaceGridAddress owner = directory.frameOwnerAddress(shipId);
            if (owner == null) continue;
            ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), owner);
            SpaceGridFrameData.get(level).frameForShip(shipId)
                    .ifPresent(frame -> frames.putIfAbsent(frame.id(), new FrameLocation(level, frame)));
        }
        return new ArrayList<>(frames.values());
    }

    void dropMembership(ServerLevel frameLevel, UUID shipId) {
        SpaceGridFrameData.get(frameLevel).removeShip(shipId);
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        SpaceGridAddress levelAddress = SpaceGridLevelFactory.addressOf(
                spaceLevel.getServer(), frameLevel.dimension());
        if (levelAddress.equals(directory.frameOwnerAddress(shipId))) directory.removeFrameOwner(shipId);
    }

    FrameLocation allocate(SpacePosition position, SpaceGridAddress projection, UUID shipId) {
        ServerLevel level = frameHost(position.grid());
        return new FrameLocation(level,
                SpaceGridFrameData.get(level).allocate(position, projection, shipId));
    }

    ServerLevel frameHost(SpaceGridAddress preferred) {
        for (int step = 0; step < HOST_SEARCH; step++) {
            SpaceGridAddress address = preferred.offset(BigInteger.ZERO, BigInteger.valueOf(step), BigInteger.ZERO);
            ServerLevel level = SpaceGridLevelFactory.getOrCreate(spaceLevel.getServer(), address);
            if (SpaceGridFrameData.get(level).hasFreeCell()) return level;
        }
        throw new IllegalStateException("no free physics frame cells near space grid " + preferred);
    }
}
