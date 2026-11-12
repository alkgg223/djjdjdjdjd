"""Thunder built from a recorded thunder clap (tools/sound_sources/thunder_clap.mp3): as it is for strikes nearby,
and changed the way distance changes real thunder for the farther classes: the air takes the highs away, the crack
softens into a swell, the peal is drawn out and lower, and echoes off hills and the cloud base fill in a long
rolling tail (mixed with the modelled thunder of gen_sounds.py so no two peals sound the same)."""
import os
import subprocess
import sys
from fractions import Fraction

import numpy as np
import scipy.signal as ss

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_sounds as G  # noqa: E402

SR = G.SR
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sound_sources', 'thunder_clap.mp3')


def load():
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', SRC, '-ac', '1', '-ar', str(SR), '-f', 's16le', '-'],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(raw, np.int16).astype(np.float64) / 32768.0
    i = int(np.argmax(np.abs(x) > 0.02))
    return x[max(0, i - int(0.01 * SR)):]


def pitch(x, p):
    """play the clip at `p` times its speed (lower and longer when p < 1)"""
    if abs(p - 1) < 1e-3:
        return x.copy()
    fr = Fraction(1 / p).limit_denominator(64)
    return ss.resample_poly(x, fr.numerator, fr.denominator)


def reverb(rng, x, decay, cutoff, wet, length):
    n = int(length * SR)
    ir_n = int(min(decay * 3, 6) * SR)
    t = np.arange(ir_n) / SR
    ir = rng.standard_normal(ir_n) * np.exp(-t / decay)
    ir = G.filt(ir, 'lowpass', cutoff, 2)
    ir[:int(0.03 * SR)] *= np.linspace(0, 1, int(0.03 * SR))
    y = np.zeros(n)
    y[:min(n, len(x))] = x[:n]
    tail = ss.fftconvolve(y, ir)[:n]
    tail = G.rms_norm(tail, 20 * np.log10(np.sqrt(np.mean(y ** 2)) + 1e-12))
    return y * (1 - wet * 0.5) + tail * wet


def echoes(rng, x, count, cutoff):
    out = x.copy()
    for _ in range(count):
        d = int(rng.uniform(0.25, 2.2) * SR)
        if d < len(x):
            out[d:] += G.filt(x[:len(x) - d], 'lowpass', cutoff * rng.uniform(0.6, 1.0), 2) * 10 ** (-rng.uniform(7, 15) / 20)
    return out


def fit(x, length):
    n = int(length * SR)
    y = np.zeros(n)
    y[:min(n, len(x))] = x[:n]
    return y


def strike(x, rng, p):
    """a few dozen metres away: the clap at full force with a chest-deep boom under it"""
    y = pitch(x, p)
    n = len(y) + int(1.5 * SR)
    y = fit(y, n / SR)
    t = np.arange(n) / SR
    boom = G.peak_norm(G.filt(G.white(rng, n), 'lowpass', 70, 3) * np.exp(-t / 1.4), -3)
    y = G.peak_norm(y, -1) + boom * 0.45
    y = reverb(rng, y, 1.2, 1500, 0.25, n / SR)
    y = np.tanh(y * 1.3) / np.tanh(1.3)
    return G.peak_norm(G.fade(y, 0, 1.2), -0.8)


def close(x, rng, p, i):
    """a few hundred metres: still a sharp crack, the highs a little softer, echoes coming back"""
    y = pitch(x, p)
    length = len(y) / SR + 3.0
    y = fit(G.filt(y, 'lowpass', 7000, 2), length)
    y = echoes(rng, y, 3, 1400)
    y = reverb(rng, y, 1.8, 1200, 0.35, length)
    synth = G.thunder_real(100 + i, rng.uniform(150, 300), length)
    y = G.peak_norm(y, -1) + G.peak_norm(fit(synth, length), -1) * 0.25
    return G.peak_norm(G.fade(y, 0, 1.8), -1)


def mid(x, rng, p, i):
    """one or two kilometres: no crack left, a deeper rolling peal that swells in"""
    y = pitch(x, p)
    length = len(y) / SR + 5.0
    y = G.filt(G.filt(y, 'lowpass', 1800, 2), 'lowpass', 2600, 2)
    a = int(0.2 * SR)
    y[:a] *= np.linspace(0, 1, a) ** 1.5
    y = fit(y, length)
    y = echoes(rng, y, 5, 900)
    y = reverb(rng, y, 2.6, 800, 0.55, length)
    synth = G.thunder_real(200 + i, rng.uniform(900, 2200), length)
    y = G.peak_norm(y, -1) + G.peak_norm(fit(synth, length), -1) * 0.45
    y = G.filt(y, 'highpass', 22, 2)
    return G.peak_norm(G.fade(y, 0, 2.5), -1)


def far(x, rng, p, i):
    """several kilometres: only the low rumble arrives, slowly swelling and rolling for a long time"""
    y = pitch(x, p)
    length = len(y) / SR + 7.0
    y = G.filt(G.filt(y, 'lowpass', 420, 2), 'lowpass', 600, 2)
    a = int(0.45 * SR)
    y[:a] *= np.linspace(0, 1, a) ** 1.5
    y = fit(y, length)
    y = echoes(rng, y, 6, 350)
    y = reverb(rng, y, 4.0, 300, 0.7, length)
    synth = G.thunder_real(300 + i, rng.uniform(4200, 9000), length)
    y = G.peak_norm(y, -1) + G.peak_norm(fit(synth, length), -1) * 0.7
    y = G.filt(y, 'highpass', 20, 2)
    return G.peak_norm(G.fade(y, 0, 3.5), -1)


def main():
    x = load()
    rng = np.random.default_rng(2026)
    for i, p in enumerate([1.0, 0.95, 1.04, 0.9]):
        G.write('thunder_strike%d' % (i + 1), strike(x, rng, p))
    for i, p in enumerate([1.0, 0.94, 1.05, 0.9, 0.97, 1.02]):
        G.write('thunder_close%d' % (i + 1), close(x, rng, p, i))
    for i, p in enumerate([0.88, 0.82, 0.92, 0.85, 0.8, 0.9]):
        G.write('thunder_mid%d' % (i + 1), mid(x, rng, p, i))
    for i, p in enumerate([0.75, 0.7, 0.8, 0.72, 0.78, 0.68]):
        G.write('thunder_far%d' % (i + 1), far(x, rng, p, i))


if __name__ == '__main__':
    main()
