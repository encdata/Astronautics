package com.encdata.astronautics.server;

import com.encdata.astronautics.registry.AstronauticsBlockEntities;
import com.encdata.astronautics.space.SpaceDataLoader;
import com.encdata.astronautics.space.ThrusterFuelDataLoader;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class AstronauticsServerRegistrations {
    private AstronauticsServerRegistrations() {}

    public static void registerReloadListeners(AddReloadListenerEvent event) {
        event.addListener(SpaceDataLoader.INSTANCE);
        event.addListener(ThrusterFuelDataLoader.INSTANCE);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                AstronauticsBlockEntities.THRUSTER.get(),
                (be, side) -> be.getFuelHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                AstronauticsBlockEntities.OXYGEN_DISTRIBUTOR.get(),
                (be, side) -> be.getOxygenHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
                AstronauticsBlockEntities.STABILIZING_THRUSTER.get(),
                (be, side) -> be.getFuelHandler(side));
    }
}
