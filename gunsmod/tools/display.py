"""Display-transform helpers for item models."""
import math

import numpy as np


def _rot(rx, ry, rz):
    def R(axis, a):
        a = math.radians(a)
        c, s = math.cos(a), math.sin(a)
        if axis == 0:
            return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
        if axis == 1:
            return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    # MC: rotationXYZ -> R = Rx * Ry * Rz applied to vector
    return R(0, rx) @ R(1, ry) @ R(2, rz)


def fit(bounds, rot, fill, max_scale=4.0):
    x0, y0, z0, x1, y1, z1 = bounds
    corners = np.array([[x, y, z] for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], float)
    c = (corners.min(0) + corners.max(0)) / 2
    R = _rot(*rot)
    pc = (corners - c) @ R.T
    ext = max(np.ptp(pc[:, 0]), np.ptp(pc[:, 1]))
    s = min(max_scale, 16.0 * fill / max(ext, 1e-3))
    t = -(R @ ((c - 8.0) * s))
    t = np.clip(t, -80, 80)
    return {"rotation": [round(v, 3) for v in rot], "translation": [round(float(v), 3) for v in t],
            "scale": [round(s, 4)] * 3}


def item_display(bounds, gui_rot=(25, -40, 0), gui_fill=0.95, hand_fill=0.55, ground_fill=0.45, fixed_rot=(0, 180, 0),
                 hand_rot=(0, -45, 0), tp_rot=(70, -45, 0), head=None):
    d = {
        "gui": fit(bounds, gui_rot, gui_fill),
        "ground": fit(bounds, (0, 0, 0), ground_fill),
        "fixed": fit(bounds, fixed_rot, 0.9),
        "firstperson_righthand": fit(bounds, hand_rot, hand_fill),
        "firstperson_lefthand": fit(bounds, hand_rot, hand_fill),
        "thirdperson_righthand": fit(bounds, tp_rot, hand_fill * 0.75),
        "thirdperson_lefthand": fit(bounds, tp_rot, hand_fill * 0.75),
        "head": fit(bounds, (0, 180, 0), 0.9),
    }
    # nudge hand positions
    for k in ("firstperson_righthand", "firstperson_lefthand"):
        d[k]["translation"][1] += 1.5
    for k in ("thirdperson_righthand", "thirdperson_lefthand"):
        d[k]["translation"][1] += 2.5
        d[k]["translation"][2] += 0.5
    return d


def block_display():
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.40, 0.40, 0.40]},
    }


def bounds_of(m, parts=None):
    els = [e for e in m.elements if parts is None or e["part"] in parts]
    xs = [v for e in els for v in (e["from"][0], e["to"][0])]
    ys = [v for e in els for v in (e["from"][1], e["to"][1])]
    zs = [v for e in els for v in (e["from"][2], e["to"][2])]
    return [min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)]
