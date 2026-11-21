package com.encdata.astronautics.mixin;

import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceGrids;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class SpaceGridEntityMixin implements SpaceGrids.Holder {
    @Unique private SpaceGridAddress astronautics$grid;
    @Unique private SpaceGridAddress astronautics$positionGrid;

    @Override
    public SpaceGridAddress astronautics$grid() {
        return astronautics$grid;
    }

    @Override
    public void astronautics$setGrid(SpaceGridAddress grid) {
        astronautics$grid = grid;
    }

    @Override
    public SpaceGridAddress astronautics$positionGrid() {
        return astronautics$positionGrid;
    }

    @Override
    public void astronautics$setPositionGrid(SpaceGridAddress grid) {
        astronautics$positionGrid = grid;
    }
}
