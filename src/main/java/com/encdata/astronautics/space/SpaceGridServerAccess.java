package com.encdata.astronautics.space;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.concurrent.Executor;

public interface SpaceGridServerAccess {
    void astronautics$registerGridLevel(ServerLevel level, BorderChangeListener borderListener);

    void astronautics$touchGridLevel(ResourceKey<Level> key);

    ServerLevel astronautics$getGridLevel(ResourceKey<Level> key);

    void astronautics$flushGridLevels();

    LevelStorageSource.LevelStorageAccess astronautics$getStorageSource();

    Executor astronautics$getExecutor();

    ChunkProgressListenerFactory astronautics$getProgressListenerFactory();
}
