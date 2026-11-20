"""Porsche 911 (901, 1963). Upright round 'frog-eye' headlamps on top of the front wings with small amber indicators under them,
twin round horn grilles in the slim front valance; small vertical rounded tail lamps on the rear wings, slatted engine lid grille,
sloping fastback roof over a rear engine."""
from _h import *
import numpy as np
L = 4.163


def top_art(car, out, P, n):
    z, x = P[..., 2], P[..., 0]
    m = (z > 0.28) & (z < 0.55) & (np.abs(x) < 0.32)
    out[m & (np.mod(x * 40, 1.0) < 0.45)] = (24, 24, 26, 240)


SPEC = dict(
    id='porsche_911_1963', name='Porsche 911 1963', make='Porsche', model='911 (901)', year=1963, category='classic', category_name='Sports coupe',
    description='Rear-engined air-cooled flat-six coupe with the fastback silhouette that the 911 still keeps.',
    L=L, W=1.61, H=1.32, YR=1.0, YF=3.21, R=0.3, TRACK=0.67, Rr=0.19, TW=0.165, tumble=0.07,
    bot=[(0, 0.36), (0.2, 0.27), (0.6, 0.23), (1.4, 0.21), (3.0, 0.21), (3.8, 0.25), (L, 0.33)],
    belt=[(0, 0.6), (0.12, 0.72), (0.4, 0.8), (0.9, 0.84), (1.4, 0.83), (2.2, 0.82), (2.9, 0.8), (3.3, 0.76), (3.8, 0.68), (L, 0.55)],
    hw=[(0, 0.62), (0.15, 0.72), (0.5, 0.79), (1.0, 0.8), (1.8, 0.78), (2.6, 0.78), (3.2, 0.8), (3.7, 0.77), (4.0, 0.7), (L, 0.6)],
    zsh=[(0, 0.5), (1.0, 0.56), (2.2, 0.55), (3.2, 0.56), (L, 0.48)],
    CABIN0=0.6, CABIN1=2.9,
    roofz=[(0.6, 0.83), (0.9, 0.98), (1.3, 1.16), (1.7, 1.29), (2.0, 1.32), (2.25, 1.31), (2.45, 1.22), (2.7, 1.03), (2.9, 0.8)],
    roofw=[(0.6, 0.62), (1.2, 0.56), (2.0, 0.53), (2.4, 0.54), (2.9, 0.6)],
    crown=[(0, 0.03), (0.6, 0.03), (2.9, 0.03), (3.6, 0.05), (L, 0.02)],
    ws=(2.42, 2.86), rear_glass=(0.75, 1.42), windows=[(1.3, 1.7), (1.73, 2.9)], bpillars=[(1.7, 1.73)], doors=[('f', 1.6, 2.86)],
    FB_Z=L - 0.25, RB_Z=0.25, hood=(2.92, L - 0.1, 0.5), fuel=('l', 3.2, 0.78), rocker_black=False,
    chrome_bumpers=((0.34, 0.42), (0.36, 0.44)), chrome_handles=True, mirror_chrome=True, mirror_round=True, a_black=False, top_art=top_art,
    head=[dict(view='T', outline=ngon(0.1, 16, 0.55, 3.72), lamps=[(0.55, 3.72, 0.08, 'sealed')], bezel='chrome', smooth=0, lens_off=0.016),
          dict(view='F', outline=rr(0.5, 0.54, 0.6, 0.57), lamps=[], rects=[(0.505, 0.545, 0.595, 0.565, 'ind')], bezel='chrome', smooth=0)],
    mouth=[dict(w=0.1, h=0.07, r=0.035, cx=0.36, cy=0.42, pattern='bars', pitch=0.015, mirror=True, frame=(214, 218, 222))],
    tail=dict(housings=[dict(outline=rr(0.5, 0.58, 0.6, 0.72, 0.04), style='chrome', off=0.006, bezel='chrome')],
              rects=[bar(0.51, 0.62, 0.59, 0.71), bar(0.51, 0.59, 0.59, 0.615, 'ind', 'amber', 0.014), bar(0.53, 0.64, 0.57, 0.69, 'brake', 'brake_core_r', 0.016)],
              garnish=dict(outline=rr(-0.1, 0.62, 0.1, 0.66), text_y=0.64, bg=(200, 204, 210), fg=(30, 30, 32), th=0.03)),
    badge='911', plate_f=0.3, plate_r=0.5, exhaust=(-0.35, 0.28, -0.04), stock_exhaust='oval', spoiler=(0.05, 0.6, 'none'),
    stock_wheel='hubcap', beam=(0.55, 0.72, 3.8),
    cabin=dict(driver_x=0.29, rear=True, rear_gap=0.55, cluster='classic', screen=False, shifter='manual', seat='flat'),
    maxSpeed=210, accel=4.6, brake=8.0, mass=1080, fuel_l=62, spawnWeight=3, engine='i4_sport', horn='high',
    colors=['#c9c4b4', '#b5121b', '#1d4f8c', '#e3b23c', '#2d2e30', '#3d6b4a'],
)

# modern-style lamps (from the reference photos)
SPEC['head'][0]['strips'] = [([(0.49, 3.72), (0.61, 3.72)], 0.01, 'drl'), ([(0.55, 3.66), (0.55, 3.78)], 0.01, 'drl')]
SPEC['tail']['center_strips'] = [([(-0.6, 0.67), (0.6, 0.67)], 0.018, 'tail', 0.012)]

# lamp size pass
SPEC['tail_scale'] = 1.22
