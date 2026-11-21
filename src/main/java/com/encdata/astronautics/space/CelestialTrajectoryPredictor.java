package com.encdata.astronautics.space;

import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public final class CelestialTrajectoryPredictor {
    private static final double STEP_SECONDS = 0.25;
    private static final int STEP_COUNT = 720;
    private static final int SAMPLE_INTERVAL = 12;
    private static final int SOURCE_REFRESH_INTERVAL = 20;

    private CelestialTrajectoryPredictor() {}

    public static List<Vector3d> predict(Vector3dc initialPosition, Vector3dc initialVelocity,
                                         double timeDays) {
        List<CelestialGravity.GravitySource> sources = CelestialGravity.sourcesAtTime(timeDays);
        List<Vector3d> points = new ArrayList<>(STEP_COUNT / SAMPLE_INTERVAL + 1);
        Vector3d position = new Vector3d(initialPosition);
        Vector3d velocity = new Vector3d(initialVelocity);
        Vector3d acceleration = new Vector3d();
        points.add(new Vector3d(position));

        for (int step = 1; step <= STEP_COUNT; step++) {
            if (step % SOURCE_REFRESH_INTERVAL == 0) {
                sources = CelestialGravity.sourcesAtTime(
                        timeDays + step * STEP_SECONDS / 1200.0);
            }
            acceleration.set(0.0);
            for (CelestialGravity.GravitySource source : sources) {
                CelestialGravity.addField(position, source, acceleration, null);
            }
            velocity.fma(STEP_SECONDS * 0.5, acceleration);
            position.fma(STEP_SECONDS, velocity);

            acceleration.set(0.0);
            for (CelestialGravity.GravitySource source : sources) {
                CelestialGravity.addField(position, source, acceleration, null);
            }
            velocity.fma(STEP_SECONDS * 0.5, acceleration);
            if (step % SAMPLE_INTERVAL == 0) points.add(new Vector3d(position));
        }
        return points;
    }
}
