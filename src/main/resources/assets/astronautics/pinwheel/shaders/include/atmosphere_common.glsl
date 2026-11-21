const int ATMOSPHERE_VIEW_SAMPLES = 8;
const int ATMOSPHERE_SUN_SAMPLES = 3;
const float ATMOSPHERE_PI = 3.14159265359;

bool atmosphereRaySphere(vec3 origin, vec3 direction, vec3 center, float radius,
                         out float nearDistance, out float farDistance) {
    vec3 offset = origin - center;
    float projection = dot(offset, direction);
    float discriminant = projection * projection - dot(offset, offset) + radius * radius;
    if (discriminant < 0.0) return false;
    float halfChord = sqrt(discriminant);
    nearDistance = -projection - halfChord;
    farDistance = -projection + halfChord;
    return farDistance > 0.0;
}

vec2 atmosphereDensity(vec3 point, vec3 center, float planetRadius, float atmosphereRadius) {
    float thickness = max(atmosphereRadius - planetRadius, 0.001);
    float altitude = clamp((length(point - center) - planetRadius) / thickness, 0.0, 1.0);
    return vec2(exp(-altitude * 4.0), exp(-altitude * 12.0));
}

vec3 atmosphereSunTransmittance(vec3 point, vec3 sunDirection, vec3 center,
                                float planetRadius, float atmosphereRadius, vec3 betaRayleigh,
                                vec3 betaMie) {
    float planetNear;
    float planetFar;
    if (atmosphereRaySphere(point + sunDirection * 0.002, sunDirection, center, planetRadius,
                            planetNear, planetFar)) {
        return vec3(0.0);
    }

    float atmosphereNear;
    float atmosphereFar;
    if (!atmosphereRaySphere(point, sunDirection, center, atmosphereRadius,
                             atmosphereNear, atmosphereFar)) {
        return vec3(1.0);
    }

    float lengthToExit = max(atmosphereFar, 0.0);
    float stepLength = lengthToExit / float(ATMOSPHERE_SUN_SAMPLES);
    vec2 depth = vec2(0.0);
    for (int i = 0; i < ATMOSPHERE_SUN_SAMPLES; i++) {
        vec3 samplePoint = point + sunDirection * ((float(i) + 0.5) * stepLength);
        depth += atmosphereDensity(samplePoint, center, planetRadius, atmosphereRadius) * stepLength;
    }
    return exp(-(betaRayleigh * depth.x + betaMie * depth.y));
}

float atmosphereRayleighPhase(float cosine) {
    return 3.0 * (1.0 + cosine * cosine) / (16.0 * ATMOSPHERE_PI);
}

float atmosphereMiePhase(float cosine, float asymmetry) {
    float g2 = asymmetry * asymmetry;
    float denominator = max(1.0 + g2 - 2.0 * asymmetry * cosine, 0.001);
    return (1.0 - g2) / (4.0 * ATMOSPHERE_PI * denominator * sqrt(denominator));
}
