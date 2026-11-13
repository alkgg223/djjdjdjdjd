"""Textures for the sky events: planets, a comet, meteors and fireballs, the space station and satellites, aurora rays,
the Milky Way, supernova and nova shells, coronal loops, and the red sprites and blue jets above thunderstorms."""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'realweather', 'textures', 'environment', 'space')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1977)


def save(rgba, name):
    Image.fromarray(np.clip(rgba * 255, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(OUT, name))
    print('saved', name)


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(float)
    return (x + 0.5) / w, (y + 0.5) / h


def noise(w, h, cells_x, cells_y, seed, wrap_x=False):
    r = np.random.default_rng(seed)
    g = r.random((cells_y + 1, cells_x + 1))
    if wrap_x:
        g[:, -1] = g[:, 0]
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(img, dtype=float) / 255


def fbm(w, h, base, octaves, seed, wrap_x=False, gain=0.5):
    s = np.zeros((h, w))
    a = 1.0
    tot = 0.0
    cx = base
    for o in range(octaves):
        cy = max(1, int(round(cx * h / w)))
        s += a * noise(w, h, cx, cy, seed + o * 17, wrap_x)
        tot += a
        a *= gain
        cx *= 2
    return s / tot


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ point glows
def glow():
    x, y = grid(128, 128)
    r = np.hypot(x - 0.5, y - 0.5) * 2
    a = np.exp(-r * 4.5) * 0.85 + np.exp(-(r / 0.06) ** 2) * 0.6
    a *= 1 - smooth(0.85, 1.0, r)
    save(np.dstack([np.ones_like(a)] * 3 + [np.clip(a, 0, 1)]), 'glow.png')


def star():
    """A brilliant star: a core, a halo and four thin diffraction spikes."""
    x, y = grid(256, 256)
    dx, dy = x - 0.5, y - 0.5
    r = np.hypot(dx, dy) * 2
    a = np.exp(-(r / 0.035) ** 2) + np.exp(-r * 9) * 0.5
    for ang in (0, math.pi / 2):
        ux, uy = math.cos(ang), math.sin(ang)
        along = np.abs(dx * ux + dy * uy) * 2
        across = np.abs(-dx * uy + dy * ux) * 2
        a += np.exp(-(across / 0.006) ** 2) * np.exp(-along * 3.2) * 0.9
    for ang in (math.pi / 4, 3 * math.pi / 4):
        ux, uy = math.cos(ang), math.sin(ang)
        along = np.abs(dx * ux + dy * uy) * 2
        across = np.abs(-dx * uy + dy * ux) * 2
        a += np.exp(-(across / 0.004) ** 2) * np.exp(-along * 6) * 0.35
    a *= 1 - smooth(0.9, 1.0, r)
    save(np.dstack([np.ones_like(a)] * 3 + [np.clip(a, 0, 1)]), 'star.png')


# ------------------------------------------------------------------ meteors, fireballs
def meteor():
    """A streak: the bright head at the right end, the train fading out to the left."""
    w, h = 256, 32
    x, y = grid(w, h)
    head = 0.93
    along = np.clip(x / head, 0, 1)
    width = 0.08 + 0.22 * along ** 2
    across = np.abs(y - 0.5) / width
    a = np.exp(-across ** 2 * 3) * along ** 2.2
    d = np.hypot((x - head) * w / h, y - 0.5)
    a += np.exp(-(d / 0.18) ** 2) * 1.2
    a[x > head] = np.exp(-(d[x > head] / 0.16) ** 2) * 1.2
    save(np.dstack([np.ones_like(a)] * 3 + [np.clip(a, 0, 1)]), 'meteor.png')


def fireball():
    """A bolide: a white-green head, a flaming plume going orange and red, and grey smoke behind it."""
    w, h = 512, 128
    x, y = grid(w, h)
    head = 0.9
    t = np.clip((head - x) / head, 0, 1)  # 0 at the head, 1 at the end of the tail
    turb = fbm(w, h, 24, 5, 11) - 0.5
    spread = 0.05 + 0.32 * t ** 0.8
    across = np.abs(y - 0.5 + turb * 0.18 * t) / spread
    body = np.exp(-across ** 2 * 2.2) * (1 - t) ** 1.3 * (0.7 + 0.6 * fbm(w, h, 40, 4, 12))
    d = np.hypot((x - head) * w / h, y - 0.5)
    core = np.exp(-(d / 0.22) ** 2)
    a = np.clip(body * 1.3 + core * 1.4, 0, 1)
    a[x > head + 0.04] = core[x > head + 0.04]
    # Colour: white-hot core with a touch of green, yellow, orange, red, then smoky.
    heat = np.clip(core * 1.5 + (1 - t) ** 2 * body, 0, 1)
    r = np.clip(0.55 + 0.45 * heat + 0.4 * (1 - t), 0, 1)
    g = np.clip(0.18 + 0.75 * heat ** 1.2 + core * 0.2, 0, 1)
    b = np.clip(0.08 + 0.7 * heat ** 2.5 + core * 0.15, 0, 1)
    smoke = smooth(0.55, 1.0, t)
    r = r * (1 - smoke) + 0.45 * smoke
    g = g * (1 - smoke) + 0.42 * smoke
    b = b * (1 - smoke) + 0.42 * smoke
    a *= 1 - smooth(0.75, 1.0, t) * 0.7
    save(np.dstack([r, g, b, a]), 'fireball.png')


# ------------------------------------------------------------------ comet
def comet():
    """The head (coma) at the left; a broad curved yellow-white dust tail and a straight thin blue ion tail."""
    w, h = 512, 256
    x, y = grid(w, h)
    hx, hy = 0.07, 0.55
    dx = (x - hx) * 2
    dy = (y - hy)
    t = np.clip(dx / 1.86, 0, 1)
    curve = 0.12 * t ** 1.6
    width_d = 0.02 + 0.26 * t ** 0.85
    dust = np.exp(-((dy + curve) / width_d) ** 2) * (1 - t) ** 1.5 * (dx > 0)
    dust *= 0.75 + 0.25 * fbm(w, h, 30, 3, 21)
    width_i = 0.006 + 0.03 * t
    streak = 0.75 + 0.5 * (fbm(w, h, 60, 3, 22) - 0.5)
    ion = np.exp(-(dy / width_i) ** 2) * (1 - t) ** 0.9 * (dx > 0) * streak
    d = np.hypot((x - hx) * w / h, y - hy)
    coma = np.exp(-(d / 0.06) ** 2) * 1.3 + np.exp(-d / 0.05) * 0.5
    dust = dust * 1.35
    a = np.clip(dust * 0.85 + ion * 0.7 + coma, 0, 1)
    r = np.clip((dust * 1.0 + ion * 0.35 + coma * 0.9) / np.maximum(a, 1e-3), 0, 1)
    g = np.clip((dust * 0.95 + ion * 0.6 + coma * 1.0) / np.maximum(a, 1e-3), 0, 1)
    b = np.clip((dust * 0.8 + ion * 1.0 + coma * 0.95) / np.maximum(a, 1e-3), 0, 1)
    save(np.dstack([r, g, b, a]), 'comet.png')


# ------------------------------------------------------------------ planets
def disc_shade(n):
    x, y = grid(n, n)
    dx, dy = (x - 0.5) * 2 / 0.92, (y - 0.5) * 2 / 0.92
    r = np.hypot(dx, dy)
    inside = smooth(1.0, 0.96, r)
    limb = np.sqrt(np.clip(1 - r * r, 0, 1)) ** 0.45
    return dx, dy, r, inside, limb


def planet_cell(kind, n=128):
    dx, dy, r, inside, limb = disc_shade(n)
    if kind == 'mercury':
        nz = fbm(n, n, 8, 5, 31)
        base = 0.55 + 0.35 * nz
        col = [base * 0.95, base * 0.9, base * 0.85]
    elif kind == 'venus':
        nz = fbm(n, n, 4, 3, 32)
        col = [0.98 * np.ones_like(nz), 0.93 + 0.04 * nz, 0.78 + 0.08 * nz]
    elif kind == 'mars':
        nz = fbm(n, n, 6, 5, 33)
        dark = smooth(0.55, 0.7, nz)
        col = [0.86 - 0.3 * dark, 0.42 - 0.15 * dark, 0.22 - 0.08 * dark]
        cap = smooth(-0.72, -0.85, dy)
        col = [c * (1 - cap) + cap * 0.97 for c in col]
    elif kind == 'jupiter':
        turb = fbm(n, n, 10, 4, 34) - 0.5
        lat = dy + turb * 0.06
        band = 0.5 + 0.5 * np.sin(lat * 15)
        col = [0.86 - 0.2 * band, 0.76 - 0.24 * band, 0.62 - 0.26 * band]
        spot = np.exp(-(((dx - 0.3) / 0.2) ** 2 + ((dy - 0.35) / 0.1) ** 2))
        col = [col[0] * (1 - spot) + spot * 0.78, col[1] * (1 - spot) + spot * 0.38, col[2] * (1 - spot) + spot * 0.25]
    elif kind == 'uranus':
        nz = fbm(n, n, 3, 2, 35)
        col = [0.62 + 0.04 * nz, 0.86 + 0.04 * nz, 0.9 + 0.03 * nz]
    else:  # neptune
        nz = fbm(n, n, 4, 3, 36)
        band = 0.5 + 0.5 * np.sin(dy * 9 + nz * 2)
        col = [0.25 + 0.08 * band, 0.42 + 0.1 * band, 0.92 + 0.05 * band]
    rgb = [np.clip(c * limb, 0, 1) for c in col]
    return np.dstack(rgb + [inside])


def planets():
    """Atlas of 4 x 2 cells: mercury, venus, mars, jupiter / uranus, neptune, (two spare)."""
    n = 128
    atlas = np.zeros((n * 2, n * 4, 4))
    for i, k in enumerate(['mercury', 'venus', 'mars', 'jupiter', 'uranus', 'neptune']):
        cx, cy = i % 4, i // 4
        atlas[cy * n:(cy + 1) * n, cx * n:(cx + 1) * n] = planet_cell(k, n)
    save(atlas, 'planets.png')


def saturn():
    """Saturn with its rings (the part of the rings behind the globe is hidden by it)."""
    w, h = 256, 128
    x, y = grid(w, h)
    px, py = (x - 0.5) * w / h, y - 0.5
    pr = 0.22
    r = np.hypot(px, py) / pr
    globe = smooth(1.0, 0.95, r)
    limb = np.sqrt(np.clip(1 - r * r, 0, 1)) ** 0.45
    band = 0.5 + 0.5 * np.sin(py / pr * 11 + (fbm(w, h, 8, 3, 41) - 0.5) * 2)
    gcol = [(0.93 - 0.1 * band) * limb, (0.84 - 0.12 * band) * limb, (0.6 - 0.12 * band) * limb]
    tilt = 0.32
    rx, ry = px, py / tilt
    rr = np.hypot(rx, ry) / pr
    ring = smooth(1.25, 1.32, rr) * (1 - smooth(2.25, 2.3, rr))
    ring *= 1 - 0.85 * (smooth(1.86, 1.89, rr) * (1 - smooth(1.94, 1.97, rr)))
    ring *= 0.65 + 0.35 * (0.5 + 0.5 * np.sin(rr * 40))
    behind = (py < 0) & (globe > 0.01)
    ring_vis = ring.copy()
    ring_vis[behind] = 0
    shadow = (py > 0) & (r < 1.0) & (np.abs(py) < pr * 0.25)
    gshade = np.where(shadow, 0.6, 1.0)
    a = np.clip(globe + ring_vis * 0.9, 0, 1)
    over = ring_vis * 0.9 / np.maximum(a, 1e-3)
    rcol = [0.86, 0.78, 0.6]
    rgb = [np.clip(gcol[i] * gshade * (1 - over) + rcol[i] * over, 0, 1) for i in range(3)]
    save(np.dstack(rgb + [a]), 'saturn.png')


# ------------------------------------------------------------------ spacecraft
def iss():
    """The space station: a long truss with four pairs of big solar wings, the modules and radiators in the middle."""
    W, H = 1024, 512
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cy = H // 2
    d.rectangle([60, cy - 9, W - 60, cy + 9], fill=(205, 205, 200, 255))
    for xx in range(60, W - 60, 22):
        d.line([xx, cy - 9, xx + 22, cy + 9], fill=(150, 150, 145, 255), width=2)
        d.line([xx, cy + 9, xx + 22, cy - 9], fill=(150, 150, 145, 255), width=2)
    for wx in (110, 250, W - 250, W - 110):
        for sgn in (-1, 1):
            y0, y1 = (cy - 230, cy - 18) if sgn < 0 else (cy + 18, cy + 230)
            for k in (-1, 1):
                x0 = wx + k * 14 - 26
                d.rectangle([x0, y0, x0 + 52, y1], fill=(40, 52, 98, 255), outline=(170, 140, 70, 255), width=3)
                for gy in range(y0, y1, 13):
                    d.line([x0, gy, x0 + 52, gy], fill=(120, 105, 60, 255), width=1)
                d.line([x0 + 26, y0, x0 + 26, y1], fill=(150, 125, 70, 255), width=2)
            d.rectangle([wx - 4, min(y0, y1), wx + 4, max(y0, y1)], fill=(180, 180, 175, 255))
    mx = W // 2
    d.rectangle([mx - 26, cy - 150, mx + 26, cy + 150], fill=(232, 232, 226, 255))
    for yy in range(cy - 150, cy + 150, 30):
        d.line([mx - 26, yy, mx + 26, yy], fill=(170, 170, 165, 255), width=2)
    d.rectangle([mx - 120, cy - 20, mx + 120, cy + 20], fill=(225, 225, 220, 255))
    d.ellipse([mx - 50, cy + 120, mx + 50, cy + 175], fill=(220, 220, 214, 255))
    for k in (-1, 1):
        d.polygon([(mx + k * 150, cy - 20), (mx + k * 210, cy - 110), (mx + k * 245, cy - 95), (mx + k * 185, cy - 20)], fill=(236, 236, 236, 255))
        d.polygon([(mx + k * 150, cy + 20), (mx + k * 210, cy + 110), (mx + k * 245, cy + 95), (mx + k * 185, cy + 20)], fill=(236, 236, 236, 255))
    im = im.filter(ImageFilter.GaussianBlur(0.7)).resize((512, 256), Image.LANCZOS)
    a = np.asarray(im, dtype=float) / 255
    save(a, 'iss.png')


def satellite():
    """A small satellite: a box body with two long solar panels (and a bright spot where the sun glints off it)."""
    W, H = 512, 256
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cy = H // 2
    d.rectangle([215, cy - 40, 297, cy + 40], fill=(210, 190, 140, 255), outline=(240, 220, 160, 255), width=3)
    d.rectangle([235, cy - 55, 277, cy - 40], fill=(200, 200, 200, 255))
    for k in (-1, 1):
        x0, x1 = (20, 200) if k < 0 else (312, 492)
        d.rectangle([x0, cy - 34, x1, cy + 34], fill=(36, 48, 96, 255), outline=(180, 150, 80, 255), width=3)
        for gx in range(x0, x1, 22):
            d.line([gx, cy - 34, gx, cy + 34], fill=(140, 120, 70, 255), width=2)
        d.line([x0, cy, x1, cy], fill=(140, 120, 70, 255), width=2)
        d.line([200 if k < 0 else 297, cy, 215 if k < 0 else 312, cy], fill=(200, 200, 200, 255), width=5)
    im = im.filter(ImageFilter.GaussianBlur(0.6)).resize((256, 128), Image.LANCZOS)
    save(np.asarray(im, dtype=float) / 255, 'satellite.png')


# ------------------------------------------------------------------ aurora
def aurora():
    """Rays: tall vertical striations (wrapping round left to right), brightest just above the sharp lower edge."""
    w, h = 512, 256
    x, y = grid(w, h)
    v = 1 - y  # 0 at the bottom, 1 at the top
    rays = np.zeros_like(x)
    r = np.random.default_rng(55)
    for f in (7, 13, 23, 41, 67, 97):
        ph = r.random() * 6.283
        rays += (0.5 + 0.5 * np.sin(x * 6.283 * f + ph)) ** 3 / (1 + f * 0.04)
    rays = rays / rays.max()
    height = 0.45 + 0.5 * (0.5 + 0.5 * np.sin(x * 6.283 * 5 + 1.3)) * (0.6 + 0.4 * rays)
    prof = smooth(0.0, 0.06, v) * np.exp(-np.clip(v, 0, None) / np.maximum(height, 0.05) * 2.2)
    a = np.clip(prof * (0.35 + 0.65 * rays) * 1.4, 0, 1)
    save(np.dstack([np.ones_like(a)] * 3 + [a]), 'aurora.png')


# ------------------------------------------------------------------ Milky Way
def milky_way():
    """A band of faint stars and glowing star clouds with dark dust lanes along the middle (wraps left to right)."""
    w, h = 1024, 256
    x, y = grid(w, h)
    across = (y - 0.5) * 2
    lane = fbm(w, h, 12, 5, 61, wrap_x=True)
    width = 0.42 + 0.25 * (fbm(w, h, 4, 2, 62, wrap_x=True) - 0.5)
    band = np.exp(-(across / width) ** 2 * 1.6)
    clouds = fbm(w, h, 20, 6, 63, wrap_x=True)
    # Dust lanes are long dark rifts along the band, so the noise is stretched along it.
    lanes = np.asarray(Image.fromarray((fbm(256, 256, 6, 6, 64, wrap_x=True) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), dtype=float) / 255
    dust = smooth(0.5, 0.64, lanes) * np.exp(-((across + 0.08) / 0.22) ** 2)
    core = 0.6 + 0.4 * (0.5 + 0.5 * np.cos(x * 6.283))
    a = band * (0.35 + 0.65 * clouds ** 1.5) * (1 - 0.75 * dust) * core * (0.75 + 0.25 * lane)
    stars = (np.random.default_rng(65).random((h, w)) > 0.9965) * np.random.default_rng(66).random((h, w))
    stars = np.asarray(Image.fromarray((stars * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.5)), dtype=float) / 255 * 3
    a = np.clip(a * 0.9 + stars * (0.4 + band), 0, 1)
    warm = clouds * band
    r = np.clip(0.8 + 0.2 * warm, 0, 1)
    g = np.clip(0.78 + 0.12 * warm, 0, 1)
    b = np.clip(0.95 - 0.15 * warm, 0, 1)
    save(np.dstack([r, g, b, a]), 'milky_way.png')


# ------------------------------------------------------------------ nebulae
def shell(name, colors, seed):
    """An expanding shell of glowing gas with filaments (supernova remnant or nova shell)."""
    n = 256
    x, y = grid(n, n)
    dx, dy = (x - 0.5) * 2, (y - 0.5) * 2
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    fil = fbm(n, n, 10, 6, seed)
    fil2 = fbm(n, n, 22, 4, seed + 5)
    ring = np.exp(-((r - 0.62 - (fil - 0.5) * 0.35) / 0.12) ** 2)
    inner = np.exp(-(r / 0.45) ** 2) * 0.35
    streaks = (0.5 + 0.5 * np.sin(ang * 14 + fil * 8)) ** 4 * smooth(0.95, 0.3, r) * 0.5
    a = np.clip((ring * (0.4 + 0.8 * fil2) + inner * fil + streaks * fil2) * (1 - smooth(0.88, 1.0, r)), 0, 1)
    mix = np.clip(fil2 * 1.2 - 0.1 + (r - 0.5), 0, 1)
    c0, c1 = np.array(colors[0]), np.array(colors[1])
    rgb = [c0[i] * (1 - mix) + c1[i] * mix for i in range(3)]
    save(np.dstack(rgb + [a]), name)


def coronal_loops():
    """Coronal loops and prominences at the sun's edge, and streamers reaching out (for a coronal mass ejection)."""
    n = 512
    x, y = grid(n, n)
    dx, dy = (x - 0.5) * 2, (y - 0.5) * 2
    r = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)
    R = 0.36
    a = np.zeros_like(r)
    rr = np.random.default_rng(71)
    for _ in range(26):
        a0 = rr.random() * 6.283
        span = 0.08 + rr.random() * 0.25
        height = 0.04 + rr.random() * 0.22
        for s in np.linspace(0, 1, 90):
            aa = a0 + (s - 0.5) * span
            hh = R + height * math.sin(s * math.pi)
            px, py = math.cos(aa) * hh, math.sin(aa) * hh
            a += np.exp(-((dx - px) ** 2 + (dy - py) ** 2) / (0.008 ** 2)) * 0.06
    streak = fbm(n, n, 14, 4, 72)
    rays = (0.5 + 0.5 * np.sin(ang * 9 + streak * 6)) ** 3 * np.exp(-(r - R) / 0.25) * (r > R) * 0.8
    halo = np.exp(-(r - R) / 0.08) * (r > R) * 0.6
    a = np.clip(a + rays + halo, 0, 1) * (1 - smooth(0.95, 1.0, r)) * smooth(R * 0.97, R * 1.02, r)
    rgb = [np.ones_like(a), np.clip(0.62 + 0.3 * a, 0, 1), np.clip(0.18 + 0.4 * a, 0, 1)]
    save(np.dstack(rgb + [a]), 'coronal_loops.png')


# ------------------------------------------------------------------ transient luminous events
def red_sprite():
    """A red sprite: a diffuse red crown on top and tendrils hanging below it that turn blue at their tips."""
    w, h = 128, 256
    x, y = grid(w, h)
    dx = (x - 0.5) * 2
    v = 1 - y
    crown = np.exp(-((v - 0.78) / 0.12) ** 2) * np.exp(-(dx / 0.7) ** 2)
    tend = np.zeros_like(x)
    rr = np.random.default_rng(81)
    for _ in range(14):
        cx = (rr.random() - 0.5) * 1.1
        bottom = 0.08 + rr.random() * 0.3
        wob = (fbm(w, h, 2, 3, int(rr.integers(1000))) - 0.5) * 0.12
        tend += np.exp(-((dx - cx - wob * (0.75 - v)) / 0.035) ** 2) * smooth(bottom, bottom + 0.2, v) * (1 - smooth(0.7, 0.8, v))
    a = np.clip(crown * 0.9 + tend * 0.7, 0, 1)
    blue = 1 - smooth(0.1, 0.45, v)
    rgb = [np.clip(1.0 - 0.7 * blue, 0, 1), np.clip(0.18 + 0.2 * blue, 0, 1), np.clip(0.3 + 0.7 * blue, 0, 1)]
    save(np.dstack(rgb + [a]), 'red_sprite.png')


def blue_jet():
    """A blue jet: a narrow cone of blue light shooting up from the top of the storm cloud."""
    w, h = 64, 256
    x, y = grid(w, h)
    dx = (x - 0.5) * 2
    v = 1 - y
    width = 0.08 + 0.55 * v
    a = np.exp(-(dx / width) ** 2 * 2) * (1 - v) ** 1.2 * smooth(0, 0.05, v) * (0.75 + 0.25 * fbm(w, h, 4, 3, 91))
    rgb = [np.full_like(a, 0.35), np.full_like(a, 0.55), np.ones_like(a)]
    save(np.dstack(rgb + [np.clip(a * 1.3, 0, 1)]), 'blue_jet.png')


if __name__ == '__main__':
    glow()
    star()
    meteor()
    fireball()
    comet()
    planets()
    saturn()
    iss()
    satellite()
    aurora()
    milky_way()
    shell('supernova.png', [(0.75, 0.35, 1.0), (1.0, 0.45, 0.75)], 101)
    shell('nova_shell.png', [(1.0, 0.35, 0.15), (1.0, 0.65, 0.3)], 111)
    coronal_loops()
    red_sprite()
    blue_jet()
