"""Mercedes E-Class 2021 (2021). Executive saloon with a twin-bar grille and a wide screen cockpit.
Body from real dimensions (L 4.935, W 1.852, H 1.468, wheelbase 2.939); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.935
SPEC = dict(
    id='mercedes_e_class_2021', name='Mercedes E-Class 2021', make='Mercedes-Benz', model='E 300 (W213)', year=2021, category='sedan', category_name='Executive sedan',
    description="Executive saloon with a twin-bar grille and a wide screen cockpit.",
    **body('sedan', L, 1.852, 1.468, 2.939, 0.880, R=0.340, clear=0.150, doors=4),
    Rr=0.231, TW=0.255,
    head=[big(slim(0.370, 0.617, 0.833, 0.668, rise=0.020, proj=3, drl='above', shape='swept'), sy=1.90, dir=(-0.3, -0.5, -1.0))],
    mouth=[dict(w=0.778, h=0.176, r=0.02, cx=0.0, cy=0.558, pattern='bars', pitch=0.035, frame=(190, 190, 195), grow=0), dict(w=0.259, h=0.117, r=0.02, cx=0.667, cy=0.352, pattern='honeycomb', mirror=True)],
    tail=dict(housings=[dict(outline=[(0.370, 0.787), (0.746, 0.797), (0.796, 0.773), (0.786, 0.704), (0.716, 0.704), (0.370, 0.714)], style='smoke_red', off=0.012)],
              strips=[([(0.390, 0.775), (0.756, 0.775)], 0.012, 'tail', 0.018)],
              rects=[bar(0.596, 0.719, 0.746, 0.754, 'brake', 'brake_core_r', 0.02), bar(0.410, 0.719, 0.490, 0.751, 'ind', 'amber', 0.02),
                     bar(0.510, 0.719, 0.590, 0.751, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.614, 0.18, 0.664), text_y=0.639, bg=(30, 30, 32), th=0.03)),
    badge="E 300", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='quad', spoiler=(0.05, 0.67, 'none'),
    stock_wheel='spoke5', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.6, 0.12), screen_mount='floating'),
    maxSpeed=250, accel=5.33, brake=9.0, mass=1680, fuel_l=60, spawnWeight=7, engine='i4_turbo', horn='high', backfire=0.00,
    colors=["#1b1c1e", "#f2f2f0", "#8a8f95", "#1f3b5a", "#6e1a1e", "#c9b48a"],
)
SPEC['grip'] = 0.96
# real performance: 0-100 km/h in 6.2 s, top speed 250 km/h
