
const float CELESTIAL_PI = 3.14159265359;

float celestialDiscOverlap(float firstRadius, float secondRadius, float separation) {
    if (separation >= firstRadius + secondRadius) return 0.0;
    if (separation <= abs(firstRadius - secondRadius)) {
        return CELESTIAL_PI * min(firstRadius, secondRadius) * min(firstRadius, secondRadius);
    }

    float firstAngle = acos(clamp((separation * separation + firstRadius * firstRadius - secondRadius * secondRadius)
            / (2.0 * separation * firstRadius), -1.0, 1.0));
    float secondAngle = acos(clamp((separation * separation + secondRadius * secondRadius - firstRadius * firstRadius)
            / (2.0 * separation * secondRadius), -1.0, 1.0));
    float lens = 0.5 * sqrt(max(0.0, (-separation + firstRadius + secondRadius)
            * (separation + firstRadius - secondRadius) * (separation - firstRadius + secondRadius)
            * (separation + firstRadius + secondRadius)));
    return firstRadius * firstRadius * firstAngle + secondRadius * secondRadius * secondAngle - lens;
}

float celestialEclipseTransmission(vec3 surface, vec3 sunCenter, float sunRadius,
                                   vec3 blockerCenter, float blockerRadius) {
    if (sunRadius <= 0.0 || blockerRadius <= 0.0) return 1.0;
    vec3 toSun = sunCenter - surface;
    vec3 toBlocker = blockerCenter - surface;
    float sunDistance = length(toSun);
    float blockerDistance = length(toBlocker);
    if (sunDistance <= sunRadius || blockerDistance <= blockerRadius || blockerDistance >= sunDistance) return 1.0;

    float sunAngularRadius = asin(clamp(sunRadius / sunDistance, 0.0, 1.0));
    float blockerAngularRadius = asin(clamp(blockerRadius / blockerDistance, 0.0, 1.0));
    float separation = acos(clamp(dot(toSun / sunDistance, toBlocker / blockerDistance), -1.0, 1.0));
    float covered = celestialDiscOverlap(sunAngularRadius, blockerAngularRadius, separation)
            / max(CELESTIAL_PI * sunAngularRadius * sunAngularRadius, 1.0e-6);
    return clamp(1.0 - covered, 0.0, 1.0);
}

float celestialRingShadow(vec3 surface, vec3 lightDirection, vec3 ringNormal, vec3 ringData) {
    if (ringData.z <= 0.0 || ringData.y <= ringData.x) return 1.0;
    vec3 normal = normalize(ringNormal);
    float incidence = dot(normal, lightDirection);
    if (abs(incidence) < 1.0e-4) return 1.0;
    float distance = -dot(surface, normal) / incidence;
    if (distance <= 0.0) return 1.0;
    vec3 hit = surface + lightDirection * distance;
    float radialDistance = length(hit - normal * dot(hit, normal));
    if (radialDistance < ringData.x || radialDistance > ringData.y) return 1.0;
    return exp(-ringData.z / max(abs(incidence), 0.08));
}
