#version 150

// A soft (Gaussian) blur along one direction.
uniform sampler2D Sampler0;
uniform vec2 Direction;

in vec2 ab;

out vec4 fragColor;

void main() {
    vec3 s = texture(Sampler0, ab).rgb * 0.2;
    s += (texture(Sampler0, ab + Direction * 1.4).rgb + texture(Sampler0, ab - Direction * 1.4).rgb) * 0.17;
    s += (texture(Sampler0, ab + Direction * 3.3).rgb + texture(Sampler0, ab - Direction * 3.3).rgb) * 0.12;
    s += (texture(Sampler0, ab + Direction * 5.6).rgb + texture(Sampler0, ab - Direction * 5.6).rgb) * 0.075;
    s += (texture(Sampler0, ab + Direction * 8.5).rgb + texture(Sampler0, ab - Direction * 8.5).rgb) * 0.035;
    fragColor = vec4(s * 1.25, 1.0);
}
