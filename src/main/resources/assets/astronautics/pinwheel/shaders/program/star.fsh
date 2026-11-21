uniform vec3 StarColor;
uniform float StarIntensity;
in vec2 texCoord0;
in vec4 vertexColor;
in vec3 surfaceNormal;
out vec4 fragColor;

float hash21(vec2 value) {
    return fract(sin(dot(value, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    float cells = sin(texCoord0.x * 47.0 + sin(texCoord0.y * 29.0) * 3.0)
            * sin(texCoord0.y * 39.0 + sin(texCoord0.x * 23.0) * 2.0);
    float granulation = 0.78 + 0.22 * cells + 0.08 * hash21(floor(texCoord0 * 96.0));
    float limb = 0.82 + 0.18 * pow(max(surfaceNormal.z, 0.0), 0.35);
    vec3 radiance = StarColor * StarIntensity * granulation * limb * 1.7;
    fragColor = vec4(radiance, vertexColor.a);
}
