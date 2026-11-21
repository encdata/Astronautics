uniform sampler2D DiffuseSampler0;
uniform sampler2D SceneDepthSampler;
uniform vec4 StarScreen0; uniform vec4 StarColor0;
uniform vec4 StarScreen1; uniform vec4 StarColor1;
uniform vec4 StarScreen2; uniform vec4 StarColor2;
uniform vec4 StarScreen3; uniform vec4 StarColor3;
uniform vec4 StarScreen4; uniform vec4 StarColor4;
uniform vec4 StarScreen5; uniform vec4 StarColor5;
uniform vec4 StarScreen6; uniform vec4 StarColor6;
uniform vec4 StarScreen7; uniform vec4 StarColor7;
in vec2 texCoord;
out vec4 fragColor;

const int RAY_COUNT = 12;
const float PI = 3.14159265359;

float hash21(vec2 value) {
    return fract(sin(dot(value, vec2(41.279, 289.317))) * 43758.5453);
}

vec3 raysForStar(vec2 uv, vec4 screen, vec4 color) {
    if (screen.w <= 0.001 || screen.x < 0.0 || screen.x > 1.0 || screen.y < 0.0 || screen.y > 1.0)
        return vec3(0.0);
    if (texture(SceneDepthSampler, screen.xy).r < 0.99995) return vec3(0.0);

    vec2 delta = uv - screen.xy;
    delta.x *= float(textureSize(DiffuseSampler0, 0).x) / float(textureSize(DiffuseSampler0, 0).y);
    float distanceFromStar = length(delta);
    float radius = max(screen.z, 0.0015);
    float nearestLine = 1.0;
    float rotation = hash21(screen.xy + color.xy) * PI;

    for (int ray = 0; ray < RAY_COUNT; ray++) {
        float angle = rotation + float(ray) * PI / float(RAY_COUNT);
        vec2 direction = vec2(cos(angle), sin(angle));
        nearestLine = min(nearestLine, abs(delta.x * direction.y - delta.y * direction.x));
    }

    float core = exp(-distanceFromStar * distanceFromStar / (radius * radius * 2.4));
    float outsideCore = smoothstep(radius * 0.55, radius * 1.75, distanceFromStar);
    float line = exp(-nearestLine / max(radius * 0.075, 0.00018));
    float falloff = exp(-distanceFromStar / max(radius * 18.0, 0.035));
    float rays = line * falloff * outsideCore;
    return color.rgb * screen.w * (core * 0.65 + rays * 0.23);
}

void main() {
    vec3 radiance = vec3(0.0);
    radiance += raysForStar(texCoord, StarScreen0, StarColor0);
    radiance += raysForStar(texCoord, StarScreen1, StarColor1);
    radiance += raysForStar(texCoord, StarScreen2, StarColor2);
    radiance += raysForStar(texCoord, StarScreen3, StarColor3);
    radiance += raysForStar(texCoord, StarScreen4, StarColor4);
    radiance += raysForStar(texCoord, StarScreen5, StarColor5);
    radiance += raysForStar(texCoord, StarScreen6, StarColor6);
    radiance += raysForStar(texCoord, StarScreen7, StarColor7);
    fragColor = vec4(radiance, 1.0);
}
