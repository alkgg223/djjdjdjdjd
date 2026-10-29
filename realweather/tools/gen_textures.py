"""Procedural textures for Real Weather (rain, snow, sand, puddles, particles, water)."""
import json, os
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets')
ENV = os.path.join(ROOT, 'realweather', 'textures', 'environment')
PART = os.path.join(ROOT, 'realweather', 'textures', 'particle')
MISC = os.path.join(ROOT, 'realweather', 'textures', 'misc')
BLOCK = os.path.join(ROOT, 'minecraft', 'textures', 'block')
WATER_ALPHA = 179


def save(path, rgba, blur=False, mcmeta=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    a = np.clip(rgba, 0, 255).astype(np.uint8)
    Image.fromarray(a, 'RGBA').save(path, optimize=True)
    meta = mcmeta or ({'texture': {'blur': True}} if blur else None)
    if meta:
        with open(path + '.mcmeta', 'w') as f:
            json.dump(meta, f, indent=2)


def rgba(rgb, alpha):
    h, w = alpha.shape
    out = np.zeros((h, w, 4))
    out[..., 0] = rgb[0]
    out[..., 1] = rgb[1]
    out[..., 2] = rgb[2]
    out[..., 3] = np.clip(alpha, 0, 1) * 255
    return out


def periodic_noise(rng, shape, scale, aniso=(1.0, 1.0, 1.0), power=2.0):
    """Tileable noise via spectral shaping; periodic along every axis."""
    spec = np.fft.fftn(rng.standard_normal(shape))
    freqs = np.meshgrid(*[np.fft.fftfreq(n) * n for n in shape], indexing='ij')
    k2 = sum((f * a) ** 2 for f, a in zip(freqs, aniso[-len(shape):]))
    k = np.sqrt(k2)
    filt = np.exp(-(k / scale) ** power)
    filt.flat[0] = 0
    x = np.real(np.fft.ifftn(spec * filt))
    x -= x.mean()
    return x / (x.std() + 1e-9)


def streak_canvas(rng, w, h, n, wpx, lmin, lmax, amin, amax):
    alpha = np.zeros((h, w))
    ys = np.arange(h)
    xs = np.arange(w)
    for _ in range(n):
        cx = rng.uniform(0, w)
        y0 = rng.uniform(0, h)
        ln = rng.uniform(lmin, lmax)
        a = rng.uniform(amin, amax)
        sig = wpx * rng.uniform(0.7, 1.3)
        dx = (xs - cx + w / 2) % w - w / 2
        col = np.exp(-0.5 * (dx / sig) ** 2)
        # Along the streak: head (bottom, leading) bright, tail fades upwards.
        dy = (ys - y0) % h
        t = dy / ln
        prof = np.where(t <= 1, (t ** 1.6) * (1 - np.exp(-(1 - t) * 25)), 0)
        alpha += a * prof[:, None] * col[None, :]
    return np.clip(alpha, 0, 1)


def disc(size, cx, cy, r, soft):
    yy, xx = np.mgrid[0:size[0], 0:size[1]]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    return np.clip((r - d) / max(soft, 1e-3) + 0.5, 0, 1)


def wrap_disc(h, w, cx, cy, r, soft):
    a = np.zeros((h, w))
    for ox in (-w, 0, w):
        for oy in (-h, 0, h):
            a = np.maximum(a, disc((h, w), cx + ox, cy + oy, r, soft))
    return a


def environment(rng):
    # Rain streaks.
    a = streak_canvas(rng, 128, 512, 64, 0.6, 40, 130, 0.4, 0.9)
    save(os.path.join(ENV, 'rain_streaks.png'), rgba((225, 232, 245), a), blur=True)
    a = streak_canvas(rng, 128, 512, 150, 0.45, 25, 80, 0.15, 0.45)
    save(os.path.join(ENV, 'rain_dense.png'), rgba((215, 222, 235), a), blur=True)
    n = periodic_noise(rng, (256, 128), 18, aniso=(7.0, 1.0))
    n2 = periodic_noise(rng, (256, 128), 40, aniso=(5.0, 1.0))
    a = np.clip(0.28 + 0.2 * n + 0.1 * n2, 0, 0.6)
    save(os.path.join(ENV, 'rain_mist.png'), rgba((200, 206, 214), a), blur=True)

    # Snow: soft flakes of many sizes, plus out-of-focus big ones.
    h, w = 512, 256
    a = np.zeros((h, w))
    for _ in range(70):
        r = rng.uniform(1.6, 4.2)
        a = np.maximum(a, wrap_disc(h, w, rng.uniform(0, w), rng.uniform(0, h), r, 1.6) * rng.uniform(0.7, 1.0))
    for _ in range(8):
        r = rng.uniform(5, 8)
        a = np.maximum(a, wrap_disc(h, w, rng.uniform(0, w), rng.uniform(0, h), r, 5) * rng.uniform(0.25, 0.45))
    save(os.path.join(ENV, 'snow_flakes.png'), rgba((255, 255, 255), a), blur=True)
    h, w = 256, 128
    a = np.zeros((h, w))
    for _ in range(120):
        a = np.maximum(a, wrap_disc(h, w, rng.uniform(0, w), rng.uniform(0, h), rng.uniform(0.8, 1.9), 1.0) * rng.uniform(0.5, 0.95))
    save(os.path.join(ENV, 'snow_fine.png'), rgba((250, 252, 255), a), blur=True)
    # Blizzard: snow driven sideways, smeared into short horizontal streaks.
    h, w = 256, 256
    a = np.zeros((h, w))
    xs = np.arange(w)
    ys = np.arange(h)
    for _ in range(170):
        cy = rng.uniform(0, h)
        x0 = rng.uniform(0, w)
        ln = rng.uniform(8, 34)
        amp = rng.uniform(0.35, 0.95)
        sig = rng.uniform(0.6, 1.3)
        dy = (ys - cy + h / 2) % h - h / 2
        row = np.exp(-0.5 * (dy / sig) ** 2)
        dx = (xs - x0) % w
        t = dx / ln
        prof = np.where(t <= 1, np.sin(np.pi * np.clip(t, 0, 1)) ** 0.7, 0)
        a += amp * row[:, None] * prof[None, :]
    save(os.path.join(ENV, 'snow_streaks.png'), rgba((255, 255, 255), np.clip(a, 0, 1)), blur=True)

    # Sandstorm dust: billowing, horizontally stretched clouds.
    n1 = periodic_noise(rng, (256, 256), 10, aniso=(1.0, 2.4))
    n2 = periodic_noise(rng, (256, 256), 30, aniso=(1.0, 2.0))
    n3 = periodic_noise(rng, (256, 256), 80, aniso=(1.0, 1.7))
    d = 0.55 * n1 + 0.3 * n2 + 0.15 * n3
    a = np.clip(0.45 + 0.28 * d, 0, 0.9)
    shade = np.clip(0.9 + 0.08 * n2, 0.75, 1.0)
    img = np.zeros((256, 256, 4))
    img[..., 0] = 214 * shade
    img[..., 1] = 172 * shade
    img[..., 2] = 118 * shade
    img[..., 3] = a * 255
    save(os.path.join(ENV, 'sand_sheet.png'), img, blur=True)
    h, w = 256, 256
    a = np.zeros((h, w))
    for _ in range(420):
        cy = rng.uniform(0, h)
        x0 = rng.uniform(0, w)
        ln = rng.uniform(4, 16)
        amp = rng.uniform(0.3, 0.9)
        dy = (ys - cy + h / 2) % h - h / 2
        row = np.exp(-0.5 * (dy / 0.6) ** 2)
        dx = (xs - x0) % w
        t = dx / ln
        prof = np.where(t <= 1, 1 - t, 0)
        a += amp * row[:, None] * prof[None, :]
    save(os.path.join(ENV, 'sand_grains.png'), rgba((205, 165, 112), np.clip(a, 0, 1)), blur=True)

    # Puddles: one organic noise field cut into three growth stages (core, middle, outer ring).
    p = periodic_noise(rng, (256, 256), 7) * 0.75 + periodic_noise(rng, (256, 256), 20) * 0.25
    p = (p - p.min()) / (p.max() - p.min())
    detail = periodic_noise(rng, (256, 256), 60)
    # Stages stack: where all three overlap (the middle of a big puddle) the water looks deepest.
    th = [0.8, 0.72, 0.65]
    for i in range(3):
        alpha = np.clip((p - th[i]) / 0.03, 0, 1) * 0.42
        img = np.zeros((256, 256, 4))
        lum = np.clip(150 + 12 * detail, 120, 175)
        img[..., 0] = lum * 0.95
        img[..., 1] = lum * 0.98
        img[..., 2] = lum * 1.05
        img[..., 3] = alpha * 255
        save(os.path.join(ENV, 'puddle_%d.png' % (i + 1)), img, blur=True)


def particles(rng):
    # Crown splash animation.
    for f in range(4):
        s = 16
        a = np.zeros((s, s))
        if f == 0:
            a = np.maximum(a, disc((s, s), 7.5, 13.5, 2.2, 1.0))
        else:
            spread = [0, 2.5, 4.5, 6.0][f]
            rise = [0, 4.0, 6.0, 5.0][f]
            for k in range(-2, 3):
                x = 7.5 + k * spread * 0.55
                y = 14 - rise * (1 - abs(k) * 0.25)
                a = np.maximum(a, disc((s, s), x, y, 1.3 if f < 3 else 0.9, 0.8))
            a = np.maximum(a, disc((s, s), 7.5, 14.5, 3.0 - f * 0.5, 1.2) * 0.6)
            a *= [1, 1, 0.9, 0.6][f]
        save(os.path.join(PART, 'splash_%d.png' % f), rgba((235, 242, 255), a))
    s = 32
    yy, xx = np.mgrid[0:s, 0:s]
    d = np.sqrt((xx - 15.5) ** 2 + (yy - 15.5) ** 2)
    a = np.exp(-0.5 * ((d - 13.0) / 1.1) ** 2) + 0.45 * np.exp(-0.5 * ((d - 8.5) / 1.0) ** 2)
    save(os.path.join(PART, 'ripple.png'), rgba((240, 246, 255), np.clip(a, 0, 1)))
    # Raindrop streak: round bright head at the top (leading end), fading tail.
    h, w = 32, 8
    yy, xx = np.mgrid[0:h, 0:w]
    t = yy / (h - 1)
    a = np.exp(-0.5 * ((xx - 3.5) / (1.1 * (1 - 0.5 * t))) ** 2) * (1 - t) ** 1.3
    a = np.maximum(a, disc((h, w), 3.5, 2.0, 1.8, 1.0))
    save(os.path.join(PART, 'rain_drop.png'), rgba((230, 238, 255), a))
    for i in range(3):
        s = 16
        a = disc((s, s), 7.5, 7.5, [3.0, 4.5, 6.0][i], [1.5, 2.5, 4.0][i]) * [1.0, 0.85, 0.6][i]
        if i == 0:
            # A tiny six-armed crystal.
            for k in range(3):
                ang = k * np.pi / 3
                yy, xx = np.mgrid[0:s, 0:s]
                dist = np.abs((xx - 7.5) * np.sin(ang) - (yy - 7.5) * np.cos(ang))
                along = np.abs((xx - 7.5) * np.cos(ang) + (yy - 7.5) * np.sin(ang))
                arm = np.clip(1.2 - dist, 0, 1) * (along < 6.5)
                a = np.maximum(a, arm * 0.9)
        save(os.path.join(PART, 'flake_%d.png' % i), rgba((255, 255, 255), a))
    h, w = 16, 8
    yy, xx = np.mgrid[0:h, 0:w]
    t = yy / (h - 1)
    a = np.exp(-0.5 * ((xx - 3.5) / 1.4) ** 2) * np.sin(np.pi * np.clip(t * 0.9 + 0.05, 0, 1)) ** 0.6
    save(os.path.join(PART, 'blizzard_flake.png'), rgba((255, 255, 255), a))
    a = np.exp(-0.5 * ((xx - 3.5) / 1.0) ** 2) * (1 - t) ** 1.2
    save(os.path.join(PART, 'sand_grain.png'), rgba((255, 255, 255), a))
    for i in range(3):
        s = 32
        n = periodic_noise(rng, (s, s), 5)
        yy, xx = np.mgrid[0:s, 0:s]
        d = np.sqrt((xx - 15.5) ** 2 + (yy - 15.5) ** 2) / 15.5
        a = np.clip((1 - d ** 1.6) * (0.75 + 0.3 * n), 0, 1)
        save(os.path.join(PART, 'dust_%d.png' % i), rgba((255, 255, 255), a))


def overlay(rng):
    h, w = 256, 256
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt(((xx - 127.5) / 127.5) ** 2 + ((yy - 127.5) / 127.5) ** 2)
    n = periodic_noise(rng, (h, w), 25)
    grit = (rng.random((h, w)) < 0.02) * rng.uniform(0.2, 0.6, (h, w))
    a = np.clip(0.18 + 0.55 * np.clip(d - 0.45, 0, 1) ** 1.5 * 2.2 + 0.06 * n + grit * 0.5, 0, 0.92)
    img = np.zeros((h, w, 4))
    img[..., 0] = 196 + 10 * n
    img[..., 1] = 150 + 8 * n
    img[..., 2] = 96 + 6 * n
    img[..., 3] = a * 255
    save(os.path.join(MISC, 'sand_overlay.png'), img, blur=True)


def water(rng):
    """Realistic water: soft moving ripples with faint caustic lines, grey so the biome colour tints it."""
    frames, s = 32, 64
    big = periodic_noise(rng, (frames, s, s), 4, aniso=(1.4, 1.0, 1.0))
    mid = periodic_noise(rng, (frames, s, s), 9, aniso=(1.6, 1.0, 1.0))
    fine = periodic_noise(rng, (frames, s, s), 20, aniso=(2.0, 1.0, 1.0))
    caustic = 1 - np.abs(np.tanh(mid * 1.2))
    caustic = caustic ** 6
    lum = 0.83 + 0.035 * big + 0.025 * fine + 0.07 * caustic
    lum = np.clip(lum, 0.62, 1.0)
    img = np.zeros((frames * s, s, 4))
    for f in range(frames):
        v = lum[f] * 255
        img[f * s:(f + 1) * s, :, 0] = v
        img[f * s:(f + 1) * s, :, 1] = v
        img[f * s:(f + 1) * s, :, 2] = v
    img[..., 3] = WATER_ALPHA
    save(os.path.join(BLOCK, 'water_still.png'), img, mcmeta={'animation': {'frametime': 3, 'interpolate': True}})

    # Flowing water: streaks stretched along the flow, scrolling one full tile per loop.
    s = 128
    base = periodic_noise(rng, (s, s), 10, aniso=(4.0, 1.0))
    base2 = periodic_noise(rng, (s, s), 24, aniso=(3.0, 1.0))
    foam = np.clip(base2 - 1.2, 0, None)
    img = np.zeros((frames * s, s, 4))
    for f in range(frames):
        shift = int(round(f * s / frames))
        b = np.roll(base, shift, axis=0)
        b2 = np.roll(base2, shift * 2 % s, axis=0)
        fo = np.roll(foam, shift, axis=0)
        v = np.clip(0.8 + 0.07 * b + 0.05 * b2 + 0.12 * fo, 0.62, 1.0) * 255
        img[f * s:(f + 1) * s, :, 0] = v
        img[f * s:(f + 1) * s, :, 1] = v
        img[f * s:(f + 1) * s, :, 2] = v
    img[..., 3] = WATER_ALPHA
    save(os.path.join(BLOCK, 'water_flow.png'), img, mcmeta={'animation': {'frametime': 2, 'interpolate': True}})

    s = 64
    n = periodic_noise(rng, (s, s), 8)
    v = np.clip(0.82 + 0.06 * n, 0.6, 1.0) * 255
    img = np.zeros((s, s, 4))
    img[..., 0] = v
    img[..., 1] = v
    img[..., 2] = v
    img[..., 3] = WATER_ALPHA
    save(os.path.join(BLOCK, 'water_overlay.png'), img)


def main():
    rng = np.random.default_rng(2026)
    environment(rng)
    particles(rng)
    overlay(rng)
    water(rng)
    print('textures done')


if __name__ == '__main__':
    main()
