"""3D models of the mod's own economy: raw resources, processed materials and tools (stage 1 of the survivor
economy).  Same pipeline as items3d.py (sculpt -> mesh -> UV -> baked texture -> OBJ item model).

    python3 economy3d.py <resources dir> [item ...]
"""
import math
import os
import sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import items3d as I
import sdfkit as K
from sdfkit import Sculpt, normalize
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, LIGHT

base_shade = I.shade


def shade(mat, P, N, AO, ctx):
    n = len(P)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    seed = ctx.get("seed", 5)
    lit = np.clip((N * LIGHT).sum(axis=1), 0, 1)
    spec = sm(0.82, 0.98, lit)
    pal = ctx.get("pal", {})
    if mat == "bark":
        ends = sm(0.8, 0.95, np.abs(N[:, ctx.get("axis", 0)]))
        bark = np.abs(np.sin(fbm(P * np.array([0.3, 1, 1]), 0.6, seed, 3) * 10))
        col = mixc(np.tile(hx("#5a3e26"), (n, 1)), hx("#2e2014"), sm(0.4, 1.0, bark) * 0.7)
        r = np.hypot(*[P[:, i] for i in range(3) if i != ctx.get("axis", 0)])
        rings = np.sin(r * 4.5 + fbm(P, 0.6, seed + 2, 2) * 2)
        wood = mixc(np.tile(hx("#d0a870"), (n, 1)), hx("#a87a48"), sm(-0.4, 0.9, rings) * 0.7)
        col = mixc(col, wood, ends)
    elif mat == "straw":
        fib = np.abs(np.sin((x * 3.0 + z * 3.0) + fbm(P, 0.4, seed, 2) * 6))
        col = mixc(np.tile(hx("#d8b860"), (n, 1)), hx("#a0823a"), sm(0.3, 1.0, fib) * 0.6) + (spec * 0.15)[:, None]
    elif mat == "fiber":
        fib = np.abs(np.sin((x + y + z) * 6.0 + fbm(P, 0.3, seed, 2) * 5))
        col = mixc(np.tile(hx("#6a8a3a"), (n, 1)), hx("#3a5a22"), sm(0.3, 1.0, fib) * 0.6) + (spec * 0.1)[:, None]
    elif mat in ("rock", "ore"):
        col = np.tile(hx("#8a867e"), (n, 1)) * (0.75 + 0.35 * fbm(P, 1.2, seed, 4))[:, None]
        col = mixc(col, hx("#a8a49a"), sm(0.6, 0.8, vnoise(P, 0.5, seed + 3)) * 0.35)
        if mat == "ore":
            col = mixc(col * 0.75, hx("#8a4a22"), sm(0.55, 0.8, fbm(P, 1.0, seed + 5, 3)) * 0.5)
            fleck = sm(0.78, 0.86, vnoise(P, 0.35, seed + 7))
            col = mixc(col, hx("#d0ccc0") * (0.7 + 0.6 * lit)[:, None], fleck)
        col = col + (spec * 0.08)[:, None]
    elif mat == "flint":
        f1, f2, _ = cells(P, 1.4, seed)
        col = np.tile(hx("#2e2c2a"), (n, 1)) * (0.8 + 0.4 * sm(0.0, 0.2, f2 - f1))[:, None] + (spec * 0.9)[:, None]
        col = mixc(col, hx("#6a645a"), sm(0.7, 0.9, vnoise(P, 0.8, seed + 2)) * 0.4)
    elif mat in ("ingot", "steel_ingot"):
        base = hx("#a8acb2") if mat == "ingot" else hx("#4a5664")
        col = np.tile(base, (n, 1)) * (0.65 + 0.45 * lit)[:, None] * (0.92 + 0.1 * vnoise(P, 0.6, seed))[:, None] + (spec * 0.9)[:, None]
        stamp = (np.abs(x) < 2.4) & (np.abs(z) < 0.9) & (N[:, 1] > 0.9)
        col[stamp] *= 0.75
    elif mat == "crystal":
        t = sm(-4, 6, y)
        col = mixc(np.tile(hx("#6a3ab0"), (n, 1)), hx("#e0d0ff"), t * 0.6)
        col = col * (0.75 + 0.35 * np.abs(np.sin(np.arctan2(N[:, 2], N[:, 0]) * 3)))[:, None] + (spec * 1.1)[:, None]
        AO = np.ones(n)
    elif mat == "obsidian":
        col = np.tile(hx("#141018"), (n, 1)) * (0.8 + 0.4 * fbm(P, 1.0, seed, 3))[:, None]
        col = col + np.outer(sm(0.5, 0.95, lit), hx("#5a3a8a")) * 0.35 + (spec * 1.0)[:, None]
    elif mat == "hide":
        f1, f2, _ = cells(P, 0.5, seed)
        col = np.tile(hx("#8a6440"), (n, 1)) * (0.8 + 0.3 * fbm(P, 0.8, seed, 3))[:, None]
        col = mixc(col, col * 0.75, sm(0.06, 0.0, f2 - f1) * 0.5)
        fur = sm(0.6, 0.9, np.abs(N[:, 0]))
        col = mixc(col, hx("#5a4a36") * (0.8 + 0.3 * np.abs(np.sin(y * 9 + z * 7)))[:, None], fur * 0.6)
    elif mat in ("keratin", "tooth", "bone"):
        base = {"keratin": hx("#6a5a44"), "tooth": hx("#e8dcc0"), "bone": hx("#e0d4b8")}[mat]
        rings = np.abs(np.sin((x + y) * 2.2 + fbm(P, 0.6, seed, 2) * 2))
        col = mixc(np.tile(base, (n, 1)), base * 0.75, sm(0.5, 1.0, rings) * 0.4) * (0.9 + 0.15 * vnoise(P, 0.5, seed + 3))[:, None]
        if mat == "keratin":
            col = mixc(col, hx("#c8b48a"), sm(4, 8, np.hypot(x, y)) * 0.5)
        col = col + (spec * 0.3)[:, None]
    elif mat == "charcoal":
        f1, f2, _ = cells(P, 0.6, seed)
        col = np.tile(hx("#1e1c1a"), (n, 1)) * (0.8 + 0.6 * sm(0.0, 0.15, f2 - f1))[:, None] + (spec * 0.35)[:, None]
    elif mat == "pouch":
        col = np.tile(np.asarray(pal.get("pouch", hx("#7a5a3a"))), (n, 1)) * (0.8 + 0.3 * fbm(P, 0.6, seed, 3))[:, None]
        col = mixc(col, col * 0.7, sm(0.9, 1.0, np.abs(np.sin(y * 5))) * 0.4) + (spec * 0.15)[:, None]
    elif mat == "powder":
        col = np.tile(np.asarray(pal["powder"]), (n, 1)) * (0.7 + 0.5 * vnoise(P, 0.25, seed))[:, None]
        col = col + (sm(0.85, 0.95, vnoise(P, 0.15, seed + 2)) * 0.5)[:, None] * np.asarray(pal.get("spark", np.zeros(3)))
    elif mat == "clay":
        col = np.tile(hx("#9a5a3a"), (n, 1)) * (0.8 + 0.3 * fbm(P, 0.8, seed, 3))[:, None] + (spec * 0.1)[:, None]
    elif mat == "paste":
        col = np.tile(hx("#9a9a8e"), (n, 1)) * (0.85 + 0.25 * fbm(P, 0.5, seed, 3))[:, None] + (spec * 0.4)[:, None]
    elif mat == "polymer":
        col = np.tile(hx("#4a4058"), (n, 1)) * (0.7 + 0.4 * lit)[:, None] + (spec * 1.0)[:, None]
    elif mat == "pcb":
        trace = ((np.abs(np.sin(x * 3.0)) > 0.93) | (np.abs(np.sin(z * 3.0)) > 0.95)) & (N[:, 1] > 0.8)
        col = np.tile(hx("#1e5a2e"), (n, 1)) * (0.85 + 0.2 * lit)[:, None]
        col[trace] = hx("#c8902a")
        col = col + (spec * 0.3)[:, None]
    elif mat == "chip":
        col = np.tile(hx("#1a1a1c"), (n, 1)) * (0.8 + 0.3 * lit)[:, None] + (spec * 0.5)[:, None]
    else:
        return base_shade(mat, P, N, AO, ctx)
    col = col * (0.45 + 0.55 * AO)[:, None]
    return np.clip(col, 0, 1)


I.shade = shade          # export() bakes with the module's shade


def S():
    sc = Sculpt()
    sc.bone("root", None, (0, 0, 0))
    return sc


def rough_fn(c, r, seed, amp, sharp=False):
    c = np.asarray(c, float)
    r = np.asarray(r, float)
    rng = np.random.default_rng(seed)
    planes = [(normalize(rng.normal(size=3)), rng.uniform(0.55, 0.85)) for _ in range(6 if sharp else 4)]

    def f(P):
        q = (P - c) / r
        d = np.linalg.norm(q, axis=1) - 1.0
        for nrm, off in planes:
            dp = q @ nrm - off
            k = 0.05 if sharp else 0.2
            h = np.clip(0.5 + 0.5 * (dp - d) / k, 0, 1)
            d = dp * h + d * (1 - h) + k * h * (1 - h)
        return d * float(r.min()) + amp * (fbm(P, 1.2, seed, 3) - 0.5)
    return f


def rock_pile(mat, count=3, sharp=False, seed=3, size=1.0):
    sc = S()
    spots = [(0, 2.2, 0, 3.4), (3.6, 1.6, 1.4, 2.6), (-3.2, 1.5, 1.8, 2.4), (0.8, 1.4, -3.2, 2.2)][:count]
    for i, (x, y, z, r) in enumerate(spots):
        r *= size
        rr = np.array([r, r * 0.75, r * 0.9])
        c = np.array([x, y, z]) * size
        sc.func(rough_fn(c, rr, seed + i, 0.35 * size, sharp), c - rr * 1.3 - 1, c + rr * 1.3 + 1, mat, bone="root", k=0.2)
    return [I.sdf_part(sc.prims, 0.08, 2600)]


def wood():
    sc = S()
    sc.cone((-5.5, 0, 0), (5.5, 0, 0), 3.2, 3.0, mat="bark", bone="root", k=0.2)
    sc.cone((1.0, 2.2, 0.4), (4.0, 4.6, 0.8), 0.8, 0.4, mat="bark", bone="root", k=0.6)       # broken branch stub
    return [I.sdf_part(sc.prims, 0.08, 2400)], dict(axis=0), 128, "small"


def thatch():
    parts = []
    rng = np.random.default_rng(4)
    for i in range(46):
        a, rr = rng.uniform(0, 2 * math.pi), math.sqrt(rng.uniform(0, 1)) * 2.4
        x0, z0 = math.cos(a) * rr, math.sin(a) * rr
        spread = 1.0 + rng.uniform(0, 0.6)
        path = [np.array([x0 * spread, -7, z0 * spread]), np.array([x0, 0, z0]), np.array([x0 * spread * 1.1, 7, z0 * spread * 1.1])]
        v, f, n = K.tube_mesh(path, [0.22, 0.2, 0.12], n=4)
        parts.append(I.mesh_part(v, f, n, "straw"))
    v, f, n = I.ring_mesh((0, 0, 0), (0, 1, 0), 2.6, 0.35, 22, 6)
    parts.append(I.mesh_part(v, f, n, "twine"))
    return parts, {}, 128, "small"


def fiber():
    parts = []
    for strand in range(5):
        pts = []
        for i in range(60):
            t = i / 59
            a = t * 6 * math.pi + strand * 1.25
            r = 3.6 - 1.2 * t + 0.25 * math.sin(strand + t * 20)
            pts.append(np.array([math.cos(a) * r, -1.0 + t * 2.2 + strand * 0.25, math.sin(a) * r]))
        v, f, n = K.tube_mesh(pts, [0.42] * 60, n=5)
        parts.append(I.mesh_part(v, f, n, "fiber"))
    return parts, {}, 128, "small"


def ingot(mat):
    sc = S()
    sc.rbox((0, 0, 0), (5.8, 1.6, 2.6), 0.5, mat=mat, bone="root", k=0.2)
    sc.rbox((0, 1.6, 0), (4.8, 0.8, 2.0), 0.4, mat=mat, bone="root", k=0.5)
    return [I.sdf_part(sc.prims, 0.07, 1600)], {}, 128, "small"


def crystal():
    parts = []
    for (ax, h, r) in (((0, 1, 0), 9.0, 1.5), ((0.5, 1, 0.2), 6.0, 1.1), ((-0.5, 1, -0.3), 5.2, 1.0), ((0.1, 1, -0.6), 4.0, 0.8)):
        d = normalize(np.array(ax))
        base = np.array([0, -4.5, 0])
        v, f, n = K.tube_mesh([base, base + d * h * 0.8, base + d * h], [r, r, 0.05], n=6)
        parts.append(I.mesh_part(v, f, n, "crystal", ao=1.0))
    parts += rock_pile("rock", 1, seed=9, size=0.55)
    for p in parts[-1:]:
        p["v"][:, 1] -= 5.0
    return parts, {}, 128, "small"


def hide():
    parts = []
    pts = []
    for i in range(80):
        t = i / 79
        a = t * 3.2 * math.pi
        r = 0.8 + 2.4 * t
        pts.append((math.cos(a) * r, math.sin(a) * r))
    sc = S()
    for i in range(len(pts) - 1):
        (y0, z0), (y1, z1) = pts[i], pts[i + 1]
        sc.cone((-6, y0, z0), (6, y0, z0), 0.45, 0.45, mat="hide", bone="root", k=0.25)
    parts.append(I.sdf_part(sc.prims, 0.1, 2400))
    for xr in (-3.5, 3.5):
        v, f, n = I.ring_mesh((xr, 0, 0), (1, 0, 0), 3.4, 0.3, 22, 6)
        parts.append(I.mesh_part(v, f, n, "twine"))
    return parts, {}, 128, "small"


def curved(mat, L, r0, bend, n=9):
    """horn / tooth: a tapering cone bent into an arc (smooth SDF, no twisting)"""
    sc = S()
    R = L / max(bend, 1e-3)
    pts, radii, bones = [], [], []
    for i in range(n):
        t = i / (n - 1)
        a = t * bend
        pts.append((R * (1 - math.cos(a)), R * math.sin(a) - L * 0.4, 0.0))
        radii.append(r0 * (1 - t) ** 0.85 + 0.08)
        bones.append("root")
    sc.spline_tube(pts, radii, bones, mat=mat, k=0.3, sub=4)
    return I.sdf_part(sc.prims, 0.06, 1600)


def keratin():
    return [curved("keratin", 13, 2.4, 1.3)], {}, 128, "small"


def tooth():
    p = curved("tooth", 11, 1.9, 0.6)
    p["v"][:, 2] *= 0.6
    return [p], {}, 128, "small"


def bone():
    sc = S()
    sc.cone((-6, 0, 0), (6, 0, 0), 1.3, 1.2, mat="bone", bone="root", k=0.3)
    for s in (-1, 1):
        sc.ell((s * 6.4, 0.9, 0), (1.8, 1.4, 1.5), mat="bone", bone="root", k=0.6)
        sc.ell((s * 6.4, -0.9, 0.3), (1.6, 1.3, 1.4), mat="bone", bone="root", k=0.6)
    return [I.sdf_part(sc.prims, 0.08, 2000)], {}, 128, "small"


def steak(cooked):
    sc = S()
    m = "cooked" if cooked else "meat"
    sc.ell((0, 0, 0), (5.2, 1.7, 3.8), mat=m, bone="root", k=0.8)
    sc.ell((-2.6, 0.05, 1.4), (2.6, 1.5, 2.2), mat=m, bone="root", k=0.9)
    sc.ell((2.4, 0.0, -1.2), (2.6, 1.5, 2.3), mat=m, bone="root", k=0.9)

    def edge(P):
        q = P / np.array([5.6, 1.0, 4.2])
        return np.clip(np.hypot(q[:, 0], q[:, 2]), 0, 1.2)
    return [I.sdf_part(sc.prims, 0.09, 2400)], dict(edge=edge), 128, "small"


def charcoal():
    return rock_pile("charcoal", 4, sharp=True, seed=21, size=0.8), {}, 128, "small"


def pouch(powder, spark):
    sc = S()
    sc.ell((0, -1.0, 0), (4.4, 4.0, 4.2), mat="pouch", bone="root", k=0.6)
    sc.cone((0, 2.4, 0), (0, 4.6, 0), 1.6, 2.4, mat="pouch", bone="root", k=0.8)
    sc.ell((0, 4.8, 0), (2.6, 0.9, 2.6), mat="powder", bone="root", k=0.5)
    parts = [I.sdf_part(sc.prims, 0.08, 2600)]
    v, f, n = I.ring_mesh((0, 2.9, 0), (0, 1, 0), 1.75, 0.28, 18, 6)
    parts.append(I.mesh_part(v, f, n, "twine"))
    return parts, dict(pal=dict(powder=hx(powder), spark=hx(spark), pouch=hx("#6a4a2e"))), 128, "small"


def paste_bowl():
    sc = S()
    sc.ell((0, 0, 0), (5.4, 2.6, 5.4), mat="clay", bone="root", k=0.3)
    sc.ell((0, 1.6, 0), (4.5, 2.4, 4.5), mat="clay", bone="root", op="sub", k=0.25)       # hollow
    sc.ell((0, -0.2, 0), (4.1, 1.5, 4.1), mat="paste", bone="root", k=0.15)              # paste filling it
    sc.ell((1.0, 0.9, -0.6), (1.6, 0.6, 1.4), mat="paste", bone="root", k=0.4)
    return [I.sdf_part(sc.prims, 0.07, 2600)], {}, 128, "small"


def polymer():
    sc = S()
    for i in range(3):
        sc.rbox((0.3 * i, -1.6 + i * 1.3, -0.4 * i), (5.2, 0.5, 3.8), 0.35, mat="polymer", bone="root", k=0.05, rot=(0, 0.15 * i, 0))
    return [I.sdf_part(sc.prims, 0.06, 2000)], {}, 128, "small"


def electronics():
    sc = S()
    sc.rbox((0, 0, 0), (6.0, 0.4, 4.2), 0.2, mat="pcb", bone="root", k=0.05)
    for (cx, cz, w, d) in ((-2.6, -1.2, 1.6, 1.6), (1.2, 1.0, 2.2, 1.2), (3.6, -1.6, 1.0, 1.0), (-0.6, 2.2, 1.0, 0.7)):
        sc.rbox((cx, 0.7, cz), (w, 0.35, d), 0.12, mat="chip", bone="root", k=0.03)
    return [I.sdf_part(sc.prims, 0.05, 2400)], {}, 128, "small"


# ---------------------------------------------------------------------------------------------- tools (along +x)
def handle(x0=-8.6, x1=5.0, r=0.55, mat="wood"):
    v, f, n = K.tube_mesh([np.array([x0, 0, 0]), np.array([x1, 0, 0])], [r, r * 0.92], n=10)
    return I.mesh_part(v, f, n, mat)


def lashing(x, r=0.75):
    v, f, n = I.ring_mesh((x, 0, 0), (1, 0, 0), r, 0.18, 14, 5)
    return I.mesh_part(v, f, n, "twine")


def pick(metal):
    parts = [handle()]
    m = "steel" if metal else "rock"
    sc = S()
    sc.cone((4.4, 0, 0), (4.0, 4.6, 0), 1.2, 0.18, mat=m, bone="root", k=0.4)
    sc.cone((4.4, 0, 0), (4.0, -4.6, 0), 1.2, 0.18, mat=m, bone="root", k=0.4)
    sc.ell((4.5, 0, 0), (1.3, 1.4, 1.1), mat=m, bone="root", k=0.4)
    parts.append(I.sdf_part(sc.prims, 0.06, 1800))
    if not metal:
        parts.append(lashing(4.5, 1.25))
    return parts, {}, 128, "long"


def hatchet(metal):
    parts = [handle()]
    m = "steel" if metal else "rock"
    sc = S()
    sc.rbox((4.6, 1.6, 0), (1.0, 2.4, 0.45), 0.25, mat=m, bone="root", k=0.2)
    sc.cone((4.6, 3.6, 0), (6.4, 3.8, 0), 0.5, 0.1, mat=m, bone="root", k=0.5)
    sc.rbox((5.4, 2.0, 0), (0.9, 2.9, 0.22), 0.15, mat=m, bone="root", k=0.4)
    parts.append(I.sdf_part(sc.prims, 0.05, 1800))
    if not metal:
        parts.append(lashing(4.6, 0.9))
    return parts, {}, 128, "long"


def sickle():
    parts = [handle(-8.6, 0.5)]
    pts, radii = [], []
    for i in range(16):
        t = i / 15
        a = -0.3 + t * 2.4
        pts.append(np.array([1.0 + 4.2 * math.sin(a), 4.2 * (1 - math.cos(a)) * 0.9, 0]))
        radii.append(0.62 * (1 - t) ** 0.5 + 0.05)
    v, f, n = K.tube_mesh(pts, radii, n=6)
    v[:, 2] *= 0.35
    parts.append(I.mesh_part(v, f, n, "steel"))
    return parts, {}, 128, "long"


def club():
    v, f, n = K.tube_mesh([np.array([-8.6, 0, 0]), np.array([-2, 0, 0]), np.array([4.6, 0, 0]), np.array([6.8, 0, 0])], [0.6, 0.9, 1.7, 1.2], n=10)
    parts = [I.mesh_part(v, f, n, "wood")]
    for (x, a) in ((1.0, 0.5), (3.2, 2.6), (4.8, 4.4)):
        v, f, n = K.sphere_mesh((x, math.cos(a) * 1.4, math.sin(a) * 1.4), 0.5, 8, 6)
        parts.append(I.mesh_part(v, f, n, "wood"))
    return parts, {}, 128, "long"


def spear():
    parts = [handle(-10.5, 6.0, 0.42)]
    sc = S()
    sc.cone((6.0, 0, 0), (10.5, 0, 0), 1.0, 0.05, mat="flint", bone="root", k=0.2, sq=(1.0, 0.35))
    parts.append(I.sdf_part(sc.prims, 0.05, 900))
    parts.append(lashing(6.2, 0.6))
    return parts, {}, 128, "long"


def sword():
    sc = S()
    sc.cone((-1.0, 0, 0), (9.6, 0, 0), 0.95, 0.08, mat="steel", bone="root", k=0.1, sq=(1.0, 0.18))
    sc.rbox((-1.4, 0, 0), (0.35, 2.4, 0.45), 0.2, mat="gold", bone="root", k=0.1)
    parts = [I.sdf_part(sc.prims, 0.05, 1600)]
    v, f, n = K.tube_mesh([np.array([-5.4, 0, 0]), np.array([-1.6, 0, 0])], [0.5, 0.5], n=8)
    parts.append(I.mesh_part(v, f, n, "leather"))
    v, f, n = K.sphere_mesh((-5.8, 0, 0), 0.75, 10, 8)
    parts.append(I.mesh_part(v, f, n, "gold"))
    return parts, dict(pal=dict(leather=hx("#3a2616"))), 128, "long"


def bow():
    pts, radii = [], []
    for i in range(21):
        t = i / 20
        a = (t - 0.5) * 2.2
        pts.append(np.array([math.sin(a) * 8.6, 0, (1 - math.cos(a)) * 4.2]))
        radii.append(0.55 - 0.25 * abs(t - 0.5) * 2)
    v, f, n = K.tube_mesh(pts, radii, n=7)
    parts = [I.mesh_part(v, f, n, "wood")]
    v, f, n = K.tube_mesh([pts[0], pts[-1]], [0.08, 0.08], n=4)
    parts.append(I.mesh_part(v, f, n, "twine"))
    v, f, n = K.tube_mesh([np.array([-1.2, 0, 0.1]), np.array([1.2, 0, 0.1])], [0.7, 0.7], n=8)
    parts.append(I.mesh_part(v, f, n, "leather"))
    return parts, {}, 128, "long"


def stone_arrow():
    parts, ctx, tex, kind = I.tranq_arrow()
    # replace the dart (vial + needle + collar) by a knapped stone tip: drop the last 4 parts
    parts = parts[:-4]
    sc = S()
    sc.cone((4.8, 0, 0), (8.6, 0, 0), 0.8, 0.05, mat="flint", bone="root", k=0.2, sq=(1.0, 0.3))
    parts.append(I.sdf_part(sc.prims, 0.05, 700))
    ctx["pal"]["fletch"] = hx("#8a6a40")
    return parts, ctx, tex, kind


ITEMS = {
    "wood": wood, "thatch": thatch, "fiber": fiber,
    "stone": lambda: (rock_pile("rock", 3, seed=3), {}, 128, "small"),
    "flint": lambda: (rock_pile("flint", 2, sharp=True, seed=7, size=0.85), {}, 128, "small"),
    "metal_ore": lambda: (rock_pile("ore", 2, seed=11), {}, 128, "small"),
    "metal_ingot": lambda: ingot("ingot"), "steel_ingot": lambda: ingot("steel_ingot"),
    "crystal": crystal,
    "obsidian": lambda: (rock_pile("obsidian", 2, sharp=True, seed=13, size=0.9), {}, 128, "small"),
    "hide": hide, "keratin": keratin, "dino_tooth": tooth, "dino_bone": bone,
    "raw_meat": lambda: steak(False), "cooked_meat": lambda: steak(True),
    "charcoal": charcoal,
    "sparkpowder": lambda: pouch("#e06a2a", "#ffd060"), "gunpowder": lambda: pouch("#2a2826", "#5a5650"),
    "cementing_paste": paste_bowl, "polymer": polymer, "electronics": electronics,
    "stone_pick": lambda: pick(False), "metal_pick": lambda: pick(True),
    "stone_hatchet": lambda: hatchet(False), "metal_hatchet": lambda: hatchet(True),
    "metal_sickle": sickle, "wooden_club": club, "stone_spear": spear, "metal_sword": sword,
    "primitive_bow": bow, "stone_arrow": stone_arrow,
}

if __name__ == "__main__":
    root = sys.argv[1]
    want = sys.argv[2:]
    prev = []
    for name, fn in ITEMS.items():
        if want and name not in want:
            continue
        parts, ctx, tex, kind = fn()
        um = I.export(root, name, parts, ctx, tex, kind, prev)
        print("%-18s %5d tris" % (name, len(um["f"])), flush=True)
    if os.environ.get("ITEM_PREVIEW"):
        import view
        from PIL import Image
        tiles = [view.render(um["v"], um["n"], um["f"], size=(200, 200), yaw=-0.55, pitch=0.38, tex=img, uv=um["uv"], ground=False)
                 for name, um, img in prev]
        Image.fromarray((view.sheet(tiles, 8) * 255).astype(np.uint8)).save(os.environ["ITEM_PREVIEW"])
