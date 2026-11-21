package com.encdata.astronautics.space;

import com.encdata.astronautics.registry.AstronauticsFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class ThrusterFuelSupport {
    private ThrusterFuelSupport() {}

    public static boolean isValid(FluidTank tank, int tier) {
        return !tank.isEmpty() && AstronauticsFluids.isFuel(tank.getFluid().getFluid())
                && data(tank).requiredThrusterTier() <= tier;
    }

    public static ThrusterFuelData data(FluidTank tank) {
        if (tank.isEmpty()) return ThrusterFuelData.DEFAULT;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(tank.getFluid().getFluid());
        return id == null ? ThrusterFuelData.DEFAULT : ThrusterFuelDataLoader.get(id);
    }

    public static double consume(FluidTank tank, double remainder, double requestedAmount) {
        double next = remainder + requestedAmount;
        int amount = (int) next;
        if (amount <= 0) return next;
        tank.drain(amount, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        return next - amount;
    }
}
