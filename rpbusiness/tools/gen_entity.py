"""GeckoLib NPC model + animations + skins, and the fridge / freezer GeckoLib block models."""
import os, json, random
from PIL import Image
from pix import hexc, mul

C = hexc
SKIN = ['f1c9a5', 'd9a27a', 'b07a52', '7a4e32']
HAIR = ['2b1d14', '4a2f1c', '8a5a2e', 'd8b35a', '1a1a1a', 'a33b22', '6b6b6b', '3b2a20']


def cube(origin, size, uv, inflate=0):
    c = {'origin': origin, 'size': size, 'uv': uv}
    if inflate:
        c['inflate'] = inflate
    return c


def bone(name, pivot, parent=None, cubes=None):
    b = {'name': name, 'pivot': pivot}
    if parent:
        b['parent'] = parent
    if cubes:
        b['cubes'] = cubes
    return b


def npc_geo():
    bones = [
        bone('root', [0, 0, 0]),
        bone('body', [0, 24, 0], 'root', [cube([-4, 12, -2], [8, 12, 4], [16, 16])]),
        bone('head', [0, 24, 0], 'body', [cube([-4, 24, -4], [8, 8, 8], [0, 0]), cube([-4, 24, -4], [8, 8, 8], [32, 0], 0.5)]),
        bone('chef_hat', [0, 32, 0], 'head', [cube([-4, 32, -4], [8, 6, 8], [0, 32], 0.4)]),
        bone('cap', [0, 32, 0], 'head', [cube([-4, 31, -4], [8, 2, 8], [32, 32], 0.35), cube([-4, 31, -8], [8, 1, 4], [32, 42])]),
        bone('right_arm', [-5, 22, 0], 'body', [cube([-8, 12, -2], [4, 12, 4], [40, 16])]),
        bone('right_item', [-6, 13, 0], 'right_arm'),
        bone('left_arm', [5, 22, 0], 'body', [cube([4, 12, -2], [4, 12, 4], [32, 48])]),
        bone('right_leg', [-1.9, 12, 0], 'root', [cube([-3.9, 0, -2], [4, 12, 4], [0, 16])]),
        bone('left_leg', [1.9, 12, 0], 'root', [cube([-0.1, 0, -2], [4, 12, 4], [16, 48])]),
    ]
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.rpbusiness.npc', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 2, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


def kf(d):
    return {f'{k:.4g}' if isinstance(k, float) else str(k): v for k, v in d.items()}


def anim(length, bones, loop=True):
    out = {'loop': loop, 'animation_length': length, 'bones': {}}
    for bn, chans in bones.items():
        out['bones'][bn] = {}
        for ch, frames in chans.items():
            if isinstance(frames, dict):
                out['bones'][bn][ch] = {str(round(t, 3)): v for t, v in frames.items()}
            else:
                out['bones'][bn][ch] = frames
    return out


# Sign convention (verified in-game): positive X rotation swings a limb backwards; negative X lifts it forward.
F = -1  # "forward" multiplier for X rotations


def npc_anims():
    A = {}
    A['idle'] = anim(3.0, {
        'right_arm': {'rotation': {0: [0, 0, 3], 1.5: [0, 0, 5], 3.0: [0, 0, 3]}},
        'left_arm': {'rotation': {0: [0, 0, -3], 1.5: [0, 0, -5], 3.0: [0, 0, -3]}},
        'head': {'rotation': {0: [0, 0, 0], 1.5: [2, 4, 0], 3.0: [0, 0, 0]}}})
    A['walk'] = anim(1.0, {
        'right_leg': {'rotation': {0: [30, 0, 0], 0.5: [-30, 0, 0], 1.0: [30, 0, 0]}},
        'left_leg': {'rotation': {0: [-30, 0, 0], 0.5: [30, 0, 0], 1.0: [-30, 0, 0]}},
        'right_arm': {'rotation': {0: [-25, 0, 0], 0.5: [25, 0, 0], 1.0: [-25, 0, 0]}},
        'left_arm': {'rotation': {0: [25, 0, 0], 0.5: [-25, 0, 0], 1.0: [25, 0, 0]}},
        'body': {'position': {0: [0, 0, 0], 0.25: [0, 0.4, 0], 0.5: [0, 0, 0], 0.75: [0, 0.4, 0], 1.0: [0, 0, 0]}}})
    A['sit'] = anim(2.0, {
        'root': {'position': [0, -11, 0]},
        'right_leg': {'rotation': [F * 90, 8, 0]},
        'left_leg': {'rotation': [F * 90, -8, 0]},
        'right_arm': {'rotation': [F * 20, 0, 4]},
        'left_arm': {'rotation': [F * 20, 0, -4]}})
    A['chop'] = anim(0.5, {
        'right_arm': {'rotation': {0: [F * 75, 0, 0], 0.25: [F * 35, 0, 0], 0.5: [F * 75, 0, 0]}},
        'left_arm': {'rotation': [F * 40, -20, 0]},
        'head': {'rotation': [20, 0, 0]}})
    A['cook'] = anim(1.2, {
        'right_arm': {'rotation': {0: [F * 55, 0, 0], 0.6: [F * 45, 10, 10], 1.2: [F * 55, 0, 0]}},
        'left_arm': {'rotation': {0: [F * 45, 0, 0], 0.6: [F * 50, -5, 0], 1.2: [F * 45, 0, 0]}},
        'head': {'rotation': [18, 0, 0]}})
    A['carry'] = anim(1.0, {
        'right_arm': {'rotation': [F * 75, 8, 0]},
        'left_arm': {'rotation': [F * 75, -8, 0]}})
    A['clean'] = anim(0.8, {
        'right_arm': {'rotation': {0: [F * 60, -30, 0], 0.4: [F * 60, 30, 0], 0.8: [F * 60, -30, 0]}},
        'left_arm': {'rotation': [F * 20, 0, -8]},
        'body': {'rotation': [8, 0, 0]},
        'head': {'rotation': [15, 0, 0]}})
    A['type'] = anim(0.6, {
        'right_arm': {'rotation': {0: [F * 45, 5, 0], 0.3: [F * 39, 5, 0], 0.6: [F * 45, 5, 0]}},
        'left_arm': {'rotation': {0: [F * 39, -5, 0], 0.3: [F * 45, -5, 0], 0.6: [F * 39, -5, 0]}},
        'head': {'rotation': [12, 0, 0]}})
    A['eat'] = anim(0.8, {
        'right_arm': {'rotation': {0: [F * 100, 20, 0], 0.4: [F * 80, 15, 0], 0.8: [F * 100, 20, 0]}},
        'left_arm': {'rotation': [F * 30, 0, -4]},
        'head': {'rotation': {0: [0, 0, 0], 0.4: [6, 0, 0], 0.8: [0, 0, 0]}}})
    A['pay'] = anim(1.0, {
        'right_arm': {'rotation': {0: [F * 60, 0, 0], 0.5: [F * 65, 0, 0], 1.0: [F * 60, 0, 0]}}})
    A['reach'] = anim(0.7, {
        'right_arm': {'rotation': {0: [F * 85, 0, 0], 0.35: [F * 60, 0, 0], 0.7: [F * 85, 0, 0]}},
        'left_arm': {'rotation': [F * 50, 0, 0]},
        'body': {'rotation': [8, 0, 0]}})
    A['talk'] = anim(1.0, {
        'right_arm': {'rotation': {0: [F * 30, 0, -10], 0.5: [F * 45, 0, -25], 1.0: [F * 30, 0, -10]}},
        'head': {'rotation': {0: [0, -8, 0], 0.5: [0, 8, 0], 1.0: [0, -8, 0]}}})
    A['read'] = anim(2.0, {
        'right_arm': {'rotation': [F * 55, 15, 0]},
        'left_arm': {'rotation': [F * 55, -15, 0]},
        'head': {'rotation': {0: [18, -4, 0], 1.0: [18, 4, 0], 2.0: [18, -4, 0]}}})
    return {'format_version': '1.8.0', 'animations': A}


# ------------------------------------------------------------------ skins
def paint_box(img, u, v, w, h, d, faces):
    """faces: dict top,bottom,right,front,left,back -> fn(x,y,fw,fh)->color or color"""
    p = img.load()
    regions = {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
    for k, (x0, y0, fw, fh) in regions.items():
        f = faces.get(k)
        if f is None:
            continue
        for y in range(fh):
            for x in range(fw):
                col = f(x, y, fw, fh) if callable(f) else f
                if col is not None:
                    p[x0 + x, y0 + y] = col


def noisy(base, r, amt=0.05):
    return mul(base, 1 + (r.random() - 0.5) * 2 * amt)


def skin_png(path, tone, hair, shirt, pants, shoes, seed, extra=None, hair_style=0):
    r = random.Random(seed)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    sk = C(tone)
    hc = C(hair)

    def face_fn(x, y, fw, fh):
        if y < 2 + (hair_style % 2):
            return noisy(hc, r)
        if hair_style == 2 and (x == 0 or x == 7) and y < 6:
            return noisy(hc, r)
        if y == 4 and x in (1, 2, 5, 6):
            return C('ffffff') if x in (1, 6) else C('2b3a55')
        if y == 3 and x in (1, 2, 5, 6):
            return mul(hc, 0.9)
        if y == 6 and x in (3, 4):
            return C('9a4a3a')
        if y == 5 and x in (3, 4):
            return mul(sk, 0.9)
        return noisy(sk, r, 0.03)

    def hair_side(x, y, fw, fh):
        lim = 3 if hair_style != 2 else 7
        return noisy(hc, r) if y < lim or (hair_style == 1 and y < 5) else noisy(sk, r, 0.03)

    paint_box(img, 0, 0, 8, 8, 8, {'top': lambda *a: noisy(hc, r), 'bottom': sk, 'front': face_fn, 'right': hair_side,
                                    'left': hair_side, 'back': lambda x, y, *a: noisy(hc, r) if y < (7 if hair_style == 2 else 6) else noisy(sk, r, 0.03)})
    sh = C(shirt)

    def body(x, y, fw, fh):
        return noisy(sh, r, 0.04)
    paint_box(img, 16, 16, 8, 12, 4, {k: body for k in ('top', 'bottom', 'right', 'front', 'left', 'back')})

    def arm(x, y, fw, fh):
        return noisy(sh, r, 0.04) if y < 5 else noisy(sk, r, 0.03)
    paint_box(img, 40, 16, 4, 12, 4, {k: arm for k in ('right', 'front', 'left', 'back')} | {'top': sh, 'bottom': sk})
    paint_box(img, 32, 48, 4, 12, 4, {k: arm for k in ('right', 'front', 'left', 'back')} | {'top': sh, 'bottom': sk})
    pc, so = C(pants), C(shoes)

    def leg(x, y, fw, fh):
        return noisy(so, r, 0.03) if y >= 10 else noisy(pc, r, 0.04)
    paint_box(img, 0, 16, 4, 12, 4, {k: leg for k in ('right', 'front', 'left', 'back')} | {'top': pc, 'bottom': so})
    paint_box(img, 16, 48, 4, 12, 4, {k: leg for k in ('right', 'front', 'left', 'back')} | {'top': pc, 'bottom': so})
    if extra:
        extra(img, r)
    img.save(path)


def body_front_px(img, x, y, col):
    img.load()[20 + x, 20 + y] = col


def outfit_extra(kind):
    def chef(img, r):
        for y in (2, 5, 8):
            body_front_px(img, 2, y, C('2b2b2b')); body_front_px(img, 5, y, C('2b2b2b'))
        for x in range(8):
            body_front_px(img, x, 0, C('dfe4e8'))
        # checkered pants
        p = img.load()
        for (u, v) in ((0, 16), (16, 48)):
            for y in range(4, 14):
                for x in range(16):
                    if y < 14 and ((x // 2 + y // 2) % 2 == 0) and p[u + x, v + y][3] > 0 and y - 4 < 10:
                        p[u + x, v + y] = C('2b2b2b')
        paint_box(img, 0, 32, 8, 6, 8, {k: (lambda x, y, *a: noisy(C('fafafa'), r, 0.03)) for k in ('top', 'bottom', 'right', 'front', 'left', 'back')})

    def waiter(img, r):
        for y in range(12):
            for x in (0, 1, 6, 7):
                body_front_px(img, x, y, C('1e1e22'))
        body_front_px(img, 3, 0, C('b0302c')); body_front_px(img, 4, 0, C('b0302c'))
        body_front_px(img, 3, 1, C('8a2420')); body_front_px(img, 4, 1, C('8a2420'))

    def cashier(img, r):
        body_front_px(img, 5, 3, C('f4f1e6')); body_front_px(img, 6, 3, C('f4f1e6'))
        body_front_px(img, 3, 0, C('ffffff')); body_front_px(img, 4, 0, C('ffffff'))

    def cleaner(img, r):
        for y in range(4, 12):
            for x in (2, 3, 4, 5):
                body_front_px(img, x, y, C('2f6fc0'))
        body_front_px(img, 2, 1, C('ffd166')); body_front_px(img, 5, 1, C('ffd166'))

    def manager(img, r):
        for y in range(12):
            body_front_px(img, 3, y, C('f4f1e6')); body_front_px(img, 4, y, C('f4f1e6'))
        for y in range(1, 9):
            body_front_px(img, 3 + (y % 2), y, C('a8302e'))

    def courier(img, r):
        body_front_px(img, 1, 2, C('ffd166')); body_front_px(img, 2, 2, C('ffd166'))
        paint_box(img, 32, 32, 8, 2, 8, {k: (lambda x, y, *a: noisy(C('c67a2a'), r, 0.04)) for k in ('top', 'bottom', 'right', 'front', 'left', 'back')})
        paint_box(img, 32, 42, 8, 1, 4, {k: (lambda x, y, *a: noisy(C('a8641f'), r, 0.04)) for k in ('top', 'bottom', 'right', 'front', 'left', 'back')})
    return {'chef': chef, 'waiter': waiter, 'cashier': cashier, 'cleaner': cleaner, 'manager': manager, 'courier': courier}[kind]


OUTFITS = {
    'chef': ('fafafa', '2b2b2b', '1e1e22'),
    'waiter': ('fafafa', '1e1e22', '111111'),
    'cashier': ('c62e2b', 'c9a36a', '3b2a20'),
    'cleaner': ('8fb0d0', '2f6fc0', '3b3b3b'),
    'manager': ('2b2f3a', '2b2f3a', '111111'),
    'courier': ('8a5a2e', '6b4a2e', '2b1d14'),
}


def skins(out):
    os.makedirs(out, exist_ok=True)
    shirts = ['3a8ee6', 'e8a33d', '7bd88f', 'b56ad8', 'f25f5c', '2fb5a8', 'f4f1e6', 'ffcf3a']
    pants = ['2f4a7a', '3b3b3b', '6b4a2e', '1e2b4a', '8a8a8a', '2b2f3a', '5a3d29', '3f5a8a']
    shoes = ['f4f4f4', '2b1d14', '111111', '7a4e32', 'c62e2b', '2b2b2b', 'e8e8e8', '3b2a20']
    for i in range(8):
        skin_png(os.path.join(out, f'customer_{i}.png'), SKIN[i % 4], HAIR[(i * 3) % len(HAIR)], shirts[i], pants[i], shoes[i], 100 + i, hair_style=i % 3)
    for k, (shirt, pant, shoe) in OUTFITS.items():
        for s in range(4):
            skin_png(os.path.join(out, f'{k}_{s}.png'), SKIN[s], HAIR[(s * 2 + 1) % len(HAIR)], shirt, pant, shoe, 200 + s + len(k), extra=outfit_extra(k), hair_style=s % 3)


# ------------------------------------------------------------------ fridge / freezer (GeckoLib block models)
def fridge_geo():
    bones = [
        bone('body', [0, 0, 0], None, [cube([-7, 0, -7], [14, 32, 14], [0, 0])]),
        bone('door', [7, 0, -7], 'body', [cube([-7, 1, -8], [14, 30, 1], [0, 46]), cube([-6, 12, -9], [1, 10, 1], [32, 46])]),
    ]
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.rpbusiness.fridge', 'texture_width': 64, 'texture_height': 96,
                        'visible_bounds_width': 2, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]}, 'bones': bones}]}


def freezer_geo():
    bones = [
        bone('body', [0, 0, 0], None, [cube([-8, 0, -7], [16, 12, 14], [0, 0])]),
        bone('lid', [0, 12, 7], 'body', [cube([-8, 12, -7], [16, 2, 14], [0, 26]), cube([-2, 11, -8], [4, 1, 1], [0, 42])]),
    ]
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.rpbusiness.freezer', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 2, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, 0.5, 0]}, 'bones': bones}]}


def storage_anims(bonename, axis, angle):
    rot_open = [0, angle, 0] if axis == 'y' else [angle, 0, 0]
    return {'format_version': '1.8.0', 'animations': {
        'open': {'loop': 'hold_on_last_frame', 'animation_length': 0.35, 'bones': {bonename: {'rotation': {'0.0': [0, 0, 0], '0.35': rot_open}}}},
        'close': {'loop': 'hold_on_last_frame', 'animation_length': 0.35, 'bones': {bonename: {'rotation': {'0.0': rot_open, '0.35': [0, 0, 0]}}}},
        'idle': {'loop': True, 'animation_length': 1.0, 'bones': {bonename: {'rotation': [0, 0, 0]}}}}}


def fridge_tex(path):
    r = random.Random(7)
    img = Image.new('RGBA', (64, 96), (0, 0, 0, 0))
    steel = lambda x, y, *a: noisy(C('d6dde2'), r, 0.03) if y % 7 else noisy(C('c3cbd1'), r, 0.03)

    def interior(x, y, fw, fh):
        if x == 0 or x == fw - 1 or y == 0 or y == fh - 1:
            return C('c3cbd1')
        if y in (10, 20):
            return C('9fb5c4')
        if 11 <= y <= 13 and 2 <= x <= 5:
            return C('e0372f')
        if 21 <= y <= 23 and 7 <= x <= 11:
            return C('ffcf3a')
        if 26 <= y <= 29 and 3 <= x <= 9:
            return C('6fd04a')
        return noisy(C('eef6fb'), r, 0.02)
    paint_box(img, 0, 0, 14, 32, 14, {'top': steel, 'bottom': steel, 'right': steel, 'left': steel, 'back': steel, 'front': interior})

    def door_front(x, y, fw, fh):
        if y == 14:
            return C('9aa3aa')
        return noisy(C('e4eaee'), r, 0.025)

    def door_back(x, y, fw, fh):
        if y % 8 == 0:
            return C('b8c3cb')
        return noisy(C('f4f8fb'), r, 0.02)
    paint_box(img, 0, 46, 14, 30, 1, {'top': steel, 'bottom': steel, 'right': steel, 'left': steel, 'front': door_front, 'back': door_back})
    paint_box(img, 32, 46, 1, 10, 1, {k: C('2b2f33') for k in ('top', 'bottom', 'right', 'front', 'left', 'back')})
    img.save(path)


def freezer_tex(path):
    r = random.Random(8)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    white = lambda x, y, *a: noisy(C('f1f4f6'), r, 0.02)

    def inside_top(x, y, fw, fh):
        if x == 0 or y == 0 or x == fw - 1 or y == fh - 1:
            return C('c3cbd1')
        return noisy(C('cfe6f5'), r, 0.04) if (x + y) % 5 else C('ffffff')
    paint_box(img, 0, 0, 16, 12, 14, {'top': inside_top, 'bottom': white, 'right': white, 'left': white, 'back': white,
                                       'front': lambda x, y, *a: C('3a8ee6') if 2 <= y <= 3 else noisy(C('f1f4f6'), r, 0.02)})
    steel = lambda x, y, *a: noisy(C('c3cbd1'), r, 0.03)
    paint_box(img, 0, 26, 16, 2, 14, {'top': steel, 'bottom': lambda *a: C('dfeaf2'), 'right': steel, 'left': steel, 'front': steel, 'back': steel})
    paint_box(img, 0, 42, 4, 1, 1, {k: C('2b2f33') for k in ('top', 'bottom', 'right', 'front', 'left', 'back')})
    img.save(path)


def generate(assets):
    geo = os.path.join(assets, 'geo')
    an = os.path.join(assets, 'animations')
    os.makedirs(os.path.join(geo, 'entity'), exist_ok=True)
    os.makedirs(os.path.join(geo, 'block'), exist_ok=True)
    os.makedirs(os.path.join(an, 'entity'), exist_ok=True)
    os.makedirs(os.path.join(an, 'block'), exist_ok=True)
    json.dump(npc_geo(), open(os.path.join(geo, 'entity', 'npc.geo.json'), 'w'), indent=1)
    json.dump(npc_anims(), open(os.path.join(an, 'entity', 'npc.animation.json'), 'w'), indent=1)
    json.dump(fridge_geo(), open(os.path.join(geo, 'block', 'fridge.geo.json'), 'w'), indent=1)
    json.dump(freezer_geo(), open(os.path.join(geo, 'block', 'freezer.geo.json'), 'w'), indent=1)
    json.dump(storage_anims('door', 'y', -105), open(os.path.join(an, 'block', 'fridge.animation.json'), 'w'), indent=1)
    json.dump(storage_anims('lid', 'x', 70), open(os.path.join(an, 'block', 'freezer.animation.json'), 'w'), indent=1)
    skins(os.path.join(assets, 'textures', 'entity', 'npc'))
    os.makedirs(os.path.join(assets, 'textures', 'entity'), exist_ok=True)
    # DefaultedEntityGeoModel expects textures/entity/npc.png as the default texture path
    Image.open(os.path.join(assets, 'textures', 'entity', 'npc', 'customer_0.png')).save(os.path.join(assets, 'textures', 'entity', 'npc.png'))
    fridge_tex(os.path.join(assets, 'textures', 'block', 'fridge.png'))
    freezer_tex(os.path.join(assets, 'textures', 'block', 'freezer.png'))
