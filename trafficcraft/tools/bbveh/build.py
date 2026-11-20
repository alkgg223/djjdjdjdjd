"""Build every car: cars/<id>.py (SPEC) -> models/<id>.bbmodel (Blockbench source) -> game assets.

usage: python3 build.py [ids...] [--no-model] [--no-game]
  --no-model : skip regenerating the .bbmodel (use the existing, possibly hand-edited, file)
"""
import os, sys, json, importlib.util, shutil
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
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
    return cid, n


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    ids = args or car_ids()
    import carbase
    for cid in ids:
        path = os.path.join(HERE, 'models', cid + '.bbmodel')
        if '--no-model' not in sys.argv:
            carbase.build(load_spec(cid), os.path.join(HERE, 'models'))
        if '--no-game' not in sys.argv:
            print('  game:', *convert(path), 'tris')


if __name__ == '__main__':
    main()
