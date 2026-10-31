"""Smooth vehicle meshes for the NeoForge OBJ loader.

Shapes are built from polygons in model units (16 = one model block; a vehicle renderer scales the block up), with
rounded boxes, lofts through cross sections, filleted profiles, cylinders, tyres and wings. Normals are smoothed
across faces that meet at less than the auto-smooth angle, so rounded shapes shade smoothly and real corners stay
crisp. Each part is written as <part>.obj + <part>.mtl + a <part>.json model using the "neoforge:obj" loader.

Materials carry a colour (OBJ Kd) over a few shared, nearly white textures; "paint" materials take the team colour
the renderer passes (tint index 0).
"""
import math
import os

import numpy as np
from PIL import Image

# ------------------------------------------------------------------------------------------------ materials
# name: (rgb, texture, tinted by the team colour)
MATS = {
    "paint": ((1.0, 1.0, 1.0), "paint", True),
    "paint_dark": ((0.62, 0.62, 0.62), "paint", True),
    "white": ((0.92, 0.92, 0.90), "paint", False),
    "grey": ((0.45, 0.47, 0.49), "paint", False),
    "armor": ((0.36, 0.37, 0.36), "metal", False),
    "armor_dark": ((0.2, 0.21, 0.21), "metal", False),
    "dark": ((0.14, 0.145, 0.15), "plastic", False),
    "trim": ((0.08, 0.08, 0.085), "plastic", False),
    "black": ((0.03, 0.03, 0.035), "plain", False),
    "dash": ((0.16, 0.16, 0.17), "plastic", False),
    "seat": ((0.2, 0.2, 0.22), "fabric", False),
    "seat_tan": ((0.55, 0.47, 0.37), "fabric", False),
    "carpet": ((0.1, 0.1, 0.11), "fabric", False),
    "steel": ((0.5, 0.52, 0.55), "metal", False),
    "chrome": ((0.82, 0.84, 0.88), "metal", False),
    "rubber": ((0.09, 0.09, 0.09), "rubber", False),
    "rim": ((0.66, 0.68, 0.7), "metal", False),
    "glass": ((0.55, 0.66, 0.74), "glass", False),
    "glass_dark": ((0.28, 0.34, 0.38), "glass", False),
    "lens": ((0.92, 0.94, 0.98), "glass", False),
    "head_light": ((1.0, 0.98, 0.9), "plain", False),
    "tail_light": ((0.9, 0.08, 0.06), "plain", False),
    "amber": ((1.0, 0.62, 0.1), "plain", False),
    "blue_light": ((0.2, 0.45, 1.0), "plain", False),
    "plate": ((0.94, 0.94, 0.9), "plain", False),
    "yellow": ((0.95, 0.75, 0.1), "paint", False),
    "orange": ((0.95, 0.42, 0.08), "paint", False),
    "red": ((0.75, 0.1, 0.08), "paint", False),
    "gauge": ((1.0, 1.0, 1.0), "gauge", False),
    "gauge_rpm": ((1.0, 1.0, 1.0), "gauge", False),
    "screen": ((0.05, 0.08, 0.1), "plain", False),
    "snow": ((0.97, 0.98, 1.0), "snow", False),
    "hydraulic": ((0.78, 0.8, 0.82), "metal", False),
    "decal": ((0.95, 0.95, 0.95), "plain", False),
}
TEXTURES = ["paint", "metal", "rubber", "glass", "plastic", "fabric", "plain", "gauge", "snow"]
EMISSIVE = {"head_light", "tail_light", "amber", "blue_light", "screen"}
TEX_REF = "realweather:item/vehicle/"
SMOOTH_DEG = 38.0
UV_TILE = 32.0          # model units the whole texture spans


def v3(x, y, z):
    return np.array([x, y, z], dtype=np.float64)


def norm(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def poly_normal(pts):
    """Newell's method: robust for any planar-ish polygon"""
    n = np.zeros(3)
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        n += np.array([(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])])
    return norm(n)


class Mesh:
    """A soup of polygons with materials; optional explicit per-corner normals."""

    def __init__(self):
        self.polys = []

    def add(self, pts, mat, normals=None, smooth=True, uv=None):
        pts = [np.asarray(p, dtype=np.float64) for p in pts]
        if len(pts) < 3:
            return
        if normals is not None:
            normals = [norm(np.asarray(n, dtype=np.float64)) for n in normals]
        self.polys.append({"pts": pts, "mat": mat, "n": normals, "smooth": smooth, "uv": uv})

    def add_oriented(self, pts, mat, outward, normals=None, smooth=True):
        """add a polygon, flipping it if it faces away from the given outward direction"""
        pts = list(pts)
        if np.dot(poly_normal(pts), outward) < 0:
            pts.reverse()
            if normals is not None:
                normals = list(reversed(normals))
        self.add(pts, mat, normals, smooth)

    def extend(self, other):
        self.polys.extend(other.polys)
        return self

    def apply(self, fn, nfn=None):
        """transform every point (and normal) in place"""
        for p in self.polys:
            p["pts"] = [fn(q) for q in p["pts"]]
            if p["n"] is not None and nfn is not None:
                p["n"] = [norm(nfn(n)) for n in p["n"]]
        return self

    def translate(self, d):
        d = np.asarray(d, dtype=np.float64)
        return self.apply(lambda q: q + d, lambda n: n)

    def rotate(self, axis, deg, origin=(0, 0, 0)):
        R = rot_matrix(axis, deg)
        o = np.asarray(origin, dtype=np.float64)
        return self.apply(lambda q: R @ (q - o) + o, lambda n: R @ n)

    def scale(self, s, origin=(0, 0, 0)):
        s = np.asarray(s if hasattr(s, "__len__") else (s, s, s), dtype=np.float64)
        o = np.asarray(origin, dtype=np.float64)
        flip = np.prod(np.sign(s)) < 0
        self.apply(lambda q: (q - o) * s + o, lambda n: n / s)
        if flip:
            for p in self.polys:
                p["pts"].reverse()
                if p["n"] is not None:
                    p["n"].reverse()
        return self

    def mirrored_x(self, cx=8.0):
        """a copy mirrored about the plane x = cx (models are symmetric about the middle)"""
        m = Mesh()
        for p in self.polys:
            pts = [np.array([2 * cx - q[0], q[1], q[2]]) for q in reversed(p["pts"])]
            ns = None if p["n"] is None else [np.array([-n[0], n[1], n[2]]) for n in reversed(p["n"])]
            m.polys.append({"pts": pts, "mat": p["mat"], "n": ns, "smooth": p["smooth"],
                            "uv": None if p.get("uv") is None else [(1 - u, v) for u, v in reversed(p["uv"])]})
        return m

    def copy(self):
        m = Mesh()
        for p in self.polys:
            m.polys.append({"pts": [q.copy() for q in p["pts"]], "mat": p["mat"], "n": None if p["n"] is None else [n.copy() for n in p["n"]],
                            "smooth": p["smooth"], "uv": p.get("uv")})
        return m

    def recolor(self, mat):
        for p in self.polys:
            p["mat"] = mat
        return self

    def bounds(self):
        a = np.array([q for p in self.polys for q in p["pts"]])
        return a.min(axis=0), a.max(axis=0)

    def quad_count(self):
        c = 0
        for p in self.polys:
            k = len(p["pts"])
            c += 1 if k <= 4 else k - 2
        return c


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


# ------------------------------------------------------------------------------------------------ 2D shapes
def rrect(w, h, r, seg=3, cx=0.0, cy=0.0):
    """rounded rectangle centred on (cx, cy), counter-clockwise"""
    r = max(0.0, min(r, w / 2 - 1e-4, h / 2 - 1e-4))
    pts = []
    corners = [(w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180), (w / 2 - r, -h / 2 + r, 270)]
    for x, y, a0 in corners:
        if r <= 1e-6:
            pts.append((cx + x, cy + y))
            continue
        for k in range(seg + 1):
            a = math.radians(a0 + 90 * k / seg)
            pts.append((cx + x + r * math.cos(a), cy + y + r * math.sin(a)))
    return pts


def circle(r, n=16, cx=0.0, cy=0.0, a0=0.0):
    return [(cx + r * math.cos(a0 + 2 * math.pi * k / n), cy + r * math.sin(a0 + 2 * math.pi * k / n)) for k in range(n)]


def ellipse(a, b, n=16, cx=0.0, cy=0.0):
    return [(cx + a * math.cos(2 * math.pi * k / n), cy + b * math.sin(2 * math.pi * k / n)) for k in range(n)]


def superellipse(a, b, e=2.6, n=24, cx=0.0, cy=0.0):
    """|x/a|^e + |y/b|^e = 1: a boxy oval, good for fuselages"""
    pts = []
    for k in range(n):
        t = 2 * math.pi * k / n
        c, s = math.cos(t), math.sin(t)
        x = a * math.copysign(abs(c) ** (2 / e), c)
        y = b * math.copysign(abs(s) ** (2 / e), s)
        pts.append((cx + x, cy + y))
    return pts


def area2(poly):
    s = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % len(poly)]
        s += x0 * y1 - x1 * y0
    return s / 2


def ccw(poly):
    return poly if area2(poly) > 0 else list(reversed(poly))


def fillet(poly, radius, seg=3, radii=None):
    """round every corner of a polygon (counter-clockwise) with an arc of the given radius"""
    poly = ccw(poly)
    n = len(poly)
    out = []
    for i in range(n):
        p0 = np.array(poly[i - 1], dtype=float)
        p1 = np.array(poly[i], dtype=float)
        p2 = np.array(poly[(i + 1) % n], dtype=float)
        r = radius if radii is None else radii[i]
        d0, d1 = norm(p0 - p1), norm(p2 - p1)
        cosang = np.clip(np.dot(d0, d1), -1, 1)
        ang = math.acos(cosang)
        if r <= 1e-6 or ang > math.radians(178) or ang < 1e-3:
            out.append(tuple(p1))
            continue
        t = r / math.tan(ang / 2)
        t = min(t, 0.45 * np.linalg.norm(p0 - p1), 0.45 * np.linalg.norm(p2 - p1))
        r_eff = t * math.tan(ang / 2)
        a = p1 + d0 * t
        b = p1 + d1 * t
        bis = norm(d0 + d1)
        c = p1 + bis * (r_eff / math.sin(ang / 2))
        a0 = math.atan2(a[1] - c[1], a[0] - c[0])
        a1 = math.atan2(b[1] - c[1], b[0] - c[0])
        # sweep the short way round
        da = (a1 - a0 + math.pi) % (2 * math.pi) - math.pi
        for k in range(seg + 1):
            t2 = a0 + da * k / seg
            out.append((c[0] + r_eff * math.cos(t2), c[1] + r_eff * math.sin(t2)))
    return out


def offset(poly, d):
    """move every edge of a counter-clockwise polygon inwards by d (mitred corners)"""
    poly = ccw(poly)
    n = len(poly)
    out = []
    for i in range(n):
        p0 = np.array(poly[i - 1], dtype=float)
        p1 = np.array(poly[i], dtype=float)
        p2 = np.array(poly[(i + 1) % n], dtype=float)
        e0 = norm(p1 - p0)
        e1 = norm(p2 - p1)
        n0 = np.array([-e0[1], e0[0]])
        n1 = np.array([-e1[1], e1[0]])
        m = n0 + n1
        if np.linalg.norm(m) < 1e-9:
            m = n0
        m = norm(m)
        c = max(0.3, float(np.dot(m, n0)))
        q = p1 + m * (d / c)
        out.append((q[0], q[1]))
    return out


def triangulate(poly):
    """ear clipping of a simple counter-clockwise polygon; returns index triples"""
    idx = list(range(len(poly)))
    if area2(poly) < 0:
        idx.reverse()
    tris = []
    pts = [np.array(p, dtype=float) for p in poly]

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        found = False
        for k in range(len(idx)):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            a, b, c = pts[i0], pts[i1], pts[i2]
            if cross(a, b, c) <= 1e-12:
                continue
            ok = True
            for j in idx:
                if j in (i0, i1, i2):
                    continue
                p = pts[j]
                if cross(a, b, p) >= -1e-12 and cross(b, c, p) >= -1e-12 and cross(c, a, p) >= -1e-12:
                    ok = False
                    break
            if ok:
                tris.append((i0, i1, i2))
                idx.pop(k)
                found = True
                break
        if not found:
            break
    if len(idx) == 3:
        tris.append(tuple(idx))
    return tris


# ------------------------------------------------------------------------------------------------ placing 2D shapes
def plane_pts(poly, axis, at, flip=False):
    """2D (u, v) points to 3D on the plane axis = at. x: (z, y) | y: (x, z) | z: (x, y)"""
    out = []
    for u, v in poly:
        if axis == "x":
            out.append(v3(at, v, u))
        elif axis == "y":
            out.append(v3(u, at, v))
        else:
            out.append(v3(u, v, at))
    return out


def cap(m, ring3, mat, outward):
    """fill a planar ring (any shape) facing outward"""
    n = poly_normal(ring3)
    if np.dot(n, outward) < 0:
        ring3 = list(reversed(ring3))
        n = -n
    # 2D projection for triangulation
    a = np.argmax(np.abs(n))
    ax = [i for i in range(3) if i != a]
    p2 = [(q[ax[0]], q[ax[1]]) for q in ring3]
    convex = all(np.dot(poly_normal([ring3[i - 1], ring3[i], ring3[(i + 1) % len(ring3)]]), n) > -1e-6 for i in range(len(ring3)))
    if convex:
        # one polygon: the writer splits it into a fan of quads
        m.add(ring3, mat, [n] * len(ring3))
        return
    for i0, i1, i2 in triangulate(p2):
        tri = [ring3[i0], ring3[i1], ring3[i2]]
        if np.dot(poly_normal(tri), n) < 0:
            tri.reverse()
        m.add(tri, mat, [n] * 3)


def loft(rings, mat, caps=(True, True), closed=True, m=None, smooth=True):
    """skin a series of rings (lists of 3D points, same count, same order); faces point away from each ring's middle"""
    m = m or Mesh()
    n = len(rings[0])
    for i in range(len(rings) - 1):
        a, b = rings[i], rings[i + 1]
        ca, cb = sum(a) / n, sum(b) / n
        for j in range(n if closed else n - 1):
            j1 = (j + 1) % n
            quad = [a[j], a[j1], b[j1], b[j]]
            centre = (a[j] + a[j1] + b[j1] + b[j]) / 4
            mid = (ca + cb) / 2
            # outward: from the axis through the ring middles to the quad
            axis = norm(cb - ca) if np.linalg.norm(cb - ca) > 1e-9 else poly_normal(a)
            rel = centre - mid
            out = rel - axis * np.dot(rel, axis)
            if np.linalg.norm(out) < 1e-9:
                out = rel
            if np.linalg.norm(quad[0] - quad[3]) < 1e-9 and np.linalg.norm(quad[1] - quad[2]) < 1e-9:
                continue
            q = [quad[0], quad[1], quad[2], quad[3]]
            # drop collapsed corners (a ring shrunk to a point)
            uniq = []
            for p in q:
                if not uniq or np.linalg.norm(p - uniq[-1]) > 1e-9:
                    uniq.append(p)
            if len(uniq) > 1 and np.linalg.norm(uniq[0] - uniq[-1]) < 1e-9:
                uniq.pop()
            if len(uniq) < 3:
                continue
            m.add_oriented(uniq, mat, out, smooth=smooth)
    if closed:
        r0, r1 = rings[0], rings[-1]
        d = sum(r1) / n - sum(r0) / n
        if np.linalg.norm(d) < 1e-9:
            d = poly_normal(r1)
        if caps[0] and np.linalg.norm(r0[0] - r0[n // 2]) > 1e-9:
            cap(m, r0, mat, -d)
        if caps[1] and np.linalg.norm(r1[0] - r1[n // 2]) > 1e-9:
            cap(m, r1, mat, d)
    return m


def prism(poly, axis, a0, a1, mat, bevel=0.0, m=None):
    """extrude a 2D profile (see plane_pts) from a0 to a1 along an axis, the end edges chamfered by `bevel`"""
    m = m or Mesh()
    poly = ccw(poly)
    if bevel > 0:
        inner = offset(poly, bevel)
        rings = [plane_pts(inner, axis, a0), plane_pts(poly, axis, a0 + bevel), plane_pts(poly, axis, a1 - bevel), plane_pts(inner, axis, a1)]
    else:
        rings = [plane_pts(poly, axis, a0), plane_pts(poly, axis, a1)]
    return loft(rings, mat, m=m)


def rbox(c0, c1, r, mat, seg=2, m=None, faces="xXyYzZ"):
    """box with rounded edges and corners (radius r) and exact smooth normals"""
    m = m or Mesh()
    c0 = np.array(c0, dtype=float)
    c1 = np.array(c1, dtype=float)
    lo, hi = np.minimum(c0, c1), np.maximum(c0, c1)
    r = max(0.0, min(r, *((hi - lo) / 2 - 1e-4)))
    if r < 1e-4:
        return box(lo, hi, mat, m=m, faces=faces)
    ilo, ihi = lo + r, hi - r

    def vals(a):
        out = [lo[a] + r * k / seg for k in range(seg + 1)]
        out += [hi[a] - r * (seg - k) / seg for k in range(seg + 1)]
        return out

    def mapped(p):
        q = np.clip(p, ilo, ihi)
        d = p - q
        L = np.linalg.norm(d)
        return q + d / L * r, d / L

    for a in range(3):
        for s in (-1, 1):
            if "xyz"[a] not in faces and "XYZ"[a] not in faces:
                continue
            if s < 0 and "xyz"[a] not in faces or s > 0 and "XYZ"[a] not in faces:
                continue
            b, c = [i for i in range(3) if i != a]
            ub, uc = vals(b), vals(c)
            grid = {}
            for i, x in enumerate(ub):
                for j, y in enumerate(uc):
                    p = np.zeros(3)
                    p[a] = hi[a] if s > 0 else lo[a]
                    p[b], p[c] = x, y
                    grid[i, j] = mapped(p)
            out = np.zeros(3)
            out[a] = s
            for i in range(len(ub) - 1):
                for j in range(len(uc) - 1):
                    if abs(ub[i + 1] - ub[i]) < 1e-9 or abs(uc[j + 1] - uc[j]) < 1e-9:
                        continue
                    ks = [(i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)]
                    pts = [grid[k][0] for k in ks]
                    ns = [grid[k][1] for k in ks]
                    m.add_oriented(pts, mat, out, ns)
    return m


def box(c0, c1, mat, m=None, faces="xXyYzZ"):
    """plain box with sharp edges"""
    m = m or Mesh()
    lo = np.minimum(np.array(c0, dtype=float), np.array(c1, dtype=float))
    hi = np.maximum(np.array(c0, dtype=float), np.array(c1, dtype=float))
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    P = lambda x, y, z: v3(x, y, z)
    fs = {
        "x": ([P(x0, y0, z0), P(x0, y0, z1), P(x0, y1, z1), P(x0, y1, z0)], (-1, 0, 0)),
        "X": ([P(x1, y0, z0), P(x1, y1, z0), P(x1, y1, z1), P(x1, y0, z1)], (1, 0, 0)),
        "y": ([P(x0, y0, z0), P(x1, y0, z0), P(x1, y0, z1), P(x0, y0, z1)], (0, -1, 0)),
        "Y": ([P(x0, y1, z0), P(x0, y1, z1), P(x1, y1, z1), P(x1, y1, z0)], (0, 1, 0)),
        "z": ([P(x0, y0, z0), P(x0, y1, z0), P(x1, y1, z0), P(x1, y0, z0)], (0, 0, -1)),
        "Z": ([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], (0, 0, 1)),
    }
    for k in faces:
        pts, n = fs[k]
        m.add_oriented(pts, mat, np.array(n, dtype=float), [np.array(n, dtype=float)] * 4, smooth=False)
    return m


def cylinder(axis, c, r, a0, a1, mat, n=16, bevel=0.0, caps=(True, True), m=None, r1=None, end_mat=None):
    """cylinder (or frustum when r1 is given) along an axis; c = centre in the other two axes (in xyz order)"""
    m = m or Mesh()
    r1 = r if r1 is None else r1

    def ring(rad, at):
        pts = []
        for k in range(n):
            t = 2 * math.pi * k / n
            u, v = c[0] + rad * math.cos(t), c[1] + rad * math.sin(t)
            if axis == "x":
                pts.append(v3(at, u, v))
            elif axis == "y":
                pts.append(v3(u, at, v))
            else:
                pts.append(v3(u, v, at))
        return pts

    if bevel > 0:
        rings = [ring(r - bevel, a0), ring(r, a0 + bevel), ring(r1, a1 - bevel), ring(r1 - bevel, a1)]
    else:
        rings = [ring(r, a0), ring(r1, a1)]
    side = loft(rings, mat, caps=(False, False))
    m.extend(side)
    d = {"x": v3(1, 0, 0), "y": v3(0, 1, 0), "z": v3(0, 0, 1)}[axis] * (1 if a1 > a0 else -1)
    if caps[0]:
        cap(m, rings[0], end_mat or mat, -d)
    if caps[1]:
        cap(m, rings[-1], end_mat or mat, d)
    return m


def tube(points, r, mat, n=10, caps=(True, True), m=None, radii=None):
    """a round tube along a polyline (pipes, barrels with a taper via radii)"""
    m = m or Mesh()
    pts = [np.asarray(p, dtype=float) for p in points]
    rings = []
    up_hint = v3(0, 1, 0)
    for i, p in enumerate(pts):
        if i == 0:
            t = norm(pts[1] - pts[0])
        elif i == len(pts) - 1:
            t = norm(pts[-1] - pts[-2])
        else:
            t = norm(norm(pts[i + 1] - pts[i]) + norm(pts[i] - pts[i - 1]))
        a = up_hint if abs(np.dot(up_hint, t)) < 0.9 else v3(1, 0, 0)
        u = norm(np.cross(t, a))
        w = np.cross(t, u)
        rad = r if radii is None else radii[i]
        rings.append([p + (u * math.cos(2 * math.pi * k / n) + w * math.sin(2 * math.pi * k / n)) * rad for k in range(n)])
    return loft(rings, mat, caps=caps, m=m)


def sphere(c, rx, ry, rz, mat, n=16, rows=8, m=None, top_only=False):
    m = m or Mesh()
    rings = []
    r0 = 0 if not top_only else rows // 2
    for i in range(r0, rows + 1):
        phi = math.pi * i / rows - math.pi / 2
        y = math.sin(phi)
        rr = math.cos(phi)
        rings.append([v3(c[0] + rx * rr * math.cos(2 * math.pi * k / n), c[1] + ry * y, c[2] + rz * rr * math.sin(2 * math.pi * k / n))
                      for k in range(n)])
    # exact normals of an ellipsoid
    centre = np.array(c, dtype=float)
    for i in range(len(rings) - 1):
        a, b = rings[i], rings[i + 1]
        for j in range(n):
            j1 = (j + 1) % n
            quad = [a[j], a[j1], b[j1], b[j]]
            uniq = []
            for p in quad:
                if not uniq or np.linalg.norm(p - uniq[-1]) > 1e-9:
                    uniq.append(p)
            if len(uniq) > 1 and np.linalg.norm(uniq[0] - uniq[-1]) < 1e-9:
                uniq.pop()
            if len(uniq) < 3:
                continue
            ns = [norm((p - centre) / np.array([rx * rx, ry * ry, rz * rz])) for p in uniq]
            m.add_oriented(uniq, mat, sum(uniq) / len(uniq) - centre, ns)
    if top_only:
        cap(m, rings[0], mat, v3(0, -1, 0))
    return m


def torus(axis, c, at, R, r, mat, n=20, k=8, m=None):
    """a tyre: ring of radius R, tube radius r, around an axis through c (other two axes) at position `at`"""
    m = m or Mesh()
    rings = []
    for i in range(n + 1):
        t = 2 * math.pi * i / n
        cu, cv = math.cos(t), math.sin(t)
        ring = []
        for j in range(k):
            s = 2 * math.pi * j / k
            rad = R + r * math.cos(s)
            along = r * math.sin(s)
            u, v = c[0] + rad * cu, c[1] + rad * cv
            if axis == "x":
                ring.append(v3(at + along, u, v))
            elif axis == "y":
                ring.append(v3(u, at + along, v))
            else:
                ring.append(v3(u, v, at + along))
        rings.append(ring)
    for i in range(n):
        a, b = rings[i], rings[i + 1]
        t0 = 2 * math.pi * i / n
        for j in range(k):
            j1 = (j + 1) % k
            quad = [a[j], a[j1], b[j1], b[j]]
            centre_ring = []
            for q, t in ((a[j], t0), (a[j1], t0), (b[j1], t0 + 2 * math.pi / n), (b[j], t0 + 2 * math.pi / n)):
                cu, cv = math.cos(t), math.sin(t)
                if axis == "x":
                    cc = v3(at, c[0] + R * cu, c[1] + R * cv)
                elif axis == "y":
                    cc = v3(c[0] + R * cu, at, c[1] + R * cv)
                else:
                    cc = v3(c[0] + R * cu, c[1] + R * cv, at)
                centre_ring.append(norm(q - cc))
            out = sum(centre_ring)
            m.add_oriented(quad, mat, out, centre_ring)
    return m


def plate(corners, thick, mat, bevel=0.0, m=None):
    """a flat plate through 3+ coplanar corners (any order round the outline), `thick` thick, centred on the outline"""
    m = m or Mesh()
    pts = [np.asarray(p, dtype=float) for p in corners]
    nrm = poly_normal(pts)
    c = sum(pts) / len(pts)
    # local 2D frame
    u = norm(pts[1] - pts[0])
    w = np.cross(nrm, u)
    poly2 = [(np.dot(p - c, u), np.dot(p - c, w)) for p in pts]
    poly2 = ccw(poly2)

    def to3(p2, off):
        return c + u * p2[0] + w * p2[1] + nrm * off

    h = thick / 2
    if bevel > 0:
        inner = offset(poly2, bevel)
        rings = [[to3(p, -h) for p in inner], [to3(p, -h + bevel) for p in poly2], [to3(p, h - bevel) for p in poly2], [to3(p, h) for p in inner]]
    else:
        rings = [[to3(p, -h) for p in poly2], [to3(p, h) for p in poly2]]
    return loft(rings, mat, m=m)


def airfoil(chord, thick, n=14):
    """symmetric NACA-like section, leading edge at u = 0, trailing edge at u = chord (closed, counter-clockwise)"""
    top = []
    for i in range(n + 1):
        x = (1 - math.cos(math.pi * i / n)) / 2
        y = 5 * thick * (0.2969 * math.sqrt(x) - 0.126 * x - 0.3516 * x * x + 0.2843 * x ** 3 - 0.1036 * x ** 4)
        top.append((x * chord, y * chord))
    bottom = [(x, -y) for x, y in reversed(top[1:-1])]
    return top + bottom


def wing(root, tip, span_axis, side, root_le, tip_le, root_chord, tip_chord, thick, mat, dihedral=0.0, m=None, n=12):
    """a wing (fin, stabiliser, blade) from a root section to a tip section.
    root / tip: positions along span_axis; *_le: (a, b) leading edge positions in the two other axes (chord runs +b);
    side: +1 / -1 thickness direction sign (unused for symmetric)."""
    m = m or Mesh()
    axes = "xyz"
    sa = axes.index(span_axis)
    others = [i for i in range(3) if i != sa]
    rings = []
    for t, le, ch in ((root, root_le, root_chord), (tip, tip_le, tip_chord)):
        ring = []
        for u, v in airfoil(ch, thick, n):
            p = np.zeros(3)
            p[sa] = t
            # chord along the second other axis, thickness along the first
            p[others[1]] = le[1] + u
            p[others[0]] = le[0] + v
            ring.append(p)
        rings.append(ring)
    return loft(rings, mat, m=m)


# ------------------------------------------------------------------------------------------------ export
def _weld_key(p):
    return (round(p[0] * 512), round(p[1] * 512), round(p[2] * 512))


def smooth_normals(mesh):
    """per-corner normals: explicit ones are kept, the rest averaged over neighbouring faces within the smoothing angle"""
    cos_t = math.cos(math.radians(SMOOTH_DEG))
    fnorm = []
    acc = {}
    for fi, p in enumerate(mesh.polys):
        n = poly_normal(p["pts"])
        area = 0.0
        pts = p["pts"]
        for i in range(1, len(pts) - 1):
            area += np.linalg.norm(np.cross(pts[i] - pts[0], pts[i + 1] - pts[0])) / 2
        fnorm.append(n)
        if p["smooth"] and p["n"] is None:
            for q in pts:
                acc.setdefault(_weld_key(q), []).append((n, max(area, 1e-6), p["mat"]))
    out = []
    for fi, p in enumerate(mesh.polys):
        if p["n"] is not None:
            out.append(p["n"])
            continue
        n = fnorm[fi]
        if not p["smooth"]:
            out.append([n] * len(p["pts"]))
            continue
        ns = []
        for q in p["pts"]:
            s = np.zeros(3)
            for fn, a, mat in acc.get(_weld_key(q), []):
                if np.dot(fn, n) >= cos_t:
                    s += fn * a
            ns.append(norm(s) if np.linalg.norm(s) > 1e-9 else n)
        out.append(ns)
    return out


def _uvs(pts, n, seed):
    """planar mapping along the face's main axis, kept inside the texture without wrapping"""
    a = int(np.argmax(np.abs(n)))
    ax = [i for i in range(3) if i != a]
    us = [q[ax[0]] / UV_TILE for q in pts]
    vs = [-q[ax[1]] / UV_TILE for q in pts]
    u0, v0 = min(us), min(vs)
    du, dv = max(us) - u0, max(vs) - v0
    sc = 1.0 / max(1.0, du, dv)
    ou = (seed * 0.6180339) % 1.0 * max(0.0, 1 - du * sc)
    ov = (seed * 0.4142135) % 1.0 * max(0.0, 1 - dv * sc)
    return [((u - u0) * sc + ou, (v - v0) * sc + ov) for u, v in zip(us, vs)]


def write_part(mesh, folder, name, res_dir):
    """write <name>.obj, <name>.mtl and <name>.json into models/<folder>"""
    out_dir = os.path.join(res_dir, "models", folder)
    os.makedirs(out_dir, exist_ok=True)
    normals = smooth_normals(mesh)
    by_mat = {}
    for p, ns in zip(mesh.polys, normals):
        by_mat.setdefault(p["mat"], []).append((p, ns))
    head = [f"# {folder}/{name}: generated by tools/meshkit.py", f"mtllib {name}.mtl"]
    lines = []
    vs, vts, vns = {}, {}, {}

    def ref(table, key):
        if key not in table:
            table[key] = len(table) + 1
        return table[key]
    used_tex = set()
    seed = 0
    for mat in sorted(by_mat):
        if mat not in MATS:
            raise KeyError(f"unknown material {mat} in {folder}/{name}")
        lines.append(f"usemtl {mat}")
        used_tex.add(MATS[mat][1])
        for p, ns in by_mat[mat]:
            pts = p["pts"]
            seed += 1
            fn = poly_normal(pts)
            uv = p.get("uv") or _uvs(pts, fn, seed)
            # faces of 3 or 4 corners only: fan out anything bigger
            polys = []
            if len(pts) <= 4:
                polys.append(list(range(len(pts))))
            else:
                for i in range(1, len(pts) - 1, 2):
                    if i + 2 <= len(pts) - 1:
                        polys.append([0, i, i + 1, i + 2])
                    else:
                        polys.append([0, i, i + 1])
            for idx in polys:
                refs = []
                for k in idx:
                    q = pts[k] / 16.0
                    nn = ns[k]
                    a = ref(vs, f"{q[0]:.5f} {q[1]:.5f} {q[2]:.5f}")
                    b = ref(vts, f"{uv[k][0]:.4f} {uv[k][1]:.4f}")
                    c = ref(vns, f"{nn[0]:.3f} {nn[1]:.3f} {nn[2]:.3f}")
                    refs.append(f"{a}/{b}/{c}")
                lines.append("f " + " ".join(refs))
    body = head + ["v " + k for k in vs] + ["vt " + k for k in vts] + ["vn " + k for k in vns] + lines
    with open(os.path.join(out_dir, name + ".obj"), "w") as fh:
        fh.write("\n".join(body) + "\n")
    mtl = []
    for mat in sorted(by_mat):
        rgb, tex, tint = MATS[mat]
        # colour from the material's own texture; Ka 1 = full bright (lamps) with emissive_ambient
        ka = "1 1 1" if mat in EMISSIVE else "0 0 0"
        mtl += [f"newmtl {mat}", "Kd 1 1 1", f"Ka {ka}", "d 1.0", f"map_Kd #{mat}"]
        if tint:
            mtl.append("neoforge_TintIndex 0")
        mtl.append("")
    with open(os.path.join(out_dir, name + ".mtl"), "w") as fh:
        fh.write("\n".join(mtl))
    textures = {t: TEX_REF + t for t in sorted(by_mat)}
    textures["particle"] = TEX_REF + "paint"
    model = {"loader": "neoforge:obj", "model": f"realweather:models/{folder}/{name}.obj", "flip_v": False, "automatic_culling": False,
             "emissive_ambient": True, "shade_quads": True, "textures": textures}
    import json
    with open(os.path.join(out_dir, name + ".json"), "w") as fh:
        json.dump(model, fh, indent=1)
    return len(vs)


# ------------------------------------------------------------------------------------------------ textures
def _dial(size, top, step, label_every, red_from, unit):
    """an instrument dial: dark face, white ticks over a 240 degree sweep, numbers, red zone"""
    from PIL import ImageDraw
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = size / 2
    d.ellipse([2, 2, size - 3, size - 3], fill=(18, 20, 24, 255), outline=(150, 156, 165, 255), width=3)
    d.ellipse([8, 8, size - 9, size - 9], outline=(40, 44, 50, 255), width=2)
    n = int(top / step)
    for i in range(n + 1):
        v = i * step
        ang = math.radians(210 - 240 * v / top)
        major = (v % label_every) == 0
        r0 = c - (20 if major else 14)
        r1 = c - 9
        col = (235, 70, 60, 255) if v >= red_from else (235, 238, 242, 255)
        d.line([(c + r0 * math.cos(ang), c - r0 * math.sin(ang)), (c + r1 * math.cos(ang), c - r1 * math.sin(ang))],
               fill=col, width=3 if major else 1)
        if major:
            r2 = c - 32
            txt = str(int(v if unit != "rpm" else v // 1000))
            tx, ty = c + r2 * math.cos(ang), c - r2 * math.sin(ang)
            w = d.textlength(txt)
            d.text((tx - w / 2, ty - 5), txt, fill=col)
    w = d.textlength(unit)
    d.text((c - w / 2, c + size * 0.18), unit, fill=(170, 176, 186, 255))
    return np.asarray(im, dtype=float) / 255


def make_textures(res_dir, size=128):
    """one texture per material: the family pattern times the material colour (all original, procedural)"""
    out = os.path.join(res_dir, "textures", "item", "vehicle")
    os.makedirs(out, exist_ok=True)
    rng = np.random.default_rng(7)

    def noise(scale, octaves=4):
        acc = np.zeros((size, size))
        amp = 1.0
        tot = 0
        for o in range(octaves):
            s = max(2, int(scale * 2 ** o))
            g = rng.random((s + 1, s + 1))
            g[-1, :] = g[0, :]
            g[:, -1] = g[:, 0]
            img = Image.fromarray((g * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC)
            acc += np.asarray(img, dtype=float) / 255 * amp
            tot += amp
            amp *= 0.5
        return acc / tot

    yy, xx = np.mgrid[0:size, 0:size]
    fam = {}
    alpha = {}
    # Glossy paint: very even, a soft sky reflection band across it.
    paint = 0.93 + (noise(3) - 0.5) * 0.05 + (noise(48, 1) - 0.5) * 0.025
    paint += 0.05 * np.exp(-((yy / size - 0.3) * 6) ** 2)
    fam["paint"] = paint
    metal = 0.84 + (noise(3) - 0.5) * 0.12
    streak = np.asarray(Image.fromarray((rng.random((size, 8)) * 255).astype(np.uint8)).resize((size, size), Image.BILINEAR), dtype=float) / 255
    fam["metal"] = metal + (streak - 0.5) * 0.08
    rubber = 0.8 + (noise(16, 3) - 0.5) * 0.12
    rubber -= 0.12 * ((yy // 6) % 2)
    fam["rubber"] = rubber
    fam["glass"] = 0.8 + 0.2 * np.clip(1 - np.abs((xx + yy) / size - 0.8) * 3, 0, 1)
    alpha["glass"] = 0.32 + 0.22 * np.clip(1 - np.abs((xx + yy) / size - 0.8) * 3, 0, 1)
    fam["plastic"] = 0.86 + (noise(24, 2) - 0.5) * 0.1
    weave = 0.82 + 0.06 * ((xx // 2 + yy // 2) % 2) + (noise(8) - 0.5) * 0.08
    fam["fabric"] = weave
    fam["plain"] = np.ones((size, size)) * 0.97
    snow = 0.9 + (noise(12, 3) - 0.5) * 0.18
    fam["snow"] = snow
    alpha["snow"] = np.clip(0.75 + (noise(20, 2) - 0.5) * 0.9, 0, 1)
    for f in os.listdir(out):
        if f.endswith(".png"):
            os.remove(os.path.join(out, f))
    for mat, (rgb, tex, tint) in MATS.items():
        if tex == "gauge":
            continue
        base = fam[tex]
        if mat in EMISSIVE:
            base = np.ones_like(base)
        a = np.ones_like(base) if tex not in alpha else alpha[tex]
        img = np.stack([base * rgb[0], base * rgb[1], base * rgb[2], a], axis=-1)
        Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(out, mat + ".png"))
    Image.fromarray((_dial(128, 200, 10, 20, 180, "km/h") * 255).astype(np.uint8), "RGBA").save(os.path.join(out, "gauge.png"))
    Image.fromarray((_dial(128, 8000, 500, 1000, 6500, "rpm") * 255).astype(np.uint8), "RGBA").save(os.path.join(out, "gauge_rpm.png"))


# ------------------------------------------------------------------------------------------------ preview
LIGHT0 = norm(v3(0.2, 1.0, -0.7))
LIGHT1 = norm(v3(-0.2, 1.0, 0.7))


def render(meshes, yaw=30, pitch=20, size=(640, 400), tints=None, bg=(52, 58, 66), zoom=1.0):
    """software render of meshes (list of (mesh, tint rgb or None)) with Minecraft-like entity lighting"""
    W, H = size
    tris = []
    for mesh, tint in meshes:
        ns_all = smooth_normals(mesh)
        for p, ns in zip(mesh.polys, ns_all):
            rgb = np.array(MATS[p["mat"]][0])
            if MATS[p["mat"]][2] and tint is not None:
                rgb = rgb * np.array(tint)
            pts = p["pts"]
            for i in range(1, len(pts) - 1):
                tris.append((pts[0], pts[i], pts[i + 1], ns[0], ns[i], ns[i + 1], rgb))
    if not tris:
        return Image.new("RGB", size, bg)
    R = rot_matrix("x", pitch) @ rot_matrix("y", yaw)
    allp = np.array([q for t in tris for q in t[:3]])
    c = (allp.min(0) + allp.max(0)) / 2
    ext = np.linalg.norm(allp.max(0) - allp.min(0))
    sc = min(W, H) / ext * 0.95 * zoom
    img = np.zeros((H, W, 3))
    img[:] = np.array(bg) / 255
    zb = np.full((H, W), -1e9)
    for a, b, cc, na, nb, nc, rgb in tris:
        P = [(R @ (q - c)) * sc for q in (a, b, cc)]
        xs = [W / 2 + p[0] for p in P]
        ys = [H / 2 - p[1] for p in P]
        zs = [p[2] for p in P]            # towards the viewer (+z) = larger
        # back faces: Minecraft culls them
        area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0])
        if area >= 0:
            continue
        x0, x1 = int(max(0, min(xs))), int(min(W - 1, max(xs) + 1))
        y0, y1 = int(max(0, min(ys))), int(min(H - 1, max(ys) + 1))
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        d = area
        w0 = ((xs[1] - gx) * (ys[2] - gy) - (xs[2] - gx) * (ys[1] - gy)) / d
        w1 = ((xs[2] - gx) * (ys[0] - gy) - (xs[0] - gx) * (ys[2] - gy)) / d
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        upd = inside & (z > sub)
        if not upd.any():
            continue
        n = (w0[..., None] * na + w1[..., None] * nb + w2[..., None] * nc)
        n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-9
        lit = 0.4 + 0.6 * (np.clip(n @ LIGHT0, 0, 1) + np.clip(n @ LIGHT1, 0, 1)) * 0.6
        col = np.clip(lit[..., None] * rgb, 0, 1)
        sub[upd] = z[upd]
        img[y0:y1 + 1, x0:x1 + 1][upd] = col[upd]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def sheet(images, cols=3, label=None):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (w * cols, h * rows), (30, 30, 30))
    for i, im in enumerate(images):
        out.paste(im, ((i % cols) * w, (i // cols) * h))
    return out
