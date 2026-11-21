package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record OrbitData(
        double distance,
        double period,
        double initialAngle,
        double inclination,
        double eccentricity,
        double ascendingNode,
        double argumentOfPeriapsis
) {
    public static final Codec<OrbitData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("distance").forGetter(OrbitData::distance),
            Codec.DOUBLE.fieldOf("period").forGetter(OrbitData::period),
            Codec.DOUBLE.optionalFieldOf("initial_angle", 0.0)
                    .forGetter(OrbitData::initialAngle),
            Codec.DOUBLE.optionalFieldOf("inclination", 0.0)
                    .forGetter(OrbitData::inclination),
            Codec.DOUBLE.optionalFieldOf("eccentricity", 0.0)
                    .forGetter(OrbitData::eccentricity),
            Codec.DOUBLE.optionalFieldOf("ascending_node", 0.0)
                    .forGetter(OrbitData::ascendingNode),
            Codec.DOUBLE.optionalFieldOf("argument_of_periapsis", 0.0)
                    .forGetter(OrbitData::argumentOfPeriapsis)
    ).apply(instance, OrbitData::new));
}
