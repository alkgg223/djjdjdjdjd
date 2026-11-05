"""Drones, model units 1/16 block, centred on (8, 0, 8), front -Z.

fpv     a kamikaze FPV quadcopter: carbon X frame with arm braces, four motors with their bells and screws, the flight
        controller stack and its standoffs, the battery strapped on top with the power lead, the camera cage in front,
        two antennas at the back, and an RPG warhead (PG-7 style, with its nose fuse and tail fins) zip-tied under it.
        One three-blade propeller is its own part, centred on (8, PROP_Y, 8): the renderer draws it on each motor in
        FPV_ROTORS and spins it.
shahed  Shahed-136 loitering munition: a cropped delta wing with elevons and panel lines, fins standing up and down at
        the tips, the fuselage pod with the warhead in the nose, the GPS hump, the air intake and the two-stroke engine
        cowl in the tail with its cooling fins and exhausts, the pusher propeller its own part (spun about the Z axis
        through PROP_SHAHED)."""
import math
import os

import numpy as np

import meshkit as K
from meshkit import Mesh, v3

PROP_SHAHED = (8.0, 6.4, 37.6)
PROP_Y = 7.0
FPV_ROTORS = [(8 + x, PROP_Y, 8 + z) for x, z in ((-5.5, -5.5), (5.5, -5.5), (-5.5, 5.5), (5.5, 5.5))]


def fpv():
    body, prop = Mesh(), Mesh()
    # carbon frame: two crossed arms (flat bars), the top and bottom plates, the braces between the arms
    for (ax, az), (bx, bz) in (((-5.5, -5.5), (5.5, 5.5)), ((5.5, -5.5), (-5.5, 5.5))):
        d = np.array([bx - ax, bz - az]) / math.hypot(bx - ax, bz - az)
        n = np.array([-d[1], d[0]]) * 0.55
        a, b = np.array([8 + ax, 8 + az]), np.array([8 + bx, 8 + bz])
        pts = [a - n - d * 0.9, b - n + d * 0.9, b + n + d * 0.9, a + n - d * 0.9]
        K.plate([v3(p[0], 5.3, p[1]) for p in pts], 0.35, "black", bevel=0.08, m=body)
    K.rbox((6.0, 5.0, 5.2), (10.0, 5.3, 10.8), 0.4, "black", seg=1, m=body)
    K.rbox((6.2, 6.3, 5.6), (9.8, 6.55, 10.4), 0.4, "black", seg=1, m=body)
    for x, z in ((6.5, 6.0), (9.5, 6.0), (6.5, 10.0), (9.5, 10.0)):
        K.cylinder("y", (x, z), 0.16, 5.3, 6.3, "steel", n=6, m=body)
    # flight controller / ESC stack between the plates, the status LED
    K.box((7.0, 5.45, 7.2), (9.0, 5.7, 9.2), "dark", m=body)
    K.box((7.1, 5.8, 7.3), (8.9, 6.05, 9.1), "olive", m=body)
    K.box((8.6, 6.05, 8.6), (8.85, 6.12, 8.85), "red", m=body)
    # motors: base, bell with cooling slots, the shaft and the prop nut
    for x, y, z in FPV_ROTORS:
        K.cylinder("y", (x, z), 1.05, 5.65, 5.95, "gunmetal", n=12, m=body)
        K.cylinder("y", (x, z), 0.95, 5.95, 6.75, "red", n=12, m=body)
        K.cylinder("y", (x, z), 0.98, 6.25, 6.4, "dark", n=12, caps=(False, False), m=body)
        K.cylinder("y", (x, z), 0.18, 6.75, 7.05, "steel", n=6, m=body)
        for k in range(4):
            a = math.radians(45 + 90 * k)
            K.cylinder("y", (x + math.cos(a) * 0.55, z + math.sin(a) * 0.55), 0.09, 5.6, 5.66, "steel", n=4, m=body)
        # the motor wire running in along the arm
        K.tube([(x + (8 - x) * 0.18, 5.62, z + (8 - z) * 0.18), (x + (8 - x) * 0.75, 5.62, z + (8 - z) * 0.75)], 0.12, "dark", n=4, m=body)
    # the propeller: three swept blades and the hub, around the centre
    K.cylinder("y", (8.0, 8.0), 0.45, PROP_Y - 0.12, PROP_Y + 0.12, "dark", n=10, m=prop)
    K.cylinder("y", (8.0, 8.0), 0.22, PROP_Y + 0.12, PROP_Y + 0.38, "steel", n=6, m=prop)
    for k in range(3):
        a0 = math.radians(k * 120)
        blade = []
        for j, (r, w) in enumerate(((0.4, 0.42), (1.3, 0.6), (2.3, 0.55), (3.0, 0.42), (3.4, 0.2))):
            a = a0 + 0.08 * j
            c, s = math.cos(a), math.sin(a)
            blade.append((8 + c * r - s * w * 0.3, 8 + s * r + c * w * 0.3, j))
        top = [v3(x, PROP_Y + 0.05 + 0.03 * j, z) for x, z, j in blade]
        bot = []
        for j, (r, w) in enumerate(((0.4, 0.42), (1.3, 0.6), (2.3, 0.55), (3.0, 0.42), (3.4, 0.2))):
            a = a0 + 0.08 * j
            c, s = math.cos(a), math.sin(a)
            bot.append(v3(8 + c * r + s * w * 0.7, PROP_Y - 0.05, 8 + s * r - c * w * 0.7))
        K.plate(top + list(reversed(bot)), 0.07, "red", m=prop)
    # battery (yellow LiPo with its label) strapped on the top plate, the lead and the XT60 plug
    K.rbox((6.3, 6.55, 6.2), (9.7, 8.1, 10.4), 0.35, "yellow", seg=1, m=body)
    K.box((6.25, 7.0, 7.0), (9.75, 7.7, 9.6), "dark", m=body, faces="xX")
    for z in (7.0, 9.4):
        K.box((6.2, 6.5, z), (9.8, 8.2, z + 0.5), "black", m=body)
    K.tube([(8.6, 7.4, 10.4), (9.2, 7.0, 11.2), (8.8, 6.6, 11.3)], 0.14, "red", n=4, m=body)
    K.tube([(7.4, 7.4, 10.4), (7.0, 7.0, 11.2), (7.6, 6.6, 11.3)], 0.14, "black", n=4, m=body)
    K.box((7.5, 6.3, 11.2), (8.9, 6.9, 11.8), "yellow", m=body)
    # camera cage and the camera tilted up, its lens
    for x in (6.9, 9.1):
        K.plate([v3(x, 5.3, 4.6), v3(x, 5.3, 6.2), v3(x, 7.0, 6.0), v3(x, 7.0, 4.9)], 0.18, "black", m=body)
    K.rbox((7.1, 5.6, 4.5), (8.9, 6.9, 5.9), 0.25, "dark", seg=1, m=body)
    K.cylinder("z", (8.0, 6.35), 0.5, 4.0, 4.5, "black", n=10, m=body)
    K.cylinder("z", (8.0, 6.35), 0.34, 3.9, 4.0, "lens", n=10, m=body)
    # the video and the control antennas at the back
    K.tube([(7.4, 6.5, 10.6), (6.9, 8.4, 12.3)], 0.1, "black", n=4, m=body)
    K.sphere((6.9, 8.6, 12.4), 0.35, 0.45, 0.35, "red", n=6, rows=3, m=body)
    K.tube([(8.8, 6.5, 10.6), (9.6, 7.6, 12.6)], 0.07, "black", n=4, m=body)
    # the RPG warhead taped underneath: the cone, the body, the nose fuse and the tail with its fins
    wy = 3.8
    K.cylinder("z", (8.0, wy), 1.45, 4.0, 8.4, "olive", n=14, m=body)
    K.cylinder("z", (8.0, wy), 1.45, 1.4, 4.0, "olive", n=14, r1=1.45, caps=(False, False), m=body)
    rings = [[v3(8 + math.cos(a) * r, wy + math.sin(a) * r, z) for a in np.linspace(0, 2 * math.pi, 14, endpoint=False)]
             for z, r in ((1.4, 1.45), (0.6, 1.1), (-0.2, 0.55), (-0.6, 0.3))]
    K.loft(rings, "olive", caps=(False, True), m=body)
    K.cylinder("z", (8.0, wy), 0.2, -1.4, -0.6, "steel", n=6, m=body)
    K.cylinder("z", (8.0, wy), 0.55, 8.4, 10.4, "gunmetal", n=10, m=body)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        c, s = math.cos(a), math.sin(a)
        K.plate([v3(8 + c * 0.5, wy + s * 0.5, 9.2), v3(8 + c * 0.5, wy + s * 0.5, 10.4), v3(8 + c * 1.5, wy + s * 1.5, 10.6),
                 v3(8 + c * 1.5, wy + s * 1.5, 9.8)], 0.08, "dark", m=body)
    for z in (4.6, 7.2):
        K.cylinder("z", (8.0, wy), 1.5, z, z + 0.5, "dark", n=14, caps=(False, False), m=body)
    # straps from the warhead up to the frame
    for z in (4.85, 7.45):
        for x in (6.4, 9.6):
            K.box((x - 0.12, wy + 1.0, z - 0.2), (x + 0.12, 5.05, z + 0.2), "dark", m=body)
    return {"body": body, "prop": prop}


def shahed():
    body, prop = Mesh(), Mesh()
    # cropped delta wing, its leading edge rounded; elevons cut into the trailing edge
    outline = [(8, -18), (24.5, 29), (25.5, 33.5), (8, 31.5), (-9.5, 33.5), (-8.5, 29)]
    K.plate([v3(x, 6.0, z) for x, z in outline], 0.9, "skin", bevel=0.35, m=body)
    for s in (-1, 1):
        x0, x1 = 8 + s * 6.5, 8 + s * 16.0
        K.box((min(x0, x1), 6.42, 29.4), (max(x0, x1), 6.5, 29.6), "skin_dark", m=body, faces="Y")
        K.box((min(x0, x1), 5.5, 29.4), (max(x0, x1), 5.58, 29.6), "skin_dark", m=body, faces="y")
        for xx in (x0, x1):
            K.box((xx - 0.1, 6.42, 29.5), (xx + 0.1, 6.5, 32.4), "skin_dark", m=body, faces="Y")
        # elevon actuator fairing under the wing
        xm = (x0 + x1) / 2
        K.rbox((xm - 0.5, 5.0, 25.5), (xm + 0.5, 5.6, 29.5), 0.25, "skin", seg=1, m=body)
        # panel lines on the top skin
        for k in (0.35, 0.7):
            K.box((8 + s * 3.0 - 0.06 if s > 0 else 8 + s * (3.0 + 17 * k), 6.43, 10 + 18 * k),
                  (8 + s * (3.0 + 17 * k) if s > 0 else 8 + s * 3.0 + 0.06, 6.46, 10 + 18 * k + 0.12), "skin_dark", m=body, faces="Y")
    # fuselage pod: warhead nose, body, tail cone round the engine
    rings = []
    for z, r in ((-21.5, 0.3), (-20.5, 1.2), (-18.5, 2.0), (-15, 2.5), (-10, 2.7), (14, 2.7), (24, 2.4), (31, 2.0), (35.5, 1.2)):
        rings.append([v3(8 + math.cos(a) * r, 6.4 + math.sin(a) * r * 0.85, z) for a in np.linspace(0, 2 * math.pi, 16, endpoint=False)])
    K.loft(rings, "skin", caps=(True, True), m=body)
    # warhead seam, the access hatches, the pitot in the nose
    K.cylinder("z", (8.0, 6.4), 2.72, -11.5, -11.2, "skin_dark", n=16, caps=(False, False), m=body)
    K.cylinder("z", (8.0, 6.4), 2.72, 2.0, 2.3, "skin_dark", n=16, caps=(False, False), m=body)
    K.box((6.6, 8.6, -6), (9.4, 8.72, -1), "skin_dark", m=body, faces="Y")
    K.cylinder("z", (8.0, 6.4), 0.12, -23.5, -21.4, "steel", n=4, m=body)
    # GPS / satcom hump on the back, the antenna blade underneath
    K.sphere((8.0, 8.6, 6.0), 1.3, 0.6, 2.0, "dark", n=10, rows=4, top_only=True, m=body)
    K.plate([v3(8, 4.2, 4), v3(8, 4.2, 7), v3(8, 2.8, 7.5), v3(8, 2.8, 6.2)], 0.15, "dark", m=body)
    # air intake scoop in front of the engine
    K.rbox((7.0, 8.6, 18.5), (9.0, 9.4, 23.0), 0.3, "skin", seg=1, m=body)
    K.box((7.2, 8.7, 18.4), (8.8, 9.25, 18.5), "black", m=body, faces="z")
    # fins standing up and down at the wing tips
    for x in (25.0, -9.0):
        K.plate([v3(x, 6.0, 26.5), v3(x, 6.0, 33.8), v3(x, 11.2, 35.2), v3(x, 11.2, 31.0)], 0.38, "skin", bevel=0.12, m=body)
        K.plate([v3(x, 6.0, 28), v3(x, 6.0, 33.8), v3(x, 3.3, 34.6), v3(x, 3.3, 31.6)], 0.38, "skin", bevel=0.12, m=body)
        K.box((x - 0.24, 6.4, 33.0), (x + 0.24, 10.8, 33.2), "skin_dark", m=body, faces="xX")
        K.sphere((x, 11.25, 33.0), 0.25, 0.2, 2.2, "skin", n=6, rows=3, m=body)
    # engine: cowl with cooling fins, the cylinder head, the exhaust pipes, the prop shaft housing
    K.rbox((6.0, 8.2, 25.0), (10.0, 10.0, 33.5), 0.7, "grey", seg=2, m=body)
    for z in np.linspace(26.0, 32.5, 8):
        K.box((6.3, 9.95, z), (9.7, 10.4, z + 0.22), "gunmetal", m=body)
    for s in (-1, 1):
        K.tube([(8 + s * 1.6, 8.6, 31.5), (8 + s * 2.6, 8.2, 33.5), (8 + s * 2.8, 7.6, 35.0)], 0.32, "burnt_metal", n=6, m=body)
        K.cylinder("z", (8 + s * 2.8, 7.6), 0.36, 35.0, 35.3, "dark", n=6, caps=(False, False), m=body)
    K.cylinder("z", (8.0, 6.4), 0.75, 35.0, 37.0, "gunmetal", n=10, m=body)
    # the two-blade pusher propeller (wooden, with a twist) and the spinner
    px, py, pz = PROP_SHAHED
    for a in (90, 270):
        r = math.radians(a)
        c, s = math.cos(r), math.sin(r)
        pts = []
        for rad, w in ((0.6, 0.7), (2.5, 0.95), (4.8, 0.8), (6.6, 0.5), (7.0, 0.25)):
            pts.append((rad, w))
        top = [v3(px + c * rr - s * w, py + s * rr + c * w, pz + 0.25) for rr, w in pts]
        bot = [v3(px + c * rr + s * w * 0.8, py + s * rr - c * w * 0.8, pz - 0.15) for rr, w in reversed(pts)]
        K.plate(top + bot, 0.2, "wood", m=prop)
    K.sphere((px, py, pz + 0.6), 0.9, 0.9, 1.2, "dark", n=10, rows=4, m=prop)
    K.cylinder("z", (px, py), 0.95, pz - 0.4, pz + 0.6, "gunmetal", n=10, m=prop)
    return {"body": body, "prop": prop}


DRONES = {"fpv": fpv, "shahed": shahed}

# ------------------------------------------------------------------------------------------------ 3.0 drones
PROP_RECON = 5.6
RECON_ROTORS = [(8 + x, PROP_RECON, 8 + z) for x, z in ((-5.2, -4.4), (5.2, -4.4), (-5.2, 4.6), (5.2, 4.6))]
PROP_BOMBER = 8.3
BOMBER_ROTORS = [(8 + 10.5 * math.sin(math.radians(30 + 60 * k)), PROP_BOMBER, 8 - 10.5 * math.cos(math.radians(30 + 60 * k))) for k in range(6)]
BOMB_SLOTS = [(5.4, 8.0), (7.1, 8.0), (8.9, 8.0), (10.6, 8.0)]
PROP_SURVEIL = (8.0, 6.0, -11.6)
PROP_LANCET = (8.0, 4.0, 12.9)


def two_blade(c, r, w, axis, mat, m, thick=0.08, twist=0.25):
    """a two-blade prop around c: blades along x (axis y) or along y (axis z)"""
    cx, cy, cz = c
    for sgn in (-1, 1):
        pts = [(0.35, w * 0.7), (r * 0.35, w), (r * 0.75, w * 0.85), (r, w * 0.35)]
        top, bot = [], []
        for rr, ww in pts:
            if axis == "y":
                top.append(v3(cx + sgn * rr, cy + twist * 0.3, cz - sgn * ww * 0.5))
                bot.append(v3(cx + sgn * rr, cy - twist * 0.3, cz + sgn * ww * 0.5))
            else:
                top.append(v3(cx - sgn * ww * 0.5, cy + sgn * rr, cz + twist * 0.3))
                bot.append(v3(cx + sgn * ww * 0.5, cy + sgn * rr, cz - twist * 0.3))
        K.plate(top + list(reversed(bot)), thick, mat, m=m)


def recon():
    """a folding military quad: grey shell, four arms to the motors, the gimbal camera under the nose, the legs"""
    body, prop = Mesh(), Mesh()
    rings = []
    for z, w, h in ((4.6, 1.2, 0.7), (5.2, 2.2, 1.2), (6.5, 2.6, 1.5), (9.5, 2.7, 1.6), (11.2, 2.2, 1.2), (11.8, 1.4, 0.7)):
        rings.append([v3(8 + math.cos(a) * w * 0.65, 3.9 + math.sin(a) * h * 0.6, z) for a in np.linspace(0, 2 * math.pi, 14, endpoint=False)])
    K.loft(rings, "grey", caps=(True, True), m=body)
    K.rbox((6.6, 4.6, 6.6), (9.4, 5.1, 10.4), 0.3, "dark", seg=1, m=body)
    K.box((7.4, 5.1, 7.4), (8.6, 5.25, 8.6), "gunmetal", m=body)
    for x, y, z in RECON_ROTORS:
        K.tube([(8 + (x - 8) * 0.25, 4.2, 8 + (z - 8) * 0.25), (x, 4.75, z)], 0.38, "grey", n=6, m=body)
        K.cylinder("y", (x, z), 0.8, 4.5, 4.85, "gunmetal", n=10, m=body)
        K.cylinder("y", (x, z), 0.72, 4.85, 5.4, "dark", n=10, m=body)
        K.cylinder("y", (x, z), 0.14, 5.4, 5.6, "steel", n=6, m=body)
        if z < 8:
            K.tube([(x, 4.5, z), (x + (8 - x) * 0.1, 3.2, z - 0.2)], 0.12, "dark", n=4, m=body)
        else:
            K.tube([(x + (8 - x) * 0.3, 3.9, z - 0.6), (x + (8 - x) * 0.3, 3.0, z - 0.4)], 0.12, "dark", n=4, m=body)
        K.cylinder("y", (x, z), 0.3, 4.8, 4.9, "red_light" if x < 8 else "green_mark", n=6, m=body)
    # gimbal: the yoke and the camera ball with two lenses (day and thermal)
    K.box((7.7, 3.0, 4.9), (8.3, 3.4, 5.6), "dark", m=body)
    K.sphere((8.0, 2.5, 5.2), 0.95, 0.9, 0.95, "gunmetal", n=12, rows=6, m=body)
    K.cylinder("z", (7.7, 2.5), 0.32, 4.05, 4.3, "lens", n=10, m=body)
    K.cylinder("z", (8.45, 2.55), 0.2, 4.15, 4.35, "glass_dark", n=8, m=body)
    two_blade((8.0, PROP_RECON, 8.0), 3.6, 0.75, "y", "dark", prop)
    K.cylinder("y", (8.0, 8.0), 0.3, PROP_RECON - 0.1, PROP_RECON + 0.15, "gunmetal", n=8, m=prop)
    return {"body": body, "prop": prop}


def bomber():
    """a heavy hexacopter bomber: six carbon arms, big props, the hub with its battery and GPS mast, landing skids, a
    thermal camera, and the drop rack under the hub (the bombs are their own part, drawn in each loaded slot)"""
    body, prop, bomb = Mesh(), Mesh(), Mesh()
    hexa = [(8 + 3.4 * math.sin(math.radians(60 * k)), 8 - 3.4 * math.cos(math.radians(60 * k))) for k in range(6)]
    K.prism(hexa, "y", 5.0, 7.2, "dark", bevel=0.25, m=body)
    K.sphere((8.0, 7.2, 8.0), 2.8, 1.1, 2.8, "black", n=12, rows=4, top_only=True, m=body)
    K.rbox((6.4, 8.0, 6.0), (9.6, 9.4, 10.2), 0.35, "olive_dark", seg=1, m=body)
    K.box((6.35, 8.4, 7.0), (9.65, 9.0, 9.2), "black", m=body, faces="xX")
    K.cylinder("y", (8.0, 10.6), 0.12, 8.2, 11.6, "dark", n=4, m=body)
    K.cylinder("y", (8.0, 10.6), 0.9, 11.6, 12.1, "white", n=10, m=body)
    for x, y, z in BOMBER_ROTORS:
        K.tube([(8 + (x - 8) * 0.28, 6.4, 8 + (z - 8) * 0.28), (x, 7.2, z)], 0.42, "black", n=6, m=body)
        K.cylinder("y", (x, z), 1.2, 6.9, 7.3, "gunmetal", n=12, m=body)
        K.cylinder("y", (x, z), 1.05, 7.3, 8.1, "dark", n=12, m=body)
        K.cylinder("y", (x, z), 0.2, 8.1, 8.3, "steel", n=6, m=body)
        # ESC under the arm, the motor lead
        K.box((x + (8 - x) * 0.35 - 0.5, 6.0, z + (8 - z) * 0.35 - 0.5), (x + (8 - x) * 0.35 + 0.5, 6.5, z + (8 - z) * 0.35 + 0.5), "gunmetal", m=body)
    # skids
    for sx in (-1, 1):
        x = 8 + sx * 3.8
        K.tube([(8 + sx * 2.4, 5.0, 6.0), (x, 0.6, 5.4)], 0.2, "black", n=5, m=body)
        K.tube([(8 + sx * 2.4, 5.0, 10.0), (x, 0.6, 10.6)], 0.2, "black", n=5, m=body)
        K.tube([(x, 0.4, 3.6), (x, 0.4, 12.4)], 0.28, "black", n=6, m=body)
    # thermal camera in front, under the hub
    K.rbox((7.1, 3.6, 3.9), (8.9, 4.9, 5.4), 0.25, "gunmetal", seg=1, m=body)
    K.cylinder("z", (8.0, 4.25), 0.45, 3.6, 3.9, "lens", n=10, m=body)
    K.box((7.8, 4.9, 4.4), (8.2, 5.1, 4.9), "dark", m=body)
    # drop rack: the rail, four release servos
    K.box((4.6, 4.3, 7.6), (11.4, 4.7, 8.4), "gunmetal", m=body)
    for x, z in BOMB_SLOTS:
        K.box((x - 0.45, 3.8, z - 0.5), (x + 0.45, 4.3, z + 0.5), "black", m=body)
    for x in (6.0, 10.0):
        K.box((x - 0.2, 4.7, 7.8), (x + 0.2, 5.0, 8.2), "dark", m=body)
    # one bomb, nose down, centred on (8, _, 8): a 40 mm / VOG-style body with a printed tail and fins
    rings = []
    for y, r in ((0.6, 0.15), (0.9, 0.45), (1.3, 0.62), (2.4, 0.62), (3.0, 0.42), (3.6, 0.3)):
        rings.append([v3(8 + math.cos(a) * r, y, 8 + math.sin(a) * r) for a in np.linspace(0, 2 * math.pi, 10, endpoint=False)])
    K.loft(rings, "olive", caps=(True, True), m=bomb)
    K.cylinder("y", (8.0, 8.0), 0.64, 1.7, 1.9, "brass", n=10, caps=(False, False), m=bomb)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        c, s = math.cos(a), math.sin(a)
        K.plate([v3(8 + c * 0.25, 3.0, 8 + s * 0.25), v3(8 + c * 0.25, 3.8, 8 + s * 0.25), v3(8 + c * 0.75, 3.8, 8 + s * 0.75),
                 v3(8 + c * 0.75, 3.2, 8 + s * 0.75)], 0.06, "white", m=bomb)
    two_blade((8.0, PROP_BOMBER, 8.0), 5.6, 1.1, "y", "black", prop, thick=0.1)
    K.cylinder("y", (8.0, 8.0), 0.45, PROP_BOMBER - 0.15, PROP_BOMBER + 0.2, "gunmetal", n=8, m=prop)
    return {"body": body, "prop": prop, "bomb": bomb}


def surveil():
    """an Orlan-style surveillance plane: the pod fuselage, the high straight wing with its struts-free root fairing,
    the tail on a boom, the camera ball under the belly, the GPS puck, the tractor prop at the nose"""
    body, prop = Mesh(), Mesh()
    rings = []
    for z, r in ((-11.2, 0.8), (-10.2, 1.6), (-8, 2.1), (-3, 2.3), (3, 2.2), (7, 1.5), (11, 0.9), (17, 0.7), (19, 0.4)):
        rings.append([v3(8 + math.cos(a) * r, 6.0 + math.sin(a) * r * 0.9, z) for a in np.linspace(0, 2 * math.pi, 14, endpoint=False)])
    K.loft(rings, "light_grey", caps=(True, True), m=body)
    # the wing: a long straight plank with rounded tips and a slight taper
    for s in (-1, 1):
        root, tip = 8 + s * 1.6, 8 + s * 24.0
        K.plate([v3(root, 8.1, -3.4), v3(tip, 8.5, -2.8), v3(tip + s * 0.6, 8.5, -1.2), v3(tip, 8.5, 0.6), v3(root, 8.1, 1.4)],
                0.5, "light_grey", bevel=0.2, m=body)
        # aileron lines and the wing-tip lights
        K.box((min(8 + s * 12, 8 + s * 22), 8.62, 0.0), (max(8 + s * 12, 8 + s * 22), 8.7, 0.15), "grey", m=body, faces="Y")
        K.cylinder("x", (8.5, -1.2), 0.25, tip + s * 0.6 - 0.2 if s > 0 else tip + s * 0.6 - 0.1,
                   tip + s * 0.6 + 0.1 if s > 0 else tip + s * 0.6 + 0.2, "red_light" if s < 0 else "green_mark", n=6, m=body)
    K.rbox((6.4, 7.6, -4.2), (9.6, 8.6, 2.2), 0.4, "light_grey", seg=1, m=body)
    # tail: the tailplane and the fin at the end of the boom
    K.plate([v3(8 - 6.5, 6.4, 15.4), v3(8 + 6.5, 6.4, 15.4), v3(8 + 6.0, 6.4, 18.6), v3(8 - 6.0, 6.4, 18.6)], 0.3, "light_grey", bevel=0.1, m=body)
    K.plate([v3(8, 6.4, 14.6), v3(8, 6.4, 19.0), v3(8, 11.0, 19.2), v3(8, 11.0, 17.6)], 0.32, "light_grey", bevel=0.1, m=body)
    K.box((7.9, 6.9, 17.0), (8.1, 10.6, 17.2), "grey", m=body, faces="xX")
    # camera ball and its fairing under the belly, the GPS puck, the antennae, the skid
    K.cylinder("y", (8.0, -1.0), 1.2, 3.6, 4.4, "grey", n=12, m=body)
    K.sphere((8.0, 3.2, -1.0), 1.15, 1.1, 1.15, "gunmetal", n=12, rows=6, m=body)
    K.cylinder("z", (8.0, 3.1), 0.38, -2.3, -2.1, "lens", n=10, m=body)
    K.cylinder("y", (8.0, 4.5), 0.8, 8.5, 8.85, "white", n=10, m=body)
    K.tube([(8, 6.2, 8.0), (8, 3.4, 9.2)], 0.08, "dark", n=4, m=body)
    K.tube([(8, 7.5, 6.0), (8, 9.6, 7.4)], 0.07, "dark", n=4, m=body)
    # engine cowl and exhaust at the nose
    K.cylinder("z", (8.0, 6.0), 1.0, -11.2, -10.6, "gunmetal", n=12, m=body)
    K.tube([(9.4, 5.0, -9.4), (10.2, 4.6, -7.0)], 0.22, "burnt_metal", n=5, m=body)
    two_blade(PROP_SURVEIL, 4.6, 0.8, "z", "black", prop)
    K.sphere((PROP_SURVEIL[0], PROP_SURVEIL[1], PROP_SURVEIL[2] - 0.5), 0.8, 0.8, 1.0, "dark", n=10, rows=4, m=prop)
    return {"body": body, "prop": prop}


def lancet():
    """a Lancet-style loitering munition: slim body, two X-wing sets, the camera nose, the pusher prop at the tail"""
    body, prop = Mesh(), Mesh()
    rings = []
    for z, r in ((-12.6, 0.25), (-12.2, 0.8), (-11.2, 1.15), (-8, 1.25), (8, 1.2), (11.5, 0.95), (12.4, 0.6)):
        rings.append([v3(8 + math.cos(a) * r, 4.0 + math.sin(a) * r, z) for a in np.linspace(0, 2 * math.pi, 14, endpoint=False)])
    K.loft(rings, "olive_dark", caps=(True, True), m=body)
    K.cylinder("z", (8.0, 4.0), 0.55, -12.8, -12.4, "lens", n=10, m=body)
    K.cylinder("z", (8.0, 4.0), 1.27, -7.2, -6.9, "dark", n=14, caps=(False, False), m=body)
    K.cylinder("z", (8.0, 4.0), 1.22, 3.0, 3.3, "dark", n=14, caps=(False, False), m=body)
    for (z0, z1, span, chord_tip) in ((-6.2, -1.0, 6.2, 2.4), (4.8, 11.0, 7.2, 3.0)):
        for k in range(4):
            a = math.radians(45 + 90 * k)
            c, s = math.cos(a), math.sin(a)
            p0 = (8 + c * 1.1, 4.0 + s * 1.1)
            p1 = (8 + c * span, 4.0 + s * span)
            K.plate([v3(p0[0], p0[1], z0), v3(p0[0], p0[1], z1), v3(p1[0], p1[1], z1), v3(p1[0], p1[1], z1 - chord_tip)], 0.18,
                    "olive_dark", bevel=0.05, m=body)
    two_blade(PROP_LANCET, 3.2, 0.7, "z", "black", prop)
    K.sphere((PROP_LANCET[0], PROP_LANCET[1], PROP_LANCET[2] + 0.3), 0.55, 0.55, 0.7, "dark", n=8, rows=4, m=prop)
    return {"body": body, "prop": prop}


DRONES.update({"recon": recon, "bomber": bomber, "surveil": surveil, "lancet": lancet})


if __name__ == "__main__":
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    import shutil
    old = os.path.join(RES, "models", "entity", "drone", "fpv")
    for ext in ("json", "obj", "mtl"):
        p = os.path.join(old, "props." + ext)
        if os.path.exists(p):
            os.remove(p)
    for k, fn in DRONES.items():
        parts = fn()
        for n, mesh in parts.items():
            K.write_part(mesh, "entity/drone/" + k, n, RES)
        print(k, sum(p.quad_count() for p in parts.values()))
