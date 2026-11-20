"""BMW M3 (G80 facelift, 2026). Huge vertical kidney grilles reaching down the bumper, slim angular laser headlamps with two
L-shaped daytime strips, big corner intakes, power-dome bonnet, gill vent behind the front wheel; L-shaped smoked 3D tail lamps,
black diffuser with quad tips, boot-lid lip spoiler, M wheels."""
from _h import *
import numpy as np
L = 4.794


def side_art(car, out, P, n):
    from bb import inside
    z, y = P[..., 2], P[..., 1]
    m = inside(np.stack([z, y], -1), [(3.35, 0.62), (3.45, 0.64), (3.48, 0.8), (3.4, 0.82)])
    out[m] = (20, 20, 22, 240)
    out[m & (np.mod(y * 50, 1.0) < 0.35)] = (60, 62, 66, 240)


h = slim(0.36, 0.585, 0.8, 0.64, rise=0.035, shape='angular')
h['strips'] += [([(0.44, 0.63), (0.5, 0.6), (0.56, 0.6)], 0.01, 'drl'), ([(0.6, 0.64), (0.66, 0.61), (0.72, 0.62)], 0.01, 'drl')]

SPEC = dict(
    id='bmw_m3_2026', name='BMW M3 2026', make='BMW', model='M3 (G80)', year=2026, category='sports', category_name='Sports saloon',
    description='Latest M3 saloon: twin-turbo straight six, huge kidney grilles and track-ready chassis.',
    L=L, W=1.903, H=1.433, YR=1.03, YF=3.89, R=0.34, TRACK=0.81, Rr=0.241, TW=0.28,
    bot=[(0, 0.37), (0.15, 0.29), (0.6, 0.24), (1.5, 0.21), (3.4, 0.21), (4.4, 0.22), (L, 0.26)],
    belt=[(0, 0.9), (0.06, 0.96), (0.5, 0.99), (1.2, 0.98), (2.3, 0.95), (3.3, 0.92), (3.9, 0.88), (4.4, 0.8), (L, 0.72)],
    hw=[(0, 0.86), (0.1, 0.91), (0.6, 0.945), (1.1, 0.95), (2.3, 0.925), (3.4, 0.935), (3.9, 0.945), (4.4, 0.92), (4.65, 0.87), (L, 0.8)],
    zsh=[(0, 0.66), (2.0, 0.62), (L, 0.58)],
    CABIN0=1.0, CABIN1=3.45,
    roofz=[(1.0, 0.99), (1.15, 1.12), (1.45, 1.33), (1.8, 1.42), (2.5, 1.433), (2.85, 1.42), (3.05, 1.3), (3.25, 1.12), (3.45, 0.93)],
    roofw=[(1.0, 0.76), (1.45, 0.69), (2.4, 0.66), (2.95, 0.68), (3.45, 0.76)],
    crown=[(0, 0.015), (1.0, 0.02), (3.4, 0.03), (4.0, 0.05), (4.5, 0.03), (L, 0.012)],
    ws=(3.0, 3.42), rear_glass=(1.05, 1.6), windows=[(1.35, 2.3), (2.37, 3.45)], bpillars=[(2.3, 2.37)], doors=[('r', 1.55, 2.33), ('f', 2.33, 3.4)],
    FB_Z=L - 0.32, RB_Z=0.36, hood=(3.47, L - 0.1, 0.7), trunk_z=1.0, trunk_y=0.84, fuel=('r', 0.85, 0.92), side_art=side_art,
    head=[h],
    mouth=[dict(w=0.22, h=0.36, r=0.05, cx=0.13, cy=0.43, pattern='bars', pitch=0.022, mirror=True, frame=(18, 18, 20), grow=0),
           dict(w=0.24, h=0.17, r=0.03, cx=0.63, cy=0.31, pattern='honeycomb', mirror=True), dict(w=0.5, h=0.05, r=0.02, cx=0.0, cy=0.23, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=[(0.42, 0.9), (0.88, 0.89), (0.92, 0.84), (0.74, 0.84), (0.7, 0.8), (0.42, 0.82)], style='smoke_red', off=0.014)],
              strips=[([(0.45, 0.87), (0.86, 0.865), (0.88, 0.85)], 0.012, 'tail', 0.018), ([(0.72, 0.86), (0.7, 0.82)], 0.012, 'tail', 0.018)],
              rects=[bar(0.46, 0.83, 0.62, 0.85, 'brake', 'brake_core_r', 0.02), bar(0.76, 0.85, 0.84, 0.862, 'ind', 'amber', 0.02),
                     bar(0.2, 0.33, 0.3, 0.36, 'reverse', 'rev_core_r', 0.006)],
              garnish=dict(outline=rr(-0.1, 0.78, 0.1, 0.82), text_y=0.8, bg=(30, 30, 32), th=0.03)),
    badge='M3', plate_f=0.36, plate_r=0.62, exhaust=(-0.5, 0.31, -0.04), stock_exhaust='quad', spoiler=(0.06, 0.68, 'ducktail'),
    stock_wheel='turbine', caliper=(200, 30, 30), beam=(0.6, 0.62, L - 0.05), emblem_y=(0.66, None),
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport'),
    maxSpeed=290, accel=7.4, brake=11.0, mass=1730, fuel_l=59, spawnWeight=4, engine='i6_twinturbo', horn='high', backfire=0.15,
    colors=['#3fae2a', '#1f6b4f', '#1b1c1e', '#e9edf0', '#1d3f9a', '#8c9095'],
)

# model-specific lamp design
SPEC['head'] = [dict(view='F', outline=[(0.36, 0.6), (0.46, 0.64), (0.8, 0.68), (0.82, 0.655), (0.72, 0.62), (0.56, 0.6), (0.42, 0.585)], lamps=[(0.6, 0.632, 0.02, 'head'), (0.7, 0.648, 0.02, 'high')], strips=[([(0.46, 0.627), (0.53, 0.612), (0.56, 0.626)], 0.009, 'drl'), ([(0.63, 0.645), (0.69, 0.633), (0.72, 0.65)], 0.009, 'drl')], rects=[(0.76, 0.655, 0.81, 0.672, 'ind')], smooth=0, house='head_house_black', bezel='trim')]
