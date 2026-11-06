"""UV-unwrap a creature mesh (xatlas) and bake a clean painted texture from 3D shaders."""
import math
import numpy as np
import xatlas
from paint import hexrgb, fbm, value_noise, cellular, smooth, pattern_mask, Palette


def unwrap(mesh, res):
    atlas = xatlas.Atlas()
    atlas.add_mesh(mesh["v"].astype(np.float32), mesh["f"].astype(np.uint32), mesh["n"].astype(np.float32))
    co = xatlas.ChartOptions()
    co.max_iterations = 2
    po = xatlas.PackOptions()
    po.resolution = res
    po.padding = 3
    po.bilinear = True
    po.bruteForce = False
    atlas.generate(co, po)
    vmap, idx, uv = atlas[0]
    assert len(idx) == len(mesh["f"]), "xatlas changed the triangle count"
    out = dict(mesh)
    for k in ("v", "n", "ao", "bi", "bw"):
        out[k] = mesh[k][vmap]
    out["f"] = idx.astype(np.int64)
    out["uv"] = uv.astype(np.float64)
    out["atlas_w"], out["atlas_h"] = atlas.width, atlas.height
    return out


# ------------------------------------------------------------------ shading
def lum(c):
    return c @ np.array([0.3, 0.59, 0.11])


def saturate(c, amt):
    l = lum(c)[:, None]
    return np.clip(l + (c - l) * amt, 0, 1)


def shade(mat, P, N, AO, tri_prim, ctx):
    pal, sp, seed = ctx["pal"], ctx["sp"], ctx["seed"]
    n = len(P)
    ny = N[:, 1]
    wob = (fbm(P, 9.0, seed + 3, 2) - 0.5)
    low = smooth(-2.0, 3.0, P[:, 1] - ctx.get("center_y", 0.0))
    belly_t = smooth(0.2 + wob * 0.3, 0.55 + wob * 0.3, ny + sp.get("belly_bias", 0.0)) * (0.35 + 0.65 * low)
    dorsal_t = smooth(-0.2, -0.9, ny)
    pat = sp.get("pattern", "none")
    pscale = sp.get("pattern_scale", 1.0)

    def organic(base_key, fine):
        base = pal[base_key]
        top = pal["skin2"] * 0.6 + base * 0.4 if sp.get("dark_back", True) else base
        col = base[None, :] * (1 - dorsal_t[:, None] * 0.55) + top[None, :] * (dorsal_t[:, None] * 0.55)
        col = col * (1 - belly_t[:, None]) + pal["belly"][None, :] * belly_t[:, None]
        mask = pattern_mask(pat, P, N, pscale, seed, sp) * (1 - belly_t) * sp.get("pattern_strength", 0.9)
        pc = pal["skin2"]
        col = col * (1 - mask[:, None]) + pc[None, :] * mask[:, None]
        if fine == "scales":
            f1, f2, cid = cellular(P, sp.get("scale_size", 1.1), seed + 5)
            edge = smooth(0.0, 0.18, f2 - f1)
            col = col * (0.93 + 0.07 * edge)[:, None]
            # larger bony scutes along the back
            g1, g2, gid = cellular(P, sp.get("scute_size", 2.6), seed + 6)
            scute = (1 - smooth(0.0, 0.12, g2 - g1)) * dorsal_t
            col = col * (1 - 0.1 * scute)[:, None]
        elif fine == "fur":
            streak = value_noise(P * np.array([5.0, 0.9, 5.0]), 1.0, seed + 11)
            col = col * (0.88 + 0.14 * streak)[:, None]
        elif fine == "feather":
            barb = 0.5 + 0.5 * np.sin((P[:, 2] * 1.6 + np.abs(P[:, 0]) * 0.8 + P[:, 1] * 0.5) * 2.2)
            col = col * (0.9 + 0.1 * barb)[:, None]
        elif fine == "smooth":
            col = col * (0.96 + 0.05 * fbm(P, 3.0, seed + 9, 2))[:, None]
        elif fine == "chitin":
            seg = 0.5 + 0.5 * np.cos(P[:, 2] * 1.4)
            col = col * (0.85 + 0.15 * seg)[:, None]
            col = col + (smooth(-0.6, -1.0, ny) * 0.12)[:, None]
        col = col * (0.97 + 0.04 * fbm(P, 14.0, seed + 31, 2))[:, None]
        return col

    skin_type = sp.get("skin_type", "scales")
    if mat in ("skin", "skin2", "belly", "head"):
        col = organic("skin" if mat != "skin2" else "skin2", skin_type)
    elif mat in ("fur", "fur2", "mane"):
        col = organic(mat if mat in pal.d else "fur", "fur")
    elif mat == "feather":
        col = organic("feather", "feather")
        tips = smooth(0.2, 1.0, value_noise(P, 2.5, seed + 77))
        col = col * (1 - tips[:, None] * 0.12)
    elif mat == "chitin":
        col = organic("chitin", "chitin")
    elif mat in ("shell", "plate"):
        base = pal[mat]
        f1, f2, cid = cellular(P, 3.0 if mat == "shell" else 2.2, seed + 51)
        edge = smooth(0.0, 0.14, f2 - f1)
        col = base[None, :] * (0.62 + 0.38 * edge[:, None]) * (0.92 + 0.12 * cid[:, None])
        col = col * (1 - belly_t[:, None] * 0.25)
    elif mat in ("horn", "tusk", "bone", "beak", "claw", "hoof"):
        base = pal[mat]
        tipc = base * (0.45 if mat in ("horn", "beak", "bone") else (0.75 if mat == "tusk" else 0.55))
        t = along(P, tri_prim, ctx)
        col = base[None, :] * (1 - t[:, None]) + tipc[None, :] * t[:, None]
        col = col * (0.93 + 0.08 * value_noise(P * np.array([4, 0.6, 4]), 1.0, seed + 61))[:, None]
    elif mat in ("membrane", "frill", "sail", "fin", "insectwing"):
        base = pal[mat]
        veins = np.abs(np.sin(P[:, 0] * 0.55 + fbm(P, 6, seed + 41) * 2.5))
        vein = smooth(0.0, 0.12, veins)
        col = base[None, :] * (0.78 + 0.22 * vein[:, None])
        if mat in ("frill", "sail"):
            mk = pattern_mask(sp.get("frill_pattern", "spots"), P, N, 0.7, seed + 7, sp)
            col = col * (1 - mk[:, None] * 0.55) + pal["skin2"][None, :] * (mk[:, None] * 0.55)
        if mat == "insectwing":
            f1, f2, _ = cellular(P, 1.6, seed + 61)
            col = col * (0.6 + 0.4 * smooth(0.0, 0.1, f2 - f1))[:, None]
    elif mat == "eye":
        col = eye_color(P, tri_prim, ctx)
        AO = np.ones(n)
    elif mat == "saddle":
        base = pal["saddle"]
        grain = fbm(P * 1.5, 1.0, seed + 71, 2)
        col = base[None, :] * (0.8 + 0.3 * grain[:, None])
        stitch = (np.sin(P[:, 2] * 3.0) > 0.6) & (np.abs(np.abs(P[:, 0]) % 3.0 - 1.5) < 0.18)
        col[stitch] = np.array([0.86, 0.78, 0.58])
    elif mat in ("glow", "fire", "lava"):
        col = pal[mat][None, :] * (0.8 + 0.4 * fbm(P, 1.6, seed + 81, 3))[:, None]
        AO = np.ones(n)
    elif mat == "ice":
        f1, f2, _ = cellular(P, 2.2, seed + 83)
        col = pal["ice"][None, :] * (0.78 + 0.25 * smooth(0.0, 0.08, f2 - f1))[:, None] + 0.05
    elif mat == "rock":
        f1, f2, cid = cellular(P, 3.2, seed + 85)
        crack = smooth(0.0, 0.1, f2 - f1)
        col = pal["rock"][None, :] * (0.7 + 0.3 * crack[:, None]) * (0.9 + 0.2 * cid[:, None])
        if sp.get("lava_cracks"):
            lv = 1 - smooth(0.0, 0.07, f2 - f1)
            col = col * (1 - lv[:, None]) + pal["lava"][None, :] * lv[:, None]
        if sp.get("crystal_veins"):
            lv = 1 - smooth(0.0, 0.05, f2 - f1)
            col = col * (1 - lv[:, None]) + pal["glow"][None, :] * lv[:, None]
    elif mat in ("metal", "tek"):
        col = pal[mat][None, :] * (0.88 + 0.12 * value_noise(P, 2.0, seed + 91))[:, None]
        grid = (np.abs((P[:, 2] % 5.0) - 2.5) > 2.3) | (np.abs((P[:, 1] % 5.0) - 2.5) > 2.3)
        col[grid] *= 0.72
        if mat == "tek":
            line = np.abs((P[:, 2] + P[:, 0]) % 7.0 - 3.5) < 0.25
            col[line] = pal["glow"]
    elif mat == "egg":
        sp_ = pattern_mask("speckle", P, N, 0.5, seed + 99, sp)
        col = pal["egg"][None, :] * (1 - sp_[:, None] * 0.4)
    elif mat == "mouth":
        col = np.tile(pal["mouth"], (n, 1))
    else:
        col = np.tile(pal[mat], (n, 1))
    ao_strength = sp.get("ao", 0.6)
    col = col * (1 - ao_strength + ao_strength * AO)[:, None]
    col = saturate(col, sp.get("saturation", 1.22))
    return np.clip(col, 0, 1)


def along(P, tri_prim, ctx):
    prims = ctx["prims"]
    t = np.zeros(len(P))
    for pid in np.unique(tri_prim):
        if pid < 0 or pid >= len(prims):
            continue
        p = prims[pid]
        sel = tri_prim == pid
        q = (P[sel] - p.t) @ p.R - p.center
        ax = int(np.argmax(p.half))
        e = q[:, ax] / max(p.half[ax], 1e-6)
        base_sign = -1 if np.linalg.norm(p.center - np.eye(3)[ax] * p.half[ax]) < np.linalg.norm(p.center + np.eye(3)[ax] * p.half[ax]) else 1
        t[sel] = np.clip((e * -base_sign + 1) / 2.0, 0, 1) ** 1.3
    return t


def eye_color(P, tri_prim, ctx):
    pal, sp = ctx["pal"], ctx["sp"]
    col = np.tile(np.array([0.08, 0.06, 0.05]), (len(P), 1))
    for pid in np.unique(tri_prim):
        k = pid - 1000
        if k < 0 or k >= len(ctx["eyes"]):
            continue
        c, fwd = ctx["eyes"][k]
        fwd = fwd / np.linalg.norm(fwd)
        sel = tri_prim == pid
        d = P[sel] - c
        d /= np.maximum(np.linalg.norm(d, axis=1, keepdims=True), 1e-9)
        cs = d @ fwd
        iris = smooth(0.35, 0.45, cs)
        cc = col[sel] * (1 - iris[:, None]) + pal["eye"][None, :] * iris[:, None]
        # radial iris detail
        cc = cc * (0.85 + 0.15 * smooth(0.6, 0.95, cs))[:, None]
        if sp.get("slit_pupils", True):
            side = np.cross(fwd, np.array([0, 1.0, 0]))
            side /= max(np.linalg.norm(side), 1e-9)
            pupil = (np.abs(d @ side) < 0.12) & (cs > 0.55)
        else:
            pupil = cs > 0.86
        cc[pupil] = np.array([0.02, 0.02, 0.02])
        up = np.array([0, -1.0, 0])
        hl = (d @ (fwd * 0.75 + up * 0.5 + np.cross(fwd, up) * 0.25)) > 0.93
        cc[hl] = np.array([0.95, 0.95, 0.95])
        col[sel] = cc
    return col


# ------------------------------------------------------------------ bake
def bake(um, ctx, k=1):
    W, H = um["atlas_w"] * k, um["atlas_h"] * k
    img = np.zeros((H, W, 3))
    glow = np.zeros((H, W, 4))
    filled = np.zeros((H, W), dtype=bool)
    uv = um["uv"] * np.array([W, H])
    V, N, AO, F = um["v"], um["n"], um["ao"], um["f"]
    mats = um["mat"]
    prims = um["prim"]
    grp = um["grp"]
    # group triangles by material for vectorised shading
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
        yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px, py = xx.ravel() + 0.5, yy.ravel() + 0.5
        d = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
        if abs(d) < 1e-12:
            continue
        l1 = ((pb[1] - pc[1]) * (px - pc[0]) + (pc[0] - pb[0]) * (py - pc[1])) / d
        l2 = ((pc[1] - pa[1]) * (px - pc[0]) + (pa[0] - pc[0]) * (py - pc[1])) / d
        l3 = 1 - l1 - l2
        eps = -0.08
        ins = (l1 >= eps) & (l2 >= eps) & (l3 >= eps)
        if not ins.any():
            continue
        l1, l2, l3 = np.clip(l1[ins], 0, 1), np.clip(l2[ins], 0, 1), np.clip(l3[ins], 0, 1)
        s = l1 + l2 + l3
        l1, l2, l3 = l1 / s, l2 / s, l3 / s
        P = V[a] * l1[:, None] + V[b] * l2[:, None] + V[c] * l3[:, None]
        Nn = N[a] * l1[:, None] + N[b] * l2[:, None] + N[c] * l3[:, None]
        A = AO[a] * l1 + AO[b] * l2 + AO[c] * l3
        key = (mats[ti], grp[ti] == "glow")
        jobs.setdefault(key, []).append((xx.ravel()[ins], yy.ravel()[ins], P, Nn, A, np.full(ins.sum(), prims[ti])))
    for (mat, is_glow), lst in jobs.items():
        X = np.concatenate([j[0] for j in lst])
        Y = np.concatenate([j[1] for j in lst])
        P = np.concatenate([j[2] for j in lst])
        Nn = np.concatenate([j[3] for j in lst])
        Nn /= np.maximum(np.linalg.norm(Nn, axis=1, keepdims=True), 1e-9)
        A = np.concatenate([j[4] for j in lst])
        TP = np.concatenate([j[5] for j in lst])
        col = shade(mat, P, Nn, A, TP, ctx)
        img[Y, X] = col
        filled[Y, X] = True
        if is_glow or mat in ctx["sp"].get("glow_mats", ("glow", "fire", "lava")):
            glow[Y, X, :3] = col
            glow[Y, X, 3] = 1
    # dilate to hide seams
    for _ in range(4):
        miss = ~filled
        if not miss.any():
            break
        acc = np.zeros_like(img)
        cnt = np.zeros((H, W))
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sh = np.roll(np.roll(img, dy, 0), dx, 1)
            sf = np.roll(np.roll(filled, dy, 0), dx, 1)
            acc += sh * sf[:, :, None]
            cnt += sf
        newf = miss & (cnt > 0)
        img[newf] = acc[newf] / cnt[newf][:, None]
        filled |= newf
    rgba = np.concatenate([img, np.ones((H, W, 1))], axis=2)
    return rgba, glow


def preview(um, tex, mats_pose=None, verts=None, norms=None, size=(420, 300), yaw=0.6, pitch=0.3, bg=(0.92, 0.93, 0.95),
            groups=("body", "glow", "saddle"), fit=None):
    W, H = size
    V = verts if verts is not None else um["v"]
    Nv = norms if norms is not None else um["n"]
    cy_, sy_ = math.cos(yaw), math.sin(yaw)
    cp, spp = math.cos(pitch), math.sin(pitch)
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -spp], [0, spp, cp]])
    Vm = Rx @ Ry
    flip = np.array([1, -1, 1])
    Q = ((V * flip) @ Vm.T)
    Nq = ((Nv * flip) @ Vm.T)
    sel = np.isin(um["grp"], groups)
    F = um["f"][sel]
    pts = Q[np.unique(F)]
    mn, mx = (pts.min(axis=0), pts.max(axis=0)) if fit is None else fit
    span = max((mx[0] - mn[0]) / (W * 0.92), (mx[1] - mn[1]) / (H * 0.92), 1e-6)
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    S = np.stack([(Q[:, 0] - cx) / span + W / 2, H / 2 - (Q[:, 1] - cy) / span, Q[:, 2]], axis=1)
    img = np.ones((H, W, 3)) * np.array(bg)
    zb = np.full((H, W), -1e9)
    TH, TW = tex.shape[0], tex.shape[1]
    UV = um["uv"] * np.array([TW, TH])
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    Lw = (light * flip) @ Vm.T * flip
    for (a, b, c) in F:
        pa, pb, pc = S[a], S[b], S[c]
        x0 = int(max(0, math.floor(min(pa[0], pb[0], pc[0]))))
        x1 = int(min(W - 1, math.ceil(max(pa[0], pb[0], pc[0]))))
        y0 = int(max(0, math.floor(min(pa[1], pb[1], pc[1]))))
        y1 = int(min(H - 1, math.ceil(max(pa[1], pb[1], pc[1]))))
        if x1 < x0 or y1 < y0:
            continue
        d = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
        if abs(d) < 1e-9:
            continue
        yy, xx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px, py = xx + 0.5, yy + 0.5
        l1 = ((pb[1] - pc[1]) * (px - pc[0]) + (pc[0] - pb[0]) * (py - pc[1])) / d
        l2 = ((pc[1] - pa[1]) * (px - pc[0]) + (pa[0] - pc[0]) * (py - pc[1])) / d
        l3 = 1 - l1 - l2
        ins = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
        if not ins.any():
            continue
        z = l1 * pa[2] + l2 * pb[2] + l3 * pc[2]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        ok = ins & (z > sub)
        if not ok.any():
            continue
        u = l1 * UV[a, 0] + l2 * UV[b, 0] + l3 * UV[c, 0]
        v = l1 * UV[a, 1] + l2 * UV[b, 1] + l3 * UV[c, 1]
        tu = np.clip(u.astype(int), 0, TW - 1)
        tv = np.clip(v.astype(int), 0, TH - 1)
        nrm = Nq[a] * l1[..., None] + Nq[b] * l2[..., None] + Nq[c] * l3[..., None]
        nrm /= np.maximum(np.linalg.norm(nrm, axis=-1, keepdims=True), 1e-9)
        lam = np.abs(nrm @ np.array([0.35, 0.85, 0.4]) / np.linalg.norm([0.35, 0.85, 0.4]))
        shadev = 0.5 + 0.5 * np.clip(nrm @ (np.array([0.35, 0.85, 0.4]) / np.linalg.norm([0.35, 0.85, 0.4])), 0, 1)
        shadev = shadev + 0.12 * np.clip(nrm @ np.array([-0.5, 0.2, -0.6]), 0, 1)
        col = tex[tv, tu, :3] * np.clip(shadev, 0, 1.1)[..., None]
        reg = img[y0:y1 + 1, x0:x1 + 1]
        reg[ok] = np.clip(col[ok], 0, 1)
        sub[ok] = z[ok]
    return img
