package com.encdata.astronautics.space;

import com.encdata.astronautics.registry.AstronauticsBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

public final class ReentryHeatShieldPolicy {
    private ReentryHeatShieldPolicy() {}

    public static boolean survives(BlockState state) {
        return !state.isAir() && state.is(AstronauticsBlockTags.HEAT_SHIELDING);
    }

    public static void destroyIfUnshielded(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!survives(state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }
}
