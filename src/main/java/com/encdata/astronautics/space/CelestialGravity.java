package com.encdata.astronautics.space;

import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3dc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CelestialGravity {
    private static final double REFERENCE_EARTH_MASS_KG = 5.97219e24;
    private static final double REFERENCE_RADIUS_BLOCKS = 2_000.0;
    private static final double REFERENCE_GRAVITATIONAL_PARAMETER =
            9.81 * REFERENCE_RADIUS_BLOCKS * REFERENCE_RADIUS_BLOCKS;

    private CelestialGravity() {}

    public static List<GravitySource> sourcesAtTime(double timeDays) {
        List<GravitySource> sources = new ArrayList<>();
        for (GalaxyData galaxy : SpaceCatalog.galaxies().values()) {
            Map<ResourceLocation, SpaceVec3> positions = CelestialPositionResolver.resolve(galaxy, timeDays);
            for (GalaxyData.StarData star : galaxy.stars()) {
                addSource(sources, star.id(), positions.get(star.id()), star.radius(),
                        star.mass(), star.gravityScale());
                addBodies(sources, star.bodies(), positions);
            }
        }
        return List.copyOf(sources);
    }

    public static void addField(SpacePosition position, GravitySource source, Vector3d acceleration,
                                double[] gradient) {
        SpaceVec3 delta = position.deltaWithin(source.position(), Double.MAX_VALUE).orElse(null);
        if (delta == null) return;
        addField(delta.x(), delta.y(), delta.z(), source, acceleration, gradient);
    }

    public static void addField(Vector3d position, GravitySource source, Vector3d acceleration,
                                double[] gradient) {
        SpaceVec3 body = source.absolutePosition();
        addField(body.x() - position.x, body.y() - position.y, body.z() - position.z,
                source, acceleration, gradient);
    }

    private static void addField(double dx, double dy, double dz, GravitySource source, Vector3d acceleration,
                                 double[] gradient) {
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(distanceSquared)) return;

        double radius = Math.max(source.radius(), 1.0);
        double radiusSquared = radius * radius;
        boolean insideBody = distanceSquared <= radiusSquared;
        double distanceCubed = insideBody
                ? radiusSquared * radius
                : distanceSquared * Math.sqrt(distanceSquared);
        double scale = REFERENCE_GRAVITATIONAL_PARAMETER
                * (source.massKg() / REFERENCE_EARTH_MASS_KG)
                * source.gravityScale() / distanceCubed;
        if (!Double.isFinite(scale)) return;

        acceleration.add(dx * scale, dy * scale, dz * scale);
        if (gradient == null) return;
        if (insideBody) {
            gradient[0] -= scale;
            gradient[4] -= scale;
            gradient[8] -= scale;
            return;
        }

        double inverseDistanceSquared = 1.0 / distanceSquared;
        double radialScale = 3.0 * scale * inverseDistanceSquared;
        gradient[0] += radialScale * dx * dx - scale;
        gradient[1] += radialScale * dx * dy;
        gradient[2] += radialScale * dx * dz;
        gradient[3] += radialScale * dy * dx;
        gradient[4] += radialScale * dy * dy - scale;
        gradient[5] += radialScale * dy * dz;
        gradient[6] += radialScale * dz * dx;
        gradient[7] += radialScale * dz * dy;
        gradient[8] += radialScale * dz * dz - scale;
    }

    public static OrbitTarget orbitTarget(ResourceLocation bodyId, Vector3dc shipPosition,
                                          double targetAltitude, double timeDays) {
        if (bodyId == null) return null;
        GravitySource body = null;
        for (GravitySource source : sourcesAtTime(timeDays)) {
            if (source.id().equals(bodyId)) {
                body = source;
                break;
            }
        }
        if (body == null) return null;

        double oneSecondInDays = 1.0 / 1200.0;
        GravitySource earlier = sourceAt(bodyId, timeDays - oneSecondInDays * 0.5);
        GravitySource later = sourceAt(bodyId, timeDays + oneSecondInDays * 0.5);
        Vector3d bodyVelocity = earlier == null || later == null ? new Vector3d()
                : new Vector3d(later.absolutePosition().x() - earlier.absolutePosition().x(),
                later.absolutePosition().y() - earlier.absolutePosition().y(),
                later.absolutePosition().z() - earlier.absolutePosition().z());

        SpaceVec3 bodyPosition = body.absolutePosition();
        Vector3d offset = new Vector3d(bodyPosition.x() - shipPosition.x(),
                bodyPosition.y() - shipPosition.y(), bodyPosition.z() - shipPosition.z());
        double distance = offset.length();
        double orbitRadius = Math.max(body.radius() + targetAltitude, body.radius() + 1.0);
        if (!(distance > 1.0e-6) || !Double.isFinite(distance)) return null;
        Vector3d radial = offset.negate().div(distance);

        return new OrbitTarget(new Vector3d(bodyPosition.x(), bodyPosition.y(), bodyPosition.z()),
                bodyVelocity, radial, distance, orbitRadius);
    }

    public static Vector3d orbitHoldVelocity(OrbitTarget orbit, Vector3dc shipVelocity,
                                             double outwardAcceleration, double inwardAcceleration,
                                             double timeStep) {
        Vector3d relativeVelocity = new Vector3d(shipVelocity).sub(orbit.bodyVelocity());
        double radialSpeed = relativeVelocity.dot(orbit.radial());
        double altitudeError = orbit.radius() - orbit.distance();
        double errorMagnitude = Math.abs(altitudeError);
        double brakingAcceleration = altitudeError >= 0.0 ? inwardAcceleration : outwardAcceleration;
        double brakingSpeed = Math.sqrt(2.0 * Math.max(0.0, brakingAcceleration) * errorMagnitude);
        double desiredRadialSpeed = Math.copySign(Math.min(20.0,
                Math.min(errorMagnitude * 0.25, brakingSpeed)), altitudeError);
        double requestedAcceleration = (desiredRadialSpeed - radialSpeed) / timeStep;
        double radialAcceleration = Math.clamp(requestedAcceleration,
                -Math.max(0.0, inwardAcceleration), Math.max(0.0, outwardAcceleration));
        return new Vector3d(shipVelocity).fma(radialAcceleration * timeStep, orbit.radial());
    }

    private static GravitySource sourceAt(ResourceLocation id, double timeDays) {
        for (GravitySource source : sourcesAtTime(timeDays)) {
            if (source.id().equals(id)) return source;
        }
        return null;
    }

    private static void addBodies(List<GravitySource> sources,
                                  Iterable<CelestialBodyData> bodies,
                                  Map<ResourceLocation, SpaceVec3> positions) {
        for (CelestialBodyData body : bodies) {
            addSource(sources, body.id(), positions.get(body.id()), body.radius(),
                    body.mass(), body.gravityScale());
            addBodies(sources, body.bodies(), positions);
        }
    }

    private static void addSource(List<GravitySource> sources, ResourceLocation id, SpaceVec3 bodyPosition,
                                  double radius, double mass, double gravityScale) {
        if (bodyPosition == null || !Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(gravityScale) || gravityScale <= 0.0) return;
        if (!Double.isFinite(mass) || mass <= 0.0) return;

        SpacePosition position = SpacePosition.normalize(
                SpaceGridAddress.ORIGIN, bodyPosition.x(), bodyPosition.y(), bodyPosition.z());
        sources.add(new GravitySource(id, position, bodyPosition, radius, mass, gravityScale));
    }

    public record GravitySource(ResourceLocation id, SpacePosition position, SpaceVec3 absolutePosition,
                                double radius, double massKg, double gravityScale) {}

    public record OrbitTarget(Vector3d bodyPosition, Vector3d bodyVelocity,
                              Vector3d radial, double distance, double radius) {}
}
