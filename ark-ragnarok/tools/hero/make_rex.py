"""Build every Rex asset: LOD meshes, UV atlases, baked textures, saddle, GeckoLib geo + animations, preview sheets."""
import sys, os, time, json
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rex, hero_build as HB, sdfkit as K, bake_hero as BH, rex_paint as RP, rex_saddle, saddle_paint as SP

LODS = {"body": [17000, 6200, 2100], "jaw": [2400, 900, 320], "tongue": [360, 140, 60]}
SADDLE_LODS = [7600, 2800, 900]
TEX = [1024, 512, 256]
SADDLE_TEX = [1024, 512, 256]


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


def build_saddle(sc, fields, lod_count):
    rex_saddle.build(sc, fields["body"])
    prims = [p for p in sc.prims if p.group == "saddle"]
    body_prims = [p for p in sc.prims if p.group == "body"]
    field = K.build_field(prims, 0.26)
    meshes, raw = K.polygonize(field, SADDLE_LODS[:lod_count])
    print("  saddle  raw %d tris -> %s" % (raw, [len(m[1]) for m in meshes]))
    out = []
    adds = [p for p in prims if p.op == "add"]
    for v, f, n in meshes:
        ao = np.minimum(K.ambient_occlusion(field, v, n), 0.35 + 0.65 * K.ambient_occlusion(fields["body"], v, n))
        bi, bw, _ = K.skin_weights(v, body_prims, sc.bone_order, s=1.2)
        D = np.stack([p.sdf(v) for p in adds], axis=1)
        vmat = np.array([adds[i].mat for i in D.argmin(axis=1)])
        out.append(HB.merge([dict(v=v, n=n, f=f, bi=bi, bw=bw, ao=ao, tmat=vmat[f[:, 0]], grp="saddle",
                                  along=np.zeros(len(v)), eye=np.full(len(v), -1))]))
    return out


def build(lod_count=3, saddle=True):
    t = time.time()
    sc, lods, fields = HB.build_lods(rex, {k: v[:lod_count] for k, v in LODS.items()})
    bones = sc.bone_order
    print("fields + polygonize: %.1fs" % (time.time() - t))
    results, saddles = [], []
    for li in range(lod_count):
        parts = lods[li]["parts"] + detail_parts(bones, li)
        m = HB.merge(parts)
        um = BH.unwrap(m, TEX[li])
        img, _ = BH.bake(um, RP.shade, TEX[li], TEX[li], dict(eyes=rex.EYES))
        print("lod %d: %d tris, %d verts (%.1fs)" % (li, len(um["f"]), len(um["v"]), time.time() - t))
        results.append((um, img))
    if saddle:
        for li, m in enumerate(build_saddle(sc, fields, lod_count)):
            um = BH.unwrap(m, SADDLE_TEX[li])
            img, _ = BH.bake(um, SP.shade, SADDLE_TEX[li], SADDLE_TEX[li], {})
            print("saddle lod %d: %d tris (%.1fs)" % (li, len(um["f"]), time.time() - t))
            saddles.append((um, img))
    return sc, results, saddles




# ------------------------------------------------------------------------------------------ export
import struct
import rex_anim


def write_mesh(path, um, bones):
    """ARKM v2: quantised positions (uint16 in the bounding box), int8 normals, uint16 uvs, 4 bones + 4 weights,
    uint16 or int32 triangle indices"""
    v = (um["v"] / 16.0)
    lo, hi = v.min(axis=0), v.max(axis=0)
    span = np.maximum(hi - lo, 1e-6)
    q = np.round((v - lo) / span * 65535).astype("<u2")
    n = np.clip(np.round(um["n"] * 127), -127, 127).astype("<i1")
    uv = np.round(np.clip(um["uv"], 0, 1) * 65535).astype("<u2")
    bi = um["bi"].astype("<u1")
    qw = np.round(um["bw"] * 255).astype(int)
    qw[:, 0] += 255 - qw.sum(axis=1)
    bw8 = np.clip(qw, 0, 255).astype("<u1")
    wide = len(v) >= 65536
    f = um["f"].astype("<i4" if wide else "<u2")
    with open(path, "wb") as fh:
        fh.write(b"ARKM")
        fh.write(struct.pack("<i", 2))
        fh.write(struct.pack("<i", len(bones)))
        for b in bones:
            enc = b.encode("utf-8")
            fh.write(struct.pack("<H", len(enc)))
            fh.write(enc)
        fh.write(struct.pack("<i", len(v)))
        fh.write(struct.pack("<6f", *lo, *hi))
        fh.write(q.tobytes())
        fh.write(n.tobytes())
        fh.write(uv.tobytes())
        fh.write(bi.tobytes())
        fh.write(bw8.tobytes())
        fh.write(struct.pack("<i", len(f)))
        fh.write(struct.pack("<B", 1 if wide else 0))
        fh.write(f.tobytes())


def save_png(path, img):
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)[:, :, :3], "RGB").save(path, optimize=True)


def geo_json(sc):
    bones = []
    for name in sc.bone_order:
        par, piv = sc.bones[name]
        b = {"name": name, "pivot": [round(-float(piv[0]), 4), round(float(piv[1]), 4), round(float(piv[2]), 4)]}
        if par:
            b["parent"] = par
        bones.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.arkragnarok.rex", "texture_width": 1024, "texture_height": 1024,
                        "visible_bounds_width": 16, "visible_bounds_height": 8, "visible_bounds_offset": [0, 3, 0]},
        "bones": bones}]}


def export(res_root, lod_count=3):
    sc, results, saddles = build(lod_count, saddle=True)
    a = res_root + "/assets/arkragnarok"
    for d in ("arkmesh", "textures/entity/rex", "geo/entity", "animations/entity"):
        os.makedirs(a + "/" + d, exist_ok=True)
    bones = sc.bone_order
    for li, (um, img) in enumerate(results):
        write_mesh(a + "/arkmesh/rex_lod%d.arkm" % li, um, bones)
        save_png(a + "/textures/entity/rex/rex_lod%d.png" % li, img)
    for li, (um, img) in enumerate(saddles):
        write_mesh(a + "/arkmesh/rex_saddle_lod%d.arkm" % li, um, bones)
        save_png(a + "/textures/entity/rex/rex_saddle_lod%d.png" % li, img)
    with open(a + "/geo/entity/rex.geo.json", "w") as fh:
        json.dump(geo_json(sc), fh, indent=1)
    with open(a + "/animations/entity/rex.animation.json", "w") as fh:
        json.dump(rex_anim.to_bedrock("animation.rex", set(bones)), fh, separators=(",", ":"))
    v = results[0][0]["v"]
    mn, mx = v.min(axis=0), v.max(axis=0)
    print("model bounds (px):", np.round(mn, 1), np.round(mx, 1))
    return sc


if __name__ == "__main__" and len(sys.argv) > 1 and sys.argv[1] == "export":
    export(sys.argv[2])
