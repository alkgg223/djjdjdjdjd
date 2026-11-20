"""Preview renders of a TrafficCraft .bbmodel (Blender/bpy, development only).

usage: bpy-python render_bb.py model.bbmodel out_dir [views|variants|all] [paint=#rrggbb]
"""
import sys, os, math, json, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
import bpy, bmesh
from mathutils import Vector, Matrix
import bb

SRC, OUT = sys.argv[1], sys.argv[2]
MODE = sys.argv[3] if len(sys.argv) > 3 else 'all'
PAINT = next((a.split('=')[1] for a in sys.argv if a.startswith('paint=')), '#a8000c')
os.makedirs(OUT, exist_ok=True)

TRIS, IMG, META = bb.read_bbmodel(SRC)
TEXPATH = os.path.join(tempfile.gettempdir(), 'bbtex_%d.png' % os.getpid())
IMG.save(TEXPATH)
INV = {v: k for k, v in bb.MATS.items()}
LAMP_COL = {'head': (1, 0.97, 0.9), 'high': (0.9, 0.95, 1), 'tail': (1, 0.05, 0.08), 'brake': (1, 0.04, 0.06), 'reverse': (1, 1, 1),
            'ind_l': (1, 0.5, 0.05), 'ind_r': (1, 0.5, 0.05), 'drl': (1, 1, 1), 'marker': (1, 0.5, 0.05), 'plate': (1, 1, 1)}


def G(p):
    """game frame -> Blender frame"""
    return (-p[0], p[2], p[1])


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) / 255 for i in (1, 3, 5))


MATCACHE = {}


def material(mid, lit):
    key = (mid, tuple(sorted(lit)))
    if key in MATCACHE:
        return MATCACHE[key]
    name = INV.get(mid, 'solid')
    m = bpy.data.materials.new('%s_%d' % (name, len(MATCACHE)))
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes['Principled BSDF']
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = IMAGE
    tex.interpolation = 'Closest'
    col = tex.outputs['Color']
    if name in ('paint', 'paint2'):
        tint = nt.nodes.new('ShaderNodeMix')
        tint.data_type = 'RGBA'
        tint.blend_type = 'MULTIPLY'
        tint.inputs['Factor'].default_value = 1.0
        nt.links.new(col, tint.inputs['A'])
        tint.inputs['B'].default_value = hexrgb(PAINT) + (1,)
        # alpha 240 texels are not tinted
        cmp = nt.nodes.new('ShaderNodeMath')
        cmp.operation = 'GREATER_THAN'
        cmp.inputs[1].default_value = 0.97
        nt.links.new(tex.outputs['Alpha'], cmp.inputs[0])
        mix = nt.nodes.new('ShaderNodeMix')
        mix.data_type = 'RGBA'
        nt.links.new(cmp.outputs[0], mix.inputs['Factor'])
        nt.links.new(col, mix.inputs['A'])
        nt.links.new(tint.outputs['Result'], mix.inputs['B'])
        nt.links.new(mix.outputs['Result'], bsdf.inputs['Base Color'])
        bsdf.inputs['Roughness'].default_value = 0.35
        bsdf.inputs['Coat Weight'].default_value = 0.5
    else:
        nt.links.new(col, bsdf.inputs['Base Color'])
        bsdf.inputs['Roughness'].default_value = 0.55
        # cut-out (rim spokes)
        nt.links.new(tex.outputs['Alpha'], bsdf.inputs['Alpha'])
    if name == 'glass':
        bsdf.inputs['Alpha'].default_value = 0.28
        for l in list(nt.links):
            if l.to_socket == bsdf.inputs['Alpha']:
                nt.links.remove(l)
        bsdf.inputs['Roughness'].default_value = 0.05
    if name in LAMP_COL and name in lit:
        nt.links.new(col, bsdf.inputs['Emission Color'])
        bsdf.inputs['Emission Strength'].default_value = 6.0 if name in ('head', 'high', 'reverse') else 4.0
    m.blend_method = 'BLEND' if name == 'glass' else 'CLIP'
    MATCACHE[key] = m
    return m


def build(config, lit=()):
    """config: slot -> index. Returns objects (body + 4 wheel instances)."""
    for o in list(bpy.data.objects):
        if o.get('veh'):
            bpy.data.objects.remove(o)
    keep = []
    wheel_tris = []
    for t in TRIS:
        part = t[0]
        if ':' in part:
            slot, idx = part.split(':')
            if slot == 'wh':
                if int(idx) == config.get('wh', 0):
                    wheel_tris.append(t)
                continue
            if int(idx) != config.get(slot, 0):
                continue
        if part == 'caliper':
            wheel_tris.append(t)
            continue
        keep.append(t)

    def mk(name, tris, M=Matrix()):
        me = bpy.data.meshes.new(name)
        bm = bmesh.new()
        uvl = bm.loops.layers.uv.new('uv')
        mats = {}
        for part, mid, P, UV, n in tris:
            vs = [bm.verts.new(G(p)) for p in P]
            try:
                f = bm.faces.new(vs)
            except ValueError:
                continue
            if mid not in mats:
                mats[mid] = len(mats)
            f.material_index = mats[mid]
            for l, uv in zip(f.loops, UV):
                l[uvl].uv = (uv[0], 1 - uv[1])
        bm.to_mesh(me)
        bm.free()
        for mid in mats:
            me.materials.append(material(mid, lit))
        o = bpy.data.objects.new(name, me)
        o['veh'] = 1
        bpy.context.scene.collection.objects.link(o)
        o.matrix_world = M
        return o
    objs = [mk('body', keep)]
    for w in META['wheels']:
        M = Matrix.Translation(G((w['x'], w['y'], w['z'])))
        if w['mirror']:
            M = M @ Matrix.Diagonal((-1, 1, 1, 1))
        objs.append(mk('wheel', wheel_tris, M))
    return objs


def studio(night=False):
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.use_denoising = True
    sc.view_settings.view_transform = 'Standard'
    sc.view_settings.look = 'None'
    w = bpy.data.worlds.new('w')
    sc.world = w
    w.use_nodes = True
    bg = w.node_tree.nodes['Background']
    bg.inputs['Color'].default_value = (0.02, 0.025, 0.04, 1) if night else (0.86, 0.87, 0.88, 1)
    bg.inputs['Strength'].default_value = 0.6 if night else 0.42
    me = bpy.data.meshes.new('ground')
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=40)
    bm.to_mesh(me)
    g = bpy.data.objects.new('ground', me)
    sc.collection.objects.link(g)
    gm = bpy.data.materials.new('ground')
    gm.use_nodes = True
    gm.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (0.05, 0.05, 0.055, 1) if night else (0.62, 0.62, 0.63, 1)
    gm.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = 0.9
    g.data.materials.append(gm)
    if night:
        sp = bpy.data.objects.new('lamp', bpy.data.lights.new('lamp', 'POINT'))
        sp.data.energy = 300
        sp.data.color = (1, 0.85, 0.6)
        sp.location = (-3, 1, 4)
        sc.collection.objects.link(sp)
        for wx in (-0.6, 0.6):
            s = bpy.data.objects.new('beam', bpy.data.lights.new('beam', 'SPOT'))
            s.data.energy = 220
            s.data.spot_size = math.radians(60)
            s.data.spot_blend = 0.7
            s.location = (wx, 2.45, 0.62)
            s.rotation_euler = (math.radians(82), 0, 0)
            sc.collection.objects.link(s)
    else:
        sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
        sun.data.energy = 2.2
        sun.data.angle = math.radians(20)
        sun.rotation_euler = (math.radians(35), math.radians(15), math.radians(150))
        sc.collection.objects.link(sun)


def camera(loc, target, lens=50, ortho=None):
    sc = bpy.context.scene
    cam = sc.camera
    if cam is None:
        cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
        sc.collection.objects.link(cam)
        sc.camera = cam
    cam.location = loc
    cam.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    cam.data.clip_start = 0.02
    if ortho:
        cam.data.type = 'ORTHO'
        cam.data.ortho_scale = ortho
    else:
        cam.data.type = 'PERSP'


def render(path, size=(900, 520), samples=24):
    sc = bpy.context.scene
    sc.render.resolution_x, sc.render.resolution_y = size
    sc.cycles.samples = samples
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)


def sheet(paths, cols, out):
    from PIL import Image
    ims = [Image.open(p) for p in paths]
    w, h = ims[0].size
    rows = (len(ims) + cols - 1) // cols
    s = Image.new('RGB', (w * cols, h * rows), (230, 230, 230))
    for i, im in enumerate(ims):
        s.paste(im, ((i % cols) * w, (i // cols) * h))
    s.save(out)


bpy.ops.wm.read_factory_settings(use_empty=True)
IMAGE = bpy.data.images.load(TEXPATH)
L = META.get('length', 4.5)
VIEWS = {
    'front34': ((5.0, 6.4, 1.7), (0, 0.2, 0.55), 50),
    'rear34': ((-4.8, -6.0, 1.7), (0, -0.3, 0.6), 50),
    'left': ((-9.5, 0.0, 0.75), (0, 0.0, 0.62), 50),
    'right': ((9.5, 0.0, 0.75), (0, 0.0, 0.62), 50),
    'front': ((0, 10.5, 0.85), (0, 0, 0.6), 60),
    'rear': ((0, -10.5, 0.95), (0, 0, 0.6), 60),
    'top': ((0.001, 0, 12), (0, 0, 0), 40),
    'interior': ((-0.3, -0.33, 1.02), (-0.2, 1.5, 0.82), 18),
    'lamp_front': ((1.4, 4.0, 1.15), (-0.55, 2.0, 0.6), 50),
    'lamp_rear': ((-1.2, -3.6, 1.0), (-0.45, -2.25, 0.7), 50),
}
done = []
if MODE in ('views', 'all'):
    studio()
    build({})
    for name, (loc, tgt, lens) in VIEWS.items():
        camera(loc, tgt, lens)
        p = os.path.join(OUT, 'v_%s.png' % name)
        render(p)
        done.append(p)
    sheet(done, 2, os.path.join(OUT, 'sheet_views.png'))
if MODE in ('night', 'all'):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    IMAGE = bpy.data.images.load(TEXPATH)
    MATCACHE.clear()
    studio(night=True)
    n = []
    for name, lit, view in (('night_front', ('head', 'tail', 'ind_l'), 'front34'), ('night_high', ('head', 'high', 'tail'), 'lamp_front'),
                            ('night_rear', ('tail', 'brake', 'ind_l'), 'rear34'), ('night_reverse', ('tail', 'reverse', 'ind_l', 'ind_r'), 'lamp_rear')):
        build({}, lit)
        loc, tgt, lens = VIEWS[view]
        camera(loc, tgt, lens)
        p = os.path.join(OUT, '%s.png' % name)
        render(p, samples=32)
        n.append(p)
    sheet(n, 2, os.path.join(OUT, 'sheet_night.png'))
if MODE in ('variants', 'all'):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    IMAGE = bpy.data.images.load(TEXPATH)
    MATCACHE.clear()
    studio()
    cat = META.get('catalog', {})
    cams = {'fb': VIEWS['front34'], 'rb': VIEWS['rear34'], 'ex': ((-2.2, -4.6, 0.6), (-0.3, -2.2, 0.35), 50), 'sp': VIEWS['rear34'],
            'hd': ((3.4, 5.2, 2.6), (0, 0.9, 0.6), 50), 'wh': ((-3.4, 2.2, 0.55), (-0.8, 1.29, 0.35), 50), 'sk': VIEWS['left']}
    for slot, opts in cat.items():
        tiles = []
        for i, o in enumerate(opts):
            build({slot: i})
            loc, tgt, lens = cams.get(slot, VIEWS['front34'])
            camera(loc, tgt, lens)
            p = os.path.join(OUT, 'var_%s_%d.png' % (slot, i))
            render(p, (600, 360), 16)
            tiles.append(p)
        sheet(tiles, 3, os.path.join(OUT, 'sheet_%s.png' % slot))
print('done')
