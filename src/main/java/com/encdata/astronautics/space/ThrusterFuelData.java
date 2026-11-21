package com.encdata.astronautics.space;

import com.encdata.astronautics.Astronautics;
import net.minecraft.resources.ResourceLocation;

public record ThrusterFuelData(
        ResourceLocation fluid,
        double efficiency,
        double thrustMultiplier,
        int fuelConsumption,
        int requiredThrusterTier,
        double maxAcceleration,
        double maxSpeed
) {
    public static final ThrusterFuelData DEFAULT = new ThrusterFuelData(
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "bioethanol"),
            0.45,
            0.55,
            10,
            1,
            8.0,
            40.0
    );
}
