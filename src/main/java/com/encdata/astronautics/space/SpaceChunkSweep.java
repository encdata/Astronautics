package com.encdata.astronautics.space;

import net.minecraft.util.Mth;

final class SpaceChunkSweep {
    private static final double MAX_LENGTH_BLOCKS = 2048.0;

    final double minX;
    final double minZ;
    final double maxX;
    final double maxZ;
    final double dx;
    final double dz;
    final int minRow;
    final int maxRow;
    int rowMin;
    int rowMax;

    private SpaceChunkSweep(double minX, double minZ, double maxX, double maxZ, double dx, double dz) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.dx = dx;
        this.dz = dz;
        minRow = Mth.floor(Math.min(minZ, minZ + dz)) >> 4;
        maxRow = Mth.floor(Math.max(maxZ, maxZ + dz)) >> 4;
    }

    static SpaceChunkSweep ofChunks(int chunkX, int chunkZ, int radius, double dx, double dz) {
        double epsilon = 1.0e-3;
        return of((chunkX - radius) * 16.0, (chunkZ - radius) * 16.0,
                (chunkX + radius + 1) * 16.0 - epsilon, (chunkZ + radius + 1) * 16.0 - epsilon, dx, dz);
    }

    static SpaceChunkSweep of(double minX, double minZ, double maxX, double maxZ, double dx, double dz) {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX > maxX || minZ > maxZ || Math.abs(minX) > 3.0e7 || Math.abs(minZ) > 3.0e7) {
            return null;
        }
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
            dx = 0.0;
            dz = 0.0;
        }

        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > MAX_LENGTH_BLOCKS) {
            dx *= MAX_LENGTH_BLOCKS / length;
            dz *= MAX_LENGTH_BLOCKS / length;
        }
        return new SpaceChunkSweep(minX, minZ, maxX, maxZ, dx, dz);
    }

    boolean overlaps(double minX, double minZ, double maxX, double maxZ) {
        return Math.max(this.maxX, this.maxX + dx) >= minX
                && Math.min(this.minX, this.minX + dx) <= maxX
                && Math.max(this.maxZ, this.maxZ + dz) >= minZ
                && Math.min(this.minZ, this.minZ + dz) <= maxZ;
    }

    boolean row(int z) {
        double rowStart = z * 16.0;
        double rowEnd = rowStart + 16.0;
        double from = 0.0;
        double to = 1.0;

        if (dz > 0.0) {
            from = Math.max(from, (rowStart - maxZ) / dz);
            to = Math.min(to, (rowEnd - minZ) / dz);
        } else if (dz < 0.0) {
            from = Math.max(from, (rowEnd - minZ) / dz);
            to = Math.min(to, (rowStart - maxZ) / dz);
        } else if (maxZ < rowStart || minZ >= rowEnd) {
            return false;
        }

        if (from > to) return false;

        double a = from * dx;
        double b = to * dx;
        rowMin = Mth.floor(minX + Math.min(a, b)) >> 4;
        rowMax = Mth.floor(maxX + Math.max(a, b)) >> 4;
        return true;
    }
}
