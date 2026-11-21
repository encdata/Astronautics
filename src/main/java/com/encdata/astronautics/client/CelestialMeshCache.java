package com.encdata.astronautics.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class CelestialMeshCache {
    private static final Map<String, SphereVertex[]> SPHERE_MESHES = new HashMap<>();
    private static final Map<String, PlanetMesh> PLANET_MESHES = new HashMap<>();
    private static final CompletableFuture<PreloadedMeshes> PRELOADED =
            CompletableFuture.supplyAsync(CelestialMeshCache::createPreloadedMeshes);
    private static boolean preloadPublished;

    private CelestialMeshCache() {}

    static PlanetMesh planetMesh(int slices, int rings) {
        return PLANET_MESHES.computeIfAbsent(key(slices, rings), ignored -> createPlanetMesh(slices, rings));
    }

    static SphereVertex[] sphereMesh(int slices, int rings) {
        return SPHERE_MESHES.computeIfAbsent(key(slices, rings), ignored -> createSphereMesh(slices, rings));
    }

    static void preloadTick() {
        if (preloadPublished || !PRELOADED.isDone()) return;
        PreloadedMeshes meshes = PRELOADED.join();
        meshes.spheres().forEach(SPHERE_MESHES::putIfAbsent);
        meshes.planets().forEach(PLANET_MESHES::putIfAbsent);
        preloadPublished = true;
    }

    private static PreloadedMeshes createPreloadedMeshes() {
        Map<String, SphereVertex[]> spheres = new HashMap<>();
        for (MeshSize size : List.of(new MeshSize(128, 64), new MeshSize(96, 48),
                new MeshSize(48, 24), new MeshSize(24, 12), new MeshSize(8, 4),
                new MeshSize(32, 16))) {
            spheres.put(key(size.slices(), size.rings()), createSphereMesh(size.slices(), size.rings()));
        }

        Map<String, PlanetMesh> planets = new HashMap<>();
        for (MeshSize size : List.of(new MeshSize(128, 64), new MeshSize(64, 32),
                new MeshSize(32, 16), new MeshSize(16, 8), new MeshSize(8, 4))) {
            planets.put(key(size.slices(), size.rings()), createPlanetMesh(size.slices(), size.rings()));
        }
        return new PreloadedMeshes(spheres, planets);
    }

    private static String key(int slices, int rings) {
        return slices + "x" + rings;
    }

    private static SphereVertex[] createSphereMesh(int slices, int rings) {
        var vertices = new java.util.ArrayList<SphereVertex>(slices * rings * 4);
        for (int ring = 0; ring < rings; ring++) {
            float v0 = (float) ring / rings;
            float v1 = (float) (ring + 1) / rings;
            double p0 = Math.PI * (v0 - 0.5);
            double p1 = Math.PI * (v1 - 0.5);
            for (int slice = 0; slice < slices; slice++) {
                float u0 = (float) slice / slices;
                float u1 = (float) (slice + 1) / slices;
                double a0 = slice * Math.PI * 2.0 / slices;
                double a1 = (slice + 1) * Math.PI * 2.0 / slices;
                addSphereVertex(vertices, p0, a0, u0, 1.0f - v0);
                addSphereVertex(vertices, p1, a0, u0, 1.0f - v1);
                addSphereVertex(vertices, p1, a1, u1, 1.0f - v1);
                addSphereVertex(vertices, p0, a1, u1, 1.0f - v0);
            }
        }
        return vertices.toArray(SphereVertex[]::new);
    }

    private static void addSphereVertex(List<SphereVertex> vertices, double pitch, double yaw,
                                        float u, float v) {
        vertices.add(new SphereVertex(
                (float) (Math.cos(pitch) * Math.cos(yaw)),
                (float) Math.sin(pitch),
                (float) (Math.cos(pitch) * Math.sin(yaw)), u, v));
    }

    private static PlanetMesh createPlanetMesh(int slices, int rings) {
        PlanetQuad[] quads = new PlanetQuad[slices * rings];
        int index = 0;
        for (int ring = 0; ring < rings; ring++) {
            float v0 = (float) ring / rings;
            float v1 = (float) (ring + 1) / rings;
            double p0 = Math.PI * (v0 - 0.5);
            double p1 = Math.PI * (v1 - 0.5);
            for (int slice = 0; slice < slices; slice++) {
                float u0 = (float) slice / slices;
                float u1 = (float) (slice + 1) / slices;
                double a0 = u0 * Math.PI * 2.0;
                double a1 = u1 * Math.PI * 2.0;
                PlanetVertex first = createPlanetVertex(p0, a0, u0, v0);
                PlanetVertex second = createPlanetVertex(p1, a0, u0, v1);
                PlanetVertex third = createPlanetVertex(p1, a1, u1, v1);
                PlanetVertex fourth = createPlanetVertex(p0, a1, u1, v0);
                double middlePitch = (p0 + p1) * 0.5;
                double middleYaw = (a0 + a1) * 0.5;
                quads[index++] = new PlanetQuad(first, second, third, fourth,
                        (float) (Math.cos(middlePitch) * Math.cos(middleYaw)),
                        (float) Math.sin(middlePitch),
                        (float) (Math.cos(middlePitch) * Math.sin(middleYaw)));
            }
        }
        return new PlanetMesh(quads);
    }

    private static PlanetVertex createPlanetVertex(double pitch, double yaw, float u, float v) {
        return new PlanetVertex((float) (Math.cos(pitch) * Math.cos(yaw)), (float) Math.sin(pitch),
                (float) (Math.cos(pitch) * Math.sin(yaw)), u, v);
    }

    record SphereVertex(float x, float y, float z, float u, float v) {}
    private record MeshSize(int slices, int rings) {}
    private record PreloadedMeshes(Map<String, SphereVertex[]> spheres, Map<String, PlanetMesh> planets) {}
    record PlanetMesh(PlanetQuad[] quads) {}
    record PlanetQuad(PlanetVertex first, PlanetVertex second, PlanetVertex third, PlanetVertex fourth,
                      float centerX, float centerY, float centerZ) {}
    record PlanetVertex(float x, float y, float z, float u, float v) {}
}
