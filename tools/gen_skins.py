"""Base skins of Brainrot Heist: 25 skins x 5 matching blocks (frame, glass, floor, carpet, lamp), all drawn here as
16x16 textures (some animated), with their blockstates, block/item models and English names.
The skin list must match BrSkin.java. Run from the project root:  python3 tools/gen_skins.py [preview.png]"""
import colorsys, json, math, os, re, sys
import numpy as np
from PIL import Image

A = 'src/main/resources/assets/eggsteal/'
S = 16
PARTS = ['frame', 'glass', 'floor', 'carpet', 'lamp']


# ---------------------------------------------------------------- basics (images are float arrays (16, 16, 4) in 0..1)

def C(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def canvas(col, a=1.0):
    img = np.zeros((S, S, 4))
    img[..., :3] = col
    img[..., 3] = a
    return img


def mix(a, b, t):
    return a + (b - a) * t


def clip(img):
    return np.clip(img, 0, 1)


def px(img, x, y, col, a=1.0):
    x %= S
    y %= S
    img[y, x, :3] = img[y, x, :3] * (1 - a) + np.asarray(col) * a
    if a >= 1 or img[y, x, 3] < a:
        img[y, x, 3] = max(img[y, x, 3], a)


def rect(img, x0, y0, x1, y1, col, a=1.0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, col, a)


def mul(img, x0, y0, x1, y1, f):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img[y % S, x % S, :3] *= f


def bevel(img, x0, y0, x1, y1, hi=1.2, lo=0.75):
    for x in range(x0, x1 + 1):
        img[y0 % S, x % S, :3] *= hi
        img[y1 % S, x % S, :3] *= lo
    for y in range(y0 + 1, y1):
        img[y % S, x0 % S, :3] *= hi
        img[y % S, x1 % S, :3] *= lo


def grain(img, rng, amp=0.1):
    img[..., :3] *= 1 + (rng.random((S, S, 1)) - 0.5) * amp


def vnoise(rng, cells=4):
    """Tileable smooth value noise in 0..1."""
    g = rng.random((cells, cells))
    out = np.zeros((S, S))
    for y in range(S):
        for x in range(S):
            fx, fy = x * cells / S, y * cells / S
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a, b = g[y0 % cells, x0 % cells], g[y0 % cells, (x0 + 1) % cells]
            c, d = g[(y0 + 1) % cells, x0 % cells], g[(y0 + 1) % cells, (x0 + 1) % cells]
            out[y, x] = mix(mix(a, b, tx), mix(c, d, tx), ty)
    return out


def fbm(rng):
    n = vnoise(rng, 2) * 0.5 + vnoise(rng, 4) * 0.3 + vnoise(rng, 8) * 0.2
    return (n - n.min()) / (n.max() - n.min() + 1e-9)


def voronoi(points):
    """Distances to the nearest and second nearest point (wrapping), and the nearest point's index."""
    f1 = np.full((S, S), 99.0)
    f2 = np.full((S, S), 99.0)
    idx = np.zeros((S, S), int)
    for y in range(S):
        for x in range(S):
            for i, (pxx, pyy) in enumerate(points):
                dx = abs(x + 0.5 - pxx)
                dy = abs(y + 0.5 - pyy)
                dx, dy = min(dx, S - dx), min(dy, S - dy)
                d = math.hypot(dx, dy)
                if d < f1[y, x]:
                    f2[y, x] = f1[y, x]
                    f1[y, x] = d
                    idx[y, x] = i
                elif d < f2[y, x]:
                    f2[y, x] = d
    return f1, f2, idx


def hue(h, s=0.85, v=1.0):
    return np.array(colorsys.hsv_to_rgb(h % 1.0, s, v))


def glowdot(img, cx, cy, r, col, a=1.0):
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
            if d < 1:
                px(img, x, y, col, a * (1 - d) ** 1.5)


# ---------------------------------------------------------------- frame / floor / carpet styles

def bricks(rng, base, mortar, bw=8, bh=4, var=0.08, hi=1.18, lo=0.78, gr=0.10):
    img = canvas(mortar)
    for r in range(S // bh):
        off = (r % 2) * (bw // 2)
        for bx in range(S // bw):
            col = base * (1 + (rng.random() - 0.5) * var * 2)
            x0, y0 = bx * bw + off, r * bh
            rect(img, x0, y0, x0 + bw - 2, y0 + bh - 2, col)
            bevel(img, x0, y0, x0 + bw - 2, y0 + bh - 2, hi, lo)
    grain(img, rng, gr)
    return img


def tiles(rng, a, b, grout, n=2, var=0.05, hi=1.15, lo=0.82, checker=True, gap=1):
    img = canvas(grout)
    t = S // n
    for ty in range(n):
        for tx in range(n):
            col = (a if (tx + ty) % 2 == 0 or not checker else b) * (1 + (rng.random() - 0.5) * var * 2)
            x0, y0 = tx * t, ty * t
            rect(img, x0, y0, x0 + t - 1 - gap, y0 + t - 1 - gap, col)
            bevel(img, x0, y0, x0 + t - 1 - gap, y0 + t - 1 - gap, hi, lo)
    grain(img, rng, 0.06)
    return img


def planks(rng, base, dark, rows=4, vertical=False, gr=0.08):
    img = canvas(base)
    h = S // rows
    for r in range(rows):
        col = base * (1 + (rng.random() - 0.5) * 0.14)
        rect(img, 0, r * h, S - 1, r * h + h - 1, col)
        for x in range(S):  # wood grain
            if rng.random() < 0.18:
                px(img, x, r * h + 1 + rng.integers(0, max(1, h - 2)), dark, 0.35)
        rect(img, 0, r * h + h - 1, S - 1, r * h + h - 1, dark)
        j = (r * 7 + 3) % S  # plank joint
        rect(img, j, r * h, j, r * h + h - 2, dark, 0.8)
        mul(img, 0, r * h, S - 1, r * h, 1.12)
    grain(img, rng, gr)
    if vertical:
        img = np.transpose(img, (1, 0, 2)).copy()
    return img


def logs(rng, bark, inner, rows=2):
    img = canvas(bark)
    h = S // rows
    for r in range(rows):
        for y in range(h):
            t = y / (h - 1)
            f = 0.72 + 0.5 * math.sin(t * math.pi) ** 0.7
            for x in range(S):
                img[r * h + y, x, :3] = bark * f * (1 + (rng.random() - 0.5) * 0.18)
        for x in range(S):  # bark lines
            if rng.random() < 0.3:
                px(img, x, r * h + rng.integers(1, h - 1), bark * 0.55, 0.7)
        rect(img, 0, r * h, S - 1, r * h, bark * 0.45)
    return img


def stripes(rng, a, b, w=4, border=None):
    img = canvas(a)
    for y in range(S):
        for x in range(S):
            if ((x + y) // w) % 2 == 0:
                img[y, x, :3] = b
    for y in range(S):  # glossy highlight band
        for x in range(S):
            if 3 <= (x - y) % S <= 4:
                img[y, x, :3] = mix(img[y, x, :3], np.ones(3), 0.25)
    if border is not None:
        rect(img, 0, 0, S - 1, 0, border)
        rect(img, 0, S - 1, S - 1, S - 1, border * 0.8)
    grain(img, rng, 0.05)
    return img


def cells(rng, base, edge, n=7, shade=0.25, edge_w=0.9, pts=None):
    pts = pts or [(rng.random() * S, rng.random() * S) for _ in range(n)]
    f1, f2, idx = voronoi(pts)
    img = canvas(base)
    tints = [1 + (rng.random() - 0.5) * shade * 2 for _ in pts]
    for y in range(S):
        for x in range(S):
            if f2[y, x] - f1[y, x] < edge_w:
                img[y, x, :3] = edge
            else:
                img[y, x, :3] = base * tints[idx[y, x]] * (1.15 - 0.3 * f1[y, x] / 6)
    return img


def hexes(rng, base, edge, glow_edge=False):
    pts = [(4, 4), (12, 4), (0, 12), (8, 12)]
    f1, f2, idx = voronoi(pts)
    img = canvas(base)
    for y in range(S):
        for x in range(S):
            e = f2[y, x] - f1[y, x]
            if e < 1.0:
                img[y, x, :3] = edge
            else:
                img[y, x, :3] = base * (1.25 - 0.12 * f1[y, x])
    grain(img, rng, 0.06)
    return img


def scales(rng, base, dark, light):
    img = canvas(dark)
    for row in range(5):
        cy = row * 4
        off = (row % 2) * 4
        for k in range(3):
            cx = k * 8 + off
            for y in range(S):
                for x in range(S):
                    dx = (x + 0.5 - cx + S / 2) % S - S / 2
                    dy = y + 0.5 - cy
                    if dy >= 0 and math.hypot(dx, dy * 1.1) < 4.6:
                        t = min(1, dy / 5)
                        img[y, x, :3] = mix(light, base, t) if math.hypot(dx, dy * 1.1) < 3.8 else dark
    grain(img, rng, 0.08)
    return img


def marble(rng, base, vein, amount=0.6):
    n = fbm(rng)
    img = canvas(base)
    for y in range(S):
        for x in range(S):
            v = abs(math.sin((x * 0.35 + y * 0.6 + n[y, x] * 6)))
            if v < 0.12:
                img[y, x, :3] = mix(base, vein, amount * (1 - v / 0.12))
            img[y, x, :3] *= 0.94 + n[y, x] * 0.1
    return img


def stars(rng, bg1, bg2, nebula=None, count=10, t=0.0, seed=0):
    r = np.random.default_rng(seed)
    n = fbm(r)
    img = canvas(bg1)
    for y in range(S):
        for x in range(S):
            img[y, x, :3] = mix(bg1, bg2, n[y, x])
            if nebula is not None:
                img[y, x, :3] = mix(img[y, x, :3], nebula, max(0, n[y, x] - 0.55) * 1.6)
    for i in range(count):
        x, y = r.integers(0, S), r.integers(0, S)
        tw = 0.5 + 0.5 * math.sin(t * 2 * math.pi + i * 1.7)
        big = i % 4 == 0
        px(img, x, y, np.ones(3), 0.55 + 0.45 * tw)
        if big and tw > 0.5:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                px(img, x + dx, y + dy, np.array([0.8, 0.85, 1.0]), 0.45 * tw)
    return img


def frames(fn, n):
    return [fn(i / n) for i in range(n)]


# ---------------------------------------------------------------- glass (see-through, with a coloured border and a motif)

def glass(rng, tint, border, motif=None, mcol=None, alpha=0.38, ma=0.75):
    img = canvas(tint, alpha)
    for y in range(S):  # a soft diagonal shine
        for x in range(S):
            if 2 <= (x + y) % 22 <= 3:
                img[y, x, :3] = mix(img[y, x, :3], np.ones(3), 0.5)
                img[y, x, 3] = min(1, alpha + 0.15)
    rect(img, 0, 0, S - 1, 0, border)
    rect(img, 0, S - 1, S - 1, S - 1, border * 0.8)
    rect(img, 0, 0, 0, S - 1, border)
    rect(img, S - 1, 0, S - 1, S - 1, border * 0.8)
    if motif:
        motif(img, mcol if mcol is not None else border, ma)
    return img


def m_lattice(step=5):
    def f(img, c, a):
        for i in range(0, S, step):
            rect(img, i, 0, i, S - 1, c, a)
            rect(img, 0, i, S - 1, i, c, a)
    return f


def m_diamond(img, c, a):
    for y in range(S):
        for x in range(S):
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                px(img, x, y, c, a)


def m_dots(img, c, a):
    for cx, cy in ((4, 4), (12, 12), (12, 4), (4, 12)):
        for y in range(S):
            for x in range(S):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) < 1.7:
                    px(img, x, y, c, a)


def m_waves(img, c, a):
    for x in range(S):
        for base in (4, 10):
            y = base + round(1.4 * math.sin(x / S * 2 * math.pi * 2))
            px(img, x, y, c, a)


def m_leaf(img, c, a):
    for cx, cy, ang in ((5, 6, 0.6), (11, 10, -0.7)):
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                u = dx * math.cos(ang) + dy * math.sin(ang)
                v = -dx * math.sin(ang) + dy * math.cos(ang)
                if (u / 4) ** 2 + (v / 1.8) ** 2 < 1:
                    px(img, x, y, c * (0.85 if abs(v) < 0.5 else 1), a)


def m_flower(img, c, a):
    cx, cy = 8, 8
    for k in range(5):
        ang = k * 2 * math.pi / 5 - math.pi / 2
        fx, fy = cx + math.cos(ang) * 3, cy + math.sin(ang) * 3
        for y in range(S):
            for x in range(S):
                if math.hypot(x + 0.5 - fx, y + 0.5 - fy) < 2.1:
                    px(img, x, y, c, a)
    rect(img, 7, 7, 8, 8, C('ffe060'), 1)


def m_bat(img, c, a):
    shape = ["................", "................", "................", "................",
             "....#......#....", "...###.##.###...", "..######..#####.", ".##############.",
             "..############..", "...##.####.##...", ".......##.......", "................"]
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == '#':
                px(img, x, y + 2, c, a)


def m_hexline(img, c, a):
    f1, f2, _ = voronoi([(4, 4), (12, 4), (0, 12), (8, 12)])
    for y in range(S):
        for x in range(S):
            if f2[y, x] - f1[y, x] < 0.9:
                px(img, x, y, c, a)


def m_snowflake(img, c, a):
    cx, cy = 7.5, 7.5
    for k in range(6):
        ang = k * math.pi / 3
        for r in np.arange(0, 6.5, 0.4):
            px(img, int(cx + math.cos(ang) * r + 0.5), int(cy + math.sin(ang) * r + 0.5), c, a)
            if 3 < r < 3.5:
                for s in (-1, 1):
                    b = ang + s * 0.8
                    for q in np.arange(0, 2.2, 0.5):
                        px(img, int(cx + math.cos(ang) * r + math.cos(b) * q + 0.5), int(cy + math.sin(ang) * r + math.sin(b) * q + 0.5), c, a)


def m_gear(img, c, a):
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            outer = 5.3 + (1.3 if math.cos(ang * 8) > 0.3 else 0)
            if 2.0 < r < outer and not (r < 3.5):
                px(img, x, y, c, a)


def m_clover(img, c, a):
    for cx, cy in ((8, 5.2), (10.8, 8), (8, 10.8), (5.2, 8)):
        for y in range(S):
            for x in range(S):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) < 2.4:
                    px(img, x, y, c, a)
    rect(img, 8, 11, 8, 14, c * 0.7, a)


def m_cobweb(img, c, a):
    for k in range(8):
        ang = k * math.pi / 4
        for r in np.arange(0, 11, 0.5):
            px(img, int(8 + math.cos(ang) * r), int(8 + math.sin(ang) * r), c, a * 0.8)
    for rr in (2.5, 4.8, 7):
        for t in np.arange(0, 2 * math.pi, 0.08):
            px(img, int(8 + math.cos(t) * rr), int(8 + math.sin(t) * rr * 0.95), c, a * 0.6)


def m_cracks(img, c, a):
    r = np.random.default_rng(4)
    for _ in range(3):
        x, y = r.integers(0, S), r.integers(0, S)
        for _ in range(10):
            px(img, x, y, c, a)
            x += r.integers(-1, 2)
            y += 1


def m_grid(img, c, a):
    rect(img, 7, 0, 8, S - 1, c, a)
    rect(img, 0, 7, S - 1, 8, c, a)
    rect(img, 1, 1, S - 2, 1, c, a * 0.6)
    rect(img, 1, S - 2, S - 2, S - 2, c, a * 0.6)


def m_rainbow(img, c, a):
    for y in range(S):
        for x in range(S):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 15)
            if 4 <= r < 10 and y < 15:
                px(img, x, y, hue((r - 4) / 6 * 0.8), a)


def m_eye(img, c, a):
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            if (dx / 6) ** 2 + (dy / 3.2) ** 2 < 1:
                px(img, x, y, C('ffcc30'), a)
                if abs(dx) < 1:
                    px(img, x, y, C('100808'), 1)


def m_stars(img, c, a):
    r = np.random.default_rng(9)
    for _ in range(12):
        px(img, r.integers(1, 15), r.integers(1, 15), np.ones(3), r.random() * 0.5 + 0.5)


def m_cloud(img, c, a):
    for cx, cy, rr in ((5, 9, 3), (9, 8, 3.6), (12, 10, 2.5), (8, 11, 2.8)):
        for y in range(S):
            for x in range(S):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) < rr:
                    px(img, x, y, c, a)


def m_crystal(img, c, a):
    for y in range(S):
        for x in range(S):
            if abs(x - 8) + abs(y - 8) * 0.55 < 4 or (abs(x - 8) + abs(y - 8) * 0.55 < 4.6 and x < 8):
                px(img, x, y, c * (1.25 if x < 8 else 0.9), a)


def m_code(img, c, a):
    r = np.random.default_rng(12)
    for x in range(1, 15, 2):
        for y in range(1, 15):
            if r.random() < 0.45:
                px(img, x, y, c, a * (0.4 + r.random() * 0.6))


def m_radiation(img, c, a):
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            if r < 1.5 or (2.5 < r < 6 and math.cos(ang * 3 + math.pi / 2) > 0.5):
                px(img, x, y, c, a)


# ---------------------------------------------------------------- lamps (side, top)

def lamp(rng, cage, core, style='cage', flicker=0.0, t=0.0):
    k = 1 + flicker * math.sin(t * 2 * math.pi) * 0.5 + flicker * math.sin(t * 6 * math.pi + 1) * 0.3
    side = canvas(cage)
    top = canvas(cage)
    hot = clip(core * 1.25 * k + 0.2)
    for img in (side, top):
        for y in range(1, S - 1):
            for x in range(1, S - 1):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8
                img[y, x, :3] = clip(mix(hot, core * 0.75 * k, min(1, d * 1.2)))
    if style == 'cage':
        for img in (side,):
            rect(img, 0, 0, S - 1, 1, cage)
            rect(img, 0, S - 2, S - 1, S - 1, cage * 0.8)
            for x in (0, 5, 10, 15):
                rect(img, x, 0, x, S - 1, cage * (1.15 if x < 8 else 0.85))
            rect(img, 2, 2, 13, 2, cage * 0.6, 0.5)
        rect(top, 0, 0, S - 1, 0, cage)
        rect(top, 0, S - 1, S - 1, S - 1, cage)
        rect(top, 0, 0, 0, S - 1, cage)
        rect(top, S - 1, 0, S - 1, S - 1, cage)
        rect(top, 4, 4, 11, 4, cage, 0.9)
        rect(top, 4, 11, 11, 11, cage, 0.9)
        rect(top, 4, 4, 4, 11, cage, 0.9)
        rect(top, 11, 4, 11, 11, cage, 0.9)
    elif style == 'orb':
        for img in (side, top):
            for y in range(S):
                for x in range(S):
                    d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                    if d > 6.6:
                        img[y, x, :3] = cage * (1.1 if y < 8 else 0.85)
                    elif d > 5.6:
                        img[y, x, :3] = cage * 1.3
            px(img, 5, 5, np.ones(3), 0.9)
            px(img, 6, 5, np.ones(3), 0.6)
            px(img, 5, 6, np.ones(3), 0.6)
    elif style == 'jack':
        side = canvas(C('e07a10'))
        for x in range(S):
            for y in range(S):
                side[y, x, :3] *= 0.8 + 0.25 * abs(math.sin(x / S * math.pi * 3))
        face = ["................", "................", "................", "...##......##...", "..####....####..",
                "..####....####..", "................", ".......##.......", "................", "..############..",
                "..#.########.#..", "...##########...", "....#..##..#....", "................", "................", "................"]
        for y, row in enumerate(face):
            for x, ch in enumerate(row):
                if ch == '#':
                    side[y, x, :3] = clip(C('ffd040') * k)
        rect(side, 0, 0, S - 1, 0, C('6a3a08'))
        top = canvas(C('c86a10'))
        rect(top, 6, 6, 9, 9, C('3a6a10'))
        rect(top, 7, 7, 8, 8, C('5a8a20'))
    elif style == 'tube':
        side = canvas(cage)
        for x in (3, 4, 7, 8, 11, 12):
            for y in range(1, S - 1):
                side[y, x, :3] = clip(core * (1.2 if x in (4, 8, 12) else 0.9) * k + 0.15)
        rect(side, 0, 0, S - 1, 0, cage * 1.3)
        rect(side, 0, S - 1, S - 1, S - 1, cage * 0.7)
    elif style == 'crystal':
        side = canvas(cage)
        for y in range(S):
            for x in range(S):
                w = 6.5 - abs(y - 8) * 0.55
                if abs(x + 0.5 - 8) < w:
                    side[y, x, :3] = clip(core * (1.3 if x < 8 else 0.85) * k)
                    if abs(x + 0.5 - 8) > w - 1:
                        side[y, x, :3] = clip(core * 1.6)
    elif style == 'disco':
        for img in (side, top):
            for y in range(S):
                for x in range(S):
                    tile = ((x // 2) * 7 + (y // 2) * 13 + int(t * 8)) % 9
                    v = 0.55 + tile * 0.06
                    img[y, x, :3] = clip(np.array([v, v, v * 1.08]))
                    if tile == 8:
                        img[y, x, :3] = hue((x + y) / 16 + t, 0.6, 1)
            bevel(img, 0, 0, S - 1, S - 1)
    elif style == 'star':
        for img in (side, top):
            for y in range(S):
                for x in range(S):
                    dx, dy = abs(x + 0.5 - 8), abs(y + 0.5 - 8)
                    v = max(0, 1 - (dx * dy) ** 0.5 / 2.2) * max(0, 1 - max(dx, dy) / 8)
                    img[y, x, :3] = clip(mix(cage, core * k + 0.3, min(1, v * 1.4)))
            rect(img, 0, 0, S - 1, 0, cage * 1.4)
    return side, top


# ---------------------------------------------------------------- the 25 skins

def skin_textures(sid, rng):
    """Returns {part: frames} where frames is a list of RGBA arrays (one = still), plus 'lamp_top'."""
    T = {}
    anim = {}

    def put(part, fr, ft=None):
        T[part] = fr if isinstance(fr, list) else [fr]
        if ft:
            anim[part] = ft

    def lamps(cage, core, style='cage', flicker=0.0, n=1, ft=3):
        fr = [lamp(rng, cage, core, style, flicker, i / n) for i in range(n)]
        put('lamp', [f[0] for f in fr], ft if n > 1 else None)
        put('lamp_top', [f[1] for f in fr], ft if n > 1 else None)

    if sid == 'wood':
        put('frame', logs(rng, C('8a5a32'), C('c89a60')))
        put('glass', glass(rng, C('f8e8c0'), C('6a4020'), m_lattice(5), C('6a4020'), 0.3, 0.95))
        put('floor', planks(rng, C('b8864e'), C('6a4420')))
        rug = canvas(C('a82820'))
        for y in range(S):
            for x in range(S):
                if (abs(x - 7.5) + abs(y - 7.5)) % 8 < 1.5:
                    rug[y, x, :3] = C('2a6a30')
                elif (abs(x - 7.5) + abs(y - 7.5)) % 8 < 2.5:
                    rug[y, x, :3] = C('e8c060')
        grain(rug, rng, 0.14)
        put('carpet', rug)
        lamps(C('3a2a1a'), C('ffb040'), 'cage', 0.25, 4, 4)
    elif sid == 'candy':
        put('frame', stripes(rng, C('fff4f8'), C('ff4a8a'), 4))
        put('glass', glass(rng, C('ffc0e0'), C('ff4a8a'), m_dots, C('ffffff'), 0.35, 0.85))
        put('floor', tiles(rng, C('ffb0d8'), C('fff0f8'), C('e070a8'), 4, gap=0))
        c = canvas(C('7ae0ff'))
        for y in range(S):
            for x in range(S):
                ang = math.atan2(y + 0.5 - 8, x + 0.5 - 8)
                r = math.hypot(y + 0.5 - 8, x + 0.5 - 8)
                if math.sin(ang * 2 + r * 0.9) > 0.2:
                    c[y, x, :3] = C('ffffff') if r < 7.5 else C('9aeaff')
        put('carpet', c)
        lamps(C('ff5aa0'), C('ffd0ec'), 'orb')
    elif sid == 'desert':
        f = tiles(rng, C('e2c88a'), C('e2c88a'), C('a88a50'), 1, checker=False, gap=1)
        for y, row in enumerate(["....#####.....", "...#.....#....", "..#..###..#...", "...#.....#....", "....#####.....",
                                 ".......#......", ".....#####....", ".......#......", "......#.#.....", ".....#...#...."]):
            for x, ch in enumerate(row):
                if ch == '#':
                    px(f, x + 1, y + 3, C('8a6a34'), 0.9)
        put('frame', f)
        put('glass', glass(rng, C('ffe0a0'), C('c89a30'), m_diamond, C('d8a830'), 0.3, 0.9))
        put('floor', tiles(rng, C('f0d8a0'), C('e0c080'), C('b89858'), 2))
        c = canvas(C('2a4aa8'))
        for y in range(S):
            for x in range(S):
                d = abs(x - 7.5) + abs(y - 7.5)
                if 3 <= d < 4.2 or d < 1.2:
                    c[y, x, :3] = C('f0c040')
                elif 5.5 <= d < 6.5:
                    c[y, x, :3] = C('c83a2a')
        grain(c, rng, 0.12)
        put('carpet', c)
        lamps(C('8a6a30'), C('ff9a20'), 'cage', 0.35, 4, 3)
    elif sid == 'ocean':
        put('frame', bricks(rng, C('5ab8a8'), C('2a6a6a'), 8, 8, 0.15))
        put('glass', glass(rng, C('6ad8ff'), C('2a7a8a'), m_waves, C('d8ffff'), 0.35, 0.8))
        put('floor', tiles(rng, C('e8dca8'), C('d8c890'), C('5ab8a8'), 4, var=0.08))
        c = canvas(C('1a6aa8'))
        for y in range(S):
            for x in range(S):
                w = (y + 2 * math.sin(x / S * 2 * math.pi)) % 8
                if w < 1.5:
                    c[y, x, :3] = C('8ae8ff')
                elif w < 3:
                    c[y, x, :3] = C('3a9ad8')
        put('carpet', c)
        lamps(C('2a7a8a'), C('d8fff4'), 'orb')
    elif sid == 'jungle':
        f = bricks(rng, C('8a8a80'), C('4a4a44'), 8, 4, 0.1)
        n = fbm(rng)
        for y in range(S):
            for x in range(S):
                if n[y, x] > 0.6:
                    f[y, x, :3] = mix(f[y, x, :3], C('4a8a2a'), 0.75)
        for x in (3, 12):
            for y in range(0, 6 + x % 5):
                px(f, x + (y // 3) % 2, y, C('2a6a1a'), 0.95)
        put('frame', f)
        put('glass', glass(rng, C('a8e070'), C('4a6a2a'), m_leaf, C('3a8a2a'), 0.3, 0.9))
        g = tiles(rng, C('7a7a6a'), C('6a6a5a'), C('3a4a2a'), 2)
        n = fbm(rng)
        for y in range(S):
            for x in range(S):
                if n[y, x] > 0.55:
                    g[y, x, :3] = mix(g[y, x, :3], C('5a9a3a'), 0.6)
        put('floor', g)
        c = canvas(C('3a7a2a'))
        for y in range(S):
            for x in range(S):
                if (x + 2 * (y // 4)) % 4 == 0:
                    c[y, x, :3] = C('5aa83a')
                elif y % 4 == 0:
                    c[y, x, :3] = C('2a5a1a')
        grain(c, rng, 0.1)
        put('carpet', c)
        lamps(C('4a3a20'), C('d8ff70'), 'cage', 0.2, 4, 5)
    elif sid == 'cherry':
        f = planks(rng, C('e8a8b0'), C('a86070'), 4, vertical=True)
        rect(f, 0, 0, S - 1, 1, C('7a3a4a'))
        rect(f, 0, S - 2, S - 1, S - 1, C('6a2a3a'))
        put('frame', f)
        put('glass', glass(rng, C('ffe0ea'), C('7a3a4a'), m_flower, C('ff8ab0'), 0.3, 0.95))
        put('floor', planks(rng, C('f0c0c8'), C('b07a88')))
        c = canvas(C('fff4f6'))
        r = np.random.default_rng(6)
        for _ in range(9):
            x, y = r.integers(0, S), r.integers(0, S)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
                px(c, x + dx, y + dy, C('ff9ab8') if (dx, dy) != (0, 0) else C('ffe070'))
        grain(c, rng, 0.06)
        put('carpet', c)
        lamps(C('5a2a3a'), C('ffb0c8'), 'cage')
    elif sid == 'pumpkin':
        put('frame', bricks(rng, C('3a2a40'), C('e07a10'), 8, 4, 0.12, gr=0.14))
        put('glass', glass(rng, C('ffa040'), C('2a1a2a'), m_bat, C('1a0a1a'), 0.4, 1.0))
        put('floor', tiles(rng, C('2a1a2a'), C('c86a10'), C('1a0a1a'), 4))
        c = canvas(C('e07a10'))
        for y in range(S):
            for x in range(S):
                if (y + abs((x % 8) - 4)) % 8 < 2:
                    c[y, x, :3] = C('1a0a1a')
        grain(c, rng, 0.1)
        put('carpet', c)
        lamps(C('2a1a2a'), C('ffc040'), 'jack', 0.3, 4, 4)
    elif sid == 'honey':
        put('frame', hexes(rng, C('e8a020'), C('8a5a10')))
        put('glass', glass(rng, C('ffc840'), C('a86a10'), m_hexline, C('c88a20'), 0.45, 0.8))
        put('floor', hexes(rng, C('f8c860'), C('c8902a')))
        c = canvas(C('f0a820'))
        for x in range(S):
            L = 3 + (x * 5 % 7)
            for y in range(L):
                px(c, x, y, C('ffd860'))
            px(c, x, L, C('c8800a'))
        grain(c, rng, 0.08)
        put('carpet', c)
        lamps(C('8a5a10'), C('ffe080'), 'orb')
    elif sid == 'frost':
        f = bricks(rng, C('b8e0ff'), C('7ab0e0'), 8, 8, 0.08, 1.25, 0.85)
        for y in range(S):
            for x in range(S):
                if (x - y) % 16 in (2, 3):
                    f[y, x, :3] = mix(f[y, x, :3], np.ones(3), 0.55)
        put('frame', f)
        put('glass', glass(rng, C('d8f4ff'), C('8ac8f0'), m_snowflake, C('ffffff'), 0.3, 0.95))
        put('floor', tiles(rng, C('c8eaff'), C('a8d8f8'), C('88c0e8'), 2, hi=1.3))
        c = canvas(C('f4faff'))
        n = fbm(rng)
        for y in range(S):
            for x in range(S):
                c[y, x, :3] *= 0.9 + 0.1 * n[y, x]
        for i in range(6):
            px(c, (i * 5 + 3) % S, (i * 7 + 2) % S, C('a8d8ff'))
        put('carpet', c)
        lamps(C('6aa8e0'), C('e8f8ff'), 'crystal')
    elif sid == 'mushroom':
        f = canvas(C('c82a2a'))
        n = fbm(rng)
        for y in range(S):
            for x in range(S):
                f[y, x, :3] *= 0.85 + 0.25 * n[y, x]
        for cx, cy, r in ((4, 4, 2.4), (12, 6, 1.8), (6, 12, 2), (13, 13, 1.5), (0, 9, 1.4)):
            for y in range(S):
                for x in range(S):
                    dx = (x + 0.5 - cx + 8) % 16 - 8
                    if math.hypot(dx, y + 0.5 - cy) < r:
                        f[y, x, :3] = C('fff4e8')
        put('frame', f)
        put('glass', glass(rng, C('ffd0c0'), C('8a3a2a'), m_dots, C('ffffff'), 0.35, 0.9))
        put('floor', tiles(rng, C('8a7a8a'), C('7a6a7a'), C('5a4a5a'), 4, var=0.1))
        c = canvas(C('8a5a3a'))
        for y in range(S):
            for x in range(S):
                if (x * 3 + y * 5) % 11 == 0:
                    c[y, x, :3] = C('e8d0b0')
        grain(c, rng, 0.12)
        put('carpet', c)
        lamps(C('fff0e0'), C('ff6a5a'), 'orb')
    elif sid == 'copper':
        f = canvas(C('c8703a'))
        n = fbm(rng)
        for y in range(S):
            for x in range(S):
                f[y, x, :3] *= 0.9 + 0.2 * n[y, x]
                if n[y, x] > 0.75:
                    f[y, x, :3] = mix(f[y, x, :3], C('4ab89a'), 0.6)
        bevel(f, 0, 0, S - 1, S - 1, 1.3, 0.6)
        rect(f, 0, 7, S - 1, 7, C('7a3a1a'))
        for x, y in ((2, 2), (13, 2), (2, 12), (13, 12), (2, 5), (13, 5), (2, 9), (13, 9)):
            px(f, x, y, C('ffc890'))
            px(f, x + 1, y + 1, C('5a2a10'))
        put('frame', f)
        put('glass', glass(rng, C('ffd8a0'), C('8a4a20'), m_gear, C('a85a2a'), 0.32, 0.95))
        g = canvas(C('5a5a60'))
        for y in range(S):
            for x in range(S):
                if (x + y) % 4 == 0 and (x - y) % 8 == 0 or (x - y) % 4 == 0 and (x + y) % 8 == 4:
                    g[y, x, :3] = C('9a9aa0')
                    px(g, x + 1, y + 1, C('3a3a40'))
        grain(g, rng, 0.1)
        put('floor', g)
        c = canvas(C('4a3a30'))
        for y in (3, 4, 5, 10, 11, 12):
            rect(c, 0, y, S - 1, y, C('d8804a') * (1.25 if y in (3, 10) else 0.8 if y in (5, 12) else 1))
        for y in (2, 6, 9, 13):
            rect(c, 7, y, 8, y, C('a8a8b0'))
        put('carpet', c)
        lamps(C('7a3a1a'), C('ffd890'), 'cage', 0.1, 1)
    elif sid == 'emerald':
        f = bricks(rng, C('2ab860'), C('d8a830'), 8, 4, 0.1, 1.3, 0.7)
        for r in range(4):
            for bx in range(2):
                x = bx * 8 + (r % 2) * 4 + 3
                px(f, x, r * 4 + 1, C('b0ffd0'))
        put('frame', f)
        put('glass', glass(rng, C('a8ffc8'), C('c89a20'), m_clover, C('2a9a4a'), 0.3, 0.95))
        g = marble(rng, C('1a8a4a'), C('8af0b0'), 0.7)
        rect(g, 0, 0, S - 1, 0, C('e8b830'))
        rect(g, 0, 0, 0, S - 1, C('e8b830'))
        put('floor', g)
        c = canvas(C('2a8a3a'))
        for cx, cy in ((4, 4), (12, 12)):
            for y in range(S):
                for x in range(S):
                    for ox, oy in ((0, -1.4), (1.4, 0), (0, 1.4), (-1.4, 0)):
                        if math.hypot(x + 0.5 - cx - ox, y + 0.5 - cy - oy) < 1.4:
                            c[y, x, :3] = C('6ae86a')
        grain(c, rng, 0.08)
        put('carpet', c)
        lamps(C('c89a20'), C('a8ffc0'), 'orb')
    elif sid == 'royal':
        f = canvas(C('9a1020'))
        for y in range(S):
            for x in range(S):
                d = (abs((x % 8) - 4) + abs((y % 8) - 4))
                f[y, x, :3] *= 1.15 - d * 0.06
                if d == 0:
                    f[y, x, :3] = C('ffd040')
        rect(f, 0, 0, S - 1, 1, C('e8b830'))
        rect(f, 0, S - 2, S - 1, S - 1, C('b88a20'))
        put('frame', f)
        put('glass', glass(rng, C('ffd8a0'), C('d8a830'), m_diamond, C('e8b830'), 0.28, 0.95))
        put('floor', tiles(rng, C('f4f0ea'), C('1a1a20'), C('c8a030'), 2, hi=1.1))
        c = canvas(C('b81a2a'))
        rect(c, 0, 0, S - 1, 0, C('e8b830'))
        rect(c, 0, S - 1, S - 1, S - 1, C('e8b830'))
        for y in range(S):
            for x in range(S):
                if abs(x - 7.5) + abs(y - 7.5) in (3, 4):
                    c[y, x, :3] = C('e8b830')
        grain(c, rng, 0.1)
        put('carpet', c)
        lamps(C('d8a830'), C('fff0c0'), 'cage', 0.15, 4, 5)
    elif sid == 'haunted':
        f = planks(rng, C('4a3a3a'), C('1a1212'), 4)
        m_cracks(f, C('100808'), 0.9)
        for x, y in ((2, 1), (13, 5), (5, 9), (11, 13)):
            px(f, x, y, C('8a8a90'))
        put('frame', f)
        put('glass', glass(rng, C('9a8ab0'), C('2a2020'), m_cobweb, C('e8e8f0'), 0.3, 0.85))
        g = tiles(rng, C('3a3440'), C('2a2430'), C('16121a'), 2)
        m_cracks(g, C('0a080c'), 0.8)
        put('floor', g)
        c = canvas(C('4a1a5a'))
        for y in range(S):
            for x in range(S):
                if (abs(x - 7.5) + abs(y - 7.5)) % 6 < 1:
                    c[y, x, :3] = C('8a5aa8')
        grain(c, rng, 0.14)
        put('carpet', c)
        lamps(C('1a1a20'), C('5ae8ff'), 'cage', 0.35, 4, 4)
    elif sid == 'toxic':
        put('frame', stripes(rng, C('f0d020'), C('1a1a1a'), 4, C('8a8a90')))
        put('glass', glass(rng, C('a8ff60'), C('3a3a40'), m_radiation, C('2a4a10'), 0.35, 0.85))
        put('floor', tiles(rng, C('e8ece8'), C('d8dcd8'), C('6ac83a'), 4, var=0.03))

        def slime(t):
            c = canvas(C('5ae82a'))
            n = fbm(np.random.default_rng(21))
            for y in range(S):
                for x in range(S):
                    v = 0.5 + 0.5 * math.sin(n[y, x] * 9 + t * 2 * math.pi)
                    c[y, x, :3] = clip(mix(C('2aa81a'), C('b8ff6a'), v))
            for i, (bx, by) in enumerate(((4, 5), (11, 3), (8, 11), (2, 13))):
                rr = 1 + ((t * 4 + i) % 4) * 0.5
                for y in range(S):
                    for x in range(S):
                        if abs(math.hypot(x + 0.5 - bx, y + 0.5 - by) - rr) < 0.5:
                            px(c, x, y, C('eaffc0'), 0.9)
            return c
        put('carpet', frames(slime, 8), 3)
        lamps(C('3a3a40'), C('9aff3a'), 'tube', 0.2, 4, 3)
    elif sid == 'lava':
        pts = [(rng.random() * S, rng.random() * S) for _ in range(7)]

        def basalt(t, base=C('2a2226'), hot=C('ff6a10')):
            f1, f2, idx = voronoi(pts)
            img = canvas(base)
            k = 0.75 + 0.25 * math.sin(t * 2 * math.pi)
            for y in range(S):
                for x in range(S):
                    e = f2[y, x] - f1[y, x]
                    if e < 0.8:
                        img[y, x, :3] = clip(hot * (1.1 * k) + C('402000') * k)
                    elif e < 1.6:
                        img[y, x, :3] = mix(base, hot * 0.6, 0.45 * k)
                    else:
                        img[y, x, :3] = base * (0.85 + 0.3 * ((idx[y, x] * 37) % 5) / 5)
            return img
        put('frame', frames(basalt, 8), 4)
        put('glass', glass(rng, C('ff7a20'), C('2a2226'), m_cracks, C('ffd060'), 0.55, 0.9))
        put('floor', frames(lambda t: basalt((t + 0.5) % 1, C('3a2a28'), C('ff4a00')), 8), 5)

        def flow(t):
            c = canvas(C('ff6a00'))
            n = fbm(np.random.default_rng(31))
            for y in range(S):
                for x in range(S):
                    v = n[(y - int(t * S)) % S, x]
                    c[y, x, :3] = clip(mix(C('c82a00'), C('ffe040'), v ** 1.5))
            return c
        put('carpet', frames(flow, 16), 2)
        lamps(C('2a2226'), C('ff8a20'), 'cage', 0.4, 4, 3)
    elif sid == 'neon':
        def panel(t, col=C('2ae8ff'), col2=C('ff3ad8')):
            img = canvas(C('101018'))
            k = 0.7 + 0.3 * math.sin(t * 2 * math.pi)
            for y in range(S):
                for x in range(S):
                    img[y, x, :3] *= 0.9 + 0.2 * ((x + y) % 2)
            rect(img, 1, 1, S - 2, 1, clip(col * k + 0.1))
            rect(img, 1, S - 2, S - 2, S - 2, clip(col * k + 0.1))
            rect(img, 1, 1, 1, S - 2, clip(col2 * k + 0.1))
            rect(img, S - 2, 1, S - 2, S - 2, clip(col2 * k + 0.1))
            rect(img, 5, 7, 10, 8, clip(col2 * (1.2 - k * 0.4)))
            return img
        put('frame', frames(panel, 8), 3)
        put('glass', glass(rng, C('3a6aff'), C('2ae8ff'), m_grid, C('ff3ad8'), 0.25, 0.9))
        g = canvas(C('14141e'))
        rect(g, 0, 0, S - 1, 0, C('2a8aa8'))
        rect(g, 0, 0, 0, S - 1, C('2a8aa8'))
        rect(g, 8, 0, 8, S - 1, C('1a4a5a'))
        rect(g, 0, 8, S - 1, 8, C('1a4a5a'))
        grain(g, rng, 0.1)
        put('floor', g)

        def stripe(t):
            c = canvas(C('101018'))
            for y in range(S):
                for x in range(S):
                    v = (x - y + int(t * 16)) % 8
                    if v < 2:
                        c[y, x, :3] = C('ff3ad8') if (x - y + int(t * 16)) % 16 < 8 else C('2ae8ff')
            return c
        put('carpet', frames(stripe, 16), 2)
        lamps(C('101018'), C('2ae8ff'), 'tube', 0.25, 4, 2)
    elif sid == 'disco':
        def mirror(t):
            img = canvas(C('a8a8b8'))
            for y in range(S):
                for x in range(S):
                    tile = ((x // 4) * 5 + (y // 4) * 3) % 7
                    img[y, x, :3] = np.array([0.5 + tile * 0.07] * 3) * np.array([0.95, 0.95, 1.05])
                    if x % 4 == 3 or y % 4 == 3:
                        img[y, x, :3] = C('5a5a6a')
            sx = int(t * 20) - 2
            for y in range(S):
                for x in range(S):
                    if abs((x + y) - sx * 2) < 2:
                        img[y, x, :3] = mix(img[y, x, :3], np.ones(3), 0.6)
            return clip(img)
        put('frame', frames(mirror, 10), 3)
        put('glass', glass(rng, C('ff8af0'), C('8a8a9a'), m_diamond, C('8af0ff'), 0.32, 0.9))
        cols = [C('ff3a6a'), C('3a8aff'), C('ffd83a'), C('3aff8a'), C('c83aff'), C('ff8a2a')]

        def dance(t):
            img = canvas(C('202028'))
            step = int(t * 6)
            for ty in range(2):
                for tx in range(2):
                    col = cols[(tx * 2 + ty * 3 + step) % 6]
                    on = (tx + ty + step) % 2 == 0
                    x0, y0 = tx * 8, ty * 8
                    rect(img, x0 + 1, y0 + 1, x0 + 6, y0 + 6, clip(col * (1.0 if on else 0.35) + (0.15 if on else 0)))
                    if on:
                        rect(img, x0 + 2, y0 + 2, x0 + 3, y0 + 3, clip(col + 0.4))
            return img
        put('floor', frames(dance, 6), 8)
        c = canvas(C('202028'))
        for y in range(S):
            for x in range(S):
                c[y, x, :3] = hue(((x + y) // 4) / 4 * 0.75, 0.75, 0.95)
        put('carpet', c)
        lamps(C('a8a8b8'), C('ffffff'), 'disco', 0, 8, 3)
    elif sid == 'matrix':
        def code(t, base=C('020a04'), col=C('3aff6a')):
            img = canvas(base)
            r = np.random.default_rng(41)
            heads = r.integers(0, S, 8)
            lens = r.integers(4, 10, 8)
            for i, x in enumerate(range(0, S, 2)):
                head = (heads[i] + int(t * S)) % S
                for k in range(lens[i]):
                    y = (head - k) % S
                    v = 1 - k / lens[i]
                    img[y, x, :3] = clip(col * v + (0.6 if k == 0 else 0))
                    if r.random() < 0.3:
                        img[y, x + 1 if x + 1 < S else x, :3] = col * v * 0.4
            return img
        put('frame', frames(code, 16), 2)
        put('glass', glass(rng, C('1a6a2a'), C('0a2a10'), m_code, C('6aff8a'), 0.4, 0.8))
        g = canvas(C('0a2a14'))
        for y in (3, 11):
            rect(g, 0, y, S - 1, y, C('1a8a3a'))
        for x in (5, 13):
            rect(g, x, 0, x, S - 1, C('1a8a3a'))
        for x, y in ((5, 3), (13, 11), (5, 11), (13, 3)):
            rect(g, x - 1, y - 1, x + 1, y + 1, C('c8b040'))
        grain(g, rng, 0.1)
        put('floor', g)

        def glowc(t):
            c = canvas(C('0a3a14'))
            k = 0.6 + 0.4 * math.sin(t * 2 * math.pi)
            for y in range(S):
                for x in range(S):
                    if x in (0, 15) or (y + int(t * 16)) % 8 == 0:
                        c[y, x, :3] = clip(C('3aff6a') * k)
            return c
        put('carpet', frames(glowc, 8), 3)
        lamps(C('0a2a10'), C('6aff8a'), 'tube', 0.3, 4, 3)
    elif sid == 'amethyst':
        put('frame', cells(rng, C('9a5ad8'), C('e8c0ff'), 7, 0.3, 0.8))
        put('glass', glass(rng, C('c88aff'), C('6a3aa8'), m_crystal, C('f0d8ff'), 0.35, 0.9))
        put('floor', tiles(rng, C('7a4ab8'), C('6a3aa8'), C('3a1a6a'), 2, hi=1.3))
        c = canvas(C('5a2a8a'))
        n = fbm(rng)
        for y in range(S):
            for x in range(S):
                c[y, x, :3] *= 0.8 + 0.4 * n[y, x]
                if (x + y) % 5 == 0 and (x - y) % 3 == 0:
                    c[y, x, :3] = C('e0b0ff')
        put('carpet', c)
        lamps(C('4a2a7a'), C('e8b0ff'), 'crystal', 0.2, 4, 4)
    elif sid == 'heaven':
        f = marble(rng, C('f4f2f0'), C('d8b040'), 0.8)
        rect(f, 0, 0, S - 1, 0, C('e8c040'))
        rect(f, 0, S - 1, S - 1, S - 1, C('c8a030'))
        put('frame', f)
        put('glass', glass(rng, C('e8f4ff'), C('e8c040'), m_cloud, C('ffffff'), 0.28, 0.85))
        g = canvas(C('e8f0ff'))
        n = fbm(rng)
        for y in range(S):
            for x in range(S):
                g[y, x, :3] = mix(C('c8d8f8'), C('ffffff'), n[y, x] ** 0.6)
        put('floor', g)
        c = canvas(C('f0c840'))
        for y in range(S):
            for x in range(S):
                if (x + y) % 6 < 2:
                    c[y, x, :3] = C('fff0a8')
        rect(c, 0, 0, S - 1, 0, C('ffffff'))
        rect(c, 0, S - 1, S - 1, S - 1, C('ffffff'))
        put('carpet', c)
        lamps(C('e8c040'), C('fffff0'), 'orb')
    elif sid == 'dragon':
        put('frame', scales(rng, C('5a2a8a'), C('1a0a2a'), C('a86ae8')))
        put('glass', glass(rng, C('8a3ad8'), C('2a1040'), m_eye, None, 0.32, 0.95))
        put('floor', tiles(rng, C('1a1424'), C('241a30'), C('5a2a8a'), 2, hi=1.4, lo=0.7))
        c = canvas(C('3a1a5a'))
        for y in range(S):
            for x in range(S):
                if (y % 4 == 0 and (x // 4 + y // 4) % 2 == 0) or (y % 4 == 2 and (x // 4 + y // 4) % 2 == 1):
                    c[y, x, :3] = C('e8b830')
        grain(c, rng, 0.12)
        put('carpet', c)
        lamps(C('1a0a2a'), C('d86aff'), 'crystal', 0.3, 4, 4)
    elif sid == 'galaxy':
        put('frame', frames(lambda t: stars(rng, C('0a0a28'), C('2a1050'), C('c83aa8'), 12, t, 51), 8), 4)
        put('glass', glass(rng, C('2a1a6a'), C('8a6aff'), m_stars, None, 0.45, 1))
        put('floor', frames(lambda t: stars(rng, C('080818'), C('1a1a48'), C('3a6ad8'), 9, t, 52), 8), 5)
        put('carpet', frames(lambda t: stars(rng, C('3a0a5a'), C('6a2aa8'), C('ff6ad8'), 14, t, 53), 8), 3)
        lamps(C('1a1048'), C('c8b0ff'), 'star', 0.3, 8, 3)
    elif sid == 'rainbow':
        def rb(t, style):
            img = canvas(np.ones(3))
            for y in range(S):
                for x in range(S):
                    if style == 'frame':
                        img[y, x, :3] = hue((y // 4) / 4 * 0.85 + t)
                    elif style == 'floor':
                        img[y, x, :3] = hue(((x // 4) + (y // 4)) / 8 + t, 0.7)
                    else:
                        img[y, x, :3] = hue((x + y) / 32 + t, 0.8)
            if style == 'frame':
                for r in range(4):
                    for x in range(S):
                        img[r * 4, x, :3] *= 1.2
                        img[r * 4 + 3, x, :3] *= 0.7
                    j = (r % 2) * 8 + 4
                    rect(img, j, r * 4, j, r * 4 + 3, np.ones(3) * 0.95)
            elif style == 'floor':
                for i in range(0, S, 4):
                    rect(img, i, 0, i, S - 1, np.ones(3), 0.6)
                    rect(img, 0, i, S - 1, i, np.ones(3), 0.6)
            return clip(img)
        put('frame', frames(lambda t: rb(t, 'frame'), 12), 3)
        put('glass', glass(rng, C('ffffff'), C('ff6aa8'), m_rainbow, None, 0.25, 0.9))
        put('floor', frames(lambda t: rb(t, 'floor'), 12), 4)
        put('carpet', frames(lambda t: rb(t, 'carpet'), 16), 2)
        fr = []
        for i in range(12):
            s, tp = lamp(rng, np.ones(3) * 0.95, hue(i / 12, 0.6), 'orb')
            fr.append((s, tp))
        put('lamp', [f[0] for f in fr], 3)
        put('lamp_top', [f[1] for f in fr], 3)
    elif sid == 'golden':
        def gold(t):
            img = bricks(np.random.default_rng(61), C('f0c030'), C('a87a10'), 8, 4, 0.1, 1.3, 0.7)
            for r in range(4):
                x = (r % 2) * 8 + 3
                rect(img, x, r * 4 + 1, x + 1, r * 4 + 1, C('c8f8ff'))
                px(img, x, r * 4 + 1, np.ones(3))
            sx = t * 40 - 8
            for y in range(S):
                for x in range(S):
                    if abs(x + y - sx) < 2:
                        img[y, x, :3] = mix(img[y, x, :3], C('fffff0'), 0.65)
            return clip(img)
        put('frame', frames(gold, 12), 2)
        put('glass', glass(rng, C('ffe080'), C('d8a020'), m_lattice(4), C('f0c030'), 0.32, 0.9))
        g = tiles(rng, C('f0c030'), C('e8b020'), C('a87a10'), 2, hi=1.3, lo=0.7)
        for cx, cy in ((3, 3), (11, 11), (11, 3), (3, 11)):
            for y in range(S):
                for x in range(S):
                    if abs(x + 0.5 - cx - 0.5) + abs(y + 0.5 - cy - 0.5) < 1.6:
                        g[y, x, :3] = C('b8f4ff') if x <= cx else C('6ad8f0')
        put('floor', g)

        def dia(t):
            c = canvas(C('5ad8f0'))
            for y in range(S):
                for x in range(S):
                    d = (abs((x % 8) - 3.5) + abs((y % 8) - 3.5))
                    c[y, x, :3] = mix(C('e8ffff'), C('3aa8d8'), min(1, d / 4))
                    if d > 3.6:
                        c[y, x, :3] = C('f0c030')
            sx = t * 40 - 8
            for y in range(S):
                for x in range(S):
                    if abs(x - y + 8 - sx) < 1.5:
                        c[y, x, :3] = mix(c[y, x, :3], np.ones(3), 0.6)
            return clip(c)
        put('carpet', frames(dia, 12), 2)
        lamps(C('f0c030'), C('fffff0'), 'star', 0.25, 8, 3)
    else:
        raise ValueError(sid)
    return T, anim


# ---------------------------------------------------------------- output

def to_img(frames_):
    h = S * len(frames_)
    out = np.zeros((h, S, 4))
    for i, f in enumerate(frames_):
        out[i * S:(i + 1) * S] = clip(f)
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), 'RGBA')


def skins_from_java():
    src = open('src/main/java/com/eggsteal/brainrot/BrSkin.java').read()
    return re.findall(r'new BrSkin\((\d+), "(\w+)", "([^"]+)"', src)


def main():
    skins = [(int(i), sid, name) for i, sid, name in skins_from_java() if int(i) > 0]
    assert len(skins) == 25, len(skins)
    os.makedirs(A + 'textures/block/brskin', exist_ok=True)
    lang = json.load(open(A + 'lang/en_us.json'))
    sheet = []
    for idx, sid, name in skins:
        rng = np.random.default_rng(1000 + idx)
        tex, anim = skin_textures(sid, rng)
        for part, fr in tex.items():
            fn = f'{sid}_{part}'
            to_img(fr).save(A + f'textures/block/brskin/{fn}.png')
            mm = A + f'textures/block/brskin/{fn}.png.mcmeta'
            if len(fr) > 1:
                json.dump({"animation": {"frametime": anim.get(part, 3), "interpolate": part in ('frame', 'floor', 'lamp', 'lamp_top') and sid in ('lava', 'galaxy', 'rainbow', 'toxic')}}, open(mm, 'w'))
            elif os.path.exists(mm):
                os.remove(mm)
        for part in PARTS:
            n = f'brskin_{sid}_{part}'
            t = f'eggsteal:block/brskin/{sid}_{part}'
            if part == 'lamp':
                model = {"parent": "minecraft:block/cube_column", "textures": {"side": t, "end": t + "_top"}}
            else:
                model = {"parent": "minecraft:block/cube_all", "textures": {"all": t}}
            if part == 'glass':
                model["render_type"] = "minecraft:translucent"
            json.dump({"variants": {"": {"model": f"eggsteal:block/{n}"}}}, open(A + f'blockstates/{n}.json', 'w'))
            json.dump(model, open(A + f'models/block/{n}.json', 'w'))
            json.dump({"parent": f"eggsteal:block/{n}"}, open(A + f'models/item/{n}.json', 'w'))
            lang[f'block.eggsteal.{n}'] = f'{name} {part.capitalize()}'
        sheet.append((name, tex))
    json.dump(lang, open(A + 'lang/en_us.json', 'w'), indent=1)
    if len(sys.argv) > 1:
        preview(sheet, sys.argv[1])
    print('skins:', len(skins), 'blocks:', len(skins) * 5)


def preview(sheet, path):
    """A wall seen from the front (frame / glass / glass / frame / lamps) over the floor and carpet seen from above."""
    from PIL import ImageDraw
    z = 3
    cw, ch = 7 * S * z + 8, 9 * S * z + 18
    W = Image.new('RGB', (cw * 5, ch * 5), (40, 40, 48))
    d = ImageDraw.Draw(W)
    for n, (name, tex) in enumerate(sheet):
        ox, oy = (n % 5) * cw, (n // 5) * ch
        def tile(part, gx, gy, bg=(150, 190, 230)):
            im = to_img(tex[part][:1]).resize((S * z, S * z), Image.NEAREST)
            base = Image.new('RGBA', im.size, bg + (255,))
            base.alpha_composite(im)
            W.paste(base.convert('RGB'), (ox + gx * S * z, oy + gy * S * z))
        for gx in range(7):
            if gx in (0, 6):
                tile('lamp', gx, 0)
            elif gx % 3 == 0:
                tile('lamp', gx, 0)
            for gy in (1, 4):
                tile('frame', gx, gy)
            for gy in (2, 3):
                tile('glass', gx, gy)
            for gy in (5, 6, 7, 8):
                tile('carpet' if gx == 3 or gy == 5 else 'floor', gx, gy)
        tile('lamp_top', 6, 8)
        d.text((ox + 4, oy + 9 * S * z + 2), name, fill=(255, 255, 255))
    W.save(path)


if __name__ == '__main__':
    main()
