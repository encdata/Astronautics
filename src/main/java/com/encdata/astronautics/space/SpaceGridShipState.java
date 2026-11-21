package com.encdata.astronautics.space;

public record SpaceGridShipState(
        double x, double y, double z,
        double rotationX, double rotationY, double rotationZ, double rotationW,
        double rotationPointX, double rotationPointY, double rotationPointZ,
        double velocityX, double velocityY, double velocityZ,
        double angularVelocityX, double angularVelocityY, double angularVelocityZ) {

    public SpaceGridShipState {
        requireFinite(x, y, z, rotationX, rotationY, rotationZ, rotationW,
                rotationPointX, rotationPointY, rotationPointZ,
                velocityX, velocityY, velocityZ,
                angularVelocityX, angularVelocityY, angularVelocityZ);
        if (x < -SpacePosition.GRID_HALF_SIZE || x >= SpacePosition.GRID_HALF_SIZE
                || y < -SpacePosition.GRID_HALF_SIZE || y >= SpacePosition.GRID_HALF_SIZE
                || z < -SpacePosition.GRID_HALF_SIZE || z >= SpacePosition.GRID_HALF_SIZE) {
            throw new IllegalArgumentException("ship position must be normalized within its grid");
        }

        double length = Math.sqrt(rotationX * rotationX + rotationY * rotationY
                + rotationZ * rotationZ + rotationW * rotationW);
        if (length < 1.0e-12) throw new IllegalArgumentException("ship rotation cannot be zero");
        rotationX /= length;
        rotationY /= length;
        rotationZ /= length;
        rotationW /= length;
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("ship state must be finite");
        }
    }
}
