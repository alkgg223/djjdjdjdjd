#version 150

// The face of a star seen close: its surface boils in granules (hot gas welling up in the middle of each cell and
// sinking, darker, at its edges), the granules come and go, larger supergranules ripple under them, sunspots sit
// in groups with their dark umbrae and streaky penumbrae, bright faculae show round them towards the limb, and the
// disc darkens and reddens to its edge. Rays in the star's frame, its radius 1.

uniform vec3 RayO;
uniform vec3 RayOx;
uniform vec3 RayOy;
uniform vec3 RayD;
uniform vec3 RayDx;
uniform vec3 RayDy;
uniform vec3 StarColor;
uniform float Time;
uniform float Pixel;
uniform float Cells;
uniform vec4 S0;
uniform vec4 S1;
uniform vec4 S2;
uniform vec4 S3;
uniform float Alpha;
uniform float Bright;

in vec2 ab;

out vec4 fragColor;

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

// Distance to the nearest and second nearest cell centre (the centres drifting about as granules are born and die),
// and a value for the nearest cell.
vec3 cells(vec3 x, float speed) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    float d1 = 8.0;
    float d2 = 8.0;
    float id = 0.0;
    for (int k = 0; k < 27; k++) {
        vec3 g = vec3(float(k % 3) - 1.0, float((k / 3) % 3) - 1.0, float(k / 9) - 1.0);
        vec3 h = hash33(i + g);
        vec3 c = g + 0.5 + 0.38 * sin(Time * speed * (0.6 + h.yzx) + 6.2831 * h) - f;
        float dd = dot(c, c);
        if (dd < d1) {
            d2 = d1;
            d1 = dd;
            id = h.x;
        } else if (dd < d2) {
            d2 = dd;
        }
    }
    return vec3(sqrt(d1), sqrt(d2), id);
}

float spot(vec3 n, vec4 s, inout float facula) {
    if (s.w <= 0.0) {
        return 1.0;
    }
    float a = acos(clamp(dot(n, s.xyz), -1.0, 1.0)) / s.w;
    facula += exp(-a * a * 0.12) * 0.5;
    if (a > 1.0) {
        return 1.0;
    }
    // The penumbra's streaks run out from the centre.
    vec3 t = normalize(cross(s.xyz, vec3(0.0, 1.0, 0.07)));
    vec3 b = cross(s.xyz, t);
    float ang = atan(dot(n, b), dot(n, t));
    float streak = 0.5 + 0.5 * sin(ang * 46.0 + a * 9.0);
    float pen = mix(0.42, 0.62, streak);
    float umbra = 0.12;
    return a < 0.42 ? mix(umbra, pen, smoothstep(0.34, 0.42, a)) : mix(pen, 1.0, smoothstep(0.88, 1.0, a));
}

void main() {
    vec3 o = RayO + RayOx * ab.x + RayOy * ab.y;
    vec3 d = normalize(RayD + RayDx * ab.x + RayDy * ab.y);
    float b = dot(o, d);
    float c = dot(o, o) - 1.0;
    float h = b * b - c;
    if (h < 0.0) {
        discard;
    }
    float t = -b - sqrt(h);
    if (t < 0.0) {
        discard;
    }
    vec3 n = normalize(o + d * t);
    float mu = max(dot(n, -d), 0.0);
    // Granules: visible once each is a few pixels across.
    float size = 1.0 / Cells;
    float k = smoothstep(size * 0.9, size * 0.18, Pixel * t);
    float g = 1.0;
    if (k > 0.0) {
        vec3 w = cells(n * Cells, 0.05);
        float lane = smoothstep(0.0, 0.32, w.y - w.x);
        g = mix(1.0, mix(0.6, 1.12, lane) * (0.9 + 0.2 * w.z), k);
    }
    vec3 sg = cells(n * Cells * 0.14, 0.012);
    g *= 0.96 + 0.07 * smoothstep(0.0, 0.5, sg.y - sg.x);
    float facula = 0.0;
    float s = spot(n, S0, facula) * spot(n, S1, facula) * spot(n, S2, facula) * spot(n, S3, facula);
    // Limb darkening, and the limb redder.
    float limb = 1.0 - 0.62 * (1.0 - mu) - 0.16 * (1.0 - mu) * (1.0 - mu);
    vec3 col = StarColor * vec3(1.0, 0.86 + 0.14 * mu, 0.66 + 0.34 * mu);
    float fac = facula * pow(1.0 - mu, 2.0) * 0.6;
    vec3 lit = col * Bright * (limb * g * s + fac);
    fragColor = vec4(lit * Alpha, Alpha);
}
