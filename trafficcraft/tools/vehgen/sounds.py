"""Procedural vehicle audio: per-engine idle/drive loops (seamless, FFT circular convolution), startup sequences,
horns and shared effect sounds. Output: mono OGG Vorbis via ffmpeg."""
import os, sys, subprocess, math
import numpy as np
from scipy import signal

SR = 44100
rng = np.random.default_rng(42)

# engine character table ------------------------------------------------------------------------------------------
# cyl, idle rpm, drive rpm, pulse pattern, exhaust resonances (Hz, decay/s, gain), diesel clatter, turbo whistle Hz,
# noise, starter pitch, low-pass
ENGINES = {
    'i3_turbo': dict(cyl=3, idle=850, drive=2600, pattern=[1.0, 0.85, 0.92], res=[(110, 38, 1.0), (330, 70, 0.55), (900, 160, 0.25)],
                     clatter=0.0, turbo=3300, noise=0.12, starter=210, lp=5000, gain=0.9),
    'i4_petrol': dict(cyl=4, idle=800, drive=2800, pattern=[1.0, 0.95, 0.98, 0.93], res=[(150, 45, 1.0), (420, 90, 0.5), (1150, 180, 0.22)],
                      clatter=0.0, turbo=0, noise=0.1, starter=190, lp=6000, gain=0.85),
    'i4_turbo': dict(cyl=4, idle=760, drive=2500, pattern=[1.0, 0.92, 0.97, 0.9], res=[(130, 40, 1.0), (380, 80, 0.5), (980, 170, 0.25)],
                     clatter=0.0, turbo=2800, noise=0.14, starter=175, lp=5500, gain=0.85),
    'v6_diesel': dict(cyl=6, idle=700, drive=2200, pattern=[1.0, 0.9, 0.95, 0.88, 0.97, 0.9], res=[(95, 30, 1.0), (260, 60, 0.6), (700, 140, 0.3)],
                      clatter=0.55, turbo=2400, noise=0.16, starter=150, lp=4500, gain=0.95),
    'v8_sport': dict(cyl=8, idle=950, drive=3400, pattern=[1.0, 0.55, 1.0, 0.72, 0.92, 0.5, 1.0, 0.68], res=[(85, 22, 1.0), (240, 50, 0.7),
                                                                                                           (620, 120, 0.35), (1500, 260, 0.12)],
                     clatter=0.0, turbo=0, noise=0.2, starter=230, lp=7000, gain=1.0),
    'v8_police': dict(cyl=8, idle=750, drive=2900, pattern=[1.0, 0.7, 0.95, 0.8, 0.95, 0.68, 1.0, 0.78], res=[(75, 25, 1.0), (210, 55, 0.6),
                                                                                                            (560, 130, 0.3)],
                      clatter=0.0, turbo=0, noise=0.16, starter=170, lp=5500, gain=1.0),
    'v6_cruiser': dict(cyl=6, idle=650, drive=2300, pattern=[1.0, 0.93, 0.97, 0.92, 0.98, 0.94], res=[(105, 35, 1.0), (300, 75, 0.45), (820, 170, 0.18)],
                       clatter=0.0, turbo=0, noise=0.09, starter=160, lp=4200, gain=0.85),
    'i4_diesel': dict(cyl=4, idle=780, drive=2300, pattern=[1.0, 0.9, 0.96, 0.88], res=[(120, 35, 1.0), (340, 75, 0.55), (880, 160, 0.3)],
                      clatter=0.7, turbo=2600, noise=0.15, starter=160, lp=4800, gain=0.9),
    'diesel_truck': dict(cyl=6, idle=600, drive=1700, pattern=[1.0, 0.92, 0.97, 0.9, 0.98, 0.93], res=[(60, 18, 1.0), (160, 40, 0.7), (420, 100, 0.4),
                                                                                                     (1100, 220, 0.15)],
                         clatter=0.85, turbo=2100, noise=0.2, starter=120, lp=4000, gain=1.0),
    # ---- real cars
    'i6_twinturbo': dict(cyl=6, idle=750, drive=3600, pattern=[1.0, 0.97, 0.99, 0.96, 1.0, 0.97], res=[(120, 32, 1.0), (360, 70, 0.6), (980, 150, 0.32),
                                                                                                    (2300, 380, 0.1)],
                         clatter=0.0, turbo=3200, noise=0.13, starter=200, lp=7000, gain=0.95),
    # ---- motorcycles
    'vtwin_cruiser': dict(cyl=2, idle=900, drive=3000, pattern=[1.0, 0.82], fire=[0.0, 0.4375], jit=0.09,
                          res=[(52, 16, 1.0), (138, 38, 0.85), (410, 105, 0.35), (1250, 250, 0.1)],
                          clatter=0.0, turbo=0, noise=0.2, starter=230, lp=3300, gain=1.0),
    'i4_sport': dict(cyl=4, idle=1300, drive=8500, pattern=[1.0, 0.96, 0.99, 0.95], res=[(250, 55, 1.0), (690, 140, 0.6), (1850, 300, 0.35),
                                                                                       (4100, 600, 0.12)],
                     clatter=0.0, turbo=0, noise=0.15, starter=270, lp=10000, gain=0.95, intake=1),
    'single_thumper': dict(cyl=1, idle=1150, drive=4800, pattern=[1.0], jit=0.14, res=[(78, 20, 1.0), (236, 55, 0.7), (880, 180, 0.3),
                                                                                       (2600, 420, 0.08)],
                           clatter=0.0, turbo=0, noise=0.24, starter=240, lp=5600, gain=1.0),
}


def ir(E, n=2048):
    t = np.arange(n) / SR
    h = np.zeros(n)
    for f, d, g in E['res']:
        h += g * np.exp(-d * t) * np.sin(2 * np.pi * f * t)
    return h / (np.abs(h).max() + 1e-9)


def clatter_ir(n=600):
    t = np.arange(n) / SR
    b, a = signal.butter(2, [1800 / (SR / 2), 5200 / (SR / 2)], 'band')
    x = rng.standard_normal(n) * np.exp(-t * 90)
    return signal.lfilter(b, a, x)


def pulse_train(times, amps, n):
    x = np.zeros(n)
    idx = (np.asarray(times) * SR).astype(int)
    ok = idx < n
    np.add.at(x, idx[ok], np.asarray(amps)[ok])
    return x


def engine_loop(E, rpm, seconds=2.0, load=0.5):
    """Seamless loop: integer number of engine cycles, circular convolution."""
    cyc_t = 120.0 / rpm  # one 4-stroke cycle (2 revolutions)
    ncyc = max(4, int(round(seconds / cyc_t)))
    n = int(round(ncyc * cyc_t * SR))
    cyl = E['cyl']
    times, amps, cl = [], [], []
    for c in range(ncyc):
        for k in range(cyl):
            off = E['fire'][k] if 'fire' in E else k / cyl
            t = (c + off) * cyc_t + rng.normal(0, 0.0004)
            a = E['pattern'][k] * (1 + rng.normal(0, E.get('jit', 0.06))) * (0.75 + 0.5 * load)
            times.append(t % (n / SR))
            amps.append(a)
    x = pulse_train(times, amps, n)
    H = np.fft.rfft(ir(E), n)
    y = np.fft.irfft(np.fft.rfft(x) * H, n)
    if E['clatter'] > 0:
        cx = pulse_train(times, np.array(amps) * E['clatter'], n)
        y += 0.35 * np.fft.irfft(np.fft.rfft(cx) * np.fft.rfft(clatter_ir(), n), n) / 1.5
    # combustion noise modulated by the firing rate
    ff = rpm / 60 * cyl / 2
    tt = np.arange(n) / SR
    noise = rng.standard_normal(n)
    noise = np.fft.irfft(np.fft.rfft(noise) * np.fft.rfft(np.exp(-np.arange(400) / 40.0), n), n)
    env = 0.6 + 0.4 * np.sin(2 * np.pi * ff * tt * (round(ff * n / SR) / max(1e-9, ff * n / SR)))
    y = y / (np.abs(y).max() + 1e-9) + E['noise'] * load * noise / (np.abs(noise).max() + 1e-9) * env
    if E['turbo'] and load > 0.3:
        f = E['turbo'] * (rpm / E['drive'])
        k = round(f * n / SR)
        y += 0.035 * load * np.sin(2 * np.pi * k * tt * SR / n)
    if E.get('intake') and load > 0.3:
        # induction howl: second harmonic of the firing frequency, band-passed (the supersport scream)
        k2 = round(2 * ff * n / SR)
        y += 0.12 * load * (np.sin(2 * np.pi * k2 * tt * SR / n) + 0.4 * np.sin(2 * np.pi * 2 * k2 * tt * SR / n))
    b, a = signal.butter(2, E['lp'] / (SR / 2))
    # filter circularly: pad with a copy to avoid the start transient
    yy = signal.lfilter(b, a, np.concatenate([y, y]))[n:]
    return yy


def startup(E, seconds=2.6):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    idle = E['idle']
    crank_end = 0.65 + 0.25 * (E['cyl'] >= 6) + (0.25 if E['clatter'] > 0.6 else 0)
    # rpm curve: crank ~220 rpm, catch -> flare -> idle
    rpm = np.where(t < crank_end, 200 + 40 * np.sin(t * 9),
                   idle + (idle * 0.9) * np.exp(-((t - crank_end - 0.35) / 0.28) ** 2) * (t > crank_end) +
                   (idle * 0.4) * np.exp(-(t - crank_end) * 2.5) * (t > crank_end))
    ff = rpm / 60 * E['cyl'] / 2
    phase = np.cumsum(ff) / SR
    k = np.floor(phase)
    fire = np.where(np.diff(k, prepend=0) > 0)[0]
    amps = []
    for i in fire:
        tt = t[i]
        if tt < crank_end:
            amps.append(0.18 + rng.normal(0, 0.03))  # compression pulses only
        else:
            amps.append((1.0 if tt < crank_end + 0.6 else 0.75) * E['pattern'][len(amps) % E['cyl']] * (1 + rng.normal(0, 0.08)))
    x = np.zeros(n)
    x[fire] = amps
    y = signal.fftconvolve(x, ir(E), 'full')[:n]
    if E['clatter'] > 0:
        cx = np.zeros(n)
        cx[fire] = np.array(amps) * E['clatter'] * (t[fire] > crank_end)
        y += 0.3 * signal.fftconvolve(cx, clatter_ir(), 'full')[:n]
    # starter motor: geared whine + brushes, amplitude dips at compression
    st = (t < crank_end + 0.05)
    sf = E['starter'] * (1 + 0.05 * np.sin(t * 9))
    sp = 2 * np.pi * np.cumsum(sf) / SR
    starter = (signal.sawtooth(sp) * 0.5 + np.sin(sp * 2) * 0.3 + 0.2 * rng.standard_normal(n)) * st
    starter *= (0.7 + 0.3 * np.sin(2 * np.pi * np.cumsum(ff) / SR)) * np.clip(t / 0.05, 0, 1) * np.clip((crank_end + 0.05 - t) / 0.08, 0, 1)
    b, a = signal.butter(2, [120 / (SR / 2), 3000 / (SR / 2)], 'band')
    starter = signal.lfilter(b, a, starter)
    y = y / (np.abs(y).max() + 1e-9) + 0.35 * starter / (np.abs(starter).max() + 1e-9)
    # key click / relay at start
    y[:600] += 0.6 * np.exp(-np.arange(600) / 60) * rng.standard_normal(600)
    b, a = signal.butter(2, E['lp'] / (SR / 2))
    y = signal.lfilter(b, a, y)
    fade = np.clip((seconds - t) / 0.3, 0, 1)
    return y * fade


def horn(kind, seconds=0.9):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    if kind == 'truck':
        fs, rich, sec = (277, 349), 1.0, 1.2
    elif kind == 'high':
        fs, rich = (520, 650), 0.5
    elif kind == 'bike':
        fs, rich = (440,), 0.75
    elif kind == 'low':
        fs, rich = (330, 415), 0.7
    else:
        fs, rich = (415, 520), 0.6
    y = np.zeros(n)
    for f in fs:
        ph = 2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 6 * t)
        y += signal.square(ph, 0.4) * rich + np.sin(ph) * (1 - rich)
    b, a = signal.butter(2, [250 / (SR / 2), 3500 / (SR / 2)], 'band')
    y = signal.lfilter(b, a, y)
    env = np.clip(t / (0.06 if kind == 'truck' else 0.015), 0, 1) * np.clip((seconds - t) / 0.06, 0, 1)
    return y * env


def siren(seconds=4.0):
    t = np.arange(int(seconds * SR)) / SR
    f = 700 + 500 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 2.0))
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.sin(ph) + 0.3 * np.sin(ph * 2) + 0.15 * signal.square(ph)
    return y


def clicks(f0, seconds=0.06, sharp=1.0):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    y = (np.sin(2 * np.pi * f0 * t) + 0.5 * rng.standard_normal(n) * sharp) * np.exp(-t * 120)
    return y


def door(close=True):
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    thud = np.sin(2 * np.pi * 70 * t) * np.exp(-t * 18) + 0.4 * np.sin(2 * np.pi * 140 * t) * np.exp(-t * 25)
    latch = np.zeros(n)
    k = int(0.01 * SR)
    latch[k:k + 1500] = rng.standard_normal(1500) * np.exp(-np.arange(1500) / 200)
    y = thud * (1.0 if close else 0.35) + latch * (0.6 if close else 0.9)
    return y


def motor(seconds=1.6, f=95):
    t = np.arange(int(seconds * SR)) / SR
    ph = 2 * np.pi * f * t
    y = signal.sawtooth(ph) * 0.4 + np.sin(ph * 2) * 0.3 + 0.15 * rng.standard_normal(len(t))
    b, a = signal.butter(2, [60 / (SR / 2), 1800 / (SR / 2)], 'band')
    y = signal.lfilter(b, a, y)
    return y * np.clip(t / 0.08, 0, 1) * np.clip((seconds - t) / 0.12, 0, 1)


def pump_loop(seconds=1.5):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    y = np.sin(2 * np.pi * 120 * t) * 0.4 + np.sin(2 * np.pi * 240 * t) * 0.2
    noise = rng.standard_normal(n)
    b, a = signal.butter(2, [400 / (SR / 2), 2500 / (SR / 2)], 'band')
    y += 0.4 * signal.lfilter(b, a, np.concatenate([noise, noise]))[n:]
    return y


def crash():
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    y = rng.standard_normal(n) * np.exp(-t * 6) + np.sin(2 * np.pi * 55 * t) * np.exp(-t * 9) * 1.5
    for k in range(12):
        s = int(rng.uniform(0.02, 0.5) * SR)
        f = rng.uniform(1500, 5000)
        L = int(0.12 * SR)
        y[s:s + L] += 0.5 * np.sin(2 * np.pi * f * np.arange(min(L, n - s)) / SR) * np.exp(-np.arange(min(L, n - s)) / (0.02 * SR))
    return y


def beep(seconds=0.5, f=1100):
    t = np.arange(int(seconds * SR)) / SR
    return np.sin(2 * np.pi * f * t) * (t < 0.25) * np.clip(t / 0.005, 0, 1)


# exhaust voices for backfires: pipe length (m), silencer formant (Hz), metal ring (Hz), loudness of the crack
BF_VOICES = {
    'turbo': dict(pipe=2.6, muff=165, ring=2600, crack=1.0, dark=0.0),
    'v8': dict(pipe=3.1, muff=105, ring=1900, crack=0.85, dark=0.25),
    'old': dict(pipe=2.9, muff=92, ring=1700, crack=0.7, dark=0.4),
    'race': dict(pipe=1.5, muff=260, ring=3400, crack=1.3, dark=-0.2),
    'rally': dict(pipe=1.7, muff=230, ring=3100, crack=1.25, dark=-0.1),
    'bike': dict(pipe=1.1, muff=300, ring=3800, crack=1.15, dark=-0.15),
    'na': dict(pipe=2.4, muff=150, ring=2400, crack=0.95, dark=0.1),
    'quiet': dict(pipe=3.0, muff=120, ring=2000, crack=0.55, dark=0.35),
    'small': dict(pipe=1.9, muff=210, ring=3000, crack=0.8, dark=0.05),
}


def _friedlander(T, b, n):
    """Blast pressure wave: instant rise, linear-exponential fall through zero into a negative phase."""
    t = np.arange(n) / SR
    p = (1 - t / T) * np.exp(-b * t / T)
    p[t > 7 * T] *= np.exp(-(t[t > 7 * T] - 7 * T) * 900)
    return p


def _pipe_ir(V, size, r, n):
    """Exhaust system response: quarter-wave pipe modes, silencer formant and a short metallic ring."""
    t = np.arange(n) / SR
    c = 343.0
    h = np.zeros(n)
    f1 = c / (4 * V['pipe'])
    for k in range(1, 10):
        f = (2 * k - 1) * f1 * r.uniform(0.97, 1.03)
        if f > 5000:
            break
        h += k ** -0.7 * np.exp(-(22 + 16 * k) / size * t) * np.sin(2 * np.pi * f * t)
    h += 0.9 * np.exp(-55 / size * t) * np.sin(2 * np.pi * V['muff'] * r.uniform(0.92, 1.08) * t)
    h += 0.18 * np.exp(-160 * t) * np.sin(2 * np.pi * V['ring'] * r.uniform(0.9, 1.1) * t)
    return h / (np.abs(h).max() + 1e-9)


def _room(y, rt60, r, wet=0.28):
    """Ground bounce, a few wall reflections (a street) and a short diffuse tail."""
    n = len(y)
    out = y.copy()
    for d_ms, g, lp in ((r.uniform(3, 6), 0.55, 6000), (r.uniform(16, 22), 0.3, 4000), (r.uniform(28, 36), 0.2, 3000), (r.uniform(44, 55), 0.12, 2500)):
        d = int(d_ms / 1000 * SR)
        b, a = signal.butter(1, lp / (SR / 2))
        e = signal.lfilter(b, a, y)[:n - d] * g
        out[d:] += e
    L = int(rt60 * SR)
    t = np.arange(L) / SR
    ir_ = r.standard_normal(L) * np.exp(-6.9 * t / rt60)
    b, a = signal.butter(2, 3500 / (SR / 2))
    ir_ = signal.lfilter(b, a, ir_)
    ir_ /= np.sqrt(np.sum(ir_ ** 2)) + 1e-9
    tail = signal.fftconvolve(y, ir_)[:n]
    return out + wet * tail


def backfire(kind, seed, voice='turbo'):
    """Exhaust backfires: 'pop' (one or two sharp cracks), 'crackle' (burst of pops and gurgle on the overrun), 'bang' (big boom).
    Physical model: blast wave -> pipe/silencer resonances, direct crack, flame whoosh, sub thump, saturation, street acoustics."""
    V = BF_VOICES.get(voice, BF_VOICES['turbo'])
    r = np.random.default_rng(1000 + seed)
    dur = {'pop': 0.75, 'crackle': 1.25, 'bang': 1.6, 'tick': 0.45}[kind]
    n = int(dur * SR)
    y = np.zeros(n)

    def one(at, amp, size):
        s = int(at * SR)
        L = min(n - s, int(0.45 * SR))
        if L <= 0:
            return
        t = np.arange(L) / SR
        shock = _friedlander(0.00045 * size + 0.0002, 1.6, L)
        body = signal.fftconvolve(shock, _pipe_ir(V, size, r, L))[:L]
        body /= np.abs(body).max() + 1e-9
        bh, ah = signal.butter(2, 1800 / (SR / 2), 'high')
        crack = signal.lfilter(bh, ah, shock)
        crack /= np.abs(crack).max() + 1e-9
        flame = r.standard_normal(L) * (1 - np.exp(-t / 0.003)) * np.exp(-t / (0.045 * size))
        bf, af = signal.butter(2, [350 / (SR / 2), 2600 / (SR / 2)], 'band')
        flame = signal.lfilter(bf, af, flame)
        flame /= np.abs(flame).max() + 1e-9
        sub = np.sin(2 * np.pi * (48 - 8 * V['dark']) / size ** 0.3 * t) * np.exp(-t * 22 / size) * min(1.0, size - 0.4)
        x = body * (1.25 + 0.3 * V['dark']) + crack * 0.42 * V['crack'] + flame * 0.26 + sub * (0.9 + 0.5 * V['dark'])
        y[s:s + L] += amp * x

    if kind == 'pop':
        one(0.0, 1.0, r.uniform(0.9, 1.25))
        if r.random() < 0.65:
            one(r.uniform(0.035, 0.11), r.uniform(0.35, 0.6), r.uniform(0.55, 0.8))
        rt = 0.38
    elif kind == 'tick':
        # one pop of an overrun crackle (the game plays these in time with each flicker of flame)
        one(0.0, 1.0, r.uniform(0.38, 0.75))
        if r.random() < 0.25:
            one(r.uniform(0.006, 0.014), r.uniform(0.3, 0.55), r.uniform(0.3, 0.45))
        rt = 0.24
    elif kind == 'bang':
        one(0.0, 1.7, r.uniform(2.1, 2.6))
        if r.random() < 0.45:
            one(r.uniform(0.07, 0.14), 0.55, 0.9)
        rt = 0.9
    else:
        # overrun crackle: clustered irregular pops (some doublets), fading out, over a low gurgle
        t0, amp = 0.0, 1.0
        for k in range(int(r.integers(9, 17))):
            one(t0, amp * r.uniform(0.45, 1.0), r.uniform(0.35, 0.85))
            if r.random() < 0.22:
                one(t0 + r.uniform(0.005, 0.013), amp * r.uniform(0.3, 0.6), r.uniform(0.3, 0.5))
            t0 += r.gamma(2.0, 0.022) + 0.006
            amp *= r.uniform(0.86, 0.97)
            if t0 > dur - 0.35:
                break
        L = int(min(t0 + 0.1, dur) * SR)
        g = r.standard_normal(L)
        bg, ag = signal.butter(2, [55 / (SR / 2), 230 / (SR / 2)], 'band')
        g = signal.lfilter(bg, ag, g)
        mod = np.abs(signal.lfilter(*signal.butter(1, 30 / (SR / 2)), r.standard_normal(L)))
        g = g * mod / (np.abs(g * mod).max() + 1e-9) * np.exp(-np.arange(L) / SR / 0.5)
        y[:L] += 0.22 * g
        rt = 0.3
    y = np.tanh(1.7 * y / (np.abs(y).max() + 1e-9)) / np.tanh(1.7)
    bl, al = signal.butter(2, (5200 - 1800 * V['dark']) / (SR / 2))
    y = signal.lfilter(bl, al, y)
    y = _room(y, rt, r, wet=0.3 if kind != 'bang' else 0.38)
    fade = int(0.05 * SR)
    y[-fade:] *= np.linspace(1, 0, fade)
    return y


def write_ogg(y, path, rms=0.16, peak=0.97):
    y = np.asarray(y, float)
    r = np.sqrt(np.mean(y ** 2)) + 1e-9
    y = y * (rms / r)
    m = np.abs(y).max()
    if m > peak:
        y = y * (peak / m)
    pcm = (y * 32767).astype('<i2').tobytes()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', '-', '-map_metadata', '-1',
                    '-c:a', 'libvorbis', '-q:a', '4', path], input=pcm, check=True)


def build_all(specs, out_dir):
    """out_dir = assets/<modid>/sounds. Returns sounds.json dict."""
    sj = {}

    def reg(name, files, stream=False):
        sj[name] = {'sounds': [{'name': 'trafficcraft:' + f, 'stream': stream} for f in files]}

    for S in specs:
        E = ENGINES[S['engine']]
        vid = S['id']
        write_ogg(engine_loop(E, E['idle'], 2.0, 0.25), f"{out_dir}/engine/{vid}_idle.ogg", rms=0.13)
        write_ogg(engine_loop(E, E['drive'], 2.0, 0.85), f"{out_dir}/engine/{vid}_drive.ogg", rms=0.15)
        write_ogg(startup(E), f"{out_dir}/engine/{vid}_start.ogg", rms=0.14)
        reg(f'engine.{vid}.idle', [f'engine/{vid}_idle'])
        reg(f'engine.{vid}.drive', [f'engine/{vid}_drive'])
        reg(f'engine.{vid}.start', [f'engine/{vid}_start'])
    for k in ('pop', 'crackle', 'bang'):
        files = []
        for v in range(3 if k != 'bang' else 2):
            write_ogg(backfire(k, v * 7 + len(k)), f"{out_dir}/backfire/{k}{v}.ogg", rms=0.2 if k != 'bang' else 0.26, peak=0.99)
            files.append(f'backfire/{k}{v}')
        reg(f'backfire.{k}', files)
    for k in ('car', 'high', 'low', 'truck', 'bike'):
        write_ogg(horn(k, 1.0 if k != 'truck' else 1.3), f"{out_dir}/horn/{k}.ogg", rms=0.2)
        reg(f'horn.{k}', [f'horn/{k}'])
    write_ogg(siren(), f"{out_dir}/siren/wail.ogg", rms=0.2)
    reg('siren.wail', ['siren/wail'])
    write_ogg(clicks(2400), f"{out_dir}/misc/indicator_on.ogg", rms=0.05)
    write_ogg(clicks(1700), f"{out_dir}/misc/indicator_off.ogg", rms=0.05)
    write_ogg(door(True), f"{out_dir}/misc/door_close.ogg", rms=0.18)
    write_ogg(door(False), f"{out_dir}/misc/door_open.ogg", rms=0.12)
    write_ogg(motor(), f"{out_dir}/misc/barrier_motor.ogg", rms=0.1)
    write_ogg(pump_loop(), f"{out_dir}/misc/fuel_pump.ogg", rms=0.08)
    write_ogg(crash(), f"{out_dir}/misc/crash.ogg", rms=0.25)
    write_ogg(beep(), f"{out_dir}/misc/reverse_beep.ogg", rms=0.08)
    write_ogg(clicks(900, 0.08, 0.3), f"{out_dir}/misc/gear_shift.ogg", rms=0.07)
    for nme in ('indicator_on', 'indicator_off', 'door_close', 'door_open', 'barrier_motor', 'fuel_pump', 'crash', 'reverse_beep', 'gear_shift'):
        reg('misc.' + nme, ['misc/' + nme])
    return sj


if __name__ == '__main__':
    import json
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import specs
    out = sys.argv[1]
    sj = build_all(specs.SPECS, out + '/sounds')
    with open(out + '/sounds.json', 'w') as f:
        json.dump(sj, f, indent=1)
    print(len(sj), 'sound events')
