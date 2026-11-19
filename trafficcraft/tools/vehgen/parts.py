"""Wheels, interior components and accessories."""
import math
import numpy as np
from geom import *

CHROME = rgb('#d2d6dc')
DARKCHROME = rgb('#5b6066')
BLACK = rgb('#141516')
RUBBER = rgb('#202123')
GREY = rgb('#3a3d42')


# ---------------------------------------------------------------- wheels
def _hexa(mesh, part, mat, pts, col):
    """8-point hexahedron: pts[0..3] back face ring, pts[4..7] front face ring (same order)."""
    b, f = pts[:4], pts[4:]
    mesh.quad(part, mat, f[0], f[1], f[2], f[3], col)
    mesh.quad(part, mat, b[3], b[2], b[1], b[0], col)
    for k in range(4):
        k2 = (k + 1) % 4
        mesh.quad(part, mat, b[k], b[k2], f[k2], f[k], col)


def spoke(mesh, part, ang, r0, r1, w0, w1, x_hub, x_lip, thick, col, twist=0.0):
    """Tapered, dished spoke in the wheel plane (axis X). ang = direction angle."""
    def P(r, s, x, a_extra=0.0):
        a = ang + a_extra
        d = v3(0, math.cos(a), math.sin(a))
        t = v3(0, -math.sin(a), math.cos(a))
        return v3(x, 0, 0) + d * r + t * s
    pts = [P(r0, -w0 / 2, x_hub - thick), P(r1, -w1 / 2, x_lip - thick, twist), P(r1, w1 / 2, x_lip - thick, twist), P(r0, w0 / 2, x_hub - thick),
           P(r0, -w0 / 2, x_hub), P(r1, -w1 / 2, x_lip, twist), P(r1, w1 / 2, x_lip, twist), P(r0, w0 / 2, x_hub)]
    # reorder to back ring / front ring (0..3) consistent
    _hexa(mesh, part, SOLID, [pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], pts[6], pts[7]], col)


def wheel(mesh, R, TW, style='spoke5', rim_ratio=0.64, rimcol=None, whitewall=False, seg=24, caliper=None, lod=0):
    """Wheel centred at origin, axis X, outer face toward +X. Parts: 'wheel' (spins) and 'caliper' (static)."""
    P = 'wheel'
    seg = 14 if lod else seg
    Rr = R * rim_ratio
    h = TW / 2
    rimcol = rgb(rimcol) if isinstance(rimcol, str) else (rimcol or rgb('#c4c8ce'))
    ring = [(math.cos(2 * math.pi * k / seg), math.sin(2 * math.pi * k / seg)) for k in range(seg + 1)]
    sw = R - Rr

    def rev(profile, col, uvr=None, mode=None, part=P, mat=SOLID):
        n = len(profile)
        for k in range(seg):
            ca, sa = ring[k]
            cb, sb = ring[k + 1]
            for j in range(n - 1):
                r0, x0 = profile[j]
                r1, x1 = profile[j + 1]
                p00 = v3(x0, r0 * ca, r0 * sa)
                p01 = v3(x1, r1 * ca, r1 * sa)
                p10 = v3(x0, r0 * cb, r0 * sb)
                p11 = v3(x1, r1 * cb, r1 * sb)
                uvs = None
                if uvr is not None:
                    u0, v0, u1, v1 = uvr
                    if mode == 'tread':
                        f0, f1 = j / (n - 1), (j + 1) / (n - 1)
                        uvs = ((u0, v0 + (v1 - v0) * f0), (u1, v0 + (v1 - v0) * f0), (u1, v0 + (v1 - v0) * f1), (u0, v0 + (v1 - v0) * f1))
                    elif mode == 'around':
                        g0, g1 = (k / seg * 2) % 1.0, ((k + 1) / seg * 2) % 1.0 or 1.0
                        if g1 < g0:
                            g1 = 1.0
                        f0, f1 = j / (n - 1), (j + 1) / (n - 1)
                        uvs = ((u0 + (u1 - u0) * g0, v0 + (v1 - v0) * f0), (u0 + (u1 - u0) * g1, v0 + (v1 - v0) * f0),
                               (u0 + (u1 - u0) * g1, v0 + (v1 - v0) * f1), (u0 + (u1 - u0) * g0, v0 + (v1 - v0) * f1))
                mesh.quad(part, mat, p00, p10, p11, p01, col, uvr=uvr if uvs is None else None, uvs=uvs)

    # ---- tyre
    inner_side = [(Rr * 1.0, -h * 0.84), (Rr + sw * 0.4, -h * 0.97), (Rr + sw * 0.78, -h * 1.0), (R - 0.022, -h * 0.93), (R - 0.006, -h * 0.8)]
    tread = [(R - 0.006, -h * 0.8), (R, -h * 0.6), (R, 0.0), (R, h * 0.6), (R - 0.006, h * 0.8)]
    outer_side = [(R - 0.006, h * 0.8), (R - 0.022, h * 0.93), (Rr + sw * 0.78, h * 1.0)]
    letter = [(Rr + sw * 0.78, h * 1.0), (Rr + sw * 0.42, h * 0.98)]
    bead = [(Rr + sw * 0.42, h * 0.98), (Rr * 1.02, h * 0.9), (Rr * 1.0, h * 0.86)]
    rev(inner_side, RUBBER)
    rev(tread, rgb('#ffffff'), uvr=UV['tread'], mode='tread')
    rev(outer_side, RUBBER)
    if whitewall:
        rev(letter, rgb('#ecebe4'))
    else:
        rev(letter, rgb('#ffffff'), uvr=UV['sidewall'], mode='around')
    rev(bead, RUBBER)
    if lod:
        rev([(Rr, h * 0.86), (Rr * 0.3, h * 0.6), (0.001, h * 0.62)], rimcol)
        return mesh
    # ---- rim barrel + polished lip
    rev([(Rr, h * 0.86), (Rr * 0.97, h * 0.55), (Rr * 0.97, -h * 0.8), (Rr * 0.55, -h * 0.7)], rgb('#26282b'))
    lipc = rgb('#e6e9ee') if style not in ('steel', 'truck') else rimcol
    rev([(Rr * 1.005, h * 0.86), (Rr * 1.03, h * 0.83), (Rr * 1.02, h * 0.78), (Rr * 0.94, h * 0.76)], lipc)
    face = h * 0.7
    nuts = 5
    if style in ('spoke5', 'twin5', 'mesh10', 'y6', 'turbine', 'offroad', 'sport'):
        # brake rotor + hat (spins with wheel)
        rot_x0 = face - 0.12
        u0, v0, u1, v1 = UV['rotor']
        disc(mesh, P, SOLID, (rot_x0, 0, 0), (1, 0, 0), Rr * 0.84, 20, rgb('#ffffff'), uvr=UV['rotor'])
        cylinder(mesh, P, SOLID, (rot_x0, 0, 0), (face - 0.04, 0, 0), Rr * 0.33, 12, rgb('#4a4c50'), caps=False)
        dish = {'spoke5': 0.035, 'twin5': 0.04, 'mesh10': 0.025, 'y6': 0.03, 'turbine': 0.02, 'offroad': 0.015, 'sport': 0.045}[style]
        if style == 'spoke5':
            for k in range(5):
                spoke(mesh, P, 2 * math.pi * k / 5, Rr * 0.24, Rr * 0.95, 0.07, 0.085, face + 0.005 - dish, face - 0.005, 0.03, rimcol)
        elif style == 'twin5' or style == 'sport':
            for k in range(5):
                a = 2 * math.pi * k / 5
                for s in (-1, 1):
                    spoke(mesh, P, a + s * 0.11, Rr * 0.25, Rr * 0.95, 0.035, 0.04, face - dish + 0.005, face - 0.005, 0.026, rimcol,
                          twist=s * 0.05)
        elif style == 'mesh10':
            for k in range(10):
                a = 2 * math.pi * k / 10
                spoke(mesh, P, a, Rr * 0.24, Rr * 0.95, 0.03, 0.03, face - dish + 0.005, face - 0.005, 0.02, rimcol, twist=0.12)
                spoke(mesh, P, a, Rr * 0.24, Rr * 0.95, 0.03, 0.03, face - dish + 0.002, face - 0.008, 0.02, rimcol, twist=-0.12)
        elif style == 'y6':
            for k in range(6):
                a = 2 * math.pi * k / 6
                spoke(mesh, P, a, Rr * 0.24, Rr * 0.62, 0.06, 0.05, face - dish + 0.005, face - 0.012, 0.028, rimcol)
                for s in (-1, 1):
                    spoke(mesh, P, a + s * 0.16, Rr * 0.6, Rr * 0.95, 0.035, 0.04, face - 0.012, face - 0.005, 0.026, rimcol)
        elif style == 'turbine':
            for k in range(7):
                spoke(mesh, P, 2 * math.pi * k / 7, Rr * 0.24, Rr * 0.95, 0.06, 0.12, face - dish, face - 0.005, 0.02, rimcol, twist=0.35)
        elif style == 'offroad':
            for k in range(6):
                spoke(mesh, P, 2 * math.pi * k / 6, Rr * 0.26, Rr * 0.93, 0.1, 0.11, face - dish, face - 0.01, 0.035, rimcol)
            for k in range(16):
                a = 2 * math.pi * k / 16
                box(mesh, P, SOLID, (h * 0.86, math.cos(a) * Rr * 0.96, math.sin(a) * Rr * 0.96), (0.018, 0.022, 0.022), rgb('#d8d8d8'))
            nuts = 6
        # hub + centre cap
        cylinder(mesh, P, SOLID, (face - dish - 0.01, 0, 0), (face - dish + 0.018, 0, 0), Rr * 0.28, 14, rimcol)
        disc(mesh, P, SOLID, (face - dish + 0.019, 0, 0), (1, 0, 0), Rr * 0.12, 12, rgb('#ffffff'), uvr=UV['logo'])
        for k in range(nuts):
            a = 2 * math.pi * k / nuts
            c = v3(face - dish + 0.018, math.cos(a) * Rr * 0.19, math.sin(a) * Rr * 0.19)
            cylinder(mesh, P, SOLID, c - v3(0.01, 0, 0), c + v3(0.012, 0, 0), 0.011, 6, rgb('#d9dce0'))
    elif style in ('steel', 'truck'):
        deep = style == 'truck'
        prof = [(Rr * 0.94, h * 0.62), (Rr * 0.8, h * 0.5), (Rr * 0.55, h * 0.5), (Rr * 0.42, h * (0.78 if deep else 0.62)),
                (Rr * 0.3, h * (0.8 if deep else 0.66))]
        rev(prof, rimcol)
        disc(mesh, P, SOLID, (h * (0.8 if deep else 0.66), 0, 0), (1, 0, 0), Rr * 0.3, 12, rimcol)
        nh = 10 if deep else 6
        for k in range(nh):
            a = 2 * math.pi * k / nh + 0.3
            p = v3(h * 0.5 + 0.004, math.cos(a) * Rr * 0.68, math.sin(a) * Rr * 0.68)
            disc(mesh, P, SOLID, p, (1, 0, 0), Rr * 0.075, 8, rgb('#121314'))
        nn = 10 if deep else 5
        for k in range(nn):
            a = 2 * math.pi * k / nn
            c = v3(h * (0.8 if deep else 0.66), math.cos(a) * Rr * 0.22, math.sin(a) * Rr * 0.22)
            cylinder(mesh, P, SOLID, c, c + v3(0.02, 0, 0), 0.012, 6, rgb('#cfd2d6'))
        if deep:
            cylinder(mesh, P, SOLID, (h * 0.8, 0, 0), (h * 1.0, 0, 0), Rr * 0.13, 12, rgb('#c8cbcf'))
    elif style == 'hubcap':
        rev([(Rr * 0.94, h * 0.6), (Rr * 0.82, h * 0.74), (Rr * 0.6, h * 0.8), (Rr * 0.45, h * 0.86), (Rr * 0.2, h * 0.96),
             (0.001, h * 1.0)], CHROME)
        for k in range(16):
            a = 2 * math.pi * k / 16
            bar(mesh, P, SOLID, v3(h * 0.79, math.cos(a) * Rr * 0.52, math.sin(a) * Rr * 0.52),
                v3(h * 0.65, math.cos(a) * Rr * 0.86, math.sin(a) * Rr * 0.86), 0.018, 0.01, rgb('#8d9298'), up=(1, 0, 0))
        disc(mesh, P, SOLID, (h * 1.002, 0, 0), (1, 0, 0), Rr * 0.14, 12, rgb('#ffffff'), uvr=UV['logo'])
    elif style == 'aero':
        rev([(Rr * 0.95, h * 0.62), (Rr * 0.7, h * 0.72), (Rr * 0.3, h * 0.78), (0.001, h * 0.8)], rgb('#d0d4da'))
        for k in range(5):
            a = 2 * math.pi * k / 5
            spoke(mesh, P, a, Rr * 0.32, Rr * 0.9, 0.05, 0.16, h * 0.79, h * 0.66, 0.004, rgb('#1d1f22'), twist=0.45)
        disc(mesh, P, SOLID, (h * 0.81, 0, 0), (1, 0, 0), Rr * 0.12, 12, rgb('#ffffff'), uvr=UV['logo'])
    # valve stem
    box(mesh, P, SOLID, (h * 0.7, Rr * 0.9, 0.0), (0.03, 0.012, 0.012), rgb('#222222'))
    # ---- caliper (static part: steers but does not spin)
    if caliper and style not in ('hubcap', 'aero', 'steel', 'truck'):
        cc = rgb(caliper)
        for k in range(5):
            a0 = math.radians(100 + k * 14)
            a1 = math.radians(100 + (k + 1) * 14)
            for (rr0, rr1) in ((Rr * 0.6, Rr * 0.86),):
                pts = []
                for x in (face - 0.17, face - 0.06):
                    pts += [v3(x, math.cos(a0) * rr0, math.sin(a0) * rr0), v3(x, math.cos(a1) * rr0, math.sin(a1) * rr0),
                            v3(x, math.cos(a1) * rr1, math.sin(a1) * rr1), v3(x, math.cos(a0) * rr1, math.sin(a0) * rr1)]
                _hexa(mesh, 'caliper', SOLID, pts, cc)
    return mesh


# ---------------------------------------------------------------- seats
def seat(mesh, part, x, y, z, w, col, kind='bucket', back_angle=0.26, headrest=True, scale=1.0, accent=None, uv='fabric', lod=0):
    """y = cushion top surface, seat faces +Z."""
    s = scale
    accent = accent or col
    uvr = UV[uv]
    if lod:
        rbox(mesh, part, SOLID, (x, y - 0.06, z), (w, 0.12, 0.5 * s), 0.04, col)
        rbox(mesh, part, SOLID, (x, y + 0.32 * s, z - 0.3 * s), (w * 0.95, 0.65 * s, 0.13), 0.04, col, rot=rot_x(back_angle))
        return
    # cushion: base + front roll + bolsters
    rbox(mesh, part, SOLID, (x, y - 0.065, z - 0.02), (w * 0.82, 0.11, 0.46 * s), 0.045, col, uvr=uvr)
    rbox(mesh, part, SOLID, (x, y - 0.06, z + 0.2 * s), (w * 0.86, 0.1, 0.1), 0.045, accent, uvr=uvr)
    if kind == 'bucket':
        for sx in (-1, 1):
            rbox(mesh, part, SOLID, (x + sx * w * 0.44, y - 0.03, z - 0.02), (w * 0.13, 0.13, 0.46 * s), 0.05, accent, uvr=uvr)
    # backrest (lumbar + shoulder blocks, slight forward curve)
    rot = rot_x(back_angle)
    base = v3(x, y - 0.02, z - 0.25 * s)
    lum = base + rot @ v3(0, 0.18 * s, -0.06)
    sho = base + rot @ v3(0, 0.47 * s, -0.075)
    rbox(mesh, part, SOLID, lum, (w * 0.8, 0.32 * s, 0.13), 0.05, col, rot=rot, uvr=uvr)
    rbox(mesh, part, SOLID, sho, (w * 0.74, 0.28 * s, 0.11), 0.05, col, rot=rot, uvr=uvr)
    if kind == 'bucket':
        for sx in (-1, 1):
            r2 = rot @ rot_y(-sx * 0.28)
            rbox(mesh, part, SOLID, base + rot @ v3(sx * w * 0.42, 0.3 * s, -0.04), (w * 0.14, 0.56 * s, 0.17), 0.055, accent, rot=r2, uvr=uvr)
    if headrest:
        top = base + rot @ v3(0, 0.62 * s, -0.08)
        hr = top + v3(0, 0.13 * s, -0.025)
        rbox(mesh, part, SOLID, hr, (min(0.27, w * 0.58), 0.16 * s, 0.1), 0.05, col, uvr=uvr)
        for px in (-0.065, 0.065):
            cylinder(mesh, part, SOLID, top + v3(x * 0 + px, -0.01, 0), hr + v3(px, -0.06, 0), 0.007, 5, CHROME, caps=False)
    # rails + base
    box(mesh, part, SOLID, (x, y - 0.165, z - 0.02), (w * 0.62, 0.09, 0.36), BLACK)
    for sx in (-1, 1):
        box(mesh, part, SOLID, (x + sx * w * 0.3, y - 0.22, z), (0.03, 0.025, 0.62), rgb('#2c2e31'))


def bench(mesh, part, x0, x1, y, z, col, heads=2, back_angle=0.3, uv='fabric', armrest=True, lod=0):
    w = abs(x1 - x0)
    cx = (x0 + x1) / 2
    uvr = UV[uv]
    rbox(mesh, part, SOLID, (cx, y - 0.065, z), (w, 0.13, 0.5), 0.055, col, uvr=uvr)
    rot = rot_x(back_angle)
    bc = v3(cx, y + 0.29, z - 0.3)
    rbox(mesh, part, SOLID, bc, (w, 0.62, 0.14), 0.055, col, rot=rot, uvr=uvr)
    if lod:
        return
    # seat contours: two/three cushions separated by seams
    for k in range(1, heads + (1 if heads == 2 else 0)):
        sxp = x0 + k * w / (heads + (1 if heads == 2 else 0))
        box(mesh, part, SOLID, (sxp, y + 0.002, z), (0.008, 0.004, 0.44), rgb('#141414'))
    top = bc + rot @ v3(0, 0.31, 0)
    for k in range(heads):
        hx = x0 + (k + 0.5) * w / heads
        rbox(mesh, part, SOLID, v3(hx, top[1] + 0.1, top[2] - 0.02), (0.25, 0.15, 0.1), 0.05, col, uvr=uvr)
    if armrest:
        rbox(mesh, part, SOLID, bc + rot @ v3(0, -0.05, 0.08), (0.2, 0.26, 0.05), 0.02, rgb('#202124'))


# ---------------------------------------------------------------- steering wheel
def steering_wheel(mesh, center, axis, R=0.18, col=None, spokes=3, paddles=False, lod=0):
    """Rim/spokes/hub go to part 'steer' (rotates); column shroud and stalks to 'interior'."""
    col = col or rgb('#1a1b1d')
    c = np.array(center, float)
    ax = normalize(np.array(axis, float))  # points from the hub toward the dashboard
    torus(mesh, 'steer', SOLID, c, ax, R, 0.026, 14 if lod else 26, 5 if lod else 8, col)
    up = normalize(np.cross(ax, v3(1, 0, 0)))
    if up[1] < 0:
        up = -up
    rt = normalize(np.cross(up, ax))
    angles = {3: [0, 180, 270], 4: [30, 150, 210, 330], 2: [0, 180]}[spokes]
    for d in angles:
        r = math.radians(d)
        dirv = math.cos(r) * rt + math.sin(r) * up
        bar(mesh, 'steer', SOLID, c + dirv * 0.05 + ax * 0.012, c + dirv * (R - 0.012), 0.055 if d != 270 else 0.04, 0.016, col, up=ax)
        if d in (0, 180) and not lod:
            # spoke button pads
            p = c + dirv * (R * 0.55) - ax * 0.01
            mesh.quad('steer', SOLID, p - rt * 0.025 - up * 0.018, p + rt * 0.025 - up * 0.018, p + rt * 0.025 + up * 0.018,
                      p - rt * 0.025 + up * 0.018, rgb('#ffffff'), uvr=UV['buttons'], smooth=False)
    rbox(mesh, 'steer', SOLID, c + ax * 0.012, (0.15, 0.12, 0.06), 0.03, rgb('#202124'),
         rot=np.column_stack([rt, up, ax]))
    disc(mesh, 'steer', SOLID, c - ax * 0.02, -ax, 0.025, 10, rgb('#ffffff'), uvr=UV['logo'])
    if paddles:
        for s in (-1, 1):
            box(mesh, 'steer', SOLID, c + rt * s * (R * 0.75) + ax * 0.05 + up * 0.03, (0.07, 0.09, 0.008), rgb('#8a8e94'),
                rot=np.column_stack([rt, up, ax]))
    # column shroud + stalks (static)
    rbox(mesh, 'interior', SOLID, c + ax * 0.16 - up * 0.03, (0.13, 0.11, 0.24), 0.04, rgb('#1b1c1e'), rot=np.column_stack([rt, up, ax]))
    for s in (-1, 1):
        bar(mesh, 'interior', SOLID, c + ax * 0.09 + rt * s * 0.05, c + ax * 0.08 + rt * s * 0.19 + up * 0.02, 0.018, 0.018, rgb('#202124'))
    cylinder(mesh, 'interior', SOLID, c + ax * 0.26, c + ax * 0.5, 0.03, 8, rgb('#151617'), caps=False)


# ---------------------------------------------------------------- mirrors
def mirror_side(mesh, part, base, outward, col_body, mat_body=PAINT, scale=1.0, truck=False, lod=0):
    b = np.array(base, float)
    s = scale
    if truck:
        arm_end = b + v3(outward * 0.3, 0.05, -0.05)
        bar(mesh, part, SOLID, b, arm_end, 0.03, 0.03, BLACK)
        bar(mesh, part, SOLID, b + v3(0, -0.25, 0), arm_end + v3(0, -0.3, 0), 0.025, 0.025, BLACK)
        rbox(mesh, part, SOLID, arm_end + v3(outward * 0.04, -0.12, 0), (0.08, 0.44, 0.15), 0.035, BLACK)
        mesh.quad(part, SOLID, *[arm_end + v3(outward * 0.04 + dx, -0.12 + dy, -0.077) for dx, dy in
                                ((0.03 * outward, -0.2), (-0.03 * outward, -0.2), (-0.03 * outward, 0.2), (0.03 * outward, 0.2))],
                  rgb('#ffffff'), uvr=UV['glass'], smooth=False)
        return
    # sail (triangular base on the door)
    rbox(mesh, part, SOLID, b + v3(outward * 0.02, 0.0, 0.0), (0.05, 0.1 * s, 0.16 * s), 0.02, BLACK)
    hub = b + v3(outward * 0.11 * s, 0.05 * s, -0.03 * s)
    bar(mesh, part, SOLID, b + v3(outward * 0.03, 0.02, 0), hub - v3(outward * 0.05, 0, 0), 0.05 * s, 0.03 * s, BLACK)
    # tapered housing: two stacked rounded boxes
    rbox(mesh, part, mat_body, hub + v3(outward * 0.03 * s, 0.0, 0.01), (0.2 * s, 0.12 * s, 0.1 * s), 0.045 * s, col_body)
    rbox(mesh, part, mat_body, hub + v3(outward * 0.06 * s, 0.005, 0.0), (0.16 * s, 0.13 * s, 0.11 * s), 0.05 * s, col_body)
    # glass faces backward (-Z)
    gz = hub[2] - 0.051 * s
    gx0, gx1 = hub[0] - outward * 0.05 * s, hub[0] + outward * 0.13 * s
    gy0, gy1 = hub[1] - 0.045 * s, hub[1] + 0.05 * s
    if outward > 0:
        q = (v3(gx1, gy0, gz), v3(gx0, gy0, gz), v3(gx0, gy1, gz), v3(gx1, gy1, gz))
    else:
        q = (v3(gx0, gy0, gz), v3(gx1, gy0, gz), v3(gx1, gy1, gz), v3(gx0, gy1, gz))
    mesh.quad(part, SOLID, *q, rgb('#ffffff'), uvr=UV['glass'], smooth=False)
    # repeater LED strip along the lower front edge
    box(mesh, part, IND_L if outward > 0 else IND_R, hub + v3(outward * 0.09 * s, -0.05 * s, 0.045 * s), (0.1 * s, 0.01, 0.02),
        rgb('#ffa424'))


# ---------------------------------------------------------------- roof/body accessories
def lightbar(mesh, part, center, width, kind='police'):
    c = np.array(center, float)
    rbox(mesh, part, SOLID, c + v3(0, 0.03, 0), (width, 0.06, 0.3), 0.03, rgb('#202226'))
    n = 8
    seg_w = width / n
    for k in range(n):
        x = -width / 2 + seg_w * (k + 0.5)
        if kind == 'police':
            mat = BEACON_A if x > 0 else BEACON_B
            col = rgb('#ff2a2a') if x > 0 else rgb('#2a5bff')
        else:
            mat = BEACON_A if k % 2 == 0 else BEACON_B
            col = rgb('#ff2a2a') if k % 2 == 0 else rgb('#f4f4f4')
        rbox(mesh, part, mat, c + v3(x, 0.095, 0), (seg_w * 0.9, 0.075, 0.26), 0.028, col)
    rbox(mesh, part, GLASS, c + v3(0, 0.1, 0), (width + 0.01, 0.09, 0.28), 0.035, rgb('#dde6f0'))
    for z in (0.1, -0.1):
        for xx in (-width * 0.4, width * 0.4):
            cylinder(mesh, part, SOLID, c + v3(xx, 0, z), c + v3(xx, -0.05, z), 0.012, 5, BLACK)


def taxi_sign(mesh, part, center):
    c = np.array(center, float)
    for xx in (-0.15, 0.15):
        bar(mesh, part, SOLID, c + v3(xx, -0.01, 0), c + v3(xx, 0.05, 0), 0.035, 0.035, BLACK)
    rbox(mesh, part, TAXI_SIGN, c + v3(0, 0.13, 0), (0.6, 0.17, 0.22), 0.035, rgb('#ffffff'))
    for s in (1, -1):
        z = 0.112 * s
        p = [c + v3(0.26 * s, 0.06, z), c + v3(-0.26 * s, 0.06, z), c + v3(-0.26 * s, 0.2, z), c + v3(0.26 * s, 0.2, z)]
        mesh.quad(part, TAXI_SIGN, p[0], p[1], p[2], p[3], rgb('#ffffff'), uvr=UV['taxi'], smooth=False)


def roof_rack(mesh, part, z0, z1, y, hw, rails_only=False):
    for sx in (-1, 1):
        bar(mesh, part, SOLID, v3(sx * hw, y + 0.06, z0), v3(sx * hw, y + 0.06, z1), 0.035, 0.03, rgb('#2a2c2f'))
        for z in (z0 + 0.05, z1 - 0.05):
            bar(mesh, part, SOLID, v3(sx * hw, y, z), v3(sx * hw, y + 0.065, z), 0.035, 0.05, rgb('#2a2c2f'))
    if not rails_only:
        for f in (0.3, 0.7):
            z = z0 + (z1 - z0) * f
            bar(mesh, part, SOLID, v3(-hw, y + 0.08, z), v3(hw, y + 0.08, z), 0.03, 0.025, rgb('#1d1e20'))


def spare_wheel(mesh, part, center, R, TW):
    c = np.array(center, float)
    w = Mesh()
    wheel(w, R, TW, 'offroad', rimcol='#c0c4c8', seg=18)
    for (pt, mat), tris in w.groups.items():
        for t in tris:
            xf = lambda p: np.array([p[2], p[1], -p[0]]) + c
            mesh.groups.setdefault((part, mat), []).append((xf(t[0]), xf(t[1]), xf(t[2])) + t[3:])
    rbox(mesh, part, SOLID, c + v3(0, 0, TW * 0.15), (R * 2.04, R * 2.04, TW * 0.5), R * 0.9, rgb('#202124'))


def wing(mesh, part, z, y, hw, chord=0.28, col=None, mat=PAINT, stand_h=0.14, stands_x=0.45):
    col = col or (255, 255, 255, 255)
    rot = rot_x(-0.12)
    rbox(mesh, part, mat, (0, y + stand_h + 0.02, z), (hw * 2, 0.035, chord), 0.015, col, rot=rot)
    for sx in (-1, 1):
        rbox(mesh, part, SOLID, (sx * hw, y + stand_h + 0.02, z), (0.02, 0.12, chord * 1.1), 0.008, BLACK)
        bar(mesh, part, SOLID, v3(sx * stands_x, y - 0.02, z - 0.02), v3(sx * stands_x, y + stand_h, z), 0.04, 0.05, BLACK)


def cargo_box(mesh, part, z0, z1, y0, y1, hw, col=None, mat=PAINT, ribs=True, rear_doors=True, logo_uv=None):
    col = col or (255, 255, 255, 255)
    zc = (z0 + z1) / 2
    yc = (y0 + y1) / 2
    rbox(mesh, part, mat, (0, yc, zc), (hw * 2, y1 - y0, z1 - z0), 0.05, col, seg=1)
    if ribs:
        n = int((z1 - z0) / 0.6)
        for k in range(1, n):
            z = z0 + (z1 - z0) * k / n
            for sx in (-1, 1):
                box(mesh, part, SOLID, (sx * (hw + 0.008), yc, z), (0.012, y1 - y0 - 0.08, 0.035), rgb('#b9bcc0'))
        bar(mesh, part, SOLID, v3(0, y1 + 0.006, z0 + 0.05), v3(0, y1 + 0.006, z1 - 0.05), hw * 2 - 0.02, 0.01, rgb('#d6d8da'))
    for sx in (-1, 1):
        box(mesh, part, SOLID, (sx * (hw - 0.02), y0 + 0.03, zc), (0.06, 0.08, z1 - z0), rgb('#2a2c2f'))
    if rear_doors:
        box(mesh, part, SOLID, (0, yc, z0 - 0.006), (0.012, y1 - y0 - 0.1, 0.012), rgb('#3a3c40'))
        for sx in (-1, 1):
            for yy in (y0 + 0.3, yc, y1 - 0.3):
                box(mesh, part, SOLID, (sx * 0.12, yy, z0 - 0.015), (0.035, 0.05, 0.025), rgb('#a8acb0'))
            bar(mesh, part, SOLID, v3(sx * 0.06, y0 + 0.2, z0 - 0.025), v3(sx * 0.06, y1 - 0.2, z0 - 0.025), 0.025, 0.02, rgb('#8d9196'))
    if logo_uv:
        for sx in (-1, 1):
            x = sx * (hw + 0.012)
            za, zb = z0 + 0.3, z1 - 0.3
            ya, yb = y0 + 0.35, y1 - 0.25
            if sx > 0:
                mesh.quad(part, SOLID, v3(x, ya, zb), v3(x, ya, za), v3(x, yb, za), v3(x, yb, zb), rgb('#ffffff'), uvr=logo_uv, smooth=False)
            else:
                mesh.quad(part, SOLID, v3(x, ya, zb), v3(x, ya, za), v3(x, yb, za), v3(x, yb, zb), rgb('#ffffff'), uvr=logo_uv, smooth=False)
