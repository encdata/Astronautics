package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CelestialLightingData(
        float ambient,
        float diffuse,
        float specular,
        float specularPower,
        int maxStarContributions,
        float sunDiscRadius,
        float shadowStrength,
        float ringShadowStrength,
        float materialRoughness
) {
    private static final CelestialLightingData DEFAULT = new CelestialLightingData(
            0.03f, 1.0f, 0.5f, 32.0f, 8,
            0.0047f, 1.0f, 1.0f, 0.7f
    );

    public static final Codec<CelestialLightingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("ambient", DEFAULT.ambient()).forGetter(CelestialLightingData::ambient),
            Codec.FLOAT.optionalFieldOf("diffuse", DEFAULT.diffuse()).forGetter(CelestialLightingData::diffuse),
            Codec.FLOAT.optionalFieldOf("specular", DEFAULT.specular()).forGetter(CelestialLightingData::specular),
            Codec.FLOAT.optionalFieldOf("specular_power", DEFAULT.specularPower())
                    .forGetter(CelestialLightingData::specularPower),
            Codec.INT.optionalFieldOf("max_star_contributions", DEFAULT.maxStarContributions())
                    .forGetter(CelestialLightingData::maxStarContributions),
            Codec.FLOAT.optionalFieldOf("sun_disc_radius", DEFAULT.sunDiscRadius())
                    .forGetter(CelestialLightingData::sunDiscRadius),
            Codec.FLOAT.optionalFieldOf("shadow_strength", DEFAULT.shadowStrength())
                    .forGetter(CelestialLightingData::shadowStrength),
            Codec.FLOAT.optionalFieldOf("ring_shadow_strength", DEFAULT.ringShadowStrength())
                    .forGetter(CelestialLightingData::ringShadowStrength),
            Codec.FLOAT.optionalFieldOf("material_roughness", DEFAULT.materialRoughness())
                    .forGetter(CelestialLightingData::materialRoughness)
    ).apply(instance, CelestialLightingData::new));

    public static CelestialLightingData defaults() {
        return DEFAULT;
    }
}
