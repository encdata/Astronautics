package com.encdata.astronautics.content.blocks.thruster;

import com.encdata.astronautics.registry.AstronauticsFluids;
import com.encdata.astronautics.registry.client.AstronauticsParticles;
import com.encdata.astronautics.registry.client.AstronauticsSounds;
import com.encdata.astronautics.space.ThrusterFuelData;
import com.encdata.astronautics.space.ThrusterFuelSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public abstract class FueledThrusterBlockEntity extends BlockEntity {
    private static final String FUEL_TAG = "Fuel";
    private static final String WARMUP_TAG = "Warmup";
    private static final String FUEL_REMAINDER_TAG = "FuelRemainder";
    private static final int CAPACITY = 2_000;
    protected static final int WARMUP_TIME = 6;
    protected static final int WARMUP_DECAY = 2;
    protected static final int TICKS_PER_SECOND = 20;
    protected static final int LOOP_SOUND_INTERVAL = 80;
    protected static final double THRUST = 60_000.0;
    protected static final int THRUSTER_TIER = 1;

    protected final FluidTank fuelTank = new FluidTank(CAPACITY,
            stack -> AstronauticsFluids.isFuel(stack.getFluid())) {
        @Override
        protected void onContentsChanged() {
            FueledThrusterBlockEntity.this.setChanged();
        }
    };
    protected int warmupTicks;
    protected double fuelRemainder;
    protected long nextLoopSoundTick;

    protected FueledThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract Direction getBlockDirection();

    public IFluidHandler getFuelHandler(@Nullable Direction side) {
        return side == getBlockDirection() ? null : fuelTank;
    }

    protected boolean isCommanded() {
        return level != null && !level.isClientSide && redstonePower() > 0.0
                && ThrusterFuelSupport.isValid(fuelTank, THRUSTER_TIER);
    }

    protected boolean updateWarmup() {
        if (!isCommanded()) {
            warmupTicks = Math.max(0, warmupTicks - WARMUP_DECAY);
            nextLoopSoundTick = 0;
            return false;
        }
        warmupTicks = Math.min(WARMUP_TIME, warmupTicks + 1);
        return true;
    }

    public boolean isActive() {
        return isCommanded() && warmupTicks >= WARMUP_TIME;
    }

    protected double redstonePower() {
        return level == null ? 0.0 : level.getBestNeighborSignal(worldPosition) / 15.0;
    }

    protected ThrusterFuelData fuelData() {
        return ThrusterFuelSupport.data(fuelTank);
    }

    protected void emitExhaust(Level level, double intensity, boolean intermittent) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (intermittent && level.getRandom().nextDouble() > intensity) return;

        double power = redstonePower();
        Direction exhaust = getBlockDirection();

        com.encdata.astronautics.content.blocks.engine.ExhaustEffects.emit(serverLevel, worldPosition,
                net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(exhaust.getStepX() * 0.58,
                        exhaust.getStepY() * 0.58, exhaust.getStepZ() * 0.58),
                new net.minecraft.world.phys.Vec3(exhaust.getStepX(), exhaust.getStepY(), exhaust.getStepZ()),
                Math.max(0.15, power) * intensity, 0.5);

        if (level.getGameTime() >= nextLoopSoundTick) {
            level.playSound(null, worldPosition, AstronauticsSounds.THRUSTER_LOOP.get(), SoundSource.BLOCKS,
                    (float) ((0.35 + power * 0.35) * intensity), (float) (0.85 + power * 0.15));
            nextLoopSoundTick = level.getGameTime() + LOOP_SOUND_INTERVAL;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(FUEL_TAG, fuelTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt(WARMUP_TAG, warmupTicks);
        tag.putDouble(FUEL_REMAINDER_TAG, fuelRemainder);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(FUEL_TAG)) fuelTank.readFromNBT(registries, tag.getCompound(FUEL_TAG));
        warmupTicks = tag.getInt(WARMUP_TAG);
        fuelRemainder = tag.getDouble(FUEL_REMAINDER_TAG);
    }
}
