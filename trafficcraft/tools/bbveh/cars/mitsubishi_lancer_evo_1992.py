"""Mitsubishi Lancer Evolution (Evo I, 1992). Slim rectangular headlamps angled up at the outer end with amber corner lamps, a thin
grille slot, big lower intake with fog lamps; bonnet ducts; wide red tail lamps joined by a red centre garnish, boot wing."""
from _h import *
import numpy as np
L = 4.31


def top_art(car, out, P, n):
    z, x = P[..., 2], P[..., 0]
    for sx in (1, -1):
        m = (np.abs(x - sx * 0.3) < 0.12) & (z > 3.55) & (z < 3.7)
        out[m] = (18, 18, 20, 240)
        out[m & (np.mod(z * 60, 1.0) < 0.35)] = (50, 50, 54, 240)


SPEC = dict(
    id='mitsubishi_lancer_evo_1992', name='Mitsubishi Lancer Evolution 1992', make='Mitsubishi', model='Lancer Evolution I', year=1992,
    category='sports', category_name='Rally saloon', description='The first Lancer Evolution: a Group A rally homologation saloon with a turbo four and AWD.',
    L=L, W=1.695, H=1.395, YR=0.95, YF=3.45, R=0.3, TRACK=0.72, Rr=0.19, TW=0.195, tumble=0.04, top_art=top_art,
    bot=[(0, 0.34), (0.12, 0.27), (0.5, 0.23), (1.3, 0.215), (3.0, 0.215), (3.9, 0.225), (L, 0.27)],
    belt=[(0, 0.88), (0.06, 0.92), (0.5, 0.93), (1.3, 0.92), (2.3, 0.9), (3.0, 0.88), (3.6, 0.83), (4.0, 0.78), (L, 0.73)],
    hw=[(0, 0.8), (0.06, 0.83), (0.5, 0.845), (1.3, 0.847), (2.6, 0.845), (3.5, 0.847), (4.0, 0.83), (L, 0.78)],
    zsh=[(0, 0.62), (L, 0.58)],
    CABIN0=0.95, CABIN1=3.1,
    roofz=[(0.95, 0.93), (1.1, 1.1), (1.4, 1.35), (1.7, 1.39), (2.4, 1.395), (2.65, 1.37), (2.85, 1.2), (3.0, 1.05), (3.1, 0.88)],
    roofw=[(0.95, 0.7), (1.4, 0.63), (2.4, 0.61), (2.85, 0.63), (3.1, 0.7)],
    crown=[(0, 0.012), (3.0, 0.02), (L, 0.012)],
    ws=(2.75, 3.06), rear_glass=(1.0, 1.42), windows=[(1.44, 2.02), (2.08, 3.1)], bpillars=[(2.02, 2.08)], doors=[('r', 1.4, 2.05), ('f', 2.05, 3.06)],
    FB_Z=L - 0.28, RB_Z=0.3, hood=(3.12, L - 0.08, 0.64), trunk_z=0.95, trunk_y=0.82, fuel=('r', 0.6, 0.86),
    head=[dict(view='F', outline=[(0.3, 0.62), (0.7, 0.64), (0.78, 0.69), (0.74, 0.71), (0.32, 0.69)], smooth=0, bezel='trim',
               lamps=[(0.42, 0.655, 0.03, 'head'), (0.55, 0.66, 0.03, 'high')], rects=[(0.67, 0.65, 0.75, 0.69, 'ind')])],
    front_lamps=[dict(outline=rr(0.56, 0.34, 0.68, 0.38), kind='fog', style='fog_lens')],
    mouth=[dict(w=0.42, h=0.045, r=0.015, cx=0.0, cy=0.655, pattern='bars', pitch=0.015), dict(w=0.9, h=0.13, r=0.03, cx=0.0, cy=0.37, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.34, 0.73, 0.8, 0.83, 0.01), style='tail_house', off=0.01)],
              rects=[bar(0.36, 0.77, 0.78, 0.82), bar(0.36, 0.74, 0.5, 0.765, 'ind', 'amber', 0.012), bar(0.51, 0.74, 0.62, 0.765, 'reverse', 'rev_core_r', 0.012),
                     bar(0.63, 0.74, 0.78, 0.765, 'brake', 'brake_core_r', 0.012)],
              garnish=dict(outline=rr(-0.32, 0.74, 0.32, 0.82), text_y=0.78, bg=(150, 14, 24), fg=(230, 230, 230), th=0.03)),
    badge='LANCER', plate_f=0.34, plate_r=0.6, exhaust=(-0.45, 0.29, -0.04), stock_exhaust='oval', spoiler=(0.06, 0.66, 'low_wing'),
    stock_wheel='spoke5', beam=(0.48, 0.66, L - 0.04),
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog2', screen=False, shifter='manual', seat='sport'),
    maxSpeed=230, accel=5.8, brake=9.2, mass=1240, fuel_l=50, spawnWeight=5, engine='i4_turbo', horn='high', backfire=0.25,
    colors=['#f2f2f0', '#a40f16', '#1b1c1e', '#b9bdc2', '#1d3f7a'],
)

# modern-style lamps (from the reference photos)
SPEC['head'] = [slim(0.34, 0.6, 0.76, 0.655, rise=0.04, shape='angular')]
SPEC['mouth'][1].update(w=0.8, h=0.2, cy=0.42)

# model-specific lamp design
SPEC['head'] = [dict(view='F', outline=[(0.34, 0.6), (0.42, 0.655), (0.76, 0.68), (0.78, 0.65), (0.62, 0.62), (0.46, 0.6)], lamps=[(0.56, 0.64, 0.024, 'head'), (0.67, 0.652, 0.022, 'high')], strips=[], rects=[(0.73, 0.655, 0.775, 0.67, 'ind')], smooth=0, house='head_house_black', bezel='trim')]

# real-size lamp wrapping over the nose corner
SPEC['head'] = [big(SPEC['head'][0])]

# lamp size pass
SPEC['tail_scale'] = 1.22

SPEC['grip'] = 1.05
