#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 2) in vec4 Color;
uniform vec3 SunDirection;
uniform vec3 PlanetUp;

out vec3 viewDirection;
out vec3 sunDirection;
out vec3 localUp;
out vec4 vertexColor;

void main() {
    vec3 viewPosition = (VeilCamera.ViewMat * vec4(Position, 1.0)).xyz;
    mat3 rotation = mat3(VeilCamera.ViewMat);
    viewDirection = normalize(viewPosition);
    sunDirection = normalize(rotation * SunDirection);
    localUp = normalize(rotation * PlanetUp);
    vertexColor = Color;
    gl_Position = VeilCamera.ProjMat * vec4(viewPosition, 1.0);
}
