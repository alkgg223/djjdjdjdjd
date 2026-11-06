"""Species surface painter (rest pose, GeckoLib model pixels).  Generalises the hero T-Rex painter: colours and
patterns per species, skin types (scales / feathers / smooth / armour), regions found through each texel's bone."""
import math
import numpy as np
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, scale_relief, wrinkles, LIGHT

REX_PAL = dict(
    dorsal="#4a4128", flank="#8a7347", belly="#dccb9e", throat="#d2b48a", stripe="#2a2216", leg="#6c5c3a", foot="#4c4433",
    accent="#7e3a22", mask="#33291b", snout="#7a6640", mouth="#6c2a27", gum="#8a3a36", tongue="#b0566a", tooth="#ece2c6",
    tooth_base="#b9a374", claw="#2a241e", claw_base="#5d4d3a", iris="#d89b1f", iris_dark="#7a4a0c", lid="#3b3222",
    horn="#332a1c", scar="#c9a38e", osteo="#2f291a", frill="#9a3b22", sail="#a8442a", plate="#6b5a3a", beak="#3a3128",
    feather="#6b5a3d", feather2="#2f2618",
)


# materials that share the skin surface and get soft, per-texel borders (instead of per-triangle steps)
SOFT = ("lid", "horn_skin", "beak", "beak2")


def soft_weights(P, prims):
    """per-texel weight of each soft material: like the nearest-primitive material choice of the mesher, but with a
    smooth border (the distance to the material's primitives against the distance to every other primitive)"""
    n = len(P)
    big = 1e6
    Dm = {m: np.full(n, big) for m in SOFT}
    Km = {m: np.full(n, 1.0) for m in SOFT}
    Do = np.full(n, big)
    for p in prims:
        lo, hi = p.bbox
        pad = 2.0 * p.k + 1.5
        sel = np.all((P > lo - pad) & (P < hi + pad), axis=1)
        if not sel.any():
            continue
        d = np.full(n, big)
        d[sel] = p.sdf(P[sel])
        if p.mat in Dm:
            closer = d < Dm[p.mat]
            Km[p.mat] = np.where(closer, p.k, Km[p.mat])
            Dm[p.mat] = np.minimum(Dm[p.mat], d)
        else:
            Do = np.minimum(Do, d)
    out = {}
    for m in SOFT:
        if not (Dm[m] < big).any():
            out[m] = None
            continue
        other = Do.copy()
        for m2 in SOFT:
            if m2 != m:
                other = np.minimum(other, Dm[m2])
        tau = 0.15 + 0.22 * Km[m]
        out[m] = sm(tau, -tau, Dm[m] - other)
    return out


def cells_id(P, scale, seed):
    """F1, F2 and a per-cell random id"""
    f1, f2, vec = cells(P, scale, seed)
    fp = P / scale - vec
    cid = np.abs(np.sin(fp @ np.array([12.9898, 78.233, 37.719])) * 43758.5453) % 1.0
    return f1, f2, cid


def palette(over):
    p = dict(REX_PAL)
    p.update(over or {})
    return {k: hx(v) if isinstance(v, str) else v for k, v in p.items()}


def stripes(P, N, seed, scale, kind):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    warp = fbm(P, 7.0 * scale, seed + 5, 3) * 7.0 * scale
    if kind == "stripes":
        band = np.sin((z + warp + (60 - y) * 0.35) * 0.42 / scale)
        m = sm(0.62, 0.74, band) * sm(0.3, 0.55, fbm(P, 2.2 * scale, seed + 13, 2) + 0.15)
    elif kind == "bands":
        band = np.sin((z + warp * 0.6) * 0.3 / scale)
        m = sm(0.35, 0.55, band)
    elif kind == "spots":
        f1, f2, cid = cells_id(P, 3.2 * scale, seed + 103)
        m = 1 - sm(0.22, 0.36, f1)
    elif kind == "rosettes":
        f1, f2, cid = cells_id(P, 3.6 * scale, seed + 104)
        m = 1 - sm(0.04, 0.1, np.abs(f1 - 0.32))
    elif kind == "mottled":
        m = sm(0.5, 0.62, fbm(P, 4.0 * scale, seed + 105, 3))
    elif kind == "chevrons":
        m = sm(0.55, 0.8, np.sin(z * 0.5 / scale + np.abs(x) * 0.5 / scale + warp * 0.3))
    elif kind == "blotch":
        f1, f2, cid = cells_id(P, 6.0 * scale, seed + 108)
        m = (1 - sm(0.3, 0.45, f1)) * (cid > 0.4)
    elif kind == "saddleback":
        m = sm(0.2, 0.8, N[:, 1]) * (1 - sm(4.0, 10.0, np.abs(z - 0.0) / scale))
    else:
        m = np.zeros(len(P))
    return m * sm(-0.35, 0.55, N[:, 1])


def feather_relief(P, N, seed, size):
    """overlapping feathers: elongated cells along the body axis, each brighter at its root, with a rachis line"""
    q = P * np.array([1.0, 1.0, 0.55])
    f1, f2, vec = cells(q, size, seed)
    edge = sm(0.0, 0.25, f2 - f1)
    along = np.clip(-vec[:, 2] / 1.2 + 0.5, 0, 1)        # front of the cell (towards the head) is lighter
    rachis = 1 - sm(0.0, 0.05, np.abs(vec[:, 0])) * 0.25
    return (0.78 + 0.22 * along) * (0.86 + 0.14 * edge) * rachis


def wrinkles2(P, N, reg, ctx, seed):
    """skin folds across the neck and rings of folds around the knees, ankles and elbows"""
    out = np.ones(len(P))
    jitter = fbm(P, 3.0, seed + 41, 2) * 2.0
    nd = ctx.get("neck_dir")
    if nd is not None:
        neck = (reg == "neck").astype(float) * (0.35 + 0.65 * sm(0.3, -0.8, N[:, 1]))
        g = np.abs(np.sin((P @ nd + jitter) * 1.25 / ctx["detail"]))
        out *= 1 - 0.34 * neck * (1 - sm(0.0, 0.22, g))
    for c, r in ctx.get("joints", ()):
        d = np.linalg.norm(P - np.array(c), axis=1)
        w = 1 - sm(r * 0.6, r * 1.25, d)
        g = np.abs(np.sin(d * 2.2 / ctx["detail"] + jitter * 0.6))
        out *= 1 - 0.28 * w * (1 - sm(0.0, 0.25, g))
    return out


def shade(mat, P, N, AO, ctx):
    sp = ctx["spec"]
    pal = ctx["pal"]
    seed = sp.get("seed", 1234)
    ps = sp.get("pattern_scale", 1.0)
    n = len(P)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    ny = N[:, 1]
    reg = ctx.get("region", np.array(["torso"] * n))
    head = (reg == "head").astype(float)
    legs = (reg == "leg").astype(float)
    if mat in ("skin", "lid", "horn_skin"):
        dorsal = sm(-0.05, 0.85, ny)
        ventral = sm(-0.15, -0.8, ny)
        col = mixc(pal["flank"], pal["dorsal"], dorsal * 0.85)
        lowleg = legs * sm(ctx["knee_y"], ctx["knee_y"] * 0.4, y)
        col = mixc(col, pal["leg"], legs * 0.5 + lowleg * 0.4)
        col = mixc(col, pal["foot"], legs * sm(ctx["knee_y"] * 0.35, 0.5, y))
        bel = ventral * (1 - legs * 0.7)
        col = mixc(col, mixc(pal["belly"], pal["throat"], (reg == "neck").astype(float)), bel)
        # head: darker mask through the eye, accent brow, snout colour
        ey, ez = ctx["eye_y"], ctx["eye_z"]
        hs = ctx["head_s"]
        mask = head * (1 - sm(1.6 * hs, 3.4 * hs, np.abs(y - ey + 0.12 * (z - ez)))) * sm(ez - 9 * hs, ez - 3 * hs, z) * sp.get("mask", 1.0)
        col = mixc(col, pal["mask"], mask * 0.45)
        # a faint accent on the brow ridge only (a bright cap over the whole skull looks like a toy)
        brow = head * sm(ey + 0.8 * hs, ey + 1.8 * hs, y) * (1 - sm(ey + 3.0 * hs, ey + 4.2 * hs, y)) \
            * (1 - sm(2.0 * hs, 3.5 * hs, np.abs(z - ez))) * sm(ctx["eye_x"] * 0.45, ctx["eye_x"] * 0.8, np.abs(x))
        col = mixc(col, pal["accent"], brow * sp.get("brow", 0.35))
        st = stripes(P, N, seed, ps, sp.get("pattern", "stripes")) * (1 - bel) * (1 - head * 0.85)
        col = mixc(col, pal["stripe"], st * sp.get("pattern_strength", 0.75))
        if sp.get("pattern2"):
            st2 = stripes(P, N, seed + 77, ps * 1.7, sp["pattern2"]) * (1 - bel)
            col = mixc(col, pal.get("stripe2", pal["accent"]), st2 * 0.6)
        col = col * (0.9 + 0.2 * fbm(P, 9.0 * ps, seed + 77, 3))[:, None]
        col = col * (0.96 + 0.08 * vnoise(P, 1.3, seed + 3))[:, None]
        skin = sp.get("skin", "scales")
        feathered = 0.0
        if sp.get("feathers"):
            fmask = (reg != "head") & (reg != "leg") if sp["feathers"] == "full" else ((reg == "neck") | ((reg == "torso") & (ny > -0.2)))
            if sp["feathers"] == "full":
                fmask = fmask | ((reg == "leg") & (y > ctx["knee_y"]))
            if sp.get("hood") is not None and mat == "skin":
                # feathered hood: the back of the head is feathered, the face in front of the eyes stays bare
                fmask = fmask | ((reg == "head") & (z > ctx["eye_z"] + sp["hood"] * ctx["head_s"]))
            feathered = fmask.astype(float)
        fs = sp.get("scale_size", 1.0) * ctx["detail"]
        big, _ = scale_relief(P, N, 2.1 * fs, seed + 101, 1.0)
        mid, _ = scale_relief(P, N, 1.25 * fs, seed + 102, 0.9)
        small, _ = scale_relief(P, N, 0.72 * fs, seed + 103, 0.8)
        if skin == "smooth":
            big = mid = 0.94 + 0.06 * (big - 0.5)
        w_big = dorsal * (1 - head) * (1 - legs)
        w_small = np.clip(head + legs * 0.8 + ventral * 0.3, 0, 1)
        w_mid = np.clip(1 - w_big - w_small, 0, 1)
        relief = (big * w_big + mid * w_mid + small * w_small) / np.maximum(w_big + w_mid + w_small, 1e-6)
        scute = np.abs(np.sin(z * math.pi / (1.7 * fs) + fbm(P, 4, seed + 7) * 1.2))
        relief = mixc(relief[:, None], (1 - 0.22 * (1 - sm(0.0, 0.2, scute)))[:, None], bel * 0.75)[:, 0]
        if np.any(feathered > 0):
            fr = feather_relief(P, N, seed + 201, 1.3 * ctx["detail"] * sp.get("feather_size", 1.0))
            fcol = mixc(pal["feather"], pal["feather2"], (1 - fr) * 1.4)
            fcol = mixc(fcol, pal["stripe"], st * 0.55)
            fcol = mixc(fcol, pal["belly"], bel * 0.6)
            col = mixc(col, fcol, feathered * 0.88)
            relief = relief * (1 - feathered) + (0.9 + 0.1 * fr) * feathered
        col = col * relief[:, None]
        if ctx.get("rex_wrinkles"):
            col = col * wrinkles(P, N, seed)[:, None]
        else:
            col = col * wrinkles2(P, N, reg, ctx, seed)[:, None]
        if sp.get("scars"):
            from rex_paint import scars
            col = mixc(col, pal["scar"], scars(P) * 0.85)
        soft = ctx.get("soft", {}).get(ctx.get("grp")) if mat == "skin" else None
        ws = soft_weights(P, soft) if soft else {m: None for m in SOFT}
        if mat == "lid":
            ws["lid"] = np.ones(n)
        if mat == "horn_skin":
            ws["horn_skin"] = np.ones(n)
        if ws["lid"] is not None:
            col = mixc(col, pal["lid"], ws["lid"] * sp.get("lid_mix", 0.6))
        if ws["horn_skin"] is not None:
            horn = mixc(col, pal["horn"], 0.55) * (0.8 + 0.25 * fbm(P, 0.6, seed + 55, 2))[:, None]
            col = mixc(col, horn, ws["horn_skin"])
        for m in ("beak", "beak2"):
            if ws[m] is not None and ws[m].max() > 0.005:
                bc = np.tile(pal.get(m, pal["beak"]), (n, 1)) * (0.85 + 0.2 * vnoise(P * np.array([3, 1, 3]), 0.8, 71))[:, None]
                bc = bc + (sm(0.6, 0.95, (N * LIGHT).sum(axis=1)) * 0.1)[:, None]
                col = mixc(col, bc, ws[m])
        mw = ctx["eye_x"] * 0.5
        nb = np.ones(n)
        for m in ("beak", "beak2"):
            if ws[m] is not None:
                nb = nb * (1 - ws[m])
        if ctx.get("grp") == "body":
            # the mouth colour only where it is hidden inside the mouth (low occlusion), never on the visible face
            palate = head * nb * sm(0.62, 0.4, AO) * sm(-0.55, -0.85, ny) * (np.abs(x) < mw) * (y < ctx["jaw_y"] + 0.4 * hs) * (z < ctx["jaw_z"])
            col = mixc(col, pal["mouth"] * (0.85 + 0.15 * np.abs(np.sin(z * 2.4)))[:, None], palate)
        elif ctx.get("grp") == "jaw":
            floor = nb * sm(0.62, 0.4, AO) * sm(0.45, 0.8, ny) * (np.abs(x) < mw * 0.8) * (y > ctx["jaw_y"] - 0.2 * hs)
            col = mixc(col, pal["mouth"], floor)
    elif mat == "tongue":
        col = pal["tongue"][None, :] * (1 - 0.3 * (1 - sm(0.0, 0.35, np.abs(x))))[:, None] * (0.9 + 0.15 * vnoise(P, 0.5, 9))[:, None]
    elif mat == "tooth":
        col = mixc(np.tile(pal["tooth_base"], (n, 1)), np.tile(pal["tooth"], (n, 1)), sm(0.0, 0.45, ctx["along"]))
        AO = np.maximum(AO, 0.8)
    elif mat in ("claw", "hoof"):
        col = mixc(np.tile(pal["claw_base"], (n, 1)), np.tile(pal["claw"], (n, 1)), sm(0.0, 0.6, ctx["along"]))
        col = col + (sm(0.5, 0.95, (N * LIGHT).sum(axis=1)) * 0.12)[:, None]
        AO = np.maximum(AO, 0.8)
    elif mat == "horn":
        t = ctx["along"]
        base = pal["horn"] * 1.6
        col = mixc(np.tile(np.clip(base, 0, 1), (n, 1)), np.tile(pal["horn"] * 0.7, (n, 1)), sm(0.2, 1.0, t) * 0.8 + 0.1)
        col = col * (0.9 + 0.15 * vnoise(P * np.array([4, 0.6, 4]), 1.0, 61))[:, None]
        AO = np.maximum(AO, 0.75)
    elif mat in ("beak", "beak2"):
        col = np.tile(pal.get(mat, pal["beak"]), (n, 1)) * (0.85 + 0.2 * vnoise(P * np.array([3, 1, 3]), 0.8, 71))[:, None]
        col = col + (sm(0.6, 0.95, (N * LIGHT).sum(axis=1)) * 0.1)[:, None]
    elif mat in ("frill", "sail"):
        base = pal[mat]
        rad = np.abs(np.sin(np.arctan2(y - ctx["eye_y"], z - ctx["eye_z"]) * 11)) if mat == "frill" else np.abs(np.sin(z * 0.9))
        col = base[None, :] * (0.78 + 0.22 * sm(0.0, 0.25, rad))[:, None]
        spots = stripes(P, N, seed + 7, 0.6 * ps, sp.get("frill_pattern", "spots"))
        col = mixc(col, pal["stripe"], spots * 0.45)
        edge = sm(0.65, 1.0, fbm(P, 3.0, seed + 41, 2))
        col = mixc(col, pal["accent"], edge * 0.35)
    elif mat == "plate":
        base = pal["plate"]
        f1, f2, cid = cells_id(P, 2.2, seed + 51)
        col = base[None, :] * (0.65 + 0.35 * sm(0.0, 0.14, f2 - f1)[:, None]) * (0.92 + 0.12 * cid[:, None])
        col = mixc(col, pal["accent"], sm(0.6, 1.0, (y - y.min()) / max(np.ptp(y), 1e-6)) * 0.5)
    elif mat == "plume":
        fr = feather_relief(P * 2.6, N, seed + 331, 1.0)
        col = mixc(pal.get("plume", pal["feather"]), pal.get("plume2", pal["feather2"]), (1 - fr) * 0.9)
        AO = np.maximum(AO, 0.7)
    elif mat == "feather":
        fr = feather_relief(P * 2.0, N, seed + 301, 1.0)
        col = mixc(pal["feather"], pal["feather2"], (1 - fr) * 1.5)
        vane = 0.85 + 0.15 * np.abs(np.sin((x + z) * 6.0))
        col = col * vane[:, None]
        col = mixc(col, pal["accent"], sm(0.7, 1.0, vnoise(P, 3.0, seed + 9)) * sp.get("feather_accent", 0.35))
    elif mat == "eye":
        c, fwd = ctx["eye"]
        d = P - np.array(c)
        d /= np.maximum(np.linalg.norm(d, axis=1, keepdims=True), 1e-9)
        cs = d @ fwd
        # reptile eye: dark glossy eyeball, small iris disc that darkens towards its rim, no white
        iris = sm(0.62, 0.7, cs)
        col = mixc(np.tile(np.array([0.06, 0.045, 0.03]), (n, 1)), pal["iris"] * 0.78, iris)
        col = mixc(col, pal["iris_dark"] * 0.8, sm(0.86, 0.7, cs) * iris)
        side = np.cross(fwd, np.array([0, 1.0, 0]))
        side /= max(np.linalg.norm(side), 1e-9)
        if sp.get("round_pupil"):
            pupil = cs > 0.9
        else:
            pupil = (np.abs(d @ side) < 0.11 * (0.5 + 0.5 * sm(0.7, 1.0, cs))) & (cs > 0.72)
        col[pupil] = np.array([0.02, 0.015, 0.01])
        hl = (d @ (fwd * 0.7 + np.array([0, 0.55, 0]) + side * 0.3) / np.linalg.norm(fwd * 0.7 + np.array([0, 0.55, 0]) + side * 0.3)) > 0.985
        col[hl] = np.array([0.8, 0.79, 0.74])
        AO = np.ones(n)
    else:
        col = np.tile(np.array([1.0, 0.0, 1.0]), (n, 1))
    col = col * (0.42 + 0.58 * AO)[:, None]
    return np.clip(col, 0, 1)
