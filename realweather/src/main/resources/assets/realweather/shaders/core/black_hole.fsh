#version 150

// A black hole, ray traced. Light from the far side of the sky (and from a galaxy straight behind it) is bent
// round the hole on its way to us: each pixel's ray is followed backwards through the curved space of a
// Schwarzschild black hole (lengths in units of its event horizon's radius, r = 1), using the exact bending of light
// rays for that geometry (d2x/dl2 = -1.5 h^2 x / r^5). Rays that come too close fall in: the shadow, 2.6 times the
// horizon. Rays that skim the photon sphere at r = 1.5 can loop round it, making the thin bright photon ring. The
// accretion disc (from the innermost stable orbit at r = 3 out to r = 15) is crossed by many rays several times, so
// its far side is seen bent up over the top of the hole and under it; its gas is hotter inwards, streaked as it
// orbits, brightened and blued where it comes towards us (Doppler beaming) and reddened deep in the hole's gravity.
// The galaxy straight behind is lensed into an Einstein ring; the stars behind are lensed too.

uniform vec2 Pc;
uniform vec2 Px;
uniform vec2 Py;
uniform vec3 DiskN;
uniform float Time;
uniform float Alpha;
uniform float Edge;
uniform float Exposure;
uniform float MaxSteps;

in vec2 ab;

out vec4 fragColor;

const float INNER = 3.0;
const float OUTER = 15.0;
const float BEHIND = 242.0;
const float GALAXY = 26.0;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise(p);
        p = p * 2.07 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

// Colour of hot gas at a temperature (1 is white-hot): deep red, orange, yellow-white, blue-white.
vec3 blackbody(float t) {
    vec3 c = mix(vec3(0.9, 0.18, 0.03), vec3(1.0, 0.62, 0.25), smoothstep(0.15, 0.6, t));
    c = mix(c, vec3(1.0, 0.93, 0.82), smoothstep(0.6, 1.1, t));
    return mix(c, vec3(0.7, 0.82, 1.0), smoothstep(1.1, 2.4, t));
}

// Light from the disc where the ray crosses it (q), and how opaque it is there.
vec3 disc(vec3 q, vec3 v, out float opac) {
    float rho = length(q);
    vec3 e1 = normalize(cross(DiskN, abs(DiskN.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 e2 = cross(DiskN, e1);
    float ang = atan(dot(q, e2), dot(q, e1));
    // Gas orbits faster inwards (Kepler), so the streaks wind up.
    float turn = ang - Time * 0.9 * pow(rho, -1.5);
    vec3 np = vec3(cos(turn) * 2.2, sin(turn) * 2.2, rho * 1.35);
    float streak = fbm(np) * 0.75 + fbm(np * vec3(1.0, 1.0, 3.4) + 4.0) * 0.5;
    float temp = pow(rho / INNER, -0.75) * pow(max(1.0 - sqrt(INNER / rho), 0.0), 0.25) * 2.4;
    vec3 tang = normalize(cross(DiskN, q));
    float beta = min(sqrt(0.5 / max(rho - 1.0, 0.5)), 0.7);
    float gam = inversesqrt(1.0 - beta * beta);
    float g = sqrt(max(1.0 - 1.0 / rho, 0.05)) / (gam * (1.0 + beta * dot(tang, normalize(v))));
    float fade = smoothstep(INNER - 0.05, INNER + 0.5, rho) * (1.0 - smoothstep(OUTER * 0.55, OUTER, rho));
    opac = clamp(fade * (0.5 + 0.45 * streak), 0.0, 0.93);
    float bright = pow(g, 3.0) * pow(temp, 2.2) * (0.35 + 1.3 * streak * streak);
    return blackbody(temp * g * 0.9) * bright * fade * 1.6;
}

// What lies beyond, along the ray as it leaves: a spiral galaxy far straight behind, and stars.
vec3 beyond(vec3 p, vec3 v) {
    vec3 col = vec3(0.0);
    if (v.z < -0.02) {
        float t = (-BEHIND - p.z) / v.z;
        vec2 s = (p.xy + v.xy * t) / GALAXY;
        float ca = cos(0.6);
        float sa = sin(0.6);
        vec2 g = vec2(s.x * ca - s.y * sa, (s.x * sa + s.y * ca) / 0.62);
        float r = length(g);
        float a = atan(g.y, g.x);
        float arms = 0.5 + 0.5 * cos(2.0 * a - 5.0 * log(r + 0.04));
        float knots = noise(vec3(g * 14.0, 1.0));
        float body = exp(-r * 2.4) * (0.25 + 0.75 * arms) * (0.7 + 0.5 * knots);
        float bulge = exp(-r * r * 30.0);
        col += vec3(0.5, 0.66, 1.0) * body * 1.2 + vec3(1.0, 0.82, 0.55) * bulge * 2.2;
    }
    vec3 sd = v * 70.0;
    vec3 c = floor(sd);
    float hs = hash(c);
    if (hs > 0.9) {
        vec3 sp = c + vec3(hash(c + 1.3), hash(c + 2.7), hash(c + 4.1));
        float dd = length(sd - sp);
        float tint = hash(c + 7.7);
        vec3 sc = tint < 0.3 ? vec3(1.0, 0.8, 0.6) : (tint < 0.7 ? vec3(1.0, 0.97, 0.92) : vec3(0.75, 0.85, 1.0));
        col += sc * smoothstep(0.16, 0.0, dd) * (hs - 0.9) * 22.0;
    }
    return col;
}

void main() {
    vec2 imp = Pc + Px * ab.x + Py * ab.y;
    float b = length(imp);
    float fadeOut = 1.0 - smoothstep(Edge * 0.72, Edge, b);
    if (fadeOut <= 0.0) {
        discard;
    }
    vec3 p = vec3(imp, 60.0);
    vec3 v = vec3(0.0, 0.0, -1.0);
    vec3 hv = cross(p, v);
    float h2 = dot(hv, hv);
    vec3 col = vec3(0.0);
    float trans = 1.0;
    bool escaped = false;
    for (int i = 0; i < 400; i++) {
        if (float(i) >= MaxSteps || trans < 0.02) {
            break;
        }
        float r2 = dot(p, p);
        float r = sqrt(r2);
        float dt = clamp(0.07 * r, 0.015, 3.0);
        vec3 acc = -1.5 * h2 * p / (r2 * r2 * r);
        vec3 pn = p + v * dt + 0.5 * acc * dt * dt;
        float rn2 = dot(pn, pn);
        float rn = sqrt(rn2);
        vec3 accn = -1.5 * h2 * pn / (rn2 * rn2 * rn);
        vec3 vn = v + 0.5 * (acc + accn) * dt;
        float s0 = dot(p, DiskN);
        float s1 = dot(pn, DiskN);
        if (s0 * s1 < 0.0) {
            vec3 q = mix(p, pn, s0 / (s0 - s1));
            float rho = length(q);
            if (rho > INNER - 0.1 && rho < OUTER) {
                float op;
                vec3 e = disc(q, vn, op);
                col += trans * e * op;
                trans *= 1.0 - op;
            }
        }
        // Light skimming the photon sphere (where it can circle the hole): the thin photon ring.
        float ring = exp(-pow((rn - 1.5) / 0.06, 2.0));
        col += trans * vec3(1.0, 0.86, 0.66) * ring * dt * 0.35;
        p = pn;
        v = vn;
        if (rn < 1.0) {
            trans = 0.0;
            break;
        }
        if (rn > 90.0 && dot(p, v) > 0.0) {
            escaped = true;
            break;
        }
    }
    if (escaped) {
        col += trans * beyond(p, normalize(v)) * fadeOut;
    }
    col = vec3(1.0) - exp(-col * Exposure);
    fragColor = vec4(col, Alpha * fadeOut);
}
