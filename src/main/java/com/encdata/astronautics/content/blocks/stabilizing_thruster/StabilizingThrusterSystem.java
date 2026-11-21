package com.encdata.astronautics.content.blocks.stabilizing_thruster;

import com.encdata.astronautics.registry.AstronauticsForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerBlockEntity;
import com.encdata.astronautics.content.blocks.ship_computer.ShipComputerMode;
import com.encdata.astronautics.space.CelestialGravity;
import com.encdata.astronautics.space.SpaceConstants;

public final class StabilizingThrusterSystem {
    private static final double STOP_EPSILON = 0.0025;
    private static final int ALLOCATION_ITERATIONS = 18;
    private static final double MIN_DENOMINATOR = 1.0e-12;
    private static final double MIN_APPLIED_IMPULSE = 1.0e-8;

    private StabilizingThrusterSystem() {}

    public static void apply(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        MassData massData = subLevel.getMassTracker();
        Vector3dc centerOfMass = massData.getCenterOfMass();
        double inverseMass = massData.getInverseMass();
        if (centerOfMass == null || !(inverseMass > 0.0) || !Double.isFinite(inverseMass)) return;

        List<StabilizingThrusterBlockEntity> thrusters = new ArrayList<>();
        ShipComputerBlockEntity computer = null;
        for (var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof StabilizingThrusterBlockEntity thruster && thruster.isActive()) {
                thrusters.add(thruster);
            } else if (actor instanceof ShipComputerBlockEntity candidate
                    && (computer == null || candidate.getBlockPos().asLong() < computer.getBlockPos().asLong())) {
                computer = candidate;
            }
        }
        if (thrusters.isEmpty()) return;
        thrusters.sort(Comparator.comparingLong(thruster -> thruster.getBlockPos().asLong()));

        ShipComputerMode mode = computer == null ? ShipComputerMode.BOTH : computer.mode();
        if (mode == ShipComputerMode.OFF) return;

        List<NozzleResponse> nozzles = new ArrayList<>(thrusters.size());
        double characteristicRadius = 1.0;
        for (StabilizingThrusterBlockEntity thruster : thrusters) {
            Vector3d position = new Vector3d(thruster.getBlockPos().getX() + 0.5,
                    thruster.getBlockPos().getY() + 0.5, thruster.getBlockPos().getZ() + 0.5);
            Vector3d direction = thruster.forceDirection();
            Vector3d offset = position.sub(centerOfMass, new Vector3d());
            double maximumImpulse = thruster.maximumImpulse(subLevel, timeStep);
            if (!(maximumImpulse > 0.0) || !Double.isFinite(maximumImpulse)) continue;

            Vector3d angularPerImpulse = massData.getInverseInertiaTensor()
                    .transform(offset.cross(direction, new Vector3d()), new Vector3d());
            nozzles.add(new NozzleResponse(thruster, position, direction,
                    direction.mul(inverseMass, new Vector3d()), angularPerImpulse, maximumImpulse));
            characteristicRadius = Math.max(characteristicRadius, offset.length());
        }
        if (nozzles.isEmpty()) return;

        Vector3d linearVelocity = subLevel.logicalPose().orientation()
                .transformInverse(handle.getLinearVelocity(new Vector3d()));
        Vector3d angularVelocity = subLevel.logicalPose().orientation()
                .transformInverse(handle.getAngularVelocity(new Vector3d()));
        boolean dampenPosition = mode.dampensPosition();
        boolean dampenRotation = mode.dampensRotation();
        double orbitLinearWeight = 1.0;
        if (mode == ShipComputerMode.ORBIT) {
            var level = subLevel.getLevel();
            if (!SpaceConstants.isSpace(level)) return;
            Vector3d worldPosition = subLevel.logicalPose().transformPosition(
                    new Vector3d(centerOfMass), new Vector3d());
            Vector3d worldVelocity = handle.getLinearVelocity(new Vector3d());
            var target = CelestialGravity.orbitTarget(computer.targetBody(), worldPosition,
                    computer.targetAltitude(), level.getDayTime() / 24000.0);
            if (target == null) return;

            Vector3d radialLocal = subLevel.logicalPose().orientation()
                    .transformInverse(target.radial(), new Vector3d());
            double outwardAcceleration = 0.0;
            double inwardAcceleration = 0.0;
            for (int nozzleIndex = 0; nozzleIndex < nozzles.size(); nozzleIndex++) {
                NozzleResponse nozzle = nozzles.get(nozzleIndex);
                double radialDot = nozzle.direction.dot(radialLocal);
                double maximumRadialAcceleration = radialDot * nozzle.maximumImpulse
                        * inverseMass / timeStep;
                if (maximumRadialAcceleration > 0.0) outwardAcceleration += maximumRadialAcceleration;
                else inwardAcceleration -= maximumRadialAcceleration;
            }
            Vector3d targetLocalVelocity = subLevel.logicalPose().orientation().transformInverse(
                    CelestialGravity.orbitHoldVelocity(target, worldVelocity, outwardAcceleration,
                            inwardAcceleration, timeStep), new Vector3d());
            linearVelocity.sub(targetLocalVelocity, linearVelocity);
            dampenPosition = true;
            dampenRotation = true;
            orbitLinearWeight = 1_000.0;
        }
        if ((!dampenPosition || linearVelocity.lengthSquared() <= STOP_EPSILON * STOP_EPSILON)
                && (!dampenRotation || angularVelocity.lengthSquared() <= STOP_EPSILON * STOP_EPSILON)) return;
        double angularWeightSquared = characteristicRadius * characteristicRadius;
        Vector3d remainingLinear = dampenPosition ? linearVelocity.negate(new Vector3d()) : new Vector3d();
        Vector3d remainingAngular = dampenRotation ? angularVelocity.negate(new Vector3d()) : new Vector3d();
        double linearWeight = dampenPosition ? orbitLinearWeight : 0.0;
        double angularWeight = dampenRotation ? angularWeightSquared : 0.0;
        if (mode == ShipComputerMode.ORBIT) {
            allocate(nozzles, remainingLinear, remainingAngular, orbitLinearWeight, 0.0, null);
            double[] orbitImpulses = new double[nozzles.size()];
            for (int i = 0; i < nozzles.size(); i++) orbitImpulses[i] = nozzles.get(i).impulse;
            allocate(nozzles, remainingLinear, remainingAngular, orbitLinearWeight,
                    angularWeight, orbitImpulses);
        } else {
            allocate(nozzles, remainingLinear, remainingAngular, linearWeight, angularWeight, null);
        }

        QueuedForceGroup group = subLevel.getOrCreateQueuedForceGroup(AstronauticsForceGroups.THRUST.get());
        for (NozzleResponse nozzle : nozzles) {
            if (!(nozzle.impulse > MIN_APPLIED_IMPULSE)) continue;
            group.applyAndRecordPointForce(nozzle.position,
                    nozzle.direction.mul(nozzle.impulse, new Vector3d()));
            nozzle.thruster.markBraking(nozzle.impulse / nozzle.maximumImpulse, timeStep);
        }
    }

    private static void allocate(List<NozzleResponse> nozzles, Vector3d remainingLinear,
                                 Vector3d remainingAngular, double linearWeight, double angularWeight,
                                 double[] minimumImpulses) {
        for (int pass = 0; pass < ALLOCATION_ITERATIONS; pass++) {
            boolean changed = false;
            for (int nozzleIndex = 0; nozzleIndex < nozzles.size(); nozzleIndex++) {
                NozzleResponse nozzle = nozzles.get(nozzleIndex);
                double denominator = linearWeight * nozzle.linearPerImpulse.lengthSquared()
                        + angularWeight * nozzle.angularPerImpulse.lengthSquared();
                if (!(denominator > MIN_DENOMINATOR) || !Double.isFinite(denominator)) continue;

                double numerator = linearWeight * nozzle.linearPerImpulse.dot(remainingLinear)
                        + angularWeight * nozzle.angularPerImpulse.dot(remainingAngular);
                double minimumImpulse = minimumImpulses == null ? 0.0 : minimumImpulses[nozzleIndex];
                double nextImpulse = Math.clamp(nozzle.impulse + numerator / denominator,
                        minimumImpulse, nozzle.maximumImpulse);
                double delta = nextImpulse - nozzle.impulse;
                if (Math.abs(delta) <= 1.0e-9) continue;

                nozzle.impulse = nextImpulse;
                remainingLinear.fma(-delta, nozzle.linearPerImpulse);
                remainingAngular.fma(-delta, nozzle.angularPerImpulse);
                changed = true;
            }
            if (!changed) break;
        }
    }

    private static final class NozzleResponse {
        private final StabilizingThrusterBlockEntity thruster;
        private final Vector3d position;
        private final Vector3d direction;
        private final Vector3d linearPerImpulse;
        private final Vector3d angularPerImpulse;
        private final double maximumImpulse;
        private double impulse;

        private NozzleResponse(StabilizingThrusterBlockEntity thruster, Vector3d position, Vector3d direction,
                               Vector3d linearPerImpulse, Vector3d angularPerImpulse, double maximumImpulse) {
            this.thruster = thruster;
            this.position = position;
            this.direction = direction;
            this.linearPerImpulse = linearPerImpulse;
            this.angularPerImpulse = angularPerImpulse;
            this.maximumImpulse = maximumImpulse;
        }
    }
}
