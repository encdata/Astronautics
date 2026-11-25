package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceChunkLoading;
import com.encdata.astronautics.space.SpaceConstants;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.ishland.c2me.rewrites.chunksystem.common.statuses.ReadFromDisk", remap = false)
public abstract class C2meEmptySpaceChunkMixin {
    @Redirect(
            method = "createEmptyProtoChunk",
            at = @At(value = "NEW", target = "net/minecraft/world/level/chunk/ProtoChunk"),
            remap = false
    )
    private static ProtoChunk astronautics$emptyVirtualChunk(ChunkPos pos, UpgradeData upgradeData,
                                                              LevelHeightAccessor level,
                                                              Registry<Biome> biomes,
                                                              BlendingData blendingData) {
        if (level instanceof ServerLevel serverLevel && SpaceConstants.isSpace(serverLevel)
                && SpaceChunkLoading.isVirtual(serverLevel, pos.x, pos.z)) {
            return SpaceChunkLoading.emptyChunk(serverLevel, pos);
        }
        return new ProtoChunk(pos, upgradeData, level, biomes, blendingData);
    }
}
