"""Military jeep (1 unit = 0.1 block). Front at -Z, +X = right side, ground y=0.
Parts: body, wheel (centred 8,8,8 axis X), turret (yaw about (8,*,13)), turret_gun (pitch about (8,23.5,13.5)),
mark_green, mark_red. Plus soldier gear models."""
import math

from modelkit import Model

P = "paint_white"     # tinted with the team colour at runtime
WHEELS = [(-1.0, 4.3, -8.0), (17.0, 4.3, -8.0), (-1.0, 4.3, 21.0), (17.0, 4.3, 21.0)]
SEATS = {"driver": (2.6, 5.6, 4.6), "passenger": (13.4, 5.6, 4.6), "gunner": (8.0, 16.6, 17.3), "rear": (13.4, 5.6, 12.6)}
TURRET_PIVOT = (8.0, 19.3, 13.0)
GUN_PIVOT = (8.0, 23.6, 10.5)   # gun mount sits 2.5 units ahead of the ring centre


def jeep():
    m = Model("jeep", density=3.0, atlas_w=512)
    b = m.box
    # ---------------------------------------------------------------- chassis + lower body
    b([1.0, 2.0, -13.0], [15.0, 3.2, 29.0], "dark_paint", part="body")                   # frame rails
    b([1.5, 3.0, -14.0], [14.5, 9.0, -3.0], P, part="body", deco=("panel",))            # engine bay
    b([-1.5, 3.0, -3.0], [17.5, 10.5, 16.6], P, part="body")                            # cabin tub
    b([1.0, 3.0, 16.6], [15.0, 9.0, 29.0], P, part="body")                              # rear tub
    # doors (armoured) left / right
    for x0, x1 in ((-2.6, -1.5), (17.5, 18.6)):
        b([x0, 4.0, -2.8], [x1, 10.6, 7.4], P, part="body", deco=("panel", "rivets"))
        b([x0, 4.0, 7.6], [x1, 10.6, 16.4], P, part="body", deco=("panel", "rivets"))
        hx = x0 - 0.3 if x0 < 0 else x1
        for z in (4.6, 13.6):
            b([hx, 8.6, z], [hx + 0.3, 9.0, z + 1.6], "dark_paint", part="body")
    # hood and fenders (full width above the wheels)
    b([-3.0, 9.0, -14.2], [19.0, 10.6, -2.8], P, part="body", deco=("panel",))
    b([-0.5, 10.6, -13.6], [16.5, 11.2, -3.6], P, part="body", deco=("vslots",))       # hood bulge
    b([-3.0, 7.6, -13.4], [-1.6, 9.0, -2.8], P, part="body")
    b([17.6, 7.6, -13.4], [19.0, 9.0, -2.8], P, part="body")
    b([-3.0, 9.0, 16.6], [19.0, 10.9, 29.4], P, part="body")                            # rear fenders/bed rim
    b([-3.0, 7.6, 16.8], [-1.6, 9.0, 25.6], P, part="body")
    b([17.6, 7.6, 16.8], [19.0, 9.0, 25.6], P, part="body")
    # grille + lights + bumper
    b([1.5, 4.0, -14.3], [14.5, 9.0, -14.0], "dark_paint", part="body", deco=("vslots",))
    for x in (-2.2, 15.6):
        b([x, 7.6, -14.45], [x + 2.6, 8.9, -14.1], "steel", part="body")
        b([x + 0.4, 7.85, -14.55], [x + 2.2, 8.65, -14.4], "glow_orange", part="body", light=True)
    b([-3.2, 3.2, -15.4], [19.2, 5.4, -14.2], "dark_paint", part="body", deco=("rivets",))
    b([5.5, 3.6, -15.9], [10.5, 5.0, -15.4], "steel", part="body")                       # winch
    for x in (0.5, 14.5):
        b([x, 3.8, -16.0], [x + 1.0, 4.8, -15.3], "steel", part="body")                  # tow hooks
    # ---------------------------------------------------------------- cab: windshield, pillars, roof
    for x0, x1 in ((-2.6, -1.6), (7.5, 8.5), (17.6, 18.6)):
        b([x0, 10.5, -1.4], [x1, 18.6, -0.4], P, part="body")                             # A pillars
    for x0, x1 in ((-2.6, -1.6), (17.6, 18.6)):
        b([x0, 10.5, 7.0], [x1, 18.6, 8.0], P, part="body")                               # B pillars
        b([x0, 10.5, 16.0], [x1, 18.6, 17.0], P, part="body")                             # C pillars
    b([-1.6, 10.8, -1.1], [7.5, 18.4, -0.7], "glass_clear", part="body")
    b([8.5, 10.8, -1.1], [17.6, 18.4, -0.7], "glass_clear", part="body")
    for x0, x1 in ((-2.4, -2.0), (18.0, 18.4)):
        b([x0, 10.9, -0.4], [x1, 17.8, 7.0], "glass_clear", part="body")
        b([x0, 10.9, 8.0], [x1, 17.8, 16.0], "glass_clear", part="body")
    # roof with a turret hatch
    b([-2.8, 18.6, -1.6], [18.8, 19.4, 8.8], P, part="body", deco=("panel",))
    b([-2.8, 18.6, 8.8], [3.6, 19.4, 17.2], P, part="body")
    b([12.4, 18.6, 8.8], [18.8, 19.4, 17.2], P, part="body")
    b([3.6, 18.6, 16.6], [12.4, 19.4, 17.2], P, part="body")
    # mirrors
    for x0, x1 in ((-4.6, -2.6), (18.6, 20.6)):
        b([x0, 12.0, -1.8], [x1, 14.0, -1.0], "dark_paint", part="body")
        b([min(x0, x1) + (0.2 if x0 < 0 else 0), 12.2, -1.85], [max(x0, x1) - (0.0 if x0 < 0 else 0.2), 13.8, -1.8], "glass_clear", part="body")
    # ---------------------------------------------------------------- interior
    b([-1.4, 10.5, -0.6], [17.4, 12.2, 1.6], "dark_paint", part="body")                 # dashboard
    b([0.6, 11.2, 1.4], [4.6, 12.6, 1.6], "screen", part="body", light=True)
    for sx in (2.6, 13.4):
        b([sx - 2.6, 3.0, 2.4], [sx + 2.6, 5.6, 7.0], "canvas", part="body")              # seat cushion
        b([sx - 2.6, 5.6, 6.2], [sx + 2.6, 12.0, 7.2], "canvas", part="body")             # backrest
        b([sx - 2.6, 3.0, 10.4], [sx + 2.6, 5.6, 15.0], "canvas", part="body")
        b([sx - 2.6, 5.6, 14.2], [sx + 2.6, 12.0, 15.2], "canvas", part="body")
    m.cyl("z", (2.6, 13.2), 1.6, 1.6, 2.0, "rubber", sides=8, part="body")                # steering wheel
    b([2.3, 11.2, 1.6], [2.9, 13.0, 2.4], "dark_paint", part="body")
    b([5.4, 3.0, -0.5], [10.6, 8.0, 7.0], "dark_paint", part="body", deco=("panel",))   # centre console
    b([6.6, 8.0, 1.0], [9.4, 9.2, 4.0], "screen_red", part="body", light=True)
    b([6.0, 3.0, 9.0], [10.0, 11.0, 13.0], "grey_metal", part="body")                   # gunner step
    # ---------------------------------------------------------------- rear bed, canvas, cans
    b([-2.8, 10.9, 17.2], [18.8, 14.4, 29.2], "tintcloth", part="body", deco=("lines:2",))
    b([-3.0, 3.4, 29.0], [19.0, 10.9, 29.8], P, part="body", deco=("panel",))            # tailgate
    for x in (-2.4, 16.8):
        b([x, 6.8, 29.7], [x + 1.6, 8.0, 30.1], "glow_red", part="body", light=True)
    for x in (2.0, 11.0):                                                                  # jerry cans
        b([x, 3.4, 29.8], [x + 3.0, 9.4, 31.6], "od_paint", part="body", deco=("panel", "stencil:F"))
    # antennas
    for x in (-2.0, 17.2):
        b([x, 10.9, 27.6], [x + 0.8, 12.0, 28.4], "dark_paint", part="body")
        b([x + 0.3, 12.0, 27.9], [x + 0.5, 31.5, 28.1], "rubber", part="body")
    # ---------------------------------------------------------------- team markings (decals)
    for x0 in (-2.7, 18.6):
        b([x0, 5.4, -1.8], [x0 + 0.06, 9.6, 6.4], "decal", part="mark_green", deco=("star",))
        b([x0, 5.4, 8.4], [x0 + 0.06, 9.6, 15.6], "decal", part="mark_green", deco=("star",))
        b([x0, 6.4, -2.6], [x0 + 0.06, 8.6, 16.2], "decal", part="mark_red", deco=("stripe:red",))
    b([3.0, 11.25, -12.4], [13.0, 11.3, -4.8], "decal", part="mark_green", deco=("star",))
    b([-2.9, 10.65, -13.8], [18.9, 10.7, -11.6], "decal", part="mark_red", deco=("stripe:red",))
    return m


def turret():
    m = Model("jeep_turret", density=3.5)
    cx, cy, cz = TURRET_PIVOT
    m.cyl("y", (cx, cz), 4.4, cy, cy + 1.2, "dark_paint", sides=16, part="turret")
    m.cyl("y", (cx, cz), 3.8, cy + 1.2, cy + 1.6, "steel", sides=16, part="turret")
    # gun shield: front plate + angled side wings
    m.box([3.0, cy + 1.2, cz - 5.2], [13.0, cy + 6.2, cz - 4.6], P, part="turret", deco=("rivets",))
    m.box([6.6, cy + 4.6, cz - 5.3], [9.4, cy + 5.6, cz - 4.5], "black", part="turret")     # gun slot
    m.box([0.6, cy + 1.2, cz - 5.0], [3.4, cy + 5.6, cz - 4.4], P, part="turret", rot=("y", 45.0, [3.0, cy + 4, cz - 4.7]))
    m.box([12.6, cy + 1.2, cz - 5.0], [15.4, cy + 5.6, cz - 4.4], P, part="turret", rot=("y", -45.0, [13.0, cy + 4, cz - 4.7]))
    m.box([5.0, cy + 6.2, cz - 5.2], [11.0, cy + 6.6, cz - 4.2], P, part="turret")
    # pintle post
    gz = GUN_PIVOT[2]
    m.box([7.4, cy + 1.2, gz - 0.6], [8.6, GUN_PIVOT[1] - 0.4, gz + 0.6], "steel", part="turret")
    return m


def turret_gun():
    m = Model("jeep_gun", density=5)
    gx, gy, gz = GUN_PIVOT
    m.box([6.9, gy - 0.6, gz - 2.0], [9.1, gy + 1.2, gz + 3.6], "gunmetal", part="gun", deco=("rivets",))
    m.box([7.1, gy + 1.2, gz - 1.6], [8.9, gy + 1.5, gz + 3.0], "gunmetal", part="gun")
    m.cyl("z", (8.0, gy + 0.3), 0.7, gz - 7.0, gz - 2.0, "gunmetal", sides=8, part="gun", deco=("vslots",))
    m.cyl("z", (8.0, gy + 0.3), 0.42, gz - 14.0, gz - 7.0, "blued", sides=8, part="gun")
    m.cyl("z", (8.0, gy + 0.3), 0.55, gz - 15.2, gz - 14.0, "blued", sides=8, part="gun")
    m.box([7.75, gy + 0.05, gz - 15.25], [8.25, gy + 0.55, gz - 15.15], "black", part="gun")
    m.box([7.7, gy + 0.7, gz - 13.2], [8.3, gy + 1.6, gz - 12.8], "gunmetal", part="gun")   # front sight
    # spade grips
    for x in (6.6, 8.9):
        m.box([x, gy - 0.6, gz + 3.6], [x + 0.5, gy + 1.0, gz + 4.6], "rubber", part="gun")
    m.box([7.6, gy - 0.1, gz + 3.6], [8.4, gy + 0.5, gz + 4.2], "steel", part="gun")
    # ammo can + belt
    m.box([3.6, gy - 1.8, gz - 1.0], [6.6, gy + 0.8, gz + 2.6], "od_paint", part="gun", deco=("stencil:50",))
    for i in range(4):
        z = gz - 0.6 + i * 0.8
        m.box([6.4, gy + 0.6, z], [7.0, gy + 1.0, z + 0.5], "brass", part="gun")
    return m


def wheel():
    m = Model("jeep_wheel", density=5)
    m.cyl("x", (8, 8), 4.3, 6.4, 9.6, "tire", sides=16)
    m.cyl("x", (8, 8), 3.9, 6.2, 9.8, "tire", sides=16)
    m.cyl("x", (8, 8), 2.5, 6.05, 9.95, "grey_metal", sides=16)
    m.cyl("x", (8, 8), 1.1, 5.7, 10.3, "steel", sides=8)
    for k in range(6):
        a = math.radians(k * 60)
        y, z = 8 + math.sin(a) * 1.7, 8 + math.cos(a) * 1.7
        m.box([5.85, y - 0.25, z - 0.25], [10.15, y + 0.25, z + 0.25], "bright")
    return m


# ------------------------------------------------------------------------------------- soldier gear
def helmet():
    """head cube occupies x[4,12] y[0,8] z[4,12]; face at -Z; +X = soldier's right"""
    m = Model("helmet", density=8)
    m.box([3.4, 4.6, 3.4], [12.6, 8.9, 12.6], P, deco=("panel",))
    m.box([3.8, 8.9, 3.8], [12.2, 9.5, 12.2], P)
    m.box([3.2, 4.2, 3.2], [12.8, 4.8, 12.8], P)                         # rim
    m.box([3.3, 2.6, 6.0], [3.5, 4.4, 10.6], P)                          # ear guards
    m.box([12.5, 2.6, 6.0], [12.7, 4.4, 10.6], P)
    m.box([3.0, 6.0, 5.0], [3.4, 6.6, 11.0], "rubber")                   # side rails
    m.box([12.6, 6.0, 5.0], [13.0, 6.6, 11.0], "rubber")
    # goggles resting on the helmet front
    m.box([4.4, 6.0, 3.0], [11.6, 7.6, 3.5], "rubber")
    m.box([5.0, 6.2, 2.9], [7.6, 7.4, 3.05], "lens")
    m.box([8.4, 6.2, 2.9], [11.0, 7.4, 3.05], "lens")
    m.box([3.3, 6.4, 3.4], [12.7, 7.0, 12.7], "rubber")                  # strap
    # NVG mount + camera
    m.box([7.0, 7.8, 2.8], [9.0, 9.0, 3.5], "dark_paint")
    m.box([10.6, 8.6, 9.0], [11.6, 9.8, 11.0], "dark_paint")
    m.box([10.9, 9.2, 8.95], [11.3, 9.6, 9.0], "glow_red", light=True)
    return m


def vest():
    """body cube occupies x[4,12] y[-12,0] z[6,10]; front at -Z"""
    m = Model("vest", density=8)
    m.box([3.5, -9.4, 5.4], [12.5, -1.4, 10.6], "tintcloth", deco=("panel",))
    m.box([4.6, -1.4, 5.6], [6.2, 0.4, 10.4], "tintcloth")             # shoulder straps
    m.box([9.8, -1.4, 5.6], [11.4, 0.4, 10.4], "tintcloth")
    # front pouches
    for i, x in enumerate((4.0, 6.2, 8.4)):
        m.box([x, -8.8, 4.4], [x + 2.0, -5.6, 5.4], "tintcloth", deco=("lines:1",))
        m.box([x + 0.4, -5.8, 4.6], [x + 1.6, -5.0, 5.3], "steel")
    m.box([10.6, -8.6, 4.6], [12.2, -6.0, 5.4], "od_paint")            # radio
    m.box([11.1, -6.0, 4.9], [11.4, -1.0, 5.1], "rubber")
    m.box([4.4, -4.2, 4.9], [7.4, -2.6, 5.4], "tintcloth")              # admin pouch
    m.box([7.8, -4.0, 5.0], [9.0, -3.0, 5.4], "glow_red", light=True)   # team light
    # backpack
    m.box([4.4, -10.4, 10.6], [11.6, -2.6, 13.4], "tintcloth", deco=("panel",))
    m.box([4.8, -9.6, 13.4], [11.2, -6.0, 14.2], "tintcloth")
    m.box([4.0, -2.8, 10.8], [12.0, -2.0, 13.0], "rubber")               # bedroll
    # belt
    m.box([3.4, -12.2, 5.6], [12.6, -11.2, 10.4], "rubber")
    m.box([7.2, -12.3, 5.4], [8.8, -11.1, 5.7], "steel")
    return m
