"""Main battle tank and attack helicopter (1 unit = 0.25 block). Front at -Z, +X = right side, ground y=0,
(8, 0, 8) = entity origin. Every number used by the Java side is defined at the top of each section."""
import math

from modelkit import Model

P = "paint_white"     # tinted with the team colour at runtime

# ------------------------------------------------------------------------------------- tank
TANK_TURRET_PIVOT = (8.0, 9.0)            # x, z of the turret ring centre
TANK_GUN_PIVOT = (8.0, 7.6, 2.6)          # trunnion (y, z in turret space)
TANK_SEAT = (10.6, 6.6, 11.0)             # commander: seat point inside the cupola (turret space)
TRACK_X = (2.5, 13.5)                     # centre lines of the two tracks
ROAD_WHEELS_Z = [-1.4 + i * 3.15 for i in range(7)]
ROAD_WHEEL_Y, ROAD_WHEEL_R = 1.75, 1.3
IDLER = (2.3, -4.0, 1.15)                 # y, z, radius
SPROCKET = (2.4, 20.0, 1.3)
LINK = 0.75                               # track link pitch
TRACK_BOTTOM = (0.0, -2.6)                # run y, first link z
TRACK_TOP = (3.05, -3.4)


def _track_static(m, x0, x1):
    """track sections bent around the idler and the sprocket (the straight runs are animated parts)"""
    T = "track"
    # front: rising from the bottom run, around the idler, back to the top run
    m.box([x0, 0.55, -4.9], [x1, 0.95, -2.3], T, rot=("x", 22.5, [8, 0.75, -3.6]), part="body")
    m.box([x0, 1.3, -5.6], [x1, 3.2, -5.2], T, part="body")
    m.box([x0, 3.35, -5.4], [x1, 3.75, -3.8], T, rot=("x", -22.5, [8, 3.55, -4.6]), part="body")
    # rear: around the sprocket
    m.box([x0, 0.5, 18.4], [x1, 0.9, 21.0], T, rot=("x", -22.5, [8, 0.7, 19.7]), part="body")
    m.box([x0, 1.3, 21.55], [x1, 3.4, 21.95], T, part="body")
    m.box([x0, 3.5, 20.0], [x1, 3.9, 21.7], T, rot=("x", 22.5, [8, 3.7, 20.85]), part="body")


def tank():
    m = Model("tank", density=6.0, atlas_w=1024)
    b = m.box
    # ---------------------------------------------------------------- hull
    b([3.8, 0.9, -4.4], [12.2, 3.8, 20.6], P, part="body", deco=("grime",))                      # lower hull
    b([1.0, 3.7, -2.6], [15.0, 5.9, 21.0], P, part="body", deco=("panel", "camo"))               # sponsons / upper hull
    # glacis + lower front plate
    b([1.0, 4.85, -6.35], [15.0, 5.45, -2.45], P, rot=("x", -22.5, [8, 5.15, -4.4]), part="body", deco=("camo",))
    b([3.0, 1.5, -6.0], [13.0, 4.5, -5.4], P, rot=("x", -22.5, [8, 3.0, -5.7]), part="body", deco=("rivets", "grime"))
    # spare track links on the glacis
    for i in range(4):
        x = 4.4 + i * 1.9
        b([x, 5.45, -3.3], [x + 1.6, 5.7, -2.6], "track", rot=("x", -22.5, [8, 5.15, -4.4]), part="body")
    # rear plate, exhaust grille, lights, hooks
    b([1.0, 1.4, 20.9], [15.0, 5.9, 21.4], P, part="body", deco=("panel", "grime"))
    b([4.0, 3.2, 21.4], [12.0, 5.4, 21.6], "dark_paint", part="body", deco=("vslots",))
    for x in (1.6, 13.4):
        b([x, 4.6, 21.4], [x + 1.0, 5.3, 21.55], "glow_red", part="body", light=True)
    for x in (3.0, 12.0):
        b([x, 1.6, 21.4], [x + 1.0, 2.4, 21.9], "steel", part="body")
    # engine deck: grille slats + access panels
    for i in range(9):
        z = 13.6 + i * 0.78
        b([3.0, 5.9, z], [13.0, 6.05, z + 0.42], "dark_paint", part="body")
    b([2.0, 5.9, 8.0], [14.0, 6.0, 13.2], P, part="body", deco=("panel",))
    # turret ring
    m.cyl("y", TANK_TURRET_PIVOT, 4.6, 5.9, 6.35, "dark_paint", sides=16, part="body")
    # driver hatch + periscopes
    b([6.8, 5.9, -2.3], [9.2, 6.15, -0.6], P, part="body", deco=("rivets",))
    for x in (6.6, 7.6, 8.6):
        b([x, 5.95, -2.55], [x + 0.8, 6.25, -2.3], "lens", part="body")
    # headlight clusters with guards
    for x in (1.4, 13.0):
        b([x, 5.9, -2.5], [x + 1.6, 6.7, -1.7], "dark_paint", part="body")
        b([x + 0.25, 6.05, -2.56], [x + 1.35, 6.5, -2.48], "glow_orange", part="body", light=True)
    # fender stowage boxes, tow cable, jerry cans
    for x0, x1 in ((1.2, 3.4), (12.6, 14.8)):
        b([x0, 5.9, 1.5], [x1, 6.7, 6.5], "od_paint", part="body", deco=("panel", "stencil:TOOLS"))
        b([x0, 5.9, 16.0], [x1, 6.9, 19.6], "od_paint", part="body", deco=("panel",))
    b([1.3, 5.95, -1.0], [1.6, 6.25, 1.2], "steel", part="body")
    b([14.4, 5.95, -1.0], [14.7, 6.25, 1.2], "steel", part="body")
    for x in (4.2, 9.4):
        b([x, 1.6, 21.6], [x + 2.4, 3.0, 22.4], "od_paint", part="body", deco=("stencil:F",))
    # side skirts (six armoured panels per side)
    for xs in ((0.6, 1.0), (15.0, 15.4)):
        for i in range(6):
            z0 = -3.4 + i * 4.0
            b([xs[0], 1.2, z0 + 0.06], [xs[1], 3.9, z0 + 3.94], P, part="body", deco=("panel", "rivets", "camo", "grime"))
        b([xs[0] - 0.05, 0.9, -3.4], [xs[1] + 0.05, 1.2, 20.6], "rubber", part="body")
    # static track bends at both ends
    for tx in TRACK_X:
        _track_static(m, tx - 1.3, tx + 1.3)
    # ---------------------------------------------------------------- team markings
    for x0 in (0.55, 15.4):
        b([x0, 1.6, 1.0], [x0 + 0.05, 3.5, 4.4], "decal", part="mark_green", deco=("star",))
        b([x0, 1.6, 1.0], [x0 + 0.05, 3.5, 4.4], "decal", part="mark_red", deco=("star:red",))
    b([6.0, 5.46, -6.0], [10.0, 5.5, -3.6], "decal", rot=("x", -22.5, [8, 5.15, -4.4]), part="mark_green", deco=("star",))
    b([6.0, 5.46, -6.0], [10.0, 5.5, -3.6], "decal", rot=("x", -22.5, [8, 5.15, -4.4]), part="mark_red", deco=("star:red",))
    # ---------------------------------------------------------------- turret
    tx, tz = TANK_TURRET_PIVOT
    t = dict(part="turret")
    b([3.2, 6.3, 4.6], [12.8, 8.9, 14.8], P, deco=("panel", "camo"), **t)
    b([5.4, 6.3, 2.0], [10.6, 8.9, 4.6], P, deco=("camo",), **t)
    b([3.6, 6.3, 1.9], [6.2, 8.9, 5.3], P, rot=("y", -22.5, [4.9, 7.6, 3.6]), deco=("camo",), **t)
    b([9.8, 6.3, 1.9], [12.4, 8.9, 5.3], P, rot=("y", 22.5, [11.1, 7.6, 3.6]), deco=("camo",), **t)
    b([3.8, 6.5, 14.8], [12.2, 8.7, 18.0], P, deco=("panel",), **t)                              # bustle
    # stowage basket
    b([3.0, 6.6, 18.0], [13.0, 6.75, 20.0], "dark_paint", deco=("vlines:8",), **t)
    for x0, x1 in ((3.0, 3.18), (12.82, 13.0)):
        b([x0, 8.0, 15.0], [x1, 8.15, 20.0], "dark_paint", **t)
        for z in (15.0, 17.5, 19.82):
            b([x0, 6.6, z], [x1, 8.15, z + 0.18], "dark_paint", **t)
    b([3.0, 8.0, 19.82], [13.0, 8.15, 20.0], "dark_paint", **t)
    for x in (4.6, 8.8):
        b([x - 0.5, 6.75, 18.2], [x + 2.4, 7.8, 19.7], "canvas_green", deco=("lines:1",), **t)
    b([7.0, 6.75, 18.3], [8.4, 8.3, 19.6], "canvas", **t)
    # side stowage boxes with tactical numbers
    b([2.5, 6.6, 8.6], [3.2, 8.4, 14.2], P, deco=("panel", "rivets"), **t)
    b([12.8, 6.6, 8.6], [13.5, 8.4, 14.2], P, deco=("panel", "rivets"), **t)
    b([2.45, 7.0, 10.6], [2.5, 8.0, 13.6], "decal", part="mark_green", deco=("star",))
    b([13.5, 7.0, 10.6], [13.55, 8.0, 13.6], "decal", part="mark_green", deco=("star",))
    b([2.45, 7.2, 9.0], [2.5, 7.8, 14.0], "decal", part="mark_red", deco=("stripe:red",))
    b([13.5, 7.2, 9.0], [13.55, 7.8, 14.0], "decal", part="mark_red", deco=("stripe:red",))
    # smoke grenade launchers
    for x0 in (2.5, 12.7):
        b([x0, 7.5, 4.4], [x0 + 0.8, 8.7, 6.2], "dark_paint", **t)
        for i in range(3):
            y = 7.6 + i * 0.36
            b([x0 + 0.15, y, 4.3], [x0 + 0.65, y + 0.28, 4.42], "black", **t)
    # commander cupola, vision blocks and open hatch
    cx, cy0, cz = TANK_SEAT[0], 8.9, TANK_SEAT[2]
    m.cyl("y", (cx, cz), 1.3, cy0, cy0 + 0.7, P, sides=16, **t)
    for k in range(6):
        a = math.radians(k * 60 + 30)
        px, pz = cx + math.sin(a) * 1.32, cz - math.cos(a) * 1.32
        m.cbox([px, cy0 + 0.42, pz], [0.45, 0.22, 0.45], "lens", **t)
    b([cx - 1.1, cy0 + 0.7, cz + 1.25], [cx + 1.1, cy0 + 2.7, cz + 1.5], P, deco=("rivets",), **t)  # lid, open upright
    # loader hatch and the loader's machine gun
    b([4.2, 8.9, 9.4], [6.6, 9.15, 11.6], P, deco=("rivets",), **t)
    b([5.25, 9.15, 8.45], [5.55, 9.9, 8.75], "steel", **t)
    b([5.15, 9.9, 7.4], [5.65, 10.35, 9.6], "gunmetal", **t)
    m.cyl("z", (5.4, 10.15), 0.13, 4.7, 7.4, "blued", sides=8, **t)
    b([5.65, 9.75, 8.0], [6.3, 10.2, 8.8], "od_paint", **t)
    # gunner's primary sight + commander's independent viewer
    b([8.9, 8.9, 4.8], [11.4, 9.9, 6.6], P, **t)
    b([9.3, 9.1, 4.72], [11.0, 9.7, 4.82], "lens", **t)
    m.cyl("y", (5.2, 6.2), 0.55, 8.9, 9.7, P, sides=8, **t)
    b([4.6, 9.7, 5.6], [5.8, 10.3, 6.8], "dark_paint", **t)
    b([4.75, 9.8, 5.52], [5.65, 10.2, 5.6], "lens", **t)
    # wind sensor + antennas
    b([7.9, 8.9, 14.0], [8.1, 10.6, 14.2], "steel", **t)
    b([7.4, 10.6, 14.0], [8.6, 10.75, 14.2], "steel", **t)
    for x in (3.9, 11.9):
        b([x - 0.2, 8.9, 14.4], [x + 0.4, 9.4, 15.0], "dark_paint", **t)
        b([x, 9.4, 14.6], [x + 0.2, 16.5, 14.8], "rubber", **t)
    # ---------------------------------------------------------------- main gun (pitch about the trunnion)
    gx, gy, gz = TANK_GUN_PIVOT
    g = dict(part="gun")
    b([6.0, 6.5, 0.9], [10.0, 8.8, 2.6], P, deco=("rivets",), **g)                                # mantlet
    m.cyl("z", (gx, gy), 0.78, -0.7, 1.0, P, sides=8, **g)
    m.cyl("z", (gx, gy), 0.5, -14.2, -0.7, P, sides=8, **g)
    for z in (-3.0, -5.6, -10.6, -12.8):
        m.cyl("z", (gx, gy), 0.57, z, z + 0.25, "dark_paint", sides=8, **g)
    m.cyl("z", (gx, gy), 0.7, -8.8, -6.4, P, sides=8, **g)                                         # bore evacuator
    m.cyl("z", (gx, gy), 0.57, -14.8, -14.2, "dark_paint", sides=8, **g)
    b([gx - 0.34, gy - 0.34, -14.86], [gx + 0.34, gy + 0.34, -14.8], "black", **g)
    b([gx - 0.15, gy + 0.55, -14.4], [gx + 0.15, gy + 0.85, -13.9], "steel", **g)                # muzzle reference sensor
    b([9.5, 7.0, 0.82], [9.9, 7.4, 0.9], "black", **g)                                           # coaxial MG port
    return m


def tank_gear():
    """running gear: road wheel, idler, sprocket (centred on 8,8,8, axis X) and the two animated track runs"""
    m = Model("tank_gear", density=8.0, atlas_w=512)
    r = ROAD_WHEEL_R
    for x0, x1 in ((7.0, 7.85), (8.15, 9.0)):
        m.cyl("x", (8, 8), r, x0, x1, "rubber", sides=16, part="roadwheel")
        m.cyl("x", (8, 8), r * 0.72, x0 - 0.05, x1 + 0.05, P, sides=16, part="roadwheel")
    m.cyl("x", (8, 8), 0.35, 6.8, 9.2, "steel", sides=8, part="roadwheel")
    for k in range(6):
        a = math.radians(k * 60)
        y, z = 8 + math.sin(a) * 0.55, 8 + math.cos(a) * 0.55
        m.box([6.9, y - 0.08, z - 0.08], [9.1, y + 0.08, z + 0.08], "bright", part="roadwheel")
    # idler
    ri = IDLER[2]
    m.cyl("x", (8, 8), ri, 7.1, 8.9, "steel", sides=16, part="idler")
    m.cyl("x", (8, 8), ri * 0.55, 6.95, 9.05, P, sides=8, part="idler")
    m.cyl("x", (8, 8), 0.3, 6.8, 9.2, "bright", sides=8, part="idler")
    # sprocket: two toothed rings
    rs = SPROCKET[2]
    for x0, x1 in ((7.0, 7.6), (8.4, 9.0)):
        m.cyl("x", (8, 8), rs * 0.86, x0, x1, "gunmetal", sides=16, part="sprocket")
    for k in range(10):
        a = math.radians(k * 36)
        y, z = 8 + math.sin(a) * rs * 0.92, 8 + math.cos(a) * rs * 0.92
        for x0, x1 in ((7.0, 7.6), (8.4, 9.0)):
            m.cbox([(x0 + x1) / 2, y, z], [0.6, 0.32, 0.32], "gunmetal", part="sprocket")
    m.cyl("x", (8, 8), rs * 0.5, 6.9, 9.1, P, sides=8, part="sprocket")
    m.cyl("x", (8, 8), 0.32, 6.7, 9.3, "bright", sides=8, part="sprocket")
    # straight runs: 29 links, first one at z = -8; horns point up into the wheels (bottom) / down (top)
    for k in range(29):
        z0 = -8 + k * LINK
        m.box([6.7, 0.0, z0], [9.3, 0.4, z0 + 0.6], "track", part="track_bottom")
        m.box([7.85, 0.4, z0 + 0.15], [8.15, 0.75, z0 + 0.45], "steel", part="track_bottom")
        m.box([6.7, 0.0, z0], [9.3, 0.4, z0 + 0.6], "track", part="track_top")
        m.box([7.85, -0.35, z0 + 0.15], [8.15, 0.0, z0 + 0.45], "steel", part="track_top")
    return m


# ------------------------------------------------------------------------------------- helicopter
HELI_ROTOR_HUB = (8.0, 10.7, 3.0)          # main rotor axis (x, y, z)
HELI_TAIL_HUB = (8.95, 8.4, 28.3)          # tail rotor axis (along X)
HELI_CHIN = (8.0, 1.6, -9.6)               # chin turret pivot
HELI_SEATS = [(8.0, 2.72, -1.8), (8.0, 2.12, -6.6)]   # pilot (rear, raised) and front seat
HELI_PODS = [(3.5, 2.95, -0.6), (12.5, 2.95, -0.6)]  # rocket pod muzzles
ROTOR_R = 18.4


def heli():
    m = Model("heli", density=6.0, atlas_w=1024)
    b = m.box
    # ---------------------------------------------------------------- nose + cockpits
    b([6.6, 2.6, -11.6], [9.4, 5.0, -9.5], P, part="body", deco=("camo",))
    b([7.0, 3.0, -12.5], [9.0, 4.7, -11.6], P, part="body")
    b([7.2, 3.6, -12.6], [8.8, 4.4, -12.5], "lens", part="body")                                   # nose sensor window
    b([6.0, 2.0, -9.5], [10.0, 4.8, -4.5], P, part="body", deco=("panel", "camo"))                 # gunner station
    b([6.0, 2.0, -4.5], [10.0, 5.3, 0.5], P, part="body", deco=("panel", "camo"))                  # pilot station
    # canopy glass with frames
    G, F = "glass_clear", "dark_paint"
    b([6.3, 4.8, -9.65], [9.7, 7.0, -9.35], G, rot=("x", 22.5, [8, 4.8, -9.5]), part="body")
    b([6.0, 4.8, -9.2], [6.2, 7.1, -4.6], G, part="body")
    b([9.8, 4.8, -9.2], [10.0, 7.1, -4.6], G, part="body")
    b([6.2, 7.0, -8.7], [9.8, 7.25, -4.6], G, part="body")
    b([6.3, 7.25, -4.6], [9.7, 7.8, -4.4], G, part="body")                                          # step to the pilot screen
    b([6.0, 5.3, -4.4], [6.2, 7.8, 0.3], G, part="body")
    b([9.8, 5.3, -4.4], [10.0, 7.8, 0.3], G, part="body")
    b([6.2, 7.8, -4.4], [9.8, 8.05, 0.3], G, part="body")
    for z in (-9.25, -4.55, 0.3):
        b([5.95, 4.8, z], [6.25, 8.05 if z > -5 else 7.25, z + 0.2], F, part="body")
        b([9.75, 4.8, z], [10.05, 8.05 if z > -5 else 7.25, z + 0.2], F, part="body")
    b([7.9, 7.0, -8.7], [8.1, 8.1, 0.3], F, part="body")                                            # centre frame
    # cockpit interior (seen through the glass)
    b([6.4, 4.4, -8.6], [9.6, 5.4, -8.0], F, part="body")
    b([6.9, 4.9, -8.05], [9.1, 5.35, -7.95], "screen", part="body", light=True)
    b([6.4, 5.4, -3.6], [9.6, 6.2, -3.0], F, part="body")
    b([6.9, 5.7, -3.05], [9.1, 6.15, -2.95], "screen_red", part="body", light=True)
    b([6.6, 2.4, -5.8], [9.4, 5.6, -5.2], "canvas", part="body")
    b([6.6, 3.0, -0.2], [9.4, 6.4, 0.4], "canvas", part="body")
    # ---------------------------------------------------------------- engine, mast
    b([5.8, 2.0, 0.5], [10.2, 6.4, 9.0], P, part="body", deco=("panel", "camo", "grime"))
    b([6.2, 6.4, -0.5], [9.8, 8.4, 8.0], P, part="body", deco=("panel", "camo"))
    for x0 in (5.9, 9.8):
        b([x0, 6.7, 0.4], [x0 + 0.3, 7.9, 2.6], "black", part="body")                                 # intakes
        b([x0, 6.9, 7.2], [x0 + 0.3, 7.9, 9.4], "dark_paint", part="body", deco=("vslots",))         # exhausts
    m.cyl("y", (HELI_ROTOR_HUB[0], HELI_ROTOR_HUB[2]), 0.95, 8.4, 9.6, P, sides=8, part="body")
    m.cyl("y", (HELI_ROTOR_HUB[0], HELI_ROTOR_HUB[2]), 0.42, 9.6, 10.45, "steel", sides=8, part="body")
    # ---------------------------------------------------------------- tail
    b([6.8, 3.4, 9.0], [9.2, 6.2, 16.0], P, part="body", deco=("camo",))
    b([7.1, 3.8, 16.0], [8.9, 5.8, 22.0], P, part="body", deco=("camo",))
    b([7.3, 4.0, 22.0], [8.7, 5.5, 27.2], P, part="body")
    b([7.6, 6.2, 8.0], [8.4, 6.5, 20.0], "dark_paint", part="body")                                   # drive shaft cover
    b([7.6, 5.0, 25.8], [8.4, 10.6, 28.0], P, rot=("x", 22.5, [8, 5.0, 26.9]), part="body", deco=("camo",))   # fin
    b([3.8, 4.6, 20.0], [12.2, 4.9, 21.8], P, part="body")                                            # stabilizer
    b([7.85, 2.6, 25.6], [8.15, 4.0, 26.0], "dark_paint", part="body")                                # tail skid
    b([7.8, 5.5, 23.0], [8.2, 5.75, 23.4], "glow_red", part="body", light=True)                      # beacon
    # ---------------------------------------------------------------- wings, pods, launchers
    b([2.0, 4.2, 1.0], [5.8, 4.6, 4.2], P, part="body")
    b([10.2, 4.2, 1.0], [14.0, 4.6, 4.2], P, part="body")
    b([1.8, 4.25, 2.0], [2.0, 4.55, 2.6], "glow_red", part="body", light=True)
    b([14.0, 4.25, 2.0], [14.2, 4.55, 2.6], "glow_green", part="body", light=True)
    for px, py, pz in HELI_PODS:
        b([px - 0.3, 3.7, 1.6], [px + 0.3, 4.2, 3.6], "dark_paint", part="body")                      # pylon
        m.cyl("z", (px, py), 0.8, pz, pz + 5.2, "od_paint", sides=8, part="body")
        for k in range(7):
            if k == 0:
                dx = dy = 0.0
            else:
                a = math.radians(k * 60)
                dx, dy = math.cos(a) * 0.48, math.sin(a) * 0.48
            b([px + dx - 0.13, py + dy - 0.13, pz - 0.06], [px + dx + 0.13, py + dy + 0.13, pz + 0.02], "black", part="body")
    for x0 in (0.4, 14.0):
        b([x0, 3.7, 0.8], [x0 + 1.6, 4.25, 4.8], "dark_paint", part="body", deco=("panel",))          # missile launcher
        for dx in (0.4, 1.2):
            b([x0 + dx - 0.2, 3.8, 0.74], [x0 + dx + 0.2, 4.15, 0.8], "black", part="body")
    # ---------------------------------------------------------------- skids
    for x in (4.4, 11.6):
        b([x - 0.2, 0.0, -6.4], [x + 0.2, 0.35, 8.2], F, part="body")
        b([x - 0.2, 0.15, -7.7], [x + 0.2, 0.5, -6.2], F, rot=("x", 45.0, [x, 0.3, -6.6]), part="body")
        for z in (-3.6, 5.0):
            b([x - 0.17, 0.3, z - 0.17], [x + 0.17, 2.1, z + 0.17], F, part="body")
    for z in (-3.6, 5.0):
        b([4.25, 1.9, z - 0.17], [11.75, 2.2, z + 0.17], F, part="body")
    # ---------------------------------------------------------------- antennas, steps
    b([7.9, 2.0, 12.0], [8.1, 2.0 - 0.01, 13.0], F, part="body")
    b([7.85, 6.5, 13.5], [8.15, 7.6, 13.8], F, part="body")
    # ---------------------------------------------------------------- markings
    for x0 in (6.75, 9.2):
        b([x0, 3.8, 10.5], [x0 + 0.05, 5.8, 14.0], "decal", part="mark_green", deco=("star",))
        b([x0, 3.8, 10.5], [x0 + 0.05, 5.8, 14.0], "decal", part="mark_red", deco=("star:red",))
    b([7.25, 4.2, 22.4], [7.3, 5.3, 26.6], "decal", part="mark_red", deco=("stripe:red",))
    b([8.7, 4.2, 22.4], [8.75, 5.3, 26.6], "decal", part="mark_red", deco=("stripe:red",))
    b([7.25, 4.2, 22.4], [7.3, 5.3, 26.6], "decal", part="mark_green", deco=("stripe:white",))
    b([8.7, 4.2, 22.4], [8.75, 5.3, 26.6], "decal", part="mark_green", deco=("stripe:white",))
    # ---------------------------------------------------------------- chin turret (yaw + pitch)
    cx, cy, cz = HELI_CHIN
    m.cyl("y", (cx, cz), 0.9, cy - 0.5, cy + 0.4, F, sides=8, part="chin")
    b([cx - 0.45, cy - 0.45, cz - 2.0], [cx + 0.45, cy + 0.15, cz - 0.4], "gunmetal", part="chin")
    m.cyl("z", (cx, cy - 0.15), 0.16, cz - 3.4, cz - 2.0, "blued", sides=8, part="chin")
    m.cyl("z", (cx, cy - 0.15), 0.22, cz - 3.6, cz - 3.4, "gunmetal", sides=8, part="chin")
    return m


def heli_rotor():
    """main rotor (spins about Y at the hub) and tail rotor (spins about X)"""
    m = Model("heli_rotor", density=4.0, atlas_w=512)
    hx, hy, hz = HELI_ROTOR_HUB
    r = dict(part="rotor")
    m.box([hx - 0.8, hy - 0.3, hz - 0.8], [hx + 0.8, hy + 0.3, hz + 0.8], "steel", **r)
    m.box([hx - 0.25, hy + 0.3, hz - 0.25], [hx + 0.25, hy + 0.7, hz + 0.25], "bright", **r)
    c = 0.45
    m.box([hx + 0.8, hy - 0.08, hz - c], [hx + ROTOR_R, hy + 0.08, hz + c], "rotor", **r)
    m.box([hx - ROTOR_R, hy - 0.08, hz - c], [hx - 0.8, hy + 0.08, hz + c], "rotor", **r)
    m.box([hx - c, hy - 0.08, hz + 0.8], [hx + c, hy + 0.08, hz + ROTOR_R], "rotor", **r)
    m.box([hx - c, hy - 0.08, hz - ROTOR_R], [hx + c, hy + 0.08, hz - 0.8], "rotor", **r)
    for s in (1, -1):
        m.box([hx + s * ROTOR_R - (1.2 if s > 0 else 0), hy - 0.09, hz - c - 0.01], [hx + s * ROTOR_R + (0 if s > 0 else 1.2), hy + 0.09, hz + c + 0.01], "yellow", **r)
        z0 = hz + s * ROTOR_R
        m.box([hx - c - 0.01, hy - 0.09, min(z0, z0 - s * 1.2)], [hx + c + 0.01, hy + 0.09, max(z0, z0 - s * 1.2)], "yellow", **r)
    tx, ty, tz = HELI_TAIL_HUB
    t = dict(part="tail_rotor")
    m.cyl("x", (ty, tz), 0.3, tx - 0.15, tx + 0.35, "steel", sides=8, **t)
    m.box([tx + 0.05, ty - 2.5, tz - 0.22], [tx + 0.2, ty + 2.5, tz + 0.22], "rotor", **t)
    m.box([tx + 0.05, ty - 0.22, tz - 2.5], [tx + 0.2, ty + 0.22, tz + 2.5], "rotor", **t)
    return m


# ------------------------------------------------------------------------------------- projectiles
def projectiles():
    """tank shell (120 mm, flying along -Z) and helicopter rocket, both centred on 8,8,8"""
    m = Model("vehicle_ammo", density=12.0, atlas_w=256)
    s = dict(part="shell")
    m.cyl("z", (8, 8), 0.5, 6.4, 9.6, "brass", sides=8, **s)
    m.cyl("z", (8, 8), 0.36, 5.4, 6.4, "steel", sides=8, **s)
    m.cyl("z", (8, 8), 0.2, 4.8, 5.4, "steel", sides=8, **s)
    m.box([7.9, 7.9, 9.6], [8.1, 8.1, 9.7], "glow_orange", light=True, **s)
    r = dict(part="rocket")
    m.cyl("z", (8, 8), 0.3, 5.6, 10.2, "white", sides=8, **r)
    m.cyl("z", (8, 8), 0.22, 4.6, 5.6, "od_paint", sides=8, **r)
    m.box([7.95, 7.95, 4.4], [8.05, 8.05, 4.6], "od_paint", **r)
    for d in ((0.55, 0), (-0.55, 0), (0, 0.55), (0, -0.55)):
        m.cbox([8 + d[0] * 0.6, 8 + d[1] * 0.6, 9.8], [0.06 if d[0] == 0 else 0.5, 0.06 if d[1] == 0 else 0.5, 0.8], "grey_metal", **r)
    m.box([7.8, 7.8, 10.2], [8.2, 8.2, 10.3], "black", **r)
    return m
