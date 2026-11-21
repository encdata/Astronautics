package com.encdata.astronautics.space;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class SpaceCatalog {
    private static volatile CatalogSnapshot catalog = CatalogSnapshot.EMPTY;
    private static volatile long revision;

    private SpaceCatalog() {}

    public static void replace(Map<ResourceLocation, GalaxyData> galaxies) {
        catalog = snapshot(galaxies);
        revision++;
    }

    public static void replaceFromNetwork(Map<ResourceLocation, GalaxyData> galaxies) {
        catalog = snapshot(galaxies);
    }

    private static CatalogSnapshot snapshot(Map<ResourceLocation, GalaxyData> galaxies) {
        Map<ResourceLocation, GalaxyData> galaxyCopy = Map.copyOf(galaxies);
        Map<ResourceLocation, CelestialBodyData> bodiesById = new HashMap<>();
        Map<ResourceLocation, CelestialBodyData> bodiesByDimension = new HashMap<>();
        List<ResourceLocation> bodyIds = new ArrayList<>();
        for (GalaxyData galaxy : new TreeMap<>(galaxyCopy).values()) {
            for (GalaxyData.StarData star : galaxy.stars()) {
                indexBodies(star.bodies(), bodiesById, bodiesByDimension, bodyIds);
            }
        }
        bodyIds.sort(ResourceLocation::compareTo);
        return new CatalogSnapshot(galaxyCopy, Map.copyOf(bodiesById),
                Map.copyOf(bodiesByDimension), List.copyOf(bodyIds));
    }

    public static Map<ResourceLocation, GalaxyData> galaxies() {
        return catalog.galaxies();
    }

    public static long revision() { return revision; }

    public static boolean isPlanetDimension(ResourceKey<Level> dimension) {
        return catalog.bodiesByDimension().containsKey(dimension.location());
    }

    public static Optional<CelestialBodyData> bodyForDimension(ResourceKey<Level> dimension) {
        return Optional.ofNullable(catalog.bodiesByDimension().get(dimension.location()));
    }

    public static Optional<CelestialBodyData> body(ResourceLocation id) {
        return Optional.ofNullable(catalog.bodiesById().get(id));
    }

    public static List<ResourceLocation> bodyIds() {
        return catalog.bodyIds();
    }

    public static PlanetEnvironmentData environmentForDimension(ResourceKey<Level> dimension) {
        CelestialBodyData body = catalog.bodiesByDimension().get(dimension.location());
        return body == null ? PlanetEnvironmentData.EARTH_LIKE : body.environment();
    }

    public static boolean usesCustomSkybox(ResourceKey<Level> dimension) {
        return environmentForDimension(dimension).customSkybox();
    }

    private static void indexBodies(Iterable<CelestialBodyData> bodies,
                                    Map<ResourceLocation, CelestialBodyData> bodiesById,
                                    Map<ResourceLocation, CelestialBodyData> bodiesByDimension,
                                    List<ResourceLocation> bodyIds) {
        for (CelestialBodyData body : bodies) {
            CelestialBodyData previous = bodiesById.putIfAbsent(body.id(), body);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate celestial body id: " + body.id());
            }
            body.dimension().ifPresent(dimension -> {
                CelestialBodyData dimensionPrevious = bodiesByDimension.putIfAbsent(dimension, body);
                if (dimensionPrevious != null) {
                    throw new IllegalArgumentException("Multiple celestial bodies use dimension " + dimension);
                }
            });
            bodyIds.add(body.id());
            indexBodies(body.bodies(), bodiesById, bodiesByDimension, bodyIds);
        }
    }

    private record CatalogSnapshot(Map<ResourceLocation, GalaxyData> galaxies,
                                   Map<ResourceLocation, CelestialBodyData> bodiesById,
                                   Map<ResourceLocation, CelestialBodyData> bodiesByDimension,
                                   List<ResourceLocation> bodyIds) {
        private static final CatalogSnapshot EMPTY = new CatalogSnapshot(
                Map.of(), Map.of(), Map.of(), List.of());
    }
}
