#version 450
layout(location=0) out vec3 vColor;
vec2 positions[3] = vec2[](
    vec2(0.0, -0.55),
    vec2(0.62, 0.48),
    vec2(-0.62, 0.48)
);
vec3 colors[3] = vec3[](
    vec3(0.12, 0.48, 0.78),
    vec3(0.18, 0.72, 0.52),
    vec3(0.92, 0.72, 0.30)
);
void main() {
    gl_Position = vec4(positions[gl_VertexIndex], 0.35, 1.0);
    vColor = colors[gl_VertexIndex];
}
