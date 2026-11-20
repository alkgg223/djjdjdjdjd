"""Honda Civic (1st gen, 1972) 3-door. Round headlamps set into a black horizontal-bar grille that spans the whole small front,
amber indicators below in the chrome bumper line; small vertical tail lamps (red over amber) beside the hatch."""
from _h import *
L = 3.405
SPEC = dict(
    id='honda_civic_1972', name='Honda Civic 1972', make='Honda', model='Civic (1st gen)', year=1972, category='compact', category_name='Hatchback',
    description='Small, frugal front-wheel-drive hatchback that made Honda a global car maker.',
    L=L, W=1.505, H=1.325, YR=0.6, YF=2.8, R=0.27, TRACK=0.62, Rr=0.152, TW=0.155, tumble=0.04,
    bot=[(0, 0.32), (0.12, 0.26), (0.4, 0.22), (1.0, 0.2), (2.4, 0.2), (3.1, 0.22), (L, 0.28)],
    belt=[(0, 0.82), (0.06, 0.85), (0.4, 0.86), (1.2, 0.84), (2.0, 0.82), (2.6, 0.78), (3.0, 0.74), (L, 0.7)],
    hw=[(0, 0.68), (0.08, 0.72), (0.4, 0.74), (1.4, 0.745), (2.6, 0.745), (3.1, 0.73), (L, 0.7)],
    zsh=[(0, 0.58), (L, 0.55)],
    CABIN0=0.05, CABIN1=2.4,
    roofz=[(0.05, 0.86), (0.1, 1.15), (0.35, 1.31), (1.0, 1.325), (1.7, 1.32), (1.95, 1.24), (2.2, 1.0), (2.4, 0.82)],
    roofw=[(0.05, 0.6), (0.35, 0.56), (1.6, 0.55), (2.4, 0.6)],
    crown=[(0, 0.01), (2.4, 0.015), (L, 0.01)],
    ws=(1.95, 2.38), rear_glass=(0.08, 0.38), windows=[(0.4, 1.0), (1.05, 2.4)], bpillars=[(1.0, 1.05)], doors=[('f', 1.05, 2.36)],
    FB_Z=L - 0.22, RB_Z=0.22, hood=(2.42, L - 0.06, 0.6), hatch_x=0.56, fuel=('r', 0.55, 0.78),
    chrome_bumpers=((0.3, 0.38), (0.3, 0.38)), chrome_handles=True, mirror_chrome=True, mirror_round=True,
    head=[round_head(0.53, 0.6, 0.07, bezel='chrome')],
    front_lamps=[dict(outline=rr(0.5, 0.42, 0.62, 0.45), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=1.36, h=0.15, r=0.01, cx=0.0, cy=0.6, pattern='bars', pitch=0.025, col=(22, 22, 24), hole=(46, 46, 50), frame=(200, 204, 210))],
    tail=dict(housings=[dict(outline=rr(0.5, 0.5, 0.64, 0.74, 0.012), style='chrome', off=0.008, bezel='chrome')],
              rects=[bar(0.515, 0.62, 0.625, 0.73), bar(0.515, 0.52, 0.625, 0.6, 'ind', 'amber', 0.012), bar(0.53, 0.64, 0.61, 0.71, 'brake', 'brake_core_r', 0.016),
                     bar(0.1, 0.36, 0.18, 0.4, 'reverse', 'rev_core_r', 0.006)]),
    badge='', plate_f=0.36, plate_r=0.52, exhaust=(-0.4, 0.27, -0.04), stock_exhaust='oval', spoiler=(0.05, 0.55, 'none'),
    stock_wheel='steel', beam=(0.53, 0.6, L - 0.04),
    cabin=dict(driver_x=0.28, rear=True, rear_gap=0.6, bench=True, cluster='classic', screen=False, shifter='manual', seat='flat'),
    maxSpeed=145, accel=2.8, brake=6.5, mass=700, fuel_l=38, spawnWeight=6, engine='i4_petrol', horn='car',
    colors=['#e3b23c', '#b5121b', '#e8e4d8', '#3d6b4a', '#7aa6c8', '#d86a17'],
)

# modern-style lamps (from the reference photos)
SPEC['head'] = [slim(0.3, 0.55, 0.66, 0.6, rise=0.03)]

# model-specific lamp design
SPEC['head'] = [dict(view='F', outline=[(0.3, 0.57), (0.66, 0.6), (0.7, 0.63), (0.66, 0.64), (0.34, 0.61)], lamps=[(0.48, 0.6, 0.016, 'head'), (0.57, 0.607, 0.016, 'high')], strips=[([(0.33, 0.605), (0.66, 0.633)], 0.01, 'drl')], rects=[(0.63, 0.6, 0.68, 0.625, 'ind')], smooth=0, house='head_house_black', bezel='trim')]
