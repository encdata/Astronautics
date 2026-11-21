package com.encdata.astronautics.mixin;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public interface SubLevelHoldingChunkMapAccessor {
    @Invoker("getOrLoadHoldingChunk")
    SubLevelHoldingChunk astronautics$getOrLoadHoldingChunk(ChunkPos chunkPos, boolean create);

    @Accessor("loadedHoldingChunks")
    Long2ObjectMap<SubLevelHoldingChunk> astronautics$loadedHoldingChunks();
}
