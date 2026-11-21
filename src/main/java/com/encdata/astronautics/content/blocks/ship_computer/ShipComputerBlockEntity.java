package com.encdata.astronautics.content.blocks.ship_computer;

import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShipComputerBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private ShipComputerMode mode = ShipComputerMode.BOTH;
    private ResourceLocation targetBody;
    private int targetAltitude = 100;

    public ShipComputerBlockEntity(BlockPos pos, BlockState state) {
        super(AstronauticsBlockEntities.SHIP_COMPUTER.get(), pos, state);
    }

    public ShipComputerMode mode() {
        return mode;
    }

    public ResourceLocation targetBody() {
        return targetBody;
    }

    public int targetAltitude() {
        return targetAltitude;
    }

    public BlockPos computerPos() {
        return worldPosition;
    }

    public static ShipComputerBlockEntity primaryComputer(SubLevel subLevel) {
        ShipComputerBlockEntity primary = null;
        for (var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof ShipComputerBlockEntity computer
                    && (primary == null || computer.getBlockPos().asLong() < primary.getBlockPos().asLong())) {
                primary = computer;
            }
        }
        return primary;
    }

    public static int computerCount(SubLevel subLevel) {
        int count = 0;
        for (var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof ShipComputerBlockEntity) count++;
        }
        return count;
    }

    public void setMode(ShipComputerMode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void setOrbitTarget(ResourceLocation body, int altitude) {
        targetBody = body;
        targetAltitude = Math.clamp(altitude, 10, 30_000);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {}

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Mode", mode.ordinal());
        if (targetBody != null) tag.putString("TargetBody", targetBody.toString());
        tag.putInt("TargetAltitude", targetAltitude);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = ShipComputerMode.byId(tag.getInt("Mode"));
        targetBody = ResourceLocation.tryParse(tag.getString("TargetBody"));
        targetAltitude = Math.clamp(tag.contains("TargetAltitude") ? tag.getInt("TargetAltitude") : 100,
                10, 30_000);
    }
}
