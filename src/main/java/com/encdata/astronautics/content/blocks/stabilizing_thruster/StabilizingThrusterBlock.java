package com.encdata.astronautics.content.blocks.stabilizing_thruster;

import com.encdata.astronautics.content.blocks.thruster.DirectionalThrusterBlock;
import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class StabilizingThrusterBlock extends DirectionalThrusterBlock {
    public static final MapCodec<StabilizingThrusterBlock> CODEC = simpleCodec(StabilizingThrusterBlock::new);

    public StabilizingThrusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StabilizingThrusterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AstronauticsBlockEntities.STABILIZING_THRUSTER.get()) return null;
        return (tickLevel, tickPos, tickState, blockEntity) ->
                StabilizingThrusterBlockEntity.serverTick(tickLevel, (StabilizingThrusterBlockEntity) blockEntity);
    }
}
