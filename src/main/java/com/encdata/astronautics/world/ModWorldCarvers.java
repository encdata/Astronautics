package com.encdata.astronautics.world;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModWorldCarvers {
    public static final DeferredRegister<WorldCarver<?>> WORLD_CARVERS =
            DeferredRegister.create(Registries.CARVER, Astronautics.MOD_ID);

    public static final Supplier<WorldCarver<?>> MOON_CRATER = WORLD_CARVERS.register(
            "crater", () -> new CraterWorldCarver(CarverConfiguration.CODEC.codec()));

    private ModWorldCarvers() {
    }
}
