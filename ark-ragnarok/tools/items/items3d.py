"""Realistic 3D item models: every mod item is a sculpted mesh with a baked texture, exported as an OBJ model for the
NeoForge OBJ loader, so it is 3D in the hand, in the inventory, on the ground and in item frames.

    python3 items3d.py <resources dir> [item ...]       (default: all items)

Authoring space: px (16 px = 1 block), +y up, +z towards the viewer.  Long items (arrow, spyglass) are authored along
+x and laid on the diagonal like a sword sprite, so the vanilla hand poses fit them.
"""
import json
import math
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import sdfkit as K
from sdfkit import Sculpt, normalize, frame_from, rot_euler
import hero_build as HB
import bake_hero as BH
import extras as EX
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, LIGHT
import dinos

BONES = ["root"]
RNG = np.random.default_rng(7)


# ============================================================================================ mesh helpers
def item_ao(field, v, n):
    F, lo, cell = field
    acc = np.zeros(len(v))
    for dist, w in ((0.25, 0.35), (0.6, 0.3), (1.2, 0.2), (2.4, 0.15)):
        d = K.sample(F, lo, cell, v + n * dist)
        acc += w * np.clip((dist - d) / dist, 0, 1)
    return np.clip(1.0 - acc * 1.25, 0.25, 1.0)


def sdf_part(prims, cell, tris):
    field = K.build_field(prims, cell, pad=1.5)
    (v, f, n), = K.polygonize(field, [tris])[0]
    adds = [p for p in prims if p.op == "add"]
    D = np.stack([p.sdf(v) for p in adds], axis=1)
    vmat = np.array([adds[i].mat for i in D.argmin(axis=1)])
    ao = item_ao(field, v, n)
    part = HB.rigid_part(v, f, n, "root", BONES, "x", ao=ao)
    part["tmat"] = vmat[part["f"][:, 0]]
    return part


def mesh_part(v, f, n, mat, ao=0.95, along=None):
    return HB.rigid_part(v, f, n, "root", BONES, mat, ao=ao, along=along)


def leaf_mesh(base, direction, side, length, width, fold=0.25, nu=9, nv=5):
    """a leaf blade: pointed ellipse, folded along the midrib"""
    d = normalize(np.asarray(direction, float))
    s = normalize(np.asarray(side, float))
    up = normalize(np.cross(d, s))
    vs, ns, fs = [], [], []
    for i in range(nu + 1):
        t = i / nu
        half = width * math.sin(math.pi * t ** 0.8) * (1 - 0.15 * t)
        for j in range(nv + 1):
            u = (j / nv) * 2 - 1
            p = np.asarray(base, float) + d * (t * length) + s * (u * half) + up * (abs(u) * half * fold - 0.15 * length * t * t)
            vs.append(p)
            ns.append(up)
    for i in range(nu):
        for j in range(nv):
            a = i * (nv + 1) + j
            b, c, e = a + 1, a + nv + 1, a + nv + 2
            fs += [(a, c, b), (b, c, e)]
    v = np.array(vs)
    f = np.array(fs, dtype=np.int64)
    # two-sided: duplicate with flipped winding, offset by a hair
    v2 = v - up * 0.06
    v = np.concatenate([v, v2])
    n = np.concatenate([np.tile(up, (len(vs), 1)), np.tile(-up, (len(vs), 1))])
    f = np.concatenate([f, f[:, ::-1] + len(vs)])
    return v, f, n


def ring_mesh(c, axis, R, r, nu=20, nv=8):
    """torus around axis"""
    a = normalize(np.asarray(axis, float))
    Fr = frame_from(a)
    u, w = Fr[:, 0], Fr[:, 1]
    vs, ns, fs = [], [], []
    for i in range(nu):
        ph = 2 * math.pi * i / nu
        d = u * math.cos(ph) + w * math.sin(ph)
        for j in range(nv):
            th = 2 * math.pi * j / nv
            nrm = d * math.cos(th) + a * math.sin(th)
            vs.append(np.asarray(c) + d * R + nrm * r)
            ns.append(nrm)
    for i in range(nu):
        for j in range(nv):
            p0 = i * nv + j
            p1 = i * nv + (j + 1) % nv
            p2 = ((i + 1) % nu) * nv + j
            p3 = ((i + 1) % nu) * nv + (j + 1) % nv
            fs += [(p0, p2, p1), (p1, p2, p3)]
    return np.array(vs), np.array(fs, dtype=np.int64), np.array(ns)


# ============================================================================================ painter
def shade(mat, P, N, AO, ctx):
    n = len(P)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    lit = np.clip((N * LIGHT).sum(axis=1), 0, 1)
    spec = sm(0.82, 0.98, lit)
    pal = ctx.get("pal", {})
    seed = ctx.get("seed", 3)
    if mat == "pellet":
        base = mixc(hx("#8a5a32"), np.asarray(pal["tier"]), 0.62)
        f1, f2, _ = cells(P, 0.45, seed)
        col = base[None, :] * (0.82 + 0.3 * fbm(P, 0.9, seed, 3))[:, None]
        col = mixc(col, base * 0.55, sm(0.12, 0.0, f1) * 0.6)                     # pores
        col = mixc(col, hx("#4a2e18"), sm(0.78, 0.95, vnoise(P, 0.35, seed + 4)) * 0.5)   # toasted specks
        col = col * (1 + 0.08 * spec)[:, None]
    elif mat in ("berry", "berry_dark"):
        base = np.asarray(pal["berry"])
        c = ctx["berry_centres"]
        d = P[:, None, :] - c[None, :, :]
        k = np.argmin((d ** 2).sum(axis=2), axis=1)
        dl = d[np.arange(n), k]
        dl = dl / np.maximum(np.linalg.norm(dl, axis=1, keepdims=True), 1e-6)
        col = base[None, :] * (0.75 + 0.35 * (dl[:, 1] * 0.5 + 0.5))[:, None]
        col = mixc(col, np.asarray(pal["berry_dark"]), sm(-0.2, -0.85, dl[:, 1]) * 0.7)
        calyx = sm(-0.86, -0.96, dl[:, 1])
        col = mixc(col, hx("#3a2a1a"), calyx)
        col = mixc(col, np.asarray(pal.get("bloom", base)), sm(0.5, 0.9, vnoise(P, 0.6, seed)) * pal.get("bloom_k", 0.25))
        col = col + (spec * 0.55)[:, None]
        AO = np.maximum(AO, 0.6)
    elif mat == "leaf":
        base = np.asarray(pal.get("leaf", hx("#4f7d32")))
        vein = np.abs(np.sin(fbm(P, 0.6, seed, 2) * 3 + (x + z) * 2.2))
        col = base[None, :] * (0.8 + 0.25 * vnoise(P, 0.8, seed + 2))[:, None]
        col = mixc(col, base * 1.35, sm(0.08, 0.0, vein) * 0.5)
        col = col + (spec * 0.15)[:, None]
    elif mat == "stem":
        col = np.tile(hx("#5a4428"), (n, 1)) * (0.85 + 0.2 * vnoise(P, 0.4, seed))[:, None]
    elif mat in ("glass", "liquid"):
        lv = ctx.get("liquid_y", -1e9)
        liquid = np.asarray(pal.get("liquid", hx("#4a2a6a")))
        glass = hx("#b8c8cc")
        inside = (y < lv).astype(float)
        col = mixc(np.tile(glass * 0.7, (n, 1)), liquid[None, :] * (0.7 + 0.4 * sm(-1, 1, z / 3.0))[:, None], inside)
        # paper label on the front
        lab = ctx.get("label")
        if lab is not None:
            ly0, ly1 = lab
            on = (y > ly0) & (y < ly1) & (z > 0.4)
            paper = hx("#d8c8a0") * (0.85 + 0.15 * vnoise(P, 0.7, seed))[:, None]
            col = np.where(on[:, None], paper, col)
            mark = on & (np.abs(x) < 0.9) & (np.abs(y - (ly0 + ly1) / 2) < 0.9)
            col[mark] = hx("#3a2418")
        col = col + (spec * 0.9)[:, None] + (sm(0.55, 0.9, np.abs(N[:, 0])) * 0.12)[:, None]
        AO = np.maximum(AO, 0.7)
    elif mat == "cork":
        col = np.tile(hx("#a07a4a"), (n, 1)) * (0.75 + 0.35 * fbm(P, 0.25, seed, 3))[:, None]
        f1, _, _ = cells(P, 0.3, seed)
        col = mixc(col, hx("#5a4028"), sm(0.08, 0.0, f1) * 0.6)
    elif mat == "twine":
        col = np.tile(hx("#c2a670"), (n, 1)) * (0.75 + 0.3 * np.abs(np.sin((x + y * 2.5 + z) * 7.0)))[:, None]
    elif mat in ("meat", "cooked"):
        cooked = mat == "cooked"
        top = np.abs(N[:, 1])
        if not cooked:
            red = hx("#a5262e") * (0.8 + 0.3 * fbm(P, 0.8, seed, 3))[:, None]
            marb = sm(0.62, 0.8, fbm(P * np.array([1.0, 0.4, 1.6]), 0.55, seed + 5, 3))
            col = mixc(red, hx("#ecd6c6"), marb * 0.8 * sm(0.3, 0.7, top))
            fat = sm(0.6, 0.3, top) * sm(0.2, 0.6, ctx["edge"](P))
            col = mixc(col, hx("#efe0c8") * (0.9 + 0.1 * vnoise(P, 0.5, seed))[:, None], fat)
            col = col + (spec * 0.35)[:, None]
        else:
            brown = hx("#6a3a1c") * (0.8 + 0.35 * fbm(P, 0.7, seed, 3))[:, None]
            grill = (np.abs(np.sin((x * 0.7 + z * 0.7) * 1.6)) > 0.93) & (top > 0.7)
            col = mixc(brown, hx("#2a160c"), grill.astype(float) * 0.85)
            col = mixc(col, hx("#a0642e"), sm(0.6, 0.3, top) * 0.5)
            col = mixc(col, hx("#1e1008"), sm(0.75, 0.9, vnoise(P, 0.4, seed + 9)) * 0.5)
            col = col + (spec * 0.25)[:, None]
    elif mat == "bone":
        col = np.tile(hx("#e8dcc0"), (n, 1)) * (0.85 + 0.15 * vnoise(P, 0.5, seed))[:, None]
        if ctx.get("cooked_bone"):
            col = mixc(col, hx("#8a6a40"), 0.35)
    elif mat == "wood":
        grain = np.sin(x * 0.6 + 5.0 * fbm(P * np.array([0.15, 1.0, 1.0]), 0.6, seed, 2))
        col = mixc(hx("#8a6038"), hx("#5a3c20"), sm(-0.2, 0.9, grain) * 0.6)
        col = col * (0.9 + 0.15 * vnoise(P, 0.4, seed))[:, None]
    elif mat == "feather":
        barb = np.abs(np.sin(ctx.get("barb", lambda q: q[:, 0] * 9)(P)))
        col = mixc(hx("#e8e4dc"), hx("#9a958c"), sm(0.4, 0.95, barb) * 0.4)
        col = mixc(col, np.asarray(pal.get("fletch", hx("#c04030"))), ctx.get("fletch_mask", lambda q: np.zeros(len(q)))(P))
        AO = np.maximum(AO, 0.75)
    elif mat in ("steel", "brass", "gold", "iron"):
        base = {"steel": hx("#b8c0c8"), "brass": hx("#c8a04a"), "gold": hx("#e0b848"), "iron": hx("#7a7a80")}[mat]
        refl = 0.55 + 0.45 * np.abs(N[:, 1]) * 0.6 + 0.3 * np.abs(N[:, 2])
        col = base[None, :] * refl[:, None] * (0.9 + 0.12 * vnoise(P, 1.2, seed))[:, None]
        col = col + (spec * 0.8)[:, None]
        bands = ctx.get("bands")
        if bands is not None and mat == "brass":
            for b0 in bands:
                col = mixc(col, base * 0.55, (np.abs(x - b0) < 0.12).astype(float))
        AO = np.maximum(AO, 0.65)
    elif mat == "leather":
        f1, f2, _ = cells(P, 0.28, seed)
        col = np.tile(np.asarray(pal.get("leather", hx("#6e4426"))), (n, 1)) * (0.8 + 0.3 * fbm(P, 0.6, seed, 3))[:, None]
        col = mixc(col, col * 0.7, sm(0.06, 0.0, f2 - f1) * 0.5)
        st = ctx.get("stitch")
        if st is not None:
            m = st(P)
            col = mixc(col, hx("#e0d2b0"), m)
        col = col + (spec * 0.18)[:, None]
    elif mat == "rubber":
        col = np.tile(hx("#3a4034"), (n, 1)) * (0.8 + 0.25 * fbm(P, 0.5, seed, 3))[:, None]
        col = mixc(col, hx("#7a6a3a"), sm(0.72, 0.9, vnoise(P, 1.2, seed + 3)) * 0.35)        # worn, dusty
        col = col + (spec * 0.12)[:, None]
    elif mat == "screen":
        flat = sm(0.85, 0.95, N[:, 2])
        scan = 0.85 + 0.15 * np.abs(np.sin(y * 9.0))
        glow = hx("#3ad8c8") * scan[:, None] * (0.6 + 0.6 * sm(1.0, -1.0, np.hypot(x, y - 1.6) / 2.6))[:, None]
        grid = ((np.abs(np.sin(x * 4.0)) > 0.96) | (np.abs(np.sin((y - 1.6) * 4.0)) > 0.96)).astype(float)
        col = mixc(np.tile(hx("#1a2224"), (n, 1)), glow + grid[:, None] * 0.15, flat)
        col = col + (spec * 0.5)[:, None]
        AO = np.maximum(AO, 0.95)
    elif mat == "button":
        col = np.tile(hx("#c84a2a"), (n, 1)) * (0.85 + 0.2 * lit)[:, None] + (spec * 0.4)[:, None]
    elif mat == "metal_dark":
        col = np.tile(hx("#4a4a4e"), (n, 1)) * (0.75 + 0.4 * lit)[:, None] + (spec * 0.6)[:, None]
    elif mat == "lens":
        col = np.tile(hx("#3a6a8a"), (n, 1)) + (spec * 1.2)[:, None] + (sm(0.5, 0.0, np.hypot(y, z) / 1.6) * 0.2)[:, None]
        AO = np.ones(n)
    elif mat == "gem":
        f1, f2, _ = cells(P, 0.5, seed)
        col = np.tile(hx("#7a3ad0"), (n, 1)) * (0.7 + 0.6 * sm(0.0, 0.2, f2 - f1))[:, None] + (spec * 1.0)[:, None]
        AO = np.ones(n)
    elif mat == "shell":
        c1, c2 = np.asarray(pal["egg"]), np.asarray(pal["spot"])
        f1, f2, _ = cells(P, 0.22, seed)
        col = c1[None, :] * (0.88 + 0.16 * fbm(P, 0.5, seed, 3))[:, None]
        # round speckles of two sizes (only some cells carry one)
        g1, _, v1 = cells(P, 1.25, seed + 3)
        g2, _, v2 = cells(P, 0.55, seed + 8)
        id1 = np.abs(np.sin((P / 1.25 - v1) @ np.array([12.9898, 78.233, 37.719])) * 43758.5453) % 1.0
        id2 = np.abs(np.sin((P / 0.55 - v2) @ np.array([12.9898, 78.233, 37.719])) * 43758.5453) % 1.0
        spots = sm(0.42, 0.3, g1 * (0.8 + 0.4 * id1)) * (id1 < 0.45) + sm(0.36, 0.24, g2) * (id2 < 0.35) * 0.8
        col = mixc(col, c2, np.clip(spots, 0, 1) * 0.85)
        col = col * (0.94 + 0.06 * sm(0.0, 0.1, f2 - f1))[:, None]
        col = col + (spec * 0.25)[:, None]
    else:
        col = np.tile(np.array([1.0, 0.0, 1.0]), (n, 1))
    col = col * (0.45 + 0.55 * AO)[:, None]
    return np.clip(col, 0, 1)


# ============================================================================================ items
def kibble(tier_col):
    """a small heap of dry kibble pellets (short rounded cylinders) in the tier colour"""
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    rng = np.random.default_rng(int(sum(tier_col)) + 11)
    spots = [(0, 0.75, 0)]
    for k in range(8):
        a = k * 2 * math.pi / 8 + 0.2
        spots.append((math.cos(a) * 2.7, 0.75, math.sin(a) * 2.5))
    for k in range(4):
        a = k * 2 * math.pi / 4 + 0.6
        spots.append((math.cos(a) * 3.9, 0.7, math.sin(a) * 3.6))
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.9
        spots.append((math.cos(a) * 1.6, 2.0, math.sin(a) * 1.5))
    spots += [(0.3, 3.15, -0.2), (-0.8, 3.05, 0.6)]
    for (px, py, pz) in spots:
        ax = normalize(rng.normal(size=3) * np.array([1.0, 0.45, 1.0]))
        c = np.array([px, py, pz]) + rng.uniform(-0.2, 0.2, 3) * np.array([1, 0.3, 1])
        r = 0.82 + rng.uniform(-0.08, 0.08)
        sc.cone(c - ax * 0.5, c + ax * 0.5, r, r * 0.96, mat="pellet", bone="root", k=0.1)
    parts = [sdf_part(sc.prims, 0.07, 3200)]
    return parts, dict(pal=dict(tier=tier_col)), 128, "small"


def berries(main, dark, leaf, bloom, bloom_k):
    parts = []
    rng = np.random.default_rng(int(main[1:], 16) % 9973)
    stem = [np.array(p, float) for p in ((0, 7.5, 0), (0.1, 5.5, 0.2), (0.0, 3.5, 0.0))]
    v, f, n = K.tube_mesh(stem, [0.32, 0.28, 0.22], n=7)
    parts.append(mesh_part(v, f, n, "stem"))
    centres = []
    spots = [(0, 1.5, 0.4), (2.0, 1.2, -0.3), (-2.0, 1.0, 0.1), (1.0, -0.6, 1.4), (-1.0, -0.9, 1.2), (0.6, -1.0, -1.6),
             (-1.2, -0.4, -1.4), (1.9, -2.4, 0.2), (-1.5, -2.6, 0.4), (0.2, -3.0, -0.6), (0.1, -1.9, 2.0)]
    for c in spots:
        r = 1.45 + rng.uniform(-0.15, 0.2)
        c = np.array(c, float)
        centres.append(c)
        v, f, n = K.sphere_mesh(c, r, 16, 12)
        parts.append(mesh_part(v, f, n, "berry", ao=0.9))
        # tiny stalk to the main stem
        v, f, n = K.tube_mesh([c + np.array([0, r * 0.8, 0]), np.array([0, 3.4, 0]) * 0.6 + c * 0.4], [0.13, 0.1], n=5)
        parts.append(mesh_part(v, f, n, "stem"))
    for (d, s) in (((1.0, 0.6, 0.3), (0.2, 0.0, -1.0)), ((-1.0, 0.5, 0.2), (0.1, 0.0, 1.0)), ((0.2, 0.7, -1.0), (1.0, 0.0, 0.1))):
        v, f, n = leaf_mesh((0, 6.4, 0), d, s, 5.6, 1.9, fold=0.3)
        parts.append(mesh_part(v, f, n, "leaf", ao=0.95))
    ctx = dict(pal=dict(berry=hx(main), berry_dark=hx(dark), leaf=hx(leaf), bloom=hx(bloom), bloom_k=bloom_k),
               berry_centres=np.array(centres))
    return parts, ctx, 128, "small"


def narcotic():
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    sc.cone((0, -5.6, 0), (0, 2.6, 0), 3.0, 3.0, mat="glass", bone="root", k=0.3)        # round-bottomed vial
    sc.cone((0, 2.6, 0), (0, 5.0, 0), 2.2, 1.45, mat="glass", bone="root", k=0.9)         # shoulder + neck
    sc.ell((0, 5.3, 0), (1.85, 0.55, 1.85), mat="glass", bone="root", k=0.3)               # lip
    sc.cone((0, 5.3, 0), (0, 7.8, 0), 1.35, 1.6, mat="cork", bone="root", k=0.2)            # cork
    parts = [sdf_part(sc.prims, 0.08, 3000)]
    v, f, n = ring_mesh((0, 4.4, 0), (0, 1, 0), 1.62, 0.22, 22, 6)
    parts.append(mesh_part(v, f, n, "twine"))
    v, f, n = ring_mesh((0, 4.0, 0), (0, 1, 0), 1.72, 0.2, 22, 6)
    parts.append(mesh_part(v, f, n, "twine"))
    ctx = dict(pal=dict(liquid=hx("#4a1e68")), liquid_y=1.0, label=(-3.6, -0.4))
    return parts, ctx, 256, "small"


def meat(cooked):
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    m = "cooked" if cooked else "meat"
    sc.ell((0, 0, 0), (6.2, 1.9, 4.6), mat=m, bone="root", k=0.8)
    for (c, r) in (((-3.4, 0.1, 1.6), (3.0, 1.8, 2.6)), ((2.6, 0.05, -1.8), (3.2, 1.7, 2.8)), ((-1.0, 0.0, -2.8), (3.2, 1.6, 2.0)),
                   ((3.4, -0.05, 1.8), (2.4, 1.6, 2.2))):
        sc.ell(c, r, mat=m, bone="root", k=0.9)
    sc.cone((4.6, 0.25, -0.4), (8.4, 0.5, -1.0), 1.0, 0.85, mat="bone", bone="root", k=0.4)
    sc.ell((8.9, 0.55, -1.1), (1.25, 1.05, 1.4), mat="bone", bone="root", k=0.4)
    sc.ell((8.9, 0.5, -0.1), (1.0, 0.9, 1.0), mat="bone", bone="root", k=0.4)
    parts = [sdf_part(sc.prims, 0.1, 3400)]

    def edge(P):
        # 1 near the outline of the slab (fat cap), 0 in the middle
        q = P / np.array([6.6, 1.0, 5.0])
        return np.clip(np.hypot(q[:, 0], q[:, 2]), 0, 1.2)
    return parts, dict(edge=edge, cooked_bone=cooked), 256, "small"


def tranq_arrow():
    parts = []
    path = [np.array([x, 0, 0], float) for x in np.linspace(-7.6, 5.0, 10)]
    v, f, n = K.tube_mesh(path, [0.3] * 10, n=8)
    parts.append(mesh_part(v, f, n, "wood"))
    v, f, n = K.sphere_mesh((-7.7, 0, 0), 0.38, 10, 8)
    parts.append(mesh_part(v, f, n, "wood"))
    for k in range(3):                        # three vanes at 120 degrees
        a = k * 2 * math.pi / 3 + 0.3
        rad = np.array([0, math.cos(a), math.sin(a)])
        side = np.cross(np.array([1.0, 0, 0]), rad)
        vs, fs = [], []
        nu, nv = 10, 4
        for i in range(nu + 1):
            t = i / nu
            h = 1.25 * math.sin(math.pi * min(1.0, t * 1.1)) ** 0.7 * (1 - 0.2 * t)
            for j in range(nv + 1):
                u = j / nv
                vs.append(np.array([-7.0 + 3.6 * t - 0.6 * u * u, 0, 0]) + rad * (0.28 + h * u) + side * (0.08 * u * u))
        for i in range(nu):
            for j in range(nv):
                a0 = i * (nv + 1) + j
                fs += [(a0, a0 + nv + 1, a0 + 1), (a0 + 1, a0 + nv + 1, a0 + nv + 2)]
        vv = np.array(vs)
        ff = np.array(fs, dtype=np.int64)
        nn = np.tile(side, (len(vv), 1))
        vv = np.concatenate([vv, vv - side * 0.05])
        ff = np.concatenate([ff, ff[:, ::-1] + len(vs)])
        nn = np.concatenate([nn, -nn])
        parts.append(mesh_part(vv, ff, nn, "feather"))
    for (x0, x1, r0, r1, mt) in ((5.0, 5.7, 0.42, 0.42, "brass"), (5.7, 7.7, 0.52, 0.52, "liquid"), (7.7, 8.1, 0.44, 0.3, "brass")):
        v, f, n = K.tube_mesh([np.array([x0, 0, 0]), np.array([x1, 0, 0])], [r0, r1], n=12)
        parts.append(mesh_part(v, f, n, mt))
    v, f, n = K.tube_mesh([np.array([8.1, 0, 0]), np.array([9.0, 0, 0]), np.array([9.9, 0, 0])], [0.16, 0.1, 0.02], n=8)
    parts.append(mesh_part(v, f, n, "steel"))
    ctx = dict(pal=dict(liquid=hx("#2a5ad8"), fletch=hx("#2a4ab0")), liquid_y=1e9,
               fletch_mask=lambda P: sm(-6.0, -6.6, P[:, 0]) * 0.9, barb=lambda P: P[:, 0] * 10 + np.abs(P[:, 1] + P[:, 2]) * 6)
    return parts, ctx, 128, "long"


def super_spyglass():
    parts = []
    segs = [(-8.2, -6.6, 0.95, 1.0, "brass"), (-6.6, -1.4, 1.12, 1.12, "leather"), (-1.4, -0.9, 1.32, 1.32, "brass"),
            (-0.9, 3.2, 1.22, 1.22, "brass"), (3.2, 3.7, 1.5, 1.5, "brass"), (3.7, 7.4, 1.42, 1.48, "brass"),
            (7.4, 8.3, 1.72, 1.72, "brass")]
    for x0, x1, r0, r1, mt in segs:
        v, f, n = K.tube_mesh([np.array([x0, 0, 0]), np.array([x1, 0, 0])], [r0, r1], n=24)
        parts.append(mesh_part(v, f, n, mt, ao=0.92))
    for xr, rr in ((-6.6, 1.18), (-1.4, 1.36), (3.2, 1.55), (7.4, 1.78), (8.3, 1.76)):
        v, f, n = ring_mesh((xr, 0, 0), (1, 0, 0), rr, 0.14, 26, 6)
        parts.append(mesh_part(v, f, n, "gold"))
    v, f, n = K.sphere_mesh((8.25, 0, 0), 1.45, 18, 10, R=np.diag([0.25, 1.0, 1.0]))
    parts.append(mesh_part(v, f, n, "lens"))
    v, f, n = K.sphere_mesh((-8.3, 0, 0), 0.7, 14, 8, R=np.diag([0.3, 1.0, 1.0]))
    parts.append(mesh_part(v, f, n, "lens"))
    # crystal set into a gold mount on top: the "super" in Super Spyglass
    v, f, n = K.tube_mesh([np.array([5.2, 1.3, 0]), np.array([5.2, 1.85, 0])], [0.62, 0.55], n=12)
    parts.append(mesh_part(v, f, n, "gold"))
    v, f, n = K.sphere_mesh((5.2, 2.05, 0), 0.55, 6, 4)
    parts.append(mesh_part(v, f, n, "gem"))

    def stitch(P):
        return ((np.abs(np.sin(P[:, 0] * 4.0)) > 0.6) & (np.abs(np.abs(np.arctan2(P[:, 2], P[:, 1])) - 1.57) < 0.06)).astype(float)
    ctx = dict(pal=dict(leather=hx("#5a3418")), bands=[0.4, 1.8, 4.6, 6.0], stitch=stitch)
    return parts, ctx, 256, "long"


def dino_saddle():
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    L = "leather"
    sc.ell((0, 0.6, 0), (4.6, 1.1, 6.0), mat=L, bone="root", k=0.6)                        # seat
    sc.ell((0, 0.8, 0.4), (3.6, 0.6, 3.8), mat=L, bone="root", op="sub", k=0.5)              # seat dip
    sc.ell((0, 1.9, -5.0), (2.2, 2.0, 1.4), mat=L, bone="root", k=0.6)                      # pommel
    sc.cone((0, 3.2, -5.4), (0, 4.9, -5.9), 0.75, 0.95, mat=L, bone="root", k=0.4)           # horn
    sc.ell((0, 2.5, 5.0), (3.8, 2.2, 1.2), mat=L, bone="root", k=0.6)                       # cantle
    for s in (-1, 1):
        sc.ell((s * 4.6, -2.4, 0.2), (0.45, 3.4, 4.6), mat=L, bone="root", k=0.7, rot=(0, 0, -s * 0.12))   # fender
        sc.ell((s * 3.6, -0.2, 0.0), (1.2, 1.6, 5.0), mat=L, bone="root", k=0.8)
    parts = [sdf_part(sc.prims, 0.1, 3600)]
    for s in (-1, 1):
        strap = [np.array([s * 4.9, -1.0, -1.0]), np.array([s * 5.1, -4.5, -1.2]), np.array([s * 5.0, -7.6, -1.3])]
        v, f, n = K.tube_mesh(strap, [0.32, 0.3, 0.3], n=6)
        v[:, 2] = (v[:, 2] - (-1.2)) * 2.2 + (-1.2)          # flat strap
        parts.append(mesh_part(v, f, n, "leather"))
        v, f, n = ring_mesh((s * 5.0, -8.6, -1.3), (0, 0, 1), 1.0, 0.2, 18, 6)
        parts.append(mesh_part(v, f, n, "iron"))
        v, f, n = ring_mesh((s * 4.75, 0.4, -3.6), (1, 0, 0), 0.5, 0.12, 12, 5)
        parts.append(mesh_part(v, f, n, "iron"))
    girth = [np.array([-5.0, -1.4, 2.6]), np.array([0, -1.0, 2.6]), np.array([5.0, -1.4, 2.6])]
    v, f, n = K.tube_mesh(girth, [0.28, 0.28, 0.28], n=6)
    parts.append(mesh_part(v, f, n, "leather"))

    def stitch(P):
        q = np.hypot(P[:, 0] / 4.3, P[:, 2] / 5.7)
        return ((np.abs(q - 0.93) < 0.02) & (np.abs(np.sin(np.arctan2(P[:, 2], P[:, 0]) * 40)) > 0.4) & (P[:, 1] > 0.6)).astype(float)
    return parts, dict(pal=dict(leather=hx("#6e4426")), stitch=stitch), 256, "small"


def command_device():
    """rugged handheld radio-computer: rubber-armoured body, glowing screen, keypad, antenna, wrist strap"""
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    sc.rbox((0, 0, 0), (3.6, 5.6, 1.3), 0.9, mat="rubber", bone="root", k=0.2)
    sc.rbox((0, 1.6, 1.05), (2.8, 2.6, 0.5), 0.35, mat="screen", bone="root", k=0.15)
    sc.rbox((0, 1.6, 1.35), (2.5, 2.3, 0.3), 0.2, mat="screen", bone="root", op="sub", k=0.12)
    for (bx, by) in ((-1.6, -2.6), (0, -2.6), (1.6, -2.6), (-1.6, -4.1), (0, -4.1), (1.6, -4.1)):
        sc.rbox((bx, by, 1.15), (0.55, 0.45, 0.35), 0.18, mat="button", bone="root", k=0.1)
    for s in (-1, 1):
        sc.rbox((s * 3.7, 0, 0), (0.5, 4.8, 1.0), 0.4, mat="metal_dark", bone="root", k=0.2)
    parts = [sdf_part(sc.prims, 0.06, 3200)]
    v, f, n = K.tube_mesh([np.array([2.2, 5.4, -0.2]), np.array([2.3, 9.2, -0.3]), np.array([2.35, 9.9, -0.3])], [0.32, 0.22, 0.12], n=8)
    parts.append(mesh_part(v, f, n, "rubber"))
    v, f, n = K.sphere_mesh((2.35, 10.0, -0.3), 0.32, 8, 6)
    parts.append(mesh_part(v, f, n, "button"))
    v, f, n = ring_mesh((0, -6.0, 0), (1, 0, 0), 1.0, 0.22, 16, 6)
    parts.append(mesh_part(v, f, n, "leather"))
    return parts, dict(pal=dict(leather=hx("#3a3026"))), 256, "small"


def egg(look):
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    sc.ell((0, -0.6, 0), (4.2, 4.8, 4.2), mat="shell", bone="root", k=1.0)
    sc.ell((0, 2.0, 0), (3.2, 3.6, 3.2), mat="shell", bone="root", k=2.0)
    parts = [sdf_part(sc.prims, 0.1, 1800)]
    pal = look.get("pal", {})
    from paint_species import palette
    p = palette(pal)
    eggc = mixc(mixc(p["belly"], p["flank"], 0.3), np.array([0.93, 0.9, 0.84]), 0.35)
    spot = mixc(p["dorsal"], p.get("stripe", p["dorsal"]), 0.5)
    return parts, dict(pal=dict(egg=eggc, spot=spot), seed=sum(ord(ch) for ch in str(pal)) % 1000), 256, "small"


# ============================================================================================ export
DISPLAY = {
    "small": {
        "gui": {"rotation": [25, -35, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.32, 0.32, 0.32]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_righthand": {"rotation": [10, -30, 0], "translation": [0, 3, 1.2], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_lefthand": {"rotation": [10, 30, 0], "translation": [0, 3, 1.2], "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [5, -30, 0], "translation": [1.5, 4.4, 0.5], "scale": [0.36, 0.36, 0.36]},
        "firstperson_lefthand": {"rotation": [5, 30, 0], "translation": [1.5, 4.4, 0.5], "scale": [0.36, 0.36, 0.36]},
    },
    "long": {
        "gui": {"rotation": [18, -18, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.42, 0.42, 0.42]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.6, 1.13], "scale": [0.46, 0.46, 0.46]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.6, 1.13], "scale": [0.46, 0.46, 0.46]},
    },
}
# per item tweaks (scale factors on top of the kind's pose)
SCALE = {"dino_saddle": {"firstperson_righthand": 0.75, "firstperson_lefthand": 0.75, "ground": 0.8}}


def display_for(name, kind):
    import copy
    d = copy.deepcopy(DISPLAY[kind])
    for ctx, f in SCALE.get(name, {}).items():
        d[ctx]["scale"] = [round(v * f, 4) for v in d[ctx]["scale"]]
    return d


def finish(m, kind):
    """authoring px -> centred model px (the texture was baked in authoring space)"""
    m = dict(m)
    v = m["v"].copy()
    if kind == "long":
        # lay the x axis on the bottom-left -> top-right diagonal like a sword sprite
        c, s_ = math.cos(math.pi / 4), math.sin(math.pi / 4)
        Rz = np.array([[c, -s_, 0], [s_, c, 0], [0, 0, 1.0]])
        v = v @ Rz.T
        m["n"] = m["n"] @ Rz.T
    lo, hi = v.min(axis=0), v.max(axis=0)
    centre = (lo + hi) / 2
    span = float((hi - lo).max())
    target = 21.0 if kind == "long" else 14.5          # px across the largest dimension
    v = (v - centre) * (target / span)
    m["v"] = v
    return m


def write_obj(path, um):
    v = um["v"] / 16.0 + 0.5
    n = um["n"]
    uv = um["uv"]
    lines = ["# generated by tools/items/items3d.py", "mtllib %s.mtl" % os.path.basename(path)[:-4], "usemtl item"]
    lines += ["v %.5f %.5f %.5f" % tuple(p) for p in v]
    lines += ["vt %.5f %.5f" % (t[0], t[1]) for t in uv]
    lines += ["vn %.4f %.4f %.4f" % tuple(q) for q in n]
    lines += ["f %d/%d/%d %d/%d/%d %d/%d/%d" % (a + 1, a + 1, a + 1, b + 1, b + 1, b + 1, c + 1, c + 1, c + 1) for a, b, c in um["f"]]
    with open(path, "w") as fh:
        fh.write("\n".join(lines) + "\n")
    with open(path[:-4] + ".mtl", "w") as fh:
        fh.write("newmtl item\nKd 1.0 1.0 1.0\nmap_Kd #texture\n")


def export(root, name, parts, ctx, tex, kind, preview=None):
    A = root + "/assets/arkragnarok"
    um = BH.unwrap(HB.merge(parts), tex, padding=2)
    img, _ = BH.bake(um, shade, tex, tex, ctx, dilate=4)
    um = finish(um, kind)
    for d in ("models/item/obj", "textures/item/3d"):
        os.makedirs(A + "/" + d, exist_ok=True)
    write_obj(A + "/models/item/obj/%s.obj" % name, um)
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(A + "/textures/item/3d/%s.png" % name)
    model = {"loader": "neoforge:obj", "model": "arkragnarok:models/item/obj/%s.obj" % name, "flip_v": False,
             "automatic_culling": False, "shade_quads": True, "gui_light": "side",
             "textures": {"texture": "arkragnarok:item/3d/%s" % name, "particle": "arkragnarok:item/3d/%s" % name},
             "display": display_for(name, kind)}
    json.dump(model, open(A + "/models/item/%s.json" % name, "w"), indent=1)
    if preview is not None:
        preview.append((name, um, img))
    return um


KIBBLES = {"basic_kibble": "#d8c08a", "simple_kibble": "#8fbf5a", "regular_kibble": "#5f8fd0", "superior_kibble": "#e08a3a",
           "exceptional_kibble": "#9a5ad0", "extraordinary_kibble": "#f0d24a"}


def all_items():
    out = {}
    for k, c in KIBBLES.items():
        out[k] = (lambda c=c: kibble(hx(c)))
    out["mejoberry"] = lambda: berries("#5a3ab0", "#24124a", "#4f7d32", "#b0a0d8", 0.35)
    out["narcoberry"] = lambda: berries("#262640", "#08080e", "#3c6a2a", "#5a5a80", 0.2)
    out["narcotic"] = narcotic
    out["raw_prime_meat"] = lambda: meat(False)
    out["cooked_prime_meat"] = lambda: meat(True)
    out["tranq_arrow"] = tranq_arrow
    out["super_spyglass"] = super_spyglass
    out["dino_saddle"] = dino_saddle
    out["survivor_command_device"] = command_device
    for sid in dinos.ALL:
        out[sid + "_spawn_egg"] = (lambda sid=sid: egg(dinos.LOOK[sid]))
    return out


if __name__ == "__main__":
    root = sys.argv[1]
    want = sys.argv[2:]
    items = all_items()
    prev = []
    for name, fn in items.items():
        if want and name not in want:
            continue
        parts, ctx, tex, kind = fn()
        um = export(root, name, parts, ctx, tex, kind, prev)
        print("%-24s %5d tris  tex %d" % (name, len(um["f"]), tex), flush=True)
    if os.environ.get("ITEM_PREVIEW"):
        import view
        tiles = []
        for name, um, img in prev:
            t = view.render(um["v"], um["n"], um["f"], size=(220, 220), yaw=-0.55, pitch=0.38, tex=img, uv=um["uv"], ground=False)
            tiles.append(t)
        Image.fromarray((view.sheet(tiles, 7) * 255).astype(np.uint8)).save(os.environ["ITEM_PREVIEW"])
