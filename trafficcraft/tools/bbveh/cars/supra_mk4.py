"""Toyota Supra MK4 (A80, 1993-2002) - Blockbench (.bbmodel) source generator.

Authoring frame: +X = car's left, +Y up, +Z forward, z measured from the rear bumper (0) to the nose (L).
Body = coarse flat-shaded loft (Minecraft-style facets) + layered lamp geometry + pixel textures.

Lighting reference (A80): front - long almond-shaped clear headlamp unit on each front corner with THREE round lamps in a row
(inner large low beam, middle large high beam, small outer park/indicator lamp); wide oval mouth with a rectangular
fog/indicator lamp at each outer end. Rear - two horizontal dark-red rounded clusters, each holding TWO round lamps
(outer brake/tail, inner tail with white reverse centre), indicator segment between them; third brake lamp in the wing.
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from bb import *
import styles as S
import parts as PT

ID = 'toyota_supra_mk4'
L, W, H, WB = 4.515, 1.81, 1.275, 2.55
YR, YF = 1.0, 3.55          # axle z positions (from the rear)
R, TRACK = 0.322, 0.765
RA = R + 0.045              # wheel-arch radius

bot = Curve([(0.0, 0.36), (0.1, 0.3), (0.3, 0.255), (0.6, 0.225), (1.45, 0.205), (3.0, 0.205), (3.9, 0.215), (4.2, 0.218), (4.42, 0.225),
             (4.515, 0.255)])
belt = Curve([(0.0, 0.84), (0.05, 0.915), (0.18, 0.952), (0.55, 0.968), (0.9, 0.962), (1.5, 0.935), (2.2, 0.892), (2.9, 0.862), (3.1, 0.85),
              (3.55, 0.8), (3.9, 0.762), (4.2, 0.7), (4.4, 0.63), (4.515, 0.55)])
hw = Curve([(0.0, 0.76), (0.05, 0.82), (0.15, 0.865), (0.35, 0.89), (0.7, 0.905), (1.1, 0.905), (1.5, 0.893), (2.0, 0.883), (2.7, 0.883),
            (3.2, 0.893), (3.6, 0.9), (3.95, 0.885), (4.2, 0.845), (4.35, 0.79), (4.45, 0.71), (4.5, 0.63), (4.515, 0.58)])
zsh = Curve([(0.0, 0.6), (1.0, 0.6), (2.2, 0.56), (3.5, 0.54), (4.2, 0.5), (4.515, 0.45)])
CABIN0, CABIN1 = 0.8, 3.1
roofz = Curve([(CABIN0, 0.968), (1.0, 1.05), (1.3, 1.15), (1.6, 1.215), (1.9, 1.255), (2.2, 1.272), (2.42, 1.266), (2.62, 1.232),
               (2.8, 1.13), (2.98, 0.975), (CABIN1, 0.85)])
roofw = Curve([(CABIN0, 0.72), (1.0, 0.67), (1.4, 0.61), (2.0, 0.57), (2.5, 0.57), (2.8, 0.61), (CABIN1, 0.69)])
crown = Curve([(0.0, 0.012), (0.8, 0.02), (3.1, 0.026), (3.6, 0.034), (4.2, 0.024), (4.515, 0.006)])
HATCH0, HATCH1 = 0.86, 1.62
WS0, WS1 = 2.62, 3.04
QW0, QW1, BP1 = 1.24, 1.69, 1.76
DOOR0, DOOR1 = 1.73, 3.1
FB_Z, RB_Z = 4.02, 0.42     # bumper seams
TAIL_Y = 0.715

m = Model(ID, 1024, 40)
m.shift = V(0, 0, -L / 2)
m.painters.update(S.COMMON)


def arch_h(z):
    for za in (YR, YF):
        d = abs(z - za)
        if d <= RA + 1e-6:
            return R + math.sqrt(max(0.0, RA * RA - d * d))
    return None


def gh(z):
    if z <= CABIN0 or z >= CABIN1:
        return 0.0
    return max(0.0, roofz(z) - belt(z))


def section(z, chin=0.0):
    """Half section (x >= 0): 10 lower points bottom-centre -> top-centre."""
    w, t, s = hw(z), belt(z), zsh(z)
    b = bot(z) - chin * smooth(FB_Z, 4.38, z)
    a = arch_h(z)
    arch = a is not None
    a = a if arch else b
    s2 = max(s, a + 0.03)
    cr = crown(z)
    pts = [(0.0, b + 0.02), (0.6, b), (0.6, a), (w * 0.965, max(a, b + 0.035)), (w, s2), (w - 0.02, (s2 + t) / 2), (w - 0.058, t - 0.022),
           (w - 0.1, t), (w * 0.5, t + cr * 0.72), (0.0, t + cr)]
    return pts


def deck_z(x, z):
    """Top surface height of the lower body (hood / deck) at (x, z)."""
    w, t = hw(z), belt(z)
    xx = min(abs(x), w - 0.1)
    return t + crown(z) * (1 - (xx / max(w - 0.1, 1e-3)) ** 2)


def gpts(z):
    """Greenhouse half section: glass bottom (= belt top), glass top, roof edge, roof centre."""
    w, t = hw(z), belt(z)
    g = gh(z)
    k = smooth(0.0, 0.08, g)
    r = roofw(min(max(z, CABIN0), CABIN1))
    rz = t + g
    hood = [(w * 0.74, deck_z(w * 0.74, z)), (w * 0.52, deck_z(w * 0.52, z)), (0.0, deck_z(0.0, z))]
    cab = [(r + 0.05, rz - 0.075), (r, rz - 0.022), (0.0, rz)]
    out = [(w - 0.1, t)]
    for (xh, zh), (xc, zc) in zip(hood, cab):
        out.append((lerp(xh, xc, k), lerp(zh, max(zc, zh), k)))
    return out


def stations():
    zs = [0.0, 0.05, 0.18, RB_Z, CABIN0, HATCH0, QW0, 1.45, HATCH1, QW1, DOOR0, BP1, 2.2, 2.42, WS0, 2.85, WS1, CABIN1, FB_Z, 4.2, 4.35, 4.45, L]
    for za in (YR, YF):
        for d in (-RA - 0.004, -RA, -0.8 * RA, -0.45 * RA, 0.0, 0.45 * RA, 0.8 * RA, RA, RA + 0.004):
            zs.append(za + d)
    zs = sorted(set(round(z, 4) for z in zs if 0 <= z <= L))
    out = []
    for z in zs:
        if not out or z - out[-1] > 0.003:
            out.append(z)
    return out


ZS = stations()


def P3(x, y, z):
    return V(x, y, z)


# --------------------------------------------------------------------------------------------- loft routing
ROUTE = {}


def el(group, mat, style):
    key = (group, mat, style)
    if key not in ROUTE:
        ROUTE[key] = m.element('%s_%s' % (group, style.replace('.', '_')), mat, group, style)
    m._cur = ROUTE[key]
    return ROUTE[key]


def outward_side(q, sx):
    c = np.mean(q, axis=0)
    return V(sx * abs(c[0]) * 0 + sx, 0, 0) * 0 + V(c[0], c[1] - 0.55, 0) * 1.0


def loft(zs, sec_fn, route_fn, down_bands=()):
    """Mirrored loft; route_fn(j, zm, side) -> (group, mat, style) or None."""
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
                q = [P3(sx * A[j][0], A[j][1], z0), P3(sx * B[j][0], B[j][1], z1), P3(sx * B[j + 1][0], B[j + 1][1], z1), P3(sx * A[j + 1][0], A[j + 1][1], z0)]
                c = np.mean(q, axis=0)
                ow = V(0, -1, 0) if j in down_bands else V(c[0], c[1] - 0.6, 0)
                m.face(q, r[2], el=el(*r), outward=ow)


def lower_route(fb='fb_0', rb='rb_0', fbs='paint.fb0', rbs='paint.rb0'):
    def f(j, zm, sx):
        side = 'l' if sx > 0 else 'r'
        under = j <= 2
        if zm > FB_Z and j <= 6:
            return (fb, 'solid', 'under') if under else (fb, 'paint', fbs)
        if zm < RB_Z and j <= 3:
            return (rb, 'solid', 'under') if under else (rb, 'paint', rbs)
        if zm > FB_Z or zm < RB_Z:
            return ('body', 'paint', 'paint')
        if under:
            return ('body', 'solid', 'under')
        if j >= 7 and CABIN0 + 0.01 < zm < CABIN1 - 0.01:
            return None                                   # hidden under the greenhouse (would cut through the cabin)
        if DOOR0 < zm < DOOR1 and 3 <= j <= 6:
            return ('door_f' + side, 'paint', 'paint')
        return ('body', 'paint', 'paint')
    return f


def only(groups):
    def deco(f):
        def g(j, zm, sx):
            r = f(j, zm, sx)
            return r if r is not None and r[0] in groups else None
        return g
    return deco


def green_route(j, zm, sx):
    side = 'l' if sx > 0 else 'r'
    if j == 0:
        if QW1 < zm < BP1:
            return ('body', 'solid', 'trim_gloss')
        if QW0 < zm < QW1:
            return ('body', 'glass', 'glass')
        if BP1 < zm < CABIN1:
            return ('door_f' + side, 'glass', 'glass') if zm > DOOR0 else ('body', 'glass', 'glass')
        return ('body', 'paint', 'paint')
    if j == 1:
        return ('body', 'solid', 'trim_gloss') if zm > WS0 - 0.02 else ('body', 'paint', 'paint')
    if WS0 < zm < WS1 or HATCH0 < zm < HATCH1:
        return ('body', 'glass', 'glass')
    return ('body', 'paint', 'paint')


def cap(z, pts, groupmat, outward, lo=0, hi=None):
    hi = len(pts) - 1 if hi is None else hi
    half = pts[lo:hi + 1]
    poly = [P3(x, y, z) for x, y in half] + [P3(-x, y, z) for x, y in reversed(half)]
    m.face(poly, groupmat[2], el=el(*groupmat), outward=outward)


# base body (front/rear bumper zones are emitted per variant below)
loft(ZS, section, only(['body', 'door_fl', 'door_fr'])(lower_route()), down_bands=(0, 2))
ZG = [z for z in ZS if CABIN0 <= z <= CABIN1]
loft(ZG, gpts, green_route)
cap(0.0, section(0.0), ('body', 'paint', 'paint.rear'), V(0, 0, -1), lo=4)

# --------------------------------------------------------------------------------------------- interior lining
def lining():
    """Inner skin so the cabin does not show painted metal from inside."""
    for z0, z1 in zip(ZG[:-1], ZG[1:]):
        zm = (z0 + z1) / 2
        A, B = gpts(z0), gpts(z1)
        for j in (1, 2):
            if j == 2 and (WS0 < zm < WS1 or HATCH0 < zm < HATCH1):
                continue
            for sx in (1, -1):
                q = [P3(sx * A[j][0], A[j][1] - 0.012, z0), P3(sx * B[j][0], B[j][1] - 0.012, z1), P3(sx * B[j + 1][0], B[j + 1][1] - 0.012, z1),
                     P3(sx * A[j + 1][0], A[j + 1][1] - 0.012, z0)]
                m.face(q, 'headliner', el=el('body', 'solid', 'headliner'), outward=V(0, -1, 0))
    # side trim panels behind the doors (rear quarters)
    for sx in (1, -1):
        x = sx * 0.8
        m.face([P3(x, 0.28, 1.2), P3(x, 0.28, DOOR0), P3(x, 0.92, DOOR0), P3(x, 0.92, 1.2)], 'dash2', el=el('body', 'solid', 'dash2'), outward=V(-sx, 0, 0))


lining()


# --------------------------------------------------------------------------------------------- ray casting on the body
def body_tris(groups=('body', 'fb_0', 'rb_0', 'door_fl', 'door_fr')):
    T = []
    for e in m.elements:
        if e.group in groups and e.mat in ('paint', 'solid', 'glass') and e.style != 'headliner':
            for f in e.faces:
                for i in range(1, len(f.verts) - 1):
                    T.append([f.verts[0], f.verts[i], f.verts[i + 1]])
    return np.array(T)


def hits(points2d, view, tris, normals=False):
    """Project 2D points onto the body. view: 'T' (x,z) from above, 'F' (x,y) from the front, 'B' (x,y) from behind, 'L'/'R' (z,y)."""
    pts = np.array(points2d, float)
    if view == 'T':
        O, d = np.c_[pts[:, 0], np.full(len(pts), 3.0), pts[:, 1]], V(0, -1, 0)
    elif view == 'F':
        O, d = np.c_[pts[:, 0], pts[:, 1], np.full(len(pts), L + 2)], V(0, 0, -1)
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
    T = tris[idx]
    N = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    N /= np.maximum(np.linalg.norm(N, axis=1), 1e-12)[:, None]
    N *= np.where(N @ d > 0, -1.0, 1.0)[:, None]
    return P, N


def lamp_conform(outline, view, tris, layers, wall_el, wall_style, rings=(1.0, 0.8, 0.6, 0.4, 0.2), dens=160, depth=0.012):
    """Lamp insert that follows the body surface (flush, nothing floats): every layer is the outline projected onto the
    car and lifted by its offset along the local surface normal. Returns proj(p2, off) -> (point, normal)."""
    ol = np.array(outline, float)
    c = ol.mean(axis=0)
    pts2 = [c + (ol - c) * s for s in rings] + [c[None]]
    allp = np.vstack(pts2)
    P, N = hits(allp, view, tris, True)
    bad = ~np.all(np.isfinite(P), axis=1)
    if bad.any():
        P[bad] = P[~bad][0]
        N[bad] = N[~bad][0]
    sizes = [len(x) for x in pts2]
    idx = np.cumsum([0] + sizes)
    n_out = len(ol)
    top_ring = None
    for off, e, style in layers:
        Q = P + N * off
        R_ = [Q[idx[i]:idx[i + 1]] for i in range(len(pts2))]
        nav = N.mean(axis=0)
        for a, b in zip(R_[:-2], R_[1:-1]):
            for k in range(n_out):
                k1 = (k + 1) % n_out
                m.face([a[k], a[k1], b[k1]], style, el=e, outward=nav, dens=dens)
                m.face([a[k], b[k1], b[k]], style, el=e, outward=nav, dens=dens)
        cc = R_[-1][0]
        last = R_[-2]
        for k in range(n_out):
            m.face([last[k], last[(k + 1) % n_out], cc], style, el=e, outward=nav, dens=dens)
        top_ring = R_[0]
    base = P[:n_out] - N[:n_out] * depth
    cen = top_ring.mean(axis=0)
    for k in range(n_out):
        k1 = (k + 1) % n_out
        q = [top_ring[k], top_ring[k1], base[k1], base[k]]
        m.face(q, wall_style, el=wall_el, outward=np.mean(q, axis=0) - cen, dens=64)

    def proj(p2, off):
        p, n = hits([p2], view, tris, True)
        return p[0] + n[0] * off, n[0]
    return proj


def fit_plane(Pts):
    c = Pts.mean(axis=0)
    u, s, vt = np.linalg.svd(Pts - c)
    n = vt[2]
    return c, n


def lamp_plate(outline, view, tris, lift, out_dir, layers, wall_style='trim', wall_el=None, dens=128):
    """A planar lamp insert fitted to the body: returns (origin, normal, project_fn). layers = [(offset, element, style)]
    adds planar copies of the outline (lens, housing...) at `offset` above the fitted plane; walls hide the gap."""
    H3 = hits(outline, view, tris)
    ok = np.all(np.isfinite(H3), axis=1)
    H3 = H3[ok]
    c, n = fit_plane(H3)
    if np.dot(n, out_dir) < 0:
        n = -n
    top = max(np.dot(H3 - c, n))
    base = c + n * top

    def proj(p2, off):
        p = hits([p2], view, tris)[0]
        if not np.all(np.isfinite(p)):
            p = H3[0]
        return p - n * np.dot(p - base, n) + n * off
    rings = {}
    for off, e, style in layers:
        ring = [proj(p, off) for p in outline]
        rings[off] = ring
        m.face(ring, style, el=e, outward=n, dens=dens)
    hi = max(o for o, _, _ in layers)
    lo_ring = [p - n * (lift + 0.03) for p in rings[hi]]
    we = wall_el or el('body', 'solid', wall_style)
    k = len(outline)
    cc = np.mean(rings[hi], axis=0)
    for i in range(k):
        q = [rings[hi][i], rings[hi][(i + 1) % k], lo_ring[(i + 1) % k], lo_ring[i]]
        m.face(q, wall_style, el=we, outward=np.mean(q, axis=0) - cc, dens=64)
    return base, n, proj


# --------------------------------------------------------------------------------------------- variants: bumpers
FB_NAMES = ['Factory', 'Factory + lip', 'Street mesh', 'Time-attack', 'Drift aero']
RB_NAMES = ['Factory', 'Diffuser', 'Vented valance', 'Track diffuser', 'Cut-out valance']
FB_CHIN = [0.0, 0.0, 0.0, 0.03, 0.015]


def front_bumpers():
    for k in range(5):
        g = 'fb_%d' % k
        st = 'paint.fb%d' % k
        zs = [z for z in ZS if z >= FB_Z - 1e-6]
        loft(zs, lambda z: section(z, FB_CHIN[k]), only([g])(lower_route(fb=g, fbs=st)), down_bands=(0, 2))
        cap(L, section(L, FB_CHIN[k]), (g, 'paint', st), V(0, 0, 1))
        b0 = bot(4.3) - FB_CHIN[k]
        if k in (1, 3, 4):
            # lip / splitter following the plan outline of the bumper bottom
            ext = 0.085 if k == 3 else 0.03
            e = el(g, 'solid', 'carbon' if k == 3 else 'trim')
            zz = [z for z in zs]
            outer = [(hw(z) * 0.965 + ext * smooth(FB_Z, 4.3, z), z) for z in zz] + [(0.0, L + ext)]
            inner = [(hw(z) * 0.965 - 0.08, z) for z in zz] + [(0.0, L - 0.08)]
            y0, y1 = b0 - 0.018, b0 - 0.004
            for sx in (1, -1):
                for i in range(len(outer) - 1):
                    a, b, c_, d = outer[i], outer[i + 1], inner[i + 1], inner[i]
                    for yy, ow in ((y1, V(0, 1, 0)), (y0, V(0, -1, 0))):
                        m.face([P3(sx * a[0], yy, a[1]), P3(sx * b[0], yy, b[1]), P3(sx * c_[0], yy, c_[1]), P3(sx * d[0], yy, d[1])], e.style, el=e, outward=ow)
                    q = [P3(sx * a[0], y0, a[1]), P3(sx * b[0], y0, b[1]), P3(sx * b[0], y1, b[1]), P3(sx * a[0], y1, a[1])]
                    m.face(q, e.style, el=e, outward=V(sx * a[0], 0, a[1] - 3.8))
        if k in (2, 3):
            e = el(g, 'solid', 'carbon')
            n_can = 1 if k == 2 else 2
            for sx in (1, -1):
                for i in range(n_can):
                    z = 4.3 - i * 0.1
                    x = hw(z) * 0.97
                    y = 0.36 + i * 0.09
                    a = math.radians(14)
                    M = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
                    m.box(V(sx * (x + 0.035), y, z), (0.08, 0.008, 0.13), 'carbon', M=M)
        if k == 4:
            e = el(g, 'solid', 'towhook')
            m.box(V(-0.36, b0 + 0.06, L + 0.02), (0.04, 0.08, 0.02), 'towhook')


def rear_bumpers():
    for k in range(5):
        g = 'rb_%d' % k
        st = 'paint.rb%d' % k
        zs = [z for z in ZS if z <= RB_Z + 1e-6]
        loft(zs, section, only([g])(lower_route(rb=g, rbs=st)), down_bands=(0, 2))
        cap(0.0, section(0.0), (g, 'paint', st), V(0, 0, -1), lo=0, hi=4)
        y0 = bot(0.12)
        if k in (1, 2, 3):
            e = el(g, 'solid', 'carbon' if k == 3 else 'trim')
            depth = 0.3 if k == 3 else 0.2
            hgt = 0.1 if k == 3 else 0.06
            wdt = 0.62 if k == 3 else 0.5
            # floor plate
            m.face([P3(wdt, y0 - 0.004, depth), P3(-wdt, y0 - 0.004, depth), P3(-wdt, y0 - 0.004, -0.02), P3(wdt, y0 - 0.004, -0.02)], e.style, el=e,
                   outward=V(0, -1, 0))
            nf = 7 if k == 3 else 5
            for i in range(nf):
                x = -wdt * 0.85 + i * (2 * wdt * 0.85) / (nf - 1)
                m.prism([(y0 - 0.004, depth), (y0 - 0.004, -0.02), (y0 - hgt, -0.02), (y0 - 0.004, depth * 0.6)][::-1], x - 0.006, x + 0.006, 'x', e.style)
        if k == 3:
            e2 = el(g, 'solid', 'towhook')
            m.box(V(0.42, y0 + 0.04, -0.02), (0.035, 0.07, 0.02), 'towhook')


front_bumpers()
rear_bumpers()
TRIS = body_tris()

# --------------------------------------------------------------------------------------------- headlamps
def head_outline(sx):
    o = [(0.36, 4.47), (0.52, 4.44), (0.66, 4.385), (0.77, 4.3), (0.835, 4.18), (0.85, 4.06), (0.81, 4.02), (0.72, 4.07), (0.6, 4.18),
         (0.48, 4.3), (0.38, 4.39)]
    o = chaikin([(sx * x, z) for x, z in o], 1)
    return o if sx > 0 else o[::-1]


HEAD_LAMPS = [(0.475, 4.38, 0.056, 'head'), (0.615, 4.29, 0.054, 'high'), (0.745, 4.165, 0.036, 'ind')]


def headlamps():
    for sx in (1, -1):
        side = 'l' if sx > 0 else 'r'
        hous = el('body', 'solid', 'head_house')
        lens = el('body', 'glass', 'lens')
        proj = lamp_conform(head_outline(sx), 'T', TRIS, [(0.005, hous, 'head_house'), (0.016, lens, 'lens')],
                            el('body', 'solid', 'head_bezel'), 'head_bezel')
        for (x, z, r, kind) in HEAD_LAMPS:
            c, n = proj((sx * x, z), 0.005)
            mat = {'head': 'head', 'high': 'high', 'ind': 'ind_' + side}[kind]
            # chrome reflector bowl + emissive lamp face
            m.element('head_bowl_' + kind, 'solid', 'body', 'chrome')
            m.cylinder(c, n, r, 0.006, 12, 'chrome', r1=r * 0.92, caps=(False, False))
            m.element('head_lamp_' + kind, mat, 'body', 'proj_' + ('amber' if kind == 'ind' else kind))
            m.cylinder(c + n * 0.006, n, r * 0.92, 0.002, 12, 'proj_' + ('amber' if kind == 'ind' else kind), r1=r * 0.6, caps=(False, True))


def bumper_lamps():
    """Rectangular fog + indicator lamp units at the outer ends of the mouth (part of every front bumper variant)."""
    for k in range(5):
        g = 'fb_%d' % k
        tris = body_tris(('body', g))
        for sx in (1, -1):
            side = 'l' if sx > 0 else 'r'
            xc = sx * 0.66
            yc = 0.335 - FB_CHIN[k] * 0.3
            fog = rrect(0.15, 0.06, 0.012, 2, xc, yc)
            if sx < 0:
                fog = fog[::-1]
            e_fog = el(g, 'head', 'fog_lens')
            e_h = el(g, 'solid', 'lamp_house')
            lamp_plate(fog, 'F', tris, 0.01, V(0, 0, 1), [(0.002, e_h, 'lamp_house'), (0.012, e_fog, 'fog_lens')], wall_el=el(g, 'solid', 'trim'),
                       wall_style='trim')
            ind = rrect(0.1, 0.03, 0.01, 2, sx * 0.72, 0.43)
            if sx < 0:
                ind = ind[::-1]
            lamp_plate(ind, 'F', tris, 0.006, V(0, 0, 1), [(0.006, el(g, 'ind_' + side, 'amber'), 'amber')], wall_el=el(g, 'solid', 'trim'))


# --------------------------------------------------------------------------------------------- tail lamps
TAIL_LAMPS = [(0.62, 0.068, 'outer'), (0.385, 0.068, 'inner')]


def taillamps():
    for sx in (1, -1):
        side = 'l' if sx > 0 else 'r'
        xc = sx * 0.505
        house = rrect(0.47, 0.175, 0.085, 4, xc, TAIL_Y)
        if sx < 0:
            house = house[::-1]
        e = el('body', 'solid', 'tail_house')
        lamp_plate(house, 'B', TRIS, 0.02, V(0, 0, -1), [(0.022, e, 'tail_house')], wall_el=el('body', 'solid', 'tail_bezel'), wall_style='tail_bezel')
        zf = -0.022
        for (x, r, kind) in TAIL_LAMPS:
            c = V(sx * x, TAIL_Y, zf)
            m.element('tail_ring_chrome', 'solid', 'body', 'chrome')
            m.cylinder(c, V(0, 0, -1), r, 0.008, 14, 'chrome', r1=r * 0.95, caps=(False, False))
            m.element('tail_' + kind, 'tail', 'body', 'tail_ring')
            m.face([c + V(0, 0, -0.008) + V(math.cos(a) * r * 0.95, math.sin(a) * r * 0.95, 0) for a in np.linspace(0, 2 * math.pi, 14, endpoint=False)],
                   'tail_ring', outward=V(0, 0, -1), dens=160)
            core = 'brake' if kind == 'outer' else 'reverse'
            m.element('tail_core_' + kind, core, 'body', 'brake_core' if core == 'brake' else 'rev_core')
            m.cylinder(c + V(0, 0, -0.008), V(0, 0, -1), r * 0.5, 0.005, 12, 'brake_core' if core == 'brake' else 'rev_core', caps=(False, True))
        # indicator segment between the two round lamps
        m.element('tail_ind', 'ind_' + side, 'body', 'amber_dark')
        m.box(V(sx * 0.502, TAIL_Y, zf - 0.004), (0.035, 0.09, 0.008), 'amber_dark')
    # centre garnish (dark red panel with model lettering)
    gar = rrect(0.3, 0.11, 0.03, 2, 0.0, TAIL_Y + 0.005)
    lamp_plate(gar, 'B', TRIS, 0.006, V(0, 0, -1), [(0.008, el('body', 'solid', 'garnish'), 'garnish')], wall_el=el('body', 'solid', 'tail_bezel'), dens=320)


# --------------------------------------------------------------------------------------------- mirrors, wipers, handles
def mirrors():
    for sx in (1, -1):
        side = 'l' if sx > 0 else 'r'
        g = 'door_f' + side
        x = sx * (hw(3.0) - 0.08)
        y = belt(3.0) + 0.02
        m.element('mirror_base', 'solid', g, 'trim')
        m.box(V(x + sx * 0.03, y, 2.99), (0.08, 0.035, 0.06), 'trim')
        m.element('mirror_head', 'paint', g, 'paint')
        a = math.radians(-sx * 8)
        M = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        m.prism([(-0.065, 0.0), (0.06, 0.0), (0.05, 0.075), (-0.055, 0.07)], 2.96, 3.04, 'z', 'paint')
        e = m.elements[-1]
        for f in e.faces[-6:]:
            f.verts = [V(p[0] + x + sx * 0.12 - 0.0, p[1] + y + 0.025, p[2]) for p in f.verts]
        m.element('mirror_glass', 'solid', g, 'mirror')
        m.face([V(x + sx * 0.06, y + 0.03, 2.957), V(x + sx * 0.18, y + 0.03, 2.957), V(x + sx * 0.17, y + 0.095, 2.957), V(x + sx * 0.065, y + 0.09, 2.957)],
               'mirror', outward=V(0, 0, -1))


def wipers():
    m.element('wipers', 'solid', 'body', 'trim_gloss')
    for x0, x1 in ((0.62, 0.08), (0.04, -0.44)):
        z0 = WS1 - 0.03
        p0 = V(x0, deck_z(x0, z0) + 0.04, z0 - 0.02)
        p1 = V(x1, deck_z(x1, z0) + 0.035, z0 - 0.005)
        d = unit(p1 - p0)
        c = (p0 + p1) / 2
        ang = math.atan2(d[2], d[0])
        M = np.array([[math.cos(-ang), 0, math.sin(-ang)], [0, 1, 0], [-math.sin(-ang), 0, math.cos(-ang)]])
        m.box(c, (np.linalg.norm(p1 - p0), 0.012, 0.012), 'trim_gloss', M=M)


# --------------------------------------------------------------------------------------------- hood variants
HD_NAMES = ['Factory', 'Carbon', 'Vented', 'Ram-air scoop', 'Carbon vented']


def clip_poly(poly, axis, val, keep_less=True):
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


def hood_panel(g, style, mat='solid', lift=0.004):
    e = el(g, mat, style)
    for z0, z1 in zip(ZS[:-1], ZS[1:]):
        if z0 < 3.12 - 1e-6 or z1 > 4.36 + 1e-6:
            continue
        A, B = section(z0), section(z1)
        for j in (7, 8):
            for sx in (1, -1):
                q = [V(sx * A[j][0], A[j][1], z0), V(sx * B[j][0], B[j][1], z1), V(sx * B[j + 1][0], B[j + 1][1], z1), V(sx * A[j + 1][0], A[j + 1][1], z0)]
                q = clip_poly(q, 0, 0.62, True) if sx > 0 else clip_poly(q, 0, -0.62, False)
                if len(q) < 3:
                    continue
                n = unit(newell(q))
                if n[1] < 0:
                    n = -n
                m.face([p + n * lift for p in q], style, el=e, outward=V(0, 1, 0))


def hoods():
    for k in range(5):
        g = 'hd_%d' % k
        if k == 0:
            m.element('hood_stock', 'solid', g, 'trim')   # empty group marker
            continue
        if k in (1, 4):
            hood_panel(g, 'carbon_hood')
        if k in (2, 4):
            e = el(g, 'solid', 'vent_slats')
            for sx in (1, -1):
                for i in range(4):
                    z = 3.55 + i * 0.06
                    x = sx * 0.4
                    y = deck_z(x, z) + 0.01
                    m.box(V(x, y, z), (0.24, 0.012, 0.03), 'vent_slats')
            if k == 2:
                hood_panel(g, 'paint.hoodvent', 'paint', 0.003)
        if k == 3:
            e = el(g, 'paint', 'paint')
            y = deck_z(0, 3.6)
            prof = [(3.35, y - 0.01), (3.95, y - 0.01), (3.75, y + 0.06), (3.42, y + 0.065)]
            from parts import prism_x
            prism_x(m, prof, -0.2, 0.2, 'paint')
            m.element('scoop_mouth', 'solid', g, 'grille')
            m.face([V(0.18, y + 0.0, 3.74), V(-0.18, y + 0.0, 3.74), V(-0.18, y + 0.055, 3.755), V(0.18, y + 0.055, 3.755)], 'grille', outward=V(0, 0.2, 1))


# --------------------------------------------------------------------------------------------- skirts
SK_NAMES = ['Factory', 'Extended skirts', 'Skirts + winglets']


def skirts():
    for k in range(3):
        g = 'sk_%d' % k
        if k == 0:
            m.element('skirt_stock', 'solid', g, 'trim')
            continue
        e = el(g, 'paint', 'paint')
        for sx in (1, -1):
            z0, z1 = YR + RA + 0.01, YF - RA - 0.01
            w = hw(2.2)
            b = bot(2.2)
            prof = [(sx * (w * 0.93), b + 0.06), (sx * (w * 1.01), b + 0.05), (sx * (w * 1.02), b - 0.035), (sx * (w * 0.9), b - 0.04)]
            if sx < 0:
                prof = prof[::-1]
            m.prism(prof, z0, z1, 'z', 'paint')
            if k == 2:
                ew = el(g, 'solid', 'carbon')
                m.box(V(sx * (w * 1.0 + 0.02), b - 0.02, z0 + 0.06), (0.06, 0.008, 0.16), 'carbon')
                m.box(V(sx * (w * 1.0 + 0.02), b - 0.02, z1 - 0.06), (0.06, 0.008, 0.16), 'carbon')


# --------------------------------------------------------------------------------------------- exhaust + spoiler + wheels
def exhausts():
    for k, kind in enumerate(PT.EXHAUSTS):
        PT.exhaust(m, 'ex_%d' % k, kind, -0.035, 0.3, x_main=-0.5)


def spoilers():
    deck = lambda x, z: max(deck_z(x, z), belt(z))
    for k, kind in enumerate(PT.SPOILERS):
        PT.spoiler(m, 'sp_%d' % k, kind, deck, 0.04, 0.7)


def wheels():
    for k, kind in enumerate(PT.WHEELS):
        PT.wheel(m, 'wh_%d' % k, kind, R=R, add_caliper=(k == 0))


# --------------------------------------------------------------------------------------------- interior
STEER_C = V(0.3, 0.83, 2.52)
STEER_AX = unit(V(0, math.sin(math.radians(24)), -math.cos(math.radians(24))))   # points at the driver
CL_C = V(0.3, 0.935, 2.745)


def rx(a):
    a = math.radians(a)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def ry(a):
    a = math.radians(a)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])


def tbox(c, size, style, M=None, el_=None):
    m.box(c, size, style, M=M)


def interior():
    e = el('body', 'solid', 'carpet')
    m.box(V(0, 0.225, 2.0), (1.5, 0.02, 2.3), 'carpet')
    m.prism([(-0.13, 0.22), (0.13, 0.22), (0.1, 0.36), (-0.1, 0.36)], 1.3, 3.0, 'z', 'carpet')
    for sx in (1, -1):
        x = sx * 0.3
        zc = 2.0
        el('body', 'solid', 'leather')
        m.box(V(x, 0.28, zc), (0.42, 0.08, 0.46), 'leather')
        el('body', 'solid', 'seat_fabric')
        m.box(V(x, 0.335, zc + 0.01), (0.32, 0.05, 0.42), 'seat_fabric', M=rx(-5))
        el('body', 'solid', 'leather')
        for bx in (1, -1):
            m.box(V(x + bx * 0.19, 0.35, zc + 0.01), (0.07, 0.09, 0.42), 'leather', M=rx(-5))
        # backrest leaning back 16 degrees about its bottom edge
        Mb = rx(-16)
        pivot = V(x, 0.33, zc - 0.23)
        def at(local):
            return pivot + Mb @ V(local)
        m.box(at((0, 0.3, -0.03)), (0.42, 0.6, 0.1), 'leather', M=Mb)
        el('body', 'solid', 'seat_fabric')
        m.box(at((0, 0.29, 0.025)), (0.28, 0.46, 0.02), 'seat_fabric', M=Mb)
        el('body', 'solid', 'leather')
        for bx in (1, -1):
            m.box(at((bx * 0.19, 0.27, 0.02)), (0.07, 0.46, 0.12), 'leather', M=Mb)
        m.box(at((0, 0.7, -0.02)), (0.24, 0.16, 0.08), 'leather', M=Mb)
        # rear 2+2 seat
        el('body', 'solid', 'seat_fabric')
        m.box(V(sx * 0.3, 0.31, 1.42), (0.4, 0.09, 0.4), 'seat_fabric')
        m.box(V(sx * 0.3, 0.52, 1.2), (0.4, 0.42, 0.1), 'seat_fabric', M=rx(-26))
    # dash
    el('body', 'solid', 'dash')
    from parts import prism_x
    prism_x(m, [(3.05, 0.86), (2.78, 0.9), (2.68, 0.885), (2.62, 0.83), (2.62, 0.7), (2.7, 0.56), (2.95, 0.42), (3.1, 0.42), (3.12, 0.8)][::-1],
            -0.83, 0.83, 'dash')
    el('body', 'solid', 'dash2')
    prism_x(m, [(2.7, 0.56), (2.95, 0.42), (3.0, 0.5), (2.76, 0.6)][::-1], -0.8, 0.8, 'dash2')
    # binnacle hood around the three-gauge cluster (driver side, +X)
    el('body', 'solid', 'dash')
    binn = [(-0.22, 0.0), (-0.21, 0.05), (-0.15, 0.1), (0.0, 0.12), (0.15, 0.1), (0.21, 0.05), (0.22, 0.0)]
    m.prism([(0.3 + a, 0.87 + b) for a, b in binn][::-1], 2.75, 2.88, 'z', 'dash')
    m.box(V(0.3, 0.99, 2.71), (0.46, 0.02, 0.09), 'dash')
    el('body', 'solid', 'gauges')
    m.face([V(0.49, 0.885, 2.75), V(0.11, 0.885, 2.75), V(0.11, 0.985, 2.74), V(0.49, 0.985, 2.74)], 'gauges', outward=V(0, 0, -1), dens=256)
    # centre stack angled towards the driver
    M = ry(12) @ rx(8)
    el('body', 'solid', 'dash')
    m.box(V(-0.02, 0.62, 2.66), (0.26, 0.32, 0.06), 'dash', M=M)
    el('body', 'solid', 'stack_face')
    m.box(V(-0.02 + 0.006, 0.62, 2.626), (0.22, 0.28, 0.006), 'stack_face', M=M)
    el('body', 'solid', 'grille')
    for vx in (0.66, -0.62):
        m.box(V(vx, 0.78, 2.625), (0.09, 0.045, 0.012), 'grille')
    # console, shifter, handbrake, armrest
    el('body', 'solid', 'dash')
    m.box(V(0, 0.4, 2.3), (0.2, 0.1, 0.75), 'dash')
    el('body', 'solid', 'alu')
    m.box(V(0, 0.452, 2.47), (0.12, 0.004, 0.2), 'alu')
    m.cylinder(V(0, 0.45, 2.47), V(0, 1, 0), 0.008, 0.12, 6, 'alu')
    el('body', 'solid', 'leather')
    m.box(V(0, 0.465, 2.47), (0.09, 0.03, 0.09), 'leather')
    m.box(V(0, 0.59, 2.47), (0.042, 0.042, 0.042), 'leather')
    m.box(V(0, 0.47, 2.08), (0.035, 0.03, 0.22), 'leather', M=rx(-12))
    m.box(V(0, 0.5, 1.85), (0.18, 0.06, 0.3), 'leather')
    # steering wheel (part steer): 12-segment rim, 4 spokes, hub
    el('steer', 'solid', 'leather')
    ax = STEER_AX
    u = unit(np.cross(V(1, 0, 0), ax)) if abs(ax[0]) < 0.9 else V(0, 1, 0)
    u = V(1, 0, 0)
    w = np.cross(ax, u)
    Rr, t = 0.185, 0.02
    N = 12
    pts = [STEER_C + (u * math.cos(a) + w * math.sin(a)) * Rr for a in np.linspace(0, 2 * math.pi, N, endpoint=False)]
    for i in range(N):
        p0, p1 = pts[i], pts[(i + 1) % N]
        rd0, rd1 = unit(p0 - STEER_C), unit(p1 - STEER_C)
        def sq(p, rd):
            return [p + rd * t + ax * t, p + rd * t - ax * t, p - rd * t - ax * t, p - rd * t + ax * t]
        A, B = sq(p0, rd0), sq(p1, rd1)
        for k in range(4):
            q = [A[k], B[k], B[(k + 1) % 4], A[(k + 1) % 4]]
            m.face(q, 'leather', outward=np.mean(q, axis=0) - (p0 + p1) / 2)
    Mw = np.c_[u, w, ax]
    el('steer', 'solid', 'dash')
    for a in (0, 180, 240, 300):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        Ms = Mw @ np.array([[ca, -sa, 0], [sa, ca, 0], [0, 0, 1]])
        m.box(STEER_C + Ms @ V(0.1, 0, 0.012), (0.15, 0.032, 0.014), 'dash', M=Ms)
    el('steer', 'solid', 'leather')
    m.box(STEER_C + ax * 0.025, (0.15, 0.12, 0.05), 'leather', M=Mw)
    el('body', 'solid', 'dash')
    m.cylinder(STEER_C, -ax, 0.035, 0.24, 8, 'dash')
    # pedals
    el('body', 'solid', 'alu')
    for px, w_ in ((0.42, 0.06), (0.32, 0.08), (0.2, 0.09)):
        m.box(V(px, 0.33, 2.92), (w_, 0.075, 0.012), 'alu', M=rx(20))
    # door cards (move with the doors)
    for sx in (1, -1):
        g = 'door_f' + ('l' if sx > 0 else 'r')
        x = sx * 0.8
        el(g, 'solid', 'door_card')
        m.face([V(x, 0.32, DOOR0 + 0.04), V(x, 0.34, DOOR1 - 0.06), V(x, 0.86, DOOR1 - 0.04), V(x, 0.9, DOOR0 + 0.04)], 'door_card', outward=V(-sx, 0, 0))
        el(g, 'solid', 'leather')
        m.box(V(sx * 0.77, 0.62, 2.25), (0.05, 0.04, 0.4), 'leather')
        el(g, 'solid', 'dash')
        m.box(V(sx * 0.785, 0.89, 2.4), (0.04, 0.035, 1.3), 'dash')
    el('body', 'solid', 'dash')
    m.box(V(0, 1.16, 2.66), (0.2, 0.055, 0.03), 'dash', M=rx(-10))
    el('body', 'solid', 'mirror')
    m.box(V(0, 1.16, 2.643), (0.18, 0.042, 0.004), 'mirror', M=rx(-10))
    el('body', 'solid', 'carpet')
    m.box(V(0, 0.86, 0.65), (1.4, 0.02, 0.45), 'carpet')


# --------------------------------------------------------------------------------------------- arch liners
def liners():
    e = el('body', 'solid', 'under')
    for za in (YR, YF):
        for sx in (1, -1):
            angs = np.linspace(math.radians(-10), math.radians(190), 10)
            for a0, a1 in zip(angs[:-1], angs[1:]):
                q = [V(sx * 0.6, R + RA * math.sin(a0), za + RA * math.cos(a0)), V(sx * 0.6, R + RA * math.sin(a1), za + RA * math.cos(a1)),
                     V(sx * 0.86, R + RA * math.sin(a1), za + RA * math.cos(a1)), V(sx * 0.86, R + RA * math.sin(a0), za + RA * math.cos(a0))]
                m.face(q, 'under', el=e, outward=V(0, R - np.mean(q, axis=0)[1], za - np.mean(q, axis=0)[2]), dens=8)


headlamps()
bumper_lamps()
taillamps()
mirrors()
wipers()
hoods()
skirts()
exhausts()
spoilers()
wheels()
interior()
liners()


# ============================================================================================= texture painters
GAP = (150, 150, 150, 255)      # panel gaps: darker shade of the paint colour
BLACK = (24, 24, 26, NOTINT)


def put(out, mask, c):
    out[mask] = np.array(c, float)


def paint_painter(face, P, n):
    st = face.style
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    out = fill(P, (236, 236, 236))
    out[..., :3] += noise(P, 3.0, 40)[..., None]
    side = np.abs(n[0]) > 0.45
    top = n[1] > 0.45
    front = n[2] > 0.45
    rear = n[2] < -0.45
    px = 1.0 / face.dens
    if side:
        # doors
        door = (y > 0.235) & (y < 1.0)
        for zz in (DOOR0, DOOR1):
            put(out, door & (np.abs(z - zz) < px * 0.6), GAP)
        # handle (flush, rear top of the door)
        put(out, (z > 1.85) & (z < 1.97) & (np.abs(y - 0.862) < 0.014), BLACK)
        # bumper seams
        put(out, (np.abs(z - FB_Z - (y - 0.3) * 0.12) < px * 0.6) & (y < 0.78), GAP)
        put(out, (np.abs(z - RB_Z) < px * 0.6) & (y < 0.62), GAP)
        # black rocker strip
        put(out, (y < 0.245) & (z > 1.37) & (z < 3.18), BLACK)
        # side markers near the nose
        put(out, (z > 4.2) & (z < 4.32) & (np.abs(y - 0.47) < 0.018), (255, 150, 40, NOTINT))
        # fuel door (right side)
        if n[0] < 0:
            put(out, np.abs(np.hypot(z - 0.82, y - 0.84) - 0.058) < px * 0.6, GAP)
    if top:
        ax = np.abs(x)
        # hood shut lines
        hood = (z > 3.12) & (z < 4.42)
        put(out, hood & (np.abs(ax - (0.64 - np.clip(z - 3.9, 0, 1) * 0.5)) < px * 0.6) & (z < 4.36), GAP)
        put(out, (np.abs(z - (4.43 - (ax / 0.6) ** 2 * 0.1)) < px * 0.6) & (ax < 0.6), GAP)
        put(out, (np.abs(z - 3.13) < px * 0.6) & (ax < 0.64), GAP)
        # hatch side lines
        put(out, (z < CABIN0 + 0.02) & (np.abs(ax - 0.69) < px * 0.6), GAP)
        if 'hoodvent' in st:
            for sx in (1, -1):
                put(out, (np.abs(x - sx * 0.4) < 0.13) & (z > 3.52) & (z < 3.76), (20, 20, 22, NOTINT))
    if rear:
        put(out, np.abs(y - 0.858) < px * 0.6, GAP)
    if 'fb' in st:
        k = int(st.split('fb')[1][0])
        if front or (n[2] > 0.2):
            mouth_w = [1.04, 1.04, 1.12, 1.16, 1.1][k]
            yc = 0.335 - FB_CHIN[k] * 0.4
            mh = [0.15, 0.15, 0.17, 0.19, 0.16][k]
            uv = np.stack([x, y], -1)
            from bb import inside as _in
            mouth = _in(uv, rrect(mouth_w, mh, 0.07, 4, 0, yc))
            if k == 4:
                mouth = _in(uv, rrect(0.5, mh, 0.05, 3, 0, yc)) | _in(uv, rrect(0.2, mh * 0.8, 0.04, 3, 0.45, yc)) | _in(uv, rrect(0.2, mh * 0.8, 0.04, 3, -0.45, yc))
            hole = S.honeycomb(P, uv, 0.024 if k != 2 else 0.018)
            put(out, mouth, (40, 41, 44, NOTINT))
            put(out, mouth & hole, (8, 8, 10, NOTINT))
            edge = mouth & ~_in(uv, rrect(mouth_w - 0.03, mh - 0.03, 0.055, 4, 0, yc)) if k != 4 else np.zeros_like(mouth)
            put(out, edge, (14, 14, 16, NOTINT))
            # small nose slot under the bonnet line
            put(out, _in(uv, rrect(0.3, 0.026, 0.012, 2, 0, 0.505)), (14, 14, 16, NOTINT))
            if k == 2:
                for sx in (1, -1):
                    put(out, _in(uv, rrect(0.12, 0.05, 0.02, 2, sx * 0.62, 0.43)) & False, BLACK)
    if 'rb' in st:
        k = int(st.split('rb')[1][0])
        if rear:
            uv = np.stack([x, y], -1)
            from bb import inside as _in
            put(out, _in(uv, rrect(0.58, 0.17, 0.03, 3, 0, 0.52)), (22, 22, 24, NOTINT))      # plate recess
            for sx in (1, -1):
                put(out, _in(uv, rrect(0.17, 0.026, 0.01, 2, sx * 0.64, 0.43)), (160, 16, 26, NOTINT))   # reflectors
                if k == 2:
                    for i in range(3):
                        put(out, _in(uv, rrect(0.12, 0.014, 0.006, 1, sx * 0.6, 0.39 - i * 0.025)), BLACK)
            if k == 4:
                put(out, (y < 0.42), (26, 26, 28, NOTINT))
                for sx in (1, -1):
                    put(out, _in(uv, rrect(0.2, 0.09, 0.04, 3, sx * 0.5, 0.33)), (8, 8, 10, NOTINT))
    out[..., 3] = np.where(out[..., 3] < 250, out[..., 3], 255)
    return out


def head_house(face, P, n):
    """Chrome housing behind the clear lens: reflector rings around the three lamps + dark separators."""
    out = fill(P, (150, 156, 164))
    out[..., :3] += noise(P, 8, 128)[..., None]
    sx = np.sign(np.mean(P[..., 0]))
    for (x, z, r, kind) in HEAD_LAMPS:
        d = np.hypot(P[..., 0] - sx * x, P[..., 2] - z)
        out[(d > r * 1.02) & (d < r * 1.25), :3] = (210, 214, 220)
        out[(d > r * 1.25) & (d < r * 1.32), :3] = (90, 94, 100)
    # dark frame around the edge of the unit
    rim = seg_dist(np.stack([P[..., 0], P[..., 2]], -1), head_outline(int(sx)), closed=True) < 0.014
    out[rim, :3] = (34, 36, 40)
    return out


def proj(kind):
    def f(face, P, n):
        c = np.mean(np.array(face.verts), axis=0)
        d = np.linalg.norm(P - c, axis=-1)
        r = max(np.linalg.norm(v - c) for v in face.verts) if len(face.verts) > 4 else 0.05
        rn = d / max(r, 1e-3)
        if kind == 'amber':
            out = fill(P, (255, 160, 50))
            out[rn < 0.5, :3] = (255, 200, 120)
        else:
            out = fill(P, (236, 240, 244))
            out[(rn > 0.55) & (rn < 0.7), :3] = (180, 186, 194)
            out[rn < 0.3, :3] = (255, 255, 250) if kind == 'head' else (246, 248, 255)
        return out
    return f


def lens(face, P, n):
    out = fill(P, (236, 242, 246))
    q = P[..., 0] * 0.7 + P[..., 2]
    out[np.mod(q * 9, 1.0) < 0.08, :3] = (255, 255, 255)
    return out


def tail_ring(face, P, n):
    c = np.mean(np.array(face.verts), axis=0)
    d = np.linalg.norm(P - c, axis=-1)
    r = max(np.linalg.norm(v - c) for v in face.verts)
    rn = d / r
    out = fill(P, (176, 14, 24))
    a = np.arctan2(P[..., 1] - c[1], P[..., 0] - c[0])
    out[np.mod(a * 12 / math.pi, 1.0) < 0.35, :3] = (150, 10, 20)
    out[(rn > 0.5) & (rn < 0.6), :3] = (210, 40, 50)
    out[rn > 0.9, :3] = (120, 8, 16)
    return out


def tail_house(face, P, n):
    out = fill(P, (64, 12, 18))
    out[np.mod(P[..., 1] * 140, 1.0) < 0.3, :3] = (78, 16, 22)
    return out


def garnish(face, P, n):
    out = fill(P, (60, 12, 18))
    uv = np.stack([P[..., 0] * -1.0, -(P[..., 1])], -1)     # seen from behind: x mirrored
    t = S.text_mask(uv, 'SUPRA', -0.105, -(TAIL_Y + 0.025), 0.05)
    out[t, :3] = (200, 200, 205)
    return out


def gauges(face, P, n):
    out = fill(P, (12, 12, 14))
    x, y = P[..., 0], P[..., 1]
    for cx, r in ((0.42, 0.045), (0.3, 0.056), (0.18, 0.045)):
        cy = 0.935
        d = np.hypot(x - cx, y - cy)
        a = np.arctan2(y - cy, x - cx)
        out[(d > r * 0.92) & (d < r), :3] = (150, 152, 158)
        tick = (d > r * 0.72) & (d < r * 0.88) & (np.mod((a + math.pi) * 24 / (2 * math.pi), 1.0) < 0.25) & (a > -math.pi * 0.25) | \
               (d > r * 0.72) & (d < r * 0.88) & (np.mod((a + math.pi) * 24 / (2 * math.pi), 1.0) < 0.25) & (a < -math.pi * 0.75)
        out[tick, :3] = (230, 230, 230)
        out[d < r * 0.08, :3] = (200, 60, 30)
    return out


def stack_face(face, P, n):
    out = fill(P, (30, 30, 33))
    y = P[..., 1]
    out[(y > 0.69) & (y < 0.74), :3] = (18, 18, 20)
    out[(y > 0.70) & (y < 0.73) & (np.mod(P[..., 0] * 60, 1.0) < 0.4), :3] = (60, 140, 90)
    out[(y > 0.52) & (y < 0.6) & (np.mod(P[..., 0] * 22, 1.0) < 0.5), :3] = (70, 70, 74)
    return out


def door_card(face, P, n):
    out = fill(P, (52, 52, 56))
    y = P[..., 1]
    out[(y > 0.42) & (y < 0.58), :3] = (40, 40, 43)
    out[np.abs(y - 0.7) < 0.006, :3] = (70, 70, 74)
    return out


m.painters.update({
    'paint': paint_painter, 'head_house': head_house, 'lens': lens, 'proj_head': proj('head'), 'proj_high': proj('high'),
    'proj_amber': proj('amber'), 'tail_ring': tail_ring, 'tail_house': tail_house, 'garnish': garnish, 'gauges': gauges,
    'stack_face': stack_face, 'door_card': door_card, 'fog_lens': S.clear_lens, 'head_bezel': S.flat((40, 42, 46), 3),
    'tail_bezel': S.flat((40, 10, 14), 3), 'brake_core': S.flat((210, 22, 34), 3), 'rev_core': S.flat((236, 236, 236), 3),
    'amber_dark': S.flat((230, 120, 30), 3), 'carbon_hood': S.carbon_untinted, 'vent_slats': S.flat((22, 22, 24), 2),
    'towhook': S.flat((210, 30, 30), 3), 'seat_fabric': S.fabric,
})

META = dict(
    id=ID, name='Toyota Supra MK4', make='Toyota', model='Supra MK4 (A80)', year=1993, category='sports', category_name='Sports coupe',
    description='Front-engine, rear-drive grand tourer with a twin-turbo straight six, famous for its tuning potential.',
    length=L, width=W, height=H, wheelbase=WB,
    wheels=[dict(x=sx * TRACK, y=R, z=za - L / 2, r=R, steer=za > 2, mirror=sx < 0) for za in (YR, YF) for sx in (1, -1)],
    seats=[[0.3, 0.4, 2.0 - L / 2], [-0.3, 0.4, 2.0 - L / 2], [0.3, 0.38, 1.42 - L / 2], [-0.3, 0.38, 1.42 - L / 2]],
    doors={'door_fl': dict(hinge=[round(hw(DOOR1) - 0.03, 3), round(DOOR1 - L / 2, 3)], side=1, kind='front'),
           'door_fr': dict(hinge=[round(-(hw(DOOR1) - 0.03), 3), round(DOOR1 - L / 2, 3)], side=-1, kind='front')},
    steer=dict(pivot=list(STEER_C + V(0, 0, -L / 2)), axis=list(STEER_AX)),
    cluster=dict(center=[0.3, 0.935, 2.746 - L / 2], right=[-1, 0, 0], up=[0, 0.995, 0.1], w=0.38, h=0.1),
    beams=[[sx * 0.6, 0.66, 4.33 - L / 2] for sx in (1, -1)],
    plates=[dict(pos=[0, 0.335, L + 0.005 - L / 2], normal=[0, 0, 1], w=0.52, h=0.12), dict(pos=[0, 0.52, -0.01 - L / 2], normal=[0, 0, -1], w=0.52, h=0.12)],
    filler=[-0.92, 0.84, 0.82 - L / 2],
    catalog=dict(fb=[dict(name=n) for n in FB_NAMES], rb=[dict(name=n) for n in RB_NAMES], ex=[dict(name=n) for n in PT.EXHAUST_NAMES],
                 sp=[dict(name=n) for n in PT.SPOILER_NAMES], hd=[dict(name=n) for n in HD_NAMES], wh=[dict(name=n) for n in PT.WHEEL_NAMES],
                 sk=[dict(name=n) for n in SK_NAMES]),
    exhaust_variants=[[[p[0], p[1], p[2] - L / 2, p[3], p[4], p[5]] for p in PT.exhaust_points(k, -0.035, 0.3, -0.5)] for k in PT.EXHAUSTS],
)


def build(out_dir):
    import json
    m.pack()
    from PIL import Image
    atlas = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'src', 'main', 'resources', 'assets', 'trafficcraft', 'textures', 'vehicle', 'atlas.png')
    mc = Image.open(atlas).convert('RGBA').crop((64, 128, 128, 192))
    m.paint(mc)
    META['matcap'] = [m.matcap_rect[0] / m.tex, m.matcap_rect[1] / m.tex, m.matcap_rect[2] / m.tex, m.matcap_rect[3] / m.tex]
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, ID + '.bbmodel')
    m.write_bbmodel(path, META)
    nf = sum(len(e.faces) for e in m.elements)
    print('wrote', path, 'elements', len(m.elements), 'faces', nf, 'texture', m.tex)
    return path


if __name__ == '__main__':
    build(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', 'models'))
