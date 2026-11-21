package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Supplier;

@EventBusSubscriber(modid = Astronautics.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AstronauticsFluidClient {
    private AstronauticsFluidClient() {}

    @SubscribeEvent
    public static void registerFluidTypes(RegisterClientExtensionsEvent event) {
        register(event, AstronauticsFluids.BIOETHANOL.type);
        register(event, AstronauticsFluids.METHANOL.type);
        register(event, AstronauticsFluids.GASOLINE.type);
        register(event, AstronauticsFluids.KEROSENE.type);
    }

    private static void register(RegisterClientExtensionsEvent event, Supplier<FluidType> supplier) {
        AstronauticsFluidType type = (AstronauticsFluidType) supplier.get();
        event.registerFluidType(type, type);
    }
}
