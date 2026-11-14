#version 150

// A planet's rings. From far off, the light and dark rings and gaps (from the ring map) and the planet's shadow
// across them. Close up, the rings break up into their particles: chunks of ice and rock each going round the
// planet on its own orbit (the inner ones faster than the outer ones, as Kepler says), lit by the star on one side,
// with the planet's shadow falling on them. Rays in the planet's frame (its radius 1, the rings in the plane y = 0).

uniform sampler2D Sampler0;

uniform vec3 RayO;
uniform vec3 RayOx;
uniform vec3 RayOy;
uniform vec3 RayD;
uniform vec3 RayDx;
uniform vec3 RayDy;
uniform vec3 LightDir;
uniform float Inner;
uniform float Outer;
uniform float Time;
uniform float Pixel;
uniform vec3 Tint;
uniform float Alpha;
uniform float Spacing;

in vec2 ab;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 o = RayO + RayOx * ab.x + RayOy * ab.y;
    vec3 d = normalize(RayD + RayDx * ab.x + RayDy * ab.y);
    if (abs(d.y) < 1.0e-6) {
        discard;
    }
    float t = -o.y / d.y;
    if (t <= 0.0) {
        discard;
    }
    vec3 p = o + d * t;
    float r = length(p.xz);
    if (r < Inner || r > Outer) {
        discard;
    }
    // Behind the planet.
    float b = dot(o, d);
    float c = dot(o, o) - 1.0;
    float h = b * b - c;
    if (h > 0.0) {
        float tp = -b - sqrt(h);
        if (tp > 0.0 && tp < t) {
            discard;
        }
    }
    float u = (r - Inner) / (Outer - Inner);
    vec4 ring = textureLod(Sampler0, vec2(u, 0.5), 0.0);
    float dens = ring.a;
    // The planet's shadow.
    float b2 = dot(p, LightDir);
    float c2 = dot(p, p) - 1.0;
    float h2 = b2 * b2 - c2;
    float shadow = (h2 > 0.0 && -b2 - sqrt(h2) > 0.0) ? 0.06 : 1.0;
    // Lit side or the side away from the star (light coming through).
    float sunSide = sign(LightDir.y) == sign(o.y) ? 1.0 : 0.45;
    vec3 far = ring.rgb * Tint * shadow * sunSide;
    float foot = Pixel * t;
    float near = smoothstep(Spacing * 1.5, Spacing * 0.3, foot);
    vec3 col = far;
    float alpha = dens * 0.92;
    if (near > 0.0) {
        float lane = floor(r / Spacing);
        float cover = 0.0;
        vec3 pc = vec3(0.0);
        for (int li = -1; li <= 1; li++) {
            float ln = lane + float(li);
            float rr = (ln + 0.5) * Spacing;
            // Kepler: the angle swept grows as r^-1.5.
            float phi = atan(p.z, p.x) - Time * 0.02 * pow(rr, -1.5) + hash12(vec2(ln, 7.0)) * 6.2831;
            float s = phi * rr / Spacing;
            float cell = floor(s);
            for (int ci = -1; ci <= 1; ci++) {
                float cc = cell + float(ci);
                float hh = hash12(vec2(ln, cc));
                if (hh > dens * 1.15) {
                    continue;
                }
                float h2b = hash12(vec2(cc, ln + 31.0));
                float size = Spacing * (0.12 + 0.3 * h2b * h2b);
                vec2 centre = vec2((ln + 0.25 + 0.5 * hash12(vec2(ln + 3.0, cc))) * Spacing, (cc + 0.5) * Spacing);
                vec2 here = vec2(r, s * Spacing);
                float dist = length(here - centre);
                if (dist < size) {
                    float z = sqrt(1.0 - (dist / size) * (dist / size));
                    float lit = 0.35 + 0.65 * z;
                    vec3 rock = ring.rgb * Tint * (0.75 + 0.5 * hash12(vec2(cc * 1.7, ln)));
                    pc = rock * lit * shadow * sunSide;
                    cover = 1.0;
                }
            }
        }
        col = mix(col, pc, near);
        alpha = mix(alpha, cover, near);
    }
    if (alpha < 0.003) {
        discard;
    }
    fragColor = vec4(col * alpha * Alpha, alpha * Alpha);
}
