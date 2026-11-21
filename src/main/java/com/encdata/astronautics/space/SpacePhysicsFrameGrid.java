package com.encdata.astronautics.space;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class SpacePhysicsFrameGrid {
    public static final double MAX_PRECISE_EXTENT = 32_768.0;
    public static final int RADIUS = 1;
    public static final int SPACING = 16_384;
    private static final double HALF_CELL = SPACING * 0.5;
    private static final double MAX_DELTA = Math.sqrt(3.0) * HALF_CELL;
    private static final List<Cell> CELLS = createCells();

    private SpacePhysicsFrameGrid() {}

    public static List<Cell> cells() {
        return CELLS;
    }

    public static double centerX(Cell cell) {
        return cell.x() * (double) SPACING;
    }

    public static double centerY(Cell cell) {
        return cell.y() * (double) SPACING;
    }

    public static double centerZ(Cell cell) {
        return cell.z() * (double) SPACING;
    }

    public static double extent() {
        return RADIUS * (double) SPACING + HALF_CELL;
    }

    public static boolean isPrecise() {
        return extent() < MAX_PRECISE_EXTENT;
    }

    public static Optional<Cell> nearestFree(Predicate<Cell> available) {
        return CELLS.stream().filter(available).findFirst();
    }

    public static Optional<SpaceVec3> toPhysicsPosition(
            Cell cell, SpacePosition frameOrigin, SpacePosition logicalPosition) {
        Optional<SpaceVec3> relative = frameOrigin.deltaWithin(logicalPosition, MAX_DELTA);
        if (relative.isEmpty()) return Optional.empty();

        SpaceVec3 offset = relative.get();
        if (Math.abs(offset.x()) > HALF_CELL
                || Math.abs(offset.y()) > HALF_CELL
                || Math.abs(offset.z()) > HALF_CELL) {
            return Optional.empty();
        }
        return Optional.of(new SpaceVec3(
                centerX(cell) + offset.x(),
                centerY(cell) + offset.y(),
                centerZ(cell) + offset.z()));
    }

    public static SpacePosition toLogicalPosition(
            Cell cell, SpacePosition frameOrigin, SpaceVec3 physicsPosition) {
        return frameOrigin.add(new SpaceVec3(
                physicsPosition.x() - centerX(cell),
                physicsPosition.y() - centerY(cell),
                physicsPosition.z() - centerZ(cell)));
    }

    private static List<Cell> createCells() {
        ArrayList<Cell> cells = new ArrayList<>((RADIUS * 2 + 1) * (RADIUS * 2 + 1) * (RADIUS * 2 + 1));
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) cells.add(new Cell(x, y, z));
            }
        }
        cells.sort(Comparator.comparingLong(Cell::distanceSquared)
                .thenComparingInt(Cell::x)
                .thenComparingInt(Cell::y)
                .thenComparingInt(Cell::z));
        return List.copyOf(cells);
    }

    public record Cell(int x, int y, int z) {
        private long distanceSquared() {
            return (long) x * x + (long) y * y + (long) z * z;
        }
    }
}
