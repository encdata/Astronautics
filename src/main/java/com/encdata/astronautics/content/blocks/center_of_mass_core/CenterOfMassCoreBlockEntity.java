package com.encdata.astronautics.content.blocks.center_of_mass_core;

import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class CenterOfMassCoreBlockEntity extends BlockEntity {
    private static final Map<Level, Set<BlockPos>> CORES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public CenterOfMassCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AstronauticsBlockEntities.CENTER_OF_MASS_CORE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  CenterOfMassCoreBlockEntity core) {
        CORES.computeIfAbsent(level, ignored -> Collections.synchronizedSet(new HashSet<>()))
                .add(pos.immutable());
    }

    public static BlockPos findActiveCore(ServerSubLevel subLevel) {
        Level level = subLevel.getLevel();
        Set<BlockPos> positions = CORES.get(level);
        if (positions == null) return null;

        synchronized (positions) {
            for (BlockPos pos : positions) {
                if (isActiveCore(level, pos, subLevel)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean isActiveCore(Level level, BlockPos pos, ServerSubLevel subLevel) {
        return level.getBestNeighborSignal(pos) > 0
                && level.getBlockEntity(pos) != null
                && Sable.HELPER.getContaining(level, pos) == subLevel;
    }

    public static void unload(Level level) {
        CORES.remove(level);
    }

    @Override
    public void setRemoved() {
        Set<BlockPos> positions = CORES.get(level);
        if (positions != null) {
            positions.remove(worldPosition);
            if (positions.isEmpty()) CORES.remove(level);
        }
        super.setRemoved();
    }
}
