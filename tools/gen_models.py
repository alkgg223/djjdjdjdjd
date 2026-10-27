"""Generates the smooth OBJ creature models in assets/eggsteal/models/creature/.

Each model is built from rounded primitives (ellipsoids, tapered tubes, tori) in a cute "chibi" style: big heads,
big shiny eyes with highlights, blush, little mouths, paws and fluffy tails. Parts that move in game are written
as separate groups:  g <colour> <animation> <pivot x y z> <phase>
Colours: main, accent, belly, shade, eye, white, blush, dark, glow, gold, pink (see CreatureRenderer).
Animations: blink, wingL/wingR, tail, earL/earR, sway, spin, finL/finR.
Each vertex carries a baked shade value (soft ambient occlusion): v x y z shade.

Run from the project root:  python3 tools/gen_models.py   (add --preview to render PNG previews into build/preview)
"""
import math, os, re, sys

R = 'src/main/resources/assets/eggsteal/models/creature/'


# ---------------------------------------------------------------- math helpers
def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def mul(a, k): return (a[0] * k, a[1] * k, a[2] * k)
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def norm(a):
    l = math.sqrt(dot(a, a)) or 1.0
    return (a[0] / l, a[1] / l, a[2] / l)
def lerp(a, b, t): return add(mul(a, 1 - t), mul(b, t))


def rot(rx=0, ry=0, rz=0):
    """Rotation matrix R = Ry * Rx * Rz (degrees): roll, then pitch, then yaw."""
    x, y, z = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    Ry = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    Rz = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    def mm(A, B): return tuple(tuple(sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)) for i in range(3))
    return mm(Ry, mm(Rx, Rz))
def ap(M, v): return (M[0][0] * v[0] + M[0][1] * v[1] + M[0][2] * v[2], M[1][0] * v[0] + M[1][1] * v[1] + M[1][2] * v[2], M[2][0] * v[0] + M[2][1] * v[1] + M[2][2] * v[2])


def facing(n):
    """Euler angles (rx, ry, 0) that turn local +z toward direction n."""
    n = norm(n)
    return (-math.degrees(math.asin(max(-1, min(1, n[1])))), math.degrees(math.atan2(n[0], n[2])), 0)


def bezier(pts, n):
    out = []
    for i in range(n + 1):
        t = i / n
        p = list(pts)
        while len(p) > 1: p = [lerp(p[k], p[k + 1], t) for k in range(len(p) - 1)]
        out.append(p[0])
    return out


# ---------------------------------------------------------------- model + primitives
class Model:
    def __init__(self):
        self.groups = {}   # key -> [verts, norms, shades, quads]
        self.floats = False
        self.sym = False

    def g(self, color, anim='none', pivot=(0, 0, 0), phase=0.0):
        key = (color, anim, tuple(round(p, 4) for p in pivot), round(phase, 3))
        if key not in self.groups: self.groups[key] = [[], [], [], []]
        return self.groups[key]

    # an ellipsoid; c = centre, r = radii, rot = euler degrees; cut = keep only y >= cut (local, -1..1) for domes
    def ell(self, color, c, r, rt=(0, 0, 0), anim='none', pivot=None, phase=0.0, seg=None, cut=-1.0, shade=1.0):
        G = self.g(color, anim, pivot if pivot is not None else c, phase)
        V, N, S, Q = G
        M = rot(*rt)
        mr = max(r)
        if seg is None: seg = max(8, min(22, int(7 + mr * 42)))
        ring = max(5, seg // 2 + 1)
        th0 = math.acos(max(-1, min(1, cut))) if cut > -1 else math.pi
        base = len(V)
        for i in range(ring + 1):
            th = th0 * i / ring
            for j in range(seg + 1):
                ph = 2 * math.pi * j / seg
                u = (math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph))
                p = add(c, ap(M, (r[0] * u[0], r[1] * u[1], r[2] * u[2])))
                n = norm(ap(M, (u[0] / r[0], u[1] / r[1], u[2] / r[2])))
                V.append(p); N.append(n); S.append(shade * (0.82 + 0.18 * (u[1] + 1) / 2))
        for i in range(ring):
            for j in range(seg):
                a = base + i * (seg + 1) + j
                Q.append((a, a + seg + 1, a + seg + 2, a + 1))
        if cut > -1:  # close the dome with a flat disc
            cy = math.cos(th0)
            ctr = len(V)
            V.append(add(c, ap(M, (0, r[1] * cy, 0)))); N.append(norm(ap(M, (0, -1, 0)))); S.append(shade * 0.8)
            last = base + ring * (seg + 1)
            for j in range(seg):
                Q.append((ctr, last + j + 1, last + j, ctr))
        return self

    def sph(self, color, c, r, **kw): return self.ell(color, c, (r, r, r), **kw)

    # a tube along a path with per-point radii; flat < 1 squashes the cross-section along `flat_dir`
    def tube(self, color, pts, radii, seg=10, flat=1.0, flat_dir=(0, 0, 1), anim='none', pivot=None, phase=0.0, cap=True, shade=1.0):
        G = self.g(color, anim, pivot if pivot is not None else pts[0], phase)
        V, N, S, Q = G
        n = len(pts)
        base = len(V)
        for i in range(n):
            T = norm(sub(pts[min(i + 1, n - 1)], pts[max(i - 1, 0)]))
            W = sub(flat_dir, mul(T, dot(flat_dir, T)))
            if dot(W, W) < 1e-6: W = sub((0, 1, 0), mul(T, T[1]))
            if dot(W, W) < 1e-6: W = sub((1, 0, 0), mul(T, T[0]))
            W = norm(W)
            U = norm(cross(T, W))
            for j in range(seg + 1):
                a = 2 * math.pi * j / seg
                off = add(mul(U, math.cos(a) * radii[i]), mul(W, math.sin(a) * radii[i] * flat))
                nn = norm(add(mul(U, math.cos(a)), mul(W, math.sin(a) / max(flat, 0.05))))
                V.append(add(pts[i], off)); N.append(nn)
                S.append(shade * (0.84 + 0.16 * (nn[1] + 1) / 2))
        for i in range(n - 1):
            for j in range(seg):
                a = base + i * (seg + 1) + j
                Q.append((a, a + seg + 1, a + seg + 2, a + 1))
        if cap:
            for end, rr in ((pts[0], radii[0]), (pts[-1], radii[-1])):
                if rr > 0.006:
                    self.ell(color, end, (rr, rr * (flat if flat < 1 else 1), rr), anim=anim, pivot=pivot if pivot is not None else pts[0], phase=phase,
                             seg=seg, shade=shade, rt=facing(flat_dir) if flat < 1 else (0, 0, 0))
        return self

    def cone(self, color, a, b, r0, r1=0.0, steps=5, bend=(0, 0, 0), **kw):
        mid = add(lerp(a, b, 0.5), bend)
        pts = bezier([a, mid, b], steps)
        radii = [r0 + (r1 - r0) * (i / steps) ** 0.9 for i in range(steps + 1)]
        return self.tube(color, pts, radii, **kw)

    def torus(self, color, c, R, r, rt=(0, 0, 0), seg=24, tseg=8, arc=360.0, anim='none', pivot=None, phase=0.0, shade=1.0):
        G = self.g(color, anim, pivot if pivot is not None else c, phase)
        V, N, S, Q = G
        M = rot(*rt)
        base = len(V)
        full = arc >= 360
        segs = seg if full else max(3, int(seg * arc / 360))
        for i in range(segs + 1):
            u = math.radians(arc) * i / segs
            for j in range(tseg + 1):
                v = 2 * math.pi * j / tseg
                p = ((R + r * math.cos(v)) * math.cos(u), r * math.sin(v), (R + r * math.cos(v)) * math.sin(u))
                n = (math.cos(v) * math.cos(u), math.sin(v), math.cos(v) * math.sin(u))
                V.append(add(c, ap(M, p))); N.append(norm(ap(M, n))); S.append(shade * (0.85 + 0.15 * (ap(M, n)[1] + 1) / 2))
        for i in range(segs):
            for j in range(tseg):
                a = base + i * (tseg + 1) + j
                Q.append((a, a + tseg + 1, a + tseg + 2, a + 1))
        return self

    def mirror(self, fn):
        """Calls fn(side) for side = +1 and -1 (left and right)."""
        fn(1); fn(-1)
        return self

    def write(self, path):
        lines = ['# generated by tools/gen_models.py']
        if self.floats: lines.append('# float')
        vi = 1
        body = []
        allv = [v for G in self.groups.values() for v in G[0]]
        ys = [v[1] for v in allv] or [0]
        ymin, ymax = min(ys), max(ys)
        for (color, anim, pivot, phase), (V, N, S, Q) in self.groups.items():
            for p, n, s in zip(V, N, S):
                h = (p[1] - ymin) / max(1e-6, ymax - ymin)
                body.append(f'v {p[0]:.4f} {p[1]:.4f} {p[2]:.4f} {max(0.55, min(1.0, s * (0.86 + 0.14 * h))):.3f}')
            for n in N: body.append(f'vn {n[0]:.3f} {n[1]:.3f} {n[2]:.3f}')
            if anim == 'none': body.append(f'g {color}')
            else: body.append(f'g {color} {anim} {pivot[0]:.4f} {pivot[1]:.4f} {pivot[2]:.4f} {phase:.2f}')
            for q in Q:
                idx = [vi + k for k in q]
                if idx[0] == idx[3] or len(set(idx)) < 4:
                    u = []
                    for k in idx:
                        if k not in u: u.append(k)
                    if len(u) < 3: continue
                    body.append('f ' + ' '.join(f'{k}//{k}' for k in u))
                else:
                    body.append('f ' + ' '.join(f'{k}//{k}' for k in idx))
            vi += len(V)
        with open(path, 'w') as f:
            f.write('\n'.join(lines + body) + '\n')

    def stats(self):
        return sum(len(G[3]) for G in self.groups.values())


# ---------------------------------------------------------------- face kit
def surf(c, r, az, el):
    """Point and normal on an ellipsoid (centre c, radii r) at azimuth az (0 = front +z, + = left/+x) and elevation el (degrees)."""
    a, e = math.radians(az), math.radians(el)
    u = (math.sin(a) * math.cos(e), math.sin(e), math.cos(a) * math.cos(e))
    p = add(c, (r[0] * u[0], r[1] * u[1], r[2] * u[2]))
    n = norm((u[0] / r[0], u[1] / r[1], u[2] / r[2]))
    return p, n


def face(m, hc, hr, eye=0.075, spread=24, el=4, blush=True, mouth='smile', nose=False, snout=None, lashes=False, eyecolor='eye'):
    """Big shiny eyes with highlights, pink cheeks, a small mouth (and an optional nose / snout)."""
    for side in (1, -1):
        p, n = surf(hc, hr, side * spread, el)
        c = sub(p, mul(n, eye * 0.35))
        rt = facing(n)
        m.ell(eyecolor, c, (eye * 0.82, eye, eye * 0.55), rt=rt, anim='blink', pivot=c)
        up = norm(cross(n, cross((0, 1, 0), n))) if abs(n[1]) < 0.99 else (0, 0, 1)
        sidev = norm(cross(up, n))
        hl = add(add(add(c, mul(n, eye * 0.42)), mul(up, eye * 0.38)), mul(sidev, eye * 0.25 * side))
        m.sph('white', hl, eye * 0.3, anim='blink', pivot=c, seg=8)
        hl2 = add(add(add(c, mul(n, eye * 0.45)), mul(up, -eye * 0.35)), mul(sidev, -eye * 0.2 * side))
        m.sph('white', hl2, eye * 0.14, anim='blink', pivot=c, seg=6)
        if lashes:
            m.cone('dark', add(c, mul(up, eye * 0.85)), add(add(c, mul(up, eye * 1.35)), mul(sidev, -eye * 0.6 * side)), eye * 0.12, 0.0, steps=3, seg=5)
        if blush:
            bp, bn = surf(hc, hr, side * (spread + 16), el - 15)
            m.ell('blush', sub(bp, mul(bn, 0.004)), (eye * 0.85, eye * 0.5, eye * 0.2), rt=facing(bn), seg=10)
    if snout is not None:
        sc, sr = snout
        m.ell('belly', sc, sr, seg=14)
        tip, tn = surf(sc, sr, 0, 25)
        m.ell('dark', tip, (sr[0] * 0.38, sr[1] * 0.28, sr[2] * 0.25), rt=facing(tn), seg=10)
        mc, mr, mel = sc, sr, -28
    else:
        if nose:
            np_, nn = surf(hc, hr, 0, el - 12)
            m.ell('dark', np_, (eye * 0.35, eye * 0.25, eye * 0.2), rt=facing(nn), seg=8)
        mc, mr, mel = hc, hr, el - 22
    mouth_line(m, mc, mr, mel, eye, mouth)


def mouth_line(m, c, r, el, eye, kind):
    """A small mouth drawn on the surface: 'smile', 'cat' (w shape) or 'open'."""
    w = math.degrees(eye * 0.55 / r[0])
    dep = math.degrees(eye * 0.3 / r[1])
    def on(az, e):
        p, n = surf(c, r, az, e)
        return add(p, mul(n, 0.003))
    if kind == 'smile':
        pts = [on(w * math.cos(a), el - dep * math.sin(a)) for a in [math.pi * k / 8 for k in range(9)]]
        m.tube('dark', pts, [eye * 0.09] * 9, seg=5)
    elif kind == 'cat':
        for sd in (1, -1):
            pts = [on(sd * w * 0.55 * (1 - math.cos(a)), el - dep * 0.8 * math.sin(a)) for a in [math.pi * k / 6 for k in range(7)]]
            m.tube('dark', pts, [eye * 0.08] * 7, seg=5)
    elif kind == 'open':
        p, n = surf(c, r, 0, el - dep * 0.6)
        m.ell('dark', p, (eye * 0.36, eye * 0.3, eye * 0.12), rt=facing(n), seg=10)
        m.ell('pink', add(p, add(mul(n, 0.004), (0, -eye * 0.1, 0))), (eye * 0.22, eye * 0.12, eye * 0.08), rt=facing(n), seg=8)


# ---------------------------------------------------------------- reusable parts
def ears(m, kind, hc, hr, color='main', inner='pink', spread=32, el=58, size=1.0):
    for side, anim in ((1, 'earL'), (-1, 'earR')):
        p, n = surf(hc, hr, side * spread, el)
        base = sub(p, mul(n, 0.03))
        if kind == 'cat':
            tip = add(base, (side * 0.06 * size, 0.2 * size, 0.0))
            m.cone(color, base, tip, 0.085 * size, 0.0, steps=4, seg=10, flat=0.45, flat_dir=(0, 0, 1), anim=anim, pivot=base)
            m.cone(inner, add(base, (0, 0.02, 0.02)), add(tip, (0, -0.06 * size, 0.02)), 0.05 * size, 0.0, steps=3, seg=8, flat=0.3, flat_dir=(0, 0, 1), anim=anim, pivot=base)
        elif kind == 'fox':
            tip = add(base, (side * 0.08 * size, 0.26 * size, -0.01))
            m.cone(color, base, tip, 0.1 * size, 0.0, steps=5, seg=10, flat=0.42, flat_dir=(0, 0, 1), anim=anim, pivot=base)
            m.cone(inner, add(base, (0, 0.02, 0.025)), add(tip, (0, -0.08 * size, 0.025)), 0.058 * size, 0.0, steps=3, seg=8, flat=0.3, flat_dir=(0, 0, 1), anim=anim, pivot=base)
        elif kind == 'long':
            tip = add(base, (side * 0.07 * size, 0.42 * size, -0.05))
            m.cone(color, base, tip, 0.07 * size, 0.045 * size, steps=6, seg=10, flat=0.5, flat_dir=(0, 0, 1), bend=(side * 0.03, 0, -0.04), anim=anim, pivot=base)
            m.cone(inner, add(base, (0, 0.04, 0.03)), add(tip, (0, -0.04, 0.02)), 0.04 * size, 0.022 * size, steps=5, seg=8, flat=0.3, flat_dir=(0, 0, 1), bend=(side * 0.03, 0, -0.04), anim=anim, pivot=base)
        elif kind == 'floppy':
            tip = add(base, (side * 0.16 * size, -0.2 * size, 0.02))
            m.cone(color, base, tip, 0.075 * size, 0.06 * size, steps=6, seg=10, flat=0.45, flat_dir=(side, 0, 0.3), bend=(side * 0.08, 0.06, 0), anim=anim, pivot=base)
        elif kind == 'round':
            c = add(base, mul(n, 0.04 * size))
            m.ell(color, c, (0.08 * size, 0.08 * size, 0.04 * size), rt=facing(n), anim=anim, pivot=base)
            m.ell(inner, add(c, mul(n, 0.02)), (0.045 * size, 0.045 * size, 0.02 * size), rt=facing(n), anim=anim, pivot=base, seg=10)
        elif kind == 'tuft':
            tip = add(base, (side * 0.07 * size, 0.18 * size, -0.02))
            m.cone(color, base, tip, 0.05 * size, 0.0, steps=4, seg=8, anim=anim, pivot=base)
        elif kind == 'bat':
            tip = add(base, (side * 0.12 * size, 0.24 * size, 0))
            m.cone(color, base, tip, 0.09 * size, 0.0, steps=4, seg=10, flat=0.35, flat_dir=(0, 0, 1), anim=anim, pivot=base)
            m.cone(inner, add(base, (0, 0.02, 0.02)), add(tip, (-side * 0.02, -0.06, 0.02)), 0.055 * size, 0.0, steps=3, seg=8, flat=0.25, flat_dir=(0, 0, 1), anim=anim, pivot=base)


def legs4(m, body_c, body_r, h, r=0.075, color='main', paw='belly', front=0.6, back=-0.55, wide=0.62):
    for sx in (1, -1):
        for fz in (front, back):
            top = (body_c[0] + sx * body_r[0] * wide, body_c[1] - body_r[1] * 0.3, body_c[2] + fz * body_r[2])
            foot = (top[0] + sx * 0.01, r * 0.75, top[2] + 0.02)
            m.tube(color, [top, lerp(top, foot, 0.5), foot], [r * 1.1, r, r * 0.95], seg=10, cap=False)
            m.ell(color, (foot[0], r * 0.75, foot[2] + r * 0.25), (r * 1.05, r * 0.75, r * 1.3), seg=12)
            m.ell(paw, (foot[0], r * 0.32, foot[2] + r * 0.9), (r * 0.6, r * 0.32, r * 0.42), seg=8)


def tail(m, kind, start, color='main', tip='accent', size=1.0):
    s = size
    if kind == 'fluffy':
        pts = bezier([start, add(start, (0, 0.12 * s, -0.16 * s)), add(start, (0, 0.38 * s, -0.22 * s)), add(start, (0, 0.5 * s, -0.08 * s))], 8)
        radii = [0.06 * s + 0.07 * s * math.sin(math.pi * min(1, i / 8 * 1.15)) for i in range(9)]
        m.tube(color, pts[:7], radii[:7], seg=12, anim='tail', pivot=start)
        m.ell(tip, pts[7], (0.085 * s, 0.1 * s, 0.085 * s), anim='tail', pivot=start)
    elif kind == 'long':
        pts = bezier([start, add(start, (0, 0.02, -0.25 * s)), add(start, (0, 0.28 * s, -0.36 * s)), add(start, (0, 0.34 * s, -0.24 * s))], 9)
        m.tube(color, pts, [0.045 * s * (1 - 0.45 * i / 9) for i in range(10)], seg=9, anim='tail', pivot=start)
        m.ell(tip, pts[-1], (0.055 * s, 0.055 * s, 0.055 * s), anim='tail', pivot=start)
    elif kind == 'short':
        m.sph(tip, add(start, (0, 0.02, -0.04)), 0.08 * s, anim='tail', pivot=start)
    elif kind == 'curl':
        pts = [add(start, (0, 0.1 * s * (1 - math.cos(a)), -0.12 * s * math.sin(a) - 0.05 * s * a)) for a in [k * 0.45 for k in range(9)]]
        m.tube(color, pts, [0.04 * s] * 9, seg=8, anim='tail', pivot=start)
    elif kind == 'spade':
        pts = bezier([start, add(start, (0, -0.02, -0.3 * s)), add(start, (0, 0.2 * s, -0.42 * s))], 8)
        m.tube(color, pts, [0.05 * s * (1 - 0.5 * i / 8) for i in range(9)], seg=9, anim='tail', pivot=start)
        m.cone(tip, pts[-1], add(pts[-1], (0, 0.1 * s, -0.08 * s)), 0.07 * s, 0.0, steps=3, seg=8, flat=0.35, flat_dir=(1, 0, 0), anim='tail', pivot=start)
    elif kind == 'fin':
        m.cone(color, start, add(start, (0, 0.03, -0.12 * s)), 0.08 * s, 0.05 * s, steps=3, seg=10, anim='tail', pivot=start)
        e = add(start, (0, 0.03, -0.14 * s))
        for sy in (1, -1):
            m.cone(tip, e, add(e, (0, sy * 0.18 * s, -0.12 * s)), 0.07 * s, 0.0, steps=4, seg=10, flat=0.25, flat_dir=(1, 0, 0), anim='tail', pivot=start)


def feather_wings(m, at, size=1.0, color='accent', folded=False):
    """Rounded bird wings: folded against the body, or spread out with a fan of feathers."""
    for side, anim in ((1, 'wingL'), (-1, 'wingR')):
        root = (at[0] * side, at[1], at[2])
        if folded:
            c = add(root, (side * 0.02 * size, -0.07 * size, -0.05 * size))
            m.ell(color, c, (0.05 * size, 0.14 * size, 0.2 * size), rt=(25, 0, side * 10), anim=anim, pivot=root, seg=16)
            for k in range(3):
                m.ell(color, add(c, (side * 0.01, -0.07 * size - 0.02 * k * size, -0.14 * size - 0.035 * k * size)), (0.035 * size, 0.05 * size, 0.09 * size),
                      rt=(35 + 10 * k, 0, side * 10), anim=anim, pivot=root, seg=10)
        else:
            for k in range(4):
                a = 40 - k * 22
                ln = (0.34 - 0.04 * k) * size
                d = (side * math.cos(math.radians(a)), math.sin(math.radians(a)), 0)
                c = add(root, add(mul(d, ln * 0.55), (0, 0, -0.03 - 0.025 * k)))
                m.ell(color, c, (ln * 0.55, 0.03 * size, 0.07 * size), rt=(0, 0, side * a), anim=anim, pivot=root, seg=12)
            m.ell(color, add(root, (side * 0.1 * size, 0.04 * size, -0.02)), (0.13 * size, 0.06 * size, 0.1 * size), rt=(0, 0, side * 20), anim=anim, pivot=root, seg=14)


def bat_wings(m, at, size=1.0, color='accent'):
    for side, anim in ((1, 'wingL'), (-1, 'wingR')):
        root = (at[0] * side, at[1], at[2])
        for k in range(3):
            tip = add(root, (side * (0.42 - k * 0.09) * size, (0.18 - k * 0.15) * size, -0.04 - 0.02 * k))
            m.cone(color, root, tip, 0.11 * size, 0.01, steps=5, seg=10, flat=0.18, flat_dir=(0, 0.25, 1), bend=(0, 0.05 * size, 0), anim=anim, pivot=root)
        m.cone('dark', root, add(root, (side * 0.44 * size, 0.2 * size, -0.04)), 0.018 * size, 0.006, steps=5, seg=6, bend=(0, 0.05 * size, 0), anim=anim, pivot=root)


def halo(m, c, R=0.17):
    m.torus('gold', c, R, 0.025, seg=26, tseg=8, anim='spin', pivot=c)


def crown(m, c, R=0.11):
    m.torus('gold', c, R, 0.025, seg=20, tseg=6)
    for k in range(5):
        a = 2 * math.pi * k / 5
        b = add(c, (R * math.cos(a), 0.0, R * math.sin(a)))
        m.cone('gold', b, add(b, (0, 0.1, 0)), 0.03, 0.0, steps=2, seg=6)
        m.sph('accent', add(b, (0, 0.11, 0)), 0.018, seg=6)


def horns(m, hc, hr, size=1.0, color='accent', curl=0.0):
    for side in (1, -1):
        p, n = surf(hc, hr, side * 28, 55)
        tip = add(p, (side * (0.06 + curl * 0.1) * size, 0.18 * size, -0.06 * size))
        m.cone(color, sub(p, mul(n, 0.02)), tip, 0.045 * size, 0.0, steps=5, seg=9, bend=(side * curl * 0.08, 0.02, -0.04 * size))


def stars_around(m, c, n=4, R=0.5, s=0.04):
    for k in range(n):
        a = 2 * math.pi * k / n + 0.4
        p = add(c, (R * math.cos(a), 0.1 * math.sin(3 * a), R * math.sin(a)))
        star(m, p, s, color='glow')


def star(m, c, s, color='accent', facing_dir=(0, 0, 1)):
    for k in range(5):
        a = math.pi / 2 + 2 * math.pi * k / 5
        tip = add(c, (s * 1.8 * math.cos(a), s * 1.8 * math.sin(a), 0))
        m.cone(color, c, tip, s * 0.7, 0.0, steps=2, seg=6, flat=0.45, flat_dir=facing_dir)


def spikes(m, start, end, n=4, size=1.0, color='accent'):
    for k in range(n):
        p = lerp(start, end, k / max(1, n - 1))
        m.cone(color, p, add(p, (0, 0.11 * size * (1 - 0.15 * k), -0.03)), 0.045 * size, 0.0, steps=2, seg=7, flat=0.45, flat_dir=(1, 0, 0))


# ---------------------------------------------------------------- body templates
def quad(m, o):
    """Four-legged chibi animal. Returns head centre and radii."""
    s = o.get('size', 1.0)
    lh = o.get('leg', 0.16) * s
    bc = (0, lh + 0.2 * s, -0.02)
    br = (0.25 * s, 0.21 * s, 0.31 * s)
    m.ell('main', bc, br)
    m.ell('belly', add(bc, (0, -0.04 * s, 0.1 * s)), (br[0] * 0.75, br[1] * 0.8, br[2] * 0.75), seg=16)
    legs4(m, bc, br, lh, r=0.068 * s)
    hc = (0, bc[1] + 0.27 * s, 0.24 * s)
    hr = (0.27 * s, 0.24 * s, 0.24 * s)
    m.ell('main', hc, hr)
    if o.get('cheeks', True):
        m.mirror(lambda sd: m.ell('main', add(hc, (sd * 0.13 * s, -0.07 * s, 0.07 * s)), (0.12 * s, 0.1 * s, 0.11 * s), seg=12))
    if o.get('snout'):
        sc = add(hc, (0, -0.07 * s, 0.2 * s))
        face(m, hc, hr, eye=0.062 * s, spread=27, el=10, snout=(sc, (0.1 * s, 0.075 * s, 0.09 * s)), mouth=o.get('mouth', 'cat'))
    else:
        face(m, hc, hr, eye=0.07 * s, spread=24, el=4, nose=True, mouth=o.get('mouth', 'cat'))
    if o.get('ears'): ears(m, o['ears'], hc, hr, size=s, color=o.get('earcolor', 'main'))
    if o.get('tail'): tail(m, o['tail'], (0, bc[1] + 0.05 * s, bc[2] - br[2] * 0.85), size=s, tip=o.get('tailtip', 'accent'))
    return hc, hr


def blob(m, o):
    s = o.get('size', 1.0)
    c = (0, 0.3 * s, 0)
    r = (0.42 * s, 0.31 * s, 0.4 * s)
    m.ell('main', c, r, seg=22)
    m.ell('belly', add(c, (0, -0.06 * s, 0.13 * s)), (r[0] * 0.7, r[1] * 0.7, r[2] * 0.75), seg=16)
    m.mirror(lambda sd: m.ell('main', (sd * 0.2 * s, 0.05 * s, 0.12 * s), (0.11 * s, 0.06 * s, 0.13 * s), seg=10))
    hc, hr = add(c, (0, 0.02, 0)), r
    face(m, hc, hr, eye=0.085 * s, spread=22, el=8, mouth=o.get('mouth', 'smile'))
    if o.get('ears'): ears(m, o['ears'], hc, hr, size=s * 1.1, spread=36, el=48)
    return hc, hr


def bird(m, o):
    s = o.get('size', 1.0)
    c = (0, 0.36 * s, 0)
    r = (0.31 * s, 0.31 * s, 0.3 * s)
    m.ell('main', c, r, seg=22)
    m.ell('belly', add(c, (0, -0.07 * s, 0.1 * s)), (r[0] * 0.72, r[1] * 0.72, r[2] * 0.75), seg=16)
    hc, hr = add(c, (0, 0.05 * s, 0)), r
    face(m, hc, hr, eye=0.075 * s, spread=27, el=12, mouth='none')
    bp, bn = surf(hc, hr, 0, 0)
    m.cone('gold' if o.get('beak', 'gold') == 'gold' else o['beak'], sub(bp, mul(bn, 0.03)), add(bp, (0, -0.03 * s, 0.12 * s)), 0.065 * s, 0.0, steps=3, seg=10, flat=0.7, flat_dir=(0, 1, 0))
    m.mirror(lambda sd: (m.cone('gold', (sd * 0.11 * s, 0.1 * s, 0.03), (sd * 0.11 * s, 0.0, 0.06 * s), 0.025 * s, 0.022 * s, steps=2, seg=6),
                         m.ell('gold', (sd * 0.11 * s, 0.02 * s, 0.1 * s), (0.06 * s, 0.022 * s, 0.07 * s), seg=8)))
    if o.get('wings', True): feather_wings(m, (0.28 * s, 0.4 * s, -0.02), size=s * 0.9, folded=not o.get('spread'))
    m.mirror(lambda sd: m.cone('accent', (sd * 0.04, 0.42 * s, -0.27 * s), (sd * 0.1 * s, 0.5 * s, -0.42 * s), 0.05 * s, 0.0, steps=3, seg=8, flat=0.3, flat_dir=(0, 1, 0), anim='tail', pivot=(0, 0.42 * s, -0.27 * s)))
    if o.get('tuft', True):
        m.cone('accent', add(hc, (0, hr[1] * 0.92, 0.02)), add(hc, (0.02, hr[1] + 0.13 * s, -0.04)), 0.035 * s, 0.0, steps=4, seg=7, bend=(0, 0.02, 0.05))
    return hc, hr


def flyer(m, o):
    s = o.get('size', 1.0)
    m.floats = True
    c = (0, 0.42 * s, 0)
    r = (0.28 * s, 0.27 * s, 0.27 * s)
    m.ell('main', c, r, seg=22)
    m.ell('belly', add(c, (0, -0.06 * s, 0.1 * s)), (r[0] * 0.7, r[1] * 0.7, r[2] * 0.75), seg=14)
    face(m, c, r, eye=0.072 * s, spread=24, el=8, mouth=o.get('mouth', 'smile'))
    m.mirror(lambda sd: m.ell('main', (sd * 0.1 * s, 0.17 * s, 0.05), (0.06 * s, 0.05 * s, 0.07 * s), seg=8))
    if o.get('ears', 'bat'): ears(m, o.get('ears', 'bat'), c, r, size=s, spread=30, el=55)
    return c, r


def fish(m, o):
    s = o.get('size', 1.0)
    m.floats = True
    c = (0, 0.45 * s, 0)
    r = (0.28 * s, 0.27 * s, 0.5 * s)
    m.ell('main', c, r, seg=22)
    m.ell('belly', add(c, (0, -0.1 * s, 0.05 * s)), (r[0] * 0.78, r[1] * 0.62, r[2] * 0.85), seg=18)
    hc, hr = add(c, (0, 0.02, 0.14 * s)), (r[0], r[1], r[2] * 0.75)
    face(m, hc, hr, eye=0.07 * s, spread=34, el=10, mouth='smile', blush=True)
    tail(m, 'fin', add(c, (0, 0.0, -r[2] * 0.9)), color='main', tip='accent', size=s * 1.3)
    for side, anim in ((1, 'finL'), (-1, 'finR')):
        root = (side * r[0] * 0.85, c[1] - 0.08 * s, c[2] + 0.08 * s)
        m.cone('accent', root, add(root, (side * 0.16 * s, -0.08 * s, -0.08 * s)), 0.07 * s, 0.0, steps=3, seg=9, flat=0.25, flat_dir=(0, 1, 0), anim=anim, pivot=root)
    return hc, hr


def crab(m, o):
    s = o.get('size', 1.0)
    c = (0, 0.27 * s, 0)
    r = (0.38 * s, 0.2 * s, 0.3 * s)
    m.ell('main', c, r, seg=22)
    m.ell('belly', add(c, (0, -0.05 * s, 0.06)), (r[0] * 0.8, r[1] * 0.6, r[2] * 0.8), seg=16)
    for side in (1, -1):
        stalk = (side * 0.13 * s, 0.4 * s, 0.14 * s)
        top = add(stalk, (side * 0.02, 0.14 * s, 0.02))
        m.tube('main', [stalk, top], [0.03 * s, 0.028 * s], seg=7)
        ec = add(top, (0, 0.05 * s, 0))
        m.sph('white', ec, 0.075 * s, seg=12)
        m.ell('eye', add(ec, (0, 0, 0.04 * s)), (0.045 * s, 0.055 * s, 0.04 * s), anim='blink', pivot=ec, seg=10)
        m.sph('white', add(ec, (side * 0.012, 0.022 * s, 0.075 * s)), 0.015 * s, anim='blink', pivot=ec, seg=6)
        sh = (side * 0.34 * s, 0.3 * s, 0.18 * s)
        claw = (side * 0.52 * s, 0.38 * s, 0.32 * s)
        m.tube('main', [sh, lerp(sh, claw, 0.5), claw], [0.05 * s, 0.045 * s, 0.05 * s], seg=8)
        m.ell(o.get('claw', 'accent'), add(claw, (0, 0.03, 0.05)), (0.12 * s, 0.09 * s, 0.12 * s), seg=14, anim='sway', pivot=claw, phase=1 if side > 0 else 2.5)
        m.cone(o.get('claw', 'accent'), add(claw, (side * 0.02, 0.06, 0.13 * s)), add(claw, (side * 0.0, 0.1 * s, 0.24 * s)), 0.05 * s, 0.0, steps=3, seg=8, anim='sway', pivot=claw, phase=1 if side > 0 else 2.5)
        for k in range(3):
            hip = (side * 0.3 * s, 0.22 * s, (0.08 - 0.13 * k) * s)
            knee = add(hip, (side * 0.14 * s, 0.06 * s, 0))
            foot = add(knee, (side * 0.06 * s, -0.25 * s, 0))
            m.tube('main', [hip, knee, foot], [0.035 * s, 0.03 * s, 0.018 * s], seg=7)
    mouth_line(m, c, r, -2, 0.06, 'smile')
    m.mirror(lambda sd: m.ell('blush', add(surf(c, r, sd * 30, -2)[0], (0, 0, -0.005)), (0.05, 0.03, 0.02), rt=facing(surf(c, r, sd * 30, -2)[1]), seg=8))
    return c, r


def jelly(m, o):
    s = o.get('size', 1.0)
    m.floats = True
    c = (0, 0.56 * s, 0)
    r = (0.36 * s, 0.32 * s, 0.36 * s)
    m.ell('main', c, r, cut=-0.25, seg=22)
    m.ell('belly', add(c, (0, -0.06 * s, 0)), (r[0] * 0.98, r[1] * 0.2, r[2] * 0.98), seg=18)
    face(m, add(c, (0, 0.0, 0)), r, eye=0.07 * s, spread=24, el=6, mouth='smile')
    n = o.get('tentacles', 7)
    for k in range(n):
        a = 2 * math.pi * k / n
        root = add(c, (0.22 * s * math.cos(a), -0.08 * s, 0.22 * s * math.sin(a)))
        pts = [add(root, (0.03 * math.sin(i * 0.9 + k) * s, -0.06 * i * s, 0.03 * math.cos(i * 0.9 + k) * s)) for i in range(7)]
        m.tube('accent', pts, [0.04 * s * (1 - 0.6 * i / 6) for i in range(7)], seg=7, anim='sway', pivot=root, phase=k * 0.9)
    return c, r


def star_t(m, o):
    """A starfish standing on two of its arms, waving with the other three."""
    s = o.get('size', 1.0)
    c = (0, 0.42 * s, 0)
    r = (0.2 * s, 0.2 * s, 0.12 * s)
    m.ell('main', c, r, seg=18)
    for k in range(5):
        a = math.pi / 2 + 2 * math.pi * k / 5
        tip = add(c, (0.4 * s * math.cos(a), 0.4 * s * math.sin(a), 0))
        moving = k in (0, 1, 4)
        kw = dict(anim='sway', pivot=c, phase=k * 1.2) if moving else {}
        m.cone('main', c, tip, 0.15 * s, 0.045 * s, steps=5, seg=10, flat=0.55, flat_dir=(0, 0, 1), **kw)
        for d in (0.45, 0.68, 0.88):
            m.sph('accent', add(lerp(c, tip, d), (0, 0, 0.06 * s * (1 - d * 0.5))), 0.022 * s, seg=6, **kw)
    face(m, c, r, eye=0.055 * s, spread=30, el=12, mouth='smile', blush=True)
    return c, r


def seahorse(m, o):
    s = o.get('size', 1.0)
    m.floats = True
    spine = bezier([(0, 0.08 * s, -0.12 * s), (0, 0.1 * s, 0.18 * s), (0, 0.55 * s, 0.12 * s), (0, 0.78 * s, -0.02 * s)], 10)
    m.tube('main', spine, [0.06 * s + 0.12 * s * math.sin(math.pi * i / 10) for i in range(11)], seg=14)
    for k in range(4):
        m.torus('belly', add(spine[3 + k], (0, 0, 0.02)), 0.12 * s - 0.01 * k, 0.018 * s, rt=(90, 0, 0), seg=16, tseg=5)
    curl = [(0, 0.08 * s - 0.06 * s * math.sin(a), -0.12 * s - 0.1 * s * (1 - math.cos(a))) for a in [k * 0.5 for k in range(9)]]
    m.tube('main', curl, [0.06 * s * (1 - 0.6 * i / 8) for i in range(9)], seg=9, anim='tail', pivot=curl[0])
    hc = (0, 0.92 * s, 0.03 * s)
    hr = (0.16 * s, 0.16 * s, 0.17 * s)
    m.ell('main', hc, hr, seg=18)
    m.cone('main', add(hc, (0, -0.04 * s, 0.1 * s)), add(hc, (0, -0.08 * s, 0.3 * s)), 0.07 * s, 0.045 * s, steps=3, seg=10)
    face(m, hc, hr, eye=0.055 * s, spread=40, el=10, mouth='none')
    spikes(m, add(hc, (0, 0.14 * s, -0.05)), (0, 0.45 * s, -0.12 * s), n=5, size=s * 0.9)
    for side, anim in ((1, 'finL'), (-1, 'finR')):
        root = (side * 0.1 * s, 0.6 * s, -0.04 * s)
        m.cone('accent', root, add(root, (side * 0.12 * s, 0.04 * s, -0.08 * s)), 0.06 * s, 0.0, steps=3, seg=8, flat=0.25, flat_dir=(0, 0, 1), anim=anim, pivot=root)
    return hc, hr


def octo(m, o):
    s = o.get('size', 1.0)
    c = (0, 0.5 * s, 0)
    r = (0.33 * s, 0.33 * s, 0.32 * s)
    m.ell('main', c, r, seg=22)
    face(m, add(c, (0, -0.05 * s, 0)), r, eye=0.08 * s, spread=24, el=-4, mouth='open')
    for k in range(8):
        a = 2 * math.pi * k / 8 + 0.2
        root = add(c, (0.2 * s * math.cos(a), -0.24 * s, 0.2 * s * math.sin(a)))
        out = (math.cos(a), 0, math.sin(a))
        pts = bezier([root, add(root, (0, -0.16 * s, 0)), add(root, add(mul(out, 0.22 * s), (0, -0.2 * s, 0))), add(root, add(mul(out, 0.3 * s), (0, -0.06 * s, 0)))], 8)
        m.tube('main', pts, [0.075 * s * (1 - 0.7 * i / 8) for i in range(9)], seg=8, anim='sway', pivot=root, phase=k * 0.8)
        for i in (2, 4, 6):
            m.sph('pink', add(pts[i], (0, -0.04 * s * (1 - i / 9), 0)), 0.018 * s, seg=6, anim='sway', pivot=root, phase=k * 0.8)
    return c, r


def turtle(m, o):
    s = o.get('size', 1.0)
    c = (0, 0.22 * s, 0)
    m.ell('belly', add(c, (0, -0.06 * s, 0)), (0.3 * s, 0.08 * s, 0.36 * s), seg=18)
    m.ell('accent', add(c, (0, 0.0, 0)), (0.33 * s, 0.24 * s, 0.38 * s), cut=-0.05, seg=22)
    for (x, z) in [(0, 0), (0.15, 0.12), (-0.15, 0.12), (0.15, -0.14), (-0.15, -0.14)]:
        p, n = surf(c, (0.33 * s, 0.24 * s, 0.38 * s), math.degrees(math.atan2(x, z)) if (x or z) else 0, 90 - math.hypot(x, z) * 180)
        m.ell('main', sub(p, mul(n, 0.02)), (0.1 * s, 0.1 * s, 0.03 * s), rt=facing(n), seg=12)
    hc = (0, 0.3 * s, 0.44 * s)
    hr = (0.16 * s, 0.15 * s, 0.15 * s)
    m.cone('main', (0, 0.22 * s, 0.25 * s), hc, 0.09 * s, 0.09 * s, steps=3, seg=10)
    m.ell('main', hc, hr, seg=18)
    face(m, hc, hr, eye=0.05 * s, spread=30, el=10, mouth='smile')
    for sx in (1, -1):
        for sz in (1, -1):
            root = (sx * 0.24 * s, 0.14 * s, sz * 0.22 * s)
            m.ell('main', add(root, (sx * 0.08 * s, -0.06 * s, sz * 0.03)), (0.1 * s, 0.05 * s, 0.07 * s), rt=(0, sx * sz * 25, 0), seg=12, anim='sway', pivot=root, phase=sx + sz)
    m.cone('main', (0, 0.18 * s, -0.36 * s), (0, 0.14 * s, -0.48 * s), 0.05 * s, 0.0, steps=2, seg=8, anim='tail', pivot=(0, 0.18 * s, -0.36 * s))
    return hc, hr


TEMPLATES = dict(quad=quad, blob=blob, bird=bird, flyer=flyer, fish=fish, crab=crab, jelly=jelly, star=star_t, seahorse=seahorse, octo=octo, turtle=turtle)


# ---------------------------------------------------------------- one-off base creatures
def slime_bun(m):
    c = (0, 0.3, 0)
    r = (0.44, 0.3, 0.42)
    m.ell('main', c, r, seg=22)
    m.ell('belly', (0, 0.42, 0), (0.3, 0.16, 0.28), seg=16)  # glossy top
    m.sph('white', (-0.16, 0.5, 0.12), 0.06, seg=10)
    m.sph('white', (-0.06, 0.55, 0.06), 0.03, seg=8)
    m.mirror(lambda sd: m.ell('main', (sd * 0.24, 0.05, 0.14), (0.12, 0.06, 0.13), seg=10))
    face(m, c, r, eye=0.085, spread=22, el=6, mouth='open')
    m.cone('accent', (0, 0.58, 0), (0.03, 0.72, -0.03), 0.04, 0.0, steps=4, seg=8, bend=(0.02, 0, 0.03))
    m.ell('accent', (0.07, 0.7, -0.03), (0.06, 0.025, 0.04), rt=(0, 0, -30), seg=10)


def chick_puff(m):
    hc, hr = bird(m, dict(tuft=True))


def mushy(m):
    c = (0, 0.24, 0)
    r = (0.2, 0.22, 0.2)
    m.ell('belly', c, r, seg=18)
    face(m, add(c, (0, -0.01, 0)), r, eye=0.055, spread=28, el=4, mouth='smile')
    m.mirror(lambda sd: m.ell('belly', (sd * 0.1, 0.04, 0.06), (0.07, 0.04, 0.08), seg=10))
    m.mirror(lambda sd: m.cone('belly', (sd * 0.17, 0.26, 0.0), (sd * 0.27, 0.18, 0.05), 0.04, 0.03, steps=2, seg=7))
    cap = (0, 0.5, 0)
    m.ell('main', cap, (0.44, 0.26, 0.44), cut=-0.1, seg=22)
    m.torus('main', (0, 0.48, 0), 0.38, 0.06, seg=26, tseg=8)
    for (az, el, rr) in [(0, 40, 0.07), (60, 25, 0.06), (-60, 25, 0.06), (130, 35, 0.07), (-130, 35, 0.07), (180, 55, 0.06), (30, 75, 0.06), (-100, 70, 0.05), (95, 60, 0.05)]:
        p, n = surf(cap, (0.44, 0.26, 0.44), az, el)
        m.ell('white', p, (rr, rr, rr * 0.35), rt=facing(n), seg=10)


def pebble_pup(m):
    hc, hr = quad(m, dict(ears='floppy', tail='short', snout=True, mouth='smile'))
    m.mirror(lambda sd: m.ell('accent', add(hc, (sd * 0.12, 0.12, -0.05)), (0.06, 0.04, 0.05), seg=8))


def fox_kit(m):
    quad(m, dict(ears='fox', tail='fluffy', snout=True, earcolor='main'))


def frog_pop(m):
    c = (0, 0.26, 0)
    r = (0.42, 0.25, 0.38)
    m.ell('main', c, r, seg=22)
    m.ell('accent', add(c, (0, -0.07, 0.1)), (0.32, 0.16, 0.28), seg=16)
    for side in (1, -1):
        ec = (side * 0.2, 0.5, 0.18)
        m.sph('main', ec, 0.13, seg=16)
        e = add(ec, (side * 0.01, 0.01, 0.08))
        m.ell('eye', e, (0.075, 0.085, 0.05), anim='blink', pivot=e, seg=12)
        m.sph('white', add(e, (side * 0.02, 0.03, 0.045)), 0.025, anim='blink', pivot=e, seg=8)
        m.ell('blush', (side * 0.28, 0.3, 0.3), (0.06, 0.035, 0.02), rt=(0, side * 40, 0), seg=10)
        foot = (side * 0.34, 0.03, 0.26)
        m.ell('main', foot, (0.13, 0.04, 0.12), seg=12)
        for k in (-1, 0, 1):
            m.sph('accent', add(foot, (k * 0.07, 0.01, 0.11)), 0.035, seg=8)
        m.ell('main', (side * 0.36, 0.14, -0.16), (0.12, 0.12, 0.16), seg=14)
    mouth_line(m, c, r, 14, 0.2, 'smile')


def bat_mochi(m):
    hc, hr = flyer(m, dict(ears='bat'))
    bat_wings(m, (0.24, 0.48, -0.04))
    m.mirror(lambda sd: m.cone('white', add(hc, (sd * 0.035, -0.12, 0.24)), add(hc, (sd * 0.035, -0.17, 0.235)), 0.016, 0.0, steps=2, seg=6))


def cloud_cat(m):
    hc, hr = quad(m, dict(ears='cat', tail='fluffy'))
    for (x, y, z, rr) in [(0.22, 0.38, 0.05, 0.12), (-0.22, 0.38, 0.05, 0.12), (0.18, 0.32, -0.25, 0.12), (-0.18, 0.32, -0.25, 0.12), (0, 0.5, -0.22, 0.14)]:
        m.sph('accent', (x, y, z), rr, seg=14)


def panda_bean(m):
    bc = (0, 0.3, -0.02)
    m.ell('main', bc, (0.27, 0.28, 0.25), seg=22)
    m.ell('belly', add(bc, (0, -0.02, 0.12)), (0.2, 0.22, 0.15), seg=16)
    hc = (0, 0.7, 0.04)
    hr = (0.27, 0.23, 0.23)
    m.ell('main', hc, hr, seg=22)
    for side in (1, -1):
        p, n = surf(hc, hr, side * 24, 4)
        m.ell('accent', sub(p, mul(n, 0.02)), (0.085, 0.1, 0.05), rt=add(facing(n), (0, 0, side * 25)), seg=12)
        m.ell('accent', (side * 0.25, 0.32, 0.08), (0.08, 0.15, 0.08), rt=(-30, 0, side * 20), seg=12)  # arms
        m.ell('accent', (side * 0.15, 0.08, 0.15), (0.09, 0.08, 0.12), seg=12)  # feet
        m.ell('pink', (side * 0.15, 0.08, 0.26), (0.05, 0.04, 0.02), seg=8)
    face(m, hc, hr, eye=0.055, spread=24, el=4, nose=True, mouth='cat')
    ears(m, 'round', hc, hr, color='accent', inner='accent', spread=38, el=52, size=1.1)


def dragon_pup(m):
    hc, hr = quad(m, dict(tail='spade', snout=True, mouth='smile'))
    horns(m, hc, hr, size=1.0, color='accent')
    bat_wings(m, (0.18, 0.5, -0.1), size=0.8)
    spikes(m, (0, 0.62, -0.05), (0, 0.5, -0.3), n=4, size=0.8)


def ice_fox(m):
    hc, hr = quad(m, dict(ears='fox', tail='fluffy', snout=True))
    for k in range(5):
        a = -0.9 + k * 0.45
        b = add(hc, (0.2 * math.sin(a), hr[1] * 0.8, -0.05 + 0.04 * math.cos(a)))
        m.cone('glow', b, add(b, (0.05 * math.sin(a), 0.14 - abs(a) * 0.05, -0.02)), 0.03, 0.0, steps=2, seg=6)


def lava_mole(m):
    c = (0, 0.28, 0)
    r = (0.36, 0.28, 0.4)
    m.ell('main', c, r, seg=22)
    m.ell('belly', add(c, (0, -0.04, 0.12)), (0.25, 0.2, 0.28), seg=16)
    hc = (0, 0.42, 0.22)
    hr = (0.24, 0.2, 0.22)
    m.ell('main', hc, hr, seg=18)
    face(m, hc, hr, eye=0.05, spread=30, el=12, snout=(add(hc, (0, -0.06, 0.2)), (0.09, 0.07, 0.09)), mouth='smile')
    m.sph('glow', add(hc, (0, -0.02, 0.32)), 0.05, seg=10)
    for side in (1, -1):
        claw = (side * 0.3, 0.1, 0.25)
        m.ell('main', claw, (0.11, 0.06, 0.1), seg=12)
        for k in (-1, 0, 1):
            m.cone('glow', add(claw, (k * 0.04, 0.0, 0.07)), add(claw, (k * 0.05, -0.04, 0.16)), 0.02, 0.0, steps=2, seg=6)
    for (az, el) in [(140, 50), (-140, 50), (180, 70), (100, 30), (-100, 30), (170, 25)]:
        p, n = surf(c, r, az, el)
        m.ell('glow', p, (0.07, 0.07, 0.02), rt=facing(n), seg=10)


def star_bunny(m):
    hc, hr = quad(m, dict(ears='long', tail='short', tailtip='belly'))
    star(m, add(hc, (0.13, 0.17, 0.12)), 0.05, color='glow')
    stars_around(m, (0, 0.6, 0), n=3, R=0.45, s=0.035)


def ghost_moth(m):
    m.floats = True
    bc = (0, 0.45, 0)
    m.ell('main', bc, (0.16, 0.26, 0.16), seg=18)
    m.ell('belly', add(bc, (0, -0.1, 0.04)), (0.13, 0.15, 0.13), seg=14)
    hc = (0, 0.8, 0.03)
    hr = (0.18, 0.17, 0.17)
    m.ell('main', hc, hr, seg=18)
    m.torus('belly', (0, 0.66, 0.0), 0.14, 0.05, seg=20, tseg=7)  # fluffy collar
    face(m, hc, hr, eye=0.06, spread=32, el=6, mouth='smile')
    for side in (1, -1):
        root = (side * 0.06, 0.95, 0.05)
        pts = bezier([root, add(root, (side * 0.05, 0.12, 0.05)), add(root, (side * 0.15, 0.16, 0.02))], 6)
        m.tube('accent', pts, [0.012] * 7, seg=5, anim='earL' if side > 0 else 'earR', pivot=root)
        m.ell('accent', pts[-1], (0.035, 0.05, 0.02), rt=(0, 0, side * 40), seg=8, anim='earL' if side > 0 else 'earR', pivot=root)
    for side, anim in ((1, 'wingL'), (-1, 'wingR')):
        root = (side * 0.1, 0.55, -0.06)
        m.ell('accent', add(root, (side * 0.3, 0.12, -0.02)), (0.3, 0.26, 0.025), rt=(0, 0, side * 15), seg=18, anim=anim, pivot=root)
        m.ell('belly', add(root, (side * 0.32, 0.14, 0.0)), (0.12, 0.1, 0.02), rt=(0, 0, side * 15), seg=12, anim=anim, pivot=root)
        m.ell('accent', add(root, (side * 0.22, -0.12, -0.02)), (0.18, 0.14, 0.02), rt=(0, 0, -side * 20), seg=14, anim=anim, pivot=root)


def crystal_deer(m):
    hc, hr = quad(m, dict(ears='round', tail='short', leg=0.3, snout=True, tailtip='belly', mouth='smile'))
    for side in (1, -1):
        base = add(hc, (side * 0.1, hr[1] * 0.85, -0.02))
        m.cone('glow', base, add(base, (side * 0.06, 0.25, -0.02)), 0.035, 0.0, steps=3, seg=6)
        m.cone('glow', add(base, (side * 0.03, 0.1, 0)), add(base, (side * 0.16, 0.2, 0.02)), 0.025, 0.0, steps=2, seg=6)
        m.cone('glow', add(base, (side * 0.04, 0.16, 0)), add(base, (side * 0.0, 0.28, 0.08)), 0.02, 0.0, steps=2, seg=6)
    for (x, z) in [(0.12, -0.05), (-0.1, -0.15), (0.06, 0.12), (-0.14, 0.05)]:
        m.ell('belly', (x, 0.55, z), (0.04, 0.02, 0.04), seg=8)


def galaxy_whale(m):
    hc, hr = fish(m, dict(size=1.25))
    m.floats = True
    m.cone('glow', (0, 0.78, 0.05), (0, 0.98, 0.05), 0.04, 0.0, steps=3, seg=8)  # water spout
    for k in range(3):
        a = 2 * math.pi * k / 3
        m.sph('glow', (0.07 * math.cos(a), 1.02, 0.05 + 0.07 * math.sin(a)), 0.03, seg=8)
    for (az, el) in [(150, 40), (-150, 40), (180, 60), (120, 20), (-120, 20), (160, 10)]:
        p, n = surf((0, 0.56, 0), (0.35, 0.34, 0.62), az, el)
        star(m, add(p, mul(n, 0.01)), 0.028, color='glow', facing_dir=n)


BASE = dict(slime_bun=slime_bun, chick_puff=chick_puff, mushy=mushy, pebble_pup=pebble_pup, fox_kit=fox_kit, frog_pop=frog_pop,
            bat_mochi=bat_mochi, cloud_cat=cloud_cat, panda_bean=panda_bean, dragon_pup=dragon_pup, ice_fox=ice_fox,
            lava_mole=lava_mole, star_bunny=star_bunny, ghost_moth=ghost_moth, crystal_deer=crystal_deer, galaxy_whale=galaxy_whale)


# ---------------------------------------------------------------- pack creatures: template + features
def F(m, name, hc, hr, *a):
    if name == 'halo': halo(m, add(hc, (0, hr[1] + 0.12, -0.02)), *a)
    elif name == 'crown': crown(m, add(hc, (0, hr[1] * 0.85, 0)))
    elif name == 'horns': horns(m, hc, hr, *a)
    elif name == 'curlhorns':
        for side in (1, -1):
            p, n = surf(hc, hr, side * 45, 40)
            pts = [add(p, (side * (0.05 + 0.1 * math.sin(t)), 0.1 * (1 - math.cos(t)) - 0.04 * t, -0.08 * math.sin(t) * 1.4)) for t in [k * 0.5 for k in range(9)]]
            m.tube('accent', pts, [0.06 * (1 - 0.6 * i / 8) for i in range(9)], seg=9)
    elif name == 'unicorn':
        p, n = surf(hc, hr, 0, 50)
        tip = add(p, (0, 0.24, 0.08))
        m.cone('gold', sub(p, mul(n, 0.02)), tip, 0.05, 0.0, steps=6, seg=10)
        for k in range(4): m.torus('accent', lerp(p, tip, 0.15 + k * 0.18), 0.045 * (1 - k * 0.2), 0.008, rt=(-20, 0, 0), seg=10, tseg=4)
    elif name == 'mane':
        for k in range(9):
            a = math.radians(-120 + k * 30)
            p, n = surf(hc, hr, -120 + k * 30, 20 + 30 * math.cos(a))
            m.sph('accent', sub(p, mul(n, 0.02)), 0.09, seg=10)
        for k in range(7):
            m.sph('accent', add(hc, (0.18 * math.sin(math.radians(-90 + k * 30)), -0.1 + 0.2 * math.cos(math.radians(-90 + k * 30)), -0.12)), 0.1, seg=10)
    elif name == 'rainbowmane':
        cols = ['accent', 'gold', 'glow', 'pink']
        for k in range(6):
            p, n = surf(hc, hr, 180, 70 - k * 22)
            m.sph(cols[k % 4], sub(p, mul(n, 0.01)), 0.07, seg=10)
    elif name == 'wings': feather_wings(m, (0.24, hc[1] - 0.18, -0.08), size=a[0] if a else 1.0, color='accent')
    elif name == 'batwings': bat_wings(m, (0.2, hc[1] - 0.2, -0.08), size=a[0] if a else 0.85)
    elif name == 'stars': stars_around(m, (0, hc[1] - 0.05, -0.05), n=4, R=0.5, s=0.035)
    elif name == 'star': star(m, add(hc, (0.12, hr[1] * 0.8, 0.1)), 0.05, color='glow')
    elif name == 'ring': m.torus('accent', (0, hc[1] - 0.15, 0), 0.48, 0.035, rt=(12, 0, 8), seg=30, tseg=6, anim='spin', pivot=(0, hc[1] - 0.15, 0))
    elif name == 'antenna':
        for side in (1, -1):
            root = add(hc, (side * 0.08, hr[1] * 0.9, 0))
            pts = bezier([root, add(root, (side * 0.02, 0.12, 0)), add(root, (side * 0.08, 0.2, 0.02))], 5)
            m.tube('main', pts, [0.018] * 6, seg=6, anim='earL' if side > 0 else 'earR', pivot=root)
            m.sph('glow', pts[-1], 0.045, seg=10, anim='earL' if side > 0 else 'earR', pivot=root)
    elif name == 'eye3':
        p, n = surf(hc, hr, 0, 34)
        c = sub(p, mul(n, 0.02))
        m.sph('white', c, 0.06, seg=10)
        m.ell('eye', add(c, mul(n, 0.03)), (0.038, 0.045, 0.03), rt=facing(n), anim='blink', pivot=c, seg=8)
    elif name == 'spikes': spikes(m, add(hc, (0, hr[1] * 0.7, -0.12)), (0, hc[1] - 0.12, -0.42), n=a[0] if a else 4)
    elif name == 'collar':
        m.torus('gold', add(hc, (0, -hr[1] * 0.8, -0.04)), 0.17, 0.035, rt=(-15, 0, 0), seg=22, tseg=6)
        m.sph('accent', add(hc, (0, -hr[1] * 0.95, 0.15)), 0.04, seg=8)
    elif name == 'headdress':
        p = add(hc, (0, 0.02, -0.06))
        for side in (1, -1):
            m.cone('gold', add(p, (side * 0.18, 0.05, 0)), add(p, (side * 0.27, -0.28, -0.05)), 0.07, 0.04, steps=3, seg=8, flat=0.4, flat_dir=(side, 0, 0))
        m.torus('gold', add(hc, (0, hr[1] * 0.55, 0)), 0.22, 0.02, rt=(-10, 0, 0), seg=22, tseg=5)
    elif name == 'trident':
        b = (0.32, 0.2, 0.2)
        m.tube('gold', [b, add(b, (0, 0.6, 0))], [0.018, 0.018], seg=6)
        for k in (-1, 0, 1):
            m.cone('gold', add(b, (k * 0.06, 0.58, 0)), add(b, (k * 0.07, 0.72 + (0.03 if k == 0 else 0), 0)), 0.02, 0.0, steps=2, seg=6)
        m.tube('gold', [add(b, (-0.07, 0.58, 0)), add(b, (0.07, 0.58, 0))], [0.016, 0.016], seg=6)
    elif name == 'flames':
        for k in range(5):
            p, n = surf(hc, hr, -60 + k * 30, 70)
            m.cone('glow', sub(p, mul(n, 0.03)), add(p, (0, 0.18 + 0.06 * (k % 2), -0.06)), 0.05, 0.0, steps=3, seg=7, bend=(0, 0, -0.04))
    elif name == 'eyepatch':
        p, n = surf(hc, hr, -24, 4)
        m.ell('dark', p, (0.075, 0.07, 0.02), rt=facing(n), seg=10)
        m.torus('dark', add(hc, (0, 0.06, 0)), hr[0] * 1.0, 0.012, rt=(-20, 0, 15), seg=24, tseg=4)
    elif name == 'lightning':
        pts = [(0.3, 0.9, -0.1), (0.36, 0.78, -0.1), (0.31, 0.76, -0.1), (0.38, 0.62, -0.1)]
        m.tube('glow', pts, [0.025] * 4, seg=6)
    elif name == 'stem':
        p = add(hc, (0, hr[1] * 0.95, 0))
        m.cone('accent', p, add(p, (0.04, 0.12, -0.02)), 0.04, 0.025, steps=3, seg=7, bend=(0.02, 0, 0))
        m.ell('accent', add(p, (0.1, 0.06, 0)), (0.07, 0.02, 0.04), rt=(0, 20, -25), seg=10)
    elif name == 'pumpkinface':
        for side in (1, -1):
            for k in range(3):
                p, n = surf(hc, hr, side * (15 + k * 35), 0)
                m.ell('shade', sub(p, mul(n, 0.012)), (0.02, hr[1] * 0.9, 0.02), rt=facing(n), seg=8)
    elif name == 'ghosttail':
        pts = bezier([(0, 0.15, -0.2), (0, 0.05, -0.38), (0.06, 0.18, -0.48)], 6)
        m.tube('main', pts, [0.18 * (1 - i / 7) for i in range(7)], seg=12, anim='tail', pivot=pts[0])
    elif name == 'wrap':
        for k in range(5):
            m.torus('accent', (0, 0.22 + k * 0.06, -0.02 + k * 0.02), 0.27 - abs(k - 2) * 0.02, 0.018, rt=(80 + k * 6, 0, (k - 2) * 8), seg=24, tseg=4)
        m.torus('accent', add(hc, (0, 0.04, 0)), hr[0] * 1.02, 0.02, rt=(-10, 0, -12), seg=24, tseg=4)
    elif name == 'cape':
        m.ell('accent', (0, 0.42, -0.2), (0.28, 0.2, 0.05), rt=(18, 0, 0), seg=16, anim='sway', pivot=(0, 0.6, -0.15))
        m.torus('accent', add(hc, (0, -hr[1] * 0.75, -0.02)), 0.17, 0.03, rt=(-10, 0, 0), seg=20, tseg=6)
    elif name == 'fangs':
        p, n = surf(hc, hr, 0, -22) if a == () else a[0]
        m.mirror(lambda sd: m.cone('white', add(p, (sd * 0.035, 0.01, -0.005)), add(p, (sd * 0.035, -0.045, 0.0)), 0.016, 0.0, steps=2, seg=6))
    elif name == 'hat':
        top = add(hc, (0, hr[1] * 0.8, 0))
        m.ell('accent', top, (0.3, 0.025, 0.3), seg=20)
        m.cone('accent', top, add(top, (0.06, 0.35, -0.08)), 0.15, 0.0, steps=6, seg=12, bend=(0.03, 0.02, -0.06))
        m.torus('gold', add(top, (0, 0.03, 0)), 0.15, 0.022, seg=20, tseg=5)
    elif name == 'bones':
        for k in range(3):
            m.torus('accent', (0, 0.36, 0.05 - 0.12 * k), 0.24 - 0.02 * k, 0.018, rt=(90, 0, 0), arc=200, seg=16, tseg=4)
        m.tube('accent', [(0, 0.48, 0.15), (0, 0.48, -0.25)], [0.025, 0.025], seg=6)
    elif name == 'web':
        for k in range(4):
            m.torus('white', add(hc, (0, 0.05, 0)), hr[0] * (0.4 + k * 0.17), 0.006, rt=(-70, 0, 0), arc=150, seg=14, tseg=3)
    elif name == 'legs8':
        for k in range(8):
            ang = math.radians(-70 + (k % 4) * 45) if k < 4 else math.radians(110 + (k % 4) * 45)
            root = (0.3 * math.sin(ang), 0.24, 0.25 * math.cos(ang))
            out = (math.sin(ang), 0, math.cos(ang))
            pts = bezier([root, add(root, add(mul(out, 0.18), (0, 0.15, 0))), add(root, add(mul(out, 0.32), (0, -0.22, 0)))], 6)
            m.tube('main', pts, [0.04 * (1 - 0.5 * i / 6) for i in range(7)], seg=7, anim='sway', pivot=root, phase=k)
            m.sph('accent', pts[3], 0.035, seg=6, anim='sway', pivot=root, phase=k)
    elif name == 'frosting':
        top = add(hc, (0, hr[1] * 0.7, 0))
        for k in range(3):
            m.torus('accent', add(top, (0, k * 0.06, 0)), 0.18 - k * 0.055, 0.05, seg=22, tseg=8)
        m.sph('accent', add(top, (0, 0.18, 0)), 0.05, seg=10)
        m.sph('pink', add(top, (0, 0.24, 0)), 0.045, seg=10)
        for k in range(8):
            a2 = 2 * math.pi * k / 8
            m.cone(['gold', 'glow', 'white', 'pink'][k % 4], add(top, (0.13 * math.cos(a2), 0.04, 0.13 * math.sin(a2))), add(top, (0.16 * math.cos(a2), 0.06, 0.16 * math.sin(a2))), 0.01, 0.01, steps=1, seg=4)
    elif name == 'stick':
        m.tube('white', [(0, 0.0, -0.36), (0, 0.32, -0.3)], [0.02, 0.02], seg=6)
        m.torus('accent', (0, 0.42, -0.3), 0.08, 0.035, rt=(90, 0, 0), seg=18, tseg=6)
        m.torus('gold', (0, 0.42, -0.3), 0.04, 0.03, rt=(90, 0, 0), seg=14, tseg=6)
    elif name == 'sprinkles':
        cols = ['gold', 'pink', 'glow', 'white']
        for k in range(10):
            az, el = (k * 137) % 360 - 180, 20 + (k * 53) % 60
            p, n = surf(hc, hr, az, el)
            if abs(az) < 40 and el < 40: continue
            m.cone(cols[k % 4], p, add(p, mul(norm(cross(n, (0.3, 1, 0.2))), 0.05)), 0.012, 0.012, steps=1, seg=4)
    elif name == 'donut':
        m.torus('accent', (0, hc[1] - 0.12, 0), 0.34, 0.08, rt=(0, 0, 0), seg=26, tseg=10)
        for k in range(12):
            a2 = 2 * math.pi * k / 12
            m.cone(['gold', 'glow', 'white'][k % 3], (0.34 * math.cos(a2), hc[1] - 0.04, 0.34 * math.sin(a2)), (0.38 * math.cos(a2), hc[1] - 0.03, 0.36 * math.sin(a2)), 0.012, 0.012, steps=1, seg=4)
    elif name == 'cottonpuffs':
        for (x, y, z, r) in [(0.2, 0.42, 0.1, 0.1), (-0.2, 0.42, 0.1, 0.1), (0.15, 0.4, -0.2, 0.11), (-0.15, 0.4, -0.2, 0.11), (0, 0.5, -0.1, 0.12)]:
            m.sph('accent', (x, y, z), r, seg=12)
    elif name == 'antlers':
        for side in (1, -1):
            b = add(hc, (side * 0.11, hr[1] * 0.85, -0.03))
            pts = bezier([b, add(b, (side * 0.05, 0.14, -0.02)), add(b, (side * 0.16, 0.24, -0.04))], 5)
            m.tube('accent', pts, [0.03 * (1 - 0.4 * i / 5) for i in range(6)], seg=7)
            for j in (2, 4):
                m.cone('accent', pts[j], add(pts[j], (side * 0.0, 0.1, 0.04)), 0.02, 0.0, steps=2, seg=6)
    elif name == 'caramel':
        for k in range(5):
            p, n = surf(hc, hr, -60 + k * 30, 55)
            m.cone('accent', sub(p, mul(n, 0.02)), add(p, (0, -0.07 - 0.03 * (k % 2), 0.01)), 0.035, 0.015, steps=2, seg=6)
    elif name == 'lamp':
        root = add(hc, (0, hr[1] * 0.9, 0.05))
        pts = bezier([root, add(root, (0, 0.2, 0.0)), add(root, (0, 0.22, 0.22))], 6)
        m.tube('main', pts, [0.018] * 7, seg=6, anim='sway', pivot=root)
        m.sph('glow', add(pts[-1], (0, -0.04, 0)), 0.065, seg=12, anim='sway', pivot=root)
    elif name == 'teeth':
        mp = add(hc, (0, -0.12, hr[2] * 0.95))
        for k in range(-2, 3):
            m.cone('white', add(mp, (k * 0.04, 0.02, 0)), add(mp, (k * 0.04, -0.03, 0.01)), 0.015, 0.0, steps=2, seg=5)
    elif name == 'tusk':
        p = add(hc, (0, 0.06, hr[2] * 0.95))
        pts = [add(p, (0.012 * math.sin(k * 1.3), 0.012 * math.cos(k * 1.3), 0.06 * k)) for k in range(9)]
        m.tube('white', pts, [0.035 * (1 - k / 9) for k in range(9)], seg=8)
    elif name == 'dorsal':
        b = add(hc, (0, hr[1] * 0.85, -0.25))
        m.cone('main', b, add(b, (0, 0.22, -0.12)), 0.1, 0.0, steps=4, seg=10, flat=0.3, flat_dir=(1, 0, 0), bend=(0, 0, -0.04))
    elif name == 'helmet':
        m.ell('gold', add(hc, (0, 0.05, -0.02)), (hr[0] * 1.06, hr[1] * 0.95, hr[2] * 1.06), cut=0.25, seg=20)
        m.cone('accent', add(hc, (0, hr[1] * 0.95, 0)), add(hc, (0, hr[1] + 0.16, -0.12)), 0.04, 0.0, steps=4, seg=7, flat=0.4, flat_dir=(1, 0, 0), bend=(0, 0.04, 0))
    elif name == 'pearl':
        m.sph('white', (0, 0.48, 0), 0.06, seg=12)
    elif name == 'shellspikes':
        for k in range(5): m.cone('main', (0, 0.42, -0.2 + 0.1 * k), (0, 0.5, -0.22 + 0.1 * k), 0.04, 0.0, steps=2, seg=6)
    elif name == 'cheeks':
        m.mirror(lambda sd: m.ell('blush', add(surf(hc, hr, sd * 42, -10)[0], (0, 0, 0)), (0.06, 0.04, 0.02), rt=facing(surf(hc, hr, sd * 42, -10)[1]), seg=10))
    elif name == 'tentmouth':
        p, n = surf(hc, hr, 0, -40)
        for k in range(4):
            m.cone('accent', sub(p, mul(n, 0.02)), add(p, ((k - 1.5) * 0.06, -0.14, 0.02)), 0.025, 0.0, steps=3, seg=6, anim='sway', pivot=p, phase=k)
    elif name == 'glowspots':
        for k in range(8):
            az, el = -150 + k * 40, 20 + (k * 37) % 50
            p, n = surf(hc, hr, az, el)
            if abs(az) < 45: continue
            m.ell('glow', p, (0.035, 0.035, 0.012), rt=facing(n), seg=8)


PACKS = {
    # Universe
    'nebula_cat': ('quad', dict(ears='cat', tail='fluffy'), ['stars']),
    'comet_pup': ('quad', dict(ears='floppy', tail='long', snout=True, mouth='smile'), ['star', 'stars']),
    'star_fox': ('quad', dict(ears='fox', tail='fluffy', snout=True), ['star']),
    'moon_bunny': ('quad', dict(ears='long', tail='short', tailtip='belly'), ['halo']),
    'sun_lion': ('quad', dict(ears='round', tail='long', snout=True), ['mane']),
    'blackhole_bat': ('flyer', dict(ears='bat'), ['batwings', 'ring']),
    'asteroid_mole': ('quad', dict(ears='round', snout=True, mouth='smile'), ['spikes', 'glowspots']),
    'galaxy_owl': ('bird', dict(tuft=True), ['wings', 'stars']),
    'alien_slime': ('blob', dict(mouth='open'), ['antenna', 'eye3']),
    'cosmic_dragon': ('quad', dict(tail='spade', snout=True, mouth='smile'), ['horns', 'batwings', 'spikes', 'stars']),
    # Gods
    'angel_lamb': ('quad', dict(ears='floppy', tail='short', tailtip='belly'), ['halo', 'wings', 'cottonpuffs']),
    'halo_chick': ('bird', dict(tuft=True), ['halo']),
    'zeus_eagle': ('bird', dict(tuft=False, spread=True), ['wings', 'crown', 'lightning']),
    'anubis_hound': ('quad', dict(ears='fox', tail='long', snout=True), ['collar', 'headdress']),
    'poseidon_whale': ('fish', dict(size=1.15), ['crown', 'trident']),
    'ra_phoenix': ('bird', dict(tuft=False, spread=True), ['wings', 'flames']),
    'thor_ram': ('quad', dict(ears='floppy', tail='short', snout=True, tailtip='belly'), ['curlhorns', 'helmet']),
    'odin_raven': ('bird', dict(tuft=True), ['wings', 'crown', 'eyepatch']),
    'athena_fawn': ('quad', dict(ears='round', tail='short', leg=0.28, snout=True, tailtip='belly'), ['helmet', 'halo']),
    'divine_dragon': ('quad', dict(tail='spade', snout=True, mouth='smile'), ['horns', 'wings', 'halo']),
    # Halloween
    'pumpkin_pup': ('quad', dict(ears='floppy', tail='short', snout=True, mouth='open'), ['stem', 'pumpkinface']),
    'ghost_kitty': ('blob', dict(ears='cat', mouth='cat'), ['ghosttail']),
    'mummy_mouse': ('quad', dict(ears='round', tail='long', snout=True), ['wrap']),
    'vampire_bat': ('flyer', dict(ears='bat', mouth='open'), ['batwings', 'cape']),
    'zombie_frog': ('blob', dict(mouth='open'), ['eye3', 'teeth']),
    'witch_cat': ('quad', dict(ears='cat', tail='long'), ['hat']),
    'skeleton_pup': ('quad', dict(ears='floppy', tail='short', snout=True, tailtip='main'), ['bones']),
    'candycorn_chick': ('bird', dict(tuft=False), ['stem', 'sprinkles']),
    'spider_plush': ('blob', dict(mouth='open'), ['legs8', 'eye3']),
    'reaper_wolf': ('quad', dict(ears='fox', tail='fluffy', snout=True), ['cape', 'horns']),
    # Candy Land
    'gummy_bun': ('blob', dict(ears='long', mouth='open'), ['cheeks', 'sprinkles']),
    'choco_cat': ('quad', dict(ears='cat', tail='fluffy'), ['caramel']),
    'lollipop_duck': ('bird', dict(tuft=True), ['stick']),
    'cupcake_pup': ('quad', dict(ears='floppy', tail='short', snout=True), ['frosting']),
    'jelly_slime': ('blob', dict(mouth='smile'), ['crown', 'sprinkles']),
    'donut_frog': ('blob', dict(mouth='smile'), ['donut', 'cheeks']),
    'cotton_fox': ('quad', dict(ears='fox', tail='fluffy', snout=True), ['cottonpuffs']),
    'mint_bunny': ('quad', dict(ears='long', tail='short', tailtip='belly'), ['sprinkles']),
    'caramel_deer': ('quad', dict(ears='round', tail='short', leg=0.28, snout=True, tailtip='belly'), ['antlers', 'caramel']),
    'rainbow_unicorn': ('quad', dict(ears='round', tail='long', leg=0.24, snout=True), ['unicorn', 'rainbowmane']),
    # Deep Ocean
    'coral_crab': ('crab', dict(), []),
    'glow_jelly': ('jelly', dict(tentacles=8), ['lamp']),
    'pearl_turtle': ('turtle', dict(), ['pearl']),
    'shark_pup': ('fish', dict(), ['dorsal', 'teeth']),
    'octo_cutie': ('octo', dict(), ['cheeks']),
    'seahorse_prince': ('seahorse', dict(), ['crown']),
    'starfish_pal': ('star', dict(), []),
    'angler_lamp': ('fish', dict(), ['lamp', 'teeth']),
    'narwhal_knight': ('fish', dict(size=1.1), ['tusk', 'crown', 'collar']),
    'kraken_baby': ('octo', dict(), ['horns', 'crown']),
}


def build(name):
    m = Model()
    if name in BASE:
        BASE[name](m)
    else:
        t, opts, feats = PACKS[name]
        hc, hr = TEMPLATES[t](m, opts)
        for f in feats:
            F(m, f, hc, hr)
    return m


def creature_colors():
    src = open('src/main/java/com/eggsteal/Creature.java').read()
    out = {}
    for mt in re.finditer(r'add\(l, "(\w+)", "[^"]+", \d+, -?\d+, \d+, \d+, 0x([0-9a-f]{6}), 0x([0-9a-f]{6})\)', src):
        out[mt.group(1)] = (int(mt.group(2), 16), int(mt.group(3), 16))
    return out


if __name__ == '__main__':
    os.makedirs(R, exist_ok=True)
    names = list(BASE) + list(PACKS)
    total = 0
    for nme in names:
        m = build(nme)
        m.write(R + nme + '.obj')
        total += m.stats()
        print(f'{nme:18s} {m.stats():5d} polys')
    print('models:', len(names), 'average polys:', total // len(names))
