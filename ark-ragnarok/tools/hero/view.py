"""Z-buffered software rasterizer for previews (G-space: y up, creature faces -z)."""
import math
import numpy as np


def camera(yaw, pitch):
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    Ry = np.array([[cy, 0, -sy], [0, 1, 0], [sy, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    return Rx @ Ry


def render(v, n, f, size=(640, 420), yaw=0.0, pitch=0.2, tex=None, uv=None, vcol=None, bg=(0.82, 0.86, 0.9),
           fit=None, light=(0.45, 0.8, -0.4), ground=True, ssaa=1):
    W, H = size[0] * ssaa, size[1] * ssaa
    R = camera(yaw, pitch)
    Q = v @ R.T
    Nq = n @ R.T
    L = np.array(light, dtype=float)
    L /= np.linalg.norm(L)
    Lq = R @ L
    if fit is None:
        mn, mx = Q.min(axis=0), Q.max(axis=0)
    else:
        mn, mx = fit
    span = max((mx[0] - mn[0]) / (W * 0.9), (mx[1] - mn[1]) / (H * 0.88), 1e-6)
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    S = np.stack([(Q[:, 0] - cx) / span + W / 2, H / 2 - (Q[:, 1] - cy) / span, Q[:, 2]], axis=1)
    # vertical gradient background
    t = np.linspace(0, 1, H)[:, None, None]
    img = np.ones((H, W, 3)) * (np.array(bg) * (1.05 - 0.15 * t))
    zb = np.full((H, W), 1e9)
    if tex is not None:
        TH, TW = tex.shape[0], tex.shape[1]
        UVp = uv * np.array([TW, TH])
    tri = S[f]
    xs, ys = tri[:, :, 0], tri[:, :, 1]
    bx0 = np.clip(np.floor(xs.min(axis=1)), 0, W - 1).astype(int)
    bx1 = np.clip(np.ceil(xs.max(axis=1)), 0, W - 1).astype(int)
    by0 = np.clip(np.floor(ys.min(axis=1)), 0, H - 1).astype(int)
    by1 = np.clip(np.ceil(ys.max(axis=1)), 0, H - 1).astype(int)
    for i in range(len(f)):
        if bx1[i] < bx0[i] or by1[i] < by0[i]:
            continue
        a, b, c = f[i]
        pa, pb, pc = S[a], S[b], S[c]
        d = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
        if abs(d) < 1e-9:
            continue
        yy, xx = np.mgrid[by0[i]:by1[i] + 1, bx0[i]:bx1[i] + 1]
        px, py = xx + 0.5, yy + 0.5
        l1 = ((pb[1] - pc[1]) * (px - pc[0]) + (pc[0] - pb[0]) * (py - pc[1])) / d
        l2 = ((pc[1] - pa[1]) * (px - pc[0]) + (pa[0] - pc[0]) * (py - pc[1])) / d
        l3 = 1 - l1 - l2
        ins = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4)
        if not ins.any():
            continue
        z = l1 * pa[2] + l2 * pb[2] + l3 * pc[2]
        sub = zb[by0[i]:by1[i] + 1, bx0[i]:bx1[i] + 1]
        ok = ins & (z < sub)
        if not ok.any():
            continue
        nn = Nq[a] * l1[..., None] + Nq[b] * l2[..., None] + Nq[c] * l3[..., None]
        nn /= np.maximum(np.linalg.norm(nn, axis=-1, keepdims=True), 1e-9)
        if (nn[..., 2] > 0).mean() > 0.5:   # back-facing -> flip for two-sided preview
            nn = -nn if False else nn
        diff = np.clip(nn @ Lq, 0, 1)
        fill = np.clip(nn @ (R @ np.array([-0.6, 0.3, 0.7])), 0, 1)
        shade = 0.38 + 0.62 * diff + 0.18 * fill
        if tex is not None:
            u = l1 * UVp[a, 0] + l2 * UVp[b, 0] + l3 * UVp[c, 0]
            vv = l1 * UVp[a, 1] + l2 * UVp[b, 1] + l3 * UVp[c, 1]
            col = tex[np.clip(vv.astype(int), 0, TH - 1), np.clip(u.astype(int), 0, TW - 1), :3]
        elif vcol is not None:
            col = vcol[a] * l1[..., None] + vcol[b] * l2[..., None] + vcol[c] * l3[..., None]
        else:
            col = np.ones(l1.shape + (3,)) * np.array([0.72, 0.7, 0.66])
        reg = img[by0[i]:by1[i] + 1, bx0[i]:bx1[i] + 1]
        reg[ok] = np.clip(col[ok] * shade[ok][:, None], 0, 1)
        sub[ok] = z[ok]
    if ssaa > 1:
        img = img.reshape(size[1], ssaa, size[0], ssaa, 3).mean(axis=(1, 3))
    return img


def sheet(tiles, cols):
    rows = [np.concatenate(tiles[i:i + cols], axis=1) for i in range(0, len(tiles), cols)]
    w = max(r.shape[1] for r in rows)
    rows = [np.pad(r, ((0, 0), (0, w - r.shape[1]), (0, 0)), constant_values=1) for r in rows]
    return np.concatenate(rows, axis=0)
