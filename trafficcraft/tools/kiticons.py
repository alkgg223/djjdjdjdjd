"""16x16 item icons for the location kits: a cardboard box with the location's emblem."""
import json, os
from PIL import Image, ImageDraw
A = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'src/main/resources/assets/trafficcraft')

def box():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(1, 5), (8, 2), (15, 5), (15, 13), (8, 15), (1, 13)], fill=(176, 132, 82, 255), outline=(92, 64, 36, 255))
    d.line([(1, 5), (8, 8), (15, 5)], fill=(120, 86, 50, 255))
    d.line([(8, 8), (8, 15)], fill=(120, 86, 50, 255))
    d.polygon([(1, 5), (8, 2), (15, 5), (8, 8)], fill=(204, 160, 104, 255))
    d.line([(4, 3), (11, 6)], fill=(226, 214, 170, 255))  # tape
    return im, d

def emblem(kind, im, d):
    if kind == 'gas_station':
        d.rectangle([3, 9, 6, 13], fill=(210, 40, 36, 255)); d.rectangle([4, 10, 5, 10], fill=(240, 240, 240, 255))
        d.line([(7, 10), (7, 12)], fill=(30, 30, 30, 255))
    elif kind == 'parking':
        d.rectangle([2, 8, 7, 13], fill=(30, 80, 200, 255))
        d.line([(4, 9), (4, 12)], fill=(255, 255, 255, 255)); d.point([(5, 9), (6, 10), (5, 11)], fill=(255, 255, 255, 255))
    elif kind == 'border':
        for i in range(2, 8): d.point((i, 10), fill=(230, 30, 30, 255) if i % 2 else (250, 250, 250, 255))
        d.line([(2, 10), (2, 13)], fill=(60, 60, 60, 255)); d.rectangle([5, 11, 7, 13], fill=(200, 200, 210, 255))
    elif kind == 'bus_stop':
        d.rectangle([2, 8, 7, 9], fill=(40, 150, 70, 255)); d.line([(2, 9), (2, 13)], fill=(80, 80, 80, 255))
        d.rectangle([3, 11, 6, 11], fill=(150, 100, 60, 255))
    # right face badge
    d.rectangle([10, 9, 13, 12], fill=(250, 210, 40, 255), outline=(120, 90, 10, 255))

for k in ('gas_station', 'parking', 'border', 'bus_stop'):
    im, d = box(); emblem(k, im, d)
    im.save(f'{A}/textures/item/kit_{k}.png')
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'trafficcraft:item/kit_{k}'}}, open(f'{A}/models/item/kit_{k}.json', 'w'))
print('ok')
