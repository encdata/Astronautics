package com.encdata.astronautics.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Optional;

public record CelestialBodyData(
        ResourceLocation id,
        String name,
        String type,
        SpaceVec3 position,
        double radius,
        double mass,
        double gravityScale,
        Optional<OrbitData> orbit,
        Optional<ResourceLocation> dimension,
        PlanetEnvironmentData environment,
        CelestialVisualData visual,
        List<CelestialBodyData> bodies
) {
    private static final int ECCENTRIC_ANOMALY_ITERATIONS = 8;
    private static final double FULL_ROTATION_RADIANS = Math.PI * 2.0;
    private static final double MAX_ECCENTRICITY = 0.999999;

    private static Codec<CelestialBodyData> createCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(CelestialBodyData::id),
                Codec.STRING.fieldOf("name").forGetter(CelestialBodyData::name),
                Codec.STRING.fieldOf("type").forGetter(CelestialBodyData::type),
                SpaceVec3.CODEC.optionalFieldOf("position", SpaceVec3.ZERO).forGetter(CelestialBodyData::position),
                Codec.DOUBLE.optionalFieldOf("radius", 1.0).forGetter(CelestialBodyData::radius),
                Codec.DOUBLE.optionalFieldOf("mass", 0.0).forGetter(CelestialBodyData::mass),
                Codec.DOUBLE.optionalFieldOf("gravity_scale", 1.0).forGetter(CelestialBodyData::gravityScale),
                OrbitData.CODEC.optionalFieldOf("orbit").forGetter(CelestialBodyData::orbit),
                ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(CelestialBodyData::dimension),
                PlanetEnvironmentData.CODEC.optionalFieldOf("environment", PlanetEnvironmentData.EARTH_LIKE)
                        .forGetter(CelestialBodyData::environment),
                CelestialVisualData.CODEC.optionalFieldOf("visual", CelestialVisualData.defaults())
                        .forGetter(CelestialBodyData::visual),
                Codec.lazyInitialized(CelestialBodyData::createCodec).listOf()
                        .optionalFieldOf("bodies", List.of()).forGetter(CelestialBodyData::bodies)
        ).apply(instance, CelestialBodyData::new));
    }

    public static final Codec<CelestialBodyData> CODEC = Codec.lazyInitialized(CelestialBodyData::createCodec);

    public SpaceVec3 positionAt(double timeDays) {
        if (orbit.isEmpty()) return position;
        OrbitData orbitData = orbit.get();
        if (orbitData.period() <= 0.0) return position;

        double eccentricity = Math.clamp(orbitData.eccentricity(), 0.0, MAX_ECCENTRICITY);
        double meanAnomaly = Math.toRadians(orbitData.initialAngle())
                + (timeDays / orbitData.period()) * FULL_ROTATION_RADIANS;

        double eccentricAnomaly = meanAnomaly;
        for (int i = 0; i < ECCENTRIC_ANOMALY_ITERATIONS; i++) {
            eccentricAnomaly -= (eccentricAnomaly - eccentricity * Math.sin(eccentricAnomaly) - meanAnomaly)
                    / (1.0 - eccentricity * Math.cos(eccentricAnomaly));
        }

        double semiMajorAxis = orbitData.distance();
        double orbitalX = semiMajorAxis * (Math.cos(eccentricAnomaly) - eccentricity);
        double orbitalZ = semiMajorAxis * Math.sqrt(1.0 - eccentricity * eccentricity)
                * Math.sin(eccentricAnomaly);

        Quaterniond orbitalRotation = new Quaterniond()
                .rotateY(Math.toRadians(orbitData.ascendingNode()))
                .rotateX(Math.toRadians(orbitData.inclination()))
                .rotateY(Math.toRadians(orbitData.argumentOfPeriapsis()));
        Vector3d orbitalPosition = orbitalRotation.transform(new Vector3d(orbitalX, 0.0, orbitalZ));

        return position.add(new SpaceVec3(orbitalPosition.x, orbitalPosition.y, orbitalPosition.z));
    }
}
