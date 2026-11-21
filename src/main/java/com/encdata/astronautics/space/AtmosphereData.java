package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AtmosphereData(
        boolean enabled,
        int color,
        double height,
        double radiusOffset,
        float density,
        float rayleigh,
        float mie,
        int mieColor,
        int sunsetColor,
        float horizonHaze,
        float nightGlow,
        AtmosphereScatteringData scattering
) {
    private static final AtmosphereData DEFAULT = new AtmosphereData(
            false, 0x3A8DFF, 0.35, 0.35, 1.0f, 0.8f, 0.25f,
            0xFFFFFF, 0xFF8C38, 0.4f, 0.0f,
            AtmosphereScatteringData.defaults()
    );

    public static final Codec<AtmosphereData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled()).forGetter(AtmosphereData::enabled),
            Codec.INT.optionalFieldOf("color", DEFAULT.color()).forGetter(AtmosphereData::color),
            Codec.DOUBLE.optionalFieldOf("height", DEFAULT.height()).forGetter(AtmosphereData::height),
            Codec.DOUBLE.optionalFieldOf("radius_offset", DEFAULT.radiusOffset())
                    .forGetter(AtmosphereData::radiusOffset),
            Codec.FLOAT.optionalFieldOf("density", DEFAULT.density()).forGetter(AtmosphereData::density),
            Codec.FLOAT.optionalFieldOf("rayleigh", DEFAULT.rayleigh()).forGetter(AtmosphereData::rayleigh),
            Codec.FLOAT.optionalFieldOf("mie", DEFAULT.mie()).forGetter(AtmosphereData::mie),
            Codec.INT.optionalFieldOf("mie_color", DEFAULT.mieColor())
                    .forGetter(AtmosphereData::mieColor),
            Codec.INT.optionalFieldOf("sunset_color", DEFAULT.sunsetColor())
                    .forGetter(AtmosphereData::sunsetColor),
            Codec.FLOAT.optionalFieldOf("horizon_haze", DEFAULT.horizonHaze())
                    .forGetter(AtmosphereData::horizonHaze),
            Codec.FLOAT.optionalFieldOf("night_glow", DEFAULT.nightGlow())
                    .forGetter(AtmosphereData::nightGlow),
            AtmosphereScatteringData.CODEC.optionalFieldOf("scattering", DEFAULT.scattering())
                    .forGetter(AtmosphereData::scattering)
    ).apply(instance, AtmosphereData::new));

    public static AtmosphereData defaults() {
        return DEFAULT;
    }
}
