#version 150

// A gas giant: no ground, only its clouds. The planet's map is turned band by band at the speed of its winds
// (neighbouring bands sliding past each other in opposite directions), its great storms turn on the spot as whole
// ovals with their spiral arms, a hexagon of jet stream can circle a pole, and the clouds' fine turbulence boils
// at the edges of the bands. Lit by its star, darker at the limb, with a thin haze of its air round the edge.
// Rays in the planet's frame, its equatorial radius 1.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec3 RayO;
uniform vec3 RayOx;
uniform vec3 RayOy;
uniform vec3 RayD;
uniform vec3 RayDx;
uniform vec3 RayDy;
uniform vec3 LightDir;
uniform vec3 LightColor;
uniform float Oblate;
uniform float Time;
uniform float Bands;
uniform vec4 V0;
uniform vec4 V1;
uniform vec4 V2;
uniform vec4 V3;
uniform float Hexagon;
uniform float Glow;
uniform vec3 AirColor;
uniform float Alpha;
uniform float Pixel;

in vec2 ab;

out vec4 fragColor;

const float PI = 3.14159265;

float noise3(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec2 rg = textureLod(Sampler1, (uv + 0.5) / 256.0, 0.0).xy;
    return mix(rg.x, rg.y, f.z);
}

float fbm(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * noise3(p);
        p = p * 2.07 + vec3(5.1, 1.7, 9.3);
        a *= 0.5;
    }
    return s / 0.9375;
}

// A storm: inside its oval the clouds are turned round its centre (the whole oval turning, wound into spiral arms).
vec2 vortex(vec2 q, vec4 v) {
    if (v.z <= 0.0) {
        return q;
    }
    vec2 d = vec2(mod(q.x - v.x + PI, 2.0 * PI) - PI, q.y - v.y);
    float c = max(cos(v.y), 0.2);
    d.x *= c;
    // Ovals are wider than they are tall.
    vec2 e = vec2(d.x * 0.62, d.y);
    float r = length(e) / v.z;
    if (r >= 1.0) {
        return q;
    }
    float k = (1.0 - r) * (1.0 - r);
    float a = v.w * (k * 1.6 + Time * 0.02 * (0.4 + k));
    float ca = cos(a);
    float sa = sin(a);
    e = vec2(ca * e.x - sa * e.y, sa * e.x + ca * e.y);
    d = vec2(e.x / 0.62, e.y);
    d.x /= c;
    return vec2(v.x, v.y) + d;
}

void main() {
    vec3 o = RayO + RayOx * ab.x + RayOy * ab.y;
    vec3 d = normalize(RayD + RayDx * ab.x + RayDy * ab.y);
    vec3 so = vec3(o.x, o.y / Oblate, o.z);
    vec3 sd = vec3(d.x, d.y / Oblate, d.z);
    float qa = dot(sd, sd);
    float qb = dot(so, sd);
    float qc = dot(so, so) - 1.0;
    float h = qb * qb - qa * qc;
    float tc = -qb / qa;
    if (h < 0.0 || tc < 0.0) {
        // Just past the edge: the haze of its air, lit on the day side.
        if (tc < 0.0) {
            discard;
        }
        vec3 pc = so + sd * tc;
        float rr = length(pc);
        float haze = exp(-(rr - 1.0) * 60.0) * 0.55;
        float lit = smoothstep(-0.25, 0.35, dot(pc / rr, LightDir));
        vec3 c = AirColor * LightColor * haze * lit;
        if (dot(c, vec3(1.0)) < 0.002) {
            discard;
        }
        fragColor = vec4(c * Alpha, 0.0);
        return;
    }
    float t = (-qb - sqrt(h)) / qa;
    if (t < 0.0) {
        discard;
    }
    vec3 p = so + sd * t;
    vec3 n = normalize(vec3(p.x, p.y / Oblate, p.z));
    float lat = asin(clamp(p.y, -1.0, 1.0));
    float lon = atan(p.z, p.x);
    // The winds: bands sliding past each other.
    float drift = Time * Bands * (0.006 * sin(lat * 9.0) + 0.003 * sin(lat * 23.0 + 1.3) + 0.002);
    vec2 q = vec2(lon + drift, lat);
    q = vortex(q, V0);
    q = vortex(q, V1);
    q = vortex(q, V2);
    q = vortex(q, V3);
    // Turbulence at the edges of the bands, boiling slowly.
    vec3 np = vec3(cos(q.y) * cos(q.x), sin(q.y), cos(q.y) * sin(q.x));
    float w = fbm(np * 7.0 + vec3(0.0, Time * 0.0008, 0.0)) - 0.5;
    q.y += w * 0.03;
    q.x += w * 0.05;
    vec2 uv = vec2(fract(q.x / (2.0 * PI)), clamp(0.5 - q.y / PI, 0.001, 0.999));
    vec3 col = textureLod(Sampler0, uv, 0.0).rgb;
    // A hexagon of jet stream round the north pole (Saturn), the pole inside it darker and bluer.
    if (Hexagon > 0.0 && lat > 1.0) {
        float edge = 1.3 + 0.028 * cos(6.0 * (lon - Time * 0.00025));
        float inside = smoothstep(edge - 0.012, edge + 0.012, lat);
        col = mix(col, col * vec3(0.72, 0.82, 0.95), inside * Hexagon);
        col *= 1.0 - 0.18 * Hexagon * exp(-pow((lat - edge) / 0.012, 2.0));
    }
    // Finer cloud detail once the planet is big on the screen.
    float detail = smoothstep(0.02, 0.002, Pixel);
    col *= 1.0 + (fbm(np * 38.0 + vec3(Time * 0.001)) - 0.5) * 0.35 * detail;
    float mu = clamp(dot(n, -d), 0.0, 1.0);
    float ndl = dot(n, LightDir);
    float day = smoothstep(-0.1, 0.2, ndl);
    vec3 lit = col * LightColor * (0.025 + 0.975 * day * (0.3 + 0.7 * max(ndl, 0.0))) * (0.6 + 0.4 * sqrt(mu));
    lit += AirColor * LightColor * pow(1.0 - mu, 4.0) * day * 0.3;
    lit += col * Glow * 0.65;
    fragColor = vec4(lit * Alpha, Alpha);
}
