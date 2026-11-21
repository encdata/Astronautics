package com.encdata.astronautics.registry.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.particles.ExhaustParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class AstronauticsParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Astronautics.MOD_ID);

    public static final Supplier<SimpleParticleType> THRUSTER_SMOKE =
            PARTICLES.register("thruster_smoke", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> THRUSTER_FLAME =
            PARTICLES.register("thruster_flame", () -> new SimpleParticleType(false));

    public static final Supplier<ExhaustParticleOptions.Type> EXHAUST_PLUME =
            PARTICLES.register("exhaust_plume", ExhaustParticleOptions.Type::new);
    public static final Supplier<ExhaustParticleOptions.Type> EXHAUST_CLOUD =
            PARTICLES.register("exhaust_cloud", ExhaustParticleOptions.Type::new);

    private AstronauticsParticles() {}
}
