"""Procedural motorcycles (cruiser / supersport / scrambler) for the TrafficCraft generator.

Same conventions as the cars: +Z forward, +Y up, +X = LEFT, metres, z centred on the bike.
Parts: 'body' (frame, engine, tank, seat, tail, exhaust), 'steer' (fork, bars, front lamp on naked bikes:
rotates about the steering head), 'wheel' / 'caliper' (one wheel mesh drawn at both axles).
"""
import math
import numpy as np
from geom import (pchip, PAINT, PAINT2, SOLID, GLASS, HEAD, HIGH, DRL, TAIL, BRAKE, IND_L, IND_R, PLATE_LIGHT, SCREEN, UV, Mesh, rgb, v3,
                  normalize, rbox, box, cylinder, disc, torus, revolve, bar)
import dash as DASH

CHROME = rgb('#d6dadf')
POLISH = rgb('#b9bdc2')
DARK = rgb('#1b1c1e')
BLACK = rgb('#0e0e0f')
RUBBER = rgb('#1d1e20')
ALU = rgb('#9a9fa6')
ENGINE_GREY = rgb('#4a4d52')
WHITE = rgb('#ffffff')


# ===================================================================================== helpers
def oquad(m, part, mat, a, b, c, d, col, inside, uvr=None, smooth=True, uvs=None):
    """Quad with its winding chosen so the normal points away from 'inside'."""
    n = np.cross(b - a, c - a)
    cen = (a + b + c + d) / 4
    if np.dot(n, cen - inside) < 0:
        a, b, c, d = d, c, b, a
        if uvs is not None:
            uvs = (uvs[3], uvs[2], uvs[1], uvs[0])
    m.quad(part, mat, a, b, c, d, col, uvr=uvr, smooth=smooth, uvs=uvs)


def ring(sec, seg):
    """Superellipse cross-section (z, cy, hw, hh, n[, cx, bottom_flat])."""
    z, cy, hw, hh, n = sec[:5]
    cx = sec[5] if len(sec) > 5 else 0.0
    flat = sec[6] if len(sec) > 6 else 1.0
    pts = []
    for k in range(seg):
        t = 2 * math.pi * k / seg
        c, s = math.cos(t), math.sin(t)
        x = cx + hw * math.copysign(abs(c) ** (2.0 / n), c)
        y = s
        y = math.copysign(abs(y) ** (2.0 / n), y)
        y = cy + hh * (y if y > 0 else y * flat)
        pts.append(v3(x, y, z))
    return pts


def loft(m, part, mat, secs, col, seg=16, caps=True, uvr=None):
    """Closed tube through superellipse sections (sorted along z)."""
    rings = [ring(s, seg) for s in secs]
    for i in range(len(rings) - 1):
        cen = v3(0.5 * ((secs[i][5] if len(secs[i]) > 5 else 0) + (secs[i + 1][5] if len(secs[i + 1]) > 5 else 0)),
                 0.5 * (secs[i][1] + secs[i + 1][1]), 0.5 * (secs[i][0] + secs[i + 1][0]))
        for k in range(seg):
            k2 = (k + 1) % seg
            oquad(m, part, mat, rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k], col, cen, uvr=uvr)
    if caps:
        for idx, sgn in ((0, -1), (-1, 1)):
            r = rings[idx]
            c = sum(r) / len(r)
            for k in range(seg):
                k2 = (k + 1) % seg
                a, b = r[k], r[k2]
                n = np.cross(b - c, a - c)
                if np.dot(n, v3(0, 0, sgn)) < 0:
                    a, b = b, a
                m.tri(part, mat, (c, b, a), ((0, 0), (0, 0), (0, 0)) if uvr is None else ((uvr[0], uvr[1]),) * 3, col, smooth=False)


def tube(m, part, pts, r, col, seg=8, mat=SOLID):
    """Polyline tube with ball joints."""
    for a, b in zip(pts[:-1], pts[1:]):
        cylinder(m, part, mat, v3(*a), v3(*b), r, seg, col, caps=False)
    for p in pts[1:-1]:
        sphere(m, part, mat, v3(*p), r, col, 6)


def sphere(m, part, mat, c, r, col, seg=8):
    prof = [(r * math.sin(math.pi * i / seg), -r * math.cos(math.pi * i / seg)) for i in range(seg + 1)]
    revolve(m, part, mat, prof, seg * 2, col, axis='y', center=tuple(c))


def arc_strip(m, part, mat, cx, cy, cz, R, a0, a1, w, thick, col, seg=14, crown=0.03):
    """Mudguard: curved strip around a wheel centre (angles from +z toward +y), slightly crowned."""
    outer, inner = [], []
    for i in range(seg + 1):
        a = a0 + (a1 - a0) * i / seg
        dz, dy = math.cos(a), math.sin(a)
        row_o, row_i = [], []
        for j in range(5):
            x = -w / 2 + w * j / 4
            cr = crown * (1 - (2 * j / 4 - 1) ** 2)
            row_o.append(v3(cx + x, cy + dy * (R + cr), cz + dz * (R + cr)))
            row_i.append(v3(cx + x, cy + dy * (R + cr - thick), cz + dz * (R + cr - thick)))
        outer.append(row_o)
        inner.append(row_i)
    ctr = v3(cx, cy, cz)
    for i in range(seg):
        for j in range(4):
            oquad(m, part, mat, outer[i][j], outer[i][j + 1], outer[i + 1][j + 1], outer[i + 1][j], col, ctr)
            q = [inner[i][j], inner[i][j + 1], inner[i + 1][j + 1], inner[i + 1][j]]
            n = np.cross(q[1] - q[0], q[2] - q[0])
            cen = sum(q) / 4
            if np.dot(n, ctr - cen) < 0:
                q = q[::-1]
            m.quad(part, mat, *q, col)
    for i in range(seg):
        for j in (0, 4):
            m.quad(part, mat, outer[i][j], outer[i + 1][j], inner[i + 1][j], inner[i][j], col)


def finned_cylinder(m, part, base, top, r, col, fins=7, fin_col=None, lod=0):
    """Air-cooled cylinder: barrel + stack of cooling fins + head."""
    base, top = v3(*base), v3(*top)
    cylinder(m, part, SOLID, base, top, r * 0.82, 12 if not lod else 8, col)
    if lod:
        cylinder(m, part, SOLID, base, top, r, 8, fin_col or col)
        return
    ax = top - base
    for i in range(fins):
        t = (i + 0.5) / fins
        p = base + ax * t
        cylinder(m, part, SOLID, p - normalize(ax) * 0.004, p + normalize(ax) * 0.004, r * (1.0 if i < fins - 1 else 0.95), 14, fin_col or col)


# ===================================================================================== wheel
def bike_wheel(m, R, TW, style, rimcol, lod=0, disc_both=True, knobby=False):
    """Wheel centred at origin, axis X. Tyre with rounded crown, rim, spokes, hub, brake rotor(s)."""
    P = 'wheel'
    seg = 14 if lod else 28
    rr = R * (0.72 if style != 'wire' else 0.70)
    h = TW / 2
    tyre = [(rr + 0.004, -h * 0.72), (rr + (R - rr) * 0.45, -h * 1.0), (R - 0.012, -h * 0.8), (R, -h * 0.35), (R, h * 0.35), (R - 0.012, h * 0.8),
            (rr + (R - rr) * 0.45, h * 1.0), (rr + 0.004, h * 0.72)]
    revolve(m, P, SOLID, tyre, seg, RUBBER, axis='x', uvr=UV['sidewall'])
    if knobby and not lod:
        for k in range(36):
            a = 2 * math.pi * k / 36
            for sx in (-1, 1):
                c = v3(sx * h * 0.42, math.cos(a) * (R + 0.006), math.sin(a) * (R + 0.006))
                rbox(m, P, SOLID, tuple(c), (h * 0.55, 0.018, 0.022), 0.004, RUBBER, rot=np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)],
                                                                                                    [0, math.sin(a), math.cos(a)]]))
    rim = [(rr - 0.02, -h * 0.55), (rr, -h * 0.68), (rr + 0.006, -h * 0.6), (rr + 0.006, h * 0.6), (rr, h * 0.68), (rr - 0.02, h * 0.55),
           (rr - 0.028, 0.0), (rr - 0.02, -h * 0.55)]
    revolve(m, P, SOLID, rim, seg, rimcol, axis='x')
    # hub
    revolve(m, P, SOLID, [(0.0, -h * 0.75), (0.05, -h * 0.75), (0.06, -h * 0.5), (0.045, 0), (0.06, h * 0.5), (0.05, h * 0.75), (0.0, h * 0.75)],
            12, POLISH if style == 'wire' else rimcol, axis='x')
    if style == 'wire':
        n = 20 if lod else 36
        for k in range(n):
            a = 2 * math.pi * k / n
            side = 1 if k % 2 else -1
            a2 = a + (0.35 if k % 4 < 2 else -0.35)
            p0 = v3(side * h * 0.55, math.cos(a) * 0.05, math.sin(a) * 0.05)
            p1 = v3(side * h * 0.2, math.cos(a2) * (rr - 0.024), math.sin(a2) * (rr - 0.024))
            cylinder(m, P, SOLID, p0, p1, 0.0028, 4, CHROME, caps=False)
    else:
        nsp = {'cast3': 3, 'cast5': 5, 'cast6': 6}.get(style, 5)
        for k in range(nsp):
            a = 2 * math.pi * k / nsp
            for j in ((-1, 1) if style == 'cast3' else (0,)):
                b = a + 0.12 * j
                p0 = v3(0, math.cos(b) * 0.055, math.sin(b) * 0.055)
                mid_a = b + 0.25
                pm = v3(0, math.cos(mid_a) * rr * 0.55, math.sin(mid_a) * rr * 0.55)
                p1 = v3(0, math.cos(b + 0.42) * (rr - 0.015), math.sin(b + 0.42) * (rr - 0.015))
                bar(m, P, SOLID, p0, pm, 0.026, 0.02, rimcol, up=(1, 0, 0))
                bar(m, P, SOLID, pm, p1, 0.022, 0.018, rimcol, up=(1, 0, 0))
    # brake rotor(s): drilled disc
    rotor_r = rr * 0.86
    for sx in ((-1, 1) if disc_both else (1,)):
        x = sx * (h * 0.85)
        revolve(m, P, SOLID, [(rotor_r * 0.62, x - 0.0025), (rotor_r, x - 0.0025), (rotor_r, x + 0.0025), (rotor_r * 0.62, x + 0.0025),
                               (rotor_r * 0.62, x - 0.0025)], seg, ALU, axis='x', uvr=UV['rotor'])
        if not lod:
            for k in range(6):
                a = 2 * math.pi * k / 6
                cylinder(m, P, SOLID, v3(x, math.cos(a) * rotor_r * 0.62, math.sin(a) * rotor_r * 0.62), v3(x * 0.6, math.cos(a) * 0.05, math.sin(a) * 0.05),
                         0.005, 4, ALU, caps=False)
        # caliper (static): radial block behind the axle
        rbox(m, 'caliper', SOLID, (x + sx * 0.012, rotor_r * 0.55, -rotor_r * 0.62), (0.03, 0.09, 0.05), 0.012, rgb('#a01818') if style != 'wire'
             else rgb('#2a2b2e'))


# ===================================================================================== the bike
class BikeBuilder:
    def __init__(self, S, lod=0):
        self.S = S
        self.lod = lod
        self.m = Mesh()
        self.meta = {'id': S['id'], 'name': S['name'], 'category': 'motorcycle', 'length': S['L'], 'width': S['W'], 'height': S['H'], 'bike': True}
        self.seg = 10 if lod else 18

    # ---------------------------------------------------------------- shared pieces
    def lamp_round(self, part, c, normal, r, depth, bucket_col, lens_mat=HEAD, high_ring=True):
        c = v3(*c)
        n = normalize(v3(*normal))
        # bucket
        prof = [(0.0, -depth), (r * 0.55, -depth), (r * 0.92, -depth * 0.55), (r * 1.05, -0.01), (r * 1.08, 0.0), (r * 0.98, 0.006)]
        revolve(self.m, part, SOLID, prof, 20, bucket_col, axis='z', center=tuple(c))
        disc(self.m, part, lens_mat, c + n * 0.008, n, r * (0.62 if high_ring else 0.95), 20, WHITE)
        if high_ring:
            torus(self.m, part, HIGH, c + n * 0.005, n, r * 0.8, r * 0.13, 20, 6, WHITE)

    def plate(self, c, normal, w=0.2, h=0.13):
        c = v3(*c)
        n = normalize(v3(*normal))
        rbox(self.m, 'body', SOLID, tuple(c - n * 0.004), (w, h, 0.006), 0.004, WHITE, uvr=UV['plate'])
        self.meta.setdefault('plates', []).append({'pos': [float(x) for x in c + n * 0.003], 'normal': [float(x) for x in n], 'w': w, 'h': h})

    def indicator(self, part, base, outward, mat, length=0.07, r=0.018, lens='round'):
        base = v3(*base)
        d = normalize(v3(*outward))
        tip = base + d * length
        cylinder(self.m, part, SOLID, base, tip, 0.006, 6, BLACK)
        if lens == 'round':
            sphere(self.m, part, mat, tip + d * r * 0.6, r, WHITE, 6)
        else:
            rbox(self.m, part, mat, tuple(tip + d * 0.02), (0.05 if abs(d[0]) > 0.5 else 0.022, 0.016, 0.03 if abs(d[0]) > 0.5 else 0.05), 0.006, WHITE)

    def cluster(self, part, c, up, w, h):
        c = v3(*c)
        up = normalize(v3(*up))
        rt = v3(-1, 0, 0)
        q = [c + rt * (-w / 2) - up * h / 2, c + rt * (w / 2) - up * h / 2, c + rt * (w / 2) + up * h / 2, c + rt * (-w / 2) + up * h / 2]
        self.m.quad(part, SCREEN, q[0], q[1], q[2], q[3], WHITE, uvr=DASH.uv_cluster(self.S.get('_dash_idx', 0)), smooth=False)
        nrm = normalize(np.cross(rt, up))
        self.meta['cluster'] = {'center': [float(x) for x in c - nrm * 0.003], 'right': [float(x) for x in rt], 'up': [float(x) for x in up], 'w': w, 'h': h}

    def mirrors(self, part, base_l, stem, head_size, col, shape='round'):
        for sx in (1, -1):
            b = v3(sx * base_l[0], base_l[1], base_l[2])
            t = b + v3(sx * stem[0], stem[1], stem[2])
            cylinder(self.m, part, SOLID, b, t, 0.006, 6, CHROME if shape == 'round' else BLACK, caps=False)
            if shape == 'round':
                revolve(self.m, part, SOLID, [(0.0, -0.02), (head_size, -0.012), (head_size, 0.004), (0.0, 0.006)], 14, col, axis='z',
                        center=tuple(t + v3(0, 0.02, 0)))
                disc(self.m, part, SOLID, t + v3(0, 0.02, -0.021), v3(0, 0, -1), head_size * 0.9, 14, rgb('#9fb4c4'))
            else:
                rbox(self.m, part, PAINT, tuple(t + v3(sx * 0.03, 0.02, 0)), (0.11, 0.05, 0.03), 0.012, WHITE)
                box(self.m, part, SOLID, (t[0] + sx * 0.03, t[1] + 0.02, t[2] - 0.016), (0.095, 0.038, 0.002), rgb('#9fb4c4'))

    def bars(self, pts_left, grip_r=0.017, col=CHROME):
        """Handlebar polyline from centre to the left end (mirrored); grips, levers, switchgear."""
        m = self.m
        pts = [v3(*p) for p in pts_left]
        full = [v3(-p[0], p[1], p[2]) for p in reversed(pts)] + pts[1:]
        tube(m, 'steer', [tuple(p) for p in full], 0.011, col)
        for sx in (1, -1):
            end = v3(sx * pts[-1][0], pts[-1][1], pts[-1][2])
            prev = v3(sx * pts[-2][0], pts[-2][1], pts[-2][2])
            d = normalize(end - prev)
            g0 = end - d * 0.12
            cylinder(m, 'steer', SOLID, g0, end + d * 0.005, grip_r, 10, RUBBER)
            disc(m, 'steer', SOLID, end + d * 0.006, d, grip_r * 0.9, 10, CHROME)
            # lever
            lv0 = g0 + v3(0, 0.0, 0.03)
            lv1 = lv0 + d * 0.13 + v3(0, -0.015, 0.045)
            cylinder(m, 'steer', SOLID, lv0, lv1, 0.005, 5, ALU, caps=False)
            rbox(m, 'steer', SOLID, tuple(g0 - d * 0.02), (0.04, 0.035, 0.04), 0.008, BLACK)
        self.meta['grips'] = [float(pts[-1][0]) - 0.06 * np.sign(pts[-1][0]), float(pts[-1][1]), float(pts[-1][2])]

    def fork(self, top, bottom, spread, r, col_upper, col_lower, usd=False):
        """Telescopic fork from the top clamp to the front axle (both legs)."""
        top, bottom = v3(*top), v3(*bottom)
        d = bottom - top
        for sx in (1, -1):
            t = top + v3(sx * spread, 0, 0)
            b = bottom + v3(sx * spread, 0, 0)
            mid = t + d * 0.55
            if usd:
                cylinder(self.m, 'steer', SOLID, t, mid, r * 1.35, 12, col_lower)
                cylinder(self.m, 'steer', SOLID, mid, b, r, 12, col_upper)
            else:
                cylinder(self.m, 'steer', SOLID, t, mid, r, 12, col_upper)
                cylinder(self.m, 'steer', SOLID, mid - d * 0.02, b, r * 1.3, 12, col_lower)
            cylinder(self.m, 'steer', SOLID, b + v3(sx * 0.012, 0, 0), b - v3(sx * 0.03, 0, 0), 0.02, 8, ALU)
        # clamps
        for p, h in ((top, 0.035), (top + d * 0.12, 0.03)):
            rbox(self.m, 'steer', SOLID, tuple(p), (spread * 2 + r * 2.6, h, 0.07), 0.015, ALU)

    def exhaust_tip(self, p, d):
        self.meta.setdefault('exhausts', []).append([float(p[0]), float(p[1]), float(p[2]), float(d[0]), float(d[1]), float(d[2])])

    def seats(self, rider, pillion):
        self.meta['seats'] = [rider, pillion]

    # ---------------------------------------------------------------- frame / engine helpers
    def head_axis(self):
        S = self.S
        zf, R = S['WB'] / 2, S['R']
        rake = math.radians(S['rake'])
        axis = normalize(v3(0, math.cos(rake), -math.sin(rake)))  # pointing up and back
        # steering axis passes through the front axle offset by the trail
        head = v3(0, R, zf) + axis * S['fork_len'] + v3(0, 0, -S.get('offset', 0.03))
        return head, axis

    def finish(self):
        S = self.S
        R, WB = S['R'], S['WB']
        head, axis = self.head_axis()
        self.meta['steer'] = {'pivot': [float(x) for x in head], 'axis': [float(x) for x in axis]}
        self.meta['wheels'] = [{'x': 0.0, 'y': R, 'z': round(-WB / 2, 3), 'r': R, 'steer': False, 'mirror': False},
                               {'x': 0.0, 'y': R, 'z': round(WB / 2, 3), 'r': R, 'steer': True, 'mirror': False}]
        self.meta['wheelbase'] = WB
        for k in ('maxSpeed', 'accel', 'brake', 'mass', 'fuel', 'spawnWeight', 'colors', 'rider', 'backfire'):
            if k in S:
                self.meta[k] = S[k]
        self.meta['seats_n'] = 2
        self.meta['filler'] = [0.0, S['tank_top'] + 0.02, S['tank_z']]
        self.meta['beams'] = self.meta.get('beams') or [[0.0, 0.9, WB / 2 + 0.3]]
        self.meta['doors'] = {}
        wm = Mesh()
        bike_wheel(wm, R, S['TW'], S['rim'], rgb(S.get('rimcol', '#1a1a1c')), lod=self.lod, disc_both=S.get('twin_disc', True), knobby=S.get('tyre') == 'knobby')
        self.meta['tris'] = self.m.count() + wm.count() * 2
        return self.m, wm, self.meta

    # ================================================================= cruiser
    def cruiser(self):
        S, m, sg = self.S, self.m, self.seg
        R, WB = S['R'], S['WB']
        zr, zf = -WB / 2, WB / 2
        head, axis = self.head_axis()
        # --- frame: double cradle, chrome-black
        fc = DARK
        tube(m, 'body', [(0.09, head[1] - 0.05, head[2] - 0.02), (0.1, 0.32, zf - 0.55), (0.1, 0.17, zf - 0.85), (0.1, 0.17, zr + 0.55),
                         (0.11, 0.36, zr + 0.28), (0.12, 0.6, zr + 0.06)], 0.018, fc)
        tube(m, 'body', [(-0.09, head[1] - 0.05, head[2] - 0.02), (-0.1, 0.32, zf - 0.55), (-0.1, 0.17, zf - 0.85), (-0.1, 0.17, zr + 0.55),
                         (-0.11, 0.36, zr + 0.28), (-0.12, 0.6, zr + 0.06)], 0.018, fc)
        tube(m, 'body', [(0, head[1] + 0.02, head[2] - 0.02), (0, 0.66, zr + 0.62), (0.11, 0.6, zr + 0.06)], 0.022, fc)
        tube(m, 'body', [(0, 0.66, zr + 0.62), (-0.11, 0.6, zr + 0.06)], 0.02, fc)
        cylinder(m, 'body', SOLID, head - axis * 0.1, head + axis * 0.05, 0.035, 12, fc)
        # swingarm + twin shocks
        for sx in (1, -1):
            tube(m, 'body', [(sx * 0.13, 0.34, zr + 0.55), (sx * 0.13, R, zr)], 0.022, fc)
            cylinder(m, 'body', SOLID, v3(sx * 0.14, R + 0.04, zr + 0.08), v3(sx * 0.14, 0.6, zr + 0.18), 0.022, 10, CHROME)
            torus(m, 'body', SOLID, v3(sx * 0.14, R + 0.2, zr + 0.13), v3(0, 0.93, 0.36), 0.03, 0.008, 12, 5, CHROME) if not self.lod else None
        # --- V-twin engine (45 degrees), polished covers
        cz = zf - 0.82
        rbox(m, 'body', SOLID, (0, 0.3, cz), (0.24, 0.2, 0.42), 0.05, ENGINE_GREY)
        revolve(m, 'body', SOLID, [(0.0, 0.12), (0.13, 0.12), (0.14, 0.135), (0.13, 0.15), (0.0, 0.15)], 20, CHROME, axis='x', center=(0, 0.3, cz))
        revolve(m, 'body', SOLID, [(0.0, -0.12), (0.11, -0.12), (0.12, -0.14), (0.0, -0.15)], 20, CHROME, axis='x', center=(0, 0.3, cz + 0.05))
        for ang, dz in ((0.39, 0.09), (-0.39, -0.09)):
            base = v3(0, 0.38, cz + dz)
            top = base + v3(0, math.cos(ang), math.sin(ang)) * 0.24
            finned_cylinder(m, 'body', base, top, 0.06, ENGINE_GREY, 8, rgb('#5a5d62'), self.lod)
            rbox(m, 'body', SOLID, tuple(top + v3(0, 0.02, 0)), (0.13, 0.05, 0.13), 0.025, CHROME)
        # air cleaner (right side, chrome oval)
        revolve(m, 'body', SOLID, [(0.0, -0.03), (0.08, -0.03), (0.09, 0.0), (0.08, 0.025), (0.0, 0.03)], 18, CHROME, axis='x',
                center=(-0.15, 0.52, cz))
        # primary cover left
        rbox(m, 'body', SOLID, (0.16, 0.24, cz - 0.28), (0.04, 0.16, 0.36), 0.05, CHROME)
        # --- teardrop tank
        tz, tt = S['tank_z'], S['tank_top']
        loft(m, 'body', PAINT, [(tz - 0.3, tt - 0.12, 0.05, 0.05, 2.2), (tz - 0.22, tt - 0.05, 0.14, 0.1, 2.4), (tz, tt - 0.065, 0.17, 0.12, 2.6),
                                (tz + 0.18, tt - 0.08, 0.15, 0.1, 2.5), (tz + 0.3, tt - 0.11, 0.08, 0.06, 2.3)], WHITE, sg)
        # tank stripe + chrome console with speedo
        for sx in (1, -1):
            revolve(m, 'body', SOLID, [(0.0, 0.0), (0.035, 0.0), (0.04, 0.006), (0.0, 0.008)], 14, CHROME, axis='x',
                    center=(sx * 0.165, tt - 0.07, tz + 0.02)) if sx > 0 else revolve(m, 'body', SOLID, [(0.0, 0.0), (0.035, 0.0), (0.04, -0.006), (0.0, -0.008)],
                                                                                         14, CHROME, axis='x', center=(-0.165, tt - 0.07, tz + 0.02))
        rbox(m, 'body', SOLID, (0, tt + 0.002, tz - 0.02), (0.07, 0.02, 0.34), 0.01, CHROME)
        revolve(m, 'body', SOLID, [(0.0, -0.018), (0.065, -0.018), (0.07, 0.0), (0.066, 0.008)], 20, CHROME, axis='y', center=(0, tt + 0.01, tz - 0.06))
        self.cluster('body', (0, tt + 0.021, tz - 0.06), (0, 1, 0.25), 0.13, 0.065)
        # --- seat (low, stepped) + rear fender
        st = S['seat_top']
        loft(m, 'body', SOLID, [(zr + 0.02, st + 0.02, 0.09, 0.035, 2.2), (zr + 0.14, st + 0.03, 0.14, 0.045, 2.6), (zr + 0.28, st + 0.03, 0.13, 0.04, 2.8),
                                (zr + 0.36, st - 0.04, 0.17, 0.06, 3.0), (zr + 0.52, st - 0.05, 0.18, 0.065, 3.0), (zr + 0.6, st - 0.01, 0.1, 0.04, 2.4)],
             rgb('#2b211b'), sg, uvr=UV['leather'])
        arc_strip(m, 'body', PAINT, 0, R, zr, R + 0.06, math.radians(15), math.radians(195), 0.26, 0.012, WHITE, 16, 0.05)
        arc_strip(m, 'body', PAINT, 0, R, zf, R + 0.05, math.radians(25), math.radians(165), 0.17, 0.01, WHITE, 12, 0.03)
        # tail: round "cat-eye" tail/brake + plate
        tl = v3(0, R + 0.2, zr - R - 0.03)
        revolve(m, 'body', SOLID, [(0.0, -0.04), (0.045, -0.04), (0.05, 0.0), (0.0, 0.0)], 14, CHROME, axis='z', center=tuple(tl))
        disc(m, 'body', TAIL, tl + v3(0, 0, -0.042), v3(0, 0, -1), 0.04, 14, WHITE)
        disc(m, 'body', BRAKE, tl + v3(0, 0, -0.043), v3(0, 0, -1), 0.022, 14, WHITE)
        self.plate(tl + v3(0, -0.13, 0.0), (0, 0.15, -1), 0.2, 0.12)
        disc(m, 'body', PLATE_LIGHT, tl + v3(0, -0.055, -0.03), v3(0, -0.6, -0.8), 0.012, 8, WHITE)
        for sx, mat in ((1, IND_L), (-1, IND_R)):
            self.indicator('body', tl + v3(sx * 0.12, 0.0, 0.1), (sx, 0.1, -0.3), mat, 0.07, 0.022)
        # --- staggered dual exhaust, right side
        for i, (y0, y1) in enumerate(((0.47, 0.25), (0.62, 0.36))):
            dz = 0.09 if i else -0.09
            tube(m, 'body', [(-0.05, 0.5 + i * 0.06, cz + dz), (-0.15, y0, cz + dz - 0.05), (-0.19, y1, cz - 0.32), (-0.2, y1 - 0.03, zr + 0.05)], 0.025,
                 CHROME)
            p1 = v3(-0.2, y1 - 0.03, zr + 0.05)
            p2 = p1 + v3(0, 0.015, -0.33)
            cylinder(m, 'body', SOLID, p1, p2, 0.042, 14, CHROME)
            disc(m, 'body', SOLID, p2 + v3(0, 0, -0.002), v3(0, 0, -1), 0.032, 12, BLACK)
            self.exhaust_tip(p2, (0, 0.05, -1))
        # floorboards / forward controls
        for sx in (1, -1):
            rbox(m, 'body', SOLID, (sx * 0.25, 0.27, zf - 0.6), (0.12, 0.02, 0.26), 0.008, BLACK)
        # --- front end: wide fork, big round headlight, pullback bars, spotlights
        bot = v3(0, R, zf)
        self.fork(head + axis * 0.06, bot, 0.1, 0.022, CHROME, CHROME)
        nacelle = head + axis * -0.02 + v3(0, -0.06, 0.12)
        rbox(m, 'steer', SOLID, tuple(nacelle + v3(0, 0.0, -0.04)), (0.26, 0.1, 0.12), 0.04, CHROME)
        self.lamp_round('steer', nacelle + v3(0, -0.02, 0.1), (0, 0, 1), 0.095, 0.12, CHROME, HEAD, True)
        for sx in (1, -1):
            self.lamp_round('steer', nacelle + v3(sx * 0.17, -0.13, 0.06), (0, 0, 1), 0.045, 0.06, CHROME, HIGH, False)
            self.indicator('steer', nacelle + v3(sx * 0.12, 0.03, 0.0), (sx, 0, 0.4), IND_L if sx > 0 else IND_R, 0.06, 0.02)
        top = head + axis * 0.08
        for sx in (1, -1):
            cylinder(m, 'steer', SOLID, v3(sx * 0.06, top[1] - 0.02, top[2]), v3(sx * 0.06, top[1] + 0.03, top[2] - 0.01), 0.016, 8, CHROME)
        self.bars([(0, top[1] + 0.04, top[2] - 0.01), (0.1, top[1] + 0.04, top[2] - 0.01), (0.2, top[1] + 0.07, top[2] - 0.08),
                   (0.33, top[1] + 0.07, top[2] - 0.22), (0.4, top[1] + 0.05, top[2] - 0.3)])
        self.mirrors('steer', (0.25, top[1] + 0.075, top[2] - 0.13), (0.05, 0.13, 0.02), 0.045, CHROME)
        self.meta['beams'] = [[0.0, float(nacelle[1] - 0.02), float(nacelle[2] + 0.12)]]
        # details: belt drive, side stand, horn, oil cooler, battery side cover, sissy-bar style grab rail
        self.chain(0.12, (0.3, cz - 0.32), 0.05, (R, zr), 0.12)
        tube(m, 'body', [(0.12, 0.2, -0.05), (0.15, 0.17, -0.36)], 0.012, DARK)
        revolve(m, 'body', SOLID, [(0.0, 0.0), (0.04, 0.0), (0.045, 0.02), (0.0, 0.025)], 14, CHROME, axis='z', center=(-0.1, 0.45, cz + 0.32))
        rbox(m, 'body', SOLID, (0, 0.2, cz + 0.33), (0.2, 0.08, 0.04), 0.01, rgb('#202124'))
        for k in range(5 if not self.lod else 0):
            box(m, 'body', SOLID, (0, 0.17 + k * 0.016, cz + 0.355), (0.18, 0.004, 0.006), CHROME)
        loft(m, 'body', PAINT, [(zr + 0.4, 0.42, 0.13, 0.09, 2.4), (zr + 0.62, 0.44, 0.14, 0.1, 2.6)], WHITE, sg)
        for sx in (1, -1):
            tube(m, 'body', [(sx * 0.14, st + 0.0, zr + 0.25), (sx * 0.15, st + 0.12, zr + 0.05), (sx * 0.1, st + 0.16, zr - 0.02)], 0.011, CHROME)
        tube(m, 'body', [(0.1, st + 0.16, zr - 0.02), (-0.1, st + 0.16, zr - 0.02)], 0.011, CHROME)
        self.seats([0.0, st - 0.05 - 0.2, zr + 0.46], [0.0, st + 0.03 - 0.2, zr + 0.16])
        return self.finish()

    # ================================================================= supersport
    def sport(self):
        S, m, sg = self.S, self.m, self.seg
        R, WB = S['R'], S['WB']
        zr, zf = -WB / 2, WB / 2
        head, axis = self.head_axis()
        nz = 14 if self.lod else 26
        # ------------------------------------------------ chassis: twin-spar frame, banana swingarm, mono shock
        for sx in (1, -1):
            spar = [v3(sx * 0.07, head[1] - 0.03, head[2] - 0.03), v3(sx * 0.16, 0.66, 0.12), v3(sx * 0.16, 0.56, -0.18), v3(sx * 0.12, 0.36, -0.24)]
            for a, b in zip(spar[:-1], spar[1:]):
                bar(m, 'body', SOLID, a, b, 0.035, 0.085, ALU, up=(0, 1, 0))
            bar(m, 'body', SOLID, v3(sx * 0.12, 0.36, -0.22), v3(sx * 0.1, R + 0.02, zr), 0.04, 0.09, ALU, up=(0, 1, 0))
            bar(m, 'body', SOLID, v3(sx * 0.11, 0.62, -0.2), v3(sx * 0.07, 0.86, -0.6), 0.02, 0.025, BLACK)   # subframe
        cylinder(m, 'body', SOLID, head - axis * 0.11, head + axis * 0.05, 0.034, 12, ALU)
        cylinder(m, 'body', SOLID, v3(0, 0.34, -0.3), v3(0, 0.6, -0.16), 0.028, 10, rgb('#d8a020'))
        for k in range(6 if not self.lod else 0):
            torus(m, 'body', SOLID, v3(0, 0.38 + k * 0.035, -0.28 + k * 0.019), v3(0, 0.88, 0.47), 0.034, 0.006, 10, 4, rgb('#d8a020'))
        # ------------------------------------------------ engine: inline four, radiator, headers into a side-exit can
        rbox(m, 'body', SOLID, (0, 0.3, 0.03), (0.32, 0.22, 0.36), 0.04, rgb('#232427'))
        rbox(m, 'body', SOLID, (0, 0.5, 0.14), (0.3, 0.16, 0.18), 0.03, rgb('#2c2d31'),
             rot=np.array([[1, 0, 0], [0, math.cos(-0.45), -math.sin(-0.45)], [0, math.sin(-0.45), math.cos(-0.45)]]))
        revolve(m, 'body', SOLID, [(0.0, 0.16), (0.09, 0.16), (0.1, 0.175), (0.0, 0.18)], 16, rgb('#3b3d41'), axis='x', center=(0, 0.28, 0.0))
        # curved radiator behind the front wheel
        rbox(m, 'body', SOLID, (0, 0.56, 0.33), (0.36, 0.26, 0.05), 0.012, rgb('#111213'),
             rot=np.array([[1, 0, 0], [0, math.cos(-0.3), -math.sin(-0.3)], [0, math.sin(-0.3), math.cos(-0.3)]]))
        for i in range(4):
            x = -0.105 + i * 0.07
            tube(m, 'body', [(x, 0.47, 0.24), (x, 0.3, 0.31), (x * 0.5, 0.15, 0.2), (0.0, 0.13, -0.05)], 0.016, rgb('#9a8676'))
        tube(m, 'body', [(0.0, 0.13, -0.05), (-0.08, 0.17, -0.3), (-0.14, 0.27, -0.46)], 0.03, rgb('#9a8676'))
        # hexagonal titanium-look can, angled up
        can = [(-0.46, 0.27, 0.06, 0.07, 6.0, -0.155), (-0.58, 0.3, 0.068, 0.075, 6.0, -0.165), (-0.74, 0.35, 0.06, 0.066, 6.0, -0.17),
               (-0.78, 0.365, 0.045, 0.05, 6.0, -0.17)]
        loft(m, 'body', SOLID, can, rgb('#5e6670'), 12)
        loft(m, 'body', SOLID, [(-0.785, 0.367, 0.046, 0.051, 6.0, -0.17), (-0.79, 0.368, 0.04, 0.045, 6.0, -0.17)], rgb('#1a1a1c'), 12)
        tip = v3(-0.17, 0.37, -0.795)
        disc(m, 'body', SOLID, tip, v3(0, 0.28, -1), 0.028, 12, BLACK)
        self.exhaust_tip(tip, (0, 0.28, -1))
        # ------------------------------------------------ bodywork made of panels (open underneath)
        nose = zf + 0.2
        cowl = dict(top=[(0.36, 0.95), (0.5, 0.9), (0.68, 0.84), (0.84, 0.79), (nose, 0.755)],
                    bot=[(0.36, 0.63), (0.5, 0.64), (0.68, 0.67), (0.84, 0.71), (nose, 0.745)],
                    hw=[(0.36, 0.2), (0.5, 0.19), (0.68, 0.15), (0.84, 0.09), (nose, 0.012)])
        self.shell('body', PAINT, cowl, 0.36, nose, nz, 3.0, WHITE)
        sides = dict(top=[(-0.08, 0.74), (0.1, 0.78), (0.3, 0.85), (0.42, 0.9)], bot=[(-0.08, 0.36), (0.08, 0.24), (0.3, 0.27), (0.42, 0.52)],
                     hw=[(-0.08, 0.15), (0.1, 0.19), (0.3, 0.205), (0.42, 0.2)])
        self.shell('body', PAINT, sides, -0.08, 0.42, nz, 3.2, WHITE)
        belly = dict(top=[(-0.1, 0.3), (0.3, 0.3)], bot=[(-0.1, 0.12), (0.05, 0.1), (0.3, 0.16)], hw=[(-0.1, 0.12), (0.1, 0.16), (0.3, 0.17)])
        self.shell('body', PAINT2, belly, -0.1, 0.3, 8, 2.4, WHITE, under=True)
        # contrasting side stripes + vents (gills)
        self.patch('body', PAINT2, sides, 0.0, 0.36, 0.08, 0.14, 0.004, WHITE)
        for k in range(3):
            z0 = 0.08 + k * 0.07
            self.patch('body', SOLID, sides, z0, z0 + 0.04, 0.2, 0.34, 0.005, BLACK)
        # screen
        scr = dict(top=[(0.4, 1.02), (0.56, 0.96), (0.7, 0.875)], bot=[(0.4, 0.935), (0.56, 0.895), (0.7, 0.845)],
                   hw=[(0.4, 0.15), (0.56, 0.13), (0.7, 0.1)])
        self.shell('body', GLASS, scr, 0.4, 0.7, 10, 2.2, rgb('#283238'))
        # headlights: twin slit eyes with LED brows + main beams below, ram-air intake in the nose
        for side in (1, -1):
            self.patch('body', HEAD, cowl, 0.8, nose - 0.025, 0.36, 0.62, 0.004, WHITE, side=side)
            self.patch('body', DRL, cowl, 0.76, nose - 0.03, 0.66, 0.74, 0.005, WHITE, side=side)
            self.patch('body', HIGH, cowl, 0.72, 0.84, 0.18, 0.34, 0.004, WHITE, side=side)
            self.patch('body', SOLID, cowl, 0.58, 0.72, 0.0, 0.16, 0.004, BLACK, side=side)      # duct
            self.indicator('body', (side * 0.19, 0.76, 0.46), (side, 0, 0.25), IND_L if side > 0 else IND_R, 0.03, lens='flat')
        rbox(m, 'body', SOLID, (0, 0.735, nose - 0.035), (0.06, 0.03, 0.06), 0.008, BLACK)
        self.mirrors('body', (0.18, 0.86, 0.5), (0.07, 0.03, 0.0), 0.0, WHITE, shape='flat')
        # tank with alloy filler cap, seat, tail with LED light and pillion cover
        tank = dict(top=[(-0.16, 0.88), (0.0, 0.97), (0.2, 1.0), (0.38, 0.95)], bot=[(-0.16, 0.74), (0.38, 0.8)],
                    hw=[(-0.16, 0.13), (0.05, 0.18), (0.25, 0.17), (0.38, 0.12)])
        self.shell('body', PAINT, tank, -0.16, 0.38, nz, 3.0, WHITE)
        self.patch('body', SOLID, tank, -0.1, 0.06, 0.25, 0.55, 0.004, rgb('#1a1a1c'))   # knee grip
        revolve(m, 'body', SOLID, [(0.0, 0.0), (0.045, 0.0), (0.05, 0.008), (0.0, 0.012)], 16, ALU, axis='y', center=(0, 0.995, 0.22))
        seat = dict(top=[(-0.46, 0.835), (-0.3, 0.83), (-0.15, 0.86)], bot=[(-0.46, 0.74), (-0.15, 0.78)], hw=[(-0.46, 0.12), (-0.3, 0.14), (-0.15, 0.13)])
        self.shell('body', SOLID, seat, -0.46, -0.15, 10, 3.0, rgb('#1a1a1c'), uvr=UV['leather'])
        tail = dict(top=[(-0.9, 0.92), (-0.75, 0.95), (-0.55, 0.92), (-0.42, 0.86)], bot=[(-0.9, 0.88), (-0.7, 0.8), (-0.42, 0.72)],
                    hw=[(-0.9, 0.025), (-0.78, 0.07), (-0.6, 0.11), (-0.42, 0.14)])
        self.shell('body', PAINT, tail, -0.9, -0.42, nz, 3.0, WHITE)
        self.patch('body', PAINT2, tail, -0.72, -0.48, 0.62, 1.0, 0.004, WHITE)   # pillion cover
        for side in (1, -1):
            self.patch('body', TAIL, tail, -0.88, -0.8, 0.3, 0.7, 0.004, WHITE, side=side)
            self.patch('body', BRAKE, tail, -0.88, -0.83, 0.72, 0.95, 0.004, WHITE, side=side)
        # under-tail hugger, plate hanger, indicators
        arc_strip(m, 'body', SOLID, 0, R, zr, R + 0.03, math.radians(50), math.radians(115), 0.16, 0.01, BLACK, 8)
        hanger = v3(0, 0.66, -0.98)
        tube(m, 'body', [(0, 0.8, -0.78), tuple(hanger)], 0.012, BLACK)
        self.plate(hanger + v3(0, -0.06, -0.01), (0, 0.35, -1), 0.18, 0.11)
        disc(m, 'body', PLATE_LIGHT, hanger + v3(0, 0.0, -0.02), v3(0, -0.4, -1), 0.01, 8, WHITE)
        for sx, mat in ((1, IND_L), (-1, IND_R)):
            self.indicator('body', hanger + v3(sx * 0.1, 0.0, 0.0), (sx, 0, -0.2), mat, 0.05, lens='flat')
        arc_strip(m, 'body', PAINT, 0, R, zf, R + 0.035, math.radians(45), math.radians(135), 0.13, 0.008, WHITE, 10, 0.02)
        # rear sets with shifter / brake lever, side stand (folded)
        for sx in (1, -1):
            cylinder(m, 'body', SOLID, v3(sx * 0.12, 0.36, -0.3), v3(sx * 0.21, 0.36, -0.3), 0.011, 6, ALU)
            bar(m, 'body', SOLID, v3(sx * 0.13, 0.42, -0.2), v3(sx * 0.13, 0.36, -0.3), 0.03, 0.01, ALU, up=(1, 0, 0))
            cylinder(m, 'body', SOLID, v3(sx * 0.17, 0.36, -0.3), v3(sx * 0.17, 0.33, -0.16), 0.006, 5, BLACK, caps=False)
        tube(m, 'body', [(0.12, 0.22, -0.18), (0.19, 0.19, -0.45)], 0.01, BLACK)
        self.chain(0.115, (0.3, -0.12), 0.045, (R, zr), 0.1)
        # dash TFT on a carbon bracket
        rbox(m, 'body', SOLID, (0, 0.905, 0.43), (0.19, 0.11, 0.03), 0.01, BLACK,
             rot=np.array([[1, 0, 0], [0, math.cos(0.98), -math.sin(0.98)], [0, math.sin(0.98), math.cos(0.98)]]))
        self.cluster('body', (0, 0.915, 0.42), (0, 0.55, -0.83), 0.15, 0.075)
        # ------------------------------------------------ front end: gold USD fork, radial calipers, clip-ons
        bot = v3(0, R, zf)
        self.fork(head + axis * 0.04, bot, 0.085, 0.024, rgb('#d8b040'), rgb('#2c2d30'), usd=True)
        top = head + axis * 0.03
        self.bars([(0.085, top[1] - 0.03, top[2]), (0.15, top[1] - 0.045, top[2] - 0.04), (0.26, top[1] - 0.06, top[2] - 0.1)], col=BLACK)
        rbox(m, 'steer', SOLID, (0.13, top[1] - 0.0, top[2] - 0.03), (0.035, 0.03, 0.03), 0.006, rgb('#202022'))   # brake reservoir
        self.meta['beams'] = [[0.07, 0.775, float(nose - 0.06)], [-0.07, 0.775, float(nose - 0.06)]]
        self.seats([0.0, 0.83 - 0.04 - 0.2, zr + 0.43], [0.0, 0.93 - 0.2, zr + 0.12])
        return self.finish()

    # ---------------------------------------------------------------- panel shells
    def _section(self, spec, z, phi, n, side, under=False):
        """Point on an arch-shaped panel section: phi 0 = lower edge, 1 = top centre (or bottom centre for under-pans)."""
        top, bot, hw = pchip(spec['top'])(z), pchip(spec['bot'])(z), pchip(spec['hw'])(z)
        a = phi * math.pi / 2
        c, s = math.cos(a), math.sin(a)
        x = hw * (abs(c) ** (2.0 / n)) * (0.9 + 0.1 * min(1.0, phi / 0.35))
        y = abs(s) ** (2.0 / n)
        if under:
            yy = top - (top - bot) * y
        else:
            yy = bot + (top - bot) * y
        return v3(side * x, yy, z)

    def shell(self, part, mat, spec, z0, z1, nz, n, col, uvr=None, under=False):
        nphi = 9 if self.lod else 14
        for side in (1, -1):
            grid = [[self._section(spec, z0 + (z1 - z0) * i / nz, j / nphi, n, side, under) for j in range(nphi + 1)] for i in range(nz + 1)]
            for i in range(nz):
                for j in range(nphi):
                    zc = z0 + (z1 - z0) * (i + 0.5) / nz
                    inside = v3(0, (pchip(spec['top'])(zc) + pchip(spec['bot'])(zc)) / 2 if not under else pchip(spec['top'])(zc) + 0.3, zc)
                    if not under:
                        inside = v3(0, pchip(spec['bot'])(zc) - 0.02, zc)
                    oquad(self.m, part, mat, grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1], col, inside, uvr=uvr)
        # close the nose / tail ends if they taper to a point the grid already meets at x~0

    def patch(self, part, mat, spec, z0, z1, p0, p1, off, col, side=0, n=3.0, nz=4, nphi=3):
        """Conformal decal (lamp lens, stripe, vent) lying on a shell surface, lifted by 'off'."""
        for sd in ((1, -1) if side == 0 else (side,)):
            def pt(z, ph):
                q = self._section(spec, z, ph, n, sd)
                e = self._section(spec, z, min(1, ph + 0.01), n, sd) - self._section(spec, z, max(0, ph - 0.01), n, sd)
                f = v3(0, 0, 1)
                nrm = normalize(np.cross(e, f)) * (1 if sd > 0 else -1)
                if nrm[0] * sd < 0 and nrm[1] < 0:
                    nrm = -nrm
                return q + nrm * off
            g = [[pt(z0 + (z1 - z0) * i / nz, p0 + (p1 - p0) * j / nphi) for j in range(nphi + 1)] for i in range(nz + 1)]
            c = v3(0, pchip(spec['bot'])((z0 + z1) / 2) - 0.05, (z0 + z1) / 2)
            for i in range(nz):
                for j in range(nphi):
                    oquad(self.m, part, mat, g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1], col, c)

    def chain(self, x, front, rf, rear, rr):
        """Final drive: sprockets + chain runs on the left (x > 0)."""
        (fy, fz), (ry, rz) = front, rear
        for (cy, cz, r) in ((fy, fz, rf), (ry, rz, rr)):
            revolve(self.m, 'body', SOLID, [(0.0, -0.004), (r, -0.004), (r, 0.004), (0.0, 0.004)], 18, ALU if r > 0.06 else DARK, axis='x', center=(x, cy, cz))
        for sgn in (1, -1):
            bar(self.m, 'body', SOLID, v3(x, fy + sgn * rf, fz), v3(x, ry + sgn * rr, rz), 0.012, 0.01, rgb('#3a3a3c'), up=(1, 0, 0))

    # ================================================================= scrambler
    def scrambler(self):
        S, m, sg = self.S, self.m, self.seg
        R, WB = S['R'], S['WB']
        zr, zf = -WB / 2, WB / 2
        head, axis = self.head_axis()
        fc = rgb('#2a2c2f')
        # --- steel trellis-ish frame
        for sx in (1, -1):
            tube(m, 'body', [(sx * 0.06, head[1] - 0.06, head[2] - 0.03), (sx * 0.1, 0.32, zf - 0.6), (sx * 0.1, 0.22, zr + 0.62), (sx * 0.12, 0.42, zr + 0.5),
                             (sx * 0.1, 0.8, zr + 0.25)], 0.016, fc)
            tube(m, 'body', [(sx * 0.12, 0.42, zr + 0.5), (sx * 0.12, R, zr)], 0.02, fc)
            cylinder(m, 'body', SOLID, v3(sx * 0.13, R + 0.05, zr + 0.06), v3(sx * 0.11, 0.78, zr + 0.25), 0.02, 10, rgb('#c8202a'))
        tube(m, 'body', [(0, head[1] + 0.01, head[2] - 0.03), (0, 0.76, zr + 0.62), (0.1, 0.8, zr + 0.25)], 0.02, fc)
        tube(m, 'body', [(0, 0.76, zr + 0.62), (-0.1, 0.8, zr + 0.25)], 0.018, fc)
        cylinder(m, 'body', SOLID, head - axis * 0.1, head + axis * 0.05, 0.032, 12, fc)
        # --- single cylinder engine, tall finned barrel
        cz = zf - 0.7
        rbox(m, 'body', SOLID, (0, 0.33, cz - 0.04), (0.2, 0.22, 0.3), 0.04, rgb('#3d4045'))
        revolve(m, 'body', SOLID, [(0.0, 0.1), (0.1, 0.1), (0.11, 0.12), (0.0, 0.125)], 18, POLISH, axis='x', center=(0, 0.32, cz - 0.04))
        finned_cylinder(m, 'body', (0, 0.42, cz + 0.02), (0, 0.66, cz + 0.08), 0.075, rgb('#4a4d52'), 9, rgb('#5d6066'), self.lod)
        rbox(m, 'body', SOLID, (0, 0.7, cz + 0.09), (0.15, 0.06, 0.15), 0.02, POLISH)
        # upswept high pipe on the right with heat shield
        tube(m, 'body', [(0, 0.6, cz + 0.17), (-0.04, 0.44, cz + 0.22), (-0.13, 0.38, cz + 0.05), (-0.16, 0.56, zr + 0.55), (-0.16, 0.66, zr + 0.25)],
             0.024, rgb('#9a8e80'))
        p1 = v3(-0.16, 0.66, zr + 0.25)
        p2 = v3(-0.16, 0.72, zr - 0.02)
        cylinder(m, 'body', SOLID, p1, p2, 0.04, 14, rgb('#2a2b2d'))
        rbox(m, 'body', SOLID, (-0.205, 0.68, zr + 0.18), (0.012, 0.07, 0.24), 0.006, rgb('#b8bcc2'))
        disc(m, 'body', SOLID, p2 + v3(0, -0.002, -0.004), normalize(v3(0, 0.2, -1)), 0.03, 12, BLACK)
        self.exhaust_tip(p2, (0, 0.2, -1))
        # --- angular tank with knee pads, flat ribbed seat, short tail
        tz, tt = S['tank_z'], S['tank_top']
        loft(m, 'body', PAINT, [(tz - 0.24, tt - 0.09, 0.12, 0.07, 3.6), (tz - 0.08, tt - 0.03, 0.17, 0.11, 4.0), (tz + 0.14, tt - 0.04, 0.16, 0.11, 4.0),
                                (tz + 0.26, tt - 0.09, 0.1, 0.07, 3.4)], WHITE, sg)
        for sx in (1, -1):
            rbox(m, 'body', SOLID, (sx * 0.165, tt - 0.07, tz - 0.08), (0.012, 0.08, 0.12), 0.01, rgb('#1d1d1e'))
        st = S['seat_top']
        loft(m, 'body', SOLID, [(zr + 0.08, st - 0.02, 0.1, 0.03, 4.0), (zr + 0.3, st, 0.13, 0.04, 4.0), (zr + 0.52, st - 0.01, 0.15, 0.04, 4.0),
                                (zr + 0.6, st - 0.04, 0.1, 0.03, 3.0)], rgb('#3a2a20'), sg, uvr=UV['leather'])
        for k in range(5 if not self.lod else 0):
            box(m, 'body', SOLID, (0, st + 0.035, zr + 0.14 + k * 0.09), (0.22, 0.006, 0.01), rgb('#2a1e17'))
        loft(m, 'body', PAINT2, [(zr - 0.05, st - 0.06, 0.04, 0.03, 2.4), (zr + 0.15, st - 0.06, 0.1, 0.05, 2.6)], WHITE, sg)
        # high fenders
        arc_strip(m, 'body', PAINT2, 0, R, zr, R + 0.09, math.radians(55), math.radians(150), 0.18, 0.01, WHITE, 10, 0.03)
        # tail: small rectangular LED + plate on a hugger
        tl = v3(0, st - 0.07, zr - 0.07)
        rbox(m, 'body', SOLID, tuple(tl + v3(0, 0, 0.01)), (0.09, 0.04, 0.03), 0.008, BLACK)
        oquad(m, 'body', TAIL, tl + v3(0.035, -0.012, -0.007), tl + v3(-0.035, -0.012, -0.007), tl + v3(-0.035, 0.012, -0.007),
              tl + v3(0.035, 0.012, -0.007), WHITE, tl)
        oquad(m, 'body', BRAKE, tl + v3(0.02, -0.006, -0.008), tl + v3(-0.02, -0.006, -0.008), tl + v3(-0.02, 0.006, -0.008),
              tl + v3(0.02, 0.006, -0.008), WHITE, tl)
        self.plate(tl + v3(0, -0.14, -0.06), (0, 0.2, -1), 0.18, 0.11)
        for sx, mat in ((1, IND_L), (-1, IND_R)):
            self.indicator('body', tl + v3(sx * 0.07, -0.03, 0), (sx, 0, -0.1), mat, 0.05, 0.016)
        # skid plate + pegs
        rbox(m, 'body', SOLID, (0, 0.17, cz - 0.02), (0.18, 0.015, 0.34), 0.006, ALU)
        for sx in (1, -1):
            box(m, 'body', SOLID, (sx * 0.16, 0.3, zr + 0.74), (0.1, 0.018, 0.035), ALU)
        # --- front: tall fork with gaiters, round lamp with cross guard and LED ring, wide braced bars
        bot = v3(0, R, zf)
        self.fork(head + axis * 0.06, bot, 0.085, 0.022, rgb('#2a2b2e'), rgb('#2a2b2e'))
        for sx in (1, -1):
            mid = head + (bot - head) * 0.62
            for k in range(6 if not self.lod else 0):
                cylinder(m, 'steer', SOLID, mid + v3(sx * 0.085, 0, 0) + (bot - head) * (k * 0.025), mid + v3(sx * 0.085, 0, 0) + (bot - head) * (k * 0.025 + 0.015),
                         0.031, 10, RUBBER)
        arc_strip(m, 'steer', PAINT2, 0, R, zf, R + 0.06, math.radians(30), math.radians(140), 0.15, 0.009, WHITE, 10, 0.025)
        lamp = head + v3(0, -0.06, 0.16)
        self.lamp_round('steer', lamp, (0, -0.05, 1), 0.085, 0.11, BLACK, HEAD, False)
        torus(m, 'steer', DRL, lamp + v3(0, -0.004, 0.012), v3(0, -0.05, 1), 0.07, 0.008, 20, 5, WHITE)
        for d in ((1, 1), (1, -1)):
            p0 = lamp + v3(0.075 * d[0], 0.075 * d[1], 0.02)
            p1 = lamp + v3(-0.075 * d[0], -0.075 * d[1], 0.02)
            cylinder(m, 'steer', SOLID, p0, p1, 0.005, 4, BLACK, caps=False)
        for sx in (1, -1):
            self.indicator('steer', lamp + v3(sx * 0.1, 0.02, -0.06), (sx, 0, 0.1), IND_L if sx > 0 else IND_R, 0.05, 0.016)
        top = head + axis * 0.08
        self.bars([(0, top[1] - 0.02, top[2]), (0.12, top[1] + 0.0, top[2] - 0.02), (0.3, top[1] + 0.03, top[2] - 0.1), (0.4, top[1] + 0.04, top[2] - 0.13)],
                  col=BLACK)
        cylinder(m, 'steer', SOLID, v3(0.13, top[1] + 0.03, top[2] - 0.03), v3(-0.13, top[1] + 0.03, top[2] - 0.03), 0.009, 6, BLACK)  # brace
        rbox(m, 'steer', SOLID, (0, top[1] + 0.03, top[2] - 0.03), (0.07, 0.03, 0.03), 0.008, rgb('#2a2b2e'))  # bar pad
        self.cluster('steer', (0, top[1] + 0.03, top[2] + 0.06), (0, 0.85, -0.5), 0.12, 0.06)
        rbox(m, 'steer', SOLID, (0.13, top[1] + 0.03, top[2] - 0.06), (0.035, 0.03, 0.03), 0.006, rgb('#202022'))   # reservoir
        self.mirrors('steer', (0.3, top[1] + 0.03, top[2] - 0.1), (0.03, 0.12, -0.02), 0.04, BLACK)
        self.meta['beams'] = [[0.0, float(lamp[1]), float(lamp[2] + 0.1)]]
        # details: chain + sprockets, shock springs, pod air filter, grab rail, side number boards, side stand, fork brace
        self.chain(0.11, (0.32, cz - 0.18), 0.045, (R, zr), 0.11)
        for sx in (1, -1):
            for k in range(9 if not self.lod else 0):
                t = k / 8
                p = v3(sx * 0.13, R + 0.08, zr + 0.08) + (v3(sx * 0.11, 0.74, zr + 0.23) - v3(sx * 0.13, R + 0.08, zr + 0.08)) * t
                torus(m, 'body', SOLID, p, v3(0, 0.8, 0.6), 0.034, 0.006, 10, 4, rgb('#c8202a'))
            rbox(m, 'body', PAINT2, (sx * 0.13, st - 0.17, zr + 0.42), (0.02, 0.16, 0.2), 0.03, WHITE)
        revolve(m, 'body', SOLID, [(0.0, 0.0), (0.05, 0.0), (0.06, 0.05), (0.055, 0.09), (0.0, 0.1)], 14, rgb('#a8323a'), axis='x', center=(0.07, 0.6, cz - 0.1))
        tube(m, 'body', [(0.1, st + 0.0, zr + 0.3), (0.1, st + 0.05, zr + 0.02), (-0.1, st + 0.05, zr + 0.02), (-0.1, st + 0.0, zr + 0.3)], 0.011, BLACK)
        tube(m, 'body', [(0.1, 0.2, -0.02), (0.13, 0.17, -0.33)], 0.012, BLACK)
        rbox(m, 'body', SOLID, (0, 0.6, cz - 0.02), (0.1, 0.12, 0.08), 0.02, rgb('#1f2022'))   # battery / electrics box
        bot2 = v3(0, R, zf)
        rbox(m, 'steer', SOLID, tuple(bot2 + v3(0, 0.25, -0.06)), (0.2, 0.025, 0.06), 0.01, BLACK)   # fork brace
        self.seats([0.0, st - 0.2, zr + 0.45], [0.0, st - 0.2, zr + 0.18])
        return self.finish()


def build(S, lod=0, verbose=False):
    b = BikeBuilder(S, lod)
    mesh, wm, meta = getattr(b, S['style'])()
    if verbose:
        print('   bike', S['id'], 'tris', meta['tris'])
    return mesh, wm, meta
