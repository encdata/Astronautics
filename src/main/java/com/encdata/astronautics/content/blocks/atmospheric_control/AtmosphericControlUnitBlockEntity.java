package com.encdata.astronautics.content.blocks.atmospheric_control;

import com.encdata.astronautics.content.misc.OxygenStorage;
import com.encdata.astronautics.content.misc.ShipAtmosphereSystem;
import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.encdata.astronautics.registry.AstronauticsFluids;
import com.encdata.astronautics.registry.AstronauticsItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public final class AtmosphericControlUnitBlockEntity extends KineticBlockEntity
        implements BlockEntitySubLevelActor, Container {
    private static final int OXYGEN_TANK_SLOT = 0;
    private static final String OXYGEN_TANK_TAG = "OxygenTank";
    private static final float MINIMUM_POWER_SPEED = 1.0F;
    private static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0;

    private ItemStack oxygenTank = ItemStack.EMPTY;
    public AtmosphericControlUnitBlockEntity(BlockPos pos, BlockState state) {
        super(AstronauticsBlockEntities.OXYGEN_DISTRIBUTOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  AtmosphericControlUnitBlockEntity unit) {
        unit.tick();
        boolean powered = unit.isPowered();
        if (state.getValue(AtmosphericControlUnitBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(AtmosphericControlUnitBlock.POWERED, powered), 3);
        }
        var subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            ShipAtmosphereSystem.register(unit, serverSubLevel);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        ShipAtmosphereSystem.register(this, subLevel);
    }

    public ItemStack oxygenTank() {
        return oxygenTank;
    }

    public boolean insertOxygenTank(ItemStack stack) {
        if (!oxygenTank.isEmpty() || !stack.is(AstronauticsItems.OXYGEN_TANK.get())) return false;
        oxygenTank = stack.copyWithCount(1);
        setChanged();
        ShipAtmosphereSystem.requestRescan(this);
        return true;
    }

    public ItemStack removeOxygenTank() {
        ItemStack result = oxygenTank;
        oxygenTank = ItemStack.EMPTY;
        setChanged();
        return result;
    }

    public int drainOxygen(int requested) {
        if (!oxygenTank.is(AstronauticsItems.OXYGEN_TANK.get())) return 0;
        int moved = OxygenStorage.extract(oxygenTank, requested);
        if (OxygenStorage.get(oxygenTank) <= 0) {
            oxygenTank = new ItemStack(AstronauticsItems.EMPTY_OXYGEN_TANK.get());
        }
        if (moved > 0) setChanged();
        return moved;
    }

    @Nullable
    public IFluidHandler getOxygenHandler(@Nullable Direction side) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) {
                int amount = oxygenTank.is(AstronauticsItems.OXYGEN_TANK.get()) ? OxygenStorage.get(oxygenTank) : 0;
                return amount == 0 ? FluidStack.EMPTY
                        : new FluidStack(AstronauticsFluids.OXYGEN.source.get(), amount);
            }
            @Override public int getTankCapacity(int tank) { return OxygenStorage.TANK_CAPACITY; }
            @Override public boolean isFluidValid(int tank, FluidStack stack) {
                return isOxygen(stack);
            }
            @Override public int fill(FluidStack stack, FluidAction action) {
                if (!isOxygen(stack) || !oxygenTank.is(AstronauticsItems.OXYGEN_TANK.get())) return 0;
                int accepted = Math.min(stack.getAmount(), OxygenStorage.TANK_CAPACITY - OxygenStorage.get(oxygenTank));
                if (action.execute() && accepted > 0) {
                    OxygenStorage.insert(oxygenTank, accepted, OxygenStorage.TANK_CAPACITY);
                    setChanged();
                    ShipAtmosphereSystem.requestRescan(AtmosphericControlUnitBlockEntity.this);
                }
                return accepted;
            }
            @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
        };
    }

    @Override
    public int getContainerSize() { return 1; }

    @Override
    public boolean isEmpty() { return oxygenTank.isEmpty(); }

    @Override
    public ItemStack getItem(int slot) {
        return slot == OXYGEN_TANK_SLOT ? oxygenTank : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != OXYGEN_TANK_SLOT) return ItemStack.EMPTY;
        ItemStack result = oxygenTank.split(amount);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != OXYGEN_TANK_SLOT) return ItemStack.EMPTY;
        ItemStack result = oxygenTank;
        oxygenTank = ItemStack.EMPTY;
        return result;
    }
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != OXYGEN_TANK_SLOT) return;
        oxygenTank = stack.copyWithCount(Math.min(1, stack.getCount()));
        setChanged();
        if (oxygenTank.is(AstronauticsItems.OXYGEN_TANK.get())) ShipAtmosphereSystem.requestRescan(this);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == OXYGEN_TANK_SLOT && stack.is(AstronauticsItems.OXYGEN_TANK.get());
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && player.distanceToSqr(worldPosition.getCenter()) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    @Override
    public void clearContent() {
        oxygenTank = ItemStack.EMPTY;
        setChanged();
    }

    public boolean isPowered() {
        return level != null && !level.isClientSide
                && Math.abs(getSpeed()) >= MINIMUM_POWER_SPEED && !isOverStressed();
    }

    @Override public float calculateStressApplied() { return 4.0F; }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!oxygenTank.isEmpty()) tag.put(OXYGEN_TANK_TAG, oxygenTank.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        oxygenTank = tag.contains(OXYGEN_TANK_TAG)
                ? ItemStack.parseOptional(registries, tag.getCompound(OXYGEN_TANK_TAG))
                : ItemStack.EMPTY;
    }

    private static boolean isOxygen(FluidStack stack) {
        return stack.getFluid() == AstronauticsFluids.OXYGEN.source.get()
                || stack.getFluid() == AstronauticsFluids.OXYGEN.flowing.get();
    }
}
