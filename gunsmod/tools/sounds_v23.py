"""v2.3 sounds: the jet cockpit (the engines muffled through the airframe, the air rushing past the canopy, the air
conditioning hiss and the avionics hum), wreckage slamming into the ground, the FPV quad's angry buzz, the Shahed-136's
two-stroke "moped" drone and its rocket booster, and the A-10's new reheat. Run from tools/:  python3 sounds_v23.py"""
import json
import os

import numpy as np

from sounds import SR, OUT, ROOT, t, noise, lp, hp, bp, env_exp, place, norm, sat, fade, write_ogg, _loopify, click, missile_motor, reverb
from sounds_v22 import ENGINES, burner


def cockpit(rng, L=3.0):
    n = int(SR * L)
    k = np.arange(n) / SR
    rumble = lp(noise(L, rng), 140, 3) * 1.6 * (1 + 0.08 * np.sin(2 * np.pi * 0.7 * k))
    rush = bp(noise(L, rng), 350, 1600) * 0.7 * (1 + 0.15 * np.sin(2 * np.pi * 0.31 * k + 1.0))
    hiss = hp(noise(L, rng), 3500) * 0.12
    hum = sum(np.sin(2 * np.pi * f * k) * a for f, a in ((400, 0.05), (800, 0.025), (1200, 0.012)))
    whine = np.sin(2 * np.pi * np.cumsum(np.full(n, 2900.0) * (1 + 0.003 * np.sin(2 * np.pi * 0.4 * k))) / SR) * 0.03
    return _loopify(norm(sat(rumble + rush + hiss + hum + whine, 1.2), 0.8))


def wreck(rng, L=2.4):
    """a lump of aircraft hitting the ground: the thud, metal ringing and bending, bits rattling down"""
    n = int(SR * L)
    out = np.zeros(n)
    k = t(0.5)
    thud = np.sin(2 * np.pi * np.cumsum(60 * (1 + 1.5 * np.exp(-k / 0.02))) / SR) * env_exp(len(k), 0.12, 0.001) * 1.4
    thud += lp(noise(0.5, rng), 500) * env_exp(len(k), 0.08, 0.001) * 1.0
    place(out, thud, 0.0)
    kk = t(1.6)
    base = 180 + rng.random() * 120
    ring = np.zeros(len(kk))
    for ratio, amp, tau in ((1.0, 0.5, 0.5), (2.32, 0.35, 0.35), (3.86, 0.25, 0.22), (5.4, 0.15, 0.12), (7.1, 0.1, 0.08)):
        f = base * ratio * (1 + 0.01 * rng.standard_normal())
        ring += np.sin(2 * np.pi * f * kk + rng.random() * 6) * amp * np.exp(-kk / tau)
    place(out, ring * 0.7, 0.01)
    scrape = bp(noise(0.9, rng), 900, 4200) * np.exp(-t(0.9) / 0.3) * 0.5
    place(out, scrape, 0.05)
    for _ in range(14):
        at = 0.15 + rng.random() * 1.8
        place(out, click(rng, 1500, 6000, 0.004 + rng.random() * 0.01, 0.15 + rng.random() * 0.35) * np.exp(-at), at)
    out = reverb(out, 0.5, 2500, 0.25, rng)
    return fade(norm(sat(out, 1.4), 0.95), 0.001, 0.3)


def fpv(rng, L=2.0):
    """four small motors at full chat: detuned saw-like whines and the propeller wash"""
    n = int(SR * L)
    k = np.arange(n) / SR
    out = np.zeros(n)
    for f in (372, 384, 397, 409):
        wob = 1 + 0.012 * np.sin(2 * np.pi * (2.3 + rng.random() * 2) * k + rng.random() * 6) + 0.004 * lp(noise(L, rng), 8, 1)
        ph = 2 * np.pi * np.cumsum(f * wob) / SR
        out += sum(np.sin(h * ph) / h for h in range(1, 9)) * 0.22
    wash = bp(noise(L, rng), 700, 4500) * 0.55 * (1 + 0.3 * np.sin(2 * np.pi * 1180 * k))
    return _loopify(norm(sat(out + wash, 1.6), 0.85))


def shahed_engine(rng, L=3.0):
    """the two-stroke flat four: an uneven pulse train at about 95 firings a second, the pusher prop beating over it"""
    n = int(SR * L)
    k = np.arange(n) / SR
    pulses = np.zeros(n)
    tt = 0.0
    burst = lp(noise(0.02, rng), 1800) * env_exp(int(0.02 * SR), 0.004, 0.0003)
    while tt < L - 0.03:
        place(pulses, burst * (0.75 + rng.random() * 0.5), tt)
        tt += (1 / 95.0) * (1 + 0.06 * rng.standard_normal())
    tone = sum(np.sin(2 * np.pi * 95 * h * k + h) / h for h in range(1, 7)) * 0.35
    prop = bp(noise(L, rng), 120, 700) * (0.6 + 0.4 * np.sin(2 * np.pi * 233 * k)) * 0.6
    out = sat(pulses * 3.0 + tone + prop, 2.2)
    return _loopify(norm(lp(out, 5000), 0.85))


def booster(rng, L=3.0):
    """the solid rocket booster kicking it off the rail, then the little engine catching"""
    out = missile_motor(rng, L, 1.6, 650, 1.1, 0.9)
    eng = shahed_engine(rng, 1.2)
    place(out, eng * np.linspace(0, 0.5, len(eng)), L - 1.2)
    return fade(norm(out, 0.95), 0.001, 0.3)


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
        sounds[name] = {"sounds": files, "subtitle": f"subtitles.gunsmod.{subtitle or name}"}

    add("jet_cockpit", [cockpit(np.random.default_rng(4000))], "jet_engine")
    add("wreck_impact", [wreck(np.random.default_rng(4010 + v)) for v in range(3)])
    add("fpv_buzz", [fpv(np.random.default_rng(4020))])
    add("shahed_engine", [shahed_engine(np.random.default_rng(4030))])
    add("shahed_launch", [booster(np.random.default_rng(4040 + v)) for v in range(2)])
    a10 = dict(ENGINES["a10"], burner=0.8)
    add("a10_burner", [burner(np.random.default_rng(3042), a10)], "jet_afterburner")
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)


if __name__ == "__main__":
    build()
    print("ok")
