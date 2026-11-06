"""v1.9 sound pass: every gunshot rebuilt in layers (pressure wave, supersonic crack, boom, the body darkening as it
dies, the action cycling with a metallic ring, ground reflection, echoes off trees and hills, a rolling tail), distant
shot versions heard far away, bullet impacts by surface, ricochets, the snap of a round passing close, a better petrol
engine for the cars and longer, rolling explosion echoes. Written over the old files with the same event names.
Run from tools/:  python3 sounds_v19.py"""
import json
import os

import numpy as np

from sounds import (SR, OUT, ROOT, t, noise, lp, hp, bp, env_exp, pad, place, norm, sat, fade, write_ogg, reverb, tv_lowpass,
                    sweep, _loopify, click)


# ------------------------------------------------------------------------------------------------ building blocks
def ring(freqs, tau, L, amps=None):
    """a struck metal part: decaying partials"""
    k = t(L)
    out = np.zeros(len(k))
    for i, f in enumerate(freqs):
        a = 1.0 if amps is None else amps[i]
        out += a * np.sin(2 * np.pi * f * k + i) * np.exp(-k / (tau * (1 - 0.15 * i)))
    return out


def outdoors(x, rng, size=1.0, tail=1.0, bright=2400):
    """the shot in open country: a ground bounce, echoes off trees and buildings, slapback off a far hill, and a
    rolling low tail; size scales the distances, tail the length"""
    L = len(x) / SR + 0.25 + 1.6 * tail
    out = pad(x.copy(), int(L * SR))
    place(out, lp(x, 4500) * 0.45, 0.004 + rng.random() * 0.004)                # ground
    for _ in range(4):                                                            # trees, walls
        at = 0.035 + rng.random() * 0.12 * size
        place(out, lp(x, bright) * (0.12 + rng.random() * 0.12), at)
    for _ in range(2):                                                            # a far hillside
        at = 0.25 + rng.random() * 0.45 * size
        place(out, lp(x, 900) * (0.10 + rng.random() * 0.08) * tail, at)
    # rolling tail: low noise breathing slowly, dying away
    n = len(out)
    k = np.arange(n) / SR
    roll = lp(noise(n / SR, rng), 700, 2) * np.exp(-k / (0.35 + 0.5 * tail))
    breathe = 0.6 + 0.4 * np.sin(2 * np.pi * (1.3 + rng.random()) * k + rng.random() * 6)
    roll *= breathe * np.clip((k - 0.02) / 0.06, 0, 1)
    out += roll * 0.22 * tail * np.max(np.abs(x))
    return out


def shot(rng, cal=1.0, boom_f=70.0, body_f=1000.0, body_tau=0.06, crack=1.0, mech=0.3, tail=0.6, drive=2.6, size=1.0, L=None,
         auto_ring=(2300, 4100, 6700)):
    """one gunshot as the shooter hears it. cal: weight of the round (0.6 pistol .. 2 cannon)"""
    Lb = 0.45 + 0.1 * cal
    n = int(Lb * SR)
    out = np.zeros(n)
    k = t(Lb)
    # muzzle blast: a sharp positive pressure pulse with a longer suction behind it
    pw = (1 - k / (0.003 * cal)) * np.exp(-k / (0.002 * cal))
    pw[k > 0.03] = 0
    out += hp(pw, 60) * 1.6
    # supersonic crack of the round (an N-wave)
    if crack:
        m = max(4, int(0.0005 * SR))
        nw = np.linspace(1, -1, m)
        c = np.zeros(int(0.01 * SR))
        c[:m] = nw
        place(out, hp(c, 1500) * crack * 1.2, 0.0)
        place(out, hp(noise(0.006, rng), 3000) * env_exp(int(0.006 * SR), 0.0012) * crack, 0.0)
    # boom: the low thump whose pitch falls
    f = boom_f * (1 + 3.0 * np.exp(-k / 0.006))
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(n, 0.045 * cal, 0.0007)
    out += boom * (0.9 + 0.3 * cal)
    # body: hot gas noise, bright at first, darkening as it dies away
    body = noise(Lb, rng) * env_exp(n, body_tau * cal, 0.0006)
    body = tv_lowpass(body, body_f * 3.5, body_f * 0.35, steps=24)
    out += body * 1.5
    # the action cycling: bolt clack with a metallic ring, sometimes the brass ringing off something
    if mech:
        at = 0.028 + rng.random() * 0.012
        cl = bp(noise(0.02, rng), 1800, 7000) * env_exp(int(0.02 * SR), 0.0025)
        rg = ring([f0 * (0.97 + 0.06 * rng.random()) for f0 in auto_ring], 0.012, 0.08) * 0.35
        place(out, (pad(cl, len(rg)) + rg) * mech, at)
        place(out, click(rng, 1500, 5000, 0.004, mech * 0.6), at + 0.035)
    out = sat(out, drive)
    out = outdoors(out, rng, size=size, tail=tail)
    if L:
        out = pad(out, int(L * SR))
    # glue: soft compression so the body and the echoes carry next to the transient
    out = np.tanh(norm(out) * 1.9) / np.tanh(1.9)
    return fade(norm(out), 0.0003, 0.12)


def shot_far(rng, weight=1.0, L=2.4):
    """a shot heard from far away: the crack and the hiss are gone, a muffled thump arrives and rolls on the hills"""
    n = int(L * SR)
    out = np.zeros(n)
    k = t(0.5)
    f = (55 + 20 / weight) * (1 + 1.2 * np.exp(-k / 0.02))
    thump = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(len(k), 0.06 * weight, 0.004)
    body = lp(noise(0.5, rng), 450 + 250 / weight, 2) * env_exp(len(k), 0.07 * weight, 0.003)
    first = thump * 1.2 + body * 1.4
    if weight >= 1.2:
        # heavy rounds still crack faintly
        place(first, bp(noise(0.01, rng), 800, 2500) * env_exp(int(0.01 * SR), 0.002) * 0.25, 0.0)
    place(out, first, 0.0)
    for i in range(5):
        at = 0.12 + i * (0.18 + rng.random() * 0.2)
        if at < L - 0.6:
            place(out, lp(first, 420) * (0.45 - i * 0.07), at)
    kk = np.arange(n) / SR
    roll = lp(noise(L, rng), 260, 2) * np.exp(-kk / (0.5 * weight + 0.3)) * np.clip(kk / 0.05, 0, 1)
    out += roll * 0.5
    return fade(norm(sat(out, 1.4), 0.9), 0.002, 0.4)


def snap(rng, L=0.35):
    """a supersonic round passing close: a sharp snap, then the tearing whizz"""
    n = int(L * SR)
    out = np.zeros(n)
    m = int(0.0004 * SR)
    c = np.zeros(int(0.005 * SR))
    c[:m] = np.linspace(1, -1, m)
    place(out, hp(c, 2000) * 1.4, 0.0)
    k = t(L)
    wh = bp(noise(L, rng), 1800, 6500) * np.exp(-k / 0.07) * np.clip(k / 0.004, 0, 1)
    whine = np.sin(2 * np.pi * np.cumsum(np.linspace(5200, 2200, n)) / SR) * np.exp(-k / 0.05) * 0.25
    out += wh * 0.8 + whine
    return fade(norm(reverb(out, 0.25, 4000, 0.2, rng)[:n]), 0.0002, 0.05)


def impact(rng, kind):
    """a round striking: dirt, stone, wood or metal"""
    L = 0.6 if kind == "metal" else 0.45
    n = int(L * SR)
    out = np.zeros(n)
    k = t(L)
    if kind == "dirt":
        out += lp(noise(L, rng), 700, 2) * env_exp(n, 0.025) * 1.4
        for _ in range(22):
            place(out, bp(noise(0.004, rng), 1500, 6000) * env_exp(int(0.004 * SR), 0.001) * (0.2 + rng.random() * 0.4),
                  0.01 + rng.random() * 0.3)
    elif kind == "stone":
        out += hp(noise(L, rng), 1500) * env_exp(n, 0.006) * 1.6
        out += bp(noise(L, rng), 600, 3000) * env_exp(n, 0.03) * 0.8
        for _ in range(14):
            place(out, bp(noise(0.006, rng), 2500, 8000) * env_exp(int(0.006 * SR), 0.0015) * (0.2 + rng.random() * 0.3),
                  0.02 + rng.random() * 0.28)
    elif kind == "wood":
        out += bp(noise(L, rng), 300, 1600) * env_exp(n, 0.02) * 1.6
        out += ring([420 + rng.random() * 80, 980 + rng.random() * 120], 0.03, L) * 0.4
        for _ in range(10):
            place(out, bp(noise(0.005, rng), 1200, 4000) * env_exp(int(0.005 * SR), 0.0015) * 0.3, 0.01 + rng.random() * 0.12)
    else:
        out += bp(noise(L, rng), 2000, 7000) * env_exp(n, 0.004) * 0.6
        base = 1800 + rng.random() * 900
        out += ring([base, base * 1.62, base * 2.37, base * 3.1], 0.12, L, [1, 0.6, 0.4, 0.25]) * 0.7
    return fade(norm(reverb(out, 0.3, 3500, 0.2, rng)[:n]), 0.0003, 0.08)


def ricochet(rng, L=0.7):
    """the round skipping off: a falling, warbling whine"""
    n = int(L * SR)
    k = t(L)
    f0 = 3200 + rng.random() * 1600
    f = f0 * (1 - 0.45 * k / L) * (1 + 0.05 * np.sin(2 * np.pi * 37 * k))
    whine = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / 0.22) * np.clip(k / 0.01, 0, 1)
    air = bp(noise(L, rng), 2000, 7000) * np.exp(-k / 0.1) * 0.3
    hit = hp(noise(0.01, rng), 2500) * env_exp(int(0.01 * SR), 0.002)
    out = whine + air
    place(out, hit * 0.8, 0.0)
    return fade(norm(reverb(out, 0.4, 3000, 0.25, rng)[:n]), 0.0003, 0.1)


def petrol_engine(rng, fire_hz, L=2.0, load=0.5):
    """a petrol V8 as a stream of firing pulses with jitter, exhaust burble, intake hiss and a little valve tick"""
    n = int(L * SR)
    out = np.zeros(n)
    pulse = lp(noise(0.05, rng), 380 + 500 * load, 2) * env_exp(int(0.05 * SR), 0.008 + 0.006 * (1 - load), 0.0008)
    tick = 0.0
    while tick < L:
        a = 0.75 + 0.5 * rng.random()
        place(out, pulse * a, tick)
        tick += (1 / fire_hz) * (0.94 + 0.12 * rng.random())
    k = np.arange(n) / SR
    tone = sum(np.sin(2 * np.pi * fire_hz * h * k + h) / h for h in (0.5, 1, 2, 3)) * 0.25
    burble = lp(noise(L, rng), 160, 2) * (0.6 + 0.4 * np.sin(2 * np.pi * fire_hz * 0.25 * k)) * 0.5
    intake = bp(noise(L, rng), 900, 3200) * (0.04 + 0.12 * load)
    valves = np.zeros(n)
    for i in range(int(L * fire_hz * 0.5)):
        place(valves, click(rng, 3000, 8000, 0.0015, 0.05), i / (fire_hz * 0.5))
    out = sat(out * 2.2 + tone + burble + intake + valves, 1.6)
    return _loopify(norm(out, 0.9))


def boom_tail(x, rng, tail=3.0):
    """an explosion's rolling echoes: discrete reflections off the land, then thunder-like rumble"""
    L = len(x) / SR + tail
    out = pad(x.copy(), int(L * SR))
    for i in range(6):
        at = 0.18 + i * (0.25 + rng.random() * 0.3)
        place(out, lp(x, 700 - i * 60) * (0.32 - i * 0.04), at)
    n = len(out)
    k = np.arange(n) / SR
    rum = lp(noise(L, rng), 120, 2) * np.exp(-k / (tail * 0.45)) * (0.7 + 0.3 * np.sin(2 * np.pi * 0.9 * k))
    out += rum * 0.6 * np.max(np.abs(x))
    return out


def explosion(rng, size=1.0, L=2.4):
    """a close blast: the crack of the shock front, a chest-hitting sub boom, roaring fire and debris raining down"""
    n = int(L * SR)
    k = t(L)
    out = np.zeros(n)
    place(out, hp(noise(0.02, rng), 1200) * env_exp(int(0.02 * SR), 0.004) * 1.4, 0.0)
    f = 38 * (1 + 2.5 * np.exp(-k / 0.03))
    out += np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(n, 0.25 * size, 0.002) * 1.8
    roar = tv_lowpass(noise(L, rng) * env_exp(n, 0.35 * size, 0.003), 3500, 250, steps=30)
    out += roar * 1.6
    for _ in range(int(60 * size)):
        at = 0.25 + rng.random() * (L - 0.4)
        place(out, bp(noise(0.01, rng), 1500, 6000) * env_exp(int(0.01 * SR), 0.002) * (0.1 + rng.random() * 0.25) * np.exp(-at / 1.2), at)
    out = sat(out, 2.5)
    out = boom_tail(out, rng, 2.2 * size)
    return fade(norm(out), 0.0005, 0.5)


# ------------------------------------------------------------------------------------------------ the build
GUNS = {
    "shot_pistol": dict(cal=0.65, boom_f=95, body_f=1300, body_tau=0.045, crack=0.6, mech=0.35, tail=0.45, size=0.8),
    "shot_magnum": dict(cal=0.95, boom_f=75, body_f=850, body_tau=0.08, crack=0.8, mech=0.2, tail=0.75, drive=3.4),
    "shot_smg": dict(cal=0.6, boom_f=100, body_f=1400, body_tau=0.035, crack=0.4, mech=0.45, tail=0.35, size=0.8),
    "shot_rifle": dict(cal=1.0, boom_f=78, body_f=950, body_tau=0.06, crack=1.1, mech=0.4, tail=0.7, drive=3.0),
    "shot_carbine": dict(cal=0.9, boom_f=84, body_f=1100, body_tau=0.05, crack=1.0, mech=0.4, tail=0.6),
    "shot_shotgun": dict(cal=1.3, boom_f=60, body_f=650, body_tau=0.1, crack=0.2, mech=0.25, tail=0.9, drive=3.5, size=1.2),
    "shot_sniper": dict(cal=1.45, boom_f=55, body_f=700, body_tau=0.09, crack=1.6, mech=0.15, tail=1.4, drive=3.2, size=1.5),
    "shot_heavy": dict(cal=1.6, boom_f=46, body_f=520, body_tau=0.11, crack=1.6, mech=0.3, tail=1.5, drive=3.8, size=1.6),
    "shot_lmg": dict(cal=1.05, boom_f=76, body_f=900, body_tau=0.06, crack=1.1, mech=0.5, tail=0.6, drive=3.0),
    "shot_minigun": dict(cal=0.8, boom_f=90, body_f=1000, body_tau=0.025, crack=0.9, mech=0.0, tail=0.12, L=0.3),
    "shot_turret": dict(cal=1.4, boom_f=50, body_f=560, body_tau=0.09, crack=1.4, mech=0.45, tail=1.0, drive=3.6, size=1.3,
                        auto_ring=(1600, 2900, 4700)),
    "heli_gun": dict(cal=1.5, boom_f=48, body_f=500, body_tau=0.07, crack=1.3, mech=0.2, tail=0.9, drive=3.8, size=1.4),
    "cannon_30": dict(cal=1.9, boom_f=40, body_f=380, body_tau=0.1, crack=1.5, mech=0.25, tail=1.2, drive=4.0, size=1.6,
                      auto_ring=(1200, 2200, 3600)),
}


def build():
    path = os.path.join(ROOT, "sounds.json")
    with open(path) as fh:
        sounds = json.load(fh)

    def add(name, xs, subtitle=None):
        old = sounds.get(name, {}).get("sounds", [])
        for f in old:
            fn = (f["name"] if isinstance(f, dict) else f).split(":", 1)[1]
            p = os.path.join(OUT, fn + ".ogg")
            if os.path.exists(p):
                os.remove(p)
        files = []
        for i, x in enumerate(xs):
            fn = f"{name}_{i + 1}" if len(xs) > 1 else name
            write_ogg(os.path.join(OUT, fn + ".ogg"), x)
            files.append(f"gunsmod:{fn}")
        sub = subtitle or sounds.get(name, {}).get("subtitle", f"subtitles.gunsmod.{name}").split("subtitles.gunsmod.")[-1]
        sounds[name] = {"sounds": files, "subtitle": f"subtitles.gunsmod.{sub}"}

    for i, (name, kw) in enumerate(GUNS.items()):
        add(name, [shot(np.random.default_rng(1900 + i * 10 + v), **kw) for v in range(4)])
    add("shot_far_light", [shot_far(np.random.default_rng(2100 + v), 0.7) for v in range(3)], "gunshot_far")
    add("shot_far_rifle", [shot_far(np.random.default_rng(2110 + v), 1.0) for v in range(3)], "gunshot_far")
    add("shot_far_heavy", [shot_far(np.random.default_rng(2120 + v), 1.6, 3.0) for v in range(3)], "gunshot_far")
    add("bullet_whiz", [snap(np.random.default_rng(2130 + v)) for v in range(4)])
    for kind in ("dirt", "stone", "wood", "metal"):
        add("impact_" + kind, [impact(np.random.default_rng(2140 + v + len(kind) * 7), kind) for v in range(4)], "bullet_impact")
    add("ricochet", [ricochet(np.random.default_rng(2180 + v)) for v in range(3)])
    add("jeep_idle", [petrol_engine(np.random.default_rng(2190), 24, 2.0, 0.2)], "jeep_engine")
    add("jeep_drive", [petrol_engine(np.random.default_rng(2191), 62, 2.0, 0.8)], "jeep_engine")
    add("explosion_big", [explosion(np.random.default_rng(2200 + v), 1.0 + 0.2 * v) for v in range(3)])
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)
    return sounds


if __name__ == "__main__":
    s = build()
    print(len(s), "sound events")
