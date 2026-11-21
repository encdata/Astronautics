package com.encdata.astronautics.client;

import static com.encdata.astronautics.client.CelestialShaderUniforms.setFloat;
import static com.encdata.astronautics.client.CelestialShaderUniforms.setVector;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.CelestialMeshCache.SphereVertex;
import com.encdata.astronautics.space.AtmosphereData;
import com.encdata.astronautics.space.SpaceCatalog;
import com.encdata.astronautics.space.SpaceFlightPhysics;
import com.encdata.astronautics.space.SpaceVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

final class CelestialAtmosphereRenderer {
    private static final float LOCAL_PLANET_RADIUS = 80_000.0f;
    private static final int LOCAL_DOME_SLICES = 128;
    private static final int LOCAL_DOME_RINGS = 64;
    private CelestialAtmosphereRenderer() {}

    static void render(PoseStack poseStack, MultiBufferSource bufferSource, SpaceVec3 position,
                       double radius, com.encdata.astronautics.space.CelestialVisualData visual,
                       SpaceVec3 observerSpace, List<SpaceVec3> suns, double renderScale) {
        var atmosphere = visual.atmosphere();
        if (!atmosphere.enabled() || position == null) return;
        RenderType type = VeilRenderType.get(ResourceLocation.fromNamespaceAndPath(
                Astronautics.MOD_ID, "celestial_atmosphere"));
        if (type == null) return;
        VertexConsumer builder = bufferSource.getBuffer(type);
        SpaceVec3 relative = position.subtract(observerSpace);
        double distance = Math.sqrt(relative.x() * relative.x()
                + relative.y() * relative.y() + relative.z() * relative.z());
        float shellRadius = (float) Math.max(1.0e-6,
                radius + (atmosphere.radiusOffset() + 10.0) * renderScale);
        if (distance < 1.0e-5 || shellRadius / distance < 0.00002f) return;
        if (CelestialBodyRenderer.projectedRadiusPixels(shellRadius, distance) < 2.0f) return;

        float projectedRadius = CelestialBodyRenderer.projectedRadiusPixels(shellRadius, distance);
        int slices;
        int rings;
        if (projectedRadius > 64.0f) {
            slices = 128;
            rings = 64;
        } else if (projectedRadius > 20.0f) {
            slices = 96;
            rings = 48;
        } else if (projectedRadius > 5.0f) {
            slices = 48;
            rings = 24;
        } else if (projectedRadius > 1.35f) {
            slices = 24;
            rings = 12;
        } else {
            slices = 8;
            rings = 4;
        }

        int alpha = Math.clamp(Math.round(atmosphere.density() * 255.0f), 0, 255);
        poseStack.pushPose();
        poseStack.translate(relative.x(), relative.y(), relative.z());
        int color = atmosphere.color();
        drawShell(builder, poseStack, shellRadius, (float) radius, slices, rings,
                ((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f, alpha, atmosphere.rayleigh(), atmosphere.mie());
        poseStack.popPose();
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            ShaderProgram shader = VeilRenderSystem.setShader(ResourceLocation.fromNamespaceAndPath(
                    Astronautics.MOD_ID, "celestial_atmosphere"));
            if (shader != null) {
                SpaceVec3 sun = CelestialLighting.dominantSun(position, suns);
                Vector3f direction = sun == null ? new Vector3f(0.35f, 0.8f, 0.45f)
                        : new Vector3f((float) (sun.x() - position.x()), (float) (sun.y() - position.y()),
                        (float) (sun.z() - position.z())).normalize();
                poseStack.last().pose().transformDirection(direction).normalize();
                CelestialShaderUniforms.setVector(shader, "SunDirection", direction);
                setVector(shader, "SunColor", CelestialLighting.starColor(sun));
                setScattering(shader, atmosphere);
            }
            source.endBatch(type);
        }
    }

    private static void drawShell(VertexConsumer builder, PoseStack poseStack, float shellRadius,
                                  float parentRadius, int slices, int rings,
                                  float red, float green, float blue, int alpha,
                                  float rayleigh, float mie) {
        SphereVertex[] mesh = CelestialMeshCache.sphereMesh(slices, rings);
        for (SphereVertex vertex : mesh) {
            builder.addVertex(poseStack.last(), vertex.x() * shellRadius,
                            vertex.y() * shellRadius, vertex.z() * shellRadius)
                    .setUv(shellRadius, parentRadius / shellRadius)
                    .setColor(Math.round(red * 255.0f), Math.round(green * 255.0f),
                            Math.round(blue * 255.0f), alpha)
                    .setNormal(poseStack.last(), vertex.x(), vertex.y(), vertex.z());
        }
    }

    private static void setScattering(ShaderProgram shader, AtmosphereData atmosphere) {
        var scattering = atmosphere.scattering();
        CelestialShaderUniforms.setFloat(shader, "PhysicalScattering", scattering.enabled() ? 1.0f : 0.0f);
        CelestialShaderUniforms.setVector(shader, "RayleighCoeff", scattering.rayleighRed(), scattering.rayleighGreen(),
                scattering.rayleighBlue());
        CelestialShaderUniforms.setVector(shader, "ScatteringParams", scattering.mieCoefficient(),
                scattering.rayleighScaleHeight(), scattering.mieScaleHeight(), scattering.mieG());
        CelestialShaderUniforms.setVector(shader, "ScatteringLighting", scattering.sunIntensity(),
                scattering.exposure(), 0.0f);
    }

    static void renderLocalDome(PoseStack poseStack, MultiBufferSource bufferSource,
                                Entity observer, float strength, Quaternionf celestialOrientation) {
        var body = SpaceCatalog.bodyForDimension(observer.level().dimension());
        if (body.isEmpty()) return;
        var atmosphere = body.get().visual().atmosphere();
        if (!atmosphere.enabled()) return;
        CelestialLighting.SunSource sun = CelestialLighting.primarySun(body.get(), observer.level());
        if (sun == null) return;

        RenderType type = VeilRenderType.get(
                ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "local_atmosphere"));
        if (type == null) return;

        float opacity = Math.clamp(atmosphere.density() * strength, 0.0f, 1.0f);
        if (opacity <= 0.001f) return;
        int color = atmosphere.color();
        VertexConsumer builder = bufferSource.getBuffer(type);
        float altitude = (float) Math.clamp(observer.getY() - observer.level().getMinBuildHeight(),
                0.0, SpaceFlightPhysics.SPACE_ALTITUDE);
        float planetRadius = Math.max(LOCAL_PLANET_RADIUS,
                (float) body.get().radius() * 32.0f);
        float localScale = planetRadius / (float) Math.max(body.get().radius(), 1.0);
        float atmosphereThickness = Math.max(1.0f, (float) atmosphere.height() * localScale);
        float atmosphereRadius = planetRadius + atmosphereThickness;
        poseStack.pushPose();

        drawShell(builder, poseStack, 1000.0f, 990.0f,
                LOCAL_DOME_SLICES, LOCAL_DOME_RINGS,
                ((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f,
                Math.round(opacity * 255.0f), atmosphere.rayleigh(), atmosphere.mie());
        poseStack.popPose();
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            ShaderProgram shader = VeilRenderSystem.setShader(
                    ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "atmospheric_sky"));
            if (shader != null) {
                Vector3f atmosphericSun = new Vector3f(sun.direction());
                celestialOrientation.transform(atmosphericSun).normalize();
                poseStack.last().pose().transformDirection(atmosphericSun).normalize();
                Vector3f atmosphericUp = new Vector3f(0.0f, 1.0f, 0.0f);
                poseStack.last().pose().transformDirection(atmosphericUp).normalize();
                setVector(shader, "SunDirection", atmosphericSun);
                setVector(shader, "SunColor", sun.color());
                float altitudeFraction = (float) (altitude / SpaceFlightPhysics.SPACE_ALTITUDE);
                float observerRadius = planetRadius + altitudeFraction * atmosphereThickness;
                setAtmosphericSky(shader, atmosphere,
                        planetRadius, atmosphereRadius, observerRadius);
                setVector(shader, "PlanetUp", atmosphericUp);
            }
            source.endBatch(type);
        }
    }

    private static void setAtmosphericSky(ShaderProgram shader, AtmosphereData atmosphere,
                                          float planetRadius, float atmosphereRadius,
                                          float observerRadius) {
        var scattering = atmosphere.scattering();
        setFloat(shader, "PhysicalScattering", scattering.enabled() ? 1.0f : 0.0f);
        setVector(shader, "AtmosphereTint", colorVector(atmosphere.color()));
        setVector(shader, "MieTint", colorVector(atmosphere.mieColor()));
        setVector(shader, "RayleighCoeff", scattering.rayleighRed(), scattering.rayleighGreen(),
                scattering.rayleighBlue());
        setFloat(shader, "AtmosphereDensity", atmosphere.density());
        setFloat(shader, "RayleighStrength", Math.max(0.0f, atmosphere.rayleigh()));
        setFloat(shader, "MieStrength", Math.max(0.0f, atmosphere.mie()));
        setFloat(shader, "MieAnisotropy", scattering.enabled() ? scattering.mieG() : 0.78f);
        setFloat(shader, "MieCoefficient", scattering.mieCoefficient());
        setFloat(shader, "SunIntensity", scattering.enabled() ? scattering.sunIntensity() : 1.0f);
        setFloat(shader, "Exposure", scattering.enabled() ? scattering.exposure() : 1.0f);
        setFloat(shader, "PlanetRadius", planetRadius);
        setFloat(shader, "AtmosphereRadius", atmosphereRadius);
        setFloat(shader, "ObserverRadius", observerRadius);
        setFloat(shader, "WorldPerKilometre", Math.max(planetRadius
                / (float) AtmosphericLightTransmission.PLANET_RADIUS_KM, 0.001f));
        setFloat(shader, "RayleighScaleHeight", Math.max(scattering.rayleighScaleHeight(), 0.001f));
        setFloat(shader, "MieScaleHeight", Math.max(scattering.mieScaleHeight(), 0.001f));
    }

    private static Vector3f colorVector(int color) {
        return new Vector3f(((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f);
    }
}
