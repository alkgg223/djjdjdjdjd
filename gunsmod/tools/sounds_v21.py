"""v2.1 sounds for the stealth bomber: its bomb close by and far away, the bomb leaving the bay, and the deep drone of
the four engines high overhead (a seamless loop). Run from tools/:  python3 sounds_v21.py"""
import json
import os

import numpy as np

from sounds import SR, OUT, ROOT, t, noise, lp, hp, bp, env_exp, pad, place, norm, sat, fade, write_ogg, tv_lowpass, sweep, _loopify
from sounds_v19 import boom_tail


def big_bomb(rng, L=11.0):
    """the bomb close by: the crack of the shock front, a chest-crushing sub boom that sags in pitch, the roar of the
    fireball going on and on, debris, and thunder rolling round the hills for seconds"""
    n = int(L * SR)
    k = t(L)
    out = np.zeros(n)
    place(out, hp(noise(0.03, rng), 900) * env_exp(int(0.03 * SR), 0.006) * 1.6, 0.0)
    f = 24 * (1 + 3.0 * np.exp(-k / 0.05))
    out += np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(n, 0.9, 0.003) * 2.4
    out += lp(noise(L, rng), 60, 3) * env_exp(n, 2.2, 0.01) * 3.0
    roar = tv_lowpass(noise(L, rng) * env_exp(n, 1.6, 0.02), 2600, 160, steps=40)
    out += roar * 2.0
    for _ in range(140):
        at = 0.4 + rng.random() * 4.0
        place(out, bp(noise(0.012, rng), 1200, 5200) * env_exp(int(0.012 * SR), 0.003) * (0.08 + rng.random() * 0.2) * np.exp(-at / 2.0), at)
    out = sat(out, 2.2)
    out = boom_tail(out, rng, 6.0)
    return fade(norm(out, 0.97), 0.0005, 1.5)


def big_bomb_far(rng, L=10.0):
    """far away: the highs are gone, a deep thud and a long rumble like thunder"""
    n = int(L * SR)
    k = t(L)
    out = np.zeros(n)
    f = 20 * (1 + 1.5 * np.exp(-k / 0.08))
    out += np.sin(2 * np.pi * np.cumsum(f) / SR) * env_exp(n, 1.2, 0.02) * 2.0
    out += lp(noise(L, rng), 180, 3) * env_exp(n, 2.6, 0.08) * 2.6
    rum = lp(noise(L, rng), 90, 2) * np.exp(-k / 3.5) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * k + rng.random() * 6))
    out += rum * 2.0
    out = sat(out, 1.6)
    out = boom_tail(out, rng, 4.0)
    return fade(norm(out, 0.9), 0.02, 1.5)


def bay_release(rng, L=1.4):
    """the bay doors bang open, the rack lets go with a clunk, wind rushes in"""
    n = int(L * SR)
    out = np.zeros(n)
    place(out, lp(noise(0.08, rng), 900) * env_exp(int(0.08 * SR), 0.03) * 1.2, 0.0)
    place(out, bp(noise(0.05, rng), 300, 2500) * env_exp(int(0.05 * SR), 0.02) * 1.4, 0.35)
    wind = bp(noise(L, rng), 200, 1800) * np.clip(t(L) / 0.3, 0, 1) * np.exp(-t(L) / 0.8) * 0.6
    out += wind
    return fade(norm(out, 0.8), 0.001, 0.2)


def drone(rng, L=4.0):
    """four engines far up: a deep breathing roar with a soft turbine whine and a slow beat between the engines"""
    n = int(L * SR)
    k = np.arange(n) / SR
    roar = lp(noise(L, rng), 420, 3) * 1.5 + lp(noise(L, rng), 110, 2) * 1.8
    beat = 0.78 + 0.22 * np.sin(2 * np.pi * 0.5 * k) * np.sin(2 * np.pi * 0.25 * k + 1)
    whine = sweep(2650 * (1 + 0.003 * np.sin(2 * np.pi * 0.5 * k)), L) * 0.05 + sweep(2710 + 0 * k, L) * 0.04
    out = sat((roar * beat + whine), 1.3)
    return _loopify(norm(out, 0.85))


def build():
    path = os.path.join(ROOT, "sounds.json")
    with open(path) as fh:
        sounds = json.load(fh)

    def add(name, xs, subtitle=None):
        files = []
        for i, x in enumerate(xs):
            fn = f"{name}_{i + 1}" if len(xs) > 1 else name
            write_ogg(os.path.join(OUT, fn + ".ogg"), x)
            files.append(f"gunsmod:{fn}")
        sounds[name] = {"sounds": files, "subtitle": f"subtitles.gunsmod.{subtitle or name}"}

    add("b2_bomb", [big_bomb(np.random.default_rng(2400 + v)) for v in range(2)], "b2_bomb")
    add("b2_bomb_far", [big_bomb_far(np.random.default_rng(2410 + v)) for v in range(2)], "b2_bomb")
    add("b2_release", [bay_release(np.random.default_rng(2420))])
    add("b2_drone", [drone(np.random.default_rng(2430))])
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)


if __name__ == "__main__":
    build()
    print("ok")
