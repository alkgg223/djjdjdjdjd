"""Synthesised Tyrannosaurus voice and body sounds -> mono OGG Vorbis (needs ffmpeg).

A roar is a low glottal pulse train (rough, jittered, with sub-harmonics) shaped by a few vocal-tract formants,
plus breath noise, a throat rattle (amplitude modulation) and a short room reverb.
"""
import os
import subprocess
import sys
import tempfile
import wave
import numpy as np

SR = 44100
rng = np.random.default_rng(7)


def envelope(n, keys):
    t = np.linspace(0, 1, n)
    k = np.array(keys)
    return np.interp(t, k[:, 0], k[:, 1])


def biquad_bandpass(x, f0, q):
    w0 = 2 * np.pi * f0 / SR
    alpha = np.sin(w0) / (2 * q)
    b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    from scipy.signal import lfilter
    return lfilter([b0 / a0, b1 / a0, b2 / a0], [1, a1 / a0, a2 / a0], x)


def lowpass(x, fc):
    from scipy.signal import butter, lfilter
    b, a = butter(2, fc / (SR / 2))
    return lfilter(b, a, x)


def highpass(x, fc):
    from scipy.signal import butter, lfilter
    b, a = butter(2, fc / (SR / 2), btype="high")
    return lfilter(b, a, x)


def glottal(f0, jitter=0.04, sub=0.35):
    """pulse train following the pitch curve f0 (Hz per sample), rough and with a sub-harmonic"""
    n = len(f0)
    f = f0 * (1 + jitter * lowpass(rng.standard_normal(n), 30) * 3)
    phase = np.cumsum(f / SR)
    saw = 2 * (phase % 1.0) - 1
    pulse = np.sign(np.sin(2 * np.pi * phase)) * 0.25 + saw * 0.75
    subh = np.sin(np.pi * phase) * sub
    return pulse + subh


def vocal(f0, formants, rattle_hz=0.0, rattle=0.0, breath=0.25):
    src = glottal(f0)
    n = len(src)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    for fc, q, g in formants:
        out += g * biquad_bandpass(src + breath * noise, fc, q)
    if rattle:
        t = np.arange(n) / SR
        out *= 1 - rattle * (0.5 + 0.5 * np.sin(2 * np.pi * rattle_hz * t + 2 * lowpass(rng.standard_normal(n), 8)))
    return out


def reverb(x, decay=0.35, length=0.9):
    n = int(length * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (decay * SR))
    ir = lowpass(ir, 3500)
    ir[0] = 1.0 / 0.15
    from scipy.signal import fftconvolve
    y = fftconvolve(x, ir)[: len(x) + n // 2]
    return y


def finish(x, peak=0.92):
    x = highpass(x, 28)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fade = int(0.04 * SR)
    x[-fade:] *= np.linspace(1, 0, fade)
    return x


def roar(dur=2.8, base=78, seed=0):
    n = int(dur * SR)
    pitch = envelope(n, [(0, 0.75), (0.12, 1.15), (0.35, 1.3), (0.7, 1.2), (0.9, 0.85), (1, 0.6)]) * base
    form = [(260, 3.0, 1.0), (620, 4.0, 0.8), (1250, 5.0, 0.45), (2600, 6.0, 0.18)]
    v = vocal(pitch, form, rattle_hz=34, rattle=0.55, breath=0.35)
    amp = envelope(n, [(0, 0), (0.06, 0.55), (0.2, 1.0), (0.75, 0.9), (0.92, 0.35), (1, 0)])
    growl = vocal(pitch * 0.5, [(160, 2.0, 1.0), (420, 3.0, 0.5)], rattle_hz=18, rattle=0.7, breath=0.2)
    x = v * amp + 0.6 * growl * amp
    return finish(reverb(x))


def growl(dur=1.6, base=58):
    n = int(dur * SR)
    pitch = envelope(n, [(0, 0.9), (0.4, 1.05), (1, 0.85)]) * base
    v = vocal(pitch, [(180, 2.5, 1.0), (480, 3.5, 0.6), (950, 4.0, 0.25)], rattle_hz=22, rattle=0.75, breath=0.3)
    amp = envelope(n, [(0, 0), (0.15, 0.8), (0.6, 1.0), (1, 0)])
    return finish(reverb(v * amp, 0.25, 0.6), 0.8)


def hurt(dur=0.75, base=110):
    n = int(dur * SR)
    pitch = envelope(n, [(0, 1.4), (0.2, 1.6), (1, 0.9)]) * base
    v = vocal(pitch, [(330, 3.0, 1.0), (780, 4.0, 0.9), (1600, 5.0, 0.4)], rattle_hz=40, rattle=0.4, breath=0.3)
    amp = envelope(n, [(0, 0), (0.05, 1.0), (0.4, 0.8), (1, 0)])
    return finish(reverb(v * amp, 0.2, 0.5))


def death(dur=2.6, base=72):
    n = int(dur * SR)
    pitch = envelope(n, [(0, 1.3), (0.25, 1.1), (0.7, 0.7), (1, 0.45)]) * base
    v = vocal(pitch, [(240, 3.0, 1.0), (560, 4.0, 0.7), (1100, 5.0, 0.3)], rattle_hz=26, rattle=0.6, breath=0.45)
    amp = envelope(n, [(0, 0), (0.05, 1.0), (0.5, 0.75), (0.85, 0.3), (1, 0)])
    return finish(reverb(v * amp, 0.4, 1.0))


def bite(dur=0.45):
    n = int(dur * SR)
    t = np.arange(n) / SR
    clack = np.exp(-t / 0.012) * rng.standard_normal(n)
    clack = biquad_bandpass(clack, 1800, 1.2) * 1.5 + biquad_bandpass(clack, 420, 1.5)
    crunch = rng.standard_normal(n) * np.exp(-((t - 0.06) / 0.05) ** 2) * (rng.random(n) > 0.6)
    crunch = biquad_bandpass(crunch, 2600, 1.0)
    thump = np.sin(2 * np.pi * 70 * t) * np.exp(-t / 0.06)
    return finish(reverb(clack + 0.6 * crunch + 0.8 * thump, 0.12, 0.3), 0.85)


def step(dur=0.7, seed=0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    boom = np.sin(2 * np.pi * (48 + 20 * np.exp(-t / 0.05)) * t) * np.exp(-t / 0.16)
    dirt = lowpass(rng.standard_normal(n), 900) * np.exp(-t / 0.05)
    rattle = biquad_bandpass(rng.standard_normal(n), 3000, 2.0) * np.exp(-t / 0.03) * 0.3
    return finish(reverb(1.3 * boom + 0.7 * dirt + rattle, 0.18, 0.5), 0.8)


def write_ogg(x, path):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tf:
        wav = tf.name
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-map_metadata", "-1", "-c:a", "libvorbis",
                    "-q:a", "4", path], check=True)
    os.unlink(wav)


def export(res_root):
    d = os.path.join(res_root, "assets/arkragnarok/sounds/rex")
    os.makedirs(d, exist_ok=True)
    made = {}
    for i, b in enumerate((76, 70, 84)):
        write_ogg(roar(2.8, b), os.path.join(d, "roar%d.ogg" % (i + 1)))
    for i, b in enumerate((56, 62)):
        write_ogg(growl(1.6, b), os.path.join(d, "growl%d.ogg" % (i + 1)))
    for i, b in enumerate((108, 122)):
        write_ogg(hurt(0.75, b), os.path.join(d, "hurt%d.ogg" % (i + 1)))
    write_ogg(death(), os.path.join(d, "death.ogg"))
    write_ogg(bite(), os.path.join(d, "bite.ogg"))
    for i in range(3):
        write_ogg(step(0.7, i), os.path.join(d, "step%d.ogg" % (i + 1)))
    sounds = {
        "rex.roar": {"subtitle": "subtitles.arkragnarok.rex.roar", "sounds": [{"name": "arkragnarok:rex/roar%d" % i, "attenuation_distance": 64} for i in (1, 2, 3)]},
        "rex.growl": {"subtitle": "subtitles.arkragnarok.rex.growl", "sounds": [{"name": "arkragnarok:rex/growl%d" % i, "attenuation_distance": 32} for i in (1, 2)]},
        "rex.hurt": {"subtitle": "subtitles.arkragnarok.rex.hurt", "sounds": ["arkragnarok:rex/hurt1", "arkragnarok:rex/hurt2"]},
        "rex.death": {"subtitle": "subtitles.arkragnarok.rex.death", "sounds": [{"name": "arkragnarok:rex/death", "attenuation_distance": 48}]},
        "rex.bite": {"subtitle": "subtitles.arkragnarok.rex.bite", "sounds": ["arkragnarok:rex/bite"]},
        "rex.step": {"subtitle": "subtitles.arkragnarok.rex.step", "sounds": [{"name": "arkragnarok:rex/step%d" % i, "attenuation_distance": 32} for i in (1, 2, 3)]},
    }
    import json
    with open(os.path.join(res_root, "assets/arkragnarok/sounds.json"), "w") as fh:
        json.dump(sounds, fh, indent=1)


if __name__ == "__main__":
    export(sys.argv[1])
