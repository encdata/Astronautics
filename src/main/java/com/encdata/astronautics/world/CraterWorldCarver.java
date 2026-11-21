package com.encdata.astronautics.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

import java.util.function.Function;

public final class CraterWorldCarver extends WorldCarver<CarverConfiguration> {
    public CraterWorldCarver(Codec<CarverConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean carve(CarvingContext context, CarverConfiguration configuration, ChunkAccess chunk,
                         Function<BlockPos, Holder<Biome>> biomeAccessor, RandomSource random,
                         Aquifer aquifer, ChunkPos chunkPos, CarvingMask carvingMask) {
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centerX, centerZ);
        return carveEllipsoid(context, configuration, chunk, biomeAccessor, aquifer, centerX + 0.5,
                surfaceY, centerZ + 0.5, 64.0, 32.0, carvingMask,
                (carvingContext, x, y, z, index) -> false);
    }

    @Override
    public boolean isStartChunk(CarverConfiguration configuration, RandomSource random) {
        return random.nextFloat() <= configuration.probability;
    }
}
