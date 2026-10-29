#version 150

#moj_import <fog.glsl>

// Real Weather: water reflects the sky at grazing angles (Fresnel), is clearer when seen from above,
// and shows small glints of the sun or moon on the moving waves.

#define WATER_ALPHA (179.0 / 255.0)
#define TAU 6.2831853

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
uniform vec4 RealWater;
uniform vec3 RealSun;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 lightColor;
in vec3 viewPos;
in vec3 localPos;
in vec3 faceNormal;
in float waterFlag;

out vec4 fragColor;

// Gradient of the vertex shader waves plus finer ripples that only shade.
vec2 waveSlope(vec2 p, float g, float rough) {
    vec2 s = vec2(0.0);
    s += cos(TAU * (p.x * 0.125 + p.y * 0.0625 + g * 250.0)) * 0.45 * TAU * vec2(0.125, 0.0625);
    s += cos(TAU * (p.x * -0.0625 + p.y * 0.1875 + g * 325.0)) * 0.30 * TAU * vec2(-0.0625, 0.1875);
    s += cos(TAU * (p.x * 0.25 + p.y * 0.25 + g * 500.0)) * 0.15 * TAU * vec2(0.25, 0.25);
    s += cos(TAU * (p.x * 0.3125 - p.y * 0.1875 + g * 590.0)) * 0.10 * TAU * vec2(0.3125, -0.1875);
    s *= 0.03 + 0.05 * rough;
    vec2 r = vec2(0.0);
    r += cos(TAU * (p.x * 1.25 + p.y * 0.75 + g * 1500.0)) * vec2(1.25, 0.75);
    r += cos(TAU * (p.x * -0.875 + p.y * 1.5 + g * 1800.0)) * vec2(-0.875, 1.5);
    r += cos(TAU * (p.x * 2.0 - p.y * 1.125 + g * 2300.0)) * vec2(2.0, -1.125) * 0.5;
    s += r * (0.006 + 0.012 * rough);
    return s;
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    vec4 color = tex * vertexColor * ColorModulator;
    if (waterFlag > 0.5 && RealWater.x > 0.5 && abs(tex.a - WATER_ALPHA) < 0.012 && abs(faceNormal.y) > 0.5) {
        vec2 slope = waveSlope(localPos.xz, GameTime, RealWater.y) * 3.0;
        vec3 n = normalize(vec3(-slope.x, 1.0, -slope.y));
        vec3 v = normalize(-viewPos);
        if (v.y < 0.0) {
            n.y = -n.y;
        }
        float light = max(lightColor.r, max(lightColor.g, lightColor.b));
        float ndv = clamp(abs(dot(n, v)), 0.0, 1.0);
        float fresnel = 0.02 + 0.98 * pow(1.0 - ndv, 5.0);
        vec3 sky = FogColor.rgb * 1.05 + vec3(0.02);
        color.rgb = mix(color.rgb, sky * light, fresnel * 0.7);
        color.a = mix(color.a * 0.8, 1.0, fresnel * 0.85);
        vec3 r = reflect(-v, n);
        float spec = pow(max(dot(r, normalize(RealSun)), 0.0), 240.0) * RealWater.z * light;
        color.rgb += vec3(1.0, 0.95, 0.82) * spec * 3.0;
        color.a = min(1.0, color.a + spec);
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
