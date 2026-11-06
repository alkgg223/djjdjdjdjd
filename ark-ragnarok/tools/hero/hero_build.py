"""Build the LOD meshes of a hero creature from its sculpt module."""
import numpy as np
import sdfkit as K

GROUP_CELL = {"body": 0.36, "jaw": 0.26, "tongue": 0.26}


def build_lods(mod, lod_targets, cells=None, verbose=True):
    """lod_targets: {group: [tris per lod...]}.  Returns list of LOD dicts with v, n, f, bi, bw, mat, ao, grp, prim."""
    sc = mod.build()
    bones = sc.bone_order
    cells = cells or GROUP_CELL
    nlod = len(next(iter(lod_targets.values())))
    lods = [dict(parts=[]) for _ in range(nlod)]
    fields = {}
    for grp, targets in lod_targets.items():
        prims = [p for p in sc.prims if p.group == grp]
        if not prims:
            continue
        field = K.build_field(prims, cells.get(grp, 0.35))
        fields[grp] = field
        meshes, raw = K.polygonize(field, targets)
        if verbose:
            print("  %-7s raw %d tris -> %s" % (grp, raw, [len(m[1]) for m in meshes]))
        for li, (v, f, n) in enumerate(meshes):
            ao = K.ambient_occlusion(field, v, n)
            bi, bw, near = K.skin_weights(v, prims, bones, s=0.9)
            adds = [p for p in prims if p.op == "add"]
            vmat = np.array([adds[i].mat for i in near])
            # triangle material = majority of its vertices
            tm = vmat[f[:, 0]]
            lods[li]["parts"].append(dict(v=v, n=n, f=f, bi=bi, bw=bw, ao=ao, tmat=tm, grp=grp,
                                          along=np.zeros(len(v)), eye=np.full(len(v), -1)))
    return sc, lods, fields


def rigid_part(v, f, n, bone, bones, mat, grp="body", ao=1.0, along=None, eye=-1):
    bi = np.zeros((len(v), 4), dtype=int)
    bi[:, 0] = bones.index(bone)
    bw = np.zeros((len(v), 4))
    bw[:, 0] = 1.0
    f = K.fix_winding(v, f, n)
    return dict(v=v, n=n, f=f, bi=bi, bw=bw, ao=np.full(len(v), ao) if np.isscalar(ao) else ao, tmat=np.array([mat] * len(f)),
                grp=grp, along=np.zeros(len(v)) if along is None else along, eye=np.full(len(v), eye))


def merge(parts):
    V, N, F, BI, BW, AO, TM, G, AL, EY = [], [], [], [], [], [], [], [], [], []
    off = 0
    for p in parts:
        V.append(p["v"])
        N.append(p["n"])
        F.append(p["f"] + off)
        bi = p["bi"]
        bw = p["bw"]
        if bi.shape[1] < 4:
            bi = np.concatenate([bi, np.zeros((len(bi), 4 - bi.shape[1]), dtype=int)], axis=1)
            bw = np.concatenate([bw, np.zeros((len(bw), 4 - bw.shape[1]))], axis=1)
        BI.append(bi)
        BW.append(bw)
        AO.append(p["ao"])
        AL.append(p["along"])
        EY.append(p["eye"])
        TM.extend(list(p["tmat"]))
        G.extend([p["grp"]] * len(p["f"]))
        off += len(p["v"])
    n = np.concatenate(N)
    n = n / np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
    return dict(v=np.concatenate(V), n=n, f=np.concatenate(F).astype(np.int64), bi=np.concatenate(BI), bw=np.concatenate(BW),
                ao=np.concatenate(AO), tmat=np.array(TM), grp=np.array(G), along=np.concatenate(AL), eye=np.concatenate(EY))


def tube_along(m_rings, n_sides, cap=True):
    a = np.repeat(np.linspace(0, 1, m_rings), n_sides)
    return np.concatenate([a, [1.0]] + ([[0.0]] if cap else []))
