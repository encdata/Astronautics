in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float wave = 0.94 + 0.06 * sin(texCoord.x * 2.7 + sin(texCoord.y * 1.9) * 1.4);
    fragColor = vec4(vertexColor.rgb * wave, vertexColor.a * wave);
}
