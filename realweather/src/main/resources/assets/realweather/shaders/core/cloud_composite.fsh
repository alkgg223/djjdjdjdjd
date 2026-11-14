#version 150

// Lays the clouds (drawn at a lower resolution) over the scene: premultiplied colour and coverage.
uniform sampler2D Sampler0;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord);
}
