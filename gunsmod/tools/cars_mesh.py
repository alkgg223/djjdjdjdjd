"""Smooth meshes for the wheeled vehicles: BTR-82A, Pantsir-S1, the 8x8 wheel, the Humvee (jeep units, 0.1 block) and
its TOW launcher. The points the game uses (turret ring, trunnions, muzzles, wheels, seats) come from armor3.py and
vehicle.py, so the physics and the effects stay where they were."""
import math

import numpy as np

import armor3 as A3
import meshkit as K
import vehicle as VJ
import vehmesh as V
from meshkit import Mesh, v3
from tanks_mesh import side_prism, top_loft


# ------------------------------------------------------------------------------------------------ helpers
def section(bot, bw, f0, f1, fw, top, rw, cx=8.0):
    """a hull cross section (x, y): narrow belly, wide fender band f0..f1, sides leaning in to a roof of half width rw"""
    return [(cx - bw, bot), (cx + bw, bot), (cx + fw, f0), (cx + fw, f1), (cx + rw, top), (cx - rw, top), (cx - fw, f1), (cx - fw, f0)]


def section_loft(m, stations, mat="paint", r=0.35, seg=2):
    """loft a hull through (z, polygon (x, y)) stations; every polygon has the same corners, all rounded"""
    rings = []
    for z, poly in stations:
        rings.append([v3(x, y, z) for x, y in K.fillet(poly, r, seg=seg)])
    return K.loft(rings, mat, m=m)


def lathe(m, profile, mat, n=20, cy=8.0, cz=8.0, caps=(False, False)):
    """a solid of revolution about the X axis through (cy, cz): profile = [(x, radius)]"""
    rings = []
    for x, r in profile:
        r = max(r, 1e-4)
        rings.append([v3(x, cy + r * math.sin(2 * math.pi * k / n), cz + r * math.cos(2 * math.pi * k / n)) for k in range(n)])
    return K.loft(rings, mat, caps=caps, m=m)


def arch(m, x0, x1, cy, cz, r_in, r_out, mat="paint", a0=8.0, a1=172.0, n=10):
    """a curved mudguard over a wheel (axle along X at y = cy, z = cz), from angle a0 to a1 measured from the front"""
    outer, inner = [], []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        outer.append((cz - r_out * math.cos(a), cy + r_out * math.sin(a)))
        inner.append((cz - r_in * math.cos(a), cy + r_in * math.sin(a)))
    return K.prism(outer + inner[::-1], "x", x0, x1, mat, m=m)


def louvres(m, x0, x1, y, z0, z1, n, mat="dark", w=0.45):
    step = (z1 - z0) / n
    for i in range(n):
        z = z0 + i * step + (step - w) / 2
        K.box((x0, y, z), (x1, y + 0.12, z + w), mat, m=m, faces="xXYzZ")


def marks(spots):
    return {"mark_green": V.team_marks(spots, True), "mark_red": V.team_marks(spots, False)}


# ================================================================================================ 8x8 wheel
def car_wheel():
    """off-road tyre centred on (8, 8, 8), axle along X, radius 2.4; the hub face is on -X (the renderer turns the right
    hand wheels round)"""
    m = Mesh()
    lathe(m, [(7.15, 1.55), (7.18, 2.05), (7.3, 2.3), (7.5, 2.4), (8.5, 2.4), (8.7, 2.3), (8.82, 2.05), (8.85, 1.55)], "rubber", n=22)
    # tread blocks staggered across the crown
    for i in range(18):
        a = i * 20.0
        for xs in ((7.35, 7.95), (8.05, 8.65)):
            off = 10.0 if xs[0] > 8 else 0.0
            lug = K.box((xs[0], 10.36, 7.78), (xs[1], 10.52, 8.22), "rubber")
            lug.rotate("x", a + off, (8, 8, 8))
            m.extend(lug)
    # rim, hub and the tyre pressure line on the outside face
    lathe(m, [(7.16, 1.55), (7.0, 1.45), (6.98, 1.0), (6.95, 0.62), (6.75, 0.55), (6.62, 0.3), (6.6, 0.0)], "olive", n=18)
    lathe(m, [(8.84, 1.55), (9.0, 1.4), (9.02, 0.6), (9.05, 0.0)], "olive", n=14)
    for k in range(8):
        a = math.radians(k * 45 + 22.5)
        K.cylinder("x", (8 + math.sin(a) * 0.85, 8 + math.cos(a) * 0.85), 0.11, 6.92, 6.99, "steel", n=6, caps=(True, False), m=m)
    K.tube([(6.85, 8.0 + 0.6, 8.0), (6.95, 8.0 + 0.9, 8.3), (7.0, 9.2, 8.7)], 0.05, "dark", n=4, m=m)
    return m


# ================================================================================================ BTR-82A
def btr82():
    g = A3.GEO.get("btr82") or (A3.btr82(), A3.GEO["btr82"])[1]
    body, tur, gun = Mesh(), Mesh(), Mesh()
    # boat hull: narrow belly, the fender band over the wheels, sides leaning in to the roof, the sharp bow
    S = section
    section_loft(body, [
        (-9.1, S(5.25, 4.4, 5.35, 5.5, 4.9, 5.75, 4.5)),
        (-8.4, S(4.4, 4.4, 5.0, 5.9, 5.3, 6.15, 4.6)),
        (-6.6, S(2.2, 4.4, 5.0, 6.4, 5.8, 6.95, 4.8)),
        (-4.4, S(1.9, 4.4, 5.0, 6.4, 5.8, 8.25, 4.8)),
        (19.6, S(1.9, 4.4, 5.0, 6.4, 5.8, 8.25, 4.8)),
        (20.8, S(2.4, 4.2, 5.0, 6.4, 5.7, 8.05, 4.6)),
        (21.3, S(3.0, 3.8, 5.1, 6.3, 5.4, 7.6, 4.2)),
    ], r=0.32)
    # trim vane folded on the bow, headlights in their guards
    K.plate([v3(3.6, 6.05, -8.0), v3(12.4, 6.05, -8.0), v3(12.4, 7.25, -5.6), v3(3.6, 7.25, -5.6)], 0.25, "paint_dark", bevel=0.06, m=body)
    for x in (3.9, 11.7):
        K.rbox((x - 0.05, 5.6, -9.05), (x + 1.15, 6.15, -8.55), 0.12, "dark", seg=1, m=body)
        K.box((x + 0.05, 5.7, -9.1), (x + 1.05, 6.05, -9.06), "glow", m=body, faces="z")
        K.tube([(x - 0.1, 5.6, -9.2), (x - 0.1, 6.4, -9.2), (x + 1.2, 6.4, -9.2), (x + 1.2, 5.6, -9.2)], 0.06, "dark", n=4, caps=(True, True), m=body)
    # driver and commander vision blocks, the front hatches
    for x in (4.6, 5.6, 9.8, 10.8):
        K.plate([v3(x, 7.6, -4.55), v3(x + 0.8, 7.6, -4.55), v3(x + 0.8, 8.05, -4.3), v3(x, 8.05, -4.3)], 0.12, "lens", m=body)
    V.hatch(body, 5.5, 8.25, -2.4, 0.95)
    V.hatch(body, 10.5, 8.25, -2.4, 0.95)
    # turret ring collar
    K.cylinder("y", g["turret"], 2.95, 8.2, 8.5, "paint_dark", n=24, bevel=0.08, m=body)
    # wheel arches over every wheel, the side doors and the firing ports
    for z in [w[2] for w in g["wheels"][::2]]:
        for xa, xb in ((1.95, 3.7), (12.3, 14.05)):
            arch(body, xa, xb, 2.4, z, 2.75, 3.0, "paint_mid")
    for x0, x1, sgn in ((2.15, 2.25, -1), (13.75, 13.85, 1)):
        K.rbox((x0 - 0.02, 5.1, 5.6), (x1 + 0.02, 6.3, 7.6), 0.08, "paint_mid", seg=1, m=body)
        for z in (-0.6, 11.8, 17.6):
            K.cylinder("x", (5.8, z + 0.2), 0.22, x0 - 0.05, x1 + 0.05, "black", n=8, m=body)
    # engine deck grilles, the exhaust box on the right, the water jet cover at the stern, tail lights
    louvres(body, 4.2, 11.8, 8.18, 13.4, 20.0, 5)
    K.rbox((12.8, 5.6, 17.6), (14.2, 6.6, 20.0), 0.2, "dark", seg=1, m=body)
    K.cylinder("x", (6.1, 18.8), 0.25, 14.2, 14.5, "black", n=8, m=body)
    K.rbox((6.0, 2.6, 20.9), (10.0, 4.6, 21.5), 0.25, "paint_dark", seg=1, m=body)
    for x in (3.4, 11.8):
        K.box((x, 6.0, 21.0), (x + 0.8, 6.5, 21.25), "red_light", m=body)
    for x in (4.4, 11.0):
        K.torus("z", (x + 0.3, 3.3), -8.2, 0.35, 0.1, "steel", n=10, k=5, m=body)
    # stowage: spare parts box, unditching log, antenna
    K.rbox((13.6, 6.1, 1.0), (14.1, 7.0, 4.6), 0.12, "olive", seg=1, m=body)
    K.cylinder("z", (2.0, 6.7), 0.45, 9.0, 16.0, "sand", n=10, bevel=0.1, m=body)
    for z in (10.0, 15.0):
        K.torus("z", (2.0, 6.7), z, 0.5, 0.05, "dark", n=10, k=4, m=body)
    V.antenna(body, 4.2, 8.25, 19.6, 7.0)
    # BPPU turret: low cast turret with the sight on the left, smoke launchers
    tx, tz = g["turret"]
    K.cylinder("y", (tx, tz), 2.7, 8.4, 9.0, "paint", n=24, bevel=0.15, m=tur)
    side_prism(tur, [(-0.9, 8.9), (5.3, 8.9), (5.3, 10.4), (4.8, 10.6), (0.4, 10.6), (-0.9, 9.6)], 5.2, 10.8, fillet=0.25, bevel=0.2)
    K.rbox((4.2, 9.6, 0.6), (5.4, 11.0, 2.8), 0.2, "paint_dark", seg=1, m=tur)
    K.box((4.35, 10.0, 0.58), (5.25, 10.7, 0.55), "lens", m=tur)
    sx, sy, sz = g["seats"][0]
    K.cylinder("y", (sx, sz), 0.95, 10.5, 11.0, "paint", n=16, bevel=0.1, m=tur)
    V.hatch(tur, sx, 11.0, sz, 0.8)
    V.periscope(tur, sx - 0.5, 10.95, sz - 0.9)
    V.smoke_launchers(tur, 4.4, 9.4, 2.8, 3, side=-1)
    V.smoke_launchers(tur, 11.6, 9.4, 2.8, 3, side=1)
    V.antenna(tur, 10.0, 10.6, 4.6, 5.0)
    # 2A72 30 mm cannon: thin long barrel, the PKTM coaxial gun on the right
    gy, gz = g["gun"]
    K.rbox((6.6, gy - 0.6, -0.6), (9.8, gy + 0.7, 1.4), 0.25, "paint", seg=1, m=gun)
    K.cylinder("z", (7.4, gy), 0.34, -2.6, -0.6, "paint", n=12, bevel=0.06, m=gun)
    K.cylinder("z", (7.4, gy), 0.17, -12.2, -2.6, "gunmetal", n=10, m=gun)
    K.cylinder("z", (7.4, gy), 0.26, -12.9, -12.2, "gunmetal", n=10, bevel=0.05, m=gun)
    K.cylinder("z", (7.4, gy), 0.12, -12.92, -12.88, "black", n=8, caps=(True, False), m=gun)
    cx, cy, cz = g["coax"]
    K.cylinder("z", (cx, cy), 0.12, cz, -0.6, "gunmetal", n=8, m=gun)
    spots = [(v3(2.18, 5.7, 13.8), (-1, 0, 0), (0, 1, 0), 0.6), (v3(13.82, 5.7, 13.8), (1, 0, 0), (0, 1, 0), 0.6),
             (v3(8.0, 8.27, 9.0), (0, 1, 0), (0, 0, -1), 1.1)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(marks(spots))
    return parts, g


# ================================================================================================ Pantsir-S1
def pantsir():
    g = A3.GEO.get("pantsir") or (A3.pantsir(), A3.GEO["pantsir"])[1]
    body, tur, gun, radar = Mesh(), Mesh(), Mesh(), Mesh()
    # chassis frame, the platform and the mudguards over the four axles
    K.box((5.0, 3.4, -14.0), (11.0, 4.6, 30.6), "dark", m=body)
    K.rbox((3.0, 4.6, 1.6), (13.0, 6.2, 30.8), 0.25, "paint", seg=1, m=body)
    for z in [w[2] for w in g["wheels"][::2]]:
        for xa, xb in ((2.6, 5.0), (11.0, 13.4)):
            arch(body, xa, xb, 2.6, z, 2.9, 3.15, "paint_dark", a0=14, a1=166)
    # KAMAZ cab over the engine: raked windscreen, grille, bumper, doors and windows, mirrors, beacon
    S = section
    section_loft(body, [
        (-15.0, S(4.6, 5.0, 8.0, 8.4, 5.0, 12.2, 4.5)),
        (-14.6, S(4.6, 5.0, 8.0, 8.4, 5.0, 12.9, 4.8)),
        (-7.4, S(4.6, 5.0, 8.0, 8.4, 5.0, 13.0, 4.8)),
    ], r=0.5, seg=2)
    K.plate([v3(3.5, 8.7, -15.0), v3(12.5, 8.7, -15.0), v3(12.5, 12.15, -14.62), v3(3.5, 12.15, -14.62)], 0.08, "glass_dark", m=body)
    K.plate([v3(7.9, 8.7, -15.04), v3(8.1, 8.7, -15.04), v3(8.1, 12.15, -14.66), v3(7.9, 12.15, -14.66)], 0.1, "dark", m=body)
    K.rbox((4.0, 5.6, -15.15), (12.0, 8.0, -14.9), 0.15, "dark", seg=1, m=body)
    for i in range(9):
        x = 4.4 + i * 0.85
        K.box((x, 5.8, -15.2), (x + 0.4, 7.8, -15.15), "black", m=body, faces="z")
    K.rbox((2.8, 3.6, -15.6), (13.2, 4.8, -14.8), 0.2, "paint_dark", seg=1, m=body)
    for x in (3.3, 11.5):
        K.rbox((x, 4.95, -15.2), (x + 1.2, 5.5, -14.95), 0.1, "dark", seg=1, m=body)
        K.box((x + 0.1, 5.05, -15.22), (x + 1.1, 5.4, -15.2), "glow", m=body, faces="z")
    for x0, x1 in ((2.95, 3.0), (13.0, 13.05)):
        K.box((x0, 9.0, -13.6), (x1, 12.0, -10.4), "glass_dark", m=body, faces="xX")
        K.box((x0, 5.2, -13.8), (x1, 8.6, -10.2), "paint_dark", m=body, faces="xX")
    for xa, xb in ((1.8, 2.3), (13.7, 14.2)):
        K.rbox((xa, 9.4, -14.4), (xb, 11.4, -14.0), 0.12, "dark", seg=1, m=body)
        K.tube([((xa + xb) / 2, 10.3, -14.2), (3.0 if xa < 8 else 13.0, 10.3, -14.2)], 0.07, "steel", n=5, m=body)
    K.rbox((7.0, 13.0, -12.4), (9.0, 13.5, -11.6), 0.15, "amber", seg=1, m=body)
    # power unit housing behind the cab, exhaust stack, fuel tanks, outriggers, tail lights, rear equipment box
    K.rbox((3.4, 4.6, -7.4), (12.6, 10.6, 1.6), 0.4, "paint", seg=2, m=body)
    for x0 in (3.3, 12.58):
        for i in range(6):
            z = -6.8 + i * 1.25
            K.box((x0, 7.2, z), (x0 + 0.12, 9.8, z + 0.6), "dark", m=body)
    K.cylinder("y", (12.8, -6.4), 0.36, 10.6, 13.4, "steel", n=10, m=body)
    K.cylinder("y", (12.8, -6.4), 0.42, 12.4, 12.8, "dark", n=10, m=body)
    for x0, x1 in ((2.6, 4.8), (11.2, 13.4)):
        K.cylinder("z", ((x0 + x1) / 2, 3.8), 1.0, 0.6, 9.8, "olive", n=14, bevel=0.2, m=body)
        for z in (2.6, 7.8):
            K.torus("z", ((x0 + x1) / 2, 3.8), z, 1.02, 0.07, "dark", n=14, k=4, m=body)
    for x in (2.4, 12.8):
        for z in (6.0, 29.4):
            K.cylinder("y", (x + 0.4, z + 0.4), 0.35, 1.0, 4.8, "steel", n=8, m=body)
            K.cylinder("y", (x + 0.4, z + 0.4), 0.75, 0.0, 1.0, "dark", n=10, r1=0.45, m=body)
    for x in (3.4, 11.8):
        K.box((x, 4.8, 30.8), (x + 0.8, 5.4, 31.0), "red_light", m=body)
    K.rbox((3.2, 6.2, 24.4), (12.8, 7.6, 30.4), 0.25, "paint_dark", seg=1, m=body)
    # combat module: turntable, the crew cabin, tracking radar on the front, electro-optics below it
    tx, tz = g["turret"]
    K.cylinder("y", (tx, tz), 4.4, 6.2, 6.8, "paint_dark", n=28, bevel=0.15, m=tur)
    side_prism(tur, [(12.4, 6.8), (23.6, 6.8), (23.6, 12.4), (14.0, 12.4), (12.4, 9.6)], 4.6, 11.4, fillet=0.35, bevel=0.3)
    K.cylinder("z", (8.0, 11.0), 2.5, 12.6, 13.4, "paint", n=28, bevel=0.12, m=tur)
    K.cylinder("z", (8.0, 11.0), 2.2, 12.5, 12.6, "dark", n=28, caps=(True, False), m=tur)
    for i in range(5):
        y = 9.6 + i * 0.6
        hw = math.sqrt(max(2.1 ** 2 - (y - 11.0) ** 2, 0.1))
        K.box((8.0 - hw, y, 12.42), (8.0 + hw, y + 0.08, 12.5), "steel", m=tur, faces="z")
    K.rbox((6.8, 7.2, 12.2), (9.2, 8.6, 13.6), 0.2, "dark", seg=1, m=tur)
    K.sphere((7.4, 7.9, 12.2), 0.4, 0.4, 0.15, "lens", n=10, rows=4, m=tur)
    K.sphere((8.6, 7.9, 12.2), 0.4, 0.4, 0.15, "red", n=10, rows=4, m=tur)
    sx, sy, sz = g["seats"][0]
    K.cylinder("y", (sx, sz), 0.95, 12.4, 12.8, "paint", n=16, bevel=0.08, m=tur)
    V.hatch(tur, sx, 12.8, sz, 0.8)
    for x0 in (4.35, 11.45):
        K.rbox((x0, 7.6, 15.0), (x0 + 0.2, 11.6, 21.6), 0.08, "dark", seg=1, m=tur)
    rx, ry, rz = g["radar"]
    K.cylinder("y", (rx, rz), 0.7, 12.4, 13.0, "steel", n=12, m=tur)
    # search radar: a planar array on its mast, spinning as one part about (8, 8, 8)
    K.cylinder("y", (8, 8), 0.45, 8.0, 9.2, "steel", n=10, m=radar)
    arr = Mesh()
    K.rbox((4.2, 9.0, 7.6), (11.8, 12.4, 8.0), 0.15, "paint", seg=1, m=arr)
    K.box((4.5, 9.3, 7.55), (11.5, 12.1, 7.6), "dark", m=arr, faces="z")
    for i in range(9):
        x = 4.9 + i * 0.775
        K.box((x, 9.35, 7.5), (x + 0.08, 12.05, 7.55), "steel", m=arr, faces="z")
    arr.rotate("x", 22.5, (8, 10.7, 7.8))
    radar.extend(arr)
    K.rbox((7.6, 8.8, 7.8), (8.4, 9.6, 8.8), 0.1, "steel", seg=1, m=radar)
    # weapon blocks either side: six missile containers over a 2A38M twin gun, elevating together
    gy, gz = g["gun"]
    for x0, x1 in ((1.6, 4.4), (11.6, 14.4)):
        K.rbox((x0 + 0.4, gy - 0.8, gz - 1.2), (x1 - 0.4, gy + 0.8, gz + 1.2), 0.2, "paint_dark", seg=1, m=gun)
        for (tx_, ty_, tz_) in g["tubes"]:
            if not (x0 <= tx_ <= x1):
                continue
            K.cylinder("z", (tx_, ty_), 0.42, tz_, tz_ + 11.0, "olive", n=10, bevel=0.06, m=gun)
            K.cylinder("z", (tx_, ty_), 0.3, tz_ - 0.02, tz_ + 0.1, "white", n=8, caps=(True, False), m=gun)
        K.rbox((x0, 9.4, 12.6), (x1, 9.6, 23.4), 0.06, "dark", seg=1, m=gun)
        K.rbox((x0 + 0.4, 7.8, 13.0), (x1 - 0.4, 9.4, 19.0), 0.2, "paint", seg=1, m=gun)
        for (mx, my, mz) in g["muzzles"]:
            if not (x0 <= mx <= x1):
                continue
            K.cylinder("z", (mx, my), 0.22, mz + 1.2, 13.0, "gunmetal", n=10, m=gun)
            K.cylinder("z", (mx, my), 0.32, mz, mz + 1.2, "gunmetal", n=10, bevel=0.05, m=gun)
            K.cylinder("z", (mx, my), 0.13, mz - 0.02, mz + 0.05, "black", n=8, caps=(True, False), m=gun)
    for x0 in (4.4, 11.4):
        K.cylinder("x", (gy, gz), 0.3, x0, x0 + 0.2, "steel", n=10, m=gun)
    spots = [(v3(2.92, 10.5, -12.0), (-1, 0, 0), (0, 1, 0), 0.9), (v3(13.08, 10.5, -12.0), (1, 0, 0), (0, 1, 0), 0.9),
             (v3(8.0, 13.02, -10.0), (0, 1, 0), (0, 0, -1), 1.2)]
    parts = {"body": body, "turret": tur, "gun": gun, "radar": radar}
    parts.update(marks(spots))
    return parts, g


# ================================================================================================ Humvee (0.1 block units)
def jeep():
    body = Mesh()
    # frame rails and the engine block under the hood
    K.box((1.0, 2.0, -13.0), (15.0, 3.2, 29.0), "dark", m=body)
    K.rbox((1.5, 3.0, -14.0), (14.5, 9.0, -3.0), 0.5, "paint_dark", seg=1, m=body)
    # hood and front fenders as one wide, gently domed panel; sloped nose
    hood = []
    for z, top, front in ((-14.6, 9.9, True), (-13.8, 10.7, False), (-3.0, 11.2, False)):
        hood.append((z, [(-3.0, 7.6), (19.0, 7.6), (19.0, top - 0.4), (17.2, top - 0.2), (16.4, top + 0.4), (-0.4, top + 0.4),
                         (-1.2, top - 0.2), (-3.0, top - 0.4)]))
    section_loft(body, hood, r=0.45, seg=2)
    louvres(body, 1.0, 15.0, 11.55, -12.8, -4.4, 7, mat="paint_dark", w=0.4)
    # cabin tub, doors with the window frames, pillars and roof round the turret ring
    K.rbox((-1.5, 3.0, -3.0), (17.5, 10.6, 16.6), 0.6, "paint", seg=2, m=body)
    for x0, x1 in ((-2.6, -1.4), (17.4, 18.6)):
        for z0, z1 in ((-2.8, 7.4), (7.6, 16.4)):
            K.rbox((x0, 4.0, z0), (x1, 10.7, z1), 0.35, "paint", seg=2, m=body)
            hx = x0 - 0.05 if x0 < 0 else x1 - 0.25
            K.rbox((hx, 8.6, z0 + 6.4), (hx + 0.3, 9.0, z0 + 8.0), 0.1, "dark", seg=1, m=body)
            K.box((x0 + 0.2, 10.75, z0 + 0.6), (x1 - 0.2, 17.8, z1 - 0.6), "glass_dark", m=body, faces="xX")
            for zz in (z0 + 0.2, z1 - 0.6):
                K.rbox((x0 + 0.1, 10.6, zz), (x1 - 0.1, 18.7, zz + 0.4), 0.12, "paint", seg=1, m=body)
            K.rbox((x0 + 0.1, 17.8, z0 + 0.2), (x1 - 0.1, 18.7, z1 - 0.2), 0.15, "paint", seg=1, m=body)
    for x0, x1 in ((-1.6, -0.6), (7.5, 8.5), (16.6, 17.6)):
        K.rbox((x0, 10.5, -1.4), (x1, 18.6, -0.4), 0.25, "paint", seg=1, m=body)
    K.box((-0.6, 10.8, -1.0), (7.5, 18.4, -0.8), "glass_dark", m=body, faces="zZ")
    K.box((8.5, 10.8, -1.0), (16.6, 18.4, -0.8), "glass_dark", m=body, faces="zZ")
    for c0, c1 in (((-2.8, 18.6, -1.6), (18.8, 19.4, 8.8)), ((-2.8, 18.6, 8.6), (3.6, 19.4, 17.2)),
                   ((12.4, 18.6, 8.6), (18.8, 19.4, 17.2)), ((3.4, 18.6, 16.6), (12.6, 19.4, 17.2))):
        K.rbox(c0, c1, 0.3, "paint", seg=1, m=body)
    # mirrors on their arms
    for x0, x1, xa in ((-4.6, -2.8, -2.6), (18.8, 20.6, 18.6)):
        K.rbox((x0, 12.0, -1.8), (x1, 14.0, -1.0), 0.2, "dark", seg=1, m=body)
        K.tube([((x0 + x1) / 2, 13.0, -1.2), (xa, 13.0, -1.2)], 0.12, "dark", n=5, m=body)
    # seats, steering wheel, dashboard, centre console, the gunner's step
    K.rbox((-1.4, 10.5, -0.6), (17.4, 12.2, 1.6), 0.3, "dark", seg=1, m=body)
    K.box((0.6, 11.2, 1.55), (4.6, 12.6, 1.6), "glow", m=body, faces="Z")
    for sx in (2.6, 13.4):
        for z0 in (2.4, 10.4):
            K.rbox((sx - 2.4, 3.0, z0), (sx + 2.4, 5.6, z0 + 4.6), 0.5, "canvas", seg=1, m=body)
            K.rbox((sx - 2.4, 5.6, z0 + 3.8), (sx + 2.4, 12.0, z0 + 4.8), 0.4, "canvas", seg=1, m=body)
    K.torus("z", (2.6, 13.2), 1.8, 1.45, 0.18, "rubber", n=16, k=5, m=body)
    K.tube([(2.6, 11.4, 2.6), (2.6, 13.2, 1.8)], 0.22, "dark", n=6, m=body)
    K.rbox((5.4, 3.0, -0.5), (10.6, 8.0, 7.0), 0.4, "dark", seg=1, m=body)
    K.rbox((6.0, 3.0, 9.0), (10.0, 11.0, 13.0), 0.3, "grey", seg=1, m=body)
    # rear: bed with the canvas cover, tailgate, lights, jerry cans, whip antennas
    K.rbox((-3.0, 3.4, 16.6), (19.0, 10.9, 29.4), 0.5, "paint", seg=2, m=body)
    K.rbox((-2.8, 10.9, 17.2), (18.8, 14.6, 29.2), 1.0, "canvas_green", seg=2, m=body)
    for z in (19.0, 23.0, 27.0):
        K.box((-2.85, 10.9, z), (18.85, 14.0, z + 0.3), "canvas", m=body, faces="xX")
    for x in (-2.4, 16.8):
        K.box((x, 6.8, 29.4), (x + 1.6, 8.0, 29.6), "red_light", m=body, faces="Z")
    for x in (2.0, 11.0):
        K.rbox((x, 3.4, 29.4), (x + 3.0, 9.4, 31.4), 0.4, "olive", seg=1, m=body)
        K.cylinder("y", (x + 0.9, 30.4), 0.35, 9.4, 10.0, "olive", n=8, m=body)
    for x in (-1.6, 17.6):
        K.cylinder("y", (x, 28.0), 0.45, 10.9, 12.0, "dark", n=8, m=body)
        K.tube([(x, 12.0, 28.0), (x, 31.5, 28.0)], 0.1, "rubber", n=4, m=body)
    # front: grille slots, bumper, winch, tow hooks, lights with brush guards
    K.rbox((1.5, 4.0, -14.3), (14.5, 9.0, -13.9), 0.2, "dark", seg=1, m=body)
    for i in range(8):
        x = 2.4 + i * 1.6
        K.box((x, 4.5, -14.36), (x + 0.6, 8.5, -14.3), "black", m=body, faces="z")
    K.rbox((-3.2, 3.2, -15.4), (19.2, 5.4, -14.2), 0.4, "paint_dark", seg=1, m=body)
    K.rbox((5.5, 3.6, -15.9), (10.5, 5.0, -15.4), 0.3, "steel", seg=1, m=body)
    for x in (1.0, 15.0):
        K.torus("z", (x, 4.3), -15.8, 0.5, 0.15, "steel", n=10, k=5, m=body)
    for x in (-2.2, 15.6):
        K.rbox((x, 7.6, -14.6), (x + 2.6, 8.9, -14.1), 0.3, "steel", seg=1, m=body)
        K.box((x + 0.4, 7.85, -14.65), (x + 2.2, 8.65, -14.6), "glow", m=body, faces="z")
    spots = [(v3(-2.65, 7.4, 2.3), (-1, 0, 0), (0, 1, 0), 2.0), (v3(18.65, 7.4, 2.3), (1, 0, 0), (0, 1, 0), 2.0),
             (v3(8.0, 11.66, -8.6), (0, 1, 0), (0, 0, -1), 2.6)]
    parts = {"body": body}
    parts.update(marks(spots))
    parts["wheel"] = jeep_wheel()
    parts["turret"] = jeep_turret()
    parts["gun"] = jeep_gun()
    return parts


def jeep_wheel():
    """run-flat tyre on a beadlock rim, centred on (8, 8, 8), axle along X, radius 4.3; both faces dressed"""
    m = Mesh()
    lathe(m, [(6.35, 2.9), (6.38, 3.6), (6.55, 4.05), (6.9, 4.3), (9.1, 4.3), (9.45, 4.05), (9.62, 3.6), (9.65, 2.9)], "rubber", n=24)
    for i in range(16):
        for xs, off in (((6.6, 7.9), 0.0), ((8.1, 9.4), 11.25)):
            lug = K.box((xs[0], 12.26, 7.55), (xs[1], 12.55, 8.45), "rubber")
            lug.rotate("x", i * 22.5 + off, (8, 8, 8))
            m.extend(lug)
    for s in (-1, 1):
        x_t, x_r = (6.36, 6.1) if s < 0 else (9.64, 9.9)
        lathe(m, [(x_t, 2.9), (x_r, 2.7), (x_r, 1.4), (x_r + s * 0.15, 1.2), (x_r + s * 0.3, 0.9), (x_r + s * 0.35, 0.0)], "olive", n=18)
        for k in range(8):
            a = math.radians(k * 45)
            K.cylinder("x", (8 + math.sin(a) * 2.2, 8 + math.cos(a) * 2.2), 0.17, x_r, x_r + s * 0.12, "steel", n=6, caps=(False, True), m=m)
        for k in range(6):
            a = math.radians(k * 60 + 30)
            K.cylinder("x", (8 + math.sin(a) * 0.6, 8 + math.cos(a) * 0.6), 0.12, x_r + s * 0.3, x_r + s * 0.42, "bright", n=6, caps=(False, True), m=m)
    return m


def jeep_turret():
    m = Mesh()
    cx, cy, cz = VJ.TURRET_PIVOT
    K.cylinder("y", (cx, cz), 4.4, cy, cy + 1.2, "paint_dark", n=28, bevel=0.2, m=m)
    K.cylinder("y", (cx, cz), 3.8, cy + 1.2, cy + 1.6, "steel", n=28, m=m)
    # curved gun shield round the front of the ring with a slot for the gun, angled wings either side
    outer, inner = [], []
    for k in range(13):
        a = math.radians(-60 + 120 * k / 12)
        outer.append((cx + 5.6 * math.sin(a), cz - 5.6 * math.cos(a)))
        inner.append((cx + 5.0 * math.sin(a), cz - 5.0 * math.cos(a)))
    shield = outer + inner[::-1]
    K.prism(shield, "y", cy + 1.2, cy + 4.4, "paint", m=m)
    K.prism(shield, "y", cy + 5.6, cy + 6.4, "paint", m=m)
    for k in list(range(0, 5)) + list(range(8, 13)):
        a0, a1 = math.radians(-60 + 10 * k), math.radians(-60 + 10 * (k + 1))
        if k == 12:
            continue
        seg = [(cx + 5.6 * math.sin(a0), cz - 5.6 * math.cos(a0)), (cx + 5.6 * math.sin(a1), cz - 5.6 * math.cos(a1)),
               (cx + 5.0 * math.sin(a1), cz - 5.0 * math.cos(a1)), (cx + 5.0 * math.sin(a0), cz - 5.0 * math.cos(a0))]
        K.prism(seg, "y", cy + 4.4, cy + 5.6, "paint", m=m)
    for s in (-1, 1):
        a = math.radians(60 * s)
        p0 = (cx + 5.3 * math.sin(a), cz - 5.3 * math.cos(a))
        wing = Mesh()
        K.rbox((p0[0] - 0.3, cy + 1.2, p0[1]), (p0[0] + 0.3, cy + 5.8, p0[1] + 3.2), 0.15, "paint", seg=1, m=wing)
        wing.rotate("y", -30 * s, (p0[0], 0, p0[1]))
        m.extend(wing)
    gz = VJ.GUN_PIVOT[2]
    K.cylinder("y", (8.0, gz), 0.55, cy + 1.2, VJ.GUN_PIVOT[1] - 0.4, "steel", n=10, m=m)
    K.rbox((7.2, VJ.GUN_PIVOT[1] - 1.0, gz - 0.7), (8.8, VJ.GUN_PIVOT[1] - 0.3, gz + 0.7), 0.15, "steel", seg=1, m=m)
    return m


def jeep_gun():
    """M2 .50 calibre on its cradle, pitching about the gun pivot"""
    m = Mesh()
    gx, gy, gz = VJ.GUN_PIVOT
    K.rbox((6.9, gy - 0.6, gz - 2.0), (9.1, gy + 1.2, gz + 3.6), 0.25, "gunmetal", seg=1, m=m)
    K.rbox((7.1, gy + 1.1, gz - 1.6), (8.9, gy + 1.5, gz + 3.0), 0.12, "gunmetal", seg=1, m=m)
    by = gy + 0.3
    K.cylinder("z", (8.0, by), 0.72, gz - 7.0, gz - 2.0, "gunmetal", n=12, bevel=0.08, m=m)
    for i in range(6):
        z = gz - 6.4 + i * 0.75
        for a in (0, 90, 180, 270):
            r = math.radians(a + 45)
            K.box((8 + math.cos(r) * 0.6 - 0.12, by + math.sin(r) * 0.6 - 0.12, z), (8 + math.cos(r) * 0.6 + 0.12, by + math.sin(r) * 0.6 + 0.12, z + 0.35),
                  "black", m=m, faces="xXyY")
    K.cylinder("z", (8.0, by), 0.42, gz - 14.0, gz - 7.0, "gunmetal", n=10, m=m)
    K.cylinder("z", (8.0, by), 0.58, gz - 15.2, gz - 14.0, "gunmetal", n=12, bevel=0.08, m=m)
    K.cylinder("z", (8.0, by), 0.25, gz - 15.22, gz - 15.15, "black", n=8, caps=(True, False), m=m)
    K.rbox((7.75, gy + 0.7, gz - 13.2), (8.25, gy + 1.6, gz - 12.8), 0.08, "gunmetal", seg=1, m=m)
    for x in (6.6, 8.9):
        K.cylinder("y", (x + 0.25, gz + 4.1), 0.28, gy - 0.6, gy + 1.0, "rubber", n=8, bevel=0.08, m=m)
    K.rbox((7.6, gy - 0.1, gz + 3.6), (8.4, gy + 0.5, gz + 4.2), 0.1, "steel", seg=1, m=m)
    K.rbox((3.6, gy - 1.8, gz - 1.0), (6.6, gy + 0.8, gz + 2.6), 0.2, "olive", seg=1, m=m)
    for i in range(4):
        z = gz - 0.6 + i * 0.8
        K.cylinder("x", (gy + 0.8, z + 0.25), 0.22, 6.3, 7.1, "brass", n=8, m=m)
    return m


def tow():
    """TOW launcher on the Humvee pintle, about the gun pivot"""
    m = Mesh()
    gx, gy, gz = A3.TOW_PIVOT
    ty = A3.TOW_MUZZLE[1]
    K.rbox((6.8, gy - 1.2, gz - 1.6), (9.2, gy + 0.2, gz + 1.6), 0.3, "olive", seg=1, m=m)
    K.cylinder("z", (gx, ty), 1.15, gz - 11.5, gz + 5.0, "olive", n=18, m=m)
    for z0, z1 in ((gz - 11.5, gz - 10.5), (gz + 4.0, gz + 5.0)):
        K.cylinder("z", (gx, ty), 1.32, z0, z1, "dark", n=18, bevel=0.12, m=m)
    K.cylinder("z", (gx, ty), 0.9, gz - 11.56, gz - 11.5, "black", n=14, caps=(True, False), m=m)
    K.sphere((gx, ty, gz - 11.3), 0.3, 0.3, 0.2, "lens", n=8, rows=4, m=m)
    for z in (gz - 6.0, gz + 1.0):
        K.torus("z", (gx, ty), z, 1.2, 0.12, "steel", n=18, k=5, m=m)
    K.rbox((3.8, gy - 1.6, gz - 3.4), (6.6, gy + 1.0, gz + 2.6), 0.35, "olive", seg=2, m=m)
    K.cylinder("z", (4.5, gy - 0.7), 0.42, gz - 3.5, gz - 3.4, "lens", n=10, caps=(True, False), m=m)
    K.cylinder("z", (5.9, gy - 0.7), 0.42, gz - 3.5, gz - 3.4, "red", n=10, caps=(True, False), m=m)
    K.cylinder("z", (5.2, gy), 0.55, gz + 2.6, gz + 3.6, "rubber", n=10, bevel=0.12, m=m)
    for x in (6.4, 9.1):
        K.cylinder("y", (x + 0.25, gz + 2.1), 0.26, gy - 0.9, gy + 0.5, "rubber", n=8, bevel=0.08, m=m)
    K.cylinder("x", (gy - 0.1, gz + 2.0), 0.18, 6.4, 9.6, "steel", n=8, m=m)
    return m


CARS = {"btr82": btr82, "pantsir": pantsir}
