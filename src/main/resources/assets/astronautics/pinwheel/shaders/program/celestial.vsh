#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 surfaceNormal;
out vec3 renderPosition;

void main() {
    texCoord0 = UV0;
    vertexColor = Color;
    surfaceNormal = normalize(Normal);
    renderPosition = Position;
    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat * vec4(Position, 1.0);
}
