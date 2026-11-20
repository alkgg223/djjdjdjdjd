"""Dodge Challenger 2021 (2021). Retro-styled two-door muscle coupe with quad headlamps and a full-width tail bar.
Body from real dimensions (L 5.027, W 1.923, H 1.450, wheelbase 2.946); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.027
SPEC = dict(
    id='dodge_challenger_2021', name='Dodge Challenger 2021', make='Dodge', model='Challenger R/T Scat Pack', year=2021, category='muscle', category_name='Muscle coupe',
    description="Retro-styled two-door muscle coupe with quad headlamps and a full-width tail bar.",
    **body('coupe', L, 1.923, 1.450, 2.946, 1.030, R=0.360, clear=0.140, doors=2),
    Rr=0.245, TW=0.250,
    head=[big(slim(0.462, 0.623, 0.885, 0.667, rise=0.000, proj=2, drl='below', shape='angular'), sy=1.60, dir=(-0.25, -0.35, -1.0))],
    mouth=[dict(w=1.731, h=0.174, r=0.02, cx=0.0, cy=0.594, pattern='honeycomb', frame=(20, 20, 22), grow=0), dict(w=1.077, h=0.102, r=0.03, cx=0.0, cy=0.348, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(-0.827, 0.676, 0.827, 0.777, 0.02), style='smoke_red', off=0.012)],
              strips=[([(-0.797, 0.727), (0.797, 0.727)], 0.020, 'tail', 0.018)],
              rects=[bar(0.527, 0.681, 0.707, 0.772, 'brake', 'brake_core_r', 0.02), bar(0.717, 0.681, 0.797, 0.772, 'ind', 'amber', 0.02),
                     bar(0.120, 0.681, 0.240, 0.772, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.22, 0.586, 0.22, 0.636), text_y=0.611, bg=(30, 30, 32), th=0.03)),
    badge="CHALLENGER", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.69, 'ducktail'),
    stock_wheel='spoke5', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog3', screen=True, shifter='manual', seat='sport', screen_size=(0.19, 0.11), screen_mount='stack'),
    maxSpeed=280, accel=7.37, brake=9.0, mass=1900, fuel_l=60, spawnWeight=3, engine='v8_sport', horn='high', backfire=0.20,
    colors=["#e3b23c", "#c8102e", "#1b1c1e", "#f2f2f0", "#2f6f3a", "#6a2a8a"],
)
SPEC['grip'] = 1.02
# real performance: 0-100 km/h in 4.4 s, top speed 280 km/h
