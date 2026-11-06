"""T-Rex surface painter: evaluated per texel at the rest-pose position (G-space px, +y up, faces -z)."""
import math
import numpy as np


def hx(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255.0


PAL = dict(
    dorsal=hx("#4a4128"), flank=hx("#8a7347"), belly=hx("#dccb9e"), throat=hx("#d2b48a"), stripe=hx("#2a2216"),
    leg=hx("#6c5c3a"), foot=hx("#4c4433"), accent=hx("#7e3a22"), mask=hx("#33291b"), snout=hx("#7a6640"),
    mouth=hx("#6c2a27"), gum=hx("#8a3a36"), tongue=hx("#b0566a"), tooth=hx("#ece2c6"), tooth_base=hx("#b9a374"),
    claw=hx("#2a241e"), claw_base=hx("#5d4d3a"), iris=hx("#d89b1f"), iris_dark=hx("#7a4a0c"), lid=hx("#3b3222"),
    horn=hx("#332a1c"), scar=hx("#c9a38e"), osteo=hx("#2f291a"),
)

LIGHT = np.array([0.25, 0.9, -0.35])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def sm(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mixc(c1, c2, t):
    t = np.asarray(t)
    if t.ndim == 1:
        t = t[:, None]
    return c1 * (1 - t) + c2 * t


def _hash(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float64) / float(0xFFFFFF)


def vnoise(P, scale, seed):
    q = P / scale
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                out = out + w * _hash(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, seed)
    return out


def fbm(P, scale, seed, oct=3):
    t, a, n = 0.0, 1.0, 0.0
    for o in range(oct):
        t = t + a * vnoise(P, scale / 2 ** o, seed + 31 * o)
        n += a
        a *= 0.5
    return t / n


def cells(P, scale, seed):
    """F1, F2 and the vector from the nearest feature point to P (world units)"""
    q = P / scale
    i = np.floor(q).astype(np.int64)
    f1 = np.full(len(P), 9.0)
    f2 = np.full(len(P), 9.0)
    vec = np.zeros_like(P)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz
                fp = np.stack([cx + 0.15 + 0.7 * _hash(cx, cy, cz, seed), cy + 0.15 + 0.7 * _hash(cx, cy, cz, seed + 1),
                               cz + 0.15 + 0.7 * _hash(cx, cy, cz, seed + 2)], axis=1)
                dv = q - fp
                d = np.sqrt((dv * dv).sum(axis=1))
                closer = d < f1
                f2 = np.where(closer, f1, np.minimum(f2, d))
                vec = np.where(closer[:, None], dv, vec)
                f1 = np.where(closer, d, f1)
    return f1, f2, vec


def scale_relief(P, N, size, seed, depth=1.0):
    """dome-shaped scales: returns (light factor, cavity)"""
    f1, f2, vec = cells(P, size, seed)
    edge = sm(0.0, 0.28, f2 - f1)              # 0 in the groove between scales
    # dome slope ~ vector from the scale centre, projected on the surface
    vt = vec - (vec * N).sum(axis=1, keepdims=True) * N
    slope = (vt * LIGHT).sum(axis=1)
    lit = 1.0 - 0.32 * depth * slope * sm(0.0, 0.6, f1)
    cav = 1.0 - 0.32 * depth * (1 - edge)
    return lit * cav, edge


def stripes_mask(P, N, seed):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    warp = fbm(P, 7.0, seed + 5, 3) * 7.0
    # bands across the body; slightly diagonal (sloping backwards towards the belly)
    band = np.sin((z + warp + (60 - y) * 0.35) * 0.42)
    width = 0.62 + 0.18 * fbm(P, 11.0, seed + 9, 2)
    m = sm(width, width + 0.12, band)
    # broken edges
    m *= sm(0.3, 0.55, fbm(P, 2.2, seed + 13, 2) + 0.15)
    top = sm(-0.35, 0.55, N[:, 1])
    return m * top


def wrinkles(P, N, seed):
    """transverse skin folds on the neck, throat, tail base and around joints: returns darkening factor"""
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    out = np.ones(len(P))
    jitter = fbm(P, 3.0, seed + 41, 2) * 2.0
    # neck & throat folds (constant-z grooves), strongest underneath
    neck = sm(-58, -50, z) * (1 - sm(-31, -25, z)) * (0.35 + 0.65 * sm(0.3, -0.8, N[:, 1]))
    g = np.abs(np.sin((z + jitter) * 1.25))
    out *= 1 - 0.38 * neck * (1 - sm(0.0, 0.22, g))
    # belly folds behind the arms
    belly = sm(-0.2, -0.85, N[:, 1]) * sm(-30, -20, z) * (1 - sm(-2, 6, z))
    g2 = np.abs(np.sin((z * 0.55 + x * 0.25 + jitter) * 1.1))
    out *= 1 - 0.25 * belly * (1 - sm(0.0, 0.2, g2))
    # joints: knees, ankles, elbows (rings of folds around the joint centres)
    joints = [((11.2, 31.8, 0.4), 6.0), ((-11.2, 31.8, 0.4), 6.0), ((11.2, 13.3, 9.2), 4.0), ((-11.2, 13.3, 9.2), 4.0),
              ((10.2, 39.8, -32.5), 2.6), ((-10.2, 39.8, -32.5), 2.6)]
    for c, r in joints:
        d = np.linalg.norm(P - np.array(c), axis=1)
        w = 1 - sm(r * 0.6, r * 1.25, d)
        g3 = np.abs(np.sin((d * 2.2 + jitter * 0.6)))
        out *= 1 - 0.3 * w * (1 - sm(0.0, 0.25, g3))
    # tail base folds on the sides
    tb = sm(18, 24, z) * (1 - sm(34, 42, z)) * (1 - np.abs(N[:, 1]))
    g4 = np.abs(np.sin((z + jitter) * 1.4))
    out *= 1 - 0.22 * tb * (1 - sm(0.0, 0.2, g4))
    return out


SCARS = [  # (start, end, width) claw-mark scars on the left flank, one on the snout and one on the right thigh
    ((12.6, 54.0, -22.0), (11.4, 41.0, -9.0), 0.42), ((12.9, 55.5, -19.2), (11.7, 42.6, -6.0), 0.38),
    ((13.0, 56.6, -16.4), (12.0, 44.6, -3.4), 0.34), ((-3.4, 66.8, -69.0), (-4.6, 62.6, -74.5), 0.3),
    ((-14.0, 44.0, 6.0), (-13.4, 36.0, 1.2), 0.36),
]


def scars(P):
    m = np.zeros(len(P))
    for a, b, w in SCARS:
        a, b = np.array(a), np.array(b)
        ab = b - a
        t = np.clip(((P - a) @ ab) / (ab @ ab), 0, 1)
        d = np.linalg.norm(P - (a + np.outer(t, ab)), axis=1)
        taper = np.sin(np.clip(t, 0, 1) * math.pi) ** 0.5
        m = np.maximum(m, (1 - sm(w * 0.5 * taper, w * taper + 0.05, d)) * (d < 3.5))
    return m


def osteoderms(P, N):
    """two rows of bony bumps along the back: returns (mask, light factor)"""
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    top = sm(0.55, 0.9, N[:, 1])
    zz = (z + 40) / 2.6
    k = np.round(zz)
    dz = (zz - k) * 2.6
    dx = np.abs(np.abs(x) - 2.6 - 0.4 * np.sin(k * 1.7))
    rng = sm(-34, -28, z) * (1 - sm(62, 72, z))
    d = np.sqrt((dx / 1.0) ** 2 + (dz / 1.15) ** 2)
    m = (1 - sm(0.55, 0.85, d)) * top * rng
    lit = 1 - 0.3 * np.clip(dz / 1.1, -1, 1) * LIGHT[2] * m
    return m, lit


def shade(mat, P, N, AO, ctx):
    n = len(P)
    seed = 1234
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    ny = N[:, 1]
    if mat in ("skin", "lid", "horn_skin"):
        # ---------------- base colour by region
        dorsal = sm(-0.05, 0.85, ny)
        ventral = sm(-0.15, -0.8, ny)
        col = mixc(PAL["flank"], PAL["dorsal"], dorsal * 0.85)
        lowleg = sm(30, 16, y) * (z > -20)
        col = mixc(col, PAL["leg"], lowleg * 0.8)
        col = mixc(col, PAL["foot"], sm(9, 3, y) * (z > -20))
        bel = ventral * sm(18, 32, y)
        throat = sm(-60, -52, z) * (1 - sm(-30, -24, z))
        bellycol = mixc(PAL["belly"], PAL["throat"], throat)
        col = mixc(col, bellycol, bel)
        # head: darker mask through the eye, reddish brows, lighter lips
        head = sm(-52, -56, z)
        mask = head * (1 - sm(1.6, 3.4, np.abs(y - 66.9 + 0.12 * (z + 63)))) * sm(-74, -66, z)
        col = mixc(col, PAL["mask"], mask * 0.7)
        brow = head * sm(68.5, 70.0, y) * sm(-67, -64, z) * (1 - sm(-59, -57, z)) * (np.abs(x) > 2.5)
        col = mixc(col, PAL["accent"], brow * 0.65)
        snout_top = head * sm(-62, -66, z) * sm(0.35, 0.8, ny)
        col = mixc(col, PAL["snout"], snout_top * 0.5)
        lips = head * (1 - sm(0.6, 1.6, np.abs(y - 60.0))) * (np.abs(x) > 2.4)
        col = mixc(col, PAL["belly"] * 0.8, lips * 0.35)
        # ---------------- stripes, tail bands, mottling
        st = stripes_mask(P, N, seed) * (1 - bel) * (1 - head * 0.6)
        col = mixc(col, PAL["stripe"], st * 0.75)
        mott = fbm(P, 9.0, seed + 77, 3)
        col = col * (0.9 + 0.2 * mott)[:, None]
        col = col * (0.96 + 0.08 * vnoise(P, 1.3, seed + 3))[:, None]
        # ---------------- scales (relief), three sizes blended by region
        big, e1 = scale_relief(P, N, 2.1, seed + 101, 1.0)
        mid, e2 = scale_relief(P, N, 1.25, seed + 102, 0.9)
        small, e3 = scale_relief(P, N, 0.72, seed + 103, 0.8)
        w_big = dorsal * (1 - head) * sm(-30, -20, z) * (1 - sm(80, 95, z))
        w_small = np.clip(head + lowleg + sm(-0.3, -0.9, ny) * 0.3, 0, 1)
        w_mid = np.clip(1 - w_big - w_small, 0, 1)
        relief = big * w_big + mid * w_mid + small * w_small
        relief = relief / np.maximum(w_big + w_mid + w_small, 1e-6)
        # belly: transverse scute rows
        scute = np.abs(np.sin(z * math.pi / 1.7 + fbm(P, 4, seed + 7) * 1.2))
        belly_scute = 1 - 0.22 * (1 - sm(0.0, 0.2, scute))
        relief = mixc(relief[:, None], belly_scute[:, None], bel * 0.75)[:, 0]
        # toes: transverse scutes on the top of the toes
        toes = sm(4.5, 2.5, y) * sm(0.0, 0.6, ny) * (z < 6)
        tscute = np.abs(np.sin((-z) * math.pi / 1.2))
        relief = relief * (1 - 0.3 * toes * (1 - sm(0.0, 0.25, tscute)))
        col = col * relief[:, None]
        col = col * wrinkles(P, N, seed)[:, None]
        om, olit = osteoderms(P, N)
        col = mixc(col, PAL["osteo"], om * 0.55) * olit[:, None]
        sc = scars(P)
        scar_edge = scars(P + N * 0.0) * 0
        col = mixc(col, PAL["scar"], sc * 0.85)
        if mat == "lid":
            col = mixc(col, PAL["lid"], 0.6)
        if mat == "horn_skin":
            col = mixc(col, PAL["horn"], 0.55) * (0.8 + 0.25 * fbm(P, 0.6, seed + 55, 2))[:, None]
        # mouth interior: palate under the head, floor of the lower jaw
        grp = ctx.get("grp")
        if grp == "body":
            palate = head * sm(-0.35, -0.75, ny) * (np.abs(x) < 3.7) * (y < 60.2) * (z < -58.5)
            col = mixc(col, PAL["mouth"] * (0.85 + 0.15 * np.abs(np.sin(z * 2.4)))[:, None], palate)
            gum = head * (1 - sm(0.2, 0.7, np.abs(y - 59.4))) * (np.abs(x) > 2.6) * (np.abs(x) < 4.9) * (z < -58)
            col = mixc(col, PAL["gum"], gum * 0.7)
        elif grp == "jaw":
            floor = sm(0.3, 0.75, ny) * (np.abs(x) < 2.75) * (y > 59.6)
            col = mixc(col, PAL["mouth"], floor)
            gum = (1 - sm(0.15, 0.6, np.abs(y - 61.0))) * (np.abs(x) > 1.4) * (np.abs(x) < 3.4)
            col = mixc(col, PAL["gum"], gum * 0.7)
    elif mat == "tongue":
        groove = 1 - 0.3 * (1 - sm(0.0, 0.35, np.abs(x)))
        col = PAL["tongue"][None, :] * groove[:, None] * (0.9 + 0.15 * vnoise(P, 0.5, 9))[:, None]
    elif mat == "tooth":
        t = ctx["along"]
        col = mixc(np.tile(PAL["tooth_base"], (n, 1)), np.tile(PAL["tooth"], (n, 1)), sm(0.0, 0.45, t))
        col = col * (0.92 + 0.1 * vnoise(P * np.array([4, 0.5, 4]), 1.0, 5))[:, None]
        AO = np.maximum(AO, 0.8)
    elif mat == "claw":
        t = ctx["along"]
        col = mixc(np.tile(PAL["claw_base"], (n, 1)), np.tile(PAL["claw"], (n, 1)), sm(0.0, 0.6, t))
        gloss = sm(0.5, 0.95, (N * LIGHT).sum(axis=1)) * 0.12
        col = col + gloss[:, None]
        AO = np.maximum(AO, 0.8)
    elif mat == "eye":
        c, fwd = ctx["eye"]
        d = P - np.array(c)
        d /= np.maximum(np.linalg.norm(d, axis=1, keepdims=True), 1e-9)
        cs = d @ fwd
        iris = sm(0.25, 0.35, cs)
        rad = np.arctan2(d @ np.array([0, 1.0, 0]), d @ np.cross(fwd, [0, 1.0, 0]))
        striae = 0.85 + 0.15 * np.sin(rad * 23.0)
        col = mixc(np.tile(np.array([0.12, 0.08, 0.05]), (n, 1)), PAL["iris"] * striae[:, None], iris)
        col = mixc(col, PAL["iris_dark"], sm(0.55, 0.35, cs) * iris)       # darker rim of the iris
        side = np.cross(fwd, np.array([0, 1.0, 0]))
        side /= np.linalg.norm(side)
        pupil = (np.abs(d @ side) < 0.16 * (0.5 + 0.5 * sm(0.6, 1.0, cs))) & (cs > 0.62)
        col[pupil] = np.array([0.02, 0.015, 0.01])
        hl = (d @ normalize3(fwd * 0.7 + np.array([0, 0.55, 0]) + side * 0.3)) > 0.95
        col[hl] = np.array([0.96, 0.95, 0.9])
        AO = np.ones(n)
    else:
        col = np.tile(np.array([1.0, 0.0, 1.0]), (n, 1))
    col = col * (0.42 + 0.58 * AO)[:, None]
    return np.clip(col, 0, 1)


def normalize3(v):
    return v / np.linalg.norm(v)
