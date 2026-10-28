"""Model kit: builds Minecraft JSON element models with a per-face painted texture atlas.

Every face of every element gets its own rectangle in the atlas, painted from a material
(procedural noise / wood grain / stipple ...) plus a bevel and optional decorations.
Also contains a small software rasterizer used to preview models before they go in game.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)
FACES = ("north", "south", "east", "west", "up", "down")


# ----------------------------------------------------------------------------- materials
def _hex(c):
    c = c.lstrip("#")
    return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


def _blur(a, r=1):
    out = a.copy()
    for _ in range(r):
        out = (out + np.roll(out, 1, 0) + np.roll(out, -1, 0) + np.roll(out, 1, 1) + np.roll(out, -1, 1)) / 5.0
    return out


def _noise(rng, size, scale):
    """value noise, tileable, in [-1, 1]"""
    n = max(2, size // scale)
    g = rng.uniform(-1, 1, (n, n)).astype(np.float32)
    img = Image.fromarray(((g + 1) * 127.5).astype(np.uint8))
    big = np.asarray(img.resize((size, size), Image.BICUBIC), dtype=np.float32) / 127.5 - 1
    return big


MATERIALS = {
    # name: (base colour, style, variation, bevel strength)
    "gunmetal": ("#2c2f35", "metal", 0.05, 0.22),
    "steel": ("#565b63", "metal", 0.06, 0.20),
    "bright": ("#9aa1aa", "metal", 0.07, 0.18),
    "chrome": ("#c3c8cf", "metal", 0.05, 0.15),
    "blued": ("#1d2129", "metal", 0.04, 0.25),
    "polymer": ("#1c1d20", "stipple", 0.05, 0.22),
    "polymer_grey": ("#3a3c40", "stipple", 0.05, 0.20),
    "tan": ("#9a8460", "stipple", 0.05, 0.18),
    "olive": ("#4a5230", "stipple", 0.05, 0.18),
    "wood": ("#6e3f20", "wood", 0.10, 0.18),
    "wood_light": ("#9a6334", "wood", 0.10, 0.16),
    "wood_red": ("#7a3418", "wood", 0.10, 0.18),
    "rubber": ("#151515", "matte", 0.03, 0.20),
    "black": ("#0a0a0b", "matte", 0.02, 0.10),
    "brass": ("#c79a3b", "metal", 0.08, 0.20),
    "copper": ("#b4693a", "metal", 0.08, 0.20),
    "lens": ("#1b3550", "glass", 0.0, 0.10),
    "lens_red": ("#4a1a1a", "glass", 0.0, 0.10),
    "glow_red": ("#ff2a1a", "glow", 0.0, 0.0),
    "glow_green": ("#39ff5a", "glow", 0.0, 0.0),
    "glow_orange": ("#ffa020", "glow", 0.0, 0.0),
    "glow_blue": ("#40c8ff", "glow", 0.0, 0.0),
    "glow_white": ("#fff6dc", "glow", 0.0, 0.0),
    "orange": ("#d0701c", "matte", 0.05, 0.18),
    "red": ("#a3221c", "paint", 0.05, 0.18),
    "yellow": ("#d9b21c", "paint", 0.05, 0.18),
    "white": ("#d8d8d2", "paint", 0.04, 0.15),
    "od_paint": ("#4c5534", "paint", 0.06, 0.18),
    "desert_paint": ("#b39a6c", "paint", 0.06, 0.16),
    "grey_paint": ("#6c7076", "paint", 0.05, 0.18),
    "dark_paint": ("#2e3236", "paint", 0.05, 0.20),
    "canvas": ("#7d7152", "canvas", 0.07, 0.15),
    "canvas_green": ("#55603c", "canvas", 0.07, 0.15),
    "sand": ("#b49f72", "canvas", 0.08, 0.10),
    "concrete": ("#8e8c86", "concrete", 0.08, 0.14),
    "concrete_dark": ("#5f5e5a", "concrete", 0.08, 0.14),
    "leather": ("#5a3a22", "matte", 0.07, 0.18),
    "cloth_green": ("#3f4a2c", "canvas", 0.08, 0.14),
    "cloth_red": ("#6b2620", "canvas", 0.08, 0.14),
    "cloth_tan": ("#8b7a57", "canvas", 0.08, 0.14),
    "skin": ("#c99574", "matte", 0.03, 0.10),
    "plastic_white": ("#e6e6e0", "matte", 0.03, 0.12),
    "screen": ("#0c2a1c", "screen", 0.0, 0.10),
    "screen_red": ("#2a0c0c", "screen", 0.0, 0.10),
    "rust": ("#6e3b22", "concrete", 0.12, 0.15),
    "tire": ("#1a1a1a", "tire", 0.04, 0.12),
    "hazard": ("#d9b21c", "hazard", 0.0, 0.10),
    "camo_wood": ("#4c5534", "camo", 0.0, 0.16),
    "camo_desert": ("#b39a6c", "camo_d", 0.0, 0.14),
    "brick": ("#8a4b35", "brick", 0.0, 0.10),
    "grey_metal": ("#7c8086", "metal", 0.06, 0.18),
    "paint_white": ("#c8c8c8", "paint", 0.05, 0.16),  # neutral paint for runtime tinting
    "smoke": ("#3b3530", "glass", 0.0, 0.14),
    "glass_clear": ("#9fc4d6", "glass", 0.0, 0.08),
    "decal": ("#000000", "matte", 0.0, 0.0),
    "tintcloth": ("#cfcfc8", "canvas", 0.06, 0.12),
    "track": ("#2a2826", "metal", 0.08, 0.30),
    "rotor": ("#232528", "paint", 0.04, 0.18),
}

MAT_ALPHA = {"glass_clear": 0.38, "decal": 0.0}
TINT_MATS = {"paint_white", "tintcloth"}
_unused = {
}

_tile_cache = {}


def material_tile(name, size=256):
    if name in _tile_cache:
        return _tile_cache[name]
    base, style, var, _ = MATERIALS[name]
    col = _hex(base)
    rng = np.random.default_rng(abs(hash(name)) % (2 ** 31))
    lum = np.zeros((size, size), np.float32)
    if style == "metal":
        lum += _noise(rng, size, 16) * var * 0.6
        lum += rng.normal(0, var * 0.35, (size, size)).astype(np.float32)
        streak = _blur(rng.normal(0, 1, (size, 1)).astype(np.float32).repeat(size, 1), 0)
        lum += np.roll(streak, 0, 1) * var * 0.25
        # rare small scratches
        for _ in range(size // 6):
            x, y, l = rng.integers(0, size), rng.integers(0, size), rng.integers(3, 9)
            for k in range(l):
                lum[(y + k // 3) % size, (x + k) % size] += 0.07
    elif style == "stipple":
        lum += _noise(rng, size, 32) * var * 0.4
        lum += (rng.random((size, size)) > 0.72).astype(np.float32) * var * 1.2 - var * 0.3
        lum = _blur(lum, 1) * 0.6 + lum * 0.4
    elif style == "wood":
        y = np.arange(size, dtype=np.float32)[:, None]
        x = np.arange(size, dtype=np.float32)[None, :]
        turb = _blur(_noise(rng, size, 48), 2) * 9 + np.sin(x * 0.05) * 3
        grain = np.sin((y + turb) * 0.55)
        grain2 = np.sin((y * 1.9 + turb * 1.3) * 0.5)
        lum += (grain * 0.65 + grain2 * 0.35) * var * 0.9
        lum += _blur(_noise(rng, size, 64), 1) * var * 0.6
        lum += rng.normal(0, var * 0.12, (size, size)).astype(np.float32)
    elif style == "matte":
        lum += _noise(rng, size, 24) * var + rng.normal(0, var * 0.4, (size, size)).astype(np.float32)
    elif style == "paint":
        lum += _noise(rng, size, 32) * var * 0.8 + rng.normal(0, var * 0.3, (size, size)).astype(np.float32)
        chips = (_noise(rng, size, 6) > 0.82).astype(np.float32)
        lum += chips * 0.10
    elif style == "canvas":
        y = np.arange(size)[:, None]
        x = np.arange(size)[None, :]
        weave = ((x + y) % 2) * 0.5 + ((x // 2 + y // 2) % 2) * 0.5
        lum += (weave - 0.5) * var * 0.8 + _noise(rng, size, 20) * var
    elif style == "concrete":
        lum += _noise(rng, size, 20) * var + _noise(rng, size, 6) * var * 0.6
        lum += rng.normal(0, var * 0.5, (size, size)).astype(np.float32)
        lum -= (rng.random((size, size)) > 0.985).astype(np.float32) * 0.12
    elif style == "glass":
        y = np.arange(size, dtype=np.float32)[:, None]
        x = np.arange(size, dtype=np.float32)[None, :]
        lum += ((x + y) % 48 < 6).astype(np.float32) * 0.25 + (y / size) * -0.08
    elif style == "glow":
        pass
    elif style == "screen":
        y = np.arange(size)[:, None]
        lum += ((y % 3) == 0).astype(np.float32) * 0.05 + _noise(rng, size, 16) * 0.04
    elif style == "tire":
        x = np.arange(size)[None, :]
        lum += ((x % 6) < 2).astype(np.float32) * 0.04 + _noise(rng, size, 16) * 0.03
    elif style == "hazard":
        y = np.arange(size)[:, None]
        x = np.arange(size)[None, :]
        stripe = (((x + y) // 6) % 2).astype(np.float32)
        rgb = np.where(stripe[..., None] > 0, _hex("#d9b21c"), _hex("#1a1a1a"))
        rgb = np.broadcast_to(rgb, (size, size, 3)).copy()
        _tile_cache[name] = np.clip(rgb, 0, 1)
        return _tile_cache[name]
    elif style in ("camo", "camo_d"):
        pal = ["#4c5534", "#2f3522", "#6b6a45", "#1f2218"] if style == "camo" else ["#b39a6c", "#8c7550", "#d1bd8f", "#6e5a3c"]
        n1, n2, n3 = _noise(rng, size, 40), _noise(rng, size, 28), _noise(rng, size, 20)
        idx = np.zeros((size, size), np.int32)
        idx[n1 > 0.25] = 1
        idx[n2 > 0.35] = 2
        idx[n3 > 0.45] = 3
        rgb = np.stack([_hex(p) for p in pal])[idx]
        rgb = rgb * (1 + rng.normal(0, 0.03, (size, size, 1)))
        _tile_cache[name] = np.clip(rgb, 0, 1).astype(np.float32)
        return _tile_cache[name]
    elif style == "brick":
        y = np.arange(size)[:, None]
        x = np.arange(size)[None, :]
        row = y // 8
        mortar = ((y % 8) == 0) | (((x + (row % 2) * 8) % 16) == 0)
        lum += _noise(rng, size, 8) * 0.06
        rgb = col[None, None, :] * (1 + lum[..., None])
        rgb[mortar] = _hex("#a9a49a")
        _tile_cache[name] = np.clip(rgb, 0, 1).astype(np.float32)
        return _tile_cache[name]
    rgb = col[None, None, :] * (1.0 + lum[..., None])
    _tile_cache[name] = np.clip(rgb, 0, 1).astype(np.float32)
    return _tile_cache[name]


# 3x5 pixel font for stencil text
FONT = {
    "A": "010101111101101", "B": "110101110101110", "C": "011100100100011", "D": "110101101101110",
    "E": "111100110100111", "F": "111100110100100", "G": "011100101101011", "H": "101101111101101",
    "I": "111010010010111", "J": "001001001101010", "K": "101101110101101", "L": "100100100100111",
    "M": "101111111101101", "N": "110101101101101", "O": "010101101101010", "P": "110101110100100",
    "Q": "010101101110011", "R": "110101110101101", "S": "011100010001110", "T": "111010010010010",
    "U": "101101101101111", "V": "101101101101010", "W": "101101111111101", "X": "101101010101101",
    "Y": "101101010010010", "Z": "111001010100111", "0": "111101101101111", "1": "010110010010111",
    "2": "110001010100111", "3": "110001010001110", "4": "101101111001001", "5": "111100110001110",
    "6": "011100111101111", "7": "111001010010010", "8": "111101111101111", "9": "111101111001110",
    "-": "000000111000000", ".": "000000000000010", " ": "000000000000000", "/": "001001010100100",
    "x": "000101010101000", "!": "010010010000010", "+": "000010111010000",
}


def draw_text(img, x, y, text, color, scale=1):
    h, w, _ = img.shape
    for ch in text:
        g = FONT.get(ch.upper(), FONT[" "])
        for i in range(15):
            if g[i] == "1":
                for sy in range(scale):
                    for sx in range(scale):
                        px, py = x + (i % 3) * scale + sx, y + (i // 3) * scale + sy
                        if 0 <= px < w and 0 <= py < h:
                            img[py, px] = color
        x += 4 * scale
    return x


def text_width(text, scale=1):
    return len(text) * 4 * scale - scale


# ----------------------------------------------------------------------------- geometry
def face_dims(e, f):
    dx, dy, dz = (e["to"][i] - e["from"][i] for i in range(3))
    if f in ("north", "south"):
        return dx, dy
    if f in ("east", "west"):
        return dz, dy
    return dx, dz


class Model:
    def __init__(self, name, density=4.0, atlas_w=256, seed=1):
        self.name = name
        self.density = density
        self.atlas_w = atlas_w
        self.elements = []
        self.rng = random.Random(seed)
        self.parts = {}
        self.mounts = {}  # named points, model units

    # --- primitives -------------------------------------------------------------------
    def box(self, frm, to, mat, rot=None, deco=(), faces=None, part="body", face_mat=None, light=False):
        frm = [float(v) for v in frm]
        to = [float(v) for v in to]
        for i in range(3):
            if to[i] < frm[i]:
                frm[i], to[i] = to[i], frm[i]
            if to[i] - frm[i] < 0.01:
                to[i] = frm[i] + 0.01
        for v in frm + to:
            assert -16 <= v <= 32, f"{self.name}: coordinate out of range {frm} {to}"
        if rot is not None:
            axis, ang, origin = rot
            if ang == 0:
                rot = None
            else:
                assert ang in ANGLES, f"bad angle {ang}"
                rot = (axis, float(ang), [float(o) for o in origin])
        e = {"from": frm, "to": to, "mat": mat, "rot": rot, "deco": list(deco),
             "faces": set(faces) if faces else set(FACES), "part": part,
             "face_mat": dict(face_mat or {}), "light": light}
        self.elements.append(e)
        return e

    def cbox(self, c, size, mat, **kw):
        """box from centre + size"""
        frm = [c[i] - size[i] / 2 for i in range(3)]
        to = [c[i] + size[i] / 2 for i in range(3)]
        return self.box(frm, to, mat, **kw)

    def cyl(self, axis, c, r, a0, a1, mat, sides=8, part="body", deco=(), end_mat=None, caps=True):
        """Faceted cylinder along axis ('x','y','z'), centre c=(u,v) in the other two axes (in xyz order),
        apothem r, from a0 to a1 along the axis. sides = 8 or 16."""
        s = 2 * r * math.tan(math.pi / sides)
        if sides == 8:
            rots = [0.0, 45.0]
        elif sides == 16:
            rots = [0.0, 22.5, 45.0, -22.5]
        else:
            rots = [0.0]
            s = 2 * r
        out = []
        k = 0
        for ang in rots:
            for swap in (False, True):
                if sides == 4 and swap:
                    continue
                w1, w2 = (2 * r, s) if not swap else (s, 2 * r)
                eps = k * 0.012
                lo, hi = a0 + eps, a1 - eps
                frm = [0, 0, 0]
                to = [0, 0, 0]
                ax = "xyz".index(axis)
                others = [i for i in range(3) if i != ax]
                frm[ax], to[ax] = lo, hi
                frm[others[0]], to[others[0]] = c[0] - w1 / 2, c[0] + w1 / 2
                frm[others[1]], to[others[1]] = c[1] - w2 / 2, c[1] + w2 / 2
                origin = [0, 0, 0]
                origin[ax] = (lo + hi) / 2
                origin[others[0]] = c[0]
                origin[others[1]] = c[1]
                fm = {}
                if end_mat:
                    for f in ({"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}[axis]):
                        fm[f] = end_mat
                fcs = set(FACES)
                if not caps:
                    fcs -= set({"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}[axis])
                out.append(self.box(frm, to, mat, rot=(axis, ang, origin) if ang else None, part=part,
                                    deco=deco, face_mat=fm, faces=fcs))
                k += 1
        return out

    def rod(self, p0, p1, r, mat, part="body", sides=4):
        """straight bar between two points lying in an axis plane (approximated with allowed angles)."""
        # choose dominant plane
        d = [p1[i] - p0[i] for i in range(3)]
        L = math.sqrt(sum(v * v for v in d))
        c = [(p0[i] + p1[i]) / 2 for i in range(3)]
        # find axis of rotation: the axis with ~0 delta
        zero = [abs(v) < 1e-6 for v in d]
        if sum(zero) >= 2:
            ax = [i for i in range(3) if not zero[i]][0]
            size = [2 * r] * 3
            size[ax] = L
            return self.cbox(c, size, mat, part=part)
        rot_axis = zero.index(True) if True in zero else 2
        others = [i for i in range(3) if i != rot_axis]
        a, b = others
        ang = math.degrees(math.atan2(d[b], d[a]))
        # snap to allowed angles relative to axis a
        best = min(ANGLES, key=lambda x: abs(x - ang))
        size = [2 * r] * 3
        size[a] = L
        # sign convention for MC rotation about axes
        axis_name = "xyz"[rot_axis]
        mc_ang = best if axis_name != "y" else -best
        if axis_name == "x":
            mc_ang = best  # rotating y->z
        return self.cbox(c, size, mat, rot=(axis_name, mc_ang, c), part=part)

    def mount(self, name, p):
        self.mounts[name] = [float(v) for v in p]

    # --- texture atlas ----------------------------------------------------------------
    def build(self, tex_path, model_dir, tex_ref, display=None, parts_out=None, extra=None, atlas_h=None):
        """Pack + paint the atlas, write one JSON per part. parts_out: {part: json_path}"""
        rects = []
        pad = 1
        for ei, e in enumerate(self.elements):
            for f in FACES:
                if f not in e["faces"]:
                    continue
                a, b = face_dims(e, f)
                w = max(1, int(round(a * self.density)))
                h = max(1, int(round(b * self.density)))
                rects.append([ei, f, w, h])
        # shelf pack, tallest first
        order = sorted(range(len(rects)), key=lambda i: (-rects[i][3], -rects[i][2]))
        W = self.atlas_w
        x = y = rowh = 0
        pos = {}
        for i in order:
            _, _, w, h = rects[i]
            w2, h2 = w + 2 * pad, h + 2 * pad
            if w2 > W:
                raise ValueError(f"{self.name}: face too wide {w2} > {W}")
            if x + w2 > W:
                x = 0
                y += rowh
                rowh = 0
            pos[i] = (x + pad, y + pad)
            x += w2
            rowh = max(rowh, h2)
        used_h = y + rowh
        H = atlas_h or 16
        while H < used_h:
            H *= 2
        if H > 2048:
            raise ValueError(f"{self.name}: atlas too tall {used_h}")
        img = np.zeros((H, W, 3), np.float32)
        alpha = np.zeros((H, W), np.float32)
        uvs = {}
        for i, (ei, f, w, h) in enumerate(rects):
            px, py = pos[i]
            e = self.elements[ei]
            mat = e["face_mat"].get(f, e["mat"])
            self._apatch = np.full((h, w), MAT_ALPHA.get(mat, 1.0), np.float32)
            patch = self._paint_face(e, f, mat, w, h)
            img[py:py + h, px:px + w] = patch
            alpha[py:py + h, px:px + w] = self._apatch
            # pad: replicate edges
            img[py - 1, px:px + w] = patch[0]
            img[py + h, px:px + w] = patch[-1]
            img[py - 1:py + h + 1, px - 1] = img[py - 1:py + h + 1, px]
            img[py - 1:py + h + 1, px + w] = img[py - 1:py + h + 1, px + w - 1]
            ap = self._apatch
            alpha[py - 1, px:px + w] = ap[0]
            alpha[py + h, px:px + w] = ap[-1]
            alpha[py - 1:py + h + 1, px - 1] = alpha[py - 1:py + h + 1, px]
            alpha[py - 1:py + h + 1, px + w] = alpha[py - 1:py + h + 1, px + w - 1]
            uvs[(ei, f)] = (px * 16.0 / W, py * 16.0 / H, (px + w) * 16.0 / W, (py + h) * 16.0 / H)
        rgba = np.concatenate([np.clip(img, 0, 1), alpha[..., None]], axis=2)
        os.makedirs(os.path.dirname(tex_path), exist_ok=True)
        Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), "RGBA").save(tex_path)
        self.atlas = rgba
        self.uvs = uvs
        # json per part
        parts = sorted(set(e["part"] for e in self.elements))
        out = {}
        for p in parts:
            elems = []
            for ei, e in enumerate(self.elements):
                if e["part"] != p:
                    continue
                je = {"from": [round(v, 4) for v in e["from"]], "to": [round(v, 4) for v in e["to"]]}
                if e["rot"]:
                    je["rotation"] = {"angle": e["rot"][1], "axis": e["rot"][0], "origin": [round(v, 4) for v in e["rot"][2]]}
                faces = {}
                for f in FACES:
                    if (ei, f) in uvs:
                        faces[f] = {"uv": [round(v, 5) for v in uvs[(ei, f)]], "texture": "#0"}
                        if e["face_mat"].get(f, e["mat"]) in TINT_MATS:
                            faces[f]["tintindex"] = 0
                je["faces"] = faces
                if e.get("light"):
                    je["neoforge_data"] = {"block_light": 15, "sky_light": 15}
                elems.append(je)
            m = {"textures": {"0": tex_ref, "particle": tex_ref}, "elements": elems}
            if display:
                m["display"] = display
            if extra:
                m.update(extra)
            path = (parts_out or {}).get(p) or os.path.join(model_dir, f"{p}.json")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w") as fh:
                json.dump(m, fh, separators=(",", ":"))
            out[p] = path
        return out

    def _paint_face(self, e, f, mat, w, h):
        tile = material_tile(mat)
        T = tile.shape[0]
        rng = self.rng
        style = MATERIALS[mat][1]
        if style == "wood" and h > w:
            tile = tile.transpose(1, 0, 2)
        ox, oy = rng.randrange(T), rng.randrange(T)
        ys = (np.arange(h) + oy) % T
        xs = (np.arange(w) + ox) % T
        patch = tile[ys][:, xs].copy()
        bev = MATERIALS[mat][3]
        if style == "glass":
            g = np.linspace(1.25, 0.75, h)[:, None, None]
            patch = patch * g
            if w >= 3 and h >= 3:
                patch[0:max(1, h // 4), 0:max(1, w // 3)] = np.clip(patch[0:max(1, h // 4), 0:max(1, w // 3)] + 0.25, 0, 1)
        if bev > 0 and w >= 3 and h >= 3:
            patch[0, :] *= 1 + bev
            patch[:, 0] *= 1 + bev * 0.7
            patch[-1, :] *= 1 - bev
            patch[:, -1] *= 1 - bev * 0.7
        for d in e["deco"]:
            self._deco(patch, d, f, w, h, e)
        return np.clip(patch, 0, 1)

    def _deco(self, p, d, f, w, h, e):
        side = f in ("north", "south")
        if ":" in d:
            kind, arg = d.split(":", 1)
        else:
            kind, arg = d, ""
        dark = np.array([0.05, 0.05, 0.06], np.float32)
        if kind == "rail" and f == "up":
            for x in range(0, w, 3):
                p[:, x:x + 1] *= 0.45
            p[0, :] *= 1.2
        elif kind == "rail" and side:
            for x in range(0, w, 3):
                p[: max(1, h // 2), x:x + 1] *= 0.5
        elif kind == "grip" and side:
            for yy in range(1, h - 1):
                for xx in range(1, w - 1):
                    if (xx + yy) % 3 == 0 or (xx - yy) % 3 == 0:
                        p[yy, xx] *= 0.7
        elif kind == "slots" and side:
            n = max(1, int(arg or 4))
            sw = max(2, w // (n * 2 + 1))
            for i in range(n):
                x0 = int((i * 2 + 1) * w / (n * 2 + 1))
                p[h // 3:h - h // 3, x0:x0 + sw] = dark
        elif kind == "vslots" and side:
            for x in range(2, w - 2, 3):
                p[2:h - 2, x] = dark
        elif kind == "rivets" and side:
            for x in (2, w - 3):
                for y in (2, h - 3):
                    if 0 <= x < w and 0 <= y < h:
                        p[y, x] = np.clip(p[y, x] * 1.8 + 0.1, 0, 1)
        elif kind == "lines" and side:
            n = int(arg or 3)
            for i in range(1, n + 1):
                y = int(i * h / (n + 1))
                p[y, 1:w - 1] *= 0.6
        elif kind == "hlines":
            n = int(arg or 3)
            for i in range(1, n + 1):
                y = int(i * h / (n + 1))
                p[y, 1:w - 1] *= 0.6
        elif kind == "vlines":
            n = int(arg or 3)
            for i in range(1, n + 1):
                x = int(i * w / (n + 1))
                p[1:h - 1, x] *= 0.6
        elif kind == "port" and f == "south":
            pw = min(int(w * 0.3), int(self.density * 3.2))
            x0 = int(w * 0.42)
            x1 = x0 + max(2, pw)
            y0, y1 = int(h * 0.22), max(int(h * 0.22) + 2, int(h * 0.58))
            p[y0:y1, x0:x1] = dark * 1.5
            p[y1 - 1, x0:x1] = np.array([0.55, 0.45, 0.25], np.float32)  # brass glint of the chamber
            p[y0, x0:x1] *= 0.5
        elif kind == "serr" and side:
            for x in range(1, min(w - 1, int(w * 0.25)), 2):
                p[1:h - 1, x] *= 0.55
        elif kind == "ribs" and side:
            for y in range(2, h - 1, 3):
                p[y, 1:w - 1] *= 0.65
        elif kind == "bore":
            if f in ("east", "west", "up", "down", "north", "south") and w >= 2 and h >= 2:
                cx, cy = (w - 1) / 2, (h - 1) / 2
                rr = min(w, h) * 0.32
                yy, xx = np.mgrid[0:h, 0:w]
                m = (xx - cx) ** 2 + (yy - cy) ** 2 <= rr * rr
                p[m] = dark
        elif kind == "text" and side:
            txt = arg
            col = np.clip(p.mean(axis=(0, 1)) * 1.6 + 0.18, 0, 0.62)
            tw = text_width(txt)
            if tw <= w - 2 and h >= 7:
                draw_text(p, (w - tw) // 2, (h - 5) // 2, txt, col)
        elif kind == "stencil" and side:  # dark stencil on light surfaces
            txt = arg
            col = np.array([0.08, 0.08, 0.06], np.float32)
            sc = 2 if (text_width(txt, 2) <= w - 4 and h >= 14) else 1
            tw = text_width(txt, sc)
            if tw <= w - 2 and h >= 6:
                draw_text(p, (w - tw) // 2, (h - 5 * sc) // 2, txt, col, sc)
        elif kind == "dot":
            cx, cy = w // 2, h // 2
            p[max(0, cy - 1):cy + 1, max(0, cx - 1):cx + 1] = np.array([1.0, 0.15, 0.1])
        elif kind == "edgehl":
            p[0, :] = np.clip(p[0, :] * 1.5 + 0.06, 0, 1)
        elif kind == "dirty":
            noise = np.random.default_rng(w * 31 + h).random((h, w, 1)).astype(np.float32)
            p *= 0.85 + noise * 0.25
        elif kind == "panel" and side:
            # recessed panel outline
            if w > 6 and h > 6:
                p[2, 2:w - 2] *= 0.6
                p[h - 3, 2:w - 2] *= 1.25
                p[2:h - 2, 2] *= 0.6
                p[2:h - 2, w - 3] *= 1.25
        elif kind == "star":
            from PIL import ImageDraw
            R = min(w, h) * 0.45
            cx, cy = w / 2, h / 2
            pts = []
            for i in range(10):
                rr = R if i % 2 == 0 else R * 0.4
                ang = -math.pi / 2 + i * math.pi / 5
                pts.append((cx + math.cos(ang) * rr, cy + math.sin(ang) * rr))
            mask = Image.new("L", (w, h), 0)
            ImageDraw.Draw(mask).polygon(pts, fill=255)
            m = np.asarray(mask) > 127
            col = np.array([0.88, 0.88, 0.84], np.float32) if arg != "red" else np.array([0.75, 0.12, 0.1], np.float32)
            p[m] = col
            self._apatch[m] = 1.0
        elif kind == "camo":
            # darker irregular blotches; on tinted paint they read as a two-tone camouflage of the team colour
            rng = np.random.default_rng(int(e["from"][0] * 131 + e["from"][1] * 17 + e["from"][2] * 7 + w * 3 + h) % (2 ** 31))
            yy, xx = np.mgrid[0:h, 0:w]
            m = np.zeros((h, w), bool)
            for _ in range(max(1, (w * h) // 260)):
                cx, cy = rng.uniform(0, w), rng.uniform(0, h)
                rx, ry = rng.uniform(3, 9), rng.uniform(2, 6)
                m |= ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 < 1.0
            p[m] *= 0.62
        elif kind == "grime":
            # dust and soot collecting towards the bottom of side faces
            if side or f in ("east", "west"):
                g = np.linspace(0.0, 1.0, h)[:, None, None] ** 2
                p *= 1.0 - g * 0.28
        elif kind == "stripe" and side:
            col = {"red": [0.7, 0.1, 0.08], "green": [0.2, 0.55, 0.2], "white": [0.85, 0.85, 0.8], "yellow": [0.85, 0.7, 0.1]}.get(arg, [0.85, 0.85, 0.8])
            p[int(h * 0.4):int(h * 0.6), :] = np.array(col, np.float32)
            self._apatch[int(h * 0.4):int(h * 0.6), :] = 1.0


# ----------------------------------------------------------------------------- preview
FACE_VERTS = {  # (x,y,z) selectors: 0=min 1=max, MC FaceInfo order; uv: v0=(u1,v1) v1=(u1,v2) v2=(u2,v2) v3=(u2,v1)
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}
FACE_NORMAL = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}


def _rot_matrix(axis, ang):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def load_json_model(path, tex_lookup):
    """Read a MC json model (our own output) -> list of quads (pts4x3, uv4x2, texture array)"""
    with open(path) as fh:
        m = json.load(fh)
    quads = []
    for e in m.get("elements", []):
        f0, t0 = np.array(e["from"], float), np.array(e["to"], float)
        R = None
        if "rotation" in e:
            r = e["rotation"]
            R = (_rot_matrix(r["axis"], r["angle"]), np.array(r["origin"], float))
        for f, fd in e["faces"].items():
            pts = []
            for sel in FACE_VERTS[f]:
                p = np.array([t0[i] if sel[i] else f0[i] for i in range(3)])
                if R is not None:
                    p = R[0] @ (p - R[1]) + R[1]
                pts.append(p)
            u1, v1, u2, v2 = fd["uv"]
            uv = np.array([[u1, v1], [u1, v2], [u2, v2], [u2, v1]]) / 16.0
            n = np.array(FACE_NORMAL[f], float)
            if R is not None:
                n = R[0] @ n
            tex = tex_lookup(m["textures"][fd["texture"].lstrip("#")] if fd["texture"].startswith("#") else fd["texture"])
            quads.append((np.array(pts), uv, n, tex))
    return quads


def render_quads(quads, yaw=30, pitch=20, size=(640, 360), scale=None, center=None, bg=(52, 58, 66), light=True, ss=2):
    """Orthographic render. yaw/pitch degrees: camera orbit. Returns PIL image."""
    W, H = size[0] * ss, size[1] * ss
    ry = _rot_matrix("y", yaw)
    rx = _rot_matrix("x", pitch)
    M = rx @ ry
    allp = np.concatenate([q[0] for q in quads]) if quads else np.zeros((1, 3))
    if center is None:
        center = (allp.min(0) + allp.max(0)) / 2
    tp = (allp - center) @ M.T
    if scale is None:
        ext = max(np.ptp(tp[:, 0]) / W, np.ptp(tp[:, 1]) / H) if len(tp) else 1
        scale = 0.85 / max(ext, 1e-6)
    else:
        scale *= ss
    img = np.zeros((H, W, 3), np.float32)
    img[:] = np.array(bg) / 255.0
    zbuf = np.full((H, W), -1e9, np.float32)
    ldir = np.array([0.35, 0.9, 0.55])
    ldir /= np.linalg.norm(ldir)
    for pts, uv, n, tex in quads:
        P = (pts - center) @ M.T
        nn = M @ n
        if nn[2] <= 0:  # backface (camera looks along -z towards +z? we look from +z)
            continue
        sx = P[:, 0] * scale + W / 2
        sy = -P[:, 1] * scale + H / 2
        sz = P[:, 2]
        shade = 0.55 + 0.45 * max(0.0, float(np.dot(n, ldir))) if light else 1.0
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], sz[list(tri)]
            uvs = uv[list(tri)]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-9:
                continue
            l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            l2 = 1 - l0 - l1
            m = (l0 >= -1e-4) & (l1 >= -1e-4) & (l2 >= -1e-4)
            if not m.any():
                continue
            z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            m &= z > sub
            if not m.any():
                continue
            u = l0 * uvs[0, 0] + l1 * uvs[1, 0] + l2 * uvs[2, 0]
            v = l0 * uvs[0, 1] + l1 * uvs[1, 1] + l2 * uvs[2, 1]
            tx = np.clip((u * tw).astype(int), 0, tw - 1)
            ty = np.clip((v * th).astype(int), 0, th - 1)
            col = tex[ty, tx]
            a = col[..., 3] > 0.5
            m &= a
            sub[m] = z[m]
            img[y0:y1 + 1, x0:x1 + 1][m] = col[..., :3][m] * shade
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    if ss > 1:
        im = im.resize(size, Image.LANCZOS)
    return im


def tex_loader(root):
    cache = {}

    def look(ref):
        if ref not in cache:
            ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
            p = os.path.join(root, "assets", ns, "textures", path + ".png")
            cache[ref] = np.asarray(Image.open(p).convert("RGBA"), np.float32) / 255.0
        return cache[ref]
    return look


def contact_sheet(images, cols=3, label=None):
    w, h = images[0].size
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, h * rows), (30, 30, 30))
    for i, im in enumerate(images):
        sheet.paste(im, ((i % cols) * w, (i // cols) * h))
    return sheet
