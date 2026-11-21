package com.encdata.astronautics.client;

import foundry.veil.api.client.render.shader.program.ShaderProgram;
import org.joml.Vector3f;

final class CelestialShaderUniforms {
    private CelestialShaderUniforms() {}

    static void setFloat(ShaderProgram shader, String name, float value) {
        if (shader == null) throw new IllegalArgumentException("Shader cannot be null when setting " + name);
        var uniform = shader.getUniform(name);
        if (uniform == null) throw new IllegalStateException("Shader is missing required uniform " + name);
        uniform.setFloat(value);
    }

    static void setVector(ShaderProgram shader, String name, Vector3f value) {
        if (shader == null) throw new IllegalArgumentException("Shader cannot be null when setting " + name);
        if (value == null) throw new IllegalArgumentException("Uniform value cannot be null for " + name);
        var uniform = shader.getUniform(name);
        if (uniform == null) throw new IllegalStateException("Shader is missing required uniform " + name);
        uniform.setVector(value);
    }

    static void setVector(ShaderProgram shader, String name, float x, float y, float z) {
        if (shader == null) throw new IllegalArgumentException("Shader cannot be null when setting " + name);
        var uniform = shader.getUniform(name);
        if (uniform == null) throw new IllegalStateException("Shader is missing required uniform " + name);
        uniform.setVector(x, y, z);
    }

    static void setVector(ShaderProgram shader, String name, float x, float y, float z, float w) {
        if (shader == null) throw new IllegalArgumentException("Shader cannot be null when setting " + name);
        var uniform = shader.getUniform(name);
        if (uniform == null) throw new IllegalStateException("Shader is missing required uniform " + name);
        uniform.setVector(x, y, z, w);
    }
}
