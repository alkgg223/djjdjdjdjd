#version 150

// Glows, haloes and thin cloud sheets in the sky: textured and tinted like position_tex_color, but without its
// alpha cut-off, so faint edges fade out smoothly instead of ending in a hard line.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
