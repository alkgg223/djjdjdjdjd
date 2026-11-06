"""Realistic building pieces (ARK style) in three tiers - thatch, wood, stone: foundation, wall, doorframe, door, ceiling.
Sculpted like the dinosaurs (plank by plank, stone by stone), baked textures, exported as OBJ block models.

    python3 building.py <resources dir> [tier ...]

Local space of a piece (px, 16 px = 1 block), origin = bottom centre of its master block:
  foundation / ceiling: x, z in [-24, 24]; foundation y in [0, 16], ceiling y in [0, 4] (bottom of its block)
  wall / doorframe: x in [-24, 24], y in [0, 48], panel in z [-8, -4] (outer face at z = -8, the facing side)
  door: x in [-7.6, 7.6], y in [0.3, 31.7], hinge axis at x = -7.8, z = -6.2
"""
import json
import math
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import sdfkit as K
from sdfkit import Sculpt, normalize
import hero_build as HB
import bake_hero as BH
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, LIGHT

BONES = ["root"]


class P_:
    """piece under construction: SDF primitives + explicit meshes"""

    def __init__(self):
        self.sc = Sculpt()
        self.sc.bone("root", None, (0, 0, 0))
        self.meshes = []
        self.n = 0

    def box(self, c, h, mat, rr=0.35, rot=None, k=0.06):
        self.n += 1
        self.sc.rbox(c, h, rr, mat="%s.%d" % (mat, self.n), bone="root", k=k, rot=rot)

    def log(self, a, b, r, mat="log", k=0.1):
        self.n += 1
        self.sc.cone(a, b, r, r * 0.97, mat="%s.%d" % (mat, self.n), bone="root", k=k)

    def ell(self, c, r, mat, k=0.1, rot=None):
        self.n += 1
        self.sc.ell(c, r, mat="%s.%d" % (mat, self.n), bone="root", k=k, rot=rot)

    def tube(self, path, radii, mat, n=8):
        v, f, nn = K.tube_mesh([np.asarray(p, float) for p in path], radii, n=n)
        self.meshes.append(HB.rigid_part(v, f, nn, "root", BONES, mat + ".0", ao=0.9))

    def hexbolt(self, c, axis, r):
        a = normalize(np.asarray(axis, float))
        c = np.asarray(c, float)
        v, f, nn = K.tube_mesh([c - a * 0.25, c + a * 0.25], [r, r], n=6)
        self.meshes.append(HB.rigid_part(v, f, nn, "root", BONES, "rivet.0", ao=0.9))

    def sphere(self, c, r, mat):
        v, f, nn = K.sphere_mesh(c, r, 8, 6)
        self.meshes.append(HB.rigid_part(v, f, nn, "root", BONES, mat + ".0", ao=0.85))


RNG = np.random.default_rng(42)


def jitter(a):
    return RNG.uniform(-a, a)


# ============================================================================================ wood tier
def wood_planks_vertical(p, x0, x1, y0, y1, z0, z1, skip=None, mat="plank"):
    x = x0
    while x < x1 - 0.5:
        w = min(5.6 + jitter(0.5), x1 - x)
        cx = x + w / 2
        if skip is None or not skip(cx):
            dz = jitter(0.25)
            p.box((cx, (y0 + y1) / 2 + jitter(0.2), (z0 + z1) / 2 + dz), (w / 2 - 0.2, (y1 - y0) / 2, (z1 - z0) / 2), mat)
        x += w


def wood_foundation(p):
    for s in (-1, 1):
        p.log((-24, 12.0, s * 21.0), (24, 12.0, s * 21.0), 3.4)
        p.log((s * 21.0, 11.6, -24), (s * 21.0, 11.6, 24), 3.3)
    for sx in (-1, 1):
        for sz in (-1, 1):
            p.log((sx * 21, 0, sz * 21), (sx * 21, 13, sz * 21), 3.2)
    p.log((0, 0, 0), (0, 12, 0), 2.8)
    p.log((-21, 11.4, 0), (21, 11.4, 0), 2.6)
    x = -24.0
    while x < 23.5:                      # deck planks across the frame
        w = 5.8 + jitter(0.4)
        p.box((x + w / 2, 15.0 + jitter(0.12), jitter(0.3)), (w / 2 - 0.2, 1.0, 24.0 - abs(jitter(0.6))), "plank")
        x += w
    for s in (-1, 1):
        for xx in np.linspace(-20, 20, 7):
            p.sphere((xx, 16.05, s * 21), 0.45, "iron")


def wood_wall(p, door=False):
    skip = (lambda cx: abs(cx) < 8.2) if door else None
    if door:
        wood_planks_vertical(p, -22, 22, 32.5, 47.5, -7.4, -4.8)
        wood_planks_vertical(p, -22, -9.5, 0.5, 33, -7.4, -4.8)
        wood_planks_vertical(p, 9.5, 22, 0.5, 33, -7.4, -4.8)
    else:
        wood_planks_vertical(p, -22, 22, 0.5, 47.5, -7.4, -4.8)
    for s in (-1, 1):                   # corner posts
        p.box((s * 22.2, 24, -6.0), (2.0, 24, 2.0), "beam", rr=0.5)
    for yb in (6.0, 41.0):              # rails on the inner face
        if door and yb < 32:
            for s in (-1, 1):
                p.box((s * 15.8, yb, -3.6), (6.0, 1.7, 1.2), "beam", rr=0.4)
        else:
            p.box((0, yb, -3.6), (20.5, 1.7, 1.2), "beam", rr=0.4)
    if not door:                         # diagonal brace
        a = math.atan2(35, 38)
        p.box((0, 23.5, -3.4), (25.5, 1.5, 1.0), "beam", rr=0.4, rot=(0, 0, a))
    else:
        for s in (-1, 1):
            p.box((s * 9.4, 17, -6.0), (1.4, 17, 2.4), "beam", rr=0.4)       # jambs
        p.box((0, 33.6, -6.0), (11.0, 1.6, 2.6), "beam", rr=0.4)                # lintel
    for yb in (6.0, 41.0):
        for xx in np.linspace(-19, 19, 8):
            if door and yb < 32 and abs(xx) < 9:
                continue
            p.sphere((xx, yb, -2.3), 0.42, "iron")


def wood_door(p):
    x = -7.6
    while x < 7.4:
        w = min(3.8 + jitter(0.3), 7.6 - x)
        p.box((x + w / 2, 16.0, -6.2 + jitter(0.12)), (w / 2 - 0.15, 15.7, 1.1), "plank")
        x += w
    for yb in (5.0, 27.0):
        p.box((0, yb, -4.6), (7.2, 1.4, 0.6), "beam", rr=0.3)
    a = math.atan2(22, 14.4)
    p.box((0, 16, -4.5), (12.6, 1.2, 0.55), "beam", rr=0.3, rot=(0, 0, a))
    for yb in (5.0, 27.0):              # strap hinges and nails
        p.box((-3.5, yb, -7.45), (4.4, 0.8, 0.18), "iron", rr=0.1)
        p.log((-7.8, yb - 1.4, -6.2), (-7.8, yb + 1.4, -6.2), 0.55, mat="iron")
    v, f, n = K.tube_mesh([np.array([5.0 + 1.1 * math.cos(t), 15 + 1.1 * math.sin(t), -7.9]) for t in np.linspace(0, 2 * math.pi, 17)],
                          [0.22] * 17, n=6)
    p.meshes.append(HB.rigid_part(v, f, n, "root", BONES, "iron.0", ao=0.9))


def wood_ceiling(p):
    for s in (-1, 1):
        p.box((s * 12.0, 1.0, 0), (1.8, 1.0, 23.8), "beam", rr=0.5)
    x = -24.0
    while x < 23.5:
        w = 5.6 + jitter(0.4)
        p.box((jitter(0.3), 3.0 + jitter(0.08), x + w / 2), (23.8, 0.95, w / 2 - 0.2), "plank")
        x += w


# ============================================================================================ stone tier
def stone_course(p, x0, x1, y0, h, z0, z1, skip=None):
    x = x0 + (RNG.uniform(0, 5) if y0 > 1 else 0)
    first = True
    while x < x1 - 0.4:
        w = min(RNG.uniform(6.5, 11.5), x1 - x) if not first else min(RNG.uniform(3, 9), x1 - x)
        first = False
        cx = x + w / 2
        if skip is None or not skip(cx, y0 + h / 2, w):
            p.box((cx, y0 + h / 2, (z0 + z1) / 2 + jitter(0.3)), (w / 2 - 0.35, h / 2 - 0.35, (z1 - z0) / 2 + jitter(0.1)), "stone",
                  rr=0.9, k=0.05)
        x += w


def stone_foundation(p):
    p.box((0, 7.5, 0), (23.6, 7.4, 23.6), "mortar", rr=0.6)
    for y0 in (0.2, 5.4, 10.6):
        for s in (-1, 1):
            stone_course(p, -24, 24, y0, 5.2, s * 24 - 1.6, s * 24 + 1.6)
            # side courses along z (rotated copy)
            z = -24 + RNG.uniform(0, 4)
            while z < 23.6:
                w = min(RNG.uniform(6.5, 11.5), 24 - z)
                p.box((s * 24 + jitter(0.3), y0 + 2.6, z + w / 2), (1.6, 2.25, w / 2 - 0.35), "stone", rr=0.9, k=0.05)
                z += w
    # flagstone paving on top
    for i in range(4):
        for j in range(4):
            cx, cz = -18 + i * 12 + jitter(1.0), -18 + j * 12 + jitter(1.0)
            p.box((cx, 15.2 + jitter(0.15), cz), (5.6 + jitter(0.4), 0.85, 5.6 + jitter(0.4)), "stone", rr=0.7, k=0.05, rot=(0, jitter(0.12), 0))


def stone_wall(p, door=False):
    p.box((0, 24, -6.0), (23.4, 23.6, 1.6), "mortar", rr=0.5) if not door else None
    if door:
        p.box((-16.2, 16.5, -6.0), (7.2, 16.2, 1.6), "mortar", rr=0.5)
        p.box((16.2, 16.5, -6.0), (7.2, 16.2, 1.6), "mortar", rr=0.5)
        p.box((0, 40.5, -6.0), (23.4, 7.2, 1.6), "mortar", rr=0.5)
    skip = (lambda cx, cy, w: abs(cx) < 8.6 + w / 2 - 1.0 and cy < 33) if door else None
    y = 0.2
    while y < 47.8:
        h = min(RNG.uniform(5.2, 6.6), 48 - y)
        stone_course(p, -24, 24, y, h, -8.0, -4.0, skip)
        y += h
    if door:
        for s in (-1, 1):                     # dressed jamb stones and a big lintel with a keystone
            for k_ in range(5):
                p.box((s * 9.8, 3.4 + k_ * 6.4, -6.0), (1.8 + (k_ % 2) * 0.6, 3.0, 2.3), "stone", rr=0.8, k=0.05)
        p.box((0, 34.6, -6.0), (12.2, 2.2, 2.4), "stone", rr=0.8, k=0.05)
        p.box((0, 35.4, -6.2), (2.0, 3.0, 2.6), "stone", rr=0.6, k=0.05)


def stone_door(p):
    wood_door(p)
    for yb in (10.0, 20.0):                   # extra iron bands with rivets
        p.box((0, yb, -7.45), (7.4, 0.9, 0.2), "iron", rr=0.12)
        for xx in np.linspace(-6, 6, 5):
            p.sphere((xx, yb, -7.7), 0.35, "iron")


def stone_ceiling(p):
    for s in (-1, 1):
        p.box((s * 12.0, 1.0, 0), (1.9, 1.0, 23.8), "beam", rr=0.5)
    for i in range(4):
        for j in range(4):
            p.box((-18 + i * 12 + jitter(0.3), 3.0, -18 + j * 12 + jitter(0.3)), (5.8, 0.95, 5.8), "stone", rr=0.6, k=0.05)


# ============================================================================================ thatch tier
def straw_bundles(p, x0, x1, y0, y1, z, skip=None):
    y = y0 + 1.6
    while y < y1 - 1.0:
        if skip is None:
            p.log((x0, y + jitter(0.25), z + jitter(0.3)), (x1, y + jitter(0.25), z + jitter(0.3)), 1.75, mat="straw", k=0.25)
        else:
            for (a, b) in skip(y):
                p.log((a, y + jitter(0.25), z + jitter(0.3)), (b, y + jitter(0.25), z + jitter(0.3)), 1.75, mat="straw", k=0.25)
        y += 3.1


def thatch_foundation(p):
    for s in (-1, 1):
        p.log((-24, 12.5, s * 21.0), (24, 12.5, s * 21.0), 3.2)
        p.log((s * 21.0, 12.0, -24), (s * 21.0, 12.0, 24), 3.1)
        for sz in (-1, 1):
            p.log((s * 21, 0, sz * 21), (s * 21, 13, sz * 21), 3.0)
    x = -22.0
    while x < 22:
        p.log((x, 14.6 + jitter(0.2), -23.5), (x, 14.6 + jitter(0.2), 23.5), 1.45 + jitter(0.1))
        x += 2.85
    for s in (-1, 1):
        for zz in np.linspace(-18, 18, 5):
            p.tube([(s * 21 - 0.2, 16.2, zz - 1.0), (s * 21 + 0.2, 16.2, zz + 1.0)], [0.45, 0.45], "rope", n=5)


def thatch_wall(p, door=False):
    for s in (-1, 1):
        p.log((s * 22.0, 0, -6.0), (s * 22.0, 48, -6.0), 2.1)
    if door:
        for s in (-1, 1):
            p.log((s * 9.2, 0, -6.0), (s * 9.2, 34, -6.0), 1.6)
        p.log((-12, 33.4, -6.0), (12, 33.4, -6.0), 1.6)
        straw_bundles(p, -21, 21, 32.5, 48, -6.0)
        straw_bundles(p, -21, 21, 0, 33, -6.0, skip=lambda y: [(-21, -10), (10, 21)])
    else:
        straw_bundles(p, -21, 21, 0, 48, -6.0)
    for yb in (8.0, 24.0, 40.0):
        if door and yb < 32:
            continue
        p.log((-21, yb, -3.9), (21, yb, -3.9), 0.9)
        for xx in np.linspace(-16, 16, 5):
            p.tube([(xx, yb + 1.6, -3.5), (xx, yb - 1.6, -3.5)], [0.35, 0.35], "rope", n=5)


def thatch_door(p):
    for s in (-1, 1):
        p.log((s * 7.0, 0.4, -6.2), (s * 7.0, 31.6, -6.2), 0.9)
    for yb in (1.0, 31.0, 16.0):
        p.log((-7.4, yb, -6.0), (7.4, yb, -6.0), 0.8)
    y = 2.6
    while y < 30:
        p.log((-6.6, y + jitter(0.2), -6.2), (6.6, y + jitter(0.2), -6.2), 1.4, mat="straw", k=0.25)
        y += 2.5
    for yb in (6.0, 26.0):
        p.tube([(-7.8, yb, -5.4), (-7.0, yb, -7.0), (-6.0, yb, -6.0)], [0.4, 0.4, 0.4], "rope", n=5)


def thatch_ceiling(p):
    for s in (-1, 1):
        p.log((-24, 1.2, s * 12.0), (24, 1.2, s * 12.0), 1.3)
    z = -23.0
    while z < 23.5:
        p.log((-23.8, 2.8 + jitter(0.2), z + jitter(0.2)), (23.8, 2.8 + jitter(0.2), z + jitter(0.2)), 1.35, mat="straw", k=0.25)
        z += 2.45


# ============================================================================================ iron and steel tiers
def girder(p, a, b, h, w, mat="girder"):
    """I-beam between a and b (axis aligned): two flanges and a web"""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    c = (a + b) / 2
    L = np.abs(b - a)
    ax = int(np.argmax(L))
    half = np.full(3, w / 2)
    half[ax] = L[ax] / 2
    up = 1 if ax != 1 else 0
    for s_ in (-1, 1):
        off = np.zeros(3)
        off[up] = s_ * (h / 2 - 0.35)
        hh = half.copy()
        hh[up] = 0.35
        p.box(tuple(c + off), tuple(hh), mat, rr=0.12, k=0.03)
    web = half.copy()
    web[up] = h / 2 - 0.4
    side = 3 - ax - up
    web[side] = 0.3
    p.box(tuple(c), tuple(web), mat, rr=0.1, k=0.03)


def plate_wall(p, x0, x1, y0, y1, z0, z1, mat, cell=(8.0, 8.0), rivet=0.42, skip=None, bolt="rivet"):
    nx = max(1, int(round((x1 - x0) / cell[0])))
    ny = max(1, int(round((y1 - y0) / cell[1])))
    w = (x1 - x0) / nx
    h = (y1 - y0) / ny
    for i in range(nx):
        for j in range(ny):
            cx, cy = x0 + (i + 0.5) * w, y0 + (j + 0.5) * h
            if skip is not None and skip(cx, cy):
                continue
            p.box((cx, cy, (z0 + z1) / 2 + jitter(0.06)), (w / 2 - 0.18, h / 2 - 0.18, (z1 - z0) / 2), mat, rr=0.25, k=0.03)
            for (u, v) in ((-1, -1), (-1, 1), (1, -1), (1, 1), (0, -1), (0, 1), (-1, 0), (1, 0)):
                p.sphere((cx + u * (w / 2 - 0.9), cy + v * (h / 2 - 0.9), z0 - 0.15), rivet, bolt)


def iron_foundation(p):
    for s_ in (-1, 1):
        girder(p, (-24, 13.5, s_ * 22.5), (24, 13.5, s_ * 22.5), 4.0, 2.6)
        girder(p, (s_ * 22.5, 13.5, -24), (s_ * 22.5, 13.5, 24), 4.0, 2.6)
    for sx in (-1, 1):
        for sz in (-1, 1):
            p.box((sx * 22, 6.5, sz * 22), (1.8, 6.5, 1.8), "girder", rr=0.3)
    girder(p, (-22, 13.0, 0), (22, 13.0, 0), 3.2, 2.2)
    girder(p, (0, 13.0, -22), (0, 13.0, 22), 3.2, 2.2)
    for i in range(3):
        for j in range(3):
            p.box((-16 + i * 16, 15.4, -16 + j * 16), (7.85, 0.6, 7.85), "checker", rr=0.15, k=0.03)
    for s_ in (-1, 1):
        for xx in np.linspace(-20, 20, 9):
            p.sphere((xx, 16.05, s_ * 22.5), 0.45, "rivet")


def iron_wall(p, door=False):
    skip = (lambda cx, cy: abs(cx) < 8.1 and cy < 32) if door else None
    plate_wall(p, -22, 22, 0.3, 47.7, -7.4, -5.2, "ironplate", cell=(7.33, 8.0), skip=skip)
    for s_ in (-1, 1):
        girder(p, (s_ * 22.5, 0, -6.0), (s_ * 22.5, 48, -6.0), 3.0, 3.0)
    for yb in (1.0, 47.0):
        girder(p, (-22, yb, -4.2), (22, yb, -4.2), 2.2, 1.6)
    if door:
        for s_ in (-1, 1):
            p.box((s_ * 8.9, 16.5, -6.0), (1.0, 16.4, 2.0), "girder", rr=0.2)
        p.box((0, 32.8, -6.0), (9.9, 1.0, 2.0), "girder", rr=0.2)
    else:
        girder(p, (-22, 24, -4.2), (22, 24, -4.2), 2.2, 1.6)


def iron_door(p):
    plate_wall(p, -7.5, 7.5, 0.4, 31.6, -7.3, -5.3, "ironplate", cell=(7.5, 7.9), rivet=0.36)
    for yb in (5.0, 27.0):
        p.box((-5.0, yb, -7.55), (3.0, 1.0, 0.25), "girder", rr=0.1)
        p.log((-7.8, yb - 1.6, -6.2), (-7.8, yb + 1.6, -6.2), 0.6, mat="girder")
    v, f, n = ring_wheel((4.8, 16.0, -8.0), 1.4)
    p.meshes.append(HB.rigid_part(v, f, n, "root", BONES, "rivet.0", ao=0.9))


def ring_wheel(c, R):
    vs, fs, ns = [], [], []
    from sdfkit import frame_from
    parts = []
    path = [np.array([c[0] + R * math.cos(t), c[1] + R * math.sin(t), c[2]]) for t in np.linspace(0, 2 * math.pi, 19)]
    v, f, n = K.tube_mesh(path, [0.22] * 19, n=6)
    for k in range(3):
        a = k * math.pi / 3
        pa = np.array([c[0] + R * math.cos(a), c[1] + R * math.sin(a), c[2]])
        pb = np.array([c[0] - R * math.cos(a), c[1] - R * math.sin(a), c[2]])
        v2, f2, n2 = K.tube_mesh([pa, pb], [0.16, 0.16], n=5)
        f = np.concatenate([f, f2 + len(v)])
        v = np.concatenate([v, v2])
        n = np.concatenate([n, n2])
    return v, f, n


def iron_ceiling(p):
    for s_ in (-1, 1):
        girder(p, (-24, 1.6, s_ * 12), (24, 1.6, s_ * 12), 3.2, 2.0)
    for i in range(3):
        for j in range(3):
            p.box((-16 + i * 16, 3.5, -16 + j * 16), (7.85, 0.5, 7.85), "checker", rr=0.15, k=0.03)


def steel_foundation(p):
    p.box((0, 8, 0), (23.6, 7.6, 23.6), "steelplate", rr=1.2, k=0.05)
    for s_ in (-1, 1):
        for i in range(3):
            p.box((-16 + i * 16, 8, s_ * 23.8), (7.4, 6.6, 0.7), "steelplate", rr=0.6, k=0.03)
            p.box((s_ * 23.8, 8, -16 + i * 16), (0.7, 6.6, 7.4), "steelplate", rr=0.6, k=0.03)
    for i in range(3):
        for j in range(3):
            p.box((-16 + i * 16, 15.6, -16 + j * 16), (7.6, 0.45, 7.6), "steelplate", rr=0.3, k=0.03)
            for (u, v) in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
                p.hexbolt((-16 + i * 16 + u * 6.3, 16.1, -16 + j * 16 + v * 6.3), (0, 1, 0), 0.75)


def steel_wall(p, door=False):
    skip = (lambda cx, cy: abs(cx) < 8.1 and cy < 32) if door else None
    nx, ny = 3, 3
    for i in range(nx):
        for j in range(ny):
            cx, cy = -14.67 + i * 14.67, 8 + j * 16
            if skip is not None and skip(cx, cy):
                if door and j < 2:
                    for s_ in (-1, 1):
                        p.box((s_ * 11.4, cy, -6.2), (3.3, 7.8, 1.6), "steelplate", rr=0.5, k=0.03)
                continue
            p.box((cx, cy, -6.2), (7.2, 7.8, 1.6), "steelplate", rr=0.7, k=0.03)
            p.box((cx, cy, -7.6), (5.6, 6.2, 0.35), "steelplate", rr=0.4, k=0.03)
            for (u, v) in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
                p.hexbolt((cx + u * 6.0, cy + v * 6.6, -7.9), (0, 0, -1), 0.6)
    for s_ in (-1, 1):
        p.box((s_ * 22.6, 24, -6.0), (1.6, 24, 2.2), "steelplate", rr=0.5, k=0.03)
    if door:
        for s_ in (-1, 1):
            p.box((s_ * 8.6, 16.5, -6.0), (0.8, 16.4, 2.4), "hazard", rr=0.3, k=0.03)
        p.box((0, 32.9, -6.0), (9.4, 0.9, 2.4), "hazard", rr=0.3, k=0.03)


def steel_door(p):
    p.box((0, 16, -6.2), (7.4, 15.6, 1.3), "steelplate", rr=0.6, k=0.03)
    for yb in (4.0, 12.0, 20.0, 28.0):
        p.box((0, yb, -7.6), (7.0, 1.1, 0.4), "steelplate", rr=0.3, k=0.03)
        for xx in (-5.5, 0.0, 5.5):
            p.hexbolt((xx, yb, -8.0), (0, 0, -1), 0.45)
    for yb in (5.0, 27.0):
        p.log((-7.8, yb - 2.0, -6.2), (-7.8, yb + 2.0, -6.2), 0.75, mat="steelplate")
    v, f, n = ring_wheel((3.6, 16.0, -8.2), 2.2)
    p.meshes.append(HB.rigid_part(v, f, n, "root", BONES, "rivet.0", ao=0.9))


def steel_ceiling(p):
    for i in range(3):
        for j in range(3):
            p.box((-16 + i * 16, 2.0, -16 + j * 16), (7.7, 1.9, 7.7), "steelplate", rr=0.5, k=0.03)
            p.hexbolt((-16 + i * 16, 4.1, -16 + j * 16), (0, 1, 0), 0.7)


PIECES = {
    "thatch": dict(foundation=thatch_foundation, wall=thatch_wall, doorframe=lambda p: thatch_wall(p, True), door=thatch_door,
                   ceiling=thatch_ceiling),
    "wood": dict(foundation=wood_foundation, wall=wood_wall, doorframe=lambda p: wood_wall(p, True), door=wood_door,
                 ceiling=wood_ceiling),
    "stone": dict(foundation=stone_foundation, wall=stone_wall, doorframe=lambda p: stone_wall(p, True), door=stone_door,
                  ceiling=stone_ceiling),
    "iron": dict(foundation=iron_foundation, wall=iron_wall, doorframe=lambda p: iron_wall(p, True), door=iron_door,
                 ceiling=iron_ceiling),
    "steel": dict(foundation=steel_foundation, wall=steel_wall, doorframe=lambda p: steel_wall(p, True), door=steel_door,
                  ceiling=steel_ceiling),
}


# ============================================================================================ painter
def idx_of(ctx):
    return ctx.get("idx", 0)


def shade(mat, P, N, AO, ctx):
    base, _, idx = mat.partition(".")
    idx = int(idx or 0)
    n = len(P)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    seed = 17 + idx * 13
    lit = np.clip((N * LIGHT).sum(axis=1), 0, 1)
    spec = sm(0.86, 0.99, lit)
    r = (idx * 0.6180339) % 1.0
    if base in ("plank", "beam"):
        tone = hx("#9a6c3e") if base == "plank" else hx("#7a5230")
        tone = tone * (0.85 + 0.3 * r)
        # grain follows the longest axis of the board: use the dominant non-normal axis
        cross = z if ctx.get("grain") == "z" else x
        g = np.sin(cross * 2.3 + 4.0 * fbm(P, 0.9, seed, 2))
        col = mixc(np.tile(tone, (n, 1)), tone * 0.62, sm(0.2, 0.95, g) * 0.55)
        knot = sm(0.86, 0.93, vnoise(P, 2.0, seed + 5))
        col = mixc(col, tone * 0.4, knot * 0.8)
        col = col * (0.88 + 0.2 * fbm(P, 0.7, seed + 7, 3))[:, None]
        col = mixc(col, hx("#5a5a4a"), sm(0.7, 0.9, vnoise(P, 3.0, seed + 9)) * 0.15)      # weathering
    elif base == "log":
        tone = hx("#6a4a2e") * (0.85 + 0.3 * r)
        bark = np.abs(np.sin((x + y + z) * 0.0 + fbm(P, 0.8, seed, 3) * 9.0))
        col = mixc(np.tile(tone, (n, 1)), tone * 0.5, sm(0.3, 0.9, bark) * 0.6)
        col = col * (0.85 + 0.25 * vnoise(P, 0.4, seed + 3))[:, None]
        ends = sm(0.85, 0.95, np.abs(N[:, 0]))
        rings = np.sin(np.hypot(y - np.round(y / 100) * 100, z) * 2.2)
        col = mixc(col, mixc(hx("#c09a66"), hx("#8a643a"), sm(-0.3, 0.8, rings)), ends * 0.0)
    elif base == "stone":
        tone = hx("#8e8a82") * (0.82 + 0.3 * r) + np.array([0.03, 0.02, 0.0]) * (r - 0.5)
        col = np.tile(tone, (n, 1)) * (0.8 + 0.3 * fbm(P, 2.0, seed, 4))[:, None]
        col = mixc(col, hx("#a8a49c"), sm(0.65, 0.85, vnoise(P, 0.6, seed + 3)) * 0.35)
        col = mixc(col, hx("#5e6a3e"), sm(0.75, 0.9, fbm(P, 4.0, seed + 5, 2)) * sm(0.2, 0.8, N[:, 1]) * 0.4)   # moss on top
        chisel = np.abs(np.sin((x + z) * 3.1 + y * 1.3))
        col = col * (0.95 + 0.07 * chisel)[:, None]
    elif base == "mortar":
        col = np.tile(hx("#b8ae98"), (n, 1)) * (0.8 + 0.25 * fbm(P, 0.6, seed, 3))[:, None]
        AO = AO * 0.85
    elif base == "straw":
        tone = hx("#c8a85a") * (0.85 + 0.25 * r)
        fib = np.abs(np.sin(np.where(np.abs(N[:, 0]) > 0.7, y * 5.0 + z * 3.0, x * 0.4 + (y + z) * 5.5) + fbm(P, 0.5, seed, 2) * 6))
        col = mixc(np.tile(tone, (n, 1)), tone * 0.55, sm(0.4, 1.0, fib) * 0.6)
        col = mixc(col, hx("#8a7a4a"), sm(0.7, 0.9, vnoise(P, 1.5, seed + 3)) * 0.3)
    elif base == "rope":
        col = np.tile(hx("#b89a62"), (n, 1)) * (0.7 + 0.35 * np.abs(np.sin((x + y + z) * 5.0)))[:, None]
    elif base in ("ironplate", "girder", "checker"):
        tone = {"ironplate": hx("#6e737a"), "girder": hx("#4e5258"), "checker": hx("#8a8e94")}[base] * (0.92 + 0.12 * r)
        col = np.tile(tone, (n, 1)) * (0.85 + 0.2 * fbm(P, 1.2, seed, 3))[:, None]
        scratch = np.abs(np.sin((x * 0.7 + y * 2.3 + z * 1.1) * 3.0 + fbm(P, 0.4, seed + 3, 2) * 8))
        col = col * (0.95 + 0.08 * sm(0.9, 1.0, scratch))[:, None]
        rust = sm(0.62, 0.85, fbm(P * np.array([1.0, 0.25, 1.0]), 1.5, seed + 5, 3)) * sm(0.3, -0.4, N[:, 1] + (y - 24) / 60)
        col = mixc(col, hx("#7a3e1e"), rust * 0.6)                                                   # rust streaks
        if base == "checker":
            dia = (np.abs(np.sin((x + z) * 1.6)) > 0.86) & (np.abs(np.sin((x - z) * 1.6)) > 0.86)
            col = mixc(col, col * 1.3, dia.astype(float) * 0.6)
        col = col + (spec * 0.45)[:, None]
    elif base == "steelplate":
        tone = hx("#3e4650") * (0.9 + 0.15 * r)
        brushed = 0.92 + 0.08 * np.abs(np.sin((y if True else x) * 14.0 + fbm(P, 0.5, seed, 2) * 4))
        col = np.tile(tone, (n, 1)) * brushed[:, None] * (0.9 + 0.15 * fbm(P, 2.0, seed + 2, 3))[:, None]
        edge = sm(0.0, 0.15, 1 - np.abs(N[:, 2]))
        col = mixc(col, hx("#7a8490"), edge * 0.25)                                                  # worn edges
        col = col + (spec * 0.7)[:, None]
    elif base == "hazard":
        stripe = (np.floor((x + y) / 2.2) % 2 == 0)
        col = np.where(stripe[:, None], hx("#d8b020")[None, :], hx("#1e1e1e")[None, :]) * (0.85 + 0.15 * vnoise(P, 0.6, seed))[:, None]
        col = col + (spec * 0.2)[:, None]
    elif base == "rivet":
        col = np.tile(hx("#9aa0a8"), (n, 1)) * (0.75 + 0.4 * lit)[:, None] + (spec * 0.8)[:, None]
    elif base == "iron":
        col = np.tile(hx("#3e3c3a"), (n, 1)) * (0.8 + 0.4 * vnoise(P, 0.5, seed))[:, None] + (spec * 0.5)[:, None]
        col = mixc(col, hx("#8a4a22"), sm(0.6, 0.85, vnoise(P, 0.8, seed + 3)) * 0.45)
    else:
        col = np.tile(np.array([1.0, 0, 1.0]), (n, 1))
    col = col * (0.4 + 0.6 * AO)[:, None]
    return np.clip(col, 0, 1)


# ============================================================================================ build + export
def piece_ao(field, v, n):
    F, lo, cell = field
    acc = np.zeros(len(v))
    for dist, w in ((0.4, 0.35), (1.2, 0.3), (2.5, 0.2), (5.0, 0.15)):
        d = K.sample(F, lo, cell, v + n * dist)
        acc += w * np.clip((dist - d) / dist, 0, 1)
    return np.clip(1.0 - acc * 1.2, 0.3, 1.0)


def build_piece(fn, tris, cell=0.22):
    p = P_()
    fn(p)
    prims = p.sc.prims
    field = K.build_field(prims, cell, pad=1.0)
    (v, f, n), = K.polygonize(field, [tris], aggressiveness=7, max_iterations=400)[0]
    for _ in range(3):                     # thin, many-part pieces (straw) need extra passes to reach the budget
        if len(f) <= tris * 1.25:
            break
        import pyfqmr
        smp = pyfqmr.Simplify()
        smp.setMesh(v.astype(np.float64), f.astype(np.int32))
        smp.simplify_mesh(target_count=int(tris), aggressiveness=8, max_iterations=600, preserve_border=False, verbose=False)
        v, f, _ = smp.getMesh()
        n = K.gradient(field[0], field[1], field[2], v)
        n = n / np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
        f = K.fix_winding(v, f, n)
    adds = [q for q in prims if q.op == "add"]
    D = np.stack([q.sdf(v) for q in adds], axis=1)
    vmat = np.array([adds[i].mat for i in D.argmin(axis=1)])
    part = HB.rigid_part(v, f, n, "root", BONES, "x", ao=piece_ao(field, v, n))
    part["tmat"] = vmat[part["f"][:, 0]]
    return HB.merge([part] + p.meshes)


def write_obj(path, um, offset):
    v = um["v"] / 16.0 + np.asarray(offset)
    lines = ["# generated by tools/props/building.py", "mtllib %s.mtl" % os.path.basename(path)[:-4], "usemtl piece"]
    lines += ["v %.4f %.4f %.4f" % tuple(q) for q in v]
    lines += ["vt %.5f %.5f" % (t[0], t[1]) for t in um["uv"]]
    lines += ["vn %.3f %.3f %.3f" % tuple(q) for q in um["n"]]
    lines += ["f %d/%d/%d %d/%d/%d %d/%d/%d" % (a + 1, a + 1, a + 1, b + 1, b + 1, b + 1, c + 1, c + 1, c + 1) for a, b, c in um["f"]]
    with open(path, "w") as fh:
        fh.write("\n".join(lines) + "\n")
    with open(path[:-4] + ".mtl", "w") as fh:
        fh.write("newmtl piece\nKd 1.0 1.0 1.0\nmap_Kd #texture\n")


TRIS = dict(foundation=9000, wall=9000, doorframe=9000, door=3000, ceiling=6000)
TEX = dict(foundation=512, wall=512, doorframe=512, door=256, ceiling=512)

ITEM_DISPLAY = {
    "gui": {"rotation": [28, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.12, 0.12, 0.12]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.15, 0.15, 0.15]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.15, 0.15, 0.15]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.16, 0.16, 0.16]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.16, 0.16, 0.16]},
}


def main(root, tiers):
    A = root + "/assets/arkragnarok"
    for d in ("models/block/obj", "models/block", "models/item", "textures/block/piece"):
        os.makedirs(A + "/" + d, exist_ok=True)
    prev = []
    for tier in tiers:
        for piece, fn in PIECES[tier].items():
            global RNG
            RNG = np.random.default_rng(sum(map(ord, tier + piece)))
            m = build_piece(fn, TRIS[piece])
            tex = TEX[piece]
            um = BH.unwrap(m, tex, padding=3)
            img, _ = BH.bake(um, shade, tex, tex, {"grain": "z" if piece == "foundation" else "x"}, dilate=4)
            name = "%s_%s" % (tier, piece)
            shift = {"wall": (0, 0, 0.75), "doorframe": (0, 0, 0.75), "door": (0, 0, 0.75), "ceiling": (0, 0.75, 0)}.get(piece, (0, 0, 0))
            # block model: origin = bottom centre of the master block (door: hinge pivot handled by the renderer)
            write_obj(A + "/models/block/obj/%s.obj" % name, um, (0.5 + shift[0], shift[1], 0.5 + shift[2]))
            if piece == "door":
                # the door is drawn by the doorframe's block entity renderer so that it can swing: ARKM mesh + texture
                from make_rex import write_mesh, save_png
                os.makedirs(A + "/arkmesh", exist_ok=True)
                os.makedirs(A + "/textures/entity/door", exist_ok=True)
                dm = dict(um)
                dm["v"] = um["v"] + np.array(shift) * 16
                write_mesh(A + "/arkmesh/door_%s.arkm" % tier, dm, BONES)
                save_png(A + "/textures/entity/door/%s.png" % tier, img)
            Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(A + "/textures/block/piece/%s.png" % name)
            model = {"loader": "neoforge:obj", "model": "arkragnarok:models/block/obj/%s.obj" % name, "flip_v": False,
                     "automatic_culling": False, "shade_quads": True, "ambient_occlusion": False, "render_type": "minecraft:cutout",
                     "textures": {"texture": "arkragnarok:block/piece/%s" % name, "particle": "arkragnarok:block/piece/%s" % name}}
            json.dump(model, open(A + "/models/block/%s.json" % name, "w"), indent=1)
            # item model: the same mesh, centred and scaled down
            c = (um["v"].min(axis=0) + um["v"].max(axis=0)) / 2 / 16.0
            write_obj(A + "/models/block/obj/%s_item.obj" % name, um, (0.5 - c[0], 0.5 - c[1], 0.5 - c[2]))
            span = float((um["v"].max(axis=0) - um["v"].min(axis=0)).max()) / 16.0
            disp = json.loads(json.dumps(ITEM_DISPLAY))
            k = 3.0 / max(span, 0.5)
            for key in disp:
                disp[key]["scale"] = [round(s * k, 4) for s in disp[key]["scale"]]
            im = dict(model)
            im["model"] = "arkragnarok:models/block/obj/%s_item.obj" % name
            im["gui_light"] = "side"
            im["display"] = disp
            im.pop("render_type", None)
            json.dump(im, open(A + "/models/item/%s.json" % name, "w"), indent=1)
            print("%-18s %6d tris" % (name, len(um["f"])), flush=True)
            prev.append((um, img))
    if os.environ.get("PIECE_PREVIEW"):
        import view
        tiles = [view.render(um["v"], um["n"], um["f"], size=(300, 260), yaw=0.7, pitch=0.3, tex=img, uv=um["uv"]) for um, img in prev]
        Image.fromarray((view.sheet(tiles, 5) * 255).astype(np.uint8)).save(os.environ["PIECE_PREVIEW"])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:] or ["thatch", "wood", "stone"])
