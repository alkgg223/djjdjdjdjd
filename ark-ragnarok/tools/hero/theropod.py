"""Parametric bipedal dinosaur sculpt.  The defaults ARE the hero Tyrannosaurus; every other biped is the same
anatomy re-proportioned region by region (torso, neck, head, arms, legs, tail) with its own skull and features.

Space: GeckoLib model pixels (+y up, ground y = 0, faces -z).  Sizes are relative; the in-game size comes from
the species' real length (1 m = 1 block).
"""
import math
import numpy as np
from sdfkit import Sculpt, normalize, frame_from

S = (-1, 1)


def side_name(s):
    return "l" if s > 0 else "r"


def _sm(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


DEFAULTS = dict(
    skull="rex", skull_k=(1.0, 1.0, 1.0), head_scale=1.2, lift=6.5, eye_size=1.0, tooth_size=1.0, teeth=True,
    torso=(1.0, 1.0, 1.0), belly=1.0, neck_len=1.0, neck_thick=1.0, neck_raise=0.0, head_rel=1.0,
    arms="tiny", arm_scale=1.0, arm_thick=1.0, fingers=2, hand_claw=1.0,
    leg_len=1.0, leg_thick=1.0, leg_x=1.0, toes="rex", toe_thick=1.0, toe_len=1.0,
    tail_len=1.0, tail_thick=1.0, tail_drop=0.0, tail_tip=0.3,
    features=(), feathers=None,
)

LX0 = 12.4
HEAD_PIV = np.array([0.0, 63.5, -51.0])
TAIL_PTS = [(0, 52.5, 19), (0, 53.4, 31), (0, 52.6, 43), (0, 50.9, 54.5), (0, 48.8, 65.5), (0, 46.6, 76), (0, 44.6, 85.5),
            (0, 43.2, 93.5), (0, 42.4, 100.5), (0, 42.0, 106.5)]
TAIL_R = [12.2, 10.5, 8.7, 7.0, 5.45, 4.05, 2.9, 1.9, 1.05, 0.3]
TAIL_BONES = ["body", "tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7", "tail8", "tail8"]
TOE_LEN = {2: 9.0, 3: 11.2, 4: 9.6}


def region_of(bone):
    if bone in ("head", "jaw"):
        return "head"
    if bone.startswith("neck"):
        return "neck"
    if bone.startswith(("arm", "forearm", "hand")):
        return "arm"
    if bone.startswith(("thigh", "shin", "ankle", "foot", "toe")):
        return "leg"
    if bone.startswith("tail"):
        return "tail"
    return "torso"


class Theropod:
    def __init__(self, params):
        self.P = dict(DEFAULTS)
        self.P.update(params)
        P = self.P
        self.LX = LX0 * P["leg_x"]
        self.hs = P["head_scale"]
        self.lift = P["lift"]
        self.sk = np.array(P["skull_k"], dtype=float)

    # ------------------------------------------------------------------ the warp chain
    def X(self, p, bone=None):
        """rex-space point -> final model space"""
        P = self.P
        p = np.array(p, dtype=float)
        reg = region_of(bone) if bone else "torso"
        if reg == "head":
            p = HEAD_PIV + (p - HEAD_PIV) * self.sk               # skull shape
            p = HEAD_PIV + (p - HEAD_PIV) * self.hs               # skull size
        p[1] = p[1] + self.lift * _sm(8.0, 40.0, p[1])            # leg length of the rex template
        return self.W(p, reg)

    def anchor(self, rex_point, bone):
        q = np.array(rex_point, dtype=float)
        q[1] = q[1] + self.lift * _sm(8.0, 40.0, q[1])
        return q

    def W(self, p, reg):
        P = self.P
        hip = self.anchor((0, 51, 10), "body")
        tor = np.array(P["torso"], dtype=float)

        def T_t(q):
            return hip + (q - hip) * tor
        if reg == "torso":
            return T_t(p)
        if reg == "leg":
            J = self.anchor((0, 50.5, 10), "body")
            out = T_t(J) + (p - J) * np.array([1.0, P["leg_len"], P["leg_len"] ** 0.5])
            out[0] = p[0] * self.P["leg_x"] * tor[0] ** 0.3
            return out
        if reg == "tail":
            Tb = self.anchor(TAIL_PTS[0], "tail1")
            d = (p - Tb) * np.array([P["tail_thick"], 1.0, P["tail_len"]])
            u = max(0.0, (p[2] - Tb[2]) / 90.0)
            d[1] -= P["tail_drop"] * 30 * u * u
            return T_t(Tb) + d
        if reg == "arm":
            Sa = self.anchor((np.sign(p[0]) * 9.8, 45, -29.5), "torso")
            return T_t(Sa) + (p - Sa) * P["arm_scale"]
        Nb = self.anchor((0, 56, -30), "neck1")
        Hp = self.anchor((0, 63.5, -51), "head")
        R = rot_x(P["neck_raise"])
        ax = normalize(Hp - Nb)

        def neck_xf(q):
            d = q - Nb
            par = (d @ ax) * ax
            return T_t(Nb) + R @ (par * P["neck_len"] + (d - par) * P["neck_thick"])
        if reg == "neck":
            return neck_xf(p)
        return neck_xf(Hp) + R @ ((p - Hp) * P["head_rel"])

    def radius_scale(self, bone):
        P = self.P
        reg = region_of(bone)
        if reg == "head":
            return self.hs * P["head_rel"]
        return {"torso": float(np.mean(P["torso"][:2])), "neck": P["neck_thick"], "arm": P["arm_scale"] * P["arm_thick"],
                "leg": P["leg_thick"], "tail": P["tail_thick"]}[reg]

    def finalize(self, sc):
        for p in sc.prims:
            b = max(p.bones, key=lambda bw: bw[1])[0]
            rs = self.radius_scale(b)
            g = p.geo
            reg = region_of(b)
            if p.kind == "ellipsoid":
                g["c"] = self.X(g["c"], b)
                if reg == "head":
                    g["r"] = g["r"] * rs * (self.sk if p.group != "x" else 1.0)
                elif reg == "torso":
                    g["r"] = g["r"] * np.array(self.P["torso"]) * np.array([1, self.P["belly"] if g["c"][1] < 50 else 1, 1])
                else:
                    g["r"] = g["r"] * rs
            elif p.kind == "roundcone":
                g["a"], g["b"] = self.X(g["a"], b), self.X(g["b"], b)
                ks = float(np.mean(self.sk[:2])) if reg == "head" else 1.0
                g["ra"], g["rb"] = g["ra"] * rs * ks, g["rb"] * rs * ks
                ax = normalize(g["b"] - g["a"])
                g["F"] = frame_from(g["b"] - g["a"], (0, 1, 0) if abs(ax[1]) < 0.9 else (0, 0, -1))
                g["L"] = float(np.linalg.norm(g["b"] - g["a"]))
            p.k = p.k * (rs if reg in ("head", "arm") else 1.0)
            p.bbox = p._bbox()
        for name, (par, piv) in list(sc.bones.items()):
            sc.bones[name] = (par, self.X(piv, name))
        # put the feet back on the ground
        lows = [p.bbox[0][1] for p in sc.prims if region_of(max(p.bones, key=lambda bw: bw[1])[0]) == "leg"]
        self.ground = 0.35 - min(lows)
        self._shift(sc, np.array([0, self.ground, 0]))
        return sc

    def _shift(self, sc, d):
        for p in sc.prims:
            g = p.geo
            if p.kind in ("ellipsoid", "roundbox"):
                g["c"] = g["c"] + d
            elif p.kind == "roundcone":
                g["a"], g["b"] = g["a"] + d, g["b"] + d
            p.bbox = p._bbox()
        for name, (par, piv) in list(sc.bones.items()):
            sc.bones[name] = (par, piv + d)

    def F(self, p, bone):
        """final position of a rex-space detail point (teeth, claws, eyes, features)"""
        return self.X(p, bone) + np.array([0, getattr(self, "ground", 0.0), 0])

    # ------------------------------------------------------------------ skeleton
    def toe_base(self, s, t):
        off = {2: -1.7, 3: 0.0, 4: 1.7}[t]
        return (s * (LX0 + off), 2.1, 1.8)

    def toe_dir(self, s, t):
        yaw = {2: -0.40, 3: 0.03, 4: 0.37}[t] * s
        return np.array([math.sin(yaw), 0.0, -math.cos(yaw)])

    def skeleton(self, sc):
        sc.bone("root", None, (0, 0, 0))
        sc.bone("body", "root", (0, 51, 10))
        sc.bone("torso", "body", (0, 50, -4))
        sc.bone("neck1", "torso", (0, 56, -30))
        sc.bone("neck2", "neck1", (0, 61.5, -40))
        sc.bone("head", "neck2", (0, 63.5, -51))
        sc.bone("jaw", "head", (0, 60.6, -54.5))
        for s in S:
            n = side_name(s)
            sh, el, wr, hc, fd = self.arm_pts(s)
            sc.bone("arm_" + n, "torso", sh)
            sc.bone("forearm_" + n, "arm_" + n, el)
            sc.bone("hand_" + n, "forearm_" + n, wr)
            sc.bone("thigh_" + n, "body", (s * (LX0 - 1.6), 50.5, 10))
            sc.bone("shin_" + n, "thigh_" + n, (s * LX0, 31.6, 0.5))
            sc.bone("ankle_" + n, "shin_" + n, (s * LX0, 13.4, 9.0))
            sc.bone("foot_" + n, "ankle_" + n, (s * LX0, 3.4, 4.4))
            for t in (2, 3, 4):
                sc.bone("toe%d_%s" % (t, n), "foot_" + n, self.toe_base(s, t))
        prev = "body"
        for i, c in enumerate(TAIL_PTS[:8]):
            sc.bone("tail%d" % (i + 1), prev, c)
            prev = "tail%d" % (i + 1)

    # ------------------------------------------------------------------ build
    def build(self):
        P = self.P
        sc = Sculpt()
        self.skeleton(sc)
        self.torso(sc)
        self.neck(sc)
        {"rex": self.skull_rex, "croc": self.skull_croc, "beak": self.skull_beak, "dome": self.skull_dome}[P["skull"]](sc)
        self.arms(sc)
        self.legs(sc)
        self.tail(sc)
        self.finalize(sc)
        return sc

    def torso(self, sc):
        sc.ell((0, 54.2, 9), (11.8, 10.8, 14.5), bone="body", k=3.0)
        sc.ell((0, 50, -1), (13.2, 13.6, 11.2), bone=[("body", 0.5), ("torso", 0.5)], k=4.5)
        sc.ell((0, 45.2, -12.5), (14.6, 16.2, 20.0), bone="torso", k=4.5)
        sc.ell((0, 48.4, -26.0), (12.2, 13.4, 10.6), bone="torso", k=4.0)
        sc.ell((0, 38.0, 3.0), (3.8, 3.0, 7.5), bone="body", k=4.0)
        for s in S:
            sc.ell((s * 8.0, 49.5, -29), (5.2, 6.8, 6.4), bone="torso", k=2.8)
            sc.ell((s * 9.6, 45.6, -29.0), (2.5, 2.8, 2.6), bone=[("torso", 0.5), ("arm_" + side_name(s), 0.5)], k=1.6)
            sc.ell((s * 10.8, 46, -13.5), (3.4, 11.0, 13.5), bone="torso", k=4.5, rot=(0.1, 0, s * 0.08))
        sc.cone((0, 58.5, -25), (0, 61.6, 6), 6.0, 6.8, bone=[("torso", 0.6), ("body", 0.4)], k=5.5, sq=(1.15, 1.0))

    def neck(self, sc):
        r_end = 9.4
        if self.P["skull"] == "beak":
            # small-headed species: the neck tapers into the head instead of being thicker than it
            r_end = min(9.4, 5.7 * self.hs * 0.8 / self.P["neck_thick"])
        sc.spline_tube([(0, 55.5, -28), (0, 60.0, -37.5), (0, 63.0, -45.0), (0, 64.8, -51.5)], [12.6, 11.0, (10.0 + r_end) / 2, r_end],
                       ["torso", "neck1", "neck2", "head"], k=3.5, sq=(0.88, 1.0), sub=4)
        for s in S:
            sc.ell((s * 5.8, 59.0, -39), (4.0, 7.4, 8.6), bone=[("neck1", 0.55), ("neck2", 0.25), ("torso", 0.2)], k=2.8,
                   rot=(-0.45, 0, 0))
        sc.ell((0, 50.6, -40.0), (6.6, 6.2, 9.4), bone=[("neck1", 0.6), ("torso", 0.25), ("neck2", 0.15)], k=3.2, rot=(-0.3, 0, 0))

    # ------------------------------------------------------------------ skulls
    def skull_rex(self, sc):
        """tyrannosaurid skull: narrow roof, flat tall snout sides, broad cheeks and jaw joints at the bottom back
        (keyhole-shaped from above), small eyes deep under a brow ridge, rough nasal ridge"""
        H = "head"
        f = self.P["features"]
        sc.ell((0, 69.4, -56.0), (5.0, 4.2, 6.6), bone=H, k=2.0)                                  # skull roof (narrow)
        sc.ell((0, 64.6, -55.6), (6.2, 5.6, 5.8), bone=H, k=2.4)                                  # braincase / occiput
        for s in S:
            sc.ell((s * 6.9, 64.4, -55.4), (3.8, 5.6, 5.4), bone=H, k=1.8)                        # jaw muscles (wide, low)
            sc.ell((s * 7.7, 61.4, -60.2), (2.3, 2.5, 5.6), bone=H, k=1.4)                        # jugal cheek
            sc.ell((s * 8.3, 59.6, -54.8), (2.4, 2.6, 2.7), bone=H, k=1.2)                        # quadratojugal corner
        sc.cone((0, 66.2, -59.0), (0, 63.8, -78.4), 7.0, 4.5, bone=H, k=1.4, sq=(0.58, 1.0))        # snout, flat sides
        sc.cone((0, 72.4, -59.5), (0, 67.9, -78.0), 1.7, 1.25, bone=H, k=1.2)                      # nasal ridge
        sc.ell((0, 63.9, -79.3), (2.6, 4.1, 2.1), bone=H, k=1.2)                                   # snout tip
        for s in S:
            sc.cone((s * 3.9, 61.9, -59.5), (s * 2.55, 61.1, -78.0), 2.4, 1.55, bone=H, k=1.2)    # upper lip line
        for i in range(8):                                                                         # nasal rugosities
            z = -61.0 - i * 2.2
            t = (z + 59.5) / -18.5
            ytop = 72.4 + 1.7 - t * (72.4 + 1.7 - 67.9 - 1.25)
            sc.ell((0, ytop - 0.25, z), (1.3 - 0.05 * i, 0.7, 1.0), bone=H, k=0.6, mat="horn_skin")
        for s in S:
            sc.ell((s * 3.7, 72.1, -63.2), (1.25, 1.35, 2.0), bone=H, k=0.8, mat="horn_skin")       # lacrimal horn
            sc.ell((s * 5.5, 71.0, -58.9), (1.8, 1.05, 2.4), bone=H, k=0.9, mat="horn_skin")        # postorbital boss
            sc.ell((s * 6.0, 70.35, -61.9), (1.5, 0.95, 3.2), bone=H, k=0.9, rot=(0, -s * 0.25, 0)) # brow ridge (hood)
            sc.ell((s * 7.0, 68.3, -61.6), (1.45, 1.2, 1.65), bone=H, op="sub", k=0.45)             # eye socket
            sc.ell((s * 6.75, 69.25, -61.7), (1.25, 0.6, 1.9), bone=H, k=0.5, mat="lid")             # upper eyelid
            sc.ell((s * 6.7, 67.35, -61.7), (1.1, 0.5, 1.7), bone=H, k=0.45, mat="lid")              # lower eyelid
            sc.ell((s * 2.3, 66.3, -78.6), (0.75, 0.7, 1.5), bone=H, op="sub", k=0.45, rot=(0.25, s * 0.35, 0))  # nostril
            if "lacrimal_crests" in f:
                sc.ell((s * 3.4, 73.4, -66.0), (1.1, 1.9, 4.2), bone=H, k=0.8, mat="horn_skin")
            if "horns" in f:
                sc.cone((s * 5.0, 71.5, -60.0), (s * 9.5, 75.5, -61.0), 2.4, 0.6, bone=H, k=0.9, mat="horn")
        sc.cone((0, 59.4, -59.0), (0, 59.6, -77.0), 2.6, 1.6, bone=H, op="sub", k=0.8, sq=(1.0, 0.55))   # palate
        self.jaw_rex(sc)
        if "double_crest" in f:
            for s in S:
                sc.ell((s * 1.9, 75.2, -66.0), (0.6, 3.4, 9.5), bone=H, k=0.5, mat="frill", rot=(0.12, 0, s * 0.12))
        if "head_crest" in f:
            sc.ell((0, 76.0, -63.0), (1.1, 5.5, 6.0), bone=H, k=0.7, mat="frill", rot=(-0.3, 0, 0))

    def jaw_rex(self, sc):
        J = "jaw"
        sc.cone((0, 58.3, -53.8), (0, 59.0, -78.0), 5.7, 2.7, bone=J, group="jaw", k=1.5, sq=(0.82, 0.86))  # mandible
        sc.ell((0, 55.8, -56.0), (5.0, 4.6, 5.4), bone=J, group="jaw", k=2.2)                              # surangular
        sc.ell((0, 58.4, -77.2), (2.4, 2.4, 1.9), bone=J, group="jaw", k=1.1)                              # chin
        for s in S:
            sc.cone((s * 3.2, 61.2, -55.5), (s * 2.1, 60.8, -77.0), 1.55, 1.05, bone=J, group="jaw", k=1.0)  # dentary lips
        sc.cone((0, 62.0, -56.5), (0, 61.1, -76.0), 2.4, 1.3, bone=J, group="jaw", op="sub", k=0.8, sq=(1.0, 0.7))
        sc.ell((0, 60.1, -66.0), (2.3, 0.95, 9.0), bone=J, group="tongue", k=1.0, mat="tongue")

    def skull_croc(self, sc):
        """spinosaurid: long, low, narrow snout with a rosette of teeth at the tip and a small crest"""
        H = "head"
        sc.ell((0, 66.6, -56.0), (7.0, 6.8, 6.4), bone=H, k=2.2)
        for s in S:
            sc.ell((s * 5.6, 65.6, -55.6), (2.8, 4.2, 4.4), bone=H, k=1.8)
        sc.cone((0, 65.4, -59.5), (0, 62.4, -89.0), 5.4, 2.4, bone=H, k=1.8, sq=(0.78, 0.82))
        sc.ell((0, 62.0, -90.0), (3.0, 2.6, 3.2), bone=H, k=1.4)                            # rosette
        sc.ell((0, 69.6, -63.0), (1.1, 1.6, 4.6), bone=H, k=0.8, mat="horn_skin")             # nasal crest
        for s in S:
            sc.cone((s * 3.4, 61.4, -59.0), (s * 1.9, 60.4, -88.0), 2.0, 1.2, bone=H, k=1.5)
            sc.ell((s * 6.2, 68.6, -59.8), (1.8, 1.7, 2.1), bone=H, op="sub", k=0.6)
            sc.ell((s * 5.9, 69.9, -60.0), (1.0, 0.7, 2.2), bone=H, k=0.6, mat="lid")
            sc.ell((s * 1.5, 64.1, -80.5), (0.6, 0.6, 1.4), bone=H, op="sub", k=0.4)
        sc.cone((0, 59.6, -59.0), (0, 60.0, -86.0), 2.0, 1.1, bone=H, op="sub", k=0.8, sq=(1.0, 0.55))
        J = "jaw"
        sc.cone((0, 58.4, -53.6), (0, 59.0, -89.5), 4.4, 2.0, bone=J, group="jaw", k=1.6, sq=(0.85, 0.75))
        sc.ell((0, 56.8, -55.5), (4.6, 3.6, 4.4), bone=J, group="jaw", k=2.2)
        for s in S:
            sc.cone((s * 2.6, 61.0, -55.5), (s * 1.6, 60.6, -88.0), 1.4, 0.9, bone=J, group="jaw", k=1.1)
        sc.cone((0, 61.6, -57.0), (0, 61.0, -86.0), 2.0, 1.0, bone=J, group="jaw", op="sub", k=0.8, sq=(1.0, 0.7))
        sc.ell((0, 60.0, -70.0), (2.0, 0.9, 12.0), bone=J, group="tongue", k=1.0, mat="tongue")

    def skull_beak(self, sc):
        """beaked head (oviraptor, gallimimus, therizinosaurus): small low cranium, long flat-sided snout ending in a
        keratin beak with a sharp cutting edge, eyes under a brow ridge, no teeth"""
        H = "head"
        bl = self.P.get("beak_len", 1.0)
        zb = -60.0 - 11.5 * bl            # beak base
        zt = -60.0 - 18.5 * bl            # beak tip
        sc.ell((0, 66.8, -56.6), (5.7, 5.5, 6.4), bone=H, k=2.2)                                   # cranium
        sc.ell((0, 64.2, -56.4), (5.2, 4.4, 5.0), bone=H, k=2.0)                                   # jaw muscles
        sc.cone((0, 66.4, -60.0), (0, 64.6, zb), 4.6, 2.7, bone=H, k=1.8, sq=(0.7, 1.0))            # snout, flat sides
        sc.cone((0, 64.9, zb + 1.0), (0, 62.6, zt), 2.7, 0.55, bone=H, k=1.2, mat="beak", sq=(0.72, 0.9))
        for s in S:
            sc.ell((s * 5.1, 67.9, -60.4), (1.75, 1.6, 2.0), bone=H, op="sub", k=0.5)              # eye socket
            sc.ell((s * 4.75, 69.35, -60.5), (1.25, 0.75, 2.4), bone=H, k=0.6, mat="lid")            # upper lid / brow
            sc.ell((s * 4.95, 66.6, -60.5), (1.05, 0.5, 1.9), bone=H, k=0.45, mat="lid")             # lower lid
            sc.ell((s * 1.7, 65.4, zb + 0.6), (0.55, 0.55, 1.0), bone=H, op="sub", k=0.35)          # nostril
        if "head_crest" in self.P["features"]:
            # tall narrow casque rising from the snout (oviraptorid), not a rooster comb
            sc.ell((0, 72.6, -61.0), (1.1, 5.6, 4.6), bone=H, k=1.4, mat="frill", rot=(-0.35, 0, 0))
            sc.cone((0, 67.5, zb + 2.5), (0, 76.0, -62.5), 1.6, 0.9, bone=H, k=1.2, mat="frill")
        J = "jaw"
        sc.cone((0, 60.6, -55.5), (0, 61.7, zb + 1.5), 3.6, 1.7, bone=J, group="jaw", k=1.6, sq=(0.82, 0.75))
        sc.cone((0, 61.6, zb + 2.0), (0, 61.9, zt + 1.0), 1.7, 0.45, bone=J, group="jaw", k=1.0, mat="beak", sq=(0.8, 0.7))
        sc.ell((0, 61.6, -61.0), (1.4, 0.55, 3.6), bone=J, group="tongue", k=0.8, mat="tongue")

    def skull_dome(self, sc):
        """pachycephalosaurid: thick bony dome ringed with knobs, short snout, small leaf teeth"""
        H = "head"
        sc.ell((0, 67.0, -57.0), (7.6, 7.4, 7.0), bone=H, k=2.2)
        sc.ell((0, 72.6, -57.2), (7.2, 5.6, 7.6), bone=H, k=1.6, mat="horn_skin")            # dome
        for i in range(9):
            a = -math.pi * 0.9 + i * math.pi * 1.8 / 8
            sc.ell((math.sin(a) * 7.4, 69.6, -57.2 + math.cos(a) * 7.6), (1.15, 1.0, 1.15), bone=H, k=0.7, mat="horn_skin")
        for i in range(4):
            sc.ell((0.0, 66.4 + i * 0.5, -64.5 - i * 1.6), (0.95, 0.8, 0.95), bone=H, k=0.6, mat="horn_skin")
        for s in S:
            sc.ell((s * 5.4, 62.2, -60.2), (2.5, 2.7, 4.8), bone=H, k=1.6)                          # cheeks over the jaw joint
            sc.ell((s * 5.6, 70.0, -61.3), (1.5, 0.9, 2.6), bone=H, k=0.8, mat="horn_skin")          # brow ridge
        sc.cone((0, 65.4, -61.0), (0, 63.2, -72.0), 5.4, 2.8, bone=H, k=2.0, sq=(0.75, 1.0))
        for s in S:
            sc.ell((s * 6.3, 68.0, -61.0), (1.6, 1.5, 1.9), bone=H, op="sub", k=0.5)
            sc.ell((s * 5.9, 69.2, -61.1), (1.1, 0.75, 2.2), bone=H, k=0.6, mat="lid")
            sc.ell((s * 5.95, 66.9, -61.1), (1.0, 0.55, 1.9), bone=H, k=0.5, mat="lid")
            sc.ell((s * 1.7, 64.4, -71.6), (0.6, 0.6, 1.1), bone=H, op="sub", k=0.4)
        J = "jaw"
        sc.cone((0, 59.8, -56.0), (0, 61.0, -71.0), 4.2, 1.9, bone=J, group="jaw", k=1.6, sq=(0.85, 0.75))
        sc.ell((0, 61.2, -63.0), (2.0, 0.9, 5.5), bone=J, group="tongue", k=1.0, mat="tongue")

    # ------------------------------------------------------------------ limbs and tail
    def arms(self, sc):
        P = self.P
        nf = P["fingers"]
        for s in S:
            n = side_name(s)
            sh, el, wr, hc, fd = self.arm_pts(s)
            sc.cone(sh, el, 2.15, 1.55, bone="arm_" + n, k=1.2)
            sc.cone(el, wr, 1.55, 1.2, bone="forearm_" + n, k=1.0)
            sc.ell(el + np.array([s * 0.05, 0.1, 0.1]), (1.5, 1.5, 1.65), bone=[("arm_" + n, 0.5), ("forearm_" + n, 0.5)], k=0.8)
            sc.ell(hc, (1.1, 1.2, 1.4), bone="hand_" + n, k=0.8)
            for dx in self.finger_offsets():
                a = hc + np.array([s * dx, -0.4, -0.6])
                b = a + np.array([s * dx * 0.3, fd[1], fd[2]])
                sc.cone(a, b, 0.6, 0.44, bone="hand_" + n, k=0.5)

    def arm_pts(self, s):
        """shoulder, elbow, wrist, hand centre, finger direction (rex space).  "fold": the arms of raptors and other
        long-armed small theropods are held folded like a bird's wing (upper arm back, forearm forward, hands down),
        not dangling in front of the chest"""
        sh = np.array([s * 9.8, 45.0, -29.5])
        if self.P.get("arm_pose") == "fold":
            el = sh + normalize(np.array([s * 0.2, -0.7, 0.68])) * 5.7
            wr = el + normalize(np.array([s * 0.06, -0.3, -0.95])) * 5.0
            fd = np.array([0.0, -1.75, -1.0])
        else:
            el = np.array([s * 11.4, 39.8, -31.2])
            wr = np.array([s * 11.8, 36.6, -35.0])
            fd = np.array([0.0, -1.3, -1.7])
        hc = wr + normalize(wr - el) * 0.95
        return sh, el, wr, hc, fd

    def finger_offsets(self):
        return {2: (-0.45, 0.5), 3: (-0.7, 0.0, 0.7)}[self.P["fingers"]]

    def legs(self, sc):
        P = self.P
        sickle = P["toes"] == "raptor"
        LX = LX0
        for s in S:
            n = side_name(s)
            th, sh, an, ft = "thigh_" + n, "shin_" + n, "ankle_" + n, "foot_" + n
            fw = (0, 0, -1)
            x0 = s * (LX - 1.6)
            xl = s * LX
            sc.cone((x0, 50.0, 10.0), (xl - s * 0.2, 31.6, 0.5), 10.6, 5.2, bone=th, k=3.2, sq=(0.76, 1.0), up=fw)
            sc.ell((xl - s * 0.5, 42.0, 3.4), (6.4, 10.2, 6.4), bone=th, k=2.8, rot=(-0.5, 0, 0))
            sc.ell((xl - s * 1.1, 43.4, 14.8), (5.8, 9.2, 5.8), bone=th, k=2.8, rot=(0.3, 0, 0))
            sc.cone((s * (LX - 4.0), 44.8, 13.0), (s * 4.4, 49.6, 32.0), 7.0, 4.0,
                    bone=[(th, 0.45), ("body", 0.3), ("tail1", 0.25)], k=3.4)
            sc.ell((xl, 31.9, 0.4), (4.5, 4.4, 4.9), bone=[(th, 0.5), (sh, 0.5)], k=1.8)
            sc.cone((xl, 31.6, 0.6), (xl, 13.7, 8.8), 4.75, 2.6, bone=sh, k=2.0, sq=(0.84, 1.0), up=fw)
            sc.ell((xl, 25.4, 5.8), (3.7, 6.9, 3.9), bone=sh, k=2.0, rot=(0.42, 0, 0))
            sc.ell((xl + s * 0.2, 24.0, 1.6), (2.7, 6.2, 2.3), bone=sh, k=1.4, rot=(0.4, 0, 0))
            sc.ell((xl, 13.3, 9.2), (2.85, 2.8, 3.1), bone=[(sh, 0.5), (an, 0.5)], k=1.2)
            sc.cone((xl, 13.1, 9.0), (xl, 3.7, 4.3), 2.6, 2.45, bone=an, k=1.2, sq=(0.86, 1.0), up=fw)
            sc.ell((xl, 2.5, 3.1), (3.9, 2.45, 3.6), bone=ft, k=1.6)
            tt, tl = P["toe_thick"], P["toe_len"]
            for t in (2, 3, 4):
                b0 = np.array(self.toe_base(s, t))
                d = self.toe_dir(s, t)
                L = TOE_LEN[t] * tl
                tb = "toe%d_%s" % (t, n)
                if sickle and t == 2:
                    # raised second toe carrying the sickle claw
                    p1 = b0 + d * 2.5 + np.array([0, 2.2, 0])
                    p2 = p1 + d * 1.8 + np.array([0, 1.0, 0])
                    sc.spline_tube([b0, p1, p2], [1.9 * tt, 1.6 * tt, 1.3 * tt], [ft, tb, tb], k=0.9, sub=3)
                    continue
                p1 = b0 + d * (L * 0.36) + np.array([0, -0.55, 0])
                p2 = b0 + d * (L * 0.68) + np.array([0, -0.75, 0])
                p3 = b0 + d * L + np.array([0, -0.9, 0])
                sc.spline_tube([b0, p1, p2, p3], [1.95 * tt, 1.7 * tt, 1.45 * tt, 1.1 * tt], [ft, tb, tb, tb], k=0.9,
                               sq=(1.0, 0.82), sub=3)
                for kpt, kr in ((p1, 1.85 * tt), (p2, 1.58 * tt)):
                    sc.ell(kpt + np.array([0, 0.35, 0]), (kr, kr * 0.75, kr * 0.9), bone=tb, k=0.6)
            sc.cone((s * (LX - 0.8), 7.6, 7.6), (s * (LX - 1.8), 4.9, 9.9), 1.0 * tt, 0.65 * tt, bone=an, k=0.6)

    def tail(self, sc):
        r = list(TAIL_R)
        r[-1] = self.P["tail_tip"]
        sc.spline_tube(TAIL_PTS, r, TAIL_BONES, k=2.8, sq=(0.8, 1.0), sub=4)

    # ------------------------------------------------------------------ explicit details
    def teeth_specs(self):
        P = self.P
        if not P["teeth"]:
            return []
        out = []
        ts = P["tooth_size"]
        if P["skull"] == "rex":
            for s in S:
                for i in range(4):
                    z = -80.6 + i * 1.0
                    x = s * (1.1 + i * 0.62)
                    out.append(("head", curved_tooth(np.array([x, 59.7, z]), (1.15 + 0.12 * i) * ts, 0.34 * ts, back=0.18)))
                for i in range(11):
                    t = i / 10.0
                    z = -77.0 + i * 1.75
                    x = s * (3.55 + 1.5 * t)
                    y = 59.6 - 0.04 * i
                    size = max(0.9, 1.25 + 1.35 * math.sin(min(1.0, (t + 0.08) * 1.15) * math.pi) ** 0.8) * ts
                    out.append(("head", curved_tooth(np.array([x, y, z]), size, 0.42 + 0.12 * size, back=0.26)))
                for i in range(13):
                    t = i / 12.0
                    z = -77.4 + i * 1.6
                    x = s * (2.05 + 1.25 * t)
                    y = 61.3 - 0.02 * i
                    size = (1.0 + 1.0 * math.sin(min(1.0, (t + 0.12) * 1.1) * math.pi) ** 0.8) * ts
                    out.append(("jaw", curved_tooth(np.array([x, y, z]), size, 0.3 + 0.1 * size, back=0.22, up=True)))
        elif P["skull"] == "croc":
            for s in S:
                for i in range(16):
                    t = i / 15.0
                    z = -89.0 + i * 1.85
                    x = s * (1.9 + 1.5 * t)
                    size = (1.3 + 0.7 * math.sin(t * math.pi * 3) ** 2) * ts
                    out.append(("head", curved_tooth(np.array([x, 59.9, z]), size, 0.28 + 0.08 * size, back=0.05)))
                    out.append(("jaw", curved_tooth(np.array([x * 0.82, 61.4, z + 0.9]), size * 0.85, 0.25 + 0.07 * size, back=0.05,
                                                    up=True)))
        elif P["skull"] == "dome":
            for s in S:
                for i in range(7):
                    z = -70.5 + i * 1.3
                    out.append(("head", curved_tooth(np.array([s * (1.8 + 0.25 * i), 60.2, z]), 0.55 * ts, 0.25, back=0.0)))
        return [(b, ([self.F(p, b) for p in path], [r * self.hs * P["head_rel"] * float(np.mean(self.sk[:2])) for r in radii]))
                for b, (path, radii) in out]

    def claw_specs(self):
        P = self.P
        out = []
        cs = P["toe_thick"]
        for s in S:
            n = side_name(s)
            for t in (2, 3, 4):
                b0 = np.array(self.toe_base(s, t))
                d = self.toe_dir(s, t)
                if P["toes"] == "raptor" and t == 2:
                    base = b0 + d * 4.3 + np.array([0, 3.2, 0])
                    pts = []
                    for k in range(6):          # big curved sickle claw held off the ground
                        a = -0.3 + k * 0.55
                        pts.append(base + d * (math.cos(a) * 2.6 - 1.0) + np.array([0, -math.sin(a) * 2.8 + 0.6, 0]) * 1.0)
                    out.append(("toe2_" + n, pts, [1.0 * cs, 0.85 * cs, 0.65 * cs, 0.45 * cs, 0.25 * cs, 0.04]))
                    continue
                L = TOE_LEN[t] * P["toe_len"]
                tip = b0 + d * L + np.array([0, -0.9, 0])
                length = {2: 2.7, 3: 3.2, 4: 2.8}[t] * cs
                pts, radii = [], []
                for k in range(5):
                    u = k / 4.0
                    pts.append(tip + d * (length * u - 0.35) + np.array([0, 0.25 - (0.95 + 0.25) * u * u, 0]))
                    radii.append((1.08 * (1 - u) ** 0.85 + 0.03) * cs)
                out.append(("toe%d_%s" % (t, n), pts, radii))
            a = np.array([s * (LX0 - 1.8), 4.9, 9.9])
            d = normalize(np.array([-s * 0.3, -0.6, 0.75]))
            out.append(("ankle_" + n, [a + d * (1.6 * k / 3.0) + np.array([0, -0.15 * (k / 3.0) ** 2, 0]) for k in range(4)],
                        [0.62 * cs, 0.48 * cs, 0.3 * cs, 0.05]))
            hc = P["hand_claw"]
            _, _, _, hcen, fd = self.arm_pts(s)
            for dx in self.finger_offsets():
                a = hcen + np.array([s * dx, -0.4, -0.6]) + np.array([s * dx * 0.3, fd[1], fd[2]])
                d = normalize(np.array([0, fd[1] * 0.58, fd[2] * 0.53]))
                L = 1.5 * hc
                pts = [a + d * (L * k / 4.0) + np.array([0, -0.35 * hc * (k / 4.0) ** 2, 0.25 * hc * (k / 4.0) ** 2]) for k in range(5)]
                out.append(("hand_" + n, pts, [0.42 * min(hc, 1.8), 0.34 * min(hc, 1.8), 0.24 * min(hc, 1.8), 0.13, 0.03]))
        res = []
        for b, pts, r in out:
            rs = self.radius_scale(b)
            res.append((b, [self.F(p, b) for p in pts], [x * rs for x in r]))
        return res

    def eyes(self):
        if self.P["skull"] == "rex":
            c, fwd = (6.55, 68.3, -61.65), (0.78, 0.05, -0.62)
            r = 1.02
        elif self.P["skull"] == "croc":
            c, fwd, r = (5.65, 68.6, -59.9), (0.9, 0.1, -0.4), 1.25
        elif self.P["skull"] == "beak":
            c, fwd, r = (4.95, 67.95, -60.5), (0.93, 0.05, -0.36), 1.42
        else:
            c, fwd, r = (5.85, 68.0, -61.1), (0.9, 0.05, -0.45), 1.3
        out = []
        for s in S:
            p = self.F((s * c[0], c[1], c[2]), "head")
            out.append((tuple(p), normalize((s * fwd[0], fwd[1], fwd[2]))))
        return out, 0.84 * r * self.hs * self.P["head_rel"] * float(np.mean(self.sk[:2])) * self.P["eye_size"]


def curved_tooth(base, h, r0, back=0.25, up=False):
    sgn = 1.0 if up else -1.0
    pts, radii = [], []
    for k in range(4):
        t = k / 3.0
        pts.append(base + np.array([0, sgn * h * t, back * h * t * t]))
        radii.append(r0 * (1 - t) ** 0.9 + 0.03)
    return pts, radii
