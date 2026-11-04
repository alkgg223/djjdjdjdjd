"""Air defence: seven radars and seven launchers / guns as smooth meshes, in block units x 16 (16 = one block), the
block's centre bottom at (8, 0, 8), front towards -Z (north). Each has a fixed 'base', a 'head' that turns about the
vertical axis through HEAD, and (launchers and guns) an 'arm' that elevates about the X axis through ARM. MUZZLES are
the launch / muzzle points on the arm at zero turn and elevation.

Writes the parts into models/entity/airdef/<id>/ and the pivots into client/render/AirDefenseGeo.java."""
import math
import os

import numpy as np

import meshkit as K
from meshkit import Mesh, v3

GEO = {}


def wheel(m, x, y, z, r=4.5, w=3.0, axis_side=1):
    K.cylinder("x", (y, z), r, x - w / 2, x + w / 2, "rubber", n=14, bevel=0.5, m=m)
    K.cylinder("x", (y, z), r * 0.55, x - w / 2 - 0.1, x + w / 2 + 0.1, "olive", n=10, m=m)


def trailer(m, w=36, l=60, h=10, y0=8, wheels_z=(10, 22), color="olive"):
    """a flatbed trailer centred on the block, wheels underneath, a tow bar in front"""
    x0, x1 = 8 - w / 2, 8 + w / 2
    z0, z1 = 8 - l / 2, 8 + l / 2
    K.rbox((x0, y0, z0), (x1, y0 + h, z1), 1.0, color, seg=1, m=m)
    for zz in wheels_z:
        for s in (-1, 1):
            wheel(m, 8 + s * (w / 2 - 1.5), 4.5, 8 + zz, 4.5)
    K.tube([(8, y0 + 2, z0), (8, 5, z0 - 14)], 1.0, "steel", n=6, m=m)
    for s in (-1, 1):
        K.rbox((8 + s * (w / 2 - 4) - 2, 0, z0 + 4), (8 + s * (w / 2 - 4) + 2, y0, z0 + 8), 0.5, "steel", seg=1, m=m)
    return y0 + h


def truck(m, w=34, l=100, deck=14, color="olive", cab_front=True):
    """a 6x6 truck chassis with the cab at the front"""
    z0, z1 = 8 - l / 2, 8 + l / 2
    K.rbox((8 - w / 2, 8, z0 + 22), (8 + w / 2, deck, z1), 1.0, color, seg=1, m=m)
    if cab_front:
        K.rbox((8 - w / 2, 8, z0), (8 + w / 2, 30, z0 + 22), 2.5, color, seg=2, m=m)
        K.box((8 - w / 2 + 2, 20, z0 - 0.05), (8 + w / 2 - 2, 28, z0 + 0.5), "glass_dark", m=m)
    for zz in (z0 + 12, z1 - 26, z1 - 12):
        for s in (-1, 1):
            wheel(m, 8 + s * (w / 2 - 2), 6, zz, 6, 4)
    return deck


def tracked(m, w=40, l=90, h=20, color="olive"):
    z0, z1 = 8 - l / 2, 8 + l / 2
    for s in (-1, 1):
        x = 8 + s * (w / 2 - 5)
        K.rbox((x - 5, 0, z0 + 2), (x + 5, 11, z1 - 2), 4.5, "track", seg=2, m=m)
        for k in range(6):
            K.cylinder("x", (5.5, z0 + 10 + k * (l - 20) / 5), 4.5, x - 5.3, x + 5.3, "rubber", n=12, m=m)
    K.rbox((8 - w / 2 + 2, 9, z0), (8 + w / 2 - 2, h, z1), 2.0, color, seg=1, m=m)
    K.plate([v3(8 - w / 2 + 2, h - 6, z0), v3(8 + w / 2 - 2, h - 6, z0), v3(8 + w / 2 - 2, h, z0 + 10), v3(8 - w / 2 + 2, h, z0 + 10)], 1.0, color, m=m)
    return h


def turntable(m, y, r=9):
    K.cylinder("y", (8, 8), r, y, y + 3, "steel", n=20, bevel=0.5, m=m)


def finish(name, base, head, arm=None, head_pivot=(8, 20, 8), arm_pivot=None, muzzles=(), kind="radar", label_y=48):
    parts = {"base": base, "head": head}
    if arm is not None:
        parts["arm"] = arm
    GEO[name] = dict(head=head_pivot, arm=arm_pivot or head_pivot, muzzles=list(muzzles), kind=kind, label=label_y)
    return parts


# ------------------------------------------------------------------------------------------------ radars
def radar_mpq65():
    """AN/MPQ-65 (Patriot): trailer with the big tilted phased-array face"""
    base, head = Mesh(), Mesh()
    top = trailer(base, 34, 64, 12)
    turntable(base, top, 10)
    hy = top + 3
    # the face: an octagonal array leaning back, frame round it, small arrays below
    tilt = math.radians(25)
    def face(px, py, dz=0.0):
        return v3(px, hy + 4 + py * math.cos(tilt), 2 + dz - py * math.sin(tilt))
    K.plate([face(-6, 0), face(22, 0), face(22, 30), face(-6, 30)], 3.0, "paint", m=head)
    octo = [face(8 + 11 * math.cos(math.radians(a + 22.5)), 15 + 11 * math.sin(math.radians(a + 22.5)), -1.6) for a in range(0, 360, 45)]
    K.plate(octo, 0.6, "dark", m=head)
    for s in (-1, 1):
        K.plate([face(8 + s * 4 - 2.5, 1.5, -1.6), face(8 + s * 4 + 2.5, 1.5, -1.6), face(8 + s * 4 + 2.5, 5, -1.6), face(8 + s * 4 - 2.5, 5, -1.6)],
                0.5, "dark", m=head)
    K.rbox((-4, hy, 6), (20, hy + 6, 18), 1.0, "paint", seg=1, m=head)
    return finish("radar_mpq65", base, head, head_pivot=(8, hy, 8), label_y=hy + 36)


def radar_96l6():
    """96L6E (S-400): a truck with a tall mast and the rotating flat array on top"""
    base, head = Mesh(), Mesh()
    deck = truck(base, 34, 100, 16)
    K.rbox((0, deck, -10), (16, deck + 14, 30), 1.0, "paint", seg=1, m=base)
    K.cylinder("y", (8, 30), 2.2, deck, deck + 60, "steel", n=12, m=base)
    for s in (-1, 1):
        K.tube([(8 + s * 12, deck, 44), (8, deck + 40, 30)], 0.8, "steel", n=6, m=base)
    hy = deck + 60
    K.cylinder("y", (8, 30), 4, hy, hy + 3, "steel", n=14, m=base)
    # the array: a big flat rectangle, its back frame, the IFF bar on top
    K.rbox((-10, hy + 3, 26), (26, hy + 27, 29), 0.6, "paint", seg=1, m=head)
    K.box((-9, hy + 4, 25.9), (25, hy + 26, 26.1), "dark", m=head, faces="z")
    K.rbox((-12, hy + 27, 27), (28, hy + 29.5, 28.5), 0.5, "light_grey", seg=1, m=head)
    for s in (-1, 1):
        K.tube([(8 + s * 14, hy + 4, 29), (8, hy + 3, 33)], 0.6, "steel", n=5, m=head)
    return finish("radar_96l6", base, head, head_pivot=(8, hy, 30), label_y=hy + 40)


def radar_giraffe():
    """Giraffe AMB: a truck whose folding mast lifts a small antenna high over the trees"""
    base, head = Mesh(), Mesh()
    deck = truck(base, 32, 84, 16, "sand")
    K.rbox((-6, deck, 0), (22, deck + 18, 40), 1.0, "sand", seg=1, m=base)
    K.tube([(8, deck + 18, 34), (8, deck + 46, 26)], 2.0, "steel", n=10, m=base)
    K.tube([(8, deck + 46, 26), (8, deck + 76, 26)], 1.6, "steel", n=10, m=base)
    hy = deck + 76
    K.rbox((-6, hy, 22), (22, hy + 12, 25), 0.8, "sand", seg=1, m=head)
    K.box((-5, hy + 1, 21.9), (21, hy + 11, 22.1), "dark", m=head, faces="z")
    K.cylinder("y", (8, 26), 2.5, hy - 2, hy + 1, "steel", n=10, m=head)
    return finish("radar_giraffe", base, head, head_pivot=(8, hy, 26), label_y=hy + 22)


def radar_sentinel():
    """AN/MPQ-64 Sentinel: a light trailer with a fast spinning flat antenna and the IFF bar"""
    base, head = Mesh(), Mesh()
    top = trailer(base, 28, 46, 10)
    K.rbox((-2, top, -2), (18, top + 12, 20), 1.0, "paint", seg=1, m=base)
    hy = top + 12
    turntable(base, hy, 6)
    K.rbox((-6, hy + 3, 6), (22, hy + 20, 8.5), 0.5, "paint", seg=1, m=head)
    K.box((-5, hy + 4, 5.9), (21, hy + 19, 6.1), "dark", m=head, faces="z")
    K.rbox((-8, hy + 20, 6.5), (24, hy + 22, 8), 0.4, "light_grey", seg=1, m=head)
    K.rbox((4, hy + 3, 8.5), (12, hy + 8, 12), 0.5, "paint", seg=1, m=head)
    return finish("radar_sentinel", base, head, head_pivot=(8, hy, 8), label_y=hy + 30)


def radar_elm2084():
    """EL/M-2084 (Iron Dome): a trailer with a large rotating phased-array panel"""
    base, head = Mesh(), Mesh()
    top = trailer(base, 36, 70, 12, color="sand")
    turntable(base, top, 10)
    hy = top + 3
    tilt = math.radians(12)
    pts = lambda dz: [v3(-12, hy + 4, 4 + dz), v3(28, hy + 4, 4 + dz), v3(28, hy + 4 + 26 * math.cos(tilt), 4 + dz - 26 * math.sin(tilt)),
                      v3(-12, hy + 4 + 26 * math.cos(tilt), 4 + dz - 26 * math.sin(tilt))]
    K.plate(pts(0), 3.0, "sand", m=head)
    K.plate(pts(-1.6), 0.5, "dark", m=head)
    K.rbox((-2, hy, 6), (18, hy + 7, 16), 1.0, "sand", seg=1, m=head)
    return finish("radar_elm2084", base, head, head_pivot=(8, hy, 8), label_y=hy + 36)


def radar_p18():
    """P-18 (VHF): a van and a tall mast carrying a big frame of Yagi antennas; long waves see stealth aircraft better"""
    base, head = Mesh(), Mesh()
    deck = truck(base, 32, 90, 16, "olive")
    K.rbox((-8, deck, -2), (24, deck + 22, 44), 1.0, "olive", seg=1, m=base)
    K.cylinder("y", (8, 20), 1.8, deck + 22, deck + 54, "steel", n=10, m=base)
    hy = deck + 54
    # the frame and two rows of Yagis pointing forward
    K.rbox((-22, hy + 2, 18), (38, hy + 4, 22), 0.4, "steel", seg=1, m=head)
    K.rbox((-22, hy + 16, 18), (38, hy + 18, 22), 0.4, "steel", seg=1, m=head)
    for k in range(8):
        x = -19 + k * 8
        for yy in (hy + 3, hy + 17):
            K.cylinder("z", (x, yy), 0.4, -6, 20, "light_grey", n=5, m=head)
            for j in range(5):
                z = -4 + j * 5
                K.cylinder("x", (yy, z), 0.25, x - 3.5 + j * 0.3, x + 3.5 - j * 0.3, "light_grey", n=4, m=head)
    K.cylinder("y", (8, 20), 3, hy - 1, hy + 2, "steel", n=10, m=head)
    return finish("radar_p18", base, head, head_pivot=(8, hy, 20), label_y=hy + 28)


def radar_kupol():
    """9S18M1 Kupol (Buk): tracked chassis with the tall curved antenna that turns over the hull"""
    base, head = Mesh(), Mesh()
    h = tracked(base, 40, 96, 20)
    turntable(base, h, 9)
    hy = h + 3
    # the curved reflector: slices of a parabolic cylinder, tall and narrow, leaning back a little
    rows = []
    for k in range(9):
        y = hy + 6 + k * 4.2
        ring = []
        for j in range(9):
            u = (j - 4) / 4.0
            x = 8 + u * 12
            z = 4 + 5 * u * u + k * 0.6
            ring.append(v3(x, y, z))
        rows.append(ring)
    for a, b in zip(rows, rows[1:]):
        for j in range(len(a) - 1):
            K.plate([a[j], a[j + 1], b[j + 1], b[j]], 0.9, "paint", m=head)
    K.tube([(8, hy + 2, 14), (8, hy + 24, 10)], 1.0, "steel", n=6, m=head)
    K.cylinder("z", (8, hy + 24), 1.2, 0, 10, "dark", n=8, m=head)
    K.rbox((0, hy, 10), (16, hy + 6, 22), 1.0, "paint", seg=1, m=head)
    return finish("radar_kupol", base, head, head_pivot=(8, hy, 8), label_y=hy + 50)


# ------------------------------------------------------------------------------------------------ launchers and guns
def canister_pack(m, cols, rows, w, h, length, y0, z0, mat="paint", tube=False, r=None):
    """a block of launch canisters (or round tubes) pointing -Z, its front face at z0"""
    x0 = 8 - cols * w / 2
    mouths = []
    for c in range(cols):
        for rr in range(rows):
            x = x0 + c * w + w / 2
            y = y0 + rr * h + h / 2
            if tube:
                K.cylinder("z", (x, y), r or w * 0.42, z0, z0 + length, mat, n=10, m=m)
                K.cylinder("z", (x, y), (r or w * 0.42) * 0.8, z0 - 0.05, z0 + 0.2, "black", n=8, caps=(True, False), m=m)
            else:
                K.rbox((x - w / 2 + 0.3, y - h / 2 + 0.3, z0), (x + w / 2 - 0.3, y + h / 2 - 0.3, z0 + length), 0.4, mat, seg=1, m=m)
                K.box((x - w / 2 + 1, y - h / 2 + 1, z0 - 0.06), (x + w / 2 - 1, y + h / 2 - 1, z0), "dark", m=m, faces="z")
            mouths.append((x, y, z0 - 1))
    return mouths


def sam_patriot():
    """M903 Patriot launcher: a trailer, a turntable and four big canisters raised on the arm"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    top = trailer(base, 34, 76, 12)
    turntable(base, top, 11)
    hy = top + 3
    K.rbox((-4, hy, 2), (20, hy + 6, 22), 1.0, "olive", seg=1, m=head)
    ay, az = hy + 8, 22
    mouths = canister_pack(arm, 2, 2, 11, 11, 66, ay, az - 66, "olive")
    K.rbox((-4, ay - 2, az - 6), (20, ay, az), 0.5, "steel", seg=1, m=arm)
    return finish("sam_patriot", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile", hy + 40)


def sam_s400():
    """5P85 (S-400): a truck whose erector stands four long tubes upright to cold-launch the missiles"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    deck = truck(base, 34, 120, 16)
    hy = deck
    K.rbox((-6, hy, 30), (22, hy + 4, 56), 0.5, "steel", seg=1, m=head)
    ay, az = hy + 6, 56
    mouths = canister_pack(arm, 2, 2, 12, 12, 96, ay, az - 96, "olive", tube=True, r=5.2)
    K.rbox((-6, ay - 3, az - 96), (22, ay, az), 0.5, "steel", seg=1, m=arm)
    return finish("sam_s400", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile_vertical", hy + 110)


def sam_nasams():
    """NASAMS: a trailer with a launcher of six AMRAAM canisters"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    top = trailer(base, 30, 56, 10, color="sand")
    turntable(base, top, 8)
    hy = top + 3
    K.rbox((-2, hy, 2), (18, hy + 5, 18), 1.0, "sand", seg=1, m=head)
    ay, az = hy + 7, 18
    mouths = canister_pack(arm, 3, 2, 7, 7, 44, ay, az - 44, "sand")
    return finish("sam_nasams", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile", hy + 30)


def sam_tamir():
    """Iron Dome launcher: a big box of twenty Tamir tubes on a turntable, fixed steep elevation"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    top = trailer(base, 36, 50, 10, color="sand")
    turntable(base, top, 10)
    hy = top + 3
    K.rbox((-4, hy, 0), (20, hy + 6, 16), 1.0, "sand", seg=1, m=head)
    ay, az = hy + 8, 16
    K.rbox((-12, ay, az - 40), (28, ay + 26, az), 1.0, "sand", seg=1, m=arm)
    mouths = []
    for c in range(5):
        for r in range(4):
            x, y = -8 + c * 8, ay + 3.5 + r * 6.3
            K.cylinder("z", (x, y), 2.4, az - 40.2, az - 39.5, "black", n=8, caps=(True, False), m=arm)
            mouths.append((x, y, az - 41))
    return finish("sam_tamir", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile_fixed", hy + 40)


def barrel(m, x, y, z0, length, r, mat="gunmetal", brake=True):
    K.cylinder("z", (x, y), r, z0, z0 + length, mat, n=10, m=m)
    if brake:
        K.cylinder("z", (x, y), r * 1.5, z0 - 2.5, z0, mat, n=10, m=m)
        K.cylinder("z", (x, y), r * 0.6, z0 - 2.6, z0 - 2.4, "black", n=8, caps=(True, False), m=m)


def aa_gepard():
    """Gepard: tracked hull, a turret with a 35 mm gun on each side and the search radar turning on its back"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    h = tracked(base, 40, 90, 20)
    hy = h
    K.rbox((-6, hy, -8), (22, hy + 14, 24), 2.0, "olive", seg=2, m=head)
    K.cylinder("y", (8, 20), 1.2, hy + 14, hy + 22, "steel", n=8, m=head)
    K.rbox((-2, hy + 22, 18), (18, hy + 28, 20), 0.5, "dark", seg=1, m=head)
    K.sphere((8, hy + 10, -9), 3, 3, 2, "dark", n=10, rows=5, m=head)
    ay, az = hy + 8, 4
    mouths = []
    for s in (-1, 1):
        x = 8 + s * 17
        K.rbox((x - 3, ay - 4, az - 4), (x + 3, ay + 4, az + 14), 1.0, "olive", seg=1, m=arm)
        barrel(arm, x, ay + 1, az - 40, 36, 1.0)
        mouths.append((x, ay + 1, az - 43))
    K.cylinder("x", (ay, az), 1.5, -9, 25, "steel", n=8, m=arm)
    return finish("aa_gepard", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "gun", hy + 40)


def aa_zu23():
    """ZU-23-2: a light two-wheel carriage with the twin 23 mm on its cradle"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    K.rbox((-6, 4, -6), (22, 8, 22), 1.0, "olive", seg=1, m=base)
    for s in (-1, 1):
        wheel(base, 8 + s * 16, 5, 8, 5, 3)
        K.tube([(8 + s * 10, 4, 8 + s * 12), (8 + s * 22, 0.5, 8 + s * 22)], 0.8, "steel", n=6, m=base)
    hy = 8
    K.rbox((-2, hy, 0), (18, hy + 10, 16), 1.0, "olive", seg=1, m=head)
    K.rbox((-6, hy + 2, 10), (-2, hy + 6, 16), 0.5, "dark", seg=1, m=head)
    ay, az = hy + 12, 6
    mouths = []
    for s in (-1, 1):
        x = 8 + s * 2.6
        K.rbox((x - 2, ay - 2.5, az - 2), (x + 2, ay + 2.5, az + 10), 0.6, "olive", seg=1, m=arm)
        barrel(arm, x, ay, az - 30, 28, 0.7)
        K.rbox((x - 1.5, ay + 2.5, az), (x + 1.5, ay + 8, az + 4), 0.4, "olive", seg=1, m=arm)
        mouths.append((x, ay, az - 33))
    return finish("aa_zu23", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "gun", hy + 30)


def aa_bofors():
    """Bofors 40 mm L/70: a four-wheel carriage, a gun shield and the long barrel with its flash hider"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    K.rbox((-10, 5, -14), (26, 10, 30), 1.0, "olive", seg=1, m=base)
    for zz in (-8, 24):
        for s in (-1, 1):
            wheel(base, 8 + s * 20, 5, zz, 5, 3)
    hy = 10
    turntable(base, hy, 9)
    K.rbox((-4, hy + 3, -2), (20, hy + 14, 20), 1.5, "olive", seg=1, m=head)
    K.plate([v3(-6, hy + 6, -4), v3(22, hy + 6, -4), v3(20, hy + 22, -2), v3(-4, hy + 22, -2)], 1.0, "olive", m=head)
    ay, az = hy + 16, 6
    K.rbox((4, ay - 4, az - 6), (12, ay + 4, az + 18), 1.0, "olive", seg=1, m=arm)
    barrel(arm, 8, ay + 1, az - 48, 44, 1.2)
    K.rbox((3, ay + 4, az - 2), (13, ay + 10, az + 6), 0.6, "olive", seg=1, m=arm)
    return finish("aa_bofors", base, head, arm, (8, hy, 8), (8, ay, az), [(8, ay + 1, az - 51)], "gun", hy + 34)


RADARS = {"radar_mpq65": radar_mpq65, "radar_96l6": radar_96l6, "radar_giraffe": radar_giraffe, "radar_sentinel": radar_sentinel,
          "radar_elm2084": radar_elm2084, "radar_p18": radar_p18, "radar_kupol": radar_kupol}
LAUNCHERS = {"sam_patriot": sam_patriot, "sam_s400": sam_s400, "sam_nasams": sam_nasams, "sam_tamir": sam_tamir,
             "aa_gepard": aa_gepard, "aa_zu23": aa_zu23, "aa_bofors": aa_bofors}
ALL = {**RADARS, **LAUNCHERS}

OUT_JAVA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "java", "com", "gunsmod", "block", "airdef",
                        "AirDefenseGeo.java")


def write_java():
    def arr(v):
        return "new double[]{" + ", ".join(repr(float(x)) for x in v) + "}"
    lines = ["package com.gunsmod.block.airdef;", "", "import java.util.Map;", "",
             "/** Generated by tools/airdef_mesh.py: pivots (model units, 16 = a block), muzzle points and label height of the air defence models. */",
             "public final class AirDefenseGeo {", "    private AirDefenseGeo() {}", "",
             "    public record Geo(double[] head, double[] arm, double[][] muzzles, double label) {}", "",
             "    public static final Map<String, Geo> GEO = Map.ofEntries("]
    items = []
    for name, g in GEO.items():
        mz = "new double[][]{" + ", ".join("{" + ", ".join(repr(float(x)) for x in p) + "}" for p in g["muzzles"]) + "}"
        items.append(f'            Map.entry("{name}", new Geo({arr(g["head"])}, {arr(g["arm"])}, {mz}, {float(g["label"])}))')
    lines.append(",\n".join(items) + ");")
    lines.append("}")
    os.makedirs(os.path.dirname(OUT_JAVA), exist_ok=True)
    with open(OUT_JAVA, "w") as fh:
        fh.write("\n".join(lines) + "\n")


RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")

if __name__ == "__main__":
    for k, fn in ALL.items():
        parts = fn()
        for n, mesh in parts.items():
            K.write_part(mesh, "entity/airdef/" + k, n, RES)
        print(k, sum(p.quad_count() for p in parts.values()), [n for n in parts])
    write_java()
