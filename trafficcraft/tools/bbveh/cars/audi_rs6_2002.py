"""Audi RS6 Avant (C5, 2002). Long swept headlamps with twin projectors and a clear indicator strip, chrome-framed honeycomb RS grille
and big honeycomb side intakes; wide horizontal tail lamps wrapping onto the rear wings with round brake elements, estate roof line,
twin oval tips."""
from _h import *
L = 4.85
SPEC = dict(
    id='audi_rs6_2002', name='Audi RS6 Avant 2002', make='Audi', model='RS6 Avant (C5)', year=2002, category='wagon', category_name='Performance estate',
    description='Twin-turbo V8, quattro all-wheel drive and a big estate body: the family car that hunts supercars.',
    L=L, W=1.85, H=1.42, YR=1.14, YF=3.9, R=0.335, TRACK=0.79, Rr=0.229, TW=0.255,
    bot=[(0, 0.36), (0.15, 0.28), (0.6, 0.24), (1.5, 0.215), (3.4, 0.215), (4.4, 0.23), (L, 0.28)],
    belt=[(0, 0.9), (0.06, 0.95), (0.4, 0.96), (1.4, 0.95), (2.5, 0.93), (3.4, 0.91), (4.0, 0.86), (4.5, 0.8), (L, 0.74)],
    hw=[(0, 0.84), (0.08, 0.88), (0.6, 0.915), (1.2, 0.925), (2.4, 0.91), (3.5, 0.925), (4.2, 0.9), (4.6, 0.85), (L, 0.78)],
    zsh=[(0, 0.64), (2.0, 0.6), (L, 0.56)],
    CABIN0=0.06, CABIN1=3.5,
    roofz=[(0.06, 0.95), (0.12, 1.25), (0.3, 1.39), (0.6, 1.41), (2.0, 1.42), (2.8, 1.41), (3.05, 1.3), (3.3, 1.1), (3.5, 0.9)],
    roofw=[(0.06, 0.76), (0.3, 0.68), (2.0, 0.66), (2.9, 0.68), (3.5, 0.76)],
    crown=[(0, 0.012), (1.0, 0.014), (3.5, 0.025), (4.3, 0.03), (L, 0.01)],
    ws=(2.95, 3.45), rear_glass=(0.07, 0.42), windows=[(0.45, 1.25), (1.32, 2.32), (2.38, 3.5)], bpillars=[(1.25, 1.32), (2.32, 2.38)],
    doors=[('r', 1.5, 2.35), ('f', 2.35, 3.45)], roof_black=False,
    FB_Z=L - 0.32, RB_Z=0.35, hood=(3.52, L - 0.1, 0.7), trunk_y=0.85, fuel=('r', 1.05, 0.92), rb_top=3,
    head=[dict(view='T', outline=[(0.4, 4.79), (0.62, 4.75), (0.8, 4.66), (0.86, 4.52), (0.83, 4.45), (0.66, 4.55), (0.42, 4.66)],
               lamps=[(0.52, 4.71, 0.042, 'head'), (0.68, 4.64, 0.042, 'high')], strips=[([(0.72, 4.62), (0.82, 4.53)], 0.016, 'ind')])],
    front_lamps=[dict(outline=ngon(0.035, 12, 0.66, 0.36), kind='fog', style='fog_lens')],
    mouth=[dict(w=0.62, h=0.17, r=0.03, cx=0.0, cy=0.6, pattern='honeycomb', frame=(200, 204, 210)),
           dict(w=0.28, h=0.14, r=0.04, cx=0.64, cy=0.38, pattern='honeycomb', mirror=True), dict(w=0.6, h=0.08, r=0.03, cx=0.0, cy=0.36, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.44, 0.8, 0.88, 0.92, 0.02), style='tail_house', off=0.012)],
              rounds=[(0.56, 0.86, 0.035, 'tail', 'brake', 0.55), (0.72, 0.86, 0.035, 'tail', 'brake', 0.55)],
              rects=[bar(0.46, 0.82, 0.5, 0.9, 'ind', 'amber', 0.016), bar(0.8, 0.83, 0.86, 0.89, 'reverse', 'rev_core_r', 0.016)],
              garnish=dict(outline=rr(-0.12, 0.78, 0.12, 0.82), text_y=0.8, bg=(200, 204, 210), fg=(30, 30, 32), th=0.03), round_off=0.026),
    badge='RS 6', plate_f=0.34, plate_r=0.6, exhaust=(-0.5, 0.3, -0.05), stock_exhaust='dual', spoiler=(0.07, 0.62, 'none'),
    stock_wheel='spoke6', caliper=(30, 30, 34), beam=(0.6, 0.66, 4.7),
    cabin=dict(driver_x=0.35, rear=True, rear_gap=0.85, bench=True, cluster='analog2', screen=False, shifter='auto', seat='sport'),
    maxSpeed=280, accel=6.2, brake=10.0, mass=1840, fuel_l=82, spawnWeight=4, engine='v8_sport', horn='high', backfire=0.08,
    colors=['#7d8187', '#1b1c1e', '#e4e6e8', '#2b3f5c', '#a40f16', '#4f5d52'],
)

# modern-style lamps (from the reference photos)
SPEC['head'][0]['strips'].append(([(0.42, 4.68), (0.62, 4.71), (0.8, 4.62)], 0.012, 'drl'))

SPEC['grip'] = 1.0

# real performance: 0-100 km/h in 4.7 s, top speed 250 km/h
SPEC['accel'] = 6.92
SPEC['maxSpeed'] = 250
