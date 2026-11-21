package com.encdata.astronautics.space;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

final class DimensionTransferGeometry {
    private DimensionTransferGeometry() {}

    static BlockPos toPlotOffset(ServerSubLevel subLevel, BlockPos position) {
        return position.subtract(plotOrigin(subLevel));
    }

    static Vector3d toPlotOffset(ServerSubLevel subLevel, Vec3 position) {
        BlockPos origin = plotOrigin(subLevel);
        return new Vector3d(position.x - origin.getX(), position.y - origin.getY(),
                position.z - origin.getZ());
    }

    static Vector3d destinationPlotPosition(ServerSubLevel subLevel, Vector3d plotOffset) {
        BlockPos origin = plotOrigin(subLevel);
        return new Vector3d(origin.getX() + plotOffset.x, origin.getY() + plotOffset.y,
                origin.getZ() + plotOffset.z);
    }

    static BlockPos destinationSeatPosition(ServerSubLevel subLevel, BlockPos plotOffset) {
        return plotOrigin(subLevel).offset(plotOffset.getX(), plotOffset.getY(), plotOffset.getZ());
    }

    private static BlockPos plotOrigin(ServerSubLevel subLevel) {
        BlockPos minimum = subLevel.getPlot().getChunkMin().getWorldPosition();
        ServerLevel level = subLevel.getLevel();
        int centerY = (level.getMinBuildHeight() + level.getMaxBuildHeight()) / 2;
        return new BlockPos(minimum.getX(), centerY, minimum.getZ());
    }

    static ServerSubLevel nearestInChain(
            Vec3 position, Collection<ServerSubLevel> chain, double maxDistance) {
        double limit = maxDistance * maxDistance;
        ServerSubLevel nearest = null;
        for (ServerSubLevel subLevel : chain) {
            var bounds = subLevel.boundingBox();
            double dx = Math.max(Math.max(bounds.minX() - position.x, 0.0), position.x - bounds.maxX());
            double dy = Math.max(Math.max(bounds.minY() - position.y, 0.0), position.y - bounds.maxY());
            double dz = Math.max(Math.max(bounds.minZ() - position.z, 0.0), position.z - bounds.maxZ());
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= limit) {
                limit = distance;
                nearest = subLevel;
            }
        }
        return nearest;
    }
}
