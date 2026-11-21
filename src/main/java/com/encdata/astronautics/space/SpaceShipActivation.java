package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.mixin.SubLevelHoldingChunkMapAccessor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3d;

public final class SpaceShipActivation {
    public static final double ACTIVE_RANGE = 10_000.0;
    private static final double ACTIVE_RANGE_SQUARED = ACTIVE_RANGE * ACTIVE_RANGE;
    private static final double UNLOAD_RANGE = ACTIVE_RANGE + 1000.0;
    private static final int STORAGE_SCAN_INTERVAL = 200;
    private static final int ACTIVATION_SCAN_INTERVAL = 5;
    private static final int SPATIAL_INDEX_INTERVAL = 20;
    private static final int SPATIAL_CELL_BLOCKS = 1024;
    private static final int SPATIAL_QUERY_RADIUS = (int) Math.ceil(ACTIVE_RANGE / SPATIAL_CELL_BLOCKS);
    private static final ExecutorService STORAGE_SCAN_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Astronautics-ship-storage-index");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean STORAGE_SCAN_IN_FLIGHT = new AtomicBoolean();
    private static long lastStorageScan = Long.MIN_VALUE;
    private static long lastActivationScan = Long.MIN_VALUE;
    private static long lastSpatialIndexScan = Long.MIN_VALUE;
    private static volatile long resetGeneration;
    private static volatile Map<Long, List<StoredSubLevelScanner.StoredSubLevel>> storedSpatialIndex = Map.of();
    private static volatile Map<Long, List<ServerSubLevel>> loadedSpatialIndex = Map.of();

    private SpaceShipActivation() {}

    public static void reset() {
        resetGeneration++;
        lastStorageScan = Long.MIN_VALUE;
        lastActivationScan = Long.MIN_VALUE;
        lastSpatialIndexScan = Long.MIN_VALUE;
        storedSpatialIndex = Map.of();
        loadedSpatialIndex = Map.of();
    }

    public static boolean isWithinRange(ServerPlayer player, SubLevel subLevel) {
        if (!SpaceConstants.isSpace(player.level()) || subLevel.getLevel() != player.level()) return false;
        if (subLevel instanceof ServerSubLevel ship && SpaceGrids.isMainSpace(player.level())) {
            return SpaceProjection.isWithinRange(player, ship, ACTIVE_RANGE);
        }
        return isWithinRange(player, subLevel.logicalPose().position());
    }

    private static boolean isWithinRange(ServerPlayer player, Vector3d position) {
        return position.distanceSquared(player.getX(), player.getY(), player.getZ()) <= ACTIVE_RANGE_SQUARED;
    }

    private static boolean isWithinRange(ServerPlayer player, SpacePosition position) {
        return SpaceProjection.observerPosition(player).deltaWithin(position, ACTIVE_RANGE).isPresent();
    }

    public static void tick(ServerLevel level) {
        if (!SpaceConstants.isSpace(level)) return;

        long gameTime = level.getGameTime();
        if (gameTime != lastActivationScan
                && lastActivationScan != Long.MIN_VALUE
                && gameTime - lastActivationScan < ACTIVATION_SCAN_INTERVAL) {
            return;
        }
        lastActivationScan = gameTime;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        if ((gameTime - lastStorageScan >= STORAGE_SCAN_INTERVAL || lastStorageScan == Long.MIN_VALUE)
                && STORAGE_SCAN_IN_FLIGHT.compareAndSet(false, true)) {
            lastStorageScan = gameTime;
            requestStorageScan(level, container.getHoldingChunkMap().getStorage(), resetGeneration);
        }

        var forceLoaded = container.collectForceLoadedSubLevels();
        if (lastSpatialIndexScan == Long.MIN_VALUE
                || gameTime - lastSpatialIndexScan >= SPATIAL_INDEX_INTERVAL) {
            loadedSpatialIndex = indexLoadedSubLevels(container);
            unloadFarLoadedSubLevels(level, container, forceLoaded);
            lastSpatialIndexScan = gameTime;
        }

        loadNearbyStoredSubLevels(level, container);
    }

    private static void loadNearbyStoredSubLevels(ServerLevel level, ServerSubLevelContainer container) {
        SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();

        SpaceGridDirectory directory = SpaceGrids.isMainSpace(level) ? SpaceGridDirectory.get(level) : null;
        for (StoredSubLevelScanner.StoredSubLevel stored : nearbyStoredSubLevels(level)) {
            if (container.getSubLevel(stored.data().uuid()) != null) continue;

            Vector3d storedPosition = stored.data().pose().position();
            boolean near;
            if (directory != null) {
                SpacePosition position = storedPosition(level, directory, stored.data().uuid(), storedPosition);
                near = level.players().stream().anyMatch(player -> isWithinRange(player, position));
            } else {
                near = isAnyPlayerWithinRange(level, storedPosition);
            }
            if (!near) continue;

            SubLevelHoldingChunk loadedChunk = ((SubLevelHoldingChunkMapAccessor) holdingMap)
                    .astronautics$getOrLoadHoldingChunk(stored.pointer().chunkPos(), false);
            if (loadedChunk == null || !holds(loadedChunk, stored.data().uuid())) continue;

            holdingMap.snatchAndLoad(stored.pointer(), stored.data().uuid());
        }
        if (directory != null) loadNearbyHeldSubLevels(level, container, holdingMap, directory);
    }

    private static void loadNearbyHeldSubLevels(ServerLevel level, ServerSubLevelContainer container,
                                                SubLevelHoldingChunkMap holdingMap, SpaceGridDirectory directory) {
        if (level.players().isEmpty()) return;
        List<GlobalSavedSubLevelPointer> pointers = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (SubLevelHoldingChunk chunk : ((SubLevelHoldingChunkMapAccessor) holdingMap)
                .astronautics$loadedHoldingChunks().values()) {
            for (HoldingSubLevel held : chunk.getLoadedHoldingSubLevels()) {
                UUID id = held.data().uuid();
                if (container.getSubLevel(id) != null) continue;
                SpacePosition position = storedPosition(level, directory, id, held.data().pose().position());
                if (level.players().stream().noneMatch(player -> isWithinRange(player, position))) continue;
                GlobalSavedSubLevelPointer previous = held.pointer();
                pointers.add(new GlobalSavedSubLevelPointer(chunk.getChunkPos(),
                        previous == null ? 0 : previous.storageIndex(),
                        previous == null ? 0 : previous.subLevelIndex()));
                ids.add(id);
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            if (container.getSubLevel(ids.get(i)) == null) holdingMap.snatchAndLoad(pointers.get(i), ids.get(i));
        }
    }

    private static SpacePosition storedPosition(ServerLevel level, SpaceGridDirectory directory, UUID id,
                                                Vector3d saved) {
        SpaceGridAddress grid = directory.shipAddress(id);
        if (grid == null) return SpacePosition.normalize(SpaceGridAddress.ORIGIN, saved.x, saved.y, saved.z);
        boolean foreign = !local(saved.x) || !local(saved.y) || !local(saved.z);
        if (foreign && anyPlayerAround(level, grid)) {
            SpaceGridShipState state = SpaceGridShipData.get(
                    SpaceGridLevelFactory.getOrCreate(level.getServer(), grid)).get(id);
            if (state != null && Double.isFinite(state.x()) && Double.isFinite(state.y())
                    && Double.isFinite(state.z())) {
                return SpacePosition.normalize(grid, state.x(), state.y(), state.z());
            }
        }
        return SpacePosition.normalize(grid, saved.x, saved.y, saved.z);
    }

    private static boolean anyPlayerAround(ServerLevel level, SpaceGridAddress grid) {
        for (ServerPlayer player : level.players()) {
            SpaceGridAddress own = SpaceProjection.observerPosition(player).grid();
            if (adjacent(own.x(), grid.x()) && adjacent(own.y(), grid.y()) && adjacent(own.z(), grid.z())) {
                return true;
            }
        }
        return false;
    }

    private static boolean local(double value) {
        return value >= -SpacePosition.GRID_HALF_SIZE && value < SpacePosition.GRID_HALF_SIZE;
    }

    private static boolean adjacent(BigInteger first, BigInteger second) {
        return first.subtract(second).abs().compareTo(BigInteger.ONE) <= 0;
    }

    private static boolean holds(SubLevelHoldingChunk chunk, UUID subLevelId) {
        for (HoldingSubLevel holding : chunk.getLoadedHoldingSubLevels()) {
            if (holding.data().uuid().equals(subLevelId)) return true;
        }
        return false;
    }

    private static List<StoredSubLevelScanner.StoredSubLevel> nearbyStoredSubLevels(ServerLevel level) {
        if (storedSpatialIndex.isEmpty()) return List.of();

        List<StoredSubLevelScanner.StoredSubLevel> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        double reach = ACTIVE_RANGE + SPATIAL_CELL_BLOCKS;
        for (ServerPlayer player : level.players()) {
            double x = player.getX();
            double z = player.getZ();
            addStoredCells(result, seen, x, z);
            double shiftX = x + reach >= SpacePosition.GRID_HALF_SIZE ? -SpacePosition.GRID_SIZE
                    : x - reach < -SpacePosition.GRID_HALF_SIZE ? SpacePosition.GRID_SIZE : 0.0;
            double shiftZ = z + reach >= SpacePosition.GRID_HALF_SIZE ? -SpacePosition.GRID_SIZE
                    : z - reach < -SpacePosition.GRID_HALF_SIZE ? SpacePosition.GRID_SIZE : 0.0;
            if (shiftX != 0.0) addStoredCells(result, seen, x + shiftX, z);
            if (shiftZ != 0.0) addStoredCells(result, seen, x, z + shiftZ);
            if (shiftX != 0.0 && shiftZ != 0.0) addStoredCells(result, seen, x + shiftX, z + shiftZ);
        }
        return result;
    }

    private static void addStoredCells(List<StoredSubLevelScanner.StoredSubLevel> result, Set<UUID> seen,
                                       double x, double z) {
        int cellX = cell(x);
        int cellZ = cell(z);
        for (int dx = -SPATIAL_QUERY_RADIUS; dx <= SPATIAL_QUERY_RADIUS; dx++) {
            for (int dz = -SPATIAL_QUERY_RADIUS; dz <= SPATIAL_QUERY_RADIUS; dz++) {
                List<StoredSubLevelScanner.StoredSubLevel> entries =
                        storedSpatialIndex.get(ChunkPos.asLong(cellX + dx, cellZ + dz));
                if (entries == null) continue;
                for (StoredSubLevelScanner.StoredSubLevel entry : entries) {
                    if (seen.add(entry.data().uuid())) result.add(entry);
                }
            }
        }
    }

    private static void unloadFarLoadedSubLevels(ServerLevel level, ServerSubLevelContainer container,
                                                  Iterable<ServerSubLevel> forceLoaded) {
        Set<ServerSubLevel> forced = new HashSet<>();
        for (ServerSubLevel subLevel : forceLoaded) forced.add(subLevel);

        List<ServerSubLevel> far = new ArrayList<>();
        Set<Long> keptColumns = new HashSet<>();
        for (List<ServerSubLevel> entries : loadedSpatialIndex.values()) {
            for (ServerSubLevel subLevel : entries) {
                if (subLevel.isRemoved()) continue;

                Vector3d position = subLevel.logicalPose().position();
                boolean near = forced.contains(subLevel) || (SpaceGrids.isMainSpace(level)
                        ? level.players().stream().anyMatch(player -> SpaceProjection.isWithinRange(player, subLevel, UNLOAD_RANGE))
                        : isAnyPlayerWithinRange(level, position));
                if (near) keepColumns(keptColumns, subLevel);
                else far.add(subLevel);
            }
        }

        SubLevelHoldingChunkMap holdingMap = container.getHoldingChunkMap();
        for (ServerSubLevel subLevel : far) {
            if (subLevel.isRemoved()) continue;
            Vector3d position = subLevel.logicalPose().position();
            ChunkPos column = new ChunkPos((int) Math.floor(position.x / 16.0), (int) Math.floor(position.z / 16.0));
            if (keptColumns.contains(column.toLong())) continue;
            holdingMap.moveToUnloaded(subLevel, column);
            holdingMap.updateChunkStatus(column, false);
        }
    }

    private static void keepColumns(Set<Long> columns, SubLevel subLevel) {
        var bounds = subLevel.boundingBox();
        int minX = (int) Math.floor((bounds.minX() - 1.0) / 16.0);
        int maxX = (int) Math.floor((bounds.maxX() + 1.0) / 16.0);
        int minZ = (int) Math.floor((bounds.minZ() - 1.0) / 16.0);
        int maxZ = (int) Math.floor((bounds.maxZ() + 1.0) / 16.0);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) columns.add(ChunkPos.asLong(x, z));
        }
    }

    private static boolean isAnyPlayerWithinRange(ServerLevel level, Vector3d position) {
        for (ServerPlayer player : level.players()) {
            if (isWithinRange(player, position)) return true;
        }
        return false;
    }

    private static Map<Long, List<StoredSubLevelScanner.StoredSubLevel>> indexStoredSubLevels(
            List<StoredSubLevelScanner.StoredSubLevel> entries) {
        Map<Long, List<StoredSubLevelScanner.StoredSubLevel>> index = new HashMap<>();
        for (StoredSubLevelScanner.StoredSubLevel entry : entries) {
            Vector3d position = entry.data().pose().position();
            index.computeIfAbsent(ChunkPos.asLong(cell(position.x), cell(position.z)), ignored -> new ArrayList<>())
                    .add(entry);
        }
        Map<Long, List<StoredSubLevelScanner.StoredSubLevel>> frozen = new HashMap<>(index.size());
        for (Map.Entry<Long, List<StoredSubLevelScanner.StoredSubLevel>> entry : index.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static Map<Long, List<ServerSubLevel>> indexLoadedSubLevels(ServerSubLevelContainer container) {
        Map<Long, List<ServerSubLevel>> index = new HashMap<>();
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            Vector3d position = subLevel.logicalPose().position();
            index.computeIfAbsent(ChunkPos.asLong(cell(position.x), cell(position.z)), ignored -> new ArrayList<>())
                    .add(subLevel);
        }
        return index;
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / SPATIAL_CELL_BLOCKS);
    }

    private static void requestStorageScan(ServerLevel level, SubLevelStorage storage, long generation) {
        CompletableFuture
                .supplyAsync(() -> {
                    try (SubLevelStorage isolatedStorage = new SubLevelStorage(storage.getFolder())) {
                        return List.copyOf(StoredSubLevelScanner.scan(isolatedStorage));
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                }, STORAGE_SCAN_EXECUTOR)
                .whenComplete((result, error) -> {
                    try {
                        if (error != null) {
                            Astronautics.LOGGER.warn("Unable to refresh the asynchronous space ship storage index", error);
                            return;
                        }
                        if (generation != resetGeneration) return;
                        storedSpatialIndex = indexStoredSubLevels(result);
                        level.getServer().execute(() -> {
                            if (generation != resetGeneration) return;
                            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                            if (container != null) {
                                loadNearbyStoredSubLevels(level, container);
                            }
                        });
                    } finally {
                        STORAGE_SCAN_IN_FLIGHT.set(false);
                    }
                });
    }

}
