"""Build and export every asset of one species: 3 LOD skinned meshes + textures, saddle LODs (rideable species),
GeckoLib geo + animations, and a metadata JSON for the Java species table.

    python3 build_species.py <resources dir> <species id ...|all>
"""
import json
import os
import sys
import time
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sdfkit as K
import hero_build as HB
import bake_hero as BH
import dinos
import extras
import paint_species as PS
import saddle_paint as SP
import rex_saddle
import anim_species as AS
from make_rex import write_mesh, save_png

GROUP_CELL = {"body": 0.36, "jaw": 0.26, "tongue": 0.26}


def budgets(length_m):
    if length_m >= 6:
        return dict(body=[16000, 6000, 2100], jaw=[2200, 850, 320], tongue=[340, 130, 60]), [1024, 512, 256]
    if length_m >= 2.5:
        return dict(body=[11000, 4400, 1600], jaw=[1500, 650, 260], tongue=[260, 110, 50]), [1024, 512, 256]
    return dict(body=[7500, 3000, 1100], jaw=[1000, 450, 200], tongue=[200, 90, 40]), [512, 256, 128]


def region_fn_for(bones, plan):
    import theropod, quadruped, sauropod
    rf = {"theropod": theropod.region_of, "quadruped": quadruped.region_of, "sauropod": sauropod.region_of}[plan]

    def f(i):
        r = rf(bones[i])
        return {"front": "leg", "hind": "leg", "arm": "torso" if plan == "theropod" else "leg"}.get(r, r)
    return f


def build(sid, res_root, lod_count=3, verbose=True):
    t0 = time.time()
    spec = dinos.ALL[sid]
    B = dinos.builder(sid)
    sc = B.build()
    bones = sc.bone_order
    targets, tex = budgets(spec["length_m"])
    fields, lods = {}, [[] for _ in range(lod_count)]
    for grp in ("body", "jaw", "tongue"):
        prims = [p for p in sc.prims if p.group == grp]
        if not prims:
            continue
        field = K.build_field(prims, GROUP_CELL[grp])
        fields[grp] = field
        meshes, raw = K.polygonize(field, targets[grp][:lod_count])
        adds = [p for p in prims if p.op == "add"]
        for li, (v, f, n) in enumerate(meshes):
            ao = K.ambient_occlusion(field, v, n)
            bi, bw, near = K.skin_weights(v, prims, bones, s=1.1)
            if grp == "body":
                bi, bw = HB.spline_override(v, bi, bw, sc, bones)
            vmat = np.array([adds[i].mat for i in near])
            lods[li].append(dict(v=v, n=n, f=f, bi=bi, bw=bw, ao=ao, tmat=vmat[f[:, 0]], grp=grp, along=np.zeros(len(v)),
                                 eye=np.full(len(v), -1)))
    eyes, eye_r = B.eyes()
    extra_parts = extras.build(sid, B, sc)
    look = dinos.LOOK[sid]
    pal = PS.palette(look.get("pal"))
    hp = sc.bones["head"][1]
    shin = [b for b in bones if b.startswith("shin")][0]
    jaw_y = sc.bones["jaw"][1][1]
    joints = []
    for b in bones:
        if b.startswith(("shin", "ankle", "forearm")):
            joints.append((tuple(sc.bones[b][1]), 4.5 * B.radius_scale(b)))
    neck = [b for b in bones if b.startswith("neck")]
    nd = sc.bones["head"][1] - sc.bones[neck[0]][1]
    nd = nd / max(np.linalg.norm(nd), 1e-9)
    hs = B.radius_scale("head")
    ctx = dict(eyes=eyes, pal=pal, spec=look, eye_y=eyes[0][0][1], eye_z=eyes[0][0][2], eye_x=abs(eyes[0][0][0]), head_s=hs,
               jaw_y=jaw_y, jaw_z=sc.bones["jaw"][1][2],
               knee_y=sc.bones[shin][1][1], detail=1.0, neck_dir=nd, joints=joints, rex_wrinkles=(sid == "tyrannosaurus"))
    rfn = region_fn_for(bones, spec["plan"])
    a = res_root + "/assets/arkragnarok"
    for d in ("arkmesh", "textures/entity/" + sid, "geo/entity", "animations/entity"):
        os.makedirs(a + "/" + d, exist_ok=True)
    first = None
    for li in range(lod_count):
        parts = list(lods[li])
        sides = (6, 5, 4)[li]
        if li < 2:
            for bone, (path, radii) in B.teeth_specs():
                v, f, n = K.tube_mesh(path, radii, n=sides)
                parts.append(HB.rigid_part(v, f, n, bone, bones, "tooth", along=HB.tube_along(len(path), sides)))
        for bone, path, radii in B.claw_specs():
            v, f, n = K.tube_mesh(path, radii, n=sides + 1)
            parts.append(HB.rigid_part(v, f, n, bone, bones, "claw", along=HB.tube_along(len(path), sides + 1)))
        for ei, (c, fwd) in enumerate(eyes):
            v, f, n = K.sphere_mesh(c, eye_r, (16, 12, 8)[li], (12, 9, 6)[li])
            parts.append(HB.rigid_part(v, f, n, "head", bones, "eye", eye=ei))
        if li < 2 or sid in ("stegosaurus", "spinosaurus", "triceratops", "pachyrhinosaurus", "ankylosaurus"):
            parts += extra_parts
        m = HB.merge(parts)
        um = BH.unwrap(m, tex[li])
        img, _ = BH.bake(um, PS.shade, tex[li], tex[li], ctx, region_fn=rfn)
        write_mesh(a + "/arkmesh/%s_lod%d.arkm" % (sid, li), um, bones)
        save_png(a + "/textures/entity/%s/%s_lod%d.png" % (sid, sid, li), img)
        if li == 0:
            first = (um, img)
        if verbose:
            print("  %s lod%d: %d tris (%.0fs)" % (sid, li, len(um["f"]), time.time() - t0), flush=True)
    # ---------------------------------------------------------------- saddle
    seat = None
    if sid in dinos.RIDEABLE:
        tor = np.array(B.P.get("torso", (1, 1, 1)))
        s_ = float(np.clip((tor[0] + tor[2]) / 2, 0.45, 1.3))
        zt = sc.bones["torso"][1][2]
        zb = sc.bones["body"][1][2]
        zc = zt + (zb - zt) * (0.2 if spec["plan"] == "theropod" else 0.35)
        collar = sc.bones[neck[0]][1][2] + 2.0 * s_
        if sid == "spinosaurus":
            zc = zb + 4.0
        info = rex_saddle.build(sc, fields["body"], zc=zc, sc_=s_, collar_z=collar, bags=spec["length_m"] > 4)
        seat = info["seat"]
        prims = [p for p in sc.prims if p.group == "saddle"]
        body_prims = [p for p in sc.prims if p.group == "body"]
        field = K.build_field(prims, 0.26 * max(0.6, s_))
        meshes, raw = K.polygonize(field, [7600, 2800, 1200][:lod_count])
        adds = [p for p in prims if p.op == "add"]
        for li, (v, f, n) in enumerate(meshes):
            ao = np.minimum(K.ambient_occlusion(field, v, n), 0.35 + 0.65 * K.ambient_occlusion(fields["body"], v, n))
            bi, bw, _ = K.skin_weights(v, body_prims, bones, s=1.2)
            D = np.stack([p.sdf(v) for p in adds], axis=1)
            vmat = np.array([adds[i].mat for i in D.argmin(axis=1)])
            sm = HB.merge([dict(v=v, n=n, f=f, bi=bi, bw=bw, ao=ao, tmat=vmat[f[:, 0]], grp="saddle", along=np.zeros(len(v)),
                                eye=np.full(len(v), -1))])
            st = [1024, 512, 256][li] if spec["length_m"] >= 6 else [512, 256, 128][li]
            um = BH.unwrap(sm, st)
            img, _ = BH.bake(um, SP.shade, st, st, {})
            write_mesh(a + "/arkmesh/%s_saddle_lod%d.arkm" % (sid, li), um, bones)
            save_png(a + "/textures/entity/%s/%s_saddle_lod%d.png" % (sid, sid, li), img)
        if verbose:
            print("  %s saddle (%.0fs)" % (sid, time.time() - t0), flush=True)
    # ---------------------------------------------------------------- skeleton + animations
    gbones = []
    for name in bones:
        par, piv = sc.bones[name]
        b = {"name": name, "pivot": [round(-float(piv[0]), 4), round(float(piv[1]), 4), round(float(piv[2]), 4)]}
        if par:
            b["parent"] = par
        gbones.append(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.arkragnarok." + sid, "texture_width": 1024, "texture_height": 1024, "visible_bounds_width": 16,
        "visible_bounds_height": 8, "visible_bounds_offset": [0, 3, 0]}, "bones": gbones}]}
    with open(a + "/geo/entity/%s.geo.json" % sid, "w") as fh:
        json.dump(geo, fh, indent=1)
    v = first[0]["v"]
    torso_v = v[np.isin(first[0]["bi"][:, 0], [bones.index("torso"), bones.index("body")])]
    c = dict(size_m=spec["length_m"], attack=dinos.ATTACK[sid], bones=set(bones), hip=float(sc.bones["body"][1][1]),
             belly=float(torso_v[:, 1].min()) if len(torso_v) else 5.0, sauropod=spec["plan"] == "sauropod")
    anims = AS.biped_set(c) if spec["plan"] == "theropod" else AS.quad_set(c)
    with open(a + "/animations/entity/%s.animation.json" % sid, "w") as fh:
        json.dump(AS.to_bedrock("animation." + sid, anims, set(bones)), fh, separators=(",", ":"))
    # ---------------------------------------------------------------- metadata for Java
    mn, mx = v.min(axis=0), v.max(axis=0)
    length_px = float(mx[2] - mn[2])
    scale = spec["length_m"] * 16.0 / length_px          # render scale (model blocks -> real metres)
    torso_w = float(np.ptp(torso_v[:, 0])) if len(torso_v) else 20.0
    meta = dict(id=sid, name=spec["name"], plan=spec["plan"], length_m=spec["length_m"], scale=round(scale, 5),
                height=round(float(mx[1]) / 16 * scale, 3), width=round(min(5.0, max(0.35, torso_w / 16 * scale * 1.05)), 3),
                hip=round(c["hip"] / 16 * scale, 3), eye=round(float(eyes[0][0][1]) / 16 * scale, 3),
                head_z=round(float(-sc.bones["head"][1][2]) / 16 * scale, 3),
                seat=[round(-float(seat[2]) / 16 * scale, 3), round(float(seat[1]) / 16 * scale, 3)] if seat else None,
                saddle=seat is not None, bones=len(bones))
    with open(os.path.join(HERE, "meta", sid + ".json"), "w") as fh:
        json.dump(meta, fh, indent=1)
    if verbose:
        print("  %s done in %.0fs: %s" % (sid, time.time() - t0, json.dumps(meta)), flush=True)
    return meta, first, sc


if __name__ == "__main__":
    os.makedirs(os.path.join(HERE, "meta"), exist_ok=True)
    root = sys.argv[1]
    ids = sys.argv[2:]
    if ids == ["all"]:
        ids = list(dinos.ALL)
    for sid in ids:
        build(sid, root)
