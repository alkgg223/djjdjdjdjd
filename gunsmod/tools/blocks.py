"""Block models (front faces north = -Z) and block-entity parts."""
import math

from modelkit import Model
from props import missile_geom

BLOCKS = {}


def blk(name, facing=True):
    def deco(fn):
        BLOCKS[name] = (fn, facing)
        return fn
    return deco


def legs(m, h, mat="grey_metal", inset=1.0, t=1.4):
    for x in (inset, 16 - inset - t):
        for z in (inset, 16 - inset - t):
            m.box([x, 0, z], [x + t, h, z + t], mat)
            m.box([x - 0.2, 0, z - 0.2], [x + t + 0.2, 0.4, z + t + 0.2], "rubber")


@blk("weapon_workbench")
def weapon_workbench():
    m = Model("weapon_workbench", density=6)
    legs(m, 12.0)
    m.box([1.4, 3.0, 1.4], [14.6, 3.6, 14.6], "grey_metal")                      # lower shelf
    # drawer cabinet (right)
    m.box([9.0, 3.6, 1.2], [15.0, 12.0, 15.0], "od_paint")
    for i, y in enumerate((3.9, 6.6, 9.3)):
        m.box([9.3, y, 0.9], [14.7, y + 2.4, 1.2], "od_paint", deco=("panel",))
        m.box([11.2, y + 1.6, 0.6], [12.8, y + 1.9, 0.9], "steel")
    # table top: wood with steel edge
    m.box([0.0, 12.0, 0.0], [16.0, 13.4, 16.0], "wood", deco=("lines:1",))
    m.box([-0.1, 12.0, -0.1], [16.1, 12.6, 16.1], "steel")
    # vice
    m.box([1.0, 13.4, 1.0], [4.4, 14.4, 4.0], "blued")
    m.box([1.4, 14.4, 1.2], [4.0, 16.0, 1.9], "blued")
    m.box([1.4, 14.4, 3.0], [4.0, 16.0, 3.7], "blued")
    m.box([2.4, 14.6, -0.6], [3.0, 15.2, 1.2], "bright")
    m.box([1.6, 14.7, -0.8], [3.8, 14.9, -0.6], "bright")
    # rifle lying on the bench
    m.box([4.2, 13.4, 8.8], [13.0, 14.6, 10.0], "gunmetal", deco=("port",))
    m.box([13.0, 13.7, 9.15], [16.6, 14.2, 9.65], "blued")
    m.box([0.6, 13.4, 8.9], [4.2, 14.4, 9.9], "wood")
    m.box([8.2, 13.4, 10.0], [9.6, 13.7, 12.4], "steel")                         # magazine lying
    m.box([5.6, 13.4, 11.0], [7.0, 13.6, 13.6], "steel")
    # tool board at the back
    m.box([0.5, 13.4, 14.6], [15.5, 22.0, 15.6], "grey_paint", deco=("dirty",))
    for x in range(2, 15, 2):
        for y in (15.0, 17.0, 19.0, 21.0):
            m.box([x - 0.15, y - 0.15, 14.5], [x + 0.15, y + 0.15, 14.6], "black")
    m.box([2.0, 16.5, 14.1], [2.6, 20.6, 14.5], "steel")                        # wrench
    m.box([1.6, 20.2, 14.1], [3.0, 21.0, 14.5], "steel")
    m.box([4.4, 15.2, 14.1], [4.9, 20.0, 14.5], "wood")                         # hammer
    m.box([3.7, 19.2, 14.0], [5.6, 20.4, 14.5], "steel")
    m.box([7.0, 16.0, 14.0], [12.0, 19.0, 14.5], "blued")                       # pistol on wall
    m.box([7.4, 14.6, 14.0], [8.6, 16.2, 14.5], "polymer")
    m.box([13.2, 15.2, 14.1], [13.8, 21.0, 14.5], "red")                         # screwdriver
    m.box([0.5, 21.8, 14.0], [15.5, 22.4, 15.8], "steel")
    return m


@blk("modification_table")
def modification_table():
    m = Model("modification_table", density=6)
    legs(m, 11.0, mat="dark_paint")
    m.box([0.0, 11.0, 0.0], [16.0, 12.4, 16.0], "dark_paint", deco=("panel",))
    m.box([0.6, 12.4, 0.6], [15.4, 12.6, 15.4], "rubber", deco=("hlines:5", "vlines:5"))  # cutting mat
    # gun cradle with a carbine
    for x in (3.0, 11.0):
        m.box([x, 12.6, 5.4], [x + 1.6, 14.2, 7.6], "grey_metal")
    m.box([1.6, 14.2, 5.9], [12.6, 15.6, 7.1], "gunmetal", deco=("port",))
    m.box([12.6, 14.6, 6.25], [16.4, 15.1, 6.75], "blued")
    m.box([1.6, 15.6, 6.05], [9.6, 16.0, 6.95], "blued", deco=("rail",))
    m.box([6.0, 12.8, 6.0], [7.4, 14.2, 7.0], "steel")
    # parts trays
    for i, x in enumerate((1.0, 5.6, 10.2)):
        m.box([x, 12.6, 10.0], [x + 4.4, 13.4, 14.6], "grey_metal")
        m.box([x + 0.3, 13.1, 10.3], [x + 4.1, 13.3, 14.3], ("brass", "steel", "red")[i])
    # magnifier lamp on an arm
    m.box([13.2, 12.6, 13.0], [15.2, 13.2, 15.0], "dark_paint")
    m.box([13.9, 13.2, 13.7], [14.5, 19.0, 14.3], "steel")
    m.box([9.0, 18.6, 13.7], [14.5, 19.2, 14.3], "steel", rot=("z", -22.5, [14.2, 18.9, 14]))
    m.cyl("y", (8.6, 13.0), 2.2, 16.2, 17.0, "dark_paint", sides=16)
    m.cyl("y", (8.6, 13.0), 1.9, 16.0, 16.25, "lens", sides=16)
    # schematic screen
    m.box([1.0, 12.6, 15.2], [7.0, 17.0, 15.8], "dark_paint")
    m.box([1.4, 13.0, 15.1], [6.6, 16.6, 15.2], "screen", deco=("text:MOD",), light=True)
    return m


@blk("ammo_crate")
def ammo_crate():
    m = Model("ammo_crate", density=6)
    m.box([1.0, 0.0, 2.0], [15.0, 10.0, 14.0], "wood_light", deco=("hlines:3", "stencil:AMMO"))
    m.box([0.8, 10.0, 1.8], [15.2, 11.2, 14.2], "wood_light", deco=("vlines:3",))
    for x in (0.7, 14.3):
        for z in (1.7, 13.3):
            m.box([x, 0.0, z], [x + 1.0, 11.4, z + 1.0], "steel")
    for z in (1.7, 13.3):
        m.box([0.7, 4.6, z], [15.3, 5.6, z + 1.0], "steel")
    # rope handles
    for x in (0.4, 15.0):
        m.box([x, 6.0, 6.0], [x + 0.6, 6.6, 10.0], "canvas")
        m.box([x, 4.6, 6.0], [x + 0.6, 6.6, 6.6], "canvas")
        m.box([x, 4.6, 9.4], [x + 0.6, 6.6, 10.0], "canvas")
    # ammo can + shells on the lid
    m.box([2.0, 11.2, 3.0], [8.0, 14.8, 6.6], "od_paint", deco=("stencil:7.62",))
    m.box([1.9, 14.8, 2.9], [8.1, 15.2, 6.7], "od_paint")
    m.box([4.4, 15.2, 4.6], [5.6, 15.8, 5.0], "steel")
    for i in range(5):
        x = 9.0 + i * 1.1
        m.box([x, 11.2, 8.0], [x + 0.7, 13.6, 8.7], "brass")
        m.box([x + 0.1, 13.6, 8.1], [x + 0.6, 14.2, 8.6], "copper")
    return m


@blk("missile_launcher")
def missile_launcher():
    m = Model("missile_launcher", density=5)
    m.box([0.0, 0.0, 0.0], [16.0, 2.0, 16.0], "dark_paint", deco=("rivets",))
    m.box([-0.05, 0.4, -0.05], [16.05, 1.6, 16.05], "hazard")
    m.cyl("y", (8.0, 9.0), 6.0, 2.0, 3.4, "grey_metal", sides=16)
    # hinge brackets at the back
    for x in (3.0, 11.4):
        m.box([x, 3.4, 12.2], [x + 1.6, 9.0, 15.6], "grey_metal")
    m.cyl("x", (8.0, 14.0), 0.9, 2.6, 13.4, "bright", sides=8)                    # hinge axle
    # hydraulic rams
    for x in (5.2, 9.6):
        m.box([x, 3.4, 4.0], [x + 1.2, 4.8, 12.0], "steel")
        m.box([x + 0.2, 3.6, 2.0], [x + 1.0, 4.4, 4.0], "chrome")
    # control box with lights (left side)
    m.box([0.4, 2.0, 0.6], [3.6, 7.0, 4.0], "od_paint", deco=("panel",))
    m.box([0.9, 5.0, 0.5], [1.7, 5.8, 0.6], "glow_red", light=True)
    m.box([2.2, 5.0, 0.5], [3.0, 5.8, 0.6], "glow_green", light=True)
    m.box([0.9, 2.8, 0.45], [3.0, 4.4, 0.6], "screen", light=True)
    # cable
    m.box([3.6, 2.0, 2.0], [5.0, 2.6, 2.6], "rubber")
    # rest cradle at the front
    m.box([5.0, 2.0, 0.4], [11.0, 6.0, 1.6], "grey_metal")
    m.box([6.6, 5.0, 0.2], [9.4, 6.2, 1.8], "rubber")
    return m


@blk("bot_spawner")
def bot_spawner():
    m = Model("bot_spawner", density=6)
    m.cyl("y", (8, 8), 7.8, 0.0, 1.6, "dark_paint", sides=16)
    m.cyl("y", (8, 8), 7.0, 1.6, 2.2, "grey_metal", sides=16)
    m.cyl("y", (8, 8), 5.6, 2.2, 2.5, "glow_blue", sides=16, end_mat="glow_blue")
    for e in m.elements[-8:]:
        e["light"] = True
    m.cyl("y", (8, 8), 5.0, 2.2, 2.7, "dark_paint", sides=16)
    m.cyl("y", (8, 8), 1.4, 2.7, 4.6, "steel", sides=8)
    m.cyl("y", (8, 8), 0.8, 4.6, 5.2, "glow_blue", sides=8)
    for e in m.elements[-4:]:
        e["light"] = True
    # four lamp posts
    for a in (45, 135, 225, 315):
        x = 8 + math.cos(math.radians(a)) * 6.2
        z = 8 + math.sin(math.radians(a)) * 6.2
        m.box([x - 0.5, 1.6, z - 0.5], [x + 0.5, 6.0, z + 0.5], "steel")
        m.box([x - 0.6, 6.0, z - 0.6], [x + 0.6, 6.8, z + 0.6], "glow_blue", light=True)
    return m


@blk("arena_controller")
def arena_controller():
    m = Model("arena_controller", density=6)
    # desk console
    m.box([0.5, 0.0, 3.0], [15.5, 9.0, 15.0], "od_paint", deco=("panel",))
    m.box([0.3, 0.0, 2.8], [15.7, 0.8, 15.2], "rubber")
    m.box([0.5, 8.2, 1.0], [15.5, 9.4, 9.0], "od_paint", rot=("x", -22.5, [8.0, 9.0, 9.0]))     # sloped keyboard deck
    m.box([2.0, 9.0, 3.4], [14.0, 9.3, 7.4], "rubber", rot=("x", -22.5, [8.0, 9.0, 9.0]), deco=("hlines:3", "vlines:8"))
    # screens
    m.box([1.0, 9.0, 11.0], [15.0, 17.0, 14.5], "dark_paint")
    m.box([1.6, 9.8, 10.9], [7.6, 16.2, 11.0], "screen", deco=("text:GREEN",), light=True)
    m.box([8.4, 9.8, 10.9], [14.4, 16.2, 11.0], "screen_red", deco=("text:RED",), light=True)
    m.box([1.0, 17.0, 11.0], [15.0, 17.6, 14.5], "dark_paint")
    # radio + antenna
    m.box([11.0, 17.6, 12.0], [14.6, 19.6, 14.4], "olive", deco=("rivets",))
    m.box([13.4, 19.6, 13.0], [13.8, 28.0, 13.4], "rubber")
    m.box([1.4, 17.6, 12.4], [2.4, 18.6, 13.4], "glow_red", light=True)
    m.box([3.0, 17.6, 12.4], [4.0, 18.6, 13.4], "glow_green", light=True)
    # big buttons
    for i, mat in enumerate(("glow_green", "glow_red", "glow_orange")):
        m.box([3.0 + i * 4.0, 9.3, 1.8], [4.6 + i * 4.0, 9.8, 3.0], mat, rot=("x", -22.5, [8.0, 9.0, 9.0]), light=True)
    return m


@blk("sandbags")
def sandbags():
    m = Model("sandbags", density=5)
    rows = [(0.0, 0.0), (5.4, 4.0), (10.8, 0.0)]
    for y, off in rows:
        x = -4.0 + off
        while x < 16.0:
            x0, x1 = max(0.0, x), min(16.0, x + 7.6)
            if x1 - x0 > 1.0:
                m.box([x0 + 0.2, y, 2.0], [x1 - 0.2, y + 5.2, 14.0], "sand", deco=("lines:1",))
                m.box([x0 + 0.6, y + 0.6, 1.4], [x1 - 0.6, y + 4.6, 14.6], "sand")
            x += 8.0
    return m


@blk("sandbags_low")
def sandbags_low():
    m = Model("sandbags_low", density=5)
    for y, off in ((0.0, 0.0), (4.0, 4.0)):
        x = -4.0 + off
        while x < 16.0:
            x0, x1 = max(0.0, x), min(16.0, x + 7.6)
            if x1 - x0 > 1.0:
                m.box([x0 + 0.2, y, 2.0], [x1 - 0.2, y + 4.0, 14.0], "sand", deco=("lines:1",))
                m.box([x0 + 0.6, y + 0.5, 1.4], [x1 - 0.6, y + 3.5, 14.6], "sand")
            x += 8.0
    return m


@blk("concrete_barrier")
def concrete_barrier():
    m = Model("concrete_barrier", density=5)
    # jersey barrier: wide foot, sloped faces, narrow top (profile in z)
    m.box([0.0, 0.0, 1.0], [16.0, 3.0, 15.0], "concrete")
    m.box([0.0, 3.0, 4.0], [16.0, 16.0, 12.0], "concrete", deco=("stripe:yellow",))
    m.box([0.0, 1.0, 1.6], [16.0, 7.0, 6.0], "concrete", rot=("x", 22.5, [8.0, 4.0, 3.8]))
    m.box([0.0, 1.0, 10.0], [16.0, 7.0, 14.4], "concrete", rot=("x", -22.5, [8.0, 4.0, 12.2]))
    m.box([7.0, 13.6, 3.9], [9.0, 14.4, 12.1], "rust")
    return m


@blk("barbed_wire")
def barbed_wire():
    m = Model("barbed_wire", density=8)
    cy, cz, r = 6.0, 8.0, 5.4
    sl = 2 * r * math.tan(math.pi / 8) + 0.3
    for i in range(5):
        x = 1.6 + i * 3.2 + (0.6 if i % 2 else 0.0)
        for k in range(8):
            th = math.radians(k * 45)
            y = cy + math.sin(th) * r
            z = cz + math.cos(th) * r
            if k in (0, 4):
                m.box([x - 0.12, y - sl / 2, z - 0.12], [x + 0.12, y + sl / 2, z + 0.12], "steel")
            elif k in (2, 6):
                m.box([x - 0.12, y - 0.12, z - sl / 2], [x + 0.12, y + 0.12, z + sl / 2], "steel")
            else:
                phi = -45.0 if k in (1, 5) else 45.0
                m.box([x - 0.12, y - sl / 2, z - 0.12], [x + 0.12, y + sl / 2, z + 0.12], "steel", rot=("x", phi, [x, y, z]))
        m.box([x - 0.6, cy + r - 0.2, cz - 0.15], [x + 0.6, cy + r + 0.2, cz + 0.15], "steel")   # barbs
        m.box([x - 0.15, cy - 0.2, cz + r - 0.6], [x + 0.15, cy + 0.2, cz + r + 0.6], "steel")
    for z in (3.0, 13.0):
        m.box([0.0, 0.9, z - 0.1], [16.0, 1.1, z + 0.1], "steel")
    for x in (0.4, 15.0):
        m.box([x, 0.0, 7.6], [x + 0.6, 12.0, 8.4], "rust")
    return m


@blk("explosive_barrel")
def explosive_barrel():
    m = Model("explosive_barrel", density=5)
    m.cyl("y", (8, 8), 6.4, 0.0, 15.0, "red", sides=16, deco=("stencil:TNT",))
    for y in (2.4, 7.2, 12.0):
        m.cyl("y", (8, 8), 6.6, y, y + 0.6, "red", sides=16)
    m.cyl("y", (8, 8), 6.5, 6.2, 8.8, "hazard", sides=16)
    m.cyl("y", (8, 8), 6.2, 15.0, 15.4, "red", sides=16)
    m.cyl("y", (11.0, 5.0), 0.9, 15.4, 16.0, "steel", sides=8)
    return m


# ------------------------------------------------------------------------------- BER parts
def launcher_erector():
    """vertical launch rail along +Y, hinge at (8,0,8)"""
    m = Model("launcher_erector", density=4)
    for x in (5.6, 9.4):
        m.box([x, 0.0, 9.4], [x + 1.0, 30.0, 10.6], "grey_metal", deco=("rivets",))
    for y in (2.0, 9.0, 16.0, 23.0, 29.0):
        m.box([5.6, y, 9.4], [10.4, y + 1.0, 10.6], "grey_metal")
    m.box([6.6, 0.0, 10.6], [9.4, 30.0, 11.2], "dark_paint", deco=("hazard",))
    # clamps
    for y in (6.0, 20.0):
        m.box([5.0, y, 6.0], [6.0, y + 1.6, 10.0], "steel")
        m.box([10.0, y, 6.0], [11.0, y + 1.6, 10.0], "steel")
    m.cyl("x", (0.0, 10.0), 1.0, 4.6, 11.4, "bright", sides=8)
    return m


def missile_model(kind):
    m = Model("missile_" + kind, density=4)
    mats = {"he": ("white", "dark_paint", "yellow"), "cluster": ("od_paint", "dark_paint", "white"),
            "incendiary": ("grey_paint", "red", "orange")}[kind]
    missile_geom(m, *mats)
    return m
