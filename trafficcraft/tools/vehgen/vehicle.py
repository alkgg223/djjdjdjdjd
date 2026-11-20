"""Assemble a complete vehicle (body, lamps, trim, interior, wheels) from a spec dict and export it."""
import math, json, struct, gzip
import numpy as np
from geom import *
from body import Body, shp_rrect, shp_ellipse, shp_slant, shp_trap, shp_wedge, scale_shape
import parts as P
import dash as DASH

LENS_CLEAR = (236, 242, 250, 70)
LENS_SMOKE = (120, 24, 30, 96)
CHROME = rgb('#d4d8de')


def shape_of(sd):
    k = sd[0]
    if k == 'rrect':
        return shp_rrect(sd[1], sd[2], sd[3] if len(sd) > 3 else 0.02)
    if k == 'ellipse':
        return shp_ellipse(sd[1], sd[2], 20)
    if k == 'slant':
        return shp_slant(sd[1], sd[2], sd[3], sd[4] if len(sd) > 4 else 0.025)
    if k == 'wedge':
        return shp_wedge(sd[1], sd[2], sd[3])
    if k == 'trap':
        return shp_trap(sd[1], sd[2], sd[3])
    if k == 'poly':
        return list(sd[1])
    raise ValueError(k)


def flip_u(shape):
    return [(-u, v) for (u, v) in reversed(shape)]


class Builder:
    def __init__(self, S, lod=0):
        self.S = S
        self.lod = lod
        self.mesh = Mesh()
        self.B = Body(S, self.mesh, lod)
        self.L = S['L']
        self.hw = S['W'] / 2
        self.zoff = -self.L / 2
        self.meta = {'id': S['id'], 'name': S['name'], 'category': S['cat'], 'length': self.L, 'width': S['W'], 'height': S['H']}
        self.beams = []
        self.report = []

    # ------------------------------------------------------------------ rays
    def ray(self, side, x, y, z=None):
        L, hw = self.L, self.hw
        if side == 'front':
            return v3(x, y, L / 2 + 1.0), v3(0, 0, -1)
        if side == 'rear':
            return v3(x, y, -L / 2 - 1.0), v3(0, 0, 1)
        if side == 'side':
            return v3(math.copysign(hw + 1.0, x), y, z + self.zoff), v3(-math.copysign(1, x), 0, 0)
        if side == 'top':
            return v3(x, self.S['H'] + 1, z + self.zoff), v3(0, -1, 0)
        if side == 'diag':
            dz = 0.45
            return v3(x + math.copysign(0.7 * dz, x), y, L / 2 + dz), normalize(v3(-math.copysign(0.7, x), 0, -1))
        if side == 'rdiag':
            dz = 0.45
            return v3(x + math.copysign(0.7 * dz, x), y, -L / 2 - dz), normalize(v3(-math.copysign(0.7, x), 0, 1))
        raise ValueError(side)

    def outer_sign(self, side, x):
        # sign that maps 'outer' (+) in shape coordinates onto the tangent u axis
        if side in ('front', 'diag'):
            return 1 if x > 0 else -1
        if side in ('rear', 'rdiag'):
            return -1 if x > 0 else 1
        return 1

    def sub(self, frame, a, b):
        """Ray (origin, dir) for a sub element at (a, b) in a lamp's tangent frame."""
        hit, n, u, v = frame
        return hit + u * a + v * b + n * 0.3, -n

    def dec(self, part, mat, ray, shape, col, **kw):
        o, d = ray
        return self.B.decal(part, mat, o, d, shape, rgb(col) if isinstance(col, str) else col, **kw)

    # ------------------------------------------------------------------ lamps
    def front_lamp(self, F):
        side = F.get('side', 'front')
        base = shape_of(F['shape'])
        style = F.get('style', 'projector')
        for x in (F['x'], -F['x']):
            sgn = self.outer_sign(side, x)
            sh = base if sgn > 0 else flip_u(base)
            ind_mat = IND_L if x > 0 else IND_R
            r = self.ray(side, x, F['y'])
            # 1) housing (deep black bezel)
            fr = self.dec('body', SOLID, r, scale_shape(sh, 1.0, 0.016), '#0b0c0e', offset=0.004, depth=0.004)
            if fr is None:
                self.report.append('front lamp miss at x=%.2f' % x)
                continue
            # 2) chrome reflector bowl (glows with the low beam)
            self.dec('body', HEAD, r, scale_shape(sh, 0.95), '#f4f6fa', offset=0.011, uvr=UV['headlamp'])
            hit = fr[0]
            self.beams.append([round(float(hit[0]), 3), round(float(hit[1]), 3), round(float(hit[2]), 3)])
            w, h = F['shape'][1], F['shape'][2]
            if self.lod:
                self.dec('body', ind_mat, self.sub(fr, sgn * w * 0.38, 0), shp_rrect(w * 0.16, h * 0.55, 0.01), '#ffae3a', offset=0.018)
                continue
            # 3) projector / LED modules
            mods = F.get('modules', [(-0.22, 0.0, HIGH), (0.18, 0.0, HEAD)]) if style in ('projector', 'led') else []
            if style == 'reflector':
                rs = self.sub(fr, -sgn * w * 0.12, 0)
                self.dec('body', HIGH, rs, shp_rrect(w * 0.42, h * 0.62, 0.012), '#ffffff', offset=0.016, uvr=UV['headlamp'])
            for (fa, fb, mm) in mods:
                rr = min(h * 0.32, 0.045)
                rs = self.sub(fr, fa * w * sgn, fb * h)
                self.dec('body', SOLID, rs, shp_ellipse(rr * 2.6, rr * 2.6, 16), CHROME, offset=0.016)
                self.dec('body', mm, rs, shp_ellipse(rr * 1.8, rr * 1.8, 16), '#ffffff', offset=0.019, dome=0.012, uvr=UV['headlamp'])
                self.dec('body', SOLID, rs, shp_ellipse(rr * 0.7, rr * 0.7, 10), '#1a1c20', offset=0.033)
            if style == 'round':
                rs = self.sub(fr, 0, 0)
                self.dec('body', HIGH, rs, shp_ellipse(w * 0.35, w * 0.35, 16), '#ffffff', offset=0.02, dome=0.01, uvr=UV['headlamp'])
            # 4) DRL light guide
            drl = F.get('drl', 'top')
            if drl == 'top':
                rs = self.sub(fr, 0.02 * sgn, h * 0.36)
                self.dec('body', DRL, rs, scale_shape(shp_slant(w * 0.92, 0.02, F['shape'][3] * 0.1 if F['shape'][0] == 'slant' else 0, 0.008), 1.0)
                         if sgn > 0 else flip_u(shp_slant(w * 0.92, 0.02, F['shape'][3] * 0.1 if F['shape'][0] == 'slant' else 0, 0.008)),
                         '#ffffff', offset=0.02, uvr=UV['led'])
            elif drl == 'L':
                rs = self.sub(fr, 0.0, -h * 0.36)
                self.dec('body', DRL, rs, shp_rrect(w * 0.9, 0.02, 0.008), '#ffffff', offset=0.02, uvr=UV['led'])
                rs = self.sub(fr, sgn * w * 0.44, 0)
                self.dec('body', DRL, rs, shp_rrect(0.02, h * 0.8, 0.008), '#ffffff', offset=0.02, uvr=UV['led'])
            elif drl == 'none':
                pass
            elif drl == 'ring':
                rs = self.sub(fr, 0, 0)
                ring_out = shp_ellipse(w * 0.92, h * 0.92, 20)
                self.dec('body', DRL, rs, ring_out, '#ffffff', offset=0.0125)
            # 5) indicator
            ia = F.get('ind', 0.38)
            if ia is not None:
                rs = self.sub(fr, sgn * w * ia, -h * 0.05)
                self.dec('body', ind_mat, rs, shp_rrect(w * 0.16, h * 0.55, 0.01), '#ffae3a', offset=0.018, uvr=UV['reflector'])
            # 6) clear outer lens with sidewalls
            self.dec('body', GLASS, r, scale_shape(sh, 1.0, 0.006), LENS_CLEAR, offset=0.026, dome=0.006)

    def rear_lamp(self, R):
        side = R.get('side', 'rear')
        base = shape_of(R['shape'])
        for x in (R['x'], -R['x']):
            sgn = self.outer_sign(side, x)
            sh = base if sgn > 0 else flip_u(base)
            ind_mat = IND_L if x > 0 else IND_R
            r = self.ray(side, x, R['y'])
            fr = self.dec('body', SOLID, r, scale_shape(sh, 1.0, 0.012), '#120607', offset=0.004, depth=0.004)
            if fr is None:
                self.report.append('rear lamp miss at x=%.2f' % x)
                continue
            w, h = R['shape'][1], R['shape'][2]
            if self.lod:
                self.dec('body', BRAKE, r, scale_shape(sh, 0.94), '#d8121c', offset=0.011)
                self.dec('body', ind_mat, self.sub(fr, sgn * w * 0.18, -h * 0.2), shp_rrect(w * 0.26, h * 0.26, 0.008), '#ffa21e', offset=0.017)
                continue
            if R.get('rounds'):
                # round lamp elements (e.g. 90s sports cars): chrome bezel + lens per element
                self.dec('body', TAIL, r, scale_shape(sh, 0.96), '#5a0a10', offset=0.009)
                for (ua, va, rr, kind) in R['rounds']:
                    mm = {'brake': BRAKE, 'tail': TAIL, 'ind': ind_mat, 'rev': REVERSE}[kind]
                    cc = {'brake': '#e0101c', 'tail': '#c0101a', 'ind': '#ffa21e', 'rev': '#f4f4f4'}[kind]
                    rs = self.sub(fr, sgn * ua * w, va * h)
                    self.dec('body', SOLID, rs, shp_ellipse(rr * 2.3, rr * 2.3, 18), '#2a2b2e', offset=0.014)
                    self.dec('body', mm, rs, shp_ellipse(rr * 2, rr * 2, 18), cc, offset=0.017, dome=0.008, uvr=UV['taillamp'])
                self.dec('body', GLASS, r, scale_shape(sh, 1.0, 0.006), LENS_SMOKE, offset=0.028, dome=0.006)
                continue
            self.dec('body', BRAKE, r, scale_shape(sh, 0.94), '#d8121c', offset=0.011, uvr=UV['taillamp'])
            # LED tail contour
            rs = self.sub(fr, 0, h * R.get('tail_v', 0.3))
            self.dec('body', TAIL, rs, shp_rrect(w * 0.86, max(0.018, h * 0.12), 0.006), '#ff3030', offset=0.017, uvr=UV['led_tail'])
            # indicator + reverse
            rs = self.sub(fr, sgn * w * R.get('ind_u', 0.18), -h * 0.2)
            self.dec('body', ind_mat, rs, shp_rrect(w * 0.26, h * 0.26, 0.008), '#ffa21e', offset=0.017, uvr=UV['reflector'])
            rs = self.sub(fr, -sgn * w * R.get('rev_u', 0.26), -h * 0.2)
            self.dec('body', REVERSE, rs, shp_rrect(w * 0.2, h * 0.26, 0.008), '#f4f4f4', offset=0.017, uvr=UV['reflector'])
            self.dec('body', GLASS, r, scale_shape(sh, 1.0, 0.006), LENS_SMOKE, offset=0.028, dome=0.006)

    # ------------------------------------------------------------------ front & rear fascia
    def fascia(self):
        S = self.S
        cat = S.get('catalog', {})
        F = dict(S.get('front', {}))
        Rr0 = dict(S.get('rear', {}))
        if cat.get('fb'):
            for k in ('lower', 'side_intakes', 'splitter'):
                F.pop(k, None)
        if cat.get('rb'):
            for k in ('diffuser', 'reflectors'):
                Rr0.pop(k, None)
        if cat.get('ex'):
            Rr0.pop('tips', None)
        if cat.get('sp'):
            Rr0.pop('lip_spoiler', None)
        B = self.B
        if 'grille' in F:
            g = F['grille']
            r = self.ray('front', 0, g['y'])
            sh = shape_of(g['shape'])
            fr = self.dec('body', SOLID, r, scale_shape(sh, 1.0, 0.018), g.get('frame', '#c9ced5'), offset=0.004, depth=0.01,
                          side_col=rgb('#2a2c30'))
            self.dec('body', SOLID, r, sh, '#ffffff', offset=0.018, uvr=UV[g.get('uv', 'grille')])
            for yb in g.get('bars', []):
                rs = self.ray('front', 0, g['y'] + yb)
                self.dec('body', SOLID, rs, shp_rrect(g['shape'][1] * 0.94, 0.014, 0.006), CHROME, offset=0.02, depth=0.006)
            if g.get('logo', True):
                rs = self.ray('front', 0, g['y'] + g.get('logo_y', 0.0))
                self.dec('body', SOLID, rs, shp_ellipse(0.11, 0.075, 18), '#ffffff', offset=0.03, uvr=UV['logo'], depth=0.008)
        if 'lower' in F:
            g = F['lower']
            r = self.ray('front', 0, g['y'])
            self.dec('body', SOLID, r, scale_shape(shape_of(g['shape']), 1.0, 0.012), '#0d0e10', offset=0.004, depth=0.006)
            self.dec('body', SOLID, r, shape_of(g['shape']), '#ffffff', offset=0.014, uvr=UV['grille_fine'])
        for si in F.get('side_intakes', []):
            for x in (si['x'], -si['x']):
                sh = shape_of(si['shape'])
                sh = sh if x > 0 else flip_u(sh)
                r = self.ray(si.get('side', 'front'), x, si['y'])
                fr = self.dec('body', SOLID, r, scale_shape(sh, 1.0, 0.01), '#0c0d0f', offset=0.004, depth=0.006)
                self.dec('body', SOLID, r, sh, '#ffffff', offset=0.014, uvr=UV['grille_fine'])
                if si.get('fog') and fr:
                    rs = self.sub(fr, 0, 0)
                    self.dec('body', SOLID, rs, shp_ellipse(si['fog'] * 2.5, si['fog'] * 2.5, 14), CHROME, offset=0.018)
                    self.dec('body', HEAD, rs, shp_ellipse(si['fog'] * 2, si['fog'] * 2, 14), '#ffffff', offset=0.022, dome=0.006,
                             uvr=UV['headlamp'])
        if 'splitter' in F:
            y, wv = F['splitter']
            self.B.strip('body', SOLID, [v3(x, y, self.L / 2 + 1) for x in np.linspace(-wv, wv, 15)], (0, 0, -1), 0.04, rgb('#0f1012'),
                         offset=0.006, depth=0.012)
        if 'plate_y' in F:
            r = self.ray('front', 0, F['plate_y'])
            fr = self.dec('body', SOLID, r, shp_rrect(0.54, 0.13, 0.012), '#ffffff', offset=0.022, depth=0.006, uvr=UV['plate'],
                          side_col=rgb('#1a1a1a'))
            if fr:
                hit, n, u, v = fr
                self.meta.setdefault('plates', []).append({'pos': [float(c) for c in hit + n * 0.0295], 'normal': [float(c) for c in n],
                                                           'w': 0.54, 'h': 0.13})
        for sx in F.get('sensors', []):
            r = self.ray('front', sx, F.get('sensor_y', 0.45))
            self.dec('body', SOLID, r, shp_ellipse(0.024, 0.024, 10), '#2d2f33', offset=0.006)
        if 'hood_seam' in F:
            y0 = F['hood_seam']
            self.B.strip('body', SOLID, [v3(x, y0, self.L / 2 + 1) for x in np.linspace(-self.hw * 0.86, self.hw * 0.86, 21)], (0, 0, -1), 0.008,
                         rgb('#0a0a0b'), offset=0.004)
        if 'hood_lines' in F:
            z0, z1, xl = F['hood_lines']
            for sx in (-1, 1):
                self.B.strip('body', SOLID, [v3(sx * xl, self.S['H'] + 1, z + self.zoff) for z in np.linspace(z0, z1, 16)], (0, -1, 0), 0.008,
                             rgb('#0a0a0b'), offset=0.004)
        Rr = Rr0
        if 'plate_y' in Rr:
            r = self.ray('rear', 0, Rr['plate_y'])
            self.dec('body', SOLID, r, shp_rrect(0.6, 0.18, 0.02), '#0f1012', offset=0.004, depth=0.004)
            fr = self.dec('body', SOLID, r, shp_rrect(0.54, 0.13, 0.012), '#ffffff', offset=0.012, depth=0.004, uvr=UV['plate'])
            if fr:
                hit, n, u, v = fr
                self.meta.setdefault('plates', []).append({'pos': [float(c) for c in hit + n * 0.0175], 'normal': [float(c) for c in n],
                                                           'w': 0.54, 'h': 0.13})
            r2 = self.ray('rear', 0, Rr['plate_y'] + 0.085)
            self.dec('body', PLATE_LIGHT, r2, shp_rrect(0.16, 0.014, 0.004), '#ffffff', offset=0.012)
        if 'chrome_bar' in Rr:
            y, wv = Rr['chrome_bar']
            self.B.strip('body', SOLID, [v3(x, y, -self.L / 2 - 1) for x in np.linspace(-wv, wv, 15)], (0, 0, 1), 0.025, CHROME, offset=0.012,
                         depth=0.004)
        if 'light_bar' in Rr:
            y, wv = Rr['light_bar']
            self.B.strip('body', TAIL, [v3(x, y, -self.L / 2 - 1) for x in np.linspace(-wv, wv, 15)], (0, 0, 1), 0.022, rgb('#ff2a2a'),
                         offset=0.014, uvr=UV['led_tail'])
        if S.get('badge') and 'badge_y' in Rr:
            rb = self.ray('rear', S.get('badge_x', 0.0), Rr['badge_y'] - S.get('badge_dy', 0.075))
            hb = self.B.project(*rb)
            if hb is not None:
                nb = self.B.surf_normal(hb)
                self.meta['badge'] = {'text': S['badge'], 'pos': [float(c) for c in hb + nb * 0.006], 'normal': [float(c) for c in nb], 'h': 0.035}
        if 'badge_y' in Rr:
            r = self.ray('rear', 0, Rr['badge_y'])
            self.dec('body', SOLID, r, shp_ellipse(0.1, 0.07, 16), '#ffffff', offset=0.014, uvr=UV['logo'], depth=0.006)
        if 'trunk_seam' in Rr:
            y0 = Rr['trunk_seam']
            self.B.strip('body', SOLID, [v3(x, y0, -self.L / 2 - 1) for x in np.linspace(-self.hw * 0.72, self.hw * 0.72, 17)], (0, 0, 1), 0.008,
                         rgb('#0a0a0b'), offset=0.004)
        if 'diffuser' in Rr:
            y, wv, hh = Rr['diffuser']
            r = self.ray('rear', 0, y)
            self.dec('body', SOLID, r, shp_rrect(wv * 2, hh, 0.03), '#121315', offset=0.005, depth=0.01)
            for k in range(-2, 3):
                rs = self.ray('rear', k * wv * 0.32, y)
                self.dec('body', SOLID, rs, shp_rrect(0.012, hh * 0.8, 0.004), '#26282b', offset=0.018, depth=0.02)
        for rf in Rr.get('reflectors', []):
            for x in (rf[0], -rf[0]):
                r = self.ray('rear', x, rf[1])
                self.dec('body', TAIL, r, shp_rrect(rf[2], 0.024, 0.008), '#9c0f16', offset=0.008, uvr=UV['reflector'])
        for ex in Rr.get('tips', []):
            x, y, w, h = ex
            for xx in (x, -x):
                r = self.ray('rear', xx, y)
                self.dec('body', SOLID, r, shp_rrect(w, h, min(w, h) * 0.45), CHROME, offset=0.006, depth=0.02, side_col=rgb('#9da2a8'))
                self.dec('body', SOLID, r, shp_rrect(w * 0.82, h * 0.7, min(w, h) * 0.35), '#050505', offset=0.028)
        if 'lip_spoiler' in Rr:
            z, w, d = Rr['lip_spoiler']
            r = self.ray('top', 0, 0, z)
            self.dec('body', PAINT, r, shp_rrect(w, d, 0.03), (255, 255, 255, 255), offset=0.004, depth=0.018, updir=(0, 0, 1))
        if Rr.get('third_brake'):
            z, y, w = Rr['third_brake']
            box(self.mesh, 'body', BRAKE, (0, y, z + self.zoff), (w, 0.022, 0.03), rgb('#ff1a1a'))
            box(self.mesh, 'body', SOLID, (0, y - 0.016, z + self.zoff + 0.01), (w + 0.04, 0.012, 0.08), rgb('#111214'))

    # ------------------------------------------------------------------ sides
    def sides(self):
        S = self.S
        B = self.B
        doors_meta = {}
        for (name, z0, z1, kind) in S.get('doors', []):
            for sx in (1, -1):
                pname = 'door_' + name + ('l' if sx > 0 else 'r')
                zm = (z0 + z1) / 2
                belt = B.top(zm)
                ybot = B.bot(zm) + 0.05
                for zz in (z0 + 0.006, z1 - 0.006):
                    o, dv = self.ray('side', sx * 1.0, (belt - 0.06 + ybot) / 2, zz)
                    B.decal(pname, SOLID, o, dv, shp_rrect(0.009, belt - ybot - 0.09, 0.002), rgb('#08090a'), offset=0.004)
                # handle: recess + grip
                hz = z0 + 0.2 if kind != 'slide' else z1 - 0.25
                hz = z1 - 0.24 if kind == 'front' else hz
                hy = belt - S.get('handle_drop', 0.11)
                o, dv = self.ray('side', sx * 1.0, hy, hz)
                B.decal(pname, SOLID, o, dv, shp_ellipse(0.17, 0.05, 16), rgb('#151618'), offset=0.004)
                hc = S.get('handle', 'paint')
                B.decal(pname, PAINT if hc == 'paint' else SOLID, o, dv, shp_rrect(0.18, 0.03, 0.014),
                        (255, 255, 255, 255) if hc == 'paint' else rgb(hc), offset=0.012, depth=0.012, side_col=None)
                hinge_z = (z1 if kind != 'rear_hinge' else z0)
                doors_meta[pname] = {'hinge': [round(sx * (B.half_w(hinge_z) - 0.03), 3), round(hinge_z + self.zoff, 3)], 'side': sx,
                                     'kind': kind}
        self.meta['doors'] = doors_meta
        for rep in S.get('repeaters', []):
            z, y = rep
            for sx in (1, -1):
                o, dv = self.ray('side', sx * 1.0, y, z)
                B.decal('body', IND_L if sx > 0 else IND_R, o, dv, shp_rrect(0.06, 0.022, 0.008), rgb('#ffa424'), offset=0.008, depth=0.004)
        if 'fuel_door' in S:
            z, y = S['fuel_door']
            o, dv = self.ray('side', -1.0, y, z)
            B.decal('body', SOLID, o, dv, shp_rrect(0.17, 0.15, 0.03), rgb('#08090a'), offset=0.003)
            B.decal('body', PAINT, o, dv, shp_rrect(0.155, 0.135, 0.026), (255, 255, 255, 255), offset=0.006)
        if 'side_line' in S:
            # chrome or black moulding along the side (z0, z1, y, colour)
            z0, z1, y, c = S['side_line']
            for sx in (1, -1):
                path = [v3(sx * (self.hw + 1), y, z + self.zoff) for z in np.linspace(z0, z1, 30)]
                B.strip('body', SOLID, path, (-sx, 0, 0), 0.02, rgb(c), offset=0.006, depth=0.004)
        if 'mirror' in S:
            mz, my = S['mirror'][:2]
            for sx in (1, -1):
                base = v3(sx * (B.xmax(mz, B.y_lip(mz)) - 0.015), my, mz + self.zoff)
                P.mirror_side(self.mesh, 'body', base, sx, (255, 255, 255, 255), scale=S.get('mirror_scale', 1.0),
                              truck=S.get('truck_mirrors', False), mat_body=S.get('mirror_mat', PAINT))
        if 'wipers' in S:
            wz, wy = S['wipers']
            for sx, ln in ((0.42, 0.62), (-0.08, 0.55)):
                p0 = v3(sx, wy + 0.01, wz + self.zoff)
                p1 = v3(sx + 0.05 - ln, wy + 0.07, wz + self.zoff - 0.13)
                bar(self.mesh, 'body', SOLID, p0, p1, 0.014, 0.012, P.BLACK)
                bar(self.mesh, 'body', SOLID, p0 + (p1 - p0) * 0.1 + v3(0, 0.01, 0), p1 + v3(0, 0.012, 0), 0.008, 0.02, rgb('#0a0a0a'))
                cylinder(self.mesh, 'body', SOLID, p0 - v3(0, 0.01, 0), p0 + v3(0, 0.015, 0), 0.016, 8, P.BLACK)
        if 'antenna' in S:
            z, y = S['antenna']
            rbox(self.mesh, 'body', SOLID, (0, y + 0.03, z + self.zoff), (0.06, 0.06, 0.16), 0.03, rgb('#111214'))

    # ------------------------------------------------------------------ interior
    def interior(self):
        S = self.S
        B = self.B
        I = S['interior_layout']
        m = self.mesh
        zoff = self.zoff
        lod = self.lod
        seat_col = rgb(I.get('seat', '#2f3136'))
        accent = rgb(I.get('accent', I.get('seat', '#2f3136')))
        dash_col = rgb(I.get('dash', '#1f2023'))
        soft = rgb(I.get('soft', '#2a2b2f'))
        trim_uv = I.get('trim_uv', 'alu')
        seat_uv = I.get('seat_uv', 'leather')
        floor = I['floor']
        zd = I['dash_z'] + zoff
        open_end = max(o[1] for o in S['open'] if o[0] < I['dash_z'] + 0.1)
        inner = B.xmax(I['dash_z'], B.y_lip(I['dash_z'])) - 0.08
        dash_top = min(I.get('dash_top', B.top(I['dash_z']) + 0.02), B.top(open_end) + 0.004)
        dd = max(0.18, open_end + zoff - zd)
        zc = zd + dd / 2
        # lower dash and upper soft dash
        rbox(m, 'interior', SOLID, (0, (floor + 0.22 + dash_top - 0.12) / 2, zc + 0.02), (inner * 2, dash_top - 0.12 - floor - 0.22, dd - 0.04), 0.05,
             dash_col)
        rbox(m, 'interior', SOLID, (0, dash_top - 0.075, zc), (inner * 2, 0.15, dd), 0.07, soft)
        # trim strip across the passenger side
        if not lod:
            ty = dash_top - 0.13
            xs = (-inner + 0.08, -0.16)
            m.quad('interior', SOLID, v3(xs[1], ty - 0.018, zd - 0.004), v3(xs[0], ty - 0.018, zd - 0.004), v3(xs[0], ty + 0.018, zd - 0.004),
                   v3(xs[1], ty + 0.018, zd - 0.004), rgb('#ffffff'), uvr=UV[trim_uv], smooth=False)
            # glovebox seam
            box(m, 'interior', SOLID, (-inner * 0.55, dash_top - 0.24, zd - 0.002), (inner * 0.6, 0.006, 0.004), rgb('#0c0c0d'))
        xd = I.get('driver_x', inner * 0.52)
        # cluster hood + gauge face
        rbox(m, 'interior', SOLID, (xd, dash_top + 0.045, zd + 0.06), (I.get('cluster_w', 0.36) + 0.08, 0.1, 0.22), 0.05, rgb('#141517'))
        tilt = I.get('cluster_tilt', 0.22)
        up = normalize(v3(0, math.cos(tilt), math.sin(tilt)))
        rt = v3(-1, 0, 0)
        gp = v3(xd, dash_top - 0.035, zd + 0.004)
        gw, gh = I.get('cluster_w', 0.36), I.get('cluster_w', 0.36) / 2
        gp = gp + up * (gh / 2 - 0.07)
        q = [gp + rt * (-gw / 2) - up * gh / 2, gp + rt * (gw / 2) - up * gh / 2, gp + rt * (gw / 2) + up * gh / 2, gp + rt * (-gw / 2) + up * gh / 2]
        didx = S.get('_dash_idx', 0)
        m.quad('interior', SCREEN, q[0], q[1], q[2], q[3], rgb('#ffffff'), uvr=DASH.uv_cluster(didx), smooth=False)
        nrm = normalize(np.cross(rt, up))
        self.meta['cluster'] = {'center': [float(c) for c in gp - nrm * 0.004], 'right': [float(c) for c in rt], 'up': [float(c) for c in up],
                                'w': gw, 'h': gh}
        # centre stack: screen, HVAC, vents
        if I.get('screen', True):
            sw_, sh_ = I.get('screen_w', 0.3), 0.15
            sp = v3(0, dash_top - 0.06, zd - 0.03)
            rbox(m, 'interior', SOLID, sp + v3(0, 0, 0.02), (sw_ + 0.03, sh_ + 0.03, 0.03), 0.01, rgb('#0b0c0d'))
            m.quad('interior', SCREEN, sp + v3(sw_ / 2, -sh_ / 2, -0.006), sp + v3(-sw_ / 2, -sh_ / 2, -0.006), sp + v3(-sw_ / 2, sh_ / 2, -0.006),
                   sp + v3(sw_ / 2, sh_ / 2, -0.006), rgb('#ffffff'), uvr=DASH.uv_screen(S.get('_dash_idx', 0)), smooth=False)
            self.meta['screen'] = {'center': [float(c) for c in sp + v3(0, 0, -0.007)], 'w': sw_, 'h': sh_}
            hp = v3(0, dash_top - 0.2, zd - 0.012)
            m.quad('interior', SOLID, hp + v3(0.15, -0.035, 0), hp + v3(-0.15, -0.035, 0), hp + v3(-0.15, 0.035, 0), hp + v3(0.15, 0.035, 0),
                   rgb('#ffffff'), uvr=UV['hvac'], smooth=False)
            if not lod:
                for kx in (0.12, -0.12):
                    cylinder(m, 'interior', SOLID, hp + v3(kx, 0, 0.0), hp + v3(kx, 0, -0.025), 0.026, 12, rgb('#ffffff'), uvr=UV['knob'])
        for vx in (inner - 0.1, 0.2, -0.2, -inner + 0.1):
            vp = v3(vx, dash_top - 0.06 if abs(vx) < 0.3 else dash_top - 0.07, zd - 0.006)
            m.quad('interior', SOLID, vp + v3(0.065, -0.035, 0), vp + v3(-0.065, -0.035, 0), vp + v3(-0.065, 0.035, 0), vp + v3(0.065, 0.035, 0),
                   rgb('#ffffff'), uvr=UV['vent'], smooth=False)
        # steering wheel
        swr = I.get('wheel_r', 0.185)
        swc = v3(xd, I.get('wheel_y', dash_top - 0.07), zd - I.get('wheel_back', 0.24))
        ang = I.get('column_angle', 0.42)
        axis = normalize(v3(0, -math.sin(ang), math.cos(ang)))
        P.steering_wheel(m, swc, axis, R=swr, paddles=I.get('paddles', False), lod=lod)
        self.meta['steer'] = {'pivot': [float(c) for c in swc], 'axis': [float(c) for c in -axis]}
        # pedals + footrest
        pz = zd + 0.06
        for k, (px, w, h, show) in enumerate(((xd - 0.15, 0.06, 0.12, True), (xd + 0.0, 0.11, 0.07, True),
                                              (xd + 0.15, 0.08, 0.07, not I.get('auto', True)))):
            if not show:
                continue
            arm_top = v3(px, floor + 0.36, pz + 0.04)
            pad = v3(px, floor + 0.14 + (0.05 if k == 0 else 0.0), pz - 0.03)
            bar(m, 'interior', SOLID, arm_top, pad + v3(0, 0.02, 0.01), 0.02, 0.015, rgb('#2c2e31'))
            rbox(m, 'interior', SOLID, pad, (w, h, 0.02), 0.008, rgb('#55585e'), rot=rot_x(-0.5))
        rbox(m, 'interior', SOLID, (xd + 0.28, floor + 0.1, pz - 0.04), (0.07, 0.16, 0.02), 0.008, rgb('#2a2c30'), rot=rot_x(-0.55))
        # seats
        seats = []
        rear_seat_z = None
        for row in I['rows']:
            kind, z, y = row[0], row[1] + zoff, row[2]
            if kind == 'front2':
                sx = row[3] if len(row) > 3 else xd
                for xx in (sx, -sx):
                    P.seat(m, 'interior', xx, y, z, 0.52, seat_col, accent=accent, uv=seat_uv, lod=lod)
                seats += [[sx, y, z], [-sx, y, z]]
            elif kind == 'front1':
                P.seat(m, 'interior', xd, y, z, 0.52, seat_col, accent=accent, uv=seat_uv, lod=lod)
                seats += [[xd, y, z]]
            elif kind == 'bench':
                w = row[3]
                heads = row[4] if len(row) > 4 else 2
                P.bench(m, 'interior', -w, w, y, z, seat_col, heads=heads, uv=seat_uv, lod=lod)
                seats += [[w * 0.5, y, z], [-w * 0.5, y, z]]
                rear_seat_z = z
            elif kind == 'bus':
                z1 = row[3] + zoff
                zz = z
                while zz < z1:
                    for sx in (1, -1):
                        for k in (0.3, 0.75):
                            P.seat(m, 'interior', sx * (inner - k * 0.55), y, zz, 0.42, seat_col, kind='flat', headrest=False, scale=0.9, uv='fabric',
                                   lod=1)
                    zz += 0.85
        # console with shifter, cupholders, armrest, parking button
        if I.get('console', True) and seats:
            cz0 = seats[0][2] - 0.2
            cz1 = zd + 0.02
            rbox(m, 'interior', SOLID, (0, floor + 0.16, (cz0 + cz1) / 2), (0.24, 0.32, cz1 - cz0), 0.04, dash_col)
            rbox(m, 'interior', SOLID, (0, floor + 0.34, cz0 + 0.18), (0.22, 0.08, 0.34), 0.035, accent, uvr=UV[seat_uv])
            gz = zd - 0.2
            gplate = v3(0, floor + 0.325, gz)
            m.quad('interior', SOLID, gplate + v3(0.06, 0, -0.06), gplate + v3(-0.06, 0, -0.06), gplate + v3(-0.06, 0, 0.06), gplate + v3(0.06, 0, 0.06),
                   rgb('#ffffff'), uvr=UV['gear_plate'], smooth=False)
            if not lod:
                rbox(m, 'interior', SOLID, gplate + v3(0, 0.03, 0), (0.06, 0.06, 0.07), 0.025, rgb('#111213'))
                cylinder(m, 'interior', SOLID, gplate + v3(0, 0.05, 0), gplate + v3(0, 0.14, -0.02), 0.011, 6, CHROME, caps=False)
                rbox(m, 'interior', SOLID, gplate + v3(0, 0.16, -0.025), (0.05, 0.065, 0.075), 0.024, rgb('#0f1011'), uvr=UV['leather'])
                for cx in (0.05, -0.05):
                    disc(m, 'interior', SOLID, (cx, floor + 0.322, gz + 0.16), (0, 1, 0), 0.035, 12, rgb('#050505'))
                box(m, 'interior', SOLID, (0.07, floor + 0.324, gz + 0.04), (0.03, 0.004, 0.02), rgb('#e04020'))
        # door panels (move with the doors)
        for (name, z0, z1, kind) in S.get('doors', []):
            for sx in (1, -1):
                pname = 'door_' + name + ('l' if sx > 0 else 'r')
                zm = (z0 + z1) / 2 + zoff
                xi = sx * (B.xmax((z0 + z1) / 2, B.y_lip((z0 + z1) / 2)) - 0.075)
                belt = B.top((z0 + z1) / 2)
                bar(m, pname, SOLID, v3(xi - sx * 0.035, belt - 0.2, z0 + zoff + 0.12), v3(xi - sx * 0.035, belt - 0.2, z1 + zoff - 0.12), 0.07, 0.05,
                    soft)
                bar(m, pname, SOLID, v3(xi - sx * 0.02, belt - 0.03, z0 + zoff + 0.05), v3(xi - sx * 0.02, belt - 0.03, z1 + zoff - 0.05), 0.04, 0.03,
                    rgb('#18191b'))
                if not lod:
                    sp = v3(xi - sx * 0.004, floor + 0.17, zm + 0.1)
                    disc(m, pname, SOLID, sp, (-sx, 0, 0), 0.07, 14, rgb('#ffffff'), uvr=UV['speaker'])
                    hp = v3(xi - sx * 0.006, belt - 0.11, z1 + zoff - 0.2)
                    rbox(m, pname, SOLID, hp, (0.02, 0.03, 0.11), 0.01, CHROME)
                    if name == 'f':
                        wp = v3(xi - sx * 0.07, belt - 0.172, zm + 0.12)
                        m.quad(pname, SOLID, wp + v3(0, 0, -0.06), wp + v3(0, 0, 0.06), wp + v3(-sx * 0.03, 0.006, 0.06), wp + v3(-sx * 0.03, 0.006, -0.06),
                               rgb('#ffffff'), uvr=UV['buttons'], smooth=False)
        # headliner furniture
        G = S.get('gh', {})
        if G:
            roof = pchip(G['roof'])
            zf = G['flat'][1]
            ry = roof(zf) - 0.04
            for sx in (1, -1):
                rbox(m, 'interior', SOLID, (sx * 0.3, ry - 0.01, zf - 0.08 + zoff), (0.36, 0.022, 0.17), 0.01, rgb(S.get('headliner', '#8c8a86')),
                     rot=rot_x(0.12))
                if rear_seat_z is not None and not lod:
                    bar(m, 'interior', SOLID, v3(sx * (inner - 0.05), ry - 0.05, rear_seat_z + 0.15), v3(sx * (inner - 0.05), ry - 0.05, rear_seat_z + 0.32),
                        0.025, 0.02, rgb('#5c5b58'))
            rbox(m, 'interior', SOLID, (0, ry - 0.005, zf - 0.22 + zoff), (0.24, 0.03, 0.14), 0.012, rgb('#6c6a66'))
            box(m, 'interior', INTERIOR_LIGHT, (0, ry - 0.022, zf - 0.22 + zoff), (0.16, 0.004, 0.06), rgb('#fff3d6'))
            if 'imirror' not in I:
                I['imirror'] = (zf - 0.05, ry)
        if 'imirror' in I:
            mz, my = I['imirror']
            bar(m, 'interior', SOLID, v3(0, my + 0.04, mz + zoff), v3(0, my - 0.02, mz + zoff - 0.03), 0.02, 0.02, P.BLACK)
            rbox(m, 'interior', SOLID, (0, my - 0.04, mz + zoff - 0.04), (0.25, 0.07, 0.03), 0.02, P.BLACK)
            m.quad('interior', SOLID, v3(0.115, my - 0.068, mz + zoff - 0.056), v3(-0.115, my - 0.068, mz + zoff - 0.056),
                   v3(-0.115, my - 0.012, mz + zoff - 0.056), v3(0.115, my - 0.012, mz + zoff - 0.056), rgb('#ffffff'), uvr=UV['glass'], smooth=False)
        # seat belts (B-pillar to floor)
        if seats and not lod:
            for s in seats[:2]:
                sx = 1 if s[0] > 0 else -1
                x = sx * (B.xmax(s[2] - zoff, B.y_lip(s[2] - zoff)) - 0.09)
                top = v3(x, B.top(s[2] - zoff) + 0.32, s[2] - 0.32)
                bar(m, 'interior', SOLID, top, v3(x, floor + 0.12, s[2] - 0.28), 0.045, 0.006, rgb('#1a1a1c'))
        # parcel shelf & third brake light
        if 'parcel' in I:
            z0, z1, y = I['parcel']
            box(m, 'interior', SOLID, (0, y, (z0 + z1) / 2 + zoff), (inner * 2 - 0.02, 0.02, z1 - z0), rgb('#141516'))
        self.meta['seats'] = [[round(float(c), 3) for c in s] for s in seats]
        self._interior_checks(seats, floor)

    def _interior_checks(self, seats, floor):
        S = self.S
        G = S.get('gh')
        if not seats or not G:
            return
        roof = pchip(G['roof'])
        eye = S.get('driver_eye', 0.72)
        for i, s in enumerate(seats[:4]):
            zz = s[2] - self.zoff
            head_top = s[1] + 0.84
            clear = roof(zz) - 0.03 - head_top
            self.report.append('seat %d: head clearance %.2f m, eye %.2f m' % (i, clear, s[1] + eye))
        st = self.meta['steer']['pivot']
        self.report.append('steering wheel -> driver backrest distance %.2f m' % (st[2] - (seats[0][2] - 0.3)))

    # ------------------------------------------------------------------ extras / wheels
    def extras(self):
        S = self.S
        m = self.mesh
        zoff = self.zoff
        for ex in S.get('extras', []):
            fn = ex[0]
            a = ex[1:]
            if fn == 'lightbar':
                P.lightbar(m, 'body', (0, a[1], a[0] + zoff), a[2], a[3] if len(a) > 3 else 'police')
            elif fn == 'taxi':
                P.taxi_sign(m, 'body', (0, a[1], a[0] + zoff))
            elif fn == 'rack':
                P.roof_rack(m, 'body', a[0] + zoff, a[1] + zoff, a[2], a[3], rails_only=a[4] if len(a) > 4 else False)
            elif fn == 'spare':
                P.spare_wheel(m, 'body', (0, a[1], a[0] + zoff), S['R'], S['TW'])
            elif fn == 'wing':
                P.wing(m, 'body', a[0] + zoff, a[1], a[2], chord=a[3] if len(a) > 3 else 0.28)
            elif fn == 'box':
                cx, cy, cz, sx, sy, sz, r, mat, col = a[:9]
                rbox(m, a[9] if len(a) > 9 else 'body', mat, (cx, cy, cz + zoff), (sx, sy, sz), r, rgb(col) if isinstance(col, str) else col,
                     uvr=UV.get(a[10]) if len(a) > 10 else None)
            elif fn == 'cargo':
                P.cargo_box(m, 'body', a[0] + zoff, a[1] + zoff, a[2], a[3], a[4], mat=a[5] if len(a) > 5 else PAINT,
                            col=(rgb(a[6]) if len(a) > 6 and isinstance(a[6], str) else None), logo_uv=UV.get(a[7]) if len(a) > 7 else None)
            elif fn == 'bar':
                p0, p1, w, h, col = a[:5]
                bar(m, 'body', a[5] if len(a) > 5 else SOLID, v3(p0[0], p0[1], p0[2] + zoff), v3(p1[0], p1[1], p1[2] + zoff), w, h,
                    rgb(col) if isinstance(col, str) else col)
            elif fn == 'cyl':
                p0, p1, r, col = a[:4]
                cylinder(m, 'body', a[4] if len(a) > 4 else SOLID, v3(p0[0], p0[1], p0[2] + zoff), v3(p1[0], p1[1], p1[2] + zoff), r, 10,
                         rgb(col) if isinstance(col, str) else col)
            elif fn == 'quad':
                pts = [v3(p[0], p[1], p[2] + zoff) for p in a[0]]
                m.quad('body', a[2] if len(a) > 2 else SOLID, pts[0], pts[1], pts[2], pts[3], rgb('#ffffff'), uvr=UV[a[1]], smooth=False)
        for d in S.get('decals', []):
            side = d.get('side', 'front')
            xs = [d.get('x', 0.0)]
            if d.get('mirror', True) and abs(d.get('x', 0.0)) > 1e-6:
                xs.append(-d['x'])
            for i, x in enumerate(xs):
                mat = d['mat']
                if i == 1 and mat in (IND_L, IND_R):
                    mat = IND_R if mat == IND_L else IND_L
                sh = shape_of(d['shape'])
                if i == 1 and not d.get('noflip'):
                    sh = flip_u(sh)
                r = self.ray(side, x, d.get('y', 0.5), d.get('z'))
                self.dec(d.get('part', 'body'), mat, r, sh, d.get('col', '#ffffff'), offset=d.get('offset', 0.01), depth=d.get('depth', 0.0),
                         uvr=UV.get(d.get('uv', 'white')), dome=d.get('dome', 0.0), updir=d.get('updir', (0, 1, 0)))

    def wheels(self):
        S = self.S
        wm = Mesh()
        P.wheel(wm, S['R'], S['TW'], S.get('rim', 'spoke5'), rim_ratio=S.get('rim_ratio', 0.64), rimcol=S.get('rimcol'),
                whitewall=S.get('whitewall', False), caliper=S.get('caliper', '#8c9096'), lod=self.lod)
        wheels = []
        tx = S.get('track', self.hw - S['TW'] / 2 - 0.035)
        for i, za in enumerate(S['axles']):
            steer = (i == len(S['axles']) - 1) or (za in S.get('steer_axles', []))
            dual = za in S.get('dual_axles', [])
            for sx in (1, -1):
                wheels.append({'x': round(sx * tx, 3), 'y': S['R'], 'z': round(za + self.zoff, 3), 'r': S['R'], 'steer': steer, 'mirror': sx < 0})
                if dual:
                    wheels.append({'x': round(sx * (tx - S['TW'] - 0.02), 3), 'y': S['R'], 'z': round(za + self.zoff, 3), 'r': S['R'], 'steer': False,
                                   'mirror': sx < 0})
            # clearance checks
            arch_top = self.B.arch(za)
            self.report.append('axle %.2f: tyre-to-arch gap %.3f m, tyre outer face %.3f vs body side %.3f' %
                               (za, arch_top - 2 * S['R'], tx + S['TW'] / 2, self.B.xmax(za, arch_top + 0.06)))
        self.meta['wheels'] = wheels
        self.meta['wheelbase'] = round(S['axles'][-1] - S['axles'][0], 3)
        return wm

    def build(self):
        S = self.S
        self.B.build_lower()
        self.B.build_greenhouse()
        if 'front_lamp' in S:
            self.front_lamp(S['front_lamp'])
        if 'rear_lamp' in S:
            self.rear_lamp(S['rear_lamp'])
        self.fascia()
        self.sides()
        self.interior()
        self.extras()
        for ex in ([] if S.get('catalog', {}).get('ex') else S.get('exhaust', [])):
            x, y, n = ex
            for sx in ([1, -1] if n == 2 else [1]):
                cylinder(self.mesh, 'body', SOLID, v3(sx * x, y, -self.L / 2 + 0.12), v3(sx * x, y, -self.L / 2 - 0.02), 0.035, 12, CHROME)
                disc(self.mesh, 'body', SOLID, v3(sx * x, y, -self.L / 2 - 0.021), v3(0, 0, -1), 0.028, 12, rgb('#0a0a0a'))
        wm = self.wheels()
        import custom
        custom.build_variants(self)
        wopts = S.get('catalog', {}).get('wh', [])
        if wopts:
            wm = Mesh()
            for i, w in enumerate(wopts):
                one = Mesh()
                P.wheel(one, S['R'], S['TW'], w.get('rim', 'spoke5'), rim_ratio=w.get('rim_ratio', S.get('rim_ratio', 0.64)), rimcol=w.get('rimcol'),
                        whitewall=w.get('whitewall', False), caliper=w.get('caliper', S.get('caliper', '#8c9096')), lod=self.lod)
                for (part, mat), tris in one.groups.items():
                    if part == 'wheel':
                        wm.groups.setdefault(('wheel:%d' % i, mat), []).extend(tris)
                    elif i == 0:
                        wm.groups.setdefault((part, mat), []).extend(tris)
        for k in ('make', 'model', 'year', 'description', 'category_name', 'body_style'):
            if k in S:
                self.meta[k] = S[k]
        meta = self.meta
        meta['beams'] = self.beams[:2] if self.beams else [[0.6, 0.6, self.L / 2], [-0.6, 0.6, self.L / 2]]
        for k in ('maxSpeed', 'accel', 'brake', 'mass', 'fuel', 'electric', 'seats_n', 'spawnWeight', 'steerMax', 'colors', 'driver_eye'):
            if k in S:
                meta[k] = S[k]
        meta['filler'] = S.get('filler', [-self.hw - 0.05, 0.85, -self.L / 2 + 0.9])
        meta['beacons'] = any(mm in (BEACON_A, BEACON_B) for (_, mm) in self.mesh.groups.keys())
        meta['tris'] = self.mesh.count() + wm.count() * len(meta['wheels'])
        return self.mesh, wm, meta


def build(S, lod=0, verbose=False):
    b = Builder(S, lod)
    mesh, wm, meta = b.build()
    if verbose:
        for r in b.report:
            print('   ', r)
    return mesh, wm, meta


def export(mesh, wmesh, meta, path_bin, path_json=None):
    out = bytearray()
    out += b'TCM2'
    groups = [(part, mat, tris) for (part, mat), tris in mesh.groups.items()]
    groups += [((part if part.startswith('wheel') else 'caliper'), mat, tris) for (part, mat), tris in wmesh.groups.items()]
    out += struct.pack('>i', len(groups))
    for part, mat, tris in groups:
        norms = compute_normals(tris, 40.0)
        nb = part.encode()
        out += struct.pack('>H', len(nb)) + nb
        out += struct.pack('>ii', mat, len(tris) * 3)
        for t, ns in zip(tris, norms):
            col = t[6]
            for k in range(3):
                p = t[k]
                uv = t[3 + k]
                n = ns[k]
                out += struct.pack('>fffbbbHHI', p[0], p[1], p[2], int(round(n[0] * 127)), int(round(n[1] * 127)), int(round(n[2] * 127)),
                                   int(max(0, min(1, uv[0])) * 65535), int(max(0, min(1, uv[1])) * 65535),
                                   (col[3] << 24) | (col[0] << 16) | (col[1] << 8) | col[2])
    with gzip.open(path_bin, 'wb', compresslevel=9) as f:
        f.write(bytes(out))
    if path_json:
        with open(path_json, 'w') as f:
            json.dump(meta, f, indent=1)
