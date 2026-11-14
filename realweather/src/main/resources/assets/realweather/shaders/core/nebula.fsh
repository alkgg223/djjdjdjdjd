#version 150

// A nebula, ray marched through its gas: along each pixel's ray, glowing gas adds its light and dark dust dims what
// is behind it. The nebula is built from a few pieces (clouds, shells, rings, pillars, filaments, lanes, each with its
// own colour, density and turbulence), with the swirling, clumpy structure of real gas from 3D noise. Lengths in the
// nebula's radius.

uniform sampler2D Sampler0;

uniform vec3 RayO;
uniform vec3 RayOx;
uniform vec3 RayOy;
uniform vec3 RayD;
uniform vec3 RayDx;
uniform vec3 RayDy;
uniform mat4 P0;
uniform mat4 P1;
uniform mat4 P2;
uniform mat4 P3;
uniform mat4 P4;
uniform mat4 P5;
uniform mat4 P6;
uniform mat4 P7;
uniform mat4 P8;
uniform mat4 P9;
uniform float Count;
uniform float Steps;
uniform float Octaves;
uniform float Seed;
uniform float Exposure;
uniform float Alpha;
uniform float Fine;

in vec2 ab;

out vec4 fragColor;

// 3D value noise from a 2D texture (two slices per fetch).
float noise3(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec2 rg = textureLod(Sampler0, (uv + 0.5) / 256.0, 0.0).xy;
    return mix(rg.x, rg.y, f.z);
}

float fbm(vec3 p, float oct) {
    float s = 0.0;
    float a = 0.5;
    float tot = 0.0;
    for (int i = 0; i < 7; i++) {
        if (float(i) >= oct) {
            break;
        }
        s += a * noise3(p);
        tot += a;
        p = p * 2.03 + vec3(11.3, 4.7, 7.1);
        a *= 0.5;
    }
    return s / tot;
}

vec3 turn(vec3 q, float a, float t) {
    float c = cos(a);
    float s = sin(a);
    q = vec3(c * q.x + s * q.y, -s * q.x + c * q.y, q.z);
    c = cos(t);
    s = sin(t);
    return vec3(q.x, c * q.y + s * q.z, -s * q.y + c * q.z);
}

void piece(mat4 m, vec3 p, float n1, float n2, float rg, inout vec3 emit, inout float dust) {
    float kind = m[0].x;
    vec3 q = turn(p - m[0].yzw, m[3].x, m[3].y);
    vec3 sz = max(m[1].xyz, vec3(0.001));
    float d;
    if (kind < 0.5) {
        vec3 e = q / sz;
        d = exp(-dot(e, e) * 2.2);
    } else if (kind < 1.5) {
        float r = length(q / sz);
        d = exp(-pow((r - 1.0) / m[1].w, 2.0));
    } else if (kind < 2.5) {
        vec2 t = vec2(length(q.xy / sz.xy) - 1.0, q.z / sz.z);
        d = exp(-dot(t, t) / (m[1].w * m[1].w));
    } else if (kind < 3.5) {
        float h = q.y / sz.y + 0.5;
        float rad = sz.x * mix(1.0, 0.42, clamp(h, 0.0, 1.0));
        float dist = length(q.xz) / rad;
        d = exp(-dist * dist * 2.5) * smoothstep(0.0, 0.06, h) * smoothstep(1.0, 0.84, h);
    } else if (kind < 4.5) {
        float r = length(q / sz);
        d = exp(-pow((r - 1.0) / m[1].w, 2.0)) * pow(rg, 5.0) * 2.6;
    } else {
        vec2 xz = q.xz / sz.xz;
        d = exp(-pow(q.y / sz.y, 2.0)) * exp(-dot(xz, xz));
    }
    // The turbulence of real gas: clumps, wisps and voids.
    float n = m[3].z > 0.5 ? n2 : n1;
    d *= mix(1.0, smoothstep(0.22, 0.82, n) * 1.8, m[3].w);
    float dens = m[2].w;
    if (dens > 0.0) {
        emit += m[2].rgb * d * dens;
    } else {
        dust += d * -dens;
    }
}

void main() {
    vec3 o = RayO + RayOx * ab.x + RayOy * ab.y;
    vec3 d = normalize(RayD + RayDx * ab.x + RayDy * ab.y);
    float b = dot(o, d);
    float c = dot(o, o) - 1.21;
    float h = b * b - c;
    if (h < 0.0) {
        discard;
    }
    h = sqrt(h);
    float t0 = max(-b - h, 0.0);
    float t1 = -b + h;
    if (t1 <= 0.0) {
        discard;
    }
    float dt = (t1 - t0) / Steps;
    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float t = t0 + dt * jitter;
    vec3 col = vec3(0.0);
    float tr = 1.0;
    for (int i = 0; i < 200; i++) {
        if (float(i) >= Steps || tr < 0.01) {
            break;
        }
        vec3 p = o + d * t;
        vec3 sp = p + vec3(Seed);
        float n1 = fbm(sp * 3.2, Octaves);
        float n2 = fbm(sp * 8.0 * Fine + 3.0, Octaves - 1.0);
        float rg = 1.0 - abs(2.0 * fbm(sp * 5.0 + 9.0, Octaves - 1.0) - 1.0);
        vec3 emit = vec3(0.0);
        float dust = 0.0;
    if (Count > 0.5) {
        piece(P0, p, n1, n2, rg, emit, dust);
    }
    if (Count > 1.5) {
        piece(P1, p, n1, n2, rg, emit, dust);
    }
    if (Count > 2.5) {
        piece(P2, p, n1, n2, rg, emit, dust);
    }
    if (Count > 3.5) {
        piece(P3, p, n1, n2, rg, emit, dust);
    }
    if (Count > 4.5) {
        piece(P4, p, n1, n2, rg, emit, dust);
    }
    if (Count > 5.5) {
        piece(P5, p, n1, n2, rg, emit, dust);
    }
    if (Count > 6.5) {
        piece(P6, p, n1, n2, rg, emit, dust);
    }
    if (Count > 7.5) {
        piece(P7, p, n1, n2, rg, emit, dust);
    }
    if (Count > 8.5) {
        piece(P8, p, n1, n2, rg, emit, dust);
    }
    if (Count > 9.5) {
        piece(P9, p, n1, n2, rg, emit, dust);
    }
        col += tr * emit * dt;
        tr *= exp(-dust * dt * 5.0);
        t += dt;
    }
    col = vec3(1.0) - exp(-col * Exposure);
    fragColor = vec4(col * Alpha, (1.0 - tr) * Alpha);
}
