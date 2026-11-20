"""Toyota Corolla (E10, 1966) 2-door saloon. Round headlamps in chrome rings either side of a slim chrome-barred grille with a centre
bar, small round amber indicators below; small rectangular tail lamps at the rear corners, notchback boot."""
from _h import *
L = 3.845
SPEC = dict(
    id='toyota_corolla_1966', name='Toyota Corolla 1966', make='Toyota', model='Corolla (E10)', year=1966, category='compact', category_name='Saloon',
    description='Simple, reliable small saloon - the start of the best-selling car name in history.',
    L=L, W=1.485, H=1.38, YR=0.78, YF=3.065, R=0.28, TRACK=0.62, Rr=0.152, TW=0.15, tumble=0.04,
    bot=[(0, 0.34), (0.12, 0.27), (0.5, 0.23), (1.2, 0.21), (2.6, 0.21), (3.5, 0.23), (L, 0.29)],
    belt=[(0, 0.82), (0.06, 0.86), (0.4, 0.87), (1.2, 0.86), (2.2, 0.85), (3.0, 0.83), (3.5, 0.8), (L, 0.76)],
    hw=[(0, 0.7), (0.08, 0.73), (0.5, 0.74), (1.5, 0.742), (3.0, 0.742), (3.6, 0.73), (L, 0.7)],
    zsh=[(0, 0.6), (L, 0.58)],
    CABIN0=0.85, CABIN1=2.75,
    roofz=[(0.85, 0.87), (1.0, 1.05), (1.25, 1.3), (1.5, 1.37), (2.0, 1.38), (2.25, 1.35), (2.45, 1.2), (2.6, 1.02), (2.75, 0.84)],
    roofw=[(0.85, 0.62), (1.3, 0.56), (2.0, 0.55), (2.4, 0.56), (2.75, 0.62)],
    crown=[(0, 0.012), (2.8, 0.02), (L, 0.012)],
    ws=(2.4, 2.72), rear_glass=(0.9, 1.3), windows=[(1.3, 1.75), (1.8, 2.75)], bpillars=[(1.75, 1.8)], doors=[('f', 1.62, 2.72)],
    FB_Z=L - 0.22, RB_Z=0.25, hood=(2.77, L - 0.06, 0.6), trunk_z=0.82, trunk_y=0.78, fuel=('r', 0.5, 0.8), belt_chrome=0.84,
    chrome_bumpers=((0.32, 0.4), (0.32, 0.4)), chrome_handles=True, mirror_chrome=True, mirror_round=True, a_black=False,
    head=[round_head(0.54, 0.62, 0.068, bezel='chrome')],
    front_lamps=[dict(outline=ngon(0.026, 12, 0.5, 0.46), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=0.72, h=0.13, r=0.02, cx=0.0, cy=0.62, pattern='bars', pitch=0.03, col=(214, 218, 222), hole=(20, 20, 22), frame=(226, 230, 234))],
    tail=dict(housings=[dict(outline=rr(0.5, 0.6, 0.66, 0.74, 0.012), style='chrome', off=0.008, bezel='chrome')],
              rects=[bar(0.515, 0.66, 0.645, 0.73), bar(0.515, 0.61, 0.645, 0.65, 'ind', 'amber', 0.012), bar(0.54, 0.675, 0.62, 0.715, 'brake', 'brake_core_r', 0.016),
                     bar(0.1, 0.36, 0.18, 0.4, 'reverse', 'rev_core_r', 0.006)]),
    badge='', plate_f=0.36, plate_r=0.56, exhaust=(-0.4, 0.28, -0.04), stock_exhaust='oval', spoiler=(0.05, 0.6, 'none'),
    stock_wheel='hubcap', beam=(0.54, 0.62, L - 0.04),
    cabin=dict(driver_x=0.29, rear=True, rear_gap=0.65, bench=True, cluster='classic', screen=False, shifter='manual', seat='flat'),
    maxSpeed=140, accel=2.6, brake=6.2, mass=730, fuel_l=36, spawnWeight=6, engine='i4_petrol', horn='car',
    colors=['#e8e4d8', '#9bbcd0', '#b5121b', '#3d6b4a', '#c9b98a'],
)

# modern-style lamps (from the reference photos)
SPEC['head'] = [slim(0.3, 0.6, 0.66, 0.66, rise=0.035)]

# model-specific lamp design
SPEC['head'] = [dict(view='F', outline=[(0.3, 0.6), (0.48, 0.66), (0.66, 0.69), (0.68, 0.66), (0.58, 0.62), (0.4, 0.595)], lamps=[(0.5, 0.635, 0.022, 'head'), (0.6, 0.655, 0.02, 'high')], strips=[([(0.33, 0.6), (0.42, 0.6), (0.58, 0.625), (0.67, 0.662)], 0.01, 'drl')], rects=[(0.62, 0.66, 0.67, 0.68, 'ind')], smooth=1, house='head_house_black', bezel='trim')]

# real-size lamp wrapping over the nose corner
SPEC['head'] = [big(SPEC['head'][0])]

# lamp size pass
SPEC['tail_scale'] = 1.22

# real performance: 0-100 km/h in 16.0 s, top speed 140 km/h
SPEC['accel'] = 2.28
SPEC['maxSpeed'] = 140
