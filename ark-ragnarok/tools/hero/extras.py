"""Explicit detail meshes that are thin or sharp: plates, spikes, sails, fins, feather fans, crests.
Each returns hero_build part dicts (rigid to one bone, or blended along a chain of bones)."""
import math
import numpy as np
import sdfkit as K
from sdfkit import normalize, frame_from
import hero_build as HB


def ellipsoid(center, axes, radii, nu=14, nv=9):
    """axes: 3x3 columns (u, v, w) orientation; radii along them"""
    v, f, n = K.sphere_mesh((0, 0, 0), 1.0, nu, nv)
    A = np.asarray(axes, float)
    r = np.asarray(radii, float)
    lv = v * r
    ln = n / np.maximum(r, 1e-3)
    ln /= np.linalg.norm(ln, axis=1, keepdims=True)
    return lv @ A.T + np.asarray(center, float), f, ln @ A.T


def kite(center, up, fwd, height, length, thick, nu=16, nv=10):
    """plate shaped like a rounded kite: wide low, pointed top"""
    side = normalize(np.cross(up, fwd))
    A = np.stack([side, normalize(up), normalize(fwd)], axis=1)
    v, f, n = ellipsoid((0, 0, 0), np.eye(3), (thick, height / 2, length / 2), nu, nv)
    t = (v[:, 1] / (height / 2) + 1) / 2              # 0 bottom .. 1 top
    v[:, 2] *= (1.0 - 0.55 * t ** 1.5)
    v[:, 1] += height / 2
    return v @ A.T + np.asarray(center, float), f, n @ A.T


def top_y(sc, x, z, y0=200.0):
    ys = np.linspace(y0, 0, 1600)
    P = np.stack([np.full_like(ys, x), ys, np.full_like(ys, z)], axis=1)
    d = np.full(len(P), 1e9)
    for p in sc.prims:
        if p.group == "body" and p.op == "add":
            d = np.minimum(d, p.sdf(P))
    i = int(np.argmax(d < 0))
    return float(ys[i])


def chain_weights(points, chain, sc):
    """blend weights along a list of bones ordered by their pivot z"""
    bones = sc.bone_order
    piv = np.array([sc.bones[b][1][2] for b in chain])
    bi = np.zeros((len(points), 4), dtype=int)
    bw = np.zeros((len(points), 4))
    for k, p in enumerate(points):
        z = p[2]
        j = int(np.clip(np.searchsorted(piv, z) - 1, 0, len(chain) - 1))
        if j >= len(chain) - 1:
            bi[k, 0] = bones.index(chain[-1])
            bw[k, 0] = 1
            continue
        t = float(np.clip((z - piv[j]) / max(piv[j + 1] - piv[j], 1e-6), 0, 1))
        bi[k, 0], bi[k, 1] = bones.index(chain[j]), bones.index(chain[j + 1])
        bw[k, 0], bw[k, 1] = 1 - t, t
    return bi, bw


def weighted(v, f, n, bi, bw, mat):
    f = K.fix_winding(v, f, n)
    return dict(v=v, n=n, f=f, bi=bi, bw=bw, ao=np.full(len(v), 0.95), tmat=np.array([mat] * len(f)), grp="body",
                along=np.zeros(len(v)), eye=np.full(len(v), -1))


def sheet(points_bottom, heights, thick, sc, chain, mat):
    """a thin sail / fin standing on a curve of the back"""
    m = len(points_bottom)
    vs, ns = [], []
    rows = 6
    for side in (-1, 1):
        for i in range(m):
            for r in range(rows + 1):
                t = r / rows
                b = points_bottom[i]
                h = heights[i]
                taper = 1 - 0.65 * t
                vs.append(b + np.array([side * thick * taper, h * t, 0.0]))
                ns.append(np.array([side, 0.0, 0.0]))
    vs = np.array(vs)
    ns = np.array(ns)
    faces = []
    for side in (0, 1):
        o = side * m * (rows + 1)
        for i in range(m - 1):
            for r in range(rows):
                a = o + i * (rows + 1) + r
                b = o + (i + 1) * (rows + 1) + r
                faces += [(a, b, a + 1), (b, b + 1, a + 1)] if side == 0 else [(a, a + 1, b), (b, a + 1, b + 1)]
    # rim along the top
    o2 = m * (rows + 1)
    for i in range(m - 1):
        a, b = i * (rows + 1) + rows, (i + 1) * (rows + 1) + rows
        faces += [(a, o2 + a, b), (b, o2 + a, o2 + b)]
    f = np.array(faces, dtype=np.int64)
    bi, bw = chain_weights(vs, chain, sc)
    return weighted(vs, f, ns, bi, bw, mat)


def rigid(v, f, n, bone, sc, mat):
    return HB.rigid_part(v, f, n, bone, sc.bone_order, mat)


def build(sid, B, sc):
    """B: the species builder (Theropod / Quadruped / Sauropod) after build()"""
    feats = set(B.P.get("features", ()))
    parts = []
    piv = lambda b: sc.bones[b][1]
    if "sail" in feats:              # spinosaurus
        zs = np.linspace(piv("neck1")[2] + 4, piv("tail2")[2], 26)
        bottoms = [np.array([0.0, top_y(sc, 0.0, z) - 3.0, z]) for z in zs]
        L = zs[-1] - zs[0]
        hs = [22.0 * math.sin(math.pi * ((z - zs[0]) / L) ** 0.9) ** 0.8 + 2.0 for z in zs]
        parts.append(sheet(bottoms, hs, 0.55, sc, ["neck1", "torso", "body", "tail1", "tail2"], "sail"))
    if "tail_fin" in feats:
        zs = np.linspace(piv("tail2")[2], piv("tail8")[2] + 6, 24)
        bottoms = [np.array([0.0, top_y(sc, 0.0, z) - 1.5, z]) for z in zs]
        hs = [5.5 * math.sin(math.pi * (i / (len(zs) - 1))) + 1.0 for i in range(len(zs))]
        parts.append(sheet(bottoms, hs, 0.35, sc, ["tail2", "tail3", "tail4", "tail5", "tail6", "tail7", "tail8"], "sail"))
    if "plates" in feats:            # stegosaurus: two alternating rows of tall plates
        chain = ["torso", "body", "tail1", "tail2", "tail3"]
        z0, z1 = piv("neck1")[2] + 2, piv("tail3")[2]
        n = 8
        for i in range(n):
            t = i / (n - 1)
            z = z0 + (z1 - z0) * t
            h = 6.0 + 15.0 * math.sin(math.pi * min(1.0, t * 1.05)) ** 1.2
            for s in (-1, 1):
                zz = z + s * (z1 - z0) / (n - 1) * 0.25       # the two rows alternate
                y = top_y(sc, 0.0, zz) - 2.2
                v, f, nrm = kite((s * 1.7, y, zz), (s * 0.2, 1.0, 0.0), (0, 0, 1.0), h, h * 0.62, 0.7)
                bone = chain[int(np.clip(np.searchsorted([piv(b)[2] for b in chain], zz) - 1, 0, len(chain) - 1))]
                parts.append(rigid(v, f, nrm, bone, sc, "plate"))
    if "thagomizer" in feats:
        b = "tail6"
        base = piv(b) + np.array([0, 1.0, 4.0])
        for s in (-1, 1):
            for k, (dz, ang) in enumerate(((0.0, 0.55), (4.5, 0.75))):
                tip = base + np.array([s * 9.0 * math.cos(ang), 6.0 * math.sin(ang), dz + 2.0])
                v, f, nrm = K.tube_mesh([base + np.array([0, 0, dz]), tip], [1.3, 0.12], n=8)
                parts.append(rigid(v, f, nrm, b, sc, "horn"))
    if "side_spikes" in feats:       # ankylosaurus
        for s in (-1, 1):
            for i, b in enumerate(("torso", "torso", "body", "body", "tail1", "tail2")):
                z = [piv("neck1")[2] + 8, piv("torso")[2] - 4, piv("torso")[2] + 8, piv("body")[2] + 2, piv("tail1")[2] + 3,
                     piv("tail2")[2] + 3][i]
                y = top_y(sc, 0.0, z) - 9.0 - i * 0.4
                x = s * (abs(top_x(sc, y, z)) - 1.0)
                base = np.array([x, y, z])
                tip = base + np.array([s * (5.5 - i * 0.5), -0.5, 1.5])
                v, f, nrm = K.tube_mesh([base, tip], [1.6 - i * 0.12, 0.12], n=8)
                parts.append(rigid(v, f, nrm, b, sc, "horn"))
    if "frill_rim" in feats or "frill_spikes" in feats:
        # small bony knobs around the frill edge; pachyrhinosaurus gets two long spikes
        hp = piv("head")
        fr = [p for p in sc.prims if p.mat == "frill"]
        if fr:
            g = fr[0].geo
            c, R, r = g["c"], g["R"], g["r"]
            for i in range(13):
                a = -math.pi * 0.85 + i * math.pi * 1.7 / 12
                local = np.array([math.sin(a) * r[0] * 0.98, math.cos(a) * r[1] * 0.98, 0.0])
                pw = c + R @ local
                outward = normalize(R @ np.array([math.sin(a), math.cos(a), 0.0]))
                big = "frill_spikes" in feats and i in (5, 7)
                L = 7.0 if big else 1.8
                v, f, nrm = K.tube_mesh([pw - outward * 0.6, pw + outward * L], [1.4 if big else 0.9, 0.15], n=7)
                parts.append(rigid(v, f, nrm, "head", sc, "horn"))
    if "wing_feathers" in feats:     # pennaceous feathers along forearm and hand
        for s in (-1, 1):
            n_ = "l" if s > 0 else "r"
            for bone, count, L in (("forearm_" + n_, 5, 5.5), ("hand_" + n_, 4, 6.5)):
                p0 = piv(bone)
                nxt = {"forearm_" + n_: "hand_" + n_, "hand_" + n_: None}[bone]
                d = normalize((piv(nxt) - p0) if nxt else np.array([0, -0.6, -0.8]))
                for k in range(count):
                    pos = p0 + d * (k + 0.5) * (2.0 if bone.startswith("forearm") else 1.4)
                    back = normalize(np.array([s * 0.25, -0.35, 1.0]))
                    axes = frame_from(back, (s * 1.0, 0, 0))
                    v, f, nrm = ellipsoid(pos + back * L * 0.45, axes, (1.1 * B.radius_scale(bone), 0.18, L * 0.5), 10, 6)
                    parts.append(rigid(v, f, nrm, bone, sc, "feather"))
    if "tail_fan" in feats or "tail_tuft" in feats:
        last = [b for b in sc.bone_order if b.startswith("tail")][-1]
        p0 = piv(last)
        fan = "tail_fan" in feats
        for k in range(7 if fan else 6):
            a = (-0.9 + k * 0.3) if fan else (-0.75 + k * 0.3)
            d = normalize(np.array([math.sin(a), 0.15 if fan else 0.6, math.cos(a)]))
            L = 7.0 if fan else 4.0
            axes = frame_from(d, (0, 1, 0))
            v, f, nrm = ellipsoid(p0 + d * L * 0.5, axes, (1.0, 0.22 if fan else 0.6, L * 0.5), 10, 6)
            parts.append(rigid(v, f, nrm, last, sc, "feather"))
    if "quill_crest" in feats:
        hp = piv("head")
        for k in range(5):
            pos = hp + np.array([0, 6.5 * B.radius_scale("head"), -2.0 + k * 1.6])
            d = normalize(np.array([0, 0.9 - k * 0.1, 0.5 + k * 0.15]))
            axes = frame_from(d, (1, 0, 0))
            v, f, nrm = ellipsoid(pos + d * 2.5, axes, (0.5, 0.15, 2.6 - k * 0.2), 8, 5)
            parts.append(rigid(v, f, nrm, "head", sc, "feather"))
    if "neck_mane" in feats:
        for b in ("neck1", "neck2"):
            p0 = piv(b)
            for k in range(4):
                pos = p0 + np.array([0, 7.5 - k * 0.3, -k * 2.2])
                d = normalize(np.array([0, 0.5, 1.0]))
                axes = frame_from(d, (1, 0, 0))
                v, f, nrm = ellipsoid(pos + d * 2.0, axes, (3.2, 0.3, 3.5), 10, 6)
                parts.append(rigid(v, f, nrm, b, sc, "feather"))
    return parts


def top_x(sc, y, z, x0=80.0):
    xs = np.linspace(x0, 0, 1600)
    P = np.stack([xs, np.full_like(xs, y), np.full_like(xs, z)], axis=1)
    d = np.full(len(P), 1e9)
    for p in sc.prims:
        if p.group == "body" and p.op == "add":
            d = np.minimum(d, p.sdf(P))
    i = int(np.argmax(d < 0))
    return float(xs[i])
