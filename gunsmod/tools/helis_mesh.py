"""Smooth helicopter meshes: fuselages lofted through rounded cross sections, glass canopies, airfoil fins, wings and
blades. Points the game uses (rotor hubs, tail rotor, chin gun, pods, rails, seats) come from the GEO tables."""
import math

import numpy as np

import armor as AR
import armor2 as A2
import meshkit as K
import vehmesh as V
from meshkit import Mesh, v3


def stations_loft(m, stations, mat, n=20, cx=8.0, caps=(True, True)):
    """a fuselage through cross sections (z, centre y, half width, half height, squareness)"""
    rings = []
    for z, cy, hw, hh, e in stations:
        rings.append([v3(cx + x, cy + y, z) for x, y in K.superellipse(max(hw, 0.02), max(hh, 0.02), e, n)])
    return K.loft(rings, mat, caps=caps, m=m)


def foil(m, le_root, le_tip, chord_root, chord_tip, thick, mat, chord_dir=(0, 0, 1), thick_dir=None, n=10):
    """a fin, wing or blade: airfoil sections at the root and the tip, leading edges given, chord along chord_dir"""
    c = K.norm(np.asarray(chord_dir, dtype=float))
    span = np.asarray(le_tip, dtype=float) - np.asarray(le_root, dtype=float)
    t = np.cross(span, c) if thick_dir is None else np.asarray(thick_dir, dtype=float)
    t = K.norm(t)
    rings = []
    for le, ch in ((le_root, chord_root), (le_tip, chord_tip)):
        le = np.asarray(le, dtype=float)
        rings.append([le + c * u + t * v for u, v in K.airfoil(ch, thick, n)])
    return K.loft(rings, mat, m=m)


def pod(m, x, y, z0, z1, r, mat="olive", tubes=7):
    """rocket pod: rounded tail, flat face with the tube mouths at the front (z0)"""
    K.cylinder("z", (x, y), r, z0, z1 - r * 0.8, mat, n=14, bevel=0.12, caps=(True, False), m=m)
    K.cylinder("z", (x, y), r, z1 - r * 0.8, z1, mat, n=14, r1=r * 0.35, caps=(False, True), m=m)
    for k in range(tubes):
        if k == 0:
            dx = dy = 0.0
        else:
            a = math.radians(k * 360.0 / (tubes - 1))
            dx, dy = math.cos(a) * r * 0.6, math.sin(a) * r * 0.6
        K.cylinder("z", (x + dx, y + dy), r * 0.2, z0 - 0.01, z0 - 0.03, "black", n=6, caps=(False, True), m=m)


def missile(m, x, y, z0, length, r, mat="olive"):
    """a guided missile on a rail: body, rounded seeker head at the front (z0), fins at the back"""
    K.sphere((x, y, z0 + r), r, r, r * 1.4, "lens", n=10, rows=6, m=m)
    K.cylinder("z", (x, y), r, z0 + r, z0 + length, mat, n=10, caps=(False, True), m=m)
    for a in (0, 90):
        sub = K.box((x - r * 2.0, y - 0.03, z0 + length - 0.8), (x + r * 2.0, y + 0.03, z0 + length - 0.1), mat)
        sub.rotate("z", a, (x, y, 0))
        m.extend(sub)


def wheel_gear(m, x, z, top, r, w=0.5):
    """a landing gear leg with its wheel touching the ground"""
    K.tube([(x, top, z), (x, r + 0.2, z)], 0.14, "steel", n=6, m=m)
    K.cylinder("x", (r, z), r, x - w / 2, x + w / 2, "rubber", n=12, bevel=0.1, m=m)
    K.cylinder("x", (r, z), r * 0.55, x - w / 2 - 0.03, x + w / 2 + 0.03, "light_grey", n=10, m=m)


def frame_lines(m, rings_idx_pts, r=0.08, mat="dark"):
    for pts in rings_idx_pts:
        K.tube(pts, r, mat, n=5, m=m)


def blade_part(R, chord, hub_r, tip_mat="yellow", thick=0.12):
    """one main rotor blade along +X from the hub at (8, 8, 8); the renderer copies it round"""
    m = Mesh()
    foil(m, (8 + hub_r, 8.0, 8 - chord / 2), (8 + R - 1.0, 8.0, 8 - chord / 2 + 0.05), chord, chord * 0.92, thick, "dark",
         chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    foil(m, (8 + R - 1.0, 8.0, 8 - chord / 2 + 0.05), (8 + R, 8.0, 8 - chord / 2 + 0.2), chord * 0.92, chord * 0.55, thick, tip_mat,
         chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    K.rbox((8 + hub_r - 0.4, 7.85, 7.75), (8 + hub_r + 0.7, 8.15, 8.25), 0.08, "steel", seg=1, m=m)
    return m


def hub_part(r=0.8):
    m = Mesh()
    K.cylinder("y", (8, 8), r, 7.7, 8.3, "steel", n=12, bevel=0.1, m=m)
    K.cylinder("y", (8, 8), 0.3, 8.3, 8.75, "bright", n=8, bevel=0.06, m=m)
    for k in range(4):
        sub = K.box((8 + r * 0.6, 7.95, 7.95), (8 + r + 0.3, 8.1, 8.05), "gunmetal")
        sub.rotate("y", k * 90 + 45, (8, 8, 8))
        m.extend(sub)
    return m


def tail_parts(R, chord=0.4):
    """tail rotor: hub along X at (8, 8, 8) and one blade reaching along +Z (turned round X by the renderer)"""
    hub = Mesh()
    K.cylinder("x", (8, 8), 0.3, 7.75, 8.35, "steel", n=10, bevel=0.06, m=hub)
    K.sphere((8.4, 8, 8), 0.12, 0.2, 0.2, "bright", n=8, rows=4, m=hub)
    blade = Mesh()
    foil(blade, (7.85, 8 - chord / 2, 8.3), (7.85, 8 - chord / 2 + 0.05, 8 + R), chord, chord * 0.8, 0.12, "dark",
         chord_dir=(0, 1, 0), thick_dir=(1, 0, 0))
    K.box((7.82, 8 - chord / 2 + 0.04, 8 + R - 0.4), (7.88, 8 + chord / 2 - 0.02, 8 + R), "white", m=blade)
    return hub, blade


def canopy(m, stations, frames_at, n=16):
    """glass canopy through rounded sections (the lower half sinks into the fuselage), with frame hoops and a spine"""
    rings = [[v3(8 + x, cy + y, z) for x, y in K.superellipse(max(hw, 0.02), max(hh, 0.02), 2.4, n)] for z, cy, hw, hh in stations]
    K.loft(rings, "glass_dark", m=m)
    for z in frames_at:
        # find the section at this station by interpolation
        for i in range(len(stations) - 1):
            z0, z1 = stations[i][0], stations[i + 1][0]
            if z0 <= z <= z1:
                t = (z - z0) / max(z1 - z0, 1e-6)
                cy = stations[i][1] + (stations[i + 1][1] - stations[i][1]) * t
                hw = stations[i][2] + (stations[i + 1][2] - stations[i][2]) * t
                hh = stations[i][3] + (stations[i + 1][3] - stations[i][3]) * t
                pts = [v3(8 + x * 1.02, cy + y * 1.02, z) for x, y in K.superellipse(hw, hh, 2.4, 16) if y >= -0.05]
                pts.sort(key=lambda p: p[0])
                K.tube(pts, 0.07, "dark", n=5, m=m)
                break
    # the spine along the top
    top = [v3(8, cy + hh * 1.02, z) for z, cy, hw, hh in stations]
    K.tube(top, 0.06, "dark", n=5, m=m)


# ============================================================================================ AH-64 Apache
def apache():
    A2.apache()
    g = A2.GEO["apache"]
    body, chin = Mesh(), Mesh()
    stations_loft(body, [(-13.2, 3.4, 0.9, 0.8, 2.4), (-12.6, 3.6, 1.45, 1.25, 2.6), (-11.0, 3.7, 1.75, 1.5, 2.9),
                         (-7.6, 3.9, 1.8, 1.75, 3.0), (-5.0, 4.15, 1.95, 1.95, 3.0), (-2.4, 4.3, 2.2, 2.3, 3.0),
                         (3.0, 4.4, 2.25, 2.4, 3.0), (7.6, 4.6, 1.8, 2.0, 2.8), (10.0, 4.6, 1.1, 1.2, 2.5),
                         (18.0, 4.6, 0.85, 0.95, 2.4), (26.2, 4.7, 0.62, 0.72, 2.3), (27.8, 4.9, 0.35, 0.45, 2.2)], "paint", n=20)
    canopy(body, [(-12.6, 4.9, 1.0, 0.3), (-11.8, 5.15, 1.55, 0.95), (-9.8, 5.25, 1.65, 1.4), (-8.0, 5.25, 1.65, 1.35),
                  (-7.7, 5.45, 1.7, 1.7), (-5.8, 5.65, 1.75, 2.15), (-3.9, 5.6, 1.7, 1.95), (-3.2, 5.5, 1.3, 1.1)],
           frames_at=(-11.6, -9.0, -7.75, -5.6, -3.6))
    # TADS / PNVS nose turret
    K.rbox((6.75, 2.5, -14.6), (9.25, 4.1, -12.6), 0.45, "dark", seg=1, m=body)
    for x in (7.0, 8.2):
        K.box((x, 2.8, -14.62), (x + 0.8, 3.6, -14.64), "lens", m=body)
    K.cylinder("y", (8.0, -13.5), 0.55, 4.0, 4.9, "dark", n=10, bevel=0.1, m=body)
    K.box((7.6, 4.3, -14.03), (8.4, 4.7, -14.05), "lens", m=body)
    # mast fairing, mast, Longbow radar dome above the rotor
    K.rbox((6.4, 6.3, -2.4), (9.6, 8.2, 7.0), 0.8, "paint", seg=2, m=body)
    K.cylinder("y", (8.0, 2.8), 0.5, 8.2, 10.0, "steel", n=10, m=body)
    K.cylinder("y", (8.0, 2.8), 0.28, 10.0, 11.2, "steel", n=8, m=body)
    K.sphere((8.0, 11.9, 2.8), 1.5, 0.75, 1.5, "paint", n=16, rows=8, m=body)
    # engine nacelles with the "black hole" exhaust deflectors
    for cx, sgn in ((4.7, -1), (11.3, 1)):
        stations_loft(body, [(-1.0, 5.8, 0.85, 0.95, 2.6), (-0.6, 5.8, 1.1, 1.2, 2.8), (6.4, 5.8, 1.1, 1.2, 2.8), (7.2, 5.8, 0.9, 1.0, 2.6)],
                      "paint", n=14, cx=cx)
        K.cylinder("z", (cx, 5.8), 0.75, -1.02, -1.04, "black", n=12, caps=(False, True), m=body)
        sub = K.rbox((cx - 0.9, 5.0, 6.8), (cx + 0.9, 6.6, 9.0), 0.3, "dark", seg=1)
        sub.rotate("y", 22.5 * sgn, (cx, 5.8, 7.9))
        body.extend(sub)
    # tail: swept fin, stabilator, tail wheel
    foil(body, (8, 5.0, 25.2), (8, 10.4, 26.9), 2.4, 1.4, 0.14, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0))
    for tip in (4.4, 11.6):
        foil(body, (8, 4.72, 25.4), (tip, 4.72, 25.8), 2.2, 1.6, 0.12, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    wheel_gear(body, 8.0, 26.6, 4.0, 0.5, w=0.4)
    # stub wings with Hydra pods inboard and Hellfire racks outboard
    for tip in (1.2, 14.8):
        root = 5.8 if tip < 8 else 10.2
        foil(body, (root, 4.25, 0.9), (tip, 4.25, 1.2), 3.1, 2.7, 0.14, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    for (px, py, pz) in g["pods"]:
        K.rbox((px - 0.22, 3.75, 1.2), (px + 0.22, 4.2, 3.4), 0.08, "dark", seg=1, m=body)
        pod(body, px, py, pz, pz + 4.6, 0.9, tubes=7)
    for (rx, ry, rz) in g["rails"]:
        K.rbox((rx - 1.0, 3.4, rz + 0.2), (rx + 1.0, 3.95, rz + 4.2), 0.12, "dark", seg=1, m=body)
        for dx in (-0.5, 0.5):
            for dy in (-0.3, -0.95):
                missile(body, rx + dx, 3.4 + dy, rz, 4.0, 0.27)
    for x in (5.4, 10.6):
        wheel_gear(body, x, -6.0, 2.7, 0.75)
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.25, fy - 0.4, fz - 0.6), (fx + 0.25, fy + 0.4, fz + 0.6), 0.1, "dark", seg=1, m=body)
    K.cylinder("z", (7.0, 5.6), 0.15, 21.9, 22.3, "red_light", n=6, m=body)
    # M230 chain gun under the nose
    cx, cy, cz = g["chin"]
    K.cylinder("y", (cx, cz), 0.75, cy - 0.4, cy + 0.6, "dark", n=12, bevel=0.15, m=chin)
    K.rbox((cx - 0.35, cy - 0.45, cz - 2.6), (cx + 0.35, cy + 0.05, cz - 0.3), 0.1, "gunmetal", seg=1, m=chin)
    K.cylinder("z", (cx, cy - 0.2), 0.17, cz - 2.6, cz - g["chinLen"], "gunmetal", n=8, m=chin)
    K.cylinder("z", (cx, cy - 0.2), 0.24, cz - g["chinLen"], cz - g["chinLen"] - 0.3, "gunmetal", n=8, bevel=0.04, m=chin)
    th, tb = tail_parts(g["tailR"])
    spots = [(v3(6.85, 4.6, 12.5), (-1, 0, 0), (0, 1, 0), 0.65), (v3(9.15, 4.6, 12.5), (1, 0, 0), (0, 1, 0), 0.65)]
    parts = {"body": body, "chin": chin, "hub": hub_part(0.8), "blade": blade_part(g["rotorR"], 0.62, 0.9),
             "tail_hub": th, "tail_blade": tb}
    parts.update(_marks(spots))
    return parts, g


def _marks(spots):
    return {"mark_green": V.team_marks(spots, True), "mark_red": V.team_marks(spots, False)}


# ============================================================================================ Mi-24 Hind
def hind():
    A2.hind()
    g = A2.GEO["hind"]
    body, chin = Mesh(), Mesh()
    stations_loft(body, [(-15.6, 3.3, 0.6, 0.6, 2.2), (-15.0, 3.4, 1.25, 1.0, 2.5), (-13.0, 3.5, 1.4, 1.1, 2.7),
                         (-10.8, 3.9, 1.6, 1.6, 2.8), (-7.0, 4.2, 2.0, 2.4, 3.2), (-4.0, 4.5, 2.6, 2.9, 3.4),
                         (6.0, 4.5, 2.6, 2.9, 3.4), (9.0, 4.9, 1.6, 1.8, 2.8), (12.0, 5.2, 1.05, 1.25, 2.5),
                         (20.0, 5.3, 0.8, 0.95, 2.4), (27.6, 5.4, 0.6, 0.7, 2.3), (28.6, 5.6, 0.35, 0.45, 2.2)], "paint", n=20)
    # the "double bubble": gunner's glazed nose and the pilot's raised canopy
    canopy(body, [(-15.3, 3.9, 0.9, 0.6), (-14.4, 4.1, 1.3, 1.5), (-12.4, 4.2, 1.35, 1.9), (-11.2, 4.2, 1.2, 1.7)], frames_at=(-14.0, -12.4))
    canopy(body, [(-10.6, 5.2, 1.2, 0.5), (-9.8, 5.5, 1.55, 1.6), (-7.8, 5.7, 1.6, 2.0), (-6.6, 5.7, 1.4, 1.6)], frames_at=(-9.6, -8.0))
    for xw in (5.4 - 0.45, 10.6 + 0.45):
        n = (-1, 0, 0) if xw < 8 else (1, 0, 0)
        for i in range(3):
            V.band(body, v3(xw - 0.02 * n[0] * -1, 5.2, -3.9 + i * 2.2), n, (0, 1, 0), 1.4, 1.1, "glass_dark")
    # engine deck: intakes with dust protectors, exhausts, mast
    K.rbox((6.2, 6.9, -4.2), (9.8, 9.2, 10.0), 0.7, "paint", seg=2, m=body)
    for x in (6.6, 9.4):
        K.cylinder("z", (x, 8.3), 0.85, -3.9, -4.6, "paint", n=12, bevel=0.15, m=body)
        K.sphere((x, 8.3, -4.65), 0.5, 0.5, 0.35, "steel", n=10, rows=6, m=body)
    for x0 in (5.35, 10.05):
        K.rbox((x0, 7.3, 6.0), (x0 + 0.6, 8.6, 9.4), 0.15, "dark", seg=1, m=body)
    K.cylinder("y", (8.0, 5.0), 0.6, 9.2, 10.4, "steel", n=10, m=body)
    # stepped, drooping wings with the pods, the bombs and the Shturm rails at the tips
    for side in (-1, 1):
        root = 8 + side * 2.6
        foil(body, (root, 5.25, 0.0), (8 + side * 7.6, 4.85, 0.25), 4.0, 3.5, 0.16, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    for (px, py, pz) in g["pods"]:
        K.rbox((px - 0.22, 4.55, 1.0), (px + 0.22, 5.05, 3.0), 0.08, "dark", seg=1, m=body)
        pod(body, px, py, pz, pz + 5.0, 1.0, tubes=9)
    for (bx, by, bz) in g["bombs"]:
        K.rbox((bx - 0.2, 4.15, 1.0), (bx + 0.2, 4.6, 3.0), 0.08, "dark", seg=1, m=body)
        K.sphere((bx, by, bz - 2.0), 0.6, 0.6, 0.9, "olive", n=12, rows=6, m=body)
        K.cylinder("z", (bx, by), 0.75, bz - 2.0, bz + 1.4, "olive", n=12, r1=0.5, caps=(False, True), m=body)
        for a in (0, 90):
            sub = K.box((bx - 0.85, by - 0.04, bz + 1.4), (bx + 0.85, by + 0.04, bz + 2.4), "olive")
            sub.rotate("z", a, (bx, by, 0))
            body.extend(sub)
    for (rx, ry, rz) in g["rails"]:
        K.rbox((rx - 0.35, ry - 0.6, rz), (rx + 0.35, ry + 0.4, rz + 3.8), 0.1, "dark", seg=1, m=body)
        for dy in (-0.3, 0.2):
            missile(body, rx, ry + dy, rz - 0.2, 3.8, 0.22)
    wheel_gear(body, 8.0, -12.4, 2.4, 0.6)
    for x in (4.6, 11.4):
        wheel_gear(body, x, 1.0, 2.9, 0.8, w=0.6)
    # tail: fin, stabilisers
    foil(body, (8, 5.4, 26.0), (8, 11.2, 27.8), 2.6, 1.6, 0.14, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0))
    for tip in (4.6, 11.4):
        foil(body, (8, 4.95, 21.0), (tip, 4.95, 21.3), 2.0, 1.6, 0.12, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    K.box((7.85, 3.0, 26.6), (8.15, 4.4, 27.0), "dark", m=body)
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.25, fy - 0.5, fz - 0.7), (fx + 0.25, fy + 0.3, fz + 0.7), 0.1, "dark", seg=1, m=body)
    # YakB-12.7 four-barrel gatling in the chin turret
    cx, cy, cz = g["chin"]
    K.sphere((cx, cy, cz), 0.85, 0.55, 0.85, "dark", n=12, rows=6, m=chin)
    K.rbox((cx - 0.4, cy - 0.55, cz - 1.4), (cx + 0.4, cy + 0.15, cz - 0.3), 0.12, "gunmetal", seg=1, m=chin)
    for dx, dy in ((-0.15, -0.35), (0.15, -0.35), (-0.15, -0.05), (0.15, -0.05)):
        K.cylinder("z", (cx + dx, cy + dy), 0.09, cz - 1.4, cz - g["chinLen"] - 1.2, "gunmetal", n=6, m=chin)
    K.cylinder("z", (cx, cy - 0.2), 0.32, cz - g["chinLen"] - 0.5, cz - g["chinLen"] - 0.8, "gunmetal", n=10, m=chin)
    th, tb = tail_parts(g["tailR"], chord=0.45)
    spots = [(v3(5.42 - 0.45, 4.4, 0.0), (-1, 0, 0), (0, 1, 0), 0.9), (v3(10.58 + 0.45, 4.4, 0.0), (1, 0, 0), (0, 1, 0), 0.9)]
    parts = {"body": body, "chin": chin, "hub": hub_part(1.0), "blade": blade_part(g["rotorR"], 0.7, 1.1),
             "tail_hub": th, "tail_blade": tb}
    parts.update(_marks(spots))
    return parts, g


# ============================================================================================ Ka-52 Alligator
def ka52():
    A2.ka52()
    g = A2.GEO["ka52"]
    body = Mesh()
    stations_loft(body, [(-15.4, 4.0, 0.4, 0.4, 2.0), (-14.8, 4.0, 1.1, 1.1, 2.0), (-13.4, 3.9, 1.6, 1.4, 2.4),
                         (-11.0, 3.8, 2.2, 1.5, 2.8), (-7.0, 4.3, 2.1, 2.1, 3.0), (-3.0, 4.6, 2.1, 2.5, 3.0),
                         (4.0, 4.6, 2.0, 2.4, 3.0), (6.4, 4.8, 1.4, 1.6, 2.7), (9.0, 4.8, 0.95, 1.1, 2.4),
                         (16.0, 4.8, 0.8, 0.9, 2.4), (23.6, 4.8, 0.55, 0.65, 2.3), (24.4, 4.9, 0.3, 0.4, 2.2)], "paint", n=20)
    canopy(body, [(-13.2, 4.9, 1.5, 0.3), (-12.2, 5.15, 2.05, 1.2), (-10.2, 5.3, 2.15, 1.85), (-8.0, 5.3, 2.05, 1.7), (-7.2, 5.2, 1.7, 1.0)],
           frames_at=(-12.0, -9.6, -7.6))
    K.cylinder("y", (8.0, -12.4), 0.65, 1.6, 2.5, "dark", n=10, bevel=0.1, m=body)
    K.sphere((8.0, 1.75, -12.4), 0.55, 0.45, 0.55, "lens", n=10, rows=6, m=body)
    # engines with exhaust deflectors, the tall coaxial mast
    for cx in (5.0, 11.0):
        stations_loft(body, [(-3.0, 5.8, 0.8, 0.9, 2.6), (-2.6, 5.8, 1.0, 1.05, 2.8), (3.6, 5.8, 1.0, 1.05, 2.8), (4.4, 5.9, 0.8, 0.8, 2.6)],
                      "paint", n=14, cx=cx)
        K.cylinder("z", (cx, 5.8), 0.7, -3.02, -3.04, "black", n=12, caps=(False, True), m=body)
        sub = K.rbox((cx - 0.8, 6.0, 4.0), (cx + 0.8, 7.0, 6.0), 0.25, "dark", seg=1)
        sub.rotate("x", -22.5, (cx, 6.5, 5.0))
        body.extend(sub)
    K.rbox((6.4, 6.2, -6.0), (9.6, 7.8, 5.0), 0.6, "paint", seg=2, m=body)
    K.cylinder("y", (8.0, 2.0), 0.5, 7.8, 12.2, "steel", n=10, m=body)
    K.cylinder("y", (8.0, 2.0), 0.75, 10.2, 11.2, "steel", n=12, bevel=0.1, m=body)
    # 2A42 cannon fairing on the right side
    cx, cy, cz = g["cannon"]
    K.rbox((cx - 0.6, cy - 0.5, -4.0), (cx + 0.5, cy + 0.6, 2.0), 0.3, "paint", seg=1, m=body)
    K.cylinder("z", (cx, cy), 0.22, -4.0, cz + 0.6, "gunmetal", n=8, m=body)
    K.rbox((cx - 0.35, cy - 0.3, cz), (cx + 0.35, cy + 0.3, cz + 0.6), 0.1, "gunmetal", seg=1, m=body)
    # wings: S-8 pods, Vikhr launchers, the countermeasure pods at the tips
    for tip in (1.6, 14.4):
        root = 6.0 if tip < 8 else 10.0
        foil(body, (root, 4.6, 0.0), (tip, 4.6, 0.2), 3.2, 2.8, 0.14, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    for (px, py, pz) in g["pods"]:
        K.rbox((px - 0.22, 4.15, 0.6), (px + 0.22, 4.5, 2.6), 0.08, "dark", seg=1, m=body)
        pod(body, px, py, pz, pz + 4.8, 0.9, tubes=7)
    for (rx, ry, rz) in g["rails"]:
        K.rbox((rx - 0.8, ry - 0.5, rz), (rx + 0.8, ry + 0.6, rz + 4.6), 0.2, "olive", seg=1, m=body)
        for dx in (-0.4, 0.0, 0.4):
            for dy in (-0.2, 0.3):
                K.cylinder("z", (rx + dx, ry + dy), 0.13, rz - 0.01, rz - 0.03, "black", n=6, caps=(False, True), m=body)
    for (fx, fy, fz) in g["flares"]:
        K.cylinder("z", (fx, fy), 0.35, fz - 2.0, fz + 1.6, "dark", n=10, bevel=0.15, m=body)
    wheel_gear(body, 8.0, -9.4, 2.4, 0.55)
    for x in (5.4, 10.6):
        wheel_gear(body, x, 2.0, 2.6, 0.75, w=0.55)
    # tail: stabiliser with end plate fins, central fin (coaxial rotors: no tail rotor)
    for tip in (3.0, 13.0):
        foil(body, (8, 4.95, 21.0), (tip, 4.95, 21.2), 2.0, 1.8, 0.12, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    for x in (3.0, 13.0):
        foil(body, (x, 4.0, 20.6), (x, 7.4, 21.4), 2.6, 1.8, 0.12, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0))
    foil(body, (8, 5.4, 21.4), (8, 8.4, 22.6), 2.2, 1.4, 0.12, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0))
    spots = [(v3(5.9, 4.4, -3.6), (-1, 0, 0), (0, 1, 0), 0.8), (v3(10.1, 4.4, -3.6), (1, 0, 0), (0, 1, 0), 0.8)]
    parts = {"body": body, "hub": hub_part(0.9), "blade": blade_part(g["rotorR"], 0.66, 1.0)}
    parts.update(_marks(spots))
    return parts, g


# ============================================================================================ AH-1 Cobra
def cobra():
    import gen_geo
    g = gen_geo.cobra()
    body = Mesh()
    hx, hy, hz = AR.HELI_ROTOR_HUB
    stations_loft(body, [(-12.6, 3.8, 0.7, 0.6, 2.2), (-11.6, 3.7, 1.3, 1.1, 2.5), (-9.4, 3.5, 1.85, 1.45, 2.8),
                         (-4.5, 3.6, 2.0, 1.7, 3.0), (0.5, 3.9, 2.1, 2.1, 3.0), (6.0, 4.1, 2.1, 2.2, 3.0),
                         (9.0, 4.6, 1.3, 1.4, 2.6), (16.0, 4.8, 0.95, 1.0, 2.4), (22.0, 4.8, 0.75, 0.8, 2.3),
                         (27.2, 4.8, 0.55, 0.6, 2.2), (28.0, 4.9, 0.3, 0.35, 2.0)], "paint", n=20)
    canopy(body, [(-9.7, 4.7, 1.3, 0.3), (-9.2, 5.0, 1.75, 1.3), (-6.5, 5.25, 1.85, 1.8), (-4.6, 5.35, 1.85, 2.1),
                  (-2.0, 5.6, 1.85, 2.4), (0.3, 5.5, 1.7, 2.1), (0.8, 5.3, 1.3, 1.2)], frames_at=(-8.8, -4.55, 0.3))
    K.rbox((6.2, 6.0, -0.5), (9.8, 8.4, 8.0), 0.9, "paint", seg=2, m=body)
    for x0 in (5.95, 9.75):
        K.box((x0, 6.7, 0.4), (x0 + 0.3, 7.9, 2.6), "black", m=body)
        K.rbox((x0 - 0.05, 6.9, 7.2), (x0 + 0.35, 7.9, 9.4), 0.1, "dark", seg=1, m=body)
    K.cylinder("y", (hx, hz), 0.95, 8.3, 9.6, "paint", n=12, bevel=0.15, m=body)
    K.cylinder("y", (hx, hz), 0.42, 9.6, 10.45, "steel", n=10, m=body)
    K.box((7.6, 6.15, 8.0), (8.4, 6.5, 20.0), "dark", m=body)
    foil(body, (8, 5.0, 25.6), (8, 10.6, 27.2), 2.4, 1.4, 0.14, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0))
    for tip in (3.8, 12.2):
        foil(body, (8, 4.75, 20.0), (tip, 4.75, 20.3), 1.8, 1.4, 0.12, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    K.box((7.85, 2.6, 25.6), (8.15, 4.0, 26.0), "dark", m=body)
    for tip in (2.0, 14.0):
        root = 5.8 if tip < 8 else 10.2
        foil(body, (root, 4.4, 1.0), (tip, 4.4, 1.2), 3.2, 2.9, 0.14, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    for px, py, pz in AR.HELI_PODS:
        K.rbox((px - 0.25, 3.7, 1.6), (px + 0.25, 4.25, 3.6), 0.08, "dark", seg=1, m=body)
        pod(body, px, py, pz, pz + 5.2, 0.8, tubes=7)
    for x0 in (0.4, 14.0):
        K.rbox((x0, 3.7, 0.8), (x0 + 1.6, 4.25, 4.8), 0.12, "dark", seg=1, m=body)
        for dx in (0.4, 1.2):
            missile(body, x0 + dx, 3.45, 0.75, 4.0, 0.2)
    # skids
    for x in (4.4, 11.6):
        K.tube([(x, 0.6, -7.6), (x, 0.2, -6.4), (x, 0.18, 8.2)], 0.2, "dark", n=8, m=body)
        for z in (-3.6, 5.0):
            K.tube([(x, 0.3, z), (x + (0.6 if x < 8 else -0.6), 2.1, z)], 0.15, "dark", n=6, m=body)
    for z in (-3.6, 5.0):
        K.tube([(4.9, 2.05, z), (11.1, 2.05, z)], 0.15, "dark", n=6, m=body)
    # main rotor (spins round the hub) and the tail rotor (round X at the tail hub), in place
    rotor = Mesh()
    K.cylinder("y", (hx, hz), 0.8, hy - 0.3, hy + 0.3, "steel", n=12, bevel=0.1, m=rotor)
    K.cylinder("y", (hx, hz), 0.25, hy + 0.3, hy + 0.7, "bright", n=8, m=rotor)
    for a in (0, 180):
        b = blade_part(AR.ROTOR_R, 0.9, 0.8)
        b.rotate("y", a, (8, 8, 8)).translate((hx - 8, hy - 8, hz - 8))
        rotor.extend(b)
    tx, ty, tz = AR.HELI_TAIL_HUB
    tail = Mesh()
    K.cylinder("x", (ty, tz), 0.3, tx - 0.15, tx + 0.35, "steel", n=10, m=tail)
    for a in (0, 180):
        sub = foil(Mesh(), (tx + 0.12, ty - 0.22, tz + 0.3), (tx + 0.12, ty - 0.18, tz + 2.5), 0.44, 0.36, 0.12, "dark",
                   chord_dir=(0, 1, 0), thick_dir=(1, 0, 0))
        sub.rotate("x", a, (tx, ty, tz))
        tail.extend(sub)
    chin = Mesh()
    cx, cy, cz = AR.HELI_CHIN
    K.sphere((cx, cy, cz), 0.9, 0.55, 0.9, "dark", n=12, rows=6, m=chin)
    K.rbox((cx - 0.45, cy - 0.45, cz - 2.0), (cx + 0.45, cy + 0.15, cz - 0.4), 0.12, "gunmetal", seg=1, m=chin)
    K.cylinder("z", (cx, cy - 0.15), 0.16, cz - 2.0, cz - 3.4, "gunmetal", n=8, m=chin)
    K.cylinder("z", (cx, cy - 0.15), 0.22, cz - 3.4, cz - 3.6, "gunmetal", n=8, m=chin)
    spots = [(v3(6.0, 4.4, 12.4), (-1, 0, 0), (0, 1, 0), 0.8), (v3(10.0, 4.4, 12.4), (1, 0, 0), (0, 1, 0), 0.8)]
    parts = {"body": body, "chin": chin, "rotor": rotor, "tail_rotor": tail}
    parts.update(_marks(spots))
    return parts, g


HELIS = {"heli": cobra, "heli_apache": apache, "heli_hind": hind, "heli_ka52": ka52}


def assemble(parts, g, team=(0.42, 0.50, 0.30)):
    """for previews: rotors placed the way HelicopterRenderer draws them"""
    out = [(parts["body"].copy(), team), (parts["mark_green"].copy(), None)]
    if "chin" in parts:
        out.append((parts["chin"].copy(), None))
    if "rotor" in parts:
        out += [(parts["rotor"].copy(), None), (parts["tail_rotor"].copy(), None)]
        return out
    hx, hy, hz = g["hub"]
    hubs = [hy] + ([g["hub2"]] if g.get("hub2") else [])
    for i, y in enumerate(hubs):
        out.append((parts["hub"].copy().translate((hx - 8, y - 8, hz - 8)), None))
        for k in range(g["blades"]):
            b = parts["blade"].copy().rotate("y", k * 360 / g["blades"] + i * 60, (8, 8, 8)).translate((hx - 8, y - 8, hz - 8))
            out.append((b, None))
    if g.get("tail"):
        tx, ty, tz = g["tail"]
        out.append((parts["tail_hub"].copy().translate((tx - 8, ty - 8, tz - 8)), None))
        for k in range(g["tailBlades"]):
            out.append((parts["tail_blade"].copy().rotate("x", k * 360 / g["tailBlades"], (8, 8, 8)).translate((tx - 8, ty - 8, tz - 8)), None))
    return out
