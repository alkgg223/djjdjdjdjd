"""Realistic 64 x 64 textures for the 47 planet blocks (seamless, lit from a relief map: cracks, pebbles, grains,
ripples, crystal facets, glowing lava seams), and the 8 x 8 atlas the ray-traced planets are built from."""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'realweather', 'textures')
BLOCK = os.path.join(RES, 'block')
SPACE = os.path.join(RES, 'environment', 'space3d')
N = 64


# ------------------------------------------------------------------------------------------ seamless noise

def vnoise(period, seed):
    """Periodic value noise on an N x N tile with `period` cells across (smooth, wraps at the edges)."""
    r = np.random.default_rng(seed)
    g = r.random((period, period))
    t = np.arange(N) / N * period
    i0 = np.floor(t).astype(int)
    f = t - i0
    f = f * f * f * (f * (f * 6 - 15) + 10)
    i1 = (i0 + 1) % period
    gy0, gy1 = g[i0 % period], g[i1]
    a = gy0[:, i0 % period] * (1 - f)[None, :] + gy0[:, i1] * f[None, :]
    b = gy1[:, i0 % period] * (1 - f)[None, :] + gy1[:, i1] * f[None, :]
    return a * (1 - f)[:, None] + b * f[:, None]


def fbm(period, octaves, seed, gain=0.5):
    s, a, tot = 0, 1.0, 0
    for o in range(octaves):
        p = min(period * (2 ** o), N)
        s = s + a * vnoise(p, seed + o * 131)
        tot += a
        a *= gain
    return s / tot


def voronoi(count, seed, warp=4.0):
    """Distances to the nearest and second nearest of `count` points (wrapping), and the nearest point's index; the
    plane is bent by noise first, so the edges are jagged like real cracks, not straight lines."""
    r = np.random.default_rng(seed)
    pts = r.random((count, 2)) * N
    y, x = np.mgrid[0:N, 0:N].astype(float)
    if warp:
        x = (x + (fbm(4, 4, seed + 777) - 0.5) * 2 * warp + (fbm(16, 2, seed + 778) - 0.5) * warp * 0.6) % N
        y = (y + (fbm(4, 4, seed + 779) - 0.5) * 2 * warp + (fbm(16, 2, seed + 780) - 0.5) * warp * 0.6) % N
    d1 = np.full((N, N), 1e9)
    d2 = np.full((N, N), 1e9)
    idx = np.zeros((N, N), int)
    for k, (px, py) in enumerate(pts):
        dx = np.abs(x - px)
        dy = np.abs(y - py)
        dx = np.minimum(dx, N - dx)
        dy = np.minimum(dy, N - dy)
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < d1
        d2 = np.where(closer, d1, np.minimum(d2, d))
        idx = np.where(closer, k, idx)
        d1 = np.where(closer, d, d1)
    return d1, d2, idx


def blobs(count, rmin, rmax, seed):
    """Height of scattered round pebbles (wrapping)."""
    r = np.random.default_rng(seed)
    y, x = np.mgrid[0:N, 0:N].astype(float)
    h = np.zeros((N, N))
    for _ in range(count):
        px, py, rad = r.random() * N, r.random() * N, rmin + (rmax - rmin) * r.random() ** 2
        dx = np.abs(x - px)
        dy = np.abs(y - py)
        dx = np.minimum(dx, N - dx)
        dy = np.minimum(dy, N - dy)
        d = np.sqrt(dx * dx + dy * dy) / rad
        h = np.maximum(h, np.sqrt(np.clip(1 - d * d, 0, 1)) * (0.6 + 0.4 * r.random()))
    return h


def craters(count, rmin, rmax, seed):
    r = np.random.default_rng(seed)
    y, x = np.mgrid[0:N, 0:N].astype(float)
    h = np.zeros((N, N))
    for _ in range(count):
        px, py, rad = r.random() * N, r.random() * N, rmin + (rmax - rmin) * r.random() ** 2
        dx = np.abs(x - px)
        dy = np.abs(y - py)
        dx = np.minimum(dx, N - dx)
        dy = np.minimum(dy, N - dy)
        d = np.sqrt(dx * dx + dy * dy) / rad
        h += np.where(d < 1, -(1 - d * d) * 0.8, np.where(d < 1.4, (1 - (d - 1) / 0.4) * 0.35, 0))
    return h


def smooth(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def ramp(v, stops):
    v = np.clip(v, 0, 1)
    out = np.zeros(v.shape + (3,))
    xs = [s[0] for s in stops]
    for c in range(3):
        out[..., c] = np.interp(v, xs, [s[1][c] for s in stops])
    return out


def blur(a, k=2):
    out = a.copy()
    for _ in range(k):
        out = (out + np.roll(out, 1, 0) + np.roll(out, -1, 0) + np.roll(out, 1, 1) + np.roll(out, -1, 1)) / 5
    return out


def shade(albedo, height, strength=1.0, gloss=0.0, ao=0.6):
    """Lights the albedo by the relief: light from the upper left, cavities darker, an optional shine."""
    gx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) * 0.5 * strength * 6
    gy = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) * 0.5 * strength * 6
    n = np.stack([-gx, -gy, np.ones_like(gx)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    light = np.array([-0.5, -0.6, 0.62])
    light /= np.linalg.norm(light)
    diff = np.clip(n @ light, 0, 1)
    cav = np.clip((height - blur(height, 3)) * 4 * strength, -1, 1)
    lit = 0.42 + 0.68 * diff
    lit *= 1 + cav * ao * 0.5
    out = albedo * lit[..., None]
    if gloss > 0:
        h = np.array([-0.25, -0.3, 1.0])
        h /= np.linalg.norm(h)
        spec = np.clip(n @ h, 0, 1) ** 40 * gloss
        out = out + spec[..., None]
    return np.clip(out, 0, 1)


def speckle(img, color, density, seed, size=1):
    r = np.random.default_rng(seed)
    out = img.copy()
    for _ in range(int(N * N * density / (size * size))):
        x, y = r.integers(0, N), r.integers(0, N)
        c = np.array(color) * (0.85 + 0.3 * r.random())
        for dx in range(size):
            for dy in range(size):
                out[(y + dy) % N, (x + dx) % N] = c
    return out


# ------------------------------------------------------------------------------------------ materials

def dusty(seed, stops, pebble=(0, 1, 2), crater=0, grain=0.35, strength=0.8):
    n = fbm(4, 5, seed)
    g = fbm(32, 2, seed + 1)
    h = n * 0.6 + g * grain
    if pebble[0]:
        h += blobs(pebble[0], pebble[1], pebble[2], seed + 2) * 0.5
    if crater:
        h += craters(crater, 3, 12, seed + 3) * 0.5
    alb = ramp(n * 0.7 + g * 0.3, stops)
    return shade(alb, h, strength)


def rocky(seed, stops, cells=14, crack=0.6, grain=0.25, strength=1.1, flecks=None, layered=0.0):
    d1, d2, idx = voronoi(cells, seed, warp=7.0)
    # Thin, broken cracks (not every edge between plates is a crack), rough fractured faces.
    edge = smooth(0.0, 1.3, d2 - d1)
    broken = smooth(0.35, 0.6, fbm(6, 3, seed + 6))
    edge = 1 - (1 - edge) * broken
    n = fbm(4, 5, seed + 4)
    g = fbm(32, 2, seed + 5)
    fr = fbm(12, 3, seed + 7)
    r = np.random.default_rng(seed)
    plate = r.random(cells)[idx]
    h = n * 0.55 + plate * 0.12 + g * grain + fr * 0.3 - (1 - edge) * crack
    v = n * 0.6 + plate * 0.12 + g * 0.13 + fr * 0.15
    if layered:
        y = np.mgrid[0:N, 0:N][0] / N
        bands = 0.5 + 0.5 * np.sin((y * 6 + n * 0.8) * np.pi * 2)
        v = v * (1 - layered) + bands * layered
        h += bands * 0.2 * layered
    alb = ramp(v, stops)
    alb = alb * (0.72 + 0.28 * edge[..., None])
    out = shade(alb, h, strength)
    if flecks:
        for i, (c, dens) in enumerate(flecks):
            out = speckle(out, c, dens, seed + 30 + i)
    return out


def icy(seed, stops, cracks=10, bubbles=0.008, strength=0.6, gloss=0.35):
    d1, d2, _ = voronoi(cracks, seed, warp=6.0)
    edge = d2 - d1
    crack = (1 - smooth(0.0, 0.9, edge)) * smooth(0.3, 0.55, fbm(5, 3, seed + 9))
    n = fbm(3, 5, seed + 6)
    deep = fbm(6, 3, seed + 7)
    alb = ramp(n * 0.6 + deep * 0.4, stops)
    alb = alb * (1 - crack[..., None] * 0.25) + crack[..., None] * 0.22
    h = n * 0.4 - crack * 0.4
    out = shade(alb, h, strength, gloss)
    if bubbles:
        out = speckle(out, (0.95, 0.98, 1.0), bubbles, seed + 8)
    return out


def snowy(seed, stops, sparkle=0.012):
    n = fbm(4, 5, seed)
    g = fbm(32, 2, seed + 1)
    alb = ramp(n * 0.7 + g * 0.3, stops)
    out = shade(alb, n * 0.6 + g * 0.2, 0.5)
    return speckle(out, (1, 1, 1), sparkle, seed + 2)


def lava(seed, crust, glow, cells=11, seam=1.6, strength=1.0):
    d1, d2, idx = voronoi(cells, seed)
    edge = d2 - d1
    hot = 1 - smooth(0.0, seam, edge)
    n = fbm(4, 5, seed + 3)
    r = np.random.default_rng(seed)
    plate = r.random(cells)[idx]
    alb = ramp(n * 0.6 + plate * 0.4, crust)
    h = n * 0.5 + plate * 0.3 - hot * 0.6
    out = shade(alb, h, strength)
    heat = np.clip(hot * (0.7 + 0.5 * fbm(8, 3, seed + 4)), 0, 1)
    gl = ramp(heat, glow)
    return np.clip(out * (1 - heat[..., None]) + gl * heat[..., None], 0, 1)


def clouds(seed, stops, bands=4.0, warp=0.25, storms=0):
    y, x = np.mgrid[0:N, 0:N] / N
    n = fbm(4, 5, seed)
    w = fbm(2, 3, seed + 1)
    v = 0.5 + 0.5 * np.sin((y * bands + w * warp * 4 + n * 0.3) * np.pi * 2)
    v = v * 0.6 + n * 0.4
    alb = ramp(v, stops)
    if storms:
        d1, _, _ = voronoi(storms, seed + 2)
        eye = smooth(9, 2, d1)
        alb = alb * (1 - eye[..., None] * 0.4) + np.array(stops[-1][1]) * eye[..., None] * 0.4
    return shade(alb, n * 0.6 + v * 0.3, 0.45)


def grass(seed, base, tip, dirt):
    g = fbm(32, 2, seed)
    n = fbm(4, 4, seed + 1)
    blades = np.random.default_rng(seed).random((N, N)) ** 3
    v = g * 0.5 + n * 0.3 + blades * 0.4
    alb = ramp(v, [(0, dirt), (0.35, base), (1, tip)])
    return shade(alb, g * 0.5 + blades * 0.6, 0.9)


def crystal(seed, stops, cells=9, glow=0.0):
    d1, d2, idx = voronoi(cells, seed)
    r = np.random.default_rng(seed)
    nx, ny = r.normal(size=cells)[idx] * 0.6, r.normal(size=cells)[idx] * 0.6
    n = np.stack([nx, ny, np.ones_like(nx)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    light = np.array([-0.5, -0.6, 0.62])
    light /= np.linalg.norm(light)
    lit = 0.45 + 0.7 * np.clip(n @ light, 0, 1)
    v = r.random(cells)[idx]
    alb = ramp(v, stops)
    edge = 1 - smooth(0, 1.2, d2 - d1)
    out = alb * lit[..., None] + edge[..., None] * 0.35
    spark = speckle(np.zeros((N, N, 3)), (1, 1, 1), 0.005, seed + 3)
    out = out + spark * 0.8 + glow * alb * 0.3
    return np.clip(out, 0, 1)


def metal(seed):
    y, x = np.mgrid[0:N, 0:N] / N
    n = fbm(4, 4, seed)
    lines = np.abs(np.sin((x * 9 + y * 5 + n * 0.4) * np.pi)) * np.abs(np.sin((x * 4 - y * 8) * np.pi))
    v = n * 0.6 + lines * 0.4
    alb = ramp(v, [(0, (0.32, 0.33, 0.35)), (0.6, (0.55, 0.56, 0.58)), (1, (0.72, 0.72, 0.74))])
    return shade(alb, n * 0.5 + lines * 0.3, 0.8, gloss=0.5)


def salt(seed):
    d1, d2, _ = voronoi(16, seed)
    ridge = 1 - smooth(0, 1.8, d2 - d1)
    n = fbm(8, 3, seed + 1)
    alb = ramp(n * 0.5 + ridge * 0.5, [(0, (0.82, 0.8, 0.76)), (0.6, (0.93, 0.92, 0.9)), (1, (1, 1, 0.98))])
    return shade(alb, ridge * 0.6 + n * 0.2, 0.9)


def ripples(seed, stops, freq=7, grain=0.3):
    y, x = np.mgrid[0:N, 0:N] / N
    n = fbm(4, 4, seed)
    w = fbm(2, 2, seed + 1)
    rip = 0.5 + 0.5 * np.sin((y * freq + x * 2 + w * 1.5) * np.pi * 2)
    g = fbm(32, 2, seed + 2)
    alb = ramp(n * 0.5 + rip * 0.2 + g * grain, stops)
    return shade(alb, rip * 0.5 + g * 0.3, 0.8)


def water(seed):
    y, x = np.mgrid[0:N, 0:N] / N
    n = fbm(4, 4, seed)
    w = 0.5 + 0.5 * np.sin((x * 3 + y * 2 + n * 1.2) * np.pi * 2)
    alb = ramp(w * 0.6 + n * 0.4, [(0, (0.06, 0.2, 0.42)), (1, (0.2, 0.45, 0.72))])
    return shade(alb, w * 0.4, 0.5, gloss=0.4)


def hematite(seed):
    base = dusty(seed, [(0, (0.42, 0.18, 0.1)), (0.6, (0.62, 0.3, 0.16)), (1, (0.75, 0.42, 0.25))], pebble=(0, 1, 2))
    spheres = blobs(26, 1.2, 2.4, seed + 9)
    sp = shade(np.full((N, N, 3), (0.18, 0.15, 0.16)), spheres, 1.5, gloss=0.5)
    m = (spheres > 0.05)[..., None]
    return np.where(m, sp, base)


def impact_glass(seed):
    d1, d2, idx = voronoi(12, seed)
    r = np.random.default_rng(seed)
    v = r.random(12)[idx]
    n = fbm(6, 4, seed + 1)
    alb = ramp(v * 0.6 + n * 0.4, [(0, (0.15, 0.3, 0.18)), (0.6, (0.35, 0.55, 0.32)), (1, (0.6, 0.75, 0.5))])
    edge = 1 - smooth(0, 1.4, d2 - d1)
    return np.clip(shade(alb, n * 0.3 - edge * 0.4, 0.8, gloss=0.6) + edge[..., None] * 0.1, 0, 1)


C = lambda *v: tuple(x / 255 for x in v)

TILES = [
    ('planet_regolith', lambda: dusty(1, [(0, C(88, 86, 84)), (0.5, C(132, 129, 126)), (1, C(176, 173, 168))], pebble=(26, 1, 3), crater=5)),
    ('planet_basalt', lambda: rocky(2, [(0, C(30, 30, 33)), (0.5, C(52, 52, 56)), (1, C(78, 77, 80))], cells=10, crack=0.8,
                                    flecks=[(C(95, 92, 88), 0.004)])),
    ('planet_sand', lambda: ripples(3, [(0, C(176, 150, 106)), (0.5, C(210, 186, 140)), (1, C(232, 214, 176))])),
    ('planet_ice', lambda: icy(4, [(0, C(110, 160, 214)), (0.5, C(160, 202, 238)), (1, C(214, 236, 252))])),
    ('planet_frost', lambda: snowy(5, [(0, C(196, 210, 226)), (0.6, C(230, 238, 246)), (1, C(250, 252, 255))])),
    ('planet_magma', lambda: lava(6, [(0, C(24, 14, 12)), (1, C(66, 34, 24))], [(0, C(150, 30, 6)), (0.5, C(240, 110, 20)), (1, C(255, 224, 120))], seam=2.2)),
    ('planet_cloud', lambda: clouds(7, [(0, C(196, 186, 168)), (0.5, C(226, 218, 202)), (1, C(248, 244, 234))], bands=3)),
    (None, lambda: water(8)),
    ('planet_moss', lambda: grass(9, C(52, 104, 42), C(120, 178, 76), C(58, 46, 30))),
    ('planet_crystal', lambda: crystal(10, [(0, C(120, 150, 220)), (0.5, C(170, 196, 246)), (1, C(222, 236, 255))], cells=22, glow=0.4)),
    ('planet_sulfur', lambda: dusty(11, [(0, C(170, 140, 30)), (0.5, C(222, 196, 62)), (1, C(246, 228, 120))], pebble=(18, 1, 2.5))),
    ('planet_ironstone', lambda: rocky(12, [(0, C(104, 44, 26)), (0.5, C(150, 70, 40)), (1, C(188, 104, 66))], cells=12, crack=0.6)),
    ('planet_storm_cloud', lambda: clouds(13, [(0, C(140, 104, 72)), (0.5, C(196, 160, 116)), (1, C(236, 214, 176))], bands=5, storms=2)),
    ('planet_obsidian', lambda: crystal(14, [(0, C(12, 8, 18)), (0.6, C(34, 20, 46)), (1, C(70, 46, 96))], cells=16)),
    ('planet_hot_rock', lambda: lava(15, [(0, C(56, 24, 14)), (1, C(110, 50, 26))], [(0, C(160, 50, 10)), (1, C(255, 150, 40))], cells=16, seam=1.0)),
    ('planet_ash', lambda: dusty(16, [(0, C(62, 60, 58)), (0.5, C(96, 93, 90)), (1, C(128, 124, 120))], pebble=(12, 1, 2), grain=0.5)),
    ('mars_dust', lambda: ripples(17, [(0, C(150, 66, 34)), (0.5, C(192, 98, 56)), (1, C(220, 136, 90))], freq=5)),
    ('mars_rock', lambda: rocky(18, [(0, C(84, 38, 24)), (0.5, C(128, 60, 36)), (1, C(170, 92, 60))], cells=9, crack=0.7,
                                flecks=[(C(60, 30, 22), 0.02)])),
    ('lunar_highland', lambda: dusty(19, [(0, C(140, 138, 134)), (0.5, C(178, 176, 172)), (1, C(214, 212, 208))], pebble=(20, 1, 2.5), crater=7)),
    ('lunar_mare', lambda: dusty(20, [(0, C(52, 52, 54)), (0.5, C(78, 78, 80)), (1, C(106, 105, 104))], pebble=(14, 1, 2), crater=6)),
    ('mercury_rock', lambda: rocky(21, [(0, C(70, 64, 58)), (0.5, C(104, 96, 88)), (1, C(140, 130, 120))], cells=13, crack=0.5)),
    ('venus_rock', lambda: rocky(22, [(0, C(110, 66, 30)), (0.5, C(158, 102, 50)), (1, C(200, 146, 82))], cells=20, crack=0.55, layered=0.3)),
    ('titan_ice', lambda: icy(23, [(0, C(150, 112, 62)), (0.5, C(190, 150, 96)), (1, C(222, 190, 140))], gloss=0.15)),
    ('methane_snow', lambda: snowy(24, [(0, C(176, 214, 220)), (0.6, C(212, 238, 240)), (1, C(240, 252, 252))])),
    ('nitrogen_ice', lambda: icy(25, [(0, C(206, 190, 176)), (0.5, C(230, 220, 208)), (1, C(250, 244, 236))], cracks=7, gloss=0.25)),
    ('tholin_crust', lambda: dusty(26, [(0, C(92, 46, 26)), (0.5, C(132, 72, 40)), (1, C(170, 104, 62))], pebble=(16, 1, 2.5))),
    ('europa_ice', lambda: icy(27, [(0, C(196, 186, 170)), (0.5, C(226, 220, 208)), (1, C(246, 244, 238))], cracks=6, gloss=0.2)),
    ('jupiter_cloud', lambda: clouds(28, [(0, C(150, 96, 56)), (0.5, C(212, 160, 112)), (1, C(240, 216, 180))], bands=4)),
    ('saturn_cloud', lambda: clouds(29, [(0, C(190, 160, 104)), (0.5, C(222, 198, 146)), (1, C(242, 228, 190))], bands=5, warp=0.12)),
    ('uranus_cloud', lambda: clouds(30, [(0, C(118, 186, 198)), (0.5, C(156, 214, 222)), (1, C(196, 236, 240))], bands=2, warp=0.08)),
    ('neptune_cloud', lambda: clouds(31, [(0, C(34, 62, 150)), (0.5, C(62, 100, 196)), (1, C(140, 176, 236))], bands=3, storms=1)),
    ('lava_crust', lambda: lava(32, [(0, C(30, 22, 20)), (1, C(64, 50, 44))], [(0, C(130, 26, 6)), (1, C(250, 120, 30))], cells=8, seam=1.2)),
    ('diamond_rock', lambda: np.clip(rocky(33, [(0, C(20, 20, 24)), (0.6, C(40, 40, 46)), (1, C(64, 64, 72))], cells=9, crack=0.5)
                                     + speckle(np.zeros((N, N, 3)), (0.9, 0.97, 1), 0.008, 34) * 0.9, 0, 1)),
    ('purple_alien_grass', lambda: grass(35, C(88, 40, 120), C(170, 110, 210), C(50, 32, 40))),
    ('red_alien_grass', lambda: grass(36, C(120, 30, 28), C(206, 90, 60), C(56, 36, 28))),
    ('seabed_sand', lambda: ripples(37, [(0, C(96, 102, 104)), (0.5, C(132, 138, 136)), (1, C(170, 172, 164))], freq=4)),
    ('salt_flat', lambda: salt(38)),
    ('permafrost', lambda: np.clip(dusty(39, [(0, C(70, 58, 46)), (0.5, C(100, 86, 70)), (1, C(130, 116, 100))], pebble=(18, 1, 2))
                                   + speckle(np.zeros((N, N, 3)), (0.85, 0.9, 0.95), 0.015, 40) * 0.5, 0, 1)),
    ('iron_meteorite', lambda: metal(41)),
    ('alien_sandstone', lambda: rocky(42, [(0, C(170, 120, 80)), (0.5, C(206, 160, 112)), (1, C(232, 196, 150))], cells=6, crack=0.3, layered=0.6)),
    ('alien_granite', lambda: rocky(43, [(0, C(130, 104, 104)), (0.5, C(170, 140, 136)), (1, C(206, 182, 176))], cells=8, crack=0.35,
                                    flecks=[(C(40, 36, 38), 0.05), (C(236, 226, 220), 0.03), (C(190, 110, 100), 0.03)])),
    ('hematite_rock', lambda: hematite(44)),
    ('cryo_slush', lambda: np.clip(dusty(45, [(0, C(80, 100, 116)), (0.5, C(116, 140, 156)), (1, C(160, 184, 198))], pebble=(10, 1.5, 3), strength=0.5)
                                   + speckle(np.zeros((N, N, 3)), (0.8, 0.9, 1), 0.012, 46) * 0.5, 0, 1)),
    ('red_spot_cloud', lambda: clouds(47, [(0, C(140, 50, 30)), (0.5, C(196, 96, 62)), (1, C(236, 170, 130))], bands=2, storms=3)),
    ('alien_dirt', lambda: dusty(48, [(0, C(56, 40, 30)), (0.5, C(84, 62, 46)), (1, C(112, 88, 66))], pebble=(20, 1, 2.2), grain=0.45)),
    ('deep_planet_rock', lambda: rocky(49, [(0, C(36, 36, 40)), (0.5, C(58, 58, 62)), (1, C(84, 84, 88))], cells=15, crack=0.55)),
    ('impact_glass', lambda: impact_glass(50)),
    ('dry_ice', lambda: icy(51, [(0, C(206, 214, 222)), (0.5, C(228, 234, 240)), (1, C(248, 250, 252))], cracks=5, bubbles=0.04, gloss=0.2)),
]
EMISSIVE = {'planet_magma', 'planet_hot_rock', 'lava_crust', 'planet_crystal'}


def save(img, path):
    Image.fromarray(np.clip(img * 255, 0, 255).astype(np.uint8)).save(path)


def main():
    atlas = np.zeros((8 * N, 8 * N, 3))
    for i, (name, fn) in enumerate(TILES):
        img = np.clip(fn(), 0, 1)
        if name:
            save(img, os.path.join(BLOCK, name + '.png'))
        r, c = divmod(i, 8)
        atlas[r * N:(r + 1) * N, c * N:(c + 1) * N] = img
        print('tile', i, name)
    save(atlas, os.path.join(SPACE, 'planet_blocks.png'))
    print('atlas saved')


if __name__ == '__main__':
    main()
