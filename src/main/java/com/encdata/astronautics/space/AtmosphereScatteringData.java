package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AtmosphereScatteringData(
        boolean enabled,
        float rayleighRed,
        float rayleighGreen,
        float rayleighBlue,
        float mieCoefficient,
        float rayleighScaleHeight,
        float mieScaleHeight,
        float mieG,
        float sunIntensity,
        float exposure
) {
    private static final AtmosphereScatteringData DEFAULT = new AtmosphereScatteringData(
            false, 0.0058f, 0.0135f, 0.0331f, 0.0210f,
            8.0f, 1.2f, 0.76f, 22.0f, 1.0f
    );

    public static final Codec<AtmosphereScatteringData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled()).forGetter(AtmosphereScatteringData::enabled),
            Codec.FLOAT.optionalFieldOf("rayleigh_red", DEFAULT.rayleighRed())
                    .forGetter(AtmosphereScatteringData::rayleighRed),
            Codec.FLOAT.optionalFieldOf("rayleigh_green", DEFAULT.rayleighGreen())
                    .forGetter(AtmosphereScatteringData::rayleighGreen),
            Codec.FLOAT.optionalFieldOf("rayleigh_blue", DEFAULT.rayleighBlue())
                    .forGetter(AtmosphereScatteringData::rayleighBlue),
            Codec.FLOAT.optionalFieldOf("mie_coefficient", DEFAULT.mieCoefficient())
                    .forGetter(AtmosphereScatteringData::mieCoefficient),
            Codec.FLOAT.optionalFieldOf("rayleigh_scale_height", DEFAULT.rayleighScaleHeight())
                    .forGetter(AtmosphereScatteringData::rayleighScaleHeight),
            Codec.FLOAT.optionalFieldOf("mie_scale_height", DEFAULT.mieScaleHeight())
                    .forGetter(AtmosphereScatteringData::mieScaleHeight),
            Codec.FLOAT.optionalFieldOf("mie_g", DEFAULT.mieG()).forGetter(AtmosphereScatteringData::mieG),
            Codec.FLOAT.optionalFieldOf("sun_intensity", DEFAULT.sunIntensity())
                    .forGetter(AtmosphereScatteringData::sunIntensity),
            Codec.FLOAT.optionalFieldOf("exposure", DEFAULT.exposure()).forGetter(AtmosphereScatteringData::exposure)
    ).apply(instance, AtmosphereScatteringData::new));

    public static AtmosphereScatteringData defaults() {
        return DEFAULT;
    }
}
