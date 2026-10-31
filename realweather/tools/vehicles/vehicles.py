"""Original vehicle models for Real Weather: a family sedan, a storm-chasing pickup with instruments, and an armoured
tornado intercept vehicle with hydraulic ground spikes.

Built in metres (1 block = 1 m), front toward -Z, +X = right side, ground at y = 0, centred on x = z = 0; parts are
scaled to model units (1/16 block) when written. Moving parts (wheels, steering wheel, pedals, gear lever, wipers,
spikes, skirts, anemometer, turret, dish) are separate models built around their own pivot. Their positions in the
vehicle go to VehicleGeometry.java, so the renderer and the physics use exactly the same points.
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import meshkit as K  # noqa: E402
from meshkit import Mesh, v3  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "realweather")
JAVA = os.path.join(HERE, "..", "..", "src", "main", "java", "com", "realweather", "vehicle", "VehicleGeometry.java")
SCALE = 16.0


# ------------------------------------------------------------------------------------------------ helpers
def interp(pts, z):
    if z <= pts[0][0]:
        return pts[0][1]
    for (z0, y0), (z1, y1) in zip(pts, pts[1:]):
        if z <= z1:
            t = (z - z0) / max(1e-9, z1 - z0)
            t = t * t * (3 - 2 * t) if len(pts) > 2 else t
            return y0 + (y1 - y0) * t
    return pts[-1][1]


def frange(a, b, step):
    n = max(1, int(math.ceil(abs(b - a) / step)))
    return [a + (b - a) * i / n for i in range(n + 1)]


class Body:
    """Shared measurements of one vehicle."""

    def __init__(self, **kw):
        self.__dict__.update(kw)

    def bottom(self, z):
        y = self.sill
        half = self.length / 2
        if z < -half + self.over_f:
            t = (z - (-half)) / self.over_f
            y = self.bumper_low + (self.sill - self.bumper_low) * t
        elif z > half - self.over_r:
            t = (half - z) / self.over_r
            y = self.bumper_low + (self.sill - self.bumper_low) * t
        for zw in (self.wz_f, self.wz_r):
            d = z - zw
            ra = self.wheel_r + 0.07
            if abs(d) < ra:
                y = max(y, self.wheel_r + math.sqrt(ra * ra - d * d))
        return y

    def profile(self, z0, z1, top, step=0.035):
        """side outline (z, y) of the body between z0 and z1, under the given top line, with the wheel arches cut in"""
        zs = frange(z0, z1, step)
        up = [(z, interp(top, z)) for z in zs]
        low = [(z, self.bottom(z)) for z in reversed(zs)]
        return simplify(up + low)

    def taper(self, mesh, amount=0.07, start=0.75):
        """round the plan view: the body narrows toward the bumpers"""
        half = self.length / 2

        def f(q):
            k = (abs(q[2]) - (half - start)) / start
            k = min(1.0, max(0.0, k))
            s = 1 - amount * k * k
            return np.array([q[0] * s, q[1], q[2]])
        return mesh.apply(f)


def simplify(poly, eps=1e-4):
    """drop repeated and collinear corners (ear clipping needs a clean outline)"""
    pts = []
    for p in poly:
        if not pts or abs(p[0] - pts[-1][0]) + abs(p[1] - pts[-1][1]) > 1e-6:
            pts.append(p)
    changed = True
    while changed and len(pts) > 3:
        changed = False
        out = []
        n = len(pts)
        for i in range(n):
            a, b, c = pts[i - 1], pts[i], pts[(i + 1) % n]
            cr = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0])
            if abs(cr) < eps * max(1e-6, math.hypot(c[0] - a[0], c[1] - a[1])):
                changed = True
                continue
            out.append(b)
        pts = out
    return pts


def mirror(m):
    return m.mirrored_x(0.0)


def both(m):
    out = m.copy()
    out.extend(mirror(m))
    return out


def side_plate(body, z0, z1, top, x_out, thick, mat):
    """a body side (door skin) between z0 and z1 at x_out (right side), with the arches"""
    poly = body.profile(z0, z1, top)
    return K.prism(poly, "x", x_out - thick, x_out, mat, bevel=min(0.012, thick / 3))


def lamp(m, c, w, h, d, lens_mat, glow_mat, face):
    """a lamp: housing, glowing insert, clear lens (face = -1 front, +1 rear)"""
    x, y, z = c
    K.rbox((x - w / 2, y - h / 2, z - d / 2), (x + w / 2, y + h / 2, z + d / 2), 0.02, "trim", m=m)
    zi = z + face * (d / 2 + 0.002)
    K.rbox((x - w / 2 + 0.012, y - h / 2 + 0.012, zi - 0.006), (x + w / 2 - 0.012, y + h / 2 - 0.012, zi + 0.006), 0.004, glow_mat, m=m)
    return m


def disc_face(c, r, normal_axis, sign, mat, n=24):
    """a flat round face (dial) with the texture mapped over the whole disc"""
    m = Mesh()
    pts = []
    uv = []
    for k in range(n):
        a = 2 * math.pi * k / n
        u, v = math.cos(a), math.sin(a)
        if normal_axis == "z":
            pts.append(v3(c[0] + u * r * sign, c[1] + v * r, c[2]))
        else:
            pts.append(v3(c[0], c[1] + v * r, c[2] - u * r * sign))
        uv.append((0.5 + 0.5 * u, 0.5 - 0.5 * v))
    m.add(pts, mat, [v3(0, 0, sign) if normal_axis == "z" else v3(sign, 0, 0)] * n, uv=uv)
    return m


def quad_uv(pts, mat):
    m = Mesh()
    m.add([v3(*p) for p in pts], mat, uv=[(0, 1), (1, 1), (1, 0), (0, 0)])
    return m


# ------------------------------------------------------------------------------------------------ shared parts
def wheel(r, width, style):
    """tyre with a tread band and sidewalls, a rim with spokes and a hub; axle along X, outer face toward +X"""
    m = Mesh()
    rw = width / 2
    # Tyre: a lathe with rounded shoulders.
    prof = [(-rw * 0.8, r * 0.7), (-rw, r * 0.78), (-rw, r * 0.93), (-rw * 0.85, r), (rw * 0.85, r), (rw, r * 0.93), (rw, r * 0.78),
            (rw * 0.8, r * 0.7)]
    n = 28
    rings = [[v3(x, rad * math.sin(2 * math.pi * k / n), rad * math.cos(2 * math.pi * k / n)) for k in range(n)] for x, rad in prof]
    K.loft(rings, "rubber", caps=(False, False), m=m)
    # Tread blocks.
    for k in range(n):
        if k % 2 == 0:
            a0 = 2 * math.pi * (k + 0.1) / n
            a1 = 2 * math.pi * (k + 0.9) / n
            ring0 = [v3(x, (r + 0.008) * math.sin(a), (r + 0.008) * math.cos(a)) for x in (-rw * 0.8, rw * 0.8) for a in (a0, a1)]
            m.add_oriented([ring0[0], ring0[1], ring0[3], ring0[2]], "rubber", (ring0[0] + ring0[3]) / 2, smooth=False)
    # Rim.
    rim_r = r * 0.68
    K.cylinder("x", (0, 0), rim_r, -rw * 0.75, rw * 0.72, "rim", n=24, caps=(True, False), m=m)
    face = rw * 0.72
    K.cylinder("x", (0, 0), rim_r * 0.9, face - 0.02, face + 0.005, "dark", n=24, caps=(False, True), m=m)
    spokes = 5 if style != "steel" else 8
    for s in range(spokes):
        a = 2 * math.pi * s / spokes
        p0 = v3(face + 0.008, 0.06 * math.sin(a), 0.06 * math.cos(a))
        p1 = v3(face - 0.004, rim_r * 0.88 * math.sin(a), rim_r * 0.88 * math.cos(a))
        K.tube([p0, p1], 0.024 if style != "steel" else 0.012, "rim" if style != "offroad" else "dark", n=6, m=m)
    K.cylinder("x", (0, 0), 0.07, face, face + 0.025, "chrome" if style == "alloy" else "dark", n=16, m=m)
    for s in range(5):
        a = 2 * math.pi * s / 5 + 0.3
        K.cylinder("x", (0.045 * math.sin(a), 0.045 * math.cos(a)), 0.009, face + 0.02, face + 0.035, "steel", n=6, m=m)
    return m


def steering_wheel(r=0.19):
    """in the XY plane, axis along +Z toward the driver"""
    m = Mesh()
    K.torus("z", (0, 0), 0.0, r, 0.02, "dark", n=28, k=8, m=m)
    for a in (-90, 30, 150):
        ar = math.radians(a)
        K.tube([v3(0, 0, -0.01), v3(math.cos(ar) * (r - 0.01), math.sin(ar) * (r - 0.01), 0)], 0.014, "dark", n=6, m=m)
    K.cylinder("z", (0, 0), 0.06, -0.04, 0.02, "dash", n=16, m=m)
    K.cylinder("z", (0, 0), 0.03, 0.02, 0.025, "chrome", n=12, m=m)
    return m


def pedal(wide):
    """pivot at the top of the arm (origin); the pad hangs below toward the driver (+Z)"""
    m = Mesh()
    K.tube([v3(0, 0, 0), v3(0, -0.16, 0.04), v3(0, -0.22, 0.06)], 0.008, "steel", n=6, m=m)
    w = 0.09 if wide else 0.05
    K.rbox((-w / 2, -0.28, 0.045), (w / 2, -0.19, 0.065), 0.006, "rubber", m=m)
    return m


def gear_lever():
    """pivot at the console (origin), stick up"""
    m = Mesh()
    K.cylinder("y", (0, 0), 0.03, 0.0, 0.03, "trim", n=12, m=m)
    K.tube([v3(0, 0, 0), v3(0, 0.12, 0.01)], 0.008, "chrome", n=8, m=m)
    K.sphere((0, 0.14, 0.012), 0.028, 0.035, 0.028, "dark", n=12, rows=6, m=m)
    return m


def wiper(length):
    """pivot at the origin, the arm along +X in the XY plane (Z = out of the glass)"""
    m = Mesh()
    K.cylinder("z", (0, 0), 0.018, -0.01, 0.02, "trim", n=10, m=m)
    K.box((0, -0.007, 0.006), (length * 0.85, 0.007, 0.016), "trim", m=m)
    K.box((length * 0.2, -0.004, 0.0), (length, 0.004, 0.01), "rubber", m=m)
    return m


def anemometer():
    """three cups on arms turning about +Y, origin at the hub"""
    m = Mesh()
    K.cylinder("y", (0, 0), 0.02, -0.02, 0.04, "steel", n=10, m=m)
    for k in range(3):
        a = 2 * math.pi * k / 3
        end = v3(math.cos(a) * 0.16, 0.02, math.sin(a) * 0.16)
        K.tube([v3(0, 0.02, 0), end], 0.006, "steel", n=6, m=m)
        # Cup facing sideways.
        tang = v3(-math.sin(a), 0, math.cos(a))
        K.sphere(tuple(end + tang * 0.02), 0.04, 0.04, 0.04, "white", n=10, rows=6, m=m)
    return m


def spike():
    """hydraulic leg: a ram that slides down out of its cylinder, origin at the top"""
    m = Mesh()
    K.cylinder("y", (0, 0), 0.05, -0.55, 0.0, "hydraulic", n=12, m=m)
    K.cylinder("y", (0, 0), 0.075, -0.6, -0.55, "armor_dark", n=12, m=m)
    K.cylinder("y", (0, 0), 0.06, -0.78, -0.6, "steel", n=12, m=m, r1=0.0)
    return m


def dish(r=0.42):
    """small Doppler dish turning about +Y; the bowl faces +Z"""
    m = Mesh()
    rings = []
    for i in range(7):
        rr = r * i / 6
        z = rr * rr / (4 * 0.35)
        rings.append([v3(rr * math.cos(2 * math.pi * k / 20), 0.42 + rr * math.sin(2 * math.pi * k / 20), z) for k in range(20)])
    for i in range(6):
        a, b = rings[i], rings[i + 1]
        for k in range(20):
            k1 = (k + 1) % 20
            quad = [a[k], a[k1], b[k1], b[k]]
            if np.linalg.norm(quad[0] - quad[1]) < 1e-9:
                quad = [a[k], b[k1], b[k]]
            m.add_oriented(quad, "white", v3(0, 0, 1))
            m.add_oriented(list(reversed(quad)), "white", v3(0, 0, -1))
    K.cylinder("y", (0, 0), 0.06, 0.0, 0.42, "steel", n=10, m=m)
    K.tube([v3(0, 0.42 + r * 0.9, r * r * 0.9 * 0.9 / 1.4), v3(0, 0.42, 0.35)], 0.008, "steel", n=6, m=m)
    K.tube([v3(0, 0.42 - r * 0.9, r * r * 0.9 * 0.9 / 1.4), v3(0, 0.42, 0.35)], 0.008, "steel", n=6, m=m)
    K.rbox((-0.03, 0.39, 0.33), (0.03, 0.45, 0.4), 0.01, "dark", m=m)
    return m


def turret():
    """roof camera turret (turns about +Y): base ring, armoured head with a camera window"""
    m = Mesh()
    K.cylinder("y", (0, 0), 0.38, 0.0, 0.08, "armor_dark", n=24, m=m)
    head = [(-0.3, 0.08), (0.3, 0.08), (0.26, 0.42), (-0.26, 0.42)]
    K.prism(head, "z", -0.32, 0.3, "armor", bevel=0.02, m=m)
    K.rbox((-0.2, 0.16, -0.36), (0.2, 0.34, -0.3), 0.02, "glass_dark", m=m)
    K.cylinder("z", (0.0, 0.25), 0.07, -0.37, -0.33, "lens", n=16, m=m)
    K.box((-0.02, 0.42, -0.02), (0.02, 0.62, 0.02), "steel", m=m)
    return m


# ------------------------------------------------------------------------------------------------ vehicle builder
class Vehicle:
    def __init__(self, name):
        self.name = name
        self.parts = {}
        self.geo = {}

    def part(self, name, mesh):
        self.parts[name] = mesh


def cabin_glass(body, zw0, zw1, zr1, zb, belt_f, belt_r, roof, xb, xr, zB, frame_mat, m, glass):
    """pillars, roof and glass of a passenger cabin; returns the windshield corners"""
    # Roof panel, slightly crowned.
    rings = []
    for z in frange(zw1 - 0.02, zr1 + 0.02, 0.2):
        ring = []
        for k in range(9):
            t = -1 + 2 * k / 8
            ring.append(v3(t * xr, roof + 0.025 * (1 - t * t), z))
        for k in range(9):
            t = 1 - 2 * k / 8
            ring.append(v3(t * (xr - 0.02), roof - 0.035, z))
        rings.append(ring)
    K.loft(rings, frame_mat, m=m)
    for s in (-1, 1):
        # A-pillars, B-pillars, C-pillars.
        K.tube([v3(s * xb, belt_f, zw0), v3(s * xr, roof, zw1)], 0.04, frame_mat, n=8, m=m)
        K.tube([v3(s * (xb - 0.01), belt_f, zB), v3(s * (xr - 0.01), roof - 0.01, zB)], 0.045, frame_mat, n=8, m=m)
        K.plate([v3(s * xb, belt_r, zb), v3(s * xr, roof - 0.01, zr1), v3(s * xr, roof - 0.01, zr1 - 0.2),
                 v3(s * xb, belt_r, zb - 0.38)], 0.04, frame_mat, m=m)
        # Window rubber along the beltline.
        K.box((s * xb - 0.03, belt_f - 0.01, zw0), (s * xb + 0.03, belt_f + 0.015, zb - 0.38), "trim", m=m)
    ws = [(-xb + 0.04, belt_f + 0.01, zw0 + 0.02), (xb - 0.04, belt_f + 0.01, zw0 + 0.02), (xr - 0.03, roof - 0.03, zw1 - 0.005),
          (-xr + 0.03, roof - 0.03, zw1 - 0.005)]
    K.plate([v3(*p) for p in ws], 0.012, "glass", m=glass)
    rear = [(-xr + 0.03, roof - 0.03, zr1 + 0.01), (xr - 0.03, roof - 0.03, zr1 + 0.01), (xb - 0.05, belt_r + 0.02, zb - 0.02),
            (-xb + 0.05, belt_r + 0.02, zb - 0.02)]
    K.plate([v3(*p) for p in rear], 0.012, "glass", m=glass)
    for s in (-1, 1):
        def side_pt(y, z):
            t = (y - belt_f) / (roof - belt_f)
            return v3(s * (xb + (xr - xb) * t), y, z)
        fw = [side_pt(belt_f + 0.01, zw0 + 0.08), side_pt(roof - 0.04, zw1 + 0.05), side_pt(roof - 0.04, zB - 0.05), side_pt(belt_f + 0.01, zB - 0.05)]
        K.plate(fw, 0.01, "glass", m=glass)
        rw = [side_pt(belt_r + 0.01, zB + 0.05), side_pt(roof - 0.04, zB + 0.05), side_pt(roof - 0.04, zr1 - 0.22),
              side_pt(belt_r + 0.01, zb - 0.4)]
        K.plate(rw, 0.01, "glass", m=glass)
    return ws


def interior(v, m, W, zw0, floor_y, belt, seats_rear, driver_x=-0.37, screen=False, seat_mat="seat"):
    """floor, dashboard with instrument binnacle, console, seats; returns the points the renderer animates"""
    g = {}
    K.box((-W / 2 + 0.05, floor_y - 0.03, zw0 - 0.25), (W / 2 - 0.05, floor_y, zw0 + 2.3 if seats_rear else zw0 + 1.45), "carpet", m=m)
    # Dashboard: a padded top under the windshield and a lower panel.
    K.rbox((-W / 2 + 0.06, belt - 0.32, zw0 - 0.2), (W / 2 - 0.06, belt - 0.02, zw0 + 0.3), 0.05, "dash", m=m)
    K.rbox((-W / 2 + 0.08, floor_y + 0.15, zw0 - 0.1), (W / 2 - 0.08, belt - 0.3, zw0 + 0.18), 0.03, "dash", m=m)
    # Instrument binnacle in front of the driver.
    K.rbox((driver_x - 0.2, belt - 0.12, zw0 + 0.18), (driver_x + 0.2, belt + 0.06, zw0 + 0.36), 0.04, "dash", m=m)
    gz = zw0 + 0.362
    gy = belt - 0.04
    m.extend(disc_face((driver_x - 0.09, gy, gz), 0.072, "z", 1, "gauge"))
    m.extend(disc_face((driver_x + 0.09, gy, gz), 0.06, "z", 1, "gauge_rpm"))
    g["speedo"] = (driver_x - 0.09, gy, gz + 0.004, 0.072)
    g["tacho"] = (driver_x + 0.09, gy, gz + 0.004, 0.06)
    # Air vents and a radio.
    for x in (-0.55, -0.12, 0.12, 0.55):
        K.box((x - 0.06, belt - 0.12, zw0 + 0.301), (x + 0.06, belt - 0.06, zw0 + 0.305), "trim", m=m)
    K.box((-0.13, belt - 0.27, zw0 + 0.181), (0.13, belt - 0.16, zw0 + 0.186), "trim", m=m)
    # Console between the seats.
    K.rbox((-0.13, floor_y, zw0 + 0.15), (0.13, floor_y + 0.32, zw0 + 1.2), 0.03, "dash", m=m)
    g["gear"] = (0.0, floor_y + 0.32, zw0 + 0.62)
    # Centre screen (the radar display on chase vehicles).
    sc = [(-0.14, belt - 0.13, zw0 + 0.31), (0.14, belt - 0.13, zw0 + 0.31), (0.14, belt + 0.03, zw0 + 0.25), (-0.14, belt + 0.03, zw0 + 0.25)]
    if screen:
        K.rbox((-0.16, belt - 0.15, zw0 + 0.22), (0.16, belt + 0.05, zw0 + 0.3), 0.015, "trim", m=m)
        g["screen"] = [tuple(p) for p in sc]
    # Steering column and the points of the controls.
    sw = (driver_x, belt - 0.02, zw0 + 0.52)
    K.tube([v3(driver_x, belt - 0.15, zw0 + 0.15), v3(*sw) + v3(0, -0.03, -0.08)], 0.03, "dash", n=8, m=m)
    g["steer"] = sw
    g["pedals"] = [(driver_x + 0.15, floor_y + 0.42, zw0 + 0.02), (driver_x - 0.02, floor_y + 0.42, zw0 + 0.02)]
    # Seats.
    seats = [(driver_x, zw0 + 1.0), (-driver_x, zw0 + 1.0)]
    for x, z in seats:
        bucket(m, x, floor_y, z, seat_mat)
    g["seats"] = [(x, floor_y + 0.06, z) for x, z in seats]
    if seats_rear:
        zr = zw0 + 1.85
        K.rbox((-W / 2 + 0.12, floor_y + 0.1, zr - 0.25), (W / 2 - 0.12, floor_y + 0.24, zr + 0.25), 0.05, seat_mat, m=m)
        back = Mesh()
        K.rbox((-W / 2 + 0.12, floor_y + 0.24, zr + 0.2), (W / 2 - 0.12, floor_y + 0.8, zr + 0.34), 0.05, seat_mat, m=back)
        back.rotate("x", -10, origin=(0, floor_y + 0.24, zr + 0.27))
        m.extend(back)
        g["seats"] += [(-0.4, floor_y + 0.02, zr), (0.4, floor_y + 0.02, zr)]
    # Rear-view mirror.
    v.geo.update(g)
    return g


def bucket(m, x, floor_y, z, mat):
    K.rbox((x - 0.07, floor_y, z - 0.18), (x + 0.07, floor_y + 0.12, z + 0.18), 0.02, "trim", m=m)
    K.rbox((x - 0.25, floor_y + 0.12, z - 0.25), (x + 0.25, floor_y + 0.26, z + 0.25), 0.05, mat, m=m)
    back = Mesh()
    K.rbox((x - 0.25, floor_y + 0.26, z + 0.2), (x + 0.25, floor_y + 0.92, z + 0.33), 0.06, mat, m=back)
    K.rbox((x - 0.13, floor_y + 0.95, z + 0.22), (x + 0.13, floor_y + 1.13, z + 0.31), 0.04, mat, m=back)
    K.box((x - 0.06, floor_y + 0.88, z + 0.26), (x - 0.05, floor_y + 0.97, z + 0.27), "chrome", m=back)
    K.box((x + 0.05, floor_y + 0.88, z + 0.26), (x + 0.06, floor_y + 0.97, z + 0.27), "chrome", m=back)
    back.rotate("x", -12, origin=(0, floor_y + 0.26, z + 0.26))
    m.extend(back)


def front_details(m, body, z_front, y_mid, W, grille_w, grille_h, lamp_y, lamp_w, plate=True):
    zf = z_front
    K.rbox((-grille_w / 2, y_mid - grille_h / 2, zf - 0.02), (grille_w / 2, y_mid + grille_h / 2, zf + 0.04), 0.02, "trim", m=m)
    for i in range(5):
        y = y_mid - grille_h / 2 + grille_h * (i + 0.5) / 5
        K.box((-grille_w / 2 + 0.02, y - 0.006, zf - 0.03), (grille_w / 2 - 0.02, y + 0.006, zf - 0.015), "chrome", m=m)
    for s in (-1, 1):
        lamp(m, (s * (W / 2 - lamp_w / 2 - 0.06), lamp_y, zf + 0.03), lamp_w, 0.11, 0.08, "lens", "head_light", -1)
        lamp(m, (s * (W / 2 - 0.12), lamp_y - 0.2, zf + 0.04), 0.1, 0.04, 0.05, "lens", "amber", -1)
    if plate:
        K.box((-0.26, y_mid - grille_h / 2 - 0.16, zf - 0.03), (0.26, y_mid - grille_h / 2 - 0.05, zf), "plate", m=m)


def rear_details(m, z_rear, W, lamp_y, plate_y):
    for s in (-1, 1):
        lamp(m, (s * (W / 2 - 0.16), lamp_y, z_rear - 0.03), 0.24, 0.12, 0.08, "lens", "tail_light", 1)
        lamp(m, (s * (W / 2 - 0.16), lamp_y - 0.1, z_rear - 0.03), 0.12, 0.05, 0.08, "lens", "amber", 1)
    K.box((-0.26, plate_y - 0.06, z_rear), (0.26, plate_y + 0.06, z_rear + 0.03), "plate", m=m)


def mirrors(m, W, y, z):
    for s in (-1, 1):
        K.rbox((s * (W / 2 - 0.02), y - 0.03, z - 0.04), (s * (W / 2 + 0.14), y + 0.03, z + 0.02), 0.01, "paint", m=m)
        K.rbox((s * (W / 2 + 0.06), y - 0.06, z - 0.03), (s * (W / 2 + 0.2), y + 0.07, z + 0.05), 0.025, "paint", m=m)
        K.box((s * (W / 2 + 0.07) - 0.0 * s, y - 0.05, z + 0.051), (s * (W / 2 + 0.19), y + 0.06, z + 0.056), "chrome", m=m)


def door_lines(m, body, x, zs, y0, y1):
    for z in zs:
        K.box((x - 0.005, y0, z - 0.004), (x + 0.004, y1, z + 0.004), "trim", m=m)


def handles(m, x, zs, y):
    for z in zs:
        K.rbox((x - 0.004, y - 0.015, z - 0.07), (x + 0.02, y + 0.015, z + 0.07), 0.008, "chrome", m=m)


def cover(points, lift=0.02):
    """snow / hail cover on a top surface: a soft lumpy slab over the given quad"""
    m = Mesh()
    p = [np.array(q, dtype=float) for q in points]
    n = 6
    grid = []
    rng = np.random.default_rng(len(points) * 13 + int(abs(p[0][2]) * 100))
    for i in range(n + 1):
        row = []
        for j in range(n + 1):
            u, w = i / n, j / n
            q = p[0] * (1 - u) * (1 - w) + p[1] * u * (1 - w) + p[2] * u * w + p[3] * (1 - u) * w
            edge = min(u, 1 - u, w, 1 - w)
            h = lift + 0.03 * min(1.0, edge * 4) + rng.random() * 0.012 * min(1.0, edge * 4)
            row.append(q + v3(0, h, 0))
        grid.append(row)
    for i in range(n):
        for j in range(n):
            m.add_oriented([grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]], "snow", v3(0, 1, 0))
    return m


# ------------------------------------------------------------------------------------------------ sedan
def sedan():
    v = Vehicle("sedan")
    W = 1.82
    b = Body(length=4.7, width=W, sill=0.2, bumper_low=0.24, over_f=0.85, over_r=0.95, wz_f=-1.38, wz_r=1.37, wheel_r=0.33)
    half = b.length / 2
    zw0, zw1, zr1, zb = -0.62, 0.12, 1.02, 1.52
    roof = 1.45
    top = [(-half, 0.6), (-half + 0.12, 0.74), (-half + 0.45, 0.79), (zw0, 0.93), (zb, 0.96), (half - 0.25, 0.99), (half, 0.86)]
    body = Mesh()
    K.prism(b.profile(-half, zw0, top), "x", -W / 2, W / 2, "paint", bevel=0.07, m=body)
    K.prism(b.profile(zb - 0.02, half, top), "x", -W / 2, W / 2, "paint", bevel=0.07, m=body)
    for s in (-1, 1):
        sidem = side_plate(b, zw0 - 0.02, zb, top, W / 2, 0.05, "paint")
        body.extend(sidem if s > 0 else mirror(sidem))
    # Bumpers and a sill under the doors.
    K.rbox((-W / 2 + 0.03, 0.24, -half - 0.02), (W / 2 - 0.03, 0.46, -half + 0.12), 0.06, "paint_dark", m=body)
    K.rbox((-W / 2 + 0.03, 0.26, half - 0.12), (W / 2 - 0.03, 0.48, half + 0.02), 0.06, "paint_dark", m=body)
    b.taper(body, 0.08, 0.8)
    glass = Mesh()
    xb, xr = W / 2 - 0.03, W / 2 - 0.17
    ws = cabin_glass(b, zw0, zw1, zr1, zb, 0.93, 0.96, roof, xb, xr, 0.42, "paint", body, glass)
    front_details(body, b, -half, 0.5, W, 0.72, 0.16, 0.64, 0.34)
    rear_details(body, half, W, 0.82, 0.55)
    mirrors(body, W, 0.98, zw0 + 0.12)
    for s in (-1, 1):
        door_lines(body, b, s * (W / 2 + 0.001), [zw0 + 0.02, 0.42, zb - 0.05], 0.3, 0.92)
        handles(body, s * (W / 2), [0.25, 1.15], 0.86)
    interior(v, body, W, zw0, 0.22, 0.95, True)
    v.part("body", body)
    v.part("glass", glass)
    v.part("wheel", wheel(b.wheel_r, 0.22, "alloy"))
    v.part("cover", cover([(-xr, roof + 0.03, zw1 + 0.05), (xr, roof + 0.03, zw1 + 0.05), (xr, roof + 0.03, zr1 - 0.05), (-xr, roof + 0.03, zr1 - 0.05)])
           .extend(cover([(-W / 2 + 0.12, 0.83, -half + 0.4), (W / 2 - 0.12, 0.83, -half + 0.4), (W / 2 - 0.12, 0.95, zw0 - 0.05),
                          (-W / 2 + 0.12, 0.95, zw0 - 0.05)]))
           .extend(cover([(-W / 2 + 0.12, 0.97, zb + 0.08), (W / 2 - 0.12, 0.97, zb + 0.08), (W / 2 - 0.12, 0.99, half - 0.3),
                          (-W / 2 + 0.12, 0.99, half - 0.3)])))
    v.geo.update(wheels=[(-0.78, b.wheel_r, b.wz_f), (0.78, b.wheel_r, b.wz_f), (-0.78, b.wheel_r, b.wz_r), (0.78, b.wheel_r, b.wz_r)],
                 wheel_r=b.wheel_r, windshield=ws, wipers=[(-0.42, 0.95, zw0 + 0.05), (0.18, 0.95, zw0 + 0.05)], wiper_len=0.55,
                 size=(W, roof, b.length), lights_front=[(-0.62, 0.64, -half), (0.62, 0.64, -half)])
    return v


# ------------------------------------------------------------------------------------------------ pickup
def pickup():
    v = Vehicle("pickup")
    W = 2.0
    b = Body(length=5.5, width=W, sill=0.36, bumper_low=0.4, over_f=0.95, over_r=1.1, wz_f=-1.62, wz_r=1.68, wheel_r=0.42)
    half = b.length / 2
    zw0, zw1, zr1, zb = -0.78, -0.12, 0.62, 0.66
    roof = 1.9
    top = [(-half, 0.95), (-half + 0.1, 1.1), (zw0 - 0.3, 1.16), (zw0, 1.2), (zb + 0.05, 1.22), (half, 1.22)]
    body = Mesh()
    K.prism(b.profile(-half, zw0, top), "x", -W / 2, W / 2, "paint", bevel=0.06, m=body)
    for s in (-1, 1):
        sidem = side_plate(b, zw0 - 0.02, zb + 0.05, top, W / 2, 0.05, "paint")
        body.extend(sidem if s > 0 else mirror(sidem))
    # Cab back wall.
    K.box((-W / 2 + 0.03, 0.4, zb), (W / 2 - 0.03, 1.22, zb + 0.06), "paint", m=body)
    # Bed: floor, sides and tailgate.
    bed0 = zb + 0.12
    K.box((-W / 2 + 0.05, 0.62, bed0), (W / 2 - 0.05, 0.66, half - 0.05), "dark", m=body)
    for s in (-1, 1):
        sidem = side_plate(b, bed0 - 0.06, half, top, W / 2, 0.06, "paint")
        body.extend(sidem if s > 0 else mirror(sidem))
        K.rbox((s * (W / 2) - 0.07, 1.2, bed0 - 0.06), (s * (W / 2) + 0.01, 1.25, half), 0.02, "trim", m=body)
    K.box((-W / 2 + 0.03, 0.62, bed0 - 0.06), (W / 2 - 0.03, 1.22, bed0), "paint", m=body)
    K.box((-W / 2 + 0.03, 0.5, half - 0.06), (W / 2 - 0.03, 1.22, half), "paint", m=body)
    K.box((-0.2, 1.05, half), (0.2, 1.1, half + 0.01), "chrome", m=body)
    # Chunky bumpers, a bull bar and step rails.
    K.rbox((-W / 2 + 0.02, 0.38, -half - 0.06), (W / 2 - 0.02, 0.62, -half + 0.12), 0.05, "grey", m=body)
    K.rbox((-W / 2 + 0.02, 0.4, half - 0.05), (W / 2 - 0.02, 0.6, half + 0.08), 0.05, "grey", m=body)
    for x in (-0.45, 0.45):
        K.tube([v3(x, 0.5, -half - 0.08), v3(x, 1.05, -half - 0.1), v3(x * 0.8, 1.12, -half + 0.05)], 0.035, "trim", n=8, m=body)
    K.tube([v3(-0.45, 1.05, -half - 0.1), v3(0.45, 1.05, -half - 0.1)], 0.035, "trim", n=8, m=body)
    for s in (-1, 1):
        K.rbox((s * (W / 2 + 0.02) - 0.08, 0.4, zw0 + 0.05), (s * (W / 2 + 0.02) + 0.08, 0.45, zb), 0.02, "trim", m=body)
    b.taper(body, 0.06, 0.6)
    glass = Mesh()
    xb, xr = W / 2 - 0.03, W / 2 - 0.14
    ws = cabin_glass(b, zw0, zw1, zr1, zb, 1.2, 1.22, roof, xb, xr, -0.05, "paint", body, glass)
    front_details(body, b, -half, 0.84, W, 1.05, 0.34, 0.98, 0.3)
    rear_details(body, half, W, 1.0, 0.7)
    mirrors(body, W, 1.32, zw0 + 0.12)
    for s in (-1, 1):
        door_lines(body, b, s * (W / 2 + 0.001), [zw0 + 0.02, zb - 0.06], 0.45, 1.2)
        handles(body, s * (W / 2), [0.35], 1.12)
    # Chase gear: roof rack with a light bar, antennas, GPS dome and a camera.
    for z in (zw1 + 0.1, zr1 - 0.1):
        K.rbox((-xr, roof + 0.03, z - 0.03), (xr, roof + 0.07, z + 0.03), 0.01, "trim", m=body)
    for s in (-1, 1):
        K.rbox((s * xr - 0.03, roof + 0.07, zw1 + 0.05), (s * xr + 0.03, roof + 0.1, zr1 - 0.05), 0.01, "trim", m=body)
    K.rbox((-0.7, roof + 0.08, zw1 + 0.06), (0.7, roof + 0.18, zw1 + 0.2), 0.04, "trim", m=body)
    for i in range(6):
        x = -0.6 + i * 0.24
        K.rbox((x - 0.09, roof + 0.1, zw1 + 0.05), (x + 0.09, roof + 0.165, zw1 + 0.07), 0.01, "amber" if i % 2 == 0 else "head_light", m=body)
    K.sphere((0.45, roof + 0.12, zr1 - 0.25), 0.09, 0.06, 0.09, "white", n=14, rows=6, m=body, top_only=True)
    for x in (-0.5, -0.35):
        K.tube([v3(x, roof + 0.07, zr1 - 0.2), v3(x, roof + 0.85, zr1 - 0.2)], 0.006, "black", n=5, m=body)
    K.rbox((-0.1, roof + 0.08, zw1 + 0.22), (0.1, roof + 0.22, zw1 + 0.38), 0.03, "dark", m=body)
    K.cylinder("z", (0, roof + 0.15), 0.04, zw1 + 0.2, zw1 + 0.23, "lens", n=12, m=body)
    # Weather station mast at the front of the bed: anemometer (turning) and a vane.
    mast = (0.62, 1.22, bed0 + 0.2)
    K.tube([v3(*mast), v3(mast[0], 2.75, mast[2])], 0.025, "steel", n=8, m=body)
    K.rbox((mast[0] - 0.12, 1.3, mast[2] - 0.12), (mast[0] + 0.12, 1.6, mast[2] + 0.12), 0.02, "white", m=body)
    for k in range(5):
        y = 1.62 + k * 0.03
        K.cylinder("y", (mast[0], mast[2]), 0.09 - k * 0.008, y, y + 0.02, "white", n=12, m=body)
    K.tube([v3(mast[0], 2.45, mast[2]), v3(mast[0], 2.45, mast[2] + 0.3)], 0.01, "steel", n=6, m=body)
    K.plate([v3(mast[0], 2.4, mast[2] + 0.25), v3(mast[0], 2.5, mast[2] + 0.25), v3(mast[0], 2.5, mast[2] + 0.4), v3(mast[0], 2.4, mast[2] + 0.4)],
            0.01, "white", m=body)
    # Equipment in the bed: instrument cases and two orange "turtle" probes.
    K.rbox((-0.85, 0.66, half - 0.75), (-0.2, 0.98, half - 0.2), 0.03, "dark", m=body)
    K.rbox((-0.85, 0.66, bed0 + 0.1), (-0.3, 0.9, bed0 + 0.6), 0.03, "grey", m=body)
    for z in (half - 0.55, half - 0.95):
        K.sphere((0.4, 0.66, z), 0.2, 0.14, 0.2, "orange", n=16, rows=8, m=body, top_only=True)
        K.cylinder("y", (0.4, z), 0.21, 0.66, 0.69, "trim", n=16, m=body)
    interior(v, body, W, zw0, 0.45, 1.2, False, screen=True, seat_mat="seat")
    v.part("body", body)
    v.part("glass", glass)
    v.part("wheel", wheel(b.wheel_r, 0.3, "offroad"))
    v.part("anemometer", anemometer())
    v.part("dish", dish(0.36))
    v.part("cover", cover([(-xr, roof + 0.2, zw1 + 0.25), (xr, roof + 0.2, zw1 + 0.25), (xr, roof + 0.12, zr1 - 0.05),
                           (-xr, roof + 0.12, zr1 - 0.05)])
           .extend(cover([(-W / 2 + 0.12, 1.12, -half + 0.4), (W / 2 - 0.12, 1.12, -half + 0.4), (W / 2 - 0.12, 1.22, zw0 - 0.05),
                          (-W / 2 + 0.12, 1.22, zw0 - 0.05)])))
    v.geo.update(wheels=[(-0.86, b.wheel_r, b.wz_f), (0.86, b.wheel_r, b.wz_f), (-0.86, b.wheel_r, b.wz_r), (0.86, b.wheel_r, b.wz_r)],
                 wheel_r=b.wheel_r, windshield=ws, wipers=[(-0.48, 1.22, zw0 + 0.05), (0.2, 1.22, zw0 + 0.05)], wiper_len=0.6,
                 size=(W, roof + 0.2, b.length), anemometer=(mast[0], 2.78, mast[2]), dish=(-0.3, 0.66, half - 1.4),
                 lights_front=[(-0.72, 0.98, -half), (0.72, 0.98, -half)])
    return v


# ------------------------------------------------------------------------------------------------ intercept vehicle
def intercept():
    """armoured: flat steel plates at angles, slit windows of thick glass, a camera turret, hydraulic spikes and skirts"""
    v = Vehicle("intercept")
    W = 2.4
    b = Body(length=6.0, width=W, sill=0.5, bumper_low=0.5, over_f=1.05, over_r=1.0, wz_f=-1.8, wz_r=1.8, wheel_r=0.5)
    half = b.length / 2
    body = Mesh()
    belt = 1.45
    roof = 2.25
    # Hull: a wedge nose, near-vertical sides, all hard plates.
    hull = [(-half, 0.55), (-half - 0.05, 0.95), (-half + 0.55, 1.35), (-1.2, belt), (half - 0.1, belt), (half, 1.3), (half, 0.55)]
    lowp = b.profile(-half, half, [(z, y) for z, y in hull if True])
    K.prism(b.profile(-half, -0.95, hull), "x", -W / 2, W / 2, "armor", bevel=0.04, m=body)
    K.prism(b.profile(0.75, half, hull), "x", -W / 2, W / 2, "armor", bevel=0.04, m=body)
    for s in (-1, 1):
        sidem = side_plate(b, -0.97, 0.77, hull, W / 2, 0.08, "armor")
        body.extend(sidem if s > 0 else mirror(sidem))
    del lowp
    # Armoured cab: sloped plates up to the roof; small slit windows.
    zf0, zf1, zr0 = -1.2, -0.75, half - 0.15
    xb, xr = W / 2 - 0.02, W / 2 - 0.22
    plates = [
        ([(-xb, belt, zf0), (xb, belt, zf0), (xr, roof, zf1), (-xr, roof, zf1)], "armor"),
        ([(-xr, roof, zf1), (xr, roof, zf1), (xr, roof, zr0), (-xr, roof, zr0)], "armor"),
        ([(-xr, roof, zr0), (xr, roof, zr0), (xb, belt, half - 0.1), (-xb, belt, half - 0.1)], "armor"),
    ]
    for pts, mat in plates:
        K.plate([v3(*p) for p in pts], 0.06, mat, m=body)
    for s in (-1, 1):
        K.plate([v3(s * xb, belt, zf0), v3(s * xr, roof, zf1), v3(s * xr, roof, zr0), v3(s * xb, belt, half - 0.1)], 0.06, "armor", m=body)
    glass = Mesh()
    # Windshield slit in the front plate, side slits.
    t0, t1 = 0.12, 0.72

    def lerp3(a, b2, t):
        return tuple(a[i] + (b2[i] - a[i]) * t for i in range(3))
    fl, fr = (-xb, belt, zf0), (xb, belt, zf0)
    tl, tr = (-xr, roof, zf1), (xr, roof, zf1)
    ws = [lerp3(fl, tl, t0), lerp3(fr, tr, t0), lerp3(fr, tr, t1), lerp3(fl, tl, t1)]
    ws = [(p[0] * 0.92, p[1], p[2] - 0.035) for p in ws]
    K.plate([v3(*p) for p in ws], 0.03, "glass", m=glass)
    K.plate([v3(p[0] * 1.03, p[1], p[2] + 0.005) for p in [(ws[0][0] - 0.05, ws[0][1] - 0.05, ws[0][2] + 0.02),
                                                           (ws[1][0] + 0.05, ws[1][1] - 0.05, ws[1][2] + 0.02),
                                                           (ws[2][0] + 0.05, ws[2][1] + 0.05, ws[2][2] + 0.02),
                                                           (ws[3][0] - 0.05, ws[3][1] + 0.05, ws[3][2] + 0.02)]], 0.02, "armor_dark", m=body)
    for s in (-1, 1):
        for z0, z1 in ((-0.6, 0.2), (0.45, 1.6)):
            y0, y1 = belt + 0.3, belt + 0.55
            def sp(y, z):
                t = (y - belt) / (roof - belt)
                return v3(s * (xb + (xr - xb) * t + 0.035), y, z)
            K.plate([sp(y0, z0), sp(y1, z0), sp(y1, z1), sp(y0, z1)], 0.02, "glass_dark", m=glass)
    # Ram plate, lights, steps, rooftop gear.
    K.plate([v3(-W / 2 + 0.1, 0.45, -half - 0.12), v3(W / 2 - 0.1, 0.45, -half - 0.12), v3(W / 2 - 0.1, 0.95, -half + 0.05),
             v3(-W / 2 + 0.1, 0.95, -half + 0.05)], 0.06, "armor_dark", m=body)
    for s in (-1, 1):
        lamp(body, (s * 0.85, 1.12, -half + 0.32), 0.3, 0.12, 0.1, "lens", "head_light", -1)
        lamp(body, (s * 0.95, 1.1, half + 0.0), 0.2, 0.14, 0.08, "lens", "tail_light", 1)
        K.rbox((s * 0.6 - 0.12, roof + 0.02, zf1 + 0.1), (s * 0.6 + 0.12, roof + 0.1, zf1 + 0.2), 0.02, "trim", m=body)
        K.rbox((s * 0.6 - 0.1, roof + 0.03, zf1 + 0.098), (s * 0.6 + 0.1, roof + 0.09, zf1 + 0.1), 0.01, "head_light", m=body)
    K.rbox((-0.5, roof, zr0 - 0.7), (0.5, roof + 0.12, zr0 - 0.2), 0.03, "amber", m=body)
    K.box((-0.3, 0.75, half), (0.3, 0.87, half + 0.02), "plate", m=body)
    # Bold safety stripes on the sides.
    for s in (-1, 1):
        K.box((s * (W / 2 + 0.002) - 0.004, 1.0, -0.9), (s * (W / 2 + 0.002) + 0.004, 1.12, 0.75), "orange", m=body)
        K.box((s * (W / 2 + 0.002) - 0.004, 0.92, -0.9), (s * (W / 2 + 0.002) + 0.004, 0.96, 0.75), "yellow", m=body)
    # Spike housings at the corners.
    spikes = [(-W / 2 + 0.25, 0.95, -1.15), (W / 2 - 0.25, 0.95, -1.15), (-W / 2 + 0.25, 0.95, 1.15), (W / 2 - 0.25, 0.95, 1.15)]
    for x, y, z in spikes:
        K.cylinder("y", (x, z), 0.11, 0.42, y, "armor_dark", n=12, m=body)
    # Anemometer mast at the rear of the roof.
    mast = (-0.7, roof, zr0 - 0.25)
    K.tube([v3(*mast), v3(mast[0], roof + 0.9, mast[2])], 0.03, "steel", n=8, m=body)
    interior(v, body, W, zf0 + 0.08, 0.62, belt - 0.05, False, driver_x=-0.45, screen=True, seat_mat="seat_tan")
    v.part("body", body)
    v.part("glass", glass)
    v.part("wheel", wheel(b.wheel_r, 0.38, "offroad"))
    v.part("spike", spike())
    skirt = Mesh()
    K.plate([v3(0, 0, -1.9), v3(0, 0, 1.9), v3(0, -0.45, 1.85), v3(0, -0.45, -1.85)], 0.05, "armor_dark", m=skirt)
    v.part("skirt", skirt)
    v.part("turret", turret())
    v.part("anemometer", anemometer())
    v.part("cover", cover([(-xr, roof + 0.03, zf1 + 0.3), (xr, roof + 0.03, zf1 + 0.3), (xr, roof + 0.03, zr0 - 0.8),
                           (-xr, roof + 0.03, zr0 - 0.8)])
           .extend(cover([(-W / 2 + 0.15, 1.36, -half + 0.6), (W / 2 - 0.15, 1.36, -half + 0.6), (W / 2 - 0.15, 1.45, zf0 - 0.05),
                          (-W / 2 + 0.15, 1.45, zf0 - 0.05)])))
    v.geo.update(wheels=[(-1.0, b.wheel_r, b.wz_f), (1.0, b.wheel_r, b.wz_f), (-1.0, b.wheel_r, b.wz_r), (1.0, b.wheel_r, b.wz_r)],
                 wheel_r=b.wheel_r, windshield=ws, wipers=[(-0.38, ws[0][1] + 0.02, ws[0][2] - 0.02), (0.22, ws[0][1] + 0.02, ws[0][2] - 0.02)],
                 wiper_len=0.45, size=(W, roof + 0.4, b.length), spikes=spikes, skirts=[(-W / 2 - 0.03, 0.5, 0.0), (W / 2 + 0.03, 0.5, 0.0)],
                 turret=(0.35, roof, zf1 + 0.7), anemometer=(mast[0], roof + 0.93, mast[2]),
                 lights_front=[(-0.85, 1.12, -half + 0.28), (0.85, 1.12, -half + 0.28)])
    return v


# ------------------------------------------------------------------------------------------------ output
def write(v):
    folder = "vehicle/" + v.name
    for name, mesh in v.parts.items():
        m = mesh.copy()
        m.scale(SCALE)
        K.write_part(m, folder, name, RES)


def java(vehicles):
    def f(x):
        return f"{x:.4f}F"

    def vec(p):
        return "{" + ", ".join(f(c) for c in p) + "}"
    lines = ["package com.realweather.vehicle;", "",
             "/** Generated by tools/vehicles/vehicles.py: points of each vehicle model in metres (front -Z, +X right). */",
             "public final class VehicleGeometry {", "    private VehicleGeometry() {}", ""]
    for v in vehicles:
        g = v.geo
        n = v.name.upper()
        lines.append(f"    // {v.name}")
        lines.append(f"    public static final float[][] {n}_WHEELS = {{{', '.join(vec(p) for p in g['wheels'])}}};")
        lines.append(f"    public static final float {n}_WHEEL_R = {f(g['wheel_r'])};")
        lines.append(f"    public static final float[][] {n}_SEATS = {{{', '.join(vec(p) for p in g['seats'])}}};")
        lines.append(f"    public static final float[] {n}_STEER = {vec(g['steer'])};")
        lines.append(f"    public static final float[][] {n}_PEDALS = {{{', '.join(vec(p) for p in g['pedals'])}}};")
        lines.append(f"    public static final float[] {n}_GEAR = {vec(g['gear'])};")
        lines.append(f"    public static final float[] {n}_SPEEDO = {vec(g['speedo'])};")
        lines.append(f"    public static final float[] {n}_TACHO = {vec(g['tacho'])};")
        lines.append(f"    public static final float[][] {n}_WINDSHIELD = {{{', '.join(vec(p) for p in g['windshield'])}}};")
        lines.append(f"    public static final float[][] {n}_WIPERS = {{{', '.join(vec(p) for p in g['wipers'])}}};")
        lines.append(f"    public static final float {n}_WIPER_LEN = {f(g['wiper_len'])};")
        lines.append(f"    public static final float[] {n}_SIZE = {vec(g['size'])};")
        lines.append(f"    public static final float[][] {n}_LIGHTS = {{{', '.join(vec(p) for p in g['lights_front'])}}};")
        lines.append(f"    public static final float[][] {n}_SCREEN = {{{', '.join(vec(p) for p in g.get('screen', [(0, 0, 0)] * 4))}}};")
        for key in ("anemometer", "dish", "turret"):
            if key in g:
                lines.append(f"    public static final float[] {n}_{key.upper()} = {vec(g[key])};")
        for key in ("spikes", "skirts"):
            if key in g:
                lines.append(f"    public static final float[][] {n}_{key.upper()} = {{{', '.join(vec(p) for p in g[key])}}};")
        lines.append("")
    lines.append("}")
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as fh:
        fh.write("\n".join(lines) + "\n")


def preview(vehicles, path):
    from PIL import Image
    images = []
    for v in vehicles:
        meshes = [(v.parts["body"], (0.75, 0.12, 0.1) if v.name == "sedan" else (0.95, 0.95, 0.95) if v.name == "pickup" else None),
                  (v.parts["glass"], None)]
        for (x, y, z) in v.geo["wheels"]:
            w = v.parts["wheel"].copy()
            if x < 0:
                w.rotate("y", 180)
            w.translate((x, y, z))
            meshes.append((w, None))
        for yaw in (35, 150):
            images.append(K.render(meshes, yaw=yaw, pitch=18, size=(520, 330)))
    sheet = K.sheet(images, cols=2)
    sheet.save(path)
    return sheet


def icons(vehicles):
    """inventory icons rendered from the models"""
    from PIL import Image
    out = os.path.join(RES, "textures", "item")
    os.makedirs(out, exist_ok=True)
    for v in vehicles:
        tint = (0.75, 0.12, 0.1) if v.name == "sedan" else (0.95, 0.95, 0.95) if v.name == "pickup" else None
        meshes = [(v.parts["body"], tint), (v.parts["glass"], None)]
        for (x, y, z) in v.geo["wheels"]:
            w = v.parts["wheel"].copy()
            if x < 0:
                w.rotate("y", 180)
            w.translate((x, y, z))
            meshes.append((w, None))
        im = K.render(meshes, yaw=215, pitch=22, size=(256, 256), bg=(0, 0, 0), zoom=1.15).convert("RGBA")
        px = im.load()
        for yy in range(im.size[1]):
            for xx in range(im.size[0]):
                r, g, b, a = px[xx, yy]
                if r == 0 and g == 0 and b == 0:
                    px[xx, yy] = (0, 0, 0, 0)
        im.resize((64, 64), Image.LANCZOS).save(os.path.join(out, v.name + ".png"))
        with open(os.path.join(RES, "models", "item", v.name + ".json"), "w") as fh:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "realweather:item/" + v.name}}, fh, indent=2)


if __name__ == "__main__":
    vs = [sedan(), pickup(), intercept()]
    K.make_textures(RES)
    for v in vs:
        write(v)
    shared = Vehicle("parts")
    shared.part("steering", steering_wheel())
    shared.part("pedal", pedal(False))
    shared.part("pedal_wide", pedal(True))
    shared.part("gear", gear_lever())
    shared.part("wiper", wiper(1.0))
    write(shared)
    java(vs)
    icons(vs)
    if len(sys.argv) > 1:
        preview(vs, sys.argv[1])
    for v in vs:
        print(v.name, {k: m.quad_count() for k, m in v.parts.items()})
