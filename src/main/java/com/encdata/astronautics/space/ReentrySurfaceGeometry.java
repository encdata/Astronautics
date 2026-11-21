package com.encdata.astronautics.space;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ReentrySurfaceGeometry {
    private static final int MAX_CACHED_FACES = 2048;
    private static final int MAX_SAMPLES = 128;
    private static final double CELL_SIZE = 2.0;

    record LocalFace(Vec3 position, Vec3 normal) {}
    record CachedFaces(long tick, List<LocalFace> faces) {}

    private ReentrySurfaceGeometry() {}

    static CachedFaces build(ServerSubLevel ship, long tick) {
        Level level = ship.getLevel();
        var bounds = ship.getPlot().getBoundingBox();
        List<LocalFace> faces = new ArrayList<>();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).isAir()) continue;
                    for (Direction direction : Direction.values()) {
                        if (!level.getBlockState(pos.relative(direction)).isAir()) continue;
                        Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                        Vec3 center = new Vec3(x + 0.5 + normal.x * 0.5,
                                y + 0.5 + normal.y * 0.5,
                                z + 0.5 + normal.z * 0.5);
                        faces.add(new LocalFace(center, normal));
                    }
                }
            }
        }
        if (faces.size() <= MAX_CACHED_FACES) return new CachedFaces(tick, List.copyOf(faces));

        List<LocalFace> sampled = new ArrayList<>(MAX_CACHED_FACES);
        double stride = faces.size() / (double) MAX_CACHED_FACES;
        for (int i = 0; i < MAX_CACHED_FACES; i++) {
            sampled.add(faces.get((int) (i * stride)));
        }
        return new CachedFaces(tick, List.copyOf(sampled));
    }

    static List<ReentrySurfaceSample> windwardSamples(ServerSubLevel ship, List<LocalFace> faces,
                                                       Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0e-8) return List.of();
        Vector3d localVelocity = new Vector3d(velocity.x, velocity.y, velocity.z);
        ship.logicalPose().orientation().transformInverse(localVelocity);
        Vec3 airflow = new Vec3(localVelocity.x, localVelocity.y, localVelocity.z).normalize();
        Vector3d origin = ship.logicalPose().position();
        List<ReentrySurfaceSample> samples = new ArrayList<>();
        for (LocalFace face : faces) {
            float exposure = (float) face.normal().dot(airflow);
            if (exposure < 0.12f) continue;
            Vector3d local = new Vector3d(face.position().x, face.position().y, face.position().z);
            Vector3d normal = new Vector3d(face.normal().x, face.normal().y, face.normal().z);
            ship.logicalPose().orientation().transform(local);
            ship.logicalPose().orientation().transform(normal);
            samples.add(new ReentrySurfaceSample(
                    new Vec3(origin.x + local.x, origin.y + local.y, origin.z + local.z),
                    new Vec3(normal.x, normal.y, normal.z).normalize(), exposure));
        }
        samples.sort(Comparator.comparingDouble(ReentrySurfaceSample::exposure).reversed());
        if (samples.size() <= MAX_SAMPLES) return List.copyOf(samples);

        List<ReentrySurfaceSample> selected = new ArrayList<>(MAX_SAMPLES);
        Set<Long> occupiedCells = new HashSet<>();
        Set<ReentrySurfaceSample> selectedSamples = new HashSet<>();
        for (ReentrySurfaceSample sample : samples) {
            Vec3 position = sample.position();
            long cell = BlockPos.asLong(
                    (int) Math.floor(position.x / CELL_SIZE),
                    (int) Math.floor(position.y / CELL_SIZE),
                    (int) Math.floor(position.z / CELL_SIZE));
            if (occupiedCells.add(cell)) {
                selected.add(sample);
                selectedSamples.add(sample);
                if (selected.size() == MAX_SAMPLES) return List.copyOf(selected);
            }
        }
        for (ReentrySurfaceSample sample : samples) {
            if (selected.size() == MAX_SAMPLES) break;
            if (selectedSamples.add(sample)) selected.add(sample);
        }
        return List.copyOf(selected);
    }

    static Direction leadingFace(ServerSubLevel ship, Vector3d worldVelocity) {
        Vector3d local = new Vector3d(worldVelocity);
        ship.logicalPose().orientation().transformInverse(local);
        double x = Math.abs(local.x);
        double y = Math.abs(local.y);
        double z = Math.abs(local.z);
        if (y >= x && y >= z) return local.y < 0.0 ? Direction.DOWN : Direction.UP;
        if (x >= z) return local.x < 0.0 ? Direction.WEST : Direction.EAST;
        return local.z < 0.0 ? Direction.NORTH : Direction.SOUTH;
    }

    static List<BlockPos> leadingSurface(ServerSubLevel ship, Direction face) {
        var bounds = ship.getPlot().getBoundingBox();
        List<BlockPos> result = new ArrayList<>();
        switch (face) {
            case DOWN, UP -> {
                int start = face == Direction.DOWN ? bounds.minY() : bounds.maxY();
                int end = face == Direction.DOWN ? bounds.maxY() : bounds.minY();
                int step = face == Direction.DOWN ? 1 : -1;
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        addFirstSolid(ship, result, x, z, start, end, step, face);
                    }
                }
            }
            case NORTH, SOUTH -> {
                int start = face == Direction.NORTH ? bounds.minZ() : bounds.maxZ();
                int end = face == Direction.NORTH ? bounds.maxZ() : bounds.minZ();
                int step = face == Direction.NORTH ? 1 : -1;
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        addFirstSolid(ship, result, x, y, start, end, step, face);
                    }
                }
            }
            case WEST, EAST -> {
                int start = face == Direction.WEST ? bounds.minX() : bounds.maxX();
                int end = face == Direction.WEST ? bounds.maxX() : bounds.minX();
                int step = face == Direction.WEST ? 1 : -1;
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        addFirstSolid(ship, result, z, y, start, end, step, face);
                    }
                }
            }
        }
        return result;
    }

    private static void addFirstSolid(ServerSubLevel ship, List<BlockPos> result,
                                      int a, int b, int start, int end, int step, Direction face) {
        Level level = ship.getLevel();
        for (int value = start; step > 0 ? value <= end : value >= end; value += step) {
            BlockPos pos = switch (face.getAxis()) {
                case Y -> new BlockPos(a, value, b);
                case Z -> new BlockPos(a, b, value);
                case X -> new BlockPos(value, b, a);
            };
            if (!level.getBlockState(pos).isAir()) {
                result.add(pos);
                return;
            }
        }
    }

}
