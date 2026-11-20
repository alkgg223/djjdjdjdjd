"""Nissan Skyline GT-R R34 (1999). Legendary all-wheel-drive twin-turbo coupe with four round tail lamps.
Body from real dimensions (L 4.600, W 1.785, H 1.360, wheelbase 2.665); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.600
SPEC = dict(
    id='nissan_skyline_r34_1999', name='Nissan Skyline GT-R R34', make='Nissan', model='Skyline GT-R (R34)', year=1999, category='sports', category_name='Sports coupe',
    description="Legendary all-wheel-drive twin-turbo coupe with four round tail lamps.",
    **body('coupe', L, 1.785, 1.360, 2.665, 0.980, R=0.330, clear=0.130, doors=2),
    Rr=0.224, TW=0.250,
    head=[big(slim(0.393, 0.571, 0.803, 0.619, rise=0.000, proj=2, drl='below', shape='angular'), sy=1.70, dir=(-0.3, -0.45, -1.0))],
    mouth=[dict(w=0.607, h=0.068, r=0.02, cx=0.0, cy=0.571, pattern='bars', pitch=0.03, frame=(20, 20, 22), grow=0), dict(w=1.178, h=0.150, r=0.03, cx=0.0, cy=0.354, pattern='honeycomb')],
    tail=dict(rounds=[(0.670, 0.650, 0.068, 'tail', 'brake', 0.45), (0.504, 0.650, 0.068, 'tail', 'brake', 0.45)], round_off=0.022,
              rects=[bar(0.100, 0.530, 0.200, 0.560, 'reverse', 'rev_core_r', 0.01), bar(0.648, 0.532, 0.728, 0.562, 'ind', 'amber', 0.01)],
              garnish=dict(outline=rr(-0.2, 0.620, 0.2, 0.670), text_y=0.645, bg=(30, 30, 32), th=0.03)),
    badge="GT-R", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='big', spoiler=(0.05, 0.64, 'gt_wing'),
    stock_wheel='spoke6', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog3', screen=False, shifter='manual', seat='sport', screen_size=(0.13, 0.07), screen_mount='stack'),
    maxSpeed=250, accel=6.65, brake=11.0, mass=1560, fuel_l=60, spawnWeight=3, engine='i6_twinturbo', horn='high', backfire=0.20,
    colors=["#1d4f9c", "#f2f2f0", "#1b1c1e", "#8a8f95", "#c8102e", "#e3b23c"],
)
SPEC['grip'] = 1.02
# real performance: 0-100 km/h in 4.9 s, top speed 250 km/h
