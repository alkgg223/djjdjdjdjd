#version 150

// Night vision (image intensifier): the scene's brightness is amplified hard, dark parts lifted the most, shown
// as green phosphor that runs into white where it saturates, with the grain of the tube, faint lines and a vignette.

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
    vec3 glow = texture(DiffuseSampler, texCoord + vec2(oneTexel.x * 3.0, 0.0)).rgb
              + texture(DiffuseSampler, texCoord - vec2(oneTexel.x * 3.0, 0.0)).rgb
              + texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y * 3.0)).rgb
              + texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y * 3.0)).rgb;
    float l = dot(mix(c, glow * 0.25, 0.35), vec3(0.299, 0.587, 0.114));
    l = 1.0 - exp(-l * 5.5);
    float grain = hash(floor(texCoord / oneTexel) + fract(Time * 7.0) * 113.0) - 0.5;
    l = clamp(l + grain * 0.14, 0.0, 1.0);
    vec3 phosphor = vec3(0.22, 1.0, 0.34) * l;
    vec3 col = mix(phosphor, vec3(0.9, 1.0, 0.92), smoothstep(0.72, 1.0, l));
    col *= 0.95 + 0.05 * sin(texCoord.y / oneTexel.y * 1.6);
    vec2 d = texCoord - 0.5;
    col *= clamp(1.0 - dot(d, d) * 1.5, 0.0, 1.0);
    fragColor = vec4(col, 1.0);
}
