#version 410 core
in vec2 PointUV;
in vec3 StarColor;
in float StarFlux;
in float Glow;
in float Footprint;
out vec4 fragColor;
void main() {
    float radius = length(PointUV) * Footprint * 0.5;
    float core = exp(-radius * radius * 3.4);
    float halo = Glow * 0.035 * exp(-radius * radius * 0.6);
    fragColor = vec4(StarColor * StarFlux * (core + halo), 0.0);
}
