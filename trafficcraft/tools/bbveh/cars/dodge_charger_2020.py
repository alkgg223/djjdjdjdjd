"""Dodge Charger 2020 (2020). Four-door muscle car with a racetrack tail lamp and a supercharged HEMI.
Body from real dimensions (L 5.100, W 1.905, H 1.480, wheelbase 3.050); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.100
SPEC = dict(
    id='dodge_charger_2020', name='Dodge Charger 2020', make='Dodge', model='Charger SRT', year=2020, category='muscle', category_name='Muscle sedan',
    description="Four-door muscle car with a racetrack tail lamp and a supercharged HEMI.",
    **body('sedan', L, 1.905, 1.480, 3.050, 1.000, R=0.360, clear=0.140, doors=4),
    Rr=0.245, TW=0.255,
    head=[big(slim(0.419, 0.636, 0.876, 0.681, rise=0.000, proj=2, drl='above', shape='angular'), sy=1.70, dir=(-0.3, -0.45, -1.0))],
    mouth=[dict(w=1.562, h=0.118, r=0.02, cx=0.0, cy=0.622, pattern='honeycomb', frame=(20, 20, 22), grow=0), dict(w=1.143, h=0.118, r=0.03, cx=0.0, cy=0.370, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(-0.819, 0.720, 0.819, 0.794, 0.02), style='smoke_red', off=0.012)],
              strips=[([(-0.789, 0.757), (0.789, 0.757)], 0.020, 'tail', 0.018)],
              rects=[bar(0.519, 0.725, 0.699, 0.789, 'brake', 'brake_core_r', 0.02), bar(0.709, 0.725, 0.789, 0.789, 'ind', 'amber', 0.02),
                     bar(0.120, 0.725, 0.240, 0.789, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.22, 0.630, 0.22, 0.680), text_y=0.655, bg=(30, 30, 32), th=0.03)),
    badge="CHARGER", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.69, 'ducktail'),
    stock_wheel='spoke5', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.21, 0.12), screen_mount='stack'),
    maxSpeed=290, accel=8.70, brake=9.0, mass=2000, fuel_l=60, spawnWeight=7, engine='v8_sport', horn='high', backfire=0.12,
    colors=["#1b1c1e", "#c8102e", "#f2f2f0", "#3a3d40", "#e3b23c", "#2f6f3a"],
)
SPEC['grip'] = 0.96
# real performance: 0-100 km/h in 3.7 s, top speed 290 km/h
