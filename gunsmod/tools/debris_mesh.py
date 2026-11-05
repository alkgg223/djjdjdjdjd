"""Burnt aircraft wreckage thrown about when a jet blows up or hits the ground: a torn wing panel, a tail fin, a
fuselage section ripped open at both ends, an engine with its nozzle, a bent skin panel. Model units = 1/16 block,
centred on (8, 0, 8) so the renderer can tumble them about their middle. Charred black with some paint left."""
import math

import numpy as np

import meshkit as K
from meshkit import Mesh, v3

rng = np.random.default_rng(11)


def jag(n, amp):
    return [amp * (rng.random() - 0.5) for _ in range(n)]


def wing():
    """a swept wing panel, the root torn off ragged"""
    m = Mesh()
    root = [(8 - 30 + j * 6, 8 + d) for j, d in enumerate(jag(6, 4))]
    outline = [(-22.0, 8.0), (30.0, -6.0), (34.0, 2.0)] + [(x + 4, z + 14) for x, z in reversed(root)]
    pts = [v3(x, 2.0, z) for x, z in outline]
    K.plate(pts, 1.4, "skin_dark", m=m)
    # charred along the torn root and in patches
    for x, z in root[:-1]:
        K.box((x + 2, 1.2, z + 10), (x + 6, 2.85, z + 13), "burnt", m=m)
    K.box((0, 2.75, 2), (12, 2.8, 8), "burnt", m=m, faces="Y")
    return m.translate((-6, 0, -2))


def fin():
    m = Mesh()
    K.plate([v3(8, 0, -12), v3(8, 0, 14), v3(8, 26, 18), v3(8, 26, 6)], 1.0, "skin_dark", m=m)
    for k in range(4):
        K.box((7.4, k * 2.0, -12 + k * 6), (8.6, k * 2.0 + 2.0, -6 + k * 6), "burnt", m=m)
    return m.translate((0, 0, 0))


def fuselage():
    """a section of the fuselage, both ends torn: the skin outside, the charred frames inside"""
    m = Mesh()
    n = 16
    r = 10.0
    rings = []
    for z, amp in ((-16, 5), (-8, 0), (8, 0), (16, 6)):
        ring = []
        for k in range(n):
            a = 2 * math.pi * k / n
            zz = z + (amp * (rng.random() - 0.5) if amp else 0)
            ring.append(v3(8 + math.cos(a) * r, 10 + math.sin(a) * r * 0.9, 8 + zz))
        rings.append(ring)
    K.loft(rings, "skin_dark", caps=(False, False), m=m)
    inner = [[(p - np.array([8, 10, 0])) * 0.92 + np.array([8, 10, 0]) for p in ring] for ring in rings]
    inn = K.loft(inner, "burnt_metal", caps=(False, False))
    for p in inn.polys:
        p["pts"].reverse()
    m.extend(inn)
    for z in (-4, 4):
        K.torus("z", (8, 10), 8 + z, r * 0.9, 0.6, "burnt", n=16, k=4, m=m)
    for k in range(5):
        a = rng.random() * 2 * math.pi
        K.box((8 + math.cos(a) * r - 2, 10 + math.sin(a) * r - 2, 8 + rng.uniform(-12, 12) - 2),
              (8 + math.cos(a) * r + 2, 10 + math.sin(a) * r + 2, 8 + rng.uniform(-12, 12) + 2), "burnt", m=m)
    return m.translate((0, -10, 0))


def engine():
    m = Mesh()
    K.cylinder("z", (8, 8), 6.0, -10, 10, "burnt_metal", n=16, m=m)
    K.cylinder("z", (8, 8), 5.6, 10, 16, "burnt", n=16, r1=4.6, caps=(False, False), m=m)
    for k in range(12):
        a = math.radians(k * 30)
        K.tube([(8 + math.cos(a) * 5.4, 8 + math.sin(a) * 5.4, 10), (8 + math.cos(a) * 4.4, 8 + math.sin(a) * 4.4, 16)], 0.4, "burnt_metal", n=4, m=m)
    K.cylinder("z", (8, 8), 5.0, -10.2, -9.8, "burnt", n=14, m=m)
    for k in range(10):
        a = math.radians(k * 36)
        K.box((8 + math.cos(a) * 1.0 - 0.5, 8 + math.sin(a) * 1.0 - 0.5, -10.4), (8 + math.cos(a) * 4.6 + 0.5, 8 + math.sin(a) * 4.6 + 0.5, -10.0),
              "burnt_metal", m=m)
    return m.translate((0, -8 + 6, 0))


def panel():
    m = Mesh()
    pts = []
    for j in range(7):
        a = 2 * math.pi * j / 7
        rr = 7 + rng.random() * 4
        pts.append((8 + math.cos(a) * rr, 8 + math.sin(a) * rr))
    top = [v3(x, 1.0 + 0.15 * (x - 8), z) for x, z in pts]
    K.plate(top, 0.6, "skin_dark", m=m)
    K.box((5, 1.35, 5), (10, 1.4, 9), "burnt", m=m, faces="Y")
    return m


PIECES = {"wing": wing, "fin": fin, "fuselage": fuselage, "engine": engine, "panel": panel}

if __name__ == "__main__":
    import os
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    for k, fn in PIECES.items():
        mesh = fn()
        K.write_part(mesh, "entity/debris", k, RES)
        lo, hi = mesh.bounds()
        print(k, mesh.quad_count(), np.round(lo, 1), np.round(hi, 1))
