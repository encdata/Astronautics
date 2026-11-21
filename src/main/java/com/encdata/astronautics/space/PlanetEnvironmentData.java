package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlanetEnvironmentData(
        boolean oxygen,
        double gravity,
        double temperature,
        double pressure,
        double airDrag,
        boolean customSkybox,
        String skyRotationAxis
) {

    public static final double EARTH_GRAVITY = 9.80665;
    public static final PlanetEnvironmentData EARTH_LIKE =
            new PlanetEnvironmentData(true, EARTH_GRAVITY, 15.0, 101.325, 0.985, false, "z");

    public static final Codec<PlanetEnvironmentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("oxygen", EARTH_LIKE.oxygen()).forGetter(PlanetEnvironmentData::oxygen),
            Codec.DOUBLE.optionalFieldOf("gravity", EARTH_LIKE.gravity())
                    .forGetter(PlanetEnvironmentData::gravity),
            Codec.DOUBLE.optionalFieldOf("temperature", EARTH_LIKE.temperature())
                    .forGetter(PlanetEnvironmentData::temperature),
            Codec.DOUBLE.optionalFieldOf("pressure", EARTH_LIKE.pressure())
                    .forGetter(PlanetEnvironmentData::pressure),
            Codec.DOUBLE.optionalFieldOf("air_drag", EARTH_LIKE.airDrag())
                    .forGetter(PlanetEnvironmentData::airDrag),
            Codec.BOOL.optionalFieldOf("custom_skybox", EARTH_LIKE.customSkybox())
                    .forGetter(PlanetEnvironmentData::customSkybox),
            Codec.STRING.optionalFieldOf("sky_rotation_axis", EARTH_LIKE.skyRotationAxis())
                    .forGetter(PlanetEnvironmentData::skyRotationAxis)
    ).apply(instance, PlanetEnvironmentData::new));
}
