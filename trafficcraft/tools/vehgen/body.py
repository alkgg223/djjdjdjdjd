"""Lofted vehicle body: shaped lower shell with flared wheel wells and open cabin, greenhouse with seals/frit,
conformal decals and projected strips."""
import math
import numpy as np
from geom import *

T_WALL = 0.07  # door / panel thickness


def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a))) if b != a else 0.0
    return t * t * (3 - 2 * t)


class Body:
    def __init__(self, S, mesh, lod=0):
        self.S = S
        self.m = mesh
        self.lod = lod
        self.L = S['L']
        self.hw = S['W'] / 2
        self.top = pchip(S['top'])
        self.bot = pchip(S['bot'])
        self.ws = pchip(S.get('plan', [(0, 1), (self.L, 1)]))
        self.n = S.get('n', 4.0)
        self.rpf = S.get('rpf', 0.35)
        self.rpr = S.get('rpr', 0.3)
        self.pf = S.get('pf', 2.6)
        self.pr = S.get('pr', 2.6)
        self.axles = S['axles']
        self.R = S['R']
        self.TW = S['TW']
        self.Ra = self.R + S.get('arch_gap', 0.05)
        self.gc = S.get('gc', 0.2)
        self.open = S.get('open', [])
        self.doors = S.get('doors', [])
        self.zoff = -self.L / 2
        self.paint_col = (255, 255, 255, 255)
        self.dark = rgb('#16181b')
        self.trim_rules = S.get('trim', [])
        self.interior_col = rgb(S.get('interior', '#2a2c30'))
        self.carpet = rgb(S.get('carpet', '#1d1e21'))
        sh = S.get('shape', {})
        self.y_sh = sh.get('shoulder', None)  # absolute y of the shoulder crease (widest line)
        self.tumble_side = sh.get('tumble', 0.04)  # inward lean from shoulder up to the belt
        self.tuck = sh.get('tuck', 0.04)  # inward tuck from shoulder down to the sill
        self.flare = sh.get('flare', 0.02)  # wheel arch flare
        self.flare_len = sh.get('flare_len', 0.4)
        self.arch_lip = sh.get('arch_lip', None)  # colour of arch cladding (SUV) or None

    # -------------------------------------------------------------- profile helpers
    def half_w(self, z):
        w = self.hw * self.ws(z)
        if z > self.L - self.rpf:
            u = min(1.0, (z - (self.L - self.rpf)) / self.rpf)
            w *= max(0.0, 1 - u ** self.pf) ** (1 / self.pf)
        if z < self.rpr:
            u = min(1.0, (self.rpr - z) / self.rpr)
            w *= max(0.0, 1 - u ** self.pr) ** (1 / self.pr)
        return w

    def arch(self, z):
        y = -1
        for za in self.axles:
            d = z - za
            if abs(d) < self.Ra:
                y = max(y, self.R + math.sqrt(self.Ra ** 2 - d ** 2))
        return y

    def flare_at(self, z):
        f = 0.0
        for za in self.axles:
            d = abs(z - za) / (self.Ra + self.flare_len)
            if d < 1:
                f = max(f, math.cos(d * math.pi / 2) ** 2)
        return f * self.flare

    def yb_outer(self, z):
        return max(self.bot(z), self.arch(z))

    def is_open(self, z):
        for (z0, z1, fy) in self.open:
            if z0 < z < z1:
                return fy
        return None

    def core_w(self, z):
        return max(0.0, min(self.hw - self.TW - 0.07, self.half_w(z) * 0.92))

    def y_lip(self, z):
        yb, yt = self.yb_outer(z), self.top(z)
        return yt - min(0.035, 0.12 * (yt - yb))

    def xmax(self, z, y):
        """Half width of the shaped section at height y (outer surface, below the lip)."""
        w = self.half_w(z)
        if w <= 1e-6:
            return 0.0
        yb, yt = self.yb_outer(z), self.top(z)
        hh = max(1e-4, (yt - yb) / 2)
        yc = (yb + yt) / 2
        q = min(1.0, abs(y - yc) / hh)
        x = w * max(0.0, 1 - q ** self.n) ** (1 / self.n)
        if self.y_sh is not None and yt - yb > 0.15:
            ysh = min(max(self.y_sh, yb + 0.05), yt - 0.03)
            if y > ysh:
                t = (y - ysh) / max(1e-3, yt - ysh)
                x -= self.tumble_side * t * t * min(1.0, w / self.hw)
                x += self.flare_at(z) * max(0.0, 1 - t * 2.5)
            else:
                t = (ysh - y) / max(1e-3, ysh - yb)
                x -= self.tuck * t * t * min(1.0, w / self.hw)
                x += self.flare_at(z) * (1 - 0.4 * t)
        return max(0.0, x)

    def ytop(self, z, x):
        """Top surface (hood / roof of lower body) between the lips."""
        yl = self.y_lip(z)
        yt = self.top(z)
        lx = max(1e-4, self.xmax(z, yl))
        t = min(1.0, abs(x) / lx)
        return yl + (yt - yl) * (1 - t ** 2.2)

    def inside(self, x, y, z):
        if z <= 0 or z >= self.L:
            return False
        yb, yt = self.yb_outer(z), self.top(z)
        if y < yb or y > yt:
            return False
        yl = self.y_lip(z)
        if y >= yl:
            lx = self.xmax(z, yl)
            return abs(x) <= lx and y <= self.ytop(z, x)
        return abs(x) <= self.xmax(z, y)

    # -------------------------------------------------------------- lower shell
    def ring(self, z, open_floor):
        w = self.half_w(z)
        yb_o = self.yb_outer(z)
        yt = self.top(z)
        yb_c = max(self.gc, min(self.bot(z), yb_o))
        wc = min(self.core_w(z), w * 0.9)
        yl = self.y_lip(z)
        hh = max(1e-4, (yt - yb_o) / 2)
        yc = (yb_o + yt) / 2
        e = 2 / self.n
        # theta range: bottom (x = wc) .. lip (y = yl)
        c0 = (min(1.0, wc / max(w, 1e-6))) ** (self.n / 2)
        th0 = -math.acos(min(1.0, c0))
        s1 = max(-1.0, min(1.0, (yl - yc) / hh))
        thl = math.asin(sexp(s1, self.n / 2))
        M = 7 if self.lod else 13
        ths = list(np.linspace(th0, thl, M))
        keys = list(self.S.get('keys', []))
        if self.y_sh is not None:
            keys.append(self.y_sh)
        for ky in keys:
            if yb_o < ky < yl:
                s = max(-1.0, min(1.0, (ky - yc) / hh))
                ths.append(min(max(math.asin(sexp(s, self.n / 2)), th0), thl))
            else:
                ths.append(th0 if ky <= yb_o else thl)
        ths.sort()
        outer = []
        for th in ths:
            y = yc + hh * sexp(math.sin(th), e)
            outer.append((self.xmax(z, y), y))
        lx = outer[-1][0]
        K = 5 if self.lod else 9
        t = min(T_WALL, lx * 0.3)
        if open_floor is None:
            tops = []
            for k in range(K):
                x = lx - (k + 1) * 2 * lx / (K + 1)
                tops.append((x, self.ytop(z, x)))
            ttag = 'top'
        else:
            fy = open_floor
            yt_cap = self.top(z) - 0.004
            # door-top cap (interior trim) then the floor
            tops = [(lx - t * 0.55, yt_cap)] + [((lx - t) - k * 2 * (lx - t) / (K - 3), fy) for k in range(K - 2)] + [(-(lx - t * 0.55), yt_cap)]
            ttag = 'floor'
        pts, tags = [], []
        pts.append((0.0, yb_c)); tags.append('bot')
        pts.append((wc, yb_c)); tags.append('bot')
        pts.append((wc, yb_o)); tags.append('well')
        for p in outer:
            pts.append(p); tags.append('outer')
        for p in tops:
            pts.append(p); tags.append(ttag)
        for p in reversed(outer):
            pts.append((-p[0], p[1])); tags.append('outer_r')
        pts.append((-wc, yb_o)); tags.append('well_r')
        pts.append((-wc, yb_c)); tags.append('bot_r')
        pts.append((0.0, yb_c)); tags.append('bot')
        return pts, tags

    def stations(self):
        L = self.L
        zs = []
        nend = 5 if self.lod else 8
        for th in np.linspace(0, math.pi / 2, nend):
            zs.append(self.rpr * (1 - math.cos(th)))
            zs.append(L - self.rpf * (1 - math.cos(th)))
        zs += list(np.arange(0, L, 0.3 if self.lod else 0.16))
        for za in self.axles:
            for th in np.linspace(-math.pi / 2, math.pi / 2, 7 if self.lod else 11):
                zs.append(za + self.Ra * math.sin(th))
            zs.append(za - self.Ra - 0.01)
            zs.append(za + self.Ra + 0.01)
        for z in self.S.get('zkeys', []):
            zs.append(z)
        for d in self.doors:
            zs += [d[1], d[2]]
        zs = sorted(set(round(z, 4) for z in zs if 0 <= z <= L))
        out = []
        for z in zs:
            if not out or z - out[-1][0] > 0.004:
                out.append([z, self.is_open(z)])
        res = []
        for (z0, z1, fy) in self.open:
            res += [(z0, None), (z0 + 0.0005, fy), (z1 - 0.0005, fy), (z1, None)]
        out = [(z, f) for z, f in out if all(abs(z - b[0]) > 0.003 for b in res)]
        out += res
        out.sort(key=lambda a: a[0])
        return out

    def trim_mat(self, x, y, z, tag):
        for r in self.trim_rules:
            z0, z1, y0, y1, mat, col = r[:6]
            if len(r) > 6 and r[6] != tag:
                continue
            if z0 <= z <= z1 and y0 <= y <= y1:
                return mat, rgb(col) if isinstance(col, str) else col
        if self.arch_lip is not None:
            a = self.arch(z)
            if a > 0 and y < a + 0.06 and abs(x) > self.core_w(z) + 0.02:
                return SOLID, rgb(self.arch_lip)
        return PAINT, self.paint_col

    def door_of(self, x, y, z):
        for (name, z0, z1, kind) in self.doors:
            if z0 <= z <= z1 and y > self.bot(z) + 0.02:
                return 'door_' + name + ('l' if x > 0 else 'r')
        return None

    def build_lower(self):
        st = self.stations()
        rings = [(z, self.ring(z, f)) for z, f in st]
        for i in range(len(rings) - 1):
            za, (pa, ta) = rings[i]
            zb, (pb, tb) = rings[i + 1]
            n = len(pa)
            for j in range(n - 1):
                A = v3(pa[j][0], pa[j][1], za + self.zoff)
                B = v3(pb[j][0], pb[j][1], zb + self.zoff)
                C = v3(pb[j + 1][0], pb[j + 1][1], zb + self.zoff)
                D = v3(pa[j + 1][0], pa[j + 1][1], za + self.zoff)
                tag = ta[j + 1] if ta[j] != ta[j + 1] else ta[j]
                cen = (A + B + C + D) / 4
                zc = cen[2] - self.zoff
                part = 'body'
                ta_open = 'floor' in (ta[j], ta[j + 1])
                tb_open = 'floor' in (tb[j], tb[j + 1])
                uvr = None
                if tag in ('bot', 'bot_r'):
                    mat, col, hint = SOLID, self.dark, v3(0, -1, 0)
                elif tag in ('well', 'well_r'):
                    mat, col = SOLID, rgb('#0b0b0c')
                    hint = v3(1 if cen[0] > 0 else -1, 0, 0)
                elif tag in ('outer', 'outer_r'):
                    mat, col = self.trim_mat(cen[0], cen[1], zc, tag)
                    hint = cen - v3(0, (self.yb_outer(zc) + self.top(zc)) / 2, cen[2])
                    d = self.door_of(cen[0], cen[1], zc)
                    if d and cen[1] > self.bot(zc) + 0.03:
                        part = d
                else:
                    if ta_open and tb_open and min(A[1], B[1], C[1], D[1]) > self.y_lip(zc) - 0.03:
                        mat, col = SOLID, rgb(self.S.get('interior_layout', {}).get('soft', '#2a2b2f'))
                        hint = v3(0, 1, 0)
                        d = self.door_of(cen[0], cen[1], zc)
                        if d:
                            part = d
                    elif ta_open and tb_open:
                        if abs(cen[0]) > self.xmax(zc, self.y_lip(zc)) - T_WALL - 0.02 and abs(A[1] - D[1]) > 0.05:
                            mat, col = SOLID, self.interior_col
                            hint = v3(-1 if cen[0] > 0 else 1, 0, 0)
                            d = self.door_of(cen[0], cen[1], zc)
                            if d:
                                part = d
                        else:
                            mat, col = SOLID, self.carpet
                            hint = v3(0, 1, 0)
                            uvr = UV['carpet']
                    elif ta_open != tb_open:
                        mat, col = SOLID, self.interior_col
                        hint = v3(0, 0, -1) if tb_open is False else v3(0, 0, 1)
                    else:
                        mat, col = self.trim_mat(cen[0], cen[1], zc, 'top')
                        hint = v3(0, 1, 0)
                self.emit_quad(part, mat, A, B, C, D, col, hint, uvr=uvr)

    def emit_quad(self, part, mat, A, B, C, D, col, hint, uvr=None, smooth=True, uvs=None):
        n = np.cross(B - A, D - A)
        if np.linalg.norm(n) < 1e-12:
            n = np.cross(C - B, A - B)
        if np.dot(n, hint) < 0:
            if uvs is not None:
                uvs = (uvs[0], uvs[3], uvs[2], uvs[1])
            self.m.quad(part, mat, A, D, C, B, col, uvr=uvr, smooth=smooth, uvs=uvs)
        else:
            self.m.quad(part, mat, A, B, C, D, col, uvr=uvr, smooth=smooth, uvs=uvs)

    # -------------------------------------------------------------- greenhouse
    def build_greenhouse(self):
        G = self.S.get('gh')
        if not G:
            return
        roof = pchip(G['roof'])
        z_start, z_end = G['roof'][0][0], G['roof'][-1][0]
        rf0, rf1 = G['flat']
        tumble = G.get('tumble', 0.12)
        crown = G.get('crown', 0.03)
        rc = G.get('rc', 0.07)
        windows = G.get('windows', [])
        pillar = G.get('pillar', 'paint')
        roofmat = G.get('roofmat', PAINT)
        belt_trim = G.get('belt_trim', 'black')
        bpillar_black = G.get('bpillar_black', False)
        frit = G.get('frit', 0.05)
        glass_col = rgb(G.get('glass', '#22323d'))[:3] + (G.get('glass_alpha', 150),)
        nst = 12 if self.lod else 26
        zs = list(np.linspace(z_start, z_end, nst))
        zs += [rf0, rf1, rf0 - frit, rf1 + frit] + [w[0] for w in windows] + [w[1] for w in windows]
        for d in self.doors:
            zs += [d[1], d[2]]
        zs = sorted(set(round(z, 4) for z in zs if z_start <= z <= z_end))
        zs2 = []
        for z in zs:
            if not zs2 or z - zs2[-1] > 0.004:
                zs2.append(z)
        zs = zs2
        hmax = G.get('hmax', 0.45)

        def gring(z):
            yb = self.top(z)
            yt = max(yb, roof(z))
            hgt = yt - yb
            wb = self.xmax(z, self.y_lip(z)) - T_WALL * 0.45
            wt = max(0.05, wb - tumble * min(1.0, hgt / hmax))
            r = min(rc, hgt * 0.45, wt * 0.3)
            seal = min(0.03, hgt * 0.3)
            pts, tags = [], []
            pts.append((wb + 0.006, yb)); tags.append('seal')
            pts.append((wb - 0.002, yb + seal)); tags.append('side')
            k = min(1, hgt / 0.3)
            pts.append(((wb + wt) / 2 + 0.014 * k, yb + seal + (hgt - r - seal) * 0.5)); tags.append('side')
            pts.append((wt, yt - r)); tags.append('side')
            for a in (math.pi / 6, math.pi / 3):
                pts.append((wt - r + r * math.cos(a), yt - r + r * math.sin(a))); tags.append('corner')
            K = 3 if self.lod else 7
            xs = np.linspace(wt - r, -(wt - r), K + 2)
            for x in xs:
                pts.append((x, yt + crown * (1 - (x / max(wt, 1e-3)) ** 2) * min(1, hgt / 0.2))); tags.append('roof')
            for a in (math.pi / 3, math.pi / 6):
                pts.append((-(wt - r + r * math.cos(a)), yt - r + r * math.sin(a))); tags.append('corner')
            pts.append((-wt, yt - r)); tags.append('side')
            pts.append((-((wb + wt) / 2 + 0.014 * k), yb + seal + (hgt - r - seal) * 0.5)); tags.append('side')
            pts.append((-(wb - 0.002), yb + seal)); tags.append('side')
            pts.append((-(wb + 0.006), yb)); tags.append('seal')
            return pts, tags

        rings = [(z, gring(z)) for z in zs]
        pil_mat, pil_col = (SOLID, rgb('#0f1012')) if pillar == 'black' else (PAINT, self.paint_col)
        head_col = rgb(self.S.get('headliner', '#8c8a86'))
        gu0, gv0, gu1, gv1 = UV['glass']
        H = self.S['H']

        def guv(p):
            u = gu0 + (gu1 - gu0) * min(1, max(0, (p[2] - self.zoff) / self.L))
            v = gv0 + (gv1 - gv0) * min(1, max(0, (H - p[1]) / 0.7))
            return (u, v)

        for i in range(len(rings) - 1):
            za, (pa, ta) = rings[i]
            zb, (pb, tb) = rings[i + 1]
            for j in range(len(pa) - 1):
                A = v3(pa[j][0], pa[j][1], za + self.zoff)
                B = v3(pb[j][0], pb[j][1], zb + self.zoff)
                C = v3(pb[j + 1][0], pb[j + 1][1], zb + self.zoff)
                D = v3(pa[j + 1][0], pa[j + 1][1], za + self.zoff)
                t0, t1 = ta[j], ta[j + 1]
                if 'seal' in (t0, t1):
                    tag = 'seal'
                elif 'corner' in (t0, t1):
                    tag = 'corner' if 'roof' in (t0, t1) or t0 == t1 else 'corner'
                else:
                    tag = t0 if t0 == t1 else ('roof' if 'roof' in (t0, t1) else t0)
                cen = (A + B + C + D) / 4
                zc = cen[2] - self.zoff
                hint = cen - v3(0, self.top(zc), cen[2])
                part = 'body'
                inflat = rf0 <= zc <= rf1
                infrit = (rf0 - frit <= zc < rf0) or (rf1 < zc <= rf1 + frit)
                uvs = None
                uvr = None
                if tag == 'seal':
                    if belt_trim == 'chrome':
                        mat, col = SOLID, rgb('#d4d8de')
                    else:
                        mat, col = SOLID, rgb('#0e0f10')
                elif tag in ('roof', 'corner'):
                    if inflat:
                        mat = roofmat
                        col = self.paint_col if roofmat in (PAINT, PAINT2) else rgb(G.get('roofcol', '#111111'))
                    elif tag == 'corner':
                        mat, col = pil_mat, pil_col
                    elif infrit:
                        mat, col, uvr = SOLID, rgb('#ffffff'), UV['frit']
                    else:
                        mat, col = GLASS, glass_col
                        uvs = (guv(A), guv(B), guv(C), guv(D))
                else:
                    inwin = any(w0 <= zc <= w1 for (w0, w1) in windows)
                    if inwin:
                        mat, col = GLASS, glass_col
                        uvs = (guv(A), guv(B), guv(C), guv(D))
                    elif bpillar_black and windows and min(w[0] for w in windows) < zc < max(w[1] for w in windows):
                        mat, col = SOLID, rgb('#0b0c0d')
                    else:
                        mat, col = pil_mat, pil_col
                    d = self.door_of(cen[0], cen[1], zc)
                    if d:
                        part = d
                if tag == 'seal':
                    d = self.door_of(cen[0], cen[1] + 0.05, zc)
                    if d:
                        part = d
                self.emit_quad(part, mat, A, B, C, D, col, hint, uvr=uvr, uvs=uvs)
                if mat != GLASS and tag != 'seal':
                    off = -normalize(hint) * 0.025
                    self.emit_quad(part, SOLID, A + off, B + off, C + off, D + off, head_col, -hint, uvr=UV['fabric'])

    # -------------------------------------------------------------- surface projection
    def project(self, origin, direction, maxd=3.0):
        o = np.array(origin, float)
        d = normalize(np.array(direction, float))
        prev = False
        step = 0.02
        t = 0.0
        last_t = 0
        while t < maxd:
            p = o + d * t
            ins = self.inside(p[0], p[1], p[2] - self.zoff)
            if ins and not prev:
                a, b = last_t, t
                for _ in range(18):
                    mid = (a + b) / 2
                    q = o + d * mid
                    if self.inside(q[0], q[1], q[2] - self.zoff):
                        b = mid
                    else:
                        a = mid
                return o + d * b
            prev = ins
            last_t = t
            t += step
        return None

    def surf_normal(self, p):
        """Normal from the signed-distance-like field (gradient by central differences)."""
        eps = 0.006

        def F(q):
            z = q[2] - self.zoff
            if z <= 0 or z >= self.L:
                return 0.2
            yb, yt = self.yb_outer(z), self.top(z)
            yl = self.y_lip(z)
            y = q[1]
            if y >= yl:
                lx = self.xmax(z, yl)
                return max(abs(q[0]) - lx, y - self.ytop(z, q[0]), yb - y)
            return max(abs(q[0]) - self.xmax(z, min(max(y, yb), yt)), yb - y)

        g = np.zeros(3)
        for k in range(3):
            e = np.zeros(3)
            e[k] = eps
            g[k] = F(p + e) - F(p - e)
        if np.linalg.norm(g) < 1e-9:
            return v3(0, 0, 1)
        return normalize(g)

    def _frame(self, n, updir):
        upv = np.array(updir, float)
        u = np.cross(upv, n)
        if np.linalg.norm(u) < 1e-6:
            u = np.cross(v3(0, 0, 1), n)
        u = normalize(u)
        v = np.cross(n, u)
        return u, v

    def decal(self, part, mat, center_hint, direction, shape, col, offset=0.012, uvr=None, depth=0.0, updir=(0, 1, 0),
              side_col=None, uvflip=False, dome=0.0):
        """Conformal decal: outline `shape` [(u, v)] in the tangent frame at the hit point is projected onto the body.
        depth extrudes it (with side walls); dome bulges the centre outward (lenses)."""
        hit = self.project(center_hint, direction)
        if hit is None:
            return None
        n = self.surf_normal(hit)
        u, v = self._frame(n, updir)
        us = [p[0] for p in shape]
        vs = [p[1] for p in shape]
        umin, umax, vmin, vmax = min(us), max(us), min(vs), max(vs)
        u0, v0_, u1, v1 = uvr if uvr else UV["white"]

        def place(a, b, off):
            q0 = hit + u * a + v * b
            q = self.project(q0 + n * 0.25, -n, maxd=0.6)
            if q is None:
                q = q0
            nn = self.surf_normal(q)
            return q + nn * off

        def uvof(a, b):
            fu = (a - umin) / max(1e-6, umax - umin)
            fv = (b - vmin) / max(1e-6, vmax - vmin)
            if uvflip:
                fu = 1 - fu
            return (u0 + (u1 - u0) * fu, v1 - (v1 - v0_) * fv)

        ca = sum(us) / len(us)
        cb = sum(vs) / len(vs)
        size = max(umax - umin, vmax - vmin)
        if self.lod or size < 0.1:
            rings = [1.0, 0.45]
        elif size < 0.3:
            rings = [1.0, 0.7, 0.3]
        else:
            rings = [1.0, 0.8, 0.5, 0.22]
        if len(shape) > 14 and (size < 0.1 or self.lod):
            shape = shape[::2]
            us = [p[0] for p in shape]
            vs = [p[1] for p in shape]
        offd = offset + depth
        R = []
        for k, s in enumerate(rings):
            pts2 = [(ca + (a - ca) * s, cb + (b - cb) * s) for a, b in shape]
            bump = dome * (1 - s * s)
            R.append((pts2, [place(a, b, offd + bump) for a, b in pts2]))
        Pc = place(ca, cb, offd + dome)
        N = len(shape)
        for r in range(len(rings) - 1):
            (sa, Pa), (sb, Pb) = R[r], R[r + 1]
            for k in range(N):
                k2 = (k + 1) % N
                self.m.tri(part, mat, (Pb[k], Pa[k], Pa[k2]), (uvof(*sb[k]), uvof(*sa[k]), uvof(*sa[k2])), col, smooth=dome > 0)
                self.m.tri(part, mat, (Pb[k], Pa[k2], Pb[k2]), (uvof(*sb[k]), uvof(*sa[k2]), uvof(*sb[k2])), col, smooth=dome > 0)
        sl, Pl = R[-1]
        for k in range(N):
            k2 = (k + 1) % N
            self.m.tri(part, mat, (Pc, Pl[k], Pl[k2]), (uvof(ca, cb), uvof(*sl[k]), uvof(*sl[k2])), col, smooth=dome > 0)
        if depth > 0.004:
            P_base = [place(a, b, offset * 0.4) for a, b in shape]
            P_out = R[0][1]
            sc = side_col if side_col is not None else col
            smat = mat if side_col is None else SOLID
            for k in range(N):
                k2 = (k + 1) % N
                self.m.quad(part, smat, P_base[k], P_base[k2], P_out[k2], P_out[k], sc, smooth=False)
        return hit, n, u, v

    def strip(self, part, mat, path, direction, width, col, offset=0.008, depth=0.0, uvr=None, samples=None):
        """A projected ribbon following a polyline of points outside the body (ray `direction` into the body).
        Used for panel gaps, chrome mouldings, crease highlights."""
        path = [np.array(p, float) for p in path]
        pts = []
        if samples:
            dense = []
            for a, b in zip(path[:-1], path[1:]):
                for k in range(samples):
                    dense.append(a + (b - a) * k / samples)
            dense.append(path[-1])
            path = dense
        d = np.array(direction, float)
        for p in path:
            h = self.project(p, d)
            if h is not None:
                pts.append((h, self.surf_normal(h)))
        if len(pts) < 2:
            return
        u0, v0, u1, v1 = uvr if uvr else UV['white']
        L = []
        R = []
        for i, (h, n) in enumerate(pts):
            t = pts[min(i + 1, len(pts) - 1)][0] - pts[max(i - 1, 0)][0]
            side = normalize(np.cross(n, normalize(t)))
            off = n * (offset + depth)
            L.append(h + side * width / 2 + off)
            R.append(h - side * width / 2 + off)
        for i in range(len(pts) - 1):
            f0, f1 = i / (len(pts) - 1), (i + 1) / (len(pts) - 1)
            uvs = ((u0 + (u1 - u0) * f0, v1), (u0 + (u1 - u0) * f1, v1), (u0 + (u1 - u0) * f1, v0), (u0 + (u1 - u0) * f0, v0))
            self.m.quad(part, mat, R[i], R[i + 1], L[i + 1], L[i], col, uvs=uvs, smooth=False)
            if depth > 0.003:
                for (Aa, Bb) in ((L, R),):
                    pass


# ---------------------------------------------------------------- shapes
def shp_rrect(w, h, r, seg=3):
    r = min(r, w / 2 * 0.999, h / 2 * 0.999)
    pts = []
    cs = [(w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180), (w / 2 - r, -h / 2 + r, 270)]
    for cx, cy, a0 in cs:
        for k in range(seg + 1):
            a = math.radians(a0 + 90 * k / seg)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def shp_ellipse(w, h, seg=18):
    return [(w / 2 * math.cos(2 * math.pi * k / seg), h / 2 * math.sin(2 * math.pi * k / seg)) for k in range(seg)]


def shp_slant(w, h, slant, r=0.02):
    """Swept lamp: the top edge is shifted by `slant` (positive = toward +u)."""
    base = shp_rrect(w, h, r, 3)
    return [(x + slant * (y / h + 0.5) - slant / 2, y) for x, y in base]


def shp_wedge(w, h_in, h_out, r=0.015):
    """Lamp that is taller at the inner end (+u side = outer)."""
    pts = []
    for (x, y) in shp_rrect(w, max(h_in, h_out), r, 3):
        t = (x + w / 2) / w
        hh = h_in + (h_out - h_in) * t
        pts.append((x, y * hh / max(h_in, h_out)))
    return pts


def shp_trap(w_top, w_bot, h, r=0.015):
    return [(-w_bot / 2, -h / 2), (w_bot / 2, -h / 2), (w_top / 2, h / 2), (-w_top / 2, h / 2)]


def scale_shape(shape, s, ds=0.0):
    ca = sum(p[0] for p in shape) / len(shape)
    cb = sum(p[1] for p in shape) / len(shape)
    out = []
    for a, b in shape:
        da, db = a - ca, b - cb
        L = math.hypot(da, db)
        k = s + (ds / L if L > 1e-6 else 0)
        out.append((ca + da * k, cb + db * k))
    return out
