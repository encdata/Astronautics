uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
#include astronautics:celestial_lighting
uniform vec3 SunDirection;
uniform vec3 SunCenter;
uniform float SunRadius;
uniform vec3 LightingParams;
uniform vec3 BlockerCenter;
uniform float BlockerRadius;
uniform vec3 RingNormal;
uniform vec3 RingData;
uniform float MaterialSpecular;
uniform float MaterialRoughness;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 surfaceNormal;
in vec3 renderPosition;

out vec4 fragColor;

void main() {
    vec3 albedo = texture(Sampler0, texCoord0).rgb;
    vec3 geometricNormal = normalize(surfaceNormal);
    vec3 normalDetail = texture(Sampler1, texCoord0).xyz * 2.0 - 1.0;
    vec3 dx = dFdx(renderPosition);
    vec3 dy = dFdy(renderPosition);
    vec2 du = dFdx(texCoord0);
    vec2 dv = dFdy(texCoord0);
    vec3 tangent = normalize(dx * dv.y - dy * du.y);
    vec3 bitangent = normalize(cross(geometricNormal, tangent));
    vec3 normal = normalize(mat3(tangent, bitangent, geometricNormal) * normalDetail);

    vec3 sun = normalize(SunDirection);
    float diffuse = max(dot(geometricNormal, sun), 0.0);
    vec3 surface = geometricNormal * LightingParams.x;
    float bodyShadow = celestialEclipseTransmission(surface, SunCenter, SunRadius, BlockerCenter, BlockerRadius);
    float ringShadow = celestialRingShadow(surface, sun, RingNormal, RingData);
    float light = LightingParams.y + LightingParams.z * diffuse * bodyShadow * ringShadow;
    vec3 viewDirection = normalize(-renderPosition);
    vec3 halfVector = normalize(sun + viewDirection);
    float roughness = clamp(MaterialRoughness, 0.04, 1.0);
    float roughnessSquared = roughness * roughness;
    float normalHalf = max(dot(normal, halfVector), 0.0);
    float normalView = max(dot(normal, viewDirection), 0.0);
    float normalLight = max(dot(normal, sun), 0.0);
    float viewHalf = max(dot(viewDirection, halfVector), 0.0);
    float distributionDenominator = normalHalf * normalHalf * (roughnessSquared - 1.0) + 1.0;
    float distribution = roughnessSquared / max(CELESTIAL_PI * distributionDenominator * distributionDenominator, 1.0e-5);
    float k = (roughness + 1.0) * (roughness + 1.0) * 0.125;
    float geometry = normalView / mix(normalView, 1.0, k) * normalLight / mix(normalLight, 1.0, k);
    float fresnel = MaterialSpecular + (1.0 - MaterialSpecular) * pow(1.0 - viewHalf, 5.0);
    float specular = texture(Sampler2, texCoord0).r * distribution * geometry * fresnel
            / max(4.0 * normalView * normalLight, 1.0e-4);
    vec3 lit = albedo * light;
    lit += vec3(specular * light);
    fragColor = vec4(lit, 1.0);
}
