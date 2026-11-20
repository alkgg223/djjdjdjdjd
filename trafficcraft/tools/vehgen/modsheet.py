"""Customisation preview sheets for one vehicle: every catalogue slot with its 5 options, themed builds, liveries."""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
from PIL import Image, ImageDraw
import specs, vehicle, render, custom
from geom import v3

vid, out = sys.argv[1], sys.argv[2]
S = [s for s in specs.SPECS if s['id'] == vid][0]
mesh, wm, meta = vehicle.build(S)
custom.livery_uv(mesh, S['L'], S['W'], S['H'])
paint = tuple(int(S['preview'][i:i + 2], 16) for i in (1, 3, 5))
L, H, W = S['L'], S['H'], S['W']
d = max(L, 4.5) * 1.55
VIEWS = {
    'fb': ((W * 1.3, H * 0.55, L * 1.05), (0, H * 0.3, L * 0.42), 30),
    'rb': ((-W * 1.2, H * 0.55, -L * 1.0), (0, H * 0.33, -L * 0.42), 30),
    'ex': ((-W * 0.9, H * 0.35, -L * 0.85), (0, H * 0.25, -L * 0.46), 26),
    'sp': ((-d * 0.55, H * 1.6, -d * 0.6), (0, H * 0.6, -L * 0.25), 26),
    'hd': ((W * 0.9, H * 2.6, L * 1.1), (0, H * 0.6, L * 0.3), 30),
    'wh': ((W * 1.6, H * 0.45, L * 0.32), (0, H * 0.3, L * 0.3), 28),
    'sk': ((d * 0.9, H * 0.5, d * 0.25), (0, H * 0.35, 0), 30),
    'wb': ((d * 0.62, H * 1.0, d * 0.72), (0, H * 0.45, 0), 28),
    'rf': ((d * 0.62, H * 1.6, d * 0.72), (0, H * 0.6, 0), 28),
}
size = (420, 260)
cat = meta['catalog']
for slot, opts in cat.items():
    imgs = []
    eye, tgt, fov = VIEWS[slot]
    for i, o in enumerate(opts):
        render.CONFIG = {slot: i}
        tris = render.collect(mesh, wm, meta, paint=paint, lights=False)
        im = render.render(tris, eye, tgt, size=size, fov=fov, shadow=(L * 0.95, W))
        render.label(im, '%s %d: %s' % (custom.SLOT_NAMES[slot], i + 1, o['name']))
        imgs.append(im)
    sheet = Image.new('RGB', (size[0] * len(imgs), size[1]), (255, 255, 255))
    for i, im in enumerate(imgs):
        sheet.paste(im, (i * size[0], 0))
    sheet.save('%s/%s_%s.png' % (out, vid, slot))
    print(slot, len(imgs))
# themed builds
builds = {'Factory': {}, 'Street': dict(fb=2, rb=2, ex=1, sp=2, hd=1, wh=1, sk=1), 'Time attack': dict(fb=3, rb=3, ex=3, sp=3, hd=4, wh=4, sk=2, wb=2),
          'Drift': dict(fb=4, rb=4, ex=2, sp=4, hd=2, wh=2, sk=1, wb=1)}
LV = Image.open('/tmp/claude-0/vehout/liveries.png').convert('RGBA')
names = json.load(open('/tmp/claude-0/vehout/liveries.json'))['names']


def tile(k):
    a = np.array(LV.crop(((k % 8) * 256, (k // 8) * 192, (k % 8 + 1) * 256, (k // 8 + 1) * 192)), float) / 255
    return a


imgs = []
for (name, cfg), liv in zip(builds.items(), (0, 51, 17, 55)):
    render.CONFIG = cfg
    render.LIVERY = tile(liv) if liv else None
    tris = render.collect(mesh, wm, meta, paint=paint, lights=False)
    for eye in ((d * 0.75, H * 1.3, d * 0.8), (-d * 0.8, H * 1.2, -d * 0.75)):
        im = render.render(tris, eye, (0, H * 0.45, 0), size=(640, 380), fov=28, shadow=(L * 0.95, W))
        render.label(im, '%s build%s' % (name, (' + livery: ' + names[liv - 1]) if liv else ''))
        imgs.append(im)
sheet = Image.new('RGB', (1280, 380 * 4), (255, 255, 255))
for i, im in enumerate(imgs):
    sheet.paste(im, ((i % 2) * 640, (i // 2) * 380))
sheet.save('%s/%s_builds.png' % (out, vid))
# livery showcase
render.CONFIG = {}
imgs = []
for liv in (1, 9, 21, 23, 33, 38, 47, 49):
    render.LIVERY = tile(liv)
    tris = render.collect(mesh, wm, meta, paint=paint if liv not in (33, 49) else (60, 60, 60), lights=False)
    im = render.render(tris, (d * 0.85, H * 1.1, d * 0.55), (0, H * 0.45, 0), size=size, fov=28, shadow=(L * 0.95, W))
    render.label(im, 'Livery %d: %s' % (liv, names[liv - 1]))
    imgs.append(im)
sheet = Image.new('RGB', (size[0] * 4, size[1] * 2), (255, 255, 255))
for i, im in enumerate(imgs):
    sheet.paste(im, ((i % 4) * size[0], (i // 4) * size[1]))
sheet.save('%s/%s_liveries.png' % (out, vid))
print('ok')
