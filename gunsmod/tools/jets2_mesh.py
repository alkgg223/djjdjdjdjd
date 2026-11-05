"""Smooth meshes of five more real combat aircraft, same conventions as the F-16 (jet_mesh.py): 1 unit = 0.375 block,
front -Z, +X right, (8, 0, 8) under the entity origin, ground y = 0 with the gear down. Real proportions.

  f22    F-22A Raptor: flat faceted body, diamond wing, canted twin fins, square twin nozzles, all stores inside
  f35    F-35A Lightning II: single engine, diamond wing, canted twin fins, bumped intakes, stores inside
  su35   Su-35S: wide lifting body, two engine nacelles with a tunnel and the tail sting between them
  mig29  MiG-29: big leading edge extensions, nacelles under them, twin fins
  a10    A-10C: straight wing, two engine pods high on the rear body, twin fins on the tailplane, the GAU-8 in the nose

Each gives the parts body, mark_green, mark_red, gear_nose, gear_main, burner (where it has reheat), mav_0..3 and
aim9_l / aim9_r (the stores on the pylons and the wing tips), plus the geometry the game uses (GEO)."""
import math

import numpy as np

import meshkit as K
import vehmesh as V
import aircraft_kit as AK
from helis_mesh import stations_loft, foil
from jet_mesh import maverick, aim9
from meshkit import Mesh, v3

GEO = {}
U = 0.375            # metres (= blocks) per model unit
_last_canopy = {}
# per jet: side stick or not, number of displays, one wide display, exterior details
COCKPIT = {"f22": (True, 3, False), "f35": (True, 3, True), "su35": (False, 3, False), "mig29": (False, 2, False), "a10": (False, 2, False)}


# ------------------------------------------------------------------------------------------------ stores
def missile(x, y, z0, length=7.0, r=0.22, body="white", canard=False):
    """a long range air-to-air missile (AIM-120 / R-77 style), nose at z0 pointing -Z"""
    m = Mesh()
    K.sphere((x, y, z0 + 0.5), r, r, 0.5, "light_grey", n=10, rows=5, m=m)
    K.cylinder("z", (x, y), r, z0 + 0.5, z0 + length, body, n=10, m=m)
    K.cylinder("z", (x, y), r + 0.01, z0 + 1.6, z0 + 1.9, "yellow", n=10, caps=(False, False), m=m)
    for a in (45, 135, 225, 315):
        f = Mesh()
        if canard:
            K.plate([v3(x + r, y, z0 + 1.0), v3(x + r + 0.45, y, z0 + 1.2), v3(x + r + 0.45, y, z0 + 1.5), v3(x + r, y, z0 + 1.7)], 0.05, body, m=f)
        else:
            K.plate([v3(x + r, y, z0 + 2.6), v3(x + r + 0.5, y, z0 + 3.0), v3(x + r + 0.5, y, z0 + 3.5), v3(x + r, y, z0 + 3.8)], 0.05, body, m=f)
        K.plate([v3(x + r, y, z0 + length), v3(x + r + 0.6, y, z0 + length), v3(x + r + 0.6, y, z0 + length - 0.5), v3(x + r, y, z0 + length - 1.2)],
                0.05, body, m=f)
        m.extend(f.rotate("z", a, (x, y, 0)))
    return m


def bomb(x, y, z0, length=5.6, r=0.42):
    """a guided bomb (GBU / KAB): ogive nose with the seeker kit, fat body, tail kit with fins"""
    m = Mesh()
    K.sphere((x, y, z0 + 0.9), r * 0.8, r * 0.8, 0.9, "olive", n=10, rows=5, m=m)
    K.cylinder("z", (x, y), r * 0.8, z0 + 0.9, z0 + 1.6, "olive", n=12, r1=r, m=m)
    K.cylinder("z", (x, y), r, z0 + 1.6, z0 + length - 1.2, "olive", n=12, m=m)
    K.cylinder("z", (x, y), r, z0 + length - 1.2, z0 + length, "grey", n=12, r1=r * 0.6, m=m)
    K.cylinder("z", (x, y), r + 0.01, z0 + 1.2, z0 + 1.35, "yellow", n=12, caps=(False, False), m=m)
    for a in (45, 135, 225, 315):
        f = Mesh()
        K.plate([v3(x + r * 0.6, y, z0 + length - 1.0), v3(x + r + 0.5, y, z0 + length - 0.6), v3(x + r + 0.5, y, z0 + length),
                 v3(x + r * 0.6, y, z0 + length)], 0.05, "grey", m=f)
        m.extend(f.rotate("z", a, (x, y, 0)))
    return m


def pylon(m, px, py, pz, wing_y, length=5.0):
    K.plate([v3(px, py + 0.45, pz + 0.6), v3(px, wing_y - 0.1, pz + 0.2), v3(px, wing_y - 0.1, pz + length + 0.2), v3(px, py + 0.45, pz + length)],
            0.28, "paint_dark", bevel=0.08, m=m)


# ------------------------------------------------------------------------------------------------ shared pieces
def gear(g, nose_h, main_h, main_x, nose_r=0.7, main_r=1.0, twin_nose=False):
    """nose gear folding back and two main legs folding forward, about the pivots in GEO"""
    nx, ny, nz = g["gearNose"]
    gn = Mesh()
    K.tube([(nx, ny, nz), (nx, nose_r + 0.25, nz - 0.1)], 0.16, "steel", n=8, m=gn)
    for dx in ((-0.32, 0.32) if twin_nose else (0.0,)):
        K.cylinder("x", (nose_r, nz - 0.2), nose_r, nx + dx - 0.25, nx + dx + 0.25, "rubber", n=14, bevel=0.1, m=gn)
        K.cylinder("x", (nose_r, nz - 0.2), nose_r * 0.55, nx + dx - 0.28, nx + dx + 0.28, "light_grey", n=10, m=gn)
    K.sphere((nx, nose_h - 0.5, nz - 0.6), 0.12, 0.12, 0.06, "glow", n=8, rows=3, m=gn)
    K.plate([v3(nx + 0.8, nose_h - 1.0, nz - 0.9), v3(nx + 0.8, nose_h, nz - 0.9), v3(nx + 0.8, nose_h, nz + 1.4), v3(nx + 0.8, nose_h - 1.0, nz + 1.4)],
            0.06, "paint", m=gn)
    mx, my, mz = g["gearMain"]
    gm = Mesh()
    for s in (-1, 1):
        x = mx + s * main_x
        K.tube([(x, my, mz), (x, main_r, mz + 0.2)], 0.18, "steel", n=8, m=gm)
        K.tube([(x - s * 0.7, my, mz), (x, main_r + 1.0, mz + 0.1)], 0.08, "steel", n=6, m=gm)
        K.cylinder("x", (main_r, mz + 0.2), main_r, x - 0.4, x + 0.4, "rubber", n=16, bevel=0.12, m=gm)
        K.cylinder("x", (main_r, mz + 0.2), main_r * 0.55, x - 0.43, x + 0.43, "light_grey", n=12, m=gm)
        K.plate([v3(x + s * 0.55, main_r + 0.6, mz - 1.1), v3(x + s * 0.55, main_h, mz - 1.1), v3(x + s * 0.55, main_h, mz + 1.3),
                 v3(x + s * 0.55, main_r + 0.6, mz + 1.3)], 0.08, "paint", m=gm)
    return gn, gm


def plume(nozzles, r, length=5.0):
    """afterburner flames with shock diamonds behind each nozzle"""
    bn = Mesh()
    for bx, by, bz in nozzles:
        K.cylinder("z", (bx, by), r, bz, bz + 1.3, "amber", n=14, r1=r * 0.78, caps=(True, False), m=bn)
        K.cylinder("z", (bx, by), r * 0.78, bz + 1.3, bz + length * 0.6, "amber", n=14, r1=r * 0.45, caps=(False, False), m=bn)
        K.cylinder("z", (bx, by), r * 0.45, bz + length * 0.6, bz + length, "amber", n=10, r1=0.05, caps=(False, False), m=bn)
        for i in range(4):
            z = bz + 0.5 + i * length * 0.18
            K.sphere((bx, by, z), r * (0.42 - i * 0.07), r * (0.42 - i * 0.07), 0.3, "glow", n=8, rows=4, m=bn)
    return bn


def canopy(m, c, rx, ry, rz, tint="glass"):
    _last_canopy["c"] = (c, rx, ry, rz)
    K.sphere(c, rx, ry, rz, tint, n=20, rows=8, top_only=True, m=m)
    x, y, z = c
    bow = [(x + rx * 0.98 * math.cos(math.radians(a)), y + ry * 0.95 * math.sin(math.radians(a)), z + rz * 0.35) for a in range(0, 181, 15)]
    K.tube(bow, 0.08, "dark", n=5, m=m)
    K.rbox((x - 0.7, y - 0.5, z + rz * 0.15), (x + 0.7, y + 1.0, z + rz * 0.45), 0.2, "dark", seg=1, m=m)


def fin(m, root_le, height, sweep, cant, chord_root, chord_tip, mat="paint", thick=0.06):
    """a vertical fin from its root leading edge, leaning `cant` degrees outward (sign gives the side)"""
    x, y, z = root_le
    tip = (x + math.sin(math.radians(cant)) * height, y + math.cos(math.radians(cant)) * height, z + sweep)
    foil(m, root_le, tip, chord_root, chord_tip, thick, mat, chord_dir=(0, 0, 1),
         thick_dir=(math.cos(math.radians(cant)), -math.sin(math.radians(cant)), 0), n=10)
    return tip


def wing_pair(m, root_x, wy, root_le_z, tip_x, tip_le_z, chord_root, chord_tip, thick=0.05, droop=0.0):
    for s in (1, -1):
        foil(m, (8 + s * root_x, wy, root_le_z), (8 + s * tip_x, wy - droop, tip_le_z), chord_root, chord_tip, thick, "paint", thick_dir=(0, 1, 0), n=12)
        AK.hinge_lines(m, (8 + s * root_x, wy, root_le_z), (8 + s * tip_x, wy - droop, tip_le_z), chord_root, chord_tip, thick)


def details(name, g, body):
    """small exterior parts: pitot / probes, blade antennas, formation light strips, IRST, wicks, nav lights"""
    (cx, cy, cz), rx, ry, rz = _last_canopy["c"]
    nose = g["ends"][0]
    if name in ("su35", "mig29", "a10"):
        AK.pitot(body, nose, 1.6 if name != "a10" else 0.8, 0.09)
    if name in ("su35", "mig29"):
        AK.irst(body, cx + 0.7, cy + 0.1, cz - rz * 1.02, 0.42)
    # blade antennas: one on the spine behind the canopy, one under the belly
    AK.blade(body, 8.0, cy - 0.2, cz + rz * 1.6, 1.0, 1.4, True)
    AK.blade(body, 8.0, 3.1 if name != "a10" else 2.9, cz + rz * 2.2, 0.9, 1.2, False)
    # formation light strips on the forward fuselage sides and the fins
    for s in (-1, 1):
        AK.light_strip(body, (8 + s * (rx + 0.75), cy - 1.2, cz - rz * 0.6), (8 + s * (rx + 0.9), cy - 1.0, cz + rz * 0.4))
    tl, tr = g["tips"]
    AK.lights(body, (tl[0] - 0.15, tl[1], tl[2] + 0.5), (tr[0] + 0.15, tr[1], tr[2] + 0.5), (8.0, g["ends"][1][1] + 0.4, g["ends"][1][2] - 0.6))
    AK.wicks(body, [(tl[0] + 0.3, tl[1], tl[2] + 2.5), (tr[0] - 0.3, tr[1], tr[2] + 2.5)])


def finish(name, g, body, spots, gear_parts, burner, stores):
    details(name, g, body)
    AK.reskin(body)
    (cx, cy, cz), rx, ry, rz = _last_canopy["c"]
    eye = (cx, cy + 0.35 * ry, cz + 0.25 * rz)
    side, displays, wide = COCKPIT[name]
    pit, stick_p, thr_p = AK.cockpit(eye, U, side_stick=side, displays=displays, wide_panel=wide)
    g["eye"] = eye
    g["seat"] = AK.place_seat(eye, U)
    g["stick"] = stick_p
    g["throttle"] = thr_p
    parts = {"body": body, "mark_green": V.team_marks(spots, True), "mark_red": V.team_marks(spots, False)}
    parts.update(pit)
    parts["gear_nose"], parts["gear_main"] = gear_parts
    if burner is not None:
        parts["burner"] = burner
    parts.update(stores)
    GEO[name] = g
    return parts, g


def store_parts(g, kinds):
    """kinds: what hangs on pylon 0..3 and the wing tips ('mav', 'bomb', 'amraam', 'r73', 'aim9')"""
    out = {}
    pts = list(g["pylons"]) + list(g["tips"])
    names = ["mav_0", "mav_1", "mav_2", "mav_3", "aim9_l", "aim9_r"]
    for name, (x, y, z), k in zip(names, pts, kinds):
        if k == "mav":
            out[name] = maverick(x, y - 0.2, z)
        elif k == "bomb":
            out[name] = bomb(x, y - 0.25, z)
        elif k == "amraam":
            out[name] = missile(x, y - 0.1, z, 7.2, 0.2, "white")
        elif k == "r73":
            out[name] = missile(x, y - 0.1, z, 5.8, 0.2, "light_grey", canard=True)
        else:
            out[name] = aim9(x, y, z)
    return out


def nozzle_round(m, x, y, z0, z1, r):
    K.cylinder("z", (x, y), r * 1.08, z0, z1 - 1.2, "gunmetal", n=20, r1=r, caps=(False, False), m=m)
    K.cylinder("z", (x, y), r, z1 - 1.2, z1, "gunmetal", n=20, r1=r * 0.88, caps=(False, False), m=m)
    for k in range(12):
        a = math.radians(k * 30)
        K.tube([(x + math.cos(a) * r * 0.97, y + math.sin(a) * r * 0.97, z1 - 1.2), (x + math.cos(a) * r * 0.86, y + math.sin(a) * r * 0.86, z1)],
               0.05, "steel", n=4, m=m)
    K.cylinder("z", (x, y), r * 0.8, z1 - 0.15, z1 - 0.05, "black", n=16, caps=(False, True), m=m)


# ------------------------------------------------------------------------------------------------ F-22A
def f22():
    WY = 5.5
    g = dict(seat=(8.0, 6.2, -14.6), cannon=(12.6, 6.0, -10.0), nozzle=(8.0, 5.8, 24.8),
             gearNose=(8.0, 3.6, -15.5), gearMain=(8.0, 4.2, 3.0),
             tips=[(-9.6, WY, 11.0), (25.6, WY, 11.0)], pylons=[(5.6, 4.3, -5.0), (10.4, 4.3, -5.0), (6.0, 4.3, 2.0), (10.0, 4.3, 2.0)],
             flares=[(6.0, 4.4, 16.0), (10.0, 4.4, 16.0)],
             ends=[(8.0, 5.2, -25.0), (8.0, 6.0, 25.0), (-9.8, WY, 12.0), (25.8, WY, 12.0), (14.6, 13.4, 19.0), (8.0, 2.2, 0.0)],
             pivotY=2.2, size=(3.6, 2.4))
    body = Mesh()
    stations_loft(body, [
        (-25.0, 5.2, 0.05, 0.05, 2.0), (-23.4, 5.25, 0.65, 0.5, 2.3), (-20.0, 5.35, 1.6, 1.25, 2.8), (-16.0, 5.6, 2.3, 1.65, 3.2),
        (-11.0, 5.8, 3.6, 1.75, 3.6), (-6.0, 5.85, 5.4, 1.6, 4.0), (2.0, 5.85, 6.0, 1.5, 4.2), (12.0, 5.85, 5.6, 1.4, 4.0),
        (18.0, 5.85, 4.4, 1.2, 3.6), (22.0, 5.85, 3.8, 1.0, 3.4), (24.2, 5.85, 3.4, 0.95, 3.4),
    ], "paint", n=24, caps=(False, True))
    canopy(body, (8.0, 7.0, -14.5), 1.25, 1.55, 4.6, "glass_dark")
    # chined intakes with the dark mouths, swept to match the wing
    for s in (1, -1):
        stations_loft(body, [(-11.2, 5.2, 0.8, 0.95, 4.0), (-6.0, 5.2, 1.0, 1.05, 4.0), (2.0, 5.3, 0.9, 1.0, 3.6), (8.0, 5.5, 0.4, 0.6, 3.0)],
                      "paint", n=16, cx=8 + s * 4.9)
        mouth = [v3(8 + s * 4.9 + x, 5.2 + y, -11.25) for x, y in K.superellipse(0.75, 0.88, 4.0, 16)]
        K.cap(body, mouth, "black", np.array([0.0, 0.0, -1.0]))
    wing_pair(body, 5.6, WY, -4.0, 17.6, 9.6, 20.0, 3.6, 0.045)
    for s in (1, -1):
        foil(body, (8 + s * 3.8, 5.7, 15.0), (8 + s * 10.6, 5.6, 21.0), 8.2, 3.0, 0.05, "paint", thick_dir=(0, 1, 0), n=10)
        fin(body, (8 + s * 3.3, 6.9, 11.0), 7.4, 7.2, 28 * s, 9.0, 3.4)
        # square two-dimensional nozzles
        K.rbox((8 + s * 1.8 - 1.2, 4.95, 21.5), (8 + s * 1.8 + 1.2, 6.65, 24.8), 0.15, "gunmetal", seg=1, m=body)
        K.box((8 + s * 1.8 - 1.0, 5.15, 24.79), (8 + s * 1.8 + 1.0, 6.45, 24.85), "black", m=body, faces="Z")
    K.cylinder("z", (12.6, 6.0), 0.2, -10.6, -9.6, "black", n=8, m=body)
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.4, fy - 0.2, fz - 0.8), (fx + 0.4, fy + 0.2, fz + 0.8), 0.08, "dark", seg=1, m=body)
    K.box((-9.5, WY - 0.05, 10.4), (-9.2, WY + 0.1, 10.8), "red_light", m=body)
    K.box((25.2, WY - 0.05, 10.4), (25.5, WY + 0.1, 10.8), "green_mark", m=body)
    spots = [(v3(20.0, WY + 0.2, 8.0), (0, 1, 0), (0, 0, -1), 0.8), (v3(-4.0, WY + 0.2, 8.0), (0, 1, 0), (0, 0, -1), 0.8),
             (v3(14.0, 10.6, 16.6), (0.88, -0.47, 0), (0, 1, 0), 0.6), (v3(2.0, 10.6, 16.6), (-0.88, -0.47, 0), (0, 1, 0), 0.6)]
    gp = gear(g, 5.2, 5.0, 3.0)
    return finish("f22", g, body, spots, gp, plume([(8 + s * 1.8, 5.8, 24.8) for s in (1, -1)], 0.95),
                  store_parts(g, ["bomb", "bomb", "amraam", "amraam", "amraam", "amraam"]))


# ------------------------------------------------------------------------------------------------ F-35A
def f35():
    WY = 5.2
    g = dict(seat=(8.0, 6.0, -11.6), cannon=(5.0, 6.7, -6.0), nozzle=(8.0, 5.7, 22.0),
             gearNose=(8.0, 3.3, -12.0), gearMain=(8.0, 3.9, 4.0),
             tips=[(-5.9, WY, 9.0), (21.9, WY, 9.0)], pylons=[(6.0, 4.0, -2.0), (10.0, 4.0, -2.0), (6.0, 4.0, 4.0), (10.0, 4.0, 4.0)],
             flares=[(6.4, 4.2, 13.0), (9.6, 4.2, 13.0)],
             ends=[(8.0, 5.0, -20.0), (8.0, 6.0, 22.0), (-6.2, WY, 10.0), (22.2, WY, 10.0), (13.0, 12.9, 16.5), (8.0, 2.4, 0.0)],
             pivotY=2.2, size=(3.2, 2.3))
    body = Mesh()
    stations_loft(body, [
        (-20.0, 5.0, 0.05, 0.05, 2.0), (-18.5, 5.05, 0.75, 0.6, 2.3), (-15.0, 5.25, 1.7, 1.4, 2.8), (-11.0, 5.5, 2.6, 1.85, 3.0),
        (-6.0, 5.7, 4.5, 1.95, 3.3), (0.0, 5.7, 5.3, 1.95, 3.5), (8.0, 5.7, 4.7, 1.8, 3.3), (14.0, 5.7, 3.1, 1.65, 2.7),
        (18.0, 5.7, 1.9, 1.6, 2.3), (19.9, 5.7, 1.65, 1.55, 2.0),
    ], "paint", n=24, caps=(False, False))
    canopy(body, (8.0, 6.9, -11.4), 1.15, 1.5, 4.1, "glass_dark")
    for s in (1, -1):
        # diverterless intake bump and the mouth behind it
        K.sphere((8 + s * 4.0, 5.6, -8.6), 0.6, 0.7, 1.2, "paint", n=12, rows=6, m=body)
        mouth = [v3(8 + s * 4.35 + x, 5.35 + y, -7.6) for x, y in K.superellipse(0.62, 0.95, 3.0, 16)]
        K.cap(body, mouth, "black", np.array([0.0, 0.0, -1.0]))
    wing_pair(body, 4.4, WY, -2.0, 13.9, 7.4, 15.0, 3.2, 0.045)
    for s in (1, -1):
        foil(body, (8 + s * 2.6, 5.7, 13.6), (8 + s * 8.8, 5.6, 18.6), 7.0, 2.8, 0.05, "paint", thick_dir=(0, 1, 0), n=10)
        fin(body, (8 + s * 2.3, 7.2, 9.6), 6.6, 6.6, 25 * s, 7.6, 3.0)
    nozzle_round(body, 8.0, 5.7, 19.9, 22.2, 1.45)
    K.rbox((4.6, 6.4, -7.0), (5.6, 7.0, -4.8), 0.2, "paint", seg=1, m=body)
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.4, fy - 0.2, fz - 0.8), (fx + 0.4, fy + 0.2, fz + 0.8), 0.08, "dark", seg=1, m=body)
    K.box((-5.8, WY - 0.05, 8.4), (-5.5, WY + 0.1, 8.8), "red_light", m=body)
    K.box((21.5, WY - 0.05, 8.4), (21.8, WY + 0.1, 8.8), "green_mark", m=body)
    spots = [(v3(17.0, WY + 0.2, 6.0), (0, 1, 0), (0, 0, -1), 0.7), (v3(-1.0, WY + 0.2, 6.0), (0, 1, 0), (0, 0, -1), 0.7),
             (v3(12.0, 10.0, 14.0), (0.9, -0.42, 0), (0, 1, 0), 0.55), (v3(4.0, 10.0, 14.0), (-0.9, -0.42, 0), (0, 1, 0), 0.55)]
    gp = gear(g, 4.9, 4.6, 2.6)
    return finish("f35", g, body, spots, gp, plume([(8.0, 5.7, 22.2)], 1.2),
                  store_parts(g, ["bomb", "bomb", "amraam", "amraam", "aim9", "aim9"]))


# ------------------------------------------------------------------------------------------------ twin-engine Flankers / Fulcrums
def flanker_like(name, L, WY, span, g, centre, lerx, nac_x, nac, nozzle_z, nozzle_r, wing, htail, fins, canopy_c, sting=None):
    body = Mesh()
    stations_loft(body, centre, "paint", n=22, caps=(False, True))
    # the flat lifting body / leading edge extensions joining fuselage, nacelles and wings
    outline = lerx + [(16 - x, z) for x, z in reversed(lerx)]
    K.plate([v3(x, WY + 0.25, z) for x, z in outline], 0.7, "paint", bevel=0.25, m=body)
    for s in (1, -1):
        stations_loft(body, [(z, y, hw, hh, e) for z, y, hw, hh, e in nac], "paint", n=18, cx=8 + s * nac_x, caps=(False, False))
        z0, y0, hw0, hh0, _ = nac[0]
        mouth = [v3(8 + s * nac_x + x, y0 + y, z0 - 0.02) for x, y in K.superellipse(hw0 * 0.9, hh0 * 0.85, 4.0, 16)]
        K.cap(body, mouth, "black", np.array([0.0, 0.0, -1.0]))
        nozzle_round(body, 8 + s * nac_x, nac[-1][1], nac[-1][0] - 0.1, nozzle_z, nozzle_r)
        foil(body, (8 + s * wing[0], WY, wing[1]), (8 + s * wing[2], WY - 0.2, wing[3]), wing[4], wing[5], 0.05, "paint", thick_dir=(0, 1, 0), n=12)
        AK.hinge_lines(body, (8 + s * wing[0], WY, wing[1]), (8 + s * wing[2], WY - 0.2, wing[3]), wing[4], wing[5], 0.05)
        foil(body, (8 + s * htail[0], htail[1], htail[2]), (8 + s * htail[3], htail[1] - 0.1, htail[4]), htail[5], htail[6], 0.05, "paint",
             thick_dir=(0, 1, 0), n=10)
        fin(body, (8 + s * fins[0], fins[1], fins[2]), fins[3], fins[4], fins[5] * s, fins[6], fins[7])
    canopy(body, canopy_c, 1.3, 1.7, 4.4, "glass")
    if sting:
        stations_loft(body, sting, "paint", n=14, caps=(False, True))
    return body


def su35():
    WY = 5.6
    g = dict(seat=(8.0, 6.8, -17.0), cannon=(11.0, 6.6, -10.0), nozzle=(8.0, 5.0, 27.2),
             gearNose=(8.0, 3.6, -19.0), gearMain=(8.0, 4.0, 4.0),
             tips=[(-12.2, WY - 0.2, 13.0), (28.2, WY - 0.2, 13.0)], pylons=[(1.0, 4.6, 3.0), (15.0, 4.6, 3.0), (-2.5, 4.6, 7.0), (18.5, 4.6, 7.0)],
             flares=[(8.0, 6.6, 27.5), (8.0, 6.3, 25.5)],
             ends=[(8.0, 5.6, -28.0), (8.0, 6.6, 30.0), (-12.4, WY - 0.2, 14.0), (28.4, WY - 0.2, 14.0), (11.8, 15.0, 24.0), (8.0, 2.4, 0.0)],
             pivotY=2.3, size=(4.2, 2.6))
    body = flanker_like("su35", 58, WY, 40.8, g,
                        centre=[(-28.0, 5.6, 0.05, 0.05, 2.0), (-26.0, 5.6, 0.8, 0.75, 2.1), (-22.0, 5.75, 1.8, 1.6, 2.3), (-17.0, 5.95, 2.4, 2.0, 2.4),
                                (-11.0, 6.15, 2.6, 2.0, 2.4), (-5.0, 6.3, 2.4, 1.6, 2.4), (5.0, 6.4, 2.0, 1.1, 2.4), (15.0, 6.45, 1.6, 0.8, 2.2),
                                (22.0, 6.5, 1.0, 0.6, 2.0)],
                        lerx=[(8.0, -21.0), (6.4, -18.0), (4.6, -12.0), (2.0, -4.0), (0.8, 4.0), (1.2, 14.0), (2.6, 20.0), (4.4, 22.0)],
                        nac_x=3.4, nac=[(-6.0, 4.3, 1.6, 1.5, 3.2), (0.0, 4.5, 1.7, 1.6, 2.8), (10.0, 4.8, 1.6, 1.6, 2.2), (20.0, 5.0, 1.5, 1.5, 2.0),
                                        (24.0, 5.0, 1.45, 1.45, 2.0)],
                        nozzle_z=27.0, nozzle_r=1.35,
                        wing=(6.8, -2.0, 20.2, 12.0, 18.0, 3.4),
                        htail=(5.4, 5.2, 21.0, 13.2, 27.0, 8.0, 3.0),
                        fins=(3.9, 7.0, 14.0, 8.0, 8.0, 3, 9.0, 3.5),
                        canopy_c=(8.0, 7.6, -17.0),
                        sting=[(22.0, 6.4, 0.9, 0.7, 2.0), (28.0, 6.4, 0.6, 0.5, 2.0), (30.0, 6.4, 0.25, 0.25, 2.0)])
    for x, y, z in g["pylons"]:
        pylon(body, x, y, z, WY)
    spots = [(v3(22.0, WY + 0.25, 9.0), (0, 1, 0), (0, 0, -1), 0.9), (v3(-6.0, WY + 0.25, 9.0), (0, 1, 0), (0, 0, -1), 0.9),
             (v3(12.0, 11.5, 19.0), (1, 0, 0), (0, 1, 0), 0.7), (v3(4.0, 11.5, 19.0), (-1, 0, 0), (0, 1, 0), 0.7)]
    gp = gear(g, 5.0, 5.0, 3.6)
    return finish("su35", g, body, spots, gp, plume([(8 + s * 3.4, 5.0, 27.0) for s in (1, -1)], 1.1),
                  store_parts(g, ["amraam", "amraam", "mav", "mav", "r73", "r73"]))


def mig29():
    WY = 5.3
    g = dict(seat=(8.0, 6.6, -13.2), cannon=(5.4, 6.0, -8.0), nozzle=(8.0, 4.9, 22.6),
             gearNose=(8.0, 3.4, -15.0), gearMain=(8.0, 3.9, 3.0),
             tips=[(-7.1, WY - 0.2, 11.5), (23.1, WY - 0.2, 11.5)], pylons=[(0.0, 4.4, 3.5), (16.0, 4.4, 3.5), (-3.0, 4.4, 6.0), (19.0, 4.4, 6.0)],
             flares=[(6.6, 6.2, 18.0), (9.4, 6.2, 18.0)],
             ends=[(8.0, 5.4, -22.0), (8.0, 6.0, 23.0), (-7.3, WY - 0.2, 12.5), (23.3, WY - 0.2, 12.5), (12.4, 13.2, 18.0), (8.0, 2.3, 0.0)],
             pivotY=2.2, size=(3.6, 2.4))
    body = flanker_like("mig29", 46, WY, 30.4, g,
                        centre=[(-22.0, 5.4, 0.05, 0.05, 2.0), (-20.0, 5.4, 0.7, 0.65, 2.1), (-16.0, 5.6, 1.6, 1.4, 2.3), (-12.0, 5.8, 2.1, 1.8, 2.4),
                                (-7.0, 6.0, 2.2, 1.7, 2.4), (0.0, 6.1, 2.0, 1.2, 2.4), (10.0, 6.1, 1.6, 0.9, 2.2), (18.0, 6.0, 1.0, 0.6, 2.0),
                                (20.0, 6.0, 0.4, 0.3, 2.0)],
                        lerx=[(8.0, -16.0), (6.8, -14.0), (5.0, -8.0), (2.8, -1.0), (2.4, 8.0), (3.0, 15.0), (4.4, 19.0)],
                        nac_x=2.6, nac=[(-4.0, 4.2, 1.3, 1.1, 3.6), (2.0, 4.4, 1.35, 1.3, 2.8), (12.0, 4.7, 1.3, 1.3, 2.2), (19.8, 4.9, 1.2, 1.2, 2.0)],
                        nozzle_z=22.6, nozzle_r=1.12,
                        wing=(5.6, 0.0, 15.1, 10.5, 14.0, 3.0),
                        htail=(4.2, 5.0, 15.5, 10.6, 20.5, 6.5, 2.5),
                        fins=(3.6, 6.8, 10.5, 6.6, 6.0, 6, 7.5, 3.0),
                        canopy_c=(8.0, 7.3, -13.0))
    for x, y, z in g["pylons"]:
        pylon(body, x, y, z, WY)
    spots = [(v3(18.0, WY + 0.25, 8.0), (0, 1, 0), (0, 0, -1), 0.8), (v3(-2.0, WY + 0.25, 8.0), (0, 1, 0), (0, 0, -1), 0.8),
             (v3(11.6, 10.6, 14.6), (1, -0.1, 0), (0, 1, 0), 0.6), (v3(4.4, 10.6, 14.6), (-1, -0.1, 0), (0, 1, 0), 0.6)]
    gp = gear(g, 4.8, 4.8, 3.2, twin_nose=True)
    return finish("mig29", g, body, spots, gp, plume([(8 + s * 2.6, 4.9, 22.6) for s in (1, -1)], 0.95),
                  store_parts(g, ["r73", "r73", "bomb", "bomb", "r73", "r73"]))


# ------------------------------------------------------------------------------------------------ A-10C
def a10():
    WY = 3.7
    g = dict(seat=(8.0, 6.3, -13.6), cannon=(7.7, 3.7, -23.2), nozzle=(8.0, 8.0, 14.6),
             gearNose=(8.9, 2.8, -13.0), gearMain=(8.0, 3.2, 0.5),
             tips=[(-14.6, WY + 0.3, 3.0), (30.6, WY + 0.3, 3.0)], pylons=[(-1.0, 2.9, 0.5), (17.0, 2.9, 0.5), (-5.0, 3.0, 1.0), (21.0, 3.0, 1.0)],
             flares=[(6.6, 3.4, 12.0), (9.4, 3.4, 12.0)],
             ends=[(8.0, 4.4, -23.0), (8.0, 6.0, 22.0), (-15.0, WY + 0.4, 3.0), (31.0, WY + 0.4, 3.0), (15.5, 11.2, 20.0), (8.0, 1.6, 0.0)],
             pivotY=2.0, size=(3.4, 2.4))
    body = Mesh()
    stations_loft(body, [
        (-21.0, 4.4, 0.35, 0.35, 2.0), (-20.0, 4.6, 1.0, 1.0, 2.2), (-17.0, 4.8, 1.9, 1.9, 2.5), (-12.0, 5.0, 2.25, 2.25, 2.7),
        (-6.0, 5.0, 2.1, 2.05, 2.7), (2.0, 5.0, 1.95, 1.85, 2.7), (10.0, 5.4, 1.45, 1.45, 2.5), (17.0, 5.8, 0.95, 1.0, 2.2),
        (21.0, 6.0, 0.45, 0.5, 2.0),
    ], "paint", n=22, caps=(False, True))
    canopy(body, (8.0, 7.0, -14.0), 1.3, 1.45, 3.4, "glass")
    # GAU-8: seven barrels in a ring under the nose
    for k in range(7):
        a = math.radians(k * 360 / 7)
        K.cylinder("z", (7.7 + math.cos(a) * 0.28, 3.7 + math.sin(a) * 0.28), 0.09, -23.4, -20.5, "gunmetal", n=6, m=body)
    K.cylinder("z", (7.7, 3.7), 0.45, -20.8, -20.2, "gunmetal", n=10, m=body)
    # straight wing with the drooped tips and the gear pods
    for s in (1, -1):
        foil(body, (8 + s * 2.0, WY, -1.0), (8 + s * 19.0, WY + 0.4, 0.6), 8.2, 5.2, 0.12, "paint", thick_dir=(0, 1, 0), n=12)
        AK.hinge_lines(body, (8 + s * 2.0, WY, -1.0), (8 + s * 19.0, WY + 0.4, 0.6), 8.2, 5.2, 0.12)
        foil(body, (8 + s * 19.0, WY + 0.4, 0.6), (8 + s * 22.8, WY - 0.4, 2.2), 5.2, 3.2, 0.12, "paint", thick_dir=(0, 1, 0), n=10)
        stations_loft(body, [(-2.4, 2.9, 0.1, 0.1, 2.0), (-1.6, 2.9, 0.8, 0.7, 2.4), (2.4, 2.9, 0.85, 0.75, 2.4), (4.6, 3.1, 0.4, 0.35, 2.0)],
                      "paint", n=12, cx=8 + s * 6.0)
        # engine pod on a strut over the rear body
        ex = 8 + s * 3.2
        K.cylinder("z", (ex, 8.0), 1.7, 6.0, 7.0, "paint", n=20, r1=1.75, caps=(False, False), m=body)
        K.cylinder("z", (ex, 8.0), 1.75, 7.0, 12.0, "paint", n=20, r1=1.5, caps=(False, False), m=body)
        K.cylinder("z", (ex, 8.0), 1.5, 12.0, 14.6, "gunmetal", n=20, r1=1.15, caps=(False, False), m=body)
        K.cylinder("z", (ex, 8.0), 1.45, 6.05, 6.15, "black", n=18, caps=(True, False), m=body)
        K.cylinder("z", (ex, 8.0), 0.5, 5.6, 6.2, "steel", n=10, m=body)
        K.cylinder("z", (ex, 8.0), 1.05, 14.5, 14.6, "black", n=16, caps=(False, True), m=body)
        K.plate([v3(ex - s * 0.4, 6.3, 7.6), v3(ex - s * 1.2, 5.6, 7.6), v3(ex - s * 1.2, 5.6, 11.8), v3(ex - s * 0.4, 6.3, 11.8)], 0.3, "paint", m=body)
        # tailplane and the twin fins at its ends
        foil(body, (8 + s * 0.6, 6.2, 17.4), (8 + s * 7.6, 6.3, 18.0), 4.6, 4.0, 0.07, "paint", thick_dir=(0, 1, 0), n=10)
        foil(body, (8 + s * 7.6, 4.6, 16.4), (8 + s * 7.6, 11.2, 17.4), 4.8, 3.8, 0.07, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0), n=10)
    for x, y, z in g["pylons"]:
        pylon(body, x, y, z, WY, 4.4)
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.4, fy - 0.2, fz - 0.8), (fx + 0.4, fy + 0.2, fz + 0.8), 0.08, "dark", seg=1, m=body)
    K.box((-14.6, WY - 0.3, 2.0), (-14.3, WY - 0.1, 2.4), "red_light", m=body)
    K.box((30.3, WY - 0.3, 2.0), (30.6, WY - 0.1, 2.4), "green_mark", m=body)
    spots = [(v3(22.0, WY + 0.25, 1.6), (0, 1, 0), (0, 0, -1), 1.0), (v3(-6.0, WY + 0.25, 1.6), (0, 1, 0), (0, 0, -1), 1.0),
             (v3(15.7, 8.6, 18.6), (1, 0, 0), (0, 1, 0), 0.8), (v3(0.3, 8.6, 18.6), (-1, 0, 0), (0, 1, 0), 0.8)]
    gp = gear(g, 4.2, 3.4, 6.0, nose_r=0.6, main_r=0.8)
    # boost: reheat plumes behind the pods (the game gives the A-10 an afterburner to get it off the ground)
    return finish("a10", g, body, spots, gp, plume([(8 + s * 3.2, 8.0, 14.6) for s in (1, -1)], 1.0, 4.2),
                  store_parts(g, ["mav", "mav", "mav", "mav", "aim9", "aim9"]))


JETS2 = {"f22": f22, "f35": f35, "su35": su35, "mig29": mig29, "a10": a10}

if __name__ == "__main__":
    for k, fn in JETS2.items():
        parts, g = fn()
        lo, hi = parts["body"].bounds()
        print(k, sum(p.quad_count() for p in parts.values()), np.round(lo, 1), np.round(hi, 1))
