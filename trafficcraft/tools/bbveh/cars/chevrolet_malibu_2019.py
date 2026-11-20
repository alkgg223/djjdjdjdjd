"""Chevrolet Malibu 2019 (2019). Midsize sedan with a split grille and wrap-around tail lamps.
Body from real dimensions (L 4.933, W 1.854, H 1.461, wheelbase 2.829); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.933
SPEC = dict(
    id='chevrolet_malibu_2019', name='Chevrolet Malibu 2019', make='Chevrolet', model='Malibu', year=2019, category='sedan', category_name='Midsize sedan',
    description="Midsize sedan with a split grille and wrap-around tail lamps.",
    **body('sedan', L, 1.854, 1.461, 2.829, 1.000, R=0.340, clear=0.150, doors=4),
    Rr=0.231, TW=0.255,
    head=[big(slim(0.408, 0.614, 0.834, 0.657, rise=0.020, proj=2, drl='below', shape='swept'), sy=1.90, dir=(-0.3, -0.5, -1.0))],
    mouth=[dict(w=0.853, h=0.073, r=0.02, cx=0.0, cy=0.599, pattern='bars', pitch=0.025, frame=(190, 190, 195), grow=0), dict(w=1.038, h=0.161, r=0.03, cx=0.0, cy=0.394, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=[(0.334, 0.784), (0.747, 0.804), (0.797, 0.769), (0.787, 0.691), (0.717, 0.691), (0.334, 0.711)], style='smoke_red', off=0.012)],
              strips=[([(0.354, 0.772), (0.757, 0.772)], 0.012, 'tail', 0.018)],
              rects=[bar(0.597, 0.716, 0.747, 0.751, 'brake', 'brake_core_r', 0.02), bar(0.374, 0.716, 0.454, 0.747, 'ind', 'amber', 0.02),
                     bar(0.474, 0.716, 0.554, 0.747, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.611, 0.18, 0.661), text_y=0.636, bg=(30, 30, 32), th=0.03)),
    badge="MALIBU", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.67, 'none'),
    stock_wheel='turbine', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.2, 0.11), screen_mount='stack'),
    maxSpeed=210, accel=4.21, brake=9.0, mass=1450, fuel_l=60, spawnWeight=7, engine='i4_turbo', horn='high', backfire=0.00,
    colors=["#f2f2f0", "#1b1c1e", "#8a8f95", "#6e1a1e", "#1f3b7a", "#3a3d40"],
)
SPEC['grip'] = 0.96
# real performance: 0-100 km/h in 8.0 s, top speed 210 km/h
