"""Wheeled fighting vehicles (1 unit = 0.25 block, front -Z, +X right, ground y=0, (8,0,8) = entity origin):
BTR-82A armoured personnel carrier and the Pantsir-S1 air defence system on its KAMAZ 8x8 truck, plus the TOW
launcher for the Humvee turret (jeep units, 0.1 block). Every point the game needs is stored in GEO."""
import math

from modelkit import Model
from armor2 import seg, smoke_launchers, antenna, marks

P = "paint_white"
F_ = "dark_paint"
G_ = "glass_clear"
GEO = {}


def wheel():
    """8x8 off-road wheel, centred on 8,8,8 with the axle along X (radius 2.4, scaled up for the truck)"""
    m = Model("car_wheel", density=8.0, atlas_w=256)
    m.cyl("x", (8, 8), 2.4, 7.2, 8.8, "tire", sides=16)
    m.cyl("x", (8, 8), 2.15, 7.05, 8.95, "tire", sides=16)
    for i in range(12):
        a = i * 30.0
        # tread lugs as short bars across the tyre
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        y, z = 8 + sa * 2.42, 8 + ca * 2.42
        m.box([7.25, y - 0.18, z - 0.18], [8.75, y + 0.18, z + 0.18], "tire")
    m.cyl("x", (8, 8), 1.35, 6.95, 9.05, "grey_metal", sides=16)
    m.cyl("x", (8, 8), 0.6, 6.7, 9.3, "steel", sides=8)
    for i in range(6):
        a = math.radians(i * 60)
        m.box([6.88, 8 + math.sin(a) * 0.95 - 0.12, 8 + math.cos(a) * 0.95 - 0.12], [6.95, 8 + math.sin(a) * 0.95 + 0.12, 8 + math.cos(a) * 0.95 + 0.12], "bright")
    return m


# ===================================================================================== BTR-82A
def btr82():
    axles = [-2.0, 3.4, 9.4, 14.8]
    g = dict(turret=(8.0, 2.6), gun=(9.0, 0.6), muzzles=[(7.4, 9.0, -12.8)], tubes=[], coax=(9.1, 9.2, -1.4), radar=None,
             wheels=[(x, 2.4, z) for z in axles for x in (2.6, 13.4)], wheelR=2.4, steerAxles=2,
             seats=[(6.4, 8.6, 4.4), (5.0, 8.2, 11.2), (11.0, 8.2, 11.2), (8.0, 8.2, 15.0)],
             front=[(3.0, 5.0, -8.6), (13.0, 5.0, -8.6), (8.0, 5.0, -9.2)], back=[(3.0, 5.0, 21.4), (13.0, 5.0, 21.4)],
             exhaust=[(13.4, 6.0, 18.8)], smoke=(8.0, 8.4, 16.0), size=(2.9, 2.6), gunMin=-4.0, gunMax=70.0)
    GEO["btr82"] = g
    m = Model("btr82", density=6.0, atlas_w=1024)
    b = m.box
    # boat hull: narrow belly, wide fender line, sides leaning in to the roof
    b([3.6, 1.9, -6.4], [12.4, 5.0, 20.6], P, deco=("grime",))
    b([2.2, 5.0, -5.6], [13.8, 6.4, 21.0], P, deco=("panel", "camo", "grime"))
    b([2.2, 6.3, -4.4], [2.7, 8.0, 20.6], P, rot=("z", -22.5, [2.45, 6.3, 8.0]), deco=("panel", "camo"))
    b([13.3, 6.3, -4.4], [13.8, 8.0, 20.6], P, rot=("z", 22.5, [13.55, 6.3, 8.0]), deco=("panel", "camo"))
    b([3.0, 6.3, -4.4], [13.0, 8.0, 20.6], P, deco=("camo",))
    b([3.2, 7.95, -3.6], [12.8, 8.25, 20.4], P, deco=("panel",))
    # sharp nose: upper glacis with the folded trim vane, lower glacis under the bow
    seg(m, 3.0, 13.0, (8.0, -4.4), (5.6, -9.0), 0.6, P, deco=("camo",))
    seg(m, 3.6, 12.4, (5.6, -9.0), (2.2, -6.6), 0.6, P, deco=("grime",))
    seg(m, 3.4, 12.6, (7.2, -5.6), (6.0, -8.0), 0.3, F_, deco=("panel",))
    for x in (3.4, 11.4):
        b([x, 5.4, -9.0], [x + 1.2, 6.0, -8.7], "glow_white", light=True)
        b([x + 0.1, 6.0, -8.95], [x + 1.1, 6.2, -8.6], F_)
    # driver and commander vision blocks, hatches
    for x in (4.6, 5.6, 9.8, 10.8):
        b([x, 7.6, -4.6], [x + 0.8, 8.0, -4.3], "lens")
    b([4.4, 8.2, -3.4], [6.6, 8.45, -1.4], P, deco=("rivets",))
    b([9.4, 8.2, -3.4], [11.6, 8.45, -1.4], P, deco=("rivets",))
    m.cyl("y", g["turret"], 2.9, 8.2, 8.5, F_, sides=16)
    # wheel arches over each wheel, side doors between the 2nd and 3rd axle, firing ports
    for z in axles:
        for x0, x1 in ((1.9, 3.4), (12.6, 14.1)):
            b([x0, 5.0, z - 2.7], [x1, 5.35, z + 2.7], F_)
    for x0 in (2.15, 13.8):
        b([x0, 5.1, 5.6], [x0 + 0.05, 6.3, 7.6], F_, deco=("panel",))
        for z in (-0.6, 11.8, 17.6):
            b([x0, 5.6, z], [x0 + 0.05, 6.0, z + 0.4], "black")
    # engine deck: grilles, exhaust on the right, water jet cover at the stern
    for i in range(5):
        z = 13.6 + i * 1.3
        b([4.2, 8.2, z], [11.8, 8.4, z + 0.8], F_, deco=("vslots",))
    b([12.8, 5.6, 17.6], [14.2, 6.6, 20.0], F_, deco=("vslots",))
    b([6.0, 2.6, 20.6], [10.0, 4.6, 21.4], F_, deco=("panel",))
    for x in (3.4, 11.8):
        b([x, 6.0, 21.0], [x + 0.8, 6.5, 21.2], "glow_red", light=True)
    # tow hooks, spare parts boxes, unditching log on the side
    for x in (4.4, 11.0):
        b([x, 3.0, -7.2], [x + 0.6, 3.6, -6.6], "steel")
    b([13.6, 6.1, 1.0], [14.1, 7.0, 4.6], "od_paint", deco=("panel",))
    m.cyl("z", (2.0, 6.6), 0.45, 9.0, 16.0, "wood", sides=8)
    antenna(m, 4.2, 19.6, 8.2, 7.0, part="body")
    marks(m, (2.1, 13.85), (5.2, 6.3), (12.0, 15.6))
    # BPPU turret: low cast turret with the sight on the left and smoke launchers
    t = dict(part="turret")
    tx, tz = g["turret"]
    m.cyl("y", (tx, tz), 2.7, 8.4, 9.0, P, sides=16, **t)
    b([5.2, 8.9, 0.2], [10.8, 10.4, 5.2], P, deco=("panel", "camo"), **t)
    seg(m, 5.2, 10.8, (10.4, 0.2), (9.2, -0.8), 0.4, P, part="turret")
    b([5.4, 10.4, 1.2], [10.6, 10.6, 5.0], P, **t)
    b([4.2, 9.6, 0.6], [5.4, 11.0, 2.8], F_, deco=("rivets",), **t)
    b([4.35, 10.0, 0.52], [5.25, 10.7, 0.6], "lens", **t)
    sx, sy, sz = g["seats"][0]
    m.cyl("y", (sx, sz), 0.9, 10.4, 10.9, P, sides=8, **t)
    b([sx - 0.8, 10.9, sz + 0.8], [sx + 0.8, 12.3, sz + 1.0], P, deco=("rivets",), **t)
    smoke_launchers(m, 4.2, 9.4, 2.8, 3)
    smoke_launchers(m, 11.0, 9.4, 2.8, 3)
    antenna(m, 10.0, 4.6, 10.6, 5.0)
    # 2A72 30 mm cannon with the long thin barrel, PKTM coaxial machine gun on the right
    k = dict(part="gun")
    gy, gz = g["gun"]
    b([6.6, gy - 0.6, -0.6], [9.8, gy + 0.7, 1.4], P, deco=("rivets",), **k)
    m.cyl("z", (7.4, gy), 0.32, -2.6, -0.6, P, sides=8, **k)
    m.cyl("z", (7.4, gy), 0.16, -12.8, -2.6, "blued", sides=8, **k)
    m.cyl("z", (7.4, gy), 0.24, -12.9, -12.2, "gunmetal", sides=8, **k)
    m.box([7.3, gy - 0.1, -12.95], [7.5, gy + 0.1, -12.9], "black", **k)
    cx, cy, cz = g["coax"]
    m.cyl("z", (cx, cy), 0.12, cz, -0.6, "blued", sides=6, **k)
    b([cx - 0.15, cy - 0.15, cz - 0.05], [cx + 0.15, cy + 0.15, cz], "black", **k)
    return m


# ===================================================================================== Pantsir-S1
def pantsir():
    axles = [-9.6, -3.8, 13.8, 19.6]
    g = dict(turret=(8.0, 18.0), gun=(10.4, 18.0),
             muzzles=[(2.6, 8.6, 9.4), (3.4, 8.6, 9.4), (12.6, 8.6, 9.4), (13.4, 8.6, 9.4)],
             tubes=[(x, y, 12.0) for y in (10.0, 11.2) for x in (2.2, 3.0, 3.8, 12.2, 13.0, 13.8)],
             coax=(8.0, 8.6, 9.4), radar=(8.0, 12.4, 21.6),
             wheels=[(x, 2.6, z) for z in axles for x in (3.8, 12.2)], wheelR=2.6, steerAxles=2,
             seats=[(6.0, 10.6, 16.4), (5.6, 6.8, -11.6), (10.4, 6.8, -11.6)],
             front=[(3.5, 5.0, -14.8), (12.5, 5.0, -14.8), (8.0, 6.0, -15.2)], back=[(3.5, 5.0, 30.6), (12.5, 5.0, 30.6)],
             exhaust=[(12.8, 13.2, -6.4)], smoke=(8.0, 10.0, 6.0), size=(2.9, 3.4), gunMin=-5.0, gunMax=82.0)
    GEO["pantsir"] = g
    m = Model("pantsir", density=5.0, atlas_w=1024)
    b = m.box
    # chassis frame, platform and mudguards
    b([5.0, 3.4, -14.0], [11.0, 4.6, 30.6], F_)
    b([3.0, 4.6, 1.6], [13.0, 6.2, 30.8], P, deco=("panel", "grime"))
    for z in axles:
        for x0, x1 in ((2.8, 4.8), (11.2, 13.2)):
            b([x0, 5.3, z - 2.9], [x1, 5.55, z + 2.9], F_)
    # KAMAZ cab over the engine: windscreen, grille, bumper, doors with windows, mirrors
    b([3.0, 4.6, -15.0], [13.0, 8.4, -7.4], P, deco=("panel", "camo", "grime"))
    b([3.0, 8.4, -14.4], [13.0, 12.8, -7.4], P, deco=("camo",))
    b([3.2, 12.8, -14.2], [12.8, 13.1, -7.6], P, deco=("rivets",))
    b([3.4, 8.6, -15.05], [12.6, 12.2, -14.4], G_)
    b([7.85, 8.6, -15.1], [8.15, 12.2, -14.95], F_)
    b([4.0, 5.6, -15.1], [12.0, 8.0, -15.0], F_, deco=("vslots",))
    b([2.8, 3.6, -15.6], [13.2, 4.8, -14.8], F_, deco=("panel",))
    for x in (3.3, 11.5):
        b([x, 4.9, -15.15], [x + 1.2, 5.5, -15.0], "glow_white", light=True)
    for x0 in (2.95, 13.0):
        b([x0, 9.0, -13.6], [x0 + 0.05, 12.0, -10.4], G_)
        b([x0, 5.0, -13.8], [x0 + 0.05, 8.6, -10.2], P, deco=("panel",))
    for x0, x1, a0, a1 in ((1.8, 2.2, 2.2, 3.0), (13.8, 14.2, 13.0, 13.8)):
        b([x0, 9.4, -14.4], [x1, 11.4, -14.0], F_)
        b([a0, 10.2, -14.3], [a1, 10.4, -14.1], "steel")
    b([7.0, 13.1, -12.4], [9.0, 13.5, -11.6], "glow_orange", light=True)
    # power unit and equipment housing behind the cab, exhaust stack, fuel tanks
    b([3.4, 4.6, -7.4], [12.6, 10.6, 1.6], P, deco=("panel", "camo"))
    b([3.4, 7.0, -7.4], [3.45, 10.0, 1.0], F_, deco=("vslots",))
    b([12.55, 7.0, -7.4], [12.6, 10.0, 1.0], F_, deco=("vslots",))
    m.cyl("y", (12.8, -6.4), 0.35, 10.6, 13.2, "steel", sides=8)
    for x0, x1 in ((2.6, 4.8), (11.2, 13.4)):
        m.cyl("z", ((x0 + x1) / 2, 3.8), 1.0, 0.6, 9.8, "grey_paint", sides=8)
    # rear outriggers (jacks), tail lights
    for x in (2.4, 12.8):
        for z in (6.0, 29.4):
            b([x, 1.0, z], [x + 0.8, 4.8, z + 0.8], "steel")
            b([x - 0.3, 0.0, z - 0.3], [x + 1.1, 1.0, z + 1.1], F_)
    for x in (3.4, 11.8):
        b([x, 4.8, 30.8], [x + 0.8, 5.4, 31.0], "glow_red", light=True)
    b([3.2, 6.2, 24.4], [12.8, 7.6, 30.4], P, deco=("panel",))
    marks(m, (2.9, 13.05), (5.4, 8.2), (-13.4, -10.6))
    # combat module: turntable, crew cabin, tracking radar on the front, electro-optics below it
    t = dict(part="turret")
    tx, tz = g["turret"]
    m.cyl("y", (tx, tz), 4.4, 6.2, 6.8, F_, sides=16, **t)
    b([4.6, 6.8, 13.6], [11.4, 12.4, 23.6], P, deco=("panel", "camo"), **t)
    seg(m, 4.6, 11.4, (12.4, 13.6), (9.2, 12.4), 0.5, P, part="turret")
    m.cyl("z", (8.0, 11.0), 2.5, 12.6, 13.2, P, sides=16, **t)
    m.cyl("z", (8.0, 11.0), 2.2, 12.5, 12.6, F_, sides=16, **t)
    for i in range(5):
        y = 9.6 + i * 0.6
        b([6.0, y, 12.45], [10.0, y + 0.08, 12.5], "steel", **t)
    b([6.8, 7.2, 12.2], [9.2, 8.6, 13.6], F_, **t)
    b([7.0, 7.5, 12.15], [7.8, 8.3, 12.2], "lens", **t)
    b([8.2, 7.5, 12.15], [9.0, 8.3, 12.2], "lens_red", **t)
    sx, sy, sz = g["seats"][0]
    m.cyl("y", (sx, sz), 0.9, 12.4, 12.8, P, sides=8, **t)
    b([sx - 0.8, 12.8, sz + 0.8], [sx + 0.8, 14.0, sz + 1.0], P, deco=("rivets",), **t)
    b([4.4, 7.6, 15.0], [4.6, 11.6, 21.6], F_, **t)
    b([11.4, 7.6, 15.0], [11.6, 11.6, 21.6], F_, **t)
    rx, ry, rz = g["radar"]
    m.cyl("y", (rx, rz), 0.7, 12.4, 13.0, "steel", sides=8, **t)
    # search radar: a planar array spinning on its mast (own part, centred on 8,8,8)
    r = dict(part="radar")
    m.cyl("y", (8, 8), 0.45, 8.0, 9.2, "steel", sides=8, **r)
    b([4.2, 9.0, 7.6], [11.8, 12.4, 8.0], P, rot=("x", -22.5, [8, 10.7, 7.8]), deco=("panel",), **r)
    b([4.4, 9.2, 7.5], [11.6, 12.2, 7.6], F_, rot=("x", -22.5, [8, 10.7, 7.8]), deco=("vlines:10",), **r)
    b([7.6, 8.8, 7.8], [8.4, 9.6, 8.8], "steel", **r)
    # weapon blocks either side: six missile containers over a 2A38M twin 30 mm gun, elevating together
    k = dict(part="gun")
    gy, gz = g["gun"]
    for x0, x1, side in ((1.6, 4.4, -1), (11.6, 14.4, 1)):
        b([x0 + 0.4, gy - 0.8, gz - 1.2], [x1 - 0.4, gy + 0.8, gz + 1.2], F_, deco=("rivets",), **k)
        for (tx_, ty_, tz_) in g["tubes"]:
            if not (x0 <= tx_ <= x1):
                continue
            m.cyl("z", (tx_, ty_), 0.42, tz_, tz_ + 11.0, "od_paint", sides=8, **k)
            m.box([tx_ - 0.3, ty_ - 0.3, tz_ - 0.06], [tx_ + 0.3, ty_ + 0.3, tz_], "white", **k)
        b([x0, 9.4, 12.6], [x1, 9.6, 23.4], F_, **k)
        b([x0 + 0.4, 7.8, 13.0], [x1 - 0.4, 9.4, 19.0], P, deco=("panel",), **k)
        for (mx, my, mz) in g["muzzles"]:
            if not (x0 <= mx <= x1):
                continue
            m.cyl("z", (mx, my), 0.24, mz + 1.2, 13.0, "blued", sides=8, **k)
            m.cyl("z", (mx, my), 0.32, mz, mz + 1.2, "gunmetal", sides=8, **k)
            m.box([mx - 0.12, my - 0.12, mz - 0.05], [mx + 0.12, my + 0.12, mz], "black", **k)
    # trunnion bars into the cabin sides
    b([4.4, gy - 0.3, gz - 0.3], [4.6, gy + 0.3, gz + 0.3], "steel", **k)
    b([11.4, gy - 0.3, gz - 0.3], [11.6, gy + 0.3, gz + 0.3], "steel", **k)
    return m


# ===================================================================================== TOW launcher for the Humvee (0.1 block units)
TOW_PIVOT = (8.0, 23.6, 10.5)
TOW_MUZZLE = (8.0, 24.8, -1.0)


def tow():
    m = Model("jeep_tow", density=5.0, atlas_w=256)
    gx, gy, gz = TOW_PIVOT
    k = dict(part="launcher")
    # traversing unit on the pintle, launch tube above it, day/night sight on the left
    m.box([6.8, gy - 1.2, gz - 1.6], [9.2, gy + 0.2, gz + 1.6], "od_paint", deco=("rivets",), **k)
    ty = TOW_MUZZLE[1]
    m.cyl("z", (gx, ty), 1.15, gz - 11.5, gz + 5.0, "od_paint", sides=8, **k)
    m.cyl("z", (gx, ty), 1.3, gz - 11.5, gz - 10.6, F_, sides=8, **k)
    m.cyl("z", (gx, ty), 1.3, gz + 4.2, gz + 5.0, F_, sides=8, **k)
    m.box([gx - 0.85, ty - 0.85, gz - 11.56], [gx + 0.85, ty + 0.85, gz - 11.5], "black", **k)
    m.box([gx - 0.25, ty - 0.25, gz - 11.4], [gx + 0.25, ty + 0.25, gz - 11.3], "lens", **k)
    for z in (gz - 6.0, gz + 1.0):
        m.box([gx - 1.25, ty - 1.25, z], [gx + 1.25, ty - 1.0, z + 0.6], "steel", **k)
    m.box([3.8, gy - 1.6, gz - 3.4], [6.6, gy + 1.0, gz + 2.6], "od_paint", deco=("panel",), **k)
    m.box([4.0, gy - 1.2, gz - 3.46], [5.0, gy - 0.2, gz - 3.4], "lens", **k)
    m.box([5.3, gy - 1.2, gz - 3.46], [6.3, gy - 0.2, gz - 3.4], "lens_red", **k)
    m.box([4.6, gy - 0.6, gz + 2.6], [5.8, gy + 0.6, gz + 3.6], "rubber", **k)
    # firing handles at the back
    for x in (6.4, 9.1):
        m.box([x, gy - 0.9, gz + 1.6], [x + 0.5, gy + 0.5, gz + 2.6], "rubber", **k)
    m.box([6.4, gy - 0.3, gz + 1.8], [9.6, gy + 0.1, gz + 2.2], "steel", **k)
    return m


CARS = {"btr82": btr82, "pantsir": pantsir}
