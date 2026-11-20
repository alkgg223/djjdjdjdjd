"""Blender (bpy) helpers for the TrafficCraft high-detail vehicle pipeline.

Blender frame used while modelling: car faces +Y, up +Z, car's LEFT side at -X, y measured from the rear bumper.
Export converts to the game frame: x_game = -x_blender (left = +X), y_game = z_blender, z_game = y_blender - L/2.
"""
import math
import bpy
import bmesh
import numpy as np
from mathutils import Vector, Matrix


# ----------------------------------------------------------------------------------------------------------- curves
class Curve:
    """Monotone cubic (Fritsch-Carlson) interpolation through key points."""

    def __init__(self, pts):
        pts = sorted(pts)
        self.x = np.array([p[0] for p in pts], float)
        self.y = np.array([p[1] for p in pts], float)
        n = len(self.x)
        h = np.diff(self.x)
        d = np.diff(self.y) / h
        m = np.zeros(n)
        m[0], m[-1] = d[0], d[-1]
        for i in range(1, n - 1):
            if d[i - 1] * d[i] <= 0:
                m[i] = 0
            else:
                w1, w2 = 2 * h[i] + h[i - 1], h[i] + 2 * h[i - 1]
                m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
        self.m, self.h = m, h

    def __call__(self, x):
        if x <= self.x[0]:
            return float(self.y[0])
        if x >= self.x[-1]:
            return float(self.y[-1])
        i = int(np.searchsorted(self.x, x) - 1)
        t = (x - self.x[i]) / self.h[i]
        h00, h10, h01, h11 = 2 * t ** 3 - 3 * t ** 2 + 1, t ** 3 - 2 * t ** 2 + t, -2 * t ** 3 + 3 * t ** 2, t ** 3 - t ** 2
        return float(h00 * self.y[i] + h10 * self.h[i] * self.m[i] + h01 * self.y[i + 1] + h11 * self.h[i] * self.m[i + 1])


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


# ----------------------------------------------------------------------------------------------------------- scene
def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


# game material ids (see tools/vehgen/geom.py)
PAINT, SOLID, GLASS, HEAD, HIGH, DRL, TAIL, BRAKE, IND_L, IND_R, REVERSE, INTERIOR_LIGHT, PLATE_LIGHT, BEACON_A, BEACON_B, TAXI_SIGN, MARKER, PAINT2, \
    SCREEN = range(19)
MATS = {}


def mat(name, color, game=SOLID, metal=0.0, rough=0.5, emit=0.0, alpha=1.0, coat=0.0, uv='white', transmission=0.0):
    """A Blender material that also carries how the game should draw it (game material id, atlas region)."""
    if name in MATS:
        return MATS[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes.get('Principled BSDF')
    c = tuple(int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)) + (1,) if isinstance(color, str) else color
    b.inputs['Base Color'].default_value = c
    b.inputs['Metallic'].default_value = metal
    b.inputs['Roughness'].default_value = rough
    if coat > 0:
        b.inputs['Coat Weight'].default_value = coat
        b.inputs['Coat Roughness'].default_value = 0.03
    if emit > 0:
        b.inputs['Emission Color'].default_value = c
        b.inputs['Emission Strength'].default_value = emit
    if transmission > 0:
        b.inputs['Transmission Weight'].default_value = transmission
        b.inputs['IOR'].default_value = 1.45
    if alpha < 1:
        b.inputs['Alpha'].default_value = alpha
    m['game'] = game
    m['color'] = color if isinstance(color, str) else '#ffffff'
    m['uv'] = uv
    m['alpha'] = alpha
    MATS[name] = m
    return m


def new_obj(name, mesh, part='body'):
    o = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(o)
    o['part'] = part
    return o


def add_mat(o, m):
    if m.name not in [s.name for s in o.data.materials]:
        o.data.materials.append(m)
    return [s.name for s in o.data.materials].index(m.name)


def apply_mods(o):
    bpy.context.view_layer.objects.active = o
    for md in list(o.modifiers):
        with bpy.context.temp_override(object=o, active_object=o):
            bpy.ops.object.modifier_apply(modifier=md.name)


def shade_smooth(o, angle=35):
    with bpy.context.temp_override(object=o, active_object=o, selected_objects=[o], selected_editable_objects=[o]):
        bpy.ops.object.shade_smooth_by_angle(angle=math.radians(angle))


def grid_mesh(name, rows, faces_mat=None, close_rows=False):
    """Quad mesh from a 2D array of points rows[i][j] (i = station, j = around the section)."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    V = [[bm.verts.new(p) for p in row] for row in rows]
    for i in range(len(rows) - 1):
        for j in range(len(rows[i]) - 1):
            f = bm.faces.new((V[i][j], V[i + 1][j], V[i + 1][j + 1], V[i][j + 1]))
            if faces_mat:
                f.material_index = faces_mat(i, j)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bm.to_mesh(me)
    bm.free()
    return me


def boolean(o, cutter, op='DIFFERENCE', solver='EXACT'):
    md = o.modifiers.new('bool', 'BOOLEAN')
    md.operation = op
    md.object = cutter
    md.solver = solver
    apply_mods(o)


def prism(name, outline, axis, a0, a1):
    """Extruded polygon: outline is a list of 2D points in the plane perpendicular to axis ('x','y','z'), from a0 to a1."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()

    def P(u, v, w):
        if axis == 'x':
            return (w, u, v)
        if axis == 'y':
            return (u, w, v)
        return (u, v, w)

    b0 = [bm.verts.new(P(u, v, a0)) for u, v in outline]
    b1 = [bm.verts.new(P(u, v, a1)) for u, v in outline]
    n = len(outline)
    for k in range(n):
        bm.faces.new((b0[k], b0[(k + 1) % n], b1[(k + 1) % n], b1[k]))
    bm.faces.new(b0[::-1])
    bm.faces.new(b1)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    o = new_obj(name, me, 'cutter')
    return o


def rrect(w, h, r, seg=4, cx=0.0, cy=0.0):
    r = min(r, w / 2 * 0.999, h / 2 * 0.999)
    pts = []
    for (qx, qy, a0) in ((w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180), (w / 2 - r, -h / 2 + r, 270)):
        for k in range(seg + 1):
            a = math.radians(a0 + 90 * k / seg)
            pts.append((cx + qx + r * math.cos(a), cy + qy + r * math.sin(a)))
    return pts


def ellipse(w, h, seg=32, cx=0.0, cy=0.0):
    return [(cx + w / 2 * math.cos(2 * math.pi * k / seg), cy + h / 2 * math.sin(2 * math.pi * k / seg)) for k in range(seg)]


def revolve(name, profile, seg=32, axis='x', part='body', material=None, center=(0, 0, 0)):
    """Surface of revolution of a profile [(radius, axial)] around an axis through center."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    rings = []
    for k in range(seg):
        a = 2 * math.pi * k / seg
        ca, sa = math.cos(a), math.sin(a)
        ring = []
        for r, ax in profile:
            if axis == 'x':
                p = (center[0] + ax, center[1] + r * ca, center[2] + r * sa)
            elif axis == 'y':
                p = (center[0] + r * ca, center[1] + ax, center[2] + r * sa)
            else:
                p = (center[0] + r * ca, center[1] + r * sa, center[2] + ax)
            ring.append(bm.verts.new(p))
        rings.append(ring)
    for k in range(seg):
        a, b = rings[k], rings[(k + 1) % seg]
        for j in range(len(profile) - 1):
            try:
                bm.faces.new((a[j], b[j], b[j + 1], a[j + 1]))
            except ValueError:
                pass
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    o = new_obj(name, me, part)
    if material:
        add_mat(o, material)
    return o


def subsurf(o, levels=2):
    md = o.modifiers.new('sub', 'SUBSURF')
    md.levels = levels
    md.render_levels = levels
    return md


def join(objs, name):
    with bpy.context.temp_override(active_object=objs[0], selected_editable_objects=objs, selected_objects=objs):
        bpy.ops.object.join()
    objs[0].name = name
    return objs[0]


# ----------------------------------------------------------------------------------------------------------- export
def export_npz(path, L, objects=None):
    """Writes all exported objects' triangles (game frame) with split normals, UVs, colours, game materials and parts."""
    dg = bpy.context.evaluated_depsgraph_get()
    P, N, UVs, C, M, PART = [], [], [], [], [], []
    parts = []
    for o in (objects or bpy.context.scene.objects):
        if o.type != 'MESH' or o.get('part') in (None, 'cutter', 'helper') or o.hide_render:
            continue
        ev = o.evaluated_get(dg)
        me = ev.to_mesh()
        me.calc_loop_triangles()
        mw = o.matrix_world
        nm = mw.to_3x3().inverted().transposed()
        part = o['part']
        if part not in parts:
            parts.append(part)
        pi = parts.index(part)
        uvl = me.uv_layers.active.data if me.uv_layers.active else None
        corner_n = [None] * len(me.loops)
        try:
            cn = me.corner_normals
            corner_n = [Vector(cn[i].vector) for i in range(len(me.loops))]
        except Exception:
            pass
        bbmin = Vector((min(v.co.x for v in me.vertices), min(v.co.y for v in me.vertices), min(v.co.z for v in me.vertices))) if me.vertices else Vector()
        bbmax = Vector((max(v.co.x for v in me.vertices), max(v.co.y for v in me.vertices), max(v.co.z for v in me.vertices))) if me.vertices else Vector()
        for t in me.loop_triangles:
            ms = me.materials[t.material_index] if me.materials else None
            if ms is not None and ms.get('skip'):
                continue
            game = int(ms['game']) if ms and 'game' in ms else SOLID
            col = ms['color'] if ms and 'color' in ms else '#ffffff'
            alpha = float(ms['alpha']) if ms and 'alpha' in ms else 1.0
            uvreg = ms['uv'] if ms and 'uv' in ms else 'white'
            tp, tn, tu = [], [], []
            for k, li in enumerate(t.loops):
                v = me.vertices[t.vertices[k]].co
                w = mw @ v
                n = corner_n[li] if corner_n[li] is not None else t.normal
                nw = (nm @ n).normalized()
                tp.append((-w.x, w.z, w.y - L / 2))
                tn.append((-nw.x, nw.z, nw.y))
                if uvl is not None and o.get('keep_uv'):
                    tu.append(tuple(uvl[li].uv))
                else:
                    tu.append(planar_uv(v, bbmin, bbmax, o.get('uvaxis', 'auto'), t.normal))
            P.append(tp)
            N.append(tn)
            UVs.append(tu)
            c = tuple(int(col[i:i + 2], 16) for i in (1, 3, 5)) + (int(alpha * 255),)
            C.append(c)
            M.append((game, pi, REGIONS.index(uvreg) if uvreg in REGIONS else 0))
        ev.to_mesh_clear()
    np.savez_compressed(path, P=np.array(P, np.float32), N=np.array(N, np.float32), UV=np.array(UVs, np.float32), C=np.array(C, np.uint8),
                        M=np.array(M, np.int32), parts=np.array(parts), regions=np.array(REGIONS))
    return len(P)


REGIONS = ['white', 'grille', 'grille_fine', 'plate', 'led', 'logo', 'headlamp', 'taillamp', 'fabric', 'led_tail', 'reflector', 'sidewall', 'rotor',
           'leather', 'alu', 'carbon', 'vent', 'carpet', 'gauges', 'buttons', 'speaker', 'gear_plate', 'hvac', 'knob', 'tread', 'dash_cluster',
           'dash_screen', 'glass']


def planar_uv(v, mn, mx, axis, n):
    d = mx - mn
    if axis == 'auto':
        a = max(range(3), key=lambda i: abs(n[i]))
        axis = 'xyz'[a]
    if axis == 'x':
        u, w = (v.y - mn.y) / max(1e-6, d.y), (v.z - mn.z) / max(1e-6, d.z)
    elif axis == 'y':
        u, w = (v.x - mn.x) / max(1e-6, d.x), (v.z - mn.z) / max(1e-6, d.z)
    else:
        u, w = (v.x - mn.x) / max(1e-6, d.x), (v.y - mn.y) / max(1e-6, d.y)
    return (u, 1 - w)


# ----------------------------------------------------------------------------------------------------------- QC render
def studio(night=False):
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 48
    sc.cycles.use_denoising = True
    sc.view_settings.view_transform = 'AgX'
    w = bpy.data.worlds.new('w')
    sc.world = w
    w.use_nodes = True
    nt = w.node_tree
    bg = nt.nodes['Background']
    sky = nt.nodes.new('ShaderNodeTexSky')
    try:
        sky.sky_type = 'HOSEK_WILKIE'
    except Exception:
        pass
    sky.sun_direction = (0.4, -0.6, 0.7)
    nt.links.new(sky.outputs['Color'], bg.inputs['Color'])
    bg.inputs['Strength'].default_value = 0.02 if night else 0.55
    # ground
    me = bpy.data.meshes.new('ground')
    bm = bmesh.new()
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=30)
    bm.to_mesh(me)
    g = new_obj('ground', me, 'helper')
    g.data.materials.append(mat('ground', '#56595e', rough=0.85))
    if not night:
        sun = bpy.data.objects.new('sun', bpy.data.lights.new('sun', 'SUN'))
        sun.data.energy = 3.2
        sun.data.angle = math.radians(8)
        sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(140))
        sc.collection.objects.link(sun)
        a = bpy.data.objects.new('soft', bpy.data.lights.new('soft', 'AREA'))
        a.data.energy = 900
        a.data.size = 8
        a.location = (-4, 6, 6)
        a.rotation_euler = (math.radians(-40), math.radians(-30), 0)
        sc.collection.objects.link(a)


def camera(loc, target, lens=50):
    sc = bpy.context.scene
    cam = sc.camera
    if cam is None:
        cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
        sc.collection.objects.link(cam)
        sc.camera = cam
    cam.location = loc
    d = Vector(target) - Vector(loc)
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    cam.data.clip_start = 0.02
    return cam


def render(path, size=(960, 560), samples=48):
    sc = bpy.context.scene
    sc.render.resolution_x, sc.render.resolution_y = size
    sc.cycles.samples = samples
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
