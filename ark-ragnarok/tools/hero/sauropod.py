"""Sauropod sculpt: Brontosaurus template (6-bone neck, 9-bone tail, columnar elephant-like legs); Diplodocus is a variant.
Space: GeckoLib model pixels (+y up, ground y = 0, faces -z)."""
import math
import numpy as np
from sdfkit import Sculpt, normalize, frame_from

S = (-1, 1)


def side_name(s):
    return "l" if s > 0 else "r"


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


DEFAULTS = dict(torso=(1.0, 1.0, 1.0), neck_len=1.0, neck_thick=1.0, neck_raise=0.0, head_scale=1.0, leg_len=1.0,
                leg_thick=1.0, tail_len=1.0, tail_thick=1.0, snout=1.0, features=(), eye_size=1.0)

HIP = np.array([0.0, 52.0, 16.0])
NECK_PTS = [(0, 52, -22), (0, 60, -34), (0, 70, -44), (0, 80, -52), (0, 89, -58), (0, 97, -62.5), (0, 103, -66)]
NECK_R = [12.5, 9.6, 7.6, 6.1, 5.0, 4.2, 3.7]
NECK_BONES = ["torso", "neck1", "neck2", "neck3", "neck4", "neck5", "neck6"]
TAIL_PTS = [(0, 54, 30), (0, 54.5, 44), (0, 52.5, 58), (0, 49.5, 72), (0, 45.5, 86), (0, 41.5, 99), (0, 37.5, 111), (0, 33.5, 122),
            (0, 30.5, 132), (0, 28.5, 140)]
TAIL_R = [10.5, 8.3, 6.5, 5.0, 3.7, 2.6, 1.8, 1.15, 0.7, 0.3]
TAIL_BONES = ["body"] + ["tail%d" % i for i in range(1, 10)]


def region_of(bone):
    if bone in ("head", "jaw"):
        return "head"
    if bone.startswith("neck"):
        return "neck"
    if bone.startswith(("arm", "forearm", "hand", "thigh", "shin", "ankle", "foot")):
        return "leg"
    if bone.startswith("tail"):
        return "tail"
    return "torso"


class Sauropod:
    def __init__(self, params):
        self.P = dict(DEFAULTS)
        self.P.update(params)
        self.ground = 0.0
        # neck as a polyline in template space: every neck point is mapped by its arclength parameter
        self.npts = np.array(NECK_PTS, dtype=float)

    def W(self, p, bone):
        P = self.P
        p = np.array(p, dtype=float)
        reg = region_of(bone) if bone else "torso"
        tor = np.array(P["torso"], dtype=float)

        def T_t(q):
            return HIP + (q - HIP) * tor
        if reg == "torso":
            return T_t(p)
        if reg == "leg":
            sx = np.sign(p[0]) if abs(p[0]) > 1e-6 else 1.0
            front = p[2] < 0
            J = np.array([sx * 12.0, 46.0, -16.0]) if front else np.array([sx * 12.0, 50.0, 16.0])
            out = T_t(J) + (p - J) * np.array([1.0, P["leg_len"], P["leg_len"] ** 0.5])
            out[0] = p[0] * tor[0] ** 0.6
            return out
        if reg == "tail":
            Tb = np.array(TAIL_PTS[0], dtype=float)
            d = (p - Tb) * np.array([P["tail_thick"], P["tail_len"] ** 0.5, P["tail_len"]])
            return T_t(Tb) + d
        base = self.npts[0]
        R = rot_x(P["neck_raise"])
        if reg == "neck":
            d = p - base
            return T_t(base) + R @ (d * np.array([P["neck_thick"], P["neck_len"], P["neck_len"]]))
        hp = self.npts[-1]
        hw = T_t(base) + R @ ((hp - base) * np.array([P["neck_thick"], P["neck_len"], P["neck_len"]]))
        return hw + R @ ((p - hp) * P["head_scale"] * np.array([1, 1, 1]))

    def radius_scale(self, bone):
        P = self.P
        return {"head": P["head_scale"], "torso": float(np.mean(P["torso"][:2])), "neck": P["neck_thick"], "leg": P["leg_thick"],
                "tail": P["tail_thick"]}[region_of(bone)]

    def finalize(self, sc):
        for p in sc.prims:
            b = max(p.bones, key=lambda bw: bw[1])[0]
            rs = self.radius_scale(b)
            g = p.geo
            if p.kind == "ellipsoid":
                g["c"] = self.W(g["c"], b)
                g["r"] = g["r"] * (np.array(self.P["torso"]) if region_of(b) == "torso" else rs)
            elif p.kind == "roundcone":
                g["a"], g["b"] = self.W(g["a"], b), self.W(g["b"], b)
                g["ra"], g["rb"] = g["ra"] * rs, g["rb"] * rs
                ax = normalize(g["b"] - g["a"])
                g["F"] = frame_from(g["b"] - g["a"], (0, 1, 0) if abs(ax[1]) < 0.9 else (0, 0, -1))
                g["L"] = float(np.linalg.norm(g["b"] - g["a"]))
            p.bbox = p._bbox()
        for name, (par, piv) in list(sc.bones.items()):
            sc.bones[name] = (par, self.W(piv, name))
        lows = [p.bbox[0][1] for p in sc.prims if region_of(max(p.bones, key=lambda bw: bw[1])[0]) == "leg"]
        self.ground = 0.3 - min(lows)
        d = np.array([0, self.ground, 0])
        for p in sc.prims:
            g = p.geo
            if p.kind == "ellipsoid":
                g["c"] = g["c"] + d
            elif p.kind == "roundcone":
                g["a"], g["b"] = g["a"] + d, g["b"] + d
            p.bbox = p._bbox()
        for name, (par, piv) in list(sc.bones.items()):
            sc.bones[name] = (par, piv + d)
        return sc

    def F(self, p, bone):
        return self.W(p, bone) + np.array([0, self.ground, 0])

    def skeleton(self, sc):
        sc.bone("root", None, (0, 0, 0))
        sc.bone("body", "root", tuple(HIP))
        sc.bone("torso", "body", (0, 50, -8))
        prev = "torso"
        for i in range(1, 7):
            sc.bone("neck%d" % i, prev, NECK_PTS[i - 1])
            prev = "neck%d" % i
        sc.bone("head", "neck6", NECK_PTS[6])
        sc.bone("jaw", "head", (0, 102.4, -68.5))
        for s in S:
            n = side_name(s)
            sc.bone("arm_" + n, "torso", (s * 12.0, 46, -16))
            sc.bone("forearm_" + n, "arm_" + n, (s * 12.5, 25, -15))
            sc.bone("hand_" + n, "forearm_" + n, (s * 12.5, 6, -16))
            sc.bone("thigh_" + n, "body", (s * 12.0, 50, 16))
            sc.bone("shin_" + n, "thigh_" + n, (s * 12.5, 27, 14))
            sc.bone("ankle_" + n, "shin_" + n, (s * 12.5, 8, 17))
            sc.bone("foot_" + n, "ankle_" + n, (s * 12.5, 3, 16))
        prev = "body"
        for i in range(1, 10):
            sc.bone("tail%d" % i, prev, TAIL_PTS[i - 1])
            prev = "tail%d" % i

    def build(self):
        sc = Sculpt()
        self.skeleton(sc)
        sc.ell((0, 52.5, 16), (13, 12, 14), bone="body", k=4.5)
        sc.ell((0, 46.5, 1), (16.5, 17.5, 23), bone=[("body", 0.5), ("torso", 0.5)], k=6.0)
        sc.ell((0, 47.0, -14), (15, 15.5, 13), bone="torso", k=5.0)
        sc.cone((0, 60, -16), (0, 62, 18), 6.5, 7.5, bone=[("torso", 0.5), ("body", 0.5)], k=6.0, sq=(1.4, 1.0))
        for s in S:
            sc.ell((s * 11.5, 46, -16), (5.5, 9, 7), bone=[("torso", 0.7), ("arm_" + side_name(s), 0.3)], k=3.5)
        sc.spline_tube(NECK_PTS, NECK_R, NECK_BONES, k=3.0, sq=(0.85, 1.0), sub=4)
        H, J = "head", "jaw"
        sn = self.P["snout"]
        sc.ell((0, 104.5, -69.0), (3.7, 3.7, 4.5), bone=H, k=1.6)
        sc.cone((0, 104.0, -71.0), (0, 102.4, -71.0 - 7.5 * sn), 3.1, 2.5, bone=H, k=1.4, sq=(0.85, 1.0))
        sc.ell((0, 107.4, -70.5), (2.0, 1.6, 2.2), bone=H, k=1.0)                       # arched nostrils
        for s in S:
            sc.ell((s * 3.1, 105.8, -70.6), (1.0, 0.9, 1.1), bone=H, op="sub", k=0.4)
            sc.ell((s * 2.9, 106.5, -70.6), (0.6, 0.45, 1.2), bone=H, k=0.4, mat="lid")
        sc.cone((0, 101.8, -69.5), (0, 101.2, -70.5 - 7.0 * sn), 2.6, 1.9, bone=J, group="jaw", k=1.2, sq=(0.85, 0.7))
        sc.ell((0, 102.0, -73.0), (1.4, 0.5, 3.2), bone=J, group="tongue", k=0.6, mat="tongue")
        for s in S:
            n = side_name(s)
            fw = (0, 0, -1)
            sc.cone((s * 12.0, 46, -16), (s * 12.5, 25, -15), 6.8, 5.2, bone="arm_" + n, k=3.0, sq=(0.9, 1.0), up=fw)
            sc.ell((s * 12.5, 25, -15), (5.0, 4.8, 5.2), bone=[("arm_" + n, 0.5), ("forearm_" + n, 0.5)], k=2.0)
            sc.cone((s * 12.5, 25, -15), (s * 12.5, 6, -16), 5.2, 4.4, bone="forearm_" + n, k=2.5, sq=(0.9, 1.0), up=fw)
            sc.ell((s * 12.5, 3.0, -16.5), (5.0, 3.0, 5.0), bone="hand_" + n, k=2.0)
            sc.cone((s * 12.0, 50, 16), (s * 12.5, 27, 14), 9.2, 6.2, bone="thigh_" + n, k=3.5, sq=(0.85, 1.0), up=fw)
            sc.ell((s * 12.5, 38, 16.5), (7.0, 10.0, 8.0), bone="thigh_" + n, k=3.0)
            sc.ell((s * 12.5, 27, 14), (6.0, 5.6, 6.2), bone=[("thigh_" + n, 0.5), ("shin_" + n, 0.5)], k=2.2)
            sc.cone((s * 12.5, 27, 14), (s * 12.5, 8, 17), 6.2, 4.6, bone="shin_" + n, k=2.5, sq=(0.9, 1.0), up=fw)
            sc.ell((s * 12.5, 8, 17), (4.6, 4.4, 4.8), bone=[("shin_" + n, 0.5), ("ankle_" + n, 0.5)], k=1.8)
            sc.ell((s * 12.5, 3.2, 15.5), (5.8, 3.2, 6.6), bone="foot_" + n, k=2.2)
        sc.spline_tube(TAIL_PTS, TAIL_R, TAIL_BONES, k=3.0, sq=(0.85, 1.0), sub=4)
        self.finalize(sc)
        return sc

    def eyes(self):
        out = [(tuple(self.F((s * 2.9, 105.8, -70.6), "head")), normalize((s * 0.95, 0.05, -0.3))) for s in S]
        return out, 0.6 * self.P["head_scale"] * self.P["eye_size"]

    def teeth_specs(self):
        return []

    def claw_specs(self):
        out = []
        for s in S:
            n = side_name(s)
            for dx in (-3.0, -1.0, 1.0, 3.0):
                base = np.array([s * (12.5 + dx), 1.3, -20.6])
                out.append(("hand_" + n, [base + np.array([0, -0.5 * k / 3.0, -1.2 * k / 3.0]) for k in range(4)],
                            [1.2, 1.1, 0.8, 0.25]))
            for dx in (-3.2, -1.1, 1.1, 3.2):
                base = np.array([s * (12.5 + dx), 1.4, 9.6])
                out.append(("foot_" + n, [base + np.array([0, -0.5 * k / 3.0, -1.4 * k / 3.0]) for k in range(4)],
                            [1.4, 1.25, 0.9, 0.3]))
        return [(b, [self.F(p, b) for p in pts], [x * self.radius_scale(b) for x in r]) for b, pts, r in out]
