"""Saddle materials: worn leather with stitching, woven blanket, fur trim, iron and brass hardware, wool bedroll, rope."""
import math
import numpy as np
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, LIGHT
import rex_saddle

PAL = dict(
    leather=hx("#6e4426"), leather_dark=hx("#3f2614"), seat=hx("#94633a"), strap=hx("#55361d"), bag=hx("#7d5431"),
    stitch=hx("#d9c49a"), cloth=hx("#7a2418"), cloth2=hx("#c8a15a"), cloth3=hx("#2b2a3a"), fur=hx("#c4ab84"),
    fur_dark=hx("#7d6446"), metal=hx("#8d9298"), metal_dark=hx("#4a4d52"), brass=hx("#b48a3c"), bedroll=hx("#5f6a52"),
    bedroll2=hx("#8c8a6a"), rope=hx("#a8895a"),
)


def leather(base, P, N, worn=0.5, seed=1):
    grain = fbm(P * 2.2, 1.0, seed, 3)
    pores = vnoise(P, 0.18, seed + 3)
    col = base[None, :] * (0.82 + 0.28 * grain[:, None]) * (0.95 + 0.06 * pores[:, None])
    # wear: lighter scuffs on exposed (upward / outward) areas, darker oil in creases
    scuff = sm(0.55, 0.85, fbm(P, 1.3, seed + 7, 3)) * worn * sm(-0.2, 0.8, N[:, 1])
    col = mixc(col, base * 1.45, scuff * 0.45)
    scratches = (1 - sm(0.0, 0.05, np.abs(np.sin(P[:, 0] * 3.1 + P[:, 2] * 7.3 + fbm(P, 2, seed + 9) * 6)))) * \
        sm(0.75, 0.9, vnoise(P, 3.0, seed + 11))
    col = mixc(col, base * 1.6, scratches * 0.5)
    return col


def stitches(P, region_fn, inset=0.75, gap=0.55):
    r = region_fn(P)
    line = 1 - sm(0.05, 0.16, np.abs(r + inset))
    along = P[:, 2] + P[:, 1] * 0.7 + P[:, 0] * 0.3
    dash = (np.sin(along * math.pi / gap) > 0.1).astype(float)
    return line * dash


def shade(mat, P, N, AO, ctx):
    n = len(P)
    if mat == "leather":
        col = leather(PAL["leather"], P, N, 0.6, 3)
        col = mixc(col, PAL["leather_dark"], (1 - sm(-1.6, -0.4, -np.abs(rex_saddle.REGIONS["leather"](P)))) * 0.5)
        col = mixc(col, PAL["stitch"], stitches(P, rex_saddle.REGIONS["leather"], 0.7) * 0.9)
        # tooled pattern: embossed diamond lines in the middle of the flap
        d = np.abs(np.sin((P[:, 1] + P[:, 2]) * 0.9)) * np.abs(np.sin((P[:, 1] - P[:, 2]) * 0.9))
        col = col * (1 - 0.18 * (1 - sm(0.0, 0.08, d)) * sm(-1.2, -2.2, rex_saddle.REGIONS["leather"](P)))[:, None]
    elif mat == "leather_dark":
        col = leather(PAL["leather_dark"], P, N, 0.8, 5)
    elif mat == "seat":
        col = leather(PAL["seat"], P, N, 0.9, 7)
        # quilted padding: diamond stitched pattern
        u, v = P[:, 0] + P[:, 2] * 0.8, P[:, 0] - P[:, 2] * 0.8
        q = np.minimum(np.abs(np.sin(u * 1.2)), np.abs(np.sin(v * 1.2)))
        col = col * (0.82 + 0.18 * sm(0.0, 0.3, q))[:, None]
        col = mixc(col, PAL["stitch"], (1 - sm(0.0, 0.07, q)) * (np.sin((u + v) * 5) > 0) * 0.6)
    elif mat == "strap":
        col = leather(PAL["strap"], P, N, 0.5, 9)
        col = mixc(col, PAL["stitch"], stitches(P, rex_saddle.REGIONS["strap"], 0.32, 0.4) * 0.85)
    elif mat == "bag":
        col = leather(PAL["bag"], P, N, 0.7, 11)
        seam = 1 - sm(0.0, 0.12, np.abs(np.sin(P[:, 1] * 1.6 + 0.4)))
        col = mixc(col, PAL["stitch"], seam * (np.sin(P[:, 2] * 9) > 0) * 0.5)
    elif mat == "cloth":
        # woven blanket: red field, golden border bands, dark zig-zag
        r = rex_saddle.REGIONS["cloth"](P)
        weave = 0.88 + 0.12 * (np.sin(P[:, 2] * 9.0) * np.sin(P[:, 1] * 9.0 + P[:, 0] * 9.0) > 0)
        col = np.tile(PAL["cloth"], (n, 1)) * weave[:, None]
        border = sm(-2.4, -1.9, r) * (1 - sm(-0.9, -0.5, r))
        col = mixc(col, PAL["cloth2"] * weave[:, None], border)
        zig = np.abs(((P[:, 2] * 0.8) % 2.0) - 1.0)
        band = (np.abs(r + 3.6 - zig * 0.9) < 0.35)
        col[band] = PAL["cloth3"]
        col = col * (0.9 + 0.12 * fbm(P, 2.0, 13, 2))[:, None]
    elif mat == "fur":
        streak = vnoise(P * np.array([5.0, 0.6, 5.0]), 1.0, 17)
        clump = fbm(P, 0.9, 19, 2)
        col = mixc(np.tile(PAL["fur"], (n, 1)), PAL["fur_dark"], (1 - clump) * 0.6) * (0.8 + 0.25 * streak)[:, None]
    elif mat in ("metal", "brass"):
        base = PAL[mat]
        f1, f2, _ = cells(P, 0.5, 23)
        pit = 1 - 0.12 * (1 - sm(0.0, 0.2, f2 - f1))
        rust = sm(0.62, 0.8, fbm(P, 0.8, 29, 3)) * (0.5 if mat == "metal" else 0.25)
        col = base[None, :] * pit[:, None]
        col = mixc(col, hx("#6a3d22") if mat == "metal" else hx("#4f5a3a"), rust)
        spec = sm(0.6, 0.95, (N * LIGHT).sum(axis=1)) * 0.35
        col = col + spec[:, None]
        AO = np.maximum(AO, 0.7)
    elif mat == "bedroll":
        stripe = (np.abs((P[:, 0] % 4.0) - 2.0) < 0.5)
        col = np.tile(PAL["bedroll"], (n, 1)) * (0.85 + 0.2 * fbm(P * 3, 1.0, 31, 2))[:, None]
        col[stripe] = col[stripe] * 0.6 + PAL["bedroll2"] * 0.4
    elif mat == "rope":
        tw = 0.5 + 0.5 * np.sin((P[:, 0] + P[:, 1] * 1.3 + P[:, 2]) * 6.0)
        col = np.tile(PAL["rope"], (n, 1)) * (0.7 + 0.35 * tw)[:, None]
    else:
        col = np.tile(np.array([1.0, 0.0, 1.0]), (n, 1))
    col = col * (0.4 + 0.6 * AO)[:, None]
    return np.clip(col, 0, 1)
