package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CelestialRenderData(
        CelestialLightingData lighting,
        AtmosphereData atmosphere,
        CelestialGeometryData geometry
) {
    private static final CelestialRenderData DEFAULT = new CelestialRenderData(
            CelestialLightingData.defaults(),
            AtmosphereData.defaults(),
            CelestialGeometryData.defaults()
    );

    public static final Codec<CelestialRenderData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CelestialLightingData.CODEC.optionalFieldOf("lighting", DEFAULT.lighting())
                    .forGetter(CelestialRenderData::lighting),
            AtmosphereData.CODEC.optionalFieldOf("atmosphere", DEFAULT.atmosphere())
                    .forGetter(CelestialRenderData::atmosphere),
            CelestialGeometryData.CODEC.optionalFieldOf("geometry", DEFAULT.geometry())
                    .forGetter(CelestialRenderData::geometry)
    ).apply(instance, CelestialRenderData::new));

    public static CelestialRenderData defaults() {
        return DEFAULT;
    }
}
