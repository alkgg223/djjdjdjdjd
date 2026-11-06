"""ARK-style resource nodes: big rocks you hit as one mass (not blocks).  Sculpted boulders in three sizes for
stone, metal (veined), obsidian (glassy, faceted) and crystal (hexagonal crystals growing from a rock).

    python3 nodes.py <resources dir>

Writes assets/arkragnarok/arkmesh/node_<kind>_<size>_lod<0|1>.arkm and textures/entity/node/<kind>_<size>.png.
Space: px (16 px = 1 block), +y up, ground at y = 0, centred on x = z = 0.
"""
import math
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import sdfkit as K
from sdfkit import Sculpt, normalize
import hero_build as HB
import bake_hero as BH
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, LIGHT
from make_rex import write_mesh, save_png

BONES = ["root"]
SIZES = {0: 19.0, 1: 34.0, 2: 54.0}          # px across (about 1.2, 2.1 and 3.4 blocks)


def ao_for(field, v, n, s):
    F, lo, cell = field
    acc = np.zeros(len(v))
    for dist, w in ((0.6, 0.35), (1.6, 0.3), (3.2, 0.2), (6.0, 0.15)):
        d = K.sample(F, lo, cell, v + n * dist * s)
        acc += w * np.clip((dist * s - d) / (dist * s), 0, 1)
    return np.clip(1.0 - acc * 1.2, 0.3, 1.0)


def boulder_fn(c, r, rng, facets, amp, sharp=False):
    """rounded boulder: ellipsoid cut by random planes (facets / fracture faces), with surface noise"""
    c = np.asarray(c, float)
    r = np.asarray(r, float)
    planes = []
    for _ in range(facets):
        nrm = normalize(rng.normal(size=3) * np.array([1.0, 0.6, 1.0]) + np.array([0, 0.25, 0]))
        planes.append((nrm, rng.uniform(0.62, 0.85)))
    seed = int(rng.integers(1, 9999))
    k = 0.08 if sharp else 0.25

    def f(P):
        q = (P - c) / r
        d = (np.linalg.norm(q, axis=1) - 1.0)
        for nrm, off in planes:
            dp = q @ nrm - off
            h = np.clip(0.5 + 0.5 * (dp - d) / k, 0, 1)          # smooth max (intersection)
            d = dp * h + d * (1 - h) + k * h * (1 - h)
        d = d * float(r.min())
        return d + amp * (fbm(P, 4.0 * r.min() / 10, seed, 3) - 0.5) + amp * 0.4 * (vnoise(P, 1.2, seed + 5) - 0.5)
    return f


def build_rock(kind, size, seed):
    rng = np.random.default_rng(seed)
    S = SIZES[size]
    s = S / 34.0
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    mat = {"rock": "stone", "metal": "metal", "obsidian": "obsidian", "crystal": "stone"}[kind]
    sharp = kind == "obsidian"
    main_r = np.array([S * 0.5, S * 0.36 if kind != "crystal" else S * 0.24, S * 0.42])
    def box(c, r):
        c = np.asarray(c, float)
        return c - r * 1.15 - 2 * s, c + r * 1.15 + 2 * s
    c0 = (0, main_r[1] * 0.55, 0)
    sc.func(boulder_fn(c0, main_r, rng, 7, 0.9 * s, sharp), *box(c0, main_r), mat, bone="root", k=0.6 * s)
    for _ in range(2 if size == 0 else 3):
        a = rng.uniform(0, 2 * math.pi)
        rr = main_r * rng.uniform(0.45, 0.62, 3)
        cc = (math.cos(a) * S * 0.36, rr[1] * 0.45, math.sin(a) * S * 0.3)
        sc.func(boulder_fn(cc, rr, rng, 5, 0.7 * s, sharp), *box(cc, rr), mat, bone="root", k=1.2 * s)
    cell = max(0.16, S / 150.0)
    field = K.build_field(sc.prims, cell, pad=2.0)
    lods, _ = K.polygonize(field, [int(2200 + 900 * size), int(700 + 250 * size)])
    out = []
    for v, f, n in lods:
        keep = v[:, 1] > -0.6 * s                     # drop the buried underside
        ok = keep[f].all(axis=1)
        f = f[ok]
        used = np.unique(f)
        remap = -np.ones(len(v), dtype=np.int64)
        remap[used] = np.arange(len(used))
        v, n, f = v[used], n[used], remap[f]
        ao = ao_for(field, v, n, s)
        part = HB.rigid_part(v, f, n, "root", BONES, mat, ao=ao)
        parts = [part]
        if kind == "crystal":
            parts += crystal_cluster(rng, S, field)
        out.append(parts)
    return out


def crystal_cluster(rng, S, field):
    parts = []
    F, lo, cell = field
    n = 9
    for i in range(n):
        a = i * 2 * math.pi / n + rng.uniform(-0.3, 0.3)
        rad = S * rng.uniform(0.05, 0.3)
        base = np.array([math.cos(a) * rad, 0.0, math.sin(a) * rad])
        # find the rock surface below / around the base
        for yy in np.linspace(S * 0.6, -1, 60):
            p = base + np.array([0, yy, 0])
            if K.sample(F, lo, cell, p[None])[0] < 0:
                base = p - np.array([0, 0.5, 0])
                break
        d = normalize(np.array([math.cos(a) * rng.uniform(0.2, 0.7), 1.0, math.sin(a) * rng.uniform(0.2, 0.7)]))
        L = S * rng.uniform(0.32, 0.62) * (1.25 if i == 0 else 1.0)
        r = S * rng.uniform(0.05, 0.085)
        path = [base, base + d * L * 0.82, base + d * L]
        v, f, nn = K.tube_mesh(path, [r, r, 0.04], n=6)
        parts.append(HB.rigid_part(v, f, nn, "root", BONES, "crystal", ao=1.0))
    return parts


def shade(mat, P, N, AO, ctx):
    n = len(P)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    s = ctx["s"]
    seed = ctx["seed"]
    lit = np.clip((N * LIGHT).sum(axis=1), 0, 1)
    spec = sm(0.85, 0.99, lit)
    up = sm(0.35, 0.85, N[:, 1])
    if mat in ("stone", "metal"):
        base = hx("#8a8680") if mat == "stone" else hx("#6e625a")
        col = base[None, :] * (0.78 + 0.35 * fbm(P, 3.0 * s, seed, 4))[:, None]
        col = mixc(col, hx("#a8a49c"), sm(0.6, 0.8, vnoise(P, 1.4 * s, seed + 3)) * 0.4)        # lighter grains
        f1, f2, _ = cells(P, 9.0 * s, seed + 7)
        crack = sm(0.035, 0.0, f2 - f1) * sm(0.5, 0.72, vnoise(P, 5.0 * s, seed + 8))        # a few long fractures
        col = mixc(col, col * 0.55, crack * 0.7)
        col = col * (0.9 + 0.2 * sm(0.3, 0.7, fbm(P, 1.0 * s, seed + 9, 2)))[:, None]          # weathered grain
        if mat == "stone":
            lichen = sm(0.66, 0.8, vnoise(P, 2.2 * s, seed + 11)) * (0.4 + 0.6 * up)
            col = mixc(col, hx("#8a9a4a"), lichen * 0.55)
            col = mixc(col, hx("#c8a040"), sm(0.82, 0.9, vnoise(P, 1.1 * s, seed + 13)) * 0.5)  # orange lichen spots
            moss = up * sm(0.45, 0.75, fbm(P, 5.0 * s, seed + 17, 3))
            col = mixc(col, hx("#4e6a2a") * (0.8 + 0.3 * vnoise(P, 0.5, seed))[:, None], moss * 0.75)
        else:
            # metal veins and nuggets: bright, glinting
            vein = np.abs(fbm(P * np.array([1.0, 1.6, 1.0]), 4.0 * s, seed + 21, 3) - 0.5)
            v = sm(0.045, 0.0, vein)
            nug = sm(0.8, 0.9, vnoise(P, 0.9 * s, seed + 23))
            metal = hx("#c8c4bc") * (0.8 + 0.4 * lit)[:, None] + (spec * 0.7)[:, None]
            col = mixc(col, metal, np.clip(v + nug, 0, 1))
            col = mixc(col, hx("#a0602a"), sm(0.7, 0.85, vnoise(P, 1.6 * s, seed + 29)) * 0.35)   # rust stains
        col = col * (0.95 + 0.1 * lit)[:, None]
    elif mat == "obsidian":
        col = hx("#16131c")[None, :] * (0.8 + 0.5 * fbm(P, 3.0 * s, seed, 3))[:, None]
        sheen = sm(0.5, 0.95, lit)
        col = col + (np.outer(sheen, hx("#5a3a8a")) * 0.35) + (spec * 0.9)[:, None]
        f1, f2, _ = cells(P, 7.0 * s, seed + 5)
        col = mixc(col, hx("#3a3048"), sm(0.03, 0.0, f2 - f1) * 0.5)
        AO = np.maximum(AO, 0.55)
    elif mat == "crystal":
        t = sm(0.0, 1.0, (y - y.min()) / max(np.ptp(y), 1e-6))
        col = mixc(np.tile(hx("#6a3ab0"), (n, 1)), hx("#d8c0ff"), t * 0.6)
        facet = np.abs(np.sin(np.arctan2(N[:, 2], N[:, 0]) * 3))
        col = col * (0.75 + 0.35 * facet)[:, None] + (spec * 1.1)[:, None]
        AO = np.ones(n)
    else:
        col = np.tile(np.array([1.0, 0, 1.0]), (n, 1))
    col = col * (0.4 + 0.6 * AO)[:, None]
    return np.clip(col, 0, 1)


def main(root):
    A = root + "/assets/arkragnarok"
    os.makedirs(A + "/arkmesh", exist_ok=True)
    os.makedirs(A + "/textures/entity/node", exist_ok=True)
    previews = []
    for ki, kind in enumerate(("rock", "metal", "obsidian", "crystal")):
        for size in (0, 1, 2):
            seed = 100 + ki * 10 + size
            lods = build_rock(kind, size, seed)
            tex = (256, 512, 512)[size]
            ctx = dict(s=SIZES[size] / 34.0, seed=seed)
            first = None
            for li, parts in enumerate(lods):
                um = BH.unwrap(HB.merge(parts), tex if li == 0 else tex // 2, padding=3)
                img, _ = BH.bake(um, shade, tex if li == 0 else tex // 2, tex if li == 0 else tex // 2, ctx, dilate=4)
                write_mesh(A + "/arkmesh/node_%s_%d_lod%d.arkm" % (kind, size, li), um, BONES)
                save_png(A + "/textures/entity/node/%s_%d_lod%d.png" % (kind, size, li), img)
                if li == 0:
                    first = (um, img)
            print("node %-9s size %d: %d tris" % (kind, size, len(first[0]["f"])), flush=True)
            previews.append(first)
    if os.environ.get("NODE_PREVIEW"):
        import view
        tiles = [view.render(um["v"], um["n"], um["f"], size=(260, 200), yaw=0.6, pitch=0.35, tex=img, uv=um["uv"]) for um, img in previews]
        Image.fromarray((view.sheet(tiles, 3) * 255).astype(np.uint8)).save(os.environ["NODE_PREVIEW"])


if __name__ == "__main__":
    main(sys.argv[1])
