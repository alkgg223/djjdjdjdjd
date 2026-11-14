"""Sky textures: a round sun and round moon phases (replacing the square vanilla ones), the eclipse corona and the
'diamond ring' flare, star points, the eclipse glasses, and tileable cirrus, cirrocumulus and veil cloud sheets."""
import os

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets')
ENV_MC = os.path.join(RES, 'minecraft', 'textures', 'environment')
ENV = os.path.join(RES, 'realweather', 'textures', 'environment')
ITEM = os.path.join(RES, 'realweather', 'textures', 'item')


def save(arr, path):
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8), 'RGBA').save(path)


def value_noise(rng, size, cells):
    g = rng.random((cells + 1, cells + 1))
    g[-1, :] = g[0, :]
    g[:, -1] = g[:, 0]
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC)
    return np.asarray(img, dtype=float) / 255


def fbm(rng, size, base, octaves, gain=0.5):
    s = np.zeros((size, size))
    a, tot = 1.0, 0.0
    c = base
    for _ in range(octaves):
        s += a * value_noise(rng, size, c)
        tot += a
        a *= gain
        c *= 2
    return s / tot


def sun():
    """Vanilla draws the sun additively on a quad: the disc fills the middle half, a glow fades out to the edge."""
    n = 128
    y, x = (np.mgrid[0:n, 0:n] + 0.5) / n * 2 - 1
    r = np.sqrt(x * x + y * y)
    disc = np.clip((0.5 - r) / 0.012 + 0.5, 0, 1)
    limb = np.sqrt(np.clip(1 - (r / 0.5) ** 2, 0, 1))
    glow = np.exp(-np.clip(r - 0.5, 0, None) / 0.13) * 0.55 + np.exp(-np.clip(r - 0.5, 0, None) / 0.035) * 0.35
    glow *= np.clip((1 - r) / 0.25, 0, 1)
    a = np.maximum(disc * (0.75 + 0.25 * limb), glow * (1 - disc))
    img = np.zeros((n, n, 4))
    img[..., 0] = 1.0
    img[..., 1] = 0.96 * disc + 0.9 * (1 - disc)
    img[..., 2] = 0.86 * disc + 0.72 * (1 - disc)
    img[..., 3] = a
    save(img, os.path.join(ENV_MC, 'sun.png'))
    # The plain disc (no glow) for the eclipse and the glasses.
    d = np.zeros((n, n, 4))
    d[..., :3] = 1.0
    d[..., 3] = np.clip((0.98 - r) / 0.02, 0, 1)
    save(d, os.path.join(ENV, 'disc.png'))


def moon():
    """Eight phases (vanilla order: full, waning gibbous, last quarter, waning crescent, new, waxing crescent, first
    quarter, waxing gibbous), each a sphere lit from the side with grey maria and craters; the dark side is clear."""
    rng = np.random.default_rng(91)
    c = 128
    y, x = (np.mgrid[0:c, 0:c] + 0.5) / c * 2 - 1
    R = 0.5
    r = np.sqrt(x * x + y * y)
    inside = np.clip((R - r) / 0.012 + 0.5, 0, 1)
    nz = np.sqrt(np.clip(1 - (r / R) ** 2, 0, 1))
    nx = x / R
    maria = fbm(rng, c, 3, 4)
    maria = np.clip((maria - 0.45) * 2.2, 0, 1)
    albedo = 0.82 - 0.32 * maria
    # Craters: small bright-rimmed dark spots.
    for _ in range(26):
        cx, cy = rng.uniform(-0.42, 0.42, 2)
        cr = rng.uniform(0.012, 0.05)
        d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
        albedo -= 0.12 * np.exp(-(d / cr) ** 2)
        albedo += 0.07 * np.exp(-((d - cr) / (cr * 0.35)) ** 2)
    albedo = np.clip(albedo, 0.25, 1)
    sheet = np.zeros((c * 2, c * 4, 4))
    phases = [0.0, 0.25, 0.5, 0.75, 1.0, -0.75, -0.5, -0.25]
    for k, ph in enumerate(phases):
        # Sun direction around the moon: full (from the viewer) .. new (from behind).
        ang = ph * np.pi
        sx, sz = np.sin(ang), np.cos(ang)
        lit = nx * sx + nz * sz
        light = np.clip(lit * 3.0 + 0.5, 0, 1) * np.clip(lit + 0.15, 0, 1) ** 0.4
        val = albedo * (0.06 + 0.94 * light)
        img = np.zeros((c, c, 4))
        img[..., 0] = val * 0.98
        img[..., 1] = val * 0.98
        img[..., 2] = val
        img[..., 3] = inside * np.clip(val * 1.6, 0, 1)
        row, col = divmod(k, 4)
        sheet[row * c:(row + 1) * c, col * c:(col + 1) * c] = img
    save(sheet, os.path.join(ENV_MC, 'moon_phases.png'))


def corona():
    """The corona round the eclipsed sun: a bright inner ring and long soft streamers (additive, alpha = brightness)."""
    rng = np.random.default_rng(92)
    n = 256
    y, x = (np.mgrid[0:n, 0:n] + 0.5) / n * 2 - 1
    r = np.sqrt(x * x + y * y)
    a = np.arctan2(y, x)
    R = 0.2
    rr = np.maximum(r - R, 0)
    streak = np.zeros_like(r)
    for k in range(1, 9):
        streak += np.cos(k * a + rng.uniform(0, 6.3)) * rng.uniform(0.2, 1.0) / k
    streak = 0.6 + 0.4 * np.tanh(streak * 1.4)
    # Two long streamers roughly along the 'equator'.
    eq = np.exp(-((np.sin(a - 0.3)) ** 2) / 0.05)
    fall = np.exp(-rr / (0.07 + 0.22 * streak + 0.25 * eq))
    inner = np.exp(-rr / 0.025)
    v = (fall * 0.7 + inner * 0.6) * (r > R * 0.97)
    v *= np.clip((1 - r) / 0.2, 0, 1)
    img = np.zeros((n, n, 4))
    img[..., 0] = 0.92
    img[..., 1] = 0.95
    img[..., 2] = 1.0
    img[..., 3] = np.clip(v, 0, 1)
    save(img, os.path.join(ENV, 'corona.png'))


def flare():
    """A bright point with a four-pointed star of rays (the diamond ring, Baily's beads)."""
    n = 256
    y, x = (np.mgrid[0:n, 0:n] + 0.5) / n * 2 - 1
    r = np.sqrt(x * x + y * y)
    core = np.exp(-(r / 0.05) ** 2) + np.exp(-r / 0.18) * 0.45
    rays = np.exp(-np.abs(x) / 0.012) * np.exp(-np.abs(y) / 0.6) + np.exp(-np.abs(y) / 0.012) * np.exp(-np.abs(x) / 0.6)
    d1, d2 = (x + y) / np.sqrt(2), (x - y) / np.sqrt(2)
    rays2 = (np.exp(-np.abs(d1) / 0.008) * np.exp(-np.abs(d2) / 0.35) + np.exp(-np.abs(d2) / 0.008) * np.exp(-np.abs(d1) / 0.35)) * 0.4
    v = np.clip(core + rays * 0.8 + rays2, 0, 1) * np.clip((1 - r) / 0.15, 0, 1)
    img = np.zeros((n, n, 4))
    img[..., :3] = 1.0
    img[..., 2] = 0.97
    img[..., 3] = v
    save(img, os.path.join(ENV, 'flare.png'))


def glasses():
    """Cardboard eclipse glasses: a pale frame with two black film lenses."""
    img = np.zeros((16, 16, 4))
    frame = (0.94, 0.86, 0.42, 1)
    lens = (0.04, 0.04, 0.05, 1)
    for yy in range(5, 11):
        for xx in range(1, 15):
            img[yy, xx] = frame
    for yy in range(6, 10):
        for xx in list(range(2, 7)) + list(range(9, 14)):
            img[yy, xx] = lens
    img[7, 7] = frame
    img[7, 8] = frame
    for xx in (0, 15):
        img[6, xx] = frame
    img[6:8, 3] = (0.12, 0.12, 0.14, 1)
    img[6:8, 10] = (0.12, 0.12, 0.14, 1)
    save(img, os.path.join(ITEM, 'eclipse_glasses.png'))


def high_clouds():
    """Tileable sheets (alpha = cloud) for the high and middle cloud layers."""
    rng = np.random.default_rng(93)
    n = 512
    # Cirrus: thin wispy strands ("mares' tails"), combed by the wind, with hooked ends.
    from PIL import ImageDraw
    canvas = Image.new('L', (n, n), 0)
    dr = ImageDraw.Draw(canvas)
    patches = fbm(rng, n, 3, 4)
    for _ in range(420):
        x0, y0 = rng.uniform(0, n, 2)
        if patches[int(y0) % n, int(x0) % n] < 0.45:
            continue
        length = rng.uniform(40, 200)
        ang = rng.normal(0, 0.12)
        curve = rng.normal(0, 0.008)
        hook = rng.uniform(-0.05, 0.05)
        steps = int(length / 2)
        pts = []
        x, y = x0, y0
        for k in range(steps):
            t = k / steps
            ang += curve + (hook if t > 0.8 else 0)
            x += np.cos(ang) * 2
            y += np.sin(ang) * 2
            pts.append((x, y, t))
        w = rng.uniform(0.8, 2.6)
        val = rng.uniform(60, 170)
        for (xa, ya, ta), (xb, yb, tb) in zip(pts, pts[1:]):
            fade = np.sin(np.pi * ta) ** 0.6
            for ox in (-n, 0, n):
                for oy in (-n, 0, n):
                    dr.line([(xa + ox, ya + oy), (xb + ox, yb + oy)], fill=int(val * fade), width=max(1, int(round(w))))
    fib = canvas.filter(ImageFilter.GaussianBlur(1.6))
    ci = np.asarray(fib, dtype=float) / 255
    ci = np.clip(ci * 1.7, 0, 1) * np.clip((patches - 0.35) * 3, 0, 1)
    img = np.zeros((n, n, 4))
    img[..., :3] = 1.0
    img[..., 3] = ci * 0.9
    save(img, os.path.join(ENV, 'cirrus.png'))
    yy, xx = np.mgrid[0:n, 0:n] / n
    warp = fbm(rng, n, 4, 3)
    streak = 0.5 + 0.5 * np.sin((yy + warp * 0.2) * 2 * np.pi * 30)
    # Cirrocumulus / altocumulus: rows of small rounded cloudlets.
    cells = value_noise(rng, n, 96) * 0.65 + value_noise(rng, n, 48) * 0.35
    ripple = 0.5 + 0.5 * np.sin((xx * 22 + warp * 2.5) * 2 * np.pi)
    patch = np.clip((fbm(rng, n, 4, 4) - 0.38) * 2.6, 0, 1)
    cc = np.clip((cells * (0.55 + 0.45 * ripple) - 0.42) * 5.0, 0, 1) * patch
    img = np.zeros((n, n, 4))
    img[..., :3] = 1.0
    img[..., 3] = np.clip(cc, 0, 1) * 0.9
    save(img, os.path.join(ENV, 'cirrocumulus.png'))
    cells = value_noise(rng, n, 24) * 0.6 + value_noise(rng, n, 12) * 0.4
    patch = np.clip((fbm(rng, n, 3, 4) - 0.3) * 2.2, 0, 1)
    ac = np.clip((cells - 0.45) * 3.2, 0, 1) * patch
    img = np.zeros((n, n, 4))
    img[..., :3] = 1.0
    img[..., 3] = np.clip(ac, 0, 1) * 0.92
    save(img, os.path.join(ENV, 'altocumulus.png'))
    # Veil (cirrostratus / altostratus): a smooth sheet with faint fibres.
    v = fbm(rng, n, 2, 5) * 0.7 + streak * 0.3
    img = np.zeros((n, n, 4))
    img[..., :3] = 1.0
    img[..., 3] = np.clip(0.35 + (v - 0.5) * 0.9, 0, 1)
    save(img, os.path.join(ENV, 'veil.png'))


if __name__ == '__main__':
    sun()
    moon()
    corona()
    flare()
    glasses()
    high_clouds()
    print('ok')
