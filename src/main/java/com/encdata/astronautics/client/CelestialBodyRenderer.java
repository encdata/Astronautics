package com.encdata.astronautics.client;

import com.encdata.astronautics.client.CelestialGalaxyCache.ResolvedGalaxy;
import com.encdata.astronautics.space.CelestialBodyData;
import com.encdata.astronautics.space.CelestialVisualData;
import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import com.encdata.astronautics.space.SpaceGridAddress;
import com.encdata.astronautics.space.SpaceVec3;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

final class CelestialBodyRenderer {
    static final Matrix4f CLIP_MATRIX = new Matrix4f();
    static final Matrix4f VIEW_MATRIX = new Matrix4f();
    static volatile float PIXELS_PER_RADIAN = 360.0f;

    private CelestialBodyRenderer() {}

    static void render(PoseStack poseStack, MultiBufferSource bufferSource, Entity observer, Camera camera,
                       boolean glowOnly, Quaternionf skyOrientation, float timeSeconds) {
        SpaceVec3 observerSpace = CelestialObserver.spacePosition(observer, camera, skyOrientation);
        double timeDays = CelestialObserver.timeDays(observer.level());
        List<ResolvedGalaxy> resolvedGalaxies = CelestialGalaxyCache.resolve(timeDays);
        if (SpaceConstants.isSpace(observer.level())) {
            SpaceGridAddress grid = SpaceGridClientState.received()
                    ? SpaceGridClientState.address() : SpaceGridAddress.ORIGIN;
            resolvedGalaxies = CelestialGalaxyCache.projectForObserver(resolvedGalaxies, grid,
                    observerSpace, observerSpace);
        }
        List<SpaceVec3> suns = sunPositions(resolvedGalaxies);
        CelestialLighting.clearFrame();
        for (ResolvedGalaxy resolved : resolvedGalaxies) {
            GalaxyData galaxy = resolved.galaxy();
            var positions = resolved.positions();
            for (GalaxyData.StarData star : galaxy.stars()) {
                SpaceVec3 starPosition = positions.get(star.id());
                if (starPosition != null) {
                    CelestialLighting.registerStar(starPosition, resolved.sourcePositions().get(star.id()),
                            star, resolved.renderScale(star.id()));
                }
                CelestialLighting.collectOcculters(star.bodies(), positions, resolved.sourcePositions(),
                        resolved.renderScales(), CelestialBodyRenderer::isObserverBody);
            }
        }
        if (!glowOnly) {
            renderSorted(poseStack, bufferSource, resolvedGalaxies, observerSpace, suns, timeDays,
                    observer, camera, skyOrientation, timeSeconds);
            return;
        }

        boolean denseLocalAtmosphere = hasDenseLocalAtmosphere(observer);
        for (ResolvedGalaxy resolved : resolvedGalaxies) {
            GalaxyData galaxy = resolved.galaxy();
            var positions = resolved.positions();
            for (GalaxyData.StarData star : galaxy.stars()) {
                if (!denseLocalAtmosphere) {
                    if (star.visual().glow()) {
                        CelestialSunRenderer.render(poseStack, bufferSource, star, positions.get(star.id()),
                                observerSpace, true, null, 0.0f, new Vector3f(1.0f),
                                resolved.renderScale(star.id()));
                    }
                }
                renderGlowBodies(poseStack, bufferSource, star.bodies(), positions, resolved.renderScales(),
                        observerSpace, suns, timeDays);
            }
        }
    }

    private static void renderSorted(PoseStack poseStack, MultiBufferSource bufferSource,
                                     List<ResolvedGalaxy> resolvedGalaxies, SpaceVec3 observerSpace,
                                     List<SpaceVec3> suns, double timeDays,
                                     Entity observer, Camera camera, Quaternionf skyOrientation, float timeSeconds) {
        SpaceVec3 observerUniverse = CelestialObserver.universePosition(observer);
        Vector3f cameraForward = new Vector3f((float) camera.getLookVector().x,
                (float) camera.getLookVector().y, (float) camera.getLookVector().z);
        new Quaternionf(skyOrientation).conjugate().transform(cameraForward).normalize();
        List<SortedDraw> draws = new ArrayList<>();
        for (ResolvedGalaxy resolved : resolvedGalaxies) {
            GalaxyData galaxy = resolved.galaxy();
            var positions = resolved.positions();
            for (GalaxyData.StarData star : galaxy.stars()) {
                SpaceVec3 starPosition = positions.get(star.id());
                if (starPosition != null) {
                    SpaceVec3 sourcePosition = resolved.sourcePositions().get(star.id());
                    double starScale = resolved.renderScale(star.id());
                    draws.add(new SortedDraw(distance(sourcePosition, observerUniverse), () -> {
                        renderAtmosphereBody(poseStack, bufferSource, starPosition, star.radius(),
                                star.visual(), observerSpace, suns, starScale);
                        flushCelestialBuffers(bufferSource);
                        renderStar(poseStack, bufferSource, star, starPosition, observerSpace,
                                observer, skyOrientation, cameraForward, timeSeconds, starScale);
                    }));
                }
                forEachCelestialBody(star.bodies(), body -> {
                    if (isObserverBody(body)) return;
                    SpaceVec3 position = positions.get(body.id());
                    if (position == null) return;
                    SpaceVec3 sourcePosition = resolved.sourcePositions().get(body.id());
                    double bodyScale = resolved.renderScales().getOrDefault(body.id(), 1.0);
                    draws.add(new SortedDraw(distance(sourcePosition, observerUniverse), () -> {
                        renderBody(poseStack, bufferSource, position, body.radius(), body.visual(),
                                observerSpace, suns, timeDays, false, bodyScale);
                        flushCelestialBuffers(bufferSource);
                        renderAtmosphereBody(poseStack, bufferSource, position, body.radius(), body.visual(),
                                observerSpace, suns, bodyScale);
                    }));
                });
            }
        }
        draws.sort(Comparator.comparingDouble(SortedDraw::distance).reversed());
        for (SortedDraw draw : draws) {
            clearDepth();
            draw.render().run();
            flushCelestialBuffers(bufferSource);
        }
    }

    private record SortedDraw(double distance, Runnable render) {}

    private static double distance(SpaceVec3 position, SpaceVec3 observerSpace) {
        if (position == null) return Double.NEGATIVE_INFINITY;
        return Math.hypot(Math.hypot(position.x() - observerSpace.x(),
                        position.y() - observerSpace.y()),
                position.z() - observerSpace.z());
    }

    private static void clearDepth() {
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static void renderStar(PoseStack poseStack, MultiBufferSource bufferSource, GalaxyData.StarData star,
                                   SpaceVec3 position, SpaceVec3 observerSpace, Entity observer,
                                   Quaternionf skyOrientation, Vector3f cameraForward, float timeSeconds,
                                   double scale) {
        Vector3f direction = new Vector3f((float) (position.x() - observerSpace.x()),
                (float) (position.y() - observerSpace.y()),
                (float) (position.z() - observerSpace.z()));
        Vector3f transmission = new Vector3f(1.0f);
        if (direction.lengthSquared() > 1.0e-8f) {
            skyOrientation.transform(direction).normalize();
            transmission = AtmosphericLightTransmission.transmittance(observer, direction);
        }
        CelestialSunRenderer.render(poseStack, bufferSource, star, position,
                observerSpace, false, cameraForward, timeSeconds, transmission, scale);
    }

    private static List<SpaceVec3> sunPositions(List<ResolvedGalaxy> galaxies) {
        return galaxies.stream().flatMap(resolved -> resolved.galaxy().stars().stream()
                        .map(star -> resolved.positions().get(star.id())))
                .filter(java.util.Objects::nonNull).toList();
    }

    private static void flushCelestialBuffers(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }

    private static boolean hasDenseLocalAtmosphere(Entity observer) {
        return SpaceCatalog.bodyForDimension(observer.level().dimension())
                .map(body -> body.visual().atmosphere().density() >= 0.30f
                        && AtmosphereClientEvents.localAtmosphereStrength(observer) >= 0.80f)
                .orElse(false);
    }

    private static void renderGlowBodies(PoseStack poseStack, MultiBufferSource bufferSource,
                                         Iterable<CelestialBodyData> bodies,
                                         Map<ResourceLocation, SpaceVec3> positions,
                                         Map<ResourceLocation, Double> scales,
                                         SpaceVec3 observerSpace, List<SpaceVec3> suns, double timeDays) {
        forEachCelestialBody(bodies, body -> {
            if (!isObserverBody(body)) {
                renderGlowBody(poseStack, bufferSource, positions.get(body.id()), body.radius(), body.visual(),
                        observerSpace, suns, timeDays, scales.getOrDefault(body.id(), 1.0));
            }
        });
    }

    private static void renderGlowBody(PoseStack poseStack, MultiBufferSource bufferSource, SpaceVec3 position,
                                       double radius, CelestialVisualData visual,
                                       SpaceVec3 observerSpace, List<SpaceVec3> suns, double timeDays,
                                       double scale) {
        radius *= scale;
        if (!isPlanetVisible(position, radius, visual, observerSpace)) return;
        if (visual.glow()) {
            CelestialSurfaceRenderer.renderBodyPass(poseStack, bufferSource, position, radius, visual, observerSpace, suns, timeDays,
                    true, true, false);
        }
    }

    private static void forEachCelestialBody(
            Iterable<CelestialBodyData> bodies, Consumer<CelestialBodyData> action) {
        for (var body : bodies) {
            action.accept(body);
            forEachCelestialBody(body.bodies(), action);
        }
    }

    private static void renderAtmosphereBody(PoseStack poseStack, MultiBufferSource bufferSource,
                                             SpaceVec3 position, double radius,
                                             CelestialVisualData visual,
                                             SpaceVec3 observerSpace, List<SpaceVec3> suns, double scale) {
        radius *= scale;
        if (!visual.atmosphere().enabled()
                || !isPlanetVisible(position, radius, visual, observerSpace)) return;
        CelestialAtmosphereRenderer.render(poseStack, bufferSource, position, radius, visual,
                observerSpace, suns, scale);
    }

    private static void renderBody(PoseStack poseStack, MultiBufferSource bufferSource, SpaceVec3 position, double radius,
                                   CelestialVisualData visual,
                                   SpaceVec3 observerSpace,
                                   List<SpaceVec3> suns, double timeDays, boolean emissive, double scale) {
        radius *= scale;
        if (!isPlanetVisible(position, radius, visual, observerSpace)) return;
        CelestialFeatureRenderer.renderRings(poseStack, bufferSource, position, radius, visual,
                observerSpace, suns, timeDays, false, scale);
        CelestialSurfaceRenderer.renderBodyPass(poseStack, bufferSource, position, radius, visual,
                observerSpace, suns, timeDays, emissive, false, false);
        if (!emissive && visual.emissiveTexture().isPresent()) {
            CelestialSurfaceRenderer.renderBodyPass(poseStack, bufferSource, position, radius, visual,
                    observerSpace, suns, timeDays, true, false, true);
        }
        CelestialFeatureRenderer.renderCloudLayers(poseStack, bufferSource, position, radius, visual,
                observerSpace, suns, timeDays, scale);
        CelestialFeatureRenderer.renderRings(poseStack, bufferSource, position, radius, visual,
                observerSpace, suns, timeDays, true, scale);
    }

    private static boolean isPlanetVisible(SpaceVec3 position, double radius,
                                           CelestialVisualData visual,
                                           SpaceVec3 observerSpace) {
        if (position == null) return false;

        if (celestialSkyActive()) return true;
        double atmosphereRadius = visual.atmosphere().enabled()
                ? visual.atmosphere().height() + visual.atmosphere().radiusOffset()
                : 0.0;
        double bound = Math.max(1.0e-6, radius + atmosphereRadius);
        SpaceVec3 relative = position.subtract(observerSpace);

        double distanceSquared = relative.x() * relative.x()
                + relative.y() * relative.y() + relative.z() * relative.z();
        if (distanceSquared <= bound * bound) return true;

        float b = (float) bound;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    Vector4f corner = new Vector4f(
                            (float) relative.x() + sx * b,
                            (float) relative.y() + sy * b,
                            (float) relative.z() + sz * b, 1.0f);
                    CLIP_MATRIX.transform(corner);
                    if (corner.w() <= 0.0f) return true;
                    float ndcX = corner.x() / corner.w();
                    float ndcY = corner.y() / corner.w();
                    minX = Math.min(minX, ndcX);
                    maxX = Math.max(maxX, ndcX);
                    minY = Math.min(minY, ndcY);
                    maxY = Math.max(maxY, ndcY);
                }
            }
        }
        return maxX >= -1.0f && minX <= 1.0f
                && maxY >= -1.0f && minY <= 1.0f;
    }

    private static boolean celestialSkyActive() {
        Entity observer = Minecraft.getInstance().getCameraEntity();
        return observer != null && SpaceCatalog.usesCustomSkybox(observer.level().dimension());
    }

    static float projectedRadiusPixels(double radius, double distance) {
        if (distance <= 1.0e-6) return Float.MAX_VALUE;
        return (float) (radius / distance * PIXELS_PER_RADIAN);
    }

    static Vector3f viewCenter(PoseStack poseStack) {
        Vector3f center = poseStack.last().pose().getTranslation(new Vector3f());
        return VIEW_MATRIX.transformPosition(center);
    }

    static boolean isObserverBody(CelestialBodyData body) {
        Entity observer = Minecraft.getInstance().getCameraEntity();
        if (observer == null || body.dimension()
                .filter(observer.level().dimension().location()::equals).isEmpty()) return false;

        return SpaceFlightPhysics.logicalAltitude(observer) < SpaceSkyClient.CELESTIAL_VISIBILITY_START;
    }

}
