package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.sky.BackgroundStarRenderer;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CelestialRenderEvents {
    private static final ResourceLocation[] CELESTIAL_SHADERS = {
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "starfield"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "galactic_background"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "celestial"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "celestial_material"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "celestial_atmosphere"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "celestial_sun_volume"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "celestial_sun_flare"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "atmospheric_sky"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "celestial_cloud"),
            ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "celestial_ring"),
    };
    private static final CameraMatrices SAVED_CAMERA = new CameraMatrices();
    private static Level warmedShaderLevel;
    private static int warmedShaderIndex;

    private CelestialRenderEvents() {}

    static boolean levelChanged(Level level) {
        boolean changed = warmedShaderLevel != null && warmedShaderLevel != level;
        if (warmedShaderLevel != level) {
            warmedShaderLevel = level;
        }
        return changed;
    }

    static void warmShader() {
        if (warmedShaderIndex >= CELESTIAL_SHADERS.length) return;
        ShaderProgram shader = VeilRenderSystem.renderer().getShaderManager()
                .getShader(CELESTIAL_SHADERS[warmedShaderIndex]);
        if (shader == null) return;
        try {
            shader.bind();
        } finally {
            ShaderProgram.unbind();
        }
        warmedShaderIndex++;
    }

    public static void registerVeilHooks() {
        VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(CelestialRenderEvents::renderVeilStage);
    }

    private static void renderVeilStage(VeilRenderLevelStageEvent.Stage stage,
                                        LevelRenderer levelRenderer,
                                        MultiBufferSource.BufferSource bufferSource,
                                        MatrixStack veilStack,
                                        Matrix4fc frustumMatrix,
                                        Matrix4fc projectionMatrix,
                                        int renderTick,
                                        DeltaTracker deltaTracker,
                                        Camera camera,
                                        Frustum frustum) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_SKY
                && stage != VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES
                && stage != VeilRenderLevelStageEvent.Stage.AFTER_PARTICLES
                && stage != VeilRenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        Entity entity = camera.getEntity();
        if (entity == null) return;
        Level level = entity.level();
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            ReentryClientRenderer.renderPlasma(camera, bufferSource);
        }
        if (!level.dimension().equals(Level.OVERWORLD)
                && !SpaceConstants.isSpace(level)
                && !SpaceCatalog.isPlanetDimension(level.dimension())) {
            return;
        }
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
            SpaceClientEvents.updateSpaceShaderLights(level);
        }
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (stage == VeilRenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        double altitude = SpaceConstants.isSpace(level) || usesOrbitalOnlyCustomSky(entity)
                ? SpaceFlightPhysics.SPACE_ALTITUDE
                : SpaceFlightPhysics.logicalAltitude(entity);
        float fade = (float) SpaceFlightPhysics.gravityFade(altitude);
        float localAtmosphere = AtmosphereClientEvents.localAtmosphereStrength(entity);
        if (fade <= 0.0f && localAtmosphere <= 0.0f) return;

        float celestialTimeSeconds = (float) (level.getDayTime()
                + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20.0f;
        renderCelestialBodiesWithFarProjection(camera, entity, frustumMatrix, projectionMatrix,
                stage == VeilRenderLevelStageEvent.Stage.AFTER_LEVEL, localAtmosphere,
                celestialTimeSeconds);
    }

    private static void renderCelestialBodiesWithFarProjection(Camera camera, Entity observer,
                                                                Matrix4fc modelView, Matrix4fc currentProjection,
                                                                boolean glowOnly, float localAtmosphere,
                                                                float timeSeconds) {
        GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
        Matrix4f originalProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        CameraMatrices cameraMatrices = VeilRenderSystem.renderer().getCameraMatrices();
        cameraMatrices.backup(SAVED_CAMERA);
        Matrix4f farCameraProjection = new Matrix4f(cameraMatrices.getProjectionMatrix());
        farCameraProjection.m22(-1.0f);
        farCameraProjection.m32(-0.1f);
        Matrix4f farProjection = new Matrix4f(farCameraProjection).mul(
                new Matrix4f(cameraMatrices.getInverseProjectionMatrix()).mul(currentProjection));
        CelestialBodyRenderer.PIXELS_PER_RADIAN = Math.max(1.0f,
                Math.abs(farProjection.m11()) * Minecraft.getInstance().getWindow().getHeight() * 0.5f);
        CelestialBodyRenderer.CLIP_MATRIX.set(farProjection).mul(modelView);
        CelestialBodyRenderer.VIEW_MATRIX.set(modelView);
        gameRenderer.resetProjectionMatrix(farProjection);

        try {
            try (ByteBufferBuilder builder = new ByteBufferBuilder(256)) {
                MultiBufferSource.BufferSource celestialBuffers = MultiBufferSource.immediate(builder);
                PoseStack celestialPose = new PoseStack();
                Quaternionf skyOrientation = CelestialObserver.skyOrientation(observer);
                celestialPose.mulPose(skyOrientation);
                cameraMatrices.update(farProjection, modelView,
                        camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
                if (!glowOnly) {
                    var body = SpaceCatalog.bodyForDimension(observer.level().dimension());
                    float air = body.map(b -> b.visual().atmosphere().enabled()
                            ? b.visual().atmosphere().density() * localAtmosphere : 0.0f).orElse(0.0f);
                    Vector3f sun = body.map(b -> CelestialLighting.primarySunDirection(b, observer.level())).orElse(null);
                    float elevation = -1.0f;
                    if (sun != null) {
                        celestialPose.last().pose().transformDirection(sun);
                        elevation = sun.normalize().y;
                    }
                    BackgroundStarRenderer.render(modelView, farProjection, celestialPose.last().pose(),
                            air, elevation, (float) (observer.level().getDayTime()
                                    + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) / 20.0f);
                }
                CelestialBodyRenderer.render(celestialPose, celestialBuffers, observer, camera, glowOnly,
                        skyOrientation, timeSeconds);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                celestialBuffers.endBatch();
                if (!glowOnly && localAtmosphere > 0.0f) {
                    try (ByteBufferBuilder fogBuilder = new ByteBufferBuilder(256)) {
                        MultiBufferSource.BufferSource fogBuffers = MultiBufferSource.immediate(fogBuilder);
                        CelestialAtmosphereRenderer.renderLocalDome(new PoseStack(), fogBuffers, observer,
                                localAtmosphere, skyOrientation);
                        fogBuffers.endBatch();
                    }
                }
            }
        } finally {
            gameRenderer.resetProjectionMatrix(originalProjection);
            cameraMatrices.restore(SAVED_CAMERA);
        }
    }

    private static boolean usesOrbitalOnlyCustomSky(Entity observer) {
        if (!SpaceCatalog.usesCustomSkybox(observer.level().dimension())) return false;
        return SpaceCatalog.bodyForDimension(observer.level().dimension())
                .map(body -> !body.visual().atmosphere().enabled())
                .orElse(false);
    }
}
