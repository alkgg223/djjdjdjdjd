"""v2.0 guns: 15 new weapons and the thermobaric RPG-7. Same conventions as guns.py (barrel along +X, centre plane
z = 8, parts 'body', 'mag', 'mag_ext', mount points)."""
import math

from modelkit import Model
from parts import *  # noqa
from guns import gun, classic_stock, mag_dir


def mounts(m, grip_, sight, muzzle, scope, under, side):
    m.mount("grip", grip_)
    m.mount("sight", sight)
    m.mount("muzzle", muzzle)
    m.mount("scope", scope)
    m.mount("under", under)
    m.mount("side", side)


# =============================================================================== PISTOLS
@gun("aps")
def aps():
    """Stechkin APS: long slide automatic pistol, fire selector, wooden holster stock lug"""
    m = Model("aps", density=8)
    block(m, -0.4, 10.4, 9.0, 10.9, 1.2, "blued", deco=("serr", "port", "text:APS"))
    m.cyl("x", (10.55, 8), 0.42, 0.0, 10.2, "blued", sides=8)
    front_post(m, 9.8, 10.9, h=0.45, ears=False)
    rear_sight(m, 0.4, 10.9)
    block(m, 0.0, 9.0, 8.15, 9.05, 1.15, "blued")
    m.box([-0.6, 9.9, 7.65], [0.2, 10.8, 8.35], "steel", rot=("z", 22.5, [-0.2, 10.3, 8]))
    m.box([0.6, 9.2, 8.55], [2.0, 9.6, 8.75], "steel")                     # selector
    grip(m, 1.4, 8.6, length=5.8, depth=2.3, w=1.2, mat="blued", deco=())
    grip(m, 1.4, 8.4, length=4.8, depth=2.0, w=1.5, mat="wood_red", deco=("grip",))
    m.box([-1.3, 3.4, 7.4], [-0.5, 4.6, 8.6], "steel")                     # stock lug
    trigger_guard(m, 3.4, 6.6, 6.9, 8.2, w=0.45)

    def mag(m, part, k):
        grip(m, 1.4, 8.1, length=6.0 * k, depth=1.6, w=0.95, mat="steel", deco=("ribs",), part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.3)
    mag_dir(m, -22.5)
    mounts(m, [1.7, 7.0, 8], [0.4, 11.45, 8], [10.4, 10.0, 8], [3.5, 10.9, 8], [7.4, 8.15, 8], [7.0, 8.6, 8.6])
    return m


@gun("m9")
def m9():
    """M9: open-top slide showing the barrel, ambidextrous safety, two-tone grips"""
    m = Model("m9", density=8)
    block(m, 0.0, 9.6, 9.6, 10.9, 1.2, "blued", deco=("serr", "text:M9"))
    block(m, 4.0, 8.6, 9.0, 9.6, 1.2, "blued")
    m.cyl("x", (9.35, 8), 0.42, 3.8, 9.7, "bright", sides=8)              # exposed barrel
    front_post(m, 9.0, 10.9, h=0.4, ears=False)
    rear_sight(m, 0.7, 10.9)
    block(m, 0.0, 4.0, 9.0, 9.6, 1.2, "blued", deco=("port",))
    for z in (7.3, 8.5):
        m.box([0.6, 10.2, z], [1.4, 10.6, z + 0.2], "steel")               # safety levers
    block(m, 0.3, 8.4, 8.2, 9.05, 1.15, "dark_paint")
    m.box([-0.6, 9.8, 7.7], [0.2, 10.7, 8.3], "steel", rot=("z", 22.5, [-0.2, 10.2, 8]))
    grip(m, 1.6, 8.6, length=5.2, depth=2.35, w=1.15, mat="dark_paint", deco=())
    grip(m, 1.6, 8.4, length=4.4, depth=2.0, w=1.45, mat="polymer", deco=("grip",))
    trigger_guard(m, 3.6, 6.8, 6.8, 8.2, w=0.45, mat="dark_paint")

    def mag(m, part, k):
        grip(m, 1.6, 8.1, length=5.4 * k, depth=1.6, w=0.95, mat="steel", deco=("ribs",), part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.3)
    mag_dir(m, -22.5)
    mounts(m, [1.9, 7.0, 8], [0.7, 11.45, 8], [9.7, 9.35, 8], [3.0, 10.9, 8], [7.0, 8.2, 8], [6.8, 8.6, 8.6])
    return m


# =============================================================================== SMGs
@gun("ppsh41")
def ppsh41():
    """PPSh-41: wooden stock, ventilated barrel jacket with the slanted compensator, 71-round drum"""
    m = Model("ppsh41", density=4.5)
    classic_stock(m, -14.0, 0.4, 10.2, 5.4, 1.6, mat="wood", plate="steel")
    block(m, 0.0, 9.0, 8.8, 10.8, 1.6, "blued", deco=("port",))
    m.cyl("x", (10.0, 8), 1.0, 9.0, 18.4, "blued", sides=12, deco=("vslots",))
    m.box([18.2, 9.0, 7.0], [18.9, 11.1, 9.0], "blued", rot=("z", -22.5, [18.5, 10.0, 8]))
    barrel(m, 9.0, 18.6, 10.0, 0.32, muzzle=False)
    front_post(m, 17.4, 11.0, h=0.7)
    rear_sight(m, 7.4, 10.8)
    block(m, 0.4, 7.4, 7.6, 8.8, 1.4, "wood")
    trigger_guard(m, 1.4, 4.6, 6.4, 7.6)

    def mag(m, part, k):
        m.cyl("z", (9.2 - 2.8 * k, 7.6), 2.8 * k, 7.0, 9.0, "blued" if part == "mag" else "steel", sides=16, part=part)
        m.box([7.3, 7.0, 7.3], [8.5, 8.8, 8.7], "blued", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.1)
    mag_dir(m, 0)
    mounts(m, [0.6, 7.0, 8], [7.4, 11.5, 8], [18.9, 10.0, 8], [4.0, 10.8, 8], [13.0, 9.0, 8], [13.0, 10.0, 9.0])
    return m


@gun("bizon")
def bizon():
    """PP-19 Bizon: AK-style receiver and folding stock with the long helical magazine under the handguard"""
    m = Model("bizon", density=5)
    block(m, -1.0, 9.6, 8.4, 10.6, 1.5, "gunmetal", deco=("rivets", "port", "text:PP-19"))
    m.cyl("x", (10.55, 8), 0.6, -0.8, 9.4, "gunmetal", sides=8)
    rear_sight(m, 8.4, 10.9)
    block(m, 9.6, 12.6, 9.1, 10.4, 1.6, "polymer", deco=("lines:2",))
    barrel(m, 12.6, 16.0, 9.75, 0.42, muzzle=False)
    front_post(m, 15.0, 10.2, h=1.0)
    block(m, 14.4, 15.6, 9.0, 10.3, 1.2, "gunmetal")
    flash_hider(m, 16.0, 17.0, 9.75, 0.48)
    grip(m, 1.6, 8.4, length=4.4, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 2.2, 5.4, 6.8, 8.3)
    for y in (10.0, 8.8):
        tube(m, -10.0, -1.0, y, 0.22, "steel")
    block(m, -10.8, -10.0, 7.6, 10.6, 1.5, "rubber")

    def mag(m, part, k):
        m.cyl("x", (8.0, 8), 1.05, 5.4, 5.4 + 10.0 * k, "polymer" if part == "mag" else "tan", sides=12, part=part, deco=("ribs",))
        m.cyl("x", (8.0, 8), 1.12, 5.4 + 10.0 * k - 0.6, 5.4 + 10.0 * k, "dark_paint", sides=12, part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    mag_dir(m, 60)
    mounts(m, [1.4, 6.8, 8], [8.4, 11.5, 8], [17.0, 9.75, 8], [3.0, 10.6, 8], [13.0, 9.0, 8], [11.0, 9.75, 8.9])
    return m


@gun("uzi")
def uzi():
    """Uzi: boxy stamped receiver, magazine through the pistol grip, folding wire stock"""
    m = Model("uzi", density=6)
    block(m, -0.6, 11.0, 8.4, 10.8, 2.0, "gunmetal", deco=("rivets", "lines:2", "text:UZI"))
    m.box([2.0, 10.8, 7.0], [5.0, 11.2, 9.0], "gunmetal", deco=("rivets",))   # cocking knob cover
    m.cyl("y", (3.5, 8.0), 0.35, 11.2, 11.7, "black", sides=8)
    barrel(m, 11.0, 13.2, 9.6, 0.42)
    block(m, 10.4, 11.4, 10.8, 11.6, 1.2, "gunmetal")
    front_post(m, 10.9, 11.6, h=0.5, ears=True)
    rear_sight(m, 0.2, 10.8)
    block(m, 6.0, 10.0, 7.4, 8.4, 1.6, "polymer", deco=("grip",))
    grip(m, 3.4, 8.4, length=4.4, depth=2.0, w=1.5, ang=0.0, mat="polymer")
    trigger_guard(m, 2.4, 5.8, 6.2, 8.2)
    for y in (10.2, 8.8):
        tube(m, -8.6, -0.6, y, 0.18, "steel")
    block(m, -9.2, -8.6, 7.4, 10.6, 1.6, "steel")

    def mag(m, part, k):
        m.box([3.6, 8.2 - 6.4 * k, 7.45], [5.2, 8.4, 8.55], "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 0)
    mounts(m, [3.4, 6.6, 8], [0.2, 11.5, 8], [13.2, 9.6, 8], [4.0, 11.2, 8], [8.0, 7.4, 8], [8.0, 9.6, 9.0])
    return m


# =============================================================================== RIFLES
@gun("m14")
def m14():
    """M14 EBR: chassis stock, rail-covered aluminium handguard, long barrel and muzzle brake, 20-round box"""
    m = Model("m14", density=4)
    block(m, -14.0, -13.4, 5.0, 10.4, 1.7, "rubber")
    block(m, -13.4, -4.0, 8.6, 10.4, 1.4, "dark_paint", deco=("panel",))
    block(m, -12.0, -8.0, 10.4, 11.2, 1.2, "dark_paint")                   # cheek riser
    for y in (8.2, 6.4):
        tube(m, -13.4, -1.0, y, 0.3, "steel")
    block(m, -4.0, 12.0, 8.0, 10.4, 1.8, "dark_paint", deco=("rivets",))
    rail(m, -2.0, 18.0, 10.9, w=1.15)
    m.cyl("x", (10.0, 8), 0.75, 0.0, 9.0, "gunmetal", sides=8)
    m.box([4.0, 10.0, 8.7], [6.4, 10.5, 9.6], "steel")                     # op rod handle
    block(m, 12.0, 20.0, 8.2, 10.6, 2.0, "dark_paint", deco=("slots:3",))
    barrel(m, 20.0, 26.4, 9.5, 0.5, muzzle=False)
    muzzle_brake(m, 26.4, 28.0, 9.5, 0.7)
    front_post(m, 19.4, 11.4, h=0.7)
    grip(m, 0.2, 8.0, length=4.2, depth=1.9, w=1.5, mat="polymer")
    trigger_guard(m, 0.8, 3.8, 6.6, 7.95)

    def mag(m, part, k):
        m.box([4.8, 8.0 - 3.6 * k, 7.35], [7.4, 8.1, 8.65], "steel" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.5)
    mag_dir(m, 5)
    mounts(m, [0.0, 6.4, 8], [0.0, 11.9, 8], [28.0, 9.5, 8], [3.0, 11.35, 8], [16.0, 8.2, 8], [16.0, 9.4, 9.0])
    return m


@gun("aks74u")
def aks74u():
    """AKS-74U: short AK carbine, side-folding stock, the big cone flash suppressor, orange-brown plum furniture"""
    m = Model("aks74u", density=5)
    for y in (10.0, 7.6):
        tube(m, -10.6, -1.4, y, 0.22, "steel")
    block(m, -11.2, -10.4, 6.8, 10.6, 1.4, "steel")
    block(m, -1.6, 10.6, 7.6, 10.2, 1.6, "gunmetal", deco=("rivets", "port"))
    m.cyl("x", (10.5, 8), 0.62, 0.2, 9.8, "gunmetal", sides=8)
    block(m, 9.0, 11.0, 10.2, 11.4, 1.3, "gunmetal")                      # hinged top cover sight block
    rear_sight(m, 9.6, 11.4)
    block(m, 10.6, 15.0, 7.6, 9.8, 2.0, "wood_red", deco=("lines:2",))
    block(m, 10.6, 15.0, 9.8, 10.8, 1.3, "wood_red")
    barrel(m, 15.0, 16.8, 8.9, 0.5, muzzle=False)
    block(m, 15.6, 16.6, 8.2, 10.6, 1.2, "gunmetal")
    front_post(m, 16.1, 10.6, h=0.8)
    m.cyl("x", (8.9, 8), 0.75, 16.8, 18.6, "gunmetal", sides=8)
    m.cyl("x", (8.9, 8), 0.95, 18.6, 19.0, "gunmetal", sides=8)
    grip(m, 4.4, 7.4, length=4.4, depth=1.9, w=1.45, mat="wood_red")
    trigger_guard(m, 4.2, 8.2, 5.9, 7.5)

    def mag(m, part, k):
        chain(m, 9.4, 7.6, [(2.8 * k, 0.0), (2.6 * k, 22.5), (2.4 * k, 22.5)], 2.7, 1.2,
              "polymer" if part == "mag" else "tan", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 15)
    mounts(m, [4.0, 6.0, 8], [9.6, 11.9, 8], [19.0, 8.9, 8], [4.0, 10.2, 8], [13.0, 7.6, 8], [13.0, 8.7, 9.0])
    return m


@gun("famas")
def famas():
    """FAMAS: bullpup with the long carry handle running the length of the gun"""
    m = Model("famas", density=4.5)
    block(m, -10.0, 9.0, 7.4, 10.6, 2.0, "dark_paint", deco=("panel", "text:FAMAS"))
    block(m, -10.6, -10.0, 7.0, 10.8, 2.0, "rubber")
    block(m, 9.0, 13.6, 7.8, 10.2, 1.8, "dark_paint", deco=("slots:2",))
    barrel(m, 13.6, 17.6, 9.2, 0.4, muzzle=False)
    flash_hider(m, 17.6, 18.8, 9.2, 0.5)
    # the long carry handle with the sights inside
    block(m, -6.0, 9.6, 12.2, 12.9, 1.0, "dark_paint")
    for x in (-6.0, 1.0, 9.0):
        block(m, x, x + 0.8, 10.6, 12.2, 0.9, "dark_paint")
    rear_sight(m, -5.4, 12.9)
    front_post(m, 8.8, 12.9, h=0.6)
    m.box([-1.0, 10.6, 8.8], [0.4, 11.4, 9.6], "steel")                    # charging lever
    grip(m, 3.0, 7.6, length=4.4, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 3.0, 8.4, 6.2, 7.5, mat="dark_paint")

    def mag(m, part, k):
        stanag_mag(m, -6.4, 7.4, length=5.2 * k, part=part, mat="steel" if part == "mag" else "tan")
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 10)
    mounts(m, [2.8, 6.2, 8], [-5.4, 13.4, 8], [18.8, 9.2, 8], [1.0, 12.9, 8], [11.0, 7.8, 8], [11.0, 9.0, 9.0])
    return m


@gun("sks")
def sks():
    """SKS: full wooden stock, fixed 10-round magazine in front of the guard, folding bayonet"""
    m = Model("sks", density=4)
    classic_stock(m, -15.0, -1.0, 9.6, 4.6, 1.5, mat="wood_light")
    block(m, -1.2, 9.6, 7.6, 10.2, 1.5, "wood_light")
    block(m, -1.0, 9.0, 10.2, 10.9, 1.2, "blued", deco=("port",))
    m.cyl("x", (10.6, 8), 0.5, 0.0, 9.0, "blued", sides=8)
    m.box([6.6, 10.4, 8.6], [7.6, 10.8, 9.5], "steel")
    rear_sight(m, 9.4, 10.9)
    block(m, 9.6, 19.0, 8.0, 10.0, 1.6, "wood_light", deco=("lines:1",))
    tube(m, 9.6, 19.6, 10.4, 0.42, "blued")                               # gas tube
    barrel(m, 19.0, 26.4, 9.1, 0.42)
    front_post(m, 25.4, 9.6, h=0.9)
    m.box([18.4, 8.3, 7.65], [24.6, 8.7, 8.35], "steel")                   # folded bayonet
    grip(m, 1.4, 7.6, length=3.4, depth=1.6, w=1.4, ang=-45.0, mat="wood_light", deco=())
    trigger_guard(m, 1.6, 4.6, 6.4, 7.6)
    sling_loop(m, -12.0, 5.0)

    def mag(m, part, k):
        m.box([5.0, 7.6 - 1.8 * k, 7.45], [7.8, 7.7, 8.55], "blued", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.6)
    mag_dir(m, 0)
    mounts(m, [1.0, 6.4, 8], [9.4, 11.5, 8], [26.4, 9.1, 8], [3.0, 10.9, 8], [15.0, 8.0, 8], [15.0, 9.0, 8.85])
    return m


# =============================================================================== PRECISION
@gun("mosin")
def mosin():
    """Mosin-Nagant: long wooden stock, the bent bolt handle, hooded front sight"""
    m = Model("mosin", density=4)
    classic_stock(m, -16.0, -1.6, 9.4, 4.4, 1.5, mat="wood_red")
    block(m, -1.8, 22.0, 8.0, 10.0, 1.6, "wood_red", deco=("lines:1",))
    block(m, 4.0, 21.6, 10.0, 10.6, 1.3, "wood_red")
    m.cyl("x", (10.4, 8), 0.75, -1.4, 5.0, "blued", sides=12)
    m.box([1.2, 9.4, 8.6], [1.6, 10.2, 10.4], "steel", rot=("x", -22.5, [1.4, 10.0, 8.6]))
    m.cyl("y", (1.4, 10.4), 0.42, 9.2, 9.8, "steel", sides=8)
    rear_sight(m, 5.4, 10.6)
    barrel(m, 22.0, 30.6, 9.0, 0.42)
    m.cyl("x", (9.0, 8), 0.6, 21.6, 22.2, "steel", sides=8)
    m.cyl("z", (29.8, 9.9), 0.5, 7.6, 8.4, "blued", sides=8)               # hooded front sight
    front_post(m, 29.8, 9.5, h=0.6, ears=False)
    trigger_guard(m, 0.6, 3.6, 6.8, 7.9)
    sling_loop(m, -13.0, 5.0)

    def mag(m, part, k):
        m.box([2.4, 7.9 - 1.4 * k, 7.4], [5.6, 8.0, 8.6], "blued", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.7)
    mag_dir(m, 0)
    mounts(m, [-1.2, 6.6, 8], [5.4, 11.2, 8], [30.6, 9.0, 8], [1.5, 11.2, 8], [16.0, 8.0, 8], [16.0, 9.0, 8.85])
    return m


@gun("vss")
def vss():
    """VSS Vintorez: skeleton wooden stock, the fat integral suppressor that is the whole barrel"""
    m = Model("vss", density=5)
    block(m, -12.0, -11.4, 5.0, 10.2, 1.5, "rubber")
    block(m, -11.4, -3.0, 9.0, 10.2, 1.4, "wood_red")
    m.box([-11.4, 5.2, 7.35], [-4.0, 6.4, 8.65], "wood_red", rot=("z", 22.5, [-11.4, 5.2, 8]))
    block(m, -4.0, -1.0, 6.4, 10.2, 1.4, "wood_red")
    block(m, -1.4, 8.6, 8.4, 10.6, 1.5, "gunmetal", deco=("rivets", "port"))
    m.cyl("x", (10.5, 8), 0.55, -1.0, 8.4, "gunmetal", sides=8)
    rail(m, 0.0, 6.0, 10.9, w=1.1)
    block(m, 8.6, 12.2, 8.6, 10.2, 1.7, "wood_red", deco=("lines:2",))
    m.cyl("x", (9.4, 8), 1.05, 12.0, 22.4, "black", sides=16, deco=("vslots",))
    m.cyl("x", (9.4, 8), 1.12, 21.8, 22.4, "gunmetal", sides=16)
    front_post(m, 21.2, 10.5, h=0.6)
    rear_sight(m, 7.8, 10.6)
    grip(m, 1.6, 8.4, length=4.2, depth=1.9, w=1.4, mat="polymer")
    trigger_guard(m, 1.8, 4.8, 6.9, 8.3)

    def mag(m, part, k):
        m.box([5.0, 8.4 - 2.8 * k, 7.45], [7.6, 8.5, 8.55], "polymer" if part == "mag" else "tan", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 5)
    mounts(m, [1.4, 6.8, 8], [7.8, 11.2, 8], [22.4, 9.4, 8], [2.4, 11.35, 8], [10.0, 8.6, 8], [10.0, 9.4, 8.9])
    return m


# =============================================================================== HEAVY
@gun("pkm")
def pkm():
    """PKM: skeleton stock, the feed cover, a fluted barrel with carrying handle, 100-round box on the right"""
    m = Model("pkm", density=4)
    block(m, -12.6, -12.0, 5.4, 10.8, 1.6, "rubber")
    block(m, -12.0, -3.0, 9.8, 10.8, 1.4, "wood_red")
    m.box([-12.0, 5.6, 7.35], [-4.0, 6.8, 8.65], "wood_red", rot=("z", 22.5, [-12.0, 5.6, 8]))
    block(m, -4.0, -1.0, 6.0, 10.8, 1.5, "wood_red")
    block(m, -1.6, 12.0, 8.2, 11.0, 1.9, "gunmetal", deco=("rivets", "panel", "text:PKM"))
    block(m, 0.0, 10.0, 11.0, 11.6, 1.8, "gunmetal", deco=("lines:1",))
    rear_sight(m, 9.6, 11.6)
    tube(m, 12.0, 22.0, 9.0, 0.45, "gunmetal")                              # gas tube
    m.cyl("x", (10.4, 8), 0.6, 12.0, 26.0, "blued", sides=8, deco=("vlines:6",))
    flash_hider(m, 26.0, 27.6, 10.4, 0.62)
    block(m, 14.8, 15.4, 11.0, 12.6, 0.8, "gunmetal")
    block(m, 14.8, 18.0, 12.4, 13.0, 0.8, "wood_red")                        # carry handle
    front_post(m, 25.0, 11.0, h=1.0)
    for dz in (-0.55, 0.55):
        m.box([18.0, 8.2, 8 + dz - 0.2], [24.6, 8.6, 8 + dz + 0.2], "steel")
    grip(m, 0.2, 8.2, length=4.2, depth=1.8, w=1.45, mat="wood_red")
    trigger_guard(m, 0.6, 3.6, 6.6, 8.1)

    def mag(m, part, k):
        m.box([3.0, 8.2 - 4.4 * k, 9.0], [8.6, 8.6, 11.6], "olive" if part == "mag" else "steel", part=part, deco=("panel",))
        for i in range(5):
            x = 3.2 + i * 1.1
            m.box([x, 8.6, 9.6], [x + 0.6, 10.2, 10.3], "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    mag_dir(m, 0)
    mounts(m, [-0.2, 6.6, 8], [9.6, 12.2, 8], [27.6, 10.4, 8], [4.0, 11.6, 8], [15.0, 8.6, 8], [15.0, 9.6, 9.0])
    return m


@gun("saiga12")
def saiga12():
    """Saiga-12: AK-pattern semi-automatic shotgun, folding stock, box magazine"""
    m = Model("saiga12", density=4.5)
    for y in (10.0, 7.8):
        tube(m, -11.0, -1.4, y, 0.24, "steel")
    block(m, -11.8, -11.0, 6.8, 10.8, 1.6, "rubber")
    block(m, -1.6, 11.2, 7.6, 10.4, 1.7, "gunmetal", deco=("rivets", "port", "text:SAIGA"))
    m.cyl("x", (10.6, 8), 0.65, 0.2, 10.8, "gunmetal", sides=8)
    rail(m, 0.0, 10.0, 11.25, w=1.15)
    block(m, 11.2, 18.0, 7.8, 10.2, 2.0, "polymer", deco=("lines:2",))
    barrel(m, 18.0, 23.0, 9.2, 0.62)
    front_post(m, 22.2, 9.8, h=1.0)
    tube(m, 11.2, 21.0, 10.5, 0.45, "gunmetal")
    grip(m, 4.4, 7.6, length=4.4, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 4.2, 8.2, 6.0, 7.5)

    def mag(m, part, k):
        chain(m, 9.8, 7.6, [(2.6 * k, 0.0), (2.4 * k, 22.5)], 3.2, 1.6, "polymer" if part == "mag" else "olive", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.5)
    mag_dir(m, 10)
    mounts(m, [4.0, 6.0, 8], [2.0, 11.9, 8], [23.0, 9.2, 8], [4.0, 11.7, 8], [15.0, 7.8, 8], [15.0, 9.0, 9.1])
    return m


# =============================================================================== EXPLOSIVES
@gun("mgl")
def mgl():
    """MGL: six-shot revolving 40 mm grenade launcher, rails, folding stock"""
    m = Model("mgl", density=4.5)
    for y in (10.4, 8.0):
        tube(m, -10.0, -1.0, y, 0.25, "steel")
    block(m, -10.8, -10.0, 7.0, 11.2, 1.8, "rubber")
    block(m, -1.4, 2.0, 7.6, 11.4, 2.0, "dark_paint", deco=("rivets",))
    grip(m, -0.4, 7.6, length=4.4, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 0.0, 3.2, 6.1, 7.5)

    def cyl_part(part):
        m.cyl("x", (9.5, 8), 2.6, 2.0, 7.8, "olive" if part == "spin" else "steel", sides=16, part=part, deco=("vlines:6",))
        for k in range(6):
            a = math.radians(k * 60)
            m.cyl("x", (9.5 + math.sin(a) * 1.6, 8 + math.cos(a) * 1.6), 0.7, 7.78, 7.84, "black", sides=8, part=part)
    cyl_part("spin")
    m.cyl("x", (10.6, 8), 1.0, 7.8, 16.0, "dark_paint", sides=12)
    m.box([15.95, 9.7, 7.1], [16.05, 11.5, 8.9], "black")
    for y in (12.25, 6.6):
        rail(m, 2.0, 15.0, y, w=1.0)
    for z in (5.3, 10.7):
        m.box([2.0, 8.6, z - 0.25], [15.0, 10.4, z + 0.25], "dark_paint")
    block(m, 10.0, 14.0, 6.0, 7.2, 1.4, "polymer", deco=("grip",))
    front_post(m, 15.0, 12.7, h=0.6)

    def mag(m, part, k):
        m.cyl("x", (10.6, 8), 0.95, 3.0, 4.6, "brass", sides=12, part=part)
        m.cyl("x", (10.6, 8), 0.9, 4.6, 5.6, "olive", sides=12, part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [-0.6, 0.8, 0.0])
    m.mount("spin", [5.0, 9.5, 8])
    mounts(m, [-0.6, 6.0, 8], [3.0, 13.2, 8], [16.0, 10.6, 8], [6.0, 12.7, 8], [12.0, 6.0, 8], [9.0, 9.5, 11.0])
    return m


@gun("igla")
def igla():
    """Igla MANPADS: long launch tube, the gripstock with the thermal battery and the seeker's cooled head"""
    m = Model("igla", density=4)
    m.cyl("x", (10.4, 8), 1.1, -14.4, 18.0, "olive", sides=16, deco=("panel",))
    m.cyl("x", (10.4, 8), 1.25, -15.6, -14.2, "dark_paint", sides=16)
    m.cyl("x", (10.4, 8), 1.25, 17.0, 18.6, "dark_paint", sides=16)
    m.box([18.55, 9.4, 7.0], [18.65, 11.4, 9.0], "lens")
    m.box([-15.65, 9.4, 7.0], [-15.55, 11.4, 9.0], "black")
    block(m, -1.2, 4.6, 7.6, 9.4, 1.6, "dark_paint", deco=("rivets",))    # gripstock
    grip(m, 0.6, 7.8, length=4.0, depth=1.8, w=1.4, ang=0.0, mat="dark_paint")
    trigger_guard(m, 0.6, 3.6, 6.2, 7.6)
    m.cyl("x", (6.6, 8), 0.65, 4.8, 9.6, "olive", sides=8)               # thermal battery
    m.box([2.0, 11.4, 6.6], [3.0, 12.8, 7.2], "dark_paint")              # aiming frame
    m.box([12.0, 11.4, 6.6], [12.6, 13.0, 7.2], "dark_paint")
    m.box([1.8, 11.4, 6.8], [12.8, 11.7, 7.0], "dark_paint")
    m.cyl("z", (2.5, 12.4), 0.35, 6.5, 6.7, "glow_red", sides=8)

    def mag(m, part, k):
        m.cyl("x", (10.4, 8), 0.75, 18.0, 19.2, "lens" if part == "mag" else "white", sides=12, part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [1.0, 0.0, 0.0])
    mounts(m, [0.4, 6.6, 8], [2.5, 13.0, 6.9], [18.6, 10.4, 8], [4.0, 11.5, 8], [10.0, 9.3, 8], [10.0, 10.4, 9.2])
    return m


@gun("rpg7_tbg")
def rpg7_tbg():
    """RPG-7 with the TBG-7V thermobaric round: the launcher, the long blunt warhead in desert tan with the red band"""
    m = Model("rpg7_tbg", density=4)
    m.cyl("x", (10.0, 8), 0.8, -10.0, 16.0, "olive", sides=16)
    m.cyl("x", (10.0, 8), 1.0, -11.6, -10.0, "olive", sides=16)
    m.cyl("x", (10.0, 8), 1.25, -13.0, -11.6, "olive", sides=16)
    m.box([-13.05, 9.2, 7.2], [-12.95, 10.8, 8.8], "black")
    m.cyl("x", (10.0, 8), 1.08, -6.0, -1.0, "wood_light", sides=16, deco=("vlines:5",))
    m.cyl("x", (10.0, 8), 1.08, 2.0, 7.0, "wood_light", sides=16, deco=("vlines:5",))
    grip(m, 0.6, 9.2, length=3.8, depth=1.6, w=1.3, mat="wood_red")
    trigger_guard(m, 1.0, 3.6, 7.6, 9.2)
    grip(m, 8.4, 9.2, length=3.4, depth=1.5, w=1.3, ang=0.0, mat="wood_red")
    front_post(m, 14.4, 10.8, h=1.1)
    block(m, 4.0, 5.0, 10.8, 11.8, 0.8, "olive")
    block(m, 0.0, 4.6, 11.2, 12.6, 1.0, "olive")
    m.cyl("x", (12.0, 6.7), 0.55, -0.6, 5.0, "olive", sides=8)
    m.cyl("x", (10.0, 8), 0.95, 15.6, 16.4, "steel", sides=16)

    def mag(m, part, k):  # TBG-7V: long cylindrical thermobaric warhead
        m.cyl("x", (10.0, 8), 0.45, 15.5, 17.6, "steel", sides=8, part=part)
        m.cyl("x", (10.0, 8), 1.3, 17.6, 25.6, "desert_paint", sides=16, part=part, deco=("panel",))
        m.cyl("x", (10.0, 8), 1.34, 19.6, 20.2, "red", sides=16, part=part)
        m.cyl("x", (10.0, 8), 1.0, 25.6, 26.6, "desert_paint", sides=12, part=part)
        m.cyl("x", (10.0, 8), 0.4, 26.6, 27.8, "steel", sides=8, part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [1.0, 0.0, 0.0])
    mounts(m, [0.2, 7.4, 8], [4.5, 12.0, 8], [27.8, 10.0, 8], [2.3, 12.6, 8], [12.0, 9.2, 8], [12.0, 10.0, 8.8])
    return m


NEW = ["aps", "m9", "ppsh41", "bizon", "uzi", "m14", "aks74u", "famas", "sks", "mosin", "vss", "pkm", "saiga12", "mgl", "igla", "rpg7_tbg"]
