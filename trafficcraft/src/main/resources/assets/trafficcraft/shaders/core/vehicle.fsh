#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float Gloss;
uniform float Emissive;
uniform float PaintMask;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec2 matcapCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    // paint: texels with alpha < 1 (240) are trim/badges and keep their own colour instead of the car colour
    float keep = (PaintMask > 0.5 && color.a < 0.985) ? 1.0 : 0.0;
    vec4 tint = vec4(mix(ColorModulator.rgb, vec3(1.0), keep), ColorModulator.a);
    if (PaintMask > 0.5) color.a = 1.0;
    color *= vertexColor * tint;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    if (Gloss > 0.0) {
        vec3 refl = texture(Sampler0, matcapCoord).rgb * Gloss * (1.0 - 0.75 * keep);
        color.rgb += refl * lightMapColor.rgb;
        color.a = min(1.0, color.a + Gloss * 0.25 * (refl.r + refl.g + refl.b));
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
