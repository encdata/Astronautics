package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.CelestialMeshCache.SphereVertex;
import com.encdata.astronautics.space.SpaceVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

final class CelestialFeatureRenderer {
    private static final double WORLD_SCALE = 1.0;

    private CelestialFeatureRenderer() {}

    static void renderRings(PoseStack poseStack, MultiBufferSource bufferSource, SpaceVec3 position,
                            double radius, com.encdata.astronautics.space.CelestialVisualData visual,
                            SpaceVec3 observerSpace, List<SpaceVec3> suns, double timeDays,
                            boolean frontHalf, double renderScale) {
        if (position == null) return;
        for (var ring : visual.geometry().rings()) {
            RenderType type = VeilRenderType.get(ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID,
                    frontHalf ? "celestial_ring_front" : "celestial_ring_back"), ring.texture());
            if (type == null) continue;
            VertexConsumer builder = bufferSource.getBuffer(type);
            poseStack.pushPose();
            SpaceVec3 relative = position.subtract(observerSpace);
            poseStack.translate(relative.x(), relative.y(), relative.z());
            poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.toRadians(visual.geometry().poleYaw())));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotation((float) Math.toRadians(visual.geometry().polePitch())));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.toRadians(ring.tilt() + visual.geometry().axialTilt())));
            float rotation = 0.0f;
            if (ring.rotationPeriod() > 0.0) {
                rotation = (float) ((timeDays / ring.rotationPeriod()) * Math.PI * 2.0);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotation(rotation));
            }
            Quaternionf orientation = new Quaternionf()
                    .rotateY((float) Math.toRadians(visual.geometry().poleYaw()))
                    .rotateX((float) Math.toRadians(visual.geometry().polePitch()))
                    .rotateZ((float) Math.toRadians(ring.tilt() + visual.geometry().axialTilt()))
                    .rotateY(rotation);
            SpaceVec3 sun = CelestialLighting.dominantSun(position, suns);
            Vector3f sunDirection = sun == null ? new Vector3f(0.35f, 0.8f, 0.45f)
                    : new Vector3f((float) (sun.x() - position.x()), (float) (sun.y() - position.y()),
                    (float) (sun.z() - position.z())).normalize();
            float inner = (float) (ring.innerRadius() * renderScale);
            float outer = (float) (ring.outerRadius() * renderScale);
            int alpha = Math.clamp(Math.round(ring.opacity() * 255.0f), 0, 255);
            double distance = Math.sqrt(relative.x() * relative.x()
                    + relative.y() * relative.y() + relative.z() * relative.z());
            float projectedRadius = distance < 1.0e-6 ? Float.MAX_VALUE
                    : CelestialBodyRenderer.projectedRadiusPixels(outer, distance);
            int segments = projectedRadius > 48.0f ? 384 : projectedRadius > 6.7f ? 256
                    : projectedRadius > 2.5f ? 192 : projectedRadius > 0.67f ? 128 : 64;
            for (int segment = 0; segment < segments; segment++) {
                double a0 = segment * Math.PI * 2.0 / segments;
                double a1 = (segment + 1) * Math.PI * 2.0 / segments;
                double middleAngle = (a0 + a1) * 0.5;
                double middleRadius = (inner + outer) * 0.5;
                Vector3f middle = orientation.transform(new Vector3f(
                        (float) (Math.cos(middleAngle) * middleRadius), 0.0f,
                        (float) (Math.sin(middleAngle) * middleRadius)));
                boolean segmentIsFront = middle.x * (float) -relative.x()
                        + middle.y * (float) -relative.y()
                        + middle.z * (float) -relative.z() > 0.0f;
                if (segmentIsFront != frontHalf) continue;
                float v0 = segment / (float) segments;
                float v1 = (segment + 1) / (float) segments;
                ringVertex(builder, poseStack.last(), Math.cos(a0) * inner, Math.sin(a0) * inner,
                        0.0f, v0, alpha, orientation);
                ringVertex(builder, poseStack.last(), Math.cos(a0) * outer, Math.sin(a0) * outer,
                        1.0f, v0, alpha, orientation);
                ringVertex(builder, poseStack.last(), Math.cos(a1) * outer, Math.sin(a1) * outer,
                        1.0f, v1, alpha, orientation);
                ringVertex(builder, poseStack.last(), Math.cos(a1) * inner, Math.sin(a1) * inner,
                        0.0f, v1, alpha, orientation);
                ringVertex(builder, poseStack.last(), Math.cos(a1) * inner, Math.sin(a1) * inner,
                        0.0f, v1, alpha, orientation);
                ringVertex(builder, poseStack.last(), Math.cos(a1) * outer, Math.sin(a1) * outer,
                        1.0f, v1, alpha, orientation);
                ringVertex(builder, poseStack.last(), Math.cos(a0) * outer, Math.sin(a0) * outer,
                        1.0f, v0, alpha, orientation);
                ringVertex(builder, poseStack.last(), Math.cos(a0) * inner, Math.sin(a0) * inner,
                        0.0f, v0, alpha, orientation);
            }
            poseStack.popPose();
            if (bufferSource instanceof MultiBufferSource.BufferSource source) {
                ShaderProgram shader = VeilRenderSystem.setShader(ResourceLocation.fromNamespaceAndPath(
                        Astronautics.MOD_ID, "celestial_ring"));
                if (shader != null) {
                    CelestialShaderUniforms.setVector(shader, "SunDirection", sunDirection);
                    CelestialShaderUniforms.setVector(shader, "BodyRenderPosition", (float) relative.x(),
                            (float) relative.y(), (float) relative.z());
                    CelestialShaderUniforms.setFloat(shader, "BodyRadius", (float) radius);
                    CelestialShaderUniforms.setFloat(shader, "RingTransmission", ring.transmission());
                }
                source.endBatch(type);
            }
        }
    }

    private static void ringVertex(VertexConsumer builder, PoseStack.Pose pose, double x, double z,
                                   float u, float v, int alpha, Quaternionf orientation) {
        Vector3f normal = orientation.transform(new Vector3f(0.0f, 1.0f, 0.0f));
        builder.addVertex(pose, (float) x, 0.0f, (float) z).setUv(u, v)
                .setColor(255, 255, 255, alpha).setNormal(normal.x, normal.y, normal.z);
    }

    static void renderCloudLayers(PoseStack poseStack, MultiBufferSource bufferSource, SpaceVec3 position,
                                  double radius, com.encdata.astronautics.space.CelestialVisualData visual,
                                  SpaceVec3 observerSpace, List<SpaceVec3> suns, double timeDays,
                                  double renderScale) {
        if (position == null) return;
        for (var layer : visual.layers()) {
            if (!"clouds".equalsIgnoreCase(layer.type())) continue;
            SpaceVec3 relative = position.subtract(observerSpace);
            RenderType type = VeilRenderType.get(ResourceLocation.fromNamespaceAndPath(
                    Astronautics.MOD_ID, "celestial_cloud"), layer.texture());
            if (type == null) continue;
            VertexConsumer builder = bufferSource.getBuffer(type);
            float shellRadius = (float) Math.max(1.0e-6, radius + layer.radiusOffset() * renderScale);
            poseStack.pushPose();
            poseStack.translate(relative.x() * WORLD_SCALE, relative.y() * WORLD_SCALE, relative.z() * WORLD_SCALE);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.toRadians(visual.geometry().poleYaw())));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotation((float) Math.toRadians(visual.geometry().polePitch())));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation((float) Math.toRadians(visual.geometry().axialTilt())));
            if (visual.rotationPeriod() > 0.0) poseStack.mulPose(com.mojang.math.Axis.YP.rotation(visual.rotationAt(timeDays)));
            if (layer.rotationPeriod() > 0.0) {
                poseStack.mulPose(com.mojang.math.Axis.YP.rotation(
                        (float) ((timeDays / layer.rotationPeriod()) * Math.PI * 2.0)));
            }
            drawCloudShell(builder, poseStack, shellRadius, layer.opacity(), timeDays * layer.uSpeed(),
                    timeDays * layer.vSpeed(), visual);
            poseStack.popPose();
            if (bufferSource instanceof MultiBufferSource.BufferSource source) {
                ShaderProgram shader = VeilRenderSystem.setShader(ResourceLocation.fromNamespaceAndPath(
                        Astronautics.MOD_ID, "celestial_cloud"));
                if (shader == null) throw new IllegalStateException("Missing celestial cloud shader");
                SpaceVec3 sun = CelestialLighting.dominantSun(position, suns);
                Vector3f direction = sun == null ? new Vector3f(0.0f, 1.0f, 0.0f)
                        : new Vector3f((float) (sun.x() - position.x()), (float) (sun.y() - position.y()),
                        (float) (sun.z() - position.z())).normalize();
                float irradiance = sun == null ? 0.0f
                        : (float) CelestialLighting.irradianceAt(position, sun);
                CelestialShaderUniforms.setVector(shader, "SunDirection", direction);
                CelestialShaderUniforms.setFloat(shader, "SunIrradiance", irradiance);
                source.endBatch(type);
            }
        }
    }

    private static void drawCloudShell(VertexConsumer builder, PoseStack poseStack, float radius, float opacity,
                                       double uOffset, double vOffset,
                                       com.encdata.astronautics.space.CelestialVisualData visual) {
        int alpha = Math.clamp(Math.round(opacity * 255.0f), 0, 255);
        for (SphereVertex vertex : CelestialMeshCache.sphereMesh(32, 16)) {
            float u = vertex.u() + (float) uOffset;
            float textureU = visual.flipHorizontal() ? 1.0f - u : u;
            builder.addVertex(poseStack.last(), vertex.x() * radius, vertex.y() * radius,
                            vertex.z() * radius)
                    .setUv(textureU, vertex.v() + (float) vOffset)
                    .setColor(255, 255, 255, alpha)
                    .setNormal(vertex.x(), vertex.y(), vertex.z());
        }
    }
}
