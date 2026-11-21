uniform sampler2D Sampler0;
#include astronautics:celestial_lighting
uniform vec3 SunDirection;
uniform vec3 SunCenter;
uniform float SunRadius;
uniform vec3 LightingParams;
uniform float LightingEnabled;
uniform vec3 BlockerCenter;
uniform float BlockerRadius;
uniform vec3 RingNormal;
uniform vec3 RingData;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 surfaceNormal;

out vec4 fragColor;

void main() {
    vec4 albedo = texture(Sampler0, texCoord0);
    if (LightingEnabled < 0.5) {
        fragColor = albedo * vertexColor;
        return;
    }
    vec3 normal = normalize(surfaceNormal);
    float diffuse = max(dot(normal, normalize(SunDirection)), 0.0);
    vec3 surface = normal * LightingParams.x;
    float bodyShadow = celestialEclipseTransmission(surface, SunCenter, SunRadius, BlockerCenter, BlockerRadius);
    float ringShadow = celestialRingShadow(surface, normalize(SunDirection), RingNormal, RingData);
    float shadow = bodyShadow * ringShadow;
    float light = LightingParams.y + LightingParams.z * diffuse * shadow;
    if (LightingEnabled > 1.5) {
        float night = clamp(1.0 - light, 0.0, 1.0);
        fragColor = vec4(albedo.rgb * vertexColor.rgb * night,
                albedo.a * vertexColor.a * night);
        return;
    }
    fragColor = vec4(albedo.rgb * light, albedo.a * vertexColor.a);
}
