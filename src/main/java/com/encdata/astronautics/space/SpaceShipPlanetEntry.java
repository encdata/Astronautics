package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpaceShipPlanetEntry {
    private static final double ENTRY_CLEARANCE = 150.0;
    private static final SpaceVec3 ARRIVAL = new SpaceVec3(0.5, Astronautics.PLANET_ARRIVAL_Y, 0.5);

    private SpaceShipPlanetEntry() {}

    public static void tick(ServerLevel space) {
        var container = SubLevelContainer.getContainer(space);
        if (container == null) return;
        List<ServerSubLevel> ships = List.copyOf(container.getAllSubLevels());
        if (ships.isEmpty()) return;

        double timeDays = space.getDayTime() / 24000.0;
        Map<ResourceLocation, SpaceVec3> bodyPositions = CelestialPositionResolver.resolveAll(
                SpaceCatalog.galaxies().values(), timeDays);
        Set<UUID> attempted = new HashSet<>();
        for (ServerSubLevel ship : ships) {
            if (ship.isRemoved() || attempted.contains(ship.getUniqueId())) continue;
            var bounds = ship.getPlot().getBoundingBox();
            double x = bounds.maxX() - bounds.minX();
            double y = bounds.maxY() - bounds.minY();
            double z = bounds.maxZ() - bounds.minZ();
            double shipRadius = Math.sqrt(x * x + y * y + z * z) * 0.5;
            Vector3d position = ship.logicalPose().position();
            SpaceVec3 universe = SpaceProjection.shipPosition(ship).originFrame();
            CelestialBodyData nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (var id : SpaceCatalog.bodyIds()) {
                var body = SpaceCatalog.body(id).orElse(null);
                if (body == null || body.dimension().isEmpty()) continue;
                SpaceVec3 bodyPosition = bodyPositions.get(id);
                if (bodyPosition == null) continue;
                double dx = universe.x() - bodyPosition.x();
                double dy = universe.y() - bodyPosition.y();
                double dz = universe.z() - bodyPosition.z();
                double distance = dx * dx + dy * dy + dz * dz;
                double radius = body.radius() + ENTRY_CLEARANCE + shipRadius;
                if (distance <= radius * radius && distance < nearestDistance) {
                    nearest = body;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) continue;
            ResourceKey<Level> destinationKey = ResourceKey.create(
                    Registries.DIMENSION, nearest.dimension().orElseThrow());
            ServerLevel destination = space.getServer().getLevel(destinationKey);
            if (destination == null) continue;
            attempted.add(ship.getUniqueId());
            for (var connected : SubLevelHelper.getConnectedChain(ship)) {
                attempted.add(connected.getUniqueId());
            }
            for (var dependency : SubLevelHelper.getLoadingDependencyChain(ship)) {
                attempted.add(dependency.getUniqueId());
            }
            if (AstronauticsDimensionTransfer.transferShip(null, destination, ARRIVAL, ship)) {
                Astronautics.LOGGER.info("Ship {} entered {} from {}", ship.getUniqueId(),
                        destination.dimension().location(), position);
            }
        }
    }
}
