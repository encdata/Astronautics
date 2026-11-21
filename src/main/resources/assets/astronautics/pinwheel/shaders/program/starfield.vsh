#version 410 core
layout(location=0) in vec3 Direction;
layout(location=1) in vec3 SpectralColor;
layout(location=2) in vec3 StarData;
uniform mat4 SkyView;
uniform mat4 SkyRotation;
uniform mat4 Projection;
uniform vec2 Viewport;
uniform float Atmosphere;
uniform float SunElevation;
uniform float SkyTime;
uniform float Twinkle;
uniform float BrightGlow;
out vec2 PointUV;
out vec3 StarColor;
out float StarFlux;
out float Glow;
out float Footprint;
float visibility(float elevation) {
    if (Atmosphere <= 0.0) return 1.0;
    float airMass = inversesqrt(max(elevation * elevation + 0.025, 0.025));
    float daylight = smoothstep(-0.18, 0.10, SunElevation);
    return smoothstep(-0.03, 0.08, elevation)
        * exp(-Atmosphere * (0.28 * airMass + daylight * 14.0));
}
void main() {
    const vec2 corners[6] = vec2[6](vec2(-1,-1),vec2(1,-1),vec2(1,1),
                                    vec2(-1,-1),vec2(1,1),vec2(-1,1));
    vec3 viewDirection = (SkyView * vec4(Direction, 0.0)).xyz;
    vec4 clip = Projection * vec4(viewDirection, 0.0);
    float magnitude = StarData.x;
    Glow = (1.0 - smoothstep(0.0, 0.8, magnitude)) * BrightGlow;


    Footprint = max(0.65, StarData.y + Glow * 1.0);
    PointUV = corners[gl_VertexID];
    clip.xy += PointUV * Footprint / max(Viewport, vec2(1.0)) * clip.w;
    clip.z = clip.w * 0.999999;
    if (viewDirection.z >= -0.001) clip = vec4(2,2,2,1);
    gl_Position = clip;
    float elevation = (SkyRotation * vec4(Direction, 0.0)).y;
    float attenuation = visibility(elevation);
    float twinkle = 1.0;
    if (Atmosphere > 0.001 && Twinkle > 0.0) {
        twinkle += 0.025 * min(Atmosphere, 1.0) * (1.0 - clamp(elevation,0.0,1.0))
            * sin(SkyTime * (1.9 + StarData.z * .1) + StarData.z);
    }
    StarFlux = min(2.0, 24.0 * pow(10.0, -0.4 * magnitude)) * attenuation * twinkle;
    StarFlux *= min(1.0, StarData.y * StarData.y);
    StarColor = SpectralColor;
}
