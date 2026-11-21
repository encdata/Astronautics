package com.encdata.astronautics.content.blocks.oxygen_compressor;

import com.encdata.astronautics.content.misc.OxygenStorage;
import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.encdata.astronautics.registry.AstronauticsItems;
import com.encdata.astronautics.space.SpaceCatalog;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OxygenCompressorBlockEntity extends KineticBlockEntity implements Container {
    public static final int FILL_TICKS = 6_000;
    private static final String TANK_TAG = "Tank";
    private static final String PROGRESS_TAG = "Progress";
    private static final int TANK_SLOT = 0;
    private static final float MINIMUM_POWER_SPEED = 1.0F;
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0;

    private ItemStack tank = ItemStack.EMPTY;
    private int progress;

    public OxygenCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(AstronauticsBlockEntities.OXYGEN_COMPRESSOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OxygenCompressorBlockEntity compressor) {
        compressor.tick();
        if (!compressor.canFill(level)) {
            return;
        }
        if (++compressor.progress >= FILL_TICKS) {
            compressor.tank = new ItemStack(AstronauticsItems.OXYGEN_TANK.get());
            OxygenStorage.set(compressor.tank, OxygenStorage.TANK_CAPACITY);
            compressor.progress = 0;
        }
        compressor.setChanged();
    }

    private boolean canFill(Level level) {
        return tank.is(AstronauticsItems.EMPTY_OXYGEN_TANK.get())
                && SpaceCatalog.environmentForDimension(level.dimension()).oxygen()
                && isPowered();
    }

    public boolean insertEmptyTank(ItemStack stack) {
        if (!tank.isEmpty() || !stack.is(AstronauticsItems.EMPTY_OXYGEN_TANK.get())) {
            return false;
        }
        tank = stack.copyWithCount(1);
        progress = 0;
        setChanged();
        return true;
    }

    public ItemStack removeTank() {
        ItemStack result = tank;
        tank = ItemStack.EMPTY;
        progress = 0;
        setChanged();
        return result;
    }

    public int progress() {
        return progress;
    }

    public ItemStack tank() {
        return tank;
    }

    public boolean isPowered() {
        return Math.abs(getSpeed()) >= MINIMUM_POWER_SPEED && !isOverStressed();
    }
    @Override
    public float calculateStressApplied() {
        return 8.0F;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return tank.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == TANK_SLOT ? tank : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != TANK_SLOT) return ItemStack.EMPTY;
        ItemStack result = tank.split(amount);
        if (tank.isEmpty()) progress = 0;
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != TANK_SLOT) return ItemStack.EMPTY;
        ItemStack result = tank;
        tank = ItemStack.EMPTY;
        progress = 0;
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != TANK_SLOT) return;
        tank = stack.copyWithCount(Math.min(1, stack.getCount()));
        progress = 0;
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == TANK_SLOT && tank.isEmpty() && stack.is(AstronauticsItems.EMPTY_OXYGEN_TANK.get());
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && player.distanceToSqr(worldPosition.getCenter()) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    @Override
    public void clearContent() {
        tank = ItemStack.EMPTY;
        progress = 0;
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!tank.isEmpty()) tag.put(TANK_TAG, tank.save(registries));
        tag.putInt(PROGRESS_TAG, progress);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank = tag.contains(TANK_TAG)
                ? ItemStack.parseOptional(registries, tag.getCompound(TANK_TAG)) : ItemStack.EMPTY;
        progress = Math.clamp(tag.getInt(PROGRESS_TAG), 0, FILL_TICKS);
    }
}
