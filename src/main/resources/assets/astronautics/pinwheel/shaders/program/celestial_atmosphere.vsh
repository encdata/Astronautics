#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;
uniform vec3 SunDirection;

out vec3 viewPosition;
out vec4 vertexColor;
out vec3 surfaceNormal;
out vec3 sunDirection;
out vec2 sphereParams;

void main() {
    viewPosition = (VeilCamera.ViewMat * vec4(Position, 1.0)).xyz;
    vertexColor = Color;
    mat3 viewRotation = mat3(VeilCamera.ViewMat);
    surfaceNormal = normalize(viewRotation * Normal);
    sunDirection = normalize(viewRotation * SunDirection);
    sphereParams = UV0;
    gl_Position = VeilCamera.ProjMat * vec4(viewPosition, 1.0);
}
