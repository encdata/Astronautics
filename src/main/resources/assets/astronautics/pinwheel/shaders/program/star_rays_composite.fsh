uniform sampler2D DiffuseSampler0;
uniform sampler2D SceneSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec3 rays = texture(DiffuseSampler0, texCoord).rgb;
    fragColor = vec4(scene.rgb + rays, scene.a);
}
