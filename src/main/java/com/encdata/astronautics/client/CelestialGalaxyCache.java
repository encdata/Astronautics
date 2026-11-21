package com.encdata.astronautics.client;

import com.encdata.astronautics.space.CelestialPositionResolver;
import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceVec3;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpacePosition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CelestialGalaxyCache {
    private static double cachedTimeDays = Double.NaN;
    private static long cachedCatalogRevision = Long.MIN_VALUE;
    private static List<ResolvedGalaxy> cachedGalaxies = List.of();
    private static List<SpaceVec3> cachedSunPositions = List.of();

    private CelestialGalaxyCache() {}

    static List<ResolvedGalaxy> resolve(double timeDays) {
        long catalogRevision = SpaceCatalog.revision();
        if (timeDays == cachedTimeDays && catalogRevision == cachedCatalogRevision) return cachedGalaxies;

        List<ResolvedGalaxy> resolved = new ArrayList<>();
        List<SpaceVec3> suns = new ArrayList<>();
        for (GalaxyData galaxy : SpaceCatalog.galaxies().values()) {
            Map<ResourceLocation, SpaceVec3> positions = CelestialPositionResolver.resolve(galaxy, timeDays);
            resolved.add(new ResolvedGalaxy(galaxy, positions, Map.of(), positions));
            for (GalaxyData.StarData star : galaxy.stars()) {
                SpaceVec3 position = positions.get(star.id());
                if (position != null) suns.add(position);
            }
        }

        cachedTimeDays = timeDays;
        cachedCatalogRevision = catalogRevision;
        cachedGalaxies = List.copyOf(resolved);
        cachedSunPositions = List.copyOf(suns);
        return cachedGalaxies;
    }

    static List<SpaceVec3> sunPositions() {
        return cachedSunPositions;
    }

    static List<ResolvedGalaxy> projectForObserver(List<ResolvedGalaxy> galaxies,
                                                    SpaceGridAddress grid,
                                                    SpaceVec3 observerLocal,
                                                    SpaceVec3 observerRenderPosition) {
        SpacePosition observer = SpacePosition.normalize(grid,
                observerLocal.x(), observerLocal.y(), observerLocal.z());
        List<ResolvedGalaxy> projected = new ArrayList<>(galaxies.size());
        for (ResolvedGalaxy galaxy : galaxies) {
            Map<ResourceLocation, SpaceVec3> positions = new HashMap<>();
            Map<ResourceLocation, Double> scales = new HashMap<>();
            for (Map.Entry<ResourceLocation, SpaceVec3> entry : galaxy.positions().entrySet()) {
                SpaceVec3 absolute = entry.getValue();
                SpacePosition target = SpacePosition.normalize(SpaceGridAddress.ORIGIN,
                        absolute.x(), absolute.y(), absolute.z());
                SpacePosition.RenderOffset offset = target.renderOffsetFrom(observer, 8192.0);
                SpaceVec3 relative = offset.vector();
                positions.put(entry.getKey(), observerRenderPosition.add(relative));
                scales.put(entry.getKey(), offset.scale());
            }
            projected.add(new ResolvedGalaxy(galaxy.galaxy(), positions, scales, galaxy.positions()));
        }
        return List.copyOf(projected);
    }

    static SpaceVec3 bodyPosition(ResourceLocation bodyId, double timeDays) {
        for (ResolvedGalaxy galaxy : resolve(timeDays)) {
            SpaceVec3 position = galaxy.positions().get(bodyId);
            if (position != null) return position;
        }
        return null;
    }

    record ResolvedGalaxy(GalaxyData galaxy, Map<ResourceLocation, SpaceVec3> positions,
                          Map<ResourceLocation, Double> renderScales,
                          Map<ResourceLocation, SpaceVec3> sourcePositions) {
        double renderScale(ResourceLocation id) {
            return renderScales.getOrDefault(id, 1.0);
        }
    }
}
