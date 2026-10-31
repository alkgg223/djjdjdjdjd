"""Original vehicle sounds for Real Weather, synthesised from scratch: engines (petrol and diesel loops that the
game pitches with the revs), the starter, horns, wipers, hydraulics, tyre squeal, crash, door and instrument beep."""
import os
import sys

import numpy as np
import scipy.signal as ss

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_sounds as G  # noqa: E402

SR = G.SR


def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def engine(seed, f0, harmonics, clatter, length=2.4, xf=0.35):
    """A running engine: the firing pulse train (with tiny cycle-to-cycle jitter), its harmonics shaped by an
    exhaust resonance, mechanical whine and a little combustion noise riding on each pulse."""
    rng = np.random.default_rng(seed)
    t = t_axis(length + xf)
    n = len(t)
    jitter = np.cumsum(rng.normal(0, 0.0009, n))
    jitter -= np.linspace(jitter[0], jitter[-1], n)
    phase = 2 * np.pi * f0 * (t + jitter * 0.02)
    x = np.zeros(n)
    for k, a in enumerate(harmonics, 1):
        x += a * np.sin(k * phase + rng.uniform(0, 6.28))
    # Firing pulses: noise bursts at the firing rate.
    pulse = np.clip(np.sin(phase), 0, None) ** 6
    noise = G.filt(rng.normal(0, 1, n), 'bandpass', (300, 2400), 2)
    x += clatter * pulse * noise
    # Exhaust resonance.
    x = G.filt(x, 'lowpass', 1600, 2) + 0.4 * G.filt(x, 'bandpass', (70, 220), 2)
    whine = 0.04 * np.sin(2 * np.pi * f0 * 11.3 * t)
    x = x + whine
    x = G.loopify(x, int(xf * SR))
    return G.rms_norm(x, -15)


def starter(seed):
    rng = np.random.default_rng(seed)
    t = t_axis(1.5)
    crank_f = 9.0 + 4 * t
    crank = np.sin(2 * np.pi * np.cumsum(crank_f) / SR)
    motor = np.sin(2 * np.pi * 180 * t) * 0.3 + np.sign(np.sin(2 * np.pi * 360 * t)) * 0.08
    x = (0.6 + 0.4 * crank) * motor * (t < 0.95)
    x += 0.3 * G.filt(rng.normal(0, 1, len(t)), 'bandpass', (500, 3000), 2) * np.clip(crank, 0, 1) * (t < 0.95)
    catch = engine(seed + 1, 30, [1, 0.6, 0.5, 0.3, 0.2], 0.9, length=1.5, xf=0.01)[:len(t)]
    env = np.clip((t - 0.85) / 0.1, 0, 1) * np.exp(-np.clip(t - 1.0, 0, None) * 1.5)
    x = x + catch * 3 * env
    return G.peak_norm(G.fade(x, 0.01, 0.2), -3)


def horn(f1, f2, length):
    t = t_axis(length)
    x = 0
    for f in (f1, f2):
        x = x + ss.square(2 * np.pi * f * t, 0.45) * 0.5 + np.sin(2 * np.pi * f * t) * 0.3
    x = G.filt(x, 'lowpass', 2800, 2)
    x = G.filt(x, 'highpass', 150, 1)
    env = np.clip(t / 0.02, 0, 1) * np.clip((length - t) / 0.05, 0, 1)
    return G.peak_norm(x * env, -2)


def wiper(seed):
    rng = np.random.default_rng(seed)
    t = t_axis(0.45)
    env = np.sin(np.pi * np.clip(t / 0.42, 0, 1)) ** 1.5
    swish = G.filt(rng.normal(0, 1, len(t)), 'bandpass', (900, 5000), 2) * env
    squeak_f = 1400 + 300 * np.sin(2 * np.pi * 3 * t)
    squeak = 0.15 * np.sin(2 * np.pi * np.cumsum(squeak_f) / SR) * env * (rng.random(len(t)) > 0.3)
    thump = 0.6 * np.sin(2 * np.pi * 90 * t) * np.exp(-np.clip(t - 0.38, 0, None) * 40) * (t > 0.38)
    return G.peak_norm(swish + squeak + thump, -6)


def hydraulic(seed):
    rng = np.random.default_rng(seed)
    t = t_axis(2.4)
    f = 260 + 220 * np.clip(t / 2.0, 0, 1)
    whine = 0.4 * np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.15 * np.sin(2 * np.pi * np.cumsum(f * 2.01) / SR)
    hiss = 0.25 * G.filt(rng.normal(0, 1, len(t)), 'highpass', 2500, 2)
    env = np.clip(t / 0.15, 0, 1) * np.clip((2.1 - t) / 0.2, 0, 1)
    clunk = np.sin(2 * np.pi * 70 * t) * np.exp(-np.clip(t - 2.1, 0, None) * 25) * (t > 2.1)
    clunk += 0.4 * G.filt(rng.normal(0, 1, len(t)), 'lowpass', 900, 2) * np.exp(-np.clip(t - 2.1, 0, None) * 30) * (t > 2.1)
    return G.peak_norm((whine + hiss) * env + clunk * 1.5, -3)


def skid(seed):
    rng = np.random.default_rng(seed)
    t = t_axis(0.7)
    f = 950 + 120 * np.sin(2 * np.pi * 7 * t) + 60 * rng.normal(0, 1, len(t)).cumsum() / SR * 30
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.5
    band = G.filt(rng.normal(0, 1, len(t)), 'bandpass', (700, 1600), 3)
    env = np.clip(t / 0.05, 0, 1) * np.clip((0.7 - t) / 0.2, 0, 1)
    return G.peak_norm((tone * 0.6 + band) * env, -6)


def crash(seed):
    rng = np.random.default_rng(seed)
    t = t_axis(1.4)
    thump = np.sin(2 * np.pi * 55 * t) * np.exp(-t * 9)
    crunch = G.filt(rng.normal(0, 1, len(t)), 'bandpass', (200, 4000), 2) * np.exp(-t * 5)
    metal = np.zeros(len(t))
    for _ in range(14):
        t0 = rng.uniform(0.0, 0.8)
        f = rng.uniform(600, 3000)
        metal += np.sin(2 * np.pi * f * t) * np.exp(-np.clip(t - t0, 0, None) * rng.uniform(15, 40)) * (t > t0) * rng.uniform(0.1, 0.4)
    glass = G.filt(rng.normal(0, 1, len(t)), 'highpass', 4000, 2) * np.exp(-np.clip(t - 0.05, 0, None) * 8) * (t > 0.05) * 0.4
    return G.peak_norm(thump * 1.5 + crunch + metal + glass, -1)


def door(seed):
    rng = np.random.default_rng(seed)
    t = t_axis(0.5)
    thunk = np.sin(2 * np.pi * 85 * t) * np.exp(-t * 18) + 0.5 * np.sin(2 * np.pi * 140 * t) * np.exp(-t * 25)
    latch = G.filt(rng.normal(0, 1, len(t)), 'bandpass', (1500, 6000), 2) * np.exp(-np.clip(t - 0.02, 0, None) * 120) * (t > 0.02)
    return G.peak_norm(thunk + 0.6 * latch, -2)


def beep():
    t = t_axis(0.42)
    x = np.sin(2 * np.pi * 1760 * t) * (((t > 0.0) & (t < 0.12)) | ((t > 0.2) & (t < 0.32)))
    return G.peak_norm(G.filt(x, 'lowpass', 6000, 1), -8)


def buffet(seed):
    rng = np.random.default_rng(seed)
    t = t_axis(3.0 + 0.4)
    x = G.filt(rng.normal(0, 1, len(t)), 'lowpass', 300, 2) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 0.7 * t)))
    return G.rms_norm(G.loopify(x, int(0.4 * SR)), -18)


if __name__ == '__main__':
    G.write('vehicle_engine', engine(1, 42, [1.0, 0.7, 0.45, 0.35, 0.2, 0.12, 0.08], 0.35))
    G.write('vehicle_diesel', engine(2, 30, [1.0, 0.8, 0.6, 0.45, 0.3, 0.2, 0.15], 1.1))
    G.write('vehicle_start', starter(3))
    G.write('vehicle_horn', horn(415, 498, 0.65))
    G.write('vehicle_horn_truck', horn(185, 233, 0.95))
    G.write('vehicle_wiper', wiper(4))
    G.write('vehicle_hydraulic', hydraulic(5))
    G.write('vehicle_skid', skid(6))
    G.write('vehicle_crash', crash(7))
    G.write('vehicle_door', door(8))
    G.write('vehicle_beep', beep())
    G.write('vehicle_wind_buffet', buffet(9))
