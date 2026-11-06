"""Signed-distance sculpting kit for hero creatures.

Space: GeckoLib model space in pixels (16 px = 1 block): +y up, ground at y = 0, the creature faces -z.
A Sculpt is an ordered list of primitives.  Each primitive belongs to a mesh group, is added with a smooth
union (or carved with a smooth subtraction), carries a material id and a bone binding (one bone, or a blend
of bones for primitives that sit across a joint, e.g. tail sub-segments).
"""
import math
import numpy as np
from skimage import measure
import pyfqmr


def normalize(v):
    v = np.asarray(v, dtype=np.float64)
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def frame_from(axis, up=(0, 1, 0)):
    w = normalize(axis)
    up = np.asarray(up, dtype=np.float64)
    if abs(np.dot(w, normalize(up))) > 0.97:
        up = np.array([1.0, 0, 0]) if abs(w[0]) < 0.9 else np.array([0, 0, 1.0])
    u = normalize(np.cross(up, w))
    v = np.cross(w, u)
    return np.stack([u, v, w], axis=1)  # columns u, v, w


def rot_euler(rx=0.0, ry=0.0, rz=0.0):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


# ------------------------------------------------------------------------------------------ primitives
class Prim:
    def __init__(self, kind, group, mat, bones, op="add", k=1.5, **geo):
        self.kind = kind
        self.group = group
        self.mat = mat
        self.bones = bones if isinstance(bones, list) else [(bones, 1.0)]
        self.op = op
        self.k = k
        self.geo = geo
        self.weight_scale = geo.pop("weight_scale", 1.0)
        self.bbox = self._bbox()

    def _bbox(self):
        g = self.geo
        if self.kind == "ellipsoid":
            r = float(np.max(g["r"]))
            return g["c"] - r, g["c"] + r
        if self.kind == "roundcone":
            r = max(g["ra"] * max(g["sq"]), g["rb"] * max(g["sq"]))
            lo = np.minimum(g["a"], g["b"]) - r
            hi = np.maximum(g["a"], g["b"]) + r
            return lo, hi
        if self.kind == "roundbox":
            r = float(np.linalg.norm(g["h"]))
            return g["c"] - r, g["c"] + r
        if self.kind == "func":
            return np.asarray(g["lo"], float), np.asarray(g["hi"], float)
        raise ValueError(self.kind)

    def sdf(self, P):
        if self.kind == "func":
            return self.geo["fn"](P)
        g = self.geo
        if self.kind == "ellipsoid":
            q = (P - g["c"]) @ g["R"]
            r = g["r"]
            k0 = np.linalg.norm(q / r, axis=-1)
            k1 = np.linalg.norm(q / (r * r), axis=-1)
            return k0 * (k0 - 1.0) / np.maximum(k1, 1e-9)
        if self.kind == "roundcone":
            F = g["F"]
            q = (P - g["a"]) @ F
            sq = g["sq"]
            q = q / np.array([sq[0], sq[1], 1.0])
            L = g["L"]
            d = _round_cone_axis(q, L, g["ra"], g["rb"])
            return d * min(sq)
        if self.kind == "roundbox":
            q = np.abs((P - g["c"]) @ g["R"]) - g["h"] + g["rr"]
            return np.linalg.norm(np.maximum(q, 0), axis=-1) + np.minimum(q.max(axis=-1), 0) - g["rr"]
        raise ValueError(self.kind)


def _round_cone_axis(q, L, r1, r2):
    """round cone along +w from 0 to L (IQ), q in frame coords (u, v, w)"""
    x = np.sqrt(q[..., 0] ** 2 + q[..., 1] ** 2)
    y = q[..., 2]
    b = (r1 - r2) / max(L, 1e-9)
    b = np.clip(b, -0.999, 0.999)
    a = math.sqrt(1 - b * b)
    k = -x * b + y * a  # dot((x,y), (-b, a))
    d1 = np.sqrt(x * x + y * y) - r1
    d2 = np.sqrt(x * x + (y - L) ** 2) - r2
    d3 = x * a + y * b - r1
    return np.where(k < 0, d1, np.where(k > a * L, d2, d3))


class Sculpt:
    def __init__(self):
        self.prims = []
        self.bones = {}      # name -> (parent, pivot)
        self.bone_order = []

    # ---------------- skeleton
    def bone(self, name, parent, pivot):
        self.bones[name] = (parent, np.asarray(pivot, dtype=np.float64))
        self.bone_order.append(name)

    # ---------------- primitive helpers
    def ell(self, c, r, mat="skin", bone="body", group="body", op="add", k=1.5, R=None, rot=None, **kw):
        if R is None:
            R = rot_euler(*rot) if rot is not None else np.eye(3)
        p = Prim("ellipsoid", group, mat, bone, op, k, c=np.asarray(c, float), r=np.asarray(r, float), R=R, **kw)
        self.prims.append(p)
        return p

    def cone(self, a, b, ra, rb, mat="skin", bone="body", group="body", op="add", k=1.5, sq=(1.0, 1.0), up=(0, 1, 0), **kw):
        a = np.asarray(a, float)
        b = np.asarray(b, float)
        F = frame_from(b - a, up)
        p = Prim("roundcone", group, mat, bone, op, k, a=a, b=b, ra=float(ra), rb=float(rb), sq=tuple(sq), F=F,
                 L=float(np.linalg.norm(b - a)), **kw)
        self.prims.append(p)
        return p

    def rbox(self, c, h, rr, mat="skin", bone="body", group="body", op="add", k=1.0, R=None, rot=None, **kw):
        if R is None:
            R = rot_euler(*rot) if rot is not None else np.eye(3)
        p = Prim("roundbox", group, mat, bone, op, k, c=np.asarray(c, float), h=np.asarray(h, float), rr=float(rr), R=R, **kw)
        self.prims.append(p)
        return p

    def func(self, fn, lo, hi, mat, bone="body", group="body", op="add", k=0.2):
        p = Prim("func", group, mat, bone, op, k, fn=fn, lo=lo, hi=hi)
        self.prims.append(p)
        return p

    def spline_tube(self, pts, radii, bones_at, mat="skin", group="body", k=2.0, sq=(1.0, 1.0), sub=4, up=(0, 1, 0)):
        """smooth tube through control points (Catmull-Rom), radius per control point, bone blend along it.
        bones_at[i] = bone owning control point i; sub-segments blend linearly between neighbouring bones."""
        pts = [np.asarray(p, float) for p in pts]
        n = len(pts)
        samples, rs, bl = [], [], []
        for i in range(n - 1):
            p0 = pts[max(0, i - 1)]
            p1, p2 = pts[i], pts[i + 1]
            p3 = pts[min(n - 1, i + 2)]
            for s in range(sub):
                t = s / sub
                t2, t3 = t * t, t * t * t
                pt = 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
                samples.append(pt)
                rs.append(radii[i] + (radii[i + 1] - radii[i]) * t)
                bl.append([(bones_at[i], 1 - t), (bones_at[i + 1], t)])
        samples.append(pts[-1])
        rs.append(radii[-1])
        bl.append([(bones_at[-1], 1.0)])
        out = []
        for j in range(len(samples) - 1):
            b = [(nm, w * 0.5) for nm, w in bl[j]] + [(nm, w * 0.5) for nm, w in bl[j + 1]]
            merged = {}
            for nm, w in b:
                merged[nm] = merged.get(nm, 0) + w
            out.append(self.cone(samples[j], samples[j + 1], rs[j], rs[j + 1], mat=mat, bone=[(k_, v) for k_, v in merged.items()],
                                 group=group, k=k, sq=sq, up=up))
        return out, samples, rs


# ------------------------------------------------------------------------------------------ field
def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b + (a - b) * h - k * h * (1.0 - h)


def smax_sub(d, s, k):
    """smooth subtraction of s from d"""
    h = np.clip(0.5 - 0.5 * (d + s) / k, 0.0, 1.0)
    return d + (-s - d) * h + k * h * (1.0 - h)


def build_field(prims, cell, pad=4.0):
    lo = np.min([p.bbox[0] for p in prims if p.op == "add"], axis=0) - pad
    hi = np.max([p.bbox[1] for p in prims if p.op == "add"], axis=0) + pad
    n = np.ceil((hi - lo) / cell).astype(int) + 1
    F = np.full(n, 40.0, dtype=np.float32)
    for p in prims:
        k = max(p.k, 1e-3)
        i0 = np.clip(np.floor((p.bbox[0] - k - 1 - lo) / cell).astype(int), 0, n - 1)
        i1 = np.clip(np.ceil((p.bbox[1] + k + 1 - lo) / cell).astype(int) + 1, 0, n)
        if np.any(i1 <= i0):
            continue
        gx = lo[0] + np.arange(i0[0], i1[0]) * cell
        gy = lo[1] + np.arange(i0[1], i1[1]) * cell
        gz = lo[2] + np.arange(i0[2], i1[2]) * cell
        G = np.stack(np.meshgrid(gx, gy, gz, indexing="ij"), axis=-1)
        d = p.sdf(G.reshape(-1, 3)).reshape(G.shape[:-1]).astype(np.float32)
        sub = F[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]]
        if p.op == "add":
            F[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = smin(sub, d, k)
        else:
            F[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = smax_sub(sub, d, k)
    return F, lo, cell


def sample(F, lo, cell, pts):
    from scipy.ndimage import map_coordinates
    return map_coordinates(F, ((pts - lo) / cell).T, order=1, mode="nearest")


def gradient(F, lo, cell, pts):
    e = cell * 0.8
    g = np.zeros_like(pts)
    for a in range(3):
        d = np.zeros(3)
        d[a] = e
        g[:, a] = sample(F, lo, cell, pts + d) - sample(F, lo, cell, pts - d)
    return g / np.maximum(np.linalg.norm(g, axis=1, keepdims=True), 1e-9)


def polygonize(field, targets):
    """marching cubes then decimate to every target triangle count (one mesh per LOD)"""
    F, lo, cell = field
    verts, faces, _, _ = measure.marching_cubes(F, level=0.0, step_size=1, allow_degenerate=False)
    verts = verts * cell + lo
    lods = []
    for t in targets:
        s = pyfqmr.Simplify()
        s.setMesh(verts.astype(np.float64), faces.astype(np.int32))
        if len(faces) > t:
            s.simplify_mesh(target_count=int(t), aggressiveness=5, preserve_border=False, verbose=False)
        v, f, _ = s.getMesh()
        n = gradient(F, lo, cell, v)
        fn = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
        avg = n[f[:, 0]] + n[f[:, 1]] + n[f[:, 2]]
        flip = (fn * avg).sum(axis=1) < 0
        f = f.copy()
        f[flip] = f[flip][:, ::-1]
        lods.append((v, f, n))
    return lods, len(faces)


def ambient_occlusion(field, v, n):
    F, lo, cell = field
    acc = np.zeros(len(v))
    for dist, w in ((0.8, 0.35), (2.0, 0.3), (4.5, 0.2), (8.0, 0.15)):
        d = sample(F, lo, cell, v + n * dist)
        acc += w * np.clip((dist - d) / dist, 0, 1)
    return np.clip(1.0 - acc * 1.3, 0.2, 1.0)


def skin_weights(v, prims, bone_names, s=1.0, max_bones=4):
    adds = [p for p in prims if p.op == "add"]
    D = np.stack([p.sdf(v) for p in adds], axis=1)
    dmin = D.min(axis=1, keepdims=True)
    scales = np.array([s * p.weight_scale for p in adds])[None, :]
    W = np.exp(-(D - dmin) / scales)
    infl = np.zeros((len(v), len(bone_names)))
    idx = {b: i for i, b in enumerate(bone_names)}
    for j, p in enumerate(adds):
        for nm, w in p.bones:
            infl[:, idx[nm]] += W[:, j] * w
    order = np.argsort(-infl, axis=1)[:, :max_bones]
    w = np.take_along_axis(infl, order, axis=1)
    w = w / np.maximum(w.sum(axis=1, keepdims=True), 1e-9)
    w[w < 0.04] = 0
    w = w / np.maximum(w.sum(axis=1, keepdims=True), 1e-9)
    near = D.argmin(axis=1)
    return order, w, near


# ------------------------------------------------------------------------------------------ explicit meshes
def tube_mesh(path, radii, n=8, cap=True):
    """generalised cone along a polyline (for teeth, claws, horns): returns verts, faces, normals"""
    path = [np.asarray(p, float) for p in path]
    vs, ns = [], []
    m = len(path)
    for i in range(m):
        if i == 0:
            t = path[1] - path[0]
        elif i == m - 1:
            t = path[-1] - path[-2]
        else:
            t = path[i + 1] - path[i - 1]
        Fr = frame_from(t)
        u, w = Fr[:, 0], Fr[:, 1]
        r = radii[i]
        for k in range(n):
            ph = 2 * math.pi * k / n
            d = u * math.cos(ph) + w * math.sin(ph)
            vs.append(path[i] + d * max(r, 0.01))
            ns.append(d)
    fs = []
    for i in range(m - 1):
        for k in range(n):
            a, b = i * n + k, i * n + (k + 1) % n
            c, d = (i + 1) * n + k, (i + 1) * n + (k + 1) % n
            fs += [(a, c, b), (b, c, d)]
    tip = len(vs)
    vs.append(path[-1] + normalize(path[-1] - path[-2]) * radii[-1] * 0.5)
    ns.append(normalize(path[-1] - path[-2]))
    for k in range(n):
        fs.append(((m - 1) * n + k, tip, (m - 1) * n + (k + 1) % n))
    if cap:
        base = len(vs)
        vs.append(path[0])
        ns.append(-normalize(path[1] - path[0]))
        for k in range(n):
            fs.append((k, (k + 1) % n, base))
    return np.array(vs), np.array(fs, dtype=np.int64), np.array(ns)


def sphere_mesh(c, r, nu=14, nv=10, R=None):
    vs, ns, fs = [], [], []
    for j in range(nv + 1):
        th = math.pi * j / nv
        for i in range(nu):
            ph = 2 * math.pi * i / nu
            d = np.array([math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph)])
            if R is not None:
                d = R @ d
            vs.append(np.asarray(c) + d * r)
            ns.append(d)
    for j in range(nv):
        for i in range(nu):
            a = j * nu + i
            b = j * nu + (i + 1) % nu
            c2 = (j + 1) * nu + i
            d = (j + 1) * nu + (i + 1) % nu
            if j > 0:
                fs.append((a, c2, b))
            if j < nv - 1:
                fs.append((b, c2, d))
    return np.array(vs), np.array(fs, dtype=np.int64), np.array(ns)


def fix_winding(v, f, n):
    fn = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
    avg = n[f[:, 0]] + n[f[:, 1]] + n[f[:, 2]]
    flip = (fn * avg).sum(axis=1) < 0
    f = f.copy()
    f[flip] = f[flip][:, ::-1]
    return f
