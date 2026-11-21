#version 410 core
out vec2 ScreenUV;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    ScreenUV = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.999999, 1.0);
}
