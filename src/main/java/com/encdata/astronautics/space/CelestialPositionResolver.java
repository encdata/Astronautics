package com.encdata.astronautics.space;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class CelestialPositionResolver {
    private CelestialPositionResolver() {}

    public static SpaceVec3 resolveBody(
            ResourceLocation bodyId,
            Iterable<GalaxyData> galaxies,
            double timeDays
    ) {
        for (GalaxyData galaxy : galaxies) {
            SpaceVec3 position = resolveBody(bodyId, galaxy, timeDays);
            if (position != null) return position;
        }
        return null;
    }

    static SpaceVec3 resolveBody(ResourceLocation bodyId, GalaxyData galaxy, double timeDays) {
        double scaledTimeDays = timeDays * galaxy.timeScale();
        for (GalaxyData.StarData star : galaxy.stars()) {
            SpaceVec3 starPosition = galaxy.position().add(star.position());
            if (star.id().equals(bodyId)) return starPosition;

            SpaceVec3 position = findBody(star.bodies(), starPosition, scaledTimeDays, bodyId);
            if (position != null) return position;
        }
        return null;
    }

    private static SpaceVec3 findBody(
            Iterable<CelestialBodyData> bodies,
            SpaceVec3 parentPosition,
            double timeDays,
            ResourceLocation bodyId
    ) {
        for (CelestialBodyData body : bodies) {
            SpaceVec3 position = parentPosition.add(body.positionAt(timeDays));
            if (body.id().equals(bodyId)) return position;

            SpaceVec3 nestedPosition = findBody(body.bodies(), position, timeDays, bodyId);
            if (nestedPosition != null) return nestedPosition;
        }
        return null;
    }

    public static Map<ResourceLocation, SpaceVec3> resolve(GalaxyData galaxy, double timeDays) {
        Map<ResourceLocation, SpaceVec3> positions = new HashMap<>();
        double scaledTimeDays = timeDays * galaxy.timeScale();

        for (GalaxyData.StarData star : galaxy.stars()) {
            SpaceVec3 starPosition = galaxy.position().add(star.position());
            positions.put(star.id(), starPosition);
            resolveBodies(star.bodies(), starPosition, scaledTimeDays, positions);
        }

        return positions;
    }

    static Map<ResourceLocation, SpaceVec3> resolveAll(
            Iterable<GalaxyData> galaxies, double timeDays) {
        Map<ResourceLocation, SpaceVec3> positions = new HashMap<>();
        for (GalaxyData galaxy : galaxies) {
            resolve(galaxy, timeDays).forEach(positions::putIfAbsent);
        }
        return Map.copyOf(positions);
    }

    private static void resolveBodies(
            Iterable<CelestialBodyData> bodies,
            SpaceVec3 parentPosition,
            double timeDays,
            Map<ResourceLocation, SpaceVec3> positions
    ) {
        for (CelestialBodyData body : bodies) {
            SpaceVec3 bodyPosition = parentPosition.add(body.positionAt(timeDays));
            positions.put(body.id(), bodyPosition);
            resolveBodies(body.bodies(), bodyPosition, timeDays, positions);
        }
    }
}
