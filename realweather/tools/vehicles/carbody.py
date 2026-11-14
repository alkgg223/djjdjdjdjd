"""Faceted car bodies in a realistic low-poly style.

One skin is lofted through cross-sections along the car (front -Z to rear +Z). Every section has the same nine
points on each side: rocker, rocker crease, door bulge, shoulder line, beltline, then either the hood (fender edge,
hood crease, centre) or the greenhouse (window seal, top of the side glass, roof rail, roof centre). Between key
stations everything is linear, so the panels are flat facets with crisp creases where they meet, and the skin is
flat shaded. The quads are sorted into paint, black trim, glass, pillars, doors and door windows.

Detail that real cars carry in their paint and lenses (lamps, grilles, plates, seams, badges) is laid onto the
facets with projector decals: every skin facet inside a box that faces the projector gets the texture, clipped to
the box and lifted a few millimetres, so the drawing follows the facets and wraps round corners.
"""
import math

import numpy as np

import meshkit as K
from meshkit import Mesh, v3


def lin(pts, z):
    """piecewise linear (z, value) curve"""
    if z <= pts[0][0]:
        return pts[0][1]
    for (z0, y0), (z1, y1) in zip(pts, pts[1:]):
        if z <= z1:
            t = (z - z0) / max(1e-9, z1 - z0)
            return y0 + (y1 - y0) * t
    return pts[-1][1]


def clamp01(x):
    return min(1.0, max(0.0, x))


ROCKER, LOWER, DOOR, SHOULDER, SEAL, WINDOW, FRAME, RAIL, TOP = range(9)
NP = 10          # points per half section


class Car:
    """
    b: the Body (length, wheel arches, sill and bumper heights); plan: [(z, half width)]; belt: [(z, y)] shoulder
    and beltline; top: [(z, y)] centreline (hood, cowl, windshield, roof, rear window, deck); zw0/zw1 windshield base
    and top; zr1/zb rear window top and base; windows: [(z0, z1)] side glass; open_top: (z0, z1) left open (a bed).
    """

    def __init__(self, b, plan, belt, top, zw0, zw1, zr1, zb, windows, tumble=0.15, crown=0.04, rail=0.07, hood_crease=0.6,
                 shoulder=0.1, rocker=0.13, tuck=0.07, cowl=0.35, tail=0.3, open_top=None, rocker_mat="paint", seal_mat="trim",
                 pillar_mat="trim", roof_mat="paint", c_mat="paint", frame_mat="trim", arch_steps=10, extra=()):
        self.b = b
        self.length = b.length
        self.half = b.length / 2
        self.plan, self.belt, self.top = plan, belt, top
        self.width = 2 * max(w for _, w in plan)
        self.zw0, self.zw1, self.zr1, self.zb = zw0, zw1, zr1, zb
        self.windows = list(windows)
        self.tumble, self.crown, self.rail, self.hood_crease = tumble, crown, rail, hood_crease
        self.shoulder, self.rocker, self.tuck, self.cowl, self.tail = shoulder, rocker, tuck, cowl, tail
        self.open_top = open_top
        self.rocker_mat, self.seal_mat, self.pillar_mat, self.roof_mat, self.c_mat = rocker_mat, seal_mat, pillar_mat, roof_mat, c_mat
        self.frame_mat = frame_mat
        self.arch_steps = arch_steps
        self.extra = list(extra)
        zm = (zw1 + zr1) / 2
        self.gh_full = max(0.2, lin(top, zm) - lin(belt, zm))
        self.skin = None
        self.doors = {}
        self.door_glass = {}

    # ---------------------------------------------------------------------------------------- lines
    def sill(self, z):
        """lower edge of the body without the wheel arches"""
        b, half = self.b, self.half
        if z < -half + b.over_f:
            return b.bumper_low + (b.sill - b.bumper_low) * (z + half) / b.over_f
        if z > half - b.over_r:
            return b.bumper_low + (b.sill - b.bumper_low) * (half - z) / b.over_r
        return b.sill

    def bottom(self, z):
        y = self.sill(z)
        for zw in (self.b.wz_f, self.b.wz_r):
            d = z - zw
            ra = self.b.wheel_r + 0.07
            if abs(d) < ra:
                y = max(y, self.b.wheel_r + math.sqrt(ra * ra - d * d))
        return y

    def g(self, z):
        """1 in the greenhouse, 0 over the hood and deck, a ramp across the cowl and the tail"""
        if z < self.zw0:
            return clamp01(1 - (self.zw0 - z) / self.cowl)
        if z > self.zb:
            return clamp01(1 - (z - self.zb) / max(1e-4, self.tail))
        return 1.0

    def roofness(self, z):
        if z <= self.zw1 or z >= self.zr1:
            return 0.0
        return clamp01(min(z - self.zw1, self.zr1 - z) / 0.2)

    def section(self, z):
        """the ten points of the right half, bottom to roof centre, as (x, y)"""
        hw = lin(self.plan, z)
        yb = self.bottom(z)
        ys = lin(self.belt, z)
        yt = lin(self.top, z)
        g = self.g(z)
        yr = self.sill(z) + self.rocker
        y3 = ys - self.shoulder
        y2 = yr + 0.45 * (y3 - yr)
        P = [(hw - self.tuck, yb), (hw - 0.012, max(yb, yr)), (hw, max(yb, y2)), (hw - 0.002, max(yb, y3)), (hw - 0.03, max(yb, ys))]
        F = clamp01((yt - ys) / self.gh_full)
        cr = self.crown * self.roofness(z)
        x5c, y5c = hw - 0.065, ys + 0.012
        x6c = x5c - self.tumble * F
        y6c = max(y5c, yt - cr - 0.02 * F)
        x7c = x6c - self.rail * (0.4 + 0.6 * F)
        y7c = max(y6c, yt - cr * 0.45)
        d = yt - ys
        x5h, y5h = hw - 0.07, ys + 0.012
        x6h, y6h = hw * self.hood_crease, ys + 0.7 * d + 0.008
        x7h, y7h = hw * 0.3, yt - 0.002

        def mix(a, b):
            return a + (b - a) * g
        p6 = (mix(x6h, x6c), mix(y6h, y6c))
        p7 = (mix(x7h, x7c), mix(y7h, y7c))
        # The window frame: a narrow strip along the top of the side glass.
        f = 0.3 * g
        p6b = (p6[0] + (p7[0] - p6[0]) * f, p6[1] + (p7[1] - p6[1]) * f)
        P += [(mix(x5h, x5c), mix(y5h, y5c)), p6, p6b, p7, (0.0, yt)]
        return P

    def point(self, z, k, side=1):
        x, y = self.section(z)[k]
        return v3(side * x, y, z)

    def ring(self, z):
        right = self.section(z)
        left = [(-x, y) for (x, y) in reversed(right[:-1])]
        return [v3(x, y, z) for x, y in right + left]

    # ---------------------------------------------------------------------------------------- stations
    def stations(self, doors):
        zs = [-self.half, self.half]
        for pts in (self.plan, self.belt, self.top):
            zs += [z for z, _ in pts]
        zs += [self.zw0, self.zw1, self.zr1, self.zb, self.zw0 - self.cowl, self.zb + self.tail, self.zw0 - 0.08,
               self.zw1 + 0.2, self.zr1 - 0.2]
        for z0, z1 in list(doors) + self.windows:
            zs += [z0, z1]
        if self.open_top:
            zs += list(self.open_top)
        zs += self.extra
        ra = self.b.wheel_r + 0.07
        for zw in (self.b.wz_f, self.b.wz_r):
            for i in range(self.arch_steps + 1):
                zs.append(zw - ra * math.cos(math.pi * i / self.arch_steps))
        zs = sorted(set(round(z, 4) for z in zs if -self.half <= z <= self.half))
        out = []
        for z in zs:
            if not out or z - out[-1] > 0.004:
                out.append(z)
        return out

    # ---------------------------------------------------------------------------------------- materials
    def material(self, zc, seg, paint):
        cabin = self.zw0 < zc < self.zb
        if seg == ROCKER:
            return self.rocker_mat
        if seg <= SHOULDER:
            return paint
        if seg == TOP and self.zw0 - 0.08 < zc < self.zw0:
            return "trim"           # cowl panel under the windshield
        if not cabin:
            return paint
        if seg == SEAL:
            return self.seal_mat
        if seg == WINDOW:
            for z0, z1 in self.windows:
                if z0 <= zc <= z1:
                    return "glass"
            if zc < self.windows[-1][1]:
                return self.pillar_mat
            return self.c_mat
        if seg == FRAME:
            return self.frame_mat if zc < self.windows[-1][1] + 0.02 or self.c_mat == "glass" else ("trim" if zc > self.zr1 else self.roof_mat)
        if seg == RAIL:
            if zc < self.zw1 or zc > self.zr1:
                return "trim"
            return self.roof_mat
        if zc < self.zw1 or zc > self.zr1:
            return "glass"
        return self.roof_mat

    # ---------------------------------------------------------------------------------------- build
    def build(self, paint="paint", doors=()):
        """returns (skin, glass); the door panels and door windows go to self.doors / self.door_glass"""
        zs = self.stations(doors)
        rings = [self.ring(z) for z in zs]
        skin, glass = Mesh(), Mesh()
        n = len(rings[0])
        for a in range(len(zs) - 1):
            zc = (zs[a] + zs[a + 1]) / 2
            ra, rb = rings[a], rings[a + 1]
            for e in range(n - 1):
                seg = e if e < NP - 1 else 2 * NP - 3 - e
                side = 1 if e < NP - 1 else -1
                if self.open_top and self.open_top[0] <= zc <= self.open_top[1] and seg >= WINDOW:
                    continue
                mat = self.material(zc, seg, paint)
                quad = [ra[e], ra[e + 1], rb[e + 1], rb[e]]
                pts = []
                for p in quad:
                    if not pts or np.linalg.norm(p - pts[-1]) > 1e-6:
                        pts.append(p)
                if len(pts) > 1 and np.linalg.norm(pts[0] - pts[-1]) < 1e-6:
                    pts.pop()
                if len(pts) < 3:
                    continue
                d = ra[e + 1] - ra[e]
                if np.linalg.norm(d[:2]) < 1e-6:
                    d = rb[e + 1] - rb[e]
                out = v3(d[1], -d[0], 0)
                target = glass if mat == "glass" else skin
                if LOWER <= seg <= WINDOW:
                    for i, (z0, z1) in enumerate(doors):
                        if z0 <= zc <= z1:
                            name = "door_" + ["f", "r"][i] + ("r" if side > 0 else "l")
                            if mat == "glass":
                                target = self.door_glass.setdefault(name + "_glass", Mesh())
                            else:
                                target = self.doors.setdefault(name, Mesh())
                            break
                target.add_oriented(pts, mat, out, smooth=False)
        # Nose and tail faces.
        for idx, outward in ((0, v3(0, 0, -1)), (-1, v3(0, 0, 1))):
            r = []
            for p in rings[idx]:
                if not r or np.linalg.norm(p - r[-1]) > 1e-6:
                    r.append(p)
            K.cap(skin, r, paint, outward)
        self.skin = skin
        self.door_spans = list(doors)
        return skin, glass

    def underbody(self, m, mat="trim"):
        b = self.b
        y = b.sill + 0.02
        w = self.width / 2 - 0.1
        cuts = sorted([(zw - b.wheel_r - 0.1, zw + b.wheel_r + 0.1) for zw in (b.wz_f, b.wz_r)])
        z_from = -self.half + 0.25
        for c0, c1 in cuts + [(self.half - 0.25, self.half)]:
            if c0 > z_from:
                K.box((-w, y, z_from), (w, y + 0.02, c0), mat, m=m)
            z_from = c1

    def skins(self):
        """every skin mesh (body and doors), for decals"""
        return [self.skin] + list(self.doors.values())

    def windshield(self, inset=0.07):
        """windshield corners (bottom-left, bottom-right, top-right, top-left), just above the glass"""
        za, zb = self.zw0 + 0.07, self.zw1 - 0.06
        xa, ya = self.section(za)[8]
        xb, yb = self.section(zb)[8]
        return [(-xa + inset, ya + 0.008, za), (xa - inset, ya + 0.008, za), (xb - inset, yb + 0.008, zb), (-xb + inset, yb + 0.008, zb)]

    def top_y(self, z, x):
        """height of the top surface (hood, roof, deck) at (x, z)"""
        P = self.section(z)
        x = abs(x)
        for k in range(4, NP - 1):
            (x0, y0), (x1, y1) = P[k], P[k + 1]
            if x1 <= x <= x0 + 1e-9:
                t = (x0 - x) / max(1e-9, x0 - x1)
                return y0 + (y1 - y0) * t
        return P[-1][1] if x < P[-2][0] else P[4][1]


# ------------------------------------------------------------------------------------------------ projector decals
def _clip(pts, axis, value, keep_greater):
    out = []
    n = len(pts)
    for i in range(n):
        a, b = pts[i], pts[(i + 1) % n]
        da = (a[axis] - value) * (1 if keep_greater else -1)
        db = (b[axis] - value) * (1 if keep_greater else -1)
        if da >= 0:
            out.append(a)
        if (da >= 0) != (db >= 0):
            t = da / (da - db)
            out.append(a + (b - a) * t)
    return out


def project(meshes, out, mat, lo, hi, facing, offset=0.005, min_dot=0.3, flip=False, uv_box=None):
    """lay the texture of `mat` over every facet of `meshes` inside the box lo..hi that faces `facing` ('-z' front,
    '+z' rear, '+x' right, '-x' left, '+y' top); texture up is +Y (or the front for '+y'), as seen by a viewer facing
    the decal. uv_box: (u0, v0, u1, v1) part of the texture to use. Results go into `out` (a Mesh or a dict
    {mesh id: Mesh} to keep decals on the mesh they lie on)."""
    lo = np.asarray(lo, dtype=float)
    hi = np.asarray(hi, dtype=float)
    sign = 1.0 if facing[0] == "+" else -1.0
    axis = "xyz".index(facing[1])
    d = np.zeros(3)
    d[axis] = sign
    u0, v0, u1, v1 = uv_box or (0.0, 0.0, 1.0, 1.0)

    def uv(q):
        x, y, z = q
        if facing == "-z":
            u, v = (hi[0] - x) / (hi[0] - lo[0]), (hi[1] - y) / (hi[1] - lo[1])
        elif facing == "+z":
            u, v = (x - lo[0]) / (hi[0] - lo[0]), (hi[1] - y) / (hi[1] - lo[1])
        elif facing == "+x":
            u, v = (hi[2] - z) / (hi[2] - lo[2]), (hi[1] - y) / (hi[1] - lo[1])
        elif facing == "-x":
            u, v = (z - lo[2]) / (hi[2] - lo[2]), (hi[1] - y) / (hi[1] - lo[1])
        else:
            u, v = (x - lo[0]) / (hi[0] - lo[0]), (z - lo[2]) / (hi[2] - lo[2])
        if flip:
            u = 1 - u
        return (u0 + (u1 - u0) * min(1.0, max(0.0, u)), v0 + (v1 - v0) * min(1.0, max(0.0, v)))

    count = 0
    for src in meshes:
        target = out[id(src)] if isinstance(out, dict) else out
        for p in list(src.polys):
            if p["mat"] == "glass" or p.get("uv") is not None:
                continue
            pts = [np.asarray(q, dtype=float) for q in p["pts"]]
            arr = np.array(pts)
            if (arr.max(0) < lo).any() or (arr.min(0) > hi).any():
                continue
            n = K.poly_normal(pts)
            if np.dot(n, d) < min_dot:
                continue
            for ax in range(3):
                pts = _clip(pts, ax, lo[ax], True)
                if len(pts) < 3:
                    break
                pts = _clip(pts, ax, hi[ax], False)
                if len(pts) < 3:
                    break
            clean = []
            for q in pts:
                if not clean or np.linalg.norm(q - clean[-1]) > 1e-5:
                    clean.append(q)
            if len(clean) > 1 and np.linalg.norm(clean[0] - clean[-1]) < 1e-5:
                clean.pop()
            if len(clean) < 3:
                continue
            area = 0.0
            for i in range(1, len(clean) - 1):
                area += np.linalg.norm(np.cross(clean[i] - clean[0], clean[i + 1] - clean[0]))
            if area < 1e-7:
                continue
            target.add([q + n * offset for q in clean], mat, [n] * len(clean), smooth=False, uv=[uv(q) for q in clean])
            count += 1
    return count


def pair(meshes, out, mat, x0, x1, y0, y1, z0, z1, facing, **kw):
    """the same decal on both sides of the car (mirrored): x0..x1 on the right (+X)"""
    flip_right = facing == "-z"
    project(meshes, out, mat, (x0, y0, z0), (x1, y1, z1), facing, flip=flip_right, **kw)
    project(meshes, out, mat, (-x1, y0, z0), (-x0, y1, z1), facing, flip=not flip_right, **kw)


def sides(meshes, out, mat, y0, y1, z0, z1, x_in=0.3, **kw):
    """a decal on both flanks (right facing +X, left facing -X)"""
    project(meshes, out, mat, (x_in, y0, z0), (5.0, y1, z1), "+x", **kw)
    project(meshes, out, mat, (-5.0, y0, z0), (-x_in, y1, z1), "-x", **kw)


# ------------------------------------------------------------------------------------------------ small parts
def mirror(m, car, z, k=4, mat="paint", base="trim"):
    """faceted door mirror on a short black arm at the front of the side glass"""
    for s in (-1, 1):
        x, y = car.section(z)[k]
        x *= s
        K.rbox((x - s * 0.02 - 0.03, y - 0.01, z - 0.06), (x + s * 0.05 + 0.03 * s * 0, y + 0.06, z + 0.05), 0.012, base, seg=1, m=m)
        hx0, hx1 = sorted((x + s * 0.04, x + s * 0.24))
        K.rbox((hx0, y + 0.02, z - 0.03), (hx1, y + 0.15, z + 0.06), 0.03, mat, seg=1, m=m)
        K.box((hx0 + 0.012, y + 0.03, z + 0.06), (hx1 - 0.012, y + 0.14, z + 0.066), "chrome", m=m)
        K.box((hx0 + 0.01, y + 0.02, z - 0.035) if s > 0 else (hx0 + 0.01, y + 0.02, z - 0.035),
              (hx1 - 0.01, y + 0.03, z - 0.02), "amber", m=m)


def handle(m, car, z, k=3, mat="chrome", lift=0.03):
    """door pull a little below the shoulder line"""
    for s in (-1, 1):
        x, y = car.section(z)[k]
        y -= lift
        xo = s * (x + 0.006)
        K.rbox((min(xo, xo + s * 0.022), y - 0.017, z - 0.075), (max(xo, xo + s * 0.022), y + 0.017, z + 0.075), 0.008, mat, seg=1, m=m)


def seams(car, z_edges, y0, y1, w=0.007):
    """thin dark panel gaps on the doors at their front and rear edges"""
    for name, m in car.doors.items():
        side = 1 if name[6] == "r" else -1
        i = 0 if name[5] == "f" else 1
        z0, z1 = car.door_spans[i]
        for za, zb in ((z0, z0 + w), (z1 - w, z1)):
            if side > 0:
                project([m], m, "black", (0.2, y0, za), (5.0, y1, zb), "+x", offset=0.003, min_dot=0.2)
            else:
                project([m], m, "black", (-5.0, y0, za), (-0.2, y1, zb), "-x", offset=0.003, min_dot=0.2)


def arch_flares(m, car, mat="trim", width=0.07, out=0.025, n=10):
    """flat arch mouldings round the wheel arches"""
    b = car.b
    ra = b.wheel_r + 0.07
    for zw in (b.wz_f, b.wz_r):
        for s in (-1, 1):
            for i in range(n):
                a0 = math.pi * i / n
                a1 = math.pi * (i + 1) / n
                pts = []
                for a, r in ((a0, ra - 0.005), (a1, ra - 0.005), (a1, ra + width), (a0, ra + width)):
                    z = zw - r * math.cos(a)
                    y = b.wheel_r + r * math.sin(a)
                    hw = lin(car.plan, z)
                    pts.append(v3(s * (hw + out), y, z))
                m.add_oriented(pts, mat, v3(s, 0, 0), smooth=False)
                # Edge toward the body.
                for a, a2 in ((a0, a1),):
                    q = []
                    for aa, r, xo in ((a, ra + width, out), (a2, ra + width, out), (a2, ra + width, -0.01), (a, ra + width, -0.01)):
                        z = zw - r * math.cos(aa)
                        y = b.wheel_r + r * math.sin(aa)
                        q.append(v3(s * (lin(car.plan, z) + xo), y, z))
                    m.add_oriented(q, mat, v3(0, math.sin((a + a2) / 2), -math.cos((a + a2) / 2)), smooth=False)
