"""Lamborghini Aventador (LP700-4, 2011). Narrow angular headlamps with a Y-shaped LED daytime signature, huge triangular black
intakes in the nose corners; Y-shaped LED tail lamps on each side of a black hexagon-mesh tail panel, hexagonal centre exhaust,
wedge profile with glass engine cover."""
from _h import *
L = 4.78


def tri(sx):
    def f(cx, cy, w, h):
        pts = [(cx - w / 2, cy + h / 2), (cx + w / 2, cy + h / 2), (cx + w * 0.3, cy - h / 2), (cx - w / 2, cy - h * 0.2)]
        return [(sx * x, y) for x, y in pts]
    return f


INTAKE = [(0.32, 0.2), (0.97, 0.2), (1.0, 0.36), (0.9, 0.44), (0.62, 0.34), (0.44, 0.3)]


def intakes(car, out, P):
    """Big black corner intakes, painted on every nose face they cover (front and wrapped corners)."""
    import numpy as np
    from bb import inside
    import styles as S
    x, y, z = P[..., 0], P[..., 1], P[..., 2]
    uv = np.stack([np.abs(x), y], -1)
    m = inside(uv, INTAKE) & (z > car.L - 0.55)
    hole = S.honeycomb(P, np.stack([x, y], -1), 0.02)
    out[m] = (24, 24, 26, 240)
    out[m & hole] = (6, 6, 8, 240)


def side_art(car, out, P, n):
    import numpy as np
    from bb import inside
    intakes(car, out, P)
    z, y = P[..., 2], P[..., 1]
    uv = np.stack([z, y], -1)
    # the big hexagonal side intake ahead of the rear wheel and the upper scoop behind the side window
    m = inside(uv, [(1.38, 0.44), (1.9, 0.5), (2.02, 0.68), (1.86, 0.8), (1.5, 0.84), (1.4, 0.7)])
    hole = np.mod(z * 40 + y * 20, 1.0) < 0.3
    out[m] = (16, 16, 18, 240)
    out[m & hole] = (40, 40, 44, 240)
    out[inside(uv, [(1.42, 0.86), (1.9, 0.86), (1.95, 0.92), (1.5, 0.95)])] = (16, 16, 18, 240)


def top_art(car, out, P, n):
    import numpy as np
    z, x = P[..., 2], P[..., 0]
    m = (z > 0.45) & (z < 1.38) & (np.abs(x) < 0.55)
    out[m] = (26, 26, 28, 240)
    out[m & (np.mod(z * 22, 1.0) < 0.4)] = (60, 62, 66, 240)


def front_art(car, out, P, n, uv, k):
    import numpy as np
    intakes(car, out, P)
    x, y = P[..., 0], P[..., 1]
    # black splitter blade and the sharp 'arrow' line in the nose
    out[y < 0.205] = (16, 16, 18, 240)
    out[(np.abs(y - (0.3 - np.abs(x) * 0.12)) < 0.006) & (np.abs(x) < 0.3)] = (30, 30, 32, 240)


SPEC = dict(
    id='lamborghini_aventador_2011', name='Lamborghini Aventador 2011', make='Lamborghini', model='Aventador LP700-4', year=2011, category='super',
    category_name='Supercar', description='V12 flagship with a carbon tub, scissor doors and a fighter-jet wedge shape.',
    L=L, W=2.03, H=1.136, YR=1.0, YF=3.7, R=0.345, TRACK=0.85, Rr=0.254, TW=0.3, tumble=0.1, arch_gap=0.03,
    bot=[(0, 0.3), (0.15, 0.24), (0.6, 0.19), (1.4, 0.17), (3.2, 0.17), (4.2, 0.18), (L, 0.2)],
    belt=[(0, 0.86), (0.06, 0.89), (0.5, 0.9), (1.2, 0.88), (1.9, 0.82), (2.6, 0.77), (3.2, 0.72), (3.62, 0.67), (4.0, 0.58), (4.4, 0.47), (4.65, 0.39), (L, 0.35)],
    hw=[(0, 0.9), (0.1, 0.97), (0.6, 1.01), (1.2, 1.0), (2.0, 0.96), (2.9, 0.97), (3.6, 1.0), (4.1, 0.97), (4.4, 0.9), (4.6, 0.8), (L, 0.62)],
    zsh=[(0, 0.56), (1.2, 0.52), (2.2, 0.48), (3.6, 0.44), (L, 0.3)],
    CABIN0=1.4, CABIN1=3.62,
    roofz=[(1.4, 0.88), (1.6, 1.04), (1.9, 1.12), (2.2, 1.136), (2.5, 1.11), (2.8, 1.02), (3.1, 0.9), (3.4, 0.77), (3.62, 0.67)],
    roofw=[(1.4, 0.74), (1.9, 0.6), (2.4, 0.57), (3.0, 0.62), (3.62, 0.74)],
    crown=[(0, 0.02), (1.5, 0.03), (3.2, 0.04), (4.0, 0.05), (4.5, 0.03), (L, 0.01)],
    ws=(2.72, 3.58), rear_glass=(1.45, 1.62), windows=[(1.95, 3.6)], doors=[('f', 1.95, 3.42)], roof_black=False, top_art=top_art,
    FB_Z=L - 0.4, RB_Z=0.3, hood=(3.66, L - 0.16, 0.6), fuel=('r', 3.65, 0.66), fb_top=6,
    head=[dict(view='T', outline=[(0.5, 4.6), (0.74, 4.5), (0.92, 4.3), (0.95, 4.22), (0.86, 4.26), (0.66, 4.42), (0.48, 4.52)],
               lamps=[(0.62, 4.5, 0.03, 'head'), (0.74, 4.42, 0.03, 'high')], house='head_house_black',
               strips=[([(0.52, 4.57), (0.75, 4.47), (0.92, 4.28)], 0.012, 'drl'), ([(0.72, 4.48), (0.68, 4.42)], 0.012, 'drl'),
                       ([(0.88, 4.3), (0.93, 4.24)], 0.03, 'ind')])],
    mouth=[dict(shape='poly', poly=lambda cx, cy, w, h: [(-0.28, 0.2), (0.28, 0.2), (0.2, 0.25), (-0.2, 0.25)], w=1, h=1, cx=0, cy=0, pattern='bars',
                pitch=0.012, grow=0)],
    front_art=front_art, side_art=side_art,
    tail=dict(housings=[dict(outline=rr(-0.92, 0.42, 0.92, 0.8, 0.01), style='grille', off=0.004, bezel='trim')],
              strips=[([(0.42, 0.76), (0.62, 0.7), (0.88, 0.74)], 0.016, 'tail', 0.012), ([(0.62, 0.7), (0.66, 0.6), (0.86, 0.56)], 0.016, 'tail', 0.012),
                      ([(0.5, 0.735), (0.7, 0.72)], 0.012, 'brake', 0.014), ([(0.86, 0.74), (0.9, 0.7)], 0.02, 'ind', 0.014)],
              rects=[bar(0.2, 0.46, 0.3, 0.5, 'reverse', 'rev_core_r', 0.008)], round_off=0.016),
    badge='', plate_f=0.3, plate_r=0.52, exhaust=(0.0, 0.38, -0.04), stock_exhaust='center', spoiler=(0.05, 0.85, 'none'),
    stock_wheel='turbine', caliper=(200, 150, 40), beam=(0.68, 0.48, 4.5),
    cabin=dict(driver_x=0.36, rear=False, cluster='digital', screen=True, shifter='auto', seat='sport'),
    maxSpeed=345, accel=8.2, brake=11.5, mass=1575, fuel_l=90, spawnWeight=2, engine='v8_sport', horn='high', backfire=0.3,
    colors=['#9acd32', '#f2a900', '#f2f2f0', '#1b1c1e', '#c8102e', '#7a7f86'],
)
