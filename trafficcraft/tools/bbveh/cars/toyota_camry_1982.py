"""Toyota Camry (V10, 1982) saloon. Flush rectangular headlamps that wrap into amber corner lamps, slim chrome-framed horizontal grille;
wide rectangular tail lamps with red, amber and white sections, square 80s body with black rubbing strips."""
from _h import *
import numpy as np
L = 4.435


def side_art(car, out, P, n):
    y, z = P[..., 1], P[..., 2]
    out[(np.abs(y - 0.55) < 0.016) & (z > 0.3) & (z < L - 0.3)] = (26, 26, 28, 240)


SPEC = dict(
    id='toyota_camry_1982', name='Toyota Camry 1982', make='Toyota', model='Camry (V10)', year=1982, category='sedan', category_name='Saloon',
    description='First front-wheel-drive Camry: a sensible, square-cut family saloon built to last.',
    L=L, W=1.69, H=1.37, YR=0.92, YF=3.52, R=0.29, TRACK=0.71, Rr=0.178, TW=0.185, tumble=0.04,
    bot=[(0, 0.34), (0.12, 0.27), (0.5, 0.23), (1.3, 0.21), (3.0, 0.21), (4.0, 0.23), (L, 0.29)],
    belt=[(0, 0.86), (0.06, 0.9), (0.5, 0.905), (1.4, 0.9), (2.5, 0.88), (3.3, 0.86), (3.9, 0.82), (L, 0.78)],
    hw=[(0, 0.8), (0.06, 0.83), (0.5, 0.84), (1.5, 0.845), (3.2, 0.845), (3.9, 0.84), (L, 0.82)],
    zsh=[(0, 0.62), (L, 0.6)],
    CABIN0=0.95, CABIN1=3.1,
    roofz=[(0.95, 0.905), (1.1, 1.08), (1.4, 1.33), (1.7, 1.37), (2.5, 1.37), (2.75, 1.34), (2.9, 1.18), (3.0, 1.04), (3.1, 0.86)],
    roofw=[(0.95, 0.7), (1.4, 0.64), (2.4, 0.62), (2.9, 0.64), (3.1, 0.7)],
    crown=[(0, 0.01), (3.0, 0.012), (L, 0.01)],
    ws=(2.8, 3.06), rear_glass=(1.0, 1.4), windows=[(1.42, 2.08), (2.14, 3.1)], bpillars=[(2.08, 2.14)], doors=[('r', 1.38, 2.11), ('f', 2.11, 3.06)],
    FB_Z=L - 0.25, RB_Z=0.28, hood=(3.12, L - 0.06, 0.66), trunk_z=0.95, trunk_y=0.8, fuel=('r', 0.6, 0.82), side_art=side_art,
    head=[rect_head(0.34, 0.6, 0.74, 0.7, lamps=[(0.44, 0.65, 0.04, 'head'), (0.6, 0.65, 0.04, 'high')], rects=[(0.69, 0.61, 0.74, 0.69, 'ind')],
                    bezel='chrome', house='head_house')],
    mouth=[dict(w=0.6, h=0.1, r=0.01, cx=0.0, cy=0.65, pattern='bars', pitch=0.02, col=(22, 22, 24), hole=(60, 60, 64), frame=(214, 218, 222)),
           dict(w=1.6, h=0.06, r=0.01, cx=0.0, cy=0.44, pattern='none', col=(26, 26, 28)), dict(w=0.5, h=0.05, r=0.01, cx=0.0, cy=0.33, pattern='bars', pitch=0.015)],
    tail=dict(housings=[dict(outline=rr(0.32, 0.66, 0.82, 0.8, 0.01), style='tail_house', off=0.008, bezel='chrome')],
              rects=[bar(0.56, 0.68, 0.8, 0.78), bar(0.34, 0.68, 0.44, 0.78, 'ind', 'amber', 0.012), bar(0.45, 0.68, 0.55, 0.78, 'reverse', 'rev_core_r', 0.012),
                     bar(0.6, 0.7, 0.77, 0.76, 'brake', 'brake_core_r', 0.016)]),
    badge='', plate_f=0.36, plate_r=0.58, exhaust=(-0.45, 0.28, -0.04), stock_exhaust='oval', spoiler=(0.05, 0.66, 'none'),
    stock_wheel='aero', beam=(0.52, 0.65, L - 0.04),
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.8, bench=True, cluster='analog2', screen=False, shifter='auto', seat='flat'),
    maxSpeed=165, accel=3.2, brake=7.0, mass=1050, fuel_l=55, spawnWeight=7, engine='i4_petrol', horn='car',
    colors=['#c9c4b4', '#8a1c22', '#3d5a7a', '#e8e4d8', '#4b4f54', '#6b5a3c'],
)

# modern-style lamps (from the reference photos)
h = slim(0.34, 0.6, 0.76, 0.66, rise=0.02)
h['strips'].append(([(0.37, 0.61), (0.35, 0.54), (0.42, 0.5)], 0.012, 'drl'))
SPEC['head'] = [h]

# model-specific lamp design
SPEC['head'] = [dict(view='F', outline=[(0.36, 0.62), (0.74, 0.66), (0.76, 0.63), (0.4, 0.6)], lamps=[(0.52, 0.625, 0.016, 'head'), (0.62, 0.635, 0.016, 'high')], strips=[([(0.38, 0.618), (0.35, 0.56), (0.41, 0.52), (0.5, 0.51)], 0.012, 'drl'), ([(0.4, 0.616), (0.72, 0.653)], 0.009, 'drl')], rects=[(0.7, 0.64, 0.75, 0.655, 'ind')], smooth=0, house='head_house_black', bezel='trim')]

# real-size lamp wrapping over the nose corner
SPEC['head'] = [big(SPEC['head'][0])]

# lamp size pass
SPEC['tail_scale'] = 1.22

# real performance: 0-100 km/h in 13.0 s, top speed 165 km/h
SPEC['accel'] = 2.73
SPEC['maxSpeed'] = 165
