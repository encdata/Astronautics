#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;

uniform vec3 CenterRelative;
uniform vec3 AxisX;
uniform vec3 AxisY;
uniform vec3 AxisZ;

out vec3 fragLocalPos;

void main() {
    vec3 viewPosition = (VeilCamera.ViewMat * vec4(Position, 1.0)).xyz;
    vec3 offset = viewPosition - CenterRelative;
    fragLocalPos = vec3(dot(offset, AxisX), dot(offset, AxisY), dot(offset, AxisZ));
    gl_Position = VeilCamera.ProjMat * vec4(viewPosition, 1.0);
}
