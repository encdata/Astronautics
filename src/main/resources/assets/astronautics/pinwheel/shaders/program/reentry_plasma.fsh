in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

uniform float Time;
uniform float WrapLayer;
uniform float CellSize;

float hash(vec2 point) {
    point = fract(point * vec2(0.1031, 0.1030));
    point += dot(point, point.yx + 33.33);
    return fract((point.x + point.y) * point.x);
}

float noise(vec2 point) {
    vec2 cell = floor(point);
    vec2 blend = fract(point);
    blend = blend * blend * (3.0 - 2.0 * blend);

    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, blend.x), mix(c, d, blend.x), blend.y);
}

void main() {
    float axial = texCoord.x;
    float transverse = texCoord.y;

    vec2 flowUv = vec2(transverse * 0.14 + Time * 0.11,
            axial * 0.22 - Time * 1.45);
    float warp = noise(flowUv * 0.55 + vec2(Time * 0.06, -Time * 0.12));
    vec2 warpedUv = flowUv + vec2((warp - 0.5) * 0.9, (warp - 0.5) * 0.42);
    float broadNoise = noise(warpedUv);
    float fineNoise = noise(warpedUv * 2.7 + vec2(-Time * 0.17, Time * 0.29));
    float turbulence = smoothstep(0.14, 0.84, broadNoise * 0.58 + fineNoise * 0.42);
    float hotPocket = smoothstep(0.72, 0.96, fineNoise + broadNoise * 0.18);

    vec2 cellUv = texCoord / max(CellSize, 0.01);
    vec2 blockCell = floor(cellUv);
    vec2 withinBlock = fract(cellUv);
    float blockSeed = hash(blockCell + vec2(17.13, 47.77));
    float blockTexture = noise(blockCell * 0.41 + withinBlock * 1.4
            + vec2(Time * 0.22, -Time * 1.1));
    float blockPulse = 0.5 + 0.5 * sin(Time * 2.7 + blockSeed * 6.2831853 + blockTexture * 1.1);
    float opacityTexture = 0.52 + blockPulse * 0.3 + blockTexture * 0.18;

    float striation = noise(vec2(transverse * 0.6 + warp * 2.2 + Time * 0.06,
            axial * 0.36 - Time * 0.92));
    float streak = smoothstep(0.67, 0.94, striation) * smoothstep(0.36, 0.78, fineNoise);
    float heat = smoothstep(0.22, 0.87, broadNoise * 0.46 + fineNoise * 0.34 + hotPocket * 0.42);
    float flicker = 0.84 + 0.16 * sin(Time * 3.4 + broadNoise * 6.2831853);
    vec3 fireColor = mix(vec3(1.0, 0.2, 0.12), vec3(1.0, 0.91, 0.68), clamp(heat + hotPocket * 0.28, 0.0, 1.0));
    vec3 coreColor = mix(vertexColor.rgb, fireColor, heat * 0.48 + streak * 0.42)
            * (0.55 + broadNoise * 0.24 + streak * 0.9 + hotPocket * 0.65) * flicker;
    float coreAlpha = (0.09 + turbulence * 0.27 + streak * 0.1)
            * (0.58 + fineNoise * 0.52) * opacityTexture;

    vec3 wrapColor = mix(vertexColor.rgb, vec3(0.78, 0.25, 1.0), 0.42 + broadNoise * 0.22)
            * (0.27 + turbulence * 0.29 + streak * 0.24) * flicker;
    float wrapAlpha = (0.035 + turbulence * 0.17 + streak * 0.08)
            * (0.62 + broadNoise * 0.4) * (0.45 + opacityTexture * 0.55);

    fragColor = vec4(mix(coreColor, wrapColor, WrapLayer),
            mix(coreAlpha, wrapAlpha, WrapLayer) * vertexColor.a);
}
