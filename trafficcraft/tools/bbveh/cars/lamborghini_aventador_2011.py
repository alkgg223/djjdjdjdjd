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


SPEC = dict(
    id='lamborghini_aventador_2011', name='Lamborghini Aventador 2011', make='Lamborghini', model='Aventador LP700-4', year=2011, category='super',
    category_name='Supercar', description='V12 flagship with a carbon tub, scissor doors and a fighter-jet wedge shape.',
    L=L, W=2.03, H=1.136, YR=1.0, YF=3.7, R=0.345, TRACK=0.85, Rr=0.254, TW=0.3, tumble=0.1,
    bot=[(0, 0.3), (0.15, 0.24), (0.6, 0.19), (1.4, 0.17), (3.2, 0.17), (4.2, 0.18), (L, 0.2)],
    belt=[(0, 0.86), (0.06, 0.88), (0.5, 0.88), (1.3, 0.86), (2.0, 0.8), (2.8, 0.72), (3.4, 0.64), (4.0, 0.52), (4.5, 0.42), (L, 0.33)],
    hw=[(0, 0.9), (0.1, 0.97), (0.6, 1.01), (1.2, 1.0), (2.0, 0.96), (2.9, 0.97), (3.6, 1.0), (4.1, 0.96), (4.5, 0.86), (L, 0.72)],
    zsh=[(0, 0.56), (1.2, 0.52), (2.2, 0.48), (3.6, 0.44), (L, 0.3)],
    CABIN0=1.55, CABIN1=3.2,
    roofz=[(1.55, 0.86), (1.7, 1.05), (1.95, 1.13), (2.25, 1.136), (2.55, 1.1), (2.8, 0.98), (3.0, 0.84), (3.2, 0.68)],
    roofw=[(1.55, 0.72), (1.95, 0.6), (2.4, 0.58), (3.2, 0.7)],
    crown=[(0, 0.02), (1.5, 0.03), (3.2, 0.03), (4.0, 0.03), (L, 0.01)],
    ws=(2.5, 3.16), rear_glass=(0.6, 1.5), windows=[(1.95, 3.1)], doors=[('f', 1.95, 3.05)], roof_black=False,
    FB_Z=L - 0.4, RB_Z=0.3, hood=(3.22, L - 0.16, 0.66), fuel=('r', 3.0, 0.7), fb_top=6,
    head=[dict(view='T', outline=[(0.5, 4.6), (0.74, 4.5), (0.92, 4.3), (0.95, 4.22), (0.86, 4.26), (0.66, 4.42), (0.48, 4.52)],
               lamps=[(0.62, 4.5, 0.03, 'head'), (0.74, 4.42, 0.03, 'high')], house='head_house_black',
               strips=[([(0.52, 4.57), (0.75, 4.47), (0.92, 4.28)], 0.012, 'drl'), ([(0.72, 4.48), (0.68, 4.42)], 0.012, 'drl'),
                       ([(0.88, 4.3), (0.93, 4.24)], 0.03, 'ind')])],
    mouth=[dict(shape='poly', poly=tri(1), w=0.42, h=0.24, cx=0.66, cy=0.32, pattern='honeycomb', grow=0),
           dict(shape='poly', poly=tri(-1), w=0.42, h=0.24, cx=0.66, cy=0.32, pattern='honeycomb', grow=0),
           dict(w=0.6, h=0.06, r=0.02, cx=0.0, cy=0.24, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(-0.92, 0.42, 0.92, 0.8, 0.01), style='grille', off=0.004, bezel='trim')],
              strips=[([(0.42, 0.76), (0.62, 0.7), (0.88, 0.74)], 0.016, 'tail', 0.012), ([(0.62, 0.7), (0.66, 0.6), (0.86, 0.56)], 0.016, 'tail', 0.012),
                      ([(0.5, 0.735), (0.7, 0.72)], 0.012, 'brake', 0.014), ([(0.86, 0.74), (0.9, 0.7)], 0.02, 'ind', 0.014)],
              rects=[bar(0.2, 0.46, 0.3, 0.5, 'reverse', 'rev_core_r', 0.008)], round_off=0.016),
    badge='', plate_f=0.3, plate_r=0.52, exhaust=(0.0, 0.38, -0.04), stock_exhaust='center', spoiler=(0.05, 0.85, 'none'),
    stock_wheel='turbine', caliper=(200, 150, 40), beam=(0.68, 0.48, 4.5),
    cabin=dict(driver_x=0.36, rear=False, cluster='digital', screen=True, shifter='auto', seat='sport', seat_z=2.2),
    maxSpeed=345, accel=8.2, brake=11.5, mass=1575, fuel_l=90, spawnWeight=2, engine='v8_sport', horn='high', backfire=0.3,
    colors=['#9acd32', '#f2a900', '#f2f2f0', '#1b1c1e', '#c8102e', '#7a7f86'],
)
