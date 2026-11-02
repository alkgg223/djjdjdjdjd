"""Civilian cars the soldiers use to get about: a double-cab pickup and an SUV (model units of 0.25 block, front -Z,
ground y = 0, (8, 0, 8) = entity origin). They carry no weapons; the body is painted in a civilian colour by the
game and a team stripe runs along the doors. GEO holds the points the game uses."""
import math

import meshkit as K
import vehmesh as V
from cars_mesh import section, section_loft, arch, lathe
from meshkit import Mesh, v3

GEO = {}


def civ_wheel():
    """road tyre on an alloy rim, centred on (8, 8, 8), axle along X, radius 2.4 (scaled down by the game); the
    rim faces -X (the renderer turns the right hand wheels round)"""
    m = Mesh()
    lathe(m, [(7.25, 1.75), (7.28, 2.2), (7.42, 2.38), (7.6, 2.4), (8.4, 2.4), (8.58, 2.38), (8.72, 2.2), (8.75, 1.75)], "rubber", n=24)
    for i in range(24):
        groove = K.box((7.5, 10.37, 7.92), (8.5, 10.43, 8.08), "black")
        groove.rotate("x", i * 15.0, (8, 8, 8))
        m.extend(groove)
    lathe(m, [(7.26, 1.75), (7.15, 1.7), (7.12, 1.25), (7.2, 0.5), (7.05, 0.42), (7.0, 0.0)], "bright", n=20)
    lathe(m, [(8.74, 1.75), (8.85, 1.6), (8.88, 0.0)], "steel", n=14)
    # five spokes
    for k in range(5):
        a = k * 72.0
        sp = K.rbox((7.06, 8.0 - 0.18, 8.45), (7.14, 8.0 + 0.18, 9.6), 0.05, "bright", seg=1)
        sp.rotate("x", a, (8, 8, 8))
        m.extend(sp)
    K.cylinder("x", (8.0, 8.0), 0.35, 6.98, 7.02, "dark", n=10, caps=(True, False), m=m)
    return m


def _marks(sides):
    """team stripe along the doors (green or red) with a white edge"""
    out = {}
    for team, mat in (("green", "green_mark"), ("red", "red_mark")):
        m = Mesh()
        for x, n, z, w in sides:
            V.band(m, v3(x, 3.55, z), (n, 0, 0), (0, 1, 0), w, 0.55, "white_mark")
            V.band(m, v3(x + n * 0.01, 3.55, z), (n, 0, 0), (0, 1, 0), w - 0.1, 0.38, mat)
        out[f"mark_{team}"] = m
    return out


def _glass_side(m, x, y0, y1, z0, z1, slope=0.0):
    K.plate([v3(x, y0, z0), v3(x, y0, z1), v3(x - slope, y1, z1 - 0.3), v3(x - slope, y1, z0 + 0.3)], 0.06, "glass_dark", m=m)


def _common_front(m, z_front, top, x0=4.2, x1=11.8):
    """grille, headlights, bumper, number plate"""
    K.rbox((x0 + 0.1, 1.2, z_front - 0.35), (x1 - 0.1, 2.1, z_front + 0.4), 0.3, "dark", seg=2, m=m)
    K.rbox((x0 + 1.6, 2.4, z_front - 0.12), (x1 - 1.6, top - 0.5, z_front + 0.1), 0.15, "dark", seg=1, m=m)
    for i in range(4):
        y = 2.6 + i * (top - 3.3) / 4
        K.box((x0 + 1.8, y, z_front - 0.16), (x1 - 1.8, y + 0.12, z_front - 0.12), "bright", m=m, faces="z")
    for x in (x0 + 0.85, x1 - 0.85):
        K.rbox((x - 0.65, top - 1.1, z_front - 0.06), (x + 0.65, top - 0.45, z_front + 0.3), 0.12, "bright", seg=1, m=m)
        K.box((x - 0.5, top - 1.0, z_front - 0.08), (x + 0.5, top - 0.55, z_front - 0.06), "glow", m=m, faces="z")
        K.box((x - 0.5, 1.45, z_front - 0.38), (x + 0.1, 1.75, z_front - 0.36), "amber", m=m, faces="z")
    K.box((7.0, 1.35, z_front - 0.38), (9.0, 1.95, z_front - 0.36), "white", m=m, faces="z")


def _mirrors(m, z, y, x0=4.2, x1=11.8):
    for x, s in ((x0, -1), (x1, 1)):
        K.rbox((x + s * 0.15 - 0.25, y, z), (x + s * 0.15 + 0.25 + s * 0.6, y + 0.65, z + 0.35), 0.1, "dark", seg=1, m=m)


def _handles(m, xs, zs, y=4.3):
    for x in xs:
        for z in zs:
            K.rbox((x - 0.06, y, z - 0.35), (x + 0.06, y + 0.14, z + 0.35), 0.04, "bright", seg=1, m=m)


# ============================================================================================ double-cab pickup
def pickup():
    axles = [-6.4, 6.2]
    g = dict(turret=(8.0, 0.0), gun=(0.0, 0.0), muzzles=[], tubes=[], coax=(8.0, 0.0, 0.0), radar=None,
             wheels=[(x, 1.6, z) for z in axles for x in (4.5, 11.5)], wheelR=1.6, steerAxles=1,
             seats=[(6.0, 2.8, -1.2), (10.0, 2.8, -1.2), (6.4, 5.4, 6.0), (9.6, 5.4, 6.0)],
             front=[(4.6, 2.5, -10.8), (11.4, 2.5, -10.8), (8.0, 2.5, -11.0)], back=[(4.6, 2.5, 10.8), (11.4, 2.5, 10.8)],
             exhaust=[(5.0, 1.0, 10.6)], smoke=(8.0, 3.6, -8.0), size=(2.4, 2.0), gunMin=0.0, gunMax=0.0)
    GEO["pickup"] = g
    body = Mesh()
    S = section
    # the lower body: bonnet sloping to the nose, the cab sill, the load bed sides
    section_loft(body, [(-10.6, S(1.2, 3.6, 2.2, 3.0, 3.7, 3.7, 3.4)), (-10.0, S(1.2, 3.7, 2.2, 3.4, 3.8, 4.3, 3.5)),
                        (-3.4, S(1.2, 3.7, 2.2, 3.6, 3.8, 4.7, 3.55)), (2.6, S(1.2, 3.7, 2.2, 3.6, 3.8, 4.7, 3.55)),
                        (2.7, S(1.2, 3.7, 2.2, 2.6, 3.8, 2.8, 3.6)), (10.4, S(1.2, 3.7, 2.2, 2.6, 3.8, 2.8, 3.6)),
                        (10.7, S(1.4, 3.5, 2.2, 2.5, 3.6, 2.7, 3.4))], r=0.3)
    for z in axles:
        for xa, xb in ((4.0, 5.0), (11.0, 12.0)):
            arch(body, xa, xb, 1.6, z, 1.85, 2.15, "paint_mid", a0=10, a1=170)
    # cab: raked windscreen, glass all round, B and C pillars, roof
    cab = [(-3.4, S(4.6, 3.55, 4.7, 4.8, 3.55, 5.0, 3.4)), (-1.2, S(4.6, 3.55, 4.7, 4.8, 3.55, 8.0, 3.05)),
           (2.6, S(4.6, 3.55, 4.7, 4.8, 3.55, 8.0, 3.05))]
    section_loft(body, cab, "glass_dark", r=0.3)
    K.rbox((4.75, 7.75, -1.3), (11.25, 8.15, 2.7), 0.3, "paint", seg=2, m=body)
    for z in (-1.35, 0.9, 2.4):
        K.rbox((4.42, 4.7, z), (4.62, 7.9, z + 0.25), 0.08, "paint", seg=1, m=body)
        K.rbox((11.38, 4.7, z), (11.58, 7.9, z + 0.25), 0.08, "paint", seg=1, m=body)
    K.plate([v3(4.5, 4.75, -3.42), v3(4.85, 7.9, -1.25), v3(5.15, 7.9, -1.25), v3(4.8, 4.75, -3.42)], 0.12, "paint", m=body)
    K.plate([v3(11.5, 4.75, -3.42), v3(11.15, 7.9, -1.25), v3(10.85, 7.9, -1.25), v3(11.2, 4.75, -3.42)], 0.12, "paint", m=body)
    # load bed: floor, inner walls, tailgate with handle, roll bar behind the cab
    K.box((4.8, 2.7, 2.8), (11.2, 2.8, 10.3), "dark", m=body, faces="Y")
    for x0, x1 in ((4.2, 4.75), (11.25, 11.8)):
        K.rbox((x0, 2.6, 2.75), (x1, 4.7, 10.4), 0.15, "paint", seg=1, m=body)
    K.rbox((4.4, 2.6, 10.25), (11.6, 4.7, 10.7), 0.15, "paint", seg=1, m=body)
    K.rbox((7.2, 4.0, 10.68), (8.8, 4.3, 10.78), 0.05, "dark", seg=1, m=body)
    K.tube([(4.9, 4.7, 3.2), (4.9, 8.3, 3.2), (11.1, 8.3, 3.2), (11.1, 4.7, 3.2)], 0.14, "bright", n=6, m=body)
    _common_front(body, -10.6, 4.3)
    for x in (4.9, 10.1):
        K.rbox((x, 3.4, 10.7), (x + 1.0, 4.4, 10.85), 0.08, "red_light", seg=1, m=body)
    K.rbox((4.0, 1.1, 10.6), (12.0, 1.6, 11.0), 0.15, "bright", seg=1, m=body)
    _mirrors(body, -3.0, 5.2)
    _handles(body, (4.25, 11.75), (-1.6, 1.6))
    K.cylinder("z", (5.0, 1.0), 0.15, 10.6, 11.0, "gunmetal", n=8, m=body)
    parts = {"body": body}
    parts.update(_marks([(4.18, -1, -0.2, 6.0), (11.82, 1, -0.2, 6.0)]))
    return parts, g


# ============================================================================================ SUV
def suv():
    axles = [-5.6, 5.4]
    g = dict(turret=(8.0, 0.0), gun=(0.0, 0.0), muzzles=[], tubes=[], coax=(8.0, 0.0, 0.0), radar=None,
             wheels=[(x, 1.6, z) for z in axles for x in (4.6, 11.4)], wheelR=1.6, steerAxles=1,
             seats=[(6.0, 2.8, -1.4), (10.0, 2.8, -1.4), (6.0, 2.8, 2.8), (10.0, 2.8, 2.8)],
             front=[(4.6, 2.5, -9.8), (11.4, 2.5, -9.8), (8.0, 2.5, -10.0)], back=[(4.6, 2.5, 9.8), (11.4, 2.5, 9.8)],
             exhaust=[(5.0, 1.0, 9.6)], smoke=(8.0, 3.6, -7.5), size=(2.4, 2.1), gunMin=0.0, gunMax=0.0)
    GEO["suv"] = g
    body = Mesh()
    S = section
    section_loft(body, [(-9.6, S(1.2, 3.5, 2.2, 3.0, 3.6, 3.8, 3.3)), (-9.0, S(1.2, 3.65, 2.2, 3.5, 3.75, 4.5, 3.45)),
                        (-3.6, S(1.2, 3.65, 2.2, 3.7, 3.8, 4.9, 3.55)), (9.3, S(1.2, 3.65, 2.2, 3.7, 3.8, 4.9, 3.55)),
                        (9.7, S(1.4, 3.5, 2.2, 3.6, 3.6, 4.7, 3.4))], r=0.45)
    for z in axles:
        for xa, xb in ((4.1, 5.1), (10.9, 11.9)):
            arch(body, xa, xb, 1.6, z, 1.85, 2.2, "paint_mid", a0=8, a1=172)
    cab = [(-3.6, S(4.8, 3.55, 4.9, 5.0, 3.55, 5.1, 3.4)), (-1.4, S(4.8, 3.55, 4.9, 5.0, 3.55, 8.2, 3.0)),
           (8.9, S(4.8, 3.55, 4.9, 5.0, 3.55, 8.2, 3.0)), (9.3, S(4.8, 3.5, 4.9, 5.0, 3.5, 7.9, 3.0))]
    section_loft(body, cab, "glass_dark", r=0.3)
    K.rbox((4.8, 7.95, -1.5), (11.2, 8.35, 9.0), 0.35, "paint", seg=2, m=body)
    for z in (-1.55, 1.2, 5.0, 8.6):
        K.rbox((4.42, 4.9, z), (4.62, 8.1, z + 0.3), 0.08, "paint", seg=1, m=body)
        K.rbox((11.38, 4.9, z), (11.58, 8.1, z + 0.3), 0.08, "paint", seg=1, m=body)
    K.plate([v3(4.5, 4.95, -3.62), v3(4.9, 8.1, -1.45), v3(5.2, 8.1, -1.45), v3(4.8, 4.95, -3.62)], 0.12, "paint", m=body)
    K.plate([v3(11.5, 4.95, -3.62), v3(11.1, 8.1, -1.45), v3(10.8, 8.1, -1.45), v3(11.2, 4.95, -3.62)], 0.12, "paint", m=body)
    # roof rails, the spare wheel on the tailgate, tail lights, rear bumper
    for x in (5.2, 10.8):
        K.tube([(x, 8.35, -0.8), (x, 8.75, -0.4), (x, 8.75, 8.0), (x, 8.35, 8.4)], 0.1, "bright", n=6, m=body)
    K.cylinder("z", (8.0, 4.6), 1.45, 9.75, 10.5, "rubber", n=20, bevel=0.25, m=body)
    K.cylinder("z", (8.0, 4.6), 0.9, 10.45, 10.55, "dark", n=16, caps=(False, True), m=body)
    for x in (4.6, 10.4):
        K.rbox((x, 4.2, 9.55), (x + 1.0, 6.2, 9.85), 0.1, "red_light", seg=1, m=body)
    K.rbox((4.0, 1.1, 9.5), (12.0, 1.7, 10.0), 0.2, "dark", seg=1, m=body)
    _common_front(body, -9.6, 4.5)
    _mirrors(body, -3.2, 5.4)
    _handles(body, (4.25, 11.75), (-1.0, 3.4), y=4.5)
    K.cylinder("z", (5.0, 1.0), 0.15, 9.6, 10.0, "gunmetal", n=8, m=body)
    parts = {"body": body}
    parts.update(_marks([(4.18, -1, 1.6, 8.0), (11.82, 1, 1.6, 8.0)]))
    return parts, g


CIVS = {"pickup": pickup, "suv": suv}
