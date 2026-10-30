"""25 gun models. Each builder returns a Model with parts 'body', 'mag' (+ 'mag_ext', optional 'spin'/'break')
and mount points: grip, sight (eye line for iron sights), muzzle, scope (rail top), under (under barrel),
side (side rail), magdir (direction the magazine leaves during reload)."""
import math

from modelkit import Model
from parts import *  # noqa

GUNS = {}


def gun(name):
    def deco(fn):
        GUNS[name] = fn
        return fn
    return deco


def classic_stock(m, xb, xf, ytop, ybot, w, mat="wood", plate="steel", part="body"):
    """butt at xb, wrist ends at xf; top line ytop; butt bottom ybot"""
    m.box([xb, ybot - 0.1, Z - w / 2 - 0.05], [xb + 0.4, ytop + 0.05, Z + w / 2 + 0.05], plate, part=part)
    m.box([xb + 0.4, ybot, Z - w / 2], [xb + 5.6, ytop, Z + w / 2], mat, part=part)
    h = min(2.5, (ytop - ybot - 2.6) / 0.924)
    x1 = xb + 5.6
    m.box([x1 - 0.4, ybot, Z - w / 2 + 0.03], [x1 + 6.5, ybot + h, Z + w / 2 - 0.03], mat, rot=("z", 22.5, [x1, ybot, Z]), part=part)
    m.box([x1 - 0.2, ybot + 2.55, Z - w / 2 + 0.06], [xf, ytop - 0.02, Z + w / 2 - 0.06], mat, part=part)


def mag_dir(m, ang_deg):
    a = math.radians(ang_deg)
    m.mount("magdir", [math.sin(a), -math.cos(a), 0.0])


# =============================================================================== PISTOLS
@gun("m1911")
def m1911():
    m = Model("m1911", density=8)
    # slide
    block(m, 0.0, 9.8, 9.0, 10.95, 1.2, "blued", deco=("serr", "port", "text:M1911"))
    m.cyl("x", (10.55, 8), 0.45, 0.2, 9.6, "blued", sides=8)
    front_post(m, 9.1, 10.95, h=0.45, ears=False)
    rear_sight(m, 0.8, 10.95)
    m.box([9.8, 9.55, 7.6], [10.05, 10.45, 8.4], "steel")              # bushing
    m.box([9.95, 9.75, 7.8], [10.08, 10.25, 8.2], "black")
    # frame
    block(m, 0.3, 8.6, 8.2, 9.05, 1.15, "blued")
    m.box([2.4, 8.85, 8.55], [5.6, 9.25, 8.75], "steel")                # slide stop
    # hammer + beavertail
    m.box([-0.5, 10.0, 7.7], [0.25, 10.95, 8.3], "steel", rot=("z", 22.5, [0.0, 10.4, 8]))
    m.box([-0.9, 8.6, 7.5], [0.5, 9.2, 8.5], "blued")
    # grip with wooden panels
    grip(m, 1.6, 8.6, length=5.3, depth=2.35, w=1.15, mat="blued", deco=())
    grip(m, 1.6, 8.4, length=4.4, depth=2.0, w=1.45, mat="wood_red", deco=("grip",))
    trigger_guard(m, 3.6, 6.4, 6.9, 8.25, w=0.45)

    def mag(m, part, k):
        grip(m, 1.6, 8.1, length=5.4 * k, depth=1.6, w=0.95, mat="steel", deco=("ribs",), part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    mag_dir(m, -22.5)
    m.mount("grip", [1.9, 7.0, 8])
    m.mount("sight", [0.8, 11.5, 8])
    m.mount("muzzle", [10.05, 10.0, 8])
    m.mount("scope", [3.5, 10.95, 8])
    m.mount("under", [7.2, 8.2, 8])
    m.mount("side", [7.0, 8.6, 8.6])
    return m


@gun("p9")
def p9():
    m = Model("p9", density=8)
    block(m, 0.0, 8.6, 9.0, 10.8, 1.15, "gunmetal", deco=("serr", "port"))
    block(m, 0.2, 8.4, 10.8, 10.9, 0.9, "gunmetal")
    front_post(m, 8.0, 10.85, h=0.4, ears=False)
    rear_sight(m, 0.7, 10.85)
    m.box([8.6, 9.35, 7.55], [8.7, 10.35, 8.45], "black")               # muzzle face
    # polymer frame + accessory rail
    block(m, 0.1, 8.4, 8.05, 9.0, 1.2, "polymer")
    rail(m, 5.4, 8.2, 7.6, w=1.0, h=0.45, mat="polymer")
    m.box([5.4, 7.6, 7.5], [8.2, 8.05, 8.5], "polymer", deco=("rail",))
    grip(m, 1.7, 8.4, length=5.0, depth=2.4, w=1.35, mat="polymer", deco=("grip",))
    m.box([2.6, 6.6, 7.45], [3.4, 7.9, 8.55], "polymer")                 # finger groove front
    # squared trigger guard
    z0, z1 = zr(0.5)
    m.box([3.5, 7.0, z0], [6.4, 7.3, z1], "polymer")
    m.box([6.1, 7.0, z0], [6.4, 8.1, z1], "polymer")
    m.box([4.5, 7.35, 7.85], [4.85, 8.05, 8.15], "polymer", rot=("z", -22.5, [4.6, 8.05, 8]))

    def mag(m, part, k):
        grip(m, 1.7, 8.0, length=5.3 * k, depth=1.7, w=1.0, mat="polymer" if part == "mag" else "steel", deco=("ribs",), part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, -22.5)
    m.mount("grip", [2.0, 7.0, 8])
    m.mount("sight", [0.7, 11.4, 8])
    m.mount("muzzle", [8.7, 9.85, 8])
    m.mount("scope", [3.5, 10.9, 8])
    m.mount("under", [6.8, 7.6, 8])
    m.mount("side", [6.8, 8.5, 8.6])
    return m


@gun("magnum50")
def magnum50():
    m = Model("magnum50", density=7)
    # big slide / barrel block
    block(m, 0.0, 6.0, 9.0, 11.4, 1.5, "bright", deco=("serr", "port"))
    block(m, 6.0, 12.4, 9.4, 11.4, 1.35, "bright", deco=("text:50 AE",))
    block(m, 6.4, 12.2, 11.4, 11.75, 0.8, "bright", deco=("rail",))   # barrel top rib
    m.box([12.4, 9.7, 7.55], [12.5, 10.8, 8.45], "black")
    block(m, 6.0, 12.4, 9.0, 9.4, 1.0, "bright")
    front_post(m, 11.6, 11.75, h=0.45, ears=False)
    rear_sight(m, 0.8, 11.4)
    # frame
    block(m, 0.2, 7.0, 8.0, 9.05, 1.45, "steel")
    m.box([-0.6, 10.2, 7.65], [0.2, 11.1, 8.35], "steel", rot=("z", 22.5, [-0.2, 10.6, 8]))
    m.box([-0.9, 8.3, 7.4], [0.5, 9.0, 8.6], "steel")
    grip(m, 1.5, 8.4, length=5.6, depth=2.6, w=1.6, mat="rubber", deco=("grip",))
    trigger_guard(m, 3.4, 6.6, 6.7, 8.1, w=0.55)

    def mag(m, part, k):
        grip(m, 1.5, 8.0, length=5.7 * k, depth=1.8, w=1.1, mat="steel", deco=("ribs",), part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    mag_dir(m, -22.5)
    m.mount("grip", [1.8, 7.0, 8])
    m.mount("sight", [0.8, 12.0, 8])
    m.mount("muzzle", [12.5, 10.25, 8])
    m.mount("scope", [8.5, 11.75, 8])
    m.mount("under", [6.0, 8.0, 8])
    m.mount("side", [9.0, 9.6, 8.7])
    return m


@gun("revolver")
def revolver():
    m = Model("revolver", density=8)
    # frame
    block(m, 0.0, 6.4, 8.6, 9.0, 1.1, "blued")
    block(m, 0.0, 1.4, 9.0, 11.3, 1.1, "blued")       # rear of frame (recoil shield)
    block(m, 5.0, 6.4, 9.0, 11.3, 1.1, "blued")       # front of frame
    block(m, 1.4, 5.0, 10.9, 11.4, 1.1, "blued")      # top strap
    # barrel with full underlug and vent rib
    barrel(m, 6.4, 14.0, 10.45, 0.48, mat="blued")
    block(m, 6.4, 13.9, 9.2, 10.4, 0.85, "blued")     # underlug
    block(m, 6.4, 14.0, 10.85, 11.2, 0.5, "blued", deco=("rail",))
    front_post(m, 13.4, 11.2, h=0.5, ears=False)
    block(m, 1.0, 1.8, 11.3, 11.75, 0.9, "blued")     # rear sight
    # hammer, trigger
    m.box([-0.7, 10.6, 7.75], [0.3, 11.6, 8.25], "bright", rot=("z", 22.5, [-0.2, 11.0, 8]))
    trigger_guard(m, 2.0, 4.6, 7.3, 8.65, w=0.45)
    # grip (wood, strongly angled)
    grip(m, 0.4, 9.0, length=5.0, depth=2.3, w=1.45, ang=-22.5, mat="wood", deco=("grip",))
    m.box([-1.4, 8.4, 7.3], [0.9, 9.3, 8.7], "blued", rot=("z", -22.5, [0.0, 8.9, 8]))

    def cyl_part(m, part, k):
        m.cyl("x", (10.0, 8), 1.25, 1.45, 4.95, "blued" if part == "mag" else "bright", sides=16, part=part, deco=("vlines:6",))
        m.cyl("x", (10.0, 8), 0.3, 0.9, 5.4, "steel", sides=8, part=part)
    cyl_part(m, "mag", 1)
    cyl_part(m, "mag_ext", 1)
    m.mount("magdir", [0.0, -0.25, -1.0])
    m.mount("grip", [-0.4, 7.0, 8])
    m.mount("sight", [1.4, 11.85, 8])
    m.mount("muzzle", [14.05, 10.45, 8])
    m.mount("scope", [9.5, 11.2, 8])
    m.mount("under", [10.0, 9.2, 8])
    m.mount("side", [10.0, 10.0, 8.5])
    return m


# =============================================================================== SMGs
@gun("smg5")
def smg5():
    m = Model("smg5", density=5)
    # receiver tube
    block(m, 0.0, 14.2, 8.5, 10.2, 1.5, "blued", deco=("port", "text:SMG-5"))
    m.cyl("x", (10.2, 8), 0.75, 0.1, 14.1, "blued", sides=8)
    # trigger housing
    block(m, 1.5, 8.2, 7.4, 8.55, 1.3, "polymer")
    grip(m, 3.0, 7.6, length=4.4, depth=1.9, w=1.4, mat="polymer")
    trigger_guard(m, 4.6, 8.0, 6.0, 7.45, mat="polymer")
    # tropical handguard
    block(m, 14.2, 20.0, 7.6, 9.9, 2.0, "polymer", deco=("lines:3",))
    m.cyl("x", (8.8, 8), 1.1, 14.3, 19.9, "polymer", sides=8, deco=("lines:3",))
    # cocking tube + handle
    tube(m, 14.2, 20.6, 10.55, 0.45, "blued")
    m.box([16.5, 10.3, 8.4], [17.0, 10.75, 9.4], "polymer")
    # barrel + lugs
    barrel(m, 20.0, 23.2, 9.35, 0.42)
    for x in (21.0, 21.6, 22.2):
        block(m, x, x + 0.3, 9.25 - 0.62, 9.35 + 0.62, 0.5, "blued")
    # hooded front sight
    m.cyl("z", (20.5, 11.6), 0.75, 7.75, 8.25, "blued", sides=8)
    block(m, 20.2, 20.8, 10.5, 11.0, 1.0, "blued")
    block(m, 20.45, 20.55, 11.0, 11.7, 0.15, "blued")
    # rear drum sight
    m.cyl("z", (1.6, 11.4), 0.55, 7.55, 8.45, "blued", sides=8)
    block(m, 1.2, 2.0, 10.6, 11.0, 0.9, "blued")
    # retractable stock
    for y in (10.0, 8.9):
        tube(m, -8.5, 0.0, y, 0.22, "steel")
    block(m, -9.3, -8.5, 7.2, 10.7, 1.5, "rubber")
    block(m, -8.6, -7.6, 8.6, 10.3, 0.7, "steel")
    # sling loop
    sling_loop(m, 14.6, 7.6)

    def mag(m, part, k):
        chain(m, 11.6, 8.5, [(2.8 * k, 0.0), (2.6 * k, 22.5), (1.6 * k, 22.5)], 1.75, 1.0, "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.45)
    mag_dir(m, 10)
    m.mount("grip", [2.6, 6.4, 8])
    m.mount("sight", [1.6, 11.6, 8])
    m.mount("muzzle", [23.2, 9.35, 8])
    m.mount("scope", [7.0, 10.95, 8])
    m.mount("under", [17.0, 7.6, 8])
    m.mount("side", [17.0, 8.8, 9.0])
    return m


@gun("micro_smg")
def micro_smg():
    m = Model("micro_smg", density=6)
    block(m, 0.0, 11.0, 8.6, 10.8, 1.6, "gunmetal", deco=("rivets", "lines:2", "text:MICRO"))
    block(m, 0.4, 10.6, 10.8, 11.1, 1.2, "gunmetal")
    m.box([7.0, 11.1, 7.6], [8.2, 11.5, 8.4], "polymer", deco=("grip",))        # top cocking knob
    # barrel nut + barrel
    m.cyl("x", (9.6, 8), 0.68, 11.0, 11.8, "gunmetal", sides=8, deco=("vlines:4",))
    barrel(m, 11.8, 13.4, 9.6, 0.38)
    # sights
    front_post(m, 10.0, 11.1, h=0.7)
    rear_sight(m, 1.0, 11.1)
    # grip with mag inside (vertical)
    block(m, 3.1, 6.3, 3.9, 8.6, 1.5, "polymer", deco=("grip",))
    block(m, 2.9, 3.2, 4.6, 8.6, 1.3, "gunmetal")                                # grip safety
    trigger_guard(m, 6.3, 9.0, 6.6, 8.6, w=0.5)
    # folding wire stock (folded under)
    block(m, -0.6, 0.0, 7.4, 10.6, 1.6, "gunmetal")
    for z in (7.35, 8.65):
        m.box([0.0, 7.65, z - 0.12], [10.2, 7.9, z + 0.12], "steel")
    block(m, 9.8, 10.4, 7.0, 8.0, 1.6, "rubber")

    def mag(m, part, k):
        m.box([3.55, 1.6 - (k - 1) * 5.0, 7.45], [5.85, 6.2, 8.55], "steel", part=part, deco=("ribs",))
        m.box([3.4, 1.3 - (k - 1) * 5.0, 7.35], [6.0, 1.7 - (k - 1) * 5.0, 8.65], "steel", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    mag_dir(m, 0)
    m.mount("grip", [4.7, 6.6, 8])
    m.mount("sight", [1.0, 11.65, 8])
    m.mount("muzzle", [13.4, 9.6, 8])
    m.mount("scope", [4.5, 11.1, 8])
    m.mount("under", [9.6, 8.6, 8])
    m.mount("side", [9.0, 9.6, 8.8])
    return m


@gun("vortex")
def vortex():
    m = Model("vortex", density=5)
    # upper receiver + full rail
    block(m, -1.0, 12.4, 9.2, 11.0, 1.7, "dark_paint", deco=("port",))
    rail(m, -0.8, 12.2, 11.0, w=1.1)
    # angular lower housing (two-tone tan)
    block(m, 2.4, 9.8, 6.6, 9.2, 1.85, "tan", deco=("panel",))
    m.box([9.6, 6.6, 7.1], [12.6, 8.1, 8.9], "tan", rot=("z", 22.5, [9.6, 6.6, 8]))
    block(m, 9.8, 12.4, 8.0, 9.2, 1.75, "tan")
    # shroud + barrel
    m.cyl("x", (10.1, 8), 0.75, 12.4, 15.2, "dark_paint", sides=8, deco=("vslots",))
    barrel(m, 15.2, 16.8, 10.1, 0.45)
    flash_hider(m, 16.8, 17.8, 10.1, 0.52)
    # grip behind the housing
    grip(m, 1.6, 9.2, length=4.4, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 1.9, 4.4, 6.2, 7.0, mat="tan")
    # folding skeleton stock
    tube(m, -6.0, -1.0, 10.3, 0.45, "dark_paint")
    tube(m, -6.0, -1.0, 8.6, 0.4, "dark_paint")
    m.box([-6.2, 6.2, 7.4], [-5.4, 10.9, 8.6], "polymer")
    m.box([-6.9, 6.0, 7.25], [-6.2, 11.1, 8.75], "rubber")
    # flip-up sights
    block(m, 0.0, 0.8, 11.45, 12.2, 0.9, "polymer")
    block(m, 11.0, 11.8, 11.45, 12.25, 0.9, "polymer")

    def mag(m, part, k):
        m.box([5.4, 6.8 - 4.6 * k, 7.45], [7.4, 6.8, 8.55], "polymer", part=part, deco=("ribs",))
        m.box([5.25, 6.6 - 4.6 * k, 7.35], [7.55, 7.0 - 4.6 * k, 8.65], "polymer", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.5)
    mag_dir(m, 0)
    m.mount("grip", [1.3, 7.2, 8])
    m.mount("sight", [0.4, 12.25, 8])
    m.mount("muzzle", [17.8, 10.1, 8])
    m.mount("scope", [5.0, 11.45, 8])
    m.mount("under", [11.0, 8.0, 8])
    m.mount("side", [13.5, 10.1, 8.75])
    return m


@gun("pdw")
def pdw():
    m = Model("pdw", density=5)
    # rounded bullpup body
    block(m, -6.0, 11.0, 7.4, 10.6, 2.0, "polymer", deco=("panel",))
    m.cyl("x", (9.0, 8), 1.55, -5.8, 10.8, "polymer", sides=8)
    m.box([9.0, 6.0, 7.1], [12.6, 9.6, 8.9], "polymer", rot=("z", 45.0, [10.8, 7.8, 8]))
    block(m, -6.6, -6.0, 6.6, 10.8, 2.1, "rubber")        # butt plate
    m.box([-6.0, 6.4, 7.05], [-1.0, 8.2, 8.95], "polymer", rot=("z", 22.5, [-3.5, 7.3, 8]))
    # thumbhole grip loop
    z0, z1 = zr(1.6)
    m.box([1.0, 5.0, z0], [8.6, 5.7, z1], "polymer")
    m.box([7.8, 5.6, z0], [8.6, 7.4, z1], "polymer")
    m.box([0.6, 5.0, z0], [1.8, 7.4, z1], "polymer", deco=("grip",))
    m.box([4.2, 6.6, 7.85], [4.6, 7.4, 8.15], "steel")    # trigger
    # top magazine
    def mag(m, part, k):
        block(m, -3.6, 9.2 + (k - 1) * 3, 10.6, 11.6, 1.8, "polymer_grey" if part == "mag" else "tan", part=part, deco=("lines:1",))
        block(m, -3.4, 9.0 + (k - 1) * 3, 10.75, 11.45, 1.86, "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    m.mount("magdir", [0.0, 1.0, 0.0])
    # integrated reflex sight
    block(m, 9.0, 11.2, 10.6, 12.6, 1.2, "polymer")
    m.box([9.6, 11.4, 7.55], [10.6, 12.4, 8.45], "lens")
    # barrel
    barrel(m, 11.0, 14.6, 9.0, 0.4)
    flash_hider(m, 13.6, 14.8, 9.0, 0.48)
    m.mount("grip", [1.2, 6.2, 8])
    m.mount("sight", [9.0, 12.0, 8])
    m.mount("muzzle", [14.8, 9.0, 8])
    m.mount("scope", [9.8, 12.6, 8])
    m.mount("under", [10.0, 7.2, 8])
    m.mount("side", [10.0, 9.0, 9.0])
    return m


# =============================================================================== RIFLES
@gun("ak47")
def ak47():
    m = Model("ak47", density=4.5)
    classic_stock(m, -15.0, -1.4, 9.4, 4.4, 1.5, mat="wood")
    # receiver (stamped steel)
    block(m, -1.6, 11.2, 7.5, 10.2, 1.6, "gunmetal", deco=("rivets", "port"))
    block(m, 0.0, 11.0, 10.2, 10.6, 1.35, "gunmetal")
    m.cyl("x", (10.55, 8), 0.62, 0.2, 10.8, "gunmetal", sides=8)
    block(m, -1.6, -0.6, 8.2, 10.4, 1.5, "gunmetal")
    m.box([1.2, 9.25, 8.8], [8.4, 9.75, 8.95], "steel")
    m.box([9.2, 9.05, 8.75], [10.2, 9.6, 9.7], "bright")
    block(m, 11.2, 13.2, 7.7, 10.5, 1.75, "gunmetal")
    rear_sight(m, 12.0, 10.5)
    block(m, 13.2, 21.0, 7.6, 9.75, 2.0, "wood", deco=("lines:2",))
    m.cyl("x", (8.7, 8), 1.02, 13.4, 20.8, "wood", sides=8)
    block(m, 13.2, 19.6, 9.75, 10.75, 1.25, "wood")
    m.box([13.3, 10.0, 7.45], [19.5, 10.95, 8.55], "wood", rot=("x", 45.0, [16.4, 10.47, 8.0]))
    block(m, 21.0, 21.6, 7.9, 9.6, 1.9, "steel")
    tube(m, 19.6, 22.6, 10.25, 0.48, "gunmetal")
    block(m, 22.0, 23.3, 8.0, 10.9, 1.25, "gunmetal")
    barrel(m, 13.0, 26.0, 8.85, 0.55, muzzle=False)
    front_post(m, 24.6, 9.35, h=1.95)
    block(m, 24.0, 25.2, 8.1, 9.4, 1.3, "gunmetal")
    muzzle_brake(m, 26.0, 27.6, 8.85, 0.72)
    tube(m, 21.6, 25.8, 7.75, 0.18, "bright")
    block(m, 2.6, 6.2, 6.9, 7.6, 1.5, "gunmetal")
    grip(m, 4.4, 7.4, length=4.6, depth=1.9, w=1.45, mat="wood_red")
    trigger_guard(m, 4.2, 8.4, 5.9, 7.5)

    def mag(m, part, k):
        chain(m, 9.6, 7.6, [(3.0 * k, 0.0), (2.8 * k, 22.5), (2.6 * k, 22.5)], 2.9, 1.25,
              "steel" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 15)
    sling_loop(m, -13.5, 4.6)
    m.mount("grip", [4.0, 6.0, 8])
    m.mount("sight", [12.0, 11.3, 8])
    m.mount("muzzle", [27.6, 8.85, 8])
    m.mount("scope", [5.0, 11.15, 8])
    m.mount("under", [17.0, 7.6, 8])
    m.mount("side", [17.0, 8.7, 9.0])
    return m


def ar_lower(m, x0, grip_x, mat="dark_paint"):
    grip(m, grip_x, 7.6, length=4.2, depth=1.8, w=1.4, mat="polymer")
    trigger_guard(m, grip_x + 0.4, grip_x + 3.5, 6.2, 7.45, mat=mat)


@gun("m4a1")
def m4a1():
    m = Model("m4a1", density=4.5)
    ar_receiver(m, -1.0, 9.6, 7.4, 10.6)
    ar_stock(m, -1.0, 9.85)
    m.box([-1.6, 10.3, 7.3], [-0.6, 10.75, 8.7], "dark_paint")                   # charging handle
    ar_lower(m, -1.0, 2.0)
    m_lok_handguard(m, 9.6, 17.6, 9.0, h=2.5, w=2.0)
    barrel(m, 17.6, 21.6, 9.0, 0.42, muzzle=False)
    # A-frame front sight base
    block(m, 17.9, 18.9, 8.4, 9.8, 1.2, "dark_paint")
    m.box([17.4, 9.8, 7.6], [19.4, 11.0, 8.4], "dark_paint", rot=("z", 45.0, [18.4, 10.4, 8]))
    front_post(m, 18.4, 10.95, h=0.75, ears=True)
    flash_hider(m, 21.6, 23.0, 9.0, 0.5)
    # rear BUIS
    block(m, 0.0, 1.2, 11.05, 11.4, 1.0, "polymer")
    rear_sight(m, 0.6, 11.4)

    def mag(m, part, k):
        stanag_mag(m, 6.0, 7.5, length=6.0 * k, part=part, mat="dark_paint" if part == "mag" else "tan")
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 10)
    m.mount("grip", [1.6, 6.2, 8])
    m.mount("sight", [0.6, 12.0, 8])
    m.mount("muzzle", [23.0, 9.0, 8])
    m.mount("scope", [4.5, 11.05, 8])
    m.mount("under", [14.0, 7.75, 8])
    m.mount("side", [14.0, 9.0, 9.0])
    return m


@gun("m16a4")
def m16a4():
    m = Model("m16a4", density=4)
    ar_receiver(m, -1.0, 9.6, 7.4, 10.6)
    m.box([-1.6, 10.3, 7.3], [-0.6, 10.75, 8.7], "dark_paint")
    ar_lower(m, -1.0, 2.0)
    # fixed A2 stock
    classic_stock(m, -14.6, -0.8, 10.3, 5.0, 1.55, mat="polymer", plate="rubber")
    # round ribbed handguard
    m.cyl("x", (9.0, 8), 1.2, 9.6, 19.2, "polymer", sides=16, deco=("vlines:8",))
    block(m, 9.4, 9.8, 7.6, 10.4, 2.5, "dark_paint")
    barrel(m, 19.2, 27.0, 9.0, 0.42, muzzle=False)
    block(m, 23.4, 24.4, 8.4, 9.8, 1.2, "dark_paint")
    m.box([22.9, 9.8, 7.6], [24.9, 11.0, 8.4], "dark_paint", rot=("z", 45.0, [23.9, 10.4, 8]))
    front_post(m, 23.9, 10.95, h=0.75)
    m.box([24.6, 8.0, 7.8], [25.4, 8.6, 8.2], "dark_paint")                   # bayonet lug
    flash_hider(m, 27.0, 28.4, 9.0, 0.5)
    block(m, 0.0, 1.2, 11.05, 11.4, 1.0, "polymer")
    rear_sight(m, 0.6, 11.4)

    def mag(m, part, k):
        stanag_mag(m, 6.0, 7.5, length=6.0 * k, part=part, mat="dark_paint" if part == "mag" else "tan")
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 10)
    sling_loop(m, -12.0, 5.2)
    m.mount("grip", [1.6, 6.2, 8])
    m.mount("sight", [0.6, 12.0, 8])
    m.mount("muzzle", [28.4, 9.0, 8])
    m.mount("scope", [4.5, 11.05, 8])
    m.mount("under", [15.0, 7.8, 8])
    m.mount("side", [15.0, 9.0, 9.2])
    return m


@gun("br17")
def br17():
    m = Model("br17", density=4.5)
    # monolithic tan upper, long rail
    block(m, -1.0, 15.0, 8.9, 11.1, 1.85, "tan", deco=("port", "panel"))
    rail(m, -0.8, 14.8, 11.1, w=1.15)
    rail(m, 9.0, 14.6, 7.6, w=1.15)
    m.box([9.0, 7.6, 7.4], [14.6, 8.9, 8.6], "tan")
    # side charging handle (left)
    m.box([8.2, 9.8, 6.4], [9.0, 10.4, 7.1], "polymer")
    # lower (black polymer)
    block(m, 0.0, 8.8, 7.4, 8.9, 1.6, "polymer")
    ar_lower(m, 0.0, 2.6)
    # folding stock with cheek riser
    block(m, -1.6, -1.0, 7.8, 10.9, 1.7, "polymer")
    block(m, -11.0, -1.6, 9.3, 10.6, 1.4, "tan")
    block(m, -10.0, -4.0, 10.6, 11.3, 1.3, "polymer")                  # cheek riser
    m.box([-11.0, 5.2, 7.3], [-4.0, 7.2, 8.7], "tan", rot=("z", 22.5, [-11.0, 5.2, 8]))
    block(m, -11.0, -8.6, 5.2, 9.4, 1.5, "tan")
    block(m, -11.6, -11.0, 5.0, 10.7, 1.6, "rubber")
    # barrel + flash hider
    barrel(m, 15.0, 21.5, 9.6, 0.5, muzzle=False)
    flash_hider(m, 21.5, 23.0, 9.6, 0.58)
    tube(m, 14.8, 17.0, 10.6, 0.3, "polymer")                             # gas regulator
    # folding sights
    block(m, 0.2, 1.2, 11.55, 12.3, 0.9, "polymer")
    block(m, 13.6, 14.6, 11.55, 12.35, 0.9, "polymer")

    def mag(m, part, k):
        m.box([5.3, 7.5 - 5.6 * k, 7.35], [7.9, 7.6, 8.65], "polymer" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 0)
    m.mount("grip", [2.2, 6.2, 8])
    m.mount("sight", [0.7, 12.3, 8])
    m.mount("muzzle", [23.0, 9.6, 8])
    m.mount("scope", [5.0, 11.55, 8])
    m.mount("under", [12.0, 7.6, 8])
    m.mount("side", [12.0, 9.6, 9.0])
    return m


@gun("br77")
def br77():
    m = Model("br77", density=4.5)
    # smooth olive bullpup body, tapering towards the front
    block(m, -9.0, 2.4, 6.8, 10.2, 2.0, "olive", deco=("panel",))
    m.cyl("x", (8.7, 8), 1.5, -8.8, 2.2, "olive", sides=8)
    block(m, 2.4, 8.0, 7.6, 10.0, 1.8, "olive")
    m.cyl("x", (9.0, 8), 1.0, 2.2, 7.8, "olive", sides=8)
    m.box([-9.0, 5.6, 7.05], [-3.4, 7.4, 8.95], "olive", rot=("z", 22.5, [-9.0, 6.6, 8]))
    m.box([-9.6, 6.6, 6.9], [-9.0, 10.4, 9.1], "rubber")
    # big trigger guard enclosing the hand
    z0, z1 = zr(1.4)
    m.box([1.6, 4.8, z0], [7.8, 5.5, z1], "olive")
    m.box([7.0, 5.4, z0], [7.8, 7.2, z1], "olive")
    m.box([1.2, 4.8, z0], [2.0, 7.0, z1], "olive")
    grip(m, 3.0, 7.2, length=2.4, depth=1.6, w=1.4, ang=-22.5, mat="olive", deco=("grip",))
    m.box([4.2, 5.8, 7.85], [4.6, 7.0, 8.15], "steel")
    # integrated optic carry handle
    block(m, 1.5, 3.0, 10.2, 11.0, 1.0, "olive")
    block(m, 8.0, 9.5, 10.2, 11.0, 1.0, "olive")
    m.cyl("x", (11.55, 8), 0.85, 1.0, 10.0, "olive", sides=16)
    m.box([9.97, 10.95, 7.4], [10.03, 12.15, 8.6], "lens")
    m.box([0.97, 11.05, 7.5], [1.03, 12.05, 8.5], "lens")
    # barrel with folding foregrip
    block(m, 8.0, 9.6, 8.2, 9.6, 1.6, "steel")
    barrel(m, 9.6, 16.4, 8.9, 0.48, muzzle=False)
    flash_hider(m, 16.4, 17.6, 8.9, 0.56)
    m.box([9.6, 4.6, 7.4], [10.6, 8.3, 8.6], "olive", rot=("z", 22.5, [10.1, 8.3, 8]))

    def mag(m, part, k):
        chain(m, -3.4, 7.1, [(2.8 * k, 0.0), (2.6 * k, 22.5)], 2.4, 1.15, "smoke" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 10)
    m.mount("grip", [2.5, 5.8, 8])
    m.mount("sight", [1.0, 11.55, 8])
    m.mount("muzzle", [17.6, 8.9, 8])
    m.mount("scope", [5.5, 12.4, 8])
    m.mount("under", [13.0, 8.4, 8])
    m.mount("side", [13.0, 8.9, 8.6])
    return m


@gun("tactical_rifle")
def tactical_rifle():
    m = Model("tactical_rifle", density=4.5)
    block(m, -2.0, 12.0, 8.0, 10.9, 1.7, "polymer_grey", deco=("port", "panel"))
    # carry handle with sights
    block(m, 0.5, 1.5, 10.9, 12.8, 1.2, "polymer_grey")
    block(m, 9.0, 10.0, 10.9, 12.8, 1.2, "polymer_grey")
    block(m, 0.5, 10.0, 12.8, 13.4, 1.2, "polymer_grey", deco=("rail",))
    m.box([1.4, 11.3, 7.5], [1.6, 12.6, 8.5], "lens")
    # handguard with vents
    block(m, 12.0, 18.4, 8.0, 10.6, 1.9, "polymer_grey", deco=("slots:4",))
    barrel(m, 18.4, 22.0, 9.3, 0.45, muzzle=False)
    flash_hider(m, 22.0, 23.3, 9.3, 0.55)
    front_post(m, 17.5, 10.6, h=0.9)
    # cocking handle on top
    m.box([10.4, 10.9, 7.5], [11.2, 11.3, 9.4], "polymer")
    # lower + grip
    block(m, 0.0, 8.0, 7.0, 8.05, 1.5, "polymer")
    ar_lower(m, 0.0, 2.2, mat="polymer")
    # skeleton folding stock
    block(m, -2.6, -2.0, 7.4, 10.9, 1.6, "polymer")
    block(m, -12.0, -2.6, 9.8, 10.8, 1.3, "polymer")
    m.box([-12.0, 5.6, 7.4], [-3.5, 6.6, 8.6], "polymer", rot=("z", 22.5, [-12.0, 5.6, 8]))
    block(m, -12.0, -11.0, 5.5, 10.8, 1.4, "polymer")
    block(m, -12.6, -12.0, 5.3, 11.0, 1.6, "rubber")

    def mag(m, part, k):
        chain(m, 6.2, 7.2, [(2.8 * k, 0.0), (2.8 * k, 22.5)], 2.4, 1.2, "smoke" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 10)
    m.mount("grip", [1.8, 6.2, 8])
    m.mount("sight", [1.5, 13.4, 8])
    m.mount("muzzle", [23.3, 9.3, 8])
    m.mount("scope", [5.0, 13.4, 8])
    m.mount("under", [15.0, 8.0, 8])
    m.mount("side", [15.0, 9.3, 8.95])
    return m


# =============================================================================== SHOTGUNS
@gun("pump_shotgun")
def pump_shotgun():
    m = Model("pump_shotgun", density=4.5)
    classic_stock(m, -14.0, 0.6, 10.2, 5.0, 1.5, mat="wood", plate="rubber")
    block(m, -1.0, 8.4, 8.2, 10.7, 1.55, "blued", deco=("port", "text:PUMP 12"))
    block(m, 2.5, 5.5, 7.4, 8.2, 1.2, "blued")                       # loading port
    trigger_guard(m, 0.6, 3.6, 6.6, 8.2)
    barrel(m, 8.4, 27.0, 10.0, 0.62)
    block(m, 8.6, 27.0, 10.6, 10.8, 0.35, "blued", deco=("rail",))   # vent rib
    m.box([26.4, 10.8, 7.85], [26.7, 11.1, 8.15], "chrome")           # bead
    tube(m, 8.4, 23.0, 8.85, 0.58, "blued")
    m.box([22.8, 8.3, 7.5], [23.4, 10.4, 8.5], "blued")               # barrel clamp
    # pump forend with grooves
    m.cyl("x", (8.85, 8), 0.95, 11.0, 17.2, "wood", sides=8, deco=("vlines:7",))

    def mag(m, part, k):  # side saddle shells
        for i in range(4 if part == "mag" else 6):
            x = 0.0 + i * 1.35
            m.box([x, 8.6, 6.55], [x + 1.15, 10.4, 7.2], "red", part=part)
            m.box([x, 8.6, 6.5], [x + 1.15, 9.1, 7.25], "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [0.0, 0.0, -1.0])
    m.mount("pump", [14.1, 8.85, 8])
    m.mount("grip", [0.0, 7.2, 8])
    m.mount("sight", [0.0, 11.0, 8])
    m.mount("muzzle", [27.0, 10.0, 8])
    m.mount("scope", [3.5, 10.7, 8])
    m.mount("under", [19.0, 8.3, 8])
    m.mount("side", [19.0, 10.0, 8.7])
    return m


@gun("auto_shotgun")
def auto_shotgun():
    m = Model("auto_shotgun", density=4.5)
    block(m, -6.0, 13.6, 7.6, 11.0, 2.2, "dark_paint", deco=("panel", "rivets", "text:AUTO-12"))
    rail(m, -2.0, 12.0, 11.0, w=1.2)
    block(m, -4.0, 4.0, 11.45, 12.2, 0.9, "dark_paint")                # carry handle
    block(m, -4.0, -3.2, 11.0, 11.45, 0.9, "dark_paint")
    block(m, 3.2, 4.0, 11.0, 11.45, 0.9, "dark_paint")
    m.cyl("x", (10.0, 8), 1.1, 13.6, 16.4, "dark_paint", sides=8, deco=("vslots",))
    barrel(m, 16.4, 20.6, 10.0, 0.65)
    front_post(m, 15.6, 11.15, h=0.8)
    # stock (integrated)
    block(m, -12.0, -6.0, 8.0, 11.0, 2.0, "polymer")
    block(m, -12.6, -12.0, 7.8, 11.2, 2.2, "rubber")
    grip(m, 1.0, 7.8, length=4.4, depth=2.0, w=1.5, mat="polymer")
    trigger_guard(m, 1.4, 4.4, 6.3, 7.65)

    def mag(m, part, k):
        m.cyl("z", (7.6, 4.6), 2.7 * k, 7.0, 9.0, "dark_paint" if part == "mag" else "olive", sides=16, part=part)
        m.box([6.4, 6.6, 7.2], [8.8, 7.7, 8.8], "dark_paint", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.15)
    mag_dir(m, 0)
    m.mount("grip", [0.6, 6.2, 8])
    m.mount("sight", [-3.6, 12.6, 8])
    m.mount("muzzle", [20.6, 10.0, 8])
    m.mount("scope", [5.0, 11.45, 8])
    m.mount("under", [12.0, 7.6, 8])
    m.mount("side", [12.0, 9.5, 9.1])
    return m


@gun("double_barrel")
def double_barrel():
    m = Model("double_barrel", density=4.5)
    classic_stock(m, -15.0, -0.6, 10.0, 5.0, 1.55, mat="wood_light", plate="rubber")
    # action (engraved, bright)
    block(m, -1.0, 2.6, 8.4, 10.8, 1.75, "bright", deco=("panel",))
    m.box([-0.6, 10.8, 7.7], [0.8, 11.2, 8.3], "bright", rot=("y", 22.5, [0.0, 11.0, 8]))  # top lever
    trigger_guard(m, -0.4, 2.4, 7.0, 8.4)
    m.box([1.0, 7.4, 7.95], [1.3, 8.4, 8.25], "steel")

    def barrels(part):
        for dz in (-0.58, 0.58):
            m.cyl("x", (10.1, 8 + dz), 0.56, 2.6, 25.4, "blued", sides=8, part=part)
            m.box([25.38, 10.1 - 0.28, 8 + dz - 0.28], [25.45, 10.1 + 0.28, 8 + dz + 0.28], "black", part=part)
        m.box([2.6, 10.6, 7.85], [25.3, 10.9, 8.15], "blued", part=part, deco=("rail",))
        m.box([24.8, 10.9, 7.85], [25.1, 11.15, 8.15], "chrome", part=part)
        block(m, 3.0, 11.0, 8.5, 9.6, 1.5, "wood_light", part=part, deco=("grip",))
        block(m, 2.6, 3.4, 8.8, 10.7, 1.7, "bright", part=part)
    barrels("break")

    def mag(m, part, k):
        for dz in (-0.58, 0.58):
            m.box([2.0, 9.6, 8 + dz - 0.48], [3.1, 10.6, 8 + dz + 0.48], "red", part=part)
            m.box([2.9, 9.65, 8 + dz - 0.5], [3.2, 10.55, 8 + dz + 0.5], "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [-0.6, 0.8, 0.0])
    m.mount("hinge", [2.6, 8.4, 8])
    m.mount("grip", [-0.2, 7.2, 8])
    m.mount("sight", [1.0, 11.15, 8])
    m.mount("muzzle", [25.45, 10.1, 8])
    m.mount("scope", [6.0, 10.9, 8])
    m.mount("under", [14.0, 8.4, 8])
    m.mount("side", [14.0, 10.1, 9.1])
    return m


# =============================================================================== SNIPERS
@gun("m24")
def m24():
    m = Model("m24", density=4)
    # OD synthetic stock
    block(m, -15.0, -14.4, 4.8, 9.9, 1.75, "rubber")
    block(m, -14.4, -8.0, 5.0, 9.8, 1.6, "olive", deco=("panel",))
    m.box([-8.2, 5.0, 7.25], [-2.0, 7.4, 8.75], "olive", rot=("z", 22.5, [-8.2, 5.0, 8]))
    block(m, -8.2, 0.2, 7.6, 9.8, 1.55, "olive")
    block(m, -12.0, -6.0, 9.8, 10.4, 1.3, "olive")                       # cheek
    grip(m, -1.4, 8.0, length=3.4, depth=1.9, w=1.5, ang=-22.5, mat="olive", deco=("grip",))
    block(m, 0.2, 18.5, 7.6, 9.8, 1.7, "olive", deco=("lines:1",))
    m.box([12.0, 7.0, 7.3], [18.4, 7.8, 8.7], "olive")
    trigger_guard(m, 0.6, 3.6, 6.6, 7.65)
    # round receiver + bolt
    m.cyl("x", (10.25, 8), 0.95, -0.6, 9.4, "blued", sides=16)
    rail(m, -0.4, 9.2, 11.05, w=1.0)
    m.box([0.6, 9.9, 8.7], [1.2, 10.5, 10.0], "blued")
    m.cyl("y", (1.0, 10.1), 0.38, 9.0, 9.8, "black", sides=8)            # bolt knob
    # heavy barrel
    m.cyl("x", (10.25, 8), 0.62, 9.4, 20.0, "blued", sides=8)
    barrel(m, 20.0, 30.0, 10.25, 0.5)
    # folded bipod
    for dz in (-0.5, 0.5):
        m.box([14.0, 6.8, 8 + dz - 0.18], [21.0, 7.15, 8 + dz + 0.18], "steel")
    block(m, 15.4, 16.6, 7.0, 7.6, 1.6, "steel")
    sling_loop(m, -11.0, 5.2)

    def mag(m, part, k):
        m.box([2.6, 7.6 - 1.6 * k, 7.45], [5.4, 7.7, 8.55], "steel", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.8)
    mag_dir(m, 0)
    m.mount("grip", [-1.6, 6.4, 8])
    m.mount("sight", [2.0, 11.5, 8])
    m.mount("muzzle", [30.0, 10.25, 8])
    m.mount("scope", [4.0, 11.5, 8])
    m.mount("under", [17.0, 7.0, 8])
    m.mount("side", [17.0, 8.7, 8.85])
    return m


@gun("svd")
def svd():
    m = Model("svd", density=4)
    # skeleton thumbhole stock
    block(m, -15.0, -14.4, 4.6, 10.4, 1.6, "rubber")
    block(m, -14.4, -2.0, 9.2, 10.3, 1.4, "wood", deco=("lines:1",))
    block(m, -14.4, -12.4, 4.8, 9.2, 1.4, "wood")
    m.box([-12.4, 4.8, 7.3], [-4.0, 6.0, 8.7], "wood", rot=("z", 22.5, [-12.4, 4.8, 8]))
    block(m, -6.0, -2.0, 6.0, 9.2, 1.4, "wood", deco=("grip",))
    m.box([-11.4, 10.3, 7.4], [-6.0, 10.9, 8.6], "wood")                 # cheek rest
    # receiver
    block(m, -2.0, 10.0, 7.8, 10.4, 1.55, "gunmetal", deco=("port", "rivets"))
    m.cyl("x", (10.4, 8), 0.58, -1.6, 9.8, "gunmetal", sides=8)
    m.box([8.6, 9.0, 8.75], [9.6, 9.5, 9.6], "bright")
    trigger_guard(m, 0.0, 4.0, 6.3, 7.8)
    # side scope rail (left)
    block(m, 2.0, 8.0, 8.8, 9.8, 1.9, "gunmetal")
    # vented wood handguard
    block(m, 10.0, 17.0, 8.0, 10.2, 1.9, "wood", deco=("slots:3",))
    block(m, 17.0, 17.6, 8.2, 10.4, 1.8, "steel")
    barrel(m, 17.6, 29.6, 9.1, 0.46, muzzle=False)
    tube(m, 17.6, 20.6, 10.4, 0.36, "gunmetal")
    front_post(m, 28.2, 9.55, h=1.6)
    flash_hider(m, 29.6, 31.2, 9.1, 0.55)
    rear_sight(m, 10.6, 10.4)

    def mag(m, part, k):
        chain(m, 7.0, 7.8, [(2.6 * k, 0.0), (1.4 * k, 22.5)], 2.4, 1.1, "steel" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.6)
    mag_dir(m, 8)
    m.mount("grip", [-3.4, 6.6, 8])
    m.mount("sight", [10.6, 11.4, 8])
    m.mount("muzzle", [31.2, 9.1, 8])
    m.mount("scope", [4.0, 11.0, 8])
    m.mount("under", [14.0, 8.0, 8])
    m.mount("side", [14.0, 9.1, 9.0])
    return m


@gun("m107")
def m107():
    m = Model("m107", density=4)
    block(m, -6.0, 16.0, 9.0, 11.4, 2.2, "dark_paint", deco=("panel", "rivets", "text:CAL .50"))
    rail(m, -5.6, 15.8, 11.4, w=1.2)
    block(m, -4.0, 6.0, 7.5, 9.0, 2.0, "dark_paint")
    grip(m, -1.8, 7.8, length=4.2, depth=1.9, w=1.5, mat="polymer")
    trigger_guard(m, -1.4, 2.0, 6.2, 7.45)
    # stock w/ monopod
    block(m, -14.0, -6.0, 8.6, 11.0, 1.9, "dark_paint")
    block(m, -14.0, -11.0, 5.0, 8.6, 1.9, "dark_paint")
    block(m, -14.6, -14.0, 4.8, 11.2, 2.1, "rubber")
    tube(m, -9.0, -6.0, 9.8, 0.55, "steel")
    m.box([-10.6, 5.6, 7.7], [-10.0, 8.6, 8.3], "steel")
    # fluted heavy barrel inside a shroud
    m.cyl("x", (10.2, 8), 1.05, 16.0, 21.0, "dark_paint", sides=8, deco=("vslots",))
    m.cyl("x", (10.2, 8), 0.68, 21.0, 29.0, "blued", sides=8, deco=("vlines:3",))
    # huge muzzle brake
    block(m, 29.0, 31.8, 9.3, 11.1, 2.8, "blued", deco=("vslots",))
    m.box([31.75, 9.9, 7.6], [31.85, 10.5, 8.4], "black")
    # bipod (folded)
    for dz in (-0.7, 0.7):
        m.box([16.0, 7.8, 8 + dz - 0.22], [25.0, 8.25, 8 + dz + 0.22], "steel")
    block(m, 15.6, 16.8, 8.0, 9.0, 2.0, "steel")
    m.box([12.2, 10.0, 9.1], [13.6, 10.8, 10.2], "bright")   # charging handle

    def mag(m, part, k):
        m.box([1.8, 7.6 - 3.8 * k, 7.15], [5.6, 7.6, 8.85], "dark_paint" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.45)
    mag_dir(m, 0)
    m.mount("grip", [-2.2, 6.2, 8])
    m.mount("sight", [0.0, 12.4, 8])
    m.mount("muzzle", [31.8, 10.2, 8])
    m.mount("scope", [3.5, 11.85, 8])
    m.mount("under", [20.0, 9.0, 8])
    m.mount("side", [18.0, 10.2, 9.1])
    return m


# =============================================================================== HEAVY
@gun("m249")
def m249():
    m = Model("m249", density=4)
    block(m, -2.0, 12.0, 8.0, 11.0, 2.0, "dark_paint", deco=("rivets", "panel", "text:M249"))
    block(m, 1.8, 9.6, 11.0, 11.7, 1.9, "dark_paint", deco=("lines:1",))   # feed cover
    rail(m, 2.4, 9.0, 11.7, w=1.1)
    m.box([1.0, 9.6, 6.0], [7.2, 10.6, 7.05], "dark_paint")                # feed tray (left)
    # handguard + heat shield
    block(m, 12.0, 18.0, 8.4, 10.6, 2.0, "polymer", deco=("slots:3",))
    m.cyl("x", (10.5, 8), 0.55, 12.0, 26.0, "blued", sides=8)
    barrel(m, 26.0, 28.0, 10.5, 0.55, muzzle=False)
    flash_hider(m, 28.0, 29.4, 10.5, 0.62)
    m.box([18.0, 11.0, 7.4], [22.0, 11.6, 8.6], "blued", deco=("vslots",))  # heat shield
    tube(m, 18.0, 24.0, 9.3, 0.4, "blued")                                  # gas tube
    # carry handle
    block(m, 17.6, 18.2, 11.0, 12.6, 0.8, "polymer")
    block(m, 17.6, 21.0, 12.4, 13.0, 0.8, "polymer")
    # folded bipod
    for dz in (-0.55, 0.55):
        m.box([19.0, 8.4, 8 + dz - 0.2], [26.0, 8.8, 8 + dz + 0.2], "steel")
    front_post(m, 26.6, 11.05, h=1.0)
    # stock + grip
    block(m, -12.0, -2.0, 8.2, 10.9, 1.7, "polymer", deco=("panel",))
    m.box([-12.0, 5.4, 7.2], [-5.0, 7.4, 8.8], "polymer", rot=("z", 22.5, [-12.0, 5.4, 8]))
    block(m, -12.0, -9.0, 5.4, 8.6, 1.6, "polymer")
    block(m, -12.6, -12.0, 5.2, 11.0, 1.8, "rubber")
    grip(m, -0.4, 8.0, length=4.2, depth=1.8, w=1.45, mat="polymer")
    trigger_guard(m, 0.0, 3.2, 6.5, 7.85)

    def mag(m, part, k):
        m.box([3.4, 7.8 - 4.6 * k, 5.6], [8.6, 8.0, 8.7], "canvas_green" if part == "mag" else "olive", part=part, deco=("panel", "stencil:5.56"))
        for i in range(5):  # belt rounds going into the tray
            x = 3.6 + i * 1.1
            m.box([x, 8.0, 6.2], [x + 0.6, 9.6, 6.9], "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    mag_dir(m, 0)
    m.mount("grip", [-0.8, 6.4, 8])
    m.mount("sight", [6.0, 12.0, 8])
    m.mount("muzzle", [29.4, 10.5, 8])
    m.mount("scope", [5.5, 12.15, 8])
    m.mount("under", [15.0, 8.4, 8])
    m.mount("side", [15.0, 9.5, 9.0])
    return m


@gun("minigun")
def minigun():
    m = Model("minigun", density=4)
    # motor housing
    m.cyl("x", (9.4, 8), 1.7, -4.0, 6.0, "dark_paint", sides=16)
    block(m, -4.0, 6.0, 7.2, 9.4, 3.0, "dark_paint", deco=("rivets", "text:M134"))
    m.cyl("x", (8.4, 10.3), 0.75, -3.0, 3.5, "steel", sides=8)               # motor (right)
    # spade grips (rear)
    for dz in (-1.2, 1.2):
        block(m, -7.0, -6.2, 6.6, 11.6, 0.6, "rubber")
        m.box([-7.0, 6.6, 8 + dz - 0.3], [-6.2, 11.6, 8 + dz + 0.3], "rubber", deco=("grip",))
    block(m, -6.4, -4.0, 8.0, 10.6, 2.4, "steel")
    m.box([-6.8, 9.8, 7.7], [-6.0, 10.4, 8.3], "red")                          # trigger button
    # top handle
    block(m, -2.0, 4.0, 12.0, 12.6, 0.8, "polymer")
    block(m, -2.0, -1.4, 11.0, 12.0, 0.8, "polymer")
    block(m, 3.4, 4.0, 11.0, 12.0, 0.8, "polymer")
    # rotating barrel cluster
    cy, cz = 9.4, 8.0
    for i in range(6):
        a = math.radians(i * 60 + 30)
        y, z = cy + math.sin(a) * 1.0, cz + math.cos(a) * 1.0
        m.cyl("x", (y, z), 0.3, 6.0, 30.0, "blued", sides=4, part="spin")
        m.box([29.95, y - 0.15, z - 0.15], [30.05, y + 0.15, z + 0.15], "black", part="spin")
    tube(m, 6.0, 30.0, cy, 0.38, "steel", part="spin")
    for x in (6.0, 15.0, 27.0):
        m.cyl("x", (cy, cz), 1.55, x, x + 1.0, "steel", sides=8, part="spin")
    # ammo feed
    def mag(m, part, k):
        m.box([-3.0, 2.0 - 2.0 * (k - 1), 6.2], [4.0, 7.2, 9.8], "od_paint" if part == "mag" else "olive", part=part, deco=("panel", "stencil:7.62"))
        m.box([-2.6, 7.2, 6.6], [-1.2, 9.0, 7.4], "canvas_green", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.6)
    mag_dir(m, 0)
    m.mount("spin", [0.0, cy, cz])
    m.mount("grip", [-6.6, 8.0, 8])
    m.mount("sight", [1.0, 13.2, 8])
    m.mount("muzzle", [30.0, 9.4, 8])
    m.mount("scope", [1.0, 12.6, 8])
    m.mount("under", [10.0, 7.7, 8])
    m.mount("side", [3.0, 9.4, 9.5])
    return m


# =============================================================================== EXPLOSIVES / SPECIAL
@gun("rpg7")
def rpg7():
    m = Model("rpg7", density=4)
    m.cyl("x", (10.0, 8), 0.8, -10.0, 16.0, "olive", sides=16)
    # flared venturi at the back
    m.cyl("x", (10.0, 8), 1.0, -11.6, -10.0, "olive", sides=16)
    m.cyl("x", (10.0, 8), 1.25, -13.0, -11.6, "olive", sides=16)
    m.box([-13.05, 9.2, 7.2], [-12.95, 10.8, 8.8], "black")
    # wooden heat guards
    m.cyl("x", (10.0, 8), 1.08, -6.0, -1.0, "wood_light", sides=16, deco=("vlines:5",))
    m.cyl("x", (10.0, 8), 1.08, 2.0, 7.0, "wood_light", sides=16, deco=("vlines:5",))
    # grips + trigger
    grip(m, 0.6, 9.2, length=3.8, depth=1.6, w=1.3, mat="wood_red")
    trigger_guard(m, 1.0, 3.6, 7.6, 9.2)
    grip(m, 8.4, 9.2, length=3.4, depth=1.5, w=1.3, ang=0.0, mat="wood_red")
    # sights + optic
    front_post(m, 14.4, 10.8, h=1.1)
    block(m, 4.0, 5.0, 10.8, 11.8, 0.8, "olive")
    block(m, 0.0, 4.6, 11.2, 12.6, 1.0, "olive")
    m.cyl("x", (12.0, 6.7), 0.55, -0.6, 5.0, "olive", sides=8)
    m.box([0.2, 10.4, 7.1], [3.0, 11.6, 7.5], "olive")
    # front muzzle ring
    m.cyl("x", (10.0, 8), 0.95, 15.6, 16.4, "steel", sides=16)

    def mag(m, part, k):  # PG-7 rocket
        m.cyl("x", (10.0, 8), 0.45, 15.5, 18.0, "steel", sides=8, part=part)
        m.cyl("x", (10.0, 8), 1.55, 18.0, 22.4, "od_paint" if part == "mag" else "desert_paint", sides=16, part=part)
        m.cyl("x", (10.0, 8), 1.2, 22.4, 24.0, "od_paint" if part == "mag" else "desert_paint", sides=16, part=part)
        m.cyl("x", (10.0, 8), 0.8, 24.0, 25.4, "od_paint" if part == "mag" else "desert_paint", sides=8, part=part)
        m.cyl("x", (10.0, 8), 0.35, 25.4, 27.0, "steel", sides=8, part=part)
        m.cyl("x", (10.0, 8), 1.58, 19.0, 19.5, "black", sides=16, part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [1.0, 0.0, 0.0])
    m.mount("grip", [0.2, 7.4, 8])
    m.mount("sight", [4.5, 12.0, 8])
    m.mount("muzzle", [27.0, 10.0, 8])
    m.mount("scope", [2.3, 12.6, 8])
    m.mount("under", [12.0, 9.2, 8])
    m.mount("side", [12.0, 10.0, 8.8])
    return m


@gun("m79")
def m79():
    m = Model("m79", density=4.5)
    classic_stock(m, -13.0, 0.4, 10.0, 5.2, 1.6, mat="wood_light", plate="rubber")
    block(m, 0.0, 3.6, 8.4, 10.8, 1.75, "blued", deco=("text:40MM",))
    trigger_guard(m, 0.4, 3.2, 7.0, 8.4)
    m.box([0.2, 10.8, 7.7], [1.4, 11.2, 8.3], "blued")

    def barrel_part(part):
        m.cyl("x", (10.0, 8), 1.25, 3.6, 16.0, "blued", sides=16, part=part)
        m.box([15.98, 9.1, 7.1], [16.05, 10.9, 8.9], "black", part=part)
        block(m, 4.2, 11.0, 8.0, 9.4, 1.9, "wood_light", part=part, deco=("grip",))
        block(m, 6.2, 7.0, 11.2, 13.0, 1.2, "blued", part=part)       # leaf sight
        m.box([6.3, 12.0, 7.5], [6.9, 12.8, 8.5], "steel", part=part)
        front_post(m, 15.2, 11.2, h=0.6, part=part)
    barrel_part("break")

    def mag(m, part, k):
        m.cyl("x", (10.0, 8), 1.05, 3.0, 4.4, "brass", sides=16, part=part)
        m.cyl("x", (10.0, 8), 0.95, 4.4, 5.6, "olive", sides=16, part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [-0.8, 0.6, 0.0])
    m.mount("hinge", [3.6, 8.6, 8])
    m.mount("grip", [0.2, 7.2, 8])
    m.mount("sight", [6.6, 13.0, 8])
    m.mount("muzzle", [16.0, 10.0, 8])
    m.mount("scope", [8.0, 11.25, 8])
    m.mount("under", [8.0, 8.0, 8])
    m.mount("side", [8.0, 10.0, 9.3])
    return m


@gun("flamethrower")
def flamethrower():
    m = Model("flamethrower", density=4)
    # wand
    m.cyl("x", (10.4, 8), 0.55, -4.0, 22.0, "dark_paint", sides=8)
    m.cyl("x", (10.4, 8), 0.85, 19.0, 22.0, "steel", sides=8, deco=("vslots",))
    m.cyl("x", (10.4, 8), 0.7, 22.0, 25.2, "blued", sides=8)
    m.box([25.15, 10.0, 7.6], [25.25, 10.8, 8.4], "black")
    # igniter + pilot light
    m.cyl("x", (11.5, 8), 0.35, 20.0, 24.6, "steel", sides=8)
    m.box([24.6, 11.25, 7.75], [25.0, 11.75, 8.25], "glow_orange", light=True)
    # heat shield
    m.box([12.0, 11.0, 7.3], [19.0, 11.4, 8.7], "steel", deco=("vslots",))
    # grips
    grip(m, 0.0, 9.85, length=4.0, depth=1.8, w=1.4, mat="rubber")
    trigger_guard(m, 0.4, 3.4, 8.0, 9.85)
    grip(m, 10.0, 9.85, length=3.6, depth=1.6, w=1.4, ang=0.0, mat="rubber")
    m.box([-4.0, 9.6, 7.3], [-2.0, 11.2, 8.7], "dark_paint")
    m.box([12.6, 10.0, 8.5], [13.6, 10.8, 9.3], "red")       # valve knob

    def mag(m, part, k):   # fuel tanks under the wand
        m.cyl("x", (6.6, 8), 1.55, -9.0, 5.0 + (k - 1) * 4, "red" if part == "mag" else "yellow", sides=16, part=part)
        m.cyl("x", (6.6, 8), 1.58, -2.0, -1.2, "hazard", sides=16, part=part)
        m.cyl("x", (6.6, 8), 1.0, 5.0 + (k - 1) * 4, 5.8 + (k - 1) * 4, "steel", sides=8, part=part)
        m.cyl("y", (4.0, 8.0), 0.3, 8.2, 9.6, "rubber", sides=4, part=part)   # hose
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    mag_dir(m, 0)
    m.mount("grip", [-0.4, 8.0, 8])
    m.mount("sight", [0.0, 12.0, 8])
    m.mount("muzzle", [25.2, 10.4, 8])
    m.mount("scope", [6.0, 11.0, 8])
    m.mount("under", [16.0, 9.8, 8])
    m.mount("side", [16.0, 10.4, 8.6])
    return m
