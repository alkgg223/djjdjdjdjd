"""3.0 naval vessels (model units 0.25 block, centred on x = 8, bow -Z, the waterline at y = 0 of the model is set by
ShipType.draft). Parts: body (hull + superstructure), gun (the main gun mount, turned by the renderer about GUN[name]),
marks. Plus the torpedo.

patrol    fast patrol boat: planing hull, wheelhouse, 30 mm mount forward, the mast with radar
frigate   frigate: 76 mm gun forward, VLS cells, bridge, mast with the radar panels, funnel, hangar and helideck aft
destroyer destroyer: 127 mm gun, two VLS fields, the tall bridge with phased-array faces, CIWS fore and aft, helideck
landing   landing craft (LCM): box hull, the bow ramp, the wheelhouse aft, a machine gun on the wheelhouse
sub       attack submarine: the round hull, the sail with periscopes and planes, the cruciform tail and the propeller
carrier   aircraft carrier: the flight deck with the angled deck, catapult tracks and markings, the island, CIWS mounts"""
import math

import meshkit as K
import vehmesh as V
from cars_mesh import marks
from meshkit import Mesh, v3

GUN = {"patrol": (8.0, 9.0, -10.0), "frigate": (8.0, 13.0, -34.0), "destroyer": (8.0, 15.0, -44.0), "landing": (8.0, 15.0, 22.0),
       "sub": (8.0, 16.0, -14.0), "carrier": (28.0, 29.0, 60.0)}


def hull(m, length, beam, deck, depth, bow_len, stern_w=0.9, mat="paint_mid", flare=1.0, keel=0.18, n=14):
    """a ship's hull from bow (z = -length/2) to stern: deck edge, the flared side, the chine and the keel"""
    rings = []
    z0, z1 = -length / 2, length / 2
    for i in range(n + 1):
        t = i / n
        z = z0 + (z1 - z0) * t
        d = z - z0
        w = beam / 2 * (math.sin(min(1.0, d / bow_len) * math.pi / 2) ** 0.8 if d < bow_len else 1.0)
        if t > 0.85:
            w *= 1 - (1 - stern_w) * (t - 0.85) / 0.15
        w = max(w, 0.15)
        kd = depth * (0.55 + 0.45 * min(1.0, d / (bow_len * 1.2)))
        ring = [(8 - w * flare, deck), (8 + w * flare, deck), (8 + w * 0.92, deck - kd * 0.5), (8 + w * keel + 0.1, deck - kd),
                (8 - w * keel - 0.1, deck - kd), (8 - w * 0.92, deck - kd * 0.5)]
        rings.append([v3(x, y, z) for x, y in ring])
    K.loft(rings, mat, caps=(True, True), m=m)
    # the boot topping at the waterline and the deck edge
    return z0, z1


def deck_plate(m, length, beam, deck, bow_len, mat="dark"):
    pts = []
    z0 = -length / 2
    for i in range(9):
        t = i / 8
        z = z0 + bow_len * t
        w = beam / 2 * (math.sin(t * math.pi / 2) ** 0.8) - 0.15
        pts.append((8 + max(0.1, w), z))
    pts += [(8 + beam / 2 - 0.15, length / 2 - 0.3), (8 - beam / 2 + 0.15, length / 2 - 0.3)]
    pts += [(16 - x, z) for x, z in reversed(pts[:9])]
    K.plate([v3(x, deck + 0.05, z) for x, z in pts], 0.1, mat, m=m)


def railing(m, x, deck, z0, z1, h=1.2):
    K.box((x - 0.05, deck + h - 0.1, z0), (x + 0.05, deck + h, z1), "steel", m=m)
    n = max(2, int((z1 - z0) / 2.0))
    for i in range(n + 1):
        z = z0 + (z1 - z0) * i / n
        K.box((x - 0.05, deck, z - 0.05), (x + 0.05, deck + h, z + 0.05), "steel", m=m)


def mast(m, x, y0, z, h, radar=True):
    K.cylinder("y", (x, z), 0.35, y0, y0 + h, "light_grey", n=8, m=m)
    K.box((x - 3.0, y0 + h * 0.7, z - 0.15), (x + 3.0, y0 + h * 0.7 + 0.3, z + 0.15), "light_grey", m=m)
    if radar:
        K.box((x - 2.2, y0 + h + 0.2, z - 0.25), (x + 2.2, y0 + h + 0.9, z + 0.25), "array", m=m)
    K.sphere((x, y0 + h + 1.4, z), 0.5, 0.5, 0.5, "white", n=8, rows=4, m=m)


def ciws(m, x, y, z):
    K.cylinder("y", (x, z), 1.0, y, y + 0.8, "light_grey", n=12, m=m)
    K.cylinder("y", (x, z), 0.8, y + 0.8, y + 2.6, "white", n=12, m=m)
    K.sphere((x, y + 2.6, z), 0.8, 0.8, 0.8, "white", n=12, rows=5, top_only=True, m=m)
    K.cylinder("z", (x, y + 1.4), 0.25, z - 0.8, z - 3.0, "gunmetal", n=8, m=m)


def vls(m, x0, x1, deck, z0, z1, cols=4, rows=4):
    K.box((x0, deck, z0), (x1, deck + 0.3, z1), "paint_dark", m=m)
    w, h = (x1 - x0) / cols, (z1 - z0) / rows
    for i in range(cols):
        for j in range(rows):
            K.box((x0 + i * w + 0.12, deck + 0.3, z0 + j * h + 0.12), (x0 + (i + 1) * w - 0.12, deck + 0.38, z0 + (j + 1) * h - 0.12), "dark", m=m, faces="Y")


def gun_mount(g, x, y, z, r, barrel, bore=0.28, shield=True):
    if shield:
        K.rbox((x - r, y, z - r * 1.1), (x + r, y + r * 1.0, z + r * 1.2), r * 0.35, "light_grey", seg=2, m=g)
    else:
        K.cylinder("y", (x, z), r, y, y + r * 0.6, "light_grey", n=12, m=g)
    K.cylinder("z", (x, y + r * 0.5), bore, z - r * 1.0, z - r * 1.0 - barrel, "gunmetal", n=10, r1=bore * 0.8, m=g)
    K.cylinder("z", (x, y + r * 0.5), bore * 0.6, z - r * 1.0 - barrel - 0.02, z - r * 1.0 - barrel, "black", n=8, m=g)


def patrol():
    m, g = Mesh(), Mesh()
    deck = 8.0
    hull(m, 48, 13, deck, 6.0, 14, stern_w=0.95)
    deck_plate(m, 48, 13, deck, 14, "tread_plate")
    K.rbox((4.0, deck, -2.0), (12.0, deck + 4.6, 10.0), 0.6, "light_grey", seg=1, m=m)
    for x in (4.0, 12.0):
        K.box((x - 0.05, deck + 2.6, -1.4), (x + 0.05, deck + 3.8, 9.0), "glass_dark", m=m, faces="xX")
    K.plate([v3(4.4, deck + 2.6, -2.05), v3(11.6, deck + 2.6, -2.05), v3(11.6, deck + 4.2, -1.0), v3(4.4, deck + 4.2, -1.0)], 0.08, "glass_dark", m=m)
    mast(m, 8.0, deck + 4.6, 4.0, 5.0)
    for x in (2.2, 13.8):
        railing(m, x, deck, -14.0, 22.0, 1.0)
    K.cylinder("y", (8.0, 19.0), 1.4, deck, deck + 1.2, "dark", n=12, m=m)
    gx, gy, gz = GUN["patrol"]
    gun_mount(g, gx, gy, gz, 1.4, 4.0, 0.2, shield=False)
    V.machine_gun(g, gx + 0.8, gy + 1.2, gz + 0.4, length=1.6)
    spots = [(v3(1.6, 5.0, 4.0), (-1, 0, 0), (0, 1, 0), 1.2), (v3(14.4, 5.0, 4.0), (1, 0, 0), (0, 1, 0), 1.2)]
    parts = {"body": m, "gun": g}
    parts.update(marks(spots))
    return parts


def frigate():
    m, g = Mesh(), Mesh()
    deck = 12.0
    hull(m, 120, 20, deck, 9.0, 34)
    deck_plate(m, 120, 20, deck, 34)
    vls(m, 4.6, 11.4, deck, -26.0, -20.0, 4, 3)
    K.rbox((2.2, deck, -16.0), (13.8, deck + 7.0, 6.0), 0.5, "light_grey", seg=1, m=m)
    K.rbox((3.4, deck + 7.0, -14.6), (12.6, deck + 10.0, -6.0), 0.4, "light_grey", seg=1, m=m)
    K.box((3.6, deck + 8.6, -14.65), (12.4, deck + 9.6, -14.6), "glass_dark", m=m, faces="z")
    for x in (3.2, 12.8):
        K.box((x - 0.05 if x < 8 else x, deck + 6.4, -12.0), (x if x < 8 else x + 0.05, deck + 8.6, -10.0), "array", m=m)
    mast(m, 8.0, deck + 10.0, -9.0, 9.0)
    K.rbox((5.4, deck + 7.0, -2.0), (10.6, deck + 11.0, 3.0), 0.8, "light_grey", seg=2, m=m)
    K.box((6.0, deck + 11.0, -1.4), (10.0, deck + 11.2, 2.4), "dark", m=m, faces="Y")
    K.rbox((3.0, deck, 10.0), (13.0, deck + 6.0, 30.0), 0.5, "light_grey", seg=1, m=m)
    K.box((4.0, deck + 0.5, 30.0), (12.0, deck + 5.4, 30.05), "paint_dark", m=m, faces="Z")
    K.box((7.8, deck + 0.06, 32.0), (8.2, deck + 0.12, 56.0), "white_mark", m=m, faces="Y")
    K.box((4.0, deck + 0.06, 44.0), (12.0, deck + 0.12, 44.4), "white_mark", m=m, faces="Y")
    ciws(m, 8.0, deck + 6.0, 26.0)
    for x in (-1.6, 17.6):
        railing(m, x, deck, -40.0, 58.0, 1.0)
    gx, gy, gz = GUN["frigate"]
    gun_mount(g, gx, gy, gz, 2.0, 7.0, 0.3)
    spots = [(v3(-1.9, 8.0, 0.0), (-1, 0, 0), (0, 1, 0), 2.0), (v3(17.9, 8.0, 0.0), (1, 0, 0), (0, 1, 0), 2.0)]
    parts = {"body": m, "gun": g}
    parts.update(marks(spots))
    return parts


def destroyer():
    m, g = Mesh(), Mesh()
    deck = 14.0
    hull(m, 144, 24, deck, 10.0, 40)
    deck_plate(m, 144, 24, deck, 40)
    vls(m, 3.8, 12.2, deck, -36.0, -28.0, 6, 4)
    vls(m, 3.8, 12.2, deck, 30.0, 38.0, 6, 4)
    K.rbox((1.0, deck, -24.0), (15.0, deck + 9.0, 8.0), 0.5, "light_grey", seg=1, m=m)
    K.rbox((2.4, deck + 9.0, -22.0), (13.6, deck + 15.0, -8.0), 0.5, "light_grey", seg=1, m=m)
    K.box((2.6, deck + 12.6, -22.05), (13.4, deck + 14.0, -22.0), "glass_dark", m=m, faces="z")
    for x, nx in ((2.38, -1), (13.62, 1)):
        K.box((x - 0.03, deck + 9.6, -18.0), (x + 0.03, deck + 13.6, -13.0), "array", m=m, faces="xX")
    K.box((5.4, deck + 9.6, -22.08), (10.6, deck + 12.4, -22.02), "array", m=m, faces="z")
    mast(m, 8.0, deck + 15.0, -12.0, 10.0, radar=False)
    for z in (-4.0, 14.0):
        K.rbox((5.2, deck + 9.0, z), (10.8, deck + 13.4, z + 5.0), 0.9, "light_grey", seg=2, m=m)
    K.rbox((2.6, deck, 16.0), (13.4, deck + 7.0, 28.0), 0.5, "light_grey", seg=1, m=m)
    ciws(m, 8.0, deck + 9.0, -24.6)
    ciws(m, 8.0, deck + 7.0, 26.0)
    K.box((7.8, deck + 0.06, 40.0), (8.2, deck + 0.12, 70.0), "white_mark", m=m, faces="Y")
    for x in (-3.6, 19.6):
        railing(m, x, deck, -50.0, 70.0, 1.0)
    gx, gy, gz = GUN["destroyer"]
    gun_mount(g, gx, gy, gz, 2.4, 9.0, 0.36)
    spots = [(v3(-3.9, 9.0, 0.0), (-1, 0, 0), (0, 1, 0), 2.4), (v3(19.9, 9.0, 0.0), (1, 0, 0), (0, 1, 0), 2.4)]
    parts = {"body": m, "gun": g}
    parts.update(marks(spots))
    return parts


def landing():
    m, g = Mesh(), Mesh()
    deck = 6.0
    K.rbox((0.0, 0.0, -28.0), (16.0, deck, 28.0), 0.6, "paint_mid", seg=1, m=m)
    K.box((0.6, deck, -26.0), (15.4, deck + 4.0, -24.0), "paint_mid", m=m)
    for x0, x1 in ((0.0, 1.2), (14.8, 16.0)):
        K.box((x0, deck, -26.0), (x1, deck + 4.0, 20.0), "paint_mid", m=m)
    K.plate([v3(1.2, deck + 0.1, -28.0), v3(14.8, deck + 0.1, -28.0), v3(14.8, deck + 4.6, -27.0), v3(1.2, deck + 4.6, -27.0)], 0.4, "paint", m=m)
    K.box((1.2, deck - 0.2, -24.0), (14.8, deck, 20.0), "tread_plate", m=m)
    K.rbox((3.0, deck, 20.0), (13.0, deck + 7.0, 27.0), 0.5, "light_grey", seg=1, m=m)
    K.box((3.4, deck + 4.8, 19.95), (12.6, deck + 6.2, 20.0), "glass_dark", m=m, faces="z")
    mast(m, 11.0, deck + 7.0, 25.0, 3.0, radar=False)
    gx, gy, gz = GUN["landing"]
    gun_mount(g, gx, gy, gz, 0.8, 2.4, 0.12, shield=False)
    spots = [(v3(-0.02, 3.0, 0.0), (-1, 0, 0), (0, 1, 0), 1.6), (v3(16.02, 3.0, 0.0), (1, 0, 0), (0, 1, 0), 1.6)]
    parts = {"body": m, "gun": g}
    parts.update(marks(spots))
    return parts


def sub():
    m, g = Mesh(), Mesh()
    cy = 7.0
    rings = []
    for z, r in ((-56, 0.4), (-54, 2.6), (-50, 4.6), (-44, 6.0), (-36, 6.6), (30, 6.6), (40, 5.4), (48, 3.6), (54, 1.6), (56, 0.6)):
        rings.append([v3(8 + math.cos(a) * r, cy + math.sin(a) * r, z) for a in [i * math.pi * 2 / 20 for i in range(20)]])
    K.loft(rings, "dark", caps=(True, True), m=m)
    K.rbox((6.6, cy + 6.0, -22.0), (9.4, cy + 13.0, -8.0), 1.2, "dark", seg=2, m=m)
    for side in (-1, 1):
        K.plate([v3(8 + side * 1.4, cy + 10.4, -18.0), v3(8 + side * 6.0, cy + 10.4, -16.0), v3(8 + side * 6.0, cy + 10.4, -13.6), v3(8 + side * 1.4, cy + 10.4, -12.8)],
                0.3, "dark", m=m)
    for x, h in ((7.6, 4.0), (8.4, 3.0), (8.0, 2.2)):
        K.cylinder("y", (x, -18.0 + (x - 8) * 4), 0.22, cy + 13.0, cy + 13.0 + h, "gunmetal", n=6, m=m)
    for a in (0, 90, 180, 270):
        p = Mesh()
        K.plate([v3(8.0, cy + 1.0, 44.0), v3(8.0, cy + 1.0, 50.0), v3(8.0, cy + 7.6, 52.0), v3(8.0, cy + 7.6, 48.0)], 0.3, "dark", m=p)
        p.rotate("z", a, (8.0, cy, 0))
        m.extend(p)
    K.cylinder("z", (8.0, cy), 0.8, 56.0, 57.2, "brass", n=10, m=m)
    for k in range(7):
        bl = Mesh()
        K.plate([v3(8.0, cy + 0.6, 56.8), v3(8.0, cy + 0.6, 57.4), v3(8.0, cy + 3.4, 57.6), v3(8.0, cy + 3.4, 57.0)], 0.12, "brass", m=bl)
        bl.rotate("z", k * 360 / 7, (8.0, cy, 0))
        m.extend(bl)
    gx, gy, gz = GUN["sub"]
    K.box((gx - 0.6, gy - 0.2, gz - 0.6), (gx + 0.6, gy, gz + 0.6), "gunmetal", m=g)
    spots = [(v3(6.58, cy + 9.6, -15.0), (-1, 0, 0), (0, 1, 0), 1.0), (v3(9.42, cy + 9.6, -15.0), (1, 0, 0), (0, 1, 0), 1.0)]
    parts = {"body": m, "gun": g}
    parts.update(marks(spots))
    return parts


def carrier():
    m, g = Mesh(), Mesh()
    deck = 28.0
    hull(m, 270, 36, deck - 3.0, 14.0, 60, stern_w=0.95, flare=1.0)
    # the flight deck overhanging the hull, the angled landing area to port
    K.box((-16.0, deck - 3.0, -140.0), (32.0, deck, 140.0), "paint_dark", m=m)
    K.plate([v3(-16.0, deck - 3.0, -20.0), v3(-30.0, deck - 3.0, 10.0), v3(-30.0, deck - 3.0, 110.0), v3(-16.0, deck - 3.0, 120.0)], 3.0, "paint_dark", m=m)
    for z in range(-130, 135, 12):
        K.box((7.8, deck, z), (8.2, deck + 0.08, z + 6), "white_mark", m=m, faces="Y")
    for x0 in (-4.0, 6.0):
        K.box((x0, deck, -136.0), (x0 + 0.6, deck + 0.06, -60.0), "dark", m=m, faces="Y")
    for z in range(0, 110, 10):
        K.box((-24.0 + z * 0.06, deck, z), (-23.4 + z * 0.06, deck + 0.08, z + 5), "amber", m=m, faces="Y")
    K.box((-27.0, deck, 96.0), (-12.0, deck + 0.08, 97.0), "white_mark", m=m, faces="Y")
    # the island on the starboard side: the bridge, the mast, the radar faces, the funnel stack
    K.rbox((24.0, deck, 20.0), (31.0, deck + 16.0, 52.0), 0.6, "light_grey", seg=1, m=m)
    K.rbox((25.0, deck + 16.0, 24.0), (30.4, deck + 22.0, 40.0), 0.5, "light_grey", seg=1, m=m)
    K.box((24.95, deck + 18.6, 24.0), (25.0, deck + 20.4, 40.0), "glass_dark", m=m, faces="x")
    K.box((24.95, deck + 10.0, 42.0), (25.0, deck + 14.0, 48.0), "array", m=m, faces="x")
    mast(m, 27.6, deck + 22.0, 32.0, 12.0)
    ciws(m, 31.0, deck - 3.0, -124.0)
    ciws(m, -14.0, deck - 3.0, 128.0)
    for (x0, z0) in ((32.0, -40.0), (32.0, 70.0)):
        K.box((x0, deck - 0.6, z0), (x0 + 0.3, deck, z0 + 18.0), "gunmetal", m=m)
    gx, gy, gz = GUN["carrier"]
    K.cylinder("y", (gx, gz), 1.0, gy - 1.0, gy, "light_grey", n=10, m=g)
    spots = [(v3(-16.1, deck - 6.0, -60.0), (-1, 0, 0), (0, 1, 0), 4.0), (v3(32.1, deck - 6.0, -80.0), (1, 0, 0), (0, 1, 0), 4.0),
             (v3(8.0, deck + 0.1, -110.0), (0, 1, 0), (0, 0, -1), 5.0)]
    parts = {"body": m, "gun": g}
    parts.update(marks(spots))
    return parts


def torpedo():
    m = Mesh()
    rings = []
    for z, r in ((-6.0, 0.1), (-5.6, 0.5), (-5.0, 0.7), (4.0, 0.7), (5.4, 0.4), (6.0, 0.2)):
        rings.append([v3(8 + math.cos(a) * r, 8 + math.sin(a) * r, 8 + z) for a in [i * math.pi * 2 / 12 for i in range(12)]])
    K.loft(rings, "gunmetal", caps=(True, True), m=m)
    for a in (0, 90, 180, 270):
        p = Mesh()
        K.plate([v3(8.0, 8.5, 12.6), v3(8.0, 8.5, 13.6), v3(8.0, 9.3, 13.8), v3(8.0, 9.3, 13.2)], 0.06, "dark", m=p)
        p.rotate("z", a, (8.0, 8.0, 0))
        m.extend(p)
    return m


SHIPS = {"patrol": patrol, "frigate": frigate, "destroyer": destroyer, "landing": landing, "sub": sub, "carrier": carrier}

if __name__ == "__main__":
    import os
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    for k, fn in SHIPS.items():
        parts = fn()
        for n, mesh in parts.items():
            K.write_part(mesh, "entity/ship_" + k, n, RES)
        print(k, sum(p.quad_count() for p in parts.values()))
    K.write_part(torpedo(), "entity/naval", "torpedo", RES)
