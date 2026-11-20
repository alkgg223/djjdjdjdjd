"""Dodge Challenger (1st gen, revealed 1969). Recessed full-width black grille with FOUR round headlamps (two per side) inside its
chrome surround; full-width tail-lamp band across the rear panel with the name in the middle; wide, low E-body proportions."""
from _h import *
L = 4.86
SPEC = dict(
    id='dodge_challenger_1969', name='Dodge Challenger 1969', make='Dodge', model='Challenger (1st gen)', year=1969, category='muscle',
    category_name='Muscle car', description='Wide, low E-body muscle car with big-block V8 options and a full-width tail-lamp panel.',
    L=L, W=1.94, H=1.3, YR=1.06, YF=3.86, R=0.345, TRACK=0.8, Rr=0.19, TW=0.215,
    bot=[(0, 0.34), (0.15, 0.27), (0.6, 0.235), (1.5, 0.215), (3.4, 0.215), (4.5, 0.25), (L, 0.3)],
    belt=[(0, 0.84), (0.08, 0.88), (0.5, 0.895), (1.2, 0.895), (2.0, 0.875), (3.0, 0.86), (4.0, 0.85), (4.6, 0.82), (L, 0.79)],
    hw=[(0, 0.88), (0.1, 0.92), (0.6, 0.95), (1.2, 0.96), (2.2, 0.95), (3.4, 0.955), (4.4, 0.94), (4.7, 0.9), (L, 0.87)],
    zsh=[(0, 0.6), (1.0, 0.6), (2.5, 0.58), (L, 0.56)],
    CABIN0=1.05, CABIN1=3.1,
    roofz=[(1.05, 0.895), (1.2, 1.03), (1.5, 1.23), (1.75, 1.29), (2.15, 1.3), (2.5, 1.29), (2.7, 1.22), (2.9, 1.06), (3.1, 0.86)],
    roofw=[(1.05, 0.8), (1.5, 0.69), (2.15, 0.66), (2.6, 0.67), (3.1, 0.75)],
    crown=[(0, 0.01), (1.0, 0.012), (3.0, 0.02), (4.0, 0.028), (L, 0.01)],
    ws=(2.65, 3.05), rear_glass=(1.1, 1.58), windows=[(1.35, 1.86), (1.9, 3.1)], bpillars=[(1.86, 1.9)], doors=[('f', 1.66, 3.05)],
    FB_Z=L - 0.3, RB_Z=0.3, hood=(3.12, L - 0.08, 0.72), trunk_z=1.0, trunk_y=0.8, fuel=('r', 0.6, 0.86),
    chrome_bumpers=((0.38, 0.48), (0.38, 0.48)), chrome_handles=True, mirror_chrome=True, mirror_round=True, a_black=False,
    head=[round_head(0.6, 0.64, 0.07, bezel='trim'), round_head(0.78, 0.64, 0.07, 'high', bezel='trim')],
    front_lamps=[dict(outline=rr(0.5, 0.33, 0.64, 0.36), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=1.78, h=0.2, r=0.02, cx=0.0, cy=0.64, pattern='grid', pitch=0.03, frame=(214, 218, 222), col=(24, 24, 26))],
    tail=dict(housings=[dict(outline=rr(0.04, 0.63, 0.86, 0.75, 0.01), style='chrome', off=0.006, bezel='chrome')],
              rects=[bar(0.2, 0.645, 0.83, 0.735), bar(0.26, 0.665, 0.8, 0.715, 'brake', 'brake_core_r', 0.016),
                     bar(0.06, 0.645, 0.17, 0.735, 'reverse', 'rev_core_r', 0.012)],
              garnish=dict(outline=rr(-0.2, 0.55, 0.2, 0.6), text_y=0.575, bg=(30, 30, 32), th=0.03)),
    badge='CHALLENGER', plate_f=0.31, plate_r=0.55, exhaust=(-0.52, 0.28, -0.06), stock_exhaust='dual', spoiler=(0.05, 0.78, 'none'),
    stock_wheel='star', beam=(0.68, 0.64, L - 0.05),
    cabin=dict(driver_x=0.37, rear=True, cluster='classic', screen=False, shifter='auto', seat='flat'),
    maxSpeed=215, accel=5.4, brake=7.0, mass=1600, fuel_l=68, spawnWeight=4, engine='v8_sport', horn='low', backfire=0.15,
    colors=['#f26b1d', '#6a2a8a', '#f4d03f', '#1b1c1e', '#2f8f3a', '#c8c9c4'],
)

# modern-style lamps (from the reference photos)
for h in SPEC['head']:
    x, y, r, k = h['lamps'][0]
    ring = ngon(r + 0.006, 14, x, y)
    h['strips'] = [(ring + ring[:1], 0.008, 'drl')]

# real performance: 0-100 km/h in 5.8 s, top speed 225 km/h
SPEC['accel'] = 5.68
SPEC['maxSpeed'] = 225
