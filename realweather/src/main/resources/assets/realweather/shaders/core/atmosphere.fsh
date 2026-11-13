#version 150

// The air of a planet, by single scattering: along each pixel's ray through the air, sunlight that reaches each
// point (dimmed by the air it crossed on the way, and cut off where the planet's own shadow falls) is scattered
// towards us, by the molecules (Rayleigh: blue sky, red sunsets) and by haze and dust (Mie: the glow round the sun).
// The same pass draws the thin bright line of the air round a planet seen from space and the whole sky seen from
// its ground. Lengths in planet radii.

uniform vec3 RayO;
uniform vec3 RayOx;
uniform vec3 RayOy;
uniform vec3 RayD;
uniform vec3 RayDx;
uniform vec3 RayDy;
uniform float Top;
uniform float ScaleH;
uniform vec3 Beta;
uniform float Mie;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform float Intensity;
uniform float Alpha;

in vec2 ab;

out vec4 fragColor;

vec2 sphere(vec3 o, vec3 d, float r) {
    float b = dot(o, d);
    float c = dot(o, o) - r * r;
    float h = b * b - c;
    if (h < 0.0) {
        return vec2(1.0e9, -1.0e9);
    }
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

float density(float r) {
    return exp(-max(r - 1.0, 0.0) / ScaleH);
}

// How much air the sunlight crosses to reach a point (a lot if the planet is in the way).
float sunDepth(vec3 p) {
    vec2 g = sphere(p, SunDir, 1.0);
    if (g.x > 0.0) {
        return 1.0e3;
    }
    vec2 t = sphere(p, SunDir, Top);
    float dt = max(t.y, 0.0) / 6.0;
    float s = 0.0;
    for (int i = 0; i < 6; i++) {
        s += density(length(p + SunDir * (dt * (float(i) + 0.5)))) * dt;
    }
    return s;
}

void main() {
    vec3 o = RayO + RayOx * ab.x + RayOy * ab.y;
    vec3 d = normalize(RayD + RayDx * ab.x + RayDy * ab.y);
    vec2 t = sphere(o, d, Top);
    if (t.y < 0.0 || t.x > t.y) {
        discard;
    }
    float t0 = max(t.x, 0.0);
    float t1 = t.y;
    vec2 g = sphere(o, d, 1.0);
    if (g.x > 0.0) {
        t1 = min(t1, g.x);
    }
    float dt = (t1 - t0) / 16.0;
    vec3 sumR = vec3(0.0);
    vec3 sumM = vec3(0.0);
    float depth = 0.0;
    vec3 ext = Beta + vec3(Mie * 1.1);
    for (int i = 0; i < 16; i++) {
        vec3 p = o + d * (t0 + dt * (float(i) + 0.5));
        float rho = density(length(p)) * dt;
        depth += rho * 0.5;
        vec3 att = exp(-ext * (depth + sunDepth(p)));
        depth += rho * 0.5;
        sumR += rho * att;
        sumM += rho * att;
    }
    float mu = dot(d, SunDir);
    float phaseR = 0.0596831 * (1.0 + mu * mu);
    float gg = 0.76;
    float phaseM = 0.1193662 * ((1.0 - gg * gg) * (1.0 + mu * mu)) / ((2.0 + gg * gg) * pow(1.0 + gg * gg - 2.0 * gg * mu, 1.5));
    vec3 col = Intensity * SunColor * (sumR * Beta * phaseR + sumM * Mie * phaseM);
    col = vec3(1.0) - exp(-col);
    fragColor = vec4(col * Alpha, 0.0);
}
