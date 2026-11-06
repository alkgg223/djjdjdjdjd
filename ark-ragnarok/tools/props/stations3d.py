"""Crafting stations, storage, bed and crop plot of the mod's economy, sculpted like the building pieces and exported
as OBJ block models (plus item models).

    python3 stations3d.py <resources dir> [name ...]

Space: px, block from x,z in [-8, 8], y in [0, 16]; the bed's head part extends towards -z (its foot block).
"""
import json
import math
import os
import sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import building as B
import sdfkit as K
import hero_build as HB
import bake_hero as BH
from rex_paint import hx, sm, mixc, vnoise, fbm, cells, LIGHT

base_shade = B.shade


def shade(mat, P, N, AO, ctx):
    base, _, idx = mat.partition(".")
    n = len(P)
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    seed = 31 + int(idx or 0) * 7
    lit = np.clip((N * LIGHT).sum(axis=1), 0, 1)
    spec = sm(0.85, 0.99, lit)
    if base == "ember":
        hot = fbm(P, 0.8, seed, 3)
        col = mixc(np.tile(hx("#3a1a0a"), (n, 1)), hx("#ff8a20"), sm(0.35, 0.75, hot)) + (sm(0.7, 0.9, hot) * 0.4)[:, None]
        AO = np.ones(n)
    elif base == "ash":
        col = np.tile(hx("#5a5652"), (n, 1)) * (0.8 + 0.3 * vnoise(P, 0.6, seed))[:, None]
    elif base == "flame":
        col = mixc(np.tile(hx("#ff6a10"), (n, 1)), hx("#ffe080"), sm(2, 9, y))
        AO = np.ones(n)
    elif base == "soil":
        col = np.tile(hx("#4a3220"), (n, 1)) * (0.75 + 0.35 * fbm(P, 0.5, seed, 3))[:, None]
    elif base == "leafy":
        col = np.tile(hx("#3e6e2a"), (n, 1)) * (0.75 + 0.35 * vnoise(P, 0.6, seed))[:, None] + (spec * 0.15)[:, None]
    elif base == "berry":
        col = np.tile(hx("#5a3ab0"), (n, 1)) * (0.7 + 0.4 * lit)[:, None] + (spec * 0.6)[:, None]
    elif base == "screen":
        flat = sm(0.6, 0.9, np.abs(N[:, 2]))
        glow = hx("#3ad8c8") * (0.7 + 0.3 * np.abs(np.sin(y * 6)))[:, None]
        col = mixc(np.tile(hx("#1a2224"), (n, 1)), glow, flat)
        AO = np.maximum(AO, 0.9)
    elif base == "hidepad":
        f1, f2, _ = cells(P, 0.9, seed)
        col = np.tile(hx("#8a6440"), (n, 1)) * (0.8 + 0.3 * fbm(P, 1.0, seed, 3))[:, None]
        col = mixc(col, col * 0.75, sm(0.05, 0.0, f2 - f1) * 0.5)
    elif base == "fur":
        strands = np.abs(np.sin(x * 5 + z * 3 + fbm(P, 0.4, seed, 2) * 8))
        col = mixc(np.tile(hx("#c8b498"), (n, 1)), hx("#7a6448"), sm(0.4, 1.0, strands) * 0.6)
    elif base == "cloth":
        weave = 0.9 + 0.1 * np.abs(np.sin(x * 6)) * np.abs(np.sin(z * 6))
        col = np.tile(hx("#d8ccb0"), (n, 1)) * weave[:, None]
    elif base == "glow":
        col = np.tile(hx("#ffb040"), (n, 1)) * (0.8 + 0.3 * vnoise(P, 0.5, seed))[:, None]
        AO = np.ones(n)
    else:
        return base_shade(mat, P, N, AO, ctx)
    col = col * (0.45 + 0.55 * AO)[:, None]
    return np.clip(col, 0, 1)


def stone_ring(p, r, n, y=1.2, size=1.6):
    for i in range(n):
        a = i * 2 * math.pi / n + B.jitter(0.15)
        p.box((math.cos(a) * r, y, math.sin(a) * r), (size + B.jitter(0.3), size * 0.8, size * 0.9 + B.jitter(0.3)), "stone", rr=1.0, k=0.05,
              rot=(B.jitter(0.3), a, B.jitter(0.3)))


def campfire(p):
    p.box((0, 0.4, 0), (5.2, 0.4, 5.2), "ash", rr=0.4)
    stone_ring(p, 6.0, 9)
    for a in (0.3, 1.9, 3.5, 5.0):
        d = np.array([math.cos(a), 0, math.sin(a)])
        p.log(tuple(d * 5.0 + np.array([0, 1.0, 0])), (0, 5.5, 0), 1.0)
    p.ell((0, 1.4, 0), (3.0, 1.0, 3.0), "ember", k=0.4)
    for i in range(5):
        a = i * 1.3
        p.ell((math.cos(a) * 1.0, 3.4 + i * 0.4, math.sin(a) * 1.0), (0.9 - i * 0.1, 2.0, 0.9 - i * 0.1), "flame", k=0.6)


def mortar(p):
    p.log((0, 0, 0), (0, 7.5, 0), 5.0)
    p.ell((0, 9.2, 0), (4.6, 2.6, 4.6), "stone", k=0.4)
    p.ell((0, 10.6, 0), (3.7, 2.2, 3.7), "stone", k=0.3)
    p.sc.ell((0, 11.6, 0), (3.4, 2.4, 3.4), mat="stone.99", bone="root", op="sub", k=0.3)
    p.ell((0, 9.6, 0), (3.0, 0.8, 3.0), "mortar", k=0.3)
    p.log((0.5, 9.6, 0.3), (4.6, 15.0, 2.0), 0.9, mat="stone")
    p.ell((0.4, 9.4, 0.2), (1.3, 1.2, 1.3), "stone", k=0.4)


def smithy(p):
    for sx in (-1, 1):
        for sz in (-1, 1):
            p.box((sx * 6.2, 4.0, sz * 5.4), (1.0, 4.0, 1.0), "beam", rr=0.4)
    p.box((0, 8.6, 0), (7.6, 0.9, 6.6), "plank", rr=0.4)
    p.box((0, 3.0, 0), (6.2, 0.5, 5.4), "plank", rr=0.3)
    # anvil
    p.box((-1.5, 10.2, 0), (2.0, 0.8, 1.4), "iron", rr=0.2)
    p.box((-1.5, 11.6, 0), (1.0, 0.7, 0.9), "iron", rr=0.2)
    p.box((-1.5, 13.0, 0), (3.2, 0.8, 1.6), "iron", rr=0.3)
    p.sc.cone((1.7, 13.2, 0), (4.4, 13.4, 0), 0.9, 0.2, mat="iron.0", bone="root", k=0.3)
    # hammer and tongs on the table
    p.log((3.0, 9.8, 3.2), (6.6, 9.8, 4.6), 0.35, mat="beam")
    p.box((6.6, 10.2, 4.6), (0.7, 0.7, 1.4), "iron", rr=0.2)
    p.log((-6.0, 9.7, -3.8), (-1.0, 9.7, -4.8), 0.25, mat="iron")
    p.log((-6.0, 9.7, -3.4), (-1.0, 9.9, -2.8), 0.25, mat="iron")
    for xx in (-4, 0, 4):
        p.sphere((xx, 4.0, 2.0), 0.7, "iron")


def forge(p):
    p.box((0, 1.0, 0), (7.4, 1.0, 7.4), "stone", rr=0.8)
    for y0 in (2.0, 5.0, 8.0):
        for i in range(4):
            a = i * math.pi / 2
            for k in range(3):
                off = (k - 1) * 4.4
                c = (math.cos(a) * 6.2 - math.sin(a) * off, y0 + 1.5, math.sin(a) * 6.2 + math.cos(a) * off)
                if i == 0 and k == 1 and y0 < 6:
                    continue          # the fire mouth
                p.box(c, (2.1 + B.jitter(0.2), 1.4, 2.1 + B.jitter(0.2)), "stone", rr=0.8, k=0.05, rot=(0, a, 0))
    p.ell((0, 11.8, 0), (6.6, 2.4, 6.6), "stone", k=0.8)
    p.log((0, 12.0, 0), (0, 16.0, 0), 2.4, mat="stone")
    p.ell((0, 3.6, 0), (4.4, 2.4, 4.4), "glow", k=0.4)
    p.ell((0, 2.6, 0), (3.8, 0.8, 3.8), "ember", k=0.4)


def fabricator(p):
    p.box((0, 6.5, 0), (7.4, 6.5, 6.4), "steelplate", rr=0.8)
    p.box((0, 9.0, -6.5), (5.4, 2.6, 0.4), "screen", rr=0.2)
    for i in range(5):
        p.box((-4 + i * 2, 3.0, -6.5), (0.6, 1.8, 0.25), "girder", rr=0.15)
    p.box((5.2, 3.8, -6.55), (1.2, 1.2, 0.3), "hazard", rr=0.2)
    p.box((0, 13.6, 0), (6.4, 0.6, 5.4), "ironplate", rr=0.3)
    for sx in (-1, 1):
        p.log((sx * 5.6, 14.0, 3.0), (sx * 5.6, 15.6, 3.0), 0.7, mat="girder")
    for xx in (-5.6, -3.0, 3.0, 5.6):
        p.hexbolt((xx, 11.8, -6.7), (0, 0, -1), 0.45)


def storage(p, big):
    w, h, d = (7.6, 7.0, 7.6) if big else (6.4, 4.6, 4.6)
    p.box((0, h, 0), (w, h, d), "plank", rr=0.5)
    p.box((0, 2 * h - 0.6, 0), (w + 0.2, 0.7, d + 0.2), "beam", rr=0.4)
    for sx in (-1, 1):
        p.box((sx * (w - 1.2), h, -d - 0.05), (0.5, h, 0.12), "iron", rr=0.1)
        p.box((sx * (w - 1.2), h, d + 0.05), (0.5, h, 0.12), "iron", rr=0.1)
    p.box((0, 2 * h - 2.0, -d - 0.25), (0.9, 1.1, 0.25), "iron", rr=0.15)
    if big:
        for sx in (-1, 1):
            for sz in (-1, 1):
                p.box((sx * w, h, sz * d), (0.7, h, 0.7), "iron", rr=0.2)


def bed_head(p):
    for sx in (-1, 1):
        p.box((sx * 7.0, 2.8, 6.6), (0.8, 2.8, 0.8), "beam", rr=0.3)
        p.box((sx * 7.0, 3.2, -0.5), (0.7, 0.7, 7.6), "beam", rr=0.3)
    p.box((0, 7.0, 6.9), (7.6, 4.0, 0.7), "plank", rr=0.4)            # headboard
    p.box((0, 4.6, -0.5), (6.6, 1.3, 7.6), "hidepad", rr=1.0)
    p.ell((0, 6.6, 4.4), (4.6, 1.3, 2.2), "cloth", k=0.5)              # pillow
    p.box((0, 6.1, -3.0), (6.9, 0.6, 5.2), "fur", rr=0.5)


def bed_foot(p):
    for sx in (-1, 1):
        p.box((sx * 7.0, 2.2, -6.6), (0.8, 2.2, 0.8), "beam", rr=0.3)
        p.box((sx * 7.0, 3.2, 0.5), (0.7, 0.7, 7.6), "beam", rr=0.3)
    p.box((0, 4.6, 0.5), (6.6, 1.3, 7.6), "hidepad", rr=1.0)
    p.box((0, 6.1, 1.5), (6.9, 0.6, 6.6), "fur", rr=0.5)
    p.box((0, 4.2, -7.0), (7.4, 2.0, 0.6), "plank", rr=0.4)


def crop(p, age):
    for sx in (-1, 1):
        p.box((sx * 7.3, 2.4, 0), (0.7, 2.4, 7.9), "plank", rr=0.3)
        p.box((0, 2.4, sx * 7.3), (7.9, 2.4, 0.7), "plank", rr=0.3)
    p.box((0, 3.6, 0), (6.6, 0.9, 6.6), "soil", rr=0.5)
    rng = np.random.default_rng(age + 5)
    for i in range(5 if age == 0 else 4):
        cx, cz = rng.uniform(-4.5, 4.5), rng.uniform(-4.5, 4.5)
        if age == 0:
            p.ell((cx, 5.0, cz), (0.6, 0.9, 0.6), "leafy", k=0.2)
        else:
            s = 1.4 if age == 1 else 2.2
            p.ell((cx, 4.4 + s, cz), (s * 1.1, s, s * 1.1), "leafy", k=0.5)
            if age == 2:
                for k in range(5):
                    a = k * 1.25
                    p.sphere((cx + math.cos(a) * s, 4.4 + s * 1.2, cz + math.sin(a) * s), 0.55, "berry")


ST = {"campfire": campfire, "mortar_and_pestle": mortar, "smithy": smithy, "refining_forge": forge, "fabricator": fabricator,
      "storage_box": lambda p: storage(p, False), "large_storage_box": lambda p: storage(p, True),
      "hide_bed_head": bed_head, "hide_bed_foot": bed_foot,
      "crop_plot_0": lambda p: crop(p, 0), "crop_plot_1": lambda p: crop(p, 1), "crop_plot_2": lambda p: crop(p, 2)}
ITEM_OF = {"hide_bed_head": "hide_bed", "crop_plot_0": "crop_plot"}
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def main(root, want):
    B.shade = shade
    A = root + "/assets/arkragnarok"
    for d in ("models/block/obj", "models/block", "models/item", "textures/block/station"):
        os.makedirs(A + "/" + d, exist_ok=True)
    prev = []
    for name, fn in ST.items():
        if want and name not in want:
            continue
        B.RNG = np.random.default_rng(sum(map(ord, name)))
        m = B.build_piece(fn, 5000, cell=0.12)
        um = BH.unwrap(m, 256, padding=3)
        img, _ = BH.bake(um, shade, 256, 256, {"grain": "x"}, dilate=4)
        B.write_obj(A + "/models/block/obj/%s.obj" % name, um, (0.5, 0.0, 0.5))
        Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).save(A + "/textures/block/station/%s.png" % name)
        model = {"loader": "neoforge:obj", "model": "arkragnarok:models/block/obj/%s.obj" % name, "flip_v": False,
                 "automatic_culling": False, "shade_quads": True, "ambient_occlusion": False, "render_type": "minecraft:cutout",
                 "textures": {"texture": "arkragnarok:block/station/%s" % name, "particle": "arkragnarok:block/station/%s" % name}}
        json.dump(model, open(A + "/models/block/%s.json" % name, "w"), indent=1)
        item = ITEM_OF.get(name, None if name in ("hide_bed_foot", "crop_plot_1", "crop_plot_2") else name)
        if item:
            im = dict(model)
            im.pop("render_type")
            im["gui_light"] = "side"
            im["display"] = DISPLAY
            json.dump(im, open(A + "/models/item/%s.json" % item, "w"), indent=1)
        print("%-20s %5d tris" % (name, len(um["f"])), flush=True)
        prev.append((um, img))
    if os.environ.get("ST_PREVIEW"):
        import view
        tiles = [view.render(um["v"], um["n"], um["f"], size=(240, 240), yaw=0.7, pitch=-0.35, tex=img, uv=um["uv"]) for um, img in prev]
        Image.fromarray((view.sheet(tiles, 6) * 255).astype(np.uint8)).save(os.environ["ST_PREVIEW"])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
