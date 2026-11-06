"""ARK-style Rex saddle: conforming leather and cloth shells built from the body's distance field, plus seat,
cantle, pommel and horn, girth and breast straps, iron buckles, brass rivets, stirrups, saddlebags, bedroll and rope."""
import math
import numpy as np
import sdfkit as K
from sdfkit import normalize


def sm(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def smax(a, b, k):
    h = np.clip(0.5 - 0.5 * (b - a) / k, 0.0, 1.0)
    return b + (a - b) * h + k * h * (1.0 - h)


class Body:
    def __init__(self, field):
        self.F, self.lo, self.cell = field

    def d(self, P):
        return K.sample(self.F, self.lo, self.cell, P)

    def n(self, P):
        return K.gradient(self.F, self.lo, self.cell, P)

    def top_y(self, x, z, y0=110.0):
        ys = np.linspace(y0, 0, 2200)
        P = np.stack([np.full_like(ys, x), ys, np.full_like(ys, z)], axis=1)
        d = self.d(P)
        i = int(np.argmax(d < 0))
        return float(ys[i])

    def side_point(self, s, y, z, x0=40.0):
        xs = np.linspace(s * x0, 0, 2400)
        P = np.stack([xs, np.full_like(xs, y), np.full_like(xs, z)], axis=1)
        d = self.d(P)
        i = int(np.argmax(d < 0))
        p = P[i]
        return p, self.n(p[None, :])[0]


def torus_fn(c, axis, R, r):
    c = np.asarray(c, float)
    F = K.frame_from(axis)
    def fn(P):
        q = (P - c) @ F
        a = np.sqrt(q[:, 0] ** 2 + q[:, 1] ** 2) - R
        return np.sqrt(a * a + q[:, 2] ** 2) - r
    return fn


def oriented_box_fn(c, axes, half, rr):
    c = np.asarray(c, float)
    A = np.stack(axes, axis=1)
    def fn(P):
        q = np.abs((P - c) @ A) - half + rr
        return np.linalg.norm(np.maximum(q, 0), axis=-1) + np.minimum(q.max(axis=-1), 0) - rr
    return fn


def frame_fn(c, axes, outer, inner, rr=0.12):
    o = oriented_box_fn(c, axes, outer, rr)
    i = oriented_box_fn(c, axes, inner, rr)
    return lambda P: np.maximum(o(P), -i(P))


REGIONS = {}   # material -> region sdf (negative inside); the painter uses them for stitching near edges


def build(sculpt, body_field):
    B = Body(body_field)
    sc = sculpt
    ys = B.top_y(0, -7.5)            # top of the back under the seat
    ysp = B.top_y(0, -15.5)          # under the pommel
    ysc = B.top_y(0, -1.0)           # under the cantle
    ysb = B.top_y(0, 3.4)            # under the bedroll
    yflap_t, yflap_b = ys - 6.5, ys - 21.0
    box_lo = np.array([-24.0, ys - 40, -34.0])
    box_hi = np.array([24.0, ys + 12, 14.0])

    # ---------------------------------------------------------------- blanket / pad (woven cloth), fur trim
    def pad_region(P):
        rz = np.abs(P[:, 2] + 7.0) - 10.0
        ry = (ys - 12.0 + 0.45 * np.sin(P[:, 2] * 1.25)) - P[:, 1]
        return smax(rz, ry, 0.8)
    REGIONS["cloth"] = pad_region
    sc.func(lambda P: smax(np.abs(B.d(P) - 0.55) - 0.42, pad_region(P), 0.25), box_lo, box_hi, "cloth", group="saddle", k=0.15)

    def trim_fn(P):
        r = pad_region(P)
        return smax(np.abs(B.d(P) - 0.75) - 0.55, np.abs(r + 0.15) - 0.75, 0.2)
    sc.func(trim_fn, box_lo, box_hi, "fur", group="saddle", k=0.15)

    # ---------------------------------------------------------------- side flaps (fenders)
    def flap_region(P):
        rz = np.abs(P[:, 2] + 7.8) - 6.6
        ry = np.abs(P[:, 1] - (yflap_t + yflap_b) / 2) - (yflap_t - yflap_b) / 2
        return smax(rz, ry, 1.2)
    REGIONS["leather"] = flap_region
    sc.func(lambda P: smax(np.abs(B.d(P) - 1.4) - 0.36, flap_region(P), 0.25), box_lo, box_hi, "leather", group="saddle", k=0.12)

    # ---------------------------------------------------------------- girth straps and breast collar
    def strap_off(P):
        return 0.95 + 0.85 * sm(yflap_b - 4, yflap_b + 0.5, P[:, 1])
    for z0 in (-15.8, 0.6):
        sc.func(lambda P, z0=z0: smax(np.abs(B.d(P) - strap_off(P)) - 0.3, np.abs(P[:, 2] - z0) - 1.15, 0.15),
                box_lo, box_hi, "strap", group="saddle", k=0.1)
    REGIONS["strap"] = lambda P: np.minimum(np.abs(P[:, 2] + 15.8) - 1.15, np.abs(P[:, 2] - 0.6) - 1.15)
    a = 0.42
    def collar(P):
        plane = np.abs((P[:, 2] + 29.5) * math.cos(a) + (P[:, 1] - (ys - 6)) * math.sin(a)) - 1.05
        return smax(smax(np.abs(B.d(P) - 0.9) - 0.3, plane, 0.15), (ys - 26) - P[:, 1], 0.4)
    sc.func(collar, np.array([-24.0, ys - 40, -46.0]), np.array([24.0, ys + 8, -14.0]), "strap", group="saddle", k=0.1)

    # ---------------------------------------------------------------- seat, cantle, pommel, horn
    sc.ell((0, ys + 1.7, -7.5), (5.9, 1.9, 7.8), mat="seat", group="saddle", k=0.6)
    sc.ell((0, ys + 3.95, -7.0), (4.5, 1.55, 5.9), mat="seat", group="saddle", op="sub", k=0.9)
    sc.ell((0, ys + 2.3, -7.4), (6.4, 0.9, 8.3), mat="leather_dark", group="saddle", k=0.5)   # welt / skirt of the seat
    cant = [(-5.7, ysc + 1.5, -1.5), (-3.3, ysc + 3.8, -0.6), (0, ysc + 4.7, -0.2), (3.3, ysc + 3.8, -0.6), (5.7, ysc + 1.5, -1.5)]
    for i in range(len(cant) - 1):
        sc.cone(cant[i], cant[i + 1], 1.3, 1.3, mat="leather_dark", group="saddle", k=0.5)
    pom = [(-4.5, ysp + 1.3, -14.9), (-2.3, ysp + 2.9, -15.4), (0, ysp + 3.35, -15.6), (2.3, ysp + 2.9, -15.4), (4.5, ysp + 1.3, -14.9)]
    for i in range(len(pom) - 1):
        sc.cone(pom[i], pom[i + 1], 1.15, 1.15, mat="leather_dark", group="saddle", k=0.5)
    sc.cone((0, ysp + 3.0, -15.5), (0, ysp + 6.6, -16.8), 0.85, 0.66, mat="leather_dark", group="saddle", k=0.3)
    sc.ell((0, ysp + 6.95, -16.95), (1.2, 0.8, 1.2), mat="metal", group="saddle", k=0.2)

    # ---------------------------------------------------------------- buckles, rivets, stirrups, bags (both sides)
    for s in (-1, 1):
        for z0 in (-15.8, 0.6):
            p, n = B.side_point(s, yflap_b - 2.6, z0)
            c = p + n * 2.15
            up = normalize(np.cross(n, np.array([0, 0, 1.0])))
            if up[1] < 0:
                up = -up
            zax = np.cross(n, up)
            sc.func(frame_fn(c, [n, up, zax], np.array([0.36, 1.55, 1.75]), np.array([1.0, 0.95, 1.05])),
                    c - 3, c + 3, "metal", group="saddle", k=0.05)
            sc.func(oriented_box_fn(c + n * 0.1, [n, up, zax], np.array([0.22, 1.2, 0.2]), 0.08), c - 3, c + 3, "metal",
                    group="saddle", k=0.05)
        # rivets along the top and bottom edges of the flap
        for yy in (yflap_t - 0.9, yflap_b + 0.9):
            for i in range(7):
                z = -13.6 + i * 1.95
                p, n = B.side_point(s, yy, z)
                c = p + n * 1.95
                sc.ell(c, (0.42, 0.42, 0.42), mat="brass", group="saddle", k=0.08)
        # stirrup leather + iron
        p, n = B.side_point(s, yflap_b + 1.5, -7.8)
        top = p + n * 1.95
        bottom = top + np.array([0, -7.5, 0]) + n * 1.4
        sc.cone(top, bottom, 0.42, 0.42, mat="strap", group="saddle", k=0.1, sq=(2.0, 0.6), up=(0, 0, 1))
        ring_c = bottom + np.array([0, -1.6, 0])
        sc.func(torus_fn(ring_c, n, 1.55, 0.32), ring_c - 3, ring_c + 3, "metal", group="saddle", k=0.1)
        sc.func(oriented_box_fn(ring_c + np.array([0, -1.45, 0]), [n, np.array([0, 1.0, 0]), normalize(np.cross(n, [0, 1.0, 0]))],
                                np.array([1.0, 0.25, 1.3]), 0.1), ring_c - 4, ring_c + 4, "metal", group="saddle", k=0.1)
        # saddlebag behind the flap
        p, n = B.side_point(s, ys - 13.5, 5.2)
        up = np.array([0, 1.0, 0])
        zax = normalize(np.cross(n, up))
        up = np.cross(zax, n)
        c = p + n * 2.9
        sc.func(oriented_box_fn(c, [n, up, zax], np.array([2.0, 3.7, 3.3]), 1.4), c - 6, c + 6, "bag", group="saddle", k=0.3)
        fc = c + n * 0.35 + up * 2.2
        sc.func(oriented_box_fn(fc, [n, up, zax], np.array([2.3, 1.65, 3.5]), 0.6), fc - 6, fc + 6, "leather_dark", group="saddle", k=0.2)
        bc = c + n * 2.15 + up * 0.3
        sc.func(oriented_box_fn(bc, [n, up, zax], np.array([0.25, 1.9, 0.55]), 0.15), bc - 3, bc + 3, "strap", group="saddle", k=0.1)
        sc.func(frame_fn(bc + n * 0.25 + up * -0.6, [n, up, zax], np.array([0.3, 0.7, 0.8]), np.array([1.0, 0.4, 0.45])),
                bc - 3, bc + 3, "metal", group="saddle", k=0.05)
    # bedroll behind the cantle with two straps
    sc.cone((-6.6, ysb + 2.2, 3.4), (6.6, ysb + 2.2, 3.4), 1.9, 1.9, mat="bedroll", group="saddle", k=0.4)
    for x in (-3.8, 3.8):
        sc.cone((x - 0.45, ysb + 2.2, 3.4), (x + 0.45, ysb + 2.2, 3.4), 2.08, 2.08, mat="strap", group="saddle", k=0.1)
    # coil of rope hanging at the pommel
    rc = np.array([5.9, ysp - 0.8, -15.0])
    for k_ in range(3):
        sc.func(torus_fn(rc + np.array([0.35 * k_, 0, 0]), np.array([1.0, 0, 0.25]), 1.7, 0.36), rc - 4, rc + 4, "rope",
                group="saddle", k=0.1)
    return dict(ys=ys, ysp=ysp, ysc=ysc, yflap_t=yflap_t, yflap_b=yflap_b)
