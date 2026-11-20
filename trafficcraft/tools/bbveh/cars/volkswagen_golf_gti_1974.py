"""Volkswagen Golf GTI (Mk1, 1974-76). Round headlamps inside a full-width black grille with the GTI's red trim line around it, black
bumpers with indicators; tall vertical tail lamps (red / amber / white) at the corners of the square hatch."""
from _h import *
import numpy as np
L = 3.705


def front_art(car, out, P, n, uv, k):
    x, y = P[..., 0], P[..., 1]
    # the GTI red pinstripe framing the grille
    out[(np.abs(x) < 0.66) & ((np.abs(y - 0.71) < 0.006) | (np.abs(y - 0.53) < 0.006))] = (200, 20, 30, 240)
    out[(y < 0.42) & (y > 0.33)] = (26, 26, 28, 240)       # black bumper


SPEC = dict(
    id='volkswagen_golf_gti_1974', name='Volkswagen Golf GTI 1974', make='Volkswagen', model='Golf GTI (Mk1)', year=1974, category='compact',
    category_name='Hot hatch', description='The car that defined the hot hatch: a light, quick, practical Golf with a fuel-injected engine.',
    L=L, W=1.61, H=1.395, YR=0.57, YF=2.97, R=0.28, TRACK=0.68, Rr=0.165, TW=0.175, tumble=0.04, front_art=front_art,
    bot=[(0, 0.33), (0.12, 0.26), (0.4, 0.22), (1.0, 0.2), (2.6, 0.2), (3.3, 0.22), (L, 0.28)],
    belt=[(0, 0.86), (0.06, 0.88), (0.4, 0.89), (1.2, 0.88), (2.0, 0.86), (2.6, 0.83), (3.2, 0.78), (L, 0.74)],
    hw=[(0, 0.76), (0.06, 0.785), (0.4, 0.8), (1.4, 0.805), (2.8, 0.8), (3.3, 0.79), (L, 0.77)],
    zsh=[(0, 0.6), (L, 0.56)],
    CABIN0=0.05, CABIN1=2.5,
    roofz=[(0.05, 0.89), (0.12, 1.2), (0.35, 1.38), (1.0, 1.395), (1.85, 1.39), (2.05, 1.3), (2.3, 1.05), (2.5, 0.84)],
    roofw=[(0.05, 0.66), (0.35, 0.6), (1.6, 0.58), (2.5, 0.66)],
    crown=[(0, 0.01), (2.5, 0.012), (L, 0.01)],
    ws=(2.05, 2.46), rear_glass=(0.07, 0.36), windows=[(0.42, 1.12), (1.18, 2.5)], bpillars=[(1.12, 1.18)], doors=[('f', 1.15, 2.45)],
    FB_Z=L - 0.2, RB_Z=0.2, hood=(2.52, L - 0.06, 0.62), hatch_x=0.62, fuel=('r', 0.45, 0.8),
    head=[round_head(0.54, 0.62, 0.075, bezel='trim')],
    front_lamps=[dict(outline=rr(0.5, 0.36, 0.64, 0.4), kind='ind', style='amber', house=False, lift=0.01)],
    mouth=[dict(w=1.3, h=0.17, r=0.01, cx=0.0, cy=0.62, pattern='bars', pitch=0.03, col=(22, 22, 24), hole=(46, 46, 50))],
    tail=dict(housings=[dict(outline=rr(0.56, 0.58, 0.76, 0.82, 0.012), style='tail_house', off=0.01)],
              rects=[bar(0.58, 0.7, 0.74, 0.8), bar(0.58, 0.64, 0.74, 0.69, 'ind', 'amber', 0.012), bar(0.58, 0.595, 0.74, 0.635, 'reverse', 'rev_core_r', 0.012),
                     bar(0.6, 0.72, 0.72, 0.78, 'brake', 'brake_core_r', 0.016)],
              garnish=dict(outline=rr(-0.08, 0.86, 0.08, 0.9), text_y=0.88, bg=(26, 26, 28), fg=(210, 30, 40), th=0.03)),
    badge='GTI', plate_f=0.37, plate_r=0.55, exhaust=(-0.4, 0.27, -0.04), stock_exhaust='oval', spoiler=(0.05, 0.6, 'none'),
    stock_wheel='snowflake', beam=(0.54, 0.62, L - 0.04),
    cabin=dict(driver_x=0.3, rear=True, rear_gap=0.66, bench=True, cluster='analog2', screen=False, shifter='manual', seat='sport'),
    maxSpeed=182, accel=4.0, brake=7.5, mass=810, fuel_l=45, spawnWeight=6, engine='i4_sport', horn='car',
    colors=['#b5121b', '#f2f2f0', '#1b1c1e', '#b9bdc2', '#2f5f3a'],
)

# modern-style lamps (from the reference photos)
h = slim(0.36, 0.58, 0.74, 0.635, rise=0.01, shape='curve')
h['strips'].append(([(0.04, 0.645), (0.38, 0.64)], 0.01, 'drl'))
SPEC['head'] = [h]

# model-specific lamp design
SPEC['head'] = [dict(view='F', outline=[(0.36, 0.6), (0.72, 0.615), (0.74, 0.645), (0.6, 0.65), (0.38, 0.63)], lamps=[(0.5, 0.622, 0.02, 'head'), (0.6, 0.627, 0.02, 'high')], strips=[([(0.03, 0.622), (0.37, 0.618)], 0.008, 'drl'), ([(0.39, 0.605), (0.72, 0.62)], 0.009, 'drl')], rects=[(0.68, 0.62, 0.73, 0.64, 'ind')], smooth=1, house='head_house_black', bezel='trim')]

# real-size lamp wrapping over the nose corner
SPEC['head'] = [big(SPEC['head'][0])]

# lamp size pass
SPEC['tail_scale'] = 1.22

SPEC['grip'] = 0.9
