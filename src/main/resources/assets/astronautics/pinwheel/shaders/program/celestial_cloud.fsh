uniform sampler2D Sampler0;
uniform vec3 SunDirection;
uniform float SunIrradiance;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 surfaceNormal;
in vec3 renderPosition;

out vec4 fragColor;

void main() {
    vec4 cloud = texture(Sampler0, texCoord0);
    float luminance = dot(cloud.rgb, vec3(0.299, 0.587, 0.114));
    if (luminance < 0.20) discard;
    float density = smoothstep(0.28, 0.72, luminance);
    float alpha = density * vertexColor.a * 0.72;
    if (alpha < 0.01) discard;



    gl_FragDepth = max(gl_FragCoord.z - 0.00001, 0.0);
    vec3 normal = normalize(surfaceNormal);
    vec3 sun = normalize(SunDirection);
    vec3 view = normalize(-renderPosition);
    float direct = max(dot(normal, sun), 0.0);
    float forwardScatter = pow(max(dot(view, sun), 0.0), 8.0) * density * 0.25;
    vec3 radiance = vec3(0.92, 0.95, 1.0) * (direct + forwardScatter) * SunIrradiance;
    fragColor = vec4(radiance, alpha);
}
