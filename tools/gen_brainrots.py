"""Brainrot Heist models: blocky voxel brainrots (assets/eggsteal/models/brainrot/<id>.obj) and the bot avatars.
Run from the project root:  python3 tools/gen_brainrots.py
Coordinates are voxels (12 per block): x left(+)/right(-), y up, z front (+z is where the face looks)."""
import math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from voxlib import Vox

OUT = 'src/main/resources/assets/eggsteal/models/brainrot/'
MODELS = {}


def model(fn):
    MODELS[fn.__name__] = fn
    return fn


# ------------------------------------------------------------------ shared body parts
def legs(m, sep, h, slot, shoe=None, w=2, d=2, shoe_len=4, z=0):
    """Two legs from the ground up to height h (pivot at the hip), with optional shoes."""
    for side, part in ((1, 'legL'), (-1, 'legR')):
        x0 = sep if side > 0 else -sep - w
        m.box(slot, x0, x0 + w - 1, 0, h - 1, z - d // 2, z + d - d // 2 - 1, part)
        if shoe:
            m.box(shoe, x0, x0 + w - 1, 0, 1, z - d // 2, z - d // 2 + shoe_len - 1, part)
        m.pivot(part, x0 + w / 2, h, z)


def arms(m, x, y, length, slot, hand=None, w=2, z=0, out=0):
    """Two arms hanging from shoulders at height y, starting at |x| (the outer side of the body); `out` spreads them."""
    for side, part in ((1, 'armL'), (-1, 'armR')):
        for k in range(length):
            xx = x + (k * out) // max(1, length) if side > 0 else -1 - x - (k * out) // max(1, length)
            x0 = xx if side > 0 else xx - w + 1
            m.box(slot, x0, x0 + w - 1, y - k - 1, y - k - 1, z - w // 2, z + w - w // 2 - 1, part)
        if hand:
            xx = x + out if side > 0 else -1 - x - out
            x0 = xx if side > 0 else xx - w + 1
            m.box(hand, x0, x0 + w - 1, y - length - 2, y - length - 1, z - w // 2, z + w - w // 2 - 1, part)
        m.pivot(part, (x + w / 2) * side, y, z)


# ------------------------------------------------------------------ Common
@model
def pizzolo():
    m = Vox('pizzolo', {'crust': 0xd89a4a, 'cheese': 0xffd23a, 'sauce': 0xe0502a, 'pep': 0xb8231e, 'leg': 0xf2c27a, 'shoe': 0xd02a2a,
                        'k_white': 0xffffff, 'k_eye': 0x1a1020, 'k_dark': 0x5a1a10})
    for y in range(5, 17):
        hw = 1 + (y - 5) * 0.6
        for x in range(-int(hw) - 1, int(hw) + 1):
            if abs(x + 0.5) <= hw:
                m.box('cheese', x, x, y, y, -1, 1)
    m.box('sauce', -8, 7, 14, 14, -1, 1)
    m.box('crust', -8, 7, 15, 17, -2, 2)
    for (px, py) in [(1, 6), (-4, 10), (5, 10)]:
        m.front('pep', [(x, y) for x in range(px - 1, px + 1) for y in range(py - 1, py + 1)], out=1)
    m.eyes(11, 2, size=2)
    m.smile(8, 2)
    legs(m, 1, 6, 'leg', 'shoe', w=1, d=1, shoe_len=3)
    arms(m, 5, 12, 5, 'leg', w=1, out=2)
    return m


@model
def tazzolino():
    m = Vox('tazzolino', {'cup': 0xf4f4f0, 'band': 0x3a7ad8, 'coffee': 0x5a3218, 'foam': 0xe8c89a, 'saucer': 0xe8e8e0, 'leg': 0xf4f4f0,
                          'shoe': 0x2a2a2a, 'k_white': 0xffffff, 'k_eye': 0x1a1020, 'k_dark': 0x3a1a10, 'k_steam': 0xe8eef8})
    m.cyl('saucer', 0, 0, 7, 3, 3)
    m.cyl('cup', 0, 0, 5.5, 4, 13)
    m.cyl('band', 0, 0, 5.6, 6, 7)
    m.cyl('coffee', 0, 0, 4.5, 13, 13)
    m.cyl('foam', 0, 0, 2.5, 13, 13)
    for y in range(7, 12):
        m.box('cup', 6, 7, y, y, 0, 0) if y in (7, 11) else m.box('cup', 7, 8, y, y, 0, 0)
    m.eyes(10, 1, size=2)
    m.smile(8, 1)
    for k in range(3):
        m.set(-1 + k, 15 + k, 0, 'k_steam')
        m.set(1 - k, 17 + k, 0, 'k_steam')
    legs(m, 1, 3, 'leg', 'shoe', w=2, d=2, shoe_len=3)
    return m


@model
def bananito():
    m = Vox('bananito', {'peel': 0xffe23a, 'tip': 0x5a3a1a, 'spot': 0xc8a020, 'tutu': 0xff7ab8, 'leg': 0xffe8a0, 'shoe': 0xff4f9a,
                         'k_white': 0xffffff, 'k_eye': 0x1a1020, 'k_dark': 0x5a2a10})
    pts = [(0, 8, -1), (0.5, 12, 0), (0.5, 16, 0.5), (-0.5, 20, 0)]
    m.tube('peel', pts, 3.2)
    m.ell('tip', -0.5, 22.5, -0.5, 1.2, 1.5, 1.2)
    m.paint('spot', lambda x, y, z: (x * 3 + y * 5 + z * 7) % 11 == 0, src='peel')
    m.cyl('tutu', 0, 0, 6.5, 8, 9, where=lambda x, y, z: (x * x + z * z) > 4)
    m.cyl('tutu', 0, 0, 5.5, 10, 10, where=lambda x, y, z: (x * x + z * z) > 4)
    m.eyes(16, 1, size=2)
    m.smile(13, 1)
    legs(m, 1, 8, 'leg', 'shoe', w=1, d=1, shoe_len=2)
    arms(m, 3, 16, 5, 'peel', w=1, out=3)
    return m


@model
def patatino():
    m = Vox('patatino', {'potato': 0xc8955a, 'spot': 0x8a5a2a, 'string': 0xf0f0f0, 'balloon': 0xe8303a, 'shine': 0xff9a9a, 'leg': 0xc8955a,
                         'shoe': 0x3a5ad8, 'k_white': 0xffffff, 'k_eye': 0x1a1020, 'k_dark': 0x4a2a10})
    m.ell('potato', 0, 9, 0, 5.5, 6.5, 4.5)
    m.paint('spot', lambda x, y, z: (x * 7 + y * 3 + z * 5) % 13 == 0, src='potato')
    m.eyes(11, 2, size=2)
    m.smile(7, 2)
    m.box('string', 0, 0, 15, 19, 0, 0)
    m.ell('balloon', 0.5, 23, 0.5, 4, 4.5, 4)
    m.ell('shine', 2.5, 25, 3, 0.8, 1, 0.8)
    m.set(0, 18, 0, 'balloon')
    legs(m, 1, 4, 'leg', 'shoe', w=2, d=2, shoe_len=3)
    arms(m, 5, 10, 4, 'leg', w=1, out=2)
    return m


@model
def pinguino():
    m = Vox('pinguino', {'bun': 0xd8963a, 'bun2': 0xf0c070, 'seed': 0xfff4d0, 'lettuce': 0x5ac83a, 'cheese': 0xffc828, 'body': 0x1e2230,
                         'belly': 0xf4f4f4, 'beak': 0xff9a1a, 'k_white': 0xffffff, 'k_eye': 0x1a1020, 'k_dark': 0x1a1020})
    m.cyl('bun', 0, 0, 6.5, 1, 3)
    m.cyl('lettuce', 0, 0, 7, 4, 4)
    m.ell('body', 0, 9.5, 0, 4.5, 5.5, 4)
    m.ell('belly', 0, 9, 1.5, 3.2, 4.5, 3)
    m.cyl('cheese', 0, 0, 6.5, 14, 14, where=lambda x, y, z: (x + z) % 3 != 0)
    m.ell('bun2', 0, 15, 0, 6.5, 3.5, 6.5, where=lambda x, y, z: y >= 15)
    m.paint('seed', lambda x, y, z: y >= 17 and (x * 5 + z * 3) % 7 == 0, src='bun2')
    m.eyes(11, 1, size=2)
    m.front('beak', [(-1, 9), (0, 9)], out=2)
    m.box('beak', -3, -1, 0, 0, 0, 2, 'legR')
    m.box('beak', 0, 2, 0, 0, 0, 2, 'legL')
    m.pivot('legL', 1, 4, 0); m.pivot('legR', -2, 4, 0)
    arms(m, 4, 11, 4, 'body', w=1, out=1)
    return m


# ------------------------------------------------------------------ Rare
@model
def squalo():
    m = Vox('squalo', {'shark': 0x5a7a9a, 'belly': 0xeef2f6, 'fin': 0x4a6a88, 'leg': 0x5a7a9a, 'boot': 0x7a4a22, 'sole': 0x3a2410,
                       'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x401018, 'k_gum': 0xe86a7a})
    m.ell('shark', 0, 15, 0, 5, 8, 4.5)
    m.ell('belly', 0, 13.5, 2, 3.5, 6, 3, where=lambda x, y, z: z >= 1)
    m.box('fin', -1, 0, 22, 25, -2, 0)
    m.box('fin', -1, 0, 24, 26, -3, -2)
    for k in range(4): m.box('fin', -1, 0, 6 + k, 7 + k, -5 - k, -4 - k)
    m.eyes(18, 2, size=2)
    m.front('k_gum', [(x, y) for x in range(-3, 3) for y in (12, 13, 14)], depth=1)
    m.front('k_white', [(x, 14) for x in range(-3, 3)] + [(x, 12) for x in range(-3, 3) if x % 2 == 0])
    legs(m, 1, 8, 'leg', None, w=3, d=3)
    for side, part in ((1, 'legL'), (-1, 'legR')):
        x0 = 1 if side > 0 else -4
        m.box('boot', x0 - (0 if side > 0 else 1), x0 + 3 if side > 0 else x0 + 2, 0, 4, -2, 3, part)
        m.box('sole', x0 - (0 if side > 0 else 1), x0 + 3 if side > 0 else x0 + 2, 0, 0, -2, 4, part)
    arms(m, 5, 15, 4, 'fin', w=1, out=3)
    return m


@model
def gattino():
    m = Vox('gattino', {'cone': 0xd8963a, 'cone2': 0xa86a20, 'cream': 0xffa8c8, 'cream2': 0xffd0e0, 'cat': 0xf6f0e8, 'ear': 0xff9ab8,
                        'k_white': 0xffffff, 'k_eye': 0x2a8a3a, 'k_dark': 0x3a2a2a, 'k_nose': 0xff6a90})
    m.cone('cone', 0, 0, 0.5, 4.5, 0, 10)
    m.paint('cone2', lambda x, y, z: y <= 10 and (x + y + z) % 3 == 0, src='cone')
    m.ell('cream', 0, 12, 0, 5.5, 3, 5.5)
    m.paint('cream2', lambda x, y, z: y >= 13 and (x * 3 + z) % 4 == 0, src='cream')
    m.ell('cat', 0, 18, 0, 5, 4.5, 4.5)
    for side in (1, -1):
        for k in range(3): m.box('cat', 2 + k if side > 0 else -3 - k, 4 if side > 0 else -5, 21 + k, 21 + k, -1, 0, sym=False)
    m.box('cat', 2, 4, 21, 23, -1, 0); m.box('cat', -5, -3, 21, 23, -1, 0)
    m.box('ear', 3, 3, 22, 22, 0, 0); m.box('ear', -4, -4, 22, 22, 0, 0)
    m.eyes(18, 2, size=2, pupil='k_eye')
    m.front('k_nose', [(-1, 16), (0, 16)], out=1)
    m.front('k_dark', [(-3, 15), (2, 15)])
    for side in (1, -1):
        for k in range(3): m.set((6 + k) if side > 0 else (-7 - k), 16 + (k % 2), 3, 'k_dark')
    arms(m, 5, 12, 3, 'cat', w=1, out=2)
    m.floats = True
    return m


@model
def tostino():
    m = Vox('tostino', {'metal': 0xc8ccd4, 'trim': 0x8a8e98, 'toast': 0xe0a860, 'crust': 0xa8682a, 'lever': 0x2a2a2a, 'leg': 0x8a8e98,
                        'shoe': 0xd02a2a, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x101018, 'g_light': 0xff5a20})
    m.box('metal', -6, 5, 4, 13, -4, 3)
    m.box('trim', -6, 5, 4, 4, -4, 3)
    m.box('trim', -6, 5, 13, 13, -4, 3)
    for x0 in (-4, 1):
        m.box('toast', x0, x0 + 2, 13, 17, -2, 1)
        m.box('crust', x0, x0 + 2, 17, 17, -2, 1)
        m.box('k_dark', x0, x0 + 2, 13, 13, -2, 1)
    m.box('lever', 6, 6, 9, 10, -1, 0)
    m.box('g_light', 6, 6, 6, 6, 1, 1)
    m.eyes(9, 2, size=2)
    m.smile(6, 2)
    legs(m, 2, 4, 'leg', 'shoe', w=2, d=2, shoe_len=3)
    arms(m, 6, 11, 4, 'trim', w=1, out=2)
    return m


@model
def cactusso():
    m = Vox('cactusso', {'cactus': 0x3aa84a, 'cactus2': 0x2a7a36, 'spine': 0xf0f0c0, 'hat': 0xf0c03a, 'hatband': 0xd02a2a, 'pot': 0xc0603a,
                         'mar1': 0xe0302a, 'mar2': 0xffd23a, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x3a1010})
    m.cyl('pot', 0, 0, 4.5, 0, 4)
    m.cyl('cactus', 0, 0, 3.5, 5, 21)
    m.paint('cactus2', lambda x, y, z: (x + z) % 3 == 0, src='cactus')
    m.paint('spine', lambda x, y, z: y % 4 == 0 and (x * x + z * z) > 6 and (x + z) % 2 == 0, src=('cactus', 'cactus2'))
    m.eyes(16, 1, size=2)
    m.smile(13, 2)
    for side, part in ((1, 'armL'), (-1, 'armR')):
        xa = 4 if side > 0 else -5
        m.box('cactus', xa, xa + side * 2, 11, 12, -1, 0, part) if side > 0 else m.box('cactus', xa - 2, xa, 11, 12, -1, 0, part)
        xb = 6 if side > 0 else -7
        m.box('cactus', xb, xb + (1 if side > 0 else 0), 11, 16, -1, 0, part) if side > 0 else m.box('cactus', xb - 1, xb, 11, 16, -1, 0, part)
        m.ell('mar1' if side > 0 else 'mar2', xb + 0.5 * side, 18, 0, 1.5, 2, 1.5, part)
        m.pivot(part, 3.5 * side, 12, 0)
    m.cyl('hat', 0, 0, 8.5, 22, 22)
    m.cyl('hat', 0, 0, 3.5, 23, 26)
    m.cyl('hatband', 0, 0, 3.6, 23, 23)
    m.paint('hatband', lambda x, y, z: y == 22 and x * x + z * z > 56, src='hat')
    return m


@model
def bruco():
    m = Vox('bruco', {'bun': 0xd8963a, 'patty': 0x6a3a1e, 'lettuce': 0x6ad04a, 'cheese': 0xffc828, 'head': 0x8ad84a, 'ant': 0x2a5a1a,
                      'leg': 0x2a5a1a, 'seed': 0xfff4d0, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x1a3a10})
    for i, zc in enumerate([-9, -4, 1]):
        m.cyl('bun', 0, zc + 0.5, 3.5, 2, 3, rz=2.2)
        m.cyl('patty', 0, zc + 0.5, 3.8, 4, 5, rz=2.4)
        m.cyl('cheese', 0, zc + 0.5, 4, 6, 6, rz=2.5)
        m.cyl('lettuce', 0, zc + 0.5, 4.2, 7, 7, rz=2.6)
        m.ell('bun', 0, 8, zc + 0.5, 3.6, 3, 2.3, where=lambda x, y, z: y >= 8)
        m.box('leg', 2, 2, 0, 1, zc, zc, 'legL' if i % 2 == 0 else 'legR')
        m.box('leg', -3, -3, 0, 1, zc, zc, 'legR' if i % 2 == 0 else 'legL')
    m.paint('seed', lambda x, y, z: y >= 10 and (x * 5 + z * 3) % 6 == 0, src='bun')
    m.pivot('legL', 0, 2, -4); m.pivot('legR', 0, 2, -4)
    m.ell('head', 0, 9, 6.5, 3.5, 3.8, 3.2)
    m.eyes(10, 1, size=2)
    m.smile(7, 1)
    m.box('ant', 1, 1, 13, 15, 6, 6); m.box('ant', -2, -2, 13, 15, 6, 6)
    m.box('ant', 2, 2, 16, 16, 6, 6); m.box('ant', -3, -3, 16, 16, 6, 6)
    return m


# ------------------------------------------------------------------ Epic
@model
def ananasso():
    m = Vox('ananasso', {'fruit': 0xf2c230, 'fruit2': 0xb8861a, 'leaf': 0x3ab84a, 'leaf2': 0x2a8a36, 'suit': 0xf0f2f6, 'suit2': 0xa8b0c0,
                         'visor': 0x3ac8ff, 'pack': 0xd8dce4, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x3a2410, 'g_lamp': 0xff3a3a})
    m.ell('fruit', 0, 13, 0, 5, 6.5, 4.5)
    m.paint('fruit2', lambda x, y, z: (x + y) % 3 == 0 or (x - y) % 3 == 0, src='fruit')
    for k, (dx, dz) in enumerate([(0, 0), (2, 1), (-2, -1), (1, -2), (-1, 2)]):
        m.box('leaf' if k % 2 == 0 else 'leaf2', dx, dx, 19, 23 - abs(dx), dz, dz)
        m.box('leaf', dx * 2 // 2 + (1 if dx > 0 else -1 if dx < 0 else 0), dx * 2 // 2 + (1 if dx > 0 else -1 if dx < 0 else 0), 20, 21, dz, dz)
    m.front('visor', [(x, y) for x in range(-3, 3) for y in range(13, 17)], out=1)
    m.front('k_eye', [(-2, 15), (1, 15)], out=1)
    m.front('k_white', [(-1, 16)], out=1)
    m.box('pack', -3, 2, 9, 15, -6, -5)
    m.box('g_lamp', -1, 0, 15, 15, -7, -7)
    legs(m, 1, 7, 'suit', 'suit2', w=2, d=2, shoe_len=3)
    arms(m, 5, 14, 5, 'suit', hand='suit2', w=2, out=1)
    m.box('suit2', 0, 0, 23, 25, 0, 0)
    m.box('g_lamp', 0, 0, 26, 26, 0, 0)
    return m


@model
def rana_razzo():
    m = Vox('rana_razzo', {'rocket': 0xf2f2f6, 'red': 0xe0302a, 'window': 0x3a8ad8, 'frog': 0x5ac83a, 'frog2': 0x9ae86a, 'fin': 0xe0302a,
                           'g_fire': 0xffa020, 'g_fire2': 0xffe060, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x1a4010})
    m.cyl('rocket', 0, 0, 4, 4, 18)
    m.cone('red', 0, 0, 4, 0.5, 19, 24)
    m.cyl('red', 0, 0, 4.1, 9, 9)
    for (dx, dz) in [(4, 0), (-5, 0), (0, 4), (0, -5)]:
        m.box('fin', dx, dx, 2, 7, dz, dz)
        m.box('fin', dx + (1 if dx > 0 else -1 if dx < 0 else 0), dx + (1 if dx > 0 else -1 if dx < 0 else 0), 2, 4, dz + (1 if dz > 0 else -1 if dz < 0 else 0), dz + (1 if dz > 0 else -1 if dz < 0 else 0))
    m.cone('g_fire', 0, 0, 2.5, 0.5, 0, 3)
    m.cone('g_fire2', 0, 0, 1.5, 0.5, 0, 2)
    m.ell('frog', 0, 14, 4, 3.5, 3, 2.5)
    m.ell('frog2', 0, 13, 5, 2.5, 1.5, 2)
    m.box('frog', 1, 3, 17, 18, 3, 5); m.box('frog', -4, -2, 17, 18, 3, 5)
    m.box('k_white', 1, 2, 17, 18, 6, 6); m.box('k_white', -3, -2, 17, 18, 6, 6)
    m.box('k_eye', 2, 2, 17, 17, 6, 6); m.box('k_eye', -3, -3, 17, 17, 6, 6)
    m.smile(12, 2, out=0)
    m.box('frog', 3, 4, 13, 13, 4, 6, 'armL'); m.box('frog', -5, -4, 13, 13, 4, 6, 'armR')
    m.pivot('armL', 3, 13, 4); m.pivot('armR', -4, 13, 4)
    return m


@model
def polpo():
    m = Vox('polpo', {'octo': 0xb04ad8, 'spot': 0xe08af0, 'sucker': 0xffc0f0, 'piano': 0x1a1a1e, 'key': 0xf4f4f4, 'leg': 0x5a3a2a,
                      'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x3a1040})
    m.ell('octo', 0, 15, -1, 5.5, 5.5, 5)
    m.paint('spot', lambda x, y, z: y > 15 and (x * 3 + z * 5 + y) % 7 == 0, src='octo')
    m.eyes(15, 2, size=2)
    m.smile(12, 2)
    for k in range(8):
        a = 2 * math.pi * k / 8
        part = 'sway%d' % k
        pts = [(math.cos(a) * 3, 10, -1 + math.sin(a) * 3), (math.cos(a) * 6, 6, -1 + math.sin(a) * 6), (math.cos(a) * 8, 3, -1 + math.sin(a) * 8)]
        m.tube('octo', pts, 1.1, part)
        m.pivot(part, math.cos(a) * 3, 10, -1 + math.sin(a) * 3, phase=k * 0.8)
    m.box('piano', -7, 6, 4, 8, 5, 9)
    m.box('key', -7, 6, 8, 8, 8, 9)
    m.paint('piano', lambda x, y, z: y == 8 and z >= 8 and x % 2 == 0)
    for x in (-7, 6):
        m.box('piano', x, x, 0, 3, 6, 6)
    return m


@model
def frigorillo():
    m = Vox('frigorillo', {'fridge': 0xeef0f4, 'line': 0xa8b0bc, 'handle': 0x5a6270, 'fur': 0x3a3438, 'face': 0x7a6a62, 'magnet': 0xff5a3a,
                           'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x1a1012})
    m.box('fridge', -6, 5, 7, 20, -3, 3)
    m.box('line', -6, 5, 14, 14, 3, 3)
    m.box('handle', 3, 3, 9, 12, 4, 4); m.box('handle', 3, 3, 16, 18, 4, 4)
    m.box('magnet', -4, -3, 17, 18, 4, 4); m.box('magnet', -2, -2, 10, 11, 4, 4)
    m.ell('fur', 0, 23.5, 0, 4.5, 4, 4)
    m.ell('face', 0, 22.5, 2.5, 3, 2.5, 2)
    m.box('fur', -4, 3, 25, 26, -1, 2)
    m.eyes(23, 1, size=2)
    m.front('k_dark', [(-1, 21), (0, 21)])
    m.smile(20, 2, part=None)
    legs(m, 1, 7, 'fur', None, w=3, d=3)
    for side, part in ((1, 'armL'), (-1, 'armR')):
        x0 = 6 if side > 0 else -9
        m.box('fur', x0, x0 + 2, 4, 19, -1, 1, part)
        m.box('face', x0, x0 + 2, 2, 3, -1, 2, part)
        m.pivot(part, (x0 + 1.5), 19, 0)
    return m


# ------------------------------------------------------------------ Legendary
@model
def ciambella():
    m = Vox('ciambella', {'dough': 0xd89a50, 'frost': 0xff7ab8, 'metal': 0x9aa2b0, 'metal2': 0x6a7280, 'k_white': 0xffffff, 'k_eye': 0x101018,
                          'k_dark': 0x3a1a10, 'g_led': 0x3affe0, 's1': 0xffffff, 's2': 0x3ad8ff, 's3': 0xffe23a})
    for x in range(-9, 9):
        for y in range(8, 26):
            for z in range(-3, 3):
                r = math.hypot(x + 0.5, y + 0.5 - 17)
                if 3.2 <= r <= 8.8 and abs(z + 0.5) <= math.sqrt(max(0, 9 - (r - 6) ** 2)) * 0.9:
                    m.set(x, y, z, 'frost' if z >= 1 else 'dough')
    m.paint('s1', lambda x, y, z: z >= 1 and (x * 7 + y * 3) % 17 == 0, src='frost')
    m.paint('s2', lambda x, y, z: z >= 1 and (x * 5 + y * 11) % 19 == 0, src='frost')
    m.paint('s3', lambda x, y, z: z >= 1 and (x * 13 + y * 7) % 23 == 0, src='frost')
    m.box('k_white', 2, 3, 21, 22, 3, 3); m.box('k_white', -4, -3, 21, 22, 3, 3)
    m.box('k_eye', 2, 2, 21, 21, 3, 3); m.box('k_eye', -3, -3, 21, 21, 3, 3)
    m.box('k_dark', -2, 1, 11, 11, 3, 3)
    m.box('metal2', 0, 0, 26, 28, 0, 0); m.box('g_led', -1, 1, 29, 29, -1, 1)
    legs(m, 2, 9, 'metal', 'metal2', w=2, d=2, shoe_len=4)
    for side, part in ((1, 'armL'), (-1, 'armR')):
        x0 = 9 if side > 0 else -11
        m.box('metal', x0, x0 + 1, 17, 17, -1, 0, part)
        m.box('metal', x0 + (1 if side > 0 else 0), x0 + (1 if side > 0 else 0), 12, 17, -1, 0, part)
        m.box('metal2', x0, x0 + 1, 10, 11, -1, 0, part)
        m.pivot(part, x0 + 1, 17, 0)
    return m


@model
def mucca():
    m = Vox('mucca', {'cow': 0xf6f6f2, 'spot': 0x1e1e22, 'snout': 0xffb0c0, 'horn': 0xf0e0b0, 'missile': 0x8a929e, 'tip': 0xe0302a,
                      'fin': 0xe0302a, 'g_fire': 0xffa020, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x3a1a1a})
    m.box('missile', -3, 2, 2, 7, -12, 8)
    for k in range(5): m.box('tip', -2 + k // 2, 1 - k // 2, 3 + k // 2, 6 - k // 2, 9 + k, 9 + k)
    m.box('fin', -5, 4, 1, 3, -12, -10); m.box('fin', -1, 0, 7, 10, -12, -10)
    m.box('g_fire', -2, 1, 3, 6, -14, -13)
    m.ell('cow', 0, 12, -1, 4, 3.5, 6)
    m.paint('spot', lambda x, y, z: (x * 3 + z * 5 + y) % 6 == 0 and y > 10, src='cow')
    m.ell('cow', 0, 15, 5, 3, 3, 3)
    m.ell('snout', 0, 13.5, 7.5, 2.5, 1.5, 1.2)
    m.box('k_dark', -2, -2, 14, 14, 8, 8); m.box('k_dark', 1, 1, 14, 14, 8, 8)
    m.box('k_white', 1, 2, 16, 17, 7, 7); m.box('k_white', -3, -2, 16, 17, 7, 7)
    m.box('k_eye', 1, 1, 16, 16, 7, 7); m.box('k_eye', -2, -2, 16, 16, 7, 7)
    m.box('horn', 2, 3, 18, 19, 5, 5); m.box('horn', -4, -3, 18, 19, 5, 5)
    for side, part in ((1, 'legL'), (-1, 'legR')):
        x0 = 3 if side > 0 else -4
        m.box('cow', x0, x0, 6, 9, 2, 3, part)
        m.box('spot', x0, x0, 6, 6, 2, 3, part)
        m.pivot(part, x0 + 0.5, 9, 2)
    return m


@model
def dino_divano():
    m = Vox('dino_divano', {'couch': 0xc8302a, 'cushion': 0xe85a4a, 'wood': 0x6a3a1a, 'dino': 0x4ab84a, 'belly': 0xc8e86a, 'k_white': 0xffffff,
                            'k_eye': 0x101018, 'k_dark': 0x1a3a10, 'tooth': 0xffffff})
    m.box('couch', -9, 8, 2, 6, -4, 4)
    m.box('cushion', -8, 7, 7, 7, -3, 3)
    m.box('couch', -9, 8, 7, 13, -5, -4)
    m.box('couch', -10, -9, 2, 10, -4, 4); m.box('couch', 8, 9, 2, 10, -4, 4)
    for x in (-9, 8): m.box('wood', x, x, 0, 1, -4, -4); m.box('wood', x, x, 0, 1, 4, 4)
    m.ell('dino', 0, 16, -2, 3, 5, 3)
    m.ell('belly', 0, 15, 0, 2, 4, 1.5)
    m.ell('dino', 0, 22, 1, 3.5, 3, 4.5)
    m.box('dino', -3, 2, 19, 20, 2, 5)
    m.front('tooth', [(x, 20) for x in range(-3, 3) if x % 2 == 0])
    m.box('k_white', 1, 2, 23, 24, 4, 4); m.box('k_white', -3, -2, 23, 24, 4, 4)
    m.box('k_eye', 1, 1, 23, 23, 4, 4); m.box('k_eye', -2, -2, 23, 23, 4, 4)
    m.tube('dino', [(0, 12, -5), (0, 10, -9), (0, 9, -13)], 1.5, 'tail')
    m.pivot('tail', 0, 12, -5)
    arms(m, 3, 17, 3, 'dino', w=1, z=1, out=1)
    return m


@model
def lumacone():
    m = Vox('lumacone', {'slug': 0xc8d85a, 'slug2': 0xa8b83a, 'boiler': 0x2a2a30, 'band': 0xd8b030, 'wheel': 0xd02a2a, 'cab': 0x3a6ad8,
                         'chimney': 0x1a1a1e, 'k_smoke': 0xd8d8e0, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x3a4010})
    m.box('slug', -3, 2, 1, 3, -11, 9)
    m.ell('slug', 0, 6, 9, 3, 4, 2.5)
    m.ell('slug2', 0, 5, 10.5, 2, 2.5, 1)
    m.box('slug', 1, 1, 9, 13, 8, 8); m.box('slug', -2, -2, 9, 13, 8, 8)
    m.box('k_white', 1, 2, 13, 14, 8, 9); m.box('k_white', -3, -2, 13, 14, 8, 9)
    m.box('k_eye', 1, 1, 13, 13, 9, 9); m.box('k_eye', -2, -2, 13, 13, 9, 9)
    m.smile(4, 1)
    for z in range(-9, 5):
        for y in range(4, 13):
            for x in range(-5, 5):
                if (x + 0.5) ** 2 + (y + 0.5 - 8.5) ** 2 <= 20: m.set(x, y, z, 'band' if z % 5 == 0 else 'boiler')
    m.box('cab', -5, 4, 4, 15, -12, -9)
    m.box('k_white', -4, 3, 12, 13, -12, -12)
    m.box('chimney', -1, 0, 13, 17, 2, 3)
    for k in range(3): m.ell('k_smoke', 0.5 - k, 19 + k * 2, 2.5 - k, 1.3, 1.1, 1.3)
    for z in (-7, -2, 3):
        for y in range(0, 5):
            for zz in range(z - 2, z + 2):
                if (y + 0.5 - 2.5) ** 2 + (zz + 0.5 - z) ** 2 <= 6:
                    m.set(5, y + 1, zz, 'wheel', sym=True)
    return m


# ------------------------------------------------------------------ Mythic
@model
def dragone():
    m = Vox('dragone', {'tube': 0xf4f4f8, 'stripe': 0x2a8ad8, 'stripe2': 0xe0302a, 'cap': 0x2a6ac8, 'horn': 0xf0e0a0, 'wing': 0x3a8ae8,
                        'paste': 0xffffff, 'paste2': 0x3ad8e8, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x101830})
    m.box('tube', -4, 3, 6, 18, -2, 1)
    for y in range(6, 19):
        if y % 4 == 0: m.box('stripe', -4, 3, y, y, -2, 1)
        if y % 4 == 2: m.box('stripe2', -4, 3, y, y, -2, 1)
    m.box('tube', -3, 2, 4, 5, -1, 0)
    m.ell('cap', 0, 21.5, 0.5, 3.5, 3, 3.5)
    m.box('cap', -2, 1, 19, 20, 3, 5)
    m.box('horn', 2, 2, 24, 26, 0, 0); m.box('horn', -3, -3, 24, 26, 0, 0)
    m.box('k_white', 1, 2, 22, 23, 4, 4); m.box('k_white', -3, -2, 22, 23, 4, 4)
    m.box('k_eye', 2, 2, 22, 22, 4, 4); m.box('k_eye', -3, -3, 22, 22, 4, 4)
    for k in range(6): m.box('paste' if k % 2 == 0 else 'paste2', -1, 0, 19 - k // 2, 20 - k // 2, 6 + k, 6 + k)
    for side, part in ((1, 'wingL'), (-1, 'wingR')):
        for k in range(6):
            x = 4 + k if side > 0 else -5 - k
            m.box('wing', x, x, 12 + k // 2, 16 + k, -2, -2, part)
        m.pivot(part, 4 * side, 14, -2)
    legs(m, 1, 6, 'cap', 'cap', w=2, d=2, shoe_len=3)
    arms(m, 4, 15, 4, 'cap', w=1, out=2)
    return m


@model
def fenicottero():
    m = Vox('fenicottero', {'stone': 0xb0b4bc, 'stone2': 0x8a8e98, 'water': 0x4aa8f0, 'g_spray': 0xbfefff, 'bird': 0xff7ab0, 'bird2': 0xff4a90,
                            'beak': 0x1a1a1e, 'beak2': 0xf4f4f4, 'leg': 0xff9ac0, 'k_white': 0xffffff, 'k_eye': 0x101018})
    m.cyl('stone', 0, 0, 7.5, 0, 3)
    m.cyl('water', 0, 0, 6.5, 3, 3)
    m.paint('stone2', lambda x, y, z: y == 3 and x * x + z * z > 40, src='stone')
    m.box('g_spray', -1, 0, 4, 6, -5, -4)
    for k in range(3): m.set(-1 + k, 7 + k, -4, 'g_spray')
    m.box('leg', 0, 0, 3, 12, 0, 0, 'legL')
    m.box('leg', -2, -1, 9, 9, 0, 0, 'legL')
    m.box('leg', -2, -2, 7, 9, 0, 0, 'legL')
    m.pivot('legL', 0, 12, 0)
    m.ell('bird', 0, 15, -1, 3.5, 3, 5)
    m.paint('bird2', lambda x, y, z: z < -3 and y > 13, src='bird')
    m.tube('bird', [(0, 16, 3), (0, 20, 4), (0, 24, 2), (0, 27, 3)], 1.2, 'head')
    m.ell('bird', 0, 27.5, 3.5, 2, 2, 2.2, 'head')
    m.box('beak2', -1, 0, 26, 27, 6, 6, 'head'); m.box('beak', -1, 0, 25, 26, 7, 7, 'head')
    m.box('k_white', 1, 1, 28, 28, 4, 4, 'head'); m.box('k_white', -2, -2, 28, 28, 4, 4, 'head')
    m.box('k_eye', 1, 1, 28, 28, 5, 5, 'head'); m.box('k_eye', -2, -2, 28, 28, 5, 5, 'head')
    m.pivot('head', 0, 17, 3)
    for side, part in ((1, 'wingL'), (-1, 'wingR')):
        m.box('bird2', 3 if side > 0 else -5, 4 if side > 0 else -4, 13, 16, -4, 1, part)
        m.pivot(part, 3 * side, 16, -1)
    return m


@model
def elefrullo():
    m = Vox('elefrullo', {'motor': 0x2a2a30, 'button': 0xff3a3a, 'glass': 0xa8e0f8, 'smoothie': 0xff8ac0, 'smoothie2': 0xffc0e0, 'ele': 0x9aa0ac,
                          'ear': 0xd0a0b0, 'tusk': 0xfaf6e8, 'k_white': 0xffffff, 'k_eye': 0x101018, 'g_blade': 0xe0e8f0})
    m.box('motor', -5, 4, 0, 5, -4, 3)
    for x in (-3, 0, 3): m.box('button', x - 1, x, 2, 3, 4, 4)
    m.box('glass', -4, 3, 6, 16, -3, 2)
    m.box('smoothie', -3, 2, 6, 12, -2, 1)
    m.paint('smoothie2', lambda x, y, z: y == 12 or (x + y) % 5 == 0 and y < 12 and y > 6, src='smoothie')
    m.box('g_blade', -2, 1, 7, 7, -1, 0)
    m.ell('ele', 0, 20, 0, 4.5, 4, 4)
    for side, part in ((1, 'earL'), (-1, 'earR')):
        x0 = 4 if side > 0 else -9
        m.box('ele', x0, x0 + 4, 17, 23, -1, 0, part)
        m.box('ear', x0 + 1, x0 + 3, 18, 22, 1, 1, part)
        m.pivot(part, 4 * side, 20, 0)
    m.tube('ele', [(0, 18, 4), (0, 15, 5), (0, 13, 7)], 1.3, 'tail')
    m.pivot('tail', 0, 18, 4)
    m.box('tusk', 2, 2, 16, 17, 4, 5); m.box('tusk', -3, -3, 16, 17, 4, 5)
    m.eyes(21, 1, size=2)
    return m


# ------------------------------------------------------------------ Brainrot God
@model
def re_ravioli():
    m = Vox('re_ravioli', {'pasta': 0xf6dc8a, 'edge': 0xe8b85a, 'sauce': 0xd02a1a, 'crown': 0xffc830, 'gem': 0x3ac8ff, 'gem2': 0xff3a5a,
                           'cape': 0xa01a2a, 'cape2': 0xf4f4f4, 'scepter': 0xffc830, 'leg': 0xf6dc8a, 'shoe': 0x6a1a8a,
                           'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x5a1a10})
    m.box('pasta', -7, 6, 6, 19, -2, 2)
    m.paint('edge', lambda x, y, z: x in (-7, 6) or y in (6, 19), src='pasta')
    m.paint('pasta', lambda x, y, z: (x in (-7, 6) and y % 2 == 0) or (y in (6, 19) and x % 2 == 0), src='edge')
    m.box('pasta', -6, 5, 7, 18, 3, 3)
    m.paint('sauce', lambda x, y, z: y >= 18 and (x * 3) % 5 != 0 and z >= 2, src=('pasta', 'edge'))
    m.box('sauce', -5, -5, 16, 17, 3, 3); m.box('sauce', 2, 2, 15, 17, 3, 3)
    m.eyes(13, 2, size=2)
    m.smile(10, 2)
    m.box('crown', -5, 4, 20, 21, -2, 2)
    for x in (-5, -2, 1, 4): m.box('crown', x, x, 22, 24, -2, 2) if x % 3 == 1 else m.box('crown', x, x, 22, 23, 0, 0)
    m.box('crown', -5, 4, 22, 22, -2, -2); m.box('crown', -5, 4, 22, 22, 2, 2)
    for x in (-4, -1, 2): m.box('crown', x, x + 1, 22, 24, 2, 2)
    m.box('gem', -1, 0, 21, 21, 3, 3); m.box('gem2', -4, -4, 21, 21, 3, 3); m.box('gem2', 3, 3, 21, 21, 3, 3)
    m.box('cape', -8, 7, 4, 19, -4, -3, 'sway')
    m.box('cape2', -8, 7, 19, 20, -4, -2, 'sway')
    m.pivot('sway', 0, 20, -3)
    legs(m, 1, 6, 'leg', 'shoe', w=2, d=2, shoe_len=3)
    arms(m, 7, 16, 5, 'edge', w=1, out=2)
    m.box('scepter', 10, 10, 6, 18, 0, 0, 'armL')
    m.box('gem', 9, 11, 19, 20, -1, 1, 'armL')
    return m


@model
def tamburo():
    m = Vox('tamburo', {'drum': 0xd02a2a, 'rim': 0xffc830, 'skin': 0xf6f0e0, 'rope': 0xf4f4f4, 'cloud': 0xc8ccd8, 'cloud2': 0x8a90a0,
                        'g_bolt': 0xfff04a, 'stick': 0xd8a060, 'leg': 0x4a4a58, 'shoe': 0x1a1a1e, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x401010})
    m.cyl('drum', 0, 0, 6.5, 6, 16)
    m.cyl('rim', 0, 0, 6.7, 6, 7); m.cyl('rim', 0, 0, 6.7, 15, 16)
    m.cyl('skin', 0, 0, 6, 17, 17)
    m.paint('rope', lambda x, y, z: 8 <= y <= 14 and ((y - 8) % 6 == (x + 13) % 6) and x * x + z * z > 30, src='drum')
    m.eyes(12, 2, size=2)
    m.smile(9, 2)
    for (cx, cy, cz, r) in [(0, 24, 0, 4.5), (-4, 23, 0, 3.5), (4, 23, 0, 3.5), (-2, 26, -1, 3), (3, 26, 0, 3), (0, 22, 2, 3)]:
        m.ell('cloud', cx, cy, cz, r, r * 0.8, r)
    m.paint('cloud2', lambda x, y, z: y <= 21, src='cloud')
    for k, (x, y) in enumerate([(3, 20), (2, 19), (3, 18), (2, 17)]): m.set(x + 4, y - 1, 2, 'g_bolt'); m.set(x + 4, y - 2, 2, 'g_bolt')
    for k, (x, y) in enumerate([(-6, 20), (-7, 19), (-6, 18)]): m.set(x, y - 1, 2, 'g_bolt')
    legs(m, 1, 6, 'leg', 'shoe', w=2, d=2, shoe_len=3)
    arms(m, 7, 14, 4, 'leg', w=1, out=1)
    m.box('stick', 8, 8, 9, 9, 1, 5, 'armL'); m.box('stick', -9, -9, 9, 9, 1, 5, 'armR')
    m.box('skin', 8, 8, 9, 9, 6, 6, 'armL'); m.box('skin', -9, -9, 9, 9, 6, 6, 'armR')
    return m


@model
def cappellone():
    m = Vox('cappellone', {'hat': 0x2a2a8a, 'hat2': 0x3a3aa8, 'band': 0xffc830, 'star': 0xfff04a, 'moon': 0xf4f4c0, 'leg': 0x2a2a8a, 'shoe': 0x6a3a1a,
                           'wand': 0x3a2410, 'g_tip': 0xfff8a0, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x101030})
    m.cyl('hat2', 0, 0, 9.5, 7, 8)
    m.cone('hat', 0, 0, 6.5, 0.6, 9, 27)
    m.cyl('band', 0, 0, 6.6, 9, 10)
    for y in range(27, 31): m.set(-(y - 26), y, -(y - 27), 'hat')
    m.set(-5, 31, -4, 'star')
    m.paint('star', lambda x, y, z: y > 12 and (x * 7 + y * 5 + z * 3) % 19 == 0, src='hat')
    m.paint('moon', lambda x, y, z: 16 <= y <= 18 and x in (-3, -2) and z > 0, src='hat')
    m.eyes(13, 1, size=2)
    m.smile(11, 1)
    legs(m, 1, 7, 'leg', 'shoe', w=2, d=2, shoe_len=4)
    arms(m, 7, 9, 4, 'hat2', w=1, out=2)
    m.box('wand', 10, 10, 3, 12, 1, 1, 'armL')
    m.box('g_tip', 9, 11, 13, 14, 0, 2, 'armL')
    return m


# ------------------------------------------------------------------ Secret
@model
def scheletrino():
    m = Vox('scheletrino', {'bone': 0xf4f0e2, 'bone2': 0xd8d2c0, 'bowl': 0xf4ead0, 'bowl2': 0x3a6ad8, 'pasta': 0xffd860, 'sauce': 0xd0301a,
                            'meat': 0x7a3a1e, 'fork': 0xc0c8d0, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x1a1a1a})
    m.box('bone', -1, 0, 9, 17, -1, 0)
    for y in (11, 13, 15, 17): m.box('bone2', -5, 4, y, y, -2, 2, where=None) if False else m.box('bone2', -5, 4, y, y, -2, -2)
    for y in (11, 13, 15, 17):
        for x in range(-5, 5): m.set(x, y, 2, 'bone2')
        m.box('bone2', -5, -5, y, y, -2, 2); m.box('bone2', 4, 4, y, y, -2, 2)
    m.box('bone', -3, 2, 8, 9, -1, 0)
    m.cyl('bowl', 0, 0, 6.5, 19, 22)
    m.cyl('bowl2', 0, 0, 6.6, 21, 21)
    m.cyl('bowl', 0, 0, 3.5, 18, 18)
    m.ell('pasta', 0, 23, 0, 6, 2.5, 6)
    m.paint('sauce', lambda x, y, z: y >= 24 and (x * x + z * z) < 9, src='pasta')
    m.ell('meat', 2.5, 25, 1.5, 1.4, 1.2, 1.4); m.ell('meat', -2.5, 24.5, -1.5, 1.4, 1.2, 1.4)
    for k in range(5): m.set(-6 + k * 3, 18 - (k % 2), 6, 'pasta')
    m.box('k_dark', 1, 3, 19, 20, 6, 6); m.box('k_dark', -4, -2, 19, 20, 6, 6)
    m.box('k_eye', 2, 2, 19, 19, 7, 7); m.box('k_eye', -3, -3, 19, 19, 7, 7)
    legs(m, 1, 8, 'bone', None, w=1, d=1)
    for side, part in ((1, 'legL'), (-1, 'legR')):
        x0 = 1 if side > 0 else -2
        m.box('bone2', x0 - (0 if side > 0 else 1), x0 + (1 if side > 0 else 0), 0, 0, 0, 2, part)
    arms(m, 5, 17, 7, 'bone', w=1, out=2)
    m.box('fork', 8, 8, 9, 18, 1, 1, 'armL')
    for z in (0, 1, 2): m.box('fork', 8, 8, 18, 20, z, z, 'armL') if z != 1 else None
    return m


@model
def gabinetto():
    m = Vox('gabinetto', {'porcelain': 0xf6f8fc, 'shade': 0xc8ccd8, 'g_water': 0x9a4aff, 'g_star': 0xffffff, 'swirl': 0x4a1aa8, 'glove': 0xf4f4f4,
                          'handle': 0xc0c8d0, 'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x2a1050})
    m.box('porcelain', -5, 4, 12, 24, -7, -4)
    m.box('shade', -5, 4, 24, 25, -7, -3)
    m.box('handle', 3, 4, 21, 21, -3, -3)
    m.cyl('porcelain', 0, 0, 3.5, 0, 5)
    m.ell('porcelain', 0, 8, 1, 6.5, 3.5, 6.5, where=lambda x, y, z: y >= 6)
    m.cyl('g_water', 0, 1, 4.6, 11, 11)
    m.paint('swirl', lambda x, y, z: y == 11 and (x * 3 + z * 2) % 5 == 0 and x * x + (z - 1) ** 2 < 21, src='g_water')
    m.paint('g_star', lambda x, y, z: y == 11 and (x * 5 + z * 7) % 13 == 0 and x * x + (z - 1) ** 2 < 21, src='g_water')
    m.box('porcelain', -5, 4, 12, 13, -3, -2)
    m.box('k_white', 1, 2, 18, 20, -3, -3); m.box('k_white', -3, -2, 18, 20, -3, -3)
    m.box('k_eye', 1, 1, 18, 19, -3, -3); m.box('k_eye', -2, -2, 18, 19, -3, -3)
    m.box('k_dark', -2, 1, 15, 15, -3, -3)
    for side, part in ((1, 'armL'), (-1, 'armR')):
        x0 = 5 if side > 0 else -8
        m.box('glove', x0, x0 + 2, 15, 17, -6, -4, part)
        m.box('glove', x0 + (1 if side > 0 else 0), x0 + (2 if side > 0 else 1), 11, 14, -5, -4, part)
        m.box('glove', x0, x0 + 2, 9, 11, -6, -3, part)
        m.pivot(part, x0 + 1, 17, -5)
    return m


@model
def teschio():
    m = Vox('teschio', {'skull': 0xf6f4ee, 'shade': 0xd8d4c8, 'feather': 0xffffff, 'feather2': 0xe0e4ec, 'k_eye': 0x101012, 'k_q': 0xffffff,
                        'k_dark': 0x101012, 'k_white': 0xffffff})
    m.box('skull', -6, 5, 6, 17, -5, 5)
    m.box('skull', -5, 4, 18, 18, -4, 4)
    m.box('shade', -4, 3, 3, 5, -3, 4)
    m.front('k_eye', [(x, y) for x in range(-5, -1) for y in range(10, 15)], depth=2)
    m.front('k_eye', [(x, y) for x in range(1, 5) for y in range(10, 15)], depth=2)
    for ex in (-4, 2):
        q = [(1, 4), (2, 4), (0, 3), (2, 3), (2, 2), (1, 1), (1, 0)]
        for (qx, qy) in q: m.set(ex + qx - 1, 10 + qy, 4, 'k_q')
    m.front('k_dark', [(-1, 8), (0, 8)], depth=2)
    for x in range(-4, 4): m.set(x, 4, 5, 'k_white' if x % 2 == 0 else 'k_dark')
    for side, part in ((1, 'wingL'), (-1, 'wingR')):
        for k in range(7):
            x = 6 + k if side > 0 else -7 - k
            top = 18 - abs(k - 2)
            m.box('feather' if k % 2 == 0 else 'feather2', x, x, top - 8 + k // 2, top, -1, 0, part)
        m.pivot(part, 6 * side, 14, 0)
    m.floats = True
    return m


# ------------------------------------------------------------------ OG
@model
def leone():
    m = Vox('leone', {'machine': 0xf2f4f8, 'panel': 0xc8ccd8, 'knob': 0x3a3a48, 'glass': 0x5ab8f0, 'clothes': 0xff5a8a, 'clothes2': 0xffe04a,
                      'mane': 0xc8681a, 'mane2': 0xe89a2a, 'lion': 0xf2c25a, 'nose': 0x3a2410, 'paw': 0xf2c25a,
                      'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x3a1a10})
    m.box('machine', -7, 6, 3, 18, -5, 4)
    m.box('panel', -7, 6, 16, 18, -5, 4)
    m.box('knob', 3, 4, 17, 17, 5, 5); m.box('knob', -5, -4, 17, 17, 5, 5)
    for x in range(-5, 5):
        for y in range(5, 15):
            d = math.hypot(x + 0.5, y + 0.5 - 10)
            if d <= 5: m.set(x, y, 5, 'panel' if d > 4 else 'glass')
    m.paint('clothes', lambda x, y, z: z == 5 and math.hypot(x + 0.5, y + 0.5 - 10) < 3.5 and (x + y) % 3 == 0, src='glass')
    m.paint('clothes2', lambda x, y, z: z == 5 and math.hypot(x + 0.5, y + 0.5 - 10) < 3.5 and (x - y) % 4 == 0, src='glass')
    m.ell('mane', 0, 23, 0, 6.5, 6, 4.5)
    m.paint('mane2', lambda x, y, z: (x + y + z) % 3 == 0, src='mane')
    m.ell('lion', 0, 23, 2.5, 4, 4, 3)
    m.ell('mane', 3.5, 27, 1, 1.5, 1.5, 1.2); m.ell('mane', -3.5, 27, 1, 1.5, 1.5, 1.2)
    m.box('k_white', 1, 2, 24, 25, 5, 5); m.box('k_white', -3, -2, 24, 25, 5, 5)
    m.box('k_eye', 1, 1, 24, 24, 5, 5); m.box('k_eye', -2, -2, 24, 24, 5, 5)
    m.box('nose', -1, 0, 22, 22, 5, 6)
    m.box('k_dark', -2, 1, 20, 20, 5, 5)
    legs(m, 2, 3, 'paw', 'paw', w=3, d=3, shoe_len=4)
    m.tube('lion', [(0, 10, -5), (0, 12, -9), (0, 16, -10)], 0.8, 'tail')
    m.ell('mane', 0, 17, -10, 1.4, 1.4, 1.4, 'tail')
    m.pivot('tail', 0, 10, -5)
    return m


@model
def uovo():
    m = Vox('uovo', {'shell': 0xffd23a, 'shell2': 0xe8a818, 'g_crack': 0xfff8c0, 'crown': 0xff9a1a, 'gem': 0xe0302a, 'feet': 0xff9a1a,
                     'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x5a3a00})
    m.ell('shell', 0, 15, 0, 7, 10.5, 6.5)
    m.paint('shell2', lambda x, y, z: (x * 3 + y * 5 + z * 7) % 9 == 0, src='shell')
    crack = [(-6, 15), (-5, 16), (-4, 15), (-3, 16), (-2, 15), (-1, 16), (0, 15), (1, 16), (2, 15), (3, 16), (4, 15), (5, 16)]
    m.front('g_crack', crack, depth=1)
    m.eyes(18, 2, size=2)
    m.smile(12, 2)
    m.box('crown', -4, 3, 25, 26, -3, 3)
    for x in (-4, -1, 2): m.box('crown', x, x + 1, 27, 28, 2, 3)
    m.box('gem', -1, 0, 26, 26, 4, 4)
    legs(m, 2, 4, 'feet', 'feet', w=2, d=2, shoe_len=4)
    arms(m, 7, 15, 4, 'shell2', w=1, out=1)
    return m


# ------------------------------------------------------------------ bot avatars (blocky player characters)
def avatar(name, hair):
    m = Vox(name, {'skin': 0xf5cd30, 'shirt': 0x3a7ad8, 'pants': 0x2a8a3a, 'shoe': 0x2a2a2a, 'hair': 0x5a3a1a, 'hat': 0xd02a2a,
                   'k_white': 0xffffff, 'k_eye': 0x101018, 'k_dark': 0x101018}, vs=1 / 16)
    legs(m, 0, 12, 'pants', 'shoe', w=4, d=4, shoe_len=4)
    for side in (1, -1):
        part = 'legL' if side > 0 else 'legR'
        m.pivot(part, 2 * side, 12, 0)
    m.box('shirt', -4, 3, 12, 23, -2, 1)
    for side, part in ((1, 'armL'), (-1, 'armR')):
        x0 = 4 if side > 0 else -8
        m.box('shirt', x0, x0 + 3, 18, 23, -2, 1, part)
        m.box('skin', x0, x0 + 3, 12, 17, -2, 1, part)
        m.pivot(part, (x0 + 2), 22, 0)
    m.box('skin', -5, 4, 24, 33, -4, 4, 'head')
    m.box('k_white', 1, 2, 28, 29, 5, 5, 'head'); m.box('k_white', -3, -2, 28, 29, 5, 5, 'head')
    m.box('k_eye', 1, 1, 28, 29, 5, 5, 'head'); m.box('k_eye', -2, -2, 28, 29, 5, 5, 'head')
    m.box('k_dark', -2, 1, 26, 26, 5, 5, 'head'); m.box('k_dark', -3, -3, 27, 27, 5, 5, 'head'); m.box('k_dark', 2, 2, 27, 27, 5, 5, 'head')
    if hair == 'spiky':
        m.box('hair', -5, 4, 33, 35, -4, 4, 'head')
        for x in range(-5, 5, 2): m.box('hair', x, x + 1, 36, 37, -2 + (x % 3), 1 + (x % 3), 'head')
        m.box('hair', -5, 4, 30, 33, -4, -3, 'head')
    elif hair == 'cap':
        m.box('hat', -5, 4, 33, 35, -4, 4, 'head')
        m.box('hat', -5, 4, 33, 33, 5, 7, 'head')
        m.box('hair', -5, 4, 30, 32, -4, -4, 'head')
    elif hair == 'long':
        m.box('hair', -5, 4, 33, 35, -4, 4, 'head')
        m.box('hair', -6, -5, 20, 34, -4, 2, 'head'); m.box('hair', 4, 5, 20, 34, -4, 2, 'head')
        m.box('hair', -5, 4, 20, 34, -5, -4, 'head')
    m.pivot('head', 0, 24, 0)
    return m


def avatars():
    for i, h in enumerate(['spiky', 'cap', 'long', 'bald']):
        m = avatar('avatar_%d' % i, h)
        n = m.write(OUT + 'avatar_%d.obj' % i)
        print(f'avatar_{i}       {n:5d} quads  height {m.height():.2f}')


def build_all():
    os.makedirs(OUT, exist_ok=True)
    heights = {}
    for name, fn in MODELS.items():
        m = fn()
        n = m.write(OUT + name + '.obj')
        heights[name] = round(m.height(), 2)
        print(f'{name:14s} {n:5d} quads  height {m.height():.2f}')
    return heights


if __name__ == '__main__':
    build_all()
    avatars()
