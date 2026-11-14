#version 150

// The parts of the picture bright enough to bloom, kept with their colour.
uniform sampler2D Sampler0;
uniform float Threshold;

in vec2 ab;

out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, ab).rgb;
    float l = max(c.r, max(c.g, c.b));
    float k = smoothstep(Threshold, 1.0, l);
    fragColor = vec4(c * k, 1.0);
}
