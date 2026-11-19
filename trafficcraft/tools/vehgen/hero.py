import sys; sys.path.insert(0, '.')
import specs, vehicle, render
from PIL import Image, ImageDraw
from geom import *
S = specs.SEDAN
mesh, wm, meta = vehicle.build(S)
L, H, W = meta['length'], meta['height'], meta['width']
out = Image.new('RGB', (1600, 1300), (255, 255, 255))
tris = render.collect(mesh, wm, meta, paint=(176, 18, 27), lights=False)
im = render.render(tris, (4.9, 1.75, 5.3), (0.05, 0.62, 0.25), size=(1600, 820), fov=34, shadow=(L * 0.95, W))
out.paste(im, (0, 0))
cols = [((242, 242, 240), 'Pearl white'), ((26, 27, 30), 'Onyx black'), ((32, 54, 94), 'Deep blue'), ((125, 130, 136), 'Graphite')]
for i, (c, n) in enumerate(cols):
    t = render.collect(mesh, wm, meta, paint=c, lights=False)
    eye = [(-7.2, 2.6, -6.6), (10.5, 1.2, 0.0), (6.2, 2.0, 7.6), (-6.8, 2.4, 7.2)][i]
    im = render.render(t, eye, (0, 0.6, 0), size=(400, 480), fov=34, shadow=(L * 0.95, W))
    render.label(im, n)
    out.paste(im, (i * 400, 820))
ImageDraw.Draw(out).rectangle([0, 0, 330, 22], fill=(0, 0, 0))
ImageDraw.Draw(out).text((8, 5), 'TrafficCraft - Aster Sedan (procedural model v2, %d tris LOD0)' % meta['tris'], fill=(255, 255, 255))
out.save('/tmp/claude-0/vehout/preview/sedan_hero.png')
