"""Hyundai Elantra (J1, 1990). Rectangular headlamps with amber wrap-around corner lamps, a slim chrome-framed bar grille; wide ribbed
rectangular tail lamps with the reversing lamps on the inner edge, joined by a red centre garnish carrying the name."""
from _h import *
L = 4.37
SPEC = dict(
    id='hyundai_elantra_1990', name='Hyundai Elantra 1990', make='Hyundai', model='Elantra (J1)', year=1990, category='sedan', category_name='Saloon',
    description='Hyundai\'s first compact saloon sold worldwide: plain, roomy and good value.',
    L=L, W=1.675, H=1.38, YR=0.95, YF=3.45, R=0.29, TRACK=0.72, Rr=0.178, TW=0.185, tumble=0.045,
    bot=[(0, 0.34), (0.12, 0.27), (0.5, 0.23), (1.3, 0.21), (3.0, 0.21), (3.9, 0.225), (L, 0.28)],
    belt=[(0, 0.87), (0.06, 0.91), (0.5, 0.92), (1.3, 0.91), (2.3, 0.89), (3.0, 0.87), (3.6, 0.82), (4.0, 0.77), (L, 0.72)],
    hw=[(0, 0.8), (0.06, 0.83), (0.5, 0.835), (1.3, 0.837), (2.6, 0.835), (3.5, 0.837), (4.0, 0.82), (L, 0.77)],
    zsh=[(0, 0.62), (L, 0.58)],
    CABIN0=0.95, CABIN1=3.1,
    roofz=[(0.95, 0.92), (1.1, 1.08), (1.4, 1.33), (1.7, 1.375), (2.4, 1.38), (2.65, 1.35), (2.85, 1.18), (3.0, 1.03), (3.1, 0.87)],
    roofw=[(0.95, 0.7), (1.4, 0.63), (2.4, 0.61), (2.85, 0.63), (3.1, 0.7)],
    crown=[(0, 0.012), (3.0, 0.02), (L, 0.012)],
    ws=(2.75, 3.06), rear_glass=(1.0, 1.42), windows=[(1.44, 2.02), (2.08, 3.1)], bpillars=[(2.02, 2.08)], doors=[('r', 1.4, 2.05), ('f', 2.05, 3.06)],
    FB_Z=L - 0.28, RB_Z=0.3, hood=(3.12, L - 0.08, 0.64), trunk_z=0.95, trunk_y=0.8, fuel=('r', 0.6, 0.85),
    head=[rect_head(0.3, 0.6, 0.74, 0.69, lamps=[(0.42, 0.645, 0.035, 'head'), (0.56, 0.645, 0.035, 'high')], rects=[(0.66, 0.61, 0.74, 0.68, 'ind')],
                    bezel='trim')],
    mouth=[dict(w=0.5, h=0.06, r=0.015, cx=0.0, cy=0.645, pattern='bars', pitch=0.018, frame=(200, 204, 210)),
           dict(w=0.7, h=0.07, r=0.02, cx=0.0, cy=0.36, pattern='bars', pitch=0.02)],
    tail=dict(housings=[dict(outline=rr(0.34, 0.68, 0.8, 0.8, 0.01), style='tail_house', off=0.01)],
              rects=[bar(0.48, 0.7, 0.78, 0.79), bar(0.36, 0.7, 0.47, 0.79, 'reverse', 'rev_core_r', 0.012), bar(0.66, 0.705, 0.77, 0.735, 'ind', 'amber', 0.016),
                     bar(0.5, 0.745, 0.76, 0.78, 'brake', 'brake_core_r', 0.016)],
              garnish=dict(outline=rr(-0.32, 0.7, 0.32, 0.79), text_y=0.745, bg=(120, 14, 22), fg=(220, 220, 224), th=0.03)),
    badge='ELANTRA', plate_f=0.36, plate_r=0.6, exhaust=(-0.45, 0.28, -0.04), stock_exhaust='oval', spoiler=(0.05, 0.66, 'none'),
    stock_wheel='aero', beam=(0.5, 0.645, L - 0.04),
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog2', screen=False, shifter='auto', seat='flat'),
    maxSpeed=175, accel=3.4, brake=7.2, mass=1100, fuel_l=50, spawnWeight=7, engine='i4_petrol', horn='car',
    colors=['#e8e4d8', '#8a1c22', '#3d5a7a', '#9aa2a8', '#2f4f3a'],
)

# modern-style lamps (from the reference photos)
SPEC['head'] = [slim(0.32, 0.6, 0.74, 0.65, rise=0.025, shape='angular')]
SPEC['mouth'][1].update(w=1.2, h=0.16, cy=0.45, pattern='honeycomb')

# model-specific lamp design
SPEC['head'] = [dict(view='F', outline=[(0.32, 0.6), (0.5, 0.64), (0.74, 0.67), (0.74, 0.64), (0.6, 0.61), (0.42, 0.59)], lamps=[(0.56, 0.632, 0.018, 'head'), (0.65, 0.645, 0.018, 'high')], strips=[([(0.34, 0.602), (0.5, 0.637), (0.73, 0.665), (0.6, 0.615)], 0.009, 'drl')], rects=[(0.69, 0.645, 0.735, 0.66, 'ind')], smooth=0, house='head_house_black', bezel='trim')]

# real-size lamp wrapping over the nose corner
SPEC['head'] = [big(SPEC['head'][0])]

# lamp size pass
SPEC['tail_scale'] = 1.22

# real performance: 0-100 km/h in 11.5 s, top speed 175 km/h
SPEC['accel'] = 3.04
SPEC['maxSpeed'] = 175
