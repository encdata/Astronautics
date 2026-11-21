package com.encdata.astronautics.content.blocks.center_of_mass_core;

import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class CenterOfMassCoreBlock extends Block implements EntityBlock {
    public static final MapCodec<CenterOfMassCoreBlock> CODEC = simpleCodec(CenterOfMassCoreBlock::new);

    public CenterOfMassCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CenterOfMassCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AstronauticsBlockEntities.CENTER_OF_MASS_CORE.get()) return null;
        return (tickLevel, tickPos, tickState, blockEntity) ->
                CenterOfMassCoreBlockEntity.serverTick(tickLevel, tickPos, tickState,
                        (CenterOfMassCoreBlockEntity) blockEntity);
    }
}
