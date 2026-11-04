"""v2.2 sounds: an engine set for each new aircraft (idle, power, reheat, the flyby), the cannons (GAU-22, GSh-30-1,
the A-10's GAU-8), the anti-aircraft guns (35 mm, 23 mm, 40 mm), flak bursting in the air and shells bursting on the
ground, the four surface-to-air missile launches and the radar's contact tone. Run from tools/:  python3 sounds_v22.py"""
import json
import os

import numpy as np

from sounds import (SR, OUT, ROOT, t, noise, lp, hp, bp, env_exp, pad, place, norm, sat, fade, write_ogg, reverb, sweep, _loopify, click,
                    missile_motor)
from sounds_v19 import shot, boom_tail, outdoors, explosion

# ------------------------------------------------------------------------------------------------ engines
# per type: roar cut-off, low rumble, turbine whine, a mid "howl" band, roughness, reheat weight
ENGINES = {
    "f22": dict(roar=1600, rumble=1.9, whine=3200, howl=(600, 1400, 0.5), rough=0.25, burner=1.2),
    "f35": dict(roar=1300, rumble=1.7, whine=2900, howl=(320, 700, 0.9), rough=0.45, burner=1.3),
    "su35": dict(roar=2000, rumble=1.5, whine=4500, howl=(900, 2200, 0.35), rough=0.3, burner=1.1),
    "mig29": dict(roar=1800, rumble=1.4, whine=3800, howl=(500, 1300, 0.55), rough=0.6, burner=1.0),
    "a10": dict(roar=900, rumble=0.8, whine=2350, howl=(1800, 3200, 0.9), rough=0.15, burner=0.0),
    "b2": dict(roar=700, rumble=1.9, whine=2600, howl=(250, 520, 0.4), rough=0.1, burner=0.0),
}


def engine(rng, e, power, L=3.0):
    """one engine loop at idle (power 0) or full power (1)"""
    n = int(SR * L)
    k = np.arange(n) / SR
    roar = lp(noise(L, rng), e["roar"] * (0.55 + 0.45 * power), 2) * (0.4 + 1.2 * power)
    rumble = lp(noise(L, rng), 100, 2) * e["rumble"] * (0.3 + 0.7 * power)
    h0, h1, hw = e["howl"]
    howl = bp(noise(L, rng), h0, h1) * hw * (0.5 + power)
    wf = e["whine"] * (0.82 + 0.18 * power) * (1 + 0.004 * np.sin(2 * np.pi * 0.6 * k))
    whine = sweep(wf, L) * (0.25 - 0.12 * power) + sweep(wf * 1.5, L) * 0.05 + sweep(wf * 0.5, L) * 0.06
    if e["whine"] < 2500:
        # the A-10's TF34: the famous whistle
        whine *= 2.4
    rough = 1 + e["rough"] * 0.25 * np.sin(2 * np.pi * (11 + rng.random() * 5) * k) * power
    out = sat((roar + rumble + howl) * rough + whine, 1.3 + 0.4 * power)
    return _loopify(norm(out, 0.85))


def burner(rng, e, L=3.0):
    n = int(SR * L)
    k = np.arange(n) / SR
    rumble = lp(noise(L, rng), 130, 3) * 2.3 * e["burner"] + lp(noise(L, rng), 500, 2) * 1.0
    crackle = np.zeros(n)
    for _ in range(int(150 * L * e["burner"])):
        place(crackle, click(rng, 300, 2600, 0.006 + rng.random() * 0.008, 0.3 + rng.random() * 0.5), rng.random() * (L - 0.05))
    out = sat((rumble + crackle * 0.8) * (1 + 0.15 * np.sin(2 * np.pi * 3.1 * k)), 1.8)
    return _loopify(norm(out, 0.9))


def flyby(rng, e, L=4.4):
    """the jet tearing past: rising roar, a quick pitch drop at the closest moment, the rumble dying away"""
    n = int(SR * L)
    k = np.arange(n) / SR
    peak = 1.3
    envl = np.exp(-((k - peak) / (0.45 if k.mean() else 0.45)) ** 2 * 1.0) * 0.9 + np.exp(-np.maximum(0, k - peak) / 1.2) * 0.4 * (k > peak)
    cut = np.where(k < peak, e["roar"] * 2.2, e["roar"] * 0.7)
    roar = np.zeros(n)
    base = noise(L, rng)
    for i in range(0, n, 2048):
        seg = base[i:i + 2048]
        roar[i:i + len(seg)] = lp(seg, float(cut[i]), 2)[:len(seg)]
    wf = e["whine"] * np.where(k < peak, 1.12, 0.86)
    whine = sweep(wf, L) * 0.18
    rumble = lp(noise(L, rng), 90, 2) * e["rumble"]
    out = sat((roar * 1.6 + rumble + whine) * envl, 1.6)
    return fade(norm(out, 0.92), 0.2, 0.6)


# ------------------------------------------------------------------------------------------------ guns
def rotary(rng, rate, L, boom_f, weight, growl):
    """a rotary or revolver cannon: discrete shots at `rate` a second over a growl, the shots' booms rolling into each other"""
    n = int(SR * L)
    k = np.arange(n) / SR
    out = np.zeros(n)
    one = shot(rng, cal=weight, boom_f=boom_f, body_f=600, body_tau=0.03, crack=0.8, mech=0.1, tail=0.2, size=1.2, L=0.4)
    one = one[: int(0.25 * SR)]
    step = 1.0 / rate
    at = 0.0
    while at < L - 0.05:
        place(out, one * (0.7 + 0.3 * rng.random()), at + rng.random() * 0.002)
        at += step
    out += lp(noise(L, rng), 160, 2) * growl * np.clip(k / 0.02, 0, 1)
    out = sat(out, 2.2)
    # open country: a ground bounce and a couple of echoes off the trees
    tail = pad(out.copy(), int((L + 0.5) * SR))
    for d, a, f in ((0.005, 0.45, 4000), (0.07, 0.22, 2000), (0.16, 0.14, 1200), (0.32, 0.08, 700)):
        place(tail, lp(out, f) * a, d)
    return fade(norm(tail, 0.92), 0.001, 0.25)


def aa_shot(rng, cal, boom_f, body_f, tail):
    return shot(rng, cal=cal, boom_f=boom_f, body_f=body_f, body_tau=0.08, crack=1.2, mech=0.35, tail=tail, drive=3.6, size=1.5,
                auto_ring=(1300, 2400, 3900))


def flak(rng, L=1.6):
    """a shell bursting in the air some way off: a hard crack and a short, hollow rumble"""
    n = int(L * SR)
    out = np.zeros(n)
    place(out, hp(noise(0.015, rng), 1500) * env_exp(int(0.015 * SR), 0.003) * 1.5, 0.0)
    k = t(L)
    out += np.sin(2 * np.pi * np.cumsum(70 * (1 + 1.5 * np.exp(-k / 0.02))) / SR) * env_exp(n, 0.08, 0.001) * 1.0
    out += lp(noise(L, rng), 900, 2) * env_exp(n, 0.25, 0.002) * 0.9
    out = sat(out, 2.0)
    out = boom_tail(out, rng, 0.9)
    return fade(norm(out, 0.9), 0.0005, 0.3)


def shell_hit(rng):
    return explosion(rng, 0.45, 1.2)


# ------------------------------------------------------------------------------------------------ missiles and radar
def cold_launch(rng, L=3.6):
    """S-400: the gas charge throws the missile out of its tube with a dull thump, a beat later the motor lights"""
    n = int(L * SR)
    out = np.zeros(n)
    k = t(0.6)
    thump = np.sin(2 * np.pi * np.cumsum(45 * (1 + np.exp(-k / 0.03))) / SR) * env_exp(len(k), 0.12, 0.002) * 1.6
    thump += lp(noise(0.6, rng), 400) * env_exp(len(k), 0.1, 0.002) * 0.8
    place(out, thump, 0.0)
    place(out, missile_motor(rng, L - 0.55, 1.0, 800, 1.2, 0.9) * 0.95, 0.55)
    return fade(norm(out, 0.95), 0.001, 0.4)


def contact(L=0.32):
    """a radar's contact tone: two quick rising beeps"""
    out = np.zeros(int(L * SR))
    for i, f in enumerate((1300, 1750)):
        k = t(0.09)
        b = np.sin(2 * np.pi * f * k) * np.clip(k / 0.005, 0, 1) * np.clip((0.09 - k) / 0.01, 0, 1) * 0.6
        place(out, b, i * 0.13)
    return out


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

    for i, (name, e) in enumerate(ENGINES.items()):
        seed = 3000 + i * 10
        add(name + "_idle", [engine(np.random.default_rng(seed), e, 0.0)], "jet_engine")
        add(name + "_engine", [engine(np.random.default_rng(seed + 1), e, 1.0)], "jet_engine")
        if e["burner"] > 0:
            add(name + "_burner", [burner(np.random.default_rng(seed + 2), e)], "jet_afterburner")
        add(name + "_flyby", [flyby(np.random.default_rng(seed + 3 + v), e) for v in range(2)], "jet_flyby")
    add("gun_gau22", [rotary(np.random.default_rng(3100 + v), 55, 0.32, 75, 1.0, 0.6) for v in range(2)], "jet_cannon")
    add("gun_gsh30", [rotary(np.random.default_rng(3110 + v), 30, 0.34, 55, 1.4, 0.4) for v in range(2)], "jet_cannon")
    add("gun_gau8", [rotary(np.random.default_rng(3120 + v), 65, 0.36, 42, 1.9, 1.6) for v in range(2)], "gau8")
    add("aa_35mm", [aa_shot(np.random.default_rng(3130 + v), 1.7, 46, 520, 1.0) for v in range(3)], "aa_gun")
    add("aa_23mm", [aa_shot(np.random.default_rng(3140 + v), 1.3, 62, 760, 0.7) for v in range(3)], "aa_gun")
    add("aa_40mm", [aa_shot(np.random.default_rng(3150 + v), 2.0, 38, 420, 1.3) for v in range(3)], "aa_gun")
    add("flak_burst", [flak(np.random.default_rng(3160 + v)) for v in range(3)])
    add("shell_impact", [shell_hit(np.random.default_rng(3170 + v)) for v in range(3)])
    add("sam_patriot", [missile_motor(np.random.default_rng(3180 + v), 4.2, 1.6, 700, 1.6, 1.1) for v in range(2)], "sam_launch")
    add("sam_s400", [cold_launch(np.random.default_rng(3190 + v)) for v in range(2)], "sam_launch")
    add("sam_nasams", [missile_motor(np.random.default_rng(3200 + v), 3.0, 1.1, 1100, 1.0, 0.8) for v in range(2)], "sam_launch")
    add("sam_tamir", [missile_motor(np.random.default_rng(3210 + v), 2.4, 0.9, 1400, 0.7, 0.6) for v in range(2)], "sam_launch")
    add("radar_contact", [contact()])
    with open(path, "w") as fh:
        json.dump(sounds, fh, indent=1)


if __name__ == "__main__":
    build()
    print("ok")
