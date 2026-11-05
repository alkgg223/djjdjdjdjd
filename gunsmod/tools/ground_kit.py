"""Ground equipment detail kit for meshkit models (model units, 16 = one block, front -Z): tyres with tread lugs, rims
and wheel nuts; a truck cab with split windscreen, side windows, doors, mirrors, grille, lamps and bumper; chassis
rails, mudguards, fuel tanks, outrigger jacks, railings, ladders, equipment cabinets, generators, cable reels, whip
antennas, a shelter container; tracked running gear with link texture, road wheels, sprocket, idler and rollers."""
import math

import meshkit as K
from meshkit import v3


# ------------------------------------------------------------------------------------------------ wheels
def tyre(m, x, y, z, r, w, side=1, rim="olive", lugs=12):
    """an off-road tyre on the x axis at (x, y, z), its rim and nuts on the `side` (+1 = +x) face"""
    K.cylinder("x", (y, z), r - 0.35, x - w / 2, x + w / 2, "rubber", n=18, bevel=min(0.7, w * 0.18), m=m)
    hw = w * 0.42
    for k in range(lugs):
        a = 2 * math.pi * (k + 0.5) / lugs
        ca, sa = math.cos(a), math.sin(a)
        cy, cz = y + sa * (r - 0.12), z + ca * (r - 0.12)
        ty, tz = ca, -sa
        ch = 0.35 if k % 2 else -0.35
        L = min(0.7, r * 0.18)

        def P(xx, tt):
            return v3(xx, cy + ty * tt, cz + tz * tt)
        K.plate([P(x - hw, -L + ch), P(x + hw, -L - ch), P(x + hw, L - ch), P(x - hw, L + ch)], 0.5, "rubber", m=m)
    # rim: recessed disc, the hub boss and the nut circle on the outer face
    fx = x + side * (w / 2)
    K.cylinder("x", (y, z), r * 0.56, fx - side * 0.5, fx + side * 0.05, rim, n=14, m=m)
    K.cylinder("x", (y, z), r * 0.2, fx, fx + side * 0.5, "steel", n=8, m=m)
    for k in range(6):
        a = 2 * math.pi * k / 6
        K.cylinder("x", (y + math.sin(a) * r * 0.34, z + math.cos(a) * r * 0.34), 0.22, fx, fx + side * 0.3, "steel", n=4, m=m)


def mudguard(m, x0, x1, y, z, r, mat="olive"):
    """a guard arching over a wheel at (y, z) of radius r, from x0 to x1, with a rubber flap behind"""
    rr = r + 1.2
    pts = [(math.radians(a), ) for a in (20, 60, 100, 140, 160)]
    for (a0,), (a1,) in zip(pts, pts[1:]):
        p0 = (y + math.sin(a0) * rr, z - math.cos(a0) * rr)
        p1 = (y + math.sin(a1) * rr, z - math.cos(a1) * rr)
        K.plate([v3(x0, p0[0], p0[1]), v3(x1, p0[0], p0[1]), v3(x1, p1[0], p1[1]), v3(x0, p1[0], p1[1])], 0.4, mat, m=m)
    a = math.radians(160)
    K.box((x0 + 0.3, y - r * 0.3, z - math.cos(a) * rr - 0.15), (x1 - 0.3, y + math.sin(a) * rr, z - math.cos(a) * rr + 0.15), "rubber", m=m)


# ------------------------------------------------------------------------------------------------ chassis bits
def rails(m, w, z0, z1, y0, y1, mat="dark", cross=10.0):
    """two ladder-frame rails and their cross members"""
    for s in (-1, 1):
        x = 8 + s * w / 2
        K.box((x - 1.2, y0, z0), (x + 1.2, y1, z1), mat, m=m)
    z = z0 + 3
    while z < z1 - 2:
        K.box((8 - w / 2, y0 + 0.5, z), (8 + w / 2, y1 - 0.5, z + 1.2), mat, m=m)
        z += cross


def outrigger(m, x_in, x_out, y_beam, z, ground=0.0, mat="olive"):
    """a stabiliser: a beam run out sideways, the jack down to the ground, the foot pad"""
    lo, hi = min(x_in, x_out), max(x_in, x_out)
    K.box((lo, y_beam - 1.0, z - 1.0), (hi, y_beam + 1.0, z + 1.0), mat, m=m)
    K.cylinder("y", (x_out, z), 0.9, ground + 0.6, y_beam + 1.6, "steel", n=8, m=m)
    K.cylinder("y", (x_out, z), 1.25, y_beam - 3.0, y_beam + 1.8, mat, n=8, m=m)
    K.cylinder("y", (x_out, z), 2.6, ground, ground + 0.6, "dark", n=10, m=m)


def railing(m, x0, x1, z0, z1, y, h=5.0, sides="xXzZ", mat="steel", step=7.0):
    """hand rail posts and a top rail round a deck edge"""
    def run(a, b):
        n = max(1, int(math.dist(a, b) / step))
        for k in range(n + 1):
            p = (a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n)
            K.cylinder("y", p, 0.25, y, y + h, mat, n=4, m=m)
        K.tube([(a[0], y + h, a[1]), (b[0], y + h, b[1])], 0.25, mat, n=4, m=m)
        K.tube([(a[0], y + h * 0.5, a[1]), (b[0], y + h * 0.5, b[1])], 0.18, mat, n=4, m=m)
    if "x" in sides:
        run((x0, z0), (x0, z1))
    if "X" in sides:
        run((x1, z0), (x1, z1))
    if "z" in sides:
        run((x0, z0), (x1, z0))
    if "Z" in sides:
        run((x0, z1), (x1, z1))


def ladder(m, x, z, y0, y1, w=3.0, along="x", mat="steel"):
    """a ladder standing at (x, z) from y0 to y1, its rungs running along `along`"""
    for s in (-1, 1):
        p = (x + s * w / 2, z) if along == "x" else (x, z + s * w / 2)
        K.cylinder("y", p, 0.22, y0, y1, mat, n=4, m=m)
    y = y0 + 1.5
    while y < y1 - 0.5:
        if along == "x":
            K.cylinder("x", (y, z), 0.16, x - w / 2, x + w / 2, mat, n=4, m=m)
        else:
            K.cylinder("z", (x, y), 0.16, z - w / 2, z + w / 2, mat, n=4, m=m)
        y += 2.0


def cabinet(m, c0, c1, mat="olive", face="z", vents=True, handles=2):
    """an equipment box: rounded body, door seam, vent grille and handles on the `face` side (x, X, z or Z)"""
    K.rbox(c0, c1, 0.4, mat, seg=1, m=m)
    lo = [min(a, b) for a, b in zip(c0, c1)]
    hi = [max(a, b) for a, b in zip(c0, c1)]
    ax = "xyz".index(face.lower())
    at = lo[ax] - 0.06 if face.islower() else hi[ax] + 0.06
    o = [i for i in range(3) if i != ax]
    a0, a1 = lo[o[0]], hi[o[0]]
    b0, b1 = lo[o[1]], hi[o[1]]

    def bx(u0, u1, w0, w1, mm, d=0.1):
        p, q = [0.0] * 3, [0.0] * 3
        p[ax], q[ax] = at - d / 2, at + d / 2
        p[o[0]], q[o[0]] = u0, u1
        p[o[1]], q[o[1]] = w0, w1
        K.box(p, q, mm, m=m)
    mid = (a0 + a1) / 2
    bx(mid - 0.08, mid + 0.08, b0 + 0.6, b1 - 0.6, "dark")
    if vents:
        bx(a0 + 1.0, mid - 0.8, b0 + 1.0, b0 + 1.0 + (b1 - b0) * 0.3, "grille")
    for k in range(handles):
        u = mid - 0.9 if k == 0 else mid + 0.9
        bx(u - 0.15, u + 0.15, (b0 + b1) / 2 - 0.8, (b0 + b1) / 2 + 0.8, "steel", 0.5)


def generator(m, x0, y0, z0, x1, y1, z1, mat="olive"):
    """a diesel generator set: the housing, louvres both sides, the exhaust and its rain cap, a fuel cap"""
    K.rbox((x0, y0, z0), (x1, y1, z1), 0.8, mat, seg=1, m=m)
    for x, f in ((x0 - 0.06, "x"), (x1 + 0.06, "X")):
        K.box((x - 0.05, y0 + 1.2, z0 + 1.5), (x + 0.05, y1 - 1.2, z0 + (z1 - z0) * 0.45), "grille", m=m, faces=f)
    ex = (x0 + 2, z1 - 2)
    K.cylinder("y", ex, 0.6, y1, y1 + 3.0, "burnt_metal", n=6, m=m)
    K.box((ex[0] - 0.8, y1 + 3.0, ex[1] - 0.8), (ex[0] + 0.8, y1 + 3.2, ex[1] + 0.8), "dark", m=m)
    K.cylinder("y", (x1 - 2, z0 + 2), 0.5, y1, y1 + 0.5, "steel", n=6, m=m)


def cable_reel(m, x, y, z, r=2.5, w=2.0, mat="olive"):
    """a drum of cable on the x axis"""
    for s in (-1, 1):
        K.cylinder("x", (y, z), r, x + s * w / 2 - 0.15, x + s * w / 2 + 0.15, mat, n=12, m=m)
    K.cylinder("x", (y, z), r * 0.75, x - w / 2, x + w / 2, "black", n=12, m=m)


def whip(m, x, y, z, h=16.0):
    """a whip antenna on its spring base"""
    K.cylinder("y", (x, z), 0.45, y, y + 1.2, "dark", n=6, m=m)
    K.tube([(x, y + 1.2, z), (x, y + h, z + h * 0.06)], 0.1, "black", n=4, m=m)


def lamp(m, x, y, z, w, h, mat="white", face="z"):
    """a lamp in its black bezel on the front (z) or rear (Z) face"""
    d = -1 if face == "z" else 1
    K.box((x - w / 2 - 0.3, y - h / 2 - 0.3, z), (x + w / 2 + 0.3, y + h / 2 + 0.3, z + d * 0.3), "black", m=m)
    K.box((x - w / 2, y - h / 2, z + d * 0.3), (x + w / 2, y + h / 2, z + d * 0.4), mat, m=m)


def shelter(m, x0, y0, z0, x1, y1, z1, mat="olive", door_side="X"):
    """an ISO-style equipment shelter: corner castings, a door with hinges, vents, an air conditioner, a ladder"""
    K.rbox((x0, y0, z0), (x1, y1, z1), 0.5, mat, seg=1, m=m)
    for x in (x0, x1):
        for y in (y0, y1):
            for z in (z0, z1):
                K.box((x - 0.8, y - 0.8, z - 0.8), (x + 0.8, y + 0.8, z + 0.8), "dark", m=m)
    # ribs on the long sides
    z = z0 + 4
    while z < z1 - 3:
        for x, f in ((x0, "x"), (x1, "X")):
            K.box((x - 0.15, y0 + 0.6, z), (x + 0.15, y1 - 0.6, z + 0.5), mat, m=m)
        z += 5
    dx = x1 + 0.1 if door_side == "X" else x0 - 0.1
    dz = (z0 + z1) / 2
    K.box((dx - 0.1, y0 + 1.0, dz - 3.2), (dx + 0.1, y1 - 1.5, dz + 3.2), "dark", m=m)
    K.box((dx - 0.2, y0 + 1.3, dz - 2.9), (dx + 0.2, y1 - 1.8, dz + 2.9), mat, m=m)
    K.box((dx - 0.4, (y0 + y1) / 2 - 0.2, dz + 2.0), (dx + 0.4, (y0 + y1) / 2 + 0.2, dz + 2.6), "steel", m=m)
    for yy in (y0 + 2.5, y1 - 3.5):
        K.box((dx - 0.3, yy, dz - 3.4), (dx + 0.3, yy + 0.8, dz - 3.0), "steel", m=m)
    # air conditioner on the back wall, vents high on the front
    K.rbox((x0 + 2, y0 + 2, z1 + 0.1), (x0 + 8, y0 + 8, z1 + 3.0), 0.4, "light_grey", seg=1, m=m)
    K.box((x0 + 2.6, y0 + 2.6, z1 + 3.0), (x0 + 7.4, y0 + 7.4, z1 + 3.1), "grille", m=m, faces="Z")
    K.box((x0 + 2, y1 - 3.5, z0 - 0.1), (x1 - 2, y1 - 1.5, z0 + 0.1), "grille", m=m, faces="z")


# ------------------------------------------------------------------------------------------------ truck
def cab(m, x0, x1, z0, z1, y0, y1, mat="olive"):
    """a forward-control military truck cab, front face at z0"""
    h = y1 - y0
    yb = y0 + h * 0.5          # waist line: windows above
    K.rbox((x0, y0, z0 + 1.5), (x1, yb, z1), 1.5, mat, seg=2, m=m)
    # upper cab leans back a little over the windscreen
    K.loft([[v3(x0 + 0.5, yb, z0 + 1.6), v3(x1 - 0.5, yb, z0 + 1.6), v3(x1 - 0.5, yb, z1), v3(x0 + 0.5, yb, z1)],
            [v3(x0 + 1.0, y1, z0 + 3.4), v3(x1 - 1.0, y1, z0 + 3.4), v3(x1 - 1.0, y1, z1), v3(x0 + 1.0, y1, z1)]], mat, caps=(False, True), m=m)
    # split windscreen, its centre pillar and the wipers
    for s in (-1, 1):
        xa, xb = (x0 + 2.0, 7.6) if s < 0 else (8.4, x1 - 2.0)
        lo = (yb + 0.8, z0 + 1.6 + 0.3)
        hi = (y1 - 1.2, z0 + 3.4 - 0.3 * (1.2 / h))
        K.plate([v3(xa, lo[0], lo[1] - 0.1), v3(xb, lo[0], lo[1] - 0.1), v3(xb, hi[0], hi[1] - 0.1), v3(xa, hi[0], hi[1] - 0.1)], 0.12, "glass_dark", m=m)
        wx = (xa + xb) / 2
        K.tube([(wx - 1.5, lo[0] + 0.2, lo[1] - 0.3), (wx + 1.0, lo[0] + 3.0, lo[1] + 0.3)], 0.1, "black", n=4, m=m)
    # side windows and doors
    for x, f in ((x0 - 0.06, "x"), (x1 + 0.06, "X")):
        K.box((x - 0.05, yb + 0.8, z0 + 4.0), (x + 0.05, y1 - 1.5, z0 + (z1 - z0) * 0.62), "glass_dark", m=m, faces=f)
        K.box((x - 0.07, y0 + 1.5, z0 + 3.0), (x + 0.07, y1 - 0.8, z0 + 3.2), "dark", m=m, faces=f)
        K.box((x - 0.07, y0 + 1.5, z0 + (z1 - z0) * 0.7), (x + 0.07, y1 - 0.8, z0 + (z1 - z0) * 0.7 + 0.2), "dark", m=m, faces=f)
        K.box((x - 0.35, yb - 1.4, z0 + (z1 - z0) * 0.6), (x + 0.35, yb - 0.9, z0 + (z1 - z0) * 0.6 + 1.2), "steel", m=m)
        # two steps under the door
        sx = x - 1.2 if f == "x" else x + 1.2
        for k, yy in enumerate((y0 - 2.0, y0 + 1.0)):
            K.box((min(x, sx), yy, z0 + 4.5), (max(x, sx), yy + 0.4, z0 + 8.5), "tread_plate", m=m)
        # mirror on its arm
        mx = x - 3.0 if f == "x" else x + 3.0
        K.tube([(x, yb + 2.0, z0 + 2.5), (mx, yb + 3.0, z0 + 2.0)], 0.2, "dark", n=4, m=m)
        K.rbox((mx - 0.5, yb + 1.5, z0 + 1.6), (mx + 0.5, yb + 5.0, z0 + 2.4), 0.2, "dark", seg=1, m=m)
        K.box((mx - 0.4, yb + 1.7, z0 + 1.55), (mx + 0.4, yb + 4.8, z0 + 1.6), "glass", m=m, faces="z")
    # grille, lamps, bumper, tow hooks
    K.box((8 - 5.0, y0 + 3.0, z0 + 1.4), (8 + 5.0, yb - 1.0, z0 + 1.5), "grille", m=m, faces="z")
    for s in (-1, 1):
        lamp(m, 8 + s * (x1 - x0) * 0.36, y0 + 4.5, z0 + 1.5, 2.2, 1.8, "white")
        lamp(m, 8 + s * (x1 - x0) * 0.36, y0 + 2.4, z0 + 1.5, 1.4, 0.7, "amber")
        K.tube([(8 + s * 6, y0 + 0.5, z0 - 0.4), (8 + s * 6, y0 - 0.6, z0 - 1.2), (8 + s * 6, y0 - 1.4, z0 - 0.4)], 0.35, "dark", n=5, m=m)
    K.rbox((x0 - 0.5, y0 - 1.5, z0 - 0.8), (x1 + 0.5, y0 + 1.2, z0 + 1.5), 0.5, "dark", seg=1, m=m)
    # sun visor, roof hatch and beacon, the whip antenna
    K.plate([v3(x0 + 1, y1 - 0.2, z0 + 3.0), v3(x1 - 1, y1 - 0.2, z0 + 3.0), v3(x1 - 1, y1 + 0.2, z0 + 1.8), v3(x0 + 1, y1 + 0.2, z0 + 1.8)], 0.25, mat, m=m)
    K.cylinder("y", (8 + (x1 - x0) * 0.2, (z0 + z1) / 2 + 2), 2.0, y1, y1 + 0.5, mat, n=10, m=m)
    K.cylinder("y", (x0 + 3, z1 - 2), 0.6, y1, y1 + 1.0, "amber", n=6, m=m)
    whip(m, x1 - 2, y1, z1 - 2, 18)
    # air intake snorkel and exhaust stack behind the cab
    K.tube([(x0 + 1.5, y0 + 4, z1 + 1.0), (x0 + 1.5, y1 + 2.5, z1 + 1.0)], 1.0, "dark", n=8, m=m)
    K.cylinder("y", (x0 + 1.5, z1 + 1.0), 1.4, y1 + 2.5, y1 + 3.5, "dark", n=8, m=m)
    K.tube([(x1 - 1.5, y0 + 2, z1 + 1.2), (x1 - 1.5, y1 + 3.0, z1 + 1.2)], 0.6, "steel", n=6, m=m)
    K.cylinder("y", (x1 - 1.5, z1 + 1.2), 0.65, y1 + 2.0, y1 + 3.2, "burnt_metal", n=6, m=m)


def truck(m, w=34, l=100, deck=14, mat="olive", cab_len=22, cab_h=30, axles=((12,), (-26, -12)), wheel_r=6.0, rigs=True):
    """a 6x6 truck: cab at the front (z0), frame, bed up to `deck`, fuel tank, guards, wheels, outriggers. Returns deck."""
    z0, z1 = 8 - l / 2, 8 + l / 2
    x0, x1 = 8 - w / 2, 8 + w / 2
    rails(m, w * 0.55, z0 + 4, z1, wheel_r + 0.5, wheel_r + 3.5)
    cab(m, x0, x1, z0, z0 + cab_len, wheel_r + 2.5, cab_h, mat)
    K.rbox((x0, deck - 3.0, z0 + cab_len + 1.5), (x1, deck, z1), 0.6, mat, seg=1, m=m)
    K.box((x0 + 1, deck + 0.02, z0 + cab_len + 2.5), (x1 - 1, deck + 0.06, z1 - 1), "tread_plate", m=m, faces="Y")
    # rear lamps, the bumper bar and the number plate
    for s in (-1, 1):
        lamp(m, 8 + s * (w / 2 - 3), deck - 2.0, z1, 2.0, 1.0, "red_light", "Z")
    K.box((x0 + 2, wheel_r - 1, z1 - 1), (x1 - 2, wheel_r + 0.5, z1 + 0.5), "dark", m=m)
    zs = [z0 + a for a in axles[0]] + [z1 + a for a in axles[1]]
    for zz in zs:
        K.cylinder("x", (wheel_r, zz), 0.9, x0 + 3, x1 - 3, "dark", n=6, m=m)
        for s in (-1, 1):
            tyre(m, 8 + s * (w / 2 - 2.2), wheel_r, zz, wheel_r, 4.2, s, mat)
            gx = 8 + s * (w / 2 - 2.2)
            mudguard(m, gx - 2.6, gx + 2.6, wheel_r, zz, wheel_r, mat)
    # fuel tank, battery box and tool box between the axles
    zt = (zs[0] + zs[1]) / 2
    K.cylinder("z", (x0 + 3.5, wheel_r + 2.0), 2.2, zt - 6, zt + 6, "dark", n=12, m=m)
    K.cylinder("y", (x0 + 3.5, zt + 3), 0.6, wheel_r + 4.0, wheel_r + 4.6, "steel", n=6, m=m)
    cabinet(m, (x1 - 6, wheel_r - 1.5, zt - 5), (x1 - 0.5, wheel_r + 3.5, zt + 5), mat, "X", vents=False)
    if rigs:
        for zz in (z0 + cab_len + 3, z1 - 3):
            for s in (-1, 1):
                outrigger(m, 8 + s * (w * 0.28), 8 + s * (w / 2 + 6), wheel_r + 2.0, zz, 0.0, mat)
    return deck


# ------------------------------------------------------------------------------------------------ trailer
def trailer(m, w=36, l=60, h=10, y0=8, wheels_z=(10, 22), mat="olive", wheel_r=4.6, rigs=True, tools=True):
    """a two-axle flatbed trailer: frame, deck with tread plate, guards, wheels, A-frame drawbar with jockey wheel,
    lamps and four outrigger jacks let down. Returns the deck height."""
    x0, x1 = 8 - w / 2, 8 + w / 2
    z0, z1 = 8 - l / 2, 8 + l / 2
    top = y0 + h
    rails(m, w * 0.5, z0 + 1, z1 - 1, y0 - 1.0, y0 + h - 2.5)
    K.rbox((x0, top - 2.5, z0), (x1, top, z1), 0.5, mat, seg=1, m=m)
    K.box((x0 + 1, top + 0.02, z0 + 1), (x1 - 1, top + 0.06, z1 - 1), "tread_plate", m=m, faces="Y")
    # a lip round the deck edge
    for x in (x0, x1 - 0.8):
        K.box((x, top, z0), (x + 0.8, top + 0.8, z1), mat, m=m)
    for zz in wheels_z:
        K.cylinder("x", (wheel_r, 8 + zz), 0.8, x0 + 4, x1 - 4, "dark", n=6, m=m)
        for s in (-1, 1):
            tx = 8 + s * (w / 2 - 2.0)
            tyre(m, tx, wheel_r, 8 + zz, wheel_r, 3.6, s, mat)
            K.box((tx - s * 3.2 - 1.0, wheel_r + 0.8, 8 + zz - 3.5), (tx - s * 3.2 + 1.0, y0 - 1.0, 8 + zz + 3.5), "dark", m=m)
    for s in (-1, 1):
        zm = 8 + (wheels_z[0] + wheels_z[-1]) / 2
        span = (wheels_z[-1] - wheels_z[0]) / 2 + wheel_r + 1.5
        K.box((8 + s * (w / 2 - 4.4), top - 2.7, zm - span), (8 + s * (w / 2 + 0.3), top - 2.4, zm + span), mat, m=m)
    # A-frame drawbar, the towing eye, the jockey wheel
    for s in (-1, 1):
        K.tube([(8 + s * w * 0.25, y0, z0 + 1), (8, 6.0, z0 - 13)], 0.7, "steel", n=6, m=m)
    K.torus("y", (8, z0 - 14.5), 6.0, 1.3, 0.4, "steel", n=10, k=4, m=m)
    K.cylinder("y", (8, z0 - 7), 0.7, 2.0, y0, "steel", n=6, m=m)
    K.cylinder("x", (1.5, z0 - 7), 1.5, 7.2, 8.8, "rubber", n=10, m=m)
    K.cylinder("y", (8, z0 - 7), 0.35, y0, y0 + 2.0, "dark", n=4, m=m)
    # lamps and reflectors front and back
    for s in (-1, 1):
        lamp(m, 8 + s * (w / 2 - 2.5), top - 1.3, z1, 2.0, 1.0, "red_light", "Z")
        lamp(m, 8 + s * (w / 2 - 2.5), top - 1.3, z0, 1.2, 0.8, "white", "z")
    K.box((8 - 3, top - 2.3, z1 + 0.05), (8 + 3, top - 0.6, z1 + 0.15), "white", m=m)
    if rigs:
        for zz in (z0 + 3, z1 - 3):
            for s in (-1, 1):
                outrigger(m, 8 + s * w * 0.25, 8 + s * (w / 2 + 5), top - 3.5, zz, 0.0, mat)
    if tools:
        zt = 8 + wheels_z[0] - wheel_r - 6
        cabinet(m, (x1 - 5.5, y0 - 4.5, zt - 4), (x1 - 0.5, y0 - 0.5, zt + 3), mat, "X", vents=False)
    return top


# ------------------------------------------------------------------------------------------------ tracks
def tracked(m, w=40, l=90, h=20, mat="olive", wheels=6, skirts=False, drive_front=True):
    """tracked hull: link-textured belts over road wheels, sprocket, idler and return rollers, the hull with a sloped
    glacis, fenders with stowage boxes, engine deck grilles, lamps and tow hooks. Returns the hull top."""
    z0, z1 = 8 - l / 2, 8 + l / 2
    tw = 10.0
    for s in (-1, 1):
        x = 8 + s * (w / 2 - tw / 2)
        xa, xb = x - tw / 2, x + tw / 2
        # the belt: a stadium shape in (z, y) run out along x, ground to the top run at 11
        rz, ry, cy = 4.6, 5.5, 5.5
        prof = []
        for k in range(8):
            a = math.pi / 2 + math.pi * k / 7
            prof.append((z0 + 2 + rz + math.cos(a) * rz, cy + math.sin(a) * ry))
        for k in range(8):
            a = -math.pi / 2 + math.pi * k / 7
            prof.append((z1 - 2 - rz + math.cos(a) * rz, cy + math.sin(a) * ry))
        K.prism(prof, "x", xa, xb, "links", m=m)
        # road wheels (rubber rims, steel hubs), the sprocket and the idler
        for k in range(wheels):
            zz = z0 + 11 + k * (l - 22) / (wheels - 1)
            K.cylinder("x", (4.6, zz), 3.9, xa - 0.6, xb + 0.6, "rubber", n=14, m=m)
            K.cylinder("x", (4.6, zz), 2.8, xa - 0.8, xb + 0.8, mat, n=12, m=m)
            K.cylinder("x", (4.6, zz), 0.9, xa - 1.2, xb + 1.2, "steel", n=6, m=m)
        zs, zi = (z0 + 5.5, z1 - 5.5) if drive_front else (z1 - 5.5, z0 + 5.5)
        K.cylinder("x", (6.0, zs), 4.3, xa - 0.7, xb + 0.7, "dark", n=16, m=m)
        for k in range(12):
            a = 2 * math.pi * k / 12
            K.box((xa - 0.5, 6.0 + math.sin(a) * 4.4 - 0.5, zs + math.cos(a) * 4.4 - 0.5),
                  (xb + 0.5, 6.0 + math.sin(a) * 4.4 + 0.5, zs + math.cos(a) * 4.4 + 0.5), "dark", m=m)
        K.cylinder("x", (6.0, zs), 1.2, xa - 1.2, xb + 1.2, "steel", n=8, m=m)
        K.cylinder("x", (5.6, zi), 3.8, xa - 0.6, xb + 0.6, mat, n=14, m=m)
        K.cylinder("x", (5.6, zi), 1.0, xa - 1.0, xb + 1.0, "steel", n=6, m=m)
        for k in range(3):
            zz = z0 + 20 + k * (l - 40) / 2
            K.cylinder("x", (10.2, zz), 1.3, xa + 1, xb - 1, "rubber", n=8, m=m)
        if skirts:
            sx = xb + 0.9 if s > 0 else xa - 0.9
            K.box((sx - 0.3, 5.5, z0 + 6), (sx + 0.3, 12.0, z1 - 4), mat, m=m)
    # hull: the lower hull between the tracks, the upper hull over them, the glacis
    K.rbox((8 - w / 2 + tw + 0.2, 3.0, z0 + 4), (8 + w / 2 - tw - 0.2, 12, z1 - 3), 1.0, mat, seg=1, m=m)
    K.rbox((8 - w / 2 - 0.5, 11.5, z0 + 10), (8 + w / 2 + 0.5, h, z1), 1.5, mat, seg=1, m=m)
    K.prism([(z0 + 1.0, 11.5), (z0 + 10.5, 11.5), (z0 + 10.5, h), (z0 + 9.0, h)], "x", 8 - w / 2 - 0.5, 8 + w / 2 + 0.5, mat, m=m)
    # lamps with brush guards, tow hooks, the driver's hatch and vision blocks
    for s in (-1, 1):
        lx = 8 + s * (w / 2 - 3)
        K.cylinder("z", (lx, 13.2), 0.9, z0 + 2.2, z0 + 3.4, "dark", n=8, m=m)
        K.cylinder("z", (lx, 13.2), 0.7, z0 + 2.0, z0 + 2.2, "white", n=8, m=m)
        K.tube([(lx - 1.4, 12.0, z0 + 3.4), (lx - 1.4, 14.6, z0 + 1.8), (lx + 1.4, 14.6, z0 + 1.8), (lx + 1.4, 12.0, z0 + 3.4)], 0.15, "steel", n=4, m=m)
        K.tube([(8 + s * 8, 9.5, z0 + 1.4), (8 + s * 8, 8.4, z0 - 0.2), (8 + s * 8, 7.4, z0 + 1.4)], 0.45, "dark", n=5, m=m)
        # stowage boxes on the fenders, the tow cable along the side
        K.rbox((8 + s * (w / 2 - 4) - 3, h, z1 - 26), (8 + s * (w / 2 - 4) + 3, h + 3.0, z1 - 14), 0.4, mat, seg=1, m=m)
        cx = 8 + s * (w / 2 + 0.7)
        K.tube([(cx, h - 2.5, z0 + 14), (cx, h - 2.5, z1 - 6)], 0.4, "steel", n=4, m=m)
    K.cylinder("y", (8 - w * 0.22, z0 + 13), 2.0, h - 0.5, h + 0.4, mat, n=10, m=m)
    for k in range(3):
        K.box((8 - w * 0.22 - 2.4 + k * 1.6, h - 1.0, z0 + 10.2), (8 - w * 0.22 - 1.4 + k * 1.6, h - 0.2, z0 + 10.6), "glass_dark", m=m)
    # engine deck: grilles and the exhausts at the back
    K.box((8 - w * 0.3, h + 0.02, z1 - 13), (8 + w * 0.3, h + 0.08, z1 - 3), "grille", m=m, faces="Y")
    for s in (-1, 1):
        K.box((8 + s * w * 0.32 - 1.5, h - 5, z1 + 0.05), (8 + s * w * 0.32 + 1.5, h - 2, z1 + 0.2), "grille", m=m, faces="Z")
        lamp(m, 8 + s * (w / 2 - 2), h - 2.0, z1, 1.4, 0.9, "red_light", "Z")
    return h


def turntable(m, y, r=9, mat="steel"):
    """a slewing ring with its bolt circle"""
    K.cylinder("y", (8, 8), r, y, y + 3, mat, n=24, bevel=0.5, m=m)
    K.cylinder("y", (8, 8), r + 0.6, y + 0.6, y + 1.6, "dark", n=24, caps=(False, False), m=m)
    for k in range(12):
        a = 2 * math.pi * k / 12
        K.cylinder("y", (8 + math.cos(a) * (r - 1.2), 8 + math.sin(a) * (r - 1.2)), 0.3, y + 3, y + 3.3, "dark", n=4, m=m)


def ram(m, a, b, r=0.9):
    """a hydraulic ram between two points: the cylinder and the polished rod"""
    a, b = [float(x) for x in a], [float(x) for x in b]
    mid = [a[i] + (b[i] - a[i]) * 0.55 for i in range(3)]
    K.tube([a, mid], r, "dark", n=8, m=m)
    K.tube([mid, b], r * 0.5, "bright", n=6, m=m)
