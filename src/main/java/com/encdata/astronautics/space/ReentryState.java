package com.encdata.astronautics.space;

import net.minecraft.world.phys.Vec3;

import java.util.List;

public record ReentryState(
        boolean active,
        boolean planetDimension,
        Vec3 position,
        double altitude,
        double distanceToAtmosphere,
        double inwardSpeed,
        double speed,
        double density,
        Vec3 planetDirection,
        Vec3 velocity,
        String dimensionId,
        String planetId,
        List<ReentrySurfaceSample> surfaceSamples
) {
    private static final double MAXIMUM_EFFECT_STRENGTH_ENERGY = 250.0;

    public static ReentryState inactive(boolean planetDimension, Vec3 position, double altitude,
                                        double distanceToAtmosphere, Vec3 velocity) {
        return new ReentryState(false, planetDimension, position, altitude, distanceToAtmosphere,
                0.0, velocity.length(), 0.0, Vec3.ZERO, velocity, "", "", List.of());
    }

    public double energy() {
        return density * Math.max(0.0, inwardSpeed) * speed * speed;
    }

    public float strength() {
        return (float) Math.clamp(energy() / MAXIMUM_EFFECT_STRENGTH_ENERGY, 0.0, 1.0);
    }

    public ReentryState withPosition(Vec3 value) {
        return new ReentryState(active, planetDimension, value, altitude, distanceToAtmosphere,
                inwardSpeed, speed, density, planetDirection, velocity, dimensionId, planetId, surfaceSamples);
    }

    public ReentryState withSurfaceSamples(List<ReentrySurfaceSample> samples) {
        return new ReentryState(active, planetDimension, position, altitude, distanceToAtmosphere,
                inwardSpeed, speed, density, planetDirection, velocity, dimensionId, planetId,
                List.copyOf(samples));
    }
}
