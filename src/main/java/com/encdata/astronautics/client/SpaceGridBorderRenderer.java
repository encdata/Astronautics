package com.encdata.astronautics.client;

import com.encdata.astronautics.space.SpaceConstants;
import com.encdata.astronautics.space.SpacePosition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

public final class SpaceGridBorderRenderer {
    private static final double GRID = SpacePosition.GRID_SIZE;
    private static final double HALF = SpacePosition.GRID_HALF_SIZE;
    private static final int CHUNKS_EACH_SIDE = 6;
    private static final double MAX_RANGE = 512.0;

    private SpaceGridBorderRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffers, double camX, double camY, double camZ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !SpaceConstants.isSpace(level)) return;

        double range = Math.min(MAX_RANGE, minecraft.options.getEffectiveRenderDistance() * 16.0);
        VertexConsumer lines = buffers.getBuffer(RenderType.debugLineStrip(1.0));
        Matrix4f matrix = poseStack.last().pose();
        double[] camera = {camX, camY, camZ};
        for (int axis = 0; axis <= 2; axis += 2) {
            long cell = (long) Math.floor((camera[axis] + HALF) / GRID);
            for (int side = 0; side <= 1; side++) {
                double plane = cell * GRID + (side == 0 ? -HALF : HALF);
                if (Math.abs(camera[axis] - plane) <= range) wall(lines, matrix, axis, plane, camera);
            }
        }
    }

    private static void wall(VertexConsumer lines, Matrix4f matrix, int axis, double plane,
                             double[] camera) {
        int along = axis == 0 ? 2 : 0;
        double from = (Math.floor(camera[along] / 16.0) - CHUNKS_EACH_SIDE) * 16.0;
        double to = from + (2 * CHUNKS_EACH_SIDE + 1) * 16.0;
        double bottom = (Math.floor(camera[1] / 16.0) - CHUNKS_EACH_SIDE) * 16.0;
        double top = bottom + (2 * CHUNKS_EACH_SIDE + 1) * 16.0;
        for (double a = from; a <= to; a += 16.0) {
            segment(lines, matrix, axis, plane, along, a, a, bottom, top, camera, a % 64.0 == 0.0);
        }
        for (double y = bottom; y <= top; y += 16.0) {
            segment(lines, matrix, axis, plane, along, from, to, y, y, camera, y % 64.0 == 0.0);
        }
    }

    private static void segment(VertexConsumer lines, Matrix4f matrix, int axis, double plane, int along,
                                double alongFrom, double alongTo, double yFrom, double yTo, double[] camera,
                                boolean bright) {
        float red = 1.0F;
        float green = bright ? 0.3F : 0.0F;
        float blue = 1.0F;
        float alpha = bright ? 1.0F : 0.7F;
        double[] start = new double[3];
        double[] end = new double[3];
        start[axis] = end[axis] = plane;
        start[along] = alongFrom;
        end[along] = alongTo;
        start[1] = yFrom;
        end[1] = yTo;
        vertex(lines, matrix, start, camera).setColor(red, green, blue, 0.0F);
        vertex(lines, matrix, start, camera).setColor(red, green, blue, alpha);
        vertex(lines, matrix, end, camera).setColor(red, green, blue, alpha);
        vertex(lines, matrix, end, camera).setColor(red, green, blue, 0.0F);
    }

    private static VertexConsumer vertex(VertexConsumer lines, Matrix4f matrix, double[] point, double[] camera) {
        return lines.addVertex(matrix, (float) (point[0] - camera[0]), (float) (point[1] - camera[1]),
                (float) (point[2] - camera[2]));
    }
}
