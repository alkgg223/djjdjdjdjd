import os, sys, math
import numpy as np
from PIL import Image
from modelkit import load_json_model, render_quads, tex_loader, _rot_matrix
import vehicle as V
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
J = os.path.join(RES, "assets", "gunsmod", "models", "entity", "jeep")
look = tex_loader(RES)
mark = sys.argv[2] if len(sys.argv) > 2 else "green"
tint = np.array([0.42, 0.48, 0.30]) if mark == "green" else np.array([0.80, 0.70, 0.50])
def tinted(qs):
    out = []
    for pts, uv, n, tex in qs:
        out.append((pts, uv, n, tex))
    return out
q = load_json_model(os.path.join(J, "body.json"), look)
# apply tint to tintindex faces: approximate by tinting texture copy (whole body texture)
import json
body = json.load(open(os.path.join(J, "body.json")))
tex = look(body["textures"]["0"]).copy()
tt = tex.copy(); tt[..., :3] *= tint
qq = []
faces = [(e, f) for e in body["elements"] for f in e["faces"]]
for (pts, uv, n, t), (e, f) in zip(q, faces):
    qq.append((pts, uv, n, tt if "tintindex" in e["faces"][f] else t))
q = qq + load_json_model(os.path.join(J, f"mark_{mark}.json"), look)
q += load_json_model(os.path.join(J, "turret.json"), look)
q += load_json_model(os.path.join(J, "gun.json"), look)
wq = load_json_model(os.path.join(J, "wheel.json"), look)
for (wx, wy, wz) in V.WHEELS:
    for pts, uv, n, t in wq:
        q.append((pts + np.array([wx - 8, wy - 8, wz - 8]), uv, n, t))
ims = [render_quads(q, yaw=a, pitch=b, size=(640, 400)) for a, b in ((-145, 18), (35, 22), (90, 5), (180, 30))]
sheet = Image.new("RGB", (1280, 800))
for i, im in enumerate(ims):
    sheet.paste(im, ((i % 2) * 640, (i // 2) * 400))
sheet.save(sys.argv[1]); print("ok")
