#include astronautics:atmosphere_common

in vec3 viewDirection;
in vec3 sunDirection;
in vec3 localUp;
in vec4 vertexColor;

uniform vec3 AtmosphereTint;
uniform vec3 MieTint;
uniform vec3 RayleighCoeff;
uniform vec3 SunColor;
uniform float AtmosphereDensity;
uniform float RayleighStrength;
uniform float MieStrength;
uniform float MieAnisotropy;
uniform float MieCoefficient;
uniform float SunIntensity;
uniform float Exposure;
uniform float PlanetRadius;
uniform float AtmosphereRadius;
uniform float ObserverRadius;
uniform float WorldPerKilometre;
uniform float RayleighScaleHeight;
uniform float MieScaleHeight;
uniform float PhysicalScattering;

out vec4 fragColor;

const float PI = 3.14159265359;
const int VIEW_SAMPLES = 16;
const int SUN_SAMPLES = 8;

bool raySphere(vec3 origin, vec3 direction, vec3 center, float radius,
               out float nearDistance, out float farDistance) {
    vec3 offset = origin - center;
    float b = dot(offset, direction);
    float c = dot(offset, offset) - radius * radius;
    float discriminant = b * b - c;
    if (discriminant <= 0.0) {
        nearDistance = 0.0;
        farDistance = 0.0;
        return false;
    }
    float root = sqrt(discriminant);
    nearDistance = -b - root;
    farDistance = -b + root;
    return farDistance > 0.0;
}

vec2 densityAt(vec3 point, vec3 center, float planetRadius) {
    float altitudeKm = max(length(point - center) - planetRadius, 0.0) / WorldPerKilometre;
    return vec2(exp(-altitudeKm / max(RayleighScaleHeight, 0.001)),
            exp(-altitudeKm / max(MieScaleHeight, 0.001)));
}

vec2 sunOpticalDepth(vec3 point, vec3 sun, vec3 center) {
    float planetNear;
    float planetFar;
    if (raySphere(point, sun, center, PlanetRadius, planetNear, planetFar) && planetFar > 0.0) {
        return vec2(-1.0);
    }

    float atmosphereNear;
    float atmosphereFar;
    if (!raySphere(point, sun, center, AtmosphereRadius, atmosphereNear, atmosphereFar)) {
        return vec2(0.0);
    }

    float stepLengthKm = atmosphereFar / (WorldPerKilometre * float(SUN_SAMPLES));
    float rayleighDepth = 0.0;
    float mieDepth = 0.0;
    for (int i = 0; i < SUN_SAMPLES; i++) {
        vec3 samplePoint = point + sun * ((float(i) + 0.5) * stepLengthKm * WorldPerKilometre);
        vec2 density = densityAt(samplePoint, center, PlanetRadius);
        rayleighDepth += density.x * stepLengthKm;
        mieDepth += density.y * stepLengthKm;
    }
    return vec2(rayleighDepth, mieDepth);
}

float miePhase(float cosine, float g) {
    float g2 = g * g;
    float denominator = max(1.0 + g2 - 2.0 * g * cosine, 0.001);
    return 3.0 * (1.0 - g2) * (1.0 + cosine * cosine)
            / (8.0 * PI * (2.0 + g2) * pow(denominator, 1.5));
}

void main() {
    if (PhysicalScattering < 0.5) {
        vec3 normal = localUp;
        vec3 view = normalize(viewDirection);
        float elevation = dot(normal, sunDirection);
        float horizon = 1.0 - abs(dot(normal, view));
        float rayleigh = pow(max(0.0, 1.0 - max(elevation, 0.0)), 1.4);
        float mie = pow(max(0.0, dot(view, sunDirection)), 8.0);
        float sunset = pow(max(0.0, 1.0 - abs(elevation)), 3.0);
        vec3 color = AtmosphereTint * (rayleigh * RayleighStrength + horizon * 0.16)
                + MieTint * (mie * MieStrength + sunset * 0.12);
        float night = 1.0 - smoothstep(-0.12, 0.05, elevation);
        color += AtmosphereTint * 0.025 * night;
        float alpha = clamp(AtmosphereDensity * (0.12 + horizon * 0.42
                + rayleigh * 0.32 + mie * 0.22), 0.0, 0.82);
        fragColor = vec4(color, alpha * vertexColor.a);
        return;
    }

    vec3 view = normalize(viewDirection);
    vec3 sun = sunDirection;
    vec3 up = localUp;
    vec3 planetCenter = -up * ObserverRadius;

    float atmosphereNear;
    float atmosphereFar;
    if (!raySphere(vec3(0.0), view, planetCenter, AtmosphereRadius,
            atmosphereNear, atmosphereFar)) discard;

    float planetNear;
    float planetFar;
    float viewEnd = atmosphereFar;
    if (raySphere(vec3(0.0), view, planetCenter, PlanetRadius, planetNear, planetFar)
            && planetFar > 0.0) {
        viewEnd = min(viewEnd, max(planetNear, 0.0));
    }
    if (viewEnd <= 0.0) discard;

    float viewStep = viewEnd / float(VIEW_SAMPLES);
    float viewStepKm = viewStep / WorldPerKilometre;
    float viewRayleighDepth = 0.0;
    float viewMieDepth = 0.0;
    vec3 rayleighInscatter = vec3(0.0);
    vec3 mieInscatter = vec3(0.0);

    for (int i = 0; i < VIEW_SAMPLES; i++) {
        float distance = (float(i) + 0.5) * viewStep;
        vec3 point = view * distance;
        vec2 density = densityAt(point, planetCenter, PlanetRadius);
        float rayleighStep = density.x * viewStepKm;
        float mieStep = density.y * viewStepKm;
        viewRayleighDepth += rayleighStep;
        viewMieDepth += mieStep;

        vec2 sunDepth = sunOpticalDepth(point, sun, planetCenter);
        if (sunDepth.x < 0.0) continue;
        vec3 transmittance = exp(-(RayleighCoeff * (viewRayleighDepth + sunDepth.x)
                + vec3(MieCoefficient * (viewMieDepth + sunDepth.y))));
        rayleighInscatter += transmittance * rayleighStep;
        mieInscatter += transmittance * mieStep;
    }

    float cosine = dot(view, sun);
    float rayleighPhase = 3.0 * (1.0 + cosine * cosine) / (16.0 * PI);
    vec3 scattering = rayleighInscatter * RayleighCoeff * rayleighPhase
            + mieInscatter * MieCoefficient * miePhase(cosine, MieAnisotropy);
    scattering *= max(SunIntensity, 0.0) * SunColor;
    scattering = vec3(1.0) - exp(-max(Exposure, 0.0) * scattering);

    vec3 viewTau = RayleighCoeff * viewRayleighDepth
            + vec3(MieCoefficient * viewMieDepth);
    float alpha = clamp(1.0 - exp(-dot(viewTau, vec3(1.0 / 3.0))), 0.0, 1.0);
    fragColor = vec4(scattering, alpha);
}
