#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    vec3 viewPosition = (VeilCamera.ViewMat * vec4(Position, 1.0)).xyz;
    gl_Position = VeilCamera.ProjMat * vec4(viewPosition, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
