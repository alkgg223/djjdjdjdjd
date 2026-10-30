"""Sight pictures shown while aiming through an optic: red dot tube, holographic window, ACOG 4x eyepiece."""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod", "textures", "gui")
N = 512


def grid():
    yy, xx = np.mgrid[0:N, 0:N]
    c = (N - 1) / 2
    return (xx - c) / c, (yy - c) / c


def save(name, rgba):
    Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name + ".png"))


def over(dst, mask, col, alpha=1.0):
    """paint colour col with coverage mask (0..1) over dst"""
    a = np.clip(mask, 0, 1)[..., None] * alpha
    dst[..., :3] = dst[..., :3] * (1 - a) + np.array(col) * a
    dst[..., 3:] = dst[..., 3:] * (1 - a) + a
    return dst


def smooth(d, w):
    """1 inside (d < 0), 0 outside, soft edge w"""
    return np.clip(0.5 - d / w, 0, 1)


def red_dot():
    x, y = grid()
    r = np.hypot(x, y)
    img = np.zeros((N, N, 4))
    img = over(img, smooth(r - 0.80, 0.01), (0.55, 0.75, 0.80), 0.05)         # faint lens tint
    tube = smooth(np.abs(r - 0.88) - 0.08, 0.012)
    shade = 0.06 + 0.06 * np.clip(-y, 0, 1)
    img = over(img, tube, (shade.mean(), shade.mean(), shade.mean() + 0.02))
    img = over(img, smooth(np.abs(r - 0.80) - 0.006, 0.006), (0.25, 0.27, 0.30), 0.9)
    img = over(img, smooth(r - 0.022, 0.012), (1.0, 0.12, 0.08))
    img = over(img, np.exp(-(r / 0.07) ** 2) * 0.5, (1.0, 0.25, 0.15), 0.6)
    return img


def holo():
    x, y = grid()
    img = np.zeros((N, N, 4))
    win = np.maximum(np.abs(x) - 0.86, np.abs(y) - 0.58)
    img = over(img, smooth(win, 0.01), (0.55, 0.80, 0.70), 0.06)
    frame = smooth(np.maximum(np.abs(x) - 0.96, np.abs(y) - 0.70), 0.01) * (1 - smooth(win, 0.01))
    img = over(img, frame, (0.07, 0.07, 0.08))
    img = over(img, smooth(np.abs(win) - 0.006, 0.006) * smooth(np.maximum(np.abs(x) - 0.9, np.abs(y) - 0.62), 0.01), (0.25, 0.25, 0.27), 0.8)
    r = np.hypot(x, y)
    red = (1.0, 0.15, 0.10)
    img = over(img, smooth(np.abs(r - 0.30) - 0.008, 0.008), red)                 # 65 MOA ring
    img = over(img, smooth(r - 0.02, 0.01), red)                                   # 1 MOA dot
    for sx, sy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        bar = smooth(np.maximum(np.abs(x - sx * 0.33) - (0.035 if sx else 0.008), np.abs(y - sy * 0.33) - (0.008 if sx else 0.035)), 0.008)
        img = over(img, bar, red)
    img = over(img, np.exp(-(r / 0.06) ** 2) * 0.4, (1.0, 0.3, 0.2), 0.5)
    return img


def acog():
    x, y = grid()
    r = np.hypot(x, y)
    img = np.zeros((N, N, 4))
    img = over(img, smooth(r - 0.62, 0.01), (0.65, 0.75, 0.60), 0.07)
    img = over(img, 1 - smooth(r - 0.62, 0.035), (0.0, 0.0, 0.0))                   # eye box
    img = over(img, smooth(np.abs(r - 0.60) - 0.02, 0.02) * 0.6, (0.0, 0.0, 0.0))
    # thin black posts from the edge, red chevron at the centre, bullet drop ticks below
    for sx, sy in ((0, -1), (-1, 0), (1, 0)):
        post = smooth(np.maximum(np.abs(x - sx * 0.36) - (0.26 if sx else 0.004), np.abs(y - sy * 0.36) - (0.004 if sx else 0.26)), 0.004)
        img = over(img, post * smooth(r - 0.62, 0.01), (0.02, 0.02, 0.02))
    img = over(img, smooth(np.maximum(np.abs(x) - 0.004, np.abs(y - 0.33) - 0.27), 0.004) * smooth(r - 0.62, 0.01), (0.02, 0.02, 0.02))
    red = (1.0, 0.18, 0.05)
    t = 0.007
    for s in (-1, 1):
        # chevron arms: lines from the tip (0,0) down to (+-0.06, 0.06)
        d = np.abs((x * s) - (y)) / np.sqrt(2)
        arm = smooth(d - t, 0.006) * (y > -0.002) * (y < 0.065) * (x * s >= -0.004)
        img = over(img, arm, red)
    for i, yy in enumerate((0.13, 0.19, 0.25, 0.31)):
        w = 0.05 - i * 0.008
        img = over(img, smooth(np.maximum(np.abs(x) - w, np.abs(y - yy) - 0.004), 0.004), (0.02, 0.02, 0.02))
    img = over(img, np.exp(-(r / 0.05) ** 2) * 0.35, (1.0, 0.35, 0.1), 0.5)
    return img


if __name__ == "__main__":
    save("scope_red_dot", red_dot())
    save("scope_holo", holo())
    save("scope_acog", acog())
    print("ok")
