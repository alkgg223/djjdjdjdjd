"""Real vehicles (1 unit = 0.25 block, front -Z, +X right, ground y=0, (8,0,8) = entity origin):
tanks Leopard 2A6, T-90A, Challenger 2. Every point the game needs is stored in GEO and exported to Java."""
import math

from modelkit import Model, ANGLES

P = "paint_white"
GEO = {}


def seg(m, x0, x1, a, b, t, mat, part="body", deco=()):
    """a plate between two side-view points a=(y,z) and b=(y,z), x0..x1 wide and t thick, snapped to allowed angles"""
    (y0, z0), (y1, z1) = a, b
    if z1 < z0:
        (y0, z0), (y1, z1) = (y1, z1), (y0, z0)
    dz, dy = z1 - z0, y1 - y0
    L = math.hypot(dz, dy)
    cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
    ang = -math.degrees(math.atan2(dy, dz))
    if abs(ang) > 67.5:
        return m.box([x0, min(y0, y1), cz - t / 2], [x1, max(y0, y1), cz + t / 2], mat, part=part, deco=deco)
    snap = min(ANGLES, key=lambda v: abs(v - ang))
    return m.box([x0, cy - t / 2, cz - L / 2], [x1, cy + t / 2, cz + L / 2], mat, rot=("x", snap, [8, cy, cz]) if snap else None,
                 part=part, deco=deco)


def track_bends(m, x0, x1, g):
    """the track wrapped round the idler (front) and the sprocket (rear); the straight runs are animated parts"""
    iy, iz, ir = g["idler"]
    sy, sz, sr = g["sprocket"]
    by, bz = g["bottom"]
    ty = g["top"][0]
    T = "track"
    seg(m, x0, x1, (by + 0.2, bz + 0.3), (iy - ir * 0.8, iz - ir * 0.55), 0.4, T)
    m.box([x0, iy - ir * 0.75, iz - ir - 0.42], [x1, iy + ir * 0.75, iz - ir - 0.02], T, part="body")
    seg(m, x0, x1, (iy + ir * 0.8, iz - ir * 0.55), (ty + 0.2, iz + ir * 0.7), 0.4, T)
    end = bz + 29 * 0.75 - 0.5
    seg(m, x0, x1, (by + 0.2, end), (sy - sr * 0.8, sz + sr * 0.55), 0.4, T)
    m.box([x0, sy - sr * 0.75, sz + sr + 0.02], [x1, sy + sr * 0.75, sz + sr + 0.42], T, part="body")
    seg(m, x0, x1, (sy + sr * 0.8, sz + sr * 0.55), (ty + 0.2, sz - sr * 0.7), 0.4, T)


def smoke_launchers(m, x0, y0, z0, n, part="turret", side=1):
    """a rack of smoke grenade tubes pointing forward and up"""
    m.box([x0, y0, z0], [x0 + 0.8, y0 + 0.4, z0 + 0.4 * n + 0.2], "dark_paint", part=part)
    for i in range(n):
        z = z0 + 0.1 + i * 0.4
        m.box([x0 + 0.15, y0 + 0.4, z], [x0 + 0.65, y0 + 1.0, z + 0.32], "dark_paint", part=part, rot=("x", 22.5, [x0 + 0.4, y0 + 0.4, z + 0.16]))


def antenna(m, x, z, y0, h, part="turret"):
    m.box([x - 0.25, y0, z - 0.25], [x + 0.25, y0 + 0.5, z + 0.25], "dark_paint", part=part)
    m.box([x - 0.08, y0 + 0.5, z - 0.08], [x + 0.08, y0 + h, z + 0.08], "rubber", part=part)


def gun_barrel(m, gx, gy, zfront, zback, r, bands, evac=None, mrs=True, part="gun", brake=None):
    m.cyl("z", (gx, gy), r, zfront, zback, P, sides=8, part=part)
    for z in bands:
        m.cyl("z", (gx, gy), r + 0.07, z, z + 0.25, "dark_paint", sides=8, part=part)
    if evac:
        m.cyl("z", (gx, gy), r + 0.2, evac[0], evac[1], P, sides=8, part=part)
    m.cyl("z", (gx, gy), r + 0.06, zfront, zfront + 0.5, "dark_paint", sides=8, part=part)
    m.box([gx - r * 0.7, gy - r * 0.7, zfront - 0.06], [gx + r * 0.7, gy + r * 0.7, zfront], "black", part=part)
    if mrs:
        m.box([gx - 0.15, gy + r + 0.05, zfront + 0.6], [gx + 0.15, gy + r + 0.35, zfront + 1.1], "steel", part=part)
    if brake:
        m.box([gx - r - 0.25, gy - r * 0.8, zfront], [gx + r + 0.25, gy + r * 0.8, zfront + 0.8], "dark_paint", part=part,
              deco=("vslots",))


def marks(m, skirt_x, skirt_y, skirt_z, turret_side=None):
    """team markings: stars on the skirts (and turret), red stripes for the red team"""
    for x0 in skirt_x:
        m.box([x0, skirt_y[0], skirt_z[0]], [x0 + 0.05, skirt_y[1], skirt_z[1]], "decal", part="mark_green", deco=("star",))
        m.box([x0, skirt_y[0], skirt_z[0]], [x0 + 0.05, skirt_y[1], skirt_z[1]], "decal", part="mark_red", deco=("star:red",))
    if turret_side:
        for x0, y0, y1, z0, z1 in turret_side:
            m.box([x0, y0, z0], [x0 + 0.05, y1, z1], "decal", part="mark_green", deco=("star",))
            m.box([x0, (y0 + y1) / 2 - 0.3, z0 - 1], [x0 + 0.05, (y0 + y1) / 2 + 0.3, z1 + 1], "decal", part="mark_red", deco=("stripe:red",))


# ===================================================================================== Leopard 2A6
def leopard():
    g = dict(trackX=(2.4, 13.6), wheelsZ=[-2.4 + i * 3.25 for i in range(7)], wheel=(1.75, 1.3), idler=(2.3, -5.2, 1.15),
             sprocket=(2.5, 20.6, 1.35), bottom=(0.0, -3.6), top=(3.05, -4.6), turret=(8.0, 9.6), gun=(7.4, 3.2), muzzle=-15.85,
             seat=(5.0, 6.4, 12.4), coax=(9.8, 7.1, 1.6), front=[(1.6, 5.4, -6.4), (14.4, 5.4, -6.4), (8, 5.4, -6.8)],
             back=[(1.6, 5.4, 22.3), (14.4, 5.4, 22.3)], exhaust=[(1.5, 5.4, 19.5), (14.5, 5.4, 19.5)], smoke=(8, 6.0, 17), size=(4.2, 2.4))
    GEO["leopard"] = g
    m = Model("leopard", density=6.0, atlas_w=1024)
    b = m.box
    b([3.8, 0.9, -5.4], [12.2, 3.8, 21.4], P, deco=("grime",))
    b([1.0, 3.7, -3.8], [15.0, 5.6, 21.8], P, deco=("panel", "camo"))
    seg(m, 1.0, 15.0, (5.6, -3.8), (4.5, -6.6), 0.6, P, deco=("camo",))                    # upper glacis
    seg(m, 3.0, 13.0, (4.6, -6.5), (1.6, -6.0), 0.6, P, deco=("grime",))                   # nose plate
    b([3.2, 4.4, -6.75], [4.6, 5.0, -6.5], "glow_orange", light=True)
    b([11.4, 4.4, -6.75], [12.8, 5.0, -6.5], "glow_orange", light=True)
    b([7.0, 5.6, -3.2], [9.6, 5.85, -1.4], P, deco=("rivets",))                              # driver hatch
    for x in (6.6, 7.6, 8.6):
        b([x, 5.62, -3.5], [x + 0.8, 5.9, -3.25], "lens")
    # rear deck: big grille covers, exhaust louvres on both rear corners, rear plate
    for i in range(8):
        z = 13.4 + i * 0.95
        b([3.4, 5.6, z], [12.6, 5.75, z + 0.55], "dark_paint")
    for x0 in (1.0, 13.6):
        b([x0, 5.6, 17.4], [x0 + 1.4, 5.8, 21.4], "dark_paint", deco=("vslots",))
    b([1.0, 1.4, 21.8], [15.0, 5.6, 22.2], P, deco=("panel", "grime"))
    for x in (1.6, 13.4):
        b([x, 4.6, 22.2], [x + 1.0, 5.2, 22.35], "glow_red", light=True)
    b([6.4, 2.4, 22.2], [9.6, 3.8, 22.8], "od_paint", deco=("panel",))                     # telephone box
    m.cyl("y", g["turret"], 4.7, 5.6, 6.0, "dark_paint", sides=16)
    # side skirts: heavy front pair, lighter panels behind with a wavy lower edge
    for xs in ((0.5, 1.0), (15.0, 15.5)):
        for i in range(7):
            z0 = -4.4 + i * 3.6
            heavy = i < 2
            x0, x1 = (xs[0] - (0.25 if heavy else 0), xs[1]) if xs[0] < 8 else (xs[0], xs[1] + (0.25 if heavy else 0))
            b([x0, 1.2 + (i % 2) * 0.2, z0 + 0.05], [x1, 3.9, z0 + 3.55], P, deco=("panel", "rivets", "camo", "grime"))
    for tx in g["trackX"]:
        track_bends(m, tx - 1.3, tx + 1.3, g)
    marks(m, (0.2, 15.75), (1.7, 3.6), (4.0, 6.8), turret_side=[(2.95, 6.4, 8.4, 9.5, 12.0), (13.0, 6.4, 8.4, 9.5, 12.0)])
    # turret: hull of the turret, arrowhead wedge armour, bustle with basket
    t = dict(part="turret")
    b([3.0, 5.9, 5.0], [13.0, 8.8, 15.6], P, deco=("panel", "camo"), **t)
    b([3.0, 5.95, 1.0], [7.0, 8.75, 6.2], P, rot=("y", -22.5, [5.0, 7.35, 3.6]), deco=("camo",), **t)
    b([9.0, 5.95, 1.0], [13.0, 8.75, 6.2], P, rot=("y", 22.5, [11.0, 7.35, 3.6]), deco=("camo",), **t)
    b([3.6, 6.0, 15.6], [12.4, 8.6, 19.0], P, deco=("panel",), **t)
    b([3.0, 6.1, 19.0], [13.0, 6.25, 21.0], "dark_paint", deco=("vlines:8",), **t)
    for x0, x1 in ((3.0, 3.18), (12.82, 13.0)):
        b([x0, 7.6, 16.0], [x1, 7.75, 21.0], "dark_paint", **t)
        for z in (16.0, 18.5, 20.82):
            b([x0, 6.1, z], [x1, 7.75, z + 0.18], "dark_paint", **t)
    b([3.0, 7.6, 20.82], [13.0, 7.75, 21.0], "dark_paint", **t)
    b([4.0, 6.25, 19.2], [7.6, 7.4, 20.7], "canvas_green", deco=("lines:1",), **t)
    b([8.4, 6.25, 19.2], [12.0, 7.2, 20.7], "canvas", **t)
    # roof: commander's PERI periscope + hatch (left), gunner's EMES sight (right), loader hatch with MG3
    sx, sy, sz = g["seat"]
    m.cyl("y", (sx, sz), 1.2, 8.8, 9.4, P, sides=16, **t)
    b([sx - 1.0, 9.4, sz + 1.1], [sx + 1.0, 11.2, sz + 1.35], P, deco=("rivets",), **t)
    m.cyl("y", (5.2, 8.4), 0.55, 8.8, 9.4, P, sides=8, **t)
    b([4.4, 9.4, 7.6], [6.0, 10.0, 9.2], "dark_paint", **t)
    b([4.55, 9.5, 7.52], [5.85, 9.9, 7.6], "lens", **t)
    b([9.4, 8.8, 4.6], [11.8, 9.9, 6.8], P, **t)
    b([9.7, 9.0, 4.52], [11.5, 9.7, 4.6], "lens", **t)
    b([9.4, 8.8, 10.4], [12.0, 9.05, 12.8], P, deco=("rivets",), **t)
    b([10.5, 9.05, 9.6], [10.8, 9.8, 9.9], "steel", **t)
    b([10.4, 9.8, 8.4], [10.9, 10.25, 10.6], "gunmetal", **t)
    m.cyl("z", (10.65, 10.05), 0.13, 5.9, 8.4, "blued", sides=8, **t)
    smoke_launchers(m, 2.3, 7.7, 12.2, 4)
    smoke_launchers(m, 12.9, 7.7, 12.2, 4)
    antenna(m, 4.0, 15.4, 8.6, 6.5)
    antenna(m, 12.0, 15.4, 8.6, 6.5)
    b([7.9, 8.8, 15.2], [8.1, 10.4, 15.4], "steel", **t)
    # gun: long L55, mantlet set back between the wedges
    gx = 8.0
    gy, gz = g["gun"]
    k = dict(part="gun")
    b([6.2, 6.1, 1.4], [9.8, 8.6, 3.6], P, deco=("rivets",), **k)
    m.cyl("z", (gx, gy), 0.8, -0.6, 1.4, P, sides=8, **k)
    gun_barrel(m, gx, gy, g["muzzle"], -0.6, 0.48, (-3.6, -10.8, -13.4), evac=(-8.6, -6.6))
    cx, cy, cz = g["coax"]
    b([cx - 0.2, cy - 0.2, cz - 0.1], [cx + 0.2, cy + 0.2, cz + 0.05], "black", **k)
    return m


# ===================================================================================== T-90A
def t90():
    g = dict(trackX=(2.6, 13.4), wheelsZ=[-1.6 + i * 3.6 for i in range(6)], wheel=(1.95, 1.55), idler=(2.5, -4.2, 1.15),
             sprocket=(2.6, 19.8, 1.35), bottom=(0.0, -2.8), top=(3.5, -3.6), turret=(8.0, 8.0), gun=(6.9, 3.4), muzzle=-14.8,
             seat=(10.4, 5.8, 10.0), coax=(9.6, 6.5, 2.4), front=[(1.6, 5.0, -6.2), (14.4, 5.0, -6.2), (8, 5.0, -6.8)],
             back=[(2.0, 5.0, 23.0), (14.0, 5.0, 23.0)], exhaust=[(1.2, 4.6, 16.0)], smoke=(8, 5.4, 15), size=(4.0, 2.2))
    GEO["t90"] = g
    m = Model("t90", density=6.0, atlas_w=1024)
    b = m.box
    b([3.8, 1.0, -4.8], [12.2, 3.9, 20.6], P, deco=("grime",))
    b([1.0, 3.9, -3.2], [15.0, 5.3, 20.8], P, deco=("panel", "camo"))
    seg(m, 1.4, 14.6, (5.3, -3.2), (3.6, -6.4), 0.6, P, deco=("camo",))
    seg(m, 3.0, 13.0, (3.7, -6.3), (1.4, -5.6), 0.6, P)
    # Kontakt-5 explosive reactive armour bricks on the glacis
    for r in range(3):
        for c in range(6):
            x = 2.0 + c * 2.05
            z = -6.4 + r * 1.15
            b([x, 4.75, z], [x + 1.85, 5.2, z + 0.95], P, rot=("x", -22.5, [8, 4.45, -4.8]), deco=("rivets",))
    b([3.2, 0.8, -6.9], [12.8, 2.4, -6.2], "dark_paint", deco=("grime",))                     # dozer blade
    for x in (2.0, 12.6):
        b([x, 4.9, -3.6], [x + 1.4, 5.6, -3.0], "dark_paint")
        b([x + 0.25, 5.05, -3.66], [x + 1.15, 5.45, -3.6], "glow_orange", light=True)
    # rubber side flaps + ERA panels in front
    for xs in ((0.6, 1.0), (15.0, 15.4)):
        b([xs[0], 2.6, -3.6], [xs[1], 3.9, 20.6], "rubber")
        for i in range(3):
            z0 = -3.6 + i * 3.2
            b([xs[0] - (0.3 if xs[0] < 8 else 0), 2.2, z0 + 0.05], [xs[1] + (0.3 if xs[0] > 8 else 0), 3.9, z0 + 3.1], P, deco=("rivets", "camo"))
    # rear: deck grilles, two fuel drums across the rear, exhaust on the left
    for i in range(7):
        z = 12.6 + i * 1.0
        b([3.2, 5.3, z], [12.8, 5.45, z + 0.55], "dark_paint")
    b([1.0, 1.4, 20.6], [15.0, 5.3, 21.0], P, deco=("panel", "grime"))
    for x0, x1 in ((1.6, 7.2), (8.8, 14.4)):
        m.cyl("x", (4.4, 22.0), 0.92, x0, x1, "od_paint", sides=8, deco=("ribs",))
        b([x0 + 0.4, 3.4, 21.0], [x0 + 0.7, 5.4, 22.6], "dark_paint")
        b([x1 - 0.7, 3.4, 21.0], [x1 - 0.4, 5.4, 22.6], "dark_paint")
    b([0.7, 3.9, 14.8], [1.0, 4.9, 17.2], "black", deco=("vslots",))
    for x in (1.6, 13.4):
        b([x, 4.4, 21.0], [x + 0.9, 4.9, 21.1], "glow_red", light=True)
    m.cyl("y", g["turret"], 4.9, 5.3, 5.7, "dark_paint", sides=16)
    for tx in g["trackX"]:
        track_bends(m, tx - 1.3, tx + 1.3, g)
    marks(m, (0.25, 15.7), (2.4, 3.7), (-0.4, 2.6))
    # turret: low rounded dome, Kontakt-5 "clamshells" on the front arc, Shtora dazzlers
    t = dict(part="turret")
    tx, tz = g["turret"]
    m.cyl("y", (tx, tz + 1.0), 4.9, 5.6, 7.3, P, sides=16, deco=("camo",), **t)
    m.cyl("y", (tx, tz + 1.4), 4.1, 7.3, 8.1, P, sides=16, **t)
    b([3.0, 5.7, 1.8], [7.2, 7.8, 4.6], P, rot=("y", -22.5, [5.1, 6.75, 3.2]), deco=("rivets", "camo"), **t)
    b([8.8, 5.7, 1.8], [13.0, 7.8, 4.6], P, rot=("y", 22.5, [10.9, 6.75, 3.2]), deco=("rivets", "camo"), **t)
    for x0 in (4.6, 10.2):
        b([x0, 7.0, 2.7], [x0 + 1.2, 8.0, 3.7], "dark_paint", **t)
        b([x0 + 0.2, 7.15, 2.62], [x0 + 1.0, 7.85, 2.7], "glow_red", light=True, **t)
    # commander cupola (right) with remote 12.7 mm Kord, gunner sight (left), snorkel, boxes
    sx, sy, sz = g["seat"]
    m.cyl("y", (sx, sz), 1.15, 8.1, 8.8, P, sides=16, **t)
    b([sx - 0.95, 8.8, sz + 1.05], [sx + 0.95, 10.4, sz + 1.3], P, deco=("rivets",), **t)
    mx = sx + 1.5
    b([mx - 0.5, 8.4, sz - 1.4], [mx + 0.5, 9.2, sz - 0.4], "dark_paint", **t)
    b([mx - 0.4, 9.2, sz - 2.2], [mx + 0.4, 9.7, sz - 0.2], "gunmetal", **t)
    m.cyl("z", (mx, 9.45), 0.15, sz - 5.2, sz - 2.2, "blued", sides=8, **t)
    b([4.8, 8.1, 5.8], [6.8, 9.2, 7.6], P, **t)
    b([5.0, 8.3, 5.72], [6.6, 9.0, 5.8], "lens", **t)
    m.cyl("x", (7.8, 13.6), 0.45, 4.0, 12.0, "dark_paint", sides=8, **t)
    for x0 in (3.6, 10.4):
        b([x0, 5.7, 12.4], [x0 + 2.0, 7.3, 14.6], "od_paint", deco=("panel",), **t)
    smoke_launchers(m, 2.8, 7.0, 6.0, 6)
    smoke_launchers(m, 12.4, 7.0, 6.0, 6)
    antenna(m, 6.0, 13.0, 8.1, 6.0)
    m.box([2.6, 6.0, 9.0], [2.65, 7.3, 11.4], "decal", part="mark_green", deco=("star",))
    m.box([13.35, 6.0, 9.0], [13.4, 7.3, 11.4], "decal", part="mark_green", deco=("star",))
    m.box([2.6, 6.4, 7.0], [2.65, 6.9, 12.4], "decal", part="mark_red", deco=("stripe:red",))
    m.box([13.35, 6.4, 7.0], [13.4, 6.9, 12.4], "decal", part="mark_red", deco=("stripe:red",))
    # 125 mm 2A46M-5
    gy, gz = g["gun"]
    k = dict(part="gun")
    m.cyl("x", (gy, gz), 1.1, 6.4, 9.6, P, sides=8, **k)
    m.cyl("z", (8.0, gy), 0.82, 0.6, 2.6, P, sides=8, **k)
    gun_barrel(m, 8.0, gy, g["muzzle"], 0.6, 0.52, (-2.4, -9.6, -12.4), evac=(-7.8, -5.2), mrs=False)
    cx, cy, cz = g["coax"]
    b([cx - 0.2, cy - 0.2, cz - 0.1], [cx + 0.2, cy + 0.2, cz + 0.05], "black", **k)
    return m


# ===================================================================================== Challenger 2
def challenger():
    g = dict(trackX=(2.5, 13.5), wheelsZ=[-1.8 + i * 3.7 for i in range(6)], wheel=(1.9, 1.5), idler=(2.4, -4.6, 1.15),
             sprocket=(2.6, 20.3, 1.35), bottom=(0.0, -3.0), top=(3.4, -4.0), turret=(8.0, 9.4), gun=(7.6, 3.4), muzzle=-15.4,
             seat=(5.0, 6.8, 9.4), coax=(9.9, 7.2, 2.4), front=[(1.6, 5.6, -6.6), (14.4, 5.6, -6.6), (8, 5.6, -6.9)],
             back=[(2.0, 5.6, 23.4), (14.0, 5.6, 23.4)], exhaust=[(1.2, 5.0, 18.0)], smoke=(8, 6.4, 17), size=(4.2, 2.5))
    GEO["challenger"] = g
    m = Model("challenger", density=6.0, atlas_w=1024)
    b = m.box
    b([3.8, 1.0, -5.0], [12.2, 3.9, 21.0], P, deco=("grime",))
    b([1.0, 3.8, -3.4], [15.0, 6.0, 21.4], P, deco=("panel", "camo"))
    seg(m, 1.0, 15.0, (6.0, -3.4), (4.4, -6.4), 0.6, P, deco=("camo",))
    b([3.0, 1.6, -6.6], [13.0, 4.5, -6.0], P, deco=("rivets", "grime"))
    for x in (2.4, 12.2):
        b([x, 5.4, -4.0], [x + 1.4, 6.1, -3.3], "dark_paint")
        b([x + 0.25, 5.55, -4.06], [x + 1.15, 5.95, -4.0], "glow_orange", light=True)
    b([7.0, 6.0, -3.0], [9.6, 6.25, -1.2], P, deco=("rivets",))
    for x in (6.8, 7.8, 8.8):
        b([x, 6.02, -3.3], [x + 0.7, 6.3, -3.05], "lens")
    # Dorchester armour: thick flat skirt panels at the front, lighter behind
    for xs in ((0.3, 1.0), (15.0, 15.7)):
        for i in range(6):
            z0 = -4.4 + i * 4.2
            thin = i >= 3
            x0, x1 = xs
            if thin:
                x0, x1 = (0.6, 1.0) if xs[0] < 8 else (15.0, 15.4)
            b([x0, 1.4, z0 + 0.05], [x1, 4.0, z0 + 4.15], P, deco=("panel", "rivets", "camo", "grime"))
    for i in range(8):
        z = 13.4 + i * 0.95
        b([3.2, 6.0, z], [12.8, 6.15, z + 0.55], "dark_paint")
    b([1.0, 1.4, 21.4], [15.0, 6.0, 21.8], P, deco=("panel", "grime"))
    for x0, x1 in ((1.8, 7.4), (8.6, 14.2)):
        m.cyl("x", (4.6, 22.6), 0.95, x0, x1, "white", sides=8, deco=("ribs",))
        b([x0 + 0.3, 3.6, 21.8], [x0 + 0.6, 5.6, 23.4], "dark_paint")
        b([x1 - 0.6, 3.6, 21.8], [x1 - 0.3, 5.6, 23.4], "dark_paint")
    b([0.7, 4.0, 17.0], [1.0, 5.2, 19.2], "black", deco=("vslots",))
    for x in (1.6, 13.4):
        b([x, 5.0, 21.8], [x + 0.9, 5.5, 21.9], "glow_red", light=True)
    m.cyl("y", g["turret"], 4.8, 6.0, 6.4, "dark_paint", sides=16)
    for tx in g["trackX"]:
        track_bends(m, tx - 1.3, tx + 1.3, g)
    marks(m, (-0.05, 16.05), (1.8, 3.6), (0.4, 3.4), turret_side=[(1.95, 6.6, 8.2, 10.0, 13.6), (14.05, 6.6, 8.2, 10.0, 13.6)])
    # turret: slab-sided box, side bins, bustle
    t = dict(part="turret")
    b([2.6, 6.0, 3.8], [13.4, 9.0, 15.0], P, deco=("panel", "camo"), **t)
    b([3.4, 6.1, 2.6], [12.6, 8.9, 3.8], P, deco=("rivets",), **t)
    for x0 in (2.0, 13.4):
        b([x0, 6.4, 8.6], [x0 + 0.6, 8.6, 15.0], P, deco=("panel", "rivets"), **t)
    b([3.4, 6.2, 15.0], [12.6, 8.8, 18.4], P, deco=("panel",), **t)
    b([3.0, 8.8, 15.6], [13.0, 9.6, 18.0], "canvas_green", deco=("lines:1",), **t)
    # commander's panoramic sight + cupola (left), gunner's sight (right), loader's GPMG
    b([4.0, 9.0, 5.2], [6.0, 10.2, 7.2], P, **t)
    b([4.3, 9.4, 5.12], [5.7, 10.0, 5.2], "lens", **t)
    sx, sy, sz = g["seat"]
    m.cyl("y", (sx, sz), 1.2, 9.0, 9.7, P, sides=16, **t)
    b([sx - 1.0, 9.7, sz + 1.1], [sx + 1.0, 11.4, sz + 1.35], P, deco=("rivets",), **t)
    b([9.8, 9.0, 4.2], [12.0, 9.9, 6.0], P, **t)
    b([10.1, 9.2, 4.12], [11.7, 9.7, 4.2], "lens", **t)
    b([10.0, 9.0, 9.0], [12.4, 9.25, 11.4], P, deco=("rivets",), **t)
    b([11.05, 9.25, 8.4], [11.35, 10.0, 8.7], "steel", **t)
    b([10.95, 10.0, 7.2], [11.45, 10.45, 9.4], "gunmetal", **t)
    m.cyl("z", (11.2, 10.25), 0.13, 4.6, 7.2, "blued", sides=8, **t)
    smoke_launchers(m, 2.0, 7.4, 4.0, 5)
    smoke_launchers(m, 13.2, 7.4, 4.0, 5)
    antenna(m, 3.8, 14.2, 9.0, 6.5)
    antenna(m, 12.2, 14.2, 9.0, 6.5)
    # L30A1 rifled gun with the fume extractor half way
    gy, gz = g["gun"]
    k = dict(part="gun")
    b([6.0, 6.4, 1.8], [10.0, 8.8, 3.6], P, deco=("rivets",), **k)
    m.cyl("z", (8.0, gy), 0.78, -0.4, 1.8, P, sides=8, **k)
    gun_barrel(m, 8.0, gy, g["muzzle"], -0.4, 0.5, (-2.8, -11.0, -13.6), evac=(-9.0, -6.0))
    cx, cy, cz = g["coax"]
    b([cx - 0.2, cy - 0.2, cz - 0.1], [cx + 0.2, cy + 0.2, cz + 0.05], "black", **k)
    return m


TANKS = {"leopard": leopard, "t90": t90, "challenger": challenger}


# ===================================================================================== helicopters
G_, F_ = "glass_clear", "dark_paint"


def canopy_box(m, x0, x1, y0, y1, z0, z1, front_lean=True, part="body"):
    """glazed cockpit section: windscreen, side panes, roof pane and frame posts"""
    if front_lean:
        m.box([x0 + 0.2, y0, z0 - 0.15], [x1 - 0.2, y1, z0 + 0.15], G_, rot=("x", 22.5, [8, y0, z0]), part=part)
    m.box([x0, y0, z0 + 0.4], [x0 + 0.2, y1, z1], G_, part=part)
    m.box([x1 - 0.2, y0, z0 + 0.4], [x1, y1, z1], G_, part=part)
    m.box([x0 + 0.2, y1 - 0.2, z0 + 0.9], [x1 - 0.2, y1, z1], G_, part=part)
    for z in (z0 + 0.4, z1 - 0.2):
        m.box([x0 - 0.05, y0, z], [x0 + 0.25, y1, z + 0.2], F_, part=part)
        m.box([x1 - 0.25, y0, z], [x1 + 0.05, y1, z + 0.2], F_, part=part)
    m.box([7.9, y1 - 0.25, z0 + 0.9], [8.1, y1 + 0.05, z1], F_, part=part)


def wheel_leg(m, x, z, top, r=0.7, part="body"):
    m.box([x - 0.15, r, z - 0.15], [x + 0.15, top, z + 0.15], "steel", part=part)
    m.cyl("x", (r, z), r, x - 0.35, x + 0.35, "tire", sides=8, part=part)
    m.cyl("x", (r, z), r * 0.5, x - 0.4, x + 0.4, "grey_metal", sides=8, part=part)


def pod(m, x, y, z0, z1, r=0.9, part="body", mat="od_paint", tubes=7):
    m.cyl("z", (x, y), r, z0, z1, mat, sides=8, part=part)
    for k in range(tubes):
        if k == 0:
            dx = dy = 0.0
        else:
            a = math.radians(k * 360.0 / (tubes - 1))
            dx, dy = math.cos(a) * r * 0.58, math.sin(a) * r * 0.58
        m.box([x + dx - 0.14, y + dy - 0.14, z0 - 0.06], [x + dx + 0.14, y + dy + 0.14, z0 + 0.02], "black", part=part)


def blade(m, R, chord, hub_r, y, part="blade", tip="yellow"):
    """one rotor blade along +X from the hub; the renderer copies it round the hub"""
    m.box([8 + hub_r, y - 0.07, 8 - chord / 2], [8 + R - 1.0, y + 0.07, 8 + chord / 2], "rotor", part=part)
    m.box([8 + R - 1.0, y - 0.08, 8 - chord / 2 + 0.05], [8 + R, y + 0.08, 8 + chord / 2], tip, part=part)
    m.box([8 + hub_r - 0.3, y - 0.12, 8 - 0.2], [8 + hub_r + 0.6, y + 0.12, 8 + 0.2], "steel", part=part)


def hub(m, y, r=0.8, part="hub"):
    m.cyl("y", (8, 8), r, y - 0.3, y + 0.3, "steel", sides=8, part=part)
    m.cyl("y", (8, 8), 0.3, y + 0.3, y + 0.7, "bright", sides=8, part=part)


def heli_marks(m, x_left, x_right, y0, y1, z0, z1):
    for x0 in (x_left, x_right):
        m.box([x0, y0, z0], [x0 + 0.05, y1, z1], "decal", part="mark_green", deco=("star",))
        m.box([x0, y0, z0], [x0 + 0.05, y1, z1], "decal", part="mark_red", deco=("star:red",))


def apache():
    g = dict(hub=(8.0, 10.2, 2.8), rotorR=19.5, blades=4, hub2=None, tail=(7.3, 9.0, 28.6), tailR=2.8, tailBlades=4,
             chin=(8.0, 1.8, -7.0), chinLen=4.6, cannon=None, seats=[(8.0, 3.3, -5.2), (8.0, 2.3, -10.2)],
             pods=[(4.4, 3.0, -0.2), (11.6, 3.0, -0.2)], rails=[(2.0, 2.7, 0.2), (14.0, 2.7, 0.2)], bombs=[],
             flares=[(6.8, 4.6, 10.5), (9.2, 4.6, 10.5)], ends=[(8, 3.5, -14.8), (8, 7.0, 29.4)], pivotY=1.3, size=(3.6, 2.8))
    GEO["apache"] = g
    m = Model("apache", density=6.0, atlas_w=1024)
    b = m.box
    # nose: TADS/PNVS sensor turret
    b([6.7, 2.5, -14.6], [9.3, 4.1, -12.8], F_, deco=("panel",))
    b([7.0, 2.8, -14.65], [7.8, 3.6, -14.6], "lens")
    b([8.2, 2.8, -14.65], [9.0, 3.6, -14.6], "lens")
    b([7.4, 4.1, -14.0], [8.6, 4.9, -13.0], F_)
    b([7.6, 4.3, -14.05], [8.4, 4.7, -14.0], "lens")
    # copilot-gunner station (front, low) and pilot station (rear, raised)
    b([6.2, 2.4, -12.8], [9.8, 5.0, -7.6], P, deco=("panel", "camo"))
    canopy_box(m, 6.2, 9.8, 5.0, 6.8, -12.8, -7.8)
    b([6.2, 2.4, -7.6], [9.8, 5.6, -3.0], P, deco=("panel", "camo"))
    canopy_box(m, 6.2, 9.8, 5.6, 8.0, -7.6, -3.2)
    b([6.5, 4.4, -12.2], [9.5, 5.2, -11.6], F_)
    b([6.9, 4.8, -11.65], [9.1, 5.15, -11.55], "screen", light=True)
    b([6.5, 5.2, -6.9], [9.5, 6.0, -6.3], F_)
    b([6.9, 5.6, -6.35], [9.1, 5.95, -6.25], "screen_red", light=True)
    # centre fuselage, mast fairing, Longbow radar dome above the rotor
    b([5.8, 2.0, -3.0], [10.2, 6.6, 8.0], P, deco=("panel", "camo", "grime"))
    b([6.4, 6.6, -2.4], [9.6, 8.2, 7.0], P, deco=("panel",))
    m.cyl("y", (8.0, 2.8), 0.55, 8.2, 10.0, "steel", sides=8)
    m.cyl("y", (8.0, 2.8), 0.3, 10.0, 11.2, "steel", sides=8)
    m.cyl("y", (8.0, 2.8), 1.5, 11.2, 12.2, P, sides=16)
    m.cyl("y", (8.0, 2.8), 1.1, 12.2, 12.6, P, sides=16)
    # engine nacelles with IR-suppressing "black hole" exhausts
    for x0, x1, sgn in ((3.6, 5.8, -1), (10.2, 12.4, 1)):
        b([x0, 4.6, -1.0], [x1, 7.0, 7.0], P, deco=("panel", "camo"))
        b([x0 + 0.3, 5.0, -1.06], [x1 - 0.3, 6.6, -1.0], "black")
        b([x0 + 0.1, 5.0, 6.8], [x1 - 0.1, 6.6, 9.0], F_, rot=("y", 22.5 * sgn, [(x0 + x1) / 2, 5.8, 7.9]), deco=("vslots",))
    # stub wings: Hydra pods inboard, Hellfire racks outboard
    b([1.2, 4.0, 1.0], [5.8, 4.5, 4.0], P)
    b([10.2, 4.0, 1.0], [14.8, 4.5, 4.0], P)
    for (px, py, pz) in g["pods"]:
        b([px - 0.25, 3.8, 1.2], [px + 0.25, 4.0, 3.4], F_)
        pod(m, px, py, pz, pz + 4.6, r=0.9, tubes=7)
    for (rx, ry, rz) in g["rails"]:
        b([rx - 1.0, 3.4, rz + 0.2], [rx + 1.0, 3.9, rz + 4.2], F_, deco=("panel",))
        for dx in (-0.5, 0.5):
            for dy in (-0.3, -0.95):
                m.cyl("z", (rx + dx, 3.4 + dy), 0.27, rz, rz + 4.0, "od_paint", sides=8)
                b([rx + dx - 0.15, 3.4 + dy - 0.15, rz - 0.06], [rx + dx + 0.15, 3.4 + dy + 0.15, rz], "lens")
    # landing gear: main wheels under the cockpits, tail wheel
    for x in (5.4, 10.6):
        wheel_leg(m, x, -6.0, 2.6, r=0.75)
    wheel_leg(m, 8.0, 26.6, 3.8, r=0.5)
    # tail: boom, swept fin, stabilator at the base of the fin
    b([7.0, 3.4, 8.0], [9.0, 5.6, 18.0], P, deco=("camo",))
    b([7.3, 3.8, 18.0], [8.7, 5.4, 26.4], P)
    b([7.6, 5.0, 25.4], [8.4, 10.4, 27.6], P, rot=("x", 22.5, [8, 5.0, 26.5]), deco=("camo",))
    b([4.4, 4.6, 25.4], [11.6, 4.85, 27.6], P)
    b([7.8, 5.4, 22.0], [8.2, 5.65, 22.4], "glow_red", light=True)
    b([1.0, 4.05, 2.0], [1.2, 4.45, 2.6], "glow_red", light=True)
    b([14.8, 4.05, 2.0], [15.0, 4.45, 2.6], "glow_green", light=True)
    for fx, fy, fz in g["flares"]:
        b([fx - 0.25, fy - 0.4, fz - 0.6], [fx + 0.25, fy + 0.4, fz + 0.6], F_, deco=("vlines:3",))
    heli_marks(m, 6.95, 9.0, 3.6, 5.4, 8.6, 11.4)
    m.box([6.95, 3.9, 12.0], [7.0, 5.2, 16.0], "decal", part="mark_red", deco=("stripe:red",))
    m.box([9.0, 3.9, 12.0], [9.05, 5.2, 16.0], "decal", part="mark_red", deco=("stripe:red",))
    m.box([6.95, 3.9, 12.0], [7.0, 5.2, 16.0], "decal", part="mark_green", deco=("stripe:white",))
    m.box([9.0, 3.9, 12.0], [9.05, 5.2, 16.0], "decal", part="mark_green", deco=("stripe:white",))
    # M230 30 mm chain gun turret (yaw + pitch about the chin pivot)
    cx, cy, cz = g["chin"]
    m.cyl("y", (cx, cz), 0.75, cy - 0.4, cy + 0.6, F_, sides=8, part="chin")
    b([cx - 0.35, cy - 0.45, cz - 2.6], [cx + 0.35, cy + 0.05, cz - 0.3], "gunmetal", part="chin")
    m.cyl("z", (cx, cy - 0.2), 0.17, cz - g["chinLen"], cz - 2.6, "blued", sides=8, part="chin")
    m.cyl("z", (cx, cy - 0.2), 0.24, cz - g["chinLen"] - 0.3, cz - g["chinLen"], "gunmetal", sides=8, part="chin")
    # rotors: hub + one blade (copied round), tail rotor hub + blade
    hub(m, 8.0)
    blade(m, g["rotorR"], 0.62, 0.9, 8.0)
    m.cyl("x", (8, 8), 0.3, 7.8, 8.3, "steel", sides=8, part="tail_hub")
    m.box([7.9, 8.0 - 0.18, 8.3], [8.0, 8.0 + 0.18, 8.0 + g["tailR"]], "rotor", part="tail_blade")
    return m


def hind():
    g = dict(hub=(8.0, 10.6, 5.0), rotorR=20.5, blades=5, hub2=None, tail=(7.25, 9.4, 28.9), tailR=2.6, tailBlades=3,
             chin=(8.0, 1.9, -12.6), chinLen=2.0, cannon=None, seats=[(8.0, 3.2, -8.6), (8.0, 2.2, -12.8), (6.6, 2.0, -2.0), (9.4, 2.0, -2.0)],
             pods=[(4.2, 3.8, -0.7), (11.8, 3.8, -0.7)], rails=[(0.6, 4.2, 0.0), (15.4, 4.2, 0.0)], bombs=[(2.4, 3.4, 2.0), (13.6, 3.4, 2.0)],
             flares=[(6.9, 4.8, 12.0), (9.1, 4.8, 12.0)], ends=[(8, 3.4, -15.6), (8, 7.0, 31.0)], pivotY=1.5, size=(3.8, 3.0))
    GEO["hind"] = g
    m = Model("hind", density=6.0, atlas_w=1024)
    b = m.box
    # "double bubble": gunner's glazed nose, pilot's raised canopy behind
    b([6.6, 2.4, -15.0], [9.4, 4.4, -10.8], P, deco=("panel", "camo"))
    canopy_box(m, 6.6, 9.4, 4.4, 6.6, -15.0, -11.0)
    b([6.9, 2.6, -15.4], [7.6, 3.4, -15.0], F_)
    b([8.4, 2.6, -15.4], [9.1, 3.4, -15.0], F_)
    b([6.4, 2.4, -10.8], [9.6, 5.4, -6.4], P, deco=("panel", "camo"))
    canopy_box(m, 6.4, 9.6, 5.4, 7.8, -10.8, -6.6)
    b([6.7, 5.0, -10.2], [9.3, 5.8, -9.6], F_)
    b([7.0, 5.4, -9.65], [9.0, 5.75, -9.55], "screen", light=True)
    # troop cabin with windows and doors
    b([5.4, 1.6, -6.4], [10.6, 7.4, 9.0], P, deco=("panel", "camo", "grime"))
    for xw in (5.35, 10.6):
        for i in range(3):
            z = -4.6 + i * 2.2
            b([xw, 4.6, z], [xw + 0.05, 5.8, z + 1.4], "lens")
        b([xw, 2.0, 2.8], [xw + 0.05, 6.4, 5.6], F_, deco=("panel",))
    # engine deck: intakes with dust protectors, exhausts
    b([6.2, 7.4, -4.0], [9.8, 9.2, 10.0], P, deco=("panel",))
    for x in (6.6, 9.4):
        m.cyl("z", (x, 8.3), 0.85, -4.6, -3.9, F_, sides=8)
        m.cyl("z", (x, 8.3), 0.45, -4.8, -4.6, "steel", sides=8)
    for x0 in (5.3, 10.1):
        b([x0, 7.4, 6.0], [x0 + 0.6, 8.6, 9.4], F_, deco=("vslots",))
    m.cyl("y", (8.0, 5.0), 0.6, 9.2, 10.4, "steel", sides=8)
    # stepped wings: UB-32 rocket pods, FAB-250 bombs, Shturm launch rails at the tips
    b([3.0, 5.0, 0.0], [5.4, 5.5, 4.0], P)
    b([0.4, 4.6, 0.2], [3.0, 5.1, 3.8], P)
    b([10.6, 5.0, 0.0], [13.0, 5.5, 4.0], P)
    b([13.0, 4.6, 0.2], [15.6, 5.1, 3.8], P)
    for (px, py, pz) in g["pods"]:
        b([px - 0.25, 4.6, 1.0], [px + 0.25, 5.0, 3.0], F_)
        pod(m, px, py, pz, pz + 5.0, r=1.0, tubes=9)
    for (bx, by, bz) in g["bombs"]:
        b([bx - 0.2, 4.2, 1.0], [bx + 0.2, 4.6, 3.0], F_)
        m.cyl("z", (bx, by), 0.75, bz - 2.0, bz + 1.6, "od_paint", sides=8)
        m.cyl("z", (bx, by), 0.45, bz - 2.6, bz - 2.0, "od_paint", sides=8)
        b([bx - 0.85, by - 0.05, bz + 1.4], [bx + 0.85, by + 0.05, bz + 2.4], "od_paint")
        b([bx - 0.05, by - 0.85, bz + 1.4], [bx + 0.05, by + 0.85, bz + 2.4], "od_paint")
    for (rx, ry, rz) in g["rails"]:
        b([rx - 0.35, ry - 0.6, rz], [rx + 0.35, ry + 0.4, rz + 3.8], F_)
        for dy in (-0.3, 0.2):
            m.cyl("z", (rx, ry + dy), 0.22, rz - 0.2, rz + 3.6, "od_paint", sides=8)
    # landing gear
    wheel_leg(m, 8.0, -12.4, 2.4, r=0.6)
    for x in (4.6, 11.4):
        wheel_leg(m, x, 1.0, 2.6, r=0.8)
    # tail: boom, horizontal stabilisers, swept fin, tail rotor on the left
    b([7.0, 4.0, 9.0], [9.0, 6.6, 20.0], P, deco=("camo",))
    b([7.3, 4.4, 20.0], [8.7, 6.2, 27.6], P)
    b([7.6, 5.4, 26.0], [8.4, 11.2, 28.4], P, rot=("x", 22.5, [8, 5.4, 27.2]), deco=("camo",))
    b([4.6, 4.8, 21.0], [11.4, 5.1, 23.0], P)
    b([7.85, 3.0, 26.6], [8.15, 4.4, 27.0], F_)
    b([7.8, 6.2, 18.0], [8.2, 6.45, 18.4], "glow_red", light=True)
    b([0.2, 4.65, 1.6], [0.4, 5.05, 2.2], "glow_red", light=True)
    b([15.6, 4.65, 1.6], [15.8, 5.05, 2.2], "glow_green", light=True)
    for fx, fy, fz in g["flares"]:
        b([fx - 0.25, fy - 0.5, fz - 0.7], [fx + 0.25, fy + 0.3, fz + 0.7], F_, deco=("vlines:3",))
    heli_marks(m, 5.3, 10.65, 3.8, 6.4, -1.8, 1.8)
    m.box([6.95, 4.6, 13.0], [7.0, 6.0, 18.0], "decal", part="mark_red", deco=("stripe:red",))
    m.box([9.0, 4.6, 13.0], [9.05, 6.0, 18.0], "decal", part="mark_red", deco=("stripe:red",))
    # YakB-12.7 four-barrel gatling in the chin turret
    cx, cy, cz = g["chin"]
    m.cyl("y", (cx, cz), 0.85, cy - 0.5, cy + 0.5, F_, sides=8, part="chin")
    b([cx - 0.4, cy - 0.55, cz - 1.4], [cx + 0.4, cy + 0.15, cz - 0.3], "gunmetal", part="chin")
    for dx, dy in ((-0.15, -0.35), (0.15, -0.35), (-0.15, -0.05), (0.15, -0.05)):
        m.cyl("z", (cx + dx, cy + dy), 0.09, cz - g["chinLen"] - 1.2, cz - 1.4, "blued", sides=4, part="chin")
    m.cyl("z", (cx, cy - 0.2), 0.32, cz - g["chinLen"] - 0.8, cz - g["chinLen"] - 0.5, "gunmetal", sides=8, part="chin")
    hub(m, 8.0, r=1.0)
    blade(m, g["rotorR"], 0.7, 1.1, 8.0)
    m.cyl("x", (8, 8), 0.3, 7.7, 8.3, "steel", sides=8, part="tail_hub")
    m.box([7.75, 8.0 - 0.2, 8.3], [7.85, 8.0 + 0.2, 8.0 + g["tailR"]], "rotor", part="tail_blade")
    return m


def ka52():
    g = dict(hub=(8.0, 11.8, 2.0), rotorR=18.0, blades=3, hub2=9.6, tail=None, tailR=0, tailBlades=0,
             chin=None, chinLen=0, cannon=(10.8, 3.2, -9.2), seats=[(6.9, 2.6, -9.6), (9.1, 2.6, -9.6)],
             pods=[(4.8, 3.3, -0.9), (11.2, 3.3, -0.9)], rails=[(2.6, 3.5, -0.5), (13.4, 3.5, -0.5)], bombs=[],
             flares=[(1.2, 4.5, 2.0), (14.8, 4.5, 2.0)], ends=[(8, 4.0, -15.4), (8, 6.0, 24.4)], pivotY=1.4, size=(3.4, 3.0))
    GEO["ka52"] = g
    m = Model("ka52", density=6.0, atlas_w=1024)
    b = m.box
    # nose radar and sensor ball, side-by-side cockpit
    m.cyl("z", (8.0, 4.0), 1.3, -14.8, -13.4, P, sides=16)
    m.cyl("z", (8.0, 4.0), 0.8, -15.3, -14.8, P, sides=8)
    m.cyl("y", (8.0, -12.4), 0.7, 1.6, 2.4, F_, sides=8)
    b([7.6, 1.8, -13.15], [8.4, 2.3, -13.05], "lens")
    b([5.8, 2.4, -13.4], [10.2, 5.0, -7.0], P, deco=("panel", "camo"))
    canopy_box(m, 5.8, 10.2, 5.0, 7.2, -13.4, -7.2)
    b([6.1, 4.4, -12.4], [9.9, 5.2, -11.8], F_)
    b([6.4, 4.8, -11.85], [7.8, 5.15, -11.75], "screen", light=True)
    b([8.2, 4.8, -11.85], [9.6, 5.15, -11.75], "screen", light=True)
    # fuselage, engines with exhaust deflectors, tall coaxial mast
    b([6.0, 2.2, -7.0], [10.0, 6.4, 6.0], P, deco=("panel", "camo", "grime"))
    b([6.4, 6.4, -6.0], [9.6, 7.8, 5.0], P, deco=("panel",))
    for x0, x1 in ((4.0, 6.0), (10.0, 12.0)):
        b([x0, 4.8, -3.0], [x1, 6.8, 4.0], P, deco=("panel", "camo"))
        b([x0 + 0.3, 5.1, -3.06], [x1 - 0.3, 6.5, -3.0], "black")
        b([x0 + 0.2, 6.0, 4.0], [x1 - 0.2, 7.0, 6.0], F_, rot=("x", -22.5, [(x0 + x1) / 2, 6.5, 5.0]), deco=("vslots",))
    m.cyl("y", (8.0, 2.0), 0.55, 7.8, 12.2, "steel", sides=8)
    m.cyl("y", (8.0, 2.0), 0.8, 10.2, 11.2, "steel", sides=8)
    # 2A42 30 mm cannon on the right side
    cx, cy, cz = g["cannon"]
    b([cx - 0.6, cy - 0.5, -4.0], [cx + 0.5, cy + 0.6, 2.0], F_, deco=("panel",))
    m.cyl("z", (cx, cy), 0.22, cz + 0.6, -4.0, "blued", sides=8)
    b([cx - 0.35, cy - 0.3, cz], [cx + 0.35, cy + 0.3, cz + 0.6], "gunmetal", deco=("vslots",))
    # wings: S-8 pods, Vikhr launchers, Vitebsk countermeasure pods at the tips
    b([1.6, 4.4, 0.0], [6.0, 4.8, 3.2], P)
    b([10.0, 4.4, 0.0], [14.4, 4.8, 3.2], P)
    for (px, py, pz) in g["pods"]:
        b([px - 0.25, 4.2, 0.6], [px + 0.25, 4.4, 2.6], F_)
        pod(m, px, py, pz, pz + 4.8, r=0.9, tubes=7)
    for (rx, ry, rz) in g["rails"]:
        b([rx - 0.8, ry - 0.5, rz], [rx + 0.8, ry + 0.6, rz + 4.6], "od_paint", deco=("panel",))
        for dx in (-0.4, 0.0, 0.4):
            for dy in (-0.2, 0.3):
                b([rx + dx - 0.14, ry + dy - 0.14, rz - 0.06], [rx + dx + 0.14, ry + dy + 0.14, rz], "black")
    for (fx, fy, fz) in g["flares"]:
        m.cyl("z", (fx, fy), 0.35, fz - 2.0, fz + 1.6, F_, sides=8)
    # landing gear
    wheel_leg(m, 8.0, -9.4, 2.4, r=0.55)
    for x in (5.4, 10.6):
        wheel_leg(m, x, 2.0, 2.4, r=0.75)
    # tail: boom, stabiliser with end plate fins, central fin (no tail rotor: coaxial rotors)
    b([7.1, 3.8, 6.0], [8.9, 5.8, 16.0], P, deco=("camo",))
    b([7.3, 4.0, 16.0], [8.7, 5.6, 23.4], P)
    b([3.0, 4.8, 21.0], [13.0, 5.1, 23.0], P)
    b([2.8, 4.0, 20.6], [3.2, 7.4, 23.4], P, deco=("camo",))
    b([12.8, 4.0, 20.6], [13.2, 7.4, 23.4], P, deco=("camo",))
    b([7.6, 5.4, 21.4], [8.4, 8.4, 23.6], P, rot=("x", 22.5, [8, 5.4, 22.5]))
    b([7.8, 5.6, 14.0], [8.2, 5.85, 14.4], "glow_red", light=True)
    heli_marks(m, 5.95, 10.0, 3.0, 5.6, -5.6, -1.4)
    m.box([7.05, 4.1, 9.0], [7.1, 5.4, 14.0], "decal", part="mark_red", deco=("stripe:red",))
    m.box([8.9, 4.1, 9.0], [8.95, 5.4, 14.0], "decal", part="mark_red", deco=("stripe:red",))
    hub(m, 8.0, r=0.9)
    blade(m, g["rotorR"], 0.66, 1.0, 8.0)
    return m


HELIS = {"apache": apache, "hind": hind, "ka52": ka52}
