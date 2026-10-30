"""v1.8 vehicles: the super-heavy tanks (Maus, Object 279 on four tracks), the rocket artillery (M142 HIMARS, BM-21 Grad)
and the C-RAM gun on its trailer. Model units of 0.25 block (the heavy tanks are drawn 1.4 times bigger by the game).
GEO holds every point the game uses; gen_geo.py writes it into VehicleGeo.java."""
import math

import armor as AR
import meshkit as K
import vehmesh as V
from cars_mesh import section, section_loft, arch, louvres, marks
from helis_mesh import stations_loft
from meshkit import Mesh, v3
from tanks_mesh import side_prism, top_loft, skirt, track_loop, _split_marks

GEO = {}

# the running gear of the Abrams (track runs are a fixed 29 links long)
_RUN = dict(idler=AR.IDLER, sprocket=AR.SPROCKET, bottom=AR.TRACK_BOTTOM, top=AR.TRACK_TOP)


# ============================================================================================ Panzer VIII Maus
def maus():
    g = dict(trackX=(2.5, 13.5), wheelsZ=[-1.4 + i * 2.1 for i in range(10)], wheel=(1.45, 1.0), **_RUN,
             turret=(8.0, 11.2), gun=(8.4, 4.8), muzzle=-19.6, seat=(5.4, 9.0, 13.6), coax=(10.3, 8.0, 3.8),
             front=[(1.6, 5.4, -7.0), (14.4, 5.4, -7.0), (8.0, 5.4, -7.4)], back=[(1.6, 5.4, 22.4), (14.4, 5.4, 22.4)],
             exhaust=[(6.0, 6.6, 20.6), (10.0, 6.6, 20.6)], smoke=(8.0, 7.0, 17.0), size=(5.8, 3.6))
    GEO["maus"] = g
    body, tur, gun = Mesh(), Mesh(), Mesh()
    # lower hull between the tracks, the wide upper hull over them, the long sloped glacis and the tail
    side_prism(body, [(-4.6, 0.9), (20.6, 0.9), (21.4, 3.4), (-6.6, 3.4), (-6.0, 1.4)], 3.8, 12.2, fillet=0.35)
    side_prism(body, [(-7.0, 3.3), (22.0, 3.3), (22.4, 5.0), (21.4, 6.6), (-1.6, 6.6), (-7.2, 3.9)], 0.7, 15.3, fillet=0.45, bevel=0.35)
    # side skirts hiding the running gear, bolted in panels
    for left in (True, False):
        x0, x1 = (0.3, 0.75) if left else (15.25, 15.7)
        for i in range(6):
            z0 = -5.8 + i * 4.6
            skirt(body, x0, x1, 1.2, 3.9, z0 + 0.05, z0 + 4.55)
            for z in (z0 + 0.6, z0 + 3.95):
                K.cylinder("x", (3.5, z), 0.13, x0 - 0.06 if left else x1, x0 if left else x1 + 0.06, "steel", n=6, m=body)
    # driver's vision port, hull machine gun ball, headlights, towing eyes
    K.rbox((5.0, 5.0, -3.9), (7.2, 5.9, -3.0), 0.15, "paint_dark", seg=1, m=body)
    K.box((5.3, 5.25, -3.92), (6.9, 5.6, -3.9), "lens", m=body, faces="z")
    for x in (2.2, 13.8):
        V.light(body, x, 4.9, -5.6, w=1.0, h=0.5)
        K.torus("z", (x, 2.4), -6.2, 0.4, 0.12, "steel", n=10, k=5, m=body)
    # engine deck: grilles, the big exhaust stacks, spare track links on the tail
    for i in range(6):
        z = 13.2 + i * 1.1
        K.box((3.6, 6.58, z), (12.4, 6.75, z + 0.6), "dark", m=body, faces="xXYzZ")
    for x in (6.0, 10.0):
        K.cylinder("y", (x, 20.6), 0.55, 6.6, 8.4, "gunmetal", n=12, m=body)
        K.cylinder("y", (x, 20.6), 0.42, 8.38, 8.42, "black", n=10, caps=(False, True), m=body)
    for i in range(5):
        K.rbox((3.2 + i * 2.0, 3.8, 22.1), (4.9 + i * 2.0, 5.2, 22.5), 0.1, "track", seg=1, m=body)
    for tx in g["trackX"]:
        track_loop(body, tx, g, 0, 0)
    # turret: a huge cast turret, round at the front, set well back on the hull; cupola, rangefinder ears
    tx, tz = g["turret"]
    top_loft(tur, K.fillet([(2.8, 6.0), (13.2, 6.0), (13.4, 16.8), (2.6, 16.8)], 3.4, seg=5), 6.5, 10.6, inset=0.45, fillet=0.0, top_bevel=0.35)
    for x0 in (2.2, 13.2):
        K.cylinder("x", (9.6, 8.6), 0.5, x0, x0 + 0.6, "paint_dark", n=12, bevel=0.1, m=tur)
        K.cylinder("x", (9.6, 8.6), 0.3, x0 - 0.02 if x0 < 8 else x0 + 0.6, x0 if x0 < 8 else x0 + 0.62, "lens", n=10, m=tur)
    sx, sy, sz = g["seat"]
    K.cylinder("y", (sx, sz), 1.15, 10.5, 11.3, "paint", n=18, bevel=0.12, m=tur)
    V.hatch(tur, sx, 11.3, sz, 0.95)
    for k in range(5):
        a = math.radians(-60 + k * 30)
        V.periscope(tur, sx + math.sin(a) * 1.05, 11.0, sz - math.cos(a) * 1.05, w=0.45, d=0.3, h=0.3)
    V.hatch(tur, 10.4, 10.6, 13.0, 0.8)
    V.antenna(tur, 11.6, 10.6, 15.6, 7.0)
    K.rbox((3.4, 6.6, 16.6), (12.6, 9.6, 17.6), 0.3, "paint_dark", seg=1, m=tur)
    # gun: 128 mm KwK 44 with the 75 mm coaxial gun beside it, in a heavy cast mantlet
    gy, gz = g["gun"]
    K.rbox((5.2, gy - 1.9, gz - 1.6), (11.4, gy + 1.7, gz + 0.8), 0.7, "paint", seg=2, m=gun)
    K.cylinder("z", (8.0, gy), 1.0, gz - 1.6, gz - 3.2, "paint", n=16, bevel=0.15, m=gun)
    V.gun_barrel(gun, 8.0, gy, g["muzzle"], gz - 3.2, 0.62, mat="paint", brake=1.4)
    cx, cy, cz = g["coax"]
    K.cylinder("z", (cx, cy), 0.42, cz - 1.0, cz - 5.2, "paint", n=12, r1=0.36, m=gun)
    K.cylinder("z", (cx, cy), 0.26, cz - 5.22, cz - 5.18, "black", n=10, caps=(True, False), m=gun)
    spots = [(v3(2.62, 8.8, 12.0), (-1, 0, 0), (0, 1, 0), 0.95), (v3(13.38, 8.8, 12.0), (1, 0, 0), (0, 1, 0), 0.95),
             (v3(0.28, 2.6, 7.0), (-1, 0, 0), (0, 1, 0), 0.8), (v3(15.72, 2.6, 7.0), (1, 0, 0), (0, 1, 0), 0.8)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(_split_marks(spots, turret_from=2))
    return parts, g


# ============================================================================================ Object 279
def obj279():
    g = dict(trackX=(2.2, 5.1, 10.9, 13.8), wheelsZ=[-1.4, 2.4, 6.2, 10.0, 13.8, 17.5], wheel=(1.75, 1.3), **_RUN,
             turret=(8.0, 9.4), gun=(6.9, 4.4), muzzle=-17.6, seat=(5.6, 7.2, 11.0), coax=(9.6, 6.6, 3.2),
             front=[(1.6, 4.2, -8.6), (14.4, 4.2, -8.6), (8.0, 4.2, -9.4)], back=[(1.6, 4.2, 23.6), (14.4, 4.2, 23.6)],
             exhaust=[(5.4, 4.4, 22.6), (10.6, 4.4, 22.6)], smoke=(8.0, 6.0, 16.0), size=(5.8, 3.2))
    GEO["obj279"] = g
    body, tur, gun = Mesh(), Mesh(), Mesh()
    # the saucer: an elongated shell over all four tracks, its lower lip at 2.4 so the tracks show beneath
    rings = []
    edge = 2.4
    for z, hw, hh in ((-9.8, 1.2, 0.3), (-9.0, 4.4, 1.1), (-7.4, 6.6, 1.8), (-4.8, 7.8, 2.3), (-1.0, 8.4, 2.6), (14.0, 8.4, 2.6),
                      (18.6, 7.9, 2.3), (21.8, 6.6, 1.8), (23.4, 4.4, 1.1), (24.2, 1.2, 0.3)):
        ring = []
        for k in range(17):
            a = math.pi * k / 16
            c, s = math.cos(a), math.sin(a)
            x = math.copysign(abs(c) ** (2 / 2.4), c) * hw
            y = abs(s) ** (2 / 2.4) * hh
            ring.append(v3(8.0 + x, edge + y, z))
        rings.append(ring)
    K.loft(rings, "paint", m=body)
    # belly between the track pairs, mud scrapers on the lip
    K.rbox((6.6, 0.9, -5.0), (9.4, 2.5, 20.0), 0.4, "paint_dark", seg=1, m=body)
    for tx in g["trackX"]:
        track_loop(body, tx, g, 0, 0)
    # driver's hatch, lights, engine louvres on the rear shell, exhausts at the tail
    V.hatch(body, 8.0, 4.95, -3.6, 0.8)
    V.periscope(body, 8.0, 4.8, -4.6, w=1.0)
    for x in (4.0, 12.0):
        V.light(body, x, 3.0, -8.1, w=0.9, h=0.45)
    for i in range(6):
        z = 14.4 + i * 1.0
        K.box((5.0, 4.95 - i * 0.12, z), (11.0, 5.08 - i * 0.12, z + 0.55), "dark", m=body, faces="xXYzZ")
    for x in (5.4, 10.6):
        K.cylinder("z", (x, 3.6), 0.45, 22.4, 23.2, "gunmetal", n=10, m=body)
    # turret: a flattened cast dome, commander's cupola, the rangefinder blister
    tx, tz = g["turret"]
    drings = []
    for y, r in ((4.6, 4.7), (5.6, 4.6), (6.6, 4.2), (7.4, 3.5), (8.0, 2.5), (8.25, 1.2)):
        drings.append([v3(tx + x, y, tz + z * 1.08) for x, z in K.circle(r, 28)])
    K.loft(drings, "paint", m=tur)
    sx, sy, sz = g["seat"]
    K.cylinder("y", (sx, sz), 1.05, 7.6, 8.5, "paint", n=16, bevel=0.12, m=tur)
    V.hatch(tur, sx, 8.5, sz, 0.85)
    K.rbox((9.6, 7.3, 6.8), (11.6, 8.5, 8.6), 0.4, "paint", seg=2, m=tur)
    K.box((10.0, 7.6, 6.78), (11.2, 8.2, 6.76), "lens", m=tur)
    V.machine_gun(tur, sx + 1.4, 8.7, sz - 1.4, length=3.0)
    V.antenna(tur, 11.2, 7.6, 13.0, 6.5)
    # gun: 130 mm M-65 with a big fume extractor
    gy, gz = g["gun"]
    K.cylinder("x", (gy, gz), 1.25, 6.2, 9.8, "paint", n=18, bevel=0.25, m=gun)
    K.cylinder("z", (8.0, gy), 0.9, gz - 1.0, gz - 2.6, "paint", n=14, bevel=0.12, m=gun)
    V.gun_barrel(gun, 8.0, gy, g["muzzle"], gz - 2.6, 0.6, sleeve=(-4.0, -11.0), evac=(-8.6, -5.8), brake=1.0)
    cx, cy, cz = g["coax"]
    K.cylinder("z", (cx, cy), 0.12, cz, cz - 0.3, "black", n=8, m=gun)
    spots = [(v3(3.45, 6.2, 9.4), (-1, 0, 0.1), (0, 1, 0), 0.8), (v3(12.55, 6.2, 9.4), (1, 0, 0.1), (0, 1, 0), 0.8),
             (v3(0.3, 3.05, 6.0), (-1, 0, 0), (0, 1, 0), 0.7), (v3(15.7, 3.05, 6.0), (1, 0, 0), (0, 1, 0), 0.7)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(_split_marks(spots, turret_from=2))
    return parts, g


# ============================================================================================ trucks
def _cab(m, z0, z1, x0=3.0, x1=13.0, y0=3.8, y1=10.6, glass_y=7.2, rake=0.6):
    """an armoured truck cab: rounded box, raked windscreen, side windows, roof hatch"""
    cx, hw = (x0 + x1) / 2, (x1 - x0) / 2
    S = section
    section_loft(m, [(z0, S(y0, hw, y0 + 2.0, y0 + 2.4, hw, y1 - 0.2, hw - 0.4, cx)),
                     (z0 + rake, S(y0, hw, y0 + 2.0, y0 + 2.4, hw, y1, hw - 0.3, cx)),
                     (z1, S(y0, hw, y0 + 2.0, y0 + 2.4, hw, y1, hw - 0.3, cx))], r=0.45, seg=2)
    K.plate([v3(x0 + 0.5, glass_y, z0 + 0.02), v3(x1 - 0.5, glass_y, z0 + 0.02), v3(x1 - 0.6, y1 - 0.45, z0 + rake - 0.05),
             v3(x0 + 0.6, y1 - 0.45, z0 + rake - 0.05)], 0.08, "glass_dark", m=m)
    K.plate([v3(cx - 0.12, glass_y, z0 - 0.02), v3(cx + 0.12, glass_y, z0 - 0.02), v3(cx + 0.12, y1 - 0.45, z0 + rake - 0.08),
             v3(cx - 0.12, y1 - 0.45, z0 + rake - 0.08)], 0.1, "paint_dark", m=m)
    for xs in (x0 - 0.02, x1 - 0.06):
        K.box((xs, glass_y + 0.2, z0 + 1.4), (xs + 0.08, y1 - 0.6, z1 - 1.0), "glass_dark", m=m, faces="xX")
    V.hatch(m, cx + 1.4, y1, (z0 + z1) / 2 + 0.6, 0.8)
    for xa, xb in ((x0 - 0.7, x0 - 0.2), (x1 + 0.2, x1 + 0.7)):
        K.rbox((xa, glass_y + 0.6, z0 + 0.6), (xb, glass_y + 2.0, z0 + 0.95), 0.12, "dark", seg=1, m=m)
    K.rbox((x0 - 0.2, y0 - 0.9, z0 - 0.7), (x1 + 0.2, y0 + 0.4, z0 + 0.2), 0.25, "paint_dark", seg=1, m=m)
    for x in (x0 + 0.4, x1 - 1.6):
        K.rbox((x, y0 + 0.9, z0 - 0.08), (x + 1.2, y0 + 1.5, z0 + 0.1), 0.1, "dark", seg=1, m=m)
        K.box((x + 0.1, y0 + 1.0, z0 - 0.1), (x + 1.1, y0 + 1.4, z0 - 0.08), "glow", m=m, faces="z")


def _truck_rear(m, z0, z1, axles, wheel_r, x0=3.0, x1=13.0, bed_y=4.6):
    K.box((5.0, 2.4, z0 - 6.0), (11.0, 3.6, z1), "dark", m=m)
    K.rbox((x0, 3.6, z0), (x1, bed_y, z1), 0.2, "paint_dark", seg=1, m=m)
    for z in axles:
        for xa, xb in ((x0 - 0.4, x0 + 1.6), (x1 - 1.6, x1 + 0.4)):
            arch(m, xa, xb, wheel_r, z, wheel_r + 0.3, wheel_r + 0.5, "paint_dark", a0=12, a1=168)
    for x in (x0 + 0.4, x1 - 1.2):
        K.box((x, 3.9, z1), (x + 0.8, 4.4, z1 + 0.15), "red_light", m=m)
    K.cylinder("z", ((x0 + x1) / 2 - 3.2, 3.1), 0.9, z0 + 1.0, z0 + 6.0, "olive", n=14, bevel=0.2, m=m)


def himars():
    axles = [-8.4, 6.6, 11.4]
    g = dict(turret=(8.0, 10.6), gun=(8.6, 15.6), muzzles=[(x, y, 1.0) for y in (9.6, 11.1) for x in (5.9, 8.0, 10.1)], tubes=[],
             coax=(8.0, 9.6, 1.0), radar=None, wheels=[(x, 2.2, z) for z in axles for x in (2.9, 13.1)], wheelR=2.2, steerAxles=1,
             seats=[(5.6, 4.6, -8.2), (10.4, 4.6, -8.2)],
             front=[(3.0, 5.0, -12.8), (13.0, 5.0, -12.8), (8.0, 5.0, -13.2)], back=[(3.0, 5.0, 16.4), (13.0, 5.0, 16.4)],
             exhaust=[(12.9, 10.8, -4.4)], smoke=(8.0, 7.0, 4.0), size=(2.9, 3.2), gunMin=-60.0, gunMax=0.0)
    GEO["himars"] = g
    body, tur, gun = Mesh(), Mesh(), Mesh()
    _cab(body, -12.4, -4.6)
    K.rbox((3.4, 3.6, -4.6), (12.6, 8.2, -2.4), 0.3, "paint", seg=1, m=body)
    K.cylinder("y", (12.9, -4.4), 0.32, 8.2, 10.8, "gunmetal", n=8, m=body)
    _truck_rear(body, -2.4, 16.2, axles, 2.2)
    # rear outrigger jacks and the launcher stowage cradle
    for x in (3.4, 12.6):
        K.cylinder("y", (x, 15.6), 0.3, 1.2, 3.6, "steel", n=8, m=body)
    K.rbox((6.6, 4.6, -1.6), (9.4, 6.2, -0.6), 0.15, "dark", seg=1, m=body)
    # launcher: turntable and the boom that lifts the pod
    tx, tz = g["turret"]
    K.cylinder("y", (tx, tz), 2.6, 4.6, 5.4, "paint_dark", n=24, bevel=0.12, m=tur)
    K.rbox((5.6, 5.4, 12.6), (10.4, 8.0, 16.6), 0.35, "paint", seg=1, m=tur)
    K.rbox((4.0, 5.4, 5.0), (4.8, 7.4, 9.4), 0.15, "paint_dark", seg=1, m=tur)
    # launch pod container: six GMLRS rockets in two rows, the caps at the front
    gy, gz = g["gun"]
    K.rbox((4.4, 8.4, 0.9), (11.6, 12.0, 16.2), 0.35, "paint", seg=2, m=gun)
    K.rbox((4.3, 8.3, 0.7), (11.7, 12.1, 1.3), 0.2, "paint_dark", seg=1, m=gun)
    for (x, y, z) in g["muzzles"]:
        K.cylinder("z", (x, y), 0.62, z - 0.08, z, "dark", n=14, caps=(True, False), m=gun)
        K.cylinder("z", (x, y), 0.45, z - 0.1, z - 0.08, "black", n=12, caps=(True, False), m=gun)
    for z in (4.0, 8.0, 12.0):
        K.box((4.32, 8.5, z), (11.68, 11.9, z + 0.25), "paint_dark", m=gun, faces="xXY")
    K.rbox((7.0, gy - 0.6, gz - 0.6), (9.0, gy + 0.2, gz + 0.6), 0.15, "steel", seg=1, m=gun)
    spots = [(v3(2.98, 8.4, -8.6), (-1, 0, 0), (0, 1, 0), 0.9), (v3(13.02, 8.4, -8.6), (1, 0, 0), (0, 1, 0), 0.9)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(marks(spots))
    return parts, g


def grad():
    axles = [-9.4, 7.8, 12.6]
    tubes = [(4.2 + c * 0.84, 9.4 + r * 0.84, 2.0) for r in range(4) for c in range(10)]
    g = dict(turret=(8.0, 12.0), gun=(8.4, 14.4), muzzles=tubes, tubes=[], coax=(8.0, 10.6, 2.0), radar=None,
             wheels=[(x, 2.4, z) for z in axles for x in (3.0, 13.0)], wheelR=2.4, steerAxles=1,
             seats=[(5.8, 5.0, -6.4), (10.2, 5.0, -6.4)],
             front=[(3.0, 5.0, -15.6), (13.0, 5.0, -15.6), (8.0, 5.0, -16.0)], back=[(3.0, 5.0, 17.4), (13.0, 5.0, 17.4)],
             exhaust=[(3.2, 4.0, -8.6)], smoke=(8.0, 7.0, 5.0), size=(2.9, 3.2), gunMin=-55.0, gunMax=0.0)
    GEO["grad"] = g
    body, tur, gun = Mesh(), Mesh(), Mesh()
    # Ural-375: long bonnet with its grille and round wings, the cab, the launcher bed
    S = section
    section_loft(body, [(-15.6, S(3.8, 3.4, 5.4, 5.8, 3.4, 8.0, 3.0)), (-14.8, S(3.8, 3.6, 5.4, 5.8, 3.6, 8.6, 3.2)),
                        (-9.8, S(3.8, 3.6, 5.4, 5.8, 3.6, 8.8, 3.2))], r=0.5)
    for i in range(7):
        K.box((5.2 + i * 0.85, 4.4, -15.68), (5.6 + i * 0.85, 7.4, -15.6), "black", m=body, faces="z")
    for x0, x1 in ((2.4, 4.6), (11.4, 13.6)):
        arch(body, x0, x1, 2.4, -9.4, 2.7, 2.95, "paint", a0=0, a1=150)
        V.light(body, (x0 + x1) / 2, 6.0, -12.2, w=0.8, h=0.6)
    _cab(body, -9.8, -3.4, x0=3.2, x1=12.8, y0=3.8, y1=10.4, glass_y=7.4, rake=0.4)
    _truck_rear(body, -3.4, 17.2, axles[1:], 2.4)
    K.rbox((3.0, 4.6, -3.0), (13.0, 8.6, -2.6), 0.15, "paint_dark", seg=1, m=body)
    K.cylinder("x", (3.4, -8.6), 0.3, 2.6, 3.4, "gunmetal", n=8, m=body)
    # launcher: turntable, the cradle frame
    tx, tz = g["turret"]
    K.cylinder("y", (tx, tz), 3.0, 4.6, 5.6, "paint_dark", n=24, bevel=0.15, m=tur)
    for x in (4.2, 11.0):
        K.rbox((x, 5.6, 11.0), (x + 0.8, 8.6, 16.6), 0.2, "paint", seg=1, m=tur)
    K.rbox((5.0, 5.6, 15.6), (11.0, 7.6, 16.8), 0.25, "paint", seg=1, m=tur)
    # forty 122 mm tubes in four rows of ten, banded together
    gy, gz = g["gun"]
    for (x, y, z) in tubes:
        K.cylinder("z", (x, y), 0.4, z, z + 14.0, "olive", n=8, caps=(False, True), m=gun)
        K.cylinder("z", (x, y), 0.3, z + 0.02, z + 0.04, "black", n=8, caps=(True, False), m=gun)
        K.torus("z", (x, y), z + 0.05, 0.4, 0.05, "olive", n=8, k=3, m=gun)
    for z in (4.0, 9.0, 14.0):
        K.rbox((3.6, 8.8, z), (12.4, 12.6, z + 0.5), 0.12, "paint_dark", seg=1, m=gun, faces="xXyYzZ")
    K.rbox((6.8, gy - 0.6, gz - 0.6), (9.2, 9.0, gz + 0.6), 0.15, "steel", seg=1, m=gun)
    spots = [(v3(3.18, 8.6, -6.6), (-1, 0, 0), (0, 1, 0), 0.9), (v3(12.82, 8.6, -6.6), (1, 0, 0), (0, 1, 0), 0.9)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(marks(spots))
    return parts, g


# ============================================================================================ C-RAM (Centurion)
def cram():
    g = dict(turret=(8.0, 6.0), gun=(9.2, 6.0), muzzles=[(8.0, 8.1, -2.2)], tubes=[], coax=(8.0, 8.1, -2.2), radar=None,
             wheels=[(x, 1.6, z) for z in (8.4, 11.8) for x in (2.6, 13.4)], wheelR=1.6, steerAxles=0,
             seats=[(12.8, 3.2, 1.0)],
             front=[(3.0, 3.0, -6.6), (13.0, 3.0, -6.6)], back=[(3.0, 3.0, 14.6), (13.0, 3.0, 14.6)],
             exhaust=[(12.6, 5.6, 13.0)], smoke=(8.0, 6.0, 10.0), size=(2.8, 3.4), gunMin=-85.0, gunMax=12.0)
    GEO["cram"] = g
    body, tur, gun = Mesh(), Mesh(), Mesh()
    # flatbed trailer, drawbar, levelling jacks, generator and fire control boxes
    K.box((5.4, 1.8, -2.0), (10.6, 2.6, 14.0), "dark", m=body)
    K.rbox((3.0, 2.6, -2.0), (13.0, 3.4, 14.4), 0.2, "paint", seg=1, m=body)
    K.tube([(6.0, 2.4, -2.0), (8.0, 2.2, -10.0), (10.0, 2.4, -2.0)], 0.22, "dark", n=6, m=body)
    K.torus("y", (8.0, -10.4), 2.2, 0.45, 0.12, "steel", n=10, k=5, m=body)
    for x, z in ((3.2, -1.6), (12.8, -1.6), (3.2, 14.0), (12.8, 14.0)):
        K.cylinder("y", (x, z), 0.25, 0.4, 2.8, "steel", n=8, m=body)
        K.cylinder("y", (x, z), 0.6, 0.0, 0.4, "dark", n=10, r1=0.35, m=body)
    for z in (8.4, 11.8):
        for xa, xb in ((2.0, 3.8), (12.2, 14.0)):
            arch(body, xa, xb, 1.6, z, 1.85, 2.05, "paint_dark", a0=20, a1=160)
    K.rbox((9.4, 3.4, 9.6), (12.8, 6.4, 14.0), 0.35, "paint", seg=2, m=body)
    for i in range(5):
        K.box((12.78, 3.9, 10.1 + i * 0.75), (12.88, 6.0, 10.5 + i * 0.75), "dark", m=body)
    K.rbox((3.2, 3.4, 10.0), (6.6, 5.6, 14.0), 0.3, "olive", seg=1, m=body)
    K.cylinder("y", (12.6, 13.0), 0.18, 6.4, 7.2, "gunmetal", n=8, m=body)
    # base and the training mount
    tx, tz = g["turret"]
    K.cylinder("y", (tx, tz), 2.6, 3.4, 4.4, "paint", n=24, bevel=0.15, m=tur)
    K.cylinder("y", (tx, tz), 2.1, 4.4, 6.6, "paint", n=24, r1=1.7, m=tur)
    for x0 in (5.4, 10.0):
        K.rbox((x0, 6.4, tz - 1.2), (x0 + 0.6, 9.8, tz + 1.2), 0.2, "paint", seg=1, m=tur)
    # elevating mass: the white radome, ammunition drum, the six-barrel 20 mm gatling under it
    gy, gz = g["gun"]
    K.cylinder("y", (8.0, gz + 0.2), 1.65, gy + 0.9, gy + 4.4, "white", n=24, bevel=0.15, m=gun)
    K.sphere((8.0, gy + 4.4, gz + 0.2), 1.65, 1.5, 1.65, "white", n=24, rows=8, top_only=True, m=gun)
    K.torus("y", (8.0, gz + 0.2), gy + 2.0, 1.66, 0.06, "light_grey", n=24, k=3, m=gun)
    K.cylinder("x", (gy - 0.6, gz + 1.2), 1.5, 6.6, 9.4, "light_grey", n=18, bevel=0.2, m=gun)
    K.rbox((6.9, gy - 1.0, gz - 1.6), (9.1, gy + 0.6, gz + 0.4), 0.3, "light_grey", seg=1, m=gun)
    mx, my, mz = g["muzzles"][0]
    for k in range(6):
        a = math.radians(k * 60)
        bx, by = mx + math.cos(a) * 0.32, my + math.sin(a) * 0.32
        K.cylinder("z", (bx, by), 0.09, mz, gz - 1.6, "gunmetal", n=6, caps=(True, False), m=gun)
    for z in (mz + 0.3, mz + 3.0):
        K.cylinder("z", (mx, my), 0.48, z, z + 0.35, "gunmetal", n=12, bevel=0.05, m=gun)
    K.cylinder("z", (mx, my), 0.6, gz - 1.6, gz - 2.4, "light_grey", n=12, bevel=0.1, m=gun)
    spots = [(v3(2.98, 3.0, 4.0), (-1, 0, 0), (0, 1, 0), 0.5), (v3(13.02, 3.0, 4.0), (1, 0, 0), (0, 1, 0), 0.5)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(marks(spots))
    return parts, g


TANKS2 = {"maus": maus, "obj279": obj279}
CARS2 = {"himars": himars, "grad": grad, "cram": cram}
