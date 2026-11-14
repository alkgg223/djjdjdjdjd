"""Tsunami sounds (synthesised): the deep, endless roar of a wall of water coming in, the rush and gurgle of the
flood racing over the land behind it, and the crash of the wave breaking on whatever is in its way."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_sounds as G  # noqa: E402

SR = G.SR


def roar(seed):
    """loop: low thunderous roar with slow surges and the hiss of spray riding on it"""
    rng = np.random.default_rng(seed)
    L, xf = 8 * SR, int(1.5 * SR)
    n = L + xf
    surge = G.ctrl(rng, n, 0.35, 0.55, 1.0)
    low = G.filt(G.brown(rng, n), 'lowpass', 180, 2) * 1.6
    body = G.filt(G.pink(rng, n), 'bandpass', (120, 900), 2) * (0.5 + 0.5 * surge)
    hiss = G.filt(G.white(rng, n), 'bandpass', (1500, 7000), 2) * 0.18 * G.ctrl(rng, n, 1.5, 0.4, 1.0)
    # Breakers tumbling within the wave: low thumps at random.
    thumps = np.zeros(n)
    for _ in range(int(8 * 2.5)):
        i = rng.integers(0, n - SR)
        m = int(rng.uniform(0.2, 0.6) * SR)
        t = np.arange(m) / SR
        thumps[i:i + m] += G.filt(rng.standard_normal(m), 'lowpass', 220, 2) * np.exp(-t * rng.uniform(4, 9)) * rng.uniform(0.6, 1.4)
    x = (low + body + hiss) * (0.7 + 0.3 * surge) + thumps * 0.9
    x = G.loopify(x, xf)
    return G.rms_norm(x, -14)


def rush(seed):
    """loop: turbulent water racing past, gurgles and churning"""
    rng = np.random.default_rng(seed)
    L, xf = 6 * SR, SR
    n = L + xf
    churn = G.filt(G.pink(rng, n), 'bandpass', (250, 3500), 2) * G.ctrl(rng, n, 3.0, 0.5, 1.0)
    low = G.filt(G.brown(rng, n), 'lowpass', 300, 2) * 0.8
    gurgle = np.zeros(n)
    for _ in range(int(6 * 18)):
        i = rng.integers(0, n - SR // 4)
        m = int(rng.uniform(0.03, 0.12) * SR)
        t = np.arange(m) / SR
        f = rng.uniform(200, 900) * (1 + 0.8 * t / t[-1])
        gurgle[i:i + m] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 30) * rng.uniform(0.1, 0.4)
    x = churn + low + gurgle
    return G.rms_norm(G.loopify(x, xf), -16)


def crash(seed):
    """one shot: a heavy wave smashing down: a deep boom, the roar of foam and things being torn apart"""
    rng = np.random.default_rng(seed)
    n = int(4.5 * SR)
    t = np.arange(n) / SR
    boom = G.filt(G.white(rng, n), 'lowpass', 120, 3) * np.exp(-t / 0.8)
    foam = G.filt(G.pink(rng, n), 'bandpass', (300, 6000), 2) * np.clip(t / 0.05, 0, 1) * np.exp(-t / 1.6)
    debris = np.zeros(n)
    for _ in range(rng.integers(10, 20)):
        t0 = rng.uniform(0.05, 2.0)
        i = int(t0 * SR)
        m = int(0.25 * SR)
        tt = np.arange(m) / SR
        k = G.filt(rng.standard_normal(m), 'bandpass', (400, 3000), 2) * np.exp(-tt * rng.uniform(15, 40))
        debris[i:i + m] += k[:max(0, min(m, n - i))] * rng.uniform(0.2, 0.6)
    x = G.peak_norm(boom, -2) + G.peak_norm(foam, -4) + debris
    return G.peak_norm(G.fade(x, 0.0, 1.5), -1)


if __name__ == '__main__':
    G.write('tsunami_roar', roar(61))
    G.write('tsunami_rush', rush(62))
    for i in range(3):
        G.write('tsunami_crash%d' % (i + 1), crash(63 + i))
