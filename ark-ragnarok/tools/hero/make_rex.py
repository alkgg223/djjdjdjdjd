"""Build every Rex asset: LOD meshes, UV atlases, baked textures, (saddle), GeckoLib geo + animations, preview sheets."""
import sys, os, time, json
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rex, hero_build as HB, sdfkit as K, bake_hero as BH, rex_paint as RP

LODS = {"body": [17000, 6200, 2100], "jaw": [2400, 900, 320], "tongue": [360, 140, 60]}
TEX = [1024, 512, 256]


def detail_parts(bones, lod):
    parts = []
    sides = {0: 6, 1: 5, 2: 4}[lod]
    if lod < 2:
        for bone, (path, radii), kind in rex.teeth_specs():
            v, f, n = K.tube_mesh(path, radii, n=sides)
            parts.append(HB.rigid_part(v, f, n, bone, bones, "tooth", along=HB.tube_along(len(path), sides)))
    for bone, path, radii in rex.claw_specs():
        v, f, n = K.tube_mesh(path, radii, n=sides + 1)
        parts.append(HB.rigid_part(v, f, n, bone, bones, "claw", along=HB.tube_along(len(path), sides + 1)))
    for ei, (c, fwd) in enumerate(rex.EYES):
        v, f, n = K.sphere_mesh(c, rex.EYE_R, (16, 12, 8)[lod], (12, 9, 6)[lod])
        parts.append(HB.rigid_part(v, f, n, "head", bones, "eye", eye=ei))
    return parts


def build(lod_count=3, out_dir=None, preview=None):
    t = time.time()
    sc, lods, fields = HB.build_lods(rex, {k: v[:lod_count] for k, v in LODS.items()})
    bones = sc.bone_order
    print("fields + polygonize: %.1fs" % (time.time() - t))
    results = []
    for li in range(lod_count):
        parts = lods[li]["parts"] + detail_parts(bones, li)
        m = HB.merge(parts)
        um = BH.unwrap(m, TEX[li])
        ctx = dict(eyes=rex.EYES)
        img, filled = BH.bake(um, RP.shade, TEX[li], TEX[li], ctx)
        print("lod %d: %d tris, %d verts, bake %.1fs" % (li, len(um["f"]), len(um["v"]), time.time() - t))
        results.append((um, img))
    return sc, results


if __name__ == "__main__":
    build(int(sys.argv[1]) if len(sys.argv) > 1 else 1)
