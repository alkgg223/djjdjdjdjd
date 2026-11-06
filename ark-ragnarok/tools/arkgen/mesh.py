"""Smooth skinned creature meshes.

The anatomy builders give a skeleton (parts = bones) and rough volumes (cubes).  Here every volume becomes
a rounded primitive; organic primitives are merged with a smooth union into ONE continuous surface
(marching cubes on a signed distance field), so the neck flows into the body, legs into the hips and the
tail tapers smoothly.  Small sharp things (horns, claws, teeth, eyes) and thin things (wing membranes,
fins, frills, sails) are explicit meshes.  Every vertex gets up to 3 bone weights so joints bend smoothly.
"""
import math
import numpy as np
from skimage import measure
import pyfqmr

PLATE_MATS = {"membrane", "frill", "sail", "fin", "insectwing", "feather"}
CONE_MATS = {"horn", "tusk", "claw", "bone"}


CHAIN_ROLES = {"TAIL", "NECK", "SPINE", "TRUNK", "STINGER", "TENTACLE", "LEG", "ARM", "BODY", "CHEST"}


class Prim:
    __slots__ = ("bone", "R", "t", "center", "half", "mat", "kind", "decals", "box", "role", "terminal", "idx", "group", "mid",
                 "rc")

    def __init__(self, **kw):
        for k, v in kw.items():
            setattr(self, k, v)

    def world(self, local):
        return (self.R @ local.T).T + self.t

    def to_local(self, pw):
        return (pw - self.t) @ self.R - self.center


def collect(m, mats, sp):
    prims = []
    for p in m.order:
        M = mats[p.name]
        R, t = M[:3, :3], M[:3, 3]
        role = p.role[0] if p.role else ""
        terminal = (role == "LEG" and not [ch for ch in p.children if ch.role]) or (p.role is None and p.parent is not None
                    and p.parent.role and p.parent.role[0] == "LEG" and not p.children)
        terminal_arm = role == "ARM" and not [c for c in p.children if c.role and c.role[0] == "ARM"]
        for c in p.cubes:
            half = np.array([c.w / 2.0 + c.inflate, c.h / 2.0 + c.inflate, c.d / 2.0 + c.inflate])
            center = np.array([c.x + c.w / 2.0, c.y + c.h / 2.0, c.z + c.d / 2.0])
            mn, mx = sorted(half)[0], sorted(half)[2]
            aspect = mx / max(0.5, sorted(half)[1])
            mat = c.mat
            kind = "blob"
            if mat == "saddle":
                kind = "saddle"
            elif mat in CONE_MATS and aspect >= 2.2 and mn <= 1.6:
                kind = "cone"
            elif mat in PLATE_MATS and mn <= 1.01:
                kind = "plate"
            elif mn <= 0.51 and mat not in ("skin", "fur", "chitin"):
                kind = "plate"
            if kind == "blob" and mat == "claw":
                mat = sp.get("foot_mat", "skin") if (terminal or terminal_arm) else "claw"
            group = "jaw" if role == "JAW" else ("main" if kind == "blob" else kind)
            mid = any(ch.cubes and ch.cubes[0].mat == c.mat and not ch.role for ch in p.children)
            rc = None
            if kind == "blob" and role in CHAIN_ROLES and c is p.cubes[0] and not ((terminal or terminal_arm) and c.mat == "claw"):
                rc = round_cone_of(p, c, half, center)
            prims.append(Prim(bone=p.name, R=R, t=t, center=center, half=half, mat=mat, kind=kind, decals=list(c.decals),
                              box=boxiness(c, role), role=role, terminal=(terminal or terminal_arm) and c.mat == "claw",
                              idx=len(prims), group=group, mid=mid, rc=rc))
    return prims


def next_segment(p):
    role = p.role[0]
    want = {"BODY": ("CHEST", "NECK"), "CHEST": ("NECK",)}.get(role, (role,))
    for w in want:
        for ch in p.children:
            if ch.role and ch.role[0] == w and ch.cubes:
                return ch
    return None


def round_cone_of(p, c, half, center):
    """segment along the cube's long axis, radius tapering towards the next segment of the chain"""
    ax = int(np.argmax(half))
    others = [i for i in range(3) if i != ax]
    e = np.zeros(3)
    e[ax] = half[ax] - min(half[others[0]], half[others[1]]) * 0.5
    a, b = center - e, center + e
    if np.linalg.norm(a) > np.linalg.norm(b):
        a, b = b, a
    ra = np.array([half[others[0]], half[others[1]]]) * 1.08
    role = p.role[0]
    if role in ("BODY", "CHEST") and ax != 2:
        return None
    nx = next_segment(p)
    if role == "BODY":       # torso: a smooth capsule, slightly tapering to the front
        return (a, b, ra, ra * 0.97, others) if np.linalg.norm(a) <= np.linalg.norm(b) else (a, b, ra, ra, others)
    if nx is not None:
        nc = nx.cubes[0]
        nh = np.array([nc.w / 2.0, nc.h / 2.0, nc.d / 2.0])
        nax = int(np.argmax(nh))
        no = [i for i in range(3) if i != nax]
        rb = np.array([nh[no[0]], nh[no[1]]]) * 1.08
        if rb.shape == ra.shape and no != others:
            rb = rb[::-1]
    else:
        rb = ra * (0.35 if role in ("TAIL", "STINGER", "TENTACLE", "TRUNK", "SPINE") else 0.85)
    return (a, b, ra, rb, others)


def boxiness(c, role):
    if "eyes" in c.decals or role == "HEAD":
        return 0.45
    if c.mat in ("shell", "plate"):
        return 0.3
    if role in ("JAW",):
        return 0.5
    return 0.22


# ----------------------------------------------------------------------------- SDF
def sdf_local(q, half, box):
    h = np.maximum(half, 0.45)
    k0 = np.linalg.norm(q / h, axis=-1)
    k1 = np.linalg.norm(q / (h * h), axis=-1)
    ell = k0 * (k0 - 1.0) / np.maximum(k1, 1e-6)
    r = 0.55 * h.min()
    qq = np.abs(q) - h + r
    rb = np.linalg.norm(np.maximum(qq, 0), axis=-1) + np.minimum(qq.max(axis=-1), 0) - r
    return ell * (1 - box) + rb * box


def sd_round_cone(q, a, b, r1, r2):
    ba = b - a
    l2 = float(ba @ ba)
    rr = r1 - r2
    a2 = l2 - rr * rr
    il2 = 1.0 / max(l2, 1e-9)
    pa = q - a
    y = pa @ ba
    z = y - l2
    xv = pa * l2 - np.outer(y, ba)
    x2 = (xv * xv).sum(axis=-1)
    y2 = y * y * l2
    z2 = z * z * l2
    k = np.sign(rr) * rr * rr * x2
    d3 = (np.sqrt(np.maximum(x2 * a2 * il2, 0)) + y * rr) * il2 - r1
    d1 = np.sqrt(x2 + z2) * il2 - r2
    d2 = np.sqrt(x2 + y2) * il2 - r1
    out = np.where(np.sign(z) * a2 * z2 > k, d1, np.where(np.sign(y) * a2 * y2 < k, d2, d3))
    return out


def prim_sdf(p, q_local_centered):
    """q relative to the cube centre in the part frame"""
    if p.rc is None:
        return sdf_local(q_local_centered, blob_half(p), p.box)
    a, b, ra, rb, others = p.rc
    q = q_local_centered + p.center
    shp = q.shape
    q = q.reshape(-1, 3)
    # elliptical cross-section: squash the second axis into the first
    s0 = 1.0
    s1 = ((ra[0] + rb[0]) / max(ra[1] + rb[1], 1e-6))
    qq = q.copy()
    qq[:, others[1]] *= s1
    aa, bb = a.copy(), b.copy()
    aa[others[1]] *= s1
    bb[others[1]] *= s1
    d = sd_round_cone(qq, aa, bb, float(ra[0]), float(rb[0]))
    return d.reshape(shp[:-1])


def smin(a, b, k):
    h = np.maximum(k - np.abs(a - b), 0.0) / k
    return np.minimum(a, b) - h * h * k * 0.25


def blob_half(p):
    return p.half * np.array([1.12, 1.12, 1.08])


def field_for(prims, cell, pad=3.0):
    pts = []
    for p in prims:
        hb = blob_half(p)
        corners = np.array([[sx, sy, sz] for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]) * hb + p.center
        pts.append(p.world(corners))
    pts = np.concatenate(pts)
    lo = pts.min(axis=0) - pad
    hi = pts.max(axis=0) + pad
    n = np.ceil((hi - lo) / cell).astype(int) + 1
    F = np.full(n, 50.0, dtype=np.float32)
    for p in prims:
        hb = blob_half(p)
        k = float(np.clip(0.55 * hb.min(), 0.4, 3.5))
        corners = np.array([[sx, sy, sz] for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]) * (hb + k + 1) + p.center
        cw = p.world(corners)
        i0 = np.clip(np.floor((cw.min(axis=0) - lo) / cell).astype(int), 0, n - 1)
        i1 = np.clip(np.ceil((cw.max(axis=0) - lo) / cell).astype(int) + 1, 0, n)
        gx = lo[0] + np.arange(i0[0], i1[0]) * cell
        gy = lo[1] + np.arange(i0[1], i1[1]) * cell
        gz = lo[2] + np.arange(i0[2], i1[2]) * cell
        G = np.stack(np.meshgrid(gx, gy, gz, indexing="ij"), axis=-1)
        q = (G - p.t) @ p.R - p.center
        d = prim_sdf(p, q).astype(np.float32)
        sub = F[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]]
        F[i0[0]:i1[0], i0[1]:i1[1], i0[2]:i1[2]] = smin(sub, d, k)
    return F, lo, cell


def sample(F, lo, cell, pts):
    from scipy.ndimage import map_coordinates
    idx = ((pts - lo) / cell).T
    return map_coordinates(F, idx, order=1, mode="nearest")


def grad(F, lo, cell, pts):
    e = cell * 0.75
    g = np.zeros_like(pts)
    for a in range(3):
        d = np.zeros(3)
        d[a] = e
        g[:, a] = sample(F, lo, cell, pts + d) - sample(F, lo, cell, pts - d)
    n = np.linalg.norm(g, axis=1, keepdims=True)
    return g / np.maximum(n, 1e-9)


def surface(prims, cell, target_tris):
    F, lo, cell = field_for(prims, cell)
    verts, faces, _, _ = measure.marching_cubes(F, level=0.0)
    verts = verts * cell + lo
    s = pyfqmr.Simplify()
    s.setMesh(verts.astype(np.float64), faces.astype(np.int32))
    if len(faces) > target_tris:
        s.simplify_mesh(target_count=int(target_tris), aggressiveness=6, preserve_border=False, verbose=False)
    v, f, _ = s.getMesh()
    # outward normals from the field gradient, and consistent winding
    nrm = grad(F, lo, cell, v)
    fn = np.cross(v[f[:, 1]] - v[f[:, 0]], v[f[:, 2]] - v[f[:, 0]])
    avg = (nrm[f[:, 0]] + nrm[f[:, 1]] + nrm[f[:, 2]])
    flip = (fn * avg).sum(axis=1) < 0
    f[flip] = f[flip][:, ::-1]
    # ambient occlusion from the field
    ao = np.ones(len(v))
    acc = np.zeros(len(v))
    for dist, w in ((1.0, 0.5), (2.5, 0.3), (5.0, 0.2)):
        d = sample(F, lo, cell, v + nrm * dist)
        acc += w * np.clip((dist - d) / dist, 0, 1)
    ao = np.clip(1.0 - acc * 1.15, 0.25, 1.0)
    return v, f, nrm, ao, (F, lo, cell)


# ----------------------------------------------------------------------------- explicit meshes
def uv_sphere(nu=14, nv=9):
    vs, fs = [], []
    for j in range(nv + 1):
        th = math.pi * j / nv
        for i in range(nu):
            ph = 2 * math.pi * i / nu
            vs.append((math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph)))
    for j in range(nv):
        for i in range(nu):
            a = j * nu + i
            b = j * nu + (i + 1) % nu
            c = (j + 1) * nu + i
            d = (j + 1) * nu + (i + 1) % nu
            if j > 0:
                fs.append((a, b, c))
            if j < nv - 1:
                fs.append((b, d, c))
    v = np.array(vs, dtype=np.float64)
    return v, np.array(fs, dtype=np.int32), v.copy()


def cone(base, tip, r0, r1=0.0, n=8, bend=None, rings=4):
    """frustum from base to tip; returns world verts, faces, normals"""
    axis = tip - base
    L = np.linalg.norm(axis)
    if L < 1e-6:
        return None
    a = axis / L
    tmp = np.array([1.0, 0, 0]) if abs(a[0]) < 0.9 else np.array([0, 1.0, 0])
    u = np.cross(a, tmp)
    u /= np.linalg.norm(u)
    w = np.cross(a, u)
    vs, ns = [], []
    for j in range(rings + 1):
        t = j / rings
        r = r0 + (r1 - r0) * (t ** 0.85)
        c = base + axis * t
        if bend is not None:
            c = c + bend * (t * t)
        for i in range(n):
            ph = 2 * math.pi * i / n
            d = u * math.cos(ph) + w * math.sin(ph)
            vs.append(c + d * max(r, 0.02))
            ns.append(d * 0.9 + a * 0.3)
    vs.append(base + axis + (bend if bend is not None else 0))
    ns.append(a)
    vs.append(base)
    ns.append(-a)
    tip_i, base_i = len(vs) - 2, len(vs) - 1
    fs = []
    for j in range(rings):
        for i in range(n):
            p0 = j * n + i
            p1 = j * n + (i + 1) % n
            q0 = (j + 1) * n + i
            q1 = (j + 1) * n + (i + 1) % n
            fs += [(p0, q0, p1), (p1, q0, q1)]
    for i in range(n):
        fs.append((rings * n + i, tip_i, rings * n + (i + 1) % n))
        fs.append((i, (i + 1) % n, base_i))
    ns = np.array(ns)
    ns /= np.linalg.norm(ns, axis=1, keepdims=True)
    return np.array(vs), np.array(fs, dtype=np.int32), ns


def ellipsoid_mesh(p, half, nu=16, nv=10):
    v, f, n = uv_sphere(nu, nv)
    lv = v * half + p.center
    ln = n / np.maximum(half, 1e-3)
    ln /= np.linalg.norm(ln, axis=1, keepdims=True)
    return p.world(lv), f, (p.R @ ln.T).T


def plate_mesh(p):
    h = p.half.copy()
    thin = int(np.argmin(h))
    h[thin] = max(h[thin] * 0.8, 0.3)
    # plates taper: slightly thicker at the root
    return ellipsoid_mesh(p, h, 18, 10)


def saddle_mesh(p):
    h = p.half.copy() * np.array([1.0, 1.0, 1.0])
    return ellipsoid_mesh(p, h * np.array([1.05, 1.1, 1.0]), 14, 8)


def horn_mesh(p):
    h = p.half
    ax = int(np.argmax(h))
    e = np.zeros(3)
    e[ax] = h[ax]
    a_end = p.center - e
    b_end = p.center + e
    # base is the end nearer to the bone pivot (local origin)
    if np.linalg.norm(a_end) < np.linalg.norm(b_end):
        base, tip = a_end, b_end
    else:
        base, tip = b_end, a_end
    others = [h[i] for i in range(3) if i != ax]
    r0 = max(0.35, min(others) * 1.05)
    # a horn segment that has a child horn segment keeps some thickness at the end
    r1 = r0 * (0.62 if p.mid else 0.08)
    bw = p.world(np.array([base, tip]))
    return cone(bw[0], bw[1], r0, r1, n=8, rings=3)


def eye_meshes(p, sp):
    """two eye spheres on the sides of a head primitive"""
    out = []
    h = p.half
    r = max(0.55, min(h[1], h[2]) * sp.get("eye_size", 0.3))
    for side in (-1, 1):
        lc = p.center + np.array([side * h[0] * 0.86, -h[1] * 0.28, -h[2] * 0.42])
        v, f, n = uv_sphere(10, 7)
        lv = v * r + lc
        out.append((p.world(lv), f, (p.R @ n.T).T, p.world(lc[None, :])[0], (p.R @ np.array([side, -0.15, -0.35]))))
    return out


def teeth_meshes(p, up, sp):
    """row of conical teeth along both sides of a snout (down) or jaw (up)"""
    out = []
    h = p.half
    L = h[2] * 2
    n = int(np.clip(L / max(1.4, sp.get("tooth_gap", 1.7)), 2, 14))
    th = float(np.clip(min(h[1] * 1.0, h[2] * 0.25), 0.5, 2.6)) * sp.get("tooth_size", 1.0)
    for side in (-1, 1):
        for i in range(n):
            z = p.center[2] - h[2] * 0.9 + (i + 0.5) / n * L * 0.85
            y = p.center[1] + (-h[1] * 0.85 if up else h[1] * 0.85)
            x = p.center[0] + side * h[0] * 0.72
            base = np.array([x, y, z])
            tip = base + np.array([0, -th if up else th, 0])
            bw = p.world(np.array([base, tip]))
            c = cone(bw[0], bw[1], th * 0.32, 0.03, n=4, rings=1)
            if c:
                out.append(c)
    return out


def claw_meshes(p):
    """claws at the front tip of a foot / hand primitive"""
    out = []
    h = p.half
    n = 3 if h[0] >= 1.4 else 2
    L = max(0.9, min(h[2] * 0.9, h[1] * 2.5 + 0.8))
    for i in range(n):
        x = p.center[0] + (i - (n - 1) / 2.0) * (h[0] * 1.4 / max(1, n - 1)) if n > 1 else p.center[0]
        base = np.array([x, p.center[1] + h[1] * 0.25, p.center[2] - h[2] * 0.85])
        tip = base + np.array([0, h[1] * 0.9, -L])
        bw = p.world(np.array([base, tip]))
        c = cone(bw[0], bw[1], max(0.3, min(h[0], h[1]) * 0.38), 0.03, n=6, rings=2)
        if c:
            out.append(c)
    return out


# ----------------------------------------------------------------------------- weights
def bone_weights(verts, prims, bones, s_scale):
    """per vertex up to 3 (bone index, weight)"""
    nb = len(bones)
    infl = np.full((len(verts), nb), 0.0)
    D = np.full((len(verts), len(prims)), 1e9)
    for j, p in enumerate(prims):
        q = (verts - p.t) @ p.R - p.center
        D[:, j] = prim_sdf(p, q)
    dmin = D.min(axis=1, keepdims=True)
    W = np.exp(-(D - dmin) / s_scale)
    for j, p in enumerate(prims):
        b = bones.index(p.bone)
        infl[:, b] = np.maximum(infl[:, b], W[:, j])
    order = np.argsort(-infl, axis=1)[:, :3]
    w = np.take_along_axis(infl, order, axis=1)
    w[w < 0.06] = 0
    w = w / np.maximum(w.sum(axis=1, keepdims=True), 1e-9)
    return order, w


# ----------------------------------------------------------------------------- build all
def build_mesh(m, mats, sp, target_tris=3200):
    prims = collect(m, mats, sp)
    bones = [p.name for p in m.order]
    allpts = np.concatenate([p.world(np.array([p.center])) for p in prims])
    ext = float((allpts.max(axis=0) - allpts.min(axis=0)).max()) + 10
    cell = float(np.clip(ext / sp.get("grid", 190), 0.22, 0.9))
    s_scale = float(np.clip(ext * 0.012, 0.35, 1.6))

    V, N, AO, Fc, MAT, BI, BW, GRP, PRIM = [], [], [], [], [], [], [], [], []
    offset = 0

    def add(v, f, n, ao, mat, bi, bw, grp, prim):
        nonlocal offset
        V.append(v)
        N.append(n)
        AO.append(ao)
        Fc.append(f + offset)
        MAT.extend([mat] * len(f) if isinstance(mat, str) else list(mat))
        GRP.extend([grp] * len(f))
        PRIM.extend([prim] * len(f) if np.isscalar(prim) else list(prim))
        BI.append(bi)
        BW.append(bw)
        offset += len(v)

    fields = {}
    for grp in ("main", "jaw"):
        gp = [p for p in prims if p.group == grp]
        if not gp:
            continue
        tt = target_tris if grp == "main" else max(200, target_tris // 10)
        v, f, n, ao, fld = surface(gp, cell, tt)
        fields[grp] = fld
        bi, bw = bone_weights(v, gp, bones, s_scale)
        # per triangle material = material of nearest primitive at centroid
        cen = (v[f[:, 0]] + v[f[:, 1]] + v[f[:, 2]]) / 3.0
        D = np.stack([prim_sdf(p, (cen - p.t) @ p.R - p.center) for p in gp], axis=1)
        near = D.argmin(axis=1)
        tri_mat = [gp[i].mat for i in near]
        tri_prim = [gp[i].idx for i in near]
        add(v, f, n, ao, tri_mat, bi, bw, "body", tri_prim)

    def rigid(v, f, n, mat, bone, grp="body", prim=-1, ao_val=1.0):
        bi = np.zeros((len(v), 3), dtype=int)
        bi[:, 0] = bones.index(bone)
        bw = np.zeros((len(v), 3))
        bw[:, 0] = 1.0
        add(v, f, n, np.full(len(v), ao_val), mat, bi, bw, grp, prim)

    eyes = []
    for p in prims:
        if p.kind == "cone":
            r = horn_mesh(p)
            if r:
                rigid(*r, p.mat, p.bone, prim=p.idx, ao_val=0.95)
        elif p.kind == "plate":
            v, f, n = plate_mesh(p)
            rigid(v, f, n, p.mat, p.bone, prim=p.idx, ao_val=0.92)
        elif p.kind == "saddle":
            v, f, n = saddle_mesh(p)
            rigid(v, f, n, "saddle", p.bone, grp="saddle", prim=p.idx)
        if p.kind == "blob":
            if "eyes" in p.decals:
                for (v, f, n, c, fwd) in eye_meshes(p, sp):
                    eyes.append((c, fwd))
                    rigid(v, f, n, "eye", p.bone, grp="glow" if sp.get("glow_eyes") else "body", prim=1000 + len(eyes) - 1)
            if "teeth_down" in p.decals and sp.get("teeth", True):
                for (v, f, n) in teeth_meshes(p, False, sp):
                    rigid(v, f, n, "tusk", p.bone, prim=p.idx)
            if "teeth_up" in p.decals and sp.get("teeth", True):
                for (v, f, n) in teeth_meshes(p, True, sp):
                    rigid(v, f, n, "tusk", p.bone, prim=p.idx)
            if p.terminal and sp.get("claws", True):
                for (v, f, n) in claw_meshes(p):
                    rigid(v, f, n, "claw", p.bone, prim=p.idx)
    mesh = dict(
        v=np.concatenate(V), n=np.concatenate(N), ao=np.concatenate(AO), f=np.concatenate(Fc),
        mat=np.array(MAT), bi=np.concatenate(BI), bw=np.concatenate(BW), grp=np.array(GRP), prim=np.array(PRIM),
        bones=bones, prims=prims, eyes=eyes, fields=fields,
    )
    nn = np.linalg.norm(mesh["n"], axis=1, keepdims=True)
    mesh["n"] = mesh["n"] / np.maximum(nn, 1e-9)
    return mesh


def skin(mesh, m, rest_mats, pose_mats):
    """apply a pose (for previews) using the same linear blend skinning as the Java renderer"""
    bones = mesh["bones"]
    S = [pose_mats[b] @ np.linalg.inv(rest_mats[b]) for b in bones]
    S = np.array(S)
    v = np.concatenate([mesh["v"], np.ones((len(mesh["v"]), 1))], axis=1)
    out = np.zeros_like(mesh["v"])
    nout = np.zeros_like(mesh["n"])
    for k in range(3):
        Mk = S[mesh["bi"][:, k]]
        w = mesh["bw"][:, k][:, None]
        out += w * np.einsum("nij,nj->ni", Mk, v)[:, :3]
        nout += w * np.einsum("nij,nj->ni", Mk[:, :3, :3], mesh["n"])
    nout /= np.maximum(np.linalg.norm(nout, axis=1, keepdims=True), 1e-9)
    return out, nout
