package com.encdata.astronautics.space;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class StoredSubLevelScanner {
    private StoredSubLevelScanner() {}

    static List<StoredSubLevel> scan(SubLevelStorage storage) {
        File[] regionFiles = storage.getFolder().toFile()
                .listFiles((directory, name) -> name.endsWith(SubLevelRegionFile.FILE_EXTENSION));
        if (regionFiles == null || regionFiles.length == 0) return List.of();

        List<StoredSubLevel> result = new ArrayList<>();
        for (File regionFile : regionFiles) {
            String name = regionFile.getName();
            String region = name.substring(0, name.length() - SubLevelRegionFile.FILE_EXTENSION.length());
            String[] parts = region.split("\\.");
            if (parts.length != 3) continue;

            int regionX;
            int regionZ;
            try {
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            for (int localX = 0; localX < SubLevelRegionFile.SIDE_LENGTH; localX++) {
                for (int localZ = 0; localZ < SubLevelRegionFile.SIDE_LENGTH; localZ++) {
                    ChunkPos chunkPos = new ChunkPos(
                            regionX * SubLevelRegionFile.SIDE_LENGTH + localX,
                            regionZ * SubLevelRegionFile.SIDE_LENGTH + localZ);
                    SubLevelHoldingChunk chunk = storage.attemptLoadHoldingChunk(chunkPos);
                    if (chunk == null) continue;

                    for (SavedSubLevelPointer pointer : chunk.getSubLevelPointers()) {
                        SubLevelData data = storage.attemptLoadSubLevel(chunkPos, pointer);
                        if (data == null) continue;
                        var globalPointer = new GlobalSavedSubLevelPointer(
                                chunkPos, pointer.storageIndex(), pointer.subLevelIndex());
                        result.add(new StoredSubLevel(globalPointer, data));
                    }
                }
            }
        }
        return result;
    }

    record StoredSubLevel(GlobalSavedSubLevelPointer pointer, SubLevelData data) {}
}
