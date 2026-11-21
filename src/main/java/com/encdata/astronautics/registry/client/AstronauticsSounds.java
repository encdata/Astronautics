package com.encdata.astronautics.registry.client;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AstronauticsSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Astronautics.MOD_ID);

    public static final Supplier<SoundEvent> THRUSTER_LOOP = SOUNDS.register("thruster_loop", () ->
            SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "thruster_loop")));

    private AstronauticsSounds() {}
}
