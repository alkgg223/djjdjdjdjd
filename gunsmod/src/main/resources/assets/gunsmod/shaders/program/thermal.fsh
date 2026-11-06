#version 150

// Thermal sight, white hot: brightness stands in for heat. Sky and shadow read cold (dark), lit ground mid grey, the
// glowing outlines of living things white. A little blur from the sensor, fixed-pattern noise, a soft vignette.

uniform sampler2D DiffuseSampler;
uniform float Time;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    vec3 b = (texture(DiffuseSampler, texCoord + vec2(oneTexel.x * 2.0, 0.0)).rgb
            + texture(DiffuseSampler, texCoord - vec2(oneTexel.x * 2.0, 0.0)).rgb
            + texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y * 2.0)).rgb
            + texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y * 2.0)).rgb) * 0.25;
    vec3 m = mix(c, b, 0.4);
    float l = dot(m, vec3(0.299, 0.587, 0.114));
    // warm colours (reds, oranges: fire, lava, skin) count hotter than cool blues (sky, water)
    float warm = clamp((m.r - m.b) * 0.9, -0.3, 0.6);
    float heat = clamp(l * 0.75 + warm * 0.5, 0.0, 1.0);
    heat = smoothstep(0.08, 0.95, heat);
    // the outline pass draws pure colour: anything that saturated stays white hot
    if (max(c.r, max(c.g, c.b)) > 0.97 && min(c.r, min(c.g, c.b)) > 0.85) heat = 1.0;
    float fpn = hash(floor(texCoord / oneTexel / 2.0)) - 0.5;
    float grain = hash(floor(texCoord / oneTexel) + fract(Time * 5.0) * 97.0) - 0.5;
    heat = clamp(heat + fpn * 0.04 + grain * 0.05, 0.0, 1.0);
    vec3 col = vec3(heat * 0.96, heat * 0.98, heat);
    vec2 d = texCoord - 0.5;
    col *= clamp(1.0 - dot(d, d) * 1.4, 0.0, 1.0);
    fragColor = vec4(col, 1.0);
}
