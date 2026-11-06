"""Parametric quadruped sculpt (ceratopsians, thyreophorans, ornithopods, Moschops).  The defaults are a Triceratops.
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


DEFAULTS = dict(
    skull="trike", head_scale=1.0, torso=(1.0, 1.0, 1.0), neck_len=1.0, neck_thick=1.0, neck_raise=0.0,
    front_len=1.0, front_thick=1.0, hind_len=1.0, hind_thick=1.0, leg_x=1.0, tail_len=1.0, tail_thick=1.0, tail_lift=0.0,
    features=(), eye_size=1.0, teeth=True, horn_len=1.0, thumb_spike=False, fingers=False, pitch=0.0, frill=1.0,
)

HIP = np.array([0.0, 36.0, 12.0])
NECK_BASE = np.array([0.0, 36.0, -33.0])
HEAD_PIV = np.array([0.0, 38.0, -48.0])
TAIL_PTS = [(0, 40, 26), (0, 40.5, 38), (0, 39, 50), (0, 36.5, 62), (0, 33.5, 73), (0, 30.5, 83), (0, 28.2, 91.5), (0, 26.8, 98)]
TAIL_R = [9.5, 7.8, 6.0, 4.4, 3.1, 2.1, 1.25, 0.35]
TAIL_BONES = ["body", "tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail6"]


def region_of(bone):
    if bone in ("head", "jaw"):
        return "head"
    if bone.startswith("neck"):
        return "neck"
    if bone.startswith(("arm", "forearm", "hand")):
        return "front"
    if bone.startswith(("thigh", "shin", "ankle", "foot")):
        return "hind"
    if bone.startswith("tail"):
        return "tail"
    return "torso"


class Quadruped:
    def __init__(self, params):
        self.P = dict(DEFAULTS)
        self.P.update(params)
        self.ground = 0.0

    # ------------------------------------------------------------------ warps
    def W(self, p, bone):
        P = self.P
        p = np.array(p, dtype=float)
        reg = region_of(bone) if bone else "torso"
        tor = np.array(P["torso"], dtype=float)

        Rp = rot_x(P["pitch"])

        def T_t(q):
            return HIP + Rp @ ((q - HIP) * tor)
        if reg == "torso":
            return T_t(p)
        if reg in ("front", "hind"):
            sx = np.sign(p[0]) if abs(p[0]) > 1e-6 else 1.0
            J = np.array([sx * 12.5, 34.0, -26.0]) if reg == "front" else np.array([sx * 12.5, 36.0, 12.0])
            L = P["front_len"] if reg == "front" else P["hind_len"]
            out = T_t(J) + (p - J) * np.array([1.0, L, L ** 0.5])
            out[0] = p[0] * P["leg_x"] * tor[0] ** 0.6
            return out
        if reg == "tail":
            Tb = np.array(TAIL_PTS[0], dtype=float)
            d = (p - Tb) * np.array([P["tail_thick"], 1.0, P["tail_len"]])
            u = max(0.0, (p[2] - Tb[2]) / 70.0)
            d[1] += P["tail_lift"] * 25 * u * u
            return T_t(Tb) + d
        R = rot_x(P["neck_raise"])
        ax = normalize(HEAD_PIV - NECK_BASE)

        def neck_xf(q):
            d = q - NECK_BASE
            par = (d @ ax) * ax
            return T_t(NECK_BASE) + R @ (par * P["neck_len"] + (d - par) * P["neck_thick"])
        if reg == "neck":
            return neck_xf(p)
        return neck_xf(HEAD_PIV) + R @ ((p - HEAD_PIV) * P["head_scale"])

    def radius_scale(self, bone):
        P = self.P
        reg = region_of(bone)
        return {"head": P["head_scale"], "torso": float(np.mean(P["torso"][:2])), "neck": P["neck_thick"],
                "front": P["front_thick"], "hind": P["hind_thick"], "tail": P["tail_thick"]}[reg]

    def finalize(self, sc):
        for p in sc.prims:
            b = max(p.bones, key=lambda bw: bw[1])[0]
            rs = self.radius_scale(b)
            reg = region_of(b)
            g = p.geo
            if p.kind == "ellipsoid":
                g["c"] = self.W(g["c"], b)
                g["r"] = g["r"] * (np.array(self.P["torso"]) if reg == "torso" else rs)
            elif p.kind == "roundcone":
                g["a"], g["b"] = self.W(g["a"], b), self.W(g["b"], b)
                g["ra"], g["rb"] = g["ra"] * rs, g["rb"] * rs
                ax = normalize(g["b"] - g["a"])
                g["F"] = frame_from(g["b"] - g["a"], (0, 1, 0) if abs(ax[1]) < 0.9 else (0, 0, -1))
                g["L"] = float(np.linalg.norm(g["b"] - g["a"]))
            if reg == "head":
                p.k *= rs
            p.bbox = p._bbox()
        for name, (par, piv) in list(sc.bones.items()):
            sc.bones[name] = (par, self.W(piv, name))
        lows = [p.bbox[0][1] for p in sc.prims if region_of(max(p.bones, key=lambda bw: bw[1])[0]) in ("front", "hind")]
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

    # ------------------------------------------------------------------ skeleton
    def skeleton(self, sc):
        sc.bone("root", None, (0, 0, 0))
        sc.bone("body", "root", tuple(HIP))
        sc.bone("torso", "body", (0, 35, -6))
        sc.bone("neck1", "torso", tuple(NECK_BASE))
        sc.bone("neck2", "neck1", (0, 37, -41))
        sc.bone("head", "neck2", tuple(HEAD_PIV))
        sc.bone("jaw", "head", (0, 33.5, -55))
        for s in S:
            n = side_name(s)
            sc.bone("arm_" + n, "torso", (s * 12.5, 34, -26))
            sc.bone("forearm_" + n, "arm_" + n, (s * 13.6, 20.5, -24))
            sc.bone("hand_" + n, "forearm_" + n, (s * 13.4, 5.5, -27))
            sc.bone("thigh_" + n, "body", (s * 12.5, 36, 12))
            sc.bone("shin_" + n, "thigh_" + n, (s * 13.4, 20.5, 8.5))
            sc.bone("ankle_" + n, "shin_" + n, (s * 13.4, 8.5, 14.5))
            sc.bone("foot_" + n, "ankle_" + n, (s * 13.4, 2.8, 11.5))
        prev = "body"
        for i, c in enumerate(TAIL_PTS[:6]):
            sc.bone("tail%d" % (i + 1), prev, c)
            prev = "tail%d" % (i + 1)

    def build(self):
        sc = Sculpt()
        self.skeleton(sc)
        self.body(sc)
        {"trike": self.skull_trike, "pachyrhino": self.skull_trike, "stego": self.skull_small, "anky": self.skull_anky,
         "moschops": self.skull_moschops, "hadro": self.skull_hadro, "iguanodon": self.skull_iguanodon}[self.P["skull"]](sc)
        self.legs(sc)
        self.tail(sc)
        self.armor(sc)
        self.finalize(sc)
        return sc

    def body(self, sc):
        sc.ell((0, 39, 14), (14, 13, 14), bone="body", k=4.0)
        sc.ell((0, 34.5, 0), (17, 15.5, 14), bone=[("body", 0.5), ("torso", 0.5)], k=5.0)
        sc.ell((0, 33.5, -17), (16, 15, 19), bone="torso", k=5.0)
        sc.cone((0, 45.5, -28), (0, 48.5, 16), 6.0, 7.2, bone=[("torso", 0.6), ("body", 0.4)], k=6.5, sq=(1.1, 1.0))
        for s in S:
            sc.ell((s * 12.0, 33.5, -27), (5.0, 8.0, 6.5), bone=[("torso", 0.7), ("arm_" + side_name(s), 0.3)], k=3.0)
            sc.ell((s * 13.5, 34.5, -5), (3.5, 11, 16), bone="torso", k=5.0)
        sc.spline_tube([(0, 35.5, -31), (0, 36.6, -40), (0, 37.8, -47.5)], [11.5, 10.0, 9.2], ["torso", "neck1", "head"],
                       k=3.5, sq=(0.92, 1.0), sub=4)
        sc.ell((0, 30.0, -39), (6.0, 5.5, 8.0), bone=[("neck1", 0.6), ("torso", 0.4)], k=3.0)

    def legs(self, sc):
        P = self.P
        for s in S:
            n = side_name(s)
            a, fa, h = "arm_" + n, "forearm_" + n, "hand_" + n
            th, sh, an, ft = "thigh_" + n, "shin_" + n, "ankle_" + n, "foot_" + n
            fw = (0, 0, -1)
            # front leg: slightly sprawled, elbow out
            sc.cone((s * 12.5, 34, -26), (s * 13.6, 20.5, -24), 5.8, 4.3, bone=a, k=2.5, sq=(0.85, 1.0), up=fw)
            sc.ell((s * 13.0, 28.5, -27.5), (4.4, 6.0, 4.0), bone=a, k=2.2)
            sc.ell((s * 13.6, 20.6, -24.2), (3.9, 3.8, 4.1), bone=[(a, 0.5), (fa, 0.5)], k=1.5)
            sc.cone((s * 13.6, 20.5, -24), (s * 13.4, 5.5, -27), 4.1, 3.0, bone=fa, k=2.0, sq=(0.85, 1.0), up=fw)
            sc.ell((s * 13.5, 15.5, -24.5), (3.2, 4.5, 3.2), bone=fa, k=1.6)
            sc.cone((s * 13.4, 5.5, -27), (s * 13.4, 2.6, -28.2), 3.1, 3.3, bone=h, k=1.2)
            sc.ell((s * 13.4, 2.3, -28.6), (4.3, 2.3, 4.2), bone=h, k=1.5)
            if P["thumb_spike"]:
                sc.cone((s * 11.2, 5.2, -29.6), (s * 9.8, 8.6, -32.5), 1.3, 0.35, bone=h, k=0.5, mat="horn")
            # hind leg: column-like, big thigh
            sc.cone((s * 12.5, 36, 12), (s * 13.4, 20.5, 8.5), 8.6, 5.8, bone=th, k=3.0, sq=(0.8, 1.0), up=fw)
            sc.ell((s * 12.9, 29.5, 9.5), (6.2, 8.0, 7.0), bone=th, k=2.8)
            sc.ell((s * 13.4, 20.8, 8.6), (4.8, 4.6, 5.0), bone=[(th, 0.5), (sh, 0.5)], k=1.8)
            sc.cone((s * 13.4, 20.5, 8.5), (s * 13.4, 8.5, 14.5), 5.0, 3.6, bone=sh, k=2.2, sq=(0.86, 1.0), up=fw)
            sc.ell((s * 13.4, 16.0, 12.2), (3.6, 5.2, 3.8), bone=sh, k=1.8, rot=(0.4, 0, 0))
            sc.ell((s * 13.4, 8.6, 14.5), (3.2, 3.1, 3.4), bone=[(sh, 0.5), (an, 0.5)], k=1.3)
            sc.cone((s * 13.4, 8.5, 14.5), (s * 13.4, 2.8, 11.5), 3.4, 3.2, bone=an, k=1.4, sq=(0.86, 1.0), up=fw)
            sc.ell((s * 13.4, 2.2, 10.0), (4.6, 2.3, 5.2), bone=ft, k=1.6)

    def tail(self, sc):
        sc.spline_tube(TAIL_PTS, TAIL_R, TAIL_BONES, k=3.0, sq=(0.85, 1.0), sub=4)

    # ------------------------------------------------------------------ skulls
    def skull_trike(self, sc):
        H, J = "head", "jaw"
        pachy = self.P["skull"] == "pachyrhino"
        sc.ell((0, 40.5, -55), (8.6, 8.2, 9.0), bone=H, k=2.4)
        sc.cone((0, 38.5, -60), (0, 33.5, -77), 6.6, 3.0, bone=H, k=2.2, sq=(0.7, 1.0))
        sc.cone((0, 32.6, -76.0), (0, 28.8, -81.0), 3.0, 0.7, bone=H, k=1.2, mat="beak", sq=(0.75, 1.0))
        for s in S:
            sc.ell((s * 8.2, 35.5, -58.5), (2.6, 3.2, 4.2), bone=H, k=1.4, mat="horn_skin")
            sc.ell((s * 7.4, 44.0, -60.5), (1.9, 1.8, 2.2), bone=H, op="sub", k=0.6)
            sc.ell((s * 6.9, 45.4, -60.6), (1.1, 0.8, 2.3), bone=H, k=0.7, mat="lid")
            sc.ell((s * 2.4, 37.0, -73.5), (0.8, 1.0, 1.6), bone=H, op="sub", k=0.5)
        hl = self.P["horn_len"]
        if not pachy:
            sc.cone((0, 41.0, -70.5), (0, 46.0 + 1.5 * hl, -74.0), 2.0, 0.4, bone=H, k=0.8, mat="horn")
            for s in S:      # two long brow horns sweeping forward
                sc.cone((s * 4.2, 46.5, -58.5), (s * 5.4, 53.0, -66.0 - 6.0 * hl), 2.6, 1.6, bone=H, k=1.0, mat="horn")
                sc.cone((s * 5.4, 53.0, -66.0 - 6.0 * hl), (s * 5.6, 54.0, -75.0 - 9.0 * hl), 1.6, 0.25, bone=H, k=0.6, mat="horn")
        else:
            sc.ell((0, 42.8, -68.0), (4.2, 2.6, 6.0), bone=H, k=1.0, mat="horn_skin")        # nasal boss
            sc.ell((0, 47.6, -60.5), (3.0, 1.9, 3.8), bone=H, k=1.0, mat="horn_skin")        # brow boss
        # frill: thick shield tilted back, with a raised rim
        fk = self.P["frill"]
        fr = (19.0 * fk, 17.0 * fk, 1.8) if not pachy else (15.0 * fk, 12.5 * fk, 1.8)
        sc.ell((0, 52.5, -44.5), fr, bone=H, k=1.4, mat="frill", rot=(-0.95, 0, 0))
        sc.ell((0, 47.0, -50.0), (11.0, 9.0, 4.5), bone=H, k=2.5, rot=(-0.95, 0, 0))
        J_ = "jaw"
        sc.cone((0, 33.0, -56), (0, 29.8, -77.5), 4.4, 1.6, bone=J_, group="jaw", k=1.6, sq=(0.85, 0.8))
        sc.cone((0, 29.6, -77.0), (0, 28.6, -81.0), 1.7, 0.5, bone=J_, group="jaw", k=0.8, mat="beak")
        sc.ell((0, 32.6, -66.0), (2.2, 0.9, 7.0), bone=J_, group="tongue", k=1.0, mat="tongue")

    def skull_small(self, sc):
        """stegosaur: long, low, narrow head, small beak"""
        H, J = "head", "jaw"
        sc.ell((0, 39.5, -54), (5.0, 4.8, 6.2), bone=H, k=2.0)
        sc.cone((0, 38.5, -57), (0, 35.5, -69), 4.2, 2.0, bone=H, k=1.8, sq=(0.75, 1.0))
        sc.cone((0, 35.0, -68.5), (0, 33.6, -72), 1.9, 0.6, bone=H, k=0.9, mat="beak")
        for s in S:
            sc.ell((s * 4.2, 41.2, -57.5), (1.4, 1.3, 1.6), bone=H, op="sub", k=0.5)
            sc.ell((s * 3.9, 42.2, -57.6), (0.8, 0.6, 1.7), bone=H, k=0.5, mat="lid")
        sc.cone((0, 36.0, -54.5), (0, 34.2, -70), 3.2, 1.3, bone=J, group="jaw", k=1.4, sq=(0.8, 0.75))
        sc.ell((0, 35.6, -62.0), (1.4, 0.6, 4.5), bone=J, group="tongue", k=0.8, mat="tongue")

    def skull_anky(self, sc):
        """ankylosaur: broad, flat, armoured head with backward horns"""
        H, J = "head", "jaw"
        sc.ell((0, 38.5, -55), (9.5, 6.4, 8.0), bone=H, k=2.2)
        sc.cone((0, 37.5, -58), (0, 35.0, -70), 7.4, 4.2, bone=H, k=2.0, sq=(1.0, 0.7))
        sc.cone((0, 34.4, -69.5), (0, 33.2, -73.0), 3.6, 1.8, bone=H, k=1.2, mat="beak", sq=(1.0, 0.6))
        for s in S:
            sc.cone((s * 8.5, 41.0, -50.0), (s * 12.5, 42.0, -45.0), 2.4, 0.4, bone=H, k=0.8, mat="horn")
            sc.cone((s * 8.6, 35.5, -51.5), (s * 12.0, 33.5, -47.5), 2.2, 0.4, bone=H, k=0.8, mat="horn")
            sc.ell((s * 6.6, 40.6, -59.5), (1.5, 1.4, 1.7), bone=H, op="sub", k=0.5)
            sc.ell((s * 6.2, 41.7, -59.5), (0.9, 0.7, 1.8), bone=H, k=0.5, mat="lid")
            for i in range(3):
                sc.ell((s * (2.5 + i * 2.2), 44.2 - i * 0.6, -57 - i * 0.5), (1.6, 1.0, 1.8), bone=H, k=0.5, mat="plate")
        sc.cone((0, 34.6, -55), (0, 33.4, -71), 5.4, 2.6, bone=J, group="jaw", k=1.4, sq=(1.0, 0.6))
        sc.ell((0, 34.8, -63.0), (2.6, 0.8, 5.0), bone=J, group="tongue", k=0.8, mat="tongue")

    def skull_moschops(self, sc):
        """dinocephalian: massive, thick-boned head, short snout, chisel teeth"""
        H, J = "head", "jaw"
        sc.ell((0, 41.0, -54), (8.4, 8.6, 8.0), bone=H, k=2.6)
        sc.ell((0, 46.5, -55), (7.0, 4.5, 7.0), bone=H, k=2.0, mat="horn_skin")
        sc.cone((0, 39.5, -59), (0, 35.5, -68), 6.2, 4.0, bone=H, k=2.2, sq=(0.85, 1.0))
        for s in S:
            sc.ell((s * 6.8, 42.8, -59.5), (1.7, 1.6, 2.0), bone=H, op="sub", k=0.6)
            sc.ell((s * 6.4, 44.1, -59.6), (1.0, 0.7, 2.1), bone=H, k=0.6, mat="lid")
            sc.ell((s * 7.8, 37.0, -55.5), (2.8, 3.4, 4.5), bone=H, k=1.6)
            sc.ell((s * 2.0, 37.6, -67.6), (0.7, 0.8, 1.2), bone=H, op="sub", k=0.4)
        sc.cone((0, 33.8, -55), (0, 33.6, -67), 5.4, 3.2, bone=J, group="jaw", k=1.6, sq=(0.9, 0.75))
        sc.ell((0, 35.0, -61.0), (2.6, 0.9, 4.5), bone=J, group="tongue", k=0.8, mat="tongue")

    def skull_hadro(self, sc):
        """parasaurolophus: duck bill and long backward tube crest"""
        H, J = "head", "jaw"
        sc.ell((0, 40.0, -54), (5.6, 5.8, 6.4), bone=H, k=2.0)
        sc.cone((0, 39.0, -57), (0, 35.5, -70), 4.6, 3.2, bone=H, k=1.8, sq=(0.78, 1.0))
        sc.ell((0, 34.4, -71.0), (4.4, 2.0, 3.6), bone=H, k=1.2, mat="beak")                    # duck bill
        sc.cone((0, 44.0, -58.0), (0, 50.5, -40.0), 1.9, 1.6, bone=H, k=1.2, mat="frill")       # tube crest
        sc.ell((0, 50.6, -39.6), (1.7, 1.7, 1.7), bone=H, k=0.8, mat="frill")
        for s in S:
            sc.ell((s * 4.6, 41.6, -58.0), (1.5, 1.4, 1.7), bone=H, op="sub", k=0.5)
            sc.ell((s * 4.2, 42.7, -58.1), (0.9, 0.7, 1.8), bone=H, k=0.5, mat="lid")
            sc.ell((s * 1.7, 37.0, -68.0), (0.6, 0.6, 1.2), bone=H, op="sub", k=0.4)
        sc.cone((0, 35.6, -55), (0, 33.6, -70), 3.6, 2.2, bone=J, group="jaw", k=1.4, sq=(0.85, 0.75))
        sc.ell((0, 33.2, -70.5), (3.8, 1.2, 3.0), bone=J, group="jaw", k=0.9, mat="beak")
        sc.ell((0, 35.4, -62.0), (1.8, 0.7, 5.0), bone=J, group="tongue", k=0.8, mat="tongue")

    def skull_iguanodon(self, sc):
        """iguanodon: long, horse-like head with a toothless beak"""
        H, J = "head", "jaw"
        sc.ell((0, 40.0, -54), (5.8, 6.2, 6.6), bone=H, k=2.0)
        sc.cone((0, 39.2, -57), (0, 35.0, -73), 5.0, 2.8, bone=H, k=1.8, sq=(0.72, 1.0))
        sc.cone((0, 34.2, -72.5), (0, 32.6, -76.0), 2.6, 1.0, bone=H, k=1.0, mat="beak", sq=(0.8, 0.8))
        for s in S:
            sc.ell((s * 4.8, 42.0, -58.0), (1.5, 1.4, 1.7), bone=H, op="sub", k=0.5)
            sc.ell((s * 4.4, 43.1, -58.1), (0.9, 0.7, 1.8), bone=H, k=0.5, mat="lid")
            sc.ell((s * 1.8, 37.4, -71.6), (0.6, 0.7, 1.3), bone=H, op="sub", k=0.4)
        sc.cone((0, 35.4, -55), (0, 33.0, -73), 4.0, 1.6, bone=J, group="jaw", k=1.4, sq=(0.82, 0.75))
        sc.ell((0, 35.4, -63.0), (1.8, 0.7, 5.5), bone=J, group="tongue", k=0.8, mat="tongue")

    # ------------------------------------------------------------------ body armour (SDF parts)
    def armor(self, sc):
        f = self.P["features"]
        if "osteoderms" in f:
            for row, x in enumerate((-8.5, -3.0, 3.0, 8.5)):
                for i in range(7):
                    z = -28 + i * 7.5
                    y = 49.0 - abs(x) * 0.35 - (0 if -10 < z < 14 else 1.5)
                    sc.ell((x, y, z), (2.2, 1.5, 2.6), bone="torso" if z < -4 else "body", k=0.9, mat="plate")
        if "tail_club" in f:
            sc.ell((0, 27.0, 96.0), (5.6, 3.4, 4.6), bone="tail6", k=1.0, mat="plate")
            for s in S:
                sc.ell((s * 3.6, 27.0, 95.0), (3.0, 2.8, 3.6), bone="tail6", k=0.8, mat="plate")

    # ------------------------------------------------------------------ explicit details
    def eyes(self):
        P = self.P
        c, fwd, r = {"trike": ((6.9, 44.0, -60.5), (0.9, 0.05, -0.4), 1.45), "pachyrhino": ((6.9, 44.0, -60.5), (0.9, 0.05, -0.4), 1.45),
                     "stego": ((3.9, 41.2, -57.5), (0.95, 0.05, -0.3), 1.0), "anky": ((6.1, 40.6, -59.5), (0.95, 0.1, -0.3), 1.05),
                     "moschops": ((6.3, 42.8, -59.5), (0.9, 0.05, -0.4), 1.25), "hadro": ((4.2, 41.6, -58.0), (0.95, 0.05, -0.3), 1.15),
                     "iguanodon": ((4.4, 42.0, -58.0), (0.95, 0.05, -0.3), 1.15)}[P["skull"]]
        out = [(tuple(self.F((s * c[0], c[1], c[2]), "head")), normalize((s * fwd[0], fwd[1], fwd[2]))) for s in S]
        return out, r * P["head_scale"] * P["eye_size"]

    def teeth_specs(self):
        return []

    def claw_specs(self):
        """hoof-like blunt claws on every toe"""
        out = []
        for s in S:
            n = side_name(s)
            for i, dx in enumerate((-2.6, -0.9, 0.9, 2.6)):
                base = np.array([s * (13.4 + dx), 1.6, -31.0])
                pts = [base + np.array([0, -0.6 * k / 3.0, -1.6 * k / 3.0]) for k in range(4)]
                out.append(("hand_" + n, pts, [1.05, 0.95, 0.7, 0.2]))
            for i, dx in enumerate((-2.4, 0.0, 2.4)):
                base = np.array([s * (13.4 + dx), 1.6, 5.2])
                pts = [base + np.array([dx * 0.1, -0.6 * k / 3.0, -2.0 * k / 3.0]) for k in range(4)]
                out.append(("foot_" + n, pts, [1.35, 1.2, 0.9, 0.25]))
        return [(b, [self.F(p, b) for p in pts], [x * self.radius_scale(b) for x in r]) for b, pts, r in out]
