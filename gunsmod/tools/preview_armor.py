"""Preview renders of the tank / helicopter exactly as the renderer assembles them."""
import json
import os
import sys

import numpy as np
from PIL import Image

import armor as AR
from modelkit import _rot_matrix, load_json_model, render_quads, tex_loader

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
E = os.path.join(RES, "assets", "gunsmod", "models", "entity")
look = tex_loader(RES)
TINT = {"green": np.array([0.42, 0.50, 0.30]), "red": np.array([0.78, 0.67, 0.48])}


def part(path, tint=None, R=None, pivot=None, off=(0, 0, 0)):
    m = json.load(open(path))
    q = load_json_model(path, look)
    faces = [(e, f) for e in m["elements"] for f in e["faces"]]
    out = []
    for (pts, uv, n, t), (e, f) in zip(q, faces):
        if tint is not None and "tintindex" in e["faces"][f]:
            t = t.copy()
            t[..., :3] *= tint
        p = pts
        if R is not None:
            p = (R @ (p - pivot).T).T + pivot
            n = R @ n
        out.append((p + np.array(off), uv, n, t))
    return out


def tank(team="green", turret=25.0, pitch=8.0):
    tint = TINT[team]
    D = os.path.join(E, "tank")
    q = part(os.path.join(D, "body.json"), tint) + part(os.path.join(D, f"mark_{team}.json"))
    px, pz = AR.TANK_TURRET_PIVOT
    Ry = _rot_matrix("y", -turret)
    piv = np.array([px, 0, pz])
    q += part(os.path.join(D, "turret.json"), tint, Ry, piv)
    gx, gy, gz = AR.TANK_GUN_PIVOT
    Rx = _rot_matrix("x", pitch)
    g = part(os.path.join(D, "gun.json"), tint, Rx, np.array([gx, gy, gz]))
    q += [((Ry @ (p - piv).T).T + piv, uv, Ry @ n, t) for p, uv, n, t in g]
    for tx in AR.TRACK_X:
        for z in AR.ROAD_WHEELS_Z:
            q += part(os.path.join(D, "roadwheel.json"), tint, off=(tx - 8, AR.ROAD_WHEEL_Y - 8, z - 8))
        q += part(os.path.join(D, "idler.json"), tint, off=(tx - 8, AR.IDLER[0] - 8, AR.IDLER[1] - 8))
        q += part(os.path.join(D, "sprocket.json"), tint, off=(tx - 8, AR.SPROCKET[0] - 8, AR.SPROCKET[1] - 8))
        y, z0 = AR.TRACK_BOTTOM
        q += part(os.path.join(D, "track_bottom.json"), off=(tx - 8, y, z0 + 8))
        y, z0 = AR.TRACK_TOP
        q += part(os.path.join(D, "track_top.json"), off=(tx - 8, y, z0 + 8))
    return q


def head(eye, tex):
    """a 0.5 block head (2 units) centred on an eye point, for checking the camera is clear"""
    from modelkit import FACE_VERTS, FACE_NORMAL
    x, y, z = eye
    lo, hi = np.array([x - 1, y - 1.2, z - 1]), np.array([x + 1, y + 0.8, z + 1])
    out = []
    for f, sel in FACE_VERTS.items():
        pts = np.array([[hi[i] if s[i] else lo[i] for i in range(3)] for s in sel])
        out.append((pts, np.array([[0, 0], [0, 1], [1, 1], [1, 0]]) * 0.0, np.array(FACE_NORMAL[f], float), tex))
    return out


def heli(team="green", rotor=20.0, chin=(15.0, 10.0)):
    tint = TINT[team]
    D = os.path.join(E, "heli")
    q = part(os.path.join(D, "body.json"), tint) + part(os.path.join(D, f"mark_{team}.json"))
    hx, hy, hz = AR.HELI_ROTOR_HUB
    q += part(os.path.join(D, "rotor.json"), None, _rot_matrix("y", rotor), np.array([hx, hy, hz]))
    tx, ty, tz = AR.HELI_TAIL_HUB
    q += part(os.path.join(D, "tail_rotor.json"), None, _rot_matrix("x", 30), np.array([tx, ty, tz]))
    cx, cy, cz = AR.HELI_CHIN
    R = _rot_matrix("y", -chin[0]) @ _rot_matrix("x", chin[1])
    q += part(os.path.join(D, "chin.json"), None, R, np.array([cx, cy, cz]))
    return q


def sheet(q, path, views=((-145, 18), (35, 22), (90, 4), (180, 60))):
    ims = [render_quads(q, yaw=a, pitch=b, size=(640, 400)) for a, b in views]
    s = Image.new("RGB", (1280, 800))
    for i, im in enumerate(ims):
        s.paste(im, ((i % 2) * 640, (i // 2) * 400))
    s.save(path)


if __name__ == "__main__":
    what, out = sys.argv[1], sys.argv[2]
    team = sys.argv[3] if len(sys.argv) > 3 else "green"
    red = np.zeros((2, 2, 4), np.float32)
    red[..., 0] = 1.0
    red[..., 3] = 1.0
    if what == "tank":
        q = tank(team)
        sx, sy, sz = AR.TANK_SEAT
        q += head((sx, sy + 1.02 / 0.25, sz), red)
        sheet(q, out)
    elif what == "heli":
        q = heli(team)
        for sx, sy, sz in AR.HELI_SEATS:
            q += head((sx, sy + 1.02 / 0.25, sz), red)
        sheet(q, out, views=((-150, 15), (40, 20), (90, 2), (0, 85)))
    print("ok")
