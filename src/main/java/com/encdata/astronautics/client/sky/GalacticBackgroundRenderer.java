package com.encdata.astronautics.client.sky;

import com.encdata.astronautics.Astronautics;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4fc;
import static org.lwjgl.opengl.GL33C.*;

public final class GalacticBackgroundRenderer {
    private static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(Astronautics.MOD_ID, "galactic_background");
    private GalacticBackgroundRenderer() {}
    public static boolean render(Matrix4fc inverseProjection, Matrix4fc inverseSkyView,
                                 Matrix4fc skyRotation, float atmosphere, float sunElevation, float time) {
        if (Boolean.getBoolean("astronautics.stars.noGalaxy")) return false;
        ShaderProgram shader = VeilRenderSystem.setShader(SHADER);
        if (shader == null) {
            BackgroundStarRenderer.warnOnce("galaxyShader", "Galactic background shader unavailable");
            return false;
        }
        shader.bind();
        shader.getUniformSafe("InverseProjection").setMatrix(inverseProjection);
        shader.getUniformSafe("InverseSkyView").setMatrix(inverseSkyView);
        shader.getUniformSafe("SkyRotation").setMatrix(skyRotation);
        AstronomicalVisibility.upload(shader, atmosphere, sunElevation, time);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        return true;
    }
}
