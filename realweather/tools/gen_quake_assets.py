"""Earthquake sounds and the crack decal (synthesised): the deep rolling rumble of the ground shaking, with the
rattle of everything loose on it; the sharp splitting of rock and soil as cracks run through the ground; and the
crash of walls and roofs coming down. The crack texture is a strip: a dark, jagged fissure with crumbled edges and
fine side cracks, tiled along each crack."""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_sounds as G  # noqa: E402

SR = G.SR
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'realweather')


def rumble(seed):
    """loop: a low roll of the whole ground, surging, with rattling and creaking riding on it"""
    rng = np.random.default_rng(seed)
    L, xf = 10 * SR, int(1.5 * SR)
    n = L + xf
    surge = G.ctrl(rng, n, 0.8, 0.35, 1.0)
    deep = G.filt(G.brown(rng, n), 'lowpass', 70, 3) * 2.2
    roll = G.filt(G.brown(rng, n), 'bandpass', (40, 220), 2) * (0.5 + 0.5 * surge)
    # Things rattling and buildings creaking: short knocks and groans at random.
    rattle = np.zeros(n)
    for _ in range(int(10 * 22)):
        i = rng.integers(0, n - SR // 4)
        m = int(rng.uniform(0.02, 0.08) * SR)
        t = np.arange(m) / SR
        rattle[i:i + m] += G.filt(rng.standard_normal(m), 'bandpass', (500, 3500), 2) * np.exp(-t * 60) * rng.uniform(0.05, 0.3)
    creak = np.zeros(n)
    for _ in range(9):
        i = rng.integers(0, n - 2 * SR)
        m = int(rng.uniform(0.5, 1.4) * SR)
        t = np.arange(m) / SR
        f = rng.uniform(90, 260) * (1 + 0.15 * np.sin(2 * np.pi * rng.uniform(1, 3) * t))
        creak[i:i + m] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / t[-1]) ** 2 * rng.uniform(0.04, 0.12)
    x = (deep + roll) * (0.6 + 0.4 * surge) + rattle * surge + creak
    return G.rms_norm(G.loopify(x, xf), -13)


def crack(seed):
    """one shot: rock and earth splitting: a sharp snap, then a tearing crackle and a low thud"""
    rng = np.random.default_rng(seed)
    n = int(2.2 * SR)
    t = np.arange(n) / SR
    snap = G.filt(G.white(rng, n), 'highpass', 900, 2) * np.exp(-t / 0.025)
    tear = np.zeros(n)
    t0 = 0.0
    while t0 < 1.0:
        i = int(t0 * SR)
        m = int(rng.uniform(0.004, 0.02) * SR)
        tt = np.arange(m) / SR
        k = G.filt(rng.standard_normal(m), 'bandpass', (700, 5000), 2) * np.exp(-tt * 200)
        end = min(n, i + m)
        tear[i:end] += k[:end - i] * rng.uniform(0.2, 1.0) * np.exp(-t0 / 0.5)
        t0 += rng.exponential(0.018)
    thud = G.filt(G.white(rng, n), 'lowpass', 140, 3) * np.exp(-t / 0.35) * np.clip(t / 0.01, 0, 1)
    x = G.peak_norm(snap, -3) + G.peak_norm(tear, -5) + G.peak_norm(thud, -4)
    return G.peak_norm(G.fade(x, 0.0, 0.6), -1)


def collapse(seed):
    """one shot: masonry and timber coming down: a heavy crash and a long tumble of rubble"""
    rng = np.random.default_rng(seed)
    n = int(4.0 * SR)
    t = np.arange(n) / SR
    crash_ = G.filt(G.white(rng, n), 'lowpass', 400, 2) * np.exp(-t / 0.5) * np.clip(t / 0.01, 0, 1)
    boom = G.filt(G.white(rng, n), 'lowpass', 90, 3) * np.exp(-t / 0.9)
    rubble = np.zeros(n)
    for _ in range(rng.integers(40, 70)):
        t0 = rng.uniform(0.0, 3.0) ** 1.3
        i = int(t0 * SR)
        if i >= n:
            continue
        m = int(rng.uniform(0.03, 0.12) * SR)
        tt = np.arange(m) / SR
        k = G.filt(rng.standard_normal(m), 'bandpass', (rng.uniform(150, 600), rng.uniform(1500, 4500)), 2) * np.exp(-tt * rng.uniform(25, 70))
        end = min(n, i + m)
        rubble[i:end] += k[:end - i] * rng.uniform(0.15, 0.7) * np.exp(-t0 / 1.6)
    dust = G.filt(G.pink(rng, n), 'bandpass', (300, 4000), 2) * np.exp(-t / 1.8) * 0.15
    x = G.peak_norm(crash_, -3) + G.peak_norm(boom, -3) + G.peak_norm(rubble, -4) + dust
    return G.peak_norm(G.fade(x, 0.0, 1.2), -1)


def crack_texture(seed):
    """64x16 strip: dark fissure along u, jagged edges, crumbled rim, little side cracks; alpha outside"""
    rng = np.random.default_rng(seed)
    w, h = 128, 32
    img = np.zeros((h, w, 4))
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    # The fissure's centre wanders a little; its width varies (periodic along u so it tiles).
    def periodic_noise(k, amp):
        phase = rng.uniform(0, 2 * np.pi, k)
        f = np.zeros(w)
        for i in range(1, k + 1):
            f += np.sin(2 * np.pi * i * np.arange(w) / w + phase[i - 1]) * amp / i
        return f
    centre = h / 2 + periodic_noise(6, 2.2)
    half = 3.2 + periodic_noise(7, 1.4)
    d = np.abs(yy - centre[None, :]) - np.maximum(0.8, half[None, :])
    rough = rng.random((h, w)) * 1.4
    inner = d + rough < 0
    rim = (d + rough * 1.6 < 3.5) & ~inner
    a = np.zeros((h, w))
    a[rim] = 0.55 * np.clip(1 - (d[rim] + rough[rim]) / 4.0, 0, 1)
    a[inner] = 0.97
    col = np.zeros((h, w, 3))
    col[...] = (0.16, 0.13, 0.11)
    col[inner] = (0.04, 0.035, 0.03)
    # Side cracks: thin jagged lines off the main one.
    for _ in range(9):
        x0 = rng.integers(0, w)
        side = rng.choice([-1, 1])
        y = centre[x0] + side * half[x0]
        x = float(x0)
        for _ in range(rng.integers(6, 13)):
            xi, yi = int(x) % w, int(round(y))
            if 0 <= yi < h:
                a[yi, xi] = max(a[yi, xi], 0.85)
                col[yi, xi] = (0.06, 0.05, 0.045)
            x += rng.uniform(0.3, 1.2)
            y += side * rng.uniform(0.4, 1.1)
    img[..., :3] = col
    img[..., 3] = a
    Image.fromarray((img * 255).astype(np.uint8), 'RGBA').save(os.path.join(ROOT, 'textures', 'environment', 'quake_crack.png'))


if __name__ == '__main__':
    G.write('quake_rumble', rumble(71))
    for i in range(3):
        G.write('quake_crack%d' % (i + 1), crack(72 + i))
    for i in range(2):
        G.write('quake_collapse%d' % (i + 1), collapse(76 + i))
    crack_texture(79)
    print('ok')
