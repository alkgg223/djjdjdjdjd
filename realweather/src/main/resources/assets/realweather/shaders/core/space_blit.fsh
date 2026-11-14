#version 150

// Puts a ray-traced picture worked out at a lower resolution onto the screen (its colour already premultiplied).
uniform sampler2D Sampler0;

in vec2 ab;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, ab);
}
