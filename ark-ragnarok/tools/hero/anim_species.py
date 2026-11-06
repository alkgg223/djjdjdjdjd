"""Animation sets for every species (authored as functions of time, sampled into GeckoLib keyframes).
Convention as in rex_anim: degrees, rx > 0 swings a hanging limb forward / pitches the head up / lowers the tail."""
import math
from rex_anim import Pose, sm, bump, env, TAU, breathe, to_bedrock as _unused  # noqa: F401

SIDES = ("r", "l")


def tails(bones):
    return sorted([b for b in bones if b.startswith("tail")], key=lambda b: int(b[4:]))


def necks(bones):
    return sorted([b for b in bones if b.startswith("neck")], key=lambda b: int(b[4:]))


# ============================================================================================ bipeds
def biped_set(c):
    """c: dict(size_m, attack, bones, hip, belly, tail, neck)"""
    T = tails(c["bones"])
    pace = max(0.45, min(1.4, (c["size_m"] / 12.3) ** 0.4))
    agile = c["size_m"] < 7.5

    def gait(p, phase, style):
        A = {"walk": dict(th=21, kn=34, an=26, st=0.62, bob=0.9, roll=2.2, yaw=2.6, tail=6.5, pitch=0.0, arm=6),
             "run": dict(th=33, kn=58, an=44, st=0.46, bob=1.8, roll=3.0, yaw=3.2, tail=5.0, pitch=-5.0, arm=12),
             "sprint": dict(th=42, kn=74, an=56, st=0.36, bob=2.8, roll=3.0, yaw=2.6, tail=3.0, pitch=-10.0, arm=16)}[style]
        bobs = c["hip"] / 57.0
        for side, off in zip(SIDES, (0.0, 0.5)):
            ph = (phase + off) % 1.0
            st = A["st"]
            th = A["th"] * math.cos(math.pi * ph / st) if ph < st else -A["th"] * math.cos(math.pi * (ph - st) / (1 - st))
            sw = bump(ph, st - 0.04, 1.0)
            push = bump(ph, st * 0.65, st + 0.06)
            p.rot("thigh_" + side, th)
            p.rot("shin_" + side, -A["kn"] * sw - 6.0 - 6.0 * push)
            p.rot("ankle_" + side, A["an"] * sw + 4.0 * push)
            p.rot("foot_" + side, -14.0 * push + 10.0 * sw)
            for t in (2, 3, 4):
                p.rot("toe%d_%s" % (t, side), (18.0 * push - 22.0 * sw) * (1.0 if t == 3 else 0.85))
            p.rot("arm_" + side, A["arm"] * math.sin(TAU * ph))
        s = math.sin(TAU * phase)
        bounce = 0.5 + 0.5 * math.cos(2 * TAU * phase)
        p.pos("body", 0, -A["bob"] * bounce * bobs, 0)
        p.rot("body", A["pitch"] + 1.2 * math.cos(2 * TAU * phase), A["yaw"] * s, A["roll"] * s)
        p.rot("torso", -0.5 * A["pitch"], -0.7 * A["yaw"] * s, -0.6 * A["roll"] * s)
        p.rot("neck1", -0.3 * A["pitch"] - 1.0 * math.cos(2 * TAU * phase), 0.3 * A["yaw"] * s, 0)
        p.rot("neck2", 0, 0.25 * A["yaw"] * s, 0)
        p.rot("head", -0.4 * A["pitch"] + 1.6 * math.cos(2 * TAU * phase + 0.6), 0.35 * A["yaw"] * s, 0)
        for i, b in enumerate(T):
            w = A["tail"] * (0.35 + 0.16 * i) * math.sin(TAU * (phase - 0.07 * (i + 1)))
            p.rot(b, (-1.5 if style != "walk" else 0.0) + 0.9 * math.cos(2 * TAU * phase - 0.4 * i), w, 0)

    def loco(style, length):
        return lambda t: _g(style, length, t)

    def _g(style, length, t):
        p = Pose()
        gait(p, (t / length) % 1.0, style)
        if style == "sprint":
            p.rot("jaw", -7.0)
        return p

    def idle(t):
        p = Pose()
        b = breathe(p, t)
        p.rot("neck1", 2.0 * math.sin(TAU * t / 8.0), 4.0 * math.sin(TAU * t / 8.0 + 0.5))
        p.rot("head", 1.5 * b - 1.0, 5.0 * math.sin(TAU * t / 8.0 + 1.1))
        p.rot("jaw", -3.0 * b)
        for i, bn in enumerate(T):
            p.rot(bn, 0.6 * math.sin(TAU * t / 4.0 - 0.3 * i), 3.2 * (0.4 + 0.14 * i) * math.sin(TAU * t / 8.0 - 0.35 * i))
        for s_ in SIDES:
            p.rot("arm_" + s_, 4.0 * b)
            p.rot("forearm_" + s_, -6.0 * b)
        return p

    def roar(t):
        import rex_anim
        return rex_anim.a_roar(t)

    def bite(t):
        import rex_anim
        return rex_anim.a_bite(t)

    def attack(t):
        import rex_anim
        kind = c["attack"]
        if kind == "shake":
            return rex_anim.a_attack(t)
        p = Pose()
        if kind == "pounce":           # crouch, leap forward, rake with the sickle claws
            crouch = env(t, [(0, 0), (0.25, 1), (0.35, 0), (1.25, 0)])
            leap = env(t, [(0, 0), (0.25, 0), (0.45, 1), (0.85, 1), (1.2, 0)])
            p.pos("body", 0, -3.0 * crouch + 5.0 * leap, -6.0 * leap)
            p.rot("body", -6 * crouch + 8 * leap)
            for s_ in SIDES:
                p.rot("thigh_" + s_, 10 * crouch + 55 * leap)
                p.rot("shin_" + s_, -30 * crouch - 70 * leap)
                p.rot("toe2_" + s_, -50 * leap)
                p.rot("arm_" + s_, -60 * leap)
                p.rot("forearm_" + s_, -40 * leap)
            p.rot("jaw", -28 * leap)
            for i, bn in enumerate(T):
                p.rot(bn, -4 * leap)
        elif kind == "slash":          # therizinosaurus: rear back and swipe both scythe claws
            up = env(t, [(0, 0), (0.35, 1), (0.6, 0.3), (1.25, 0)])
            sw = env(t, [(0, 0), (0.35, 0), (0.55, 1), (0.8, 1), (1.2, 0)])
            p.rot("body", 8 * up - 6 * sw)
            for s_ in SIDES:
                p.rot("arm_" + s_, 70 * up - 95 * sw, 0, (18 if s_ == "l" else -18) * up)
                p.rot("forearm_" + s_, 40 * up - 30 * sw)
            p.rot("neck1", 12 * up - 10 * sw)
        elif kind == "headbutt":       # pachycephalosaurus: lower the dome and ram
            low = env(t, [(0, 0), (0.3, 1), (0.9, 1), (1.2, 0)])
            ram = env(t, [(0, 0), (0.35, 0), (0.5, 1), (0.7, 0.4), (1.2, 0)])
            p.rot("body", -10 * low)
            p.rot("neck1", -25 * low)
            p.rot("neck2", -10 * low)
            p.rot("head", -30 * low)
            p.pos("body", 0, -1.5 * low, -5.0 * ram)
        elif kind == "peck":
            k = env(t, [(0, 0), (0.15, 1), (0.3, 0), (0.45, 1), (0.6, 0), (1.25, 0)])
            p.rot("neck1", -18 * k)
            p.rot("neck2", -10 * k)
            p.rot("head", -14 * k)
            p.rot("jaw", -22 * k)
        elif kind == "spit":           # dilophosaurus: rear up, open wide, lunge the venom forward
            up = env(t, [(0, 0), (0.4, 1), (0.75, 1), (1.2, 0)])
            spit = env(t, [(0, 0), (0.45, 0), (0.6, 1), (0.85, 0.2), (1.2, 0)])
            p.rot("neck1", 16 * up - 10 * spit)
            p.rot("head", 14 * up - 12 * spit)
            p.rot("jaw", -(30 * up + 8 * spit))
            p.pos("body", 0, 0, -2.0 * spit)
        return p

    def hurt(t):
        import rex_anim
        return rex_anim.a_hurt(t)

    def death(t):
        import rex_anim
        p = rex_anim.a_death(t)
        k = c["hip"] / 57.5
        e = p.get("root")
        if e:
            e[3] *= k
            e[4] *= k
        return p

    def eat(t):
        import rex_anim
        return rex_anim.a_eat(t)

    def sleep(t):
        import rex_anim
        p = rex_anim.a_sleep(t)
        e = p.get("body")
        drop = c["hip"] - c["belly"] - 0.5
        if e:
            e[4] = e[4] - (-30.5) - drop
        return p

    L = lambda x: x * pace
    return {
        "idle": (idle, 8.0, True, 0.2),
        "walk": (loco("walk", L(1.6)), L(1.6), True, 0.05),
        "run": (loco("run", L(0.9)), L(0.9), True, 0.05),
        "sprint": (loco("sprint", L(0.66)), L(0.66), True, 0.04),
        "roar": (roar, 2.8, False, 0.05),
        "bite": (bite, 0.75, False, 0.025),
        "attack": (attack, 1.25, False, 0.025),
        "hurt": (hurt, 0.5, False, 0.025),
        "death": (death, 3.0, "hold_on_last_frame", 0.05),
        "eat": (eat, 4.4, True, 0.05),
        "sleep": (sleep, 5.0, True, 0.25),
    }


# ============================================================================================ quadrupeds
FRONT = ("arm", "forearm", "hand")
HIND = ("thigh", "shin", "ankle", "foot")


def quad_set(c):
    T = tails(c["bones"])
    NK = necks(c["bones"])
    pace = max(0.6, min(1.6, (c["size_m"] / 9.0) ** 0.4))
    sauro = c.get("sauropod", False)

    def gait(p, phase, style):
        A = {"walk": dict(f=18, h=20, kn=30, st=0.65, bob=0.6, roll=1.6, tail=5.0, neck=2.0),
             "run": dict(f=28, h=30, kn=48, st=0.5, bob=1.2, roll=2.2, tail=4.0, neck=3.0),
             "sprint": dict(f=36, h=38, kn=62, st=0.4, bob=2.0, roll=2.0, tail=3.0, neck=4.0)}[style]
        order = {"walk": {"thigh_l": 0.0, "arm_l": 0.25, "thigh_r": 0.5, "arm_r": 0.75},
                 "run": {"thigh_l": 0.0, "arm_r": 0.0, "thigh_r": 0.5, "arm_l": 0.5},
                 "sprint": {"thigh_l": 0.0, "thigh_r": 0.1, "arm_l": 0.5, "arm_r": 0.6}}[style]
        for s_ in SIDES:
            for limb, amp, chain in (("arm_" + s_, A["f"], FRONT), ("thigh_" + s_, A["h"], HIND)):
                ph = (phase + order[limb]) % 1.0
                st = A["st"]
                sw_ang = amp * math.cos(math.pi * ph / st) if ph < st else -amp * math.cos(math.pi * (ph - st) / (1 - st))
                sw = bump(ph, st - 0.03, 1.0)
                if chain is FRONT:
                    p.rot("arm_" + s_, sw_ang)
                    p.rot("forearm_" + s_, A["kn"] * 0.8 * sw)          # wrist/elbow folds backwards while stepping
                    p.rot("hand_" + s_, -A["kn"] * 0.5 * sw)
                else:
                    p.rot("thigh_" + s_, sw_ang)
                    p.rot("shin_" + s_, -A["kn"] * sw - 4)
                    p.rot("ankle_" + s_, A["kn"] * 0.7 * sw + 3)
                    p.rot("foot_" + s_, 8 * sw)
        s = math.sin(TAU * phase)
        bounce = 0.5 + 0.5 * math.cos(2 * TAU * phase)
        p.pos("body", 0, -A["bob"] * bounce * c["hip"] / 36.0, 0)
        p.rot("body", 0.8 * math.cos(2 * TAU * phase), 1.5 * s, A["roll"] * s)
        p.rot("torso", 0, -1.0 * s, -0.5 * A["roll"] * s)
        for i, nb in enumerate(NK):
            p.rot(nb, A["neck"] * 0.4 * math.cos(2 * TAU * phase + i * 0.4) / max(1, len(NK) / 2),
                  1.2 * s * (0.5 + 0.2 * i) / max(1, len(NK) / 2))
        p.rot("head", -1.0 * math.cos(2 * TAU * phase), 0.8 * s)
        for i, b in enumerate(T):
            p.rot(b, 0.6 * math.cos(2 * TAU * phase - 0.4 * i), A["tail"] * (0.3 + 0.14 * i) * math.sin(TAU * (phase - 0.07 * (i + 1))))

    def loco(style, length):
        def f(t):
            p = Pose()
            gait(p, (t / length) % 1.0, style)
            return p
        return f

    def idle(t):
        p = Pose()
        b = breathe(p, t, period=5.0)
        for i, nb in enumerate(NK):
            p.rot(nb, 1.5 * math.sin(TAU * t / 8 + i * 0.3) / max(1, len(NK) / 2), 4.0 * math.sin(TAU * t / 10 + i * 0.25) / max(1, len(NK) / 2))
        p.rot("head", 2.0 * b, 4.0 * math.sin(TAU * t / 10 + 1.0))
        p.rot("jaw", -2.0 * b)
        for i, bn in enumerate(T):
            p.rot(bn, 0.5 * math.sin(TAU * t / 5 - 0.3 * i), 3.0 * (0.4 + 0.14 * i) * math.sin(TAU * t / 8 - 0.35 * i))
        return p

    def roar(t):
        p = Pose()
        rise = env(t, [(0, 0), (0.6, 1), (2.0, 1), (2.7, 0)])
        for i, nb in enumerate(NK):
            p.rot(nb, 16 * rise / max(1, len(NK) / 2))
        p.rot("head", 14 * rise)
        p.rot("jaw", -env(t, [(0, 0), (0.6, 26), (1.9, 24), (2.4, 0)]))
        p.pos("body", 0, 0.8 * rise, 0)
        for i, bn in enumerate(T):
            p.rot(bn, -4 * rise * (1 - i / 9))
        breathe(p, t, period=2.8, amt=0.03 * rise)
        return p

    def attack(t):
        p = Pose()
        kind = c["attack"]
        if kind == "horn":            # lower the horns and toss the head up
            low = env(t, [(0, 0), (0.35, 1), (0.55, 0.2), (1.2, 0)])
            toss = env(t, [(0, 0), (0.35, 0), (0.55, 1), (0.8, 0.3), (1.2, 0)])
            for nb in NK:
                p.rot(nb, (-16 * low + 18 * toss) / len(NK))
            p.rot("head", -22 * low + 26 * toss)
            p.pos("body", 0, -1.2 * low, -4.0 * toss)
            for s_ in SIDES:
                p.rot("arm_" + s_, -8 * low)
        elif kind == "tail":          # swing the body and whip the tail sideways
            sw = math.sin(TAU * env(t, [(0, 0), (1.2, 1)]) * 0.5) * env(t, [(0, 0), (0.2, 1), (1.0, 1), (1.25, 0)])
            p.rot("body", 0, 14 * sw, 0)
            p.rot("torso", 0, -10 * sw, 0)
            for i, bn in enumerate(T):
                p.rot(bn, -3 * abs(sw), 9 * sw * (0.5 + 0.2 * i))
        elif kind == "rear":          # rise on the hind legs and strike with the forelimbs / thumb spikes
            up = env(t, [(0, 0), (0.4, 1), (0.75, 1), (1.2, 0)])
            hit = env(t, [(0, 0), (0.55, 0), (0.7, 1), (0.9, 0), (1.2, 0)])
            p.rot("body", 22 * up - 10 * hit)
            for s_ in SIDES:
                p.rot("arm_" + s_, -50 * up + 40 * hit)
                p.rot("forearm_" + s_, -30 * up)
                p.rot("thigh_" + s_, -14 * up)
            p.rot("neck1", -8 * up)
        elif kind == "stomp":         # sauropod: rear up and stamp the front feet down
            up = env(t, [(0, 0), (0.7, 1), (1.0, 1), (1.25, 0)])
            p.rot("body", 24 * up)
            for s_ in SIDES:
                p.rot("arm_" + s_, -35 * up)
                p.rot("forearm_" + s_, -25 * up)
                p.rot("thigh_" + s_, -18 * up)
            for i, nb in enumerate(NK):
                p.rot(nb, -6 * up / 2)
            for i, bn in enumerate(T):
                p.rot(bn, 6 * up)
        else:                         # bite
            lunge = env(t, [(0, 0), (0.3, 1), (0.45, 1), (0.9, 0), (1.25, 0)])
            for nb in NK:
                p.rot(nb, -12 * lunge / len(NK))
            p.rot("head", -8 * lunge)
            p.rot("jaw", -env(t, [(0, 0), (0.25, 26), (0.35, 0), (1.25, 0)]))
            p.pos("body", 0, 0, -2.0 * lunge)
        return p

    def bite(t):
        p = Pose()
        lunge = env(t, [(0, 0), (0.25, 1), (0.4, 1), (0.75, 0)])
        for nb in NK:
            p.rot(nb, -10 * lunge / len(NK))
        p.rot("head", -10 * lunge)
        p.rot("jaw", -env(t, [(0, 0), (0.2, 24), (0.32, 0), (0.75, 0)]))
        return p

    def hurt(t):
        p = Pose()
        k = env(t, [(0, 0), (0.1, 1), (0.5, 0)])
        p.rot("torso", 0, 6 * k, 0)
        for nb in NK:
            p.rot(nb, 8 * k / len(NK), -4 * k / len(NK))
        p.rot("head", 10 * k)
        p.rot("jaw", -12 * k)
        for i, bn in enumerate(T):
            p.rot(bn, 0, -8 * k * math.sin(TAU * (t * 2 - 0.08 * i)))
        return p

    def death(t):
        p = Pose()
        fall = env(t, [(0, 0), (0.6, 0), (1.6, 1), (3.0, 1)])
        stagger = env(t, [(0, 0), (0.4, 1), (0.9, 1), (1.4, 0)])
        for nb in NK:
            p.rot(nb, (10 * stagger - 10 * fall) / len(NK))
        p.rot("head", 10 * stagger - 14 * fall)
        p.rot("jaw", -(20 * stagger + 12 * fall))
        p.rot("root", 0, 0, -80 * fall)
        h = c["hip"]
        p.pos("root", -h * 0.78 * fall, h * 0.22 * fall, 0)
        for s_ in SIDES:
            p.rot("thigh_" + s_, 18 * fall)
            p.rot("arm_" + s_, -16 * fall)
        for i, bn in enumerate(T):
            p.rot(bn, 2.0 * fall, -3 * fall * (i / 8))
        return p

    def eat(t):
        p = Pose()
        down = 1.0
        chew = abs(math.sin(TAU * 1.6 * t))
        tear = bump(t % 2.2, 0.5, 1.1)
        n = max(1, len(NK))
        for nb in NK:
            p.rot(nb, (-30 * down + 8 * tear) / n * (1.6 if sauro else 1.0))
        p.rot("head", -18 * down + 6 * tear)
        p.rot("jaw", -8 * chew)
        for s_ in SIDES:
            p.rot("arm_" + s_, 6)
            p.rot("forearm_" + s_, 6)
        return p

    def sleep(t):
        p = Pose()
        drop = c["hip"] - c["belly"] - 0.4
        p.pos("body", 0, -drop, 0)
        for s_ in SIDES:
            p.rot("thigh_" + s_, 62)
            p.rot("shin_" + s_, -125)
            p.rot("ankle_" + s_, 70)
            p.rot("arm_" + s_, -70)
            p.rot("forearm_" + s_, 125)
            p.rot("hand_" + s_, -50)
        n = max(1, len(NK))
        for nb in NK:
            p.rot(nb, -14 / n * (2.0 if sauro else 1.0))
        p.rot("head", -6)
        for i, bn in enumerate(T):
            p.rot(bn, 5.0 if i < 2 else 1.5, 2.5 * math.sin(i * 0.7))
        breathe(p, t, period=5.0, amt=0.03)
        return p

    L = lambda x: x * pace
    return {
        "idle": (idle, 10.0, True, 0.25),
        "walk": (loco("walk", L(1.8)), L(1.8), True, 0.05),
        "run": (loco("run", L(1.0)), L(1.0), True, 0.05),
        "sprint": (loco("sprint", L(0.7)), L(0.7), True, 0.04),
        "roar": (roar, 2.8, False, 0.05),
        "bite": (bite, 0.75, False, 0.025),
        "attack": (attack, 1.25, False, 0.025),
        "hurt": (hurt, 0.5, False, 0.025),
        "death": (death, 3.0, "hold_on_last_frame", 0.05),
        "eat": (eat, 4.4, True, 0.05),
        "sleep": (sleep, 5.0, True, 0.25),
    }


def to_bedrock(prefix, anims, bones):
    out = {"format_version": "1.8.0", "animations": {}}
    for name, (fn, length, loop, step) in anims.items():
        n = int(round(length / step))
        frames = [(round(i * step, 4), fn(i * step)) for i in range(n + 1)]
        used = sorted({b for _, p in frames for b in p.keys() if b in bones})
        bj = {}
        for b in used:
            rot, pos, scl = {}, {}, {}
            for t, p in frames:
                e = p.get(b, [0.0] * 9)
                k = "%g" % t
                rot[k] = [round(-e[0], 3), round(-e[1], 3), round(e[2], 3)]
                pos[k] = [round(-e[3], 3), round(e[4], 3), round(e[5], 3)]
                scl[k] = [round(1 + e[6], 4), round(1 + e[7], 4), round(1 + e[8], 4)]
            entry = {}
            if any(any(abs(x) > 1e-4 for x in v) for v in rot.values()):
                entry["rotation"] = rot
            if any(any(abs(x) > 1e-4 for x in v) for v in pos.values()):
                entry["position"] = pos
            if any(any(abs(x - 1) > 1e-5 for x in v) for v in scl.values()):
                entry["scale"] = scl
            if entry:
                bj[b] = entry
        out["animations"]["%s.%s" % (prefix, name)] = {"loop": loop, "animation_length": length, "bones": bj}
    return out
