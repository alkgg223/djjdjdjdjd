"""Porsche 911 (992) 2020 (2020). Rear-engine sports car with round lamps and a full-width light strip.
Body from real dimensions (L 4.519, W 1.852, H 1.300, wheelbase 2.450); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.519
SPEC = dict(
    id='porsche_911_992_2020', name='Porsche 911 (992) 2020', make='Porsche', model='911 Carrera S (992)', year=2020, category='sports', category_name='Sports car',
    description="Rear-engine sports car with round lamps and a full-width light strip.",
    **body('coupe', L, 1.852, 1.300, 2.450, 1.080, R=0.340, clear=0.120, doors=2),
    Rr=0.231, TW=0.250,
    head=[big(slim(0.556, 0.390, 0.815, 0.455, rise=0.000, proj=2, drl='above', shape='curve'), sy=1.60, dir=(-0.2, -0.7, -1.0))],
    mouth=[dict(w=0.333, h=0.104, r=0.02, cx=0.611, cy=0.260, pattern='bars', pitch=0.025, mirror=True, frame=(20, 20, 22), grow=0), dict(w=0.593, h=0.052, r=0.01, cx=0.0, cy=0.234, pattern='bars', pitch=0.025)],
    tail=dict(housings=[dict(outline=rr(-0.796, 0.663, 0.796, 0.695, 0.02), style='smoke_red', off=0.012)],
              strips=[([(-0.766, 0.679), (0.766, 0.679)], 0.016, 'tail', 0.018)],
              rects=[bar(0.496, 0.668, 0.676, 0.690, 'brake', 'brake_core_r', 0.02), bar(0.686, 0.668, 0.766, 0.690, 'ind', 'amber', 0.02),
                     bar(0.120, 0.668, 0.240, 0.690, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.22, 0.573, 0.22, 0.623), text_y=0.598, bg=(30, 30, 32), th=0.03)),
    badge="911", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.67, 'ducktail'),
    stock_wheel='spoke5', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog3', screen=True, shifter='manual', seat='sport', screen_size=(0.25, 0.1), screen_mount='stack'),
    maxSpeed=308, accel=8.70, brake=11.0, mass=1515, fuel_l=60, spawnWeight=3, engine='i6_twinturbo', horn='high', backfire=0.20,
    colors=["#f2f2f0", "#1b1c1e", "#c8102e", "#e3b23c", "#2f6f3a", "#8a8f95"],
)
SPEC['grip'] = 1.02
# real performance: 0-100 km/h in 3.7 s, top speed 308 km/h
