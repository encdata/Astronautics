#version 410 core
uniform mat4 InverseProjection;
uniform mat4 InverseSkyView;
uniform mat4 SkyRotation;
uniform float Atmosphere;
uniform float SunElevation;
in vec2 ScreenUV;
out vec4 fragColor;
void main() {
    vec4 view = InverseProjection * vec4(ScreenUV * 2.0 - 1.0, 1.0, 1.0);
    vec3 direction = normalize((InverseSkyView * vec4(view.xyz,0.0)).xyz);
    float elevation = (SkyRotation * vec4(direction,0.0)).y;
    float attenuation = 1.0;
    if (Atmosphere > 0.0) {
        float airMass = inversesqrt(max(elevation*elevation+.025,.025));
        attenuation = smoothstep(-.03,.08,elevation) * exp(-Atmosphere *
            (.28*airMass + 14.0*smoothstep(-.18,.10,SunElevation)));
    }


    float cloud = 0.5 + 0.25 * sin(dot(direction, vec3(17.0, 31.0, 23.0)))
            + 0.25 * sin(dot(direction, vec3(-13.0, 7.0, 29.0)));
    float light=.0015*(.75+.25*cloud)*attenuation;
    fragColor=vec4(vec3(0.94,0.95,1.0)*light,0.0);
}
