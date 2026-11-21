#veil:buffer veil:camera VeilCamera
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;
out vec2 texCoord;
out vec4 vertexColor;
void main() {
    texCoord = UV0;
    vertexColor = Color;
    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat * vec4(Position, 1.0);
}
