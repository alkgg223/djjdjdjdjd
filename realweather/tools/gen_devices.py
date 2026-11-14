"""Block models, textures and data for the weather radar and its monitor (all original art)."""
import json, os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'realweather')
DATA = os.path.join(RES, 'data')
TEX = os.path.join(ASSETS, 'textures', 'block')
rng = np.random.default_rng(404)


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)


def save(name, rgb, alpha=None):
    h, w = rgb.shape[:2]
    out = np.zeros((h, w, 4), np.uint8)
    out[..., :3] = np.clip(rgb * 255, 0, 255)
    out[..., 3] = 255 if alpha is None else np.clip(alpha * 255, 0, 255)
    os.makedirs(TEX, exist_ok=True)
    Image.fromarray(out, 'RGBA').save(os.path.join(TEX, name + '.png'))


def noise(s, amp):
    return (rng.random((s, s)) - 0.5) * amp


def base(color, s=32, amp=0.05):
    img = np.zeros((s, s, 3))
    n = noise(s, amp)
    for k in range(3):
        img[..., k] = color[k] + n
    return img


def bevel(img, light=0.08, dark=0.12):
    img[0, :, :] += light
    img[:, 0, :] += light
    img[-1, :, :] -= dark
    img[:, -1, :] -= dark
    return img


def rivets(img, pts, c=(0.75, 0.75, 0.78)):
    for (x, y) in pts:
        img[y, x] = c
        img[y + 1, x + 1] = np.array(c) * 0.55
    return img


def textures():
    s = 32
    # Painted steel.
    m = base((0.5, 0.53, 0.56), amp=0.05)
    for y in range(s):
        m[y, :, :] += 0.02 * np.sin(y * 0.9)
    m = bevel(m)
    m = rivets(m, [(3, 3), (28, 3), (3, 28), (28, 28)])
    save('radar_metal', m)
    # Concrete pad.
    c = base((0.62, 0.61, 0.58), amp=0.1)
    c[15:17, :, :] -= 0.12
    c[:, 15:17, :] -= 0.12
    save('radar_concrete', bevel(c, 0.04, 0.08))
    # Steel grating (cutout).
    g = base((0.46, 0.48, 0.5), amp=0.05)
    a = np.ones((s, s))
    for y in range(s):
        for x in range(s):
            if (x % 4 != 0) and (y % 8 != 0):
                a[y, x] = 0
    save('radar_grate', g, a)
    # Fibreglass dish panels.
    d = base((0.93, 0.94, 0.95), amp=0.03)
    d[:, 0:1, :] -= 0.18
    d[0:1, :, :] -= 0.1
    d[:, 15:16, :] -= 0.08
    save('radar_dish', d)
    # Equipment cabinet: off-white with louvres and status lights.
    cab = base((0.82, 0.82, 0.78), amp=0.03)
    for y in range(6, 26, 3):
        cab[y, 5:27, :] -= 0.25
        cab[y + 1, 5:27, :] += 0.05
    cab[28:30, 4:6] = (0.2, 0.9, 0.3)
    cab[28:30, 8:10] = (0.95, 0.7, 0.15)
    cab[28:30, 12:14] = (0.9, 0.2, 0.2)
    save('radar_cabinet', bevel(cab))
    top = base((0.24, 0.25, 0.27), amp=0.03)
    save('radar_cabinet_top', bevel(top, 0.05, 0.05))
    # Console front: rows of buttons, a small LCD and dials.
    p = base((0.3, 0.32, 0.35), amp=0.03)
    p[4:10, 4:18] = (0.12, 0.3, 0.22)
    p[5:9, 5:17] = (0.2, 0.55, 0.35)
    for i, col in enumerate([(0.85, 0.2, 0.2), (0.95, 0.75, 0.2), (0.2, 0.75, 0.3), (0.25, 0.5, 0.9)]):
        p[13:16, 4 + i * 5:7 + i * 5] = col
    for cx in (23, 28):
        yy, xx = np.mgrid[0:s, 0:s]
        ring = ((xx - cx) ** 2 + (yy - 8) ** 2) <= 6
        p[ring] = (0.75, 0.75, 0.78)
        p[8, cx - 1:cx + 1] = (0.1, 0.1, 0.1)
    for y in range(20, 30, 3):
        p[y, 3:29, :] -= 0.12
    save('radar_panel', bevel(p))
    # Keyboard.
    k = base((0.14, 0.15, 0.17), amp=0.02)
    for y in range(3, 30, 6):
        for x in range(2, 30, 3):
            k[y:y + 4, x:x + 2] = (0.32, 0.33, 0.36)
            k[y, x:x + 2] = (0.42, 0.43, 0.46)
    save('radar_keyboard', k)
    # Monitor bezel (16-unit hole from u,v 3..13 = pixels 6..26).
    b = base((0.08, 0.085, 0.09), amp=0.02)
    b[6:26, 6:26] = (0.02, 0.025, 0.03)
    b[5, 5:27] += 0.08
    b[5:27, 5] += 0.08
    b[27:28, 22:24] = (0.25, 0.95, 0.35)
    save('radar_bezel', bevel(b, 0.06, 0.04))
    back = base((0.18, 0.19, 0.2), amp=0.03)
    for y in range(4, 28, 3):
        back[y, 6:26, :] -= 0.08
    save('radar_monitor_back', bevel(back))


def box(frm, to, tex, faces=None, uv=None):
    faces = faces or ['north', 'south', 'east', 'west', 'up', 'down']
    out = {}
    for f in faces:
        t = tex[f] if isinstance(tex, dict) else tex
        face = {'texture': '#' + t}
        if uv and f in uv:
            face['uv'] = uv[f]
        out[f] = face
    return {'from': frm, 'to': to, 'faces': out}


def models():
    mdir = os.path.join(ASSETS, 'models')
    els = [box([0, 0, 0], [16, 2, 16], 'concrete')]
    for x in (2, 12):
        for z in (2, 12):
            els.append(box([x, 2, z], [x + 2, 15, z + 2], 'metal'))
    for y in (6, 11):
        els.append(box([4, y, 2.5], [12, y + 1, 3.5], 'metal'))
        els.append(box([4, y, 12.5], [12, y + 1, 13.5], 'metal'))
        els.append(box([2.5, y, 4], [3.5, y + 1, 12], 'metal'))
        els.append(box([12.5, y, 4], [13.5, y + 1, 12], 'metal'))
    els.append(box([5, 2, 5], [11, 7, 11], {'north': 'cabinet', 'south': 'cabinet', 'east': 'cabinet', 'west': 'cabinet',
                                             'up': 'top', 'down': 'top'}))
    els.append(box([1, 15, 1], [15, 16, 15], 'grate'))
    for x in (1, 14):
        for z in (1, 14):
            els.append(box([x, 16, z], [x + 1, 19, z + 1], 'metal'))
    els.append(box([1, 18.5, 1], [15, 19, 1.6], 'metal'))
    els.append(box([1, 18.5, 14.4], [15, 19, 15], 'metal'))
    els.append(box([1, 18.5, 1], [1.6, 19, 15], 'metal'))
    els.append(box([14.4, 18.5, 1], [15, 19, 15], 'metal'))
    els.append(box([5, 16, 5], [11, 18, 11], 'metal'))
    wjson(os.path.join(mdir, 'block', 'weather_radar.json'), {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'particle': 'realweather:block/radar_metal', 'metal': 'realweather:block/radar_metal',
                     'concrete': 'realweather:block/radar_concrete', 'grate': 'realweather:block/radar_grate',
                     'cabinet': 'realweather:block/radar_cabinet', 'top': 'realweather:block/radar_cabinet_top'},
        'elements': els})
    mon = [
        box([1, 0, 2], [15, 8, 15], {'north': 'panel', 'south': 'cabinet', 'east': 'cabinet', 'west': 'cabinet', 'up': 'top', 'down': 'top'}),
        box([3, 8, 3], [13, 8.75, 7], {'north': 'back', 'south': 'back', 'east': 'back', 'west': 'back', 'up': 'keyboard', 'down': 'back'}),
        box([6.5, 8, 10], [9.5, 9, 12], 'metal'),
        box([1, 9, 8], [15, 21, 12], {'north': 'bezel', 'south': 'back', 'east': 'back', 'west': 'back', 'up': 'back', 'down': 'back'},
            uv={'north': [1, 2, 15, 14], 'south': [1, 2, 15, 14], 'east': [4, 2, 8, 14], 'west': [8, 2, 12, 14]}),
    ]
    wjson(os.path.join(mdir, 'block', 'radar_monitor.json'), {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:cutout',
        'textures': {'particle': 'realweather:block/radar_monitor_back', 'metal': 'realweather:block/radar_metal',
                     'panel': 'realweather:block/radar_panel', 'cabinet': 'realweather:block/radar_cabinet',
                     'top': 'realweather:block/radar_cabinet_top', 'keyboard': 'realweather:block/radar_keyboard',
                     'bezel': 'realweather:block/radar_bezel', 'back': 'realweather:block/radar_monitor_back'},
        'elements': mon})
    for name in ('weather_radar', 'radar_monitor'):
        wjson(os.path.join(mdir, 'item', name + '.json'), {'parent': 'realweather:block/' + name})
    bs = os.path.join(ASSETS, 'blockstates')
    wjson(os.path.join(bs, 'weather_radar.json'), {'variants': {'': {'model': 'realweather:block/weather_radar'}}})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    wjson(os.path.join(bs, 'radar_monitor.json'), {'variants': {
        'facing=' + f: ({'model': 'realweather:block/radar_monitor', 'y': r} if r else {'model': 'realweather:block/radar_monitor'})
        for f, r in rot.items()}})


def data():
    for name in ('weather_radar', 'radar_monitor'):
        wjson(os.path.join(DATA, 'realweather', 'loot_table', 'blocks', name + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'realweather:' + name}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': 'realweather:blocks/' + name})
    wjson(os.path.join(DATA, 'realweather', 'recipe', 'weather_radar.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': [' W ', 'IRI', 'IOI'],
        'key': {'W': {'item': 'minecraft:white_concrete'}, 'I': {'item': 'minecraft:iron_ingot'},
                'R': {'item': 'minecraft:redstone_block'}, 'O': {'item': 'minecraft:observer'}},
        'result': {'count': 1, 'id': 'realweather:weather_radar'}})
    wjson(os.path.join(DATA, 'realweather', 'recipe', 'radar_monitor.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': ['IGI', 'IRI', 'ICI'],
        'key': {'G': {'item': 'minecraft:glass_pane'}, 'I': {'item': 'minecraft:iron_ingot'},
                'R': {'item': 'minecraft:redstone'}, 'C': {'item': 'minecraft:comparator'}},
        'result': {'count': 1, 'id': 'realweather:radar_monitor'}})
    wjson(os.path.join(DATA, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), {
        'replace': False, 'values': ['realweather:weather_radar', 'realweather:radar_monitor']})


if __name__ == '__main__':
    textures()
    models()
    data()
    print('device assets done')
