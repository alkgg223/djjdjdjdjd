"""Toyota Yaris 2020 (2020). Compact city hatchback with a big lower grille.
Body from real dimensions (L 3.940, W 1.745, H 1.500, wheelbase 2.560); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 3.940
SPEC = dict(
    id='toyota_yaris_2020', name='Toyota Yaris 2020', make='Toyota', model='Yaris (XP210)', year=2020, category='hatch', category_name='Supermini',
    description="Compact city hatchback with a big lower grille.",
    **body('hatch', L, 1.745, 1.500, 2.560, 0.780, R=0.300, clear=0.140, doors=4),
    Rr=0.204, TW=0.255,
    head=[big(slim(0.384, 0.645, 0.785, 0.698, rise=0.030, proj=2, drl='below', shape='swept'), sy=1.80, dir=(-0.3, -0.5, -1.0))],
    mouth=[dict(w=0.873, h=0.210, r=0.05, cx=0.0, cy=0.405, pattern='honeycomb', frame=(20, 20, 22), grow=0), dict(w=0.349, h=0.030, r=0.01, cx=0.0, cy=0.630, pattern='none', col=(20, 20, 22), grow=0)],
    tail=dict(housings=[dict(outline=rr(0.628, 0.505, 0.750, 0.805, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.648, 0.785), (0.730, 0.785), (0.730, 0.535)], 0.014, 'tail', 0.018)],
              rects=[bar(0.648, 0.670, 0.720, 0.745, 'brake', 'brake_core_r', 0.018), bar(0.648, 0.595, 0.720, 0.655, 'ind', 'amber', 0.018),
                     bar(0.648, 0.515, 0.720, 0.580, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.725, 0.3, 0.775), text_y=0.750, bg=(30, 30, 32), th=0.03)),
    badge="YARIS", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='oval', spoiler=(0.05, 0.63, 'none'),
    stock_wheel='spoke5', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.34, rear=True, rear_gap=0.8, bench=True, cluster='analog2', screen=True, shifter='auto', seat='flat', screen_size=(0.18, 0.1), screen_mount='floating'),
    maxSpeed=175, accel=3.11, brake=9.0, mass=1050, fuel_l=60, spawnWeight=6, engine='i3_turbo', horn='high', backfire=0.00,
    colors=["#c8102e", "#f2f2f0", "#1b1c1e", "#8a8f95", "#1f5fbf", "#e3b23c"],
)
SPEC['grip'] = 0.94
# real performance: 0-100 km/h in 11.2 s, top speed 175 km/h
