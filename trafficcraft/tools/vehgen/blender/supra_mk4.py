"""Toyota Supra MK4 (A80, 1993-2002) - clean low-poly model built in Blender (bpy) and exported to the game.

Style: simple, smooth-shaded low-poly body with separately modelled details (lamps with inner elements, grille, glass,
mirrors, wing, exhaust, wheels with spokes/discs/calipers, full interior).
Dimensions: L 4515, W 1810, H 1275, wheelbase 2550, tyres 235/45R17 front, 255/40R17 rear.
Usage:  bpy-python supra_mk4.py <out_dir> [qc] [night]
"""
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy, bmesh
import numpy as np
from mathutils import Vector, Matrix
from mathutils.bvhtree import BVHTree
from blib import *

OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/supra'
QC = 'qc' in sys.argv
FACET = 'facet' in sys.argv      # Blue-Seoul-like faceted low-poly look (flat shaded, coarse stations)
os.makedirs(OUT, exist_ok=True)
reset()

L, W, H, WB = 4.515, 1.81, 1.275, 2.55
YR, YF = 1.0, 3.55          # axle positions from the rear
R = 0.322                   # tyre radius
TRACK = 0.765               # half track (wheel centre plane)

# ----------------------------------------------------------------------------------------------------------- materials
M_PAINT = mat('paint', '#b0101c', PAINT, metal=0.05, rough=0.42, coat=0.0) if FACET else mat('paint', '#a00d18', PAINT, metal=0.2, rough=0.22, coat=1.0)
M_GLASS = mat('glass', '#26313a', GLASS, rough=0.02, alpha=0.32, transmission=0.85)
M_SKIP = mat('glass_inner', '#26313a', GLASS, rough=0.02, alpha=0.0, transmission=1.0)
M_SKIP['skip'] = 1
M_BLACK = mat('black_trim', '#101112', SOLID, rough=0.5)
M_GLOSSBLK = mat('gloss_black', '#0b0c0d', SOLID, rough=0.12)
M_RUBBER = mat('rubber', '#141516', SOLID, rough=0.85)
M_UNDER = mat('underbody', '#0d0d0e', SOLID, rough=0.9)
M_RIM = mat('sheet_edge', '#151617', SOLID, rough=0.6)
M_HEADLINER = mat('headliner', '#7d7b77', SOLID, rough=0.95, uv='fabric')
M_CARPET = mat('carpet', '#1d1d1f', SOLID, rough=1.0, uv='carpet')
M_CHROME = mat('chrome', '#dfe2e5', SOLID, metal=1.0, rough=0.08)
M_HOUSING = mat('lamp_housing', '#2c2f34', SOLID, metal=0.7, rough=0.3)
M_CLEAR = mat('lens_clear', '#ffffff', GLASS, rough=0.0, alpha=0.12, transmission=1.0)
M_HEAD = mat('head_lamp', '#f2f5f8', HEAD, metal=0.0, rough=0.1, uv='headlamp')
M_IND_L = mat('ind_l', '#ff8a10', IND_L, rough=0.15, alpha=0.9)
M_IND_R = mat('ind_r', '#ff8a10', IND_R, rough=0.15, alpha=0.9)
M_TAIL = mat('tail', '#9a0010', TAIL, rough=0.12, uv='taillamp')
M_BRAKE = mat('brake', '#c4001a', BRAKE, rough=0.12, uv='taillamp')
M_REV = mat('reverse', '#e9ecee', REVERSE, rough=0.1)
M_TAILHOUSE = mat('tail_housing', '#1b0d10', SOLID, rough=0.25)
M_GRILLE = mat('grille', '#060607', SOLID, rough=0.5, uv='grille')
M_REFLECT = mat('reflector', '#a3101b', SOLID, rough=0.3, uv='reflector')
M_LEATHER = mat('leather', '#1e1e20', SOLID, rough=0.6, uv='leather')
M_FABRIC = mat('seat_fabric', '#2c2d30', SOLID, rough=0.95, uv='fabric')
M_DASH = mat('dash', '#232426', SOLID, rough=0.75)
M_DASH2 = mat('dash_lower', '#38393c', SOLID, rough=0.8)
M_ALU = mat('alu', '#a7abb0', SOLID, metal=0.9, rough=0.3, uv='alu')
M_GAUGE = mat('gauges', '#0a0b0c', SCREEN, rough=0.2, uv='gauges')
M_HVAC = mat('hvac', '#1a1b1d', SOLID, rough=0.6, uv='hvac')
M_BUTTONS = mat('buttons', '#1a1b1d', SOLID, rough=0.6, uv='buttons')
M_TYRE = mat('tyre', '#18191a', SOLID, rough=0.9, uv='sidewall')
M_TREAD = mat('tread', '#141516', SOLID, rough=0.95, uv='tread')
M_WHEEL = mat('rim', '#c8ccd1', SOLID, metal=0.9, rough=0.22)
M_ROTOR = mat('rotor', '#6f7277', SOLID, metal=0.8, rough=0.4, uv='rotor')
M_CALIPER = mat('caliper', '#8d9197', SOLID, metal=0.4, rough=0.4)
M_MIRROR = mat('mirror_glass', '#b9c2c9', SOLID, metal=1.0, rough=0.02)
LAMPS = [(M_HEAD, '#fff6e6', 14), (M_TAIL, '#ff0a1a', 4), (M_REV, '#ffffff', 0), (M_BRAKE, '#ff0a1a', 0), (M_IND_L, '#ff8a10', 0),
         (M_IND_R, '#ff8a10', 0)]

# ----------------------------------------------------------------------------------------------------------- profiles
bot = Curve([(0.0, 0.36), (0.1, 0.3), (0.3, 0.255), (0.6, 0.225), (1.45, 0.2), (3.0, 0.2), (3.9, 0.215), (4.2, 0.218), (4.42, 0.222),
             (4.515, 0.26)])
# top edge of the body side (deck lip -> rear quarter -> door belt -> front fender -> nose)
belt = Curve([(0.0, 0.84), (0.05, 0.915), (0.18, 0.952), (0.55, 0.968), (0.9, 0.968), (1.5, 0.952), (2.2, 0.926), (2.9, 0.897), (3.1, 0.88),
              (3.55, 0.815), (3.9, 0.762), (4.2, 0.7), (4.4, 0.63), (4.515, 0.55)])
# half width of the body at its widest line (plan view), haunches over both axles
hw = Curve([(0.0, 0.66), (0.05, 0.76), (0.15, 0.84), (0.35, 0.886), (0.7, 0.905), (1.1, 0.905), (1.5, 0.893), (2.0, 0.883), (2.7, 0.883),
            (3.2, 0.893), (3.6, 0.9), (3.95, 0.885), (4.2, 0.845), (4.35, 0.78), (4.45, 0.68), (4.5, 0.57), (4.515, 0.48)])
zsh = Curve([(0.0, 0.6), (1.0, 0.6), (2.2, 0.56), (3.5, 0.54), (4.2, 0.5), (4.515, 0.45)])
# greenhouse
CABIN0, CABIN1 = 0.8, 3.1
roofz = Curve([(CABIN0, 0.968), (1.0, 1.05), (1.3, 1.15), (1.6, 1.215), (1.9, 1.255), (2.2, 1.272), (2.42, 1.266), (2.6, 1.232),
               (2.8, 1.135), (2.98, 0.99), (CABIN1, 0.88)])
roofw = Curve([(CABIN0, 0.72), (1.0, 0.67), (1.4, 0.61), (2.0, 0.57), (2.5, 0.57), (2.8, 0.61), (CABIN1, 0.69)])
crown = Curve([(0.0, 0.012), (0.8, 0.02), (3.1, 0.026), (3.6, 0.034), (4.2, 0.024), (4.515, 0.006)])
HATCH0, HATCH1 = 0.86, 1.62       # hatch glass
WS0, WS1 = 2.62, 3.04             # windscreen
QW0, QW1, BP1 = 1.24, 1.69, 1.76  # quarter glass, B-pillar
DOOR0, DOOR1 = 1.73, 3.1


def gh(y):
    if y <= CABIN0 or y >= CABIN1:
        return 0.0
    return max(0.0, roofz(y) - belt(y))


def section(y):
    """Half section (x>0), from the floor centre round the side to the roof / hood centre. 14 points."""
    w, b, t, s = hw(y), bot(y), belt(y), zsh(y)
    g = gh(y)
    k = smooth(0.0, 0.08, g)
    pts = [(0.0, b + 0.02), (w * 0.55, b + 0.012), (w * 0.87, b), (w * 0.95, b + 0.028), (w * 0.99, b + (s - b) * 0.45), (w, s),
           (w - 0.012, s + (t - s) * 0.5), (w - 0.038, t - 0.026), (w - 0.072, t - 0.005), (w - 0.1, t + 0.004)]
    cr = crown(y)
    hz = lambda x: t + 0.004 + cr * (1 - (x / max(w - 0.1, 1e-3)) ** 2)
    r = roofw(min(max(y, CABIN0), CABIN1))
    rz = t + g
    hood = [(w * 0.74, hz(w * 0.74)), (w * 0.52, hz(w * 0.52)), (w * 0.26, hz(w * 0.26)), (0.0, hz(0.0))]
    cab = [(r + 0.045, rz - 0.07), (r, rz - 0.022), (r * 0.52, rz - 0.005), (0.0, rz)]
    for (xh, zh), (xc, zc) in zip(hood, cab):
        pts.append((lerp(xh, xc, k), lerp(zh, max(zc, zh), k)))
    return pts


GLTOP = 10   # index of the side glass top point


def stations():
    ys = (list(np.arange(0.0, L, 0.2)) + [0.06, L - 0.06, L]) if FACET else (list(np.arange(0.0, L, 0.075)) + [0.02, 0.05, 0.1, L - 0.04, L - 0.015, L])
    ys += [CABIN0, HATCH0, HATCH1, WS0, WS1, CABIN1, QW0, QW1, BP1, DOOR0, DOOR1, 2.2, 2.42]
    for ya in (YR, YF):
        ys += [ya - R - 0.05, ya + R + 0.05, ya]
    ys = sorted(set(round(y, 4) for y in ys if 0 <= y <= L))
    out = []
    for y in ys:
        if not out or y - out[-1] > (0.05 if FACET else 0.012):
            out.append(y)
    return out


def build_shell():
    ys = stations()
    rows = [[(-x, y, z) for (x, z) in section(y)] for y in ys]
    # flat-ish end caps: one ring pulled in, one on the centre plane (the mirror merges it)
    t0 = [(x * 0.8, -0.012, z) for (x, _, z) in rows[0]]
    t1 = [(0.0, -0.016, z) for (_, _, z) in rows[0]]
    n0 = [(x * 0.8, L + 0.01, z) for (x, _, z) in rows[-1]]
    n1 = [(0.0, L + 0.013, z) for (_, _, z) in rows[-1]]
    rows = [t1, t0] + rows + [n0, n1]
    ys_all = [-0.016, -0.012] + ys + [L + 0.01, L + 0.013]

    def fm(i, j):
        y = (ys_all[i] + ys_all[i + 1]) / 2
        if j < 2:
            return 4
        if j == 9 and CABIN0 + 0.02 < y < CABIN1 - 0.01:      # side glass band
            if QW0 < y < QW1 or BP1 < y < CABIN1:
                return 1
            if QW1 < y < BP1:
                return 2
            return 0
        if j == 10 and WS0 - 0.02 < y < CABIN1:              # A-pillar
            return 2
        if j >= 11 and (WS0 < y < WS1 or HATCH0 < y < HATCH1):
            return 1
        return 0

    me = grid_mesh('body', rows, fm)
    o = new_obj('body', me, 'body')
    for m in (M_PAINT, M_GLASS, M_BLACK, M_RIM, M_UNDER, M_HEADLINER, M_SKIP, M_BLACK, M_RIM, M_CARPET):
        o.data.materials.append(m)
    bm = bmesh.new()
    bm.from_mesh(o.data)
    cl = bm.edges.layers.float.get('crease_edge') or bm.edges.layers.float.new('crease_edge')
    for e in bm.edges:
        fs = e.link_faces
        if len(fs) == 2 and fs[0].material_index != fs[1].material_index:
            e[cl] = 1.0
            e.smooth = False
    bm.to_mesh(o.data)
    bm.free()
    mir = o.modifiers.new('mirror', 'MIRROR')
    mir.use_clip = True
    mir.use_mirror_merge = True
    mir.merge_threshold = 0.003
    if not FACET:
        subsurf(o, 1)
    return o


body = build_shell()
apply_mods(body)
for ya in (YR, YF):
    cut = bpy.data.objects.new('archcut', bpy.data.meshes.new('archcut'))
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=18 if FACET else 48, radius1=R + 0.04, radius2=R + 0.04, depth=2.4)
    bm.to_mesh(cut.data)
    bm.free()
    bpy.context.scene.collection.objects.link(cut)
    cut.rotation_euler = (0, math.radians(90), 0)
    cut.location = (0, ya, R + 0.005)
    cut['part'] = 'cutter'
    boolean(body, cut)
    bpy.data.objects.remove(cut)
sol = body.modifiers.new('sol', 'SOLIDIFY')
sol.thickness = 0.01
sol.offset = -1
sol.material_offset = 5
sol.material_offset_rim = 3
sol.use_even_offset = False
sol.use_quality_normals = True
sol.nonmanifold_thickness_mode = "CONSTRAINTS"
apply_mods(body)
shade_smooth(body, 6 if FACET else 34)

# ----------------------------------------------------------------------------------------------------------- doors
def door_outline(gap=0.0):
    top = []
    for y in np.linspace(DOOR0 + 0.01, DOOR1, 24):
        top.append((y, section(y)[GLTOP][1] + 0.004))
    pts = [(DOOR1 + gap, 0.27 - gap)] + [(y + gap * 0, z + gap) for (y, z) in reversed(top)] + [(DOOR0 - gap, top[0][1] + gap), (DOOR0 - gap, 0.27 - gap)]
    return pts


doors = {}
for sx, pname in ((-1, 'door_fl'), (1, 'door_fr')):
    a0, a1 = (-1.3, -0.45) if sx < 0 else (0.45, 1.3)
    piece = body.copy()
    piece.data = body.data.copy()
    bpy.context.scene.collection.objects.link(piece)
    c_in = prism('dcut_in', door_outline(0.0), 'x', a0, a1)
    boolean(piece, c_in, 'INTERSECT')
    c_out = prism('dcut_out', door_outline(0.004), 'x', a0, a1)
    boolean(body, c_out, 'DIFFERENCE')
    bpy.data.objects.remove(c_in)
    bpy.data.objects.remove(c_out)
    piece.name = pname
    piece['part'] = pname
    doors[pname] = piece

DG = bpy.context.evaluated_depsgraph_get()


def make_bvh():
    objs = [body] + list(doors.values())
    bm = bmesh.new()
    for o in objs:
        bm.from_mesh(o.data)
    b = BVHTree.FromBMesh(bm)
    bm.free()
    return b


BVH = make_bvh()
VIEWS = {
    'L': (lambda u, v: Vector((-3.0, u, v)), Vector((1, 0, 0))),
    'R': (lambda u, v: Vector((3.0, u, v)), Vector((-1, 0, 0))),
    'B': (lambda u, v: Vector((u, -3.0, v)), Vector((0, 1, 0))),
    'F': (lambda u, v: Vector((u, L + 3.0, v)), Vector((0, -1, 0))),
    'T': (lambda u, v: Vector((u, v, 4.0)), Vector((0, 0, -1))),
}


def cast(view, u, v):
    f, d = VIEWS[view]
    loc, n, _, _ = BVH.ray_cast(f(u, v), d)
    if loc is None:
        return None, None
    if n.dot(d) > 0:
        n = -n
    return loc, n


def resample(poly, n, closed=True):
    P = [np.array(p, float) for p in poly]
    if closed:
        P = P + [P[0]]
    seg = [np.linalg.norm(P[i + 1] - P[i]) for i in range(len(P) - 1)]
    tot = sum(seg)
    out = []
    cnt = n if closed else n - 1
    for k in range(n):
        d = tot * k / cnt
        i = 0
        while i < len(seg) - 1 and d > seg[i]:
            d -= seg[i]
            i += 1
        t = d / max(seg[i], 1e-9)
        out.append(tuple(P[i] + (P[i + 1] - P[i]) * min(1, t)))
    return out


def chaikin(poly, it=2):
    for _ in range(it):
        out = []
        for i in range(len(poly)):
            a, b = np.array(poly[i]), np.array(poly[(i + 1) % len(poly)])
            out += [tuple(a * 0.75 + b * 0.25), tuple(a * 0.25 + b * 0.75)]
        poly = out
    return poly


def finish(name, bm, mats, part, smooth_angle=50):
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces) if False else None
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    o = new_obj(name, me, part)
    for m in mats:
        o.data.materials.append(m)
    shade_smooth(o, smooth_angle)
    return o


def _face(bm, vs, ref_n, mi=0):
    if any(v is None for v in vs):
        return None
    try:
        f = bm.faces.new(vs)
    except ValueError:
        return None
    f.normal_update()
    if f.normal.dot(ref_n) < 0:
        f.normal_flip()
    f.material_index = mi
    return f


def decal(name, outline, view, off, material, part='body', rings=(1.0, 0.72, 0.45, 0.2), n=40, wall=None, wall_mat=None, centre=None):
    """Polygon (2D, in the view plane) projected onto the car surface and lifted by `off`. Optional side wall down to `wall`."""
    ol = resample(outline, n)
    c = np.array(centre if centre is not None else np.mean(np.array(ol), axis=0))
    bm = bmesh.new()
    nref = -VIEWS[view][1]
    R_ = []
    for s in rings:
        ring = []
        for p in ol:
            q = c + (np.array(p) - c) * s
            loc, nn = cast(view, q[0], q[1])
            ring.append(bm.verts.new(loc + nn * off) if loc is not None else None)
        R_.append(ring)
    loc, nn = cast(view, c[0], c[1])
    cv = bm.verts.new(loc + nn * off) if loc is not None else None
    for a, b in zip(R_[:-1], R_[1:]):
        for k in range(n):
            _face(bm, (a[k], a[(k + 1) % n], b[(k + 1) % n], b[k]), nref)
    for k in range(n):
        _face(bm, (R_[-1][k], R_[-1][(k + 1) % n], cv), nref)
    mats = [material]
    if wall is not None:
        base = []
        for p in ol:
            loc, nn = cast(view, p[0], p[1])
            base.append(bm.verts.new(loc + nn * wall) if loc is not None else None)
        for k in range(n):
            a, b = R_[0][k], R_[0][(k + 1) % n]
            c0, c1 = base[k], base[(k + 1) % n]
            if None in (a, b, c0, c1):
                continue
            mid = (a.co + b.co) / 2 - Vector(np.r_[c, 0][:3] if False else (0, 0, 0))
            f = _face(bm, (a, b, c1, c0), nref, 1)
            if f is not None:
                # wall faces point away from the decal centre
                cc = sum((v.co for v in R_[-1] if v is not None), Vector()) / max(1, len([v for v in R_[-1] if v is not None]))
                if f.normal.dot(f.calc_center_median() - cc) < 0:
                    f.normal_flip()
        mats.append(wall_mat or M_BLACK)
    return finish(name, bm, mats, part)


def ribbon(name, poly, view, width, off, material, part='body', closed=False, step=0.015):
    """Thin strip (panel gap / trim) along a 2D polyline in the view plane, projected onto the surface."""
    P = [np.array(p, float) for p in poly]
    length = sum(np.linalg.norm(P[i + 1] - P[i]) for i in range(len(P) - 1)) + (np.linalg.norm(P[0] - P[-1]) if closed else 0)
    n = max(4, int(length / step))
    pts = [np.array(p) for p in resample(poly, n, closed)]
    bm = bmesh.new()
    nref = -VIEWS[view][1]
    A, B = [], []
    for i, p in enumerate(pts):
        if closed:
            d = pts[(i + 1) % n] - pts[i - 1]
        else:
            d = pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)]
        d = d / max(np.linalg.norm(d), 1e-9)
        nn2 = np.array((-d[1], d[0]))
        for side, lst in ((1, A), (-1, B)):
            q = p + nn2 * width / 2 * side
            loc, nn = cast(view, q[0], q[1])
            lst.append(bm.verts.new(loc + nn * off) if loc is not None else None)
    for i in range(n if closed else n - 1):
        j = (i + 1) % n
        _face(bm, (A[i], A[j], B[j], B[i]), nref)
    return finish(name, bm, [material], part)


def orient(n, up=Vector((0, 0, 1))):
    z = Vector(n).normalized()
    if abs(z.dot(up)) > 0.95:
        up = Vector((0, 1, 0))
    x = up.cross(z).normalized()
    y = z.cross(x)
    return Matrix((x, y, z)).transposed()


def place(o, loc, normal, up=Vector((0, 0, 1)), scale=(1, 1, 1)):
    o.matrix_world = Matrix.Translation(loc) @ orient(normal, up).to_4x4() @ Matrix.Diagonal((scale[0], scale[1], scale[2], 1))
    return o


def cup(name, r, depth, loc, normal, mat_cup, mat_core=None, core=0.0, part='body', seg=28, up=Vector((0, 0, 1)), scale=(1, 1)):
    """Lamp reflector bowl (axis = normal) with an optional emissive core disc."""
    prof = [(r, 0.0), (r * 0.96, -depth * 0.35), (r * 0.75, -depth * 0.8), (r * 0.45, -depth), (0.0, -depth)]
    o = revolve(name, prof, seg, 'z', part, mat_cup)
    place(o, loc, normal, up, (scale[0], scale[1], 1))
    shade_smooth(o, 60)
    out = [o]
    if mat_core is not None and core > 0:
        c = revolve(name + '_core', [(0.0, -depth * 0.55), (core * 0.7, -depth * 0.58), (core, -depth * 0.72)], seg, 'z', part, mat_core)
        place(c, loc, normal, up, (scale[0], scale[1], 1))
        shade_smooth(c, 60)
        out.append(c)
    return out


def rbox(name, size, mat_, M=Matrix(), bevel=0.015, part='body', seg=2):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * size[0], v.co.y * size[1], v.co.z * size[2]))
    bm.to_mesh(me)
    bm.free()
    o = new_obj(name, me, part)
    add_mat(o, mat_)
    if bevel > 0:
        md = o.modifiers.new('bv', 'BEVEL')
        md.width = min(bevel, min(size) * 0.45)
        md.segments = seg
        md.limit_method = 'NONE'
        apply_mods(o)
    o.matrix_world = M
    shade_smooth(o, 40)
    return o


def T(x, y, z):
    return Matrix.Translation((x, y, z))


def Rx(a):
    return Matrix.Rotation(math.radians(a), 4, 'X')


def Ry(a):
    return Matrix.Rotation(math.radians(a), 4, 'Y')


def Rz(a):
    return Matrix.Rotation(math.radians(a), 4, 'Z')


def extrude(name, outline, axis, a0, a1, mat_, part='body', bevel=0.0, M=Matrix()):
    o = prism(name, outline, axis, a0, a1)
    o['part'] = part
    add_mat(o, mat_)
    if bevel > 0:
        md = o.modifiers.new('bv', 'BEVEL')
        md.width = bevel
        md.segments = 2
        md.limit_method = 'ANGLE'
        apply_mods(o)
    o.matrix_world = M
    shade_smooth(o, 40)
    return o


def mirror_x(o, name=None):
    c = o.copy()
    c.data = o.data.copy()
    bpy.context.scene.collection.objects.link(c)
    c.name = name or o.name + '_r'
    c.matrix_world = Matrix.Diagonal((-1, 1, 1, 1)) @ o.matrix_world
    me = c.data
    me.transform(c.matrix_world)
    c.matrix_world = Matrix()
    me.flip_normals()
    return c


DETAILS = []

# ----------------------------------------------------------------------------------------------------------- panel lines
for sx, view in ((-1, 'L'), (1, 'R')):
    dp = 'door_fl' if sx < 0 else 'door_fr'
    # hood shut lines (top view)
    hood = [(sx * 0.64, 3.12), (sx * 0.655, 3.55), (sx * 0.61, 3.95), (sx * 0.5, 4.25), (sx * 0.3, 4.4), (0.0, 4.43)]
    DETAILS.append(ribbon('hoodline', hood, 'T', 0.006, 0.0015, M_GLOSSBLK))
    # front bumper seam behind the front wheel and above the arch
    DETAILS.append(ribbon('fseam', [(4.02, 0.3), (4.06, 0.55), (4.1, 0.7)], view, 0.005, 0.0015, M_GLOSSBLK))
    # hatch: side shut lines on the deck
    DETAILS.append(ribbon('hatchside', [(sx * 0.66, 0.0), (sx * 0.69, 0.4), (sx * 0.72, CABIN0 + 0.02)], 'T', 0.006, 0.0015, M_GLOSSBLK))
    # fuel door (left rear quarter on the A80 is on the driver's... game filler is on the right)
    if sx > 0:
        fd = [(0.82 + 0.06 * math.cos(a), 0.84 + 0.055 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 24, endpoint=False)]
        DETAILS.append(ribbon('fueldoor', fd, view, 0.004, 0.0015, M_GLOSSBLK, closed=True))
    # door handle (flush, top rear of the door)
    DETAILS.append(decal('handle', rrect(0.13, 0.026, 0.012, 3, 1.9, 0.855), view, 0.003, M_BLACK, part=dp, wall=0.0, wall_mat=M_BLACK))
    # side marker lamps near the nose
    DETAILS.append(decal('marker', rrect(0.11, 0.035, 0.016, 3, 4.25, 0.47), view, 0.003, M_IND_L if sx < 0 else M_IND_R, wall=0.0))
    # black rocker strip
    DETAILS.append(ribbon('rocker', [(1.42, 0.245), (3.12, 0.245)], view, 0.035, 0.002, M_BLACK))
# rear hatch line across the tail above the lamps
DETAILS.append(ribbon('hatchrear', [(-0.66, 0.855), (-0.3, 0.862), (0.3, 0.862), (0.66, 0.855)], 'B', 0.006, 0.0015, M_GLOSSBLK))
DETAILS.append(ribbon('fbumper_top', [(-0.42, 0.56), (0.0, 0.575), (0.42, 0.56)], 'F', 0.005, 0.0015, M_GLOSSBLK))

# ----------------------------------------------------------------------------------------------------------- headlamps
for sx in (-1, 1):
    out = [(-0.36, 4.475), (-0.6, 4.405), (-0.76, 4.29), (-0.83, 4.14), (-0.83, 4.03), (-0.77, 4.01), (-0.64, 4.1), (-0.48, 4.28),
           (-0.38, 4.39)]
    out = chaikin([(sx * x, y) for x, y in out], 2)
    if sx > 0:
        out = out[::-1]
    DETAILS.append(decal('head_housing', out, 'T', 0.002, M_HOUSING, rings=(1.0, 0.75, 0.5, 0.25)))
    for (x, y, r) in ((-0.47, 4.39, 0.045), (-0.6, 4.3, 0.043), (-0.725, 4.175, 0.038)):
        loc, nn = cast('T', sx * x, y)
        if loc is not None:
            DETAILS += cup('projector', r, 0.008, loc + nn * 0.011, nn, M_CHROME, M_HEAD, r * 0.55)
    DETAILS.append(decal('head_lens', out, 'T', 0.014, M_CLEAR, wall=0.001, wall_mat=M_HOUSING, rings=(1.0, 0.75, 0.5, 0.25)))
    # front corner indicators + fog lamps in the bumper
    DETAILS.append(decal('ind_front', rrect(0.15, 0.045, 0.02, 3, sx * 0.7, 0.47), 'F', 0.004, M_IND_L if sx < 0 else M_IND_R, wall=0.0))
    fl = ellipse(0.085, 0.075, 24, sx * 0.64, 0.335)
    DETAILS.append(decal('fog_bezel', ellipse(0.11, 0.095, 24, sx * 0.64, 0.335), 'F', 0.003, M_BLACK, wall=0.0))
    loc, nn = cast('F', sx * 0.64, 0.335)
    if loc is not None:
        DETAILS += cup('fog', 0.038, 0.01, loc + nn * 0.013, nn, M_CHROME, M_HEAD, 0.02)

# ----------------------------------------------------------------------------------------------------------- front mouth
mouth = rrect(1.04, 0.15, 0.07, 6, 0.0, 0.335)
DETAILS.append(decal('mouth', mouth, 'F', 0.002, M_GRILLE, n=64, rings=(1.0, 0.8, 0.6, 0.4, 0.2)))
DETAILS.append(ribbon('mouth_frame', mouth, 'F', 0.022, 0.004, M_GLOSSBLK, closed=True))
DETAILS.append(decal('nose_slot', rrect(0.32, 0.03, 0.014, 3, 0.0, 0.5), 'F', 0.002, M_GRILLE, wall=0.0))
DETAILS.append(ribbon('front_lip', [(-0.78, 0.258), (-0.4, 0.236), (0.0, 0.232), (0.4, 0.236), (0.78, 0.258)], 'F', 0.03, 0.003, M_BLACK))

# ----------------------------------------------------------------------------------------------------------- rear
TAIL_Z = 0.715
for sx in (-1, 1):
    hx = sx * 0.535
    DETAILS.append(decal('tail_housing', rrect(0.52, 0.19, 0.09, 6, hx, TAIL_Z), 'B', 0.003, M_TAILHOUSE, n=48, wall=0.0))
    for (x, kind) in ((sx * 0.66, 'outer'), (sx * 0.41, 'inner')):
        loc, nn = cast('B', x, TAIL_Z)
        if loc is None:
            continue
        DETAILS += cup('tail_ring', 0.074, 0.012, loc + nn * 0.016, nn, M_CHROME)
        lens = revolve('tail_lens', [(0.0, 0.0), (0.03, -0.002), (0.066, -0.008), (0.07, -0.012)], 32, 'z', 'body', M_TAIL)
        place(lens, loc + nn * 0.016, nn)
        shade_smooth(lens, 60)
        DETAILS.append(lens)
        core_m = M_BRAKE if kind == 'outer' else M_REV
        core = revolve('tail_core', [(0.0, 0.002), (0.028, 0.0), (0.034, -0.004)], 24, 'z', 'body', core_m)
        place(core, loc + nn * 0.016, nn)
        shade_smooth(core, 60)
        DETAILS.append(core)
    DETAILS.append(decal('reflector', rrect(0.17, 0.028, 0.012, 3, sx * 0.64, 0.43), 'B', 0.003, M_REFLECT, wall=0.0))
# rear garnish between the lamps and plate recess
DETAILS.append(decal('garnish', rrect(0.3, 0.07, 0.03, 4, 0.0, TAIL_Z + 0.01), 'B', 0.002, M_TAILHOUSE, wall=0.0))
DETAILS.append(decal('plate_recess', rrect(0.58, 0.17, 0.03, 4, 0.0, 0.51), 'B', 0.001, M_GLOSSBLK, wall=0.0))
DETAILS.append(ribbon('rear_lower', [(-0.82, 0.39), (-0.5, 0.372), (0.0, 0.368), (0.5, 0.372), (0.82, 0.39)], 'B', 0.035, 0.002, M_BLACK))

# exhaust: single oval tip under the right side of the bumper
ex_prof = [(0.0, 0.03), (0.034, 0.03), (0.04, 0.0), (0.05, -0.005), (0.054, 0.0), (0.054, 0.24)]
ex = revolve('exhaust', ex_prof, 28, 'y', 'body', M_CHROME, center=(0.5, -0.03, 0.31))
ex.data.transform(T(0.5, 0, 0.31) @ Matrix.Diagonal((1.0, 1.0, 0.72, 1)) @ T(-0.5, 0, -0.31))
shade_smooth(ex, 50)
inner = revolve('exhaust_in', [(0.0, 0.05), (0.034, 0.029)], 20, 'y', 'body', M_UNDER, center=(0.5, -0.03, 0.31))
inner.data.transform(T(0.5, 0, 0.31) @ Matrix.Diagonal((1.0, 1.0, 0.72, 1)) @ T(-0.5, 0, -0.31))
DETAILS += [ex, inner]

# ----------------------------------------------------------------------------------------------------------- hoop wing
WY0, WY1, WZ = 0.04, 0.29, 1.085
foil = [(WY1, 0.0), (WY1 - 0.02, 0.014), (WY1 - 0.08, 0.022), (WY0 + 0.06, 0.014), (WY0, 0.002), (WY0 + 0.01, -0.006), (WY1 - 0.1, -0.012),
        (WY1 - 0.02, -0.009)]
foil = [(y, WZ + z + (y - WY1) * 0.06) for y, z in foil]
wing = extrude('wing', foil, 'x', -0.665, 0.665, M_PAINT, bevel=0.003)
DETAILS.append(wing)
for sx in (-1, 1):
    x = sx * 0.635
    base_f = cast('T', x, WY1 - 0.01)[0]
    base_r = cast('T', x, WY0 + 0.04)[0]
    if base_f is None or base_r is None:
        continue
    up = [(WY1 + 0.03, base_f.z - 0.01), (WY0 + 0.03, base_r.z - 0.01), (WY0 + 0.01, WZ + 0.006), (WY1 - 0.01, WZ + 0.016)]
    DETAILS.append(extrude('wing_post', up, 'x', x - 0.045, x + 0.045, M_PAINT, bevel=0.01))
third = extrude('third_brake', rrect(0.004, 0.012, 0.002, 1, WY0 + 0.002, WZ + 0.002)[::-1], 'x', -0.22, 0.22, M_BRAKE)
DETAILS.append(third)

# ----------------------------------------------------------------------------------------------------------- mirrors, wipers
for sx in (-1, 1):
    dp = 'door_fl' if sx < 0 else 'door_fr'
    loc, nn = cast('L' if sx < 0 else 'R', 3.0, 0.925)
    if loc is None:
        continue
    stalk = rbox('mirror_stalk', (0.07, 0.03, 0.022), M_BLACK, T(loc.x + sx * 0.04, 2.985, loc.z + 0.035), 0.006, part=dp)
    head = rbox('mirror_head', (0.13, 0.075, 0.09), M_PAINT, T(loc.x + sx * 0.1, 2.97, loc.z + 0.07) @ Rz(sx * -8), bevel=0.03, part=dp, seg=3)
    glass = rbox('mirror_glass', (0.112, 0.004, 0.072), M_MIRROR, T(loc.x + sx * 0.1, 2.933, loc.z + 0.07) @ Rz(sx * -8), bevel=0.0015, part=dp)
    DETAILS += [stalk, head, glass]
for (x0, x1) in ((-0.62, -0.08), (-0.04, 0.42)):
    pts = []
    for t in np.linspace(0, 1, 8):
        x = lerp(x0, x1, t)
        loc, nn = cast('T', x, WS1 - 0.06 + 0.03 * t)
        if loc is not None:
            pts.append(loc + nn * 0.012)
    if len(pts) >= 2:
        bm = bmesh.new()
        prev = None
        for p in pts:
            ring = [bm.verts.new(p + Vector((0, 0.008 * math.cos(a), 0.008 * math.sin(a)))) for a in np.linspace(0, 2 * math.pi, 6, endpoint=False)]
            if prev:
                for k in range(6):
                    bm.faces.new((prev[k], prev[(k + 1) % 6], ring[(k + 1) % 6], ring[k]))
            prev = ring
        DETAILS.append(finish('wiper', bm, [M_BLACK], 'body', 60))

# ----------------------------------------------------------------------------------------------------------- arch liners
def liner(ya, sx):
    bm = bmesh.new()
    rr = R + 0.04
    xs = (sx * 0.5, sx * 0.86)
    angs = np.linspace(math.radians(-15), math.radians(195), 28)
    rows = [[bm.verts.new((x, ya + rr * math.cos(a), R + 0.005 + rr * math.sin(a))) for x in xs] for a in angs]
    for i in range(len(rows) - 1):
        f = bm.faces.new((rows[i][0], rows[i][1], rows[i + 1][1], rows[i + 1][0]))
    for f in bm.faces:
        f.normal_update()
        c = f.calc_center_median()
        if f.normal.dot(Vector((0, ya, R)) - Vector((0, c.y, c.z))) < 0:
            f.normal_flip()
    # inner wall towards the engine bay
    return finish('liner', bm, [M_UNDER], 'body', 60)


for ya in (YR, YF):
    for sx in (-1, 1):
        DETAILS.append(liner(ya, sx))

# ----------------------------------------------------------------------------------------------------------- interior
INT = []
SEAT_Y = 2.0
for sx in (-1, 1):
    x = sx * 0.3
    base = T(x, SEAT_Y, 0.27)
    INT.append(rbox('seat_base', (0.42, 0.46, 0.08), M_LEATHER, base @ T(0, 0.02, 0.0), 0.03))
    INT.append(rbox('cushion', (0.34, 0.46, 0.06), M_FABRIC, base @ T(0, 0.02, 0.06) @ Rx(6), 0.025))
    for bx in (-1, 1):
        INT.append(rbox('cush_bolster', (0.07, 0.42, 0.1), M_LEATHER, base @ T(bx * 0.19, 0.03, 0.07) @ Rx(6), 0.03))
    back = base @ T(0, -0.21, 0.06) @ Rx(16)
    INT.append(rbox('backrest', (0.4, 0.1, 0.58), M_LEATHER, back @ T(0, -0.02, 0.3), 0.04))
    INT.append(rbox('back_insert', (0.28, 0.04, 0.46), M_FABRIC, back @ T(0, 0.035, 0.28), 0.015))
    for bx in (-1, 1):
        INT.append(rbox('back_bolster', (0.07, 0.13, 0.46), M_LEATHER, back @ T(bx * 0.19, 0.02, 0.26) @ Rz(bx * -10), 0.03))
    INT.append(rbox('headrest', (0.24, 0.08, 0.16), M_LEATHER, back @ T(0, -0.01, 0.7), 0.035))
    for px in (-0.06, 0.06):
        INT.append(rbox('hr_post', (0.012, 0.012, 0.08), M_ALU, back @ T(px, -0.01, 0.6), 0.0))
    # rear 2+2 bench halves
    INT.append(rbox('rear_cushion', (0.4, 0.4, 0.09), M_FABRIC, T(sx * 0.3, 1.42, 0.3) @ Rx(4), 0.035))
    INT.append(rbox('rear_back', (0.4, 0.1, 0.42), M_FABRIC, T(sx * 0.3, 1.2, 0.52) @ Rx(26), 0.04))
# floor carpet + transmission tunnel
INT.append(rbox('carpet', (1.5, 2.3, 0.02), M_CARPET, T(0, 2.0, 0.225), 0.0))
INT.append(extrude('tunnel', [(-0.13, 0.22), (0.13, 0.22), (0.1, 0.36), (-0.1, 0.36)], 'y', 1.3, 3.0, M_CARPET, bevel=0.02))
# centre console with shifter and handbrake
INT.append(rbox('console', (0.2, 0.75, 0.1), M_DASH, T(0, 2.3, 0.4), 0.02))
INT.append(rbox('console_trim', (0.12, 0.2, 0.004), M_ALU, T(0, 2.47, 0.452), 0.0))
INT.append(rbox('boot', (0.09, 0.09, 0.03), M_LEATHER, T(0, 2.47, 0.465), 0.02))
INT.append(revolve('shifter', [(0.008, 0.0), (0.008, 0.12)], 10, 'z', 'body', M_ALU, center=(0, 2.47, 0.46)))
knob = revolve('knob', [(0.0, -0.022), (0.02, -0.018), (0.024, 0.0), (0.02, 0.018), (0.0, 0.022)], 16, 'z', 'body', M_LEATHER,
               center=(0, 2.47, 0.6))
shade_smooth(knob, 60)
INT.append(knob)
INT.append(rbox('handbrake', (0.035, 0.22, 0.03), M_LEATHER, T(0.0, 2.08, 0.47) @ Rx(12), 0.012))
INT.append(rbox('armrest', (0.18, 0.3, 0.06), M_LEATHER, T(0, 1.85, 0.5), 0.025))

# dash: wraparound cockpit (driver on the game's left = -X here)
dprof = [(3.05, 0.86), (2.78, 0.9), (2.68, 0.885), (2.62, 0.83), (2.62, 0.7), (2.7, 0.56), (2.95, 0.42), (3.1, 0.42), (3.12, 0.8)]
INT.append(extrude('dash', dprof, 'x', -0.83, 0.83, M_DASH, bevel=0.012))
INT.append(extrude('dash_lower', [(2.7, 0.56), (2.95, 0.42), (3.1, 0.42), (3.0, 0.5), (2.76, 0.6)], 'x', -0.8, 0.8, M_DASH2))
# cluster binnacle and gauges (driver side)
DX = -0.3
binn = [(-0.22, 0.0), (-0.21, 0.05), (-0.15, 0.1), (0.0, 0.12), (0.15, 0.1), (0.21, 0.05), (0.22, 0.0)]
INT.append(extrude('binnacle', [(DX + a, 0.87 + b) for a, b in binn], 'y', 2.75, 2.88, M_DASH, bevel=0.01))
INT.append(rbox('visor', (0.46, 0.09, 0.02), M_DASH, T(DX, 2.71, 0.99), 0.008))
CL_Y, CL_Z = 2.745, 0.935
gface = rbox('gauge_face', (0.38, 0.006, 0.1), M_GAUGE, T(DX, CL_Y, CL_Z) @ Rx(-12), 0.0)
INT.append(gface)
for gx, gr in ((-0.12, 0.045), (0.0, 0.058), (0.12, 0.045)):
    ring = revolve('gauge_ring', [(gr, 0.0), (gr + 0.006, 0.0), (gr + 0.006, 0.012), (gr, 0.012)], 24, 'z', 'body', M_ALU)
    place(ring, Vector((DX + gx, CL_Y - 0.004, CL_Z)), Vector((0, -math.cos(math.radians(12)), math.sin(math.radians(12)))))
    INT.append(ring)
# centre stack angled towards the driver
stack = T(0.02, 2.66, 0.62) @ Rz(-12) @ Rx(-8)
INT.append(rbox('stack', (0.26, 0.06, 0.32), M_DASH, stack, 0.015))
INT.append(rbox('radio', (0.18, 0.012, 0.05), M_BUTTONS, stack @ T(0, -0.033, 0.08), 0.0))
INT.append(rbox('hvac_panel', (0.2, 0.012, 0.07), M_HVAC, stack @ T(0, -0.033, -0.03), 0.0))
for vx in (-0.08, 0.08):
    INT.append(rbox('vent', (0.07, 0.012, 0.035), M_GRILLE, stack @ T(vx, -0.033, 0.13), 0.004))
for vx in (-0.66, 0.62):
    INT.append(rbox('vent_side', (0.09, 0.012, 0.045), M_GRILLE, T(vx, 2.63, 0.78), 0.005))
# steering wheel (part 'steer')
SW_C = Vector((DX, 2.52, 0.83))
SW_AX = Vector((0, -math.cos(math.radians(62)), math.sin(math.radians(62)))).normalized()     # towards the driver
SW_M = Matrix.Translation(SW_C) @ orient(SW_AX, Vector((0, 1, 0))).to_4x4()
bm = bmesh.new()
RA, RT = 0.185, 0.02
NU, NV = 36, 8
V = [[bm.verts.new(((RA + RT * math.cos(b)) * math.cos(a), (RA + RT * math.cos(b)) * math.sin(a), RT * math.sin(b) * 1.15))
      for b in np.linspace(0, 2 * math.pi, NV, endpoint=False)] for a in np.linspace(0, 2 * math.pi, NU, endpoint=False)]
for i in range(NU):
    for j in range(NV):
        bm.faces.new((V[i][j], V[(i + 1) % NU][j], V[(i + 1) % NU][(j + 1) % NV], V[i][(j + 1) % NV]))
bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
rim = finish('steer_rim', bm, [M_LEATHER], 'steer', 60)
rim.matrix_world = SW_M
INT.append(rim)
INT.append(rbox('steer_hub', (0.15, 0.12, 0.05), M_LEATHER, SW_M @ T(0, 0, 0.02), 0.02, part='steer'))
for a in (0, 180, 245, 295):
    INT.append(rbox('steer_spoke', (0.13, 0.03, 0.012), M_DASH, SW_M @ Rz(a) @ T(0.11, 0, 0.012), 0.006, part='steer'))
col = revolve('column', [(0.03, 0.0), (0.035, -0.22)], 12, 'z', 'body', M_DASH)
col.matrix_world = SW_M
INT.append(col)
# pedals
for px, w_ in ((-0.42, 0.06), (-0.32, 0.08), (-0.2, 0.09)):
    INT.append(rbox('pedal', (w_, 0.012, 0.075), M_ALU, T(px, 2.92, 0.33) @ Rx(-20), 0.004))
# door cards (move with the doors)
for sx, dp in ((-1, 'door_fl'), (1, 'door_fr')):
    INT.append(extrude('door_card', [(DOOR0 + 0.04, 0.32), (DOOR1 - 0.06, 0.34), (DOOR1 - 0.04, 0.86), (DOOR0 + 0.04, 0.9)], 'x', sx * 0.79, sx * 0.815,
                       M_DASH2, part=dp))
    INT.append(rbox('armrest_d', (0.05, 0.4, 0.04), M_LEATHER, T(sx * 0.77, 2.25, 0.62), 0.015, part=dp))
    INT.append(rbox('door_pull', (0.02, 0.1, 0.02), M_ALU, T(sx * 0.775, 2.65, 0.75), 0.006, part=dp))
    INT.append(rbox('door_top', (0.05, 1.3, 0.035), M_DASH, T(sx * 0.79, 2.4, 0.89), 0.012, part=dp))
INT.append(rbox('rv_mirror', (0.2, 0.03, 0.055), M_DASH, T(0, 2.66, 1.16) @ Rx(10), 0.015))
INT.append(rbox('rv_glass', (0.18, 0.004, 0.042), M_MIRROR, T(0, 2.64, 1.16) @ Rx(10), 0.0))
# parcel shelf behind the rear seats
INT.append(rbox('parcel', (1.4, 0.45, 0.02), M_CARPET, T(0, 0.65, 0.86), 0.0))

# ----------------------------------------------------------------------------------------------------------- wheels (built at the origin)
WHEEL = []
WSEG = 20 if FACET else 48
TWH = 0.125   # half tyre width


def a_(a):
    """axial coordinate: + towards the outside of the car (-X in Blender, +X in the game)."""
    return -a


tyre_side = [(0.22, 0.1), (0.25, 0.118), (0.29, 0.122), (0.312, 0.115), (0.322, 0.095)]
tread = [(0.322, 0.095), (0.324, 0.06), (0.324, -0.06), (0.322, -0.095)]
tyre_in = [(0.322, -0.095), (0.312, -0.115), (0.29, -0.122), (0.25, -0.118), (0.22, -0.1)]
for nm_, prof, m_ in (('tyre_out', tyre_side, M_TYRE), ('tread', tread, M_TREAD), ('tyre_in', tyre_in, M_TYRE)):
    o = revolve(nm_, [(r, a_(a)) for r, a in prof], WSEG, 'x', 'wheel', m_)
    o['uvaxis'] = 'x' if m_ is M_TYRE else 'auto'
    shade_smooth(o, 50)
    WHEEL.append(o)
barrel = [(0.212, 0.112), (0.228, 0.108), (0.229, 0.098), (0.214, 0.092), (0.206, 0.06), (0.2, -0.1), (0.21, -0.11)]
o = revolve('rim_barrel', [(r, a_(a)) for r, a in barrel], WSEG, 'x', 'wheel', M_WHEEL)
shade_smooth(o, 45)
WHEEL.append(o)
WHEEL.append(revolve('rim_back', [(0.0, a_(-0.06)), (0.2, a_(-0.06))], 32, 'x', 'wheel', M_UNDER))
hub = revolve('hub', [(0.0, a_(0.088)), (0.03, a_(0.087)), (0.062, a_(0.08)), (0.07, a_(0.066)), (0.07, a_(0.03))], 30, 'x', 'wheel', M_WHEEL)
shade_smooth(hub, 40)
WHEEL.append(hub)
WHEEL.append(revolve('cap', [(0.0, a_(0.095)), (0.022, a_(0.092)), (0.027, a_(0.086))], 20, 'x', 'wheel', M_CHROME))
# five spokes, slightly concave towards the rim
for k in range(5):
    ang = 2 * math.pi * k / 5 + math.pi / 2
    bm = bmesh.new()
    prof = [(0.06, 0.03, 0.082), (0.13, 0.026, 0.072), (0.2, 0.036, 0.086)]   # (radius, half width, axial face)
    rows = []
    for r_, hwid, af in prof:
        row = []
        for (s, depth) in ((-1, 0.0), (-0.65, 0.008), (0.65, 0.008), (1, 0.0)):
            row.append((r_, s * hwid, af + depth * 0.5))
        rows.append(row)
    def P_(r_, t_, ax):
        c, s_ = math.cos(ang), math.sin(ang)
        return Vector((a_(ax), r_ * c - t_ * s_, r_ * s_ + t_ * c))
    front = [[bm.verts.new(P_(r_, t_, ax)) for (r_, t_, ax) in row] for row in rows]
    back = [[bm.verts.new(P_(r_, t_, ax - 0.03)) for (r_, t_, ax) in (row[0], row[-1])] for row in rows]
    for i in range(len(rows) - 1):
        for j in range(3):
            bm.faces.new((front[i][j], front[i + 1][j], front[i + 1][j + 1], front[i][j + 1]))
        bm.faces.new((front[i][0], back[i][0], back[i + 1][0], front[i + 1][0]))
        bm.faces.new((front[i][3], front[i + 1][3], back[i + 1][1], back[i][1]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    WHEEL.append(finish('spoke', bm, [M_WHEEL], 'wheel', 35))
for k in range(5):
    ang = 2 * math.pi * k / 5 + math.pi / 2 + math.pi / 5
    c = (0.0, 0.045 * math.cos(ang), 0.045 * math.sin(ang))
    nut = revolve('lug', [(0.0, a_(0.094)), (0.009, a_(0.093)), (0.011, a_(0.086)), (0.011, a_(0.08))], 6, 'x', 'wheel', M_CHROME, center=c)
    WHEEL.append(nut)
rot = revolve('rotor', [(0.07, a_(0.012)), (0.165, a_(0.012)), (0.165, a_(-0.018)), (0.07, a_(-0.018))], 36, 'x', 'wheel', M_ROTOR)
rot['uvaxis'] = 'x'
WHEEL.append(rot)
# caliper (static, behind the spokes, towards the rear-top)
bm = bmesh.new()
cal_rows = []
for a in np.linspace(math.radians(100), math.radians(160), 8):
    ring = []
    for (r_, ax) in ((0.125, 0.026), (0.182, 0.026), (0.182, -0.032), (0.125, -0.032)):
        ring.append(bm.verts.new((a_(ax), r_ * math.cos(a), r_ * math.sin(a))))
    cal_rows.append(ring)
for i in range(len(cal_rows) - 1):
    for j in range(4):
        bm.faces.new((cal_rows[i][j], cal_rows[i + 1][j], cal_rows[i + 1][(j + 1) % 4], cal_rows[i][(j + 1) % 4]))
bm.faces.new(cal_rows[0])
bm.faces.new(cal_rows[-1][::-1])
bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
WHEEL.append(finish('caliper', bm, [M_CALIPER], 'caliper', 40))

BODY_OBJS = [body] + list(doors.values()) + DETAILS + INT


def wheel_instances():
    """Linked copies of the wheel at the four corners (QC only; the game places the wheel itself)."""
    out = []
    for ya in (YR, YF):
        for sx in (-1, 1):
            for o in WHEEL:
                c = o.copy()
                bpy.context.scene.collection.objects.link(c)
                M = T(sx * TRACK, ya, R)
                if sx > 0:
                    M = M @ Matrix.Diagonal((-1, 1, 1, 1))
                c.matrix_world = M @ o.matrix_world
                c['part'] = 'helper'
                out.append(c)
    for o in WHEEL:
        o.hide_render = True
    return out


# ----------------------------------------------------------------------------------------------------------- export
np_body = export_npz(os.path.join(OUT, 'supra_body.npz'), L, BODY_OBJS)
np_wheel = export_npz(os.path.join(OUT, 'supra_wheel.npz'), 0.0, WHEEL)
print('exported tris: body', np_body, 'wheel', np_wheel)
import json
glt = section(2.4)[GLTOP]
info = dict(L=L, YR=YR, YF=YF, R=R, TRACK=TRACK, steer_c=list(SW_C), steer_ax=list(SW_AX), cluster=[DX, CL_Y - 0.006, CL_Z], cluster_tilt=12,
            seat_y=SEAT_Y, door=dict(y0=DOOR0, y1=DOOR1, hinge_x=hw(DOOR1) - 0.03), tail_z=TAIL_Z, beams=[[x, 4.3, 0.66] for x in (-0.6, 0.6)],
            exhaust=[0.5, -0.04, 0.31], wing_brake=[0, WY0, WZ])
json.dump(info, open(os.path.join(OUT, 'supra_info.json'), 'w'), indent=1)

# ----------------------------------------------------------------------------------------------------------- QC
if QC:
    wheel_instances()
    night = 'night' in sys.argv
    studio(night)
    if night:
        for m_, col, st in LAMPS:
            b = m_.node_tree.nodes['Principled BSDF']
            c = tuple(int(col[i:i + 2], 16) / 255 for i in (1, 3, 5)) + (1,)
            b.inputs['Emission Color'].default_value = c
            b.inputs['Emission Strength'].default_value = st
        for sx in (-1, 1):
            sp = bpy.data.objects.new('beam', bpy.data.lights.new('beam', 'SPOT'))
            sp.data.energy = 260
            sp.data.spot_size = math.radians(55)
            sp.data.spot_blend = 0.6
            sp.data.color = (1.0, 0.95, 0.85)
            sp.location = (sx * 0.6, 4.35, 0.68)
            sp.rotation_euler = (math.radians(-82), 0, math.radians(180))
            bpy.context.scene.collection.objects.link(sp)
        moon = bpy.data.objects.new('moon', bpy.data.lights.new('moon', 'SUN'))
        moon.data.energy = 0.12
        moon.data.color = (0.6, 0.7, 1.0)
        moon.rotation_euler = (math.radians(40), 0, math.radians(200))
        bpy.context.scene.collection.objects.link(moon)
        street = bpy.data.objects.new('street', bpy.data.lights.new('street', 'POINT'))
        street.data.energy = 900
        street.data.color = (1.0, 0.8, 0.55)
        street.location = (-3.5, 1.0, 5.0)
        bpy.context.scene.collection.objects.link(street)
        views = {'night34': ((5.0, 9.0, 1.6), (0, 2.6, 0.55), 50), 'nightrear': ((-3.6, -4.6, 1.4), (0, 1.5, 0.6), 50)}
    else:
        views = {
            'front34': ((5.2, 8.3, 1.9), (0, 2.4, 0.55), 55),
            'rear34': ((-5.0, -3.3, 1.8), (0, 2.0, 0.55), 55),
            'left': ((-8.5, 2.25, 0.7), (0, 2.25, 0.6), 60),
            'right': ((8.5, 2.25, 0.7), (0, 2.25, 0.6), 60),
            'front': ((0, 11.5, 0.9), (0, 2.25, 0.6), 70),
            'rear': ((0, -7.0, 1.0), (0, 2.25, 0.6), 70),
            'interior': ((-0.3, 1.97, 1.04), (-0.18, 3.6, 0.8), 20),
            'detail_front': ((2.0, 6.4, 1.2), (0.3, 4.2, 0.5), 60),
        }
    for name, (loc, tgt, lens) in views.items():
        camera(loc, tgt, lens)
        render(os.path.join(OUT, 'qc_%s.png' % name), (800, 460), 20)
