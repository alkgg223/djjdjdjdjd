"""The 150 planetary surface blocks: a seamless 64 x 64 texture for each (in a folder per world), their block and
item models, loot tables, tool tags, names (English and Arabic) and the Java table that registers them and tells the
terrain where each belongs. Every texture is drawn from its own recipe (its geology): grains, pores, cracks, layers,
crystals, flows, frost, clouds."""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_planet_blocks import (N, C, vnoise, fbm, voronoi, blobs, craters, smooth, ramp, blur, shade, speckle, dusty, rocky, icy, snowy,
                               lava, clouds, grass, crystal, salt, ripples)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main')
ASSETS = os.path.join(ROOT, 'resources', 'assets', 'realweather')
DATA = os.path.join(ROOT, 'resources', 'data')
TEX = os.path.join(ASSETS, 'textures', 'block', 'planets')

GRID = np.mgrid[0:N, 0:N] / N
YY, XX = GRID[0], GRID[1]


# ------------------------------------------------------------------------------------------ more materials

def strata(seed, stops, layers=6, k=1, grain=0.3, crack=0.35, strength=1.0, sharp=0.6):
    """Sedimentary layers (whole numbers of bands across, so it tiles), with grains and a few cracks."""
    n = fbm(4, 5, seed)
    w = fbm(2, 3, seed + 1)
    t = YY * layers + XX * k + (w - 0.5) * 0.9
    band = 0.5 + 0.5 * np.sin(t * np.pi * 2)
    step = np.floor(t * 2) / (layers * 2)
    r = np.random.default_rng(seed)
    tone = r.random(layers * 4 + 8)[(np.floor(t * 2).astype(int)) % (layers * 4 + 8)]
    v = tone * sharp + band * (1 - sharp) * 0.5 + n * 0.25
    d1, d2, _ = voronoi(8, seed + 3, warp=6)
    cr = 1 - smooth(0, 1.0, d2 - d1)
    g = fbm(32, 2, seed + 4)
    h = band * 0.4 + n * 0.4 + g * grain - cr * crack
    alb = ramp(np.clip(v, 0, 1), stops) * (1 - cr[..., None] * crack * 0.5)
    return shade(alb, h, strength)


def soil(seed, stops, organics=0.0, pebbles=10, roots=0.0, grain=0.4, strength=0.8):
    n = fbm(4, 5, seed)
    g = fbm(32, 2, seed + 1)
    clump = fbm(8, 3, seed + 2)
    h = n * 0.4 + g * grain + clump * 0.3
    if pebbles:
        h = h + blobs(pebbles, 1, 2.2, seed + 3) * 0.5
    alb = ramp(n * 0.5 + clump * 0.3 + g * 0.2, stops)
    if organics:
        dark = smooth(0.55, 0.8, fbm(16, 2, seed + 5))
        alb = alb * (1 - dark[..., None] * organics)
    out = shade(alb, h, strength)
    if roots:
        rr = np.abs(np.sin((XX * 7 + fbm(4, 3, seed + 6) * 2) * np.pi * 2)) < 0.06
        out = np.where(rr[..., None], out * 0.7 + np.array(C(150, 120, 80)) * 0.3, out)
    return out


def mudcracks(seed, stops, cells=12, width=1.1, strength=1.0):
    d1, d2, idx = voronoi(cells, seed, warp=3)
    edge = smooth(0, width, d2 - d1)
    r = np.random.default_rng(seed)
    plate = r.random(cells)[idx]
    n = fbm(8, 3, seed + 1)
    curl = np.clip(d1 / 10, 0, 1)
    alb = ramp(plate * 0.3 + n * 0.5 + curl * 0.2, stops) * (0.45 + 0.55 * edge[..., None])
    return shade(alb, edge * 0.6 + n * 0.2 + curl * 0.2, strength)


def marble(seed, stops, vein, freq=2, strength=0.6):
    w = fbm(2, 5, seed)
    v = np.abs(np.sin((XX * freq + YY * freq + w * 2.5) * np.pi))
    veins = 1 - smooth(0.0, 0.12, v)
    n = fbm(4, 4, seed + 1)
    alb = ramp(n, stops)
    alb = alb * (1 - veins[..., None] * 0.7) + np.array(vein) * veins[..., None] * 0.7
    return shade(alb, n * 0.2, strength, gloss=0.15)


def polygons(seed, stops, cells=7, trough=0.5, gloss=0.2, frost=None):
    """Patterned ground: cells parted by troughs (Mars's polar ice, Pluto's nitrogen plains)."""
    d1, d2, idx = voronoi(cells, seed, warp=5)
    edge = smooth(0, 3.0, d2 - d1)
    n = fbm(4, 5, seed + 1)
    dome = np.clip(d2 - d1, 0, 8) / 8
    alb = ramp(n * 0.6 + dome * 0.4, stops) * (1 - (1 - edge[..., None]) * trough * 0.6)
    out = shade(alb, dome * 0.6 + n * 0.3 - (1 - edge) * trough, 0.8, gloss)
    if frost:
        out = speckle(out, frost, 0.01, seed + 3)
    return out


def grooves(seed, stops, freq=8, k=2, depth=0.6, strength=1.0):
    """Sets of parallel ridges and troughs (Ganymede's grooved terrain, Miranda's coronae)."""
    w = fbm(2, 3, seed)
    t = YY * freq + XX * k + (w - 0.5) * 1.2
    g = 0.5 + 0.5 * np.sin(t * np.pi * 2)
    g = g ** 1.6
    n = fbm(4, 5, seed + 1)
    fine = fbm(32, 2, seed + 2)
    alb = ramp(n * 0.5 + g * 0.35 + fine * 0.15, stops)
    return shade(alb, g * depth + n * 0.3 + fine * 0.2, strength)


def chaos(seed, stops, matrix, cells=9, strength=1.0):
    """Rafts of broken ice crust, shifted and tilted, set in a lumpy darker matrix (Europa's chaos terrain)."""
    d1, d2, idx = voronoi(cells, seed, warp=5)
    raft = smooth(1.5, 3.5, d2 - d1) * smooth(0.0, 0.3, (np.random.default_rng(seed).random(cells) > 0.2)[idx] * 1.0)
    n = fbm(4, 5, seed + 1)
    r = np.random.default_rng(seed + 2)
    ang = r.random(cells)[idx] * np.pi
    lines = 0.5 + 0.5 * np.sin((XX * np.cos(ang) * 14 + YY * np.sin(ang) * 14) * np.pi * 2)
    ice = ramp(n * 0.6 + lines * 0.25, stops)
    mat = ramp(fbm(8, 4, seed + 3), matrix)
    alb = ice * raft[..., None] + mat * (1 - raft[..., None])
    return shade(alb, raft * 0.6 + n * 0.3 + lines * 0.1 * raft, strength)


def ridged_ice(seed, stops, ridge, count=3, strength=0.8):
    """Smooth ice crossed by double ridges with a dark trough between (Europa's lineae)."""
    n = fbm(4, 5, seed)
    out_v = n.copy()
    h = n * 0.3
    tint = np.zeros((N, N))
    r = np.random.default_rng(seed)
    for i in range(count):
        a, b = int(r.integers(1, 3)), int(r.integers(-2, 3))
        ph = r.random()
        w = fbm(2, 3, seed + 10 + i)
        t = np.sin((XX * a + YY * b + ph + (w - 0.5) * 0.4) * np.pi * 2)
        d = np.abs(t)
        crest = np.exp(-((d - 0.05) / 0.035) ** 2)
        trough = np.exp(-(d / 0.02) ** 2)
        h = h + crest * 0.5 - trough * 0.4
        tint = np.maximum(tint, np.exp(-(d / 0.09) ** 2))
    alb = ramp(out_v, stops)
    alb = alb * (1 - tint[..., None] * 0.6) + np.array(ridge) * tint[..., None] * 0.6
    return shade(alb, h, strength, gloss=0.15)


def porous(seed, stops, pits=60, strength=1.1, dust=None):
    """A fluffy, porous mix full of pits and hollows (comets, Hyperion)."""
    n = fbm(4, 5, seed)
    h = n * 0.5 + craters(pits, 0.8, 3.0, seed + 1) * 0.6 + fbm(32, 2, seed + 2) * 0.3
    alb = ramp(n * 0.6 + fbm(16, 2, seed + 3) * 0.4, stops)
    alb = alb * (0.6 + 0.4 * smooth(-0.6, 0.2, h)[..., None])
    out = shade(alb, h, strength)
    if dust:
        out = speckle(out, dust, 0.02, seed + 4)
    return out


def breccia(seed, stops, clasts, cells=16, strength=1.1):
    """Angular broken fragments cemented in a fine matrix (impact breccia)."""
    d1, d2, idx = voronoi(cells, seed, warp=2.5)
    r = np.random.default_rng(seed)
    keep = (r.random(cells) > 0.35)[idx]
    inner = smooth(0.8, 1.6, d2 - d1) * keep
    tone = r.random(cells)[idx]
    cl = ramp(tone * 0.7 + fbm(16, 2, seed + 1) * 0.3, clasts)
    mt = ramp(fbm(32, 2, seed + 2) * 0.6 + fbm(4, 4, seed + 3) * 0.4, stops)
    alb = cl * inner[..., None] + mt * (1 - inner[..., None])
    return shade(alb, inner * 0.5 + fbm(32, 2, seed + 4) * 0.25, strength)


def ropy(seed, crust, glow=None, freq=6, strength=1.0):
    """Lava that flowed and set in ropes and wrinkles (pahoehoe), with hot seams if still glowing."""
    w = fbm(2, 4, seed)
    t = YY * freq + (w - 0.5) * 2.4
    rope = 0.5 + 0.5 * np.sin(t * np.pi * 2 + np.sin(XX * np.pi * 2 * 3 + w * 4) * 0.8)
    n = fbm(8, 3, seed + 1)
    alb = ramp(rope * 0.5 + n * 0.5, crust)
    out = shade(alb, rope * 0.7 + n * 0.3, strength, gloss=0.2)
    if glow:
        hot = np.clip(1 - smooth(0.0, 0.08, np.abs(rope - 0.06)), 0, 1) * smooth(0.45, 0.7, fbm(4, 3, seed + 2))
        out = np.clip(out * (1 - hot[..., None]) + ramp(hot, glow) * hot[..., None], 0, 1)
    return out


def glassy(seed, stops, strength=0.7):
    """Volcanic glass: smooth, with the curved shell-like breaks of conchoidal fracture and bright edges."""
    d1, d2, idx = voronoi(7, seed, warp=4)
    r = np.random.default_rng(seed)
    shell = np.sin(d1 * 0.9 + r.random(7)[idx] * 6)
    n = fbm(4, 4, seed + 1)
    alb = ramp(n * 0.6 + (shell * 0.5 + 0.5) * 0.4, stops)
    edge = 1 - smooth(0, 1.0, d2 - d1)
    out = shade(alb, shell * 0.3 + n * 0.2, strength, gloss=0.8)
    return np.clip(out + edge[..., None] * 0.18, 0, 1)


def widmanstatten(seed, base, contrast=0.35):
    """Etched nickel-iron: criss-crossing plates of kamacite and taenite."""
    n = fbm(4, 3, seed)
    v = np.zeros((N, N))
    for a, b in ((3, 1), (1, -3), (2, 2)):
        v += (np.abs(np.sin((XX * a + YY * b + n * 0.3) * np.pi * 2 * 2)) < 0.18) * 1.0
    v = np.clip(v, 0, 1)
    alb = ramp(n * 0.5 + v * contrast, base)
    return shade(alb, v * 0.2 + n * 0.3, 0.6, gloss=0.6)


def graphite(seed):
    n = fbm(4, 4, seed)
    flakes = 0.5 + 0.5 * np.sin((YY * 18 + fbm(2, 3, seed + 1) * 3) * np.pi * 2)
    alb = ramp(n * 0.5 + flakes * 0.5, [(0, C(18, 18, 20)), (0.6, C(42, 42, 46)), (1, C(78, 80, 86))])
    return shade(alb, flakes * 0.5 + n * 0.3, 0.9, gloss=0.55)


def nodules(seed, stops, nod, count=22):
    """Fine sediment strewn with dark rounded nodules (the deep ocean floor)."""
    base = soil(seed, stops, pebbles=0, grain=0.25, strength=0.6)
    b = blobs(count, 1.4, 3.2, seed + 9)
    sp = shade(np.full((N, N, 3), nod), b + fbm(32, 2, seed) * 0.3, 1.4)
    m = (b > 0.05)[..., None]
    return np.where(m, sp, base)


def frosted(img, seed, amount=0.4, color=(0.92, 0.95, 1.0), patch=0.5):
    """Frost settled in the hollows and patches of a surface."""
    f = smooth(patch, patch + 0.2, fbm(8, 3, seed)) * amount
    out = img * (1 - f[..., None]) + np.array(color) * f[..., None]
    return speckle(out, color, 0.01, seed + 1)


def stripes(seed, stops, stripe, count=2, strength=0.7):
    """Bright ice cut by a few long dark-blue fractures (the 'tiger stripes' of Enceladus)."""
    base = icy(seed, stops, cracks=6, strength=strength, gloss=0.25)
    w = fbm(2, 3, seed + 3)
    t = np.abs(np.sin((YY * count + XX * 1 + (w - 0.5) * 0.3) * np.pi))
    s = np.exp(-(t / 0.05) ** 2)
    rim = np.exp(-((t - 0.08) / 0.04) ** 2) * 0.4
    out = base * (1 - s[..., None] * 0.7) + np.array(stripe) * s[..., None] * 0.7
    return np.clip(out + rim[..., None] * 0.15, 0, 1)


def cloud_rgba(seed, stops, bands=3, density=0.75, swirl=0.4, storms=0):
    """A wisp of cloud deck, translucent at its thin parts (for atmospheres, not solid ground)."""
    n = fbm(4, 5, seed)
    w = fbm(2, 3, seed + 1)
    v = 0.5 + 0.5 * np.sin((YY * bands + w * swirl * 4 + n * 0.4) * np.pi * 2)
    v = v * 0.5 + n * 0.5
    if storms:
        d1, _, _ = voronoi(storms, seed + 2)
        v = np.clip(v + smooth(8, 2, d1) * 0.3, 0, 1)
    rgb = shade(ramp(v, stops), n * 0.5 + v * 0.3, 0.35)
    a = np.clip(0.35 + (n * 0.6 + v * 0.4) * density, 0, 0.95)
    return np.concatenate([rgb, a[..., None]], -1)


def supercritical(seed):
    w = fbm(2, 5, seed)
    t = 0.5 + 0.5 * np.sin((XX * 2 + YY * 1 + w * 3) * np.pi * 2)
    s = 0.5 + 0.5 * np.sin((XX * 1 - YY * 2 + w * 2.2) * np.pi * 2)
    rgb = ramp(t * 0.6 + s * 0.4, [(0, C(30, 60, 120)), (0.5, C(90, 150, 200)), (1, C(200, 230, 250))])
    rgb = shade(rgb, t * 0.3, 0.3, gloss=0.3)
    a = 0.55 + 0.3 * s
    return np.concatenate([rgb, a[..., None]], -1)


def hollows(seed, stops, bright, count=7):
    """Dark rock with bright, shallow pits (Mercury's hollows)."""
    base = dusty(seed, stops, pebble=(10, 1, 2), crater=4, strength=0.9)
    b = blobs(count, 2.5, 6, seed + 5)
    m = smooth(0.05, 0.4, b)
    return np.clip(base * (1 - m[..., None]) + np.array(bright) * m[..., None] * (0.85 + 0.15 * fbm(16, 2, seed)[..., None]), 0, 1)


def tessera(seed, stops):
    """Venus's tessera: the oldest ground, crumpled in criss-crossing ridges."""
    a = grooves(seed, stops, freq=7, k=3, depth=0.5)
    b = grooves(seed + 1, stops, freq=3, k=-7, depth=0.5)
    return np.clip(a * 0.55 + b * 0.55 - 0.05, 0, 1)


def ejecta(seed, stops, rays=(0.85, 0.85, 0.82)):
    """Crater debris: blocks and boulders strewn on fine dust, with light streaks of ejecta."""
    base = dusty(seed, stops, pebble=(30, 1, 3.2), crater=3, grain=0.4, strength=1.1)
    w = fbm(2, 3, seed + 3)
    ray = np.exp(-((np.sin((XX * 2 + YY * 1 + w) * np.pi * 2)) / 0.25) ** 2) * 0.25
    return np.clip(base + ray[..., None] * np.array(rays) * 0.5, 0, 1)


def sulfur_flow(seed, stops, dark=None):
    """Sulfur fields: smooth colour patches poured over each other, and fine frost."""
    n = fbm(3, 5, seed)
    w = fbm(2, 3, seed + 1)
    flow = 0.5 + 0.5 * np.sin((n * 3 + w * 2) * np.pi * 2)
    alb = ramp(n * 0.5 + flow * 0.5, stops)
    if dark:
        d = smooth(0.62, 0.7, fbm(4, 4, seed + 2))
        alb = alb * (1 - d[..., None]) + np.array(dark) * d[..., None]
    return shade(alb, flow * 0.3 + n * 0.3, 0.7, gloss=0.1)


# ------------------------------------------------------------------------------------------ the 150

# id, folder, English, Arabic, material, hardness, light, geological type, density (0 low .. 3 very high),
# surface temperature range (Celsius), texture.
M = {'dust': 0, 'rock': 1, 'ice': 2, 'snow': 3, 'cloud': 4, 'plant': 5, 'crystal': 6, 'metal': 7, 'glass': 8, 'mud': 9, 'gas': 10}

B = []


def b(id, folder, en, ar, mat, hard, light, geo, dens, tmin, tmax, fn):
    B.append(dict(id=id, folder=folder, en=en, ar=ar, mat=mat, hard=hard, light=light, geo=geo, dens=dens, tmin=tmin, tmax=tmax, fn=fn))


# Earth
b('earth_grassland_soil', 'earth', 'Grassland Soil', 'تربة المراعي', 'plant', 0.6, 0, 'soil', 1, -20, 45,
  lambda: grass(101, C(70, 120, 46), C(140, 186, 84), C(74, 56, 36)))
b('earth_fertile_soil', 'earth', 'Dark Fertile Soil', 'تربة خصبة داكنة', 'mud', 0.5, 0, 'soil', 1, -20, 45,
  lambda: soil(102, [(0, C(30, 22, 16)), (0.5, C(52, 38, 26)), (1, C(78, 60, 42))], organics=0.3, roots=1))
b('earth_dry_earth', 'earth', 'Dry Earth', 'أرض جافة', 'dust', 0.6, 0, 'soil', 1, -10, 55,
  lambda: mudcracks(103, [(0, C(120, 92, 62)), (0.5, C(152, 120, 84)), (1, C(182, 152, 112))], cells=14, width=1.3))
b('earth_desert_sand', 'earth', 'Desert Sand', 'رمل صحراوي', 'dust', 0.5, 0, 'sediment', 1, -5, 70,
  lambda: ripples(104, [(0, C(196, 160, 106)), (0.5, C(222, 190, 136)), (1, C(242, 216, 168))], freq=6, grain=0.35))
b('earth_red_desert_sand', 'earth', 'Red Desert Sand', 'رمل صحراوي أحمر', 'dust', 0.5, 0, 'sediment', 1, -5, 70,
  lambda: ripples(105, [(0, C(150, 70, 36)), (0.5, C(186, 100, 56)), (1, C(214, 136, 86))], freq=5, grain=0.4))
b('earth_wet_sand', 'earth', 'Wet Sand', 'رمل مبلل', 'mud', 0.6, 0, 'sediment', 2, 0, 40,
  lambda: np.clip(soil(106, [(0, C(104, 88, 62)), (0.5, C(130, 112, 82)), (1, C(152, 134, 104))], pebbles=6, grain=0.5, strength=0.5)
                  + blur(speckle(np.zeros((N, N, 3)), (0.12, 0.12, 0.14), 0.04, 1061), 1) * 0.4, 0, 1))
b('earth_beach_sand', 'earth', 'Beach Sand', 'رمل الشاطئ', 'dust', 0.5, 0, 'sediment', 1, 0, 50,
  lambda: speckle(speckle(dusty(107, [(0, C(206, 188, 150)), (0.5, C(228, 214, 180)), (1, C(246, 236, 208))], pebble=(8, 0.8, 1.6), grain=0.5),
                          C(250, 246, 240), 0.02, 1071), C(120, 110, 100), 0.01, 1072))
b('earth_rocky_soil', 'earth', 'Rocky Soil', 'تربة صخرية', 'dust', 0.9, 0, 'soil', 2, -30, 50,
  lambda: soil(108, [(0, C(70, 58, 46)), (0.5, C(100, 86, 70)), (1, C(132, 118, 100))], pebbles=34, grain=0.3, strength=1.1))
b('earth_limestone', 'earth', 'Limestone', 'حجر جيري', 'rock', 1.5, 0, 'sedimentary rock', 2, -30, 50,
  lambda: speckle(strata(109, [(0, C(176, 170, 152)), (0.5, C(202, 196, 178)), (1, C(224, 220, 204))], layers=3, k=0, crack=0.25, sharp=0.3),
                  C(150, 144, 128), 0.015, 1091, 2))
b('earth_granite', 'earth', 'Granite', 'جرانيت', 'rock', 2.5, 0, 'igneous rock', 2, -40, 50,
  lambda: rocky(110, [(0, C(150, 120, 112)), (0.5, C(186, 160, 150)), (1, C(214, 194, 186))], cells=7, crack=0.25,
                flecks=[(C(30, 28, 30), 0.07), (C(240, 232, 226), 0.04), (C(200, 120, 100), 0.04)]))
b('earth_basalt', 'earth', 'Basalt', 'بازلت', 'rock', 2.5, 0, 'igneous rock', 3, -40, 50,
  lambda: polygons(111, [(0, C(36, 36, 40)), (0.5, C(58, 58, 62)), (1, C(84, 84, 88))], cells=6, trough=0.7, gloss=0.05))
b('earth_sandstone', 'earth', 'Sandstone', 'حجر رملي', 'rock', 1.2, 0, 'sedimentary rock', 2, -20, 60,
  lambda: strata(112, [(0, C(176, 128, 82)), (0.5, C(206, 160, 110)), (1, C(230, 194, 146))], layers=5, k=1, crack=0.2))
b('earth_slate', 'earth', 'Slate', 'أردواز', 'rock', 2.0, 0, 'metamorphic rock', 2, -40, 50,
  lambda: strata(113, [(0, C(44, 50, 58)), (0.5, C(66, 72, 80)), (1, C(92, 98, 106))], layers=11, k=2, grain=0.15, crack=0.45, sharp=0.8))
b('earth_marble', 'earth', 'Marble', 'رخام', 'rock', 2.0, 0, 'metamorphic rock', 2, -40, 50,
  lambda: marble(114, [(0, C(214, 212, 208)), (0.6, C(236, 234, 230)), (1, C(250, 250, 248))], C(120, 120, 128)))
b('earth_snow', 'earth', 'Snow', 'ثلج', 'snow', 0.3, 0, 'snow', 0, -60, 0,
  lambda: snowy(115, [(0, C(206, 216, 230)), (0.6, C(236, 242, 250)), (1, C(255, 255, 255))], sparkle=0.02))
b('earth_compressed_ice', 'earth', 'Compressed Ice', 'جليد مضغوط', 'ice', 1.0, 0, 'ice', 1, -60, 0,
  lambda: icy(116, [(0, C(70, 130, 200)), (0.5, C(120, 178, 230)), (1, C(186, 222, 248))], cracks=7, bubbles=0.02, gloss=0.4))
b('earth_mountain_rock', 'earth', 'Mountain Rock', 'صخر جبلي', 'rock', 2.2, 0, 'metamorphic rock', 2, -50, 40,
  lambda: frosted(rocky(117, [(0, C(70, 70, 72)), (0.5, C(104, 102, 100)), (1, C(140, 138, 134))], cells=9, crack=0.8, strength=1.3), 1171, 0.35))
b('earth_volcanic_rock', 'earth', 'Volcanic Rock', 'صخر بركاني', 'rock', 1.8, 0, 'igneous rock', 1, -20, 60,
  lambda: porous(118, [(0, C(40, 30, 28)), (0.5, C(70, 52, 46)), (1, C(100, 78, 66))], pits=90))
b('earth_clay', 'earth', 'Clay', 'طين', 'mud', 0.6, 0, 'sediment', 2, -10, 45,
  lambda: soil(119, [(0, C(140, 104, 84)), (0.5, C(170, 132, 108)), (1, C(194, 160, 136))], pebbles=0, grain=0.15, strength=0.5))
b('earth_ocean_floor', 'earth', 'Deep Ocean Floor', 'قاع المحيط العميق', 'mud', 0.8, 0, 'sediment', 2, 0, 4,
  lambda: nodules(120, [(0, C(70, 74, 72)), (0.5, C(92, 96, 92)), (1, C(116, 120, 112))], C(30, 26, 24)))
# Mars
b('mars_regolith', 'mars', 'Martian Regolith', 'تربة المريخ السطحية', 'dust', 0.8, 0, 'regolith', 1, -125, 20,
  lambda: dusty(201, [(0, C(130, 62, 34)), (0.5, C(170, 88, 52)), (1, C(200, 120, 80))], pebble=(26, 1, 2.6), crater=2))
b('mars_dark_basalt', 'mars', 'Dark Martian Basalt', 'بازلت مريخي داكن', 'rock', 2.2, 0, 'igneous rock', 3, -125, 20,
  lambda: rocky(202, [(0, C(36, 30, 30)), (0.5, C(58, 48, 46)), (1, C(84, 66, 60))], cells=11, crack=0.7, flecks=[(C(140, 70, 40), 0.03)]))
b('mars_iron_oxide_soil', 'mars', 'Red Iron Oxide Soil', 'تربة أكسيد الحديد الحمراء', 'dust', 0.6, 0, 'regolith', 1, -125, 20,
  lambda: soil(203, [(0, C(140, 48, 24)), (0.5, C(176, 72, 38)), (1, C(206, 104, 62))], pebbles=12, grain=0.5))
b('mars_fine_dust', 'mars', 'Martian Dust', 'غبار المريخ', 'dust', 0.4, 0, 'dust', 0, -125, 20,
  lambda: snowy(204, [(0, C(178, 104, 66)), (0.6, C(204, 134, 92)), (1, C(222, 160, 118))], sparkle=0.0))
b('mars_dunes', 'mars', 'Martian Dunes', 'كثبان المريخ', 'dust', 0.5, 0, 'sediment', 1, -125, 20,
  lambda: ripples(205, [(0, C(70, 50, 46)), (0.5, C(110, 76, 62)), (1, C(150, 104, 80))], freq=9, grain=0.3))
b('mars_ancient_rock', 'mars', 'Ancient Martian Rock', 'صخر مريخي قديم', 'rock', 2.4, 0, 'igneous rock', 2, -125, 20,
  lambda: breccia(206, [(0, C(92, 56, 40)), (1, C(130, 84, 62))], [(0, C(60, 44, 40)), (0.5, C(140, 92, 70)), (1, C(170, 120, 92))]))
b('mars_sandstone', 'mars', 'Martian Sandstone', 'حجر رملي مريخي', 'rock', 1.4, 0, 'sedimentary rock', 2, -125, 20,
  lambda: strata(207, [(0, C(150, 86, 54)), (0.5, C(184, 116, 78)), (1, C(210, 150, 108))], layers=4, k=1, crack=0.3))
b('mars_hematite', 'mars', 'Hematite Deposits', 'رواسب الهيماتيت', 'rock', 2.0, 0, 'mineral deposit', 3, -125, 20,
  lambda: np.where((blobs(40, 0.9, 1.8, 2081) > 0.05)[..., None],
                   shade(np.full((N, N, 3), C(52, 44, 48)), blobs(40, 0.9, 1.8, 2081), 1.6, gloss=0.6),
                   dusty(208, [(0, C(120, 56, 36)), (1, C(170, 92, 62))], pebble=(0, 1, 2))))
b('mars_sulfate', 'mars', 'Sulfate Deposits', 'رواسب الكبريتات', 'rock', 1.2, 0, 'evaporite', 1, -125, 20,
  lambda: np.clip(strata(209, [(0, C(196, 170, 140)), (0.5, C(220, 200, 172)), (1, C(238, 226, 206))], layers=3, k=0, crack=0.5, sharp=0.4)
                  + speckle(np.zeros((N, N, 3)), C(255, 250, 240), 0.01, 2091) * 0.4, 0, 1))
b('mars_polar_ice', 'mars', 'Martian Polar Ice', 'جليد قطبي مريخي', 'ice', 0.9, 0, 'ice', 1, -150, -60,
  lambda: polygons(210, [(0, C(196, 180, 170)), (0.5, C(224, 214, 206)), (1, C(244, 240, 236))], cells=8, trough=0.45, gloss=0.25,
                   frost=C(255, 255, 255)))
b('mars_co2_frost', 'mars', 'Frozen Carbon Dioxide', 'ثاني أكسيد كربون متجمد', 'snow', 0.5, 0, 'volatile ice', 1, -150, -78,
  lambda: speckle(snowy(211, [(0, C(220, 224, 230)), (0.6, C(238, 242, 246)), (1, C(252, 252, 255))], sparkle=0.03), C(200, 210, 222), 0.02, 2111, 2))
b('mars_crater_rock', 'mars', 'Martian Crater Rock', 'صخر فوهات المريخ', 'rock', 2.0, 0, 'impactite', 2, -125, 20,
  lambda: ejecta(212, [(0, C(110, 60, 40)), (0.5, C(146, 86, 60)), (1, C(178, 116, 86))], rays=(0.9, 0.7, 0.55)))
b('mars_canyon_rock', 'mars', 'Martian Canyon Rock', 'صخر أخاديد المريخ', 'rock', 2.2, 0, 'sedimentary rock', 2, -125, 20,
  lambda: strata(213, [(0, C(90, 46, 30)), (0.4, C(140, 74, 46)), (0.7, C(176, 104, 70)), (1, C(206, 140, 100))], layers=9, k=0, crack=0.5,
                 sharp=0.8, strength=1.3))
b('mars_volcanic_rock', 'mars', 'Martian Volcanic Rock', 'صخر بركاني مريخي', 'rock', 2.0, 0, 'igneous rock', 2, -125, 20,
  lambda: ropy(214, [(0, C(50, 34, 30)), (0.5, C(84, 56, 46)), (1, C(118, 80, 64))], freq=5))
b('mars_sediment_layers', 'mars', 'Martian Sedimentary Layers', 'طبقات رسوبية مريخية', 'rock', 1.5, 0, 'sedimentary rock', 2, -125, 20,
  lambda: strata(215, [(0, C(120, 80, 60)), (0.3, C(170, 120, 90)), (0.6, C(200, 160, 120)), (1, C(150, 100, 76))], layers=14, k=1, grain=0.2,
                 crack=0.2, sharp=0.9))
# Moon
b('moon_highlands', 'moon', 'Lunar Highlands', 'مرتفعات القمر', 'rock', 1.4, 0, 'regolith', 2, -170, 120,
  lambda: dusty(301, [(0, C(150, 148, 144)), (0.5, C(186, 184, 180)), (1, C(220, 218, 214))], pebble=(18, 1, 2.4), crater=9))
b('moon_maria_basalt', 'moon', 'Lunar Maria Basalt', 'بازلت البحار القمرية', 'rock', 2.0, 0, 'igneous rock', 3, -170, 120,
  lambda: rocky(302, [(0, C(44, 44, 46)), (0.5, C(64, 64, 66)), (1, C(88, 88, 88))], cells=12, crack=0.5, flecks=[(C(120, 110, 90), 0.01)]))
b('moon_regolith', 'moon', 'Lunar Regolith', 'تربة القمر السطحية', 'dust', 0.7, 0, 'regolith', 1, -170, 120,
  lambda: dusty(303, [(0, C(96, 96, 96)), (0.5, C(124, 124, 122)), (1, C(152, 150, 148))], pebble=(30, 0.8, 2.2), crater=12))
b('moon_dust', 'moon', 'Lunar Dust', 'غبار القمر', 'dust', 0.3, 0, 'dust', 0, -170, 120,
  lambda: snowy(304, [(0, C(110, 110, 110)), (0.6, C(132, 132, 130)), (1, C(150, 150, 148))], sparkle=0.004))
b('moon_breccia', 'moon', 'Lunar Breccia', 'بريشيا قمرية', 'rock', 2.0, 0, 'impactite', 2, -170, 120,
  lambda: breccia(305, [(0, C(70, 70, 72)), (1, C(100, 100, 100))], [(0, C(60, 60, 62)), (0.5, C(160, 158, 154)), (1, C(220, 218, 214))]))
b('moon_anorthosite', 'moon', 'Anorthosite', 'أنورثوسيت', 'rock', 2.2, 0, 'igneous rock', 2, -170, 120,
  lambda: crystal(306, [(0, C(196, 196, 196)), (0.5, C(220, 220, 218)), (1, C(240, 240, 238))], cells=26))
b('moon_impact_melt', 'moon', 'Impact Melt Rock', 'صخر الانصهار الارتطامي', 'rock', 2.0, 0, 'impactite', 3, -170, 120,
  lambda: ropy(307, [(0, C(40, 40, 42)), (0.5, C(70, 68, 66)), (1, C(104, 100, 96))], freq=7))
b('moon_crater_debris', 'moon', 'Lunar Crater Debris', 'حطام فوهات القمر', 'rock', 1.6, 0, 'impactite', 2, -170, 120,
  lambda: ejecta(308, [(0, C(90, 90, 90)), (0.5, C(130, 130, 128)), (1, C(170, 168, 164))]))
b('moon_impact_glass', 'moon', 'Glassy Impact Material', 'مادة زجاجية ارتطامية', 'glass', 0.8, 0, 'glass', 2, -170, 120,
  lambda: np.where((blobs(46, 0.8, 1.6, 3091) > 0.05)[..., None],
                   shade(np.full((N, N, 3), C(150, 110, 50)), blobs(46, 0.8, 1.6, 3091), 1.5, gloss=0.8),
                   dusty(309, [(0, C(70, 68, 66)), (1, C(110, 108, 104))], pebble=(0, 1, 2))))
b('moon_polar_ice', 'moon', 'Lunar Polar Ice', 'جليد قطبي قمري', 'ice', 0.9, 0, 'ice', 1, -240, -170,
  lambda: frosted(dusty(310, [(0, C(70, 70, 74)), (0.5, C(96, 96, 100)), (1, C(124, 124, 128))], pebble=(16, 1, 2), crater=5), 3101, 0.55,
                  (0.8, 0.86, 0.94), 0.4))
# Mercury
b('mercury_regolith', 'mercury', 'Mercury Regolith', 'تربة عطارد السطحية', 'dust', 0.8, 0, 'regolith', 2, -180, 430,
  lambda: dusty(401, [(0, C(84, 80, 76)), (0.5, C(116, 110, 104)), (1, C(146, 140, 132))], pebble=(24, 1, 2.2), crater=10))
b('mercury_dark_rock', 'mercury', 'Dark Mercury Rock', 'صخر عطارد الداكن', 'rock', 2.2, 0, 'igneous rock', 3, -180, 430,
  lambda: rocky(402, [(0, C(30, 30, 34)), (0.5, C(48, 48, 54)), (1, C(70, 70, 78))], cells=13, crack=0.6, flecks=[(C(90, 96, 110), 0.02)]))
b('mercury_crater_material', 'mercury', 'Mercury Crater Material', 'مادة فوهات عطارد', 'rock', 1.8, 0, 'impactite', 2, -180, 430,
  lambda: ejecta(403, [(0, C(70, 66, 62)), (0.5, C(104, 98, 92)), (1, C(140, 132, 124))], rays=(0.8, 0.78, 0.74)))
b('mercury_ancient_impact_rock', 'mercury', 'Ancient Impact Rock', 'صخر ارتطامي قديم', 'rock', 2.4, 0, 'impactite', 3, -180, 430,
  lambda: breccia(404, [(0, C(60, 56, 54)), (1, C(90, 84, 80))], [(0, C(40, 38, 38)), (0.5, C(110, 104, 98)), (1, C(150, 142, 132))], cells=22))
b('mercury_volcanic_plains', 'mercury', 'Mercury Volcanic Plains', 'سهول عطارد البركانية', 'rock', 2.0, 0, 'igneous rock', 3, -180, 430,
  lambda: ropy(405, [(0, C(80, 74, 66)), (0.5, C(110, 102, 92)), (1, C(140, 132, 120))], freq=4))
b('mercury_silicate_rock', 'mercury', 'Mercury Silicate Rock', 'صخر سيليكاتي عطاردي', 'rock', 2.0, 0, 'igneous rock', 2, -180, 430,
  lambda: rocky(406, [(0, C(100, 94, 86)), (0.5, C(132, 124, 114)), (1, C(164, 156, 144))], cells=17, crack=0.4,
                flecks=[(C(60, 56, 50), 0.03), (C(200, 194, 186), 0.02)]))
b('mercury_bright_deposits', 'mercury', 'Mercury Bright Deposits', 'رواسب عطارد اللامعة', 'dust', 0.6, 0, 'volatile deposit', 1, -180, 430,
  lambda: hollows(407, [(0, C(70, 66, 64)), (0.5, C(96, 92, 88)), (1, C(122, 116, 110))], C(200, 214, 226)))
b('mercury_polar_ice', 'mercury', 'Mercury Polar Ice', 'جليد عطارد القطبي', 'ice', 0.9, 0, 'ice', 1, -200, -150,
  lambda: icy(408, [(0, C(130, 140, 156)), (0.5, C(170, 180, 194)), (1, C(206, 214, 226))], cracks=8, bubbles=0.004, gloss=0.2))
# Venus
b('venus_basalt', 'venus', 'Venus Basalt', 'بازلت الزهرة', 'rock', 2.2, 0, 'igneous rock', 3, 440, 480,
  lambda: rocky(501, [(0, C(72, 50, 34)), (0.5, C(104, 74, 50)), (1, C(136, 102, 72))], cells=12, crack=0.6))
b('venus_volcanic_rock', 'venus', 'Venus Volcanic Rock', 'صخر الزهرة البركاني', 'rock', 2.0, 0, 'igneous rock', 3, 440, 480,
  lambda: porous(502, [(0, C(70, 44, 28)), (0.5, C(110, 72, 44)), (1, C(150, 106, 70))], pits=70))
b('venus_lava_plains', 'venus', 'Venus Lava Plains', 'سهول الزهرة البركانية', 'rock', 1.8, 2, 'igneous rock', 3, 440, 480,
  lambda: ropy(503, [(0, C(80, 52, 30)), (0.5, C(120, 82, 50)), (1, C(160, 116, 76))], glow=[(0, C(160, 40, 10)), (1, C(255, 130, 40))], freq=5))
b('venus_tessera', 'venus', 'Venus Tessera Terrain', 'تضاريس تيسيرا الزهرة', 'rock', 2.4, 0, 'deformed rock', 3, 440, 480,
  lambda: tessera(504, [(0, C(100, 70, 44)), (0.5, C(146, 108, 70)), (1, C(186, 146, 100))]))
b('venus_highland_rock', 'venus', 'Venus Highland Rock', 'صخر مرتفعات الزهرة', 'rock', 2.2, 0, 'igneous rock', 2, 380, 440,
  lambda: speckle(rocky(505, [(0, C(130, 96, 66)), (0.5, C(170, 130, 92)), (1, C(206, 168, 126))], cells=8, crack=0.7), C(220, 220, 230), 0.012, 5051))
b('venus_fractured_rock', 'venus', 'Venus Fractured Rock', 'صخر الزهرة المتشقق', 'rock', 1.8, 0, 'deformed rock', 3, 440, 480,
  lambda: mudcracks(506, [(0, C(110, 76, 46)), (0.5, C(146, 104, 66)), (1, C(178, 136, 94))], cells=18, width=0.8, strength=1.2))
b('venus_volcanic_deposits', 'venus', 'Venus Volcanic Deposits', 'رواسب الزهرة البركانية', 'dust', 0.8, 0, 'pyroclastic', 1, 440, 480,
  lambda: sulfur_flow(507, [(0, C(140, 100, 50)), (0.5, C(180, 140, 70)), (1, C(214, 184, 110))], dark=C(80, 56, 36)))
b('venus_impact_material', 'venus', 'Venus Impact Material', 'مادة ارتطامية زهرية', 'rock', 1.8, 0, 'impactite', 2, 440, 480,
  lambda: ejecta(508, [(0, C(96, 66, 40)), (0.5, C(136, 98, 62)), (1, C(176, 136, 94))], rays=(0.95, 0.8, 0.55)))
# Io
b('io_sulfur', 'io', 'Io Sulfur', 'كبريت آيو', 'dust', 0.7, 0, 'volatile deposit', 1, -160, 1600,
  lambda: sulfur_flow(601, [(0, C(196, 170, 60)), (0.5, C(226, 206, 96)), (1, C(246, 236, 150))]))
b('io_yellow_sulfur', 'io', 'Io Yellow Sulfur Deposits', 'رواسب كبريت آيو الصفراء', 'crystal', 0.8, 0, 'mineral deposit', 1, -160, 200,
  lambda: crystal(602, [(0, C(206, 180, 30)), (0.5, C(236, 214, 60)), (1, C(255, 244, 130))], cells=18))
b('io_black_lava', 'io', 'Io Black Lava Rock', 'صخر حمم آيو السوداء', 'rock', 2.0, 5, 'igneous rock', 3, -160, 1600,
  lambda: lava(603, [(0, C(10, 10, 12)), (1, C(36, 32, 32))], [(0, C(150, 20, 4)), (0.5, C(240, 90, 10)), (1, C(255, 210, 110))], cells=9, seam=1.0))
b('io_silicate_rock', 'io', 'Io Silicate Volcanic Rock', 'صخر آيو البركاني السيليكاتي', 'rock', 2.2, 0, 'igneous rock', 3, -160, 1600,
  lambda: rocky(604, [(0, C(70, 56, 40)), (0.5, C(104, 86, 60)), (1, C(140, 120, 88))], cells=10, crack=0.6, flecks=[(C(220, 200, 80), 0.02)]))
b('io_orange_sulfur', 'io', 'Io Orange Sulfur Deposits', 'رواسب كبريت آيو البرتقالية', 'dust', 0.7, 0, 'volatile deposit', 1, -160, 200,
  lambda: sulfur_flow(605, [(0, C(170, 70, 20)), (0.5, C(214, 110, 40)), (1, C(240, 160, 80))], dark=C(120, 40, 20)))
b('io_so2_frost', 'io', 'Io Frozen Sulfur Dioxide', 'ثاني أكسيد الكبريت المتجمد', 'snow', 0.5, 0, 'volatile ice', 1, -160, -100,
  lambda: speckle(snowy(606, [(0, C(206, 206, 190)), (0.6, C(230, 232, 220)), (1, C(248, 250, 240))], sparkle=0.02), C(220, 210, 140), 0.02, 6061))
# Europa
b('europa_water_ice', 'europa', 'Europa Water Ice', 'جليد مائي أوروبي', 'ice', 0.8, 0, 'ice', 1, -220, -140,
  lambda: icy(701, [(0, C(200, 196, 186)), (0.5, C(226, 224, 216)), (1, C(246, 246, 242))], cracks=5, bubbles=0.003, gloss=0.25))
b('europa_fractured_ice', 'europa', 'Europa Fractured Ice', 'جليد أوروبا المتشقق', 'ice', 0.8, 0, 'ice', 1, -220, -140,
  lambda: mudcracks(702, [(0, C(176, 166, 150)), (0.5, C(214, 206, 192)), (1, C(238, 234, 226))], cells=16, width=0.7, strength=0.8))
b('europa_red_deposits', 'europa', 'Europa Red-Brown Surface Deposits', 'رواسب أوروبا الحمراء البنية', 'dust', 0.6, 0, 'salt deposit', 1, -220, -140,
  lambda: sulfur_flow(703, [(0, C(110, 60, 40)), (0.5, C(150, 92, 62)), (1, C(190, 140, 110))], dark=C(80, 40, 30)))
b('europa_chaos', 'europa', 'Europa Chaos Terrain', 'تضاريس أوروبا الفوضوية', 'ice', 1.0, 0, 'ice', 1, -220, -140,
  lambda: chaos(704, [(0, C(196, 190, 178)), (0.6, C(224, 220, 210)), (1, C(244, 242, 236))], [(0, C(110, 70, 50)), (1, C(160, 116, 90))]))
b('europa_ice_ridges', 'europa', 'Europa Ice Ridges', 'حواف جليد أوروبا', 'ice', 1.0, 0, 'ice', 1, -220, -140,
  lambda: ridged_ice(705, [(0, C(210, 204, 194)), (0.6, C(232, 228, 220)), (1, C(248, 246, 242))], C(150, 90, 60), count=3))
b('europa_ocean_ice', 'europa', 'Europa Subsurface Ocean Material', 'مادة محيط أوروبا الجوفي', 'mud', 0.6, 0, 'brine ice', 2, -40, 0,
  lambda: np.clip(icy(706, [(0, C(40, 70, 110)), (0.5, C(70, 110, 150)), (1, C(130, 170, 200))], cracks=4, bubbles=0.03, gloss=0.3)
                  + speckle(np.zeros((N, N, 3)), C(240, 240, 230), 0.01, 7061) * 0.6, 0, 1))
# Ganymede
b('ganymede_dark_terrain', 'ganymede', 'Ganymede Dark Terrain', 'تضاريس غانيميد الداكنة', 'rock', 1.6, 0, 'ice-rock mix', 2, -200, -120,
  lambda: dusty(801, [(0, C(66, 60, 54)), (0.5, C(92, 84, 76)), (1, C(120, 112, 102))], pebble=(18, 1, 2.2), crater=6))
b('ganymede_bright_ice', 'ganymede', 'Ganymede Bright Ice', 'جليد غانيميد اللامع', 'ice', 0.8, 0, 'ice', 1, -200, -120,
  lambda: icy(802, [(0, C(176, 180, 186)), (0.5, C(210, 214, 220)), (1, C(236, 238, 242))], cracks=9, bubbles=0.006, gloss=0.2))
b('ganymede_grooved_ice', 'ganymede', 'Ganymede Grooved Ice', 'جليد غانيميد المخدد', 'ice', 1.0, 0, 'ice', 1, -200, -120,
  lambda: grooves(803, [(0, C(130, 132, 136)), (0.5, C(176, 178, 182)), (1, C(214, 216, 220))], freq=10, k=3, depth=0.7))
b('ganymede_crater_ice', 'ganymede', 'Ganymede Crater Ice', 'جليد فوهات غانيميد', 'ice', 0.9, 0, 'ice', 1, -200, -120,
  lambda: ejecta(804, [(0, C(150, 150, 152)), (0.5, C(190, 192, 196)), (1, C(226, 228, 232))], rays=(0.95, 0.97, 1.0)))
b('ganymede_ancient_rock', 'ganymede', 'Ganymede Ancient Surface Rock', 'صخر غانيميد السطحي القديم', 'rock', 2.0, 0, 'ice-rock mix', 2, -200, -120,
  lambda: breccia(805, [(0, C(70, 64, 58)), (1, C(100, 92, 84))], [(0, C(60, 56, 52)), (0.5, C(140, 134, 126)), (1, C(200, 198, 196))], cells=14))
# Callisto
b('callisto_dark_ice', 'callisto', 'Callisto Dark Ice', 'جليد كاليستو الداكن', 'ice', 1.0, 0, 'ice-rock mix', 2, -190, -110,
  lambda: frosted(dusty(901, [(0, C(50, 46, 44)), (0.5, C(72, 66, 62)), (1, C(96, 90, 84))], pebble=(12, 1, 2), crater=4), 9011, 0.3,
                  (0.86, 0.86, 0.88), 0.55))
b('callisto_crater_material', 'callisto', 'Callisto Ancient Crater Material', 'مادة فوهات كاليستو القديمة', 'rock', 1.6, 0, 'impactite', 2, -190, -110,
  lambda: dusty(902, [(0, C(70, 64, 58)), (0.5, C(100, 92, 84)), (1, C(130, 122, 112))], pebble=(14, 1, 2.4), crater=16))
b('callisto_ice_rock', 'callisto', 'Callisto Ice-Rock Mixture', 'خليط جليد وصخر كاليستو', 'rock', 1.4, 0, 'ice-rock mix', 2, -190, -110,
  lambda: speckle(rocky(903, [(0, C(76, 70, 64)), (0.5, C(104, 98, 90)), (1, C(136, 130, 122))], cells=15, crack=0.4), C(220, 222, 226), 0.05, 9031, 2))
b('callisto_impact_debris', 'callisto', 'Callisto Impact Debris', 'حطام كاليستو الارتطامي', 'rock', 1.4, 0, 'impactite', 2, -190, -110,
  lambda: ejecta(904, [(0, C(60, 56, 52)), (0.5, C(92, 86, 80)), (1, C(126, 118, 110))], rays=(0.9, 0.92, 0.95)))
# Titan
b('titan_organic_sand', 'titan', 'Titan Organic Sand', 'رمل تيتان العضوي', 'dust', 0.6, 0, 'organic sediment', 1, -180, -178,
  lambda: dusty(1001, [(0, C(60, 44, 26)), (0.5, C(86, 64, 38)), (1, C(112, 86, 54))], pebble=(6, 0.8, 1.6), grain=0.5))
b('titan_hydrocarbon_ice', 'titan', 'Titan Hydrocarbon Ice', 'جليد تيتان الهيدروكربوني', 'ice', 0.9, 0, 'volatile ice', 1, -180, -178,
  lambda: icy(1002, [(0, C(140, 110, 70)), (0.5, C(176, 146, 100)), (1, C(206, 182, 136))], cracks=8, bubbles=0.012, gloss=0.3))
b('titan_lake_bed', 'titan', 'Titan Methane Lake Bed', 'قاع بحيرة ميثان تيتان', 'mud', 0.6, 0, 'organic sediment', 2, -180, -178,
  lambda: mudcracks(1003, [(0, C(50, 40, 30)), (0.5, C(74, 60, 44)), (1, C(98, 82, 62))], cells=10, width=1.6, strength=0.7))
b('titan_dune_material', 'titan', 'Titan Dark Dune Material', 'مادة كثبان تيتان الداكنة', 'dust', 0.5, 0, 'organic sediment', 1, -180, -178,
  lambda: ripples(1004, [(0, C(40, 30, 22)), (0.5, C(62, 48, 34)), (1, C(88, 70, 50))], freq=8, grain=0.25))
b('titan_ice_bedrock', 'titan', 'Titan Water Ice Bedrock', 'صخر تيتان الجليدي', 'ice', 2.0, 0, 'ice bedrock', 2, -180, -178,
  lambda: rocky(1005, [(0, C(150, 140, 124)), (0.5, C(184, 174, 156)), (1, C(212, 204, 188))], cells=10, crack=0.6, strength=0.9))
b('titan_organic_deposits', 'titan', 'Titan Organic Deposits', 'رواسب تيتان العضوية', 'mud', 0.6, 0, 'organic sediment', 1, -180, -178,
  lambda: sulfur_flow(1006, [(0, C(100, 60, 24)), (0.5, C(140, 92, 40)), (1, C(176, 128, 66))], dark=C(50, 30, 16)))
# Enceladus
b('enceladus_bright_ice', 'enceladus', 'Enceladus Bright Ice', 'جليد إنسيلادوس اللامع', 'ice', 0.8, 0, 'ice', 1, -200, -180,
  lambda: snowy(1101, [(0, C(226, 234, 242)), (0.6, C(244, 248, 252)), (1, C(255, 255, 255))], sparkle=0.03))
b('enceladus_fractured_ice', 'enceladus', 'Enceladus Fractured Ice', 'جليد إنسيلادوس المتشقق', 'ice', 1.0, 0, 'ice', 1, -200, -160,
  lambda: stripes(1102, [(0, C(206, 216, 226)), (0.5, C(230, 236, 242)), (1, C(248, 250, 252))], C(70, 120, 150)))
b('enceladus_fresh_ice', 'enceladus', 'Enceladus Fresh Ice Deposits', 'رواسب جليد إنسيلادوس الطازجة', 'snow', 0.4, 0, 'plume deposit', 0, -200, -180,
  lambda: speckle(snowy(1103, [(0, C(232, 240, 248)), (0.6, C(248, 252, 255)), (1, C(255, 255, 255))], sparkle=0.05), C(200, 230, 255), 0.02, 11031))
b('enceladus_cryovolcanic', 'enceladus', 'Enceladus Cryovolcanic Material', 'مادة إنسيلادوس البركانية الجليدية', 'ice', 0.8, 2, 'cryovolcanic', 1, -180, -90,
  lambda: np.clip(porous(1104, [(0, C(150, 186, 206)), (0.5, C(186, 214, 230)), (1, C(222, 238, 248))], pits=40, strength=0.7)
                  + blur(speckle(np.zeros((N, N, 3)), (0.6, 0.85, 1.0), 0.02, 11041), 1) * 0.5, 0, 1))
# Other Saturn moons
b('rhea_ice', 'saturn_moons', 'Rhea Ice', 'جليد ريا', 'ice', 0.9, 0, 'ice', 1, -220, -174,
  lambda: dusty(1201, [(0, C(170, 170, 172)), (0.5, C(204, 204, 206)), (1, C(232, 232, 234))], pebble=(10, 1, 2), crater=11, strength=0.7))
b('dione_ice', 'saturn_moons', 'Dione Ice', 'جليد ديون', 'ice', 0.9, 0, 'ice', 1, -220, -186,
  lambda: ridged_ice(1202, [(0, C(176, 178, 182)), (0.6, C(206, 208, 212)), (1, C(230, 232, 236))], C(250, 252, 255), count=4))
b('iapetus_dark_material', 'saturn_moons', 'Iapetus Dark Material', 'مادة يابيتوس الداكنة', 'dust', 0.6, 0, 'organic deposit', 1, -170, -140,
  lambda: dusty(1203, [(0, C(40, 26, 18)), (0.5, C(62, 42, 28)), (1, C(86, 60, 40))], pebble=(16, 1, 2), crater=4, grain=0.45))
b('iapetus_bright_ice', 'saturn_moons', 'Iapetus Bright Ice', 'جليد يابيتوس اللامع', 'ice', 0.9, 0, 'ice', 1, -200, -170,
  lambda: icy(1204, [(0, C(206, 204, 198)), (0.5, C(230, 228, 224)), (1, C(250, 250, 248))], cracks=11, bubbles=0.004, gloss=0.15))
b('mimas_crater_material', 'saturn_moons', 'Mimas Crater Material', 'مادة فوهات ميماس', 'ice', 1.0, 0, 'impactite', 1, -210, -180,
  lambda: dusty(1205, [(0, C(150, 150, 152)), (0.5, C(184, 184, 186)), (1, C(214, 214, 216))], pebble=(8, 1, 2), crater=22, strength=1.1))
b('hyperion_porous_ice', 'saturn_moons', 'Hyperion Porous Ice-Rock', 'صخر جليدي مسامي لهايبريون', 'ice', 0.7, 0, 'ice-rock mix', 0, -200, -180,
  lambda: porous(1206, [(0, C(110, 96, 78)), (0.5, C(150, 134, 112)), (1, C(186, 172, 150))], pits=120, strength=1.3))
# Uranus and Neptune moons
b('triton_nitrogen_ice', 'outer_moons', 'Triton Nitrogen Ice', 'جليد نيتروجين تريتون', 'ice', 0.6, 0, 'volatile ice', 1, -238, -230,
  lambda: polygons(1301, [(0, C(210, 196, 190)), (0.5, C(230, 220, 214)), (1, C(246, 240, 236))], cells=9, trough=0.3, gloss=0.3))
b('triton_methane_frost', 'outer_moons', 'Triton Frozen Methane', 'ميثان تريتون المتجمد', 'snow', 0.4, 0, 'volatile ice', 0, -238, -230,
  lambda: snowy(1302, [(0, C(206, 190, 178)), (0.6, C(228, 214, 204)), (1, C(244, 236, 230))], sparkle=0.025))
b('triton_water_ice', 'outer_moons', 'Triton Water Ice', 'جليد تريتون المائي', 'ice', 1.0, 0, 'ice bedrock', 1, -238, -230,
  lambda: grooves(1303, [(0, C(150, 150, 140)), (0.5, C(184, 182, 170)), (1, C(214, 212, 200))], freq=5, k=5, depth=0.4))
b('triton_cryovolcanic', 'outer_moons', 'Triton Cryovolcanic Deposits', 'رواسب تريتون البركانية الجليدية', 'dust', 0.6, 0, 'cryovolcanic', 1, -238, -230,
  lambda: speckle(sulfur_flow(1304, [(0, C(170, 140, 130)), (0.5, C(200, 176, 166)), (1, C(226, 210, 202))], dark=C(60, 46, 46)), C(40, 30, 30), 0.015,
                  13041, 2))
b('miranda_fractured_ice', 'outer_moons', 'Miranda Fractured Ice', 'جليد ميراندا المتشقق', 'ice', 1.0, 0, 'ice', 1, -210, -187,
  lambda: grooves(1305, [(0, C(130, 132, 136)), (0.5, C(170, 172, 176)), (1, C(206, 208, 212))], freq=14, k=1, depth=0.9, strength=1.3))
b('miranda_ancient_rock', 'outer_moons', 'Miranda Ancient Rock', 'صخر ميراندا القديم', 'rock', 1.8, 0, 'ice-rock mix', 2, -210, -187,
  lambda: dusty(1306, [(0, C(100, 100, 102)), (0.5, C(132, 132, 134)), (1, C(164, 164, 166))], pebble=(14, 1, 2.4), crater=14))
b('ariel_ice', 'outer_moons', 'Ariel Ice', 'جليد آرييل', 'ice', 0.9, 0, 'ice', 1, -213, -190,
  lambda: mudcracks(1307, [(0, C(170, 172, 176)), (0.5, C(200, 202, 206)), (1, C(226, 228, 232))], cells=7, width=1.8, strength=0.9))
b('umbriel_dark_ice', 'outer_moons', 'Umbriel Dark Ice', 'جليد أمبريل الداكن', 'ice', 1.0, 0, 'ice-rock mix', 2, -213, -190,
  lambda: dusty(1308, [(0, C(50, 50, 52)), (0.5, C(70, 70, 72)), (1, C(92, 92, 94))], pebble=(12, 1, 2), crater=12, strength=0.8))
b('oberon_ice_rock', 'outer_moons', 'Oberon Ice-Rock', 'صخر أوبيرون الجليدي', 'rock', 1.6, 0, 'ice-rock mix', 2, -213, -190,
  lambda: speckle(rocky(1309, [(0, C(90, 80, 76)), (0.5, C(120, 110, 104)), (1, C(150, 140, 132))], cells=12, crack=0.4), C(214, 216, 220), 0.03, 13091))
b('neptune_moon_deposits', 'outer_moons', 'Neptune Moon Surface Deposits', 'رواسب أسطح أقمار نبتون', 'dust', 0.6, 0, 'volatile deposit', 1, -238, -220,
  lambda: dusty(1310, [(0, C(150, 130, 126)), (0.5, C(180, 162, 156)), (1, C(206, 192, 186))], pebble=(10, 1, 2), grain=0.4, strength=0.7))
# Dwarf planets
b('pluto_nitrogen_ice', 'dwarf_planets', 'Pluto Nitrogen Ice', 'جليد نيتروجين بلوتو', 'ice', 0.6, 0, 'volatile ice', 1, -240, -218,
  lambda: polygons(1401, [(0, C(220, 204, 186)), (0.5, C(238, 226, 212)), (1, C(250, 244, 236))], cells=5, trough=0.6, gloss=0.2))
b('pluto_methane_ice', 'dwarf_planets', 'Pluto Methane Ice', 'جليد ميثان بلوتو', 'ice', 0.6, 0, 'volatile ice', 1, -240, -218,
  lambda: grooves(1402, [(0, C(196, 170, 150)), (0.5, C(220, 200, 184)), (1, C(240, 228, 216))], freq=6, k=2, depth=0.8))
b('pluto_ice_bedrock', 'dwarf_planets', 'Pluto Water Ice Bedrock', 'صخر بلوتو الجليدي المائي', 'ice', 2.0, 0, 'ice bedrock', 2, -240, -218,
  lambda: rocky(1403, [(0, C(140, 130, 124)), (0.5, C(170, 162, 156)), (1, C(200, 194, 188))], cells=8, crack=0.8, strength=1.3))
b('pluto_dark_organics', 'dwarf_planets', 'Pluto Dark Organic Material', 'مادة بلوتو العضوية الداكنة', 'dust', 0.6, 0, 'organic deposit', 1, -240, -218,
  lambda: dusty(1404, [(0, C(70, 30, 18)), (0.5, C(104, 50, 30)), (1, C(136, 76, 50))], pebble=(10, 1, 2), crater=3, grain=0.45))
b('pluto_crater_rock', 'dwarf_planets', 'Pluto Ancient Crater Rock', 'صخر فوهات بلوتو القديمة', 'rock', 1.6, 0, 'impactite', 2, -240, -218,
  lambda: ejecta(1405, [(0, C(110, 80, 64)), (0.5, C(146, 114, 96)), (1, C(180, 152, 134))], rays=(0.95, 0.92, 0.88)))
b('charon_water_ice', 'dwarf_planets', 'Charon Water Ice', 'جليد شارون المائي', 'ice', 1.0, 0, 'ice', 1, -240, -220,
  lambda: icy(1406, [(0, C(140, 140, 144)), (0.5, C(170, 170, 174)), (1, C(198, 198, 202))], cracks=12, bubbles=0.004, gloss=0.1))
b('ceres_salt_deposits', 'dwarf_planets', 'Ceres Bright Salt Deposits', 'رواسب سيريس الملحية اللامعة', 'crystal', 0.8, 0, 'evaporite', 1, -130, -40,
  lambda: salt(1407))
b('ceres_carbonate', 'dwarf_planets', 'Ceres Carbonate Material', 'مادة سيريس الكربونية', 'rock', 1.2, 0, 'carbonate', 2, -130, -40,
  lambda: crystal(1408, [(0, C(150, 146, 136)), (0.5, C(184, 180, 168)), (1, C(214, 212, 200))], cells=30))
b('ceres_dark_regolith', 'dwarf_planets', 'Ceres Dark Regolith', 'تربة سيريس الداكنة', 'dust', 0.7, 0, 'regolith', 1, -130, -40,
  lambda: dusty(1409, [(0, C(42, 40, 40)), (0.5, C(62, 60, 58)), (1, C(84, 82, 78))], pebble=(20, 1, 2), crater=8))
b('haumea_water_ice', 'dwarf_planets', 'Haumea Water Ice', 'جليد هاوميا المائي', 'ice', 0.9, 0, 'crystalline ice', 1, -241, -223,
  lambda: crystal(1410, [(0, C(196, 214, 230)), (0.5, C(220, 232, 244)), (1, C(242, 248, 255))], cells=20, glow=0.1))
b('makemake_methane_ice', 'dwarf_planets', 'Makemake Methane Ice', 'جليد ميكميك الميثاني', 'ice', 0.6, 0, 'volatile ice', 1, -243, -239,
  lambda: icy(1411, [(0, C(186, 140, 110)), (0.5, C(214, 176, 150)), (1, C(236, 210, 190))], cracks=9, bubbles=0.01, gloss=0.2))
b('eris_methane_ice', 'dwarf_planets', 'Eris Methane Ice', 'جليد إيريس الميثاني', 'snow', 0.5, 0, 'volatile ice', 1, -243, -217,
  lambda: snowy(1412, [(0, C(222, 222, 220)), (0.6, C(240, 240, 238)), (1, C(254, 254, 252))], sparkle=0.015))
# Asteroids and comets
b('asteroid_carbonaceous_rock', 'small_bodies', 'Carbonaceous Asteroid Rock', 'صخر كويكبي كربوني', 'rock', 1.4, 0, 'chondrite', 1, -100, 30,
  lambda: speckle(dusty(1501, [(0, C(30, 30, 32)), (0.5, C(46, 46, 48)), (1, C(64, 64, 66))], pebble=(40, 1, 2.5), grain=0.4), C(200, 196, 180),
                  0.012, 15011))
b('asteroid_metallic_iron', 'small_bodies', 'Metallic Asteroid Iron', 'حديد كويكبي معدني', 'metal', 5.0, 0, 'metallic', 3, -100, 30,
  lambda: widmanstatten(1502, [(0, C(70, 72, 76)), (0.6, C(120, 122, 126)), (1, C(170, 172, 176))]))
b('asteroid_nickel_iron', 'small_bodies', 'Nickel-Iron Material', 'مادة النيكل والحديد', 'metal', 5.5, 0, 'metallic', 3, -100, 30,
  lambda: shade(ramp(fbm(4, 4, 1503) * 0.6 + blobs(30, 1, 3, 15031) * 0.4, [(0, C(90, 86, 80)), (0.5, C(140, 134, 124)), (1, C(190, 184, 172))]),
                blobs(30, 1, 3, 15031) * 0.6 + fbm(16, 2, 15032) * 0.3, 0.9, gloss=0.5))
b('asteroid_silicate_rock', 'small_bodies', 'Silicate Asteroid Rock', 'صخر كويكبي سيليكاتي', 'rock', 2.0, 0, 'chondrite', 2, -100, 30,
  lambda: rocky(1504, [(0, C(110, 100, 86)), (0.5, C(146, 134, 116)), (1, C(178, 166, 146))], cells=16, crack=0.5,
                flecks=[(C(60, 60, 58), 0.03), (C(210, 210, 200), 0.02), (C(140, 140, 150), 0.03)]))
b('asteroid_regolith', 'small_bodies', 'Asteroid Regolith', 'تربة كويكبية', 'dust', 0.6, 0, 'regolith', 1, -100, 30,
  lambda: dusty(1505, [(0, C(90, 84, 76)), (0.5, C(120, 112, 102)), (1, C(150, 142, 130))], pebble=(46, 1.2, 3.4), grain=0.3, strength=1.2))
b('asteroid_impact_material', 'small_bodies', 'Asteroid Impact Material', 'مادة ارتطام كويكبية', 'rock', 1.6, 0, 'impactite', 2, -100, 30,
  lambda: breccia(1506, [(0, C(70, 66, 60)), (1, C(100, 94, 86))], [(0, C(50, 48, 46)), (0.5, C(130, 122, 112)), (1, C(180, 172, 160))], cells=24))
b('comet_porous_ice', 'small_bodies', 'Porous Comet Ice', 'جليد مذنب مسامي', 'ice', 0.5, 0, 'cometary ice', 0, -220, -70,
  lambda: porous(1507, [(0, C(150, 150, 156)), (0.5, C(190, 190, 196)), (1, C(224, 226, 232))], pits=110, strength=1.0, dust=C(60, 58, 56)))
b('comet_dust', 'small_bodies', 'Comet Dust', 'غبار مذنب', 'dust', 0.3, 0, 'dust', 0, -220, -70,
  lambda: snowy(1508, [(0, C(40, 38, 36)), (0.6, C(56, 54, 50)), (1, C(76, 72, 68))], sparkle=0.006))
b('comet_co2_ice', 'small_bodies', 'Comet Frozen Carbon Dioxide', 'ثاني أكسيد كربون متجمد مذنبي', 'snow', 0.5, 0, 'volatile ice', 1, -220, -78,
  lambda: crystal(1509, [(0, C(206, 212, 220)), (0.5, C(226, 232, 238)), (1, C(246, 250, 252))], cells=36))
b('comet_co_ice', 'small_bodies', 'Frozen Carbon Monoxide', 'أول أكسيد كربون متجمد', 'snow', 0.4, 0, 'volatile ice', 0, -250, -205,
  lambda: icy(1510, [(0, C(196, 206, 214)), (0.5, C(222, 230, 236)), (1, C(244, 248, 252))], cracks=14, bubbles=0.03, gloss=0.3))
b('comet_organic_material', 'small_bodies', 'Organic Comet Material', 'مادة مذنب عضوية', 'mud', 0.5, 0, 'organic deposit', 1, -220, -70,
  lambda: sulfur_flow(1511, [(0, C(50, 32, 22)), (0.5, C(76, 50, 34)), (1, C(104, 72, 50))], dark=C(24, 16, 12)))
b('comet_rocky_core', 'small_bodies', 'Rocky Comet Core', 'لب مذنب صخري', 'rock', 1.6, 0, 'ice-rock mix', 1, -220, -70,
  lambda: frosted(rocky(1512, [(0, C(40, 38, 36)), (0.5, C(62, 58, 54)), (1, C(86, 82, 76))], cells=10, crack=0.7), 15121, 0.25, (0.85, 0.88, 0.92), 0.6))
# Gas and ice giants' clouds (for the atmospheres; you pass through them)
b('jupiter_ammonia_cloud', 'atmospheres', 'Jupiter Ammonia Cloud Material', 'مادة سحب الأمونيا في المشتري', 'gas', 0.2, 0, 'cloud', 0, -145, -100,
  lambda: cloud_rgba(1601, [(0, C(206, 196, 180)), (0.5, C(230, 222, 208)), (1, C(250, 246, 238))], bands=3, density=0.7))
b('jupiter_nh4sh_cloud', 'atmospheres', 'Jupiter Ammonium Hydrosulfide Cloud Material', 'مادة سحب هيدروكبريتيد الأمونيوم في المشتري', 'gas', 0.2, 0,
  'cloud', 0, -70, -20, lambda: cloud_rgba(1602, [(0, C(150, 90, 50)), (0.5, C(190, 130, 80)), (1, C(222, 176, 126))], bands=4, density=0.8, storms=1))
b('jupiter_water_cloud', 'atmospheres', 'Jupiter Water Cloud Material', 'مادة سحب الماء في المشتري', 'gas', 0.2, 0, 'cloud', 0, -20, 30,
  lambda: cloud_rgba(1603, [(0, C(110, 120, 140)), (0.5, C(160, 170, 186)), (1, C(214, 220, 232))], bands=2, density=0.85, swirl=0.7))
b('saturn_ammonia_cloud', 'atmospheres', 'Saturn Ammonia Cloud Material', 'مادة سحب الأمونيا في زحل', 'gas', 0.2, 0, 'cloud', 0, -180, -130,
  lambda: cloud_rgba(1604, [(0, C(200, 176, 128)), (0.5, C(226, 206, 160)), (1, C(244, 232, 200))], bands=5, density=0.65, swirl=0.2))
b('saturn_haze', 'atmospheres', 'Saturn Atmospheric Haze', 'ضباب زحل الجوي', 'gas', 0.1, 0, 'haze', 0, -190, -150,
  lambda: cloud_rgba(1605, [(0, C(214, 196, 150)), (0.5, C(232, 218, 182)), (1, C(246, 238, 214))], bands=1, density=0.35, swirl=0.1))
b('uranus_methane_cloud', 'atmospheres', 'Uranus Methane Cloud Material', 'مادة سحب الميثان في أورانوس', 'gas', 0.2, 0, 'cloud', 0, -224, -200,
  lambda: cloud_rgba(1606, [(0, C(130, 196, 206)), (0.5, C(170, 220, 228)), (1, C(214, 242, 246))], bands=2, density=0.6, swirl=0.15))
b('neptune_methane_cloud', 'atmospheres', 'Neptune Methane Cloud Material', 'مادة سحب الميثان في نبتون', 'gas', 0.2, 0, 'cloud', 0, -218, -200,
  lambda: cloud_rgba(1607, [(0, C(50, 80, 170)), (0.5, C(90, 130, 210)), (1, C(200, 220, 250))], bands=3, density=0.7, storms=1))
b('deep_atmosphere_cloud', 'atmospheres', 'Deep Atmospheric Cloud Material', 'مادة السحب الجوية العميقة', 'gas', 0.3, 0, 'cloud', 1, 100, 1000,
  lambda: cloud_rgba(1608, [(0, C(70, 40, 30)), (0.5, C(120, 70, 46)), (1, C(170, 110, 70))], bands=2, density=0.95, swirl=0.6))
# Exotic worlds
b('exo_super_earth_rock', 'exotic', 'Super-Earth Dense Rock', 'صخر الأرض الفائقة الكثيف', 'rock', 4.0, 0, 'high-pressure rock', 3, -50, 200,
  lambda: rocky(1701, [(0, C(50, 48, 54)), (0.5, C(76, 74, 82)), (1, C(104, 100, 110))], cells=20, crack=0.9, strength=1.4,
                flecks=[(C(130, 160, 120), 0.03)]))
b('exo_iron_core', 'exotic', 'Iron Planet Core Material', 'مادة لب الكوكب الحديدي', 'metal', 6.0, 0, 'metallic', 3, 200, 1200,
  lambda: widmanstatten(1702, [(0, C(56, 50, 48)), (0.6, C(104, 92, 86)), (1, C(150, 134, 122))], contrast=0.5))
b('exo_graphite', 'exotic', 'Carbon Planet Graphite', 'غرافيت الكوكب الكربوني', 'rock', 1.0, 0, 'carbon', 2, -50, 500, lambda: graphite(1703))
b('exo_diamond_carbon', 'exotic', 'Diamond-Rich Carbon Material', 'مادة كربونية غنية بالألماس', 'crystal', 8.0, 4, 'carbon', 3, -50, 500,
  lambda: np.clip(graphite(1704) * 0.8 + crystal(17041, [(0, C(160, 190, 220)), (1, C(240, 250, 255))], cells=40)
                  * (blobs(14, 1.5, 3.5, 17042) > 0.05)[..., None], 0, 1))
b('exo_lava_crust', 'exotic', 'Lava Planet Crust', 'قشرة كوكب الحمم', 'rock', 1.8, 9, 'igneous rock', 3, 1000, 2500,
  lambda: lava(1705, [(0, C(26, 18, 16)), (1, C(60, 40, 32))], [(0, C(170, 30, 4)), (0.5, C(255, 120, 20)), (1, C(255, 230, 150))], cells=14, seam=1.8))
b('exo_volcanic_glass', 'exotic', 'Obsidian-Like Volcanic Glass', 'زجاج بركاني شبيه بالسبج', 'glass', 4.0, 0, 'glass', 2, -50, 1000,
  lambda: glassy(1706, [(0, C(8, 6, 12)), (0.6, C(26, 20, 34)), (1, C(60, 50, 76))]))
b('exo_high_pressure_ice', 'exotic', 'High-Pressure Ice', 'جليد الضغط العالي', 'ice', 3.0, 0, 'exotic ice', 3, -100, 300,
  lambda: crystal(1707, [(0, C(70, 110, 170)), (0.5, C(110, 156, 210)), (1, C(170, 206, 240))], cells=14, glow=0.05))
b('exo_supercritical_fluid', 'exotic', 'Supercritical Fluid Visualization Material', 'مادة توضيح المائع فوق الحرج', 'gas', 0.3, 3,
  'supercritical fluid', 2, 374, 2000, lambda: supercritical(1708))
b('exo_mineral_deposits', 'exotic', 'Exoplanet Mineral Deposits', 'رواسب معدنية من كواكب خارجية', 'crystal', 3.0, 2, 'mineral deposit', 3, -100, 300,
  lambda: np.where((blobs(12, 2, 4.5, 17091) > 0.05)[..., None],
                   crystal(17092, [(0, C(30, 120, 110)), (0.5, C(60, 180, 160)), (1, C(160, 240, 220))], cells=24, glow=0.2),
                   rocky(1709, [(0, C(60, 54, 60)), (0.5, C(90, 82, 90)), (1, C(120, 112, 120))], cells=12, crack=0.5)))
b('exo_crystalline_rock', 'exotic', 'Ancient Extraterrestrial Crystalline Rock', 'صخر بلوري قديم من خارج الأرض', 'crystal', 4.0, 5, 'crystalline', 3,
  -150, 150, lambda: crystal(1710, [(0, C(70, 40, 110)), (0.4, C(120, 80, 170)), (0.8, C(180, 150, 230)), (1, C(230, 220, 255))], cells=12, glow=0.35))

assert len(B) == 150, len(B)
assert len({x['id'] for x in B}) == 150

GEO_TYPES = sorted({x['geo'] for x in B})
TOOL = {'dust': 'shovel', 'snow': 'shovel', 'mud': 'shovel', 'plant': 'shovel', 'rock': 'pickaxe', 'ice': 'pickaxe', 'crystal': 'pickaxe',
        'metal': 'pickaxe', 'glass': 'pickaxe', 'cloud': None, 'gas': None}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def main():
    for x in B:
        img = np.clip(x['fn'](), 0, 1)
        folder = os.path.join(TEX, x['folder'])
        os.makedirs(folder, exist_ok=True)
        mode = 'RGBA' if img.shape[-1] == 4 else 'RGB'
        Image.fromarray((img * 255).astype(np.uint8), mode).save(os.path.join(folder, x['id'] + '.png'))
        tex = 'realweather:block/planets/%s/%s' % (x['folder'], x['id'])
        model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex}}
        if x['mat'] == 'gas':
            model['render_type'] = 'minecraft:translucent'
        write_json(os.path.join(ASSETS, 'models', 'block', x['id'] + '.json'), model)
        write_json(os.path.join(ASSETS, 'models', 'item', x['id'] + '.json'), {'parent': 'realweather:block/' + x['id']})
        write_json(os.path.join(ASSETS, 'blockstates', x['id'] + '.json'), {'variants': {'': {'model': 'realweather:block/' + x['id']}}})
        write_json(os.path.join(DATA, 'realweather', 'loot_table', 'blocks', x['id'] + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'realweather:' + x['id']}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
        print('block', x['id'])
    # Tool tags.
    for tool in ('pickaxe', 'shovel'):
        path = os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', tool + '.json')
        tag = json.load(open(path))
        vals = [v for v in tag['values'] if not any(v == 'realweather:' + x['id'] for x in B)]
        vals += ['realweather:' + x['id'] for x in B if TOOL[x['mat']] == tool]
        tag['values'] = vals
        write_json(path, tag)
    # Names.
    geo_en = {g: g.capitalize() for g in GEO_TYPES}
    for f, key in (('en_us.json', 'en'), ('ar_sa.json', 'ar'), ('ar_iq.json', 'ar')):
        path = os.path.join(ASSETS, 'lang', f)
        d = json.load(open(path), object_pairs_hook=__import__('collections').OrderedDict)
        for x in B:
            d['block.realweather.' + x['id']] = x[key]
        write_json(path, d)
    # The Java table.
    lines = []
    for i, x in enumerate(B):
        lines.append('            new Geo("%s", Tiles.M_%s, %.1fF, %d, %d, %d, %d, %d)' % (
            x['id'], x['mat'].upper(), x['hard'], x['light'], GEO_TYPES.index(x['geo']), x['dens'], x['tmin'], x['tmax']))
    consts = []
    for i, x in enumerate(B):
        consts.append('%s = BASE + %d' % (x['id'].upper(), i))
    java = '''package com.realweather.space;

/**
 * The 150 planetary surface materials (generated by tools/gen_geology.py; do not edit by hand): each a block with its
 * own texture (in a folder per world), material, hardness, light, geological type, density and the range of surface
 * temperatures where it is found. They follow the first tiles in {@link Tiles#ALL}.
 */
public final class Geology {
    /** One material: name, how it sounds and breaks, hardness, light, geological type (index into TYPES), density (0..3), temperatures. */
    public record Geo(String name, int material, float hardness, int light, int type, int density, int tMin, int tMax) {}

    public static final String[] TYPES = {%s};

    public static final int BASE = 48;
    public static final int %s;

    public static final Geo[] ALL = {
%s
    };

    private Geology() {}
}
''' % (', '.join('"%s"' % g.replace(' ', '_') for g in GEO_TYPES), ',\n            '.join(consts), ',\n'.join(lines))
    with open(os.path.join(ROOT, 'java', 'com', 'realweather', 'space', 'Geology.java'), 'w') as f:
        f.write(java)
    print('types', GEO_TYPES)


if __name__ == '__main__':
    main()
