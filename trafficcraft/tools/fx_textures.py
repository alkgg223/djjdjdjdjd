"""Exhaust backfire textures: flame tongue (4 growth frames) and fireball (4 frames). Writes into the mod's textures/vehicle."""
import numpy as np
from PIL import Image
import os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/trafficcraft/textures/vehicle')
rng = np.random.default_rng(7)


def fbm(h, w, oct_=5, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp, tot = 1.0, 0.0
    for o in range(oct_):
        n = 2 ** (o + 2)
        g = r.random((n + 1, n + 1))
        ys, xs = np.linspace(0, n, h, endpoint=False), np.linspace(0, n, w, endpoint=False)
        y0, x0 = ys.astype(int), xs.astype(int)
        fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
        fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
        a = g[y0][:, x0]; b = g[y0][:, x0 + 1]; c = g[y0 + 1][:, x0]; d = g[y0 + 1][:, x0 + 1]
        out += amp * (a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy)
        tot += amp
        amp *= 0.5
    return out / tot


def ramp(t):
    """0 = hottest (blue-white) .. 1 = cool red edge."""
    stops = [(0.0, (255, 255, 255)), (0.12, (200, 225, 255)), (0.25, (255, 245, 190)), (0.45, (255, 200, 70)), (0.65, (255, 120, 20)),
             (0.85, (200, 40, 10)), (1.0, (90, 10, 5))]
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,))
    for (a, ca), (b, cb) in zip(stops[:-1], stops[1:]):
        m = (t >= a) & (t <= b)
        k = ((t - a) / (b - a))[m][:, None]
        out[m] = np.array(ca) * (1 - k) + np.array(cb) * k
    return out


def flame(W=128, H=256):
    frames = []
    for f in range(4):
        grow = [0.55, 1.0, 0.85, 0.6][f]
        x = np.linspace(-1, 1, W)[None, :]
        y = np.linspace(0, 1, H)[:, None]                 # 0 at the pipe, 1 at the tip
        n = fbm(H, W, 5, seed=11 + f)
        n2 = fbm(H, W, 3, seed=31 + f)
        wobble = (n2 - 0.5) * 0.5 * y
        width = (0.5 + 0.55 * np.sqrt(y / grow)) * np.clip(1 - (y / grow) ** 2.2, 0, 1)
        d = np.abs(x + wobble) / np.maximum(width, 1e-3)
        dens = np.clip(1 - d, 0, 1) ** 0.8 * (0.6 + 0.8 * n) * np.clip(1 - y / grow, 0, 1) ** 0.6
        dens = np.clip(dens * 1.5, 0, 1)
        heat = np.clip(1 - dens, 0, 1) * 0.7 + y / grow * 0.45 + (n - 0.5) * 0.2
        rgb = ramp(heat)
        a = np.clip(dens * 1.3, 0, 1)
        frames.append(np.dstack([rgb, a * 255]))
    return np.hstack(frames)


def fireball(S=128):
    frames = []
    for f in range(4):
        r0 = [0.55, 0.85, 1.0, 1.0][f]
        yy, xx = np.mgrid[-1:1:S * 1j, -1:1:S * 1j]
        rr = np.hypot(xx, yy)
        n = fbm(S, S, 5, seed=51 + f)
        edge = r0 * (0.75 + 0.35 * n)
        dens = np.clip(1 - rr / edge, 0, 1) ** 0.7
        heat = np.clip(rr / edge, 0, 1) * 0.8 + f * 0.12 + (n - 0.5) * 0.25
        rgb = ramp(heat)
        if f == 3:      # burning out into dark smoke
            rgb = rgb * 0.5 + 25
        a = np.clip(dens * (1.4 - f * 0.25), 0, 1)
        frames.append(np.dstack([rgb, a * 255]))
    return np.hstack(frames)


if __name__ == '__main__':
    Image.fromarray(flame().astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'flame.png'))
    Image.fromarray(fireball().astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'fireball.png'))
    print('ok')
