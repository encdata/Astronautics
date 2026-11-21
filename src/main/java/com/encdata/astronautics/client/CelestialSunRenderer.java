package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.space.GalaxyData;
import com.encdata.astronautics.space.SpaceVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class CelestialSunRenderer {
    private static final ResourceLocation SHADER = id("celestial_sun_volume");
    private static final ResourceLocation MAIN_TYPE = id("celestial_sun_volume");
    private static final ResourceLocation BLOOM_TYPE = id("celestial_sun_bloom");
    private static final ResourceLocation FLARE_SHADER = id("celestial_sun_flare");
    private static final ResourceLocation FLARE_TYPE = id("celestial_sun_flare");

    private CelestialSunRenderer() {}

    static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       GalaxyData.StarData star, SpaceVec3 position,
                       SpaceVec3 observerSpace, boolean bloomOnly,
                       Vector3f cameraForward, float timeSeconds, Vector3fc atmosphereTransmission,
                       double renderScale) {
        if (position == null || star.radius() <= 0.0
                || !(bufferSource instanceof MultiBufferSource.BufferSource source)) return;
        SpaceVec3 relative = position.subtract(observerSpace);
        double distance = Math.sqrt(relative.x() * relative.x()
                + relative.y() * relative.y() + relative.z() * relative.z());
        float coreRadius = (float) (star.radius() * renderScale);
        if (distance < 1.0e-5 || CelestialBodyRenderer.projectedRadiusPixels(coreRadius, distance) < 0.2f) return;

        RenderType type = VeilRenderType.get(bloomOnly ? BLOOM_TYPE : MAIN_TYPE);
        if (type == null) return;
        poseStack.pushPose();
        poseStack.translate(relative.x(), relative.y(), relative.z());
        Vector3f centerView = CelestialBodyRenderer.viewCenter(poseStack);
        Vector3f axisX = viewAxis(poseStack, 1.0f, 0.0f, 0.0f);
        Vector3f axisY = viewAxis(poseStack, 0.0f, 1.0f, 0.0f);
        Vector3f axisZ = viewAxis(poseStack, 0.0f, 0.0f, 1.0f);
        Vector3f cameraLocalPos = new Vector3f(-centerView.dot(axisX),
                -centerView.dot(axisY), -centerView.dot(axisZ));
        ShaderProgram shader = VeilRenderSystem.setShader(SHADER);
        if (shader == null) {
            poseStack.popPose();
            return;
        }

        float coronaRadius = coreRadius * 2.0f;
        Vector3f tint = sunTint(star).mul(atmosphereTransmission);
        CelestialShaderUniforms.setVector(shader, "CenterRelative", centerView);
        CelestialShaderUniforms.setVector(shader, "AxisX", axisX);
        CelestialShaderUniforms.setVector(shader, "AxisY", axisY);
        CelestialShaderUniforms.setVector(shader, "AxisZ", axisZ);
        CelestialShaderUniforms.setVector(shader, "CameraLocalPos", cameraLocalPos);
        CelestialShaderUniforms.setVector(shader, "SunTint", tint);
        CelestialShaderUniforms.setFloat(shader, "PlanetRadius", coreRadius);
        CelestialShaderUniforms.setFloat(shader, "BloomRadius", coronaRadius);
        CelestialShaderUniforms.setFloat(shader, "BloomPass", bloomOnly ? 1.0f : 0.0f);

        VertexConsumer builder = bufferSource.getBuffer(type);
        CelestialVolumeMesh.emitCube(builder, poseStack.last(), coreRadius * 1.8f);
        poseStack.popPose();
        source.endBatch(type);
        if (!bloomOnly && star.visual().glow()) {
            renderFlare(poseStack, source, relative, distance, coreRadius * 1.95f,
                    cameraForward, timeSeconds, tint);
        }
    }

    private static Vector3f viewAxis(PoseStack poseStack, float x, float y, float z) {
        Vector3f axis = poseStack.last().pose().transformDirection(new Vector3f(x, y, z)).normalize();
        return CelestialBodyRenderer.VIEW_MATRIX.transformDirection(axis).normalize();
    }

    private static void renderFlare(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                    SpaceVec3 relative, double distance,
                                    float flareRadius, Vector3f cameraForward,
                                    float timeSeconds, Vector3f tint) {
        float distanceAlpha = (float) Math.clamp((distance - flareRadius) / (flareRadius * 4.0), 0.0, 1.0);
        Vector3f toSun = new Vector3f((float) relative.x(), (float) relative.y(), (float) relative.z());
        if (toSun.lengthSquared() < 1.0e-6f || cameraForward == null) return;
        Vector3f direction = new Vector3f(toSun).normalize();
        float viewDot = cameraForward.dot(direction);
        float viewAlpha = (float) Math.clamp((viewDot - 0.15f) / 0.85f, 0.0, 1.0);
        float alpha = distanceAlpha * viewAlpha;
        if (alpha <= 0.001f) return;

        Vector3f worldUp = Math.abs(direction.y) > 0.999f
                ? new Vector3f(0.0f, 0.0f, 1.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
        float flareHalfSize = flareRadius * 2.0f;
        Vector3f right = new Vector3f(worldUp).cross(direction).normalize().mul(flareHalfSize);
        Vector3f up = new Vector3f(direction).cross(new Vector3f(right).normalize()).normalize()
                .mul(flareHalfSize);
        Vector3f center = new Vector3f(toSun);
        Vector3f p1 = new Vector3f(center).sub(right).sub(up);
        Vector3f p2 = new Vector3f(center).add(right).sub(up);
        Vector3f p3 = new Vector3f(center).add(right).add(up);
        Vector3f p4 = new Vector3f(center).sub(right).add(up);

        RenderType type = VeilRenderType.get(FLARE_TYPE);
        if (type == null) return;
        ShaderProgram shader = VeilRenderSystem.setShader(FLARE_SHADER);
        if (shader == null) return;
        CelestialShaderUniforms.setVector(shader, "SunTint", tint);
        CelestialShaderUniforms.setFloat(shader, "Time", timeSeconds);
        CelestialShaderUniforms.setFloat(shader, "FlareAlpha", alpha);

        VertexConsumer builder = buffers.getBuffer(type);
        PoseStack.Pose pose = poseStack.last();
        flareVertex(builder, pose, p1, 0.0f, 1.0f);
        flareVertex(builder, pose, p2, 1.0f, 1.0f);
        flareVertex(builder, pose, p3, 1.0f, 0.0f);
        flareVertex(builder, pose, p4, 0.0f, 0.0f);
        buffers.endBatch(type);
    }

    private static void flareVertex(VertexConsumer builder, PoseStack.Pose pose,
                                    Vector3f point, float u, float v) {
        builder.addVertex(pose, point.x, point.y, point.z)
                .setUv(u, v)
                .setColor(255, 255, 255, 255);
    }

    private static Vector3f sunTint(GalaxyData.StarData star) {
        Vector3f tint = StarColor.fromTemperature(star.temperature());
        float warmth = (float) Math.clamp(5772.0 / Math.max(star.temperature(), 1000.0), 0.35, 1.0);
        tint.z *= 1.0f - 0.2f * warmth;
        return tint;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, path);
    }
}
