"""Kia Stinger (CK, 2017). 'Tiger-nose' grille pinched in the middle with a chrome surround, slim LED headlamps with a light-strip
eyebrow, tall vertical side intakes with LED fog / indicator; long thin tail lamps running from the boot round onto the rear wings
with an LED outline, quad round tips, fastback five-door."""
from _h import *
L = 4.83


def tiger(cx, cy, w, h):
    return [(-w / 2, cy + h / 2), (-w * 0.12, cy + h * 0.38), (w * 0.12, cy + h * 0.38), (w / 2, cy + h / 2), (w * 0.47, cy - h / 2),
            (-w * 0.47, cy - h / 2)][::-1]


SPEC = dict(
    id='kia_stinger_2017', name='Kia Stinger 2017', make='Kia', model='Stinger GT', year=2017, category='sports', category_name='Gran turismo',
    description='Rear-drive fastback gran turismo with a twin-turbo V6 and a long, low silhouette.',
    L=L, W=1.87, H=1.4, YR=0.98, YF=3.885, R=0.34, TRACK=0.8, Rr=0.241, TW=0.245,
    bot=[(0, 0.36), (0.15, 0.28), (0.6, 0.24), (1.5, 0.215), (3.4, 0.215), (4.4, 0.23), (L, 0.28)],
    belt=[(0, 0.88), (0.06, 0.93), (0.4, 0.96), (1.2, 0.95), (2.4, 0.92), (3.4, 0.89), (3.9, 0.84), (4.4, 0.76), (L, 0.68)],
    hw=[(0, 0.82), (0.1, 0.88), (0.6, 0.925), (1.1, 0.935), (2.4, 0.92), (3.6, 0.93), (4.3, 0.9), (4.65, 0.83), (L, 0.72)],
    zsh=[(0, 0.64), (2.0, 0.6), (L, 0.54)],
    CABIN0=0.5, CABIN1=3.45,
    roofz=[(0.5, 0.96), (0.7, 1.08), (1.1, 1.26), (1.5, 1.38), (2.1, 1.4), (2.7, 1.39), (3.0, 1.28), (3.25, 1.08), (3.45, 0.9)],
    roofw=[(0.5, 0.76), (1.1, 0.68), (2.1, 0.64), (2.8, 0.66), (3.45, 0.76)],
    crown=[(0, 0.015), (1.0, 0.02), (3.5, 0.03), (4.3, 0.035), (L, 0.01)],
    ws=(2.95, 3.4), rear_glass=(0.55, 1.45), windows=[(1.3, 2.2), (2.27, 3.45)], bpillars=[(2.2, 2.27)], doors=[('r', 1.45, 2.25), ('f', 2.25, 3.4)],
    FB_Z=L - 0.32, RB_Z=0.36, hood=(3.47, L - 0.12, 0.7), hatch_x=0.7, trunk_y=0.82, fuel=('l', 0.85, 0.9), handle_rear=False,
    head=[dict(view='T', outline=[(0.46, 4.75), (0.64, 4.7), (0.8, 4.6), (0.88, 4.45), (0.86, 4.38), (0.76, 4.48), (0.6, 4.6), (0.44, 4.66)],
               lamps=[(0.56, 4.68, 0.035, 'head'), (0.7, 4.6, 0.035, 'high')],
               strips=[([(0.47, 4.73), (0.64, 4.67), (0.79, 4.57), (0.86, 4.44)], 0.012, 'drl'), ([(0.78, 4.54), (0.85, 4.43)], 0.02, 'ind')])],
    front_lamps=[dict(outline=rr(0.66, 0.3, 0.74, 0.46, 0.01), kind='fog', style='fog_lens')],
    mouth=[dict(shape='poly', poly=lambda cx, cy, w, h: tiger(cx, cy, w, h), w=0.66, h=0.16, cx=0.0, cy=0.58, pattern='honeycomb', grow=0.5),
           dict(w=0.16, h=0.2, r=0.02, cx=0.66, cy=0.38, pattern='honeycomb', mirror=True), dict(w=0.7, h=0.07, r=0.03, cx=0.0, cy=0.33, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=[(0.18, 0.86), (0.8, 0.86), (0.9, 0.84), (0.92, 0.8), (0.8, 0.81), (0.18, 0.83)], style='smoke_red', off=0.012)],
              strips=[([(0.2, 0.855), (0.8, 0.852), (0.89, 0.835), (0.91, 0.805)], 0.012, 'tail', 0.016)],
              rects=[bar(0.62, 0.82, 0.78, 0.845, 'brake', 'brake_core_r', 0.018), bar(0.3, 0.82, 0.42, 0.845, 'reverse', 'rev_core_r', 0.018),
                     bar(0.46, 0.82, 0.58, 0.845, 'ind', 'amber', 0.018)],
              garnish=dict(outline=rr(-0.15, 0.7, 0.15, 0.75), text_y=0.725, bg=(30, 30, 32), th=0.03)),
    badge='STINGER', plate_f=0.36, plate_r=0.58, exhaust=(-0.52, 0.3, -0.04), stock_exhaust='quad', spoiler=(0.06, 0.66, 'ducktail'),
    stock_wheel='turbine', caliper=(200, 30, 30), beam=(0.62, 0.7, 4.65),
    cabin=dict(driver_x=0.35, rear=True, rear_gap=0.82, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport'),
    maxSpeed=270, accel=6.6, brake=10.0, mass=1800, fuel_l=60, spawnWeight=5, engine='v6_cruiser', horn='high', backfire=0.1,
    colors=['#c8102e', '#e9edf0', '#1b1c1e', '#6c7178', '#1d3f7a', '#9aa3ad'],
)

SPEC['grip'] = 0.97

# real infotainment screen size / mounting
SPEC['cabin'].update(dict(screen_size=(0.18, 0.1), screen_mount='floating', screen_x=0.03))

# real performance: 0-100 km/h in 4.9 s, top speed 270 km/h
SPEC['accel'] = 6.65
SPEC['maxSpeed'] = 270
