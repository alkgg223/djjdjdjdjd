"""Build, ground, pack, paint and preview one species model."""
import numpy as np
from PIL import Image
from geom import Model, GROUND
from anatomy import BUILDERS
import paint
import preview


def corners_world(m, mats):
    pts = []
    for p in m.order:
        M = mats[p.name]
        for c in p.cubes:
            for x in (c.x, c.x + c.w):
                for y in (c.y, c.y + c.h):
                    for z in (c.z, c.z + c.d):
                        pts.append((M @ np.array([x, y, z, 1.0]))[:3])
    return np.array(pts)


def ground(m):
    mats = m.world_matrices()
    pts = corners_world(m, mats)
    lowest = pts[:, 1].max()
    shift = GROUND - lowest
    for p in m.root.children:
        p.pivot[1] += shift
    return m.world_matrices()


def build_model(spec):
    m = Model(spec["id"])
    plan = spec["plan"]
    p = BUILDERS[plan](m, dict(spec.get("geo", {})))
    mats = ground(m)
    m.pack(max_w=spec.get("max_tex", 256))
    return m, p, mats


def dims(m, mats):
    pts = corners_world(m, mats)
    mn, mx = pts.min(axis=0), pts.max(axis=0)
    return mn, mx


def paint_model(spec, m, mats, k):
    pal = paint.Palette(spec["palette"])
    sp = dict(spec.get("paint", {}))
    mn, mx = dims(m, mats)
    sp.setdefault("center_y", (mn[1] + mx[1]) / 2.0)
    img, glow = paint.paint(m, mats, pal, sp, spec.get("seed", 7), k=k)
    return img, glow


def to_png(arr, path, quantize=True):
    a = (np.clip(arr, 0, 1) * 255).astype(np.uint8)
    im = Image.fromarray(a, "RGBA")
    if quantize:
        alpha = a[:, :, 3]
        if alpha.min() == 255 or True:
            q = im.convert("RGB").quantize(colors=255, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
            # keep transparency of unused UV space as palette index with alpha 0
            q = q.convert("RGBA")
            qa = np.array(q)
            qa[:, :, 3] = alpha
            im = Image.fromarray(qa, "RGBA")
    im.save(path, optimize=True)


def preview_sheet(spec, m, mats, img, k, path):
    views = [(1.5708, 0.05), (0.75, 0.35), (-0.6, 0.25), (0.0, 1.35)]
    tiles = [preview.render(m, mats, img, k, size=(300, 220), yaw=yw, pitch=pt) for yw, pt in views]
    sheet = np.concatenate([np.concatenate(tiles[:2], axis=1), np.concatenate(tiles[2:], axis=1)], axis=0)
    Image.fromarray((np.clip(sheet, 0, 1) * 255).astype(np.uint8)).save(path)
