#version 150

// Lays a half-resolution layer (premultiplied colour) over the screen, smoothly filtered.
uniform sampler2D Sampler0;

in vec2 ab;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, ab);
}
