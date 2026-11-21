package com.encdata.astronautics.space;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3dc;

public final class SpaceShipIntegration {
    private SpaceShipIntegration() {}

    static boolean isInsideShip(Level level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos) != null;
    }

    public static SubLevel containing(Entity entity) {
        SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing != null) return containing;

        containing = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        return containing != null ? containing : Sable.HELPER.getTrackingSubLevel(entity);
    }

    static SubLevel containing(Entity entity, BlockPos attachmentPosition) {
        SubLevel containing = Sable.HELPER.getContaining(entity.level(), attachmentPosition);
        return containing != null ? containing : containing(entity);
    }

    private static ServerSubLevel nearestShip(Level level, Vec3 position, double maxDistance) {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        double maxDistanceSquared = maxDistance * maxDistance;
        ServerSubLevel nearest = null;
        double nearestDistanceSquared = maxDistanceSquared;
        for (var candidate : container.getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel subLevel)) continue;
            if (subLevel.isRemoved()) continue;
            var bounds = subLevel.boundingBox();
            double dx = Math.max(Math.max(bounds.minX() - position.x, 0.0), position.x - bounds.maxX());
            double dy = Math.max(Math.max(bounds.minY() - position.y, 0.0), position.y - bounds.maxY());
            double dz = Math.max(Math.max(bounds.minZ() - position.z, 0.0), position.z - bounds.maxZ());
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = subLevel;
            }
        }
        return nearest;
    }

    public static ServerSubLevel findLiveShip(Entity entity, double maxDistance) {
        if (entity == null || entity.level().isClientSide()) return null;

        ServerLevel spaceLevel = spaceLevel(entity);
        if (spaceLevel != null) {
            SubLevel direct = containing(entity);
            if (direct instanceof ServerSubLevel ship && isLiveShip(ship, spaceLevel)
                    && isWithinRange(entity, ship, maxDistance)) return ship;

            var container = SubLevelContainer.getContainer(spaceLevel);
            if (container == null) return null;
            ServerSubLevel nearest = null;
            double nearestDistance = maxDistance * maxDistance;
            for (var candidate : container.getAllSubLevels()) {
                if (!(candidate instanceof ServerSubLevel ship) || !isLiveShip(ship, spaceLevel)) continue;
                double distance = logicalDistanceToShipSqr(entity, ship, maxDistance);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = ship;
                }
            }
            return nearest;
        }

        SubLevel direct = containing(entity);
        if (direct instanceof ServerSubLevel serverSubLevel
                && isLiveShip(serverSubLevel, entity.level())
                && isWithinRange(entity, serverSubLevel, maxDistance)) {
            return serverSubLevel;
        }

        return nearestShip(entity.level(), entity.position(), maxDistance);
    }

    public static boolean isWithinRange(Entity entity, ServerSubLevel ship, double range) {
        if (entity == null || ship == null || ship.isRemoved() || !Double.isFinite(range) || range < 0.0) {
            return false;
        }
        ServerLevel spaceLevel = spaceLevel(entity);
        if (spaceLevel != null && ship.getLevel() == spaceLevel) {
            return logicalDistanceToShipSqr(entity, ship, range) <= range * range;
        }
        return entity.level() == ship.getLevel()
                && ship.boundingBox().toMojang().distanceToSqr(entity.position()) <= range * range;
    }

    private static ServerLevel spaceLevel(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !SpaceConstants.isSpace(level)) return null;
        return SpaceGridLevelFactory.spaceLevel(level.getServer());
    }

    private static double logicalDistanceToShipSqr(Entity entity, ServerSubLevel ship, double maxDistance) {
        if (!Double.isFinite(maxDistance) || maxDistance < 0.0) return Double.POSITIVE_INFINITY;
        SpacePosition entityPosition = SpaceEntityState.position(entity);
        SpacePosition shipPosition = SpaceProjection.shipPosition(ship);
        Vector3dc pose = ship.logicalPose().position();
        double minX = 0.0, minY = 0.0, minZ = 0.0, maxX = 0.0, maxY = 0.0, maxZ = 0.0;
        if (hasBounds(ship)) {
            var bounds = ship.boundingBox().toMojang();
            Vec3 center = bounds.getCenter();
            double shiftX = gridShift(center.x - pose.x());
            double shiftY = gridShift(center.y - pose.y());
            double shiftZ = gridShift(center.z - pose.z());
            minX = bounds.minX - shiftX - pose.x();
            minY = bounds.minY - shiftY - pose.y();
            minZ = bounds.minZ - shiftZ - pose.z();
            maxX = bounds.maxX - shiftX - pose.x();
            maxY = bounds.maxY - shiftY - pose.y();
            maxZ = bounds.maxZ - shiftZ - pose.z();
        }
        double radius = Math.sqrt(
                Math.max(minX * minX, maxX * maxX)
                        + Math.max(minY * minY, maxY * maxY)
                        + Math.max(minZ * minZ, maxZ * maxZ));
        SpaceVec3 delta = entityPosition.deltaWithin(shipPosition, maxDistance + radius).orElse(null);
        if (delta == null) return Double.POSITIVE_INFINITY;

        double dx = axisDistance(0.0, delta.x() + minX, delta.x() + maxX);
        double dy = axisDistance(0.0, delta.y() + minY, delta.y() + maxY);
        double dz = axisDistance(0.0, delta.z() + minZ, delta.z() + maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean hasBounds(SubLevel ship) {
        var bounds = ship.boundingBox();
        return bounds.minX() != 0.0 || bounds.maxX() != 0.0 || bounds.minY() != 0.0
                || bounds.maxY() != 0.0 || bounds.minZ() != 0.0 || bounds.maxZ() != 0.0;
    }

    private static double gridShift(double offset) {
        return Math.round(offset / SpacePosition.GRID_SIZE) * SpacePosition.GRID_SIZE;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        return value < minimum ? minimum - value : value > maximum ? value - maximum : 0.0;
    }

    private static boolean isLiveShip(ServerSubLevel subLevel, Level level) {
        if (subLevel.isRemoved() || subLevel.getLevel() != level) return false;
        var container = SubLevelContainer.getContainer(level);
        return container != null && container.getSubLevel(subLevel.getUniqueId()) == subLevel;
    }

}
