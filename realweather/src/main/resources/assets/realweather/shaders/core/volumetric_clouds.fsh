#version 150

// Volumetric clouds, ray-marched. Three layers:
//  - low (raymarched): cumulus (flat bases, billowing tops), stratocumulus (a lumpy deck with breaks), stratus (a
//    flat grey sheet), nimbostratus (the thick rain deck) and cumulonimbus (towers up to an anvil);
//  - middle (a thin marched layer): altocumulus (rows of rounded cloudlets) and altostratus (a grey sheet);
//  - high (a sheet of ice cloud): cirrus streaks, cirrocumulus ripples and a cirrostratus veil with its 22-degree halo.
// The day's mix comes in as uniforms; on top of it every region of the sky gets its own share of each type. Each sample
// is lit by the sun through the cloud (Beer-Lambert, with the silver lining round the sun), by the sky, and by any
// lightning flashing inside the cloud. The scene depth stops the rays at the terrain.

uniform sampler2D Sampler0;
uniform mat4 InvViewProj;
uniform vec3 CameraPos;
uniform vec2 Drift;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform vec3 SkyColor;
uniform vec3 HazeColor;
uniform float Coverage;
uniform float Darkness;
uniform float CloudBase;
uniform float CloudTop;
uniform float CloudTime;
uniform float FarFade;
uniform float Steps;
uniform float LightSteps;
uniform float Detail;
uniform vec4 TypesLow;   // cumulus, stratocumulus, stratus, cumulonimbus
uniform vec4 TypesHigh;  // altocumulus, altostratus, cirrus, cirrocumulus
uniform vec2 TypesVeil;  // cirrostratus, (unused)
uniform vec2 DayOffset;
uniform vec4 Flash0;
uniform vec4 Flash1;
uniform vec4 Flash2;
uniform vec4 Flash3;
uniform vec4 FlashPower;
// Storm cells: x, z relative to the camera, radius, strength (0 = none).
uniform vec4 Storm0;
uniform vec4 Storm1;
uniform vec4 Storm2;
uniform vec4 Storm3;
uniform vec4 Storm4;
uniform vec4 Storm5;
uniform vec4 Storm6;
uniform vec4 Storm7;
uniform float StormBase;

in vec2 texCoord;

out vec4 fragColor;

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float s = vnoise(p) * 0.5;
    p = p * 2.03 + vec3(1.7, 9.2, 3.1);
    s += vnoise(p) * 0.25;
    p = p * 2.01 + vec3(8.3, 2.8, 4.7);
    s += vnoise(p) * 0.125;
    return s / 0.875;
}

float fbm2(vec3 p) {
    float s = vnoise(p) * 0.66;
    s += vnoise(p * 2.07 + vec3(5.1, 1.3, 7.7)) * 0.34;
    return s;
}

// Per-ray share of each low cloud type in this part of the sky (types vary from region to region).
vec4 wLow;
vec4 wHigh;
float wVeil;
float lowBottom;
float lowTop;
bool storms;
vec4 stormCells[8];

// Thunderstorms: a great tower with a flat dark base, its top spreading out into the anvil, and a curtain of rain
// hanging under it. `shaft` returns the rain separately (it isn't eroded like the cloud).
float stormDensity(vec3 wp, out float shaft) {
    shaft = 0.0;
    float d = 0.0;
    vec3 rel = wp - CameraPos;
    for (int i = 0; i < 8; i++) {
        vec4 c = stormCells[i];
        if (c.w <= 0.01) {
            continue;
        }
        vec2 o = rel.xz - c.xy;
        float r = length(o) / c.z;
        if (r > 1.9) {
            continue;
        }
        if (wp.y < StormBase) {
            // Rain shafts under the core.
            if (wp.y > 62.0) {
                float curtain = (1.0 - smoothstep(0.25, 0.65, r)) * smoothstep(62.0, 80.0, wp.y);
                shaft = max(shaft, curtain * 0.11 * c.w);
            }
            continue;
        }
        float hh = (wp.y - StormBase) / (660.0 - StormBase);
        if (hh > 1.0) {
            continue;
        }
        // The tower narrows a little going up and bulges in big turrets; the anvil fans out at the top.
        float bulge = vnoise(vec3(o / (c.z * 0.35), hh * 4.0)) * 0.35;
        float body = (1.0 - smoothstep(0.45 + bulge, 0.85 + bulge, r * (1.0 + hh * 0.25))) * smoothstep(0.0, 0.035, hh)
                * (1.0 - smoothstep(0.78, 0.9, hh));
        float anvil = (1.0 - smoothstep(1.1, 1.85, r)) * smoothstep(0.8, 0.88, hh) * (1.0 - smoothstep(0.94, 1.0, hh));
        // A ragged, broken shelf of cloud round the base.
        float rag = vnoise(vec3(o / (c.z * 0.18), 3.0));
        float shelf = (1.0 - smoothstep(0.6 + rag * 0.5, 1.2 + rag * 0.4, r)) * smoothstep(0.0, 0.03, hh) * (1.0 - smoothstep(0.03, 0.09, hh))
                * clamp((rag - 0.35) * 2.5, 0.0, 1.0) * 0.6;
        d = max(d, max(max(body, anvil * 0.75), shelf) * c.w);
    }
    return d;
}

float lowDensity(vec3 wp, bool full) {
    if (wp.y <= lowBottom || wp.y >= lowTop) {
        return 0.0;
    }
    vec3 p = vec3(wp.x + Drift.x, wp.y, wp.z + Drift.y);
    float d = 0.0;
    float h = (wp.y - CloudBase) / (CloudTop - CloudBase);
    float base = 0.0;
    if (wLow.x + wLow.y + Darkness > 0.01 && h > 0.0 && h < 1.0) {
        base = fbm3(p * vec3(1.0 / 230.0, 1.0 / 170.0, 1.0 / 230.0));
        // Cumulus: separate cells, flat bases, rounded tops towering where the cell is dense.
        float cov = clamp((base - (1.0 - Coverage)) / max(0.08, Coverage) * 1.6, 0.0, 1.0);
        float topH = 0.22 + 0.78 * cov;
        float cu = cov * smoothstep(0.0, 0.06, h) * (1.0 - smoothstep(topH * 0.55, topH, h));
        // Stratocumulus: a lumpy, flattened deck low in the layer, broken into big rolls.
        float rolls = fbm2(p * vec3(1.0 / 420.0, 1.0 / 60.0, 1.0 / 160.0));
        float sc = clamp((base * 0.55 + rolls * 0.6 - 0.48) * 3.0, 0.0, 1.0) * smoothstep(0.0, 0.05, h) * (1.0 - smoothstep(0.16, 0.3, h));
        // Nimbostratus: the thick rain deck with a ragged base.
        float ns = smoothstep(0.0, 0.12, h) * (1.0 - smoothstep(0.5, 0.92, h)) * (0.55 + 0.45 * base);
        d = max(cu * wLow.x, sc * wLow.y);
        d = mix(d, max(d, ns), Darkness);
    }
    // Stratus: a flat grey sheet low down, nearly featureless.
    if (wLow.z > 0.01 && wp.y > 118.0 && wp.y < 150.0) {
        float hs = (wp.y - 118.0) / 32.0;
        float st = smoothstep(0.0, 0.25, hs) * (1.0 - smoothstep(0.6, 1.0, hs)) * (0.55 + 0.25 * vnoise(p / 140.0));
        d = max(d, st * wLow.z * 0.8);
    }
    // Cumulonimbus: a few great towers right up to an anvil spreading out at the top.
    if (wLow.w > 0.01 && wp.y > CloudBase) {
        float cell = vnoise(vec3((p.x + DayOffset.x) / 900.0, 0.0, (p.z + DayOffset.y) / 900.0));
        float tower = smoothstep(0.7, 0.82, cell);
        float hc = (wp.y - CloudBase) / (690.0 - CloudBase);
        float column = tower * (1.0 - smoothstep(0.82, 1.0, hc)) * smoothstep(0.0, 0.03, hc);
        float anvil = smoothstep(0.6, 0.72, cell) * smoothstep(0.8, 0.88, hc) * (1.0 - smoothstep(0.95, 1.0, hc));
        d = max(d, (column * 0.95 + anvil * 0.7) * wLow.w);
    }
    float shaft = 0.0;
    if (storms) {
        d = max(d, stormDensity(wp, shaft));
    }
    if (d <= 0.002) {
        return shaft;
    }
    if (full) {
        float det = fbm2(p / 31.0 + vec3(0.0, -CloudTime * 0.004, CloudTime * 0.002));
        d -= (1.0 - det) * (0.42 - 0.1 * Darkness) * (1.0 - 0.4 * clamp(h, 0.0, 1.0));
        if (Detail > 0.5) {
            float fine = vnoise(p / 9.5 + vec3(CloudTime * 0.006));
            d -= (1.0 - fine) * 0.1;
        }
    }
    return clamp(d * 1.4, 0.0, 1.0) + shaft;
}

float hg(float c, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * c, 1.5);
}

vec3 flashLight(vec3 rel) {
    vec3 s = vec3(0.0);
    vec4 f[4] = vec4[4](Flash0, Flash1, Flash2, Flash3);
    for (int i = 0; i < 4; i++) {
        float p = FlashPower[i];
        if (p > 0.01) {
            vec3 d = rel - f[i].xyz;
            d.y *= 1.4;
            s += p * exp(-dot(d, d) / (f[i].w * f[i].w));
        }
    }
    return s * vec3(0.85, 0.9, 1.0) * 4.0;
}

// One layer between heights y0 and y1, marched in `n` steps; returns premultiplied colour and transmittance.
vec4 marchLow(vec3 dir, float tStart, float tEnd, float phase, int n) {
    float stepLen = max((tEnd - tStart) / float(n), 3.0);
    float t = tStart + stepLen * hash13(vec3(gl_FragCoord.xy, 17.0));
    float T = 1.0;
    vec3 col = vec3(0.0);
    int ls = int(LightSteps);
    for (int i = 0; i < 96; i++) {
        if (i >= n || t > tEnd) {
            break;
        }
        vec3 pos = CameraPos + dir * t;
        float d = lowDensity(pos, true);
        if (d > 0.003) {
            float ld = 0.0;
            for (int j = 1; j <= 3; j++) {
                if (j > ls) {
                    break;
                }
                ld += lowDensity(pos + SunDir * (float(j) * float(j) * 9.0), false) * (float(j) * 9.0);
            }
            float shadow = exp(-ld * 0.055) * 0.85 + 0.15 * exp(-ld * 0.012);
            float h = clamp((pos.y - CloudBase) / (CloudTop - CloudBase), 0.0, 1.0);
            vec3 ambient = SkyColor * (0.5 + 0.6 * h) * (1.0 - 0.45 * Darkness) + vec3(0.03);
            float powder = 1.0 - exp(-d * 4.0);
            vec3 light = SunColor * shadow * phase * (0.6 + 0.4 * powder) * 1.25 + ambient * 0.72 + flashLight(pos - CameraPos);
            float a = 1.0 - exp(-d * stepLen * 0.055);
            col += T * a * light;
            T *= 1.0 - a;
            if (T < 0.02) {
                break;
            }
        }
        t += stepLen;
    }
    return vec4(col, T);
}

// The middle layer (altocumulus, altostratus), 3800..5200 m up in real terms: 430..500 here.
float midDensity(vec3 wp) {
    float h = (wp.y - 430.0) / 70.0;
    if (h <= 0.0 || h >= 1.0) {
        return 0.0;
    }
    vec3 p = vec3(wp.x + Drift.x * 1.6, wp.y, wp.z + Drift.y * 1.6);
    float prof = smoothstep(0.0, 0.3, h) * (1.0 - smoothstep(0.6, 1.0, h));
    float pat = fbm2(vec3((p.x + DayOffset.x) / 700.0, 0.0, (p.z + DayOffset.y) / 700.0));
    float cells = vnoise(p / vec3(30.0, 18.0, 30.0)) * 0.7 + vnoise(p / 13.0) * 0.3;
    float ac = clamp((cells - 0.52) * 4.0, 0.0, 1.0) * smoothstep(0.35, 0.6, pat);
    float as_ = (0.55 + 0.45 * pat) * 0.32;
    return (ac * wHigh.x + as_ * wHigh.y) * prof;
}

void main() {
    // The nearest depth around this (low resolution) pixel: clouds don't bleed over the edges of hills and roofs.
    vec2 px = 1.5 / vec2(textureSize(Sampler0, 0));
    float depth = texture(Sampler0, texCoord).r;
    depth = min(depth, texture(Sampler0, texCoord + vec2(px.x, px.y)).r);
    depth = min(depth, texture(Sampler0, texCoord + vec2(-px.x, px.y)).r);
    depth = min(depth, texture(Sampler0, texCoord + vec2(px.x, -px.y)).r);
    depth = min(depth, texture(Sampler0, texCoord + vec2(-px.x, -px.y)).r);
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 wp = InvViewProj * ndc;
    vec3 rel = wp.xyz / wp.w;
    float sceneDist = depth >= 0.99999 ? 1.0e9 : length(rel);
    vec3 dir = normalize(rel);
    float camY = CameraPos.y;
    float cosT = dot(dir, SunDir);
    float phase = 0.5 + 0.35 * hg(cosT, 0.6) + 0.15 * hg(cosT, -0.25);

    // The mix of cloud types over this part of the sky (taken where the ray meets the low layer).
    float tProbe = abs(dir.y) > 1.0e-3 ? clamp((CloudBase - camY) / dir.y, 0.0, 6000.0) : 2000.0;
    vec2 at = (CameraPos.xz + dir.xz * tProbe + DayOffset) / 3200.0;
    float r1 = vnoise(vec3(at, 1.0)), r2 = vnoise(vec3(at * 1.3, 7.0)), r3 = vnoise(vec3(at * 0.8, 13.0)), r4 = vnoise(vec3(at * 1.1, 19.0));
    // Clumpy types (cumulus, towers, cloudlets, streaks) come and go from region to region; the sheets (stratocumulus,
    // stratus, altostratus, cirrostratus) only thin out here and there.
    wLow = TypesLow * vec4(smoothstep(0.2, 0.55, r1) * 1.3, 0.55 + 0.6 * smoothstep(0.3, 0.6, r2), 0.6 + 0.55 * smoothstep(0.3, 0.6, r3),
            smoothstep(0.4, 0.65, r4) * 1.6);
    wLow = clamp(wLow, 0.0, 1.0);
    wHigh = clamp(TypesHigh * vec4(smoothstep(0.25, 0.55, r2) * 1.4, 0.6 + 0.6 * smoothstep(0.2, 0.5, r3), smoothstep(0.15, 0.5, r4) * 1.4,
            smoothstep(0.3, 0.6, r1) * 1.4), 0.0, 1.0);
    wVeil = clamp(TypesVeil.x * (0.6 + 0.6 * smoothstep(0.2, 0.5, r1)), 0.0, 1.0);
    stormCells = vec4[8](Storm0, Storm1, Storm2, Storm3, Storm4, Storm5, Storm6, Storm7);
    storms = false;
    for (int i = 0; i < 8; i++) {
        storms = storms || stormCells[i].w > 0.01;
    }
    lowBottom = storms ? 62.0 : (wLow.z > 0.01 ? 118.0 : CloudBase);
    lowTop = (wLow.w > 0.01 || storms) ? 700.0 : CloudTop;

    vec3 total = vec3(0.0);
    float T = 1.0;
    float far = 0.0;
    float wsum = 0.0;

    // Low layer.
    if (abs(dir.y) > 1.0e-4 || (camY > lowBottom && camY < lowTop)) {
        float ta = abs(dir.y) > 1.0e-4 ? (lowBottom - camY) / dir.y : 0.0;
        float tb = abs(dir.y) > 1.0e-4 ? (lowTop - camY) / dir.y : 6000.0;
        float t0 = max(0.0, min(ta, tb)), t1 = min(max(ta, tb), min(sceneDist, 7000.0));
        if (t1 > t0) {
            int n = int(Steps) + (lowTop > 600.0 ? int(Steps * 0.5) : 0);
            vec4 r = marchLow(dir, t0, t1, phase, n);
            total += r.rgb;
            far += (1.0 - r.a) * (t0 + t1) * 0.5;
            wsum += 1.0 - r.a;
            T *= r.a;
        }
    }
    // Middle layer.
    if (T > 0.03 && wHigh.x + wHigh.y > 0.01 && abs(dir.y) > 1.0e-3) {
        float ta = (430.0 - camY) / dir.y, tb = (500.0 - camY) / dir.y;
        float t0 = max(0.0, min(ta, tb)), t1 = min(max(ta, tb), min(sceneDist, 9000.0));
        if (t1 > t0) {
            int n = Detail > 0.5 ? 6 : 3;
            float stepLen = (t1 - t0) / float(n);
            float t = t0 + stepLen * hash13(vec3(gl_FragCoord.xy, 3.0));
            vec3 col = vec3(0.0);
            float Tm = 1.0;
            for (int i = 0; i < 6; i++) {
                if (i >= n) {
                    break;
                }
                vec3 pos = CameraPos + dir * t;
                float d = midDensity(pos);
                if (d > 0.003) {
                    float sh = exp(-midDensity(pos + SunDir * 25.0) * 1.4);
                    vec3 light = SunColor * (0.55 + 0.45 * sh) * phase * 1.1 + SkyColor * 0.75;
                    float a = 1.0 - exp(-d * stepLen * 0.05);
                    col += Tm * a * light;
                    Tm *= 1.0 - a;
                }
                t += stepLen;
            }
            total += T * col;
            far += T * (1.0 - Tm) * (t0 + t1) * 0.5;
            wsum += T * (1.0 - Tm);
            T *= Tm;
        }
    }
    // High layer: one sheet of ice cloud at 760.
    if (T > 0.03 && wHigh.z + wHigh.w + wVeil > 0.01 && dir.y > 1.0e-3 && camY < 760.0) {
        float t = (760.0 - camY) / dir.y;
        if (t < min(sceneDist, 14000.0)) {
            vec2 q = CameraPos.xz + dir.xz * t + Drift * 2.5 + DayOffset;
            // Cirrus: strands combed out along the wind, hooked at the ends.
            vec2 wind = normalize(Drift + vec2(0.001, 0.0));
            vec2 qa = vec2(dot(q, wind), dot(q, vec2(-wind.y, wind.x)));
            float warp = vnoise(vec3(qa / 700.0, 3.0)) * 2.0 - 1.0;
            float warp2 = vnoise(vec3(qa / 300.0, 8.0)) * 2.0 - 1.0;
            vec2 qs = vec2(qa.x / 900.0 + warp2 * 0.6, qa.y / 120.0 + warp * 9.0);
            float strands = fbm2(vec3(qs, 5.0));
            // Each streak fades out along its length (the 'mares' tails').
            float tails = vnoise(vec3(qa.x / 500.0, qa.y / 160.0 + warp * 4.0, 2.0));
            float pat = vnoise(vec3(qa / 2600.0, 9.0));
            float ci = clamp((strands - 0.56) * 3.0, 0.0, 1.0) * smoothstep(0.3, 0.75, tails) * smoothstep(0.35, 0.65, pat) * wHigh.z * 0.8;
            // Cirrocumulus: a fine rippled grain of tiny cloudlets.
            float grain = vnoise(vec3(q / 11.0, 1.0)) * 0.6 + 0.4 * (0.5 + 0.5 * sin(qa.x / 9.0 + warp * 8.0));
            float cc = clamp((grain - 0.6) * 4.0, 0.0, 1.0) * smoothstep(0.45, 0.7, vnoise(vec3(q / 1400.0, 4.0))) * wHigh.w;
            // Cirrostratus: a thin, even veil.
            float cs = (0.45 + 0.35 * vnoise(vec3(q / 1800.0, 6.0))) * wVeil * 0.55;
            float a = clamp(ci * 0.75 + cc * 0.8 + cs, 0.0, 0.95);
            if (a > 0.003) {
                float phaseIce = 0.55 + 0.45 * hg(cosT, 0.75);
                vec3 light = SunColor * phaseIce * 1.15 + SkyColor * 0.85;
                // The 22-degree halo round the sun in a cirrostratus veil.
                float ang = acos(clamp(cosT, -1.0, 1.0));
                float halo = exp(-pow((ang - 0.384) / 0.014, 2.0)) * cs * 2.2;
                light += SunColor * vec3(1.0 + 0.2 * halo, 1.0, 1.0 - 0.2 * halo) * halo;
                total += T * a * light;
                far += T * a * t;
                wsum += T * a;
                T *= 1.0 - a;
            }
        }
    }

    float alpha = 1.0 - T;
    if (alpha < 0.002) {
        fragColor = vec4(0.0);
        return;
    }
    float dist = wsum > 0.0 ? far / wsum : 3000.0;
    float haze = 1.0 - exp(-dist / FarFade);
    total = mix(total, HazeColor * alpha, clamp(haze, 0.0, 1.0));
    float horizon = 1.0 - smoothstep(6000.0, 11000.0, dist);
    fragColor = vec4(total * horizon, alpha * horizon);
}
