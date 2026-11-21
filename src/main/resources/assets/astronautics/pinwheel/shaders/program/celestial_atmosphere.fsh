#veil:buffer veil:camera VeilCamera
#include astronautics:atmosphere_common

in vec3 viewPosition;
in vec4 vertexColor;
in vec3 surfaceNormal;
in vec3 sunDirection;
in vec2 sphereParams;

uniform vec3 RayleighCoeff;
uniform vec3 SunColor;
uniform vec4 ScatteringParams;
uniform vec3 ScatteringLighting;
uniform float PhysicalScattering;

out vec4 fragColor;



const int IN_SCATTER_POINTS = 8;
const int SUN_SCATTER_POINTS = 3;
const float PI = 3.14159265359;

vec3 sunTransmittance(vec3 point, vec3 sun, vec3 center, float planetRadius,
                      float atmosphereRadius, vec3 betaRayleigh, float betaMie,
                      float rayleighScaleHeight, float mieScaleHeight) {
    float planetNear;
    float planetFar;
    if (atmosphereRaySphere(point, sun, center, planetRadius, planetNear, planetFar)
            && planetFar > 0.0) return vec3(0.0);

    float atmosphereNear;
    float atmosphereFar;
    if (!atmosphereRaySphere(point, sun, center, atmosphereRadius, atmosphereNear, atmosphereFar)) {
        return vec3(1.0);
    }
    float stepLength = atmosphereFar / float(SUN_SCATTER_POINTS);
    float rayleighDepth = 0.0;
    float mieDepth = 0.0;
    for (int i = 0; i < SUN_SCATTER_POINTS; i++) {
        vec3 samplePoint = point + sun * ((float(i) + 0.5) * stepLength);
        float altitude = max(length(samplePoint - center) - planetRadius, 0.0);
        rayleighDepth += exp(-altitude / rayleighScaleHeight) * stepLength;
        mieDepth += exp(-altitude / mieScaleHeight) * stepLength;
    }
    return exp(-(betaRayleigh * rayleighDepth + vec3(betaMie * mieDepth)));
}

void main() {
    if (PhysicalScattering < 0.5) {


        vec3 normal = normalize(surfaceNormal);
        vec3 view = normalize(-viewPosition);
        float rim = pow(1.0 - max(dot(normal, view), 0.0), 1.65);
        float sunLight = 0.28 + 0.72 * max(dot(normal, sunDirection), 0.0);
        float alpha = rim * vertexColor.a;
        if (alpha < 0.002) discard;
        gl_FragDepth = max(gl_FragCoord.z - 0.00001, 0.0);
        fragColor = vec4(vertexColor.rgb * sunLight, alpha);
        return;
    }

    float atmosphereRadius = sphereParams.x;
    float planetRadius = atmosphereRadius * clamp(sphereParams.y, 0.0, 1.0);
    vec3 normal = normalize(surfaceNormal);
    vec3 ray = normalize(viewPosition);
    vec3 sun = sunDirection;

    vec3 center = vec3(0.0);
    float cameraDistance = length(viewPosition);
    float shift = min(cameraDistance, atmosphereRadius * 8.0);
    vec3 origin = normal * atmosphereRadius - ray * shift;
    float cameraT = shift - cameraDistance;

    float atmosphereNear;
    float atmosphereFar;
    if (!atmosphereRaySphere(origin, ray, center, atmosphereRadius,
            atmosphereNear, atmosphereFar)) discard;
    float start = max(atmosphereNear, cameraT);
    float end = atmosphereFar;
    float planetNear;
    float planetFar;
    if (atmosphereRaySphere(origin, ray, center, planetRadius, planetNear, planetFar)
            && planetFar > cameraT) end = min(end, max(planetNear, cameraT));
    if (end <= start) discard;



    float worldPerKilometre = max(planetRadius / 6371.0, 0.001);
    vec3 betaRayleigh = RayleighCoeff / worldPerKilometre;
    float betaMie = ScatteringParams.x / worldPerKilometre;
    float rayleighScaleHeight = max(ScatteringParams.y * worldPerKilometre, 0.001);
    float mieScaleHeight = max(ScatteringParams.z * worldPerKilometre, 0.001);

    float stepLength = (end - start) / float(IN_SCATTER_POINTS);
    float viewRayleighDepth = 0.0;
    float viewMieDepth = 0.0;
    vec3 inScatterRayleigh = vec3(0.0);
    vec3 inScatterMie = vec3(0.0);
    for (int i = 0; i < IN_SCATTER_POINTS; i++) {
        vec3 point = origin + ray * (start + (float(i) + 0.5) * stepLength);
        float altitude = max(length(point - center) - planetRadius, 0.0);
        float densityRayleigh = exp(-altitude / rayleighScaleHeight) * stepLength;
        float densityMie = exp(-altitude / mieScaleHeight) * stepLength;
        viewRayleighDepth += densityRayleigh;
        viewMieDepth += densityMie;
        vec3 viewTransmittance = exp(-(betaRayleigh * viewRayleighDepth
                + vec3(betaMie * viewMieDepth)));
        vec3 sunTransmission = sunTransmittance(point, sun, center, planetRadius, atmosphereRadius,
                betaRayleigh, betaMie, rayleighScaleHeight, mieScaleHeight);
        inScatterRayleigh += densityRayleigh * viewTransmittance * sunTransmission;
        inScatterMie += densityMie * viewTransmittance * sunTransmission;
    }




    float mu = dot(ray, sun);
    float rayleighPhase = 3.0 * (1.0 + mu * mu) / (16.0 * PI);
    float g = clamp(ScatteringParams.w, -0.98, 0.98);
    float miePhase = 3.0 * (1.0 - g * g) * (1.0 + mu * mu)
            / (8.0 * PI * (2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    vec3 radiance = ScatteringLighting.x * SunColor * (inScatterRayleigh * betaRayleigh * rayleighPhase
            + inScatterMie * betaMie * miePhase);
    radiance = vec3(1.0) - exp(-ScatteringLighting.y * radiance);

    vec3 viewTau = betaRayleigh * viewRayleighDepth + vec3(betaMie * viewMieDepth);



    float alpha = clamp(1.0 - exp(-dot(viewTau, vec3(1.0 / 3.0))), 0.0, 1.0);



    float surfaceFacing = max(dot(normal, -ray), 0.0);
    float limbWeight = pow(1.0 - surfaceFacing, 0.7);
    float inScatterWeight = mix(0.22, 1.0, limbWeight);
    radiance *= inScatterWeight;
    alpha *= inScatterWeight;
    if (alpha < 0.002) discard;



    gl_FragDepth = max(gl_FragCoord.z - 0.00001, 0.0);

    fragColor = vec4(radiance / max(alpha, 0.001), alpha);
}
