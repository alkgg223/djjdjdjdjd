"""Item models: attachments, ammo, grenades, missiles, tools, figurines."""
import math

from modelkit import Model
from parts import block, tube, Z, zr, grip, rail

ITEMS = {}


def item(name, kind="item"):
    def deco(fn):
        ITEMS[name] = (fn, kind)
        return fn
    return deco


# =============================================================================== ATTACHMENTS
# scopes: base bottom centre at (8,4,8); muzzle: rear centre (2,8,8) going +X;
# grips/laser: top centre (8,12,8) hanging down.
@item("red_dot", "attachment")
def red_dot():
    m = Model("red_dot", density=10)
    block(m, 6.0, 10.0, 4.0, 4.6, 1.4, "blued", deco=("rail",))
    block(m, 6.6, 9.4, 4.6, 5.1, 1.2, "blued")
    m.cyl("x", (6.2, 8), 1.05, 5.6, 10.4, "blued", sides=16)
    m.cyl("x", (6.2, 8), 1.15, 9.6, 10.6, "dark_paint", sides=16)
    m.box([10.55, 5.45, 7.25], [10.62, 6.95, 8.75], "lens_red")
    m.box([5.5, 5.45, 7.25], [5.57, 6.95, 8.75], "lens")
    m.cyl("y", (8.0, 8.9), 0.32, 6.0, 7.6, "dark_paint", sides=8)     # turret
    m.box([7.95, 6.15, 7.95], [8.05, 6.25, 8.05], "glow_red", light=True)
    return m


@item("holo_sight", "attachment")
def holo_sight():
    m = Model("holo_sight", density=10)
    block(m, 5.0, 11.0, 4.0, 5.2, 1.5, "dark_paint", deco=("rail",))
    block(m, 6.6, 11.0, 5.2, 5.8, 1.5, "dark_paint", deco=("text:HOLO",))
    # window frame
    block(m, 5.4, 6.0, 5.2, 8.2, 1.9, "dark_paint")
    block(m, 9.6, 10.2, 5.2, 8.2, 1.9, "dark_paint")
    block(m, 5.4, 10.2, 7.8, 8.4, 1.9, "dark_paint")
    for s in (-1, 1):
        m.box([5.4, 5.2, 8 + s * 0.95 - 0.12], [10.2, 8.4, 8 + s * 0.95 + 0.12], "dark_paint")
    m.box([9.65, 5.85, 7.1], [9.75, 7.8, 8.9], "lens")
    m.box([7.95, 6.85, 7.92], [8.05, 7.0, 8.08], "glow_red", light=True)
    return m


@item("acog", "attachment")
def acog():
    m = Model("acog", density=10)
    block(m, 5.0, 11.0, 4.0, 4.7, 1.3, "dark_paint", deco=("rail",))
    block(m, 6.0, 10.0, 4.7, 5.6, 1.6, "dark_paint")
    m.cyl("x", (6.6, 8), 1.0, 4.4, 11.4, "dark_paint", sides=16)
    m.cyl("x", (6.7, 8), 1.25, 10.6, 12.6, "dark_paint", sides=16)    # objective
    m.cyl("x", (6.6, 8), 0.95, 3.2, 4.6, "rubber", sides=16)           # eyepiece
    m.box([12.55, 5.75, 7.15], [12.65, 7.65, 8.85], "lens")
    m.box([3.15, 6.0, 7.4], [3.22, 7.2, 8.6], "lens")
    block(m, 6.0, 10.0, 7.5, 8.0, 0.5, "glow_red", light=True)        # fibre optic
    m.cyl("y", (8.0, 9.0), 0.4, 6.4, 7.8, "dark_paint", sides=8)
    return m


@item("sniper_scope", "attachment")
def sniper_scope():
    m = Model("sniper_scope", density=8)
    for x in (3.5, 10.5):
        block(m, x - 0.8, x + 0.8, 4.0, 5.9, 1.4, "blued")
        m.cyl("x", (6.6, 8), 0.95, x - 0.6, x + 0.6, "blued", sides=16)
    m.cyl("x", (6.6, 8), 0.75, -1.5, 16.0, "blued", sides=16)
    m.cyl("x", (6.6, 8), 1.0, 13.0, 15.0, "blued", sides=16)
    m.cyl("x", (6.6, 8), 1.3, 15.0, 19.0, "blued", sides=16)            # objective bell
    m.cyl("x", (6.6, 8), 1.0, -4.5, -1.5, "blued", sides=16)            # eyepiece
    m.cyl("x", (6.6, 8), 1.05, -5.0, -4.4, "rubber", sides=16)
    m.cyl("y", (6.8, 8.0), 0.55, 7.2, 8.5, "blued", sides=8, deco=("vlines:4",))   # elevation turret
    m.cyl("z", (6.8, 6.6), 0.55, 8.6, 9.8, "blued", sides=8, deco=("vlines:4",))   # windage
    m.box([18.95, 5.5, 6.9], [19.05, 7.7, 9.1], "lens")
    m.box([-5.05, 5.8, 7.2], [-4.95, 7.4, 8.8], "lens")
    return m


@item("suppressor", "attachment")
def suppressor():
    m = Model("suppressor", density=10)
    m.cyl("x", (8, 8), 0.9, 2.0, 11.0, "dark_paint", sides=16, deco=("text:SUPP",))
    m.cyl("x", (8, 8), 0.95, 2.0, 2.8, "blued", sides=16, deco=("vlines:6",))
    m.box([10.98, 7.65, 7.65], [11.05, 8.35, 8.35], "black")
    return m


@item("compensator", "attachment")
def compensator():
    m = Model("compensator", density=12)
    m.cyl("x", (8, 8), 0.66, 2.0, 4.6, "blued", sides=8)
    for x in (2.8, 3.5, 4.2):
        m.box([x, 8.4, 7.75], [x + 0.35, 8.7, 8.25], "black")
        m.box([x, 7.75, 8.45], [x + 0.35, 8.25, 8.72], "black")
        m.box([x, 7.75, 7.28], [x + 0.35, 8.25, 7.55], "black")
    m.box([4.58, 7.7, 7.7], [4.65, 8.3, 8.3], "black")
    return m


@item("vertical_grip", "attachment")
def vertical_grip():
    m = Model("vertical_grip", density=10)
    block(m, 6.6, 9.4, 11.4, 12.0, 1.3, "dark_paint", deco=("rail",))
    m.cyl("y", (8, 8), 0.62, 7.0, 11.4, "polymer", sides=8, deco=("grip",))
    m.cyl("y", (8, 8), 0.7, 6.6, 7.1, "polymer", sides=8)
    return m


@item("angled_grip", "attachment")
def angled_grip():
    m = Model("angled_grip", density=10)
    block(m, 5.6, 10.4, 11.3, 12.0, 1.25, "dark_paint", deco=("rail",))
    m.box([5.4, 9.8, 7.4], [10.6, 11.3, 8.6], "polymer")
    m.box([4.6, 9.2, 7.45], [7.6, 10.4, 8.55], "polymer", rot=("z", -45.0, [6.4, 10.0, 8]), deco=("grip",))
    return m


@item("laser_sight", "attachment")
def laser_sight():
    m = Model("laser_sight", density=10)
    block(m, 6.4, 9.6, 11.4, 12.0, 1.3, "dark_paint", deco=("rail",))
    block(m, 5.6, 10.6, 9.8, 11.4, 1.6, "dark_paint", deco=("text:LZR",))
    m.cyl("x", (10.3, 7.55), 0.42, 10.6, 11.2, "dark_paint", sides=8)
    m.box([11.18, 10.15, 7.4], [11.25, 10.45, 7.7], "glow_red", light=True)
    m.cyl("x", (10.3, 8.45), 0.42, 10.6, 11.0, "dark_paint", sides=8)
    m.box([10.98, 10.15, 8.3], [11.05, 10.45, 8.6], "lens")
    m.box([7.6, 10.2, 8.75], [8.4, 10.8, 9.0], "steel")          # switch
    return m


@item("extended_mag", "attachment")
def extended_mag():
    m = Model("extended_mag", density=8)
    from guns import chain
    chain(m, 8.0, 14.0, [(4.0, 0.0), (4.0, 22.5), (3.0, 22.5)], 2.6, 1.2, "tan", deco=("ribs",))
    block(m, 6.4, 9.6, 13.6, 14.4, 1.3, "steel")
    return m


# =============================================================================== AMMO
def ammo_can(m, x0, x1, h, w, mat, label, rounds=False):
    """military ammo can, front (label side) faces +Z"""
    z0, z1 = Z - w / 2, Z + w / 2
    m.box([x0, 2, z0], [x1, 2 + h, z1], mat, deco=("stencil:" + label,))
    m.box([x0 - 0.2, 2 + h, z0 - 0.2], [x1 + 0.2, 2 + h + 0.9, z1 + 0.2], mat)          # lid
    m.box([x0 + 0.6, 2 + h * 0.15, z1], [x0 + 1.4, 2 + h * 0.85, z1 + 0.15], mat)          # side ribs
    m.box([x1 - 1.4, 2 + h * 0.15, z1], [x1 - 0.6, 2 + h * 0.85, z1 + 0.15], mat)
    # handle
    cx = (x0 + x1) / 2
    m.box([cx - 2.0, 2 + h + 0.9, Z - 0.25], [cx - 1.5, 2 + h + 2.0, Z + 0.25], "steel")
    m.box([cx + 1.5, 2 + h + 0.9, Z - 0.25], [cx + 2.0, 2 + h + 2.0, Z + 0.25], "steel")
    m.box([cx - 2.0, 2 + h + 1.8, Z - 0.35], [cx + 2.0, 2 + h + 2.3, Z + 0.35], "steel")
    # latch
    m.box([x1 + 0.2, 2 + h - 1.5, Z - 0.8], [x1 + 0.7, 2 + h + 1.0, Z + 0.8], "steel")
    if rounds:
        for i in range(4):
            x = x0 + 1.0 + i * 1.2
            m.box([x, 2 + h + 0.9, Z - 1.5], [x + 0.7, 2 + h + 2.8, Z - 0.8], "brass")


@item("ammo_pistol")
def ammo_pistol():
    m = Model("ammo_pistol", density=8)
    ammo_can(m, 3.0, 13.0, 6.0, 4.0, "od_paint", "9MM")
    return m


@item("ammo_rifle")
def ammo_rifle():
    m = Model("ammo_rifle", density=7)
    ammo_can(m, 1.5, 14.5, 7.5, 5.0, "od_paint", "5.56-7.62")
    return m


@item("ammo_heavy")
def ammo_heavy():
    m = Model("ammo_heavy", density=6)
    ammo_can(m, 0.5, 15.5, 9.5, 7.0, "od_paint", "BELT 200")
    # belt hanging out
    for i in range(6):
        x = 2.0 + i * 1.1
        m.box([x, 11.5, Z + 3.5], [x + 0.6, 13.5, Z + 4.2], "brass")
        m.box([x, 13.5, Z + 3.6], [x + 0.6, 14.3, Z + 4.1], "copper")
    return m


@item("ammo_sniper")
def ammo_sniper():
    m = Model("ammo_sniper", density=7)
    ammo_can(m, 2.0, 14.0, 6.5, 4.5, "dark_paint", "CAL .50")
    return m


@item("ammo_shotgun")
def ammo_shotgun():
    m = Model("ammo_shotgun", density=8)
    # cardboard box with open lid, shells standing inside
    m.box([3.0, 2.0, 5.0], [13.0, 7.0, 11.0], "red", deco=("stencil:12 GA",))
    m.box([3.0, 7.0, 4.2], [13.0, 7.3, 5.0], "red", rot=("x", -45.0, [8.0, 7.0, 5.0]))
    for i in range(4):
        for j in range(3):
            x, z = 3.8 + i * 2.3, 5.8 + j * 1.8
            m.box([x, 6.0, z], [x + 1.4, 8.6, z + 1.4], "red")
            m.box([x - 0.05, 6.0, z - 0.05], [x + 1.45, 7.2, z + 1.45], "brass")
    return m


@item("rocket_ammo")
def rocket_ammo():
    m = Model("rocket_ammo", density=6)
    m.cyl("x", (8, 8), 0.45, -6.0, 0.0, "steel", sides=8)
    for s in (-1, 1):
        m.box([-6.0, 8 + s * 0.4 - 0.08, 6.2], [-3.0, 8 + s * 0.4 + 0.08, 9.8], "steel")
    m.cyl("x", (8, 8), 1.6, 0.0, 6.0, "od_paint", sides=16, deco=("stencil:PG-7",))
    m.cyl("x", (8, 8), 1.25, 6.0, 8.0, "od_paint", sides=16)
    m.cyl("x", (8, 8), 0.85, 8.0, 10.0, "od_paint", sides=8)
    m.cyl("x", (8, 8), 0.38, 10.0, 12.2, "steel", sides=8)
    m.cyl("x", (8, 8), 1.63, 1.0, 1.6, "black", sides=16)
    return m


@item("grenade_40mm")
def grenade_40mm():
    m = Model("grenade_40mm", density=10)
    m.cyl("y", (8, 8), 1.6, 2.0, 6.0, "brass", sides=16)
    m.cyl("y", (8, 8), 1.7, 2.0, 2.5, "brass", sides=16)
    m.cyl("y", (8, 8), 1.55, 6.0, 8.6, "olive", sides=16, deco=("stencil:HE",))
    m.cyl("y", (8, 8), 1.1, 8.6, 9.8, "olive", sides=16)
    m.cyl("y", (8, 8), 0.5, 9.8, 10.6, "yellow", sides=8)
    return m


@item("fuel_canister")
def fuel_canister():
    m = Model("fuel_canister", density=7)
    m.box([3.5, 1.5, 5.5], [12.5, 12.5, 10.5], "red", deco=("panel", "stencil:FUEL"))
    m.box([3.3, 4.0, 5.3], [12.7, 4.6, 10.7], "red")
    m.box([3.3, 9.0, 5.3], [12.7, 9.6, 10.7], "red")
    # handles on top
    for x in (4.5, 7.0, 9.5):
        m.box([x, 12.5, 7.6], [x + 0.6, 14.4, 8.4], "red")
    m.box([4.5, 14.0, 7.6], [10.1, 14.6, 8.4], "red")
    # spout
    m.cyl("y", (11.2, 6.6), 0.7, 12.5, 14.0, "steel", sides=8)
    m.box([10.4, 14.0, 5.8], [12.0, 14.6, 7.4], "steel")
    return m


# =============================================================================== GRENADES / EXPLOSIVES
@item("frag_grenade")
def frag_grenade():
    m = Model("frag_grenade", density=12)
    m.cyl("y", (8, 8), 1.85, 3.0, 7.4, "od_paint", sides=16, deco=("hlines:2",))
    m.cyl("y", (8, 8), 1.3, 2.4, 8.0, "od_paint", sides=16)
    m.cyl("y", (8, 8), 0.65, 8.0, 9.4, "steel", sides=8)
    m.box([8.4, 6.0, 7.6], [9.0, 9.6, 8.4], "steel", rot=("z", -22.5, [8.6, 9.2, 8]))   # spoon
    m.cyl("z", (7.0, 9.0), 0.55, 7.4, 8.6, "steel", sides=8, caps=False)               # pin ring
    m.box([7.25, 8.85, 7.2], [7.6, 9.15, 8.8], "steel")
    m.box([6.0, 4.6, 8.0 + 1.82], [10.0, 5.4, 8.0 + 1.9], "yellow")
    return m


@item("smoke_grenade")
def smoke_grenade():
    m = Model("smoke_grenade", density=12)
    m.cyl("y", (8, 8), 1.4, 2.0, 8.4, "grey_paint", sides=16, deco=("stencil:SMOKE",))
    m.cyl("y", (8, 8), 1.45, 6.6, 7.2, "white", sides=16)
    m.cyl("y", (8, 8), 0.6, 8.4, 9.6, "steel", sides=8)
    m.box([8.4, 5.4, 7.6], [9.0, 9.8, 8.4], "steel", rot=("z", -22.5, [8.6, 9.4, 8]))
    m.cyl("z", (7.0, 9.2), 0.55, 7.4, 8.6, "steel", sides=8, caps=False)
    return m


def missile_geom(m, body_mat, tip_mat, band_mat, part="body", scale=1.0):
    """vertical missile along +Y, centred x=z=8, from y=-14 to y=30"""
    m.cyl("y", (8, 8), 1.6, -12.0, 20.0, body_mat, sides=16, part=part, deco=("stencil:GW-1",))
    m.cyl("y", (8, 8), 1.65, 14.0, 15.0, band_mat, sides=16, part=part)
    m.cyl("y", (8, 8), 1.65, -6.0, -5.2, band_mat, sides=16, part=part)
    m.cyl("y", (8, 8), 1.45, 20.0, 23.0, tip_mat, sides=16, part=part)
    m.cyl("y", (8, 8), 1.15, 23.0, 25.5, tip_mat, sides=16, part=part)
    m.cyl("y", (8, 8), 0.8, 25.5, 27.5, tip_mat, sides=8, part=part)
    m.cyl("y", (8, 8), 0.4, 27.5, 29.0, tip_mat, sides=8, part=part)
    m.cyl("y", (8, 8), 1.2, -14.0, -12.0, "steel", sides=16, part=part)
    m.box([7.3, -14.05, 7.3], [8.7, -13.95, 8.7], "black", part=part)
    # tail fins (x and z)
    m.box([4.0, -13.0, 7.85], [12.0, -7.0, 8.15], body_mat, part=part)
    m.box([7.85, -13.0, 4.0], [8.15, -7.0, 12.0], body_mat, part=part)
    # canards
    m.box([5.6, 15.5, 7.9], [10.4, 17.5, 8.1], body_mat, part=part)
    m.box([7.9, 15.5, 5.6], [8.1, 17.5, 10.4], body_mat, part=part)


@item("missile_he", "missile")
def missile_he():
    m = Model("missile_he", density=4)
    missile_geom(m, "white", "dark_paint", "yellow")
    return m


@item("missile_cluster", "missile")
def missile_cluster():
    m = Model("missile_cluster", density=4)
    missile_geom(m, "od_paint", "dark_paint", "white")
    return m


@item("missile_incendiary", "missile")
def missile_incendiary():
    m = Model("missile_incendiary", density=4)
    missile_geom(m, "grey_paint", "red", "orange")
    return m


def heavy_missile_geom(m, body_mat, nose_mat, band_mat, stencil, fin_mat=None, nose="ogive", part="body"):
    """big strategic missile, vertical along +Y, centred x=z=8, y -14..30. Rendered ~1.65x on the heavy launcher."""
    fin_mat = fin_mat or body_mat
    m.cyl("y", (8, 8), 2.4, -11.0, 18.0, body_mat, sides=16, part=part, deco=("stencil:" + stencil,))
    m.cyl("y", (8, 8), 2.45, -2.0, -0.6, band_mat, sides=16, part=part)
    m.cyl("y", (8, 8), 2.45, 12.0, 13.2, band_mat, sides=16, part=part)
    m.cyl("y", (8, 8), 2.5, 6.6, 7.0, "steel", sides=16, part=part)               # stage joint
    if nose == "ogive":
        m.cyl("y", (8, 8), 2.25, 18.0, 21.5, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 1.9, 21.5, 24.5, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 1.4, 24.5, 27.0, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 0.8, 27.0, 28.8, nose_mat, sides=8, part=part)
        m.cyl("y", (8, 8), 0.35, 28.8, 30.0, nose_mat, sides=8, part=part)
    elif nose == "blunt":                                                        # bunker buster: hardened penetrator
        m.cyl("y", (8, 8), 2.2, 18.0, 23.0, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 1.6, 23.0, 26.5, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 1.0, 26.5, 28.5, "chrome", sides=8, part=part)
        m.cyl("y", (8, 8), 0.5, 28.5, 29.6, "chrome", sides=8, part=part)
    else:                                                                        # thermobaric: rounded bulb warhead
        m.cyl("y", (8, 8), 2.6, 18.0, 22.0, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 2.3, 22.0, 25.0, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 1.7, 25.0, 27.4, nose_mat, sides=16, part=part)
        m.cyl("y", (8, 8), 0.9, 27.4, 29.2, nose_mat, sides=8, part=part)
    # engine section + nozzle
    m.cyl("y", (8, 8), 2.1, -13.0, -11.0, "steel", sides=16, part=part)
    m.cyl("y", (8, 8), 1.5, -14.0, -13.0, "gunmetal", sides=16, part=part)
    m.box([6.9, -14.05, 6.9], [9.1, -13.95, 9.1], "black", part=part)
    # 4 big tail fins with root chord along the body
    m.box([2.6, -12.5, 7.8], [13.4, -4.0, 8.2], fin_mat, part=part, deco=("panel",))
    m.box([7.8, -12.5, 2.6], [8.2, -4.0, 13.4], fin_mat, part=part, deco=("panel",))
    m.box([2.2, -12.5, 7.75], [3.0, -9.0, 8.25], "black", part=part)
    m.box([13.0, -12.5, 7.75], [13.8, -9.0, 8.25], "black", part=part)
    m.box([7.75, -12.5, 2.2], [8.25, -9.0, 3.0], "black", part=part)
    m.box([7.75, -12.5, 13.0], [8.25, -9.0, 13.8], "black", part=part)
    # mid-body strakes
    m.box([5.4, 0.0, 7.85], [10.6, 5.0, 8.15], fin_mat, part=part)
    m.box([7.85, 0.0, 5.4], [8.15, 5.0, 10.6], fin_mat, part=part)
    # raceway (cable conduit) along one side
    m.box([10.3, -10.0, 7.6], [10.7, 17.0, 8.4], "grey_metal", part=part)


@item("missile_thermobaric", "missile")
def missile_thermobaric():
    m = Model("missile_thermobaric", density=4)
    heavy_missile_geom(m, "od_paint", "orange", "yellow", "TBX-9", fin_mat="dark_paint", nose="bulb")
    return m


@item("missile_bunker_buster", "missile")
def missile_bunker_buster():
    m = Model("missile_bunker_buster", density=4)
    heavy_missile_geom(m, "grey_paint", "dark_paint", "yellow", "GBU-X", fin_mat="dark_paint", nose="blunt")
    return m


@item("missile_titan", "missile")
def missile_titan():
    m = Model("missile_titan", density=4)
    heavy_missile_geom(m, "white", "dark_paint", "red", "TITAN", fin_mat="black", nose="ogive")
    return m


# =============================================================================== TOOLS / MISC ITEMS
@item("launch_remote")
def launch_remote():
    m = Model("launch_remote", density=12)
    m.box([4.0, 1.0, 6.8], [12.0, 13.0, 9.2], "dark_paint", deco=("panel",))
    m.box([3.6, 0.6, 6.6], [12.4, 1.4, 9.4], "rubber")
    m.box([3.6, 12.6, 6.6], [12.4, 13.4, 9.4], "rubber")
    m.box([5.0, 8.0, 9.2], [11.0, 12.0, 9.3], "screen", deco=("text:LOCK",))      # screen
    m.box([6.6, 3.0, 9.2], [9.4, 5.8, 9.5], "yellow")                              # cover frame
    m.box([7.1, 3.5, 9.3], [8.9, 5.3, 9.8], "glow_red", light=True)               # big button
    m.box([6.6, 5.8, 9.2], [9.4, 7.0, 9.9], "lens", rot=("x", -45.0, [8.0, 5.8, 9.5]))   # flip cover
    for x in (5.0, 10.2):
        m.box([x, 6.0, 9.2], [x + 0.8, 6.8, 9.45], "steel")
    m.cyl("y", (10.6, 8.0), 0.22, 13.4, 22.0, "rubber", sides=4)                   # antenna
    m.cyl("y", (10.6, 8.0), 0.4, 21.6, 22.4, "rubber", sides=8)
    m.box([5.0, 13.4, 7.6], [6.4, 14.4, 8.4], "glow_green", light=True)
    return m


@item("dog_tag")
def dog_tag():
    m = Model("dog_tag", density=14)
    for i, (x, y, rz) in enumerate(((5.0, 4.0, 0.0), (7.2, 3.4, 22.5))):
        m.box([x, y, 7.8 + i * 0.3], [x + 4.4, y + 7.0, 8.0 + i * 0.3], "chrome", rot=("z", rz, [x + 2.2, y + 3.5, 8]) if rz else None,
              deco=("text:GW",))
        m.box([x + 1.9, y + 6.3, 7.75 + i * 0.3], [x + 2.5, y + 6.7, 8.05 + i * 0.3], "black")
    # ball chain
    for k in range(9):
        a = math.radians(200 - k * 22)
        x, y = 8.2 + math.cos(a) * 3.2, 11.0 + math.sin(a) * 2.8
        m.box([x - 0.25, y - 0.25, 7.8], [x + 0.25, y + 0.25, 8.3], "steel")
    return m


@item("arena_tool")
def arena_tool():
    m = Model("arena_tool", density=10)
    # rugged tablet with antenna and a flag
    m.box([2.0, 3.0, 7.0], [12.0, 11.0, 8.6], "od_paint", deco=("panel",))
    m.box([2.8, 3.8, 8.6], [11.2, 10.2, 8.7], "screen", deco=("text:ARENA",))
    m.box([1.6, 2.6, 6.8], [2.6, 11.4, 8.8], "rubber")
    m.box([11.4, 2.6, 6.8], [12.4, 11.4, 8.8], "rubber")
    m.cyl("y", (12.0, 7.8), 0.25, 11.0, 19.0, "steel", sides=4)
    m.box([12.25, 15.2, 7.7], [16.5, 18.8, 7.9], "red", deco=("star:",))
    m.box([3.4, 4.6, 8.65], [5.0, 6.2, 8.75], "glow_green", light=True)
    m.box([9.0, 4.6, 8.65], [10.6, 6.2, 8.75], "glow_red", light=True)
    return m


def soldier_fig(m, cloth, helmet):
    """toy soldier figurine ~12 units tall, aiming a rifle"""
    # legs
    m.box([6.6, 1.0, 7.3], [7.8, 6.0, 8.7], cloth)
    m.box([8.2, 1.0, 7.3], [9.4, 6.0, 8.7], cloth)
    m.box([6.5, 0.0, 7.0], [7.9, 1.0, 9.1], "rubber")
    m.box([8.1, 0.0, 7.0], [9.5, 1.0, 9.1], "rubber")
    m.box([5.0, -0.0, 5.0], [11.0, 0.0001 + 0.3, 11.0], "dark_paint")
    # torso + vest
    m.box([6.4, 6.0, 7.2], [9.6, 10.4, 8.8], cloth)
    m.box([6.3, 6.6, 7.0], [9.7, 9.8, 9.0], "canvas", deco=("lines:2",))
    # head + helmet
    m.box([7.1, 10.4, 7.3], [8.9, 12.2, 9.0], "skin")
    m.box([6.8, 11.6, 7.0], [9.2, 12.8, 9.3], helmet)
    m.box([7.3, 11.4, 8.95], [8.7, 11.7, 9.05], "black")
    # arms holding rifle forward (+Z)
    m.box([5.6, 8.6, 7.6], [6.4, 9.4, 11.0], cloth)
    m.box([9.6, 8.6, 7.6], [10.4, 9.4, 10.6], cloth)
    m.box([7.6, 9.0, 8.0], [8.4, 9.8, 14.5], "gunmetal")
    m.box([7.7, 8.4, 9.0], [8.3, 9.0, 10.2], "gunmetal")
    m.box([7.6, 8.6, 6.0], [8.4, 9.6, 8.0], "wood")


@item("green_soldier")
def green_soldier():
    m = Model("green_soldier", density=12)
    soldier_fig(m, "camo_wood", "od_paint")
    return m


@item("red_soldier")
def red_soldier():
    m = Model("red_soldier", density=12)
    soldier_fig(m, "camo_desert", "red")
    return m


@item("steel_ingot")
def steel_ingot():
    m = Model("steel_ingot", density=12)
    m.box([3.0, 5.0, 6.0], [13.0, 7.6, 10.0], "grey_metal", deco=("text:STEEL",))
    m.box([3.6, 7.6, 6.6], [12.4, 8.4, 9.4], "grey_metal")
    return m


@item("gun_parts")
def gun_parts():
    m = Model("gun_parts", density=12)
    m.cyl("x", (5.0, 8.0), 0.6, 2.0, 12.0, "blued", sides=8)
    m.box([4.0, 6.0, 5.5], [10.0, 7.6, 7.2], "gunmetal", deco=("rivets",))
    m.box([9.0, 5.6, 8.6], [13.0, 6.4, 10.0], "steel")
    m.cyl("y", (6.0, 10.0), 0.9, 5.4, 6.6, "bright", sides=8)
    m.box([3.0, 5.0, 9.0], [5.0, 6.0, 12.0], "polymer")
    return m
