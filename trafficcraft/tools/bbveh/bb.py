"""TrafficCraft Blockbench vehicle pipeline - core.

Vehicles are authored as Blockbench projects (.bbmodel, "Generic Model"/free format): flat-shaded mesh elements and
cubes with a single per-vehicle pixel texture. The .bbmodel is the source of truth - it can be opened and edited in
Blockbench - and `convert()` turns it into the game's .tcm mesh + texture (players never need Blockbench).

Conventions inside the .bbmodel
  * units: Blockbench pixels, 16 px = 1 m (= 1 Minecraft block). Game frame: +X = vehicle left, +Y up, +Z forward,
    origin on the ground at the middle of the wheelbase... (we use the middle of the length, like the old pipeline).
  * outliner groups name the moving part: body, door_fl, door_fr, steer, wheel, caliper, and customisation variants
    "<slot>_<n>" (fb_0 .. fb_4, sp_2, ...). Elements inherit the nearest such group.
  * element names end with "@<material>": @paint @solid @glass @head @high @drl @tail @brake @ind_l @ind_r @reverse
    @interior @plate @marker @paint2 @screen. Untagged = solid.
  * texture pixels with alpha 240 on @paint elements are NOT tinted by the car colour (trim, badges, panel gaps).
"""
import json, uuid, base64, io, math, gzip, struct, re
import numpy as np
from PIL import Image

MATS = {'paint': 0, 'solid': 1, 'glass': 2, 'head': 3, 'high': 4, 'drl': 5, 'tail': 6, 'brake': 7, 'ind_l': 8, 'ind_r': 9, 'reverse': 10,
        'interior': 11, 'plate': 12, 'beacon_a': 13, 'beacon_b': 14, 'taxi': 15, 'marker': 16, 'paint2': 17, 'screen': 18, 'deco_a': 19, 'deco_b': 20, 'deco_y': 21}
PX = 16.0   # Blockbench pixels per metre
NOTINT = 240  # alpha marking untinted pixels on paint


def V(*a):
    return np.array(a if len(a) == 3 else a[0], float)


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


# ======================================================================================================== model
class Face:
    __slots__ = ('verts', 'style', 'dens', 'uv', 'rect', 'basis', 'n', 'el')

    def __init__(self, verts, style, dens):
        self.verts = [V(p) for p in verts]
        self.style = style
        self.dens = dens
        self.uv = None
        self.rect = None


class Element:
    def __init__(self, name, mat, group, style):
        self.name, self.mat, self.group, self.style = name, mat, group, style
        self.faces = []


class Model:
    """Collects faces (in metres, game frame), packs + paints the texture and writes a .bbmodel."""

    def __init__(self, name, tex_size=1024, density=48):
        self.name = name
        self.tex = tex_size
        self.density = density
        self.elements = []
        self.groups = ['body']
        self.painters = {}      # style -> f(face, P(np[h,w,3]), N) -> rgba array
        self._cur = None
        self.shift = np.zeros(3)   # added to every vertex when writing (authoring frame -> game frame)

    # ---------------------------------------------------------------- building
    def element(self, name, mat='solid', group='body', style=None):
        if group not in self.groups:
            self.groups.append(group)
        e = Element(name, mat, group, style or mat)
        self.elements.append(e)
        self._cur = e
        return e

    def face(self, verts, style=None, dens=None, el=None, outward=None):
        """Planar polygon (3+ points, metres). If `outward` is given the winding is flipped to face it."""
        e = el or self._cur
        vs = [V(p) for p in verts]
        # drop duplicate consecutive points
        out = []
        for p in vs:
            if not out or np.linalg.norm(p - out[-1]) > 1e-5:
                out.append(p)
        if len(out) > 2 and np.linalg.norm(out[0] - out[-1]) < 1e-5:
            out.pop()
        if len(out) < 3:
            return None
        n = newell(out)
        if np.linalg.norm(n) < 1e-9:
            return None
        if outward is not None and np.dot(n, outward) < 0:
            out = out[::-1]
        f = Face(out, style or e.style, dens or self.density)
        f.el = e
        e.faces.append(f)
        return f

    def quad_strip(self, A, B, style=None, outward_fn=None, closed=False):
        """Faces between two polylines A[i] and B[i]."""
        n = len(A)
        for i in range(n if closed else n - 1):
            j = (i + 1) % n
            q = [A[i], A[j], B[j], B[i]]
            ow = outward_fn(q) if outward_fn else None
            self.face(q, style, outward=ow)

    def prism(self, outline, a0, a1, axis, style=None, cap0=True, cap1=True, side_style=None):
        """Extrude a 2D polygon (CCW) along axis 'x'|'y'|'z' from a0 to a1. Point mapping like blib.prism."""
        def P(u, v, w):
            return V(w, u, v) if axis == 'x' else V(u, w, v) if axis == 'y' else V(u, v, w)
        c = np.mean(np.array(outline), axis=0)
        b0 = [P(u, v, a0) for u, v in outline]
        b1 = [P(u, v, a1) for u, v in outline]
        n = len(outline)
        cc = P(c[0], c[1], (a0 + a1) / 2)
        for k in range(n):
            q = [b0[k], b0[(k + 1) % n], b1[(k + 1) % n], b1[k]]
            self.face(q, side_style or style, outward=np.mean(q, axis=0) - cc)
        ax = {'x': V(1, 0, 0), 'y': V(0, 1, 0), 'z': V(0, 0, 1)}[axis] * np.sign(a1 - a0)
        if cap0:
            self.face(b0, style, outward=-ax)
        if cap1:
            self.face(b1, style, outward=ax)

    def box(self, c, size, style=None, M=None):
        """Axis box (centre, size) optionally transformed by a 3x3 rotation M about its centre."""
        hx, hy, hz = np.array(size) / 2
        corners = np.array([[x, y, z] for x in (-hx, hx) for y in (-hy, hy) for z in (-hz, hz)])
        if M is not None:
            corners = corners @ np.array(M).T
        P = corners + np.array(c)
        idx = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
        cc = np.array(c)
        for q in idx:
            pts = [P[i] for i in q]
            self.face(pts, style, outward=np.mean(pts, axis=0) - cc)

    def cylinder(self, c, axis, r, h, seg=12, style=None, cap_style=None, r1=None, caps=(True, True), rot=0.0):
        """Faceted cylinder/cone along unit `axis`, base centre c, height h."""
        ax = unit(V(axis))
        up = V(0, 1, 0) if abs(ax[1]) < 0.9 else V(1, 0, 0)
        u = unit(np.cross(up, ax))
        w = np.cross(ax, u)
        r1 = r if r1 is None else r1
        ring0 = [V(c) + (u * math.cos(a) + w * math.sin(a)) * r for a in np.linspace(0, 2 * math.pi, seg, endpoint=False) + rot]
        ring1 = [V(c) + ax * h + (u * math.cos(a) + w * math.sin(a)) * r1 for a in np.linspace(0, 2 * math.pi, seg, endpoint=False) + rot]
        mid = V(c) + ax * h / 2
        for k in range(seg):
            q = [ring0[k], ring0[(k + 1) % seg], ring1[(k + 1) % seg], ring1[k]]
            self.face(q, style, outward=np.mean(q, axis=0) - mid)
        if caps[0]:
            self.face(ring0, cap_style or style, outward=-ax)
        if caps[1]:
            self.face(ring1, cap_style or style, outward=ax)

    # ---------------------------------------------------------------- texture
    def pack(self):
        faces = [f for e in self.elements for f in e.faces]
        for f in faces:
            n = unit(newell(f.verts))
            f.n = n
            # basis: u along the longest edge projected, v = n x u
            best = max(range(len(f.verts)), key=lambda i: np.linalg.norm(f.verts[(i + 1) % len(f.verts)] - f.verts[i]))
            eu = unit(f.verts[(best + 1) % len(f.verts)] - f.verts[best])
            # prefer horizontal u on side faces so textures stay upright
            if abs(n[1]) < 0.7:
                h = np.cross(V(0, 1, 0), n)
                if np.linalg.norm(h) > 1e-6:
                    eu = unit(-h)
            ev = np.cross(n, eu)
            if abs(n[1]) < 0.7 and ev[1] > 0:
                ev = -ev      # v grows downwards on side faces
            P = np.array(f.verts)
            o = P[0]
            s = (P - o) @ eu
            t = (P - o) @ ev
            f.basis = (o, eu, ev, s.min(), t.min())
            f.uv = (s - s.min(), t - t.min())
        # shelf packing (sizes in texels)
        items = []
        for f in faces:
            w = max(1, int(math.ceil(f.uv[0].max() * f.dens)))
            h = max(1, int(math.ceil(f.uv[1].max() * f.dens)))
            items.append((h, w, f))
        items.sort(key=lambda x: (-x[0], -x[1]))
        size = self.tex
        while True:
            x = y = 0
            row = 0
            ok = True
            placed = []
            reserve = (size - 64, 0, size, 64)    # matcap copy in the top right corner
            for h, w, f in items:
                if w + 2 > size:
                    ok = False
                    break
                if x + w + 2 > (reserve[0] if y < reserve[3] else size):
                    x = 0
                    y += row
                    row = 0
                if y + h + 2 > size:
                    ok = False
                    break
                placed.append((x + 1, y + 1, w, h, f))
                x += w + 2
                row = max(row, h + 2)
            if ok:
                break
            size *= 2
        self.tex = size
        self.matcap_rect = reserve
        for x, y, w, h, f in placed:
            f.rect = (x, y, w, h)

    def paint(self, matcap_img=None):
        img = np.zeros((self.tex, self.tex, 4), np.uint8)
        for e in self.elements:
            for f in e.faces:
                x, y, w, h = f.rect
                o, eu, ev, s0, t0 = f.basis
                ii, jj = np.meshgrid(np.arange(w + 2) - 1, np.arange(h + 2) - 1)     # 1 texel bleed border
                s = (ii + 0.5) / f.dens + s0
                t = (jj + 0.5) / f.dens + t0
                P = o + s[..., None] * eu + t[..., None] * ev
                fn = self.painters.get(f.style) or self.painters.get(f.style.split('.')[0]) or self.painters['default']
                col = fn(f, P, f.n)
                col = np.clip(col, 0, 255).astype(np.uint8)
                y0, x0 = y - 1, x - 1
                img[y0:y0 + h + 2, x0:x0 + w + 2] = col
        if matcap_img is not None:
            mx0, my0, mx1, my1 = self.matcap_rect
            img[my0:my1, mx0:mx1] = np.array(matcap_img.convert('RGBA').resize((mx1 - mx0, my1 - my0)))
        self.image = Image.fromarray(img, 'RGBA')
        return self.image

    # ---------------------------------------------------------------- bbmodel
    def write_bbmodel(self, path, extra=None):
        size = self.tex
        elems = []
        group_children = {g: [] for g in self.groups}
        for e in self.elements:
            if not e.faces:
                continue
            verts = {}
            vk = {}
            faces = {}

            sh = np.zeros(3) if (e.group.startswith('wh_') or e.group == 'caliper') else self.shift

            def key(p):
                k = tuple(np.round((p + sh) * PX, 4))
                if k not in vk:
                    name = 'v%d' % len(vk)
                    vk[k] = name
                    verts[name] = [float(k[0]), float(k[1]), float(k[2])]
                return vk[k]
            for fi, f in enumerate(e.faces):
                ks = [key(p) for p in f.verts]
                x, y, w, h = f.rect
                us = f.uv[0] * f.dens + x
                vs = f.uv[1] * f.dens + y
                faces['f%d' % fi] = {'uv': {k: [round(float(u), 4), round(float(v), 4)] for k, u, v in zip(ks, us, vs)}, 'vertices': ks,
                                     'texture': 0}
            uid = str(uuid.uuid4())
            elems.append({'name': '%s@%s' % (e.name, e.mat), 'color': 0, 'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'export': True,
                          'visibility': True, 'locked': False, 'render_order': 'default', 'allow_mirror_modeling': True, 'vertices': verts,
                          'faces': faces, 'type': 'mesh', 'uuid': uid})
            group_children[e.group].append(uid)
        outliner = []
        for g in self.groups:
            outliner.append({'name': g, 'origin': [0, 0, 0], 'color': 0, 'uuid': str(uuid.uuid4()), 'export': True, 'mirror_uv': False,
                             'isOpen': False, 'locked': False, 'visibility': True, 'autouv': 0, 'children': group_children[g]})
        buf = io.BytesIO()
        self.image.save(buf, 'PNG')
        tex = {'path': '', 'name': self.name + '.png', 'folder': '', 'namespace': '', 'id': '0', 'width': size, 'height': size, 'uv_width': size,
               'uv_height': size, 'particle': False, 'use_as_default': False, 'layers_enabled': False, 'sync_to_project': '', 'render_mode': 'default',
               'render_sides': 'auto', 'frame_time': 1, 'frame_order_type': 'loop', 'frame_order': '', 'frame_interpolate': False, 'visible': True,
               'internal': True, 'saved': True, 'uuid': str(uuid.uuid4()), 'relative_path': '',
               'source': 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()}
        doc = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': self.name, 'model_identifier': '',
               'visible_box': [1, 1, 0], 'variable_placeholders': '', 'variable_placeholder_buttons': [], 'timeline_setups': [],
               'unhandled_root_fields': {}, 'resolution': {'width': size, 'height': size}, 'elements': elems, 'outliner': outliner,
               'textures': [tex]}
        if extra:
            doc['trafficcraft'] = extra
        with open(path, 'w') as fp:
            json.dump(doc, fp, separators=(',', ':'))


def newell(P):
    n = np.zeros(3)
    for i in range(len(P)):
        a, b = P[i], P[(i + 1) % len(P)]
        n += np.array([(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])])
    return n


# ======================================================================================================== reading
SLOT_RE = re.compile(r'^([a-z]{2})_(\d+)$')


def _rot_matrix(rot):
    rx, ry, rz = [math.radians(a) for a in rot]
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X     # Blockbench / three.js 'ZYX' order


def read_bbmodel(path):
    """Returns (triangles, image, extra). triangle = (part, mat, [p0,p1,p2] metres, [uv0..] 0..1, normal)."""
    doc = json.load(open(path))
    W = doc['resolution']['width']
    Hh = doc['resolution']['height']
    src = doc['textures'][0]['source']
    img = Image.open(io.BytesIO(base64.b64decode(src.split(',', 1)[1]))).convert('RGBA')
    tw, th = doc['textures'][0].get('uv_width', W), doc['textures'][0].get('uv_height', Hh)
    part_of = {}

    def walk(node, part):
        if isinstance(node, str):
            part_of[node] = part
            return
        name = node['name']
        p = name if (name in ('body', 'steer', 'wheel', 'caliper', 'shifter') or name.startswith('door_') or name.startswith('popup_')
                     or SLOT_RE.match(name)) else part
        m = SLOT_RE.match(p)
        for ch in node.get('children', []):
            walk(ch, p)
    for n in doc['outliner']:
        walk(n, 'body')
    tris = []
    for e in doc['elements']:
        name = e['name']
        mat = name.rsplit('@', 1)[1] if '@' in name else 'solid'
        mid = MATS.get(mat, 1)
        part = part_of.get(e['uuid'], 'body')
        m = SLOT_RE.match(part)
        if m:
            part = ('wheel:%s' % m.group(2)) if m.group(1) == 'wh' else '%s:%s' % (m.group(1), m.group(2))
        origin = np.array(e.get('origin', [0, 0, 0]), float)
        R = _rot_matrix(e.get('rotation', [0, 0, 0]))
        if e.get('type') == 'mesh':
            verts = {k: R @ (np.array(v, float)) + origin for k, v in e['vertices'].items()}
            for f in e['faces'].values():
                ks = f['vertices']
                if len(ks) < 3:
                    continue
                if len(ks) == 4:
                    ks = sort_quad(ks, verts)
                P = [verts[k] / PX for k in ks]
                UV = [(f['uv'][k][0] / tw, f['uv'][k][1] / th) for k in ks]
                n = unit(newell(P))
                for i in range(1, len(P) - 1):
                    tris.append((part, mid, [P[0], P[i], P[i + 1]], [UV[0], UV[i], UV[i + 1]], n))
        else:
            tris += cube_tris(e, part, mid, R, origin, tw, th)
    return tris, img, doc.get('trafficcraft', {})


def sort_quad(ks, verts):
    """Blockbench stores quad vertices unordered; order them around the face."""
    P = np.array([verts[k] for k in ks])
    c = P.mean(axis=0)
    n = newell(P)
    if np.linalg.norm(n) < 1e-9:
        n = np.cross(P[1] - P[0], P[2] - P[0])
    n = unit(n)
    a = unit(P[0] - c)
    b = np.cross(n, a)
    ang = [math.atan2(np.dot(p - c, b), np.dot(p - c, a)) for p in P]
    order = sorted(range(4), key=lambda i: ang[i])
    return [ks[i] for i in order]


def cube_tris(e, part, mid, R, origin, tw, th):
    f0 = np.array(e['from'], float) - e.get('inflate', 0)
    t0 = np.array(e['to'], float) + e.get('inflate', 0)
    x0, y0, z0 = f0
    x1, y1, z1 = t0
    # corners per face in the order (u0,v0) (u1,v0) (u1,v1) (u0,v1) following Minecraft face orientation
    F = {'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
         'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
         'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
         'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
         'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
         'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}
    out = []
    for fname, face in e.get('faces', {}).items():
        if face.get('texture') is None or fname not in F:
            continue
        u0, v0, u1, v1 = face['uv']
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        rot = int(face.get('rotation', 0)) // 90
        uvs = uvs[-rot:] + uvs[:-rot] if rot else uvs
        P = [(R @ (np.array(p) - origin) + origin) / PX for p in F[fname]]
        UV = [(u / tw, v / th) for u, v in uvs]
        n = unit(newell(P))
        out.append((part, mid, [P[0], P[1], P[2]], [UV[0], UV[1], UV[2]], n))
        out.append((part, mid, [P[0], P[2], P[3]], [UV[0], UV[2], UV[3]], n))
    return out


# ======================================================================================================== game export
def write_tcm(tris, path, alpha_glass=70):
    groups = {}
    for part, mid, P, UV, n in tris:
        groups.setdefault((part, mid), []).append((P, UV, n))
    out = bytearray(b'TCM2')
    out += struct.pack('>i', len(groups))
    for (part, mid), lst in groups.items():
        nb = part.encode()
        out += struct.pack('>H', len(nb)) + nb
        out += struct.pack('>ii', mid, len(lst) * 3)
        col = (alpha_glass << 24 | 0xFFFFFF) if mid == MATS['glass'] else 0xFFFFFFFF
        for P, UV, n in lst:
            for k in range(3):
                p, uv = P[k], UV[k]
                out += struct.pack('>fffbbbHHI', float(p[0]), float(p[1]), float(p[2]), int(round(n[0] * 127)), int(round(n[1] * 127)),
                                   int(round(n[2] * 127)), int(max(0, min(1, uv[0])) * 65535), int(max(0, min(1, uv[1])) * 65535), col & 0xFFFFFFFF)
    with gzip.open(path, 'wb', compresslevel=9) as fp:
        fp.write(bytes(out))
    return sum(len(v) for v in groups.values())


# ======================================================================================================== helpers
class Curve:
    """Monotone cubic (Fritsch-Carlson) interpolation through key points."""

    def __init__(self, pts):
        pts = sorted(pts)
        self.x = np.array([p[0] for p in pts], float)
        self.y = np.array([p[1] for p in pts], float)
        h = np.diff(self.x)
        d = np.diff(self.y) / h
        n = len(self.x)
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


def rrect(w, h, r, seg=3, cx=0.0, cy=0.0):
    r = min(r, w / 2 * 0.999, h / 2 * 0.999)
    pts = []
    for (qx, qy, a0) in ((w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180), (w / 2 - r, -h / 2 + r, 270)):
        for k in range(seg + 1):
            a = math.radians(a0 + 90 * k / seg)
            pts.append((cx + qx + r * math.cos(a), cy + qy + r * math.sin(a)))
    return pts


def ngon(r, n, cx=0.0, cy=0.0, rot=0.0, ry=None):
    ry = r if ry is None else ry
    return [(cx + r * math.cos(rot + 2 * math.pi * k / n), cy + ry * math.sin(rot + 2 * math.pi * k / n)) for k in range(n)]


def chaikin(poly, it=2, closed=True):
    for _ in range(it):
        out = []
        n = len(poly)
        for i in range(n if closed else n - 1):
            a, b = np.array(poly[i]), np.array(poly[(i + 1) % n])
            out += [tuple(a * 0.75 + b * 0.25), tuple(a * 0.25 + b * 0.75)]
        poly = out if closed else [poly[0]] + out + [poly[-1]]
    return poly


def seg_dist(Q, poly, closed=False):
    """Distance from points Q[...,2] to a 2D polyline."""
    Q = np.asarray(Q, float)
    best = np.full(Q.shape[:-1], 1e9)
    P = [np.array(p, float) for p in poly]
    if closed:
        P = P + [P[0]]
    for a, b in zip(P[:-1], P[1:]):
        ab = b - a
        t = np.clip(((Q - a) @ ab) / max(ab @ ab, 1e-12), 0, 1)
        d = np.linalg.norm(Q - (a + t[..., None] * ab), axis=-1)
        best = np.minimum(best, d)
    return best


def inside(Q, poly):
    """Point-in-polygon (even-odd) for Q[...,2]."""
    Q = np.asarray(Q, float)
    x, y = Q[..., 0], Q[..., 1]
    res = np.zeros(Q.shape[:-1], bool)
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        cond = ((y1 > y) != (y2 > y)) & (x < (x2 - x1) * (y - y1) / ((y2 - y1) + 1e-12) + x1)
        res ^= cond
    return res


def rgba(c, a=255):
    if isinstance(c, str):
        return np.array([int(c[i:i + 2], 16) for i in (1, 3, 5)] + [a], float)
    return np.array(list(c)[:3] + [a], float)


def fill(P, c, a=255):
    out = np.zeros(P.shape[:-1] + (4,), float)
    out[...] = rgba(c, a)
    return out


def noise(P, amp=4.0, scale=48.0, seed=0):
    """Deterministic per-texel noise from world position (pixel-art grain)."""
    q = np.floor(P * scale).astype(np.int64)
    h = (q[..., 0] * 73856093) ^ (q[..., 1] * 19349663) ^ (q[..., 2] * 83492791) ^ seed
    h = (h ^ (h >> 13)) * 1274126177
    return ((h & 255) / 255.0 - 0.5) * 2 * amp


def ray_tris(origins, d, tris):
    """Nearest hit of rays (origins[N,3], direction d) with triangles [M,3,3]. Returns (t[N], tri_index[N]) with inf if no hit."""
    T = np.asarray(tris, float)
    v0, v1, v2 = T[:, 0], T[:, 1], T[:, 2]
    e1, e2 = v1 - v0, v2 - v0
    pv = np.cross(d, e2)
    det = np.einsum('ij,ij->i', e1, pv)
    ok = np.abs(det) > 1e-12
    inv = np.where(ok, 1.0 / np.where(ok, det, 1), 0)
    O = np.asarray(origins, float)[:, None, :]
    tv = O - v0[None]
    u = np.einsum('nmj,mj->nm', tv, pv) * inv
    qv = np.cross(tv, e1[None])
    v = np.einsum('j,nmj->nm', d, qv) * inv
    t = np.einsum('mj,nmj->nm', e2, qv) * inv
    hit = ok[None] & (u >= 0) & (v >= 0) & (u + v <= 1) & (t > 1e-6)
    t = np.where(hit, t, np.inf)
    idx = np.argmin(t, axis=1)
    return t[np.arange(len(O)), idx], idx
