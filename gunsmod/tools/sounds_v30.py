"""v3.0 gunshots: one sound profile for each of the 25 new guns (three takes each so repeats differ). Parameters of
sounds_v19.shot: cal (round weight), boom_f (thump pitch), body_f / body_tau (blast colour and length), crack (the
supersonic crack), mech (the action), tail (echo length), drive (saturation), size (the space). Run from tools/."""
import json
import os

import numpy as np

from sounds import OUT, ROOT, write_ogg, SR, noise, lp, hp, bp, norm, sat, fade
from sounds_v19 import shot

GUNS = {
    "shot_hk416": dict(cal=0.98, boom_f=80, body_f=1150, body_tau=0.05, crack=1.0, mech=0.35, tail=0.6),
    "shot_scar_l": dict(cal=1.0, boom_f=78, body_f=1050, body_tau=0.055, crack=1.05, mech=0.4, tail=0.65),
    "shot_scar_h": dict(cal=1.35, boom_f=62, body_f=820, body_tau=0.07, crack=1.3, mech=0.45, tail=0.8, drive=3.0),
    "shot_g36": dict(cal=0.95, boom_f=84, body_f=1250, body_tau=0.05, crack=0.95, mech=0.3, tail=0.6),
    "shot_aug": dict(cal=0.95, boom_f=82, body_f=1300, body_tau=0.048, crack=0.95, mech=0.25, tail=0.55),
    "shot_qbz95": dict(cal=0.92, boom_f=86, body_f=1350, body_tau=0.045, crack=0.9, mech=0.3, tail=0.55),
    "shot_ak12": dict(cal=1.05, boom_f=72, body_f=980, body_tau=0.06, crack=1.05, mech=0.45, tail=0.65, drive=2.8),
    "shot_ak103": dict(cal=1.22, boom_f=64, body_f=860, body_tau=0.068, crack=1.2, mech=0.5, tail=0.75, drive=3.0),
    "shot_mp5": dict(cal=0.68, boom_f=110, body_f=1500, body_tau=0.035, crack=0.25, mech=0.5, tail=0.4),
    "shot_mp7": dict(cal=0.62, boom_f=120, body_f=1800, body_tau=0.03, crack=0.6, mech=0.35, tail=0.35),
    "shot_p90": dict(cal=0.62, boom_f=118, body_f=1700, body_tau=0.03, crack=0.6, mech=0.3, tail=0.35),
    "shot_mpx": dict(cal=0.7, boom_f=105, body_f=1450, body_tau=0.036, crack=0.25, mech=0.45, tail=0.4),
    "shot_m240": dict(cal=1.3, boom_f=60, body_f=780, body_tau=0.075, crack=1.3, mech=0.55, tail=0.9, drive=3.2, size=1.2),
    "shot_mg3": dict(cal=1.2, boom_f=66, body_f=900, body_tau=0.05, crack=1.2, mech=0.3, tail=0.7, drive=3.2),
    "shot_pkp": dict(cal=1.28, boom_f=60, body_f=820, body_tau=0.07, crack=1.25, mech=0.5, tail=0.85, drive=3.1, size=1.2),
    "shot_m82": dict(cal=1.95, boom_f=42, body_f=520, body_tau=0.1, crack=1.6, mech=0.35, tail=1.5, drive=3.8, size=1.6),
    "shot_axmc": dict(cal=1.6, boom_f=50, body_f=640, body_tau=0.085, crack=1.5, mech=0.25, tail=1.3, drive=3.4, size=1.5),
    "shot_m200": dict(cal=1.85, boom_f=45, body_f=560, body_tau=0.095, crack=1.6, mech=0.25, tail=1.5, drive=3.6, size=1.6),
    "shot_benelli": dict(cal=1.35, boom_f=58, body_f=700, body_tau=0.08, crack=0.0, mech=0.55, tail=0.8, drive=3.2),
    "shot_r870": dict(cal=1.45, boom_f=54, body_f=660, body_tau=0.085, crack=0.0, mech=0.0, tail=0.9, drive=3.3),
    "shot_spas12": dict(cal=1.4, boom_f=56, body_f=680, body_tau=0.082, crack=0.0, mech=0.4, tail=0.85, drive=3.3),
    "shot_glock": dict(cal=0.66, boom_f=112, body_f=1600, body_tau=0.032, crack=0.2, mech=0.4, tail=0.45),
    "shot_deagle": dict(cal=1.25, boom_f=62, body_f=820, body_tau=0.06, crack=0.5, mech=0.4, tail=0.8, drive=3.4),
    "shot_p320": dict(cal=0.68, boom_f=108, body_f=1550, body_tau=0.033, crack=0.2, mech=0.4, tail=0.45),
    "shot_fiveseven": dict(cal=0.6, boom_f=124, body_f=1850, body_tau=0.028, crack=0.6, mech=0.35, tail=0.4),
}


def take(seed, kw):
    """sounds_v19.outdoors trips over a rounding error for some lengths: nudge the tail a hair until it fits"""
    kw = dict(kw)
    for _ in range(20):
        try:
            return shot(np.random.default_rng(seed), **kw)
        except ValueError:
            kw["tail"] = kw.get("tail", 0.6) + 0.003
    raise RuntimeError(seed)


def build():
    path = os.path.join(ROOT, "sounds.json")
    with open(path) as fh:
        sounds = json.load(fh)
    for i, (name, kw) in enumerate(GUNS.items()):
        old = sounds.get(name, {}).get("sounds", [])
        for f in old:
            fn = (f["name"] if isinstance(f, dict) else f).split(":", 1)[1]
            p = os.path.join(OUT, fn + ".ogg")
            if os.path.exists(p):
                os.remove(p)
        files = []
        for v in range(3):
            fn = f"{name}_{v + 1}"
            write_ogg(os.path.join(OUT, fn + ".ogg"), take(5000 + i * 10 + v, kw))
            files.append(f"gunsmod:{fn}")
        sounds[name] = {"sounds": files, "subtitle": "subtitles.gunsmod.gunshot"}
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)


if __name__ == "__main__":
    build()
    print("ok", len(GUNS))


# ------------------------------------------------------------------------------------------------ impacts
def glass_hit(seed):
    from sounds import t, noise, hp, bp, env_exp, place, norm, fade, SR
    rng = np.random.default_rng(seed)
    n = int(0.7 * SR)
    out = np.zeros(n)
    place(out, hp(noise(0.02, rng), 2500) * env_exp(int(0.02 * SR), 0.004) * 1.2, 0.0)
    for _ in range(18):
        f = 2500 + rng.random() * 6000
        k = t(0.08)
        tink = np.sin(2 * np.pi * f * k) * np.exp(-k / (0.01 + rng.random() * 0.03)) * (0.2 + rng.random() * 0.4)
        place(out, tink, 0.01 + rng.random() * 0.45)
    return fade(norm(out, 0.85), 0.001, 0.1)


def armour_hit(seed):
    from sounds import t, noise, bp, env_exp, place, norm, fade, SR
    rng = np.random.default_rng(seed)
    k = t(0.85)
    out = np.zeros(len(k))
    place(out, bp(noise(0.015, rng), 1500, 7000) * env_exp(int(0.015 * SR), 0.003) * 1.4, 0.0)
    base = 900 + rng.random() * 500
    for r, a, tau in ((1.0, 0.6, 0.25), (1.71, 0.4, 0.18), (2.53, 0.3, 0.12), (3.9, 0.2, 0.07)):
        out += np.sin(2 * np.pi * base * r * k + rng.random() * 6) * a * np.exp(-k / tau)
    return fade(norm(out, 0.9), 0.001, 0.2)


def build_impacts():
    path = os.path.join(ROOT, "sounds.json")
    with open(path) as fh:
        sounds = json.load(fh)
    for name, fn, seed in (("impact_glass", glass_hit, 5600), ("impact_armor", armour_hit, 5610)):
        files = []
        for v in range(3):
            fname = f"{name}_{v + 1}"
            write_ogg(os.path.join(OUT, fname + ".ogg"), fn(seed + v))
            files.append(f"gunsmod:{fname}")
        sounds[name] = {"sounds": files, "subtitle": "subtitles.gunsmod.bullet_impact"}
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)


if __name__ == "__main__":
    build_impacts()


def build_ciws():
    from sounds_v22 import rotary
    path = os.path.join(ROOT, "sounds.json")
    with open(path) as fh:
        sounds = json.load(fh)
    files = []
    for v in range(2):
        fname = f"ciws_burst_{v + 1}"
        write_ogg(os.path.join(OUT, fname + ".ogg"), rotary(np.random.default_rng(5700 + v), 75, 0.4, 95, 0.8, 0.9))
        files.append(f"gunsmod:{fname}")
    sounds["ciws_burst"] = {"sounds": files, "subtitle": "subtitles.gunsmod.aa_gun"}
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)


def quad(rng, freqs, harm, wash_lo, wash_hi, wash, L=2.0, drive=1.4):
    """multirotor motors: detuned whines (one per motor) and the propeller wash beating over them"""
    from sounds import _loopify
    n = int(SR * L)
    k = np.arange(n) / SR
    out = np.zeros(n)
    for f in freqs:
        wob = 1 + 0.01 * np.sin(2 * np.pi * (1.6 + rng.random() * 2) * k + rng.random() * 6) + 0.004 * lp(noise(L, rng), 8, 1)
        ph = 2 * np.pi * np.cumsum(f * wob) / SR
        out += sum(np.sin(h * ph) / h for h in range(1, harm)) * 0.2
    w = bp(noise(L, rng), wash_lo, wash_hi) * wash * (1 + 0.35 * np.sin(2 * np.pi * freqs[0] * 2.0 * k))
    return _loopify(norm(sat(out + w, drive), 0.85))


def plane_engine(rng, L=3.0):
    """a small four-stroke single: an even pulse train at about 70 firings a second, the tractor prop over it"""
    from sounds import _loopify, place, env_exp
    n = int(SR * L)
    k = np.arange(n) / SR
    pulses = np.zeros(n)
    tt = 0.0
    burst = lp(noise(0.025, rng), 1400) * env_exp(int(0.025 * SR), 0.005, 0.0004)
    while tt < L - 0.03:
        place(pulses, burst * (0.85 + rng.random() * 0.3), tt)
        tt += (1 / 70.0) * (1 + 0.025 * rng.standard_normal())
    tone = sum(np.sin(2 * np.pi * 70 * h * k + h) / h for h in range(1, 6)) * 0.3
    prop = bp(noise(L, rng), 160, 900) * (0.6 + 0.4 * np.sin(2 * np.pi * 140 * k)) * 0.5
    return _loopify(norm(lp(sat(pulses * 2.6 + tone + prop, 1.8), 4200), 0.85))


def release(seed):
    """the drop rack's servo snapping open and the bomb leaving"""
    rng = np.random.default_rng(seed)
    from sounds import place, env_exp
    n = int(SR * 0.5)
    out = np.zeros(n)
    k = np.arange(int(0.06 * SR)) / SR
    servo = np.sin(2 * np.pi * (900 + 1400 * k / k[-1]) * k) * np.exp(-k * 30) * 0.4
    place(out, servo, 0.0)
    snap = hp(noise(0.02, rng), 2500) * env_exp(int(0.02 * SR), 0.0005, 0.004)
    place(out, snap * 0.9, 0.055)
    whoosh = bp(noise(0.4, rng), 400, 2200) * np.linspace(0.3, 0, int(0.4 * SR))
    place(out, whoosh, 0.07)
    return fade(norm(out, 0.9), 0.001, 0.05)


def static_noise(seed, L=1.2):
    """video link breaking up: hiss with dropouts and digital crackle"""
    rng = np.random.default_rng(seed)
    n = int(SR * L)
    hiss = bp(noise(L, rng), 900, 9000) * 0.6
    gate = (lp(noise(L, rng), 18, 1) > -0.1).astype(float) * 0.7 + 0.3
    crackle = (rng.random(n) > 0.9993).astype(float) * rng.standard_normal(n) * 3
    return fade(norm(hiss * gate + crackle, 0.8), 0.01, 0.15)


def ping(seed, f0, f1, L=0.35):
    from sounds import env_exp
    n = int(SR * L)
    k = np.arange(n) / SR
    f = np.linspace(f0, f1, n)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k * 9)
    tone += 0.3 * np.sin(4 * np.pi * np.cumsum(f) / SR) * np.exp(-k * 14)
    return fade(norm(tone, 0.7), 0.002, 0.05)


def build_drones():
    path = os.path.join(ROOT, "sounds.json")
    with open(path) as fh:
        sounds = json.load(fh)

    def put(name, xs, subtitle):
        files = []
        for v, x in enumerate(xs):
            fname = f"{name}_{v + 1}" if len(xs) > 1 else name
            write_ogg(os.path.join(OUT, fname + ".ogg"), x)
            files.append(f"gunsmod:{fname}")
        sounds[name] = {"sounds": files, "subtitle": f"subtitles.gunsmod.{subtitle}"}

    put("drone_quad", [quad(np.random.default_rng(5800), (231, 238, 246, 252), 7, 600, 3800, 0.4)], "drone")
    put("drone_hexa", [quad(np.random.default_rng(5810), (118, 121, 124, 127, 130, 133), 9, 180, 1800, 0.65, drive=1.8)], "drone")
    put("drone_lancet", [quad(np.random.default_rng(5820), (520, 1040), 5, 1200, 7000, 0.6, drive=1.2)], "drone")
    put("drone_plane", [plane_engine(np.random.default_rng(5830))], "drone")
    put("drone_drop", [release(5840 + v) for v in range(2)], "drone_drop")
    put("ui_drone_static", [static_noise(5850)], "drone_static")
    put("ui_drone_spot", [ping(5860, 1400, 1900)], "drone_spot")
    put("ui_drone_lock", [ping(5870, 900, 2400, 0.5)], "drone_spot")
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)
