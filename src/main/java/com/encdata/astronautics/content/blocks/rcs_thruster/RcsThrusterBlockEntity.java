package com.encdata.astronautics.content.blocks.rcs_thruster;

import com.encdata.astronautics.content.blocks.thruster.FueledThrusterBlockEntity;
import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.encdata.astronautics.registry.AstronauticsForceGroups;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.ThrusterFuelData;
import com.encdata.astronautics.space.ThrusterFuelSupport;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public final class RcsThrusterBlockEntity extends FueledThrusterBlockEntity
        implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {
    private int fuelTick;

    public RcsThrusterBlockEntity(BlockPos pos, BlockState state) {
        super(AstronauticsBlockEntities.THRUSTER.get(), pos, state);
    }

    public static void serverTick(Level level, RcsThrusterBlockEntity rcs) {
        if (!rcs.updateWarmup()) return;
        boolean active = rcs.isActive();
        if (active) rcs.emitExhaust(level, 1.0, false);
        if (active && ++rcs.fuelTick >= TICKS_PER_SECOND) {
            rcs.consumeFuel();
        }
    }

    private void consumeFuel() {
        fuelTick = 0;
        ThrusterFuelData fuel = fuelData();
        fuelRemainder = ThrusterFuelSupport.consume(fuelTank, fuelRemainder,
                fuel.fuelConsumption() * redstonePower() / fuel.efficiency());
        setChanged();
    }

    @Override
    public Direction getBlockDirection() {
        return getBlockState().getValue(RcsThrusterBlock.FACING);
    }

    @Override
    public double getAirflow() {
        return 0.0;
    }

    @Override
    public double getThrust() {
        return THRUST * redstonePower();
    }

    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    @Override
    public double getScaledThrust() {
        return level != null && SpaceConstants.isSpace(level)
                ? -getThrust() : BlockEntityPropeller.super.getScaledThrust();
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!isActive()) return;
        Direction facing = getBlockDirection();
        ThrusterFuelData fuel = fuelData();
        double mass = Math.max(1.0, subLevel.getMassTracker().getMass());
        double power = redstonePower();
        double signedThrust = -getThrust() * fuel.thrustMultiplier();
        Vector3d force = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ())
                .normalize().mul(Math.signum(signedThrust));
        Vector3d velocity = subLevel.logicalPose().orientation()
                .transformInverse(handle.getLinearVelocity(new Vector3d()));
        double currentForwardSpeed = velocity.dot(force);
        double remainingSpeed = fuel.maxSpeed() - currentForwardSpeed;
        if (remainingSpeed <= 0.0) return;
        double forceMagnitude = Math.min(Math.min(
                Math.abs(signedThrust) * timeStep,
                mass * fuel.maxAcceleration() * power * timeStep),
                mass * remainingSpeed);
        force.mul(forceMagnitude);
        QueuedForceGroup group = subLevel.getOrCreateQueuedForceGroup(AstronauticsForceGroups.THRUST.get());
        group.applyAndRecordPointForce(JOMLConversion.atCenterOf(getBlockPos()), force);
    }

}
