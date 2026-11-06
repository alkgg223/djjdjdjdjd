"""Software preview renderer using the exact ModelPart math (orthographic, z-buffered, textured)."""
import math
import numpy as np
from geom import face_regions, face_point, FACE_NORMAL


def view_matrix(yaw, pitch):
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    return Rx @ Ry


def render(model, mats, tex, k, size=(360, 260), yaw=0.6, pitch=0.35, bg=(0.93, 0.94, 0.96), extra_lines=None, fit=None):
    W, H = size
    V = view_matrix(yaw, pitch)
    faces = []
    allpts = []
    for p in model.order:
        M = mats[p.name]
        for c in p.cubes:
            for face, (u0, v0, uw, vh) in face_regions(c).items():
                s = np.array([0.0, 1.0, 0.0])
                t = np.array([0.0, 0.0, 1.0])
                lx, ly, lz = face_point(c, face, s, t)
                L = np.stack([lx, ly, lz, np.ones(3)], axis=1)
                Pw = (M @ L.T).T[:, :3]
                Pw = Pw * np.array([1, -1, 1])   # y up for display
                nrm = M[:3, :3] @ np.array(FACE_NORMAL[face], dtype=float) * np.array([1, -1, 1])
                faces.append((Pw, nrm, (u0, v0, uw, vh)))
                allpts.append(Pw)
    pts = np.concatenate(allpts)
    view = (V @ pts.T).T
    if fit is None:
        mn, mx = view.min(axis=0), view.max(axis=0)
    else:
        mn, mx = fit
    span = max((mx[0] - mn[0]) / (W * 0.9), (mx[1] - mn[1]) / (H * 0.9), 1e-6)
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    img = np.ones((H, W, 3)) * np.array(bg)
    zbuf = np.full((H, W), -1e9)
    light = np.array([0.35, 0.85, 0.4])
    light = light / np.linalg.norm(light)
    TH, TW = tex.shape[0], tex.shape[1]
    for Pw, nrm, (u0, v0, uw, vh) in faces:
        Q = (V @ Pw.T).T
        sx = (Q[:, 0] - cx) / span + W / 2
        sy = H / 2 - (Q[:, 1] - cy) / span
        a, b, c2 = np.array([sx[0], sy[0]]), np.array([sx[1], sy[1]]), np.array([sx[2], sy[2]])
        e1, e2 = b - a, c2 - a
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        x0 = int(max(0, math.floor(min(sx[0], sx[1], sx[2], sx[1] + sx[2] - sx[0]))))
        x1 = int(min(W - 1, math.ceil(max(sx[0], sx[1], sx[2], sx[1] + sx[2] - sx[0]))))
        y0 = int(max(0, math.floor(min(sy[0], sy[1], sy[2], sy[1] + sy[2] - sy[0]))))
        y1 = int(min(H - 1, math.ceil(max(sy[0], sy[1], sy[2], sy[1] + sy[2] - sy[0]))))
        if x1 < x0 or y1 < y0:
            continue
        yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px = xx + 0.5 - a[0]
        py = yy + 0.5 - a[1]
        s = (px * e2[1] - py * e2[0]) / det
        t = (e1[0] * py - e1[1] * px) / det
        inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
        if not inside.any():
            continue
        z = Q[0, 2] + s * (Q[1, 2] - Q[0, 2]) + t * (Q[2, 2] - Q[0, 2])
        zs = -z  # camera looks along -z of view space; larger -z is nearer? keep consistent: nearer = larger value
        zs = z
        tu = np.clip(((u0 + s * uw) * k).astype(int), 0, TW - 1)
        tv = np.clip(((v0 + t * vh) * k).astype(int), 0, TH - 1)
        col = tex[tv, tu, :3]
        alpha = tex[tv, tu, 3]
        nview = V @ nrm
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(nrm / (np.linalg.norm(nrm) + 1e-9), light)))
        ok = inside & (alpha > 0.1) & (zs > zbuf[y0:y1 + 1, x0:x1 + 1])
        sub = img[y0:y1 + 1, x0:x1 + 1]
        sub[ok] = col[ok] * shade
        zb = zbuf[y0:y1 + 1, x0:x1 + 1]
        zb[ok] = zs[ok]
    return img


def ground_line(img, model, mats):
    return img
