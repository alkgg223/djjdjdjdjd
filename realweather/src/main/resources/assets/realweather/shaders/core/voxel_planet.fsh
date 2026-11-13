#version 150

// A planet made of blocks. Each pixel's ray is followed cell by cell through a grid of one-block cells round the
// planet; a cell is solid where it lies below the ground of its column, and the ground's height and colour come from
// the planet's own surface map (bright ground stands higher, oceans are flooded to sea level). The block that is hit
// shows the face of a planet block (rock, dust, sand, ice, snow, lava, cloud, water...), tinted by the map, lit by its
// star with Minecraft-like shading of its faces. Inside the air (close to the ground) the sky and the haze are
// drawn too.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec3 RayO;
uniform vec3 RayOx;
uniform vec3 RayOy;
uniform vec3 RayD;
uniform vec3 RayDx;
uniform vec3 RayDy;
uniform float Radius;
uniform float Relief;
uniform float Sea;
uniform vec3 LightDir;
uniform vec3 LightColor;
uniform float Ambient;
uniform float Glow;
uniform float Alpha;
uniform float MaxSteps;
uniform vec4 Tiles;
uniform float Gas;
uniform float Air;
uniform vec3 AirColor;
uniform float AirHeight;
uniform float PixelSize;
uniform float PixelGrow;
uniform float Hills;

in vec2 ab;

out vec4 fragColor;

const float PI = 3.14159265;

vec2 equi(vec3 d) {
    return vec2(fract(atan(d.z, d.x) / (2.0 * PI)), acos(clamp(d.y, -1.0, 1.0)) / PI);
}

vec3 mapAt(vec3 d) {
    return textureLod(Sampler0, equi(d), 0.0).rgb;
}

// A small hash and value noise (the same sums are done on the CPU to find the ground under the camera).
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

float hillNoise(vec3 c) {
    vec3 q = c / 8.0;
    vec3 i = floor(q);
    vec3 f = q - i;
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x), f.y);
    float b = mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x), f.y);
    return mix(a, b, f.z);
}

float lum(vec3 c) {
    return dot(c, vec3(0.3, 0.59, 0.11));
}

bool isOcean(vec3 c) {
    return Sea >= 0.0 && c.b > c.r * 1.15 && c.b > c.g * 0.95;
}

// Height of the ground (whole blocks above Radius) under this colour of the map, in this direction (hills on top).
float groundOf(vec3 c, vec3 dir) {
    if (isOcean(c)) {
        return max(Sea - 2.0, 0.0);
    }
    float h = floor(lum(c) * Relief + 0.5) + floor((hillNoise(dir * Radius) - 0.5) * 2.0 * Hills + 0.5);
    if (Sea >= 0.0) {
        h = max(h, Sea);
    }
    return max(h, 0.0);
}

float tileFor(vec3 c, float h, bool water) {
    if (water) {
        return Tiles.w;
    }
    float l = lum(c);
    if (Gas > 0.5) {
        return l > 0.6 ? 12.0 : 6.0;
    }
    if (l > 0.8) {
        return 4.0;
    }
    if (Glow > 0.2 && c.r > 0.7 && c.g < 0.62 && c.r > c.b * 1.6) {
        return 5.0;
    }
    float rel = h / max(Relief, 1.0);
    return rel < 0.34 ? Tiles.x : (rel < 0.7 ? Tiles.y : Tiles.z);
}

vec3 face(float tile, vec2 uv) {
    vec2 cell = vec2(mod(tile, 4.0), floor(tile / 4.0));
    return textureLod(Sampler1, (cell + clamp(uv, 0.004, 0.996)) / 4.0, 0.0).rgb;
}

// The sky seen from inside the air: deeper overhead, paler towards the horizon, red at sunset, dark at night.
vec4 skyAt(vec3 d, vec3 up, float sunUp, float inside) {
    float e = dot(d, up);
    float dayK = smoothstep(-0.22, 0.18, sunUp);
    float horizon = pow(1.0 - clamp(e, 0.0, 1.0), 3.0);
    vec3 c = AirColor * (0.55 + 0.55 * horizon);
    float sunset = (1.0 - smoothstep(0.0, 0.35, sunUp)) * dayK;
    c = mix(c, vec3(1.0, 0.5, 0.24), sunset * horizon * 0.75);
    float toSun = max(dot(d, LightDir), 0.0);
    c += LightColor * (pow(toSun, 12.0) * 0.35 + pow(toSun, 300.0) * 1.5) * dayK;
    float a = inside * Air * (0.15 + 0.85 * dayK) * (0.65 + 0.35 * horizon);
    return vec4(c * (0.12 + 0.88 * dayK), clamp(a, 0.0, 1.0));
}

void main() {
    vec3 o = RayO + RayOx * ab.x + RayOy * ab.y;
    vec3 d = normalize(RayD + RayDx * ab.x + RayDy * ab.y);
    float camR = length(o);
    vec3 up = o / camR;
    float inside = Air > 0.0 ? clamp(1.0 - (camR - Radius) / AirHeight, 0.0, 1.0) : 0.0;
    float sunUp = dot(up, LightDir);
    vec4 sky = inside > 0.0 ? skyAt(d, up, sunUp, inside) : vec4(0.0);

    float top = Radius + Relief + 1.5;
    float b = dot(o, d);
    float disc = b * b - (dot(o, o) - top * top);
    float t1 = -b + sqrt(max(disc, 0.0));
    if (disc < 0.0 || t1 < 0.0) {
        if (sky.a <= 0.002) {
            discard;
        }
        fragColor = vec4(sky.rgb * sky.a * Alpha, sky.a * Alpha);
        return;
    }
    float t = max(-b - sqrt(disc), 0.0);
    vec3 p = o + d * t;
    vec3 cell = floor(p);
    vec3 st = vec3(d.x >= 0.0 ? 1.0 : -1.0, d.y >= 0.0 ? 1.0 : -1.0, d.z >= 0.0 ? 1.0 : -1.0);
    vec3 inv = 1.0 / max(abs(d), vec3(1.0e-7));
    vec3 tMax = (cell + max(st, 0.0) - p) * st * inv;
    float tEnter = t;
    int axis = -1;
    bool hit = false;
    bool water = false;
    bool left = false;
    vec3 col = vec3(0.0);
    vec3 cc = cell + 0.5;
    float ground = 0.0;
    for (int i = 0; i < 400; i++) {
        if (float(i) >= MaxSteps) {
            break;
        }
        cc = cell + 0.5;
        float r = length(cc);
        if (r > top + 1.0 && dot(cc, d) > 0.0) {
            left = true;
            break;
        }
        // (Starting inside a block, as on landing, look out of it.)
        if (r < top && !(i == 0 && t <= 0.0)) {
            vec3 m = mapAt(cc / r);
            ground = groundOf(m, cc / r);
            float lift = r - Radius;
            if (lift < ground) {
                hit = true;
                col = m;
                break;
            }
            if (Sea >= 0.0 && lift < Sea) {
                hit = true;
                water = true;
                col = m;
                break;
            }
        }
        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            cell.x += st.x;
            tEnter = tMax.x;
            tMax.x += inv.x;
            axis = 0;
        } else if (tMax.y < tMax.z) {
            cell.y += st.y;
            tEnter = tMax.y;
            tMax.y += inv.y;
            axis = 1;
        } else {
            cell.z += st.z;
            tEnter = tMax.z;
            tMax.z += inv.z;
            axis = 2;
        }
    }
    vec3 radial = normalize(cc);
    if (!hit) {
        if (left) {
            if (sky.a <= 0.002) {
                discard;
            }
            fragColor = vec4(sky.rgb * sky.a * Alpha, sky.a * Alpha);
            return;
        }
        // A grazing ray that would need too many steps: the smooth ground there instead.
        float mid = Radius + Relief * 0.4;
        float disc2 = b * b - (dot(o, o) - mid * mid);
        if (disc2 < 0.0) {
            if (sky.a <= 0.002) {
                discard;
            }
            fragColor = vec4(sky.rgb * sky.a * Alpha, sky.a * Alpha);
            return;
        }
        vec3 q = o + d * max(-b - sqrt(disc2), 0.0);
        vec3 qn = normalize(q);
        vec3 m = mapAt(qn);
        float dayS = smoothstep(-0.12, 0.22, dot(qn, LightDir));
        vec3 sm = m * (Ambient + (1.0 - Ambient) * dayS * (0.4 + 0.6 * max(dot(qn, LightDir), 0.0))) * LightColor + m * Glow * 0.4;
        if (inside > 0.0) {
            sm = mix(sm, sky.rgb, 0.8 * (1.0 - exp(-length(q - o) / (AirHeight * 3.0))));
        }
        fragColor = vec4(sm * Alpha, Alpha);
        return;
    }
    vec3 n = axis == 0 ? vec3(-st.x, 0.0, 0.0) : (axis == 1 ? vec3(0.0, -st.y, 0.0) : (axis == 2 ? vec3(0.0, 0.0, -st.z) : radial));
    vec3 hp = o + d * tEnter;
    vec2 uv = axis == 0 ? hp.zy : (axis == 1 ? hp.xz : hp.xy);
    uv = fract(uv);
    float tile = tileFor(col, ground, water);
    vec3 tex = face(tile, uv);
    // The block's own texture shows once a block is big enough on the screen (smaller, it would only shimmer).
    float foot = PixelSize + PixelGrow * tEnter;
    float dk = smoothstep(0.2, 0.085, foot);
    float detail = mix(1.0, lum(tex) / 0.62, dk);
    float jitter = 0.9 + 0.2 * hash13(cell + vec3(7.0, 3.0, 5.0));
    vec3 base = mix(col * detail, tex * (0.35 + lum(col) * 1.1), 0.25 * dk) * jitter;
    if (water) {
        base = mix(col, vec3(0.1, 0.3, 0.6), 0.4) * (0.75 + 0.35 * detail);
    }
    float ndl = max(dot(n, LightDir), 0.0);
    float day = smoothstep(-0.12, 0.22, dot(radial, LightDir));
    float faceShade = 0.6 + 0.4 * abs(dot(n, radial));
    vec3 lit = base * (Ambient + (1.0 - Ambient) * day * (0.4 + 0.6 * ndl)) * faceShade * LightColor;
    if (tile == 5.0) {
        lit += col * (0.8 + 0.6 * detail) * (1.0 - 0.5 * day);
    } else if (Glow > 0.0) {
        lit += col * Glow * 0.45 * (1.0 - 0.5 * day);
    }
    if (water) {
        lit += LightColor * pow(max(dot(reflect(d, radial), LightDir), 0.0), 60.0) * 0.8 * day;
    }
    if (inside > 0.0) {
        float f = 1.0 - exp(-length(hp - o) / (AirHeight * 3.0));
        lit = mix(lit, sky.rgb + AirColor * 0.15, f * 0.85);
    }
    fragColor = vec4(lit * Alpha, Alpha);
}
