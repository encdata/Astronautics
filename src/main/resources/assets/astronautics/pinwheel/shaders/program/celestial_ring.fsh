uniform sampler2D Sampler0;
uniform vec3 SunDirection;
uniform vec3 BodyRenderPosition;
uniform float BodyRadius;
uniform float RingTransmission;
in vec2 texCoord0;
in vec4 vertexColor;
in vec3 surfaceNormal;
in vec3 renderPosition;
out vec4 fragColor;

float parentShadow(vec3 origin, vec3 direction) {
    vec3 toCenter = -origin;
    float along = dot(toCenter, direction);
    if (along <= 0.0) return 1.0;
    float miss = length(toCenter - direction * along);
    return miss < BodyRadius ? 0.0 : 1.0;
}

void main() {
    vec4 ring = texture(Sampler0, texCoord0);
    if (ring.a * vertexColor.a < 0.03) discard;
    vec3 lightDirection = normalize(SunDirection);
    float faceToSun = dot(normalize(surfaceNormal), lightDirection);
    float frontLight = max(faceToSun, 0.0);
    float backLight = max(-faceToSun, 0.0) * RingTransmission;
    float density = max(ring.a, dot(ring.rgb, vec3(0.299, 0.587, 0.114)));
    float opticalDepth = max(density, 0.02);
    float incidence = max(abs(faceToSun), 0.08);
    float transmission = exp(-opticalDepth / incidence);
    float directLight = frontLight * (1.0 - transmission) + backLight * transmission;
    float parentOcclusion = parentShadow(renderPosition - BodyRenderPosition, lightDirection);
    fragColor = vec4(ring.rgb * directLight * parentOcclusion, vertexColor.a);
}
