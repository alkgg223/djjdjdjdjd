"""T-Rex animation set, authored as functions of time and sampled into GeckoLib (Bedrock) keyframes.

Authoring convention (GeckoLib internal space, degrees): rotations are applied Rz*Ry*Rx about each bone's pivot.
  rx > 0: a limb hanging down swings forward; the head / snout pitches up; the tail tip goes down.
  ry > 0: the head turns towards -x.   rz: roll.
Positions are offsets in model pixels (y up, -z forward).  Export converts to the Bedrock/GeckoLib file convention:
  json rotation = (-rx, -ry, rz), json position = (-dx, dy, dz).
"""
import json
import math

TAU = 2 * math.pi
TAIL = ["tail%d" % i for i in range(1, 9)]
SIDES = ("r", "l")   # r = phase 0, l = phase 0.5


def sm(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def bump(x, a, b):
    """smooth 0->1->0 over [a, b]"""
    if x <= a or x >= b:
        return 0.0
    return math.sin(math.pi * (x - a) / (b - a)) ** 2


def env(t, keys):
    """piecewise smooth envelope through (time, value) pairs"""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            return v0 + (v1 - v0) * sm(t0, t1, t)
    return keys[-1][1]


class Pose(dict):
    def rot(self, bone, rx=0.0, ry=0.0, rz=0.0):
        e = self.setdefault(bone, [0.0] * 9 + [1.0, 1.0, 1.0][:0])
        while len(e) < 9:
            e.append(0.0)
        e[0] += rx
        e[1] += ry
        e[2] += rz
        return self

    def pos(self, bone, dx=0.0, dy=0.0, dz=0.0):
        e = self.setdefault(bone, [0.0] * 9)
        e[3] += dx
        e[4] += dy
        e[5] += dz
        return self

    def scl(self, bone, sx=1.0, sy=1.0, sz=1.0):
        e = self.setdefault(bone, [0.0] * 9)
        e[6] += sx - 1.0
        e[7] += sy - 1.0
        e[8] += sz - 1.0
        return self


# ------------------------------------------------------------------------------------------ locomotion
def gait(p, phase, style):
    A = {"walk": dict(th=21, kn=34, an=26, st=0.62, bob=0.9, roll=2.2, yaw=2.6, tail=6.5, pitch=0.0, arm=6),
         "run": dict(th=33, kn=58, an=44, st=0.46, bob=1.8, roll=3.0, yaw=3.2, tail=5.0, pitch=-5.0, arm=12),
         "sprint": dict(th=40, kn=72, an=55, st=0.38, bob=2.6, roll=3.5, yaw=3.0, tail=3.5, pitch=-9.0, arm=16)}[style]
    for side, off in zip(SIDES, (0.0, 0.5)):
        ph = (phase + off) % 1.0
        st = A["st"]
        # thigh swings back during stance, forward during swing (smooth cosine-like profile)
        if ph < st:
            u = ph / st
            th = A["th"] * math.cos(math.pi * u)
        else:
            u = (ph - st) / (1 - st)
            th = -A["th"] * math.cos(math.pi * u)
        sw = bump(ph, st - 0.04, 1.0)                 # swing: knee flexes, foot lifts
        push = bump(ph, st * 0.65, st + 0.06)         # push-off at the end of stance
        knee = -A["kn"] * sw - 6.0 - 6.0 * push
        ankle = A["an"] * sw + 4.0 * push
        foot = -14.0 * push + 10.0 * sw
        toes = 18.0 * push - 22.0 * sw
        p.rot("thigh_" + side, th)
        p.rot("shin_" + side, knee)
        p.rot("ankle_" + side, ankle)
        p.rot("foot_" + side, foot)
        for t in (2, 3, 4):
            p.rot("toe%d_%s" % (t, side), toes * (1.0 if t == 3 else 0.85))
        p.rot("arm_" + side, A["arm"] * math.sin(TAU * ph))
    s = math.sin(TAU * phase)
    bounce = 0.5 + 0.5 * math.cos(2 * TAU * phase)
    p.pos("body", 0, -A["bob"] * bounce, 0)
    p.rot("body", A["pitch"] + 1.2 * math.cos(2 * TAU * phase), A["yaw"] * s, A["roll"] * s)
    p.rot("torso", -0.5 * A["pitch"], -0.7 * A["yaw"] * s, -0.6 * A["roll"] * s)
    p.rot("neck1", -0.3 * A["pitch"] - 1.0 * math.cos(2 * TAU * phase), 0.3 * A["yaw"] * s, 0)
    p.rot("neck2", 0, 0.25 * A["yaw"] * s, 0)
    p.rot("head", -0.4 * A["pitch"] + 1.6 * math.cos(2 * TAU * phase + 0.6), 0.35 * A["yaw"] * s, 0)
    for i, b in enumerate(TAIL):
        w = A["tail"] * (0.35 + 0.16 * i) * math.sin(TAU * (phase - 0.07 * (i + 1)))
        p.rot(b, (-1.5 if style != "walk" else 0.0) + 0.9 * math.cos(2 * TAU * phase - 0.4 * i), w, 0)
    if style == "sprint":
        p.rot("jaw", -7.0)


def breathe(p, t, period=4.0, amt=0.022):
    b = 0.5 - 0.5 * math.cos(TAU * t / period)
    s = 1 + amt * b
    p.scl("torso", s, s, 1 + amt * 0.4 * b)
    for c in ("neck1", "arm_r", "arm_l"):
        p.scl(c, 1 / s, 1 / s, 1 / (1 + amt * 0.4 * b))
    p.pos("body", 0, 0.25 * b, 0)
    return b


# ------------------------------------------------------------------------------------------ animations
def a_idle(t):
    p = Pose()
    b = breathe(p, t)
    p.rot("neck1", 2.0 * math.sin(TAU * t / 8.0), 4.0 * math.sin(TAU * t / 8.0 + 0.5))
    p.rot("head", 1.5 * b - 1.0, 5.0 * math.sin(TAU * t / 8.0 + 1.1))
    p.rot("jaw", -3.0 * b)
    for i, bn in enumerate(TAIL):
        p.rot(bn, 0.6 * math.sin(TAU * t / 4.0 - 0.3 * i), 3.2 * (0.4 + 0.14 * i) * math.sin(TAU * t / 8.0 - 0.35 * i))
    for s_ in SIDES:
        p.rot("arm_" + s_, 4.0 * b)
        p.rot("forearm_" + s_, -6.0 * b)
    return p


def a_loco(style, length):
    def f(t):
        p = Pose()
        gait(p, (t / length) % 1.0, style)
        return p
    return f


def a_roar(t):
    p = Pose()
    crouch = env(t, [(0.0, 0), (0.55, 1), (0.8, 0), (2.8, 0)])
    rise = env(t, [(0.0, 0), (0.55, 0), (0.85, 1), (2.05, 1), (2.75, 0)])
    p.pos("body", 0, -1.6 * crouch + 0.6 * rise, 0)
    p.rot("body", -4 * crouch + 5 * rise)
    p.rot("neck1", -12 * crouch + 16 * rise)
    p.rot("neck2", -4 * crouch + 7 * rise)
    p.rot("head", -9 * crouch + 17 * rise)
    jaw = env(t, [(0.0, 0), (0.6, 4), (0.85, 40), (1.95, 36), (2.4, 4), (2.8, 0)])
    shake = rise * math.sin(TAU * 4.0 * t) * sm(0.85, 1.1, t) * (1 - sm(1.8, 2.05, t))
    p.rot("jaw", -jaw - 2.0 * shake)
    p.rot("head", 0, 6.0 * shake, 2.0 * shake)
    p.rot("neck1", 0, 0, 2.5 * shake)
    for s_ in SIDES:
        p.rot("arm_" + s_, -28 * rise)
        p.rot("forearm_" + s_, -20 * rise)
    for i, bn in enumerate(TAIL):
        p.rot(bn, -5 * crouch * (1 - i / 9) + -3 * rise * (1 - i / 8), 2.5 * shake * (i / 8))
    breathe(p, t, period=2.8, amt=0.03 * rise)
    return p


def a_bite(t):
    p = Pose()
    back = env(t, [(0.0, 0), (0.18, 1), (0.26, 0), (0.75, 0)])
    lunge = env(t, [(0.0, 0), (0.16, 0), (0.3, 1), (0.42, 1), (0.75, 0)])
    p.rot("neck1", 8 * back - 15 * lunge)
    p.rot("neck2", 3 * back - 8 * lunge)
    p.rot("head", 10 * back - 9 * lunge)
    p.rot("torso", -6 * lunge)
    p.pos("body", 0, -0.8 * lunge, -3.0 * lunge)
    p.rot("body", -3 * lunge)
    jaw = env(t, [(0.0, 0), (0.18, 30), (0.28, 32), (0.33, 0), (0.75, 0)])
    p.rot("jaw", -jaw)
    for i, bn in enumerate(TAIL):
        p.rot(bn, -2.5 * lunge * (1 - i / 9))
    return p


def a_attack(t):
    """grab and shake: bite, then thrash the head left and right"""
    p = Pose()
    lunge = env(t, [(0.0, 0), (0.22, 1), (1.0, 1), (1.25, 0)])
    p.rot("neck1", -12 * lunge)
    p.rot("neck2", -6 * lunge)
    p.rot("head", -6 * lunge)
    p.pos("body", 0, -1.0 * lunge, -2.5 * lunge)
    jaw = env(t, [(0.0, 0), (0.14, 30), (0.24, 0), (1.25, 0)])
    p.rot("jaw", -jaw)
    shake = math.sin(TAU * 2.6 * (t - 0.25)) * sm(0.24, 0.34, t) * (1 - sm(0.95, 1.15, t))
    p.rot("head", 0, 18 * shake, 6 * shake)
    p.rot("neck1", 0, 10 * shake, 0)
    p.rot("neck2", 0, 8 * shake, 0)
    p.rot("torso", 0, -4 * shake, 0)
    for i, bn in enumerate(TAIL):
        p.rot(bn, 0, -9 * shake * (0.4 + i / 10))
    return p


def a_hurt(t):
    p = Pose()
    k = env(t, [(0.0, 0), (0.1, 1), (0.5, 0)])
    p.rot("torso", 0, 8 * k, 0)
    p.rot("neck1", 10 * k, -4 * k)
    p.rot("head", 12 * k, -8 * k)
    p.rot("jaw", -14 * k)
    p.pos("body", 0, -0.6 * k, 0)
    for i, bn in enumerate(TAIL):
        p.rot(bn, 0, -10 * k * math.sin(TAU * (t * 2 - 0.08 * i)))
    return p


def a_death(t):
    p = Pose()
    stagger = env(t, [(0.0, 0), (0.45, 1), (0.9, 1), (1.4, 0)])
    fall = env(t, [(0.0, 0), (0.55, 0), (1.55, 1), (3.0, 1)])
    p.rot("neck1", 14 * stagger - 10 * fall)
    p.rot("head", 16 * stagger - 22 * fall)
    p.rot("jaw", -(26 * stagger + 16 * fall))
    p.rot("body", 0, 10 * stagger, 0)
    p.rot("root", 0, 0, -84 * fall)
    p.pos("root", -46 * fall, 11.5 * fall, 0)
    for s_ in SIDES:
        p.rot("thigh_" + s_, 26 * fall - 10 * stagger)
        p.rot("shin_" + s_, -34 * fall)
        p.rot("ankle_" + s_, 24 * fall)
        p.rot("arm_" + s_, -30 * fall)
    for i, bn in enumerate(TAIL):
        p.rot(bn, 2.0 * fall, -3 * fall * (i / 8) + 4 * stagger * math.sin(TAU * t * 1.5 - i * 0.4))
    twitch = bump(t, 2.0, 2.3)
    p.rot("toe3_r", 18 * twitch)
    p.rot("head", 3 * twitch)
    return p


def a_eat(t):
    p = Pose()
    down = 1.0
    tear = bump(t % 2.2, 0.5, 1.1)
    chew = abs(math.sin(TAU * 1.6 * t)) * sm(1.05, 1.2, t % 2.2)
    p.rot("body", -9 * down + 3 * tear)
    p.rot("neck1", -26 * down + 12 * tear)
    p.rot("neck2", -10 * down + 4 * tear, 0, 3 * tear)
    p.rot("head", -14 * down + 8 * tear, 6 * tear)
    p.rot("jaw", -(10 * bump(t % 2.2, 0.2, 0.55) + 9 * chew))
    p.pos("body", 0, -1.5 * down, 0)
    for s_ in SIDES:
        p.rot("thigh_" + s_, 6 * down)
        p.rot("shin_" + s_, -8 * down)
    for i, bn in enumerate(TAIL):
        p.rot(bn, -2.5 + 0.8 * math.sin(TAU * t / 2.2 - 0.3 * i), 2.0 * math.sin(TAU * t / 4.4 - 0.35 * i))
    return p


def a_sleep(t):
    p = Pose()
    p.pos("body", 0, -30.5, 0)
    p.rot("body", 2.0)
    for s_ in SIDES:
        p.rot("thigh_" + s_, 68)
        p.rot("shin_" + s_, -142)
        p.rot("ankle_" + s_, 92)
        p.rot("foot_" + s_, -18)
        p.rot("arm_" + s_, -20)
    p.rot("neck1", -18)
    p.rot("neck2", -6)
    p.rot("head", -8)
    for i, bn in enumerate(TAIL):
        p.rot(bn, 6.0 if i < 2 else 1.5, 2.5 * math.sin(i * 0.7))
    b = breathe(p, t, period=5.0, amt=0.03)
    p.rot("jaw", -2 * b)
    return p


ANIMS = {
    # name: (function, length seconds, loop, sample step)
    "idle": (a_idle, 8.0, True, 0.2),
    "walk": (a_loco("walk", 1.6), 1.6, True, 0.05),
    "run": (a_loco("run", 0.9), 0.9, True, 0.05),
    "sprint": (a_loco("sprint", 0.68), 0.68, True, 0.04),
    "roar": (a_roar, 2.8, False, 0.05),
    "bite": (a_bite, 0.75, False, 0.025),
    "attack": (a_attack, 1.25, False, 0.025),
    "hurt": (a_hurt, 0.5, False, 0.025),
    "death": (a_death, 3.0, "hold_on_last_frame", 0.05),
    "eat": (a_eat, 4.4, True, 0.05),
    "sleep": (a_sleep, 5.0, True, 0.25),
}


def to_bedrock(prefix, bones):
    out = {"format_version": "1.8.0", "animations": {}}
    for name, (fn, length, loop, step) in ANIMS.items():
        n = int(round(length / step))
        frames = [(round(i * step, 4), fn(i * step)) for i in range(n + 1)]
        used = sorted({b for _, p in frames for b in p.keys()})
        bones_json = {}
        for b in used:
            if b not in bones:
                raise KeyError("animation %s uses unknown bone %s" % (name, b))
            rot, pos, scl = {}, {}, {}
            for t, p in frames:
                e = p.get(b, [0.0] * 9)
                key = "%g" % t
                rot[key] = [round(-e[0], 3), round(-e[1], 3), round(e[2], 3)]
                pos[key] = [round(-e[3], 3), round(e[4], 3), round(e[5], 3)]
                scl[key] = [round(1 + e[6], 4), round(1 + e[7], 4), round(1 + e[8], 4)]
            entry = {}
            if any(any(abs(x) > 1e-4 for x in v) for v in rot.values()):
                entry["rotation"] = rot
            if any(any(abs(x) > 1e-4 for x in v) for v in pos.values()):
                entry["position"] = pos
            if any(any(abs(x - 1) > 1e-5 for x in v) for v in scl.values()):
                entry["scale"] = scl
            if entry:
                bones_json[b] = entry
        out["animations"]["%s.%s" % (prefix, name)] = {"loop": loop, "animation_length": length, "bones": bones_json}
    return out


def evaluate(name, t):
    fn, length, loop, step = ANIMS[name]
    if loop is True:
        t = t % length
    return fn(min(t, length))
