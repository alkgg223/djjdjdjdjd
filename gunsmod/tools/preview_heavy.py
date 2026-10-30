"""Preview of the heavy launcher as the block entity renderer assembles it (base x2, erector at an angle, missile)."""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw
from modelkit import load_json_model, render_quads, tex_loader
from blocks import HEAVY_HINGE, ERECTOR_Y0

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
B = os.path.join(RES, "assets", "gunsmod", "models")
look = tex_loader(RES)
MISSILE_K, MISSILE_Z = 0.82, 12.0          # must match HeavyLauncherRenderer


def xform(q, f):
    return [(f(np.asarray(pts, float)), uv, n, t) for pts, uv, n, t in q]


def rot_x(p, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    y, z = p[:, 1].copy(), p[:, 2].copy()
    p = p.copy()
    p[:, 1] = y * c - z * s
    p[:, 2] = y * s + z * c
    return p


def scene(elev, missile="missile_titan"):
    base = load_json_model(os.path.join(B, "block", "heavy_launcher_base.json"), look)
    er = load_json_model(os.path.join(B, "block", "heavy_launcher_erector.json"), look)
    mi = load_json_model(os.path.join(B, "item", missile + ".json"), look)
    hx, hy, hz = HEAVY_HINGE

    def erector_to_model(p):
        p = p - np.array([8.0, ERECTOR_Y0, 8.0])
        p = rot_x(p, -90 + elev)
        return p + np.array([hx, hy, hz])

    def missile_to_erector(p):
        return (p - 8.0) * MISSILE_K + np.array([8.0, ERECTOR_Y0 + 22 * MISSILE_K, MISSILE_Z])

    q = base + xform(er, erector_to_model) + xform(mi, lambda p: erector_to_model(missile_to_erector(p)))
    # half-scale model units -> render in "16 units per block" space (x2), centred on the middle block
    return xform(q, lambda p: (p - np.array([8.0, 0, 8.0])) * 2 + np.array([8.0, 0, 8.0]))


if __name__ == "__main__":
    out = sys.argv[1]
    views = [(0, -140, 22), (45, -140, 22), (90, -140, 18), (90, 90, 5)]
    ims = []
    for elev, yaw, pitch in views:
        im = render_quads(scene(elev), yaw=yaw, pitch=pitch, size=(640, 420))
        ImageDraw.Draw(im).text((6, 6), f"elev {elev}", fill=(255, 255, 0))
        ims.append(im)
    sheet = Image.new("RGB", (1280, 840))
    for i, im in enumerate(ims):
        sheet.paste(im, ((i % 2) * 640, (i // 2) * 420))
    sheet.save(out)
    print("ok")
