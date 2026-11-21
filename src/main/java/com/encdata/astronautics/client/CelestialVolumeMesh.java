package com.encdata.astronautics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

final class CelestialVolumeMesh {
    private static final float[][] FACES = {
            {-1,-1, 1,  1,-1, 1,  1, 1, 1, -1, 1, 1},
            { 1,-1,-1, -1,-1,-1, -1, 1,-1,  1, 1,-1},
            { 1,-1,-1,  1, 1,-1,  1, 1, 1,  1,-1, 1},
            {-1,-1, 1, -1, 1, 1, -1, 1,-1, -1,-1,-1},
            {-1, 1, 1,  1, 1, 1,  1, 1,-1, -1, 1,-1},
            {-1,-1,-1,  1,-1,-1,  1,-1, 1, -1,-1, 1}
    };

    private CelestialVolumeMesh() {}

    static void emitCube(VertexConsumer builder, PoseStack.Pose pose, float halfExtent) {
        for (float[] face : FACES) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = face[vertex * 3] * halfExtent;
                float y = face[vertex * 3 + 1] * halfExtent;
                float z = face[vertex * 3 + 2] * halfExtent;
                builder.addVertex(pose, x, y, z)
                        .setUv(0.0f, 0.0f)
                        .setColor(255, 255, 255, 255)
                        .setNormal(0.0f, 0.0f, 1.0f);
            }
        }
    }
}
