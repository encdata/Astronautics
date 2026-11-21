package com.encdata.astronautics.space;

import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.Objects;

public final class SpaceGridLevelFactory {
    private static final ThreadLocal<ServerLevel> PLOT_LEVEL = new ThreadLocal<>();
    private static volatile ServerLevel startingSpaceLevel;

    private SpaceGridLevelFactory() {}

    public static ServerLevel currentPlotLevel() {
        return PLOT_LEVEL.get();
    }

    public static void spaceLevelStarting(ServerLevel level) {
        startingSpaceLevel = level;
    }

    public static ServerLevel spaceLevel(MinecraftServer server) {
        ServerLevel level = server.getLevel(SpaceConstants.SPACE_LEVEL);
        ServerLevel starting = startingSpaceLevel;
        if (level != null) {
            if (starting != null) startingSpaceLevel = null;
            return level;
        }
        return starting != null && starting.getServer() == server ? starting : null;
    }

    public static ServerLevel getOrCreate(MinecraftServer server, SpaceGridAddress address) {
        ServerLevel spaceLevel = Objects.requireNonNull(spaceLevel(server), "space level is not available");
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        ResourceKey<Level> key = directory.allocateLevelKey(address);
        SpaceGridServerAccess serverAccess = (SpaceGridServerAccess) server;
        ServerLevel existing = serverAccess.astronautics$getGridLevel(key);
        if (existing != null) {
            serverAccess.astronautics$touchGridLevel(key);
            return existing;
        }

        WorldData worldData = server.getWorldData();
        LevelStem stem = new LevelStem(spaceLevel.dimensionTypeRegistration(),
                spaceLevel.getChunkSource().getGenerator());
        LevelStorageSource.LevelStorageAccess storage = serverAccess.astronautics$getStorageSource();
        ChunkProgressListenerFactory progressFactory = serverAccess.astronautics$getProgressListenerFactory();
        PLOT_LEVEL.set(spaceLevel);
        ServerLevel level;
        try {
            level = new ServerLevel(
                    server,
                    serverAccess.astronautics$getExecutor(),
                    storage,
                    new DerivedLevelData(worldData, worldData.overworldData()),
                    key,
                    stem,
                    progressFactory.create(10),
                    worldData.isDebugWorld(),
                    BiomeManager.obfuscateSeed(worldData.worldGenOptions().seed()),
                    ImmutableList.of(),
                    false,
                    null
            );
        } finally {
            PLOT_LEVEL.remove();
        }

        BorderChangeListener borderListener =
                new BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder());
        spaceLevel.getWorldBorder().addListener(borderListener);
        level.getChunkSource().setSimulationDistance(server.getPlayerList().getSimulationDistance());
        level.getChunkSource().setViewDistance(server.getPlayerList().getViewDistance());
        ForcedChunksSavedData forcedChunks = level.getDataStorage()
                .get(ForcedChunksSavedData.factory(), "chunks");
        if (forcedChunks != null) {
            for (long packedPos : forcedChunks.getChunks()) {
                level.getChunkSource().updateChunkForced(new ChunkPos(packedPos), true);
            }
        }
        level.setSpawnSettings(false, false);
        serverAccess.astronautics$registerGridLevel(level, borderListener);
        return level;
    }

    public static ServerLevel find(MinecraftServer server, ResourceKey<Level> key) {
        if (!SpaceConstants.isGridLevel(key)) return null;
        return ((SpaceGridServerAccess) server).astronautics$getGridLevel(key);
    }

    public static SpaceGridAddress addressOf(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel spaceLevel = spaceLevel(server);
        if (spaceLevel == null) return null;
        return SpaceGridDirectory.get(spaceLevel).findAddress(key);
    }

    public static void flushPending(MinecraftServer server) {
        ((SpaceGridServerAccess) server).astronautics$flushGridLevels();
    }
}
