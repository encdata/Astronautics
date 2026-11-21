package com.encdata.astronautics.client;

import com.encdata.astronautics.Astronautics;
import com.encdata.astronautics.client.CelestialMeshCache.PlanetMesh;
import com.encdata.astronautics.client.CelestialMeshCache.PlanetQuad;
import com.encdata.astronautics.client.CelestialMeshCache.PlanetVertex;
import com.encdata.astronautics.space.CelestialVisualData;
import com.encdata.astronautics.space.SpaceVec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

final class CelestialSurfaceRenderer {
    private CelestialSurfaceRenderer() {}

    static void renderBodyPass(PoseStack poseStack, MultiBufferSource bufferSource, SpaceVec3 position,
                               double radius, CelestialVisualData visual, SpaceVec3 observerSpace,
                               List<SpaceVec3> suns, double timeDays, boolean emissive, boolean bloomPass,
                               boolean emissivePass) {
        ResourceLocation texture = emissivePass
                ? visual.emissiveTexture().orElse(null)
                : visual.texture().or(() -> visual.emissiveTexture()).orElse(null);
        ResourceLocation normalTexture = visual.normalTexture().orElse(null);
        ResourceLocation specularTexture = visual.specularTexture().orElse(null);
        if (position == null || texture == null) return;
        SpaceVec3 relative = position.subtract(observerSpace);
        double distance = Math.sqrt(relative.x() * relative.x() + relative.y() * relative.y()
                + relative.z() * relative.z());
        if (distance < 1.0e-6) return;

        boolean materialPass = !bloomPass && !emissivePass && normalTexture != null && specularTexture != null;
        String renderTypeName = bloomPass ? "celestial_glow" : emissivePass ? "celestial_emissive"
                : materialPass ? "celestial_material" : "celestial";
        Object[] renderParams = materialPass
                ? new Object[]{texture, normalTexture, specularTexture}
                : new Object[]{texture};
        RenderType celestialType = VeilRenderType.get(
                ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, renderTypeName), renderParams);
        if (celestialType == null) return;
        VertexConsumer builder = bufferSource.getBuffer(celestialType);

        double angularSize = radius / distance;
        float projectedRadius = CelestialBodyRenderer.projectedRadiusPixels(radius, distance);
        int slices;
        int rings;
        if (projectedRadius > 6.7f) {
            slices = 128;
            rings = 64;
        } else if (projectedRadius > 2.5f) {
            slices = 64;
            rings = 32;
        } else if (projectedRadius > 0.67f) {
            slices = 32;
            rings = 16;
        } else if (projectedRadius > 0.17f) {
            slices = 16;
            rings = 8;
        } else {
            slices = 8;
            rings = 4;
        }
        poseStack.pushPose();
        poseStack.translate(relative.x(), relative.y(), relative.z());
        poseStack.mulPose(Axis.YP.rotation((float) Math.toRadians(visual.geometry().poleYaw())));
        poseStack.mulPose(Axis.XP.rotation((float) Math.toRadians(visual.geometry().polePitch())));
        poseStack.mulPose(Axis.ZP.rotation((float) Math.toRadians(visual.geometry().axialTilt())));
        float polarScale = (float) (1.0 - Math.clamp(visual.geometry().oblateness(), 0.0, 0.5));
        poseStack.scale(1.0f, polarScale, 1.0f);
        if (visual.rotationPeriod() > 0.0) poseStack.mulPose(Axis.YP.rotation(visual.rotationAt(timeDays)));

        float meshRadius = (float) Math.max(1.0e-6, radius);
        PlanetMesh mesh = CelestialMeshCache.planetMesh(slices, rings);
        Quaternionf bodyOrientation = new Quaternionf()
                .rotateY((float) Math.toRadians(visual.geometry().poleYaw()))
                .rotateX((float) Math.toRadians(visual.geometry().polePitch()))
                .rotateZ((float) Math.toRadians(visual.geometry().axialTilt()))
                .rotateY(visual.rotationAt(timeDays));
        Vector3f toCamera = new Vector3f((float) (observerSpace.x() - position.x()),
                (float) (observerSpace.y() - position.y()),
                (float) (observerSpace.z() - position.z())).normalize();
        Vector3f orientedNormal = new Vector3f();
        Vector3f quadNormal = new Vector3f();
        boolean emittedVertices = false;
        for (PlanetQuad quad : mesh.quads()) {
            if (!isFrontHemisphere(quad, polarScale, bodyOrientation, toCamera, quadNormal)) continue;
            vertex(builder, poseStack.last(), quad.first(), meshRadius, position, observerSpace, visual,
                    emissivePass, bloomPass, polarScale, bodyOrientation, orientedNormal);
            vertex(builder, poseStack.last(), quad.second(), meshRadius, position, observerSpace, visual,
                    emissivePass, bloomPass, polarScale, bodyOrientation, orientedNormal);
            vertex(builder, poseStack.last(), quad.third(), meshRadius, position, observerSpace, visual,
                    emissivePass, bloomPass, polarScale, bodyOrientation, orientedNormal);
            vertex(builder, poseStack.last(), quad.fourth(), meshRadius, position, observerSpace, visual,
                    emissivePass, bloomPass, polarScale, bodyOrientation, orientedNormal);
            emittedVertices = true;
        }
        poseStack.popPose();
        if (emittedVertices && bufferSource instanceof MultiBufferSource.BufferSource source) {
            CelestialLighting.configureCelestialLighting(position, radius, visual, suns,
                    materialPass, !emissivePass && !bloomPass && !(visual.glow() || emissive),
                    emissivePass && visual.emissiveNightOnly());
            source.endBatch(celestialType);
        }
    }

    private static void vertex(VertexConsumer builder, PoseStack.Pose pose, PlanetVertex vertex, float radius,
                       SpaceVec3 body, SpaceVec3 observerSpace,
                       CelestialVisualData visual,
                       boolean emissivePass, boolean bloomPass, float polarScale,
                       Quaternionf orientation, Vector3f orientedNormal) {
        float x = vertex.x() * radius;
        float y = vertex.y() * radius;
        float z = vertex.z() * radius;
        orientedNormal.set(vertex.x(), vertex.y() / polarScale, vertex.z()).normalize();
        orientation.transform(orientedNormal);
        float nx = orientedNormal.x;
        float ny = orientedNormal.y;
        float nz = orientedNormal.z;
        float corona = 1.0f;
        if (bloomPass) {
            SpaceVec3 relative = body.subtract(observerSpace);
            float viewX = (float) -(relative.x() + x);
            float viewY = (float) -(relative.y() + y);
            float viewZ = (float) -(relative.z() + z);
            float viewLength = Math.max(1.0e-5f,
                    (float) Math.sqrt(viewX * viewX + viewY * viewY + viewZ * viewZ));
            float facing = Math.max(0.0f, (nx * viewX + ny * viewY + nz * viewZ) / viewLength);
            corona = (float) Math.pow(1.0f - facing, 2.25);
        }
        int outputAlpha = emissivePass ? Math.clamp(Math.round(255.0f * visual.emissiveStrength()), 0, 255)
                : bloomPass ? Math.clamp(Math.round(255.0f * corona * visual.emissiveStrength()), 0, 255)
                : 255;
        builder.addVertex(pose, x, y, z)
                .setUv(visual.flipHorizontal() ? 1.0f - vertex.u() : vertex.u(), 1.0f - vertex.v())
                .setColor(255, 255, 255, outputAlpha)
                .setNormal(nx, ny, nz);
    }

    private static boolean isFrontHemisphere(PlanetQuad quad, float polarScale, Quaternionf orientation,
                                             Vector3f toCamera, Vector3f normal) {
        normal.set(quad.centerX(), quad.centerY() / polarScale, quad.centerZ()).normalize();
        orientation.transform(normal);
        return normal.dot(toCamera) > -0.02f;
    }
}
