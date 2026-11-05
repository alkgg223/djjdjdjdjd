"""v3.0 guns: 27 new weapons. Same conventions as guns.py (barrel along +X, centre plane z = 8, parts 'body', 'mag',
'mag_ext', mount points grip / sight / muzzle / scope / under / side / magdir)."""
import math

from modelkit import Model
from parts import *  # noqa
from guns import gun, classic_stock, mag_dir, ar_lower


def mounts(m, grip_, sight, muzzle, scope, under, side):
    m.mount("grip", grip_)
    m.mount("sight", sight)
    m.mount("muzzle", muzzle)
    m.mount("scope", scope)
    m.mount("under", under)
    m.mount("side", side)


def skeleton_stock(m, x1, y, mat="polymer", length=9.0, drop=3.4):
    """a folding skeleton stock: top bar, bottom bar, butt plate"""
    block(m, x1 - length, x1, y - 0.5, y + 0.5, 1.3, mat)
    block(m, x1 - length, x1 - length * 0.45, y - drop - 0.4, y - drop + 0.4, 1.3, mat)
    block(m, x1 - length * 0.45 - 0.8, x1 - length * 0.45, y - drop - 0.4, y - 0.5, 1.3, mat)
    block(m, x1 - length - 0.6, x1 - length, y - drop - 0.6, y + 0.7, 1.6, "rubber")
    block(m, x1 - 0.8, x1, y - 1.6, y + 0.6, 1.4, mat)


def side_folder_stock(m, x1, y, mat="polymer"):
    """an adjustable side-folding stock (AK-12 / SCAR style): the cheek riser on top, the stepped butt"""
    block(m, x1 - 1.2, x1, y - 1.3, y + 0.6, 1.5, mat)
    block(m, x1 - 8.6, x1 - 1.2, y - 0.9, y + 0.6, 1.4, mat, deco=("panel",))
    block(m, x1 - 7.6, x1 - 3.0, y + 0.6, y + 1.2, 1.3, mat)
    block(m, x1 - 9.6, x1 - 8.6, y - 3.6, y + 0.8, 1.6, mat)
    block(m, x1 - 10.0, x1 - 9.6, y - 3.7, y + 0.9, 1.7, "rubber")
    m.box([x1 - 8.8, y - 3.4, Z - 0.62], [x1 - 3.6, y - 2.4, Z + 0.62], mat, rot=("z", 22.5, [x1 - 8.8, y - 3.4, Z]))


def ak_body(m, x0, x1, y0, y1, mat="gunmetal", rail_top=False):
    block(m, x0, x1, y0, y1, 1.6, mat, deco=("rivets", "port"))
    block(m, x0 + 1.6, x1 - 0.2, y1, y1 + 0.4, 1.35, mat)
    m.box([x0 + 2.8, y1 - 1.35, Z + 0.8], [x0 + 10.0, y1 - 0.85, Z + 0.95], "steel")
    m.box([x1 - 2.0, y1 - 1.55, Z + 0.75], [x1 - 1.0, y1 - 1.0, Z + 1.7], "bright")
    if rail_top:
        rail(m, x0 + 1.0, x1 - 0.4, y1 + 0.4, w=1.1)


def ak_mag(m, x, y, mat_a="polymer", mat_b="steel"):
    def mag(m, part, k):
        chain(m, x, y, [(3.0 * k, 0.0), (2.8 * k, 22.5), (2.6 * k, 22.5)], 2.9, 1.25, mat_a if part == "mag" else mat_b, part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 15)


def box_mag(m, x0, x1, ytop, length, w=1.2, mat="polymer", ext_mat="steel", deco=("ribs",)):
    def mag(m, part, k):
        m.box([x0, ytop - length * k, Z - w / 2], [x1, ytop, Z + w / 2], mat if part == "mag" else ext_mat, part=part, deco=deco)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 0)


# =============================================================================== ASSAULT RIFLES
@gun("hk416")
def hk416():
    """HK416: AR lower with the piston upper, the long free-float rail, the HK slim-line stock, steel mag"""
    m = Model("hk416", density=4.5)
    ar_receiver(m, -1.0, 9.6, 7.4, 10.6, mat="dark_paint")
    side_folder_stock(m, -1.0, 9.6, mat="polymer")
    m.box([-1.6, 10.3, 7.3], [-0.6, 10.75, 8.7], "dark_paint")
    ar_lower(m, -1.0, 2.0)
    block(m, 9.6, 19.6, 7.75, 10.55, 2.15, "dark_paint", deco=("slots:6",))
    rail(m, 9.6, 19.6, 10.55, w=1.1)
    for y in (8.3, 9.9):
        rail(m, 10.4, 18.8, y, w=0.4, h=0.35)
    barrel(m, 19.6, 22.4, 9.2, 0.44, muzzle=False)
    block(m, 19.0, 19.9, 9.4, 10.9, 1.15, "dark_paint")
    front_post(m, 19.4, 10.9, h=0.7)
    flash_hider(m, 22.4, 23.8, 9.2, 0.5)
    block(m, 0.0, 1.2, 11.05, 11.4, 1.0, "polymer")
    rear_sight(m, 0.6, 11.4)

    def mag(m, part, k):
        stanag_mag(m, 6.0, 7.5, length=6.0 * k, part=part, mat="steel" if part == "mag" else "tan")
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 10)
    mounts(m, [1.6, 6.2, 8], [0.6, 12.0, 8], [23.8, 9.2, 8], [4.5, 11.05, 8], [15.0, 7.75, 8], [15.0, 9.2, 9.1])
    return m


def scar(name, heavy):
    m = Model(name, density=4.5)
    mat = "tan"
    # the long aluminium upper with its full-length rail, the polymer lower
    block(m, -1.0, 18.4, 9.4, 11.2, 1.75, mat, deco=("panel", "port"))
    rail(m, -0.8, 18.2, 11.2, w=1.15)
    block(m, -1.0, 8.4, 7.3, 9.4, 1.6, "polymer")
    for y in (9.6, 10.6):
        rail(m, 10.0, 17.6, y, w=0.4, h=0.35)
    m.box([5.0, 10.0, 8.85], [6.2, 10.6, 9.8], "dark_paint")                       # side charging handle
    grip(m, 2.2, 7.6, length=4.3, depth=1.85, w=1.45, mat="polymer")
    trigger_guard(m, 2.6, 5.8, 6.2, 7.45, mat="polymer")
    side_folder_stock(m, -1.0, 10.0, mat=mat)
    barrel(m, 18.4, 22.0 if heavy else 21.0, 9.9, 0.5 if heavy else 0.44, muzzle=False)
    block(m, 17.6, 18.6, 9.0, 9.8, 1.2, mat)
    flash_hider(m, 22.0 if heavy else 21.0, 23.6 if heavy else 22.4, 9.9, 0.56 if heavy else 0.5)
    rear_sight(m, 0.8, 11.65)
    front_post(m, 17.4, 11.65, h=0.7)

    def mag(m, part, k):
        if heavy:
            m.box([6.4, 7.4 - 4.0 * k, Z - 0.62], [8.6, 7.4, Z + 0.62], "polymer" if part == "mag" else "steel", part=part, deco=("ribs",))
        else:
            stanag_mag(m, 6.9, 7.4, length=6.0 * k, part=part, mat="polymer" if part == "mag" else "tan")
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 0 if heavy else 10)
    tip = 23.6 if heavy else 22.4
    mounts(m, [1.8, 6.2, 8], [0.8, 12.25, 8], [tip, 9.9, 8], [4.5, 11.65, 8], [14.0, 9.2, 8], [14.0, 10.1, 9.0])
    return m


@gun("scar_l")
def scar_l():
    """SCAR-L: the tan aluminium upper with its long rail, the polymer lower, side-folding stock, 5.56 STANAG"""
    return scar("scar_l", False)


@gun("scar_h")
def scar_h():
    """SCAR-H: the 7.62 SCAR: longer barrel, the straight 20-round box"""
    return scar("scar_h", True)


@gun("g36")
def g36():
    """G36: all-polymer, the tall carry handle with its built-in optic, skeleton folding stock, translucent magazine"""
    m = Model("g36", density=4.5)
    block(m, -1.4, 13.0, 7.6, 10.3, 1.7, "polymer_grey", deco=("panel",))
    # the carry handle with the optic in it
    block(m, 0.6, 9.6, 12.2, 13.4, 1.4, "polymer_grey")
    for x in (0.8, 8.8):
        block(m, x, x + 0.8, 10.3, 12.2, 1.4, "polymer_grey")
    m.cyl("x", (12.8, 8), 0.55, 9.6, 10.2, "blued", sides=8)
    m.box([10.18, 12.5, 7.6], [10.22, 13.1, 8.4], "lens")
    m.cyl("x", (12.8, 8), 0.5, 0.0, 0.6, "blued", sides=8)
    block(m, 13.0, 18.6, 7.9, 10.0, 2.0, "polymer_grey", deco=("slots:4",))
    barrel(m, 18.6, 22.4, 9.1, 0.42, muzzle=False)
    flash_hider(m, 22.4, 23.6, 9.1, 0.5)
    m.box([4.0, 10.0, 8.8], [5.2, 10.7, 9.6], "polymer_grey")                       # charging handle
    grip(m, 3.0, 7.7, length=4.3, depth=1.9, w=1.45, mat="polymer_grey")
    trigger_guard(m, 3.4, 6.4, 6.2, 7.6, mat="polymer_grey")
    skeleton_stock(m, -1.4, 9.4, mat="polymer_grey")

    def mag(m, part, k):
        chain(m, 8.4, 7.6, [(3.0 * k, 0.0), (2.6 * k, 22.5)], 2.6, 1.15, "lens" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 12)
    mounts(m, [2.6, 6.2, 8], [1.0, 13.9, 8], [23.6, 9.1, 8], [5.0, 13.4, 8], [16.0, 7.9, 8], [16.0, 9.0, 9.0])
    return m


def bullpup(m, mat, x0, x1, y0, y1):
    """the bullpup body: the action behind the grip, the cheek rest on top"""
    block(m, x0, x1, y0, y1, 2.0, mat, deco=("panel",))
    block(m, x0 - 0.5, x0, y0 - 0.2, y1 + 0.1, 2.1, "rubber")


@gun("aug")
def aug():
    """Steyr AUG A3: the olive bullpup with the integral 1.5x scope, the folding vertical foregrip, a clear magazine behind the grip"""
    m = Model("aug", density=4.5)
    bullpup(m, "olive", -9.0, 4.0, 6.6, 10.0)
    m.cyl("x", (8.6, 8), 1.7, 3.0, 9.0, "olive", sides=12)
    block(m, 4.0, 10.0, 9.6, 10.6, 1.4, "olive")
    # the scope on its integral mount
    for x in (-3.4, 2.6):
        block(m, x, x + 1.2, 10.0, 11.4, 1.0, "olive")
    m.cyl("x", (12.1, 8), 0.95, -4.6, 4.4, "olive", sides=12)
    m.cyl("x", (12.1, 8), 1.1, 3.4, 4.6, "olive", sides=12)
    m.box([4.58, 11.3, 7.2], [4.62, 12.9, 8.8], "lens")
    barrel(m, 9.0, 16.4, 8.9, 0.46, muzzle=False)
    flash_hider(m, 16.4, 17.8, 8.9, 0.5)
    # the large trigger guard enclosing the whole hand, the folding foregrip
    block(m, 4.6, 9.0, 5.2, 5.6, 0.6, "olive")
    block(m, 8.6, 9.0, 5.2, 7.4, 0.6, "olive")
    grip(m, 4.6, 7.0, length=4.0, depth=1.8, w=1.5, mat="olive")
    m.box([9.6, 4.4, 7.3], [10.6, 7.4, 8.7], "olive", rot=("z", -22.5, [10.1, 7.4, 8]))
    trigger_guard(m, 4.8, 7.4, 6.0, 7.0, mat="olive")

    def mag(m, part, k):
        m.box([-2.0, 6.6 - 4.6 * k, Z - 0.6], [0.4, 6.6, Z + 0.6], "lens" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 0)
    mounts(m, [4.2, 5.6, 8], [-4.6, 12.1, 8], [17.8, 8.9, 8], [0.0, 13.1, 8], [12.0, 7.6, 8], [12.0, 8.7, 9.6])
    return m


@gun("qbz95")
def qbz95():
    """QBZ-95: the dark green bullpup, the tall carry handle, the magazine far behind the grip"""
    m = Model("qbz95", density=4.5)
    bullpup(m, "od_paint", -9.6, 6.0, 6.6, 9.8)
    block(m, -6.0, 6.0, 9.8, 10.4, 1.4, "od_paint")
    block(m, -2.0, 6.0, 11.4, 12.2, 1.2, "od_paint")
    for x in (-2.0, 5.0):
        block(m, x, x + 1.0, 10.4, 11.4, 1.2, "od_paint")
    rear_sight(m, -1.4, 12.2)
    block(m, 6.0, 12.6, 7.6, 9.6, 2.0, "od_paint", deco=("slots:3",))
    barrel(m, 12.6, 17.6, 8.8, 0.44, muzzle=False)
    front_post(m, 12.0, 9.6, h=1.8)
    flash_hider(m, 17.6, 18.8, 8.8, 0.5)
    grip(m, 5.6, 7.0, length=4.2, depth=1.9, w=1.5, mat="od_paint")
    block(m, 5.4, 9.4, 4.6, 5.0, 0.6, "od_paint")
    block(m, 9.0, 9.4, 4.6, 7.4, 0.6, "od_paint")
    trigger_guard(m, 5.8, 8.6, 5.8, 7.0, mat="od_paint")

    def mag(m, part, k):
        chain(m, -3.0, 6.6, [(2.8 * k, 0.0), (2.4 * k, 22.5)], 2.5, 1.15, "polymer" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 12)
    mounts(m, [5.2, 5.6, 8], [-1.4, 12.75, 8], [18.8, 8.8, 8], [1.5, 12.2, 8], [10.0, 7.6, 8], [10.0, 8.6, 9.0])
    return m


@gun("ak12")
def ak12():
    """AK-12: the modern AK: a rail on the top cover and round the handguard, the adjustable side-folding stock, the big brake"""
    m = Model("ak12", density=4.5)
    ak_body(m, -1.6, 11.2, 7.5, 10.2, rail_top=True)
    side_folder_stock(m, -1.6, 9.4, mat="polymer")
    block(m, 11.2, 21.0, 7.6, 10.4, 2.05, "polymer", deco=("slots:5",))
    rail(m, 11.2, 21.0, 10.4, w=1.1)
    rail(m, 12.0, 20.4, 7.2, w=1.0, h=0.4)
    barrel(m, 21.0, 25.6, 8.85, 0.52, muzzle=False)
    block(m, 21.0, 22.2, 8.1, 10.8, 1.25, "gunmetal")
    front_post(m, 24.2, 9.35, h=1.4)
    muzzle_brake(m, 25.6, 27.6, 8.85, 0.78)
    grip(m, 4.4, 7.4, length=4.6, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 4.2, 8.4, 5.9, 7.5)
    ak_mag(m, 9.6, 7.6, "polymer", "tan")
    mounts(m, [4.0, 6.0, 8], [2.0, 11.4, 8], [27.6, 8.85, 8], [4.0, 10.6, 8], [16.0, 7.2, 8], [16.0, 9.0, 9.1])
    return m


@gun("ak103")
def ak103():
    """AK-103: the 7.62 AK-100: black polymer furniture, the folding stock, the slant brake, the 7.62 curved magazine"""
    m = Model("ak103", density=4.5)
    ak_body(m, -1.6, 11.2, 7.5, 10.2)
    classic_stock(m, -15.0, -1.4, 9.4, 4.4, 1.5, mat="polymer", plate="rubber")
    block(m, 11.2, 13.2, 7.7, 10.5, 1.75, "gunmetal")
    rear_sight(m, 12.0, 10.5)
    block(m, 13.2, 21.0, 7.6, 9.75, 2.0, "polymer", deco=("lines:2",))
    block(m, 13.2, 19.6, 9.75, 10.75, 1.25, "polymer")
    tube(m, 19.6, 22.6, 10.25, 0.48, "gunmetal")
    block(m, 22.0, 23.3, 8.0, 10.9, 1.25, "gunmetal")
    barrel(m, 13.0, 26.0, 8.85, 0.55, muzzle=False)
    front_post(m, 24.6, 9.35, h=1.95)
    m.cyl("x", (8.85, 8), 0.72, 26.0, 27.8, "gunmetal", sides=8, deco=("vslots",))
    grip(m, 4.4, 7.4, length=4.6, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 4.2, 8.4, 5.9, 7.5)
    ak_mag(m, 9.6, 7.6, "polymer", "steel")
    mounts(m, [4.0, 6.0, 8], [12.0, 11.3, 8], [27.8, 8.85, 8], [5.0, 11.15, 8], [17.0, 7.6, 8], [17.0, 8.7, 9.0])
    return m


# =============================================================================== SMGs
@gun("mp5")
def mp5():
    """MP5: the slim stamped receiver, the drum front sight, the slim curved magazine, the retractable stock"""
    m = Model("mp5", density=6)
    block(m, -1.0, 12.0, 8.4, 10.6, 1.5, "blued", deco=("rivets", "port"))
    m.cyl("x", (10.0, 8), 0.5, 12.0, 15.8, "blued", sides=8)
    m.cyl("x", (10.0, 8), 0.38, 15.8, 17.0, "blued", sides=8)
    block(m, 6.6, 11.8, 7.4, 9.2, 2.0, "polymer", deco=("lines:3",))
    m.cyl("x", (11.0, 8), 0.7, 0.0, 1.0, "blued", sides=10)                       # drum rear sight
    m.cyl("x", (11.0, 8), 0.55, 14.6, 15.4, "blued", sides=10)
    block(m, 14.8, 15.2, 10.6, 11.5, 0.4, "blued")
    m.box([8.8, 10.0, 8.75], [9.6, 10.6, 10.0], "blued")                          # cocking tube handle
    grip(m, 2.2, 8.4, length=4.0, depth=1.8, w=1.4, mat="polymer")
    trigger_guard(m, 2.6, 5.6, 6.9, 8.2, mat="polymer")
    for y in (9.8, 8.6):
        tube(m, -9.4, -1.0, y, 0.22, "steel")
    block(m, -10.0, -9.4, 7.4, 10.4, 1.4, "rubber")

    def mag(m, part, k):
        chain(m, 5.8, 8.4, [(3.0 * k, 0.0), (2.4 * k, 22.5)], 1.7, 0.9, "blued" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 15)
    mounts(m, [1.8, 7.0, 8], [0.5, 11.8, 8], [17.0, 10.0, 8], [4.0, 10.6, 8], [10.0, 7.4, 8], [10.0, 8.4, 9.1])
    return m


@gun("mp7")
def mp7():
    """MP7: the compact PDW: the long magazine in the grip, the folding vertical foregrip, the sliding stock, flip-up sights"""
    m = Model("mp7", density=7)
    block(m, -1.0, 9.6, 8.6, 11.0, 1.5, "polymer", deco=("panel",))
    rail(m, -0.8, 9.4, 11.0, w=1.05)
    barrel(m, 9.6, 12.4, 9.8, 0.4, muzzle=False)
    flash_hider(m, 12.4, 13.4, 9.8, 0.45)
    block(m, 7.0, 9.6, 7.8, 8.6, 1.3, "polymer")
    m.box([7.6, 5.6, 7.45], [8.6, 7.8, 8.55], "polymer")                         # folded-down foregrip
    grip(m, 2.4, 8.6, length=5.0, depth=2.0, w=1.4, mat="polymer")
    trigger_guard(m, 2.8, 6.0, 7.0, 8.4, mat="polymer")
    for z in (7.3, 8.7):
        m.box([-7.0, 9.4, z - 0.15], [-1.0, 9.8, z + 0.15], "steel")
    block(m, -7.6, -7.0, 7.6, 10.0, 1.4, "polymer")
    block(m, 0.0, 0.8, 11.45, 12.0, 0.9, "polymer")
    block(m, 8.4, 9.2, 11.45, 12.0, 0.9, "polymer")

    def mag(m, part, k):
        grip(m, 2.4, 8.2, length=6.4 * k, depth=1.4, w=0.9, mat="polymer" if part == "mag" else "steel", deco=("ribs",), part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.3)
    mag_dir(m, -22.5)
    mounts(m, [2.2, 7.0, 8], [0.4, 12.4, 8], [13.4, 9.8, 8], [4.0, 11.45, 8], [8.0, 7.8, 8], [8.0, 9.4, 8.9])
    return m


@gun("p90")
def p90():
    """P90: the rounded bullpup shell with the thumbhole grip, the clear 50-round magazine lying on top, the reflex sight"""
    m = Model("p90", density=6)
    block(m, -6.0, 8.4, 6.6, 9.8, 2.2, "polymer", deco=("panel",))
    m.box([-6.6, 6.4, 6.8], [-6.0, 10.0, 9.2], "rubber")
    # the thumbhole: the frame round the hand
    block(m, 2.0, 8.4, 5.0, 5.5, 2.0, "polymer")
    block(m, 7.8, 8.4, 5.0, 6.6, 2.0, "polymer")
    grip(m, 3.0, 6.6, length=1.6, depth=1.8, w=1.5, mat="polymer")
    block(m, 8.4, 11.0, 7.0, 9.4, 2.0, "polymer")
    barrel(m, 11.0, 13.0, 8.4, 0.4, muzzle=False)
    flash_hider(m, 13.0, 13.8, 8.4, 0.46)
    # the reflex sight housing
    block(m, 1.0, 5.2, 11.0, 12.6, 1.4, "polymer")
    m.box([5.15, 11.3, 7.55], [5.2, 12.3, 8.45], "lens")

    def mag(m, part, k):
        m.box([-5.0, 9.8, Z - 0.9], [-5.0 + 11.0 * min(1.0, k), 11.0, Z + 0.9], "lens" if part == "mag" else "polymer", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [0.0, 1.0, 0.0])
    mounts(m, [3.0, 5.6, 8], [1.6, 12.8, 8], [13.8, 8.4, 8], [3.0, 12.6, 8], [9.6, 7.0, 8], [9.6, 8.4, 9.1])
    return m


@gun("mpx")
def mpx():
    """SIG MPX: AR-style controls on a short upper, the slim M-LOK handguard, the folding stock, a straight 9 mm magazine"""
    m = Model("mpx", density=5.5)
    ar_receiver(m, -1.0, 8.0, 7.4, 10.4, mat="dark_paint")
    skeleton_stock(m, -1.0, 9.6, mat="polymer", length=8.0, drop=3.0)
    ar_lower(m, -1.0, 2.0)
    m_lok_handguard(m, 8.0, 14.4, 9.0, h=2.3, w=1.9)
    barrel(m, 14.4, 15.6, 9.0, 0.4, muzzle=False)
    flash_hider(m, 15.6, 16.6, 9.0, 0.46)
    rear_sight(m, 0.6, 11.0)
    front_post(m, 13.8, 11.45, h=0.6)

    def mag(m, part, k):
        m.box([5.0, 7.4 - 5.4 * k, Z - 0.55], [6.6, 7.4, Z + 0.55], "polymer" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, 0)
    mounts(m, [1.6, 6.2, 8], [0.6, 11.6, 8], [16.6, 9.0, 8], [3.5, 10.85, 8], [11.4, 7.85, 8], [11.4, 9.0, 9.0])
    return m


# =============================================================================== MACHINE GUNS
@gun("m240")
def m240():
    """M240: the long heavy receiver with its feed tray cover, the big carrying handle, the bipod, the 100-round soft pouch"""
    m = Model("m240", density=4)
    block(m, -2.0, 12.0, 7.4, 10.8, 2.2, "dark_paint", deco=("rivets", "panel"))
    block(m, 0.0, 9.0, 10.8, 11.6, 2.0, "dark_paint", deco=("rail",))
    block(m, -11.0, -2.0, 7.6, 10.4, 1.7, "polymer")
    block(m, -12.0, -11.0, 6.0, 10.6, 1.9, "rubber")
    block(m, -11.0, -5.0, 6.0, 7.6, 1.6, "polymer")
    grip(m, 1.4, 7.4, length=4.3, depth=1.9, w=1.5, mat="polymer")
    trigger_guard(m, 1.6, 5.0, 6.0, 7.3)
    m.cyl("x", (9.2, 8), 0.75, 12.0, 26.0, "blued", sides=8)
    m.cyl("x", (8.0, 8), 0.4, 12.0, 22.0, "blued", sides=8)
    block(m, 14.0, 15.4, 9.6, 12.4, 0.5, "blued")
    m.box([13.4, 12.2, 7.7], [16.2, 12.6, 8.3], "blued")                        # carrying handle
    flash_hider(m, 26.0, 28.0, 9.2, 0.85)
    front_post(m, 25.0, 10.0, h=1.0)
    for dz in (-0.7, 0.7):
        m.box([18.0, 7.4, 8 + dz - 0.2], [26.0, 7.8, 8 + dz + 0.2], "steel")

    def mag(m, part, k):
        m.box([2.0, 7.6 - 4.2 * k, Z - 2.4], [7.6, 9.6, Z - 1.1], "canvas_green" if part == "mag" else "polymer", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    m.mount("magdir", [0.0, -0.6, -0.8])
    mounts(m, [1.0, 6.0, 8], [3.0, 12.2, 8], [28.0, 9.2, 8], [3.0, 11.6, 8], [20.0, 8.0, 8], [20.0, 9.4, 9.2])
    return m


@gun("mg3")
def mg3():
    """MG3: the perforated barrel jacket, the wooden-coloured polymer stock, the top cover with the feed, a 50-round drum"""
    m = Model("mg3", density=4)
    block(m, -2.0, 10.0, 7.6, 10.6, 1.9, "blued", deco=("rivets",))
    block(m, 0.0, 9.6, 10.6, 11.2, 1.8, "blued")
    block(m, -12.0, -2.0, 7.8, 10.2, 1.6, "polymer")
    block(m, -12.4, -12.0, 6.2, 10.4, 1.8, "rubber")
    block(m, -12.0, -6.0, 6.2, 7.8, 1.5, "polymer")
    grip(m, 1.6, 7.6, length=4.2, depth=1.8, w=1.45, mat="polymer")
    trigger_guard(m, 1.8, 5.0, 6.2, 7.5)
    m.cyl("x", (9.4, 8), 1.05, 10.0, 24.0, "blued", sides=10, deco=("vslots",))
    barrel(m, 24.0, 25.6, 9.4, 0.5, muzzle=False)
    m.cyl("x", (9.4, 8), 0.8, 25.6, 27.0, "blued", sides=8)
    front_post(m, 23.4, 10.45, h=1.6)
    rear_sight(m, 8.6, 11.2)
    for dz in (-0.7, 0.7):
        m.box([16.0, 7.6, 8 + dz - 0.2], [24.0, 8.0, 8 + dz + 0.2], "steel")

    def mag(m, part, k):
        m.cyl("z", (4.6, 7.0), 2.0 * k, Z - 2.6, Z - 1.0, "od_paint" if part == "mag" else "steel", sides=12, part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.2)
    m.mount("magdir", [0.0, -0.6, -0.8])
    mounts(m, [1.2, 6.2, 8], [8.6, 11.8, 8], [27.0, 9.4, 8], [3.0, 11.2, 8], [18.0, 8.2, 8], [18.0, 9.4, 9.2])
    return m


@gun("pkp")
def pkp():
    """PKP Pecheneg: the PK action, the heavy finned barrel in its forced-air shroud, the handle over it, the 100-round box"""
    m = Model("pkp", density=4)
    block(m, -2.0, 11.0, 7.6, 10.4, 1.8, "gunmetal", deco=("rivets", "port"))
    block(m, 0.0, 10.6, 10.4, 11.0, 1.7, "gunmetal")
    for y in (9.6, 7.8):
        tube(m, -11.0, -2.0, y, 0.24, "steel")
    block(m, -12.0, -11.0, 6.8, 10.4, 1.6, "polymer")
    grip(m, 3.0, 7.6, length=4.4, depth=1.9, w=1.45, mat="polymer")
    trigger_guard(m, 3.0, 6.6, 6.0, 7.5)
    m.cyl("x", (9.0, 8), 1.0, 11.0, 23.4, "gunmetal", sides=10, deco=("vlines:4",))
    m.cyl("x", (9.0, 8), 0.75, 23.4, 26.4, "gunmetal", sides=8)
    block(m, 15.0, 16.0, 10.0, 11.8, 0.5, "gunmetal")
    m.box([14.0, 11.6, 7.7], [17.0, 12.0, 8.3], "gunmetal")
    flash_hider(m, 26.4, 27.8, 9.0, 0.8)
    rear_sight(m, 9.4, 11.0)
    for dz in (-0.7, 0.7):
        m.box([17.0, 7.6, 8 + dz - 0.2], [25.0, 8.0, 8 + dz + 0.2], "steel")

    def mag(m, part, k):
        m.box([4.4, 7.6 - 4.6 * k, Z + 0.9], [8.6, 9.4, Z + 2.8], "od_paint" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.25)
    m.mount("magdir", [0.0, -0.6, 0.8])
    mounts(m, [2.6, 6.0, 8], [9.4, 11.6, 8], [27.8, 9.0, 8], [4.0, 11.0, 8], [19.0, 8.0, 8], [19.0, 9.2, 9.1])
    return m


# =============================================================================== SNIPER RIFLES
@gun("m82")
def m82():
    """Barrett M82A1: the boxy receiver over the long recoiling barrel, the arrowhead brake, the carry handle, the bipod"""
    m = Model("m82", density=4)
    block(m, -6.0, 15.0, 8.8, 11.6, 2.3, "dark_paint", deco=("panel", "rivets", "text:BARRETT"))
    rail(m, -5.6, 8.0, 11.6, w=1.2)
    block(m, -4.0, 5.0, 7.4, 8.8, 2.0, "dark_paint")
    grip(m, -2.0, 7.6, length=4.2, depth=1.9, w=1.5, mat="polymer")
    trigger_guard(m, -1.6, 1.8, 6.2, 7.45)
    block(m, -15.0, -6.0, 8.4, 11.2, 2.0, "dark_paint")
    block(m, -15.6, -15.0, 6.8, 11.4, 2.2, "rubber")
    block(m, -14.0, -9.0, 6.8, 8.4, 1.8, "dark_paint")
    m.cyl("x", (10.2, 8), 1.1, 15.0, 20.0, "dark_paint", sides=8, deco=("vslots",))
    m.cyl("x", (10.2, 8), 0.7, 20.0, 29.2, "blued", sides=8)
    # the arrowhead brake: wide, with two big ports
    block(m, 29.2, 31.8, 9.4, 11.0, 3.4, "blued", deco=("vslots",))
    m.box([31.75, 9.9, 7.6], [31.85, 10.5, 8.4], "black")
    m.box([10.0, 12.8, 7.7], [14.0, 13.2, 8.3], "dark_paint")
    for x in (10.4, 13.2):
        block(m, x, x + 0.4, 11.6, 12.8, 0.6, "dark_paint")
    for dz in (-0.8, 0.8):
        m.box([15.0, 8.0, 8 + dz - 0.22], [24.0, 8.45, 8 + dz + 0.22], "steel")

    def mag(m, part, k):
        m.box([1.4, 7.4 - 3.6 * k, 7.1], [5.0, 7.4, 8.9], "dark_paint" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 0)
    mounts(m, [-2.4, 6.0, 8], [0.0, 12.6, 8], [31.8, 10.2, 8], [2.0, 12.05, 8], [20.0, 9.2, 8], [18.0, 10.2, 9.2])
    return m


@gun("axmc")
def axmc():
    """AI AXMC: the green chassis with the folding skeleton stock and adjustable cheek piece, the long barrel and brake"""
    m = Model("axmc", density=4)
    block(m, -2.0, 12.0, 8.6, 10.8, 1.9, "olive", deco=("panel",))
    rail(m, -1.6, 11.6, 10.8, w=1.15)
    block(m, 8.0, 18.0, 8.0, 10.2, 2.0, "olive", deco=("slots:4",))
    m.box([3.0, 10.2, 8.9], [4.6, 10.8, 10.4], "steel")                         # bolt handle
    m.cyl("y", (3.8, 10.5), 0.45, 10.6, 11.4, "black", sides=8)
    grip(m, 0.6, 8.6, length=4.4, depth=2.0, w=1.5, mat="polymer")
    trigger_guard(m, 1.0, 4.4, 7.0, 8.4, mat="olive")
    # folding stock: the hinge, the top tube, the cheek rest, the monopod
    block(m, -3.0, -2.0, 7.6, 10.4, 1.7, "olive")
    block(m, -12.0, -3.0, 9.0, 10.0, 1.5, "olive")
    block(m, -10.0, -4.0, 10.0, 11.0, 1.4, "olive")
    block(m, -13.0, -12.0, 5.4, 10.4, 1.7, "rubber")
    block(m, -12.0, -8.0, 5.4, 6.4, 1.4, "olive")
    m.cyl("y", (-9.0, 8), 0.3, 4.2, 5.4, "steel", sides=6)
    m.cyl("x", (9.4, 8), 0.55, 18.0, 29.0, "blued", sides=8)
    muzzle_brake(m, 29.0, 31.0, 9.4, 0.85)

    def mag(m, part, k):
        m.box([5.2, 8.6 - 3.0 * k, Z - 0.75], [7.6, 8.6, Z + 0.75], "polymer" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.5)
    mag_dir(m, 0)
    mounts(m, [0.2, 7.0, 8], [0.0, 11.8, 8], [31.0, 9.4, 8], [3.0, 11.25, 8], [14.0, 8.0, 8], [14.0, 9.4, 9.1])
    return m


@gun("m200")
def m200():
    """CheyTac M200 Intervention: the long fluted barrel inside the handguard tube, the telescoping stock, the big brake"""
    m = Model("m200", density=4)
    block(m, -2.0, 10.0, 8.8, 11.0, 1.9, "dark_paint", deco=("panel",))
    rail(m, -1.6, 9.6, 11.0, w=1.15)
    m.cyl("x", (9.8, 8), 1.2, 10.0, 22.0, "dark_paint", sides=10, deco=("vslots",))
    m.cyl("x", (9.8, 8), 0.6, 22.0, 29.8, "blued", sides=10, deco=("vlines:4",))
    block(m, 29.8, 31.8, 9.0, 10.6, 2.0, "blued", deco=("vslots",))
    m.box([31.75, 9.5, 7.6], [31.85, 10.1, 8.4], "black")
    m.box([3.0, 10.4, 8.9], [4.6, 11.0, 10.4], "steel")
    m.cyl("y", (3.8, 10.6), 0.45, 10.8, 11.6, "black", sides=8)
    grip(m, 0.4, 8.8, length=4.4, depth=2.0, w=1.5, mat="polymer")
    trigger_guard(m, 0.8, 4.2, 7.2, 8.6, mat="dark_paint")
    for y in (10.0, 9.0):
        tube(m, -11.0, -2.0, y, 0.3, "steel")
    block(m, -11.6, -9.0, 9.8, 11.0, 1.4, "dark_paint")
    block(m, -12.4, -11.6, 5.8, 11.0, 1.7, "rubber")
    block(m, -11.6, -9.6, 5.8, 9.0, 1.5, "dark_paint")
    for dz in (-0.8, 0.8):
        m.box([14.0, 8.4, 8 + dz - 0.22], [22.0, 8.8, 8 + dz + 0.22], "steel")

    def mag(m, part, k):
        m.box([5.0, 8.8 - 3.4 * k, Z - 0.8], [7.8, 8.8, Z + 0.8], "dark_paint" if part == "mag" else "steel", part=part, deco=("ribs",))
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.4)
    mag_dir(m, 0)
    mounts(m, [0.0, 7.2, 8], [0.0, 12.0, 8], [31.8, 9.8, 8], [3.0, 11.45, 8], [16.0, 8.6, 8], [16.0, 9.8, 9.3])
    return m


# =============================================================================== SHOTGUNS
@gun("benelli_m4")
def benelli_m4():
    """Benelli M4: the semi-automatic with the twin gas pistons, the pistol grip stock, the ghost-ring sights, the rail"""
    m = Model("benelli_m4", density=4.5)
    block(m, -1.0, 8.6, 8.2, 10.8, 1.55, "blued", deco=("port", "text:M4"))
    rail(m, -0.6, 8.2, 10.8, w=1.05)
    trigger_guard(m, 0.8, 3.8, 6.6, 8.2)
    grip(m, 0.4, 8.2, length=4.2, depth=1.9, w=1.45, mat="polymer")
    side_folder_stock(m, -1.0, 9.8, mat="polymer")
    barrel(m, 8.6, 26.0, 10.0, 0.62)
    tube(m, 8.6, 22.0, 8.85, 0.58, "blued")
    m.cyl("x", (8.85, 8), 0.92, 10.0, 17.0, "polymer", sides=8, deco=("vlines:3",))
    m.box([21.8, 8.3, 7.5], [22.4, 10.4, 8.5], "blued")
    front_post(m, 25.4, 10.6, h=0.6)
    rear_sight(m, 1.0, 11.25)

    def mag(m, part, k):
        for i in range(3 if part == "mag" else 5):
            x = 1.0 + i * 1.35
            m.box([x, 8.6, 6.55], [x + 1.15, 10.4, 7.2], "red", part=part)
            m.box([x, 8.6, 6.5], [x + 1.15, 9.1, 7.25], "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [0.0, 0.0, -1.0])
    mounts(m, [0.0, 7.2, 8], [1.0, 11.8, 8], [26.0, 10.0, 8], [3.5, 11.25, 8], [19.0, 8.3, 8], [19.0, 10.0, 8.7])
    return m


@gun("r870")
def r870():
    """Remington 870: the classic pump with the wooden forend and stock, the magazine tube under the barrel, the bead"""
    m = Model("r870", density=4.5)
    classic_stock(m, -14.0, 0.6, 10.2, 5.0, 1.5, mat="wood_light", plate="rubber")
    block(m, -1.0, 8.4, 8.2, 10.7, 1.55, "blued", deco=("port", "text:870"))
    trigger_guard(m, 0.6, 3.6, 6.6, 8.2)
    barrel(m, 8.4, 26.0, 10.0, 0.6)
    block(m, 8.6, 26.0, 10.6, 10.8, 0.35, "blued", deco=("rail",))
    m.box([25.4, 10.8, 7.85], [25.7, 11.1, 8.15], "brass")
    tube(m, 8.4, 22.4, 8.85, 0.56, "blued")
    m.box([22.2, 8.3, 7.5], [22.8, 10.4, 8.5], "blued")
    m.cyl("x", (8.85, 8), 0.95, 11.6, 17.4, "wood_light", sides=8, deco=("vlines:9",))

    def mag(m, part, k):
        for i in range(4 if part == "mag" else 6):
            x = 0.0 + i * 1.35
            m.box([x, 8.6, 6.55], [x + 1.15, 10.4, 7.2], "red", part=part)
            m.box([x, 8.6, 6.5], [x + 1.15, 9.1, 7.25], "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [0.0, 0.0, -1.0])
    m.mount("pump", [14.5, 8.85, 8])
    mounts(m, [0.0, 7.2, 8], [0.0, 11.0, 8], [26.0, 10.0, 8], [3.5, 10.7, 8], [19.0, 8.3, 8], [19.0, 10.0, 8.7])
    return m


@gun("spas12")
def spas12():
    """SPAS-12: the black pump / semi with the perforated heat shield, the folding hook stock, the big forend"""
    m = Model("spas12", density=4.5)
    block(m, -1.0, 8.6, 8.2, 10.8, 1.6, "blued", deco=("port", "text:SPAS-12"))
    trigger_guard(m, 0.8, 3.8, 6.6, 8.2)
    grip(m, 0.4, 8.2, length=4.2, depth=1.9, w=1.45, mat="polymer")
    block(m, -12.0, -1.0, 10.0, 10.6, 1.4, "steel")
    m.box([-12.6, 7.4, Z - 0.5], [-12.0, 10.6, Z + 0.5], "steel")
    m.box([-12.0, 7.0, Z - 0.5], [-10.0, 7.6, Z + 0.5], "steel")
    barrel(m, 8.6, 24.0, 10.0, 0.62)
    m.cyl("x", (10.0, 8), 0.95, 8.6, 18.0, "blued", sides=10, deco=("vslots",))
    tube(m, 8.6, 21.0, 8.7, 0.6, "blued")
    m.cyl("x", (8.7, 8), 1.05, 11.0, 17.0, "polymer", sides=8, deco=("vlines:6",))
    flash_hider(m, 24.0, 25.0, 10.0, 0.66)
    front_post(m, 23.0, 10.6, h=0.8)
    rear_sight(m, 1.0, 10.8)

    def mag(m, part, k):
        for i in range(4 if part == "mag" else 6):
            x = 1.0 + i * 1.35
            m.box([x, 8.6, 6.55], [x + 1.15, 10.4, 7.2], "red", part=part)
            m.box([x, 8.6, 6.5], [x + 1.15, 9.1, 7.25], "brass", part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.0)
    m.mount("magdir", [0.0, 0.0, -1.0])
    m.mount("pump", [14.0, 8.7, 8])
    mounts(m, [0.0, 7.2, 8], [1.0, 11.4, 8], [25.0, 10.0, 8], [3.5, 10.8, 8], [19.0, 8.0, 8], [19.0, 10.0, 8.8])
    return m


# =============================================================================== PISTOLS
def pistol(name, slide_len, slide_h, frame_mat, slide_mat, grip_len, grip_depth, text, mag_len, two_tone=False, heavy=False, rail_len=2.6):
    m = Model(name, density=8)
    y0 = 9.0
    block(m, 0.0, slide_len, y0, y0 + slide_h, 1.25 if heavy else 1.15, slide_mat, deco=("serr", "port", "text:" + text))
    block(m, 0.2, slide_len - 0.2, y0 + slide_h, y0 + slide_h + 0.12, 0.9, slide_mat)
    front_post(m, slide_len - 0.6, y0 + slide_h, h=0.4, ears=False)
    rear_sight(m, 0.7, y0 + slide_h)
    m.box([slide_len, y0 + 0.35, 7.55], [slide_len + 0.1, y0 + slide_h - 0.45, 8.45], "black")
    block(m, 0.1, slide_len - 0.2, 8.05, y0, 1.2, frame_mat)
    rail(m, slide_len - rail_len - 0.2, slide_len - 0.2, 7.6, w=1.0, h=0.45, mat=frame_mat)
    grip(m, 1.7, 8.4, length=grip_len, depth=grip_depth, w=1.35 if not heavy else 1.5, mat=frame_mat, deco=("grip",))
    z0, z1 = zr(0.5)
    m.box([3.5, 7.0, z0], [6.4, 7.3, z1], frame_mat)
    m.box([6.1, 7.0, z0], [6.4, 8.1, z1], frame_mat)
    m.box([4.5, 7.35, 7.85], [4.85, 8.05, 8.15], "steel", rot=("z", -22.5, [4.6, 8.05, 8]))
    if heavy:
        m.cyl("x", (y0 + 0.9, 8), 0.5, slide_len - 1.0, slide_len + 0.1, slide_mat, sides=8)
        m.box([-0.6, y0 + 0.6, 7.6], [0.2, y0 + 1.4, 8.4], "steel")            # hammer
    if two_tone:
        m.box([1.0, y0 + 0.2, Z + 0.58], [3.0, y0 + 0.9, Z + 0.62], "bright")

    def mag(m, part, k):
        grip(m, 1.7, 8.0, length=mag_len * k, depth=grip_depth - 0.6, w=1.0, mat="polymer" if part == "mag" else "steel", deco=("ribs",), part=part)
    mag(m, "mag", 1.0)
    mag(m, "mag_ext", 1.35)
    mag_dir(m, -22.5)
    mounts(m, [2.0, 7.0, 8], [0.7, y0 + slide_h + 0.6, 8], [slide_len + 0.1, y0 + slide_h * 0.5, 8], [3.5, y0 + slide_h + 0.1, 8],
           [slide_len - 1.6, 7.6, 8], [slide_len - 1.6, 8.5, 8.6])
    return m


@gun("glock17")
def glock17():
    """Glock 17: the squared-off slide, the polymer frame with its finger grooves and accessory rail"""
    return pistol("glock17", 8.6, 1.8, "polymer", "gunmetal", 5.0, 2.4, "GLOCK 17", 5.3)


@gun("deagle")
def deagle():
    """Desert Eagle: the massive slide with the triangular barrel profile, the hammer, the long chrome finish"""
    return pistol("deagle", 10.4, 2.2, "chrome", "chrome", 5.6, 2.6, "DESERT EAGLE", 5.4, heavy=True)


@gun("p320")
def p320():
    """SIG P320: the slim slide with front serrations, the modular grip frame, the high-visibility sights"""
    return pistol("p320", 8.8, 1.75, "polymer", "blued", 5.0, 2.3, "SIG P320", 5.3, two_tone=True)


@gun("fiveseven")
def fiveseven():
    """FN Five-seveN: the long slim slide over the tall grip that holds twenty small rounds"""
    return pistol("fiveseven", 9.0, 1.6, "polymer_grey", "polymer", 5.6, 2.2, "FN 5.7", 6.0, rail_len=3.2)


NEW3 = ["hk416", "scar_l", "scar_h", "g36", "aug", "qbz95", "ak12", "ak103", "mp5", "mp7", "p90", "mpx", "m240", "mg3", "pkp", "m82", "axmc", "m200",
        "benelli_m4", "r870", "spas12", "glock17", "deagle", "p320", "fiveseven"]
