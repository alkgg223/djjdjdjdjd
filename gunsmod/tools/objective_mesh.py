"""Game-mode objectives, model units 1/16 block, centred on (8, 0, 8).

pole        capture point: a concrete footing with sandbags round it, the steel mast (four blocks tall) with its
            halyard cleat and the finial; the flag is its own part and climbs the mast as the point is taken
flag        a flag cloth on its hoist (white paint, tinted with the owner's colour by the renderer), hoist at x = 8
stand       capture-the-flag stand: a tripod with a short mast, the team plate on the front
target      command post / demolition target: stacked radio crates, a generator, a field desk, the antenna mast
destination convoy destination: a supply depot gate post with a striped boom and a sign board
control     mission control table: a folding table with the map board, a field radio, a laptop and a lamp"""
import math
import os

import numpy as np

import meshkit as K
from meshkit import Mesh, v3

MAST_TOP = 64.0


def pole():
    m = Mesh()
    K.rbox((3.0, 0.0, 3.0), (13.0, 1.6, 13.0), 0.5, "light_grey", seg=1, m=m)
    for k in range(8):
        a = math.radians(k * 45)
        c, s = math.cos(a), math.sin(a)
        K.rbox((8 + c * 5.6 - 1.4, 0.0, 8 + s * 5.6 - 0.9), (8 + c * 5.6 + 1.4, 1.3, 8 + s * 5.6 + 0.9), 0.5, "canvas", seg=1, m=m)
    K.cylinder("y", (8.0, 8.0), 0.55, 1.6, MAST_TOP, "steel", n=10, m=m)
    K.cylinder("y", (8.0, 8.0), 0.8, 1.6, 3.0, "gunmetal", n=10, m=m)
    K.sphere((8.0, MAST_TOP + 0.6, 8.0), 0.9, 0.9, 0.9, "brass", n=10, rows=5, m=m)
    K.box((8.5, 5.0, 7.7), (9.4, 5.6, 8.3), "gunmetal", m=m)
    K.tube([(8.6, 5.3, 8.0), (8.6, MAST_TOP - 0.5, 8.0)], 0.06, "white", n=3, m=m)
    return m


def flag():
    m = Mesh()
    # the cloth hangs from the hoist at x = 8 out to x = 22, gently rippled
    cols, rows = 10, 4
    for i in range(cols):
        for j in range(rows):
            x0, x1 = 8.6 + i * 1.4, 8.6 + (i + 1) * 1.4
            y0, y1 = -9.0 + j * 2.25, -9.0 + (j + 1) * 2.25
            def z(x):
                return 8 + math.sin((x - 8.6) * 0.55) * 0.7 * ((x - 8.6) / 14)
            pts = [v3(x0, y0, z(x0)), v3(x1, y0, z(x1)), v3(x1, y1, z(x1)), v3(x0, y1, z(x0))]
            m.add(pts, "paint")
            m.add(list(reversed(pts)), "paint")
    K.cylinder("y", (8.0, 8.0), 0.35, -9.2, 0.2, "white", n=6, m=m)
    return m


def stand():
    m = Mesh()
    for k in range(3):
        a = math.radians(90 + k * 120)
        c, s = math.cos(a), math.sin(a)
        K.tube([(8, 10, 8), (8 + c * 6, 0.2, 8 + s * 6)], 0.35, "gunmetal", n=6, m=m)
        K.cylinder("y", (8 + c * 6, 8 + s * 6), 0.8, 0.0, 0.4, "rubber", n=8, m=m)
    K.cylinder("y", (8.0, 8.0), 0.45, 6.0, 36.0, "steel", n=8, m=m)
    K.cylinder("y", (8.0, 8.0), 0.9, 9.0, 11.0, "gunmetal", n=10, m=m)
    K.sphere((8.0, 36.5, 8.0), 0.8, 0.8, 0.8, "brass", n=8, rows=4, m=m)
    K.box((5.5, 12.0, 6.9), (10.5, 16.0, 7.3), "paint_dark", m=m)
    return m


def target():
    m = Mesh()
    # two stacked radio crates, a third beside, with latches and the stencil plate
    for (x0, y0, z0, x1, y1, z1) in ((2.0, 0.0, 3.0, 9.5, 5.0, 9.0), (2.5, 5.0, 3.5, 9.0, 9.0, 8.5), (10.0, 0.0, 2.5, 14.5, 6.5, 8.0)):
        K.rbox((x0, y0, z0), (x1, y1, z1), 0.35, "olive", seg=1, m=m)
        K.box((x0 + 0.6, y1 - 1.0, z0 - 0.1), (x1 - 0.6, y1 - 0.6, z0), "olive_dark", m=m, faces="z")
        for xx in (x0 + 1.0, x1 - 1.6):
            K.box((xx, (y0 + y1) / 2 - 0.4, z0 - 0.25), (xx + 0.6, (y0 + y1) / 2 + 0.4, z0), "steel", m=m)
    K.box((3.5, 6.0, 3.3), (8.0, 8.0, 3.5), "screen", m=m, faces="z")
    for k in range(5):
        K.cylinder("z", (4.2 + k * 0.8, 5.6), 0.22, 3.2, 3.5, "dark", n=6, m=m)
    # the generator on its skid, the fuel can
    K.box((1.5, 0.0, 10.0), (8.5, 0.6, 14.5), "gunmetal", m=m)
    K.rbox((2.0, 0.6, 10.4), (8.0, 4.6, 14.0), 0.5, "yellow", seg=1, m=m)
    K.cylinder("x", (2.6, 12.2), 1.0, 8.0, 8.4, "grille", n=10, m=m)
    K.tube([(7.0, 4.6, 11.0), (7.4, 6.2, 11.0), (8.4, 6.6, 11.0)], 0.25, "burnt_metal", n=5, m=m)
    K.rbox((10.5, 0.0, 10.5), (13.5, 4.0, 12.5), 0.4, "olive_dark", seg=1, m=m)
    # the antenna mast with its guys and the whip at the top
    K.cylinder("y", (12.0, 5.5), 0.3, 6.5, 40.0, "steel", n=6, m=m)
    for (gx, gz) in ((15.5, 9.0), (8.5, 0.5), (15.5, 0.5)):
        K.tube([(12.0, 34.0, 5.5), (gx, 0.2, gz)], 0.05, "dark", n=3, m=m)
    K.tube([(12.0, 40.0, 5.5), (12.0, 52.0, 5.5)], 0.08, "black", n=4, m=m)
    for y in (36.0, 38.0):
        K.box((10.0, y, 5.4), (14.0, y + 0.25, 5.6), "steel", m=m)
    # cables between the crates and the generator
    K.tube([(6.0, 2.0, 9.0), (6.0, 1.0, 10.0), (5.0, 0.8, 10.4)], 0.18, "black", n=4, m=m)
    return m


def destination():
    m = Mesh()
    K.rbox((1.0, 0.0, 6.0), (5.0, 3.0, 10.0), 0.4, "light_grey", seg=1, m=m)
    K.box((2.2, 3.0, 7.2), (3.8, 20.0, 8.8), "white", m=m)
    for k in range(4):
        K.box((2.15, 4.0 + k * 4.0, 7.15), (3.85, 6.0 + k * 4.0, 8.85), "red", m=m)
    # the boom across the road on its hinge, striped
    K.cylinder("y", (3.0, 8.0), 1.2, 14.0, 16.0, "gunmetal", n=10, m=m)
    for k in range(8):
        K.box((4.0 + k * 3.0, 14.4, 7.5), (7.0 + k * 3.0, 15.6, 8.5), "red" if k % 2 == 0 else "white", m=m)
    K.box((27.5, 0.0, 7.4), (28.5, 14.4, 8.6), "gunmetal", m=m)
    # the sign board on two posts
    K.box((-5.0, 0.0, 7.6), (-4.4, 22.0, 8.2), "gunmetal", m=m)
    K.box((0.4, 16.0, 7.6), (1.0, 22.0, 8.2), "gunmetal", m=m)
    K.box((-6.0, 18.0, 7.2), (2.0, 24.0, 7.6), "green_mark", m=m)
    K.box((-5.4, 19.0, 7.15), (1.4, 19.6, 7.2), "white_mark", m=m, faces="z")
    K.box((-5.4, 21.6, 7.15), (1.4, 22.2, 7.2), "white_mark", m=m, faces="z")
    K.sphere((3.0, 20.6, 8.0), 0.7, 0.7, 0.7, "amber", n=8, rows=4, m=m)
    return m


def control():
    m = Mesh()
    # folding table: top, the scissor legs
    K.rbox((0.5, 9.0, 2.0), (15.5, 9.8, 14.0), 0.25, "olive", seg=1, m=m)
    for x in (1.5, 14.5):
        K.tube([(x, 0.0, 3.0), (x, 9.0, 13.0)], 0.3, "gunmetal", n=5, m=m)
        K.tube([(x, 0.0, 13.0), (x, 9.0, 3.0)], 0.3, "gunmetal", n=5, m=m)
    K.tube([(1.5, 4.5, 8.0), (14.5, 4.5, 8.0)], 0.25, "gunmetal", n=5, m=m)
    # the map board standing at the back, the map on it and the pins
    K.box((1.5, 9.8, 12.0), (14.5, 20.0, 12.6), "wood", m=m)
    K.box((2.0, 10.4, 11.9), (14.0, 19.4, 12.0), "sand", m=m, faces="z")
    for (x, y, c) in ((4.0, 17.0, "red_mark"), (7.5, 13.0, "green_mark"), (10.0, 16.0, "red_mark"), (12.0, 12.0, "green_mark"), (5.5, 11.5, "amber")):
        K.sphere((x, y, 11.7), 0.4, 0.4, 0.3, c, n=6, rows=3, m=m)
    K.box((3.0, 14.4, 11.85), (13.0, 14.6, 11.9), "dark", m=m, faces="z")
    K.box((8.0, 10.8, 11.85), (8.2, 19.0, 11.9), "dark", m=m, faces="z")
    # the field radio with its handset and whip
    K.rbox((1.2, 9.8, 3.0), (6.0, 13.2, 7.0), 0.3, "olive_dark", seg=1, m=m)
    K.box((1.6, 11.2, 2.95), (5.6, 12.8, 3.0), "screen_amber", m=m, faces="z")
    for k in range(3):
        K.cylinder("z", (2.2 + k * 1.4, 10.4), 0.3, 2.7, 3.0, "dark", n=6, m=m)
    K.tube([(5.2, 13.2, 6.4), (5.6, 22.0, 6.6)], 0.07, "black", n=3, m=m)
    K.rbox((6.5, 9.8, 3.0), (7.8, 10.6, 6.0), 0.3, "black", seg=1, m=m)
    # the laptop: base and the open screen
    K.box((8.5, 9.8, 3.0), (13.5, 10.2, 7.0), "gunmetal", m=m)
    K.plate([v3(8.5, 10.2, 7.0), v3(13.5, 10.2, 7.0), v3(13.5, 13.6, 8.2), v3(8.5, 13.6, 8.2)], 0.25, "gunmetal", m=m)
    K.plate([v3(8.9, 10.5, 6.9), v3(13.1, 10.5, 6.9), v3(13.1, 13.3, 8.0), v3(8.9, 13.3, 8.0)], 0.05, "screen", m=m)
    # a lamp on a bent neck
    K.cylinder("y", (14.0, 4.0), 0.7, 9.8, 10.2, "dark", n=8, m=m)
    K.tube([(14.0, 10.2, 4.0), (14.0, 13.0, 4.4), (13.0, 14.0, 5.4)], 0.12, "dark", n=4, m=m)
    K.sphere((12.7, 13.7, 5.7), 0.7, 0.5, 0.7, "glow", n=8, rows=4, m=m)
    return m


PARTS = {"pole": pole, "flag": flag, "stand": stand, "target": target, "destination": destination, "control": control}

if __name__ == "__main__":
    RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod")
    for k, fn in PARTS.items():
        mesh = fn()
        K.write_part(mesh, "entity/objective", k, RES)
        print(k, mesh.quad_count())
