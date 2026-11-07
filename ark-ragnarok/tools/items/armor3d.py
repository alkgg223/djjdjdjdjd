"""3D armour (cloth, hide, chitin, flak, riot - helmet, chestplate, leggings, boots) and the tranquilizer arrow family,
same pipeline as items3d.py (sculpt -> mesh -> UV -> baked texture -> OBJ item model), plus the textures worn on the
body (textures/models/armor/<set>_layer_1/2.png, the humanoid armour layout at 128 x 64).

    python3 armor3d.py <resources dir> [item ...]
"""
import math
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import items3d as I
import economy3d as E
import sdfkit as K
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, LIGHT

eco_shade = E.shade

SETS = {
    "cloth": dict(base="#b4a078", dark="#7a6a4a", trim="#8a7a58"),
    "hide": dict(base="#7a5434", dark="#4a3220", trim="#c8b8a0"),
    "chitin": dict(base="#4a4a2a", dark="#22200e", trim="#8a7a3a"),
    "flak": dict(base="#5a5e44", dark="#2e3022", trim="#3a2a1a"),
    "riot": dict(base="#1e2632", dark="#0c1018", trim="#5a6a7a"),
}


def shade(mat, P, N, AO, ctx):
    n = len(P)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    seed = ctx.get("seed", 5)
    lit = np.clip((N * LIGHT).sum(axis=1), 0, 1)
    spec = sm(0.82, 0.98, lit)
    st = SETS.get(ctx.get("set", "cloth"))
    if mat == "cloth":
        weave = np.abs(np.sin(x * 7.0)) * 0.5 + np.abs(np.sin((y + z) * 7.0)) * 0.5
        col = np.tile(hx(st["base"]), (n, 1)) * (0.82 + 0.18 * weave)[:, None] * (0.9 + 0.15 * fbm(P, 0.7, seed, 3))[:, None]
        col = mixc(col, hx(st["dark"]), sm(0.62, 0.8, fbm(P, 0.25, seed + 4, 2)) * 0.35)       # dirt and wear
    elif mat == "fur":
        strands = np.abs(np.sin(y * 9 + fbm(P, 0.4, seed, 3) * 8))
        col = mixc(np.tile(hx(st["trim"]), (n, 1)), hx(st["trim"]) * 0.6, sm(0.3, 1.0, strands) * 0.6) * (0.85 + 0.2 * vnoise(P, 0.3, seed))[:, None]
    elif mat == "chitin":
        band = np.abs(np.sin(y * 2.2 + x * 0.4))
        col = np.tile(hx(st["base"]), (n, 1)) * (0.7 + 0.35 * band)[:, None]
        col = mixc(col, hx(st["dark"]), sm(0.92, 1.0, band) * 0.8)                                 # the seams between the plates
        col = mixc(col, hx(st["trim"]), sm(0.55, 0.85, fbm(P, 0.8, seed, 3)) * 0.25)
        col = col + (spec * 0.9)[:, None]
    elif mat == "flak":
        col = np.tile(hx(st["base"]), (n, 1)) * (0.75 + 0.3 * lit)[:, None] * (0.9 + 0.12 * fbm(P, 0.9, seed, 3))[:, None]
        chip = sm(0.72, 0.8, fbm(P, 0.5, seed + 3, 3))
        col = mixc(col, hx("#9a9ca0") * (0.7 + 0.5 * lit)[:, None], chip * 0.6) + (spec * 0.45)[:, None]
    elif mat == "riot":
        col = np.tile(hx(st["base"]), (n, 1)) * (0.6 + 0.5 * lit)[:, None] + (spec * 1.1)[:, None]
        col = mixc(col, hx(st["trim"]), sm(0.94, 1.0, np.abs(np.sin(y * 1.6))) * 0.5)
    elif mat == "visor":
        fres = 1 - np.abs(N[:, 2])
        col = np.tile(hx("#10202e"), (n, 1)) + np.outer(fres, hx("#4a7aa0")) * 0.6 + (spec * 1.4)[:, None]
        AO = np.ones(n)
    elif mat == "strap":
        col = np.tile(hx(st["trim"]), (n, 1)) * (0.8 + 0.3 * fbm(P, 0.6, seed, 2))[:, None] + (spec * 0.2)[:, None]
    elif mat == "sole":
        col = np.tile(hx("#1c1a18"), (n, 1)) * (0.8 + 0.3 * vnoise(P, 0.4, seed))[:, None] + (spec * 0.15)[:, None]
    elif mat == "rivet":
        col = np.tile(hx("#b8b8b0"), (n, 1)) * (0.6 + 0.6 * lit)[:, None] + (spec * 1.2)[:, None]
    elif mat == "coil":
        col = np.tile(hx("#c8862a"), (n, 1)) * (0.6 + 0.5 * lit)[:, None] + (spec * 1.0)[:, None]
    elif mat == "spark":
        col = np.tile(hx("#7ad8ff"), (n, 1)) * (0.9 + 0.3 * lit)[:, None]
        AO = np.ones(n)
    else:
        return eco_shade(mat, P, N, AO, ctx)
    col = col * (0.45 + 0.55 * AO)[:, None]
    return np.clip(col, 0, 1)


I.shade = shade
E.I.shade = shade


def S():
    return E.S()


def body_mat(set_id):
    return {"cloth": "cloth", "hide": "hide", "chitin": "chitin", "flak": "flak", "riot": "riot"}[set_id]


# ------------------------------------------------------------------------------------------------ armour pieces
def helmet(set_id):
    m = body_mat(set_id)
    sc = S()
    parts = []
    if set_id == "cloth":                                   # a hood with a drape down the back
        sc.ell((0, 1.0, 0), (6.0, 6.4, 6.4), mat=m, bone="root", k=0.6)
        sc.rbox((0, -3.5, 3.4), (5.2, 3.2, 1.6), 1.2, mat=m, bone="root", k=1.0)
        sc.ell((0, 0.2, -4.6), (3.6, 4.0, 3.0), mat=m, bone="root", op="sub", k=0.5)
        sc.ell((0, 0.4, 0.4), (5.0, 5.6, 5.4), mat=m, bone="root", op="sub", k=0.3)
    elif set_id == "hide":                                  # leather cap with ear flaps and a fur rim
        sc.ell((0, 1.4, 0), (5.8, 5.4, 6.0), mat=m, bone="root", k=0.6)
        for sx in (-1, 1):
            sc.rbox((sx * 5.4, -2.0, 0.4), (0.8, 3.0, 2.4), 0.7, mat=m, bone="root", k=0.6)
        sc.ell((0, 0.2, 0.2), (5.0, 5.0, 5.2), mat=m, bone="root", op="sub", k=0.3)
        sc.rbox((0, -4.0, 0), (8, 3.5, 8), 0.1, mat=m, bone="root", op="sub", k=0.2)
        v, f, n = I.ring_mesh((0, -0.2, 0), (0, 1, 0), 5.9, 0.75, 24, 8)
        parts.append(I.mesh_part(v, f, n, "fur"))
    elif set_id == "chitin":                                # overlapping shell plates, cheek guards, two small horns
        for i, (yy, rr) in enumerate(((3.6, 5.2), (1.8, 6.0), (0.0, 6.3))):
            sc.ell((0, yy, 0.3 * i), (rr, 2.4, rr * 1.05), mat=m, bone="root", k=0.3)
        for sx in (-1, 1):
            sc.rbox((sx * 5.6, -1.8, -1.6), (0.9, 2.6, 2.2), 0.6, mat=m, bone="root", k=0.5)
            sc.cone((sx * 3.2, 4.6, 1.0), (sx * 4.4, 8.0, 3.2), 1.0, 0.1, mat=m, bone="root", k=0.6)
        sc.ell((0, 0.4, 0.2), (5.0, 5.0, 5.2), mat=m, bone="root", op="sub", k=0.3)
        sc.rbox((0, -4.4, 0), (8, 3.2, 8), 0.1, mat=m, bone="root", op="sub", k=0.2)
    elif set_id == "flak":                                  # combat helmet with a brim, chin strap and side rivets
        sc.ell((0, 1.6, 0), (6.2, 5.4, 6.6), mat=m, bone="root", k=0.6)
        sc.ell((0, -0.6, 0), (7.0, 0.9, 7.4), mat=m, bone="root", k=0.8)
        sc.ell((0, 0.6, 0), (5.4, 4.8, 5.8), mat=m, bone="root", op="sub", k=0.3)
        sc.rbox((0, -3.6, 0), (9, 2.8, 9), 0.1, mat=m, bone="root", op="sub", k=0.2)
        for sx in (-1, 1):
            parts.append(I.mesh_part(*K.sphere_mesh((sx * 6.0, 0.6, 0), 0.45, 8, 6), "rivet"))
        v, f, n = K.tube_mesh([np.array([-5.6, -0.4, 0.4]), np.array([-3.0, -4.6, -1.6]), np.array([3.0, -4.6, -1.6]),
                               np.array([5.6, -0.4, 0.4])], [0.35] * 4, n=6)
        parts.append(I.mesh_part(v, f, n, "strap"))
    else:                                                   # riot: full helmet with a tinted face shield
        sc.ell((0, 1.0, 0.4), (6.2, 6.6, 6.8), mat=m, bone="root", k=0.6)
        sc.rbox((0, -3.8, -0.8), (5.0, 1.6, 4.6), 1.2, mat=m, bone="root", k=0.8)
        sc.ell((0, 0.6, -5.6), (5.0, 3.4, 2.0), mat="visor", bone="root", k=0.4)
        sc.ell((0, 0.6, 0.6), (5.2, 5.6, 5.6), mat=m, bone="root", op="sub", k=0.3)
    parts.insert(0, I.sdf_part(sc.prims, 0.08, 3600))
    return parts, dict(set=set_id), 256, "small"


def chestplate(set_id):
    m = body_mat(set_id)
    sc = S()
    parts = []
    sc.rbox((0, 0, 0), (5.2, 6.6, 3.0), 1.8, mat=m, bone="root", k=0.6)
    for sx in (-1, 1):
        sc.ell((sx * 5.6, 4.6, 0), (2.6, 2.2, 3.0), mat=m if set_id != "hide" else "fur", bone="root", k=0.8)
    sc.ell((0, 6.8, 0), (2.4, 1.8, 2.2), mat=m, bone="root", op="sub", k=0.4)              # neck opening
    if set_id == "cloth":
        v, f, n = I.ring_mesh((0, -3.4, 0), (0, 1, 0), 4.4, 0.45, 26, 6)
        parts.append(I.mesh_part(v * np.array([1.2, 1, 0.75]), f, n, "twine"))
    elif set_id == "hide":
        v, f, n = I.ring_mesh((0, 6.2, 0), (0, 1, 0), 3.0, 0.8, 20, 8)
        parts.append(I.mesh_part(v, f, n, "fur"))
        v, f, n = K.tube_mesh([np.array([-4.0, 5.6, -3.0]), np.array([4.2, -5.6, -3.0])], [0.45, 0.45], n=6)
        parts.append(I.mesh_part(v, f, n, "strap"))
    elif set_id == "chitin":
        for i in range(4):                                  # segmented belly plates
            sc.rbox((0, -5.4 + i * 2.2, -3.0), (3.6 - i * 0.2, 0.9, 0.7), 0.5, mat=m, bone="root", k=0.3)
    elif set_id == "flak":
        for sx in (-1, 1):
            sc.rbox((sx * 2.4, 1.2, -3.2), (2.0, 4.2, 0.6), 0.5, mat=m, bone="root", k=0.3)
            for yy in (4.4, -2.2):
                parts.append(I.mesh_part(*K.sphere_mesh((sx * 3.8, yy, -3.85), 0.35, 8, 6), "rivet"))
        v, f, n = I.ring_mesh((0, -4.6, 0), (0, 1, 0), 4.4, 0.5, 26, 6)
        parts.append(I.mesh_part(v * np.array([1.25, 1, 0.8]), f, n, "strap"))
    else:
        sc.rbox((0, 2.0, -3.3), (3.6, 2.6, 0.5), 0.6, mat=m, bone="root", k=0.3)
        sc.rbox((0, -3.0, -3.3), (3.0, 1.8, 0.5), 0.6, mat=m, bone="root", k=0.3)
        v, f, n = K.tube_mesh([np.array([-2.6, 0.1, -3.9]), np.array([2.6, 0.1, -3.9])], [0.22, 0.22], n=6)
        parts.append(I.mesh_part(v, f, n, "visor"))
    parts.insert(0, I.sdf_part(sc.prims, 0.08, 3600))
    return parts, dict(set=set_id), 256, "small"


def leggings(set_id):
    m = body_mat(set_id)
    sc = S()
    parts = []
    sc.rbox((0, 5.6, 0), (4.6, 1.4, 2.8), 1.0, mat=m, bone="root", k=0.6)
    for sx in (-1, 1):
        sc.cone((sx * 2.3, 5.0, 0), (sx * 2.5, -7.0, 0.2), 2.3, 1.8, mat=m, bone="root", k=0.6)
        if set_id in ("chitin", "flak", "riot"):
            sc.ell((sx * 2.5, -1.6, -1.9), (1.7, 1.6, 1.0), mat=m, bone="root", k=0.4)          # knee guards
        if set_id == "hide":
            v, f, n = I.ring_mesh((sx * 2.5, -6.0, 0.2), (0, 1, 0), 2.0, 0.55, 16, 6)
            parts.append(I.mesh_part(v, f, n, "fur"))
    if set_id in ("cloth", "flak"):
        v, f, n = I.ring_mesh((0, 5.6, 0), (0, 1, 0), 3.9, 0.45, 26, 6)
        parts.append(I.mesh_part(v * np.array([1.25, 1, 0.8]), f, n, "twine" if set_id == "cloth" else "strap"))
    parts.insert(0, I.sdf_part(sc.prims, 0.08, 3600))
    return parts, dict(set=set_id), 256, "small"


def boots(set_id):
    m = body_mat(set_id)
    sc = S()
    parts = []
    for sx in (-1, 1):
        sc.cone((sx * 3.0, 3.6, 0.6), (sx * 3.0, -3.4, 0.4), 1.9, 2.0, mat=m, bone="root", k=0.5)
        sc.rbox((sx * 3.0, -4.6, -1.0), (1.9, 1.2, 3.0), 0.9, mat=m, bone="root", k=0.6)
        sc.rbox((sx * 3.0, -5.9, -1.0), (2.0, 0.35, 3.1), 0.3, mat="sole", bone="root", k=0.2)
        if set_id in ("flak", "riot"):
            sc.ell((sx * 3.0, -4.6, -3.4), (1.5, 1.0, 1.0), mat="rivet" if set_id == "flak" else m, bone="root", k=0.3)
        if set_id == "hide":
            v, f, n = I.ring_mesh((sx * 3.0, 3.4, 0.6), (0, 1, 0), 1.9, 0.6, 16, 6)
            parts.append(I.mesh_part(v, f, n, "fur"))
        if set_id == "cloth":
            for yy in (2.0, 0.0, -2.0):
                v, f, n = I.ring_mesh((sx * 3.0, yy, 0.5), (0, 1, 0), 2.0, 0.18, 16, 4)
                parts.append(I.mesh_part(v, f, n, "twine"))
    parts.insert(0, I.sdf_part(sc.prims, 0.08, 3600))
    return parts, dict(set=set_id), 256, "small"


# ------------------------------------------------------------------------------------------------ tranquilizer arrows
def potent_tranq_arrow():
    parts, ctx, tex, kind = I.tranq_arrow()
    # a longer vial and a forged broadhead
    sc = S()
    sc.cone((8.1, 0, 0), (10.6, 0, 0), 0.9, 0.04, mat="steel", bone="root", k=0.2, sq=(1.0, 0.25))
    parts = parts[:-1] + [I.sdf_part(sc.prims, 0.05, 900)]
    ctx["pal"]["liquid"] = hx("#7a2ad8")
    ctx["pal"]["fletch"] = hx("#5a1a8a")
    return parts, ctx, tex, kind


def shock_tranq_arrow():
    parts, ctx, tex, kind = I.tranq_arrow()
    # copper coils round the vial and a glowing charge at the tip
    for xx in (5.9, 6.6, 7.3):
        v, f, n = I.ring_mesh((xx, 0, 0), (1, 0, 0), 0.6, 0.12, 14, 5)
        parts.append(I.mesh_part(v, f, n, "coil"))
    parts.append(I.mesh_part(*K.sphere_mesh((9.2, 0, 0), 0.32, 10, 8), "spark"))
    ctx["pal"]["liquid"] = hx("#2ad8e8")
    ctx["pal"]["fletch"] = hx("#e8c82a")
    return parts, ctx, tex, kind


def tranq_bolt():
    parts, ctx, tex, kind = I.tranq_arrow()
    # a thick heavy bolt: fatter shaft, short stiff vanes, a big vial
    out = []
    v, f, n = K.tube_mesh([np.array([x, 0, 0], float) for x in np.linspace(-7.0, 5.0, 8)], [0.48] * 8, n=10)
    out.append(I.mesh_part(v, f, n, "wood"))
    out += parts[2:5]
    for (x0, x1, r0, r1, mt) in ((5.0, 5.8, 0.66, 0.66, "brass"), (5.8, 8.2, 0.78, 0.78, "liquid"), (8.2, 8.7, 0.66, 0.4, "brass")):
        v, f, n = K.tube_mesh([np.array([x0, 0, 0]), np.array([x1, 0, 0])], [r0, r1], n=12)
        out.append(I.mesh_part(v, f, n, mt))
    sc = S()
    sc.cone((8.7, 0, 0), (11.2, 0, 0), 0.7, 0.05, mat="steel", bone="root", k=0.2)
    out.append(I.sdf_part(sc.prims, 0.05, 900))
    ctx["pal"]["liquid"] = hx("#d82a2a")
    ctx["pal"]["fletch"] = hx("#3a2a1a")
    return out, ctx, tex, kind


def narco_dart():
    parts, ctx, tex, kind = I.tranq_arrow()
    # a light dart: thin shaft, a berry-juice vial, plain fletching
    ctx["pal"]["liquid"] = hx("#3a3a6a")
    ctx["pal"]["fletch"] = hx("#c8c0a8")
    return parts, ctx, tex, kind


ITEMS = {}
for sid in SETS:
    ITEMS[sid + "_helmet"] = (lambda sid=sid: helmet(sid))
    ITEMS[sid + "_chestplate"] = (lambda sid=sid: chestplate(sid))
    ITEMS[sid + "_leggings"] = (lambda sid=sid: leggings(sid))
    ITEMS[sid + "_boots"] = (lambda sid=sid: boots(sid))
ITEMS["potent_tranq_arrow"] = potent_tranq_arrow
ITEMS["shock_tranq_arrow"] = shock_tranq_arrow
ITEMS["tranq_bolt"] = tranq_bolt
ITEMS["narco_dart"] = narco_dart


# ------------------------------------------------------------------------------------------------ worn textures
def cube_faces(u, v, w, h, d):
    """the six faces of a humanoid-model box in its texture: name -> (x0, y0, x1, y1)"""
    return {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + w + w, v + d),
            "right": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
            "left": (u + d + w, v + d, u + d + w + d, v + d + h), "back": (u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h)}


def paint_layer(set_id, layer, out):
    W, H, s = 128, 64, 2
    st = SETS[set_id]
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    P = np.stack([xx * 0.5, yy * 0.5, np.zeros_like(xx)], axis=-1).reshape(-1, 3)
    base = np.asarray(hx(st["base"]))
    dark = np.asarray(hx(st["dark"]))
    noise = fbm(P, 0.35, 11, 4).reshape(H, W)
    img = np.zeros((H, W, 4))
    col = base[None, None, :] * (0.82 + 0.3 * noise)[..., None]
    if set_id == "cloth":
        weave = (np.abs(np.sin(xx * 1.6)) * 0.5 + np.abs(np.sin(yy * 1.6)) * 0.5)
        col = col * (0.85 + 0.2 * weave)[..., None]
    elif set_id == "hide":
        f1, f2, _ = cells(P * 0.6, 1.0, 5)
        crack = sm(0.05, 0.0, (f2 - f1).reshape(H, W))
        col = mixc(col.reshape(-1, 3), dark, crack.reshape(-1) * 0.5).reshape(H, W, 3)
    elif set_id == "chitin":
        band = np.abs(np.sin(yy * 0.9))
        col = col * (0.75 + 0.35 * band)[..., None]
        col = mixc(col.reshape(-1, 3), dark, (sm(0.93, 1.0, band)).reshape(-1) * 0.9).reshape(H, W, 3)
    elif set_id == "flak":
        chip = sm(0.7, 0.78, fbm(P, 0.6, 3, 3).reshape(H, W))
        col = mixc(col.reshape(-1, 3), hx("#9a9ca0"), chip.reshape(-1) * 0.5).reshape(H, W, 3)
    else:
        col = col * (0.8 + 0.4 * sm(0.3, 1.0, noise))[..., None]
    mask = np.zeros((H, W), bool)
    edge = np.zeros((H, W))
    boxes = []
    if layer == 1:
        boxes += [("head", cube_faces(0, 0, 8, 8, 8)), ("body", cube_faces(16, 16, 8, 12, 4)), ("arm", cube_faces(40, 16, 4, 12, 4)),
                  ("boot", cube_faces(0, 16, 4, 12, 4))]
    else:
        boxes += [("waist", cube_faces(16, 16, 8, 12, 4)), ("leg", cube_faces(0, 16, 4, 12, 4))]
    for kind, faces in boxes:
        for fname, (x0, y0, x1, y1) in faces.items():
            X0, Y0, X1, Y1 = x0 * s, y0 * s, x1 * s, y1 * s
            if kind == "boot" and fname not in ("bottom",) and fname != "top":
                Y0 = max(Y0, (16 + 4 + 7) * s)                  # boots: only the foot end of the leg box
            if kind == "waist" and fname in ("front", "back", "left", "right"):
                Y0 = max(Y0, (16 + 4 + 8) * s)                  # leggings: the hips
            if X1 <= X0 or Y1 <= Y0:
                continue
            mask[Y0:Y1, X0:X1] = True
            # darker seams at the face borders, a lighter worn middle
            fy, fx = np.mgrid[Y0:Y1, X0:X1]
            dx = np.minimum(fx - X0, X1 - 1 - fx)
            dy = np.minimum(fy - Y0, Y1 - 1 - fy)
            edge[Y0:Y1, X0:X1] = np.maximum(edge[Y0:Y1, X0:X1], sm(2.5, 0.0, np.minimum(dx, dy).astype(float)))
            if set_id == "flak" and fname == "front" and kind in ("body", "head"):
                for ry in range(Y0 + 3, Y1 - 2, 6):
                    for rx in (X0 + 2, X1 - 3):
                        img[ry:ry + 2, rx:rx + 2, :3] = hx("#c8c8c0")
            if set_id == "riot" and kind == "head" and fname == "front":
                col[Y0 + 5:Y1 - 4, X0 + 2:X1 - 2] = hx("#14283a") * 1.0 + 0.15
            if set_id in ("hide", "cloth") and fname in ("front", "back"):
                for sy in range(Y0 + 2, Y1 - 1, 3):          # stitching down the middle
                    cx = (X0 + X1) // 2
                    col[sy:sy + 1, cx:cx + 1] = hx("#d8c8a0")
    col = col * (1 - 0.35 * edge)[..., None]
    img[..., :3] = np.where(img[..., :3] > 0, img[..., :3], col)
    img[..., 3] = mask.astype(float)
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(out)


if __name__ == "__main__":
    root = sys.argv[1]
    want = sys.argv[2:]
    prev = []
    for name, fn in ITEMS.items():
        if want and name not in want:
            continue
        parts, ctx, tex, kind = fn()
        um = I.export(root, name, parts, ctx, tex, kind, prev)
        print("%-22s %5d tris" % (name, len(um["f"])), flush=True)
    if not want or "layers" in want:
        d = root + "/assets/arkragnarok/textures/models/armor"
        os.makedirs(d, exist_ok=True)
        for sid in SETS:
            for layer in (1, 2):
                paint_layer(sid, layer, "%s/%s_layer_%d.png" % (d, sid, layer))
        print("armour layers written")
    if os.environ.get("ITEM_PREVIEW"):
        import view
        tiles = [view.render(um["v"], um["n"], um["f"], size=(200, 200), yaw=-0.55, pitch=-0.38, tex=img, uv=um["uv"], ground=False)
                 for name, um, img in prev]
        Image.fromarray((view.sheet(tiles, 6) * 255).astype(np.uint8)).save(os.environ["ITEM_PREVIEW"])
