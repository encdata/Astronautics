package com.encdata.astronautics.world;

import com.encdata.astronautics.Astronautics;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModDensityFunctions {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Astronautics.MOD_ID);

    public static final Supplier<MapCodec<MoonCraterDensityFunction>> MOON_CRATERS =
            DENSITY_FUNCTIONS.register("moon_craters", () -> MoonCraterDensityFunction.MAP_CODEC);

    private ModDensityFunctions() {
    }
}
