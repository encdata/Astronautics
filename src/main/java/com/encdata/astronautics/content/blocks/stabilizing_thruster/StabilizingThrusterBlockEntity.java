package com.encdata.astronautics.content.blocks.stabilizing_thruster;

import com.encdata.astronautics.content.blocks.thruster.FueledThrusterBlockEntity;
import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.encdata.astronautics.space.ThrusterFuelData;
import com.encdata.astronautics.space.ThrusterFuelSupport;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public final class StabilizingThrusterBlockEntity extends FueledThrusterBlockEntity
        implements BlockEntitySubLevelActor {
    private double brakingFractionSeconds;

    public StabilizingThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(AstronauticsBlockEntities.STABILIZING_THRUSTER.get(), pos, state);
    }

    public static void serverTick(Level level, StabilizingThrusterBlockEntity thruster) {
        if (!thruster.updateWarmup()) {
            thruster.brakingFractionSeconds = 0.0;
            return;
        }
        double brakingFactor = Math.clamp(thruster.brakingFractionSeconds * TICKS_PER_SECOND, 0.0, 1.0);
        thruster.brakingFractionSeconds = 0.0;
        if (!thruster.isActive() || !(brakingFactor > 0.0)) return;
        thruster.emitExhaust(level, brakingFactor, true);
        ThrusterFuelData fuel = thruster.fuelData();

        thruster.fuelRemainder = ThrusterFuelSupport.consume(thruster.fuelTank, thruster.fuelRemainder,
                fuel.fuelConsumption() * thruster.redstonePower() * brakingFactor
                        / (fuel.efficiency() * TICKS_PER_SECOND));
        thruster.setChanged();
    }

    @Override
    public Direction getBlockDirection() {
        return getBlockState().getValue(StabilizingThrusterBlock.FACING);
    }

    public Vector3d forceDirection() {
        Direction facing = getBlockDirection();
        return new Vector3d(-facing.getStepX(), -facing.getStepY(), -facing.getStepZ());
    }
    public double maximumImpulse(ServerSubLevel subLevel, double timeStep) {
        double mass = Math.max(1.0, subLevel.getMassTracker().getMass());
        double power = redstonePower();
        ThrusterFuelData fuel = fuelData();
        double thrust = THRUST * power * fuel.thrustMultiplier();
        return Math.min(thrust * timeStep, mass * fuel.maxAcceleration() * power * timeStep);
    }

    public void markBraking(double impulseFraction, double timeStep) {
        brakingFractionSeconds += Math.clamp(impulseFraction, 0.0, 1.0) * timeStep;
    }
}
