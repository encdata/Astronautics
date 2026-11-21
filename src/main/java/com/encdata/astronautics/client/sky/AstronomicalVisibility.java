package com.encdata.astronautics.client.sky;

import foundry.veil.api.client.render.shader.program.ShaderProgram;

public final class AstronomicalVisibility {
    private AstronomicalVisibility() {}
    public static void upload(ShaderProgram shader, float atmosphere, float sunElevation, float time) {
        shader.getUniformSafe("Atmosphere").setFloat(Math.clamp(atmosphere, 0, 4));
        shader.getUniformSafe("SunElevation").setFloat(sunElevation);
        shader.getUniformSafe("SkyTime").setFloat(time);
        shader.getUniformSafe("Twinkle").setFloat(Boolean.getBoolean("astronautics.stars.noTwinkle") ? 0 : 1);
    }
}
