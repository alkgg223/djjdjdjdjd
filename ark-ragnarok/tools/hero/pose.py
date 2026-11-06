"""Bone matrices exactly like GeckoLib's RenderUtil.prepMatrixForBone, and linear blend skinning (same as the Java renderer)."""
import math
import numpy as np
from sdfkit import rot_euler


def bone_mats(sc, pose):
    mats = {}
    for name in sc.bone_order:
        par, piv = sc.bones[name]
        e = pose.get(name, [0.0] * 9)
        rx, ry, rz = (math.radians(a) for a in e[:3])
        T = np.eye(4)
        T[:3, 3] = np.array(e[3:6]) + piv
        R = np.eye(4)
        R[:3, :3] = rot_euler(rx, ry, rz)           # Rz * Ry * Rx
        Sm = np.diag([1 + e[6], 1 + e[7], 1 + e[8], 1.0])
        Ti = np.eye(4)
        Ti[:3, 3] = -piv
        M = T @ R @ Sm @ Ti
        mats[name] = (mats[par] @ M) if par else M
    return mats


def skin(um, sc, pose):
    mats = bone_mats(sc, pose)
    Ms = np.array([mats[b] for b in sc.bone_order])
    v = np.concatenate([um["v"], np.ones((len(um["v"]), 1))], axis=1)
    out = np.zeros((len(v), 3))
    nout = np.zeros((len(v), 3))
    for k in range(um["bi"].shape[1]):
        w = um["bw"][:, k]
        if not np.any(w):
            continue
        Mk = Ms[um["bi"][:, k]]
        out += w[:, None] * np.einsum("nij,nj->ni", Mk, v)[:, :3]
        nout += w[:, None] * np.einsum("nij,nj->ni", Mk[:, :3, :3], um["n"])
    nout /= np.maximum(np.linalg.norm(nout, axis=1, keepdims=True), 1e-9)
    return out, nout
