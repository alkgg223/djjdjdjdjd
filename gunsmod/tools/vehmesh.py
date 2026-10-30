"""Vehicle parts for the smooth meshes: guns, hatches, sights, smoke dischargers, aerials, team markings, running gear.
All in model units (16 = one model block), front of the vehicle at -Z, centre line x = 8."""
import math

import numpy as np

import meshkit as K
from meshkit import Mesh, v3


def gun_barrel(m, gx, gy, z_front, z_back, r, mat="paint", sleeve=None, evac=None, brake=None, mrs=False):
    """a tank gun: tapered tube from the breech end to the muzzle, thermal sleeve bands, bore evacuator, muzzle brake"""
    K.cylinder("z", (gx, gy), r * 1.12, z_back, z_back - 1.2, mat, n=12, bevel=0.08, caps=(False, True), m=m)
    K.cylinder("z", (gx, gy), r, z_back - 1.2, z_front + 0.4, mat, n=12, r1=r * 0.86, caps=(False, False), m=m)
    K.cylinder("z", (gx, gy), r * 0.86, z_front + 0.4, z_front, "gunmetal", n=12, bevel=0.05, caps=(False, False), m=m)
    K.cylinder("z", (gx, gy), r * 0.81, z_front - 0.05, z_front - 0.07, "black", n=12, caps=(False, True), m=m)
    for z in sleeve or ():
        t = (z_back - 1.2 - z) / max(1e-6, (z_back - 1.2 - z_front))
        rr = r + (r * 0.86 - r) * t
        K.cylinder("z", (gx, gy), rr * 1.07, z + 0.18, z - 0.18, "paint_dark", n=12, bevel=0.04, m=m)
    if evac:
        K.cylinder("z", (gx, gy), r * 1.45, evac[0], evac[1], mat, n=12, bevel=0.2, m=m)
    if brake:
        K.rbox((gx - r * 1.5, gy - r * 1.1, z_front + brake), (gx + r * 1.5, gy + r * 1.1, z_front), r * 0.4, "gunmetal", seg=1, m=m)
    if mrs:
        K.rbox((gx - 0.2, gy + r * 0.86, z_front + 0.9), (gx + 0.2, gy + r * 0.86 + 0.35, z_front + 0.3), 0.08, "dark", seg=1, m=m)
    return m


def hatch(m, x, y, z, r, mat="paint", h=0.3):
    K.cylinder("y", (x, z), r, y, y + h, mat, n=12, bevel=0.08, caps=(False, True), m=m)
    K.cylinder("y", (x, z), r * 0.75, y + h, y + h + 0.12, mat, n=12, bevel=0.05, caps=(False, True), m=m)
    K.cylinder("x", (y + h + 0.2, z + r * 0.55), 0.07, x - r * 0.4, x + r * 0.4, "steel", n=6, m=m)


def periscope(m, x, y, z, w=0.7, d=0.5, h=0.35, face=-1):
    K.rbox((x - w / 2, y, z - d / 2), (x + w / 2, y + h, z + d / 2), 0.08, "dark", seg=1, m=m)
    zf = z - d / 2 - 0.01 if face < 0 else z + d / 2 + 0.01
    K.box((x - w * 0.38, y + h * 0.3, zf), (x + w * 0.38, y + h * 0.8, zf + (0.02 if face > 0 else -0.02)), "lens", m=m)


def smoke_launchers(m, x, y, z, n=4, side=-1, mat="paint_dark", tilt=-22.5):
    """a bank of discharger tubes pointing forwards and outwards"""
    sub = Mesh()
    for k in range(n):
        yy = y + (k % 2) * 0.5
        zz = z - (k // 2) * 0.55
        K.cylinder("z", (x, yy), 0.22, zz + 0.6, zz - 0.5, mat, n=8, m=sub, end_mat=None)
        K.cylinder("z", (x, yy), 0.15, zz - 0.5, zz - 0.52, "black", n=8, caps=(False, True), m=sub)
    sub.rotate("y", tilt * side, (x, y, z))
    sub.rotate("x", 12, (x, y, z))
    m.extend(sub)


def antenna(m, x, y, z, h, base_r=0.18):
    K.cylinder("y", (x, z), base_r, y, y + 0.4, "dark", n=8, bevel=0.04, m=m)
    K.tube([(x, y + 0.4, z), (x, y + h * 0.5, z + 0.05), (x, y + h, z + 0.25)], 0.05, "black", n=4, m=m, radii=[0.06, 0.045, 0.03])


def machine_gun(m, x, y, z, length=2.4, mat="gunmetal"):
    """light machine gun on a pintle, pointing forward (-Z) from (x, y, z)"""
    K.rbox((x - 0.18, y - 0.2, z - 0.6), (x + 0.18, y + 0.2, z + 0.5), 0.06, mat, seg=1, m=m)
    K.cylinder("z", (x, y + 0.05), 0.07, z - 0.6, z - length, "black", n=8, m=m)
    K.rbox((x - 0.08, y - 0.5, z - 0.2), (x + 0.08, y - 0.2, z + 0.1), 0.03, mat, seg=1, m=m)
    K.cylinder("y", (x, z + 0.1), 0.08, y - 1.2, y - 0.2, "steel", n=6, m=m)


def light(m, x, y, z, w=0.6, h=0.4, face=-1, mat="glow"):
    """a headlight housing with a lit lens on the front (-Z) or back (+Z)"""
    d = 0.35
    z0, z1 = (z, z + d) if face < 0 else (z - d, z)
    K.rbox((x - w / 2, y - h / 2, z0), (x + w / 2, y + h / 2, z1), 0.08, "dark", seg=1, m=m)
    zf = z0 - 0.01 if face < 0 else z1 + 0.01
    K.box((x - w * 0.36, y - h * 0.3, zf), (x + w * 0.36, y + h * 0.3, zf + (-0.02 if face < 0 else 0.02)), mat, m=m)


def star(m, c, normal, up, size, mat, outline=None):
    """a five-pointed star decal lying on a surface (centre, outward normal, up direction)"""
    _flat(m, c, normal, up, [(math.cos(math.radians(90 + 36 * k)) * size * (1.0 if k % 2 == 0 else 0.4),
                               math.sin(math.radians(90 + 36 * k)) * size * (1.0 if k % 2 == 0 else 0.4)) for k in range(10)], mat, 0.03)
    if outline:
        _flat(m, c, normal, up, K.circle(size * 1.25, 20), outline, 0.02)


def chevron(m, c, normal, up, size, mat):
    """an inverted V (the green team's mark)"""
    s = size
    pts = [(-s, -s * 0.7), (0, s * 0.7), (s, -s * 0.7), (s * 0.62, -s * 0.7), (0, s * 0.05), (-s * 0.62, -s * 0.7)]
    _flat_tris(m, c, normal, up, pts, [(0, 1, 5), (5, 1, 4), (4, 1, 2), (4, 2, 3)], mat, 0.03)


def triangle_mark(m, c, normal, up, size, mat, border=None):
    """a filled triangle pointing up (the red team's mark), with an optional border"""
    if border:
        _flat(m, c, normal, up, [(0, size * 1.25), (-size * 1.15, -size * 0.72), (size * 1.15, -size * 0.72)], border, 0.02)
    _flat(m, c, normal, up, [(0, size), (-size * 0.87, -size * 0.5), (size * 0.87, -size * 0.5)], mat, 0.035)


def band(m, c, normal, up, w, h, mat):
    _flat(m, c, normal, up, [(-w / 2, -h / 2), (w / 2, -h / 2), (w / 2, h / 2), (-w / 2, h / 2)], mat, 0.03)


def _frame(c, normal, up):
    n = K.norm(np.asarray(normal, dtype=float))
    u = np.asarray(up, dtype=float)
    u = K.norm(u - n * np.dot(u, n))
    r = np.cross(u, n)
    return np.asarray(c, dtype=float), n, u, r


def _flat(m, c, normal, up, pts2, mat, lift):
    c, n, u, r = _frame(c, normal, up)
    ring = [c + n * lift + r * x + u * y for x, y in pts2]
    centre = c + n * lift
    for i in range(len(ring)):
        m.add_oriented([centre, ring[i], ring[(i + 1) % len(ring)]], mat, n, [n] * 3, smooth=False)


def _flat_tris(m, c, normal, up, pts2, tris, mat, lift):
    c, n, u, r = _frame(c, normal, up)
    P = [c + n * lift + r * x + u * y for x, y in pts2]
    for a, b, d in tris:
        m.add_oriented([P[a], P[b], P[d]], mat, n, [n] * 3, smooth=False)


def team_marks(points, green=True):
    """the team's markings at the given (centre, normal, up, size) spots"""
    m = Mesh()
    for c, n, up, s in points:
        if green:
            chevron(m, c, n, up, s, "white_mark")
        else:
            triangle_mark(m, c, n, up, s * 0.9, "red_mark", border="white_mark")
    return m


# ------------------------------------------------------------------------------------------------ running gear
LINK = 0.75


def roadwheel(r=1.3):
    """dual road wheel centred on (8, 8, 8), axle along X: rubber tyres, dished paint discs, hub"""
    m = Mesh()
    K.cylinder("x", (8, 8), r, 7.0, 9.0, "rubber", n=12, bevel=0.12, caps=(False, False), m=m)
    for xo, side in ((7.0, -1), (9.0, 1)):
        K.cylinder("x", (8, 8), r * 0.8, xo - side * 0.1, xo + side * 0.05, "paint", n=12, bevel=0.05, caps=(False, True), m=m)
        K.cylinder("x", (8, 8), r * 0.36, xo + side * 0.05, xo + side * 0.17, "paint_dark", n=8, bevel=0.04, caps=(False, True), m=m)
    return m


def idler(r=1.15):
    m = Mesh()
    K.cylinder("x", (8, 8), r, 7.1, 8.9, "steel", n=14, bevel=0.12, caps=(False, False), m=m)
    for xo, side in ((7.1, -1), (8.9, 1)):
        K.cylinder("x", (8, 8), r * 0.82, xo - side * 0.1, xo + side * 0.04, "steel", n=14, caps=(False, True), m=m)
        K.cylinder("x", (8, 8), r * 0.55, xo + side * 0.04, xo + side * 0.16, "paint_dark", n=10, bevel=0.04, caps=(False, True), m=m)
    return m


def sprocket(r=1.3, teeth=10):
    m = Mesh()
    for x0, x1 in ((7.0, 7.55), (8.45, 9.0)):
        K.cylinder("x", (8, 8), r * 0.84, x0, x1, "gunmetal", n=teeth * 2, m=m)
        for k in range(teeth):
            a = math.radians(k * 360 / teeth)
            sub = K.box((x0 + 0.04, 8 + r * 0.8, 8 - 0.15), (x1 - 0.04, 8 + r * 1.0, 8 + 0.15), "gunmetal", faces="xXYzZ")
            sub.rotate("x", math.degrees(a), (8, 8, 8))
            m.extend(sub)
    K.cylinder("x", (8, 8), r * 0.5, 6.9, 9.1, "paint_dark", n=10, bevel=0.06, m=m)
    return m


def track_run(upper=False, links=29):
    """a straight run of track links from z = -8, the guide horns towards the wheels"""
    m = Mesh()
    for k in range(links):
        z0 = -8 + k * LINK
        K.box((6.7, 0.0, z0), (9.3, 0.36, z0 + 0.6), "track", m=m)
        # the guide horn towards the wheels
        if upper:
            K.box((7.86, -0.32, z0 + 0.16), (8.14, 0.0, z0 + 0.44), "steel", m=m, faces="xXyzZ")
        else:
            K.box((7.86, 0.36, z0 + 0.16), (8.14, 0.7, z0 + 0.44), "steel", m=m, faces="xXYzZ")
    return m


def track_ends(m, tx, g, width=2.6):
    """the curved bits of the track round the idler and the sprocket (static, part of the hull)"""
    for (y, z, r), sgn in ((g["idler"], -1), (g["sprocket"], 1)):
        rr = r + 0.18
        # a half ring of links on the outer side of the wheel
        pts = []
        for k in range(9):
            a = math.radians(90 + 180 * k / 8) if sgn < 0 else math.radians(-90 + 180 * k / 8)
            pts.append((z + math.cos(a) * rr, y + math.sin(a) * rr))
        for k in range(8):
            (z0, y0), (z1, y1) = pts[k], pts[k + 1]
            c = ((z0 + z1) / 2, (y0 + y1) / 2)
            ang = math.degrees(math.atan2(y1 - y0, z1 - z0))
            seg = K.box((tx - width / 2, -0.18, -0.36), (tx + width / 2, 0.18, 0.36), "track")
            seg.rotate("x", -ang, (0, 0, 0))
            seg.translate((0, c[1], c[0]))
            m.extend(seg)
