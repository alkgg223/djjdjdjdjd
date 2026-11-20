"""Vehicle customisation: per-vehicle catalogues of swappable parts built onto the vehicle's own surface.

Every option becomes its own mesh part named '<slot>:<index>' (wheels: 'wheel:<index>'); the game draws the selected
index of each slot. Option 0 of every slot is the factory part. Parts are placed with rays against the vehicle's body
(conformal decals, ledges that follow the bumper contour, wings standing on the boot lid), so they fit each body
shape instead of floating.

Slots:
  fb front bumper, rb rear bumper, ex exhaust, sp spoiler/wing, hd hood/bonnet, wh wheels,
  sk side skirts, wb widebody / fender flares, rf roof accessory.
"""
import math
import numpy as np
from geom import *
from body import shp_rrect, shp_ellipse, shp_trap, scale_shape
import parts as P

SLOTS = ['fb', 'rb', 'ex', 'sp', 'hd', 'wh', 'sk', 'wb', 'rf']
SLOT_NAMES = {'fb': 'Front bumper', 'rb': 'Rear bumper', 'ex': 'Exhaust', 'sp': 'Spoiler / wing', 'hd': 'Hood', 'wh': 'Wheels',
              'sk': 'Side skirts', 'wb': 'Widebody', 'rf': 'Roof'}
CHROME = rgb('#d4d8de')
BLACK = rgb('#121315')
CARBON_UV = (0 / 256, 224 / 256, 64 / 256, 256 / 256)
FINISH = {'chrome': '#d6dade', 'black': '#18191b', 'titanium': '#8c7fa6', 'carbon': '#26272a', 'polished': '#c3c7cc', 'bronze': '#8c6a3a'}


def colof(c):
    if c is None:
        return (255, 255, 255, 255)
    return rgb(c) if isinstance(c, str) else c


def matof(c):
    """'paint' -> PAINT (follows the body colour / livery), 'carbon' -> carbon texture, else SOLID colour."""
    if c in (None, 'paint'):
        return PAINT, (255, 255, 255, 255), None
    if c == 'carbon':
        return SOLID, rgb('#ffffff'), CARBON_UV
    return SOLID, colof(c), None


def shape_of(sd):
    from vehicle import shape_of as so
    return so(sd)


class Parts:
    def __init__(self, b):
        self.b = b
        self.B = b.B
        self.m = b.mesh
        self.S = b.S
        self.L = b.L
        self.hw = b.hw
        self.zoff = b.zoff
        self.lod = b.lod

    # ------------------------------------------------------------------ geometry helpers
    def hit(self, side, x, y, z=None):
        o, d = self.b.ray(side, x, y, z)
        h = self.B.project(o, d)
        return None if h is None else (h, self.B.surf_normal(h))

    def front_contour(self, y, w, n=15, side='front'):
        """Points on the front (or rear) surface at height y across +-w."""
        pts = []
        for x in np.linspace(-w, w, n):
            h = self.hit(side, x, y)
            if h is not None:
                pts.append(h)
        return pts

    def ledge(self, part, pts, out, ext, thick, col, mat=SOLID, uvr=None, droop=0.0):
        """A plate following a contour (lip / splitter / skirt): inner edge on the body, outer edge pushed along 'out'."""
        if len(pts) < 2:
            return
        out = normalize(np.array(out, float))
        up = v3(0, 1, 0)
        inner = [p for p, n in pts]
        outer = [p + out * ext - up * droop for p, n in pts]
        tops = [(a + up * thick / 2, b + up * thick / 2) for a, b in zip(inner, outer)]
        bots = [(a - up * thick / 2, b - up * thick / 2) for a, b in zip(inner, outer)]
        c = sum(inner) / len(inner) - out * 0.3
        for i in range(len(inner) - 1):
            a0, b0 = tops[i]
            a1, b1 = tops[i + 1]
            self._oq(part, mat, a0, a1, b1, b0, col, c - up * 1.0, uvr)
            a0, b0 = bots[i]
            a1, b1 = bots[i + 1]
            self._oq(part, mat, a0, a1, b1, b0, col, c + up * 1.0, uvr)
            self._oq(part, mat, tops[i][1], tops[i + 1][1], bots[i + 1][1], bots[i][1], col, c, uvr)
        for i in (0, len(inner) - 1):
            self._oq(part, mat, tops[i][0], tops[i][1], bots[i][1], bots[i][0], col, c + v3(0, 0, 0) - (outer[i] - c) * 0.0 +
                     (v3(-1, 0, 0) if outer[i][0] > 0 else v3(1, 0, 0)) * 3, uvr)

    def _oq(self, part, mat, a, b, c, d, col, inside, uvr=None):
        n = np.cross(b - a, c - a)
        cen = (a + b + c + d) / 4
        if np.dot(n, cen - inside) < 0:
            a, b, c, d = d, c, b, a
        self.m.quad(part, mat, a, b, c, d, col, uvr=uvr, smooth=False)

    def plate(self, part, center, size, rot, col, mat=SOLID, r=0.006, uvr=None):
        rbox(self.m, part, mat, tuple(center), size, r, col, rot=rot, uvr=uvr)

    def top_y(self, x, z):
        """Surface height of the top (boot lid / hood / roof) at body coords (x, z from rear)."""
        o, d = self.b.ray('top', x, 0, z)
        h = self.B.project(o, d)
        return None if h is None else h[1]

    # ------------------------------------------------------------------ FRONT BUMPER
    def front_bumper(self, part, o):
        b = self.b
        if 'lower' in o:
            g = o['lower']
            r = b.ray('front', 0, g['y'])
            sh = shape_of(g['shape'])
            b.dec(part, SOLID, r, scale_shape(sh, 1.0, 0.012), g.get('frame', '#0d0e10'), offset=0.004, depth=0.006)
            b.dec(part, SOLID, r, sh, '#ffffff', offset=0.014, uvr=UV[g.get('uv', 'grille_fine')])
            for k in range(g.get('slats', 0)):
                yy = g['y'] + (k - (g['slats'] - 1) / 2) * g['shape'][2] / (g['slats'] + 1)
                rs = b.ray('front', 0, yy)
                smat, scol = self._mc(g.get('slat_col', '#1b1c1e'))
                b.dec(part, smat, rs, shp_rrect(g['shape'][1] * 0.95, 0.012, 0.005), scol, offset=0.02, depth=0.006)
        for si in o.get('intakes', []):
            for x in (si['x'], -si['x']) if si.get('mirror', True) else (si['x'],):
                sh = shape_of(si['shape'])
                sh = sh if x > 0 else [(-u, v) for (u, v) in reversed(sh)]
                r = b.ray(si.get('side', 'front'), x, si['y'])
                fr = b.dec(part, SOLID, r, scale_shape(sh, 1.0, 0.01), si.get('frame', '#0c0d0f'), offset=0.004, depth=si.get('depth', 0.006))
                b.dec(part, SOLID, r, sh, '#ffffff', offset=0.014, uvr=UV[si.get('uv', 'grille_fine')])
                if fr and si.get('fog'):
                    rs = b.sub(fr, 0, 0)
                    b.dec(part, SOLID, rs, shp_ellipse(si['fog'] * 2.5, si['fog'] * 2.5, 14), CHROME, offset=0.018)
                    b.dec(part, HEAD, rs, shp_ellipse(si['fog'] * 2, si['fog'] * 2, 14), '#ffffff', offset=0.022, dome=0.006, uvr=UV['headlamp'])
                if fr and si.get('drl'):
                    rs = b.sub(fr, 0, si['shape'][2] * 0.3 if len(si['shape']) > 2 else 0)
                    b.dec(part, DRL, rs, shp_rrect(si['shape'][1] * 0.8, 0.016, 0.006), '#ffffff', offset=0.02, uvr=UV['led'])
        if 'splitter' in o:
            s = o['splitter']
            pts = self.front_contour(s['y'], s['w'], 17)
            mat, col, uvr = matof(s.get('col', '#0f1012'))
            self.ledge(part, pts, (0, 0, 1), s.get('ext', 0.06), s.get('thick', 0.018), col, mat, uvr, droop=s.get('droop', 0.0))
            if s.get('struts'):
                for x in (s['w'] * 0.6, -s['w'] * 0.6):
                    h = self.hit('front', x, s['y'] + 0.08)
                    if h:
                        p, n = h
                        bar(self.m, part, SOLID, p, p + v3(0, -0.08, s.get('ext', 0.06) * 0.7), 0.008, 0.008, BLACK)
        if 'lip' in o:
            s = o['lip']
            pts = self.front_contour(s['y'], s['w'], 17)
            mat, col, uvr = matof(s.get('col', 'paint'))
            self.ledge(part, pts, (0, -0.35, 1), s.get('ext', 0.035), s.get('thick', 0.025), col, mat, uvr)
        for c in o.get('canards', []):
            for sx in (1, -1):
                h = self.hit('diag', sx * c['x'], c['y'])
                if not h:
                    continue
                p, n = h
                for k in range(c.get('n', 1)):
                    q = p + v3(0, -k * 0.07, 0) + n * 0.04
                    rot = np.array(rot_y(sx * 0.6)) @ np.array(rot_z(sx * 0.18))
                    mat, col, uvr = matof(c.get('col', 'carbon'))
                    rbox(self.m, part, mat, tuple(q), (0.16, 0.008, 0.07), 0.003, col, rot=rot, uvr=uvr)
        for v in o.get('vents', []):
            for x in (v['x'], -v['x']):
                r = b.ray(v.get('side', 'front'), x, v['y'], v.get('z'))
                b.dec(part, SOLID, r, shape_of(v['shape']), v.get('col', '#08090a'), offset=0.006, depth=v.get('depth', 0.0))
                for k in range(v.get('slats', 0)):
                    rs = b.ray(v.get('side', 'front'), x, v['y'] + (k - (v['slats'] - 1) / 2) * 0.022, v.get('z'))
                    b.dec(part, SOLID, rs, shp_rrect(v['shape'][1] * 0.9, 0.007, 0.003), '#2c2e31', offset=0.014)
        for t in o.get('drl', []):
            for x in (t['x'], -t['x']):
                r = b.ray('front', x, t['y'])
                b.dec(part, DRL, r, shp_rrect(t['w'], t.get('h', 0.016), 0.006), '#ffffff', offset=0.016, uvr=UV['led'])
        if 'tow' in o:
            x, y = o['tow']
            h = self.hit('front', x, y)
            if h:
                p, n = h
                rbox(self.m, part, SOLID, tuple(p + n * 0.02), (0.04, 0.012, 0.06), 0.004, rgb('#d81e1e'))
        if 'plate_off' in o:
            pass  # plate positions stay on the base body

    def _mc(self, c):
        mat, col, uvr = matof(c)
        return mat, col

    # ------------------------------------------------------------------ REAR BUMPER
    def rear_bumper(self, part, o):
        b = self.b
        if 'diffuser' in o:
            d = o['diffuser']
            r = b.ray('rear', 0, d['y'])
            mat, col, uvr = matof(d.get('col', '#121315'))
            b.dec(part, mat, r, shp_rrect(d['w'] * 2, d['h'], 0.03), col, offset=0.005, depth=0.01, uvr=uvr)
            nf = d.get('fins', 5)
            for k in range(nf):
                xx = (k - (nf - 1) / 2) * d['w'] * 2 / max(1, nf)
                h = self.hit('rear', xx, d['y'])
                if not h:
                    continue
                p, n = h
                ext = d.get('ext', 0.05)
                bar(self.m, part, SOLID, p + v3(0, d['h'] * 0.45, 0), p + n * ext + v3(0, -d['h'] * 0.45, 0), 0.008, 0.01, rgb('#26282b'),
                    up=(1, 0, 0))
            if d.get('plate'):
                pts = self.front_contour(d['y'] - d['h'] / 2, d['w'], 15, side='rear')
                self.ledge(part, pts, (0, 0, -1), d.get('ext', 0.05) * 0.8, 0.012, col, mat, uvr)
        if 'valance' in o:
            v = o['valance']
            pts = self.front_contour(v['y'], v['w'], 15, side='rear')
            mat, col, uvr = matof(v.get('col', 'paint'))
            self.ledge(part, pts, (0, -0.3, -1), v.get('ext', 0.03), v.get('thick', 0.03), col, mat, uvr)
        for rf in o.get('reflectors', []):
            for x in (rf[0], -rf[0]):
                r = b.ray('rear', x, rf[1])
                b.dec(part, TAIL, r, shp_rrect(rf[2], rf[3] if len(rf) > 3 else 0.024, 0.008), '#9c0f16', offset=0.008, uvr=UV['reflector'])
        for v in o.get('vents', []):
            for x in (v['x'], -v['x']):
                r = b.ray(v.get('side', 'rear'), x, v['y'], v.get('z'))
                b.dec(part, SOLID, r, shape_of(v['shape']), v.get('col', '#08090a'), offset=0.006, depth=v.get('depth', 0.004))
                for k in range(v.get('slats', 0)):
                    rs = b.ray(v.get('side', 'rear'), x, v['y'] + (k - (v['slats'] - 1) / 2) * 0.022, v.get('z'))
                    b.dec(part, SOLID, rs, shp_rrect(v['shape'][1] * 0.9, 0.007, 0.003), '#2c2e31', offset=0.014)
        if 'f1' in o:
            y = o['f1']
            r = b.ray('rear', 0, y)
            b.dec(part, SOLID, r, shp_rrect(0.11, 0.05, 0.01), '#0b0b0c', offset=0.006, depth=0.008)
            b.dec(part, BRAKE, r, shp_rrect(0.09, 0.032, 0.008), '#e0101c', offset=0.018, uvr=UV['led_tail'])
        for c in o.get('cutouts', []):
            for x in (c['x'], -c['x']) if c.get('mirror', True) else (c['x'],):
                r = b.ray('rear', x, c['y'])
                b.dec(part, SOLID, r, shape_of(c['shape']), '#0a0a0b', offset=0.005, depth=0.004)

    # ------------------------------------------------------------------ EXHAUST
    def exhaust(self, part, o, meta_tips):
        for t in o.get('tips', []):
            xs = (t['x'], -t['x']) if t.get('mirror', True) and abs(t['x']) > 1e-3 else (t['x'],)
            for x in xs:
                h = self.hit('rear', x, t['y'])
                if not h:
                    continue
                p, n = h
                L = t.get('len', 0.05)
                fin = colof(FINISH.get(t.get('finish', 'chrome'), t.get('finish', 'chrome')))
                w, hh = t.get('w', 0.09), t.get('h', t.get('w', 0.09))
                shape = t.get('shape', 'round')
                back = v3(0, 0, -1)
                p0 = p + back * -0.04
                p1 = p + back * L
                if shape == 'round':
                    cylinder(self.m, part, SOLID, p0, p1, w / 2, 8 if self.lod else 16, fin, caps=False)
                    disc(self.m, part, SOLID, p1 + back * -0.004, back, w / 2 * 0.8, 8 if self.lod else 16, rgb('#050505'))
                    if t.get('rolled'):
                        torus(self.m, part, SOLID, p1, back, w / 2 * 0.98, 0.006, 16, 4, fin)
                else:
                    r = min(w, hh) * (0.48 if shape == 'oval' else 0.12)
                    c = (p0 + p1) / 2
                    rbox(self.m, part, SOLID, tuple(c), (w, hh, float(np.linalg.norm(p1 - p0))), r, fin)
                    rbox(self.m, part, SOLID, tuple(p1 + back * 0.002), (w * 0.82, hh * 0.7, 0.004), r * 0.8, rgb('#050505'))
                if t.get('double'):
                    off = v3(math.copysign(w * 1.1, x if x else 1), 0, 0)
                    cylinder(self.m, part, SOLID, p0 + off, p1 + off, w / 2, 8 if self.lod else 16, fin, caps=False)
                    disc(self.m, part, SOLID, p1 + off + back * -0.004, back, w / 2 * 0.8, 8 if self.lod else 16, rgb('#050505'))
                    meta_tips.append([float(c) for c in (p1 + off)] + [0.0, 0.0, -1.0])
                # dark cut-out in the bumper round the tip
                self.b.dec(part, SOLID, (p + n * 0.3, -n), shp_rrect(w * 1.35 * (2.1 if t.get('double') else 1), hh * 1.35, min(w, hh) * 0.5),
                           '#09090a', offset=0.004)
                meta_tips.append([float(c) for c in p1] + [0.0, 0.0, -1.0])

    # ------------------------------------------------------------------ SPOILER / WING
    def spoiler(self, part, o):
        zoff = self.zoff
        if 'lip' in o:
            s = o['lip']
            mat, col, uvr = matof(s.get('col', 'paint'))
            r = self.b.ray('top', 0, 0, s['z'])
            self.b.dec(part, mat, r, shp_rrect(s['w'], s.get('d', 0.08), 0.03), col, offset=0.004, depth=s.get('h', 0.02), updir=(0, 0, 1),
                       uvr=uvr)
        if 'ducktail' in o:
            s = o['ducktail']
            mat, col, uvr = matof(s.get('col', 'paint'))
            n = 9
            xs = np.linspace(-s['w'] / 2, s['w'] / 2, n)
            rows = []
            for zz in (s['z'] + s.get('d', 0.25), s['z']):
                row = []
                for x in xs:
                    y = self.top_y(x, zz)
                    if y is None:
                        y = self.top_y(0, zz) or 1.0
                    row.append(v3(x, y + 0.004, zz + zoff))
                rows.append(row)
            rise = s.get('rise', 0.06)
            lip = [p + v3(0, rise, -0.03) for p in rows[1]]
            c = v3(0, 0, s['z'] + zoff)
            for i in range(n - 1):
                self._oq(part, mat, rows[0][i], rows[0][i + 1], lip[i + 1], lip[i], col, c - v3(0, 1, 0), uvr)
                self._oq(part, mat, rows[1][i], rows[1][i + 1], lip[i + 1], lip[i], col, c + v3(0, 0.0, 0.5), uvr)
            for i in (0, n - 1):
                self._oq(part, mat, rows[0][i], rows[1][i], lip[i], lip[i], col, c, uvr)
        if 'wing' in o:
            self.wing(part, o['wing'])
        if 'roof' in o:
            s = o['roof']
            G = self.S.get('gh', {})
            roof = pchip(G['roof']) if G else None
            z = s['z']
            y = (roof(z) if roof else self.S['H']) + 0.005
            mat, col, uvr = matof(s.get('col', 'paint'))
            ang = s.get('angle', 0.25)
            rbox(self.m, part, mat, (0, y - s.get('drop', 0.0), z + zoff - s.get('d', 0.18) / 2), (s['w'], 0.025, s.get('d', 0.18)), 0.012, col,
                 rot=rot_x(ang), uvr=uvr)
            for sx in (1, -1):
                rbox(self.m, part, mat, (sx * s['w'] / 2, y - 0.03, z + zoff - s.get('d', 0.18) / 2), (0.02, 0.07, s.get('d', 0.18)), 0.008, col,
                     uvr=uvr)

    def wing(self, part, s):
        zoff = self.zoff
        z = s['z']
        span, chord = s['span'], s.get('chord', 0.24)
        mat, col, uvr = matof(s.get('col', 'paint'))
        ys = [self.top_y(sx, z) for sx in s.get('stands', [0.45, -0.45])]
        base_y = max([y for y in ys if y is not None] + [self.top_y(0, z) or 1.0])
        y = base_y + s.get('h', 0.14)
        ang = s.get('angle', 0.12)
        # main plane (slightly cambered: two stacked plates)
        rbox(self.m, part, mat, (0, y, z + zoff), (span, s.get('thick', 0.022), chord), 0.01, col, rot=rot_x(ang), uvr=uvr)
        if s.get('gurney'):
            rbox(self.m, part, SOLID, (0, y + 0.02, z + zoff - chord / 2 + 0.01), (span * 0.98, 0.03, 0.008), 0.002, BLACK)
        if s.get('dual'):
            rbox(self.m, part, mat, (0, y + 0.06, z + zoff - chord * 0.35), (span * 0.98, 0.018, chord * 0.55), 0.008, col, rot=rot_x(ang + 0.25),
                 uvr=uvr)
        # end plates
        ep = s.get('endplate', (0.16, 0.16))
        if ep:
            for sx in (1, -1):
                rbox(self.m, part, mat, (sx * (span / 2 + 0.006), y - 0.01, z + zoff), (0.012, ep[1], max(chord, ep[0])), 0.008, col, uvr=uvr)
        # stands from the boot lid
        style = s.get('style', 'post')
        for sx, yb in zip(s.get('stands', [0.45, -0.45]), ys):
            if yb is None:
                continue
            bot = v3(sx, yb, z + zoff - 0.02)
            if style == 'swan':
                top = v3(sx, y + 0.03, z + zoff + chord * 0.15)
                bar(self.m, part, SOLID, bot, bot + v3(0, (y - yb) * 0.5, -0.12), 0.016, 0.06, colof(s.get('stand_col', '#151618')), up=(1, 0, 0))
                bar(self.m, part, SOLID, bot + v3(0, (y - yb) * 0.5, -0.12), top, 0.016, 0.05, colof(s.get('stand_col', '#151618')), up=(1, 0, 0))
            elif style == 'hoop':
                top = v3(math.copysign(span / 2, sx), y - 0.005, z + zoff)
                bar(self.m, part, mat, bot + v3(0, 0, 0.02), top, 0.03, 0.09, col, up=(0, 0, 1))
            else:
                top = v3(sx, y - 0.01, z + zoff)
                bar(self.m, part, mat if style == 'body' else SOLID, bot, top, 0.02, 0.07, col if style == 'body' else colof(s.get('stand_col', '#151618')),
                    up=(1, 0, 0))
        if s.get('brake_light'):
            box(self.m, part, BRAKE, (0, y + 0.015, z + zoff - chord / 2 - 0.004), (span * 0.4, 0.012, 0.006), rgb('#ff2020'))

    # ------------------------------------------------------------------ HOOD
    def hood(self, part, o):
        b = self.b
        if 'carbon' in o:
            c = o['carbon']
            r = b.ray('top', 0, 0, (c['z0'] + c['z1']) / 2)
            b.dec(part, SOLID, r, shp_rrect(c['w'], c['z1'] - c['z0'], 0.06), '#ffffff', offset=0.005, uvr=CARBON_UV, updir=(0, 0, 1))
        if 'bulge' in o:
            g = o['bulge']
            r = b.ray('top', 0, 0, g['z'])
            mat, col, uvr = matof(g.get('col', 'paint'))
            b.dec(part, mat, r, shp_rrect(g['w'], g['l'], g.get('r', 0.08)), col, offset=0.004, dome=g.get('h', 0.035), updir=(0, 0, 1), uvr=uvr)
        if 'scoop' in o:
            g = o['scoop']
            r = b.ray('top', g.get('x', 0), 0, g['z'])
            mat, col, uvr = matof(g.get('col', 'paint'))
            fr = b.dec(part, mat, r, shp_trap(g['w'] * 0.85, g['w'], g['l']), col, offset=0.004, depth=g.get('h', 0.06), dome=g.get('dome', 0.01),
                       updir=(0, 0, 1), side_col=colof(g.get('side_col', None)) if g.get('side_col') else None, uvr=uvr)
            if fr:
                hit, n, u, v = fr
                mouth = hit + v * (g['l'] / 2 - 0.004) + n * (g.get('h', 0.06) * 0.55)
                rbox(self.m, part, SOLID, tuple(mouth), (g['w'] * 0.7, g.get('h', 0.06) * 0.6, 0.012), 0.008, rgb('#060606'),
                     rot=self._frame_rot(n, v))
        for v in o.get('vents', []):
            for x in (v['x'], -v['x']) if v.get('mirror', True) else (v['x'],):
                r = b.ray('top', x, 0, v['z'])
                fr = b.dec(part, SOLID, r, shp_rrect(v['w'], v['l'], v.get('r', 0.02)), v.get('col', '#08090a'), offset=0.005, updir=(0, 0, 1))
                if fr:
                    for k in range(v.get('slats', 5)):
                        rs = b.ray('top', x, 0, v['z'] + (k - (v.get('slats', 5) - 1) / 2) * v['l'] / (v.get('slats', 5) + 0.5))
                        b.dec(part, SOLID, rs, shp_rrect(v['w'] * 0.92, 0.012, 0.004), v.get('slat_col', '#2a2c2f'), offset=0.012, depth=0.008,
                              updir=(0, 0, 1))
        for v in o.get('naca', []):
            for x in (v['x'], -v['x']):
                r = b.ray('top', x, 0, v['z'])
                b.dec(part, SOLID, r, shp_trap(v['w'] * 0.4, v['w'], v['l']), '#060607', offset=0.005, updir=(0, 0, 1))
        for (x, z) in o.get('pins', []):
            for xx in (x, -x):
                r = b.ray('top', xx, 0, z)
                b.dec(part, SOLID, r, shp_ellipse(0.04, 0.04, 10), CHROME, offset=0.008, depth=0.006)
        if 'shaker' in o:
            g = o['shaker']
            y = self.top_y(0, g['z'])
            if y is not None:
                c = v3(0, y + g.get('h', 0.1) / 2, g['z'] + self.zoff)
                rbox(self.m, part, SOLID, tuple(c), (g['w'], g.get('h', 0.1), g['l']), 0.03, colof(g.get('col', '#111214')))
                rbox(self.m, part, SOLID, tuple(c + v3(0, 0.0, g['l'] / 2 + 0.002)), (g['w'] * 0.8, g.get('h', 0.1) * 0.6, 0.006), 0.01, rgb('#040404'))
        for st in o.get('stripes', []):
            # raised body-colour character lines
            for x in (st['x'], -st['x']):
                path = [v3(x, self.S['H'] + 1, z + self.zoff) for z in np.linspace(st['z0'], st['z1'], 14)]
                self.B.strip(part, PAINT, path, (0, -1, 0), st.get('w', 0.03), (255, 255, 255, 255), offset=0.004, depth=0.006)

    def _frame_rot(self, n, v):
        n = normalize(n)
        v = normalize(v)
        x = normalize(np.cross(v, n))
        return np.array([x, n, v]).T

    # ------------------------------------------------------------------ SIDE SKIRTS / WIDEBODY / ROOF
    def skirts(self, part, o):
        if 'skirt' not in o:
            return
        s = o['skirt']
        mat, col, uvr = matof(s.get('col', 'paint'))
        for sx in (1, -1):
            pts = []
            for z in np.linspace(s['z0'], s['z1'], 12):
                h = self.hit('side', sx * 1.0, s['y'], z)
                if h:
                    pts.append(h)
            self.ledge(part, pts, (sx, -0.2, 0), s.get('ext', 0.04), s.get('thick', 0.05), col, mat, uvr)

    def widebody(self, part, o):
        if 'flares' not in o:
            return
        f = o['flares']
        mat, col, uvr = matof(f.get('col', 'paint'))
        R = self.B.Ra
        for za in self.S['axles']:
            for sx in (1, -1):
                pts = []
                for a in np.linspace(math.radians(8), math.radians(172), 13):
                    z = za + math.cos(a) * (R + f.get('w', 0.06) / 2)
                    y = self.S['R'] + math.sin(a) * (R + f.get('w', 0.06) / 2)
                    h = self.hit('side', sx * 1.0, y, z)
                    if h:
                        pts.append(h)
                if len(pts) < 2:
                    continue
                inner = [p for p, n in pts]
                outer = [p + n * f.get('out', 0.06) for p, n in pts]
                rad = []
                for p in pts:
                    q = p[0]
                    d = normalize(v3(0, q[1] - self.S['R'], q[2] - (za + self.zoff)))
                    rad.append(d * f.get('w', 0.06) / 2)
                c = v3(sx * (self.hw - 0.5), self.S['R'], za + self.zoff)
                for i in range(len(pts) - 1):
                    a0, a1 = outer[i], outer[i + 1]
                    self._oq(part, mat, a0 + rad[i], a1 + rad[i + 1], a1 - rad[i + 1], a0 - rad[i], col, c, uvr)
                    self._oq(part, mat, inner[i] + rad[i], inner[i + 1] + rad[i + 1], a1 + rad[i + 1], a0 + rad[i], col, c - v3(0, 2, 0), uvr)
                    self._oq(part, mat, inner[i] - rad[i], inner[i + 1] - rad[i + 1], a1 - rad[i + 1], a0 - rad[i], col, c + v3(0, 2, 0), uvr)
                if f.get('rivets') and not self.lod:
                    for i in range(1, len(pts) - 1, 2):
                        disc(self.m, part, SOLID, outer[i] + pts[i][1] * 0.002, pts[i][1], 0.008, 6, CHROME)

    def roof(self, part, o):
        G = self.S.get('gh', {})
        if not G:
            return
        roof = pchip(G['roof'])
        z0, z1 = G['flat']
        zm = (z0 + z1) / 2
        y = roof(zm) + 0.03
        if 'rack' in o:
            r = o['rack']
            P.roof_rack(self.m, part, z0 + self.zoff + 0.05, z1 + self.zoff - 0.05, y - 0.02, r.get('hw', 0.5), rails_only=r.get('rails_only', False))
        if 'lightbar' in o:
            P.lightbar(self.m, part, (0, y + 0.02, zm + self.zoff + 0.15), o['lightbar'].get('w', 1.1), 'police')
        if 'taxi' in o:
            P.taxi_sign(self.m, part, (0, y + 0.02, zm + self.zoff))
        if 'pod' in o:
            for sx in (-0.3, -0.1, 0.1, 0.3):
                rbox(self.m, part, SOLID, (sx, y + 0.05, z1 + self.zoff + 0.1), (0.14, 0.09, 0.06), 0.02, BLACK)
                disc(self.m, part, HIGH, v3(sx, y + 0.05, z1 + self.zoff + 0.131), v3(0, 0, 1), 0.035, 12, rgb('#ffffff'))
        if 'scoop' in o:
            r = self.b.ray('top', 0, 0, z1 - 0.1)
            self.b.dec(part, PAINT, r, shp_trap(0.2, 0.26, 0.24), (255, 255, 255, 255), offset=0.004, depth=0.05, updir=(0, 0, 1))
        if 'fin' in o:
            rbox(self.m, part, SOLID, (0, y + 0.03, z0 + self.zoff), (0.05, 0.05, 0.12), 0.02, BLACK)


def build_variants(b):
    """Builds every catalogue option of the spec as its own part; returns catalogue meta."""
    S = b.S
    cat = S.get('catalog')
    if not cat:
        return None
    pp = Parts(b)
    meta = {}
    tips_meta = []
    for slot in SLOTS:
        opts = cat.get(slot, [])
        if not opts:
            continue
        meta[slot] = []
        for i, o in enumerate(opts):
            part = '%s:%d' % (slot, i)
            meta[slot].append({'name': o.get('name', '%s %d' % (SLOT_NAMES[slot], i + 1)), 'tags': o.get('tags', [])})
            if slot == 'fb':
                pp.front_bumper(part, o)
            elif slot == 'rb':
                pp.rear_bumper(part, o)
            elif slot == 'ex':
                tips = []
                pp.exhaust(part, o, tips)
                tips_meta.append(tips)
            elif slot == 'sp':
                pp.spoiler(part, o)
            elif slot == 'hd':
                pp.hood(part, o)
            elif slot == 'sk':
                pp.skirts(part, o)
            elif slot == 'wb':
                pp.widebody(part, o)
            elif slot == 'rf':
                pp.roof(part, o)
            elif slot == 'wh':
                meta[slot][-1].update({'rim': o.get('rim', 'spoke5')})
    b.meta['exhaust_variants'] = tips_meta
    if tips_meta:
        b.meta['exhausts'] = tips_meta[0]
    b.meta['catalog'] = meta
    b.meta['themes'] = S.get('themes', {})
    return meta


def livery_uv(mesh, L, W, H):
    """Projects PAINT triangles into the livery tile: left side, right side, top, front, rear regions.
    Tile layout (u right, v down), normalised to [0,1]:
      v 0.000-0.333 left side  (seen from outside, front on the left)
      v 0.333-0.667 right side (front on the right)
      v 0.667-0.917 top        (front on the right, car's left side up)
      v 0.917-1.000 front (u 0-0.5, seen from the front) / rear (u 0.5-1, seen from behind)
    Each triangle uses one region (by its face normal), so graphics never stretch across a fold."""
    out = {}
    for key, tris in mesh.groups.items():
        part, mat = key
        if mat != PAINT:
            out[key] = tris
            continue
        nt = []
        for t in tris:
            a, bb, c = t[0], t[1], t[2]
            n = np.cross(bb - a, c - a)
            ax, ay, az = abs(n[0]), abs(n[1]), abs(n[2])

            def uv(p):
                zn = min(1.0, max(0.0, (p[2] + L / 2) / L))
                yn = min(1.0, max(0.0, p[1] / H))
                xn = min(0.5, max(-0.5, p[0] / W))
                if ax >= ay * 0.75 and ax >= az:
                    if n[0] > 0:  # left side
                        return (1 - zn, (1 - yn) / 3)
                    return (zn, 1 / 3 + (1 - yn) / 3)
                if ay >= az:
                    return (zn, 2 / 3 + (0.5 - xn) * 0.25)
                if n[2] > 0:
                    return ((0.5 + xn) * 0.5, 11 / 12 + (1 - yn) / 12)
                return (0.5 + (0.5 - xn) * 0.5, 11 / 12 + (1 - yn) / 12)

            nt.append((a, bb, c, uv(a), uv(bb), uv(c)) + tuple(t[6:]))
        out[key] = nt
    mesh.groups = out
