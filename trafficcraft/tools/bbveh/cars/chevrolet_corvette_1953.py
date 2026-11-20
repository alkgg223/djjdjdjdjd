"""Chevrolet Corvette (C1, 1953). Open roadster: round headlamps behind mesh stone guards at the top of the front fenders, a chrome
'toothy' grille of vertical bars in an oval mouth; small rocket-fin tail lamps on the rear fenders; whitewall tyres, chrome bumpers."""
from _h import *
L = 4.25


def front_art(car, out, P, n, uv, k):
    import numpy as np
    # stone-guard mesh over the headlamps is drawn on the lamp lens; here: chrome strip along the grille top
    y = P[..., 1]
    out[(abs(P[..., 0]) < 0.5) & (abs(y - 0.58) < 0.008)] = (220, 224, 228, 240)


SPEC = dict(
    id='chevrolet_corvette_1953', name='Chevrolet Corvette 1953', make='Chevrolet', model='Corvette (C1)', year=1953, category='classic',
    category_name='Roadster', description='America\'s first sports car: a fibreglass two-seat roadster with a wrap-around windscreen.',
    L=L, W=1.83, H=1.31, YR=0.82, YF=3.41, R=0.33, TRACK=0.72, Rr=0.19, TW=0.18, open_top=True,
    bot=[(0, 0.38), (0.2, 0.3), (0.6, 0.26), (1.4, 0.24), (3.0, 0.24), (3.9, 0.27), (L, 0.33)],
    belt=[(0, 0.7), (0.1, 0.78), (0.4, 0.84), (0.9, 0.86), (1.4, 0.83), (2.2, 0.82), (3.0, 0.83), (3.5, 0.82), (4.0, 0.78), (L, 0.68)],
    hw=[(0, 0.7), (0.1, 0.8), (0.4, 0.88), (0.9, 0.9), (1.6, 0.86), (2.4, 0.86), (3.4, 0.9), (3.9, 0.86), (4.15, 0.78), (L, 0.68)],
    zsh=[(0, 0.6), (1.0, 0.62), (2.2, 0.6), (3.4, 0.62), (L, 0.55)],
    CABIN0=1.25, CABIN1=2.7,
    roofz=[(1.25, 0.83), (2.3, 0.83), (2.4, 1.15), (2.55, 1.2), (2.7, 0.83)],
    roofw=[(1.25, 0.7), (2.3, 0.7), (2.7, 0.72)],
    crown=[(0, 0.04), (1.0, 0.03), (2.7, 0.04), (3.6, 0.05), (L, 0.02)],
    ws=(2.4, 2.7), windows=[], doors=[('f', 1.55, 2.55)],
    FB_Z=L - 0.32, RB_Z=0.3, hood=(2.75, L - 0.1, 0.55), trunk_z=0.6, fuel=('l', 0.6, 0.82), rocker_black=False,
    chrome_bumpers=((0.36, 0.45), (0.4, 0.48)), chrome_handles=True, mirror_chrome=True, mirror_round=True, a_black=False,
    head=[dict(view='F', outline=ngon(0.1, 16, 0.6, 0.7), lamps=[(0.6, 0.7, 0.082, 'sealed')], bezel='chrome', smooth=0, house='head_house',
               lens_off=0.02)],
    front_lamps=[dict(outline=rr(0.42, 0.42, 0.56, 0.45), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=0.92, h=0.17, r=0.08, cx=0.0, cy=0.48, pattern='vbars', pitch=0.068, col=(220, 224, 228), hole=(18, 18, 20), frame=(214, 218, 222))],
    front_art=front_art,
    tail=dict(rounds=[(0.66, 0.74, 0.04, 'tail', 'brake', 0.5)],
              rects=[bar(0.2, 0.44, 0.28, 0.48, 'reverse', 'rev_core_r', 0.006)], round_off=0.03),
    badge='', plate_f=0.31, plate_r=0.6, exhaust=(-0.35, 0.3, -0.06), stock_exhaust='dual', spoiler=(0.05, 0.7, 'none'),
    stock_wheel='hubcap', whitewall=True, beam=(0.6, 0.7, L - 0.05),
    cabin=dict(driver_x=0.3, rear=False, cluster='classic', screen=False, shifter='auto', seat='flat', seat_z=1.75),
    maxSpeed=170, accel=3.6, brake=6.5, mass=1300, fuel_l=64, spawnWeight=2, engine='v6_cruiser', horn='low',
    colors=['#f4f1e8', '#b5121b', '#1b1c1e', '#2f5e8f', '#d9c27a'],
)
