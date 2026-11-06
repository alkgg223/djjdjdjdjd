"""Cockpit textures in colour (H x W x 3, 0..1), each mapped over the whole face it is on: three display pages
(radar, moving map / HSI, engine and stores), round gauges (airspeed, attitude ball, altimeter), a console switch
panel, the up-front keypad and the caution light panel."""
import math

import numpy as np

S = 128


def _canvas(c):
    img = np.zeros((S, S, 3))
    img[:] = c
    return img


def _line(img, x0, y0, x1, y1, col, w=1):
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for k in range(n + 1):
        t = k / n
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        xi, yi = int(round(x)), int(round(y))
        img[max(0, yi - w // 2):min(S, yi + (w + 1) // 2), max(0, xi - w // 2):min(S, xi + (w + 1) // 2)] = col


def _circle(img, cx, cy, r, col, w=1, a0=0, a1=360):
    n = int(r * 6) + 8
    pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * k / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * k / n))) for k in range(n + 1)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        _line(img, x0, y0, x1, y1, col, w)


def _disc(img, cx, cy, r, col):
    yy, xx = np.mgrid[0:S, 0:S]
    img[(xx - cx) ** 2 + (yy - cy) ** 2 <= r * r] = col


def _rect(img, x0, y0, x1, y1, col, fill=False):
    if fill:
        img[int(y0):int(y1), int(x0):int(x1)] = col
        return
    _line(img, x0, y0, x1, y0, col)
    _line(img, x1, y0, x1, y1, col)
    _line(img, x1, y1, x0, y1, col)
    _line(img, x0, y1, x0, y0, col)


def _text(img, x, y, n, col, rng, h=3):
    """a row of dashes and blocks reading like small text"""
    for k in range(n):
        w = int(rng.integers(1, 3))
        img[y:y + h, x:x + w] = col
        x += w + 1
        if rng.random() < 0.2:
            x += 2


GREEN = (0.35, 1.0, 0.45)
DGREEN = (0.12, 0.45, 0.18)
CYAN = (0.3, 0.75, 1.0)
AMBER = (1.0, 0.7, 0.2)
WHITE = (0.92, 0.94, 0.92)


def _bezel(img, rng):
    """the display bezel: a dark frame with five push buttons along each edge"""
    frame = (0.16, 0.17, 0.17)
    img[:12, :] = frame
    img[-12:, :] = frame
    img[:, :12] = frame
    img[:, -12:] = frame
    for k in range(5):
        p = 22 + k * 20
        for (x0, y0) in ((p - 4, 3), (p - 4, S - 9), (3, p - 4), (S - 9, p - 4)):
            img[y0:y0 + 6, x0:x0 + 8] = (0.55, 0.56, 0.55)
            img[y0 + 1:y0 + 5, x0 + 1:x0 + 7] = (0.42, 0.43, 0.42)
    img[12, 12:S - 12] = (0.3, 0.3, 0.3)


def mfd(kind, seed=0):
    rng = np.random.default_rng(seed)
    img = _canvas((0.02, 0.06, 0.04))
    c = S // 2
    if kind == 0:
        # radar B-scope: range arcs, the azimuth lines, contacts, the cursor
        for r in (22, 36, 50):
            _circle(img, c, S - 14, r, DGREEN, 1, 200, 340)
        for a in (-60, -30, 0, 30, 60):
            x = c + math.sin(math.radians(a)) * 52
            _line(img, c, S - 14, x, S - 14 - math.cos(math.radians(a)) * 52, DGREEN)
        for _ in range(4):
            x, y = int(rng.integers(30, S - 30)), int(rng.integers(26, S - 40))
            img[y:y + 4, x:x + 4] = GREEN
        _line(img, c - 8, 50, c - 3, 50, WHITE)
        _line(img, c + 3, 50, c + 8, 50, WHITE)
        for k in range(4):
            _line(img, 20, 24 + k * 8, 26, 24 + k * 8, GREEN)
        _text(img, 18, 16, 10, GREEN, rng)
        _text(img, 76, 16, 9, GREEN, rng)
    elif kind == 1:
        # HSI / moving map: compass rose, the aircraft symbol, a route with waypoints
        _circle(img, c, c + 6, 40, GREEN, 1)
        for k in range(36):
            a = math.radians(k * 10)
            r0 = 36 if k % 3 else 32
            _line(img, c + math.sin(a) * r0, c + 6 - math.cos(a) * r0, c + math.sin(a) * 40, c + 6 - math.cos(a) * 40, GREEN)
        _disc(img, c, c + 6, 7, CYAN)
        _line(img, c, c - 4, c, c + 18, WHITE, 2)
        _line(img, c - 9, c + 6, c + 9, c + 6, WHITE, 2)
        pts = [(c - 30, S - 22), (c - 6, c + 10), (c + 14, c - 14), (c + 34, 30)]
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            _line(img, x0, y0, x1, y1, CYAN)
        for x, y in pts:
            _rect(img, x - 3, y - 3, x + 3, y + 3, CYAN)
        _text(img, 18, 16, 8, GREEN, rng)
        _text(img, 80, S - 20, 7, GREEN, rng)
    else:
        # engine and stores page: two tape gauges, the planform with the stations
        for k, x in enumerate((24, 38)):
            _rect(img, x, 24, x + 8, 92, GREEN)
            h = int(30 + 20 * rng.random())
            _rect(img, x + 1, 92 - h, x + 8, 92, GREEN, True)
        _line(img, 84, 30, 84, 96, GREEN)
        _line(img, 62, 64, 106, 64, GREEN)
        _line(img, 74, 86, 94, 86, GREEN)
        for x in (66, 74, 94, 102):
            _rect(img, x - 2, 66, x + 2, 76, AMBER if x in (66, 102) else GREEN, True)
        _text(img, 18, 16, 12, GREEN, rng)
        _text(img, 60, 102, 9, GREEN, rng)
    _bezel(img, rng)
    return img


def dial(kind, seed=0):
    rng = np.random.default_rng(seed)
    img = _canvas((0.2, 0.21, 0.21))
    c = S // 2
    _disc(img, c, c, 60, (0.55, 0.56, 0.56))
    _disc(img, c, c, 54, (0.04, 0.04, 0.045))
    if kind == 1:
        # attitude ball: sky and earth halves, the pitch lines, the wings
        yy, xx = np.mgrid[0:S, 0:S]
        ins = (xx - c) ** 2 + (yy - c) ** 2 <= 50 * 50
        img[ins & (yy < c + 4)] = (0.25, 0.55, 0.9)
        img[ins & (yy >= c + 4)] = (0.45, 0.3, 0.16)
        for k in (-2, -1, 1, 2):
            _line(img, c - 14, c + 4 + k * 12, c + 14, c + 4 + k * 12, WHITE)
        _line(img, c - 34, c, c - 10, c, AMBER, 3)
        _line(img, c + 10, c, c + 34, c, AMBER, 3)
        _disc(img, c, c, 3, AMBER)
    else:
        # a dial: ticks round the face, numerals, the needle
        for k in range(40):
            a = math.radians(k * 9 - 90)
            r0 = 44 if k % 4 else 38
            _line(img, c + math.cos(a) * r0, c + math.sin(a) * r0, c + math.cos(a) * 52, c + math.sin(a) * 52, WHITE, 1 if k % 4 else 2)
        for k in range(10):
            a = math.radians(k * 36 - 90)
            x, y = int(c + math.cos(a) * 30) - 2, int(c + math.sin(a) * 30) - 2
            img[y:y + 5, x:x + 4] = WHITE
            img[y + 1:y + 4, x + 1:x + 3] = (0.04, 0.04, 0.045)
        a = math.radians(rng.uniform(0, 360))
        _line(img, c, c, c + math.cos(a) * 46, c + math.sin(a) * 46, (1, 1, 1) if kind == 0 else AMBER, 3)
        a2 = a + rng.uniform(1.5, 4.5)
        if kind == 2:
            _line(img, c, c, c + math.cos(a2) * 30, c + math.sin(a2) * 30, WHITE, 4)
        _disc(img, c, c, 5, (0.6, 0.6, 0.6))
    return img


def switches(seed=0):
    """a console panel: rows of toggle switches with their guards and labels, a couple of knobs"""
    rng = np.random.default_rng(seed)
    img = _canvas((0.24, 0.25, 0.26))
    for y in range(8, S, 30):
        img[y - 4, 4:S - 4] = (0.15, 0.15, 0.16)
        x = 8
        while x < S - 14:
            if rng.random() < 0.25:
                _disc(img, x + 6, y + 10, 6, (0.1, 0.1, 0.1))
                _disc(img, x + 6, y + 10, 4, (0.6, 0.6, 0.62))
                x += 18
                continue
            img[y + 6:y + 16, x + 2:x + 8] = (0.08, 0.08, 0.08)
            up = rng.random() < 0.5
            img[(y + 3 if up else y + 11):(y + 9 if up else y + 18), x + 4:x + 6] = (0.85, 0.86, 0.86)
            if rng.random() < 0.2:
                img[y + 4:y + 18, x:x + 2] = (0.75, 0.15, 0.1)
            _text(img, x, y + 21, 3, WHITE, rng, 2)
            x += 14
    return img


def keypad(seed=0):
    """the up-front control panel: a small green display over a 3 x 4 keypad"""
    rng = np.random.default_rng(seed)
    img = _canvas((0.18, 0.19, 0.19))
    img[10:44, 14:S - 14] = (0.02, 0.07, 0.04)
    for k in range(3):
        _text(img, 20, 16 + k * 9, 16, GREEN, rng)
    for r in range(4):
        for c in range(3):
            x, y = 26 + c * 28, 54 + r * 18
            img[y:y + 13, x:x + 20] = (0.5, 0.51, 0.5)
            img[y + 1:y + 12, x + 1:x + 19] = (0.36, 0.37, 0.37)
            img[y + 4:y + 9, x + 8:x + 12] = WHITE
    return img


def caution(seed=0):
    """warning / caution lights: a grid of small captions, a few of them lit"""
    rng = np.random.default_rng(seed)
    img = _canvas((0.1, 0.1, 0.1))
    for r in range(6):
        for c in range(4):
            x, y = 6 + c * 30, 6 + r * 20
            lit = rng.random() < 0.25
            col = (AMBER if rng.random() < 0.6 else GREEN) if lit else (0.22, 0.2, 0.16)
            img[y:y + 15, x:x + 26] = col
            img[y + 6:y + 9, x + 4:x + 22] = (0.1, 0.1, 0.1) if lit else (0.32, 0.3, 0.25)
    return img


def all_textures():
    return {"mfd_a": mfd(0, 1), "mfd_b": mfd(1, 2), "mfd_c": mfd(2, 3), "dial_a": dial(0, 4), "dial_b": dial(1, 5), "dial_c": dial(2, 6),
            "switches": switches(7), "keypad": keypad(8), "caution": caution(9)}
