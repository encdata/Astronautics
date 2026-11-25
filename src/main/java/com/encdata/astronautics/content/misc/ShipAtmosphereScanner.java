package com.encdata.astronautics.content.misc;

import com.encdata.astronautics.content.blocks.atmospheric_control.AtmosphericControlUnitBlockEntity;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ShipAtmosphereScanner {
    private static final double OXYGEN_PER_CELL = 100.0;
    private static final int MAX_SCAN_CELLS_PER_SHIP = 131_072;

    private ShipAtmosphereScanner() {}

    static ShipAtmosphere scanRooms(Map<Long, UnitRef> units, ShipAtmosphere previous) {
        List<Seed> seeds = new ArrayList<>();
        ServerSubLevel subLevel = null;
        for (var entry : units.entrySet()) {
            BlockPos pos = entry.getValue().unit.getBlockPos();
            List<BlockPos> starts = new ArrayList<>();
            for (Direction direction : Direction.values()) starts.add(pos.relative(direction));
            seeds.add(new Seed(pos, starts, entry.getKey()));
            subLevel = entry.getValue().subLevel;
        }
        return subLevel == null ? new ShipAtmosphere(List.of()) : scanRooms(subLevel, seeds, previous);
    }

    static ShipAtmosphere scanWorldRooms(Level level,
                                         Map<Long, AtmosphericControlUnitBlockEntity> units,
                                         ShipAtmosphere previous) {
        List<Seed> seeds = new ArrayList<>();
        for (var entry : units.entrySet()) {
            BlockPos pos = entry.getValue().getBlockPos();
            List<BlockPos> starts = new ArrayList<>();
            for (Direction direction : Direction.values()) starts.add(pos.relative(direction));
            seeds.add(new Seed(pos, starts, entry.getKey()));
        }
        return scanRooms(level, seeds, previous, null);
    }

    static ShipAtmosphere scanFromPorts(ServerSubLevel subLevel, Set<Long> ports, ShipAtmosphere previous) {
        Level level = subLevel.getLevel();
        List<Seed> seeds = new ArrayList<>();
        for (long port : ports) {
            BlockPos pos = BlockPos.of(port);
            Direction front = front(level.getBlockState(pos));
            if (front == null) continue;
            List<BlockPos> candidates = new ArrayList<>();
            BlockPos backing = pos.relative(front.getOpposite());
            for (Direction direction : Direction.values()) {
                if (direction == front) continue;
                candidates.add(pos.relative(direction));
                if (direction != front.getOpposite()) candidates.add(backing.relative(direction));
            }
            candidates.add(backing.relative(front.getOpposite()));
            List<BlockPos> starts = new ArrayList<>();
            for (BlockPos candidate : candidates) {
                if (isOpen(level, candidate) && opensIntoSealedRoom(subLevel, candidate)) starts.add(candidate);
            }
            seeds.add(new Seed(pos, starts, null));
        }
        return scanRooms(subLevel, seeds, previous);
    }

    private static boolean opensIntoSealedRoom(ServerSubLevel subLevel, BlockPos start) {
        Level level = subLevel.getLevel();
        var bounds = subLevel.getPlot().getBoundingBox();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (outside(bounds, pos)) return false;
            if (visited.contains(pos) || !isOpen(level, pos)) continue;
            visited.add(pos);
            if (visited.size() > MAX_SCAN_CELLS_PER_SHIP) return false;
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }
        return !visited.isEmpty();
    }

    private static ShipAtmosphere scanRooms(ServerSubLevel subLevel, List<Seed> seeds, ShipAtmosphere previous) {
        return scanRooms(subLevel.getLevel(), seeds, previous, subLevel.getPlot().getBoundingBox());
    }

    private static ShipAtmosphere scanRooms(Level level, List<Seed> seeds,
                                             ShipAtmosphere previous, BoundingBox3ic bounds) {
        List<Compartment> rooms = new ArrayList<>();
        int remainingCells = MAX_SCAN_CELLS_PER_SHIP;
        for (Seed seed : seeds) {
            Compartment adjacent = adjacentRoom(rooms, seed);
            if (adjacent != null) {
                seed.addTo(adjacent);
                continue;
            }

            Compartment scanned = scan(level, bounds, seed, remainingCells);
            remainingCells -= scanned.cells.size();
            seed.addTo(scanned);
            Compartment match = null;
            for (Compartment room : rooms) {
                if (room.overlapSize(scanned) > 0) {
                    match = room;
                    break;
                }
            }
            if (match == null) rooms.add(scanned);
            else match.merge(scanned);
        }

        if (previous != null) {
            Set<Compartment> retained = new HashSet<>();
            for (Compartment room : rooms) {
                Compartment best = null;
                int largestOverlap = 0;
                for (Compartment oldRoom : previous.rooms) {
                    if (retained.contains(oldRoom)) continue;
                    int overlap = room.overlapSize(oldRoom);
                    if (overlap > largestOverlap) {
                        largestOverlap = overlap;
                        best = oldRoom;
                    }
                }
                if (best != null) {
                    room.oxygen = Math.min(room.capacity(), best.oxygen);
                    retained.add(best);
                }
            }
        }
        return new ShipAtmosphere(rooms);
    }

    private static Compartment adjacentRoom(List<Compartment> rooms, Seed seed) {
        for (BlockPos start : seed.starts) {
            for (Compartment room : rooms) {
                if (room.cells.contains(start)) return room;
            }
        }
        return null;
    }

    private static Compartment scan(Level level, BoundingBox3ic bounds, Seed seed, int remainingCells) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> connectors = new HashSet<>();
        Set<BlockPos> walls = new HashSet<>();
        Set<Long> mounted = new HashSet<>();
        boolean sealed = true;
        queue.addAll(seed.starts);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (bounds != null && outside(bounds, pos)) {
                sealed = false;
                continue;
            }
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;
            if (visited.contains(pos)) continue;
            if (!level.hasChunkAt(pos)) {
                sealed = false;
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!isOpen(level, pos, state)) {
                if (state.getBlock() instanceof DockingConnectorBlock) {
                    connectors.add(pos);
                } else if (walls.add(pos)) {
                    collectMountedConnectors(level, pos, mounted);
                }
                continue;
            }
            if (remainingCells <= 0) {
                sealed = false;
                break;
            }
            remainingCells--;
            visited.add(pos);
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }

        Compartment room = new Compartment(visited, sealed);
        room.ports.addAll(mounted);
        for (BlockPos connector : connectors) {
            if (facesOutOf(room, connector, front(level.getBlockState(connector)))) {
                room.ports.add(connector.asLong());
            }
        }
        return room;
    }

    private static void collectMountedConnectors(Level level, BlockPos wall, Set<Long> mounted) {
        for (Direction direction : Direction.values()) {
            BlockPos next = wall.relative(direction);
            BlockState state = level.getBlockState(next);
            if (state.getBlock() instanceof DockingConnectorBlock && front(state) == direction) {
                mounted.add(next.asLong());
            }
        }
    }

    private static boolean isOpen(Level level, BlockPos pos) {
        return isOpen(level, pos, level.getBlockState(pos));
    }

    private static boolean isOpen(Level level, BlockPos pos, BlockState state) {
        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean facesOutOf(Compartment room, BlockPos connector, Direction front) {
        if (front == null || room.cells.contains(connector.relative(front))) return false;
        for (Direction direction : Direction.values()) {
            if (direction != front && room.cells.contains(connector.relative(direction))) return true;
        }
        return false;
    }

    private static Direction front(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : null;
    }

    private static boolean outside(BoundingBox3ic bounds, BlockPos pos) {
        return pos.getX() < bounds.minX() || pos.getX() > bounds.maxX()
                || pos.getY() < bounds.minY() || pos.getY() > bounds.maxY()
                || pos.getZ() < bounds.minZ() || pos.getZ() > bounds.maxZ();
    }

    private record Seed(BlockPos pos, List<BlockPos> starts, Long distributor) {
        void addTo(Compartment room) {
            if (distributor != null) room.distributors.add(distributor);
            else room.ports.add(pos.asLong());
        }
    }

    record UnitRef(AtmosphericControlUnitBlockEntity unit, ServerSubLevel subLevel) {}

    record ShipAtmosphere(List<Compartment> rooms) {
        Compartment roomAt(BlockPos pos) {
            for (Compartment room : rooms) {
                if (room.cells.contains(pos)) return room;
            }
            return null;
        }

        Compartment roomWithPort(long port) {
            for (Compartment room : rooms) {
                if (room.ports.contains(port)) return room;
            }
            return null;
        }
    }

    static final class Compartment {
        final Set<BlockPos> cells;
        final Set<Long> distributors = new HashSet<>();
        final Set<Long> ports = new HashSet<>();
        boolean sealed;
        double oxygen;

        Compartment(Set<BlockPos> cells, boolean sealed) {
            this.cells = cells;
            this.sealed = sealed;
        }

        double capacity() {
            return cells.size() * OXYGEN_PER_CELL;
        }

        int overlapSize(Compartment other) {
            Set<BlockPos> smaller = cells.size() <= other.cells.size() ? cells : other.cells;
            Set<BlockPos> larger = smaller == cells ? other.cells : cells;
            int count = 0;
            for (BlockPos pos : smaller) {
                if (larger.contains(pos)) count++;
            }
            return count;
        }

        void merge(Compartment other) {
            cells.addAll(other.cells);
            distributors.addAll(other.distributors);
            ports.addAll(other.ports);
            sealed &= other.sealed;
        }
    }
}
