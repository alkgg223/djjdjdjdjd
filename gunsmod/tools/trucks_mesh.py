"""3.0 wheeled vehicles (model units 0.25 block, front -Z, centred on x = 8).

Fighting vehicles (a turret with a cannon, soldiers ride on the roof): M1128-style Stryker with the 30 mm turret,
Boxer with the RCT30 turret. Transports (no weapons; soldiers ride inside and jump out on contact): MaxxPro MRAP,
JLTV, and the 6x6 truck family - cargo (canvas tilt), fuel tanker, ammunition carrier (crates and a crane), command post
(shelter box, antenna masts, generator) and recovery truck (crane boom and the spade).
GEO holds the points the game uses; gen_geo.py writes them into VehicleGeo.java."""
import math

import meshkit as K
import vehmesh as V
from cars_mesh import arch, louvres, marks
from meshkit import Mesh, v3

GEO = {}


def glass(m, x0, x1, y0, y1, z, face=-1):
    K.box((x0, y0, z - 0.04), (x1, y1, z + 0.04), "glass_dark", m=m, faces="zZ")
    for x in (x0, x1):
        K.box((x - 0.12, y0, z - 0.1), (x + 0.12, y1, z + 0.1), "paint_dark", m=m)


def side_glass(m, x, y0, y1, z0, z1):
    K.box((x - 0.04, y0, z0), (x + 0.04, y1, z1), "glass_dark", m=m, faces="xX")


def wheels_at(zs, y, xl=2.4, xr=13.6):
    out = []
    for z in zs:
        out += [[xl, y, z], [xr, y, z]]
    return out


def arches(m, zs, r, x0=1.6, x1=14.4, cy=None):
    for z in zs:
        for a, b in ((x0 - 0.2, x0 + 0.9), (x1 - 0.9, x1 + 0.2)):
            arch(m, a, b, cy if cy is not None else r, z, r + 0.15, r + 0.55)


def cab(m, z0, z1, y0, y1, x0=3.0, x1=13.0, rake=1.2, mat="paint"):
    """a truck / MRAP cab: the box with a raked windscreen, side windows, mirrors, the roof hatch"""
    pts = [(z1, y0), (z1, y1), (z0 + rake, y1), (z0, y1 - 2.2), (z0, y0)]
    K.prism(K.fillet(pts, 0.3, seg=1), "x", x0, x1, mat, bevel=0.2, m=m)
    # windscreen (two panes on the rake), side windows
    for xa, xb in ((x0 + 0.6, 7.8), (8.2, x1 - 0.6)):
        pts = [v3(xa, y1 - 2.0, z0 + 0.15), v3(xb, y1 - 2.0, z0 + 0.15), v3(xb, y1 - 0.25, z0 + rake - 0.1), v3(xa, y1 - 0.25, z0 + rake - 0.1)]
        K.plate(pts, 0.08, "glass_dark", m=m)
    for x in (x0 - 0.02, x1 + 0.02):
        side_glass(m, x, y1 - 2.0, y1 - 0.4, z0 + rake + 0.4, z0 + rake + 2.6)
    for x in (x0 - 0.9, x1 + 0.4):
        K.box((x, y1 - 1.6, z0 + 0.4), (x + 0.5, y1 - 0.7, z0 + 0.6), "dark", m=m)
    V.hatch(m, 8.0, y1, (z0 + z1) / 2 + 0.6, 0.8)


def base_frame(m, z0, z1, y0=2.3, y1=3.2):
    K.box((3.6, y0, z0), (12.4, y1, z1), "dark", m=m)
    K.box((2.6, y1, z0), (13.4, y1 + 0.3, z1), "paint_dark", m=m)


def lights(m, zf, zb, y=3.8):
    for x in (3.2, 12.8):
        V.light(m, x, y, zf - 0.05, w=0.9, h=0.45)
        V.light(m, x, y, zb + 0.1, w=0.6, h=0.35, face=1, mat="red_light")


def turret_cannon(tur, gun, tx, tz, ty0, ty1, gy, gz, muzzle, w=3.0, l=4.0, rws=False):
    K.rbox((tx - w, ty0, tz - l * 0.5), (tx + w, ty1, tz + l * 0.6), 0.35, "paint", seg=1, m=tur)
    K.box((tx - w * 0.6, ty1 - 0.1, tz - l * 0.5 - 0.05), (tx + w * 0.6, ty1 - 0.6, tz - l * 0.5), "paint_dark", m=tur)
    K.rbox((tx + w - 1.6, ty1, tz - 1.0), (tx + w - 0.4, ty1 + 0.9, tz + 0.4), 0.15, "paint_dark", seg=1, m=tur)
    K.box((tx + w - 1.4, ty1 + 0.2, tz - 1.02), (tx + w - 0.6, ty1 + 0.7, tz - 1.0), "lens", m=tur, faces="z")
    V.smoke_launchers(tur, tx - w - 0.2, ty0 + 1.0, tz + 0.6, 3, side=-1)
    V.smoke_launchers(tur, tx + w + 0.2, ty0 + 1.0, tz + 0.6, 3, side=1)
    V.antenna(tur, tx - w + 0.6, ty1, tz + l * 0.5, 6.0)
    if rws:
        V.machine_gun(tur, tx - 1.2, ty1 + 0.9, tz + 0.4)
    K.rbox((tx - 0.7, gy - 0.6, gz - 1.0), (tx + 0.7, gy + 0.6, gz + 0.4), 0.2, "paint", seg=1, m=gun)
    K.cylinder("z", (tx, gy), 0.26, gz - 1.0, muzzle + 1.2, "gunmetal", n=10, m=gun)
    K.cylinder("z", (tx, gy), 0.36, muzzle + 1.2, muzzle, "gunmetal", n=10, m=gun)
    K.cylinder("z", (tx, gy), 0.18, muzzle - 0.02, muzzle, "black", n=8, m=gun)


def ifv8(name, length=(-12.0, 21.0), roof=8.4, turret=(8.0, 4.0), slope=3.4, boxer=False):
    zf, zb = length
    zs = [-6.0, -1.0, 9.0, 14.0] if not boxer else [-6.4, -1.4, 8.6, 13.6]
    wr = 2.2
    g = dict(turret=turret, gun=(roof + 2.0, turret[1] - 2.0), muzzles=[[8.0, roof + 2.0, zf - 4.6]], tubes=[], coax=[9.0, roof + 1.8, turret[1] - 2.0],
             radar=None, wheels=wheels_at(zs, wr), wheelR=wr, steerAxles=2,
             seats=[[6.4, roof + 0.4, turret[1] + 1.0], [4.6, roof, 11.0], [11.4, roof, 11.0], [4.6, roof, 16.0], [11.4, roof, 16.0]],
             front=[[3.2, 3.8, zf - 0.1], [12.8, 3.8, zf - 0.1], [8.0, 3.8, zf - 0.3]], back=[[3.2, 3.8, zb + 0.1], [12.8, 3.8, zb + 0.1]],
             exhaust=[[13.6, 6.0, zf + 6.0]], smoke=[8.0, roof, zf + 6.0], size=[3.0, 3.0], gunMin=-8.0, gunMax=55.0)
    GEO[name] = g
    body, tur, gun = Mesh(), Mesh(), Mesh()
    # the hull: a long box with the sloped nose, the sides flaring over the wheels, the flat roof
    prof = [(zf + 1.2, 1.6), (zb, 1.6), (zb, roof), (zf + slope, roof), (zf, roof - 2.6), (zf, 3.4)]
    K.prism(K.fillet(prof, 0.35, seg=1), "x", 1.4, 14.6, "paint", bevel=0.3, m=body)
    K.box((2.0, 0.9, zf + 2.0), (14.0, 1.7, zb - 1.0), "dark", m=body)
    arches(body, zs, wr, 1.4, 14.6)
    # bolted add-on armour panels along the sides, the driver's periscopes, the rear ramp, stowage
    for left in (True, False):
        x0, x1 = (1.0, 1.4) if left else (14.6, 15.0)
        for i in range(5):
            za = zf + 4.0 + i * 5.2
            if za + 4.8 > zb:
                break
            K.rbox((x0, 3.4, za), (x1, roof - 1.0, za + 4.8), 0.12, "paint_mid", seg=1, m=body)
    for x in (3.2, 4.2, 5.2):
        V.periscope(body, x, roof, zf + slope + 0.3)
    V.hatch(body, 4.2, roof, zf + slope + 1.6, 0.9)
    K.rbox((5.0, 2.0, zb - 0.1), (11.0, roof - 0.8, zb + 0.3), 0.15, "paint", seg=1, m=body)
    louvres(body, 10.6, 14.0, roof, zf + 4.4, zf + 8.6, 6)
    lights(body, zf, zb)
    for z in (13.0, 17.0):
        K.rbox((2.4, roof, z), (5.2, roof + 0.7, z + 2.6), 0.25, "canvas_green", seg=1, m=body)
    tx, tz = turret
    turret_cannon(tur, gun, tx, tz, roof, roof + (2.4 if boxer else 2.0), roof + 2.0, tz - 2.0, zf - 4.6, w=3.2 if boxer else 2.6, l=4.6 if boxer else 4.0,
                  rws=True)
    spots = [(v3(1.0, 5.8, 8.0), (-1, 0, 0), (0, 1, 0), 0.8), (v3(15.0, 5.8, 8.0), (1, 0, 0), (0, 1, 0), 0.8), (v3(8.0, 5.0, zb + 0.32), (0, 0, 1), (0, 1, 0), 0.6)]
    parts = {"body": body, "turret": tur, "gun": gun}
    parts.update(marks(spots))
    return parts, g


def _unarmed(name, g):
    g.update(turret=[8.0, 0.0], gun=[0.0, 0.0], muzzles=[], tubes=[], coax=[8.0, 0.0, 0.0], radar=None, gunMin=0.0, gunMax=0.0)
    GEO[name] = g


def mrap(name="mrap"):
    zf, zb = -12.0, 15.0
    wr = 2.4
    zs = [-5.6, 9.0]
    g = dict(wheels=wheels_at(zs, wr), wheelR=wr, steerAxles=1,
             seats=[[6.0, 4.4, -4.4], [10.0, 4.4, -4.4], [6.0, 4.4, 2.0], [10.0, 4.4, 2.0], [6.0, 4.4, 7.0], [10.0, 4.4, 7.0]],
             front=[[3.2, 3.8, zf - 0.1], [12.8, 3.8, zf - 0.1], [8.0, 3.8, zf - 0.3]], back=[[3.2, 3.8, zb + 0.1], [12.8, 3.8, zb + 0.1]],
             exhaust=[[13.4, 9.6, -6.0]], smoke=[8.0, 6.0, -8.0], size=[2.8, 3.2])
    _unarmed(name, g)
    m = Mesh()
    # the V hull (deflects a mine blast), the armoured body up high, the bonnet, the big windows
    K.prism([(1.8, 3.0), (14.2, 3.0), (11.6, 1.2), (4.4, 1.2)], "z", zf + 3.0, zb - 0.6, "paint_dark", m=m)
    K.rbox((1.8, 3.0, zf + 4.4), (14.2, 10.6, zb), 0.45, "paint", seg=1, m=m)
    K.rbox((2.6, 3.0, zf), (13.4, 6.4, zf + 4.6), 0.4, "paint", seg=1, m=m)
    louvres(m, 4.0, 12.0, 6.4, zf + 0.6, zf + 3.6, 6)
    K.box((3.0, 3.4, zf - 0.2), (13.0, 5.6, zf), "grille", m=m, faces="z")
    for xa, xb in ((2.6, 7.8), (8.2, 13.4)):
        pts = [v3(xa, 7.0, zf + 4.38), v3(xb, 7.0, zf + 4.38), v3(xb, 10.0, zf + 5.4), v3(xa, 10.0, zf + 5.4)]
        K.plate(pts, 0.1, "glass_dark", m=m)
    for x in (1.76, 14.24):
        for z in (zf + 6.6, zf + 11.0, zf + 15.4):
            side_glass(m, x, 7.4, 9.4, z, z + 2.6)
    # doors with their hinges, the step, the roof hatch and the gun ring (empty), the rear door
    for x in (1.7, 14.3):
        for z in (zf + 6.0, zf + 10.4):
            K.box((x - 0.05, 4.0, z), (x + 0.05, 10.0, z + 0.12), "dark", m=m)
        K.box((x - 0.5 if x < 8 else x, 2.4, zf + 6.0), (x if x < 8 else x + 0.5, 2.6, zf + 14.0), "tread_plate", m=m)
    K.cylinder("y", (8.0, 1.0), 1.6, 10.6, 11.2, "paint_dark", n=16, m=m)
    K.rbox((5.4, 3.4, zb - 0.1), (10.6, 10.0, zb + 0.4), 0.2, "paint_mid", seg=1, m=m)
    K.box((6.2, 7.4, zb + 0.38), (9.8, 9.2, zb + 0.42), "glass_dark", m=m, faces="Z")
    arches(m, zs, wr, 1.8, 14.2)
    lights(m, zf, zb)
    V.antenna(m, 3.0, 10.6, zb - 2.0, 8.0)
    V.antenna(m, 13.0, 10.6, zb - 2.0, 8.0)
    spots = [(v3(1.74, 5.6, 6.0), (-1, 0, 0), (0, 1, 0), 0.8), (v3(14.26, 5.6, 6.0), (1, 0, 0), (0, 1, 0), 0.8)]
    parts = {"body": m}
    parts.update(marks(spots))
    return parts, g


def jltv(name="jltv"):
    zf, zb = -10.0, 11.0
    wr = 2.0
    zs = [-5.4, 6.8]
    g = dict(wheels=wheels_at(zs, wr, 2.6, 13.4), wheelR=wr, steerAxles=1,
             seats=[[6.0, 3.8, -1.2], [10.0, 3.8, -1.2], [6.0, 3.8, 3.2], [10.0, 3.8, 3.2]],
             front=[[3.4, 3.6, zf - 0.1], [12.6, 3.6, zf - 0.1], [8.0, 3.6, zf - 0.3]], back=[[3.4, 3.6, zb + 0.1], [12.6, 3.6, zb + 0.1]],
             exhaust=[[13.0, 2.4, zb - 0.2]], smoke=[8.0, 5.0, -7.0], size=[2.6, 2.6])
    _unarmed(name, g)
    m = Mesh()
    K.rbox((2.4, 2.2, zf), (13.6, 5.8, zf + 6.0), 0.5, "paint", seg=1, m=m)
    louvres(m, 4.4, 11.6, 5.8, zf + 0.8, zf + 4.6, 7)
    K.box((3.6, 3.0, zf - 0.2), (12.4, 5.0, zf), "grille", m=m, faces="z")
    K.rbox((2.4, 2.2, zf + 5.8), (13.6, 8.6, zf + 15.6), 0.5, "paint", seg=1, m=m)
    K.rbox((2.6, 2.2, zf + 15.4), (13.4, 5.2, zb), 0.4, "paint", seg=1, m=m)
    for xa, xb in ((2.8, 7.8), (8.2, 13.2)):
        pts = [v3(xa, 6.0, zf + 5.78), v3(xb, 6.0, zf + 5.78), v3(xb, 8.2, zf + 6.8), v3(xa, 8.2, zf + 6.8)]
        K.plate(pts, 0.1, "glass_dark", m=m)
    for x in (2.36, 13.64):
        for z in (zf + 7.4, zf + 11.4):
            side_glass(m, x, 6.2, 8.0, z, z + 3.0)
    K.cylinder("y", (8.0, zf + 11.0), 1.4, 8.6, 9.1, "paint_dark", n=14, m=m)
    arches(m, zs, wr, 2.4, 13.6)
    lights(m, zf, zb, 3.6)
    K.rbox((4.0, 5.2, zf + 16.0), (12.0, 6.0, zb - 0.6), 0.3, "canvas_green", seg=1, m=m)
    spots = [(v3(2.34, 4.4, 2.0), (-1, 0, 0), (0, 1, 0), 0.7), (v3(13.66, 4.4, 2.0), (1, 0, 0), (0, 1, 0), 0.7)]
    parts = {"body": m}
    parts.update(marks(spots))
    return parts, g


def truck(name, kind):
    """the 6x6 truck: cab forward, the chassis, and its load"""
    zf, zb = -14.0, 18.0
    wr = 2.3
    zs = [-8.6, 9.4, 13.8]
    seats = [[6.0, 5.4, -10.2], [10.0, 5.4, -10.2]]
    if kind == "cargo":
        seats += [[5.0, 5.6, 0.0], [11.0, 5.6, 0.0], [5.0, 5.6, 5.0], [11.0, 5.6, 5.0], [5.0, 5.6, 10.0], [11.0, 5.6, 10.0]]
    g = dict(wheels=wheels_at(zs, wr), wheelR=wr, steerAxles=1, seats=seats,
             front=[[3.4, 4.0, zf - 0.1], [12.6, 4.0, zf - 0.1], [8.0, 4.0, zf - 0.3]], back=[[3.4, 3.4, zb + 0.1], [12.6, 3.4, zb + 0.1]],
             exhaust=[[13.2, 11.4, -8.4]], smoke=[8.0, 6.0, -10.0], size=[2.8, 3.4])
    _unarmed(name, g)
    m = Mesh()
    base_frame(m, zf + 1.0, zb)
    cab(m, zf, zf + 7.0, 3.2, 10.4)
    K.box((3.4, 3.4, zf - 0.25), (12.6, 6.2, zf), "grille", m=m, faces="z")
    K.box((2.6, 2.6, zf - 0.6), (13.4, 3.4, zf + 0.4), "dark", m=m)
    K.cylinder("y", (13.2, -8.4), 0.3, 6.0, 11.4, "burnt_metal", n=8, m=m)
    for x in (2.4, 13.0):
        K.box((x, 3.6, -6.6), (x + 0.6, 5.0, -4.0), "olive_dark", m=m)
    arches(m, zs[:1], wr, 2.0, 14.0)
    for z in zs[1:]:
        for x0, x1 in ((1.8, 2.8), (13.2, 14.2)):
            K.box((x0, wr + 1.8, z - 2.4), (x1, wr + 2.0, z + 2.4), "paint_dark", m=m)
    lights(m, zf, zb, 3.6)
    y0 = 3.5
    if kind == "cargo":
        # the flat bed with drop sides and the canvas tilt over bows
        K.box((2.4, y0, -6.2), (13.6, y0 + 0.4, zb), "wood", m=m)
        for x0, x1 in ((2.2, 2.5), (13.5, 13.8)):
            K.box((x0, y0, -6.2), (x1, y0 + 2.4, zb), "paint", m=m)
        rings = []
        for k in range(9):
            a = math.pi * k / 8
            rings.append((8.0 + math.cos(a) * 5.7, y0 + 2.4 + math.sin(a) * 3.0))
        K.prism(rings, "z", -6.0, zb - 0.2, "canvas_green", m=m)
        for z in (-5.6, 0.0, 6.0, 12.0, zb - 0.6):
            K.box((2.2, y0 + 2.2, z), (13.8, y0 + 2.6, z + 0.25), "canvas", m=m)
    elif kind == "fuel":
        # the tank on its saddles, the walkway and the hose reels, the hazard placard
        rings = [[v3(8.0 + math.cos(a) * 5.2, y0 + 3.4 + math.sin(a) * 3.0, z) for a in [i * math.pi * 2 / 18 for i in range(18)]] for z in (-5.6, zb - 0.4)]
        K.loft(rings, "light_grey", caps=(True, True), m=m)
        for z in (-3.0, 4.0, 11.0):
            K.box((3.0, y0, z), (13.0, y0 + 1.0, z + 0.6), "dark", m=m)
            K.cylinder("z", (8.0, y0 + 3.4), 5.25, z - 0.1, z + 0.1, "steel", n=18, caps=(False, False), m=m)
        K.box((5.6, y0 + 6.3, -5.0), (10.4, y0 + 6.5, zb - 1.0), "tread_plate", m=m)
        for z in (0.0, 8.0):
            K.cylinder("y", (8.0, z), 0.9, y0 + 6.3, y0 + 6.8, "steel", n=12, m=m)
        K.box((6.2, y0 + 1.6, zb - 0.43), (9.8, y0 + 4.6, zb - 0.39), "warning", m=m, faces="Z")
        K.cylinder("x", (y0 + 1.0, zb - 2.0), 1.0, 1.4, 2.6, "red", n=12, m=m)
    elif kind == "ammo":
        # crates on the bed, the loader crane behind the cab
        K.box((2.4, y0, -6.2), (13.6, y0 + 0.4, zb), "wood", m=m)
        for i in range(4):
            for j in range(2):
                z = -5.6 + i * 5.6
                x = 3.0 + j * 5.4
                K.rbox((x, y0 + 0.4, z), (x + 4.8, y0 + 2.8, z + 4.8), 0.2, "olive", seg=1, m=m)
                K.box((x + 0.6, y0 + 2.0, z - 0.02), (x + 4.2, y0 + 2.5, z), "warning", m=m, faces="z")
        K.cylinder("y", (8.0, -6.6), 0.8, y0, y0 + 4.0, "yellow", n=10, m=m)
        K.tube([(8.0, y0 + 4.0, -6.6), (8.0, y0 + 5.6, 4.0)], 0.4, "yellow", n=8, m=m)
        K.tube([(8.0, y0 + 5.6, 4.0), (8.0, y0 + 3.6, 5.0)], 0.08, "dark", n=4, m=m)
    elif kind == "command":
        # the shelter box with its door and air conditioner, the antenna masts, the generator trailer hitch
        K.rbox((2.4, y0, -6.0), (13.6, y0 + 6.4, zb - 0.6), 0.3, "paint", seg=1, m=m)
        K.box((10.0, y0 + 0.6, zb - 0.62), (12.6, y0 + 5.6, zb - 0.58), "paint_dark", m=m, faces="Z")
        K.rbox((3.4, y0 + 3.2, zb - 0.62), (6.4, y0 + 5.4, zb), 0.15, "grille", seg=1, m=m)
        for x, z, h in ((3.6, -4.6, 18.0), (12.4, 14.0, 14.0)):
            K.cylinder("y", (x, z), 0.25, y0 + 6.4, y0 + 6.4 + h, "steel", n=6, m=m)
            for k in range(3):
                K.box((x - 1.0, y0 + 6.4 + h - 1.0 - k * 1.2, z - 0.05), (x + 1.0, y0 + 6.4 + h - 0.9 - k * 1.2, z + 0.05), "steel", m=m)
        K.box((5.0, y0 + 6.4, 2.0), (11.0, y0 + 6.6, 8.0), "array", m=m)
        for z in (0.0, 6.0, 12.0):
            side_glass(m, 2.38, y0 + 3.0, y0 + 4.6, z, z + 2.0)
    elif kind == "recovery":
        # the crane boom on its turntable, the winch, the rear spade and the towing fork
        K.box((2.4, y0, -6.2), (13.6, y0 + 0.6, zb), "tread_plate", m=m)
        K.cylinder("y", (8.0, 4.0), 2.6, y0 + 0.6, y0 + 1.6, "paint_dark", n=16, m=m)
        K.rbox((5.6, y0 + 1.6, 1.6), (10.4, y0 + 3.6, 6.4), 0.3, "yellow", seg=1, m=m)
        K.tube([(8.0, y0 + 3.0, 4.0), (8.0, y0 + 7.4, 15.4)], 0.55, "yellow", n=8, m=m)
        K.tube([(8.0, y0 + 7.4, 15.4), (8.0, y0 + 3.0, 16.6)], 0.06, "dark", n=4, m=m)
        K.cylinder("x", (y0 + 2.2, 9.0), 0.9, 5.6, 10.4, "gunmetal", n=12, m=m)
        K.box((4.0, 0.6, zb - 0.4), (12.0, y0, zb + 0.4), "paint_dark", m=m)
        K.tube([(6.0, 2.4, zb + 0.3), (8.0, 2.0, zb + 2.6), (10.0, 2.4, zb + 0.3)], 0.3, "dark", n=6, m=m)
    spots = [(v3(2.98, 7.6, -10.6), (-1, 0, 0), (0, 1, 0), 0.8), (v3(13.02, 7.6, -10.6), (1, 0, 0), (0, 1, 0), 0.8)]
    parts = {"body": m}
    parts.update(marks(spots))
    return parts, g


CARS3 = {"stryker": lambda: ifv8("stryker"), "boxer": lambda: ifv8("boxer", (-12.6, 21.6), 9.4, (8.0, 6.0), 4.0, True),
         "mrap": mrap, "jltv": jltv, "truck": lambda: truck("truck", "cargo"), "fuel_truck": lambda: truck("fuel_truck", "fuel"),
         "ammo_truck": lambda: truck("ammo_truck", "ammo"), "command_truck": lambda: truck("command_truck", "command"),
         "recovery_truck": lambda: truck("recovery_truck", "recovery")}

if __name__ == "__main__":
    import os
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    for k, fn in CARS3.items():
        parts, g = fn()
        for n, mesh in parts.items():
            K.write_part(mesh, "entity/" + k, n, RES)
        print(k, sum(p.quad_count() for p in parts.values()))
