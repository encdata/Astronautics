in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

uniform vec3 SunTint;
uniform float Time;
uniform float FlareAlpha;

float hash(vec2 cell) {
    vec3 p = fract(vec3(cell.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 f = fract(point);
    vec2 blend = f * f * (3.0 - 2.0 * f);
    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, blend.x), mix(c, d, blend.x), blend.y);
}

float flicker(vec2 point, float time) {
    float index = floor(time);
    float blend = smoothstep(0.0, 1.0, fract(time));
    vec2 jump = vec2(37.17, 11.83);
    return mix(valueNoise(point + jump * index), valueNoise(point + jump * (index + 1.0)), blend);
}

void main() {
    vec2 point = (texCoord - vec2(0.5)) * 2.0;
    float distanceFromCenter = length(point);
    vec2 direction = point / max(distanceFromCenter, 1.0e-4);

    float core = exp(-distanceFromCenter * 12.0) * 3.0;
    float breathing = 0.68 + 0.05 * sin(Time * 0.45) + 0.02 * sin(Time * 1.7 + 1.3);
    float halo = exp(-distanceFromCenter * 3.5) * breathing;

    float petals = flicker(direction * 2.6 + 3.0, Time * 0.08);
    float fibres = 0.7 + 0.3 * flicker(direction * 30.0, Time * 0.2);
    float streamers = pow(petals, 2.5) * fibres
            * exp(-distanceFromCenter * (2.0 + 2.5 * (1.0 - petals))) * 1.2
            * smoothstep(0.05, 0.3, distanceFromCenter);

    if (distanceFromCenter > 1.0) discard;
    float radialMask = 1.0 - smoothstep(0.6, 1.0, distanceFromCenter);
    vec3 coreColor = mix(vec3(1.0), SunTint, smoothstep(0.0, 0.35, distanceFromCenter));
    vec3 color = (coreColor * core + SunTint * (halo + streamers)) * radialMask;

    float alpha = max(color.r, max(color.g, color.b)) * FlareAlpha * vertexColor.a;
    if (alpha <= 0.001) discard;
    fragColor = vec4(color, alpha);
}