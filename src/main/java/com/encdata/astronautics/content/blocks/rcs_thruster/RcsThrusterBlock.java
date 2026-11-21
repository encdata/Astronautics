package com.encdata.astronautics.content.blocks.rcs_thruster;

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

public final class RcsThrusterBlock extends DirectionalThrusterBlock {
    public static final MapCodec<RcsThrusterBlock> CODEC = simpleCodec(RcsThrusterBlock::new);

    public RcsThrusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RcsThrusterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AstronauticsBlockEntities.THRUSTER.get()) return null;
        return (tickLevel, tickPos, tickState, blockEntity) ->
                RcsThrusterBlockEntity.serverTick(tickLevel, (RcsThrusterBlockEntity) blockEntity);
    }
}
