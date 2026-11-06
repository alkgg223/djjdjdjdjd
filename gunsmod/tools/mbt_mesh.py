"""3.0 tanks and tracked fighting vehicles, built from one description each (model units 0.25 block, front -Z).

Main battle tanks: M1A2 SEP v3, Leopard 2A7, T-14 Armata, T-80BVM, Merkava Mk 4, K2 Black Panther, Type 99A.
Infantry fighting vehicles (they carry soldiers on the hull): M2A3 Bradley, BMP-3, CV90, BMD-4M.

All of them run on the standard running gear (idler, sprocket and track runs of the Abrams), so the track animation of
TankRenderer works unchanged; the hull, its armour kit, the turret, the gun and the fittings come from the spec.
GEO holds the points the game uses (gen_geo.py writes them into VehicleGeo.java)."""
import math

import armor as AR
import meshkit as K
import vehmesh as V
from meshkit import Mesh, v3
from tanks_mesh import side_prism, top_loft, skirt, track_loop, _split_marks

GEO = {}
_RUN = dict(idler=AR.IDLER, sprocket=AR.SPROCKET, bottom=AR.TRACK_BOTTOM, top=AR.TRACK_TOP)


def era_bricks(m, x0, x1, y0, y1, z, cols, rows, depth=0.35, face=-1, mat="paint_mid"):
    """a field of explosive reactive armour bricks on a vertical face (facing -z or +z)"""
    w, h = (x1 - x0) / cols, (y1 - y0) / rows
    for i in range(cols):
        for j in range(rows):
            bx, by = x0 + i * w, y0 + j * h
            za, zb = (z - depth, z) if face < 0 else (z, z + depth)
            K.box((bx + 0.05, by + 0.05, za), (bx + w - 0.05, by + h - 0.05, zb), mat, m=m)


def era_glacis(m, z0, y0, z1, y1, x0, x1, cols, rows, mat="paint_mid"):
    """bricks along a sloped glacis from (z0, y0) up to (z1, y1)"""
    for j in range(rows):
        t0, t1 = j / rows, (j + 1) / rows
        za, ya = z0 + (z1 - z0) * t0, y0 + (y1 - y0) * t0
        zb, yb = z0 + (z1 - z0) * t1, y0 + (y1 - y0) * t1
        w = (x1 - x0) / cols
        for i in range(cols):
            bx = x0 + i * w
            pts = [v3(bx + 0.06, ya + 0.04, za + 0.02), v3(bx + w - 0.06, ya + 0.04, za + 0.02), v3(bx + w - 0.06, yb + 0.04, zb + 0.02),
                   v3(bx + 0.06, yb + 0.04, zb + 0.02)]
            K.plate(pts, 0.3, mat, m=m)


def slat_skirt(m, x0, x1, y0, y1, z0, z1, mat="gunmetal"):
    """bar armour panel"""
    K.box((x0, y1 - 0.15, z0), (x1, y1, z1), mat, m=m)
    K.box((x0, y0, z0), (x1, y0 + 0.15, z1), mat, m=m)
    n = max(2, int((z1 - z0) / 0.55))
    for i in range(n + 1):
        z = z0 + (z1 - z0) * i / n
        K.box((x0, y0, z - 0.05), (x1, y1, z + 0.05), mat, m=m)


def build(s):
    g = dict(trackX=(2.5, 13.5), wheelsZ=s.get("wheelsZ", [-1.4 + i * (18.9 / (s.get("wheels", 7) - 1)) for i in range(s.get("wheels", 7))]),
             wheel=s.get("wheel", (1.75, 1.3)), **_RUN, turret=s["turret"], gun=s["gun"], muzzle=s["muzzle"], seat=s["seat"], coax=s["coax"],
             front=s.get("front", [(1.6, 5.4, -6.4), (14.4, 5.4, -6.4), (8.0, 5.4, -6.8)]), back=s.get("back", [(1.6, 5.4, 22.3), (14.4, 5.4, 22.3)]),
             exhaust=s.get("exhaust", [(1.5, 5.2, 19.5), (14.5, 5.2, 19.5)]), smoke=s.get("smoke", (8.0, 6.0, 17.0)), size=s.get("size", (4.2, 2.4)))
    GEO[s["id"]] = g
    body, tur, gun = Mesh(), Mesh(), Mesh()
    roof = s.get("roof", 5.6)
    nose, gl, rear = s.get("nose_z", -6.5), s.get("glacis_z", -3.6), s.get("rear_z", 21.9)
    noseY = s.get("nose_y", 4.4)
    # lower hull between the tracks and the upper hull over them
    side_prism(body, [(nose + 1.5, 0.9), (rear - 0.5, 0.9), (rear - 0.5, 3.9), (nose, 3.9), (nose + 0.2, 1.9)], 3.8, 12.2, fillet=0.35)
    prof = s.get("hull_profile") or [(nose, 3.7), (rear, 3.7), (rear, roof), (gl, roof), (nose - 0.2, noseY)]
    side_prism(body, prof, 1.0, 15.0, fillet=0.4, bevel=0.3)
    # engine deck grilles (at the back, or at the front on the Merkava)
    deck0, deck1 = s.get("deck", (13.6, 20.8))
    for i in range(int((deck1 - deck0) / 0.95)):
        z = deck0 + i * 0.95
        K.box((3.5, roof - 0.05, z), (12.5, roof + 0.12, z + 0.55), "dark", m=body, faces="xXYzZ")
    # driver's hatch with periscopes, lights, tow eyes
    dz = s.get("driver_z", gl + 0.6)
    K.rbox((7.0, roof - 0.05, dz), (9.6, roof + 0.25, dz + 1.9), 0.15, "paint", seg=1, m=body)
    for x in (6.7, 7.7, 8.7):
        V.periscope(body, x + 0.35, roof, dz - 0.35, w=0.75, d=0.45, h=0.3)
    for x in (3.4, 12.6):
        V.light(body, x, 4.85, nose - 0.05, w=0.9, h=0.45)
        V.light(body, x, 5.0, rear + 0.1, w=0.7, h=0.35, face=1, mat="red_light")
        K.cylinder("z", (x, 2.6), 0.28, nose + 0.25, nose - 0.25, "steel", n=10, bevel=0.06, m=body)
    if s.get("rear_door"):
        # infantry door / ramp in the back
        K.rbox((5.4, 1.4, rear - 0.05), (10.6, roof - 0.4, rear + 0.35), 0.2, "paint", seg=1, m=body)
        K.box((7.4, 3.2, rear + 0.34), (8.6, 4.6, rear + 0.4), "lens", m=body, faces="Z")
        for x in (5.8, 10.2):
            K.cylinder("x", (2.0, rear + 0.3), 0.18, x - 0.4, x + 0.4, "steel", n=8, m=body)
    if s.get("firing_ports"):
        for left in (True, False):
            x = 0.98 if left else 15.02
            for z in s["firing_ports"]:
                K.cylinder("x", (roof - 1.0, z), 0.22, x - 0.1, x + 0.1, "gunmetal", n=8, m=body)
    # side protection: skirts, ERA, slats
    kit = s.get("skirt", "plain")
    for left in (True, False):
        for i in range(7):
            z0 = nose + 2.1 + i * 3.6
            if z0 + 3.55 > rear + 0.5:
                break
            heavy = i < 2
            x0, x1 = (0.25 if heavy else 0.5, 1.05) if left else (14.95, 15.75 if heavy else 15.5)
            if kit == "slat" and i >= 4:
                slat_skirt(body, x0, x1, 1.6, 3.95, z0 + 0.05, z0 + 3.55)
                continue
            skirt(body, x0, x1, 1.25 + (i % 2) * 0.15, 3.95, z0 + 0.05, z0 + 3.55)
        if kit == "era":
            for i in range(5):
                z0 = nose + 2.3 + i * 3.6
                for j in range(2):
                    xa, xb = (0.0, 0.3) if left else (15.7, 16.0)
                    for k in range(3):
                        za = z0 + k * 1.1
                        K.box((xa, 2.0 + j * 0.95, za), (xb, 2.85 + j * 0.95, za + 1.0), "paint_mid", m=body)
    if s.get("era_glacis"):
        era_glacis(body, nose - 0.1, noseY + 0.05, gl - 0.2, roof - 0.05, 3.2, 12.8, 6, 2)
    for tx in g["trackX"]:
        track_loop(body, tx, g, 0, 0)
    # ---------------------------------------------------------------------------- turret
    tx, tz = s["turret"]
    ty0, ty1 = roof + 0.35, s.get("turret_top", roof + 3.2)
    outline = s["outline"]
    top_loft(tur, outline, ty0, ty1, inset=s.get("inset", 0.2), fillet=s.get("fillet", 0.45))
    style = s.get("style", "slab")
    if style == "wedge":
        # arrowhead add-on armour in front of the cheeks
        fz = min(z for _, z in outline)
        for left in (True, False):
            w = K.prism(K.fillet([(3.0, fz + 2.6), (6.45, fz), (6.55, fz - 2.3)], 0.18, seg=1), "y", ty0 + 0.05, ty1 - 0.15, "paint", bevel=0.14)
            tur.extend(w if left else w.mirrored_x())
    if s.get("era_turret"):
        fz = min(z for _, z in outline)
        for x0 in (3.0, 9.6):
            era_bricks(tur, x0, x0 + 3.4, ty0 + 0.3, ty1 - 0.3, fz + 0.2, 3, 2)
    if style == "t14":
        # the unmanned turret: sensor blocks at the corners, radar panels on the sides, the remote MG on top
        for x0, z0 in ((3.0, tz - 3.4), (11.4, tz - 3.4)):
            K.rbox((x0, ty1 - 0.1, z0), (x0 + 1.6, ty1 + 1.0, z0 + 1.6), 0.15, "paint_dark", seg=1, m=tur)
            K.box((x0 + 0.2, ty1 + 0.2, z0 - 0.02), (x0 + 1.4, ty1 + 0.8, z0), "lens", m=tur, faces="z")
        for x in (2.7, 13.3):
            K.box((x - 0.15, ty0 + 0.4, tz - 1.5), (x + 0.15, ty1 - 0.3, tz + 1.5), "array", m=tur)
    # commander's sight, gunner's sight, hatches, machine gun / RWS, smoke dischargers, antennas, stowage
    sx, sy, sz = s["seat"]
    if style != "t14":
        V.hatch(tur, sx, ty1, sz, 1.0)
        K.cylinder("y", (sx, sz), 1.1, ty1 - 0.05, ty1 + 0.4, "paint", n=16, bevel=0.06, m=tur)
    gsx = s.get("sight_x", 11.0)
    fz = min(z for _, z in outline)
    K.rbox((gsx - 1.2, ty1 - 0.1, fz + 1.5), (gsx + 1.2, ty1 + 1.0, fz + 3.6), 0.22, "paint", seg=1, m=tur)
    K.box((gsx - 0.9, ty1 + 0.2, fz + 1.48), (gsx + 0.9, ty1 + 0.85, fz + 1.46), "lens", m=tur)
    if s.get("cits"):
        cx_, cz_ = s["cits"]
        K.cylinder("y", (cx_, cz_), 0.55, ty1, ty1 + 0.7, "paint", n=14, m=tur)
        K.rbox((cx_ - 0.7, ty1 + 0.7, cz_ - 0.7), (cx_ + 0.7, ty1 + 1.6, cz_ + 0.7), 0.2, "paint_mid", seg=1, m=tur)
        K.box((cx_ - 0.5, ty1 + 0.9, cz_ - 0.72), (cx_ + 0.5, ty1 + 1.4, cz_ - 0.7), "lens", m=tur, faces="z")
    if s.get("rws"):
        rx, rz = s["rws"]
        K.rbox((rx - 0.8, ty1, rz - 0.8), (rx + 0.8, ty1 + 0.9, rz + 0.8), 0.15, "paint_dark", seg=1, m=tur)
        V.machine_gun(tur, rx, ty1 + 1.25, rz - 0.4, length=2.6)
        K.box((rx + 0.5, ty1 + 0.95, rz - 0.6), (rx + 0.9, ty1 + 1.4, rz - 0.2), "lens", m=tur)
    else:
        V.machine_gun(tur, s.get("mg_x", 10.7), ty1 + 1.1, s.get("mg_z", tz + 1.0))
    sl_z = s.get("smoke_z", fz + 7.0)
    V.smoke_launchers(tur, outline_x_min(outline) - 0.3, ty0 + 1.8, sl_z, 4, side=-1)
    V.smoke_launchers(tur, outline_x_max(outline) + 0.3, ty0 + 1.8, sl_z, 4, side=1)
    bz = max(z for _, z in outline)
    V.antenna(tur, 4.2, ty1, bz - 3.0, 7.0)
    V.antenna(tur, 11.8, ty1, bz - 3.0, 6.0)
    if s.get("aps") == "trophy":
        for x in (outline_x_min(outline) - 0.9, outline_x_max(outline) + 0.1):
            K.rbox((x, ty0 + 0.6, tz - 1.0), (x + 0.8, ty0 + 2.0, tz + 1.4), 0.15, "paint_dark", seg=1, m=tur)
            K.box((x + (0 if x < 8 else 0.8) - 0.02, ty0 + 0.9, tz - 0.8), (x + (0 if x < 8 else 0.8) + 0.02, ty0 + 1.7, tz + 1.2), "array", m=tur)
    elif s.get("aps") == "afganit":
        for left in (True, False):
            x = 0.7 if left else 15.3
            for k in range(5):
                K.cylinder("x", (roof - 0.2, 2.0 + k * 0.8), 0.28, x - 0.4, x + 0.4, "paint_dark", n=8, m=body)
    if s.get("bustle_rack", True):
        K.rbox((3.4, ty1 - 0.05, bz - 2.6), (7.3, ty1 + 0.65, bz - 0.4), 0.3, "canvas_green", seg=1, m=tur)
        K.rbox((8.7, ty1 - 0.05, bz - 2.4), (12.6, ty1 + 0.5, bz - 0.4), 0.25, "canvas", seg=1, m=tur)
    if s.get("atgm"):
        ax, ay, az, n = s["atgm"]
        K.rbox((ax - 0.9, ay - 0.8, az - 1.2), (ax + 0.9, ay + 0.8, az + 2.2), 0.2, "paint", seg=1, m=tur)
        for k in range(n):
            K.cylinder("z", (ax - 0.4 + k * 0.8, ay), 0.32, az - 1.22, az - 1.18, "dark", n=10, m=tur)
    # ---------------------------------------------------------------------------- gun
    gy, gz = s["gun"]
    mw = s.get("mantlet", (2.9, 2.45, 2.6))
    K.rbox((8.0 - mw[0] / 2, gy - mw[1] / 2, gz - mw[2]), (8.0 + mw[0] / 2, gy + mw[1] / 2, gz + 0.2), 0.35, "paint", seg=2, m=gun)
    V.gun_barrel(gun, 8.0, gy, s["muzzle"], gz - mw[2], s.get("bore", 0.48), sleeve=s.get("sleeve"), evac=s.get("evac"), brake=s.get("brake"),
                 mrs=s.get("mrs", False))
    if s.get("second_gun"):
        # the BMP-3 carries a 30 mm cannon beside its 100 mm gun
        x2, y2, z2 = s["second_gun"]
        K.cylinder("z", (x2, y2), 0.2, gz - mw[2], z2, "gunmetal", n=10, m=gun)
        K.cylinder("z", (x2, y2), 0.27, z2 + 0.6, z2, "gunmetal", n=10, m=gun)
    cx, cy, cz = s["coax"]
    K.cylinder("z", (cx, cy), 0.12, cz, cz - 0.3, "black", n=8, m=gun)
    spots = [(v3(outline_x_min(outline) - 0.02, ty0 + 1.5, tz + 2.0), (-1, 0, 0), (0, 1, 0), 0.7),
             (v3(outline_x_max(outline) + 0.02, ty0 + 1.5, tz + 2.0), (1, 0, 0), (0, 1, 0), 0.7),
             (v3(0.24, 2.6, 9.0), (-1, 0, 0), (0, 1, 0), 0.65), (v3(15.76, 2.6, 9.0), (1, 0, 0), (0, 1, 0), 0.65),
             (v3(8.0, 4.0, rear + 0.62), (0, 0, 1), (0, 1, 0), 0.5)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(_split_marks(spots, turret_from=2))
    return parts, g


def outline_x_min(o):
    return min(x for x, _ in o)


def outline_x_max(o):
    return max(x for x, _ in o)


def _wedge_outline(front, back, w0=3.0, w1=13.0, nose=6.0):
    return [(w0, front + 1.9), (nose, front), (16 - nose, front), (w1, front + 1.9), (w1, back - 1.4), (w1 - 1.0, back), (w0 + 1.0, back), (w0, back - 1.4)]


SPECS = {
    # ---------------------------------------------------------------- main battle tanks
    "m1a2sep": dict(id="m1a2sep", turret=(8.0, 9.0), gun=(7.6, 2.6), muzzle=-14.85, seat=(10.6, 6.6, 11.0), coax=(9.7, 7.2, 0.6),
                    outline=[(2.6, 2.4), (13.4, 2.4), (13.6, 4.6), (13.6, 17.6), (12.6, 19.4), (3.4, 19.4), (2.4, 17.6), (2.4, 4.6)],
                    turret_top=8.4, style="slab", cits=(5.0, 12.0), rws=(10.6, 9.6), aps="trophy", sleeve=(-3.0, -9.8, -12.6), evac=(-7.6, -5.6),
                    mrs=True, skirt="slat", sight_x=11.2, size=(4.2, 2.4)),
    "leopard2a7": dict(id="leopard2a7", turret=(8.0, 9.6), gun=(7.4, 3.2), muzzle=-15.85, seat=(5.0, 6.4, 12.4), coax=(9.8, 7.1, 1.6),
                       outline=[(3.0, 5.2), (6.0, 3.3), (10.0, 3.3), (13.0, 5.2), (13.0, 19.6), (12.0, 21.0), (4.0, 21.0), (3.0, 19.6)], turret_top=8.8,
                       style="wedge", rws=(10.6, 11.0), sleeve=(-3.4, -10.8, -13.6), evac=(-8.8, -6.4), mrs=True, cits=(5.0, 14.0),
                       hull_profile=[(-6.5, 3.7), (21.9, 3.7), (21.9, 5.6), (-3.6, 5.6), (-7.0, 4.2)], era_glacis=False, size=(4.2, 2.4)),
    "t14": dict(id="t14", turret=(8.0, 10.0), gun=(6.6, 4.4), muzzle=-16.6, seat=(8.0, 5.6, -2.0), coax=(9.4, 6.4, 3.4),
                outline=[(3.0, 5.4), (5.0, 3.6), (11.0, 3.6), (13.0, 5.4), (13.0, 15.4), (11.6, 16.4), (4.4, 16.4), (3.0, 15.4)], turret_top=7.4,
                style="t14", rws=(8.0, 12.6), aps="afganit", wheels=7, roof=5.4, glacis_z=-2.4, driver_z=-2.6, mantlet=(2.2, 1.6, 2.0), bore=0.5,
                evac=(-8.4, -6.6), skirt="plain", bustle_rack=False, size=(4.4, 2.4)),
    "t80bvm": dict(id="t80bvm", turret=(8.0, 8.4), gun=(6.9, 3.6), muzzle=-14.9, seat=(10.4, 5.8, 10.4), coax=(9.6, 6.5, 2.4),
                   outline=K.fillet([(3.2, 3.6), (12.8, 3.6), (13.2, 12.6), (2.8, 12.6)], 3.0, seg=4), turret_top=7.9, fillet=0.0, inset=0.5,
                   style="round", era_turret=True, era_glacis=True, skirt="era", wheels=6, wheel=(1.95, 1.55), evac=(-8.0, -6.2),
                   exhaust=[(8.0, 5.0, 22.2)], size=(4.0, 2.2)),
    "merkava4": dict(id="merkava4", turret=(8.0, 13.4), gun=(7.6, 7.6), muzzle=-11.8, seat=(10.4, 7.4, 15.6), coax=(9.8, 8.0, 5.4),
                     outline=[(3.2, 9.6), (6.0, 6.4), (10.0, 6.4), (12.8, 9.6), (13.4, 19.6), (12.0, 21.4), (4.0, 21.4), (2.6, 19.6)], turret_top=8.6,
                     roof=6.2, glacis_z=5.0, nose_y=4.0, deck=(-3.0, 3.6), driver_z=4.4, rear_door=True, aps="trophy", rws=(5.4, 17.0),
                     sleeve=(0.6, -6.4), mrs=True, skirt="plain", bustle_rack=True, exhaust=[(1.5, 5.2, 0.5)], size=(4.4, 2.6)),
    "k2": dict(id="k2", turret=(8.0, 9.4), gun=(7.4, 3.0), muzzle=-15.6, seat=(5.0, 6.6, 12.0), coax=(9.8, 7.1, 1.4),
               outline=_wedge_outline(3.0, 21.6, nose=5.6), turret_top=8.6, style="slab", cits=(5.0, 13.6), rws=(10.6, 12.0),
               sleeve=(-3.4, -10.6), evac=(-8.6, -6.4), mrs=True, skirt="plain", size=(4.2, 2.4)),
    "type99a": dict(id="type99a", turret=(8.0, 9.2), gun=(6.9, 3.4), muzzle=-15.2, seat=(10.2, 6.0, 11.4), coax=(9.6, 6.6, 2.0),
                    outline=_wedge_outline(3.4, 18.6, nose=6.4), turret_top=8.2, style="wedge", era_turret=True, era_glacis=True, skirt="era",
                    wheels=6, wheel=(1.95, 1.55), evac=(-8.6, -6.4), cits=(5.6, 12.4), size=(4.2, 2.4)),
    # ---------------------------------------------------------------- infantry fighting vehicles
    "bradley": dict(id="bradley", turret=(8.0, 4.6), gun=(9.4, 1.2), muzzle=-7.6, seat=(5.6, 9.4, 6.0), coax=(9.6, 9.0, 0.6),
                    outline=[(3.6, 2.0), (10.6, 2.0), (11.4, 3.2), (11.4, 7.6), (10.6, 8.6), (4.4, 8.6), (3.6, 7.6)], turret_top=9.6, roof=7.2,
                    glacis_z=-1.0, nose_y=4.0, driver_z=-0.6, deck=(-1.8, 0.0), rear_door=True, firing_ports=[8.0, 12.0, 16.0],
                    mantlet=(1.4, 1.2, 1.4), bore=0.18, atgm=(2.6, 8.6, 5.2, 2), skirt="plain", bustle_rack=False, mg_x=5.6, mg_z=7.0,
                    smoke_z=3.2, exhaust=[(14.5, 6.0, 0.6)], size=(3.6, 3.0), riders=[(4.2, 7.4, 13.6), (11.8, 7.4, 13.6), (4.2, 7.4, 17.4), (11.8, 7.4, 17.4)]),
    "bmp3": dict(id="bmp3", turret=(8.0, 5.0), gun=(8.6, 1.6), muzzle=-10.6, seat=(5.8, 8.2, 6.6), coax=(9.8, 8.4, 0.8),
                 outline=K.fillet([(4.0, 2.4), (12.0, 2.4), (12.4, 7.8), (3.6, 7.8)], 1.6, seg=3), turret_top=8.4, fillet=0.0, inset=0.5, roof=6.4,
                 glacis_z=-1.6, nose_y=3.6, driver_z=-1.2, deck=(14.0, 20.8), rear_door=True, firing_ports=[9.0, 13.0], mantlet=(2.0, 1.6, 1.6),
                 bore=0.42, second_gun=(9.5, 8.6, -6.6), skirt="plain", bustle_rack=False, smoke_z=4.2, size=(3.4, 2.6),
                 riders=[(4.2, 6.6, 12.0), (11.8, 6.6, 12.0), (4.2, 6.6, 16.6), (11.8, 6.6, 16.6)]),
    "cv90": dict(id="cv90", turret=(8.0, 6.6), gun=(8.0, 2.2), muzzle=-9.4, seat=(5.4, 9.2, 8.0), coax=(9.4, 8.8, 1.6),
                 outline=[(3.6, 3.6), (6.0, 2.2), (10.0, 2.2), (12.4, 3.6), (12.4, 10.0), (11.6, 11.0), (4.4, 11.0), (3.6, 10.0)], turret_top=9.4,
                 roof=7.0, glacis_z=-2.0, nose_y=4.2, driver_z=-1.6, deck=(-1.2, 0.6), rear_door=True, mantlet=(1.8, 1.4, 1.8), bore=0.26,
                 brake=0.4, skirt="plain", bustle_rack=False, rws=(10.4, 8.4), smoke_z=5.0, exhaust=[(14.5, 6.0, 1.0)], size=(3.6, 2.8),
                 riders=[(4.2, 7.2, 14.4), (11.8, 7.2, 14.4), (4.2, 7.2, 18.2), (11.8, 7.2, 18.2)]),
    "bmd4": dict(id="bmd4", turret=(8.0, 6.0), gun=(8.6, 2.4), muzzle=-9.8, seat=(5.8, 7.4, 7.4), coax=(9.8, 7.8, 1.6),
                 outline=K.fillet([(4.2, 3.4), (11.8, 3.4), (12.0, 8.6), (4.0, 8.6)], 1.4, seg=3), turret_top=7.6, fillet=0.0, inset=0.45, roof=5.2,
                 glacis_z=-3.0, nose_y=3.4, driver_z=-2.8, deck=(14.6, 20.6), mantlet=(1.8, 1.4, 1.4), bore=0.4, second_gun=(9.5, 7.8, -6.0),
                 skirt="plain", bustle_rack=False, wheels=5, wheelsZ=[-1.0, 3.6, 8.2, 12.8, 17.4], smoke_z=4.6, size=(3.2, 2.2),
                 riders=[(4.2, 5.6, 12.6), (11.8, 5.6, 12.6), (8.0, 5.6, 17.4)]),
}

TANKS3 = {k: (lambda k=k: build(SPECS[k])) for k in SPECS}

if __name__ == "__main__":
    import os
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    for k, fn in TANKS3.items():
        parts, g = fn()
        for n, mesh in parts.items():
            K.write_part(mesh, "entity/" + k, n, RES)
        print(k, sum(p.quad_count() for p in parts.values()))
