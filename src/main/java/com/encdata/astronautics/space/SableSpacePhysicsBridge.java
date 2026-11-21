package com.encdata.astronautics.space;

import com.encdata.astronautics.content.blocks.stabilizing_thruster.StabilizingThrusterSystem;
import com.encdata.astronautics.registry.AstronauticsForceGroups;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;

public final class SableSpacePhysicsBridge {
    private SableSpacePhysicsBridge() {}

    public static void register() {
        SableEventPlatform.INSTANCE.onPhysicsTick(SableSpacePhysicsBridge::onPrePhysicsTick);
    }

    private static void onPrePhysicsTick(SubLevelPhysicsSystem physics, double timeStep) {
        if (!(physics.getLevel() instanceof ServerLevel level)) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        boolean space = SpaceConstants.isSpace(level);
        boolean planet = SpaceCatalog.isPlanetDimension(level.dimension());
        double gravityCorrection = 0.0;
        double stepRetention = 1.0;
        var gravitySources = space
                ? CelestialGravity.sourcesAtTime(
                        (level.getDayTime() + physics.getPartialPhysicsTick()) / 24000.0)
                : List.<CelestialGravity.GravitySource>of();
        if (planet) {
            PlanetEnvironmentData environment = SpaceCatalog.environmentForDimension(level.dimension());
            gravityCorrection = PlanetEnvironmentData.EARTH_GRAVITY - environment.gravity();
            double retainedVelocity = Math.clamp(environment.airDrag(), 0.0, 1.0);
            stepRetention = Math.pow(retainedVelocity, timeStep * 20.0);
        }

        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            var handle = physics.getPhysicsHandle(subLevel);
            boolean valid = handle.isValid();
            if (valid) StabilizingThrusterSystem.apply(subLevel, handle, timeStep);
            if (valid) CrewPushes.apply(subLevel);

            if (space) {
                clearForceGroup(subLevel, ForceGroups.BALLOON_LIFT.get());
                clearForceGroup(subLevel, ForceGroups.PROPULSION.get());
                if (valid) {
                    var pose = subLevel.logicalPose();
                    MassData massData = subLevel.getMassTracker();
                    double shipMassKpg = massData.getMass();
                    var centerOfMass = massData.getCenterOfMass();
                    var gravityGroup = subLevel.getOrCreateQueuedForceGroup(
                            AstronauticsForceGroups.CELESTIAL_GRAVITY.get());

                    if (centerOfMass != null && shipMassKpg > 0.0) {
                        Vector3d worldCenter = pose.transformPosition(
                                new Vector3d(centerOfMass), new Vector3d());
                        SpacePosition globalCenter = SpacePosition.normalize(
                                SpaceGrids.shipGrid(level, subLevel),
                                worldCenter.x, worldCenter.y, worldCenter.z);
                        double[] worldGradient = new double[9];
                        Vector3d worldAcceleration = new Vector3d();
                        Vector3d worldImpulse = new Vector3d();
                        Vector3d localImpulse = new Vector3d();
                        worldAcceleration.zero();
                        for (CelestialGravity.GravitySource source : gravitySources) {
                            CelestialGravity.addField(globalCenter, source, worldAcceleration,
                                    worldGradient);
                        }
                        worldAcceleration.mul(shipMassKpg * timeStep, worldImpulse);
                        pose.transformNormalInverse(worldImpulse, localImpulse);
                        gravityGroup.applyAndRecordPointForce(centerOfMass, localImpulse);

                        double[] localGradient = toLocalGradient(worldGradient, pose.orientation());
                        Vector3d torqueImpulse = gravityGradientTorque(
                                massData, localGradient).mul(timeStep);
                        gravityGroup.getForceTotal().applyAngularImpulse(torqueImpulse);
                    }
                    ReentrySystem.damageIfDescending(subLevel, handle);
                }
                continue;
            }
            if (!planet || !valid) continue;

            Vector3d velocity = handle.getLinearVelocity(new Vector3d());
            Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());
            Vector3d linearCorrection = new Vector3d(
                    velocity.x * (stepRetention - 1.0),
                    gravityCorrection * timeStep + velocity.y * (stepRetention - 1.0),
                    velocity.z * (stepRetention - 1.0));
            Vector3d angularCorrection = angularVelocity.mul(stepRetention - 1.0);
            handle.addLinearAndAngularVelocity(linearCorrection, angularCorrection);
            ReentrySystem.damageIfDescending(subLevel, handle);
        }
    }

    private static void clearForceGroup(ServerSubLevel subLevel, ForceGroup group) {
        Map<ForceGroup, QueuedForceGroup> groups = subLevel.getQueuedForceGroups();
        if (groups == null) return;
        QueuedForceGroup queued = groups.get(group);
        if (queued != null) queued.reset();
    }

    private static double[] toLocalGradient(double[] worldGradient,
                                            org.joml.Quaterniondc orientation) {
        double[] localGradient = new double[9];
        Vector3d localAxis = new Vector3d();
        Vector3d worldAxis = new Vector3d();
        Vector3d worldResult = new Vector3d();
        Vector3d localResult = new Vector3d();
        for (int column = 0; column < 3; column++) {
            switch (column) {
                case 0 -> localAxis.set(1.0, 0.0, 0.0);
                case 1 -> localAxis.set(0.0, 1.0, 0.0);
                default -> localAxis.set(0.0, 0.0, 1.0);
            }
            orientation.transform(localAxis, worldAxis);
            worldResult.set(
                    worldGradient[0] * worldAxis.x + worldGradient[1] * worldAxis.y
                            + worldGradient[2] * worldAxis.z,
                    worldGradient[3] * worldAxis.x + worldGradient[4] * worldAxis.y
                            + worldGradient[5] * worldAxis.z,
                    worldGradient[6] * worldAxis.x + worldGradient[7] * worldAxis.y
                            + worldGradient[8] * worldAxis.z);
            orientation.transformInverse(worldResult, localResult);
            localGradient[column] = localResult.x;
            localGradient[3 + column] = localResult.y;
            localGradient[6 + column] = localResult.z;
        }
        return localGradient;
    }

    private static Vector3d gravityGradientTorque(MassData massData, double[] gradient) {
        var inertiaTensor = massData.getInertiaTensor();
        double[][] inertia = {
                {inertiaTensor.m00(), inertiaTensor.m10(), inertiaTensor.m20()},
                {inertiaTensor.m01(), inertiaTensor.m11(), inertiaTensor.m21()},
                {inertiaTensor.m02(), inertiaTensor.m12(), inertiaTensor.m22()}
        };
        double halfTrace = (inertia[0][0] + inertia[1][1] + inertia[2][2]) * 0.5;
        double[][] secondMoment = new double[3][3];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                secondMoment[row][column] = (row == column ? halfTrace : 0.0)
                        - inertia[row][column];
            }
        }

        double torqueX = 0.0;
        double torqueY = 0.0;
        double torqueZ = 0.0;
        for (int axis = 0; axis < 3; axis++) {
            torqueX += secondMoment[1][axis] * gradient[6 + axis]
                    - secondMoment[2][axis] * gradient[3 + axis];
            torqueY += secondMoment[2][axis] * gradient[axis]
                    - secondMoment[0][axis] * gradient[6 + axis];
            torqueZ += secondMoment[0][axis] * gradient[3 + axis]
                    - secondMoment[1][axis] * gradient[axis];
        }
        return new Vector3d(torqueX, torqueY, torqueZ);
    }

}
