"""Tiny voxel modelling library for the Brainrot Heist models (blocky "brainrot" style).

A model is a set of voxels (1 voxel = 1/12 block by default), each with a colour slot and a part. Parts can be animated in
game (legL/legR/armL/armR swing while walking, tail/wingL/wingR/sway/head...). Colour slots are named; slots starting
with "k_" keep their colour when a mutation recolours the brainrot (eyes, teeth...), slots starting with "g_" glow.
The mesher merges equal faces into big quads whose texture coordinates are in voxel units, so a repeating
16x16 texture draws the voxel grid.
"""
import math

DIRS = [((1, 0, 0), 0.86), ((-1, 0, 0), 0.86), ((0, 1, 0), 1.0), ((0, -1, 0), 0.72), ((0, 0, 1), 0.93), ((0, 0, -1), 0.93)]


class Vox:
    def __init__(self, name, pal, vs=1 / 12):
        self.name = name
        self.pal = dict(pal)
        self.vs = vs
        self.vox = {}
        self.pivots = {'body': (0, 0, 0)}
        self.phase = {}
        self.floats = False

    # ---------------------------------------------------------------- placing voxels
    def set(self, x, y, z, slot, part='body', sym=False):
        self.vox[(x, y, z)] = (slot, part)
        if sym: self.vox[(-1 - x, y, z)] = (slot, part)

    def rm(self, x, y, z, sym=False):
        self.vox.pop((x, y, z), None)
        if sym: self.vox.pop((-1 - x, y, z), None)

    def box(self, slot, x0, x1, y0, y1, z0, z1, part='body', sym=False):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, slot, part, sym)

    def ell(self, slot, cx, cy, cz, rx, ry, rz, part='body', sym=False, hollow=False, where=None):
        """Ellipsoid centred on (cx, cy, cz) in voxel space (use .5 centres for even sizes)."""
        for x in range(int(math.floor(cx - rx)) - 1, int(math.ceil(cx + rx)) + 1):
            for y in range(int(math.floor(cy - ry)) - 1, int(math.ceil(cy + ry)) + 1):
                for z in range(int(math.floor(cz - rz)) - 1, int(math.ceil(cz + rz)) + 1):
                    d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 + ((z + 0.5 - cz) / rz) ** 2
                    if d <= 1 and (not hollow or d > 0.6) and (where is None or where(x, y, z)):
                        self.set(x, y, z, slot, part, sym)

    def cyl(self, slot, cx, cz, rx, y0, y1, part='body', rz=None, sym=False, where=None):
        """Vertical (elliptic) cylinder."""
        rz = rz or rx
        for y in range(y0, y1 + 1):
            for x in range(int(math.floor(cx - rx)) - 1, int(math.ceil(cx + rx)) + 1):
                for z in range(int(math.floor(cz - rz)) - 1, int(math.ceil(cz + rz)) + 1):
                    if ((x + 0.5 - cx) / rx) ** 2 + ((z + 0.5 - cz) / rz) ** 2 <= 1 and (where is None or where(x, y, z)):
                        self.set(x, y, z, slot, part, sym)

    def cone(self, slot, cx, cz, r0, r1, y0, y1, part='body'):
        for y in range(y0, y1 + 1):
            t = (y - y0) / max(1, y1 - y0)
            r = r0 + (r1 - r0) * t
            if r <= 0.35:
                self.set(int(math.floor(cx)), y, int(math.floor(cz)), slot, part)
                continue
            for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 1):
                for z in range(int(math.floor(cz - r)) - 1, int(math.ceil(cz + r)) + 1):
                    if (x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2 <= r * r:
                        self.set(x, y, z, slot, part)

    def tube(self, slot, pts, r, part='body', sym=False):
        """Spheres of radius r along a poly line of (x, y, z) points."""
        for (a, b) in zip(pts, pts[1:]):
            n = max(1, int(math.dist(a, b) * 2))
            for k in range(n + 1):
                t = k / n
                c = [a[i] + (b[i] - a[i]) * t for i in range(3)]
                self.ell(slot, c[0], c[1], c[2], r, r, r, part, sym)

    def paint(self, slot, pred, part=None, src=None):
        """Recolours voxels where pred(x, y, z) is true (only voxels of colour `src`, a name or a tuple, when given)."""
        if isinstance(src, str): src = (src,)
        for k, (s, p) in list(self.vox.items()):
            if (part is None or p == part) and (src is None or s in src) and pred(*k):
                self.vox[k] = (slot, p)

    def front(self, slot, cells, depth=1, part=None, out=0):
        """Paints the front-most voxel of each (x, y) column (the face), optionally pushing it out by `out` voxels."""
        for (x, y) in cells:
            zs = [k[2] for k, (s, p) in self.vox.items() if k[0] == x and k[1] == y and (part is None or p == part)]
            if not zs: continue
            z = max(zs)
            p = self.vox[(x, y, z)][1]
            for d in range(depth):
                if (x, y, z - d) in self.vox: self.vox[(x, y, z - d)] = (slot, p)
            for d in range(1, out + 1): self.vox[(x, y, z + d)] = (slot, p)

    def pivot(self, part, x, y, z, phase=0.0):
        self.pivots[part] = (x, y, z)
        self.phase[part] = phase

    # ---------------------------------------------------------------- faces
    def eyes(self, y, sep, size=2, part=None, pupil='k_eye', white='k_white', out=1, look=0):
        """Two square eyes on the front surface: a white square with a black pupil (`look` shifts the pupils sideways)."""
        for side in (1, -1):
            cx = sep if side > 0 else -1 - sep
            cells = [(cx + dx * side, y + dy) for dx in range(-(size // 2), size - size // 2) for dy in range(size)]
            self.front(white, cells, part=part, out=out)
            px = cx + (look if side > 0 else -look)
            self.front(pupil, [(px, y + size // 2 - (1 if size > 2 else 0)), (px, y + size // 2 - (1 if size > 2 else 0) + (1 if size > 2 else 0))] if size > 2 else [(px, y)], part=part, out=out)

    def smile(self, y, half, part=None, slot='k_dark', out=0):
        cells = [(x, y) for x in range(-half, half)] + [(-half - 1, y + 1), (half, y + 1)]
        self.front(slot, cells, part=part, out=out)

    def mouth_open(self, y, half, h=2, part=None, teeth=True):
        cells = [(x, y + d) for x in range(-half, half) for d in range(h)]
        self.front('k_dark', cells, part=part, depth=1)
        if teeth: self.front('k_white', [(x, y + h - 1) for x in range(-half, half) if (x & 1) == 0], part=part)

    # ---------------------------------------------------------------- meshing + output
    def mesh(self):
        """Greedy meshing per part, colour slot, direction and layer. Returns {(slot, part): [(verts4, normal, shade, uvs4)]}."""
        out = {}
        for (dx, dy, dz), shade in DIRS:
            layers = {}
            for (x, y, z), (slot, part) in self.vox.items():
                n = self.vox.get((x + dx, y + dy, z + dz))
                if n is not None and n[1] == part: continue
                if dx: key, uv = x, (z, y)
                elif dy: key, uv = y, (x, z)
                else: key, uv = z, (x, y)
                layers.setdefault((slot, part, key), set()).add(uv)
            for (slot, part, layer), cells in layers.items():
                cells = set(cells)
                while cells:
                    u0, v0 = min(cells, key=lambda c: (c[1], c[0]))
                    u1 = u0
                    while (u1 + 1, v0) in cells: u1 += 1
                    v1 = v0
                    while all((u, v1 + 1) in cells for u in range(u0, u1 + 1)): v1 += 1
                    for u in range(u0, u1 + 1):
                        for v in range(v0, v1 + 1): cells.discard((u, v))
                    out.setdefault((slot, part), []).append(self._quad((dx, dy, dz), layer, u0, v0, u1 + 1, v1 + 1, shade))
        return out

    def _quad(self, d, layer, u0, v0, u1, v1, shade):
        dx, dy, dz = d
        plane = layer + 1 if (dx + dy + dz) > 0 else layer
        if dx:
            pts = [(plane, v0, u0), (plane, v0, u1), (plane, v1, u1), (plane, v1, u0)]
            uvs = [(u0, -v0), (u1, -v0), (u1, -v1), (u0, -v1)]
        elif dy:
            pts = [(u0, plane, v0), (u1, plane, v0), (u1, plane, v1), (u0, plane, v1)]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        else:
            pts = [(u0, v0, plane), (u1, v0, plane), (u1, v1, plane), (u0, v1, plane)]
            uvs = [(u0, -v0), (u1, -v0), (u1, -v1), (u0, -v1)]
        return pts, d, shade, uvs

    def bounds(self):
        xs = [k[0] for k in self.vox]; ys = [k[1] for k in self.vox]; zs = [k[2] for k in self.vox]
        return (min(xs), max(xs) + 1, min(ys), max(ys) + 1, min(zs), max(zs) + 1)

    def height(self):
        b = self.bounds()
        return (b[3] - b[2]) * self.vs

    def write(self, path):
        vs = self.vs
        lines = ['# voxel model ' + self.name + ' (tools/gen_brainrots.py)']
        if self.floats: lines.append('# float')
        for k, v in self.pal.items(): lines.append(f'# pal {k} {v:06x}')
        quads = self.mesh()
        V, T, N, F = [], [], [], []
        groups = []
        for (slot, part), qs in sorted(quads.items()):
            faces = []
            for pts, d, shade, uvs in qs:
                idx = []
                for p, uv in zip(pts, uvs):
                    V.append((p[0] * vs, p[1] * vs, p[2] * vs, shade))
                    T.append(uv)
                    N.append(d)
                    idx.append(len(V))
                faces.append(idx)
            groups.append((slot, part, faces))
        for x, y, z, s in V: lines.append(f'v {x:.4f} {y:.4f} {z:.4f} {s:.2f}')
        for u, v in T: lines.append(f'vt {u} {v}')
        for n in N: lines.append(f'vn {n[0]} {n[1]} {n[2]}')
        for slot, part, faces in groups:
            if part == 'body': lines.append(f'g {slot}')
            else:
                px, py, pz = self.pivots.get(part, (0, 0, 0))
                anim = part.rstrip('0123456789')
                lines.append(f'g {slot} {anim} {px * vs:.4f} {py * vs:.4f} {pz * vs:.4f} {self.phase.get(part, 0):.2f}')
            for f in faces: lines.append('f ' + ' '.join(f'{i}/{i}/{i}' for i in f))
        open(path, 'w').write('\n'.join(lines) + '\n')
        return sum(len(q) for q in quads.values())
