"""Shared Minecraft-style texture painters (world-space, so patterns continue across faces)."""
import math
import numpy as np
from bb import fill, noise, rgba, seg_dist, inside, NOTINT


def flat(c, amp=3.0, a=255, scale=48.0):
    def f(face, P, n):
        out = fill(P, c, a)
        out[..., :3] += noise(P, amp, scale)[..., None]
        return out
    return f


def plane_uv(P, o, ex, ey):
    d = P - np.asarray(o, float)
    return np.stack([d @ np.asarray(ex, float), d @ np.asarray(ey, float)], -1)


def face_uv(face, P):
    """2D coords of texels in the face plane (metres)."""
    o, eu, ev, s0, t0 = face.basis
    return plane_uv(P, o, eu, ev)


def glass(face, P, n):
    out = fill(P, (214, 226, 232))
    q = P @ np.array([1.0, 0.8, 0.6])
    streak = (np.mod(q * 3.0, 1.0) < 0.12) | (np.mod(q * 3.0 + 0.35, 1.0) < 0.05)
    out[streak, :3] = (240, 246, 250)
    return out


def chrome(face, P, n):
    out = fill(P, (196, 200, 206))
    t = np.mod(P[..., 1] * 18 + P[..., 0] * 3, 1.0)
    out[..., :3] += (np.where(t < 0.5, 30, -20))[..., None]
    return out


def carbon(face, P, n):
    q = np.floor(P * 64).astype(int)
    a = (q[..., 0] + q[..., 2] + q[..., 1]) // 2 % 2
    out = fill(P, (34, 35, 38))
    out[a == 1, :3] = (54, 56, 60)
    return out


def carbon_untinted(face, P, n):
    out = carbon(face, P, n)
    out[..., 3] = NOTINT
    return out


def honeycomb(P, uv, cell=0.022):
    """Mesh grille pattern on 2D coords: dark holes in a slightly lighter web."""
    u, v = uv[..., 0] / cell, uv[..., 1] / (cell * 0.866)
    row = np.floor(v).astype(int)
    uu = u + (row % 2) * 0.5
    fu, fv = np.mod(uu, 1.0) - 0.5, np.mod(v, 1.0) - 0.5
    hole = (np.abs(fu) < 0.32) & (np.abs(fv) < 0.3)
    return hole


def grille(face, P, n):
    uv = face_uv(face, P)
    out = fill(P, (52, 54, 58))
    out[honeycomb(P, uv), :3] = (10, 10, 12)
    return out


def leather(face, P, n):
    out = fill(P, (40, 40, 43))
    out[..., :3] += noise(P, 5, 64)[..., None]
    return out


def fabric(face, P, n):
    q = np.floor(P * 96).astype(int)
    out = fill(P, (58, 58, 63))
    out[(q[..., 0] + q[..., 1] + q[..., 2]) % 2 == 0, :3] = (50, 50, 55)
    return out


def tyre(face, P, n):
    # wheel-local frame: axis X; radius in the YZ plane
    r = np.linalg.norm(P[..., 1:], axis=-1)
    out = fill(P, (36, 36, 38))
    out[(np.abs(r - 0.28) < 0.004), :3] = (52, 52, 55)
    out[(np.abs(r - 0.246) < 0.003), :3] = (46, 46, 49)
    return out


def tread(face, P, n):
    ang = np.arctan2(P[..., 2], P[..., 1])
    a = np.mod(ang * 40 / (2 * math.pi), 1.0)
    out = fill(P, (40, 40, 42))
    x = P[..., 0]
    groove = (np.abs(np.abs(x) - 0.035) < 0.006) | ((a < 0.2) & (np.abs(x) > 0.045))
    out[groove, :3] = (22, 22, 24)
    return out


def rotor(face, P, n):
    r = np.linalg.norm(P[..., 1:], axis=-1)
    out = fill(P, (126, 128, 132))
    out[..., :3] += noise(P, 6, 200)[..., None]
    out[r < 0.085, :3] = (70, 72, 76)
    ang = np.arctan2(P[..., 2], P[..., 1])
    slot = (np.abs(np.mod(ang * 6 / math.pi + r * 30, 1.0) - 0.5) < 0.06) & (r > 0.1) & (r < 0.155)
    out[slot, :3] = (96, 98, 102)
    return out


def amber(face, P, n):
    out = fill(P, (255, 150, 30))
    uv = face_uv(face, P)
    out[np.mod(uv[..., 0] * 60, 1) < 0.25, :3] = (255, 182, 70)
    return out


def clear_lens(face, P, n):
    out = fill(P, (232, 238, 242))
    uv = face_uv(face, P)
    out[np.mod(uv[..., 0] * 40 + uv[..., 1] * 20, 1) < 0.15, :3] = (250, 252, 255)
    return out


def red_lens(base=(178, 14, 24)):
    def f(face, P, n):
        out = fill(P, base)
        uv = face_uv(face, P)
        q = np.floor(uv * 90).astype(int)
        out[(q[..., 0] + q[..., 1]) % 2 == 0, :3] = np.array(base) * 0.86
        return out
    return f


# ---------------------------------------------------------------------- 5x7 pixel font (only what badges/plates need)
FONT = {
    'S': ['01111', '10000', '10000', '01110', '00001', '00001', '11110'], 'U': ['10001', '10001', '10001', '10001', '10001', '10001', '01110'],
    'P': ['11110', '10001', '10001', '11110', '10000', '10000', '10000'], 'R': ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
    'A': ['01110', '10001', '10001', '11111', '10001', '10001', '10001'], 'T': ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
    'O': ['01110', '10001', '10001', '10001', '10001', '10001', '01110'], 'B': ['11110', '10001', '10001', '11110', '10001', '10001', '11110'],
    '0': ['01110', '10011', '10101', '10101', '11001', '10001', '01110'], '1': ['00100', '01100', '00100', '00100', '00100', '00100', '01110'],
    '2': ['01110', '10001', '00001', '00110', '01000', '10000', '11111'], '3': ['11110', '00001', '00001', '01110', '00001', '00001', '11110'],
    '4': ['00010', '00110', '01010', '10010', '11111', '00010', '00010'], '5': ['11111', '10000', '11110', '00001', '00001', '10001', '01110'],
    '6': ['00110', '01000', '10000', '11110', '10001', '10001', '01110'], '7': ['11111', '00001', '00010', '00100', '01000', '01000', '01000'],
    '8': ['01110', '10001', '10001', '01110', '10001', '10001', '01110'], '9': ['01110', '10001', '10001', '01111', '00001', '00010', '01100'],
    'K': ['10001', '10010', '10100', '11000', '10100', '10010', '10001'], 'M': ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
    'I': ['01110', '00100', '00100', '00100', '00100', '00100', '01110'], 'N': ['10001', '11001', '10101', '10011', '10001', '10001', '10001'],
    'E': ['11111', '10000', '10000', '11110', '10000', '10000', '11111'], 'D': ['11110', '10001', '10001', '10001', '10001', '10001', '11110'],
    'G': ['01111', '10000', '10000', '10111', '10001', '10001', '01111'], 'H': ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
    'J': ['00111', '00010', '00010', '00010', '00010', '10010', '01100'], '-': ['00000', '00000', '00000', '11111', '00000', '00000', '00000'],
    'C': ['01111', '10000', '10000', '10000', '10000', '10000', '01111'], 'L': ['10000', '10000', '10000', '10000', '10000', '10000', '11111'],
    'F': ['11111', '10000', '10000', '11110', '10000', '10000', '10000'], 'V': ['10001', '10001', '10001', '10001', '10001', '01010', '00100'],
    'W': ['10001', '10001', '10001', '10101', '10101', '11011', '10001'], 'X': ['10001', '10001', '01010', '00100', '01010', '10001', '10001'],
    'Y': ['10001', '10001', '01010', '00100', '00100', '00100', '00100'], 'Z': ['11111', '00001', '00010', '00100', '01000', '10000', '11111'],
    'Q': ['01110', '10001', '10001', '10001', '10101', '10010', '01101'], '.': ['00000'] * 6 + ['00100'],
    ' ': ['00000'] * 7,
}


def text_mask(uv, text, x0, y0, ch_h):
    """Mask of pixel-font text: uv in metres (x right, y DOWN), top-left (x0, y0), char height ch_h."""
    px = ch_h / 7.0
    m = np.zeros(uv.shape[:-1], bool)
    for i, c in enumerate(text):
        g = FONT.get(c.upper(), FONT[' '])
        cx = x0 + i * px * 6
        gx = np.floor((uv[..., 0] - cx) / px).astype(int)
        gy = np.floor((uv[..., 1] - y0) / px).astype(int)
        ok = (gx >= 0) & (gx < 5) & (gy >= 0) & (gy < 7)
        for yy in range(7):
            for xx in range(5):
                if g[yy][xx] == '1':
                    m |= ok & (gx == xx) & (gy == yy)
    return m


COMMON = {
    'default': flat((150, 150, 150)),
    'under': flat((26, 26, 28), 2),
    'trim': flat((30, 30, 33), 3),
    'trim_gloss': flat((18, 18, 20), 2),
    'rubber': flat((26, 26, 27), 2),
    'glass': glass,
    'chrome': chrome,
    'carbon': carbon,
    'grille': grille,
    'leather': leather,
    'fabric': fabric,
    'dash': flat((44, 44, 47), 3, scale=64),
    'dash2': flat((60, 60, 64), 3, scale=64),
    'carpet': flat((32, 32, 34), 4, scale=96),
    'alu': flat((168, 172, 178), 6, scale=96),
    'mirror': flat((176, 194, 206), 2),
    'tyre': tyre,
    'tread': tread,
    'rotor': rotor,
    'amber': amber,
    'clear_lens': clear_lens,
    'red_lens': red_lens(),
    'smoke_red': red_lens((70, 12, 18)),
    'headliner': flat((150, 148, 144), 3, scale=96),
    'lamp_house': flat((70, 74, 80), 5),
    'bright': flat((255, 252, 240), 1),
    'white_lens': flat((236, 238, 240), 2),
    'brake_red': flat((205, 18, 30), 3),
}
