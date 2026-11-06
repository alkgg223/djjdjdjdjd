"""Smooth B-2 style flying wing (1 unit = 1 block, front -Z, origin (8, 0, 8) under the middle of the wing).

Built at 0.6 of the real size from the planform in metres: a 33 degree swept leading edge from the nose to the tips,
the saw-tooth "double W" trailing edge, a deep centre body with the cockpit hump, the jagged engine intakes on top
and the exhaust troughs behind them. The underside is flatter than the top.

Parts: body (team-tinted dark grey), mark_green / mark_red (small team flashes on the outer wings), bay (the open bomb
bay doors, drawn while a bomb drops)."""
import math

import numpy as np

import meshkit as K
import aircraft_kit as AK
import cockpit_kit as CK
from meshkit import Mesh, v3

SCALE = 0.6
SWEEP = math.tan(math.radians(33.0))
HALF_SPAN = 26.2
NOSE_Z = -10.6          # metres: where the nose sits relative to the model origin (along z)
MID_Y = 1.9             # model units: the mid plane of the wing
# trailing edge (half), metres: centre point, inner notch, rear point, tip
TE = [(0.0, 21.0), (6.0, 17.4), (13.2, 21.0), (HALF_SPAN, 18.2)]
# spanwise stations (metres) and the section thickness there
STATIONS = [(0.0, 3.7), (1.3, 3.5), (2.6, 3.05), (4.0, 2.5), (6.0, 1.9), (8.0, 1.45), (10.5, 1.12), (13.2, 0.92),
            (16.5, 0.66), (20.0, 0.46), (23.5, 0.3), (HALF_SPAN, 0.12)]


def te_z(x):
    x = abs(x)
    for (x0, z0), (x1, z1) in zip(TE, TE[1:]):
        if x <= x1 + 1e-9:
            return z0 + (z1 - z0) * (x - x0) / (x1 - x0)
    return TE[-1][1]


def le_z(x):
    return abs(x) * SWEEP


def to_model(x, y, z):
    """metres (x right, y up from the mid plane, z back from the nose) -> model units"""
    return v3(8.0 + x * SCALE, MID_Y + y * SCALE, (z + NOSE_Z) * SCALE + 8.0)


def section(x, thick, n=16):
    """one chordwise ring at span position x: rounded leading edge, sharp trailing edge, top fuller than bottom"""
    le, te = le_z(x), te_z(x)
    chord = te - le
    ring = []
    for u, v in K.airfoil(chord, thick / chord, n):
        # the airfoil helper gives the half thickness symmetric: push most of it to the top
        v = v * (1.45 if v > 0 else 0.55)
        ring.append(to_model(x, v, le + u))
    return ring


def surface_y(x, z, top=True):
    """model y of the skin above (or below) the point x, z (metres), for parts laid on the wing"""
    # thickness at x by interpolating the stations
    xs = [s[0] for s in STATIONS]
    th = float(np.interp(abs(x), xs, [s[1] for s in STATIONS]))
    le, te = le_z(x), te_z(x)
    chord = te - le
    u = min(max((z - le) / chord, 0.0), 1.0)
    t = th / chord
    yt = 5 * t * (0.2969 * math.sqrt(u) - 0.126 * u - 0.3516 * u * u + 0.2843 * u ** 3 - 0.1036 * u ** 4) * chord
    yt *= 1.45 if top else -0.55
    return MID_Y + yt * SCALE


def on_skin(x, z, lift=0.02, top=True):
    return v3(8.0 + x * SCALE, surface_y(x, z, top) + (lift if top else -lift), (z + NOSE_Z) * SCALE + 8.0)


def skin_patch(m, corners, mat, lift=0.03, top=True):
    """a thin plate following the skin through corner points given in metres (x, z)"""
    pts = [on_skin(x, z, lift, top) for x, z in corners]
    K.plate(pts, 0.04, mat, m=m)


def intake(m, side):
    """engine intake hump on the top skin with its saw-tooth lip and the dark mouth"""
    x0, x1 = 3.3 * side, 6.6 * side
    z0, z1 = 5.6, 10.8
    rings = []
    for k in range(7):
        f = k / 6
        z = z0 + (z1 - z0) * f
        # hump height grows from the lip, then fairs back into the skin
        h = (0.55 if f < 0.25 else 0.55 * (1 - (f - 0.25) / 0.75) ** 1.4) + 0.02
        ring = []
        for j in range(10):
            a = math.pi * j / 9
            xx = (x0 + x1) / 2 + math.cos(a) * (x1 - x0) / 2
            base = surface_y(xx, z)
            ring.append(v3(8.0 + xx * SCALE, base + math.sin(a) * h * SCALE * 1.6 - 0.02, (z + NOSE_Z) * SCALE + 8.0))
        rings.append(ring)
    K.loft(rings, "paint", caps=(False, False), closed=False, m=m)
    # the lip: a saw-tooth dark opening facing forward
    zl = z0 + 0.05
    mouth = []
    for j in range(10):
        a = math.pi * j / 9
        xx = (x0 + x1) / 2 + math.cos(a) * (x1 - x0) / 2 * 0.92
        mouth.append(v3(8.0 + xx * SCALE, surface_y(xx, zl) + math.sin(a) * 0.5 * SCALE * 1.6, (zl + NOSE_Z) * SCALE + 8.0 - 0.01))
    base_l = v3(8.0 + x1 * SCALE * 0.98, surface_y(x1, zl), (zl + NOSE_Z) * SCALE + 8.0 - 0.01)
    base_r = v3(8.0 + x0 * SCALE * 1.02, surface_y(x0, zl), (zl + NOSE_Z) * SCALE + 8.0 - 0.01)
    K.cap(m, mouth + [base_l, base_r][::1], "black", np.array([0.0, 0.0, -1.0]))


def b2():
    body = Mesh()
    # the wing: right tip -> centre -> left tip
    st = [(-x, t) for x, t in reversed(STATIONS[1:])] + STATIONS
    rings = [section(x, t) for x, t in st]
    K.loft(rings, "paint", caps=(True, True), m=body)
    # cockpit: four windows on the hump, dark glass with a frame
    for side in (-1, 1):
        skin_patch(body, [(0.25 * side, 3.1), (1.35 * side, 3.25), (1.25 * side, 4.25), (0.25 * side, 4.15)], "glass_clear", 0.025)
        skin_patch(body, [(1.55 * side, 3.4), (2.15 * side, 3.7), (2.0 * side, 4.4), (1.45 * side, 4.3)], "glass_clear", 0.025)
    # engine intakes and the exhaust troughs behind them
    for side in (-1, 1):
        intake(body, side)
        skin_patch(body, [(2.6 * side, 14.6), (7.6 * side, 13.4), (8.2 * side, 15.6), (3.0 * side, 17.0)], "gunmetal", 0.02)
        skin_patch(body, [(3.0 * side, 15.0), (7.4 * side, 13.9), (7.8 * side, 15.2), (3.3 * side, 16.4)], "black", 0.035)
        # landing gear doors and the bomb bay outline underneath (darker lines on the skin)
        skin_patch(body, [(0.6 * side, 7.5), (2.6 * side, 7.5), (2.6 * side, 13.5), (0.6 * side, 13.5)], "dark", 0.02, top=False)
        skin_patch(body, [(5.0 * side, 9.0), (6.4 * side, 9.0), (6.4 * side, 13.0), (5.0 * side, 13.0)], "dark", 0.02, top=False)
        # navigation light at the tip (red left, white right) and a dim formation strip
        tip_z = le_z(HALF_SPAN) + 0.4
        K.sphere(tuple(on_skin(HALF_SPAN * side * 0.995, tip_z, 0.0)), 0.07, 0.07, 0.07, "red_light" if side < 0 else "glow", n=6, rows=3, m=body)
    # flight control surfaces: thin slightly darker strips along the trailing edge (elevons, drag rudders)
    for side in (-1, 1):
        for xa, xb in ((7.2, 12.6), (14.0, 19.5), (20.0, 25.4)):
            za, zb = te_z(xa) - 1.3, te_z(xb) - 1.3
            skin_patch(body, [(xa * side, za), (xb * side, zb), (xb * side, te_z(xb) - 0.1), (xa * side, te_z(xa) - 0.1)], "paint_dark", 0.012)
    marks = {}
    for team, mat in (("green", "green_mark"), ("red", "red_mark")):
        m = Mesh()
        for side in (-1, 1):
            skin_patch(m, [(19.0 * side, 13.1), (20.6 * side, 14.1), (20.6 * side, 15.6), (19.0 * side, 15.4)], mat, 0.03)
        marks["mark_" + team] = m
    # bomb bay doors hanging open below the centre body
    bay = Mesh()
    for side in (-1, 1):
        x0, x1 = 0.6 * side, 2.6 * side
        hinge = [on_skin(x1, 7.5, 0.0, top=False), on_skin(x1, 13.5, 0.0, top=False)]
        drop = 1.6
        tip = [h + np.array([-(x1 - x0) * SCALE * 0.15, -drop * SCALE, 0]) for h in hinge]
        K.plate([hinge[0], hinge[1], tip[1], tip[0]], 0.05, "paint_dark", m=bay)
        K.box((8.0 + x0 * SCALE - 0.08, MID_Y - 0.5, (7.6 + NOSE_Z) * SCALE + 8), (8.0 + x0 * SCALE + 0.08, MID_Y - 0.35, (13.4 + NOSE_Z) * SCALE + 8), "black", m=bay)
    return {"body": body, **marks, "bay": bay}, None


# where the game needs things (model units = blocks here; the jet code takes them from GEO)
GEO = {"b2": dict(seat=(8.0, 2.7, 3.4), cannon=(8.0, 1.6, 2.0), nozzle=(8.0, 2.7, 11.4),
                  gearNose=(8.0, 1.3, 4.6), gearMain=(8.0, 1.3, 9.4),
                  tips=[(-7.6, 1.9, 12.2), (23.6, 1.9, 12.2)], pylons=[(7.4, 1.0, 7.8), (8.6, 1.0, 7.8), (7.4, 1.0, 9.6), (8.6, 1.0, 9.6)],
                  flares=[(6.0, 1.5, 11.0), (10.0, 1.5, 11.0)],
                  ends=[(8.0, 1.9, 1.7), (8.0, 2.0, 14.2), (-7.4, 1.9, 12.4), (23.4, 1.9, 12.4), (8.0, 3.4, 6.0), (8.0, 1.1, 8.0)],
                  pivotY=1.9, size=(8.0, 2.6))}


def b2_gear():
    """the nose leg and the two main bogies (folding into the wing), wheels touching y = 0"""
    g = GEO["b2"]
    nx, ny, nz = g["gearNose"]
    gn = Mesh()
    K.tube([(nx, ny, nz), (nx, 0.3, nz - 0.05)], 0.07, "steel", n=8, m=gn)
    for dx in (-0.16, 0.16):
        K.cylinder("x", (0.3, nz - 0.08), 0.3, nx + dx - 0.1, nx + dx + 0.1, "rubber", n=12, bevel=0.04, m=gn)
        K.cylinder("x", (0.3, nz - 0.08), 0.16, nx + dx - 0.11, nx + dx + 0.11, "light_grey", n=8, m=gn)
    K.plate([v3(nx + 0.35, 0.8, nz - 0.5), v3(nx + 0.35, 1.3, nz - 0.5), v3(nx + 0.35, 1.3, nz + 0.8), v3(nx + 0.35, 0.8, nz + 0.8)], 0.03,
            "paint", m=gn)
    mx, my, mz = g["gearMain"]
    gm = Mesh()
    for s in (-1, 1):
        x = mx + s * 3.0
        K.tube([(x, my, mz), (x, 0.36, mz + 0.1)], 0.09, "steel", n=8, m=gm)
        for dz in (-0.45, 0.45):
            for dx in (-0.2, 0.2):
                K.cylinder("x", (0.34, mz + 0.1 + dz), 0.34, x + dx - 0.1, x + dx + 0.1, "rubber", n=12, bevel=0.04, m=gm)
                K.cylinder("x", (0.34, mz + 0.1 + dz), 0.18, x + dx - 0.11, x + dx + 0.11, "light_grey", n=8, m=gm)
        K.box((x - 0.3, 0.3, mz - 0.6), (x + 0.3, 0.42, mz + 0.8), "steel", m=gm)
        K.plate([v3(x + s * 0.45, 0.7, mz - 0.9), v3(x + s * 0.45, 1.3, mz - 0.9), v3(x + s * 0.45, 1.3, mz + 1.2), v3(x + s * 0.45, 0.7, mz + 1.2)],
                0.03, "paint", m=gm)
    return gn, gm


def b2_all():
    parts, _ = b2()
    AK.reskin(parts["body"])
    parts["gear_nose"], parts["gear_main"] = b2_gear()
    # the flight deck in the hump under the four windows (model units are metres here); the hull is lined inside
    eye = (8.0 - 0.45, 3.2, 4.75)
    deck = ((5.9, 2.0, 2.4), (10.1, 3.75, 6.4))
    pit, stick_p, thr_p = CK.cockpit(parts["body"], eye, 1.0, displays=3, tub_w=0.45, enclosed=(deck, deck))
    # the second seat, the mission commander's, on the right
    co, _, _ = CK.cockpit(parts["body"], (8.0 + 0.45, 3.2, 4.75), 1.0, displays=3, tub_w=0.45, enclosed=(deck, deck), station_only=True)
    pit["cockpit"].extend(co["cockpit"])
    pit["cockpit"].extend(co["stick"])
    pit["cockpit"].extend(co["throttle"])
    parts.update(pit)
    g = GEO["b2"]
    g["eye"], g["seat"], g["stick"], g["throttle"] = eye, AK.place_seat(eye, 1.0), stick_p, thr_p
    return parts, g


BOMBERS = {"b2": b2_all}

if __name__ == "__main__":
    parts, _ = b2()
    for k, v in parts.items():
        lo, hi = v.bounds()
        print(k, v.quad_count(), np.round(lo, 2), np.round(hi, 2))
