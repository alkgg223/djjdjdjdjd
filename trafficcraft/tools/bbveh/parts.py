"""Universal customisation parts shared by every car: 5 wheels, 5 spoilers, exhaust tips.
Each builder adds elements to a bb.Model inside the given outliner group."""
import math
import numpy as np
from bb import V, unit, ngon, rrect, fill, noise, NOTINT, seg_dist
import styles as S

WHEELS = ['spoke5', 'spoke6', 'mesh', 'dish', 'turbine']
WHEEL_NAMES = ['Factory 5-spoke', 'Forged 6-spoke', 'Cross mesh', 'Deep dish', 'Turbine 10-spoke']
SPOILERS = ['hoop', 'ducktail', 'low_wing', 'gt_wing', 'ta_wing']
SPOILER_NAMES = ['Hoop wing', 'Ducktail lip', 'Low pedestal wing', 'Swan-neck GT wing', 'Time-attack wing']
EXHAUSTS = ['oval', 'dual', 'big', 'quad', 'center']
EXHAUST_NAMES = ['Factory oval tip', 'Dual round tips', 'Single titanium cannon', 'Quad tips', 'Centre exit']


# ------------------------------------------------------------------------------------------------ helpers
def prism_x(m, outline_zy, x0, x1, style, **kw):
    """Extrude a side-view outline [(z, y)] along X."""
    m.prism([(y, z) for z, y in outline_zy], x0, x1, 'x', style, **kw)


def tube(m, outline_xy, z0, z1, style_out, style_end, style_in, inner=0.78):
    """Exhaust-like tube along Z from z0 to z1 (z1 = exit), with a rolled lip and dark inside."""
    m.prism(outline_xy, z0, z1, 'z', style_out, cap0=False, cap1=False)
    c = np.mean(np.array(outline_xy), axis=0)
    n = len(outline_xy)
    inn = [tuple(c + (np.array(p) - c) * inner) for p in outline_xy]
    d = np.sign(z1 - z0)
    for k in range(n):
        a, b = outline_xy[k], outline_xy[(k + 1) % n]
        ia, ib = inn[k], inn[(k + 1) % n]
        m.face([V(a[0], a[1], z1), V(b[0], b[1], z1), V(ib[0], ib[1], z1), V(ia[0], ia[1], z1)], style_end, outward=V(0, 0, d))
    m.face([V(p[0], p[1], z1 - d * 0.03) for p in inn], style_in, outward=V(0, 0, d))


# ------------------------------------------------------------------------------------------------ wheels
def rim_painter(kind):
    """Rim face texture in wheel-local polar coordinates; spoke gaps are transparent (cut out)."""
    col = {'spoke5': (200, 204, 210), 'spoke6': (150, 120, 70), 'mesh': (206, 208, 212), 'dish': (60, 62, 66), 'turbine': (90, 94, 100)}[kind]

    def f(face, P, n):
        r = np.linalg.norm(P[..., 1:], axis=-1)
        a = np.arctan2(P[..., 2], P[..., 1])
        R = 0.216
        rn = r / R
        out = fill(P, col)
        out[..., :3] += noise(P, 4, 256)[..., None]
        if kind == 'spoke5':
            k, w0, w1 = 5, 0.11, 0.07
        elif kind == 'spoke6':
            k, w0, w1 = 6, 0.07, 0.06
        elif kind == 'dish':
            k, w0, w1 = 5, 0.09, 0.08
        elif kind == 'turbine':
            k, w0, w1 = 10, 0.05, 0.035
        else:
            k, w0, w1 = 10, 0.03, 0.03
        if kind == 'mesh':
            a1 = np.mod((a + rn * 0.9) * 10 / (2 * math.pi), 1.0)
            a2 = np.mod((a - rn * 0.9) * 10 / (2 * math.pi), 1.0)
            spoke = (np.abs(a1 - 0.5) > 0.42) | (np.abs(a2 - 0.5) > 0.42)
        else:
            ph = np.mod(a * k / (2 * math.pi) + (rn * 0.25 if kind == 'turbine' else 0), 1.0)
            half = (w0 + (w1 - w0) * rn) * k / (2 * math.pi) / np.maximum(rn, 0.2) * 1.6
            spoke = (ph < half) | (ph > 1 - half)
        ring = rn > 0.9
        hub = rn < (0.36 if kind != 'mesh' else 0.3)
        solid = spoke | ring | hub
        out[~solid, 3] = 0
        # darker spoke edges, lug nuts and centre cap
        edge = solid & ~ring & ~hub & ~(np.roll(solid, 1, 0) & np.roll(solid, -1, 0) & np.roll(solid, 1, 1) & np.roll(solid, -1, 1))
        out[edge, :3] *= 0.7
        lug = np.zeros_like(rn, bool)
        for i in range(5):
            aa = 2 * math.pi * i / 5 + math.pi / 5
            lug |= np.hypot(P[..., 1] - 0.05 * math.cos(aa), P[..., 2] - 0.05 * math.sin(aa)) < 0.009
        out[lug, :3] = (110, 112, 116)
        out[rn < 0.12, :3] = (40, 40, 44) if kind != 'spoke6' else (30, 30, 32)
        if kind == 'spoke6':
            out[(rn > 0.88) & (rn < 0.92), :3] = (60, 60, 60)
        return out
    return f


def wheel(m, group, kind, R=0.322, TW=0.245, Rr=0.216, seg=16, caliper_color=(140, 142, 148), add_caliper=True):
    """Wheel centred at the origin, axis X, outer face +X (game convention)."""
    h = TW / 2
    m.painters['rim_' + kind] = rim_painter(kind)
    rot = math.pi / seg
    m.element('tyre', 'solid', group, 'tyre')
    # tread with chamfered shoulders
    m.cylinder(V(-h + 0.02, 0, 0), V(1, 0, 0), R, TW - 0.04, seg, 'tread', caps=(False, False), rot=rot)
    m.cylinder(V(h - 0.02, 0, 0), V(1, 0, 0), R, 0.02, seg, 'tyre', r1=R - 0.018, caps=(False, False), rot=rot)
    m.cylinder(V(-h + 0.02, 0, 0), V(-1, 0, 0), R, 0.02, seg, 'tyre', r1=R - 0.018, caps=(False, False), rot=rot)
    # sidewalls (annuli)
    for sx in (1, -1):
        r0, r1 = R - 0.018, Rr + 0.012
        ring0 = ngon(r0, seg, rot=rot)
        ring1 = ngon(r1, seg, rot=rot)
        for k in range(seg):
            q = [V(sx * h, *ring0[k]), V(sx * h, *ring0[(k + 1) % seg]), V(sx * (h - 0.004), *ring1[(k + 1) % seg]), V(sx * (h - 0.004), *ring1[k])]
            m.face(q, 'tyre', outward=V(sx, 0, 0), dens=64)
    m.element('rim', 'solid', group, 'alu')
    lipx = h - 0.004
    depth = 0.075 if kind == 'dish' else 0.03
    # polished lip
    for k in range(seg):
        a0, a1 = ngon(Rr + 0.012, seg, rot=rot), ngon(Rr, seg, rot=rot)
        q = [V(lipx, *a0[k]), V(lipx, *a0[(k + 1) % seg]), V(lipx + 0.006, *a1[(k + 1) % seg]), V(lipx + 0.006, *a1[k])]
        m.face(q, 'chrome' if kind in ('dish', 'mesh') else 'alu', outward=V(1, 0, 0))
    # barrel down to the face
    m.cylinder(V(lipx - depth, 0, 0), V(1, 0, 0), Rr, depth + 0.006, seg, 'chrome' if kind == 'dish' else 'alu', caps=(False, False), rot=rot)
    m.element('rim_face', 'solid', group, 'rim_' + kind)
    m.face([V(lipx - depth, *p) for p in ngon(Rr, seg, rot=rot)], 'rim_' + kind, outward=V(1, 0, 0), dens=200)
    m.element('hub', 'solid', group, 'alu')
    m.cylinder(V(lipx - depth, 0, 0), V(1, 0, 0), 0.042, 0.014, 8, 'alu', cap_style='trim')
    # inside of the wheel: brake disc + dark backing so nothing shows through the spokes
    m.element('rotor', 'solid', group, 'rotor')
    m.face([V(lipx - depth - 0.04, *p) for p in ngon(0.165, seg, rot=rot)], 'rotor', outward=V(1, 0, 0), dens=120)
    m.cylinder(V(lipx - depth - 0.06, 0, 0), V(1, 0, 0), 0.165, 0.02, seg, 'rotor', caps=(False, False), rot=rot)
    m.element('wheel_back', 'solid', group, 'under')
    m.face([V(-h + 0.03, *p) for p in ngon(Rr, seg, rot=rot)], 'under', outward=V(1, 0, 0), dens=8)
    m.cylinder(V(-h + 0.03, 0, 0), V(1, 0, 0), Rr - 0.002, lipx - depth - 0.06 + h - 0.03, seg, 'under', caps=(False, False), rot=rot)
    if add_caliper:
        m.painters['caliper'] = S.flat(caliper_color, 4)
        m.element('caliper', 'solid', 'caliper', 'caliper')
        a = math.radians(125)
        c = V(lipx - depth - 0.02, 0.14 * math.sin(a), 0.14 * math.cos(a))
        M = np.array([[1, 0, 0], [0, math.cos(a - math.pi / 2), -math.sin(a - math.pi / 2)], [0, math.sin(a - math.pi / 2), math.cos(a - math.pi / 2)]])
        m.box(c, (0.035, 0.06, 0.12), 'caliper', M=M)


# ------------------------------------------------------------------------------------------------ spoilers
def spoiler(m, group, kind, deck, z_te, half_w, brake=True, style='paint'):
    """deck(x, z) -> surface height. z_te = trailing-edge position (rear end of the deck), half_w = mounting half width."""
    xm = half_w - 0.04
    if kind == 'none':
        return
    if kind == 'ducktail':
        m.element('ducktail', 'paint', group, style)
        y0 = deck(0.0, z_te + 0.06)
        prof = [(z_te + 0.2, y0 - 0.005), (z_te + 0.002, y0 - 0.005), (z_te - 0.008, y0 + 0.055), (z_te + 0.03, y0 + 0.05)]
        prism_x(m, prof, -half_w + 0.03, half_w - 0.03, style)
        return
    if kind == 'hoop':
        # factory-style: tall posts at the outer edges, blade swept up between them
        yb = max(deck(xm, z_te + 0.12), deck(-xm, z_te + 0.12))
        yw = yb + 0.135
        m.element('wing_blade', 'paint', group, style)
        foil = [(z_te + 0.27, yw + 0.012), (z_te + 0.24, yw + 0.026), (z_te + 0.12, yw + 0.03), (z_te + 0.04, yw + 0.02), (z_te + 0.005, yw + 0.012),
                (z_te + 0.01, yw - 0.002), (z_te + 0.14, yw - 0.004), (z_te + 0.25, yw + 0.002)]
        prism_x(m, foil, -xm - 0.03, xm + 0.03, style)
        m.element('wing_posts', 'paint', group, style)
        for sx in (-1, 1):
            x = sx * xm
            yf, yr = deck(x, z_te + 0.3), deck(x, z_te + 0.05)
            post = [(z_te + 0.34, yf - 0.01), (z_te + 0.05, yr - 0.01), (z_te + 0.0, yw + 0.016), (z_te + 0.27, yw + 0.022), (z_te + 0.3, yw - 0.02)]
            prism_x(m, post, x - 0.04, x + 0.04, style)
        if brake:
            m.element('third_brake', 'brake', group, 'brake_red')
            m.box(V(0, yw + 0.012, z_te + 0.002), (0.42, 0.012, 0.006), 'brake_red')
        return
    if kind == 'low_wing':
        yb = deck(0, z_te + 0.12)
        yw = yb + 0.1
        m.element('wing_blade', 'solid', group, 'carbon')
        foil = [(z_te + 0.24, yw), (z_te + 0.2, yw + 0.016), (z_te + 0.06, yw + 0.02), (z_te + 0.0, yw + 0.008), (z_te + 0.02, yw - 0.006), (z_te + 0.2, yw - 0.006)]
        prism_x(m, foil, -xm, xm, 'carbon')
        m.element('wing_posts', 'solid', group, 'trim')
        for sx in (-1, 1):
            x = sx * (xm - 0.25)
            y0 = deck(x, z_te + 0.12)
            prism_x(m, [(z_te + 0.18, y0 - 0.01), (z_te + 0.08, y0 - 0.01), (z_te + 0.08, yw), (z_te + 0.16, yw)], x - 0.012, x + 0.012, 'trim')
        for sx in (-1, 1):
            m.prism([(yw - 0.03, z_te - 0.01), (yw - 0.02, z_te + 0.26), (yw + 0.04, z_te + 0.26), (yw + 0.04, z_te - 0.01)], sx * xm, sx * (xm + 0.008), 'x', 'carbon')
        return
    if kind in ('gt_wing', 'ta_wing'):
        big = kind == 'ta_wing'
        h = 0.38 if big else 0.3
        chord = 0.34 if big else 0.27
        zf = z_te - (0.08 if big else 0.04)
        yb = deck(0, z_te + 0.15)
        yw = yb + h
        span = half_w + (0.04 if big else 0.0)
        m.element('wing_blade', 'solid', group, 'carbon')
        foil = [(zf + chord, yw - 0.01), (zf + chord - 0.04, yw + 0.014), (zf + chord * 0.4, yw + 0.022), (zf + 0.02, yw + 0.012), (zf, yw - 0.004),
                (zf + 0.03, yw - 0.016), (zf + chord * 0.5, yw - 0.02), (zf + chord - 0.02, yw - 0.016)]
        prism_x(m, foil, -span, span, 'carbon')
        if big:
            flap = [(zf + 0.06, yw + 0.03), (zf + 0.0, yw + 0.06), (zf - 0.01, yw + 0.05), (zf + 0.05, yw + 0.02)]
            prism_x(m, flap, -span, span, 'carbon')
        m.element('endplates', 'solid', group, 'carbon')
        for sx in (-1, 1):
            ep = [(zf - 0.03, yw - 0.07), (zf + chord + 0.02, yw - 0.05), (zf + chord + 0.02, yw + 0.04), (zf - 0.03, yw + (0.1 if big else 0.06))]
            prism_x(m, ep, sx * span, sx * (span + 0.008), 'carbon')
        m.element('wing_posts', 'solid', group, 'trim_gloss')
        for sx in (-1, 1):
            x = sx * 0.36
            y0 = deck(x, z_te + 0.16)
            if kind == 'gt_wing':
                # swan neck: rises behind and hooks over the top of the blade
                neck = [(z_te + 0.22, y0 - 0.01), (z_te + 0.12, y0 - 0.01), (zf + 0.12, yw + 0.02), (zf + 0.1, yw + 0.05), (zf + 0.2, yw + 0.05),
                        (zf + 0.2, yw + 0.022), (z_te + 0.2, y0 + 0.06)]
            else:
                neck = [(z_te + 0.24, y0 - 0.01), (z_te + 0.1, y0 - 0.01), (zf + 0.1, yw - 0.02), (zf + 0.2, yw - 0.02)]
            prism_x(m, neck, x - 0.01, x + 0.01, 'trim_gloss')
        if brake:
            m.element('third_brake', 'brake', group, 'brake_red')
            m.box(V(0, yw + 0.002, zf - 0.001), (0.3, 0.01, 0.004), 'brake_red')
        return
    raise ValueError(kind)


# ------------------------------------------------------------------------------------------------ exhausts
def exhaust(m, group, kind, z_exit, y, x_main=-0.5, length=0.3):
    """Exhaust tips exiting backwards (towards -Z) at z_exit. x_main = main tip lateral position (negative = right side)."""
    m.painters.setdefault('ti', ti_painter)
    m.element('exhaust', 'solid', group, 'chrome')
    z0 = z_exit + length
    if kind == 'oval':
        tube(m, ngon(0.055, 14, x_main, y, ry=0.038), z0, z_exit, 'chrome', 'chrome', 'under')
    elif kind == 'dual':
        for sx in (1, -1):
            tube(m, ngon(0.042, 12, sx * abs(x_main), y), z0, z_exit, 'chrome', 'chrome', 'under')
    elif kind == 'big':
        tube(m, ngon(0.068, 16, x_main, y + 0.005), z0, z_exit - 0.03, 'ti', 'ti', 'under')
    elif kind == 'quad':
        for sx in (1, -1):
            for dx in (-0.065, 0.065):
                tube(m, ngon(0.036, 12, sx * abs(x_main) + dx, y), z0, z_exit, 'chrome', 'chrome', 'under')
    elif kind == 'center':
        for dx in (-0.075, 0.075):
            tube(m, ngon(0.045, 12, dx, y + 0.01), z0, z_exit - 0.01, 'chrome', 'chrome', 'under')
    else:
        raise ValueError(kind)


def exhaust_points(kind, z_exit, y, x_main=-0.5):
    """Tip positions for flames/smoke: [x, y, z, dx, dy, dz] in the authoring frame."""
    if kind == 'oval':
        xs = [x_main]
    elif kind == 'dual':
        xs = [abs(x_main), -abs(x_main)]
    elif kind == 'big':
        xs = [x_main]
    elif kind == 'quad':
        xs = [abs(x_main) - 0.065, abs(x_main) + 0.065, -abs(x_main) - 0.065, -abs(x_main) + 0.065]
    else:
        xs = [-0.075, 0.075]
    return [[x, y, z_exit, 0, 0, -1] for x in xs]


def ti_painter(face, P, n):
    out = fill(P, (176, 170, 160))
    t = np.mod(P[..., 2] * 14, 1.0)
    out[..., 0] += np.where(t < 0.3, -30, 10)
    out[..., 2] += np.where(t < 0.3, 50, 0)
    return out
