package com.encdata.astronautics.client;

import org.joml.Vector3f;

public final class StarColor {
    private StarColor() {}

    public static Vector3f fromTemperature(double kelvin) {
        double temperature = Math.clamp(kelvin, 1_000.0, 40_000.0) / 100.0;
        double red = temperature <= 66.0 ? 255.0
                : Math.clamp(329.698727446 * Math.pow(temperature - 60.0, -0.1332047592), 0.0, 255.0);
        double green = temperature <= 66.0
                ? Math.clamp(99.4708025861 * Math.log(temperature) - 161.1195681661, 0.0, 255.0)
                : Math.clamp(288.1221695283 * Math.pow(temperature - 60.0, -0.0755148492), 0.0, 255.0);
        double blue = temperature >= 66.0 ? 255.0 : temperature <= 19.0 ? 0.0
                : Math.clamp(138.5177312231 * Math.log(temperature - 10.0) - 305.0447927307, 0.0, 255.0);
        return new Vector3f((float) (red / 255.0), (float) (green / 255.0), (float) (blue / 255.0));
    }
}
