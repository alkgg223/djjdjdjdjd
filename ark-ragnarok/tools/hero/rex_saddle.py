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


def build(sculpt, body_field, zc=-7.5, sc_=1.0, collar_z=None, bags=True):
    Z = lambda z: zc + sc_ * (z + 7.5)
    L = lambda v: v * sc_
    B = Body(body_field)
    sc = sculpt
    ys = B.top_y(0, Z(-7.5))            # top of the back under the seat
    ysp = B.top_y(0, Z(-15.5))          # under the pommel
    ysc = B.top_y(0, Z(-1.0))           # under the cantle
    ysb = B.top_y(0, Z(3.4))            # under the bedroll
    yflap_t, yflap_b = ys - L(6.5), ys - L(21.0)
    box_lo = np.array([-L(26.0), ys - L(42), Z(-34.0) - L(14)])
    box_hi = np.array([L(26.0), ys + L(12), Z(14.0)])

    # ---------------------------------------------------------------- blanket / pad (woven cloth), fur trim
    def pad_region(P):
        rz = np.abs(P[:, 2] - Z(-7.0)) - L(10.0)
        ry = (ys - L(12.0) + L(0.45) * np.sin(P[:, 2] * 1.25 / sc_)) - P[:, 1]
        return smax(rz, ry, 0.8)
    REGIONS["cloth"] = pad_region
    sc.func(lambda P: smax(np.abs(B.d(P) - L(0.55)) - L(0.42), pad_region(P), L(0.25)), box_lo, box_hi, "cloth", group="saddle", k=0.15)

    def trim_fn(P):
        r = pad_region(P)
        return smax(np.abs(B.d(P) - L(0.75)) - L(0.55), np.abs(r + L(0.15)) - L(0.75), L(0.2))
    sc.func(trim_fn, box_lo, box_hi, "fur", group="saddle", k=0.15)

    # ---------------------------------------------------------------- side flaps (fenders)
    def flap_region(P):
        rz = np.abs(P[:, 2] - Z(-7.8)) - L(6.6)
        ry = np.abs(P[:, 1] - (yflap_t + yflap_b) / 2) - (yflap_t - yflap_b) / 2
        return smax(rz, ry, L(1.2))
    REGIONS["leather"] = flap_region
    sc.func(lambda P: smax(np.abs(B.d(P) - L(1.4)) - L(0.36), flap_region(P), L(0.25)), box_lo, box_hi, "leather", group="saddle", k=0.12)

    # ---------------------------------------------------------------- girth straps and breast collar
    def strap_off(P):
        return L(0.95) + L(0.85) * sm(yflap_b - L(4), yflap_b + L(0.5), P[:, 1])
    for z0 in (Z(-15.8), Z(0.6)):
        sc.func(lambda P, z0=z0: smax(np.abs(B.d(P) - strap_off(P)) - L(0.3), np.abs(P[:, 2] - z0) - L(1.15), L(0.15)),
                box_lo, box_hi, "strap", group="saddle", k=0.1)
    REGIONS["strap"] = lambda P: np.minimum(np.abs(P[:, 2] - Z(-15.8)) - L(1.15), np.abs(P[:, 2] - Z(0.6)) - L(1.15))
    a = 0.42
    cz = collar_z if collar_z is not None else Z(-29.5)

    def collar(P):
        plane = np.abs((P[:, 2] - cz) * math.cos(a) + (P[:, 1] - (ys - L(6))) * math.sin(a)) - L(1.05)
        return smax(smax(np.abs(B.d(P) - L(0.9)) - L(0.3), plane, L(0.15)), (ys - L(26)) - P[:, 1], L(0.4))
    sc.func(collar, np.array([-L(28.0), ys - L(44), cz - L(18.0)]), np.array([L(28.0), ys + L(8), cz + L(16.0)]), "strap", group="saddle", k=0.1)

    # ---------------------------------------------------------------- seat, cantle, pommel, horn
    sc.ell((0, ys + L(1.7), Z(-7.5)), (L(5.9), L(1.9), L(7.8)), mat="seat", group="saddle", k=L(0.6))
    sc.ell((0, ys + L(3.95), Z(-7.0)), (L(4.5), L(1.55), L(5.9)), mat="seat", group="saddle", op="sub", k=L(0.9))
    sc.ell((0, ys + L(2.3), Z(-7.4)), (L(6.4), L(0.9), L(8.3)), mat="leather_dark", group="saddle", k=L(0.5))   # welt / skirt of the seat
    cant = [(L(-5.7), ysc + L(1.5), Z(-1.5)), (L(-3.3), ysc + L(3.8), Z(-0.6)), (0, ysc + L(4.7), Z(-0.2)), (L(3.3), ysc + L(3.8), Z(-0.6)), (L(5.7), ysc + L(1.5), Z(-1.5))]
    for i in range(len(cant) - 1):
        sc.cone(cant[i], cant[i + 1], L(1.3), L(1.3), mat="leather_dark", group="saddle", k=L(0.5))
    pom = [(L(-4.5), ysp + L(1.3), Z(-14.9)), (L(-2.3), ysp + L(2.9), Z(-15.4)), (0, ysp + L(3.35), Z(-15.6)), (L(2.3), ysp + L(2.9), Z(-15.4)), (L(4.5), ysp + L(1.3), Z(-14.9))]
    for i in range(len(pom) - 1):
        sc.cone(pom[i], pom[i + 1], L(1.15), L(1.15), mat="leather_dark", group="saddle", k=L(0.5))
    sc.cone((0, ysp + L(3.0), Z(-15.5)), (0, ysp + L(6.6), Z(-16.8)), L(0.85), L(0.66), mat="leather_dark", group="saddle", k=L(0.3))
    sc.ell((0, ysp + L(6.95), Z(-16.95)), (L(1.2), L(0.8), L(1.2)), mat="metal", group="saddle", k=L(0.2))

    # ---------------------------------------------------------------- buckles, rivets, stirrups, bags (both sides)
    for s in (-1, 1):
        for z0 in (Z(-15.8), Z(0.6)):
            p, n = B.side_point(s, yflap_b - L(2.6), z0)
            c = p + n * L(2.15)
            up = normalize(np.cross(n, np.array([0, 0, 1.0])))
            if up[1] < 0:
                up = -up
            zax = np.cross(n, up)
            sc.func(frame_fn(c, [n, up, zax], L(np.array([0.36, 1.55, 1.75])), L(np.array([1.0, 0.95, 1.05]))),
                    c - L(3), c + L(3), "metal", group="saddle", k=0.05)
            sc.func(oriented_box_fn(c + n * L(0.1), [n, up, zax], L(np.array([0.22, 1.2, 0.2])), L(0.08)), c - L(3), c + L(3), "metal",
                    group="saddle", k=0.05)
        # rivets along the top and bottom edges of the flap
        for yy in (yflap_t - L(0.9), yflap_b + L(0.9)):
            for i in range(7):
                z = Z(-13.6 + i * 1.95)
                p, n = B.side_point(s, yy, z)
                c = p + n * L(1.95)
                sc.ell(c, (L(0.42), L(0.42), L(0.42)), mat="brass", group="saddle", k=0.08)
        # stirrup leather + iron
        p, n = B.side_point(s, yflap_b + L(1.5), Z(-7.8))
        top = p + n * L(1.95)
        bottom = top + np.array([0, -L(7.5), 0]) + n * L(1.4)
        sc.cone(top, bottom, L(0.42), L(0.42), mat="strap", group="saddle", k=0.1, sq=(2.0, 0.6), up=(0, 0, 1))
        ring_c = bottom + np.array([0, -L(1.6), 0])
        sc.func(torus_fn(ring_c, n, L(1.55), L(0.32)), ring_c - L(3), ring_c + L(3), "metal", group="saddle", k=0.1)
        sc.func(oriented_box_fn(ring_c + np.array([0, -L(1.45), 0]), [n, np.array([0, 1.0, 0]), normalize(np.cross(n, [0, 1.0, 0]))],
                                L(np.array([1.0, 0.25, 1.3])), L(0.1)), ring_c - L(4), ring_c + L(4), "metal", group="saddle", k=0.1)
        # saddlebag behind the flap
        if not bags:
            continue
        p, n = B.side_point(s, ys - L(13.5), Z(5.2))
        up = np.array([0, 1.0, 0])
        zax = normalize(np.cross(n, up))
        up = np.cross(zax, n)
        c = p + n * L(2.9)
        sc.func(oriented_box_fn(c, [n, up, zax], L(np.array([2.0, 3.7, 3.3])), L(1.4)), c - L(6), c + L(6), "bag", group="saddle", k=0.3)
        fc = c + n * L(0.35) + up * L(2.2)
        sc.func(oriented_box_fn(fc, [n, up, zax], L(np.array([2.3, 1.65, 3.5])), L(0.6)), fc - L(6), fc + L(6), "leather_dark", group="saddle", k=0.2)
        bc = c + n * L(2.15) + up * L(0.3)
        sc.func(oriented_box_fn(bc, [n, up, zax], L(np.array([0.25, 1.9, 0.55])), L(0.15)), bc - L(3), bc + L(3), "strap", group="saddle", k=0.1)
        sc.func(frame_fn(bc + n * L(0.25) + up * -L(0.6), [n, up, zax], L(np.array([0.3, 0.7, 0.8])), L(np.array([1.0, 0.4, 0.45]))),
                bc - L(3), bc + L(3), "metal", group="saddle", k=0.05)
    # bedroll behind the cantle with two straps
    sc.cone((-L(6.6), ysb + L(2.2), Z(3.4)), (L(6.6), ysb + L(2.2), Z(3.4)), L(1.9), L(1.9), mat="bedroll", group="saddle", k=L(0.4))
    for x in (-L(3.8), L(3.8)):
        sc.cone((x - L(0.45), ysb + L(2.2), Z(3.4)), (x + L(0.45), ysb + L(2.2), Z(3.4)), L(2.08), L(2.08), mat="strap", group="saddle", k=0.1)
    # coil of rope hanging at the pommel
    rc = np.array([L(5.9), ysp - L(0.8), Z(-15.0)])
    for k_ in range(3):
        sc.func(torus_fn(rc + np.array([L(0.35) * k_, 0, 0]), np.array([1.0, 0, 0.25]), L(1.7), L(0.36)), rc - L(4), rc + L(4), "rope",
                group="saddle", k=0.1)
    return dict(ys=ys, ysp=ysp, ysc=ysc, yflap_t=yflap_t, yflap_b=yflap_b, seat=(0.0, ys + L(3.4), Z(-7.0)))
