#version 150

// Volumetric clouds: a ray march through a layer of real three-dimensional cloud. The cloud field is noise shaped
// into cumulus cells (flat bases, billowing rounded tops that tower where the cells are dense) or, in bad weather,
// a thick grey deck. Each sample is lit by sunlight that has come through the cloud towards the sun (Beer-Lambert,
// with the forward-scattering silver lining around the sun) and by the sky; distant cloud fades into the haze.
// The scene depth stops the rays at the terrain, so hills and buildings stand in front of the clouds.

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

// Density of cloud at a world position, 0..1. `full` adds the billowing detail (the light rays skip it).
float density(vec3 wp, bool full) {
    float h = (wp.y - CloudBase) / (CloudTop - CloudBase);
    if (h <= 0.0 || h >= 1.0) {
        return 0.0;
    }
    vec3 p = vec3(wp.x + Drift.x, wp.y, wp.z + Drift.y);
    float base = fbm3(p * vec3(1.0 / 230.0, 1.0 / 170.0, 1.0 / 230.0));
    float cov = clamp((base - (1.0 - Coverage)) / max(0.08, Coverage) * 1.6, 0.0, 1.0);
    // Cumulus: a flat base and a rounded top that towers where the cell is dense.
    float topH = 0.22 + 0.78 * cov;
    float prof = smoothstep(0.0, 0.06, h) * (1.0 - smoothstep(topH * 0.55, topH, h));
    // Bad weather: a thick deck with a ragged base.
    float deck = smoothstep(0.0, 0.12, h) * (1.0 - smoothstep(0.5, 0.92, h)) * (0.55 + 0.45 * base);
    float d = mix(cov * prof, max(cov * prof, deck), Darkness);
    if (d <= 0.002) {
        return 0.0;
    }
    if (full) {
        float det = fbm2(p / 31.0 + vec3(0.0, -CloudTime * 0.004, CloudTime * 0.002));
        float fine = vnoise(p / 9.5 + vec3(CloudTime * 0.006));
        d -= (1.0 - det) * (0.42 - 0.1 * Darkness) * (1.0 - 0.4 * h);
        d -= (1.0 - fine) * 0.1;
    }
    return clamp(d * 1.4, 0.0, 1.0);
}

float hg(float c, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * c, 1.5);
}

void main() {
    float depth = texture(Sampler0, texCoord).r;
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 wp = InvViewProj * ndc;
    vec3 rel = wp.xyz / wp.w;
    float sceneDist = depth >= 0.99999 ? 1.0e9 : length(rel);
    vec3 dir = normalize(rel);

    float camY = CameraPos.y;
    float t0;
    float t1;
    if (abs(dir.y) < 1.0e-4) {
        if (camY < CloudBase || camY > CloudTop) {
            fragColor = vec4(0.0);
            return;
        }
        t0 = 0.0;
        t1 = 6000.0;
    } else {
        float ta = (CloudBase - camY) / dir.y;
        float tb = (CloudTop - camY) / dir.y;
        t0 = max(0.0, min(ta, tb));
        t1 = max(ta, tb);
    }
    t1 = min(t1, min(sceneDist, 7000.0));
    if (t1 <= t0) {
        fragColor = vec4(0.0);
        return;
    }
    int steps = int(Steps);
    float stepLen = max((t1 - t0) / Steps, 3.0);
    float t = t0 + stepLen * hash13(vec3(gl_FragCoord.xy, 17.0));
    float cosT = dot(dir, SunDir);
    // Forward scattering (the bright rim towards the sun) plus a little back scattering.
    float phase = 0.5 + 0.35 * hg(cosT, 0.6) + 0.15 * hg(cosT, -0.25);
    float T = 1.0;
    vec3 col = vec3(0.0);
    float distSum = 0.0;
    float weight = 0.0;
    for (int i = 0; i < 96; i++) {
        if (i >= steps || t > t1) {
            break;
        }
        vec3 pos = CameraPos + dir * t;
        float d = density(pos, true);
        if (d > 0.003) {
            float ld = 0.0;
            for (int j = 1; j <= 3; j++) {
                ld += density(pos + SunDir * (float(j) * float(j) * 9.0), false) * (float(j) * 9.0);
            }
            float shadow = exp(-ld * 0.055) * 0.85 + 0.15 * exp(-ld * 0.012);
            float h = clamp((pos.y - CloudBase) / (CloudTop - CloudBase), 0.0, 1.0);
            vec3 ambient = SkyColor * (0.5 + 0.6 * h) * (1.0 - 0.45 * Darkness) + vec3(0.03);
            float powder = 1.0 - exp(-d * 4.0);
            vec3 light = SunColor * shadow * phase * (0.6 + 0.4 * powder) * 1.25 + ambient * 0.72;
            float a = 1.0 - exp(-d * stepLen * 0.055);
            col += T * a * light;
            distSum += T * a * t;
            weight += T * a;
            T *= 1.0 - a;
            if (T < 0.02) {
                break;
            }
        }
        t += stepLen;
    }
    float alpha = 1.0 - T;
    if (alpha < 0.002) {
        fragColor = vec4(0.0);
        return;
    }
    float far = weight > 0.0 ? distSum / weight : t1;
    float haze = 1.0 - exp(-far / FarFade);
    col = mix(col, HazeColor * alpha, clamp(haze, 0.0, 1.0));
    // Far out at the horizon the clouds melt into the sky.
    float horizon = 1.0 - smoothstep(4500.0, 7000.0, far);
    fragColor = vec4(col * horizon, alpha * horizon);
}
