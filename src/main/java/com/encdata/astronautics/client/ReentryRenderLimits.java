package com.encdata.astronautics.client;

import net.minecraft.world.phys.Vec3;

public final class ReentryRenderLimits {
    private static final double MAX_RENDER_DISTANCE = 10_000.0;
    private static final double MAX_RENDER_DISTANCE_SQR = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    private ReentryRenderLimits() {}

    public static boolean withinRenderRange(Vec3 camera, Vec3 source) {
        return camera.distanceToSqr(source) <= MAX_RENDER_DISTANCE_SQR;
    }
}
