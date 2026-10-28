"""Procedural sound effects -> mono OGG Vorbis + sounds.json"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy import signal

SR = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
OUT = os.path.join(ROOT, "sounds")


def t(sec):
    return np.arange(int(SR * sec)) / SR


def noise(sec, rng):
    return rng.standard_normal(int(SR * sec))


def lp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, "low", fs=SR, output="sos"), x)


def hp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, "high", fs=SR, output="sos"), x)


def bp(x, f0, f1, order=2):
    return signal.sosfilt(signal.butter(order, [f0, f1], "band", fs=SR, output="sos"), x)


def env_exp(n, tau, attack=0.0005):
    tt = np.arange(n) / SR
    a = np.clip(tt / max(attack, 1e-6), 0, 1)
    return a * np.exp(-tt / tau)


def pad(x, n):
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros(n - len(x))])


def place(dst, src, at):
    i = int(at * SR)
    j = min(len(dst), i + len(src))
    if i < len(dst):
        dst[i:j] += src[:j - i]
    return dst


def reverb(x, decay=0.6, lpf=3000, mix=0.35, rng=None, predelay=0.02):
    rng = rng or np.random.default_rng(1)
    n = int(SR * decay * 1.5)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / (decay / 3))
    ir = lp(ir, lpf)
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    wet = signal.fftconvolve(x, ir)[: len(x) + len(ir)]
    wet /= np.max(np.abs(wet)) + 1e-9
    dry = pad(x, len(wet))
    return dry * (1 - mix) + wet * mix * np.max(np.abs(x))


def norm(x, peak=0.95):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def sat(x, drive=2.0):
    return np.tanh(x * drive) / np.tanh(drive)


def fade(x, fin=0.002, fout=0.05):
    n = len(x)
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def write_ogg(path, x):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.clip(x, -1, 1)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        w = wave.open(tmp.name, "wb")
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
        w.close()
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-ac", "1", "-map_metadata", "-1",
                        "-c:a", "libvorbis", "-q:a", "5", path], check=True)
        os.unlink(tmp.name)


# ----------------------------------------------------------------------------------- gunshots
def gunshot(rng, crack=1.0, body_f=900, body_tau=0.07, thump_f=80, thump=0.8, tail=0.5, tail_f=2200,
            echo=0.0, mech=0.25, drive=2.5, length=None):
    L = length or (0.15 + tail * 1.6)
    n = int(SR * L)
    out = np.zeros(n)
    # supersonic crack / muzzle blast transient
    c = hp(noise(0.012, rng), 1800) * env_exp(int(0.012 * SR), 0.0025)
    place(out, c * crack * 1.4, 0.0)
    # body
    b = lp(noise(0.6, rng), body_f, 2) * env_exp(int(0.6 * SR), body_tau, 0.0008)
    place(out, b * 1.6, 0.0)
    # low thump with pitch drop
    tt = t(0.35)
    f = thump_f * (1 + 1.5 * np.exp(-tt / 0.01))
    ph = 2 * np.pi * np.cumsum(f) / SR
    th = np.sin(ph) * env_exp(len(tt), 0.07, 0.001)
    place(out, th * thump * 1.2, 0.0)
    # mechanical action
    if mech:
        m = bp(noise(0.02, rng), 2500, 6000) * env_exp(int(0.02 * SR), 0.003)
        place(out, m * mech, 0.035 + rng.uniform(0, 0.01))
    out = sat(out, drive)
    # outdoor tail
    if tail > 0:
        out = reverb(out, decay=tail, lpf=tail_f, mix=0.45, rng=rng)
    if echo:
        e = lp(out, 1200) * echo
        out = pad(out, len(out) + int(0.25 * SR))
        out = place(out, e, 0.22)
    out = pad(out, int(SR * L))
    return fade(norm(out), 0.0005, 0.08)


GUNSHOTS = {
    "shot_pistol": dict(crack=0.9, body_f=1100, body_tau=0.05, thump_f=95, thump=0.6, tail=0.35),
    "shot_magnum": dict(crack=1.2, body_f=650, body_tau=0.11, thump_f=70, thump=1.1, tail=0.75, drive=3.5),
    "shot_smg": dict(crack=0.7, body_f=1300, body_tau=0.04, thump_f=100, thump=0.5, tail=0.25, mech=0.35),
    "shot_rifle": dict(crack=1.1, body_f=750, body_tau=0.08, thump_f=80, thump=0.9, tail=0.6, drive=3.0),
    "shot_carbine": dict(crack=1.2, body_f=1000, body_tau=0.065, thump_f=85, thump=0.8, tail=0.5),
    "shot_shotgun": dict(crack=0.8, body_f=520, body_tau=0.14, thump_f=60, thump=1.3, tail=0.8, drive=3.5),
    "shot_sniper": dict(crack=1.6, body_f=520, body_tau=0.13, thump_f=55, thump=1.2, tail=1.2, echo=0.35, drive=3.0),
    "shot_heavy": dict(crack=1.6, body_f=380, body_tau=0.18, thump_f=45, thump=1.5, tail=1.5, echo=0.4, drive=4.0),
    "shot_lmg": dict(crack=1.1, body_f=800, body_tau=0.07, thump_f=75, thump=0.9, tail=0.45, mech=0.4),
    "shot_minigun": dict(crack=0.7, body_f=900, body_tau=0.03, thump_f=90, thump=0.6, tail=0.15, mech=0.0, length=0.22),
    "shot_turret": dict(crack=1.4, body_f=420, body_tau=0.12, thump_f=50, thump=1.3, tail=0.8, drive=3.5),
}


def suppressed(rng):
    n = int(SR * 0.35)
    out = np.zeros(n)
    b = lp(noise(0.3, rng), 700, 2) * env_exp(int(0.3 * SR), 0.035, 0.002)
    place(out, b, 0)
    place(out, bp(noise(0.03, rng), 2000, 5000) * env_exp(int(0.03 * SR), 0.004) * 0.8, 0.02)
    place(out, bp(noise(0.03, rng), 1500, 4000) * env_exp(int(0.03 * SR), 0.004) * 0.6, 0.06)
    return fade(norm(reverb(out, 0.25, 1500, 0.2, rng)), 0.001, 0.05)


def thoomp(rng):
    tt = t(0.6)
    f = 70 * (1 + 0.8 * np.exp(-tt / 0.03))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(len(tt), 0.09, 0.002)
    s += lp(noise(0.6, rng), 400) * env_exp(len(tt), 0.06, 0.002) * 0.8
    s += bp(noise(0.6, rng), 800, 2500) * env_exp(len(tt), 0.01) * 0.3
    return fade(norm(reverb(sat(s, 2), 0.5, 1500, 0.3, rng)), 0.001, 0.1)


def rocket_launch(rng):
    L = 1.8
    n = int(SR * L)
    out = np.zeros(n)
    place(out, gunshot(rng, crack=1.0, body_f=500, body_tau=0.1, thump_f=60, thump=1.2, tail=0.3)[: int(0.5 * SR)], 0)
    # whoosh: band noise sweeping up then fading
    x = noise(L, rng)
    k = np.linspace(0, 1, n)
    sweep = np.zeros(n)
    for i, (f0, f1) in enumerate(((300, 900), (900, 2500), (2500, 5000))):
        sweep += bp(x, f0, f1) * np.exp(-((k - 0.15 - i * 0.1) ** 2) / 0.05)
    roar = lp(noise(L, rng), 900) * np.exp(-k * 2.2) * np.clip(k * 20, 0, 1)
    out += sweep * 0.8 + roar * 0.9
    return fade(norm(sat(out, 2)), 0.001, 0.3)


def flame_loop(rng):
    L = 2.0
    n = int(SR * L)
    x = lp(noise(L + 0.5, rng), 1600, 2)
    x += bp(noise(L + 0.5, rng), 150, 400) * 1.5
    mod = 1 + 0.25 * lp(noise(L + 0.5, rng), 6, 1) * 20
    x *= mod
    # crackles
    for _ in range(60):
        at = rng.uniform(0, L + 0.4)
        c = hp(noise(0.006, rng), 3000) * env_exp(int(0.006 * SR), 0.0015) * rng.uniform(0.5, 2)
        place(x, c, at)
    x = sat(norm(x), 1.5)
    # seamless loop: crossfade tail into head
    xf = int(0.5 * SR)
    body = x[:n].copy()
    tail = x[n:n + xf]
    w = np.linspace(0, 1, xf)
    body[:xf] = body[:xf] * w + tail * (1 - w)
    return norm(body, 0.85)


def engine_loop(rng, rpm_hz, rough=0.3, noise_amt=0.35, L=2.0):
    n = int(SR * L)
    tt = np.arange(n) / SR
    # integer number of cycles for a seamless loop
    f = round(rpm_hz * L) / L
    phase = 2 * np.pi * f * tt
    x = np.zeros(n)
    for h, a in ((1, 1.0), (2, 0.6), (3, 0.35), (4, 0.2), (6, 0.12)):
        x += a * np.sin(h * phase + rng.uniform(0, 6.28))
    pulses = (np.sin(phase) > 0.6).astype(float)
    x += lp(pulses * rng.standard_normal(n), 900) * rough * 3
    x += lp(rng.standard_normal(n), 500) * noise_amt * 2
    x = lp(x, 2500)
    # loop-safe: make the noise components periodic by crossfading with a rolled copy
    xf = int(0.3 * SR)
    w = np.linspace(0, 1, xf)
    x[:xf] = x[:xf] * w + x[-xf:] * (1 - w)
    x = x[: n]
    return norm(sat(norm(x), 1.3), 0.8)


def click(rng, f0=2500, f1=7000, tau=0.003, amp=1.0, L=0.03):
    return bp(noise(L, rng), f0, f1) * env_exp(int(L * SR), tau) * amp


def thunk(rng, f=180, tau=0.03, amp=1.0):
    tt = t(0.15)
    return (np.sin(2 * np.pi * f * tt) * env_exp(len(tt), tau, 0.001) + lp(noise(0.15, rng), 800) * env_exp(len(tt), tau * 0.6) * 0.5) * amp


def scrape(rng, L=0.12, f0=1500, f1=5000, amp=0.4):
    n = int(L * SR)
    e = np.sin(np.linspace(0, np.pi, n))
    return bp(noise(L, rng), f0, f1) * e * amp


def compose(parts, L):
    out = np.zeros(int(SR * L))
    for s, at in parts:
        place(out, s, at)
    return fade(norm(out, 0.9), 0.001, 0.03)


def siren(L=1.2):
    tt = t(L)
    out = np.zeros(len(tt))
    for i in range(3):
        s = int(i * 0.4 * SR)
        e = int(s + 0.22 * SR)
        seg = tt[: e - s]
        out[s:e] += signal.square(2 * np.pi * 880 * seg, 0.5) * 0.4 + np.sin(2 * np.pi * 1760 * seg) * 0.3
        out[s:e] *= np.minimum(1, np.minimum(np.arange(e - s) / 200, (e - s - np.arange(e - s)) / 400))
    return norm(lp(out, 5000), 0.8)


def beep(f=1200, L=0.09):
    tt = t(L)
    x = np.sin(2 * np.pi * f * tt) * np.minimum(1, np.minimum(np.arange(len(tt)) / 100, (len(tt) - np.arange(len(tt))) / 300))
    return norm(x, 0.7)


def horn():
    tt = t(0.7)
    x = signal.sawtooth(2 * np.pi * 330 * tt) * 0.5 + signal.sawtooth(2 * np.pi * 415 * tt) * 0.5
    x = lp(x, 2500) * np.minimum(1, np.minimum(np.arange(len(tt)) / 500, (len(tt) - np.arange(len(tt))) / 2000))
    return norm(x, 0.8)


def missile_launch(rng):
    L = 4.0
    n = int(SR * L)
    k = np.arange(n) / SR
    roar = lp(noise(L, rng), 700, 2) * 1.2 + bp(noise(L, rng), 700, 2500) * 0.6
    envl = np.clip(k / 0.15, 0, 1) * np.exp(-np.maximum(0, k - 0.8) / 1.2)
    out = roar * envl
    place(out, gunshot(rng, crack=1.0, body_f=300, body_tau=0.2, thump_f=40, thump=1.5, tail=0.6)[: int(1.0 * SR)] * 1.2, 0)
    out *= 1 + 0.15 * np.sin(2 * np.pi * 7 * k)
    return fade(norm(sat(out, 1.8)), 0.001, 0.5)


def explosion_big(rng, L=4.5):
    """close heavy blast: sharp crack, sub-bass thump, roaring rumble and falling debris crackle"""
    n = int(SR * L)
    k = np.arange(n) / SR
    out = np.zeros(n)
    f = 38 * (1 + 1.6 * np.exp(-k / 0.05))
    out += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / 0.45) * 1.6
    out += lp(noise(L, rng), 300, 2) * np.exp(-k / 0.9) * np.clip(k / 0.01, 0, 1) * 2.0
    out += bp(noise(L, rng), 300, 1800) * np.exp(-k / 0.35) * 1.0
    out += hp(noise(L, rng), 2500) * np.exp(-k / 0.04) * 1.2
    # debris crackle
    crack = np.zeros(n)
    for _ in range(140):
        at = 0.25 + rng.exponential(0.9)
        if at < L - 0.1:
            c = click(rng, 1200, 6000, 0.004 + rng.random() * 0.01, 0.15 + rng.random() * 0.4)
            place(crack, c, at)
    out += crack * np.exp(-k / 2.2)
    out = reverb(sat(out, 2.2), 1.6, 1200, 0.4, rng, 0.05)
    return fade(norm(out[:n]), 0.0005, 0.8)


def explosion_far(rng, L=5.0):
    """distant blast: muffled thump and long rolling rumble (echoes)"""
    n = int(SR * L)
    k = np.arange(n) / SR
    out = lp(noise(L, rng), 160, 3) * np.exp(-k / 1.4) * np.clip(k / 0.03, 0, 1) * 3.0
    f = 32 * (1 + 0.8 * np.exp(-k / 0.08))
    out += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / 0.6)
    for d, a in ((0.45, 0.5), (1.1, 0.35), (1.9, 0.22)):
        echo = np.zeros(n)
        place(echo, out[: n - int(d * SR)] * a, d)
        out += lp(echo, 220)
    return fade(norm(sat(out, 1.5)), 0.01, 1.0)


def missile_incoming(rng, L=2.6):
    """falling whistle that drops in pitch and swells"""
    n = int(SR * L)
    k = np.arange(n) / SR
    f = 2600 * np.exp(-k / 1.6) + 500
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR)
    air = bp(noise(L, rng), 1500, 5000) * 0.35
    env = np.clip(k / L, 0, 1) ** 1.6
    return fade(norm((tone * 0.7 + air) * env), 0.05, 0.05)


def heavy_launch(rng):
    """long heavy rocket ignition roar with crackle"""
    L = 6.0
    n = int(SR * L)
    k = np.arange(n) / SR
    roar = lp(noise(L, rng), 450, 2) * 1.4 + bp(noise(L, rng), 400, 1800) * 0.7
    crackle = np.zeros(n)
    for _ in range(400):
        at = rng.random() * (L - 0.2)
        place(crackle, click(rng, 800, 4000, 0.003, 0.2 + rng.random() * 0.3), at)
    envl = np.clip(k / 0.3, 0, 1) * np.exp(-np.maximum(0, k - 2.5) / 1.4)
    out = (roar + crackle * 0.6) * envl
    place(out, gunshot(rng, crack=1.2, body_f=220, body_tau=0.3, thump_f=32, thump=2.0, tail=0.8)[: int(1.2 * SR)] * 1.3, 0)
    out *= 1 + 0.12 * np.sin(2 * np.pi * 5 * k)
    return fade(norm(sat(out, 1.8)), 0.001, 0.8)


def build_all():
    rng = np.random.default_rng(7)
    sounds = {}

    def add(name, xs, subtitle=None, stream=False):
        files = []
        for i, x in enumerate(xs):
            fn = f"{name}_{i + 1}" if len(xs) > 1 else name
            write_ogg(os.path.join(OUT, fn + ".ogg"), x)
            files.append({"name": f"gunsmod:{fn}", "stream": stream} if stream else f"gunsmod:{fn}")
        sounds[name] = {"sounds": files, "subtitle": f"subtitles.gunsmod.{subtitle or name}"}

    for name, kw in GUNSHOTS.items():
        add(name, [gunshot(np.random.default_rng(hash(name) % 1000 + i), **kw) for i in range(3)], "gunshot")
    add("shot_suppressed", [suppressed(np.random.default_rng(30 + i)) for i in range(2)], "gunshot_suppressed")
    add("shot_grenade_launcher", [thoomp(rng)], "grenade_launch")
    add("rocket_launch", [rocket_launch(rng)], "rocket_launch")
    add("flamethrower_loop", [flame_loop(rng)], "flamethrower")
    add("dry_fire", [compose([(click(rng, 2000, 6000, 0.002, 1.0), 0.0), (click(rng, 1500, 4000, 0.003, 0.5), 0.03)], 0.12)])
    add("mag_out", [compose([(click(rng, 2000, 7000, 0.003), 0.0), (scrape(rng, 0.15, 1200, 4000, 0.5), 0.02), (thunk(rng, 220, 0.02, 0.4), 0.17)], 0.4)], "reload")
    add("mag_in", [compose([(scrape(rng, 0.08, 1500, 5000, 0.4), 0.0), (click(rng, 1500, 6000, 0.004, 1.2), 0.09), (thunk(rng, 160, 0.03, 0.8), 0.09)], 0.3)], "reload")
    add("bolt", [compose([(click(rng, 2500, 8000, 0.003), 0.0), (scrape(rng, 0.09, 2000, 6000, 0.5), 0.01), (click(rng, 2000, 7000, 0.004, 1.2), 0.16), (thunk(rng, 300, 0.015, 0.4), 0.16)], 0.35)], "reload")
    add("shell_insert", [compose([(scrape(rng, 0.05, 2000, 6000, 0.4), 0.0), (click(rng, 2500, 7000, 0.003, 1.0), 0.05)], 0.15)], "reload")
    add("pump", [compose([(thunk(rng, 140, 0.03, 0.9), 0.0), (click(rng, 1500, 5000, 0.004, 1.0), 0.0), (scrape(rng, 0.1, 800, 3000, 0.5), 0.02), (thunk(rng, 170, 0.03, 1.0), 0.2), (click(rng, 1500, 6000, 0.004, 1.2), 0.2)], 0.4)], "pump")
    add("break_open", [compose([(click(rng, 1500, 5000, 0.005, 1.0), 0.0), (thunk(rng, 120, 0.05, 0.8), 0.02)], 0.25)], "reload")
    add("jeep_idle", [engine_loop(rng, 26, 0.35, 0.3)], "jeep_engine")
    add("jeep_drive", [engine_loop(rng, 58, 0.25, 0.45)], "jeep_engine")
    st = np.concatenate([engine_loop(rng, 9, 0.2, 0.2, 0.6) * 0.6, engine_loop(rng, 30, 0.4, 0.3, 0.8)])
    add("jeep_start", [fade(norm(st), 0.01, 0.2)], "jeep_start")
    add("jeep_horn", [horn()], "jeep_horn")
    add("missile_launch", [missile_launch(rng)], "missile_launch")
    add("missile_alarm", [siren()], "missile_alarm")
    add("heavy_launch", [heavy_launch(rng)], "missile_launch")
    add("explosion_big", [explosion_big(np.random.default_rng(70 + i)) for i in range(2)], "explosion_big")
    add("explosion_far", [explosion_far(np.random.default_rng(80 + i)) for i in range(2)], "explosion_far")
    add("missile_incoming", [missile_incoming(rng)], "missile_incoming")
    add("cover_open", [compose([(click(rng, 1000, 4000, 0.006, 1.0), 0.0), (thunk(rng, 200, 0.04, 0.8), 0.03), (scrape(rng, 0.15, 3000, 7000, 0.15), 0.05)], 0.35)], "ui_switch")
    add("arm_switch", [compose([(click(rng, 1500, 6000, 0.003, 1.0), 0.0), (click(rng, 1000, 3000, 0.004, 0.6), 0.04)], 0.15)], "ui_switch")
    add("ui_beep", [beep(1200)], "ui_beep")
    add("ui_confirm", [compose([(beep(900, 0.08), 0.0), (beep(1350, 0.12), 0.09)], 0.25)], "ui_beep")
    add("hitmarker", [compose([(click(rng, 3000, 9000, 0.0015, 1.0, 0.015), 0.0)], 0.05)], "hitmarker")
    add("grenade_bounce", [compose([(click(rng, 2000, 6000, 0.008, 1.0), 0.0), (thunk(rng, 500, 0.01, 0.4), 0.0)], 0.12)], "grenade_bounce")
    add("bullet_whiz", [compose([(bp(noise(0.25, rng), 1500, 5000) * np.sin(np.linspace(0, np.pi, int(0.25 * SR))) ** 3, 0.0)], 0.3)], "bullet_whiz")
    add("turret_overheat", [compose([(bp(noise(0.8, rng), 3000, 8000) * np.exp(-np.arange(int(0.8 * SR)) / SR / 0.3) * 0.5, 0.0), (click(rng, 1000, 3000, 0.01, 1.0), 0.0)], 0.9)], "overheat")
    with open(os.path.join(ROOT, "sounds.json"), "w") as fh:
        json.dump(sounds, fh, indent=1)
    return sounds


if __name__ == "__main__":
    s = build_all()
    print(len(s), "sound events")
