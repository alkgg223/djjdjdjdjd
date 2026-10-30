"""Smooth tank meshes. Every point the game uses (turret ring, gun trunnion, muzzle, wheels, tracks) comes from the
GEO tables of armor.py / armor2.py so the physics and the effects stay where they were."""
import math

import numpy as np

import armor as AR
import armor2 as A2
import meshkit as K
import vehmesh as V
from meshkit import Mesh, v3


def side_prism(m, profile_zy, x0, x1, mat="paint", fillet=0.3, bevel=0.25):
    """a hull block: side view outline (z, y) extruded across x0..x1 with rounded corners and chamfered side edges"""
    poly = K.fillet(profile_zy, fillet, seg=2) if fillet > 0 else profile_zy
    return K.prism(poly, "x", x0, x1, mat, bevel=bevel, m=m)


def skirt(m, x0, x1, y0, y1, z0, z1, mat="paint", r=0.18):
    """a side skirt panel with rounded corners"""
    K.prism(K.fillet([(z0, y0), (z1, y0), (z1, y1), (z0, y1)], r, seg=1), "x", x0, x1, mat, bevel=0.06, m=m)


def top_loft(m, outline_xz, y0, y1, mat="paint", inset=0.25, fillet=0.4, top_bevel=0.22):
    """a turret or superstructure: plan outline (x, z) from y0 up to y1, the sides leaning in by `inset`, the roof edge rounded"""
    base = K.fillet(outline_xz, fillet, seg=2)
    top = K.offset(base, inset)
    roof = K.offset(top, top_bevel)
    rings = [K.plane_pts(base, "y", y0), K.plane_pts(top, "y", y1 - top_bevel), K.plane_pts(roof, "y", y1)]
    return K.loft(rings, mat, m=m)


def track_loop(m, tx, g, links_front, links_back, width=2.6):
    """the bent ends of both tracks (static) round the idler and the sprocket, joining the animated runs"""
    by, bz = g["bottom"]
    ty, tz = g["top"]
    iy, iz, ir = g["idler"]
    sy, sz, sr = g["sprocket"]
    yb, yt = by + 0.18, ty + 0.18
    R1, R2 = ir + 0.18, sr + 0.18
    front = [(bz, yb)]
    for k in range(13):
        a = math.radians(-60 - 210 * k / 12)
        front.append((iz + R1 * math.cos(a), iy + R1 * math.sin(a)))
    front.append((tz, yt))
    end_top = tz + 29 * V.LINK
    end_bottom = bz + 29 * V.LINK
    back = [(end_top, yt)]
    for k in range(13):
        a = math.radians(120 - 210 * k / 12)
        back.append((sz + R2 * math.cos(a), sy + R2 * math.sin(a)))
    back.append((end_bottom, yb))
    for path in (front, back):
        _links_along(m, tx, path, width)


def _links_along(m, tx, path, width):
    """a smooth band of track along a side-view path, with cleats every link"""
    pts = [np.array(p, dtype=float) for p in path]
    rings = []
    for i, p in enumerate(pts):
        d = pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]
        d = d / max(np.linalg.norm(d), 1e-9)
        nrm = np.array([-d[1], d[0]])          # left of the direction in (z, y)
        ring = []
        for xo, h in ((-width / 2, -0.18), (width / 2, -0.18), (width / 2, 0.18), (-width / 2, 0.18)):
            q = p + nrm * h
            ring.append(v3(tx + xo, q[1], q[0]))
        rings.append(ring)
    K.loft(rings, "track", m=m, smooth=False)


def assemble(parts, g, team=(0.42, 0.50, 0.30), turret_yaw=0.0, gun_pitch=0.0):
    """everything in hull space, the way TankRenderer draws it (for previews)"""
    out = []
    body = parts["body"].copy()
    out.append((body, team))
    out.append((parts["mark_green"].copy(), None))
    gear = gear_parts()
    wy, wr = g["wheel"]
    for tx in g["trackX"]:
        for z in g["wheelsZ"]:
            w = gear["roadwheel"].copy().scale((1, wr / 1.3, wr / 1.3), (8, 8, 8)).translate((tx - 8, wy - 8, z - 8))
            out.append((w, team))
        iy, iz, ir = g["idler"]
        out.append((gear["idler"].copy().translate((tx - 8, iy - 8, iz - 8)), team))
        sy, sz, sr = g["sprocket"]
        out.append((gear["sprocket"].copy().translate((tx - 8, sy - 8, sz - 8)), team))
        by, bz = g["bottom"]
        out.append((gear["track_bottom"].copy().translate((tx - 8, by, bz + 8)), None))
        ty, tz = g["top"]
        out.append((gear["track_top"].copy().translate((tx - 8, ty, tz + 8 + V.LINK)), None))
    px, pz = g["turret"]
    tur = parts["turret"].copy().extend(parts["tmark_green"].copy()).rotate("y", -turret_yaw, (px, 0, pz))
    gun = parts["gun"].copy()
    gy, gz = g["gun"]
    gun.rotate("x", -gun_pitch, (px, gy, gz)).rotate("y", -turret_yaw, (px, 0, pz))
    out += [(tur, team), (gun, team)]
    return out


_GEAR = {}


def gear_parts():
    if not _GEAR:
        _GEAR["roadwheel"] = V.roadwheel(1.3)
        _GEAR["idler"] = V.idler(1.15)
        _GEAR["sprocket"] = V.sprocket(1.3)
        _GEAR["track_bottom"] = V.track_run(False)
        _GEAR["track_top"] = V.track_run(True)
    return _GEAR


def marks_for(spots):
    return {"mark_green": V.team_marks(spots, True), "mark_red": V.team_marks(spots, False)}


# ============================================================================================ Leopard 2A6
def leopard():
    A2.leopard()
    g = A2.GEO["leopard"]
    body, tur, gun = Mesh(), Mesh(), Mesh()
    # hull: lower tub between the tracks, upper hull over them with the long, flat upper glacis
    side_prism(body, [(-5.0, 0.9), (21.4, 0.9), (21.4, 3.9), (-6.5, 3.9), (-6.3, 1.9)], 3.8, 12.2, fillet=0.35)
    side_prism(body, [(-6.5, 3.7), (21.9, 3.7), (21.9, 5.6), (-3.6, 5.6), (-6.7, 4.4)], 1.0, 15.0, fillet=0.4, bevel=0.3)
    # rear deck: grille slats and the exhaust louvres
    for i in range(8):
        z = 13.6 + i * 0.95
        K.box((3.5, 5.55, z), (12.5, 5.72, z + 0.55), "dark", m=body, faces="xXYzZ")
    for x0 in (1.2, 13.5):
        K.box((x0, 5.55, 17.6), (x0 + 1.3, 5.78, 21.4), "dark", m=body, faces="xXYzZ")
    # driver's hatch and periscopes, lights, tow eyes
    K.rbox((7.0, 5.55, -3.3), (9.6, 5.85, -1.4), 0.15, "paint", seg=1, m=body)
    for x in (6.7, 7.7, 8.7):
        V.periscope(body, x + 0.35, 5.6, -3.65, w=0.75, d=0.45, h=0.3)
    for x in (3.4, 12.6):
        V.light(body, x, 4.85, -6.45, w=0.9, h=0.45)
        V.light(body, x, 5.0, 22.0, w=0.7, h=0.35, face=1, mat="red_light")
        K.cylinder("z", (x, 2.6), 0.28, -6.25, -6.75, "steel", n=10, bevel=0.06, m=body)
    K.rbox((6.6, 2.5, 21.85), (9.4, 3.9, 22.5), 0.15, "paint_mid", seg=1, m=body)      # telephone box
    # side skirts: heavy front pair, lighter panels behind
    for left in (True, False):
        for i in range(7):
            z0 = -4.4 + i * 3.6
            heavy = i < 2
            x0, x1 = (0.25 if heavy else 0.5, 1.05) if left else (14.95, 15.75 if heavy else 15.5)
            skirt(body, x0, x1, 1.25 + (i % 2) * 0.15, 3.95, z0 + 0.05, z0 + 3.55)
    for tx in g["trackX"]:
        track_loop(body, tx, g, 0, 0)
    # turret: slab-sided with a long bustle, the arrowhead wedges in front of the cheeks
    top_loft(tur, [(3.0, 5.2), (6.0, 3.3), (10.0, 3.3), (13.0, 5.2), (13.0, 19.6), (12.0, 21.0), (4.0, 21.0), (3.0, 19.6)],
             5.95, 8.8, inset=0.2, fillet=0.45)
    for left in (True, False):
        wedge = K.prism(K.fillet([(3.0, 6.1), (6.45, 3.3), (6.55, 1.0)], 0.18, seg=1), "y", 6.0, 8.65, "paint", bevel=0.14)
        tur.extend(wedge if left else wedge.mirrored_x())
    # roof: gunner's EMES sight (right front), commander's PERI (left), hatches, loader's MG3
    K.rbox((9.4, 8.7, 4.2), (11.9, 9.95, 6.6), 0.22, "paint", seg=1, m=tur)
    K.box((9.7, 9.1, 4.18), (11.6, 9.75, 4.16), "lens", m=tur)
    K.cylinder("y", (5.2, 8.4), 0.55, 8.75, 9.5, "paint", n=16, bevel=0.06, m=tur)
    K.rbox((4.55, 9.5, 7.8), (5.85, 10.15, 9.0), 0.18, "paint_mid", seg=1, m=tur)
    K.box((4.75, 9.62, 7.78), (5.65, 10.0, 7.76), "lens", m=tur)
    sx, sy, sz = g["seat"]
    V.hatch(tur, sx, 8.75, sz, 1.0)
    V.hatch(tur, 10.7, 8.75, 11.6, 0.9)
    V.machine_gun(tur, 10.7, 9.85, 10.2)
    V.smoke_launchers(tur, 2.65, 7.7, 12.6, 4, side=-1)
    V.smoke_launchers(tur, 13.35, 7.7, 12.6, 4, side=1)
    V.antenna(tur, 4.2, 8.75, 16.2, 7.0)
    V.antenna(tur, 11.8, 8.75, 16.2, 7.0)
    K.cylinder("y", (8.0, 15.4), 0.12, 8.75, 10.6, "dark", n=8, m=tur)
    K.sphere((8.0, 10.65, 15.4), 0.25, 0.25, 0.25, "dark", n=10, rows=6, m=tur)
    # stowage on the bustle
    K.rbox((3.4, 8.75, 18.4), (7.3, 9.45, 20.6), 0.3, "canvas_green", seg=1, m=tur)
    K.rbox((8.7, 8.75, 18.6), (12.6, 9.3, 20.6), 0.25, "canvas", seg=1, m=tur)
    # gun: mantlet block between the wedges, the long L55 with its thermal sleeve and bore evacuator
    gy, gz = g["gun"]
    K.rbox((6.55, 6.15, 1.0), (9.45, 8.6, 3.6), 0.35, "paint", seg=2, m=gun)
    V.gun_barrel(gun, 8.0, gy, g["muzzle"], 1.0, 0.48, sleeve=(-3.4, -10.8, -13.6), evac=(-8.8, -6.4), mrs=True)
    cx, cy, cz = g["coax"]
    K.cylinder("z", (cx, cy), 0.12, cz, cz - 0.3, "black", n=8, m=gun)
    spots = [(v3(2.98, 7.5, 11.0), (-1, 0, 0), (0, 1, 0), 0.75), (v3(13.02, 7.5, 11.0), (1, 0, 0), (0, 1, 0), 0.75),
             (v3(0.24, 2.6, 9.0), (-1, 0, 0), (0, 1, 0), 0.65), (v3(15.76, 2.6, 9.0), (1, 0, 0), (0, 1, 0), 0.65),
             (v3(8.0, 4.0, 22.52), (0, 0, 1), (0, 1, 0), 0.5)]
    # turret marks belong to the turret: they turn with it, so they are made part of the turret mesh, hull marks of the hull
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(_split_marks(spots, turret_from=2))
    return parts, g


def _split_marks(spots, turret_from):
    """team marks: the first spots go on the turret (turning with it), the rest on the hull"""
    out = {}
    for team in ("green", "red"):
        out[f"mark_{team}"] = V.team_marks(spots[turret_from:], team == "green")
        out[f"tmark_{team}"] = V.team_marks(spots[:turret_from], team == "green")
    return out


# ============================================================================================ M1A2 Abrams
def abrams():
    import gen_geo
    g = gen_geo.abrams()
    body, tur, gun = Mesh(), Mesh(), Mesh()
    side_prism(body, [(-4.4, 0.9), (20.9, 0.9), (20.9, 3.9), (-6.1, 3.9), (-5.7, 1.6)], 3.8, 12.2, fillet=0.35)
    side_prism(body, [(-6.0, 3.7), (21.4, 3.7), (21.4, 5.9), (-2.4, 5.9), (-6.4, 4.7)], 1.0, 15.0, fillet=0.4, bevel=0.3)
    # spare track links on the glacis, engine deck slats and access panels, rear grille
    for i in range(4):
        sub = K.box((4.4 + i * 1.9, 0, -0.35), (6.0 + i * 1.9, 0.25, 0.35), "track")
        sub.rotate("x", 17, (0, 0, 0)).translate((0, 5.35, -3.6))
        body.extend(sub)
    for i in range(9):
        z = 13.6 + i * 0.78
        K.box((3.0, 5.85, z), (13.0, 6.0, z + 0.42), "dark", m=body, faces="xXYzZ")
    K.rbox((2.2, 5.85, 8.0), (13.8, 6.0, 13.2), 0.06, "paint_mid", seg=1, m=body)
    K.rbox((4.0, 3.2, 21.35), (12.0, 5.4, 21.6), 0.08, "dark", seg=1, m=body)
    K.rbox((6.8, 5.85, -2.3), (9.2, 6.15, -0.6), 0.15, "paint", seg=1, m=body)
    for x in (6.6, 7.6, 8.6):
        V.periscope(body, x + 0.4, 5.9, -2.55, w=0.75, d=0.45, h=0.3)
    for x in (2.2, 13.8):
        K.rbox((x - 0.8, 5.85, -2.5), (x + 0.8, 6.7, -1.7), 0.15, "dark", seg=1, m=body)
        V.light(body, x, 6.28, -2.5, w=1.1, h=0.4)
        V.light(body, x, 4.9, 21.4, w=0.9, h=0.5, face=1, mat="red_light")
        K.cylinder("z", (x + 0.8 * (1 if x > 8 else -1), 2.0), 0.3, 21.4, 21.9, "steel", n=8, bevel=0.06, m=body)
    for x0, x1 in ((1.2, 3.4), (12.6, 14.8)):
        K.rbox((x0, 5.85, 1.5), (x1, 6.75, 6.5), 0.18, "paint_mid", seg=1, m=body)
        K.rbox((x0, 5.85, 16.0), (x1, 6.95, 19.6), 0.18, "paint_mid", seg=1, m=body)
    for left in (True, False):
        for i in range(6):
            z0 = -3.4 + i * 4.0
            x0, x1 = (0.6, 1.05) if left else (14.95, 15.4)
            skirt(body, x0, x1, 1.2, 3.95, z0 + 0.06, z0 + 3.94)
        xr = (0.55, 1.1) if left else (14.9, 15.45)
        K.box((xr[0], 0.9, -3.4), (xr[1], 1.25, 20.6), "rubber", m=body)
    for tx in g["trackX"]:
        track_loop(body, tx, g, 0, 0)
    # turret: wide and low, flat faces, the cheeks angled in to the gun, bustle rack behind
    top_loft(tur, [(3.2, 5.8), (5.3, 1.9), (10.7, 1.9), (12.8, 5.8), (12.8, 14.8), (12.2, 18.2), (3.8, 18.2), (3.2, 14.8)],
             6.3, 8.9, inset=0.4, fillet=0.35)
    for x0, x1 in ((2.45, 3.25), (12.75, 13.55)):
        K.rbox((x0, 6.6, 8.6), (x1, 8.4, 14.2), 0.15, "paint", seg=1, m=tur)
    for x0 in (2.4, 12.8):
        K.rbox((x0, 7.5, 4.4), (x0 + 0.8, 8.6, 6.2), 0.12, "paint_dark", seg=1, m=tur)
        for i in range(3):
            K.cylinder("z", (x0 + 0.4, 7.7 + i * 0.32), 0.13, 4.45, 4.25, "black", n=8, caps=(False, True), m=tur)
    # bustle rack: frame tubes and stowed bags
    for x in (3.05, 12.95):
        V_rail(tur, [(x, 6.6, 18.0), (x, 8.1, 18.0), (x, 8.1, 20.0), (x, 6.6, 20.0)])
    V_rail(tur, [(3.05, 8.1, 20.0), (12.95, 8.1, 20.0)])
    K.box((3.0, 6.55, 18.0), (13.0, 6.7, 20.0), "dark", m=tur)
    for x in (4.6, 8.9):
        K.rbox((x - 0.5, 6.7, 18.2), (x + 2.4, 7.8, 19.7), 0.35, "canvas_green", seg=1, m=tur)
    # commander's cupola with M2, loader's hatch with M240, gunner's sight, CITV, wind sensor, antennas
    cx, cz = g["seat"][0], g["seat"][2]
    K.cylinder("y", (cx, cz), 1.3, 8.85, 9.6, "paint", n=16, bevel=0.1, m=tur)
    for k in range(6):
        a = math.radians(k * 60 + 30)
        K.box((cx + math.sin(a) * 1.25 - 0.22, 9.15, cz - math.cos(a) * 1.25 - 0.22), (cx + math.sin(a) * 1.25 + 0.22, 9.4, cz - math.cos(a) * 1.25 + 0.22), "lens", m=tur)
    K.rbox((cx - 1.1, 9.6, cz + 1.2), (cx + 1.1, 11.6, cz + 1.45), 0.12, "paint", seg=1, m=tur)
    V.machine_gun(tur, cx, 10.2, cz - 1.2, length=3.0)
    V.hatch(tur, 5.4, 8.85, 10.5, 1.0)
    V.machine_gun(tur, 5.4, 9.95, 8.6, length=2.6)
    K.rbox((8.9, 8.8, 4.8), (11.4, 9.95, 6.6), 0.2, "paint", seg=1, m=tur)
    K.box((9.3, 9.1, 4.78), (11.0, 9.7, 4.76), "lens", m=tur)
    K.cylinder("y", (5.2, 6.2), 0.55, 8.85, 9.7, "paint", n=12, bevel=0.06, m=tur)
    K.rbox((4.6, 9.7, 5.6), (5.8, 10.3, 6.8), 0.15, "dark", seg=1, m=tur)
    K.box((4.75, 9.8, 5.58), (5.65, 10.2, 5.56), "lens", m=tur)
    K.cylinder("y", (8.0, 14.1), 0.1, 8.85, 10.6, "steel", n=6, m=tur)
    K.cylinder("x", (10.65, 14.1), 0.08, 7.4, 8.6, "steel", n=6, m=tur)
    V.antenna(tur, 4.0, 8.85, 14.7, 7.2)
    V.antenna(tur, 12.0, 8.85, 14.7, 7.2)
    # gun: recessed mantlet, M256 120 mm
    gx, gy, gz = 8.0, g["gun"][0], g["gun"][1]
    K.rbox((6.0, 6.45, 0.9), (10.0, 8.85, 2.6), 0.3, "paint", seg=1, m=gun)
    V.gun_barrel(gun, gx, gy, g["muzzle"], 0.9, 0.5, sleeve=(-3.0, -5.6, -10.6, -12.8), evac=(-8.8, -6.4), mrs=True)
    K.box((9.5, 7.0, 0.88), (9.9, 7.4, 0.86), "black", m=gun)
    spots = [(v3(2.44, 7.5, 11.4), (-1, 0, 0), (0, 1, 0), 0.7), (v3(13.56, 7.5, 11.4), (1, 0, 0), (0, 1, 0), 0.7),
             (v3(0.59, 2.55, 2.7), (-1, 0, 0), (0, 1, 0), 0.8), (v3(15.41, 2.55, 2.7), (1, 0, 0), (0, 1, 0), 0.8)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(_split_marks(spots, turret_from=2))
    return parts, g


def V_rail(m, pts, r=0.08):
    K.tube(pts, r, "dark", n=6, m=m)


# ============================================================================================ T-90A
def t90():
    A2.t90()
    g = A2.GEO["t90"]
    body, tur, gun = Mesh(), Mesh(), Mesh()
    side_prism(body, [(-4.8, 1.0), (20.6, 1.0), (20.6, 4.0), (-6.3, 4.0), (-5.7, 1.4)], 3.8, 12.2, fillet=0.35)
    side_prism(body, [(-6.2, 3.8), (21.0, 3.8), (21.0, 5.3), (-3.2, 5.3), (-6.5, 3.95)], 1.0, 15.0, fillet=0.4, bevel=0.3)
    # Kontakt-5 bricks on the glacis
    for r in range(3):
        for c in range(6):
            x = 2.0 + c * 2.05
            sub = K.rbox((x, 0, -0.45), (x + 1.85, 0.42, 0.45), 0.08, "paint", seg=1)
            sub.rotate("x", 23, (0, 0, 0)).translate((0, 4.25 + r * 0.42, -5.8 + r * 1.0))
            body.extend(sub)
    K.rbox((3.2, 0.8, -6.9), (12.8, 2.4, -6.2), 0.15, "dark", seg=1, m=body)          # dozer blade
    for x in (2.7, 13.3):
        V.light(body, x, 5.25, -3.6, w=1.1, h=0.5)
        V.light(body, x, 4.65, 21.0, w=0.8, h=0.4, face=1, mat="red_light")
    K.rbox((7.0, 5.25, -2.6), (9.4, 5.5, -0.9), 0.15, "paint", seg=1, m=body)
    for left in (True, False):
        xs = (0.6, 1.0) if left else (15.0, 15.4)
        K.rbox((xs[0], 2.6, -3.6), (xs[1], 3.95, 20.6), 0.08, "rubber", seg=1, m=body)
        for i in range(3):
            z0 = -3.6 + i * 3.2
            x0, x1 = (0.3, 1.05) if left else (14.95, 15.7)
            skirt(body, x0, x1, 2.2, 3.95, z0 + 0.05, z0 + 3.1)
    for i in range(7):
        z = 12.6 + i * 1.0
        K.box((3.2, 5.25, z), (12.8, 5.42, z + 0.55), "dark", m=body, faces="xXYzZ")
    for x0, x1 in ((1.6, 7.2), (8.8, 14.4)):
        K.cylinder("x", (4.4, 22.0), 0.92, x0, x1, "olive", n=14, bevel=0.15, m=body)
        for x in (x0 + 0.5, x1 - 0.5):
            K.cylinder("x", (4.4, 22.0), 0.96, x - 0.1, x + 0.1, "dark", n=14, m=body)
        for x in (x0 + 0.55, x1 - 0.55):
            K.box((x - 0.15, 3.4, 21.0), (x + 0.15, 5.4, 22.6), "dark", m=body)
    K.rbox((0.7, 3.9, 14.8), (1.05, 4.9, 17.2), 0.08, "black", seg=1, m=body)
    for tx in g["trackX"]:
        track_loop(body, tx, g, 0, 0)
    # turret: the low cast dome, Kontakt-5 wedges on the front arc, Shtora dazzlers either side of the gun
    tx, tz = g["turret"]
    rings = []
    for y, rx, rz, dz in ((5.6, 4.9, 5.1, 1.0), (6.7, 4.85, 5.05, 1.1), (7.5, 4.45, 4.7, 1.3), (8.0, 3.7, 4.0, 1.5), (8.15, 2.6, 3.0, 1.6)):
        rings.append([v3(tx + x, y, tz + dz + z) for x, z in K.ellipse(rx, rz, 24)])
    K.loft(rings, "paint", m=tur)
    for left in (True, False):
        wedge = K.prism(K.fillet([(2.9, 6.6), (6.6, 2.4), (7.0, 3.1), (3.9, 7.4)], 0.2, seg=1), "y", 5.7, 7.8, "paint", bevel=0.15)
        tur.extend(wedge if left else wedge.mirrored_x())
        x0 = 4.6 if left else 10.2
        K.rbox((x0, 7.0, 2.7), (x0 + 1.2, 8.0, 3.7), 0.12, "dark", seg=1, m=tur)
        K.box((x0 + 0.2, 7.15, 2.68), (x0 + 1.0, 7.85, 2.66), "red_light", m=tur)
    sx, sy, sz = g["seat"]
    K.cylinder("y", (sx, sz), 1.15, 8.0, 8.8, "paint", n=16, bevel=0.1, m=tur)
    K.rbox((sx - 0.95, 8.8, sz + 1.05), (sx + 0.95, 10.4, sz + 1.3), 0.1, "paint", seg=1, m=tur)
    mx = sx + 1.5
    K.rbox((mx - 0.5, 8.4, sz - 1.4), (mx + 0.5, 9.2, sz - 0.4), 0.1, "dark", seg=1, m=tur)
    V.machine_gun(tur, mx, 9.5, sz - 1.2, length=3.6)
    K.rbox((4.8, 8.0, 5.8), (6.8, 9.2, 7.6), 0.18, "paint", seg=1, m=tur)
    K.box((5.0, 8.3, 5.78), (6.6, 9.0, 5.76), "lens", m=tur)
    K.cylinder("x", (7.6, 13.6), 0.45, 4.0, 12.0, "dark", n=10, bevel=0.1, m=tur)
    for x0 in (3.6, 10.4):
        K.rbox((x0, 5.7, 12.4), (x0 + 2.0, 7.3, 14.6), 0.2, "olive", seg=1, m=tur)
    V.smoke_launchers(tur, 3.0, 7.0, 7.2, 6, side=-1)
    V.smoke_launchers(tur, 13.0, 7.0, 7.2, 6, side=1)
    V.antenna(tur, 6.0, 8.0, 13.0, 6.0)
    # gun: 125 mm 2A46M-5 with its round mantlet
    gy, gz = g["gun"]
    K.cylinder("x", (gy, gz), 1.1, 6.4, 9.6, "paint", n=16, bevel=0.2, m=gun)
    K.cylinder("z", (8.0, gy), 0.82, 2.6, 0.6, "paint", n=12, bevel=0.1, m=gun)
    V.gun_barrel(gun, 8.0, gy, g["muzzle"], 0.6, 0.52, sleeve=(-2.4, -9.6, -12.4), evac=(-7.8, -5.2))
    cx, cy, cz = g["coax"]
    K.cylinder("z", (cx, cy), 0.12, cz, cz - 0.3, "black", n=8, m=gun)
    spots = [(v3(3.2, 6.9, 10.4), (-1, 0, 0.15), (0, 1, 0), 0.65), (v3(12.8, 6.9, 10.4), (1, 0, 0.15), (0, 1, 0), 0.65),
             (v3(0.29, 3.05, 1.2), (-1, 0, 0), (0, 1, 0), 0.6), (v3(15.71, 3.05, 1.2), (1, 0, 0), (0, 1, 0), 0.6)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(_split_marks(spots, turret_from=2))
    return parts, g


# ============================================================================================ Challenger 2
def challenger():
    A2.challenger()
    g = A2.GEO["challenger"]
    body, tur, gun = Mesh(), Mesh(), Mesh()
    side_prism(body, [(-5.0, 1.0), (21.0, 1.0), (21.0, 4.0), (-6.6, 4.0), (-6.3, 1.6)], 3.8, 12.2, fillet=0.35)
    side_prism(body, [(-6.5, 3.8), (21.6, 3.8), (21.6, 6.0), (-3.4, 6.0), (-6.6, 4.5)], 1.0, 15.0, fillet=0.4, bevel=0.3)
    for x in (3.1, 12.9):
        V.light(body, x, 5.75, -4.0, w=1.2, h=0.5)
        V.light(body, x, 5.25, 21.6, w=0.8, h=0.4, face=1, mat="red_light")
    K.rbox((7.0, 5.95, -3.0), (9.6, 6.25, -1.2), 0.15, "paint", seg=1, m=body)
    for x in (6.8, 7.8, 8.8):
        V.periscope(body, x + 0.35, 6.0, -3.25, w=0.7, d=0.4, h=0.28)
    for left in (True, False):
        for i in range(6):
            z0 = -4.4 + i * 4.2
            thin = i >= 3
            if left:
                x0, x1 = (0.6, 1.05) if thin else (0.3, 1.05)
            else:
                x0, x1 = (14.95, 15.4) if thin else (14.95, 15.7)
            skirt(body, x0, x1, 1.4, 4.05, z0 + 0.05, z0 + 4.15)
    for i in range(8):
        z = 13.4 + i * 0.95
        K.box((3.2, 5.95, z), (12.8, 6.12, z + 0.55), "dark", m=body, faces="xXYzZ")
    for x0, x1 in ((1.8, 7.4), (8.6, 14.2)):
        K.cylinder("x", (4.6, 22.6), 0.95, x0, x1, "light_grey", n=14, bevel=0.15, m=body)
        for x in (x0 + 0.5, x1 - 0.5):
            K.cylinder("x", (4.6, 22.6), 0.99, x - 0.1, x + 0.1, "dark", n=14, m=body)
    for tx in g["trackX"]:
        track_loop(body, tx, g, 0, 0)
    # turret: tall slab sides, angled front plates, side bins and the bustle
    top_loft(tur, [(2.6, 6.2), (4.0, 2.6), (12.0, 2.6), (13.4, 6.2), (13.4, 15.0), (12.6, 18.4), (3.4, 18.4), (2.6, 15.0)],
             6.0, 9.0, inset=0.15, fillet=0.3)
    for x0, x1 in ((1.95, 2.65), (13.35, 14.05)):
        K.rbox((x0, 6.4, 8.6), (x1, 8.6, 15.0), 0.15, "paint", seg=1, m=tur)
    K.rbox((3.0, 8.95, 15.6), (13.0, 9.6, 18.0), 0.3, "canvas_green", seg=1, m=tur)
    K.rbox((4.0, 8.9, 5.2), (6.0, 10.2, 7.2), 0.2, "paint", seg=1, m=tur)
    K.box((4.3, 9.4, 5.18), (5.7, 10.0, 5.16), "lens", m=tur)
    sx, sy, sz = g["seat"]
    K.cylinder("y", (sx, sz), 1.2, 8.95, 9.7, "paint", n=16, bevel=0.1, m=tur)
    K.rbox((sx - 1.0, 9.7, sz + 1.1), (sx + 1.0, 11.4, sz + 1.35), 0.1, "paint", seg=1, m=tur)
    K.rbox((9.8, 8.95, 4.2), (12.0, 9.9, 6.0), 0.18, "paint", seg=1, m=tur)
    K.box((10.1, 9.2, 4.18), (11.7, 9.7, 4.16), "lens", m=tur)
    V.hatch(tur, 11.2, 8.95, 10.2, 0.95)
    V.machine_gun(tur, 11.2, 10.1, 8.4, length=2.6)
    V.smoke_launchers(tur, 2.4, 7.4, 4.8, 5, side=-1)
    V.smoke_launchers(tur, 13.6, 7.4, 4.8, 5, side=1)
    V.antenna(tur, 3.8, 8.95, 14.2, 6.5)
    V.antenna(tur, 12.2, 8.95, 14.2, 6.5)
    # gun: L30A1 rifled, the fume extractor half way
    gy, gz = g["gun"]
    K.rbox((6.0, 6.4, 1.8), (10.0, 8.8, 3.6), 0.3, "paint", seg=1, m=gun)
    K.cylinder("z", (8.0, gy), 0.78, 1.8, -0.4, "paint", n=12, bevel=0.1, m=gun)
    V.gun_barrel(gun, 8.0, gy, g["muzzle"], -0.4, 0.5, sleeve=(-2.8, -11.0, -13.6), evac=(-9.0, -6.0))
    cx, cy, cz = g["coax"]
    K.cylinder("z", (cx, cy), 0.12, cz, cz - 0.3, "black", n=8, m=gun)
    spots = [(v3(1.94, 7.5, 11.8), (-1, 0, 0), (0, 1, 0), 0.75), (v3(14.06, 7.5, 11.8), (1, 0, 0), (0, 1, 0), 0.75),
             (v3(0.29, 2.7, 2.0), (-1, 0, 0), (0, 1, 0), 0.75), (v3(15.71, 2.7, 2.0), (1, 0, 0), (0, 1, 0), 0.75)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(_split_marks(spots, turret_from=2))
    return parts, g


TANKS = {"tank": abrams, "leopard": leopard, "t90": t90, "challenger": challenger}
