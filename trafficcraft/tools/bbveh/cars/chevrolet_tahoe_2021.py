"""Chevrolet Tahoe 2021 (2021). Three-row American SUV with a small-block V8 and split headlamps.
Body from real dimensions (L 5.352, W 2.058, H 1.927, wheelbase 3.071); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.352
SPEC = dict(
    id='chevrolet_tahoe_2021', name='Chevrolet Tahoe 2021', make='Chevrolet', model='Tahoe (GMT1YC)', year=2021, category='suv', category_name='Full-size SUV',
    description="Three-row American SUV with a small-block V8 and split headlamps.",
    **body('suv', L, 2.058, 1.927, 3.071, 1.060, R=0.390, clear=0.220, doors=4),
    Rr=0.265, TW=0.255,
    head=[big(slim(0.494, 0.867, 0.947, 0.896, rise=0.000, proj=2, drl='above', shape='angular'), sy=1.60, dir=(-0.3, -0.35, -1.0))],
    mouth=[dict(w=1.152, h=0.385, r=0.02, cx=0.0, cy=0.694, pattern='honeycomb', frame=(30, 30, 32), grow=0), dict(w=1.152, h=0.035, r=0.01, cx=0.0, cy=0.732, pattern='none', col=(190, 190, 195), grow=0)],
    tail=dict(housings=[dict(outline=rr(0.792, 0.597, 0.936, 0.982, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.812, 0.962), (0.916, 0.962), (0.916, 0.627)], 0.014, 'tail', 0.018)],
              rects=[bar(0.812, 0.809, 0.906, 0.905, 'brake', 'brake_core_r', 0.018), bar(0.812, 0.712, 0.906, 0.789, 'ind', 'amber', 0.018),
                     bar(0.812, 0.607, 0.906, 0.693, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.902, 0.3, 0.952), text_y=0.927, bg=(30, 30, 32), th=0.03)),
    badge="TAHOE", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.74, 'none'),
    stock_wheel='spoke6', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.9, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.25, 0.15), screen_mount='floating'),
    maxSpeed=180, accel=4.41, brake=9.0, mass=2550, fuel_l=80, spawnWeight=5, engine='v8_sport', horn='low', backfire=0.00,
    colors=["#1b1c1e", "#f2f2f0", "#8a8f95", "#6e1a1e", "#1f3b5a", "#3a3d40"],
)
SPEC['grip'] = 0.92
# real performance: 0-100 km/h in 7.6 s, top speed 180 km/h
