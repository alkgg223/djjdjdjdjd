"""Build every car: cars/<id>.py (SPEC) -> models/<id>.bbmodel (Blockbench source) -> game assets.

usage: python3 build.py [ids...] [--no-model] [--no-game]
  --no-model : skip regenerating the .bbmodel (use the existing, possibly hand-edited, file)
"""
import os, sys, json, importlib.util, shutil
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, 'cars'))
import bb
RES = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'trafficcraft')


def car_ids():
    return sorted(f[:-3] for f in os.listdir(os.path.join(HERE, 'cars')) if f.endswith('.py') and not f.startswith('_'))


def load_spec(cid):
    sp = importlib.util.spec_from_file_location('car_' + cid, os.path.join(HERE, 'cars', cid + '.py'))
    mod = importlib.util.module_from_spec(sp)
    sp.loader.exec_module(mod)
    return mod.SPEC


def convert(path):
    tris, img, meta = bb.read_bbmodel(path)
    cid = meta['id']
    n = bb.write_tcm(tris, os.path.join(RES, 'vehicles', cid + '.tcm'))
    shutil.copy(os.path.join(RES, 'vehicles', cid + '.tcm'), os.path.join(RES, 'vehicles', cid + '_lod1.tcm'))
    img.save(os.path.join(RES, 'textures', 'vehicle', cid + '.png'))
    meta['tris'] = n
    json.dump(meta, open(os.path.join(RES, 'vehicles', cid + '.json'), 'w'), indent=1)
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'trafficcraft:item/vehicle_' + cid}},
              open(os.path.join(RES, 'models', 'item', 'vehicle_' + cid + '.json'), 'w'))
    icon(cid)
    lang(meta)
    return cid, n


def icon(cid):
    """64x64 inventory icon cut from the preview render (render_bb.py quick), background keyed out."""
    from PIL import Image
    src = os.path.join('/tmp/claude-0/quick', cid, 'q_front34.png')
    out = os.path.join(RES, 'textures', 'item', 'vehicle_' + cid + '.png')
    if not os.path.exists(src):
        return
    im = Image.open(src).convert('RGBA')
    px = im.load()
    W, H = im.size
    bg = [px[2, 2], px[2, H - 3]]
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if all(abs(r - c[0]) + abs(g - c[1]) + abs(b - c[2]) < 40 for c in bg[:1]) or (abs(r - g) < 6 and abs(g - b) < 6 and r > 150):
                px[x, y] = (0, 0, 0, 0)
    bbox = im.getbbox() or (0, 0, W, H)
    im = im.crop(bbox)
    s = max(im.size)
    sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    sq.paste(im, ((s - im.size[0]) // 2, (s - im.size[1]) // 2))
    sq.resize((64, 64), Image.LANCZOS).save(out)


CAT_AR = {'muscle': ('Muscle car', 'سيارة عضلية'), 'classic': ('Classic car', 'سيارة كلاسيكية'), 'compact': ('Compact car', 'سيارة صغيرة'),
          'luxury': ('Luxury car', 'سيارة فاخرة'), 'wagon': ('Estate car', 'سيارة ستيشن'), 'super': ('Supercar', 'سيارة خارقة'),
          'sports': ('Sports car', 'سيارة رياضية'), 'sedan': ('Sedan', 'سيدان'), 'suv': ('SUV', 'دفع رباعي')}


def lang(meta):
    for f in ('en_us', 'ar_sa', 'ar_iq'):
        p = os.path.join(RES, 'lang', f + '.json')
        d = json.load(open(p, encoding='utf-8'))
        d['item.trafficcraft.vehicle_' + meta['id']] = meta['name']
        c = meta['category']
        if c in CAT_AR:
            d['tooltip.trafficcraft.category.' + c] = CAT_AR[c][0 if f == 'en_us' else 1]
        json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)


KEEP = ['police', 'taxi', 'delivery_van', 'box_truck', 'cruiser_bike', 'sport_bike', 'scrambler_bike']   # service vehicles + motorcycles


def write_index():
    cars = sorted(f[:-8] for f in os.listdir(os.path.join(HERE, 'models')) if f.endswith('.bbmodel'))
    cars = [c for c in cars if os.path.exists(os.path.join(RES, 'vehicles', c + '.json'))]
    json.dump(cars + KEEP, open(os.path.join(RES, 'vehicles', 'index.json'), 'w'))
    print('index:', len(cars), 'cars +', len(KEEP), 'kept')


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    ids = args or car_ids()
    import carbase
    for cid in ids:
        path = os.path.join(HERE, 'models', cid + '.bbmodel')
        if '--no-model' not in sys.argv:
            try:
                carbase.build(load_spec(cid), os.path.join(HERE, 'models'))
            except Exception:
                import traceback
                print('FAILED', cid)
                traceback.print_exc()
                continue
        if '--no-game' not in sys.argv:
            print('  game:', *convert(path), 'tris')
    if '--no-game' not in sys.argv:
        write_index()


if __name__ == '__main__':
    main()
