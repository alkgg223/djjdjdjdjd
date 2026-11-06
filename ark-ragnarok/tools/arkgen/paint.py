"""Procedural creature textures.

Every texel of every cube face is mapped back to its 3D rest-pose position (model space), so patterns
(stripes, spots, rosettes, countershading, scales) are solid 3D textures that run continuously across
neighbouring parts.  Decals (eyes, teeth, nostrils, mouth) are drawn per face.
"""
import math
import numpy as np
from geom import face_regions, face_point, FACE_NORMAL


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], dtype=np.float64) / 255.0


# ------------------------------------------------------------------ noise
def _hash3(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float64) / float(0xFFFFFF)


def value_noise(p, scale, seed):
    q = p / scale
    i = np.floor(q).astype(np.int64)
    fr = q - i
    fr = fr * fr * (3 - 2 * fr)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fr[:, 0] if dx else 1 - fr[:, 0]) * (fr[:, 1] if dy else 1 - fr[:, 1]) * (fr[:, 2] if dz else 1 - fr[:, 2])
                out = out + w * _hash3(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, seed)
    return out


def fbm(p, scale, seed, octaves=3):
    tot, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        tot = tot + amp * value_noise(p, scale / (2 ** o), seed + o * 17)
        norm += amp
        amp *= 0.5
    return tot / norm


def cellular(p, scale, seed):
    """distance to nearest and second nearest jittered point (F1, F2) in cell units"""
    q = p / scale
    i = np.floor(q).astype(np.int64)
    f1 = np.full(len(p), 9.0)
    f2 = np.full(len(p), 9.0)
    cid = np.zeros(len(p))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz
                jx = _hash3(cx, cy, cz, seed)
                jy = _hash3(cx, cy, cz, seed + 1)
                jz = _hash3(cx, cy, cz, seed + 2)
                d = np.sqrt((cx + jx - q[:, 0]) ** 2 + (cy + jy - q[:, 1]) ** 2 + (cz + jz - q[:, 2]) ** 2)
                closer = d < f1
                f2 = np.where(closer, f1, np.minimum(f2, d))
                cid = np.where(closer, jx, cid)
                f1 = np.where(closer, d, f1)
    return f1, f2, cid


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mix(c1, c2, t):
    t = np.asarray(t)[:, None] if np.ndim(t) else t
    return c1 * (1 - t) + c2 * t


# ------------------------------------------------------------------ palette
class Palette:
    def __init__(self, d):
        self.d = {k: hexrgb(v) if isinstance(v, str) else v for k, v in d.items()}

    def __getitem__(self, k):
        if k in self.d:
            return self.d[k]
        fallback = {"skin2": "skin", "belly": "skin", "accent": "skin2", "fur": "skin", "fur2": "fur", "feather": "accent",
                    "horn": None, "claw": None, "eye": None, "mane": "fur2", "membrane": "skin", "fin": "skin", "plate": "skin2",
                    "frill": "accent", "sail": "accent", "beak": None, "shell": "skin2", "chitin": "skin", "glow": None}
        defaults = {"horn": "#d8c9a3", "claw": "#2b2420", "eye": "#d9a21e", "beak": "#c9a05a", "glow": "#7ff0ff", "tusk": "#efe6cf",
                    "bone": "#d9cfb6", "saddle": "#6b4426", "egg": "#e8dcc0", "mouth": "#6a1f22", "metal": "#8c939b", "tek": "#2a3340",
                    "fire": "#ff7a1a", "ice": "#bfe8ff", "rock": "#7d756b", "lava": "#ff5a00", "insectwing": "#cfe9f2"}
        f = fallback.get(k)
        if f:
            return self[f]
        return hexrgb(defaults.get(k, "#ff00ff"))


# ------------------------------------------------------------------ materials
def shade_material(mat, P, N, loc, pal, sp, seed, k_res):
    """P: world positions (n,3) model px, N: normals (n,3), loc: dict with s,t, along (0..1 along part), returns rgb (n,3)."""
    n = len(P)
    pat = sp.get("pattern", "none")
    pscale = sp.get("pattern_scale", 1.0)
    if mat in ("skin", "skin2", "belly"):
        base = pal["skin"] if mat != "skin2" else pal["skin2"]
        belly_t = smooth(-0.2, 0.9, N[:, 1]) * 0.85 + smooth(sp.get("belly_y", 3.0), sp.get("belly_y", 3.0) + 6.0, P[:, 1] - sp.get("center_y", 12)) * 0.4
        belly_t = np.clip(belly_t * sp.get("countershade", 1.0), 0, 1)
        col = mix(base, pal["belly"], belly_t)
        mask = pattern_mask(pat, P, N, pscale, seed, sp)
        col = mix(col, pal["skin2"], mask * (1 - belly_t * 0.7) * sp.get("pattern_strength", 0.85))
        # scales: cellular edges
        sc = sp.get("scale_size", 1.6)
        if sc > 0 and sp.get("skin_type", "scales") == "scales":
            f1, f2, cid = cellular(P, sc, seed + 5)
            edge = smooth(0.0, 0.25, f2 - f1)
            col = col * (0.82 + 0.18 * edge)[:, None] * (0.94 + 0.08 * cid)[:, None]
        elif sp.get("skin_type") == "wrinkled":
            w = fbm(P * np.array([1, 3, 1]), 2.0, seed + 9, 2)
            col = col * (0.85 + 0.2 * w)[:, None]
        elif sp.get("skin_type") == "smooth":
            w = fbm(P, 4.0, seed + 9, 2)
            col = col * (0.92 + 0.1 * w)[:, None]
        col = col * (0.9 + 0.12 * fbm(P, 6.0, seed + 3, 2))[:, None]
        return col
    if mat in ("fur", "fur2", "mane"):
        base = pal[mat]
        streak = value_noise(P * np.array([4.0, 0.6, 4.0]), 1.0, seed + 11)
        clump = fbm(P, 3.0, seed + 12, 2)
        col = base * (0.72 + 0.28 * streak[:, None]) * (0.85 + 0.25 * clump[:, None])
        belly_t = smooth(0.0, 1.0, N[:, 1]) * 0.6
        col = mix(col, pal["belly"] if "belly" in pal.d else base * 1.15, belly_t)
        mask = pattern_mask(pat, P, N, pscale, seed, sp)
        col = mix(col, pal["skin2"] if "skin2" in pal.d else base * 0.6, mask * sp.get("pattern_strength", 0.8) * (1 - belly_t))
        return np.clip(col, 0, 1)
    if mat in ("feather",):
        base = pal["feather"]
        s, t = loc["s"], loc["t"]
        barb = 0.5 + 0.5 * np.sin((s * 6.0 + t * 2.5) * math.pi * 2 * max(1.0, loc["size"] / 4.0))
        tips = smooth(0.6, 1.0, loc["along"])
        col = base * (0.75 + 0.25 * barb[:, None])
        col = mix(col, pal["accent"] * 0.7 if "accent" in pal.d else base * 0.6, tips * 0.5)
        col = col * (0.9 + 0.15 * value_noise(P, 1.2, seed + 21))[:, None]
        return np.clip(col, 0, 1)
    if mat in ("horn", "claw", "tusk", "beak", "bone"):
        base = pal[mat]
        streak = value_noise(P * np.array([3.0, 0.5, 3.0]), 1.0, seed + 31)
        tip = smooth(0.3, 1.0, loc["along"])
        dark = base * 0.45 if mat != "claw" else base * 0.6
        col = mix(base, dark, tip * (0.6 if mat in ("horn", "tusk") else 0.3))
        return np.clip(col * (0.85 + 0.2 * streak[:, None]), 0, 1)
    if mat in ("membrane", "frill", "sail", "fin"):
        base = pal[mat]
        s, t = loc["s"], loc["t"]
        veins = np.abs(np.sin((P[:, 0] * 0.9 + P[:, 2] * 0.35) * 1.3 + fbm(P, 3, seed + 41) * 3.0))
        vein = smooth(0.0, 0.18, veins)
        edge = smooth(0.75, 1.0, np.maximum(np.abs(s - 0.5), np.abs(t - 0.5)) * 2)
        col = base * (0.7 + 0.3 * vein[:, None])
        col = mix(col, pal["skin2"] if mat in ("frill", "sail") else base * 0.6, edge * 0.5)
        if mat in ("frill", "sail") and sp.get("frill_pattern", True):
            spots = pattern_mask("spots", P, N, 0.8, seed + 7, sp)
            col = mix(col, pal["skin2"] * 0.8, spots * 0.6)
        return np.clip(col * (0.92 + 0.1 * value_noise(P, 1.5, seed + 42))[:, None], 0, 1)
    if mat in ("plate", "shell"):
        base = pal[mat]
        f1, f2, cid = cellular(P, 2.6 if mat == "shell" else 1.8, seed + 51)
        edge = smooth(0.0, 0.18, f2 - f1)
        col = base * (0.6 + 0.4 * edge[:, None]) * (0.9 + 0.2 * cid[:, None])
        ring = 0.5 + 0.5 * np.sin(f1 * 18.0)
        return np.clip(col * (0.9 + 0.1 * ring[:, None]), 0, 1)
    if mat == "chitin":
        base = pal["chitin"]
        seg = 0.5 + 0.5 * np.sin(P[:, 2] * 1.6)
        gloss = smooth(0.6, 1.0, -N[:, 1]) * 0.25
        col = base * (0.75 + 0.25 * seg[:, None]) + gloss[:, None]
        mask = pattern_mask(pat, P, N, pscale, seed, sp)
        col = mix(col, pal["skin2"], mask * 0.7)
        return np.clip(col, 0, 1)
    if mat == "insectwing":
        base = pal["insectwing"]
        cells = cellular(P, 1.4, seed + 61)
        edge = smooth(0.0, 0.12, cells[1] - cells[0])
        return np.clip(base * (0.55 + 0.45 * edge[:, None]), 0, 1)
    if mat == "saddle":
        base = pal["saddle"]
        grain = fbm(P * 2, 1.0, seed + 71, 2)
        col = base * (0.75 + 0.35 * grain[:, None])
        s, t = loc["s"], loc["t"]
        stitch = ((np.abs(s - 0.1) < 0.04) | (np.abs(s - 0.9) < 0.04)) & (np.sin(t * 40) > 0)
        col[stitch] = np.array([0.85, 0.78, 0.6])
        buckle = (np.abs(s - 0.5) < 0.08) & (np.abs(t - 0.5) < 0.12)
        col[buckle] = np.array([0.72, 0.7, 0.62])
        return col
    if mat in ("glow", "fire", "lava"):
        base = pal[mat]
        n1 = fbm(P, 1.5, seed + 81, 3)
        return np.clip(base * (0.75 + 0.5 * n1[:, None]), 0, 1)
    if mat == "ice":
        base = pal["ice"]
        n1 = fbm(P, 2.0, seed + 82, 3)
        f1, f2, _ = cellular(P, 2.0, seed + 83)
        crack = smooth(0.0, 0.08, f2 - f1)
        return np.clip(base * (0.8 + 0.25 * n1[:, None]) * (0.75 + 0.25 * crack[:, None]) + 0.05, 0, 1)
    if mat == "rock":
        base = pal["rock"]
        n1 = fbm(P, 2.5, seed + 84, 3)
        f1, f2, cid = cellular(P, 3.0, seed + 85)
        crack = smooth(0.0, 0.1, f2 - f1)
        col = base * (0.7 + 0.35 * n1[:, None]) * (0.6 + 0.4 * crack[:, None]) * (0.9 + 0.2 * cid[:, None])
        if sp.get("lava_cracks"):
            lava = (1 - smooth(0.0, 0.06, f2 - f1))
            col = mix(col, pal["lava"], lava * 0.9)
        if sp.get("crystal_veins"):
            v = (1 - smooth(0.0, 0.05, f2 - f1))
            col = mix(col, pal["glow"], v * 0.8)
        return np.clip(col, 0, 1)
    if mat in ("metal", "tek"):
        base = pal[mat]
        s, t = loc["s"], loc["t"]
        panel = (np.abs(((s * loc["size"]) % 6) - 3) > 2.7) | (np.abs(((t * loc["size"]) % 6) - 3) > 2.7)
        col = base * (0.85 + 0.15 * value_noise(P, 2.0, seed + 91))[:, None]
        col[panel] = col[panel] * 0.7
        if mat == "tek":
            line = (np.abs(((s * loc["size"]) % 8) - 4) < 0.4)
            col[line] = pal["glow"]
        return np.clip(col, 0, 1)
    if mat == "egg":
        base = pal["egg"]
        sp_ = pattern_mask("speckle", P, N, 0.5, seed + 99, sp)
        return np.clip(mix(base, base * 0.55, sp_ * 0.8), 0, 1)
    if mat == "mouth":
        return np.tile(pal["mouth"], (n, 1)) * (0.85 + 0.2 * value_noise(P, 1.0, seed))[:, None]
    return np.tile(pal[mat], (n, 1))


def pattern_mask(pat, P, N, scale, seed, sp):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    wob = fbm(P, 5.0 * scale, seed + 101, 2)
    if pat == "stripes":      # slender irregular bands across the body axis, fading towards the belly
        v = np.sin((z * 0.5 / scale) + wob * 5.0 + np.abs(x) * 0.06 + y * 0.12)
        return smooth(0.72, 0.9, v) * smooth(-0.6, 0.4, -N[:, 1] + 0.25)
    if pat == "tiger":
        v = np.sin((z * 0.7 / scale) + wob * 6.0 + y * 0.25)
        return smooth(0.55, 0.8, v)
    if pat == "bands":        # broad rings (tails, snakes)
        v = np.sin(z * 0.35 / scale + wob * 1.5)
        return smooth(0.3, 0.6, v)
    if pat == "spots":
        f1, f2, cid = cellular(P, 3.0 * scale, seed + 103)
        return 1 - smooth(0.25, 0.4, f1)
    if pat == "rosettes":
        f1, f2, cid = cellular(P, 3.2 * scale, seed + 104)
        ring = np.abs(f1 - 0.33)
        return 1 - smooth(0.05, 0.12, ring)
    if pat == "mottled":
        v = fbm(P, 3.5 * scale, seed + 105, 3)
        return smooth(0.52, 0.65, v)
    if pat == "speckle":
        v = value_noise(P, 0.6 * scale, seed + 106)
        return smooth(0.82, 0.9, v)
    if pat == "dorsal":       # dark back stripe
        top = smooth(0.2, 0.9, -N[:, 1]) + smooth(4.0, 0.0, np.abs(x)) * 0.5
        return np.clip(top * smooth(0.3, 0.6, fbm(P, 6.0, seed + 107, 2) + 0.3), 0, 1)
    if pat == "saddle":       # dark saddle patch on the back
        d = np.sqrt((z - sp.get("saddle_z", 0)) ** 2 * 0.02 + x ** 2 * 0.0) + 0
        return smooth(0.1, 0.8, -N[:, 1]) * smooth(10, 4, np.abs(z - sp.get("saddle_z", 0)))
    if pat == "blotch":
        f1, f2, cid = cellular(P, 5.0 * scale, seed + 108)
        return (1 - smooth(0.35, 0.5, f1)) * (cid > 0.45)
    if pat == "chevrons":
        v = np.sin(z * 0.6 / scale + np.abs(x) * 0.6 + wob * 2)
        return smooth(0.5, 0.8, v)
    if pat == "zebra":
        v = np.sin(z * 1.0 / scale + wob * 7.0 + y * 0.5)
        return smooth(0.1, 0.25, v)
    if pat == "countershade":
        return np.zeros(len(P))
    if pat == "reticulated":
        f1, f2, cid = cellular(P, 2.5 * scale, seed + 109)
        return 1 - smooth(0.0, 0.12, f2 - f1)
    return np.zeros(len(P))


# ------------------------------------------------------------------ decals
def draw_decals(img, glow, c, face, u0, v0, uw, vh, k, pal, sp, model_ctx):
    """img arrays are (H, W, 4) floats.  u0..: face region in UV units; k: pixels per UV unit."""
    X0, Y0, W, H = int(u0 * k), int(v0 * k), int(uw * k), int(vh * k)
    if W <= 0 or H <= 0:
        return

    def ell(cx, cy, rx, ry, col, target=None, alpha=1.0):
        tgt = img if target is None else target
        yy, xx = np.mgrid[0:H, 0:W]
        m = ((xx + 0.5 - cx) / max(rx, 0.5)) ** 2 + ((yy + 0.5 - cy) / max(ry, 0.5)) ** 2 <= 1.0
        reg = tgt[Y0:Y0 + H, X0:X0 + W]
        reg[m, :3] = reg[m, :3] * (1 - alpha) + np.asarray(col)[:3] * alpha
        reg[m, 3] = 1.0

    for dec in c.decals:
        if dec == "eyes" and face in ("west", "east"):
            # eye sits near the front-top of the cranium side face. west: s runs z1->z0 (back->front), east: z0->z1
            front_s = 0.80 if face == "west" else 0.20
            cx, cy = front_s * W, 0.32 * H
            r = max(1.2, min(W, H) * sp.get("eye_size", 0.14))
            ell(cx, cy, r * 1.15, r, (0.12, 0.1, 0.08))
            ell(cx, cy, r * 0.85, r * 0.8, pal["eye"])
            if sp.get("slit_pupils", True):
                ell(cx, cy, r * 0.22, r * 0.75, (0.02, 0.02, 0.02))
            else:
                ell(cx, cy, r * 0.42, r * 0.42, (0.02, 0.02, 0.02))
            ell(cx - r * 0.3, cy - r * 0.35, r * 0.18, r * 0.18, (1, 1, 1), alpha=0.8)
            if sp.get("glow_eyes"):
                ell(cx, cy, r * 0.85, r * 0.8, pal["eye"], target=glow)
        elif dec in ("eyes_front", "eyes_big") and face == "north":
            if dec == "eyes_front":   # spiders: eight eyes
                for (ex, ey, rr) in ((0.35, 0.3, 0.1), (0.65, 0.3, 0.1), (0.2, 0.25, 0.06), (0.8, 0.25, 0.06),
                                     (0.42, 0.15, 0.05), (0.58, 0.15, 0.05), (0.28, 0.42, 0.05), (0.72, 0.42, 0.05)):
                    r = max(1.0, min(W, H) * rr)
                    ell(ex * W, ey * H, r, r, (0.05, 0.05, 0.06))
                    ell(ex * W - r * 0.3, ey * H - r * 0.3, r * 0.3, r * 0.3, (0.9, 0.2, 0.15) if sp.get("glow_eyes") else (0.8, 0.8, 0.85))
                    if sp.get("glow_eyes"):
                        ell(ex * W, ey * H, r, r, (0.9, 0.1, 0.1), target=glow)
            else:
                for ex in (0.22, 0.78):
                    r = max(1.2, min(W, H) * 0.18)
                    ell(ex * W, 0.4 * H, r, r * 1.1, (0.05, 0.05, 0.05))
                    ell(ex * W - r * 0.3, 0.4 * H - r * 0.3, r * 0.3, r * 0.3, (0.9, 0.9, 0.9))
        elif dec == "nostrils" and face == "north":
            for ex in (0.3, 0.7):
                ell(ex * W, 0.25 * H, max(0.8, W * 0.06), max(0.8, H * 0.08), (0.08, 0.05, 0.05))
        elif dec == "teeth_down" and face in ("west", "east", "north"):
            n = max(2, int(W / (1.6 * k)))
            tw = W / n
            th = max(1.5, min(H * 0.35, 2.2 * k))
            yy, xx = np.mgrid[0:H, 0:W]
            local = (xx % tw) / tw
            tri = (yy >= H - th * (1 - np.abs(local - 0.5) * 2)) & (yy < H)
            reg = img[Y0:Y0 + H, X0:X0 + W]
            reg[tri, :3] = pal["tusk"] * 0.95
        elif dec == "teeth_up" and face in ("west", "east", "north"):
            n = max(2, int(W / (1.6 * k)))
            tw = W / n
            th = max(1.5, min(H * 0.45, 2.2 * k))
            yy, xx = np.mgrid[0:H, 0:W]
            local = (xx % tw) / tw
            tri = yy < th * (1 - np.abs(local - 0.5) * 2)
            reg = img[Y0:Y0 + H, X0:X0 + W]
            reg[tri, :3] = pal["tusk"] * 0.92
        elif dec == "mouth" and face == "top":
            reg = img[Y0:Y0 + H, X0:X0 + W]
            yy, xx = np.mgrid[0:H, 0:W]
            inner = (xx > W * 0.15) & (xx < W * 0.85) & (yy > H * 0.1)
            reg[inner, :3] = pal["mouth"]
        elif dec == "maw" and face == "north":
            ell(W / 2, H / 2, W * 0.42, H * 0.42, pal["mouth"])
            for i in range(10):
                a = i * math.pi / 5
                ell(W / 2 + math.cos(a) * W * 0.34, H / 2 + math.sin(a) * H * 0.34, max(1, W * 0.05), max(1, H * 0.05), pal["tusk"])
        elif dec == "face" and face == "north":
            reg = img[Y0:Y0 + H, X0:X0 + W]
            yy, xx = np.mgrid[0:H, 0:W]
            m = ((xx - W / 2) / (W * 0.42)) ** 2 + ((yy - H * 0.55) / (H * 0.45)) ** 2 < 1
            reg[m, :3] = reg[m, :3] * 0.3 + pal["skin"] * 0.7 if sp.get("face_col") is None else hexrgb(sp["face_col"])
            for ex in (0.3, 0.7):
                ell(ex * W, 0.4 * H, max(1, W * 0.08), max(1, H * 0.08), (0.95, 0.92, 0.85))
                ell(ex * W, 0.4 * H, max(0.6, W * 0.04), max(0.6, H * 0.05), (0.1, 0.07, 0.05))


# ------------------------------------------------------------------ main painter
def paint(model, mats_world, pal, sp, seed, k=2):
    """Returns (rgba, glow_rgba) numpy arrays, size (tex_h*k, tex_w*k, 4)."""
    Wt, Ht = model.tex_w * k, model.tex_h * k
    img = np.zeros((Ht, Wt, 4))
    glow = np.zeros((Ht, Wt, 4))
    glow_mats = set(sp.get("glow_mats", ("glow", "fire", "lava")))
    for p in model.order:
        M = mats_world[p.name]
        R = M[:3, :3]
        # local "along" axis: from part pivot outwards, used for horn tips, feather tips
        for c in p.cubes:
            regs = face_regions(c)
            ext = max(c.w, c.h, c.d)
            for face, (u0, v0, uw, vh) in regs.items():
                X0, Y0, W, H = int(round(u0 * k)), int(round(v0 * k)), int(round(uw * k)), int(round(vh * k))
                if W <= 0 or H <= 0:
                    continue
                yy, xx = np.mgrid[0:H, 0:W]
                s = ((xx + 0.5) / W).ravel()
                t = ((yy + 0.5) / H).ravel()
                lx, ly, lz = face_point(c, face, s, t)
                L = np.stack([lx, ly, lz, np.ones_like(lx)], axis=1)
                Pw = (M @ L.T).T[:, :3]
                nrm = np.array(FACE_NORMAL[face], dtype=np.float64)
                Nw = np.tile(R @ nrm, (len(s), 1))
                dist = np.sqrt(lx ** 2 + ly ** 2 + lz ** 2)
                along = np.clip(dist / max(1.0, ext + 1.0), 0, 1)
                loc = {"s": s, "t": t, "along": along, "size": ext}
                col = shade_material(c.mat, Pw, Nw, loc, pal, sp, seed + int(c.tone * 1000), k)
                if c.tone:
                    col = col * (1.0 - c.tone * 0.5)
                # subtle edge darkening to read the cube edges
                edge = np.minimum(np.minimum(s, 1 - s) * W, np.minimum(t, 1 - t) * H)
                col = col * (0.9 + 0.1 * smooth(0.0, 1.5 * k, edge))[:, None]
                img[Y0:Y0 + H, X0:X0 + W, :3] = col.reshape(H, W, 3)
                img[Y0:Y0 + H, X0:X0 + W, 3] = 1.0
                if c.mat in glow_mats:
                    glow[Y0:Y0 + H, X0:X0 + W, :3] = col.reshape(H, W, 3)
                    glow[Y0:Y0 + H, X0:X0 + W, 3] = 1.0
                if c.decals:
                    draw_decals(img, glow, c, face, u0, v0, uw, vh, k, pal, sp, None)
    return np.clip(img, 0, 1), np.clip(glow, 0, 1)
