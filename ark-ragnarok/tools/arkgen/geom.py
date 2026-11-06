"""Model geometry in Minecraft ModelPart terms.

Coordinates are model pixels, Minecraft model space: +x = model left, +y = DOWN, -z = forward (head).
Ground is at y = 24.  A Part has a pivot relative to its parent pivot (in the parent's rotated frame)
and a rotation applied as Rz*Ry*Rx (ModelPart.translateAndRotate).  Cubes are given by their min
corner relative to the pivot and integer sizes, exactly like CubeListBuilder.addBox.
"""
import math
import numpy as np

GROUND = 24.0


class Cube:
    __slots__ = ("x", "y", "z", "w", "h", "d", "mat", "inflate", "u", "v", "decals", "tone")

    def __init__(self, x, y, z, w, h, d, mat, inflate=0.0, decals=None, tone=0.0):
        self.x, self.y, self.z = float(x), float(y), float(z)
        self.w, self.h, self.d = max(1, int(round(w))), max(1, int(round(h))), max(1, int(round(d)))
        self.mat = mat
        self.inflate = inflate
        self.u = self.v = 0
        self.decals = decals or []
        self.tone = tone

    def footprint(self):
        return 2 * (self.w + self.d), self.d + self.h


class Part:
    def __init__(self, name, parent, pivot, rot=(0.0, 0.0, 0.0), role=None):
        self.name = name
        self.parent = parent
        self.pivot = [float(v) for v in pivot]
        self.rot = [float(v) for v in rot]
        self.cubes = []
        self.children = []
        self.role = role  # (ROLE, args...)
        if parent is not None:
            parent.children.append(self)

    def box(self, x, y, z, w, h, d, mat="skin", inflate=0.0, decals=None, tone=0.0):
        c = Cube(x, y, z, w, h, d, mat, inflate, decals, tone)
        self.cubes.append(c)
        return c

    def cbox(self, cx, cy, cz, w, h, d, mat="skin", inflate=0.0, decals=None, tone=0.0):
        """box given by its centre"""
        w, h, d = max(1, int(round(w))), max(1, int(round(h))), max(1, int(round(d)))
        return self.box(cx - w / 2.0, cy - h / 2.0, cz - d / 2.0, w, h, d, mat, inflate, decals, tone)


class Model:
    def __init__(self, name):
        self.name = name
        self.parts = {}
        self.root = Part("root", None, (0, 0, 0))
        self.order = []
        self.tex_w = self.tex_h = 0

    def part(self, name, parent, pivot, rot=(0, 0, 0), role=None):
        if name in self.parts:
            raise ValueError("duplicate part " + name + " in " + self.name)
        p = Part(name, self.parts[parent] if isinstance(parent, str) else (parent or self.root), pivot, rot, role)
        self.parts[name] = p
        self.order.append(p)
        return p

    # ------------------------------------------------------------------ matrices
    @staticmethod
    def rot_matrix(rx, ry, rz):
        cx, sx = math.cos(rx), math.sin(rx)
        cy, sy = math.cos(ry), math.sin(ry)
        cz, sz = math.cos(rz), math.sin(rz)
        Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
        Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
        return Rz @ Ry @ Rx

    def world_matrices(self, pose=None):
        """4x4 matrix per part (model pixels).  pose: name -> (drx, dry, drz) added to the rest rotation."""
        out = {}

        def walk(p, M):
            for c in p.children:
                r = list(c.rot)
                if pose and c.name in pose:
                    d = pose[c.name]
                    r = [r[0] + d[0], r[1] + d[1], r[2] + d[2]]
                T = np.eye(4)
                T[:3, 3] = c.pivot
                R = np.eye(4)
                R[:3, :3] = self.rot_matrix(*r)
                W = M @ T @ R
                out[c.name] = W
                walk(c, W)

        walk(self.root, np.eye(4))
        return out

    # ------------------------------------------------------------------ uv packing
    def pack(self, max_w=512):
        cubes = [c for p in self.order for c in p.cubes]
        cubes_sorted = sorted(cubes, key=lambda c: (-c.footprint()[1], -c.footprint()[0]))
        total = sum(c.footprint()[0] * c.footprint()[1] for c in cubes)
        widest = max(c.footprint()[0] for c in cubes)
        for tw in (64, 128, 256, 512, 1024):
            if tw < widest or tw > max(max_w, widest):
                continue
            if tw * tw * 0.55 < total and tw < max(max_w, widest):
                continue
            x = y = row_h = 0
            ok = True
            for c in cubes_sorted:
                fw, fh = c.footprint()
                if x + fw > tw:
                    x = 0
                    y += row_h
                    row_h = 0
                c.u, c.v = x, y
                x += fw
                row_h = max(row_h, fh)
            th = y + row_h
            if th <= tw * 2 or tw >= max_w:
                self.tex_w = tw
                self.tex_h = int(math.ceil(th / 16.0) * 16)
                return
        raise RuntimeError("cannot pack " + self.name)

    def cube_count(self):
        return sum(len(p.cubes) for p in self.order)

    # ------------------------------------------------------------------ export
    def export(self):
        lines = ["TEX %d %d" % (self.tex_w, self.tex_h)]
        for p in self.order:
            par = p.parent.name if p.parent is not None and p.parent.name != "root" else "root"
            lines.append("P %s %s %s %s %s %s %s %s" % (p.name, par, f(p.pivot[0]), f(p.pivot[1]), f(p.pivot[2]),
                                                       f4(p.rot[0]), f4(p.rot[1]), f4(p.rot[2])))
            for c in p.cubes:
                lines.append("B %s %s %s %d %d %d %d %d %s" % (f(c.x), f(c.y), f(c.z), c.w, c.h, c.d, c.u, c.v, f(c.inflate)))
            if p.role:
                lines.append("R " + " ".join(str(a) for a in p.role))
        return "\n".join(lines) + "\n"


def f(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def f4(v):
    s = ("%.4f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---------------------------------------------------------------------- faces
# For every face: function giving the model-space point (relative to the cube min corner, before inflate)
# of the texel (s, t) where s,t in [0,1] run along the face's U and V in texture space.
# Derived from ModelPart.Cube / Polygon.remap (vertex0 = (u2,v1), vertex1 = (u1,v1), vertex2 = (u1,v2), vertex3 = (u2,v2)).

def face_regions(c):
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    return {
        # name: (u0, v0, uw, vh)
        "top": (u + d, v, w, d),          # model y = min (visual top)
        "bottom": (u + d + w, v, w, d),   # model y = max
        "west": (u, v + d, d, h),         # x = min
        "north": (u + d, v + d, w, h),    # z = min (front)
        "east": (u + d + w, v + d, d, h),  # x = max
        "south": (u + 2 * d + w, v + d, w, h),  # z = max (back)
    }


def face_point(c, face, s, t):
    """s,t arrays in [0,1] -> (x,y,z) arrays in cube-local model space (relative to pivot)."""
    x0, y0, z0 = c.x - c.inflate, c.y - c.inflate, c.z - c.inflate
    x1, y1, z1 = c.x + c.w + c.inflate, c.y + c.h + c.inflate, c.z + c.d + c.inflate
    if face == "top":
        return x0 + s * (x1 - x0), np.full_like(s, y0), z1 - t * (z1 - z0)
    if face == "bottom":
        return x0 + s * (x1 - x0), np.full_like(s, y1), z1 - t * (z1 - z0)
    if face == "west":
        return np.full_like(s, x0), y0 + t * (y1 - y0), z1 - s * (z1 - z0)
    if face == "north":
        return x0 + s * (x1 - x0), y0 + t * (y1 - y0), np.full_like(s, z0)
    if face == "east":
        return np.full_like(s, x1), y0 + t * (y1 - y0), z0 + s * (z1 - z0)
    if face == "south":
        return x1 - s * (x1 - x0), y0 + t * (y1 - y0), np.full_like(s, z1)
    raise ValueError(face)


FACE_NORMAL = {"top": (0, -1, 0), "bottom": (0, 1, 0), "west": (-1, 0, 0), "north": (0, 0, -1),
               "east": (1, 0, 0), "south": (0, 0, 1)}
