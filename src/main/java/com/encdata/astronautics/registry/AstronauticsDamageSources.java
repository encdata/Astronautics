package com.encdata.astronautics.registry;

import com.encdata.astronautics.Astronautics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public final class AstronauticsDamageSources {
    public static final ResourceKey<DamageType> VACUUM = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "vacuum"));

    public static final ResourceKey<DamageType> EXHAUST = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "exhaust"));

    private AstronauticsDamageSources() {
    }

    public static DamageSource vacuum(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(VACUUM));
    }

    public static DamageSource exhaust(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(EXHAUST));
    }
}
