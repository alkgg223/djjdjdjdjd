import sys, os, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import specs, vehicle, render, dash, bike
OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/vehout'
only = sys.argv[2].split(',') if len(sys.argv) > 2 and not sys.argv[2].startswith('--') else None
quick = '--quick' in sys.argv
noprev = '--noprev' in sys.argv
for d in ('preview', 'mesh', 'icon'):
    os.makedirs(OUT + '/' + d, exist_ok=True)
dmeta = dash.build_dash_atlas(specs.SPECS, OUT + '/dash.png')
render.DASHA = None
for S in specs.SPECS:
    if only and S['id'] not in only:
        continue
    t = time.time()
    B = bike if S.get('bike') else vehicle
    mesh, wm, meta = B.build(S, verbose=True)
    meta['dash'] = dmeta[S['id']]
    import sounds as SND
    E = SND.ENGINES[S['engine']]
    meta['engine'] = dict(type=S['engine'], idle=E['idle'], drive=E['drive'], cyl=E['cyl'], diesel=E['clatter'] > 0)
    meta['paint2'] = S.get('paint2', '#f2f2f2')
    meta['horn'] = S.get('horn', {'truck': 'truck', 'sports': 'high', 'pickup': 'low', 'van': 'low'}.get(S['cat'], 'car'))
    meta['siren'] = S['cat'] == 'emergency'
    if 'backfire' in S:
        meta['backfire'] = S['backfire']
    import custom
    custom.livery_uv(mesh, S['L'], S['W'], S['H'])
    vehicle.export(mesh, wm, meta, f"{OUT}/mesh/{S['id']}.tcm", f"{OUT}/mesh/{S['id']}.json")
    if not quick:
        m1, w1, _ = B.build(S, lod=1)
        custom.livery_uv(m1, S['L'], S['W'], S['H'])
        vehicle.export(m1, w1, meta, f"{OUT}/mesh/{S['id']}_lod1.tcm")
        print('  lod1 tris', m1.count() + w1.count() * len(meta['wheels']))
    col = tuple(int(S.get('preview', S.get('colors', ['#c0262c'])[0])[i:i + 2], 16) for i in (1, 3, 5))
    if quick:
        render.quick(mesh, wm, meta, f"{OUT}/preview/{S['id']}_q.png", paint=col)
    elif not noprev:
        render.sheet(mesh, wm, meta, f"{OUT}/preview/{S['id']}.png", paint=col)
    render.icon(mesh, wm, meta, f"{OUT}/icon/{S['id']}.png", paint=col)
    print(S['id'], meta['tris'], 'tris', round(time.time() - t, 1), 's', flush=True)
