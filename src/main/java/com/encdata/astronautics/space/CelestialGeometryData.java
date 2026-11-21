package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record CelestialGeometryData(
        double axialTilt,
        double oblateness,
        double poleYaw,
        double polePitch,
        List<CelestialRingData> rings
) {
    private static final CelestialGeometryData DEFAULT = new CelestialGeometryData(0.0, 0.0, 0.0, 0.0, List.of());

    public static final Codec<CelestialGeometryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("axial_tilt", DEFAULT.axialTilt())
                    .forGetter(CelestialGeometryData::axialTilt),
            Codec.DOUBLE.optionalFieldOf("oblateness", DEFAULT.oblateness())
                    .forGetter(CelestialGeometryData::oblateness),
            Codec.DOUBLE.optionalFieldOf("pole_yaw", DEFAULT.poleYaw())
                    .forGetter(CelestialGeometryData::poleYaw),
            Codec.DOUBLE.optionalFieldOf("pole_pitch", DEFAULT.polePitch())
                    .forGetter(CelestialGeometryData::polePitch),
            CelestialRingData.CODEC.listOf().optionalFieldOf("rings", DEFAULT.rings())
                    .forGetter(CelestialGeometryData::rings)
    ).apply(instance, CelestialGeometryData::new));

    public static CelestialGeometryData defaults() {
        return DEFAULT;
    }
}
