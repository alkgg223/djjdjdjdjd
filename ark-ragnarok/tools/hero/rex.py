"""Hero Tyrannosaurus rex sculpt (GeckoLib model space, pixels; +y up, ground y=0, faces -z)."""
import math
import numpy as np
from sdfkit import Sculpt, normalize

S = (-1, 1)


def side_name(s):
    return "l" if s > 0 else "r"


LX = 12.4          # half track width of the legs (wide, heavy stance)
LIFT = 6.5         # leg stretch: everything above the knees is raised by this much
HEAD_PIV = np.array([0.0, 63.5, -51.0])
HEAD_S = 1.2       # the skull is scaled up around its pivot: a big, deep T-rex head
HEAD_BONES = {"head", "jaw"}


def _sm(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def X(p, bone=None):
    """global warp applied to every sculpt point, bone pivot and detail mesh"""
    p = np.array(p, dtype=float)
    if bone in HEAD_BONES:
        p = HEAD_PIV + (p - HEAD_PIV) * HEAD_S
    p[1] = p[1] + LIFT * _sm(8.0, 40.0, p[1])
    return p


def finalize(sc):
    from sdfkit import frame_from
    for p in sc.prims:
        b = p.bones[0][0]
        hs = HEAD_S if b in HEAD_BONES else 1.0
        g = p.geo
        if p.kind == "ellipsoid":
            g["c"] = X(g["c"], b)
            g["r"] = g["r"] * hs
        elif p.kind == "roundcone":
            g["a"], g["b"] = X(g["a"], b), X(g["b"], b)
            g["ra"], g["rb"] = g["ra"] * hs, g["rb"] * hs
            g["F"] = frame_from(g["b"] - g["a"], (0, 1, 0) if abs(normalize(g["b"] - g["a"])[1]) < 0.9 else (0, 0, -1))
            g["L"] = float(np.linalg.norm(g["b"] - g["a"]))
        elif p.kind == "roundbox":
            g["c"] = X(g["c"], b)
        p.k = p.k * hs
        p.bbox = p._bbox()
    for name, (par, piv) in list(sc.bones.items()):
        sc.bones[name] = (par, X(piv, name))
    return sc


def skeleton(sc):
    sc.bone("root", None, (0, 0, 0))
    sc.bone("body", "root", (0, 51, 10))
    sc.bone("torso", "body", (0, 50, -4))
    sc.bone("neck1", "torso", (0, 56, -30))
    sc.bone("neck2", "neck1", (0, 61.5, -40))
    sc.bone("head", "neck2", (0, 63.5, -51))
    sc.bone("jaw", "head", (0, 60.6, -54.5))
    for s in S:
        n = side_name(s)
        sc.bone("arm_" + n, "torso", (s * 9.8, 45, -29.5))
        sc.bone("forearm_" + n, "arm_" + n, (s * 11.4, 39.8, -31.2))
        sc.bone("hand_" + n, "forearm_" + n, (s * 11.8, 36.6, -35.0))
        sc.bone("thigh_" + n, "body", (s * (LX - 1.6), 50.5, 10))
        sc.bone("shin_" + n, "thigh_" + n, (s * LX, 31.6, 0.5))
        sc.bone("ankle_" + n, "shin_" + n, (s * LX, 13.4, 9.0))
        sc.bone("foot_" + n, "ankle_" + n, (s * LX, 3.4, 4.4))
        for t in (2, 3, 4):
            sc.bone("toe%d_%s" % (t, n), "foot_" + n, toe_base(s, t))
    prev = "body"
    for i, c in enumerate(TAIL_PTS[:8]):
        sc.bone("tail%d" % (i + 1), prev, c)
        prev = "tail%d" % (i + 1)


TAIL_PTS = [(0, 52.5, 19), (0, 53.4, 31), (0, 52.6, 43), (0, 50.9, 54.5), (0, 48.8, 65.5), (0, 46.6, 76), (0, 44.6, 85.5),
            (0, 43.2, 93.5), (0, 42.4, 100.5), (0, 42.0, 106.5)]
TAIL_R = [12.2, 10.5, 8.7, 7.0, 5.45, 4.05, 2.9, 1.9, 1.05, 0.3]
TAIL_BONES = ["body", "tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7", "tail8", "tail8"]


def toe_base(s, t):
    off = {2: -1.7, 3: 0.0, 4: 1.7}[t]
    return (s * (LX + off), 2.1, 1.8)


def toe_dir(s, t):
    yaw = {2: -0.40, 3: 0.03, 4: 0.37}[t] * s
    return np.array([math.sin(yaw), 0.0, -math.cos(yaw)])


TOE_LEN = {2: 9.0, 3: 11.2, 4: 9.6}


def build(details=True):
    sc = Sculpt()
    skeleton(sc)
    # ------------------------------------------------------------------ torso: deep barrel chest, broad hips
    sc.ell((0, 54.2, 9), (11.8, 10.8, 14.5), bone="body", k=3.0)                              # pelvis / hips
    sc.ell((0, 50, -1), (13.2, 13.6, 11.2), bone=[("body", 0.5), ("torso", 0.5)], k=4.5)     # waist
    sc.ell((0, 45.2, -12.5), (14.6, 16.2, 20.0), bone="torso", k=4.5)                         # barrel chest / belly
    sc.ell((0, 48.4, -26.0), (12.2, 13.4, 10.6), bone="torso", k=4.0)                         # front ribcage
    sc.ell((0, 38.0, 3.0), (3.8, 3.0, 7.5), bone="body", k=4.0)                               # pubic boot
    for s in S:
        sc.ell((s * 8.0, 49.5, -29), (5.2, 6.8, 6.4), bone="torso", k=2.8)                   # shoulder girdle
        sc.ell((s * 9.6, 45.6, -29.0), (2.5, 2.8, 2.6), bone=[("torso", 0.5), ("arm_" + side_name(s), 0.5)], k=1.6)  # deltoid
        sc.ell((s * 10.8, 46, -13.5), (3.4, 11.0, 13.5), bone="torso", k=4.5, rot=(0.1, 0, s * 0.08))   # flank / ribs
    sc.cone((0, 58.5, -25), (0, 61.6, 6), 6.0, 6.8, bone=[("torso", 0.6), ("body", 0.4)], k=5.5, sq=(1.15, 1.0))   # back muscles
    # ------------------------------------------------------------------ neck: short, thick, S-curved
    sc.spline_tube([(0, 55.5, -28), (0, 60.0, -37.5), (0, 63.0, -45.0), (0, 64.8, -51.5)], [12.6, 11.0, 10.0, 9.4],
                   ["torso", "neck1", "neck2", "head"], k=3.5, sq=(0.88, 1.0), sub=4)
    for s in S:
        sc.ell((s * 5.8, 59.0, -39), (4.0, 7.4, 8.6), bone="neck1", k=2.8, rot=(-0.45, 0, 0))   # neck muscles
    sc.ell((0, 50.6, -40.0), (6.6, 6.2, 9.4), bone=[("neck1", 0.7), ("torso", 0.3)], k=3.2, rot=(-0.3, 0, 0))   # throat
    # ------------------------------------------------------------------ skull: huge, deep, broad at the back
    H = "head"
    sc.ell((0, 66.4, -56.0), (9.6, 8.2, 6.8), bone=H, k=2.4)                                   # cranium
    for s in S:
        sc.ell((s * 7.6, 65.6, -55.4), (3.7, 5.4, 5.2), bone=H, k=2.0)                          # temporal flare (jaw muscles)
    sc.cone((0, 66.5, -60.0), (0, 64.0, -78.6), 7.3, 4.7, bone=H, k=2.0, sq=(0.7, 1.0))         # deep snout
    for s in S:
        sc.cone((s * 4.75, 61.9, -59.5), (s * 3.15, 61.1, -78.0), 2.85, 1.9, bone=H, k=1.8)    # upper lip / maxilla
    sc.ell((0, 63.9, -79.6), (3.3, 4.5, 2.6), bone=H, k=1.6)                                   # tall blunt snout tip
    if details:
        for i in range(8):                                                                    # nasal rugosities
            z = -61.5 - i * 2.2
            t = (z + 60.0) / -18.6
            ytop = 66.5 + 7.3 - t * (66.5 + 7.3 - 64.0 - 4.7)
            sc.ell((0, ytop - 0.4, z), (1.8 - 0.08 * i, 1.0, 1.25), bone=H, k=0.8, mat="horn_skin")
    for s in S:
        sc.ell((s * 6.7, 71.4, -59.4), (2.7, 1.9, 2.9), bone=H, k=1.4, rot=(0, 0, s * 0.3))      # postorbital boss
        sc.ell((s * 4.8, 72.5, -63.4), (1.9, 1.7, 2.5), bone=H, k=1.2, mat="horn_skin")           # lacrimal horn
        sc.ell((s * 7.7, 62.9, -59.5), (2.5, 3.3, 5.1), bone=H, k=1.6)                           # jugal cheek
        sc.ell((s * 8.7, 61.6, -56.8), (1.4, 1.6, 1.9), bone=H, k=1.0, mat="horn_skin")           # jugal horn
        sc.ell((s * 8.3, 68.2, -61.4), (2.0, 1.85, 2.3), bone=H, op="sub", k=0.6)                # eye socket
        sc.ell((s * 7.85, 69.55, -61.5), (1.15, 0.8, 2.4), bone=H, k=0.7, mat="lid")              # upper eyelid
        sc.ell((s * 7.8, 66.75, -61.6), (0.95, 0.65, 2.1), bone=H, k=0.6, mat="lid")              # lower eyelid
        sc.ell((s * 2.65, 66.1, -78.9), (0.85, 0.75, 1.6), bone=H, op="sub", k=0.5, rot=(0.25, s * 0.35, 0))  # nostril
    sc.cone((0, 59.3, -59.0), (0, 59.4, -77.0), 2.8, 1.7, bone=H, op="sub", k=0.9, sq=(1.0, 0.55))   # palate
    # ------------------------------------------------------------------ lower jaw: deep at the back, curved
    J = "jaw"
    sc.cone((0, 58.2, -53.6), (0, 58.7, -78.0), 5.6, 2.9, bone=J, group="jaw", k=1.8, sq=(0.92, 0.85))  # mandible
    sc.ell((0, 56.2, -55.2), (5.6, 4.4, 4.8), bone=J, group="jaw", k=2.6)                              # deep angular
    sc.ell((0, 58.5, -77.4), (2.8, 2.6, 2.0), bone=J, group="jaw", k=1.3)                              # chin
    for s in S:
        sc.cone((s * 3.45, 61.2, -55.2), (s * 2.35, 60.7, -77.0), 1.7, 1.15, bone=J, group="jaw", k=1.2)  # dentary lips
    sc.cone((0, 62.0, -56.5), (0, 61.0, -76.0), 2.6, 1.4, bone=J, group="jaw", op="sub", k=0.9, sq=(1.0, 0.7))  # mouth floor
    sc.ell((0, 60.0, -66.0), (2.6, 1.05, 9.0), bone=J, group="tongue", k=1.0, mat="tongue")
    # ------------------------------------------------------------------ arms (tiny, two fingers)
    for s in S:
        n = side_name(s)
        sc.cone((s * 9.8, 45.0, -29.5), (s * 11.4, 39.8, -31.2), 2.15, 1.55, bone="arm_" + n, k=1.2)
        sc.cone((s * 11.4, 39.8, -31.2), (s * 11.8, 36.6, -35.0), 1.55, 1.2, bone="forearm_" + n, k=1.0)
        sc.ell((s * 11.45, 39.9, -31.1), (1.5, 1.5, 1.65), bone=[("arm_" + n, 0.5), ("forearm_" + n, 0.5)], k=0.8)  # elbow
        sc.ell((s * 11.85, 36.1, -35.8), (1.1, 1.2, 1.4), bone="hand_" + n, k=0.8)
        for f, dx in ((1, -0.45), (2, 0.5)):
            a = np.array([s * (11.85 + dx), 35.7, -36.4])
            b = a + np.array([s * dx * 0.3, -1.3, -1.7])
            sc.cone(a, b, 0.6, 0.44, bone="hand_" + n, k=0.5)
    # ------------------------------------------------------------------ legs: massive thighs, long shins
    for s in S:
        n = side_name(s)
        th, sh, an, ft = "thigh_" + n, "shin_" + n, "ankle_" + n, "foot_" + n
        fw = (0, 0, -1)
        x0 = s * (LX - 1.6)
        xl = s * LX
        sc.cone((x0, 50.0, 10.0), (xl - s * 0.2, 31.6, 0.5), 10.6, 5.2, bone=th, k=3.2, sq=(0.76, 1.0), up=fw)   # femur mass
        sc.ell((xl - s * 0.5, 42.0, 3.4), (6.4, 10.2, 6.4), bone=th, k=2.8, rot=(-0.5, 0, 0))                  # quadriceps
        sc.ell((xl - s * 1.1, 43.4, 14.8), (5.8, 9.2, 5.8), bone=th, k=2.8, rot=(0.3, 0, 0))                   # hamstrings
        sc.cone((s * (LX - 4.0), 44.8, 13.0), (s * 4.4, 49.6, 32.0), 7.0, 4.0, bone=[(th, 0.45), ("body", 0.3), ("tail1", 0.25)], k=3.4)  # caudofemoralis
        sc.ell((xl, 31.9, 0.4), (4.5, 4.4, 4.9), bone=[(th, 0.5), (sh, 0.5)], k=1.8)                           # knee
        sc.cone((xl, 31.6, 0.6), (xl, 13.7, 8.8), 4.75, 2.6, bone=sh, k=2.0, sq=(0.84, 1.0), up=fw)            # shin
        sc.ell((xl, 25.4, 5.8), (3.7, 6.9, 3.9), bone=sh, k=2.0, rot=(0.42, 0, 0))                             # calf
        sc.ell((xl + s * 0.2, 24.0, 1.6), (2.7, 6.2, 2.3), bone=sh, k=1.4, rot=(0.4, 0, 0))                    # tibialis
        sc.ell((xl, 13.3, 9.2), (2.85, 2.8, 3.1), bone=[(sh, 0.5), (an, 0.5)], k=1.2)                          # ankle
        sc.cone((xl, 13.1, 9.0), (xl, 3.7, 4.3), 2.6, 2.45, bone=an, k=1.2, sq=(0.86, 1.0), up=fw)            # metatarsus
        sc.ell((xl, 2.5, 3.1), (3.9, 2.45, 3.6), bone=ft, k=1.6)                                               # ball of the foot
        for t in (2, 3, 4):
            b0 = np.array(toe_base(s, t))
            d = toe_dir(s, t)
            L = TOE_LEN[t]
            p1 = b0 + d * (L * 0.36) + np.array([0, -0.55, 0])
            p2 = b0 + d * (L * 0.68) + np.array([0, -0.75, 0])
            p3 = b0 + d * L + np.array([0, -0.9, 0])
            tb = "toe%d_%s" % (t, n)
            sc.spline_tube([b0, p1, p2, p3], [1.95, 1.7, 1.45, 1.1], [ft, tb, tb, tb], k=0.9, sq=(1.0, 0.82), sub=3)
            for kpt, kr in ((p1, 1.85), (p2, 1.58)):                                                           # knuckles
                sc.ell(kpt + np.array([0, 0.35, 0]), (kr, kr * 0.75, kr * 0.9), bone=tb, k=0.6)
        sc.cone((s * (LX - 0.8), 7.6, 7.6), (s * (LX - 1.8), 4.9, 9.9), 1.0, 0.65, bone=an, k=0.6)          # dewclaw toe
    # ------------------------------------------------------------------ tail: thick muscular base, horizontal
    sc.spline_tube(TAIL_PTS, TAIL_R, TAIL_BONES, k=2.8, sq=(0.8, 1.0), sub=4)
    return finalize(sc)


# ------------------------------------------------------------------ explicit detail meshes (teeth, claws, eyes)
def teeth_specs():
    """(bone, path points, radii) for every tooth.  Upper teeth hang from the lip line, lower teeth rise from the dentary."""
    out = []
    # upper: 4 premaxillary per side (small, D-shaped) then 11 maxillary (big in the middle)
    for s in S:
        for i in range(4):
            z = -80.6 + i * 1.0
            x = s * (1.1 + i * 0.62)
            base = np.array([x, 59.7, z])
            h = 1.15 + 0.12 * i
            out.append(("head", curved_tooth(base, h, 0.34, back=0.18), "upper"))
        for i in range(11):
            t = i / 10.0
            z = -77.0 + i * 1.75
            x = s * (3.55 + 1.5 * t)
            y = 59.6 - 0.04 * i
            size = 1.25 + 1.35 * math.sin(min(1.0, (t + 0.08) * 1.15) * math.pi) ** 0.8
            size = max(0.9, size)
            out.append(("head", curved_tooth(np.array([x, y, z]), size, 0.42 + 0.12 * size, back=0.26), "upper"))
    # lower: 13 per side, a little smaller, pointing up and slightly outwards
    for s in S:
        for i in range(13):
            t = i / 12.0
            z = -77.4 + i * 1.6
            x = s * (2.05 + 1.25 * t)
            y = 61.3 - 0.02 * i
            size = 1.0 + 1.0 * math.sin(min(1.0, (t + 0.12) * 1.1) * math.pi) ** 0.8
            path, radii = curved_tooth(np.array([x, y, z]), size, 0.3 + 0.1 * size, back=0.22, up=True)
            out.append(("jaw", (path, radii), "lower"))
    warped = []
    for bone, (path, radii), kind in out:
        warped.append((bone, ([X(p, bone) for p in path], [r * HEAD_S for r in radii]), kind))
    return warped


def curved_tooth(base, h, r0, back=0.25, up=False):
    sgn = 1.0 if up else -1.0
    pts, radii = [], []
    for k in range(4):
        t = k / 3.0
        pts.append(base + np.array([0, sgn * h * t, back * h * t * t]))
        radii.append(r0 * (1 - t) ** 0.9 + 0.03)
    return pts, radii


def claw_specs():
    out = []
    for s in S:
        n = side_name(s)
        for t in (2, 3, 4):
            b0 = np.array(toe_base(s, t))
            d = toe_dir(s, t)
            L = TOE_LEN[t]
            tip = b0 + d * L + np.array([0, -0.9, 0])
            length = {2: 2.7, 3: 3.2, 4: 2.8}[t]
            pts, radii = [], []
            for k in range(5):
                u = k / 4.0
                pts.append(tip + d * (length * u - 0.35) + np.array([0, 0.25 - (0.95 + 0.25) * u * u, 0]))
                radii.append(1.08 * (1 - u) ** 0.85 + 0.03)
            out.append(("toe%d_%s" % (t, n), pts, radii))
        # dewclaw
        a = np.array([s * (LX - 1.8), 4.9, 9.9])
        d = normalize(np.array([-s * 0.3, -0.6, 0.75]))
        pts = [a + d * (1.6 * k / 3.0) + np.array([0, -0.15 * (k / 3.0) ** 2, 0]) for k in range(4)]
        out.append(("ankle_" + n, pts, [0.62, 0.48, 0.3, 0.05]))
        # hand claws
        for f, dx in ((1, -0.45), (2, 0.5)):
            a = np.array([s * (11.85 + dx), 35.7, -36.4]) + np.array([s * dx * 0.3, -1.3, -1.7])
            d = normalize(np.array([0, -0.75, -0.9]))
            pts = [a + d * (1.5 * k / 3.0) + np.array([0, -0.25 * (k / 3.0) ** 2, 0]) for k in range(4)]
            out.append(("hand_" + n, pts, [0.42, 0.32, 0.2, 0.04]))
    return [(b, [X(p, b) for p in pts], r) for b, pts, r in out]


EYES = [(tuple(X((s * 7.55, 68.2, -61.55), "head")), normalize((s * 0.85, 0.06, -0.55))) for s in S]
EYE_R = 1.5 * HEAD_S
