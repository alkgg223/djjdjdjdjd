"""Detail textures for the cars (original art, drawn here): headlight and taillight units, grilles, number plates,
rims and brake discs. Transparent where there is nothing, so they work as cut-out decals over the body."""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

import meshkit as K

NAMES = {}


def reg(name, emissive=False):
    K.MATS[name] = ((1.0, 1.0, 1.0), "decal", False)
    if emissive:
        K.EMISSIVE.add(name)
    NAMES[name] = emissive
    return name


HEADLIGHTS = [reg("hl_classic", True), reg("hl_slim", True), reg("hl_muscle", True), reg("hl_sport", True), reg("hl_truck", True)]
TAILLIGHTS = [reg("tl_classic", True), reg("tl_bar", True), reg("tl_muscle", True), reg("tl_sport", True), reg("tl_tall", True)]
GRILLES = [reg("gr_bars"), reg("gr_hex"), reg("gr_cascade"), reg("gr_mesh"), reg("gr_truck")]
OTHER = [reg("plate_front"), reg("plate_rear"), reg("rim_alloy"), reg("rim_multi"), reg("rim_sport"), reg("rim_offroad"), reg("rim_steel"), reg("brake_disc"),
         reg("fog_light", True), reg("marker_amber", True), reg("badge"), reg("tyre_side"), reg("vent_mesh")]


def _canvas(w, h):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def _rounded(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, r, fill=fill, outline=outline, width=width)


def _glow_line(d, pts, color, width):
    for k, a in ((width + 6, 40), (width + 3, 110), (width, 255)):
        d.line(pts, fill=color[:3] + (a,), width=k, joint="curve")


def _shade_poly(im, poly, top, bottom):
    """fill a polygon with a vertical gradient"""
    w, h = im.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(poly, fill=255)
    grad = Image.new("RGBA", (w, h))
    gd = ImageDraw.Draw(grad)
    for y in range(h):
        t = y / max(1, h - 1)
        gd.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    im.paste(grad, (0, 0), mask)


def _bowl(d, cx, cy, r, glow=(250, 252, 255)):
    """a projector / reflector: chrome ring, dark bowl, bright lens"""
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(150, 156, 166, 255))
    d.ellipse((cx - r + 4, cy - r + 4, cx + r - 4, cy + r - 4), fill=(52, 56, 64, 255))
    r2 = int(r * 0.58)
    d.ellipse((cx - r2, cy - r2, cx + r2, cy + r2), fill=glow + (255,))
    d.ellipse((cx - r2 + 3, cy - r2 + 2, cx - 1, cy - 2), fill=(255, 255, 255, 255))


HL_SHAPES = {
    # outline of the right-hand lamp as seen from the front (outer end at the right of the picture)
    "hl_classic": [(4, 30), (150, 14), (252, 6), (250, 70), (200, 90), (14, 88)],
    "hl_slim": [(2, 44), (120, 30), (254, 6), (252, 48), (180, 70), (6, 74)],
    "hl_muscle": [(4, 10), (252, 4), (246, 86), (10, 92)],
    "hl_sport": [(2, 52), (110, 30), (254, 4), (250, 40), (160, 80), (8, 84)],
    "hl_truck": [(4, 6), (252, 6), (252, 90), (4, 90)],
}


def headlight(style):
    w, h = 256, 96
    im, d = _canvas(w, h)
    poly = HL_SHAPES[style]
    d.polygon(poly, fill=(200, 205, 212, 255))
    inner = [(x + (4 if x < 128 else -4), y + (4 if y < 48 else -4)) for x, y in poly]
    _shade_poly(im, inner, (70, 76, 86), (24, 26, 30))
    d = ImageDraw.Draw(im)
    if style == "hl_truck":
        d.rectangle((10, 12, w - 10, h - 12), fill=(28, 30, 34, 255))
        _bowl(d, 64, 48, 32)
        _bowl(d, 144, 48, 26)
        d.rectangle((196, 18, 244, 78), fill=(255, 170, 40, 255))
        _glow_line(d, [(14, 84), (w - 14, 84)], (255, 255, 248), 4)
        return im
    if style == "hl_muscle":
        _bowl(d, 58, 48, 34)
        _bowl(d, 140, 48, 30)
        d.rectangle((188, 26, 238, 70), fill=(255, 165, 40, 255))
        _glow_line(d, [(24, 84), (230, 82)], (255, 255, 245), 3)
        return im
    if style == "hl_classic":
        _bowl(d, 120, 50, 28)
        _bowl(d, 186, 44, 24)
        d.polygon([(20, 70), (80, 56), (84, 76), (24, 84)], fill=(255, 168, 40, 255))
        _glow_line(d, [(40, 30), (150, 18), (238, 12)], (255, 255, 250), 4)
        return im
    if style == "hl_slim":
        _bowl(d, 150, 46, 18)
        _bowl(d, 200, 36, 18)
        _glow_line(d, [(14, 66), (110, 56), (176, 62), (240, 40)], (255, 255, 250), 5)
        d.polygon([(30, 50), (90, 40), (92, 50), (32, 60)], fill=(255, 168, 40, 255))
        return im
    # sport: blue-white light blade and twin projectors
    _bowl(d, 170, 40, 20, glow=(235, 242, 255))
    _bowl(d, 214, 26, 16, glow=(235, 242, 255))
    _glow_line(d, [(16, 74), (100, 62), (150, 70), (244, 22)], (150, 200, 255), 5)
    _glow_line(d, [(40, 52), (108, 38)], (150, 200, 255), 3)
    return im


TL_SHAPES = {
    # outline of the right-hand lamp as seen from behind (outer end at the right of the picture)
    "tl_classic": [(4, 22), (252, 6), (250, 88), (120, 80), (6, 70)],
    "tl_bar": [(0, 26), (256, 26), (256, 70), (0, 70)],
    "tl_muscle": [(4, 10), (252, 10), (252, 86), (4, 86)],
    "tl_sport": [(2, 40), (140, 22), (254, 8), (252, 60), (160, 84), (4, 72)],
}


def taillight(style):
    if style == "tl_tall":
        w2, h2 = 96, 256
        im, d = _canvas(w2, h2)
        _rounded(d, (6, 4, w2 - 6, h2 - 4), 12, fill=(60, 8, 10, 255), outline=(30, 6, 6, 255), width=4)
        _shade_poly(im, [(12, 10), (w2 - 12, 10), (w2 - 12, h2 - 80), (12, h2 - 80)], (170, 20, 22), (100, 10, 12))
        d = ImageDraw.Draw(im)
        for y in range(28, h2 - 90, 24):
            _glow_line(d, [(22, y), (w2 - 22, y + 8)], (255, 60, 50), 4)
        _rounded(d, (16, h2 - 74, w2 - 16, h2 - 44), 6, fill=(235, 235, 235, 255))
        _rounded(d, (16, h2 - 40, w2 - 16, h2 - 14), 6, fill=(255, 150, 40, 255))
        return im
    w, h = 256, 96
    im, d = _canvas(w, h)
    poly = TL_SHAPES[style]
    d.polygon(poly, fill=(40, 6, 8, 255))
    inner = [(x + (4 if x < 128 else -4), y + (4 if y < 48 else -4)) for x, y in poly]
    _shade_poly(im, inner, (175, 22, 24), (95, 8, 10))
    d = ImageDraw.Draw(im)
    if style == "tl_bar":
        _glow_line(d, [(0, 40), (w, 40)], (255, 50, 40), 6)
        _glow_line(d, [(0, 56), (w, 56)], (255, 40, 30), 3)
        return im
    if style == "tl_muscle":
        for k in range(3):
            x0 = 14 + k * 80
            _rounded(d, (x0, 20, x0 + 70, 76), 10, fill=(70, 8, 10, 255), outline=(150, 156, 165, 255), width=3)
            _glow_line(d, [(x0 + 10, 48), (x0 + 60, 48)], (255, 50, 40), 6)
        return im
    if style == "tl_sport":
        for k in range(3):
            _glow_line(d, [(20, 52 + k * 7), (150, 34 + k * 7), (240, 20 + k * 7)], (255, 45, 35), 3)
        _rounded(d, (40, 64, 110, 74), 3, fill=(232, 232, 232, 255))
        return im
    _glow_line(d, [(16, 30), (240, 14), (238, 80)], (255, 55, 45), 6)
    _glow_line(d, [(40, 50), (200, 42)], (255, 40, 30), 3)
    _rounded(d, (30, 58, 110, 72), 4, fill=(232, 232, 232, 255))
    d.polygon([(130, 58), (200, 54), (204, 74), (134, 76)], fill=(255, 150, 40, 255))
    return im


def grille(style):
    w, h = 256, 128
    im, d = _canvas(w, h)
    if style == "gr_truck":
        _rounded(d, (2, 2, w - 3, h - 3), 14, fill=(18, 19, 22, 255), outline=(190, 195, 205, 255), width=6)
        for y in range(18, h - 12, 18):
            _rounded(d, (12, y, w - 12, y + 9), 4, fill=(185, 190, 198, 255))
        return im
    outline = (200, 205, 212, 255)
    _rounded(d, (2, 2, w - 3, h - 3), 22, fill=(14, 15, 17, 255), outline=outline, width=5)
    if style == "gr_bars":
        for y in range(16, h - 10, 16):
            _rounded(d, (12, y, w - 12, y + 6), 3, fill=(175, 180, 188, 255))
    elif style == "gr_hex":
        r = 9
        for row in range(10):
            for col in range(16):
                cx = 14 + col * 16 + (8 if row % 2 else 0)
                cy = 12 + row * 12
                pts = [(cx + r * math.cos(math.radians(60 * k + 30)), cy + r * 0.8 * math.sin(math.radians(60 * k + 30))) for k in range(6)]
                d.polygon(pts, outline=(70, 74, 80, 255))
    elif style == "gr_cascade":
        for row in range(9):
            for col in range(15):
                cx = 14 + col * 16 + (8 if row % 2 else 0)
                cy = 12 + row * 13
                d.polygon([(cx, cy - 5), (cx + 6, cy), (cx, cy + 5), (cx - 6, cy)], fill=(150, 155, 162, 255))
    else:
        for x in range(10, w - 8, 8):
            d.line([(x, 10), (x - 6, h - 10)], fill=(55, 58, 64, 255), width=2)
        for y in range(10, h - 8, 8):
            d.line([(10, y), (w - 10, y)], fill=(40, 42, 46, 255), width=1)
    return im


def plate(text):
    w, h = 256, 64
    im, d = _canvas(w, h)
    _rounded(d, (2, 2, w - 3, h - 3), 8, fill=(240, 240, 232, 255), outline=(30, 30, 34, 255), width=4)
    for cx in (14, w - 14):
        d.ellipse((cx - 4, h // 2 - 4, cx + 4, h // 2 + 4), fill=(120, 124, 130, 255))
    # Big blocky characters drawn with rectangles (no font needed).
    x = 30
    for ch in text:
        _glyph(d, ch, x, 12, 18, 40)
        x += 26
    return im


_SEG = {"0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc", "5": "afgcd", "6": "afgedc", "7": "abc", "8": "abcdefg",
        "9": "abcfgd", "R": "abcefg", "W": "fedcb", "-": "g", " ": ""}


def _glyph(d, ch, x, y, w, h):
    col = (25, 25, 30, 255)
    t = 5
    seg = _SEG.get(ch, "")
    boxes = {"a": (x, y, x + w, y + t), "b": (x + w - t, y, x + w, y + h // 2), "c": (x + w - t, y + h // 2, x + w, y + h),
             "d": (x, y + h - t, x + w, y + h), "e": (x, y + h // 2, x + t, y + h), "f": (x, y, x + t, y + h // 2),
             "g": (x, y + h // 2 - t // 2, x + w, y + h // 2 + t // 2)}
    for s in seg:
        d.rectangle(boxes[s], fill=col)


def rim(style):
    """rim face: outer lip, spokes and hub; open (transparent) between the spokes so the brake shows through"""
    s = 256
    im, d = _canvas(s, s)
    c = s / 2
    face = {"rim_alloy": (205, 208, 214), "rim_multi": (218, 220, 226), "rim_sport": (44, 46, 50), "rim_offroad": (70, 72, 76),
            "rim_steel": (150, 152, 156)}[style]
    edge = tuple(int(v * 0.62) for v in face)
    hi = tuple(min(255, int(v * 1.12) + 12) for v in face)
    # Outer lip: a ring with a bright edge.
    d.ellipse((3, 3, s - 3, s - 3), fill=edge + (255,))
    d.ellipse((9, 9, s - 9, s - 9), fill=hi + (255,))
    d.ellipse((20, 20, s - 20, s - 20), fill=(0, 0, 0, 0))
    spokes = {"rim_alloy": 5, "rim_multi": 10, "rim_sport": 7, "rim_offroad": 6, "rim_steel": 8}[style]
    wide = {"rim_alloy": 26, "rim_multi": 10, "rim_sport": 11, "rim_offroad": 30, "rim_steel": 18}[style]
    for k in range(spokes):
        a = 2 * math.pi * k / spokes - math.pi / 2
        offs = (-0.1, 0.1) if style == "rim_sport" else (0.0,)
        for off in offs:
            x1, y1 = c + math.cos(a + off) * (c - 16), c + math.sin(a + off) * (c - 16)
            x0, y0 = c + math.cos(a) * 30, c + math.sin(a) * 30
            d.line([(x0, y0), (x1, y1)], fill=edge + (255,), width=wide + 6)
            d.line([(x0, y0), (x1, y1)], fill=face + (255,), width=wide)
            # A bright edge down one side of each spoke.
            px, py = -math.sin(a + off) * wide * 0.3, math.cos(a + off) * wide * 0.3
            d.line([(x0 + px, y0 + py), (x1 + px, y1 + py)], fill=hi + (255,), width=max(2, wide // 4))
    if style == "rim_steel":
        d.ellipse((c - 70, c - 70, c + 70, c + 70), fill=face + (255,), outline=edge + (255,), width=4)
        for k in range(8):
            a = 2 * math.pi * k / 8
            x, y = c + math.cos(a) * 52, c + math.sin(a) * 52
            d.ellipse((x - 10, y - 7, x + 10, y + 7), fill=(0, 0, 0, 0))
    d.ellipse((c - 42, c - 42, c + 42, c + 42), fill=face + (255,), outline=edge + (255,), width=4)
    for k in range(5):
        a = 2 * math.pi * k / 5 + 0.3
        x, y = c + math.cos(a) * 26, c + math.sin(a) * 26
        d.ellipse((x - 6, y - 6, x + 6, y + 6), fill=(160, 164, 170, 255), outline=(60, 62, 66, 255))
    d.ellipse((c - 13, c - 13, c + 13, c + 13), fill=(60, 64, 70, 255), outline=(190, 194, 200, 255), width=2)
    return im


def brake_disc():
    s = 256
    im, d = _canvas(s, s)
    c = s / 2
    d.ellipse((10, 10, s - 10, s - 10), fill=(120, 122, 126, 255))
    for k in range(36):
        a = 2 * math.pi * k / 36
        for r in (78, 98):
            x, y = c + math.cos(a + r * 0.003) * r, c + math.sin(a + r * 0.003) * r
            d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=(50, 52, 56, 255))
    d.ellipse((c - 52, c - 52, c + 52, c + 52), fill=(70, 72, 76, 255))
    return im


def tyre_side():
    """sidewall ring (transparent in the middle where the rim sits)"""
    s = 256
    im, d = _canvas(s, s)
    c = s / 2
    d.ellipse((2, 2, s - 2, s - 2), fill=(22, 22, 24, 255))
    d.ellipse((10, 10, s - 10, s - 10), outline=(30, 30, 33, 255), width=6)
    for k in range(40):
        a = 2 * math.pi * k / 40
        x, y = c + math.cos(a) * 110, c + math.sin(a) * 110
        d.ellipse((x - 2, y - 2, x + 2, y + 2), fill=(38, 38, 42, 255))
    # Raised lettering band (just bars, no words).
    for k in range(10):
        a0 = 2 * math.pi * k / 10
        for j in range(5):
            a = a0 + j * 0.035
            x0, y0 = c + math.cos(a) * 97, c + math.sin(a) * 97
            x1, y1 = c + math.cos(a) * 104, c + math.sin(a) * 104
            d.line([(x0, y0), (x1, y1)], fill=(48, 48, 52, 255), width=2)
    r_in = int(c * 0.70)
    d.ellipse((c - r_in - 4, c - r_in - 4, c + r_in + 4, c + r_in + 4), fill=(12, 12, 13, 255))
    d.ellipse((c - r_in, c - r_in, c + r_in, c + r_in), fill=(0, 0, 0, 0))
    return im


def simple(name):
    if name == "fog_light":
        im, d = _canvas(64, 64)
        d.ellipse((4, 4, 60, 60), fill=(40, 42, 46, 255), outline=(170, 175, 182, 255), width=4)
        d.ellipse((14, 14, 50, 50), fill=(250, 250, 236, 255))
        return im
    if name == "marker_amber":
        im, d = _canvas(64, 32)
        _rounded(d, (2, 4, 62, 28), 8, fill=(255, 160, 40, 255))
        return im
    if name == "badge":
        im, d = _canvas(64, 48)
        d.ellipse((4, 4, 60, 44), outline=(215, 220, 228, 255), width=5)
        d.line([(20, 34), (32, 12), (44, 34)], fill=(215, 220, 228, 255), width=5)
        return im
    if name == "vent_mesh":
        im, d = _canvas(128, 64)
        _rounded(d, (2, 2, 126, 62), 8, fill=(16, 17, 19, 255), outline=(60, 62, 66, 255), width=3)
        for x in range(8, 124, 7):
            d.line([(x, 6), (x - 4, 58)], fill=(45, 47, 52, 255), width=2)
        return im
    raise KeyError(name)


def generate(res_dir):
    out = os.path.join(res_dir, "textures", "item", "vehicle")
    os.makedirs(out, exist_ok=True)
    for n in HEADLIGHTS:
        headlight(n).save(os.path.join(out, n + ".png"))
    for n in TAILLIGHTS:
        taillight(n).save(os.path.join(out, n + ".png"))
    for n in GRILLES:
        grille(n).save(os.path.join(out, n + ".png"))
    plate("RW 2026").save(os.path.join(out, "plate_front.png"))
    plate("RW 2026").save(os.path.join(out, "plate_rear.png"))
    for n in ("rim_alloy", "rim_multi", "rim_sport", "rim_offroad", "rim_steel"):
        rim(n).save(os.path.join(out, n + ".png"))
    brake_disc().save(os.path.join(out, "brake_disc.png"))
    tyre_side().save(os.path.join(out, "tyre_side.png"))
    for n in ("fog_light", "marker_amber", "badge", "vent_mesh"):
        simple(n).save(os.path.join(out, n + ".png"))
    flames(res_dir)


def flames(res_dir):
    """backfire flame (u along the flame from the tailpipe, v across) and the round glow at the pipe; drawn for
    additive blending, so black is see-through"""
    out = os.path.join(res_dir, "textures", "entity", "vehicle")
    os.makedirs(out, exist_ok=True)
    w, h = 128, 64
    u = np.linspace(0, 1, w)[None, :]
    v = np.linspace(-1, 1, h)[:, None]
    rng = np.random.default_rng(5)
    wobble = np.interp(np.linspace(0, 1, w), np.linspace(0, 1, 9), rng.uniform(-0.12, 0.12, 9))[None, :]
    half = 0.95 * (1 - u) ** 0.55 * (0.75 + 0.25 * np.sin(np.pi * np.clip(u * 1.4, 0, 1)))
    d = np.abs(v - wobble * u) / np.maximum(half, 1e-3)
    body = np.clip(1 - d, 0, 1) ** 1.3 * (1 - u) ** 0.7
    core = np.clip(1 - d * 1.8, 0, 1) * np.clip(1 - u * 3.0, 0, 1)
    r = np.clip(body * 1.9, 0, 1)
    g = np.clip(body ** 1.15 * 1.25 + core * 0.5, 0, 1)
    b = np.clip(body ** 3 * 0.35 + core * 1.0, 0, 1)
    img = np.stack([r, g, b, np.ones_like(r)], axis=-1)
    Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(os.path.join(out, "backfire.png"))
    s = 64
    yy, xx = np.mgrid[0:s, 0:s]
    rr = np.hypot(xx - s / 2 + 0.5, yy - s / 2 + 0.5) / (s / 2)
    gl = np.clip(1 - rr, 0, 1) ** 1.8
    core = np.clip(1 - rr * 2.2, 0, 1)
    img = np.stack([np.clip(gl * 1.2, 0, 1), np.clip(gl ** 1.5 * 0.7 + core * 0.4, 0, 1), np.clip(core * 0.9 + gl ** 3 * 0.2, 0, 1), np.ones_like(gl)],
                   axis=-1)
    Image.fromarray((img * 255).astype(np.uint8), "RGBA").save(os.path.join(out, "backfire_glow.png"))
