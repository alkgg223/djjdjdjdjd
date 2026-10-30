"""Smooth F-16C mesh (1 unit = 0.375 block, front -Z, ground y = 0 with the gear down). Every point the game uses
(seat, cannon, nozzle, gear pivots, pylons, wing tips) comes from jet.GEO."""
import math

import numpy as np

import jet as J
import meshkit as K
import vehmesh as V
from helis_mesh import stations_loft, foil
from meshkit import Mesh, v3


def aim9(x, y, z0):
    """AIM-9 on a wing tip rail, nose at z0 pointing -Z"""
    m = Mesh()
    K.sphere((x, y, z0 + 0.45), 0.25, 0.25, 0.45, "lens", n=10, rows=5, m=m)
    K.cylinder("z", (x, y), 0.25, z0 + 0.45, z0 + 7.6, "white", n=10, m=m)
    K.cylinder("z", (x, y), 0.26, z0 + 1.9, z0 + 2.3, "yellow", n=10, caps=(False, False), m=m)
    for a in (0, 90, 180, 270):
        f = Mesh()
        K.plate([v3(x + 0.24, y, z0 + 1.6), v3(x + 0.65, y, z0 + 1.3), v3(x + 0.65, y, z0 + 1.05), v3(x + 0.24, y, z0 + 0.9)], 0.05, "white", m=f)
        K.plate([v3(x + 0.24, y, z0 + 7.6), v3(x + 0.85, y, z0 + 7.6), v3(x + 0.85, y, z0 + 7.1), v3(x + 0.24, y, z0 + 6.4)], 0.05, "white", m=f)
        m.extend(f.rotate("z", a + 45, (x, y, 0)))
    return m


def maverick(x, y, z0):
    """AGM-65 under a pylon, nose at z0"""
    m = Mesh()
    K.sphere((x, y, z0 + 0.6), 0.42, 0.42, 0.6, "lens", n=10, rows=5, m=m)
    K.cylinder("z", (x, y), 0.42, z0 + 0.6, z0 + 6.6, "olive", n=12, m=m)
    K.cylinder("z", (x, y), 0.43, z0 + 1.2, z0 + 1.5, "yellow", n=12, caps=(False, False), m=m)
    for a in (0, 90, 180, 270):
        f = Mesh()
        K.plate([v3(x + 0.4, y, z0 + 2.4), v3(x + 1.0, y, z0 + 4.6), v3(x + 1.0, y, z0 + 5.4), v3(x + 0.4, y, z0 + 5.4)], 0.06, "olive", m=f)
        K.plate([v3(x + 0.4, y, z0 + 5.8), v3(x + 0.8, y, z0 + 6.3), v3(x + 0.8, y, z0 + 6.6), v3(x + 0.4, y, z0 + 6.6)], 0.06, "olive", m=f)
        m.extend(f.rotate("z", a + 45, (x, y, 0)))
    return m


def f16():
    g = J.GEO.get("f16") or (J.f16(), J.GEO["f16"])[1]
    WY = J.WING_Y
    body = Mesh()
    # fuselage: needle nose and radome, the forward body, the wide centre body, the tail cone to the nozzle
    stations_loft(body, [
        (-16.0, 5.5, 0.04, 0.04, 2.0), (-15.4, 5.5, 0.35, 0.35, 2.0), (-14.4, 5.5, 0.75, 0.72, 2.0), (-12.8, 5.55, 1.2, 1.15, 2.0),
        (-11.0, 5.65, 1.55, 1.5, 2.1), (-8.0, 5.7, 1.85, 1.7, 2.3), (-5.0, 5.6, 2.15, 1.8, 2.5), (-2.0, 5.6, 2.4, 1.8, 2.7),
        (12.0, 5.6, 2.4, 1.8, 2.7), (15.0, 5.6, 2.15, 1.9, 2.3), (19.0, 5.6, 1.95, 1.95, 2.0), (21.4, 5.6, 1.78, 1.78, 2.0),
    ], "paint", n=24, caps=(False, True))
    K.cylinder("z", (8.0, 5.5), 0.05, -16.3, -15.9, "steel", n=6, m=body)
    # nozzle: turkey feathers tapering to the exhaust, black inside
    K.cylinder("z", (8.0, 5.6), 1.72, 21.3, 22.4, "gunmetal", n=24, r1=1.6, caps=(False, False), m=body)
    K.cylinder("z", (8.0, 5.6), 1.6, 22.4, 24.2, "gunmetal", n=24, r1=1.42, caps=(False, False), m=body)
    for k in range(12):
        a = math.radians(k * 30)
        x, y = 8 + math.cos(a) * 1.52, 5.6 + math.sin(a) * 1.52
        K.tube([(x, y, 22.4), (8 + math.cos(a) * 1.44, 5.6 + math.sin(a) * 1.44, 24.2)], 0.05, "steel", n=4, m=body)
    K.cylinder("z", (8.0, 5.6), 1.3, 24.0, 24.15, "black", n=20, caps=(False, True), m=body)
    K.torus("z", (8.0, 5.6), 24.2, 1.42, 0.06, "steel", n=24, k=4, m=body)
    # chin intake under the forward body, its dark mouth
    stations_loft(body, [
        (-7.5, 3.35, 1.62, 0.98, 3.0), (-7.2, 3.35, 1.72, 1.08, 3.0), (-3.0, 3.45, 1.7, 1.1, 3.0), (2.0, 3.7, 1.55, 1.0, 3.0),
        (6.0, 4.3, 1.0, 0.5, 2.6), (8.0, 4.7, 0.3, 0.2, 2.2),
    ], "paint", n=20, caps=(False, True))
    mouth = [v3(8 + x, 3.35 + y, -7.45) for x, y in K.superellipse(1.5, 0.88, 3.0, 20)]
    K.cap(body, mouth, "black", np.array([0.0, 0.0, -1.0]))
    # leading edge extensions blending the forward body into the wings
    lex = [(8.0, -12.0), (6.1, -10.6), (5.3, -8.0), (4.6, -5.0), (3.9, -2.0), (3.4, 0.6), (3.6, 3.0)]
    outline = lex + [(16 - x, z) for x, z in reversed(lex)]
    K.plate([v3(x, WY + 0.15, z) for x, z in outline], 0.34, "paint", bevel=0.12, m=body)
    # dorsal spine behind the canopy
    stations_loft(body, [(-5.4, 7.0, 0.3, 0.3, 2.0), (-4.4, 7.15, 1.0, 0.95, 2.3), (6.0, 7.2, 1.0, 0.8, 2.4), (13.5, 7.1, 0.75, 0.55, 2.4),
                         (16.0, 6.9, 0.25, 0.2, 2.0)], "paint", n=16)
    # bubble canopy, its frame bow, the seat headrest and the HUD
    K.sphere((8.0, 7.2, -8.3), 1.45, 2.05, 4.0, "glass", n=20, rows=8, top_only=True, m=body)
    bow = [(8 + 1.42 * math.cos(math.radians(a)), 7.2 + 1.95 * math.sin(math.radians(a)), -5.4) for a in range(0, 181, 15)]
    K.tube(bow, 0.09, "dark", n=5, m=body)
    K.rbox((7.25, 6.6, -7.2), (8.75, 8.8, -6.3), 0.25, "dark", seg=1, m=body)
    K.rbox((7.3, 7.5, -10.4), (8.7, 8.1, -10.0), 0.1, "dark", seg=1, m=body)
    K.box((7.4, 8.1, -10.3), (8.6, 8.5, -10.28), "glow", m=body, faces="z")
    # wings, stabilators, the fin and the ventral fins
    for s in (1, -1):
        xr = 8 + s * 2.2
        foil(body, (xr, WY, -1.0), (8 + s * 12.6, WY, 9.4), 13.2, 1.2, 0.05, "paint", thick_dir=(0, 1, 0), n=12)
        foil(body, (8 + s * 2.0, WY + 0.3, 16.4), (8 + s * 7.6, WY - 0.4, 22.0), 6.0, 0.6, 0.06, "paint", thick_dir=(0, 1, 0), n=10)
        K.plate([v3(8 + s * 1.6, 2.4, 15.4), v3(8 + s * 1.6, 3.9, 15.4), v3(8 + s * 1.5, 3.9, 18.6), v3(8 + s * 1.6, 2.6, 18.6)], 0.14, "paint", m=body)
        # wing tip rail and the pylons
        K.rbox((8 + s * 12.95 - 0.2, WY - 0.25, 3.6), (8 + s * 12.95 + 0.2, WY + 0.25, 11.2), 0.1, "paint_dark", seg=1, m=body)
        K.box((8 + s * 12.7 - 0.12, WY - 0.08, 9.35), (8 + s * 12.7 + 0.12, WY + 0.12, 9.85), "red_light" if s < 0 else "green_mark", m=body)
    foil(body, (8.0, 7.0, 13.2), (8.0, 13.2, 19.4), 8.6, 3.0, 0.07, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0), n=10)
    K.rbox((7.62, 12.9, 19.2), (8.38, 13.45, 22.4), 0.15, "paint_dark", seg=1, m=body)
    K.rbox((7.4, 7.4, 21.2), (8.6, 8.3, 23.4), 0.3, "paint", seg=1, m=body)
    K.box((7.84, 12.4, 22.38), (8.16, 12.8, 22.5), "glow", m=body)
    for (px, py, pz) in g["pylons"]:
        K.plate([v3(px, py + 0.45, pz + 0.6), v3(px, WY - 0.1, pz + 0.2), v3(px, WY - 0.1, pz + 5.4), v3(px, py + 0.45, pz + 5.2)], 0.3, "paint_dark",
                bevel=0.08, m=body)
    # gun port, flare dispensers
    K.cylinder("z", (5.75, 5.6), 0.22, -5.2, -4.0, "black", n=8, m=body)
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.4, fy - 0.2, fz - 0.8), (fx + 0.4, fy + 0.2, fz + 0.8), 0.08, "dark", seg=1, m=body)
    spots = [(v3(16.4, WY + 0.25, 8.0), (0, 1, 0), (0, 0, -1), 0.9), (v3(-0.4, WY + 0.25, 8.0), (0, 1, 0), (0, 0, -1), 0.9),
             (v3(8.22, 10.6, 19.6), (1, 0, 0), (0, 1, 0), 0.75), (v3(7.78, 10.6, 19.6), (-1, 0, 0), (0, 1, 0), 0.75),
             (v3(6.29, 3.5, -2.5), (-1, 0, 0), (0, 1, 0), 0.55), (v3(9.71, 3.5, -2.5), (1, 0, 0), (0, 1, 0), 0.55)]
    parts = {"body": body, "mark_green": V.team_marks(spots, True), "mark_red": V.team_marks(spots, False)}
    # gear (folding about its pivots)
    nx, ny, nz = g["gearNose"]
    gn = Mesh()
    K.tube([(nx, ny, nz), (nx, 1.0, nz - 0.1)], 0.16, "steel", n=8, m=gn)
    K.rbox((nx - 0.38, 0.7, nz - 0.45), (nx + 0.38, 1.4, nz - 0.05), 0.1, "steel", seg=1, m=gn)
    K.cylinder("x", (0.75, nz - 0.2), 0.75, nx - 0.28, nx + 0.28, "rubber", n=14, bevel=0.1, m=gn)
    K.cylinder("x", (0.75, nz - 0.2), 0.42, nx - 0.31, nx + 0.31, "light_grey", n=10, m=gn)
    K.sphere((nx, 2.3, nz - 0.6), 0.12, 0.12, 0.06, "glow", n=8, rows=3, m=gn)
    K.plate([v3(nx + 0.75, 2.0, nz - 0.9), v3(nx + 0.75, 3.1, nz - 0.9), v3(nx + 0.75, 3.1, nz + 1.4), v3(nx + 0.75, 2.0, nz + 1.4)], 0.06, "paint", m=gn)
    parts["gear_nose"] = gn
    mx, my, mz = g["gearMain"]
    gm = Mesh()
    for s in (-1, 1):
        x = mx + s * 3.1
        K.tube([(x, my, mz), (x, 1.0, mz + 0.2)], 0.17, "steel", n=8, m=gm)
        K.tube([(x - s * 0.7, my, mz), (x, 2.0, mz + 0.1)], 0.08, "steel", n=6, m=gm)
        K.cylinder("x", (1.0, mz + 0.2), 1.0, x - 0.38, x + 0.38, "rubber", n=16, bevel=0.12, m=gm)
        K.cylinder("x", (1.0, mz + 0.2), 0.55, x - 0.41, x + 0.41, "light_grey", n=12, m=gm)
        K.plate([v3(x + s * 0.5, 1.6, mz - 1.1), v3(x + s * 0.5, my, mz - 1.1), v3(x + s * 0.5, my, mz + 1.3), v3(x + s * 0.5, 1.6, mz + 1.3)], 0.08, "paint",
                m=gm)
    parts["gear_main"] = gm
    for i, (px, py, pz) in enumerate(g["pylons"]):
        parts[f"mav_{i}"] = maverick(px, py, pz)
    parts["aim9_l"] = aim9(8 - 12.95, WY, 3.0)
    parts["aim9_r"] = aim9(8 + 12.95, WY, 3.0)
    # afterburner flame: a glowing cone with shock diamonds
    bx, by, bz = g["burner"]
    bn = Mesh()
    K.cylinder("z", (bx, by), 1.3, bz, bz + 1.4, "amber", n=16, r1=1.0, caps=(True, False), m=bn)
    K.cylinder("z", (bx, by), 1.0, bz + 1.4, bz + 3.2, "amber", n=16, r1=0.6, caps=(False, False), m=bn)
    K.cylinder("z", (bx, by), 0.6, bz + 3.2, bz + 5.0, "amber", n=12, r1=0.05, caps=(False, False), m=bn)
    for i in range(4):
        z = bz + 0.5 + i * 1.0
        K.sphere((bx, by, z), 0.55 - i * 0.1, 0.55 - i * 0.1, 0.35, "glow", n=10, rows=4, m=bn)
    parts["burner"] = bn
    return parts, g


JETS = {"f16": f16}
