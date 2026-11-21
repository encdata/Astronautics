package com.encdata.astronautics.client;

import static com.encdata.astronautics.client.CelestialShaderUniforms.setFloat;
import static com.encdata.astronautics.client.CelestialShaderUniforms.setVector;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.CelestialBodyData;
import com.encdata.astronautics.space.CelestialVisualData;
import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceVec3;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class CelestialLighting {
    static final double REFERENCE_SOLAR_DISTANCE = 320_000.0;

    record SunSource(Vector3f direction, Vector3f color) {}

    private static final List<Occulter> ACTIVE_OCCULTERS = new ArrayList<>();
    private static final Map<SpaceVec3, StarRenderData> ACTIVE_STARS = new HashMap<>();
    private static final Map<SpaceVec3, SpaceVec3> SOURCE_POSITIONS = new HashMap<>();
    private static final Map<SpaceVec3, Double> RENDER_SCALES = new HashMap<>();

    private CelestialLighting() {}

    static void clearFrame() {
        ACTIVE_OCCULTERS.clear();
        ACTIVE_STARS.clear();
        SOURCE_POSITIONS.clear();
        RENDER_SCALES.clear();
    }

    static void registerStar(SpaceVec3 renderPosition, SpaceVec3 sourcePosition,
                             GalaxyData.StarData star, double renderScale) {
        ACTIVE_STARS.put(renderPosition, new StarRenderData(star, sourcePosition, renderScale));
        SOURCE_POSITIONS.put(renderPosition, sourcePosition);
        RENDER_SCALES.put(renderPosition, renderScale);
    }

    static void collectOcculters(Iterable<CelestialBodyData> bodies, Map<ResourceLocation, SpaceVec3> positions,
                                 Map<ResourceLocation, SpaceVec3> sourcePositions,
                                 Map<ResourceLocation, Double> renderScales,
                                 Predicate<CelestialBodyData> isObserverBody) {
        for (CelestialBodyData body : bodies) {
            SpaceVec3 position = positions.get(body.id());
            if (position != null && !isObserverBody.test(body)) {
                double scale = renderScales.getOrDefault(body.id(), 1.0);
                ACTIVE_OCCULTERS.add(new Occulter(position, body.radius() * scale));
                SOURCE_POSITIONS.put(position, sourcePositions.get(body.id()));
                RENDER_SCALES.put(position, scale);
            }
            collectOcculters(body.bodies(), positions, sourcePositions, renderScales, isObserverBody);
        }
    }

    static void configureCelestialLighting(SpaceVec3 body, double radius, CelestialVisualData visual,
                                           List<SpaceVec3> suns, boolean materialPass,
                                           boolean enabled, boolean nightOnly) {
        ResourceLocation shaderId = ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID,
                materialPass ? "celestial_material" : "celestial");
        ShaderProgram shader = VeilRenderSystem.setShader(shaderId);
        if (shader == null) throw new IllegalStateException("Missing celestial shader " + shaderId);

        SpaceVec3 sun = dominantSun(body, suns);
        Vector3f sunDirection = sun == null ? new Vector3f(0.35f, 0.8f, 0.45f)
                : new Vector3f((float) (sun.x() - body.x()), (float) (sun.y() - body.y()),
                (float) (sun.z() - body.z())).normalize();
        var lighting = visual.lighting();
        float irradiance = sun == null ? 0.0f : (float) irradianceAt(body, sun);
        setVector(shader, "SunDirection", sunDirection);
        setVector(shader, "SunCenter", sun == null ? 0.0f : (float) (sun.x() - body.x()),
                sun == null ? 0.0f : (float) (sun.y() - body.y()),
                sun == null ? 0.0f : (float) (sun.z() - body.z()));
        setFloat(shader, "SunRadius", sun == null ? 0.0f : (float) starRadius(sun));
        setVector(shader, "LightingParams", (float) radius, lighting.ambient(), lighting.diffuse() * irradiance);
        if (!materialPass) setFloat(shader, "LightingEnabled", nightOnly ? 2.0f : enabled ? 1.0f : 0.0f);
        if (materialPass) {
            setFloat(shader, "MaterialSpecular", lighting.specular());
            setFloat(shader, "MaterialRoughness", lighting.materialRoughness());
        }

        Occulter blocker = strongestBlocker(body, sun);
        if (blocker == null) {
            setVector(shader, "BlockerCenter", 0.0f, 0.0f, 0.0f);
            setFloat(shader, "BlockerRadius", 0.0f);
        } else {
            setVector(shader, "BlockerCenter", (float) (blocker.position().x() - body.x()),
                    (float) (blocker.position().y() - body.y()), (float) (blocker.position().z() - body.z()));
            setFloat(shader, "BlockerRadius", (float) blocker.radius());
        }

        if (visual.geometry().rings().isEmpty()) {
            setVector(shader, "RingNormal", 0.0f, 1.0f, 0.0f);
            setVector(shader, "RingData", 0.0f, 0.0f, 0.0f);
        } else {
            var ring = visual.geometry().rings().getFirst();
            double scale = renderScale(body);
            Vector3f ringNormal = new Quaternionf()
                    .rotateY((float) Math.toRadians(visual.geometry().poleYaw()))
                    .rotateX((float) Math.toRadians(visual.geometry().polePitch()))
                    .rotateZ((float) Math.toRadians(ring.tilt() + visual.geometry().axialTilt()))
                    .transform(new Vector3f(0.0f, 1.0f, 0.0f));
            setVector(shader, "RingNormal", ringNormal);
            setVector(shader, "RingData", (float) (ring.innerRadius() * scale),
                    (float) (ring.outerRadius() * scale),
                    ring.shadowDensity());
        }
    }

    static Vector3f primarySunDirection(CelestialBodyData body, Level level) {
        SunSource source = primarySun(body, level);
        return source == null ? null : new Vector3f(source.direction());
    }

    static SunSource primarySun(CelestialBodyData body, Level level) {
        if (body == null) return null;
        double timeDays = CelestialObserver.timeDays(level);
        for (CelestialGalaxyCache.ResolvedGalaxy resolved : CelestialGalaxyCache.resolve(timeDays)) {
            GalaxyData galaxy = resolved.galaxy();
            var positions = resolved.positions();
            SpaceVec3 bodyPosition = positions.get(body.id());
            if (bodyPosition == null) continue;
            for (GalaxyData.StarData star : galaxy.stars()) {
                SpaceVec3 starPosition = positions.get(star.id());
                if (starPosition == null) continue;
                Vector3f direction = new Vector3f((float) (starPosition.x() - bodyPosition.x()),
                        (float) (starPosition.y() - bodyPosition.y()),
                        (float) (starPosition.z() - bodyPosition.z()));
                if (direction.lengthSquared() > 1.0e-8f) {
                    return new SunSource(direction.normalize(), StarColor.fromTemperature(star.temperature()));
                }
            }
        }
        return null;
    }

    static Vector3f starColor(SpaceVec3 position) {
        StarRenderData star = ACTIVE_STARS.get(position);
        return star == null ? new Vector3f(1.0f) : StarColor.fromTemperature(star.data().temperature());
    }

    static SpaceVec3 dominantSun(SpaceVec3 receiver, List<SpaceVec3> suns) {
        SpaceVec3 best = null;
        double bestIrradiance = -1.0;
        for (SpaceVec3 sun : suns) {
            double irradiance = irradianceAt(receiver, sun);
            if (irradiance > bestIrradiance) {
                bestIrradiance = irradiance;
                best = sun;
            }
        }
        return best;
    }

    static double irradianceAt(SpaceVec3 receiver, SpaceVec3 star) {
        SpaceVec3 sourceReceiver = SOURCE_POSITIONS.getOrDefault(receiver, receiver);
        SpaceVec3 sourceStar = SOURCE_POSITIONS.getOrDefault(star, star);
        double distance = distance(sourceReceiver, sourceStar);
        return starLuminosity(star) * REFERENCE_SOLAR_DISTANCE * REFERENCE_SOLAR_DISTANCE
                / Math.max(distance * distance, 1.0);
    }

    static double distance(SpaceVec3 first, SpaceVec3 second) {
        double x = second.x() - first.x();
        double y = second.y() - first.y();
        double z = second.z() - first.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    static double starLuminosity(SpaceVec3 position) {
        StarRenderData star = ACTIVE_STARS.get(position);
        return star == null ? 1.0 : Math.max(0.0, star.data().luminosity());
    }

    private static double starRadius(SpaceVec3 position) {
        StarRenderData star = ACTIVE_STARS.get(position);
        return star == null ? 0.0 : Math.max(0.0, star.data().radius() * star.renderScale());
    }

    private static double renderScale(SpaceVec3 position) {
        return RENDER_SCALES.getOrDefault(position, 1.0);
    }

    private static Occulter strongestBlocker(SpaceVec3 receiver, SpaceVec3 sun) {
        if (sun == null) return null;
        SpaceVec3 sunOffset = sun.subtract(receiver);
        SpaceVec3 light = sunOffset.normalize();
        double sunDistance = Math.sqrt(sunOffset.x() * sunOffset.x()
                + sunOffset.y() * sunOffset.y() + sunOffset.z() * sunOffset.z());
        Occulter best = null;
        double bestScore = 0.0;
        for (Occulter candidate : ACTIVE_OCCULTERS) {
            SpaceVec3 offset = candidate.position().subtract(receiver);
            double along = offset.x() * light.x() + offset.y() * light.y() + offset.z() * light.z();
            if (along <= 1.0 || along >= sunDistance) continue;
            double missX = offset.x() - light.x() * along;
            double missY = offset.y() - light.y() * along;
            double missZ = offset.z() - light.z() * along;
            double miss = Math.sqrt(missX * missX + missY * missY + missZ * missZ);
            if (miss > candidate.radius() * 1.15) continue;
            double score = candidate.radius() / Math.max(1.0, miss);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private record StarRenderData(GalaxyData.StarData data, SpaceVec3 sourcePosition, double renderScale) {}
    private record Occulter(SpaceVec3 position, double radius) {}
}
