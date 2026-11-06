"""3.0 helicopters (the helicopter model units, front -Z): UH-60M Black Hawk (armed with stub-wing rocket pods and a
door minigun, carries four soldiers) and CH-47F Chinook (tandem rotors - the second hub is written as a negative
hub2 = -z of the rear hub - door gun, rocket pods on the stub pylons, carries six). GEO is written by gen_geo.py."""
import math

import meshkit as K
import vehmesh as V
from helis_mesh import stations_loft, foil, pod, wheel_gear, blade_part, hub_part, tail_parts, canopy, _marks
from meshkit import Mesh, v3

GEO = {}


def door_gun(cx, cy, cz, length=3.0, barrels=6):
    m = Mesh()
    K.rbox((cx - 0.35, cy - 0.35, cz - 0.6), (cx + 0.35, cy + 0.3, cz + 0.6), 0.1, "gunmetal", seg=1, m=m)
    for k in range(barrels):
        a = 2 * math.pi * k / barrels
        K.cylinder("z", (cx + math.cos(a) * 0.13, cy + math.sin(a) * 0.13), 0.05, cz - 0.6, cz - 0.6 - length, "gunmetal", n=5, m=m)
    K.cylinder("z", (cx, cy), 0.22, cz - length - 0.4, cz - length - 0.6, "gunmetal", n=8, m=m)
    K.rbox((cx + 0.35, cy - 0.5, cz - 0.3), (cx + 0.75, cy + 0.1, cz + 0.4), 0.1, "olive_dark", seg=1, m=m)
    return m


def uh60():
    g = dict(hub=(8.0, 10.4, -1.0), rotorR=18.0, blades=4, hub2=0.0, tail=(8.6, 9.6, 26.0), tailR=2.8, tailBlades=4,
             chin=(4.6, 3.6, -2.0), chinLen=3.0, cannon=None,
             seats=[(6.9, 2.6, -8.0), (9.1, 2.6, -8.0), (6.6, 2.0, -3.0), (9.4, 2.0, -3.0), (6.6, 2.0, 0.8), (9.4, 2.0, 0.8)],
             pods=[(1.6, 3.6, -0.4), (14.4, 3.6, -0.4)], rails=[], bombs=[], flares=[(6.8, 3.2, 8.0), (9.2, 3.2, 8.0)],
             ends=[(8.0, 3.6, -12.6), (8.0, 7.6, 27.0)], pivotY=1.3, size=(3.4, 2.8))
    GEO["uh60"] = g
    body, chin = Mesh(), Mesh()
    stations_loft(body, [(-12.4, 3.6, 0.4, 0.4, 2.2), (-12.0, 3.7, 1.6, 1.3, 2.4), (-10.6, 4.0, 2.4, 2.0, 2.8), (-8.0, 4.6, 2.9, 2.8, 3.6),
                         (-4.0, 4.8, 3.0, 3.0, 4.0), (4.0, 4.8, 3.0, 3.0, 4.0), (7.0, 5.2, 2.2, 2.4, 3.2), (10.0, 5.8, 1.2, 1.3, 2.6),
                         (20.0, 6.4, 0.75, 0.8, 2.4), (26.0, 6.8, 0.55, 0.6, 2.2), (27.0, 7.0, 0.3, 0.35, 2.2)], "paint", n=20)
    canopy(body, [(-11.8, 4.2, 1.6, 0.8), (-10.8, 4.9, 2.3, 1.7), (-9.2, 5.4, 2.6, 2.2), (-8.0, 5.5, 2.6, 2.0)], frames_at=(-10.4, -9.0))
    for x in (5.0, 11.0):
        n = (-1, 0, 0) if x < 8 else (1, 0, 0)
        for z in (-5.0, -1.4, 2.0):
            V.band(body, v3(x + (-0.02 if x < 8 else 0.02), 5.4, z), n, (0, 1, 0), 1.6, 1.3, "glass_dark")
    # the engine doghouse with its two intakes and exhausts, the mast
    K.rbox((5.8, 7.4, -5.0), (10.2, 9.4, 6.0), 0.7, "paint", seg=2, m=body)
    for x in (6.2, 9.8):
        K.cylinder("z", (x, 8.4), 0.7, -4.9, -5.5, "dark", n=12, m=body)
        K.cylinder("x", (8.6, 4.2), 0.55, x - 0.6 if x < 8 else x + 0.1, x - 0.1 if x < 8 else x + 0.6, "burnt_metal", n=10, m=body)
    K.cylinder("y", (8.0, -1.0), 0.6, 9.4, 10.6, "steel", n=10, m=body)
    # stub wings (ESSS) with the rocket pods, the sponsons, the gear
    for side in (-1, 1):
        foil(body, (8 + side * 3.0, 6.2, -1.6), (8 + side * 6.6, 5.4, -1.2), 1.8, 1.4, 0.16, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    for (px, py, pz) in g["pods"]:
        K.rbox((px - 0.2, py + 0.9, pz + 0.6), (px + 0.2, 5.6, pz + 2.0), 0.08, "dark", seg=1, m=body)
        pod(body, px, py, pz, pz + 4.4, 0.9, tubes=7)
    for side in (-1, 1):
        K.rbox((8 + side * 3.1 - 0.8, 2.4, 3.4), (8 + side * 3.1 + 0.8, 4.2, 7.4), 0.5, "paint", seg=1, m=body)
        wheel_gear(body, 8 + side * 2.8, -6.6, 2.6, 0.7)
    wheel_gear(body, 8.0, 22.0, 5.6, 0.5)
    # tail: canted tail rotor pylon, the stabilator
    foil(body, (8, 6.6, 23.4), (8.4, 10.4, 26.4), 2.6, 1.6, 0.18, "paint", chord_dir=(0, 0, 1), thick_dir=(1, 0, 0))
    for tip in (4.4, 11.6):
        foil(body, (8, 6.8, 24.0), (tip, 6.9, 24.4), 2.0, 1.6, 0.12, "paint", chord_dir=(0, 0, 1), thick_dir=(0, 1, 0))
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.25, fy - 0.5, fz - 0.7), (fx + 0.25, fy + 0.3, fz + 0.7), 0.1, "dark", seg=1, m=body)
    cx, cy, cz = g["chin"]
    chin.extend(door_gun(cx, cy, cz, g["chinLen"]))
    th, tb = tail_parts(g["tailR"], chord=0.45)
    spots = [(v3(4.98, 4.4, 4.0), (-1, 0, 0), (0, 1, 0), 0.9), (v3(11.02, 4.4, 4.0), (1, 0, 0), (0, 1, 0), 0.9)]
    parts = {"body": body, "chin": chin, "hub": hub_part(1.0), "blade": blade_part(g["rotorR"], 0.7, 1.1), "tail_hub": th, "tail_blade": tb}
    parts.update(_marks(spots))
    return parts, g


def ch47():
    g = dict(hub=(8.0, 11.0, -13.4), rotorR=18.0, blades=3, hub2=-19.6, tail=None, tailR=0.0, tailBlades=0,
             chin=(4.6, 4.0, -9.0), chinLen=2.6, cannon=None,
             seats=[(6.9, 2.6, -14.0), (9.1, 2.6, -14.0), (6.6, 2.0, -6.0), (9.4, 2.0, -6.0), (6.6, 2.0, -1.0), (9.4, 2.0, -1.0),
                    (6.6, 2.0, 4.0), (9.4, 2.0, 4.0)],
             pods=[(2.0, 3.6, -3.0), (14.0, 3.6, -3.0)], rails=[], bombs=[], flares=[(5.8, 4.2, 14.0), (10.2, 4.2, 14.0)],
             ends=[(8.0, 4.0, -17.6), (8.0, 6.0, 22.2)], pivotY=1.5, size=(4.0, 3.6))
    GEO["ch47"] = g
    body, chin = Mesh(), Mesh()
    # the long box fuselage, the rounded nose, the ramp at the back
    stations_loft(body, [(-17.4, 4.2, 0.4, 0.4, 2.2), (-17.0, 4.4, 1.8, 1.6, 2.6), (-15.6, 4.8, 2.8, 2.6, 3.4), (-13.0, 5.4, 3.2, 3.4, 4.6),
                         (14.0, 5.4, 3.2, 3.4, 4.6), (18.0, 5.6, 3.0, 3.2, 4.2), (21.6, 6.2, 2.4, 2.6, 3.4), (22.4, 6.6, 1.6, 1.6, 2.6)], "paint", n=20)
    canopy(body, [(-17.2, 4.8, 1.8, 0.8), (-16.4, 5.6, 2.6, 1.6), (-15.0, 6.2, 2.9, 2.2), (-14.0, 6.4, 2.9, 2.0)], frames_at=(-16.0, -14.8))
    for x in (4.78, 11.22):
        n = (-1, 0, 0) if x < 8 else (1, 0, 0)
        for i in range(6):
            V.band(body, v3(x, 6.2, -10.0 + i * 3.6), n, (0, 1, 0), 0.9, 0.9, "glass_dark")
    # the forward rotor pylon, the tall aft pylon with the engines either side, the spine fairing between them
    K.rbox((6.6, 8.4, -15.6), (9.4, 10.6, -11.6), 0.6, "paint", seg=2, m=body)
    K.rbox((6.0, 8.4, 14.6), (10.0, 11.8, 22.0), 0.7, "paint", seg=2, m=body)
    K.rbox((6.8, 8.6, -11.8), (9.2, 9.4, 14.8), 0.4, "paint", seg=1, m=body)
    for side in (-1, 1):
        x = 8 + side * 4.2
        K.cylinder("z", (x, 8.6), 1.0, 10.4, 18.6, "paint", n=14, m=body)
        K.cylinder("z", (x, 8.6), 0.8, 10.3, 10.4, "dark", n=12, caps=(True, False), m=body)
        K.cylinder("z", (x, 8.6), 0.7, 18.6, 19.4, "burnt_metal", n=12, m=body)
        K.tube([(x - side * 0.8, 8.6, 14.0), (8 + side * 2.2, 9.4, 15.6)], 0.3, "paint", n=6, m=body)
    for (hx, hy, hz) in ((8.0, 10.6, -13.4), (8.0, 11.8, 19.6)):
        K.cylinder("y", (hx, hz), 0.6, hy, 11.2 if hz < 0 else 12.0, "steel", n=10, m=body)
    # the side fuel pods (fairings) along the lower fuselage, the gear, the ramp, the stub pylons with the pods
    for side in (-1, 1):
        K.rbox((8 + side * 3.2 - (1.0 if side < 0 else 0), 2.4, -11.0), (8 + side * 3.2 + (0 if side < 0 else 1.0), 4.6, 10.0), 0.6, "paint", seg=1, m=body)
        wheel_gear(body, 8 + side * 3.4, -10.0, 2.6, 0.8)
        wheel_gear(body, 8 + side * 3.4, 12.0, 2.6, 0.8)
    K.plate([v3(5.4, 2.4, 22.2), v3(10.6, 2.4, 22.2), v3(10.6, 5.4, 21.0), v3(5.4, 5.4, 21.0)], 0.3, "paint_mid", m=body)
    for (px, py, pz) in g["pods"]:
        K.rbox((px - 0.2 if px < 8 else px - 1.4, py + 0.6, pz + 0.6), (px + 1.4 if px < 8 else px + 0.2, py + 1.0, pz + 2.0), 0.08, "dark", seg=1, m=body)
        pod(body, px, py, pz, pz + 4.4, 0.9, tubes=7)
    for fx, fy, fz in g["flares"]:
        K.rbox((fx - 0.25, fy - 0.5, fz - 0.7), (fx + 0.25, fy + 0.3, fz + 0.7), 0.1, "dark", seg=1, m=body)
    cx, cy, cz = g["chin"]
    chin.extend(door_gun(cx, cy, cz, g["chinLen"], barrels=1))
    spots = [(v3(4.78, 4.4, 8.0), (-1, 0, 0), (0, 1, 0), 1.0), (v3(11.22, 4.4, 8.0), (1, 0, 0), (0, 1, 0), 1.0)]
    parts = {"body": body, "chin": chin, "hub": hub_part(1.1), "blade": blade_part(g["rotorR"], 0.9, 1.2)}
    parts.update(_marks(spots))
    return parts, g


HELIS3 = {"heli_uh60": uh60, "heli_ch47": ch47}

if __name__ == "__main__":
    import os
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    for k, fn in HELIS3.items():
        parts, g = fn()
        for n, mesh in parts.items():
            K.write_part(mesh, "entity/" + k, n, RES)
        print(k, sum(p.quad_count() for p in parts.values()))
