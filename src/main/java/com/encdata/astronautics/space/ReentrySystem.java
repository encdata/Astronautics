package com.encdata.astronautics.space;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReentrySystem {
    private static final double END_Y = 5_000.0;
    private static final double SPACE_ENTRY_RANGE = 1_000.0;
    private static final double MIN_REENTRY_SPEED = 0.15;
    private static final double MIN_INWARD_SPEED = 0.05;
    private static final double DESCENT_SPEED_THRESHOLD = -0.05;
    private static final int DAMAGE_INTERVAL_TICKS = 100;
    private static final int SURFACE_REFRESH_TICKS = 20;
    private static final int STATE_EXPIRY_TICKS = 30;
    private static final Map<UUID, ShipState> SHIPS = new HashMap<>();

    private record ShipState(ReentryState reentry, long lastSeen,
                             ReentrySurfaceGeometry.CachedFaces surface) {}
    private record NearestAtmosphere(CelestialBodyData body, GalaxyData galaxy,
                                     SpaceVec3 position, double distanceSquared) {}

    private ReentrySystem() {}

    public static Map<UUID, ReentryState> states() {
        Map<UUID, ReentryState> states = new HashMap<>();
        SHIPS.forEach((id, ship) -> states.put(id, ship.reentry()));
        return Map.copyOf(states);
    }

    public static void clearStates() {
        SHIPS.clear();
    }

    public static void expireStates(long tick) {
        Iterator<ShipState> states = SHIPS.values().iterator();
        while (states.hasNext()) {
            if (tick - states.next().lastSeen() > STATE_EXPIRY_TICKS) states.remove();
        }
    }

    private static ReentryState evaluate(Level level, Vec3 position, Vec3 velocity) {
        boolean planetDimension = SpaceCatalog.isPlanetDimension(level.dimension());
        if (planetDimension) {
            double altitude = Math.max(0.0, position.y);
            Vec3 inward = new Vec3(0.0, -1.0, 0.0);
            double inwardSpeed = velocity.dot(inward);
            double density = atmosphericDensity(level.dimension(), altitude);
            boolean active = altitude > END_Y
                    && altitude <= SpaceFlightPhysics.SPACE_ALTITUDE
                    && inwardSpeed > MIN_INWARD_SPEED
                    && velocity.length() > MIN_REENTRY_SPEED
                    && density > 0.0;
            String bodyId = SpaceCatalog.bodyForDimension(level.dimension()).map(body -> body.id().toString()).orElse("");
            return new ReentryState(active, true, position, altitude, 0.0, inwardSpeed,
                    velocity.length(), density, inward, velocity, level.dimension().location().toString(), bodyId, List.of());
        }

        if (!SpaceConstants.isSpace(level)) {
            return ReentryState.inactive(false, position, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, velocity);
        }

        double timeDays = level.getDayTime() / 24000.0;
        NearestAtmosphere nearest = null;
        for (GalaxyData galaxy : SpaceCatalog.galaxies().values()) {
            double scaledTimeDays = timeDays * galaxy.timeScale();
            for (GalaxyData.StarData star : galaxy.stars()) {
                SpaceVec3 starPosition = galaxy.position().add(star.position());
                nearest = findNearestAtmosphere(star.bodies(), starPosition, scaledTimeDays, position,
                        galaxy, nearest);
            }
        }
        if (nearest == null) {
            return ReentryState.inactive(false, position, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, velocity);
        }

        CelestialBodyData nearestBody = nearest.body();
        double nearestDistance = Math.sqrt(nearest.distanceSquared());
        Vec3 inward = new Vec3(nearest.position().x() - position.x,
                nearest.position().y() - position.y, nearest.position().z() - position.z);
        if (inward.lengthSqr() < 1.0e-10) {
            return ReentryState.inactive(false, position, 0.0, 0.0, velocity);
        }
        inward = inward.normalize();
        double dt = 1.0 / 24000.0;
        SpaceVec3 before = CelestialPositionResolver.resolveBody(nearestBody.id(), nearest.galaxy(), timeDays - dt);
        SpaceVec3 after = CelestialPositionResolver.resolveBody(nearestBody.id(), nearest.galaxy(), timeDays + dt);
        Vec3 planetVelocity = new Vec3((after.x() - before.x()) / 2.0,
                (after.y() - before.y()) / 2.0, (after.z() - before.z()) / 2.0);
        Vec3 relativeVelocity = velocity.subtract(planetVelocity);
        double altitude = Math.max(0.0, nearestDistance - nearestBody.radius());
        double atmosphereHeight = Math.max(1.0, nearestBody.visual().atmosphere().height());
        double distanceToAtmosphere = nearestDistance - nearestBody.radius() - atmosphereHeight;
        double inwardSpeed = relativeVelocity.dot(inward);
        double density = atmosphericDensity(nearestBody, altitude);
        boolean active = distanceToAtmosphere <= SPACE_ENTRY_RANGE
                && inwardSpeed > MIN_INWARD_SPEED
                && relativeVelocity.length() > MIN_REENTRY_SPEED
                && density > 0.0;
        return new ReentryState(active, false, position, altitude, distanceToAtmosphere,
                inwardSpeed, relativeVelocity.length(), density, inward, relativeVelocity,
                level.dimension().location().toString(), nearestBody.id().toString(), List.of());
    }

    private static double atmosphericDensity(ResourceKey<Level> dimension, double altitude) {
        return SpaceCatalog.bodyForDimension(dimension)
                .map(body -> atmosphericDensity(body, altitude)).orElse(0.0);
    }

    private static double atmosphericDensity(CelestialBodyData body, double altitude) {
        double pressure = Math.max(0.0, body.environment().pressure()) / 101.325;
        double scaleHeight = Math.max(250.0, body.visual().atmosphere().height() * 0.18);
        return pressure * Math.exp(-Math.max(0.0, altitude) / scaleHeight);
    }

    private static NearestAtmosphere findNearestAtmosphere(
            Iterable<CelestialBodyData> bodies,
            SpaceVec3 parentPosition,
            double timeDays,
            Vec3 observer,
            GalaxyData galaxy,
            NearestAtmosphere nearest
    ) {
        for (CelestialBodyData body : bodies) {
            SpaceVec3 bodyPosition = parentPosition.add(body.positionAt(timeDays));
            if (body.visual().atmosphere().enabled()) {
                double dx = bodyPosition.x() - observer.x;
                double dy = bodyPosition.y() - observer.y;
                double dz = bodyPosition.z() - observer.z;
                double distanceSquared = dx * dx + dy * dy + dz * dz;
                if (nearest == null || distanceSquared < nearest.distanceSquared()) {
                    nearest = new NearestAtmosphere(body, galaxy, bodyPosition, distanceSquared);
                }
            }
            nearest = findNearestAtmosphere(body.bodies(), bodyPosition, timeDays, observer, galaxy, nearest);
        }
        return nearest;
    }

    static void damageIfDescending(ServerSubLevel ship, RigidBodyHandle handle) {
        ServerLevel level = ship.getLevel();
        boolean planet = level != null && SpaceCatalog.isPlanetDimension(level.dimension());
        if (!handle.isValid() || level == null
                || (!planet && !SpaceConstants.isSpace(level))) {
            return;
        }
        Vector3d p = ship.logicalPose().position();
        Vector3d v = handle.getLinearVelocity(new Vector3d());
        Vec3 worldPosition = new Vec3(p.x, p.y, p.z);
        Vec3 evaluatedPosition = worldPosition;
        if (!planet) {
            SpaceVec3 universe = SpaceProjection.shipPosition(ship).originFrame();
            if (!Double.isFinite(universe.x()) || !Double.isFinite(universe.y()) || !Double.isFinite(universe.z())) return;
            evaluatedPosition = new Vec3(universe.x(), universe.y(), universe.z());
        }
        Vec3 worldVelocity = new Vec3(v.x, v.y, v.z);
        ReentryState state = evaluate(level, evaluatedPosition, worldVelocity).withPosition(worldPosition);
        UUID id = ship.getUniqueId();
        ShipState previous = SHIPS.get(id);
        ReentrySurfaceGeometry.CachedFaces geometry = previous == null ? null : previous.surface();
        long tick = level.getServer().getTickCount();
        if (state.active()) {
            if (geometry == null || tick - geometry.tick() >= SURFACE_REFRESH_TICKS) {
                geometry = ReentrySurfaceGeometry.build(ship, tick);
            }
            state = state.withSurfaceSamples(
                    ReentrySurfaceGeometry.windwardSamples(ship, geometry.faces(), state.velocity()));
        }
        SHIPS.put(id, new ShipState(state, tick, geometry));

        if (!planet
                || p.y <= END_Y
                || v.y >= DESCENT_SPEED_THRESHOLD
                || level.getGameTime() % DAMAGE_INTERVAL_TICKS != 0) {
            return;
        }

        Vec3 relativeVelocity = state.velocity();
        Direction leadingFace = ReentrySurfaceGeometry.leadingFace(ship,
                new Vector3d(relativeVelocity.x, relativeVelocity.y, relativeVelocity.z));

        List<BlockPos> unprotected = new ArrayList<>();
        for (BlockPos pos : ReentrySurfaceGeometry.leadingSurface(ship, leadingFace)) {
            if (!ReentryHeatShieldPolicy.survives(level.getBlockState(pos))) unprotected.add(pos);
        }
        if (!unprotected.isEmpty()) {
            ReentryHeatShieldPolicy.destroyIfUnshielded(level,
                    unprotected.get(level.getRandom().nextInt(unprotected.size())));
        }
    }
}
