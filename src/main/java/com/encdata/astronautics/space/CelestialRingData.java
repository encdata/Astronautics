package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record CelestialRingData(
        ResourceLocation texture,
        double innerRadius,
        double outerRadius,
        double tilt,
        double rotationPeriod,
        float opacity,
        float thickness,
        float transmission,
        float shadowDensity,
        float selfShadowStrength
) {
    public static final Codec<CelestialRingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("texture").forGetter(CelestialRingData::texture),
            Codec.DOUBLE.fieldOf("inner_radius").forGetter(CelestialRingData::innerRadius),
            Codec.DOUBLE.fieldOf("outer_radius").forGetter(CelestialRingData::outerRadius),
            Codec.DOUBLE.optionalFieldOf("tilt", 0.0).forGetter(CelestialRingData::tilt),
            Codec.DOUBLE.optionalFieldOf("rotation_period", 0.0).forGetter(CelestialRingData::rotationPeriod),
            Codec.FLOAT.optionalFieldOf("opacity", 1.0f).forGetter(CelestialRingData::opacity),
            Codec.FLOAT.optionalFieldOf("thickness", 90.0f)
                    .forGetter(CelestialRingData::thickness),
            Codec.FLOAT.optionalFieldOf("transmission", 0.22f)
                    .forGetter(CelestialRingData::transmission),
            Codec.FLOAT.optionalFieldOf("shadow_density", 0.8f)
                    .forGetter(CelestialRingData::shadowDensity),
            Codec.FLOAT.optionalFieldOf("self_shadow_strength", 0.6f)
                    .forGetter(CelestialRingData::selfShadowStrength)
    ).apply(instance, CelestialRingData::new));
}
