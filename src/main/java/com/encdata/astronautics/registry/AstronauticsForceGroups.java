package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AstronauticsForceGroups {
    private static final DeferredRegister<ForceGroup> REGISTRY =
            DeferredRegister.create(ForceGroups.REGISTRY_KEY, Astronautics.MOD_ID);

    public static final Supplier<ForceGroup> THRUST = REGISTRY.register(
            "thrusters", () -> new ForceGroup(
                    Component.literal("Thrusters"),
                    Component.literal("Push from the ship's thrusters, including braking"),
                    0xFF6A2A,
                    true));
    public static final Supplier<ForceGroup> CREW = REGISTRY.register(
            "crew", () -> new ForceGroup(
                    Component.literal("Crew"),
                    Component.literal("Push from players grabbing the ship"),
                    0x4FC3F7,
                    true));
    public static final Supplier<ForceGroup> CELESTIAL_GRAVITY = REGISTRY.register(
            "celestial_gravity", () -> new ForceGroup(
                    Component.literal("Space Gravity"),
                    Component.literal("Pull from nearby planets and stars"),
                    0x8C78D8,
                    true));

    private AstronauticsForceGroups() {}

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
