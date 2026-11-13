#version 150

// Crepuscular rays ("god rays"): sunlight streaming through gaps in the clouds, the trees and the land. Each pixel
// gathers light along the line towards the sun on the screen: bright open sky there lets light through, clouds,
// hills and leaves block it, so shafts of light and shadow fan out from the sun through the hazy air.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 SunPos;
uniform float Strength;
uniform vec3 SunColor;
uniform float Aspect;

in vec2 ab;

out vec4 fragColor;

void main() {
    vec2 uv = ab;
    vec2 delta = (SunPos - uv) / 64.0;
    vec2 p = uv + delta * fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float light = 0.0;
    float decay = 1.0;
    for (int i = 0; i < 64; i++) {
        p += delta;
        if (p.x > 0.0 && p.x < 1.0 && p.y > 0.0 && p.y < 1.0) {
            float sky = texture(Sampler1, p).r >= 0.99999 ? 1.0 : 0.0;
            vec3 c = texture(Sampler0, p).rgb;
            light += sky * smoothstep(0.4, 0.95, dot(c, vec3(0.333))) * decay;
        }
        decay *= 0.966;
    }
    light /= 64.0;
    float d = length((uv - SunPos) * vec2(Aspect, 1.0));
    vec3 rays = SunColor * light * Strength / (1.0 + d * 2.5);
    fragColor = vec4(rays, 1.0);
}
