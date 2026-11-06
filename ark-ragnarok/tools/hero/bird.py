"""Dodo (Raphus cucullatus): a plump flightless pigeon, not a dinosaur template.

Big pear-shaped body sitting low, short thick S-neck, small feathered head with a bare grey face, a massive hooked beak
(as long as the head and neck together are thick), tiny stub wings folded on the flanks, a raised tuft of curly tail
plumes, short thick yellow legs with three forward toes and a hind toe.

Same bone names as the theropod skeleton (body, torso, neck1-2, head, jaw, arm/forearm/hand, thigh/shin/ankle/foot,
toe2-4, tail1-2) so the biped animation set, head tracking and skinning work unchanged.  Built directly in model
pixels (no rex-template warp).  Space: +y up, ground y = 0, faces -z.
"""
import math
import numpy as np
from sdfkit import Sculpt, normalize, frame_from
from theropod import Theropod, S, side_name
import extras as EX

LX = 6.2          # leg spacing (half)


class Dodo(Theropod):
    def __init__(self, params):
        p = dict(skull="dodo", teeth=False, torso=(1.0, 1.0, 1.0), belly=1.0, head_scale=1.0, lift=0.0, skull_k=(1.0, 1.0, 1.0),
                 eye_size=1.0, features=())
        p.update(params)
        super().__init__(p)

    # no template warp: everything is authored in final model space
    def X(self, p, bone=None):
        return np.array(p, dtype=float)

    def radius_scale(self, bone):
        return 1.0

    # ------------------------------------------------------------------ skeleton
    def toe_base(self, s, t):
        return (s * LX + s * {2: -0.55, 3: 0.0, 4: 0.55}[t], 1.5, 0.6)

    def toe_dir(self, s, t):
        yaw = {2: -0.5, 3: 0.02, 4: 0.48}[t] * s
        return np.array([math.sin(yaw), 0.0, -math.cos(yaw)])

    def skeleton(self, sc):
        sc.bone("root", None, (0, 0, 0))
        sc.bone("body", "root", (0, 24.0, 5.0))
        sc.bone("torso", "body", (0, 26.5, -3.0))
        sc.bone("neck1", "torso", (0, 33.5, -9.5))
        sc.bone("neck2", "neck1", (0, 38.5, -12.5))
        sc.bone("head", "neck2", (0, 43.2, -14.2))
        sc.bone("jaw", "head", (0, 42.6, -17.6))
        for s in S:
            n = side_name(s)
            sc.bone("arm_" + n, "torso", (s * 10.6, 30.5, -4.5))
            sc.bone("forearm_" + n, "arm_" + n, (s * 12.4, 28.0, 0.5))
            sc.bone("hand_" + n, "forearm_" + n, (s * 12.6, 26.6, 5.0))
            sc.bone("thigh_" + n, "body", (s * 5.6, 21.0, 4.0))
            sc.bone("shin_" + n, "thigh_" + n, (s * 6.0, 14.6, -0.6))
            sc.bone("ankle_" + n, "shin_" + n, (s * LX, 8.8, 3.6))
            sc.bone("foot_" + n, "ankle_" + n, (s * LX, 2.0, 1.6))
            for t in (2, 3, 4):
                sc.bone("toe%d_%s" % (t, n), "foot_" + n, self.toe_base(s, t))
        sc.bone("tail1", "body", (0, 30.0, 17.5))
        sc.bone("tail2", "tail1", (0, 33.6, 20.6))

    def build(self):
        sc = Sculpt()
        self.skeleton(sc)
        self.torso(sc)
        self.neck(sc)
        self.skull_dodo(sc)
        self.arms(sc)
        self.legs(sc)
        self.tail(sc)
        self.finalize(sc)
        return sc

    # ------------------------------------------------------------------ body
    def torso(self, sc):
        sc.ell((0, 25.5, 2.5), (12.6, 13.2, 16.0), bone=[("body", 0.55), ("torso", 0.45)], k=3.0, rot=(-0.22, 0, 0))   # pear body
        sc.ell((0, 28.6, -6.5), (10.8, 11.2, 9.2), bone="torso", k=3.0)                                            # breast
        sc.ell((0, 19.2, 1.0), (11.2, 7.6, 12.0), bone=[("body", 0.6), ("torso", 0.4)], k=3.0)                      # heavy belly
        sc.ell((0, 27.8, 12.8), (10.0, 10.4, 8.6), bone="body", k=3.0)                                              # rump
        sc.ell((0, 33.2, 6.0), (8.6, 4.6, 12.0), bone=[("torso", 0.5), ("body", 0.5)], k=3.0)                       # back
        for s in S:
            sc.ell((s * 7.2, 21.5, 5.0), (4.6, 6.2, 6.4), bone=[("thigh_" + side_name(s), 0.6), ("body", 0.4)], k=2.4)  # feather "trousers"

    def neck(self, sc):
        sc.spline_tube([(0, 30.5, -6.5), (0, 34.6, -10.6), (0, 38.6, -12.6), (0, 42.2, -13.6), (0, 44.6, -14.0)],
                       [7.6, 6.4, 5.5, 5.0, 4.8], ["torso", "neck1", "neck2", "neck2", "head"], k=2.6, sq=(0.92, 1.0), sub=4)
        sc.ell((0, 33.0, -9.0), (7.0, 5.6, 5.2), bone=[("neck1", 0.6), ("torso", 0.4)], k=2.4)                     # crop / ruff

    def skull_dodo(self, sc):
        H, J = "head", "jaw"
        # small rounded feathered cranium, bare face in front of the eyes
        sc.ell((0, 45.9, -14.2), (5.2, 4.9, 5.4), bone=H, k=1.6)
        sc.ell((0, 44.5, -17.6), (4.3, 3.9, 3.6), bone=H, k=1.5, mat="lid")                 # bare face (grey skin)
        sc.ell((0, 44.6, -19.4), (3.4, 3.4, 1.8), bone=H, k=1.2, mat="lid")                 # skin at the beak base
        for s in S:
            sc.ell((s * 3.75, 47.05, -16.6), (1.35, 0.7, 1.8), bone=H, k=0.5, mat="lid")      # brow over the eye
            sc.ell((s * 4.15, 46.15, -16.8), (1.0, 0.9, 1.1), bone=H, op="sub", k=0.35)      # eye socket
        # massive upper beak: tall narrow sides, swollen hooked tip
        sc.cone((0, 44.9, -20.4), (0, 44.1, -28.6), 3.45, 2.3, bone=H, k=1.1, sq=(0.64, 1.0), mat="beak")
        sc.cone((0, 46.7, -20.6), (0, 45.7, -28.0), 1.2, 0.95, bone=H, k=0.9, mat="beak")      # culmen ridge
        sc.ell((0, 44.5, -29.7), (2.1, 2.65, 2.6), bone=H, k=1.6, mat="beak2")                 # swollen tip
        sc.cone((0, 44.9, -30.8), (0, 41.5, -32.9), 1.6, 0.45, bone=H, k=0.9, mat="beak2")     # hook
        for s in S:
            sc.cone((s * 1.25, 44.9, -22.4), (s * 1.05, 44.5, -25.6), 0.35, 0.3, bone=H, op="sub", k=0.25)   # nostril slit
            sc.cone((s * 2.2, 43.2, -19.2), (s * 1.6, 42.7, -28.0), 0.45, 0.35, bone=H, op="sub", k=0.4)     # cutting edge groove
        # lower beak (jaw)
        sc.ell((0, 41.6, -18.4), (2.9, 1.9, 2.4), bone=J, group="jaw", k=1.0, mat="lid")
        sc.cone((0, 41.9, -20.2), (0, 42.1, -29.2), 2.7, 1.35, bone=J, group="jaw", k=1.0, sq=(0.74, 0.85), mat="beak")
        sc.ell((0, 42.3, -29.4), (1.3, 1.0, 1.4), bone=J, group="jaw", k=0.7, mat="beak2")
        sc.ell((0, 43.2, -22.5), (1.2, 0.5, 4.2), bone=J, group="tongue", k=0.6, mat="tongue")

    # ------------------------------------------------------------------ limbs
    def arms(self, sc):
        """tiny stub wings folded on the flanks"""
        for s in S:
            n = side_name(s)
            sc.ell((s * 11.3, 29.0, -2.0), (2.0, 3.6, 5.2), bone=[("arm_" + n, 0.6), ("torso", 0.4)], k=1.6, rot=(0.25, 0, s * 0.1))
            sc.ell((s * 12.3, 27.0, 3.0), (1.6, 3.0, 4.6), bone="forearm_" + n, k=1.3, rot=(0.35, 0, s * 0.12))

    def legs(self, sc):
        for s in S:
            n = side_name(s)
            th, sh, an, ft = "thigh_" + n, "shin_" + n, "ankle_" + n, "foot_" + n
            # feathered drumstick (tibiotarsus) down to the heel
            sc.cone((s * 5.9, 19.0, 3.0), (s * 6.1, 14.4, -0.4), 4.2, 3.0, bone=th, k=1.8)
            sc.cone((s * 6.1, 14.6, -0.4), (s * LX, 9.2, 3.4), 2.9, 1.9, bone=sh, k=1.4)
            # bare scaly yellow tarsus: short and thick
            sc.ell((s * LX, 8.9, 3.5), (1.75, 1.6, 1.75), bone=[(sh, 0.5), (an, 0.5)], k=0.8)
            sc.cone((s * LX, 8.8, 3.5), (s * LX, 2.2, 1.6), 1.6, 1.45, bone=an, k=0.9, sq=(0.92, 1.0))
            sc.ell((s * LX, 1.75, 1.2), (1.9, 1.15, 1.8), bone=ft, k=0.8)
            for t in (2, 3, 4):
                b0 = np.array(self.toe_base(s, t))
                d = self.toe_dir(s, t)
                L = {2: 4.6, 3: 5.8, 4: 4.8}[t]
                tb = "toe%d_%s" % (t, n)
                p1 = b0 + d * (L * 0.45) + np.array([0, -0.45, 0])
                p2 = b0 + d * L + np.array([0, -0.75, 0])
                sc.spline_tube([b0, p1, p2], [0.95, 0.78, 0.6], [ft, tb, tb], k=0.5, sq=(1.0, 0.85), sub=3)
                sc.ell(p1 + np.array([0, 0.2, 0]), (0.9, 0.65, 0.85), bone=tb, k=0.35)
            # hind toe (hallux)
            h0 = np.array([s * LX, 1.6, 2.4])
            sc.cone(h0, h0 + np.array([s * 0.3, -0.75, 2.4]), 0.75, 0.55, bone=ft, k=0.45)

    def tail(self, sc):
        sc.ell((0, 31.0, 17.6), (5.6, 5.0, 4.6), bone="tail1", k=2.2)

    # ------------------------------------------------------------------ explicit details
    def teeth_specs(self):
        return []

    def claw_specs(self):
        out = []
        for s in S:
            n = side_name(s)
            for t in (2, 3, 4):
                b0 = np.array(self.toe_base(s, t))
                d = self.toe_dir(s, t)
                L = {2: 4.6, 3: 5.8, 4: 4.8}[t]
                tip = b0 + d * L + np.array([0, -0.75, 0])
                pts = [tip + d * (1.5 * k / 3.0 - 0.25) + np.array([0, 0.15 - 0.55 * (k / 3.0) ** 2, 0]) for k in range(4)]
                out.append(("toe%d_%s" % (t, n), pts, [0.55, 0.42, 0.26, 0.05]))
            h0 = np.array([s * LX, 1.6, 2.4]) + np.array([s * 0.3, -0.75, 2.4])
            out.append(("foot_" + n, [h0 + np.array([0, -0.1 * k, 0.45 * k]) for k in range(4)], [0.45, 0.35, 0.22, 0.04]))
        return out

    def eyes(self):
        out = []
        for s in S:
            c = np.array([s * 3.95, 46.15, -16.85]) + np.array([0, self.ground, 0])
            out.append((tuple(c), normalize((s * 0.86, 0.06, -0.5))))
        return out, 0.86 * self.P["eye_size"]

    def extra_parts(self, sc):
        """curly tail plumes, wing quills and a ruff of loose feathers at the back of the head"""
        parts = []
        piv = lambda b: sc.bones[b][1]
        t2 = piv("tail2")
        for k in range(11):
            a = -1.0 + k * (2.0 / 10)
            pts, radii = [], []
            L = 7.5 + 2.0 * math.cos(a * 1.4)
            base = t2 + np.array([math.sin(a) * 2.6, -0.6 - 0.8 * abs(a), -1.5])
            for i in range(7):
                u = i / 6.0
                # rise up and back, then curl forward over the rump
                ang = 0.45 + 2.1 * u ** 1.5
                r = L * u
                pts.append(base + np.array([math.sin(a) * (1.0 + 3.6 * u), r * math.sin(ang) * 0.9, r * math.cos(ang) * 0.75 + 1.6 * u]))
                radii.append(1.15 * (1 - u) ** 0.55 + 0.14)
            v, f, nrm = EX.K.tube_mesh(pts, radii, n=6)
            parts.append(EX.rigid(v, f, nrm, "tail2", sc, "plume"))
        for s in S:
            n = side_name(s)
            h = piv("hand_" + n)
            for k in range(4):
                d = normalize(np.array([s * 0.2, 0.25 - 0.18 * k, 1.0]))
                pos = h + np.array([s * 0.3, 0.8 - 0.9 * k, -0.6])
                axes = frame_from(d, (s * 1.0, 0, 0))
                v, f, nrm = EX.ellipsoid(pos + d * 2.0, axes, (0.75, 0.16, 2.4 - 0.2 * k), 10, 6)
                parts.append(EX.rigid(v, f, nrm, "hand_" + n, sc, "feather"))
        return parts
