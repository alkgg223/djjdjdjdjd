"""Procedural sound synthesis for Real Weather.

Writes mono OGG Vorbis files into src/main/resources/assets/realweather/sounds/.
Thunder is built from a physically motivated model: a tortuous lightning channel
made of short segments, each radiating an N-wave that reaches the listener after
r/343 seconds, with distance dependent air absorption and terrain echoes.
"""
import os, subprocess, sys, tempfile
import numpy as np
import scipy.signal as ss
from scipy.io import wavfile

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'realweather', 'sounds')


def sos(kind, f, order=2):
    return ss.butter(order, f, btype=kind, fs=SR, output='sos')


def filt(x, kind, f, order=2):
    return ss.sosfilt(sos(kind, f, order), x)


def white(rng, n):
    return rng.standard_normal(n)


def pink(rng, n):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    spec /= np.sqrt(f)
    x = np.fft.irfft(spec, n)
    return x / np.std(x)


def brown(rng, n):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    spec /= f
    spec[f < 15] = 0
    x = np.fft.irfft(spec, n)
    return x / np.std(x)


def ctrl(rng, n, rate, lo=0.0, hi=1.0, smooth=3):
    """Smooth random control signal with about `rate` changes per second."""
    k = max(4, int(n / SR * rate) + 4)
    pts = rng.uniform(lo, hi, k)
    for _ in range(smooth):
        pts = np.convolve(pts, [0.25, 0.5, 0.25], mode='same')
    xs = np.linspace(0, k - 1, n)
    from scipy.interpolate import CubicSpline
    return np.clip(CubicSpline(np.arange(k), pts)(xs), lo, hi)


def impulses(rng, n, rate, alpha=2.2, amp_cap=1.0):
    """Poisson impulse train with power-law amplitudes (many small, few large)."""
    count = rng.poisson(rate * n / SR)
    pos = rng.integers(0, n, count)
    amp = np.minimum((rng.pareto(alpha, count) + 1) * 0.12, amp_cap)
    amp *= rng.choice([-1, 1], count)
    x = np.zeros(n)
    np.add.at(x, pos, amp)
    return x


def burst(rng, ms, lo, hi, decay_ms):
    m = int(SR * ms / 1000)
    t = np.arange(m) / SR
    k = rng.standard_normal(m) * np.exp(-t / (decay_ms / 1000))
    k = filt(k, 'bandpass', [lo, min(hi, SR / 2 - 100)], 2)
    return k / (np.max(np.abs(k)) + 1e-9)


def plip(rng, f0, decay_ms, chirp=0.6):
    """Bubble resonance of a drop hitting water: a damped rising sine."""
    m = int(SR * decay_ms * 5 / 1000)
    t = np.arange(m) / SR
    f = f0 * (1 + chirp * t / (decay_ms / 1000) * 0.2)
    ph = 2 * np.pi * np.cumsum(f) / SR
    env = np.exp(-t / (decay_ms / 1000)) * (1 - np.exp(-t / 0.0004))
    return np.sin(ph) * env


def drops(rng, n, rate, kernels, alpha=2.2):
    out = np.zeros(n)
    per = rate / len(kernels)
    for k in kernels:
        out += ss.fftconvolve(impulses(rng, n, per, alpha), k)[:n]
    return out


def loopify(x, xf):
    """Turn x (length L + xf) into a seamless loop of length L."""
    L = len(x) - xf
    out = x[:L].copy()
    t = np.linspace(0, 1, xf)
    out[:xf] = x[:xf] * np.sqrt(t) + x[L:L + xf] * np.sqrt(1 - t)
    return out


def rms_norm(x, db):
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12) * 10 ** (db / 20)


def peak_norm(x, db=-1.0):
    return x / (np.max(np.abs(x)) + 1e-12) * 10 ** (db / 20)


def limit(x, ceiling=0.97):
    # Soft limiter to keep peaks in range without hard clipping.
    return np.tanh(x / ceiling) * ceiling


def compress(x, threshold, ratio):
    """Envelope compressor: keeps the rumble audible after the loudest crack."""
    x = peak_norm(x, 0.0)
    env = np.sqrt(ss.sosfilt(sos('lowpass', 12, 1), x ** 2).clip(1e-12))
    gain = np.where(env > threshold, (env / threshold) ** (1.0 / ratio - 1.0), 1.0)
    return x * gain


def fade(x, fin=0.0, fout=0.0):
    x = x.copy()
    a = int(fin * SR)
    b = int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a) ** 2
    if b:
        x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


def write(name, x, q=5):
    os.makedirs(OUT, exist_ok=True)
    x = np.clip(x, -1, 1)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        wavfile.write(tf.name, SR, (x * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tf.name, '-ac', '1', '-map_metadata', '-1',
                        '-c:a', 'libvorbis', '-q:a', str(q), os.path.join(OUT, name + '.ogg')], check=True)
        os.unlink(tf.name)
    print('wrote', name, '%.1fs' % (len(x) / SR), 'rms %.1f dB' % (20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)))


# ---------------------------------------------------------------- rain

def rain(seed, heavy):
    rng = np.random.default_rng(seed)
    L, xf = 14 * SR, 2 * SR
    n = L + xf
    gust = ctrl(rng, n, 0.35, 0.65, 1.0)
    # Bed: countless distant drops merge into a pink hiss with a soft peak around 3-6 kHz.
    bed = pink(rng, n)
    bed = filt(bed, 'bandpass', [250, 14000], 2) * 0.6 + filt(white(rng, n), 'bandpass', [2500, 9000], 2) * 0.35
    # Near drops: short ticks on leaves, grass and ground.
    ticks = [burst(rng, 6, lo, lo * rng.uniform(2.5, 5), rng.uniform(0.6, 2.2)) for lo in rng.uniform(900, 5000, 18)]
    # Drops into puddles: bubble "plips".
    plips = [plip(rng, f, rng.uniform(4, 14)) * 0.8 for f in rng.uniform(1300, 4200, 14)]
    # Splatter of larger drops on hard ground.
    thuds = [burst(rng, 25, 120, rng.uniform(600, 1500), rng.uniform(3, 8)) for _ in range(6)]
    if heavy:
        x = rms_norm(bed, -21)
        x += rms_norm(drops(rng, n, 2600, ticks), -24)
        x += rms_norm(drops(rng, n, 160, plips, 2.6), -31)
        x += rms_norm(drops(rng, n, 700, thuds), -27)
        x += rms_norm(filt(brown(rng, n), 'lowpass', 400), -30)  # downpour body
        x *= gust
    else:
        x = rms_norm(bed, -31)
        x += rms_norm(drops(rng, n, 420, ticks, 2.0), -27)
        x += rms_norm(drops(rng, n, 45, plips, 2.4), -31)
        x += rms_norm(drops(rng, n, 60, thuds), -34)
        x *= 0.85 + 0.15 * gust
    x = filt(x, 'highpass', 70)
    return limit(rms_norm(loopify(x, xf), -19 if heavy else -22))


def rain_roof(seed):
    """Rain heard from inside a house: a muffled drumming on the roof plus gutter drips."""
    rng = np.random.default_rng(seed)
    L, xf = 14 * SR, 2 * SR
    n = L + xf
    gust = ctrl(rng, n, 0.3, 0.6, 1.0)
    taps = []
    for _ in range(14):
        k = burst(rng, 40, 90, rng.uniform(380, 900), rng.uniform(5, 14))
        # Plank resonance.
        t = np.arange(len(k)) / SR
        k += np.sin(2 * np.pi * rng.uniform(140, 260) * t) * np.exp(-t / 0.012) * 0.6
        taps.append(k / np.max(np.abs(k)))
    x = rms_norm(drops(rng, n, 1800, taps, 2.4), -24)
    x += rms_norm(filt(pink(rng, n), 'lowpass', 900, 3), -27)
    x *= gust
    # Gutter drips with a little room echo.
    dr = np.zeros(n)
    tpos = 0.0
    while True:
        tpos += rng.exponential(0.55)
        i = int(tpos * SR)
        if i >= n - SR:
            break
        k = plip(rng, rng.uniform(900, 1900), rng.uniform(15, 35), 1.0)
        dr[i:i + len(k)] += k * rng.uniform(0.3, 1.0)
    room = np.exp(-np.arange(int(0.35 * SR)) / SR / 0.08) * rng.standard_normal(int(0.35 * SR))
    dr = dr + 0.25 * ss.fftconvolve(dr, room / np.sum(np.abs(room)) * 30)[:n]
    x += rms_norm(dr, -33)
    x = filt(x, 'lowpass', 2600, 2)
    x = filt(x, 'highpass', 50)
    return limit(rms_norm(loopify(x, xf), -21))


# ---------------------------------------------------------------- wind family

def whistles(rng, n, centers, q, level_ctrl):
    out = np.zeros(n)
    for c in centers:
        bw = c / q
        band = filt(white(rng, n), 'bandpass', [c - bw / 2, c + bw / 2], 2)
        env = ctrl(rng, n, 0.25, 0, 1) ** 3
        out += band / np.std(band) * env
    return out * level_ctrl


def wind(seed):
    rng = np.random.default_rng(seed)
    L, xf = 22 * SR, 3 * SR
    n = L + xf
    g = ctrl(rng, n, 0.22, 0.15, 1.0) ** 1.4
    low = rms_norm(filt(brown(rng, n), 'lowpass', 260, 3), -20)
    mid = rms_norm(filt(pink(rng, n), 'bandpass', [260, 950], 2), -24)
    hi = rms_norm(filt(pink(rng, n), 'bandpass', [950, 3200], 2), -32)
    x = low * (0.45 + 0.55 * g) + mid * g ** 1.3 + hi * g ** 2.2
    x += rms_norm(whistles(rng, n, rng.uniform(380, 820, 7), 22, g ** 2), -31)
    x = filt(x, 'highpass', 30)
    return limit(rms_norm(loopify(x, xf), -20))


def blizzard(seed):
    rng = np.random.default_rng(seed)
    L, xf = 22 * SR, 3 * SR
    n = L + xf
    g = ctrl(rng, n, 0.35, 0.45, 1.0) ** 1.2
    roar = rms_norm(filt(brown(rng, n), 'lowpass', 320, 3), -17)
    body = rms_norm(filt(pink(rng, n), 'bandpass', [300, 1400], 2), -21)
    howl = rms_norm(whistles(rng, n, rng.uniform(450, 1350, 11), 28, g ** 1.5), -23)
    # Ice crystals pelting everything.
    hiss = filt(white(rng, n), 'highpass', 4500, 2) * ctrl(rng, n, 2.0, 0.5, 1.0)
    grains = filt(impulses(rng, n, 9000, 2.8), 'highpass', 3500, 2)
    x = roar * (0.6 + 0.4 * g) + body * g + howl + rms_norm(hiss, -31) * g + rms_norm(grains, -33) * g
    x = filt(x, 'highpass', 28)
    return limit(rms_norm(loopify(x, xf), -17))


def sandstorm(seed):
    rng = np.random.default_rng(seed)
    L, xf = 22 * SR, 3 * SR
    n = L + xf
    g = ctrl(rng, n, 0.4, 0.4, 1.0) ** 1.3
    roar = rms_norm(filt(brown(rng, n), 'lowpass', 230, 3), -16)
    body = rms_norm(filt(pink(rng, n), 'bandpass', [230, 1100], 2), -21)
    # Sand: a dense crackle of grains hitting the ground and the listener.
    grains = drops(rng, n, 26000, [burst(rng, 1.2, lo, lo * 3, 0.15) for lo in rng.uniform(2200, 6000, 8)], 3.0)
    sand = rms_norm(grains, -22) * g ** 1.6
    hiss = rms_norm(filt(white(rng, n), 'bandpass', [2500, 10000], 2), -27) * g ** 1.4
    howl = rms_norm(whistles(rng, n, rng.uniform(300, 700, 6), 15, g ** 2), -29)
    x = roar * (0.55 + 0.45 * g) + body * g + sand + hiss + howl
    x = filt(x, 'highpass', 28)
    return limit(rms_norm(loopify(x, xf), -17))


# ---------------------------------------------------------------- thunder

def channel(rng):
    """Random tortuous lightning channel: cloud-to-ground leader plus in-cloud branches."""
    pts = [np.zeros(3)]
    p = np.zeros(3)
    d = np.array([0.0, 0.0, 1.0])
    top = rng.uniform(1500, 2800)
    while p[2] < top:
        d = d + rng.normal(0, 0.55, 3)
        d[2] = abs(d[2]) + 0.35
        d /= np.linalg.norm(d)
        p = p + d * rng.uniform(4, 14)
        pts.append(p.copy())
    segs = [np.array(pts)]
    # Horizontal spider channels inside the cloud carry much of the rumble.
    for _ in range(rng.integers(2, 5)):
        q = segs[0][rng.integers(len(segs[0]) * 2 // 3, len(segs[0]))].copy()
        h = rng.normal(0, 1, 2)
        h /= np.linalg.norm(h)
        d = np.array([h[0], h[1], 0.0])
        bp = [q.copy()]
        for _ in range(rng.integers(150, 420)):
            d = d + rng.normal(0, 0.5, 3)
            d[2] *= 0.3
            d /= np.linalg.norm(d)
            q = q + d * rng.uniform(4, 14)
            bp.append(q.copy())
        segs.append(np.array(bp))
    return segs


def nwave(tau_s):
    m = max(3, int(tau_s * SR))
    return np.linspace(1, -1, m)


def thunder(seed, dist, length):
    rng = np.random.default_rng(seed)
    segs = channel(rng)
    az = rng.uniform(0, 2 * np.pi)
    lis = np.array([dist * np.cos(az), dist * np.sin(az), 1.7])
    mids, dirs, lens = [], [], []
    for s in segs:
        a, b = s[:-1], s[1:]
        v = b - a
        ln = np.linalg.norm(v, axis=1)
        mids.append((a + b) / 2)
        dirs.append(v / ln[:, None])
        lens.append(ln)
    mids, dirs, lens = np.concatenate(mids), np.concatenate(dirs), np.concatenate(lens)
    rv = lis - mids
    r = np.linalg.norm(rv, axis=1)
    cosang = np.abs(np.sum(dirs * rv, axis=1) / r)
    sin2 = 1 - cosang ** 2
    # Each segment radiates mostly perpendicular to itself (line source), with spherical spreading.
    # A long channel acts like a line source: closer to cylindrical spreading than 1/r.
    amp = lens * (0.08 + sin2 ** 3) / r ** 0.65 * rng.uniform(0.6, 1.4, len(r))
    t = r / 343.0
    t -= t.min()
    t += 0.04
    n = int(length * SR)
    tau = 0.004 * (r / 100.0) ** 0.25 * rng.uniform(0.6, 1.6, len(r))
    out = np.zeros(n)
    # Group by distance so each group gets the matching air absorption low-pass.
    edges = np.geomspace(max(1.0, r.min() * 0.999), r.max() * 1.001, 14)
    taus = np.geomspace(tau.min(), tau.max() + 1e-6, 10)
    for i in range(len(edges) - 1):
        sel = (r >= edges[i]) & (r < edges[i + 1])
        if not np.any(sel):
            continue
        band = np.zeros(n)
        ti = np.digitize(tau[sel], taus)
        for k in np.unique(ti):
            s2 = np.where(sel)[0][ti == k]
            imp = np.zeros(n)
            idx = (t[s2] * SR).astype(int)
            ok = idx < n
            np.add.at(imp, idx[ok], amp[s2][ok])
            tk = taus[min(k, len(taus) - 1)]
            band += ss.fftconvolve(imp, nwave(tk))[:n]
        rm = np.sqrt(edges[i] * edges[i + 1])
        fc = float(np.clip(14000 * (60.0 / rm) ** 0.85, 60, 15000))
        band = filt(band, 'lowpass', fc, 2)
        out += band
    # Echoes from the ground, hills and the cloud deck.
    ir_len = int(min(length, 4.5) * SR)
    ti = np.arange(ir_len) / SR
    ir = rng.standard_normal(ir_len) * np.exp(-ti / rng.uniform(0.7, 1.3))
    ir = filt(ir, 'lowpass', 900, 2)
    ir[0] = 0
    wet = ss.fftconvolve(out, ir)[:n]
    out = out + rms_norm(wet, 20 * np.log10(np.sqrt(np.mean(out ** 2)) + 1e-12) - (4 if dist < 500 else 1))
    out = filt(out, 'highpass', 22, 2)
    # Body: the low boom that follows the crack, felt more than heard.
    boom = filt(out, 'lowpass', 120, 2)
    out = out + boom * (1.8 if dist < 500 else 1.0)
    out = compress(out, 0.18, 3.0)
    out = peak_norm(out, -0.5)
    if dist < 500:
        out = np.tanh(out * 1.6) / np.tanh(1.6)
    out = fade(out, 0.0, min(2.5, length * 0.3))
    return peak_norm(out, -1.0)


def main():
    if len(sys.argv) > 1 and sys.argv[1] == 'thunder':
        return thunders()
    write('rain_light', rain(11, False))
    write('rain_heavy', rain(12, True))
    write('rain_roof', rain_roof(13))
    write('wind', wind(21))
    write('blizzard', blizzard(22))
    write('sandstorm', sandstorm(23))
    thunders()


def thunders():
    for i, (d, ln) in enumerate([(180, 9), (260, 10), (120, 8)]):
        write('thunder_close%d' % (i + 1), thunder(100 + i, d, ln))
    for i, (d, ln) in enumerate([(1300, 11), (2000, 12), (1600, 11)]):
        write('thunder_mid%d' % (i + 1), thunder(200 + i, d, ln))
    for i, (d, ln) in enumerate([(5500, 12), (8000, 13), (6500, 12)]):
        write('thunder_far%d' % (i + 1), thunder(300 + i, d, ln))


if __name__ == '__main__':
    main()
