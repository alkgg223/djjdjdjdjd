"""UV unwrap (xatlas) and texture bake for hero meshes."""
import math
import numpy as np
import xatlas


def unwrap(m, resolution=1024, padding=4):
    atlas = xatlas.Atlas()
    atlas.add_mesh(m["v"].astype(np.float32), m["f"].astype(np.uint32), m["n"].astype(np.float32))
    co = xatlas.ChartOptions()
    co.max_iterations = 4
    co.normal_deviation_weight = 2.0
    co.max_cost = 2.0
    po = xatlas.PackOptions()
    po.resolution = resolution
    po.padding = padding
    po.bilinear = True
    po.blockAlign = True
    atlas.generate(co, po)
    vmap, idx, uv = atlas[0]
    assert len(idx) == len(m["f"])
    out = {}
    for k, val in m.items():
        if isinstance(val, np.ndarray) and len(val) == len(m["v"]):
            out[k] = val[vmap]
        else:
            out[k] = val
    out["f"] = idx.astype(np.int64)
    out["uv"] = uv.astype(np.float64)
    return out


def bake(um, shade_fn, W, H, ctx_base=None, dilate=6, region_fn=None):
    img = np.zeros((H, W, 3))
    filled = np.zeros((H, W), dtype=bool)
    uv = um["uv"] * np.array([W, H])
    V, N, AO, F = um["v"], um["n"], um["ao"], um["f"]
    AL = um.get("along", np.zeros(len(V)))
    EY = um.get("eye", np.full(len(V), -1))
    jobs = {}
    for ti in range(len(F)):
        a, b, c = F[ti]
        pa, pb, pc = uv[a], uv[b], uv[c]
        x0 = int(max(0, math.floor(min(pa[0], pb[0], pc[0]) - 1)))
        x1 = int(min(W - 1, math.ceil(max(pa[0], pb[0], pc[0]) + 1)))
        y0 = int(max(0, math.floor(min(pa[1], pb[1], pc[1]) - 1)))
        y1 = int(min(H - 1, math.ceil(max(pa[1], pb[1], pc[1]) + 1)))
        if x1 < x0 or y1 < y0:
            continue
        d = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
        if abs(d) < 1e-12:
            continue
        yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px, py = xx.ravel() + 0.5, yy.ravel() + 0.5
        l1 = ((pb[1] - pc[1]) * (px - pc[0]) + (pc[0] - pb[0]) * (py - pc[1])) / d
        l2 = ((pc[1] - pa[1]) * (px - pc[0]) + (pa[0] - pc[0]) * (py - pc[1])) / d
        l3 = 1 - l1 - l2
        ins = (l1 >= -0.05) & (l2 >= -0.05) & (l3 >= -0.05)
        if not ins.any():
            continue
        l1, l2, l3 = np.clip(l1[ins], 0, 1), np.clip(l2[ins], 0, 1), np.clip(l3[ins], 0, 1)
        s = l1 + l2 + l3
        l1, l2, l3 = l1 / s, l2 / s, l3 / s
        P = V[a] * l1[:, None] + V[b] * l2[:, None] + V[c] * l3[:, None]
        Nn = N[a] * l1[:, None] + N[b] * l2[:, None] + N[c] * l3[:, None]
        A = AO[a] * l1 + AO[b] * l2 + AO[c] * l3
        al = AL[a] * l1 + AL[b] * l2 + AL[c] * l3
        reg = region_fn(int(um["bi"][a, 0])) if region_fn is not None else "torso"
        key = (um["tmat"][ti], um["grp"][ti], int(EY[a]), reg)
        jobs.setdefault(key, []).append((xx.ravel()[ins], yy.ravel()[ins], P, Nn, A, al))
    for (mat, grp, eye, reg), lst in jobs.items():
        X = np.concatenate([j[0] for j in lst])
        Y = np.concatenate([j[1] for j in lst])
        P = np.concatenate([j[2] for j in lst])
        Nn = np.concatenate([j[3] for j in lst])
        Nn /= np.maximum(np.linalg.norm(Nn, axis=1, keepdims=True), 1e-9)
        A = np.concatenate([j[4] for j in lst])
        al = np.concatenate([j[5] for j in lst])
        ctx = dict(ctx_base or {})
        ctx.update(grp=grp, along=al, region=np.array([reg] * len(P)))
        if eye >= 0:
            ctx["eye"] = ctx_base["eyes"][eye]
        img[Y, X] = shade_fn(mat, P, Nn, A, ctx)
        filled[Y, X] = True
    for _ in range(dilate):
        miss = ~filled
        if not miss.any():
            break
        acc = np.zeros_like(img)
        cnt = np.zeros((H, W))
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            acc += np.roll(np.roll(img, dy, 0), dx, 1) * np.roll(np.roll(filled, dy, 0), dx, 1)[:, :, None]
            cnt += np.roll(np.roll(filled, dy, 0), dx, 1)
        newf = miss & (cnt > 0)
        img[newf] = acc[newf] / cnt[newf][:, None]
        filled |= newf
    return img, filled
