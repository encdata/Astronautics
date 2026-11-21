package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGridDirectory;
import com.encdata.astronautics.space.SpaceGridServerAccess;
import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class SpaceGridMinecraftServerMixin implements SpaceGridServerAccess {
    @Unique private static final int astronautics$GRID_IDLE_TICKS = 1200;
    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;
    @Shadow @Final private LevelStorageSource.LevelStorageAccess storageSource;
    @Shadow @Final private Executor executor;
    @Shadow @Final private ChunkProgressListenerFactory progressListenerFactory;
    @Unique private final List<ServerLevel> astronautics$pendingGridLevels = new ArrayList<>();
    @Unique private final Map<ResourceKey<Level>, BorderChangeListener> astronautics$gridBorderListeners = new HashMap<>();
    @Unique private final Map<ResourceKey<Level>, Integer> astronautics$gridLastActive = new HashMap<>();
    @Unique private boolean astronautics$tickingLevels;

    @Inject(method = "tickChildren", at = @At("HEAD"))
    private void astronautics$beginLevelTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ServerLevel spaceLevel = levels.get(SpaceConstants.SPACE_LEVEL);
        if (spaceLevel != null && SpaceGridDirectory.get(spaceLevel).isDirty()) {
            spaceLevel.getDataStorage().save();
        }
        astronautics$flushGridLevels();
        astronautics$unloadIdleGridLevels();
        astronautics$tickingLevels = true;
    }

    @Inject(method = "tickChildren", at = @At("RETURN"))
    private void astronautics$endLevelTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        astronautics$tickingLevels = false;
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void astronautics$closeGridLevelsLast(CallbackInfo ci) {
        astronautics$flushGridLevels();
        List<ResourceKey<Level>> gridKeys = new ArrayList<>();
        for (ResourceKey<Level> key : levels.keySet()) {
            if (SpaceConstants.isGridLevel(key)) gridKeys.add(key);
        }
        for (ResourceKey<Level> key : gridKeys) levels.put(key, levels.remove(key));
    }

    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true)
    private void astronautics$getPendingGridLevel(ResourceKey<Level> key,
                                                  CallbackInfoReturnable<ServerLevel> cir) {
        if (!SpaceConstants.isGridLevel(key)) return;
        cir.setReturnValue(astronautics$getGridLevel(key));
    }

    @Override
    public void astronautics$registerGridLevel(ServerLevel level, BorderChangeListener borderListener) {
        if (levels.containsKey(level.dimension()) || astronautics$containsPending(level.dimension())) return;
        astronautics$gridBorderListeners.put(level.dimension(), borderListener);
        astronautics$touchGridLevel(level.dimension());
        if (astronautics$tickingLevels) astronautics$pendingGridLevels.add(level);
        else levels.put(level.dimension(), level);
    }

    @Override
    public void astronautics$touchGridLevel(ResourceKey<Level> key) {
        astronautics$gridLastActive.put(key, ((MinecraftServer) (Object) this).getTickCount());
    }

    @Override
    public ServerLevel astronautics$getGridLevel(ResourceKey<Level> key) {
        ServerLevel level = levels.get(key);
        if (level != null) return level;
        for (ServerLevel pending : astronautics$pendingGridLevels) {
            if (pending.dimension().equals(key)) return pending;
        }
        return null;
    }

    @Override
    public void astronautics$flushGridLevels() {
        if (astronautics$pendingGridLevels.isEmpty()) return;
        for (ServerLevel level : astronautics$pendingGridLevels) {
            levels.putIfAbsent(level.dimension(), level);
        }
        astronautics$pendingGridLevels.clear();
    }

    @Override
    public LevelStorageSource.LevelStorageAccess astronautics$getStorageSource() {
        return storageSource;
    }

    @Override
    public Executor astronautics$getExecutor() {
        return executor;
    }

    @Override
    public ChunkProgressListenerFactory astronautics$getProgressListenerFactory() {
        return progressListenerFactory;
    }

    @Unique
    private void astronautics$unloadIdleGridLevels() {
        MinecraftServer server = (MinecraftServer) (Object) this;
        int tick = server.getTickCount();
        if (tick % 20 != 0) return;

        List<ResourceKey<Level>> unload = new ArrayList<>();
        ServerLevel spaceLevel = server.getLevel(SpaceConstants.SPACE_LEVEL);
        if (spaceLevel == null) return;
        SpaceGridDirectory directory = SpaceGridDirectory.get(spaceLevel);
        Set<SpaceGridAddress> activeShipGrids = astronautics$activeShipGrids(spaceLevel, directory);
        for (Map.Entry<ResourceKey<Level>, ServerLevel> entry : levels.entrySet()) {
            ResourceKey<Level> key = entry.getKey();
            if (!SpaceConstants.isGridLevel(key)) continue;

            ServerLevel level = entry.getValue();
            SpaceGridAddress address = directory.findAddress(key);
            if (address != null && activeShipGrids.contains(address)) continue;
            if (!level.players().isEmpty()) {
                astronautics$gridLastActive.put(key, tick);
                continue;
            }

            ForcedChunksSavedData forcedChunks = level.getDataStorage()
                    .get(ForcedChunksSavedData.factory(), "chunks");
            if (forcedChunks != null && !forcedChunks.getChunks().isEmpty()) continue;

            int lastActive = astronautics$gridLastActive.computeIfAbsent(key, ignored -> tick);
            if (tick - lastActive >= astronautics$GRID_IDLE_TICKS) unload.add(key);
        }

        for (ResourceKey<Level> key : unload) astronautics$unloadGridLevel(server, key);
    }

    @Unique
    private Set<SpaceGridAddress> astronautics$activeShipGrids(ServerLevel spaceLevel, SpaceGridDirectory directory) {
        SubLevelContainer container = SubLevelContainer.getContainer(spaceLevel);
        if (container == null) return Set.of();

        Set<SpaceGridAddress> active = new HashSet<>();
        for (Object subLevel : container.getAllSubLevels()) {
            if (!(subLevel instanceof ServerSubLevel ship)) continue;
            SpaceGridAddress address = directory.shipAddress(ship.getUniqueId());
            if (address != null) active.add(address);
            SpaceGridAddress frameOwner = directory.frameOwnerAddress(ship.getUniqueId());
            if (frameOwner != null) active.add(frameOwner);
        }
        return active;
    }

    @Unique
    private void astronautics$unloadGridLevel(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel level = levels.get(key);
        if (level == null || !level.players().isEmpty()) return;

        try {
            level.save(null, true, level.noSave);
            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
            level.close();
        } catch (IOException exception) {
            Astronautics.LOGGER.error("Failed to save space grid level {}", key.location(), exception);
            astronautics$gridLastActive.put(key, server.getTickCount());
            return;
        }

        levels.remove(key);
        astronautics$gridLastActive.remove(key);
        BorderChangeListener borderListener = astronautics$gridBorderListeners.remove(key);
        ServerLevel spaceLevel = server.getLevel(SpaceConstants.SPACE_LEVEL);
        if (spaceLevel != null && borderListener != null) {
            spaceLevel.getWorldBorder().removeListener(borderListener);
        }
    }

    @Unique
    private boolean astronautics$containsPending(ResourceKey<Level> key) {
        for (ServerLevel level : astronautics$pendingGridLevels) {
            if (level.dimension().equals(key)) return true;
        }
        return false;
    }
}
