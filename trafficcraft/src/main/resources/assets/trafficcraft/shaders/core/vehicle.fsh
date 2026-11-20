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
uniform float Livery;
uniform vec4 LiveryDims;   // half length, half width, height, seed

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec2 matcapCoord;
in vec3 objPos;
in vec3 objNormal;

out vec4 fragColor;

// ------------------------------------------------------------------ liveries, drawn on the paint in Minecraft pixels (1/16 block)
float hash1(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

bool seg7(int d, vec2 q) {
    // q in a 1 x 2 cell, origin bottom-left; segments a..g
    bool h = abs(q.x - 0.5) < 0.38;
    bool v = q.x < 0.2 || q.x > 0.8;
    bool a = h && q.y > 1.82, g = h && abs(q.y - 1.0) < 0.16, dd = h && q.y < 0.18;
    bool f = q.x < 0.2 && q.y > 1.0, b = q.x > 0.8 && q.y > 1.0, e = q.x < 0.2 && q.y < 1.0, c = q.x > 0.8 && q.y < 1.0;
    int m[10] = int[10](0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F);
    int s = m[d];
    return ((s & 1) != 0 && a) || ((s & 2) != 0 && b) || ((s & 4) != 0 && c) || ((s & 8) != 0 && dd) || ((s & 16) != 0 && e) || ((s & 32) != 0 && f) || ((s & 64) != 0 && g);
}

vec4 livery(vec3 p, vec3 n) {
    vec3 q = floor(p * 16.0) / 16.0 + 1.0 / 32.0;
    float L = LiveryDims.x, W = LiveryDims.y, H = LiveryDims.z, seed = LiveryDims.w;
    bool side = abs(n.x) > 0.45, top = n.y > 0.55, endf = abs(n.z) > 0.55;
    int id = int(Livery + 0.5);
    if (id == 1) {                                   // twin racing stripes over the top, a thin line down each side
        float a = abs(q.x);
        if ((top || endf) && a > 0.07 && a < 0.21) return vec4(0.97, 0.97, 0.97, 1.0);
        if ((top || endf) && a > 0.24 && a < 0.27) return vec4(0.97, 0.97, 0.97, 1.0);
        if (side && abs(q.y - H * 0.3) < 0.035) return vec4(0.97, 0.97, 0.97, 1.0);
    } else if (id == 2) {                            // flames licking back from the front wheels and over the bonnet
        float d = L * 0.92 - q.z;
        float lane = side ? q.y : abs(q.x) * 1.6 + H * 0.3;
        float row = floor(lane * 10.0);
        float len = 0.7 + 1.5 * hash1(vec3(row, seed, 3.0)) + 0.35 * sin(lane * 17.0);
        float y0 = side ? H * 0.47 : H * 0.42;
        bool band = side ? abs(q.y - y0) < H * 0.17 : (top && abs(q.x) < W * 0.75);
        if (band && d > 0.0 && d < len) {
            float t = d / len;
            if (t > 0.92) return vec4(0.1, 0.05, 0.05, 1.0);
            return t < 0.35 ? vec4(1.0, 0.9, 0.25, 1.0) : t < 0.7 ? vec4(1.0, 0.5, 0.06, 1.0) : vec4(0.85, 0.12, 0.05, 1.0);
        }
    } else if (id == 3) {                            // race number in a white roundel on the doors and bonnet, contrast band
        vec2 c = side ? vec2(q.z - L * 0.05, q.y - H * 0.47) : vec2(q.x, q.z - L * 0.6);
        float r = side ? H * 0.2 : W * 0.34;
        if ((side || top) && length(c) < r) {
            int num = int(mod(seed, 89.0)) + 10;
            vec2 u = c / (r * 0.62);
            vec2 q1 = vec2((u.x + 1.05) * 1.0, u.y + 1.0), q2 = vec2((u.x - 0.05) * 1.0, u.y + 1.0);
            if (side && n.x < 0.0) { q1.x = 1.0 - (u.x + 1.05) + 1.0; q2.x = 1.0 - (u.x - 0.05); }
            bool ink = (q1.x > 0.0 && q1.x < 1.0 && q1.y > 0.0 && q1.y < 2.0 && seg7(num / 10, q1)) || (q2.x > 0.0 && q2.x < 1.0 && q2.y > 0.0 && q2.y < 2.0 && seg7(num % 10, q2));
            return ink ? vec4(0.06, 0.06, 0.07, 1.0) : vec4(0.97, 0.97, 0.97, 1.0);
        }
        if (side && q.y < H * 0.32) return vec4(0.08, 0.08, 0.1, 1.0);
    } else if (id == 4) {                            // pixel camouflage
        float v = hash1(floor(q * 4.0) + seed) * 0.65 + hash1(floor(q * 9.0) + seed) * 0.35;
        return v < 0.3 ? vec4(0.24, 0.29, 0.17, 1.0) : v < 0.55 ? vec4(0.43, 0.45, 0.3, 1.0) : v < 0.8 ? vec4(0.33, 0.25, 0.16, 1.0) : vec4(0.12, 0.13, 0.1, 1.0);
    } else if (id == 5) {                            // shark teeth along the sills and a black bonnet
        if (side && q.y < H * 0.2 + H * 0.18 * abs(fract(q.z * 1.6 + 0.5) * 2.0 - 1.0)) return vec4(0.05, 0.05, 0.06, 1.0);
        if (side && q.y < H * 0.2 + H * 0.18 * abs(fract(q.z * 1.6 + 0.5) * 2.0 - 1.0) + 0.04) return vec4(0.95, 0.1, 0.12, 1.0);
        if (top && q.z > L * 0.35) return vec4(0.05, 0.05, 0.06, 1.0);
    } else if (id == 6) {                            // chequered flag over the rear half
        if (q.z < 0.0) {
            float k = mod(floor(q.x * 6.0) + floor(q.y * 6.0) + floor(q.z * 6.0), 2.0);
            float fade = clamp(-q.z / L * 2.5, 0.0, 1.0);
            if (hash1(floor(q * 6.0)) < fade) return k < 0.5 ? vec4(0.05, 0.05, 0.06, 1.0) : vec4(0.97, 0.97, 0.97, 1.0);
        }
    } else if (id == 7) {                            // Halloween: pumpkin orange with carved faces, vines
        vec3 org = vec3(0.95, 0.45, 0.05), blk = vec3(0.06, 0.04, 0.02);
        vec2 c = side ? vec2((n.x > 0.0 ? 1.0 : -1.0) * (q.z - L * 0.05), q.y - H * 0.46) : top ? vec2(q.x, q.z - L * 0.62) : vec2(q.x, q.y - H * 0.4);
        float s = side ? H * 0.22 : W * 0.36;
        vec2 u = c / s;
        bool eye = (abs(u.x - 0.42) + max(0.0, -(u.y - 0.32)) * 0.0 < 0.25 && u.y > 0.18 && u.y < 0.18 + 0.55 * (0.25 - abs(u.x - 0.42)) / 0.25 * 0.6)
                || (abs(u.x + 0.42) < 0.25 && u.y > 0.18 && u.y < 0.18 + 0.55 * (0.25 - abs(u.x + 0.42)) / 0.25 * 0.6);
        bool mouth = abs(u.x) < 0.7 && u.y < -0.12 && u.y > -0.5 + 0.12 * abs(fract(u.x * 3.0) * 2.0 - 1.0) && u.y < -0.12 - 0.1 * abs(fract(u.x * 3.0 + 0.5) * 2.0 - 1.0);
        bool nose = abs(u.x) < 0.1 && u.y > -0.05 && u.y < 0.1 - abs(u.x);
        if (length(u) < 1.15 && (eye || mouth || nose)) return vec4(1.0, 0.82, 0.2, 1.0);
        if (side && abs(q.y - (H * 0.22 + 0.05 * sin(q.z * 6.0))) < 0.025) return vec4(0.12, 0.3, 0.08, 1.0);
        if (abs(length(u) - 1.2) < 0.05) return vec4(blk, 1.0);
        return vec4(org, 1.0);
    } else if (id == 8) {                            // Christmas candy cane stripes
        float t = fract((q.z + q.y + abs(q.x) * 0.6) * 2.2);
        return t < 0.5 ? vec4(0.86, 0.06, 0.1, 1.0) : vec4(0.97, 0.97, 0.97, 1.0);
    }
    return vec4(0.0);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    // paint: texels with alpha < 1 (240) are trim/badges and keep their own colour instead of the car colour
    float keep = (PaintMask > 0.5 && color.a < 0.985) ? 1.0 : 0.0;
    vec4 tint = vec4(mix(ColorModulator.rgb, vec3(1.0), keep), ColorModulator.a);
    if (PaintMask > 0.5 && keep < 0.5 && Livery > 0.5) {
        vec4 lv = livery(objPos, normalize(objNormal));
        if (lv.a > 0.5) tint.rgb = lv.rgb;
    }
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
