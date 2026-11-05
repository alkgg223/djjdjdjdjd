"""Air defence: seven radars and seven launchers / guns as smooth meshes, in block units x 16 (16 = one block), the
block's centre bottom at (8, 0, 8), front towards -Z (north). Each has a fixed 'base', a 'head' that turns about the
vertical axis through HEAD, and (launchers and guns) an 'arm' that elevates about the X axis through ARM. MUZZLES are
the launch / muzzle points on the arm at zero turn and elevation.

The chassis come from ground_kit: real tyres, truck cabs with windows and mirrors, trailers with drawbars and
outrigger jacks let down, tracked running gear. Array faces carry the module-grid texture, decks the tread plate.

Writes the parts into models/entity/airdef/<id>/ and the pivots into client/render/AirDefenseGeo.java."""
import math
import os

import numpy as np

import ground_kit as G
import meshkit as K
from meshkit import Mesh, v3

GEO = {}


def finish(name, base, head, arm=None, head_pivot=(8, 20, 8), arm_pivot=None, muzzles=(), kind="radar", label_y=48):
    parts = {"base": base, "head": head}
    if arm is not None:
        parts["arm"] = arm
    GEO[name] = dict(head=head_pivot, arm=arm_pivot or head_pivot, muzzles=list(muzzles), kind=kind, label=label_y)
    return parts


def array_face(m, corners, thick, mat="paint", face="array", frame="steel", inset=1.2, front=(0, 0, -1)):
    """a flat antenna: the panel, its module-grid face on the `front` side, a frame round the face. Returns the normal."""
    pts = [np.asarray(p, dtype=float) for p in corners]
    K.plate(pts, thick, mat, bevel=0.3, m=m)
    n = K.poly_normal(pts)
    if np.dot(n, np.asarray(front, dtype=float)) < 0:
        n = -n
    c = sum(pts) / len(pts)
    inner = [c + (p - c) * (1 - inset / max(4.0, float(np.linalg.norm(p - c)))) + n * (thick / 2 + 0.06) for p in pts]
    K.plate(inner, 0.12, face, m=m)
    for a, b in zip(pts, pts[1:] + pts[:1]):
        K.tube([a + n * (thick / 2), b + n * (thick / 2)], 0.35, frame, n=4, caps=(False, False), m=m)
    return n


def stiffeners(m, a, b, c, d, n_ribs, mat="paint", depth=1.5):
    """ribs across the back of a panel a-b-c-d (a-b bottom edge, d-c top edge)"""
    a, b, c, d = [np.asarray(p, dtype=float) for p in (a, b, c, d)]
    for k in range(n_ribs):
        t = (k + 0.5) / n_ribs
        p0, p1 = a + (b - a) * t, d + (c - d) * t
        K.tube([p0, p1], depth * 0.4, mat, n=4, m=m)


def iff_bar(m, x0, x1, y, z, mat="light_grey"):
    """the IFF interrogator antenna: a bar with a row of dipoles"""
    K.rbox((x0, y, z - 0.7), (x1, y + 1.6, z + 0.7), 0.4, mat, seg=1, m=m)
    for k in range(int((x1 - x0) / 3)):
        x = x0 + 1.5 + k * 3
        K.box((x - 0.15, y + 1.6, z - 0.9), (x + 0.15, y + 2.6, z - 0.6), "dark", m=m)


def cables(m, pts, r=0.35, mat="black"):
    K.tube(pts, r, mat, n=4, m=m)


def lattice_mast(m, x, z, y0, y1, half=2.2, mat="steel"):
    """a four-legged lattice mast with zig-zag bracing"""
    legs = [(x - half, z - half), (x + half, z - half), (x + half, z + half), (x - half, z + half)]
    for lx, lz in legs:
        K.cylinder("y", (lx, lz), 0.35, y0, y1, mat, n=4, m=m)
    y = y0
    step = half * 2.2
    k = 0
    while y + step <= y1 + 0.01:
        for i in range(4):
            a, b = legs[i], legs[(i + 1) % 4]
            lo, hi = (a, b) if k % 2 == 0 else (b, a)
            K.tube([(lo[0], y, lo[1]), (hi[0], y + step, hi[1])], 0.18, mat, n=4, m=m)
        y += step
        k += 1


def canister(m, x, y, z0, length, w, h, mat, bands=4, round_=False, r=None):
    """a launch canister pointing -Z: body, reinforcing bands, the frangible front cover, lifting lugs"""
    if round_:
        rr = r or w * 0.45
        K.cylinder("z", (x, y), rr, z0, z0 + length, mat, n=12, m=m)
        for k in range(bands):
            zb = z0 + 2 + k * (length - 4) / max(1, bands - 1)
            K.cylinder("z", (x, y), rr + 0.3, zb, zb + 1.0, mat, n=12, caps=(False, False), m=m)
        K.cylinder("z", (x, y), rr * 0.92, z0 - 0.3, z0, "dark", n=12, m=m)
        K.cylinder("z", (x, y), rr * 0.5, z0 - 0.4, z0 - 0.3, "white", n=8, caps=(True, False), m=m)
        return (x, y, z0 - 1)
    K.rbox((x - w / 2 + 0.3, y - h / 2 + 0.3, z0), (x + w / 2 - 0.3, y + h / 2 - 0.3, z0 + length), 0.5, mat, seg=1, m=m)
    for k in range(bands):
        zb = z0 + 2 + k * (length - 4) / max(1, bands - 1)
        K.box((x - w / 2 + 0.1, y - h / 2 + 0.1, zb), (x + w / 2 - 0.1, y + h / 2 - 0.1, zb + 1.0), mat, m=m)
    K.box((x - w / 2 + 1.0, y - h / 2 + 1.0, z0 - 0.15), (x + w / 2 - 1.0, y + h / 2 - 1.0, z0), "dark", m=m, faces="z")
    K.box((x - 0.3, y - h / 2 + 1.0, z0 - 0.2), (x + 0.3, y + h / 2 - 1.0, z0 - 0.15), "white", m=m, faces="z")
    K.box((x - w / 2 + 1.0, y - 0.3, z0 - 0.2), (x + w / 2 - 1.0, y + 0.3, z0 - 0.15), "white", m=m, faces="z")
    return (x, y, z0 - 1)


def barrel(m, x, y, z0, length, r, mat="gunmetal", brake=True, jacket=0.0):
    """a gun barrel pointing -Z from z0 + length to z0, a cooling jacket over its rear part, the muzzle brake"""
    K.cylinder("z", (x, y), r, z0, z0 + length, mat, n=10, m=m)
    if jacket > 0:
        K.cylinder("z", (x, y), r * 1.6, z0 + length * (1 - jacket), z0 + length, mat, n=10, m=m)
        for k in range(6):
            zz = z0 + length * (1 - jacket) + k * length * jacket / 6
            K.cylinder("z", (x, y), r * 1.75, zz, zz + 0.4, "dark", n=10, caps=(False, False), m=m)
    if brake:
        K.cylinder("z", (x, y), r * 1.5, z0 - 2.5, z0, mat, n=10, m=m)
        for s in (-1, 1):
            K.box((x + s * r * 1.5 - 0.15, y - r * 0.8, z0 - 2.0), (x + s * r * 1.5 + 0.15, y + r * 0.8, z0 - 0.6), "black", m=m)
        K.cylinder("z", (x, y), r * 0.6, z0 - 2.6, z0 - 2.4, "black", n=8, caps=(True, False), m=m)


def handwheel(m, x, y, z, r=1.6, axis="x"):
    K.torus(axis, (y, z) if axis == "x" else (x, y), x if axis == "x" else z, r, 0.2, "dark", n=10, k=4, m=m)
    K.cylinder(axis, (y, z) if axis == "x" else (x, y), 0.3, (x if axis == "x" else z) - 1.0, (x if axis == "x" else z), "steel", n=4, m=m)


def seat(m, x, y, z, mat="olive_dark", back="Z"):
    """a gunner's seat: pan, back rest, the post"""
    K.rbox((x - 1.6, y, z - 1.6), (x + 1.6, y + 0.8, z + 1.6), 0.3, "cushion", seg=1, m=m)
    bz = z + 1.6 if back == "Z" else z - 1.6
    K.rbox((x - 1.6, y + 0.8, min(bz, bz + (0.8 if back == "Z" else -0.8))), (x + 1.6, y + 4.0, max(bz, bz + (0.8 if back == "Z" else -0.8))),
           0.3, "cushion", seg=1, m=m)
    K.cylinder("y", (x, z), 0.4, y - 3.0, y, "steel", n=6, m=m)


# ------------------------------------------------------------------------------------------------ radars
def radar_mpq65():
    """AN/MPQ-65 (Patriot): semi-trailer, the big tilted phased-array face with its sidelobe arrays and IFF, the
    equipment housing behind it, a generator and cable reel on the deck"""
    base, head = Mesh(), Mesh()
    top = G.trailer(base, 34, 64, 12)
    G.generator(base, -5, top, 26, 5, top + 8, 37)
    G.cable_reel(base, 17, top + 3, 33, 2.6, 4)
    G.railing(base, -9, 25, -24, 40, top, 4.5, sides="xX")
    G.ladder(base, 25.5, 30, 0.5, top, 3.0, "z")
    G.turntable(base, top, 10)
    hy = top + 3
    tilt = math.radians(25)

    def face(px, py, dz=0.0):
        return v3(px, hy + 4 + py * math.cos(tilt), 2 + dz - py * math.sin(tilt))
    corners = [face(-7, 0), face(23, 0), face(23, 31), face(-7, 31)]
    array_face(head, corners, 3.0, "paint", "paint_dark", inset=0.6)
    octo = [face(8 + 11 * math.cos(math.radians(a + 22.5)), 16 + 11 * math.sin(math.radians(a + 22.5)), -1.75) for a in range(0, 360, 45)]
    K.plate(octo, 0.4, "array", m=head)
    K.tube(octo + [octo[0]], 0.3, "dark", n=4, caps=(False, False), m=head)
    for s in (-1, 1):
        K.plate([face(8 + s * 4 - 2.6, 1.3, -1.75), face(8 + s * 4 + 2.6, 1.3, -1.75), face(8 + s * 4 + 2.6, 4.6, -1.75), face(8 + s * 4 - 2.6, 4.6, -1.75)],
                0.4, "array", m=head)
        K.plate([face(8 + s * 13.2 - 1.3, 22, -1.75), face(8 + s * 13.2 + 1.3, 22, -1.75), face(8 + s * 13.2 + 1.3, 28, -1.75), face(8 + s * 13.2 - 1.3, 28, -1.75)],
                0.4, "array", m=head)
    K.plate([face(3, 28.2, -1.75), face(13, 28.2, -1.75), face(13, 30, -1.75), face(3, 30, -1.75)], 0.4, "dark", m=head)
    stiffeners(head, face(-6, 0, 1.6), face(22, 0, 1.6), face(22, 30, 1.6), face(-6, 30, 1.6), 5)
    # the support struts and the equipment housing behind the face
    for s in (-1, 1):
        K.tube([(8 + s * 12, hy + 1, 16), face(8 + s * 12, 18, 1.6)], 0.8, "steel", n=6, m=head)
    G.cabinet(head, (-4, hy, 8), (20, hy + 8, 20), "paint", "Z")
    for s in (-1, 1):
        G.cabinet(head, (8 + s * 9 - 3, hy, 20), (8 + s * 9 + 3, hy + 5, 23), "paint_dark", "Z", vents=True, handles=1)
    cables(head, [(2, hy + 0.5, 19), (0, hy - 1.5, 21), (-2, hy - 2.5, 26)])
    return finish("radar_mpq65", base, head, head_pivot=(8, hy, 8), label_y=hy + 36)


def radar_96l6():
    """96L6E (S-400): a 6x6 truck with the operators' shelter, a lattice mast and the rotating flat array on top"""
    base, head = Mesh(), Mesh()
    deck = G.truck(base, 34, 100, 16)
    G.shelter(base, -7, deck, -14, 23, deck + 16, 20)
    G.ladder(base, 24.0, 4, 0.5, deck + 16, 3.0, "z")
    G.railing(base, -7, 23, -14, 20, deck + 16, 4, sides="xXz")
    lattice_mast(base, 8, 30, deck, deck + 58, 2.4)
    for s in (-1, 1):
        K.tube([(8 + s * 13, deck, 46), (8 + s * 2.4, deck + 34, 32.4)], 0.6, "steel", n=5, m=base)
        G.ram(base, (8 + s * 6, deck + 1, 52), (8 + s * 2.4, deck + 22, 32.4), 0.8)
    cables(base, [(10.4, deck + 2, 27.6), (10.4, deck + 56, 27.6)], 0.4)
    hy = deck + 60
    K.cylinder("y", (8, 30), 4, hy - 2, hy, "steel", n=14, m=base)
    # the array: a big flat panel facing forward, its back frame and electronics boxes, the IFF bar on top
    array_face(head, [v3(-10, hy + 3, 26), v3(26, hy + 3, 26), v3(26, hy + 27, 26), v3(-10, hy + 27, 26)], 3.0, "paint", "array")
    stiffeners(head, v3(-9, hy + 3, 28.5), v3(25, hy + 3, 28.5), v3(25, hy + 27, 28.5), v3(-9, hy + 27, 28.5), 6)
    iff_bar(head, -12, 28, hy + 27.5, 26.5)
    for s in (-1, 1):
        K.tube([(8 + s * 14, hy + 4, 29), (8, hy + 2, 33)], 0.6, "steel", n=5, m=head)
        G.cabinet(head, (8 + s * 9 - 4, hy + 6, 28.5), (8 + s * 9 + 4, hy + 14, 31.5), "paint", "Z", handles=1)
    K.cylinder("y", (8, 30), 3, hy, hy + 3, "dark", n=12, m=head)
    return finish("radar_96l6", base, head, head_pivot=(8, hy, 30), label_y=hy + 40)


def radar_giraffe():
    """Giraffe AMB: a truck with the operators' shelter, its folding mast lifting the antenna high over the trees"""
    base, head = Mesh(), Mesh()
    deck = G.truck(base, 32, 84, 16, "sand")
    G.shelter(base, -6, deck, 0, 22, deck + 18, 38, "sand")
    G.ladder(base, 23, 18, 0.5, deck + 18, 3.0, "z")
    # the mast: lower and upper sections with their collars, the knuckle and the lifting ram
    K.tube([(8, deck + 18, 34), (8, deck + 46, 26)], 2.0, "steel", n=10, m=base)
    K.cylinder("y", (8, 26), 2.6, deck + 44, deck + 48, "sand", n=10, m=base)
    K.tube([(8, deck + 46, 26), (8, deck + 76, 26)], 1.6, "steel", n=10, m=base)
    for yy in (deck + 56, deck + 66):
        K.cylinder("y", (8, 26), 2.0, yy, yy + 1.2, "sand", n=10, m=base)
    G.ram(base, (8, deck + 18, 26), (8, deck + 38, 28), 1.0)
    cables(base, [(9.8, deck + 19, 34), (9.6, deck + 45, 27.5), (9.6, deck + 75, 27.5)], 0.35)
    hy = deck + 76
    array_face(head, [v3(-6, hy, 22), v3(22, hy, 22), v3(22, hy + 12, 22), v3(-6, hy + 12, 22)], 3.0, "sand", "array_sand")
    iff_bar(head, -7, 23, hy + 12.5, 23)
    G.cabinet(head, (2, hy + 2, 23.5), (14, hy + 9, 26), "sand", "Z", handles=1)
    K.cylinder("y", (8, 26), 2.5, hy - 2, hy + 1, "steel", n=10, m=head)
    return finish("radar_giraffe", base, head, head_pivot=(8, hy, 26), label_y=hy + 22)


def radar_sentinel():
    """AN/MPQ-64 Sentinel: a light trailer, the radar housing with its doors and vents, a fast spinning flat antenna
    and the IFF bar, the generator on the drawbar"""
    base, head = Mesh(), Mesh()
    top = G.trailer(base, 28, 46, 10)
    G.cabinet(base, (-2, top, -2), (18, top + 12, 20), "paint", "z")
    G.cabinet(base, (-2.6, top + 1, 2), (-2, top + 10, 16), "paint", "x", handles=1)
    G.generator(base, 1, top, -14, 15, top + 7, -4)
    G.whip(base, 17, top + 12, 19, 14)
    hy = top + 12
    G.turntable(base, hy, 6)
    array_face(head, [v3(-6, hy + 3, 6), v3(22, hy + 3, 6), v3(22, hy + 20, 6), v3(-6, hy + 20, 6)], 2.5, "paint", "array")
    stiffeners(head, v3(-5, hy + 3, 8), v3(21, hy + 3, 8), v3(21, hy + 20, 8), v3(-5, hy + 20, 8), 4)
    iff_bar(head, -8, 24, hy + 20.5, 7.2)
    G.cabinet(head, (3, hy + 3, 8.5), (13, hy + 9, 13), "paint", "Z", handles=1)
    return finish("radar_sentinel", base, head, head_pivot=(8, hy, 8), label_y=hy + 30)


def radar_elm2084():
    """EL/M-2084 (Iron Dome): a trailer with the large rotating phased-array panel and its electronics behind it"""
    base, head = Mesh(), Mesh()
    top = G.trailer(base, 36, 70, 12, mat="sand")
    G.generator(base, -7, top, 28, 3, top + 8, 40, "sand")
    G.cabinet(base, (15, top, 28), (24, top + 9, 40), "sand", "X")
    G.railing(base, -10, 26, -27, 43, top, 4.5, sides="xX")
    G.turntable(base, top, 10)
    hy = top + 3
    tilt = math.radians(12)
    pts = [v3(-12, hy + 4, 4), v3(28, hy + 4, 4), v3(28, hy + 4 + 26 * math.cos(tilt), 4 - 26 * math.sin(tilt)),
           v3(-12, hy + 4 + 26 * math.cos(tilt), 4 - 26 * math.sin(tilt))]
    array_face(head, pts, 3.0, "sand", "array_sand")
    back = [p + v3(0, 0, 1.6) for p in pts]
    stiffeners(head, back[0], back[1], back[2], back[3], 6, "sand")
    for s in (-1, 1):
        K.tube([(8 + s * 14, hy + 1, 15), (8 + s * 14, hy + 18, 3.5)], 0.8, "steel", n=6, m=head)
    G.cabinet(head, (-2, hy, 6), (18, hy + 7, 16), "sand", "Z")
    cables(head, [(16, hy + 0.5, 15), (17, hy - 2, 18)])
    return finish("radar_elm2084", base, head, head_pivot=(8, hy, 8), label_y=hy + 36)


def radar_p18():
    """P-18 (VHF): a van body truck, a tall mast held by guy wires, a big frame of Yagi antennas; long waves see
    stealth aircraft better"""
    base, head = Mesh(), Mesh()
    deck = G.truck(base, 32, 90, 16, "olive")
    G.shelter(base, -8, deck, -2, 24, deck + 22, 44, "olive")
    for z in (8, 20, 32):
        K.box((24.1, deck + 12, z), (24.2, deck + 17, z + 5), "glass_dark", m=base)
    G.ladder(base, 25, 2, 0.5, deck + 22, 3.0, "z")
    K.cylinder("y", (8, 20), 1.8, deck + 22, deck + 54, "steel", n=10, m=base)
    for yy in (deck + 32, deck + 42):
        K.cylinder("y", (8, 20), 2.2, yy, yy + 1.0, "olive", n=10, m=base)
    for dx, dz in ((-30, -10), (46, -10), (-30, 52), (46, 52)):
        K.tube([(8, deck + 50, 20), (8 + (dx - 8) * 0.8, 0.5, 20 + (dz - 20) * 0.8)], 0.08, "black", n=3, m=base)
        K.cylinder("y", (8 + (dx - 8) * 0.8, 20 + (dz - 20) * 0.8), 0.6, 0, 1.2, "steel", n=4, m=base)
    hy = deck + 54
    # the frame and two rows of Yagis pointing forward
    K.rbox((-22, hy + 2, 18), (38, hy + 4, 22), 0.4, "steel", seg=1, m=head)
    K.rbox((-22, hy + 16, 18), (38, hy + 18, 22), 0.4, "steel", seg=1, m=head)
    for x in (-22, 8, 38):
        K.box((x - 0.5, hy + 2, 19.5), (x + 0.5, hy + 18, 20.5), "steel", m=head)
    for k in range(8):
        x = -19 + k * 8
        for yy in (hy + 3, hy + 17):
            K.cylinder("z", (x, yy), 0.4, -6, 20, "light_grey", n=5, m=head)
            for j in range(5):
                z = -4 + j * 5
                K.cylinder("x", (yy, z), 0.25, x - 3.5 + j * 0.3, x + 3.5 - j * 0.3, "light_grey", n=4, m=head)
    K.cylinder("y", (8, 20), 3, hy - 1, hy + 2, "steel", n=10, m=head)
    cables(head, [(-19, hy + 3, 19), (8, hy + 1, 21)], 0.25)
    return finish("radar_p18", base, head, head_pivot=(8, hy, 20), label_y=hy + 28)


def radar_kupol():
    """9S18M1 Kupol (Buk): tracked chassis carrying the tall curved antenna, its feed and the IFF on top"""
    base, head = Mesh(), Mesh()
    h = G.tracked(base, 40, 96, 20)
    G.turntable(base, h, 9)
    hy = h + 3
    # the curved reflector: slices of a parabolic cylinder, tall and narrow, leaning back a little; the module grid on its face
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
            K.plate([a[j] + v3(0, 0, -0.55), a[j + 1] + v3(0, 0, -0.55), b[j + 1] + v3(0, 0, -0.55), b[j] + v3(0, 0, -0.55)], 0.1, "array", m=head)
    # rim tubes, back ribs, the feed on its boom
    for ring in (rows[0], rows[-1]):
        K.tube([p + v3(0, 0, 0.5) for p in ring], 0.4, "steel", n=4, caps=(False, False), m=head)
    for j in (0, 4, 8):
        K.tube([rows[k][j] + v3(0, 0, 0.8) for k in range(9)], 0.5, "steel", n=4, caps=(False, False), m=head)
    K.tube([(8, hy + 2, 14), (8, hy + 24, 10)], 1.0, "steel", n=6, m=head)
    K.cylinder("z", (8, hy + 24), 1.2, 0, 10, "dark", n=8, m=head)
    K.rbox((6.5, hy + 22.5, -1.5), (9.5, hy + 25.5, 0.2), 0.3, "dark", seg=1, m=head)
    iff_bar(head, -2, 18, hy + 6 + 8 * 4.2 + 1.0, 4 + 8 * 0.6)
    G.cabinet(head, (0, hy, 10), (16, hy + 6, 22), "paint", "Z")
    cables(head, [(14, hy + 0.5, 21), (14, hy + 6, 18), (10, hy + 20, 12)])
    return finish("radar_kupol", base, head, head_pivot=(8, hy, 8), label_y=hy + 50)


# ------------------------------------------------------------------------------------------------ launchers and guns
def sam_patriot():
    """M903 Patriot launcher: a semi-trailer, the launcher control station, a generator, and four big canisters in
    their frame raised on the arm"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    top = G.trailer(base, 34, 76, 12)
    G.generator(base, -6, top, -34, 6, top + 8, -24)
    G.cabinet(base, (14, top, -34), (24, top + 9, -24), "olive", "z")
    G.turntable(base, top, 11)
    hy = top + 3
    # the launcher mount: the pedestal, the trunnion brackets, the control box
    K.rbox((-4, hy, 2), (20, hy + 6, 22), 1.0, "olive", seg=1, m=head)
    for s in (-1, 1):
        K.plate([v3(8 + s * 12, hy, 4), v3(8 + s * 12, hy, 24), v3(8 + s * 12, hy + 9, 24), v3(8 + s * 12, hy + 6, 10)], 1.2, "olive", m=head)
    G.cabinet(head, (20.5, hy, 6), (24, hy + 5, 14), "olive_dark", "X", handles=1)
    cables(head, [(20, hy + 1, 13), (21, hy - 1, 18), (18, hy - 2.5, 26)])
    ay, az = hy + 8, 22
    mouths = []
    for c in range(2):
        for r in range(2):
            x, y = 8 - 5.5 + c * 11, ay + 5.5 + r * 11
            mouths.append(canister(arm, x, y, az - 66, 66, 11, 11, "olive", bands=5))
    # the canister frame: side rails, cross straps, the trunnion beam
    for s in (-1, 1):
        K.box((8 + s * 11.2 - 0.6, ay, az - 64), (8 + s * 11.2 + 0.6, ay + 22, az - 62), "steel", m=arm)
        K.box((8 + s * 11.2 - 0.6, ay, az - 4), (8 + s * 11.2 + 0.6, ay + 22, az - 2), "steel", m=arm)
        K.box((8 + s * 11.2 - 0.5, ay - 0.5, az - 64), (8 + s * 11.2 + 0.5, ay + 0.5, az), "steel", m=arm)
        K.box((8 + s * 11.2 - 0.5, ay + 21.5, az - 64), (8 + s * 11.2 + 0.5, ay + 22.5, az), "steel", m=arm)
        G.ram(arm, (8 + s * 9, ay - 0.5, az - 30), (8 + s * 9, ay - 5, az - 4), 0.9)
    K.rbox((-4, ay - 2, az - 6), (20, ay, az), 0.5, "steel", seg=1, m=arm)
    K.cylinder("x", (ay, az - 2), 1.2, -5, 21, "steel", n=8, m=arm)
    return finish("sam_patriot", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile", hy + 40)


def sam_s400():
    """5P85 (S-400): an 8x8 truck whose erector stands four long tubes upright to cold-launch the missiles"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    deck = G.truck(base, 34, 120, 16, axles=((12, 26), (-28, -14)))
    G.cabinet(base, (-9, deck, -18), (25, deck + 7, -12), "olive", "z")
    hy = deck
    # the erector's rear hinge brackets and the lifting rams' anchors
    K.rbox((-6, hy, 30), (22, hy + 4, 56), 0.5, "steel", seg=1, m=head)
    for s in (-1, 1):
        K.plate([v3(8 + s * 12, hy, 48), v3(8 + s * 12, hy, 58), v3(8 + s * 12, hy + 8, 58), v3(8 + s * 12, hy + 8, 52)], 1.2, "steel", m=head)
    ay, az = hy + 6, 56
    mouths = []
    for c in range(2):
        for r in range(2):
            x, y = 8 - 6 + c * 12, ay + 6 + r * 12
            mouths.append(canister(arm, x, y, az - 96, 96, 12, 12, "olive", bands=6, round_=True, r=5.2))
    # the erector frame under the tubes, the clamps round them, the twin rams
    K.rbox((-6, ay - 3, az - 96), (22, ay, az), 0.5, "steel", seg=1, m=arm)
    for k in range(4):
        zz = az - 90 + k * 26
        K.box((-6.5, ay, zz), (-5.5, ay + 25, zz + 2), "steel", m=arm)
        K.box((21.5, ay, zz), (22.5, ay + 25, zz + 2), "steel", m=arm)
        K.box((-6.5, ay + 24, zz), (22.5, ay + 25, zz + 2), "steel", m=arm)
    for s in (-1, 1):
        G.ram(arm, (8 + s * 8, ay - 3, az - 60), (8 + s * 8, ay - 9, az - 30), 1.2)
    return finish("sam_s400", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile_vertical", hy + 110)


def sam_nasams():
    """NASAMS: a trailer with a launcher of six AMRAAM canisters in a frame"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    top = G.trailer(base, 30, 56, 10, mat="sand")
    G.cabinet(base, (-6, top, 22), (4, top + 7, 30), "sand", "Z")
    G.turntable(base, top, 8)
    hy = top + 3
    K.rbox((-2, hy, 2), (18, hy + 5, 18), 1.0, "sand", seg=1, m=head)
    for s in (-1, 1):
        K.plate([v3(8 + s * 10.5, hy, 6), v3(8 + s * 10.5, hy, 19), v3(8 + s * 10.5, hy + 8, 19), v3(8 + s * 10.5, hy + 5, 10)], 1.0, "sand", m=head)
    ay, az = hy + 7, 18
    mouths = []
    for c in range(3):
        for r in range(2):
            x, y = 8 - 10.5 + c * 7 + 3.5, ay + 3.5 + r * 7
            mouths.append(canister(arm, x, y, az - 44, 44, 7, 7, "sand", bands=4))
    for s in (-1, 1):
        K.box((8 + s * 10.8 - 0.5, ay - 0.5, az - 43), (8 + s * 10.8 + 0.5, ay + 14.5, az - 41), "steel", m=arm)
        K.box((8 + s * 10.8 - 0.5, ay - 0.5, az - 4), (8 + s * 10.8 + 0.5, ay + 14.5, az - 2), "steel", m=arm)
    K.cylinder("x", (ay, az - 2), 1.0, -3.5, 19.5, "steel", n=8, m=arm)
    G.ram(arm, (8, ay - 0.5, az - 22), (8, ay - 5, az - 6), 0.8)
    return finish("sam_nasams", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile", hy + 30)


def sam_tamir():
    """Iron Dome launcher: a big box of twenty Tamir tubes on a turntable, fixed steep elevation"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    top = G.trailer(base, 36, 50, 10, mat="sand")
    G.cabinet(base, (-10, top, 22), (0, top + 8, 32), "sand", "Z")
    G.cable_reel(base, 18, top + 3, 27, 2.6, 4, "sand")
    G.turntable(base, top, 10)
    hy = top + 3
    K.rbox((-4, hy, 0), (20, hy + 6, 16), 1.0, "sand", seg=1, m=head)
    for s in (-1, 1):
        K.plate([v3(8 + s * 12.5, hy, 4), v3(8 + s * 12.5, hy, 17), v3(8 + s * 12.5, hy + 10, 17), v3(8 + s * 12.5, hy + 7, 8)], 1.0, "sand", m=head)
    ay, az = hy + 8, 16
    K.rbox((-12, ay, az - 40), (28, ay + 26, az), 1.0, "sand", seg=1, m=arm)
    # ribs round the box, lifting eyes, the cover hinge line and the cable boxes on the back
    for k in range(4):
        zz = az - 37 + k * 11
        K.box((-12.4, ay - 0.4, zz), (28.4, ay + 26.4, zz + 1.0), "sand_dark", m=arm, faces="xXyY")
    for x in (-10, 26):
        K.torus("z", (x, ay + 26.8), az - 20, 0.9, 0.25, "steel", n=8, k=4, m=arm)
    K.box((-12, ay + 25.6, az - 40.5), (28, ay + 26.4, az - 39.6), "dark", m=arm)
    G.cabinet(arm, (-6, ay + 4, az), (6, ay + 14, az + 3), "sand", "Z", handles=1)
    G.cabinet(arm, (10, ay + 4, az), (22, ay + 14, az + 3), "sand", "Z", handles=1)
    mouths = []
    for c in range(5):
        for r in range(4):
            x, y = -8 + c * 8, ay + 3.5 + r * 6.3
            K.cylinder("z", (x, y), 2.4, az - 40.2, az - 39.5, "black", n=8, caps=(True, False), m=arm)
            K.cylinder("z", (x, y), 2.7, az - 40.3, az - 40.1, "sand_dark", n=8, caps=(False, False), m=arm)
            mouths.append((x, y, az - 41))
    return finish("sam_tamir", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "missile_fixed", hy + 40)


def aa_gepard():
    """Gepard: tracked hull, a turret with a 35 mm gun on each side, the search radar turning on its back and the
    tracking radar dish in front, smoke dischargers"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    h = G.tracked(base, 40, 90, 20, skirts=True)
    hy = h
    K.rbox((-6, hy, -8), (22, hy + 14, 24), 2.0, "olive", seg=2, m=head)
    K.plate([v3(-6, hy + 2, -8.5), v3(22, hy + 2, -8.5), v3(20, hy + 13, -5), v3(-4, hy + 13, -5)], 1.2, "olive", m=head)
    # search radar on its mast at the back, the tracking radar dish at the front
    K.cylinder("y", (8, 20), 1.2, hy + 14, hy + 22, "steel", n=8, m=head)
    array_face(head, [v3(-2, hy + 22, 19), v3(18, hy + 22, 19), v3(18, hy + 28, 19), v3(-2, hy + 28, 19)], 1.2, "olive", "array")
    K.cylinder("z", (8, hy + 9), 3.6, -10.5, -9.2, "dark", n=14, m=head)
    K.sphere((8, hy + 9, -10.4), 3.3, 3.3, 1.2, "olive", n=14, rows=5, m=head)
    K.cylinder("z", (8, hy + 9), 0.4, -13, -10.5, "steel", n=6, m=head)
    # hatches, periscopes, smoke grenade launchers on both sides
    for x in (2, 14):
        K.cylinder("y", (x, 10), 2.2, hy + 14, hy + 14.6, "olive", n=10, m=head)
        K.box((x - 1, hy + 14.6, 7.2), (x + 1, hy + 15.6, 8.2), "glass_dark", m=head)
    for s in (-1, 1):
        for k in range(4):
            K.cylinder("z", (8 + s * 13.5, hy + 4 + k * 1.6), 0.6, -7, -4, "dark", n=6, m=head)
        G.cabinet(head, (8 + s * 14 - 1.5, hy, 12), (8 + s * 14 + 1.5, hy + 8, 22), "olive", "X" if s > 0 else "x", handles=1)
    ay, az = hy + 8, 4
    mouths = []
    for s in (-1, 1):
        x = 8 + s * 17
        K.rbox((x - 3, ay - 4, az - 4), (x + 3, ay + 4, az + 14), 1.0, "olive", seg=1, m=arm)
        K.box((x - 3.1, ay + 1, az - 2), (x + 3.1, ay + 3, az + 8), "grille", m=arm, faces="xX")
        barrel(arm, x, ay + 1, az - 40, 36, 1.0, jacket=0.25)
        mouths.append((x, ay + 1, az - 43))
        # the ammunition feed chute into the turret
        K.tube([(x - s * 3, ay - 1, az + 8), (x - s * 7, ay - 2, az + 10)], 1.0, "dark", n=6, m=arm)
    K.cylinder("x", (ay, az), 1.5, -9, 25, "steel", n=8, m=arm)
    return finish("aa_gepard", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "gun", hy + 40)


def aa_zu23():
    """ZU-23-2: a light carriage, wheels lifted and the three legs let down; the twin 23 mm on its cradle with the
    magazines, the two gunners' seats, the sight and the hand wheels"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    K.rbox((-6, 4, -6), (22, 8, 22), 1.0, "olive", seg=1, m=base)
    for s in (-1, 1):
        G.tyre(base, 8 + s * 16, 5, 8, 5, 3, s)
        K.tube([(8 + s * 10, 4, 8 + s * 12), (8 + s * 22, 0.5, 8 + s * 22)], 0.8, "steel", n=6, m=base)
        K.cylinder("y", (8 + s * 22, 8 + s * 22), 1.8, 0, 0.5, "dark", n=8, m=base)
    K.tube([(8, 4, -6), (8, 0.5, -20)], 0.8, "steel", n=6, m=base)
    K.cylinder("y", (8, -20), 1.8, 0, 0.5, "dark", n=8, m=base)
    hy = 8
    K.rbox((-2, hy, 0), (18, hy + 10, 16), 1.0, "olive", seg=1, m=head)
    K.rbox((-6, hy + 2, 10), (-2, hy + 6, 16), 0.5, "dark", seg=1, m=head)
    for s in (-1, 1):
        seat(head, 8 + s * 6.5, hy + 4, 15, back="Z")
        handwheel(head, 8 + s * 10.2, hy + 7, 9, 1.6, "x")
    K.box((2, hy + 10, 3), (4, hy + 15, 5), "dark", m=head)
    K.rbox((1.2, hy + 15, 2.5), (4.8, hy + 17, 5.5), 0.3, "dark", seg=1, m=head)
    K.box((1.6, hy + 15.5, 2.45), (4.4, hy + 16.6, 2.5), "glass", m=head, faces="z")
    ay, az = hy + 12, 6
    mouths = []
    for s in (-1, 1):
        x = 8 + s * 2.6
        K.rbox((x - 2, ay - 2.5, az - 2), (x + 2, ay + 2.5, az + 10), 0.6, "olive", seg=1, m=arm)
        barrel(arm, x, ay, az - 30, 28, 0.7, jacket=0.2)
        K.rbox((x - 1.5, ay + 2.5, az), (x + 1.5, ay + 8, az + 4), 0.4, "olive", seg=1, m=arm)
        # the magazine box on each side
        K.rbox((x + s * 2 - (1.6 if s < 0 else 0), ay - 3, az + 1), (x + s * 2 + (1.6 if s > 0 else 0), ay + 2, az + 9), 0.3, "olive_dark", seg=1, m=arm)
        mouths.append((x, ay, az - 33))
    return finish("aa_zu23", base, head, arm, (8, hy, 8), (8, ay, az), mouths, "gun", hy + 30)


def aa_bofors():
    """Bofors 40 mm L/70: a four-wheel carriage on its jacks, the gun shield, the seats and hand wheels, and the long
    barrel with its flash hider, the ammunition rack and the recuperators"""
    base, head, arm = Mesh(), Mesh(), Mesh()
    K.rbox((-10, 5, -14), (26, 10, 30), 1.0, "olive", seg=1, m=base)
    for zz in (-8, 24):
        for s in (-1, 1):
            G.tyre(base, 8 + s * 20, 5, zz, 5, 3, s)
    for zz in (-14, 30):
        for s in (-1, 1):
            G.outrigger(base, 8 + s * 16, 8 + s * 24, 7.5, zz, 0.0)
    K.tube([(8, 6, -14), (8, 4, -26)], 0.8, "steel", n=6, m=base)
    hy = 10
    G.turntable(base, hy, 9)
    K.rbox((-4, hy + 3, -2), (20, hy + 14, 20), 1.5, "olive", seg=1, m=head)
    K.plate([v3(-6, hy + 6, -4), v3(22, hy + 6, -4), v3(20, hy + 22, -2), v3(-4, hy + 22, -2)], 1.0, "olive", m=head)
    K.box((5.5, hy + 15, -4.8), (10.5, hy + 18, -3.4), "dark", m=head)
    for s in (-1, 1):
        seat(head, 8 + s * 9, hy + 10, 14, back="Z")
        handwheel(head, 8 + s * 12.4, hy + 12, 8, 1.8, "x")
    ay, az = hy + 16, 6
    K.rbox((4, ay - 4, az - 6), (12, ay + 4, az + 18), 1.0, "olive", seg=1, m=arm)
    barrel(arm, 8, ay + 1, az - 48, 44, 1.2, jacket=0.18)
    K.cylinder("z", (8, ay + 1), 1.9, az - 52, az - 48, "gunmetal", n=10, m=arm)
    for k in range(4):
        K.box((8 - 2.0, ay + 0.6, az - 51.5 + k), (8 + 2.0, ay + 1.4, az - 51.2 + k), "black", m=arm)
    for s in (-1, 1):
        K.cylinder("z", (8 + s * 2.4, ay - 2.2), 0.8, az - 14, az + 10, "steel", n=8, m=arm)
    # the four-round clip rack standing over the breech
    K.rbox((3, ay + 4, az - 2), (13, ay + 10, az + 6), 0.6, "olive", seg=1, m=arm)
    for k in range(4):
        K.cylinder("y", (5.0 + k * 2.0, az + 2), 0.7, ay + 10, ay + 13, "brass", n=6, m=arm)
    return finish("aa_bofors", base, head, arm, (8, hy, 8), (8, ay, az), [(8, ay + 1, az - 55)], "gun", hy + 34)


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
