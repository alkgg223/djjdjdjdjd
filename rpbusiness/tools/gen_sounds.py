"""Synthesised sound effects (original), written as mono OGG Vorbis via ffmpeg."""
import os, subprocess, tempfile, wave
import numpy as np

SR = 44100


def env(n, a=0.005, d=0.2, curve=4.0):
    t = np.arange(n) / SR
    att = np.clip(t / max(a, 1e-4), 0, 1)
    dec = np.exp(-curve * np.maximum(0, t - a) / max(d, 1e-4))
    return att * dec


def noise(n, seed=0):
    return np.random.default_rng(seed).uniform(-1, 1, n)


def lowpass(x, alpha):
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += alpha * (x[i] - acc)
        y[i] = acc
    return y


def highpass(x, alpha):
    return x - lowpass(x, alpha)


def sine(freq, n, phase=0):
    t = np.arange(n) / SR
    return np.sin(2 * np.pi * freq * t + phase)


def chop():
    n = int(SR * 0.18)
    thud = sine(110, n) * env(n, 0.001, 0.05, 6)
    click = highpass(noise(n, 1), 0.3) * env(n, 0.0005, 0.02, 8)
    return 0.8 * thud + 0.6 * click


def sizzle():
    n = int(SR * 1.4)
    x = highpass(noise(n, 2), 0.25)
    crackle = (np.random.default_rng(3).random(n) > 0.997) * np.random.default_rng(4).uniform(-1, 1, n)
    e = np.minimum(1, np.arange(n) / (SR * 0.1)) * np.minimum(1, (n - np.arange(n)) / (SR * 0.3))
    return (0.35 * x + 0.9 * lowpass(crackle, 0.6)) * e


def bubble():
    n = int(SR * 1.2)
    out = np.zeros(n)
    rng = np.random.default_rng(5)
    for i in range(9):
        st = rng.integers(0, n - 4000)
        m = 3500
        f0 = rng.uniform(300, 700)
        t = np.arange(m) / SR
        out[st:st + m] += np.sin(2 * np.pi * (f0 + 900 * t) * t) * np.exp(-t * 40) * 0.6
    return out


def pour():
    n = int(SR * 1.6)
    x = lowpass(noise(n, 6), 0.08) * 3
    t = np.arange(n) / SR
    wob = 0.7 + 0.3 * np.sin(2 * np.pi * 7 * t)
    e = np.minimum(1, t / 0.08) * np.minimum(1, (1.6 - t) / 0.3)
    return x * wob * e


def bell():
    n = int(SR * 1.2)
    x = sum(a * sine(1320 * k, n) for k, a in ((1, 1.0), (2.01, 0.4), (3.02, 0.2), (4.2, 0.1)))
    return x * env(n, 0.002, 0.5, 3) * 0.5


def cash_register():
    n = int(SR * 0.9)
    drawer = lowpass(noise(n, 7), 0.3) * env(n, 0.005, 0.12, 5)
    ring = (sine(2093, n) + 0.5 * sine(2637, n) + 0.3 * sine(3136, n)) * env(n, 0.002, 0.45, 3)
    shift = int(SR * 0.08)
    ring = np.concatenate([np.zeros(shift), ring[:-shift]])
    return 0.6 * drawer + 0.45 * ring


def card_beep():
    n = int(SR * 0.45)
    t = np.arange(n) / SR
    x = sine(1800, n) * ((t < 0.12) | ((t > 0.2) & (t < 0.32)))
    return 0.4 * x


def clean():
    n = int(SR * 0.7)
    t = np.arange(n) / SR
    squeak = np.sin(2 * np.pi * (1500 + 600 * np.sin(2 * np.pi * 6 * t)) * t) * 0.25
    rub = highpass(noise(n, 8), 0.2) * 0.3
    e = np.minimum(1, t / 0.05) * np.minimum(1, (0.7 - t) / 0.2)
    return (squeak + rub) * e


def place_food():
    n = int(SR * 0.2)
    return (0.8 * sine(180, n) + 0.3 * lowpass(noise(n, 9), 0.2)) * env(n, 0.001, 0.06, 6)


def fridge_open():
    n = int(SR * 0.5)
    pop = lowpass(noise(n, 10), 0.15) * env(n, 0.002, 0.08, 5) * 2
    hum = sine(120, n) * 0.15 * env(n, 0.05, 0.4, 2)
    return pop + hum


def fridge_close():
    n = int(SR * 0.35)
    return (sine(80, n) * 0.9 + lowpass(noise(n, 11), 0.1) * 1.5) * env(n, 0.001, 0.07, 6)


def eat():
    n = int(SR * 0.3)
    out = np.zeros(n)
    for i, st in enumerate((0, 3000, 6500)):
        m = 3500
        out[st:st + m] += highpass(noise(m, 12 + i), 0.15) * env(m, 0.001, 0.03, 6)
    return out * 0.8


def voice(f0, f1, n):
    t = np.arange(n) / SR
    f = f0 + (f1 - f0) * t / t[-1]
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)
    return lowpass(x, 0.2) * env(n, 0.03, 0.25, 3)


def happy():
    n = int(SR * 0.45)
    return voice(260, 420, n) * 0.6


def unhappy():
    n = int(SR * 0.5)
    return voice(300, 170, n) * 0.6


def tv():
    # muffled programme heard through a TV speaker: a talk show voice over a soft music bed (3.5 s, loops every 70 ticks)
    n = int(SR * 3.5)
    t = np.arange(n) / SR
    rng = np.random.default_rng(42)
    music = np.zeros(n)
    chords = [(220, 277, 330), (196, 247, 294), (175, 220, 262), (196, 247, 294)]
    seg = n // len(chords)
    for i, ch in enumerate(chords):
        m = np.zeros(n)
        for f in ch:
            m[i * seg:(i + 1) * seg] += sine(f, n)[i * seg:(i + 1) * seg]
        music += m * 0.12
    talk = np.zeros(n)
    pos = int(SR * 0.1)
    while pos < n - int(SR * 0.3):
        ln = int(SR * rng.uniform(0.12, 0.28))
        f0 = rng.uniform(150, 260)
        talk[pos:pos + ln] += voice(f0, f0 * rng.uniform(0.8, 1.2), ln) * rng.uniform(0.5, 1.0)
        pos += ln + int(SR * rng.uniform(0.02, 0.15))
    x = music + talk * 0.7 + noise(n, 7) * 0.02
    x = lowpass(highpass(x, 0.05), 0.35)
    fade = np.minimum(1, np.minimum(t / 0.05, (3.5 - t) / 0.05))
    return x * fade


def reception_bell():
    n = int(SR * 1.2)
    x = sine(2093, n) * 0.6 + sine(2637, n) * 0.25 + sine(4186, n) * 0.15
    return x * env(n, 0.001, 0.6, 3)


SOUNDS = {'tv': tv, 'reception_bell': reception_bell, 'chop': chop, 'sizzle': sizzle, 'bubble': bubble, 'pour': pour, 'order_bell': bell, 'cash_register': cash_register,
          'card_beep': card_beep, 'clean': clean, 'place_food': place_food, 'fridge_open': fridge_open, 'fridge_close': fridge_close,
          'eat': eat, 'happy': happy, 'unhappy': unhappy}


def write_ogg(x, path):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1
    x = x / peak * 0.85
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        tmp = f.name
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-ac', '1', '-map_metadata', '-1', '-c:a', 'libvorbis', '-q:a', '5', path], check=True)
    os.unlink(tmp)


def generate(assets, mod='rpbusiness'):
    d = os.path.join(assets, 'sounds')
    os.makedirs(d, exist_ok=True)
    sj = {}
    for name, fn in SOUNDS.items():
        write_ogg(fn(), os.path.join(d, name + '.ogg'))
        sj[name] = {'sounds': [{'name': f'{mod}:{name}'}], 'subtitle': f'subtitles.{mod}.{name}'}
    return sj
