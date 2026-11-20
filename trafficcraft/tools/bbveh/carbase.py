"""Generic Minecraft-style car builder used by every car in tools/bbveh/cars/.

A car file only describes what makes that car unique (proportions, glass, doors, its own head / tail lamp designs,
grille art, interior layout, physics). This module turns the description into a Blockbench project (.bbmodel):
faceted lofted body + greenhouse, doors as separate parts, layered lamp inserts that follow the body surface,
pixel textures, a full interior (seats, dash, gauges, steering wheel 'steer', gear lever 'shifter', pedals, mirrors)
and the customisation variants (fb rb ex sp hd wh sk).

Authoring frame: +X = car's left, +Y up, +Z forward, z from the rear bumper (0) to the nose (L).
"""
import math, os, sys, json
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bb import *
import styles as S
import parts as PT

GAP = (150, 150, 150, 255)          # panel gaps: darker shade of the paint
BLACK = (24, 24, 26, NOTINT)
CHROME_T = (210, 214, 220, NOTINT)
FB_NAMES = ['Factory', 'Factory + lip', 'Street mesh', 'Time-attack', 'Drift aero']
RB_NAMES = ['Factory', 'Diffuser', 'Vented valance', 'Track diffuser', 'Cut-out valance']
HD_NAMES = ['Factory', 'Carbon', 'Vented', 'Ram-air scoop', 'Carbon vented']
SK_NAMES = ['Factory', 'Extended skirts', 'Skirts + winglets']
FB_CHIN = [0.0, 0.0, 0.0, 0.03, 0.015]
LAMP_MAT = {'head': 'head', 'high': 'high', 'drl': 'drl', 'fog': 'head', 'tail': 'tail', 'brake': 'brake', 'reverse': 'reverse',
            'marker': 'marker'}
LAMP_STYLE = {'head': 'proj_head', 'high': 'proj_high', 'drl': 'led_white', 'fog': 'proj_head', 'ind': 'proj_amber', 'tail': 'tail_ring',
              'brake': 'brake_core', 'reverse': 'rev_core', 'marker': 'amber_dark', 'sealed': 'sealed'}


def mat_of(kind, side):
    if kind == 'ind':
        return 'ind_' + side
    if kind == 'sealed':
        return 'head'
    return LAMP_MAT.get(kind, 'solid')


def rx(a):
    a = math.radians(a)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def ry(a):
    a = math.radians(a)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])


def rz(a):
    a = math.radians(a)
    return np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])


def mirror2(outline, sx):
    o = [(sx * u, v) for u, v in outline]
    return o if sx > 0 else o[::-1]


class Car:
    def __init__(self, spec):
        d = dict(TW=0.235, Rr=0.216, density=40, windows=[], bpillars=[], doors=[], open_top=False, rocker_black=True, markers=[],
                 head=[], popups=[], front_lamps=[], tail={}, mouth=[], plate_f=0.34, plate_r=0.52, exhaust=(-0.5, 0.3, -0.035),
                 spoiler=(0.04, 0.7, 'none'), stock_wheel='spoke5', wheel_color=None, caliper=(140, 142, 148), fuel=('r', 0.8, 0.84),
                 hood=None, trunk_y=None, hatch_x=None, rear_glass=None, ws=None, a_black=True, chrome_bumpers=None, cabin={}, beam=None,
                 fb_top=6, rb_top=3, front_art=None, rear_art=None, side_art=None, extra=None, colors=['#b0101c'], badge=None,
                 roof_black=False, belt_chrome=False, window_chrome=False, top_art=None, no_exhaust=False)
        d.update(spec)
        self.__dict__.update(d)
        for k in ('bot', 'belt', 'hw', 'zsh', 'roofz', 'roofw', 'crown'):
            setattr(self, k, Curve(d[k]))
        self.m = Model(self.id, 1024, self.density)
        self.m.shift = V(0, 0, -self.L / 2)
        self.m.painters.update(S.COMMON)
        self.ROUTE = {}
        self.RA = self.R + d.get('arch_gap', 0.045)
        self.WS0, self.WS1 = self.ws
        self.meta_extra = {}
        if self.beam is None:
            self.beam = (0.6, 0.66, self.L - 0.2)

    # ===================================================================================== geometry helpers
    def el(self, group, mat, style):
        key = (group, mat, style)
        if key not in self.ROUTE:
            self.ROUTE[key] = self.m.element('%s_%s' % (group, style.replace('.', '_')), mat, group, style)
        self.m._cur = self.ROUTE[key]
        return self.ROUTE[key]

    def arch_h(self, z):
        for za in (self.YR, self.YF):
            d = abs(z - za)
            if d <= self.RA + 1e-6:
                return self.R + math.sqrt(max(0.0, self.RA ** 2 - d * d))
        return None

    def gh(self, z):
        if z <= self.CABIN0 or z >= self.CABIN1:
            return 0.0
        return max(0.0, self.roofz(z) - self.belt(z))

    def section(self, z, chin=0.0):
        w, t, s = self.hw(z), self.belt(z), self.zsh(z)
        b = self.bot(z) - chin * smooth(self.FB_Z, max(self.FB_Z + 0.05, self.L - 0.13), z)
        a = self.arch_h(z)
        a = a if a is not None else b
        cr = self.crown(z)
        ts = self.fender_t(z)
        s2 = max(s, a + 0.03)
        s2 = min(s2, ts - 0.05)
        inner = min(0.6, w * 0.7)
        lift = ts - t                       # the bonnet follows the raised fenders, keeping only a gentle valley
        return [(0.0, b + 0.02), (inner, b), (inner, a), (w * 0.965, max(a, b + 0.035)), (w, s2), (w - 0.02, (s2 + ts) / 2),
                (w - 0.058, ts - 0.022), (w - 0.1, ts), (w * 0.5, t + cr * 0.72 + lift * 0.8), (0.0, t + cr + lift * 0.72)]

    def fender_t(self, z):
        """Top of the body side; raised over the wheels where the nose / tail is lower than the tyre top."""
        t = self.belt(z)
        ts = t
        for za in (self.YR, self.YF):
            d = abs(z - za)
            top = self.R + self.RA + 0.05
            if d < self.RA + 0.35 and top > t:
                ts = max(ts, lerp(t, top, smooth(self.RA + 0.35, self.RA - 0.1, d)))
        return ts

    def deck(self, x, z):
        w, t = self.hw(z), self.belt(z)
        lift = self.fender_t(z) - t
        xx = min(abs(x), w - 0.1)
        f = (xx / max(w - 0.1, 1e-3)) ** 2
        return t + self.crown(z) * (1 - f) + lift * (0.72 + 0.28 * f)

    def gpts(self, z):
        w, t = self.hw(z), self.belt(z)
        g = self.gh(z)
        k = smooth(0.0, 0.08, g)
        r = self.roofw(min(max(z, self.CABIN0), self.CABIN1))
        rz = t + g
        hood = [(w * 0.74, self.deck(w * 0.74, z)), (w * 0.52, self.deck(w * 0.52, z)), (0.0, self.deck(0.0, z))]
        cab = [(r + self.tumble, rz - self.glass_drop), (r, rz - 0.022), (0.0, rz)]
        out = [(w - 0.1, t)]
        for (xh, zh), (xc, zc) in zip(hood, cab):
            out.append((lerp(xh, xc, k), lerp(zh, max(zc, zh), k)))
        return out

    tumble = 0.05
    glass_drop = 0.075

    def stations(self):
        zs = [0.0, 0.05, 0.18, self.RB_Z, self.CABIN0, self.CABIN1, self.FB_Z, self.WS0, self.WS1, self.L - 0.31, self.L - 0.16, self.L - 0.07,
              self.L]
        for a, b in self.windows + self.bpillars:
            zs += [a, b]
        for _, a, b in self.doors:
            zs += [a, b]
        if self.rear_glass:
            zs += list(self.rear_glass)
        zs += list(np.arange(0.2, self.L, 0.25))
        for za in (self.YR, self.YF):
            for d in (-self.RA - 0.004, -self.RA, -0.8 * self.RA, -0.45 * self.RA, 0.0, 0.45 * self.RA, 0.8 * self.RA, self.RA, self.RA + 0.004):
                zs.append(za + d)
        zs = sorted(set(round(z, 4) for z in zs if 0 <= z <= self.L))
        out = []
        for z in zs:
            if not out or z - out[-1] > 0.003:
                out.append(z)
        # drop generic stations that are too close to a feature station
        return out

    def loft(self, zs, sec_fn, route_fn, down_bands=()):
        rows = [sec_fn(z) for z in zs]
        for i in range(len(zs) - 1):
            z0, z1 = zs[i], zs[i + 1]
            zm = (z0 + z1) / 2
            A, B = rows[i], rows[i + 1]
            for j in range(len(A) - 1):
                for sx in (1, -1):
                    r = route_fn(j, zm, sx)
                    if r is None:
                        continue
                    q = [V(sx * A[j][0], A[j][1], z0), V(sx * B[j][0], B[j][1], z1), V(sx * B[j + 1][0], B[j + 1][1], z1), V(sx * A[j + 1][0], A[j + 1][1], z0)]
                    c = np.mean(q, axis=0)
                    ow = V(0, -1, 0) if j in down_bands else V(c[0], c[1] - 0.6, 0)
                    self.m.face(q, r[2], el=self.el(*r), outward=ow)

    def door_at(self, zm):
        for name, a, b in self.doors:
            if a < zm < b:
                return name
        return None

    def lower_route(self, fb='fb_0', rb='rb_0', fbs='paint.fb0', rbs='paint.rb0'):
        def f(j, zm, sx):
            side = 'l' if sx > 0 else 'r'
            under = j <= 2
            if zm > self.FB_Z and j <= self.fb_top:
                return (fb, 'solid', 'under') if under else (fb, 'paint', fbs)
            if zm < self.RB_Z and j <= self.rb_top:
                return (rb, 'solid', 'under') if under else (rb, 'paint', rbs)
            if zm > self.FB_Z or zm < self.RB_Z:
                return ('body', 'paint', 'paint')
            if under:
                return ('body', 'solid', 'under')
            if j >= 7 and self.CABIN0 + 0.01 < zm < self.CABIN1 - 0.01:
                return None
            dn = self.door_at(zm)
            if dn and 3 <= j <= 6:
                return ('door_%s%s' % (dn, side), 'paint', 'paint')
            return ('body', 'paint', 'paint')
        return f

    @staticmethod
    def only(groups, f):
        def g(j, zm, sx):
            r = f(j, zm, sx)
            return r if r is not None and r[0] in groups else None
        return g

    def green_route(self, j, zm, sx):
        side = 'l' if sx > 0 else 'r'
        rg = self.rear_glass or (0, 0)
        if j == 0:
            for a, b in self.bpillars:
                if a < zm < b:
                    return ('body', 'solid', 'trim_gloss')
            for a, b in self.windows:
                if a < zm < b:
                    dn = self.door_at(zm)
                    return ('door_%s%s' % (dn, side) if dn else 'body', 'glass', 'glass')
            return ('body', 'paint', 'paint')
        if j == 1:
            if self.a_black and zm > self.WS0 - 0.02:
                return ('body', 'solid', 'trim_gloss')
            return ('body', 'solid', 'trim_gloss') if self.roof_black else ('body', 'paint', 'paint')
        if self.WS0 < zm < self.WS1 or rg[0] < zm < rg[1]:
            return ('body', 'glass', 'glass')
        return ('body', 'solid', 'trim_gloss') if self.roof_black else ('body', 'paint', 'paint')

    def cap(self, z, pts, gm, outward, lo=0, hi=None):
        hi = len(pts) - 1 if hi is None else hi
        half = pts[lo:hi + 1]
        poly = [V(x, y, z) for x, y in half] + [V(-x, y, z) for x, y in reversed(half)]
        self.m.face(poly, gm[2], el=self.el(*gm), outward=outward)

    def body_tris(self, groups=('body', 'fb_0', 'rb_0', 'door_fl', 'door_fr', 'door_rl', 'door_rr')):
        T = []
        for e in self.m.elements:
            if e.group in groups and e.mat in ('paint', 'solid', 'glass') and e.style not in ('headliner', 'dash2'):
                for f in e.faces:
                    for i in range(1, len(f.verts) - 1):
                        T.append([f.verts[0], f.verts[i], f.verts[i + 1]])
        return np.array(T)

    def hits(self, points2d, view, tris, normals=False):
        pts = np.array(points2d, float)
        Lz = self.L
        if isinstance(view, tuple):
            # oblique projection: 2D points in the z = L plane, rays along view[1] (wraps lamps round the nose corner)
            d = unit(V(view[1]))
            O = np.c_[pts[:, 0], pts[:, 1], np.full(len(pts), Lz)] - d * 2.5
        elif view == 'T':
            O, d = np.c_[pts[:, 0], np.full(len(pts), 3.0), pts[:, 1]], V(0, -1, 0)
        elif view == 'F':
            O, d = np.c_[pts[:, 0], pts[:, 1], np.full(len(pts), Lz + 2)], V(0, 0, -1)
        elif view == 'B':
            O, d = np.c_[pts[:, 0], pts[:, 1], np.full(len(pts), -2.0)], V(0, 0, 1)
        elif view == 'L':
            O, d = np.c_[np.full(len(pts), 3.0), pts[:, 1], pts[:, 0]], V(-1, 0, 0)
        else:
            O, d = np.c_[np.full(len(pts), -3.0), pts[:, 1], pts[:, 0]], V(1, 0, 0)
        t, idx = ray_tris(O, d, tris)
        P = O + t[:, None] * d
        if not normals:
            return P
        Tt = tris[idx]
        N = np.cross(Tt[:, 1] - Tt[:, 0], Tt[:, 2] - Tt[:, 0])
        N /= np.maximum(np.linalg.norm(N, axis=1), 1e-12)[:, None]
        N *= np.where(N @ d > 0, -1.0, 1.0)[:, None]
        return P, N

    def conform(self, outline, view, tris, layers, wall_el, wall_style, rings=(1.0, 0.8, 0.6, 0.4, 0.2), dens=160, depth=0.012):
        """Lamp/trim insert that follows the body surface; layers = [(offset, element, style)]."""
        m = self.m
        ol = np.array(outline, float)
        c = ol.mean(axis=0)
        pts2 = [c + (ol - c) * s for s in rings] + [c[None]]
        P, N = self.hits(np.vstack(pts2), view, tris, True)
        bad = ~np.all(np.isfinite(P), axis=1)
        if bad.all():
            return None
        if bad.any():
            P[bad] = P[~bad][0]
            N[bad] = N[~bad][0]
        idx = np.cumsum([0] + [len(x) for x in pts2])
        n_out = len(ol)
        top = None
        for off, e, style in layers:
            Q = P + N * off
            R_ = [Q[idx[i]:idx[i + 1]] for i in range(len(pts2))]
            nav = N.mean(axis=0)
            for a, b in zip(R_[:-2], R_[1:-1]):
                for k in range(n_out):
                    k1 = (k + 1) % n_out
                    m.face([a[k], a[k1], b[k1]], style, el=e, outward=nav, dens=dens)
                    m.face([a[k], b[k1], b[k]], style, el=e, outward=nav, dens=dens)
            last = R_[-2]
            for k in range(n_out):
                m.face([last[k], last[(k + 1) % n_out], R_[-1][0]], style, el=e, outward=nav, dens=dens)
            top = R_[0]
        if wall_el is not None:
            base = P[:n_out] - N[:n_out] * depth
            cen = top.mean(axis=0)
            for k in range(n_out):
                k1 = (k + 1) % n_out
                q = [top[k], top[k1], base[k1], base[k]]
                m.face(q, wall_style, el=wall_el, outward=np.mean(q, axis=0) - cen, dens=64)

        def proj(p2, off):
            p, n = self.hits([p2], view, tris, True)
            return p[0] + n[0] * off, n[0]
        return proj

    # ===================================================================================== body
    def build_body(self):
        self.ZS = self.stations()
        ZS = self.ZS
        self.loft(ZS, self.section, self.only(['body', 'door_fl', 'door_fr', 'door_rl', 'door_rr'], self.lower_route()), down_bands=(0, 2))
        if self.open_top:
            self.ZG = [z for z in ZS if self.WS0 <= z <= self.CABIN1]
            route = lambda j, zm, sx: (('body', 'glass', 'glass') if j == 2 else ('body', 'solid', 'trim_gloss') if j == 1 else ('body', 'paint', 'paint'))
            self.loft(self.ZG, self.gpts_open, route)
        else:
            self.ZG = [z for z in ZS if self.CABIN0 <= z <= self.CABIN1]
            self.loft(self.ZG, self.gpts, self.green_route)
        self.cap(0.0, self.section(0.0), ('body', 'paint', 'paint.rear'), V(0, 0, -1), lo=self.rb_top + 1)
        self.lining()
        self.front_bumpers()
        self.rear_bumpers()
        self.TRIS = self.body_tris()

    def gpts_open(self, z):
        """Open roadster: only the windscreen frame stands up."""
        w, t = self.hw(z), self.belt(z)
        h = self.roofz(min(max(z, self.CABIN0), self.CABIN1)) - t
        k = smooth(self.WS0, self.WS0 + 0.05, z) * 0 + 1
        r = self.roofw(z)
        return [(w - 0.1, t), (r + 0.05, t + h * 0.15 + 0.02), (r + 0.02, t + h * 0.15 + 0.04), (0.0, t + h * 0.15 + 0.05)] if z < self.WS0 + 0.01 else \
            [(w - 0.1, t), (r + 0.05, t + h - 0.04), (r + 0.02, t + h - 0.02), (0.0, t + h)]

    def lining(self):
        m = self.m
        if not self.open_top:
            rg = self.rear_glass or (0, 0)
            for z0, z1 in zip(self.ZG[:-1], self.ZG[1:]):
                zm = (z0 + z1) / 2
                A, B = self.gpts(z0), self.gpts(z1)
                for j in (1, 2):
                    if j == 2 and (self.WS0 < zm < self.WS1 or rg[0] < zm < rg[1]):
                        continue
                    for sx in (1, -1):
                        q = [V(sx * A[j][0], A[j][1] - 0.012, z0), V(sx * B[j][0], B[j][1] - 0.012, z1), V(sx * B[j + 1][0], B[j + 1][1] - 0.012, z1),
                             V(sx * A[j + 1][0], A[j + 1][1] - 0.012, z0)]
                        m.face(q, 'headliner', el=self.el('body', 'solid', 'headliner'), outward=V(0, -1, 0))
        # side trims inside the cabin where there is no door
        d0 = min(a for _, a, _ in self.doors)
        for sx in (1, -1):
            x = sx * (self.hw(d0) - 0.09)
            z0 = self.CABIN0 + 0.1
            if d0 - z0 > 0.1:
                m.face([V(x, 0.28, z0), V(x, 0.28, d0), V(x, self.belt(d0) - 0.02, d0), V(x, self.belt(z0) - 0.02, z0)], 'dash2', el=self.el('body', 'solid', 'dash2'),
                       outward=V(-sx, 0, 0))

    # ===================================================================================== bumpers (variants)
    def front_bumpers(self):
        m = self.m
        for k in range(5):
            g = 'fb_%d' % k
            st = 'paint.fb%d' % k
            zs = [z for z in self.ZS if z >= self.FB_Z - 1e-6]
            self.loft(zs, lambda z, k=k: self.section(z, FB_CHIN[k]), self.only([g], self.lower_route(fb=g, fbs=st)), down_bands=(0, 2))
            self.cap(self.L, self.section(self.L, FB_CHIN[k]), (g, 'paint', st), V(0, 0, 1), hi=self.fb_top + 1)
            b0 = self.bot(self.L - 0.2) - FB_CHIN[k]
            if self.chrome_bumpers and k == 0:
                self.chrome_bar(g, zs, self.chrome_bumpers[0], front=True)
            if k in (1, 3, 4):
                ext = 0.085 if k == 3 else 0.03
                e = self.el(g, 'solid', 'carbon' if k == 3 else 'trim')
                outer = [(self.hw(z) * 0.965 + ext * smooth(self.FB_Z, max(self.FB_Z + 0.05, self.L - 0.2), z), z) for z in zs] + [(0.0, self.L + ext)]
                inner = [(self.hw(z) * 0.965 - 0.08, z) for z in zs] + [(0.0, self.L - 0.08)]
                y0, y1 = b0 - 0.018, b0 - 0.004
                for sx in (1, -1):
                    for i in range(len(outer) - 1):
                        a, b, c_, d = outer[i], outer[i + 1], inner[i + 1], inner[i]
                        for yy, ow in ((y1, V(0, 1, 0)), (y0, V(0, -1, 0))):
                            m.face([V(sx * a[0], yy, a[1]), V(sx * b[0], yy, b[1]), V(sx * c_[0], yy, c_[1]), V(sx * d[0], yy, d[1])], e.style, el=e, outward=ow)
                        q = [V(sx * a[0], y0, a[1]), V(sx * b[0], y0, b[1]), V(sx * b[0], y1, b[1]), V(sx * a[0], y1, a[1])]
                        m.face(q, e.style, el=e, outward=V(sx * a[0], 0, a[1] - self.L + 0.7))
            if k in (2, 3):
                self.el(g, 'solid', 'carbon')
                for sx in (1, -1):
                    for i in range(1 if k == 2 else 2):
                        z = self.L - 0.22 - i * 0.1
                        x = self.hw(z) * 0.97
                        y = b0 + 0.14 + i * 0.09
                        m.box(V(sx * (x + 0.035), y, z), (0.08, 0.008, 0.13), 'carbon', M=rx(14))
            if k == 4:
                self.el(g, 'solid', 'towhook')
                m.box(V(-0.36, b0 + 0.06, self.L + 0.02), (0.04, 0.08, 0.02), 'towhook')

    def rear_bumpers(self):
        m = self.m
        for k in range(5):
            g = 'rb_%d' % k
            st = 'paint.rb%d' % k
            zs = [z for z in self.ZS if z <= self.RB_Z + 1e-6]
            self.loft(zs, self.section, self.only([g], self.lower_route(rb=g, rbs=st)), down_bands=(0, 2))
            self.cap(0.0, self.section(0.0), (g, 'paint', st), V(0, 0, -1), lo=0, hi=self.rb_top + 1)
            if self.chrome_bumpers and k == 0:
                self.chrome_bar(g, zs, self.chrome_bumpers[1], front=False)
            y0 = self.bot(0.12)
            if k in (1, 2, 3):
                e = self.el(g, 'solid', 'carbon' if k == 3 else 'trim')
                depth = 0.3 if k == 3 else 0.2
                hgt = 0.1 if k == 3 else 0.06
                wdt = min(0.62 if k == 3 else 0.5, self.hw(0.1) - 0.15)
                m.face([V(wdt, y0 - 0.004, depth), V(-wdt, y0 - 0.004, depth), V(-wdt, y0 - 0.004, -0.02), V(wdt, y0 - 0.004, -0.02)], e.style, el=e,
                       outward=V(0, -1, 0))
                nf = 7 if k == 3 else 5
                for i in range(nf):
                    x = -wdt * 0.85 + i * (2 * wdt * 0.85) / (nf - 1)
                    m.prism([(y0 - 0.004, depth), (y0 - 0.004, -0.02), (y0 - hgt, -0.02), (y0 - 0.004, depth * 0.6)][::-1], x - 0.006, x + 0.006, 'x', e.style)
            if k == 3:
                self.el(g, 'solid', 'towhook')
                m.box(V(0.42, y0 + 0.04, -0.02), (0.035, 0.07, 0.02), 'towhook')

    def chrome_bar(self, g, zs, yr, front):
        """Classic chrome bumper bar wrapping the end of the car (y range yr=(y0, y1))."""
        m = self.m
        e = self.el(g, 'solid', 'chrome')
        y0, y1 = yr
        zz = zs if front else zs
        pts = []
        for z in zz:
            pts.append((self.hw(z) * 0.985 + 0.03, z))
        pts = sorted(pts, key=lambda p: p[1])
        endz = self.L + 0.04 if front else -0.04
        if front:
            outline = pts + [(0.0, endz)]
        else:
            outline = [(0.0, endz)] + pts
        for sx in (1, -1):
            for a, b in zip(outline[:-1], outline[1:]):
                q = [V(sx * a[0], y0, a[1]), V(sx * b[0], y0, b[1]), V(sx * b[0], y1, b[1]), V(sx * a[0], y1, a[1])]
                m.face(q, 'chrome', el=e, outward=V(sx * (a[0] + b[0]) / 2, 0, (a[1] + b[1]) / 2 - self.L / 2))
                for yy, ow in ((y1, V(0, 1, 0)), (y0, V(0, -1, 0))):
                    ia = (a[0] - 0.05, a[1] - (0.05 if front else -0.05))
                    ib = (b[0] - 0.05, b[1] - (0.05 if front else -0.05))
                    m.face([V(sx * a[0], yy, a[1]), V(sx * b[0], yy, b[1]), V(sx * ib[0], yy, ib[1]), V(sx * ia[0], yy, ia[1])], 'chrome', el=e, outward=ow)

    # ===================================================================================== lamps
    def headlamps(self):
        m = self.m
        for hd in self.head:
            for sx in (1, -1):
                side = 'l' if sx > 0 else 'r'
                view = hd.get('view', 'T')
                if 'dir' in hd:
                    dx, dy, dz = hd['dir']
                    view = ('D', (sx * dx, dy, dz))
                out = chaikin(mirror2(hd['outline'], sx), hd.get('smooth', 1)) if hd.get('smooth', 1) else mirror2(hd['outline'], sx)
                hs = hd.get('house', 'head_house')
                proj = self.conform(out, view, self.TRIS, [(hd.get('house_off', 0.005), self.el('body', 'solid', hs), hs),
                                                          (hd.get('lens_off', 0.016), self.el('body', 'glass', 'lens'), 'lens')],
                                    self.el('body', 'solid', hd.get('bezel', 'head_bezel')), hd.get('bezel', 'head_bezel'))
                if proj is None:
                    continue
                for (u, v, r, kind) in hd.get('lamps', []):
                    c, n = proj((sx * u, v), hd.get('house_off', 0.005))
                    m.element('lamp_bowl', 'solid', 'body', 'chrome')
                    m.cylinder(c, n, r, 0.006, 12, 'chrome', r1=r * 0.92, caps=(False, False))
                    m.element('lamp_' + kind, mat_of(kind, side), 'body', LAMP_STYLE[kind])
                    m.cylinder(c + n * 0.006, n, r * 0.92, 0.002, 12, LAMP_STYLE[kind], r1=r * 0.6, caps=(False, True))
                for (u0, v0, u1, v1, kind) in hd.get('rects', []):
                    rect = [(sx * u0, v0), (sx * u1, v0), (sx * u1, v1), (sx * u0, v1)]
                    rect = rect if sx > 0 else rect[::-1]
                    self.conform(rect, view, self.TRIS, [(hd.get('house_off', 0.005) + 0.004, self.el('body', mat_of(kind, side), LAMP_STYLE.get(kind, 'proj_head') + '_r'),
                                                          LAMP_STYLE.get(kind, 'proj_head') + '_r')], None, None, rings=(1.0, 0.5), dens=128)
                for (poly, width, kind) in hd.get('strips', []):
                    self.strip([(sx * u, v) for u, v in poly], width, view, hd.get('house_off', 0.005) + 0.004,
                               self.el('body', mat_of(kind, side), LAMP_STYLE[kind]), LAMP_STYLE[kind])

    def strip(self, poly, width, view, off, e, style):
        """LED light strip along a 2D polyline projected on the body."""
        P = [np.array(p, float) for p in poly]
        A, B = [], []
        for i, p in enumerate(P):
            d = P[min(i + 1, len(P) - 1)] - P[max(i - 1, 0)]
            d /= max(np.linalg.norm(d), 1e-9)
            nn = np.array([-d[1], d[0]])
            A.append(p + nn * width / 2)
            B.append(p - nn * width / 2)
        HA, NA = self.hits(A, view, self.TRIS, True)
        HB, NB = self.hits(B, view, self.TRIS, True)
        for i in range(len(P) - 1):
            q = [HA[i] + NA[i] * off, HA[i + 1] + NA[i + 1] * off, HB[i + 1] + NB[i + 1] * off, HB[i] + NB[i] * off]
            if all(np.all(np.isfinite(x)) for x in q):
                self.m.face(q, style, el=e, outward=NA[i], dens=128)

    def popup_lamps(self):
        """Pop-up headlamps: closed pod flush with the bonnet (part popup_l / popup_r) that rotates up when the lights are on."""
        m = self.m
        pops = {}
        for p in self.popups:
            for sx in (1, -1):
                side = 'l' if sx > 0 else 'r'
                g = 'popup_' + side
                x0, x1 = sorted((sx * p['x0'], sx * p['x1']))
                z0, z1 = p['z0'], p['z1']
                yt = (self.deck(x0, z1) + self.deck(x1, z1) + self.deck(x0, z0) + self.deck(x1, z0)) / 4 + 0.003
                h = p.get('h', 0.12)
                self.el(g, 'paint', 'paint')
                m.box(V((x0 + x1) / 2, yt - h / 2, (z0 + z1) / 2), (x1 - x0, h, z1 - z0), 'paint')
                # lamp on the pod's underside: faces forward once the pod is raised
                self.el(g, 'solid', 'trim')
                m.face([V(x0 + 0.01, yt - h - 0.002, z0 + 0.01), V(x1 - 0.01, yt - h - 0.002, z0 + 0.01), V(x1 - 0.01, yt - h - 0.002, z1 - 0.01),
                        V(x0 + 0.01, yt - h - 0.002, z1 - 0.01)], 'trim', outward=V(0, -1, 0))
                c = V((x0 + x1) / 2, yt - h - 0.004, (z0 + z1) / 2)
                r = min(x1 - x0, z1 - z0) * 0.4
                m.element('popup_lamp', 'head', g, p.get('style', 'sealed'))
                m.cylinder(c, V(0, -1, 0), r, 0.006, 12, p.get('style', 'sealed'))
                if p.get('front_lamp'):
                    # twin lamps on the front face of the pod (visible all the time, lit with the low / high beams)
                    zf = z1 + 0.003
                    self.el(g, 'solid', 'head_house_black')
                    m.face([V(x0 + 0.01, yt - h + 0.012, zf), V(x1 - 0.01, yt - h + 0.012, zf), V(x1 - 0.01, yt - 0.012, zf), V(x0 + 0.01, yt - 0.012, zf)],
                           'head_house_black', outward=V(0, 0, 1), dens=200)
                    rr_ = min(h * 0.36, (x1 - x0) * 0.2)
                    for i, kind in enumerate(('head', 'high')):
                        cx = x0 + (x1 - x0) * (0.3 + 0.4 * i)
                        cc = V(cx, yt - h / 2, zf)
                        m.element('pod_bowl', 'solid', g, 'chrome')
                        m.cylinder(cc, V(0, 0, 1), rr_, 0.006, 12, 'chrome', r1=rr_ * 0.92, caps=(False, False))
                        m.element('pod_lamp_' + kind, kind, g, 'proj_' + kind)
                        m.cylinder(cc + V(0, 0, 0.006), V(0, 0, 1), rr_ * 0.92, 0.002, 12, 'proj_' + kind, r1=rr_ * 0.6, caps=(False, True))
                    self.el(g, 'glass', 'lens')
                    m.face([V(x0 + 0.008, yt - h + 0.008, zf + 0.012), V(x1 - 0.008, yt - h + 0.008, zf + 0.012), V(x1 - 0.008, yt - 0.008, zf + 0.012),
                            V(x0 + 0.008, yt - 0.008, zf + 0.012)], 'lens', outward=V(0, 0, 1), dens=120)
                pops[g] = dict(pivot=[round(yt - h * 0.5, 4), round(z0 - self.L / 2, 4)], angle=p.get('angle', -62))
        self.meta_extra['popups'] = pops

    def front_bumper_lamps(self):
        for k in range(5):
            g = 'fb_%d' % k
            tris = self.body_tris(('body', g))
            for fl in self.front_lamps:
                for sx in (1, -1):
                    side = 'l' if sx > 0 else 'r'
                    dy = -FB_CHIN[k] * 0.3
                    out = mirror2([(u, v + dy) for u, v in fl['outline']], sx)
                    kind = fl.get('kind', 'fog')
                    layers = []
                    if fl.get('house', True):
                        layers.append((0.002, self.el(g, 'solid', 'lamp_house'), 'lamp_house'))
                    layers.append((fl.get('lift', 0.01), self.el(g, mat_of(kind, side), fl.get('style', 'fog_lens')), fl.get('style', 'fog_lens')))
                    self.conform(out, fl.get('view', 'F'), tris, layers, self.el(g, 'solid', 'trim'), 'trim', rings=(1.0, 0.6), dens=128)

    def taillamps(self):
        m = self.m
        T = self.tail
        k = getattr(self, 'tail_scale', 1.0)
        if k != 1.0 and not getattr(self, '_tail_scaled', False):
            # make every rear lamp element bigger about its own centre (keeps the layout, real-size units)
            self._tail_scaled = True
            def sc(ol, kk=k):
                c = np.mean(np.array(ol), axis=0)
                return [tuple(c + (np.array(p) - c) * kk) for p in ol]
            T = dict(T)
            T['housings'] = [dict(h, outline=sc(h['outline'])) for h in T.get('housings', [])]
            T['rects'] = [(sc(o), kd, st, off) for (o, kd, st, off) in T.get('rects', [])]
            T['rounds'] = [(x, y, r * k, ring, core, cr) for (x, y, r, ring, core, cr) in T.get('rounds', [])]
            T['strips'] = [(poly, w * k, kd, off) for (poly, w, kd, off) in T.get('strips', [])]
            self.tail = T
        for sx in (1, -1):
            side = 'l' if sx > 0 else 'r'
            for h in T.get('housings', []):
                out = mirror2(h['outline'], sx)
                st = h.get('style', 'tail_house')
                mat = h.get('mat', 'solid')
                if mat == 'ind':
                    mat = 'ind_' + side
                self.conform(out, 'B', self.TRIS, [(h.get('off', 0.02), self.el('body', mat, st), st)], self.el('body', 'solid', h.get('bezel', 'tail_bezel')),
                             h.get('bezel', 'tail_bezel'), dens=h.get('dens', 160))
            for (x, y, r, ring, core, cr) in T.get('rounds', []):
                p, n = self.hits([(sx * x, y)], 'B', self.TRIS, True)
                c = p[0] + n[0] * (T.get('round_off', 0.022))
                n = n[0]
                m.element('tail_chrome', 'solid', 'body', 'chrome')
                m.cylinder(c, n, r, 0.008, 14, 'chrome', r1=r * 0.95, caps=(False, False))
                rm = mat_of(ring, side)
                m.element('tail_' + ring, rm, 'body', 'tail_ring' if ring in ('tail', 'brake') else LAMP_STYLE.get(ring, 'tail_ring'))
                ax = unit(np.cross(V(0, 1, 0), n)) if abs(n[1]) < 0.9 else V(1, 0, 0)
                aw = np.cross(n, ax)
                m.face([c + n * 0.008 + (ax * math.cos(a) + aw * math.sin(a)) * r * 0.95 for a in np.linspace(0, 2 * math.pi, 14, endpoint=False)],
                       m._cur.style, outward=n, dens=160)
                if core:
                    cm = mat_of(core, side)
                    m.element('tail_core_' + core, cm, 'body', LAMP_STYLE[core])
                    m.cylinder(c + n * 0.008, n, r * cr, 0.005, 12, LAMP_STYLE[core], caps=(False, True))
            for (outline, kind, style, off) in T.get('rects', []):
                out = mirror2(outline, sx)
                self.conform(out, 'B', self.TRIS, [(off, self.el('body', mat_of(kind, side), style), style)], None, None, rings=(1.0, 0.5), dens=160)
            for (poly, width, kind, off) in T.get('strips', []):
                pp = [(sx * u, v) for u, v in poly]
                self.strip(pp, width, 'B', off, self.el('body', mat_of(kind, side), LAMP_STYLE.get(kind, 'tail_ring')), LAMP_STYLE.get(kind, 'tail_ring'))
        for (poly, width, kind, off) in T.get('center_strips', []):
            self.strip(poly, width, 'B', off, self.el('body', mat_of(kind, 'l'), LAMP_STYLE.get(kind, 'brake_core')), LAMP_STYLE.get(kind, 'brake_core'))
        if 'garnish' in T:
            gdef = T['garnish']
            self.conform(gdef['outline'], 'B', self.TRIS, [(gdef.get('off', 0.008), self.el('body', 'solid', 'garnish'), 'garnish')],
                         self.el('body', 'solid', 'tail_bezel'), 'tail_bezel', dens=320)

    def emblems(self):
        """Generic maker badge (shape + initial) on the nose and the tail."""
        import importlib
        B = importlib.import_module('_brands').BRANDS
        if self.make not in B:
            return
        shape, txt, col = B[self.make]
        w, h = (0.1, 0.1) if shape in ('circle', 'diamond') else (0.13, 0.07) if shape != 'shield' else (0.08, 0.1)
        def outline(cx, cy):
            if shape == 'circle':
                return ngon(w / 2, 16, cx, cy)
            if shape == 'oval':
                return ngon(w / 2, 16, cx, cy, ry=h / 2)
            if shape == 'diamond':
                return [(cx, cy + h / 2), (cx - w / 2, cy), (cx, cy - h / 2), (cx + w / 2, cy)][::-1]
            if shape == 'shield':
                return [(cx - w / 2, cy + h / 2), (cx + w / 2, cy + h / 2), (cx + w / 2, cy), (cx, cy - h / 2), (cx - w / 2, cy)][::-1]
            return rrect(w, h, 0.012, 2, cx, cy)
        fy, ry_ = getattr(self, 'emblem_y', (None, None))
        fy = fy if fy is not None else self.belt(self.L) - 0.07
        ry_ = ry_ if ry_ is not None else self.plate_r + 0.15
        for view, cy, sgn in (('F', fy, 1), ('B', ry_, -1)):
            style = 'emblem_' + view
            self.m.painters[style] = emblem_painter(shape, txt, col, 0.0, cy, w, h, sgn)
            tris = self.body_tris(('body', 'fb_0', 'rb_0')) if view == 'F' else self.TRIS
            self.conform(outline(0.0, cy), view, tris, [(0.007, self.el('body', 'solid', style), style)], self.el('body', 'solid', 'chrome'), 'chrome',
                         rings=(1.0, 0.6, 0.3), dens=420, depth=0.01)

    # ===================================================================================== small parts
    def mirrors(self):
        m = self.m
        mz = self.WS1 - 0.04
        for sx in (1, -1):
            side = 'l' if sx > 0 else 'r'
            dn = self.door_at(mz) or self.door_at(mz - 0.1)
            g = 'door_%s%s' % (dn, side) if dn else 'body'
            x = sx * (self.hw(mz) - 0.08)
            y = self.belt(mz) + 0.02
            self.el(g, 'solid', 'trim')
            m.box(V(x + sx * 0.03, y, mz), (0.08, 0.035, 0.06), 'trim')
            self.el(g, 'paint' if not self.mirror_chrome else 'solid', 'paint' if not self.mirror_chrome else 'chrome')
            style = m._cur.style
            outline = [(-0.065, 0.0), (0.06, 0.0), (0.05, 0.075), (-0.055, 0.07)] if not self.mirror_round else ngon(0.045, 10, 0, 0.04)
            n0 = len(m._cur.faces)
            m.prism(outline, mz - 0.03, mz + 0.05, 'z', style)
            for f in m._cur.faces[n0:]:
                f.verts = [V(p[0] + x + sx * 0.12, p[1] + y + 0.025, p[2]) for p in f.verts]
            self.el(g, 'solid', 'mirror')
            m.face([V(x + sx * 0.06, y + 0.03, mz - 0.033), V(x + sx * 0.18, y + 0.03, mz - 0.033), V(x + sx * 0.17, y + 0.095, mz - 0.033),
                    V(x + sx * 0.065, y + 0.09, mz - 0.033)], 'mirror', outward=V(0, 0, -1))

    mirror_chrome = False
    mirror_round = False

    def wipers(self):
        m = self.m
        self.el('body', 'solid', 'trim_gloss')
        w = self.hw(self.WS1) - 0.2
        for x0, x1 in ((w - 0.05, 0.08), (0.04, -w * 0.62)):
            z0 = self.WS1 - 0.03
            p0 = V(x0, self.deck(x0, z0) + 0.04, z0 - 0.02)
            p1 = V(x1, self.deck(x1, z0) + 0.035, z0 - 0.005)
            d = unit(p1 - p0)
            ang = math.atan2(d[2], d[0])
            m.box((p0 + p1) / 2, (np.linalg.norm(p1 - p0), 0.012, 0.012), 'trim_gloss', M=ry(-math.degrees(ang)))

    def liners(self):
        m = self.m
        e = self.el('body', 'solid', 'under')
        for za in (self.YR, self.YF):
            inner = min(0.6, self.hw(za) * 0.7)
            for sx in (1, -1):
                angs = np.linspace(math.radians(-10), math.radians(190), 10)
                for a0, a1 in zip(angs[:-1], angs[1:]):
                    RA, R = self.RA, self.R
                    q = [V(sx * inner, R + RA * math.sin(a0), za + RA * math.cos(a0)), V(sx * inner, R + RA * math.sin(a1), za + RA * math.cos(a1)),
                         V(sx * (self.hw(za) - 0.04), R + RA * math.sin(a1), za + RA * math.cos(a1)),
                         V(sx * (self.hw(za) - 0.04), R + RA * math.sin(a0), za + RA * math.cos(a0))]
                    c = np.mean(q, axis=0)
                    m.face(q, 'under', el=e, outward=V(0, R - c[1], za - c[2]), dens=8)

    # ===================================================================================== variants
    def clip_poly(self, poly, axis, val, keep_less=True):
        out = []
        n = len(poly)
        for i in range(n):
            a, b = poly[i], poly[(i + 1) % n]
            ia = (a[axis] <= val) == keep_less
            ib = (b[axis] <= val) == keep_less
            if ia:
                out.append(a)
            if ia != ib:
                t = (val - a[axis]) / (b[axis] - a[axis])
                out.append(a + (b - a) * t)
        return out

    def hood_panel(self, g, style, mat='solid', lift=0.004):
        z0h, z1h, hwid = self.hood
        e = self.el(g, mat, style)
        for z0, z1 in zip(self.ZS[:-1], self.ZS[1:]):
            if z0 < z0h + 0.02 - 1e-6 or z1 > z1h - 0.06 + 1e-6:
                continue
            A, B = self.section(z0), self.section(z1)
            for j in (7, 8):
                for sx in (1, -1):
                    q = [V(sx * A[j][0], A[j][1], z0), V(sx * B[j][0], B[j][1], z1), V(sx * B[j + 1][0], B[j + 1][1], z1), V(sx * A[j + 1][0], A[j + 1][1], z0)]
                    q = self.clip_poly(q, 0, hwid - 0.02, True) if sx > 0 else self.clip_poly(q, 0, -hwid + 0.02, False)
                    if len(q) < 3:
                        continue
                    n = unit(newell(q))
                    if n[1] < 0:
                        n = -n
                    self.m.face([p + n * lift for p in q], style, el=e, outward=V(0, 1, 0))

    def hoods(self):
        m = self.m
        z0h, z1h, hwid = self.hood
        zc = z0h + (z1h - z0h) * 0.4
        for k in range(5):
            g = 'hd_%d' % k
            if k == 0:
                m.element('hood_stock', 'solid', g, 'trim')
                continue
            if k in (1, 4):
                self.hood_panel(g, 'carbon_hood')
            if k in (2, 4):
                self.el(g, 'solid', 'vent_slats')
                for sx in (1, -1):
                    for i in range(4):
                        z = zc + i * 0.06
                        x = sx * hwid * 0.62
                        m.box(V(x, self.deck(x, z) + 0.01, z), (hwid * 0.38, 0.012, 0.03), 'vent_slats')
                if k == 2:
                    self.hood_panel(g, 'paint.hoodvent', 'paint', 0.003)
            if k == 3:
                self.el(g, 'paint', 'paint')
                y = self.deck(0, zc)
                PT.prism_x(m, [(zc - 0.25, y - 0.01), (zc + 0.35, y - 0.01), (zc + 0.15, y + 0.06), (zc - 0.18, y + 0.065)], -0.2, 0.2, 'paint')
                m.element('scoop_mouth', 'solid', g, 'grille')
                m.face([V(0.18, y, zc + 0.14), V(-0.18, y, zc + 0.14), V(-0.18, y + 0.055, zc + 0.155), V(0.18, y + 0.055, zc + 0.155)], 'grille', outward=V(0, 0.2, 1))

    def skirts(self):
        m = self.m
        for k in range(3):
            g = 'sk_%d' % k
            if k == 0:
                m.element('skirt_stock', 'solid', g, 'trim')
                continue
            self.el(g, 'paint', 'paint')
            z0, z1 = self.YR + self.RA + 0.01, self.YF - self.RA - 0.01
            zm = (z0 + z1) / 2
            w, b = self.hw(zm), self.bot(zm)
            for sx in (1, -1):
                prof = [(sx * (w * 0.93), b + 0.06), (sx * (w * 1.01), b + 0.05), (sx * (w * 1.02), b - 0.035), (sx * (w * 0.9), b - 0.04)]
                m.prism(prof if sx > 0 else prof[::-1], z0, z1, 'z', 'paint')
                if k == 2:
                    self.el(g, 'solid', 'carbon')
                    for zz in (z0 + 0.06, z1 - 0.06):
                        m.box(V(sx * (w + 0.02), b - 0.02, zz), (0.06, 0.008, 0.16), 'carbon')
                    self.el(g, 'paint', 'paint')

    def exhaust_list(self):
        if self.no_exhaust:
            return []
        st = getattr(self, 'stock_exhaust', 'oval')
        return [st] + [e for e in PT.EXHAUSTS if e != st][:4]

    def exhausts(self):
        xm, y, ze = self.exhaust
        for k, kind in enumerate(self.exhaust_list()):
            PT.exhaust(self.m, 'ex_%d' % k, kind, ze, y, x_main=xm)

    def spoiler_list(self):
        stock = self.spoiler[2]
        rest = [s for s in PT.SPOILERS if s != stock]
        if stock == 'none':
            return ['none'] + rest[:4] if 'hoop' not in rest[:4] else ['none'] + [s for s in rest if s != 'hoop'][:4]
        return [stock] + rest[:4]

    def spoilers(self):
        z_te, hwid, stock = self.spoiler
        deck = lambda x, z: max(self.deck(x, z), self.belt(z))
        for k, kind in enumerate(self.spoiler_list()):
            if kind == 'none':
                self.m.element('spoiler_none', 'solid', 'sp_%d' % k, 'trim')
                continue
            PT.spoiler(self.m, 'sp_%d' % k, kind, deck, z_te, hwid)

    def wheel_list(self):
        rest = [w for w in PT.WHEELS if w != self.stock_wheel]
        return [self.stock_wheel] + rest[:4]

    def wheels(self):
        for k, kind in enumerate(self.wheel_list()):
            PT.wheel(self.m, 'wh_%d' % k, kind, R=self.R, TW=self.TW + 0.01, Rr=self.Rr, add_caliper=(k == 0), caliper_color=self.caliper,
                     whitewall=(k == 0 and getattr(self, 'whitewall', False)))

    # ===================================================================================== interior
    def interior(self):
        m = self.m
        I = dict(driver_x=0.3, rear=True, cluster='analog3', screen=False, shifter='manual', seat='sport', dash_col=(44, 44, 47))
        I.update(self.cabin)
        dx = I['driver_x']
        wz = self.WS1                                     # bottom of the windscreen
        bt = self.belt(wz - 0.3) + 0.01
        fz = I.get('seat_z', wz - 1.04)
        self.seat_z = fz
        floor = self.bot((self.YR + self.YF) / 2) + 0.025
        sw = min(0.42, (self.hw(fz) - 0.12) - 0.05)
        self.el('body', 'solid', 'carpet')
        m.box(V(0, floor, (self.CABIN0 + wz) / 2), (2 * (self.hw(fz) - 0.12), 0.02, wz - self.CABIN0), 'carpet')
        m.prism([(-0.13, floor), (0.13, floor), (0.1, floor + 0.14), (-0.1, floor + 0.14)], self.CABIN0 + 0.4, wz - 0.05, 'z', 'carpet')
        rows = [(fz, [dx, -dx], 'front')]
        if I['rear']:
            rows.append((fz - I.get('rear_gap', 0.62), [dx, -dx] if not I.get('bench') else [0.0], 'rear'))
        self.seats_meta = []
        for zc, xs, kind in rows:
            for x in xs:
                hy = floor + 0.06
                if kind == 'front':
                    self.front_seat(x, hy, zc, sw, I['seat'])
                else:
                    wbench = 1.0 if I.get('bench') else sw
                    self.el('body', 'solid', 'seat_fabric')
                    m.box(V(x, hy + 0.05, zc), (wbench, 0.09, 0.4), 'seat_fabric')
                    m.box(V(x, hy + 0.27, zc - 0.22), (wbench, 0.42, 0.1), 'seat_fabric', M=rx(-24))
            for x in (xs if len(xs) == 2 else [dx, -dx]):
                self.seats_meta.append([x, floor + 0.15, zc - self.L / 2])
        # dashboard
        self.el('body', 'solid', 'dash')
        prof = [(wz + 0.01, bt - 0.03), (wz - 0.26, bt + 0.01), (wz - 0.36, bt - 0.005), (wz - 0.42, bt - 0.06), (wz - 0.42, bt - 0.19), (wz - 0.34, bt - 0.33),
                (wz - 0.09, floor + 0.2), (wz + 0.06, floor + 0.2), (wz + 0.08, bt - 0.1)]
        dwid = self.hw(wz - 0.3) - 0.07
        PT.prism_x(m, prof[::-1], -dwid, dwid, 'dash')
        # cluster
        cz = wz - 0.295
        cy = bt + 0.045
        self.el('body', 'solid', 'dash')
        binn = [(-0.22, 0.0), (-0.21, 0.05), (-0.15, 0.1), (0.0, 0.12), (0.15, 0.1), (0.21, 0.05), (0.22, 0.0)]
        if I['cluster'] != 'flat':
            m.prism([(dx + a, bt - 0.02 + b) for a, b in binn][::-1], cz + 0.005, cz + 0.135, 'z', 'dash')
            m.box(V(dx, bt + 0.1, cz - 0.035), (0.46, 0.02, 0.09), 'dash')
        self.el('body', 'screen', 'gauges_' + I['cluster'])
        m.face([V(dx + 0.19, cy - 0.05, cz), V(dx - 0.19, cy - 0.05, cz), V(dx - 0.19, cy + 0.05, cz - 0.01), V(dx + 0.19, cy + 0.05, cz - 0.01)],
               'gauges_' + I['cluster'], outward=V(0, 0, -1), dens=256)
        self.m.painters['gauges_' + I['cluster']] = gauge_painter(I['cluster'], dx, cy)
        self.cluster_meta = dict(center=[dx, cy, cz - 0.006 - self.L / 2], right=[-1, 0, 0], up=[0, 0.995, 0.1], w=0.38, h=0.1)
        # centre stack (+ screen on modern cars)
        Ms = ry(12 if dx > 0 else -12) @ rx(8)
        self.el('body', 'solid', 'dash')
        m.box(V(0, bt - 0.28, wz - 0.38), (0.26, 0.32, 0.06), 'dash', M=Ms)
        self.el('body', 'screen' if I['screen'] else 'solid', 'stack_screen' if I['screen'] else 'stack_face')
        m.box(V(0.0, bt - 0.28, wz - 0.414), (0.22, 0.28, 0.006), m._cur.style, M=Ms)
        self.el('body', 'solid', 'grille')
        for vx in (dwid - 0.17, -dwid + 0.17):
            m.box(V(vx, bt - 0.12, wz - 0.415), (0.09, 0.045, 0.012), 'grille')
        # console, gear lever (part 'shifter'), handbrake
        self.el('body', 'solid', 'dash')
        m.box(V(0, floor + 0.18, fz + 0.3), (0.2, 0.1, 0.75), 'dash')
        gz = fz + 0.47
        gy = floor + 0.23
        self.el('body', 'solid', 'alu')
        m.box(V(0, gy + 0.002, gz), (0.12, 0.004, 0.2), 'alu')
        self.el('body', 'solid', 'leather')
        m.box(V(0, gy + 0.015, gz), (0.09, 0.03, 0.09), 'leather')
        self.el('shifter', 'solid', 'alu')
        if I['shifter'] == 'auto':
            m.box(V(0, gy + 0.07, gz), (0.03, 0.11, 0.05), 'leather')
            self.el('shifter', 'solid', 'leather')
            m.box(V(0, gy + 0.14, gz + 0.005), (0.05, 0.06, 0.08), 'leather')
        else:
            m.cylinder(V(0, gy, gz), V(0, 1, 0), 0.008, 0.12, 6, 'alu')
            self.el('shifter', 'solid', 'leather')
            m.box(V(0, gy + 0.14, gz), (0.042, 0.042, 0.042), 'leather')
        self.shifter_meta = dict(pivot=[0, gy, gz - self.L / 2], type=I['shifter'])
        self.el('body', 'solid', 'leather')
        m.box(V(0, gy + 0.02, fz + 0.08), (0.035, 0.03, 0.22), 'leather', M=rx(-12))
        m.box(V(0, gy + 0.05, fz - 0.15), (0.18, 0.06, 0.3), 'leather')
        # steering wheel (part 'steer')
        sc = V(dx, bt - 0.06, wz - 0.52)
        ax = unit(V(0, math.sin(math.radians(24)), -math.cos(math.radians(24))))
        self.steer_meta = dict(pivot=list(sc + V(0, 0, -self.L / 2)), axis=list(ax))
        self.steering_wheel(sc, ax, I.get('wheel_r', 0.185), I.get('spokes', (0, 180, 240, 300)))
        self.el('body', 'solid', 'dash')
        m.cylinder(sc, -ax, 0.035, 0.24, 8, 'dash')
        # pedals
        self.el('body', 'solid', 'alu')
        for px, w_ in ((0.12, 0.06), (0.02, 0.08), (-0.1, 0.09)):
            m.box(V(dx + px * (1 if dx > 0 else -1), floor + 0.1, wz - 0.12), (w_, 0.075, 0.012), 'alu', M=rx(20))
        # door cards
        for name, a, b in self.doors:
            for sx in (1, -1):
                g = 'door_%s%s' % (name, 'l' if sx > 0 else 'r')
                x = sx * (self.hw((a + b) / 2) - 0.085)
                top = self.belt(b) - 0.02
                self.el(g, 'solid', 'door_card')
                m.face([V(x, floor + 0.08, a + 0.04), V(x, floor + 0.1, b - 0.06), V(x, top, b - 0.04), V(x, top + 0.02, a + 0.04)], 'door_card', outward=V(-sx, 0, 0))
                self.el(g, 'solid', 'leather')
                m.box(V(x - sx * 0.03, floor + 0.38, (a + b) / 2), (0.05, 0.04, (b - a) * 0.3), 'leather')
                self.el(g, 'solid', 'dash')
                m.box(V(x - sx * 0.015, top, (a + b) / 2), (0.04, 0.035, b - a - 0.1), 'dash')
        if not self.open_top:
            rz = wz - 0.36
            ry_ = self.roofz(rz) - 0.1
            self.el('body', 'solid', 'dash')
            m.box(V(0, ry_, rz), (0.2, 0.055, 0.03), 'dash', M=rx(-10))
            self.el('body', 'solid', 'mirror')
            m.box(V(0, ry_, rz - 0.017), (0.18, 0.042, 0.004), 'mirror', M=rx(-10))
        self.I = I

    def front_seat(self, x, hy, zc, sw, kind):
        m = self.m
        self.el('body', 'solid', 'leather')
        m.box(V(x, hy, zc), (sw, 0.08, 0.46), 'leather')
        self.el('body', 'solid', 'seat_fabric')
        m.box(V(x, hy + 0.055, zc + 0.01), (sw - 0.1, 0.05, 0.42), 'seat_fabric', M=rx(-5))
        self.el('body', 'solid', 'leather')
        if kind == 'sport':
            for bx in (1, -1):
                m.box(V(x + bx * (sw / 2 - 0.02), hy + 0.07, zc + 0.01), (0.07, 0.09, 0.42), 'leather', M=rx(-5))
        Mb = rx(-16)
        pivot = V(x, hy + 0.05, zc - 0.23)
        at = lambda l: pivot + Mb @ V(l)
        m.box(at((0, 0.3, -0.03)), (sw, 0.6, 0.1), 'leather', M=Mb)
        self.el('body', 'solid', 'seat_fabric')
        m.box(at((0, 0.29, 0.025)), (sw - 0.14, 0.46, 0.02), 'seat_fabric', M=Mb)
        self.el('body', 'solid', 'leather')
        if kind == 'sport':
            for bx in (1, -1):
                m.box(at((bx * (sw / 2 - 0.02), 0.27, 0.02)), (0.07, 0.46, 0.12), 'leather', M=Mb)
        m.box(at((0, 0.7, -0.02)), (0.24, 0.16, 0.08), 'leather', M=Mb)

    def steering_wheel(self, c, ax, Rr=0.185, spokes=(0, 180, 240, 300)):
        m = self.m
        self.el('steer', 'solid', 'leather')
        u = V(1, 0, 0)
        w = np.cross(ax, u)
        t = 0.02
        N = 12
        pts = [c + (u * math.cos(a) + w * math.sin(a)) * Rr for a in np.linspace(0, 2 * math.pi, N, endpoint=False)]
        for i in range(N):
            p0, p1 = pts[i], pts[(i + 1) % N]
            r0, r1 = unit(p0 - c), unit(p1 - c)
            sq = lambda p, rd: [p + rd * t + ax * t, p + rd * t - ax * t, p - rd * t - ax * t, p - rd * t + ax * t]
            A, B = sq(p0, r0), sq(p1, r1)
            for k in range(4):
                q = [A[k], B[k], B[(k + 1) % 4], A[(k + 1) % 4]]
                m.face(q, 'leather', outward=np.mean(q, axis=0) - (p0 + p1) / 2)
        Mw = np.c_[u, w, ax]
        self.el('steer', 'solid', 'dash')
        for a in spokes:
            Ms = Mw @ rz(a)
            m.box(c + Ms @ V(Rr * 0.55, 0, 0.012), (Rr * 0.8, 0.032, 0.014), 'dash', M=Ms)
        self.el('steer', 'solid', 'leather')
        m.box(c + ax * 0.025, (0.15, 0.12, 0.05), 'leather', M=Mw)

    # ===================================================================================== painting
    def paint_painter(self):
        car = self

        def f(face, P, n):
            st = face.style
            x, y, z = P[..., 0], P[..., 1], P[..., 2]
            out = fill(P, (236, 236, 236))
            out[..., :3] += noise(P, 3.0, 40)[..., None]
            side = abs(n[0]) > 0.45
            top = n[1] > 0.45
            front = n[2] > 0.1
            rear = n[2] < -0.45
            px = 1.0 / face.dens
            put = lambda mask, c: out.__setitem__(mask, np.array(c, float))
            if side:
                lo = car.bot((car.YR + car.YF) / 2) + 0.035
                for name, a, b in car.doors:
                    door = (y > lo) & (y < car.belt(b) + 0.15)
                    for zz in (a, b):
                        put(door & (np.abs(z - zz) < px * 0.6), GAP)
                    hz = a + 0.12 if name == 'f' and len(car.doors) == 1 else a + 0.1
                    hz = b - 0.2 if car.handle_rear else a + 0.08
                    put((z > hz - 0.06) & (z < hz + 0.06) & (np.abs(y - (car.belt(hz) - 0.055)) < 0.014), BLACK if not car.chrome_handles else CHROME_T)
                put((np.abs(z - car.FB_Z - (y - 0.3) * 0.12) < px * 0.6) & (y < car.belt(car.FB_Z) - 0.04), GAP)
                put((np.abs(z - car.RB_Z) < px * 0.6) & (y < car.zsh(car.RB_Z) + 0.02), GAP)
                if car.rocker_black:
                    put((y < lo + 0.04) & (z > car.YR + car.RA - 0.01) & (z < car.YF - car.RA + 0.01), BLACK)
                if car.belt_chrome:
                    put((np.abs(y - (car.belt_chrome)) < 0.008) & (z > 0.15) & (z < car.L - 0.15), CHROME_T)
                for (mz, my) in car.markers:
                    put((np.abs(z - mz) < 0.06) & (np.abs(y - my) < 0.018), (255, 150, 40, NOTINT) if mz > car.L / 2 else (200, 20, 30, NOTINT))
                fs, fz, fy = car.fuel
                if (fs == 'r') == (n[0] < 0):
                    put(np.abs(np.hypot(z - fz, y - fy) - 0.058) < px * 0.6, GAP)
            if top and car.hood:
                z0h, z1h, hwid = car.hood
                ax = np.abs(x)
                hood = (z > z0h) & (z < z1h)
                put(hood & (np.abs(ax - hwid) < px * 0.6), GAP)
                put((np.abs(z - z0h) < px * 0.6) & (ax < hwid), GAP)
                put((np.abs(z - z1h) < px * 0.6) & (ax < hwid), GAP)
                if car.hatch_x:
                    put((z < car.CABIN0 + 0.02) & (np.abs(ax - car.hatch_x) < px * 0.6), GAP)
                if car.trunk_z:
                    put((np.abs(z - car.trunk_z) < px * 0.6) & (ax < car.hw(car.trunk_z) - 0.12), GAP)
                if car.top_art:
                    car.top_art(car, out, P, n)
                if 'hoodvent' in st:
                    for sx in (1, -1):
                        put((np.abs(x - sx * hwid * 0.62) < hwid * 0.2) & (z > z0h + (z1h - z0h) * 0.4 - 0.03) & (z < z0h + (z1h - z0h) * 0.4 + 0.21), (20, 20, 22, NOTINT))
            if rear and car.trunk_y:
                put(np.abs(y - car.trunk_y) < px * 0.6, GAP)
            if 'fb' in st and front:
                k = int(st.split('fb')[1][0])
                uv = np.stack([x, y], -1)
                car.paint_mouth(out, P, uv, k)
                if car.front_art:
                    car.front_art(car, out, P, n, uv, k)
            if 'rb' in st and rear:
                k = int(st.split('rb')[1][0])
                uv = np.stack([x, y], -1)
                py = car.plate_r
                put(inside(uv, rrect(0.58, 0.17, 0.03, 3, 0, py)), (22, 22, 24, NOTINT))
                for sx in (1, -1):
                    put(inside(uv, rrect(0.17, 0.026, 0.01, 2, sx * (car.hw(0) - 0.12), car.bot(0) + 0.07)), (160, 16, 26, NOTINT))
                    if k == 2:
                        for i in range(3):
                            put(inside(uv, rrect(0.12, 0.014, 0.006, 1, sx * (car.hw(0) - 0.16), car.bot(0) + 0.03 + i * 0.025)), BLACK)
                if k == 4:
                    put(y < car.bot(0) + 0.06, (26, 26, 28, NOTINT))
                    for sx in (1, -1):
                        put(inside(uv, rrect(0.2, 0.09, 0.04, 3, sx * 0.5, car.exhaust[1] + 0.03)), (8, 8, 10, NOTINT))
                if car.rear_art:
                    car.rear_art(car, out, P, n, uv, k)
            if side and car.side_art:
                car.side_art(car, out, P, n)
            out[..., 3] = np.where(out[..., 3] < 250, out[..., 3], 255)
            return out
        return f

    handle_rear = False
    chrome_handles = False
    trunk_z = None

    def paint_mouth(self, out, P, uv, k):
        """Front intakes / grilles described by self.mouth: [dict(shape, w, h, r, cx, cy, pattern, col, chrome_frame)]."""
        for i, mo in enumerate(self.mouth):
            if mo.get('only') is not None and k not in mo['only']:
                continue
            grow = 1.0 + (0.08 if k in (2, 3) else 0.0) * mo.get('grow', 1.0)
            w, h = mo['w'] * grow, mo['h'] * grow
            cy = mo['cy'] - FB_CHIN[k] * 0.4
            outs = [(mo['cx'], cy)] if not mo.get('mirror') else [(mo['cx'], cy), (-mo['cx'], cy)]
            for cx, cyy in outs:
                poly = rrect(w, h, mo.get('r', 0.03), 3, cx, cyy) if mo.get('shape', 'rrect') == 'rrect' else mo['poly'](cx, cyy, w, h)
                mask = inside(uv, poly)
                base = mo.get('col', (40, 41, 44))
                out[mask] = np.array(list(base) + [NOTINT], float)
                pat = mo.get('pattern', 'honeycomb')
                if pat == 'honeycomb':
                    hole = S.honeycomb(P, uv, 0.024 if k != 2 else 0.018)
                elif pat == 'bars':
                    hole = np.mod((uv[..., 1] - cyy) / mo.get('pitch', 0.03), 1.0) < 0.55
                elif pat == 'vbars':
                    hole = np.mod((uv[..., 0] - cx) / mo.get('pitch', 0.03), 1.0) < 0.55
                elif pat == 'grid':
                    hole = (np.mod((uv[..., 1] - cyy) / mo.get('pitch', 0.03), 1.0) < 0.6) & (np.mod((uv[..., 0] - cx) / mo.get('pitch', 0.03), 1.0) < 0.6)
                else:
                    hole = np.zeros(mask.shape, bool)
                hc = mo.get('hole', (8, 8, 10))
                out[mask & hole] = np.array(list(hc) + [NOTINT], float)
                if mo.get('frame'):
                    inner = inside(uv, rrect(w - 0.03, h - 0.03, max(0.002, mo.get('r', 0.03) - 0.015), 3, cx, cyy))
                    out[mask & ~inner] = np.array(list(mo['frame']) + [NOTINT], float)

    # ===================================================================================== run
    def lamp_painters(self):
        car = self
        for hd in self.head:
            pass

        def head_house(face, P, n):
            out = fill(P, (150, 156, 164))
            out[..., :3] += noise(P, 8, 128)[..., None]
            sxs = np.sign(np.mean(P[..., 0]))
            for hd in car.head:
                view = hd.get('view', 'T')
                uv = np.stack([P[..., 0], P[..., 2]], -1) if view == 'T' else np.stack([P[..., 0], P[..., 1]], -1)
                for (u, v, r, kind) in hd.get('lamps', []):
                    d = np.hypot(uv[..., 0] - sxs * u, uv[..., 1] - v)
                    out[(d > r * 1.02) & (d < r * 1.25), :3] = (210, 214, 220)
                    out[(d > r * 1.25) & (d < r * 1.32), :3] = (90, 94, 100)
                rim = seg_dist(uv, mirror2(hd['outline'], int(sxs) or 1), closed=True) < 0.012
                out[rim, :3] = hd.get('rim', (34, 36, 40))
            return out
        dark = lambda face, P, n: (lambda o: (o.__setitem__((Ellipsis, slice(0, 3)), o[..., :3] * 0.35), o)[1])(head_house(face, P, n))
        self.m.painters.update({'head_house': head_house, 'head_house_black': dark})

    def run(self, out_dir):
        m = self.m
        self.build_body()
        self.headlamps()
        if self.popups:
            self.popup_lamps()
        self.front_bumper_lamps()
        self.taillamps()
        self.emblems()
        self.mirrors()
        self.wipers()
        self.hoods()
        self.skirts()
        self.exhausts()
        self.spoilers()
        self.wheels()
        self.interior()
        self.liners()
        if self.extra:
            self.extra(self)
        self.lamp_painters()
        m.painters.update(LAMP_PAINTERS)
        m.painters['paint'] = self.paint_painter()
        gd = self.tail.get('garnish', {})
        m.painters['garnish'] = garnish_painter(self.badge or '', gd.get('text_y', 0.7), gd.get('bg', (60, 12, 18)), gd.get('fg', (200, 200, 205)), gd.get('th', 0.05))
        m.painters.update(self.painters if hasattr(self, 'painters') else {})
        return self.write(out_dir)

    def write(self, out_dir):
        m = self.m
        m.pack()
        from PIL import Image
        atlas = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'trafficcraft', 'textures', 'vehicle',
                             'atlas.png')
        mc = Image.open(atlas).convert('RGBA').crop((64, 128, 128, 192))
        m.paint(mc)
        L = self.L
        cat = dict(fb=[dict(name=n) for n in FB_NAMES], rb=[dict(name=n) for n in RB_NAMES],
                   ex=[dict(name=PT.EXHAUST_NAMES[PT.EXHAUSTS.index(e)]) for e in self.exhaust_list()],
                   sp=[dict(name=PT.SPOILER_NAMES[PT.SPOILERS.index(s)] if s in PT.SPOILERS else 'None') for s in self.spoiler_list()],
                   hd=[dict(name=n) for n in HD_NAMES], wh=[dict(name=PT.WHEEL_NAMES[PT.WHEELS.index(w)] if w in PT.WHEELS else w) for w in self.wheel_list()],
                   sk=[dict(name=n) for n in SK_NAMES])
        doors = {}
        for name, a, b in self.doors:
            for sx in (1, -1):
                doors['door_%s%s' % (name, 'l' if sx > 0 else 'r')] = dict(hinge=[round(sx * (self.hw(b) - 0.03), 3), round(b - L / 2, 3)], side=sx,
                                                                            kind='front' if name == 'f' else 'rear')
        xm, ye, ze = self.exhaust
        META = dict(id=self.id, name=self.name, make=self.make, model=self.model, year=self.year, category=self.category, category_name=self.category_name,
                    description=self.description, length=L, width=self.W, height=self.H, wheelbase=round(self.YF - self.YR, 3),
                    wheels=[dict(x=sx * self.TRACK, y=self.R, z=round(za - L / 2, 4), r=self.R, steer=za > L / 2, mirror=sx < 0) for za in (self.YR, self.YF)
                            for sx in (1, -1)],
                    seats=self.seats_meta, doors=doors, steer=self.steer_meta, cluster=self.cluster_meta, shifter=self.shifter_meta,
                    beams=[[sx * self.beam[0], self.beam[1], self.beam[2] - L / 2] for sx in (1, -1)],
                    plates=[dict(pos=[0, self.plate_f, round(self.L + 0.005 - L / 2, 4)], normal=[0, 0, 1], w=0.52, h=0.12),
                            dict(pos=[0, self.plate_r, round(-0.012 - L / 2, 4)], normal=[0, 0, -1], w=0.52, h=0.12)],
                    filler=[(-1 if self.fuel[0] == 'r' else 1) * (self.hw(self.fuel[1]) + 0.02), self.fuel[2], self.fuel[1] - L / 2],
                    catalog=cat, exhaust_variants=[[[p[0], p[1], p[2] - L / 2, p[3], p[4], p[5]] for p in PT.exhaust_points(k, ze, ye, xm)] for k in self.exhaust_list()],
                    screen_sensor=bool(self.I.get('screen')), texture=self.id, seats_n=len(self.seats_meta))
        META['matcap'] = [m.matcap_rect[0] / m.tex, m.matcap_rect[1] / m.tex, m.matcap_rect[2] / m.tex, m.matcap_rect[3] / m.tex]
        META['exhausts'] = META['exhaust_variants'][0] if META['exhaust_variants'] else []
        if self.no_exhaust:
            cat.pop('ex', None)
        META['dash'] = dict(cluster_style=self.I['cluster'], dials=DIALS.get(self.I['cluster'], DIALS['analog3']))
        for k in ('maxSpeed', 'accel', 'brake', 'mass', 'fuel_l', 'spawnWeight', 'colors', 'electric', 'horn', 'steerMax', 'backfire'):
            if hasattr(self, k):
                META['fuel' if k == 'fuel_l' else k] = getattr(self, k)
        GRIP = dict(super=1.18, sports=1.0, wagon=0.95, sedan=0.86, compact=0.84, luxury=0.82, muscle=0.8, classic=0.74, suv=0.7)
        META['grip'] = getattr(self, 'grip', GRIP.get(self.category, 0.85))
        from_engine = __import__('sounds_ref').engine_meta(self.engine)
        META['engine'] = from_engine
        META.update(self.meta_extra)
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, self.id + '.bbmodel')
        m.write_bbmodel(path, META)
        print('wrote', path, 'elements', len(m.elements), 'faces', sum(len(e.faces) for e in m.elements), 'texture', m.tex)
        return path


# ========================================================================================= shared painters
def gauge_painter(style, dx, cy):
    def f(face, P, n):
        out = fill(P, (12, 12, 14))
        x, y = P[..., 0], P[..., 1]
        if style == 'digital':
            out[..., :3] = (8, 14, 22)
            for i, (cx, r) in enumerate(((dx + 0.11, 0.045), (dx - 0.11, 0.045))):
                d = np.hypot(x - cx, y - cy)
                out[(d > r * 0.85) & (d < r), :3] = (40, 160, 230)
            out[(np.abs(x - dx) < 0.05) & (np.abs(y - cy) < 0.03), :3] = (30, 60, 90)
            return out
        dials = {'analog3': ((dx + 0.12, 0.045), (dx, 0.056), (dx - 0.12, 0.045)), 'analog2': ((dx + 0.09, 0.05), (dx - 0.09, 0.05)),
                 'classic': ((dx + 0.1, 0.048), (dx - 0.1, 0.048)), 'flat': ((dx + 0.09, 0.045), (dx - 0.09, 0.045))}.get(style, ((dx, 0.05),))
        for cx, r in dials:
            d = np.hypot(x - cx, y - cy)
            a = np.arctan2(y - cy, x - cx)
            face_col = (236, 230, 210) if style == 'classic' else (12, 12, 14)
            out[d < r, :3] = face_col
            out[(d > r * 0.92) & (d < r), :3] = (150, 152, 158)
            tick = (d > r * 0.72) & (d < r * 0.88) & (np.mod((a + math.pi) * 24 / (2 * math.pi), 1.0) < 0.25) & ((a > -math.pi * 0.25) | (a < -math.pi * 0.75))
            out[tick, :3] = (30, 30, 30) if style == 'classic' else (230, 230, 230)
            out[d < r * 0.08, :3] = (200, 60, 30)
        return out
    return f


def emblem_painter(shape, txt, col, cx, cy, w, h, sgn):
    def f(face, P, n):
        out = fill(P, (206, 210, 216))
        uv = np.stack([sgn * (P[..., 0] - cx), -(P[..., 1] - cy)], -1)
        rx, ry_ = np.abs(uv[..., 0]) / (w / 2), np.abs(uv[..., 1]) / (h / 2)
        inner = (rx < 0.78) & (ry_ < 0.78) if shape != 'circle' and shape != 'oval' else (rx ** 2 + ry_ ** 2 < 0.62)
        out[inner, :3] = col
        th = h * 0.55
        tw = len(txt) * th / 7 * 6
        out[S.text_mask(uv, txt, -tw / 2, -th / 2, th), :3] = (236, 238, 240) if sum(col) < 600 else (30, 30, 34)
        return out
    return f


def garnish_painter(text, ty, bg=(60, 12, 18), fg=(200, 200, 205), th=0.05):
    def f(face, P, n):
        out = fill(P, bg)
        if text:
            uv = np.stack([-P[..., 0], -P[..., 1]], -1)
            h = th
            wtxt = len(text) * h / 7 * 6
            out[S.text_mask(uv, text, -wtxt / 2, -(ty + h / 2), h), :3] = fg
        return out
    return f


def _proj(kind):
    def f(face, P, n):
        c = np.mean(np.array(face.verts), axis=0)
        d = np.linalg.norm(P - c, axis=-1)
        r = max(np.linalg.norm(v - c) for v in face.verts) if len(face.verts) > 4 else 0.05
        rn = d / max(r, 1e-3)
        if kind == 'amber':
            out = fill(P, (255, 160, 50))
            out[rn < 0.5, :3] = (255, 200, 120)
        elif kind == 'sealed':
            out = fill(P, (226, 230, 232))
            q = np.floor(P * 140).astype(int)
            out[(q[..., 0] + q[..., 1] + q[..., 2]) % 3 == 0, :3] = (246, 248, 250)
            out[rn < 0.25, :3] = (255, 255, 248)
        else:
            out = fill(P, (236, 240, 244))
            out[(rn > 0.55) & (rn < 0.7), :3] = (180, 186, 194)
            out[rn < 0.3, :3] = (255, 255, 250)
        return out
    return f


def _lens(face, P, n):
    out = fill(P, (236, 242, 246))
    q = P[..., 0] * 0.7 + P[..., 2]
    out[np.mod(q * 9, 1.0) < 0.08, :3] = (255, 255, 255)
    return out


def _tail_ring(face, P, n):
    c = np.mean(np.array(face.verts), axis=0)
    d = np.linalg.norm(P - c, axis=-1)
    r = max(np.linalg.norm(v - c) for v in face.verts)
    rn = d / max(r, 1e-6)
    out = fill(P, (176, 14, 24))
    a = np.arctan2(P[..., 1] - c[1], P[..., 0] - c[0])
    out[np.mod(a * 12 / math.pi, 1.0) < 0.35, :3] = (150, 10, 20)
    out[(rn > 0.5) & (rn < 0.6), :3] = (210, 40, 50)
    out[rn > 0.9, :3] = (120, 8, 16)
    return out


def _tail_house(face, P, n):
    out = fill(P, (64, 12, 18))
    out[np.mod(P[..., 1] * 140, 1.0) < 0.3, :3] = (78, 16, 22)
    return out


def _red_rect(face, P, n):
    out = fill(P, (180, 16, 26))
    out[np.mod(P[..., 1] * 90, 1.0) < 0.3, :3] = (150, 10, 20)
    out[np.mod(P[..., 0] * 30, 1.0) < 0.12, :3] = (205, 40, 50)
    return out


def _stack(face, P, n):
    out = fill(P, (30, 30, 33))
    y = P[..., 1]
    c = np.mean(y)
    out[(y > c + 0.07) & (y < c + 0.12), :3] = (18, 18, 20)
    out[(y > c + 0.08) & (y < c + 0.11) & (np.mod(P[..., 0] * 60, 1.0) < 0.4), :3] = (60, 140, 90)
    out[(y > c - 0.1) & (y < c - 0.02) & (np.mod(P[..., 0] * 22, 1.0) < 0.5), :3] = (70, 70, 74)
    return out


def _screen(face, P, n):
    out = fill(P, (14, 22, 34))
    y = P[..., 1]
    c = np.mean(y)
    out[(y > c + 0.02) & (y < c + 0.1), :3] = (30, 70, 120)
    out[(np.abs(y - c + 0.04) < 0.01), :3] = (80, 160, 220)
    return out


def _door_card(face, P, n):
    out = fill(P, (52, 52, 56))
    y = P[..., 1]
    c = np.mean(y)
    out[(y > c - 0.08) & (y < c + 0.06), :3] = (40, 40, 43)
    out[np.abs(y - c - 0.14) < 0.006, :3] = (70, 70, 74)
    return out


LAMP_PAINTERS = {
    'lens': _lens, 'proj_head': _proj('head'), 'proj_high': _proj('high'), 'proj_amber': _proj('amber'), 'sealed': _proj('sealed'),
    'tail_ring': _tail_ring, 'tail_house': _tail_house, 'red_rect': _red_rect, 'stack_face': _stack, 'stack_screen': _screen, 'door_card': _door_card,
    'fog_lens': S.clear_lens, 'head_bezel': S.flat((40, 42, 46), 3), 'tail_bezel': S.flat((40, 10, 14), 3), 'brake_core': S.flat((210, 22, 34), 3),
    'rev_core': S.flat((236, 236, 236), 3), 'amber_dark': S.flat((230, 120, 30), 3), 'carbon_hood': S.carbon_untinted, 'vent_slats': S.flat((22, 22, 24), 2),
    'towhook': S.flat((210, 30, 30), 3), 'seat_fabric': S.fabric, 'led_white': S.flat((250, 252, 255), 1), 'proj_head_r': S.clear_lens,
    'proj_high_r': S.clear_lens, 'proj_amber_r': S.amber, 'led_white_r': S.flat((250, 252, 255), 1), 'sealed_r': _proj('sealed'),
    'amber': S.amber, 'smoke_red': S.red_lens((70, 12, 18)), 'red_lens': S.red_lens(), 'clear_red': S.red_lens((200, 30, 40)),
    'tail_ring_r': _red_rect, 'brake_core_r': _red_rect, 'rev_core_r': S.flat((236, 236, 236), 3),
}

DIALS = {
    'analog3': [dict(kind='rpm', cx=0.5, cy=0.5, r=0.28, a0=225, a1=-45, max=9000, color=0xFF5A1A), dict(kind='speed', cx=0.18, cy=0.5, r=0.22, a0=225, a1=-45, max=180, color=0xFF5A1A),
                dict(kind='fuel', cx=0.82, cy=0.5, r=0.2, a0=200, a1=-20, max=1, color=0xFF5A1A)],
    'analog2': [dict(kind='rpm', cx=0.27, cy=0.5, r=0.24, a0=225, a1=-45, max=8000, color=0xFF3020), dict(kind='speed', cx=0.73, cy=0.5, r=0.24, a0=225, a1=-45, max=240, color=0xFF3020)],
    'classic': [dict(kind='speed', cx=0.24, cy=0.5, r=0.24, a0=225, a1=-45, max=200, color=0x202020), dict(kind='rpm', cx=0.76, cy=0.5, r=0.24, a0=225, a1=-45, max=7000, color=0x202020)],
    'digital': [dict(kind='speed', cx=0.21, cy=0.5, r=0.22, a0=225, a1=-45, max=260, color=0x30A0FF), dict(kind='rpm', cx=0.79, cy=0.5, r=0.22, a0=225, a1=-45, max=8000, color=0x30A0FF)],
    'flat': [dict(kind='speed', cx=0.27, cy=0.5, r=0.22, a0=225, a1=-45, max=180, color=0xFF3020), dict(kind='rpm', cx=0.73, cy=0.5, r=0.22, a0=225, a1=-45, max=7000, color=0xFF3020)],
}


def build(spec, out_dir):
    c = Car(spec)
    return c.run(out_dir)
