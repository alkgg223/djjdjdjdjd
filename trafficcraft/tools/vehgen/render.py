"""Numpy z-buffer rasteriser for previewing generated vehicles.
Gouraud shading from the exported smooth normals and perspective-correct per-pixel texture sampling,
which mirrors how Minecraft's entity shader draws the meshes (per-vertex light, textured)."""
import math, os
import numpy as np
from PIL import Image, ImageDraw
from geom import *

LIGHT_ON = {HEAD: (255, 250, 236), HIGH: (236, 244, 255), DRL: (244, 250, 255), TAIL: (230, 30, 30), BRAKE: (255, 40, 36),
            IND_L: (255, 160, 30), IND_R: (255, 160, 30), REVERSE: (250, 250, 250), MARKER: (255, 160, 30),
            BEACON_A: (255, 40, 40), BEACON_B: (60, 100, 255), TAXI_SIGN: (255, 236, 150), INTERIOR_LIGHT: (255, 240, 210),
            PLATE_LIGHT: (255, 255, 255), SCREEN: (255, 255, 255)}
LIGHT_OFF = {HEAD: (226, 230, 236), HIGH: (215, 220, 228), TAIL: (120, 12, 18), BRAKE: (140, 14, 20), IND_L: (205, 118, 30),
             IND_R: (205, 118, 30), REVERSE: (205, 205, 208), MARKER: (200, 110, 30), INTERIOR_LIGHT: (200, 196, 184),
             PLATE_LIGHT: (190, 190, 190), BEACON_A: (150, 30, 30), BEACON_B: (30, 50, 150), TAXI_SIGN: (230, 200, 90)}
ATLAS = None
DASHA = None


def dash_atlas():
    global DASHA
    if DASHA is None:
        p = '/tmp/claude-0/vehout/dash.png'
        DASHA = np.array(Image.open(p).convert('RGB'), float) / 255.0 if os.path.exists(p) else np.ones((8, 8, 3))
    return DASHA


def atlas():
    global ATLAS
    if ATLAS is None:
        p = '/tmp/claude-0/vehout/atlas.png'
        ATLAS = np.array(Image.open(p).convert('RGB'), float) / 255.0 if os.path.exists(p) else np.ones((8, 8, 3))
    return ATLAS


def collect(mesh, wmesh, meta, paint=(200, 30, 35), paint2=(240, 240, 240), lights=True, parts_skip=()):
    """Returns list of (P[3], N[3], UV[3], mat, rgba)."""
    out = []

    def add(groups, xf=None, nf=None):
        for (part, mat), ts in groups.items():
            if part in parts_skip:
                continue
            ns = compute_normals(ts, 40.0)
            for t, n3 in zip(ts, ns):
                P = [t[0], t[1], t[2]]
                N = list(n3)
                if xf:
                    P = [xf(p) for p in P]
                    N = [nf(n) for n in N]
                col = t[6]
                if mat == PAINT:
                    c = tuple(int(col[i] * paint[i] / 255) for i in range(3)) + (255,)
                elif mat == PAINT2:
                    c = tuple(paint2) + (255,)
                elif mat in LIGHT_ON:
                    if lights or mat in (DRL, SCREEN):
                        c = LIGHT_ON[mat] + (255,)
                    else:
                        lo = LIGHT_OFF.get(mat, (120, 120, 120))
                        c = tuple(int(lo[i] * col[i] / 255) for i in range(3)) + (255,)
                else:
                    c = col
                out.append((P, N, [t[3], t[4], t[5]], mat, c))

    add(mesh.groups)
    for w in meta['wheels']:
        c = v3(w['x'], w['y'], w['z'])
        m = w['mirror']
        xf = (lambda p, c=c, m=m: v3(-p[0] if m else p[0], p[1], p[2]) + c)
        nf = (lambda n, m=m: v3(-n[0] if m else n[0], n[1], n[2]))
        add(wmesh.groups, xf, nf)
    return out


def render(tris, eye, target, size=(640, 420), fov=32, bg=(206, 214, 222), ground=True, dark=False, ss=2, shadow=None):
    im = _render(tris, eye, target, (size[0] * ss, size[1] * ss), fov, bg, ground, dark, shadow)
    return im.resize(size, Image.LANCZOS) if ss > 1 else im


def _render(tris, eye, target, size, fov, bg, ground, dark, shadow):
    W, H = size
    A = atlas()
    AH, AW = A.shape[:2]
    eye = np.array(eye, float)
    target = np.array(target, float)
    f = normalize(target - eye)
    r = normalize(np.cross(f, v3(0, 1, 0)))
    u = np.cross(r, f)
    focal = (H / 2) / math.tan(math.radians(fov) / 2)
    zbuf = np.full((H, W), np.inf)
    img = np.zeros((H, W, 3), float)
    yy = np.linspace(0, 1, H)[:, None]
    img[:] = (np.array(bg, float) * (1.04 - 0.1 * yy)[..., None]) if not dark else np.array(bg, float)
    light = normalize(v3(0.45, 0.8, 0.4))
    light2 = normalize(v3(-0.5, 0.35, -0.6))
    wuv = UV['white']
    wuv3 = [(wuv[0] + 0.001, wuv[1] + 0.001)] * 3
    pre = []
    if ground:
        gc = (140, 144, 146, 255) if not dark else (26, 28, 34, 255)
        up = [v3(0, 1, 0)] * 3
        for gx in range(-12, 12):
            for gz in range(-12, 12):
                a0, a1, b0, b1 = gx * 2.0, gx * 2.0 + 2, gz * 2.0, gz * 2.0 + 2
                pre += [([v3(a0, 0, b0), v3(a1, 0, b1), v3(a1, 0, b0)], up, wuv3, 99, gc),
                        ([v3(a0, 0, b0), v3(a0, 0, b1), v3(a1, 0, b1)], up, wuv3, 99, gc)]
    trans = []
    if shadow:
        sl, sw = shadow
        for k, (s, a) in enumerate(((1.06, 0.1), (0.97, 0.14), (0.88, 0.2))):
            hl, hw = sl / 2 * s, sw / 2 * s
            y = 0.003 + k * 0.001
            q = [v3(-hw, y, -hl), v3(hw, y, -hl), v3(hw, y, hl), v3(-hw, y, hl)]
            col = (0, 0, 0, int(a * 255))
            trans.append(([q[0], q[1], q[2]], [v3(0, 1, 0)] * 3, wuv3, 98, col))
            trans.append(([q[0], q[2], q[3]], [v3(0, 1, 0)] * 3, wuv3, 98, col))

    def proj(p):
        d = p - eye
        z = np.dot(d, f)
        return np.array([W / 2 + np.dot(d, r) * focal / z, H / 2 - np.dot(d, u) * focal / z, z])

    def vshade(p, n, mat, col):
        base = np.array(col[:3], float)
        if mat in LIGHT_ON or mat in (99, 98):
            return base
        vdir = normalize(eye - p)
        if np.dot(n, vdir) < 0:
            n = -n
        diff = 0.36 + 0.56 * max(0, np.dot(n, light)) + 0.14 * max(0, np.dot(n, light2))
        if dark:
            diff *= 0.2
        c = base * diff
        if mat in (PAINT, PAINT2, GLASS):
            rv = 2 * np.dot(n, vdir) * n - vdir
            env = np.array([178, 198, 225.0]) * (0.7 + 0.3 * rv[1]) if rv[1] > 0 else np.array([62, 64, 66.0]) * (1 + rv[1] * 0.3)
            if dark:
                env = env * 0.15
            fres = 0.05 + 0.6 * (1 - abs(np.dot(n, vdir))) ** 4 if mat != GLASS else 0.2 + 0.65 * (1 - abs(np.dot(n, vdir))) ** 2
            c = c * (1 - fres) + env * fres
            h = normalize(light + vdir)
            c = c + (255 if not dark else 40) * (0.5 if mat != GLASS else 0.6) * max(0, np.dot(n, h)) ** 40
        return c

    def raster(P, N, UVs, mat, col, alpha=1.0):
        S = [proj(p) for p in P]
        if min(s[2] for s in S) < 0.05:
            return
        xs = [s[0] for s in S]
        ys = [s[1] for s in S]
        x0, x1 = max(0, int(min(xs))), min(W - 1, int(max(xs)) + 1)
        y0, y1 = max(0, int(min(ys))), min(H - 1, int(max(ys)) + 1)
        if x0 > x1 or y0 > y1:
            return
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = S
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        w0 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
        w1 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
        if not m.any():
            return
        iz = w0 / az + w1 / bz + w2 / cz
        z = 1 / iz
        zb = zbuf[y0:y1 + 1, x0:x1 + 1]
        m &= z < zb
        if not m.any():
            return
        izm = iz[m]
        p0, p1, p2 = w0[m] / az / izm, w1[m] / bz / izm, w2[m] / cz / izm
        cs = [vshade(P[k], N[k], mat, col) for k in range(3)]
        cpx = p0[:, None] * cs[0] + p1[:, None] * cs[1] + p2[:, None] * cs[2]
        uu = p0 * UVs[0][0] + p1 * UVs[1][0] + p2 * UVs[2][0]
        vv = p0 * UVs[0][1] + p1 * UVs[1][1] + p2 * UVs[2][1]
        if mat == SCREEN:
            D = dash_atlas()
            tex = D[np.clip((vv * D.shape[0]).astype(int), 0, D.shape[0] - 1), np.clip((uu * D.shape[1]).astype(int), 0, D.shape[1] - 1)]
        else:
            tex = A[np.clip((vv * AH).astype(int), 0, AH - 1), np.clip((uu * AW).astype(int), 0, AW - 1)]
        cpx = np.clip(cpx * tex, 0, 255)
        sub = img[y0:y1 + 1, x0:x1 + 1]
        if alpha >= 1:
            zb[m] = z[m]
            sub[m] = cpx
        else:
            sub[m] = sub[m] * (1 - alpha) + cpx * alpha

    for (P, N, UVs, mat, col) in pre + tris:
        if mat == GLASS:
            trans.append((P, N, UVs, mat, col))
            continue
        raster(P, N, UVs, mat, col)

    def depth(t):
        c = (t[0][0] + t[0][1] + t[0][2]) / 3
        return -np.dot(c - eye, f)

    trans.sort(key=lambda t: (0 if t[3] == 98 else 1, depth(t)))
    for (P, N, UVs, mat, col) in trans:
        al = col[3] / 255.0 if len(col) > 3 and col[3] < 255 else 0.6
        if mat == 98:
            raster(P, N, UVs, 99, (0, 0, 0, 255), alpha=al)
        else:
            raster(P, N, UVs, mat, col, alpha=min(0.9, al + 0.15) if al > 0.4 else al * 0.7)
    return Image.fromarray(img.astype(np.uint8))


def label(im, text, dark=False):
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 10 + len(text) * 6, 16], fill=(0, 0, 0) if not dark else (60, 60, 70))
    d.text((5, 3), text, fill=(255, 255, 255))


def sheet(mesh, wmesh, meta, path, paint=(200, 30, 35), extra_views=True, size=(800, 480)):
    tris = collect(mesh, wmesh, meta, paint=paint, lights=False)
    L, Hh, Wd = meta['length'], meta['height'], meta['width']
    d = max(L, 4.5) * 1.55
    sh = (L * 0.95, Wd)
    views = [
        ((d * 0.75, Hh * 1.3, d * 0.8), (0, Hh * 0.45, 0), 28, 'front 3/4'),
        ((-d * 0.8, Hh * 1.2, -d * 0.75), (0, Hh * 0.45, 0), 28, 'rear 3/4'),
        ((d * 1.25, Hh * 0.55, 0.0), (0, Hh * 0.45, 0), 28, 'side'),
        ((0.0, Hh * 0.8, d * 1.1), (0, Hh * 0.45, 0), 24, 'front'),
        ((0.0, Hh * 0.9, -d * 1.1), (0, Hh * 0.45, 0), 24, 'rear'),
        ((Wd * 1.25, Hh * 0.75, L * 0.95), (Wd * 0.2, Hh * 0.4, L * 0.4), 34, 'front detail (lamp / grille / wheel)'),
    ]
    imgs = []
    for eye, tgt, fov, name in views:
        im = render(tris, eye, tgt, size=size, fov=fov, shadow=sh)
        label(im, name + '  -  ' + meta['name'])
        imgs.append(im)
    if extra_views:
        t2 = [t for t in tris if not (min(t[0][0][1], t[0][1][1], t[0][2][1]) > meta.get('cut_y', Hh - 0.22))]
        im = render(t2, (1.5, Hh + 2.4, -L * 0.2), (0, Hh * 0.3, 0.25), size=size, fov=40)
        label(im, 'interior (roof removed)')
        imgs.append(im)
        if meta.get('seats'):
            s = meta['seats'][0]
            eye = v3(s[0], s[1] + meta.get('driver_eye', 0.72), s[2] - 0.05)
            im = render(tris, eye, eye + v3(-0.1, -0.12, 1.0), size=size, fov=74, ground=True)
            label(im, 'driver view')
            imgs.append(im)
            c = meta.get('cluster')
            if c:
                cc = v3(*c['center'])
                eye2 = cc + v3(-0.08, 0.2, -0.55)
                im = render(tris, eye2, cc + v3(-0.28, -0.06, 0.0), size=size, fov=60, ground=True)
                label(im, 'dashboard: cluster / screen / controls (steering wheel visible)')
                imgs.append(im)
            im = render(tris, v3(-0.05, Hh - 0.15, -L * 0.17), v3(0.15, 0.6, 0.7), size=size, fov=80, ground=True)
            label(im, 'cabin from the rear seat')
            imgs.append(im)
    tn = collect(mesh, wmesh, meta, paint=paint, lights=True)
    for eye, name in (((d * 0.75, Hh * 1.1, d * 0.8), 'night: headlights + DRL + indicators'),
                      ((-d * 0.8, Hh * 1.0, -d * 0.75), 'night: tail / brake / reverse / indicators')):
        im = render(tn, eye, (0, Hh * 0.45, 0), size=size, fov=28, bg=(16, 20, 30), dark=True)
        label(im, name, True)
        imgs.append(im)
    cols = 2
    rows = (len(imgs) + 1) // 2
    out = Image.new('RGB', (size[0] * cols, size[1] * rows), (255, 255, 255))
    for i, im in enumerate(imgs):
        out.paste(im, ((i % cols) * size[0], (i // cols) * size[1]))
    out.save(path)
    return imgs


def icon(mesh, wmesh, meta, path, paint=(200, 30, 35), size=64):
    tris = collect(mesh, wmesh, meta, paint=paint, lights=False)
    L, Hh = meta['length'], meta['height']
    d = max(L, 3.5) * 1.35
    im = render(tris, (d * 0.85, Hh * 0.9 + 0.8, d * 0.7), (0, Hh * 0.42, 0), size=(size * 2, size * 2), fov=34, bg=(255, 0, 255), ground=False,
                ss=2)
    im = im.resize((size, size), Image.LANCZOS).convert('RGBA')
    px = im.load()
    for y in range(size):
        for x in range(size):
            r_, g_, b_, a_ = px[x, y]
            if r_ > 180 and b_ > 180 and g_ < 110:
                px[x, y] = (0, 0, 0, 0)
    im.save(path)


def quick(mesh, wmesh, meta, path, paint=(200, 30, 35)):
    """Fast 4-view sheet for design iteration."""
    tris = collect(mesh, wmesh, meta, paint=paint, lights=False)
    L, Hh, Wd = meta['length'], meta['height'], meta['width']
    d = max(L, 4.5) * 1.55
    sh = (L * 0.95, Wd)
    views = [((d * 0.75, Hh * 1.3, d * 0.8), 'front 3/4'), ((-d * 0.8, Hh * 1.2, -d * 0.75), 'rear 3/4'), ((d * 1.25, Hh * 0.55, 0.0), 'side'),
             ((0.0, Hh * 0.8, d * 1.1), 'front')]
    out = Image.new('RGB', (1200, 720), (255, 255, 255))
    for i, (eye, name) in enumerate(views):
        im = render(tris, eye, (0, Hh * 0.45, 0), size=(600, 360), fov=28 if name != 'front' else 24, shadow=sh, ss=1)
        label(im, name + ' - ' + meta['name'])
        out.paste(im, ((i % 2) * 600, (i // 2) * 360))
    out.save(path)
