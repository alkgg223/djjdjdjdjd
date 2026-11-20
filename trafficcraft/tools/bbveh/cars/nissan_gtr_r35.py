"""Nissan GT-R (R35, 2007).
Lamps: long boomerang headlamp that runs from the grille round the corner and back up the fender, three projector units in a row
and an L-shaped LED daytime strip along its lower edge; chrome-framed trapezoid mesh grille, side intakes with fog lamps.
Rear: four round tail lamps (two per side) each a bright ring with a dark centre inside a black round surround, reversing lamps low
in the bumper, quad round titanium tips, carbon diffuser look, trunk-lid wing."""
from bb import rrect, ngon
import numpy as np

L = 4.655


def side_art(car, out, P, n):
    # the R35's vertical fender gill behind the front wheel
    z, y = P[..., 2], P[..., 1]
    m = (z > 3.26) & (z < 3.36) & (y > 0.6) & (y < 0.78)
    out[m] = (20, 20, 22, 240)
    out[m & (np.mod(y * 60, 1.0) < 0.4)] = (60, 62, 66, 240)


SPEC = dict(
    id='nissan_gtr_r35', name='Nissan GT-R R35', make='Nissan', model='GT-R (R35)', year=2007, category='sports', category_name='Sports coupe',
    description='All-wheel-drive 2+2 supercar with a hand-built twin-turbo V6, nicknamed Godzilla.',
    L=L, W=1.895, H=1.37, YR=0.93, YF=3.71, R=0.356, TRACK=0.8, Rr=0.254, TW=0.255,
    bot=[(0.0, 0.38), (0.15, 0.3), (0.45, 0.24), (1.4, 0.215), (3.1, 0.215), (4.0, 0.22), (4.4, 0.225), (L, 0.27)],
    belt=[(0.0, 0.9), (0.06, 0.97), (0.25, 1.0), (0.7, 1.01), (1.2, 0.995), (2.0, 0.965), (2.8, 0.94), (3.25, 0.92), (3.7, 0.865), (4.1, 0.79),
          (4.4, 0.72), (L, 0.62)],
    hw=[(0.0, 0.8), (0.08, 0.86), (0.3, 0.92), (0.8, 0.947), (1.2, 0.94), (2.0, 0.925), (2.9, 0.93), (3.6, 0.945), (4.0, 0.93), (4.3, 0.88),
        (4.5, 0.79), (L, 0.64)],
    zsh=[(0.0, 0.64), (1.0, 0.62), (2.5, 0.58), (3.7, 0.56), (L, 0.5)],
    CABIN0=0.75, CABIN1=3.3,
    roofz=[(0.75, 1.01), (0.9, 1.08), (1.2, 1.2), (1.55, 1.315), (1.9, 1.36), (2.3, 1.37), (2.65, 1.345), (2.9, 1.24), (3.12, 1.08), (3.3, 0.92)],
    roofw=[(0.75, 0.76), (1.0, 0.7), (1.5, 0.64), (2.2, 0.62), (2.7, 0.63), (3.0, 0.67), (3.3, 0.74)],
    crown=[(0.0, 0.015), (0.75, 0.02), (3.3, 0.03), (3.9, 0.045), (4.4, 0.03), (L, 0.01)],
    ws=(2.72, 3.25), rear_glass=(0.8, 1.55), windows=[(1.28, 1.6), (1.67, 3.3)], bpillars=[(1.6, 1.67)], doors=[('f', 1.66, 3.25)],
    FB_Z=4.0, RB_Z=0.4, hood=(3.32, 4.52, 0.66), trunk_z=0.74, trunk_y=0.8, fuel=('l', 0.85, 0.92),
    head=[dict(view='T', outline=[(0.42, 4.565), (0.6, 4.52), (0.76, 4.44), (0.86, 4.32), (0.885, 4.15), (0.87, 3.98), (0.82, 3.96), (0.79, 4.12),
                                  (0.72, 4.27), (0.6, 4.37), (0.46, 4.45), (0.4, 4.5)],
               lamps=[(0.5, 4.5, 0.042, 'head'), (0.63, 4.44, 0.042, 'high'), (0.77, 4.33, 0.03, 'ind')],
               strips=[([(0.44, 4.545), (0.6, 4.5), (0.76, 4.42), (0.845, 4.3), (0.865, 4.14), (0.855, 4.0)], 0.014, 'drl')])],
    front_lamps=[dict(outline=[(0.6, 0.31), (0.72, 0.31), (0.72, 0.34), (0.6, 0.34)], kind='fog', style='fog_lens')],
    mouth=[dict(w=0.7, h=0.22, r=0.05, cx=0.0, cy=0.47, pattern='honeycomb', frame=(200, 204, 210)),
           dict(w=0.26, h=0.13, r=0.03, cx=0.62, cy=0.34, pattern='honeycomb', mirror=True),
           dict(w=0.56, h=0.05, r=0.02, cx=0.0, cy=0.29, pattern='bars', pitch=0.02)],
    tail=dict(housings=[dict(outline=ngon(0.1, 16, 0.68, 0.9)), dict(outline=ngon(0.1, 16, 0.45, 0.9))],
              rounds=[(0.68, 0.9, 0.082, 'tail', 'brake', 0.55), (0.45, 0.9, 0.082, 'tail', 'brake', 0.55)],
              rects=[([(0.2, 0.41), (0.3, 0.41), (0.3, 0.44), (0.2, 0.44)], 'reverse', 'rev_core_r', 0.006),
                     ([(0.52, 0.79), (0.62, 0.79), (0.62, 0.81), (0.52, 0.81)], 'ind', 'amber', 0.012)],
              garnish=dict(outline=rrect(0.22, 0.07, 0.02, 2, 0.0, 0.9), text_y=0.9), round_off=0.024),
    badge='GT-R', plate_f=0.36, plate_r=0.6, exhaust=(-0.55, 0.32, -0.04), stock_exhaust='quad', spoiler=(0.05, 0.75, 'low_wing'),
    stock_wheel='turbine', caliper=(200, 150, 40), beam=(0.62, 0.72, 4.45), side_art=side_art,
    cabin=dict(driver_x=0.33, rear=True, cluster='analog3', screen=True, shifter='auto', seat='sport'),
    maxSpeed=315, accel=7.2, brake=11.0, mass=1740, fuel_l=74, spawnWeight=5, engine='i6_twinturbo', horn='high', backfire=0.12,
    colors=['#dcdedf', '#16171a', '#8c9095', '#1c3f8a', '#a40f16', '#d86a17'],
)

SPEC['grip'] = 1.12
