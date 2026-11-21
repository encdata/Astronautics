#veil:buffer veil:camera VeilCamera

in vec3 fragLocalPos;
out vec4 fragColor;

uniform float PlanetRadius;
uniform float BloomRadius;
uniform float BloomPass;
uniform vec3 SunTint;
uniform vec3 CameraLocalPos;

const float DENSITY = 4.0;
const float SCATTERING_STRENGTH = 1.0;
const int MARCH_STEPS = 3;
const float DENSITY_CURVE = 0.97;
const vec3 CORE_COLOR = vec3(1.00, 0.98, 0.90);
const vec3 MID_COLOR = vec3(1.00, 0.65, 0.20);
const vec3 OUTER_COLOR = vec3(0.70, 0.20, 0.05);

struct Ray {
    vec3 origin;
    vec3 direction;
};

bool raySphere(Ray ray, float radius, out vec2 hit) {
    float b = dot(ray.origin, ray.direction);
    float c = dot(ray.origin, ray.origin) - radius * radius;
    float discriminant = b * b - c;
    if (discriminant < 0.0) {
        hit = vec2(-1.0);
        return false;
    }
    float root = sqrt(discriminant);
    hit = vec2(-b - root, -b + root);
    if (hit.y < 0.0) {
        hit = vec2(-1.0);
        return false;
    }
    hit = max(hit, 0.0);
    return true;
}

float densityAt(vec3 point, float innerRadius, float outerRadius) {
    float height = clamp((length(point) - innerRadius)
            / max(outerRadius - innerRadius, 0.0001), 0.0, 1.0);
    float denominator = 1.0 + height * DENSITY * 10.0;
    return (1.0 - height) / max(pow(denominator, DENSITY_CURVE), 0.0001);
}

vec3 toneMap(vec3 color) {
    return clamp((color * (2.51 * color + 0.03))
            / (color * (2.43 * color + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    if (!gl_FrontFacing) discard;

    float innerRadius = PlanetRadius;
    float outerRadius = max(BloomRadius, innerRadius + 0.0001);

    vec3 toFragment = fragLocalPos - CameraLocalPos;
    float cameraDistance = length(toFragment);
    Ray viewRay;
    viewRay.direction = toFragment / max(cameraDistance, 0.0001);
    viewRay.origin = fragLocalPos - viewRay.direction * min(cameraDistance, outerRadius * 8.0);
    vec2 coreHit;
    vec2 bloomHit;
    bool hitsCore = raySphere(viewRay, innerRadius, coreHit);
    if (!raySphere(viewRay, outerRadius, bloomHit)) discard;

    float start = bloomHit.x;
    float end = bloomHit.y;
    if (hitsCore) end = coreHit.x;
    float shellThickness = outerRadius - innerRadius;
    float stepLength = max(end - start, 0.0) / float(MARCH_STEPS);
    float normalizedStep = stepLength / max(shellThickness, 0.0001);
    vec3 accumulatedGlow = vec3(0.0);
    float transmission = 1.0;

    for (int i = 0; i < MARCH_STEPS; i++) {
        float distanceAlongRay = start + (float(i) + 0.5) * stepLength;
        vec3 point = viewRay.origin + viewRay.direction * distanceAlongRay;
        float height = clamp((length(point) - innerRadius) / shellThickness, 0.0, 1.0);
        float density = densityAt(point, innerRadius, outerRadius);
        vec3 layerColor = mix(MID_COLOR, OUTER_COLOR, height);
        accumulatedGlow += layerColor * density * normalizedStep
                * SCATTERING_STRENGTH * 16.0 * transmission;
        transmission *= exp(-density * normalizedStep * SCATTERING_STRENGTH * 3.0);
        if (transmission < 0.001) break;
    }

    vec3 color = accumulatedGlow * SunTint;
    float alpha = 1.0 - transmission;
    if (BloomPass > 0.5) {
        if (hitsCore) discard;
        if (alpha < 0.0001) discard;
        fragColor = vec4(toneMap(color), clamp(alpha, 0.0, 1.0));
        return;
    }

    if (hitsCore) {
        vec3 hitPosition = viewRay.origin + viewRay.direction * coreHit.x;
        vec3 normal = normalize(hitPosition);
        float lightDirection = max(dot(-viewRay.direction, normal), 0.0);
        float lighting = 0.35 + 0.65 * sqrt(lightDirection);
        color += mix(MID_COLOR, CORE_COLOR, lighting) * 2.5 * SunTint;
        alpha = 1.0;
    }
    if (alpha < 0.0001) discard;
    float coreExposure = 1.0;
    if (hitsCore) coreExposure = 1.4;
    fragColor = vec4(toneMap(color * coreExposure), clamp(alpha, 0.0, 1.0));
}
