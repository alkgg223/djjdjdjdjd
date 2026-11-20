"""Nissan Patrol 2020 (2020). Big V8 off-roader with V-motion grille and boomerang lamps, a favourite on Gulf roads.
Body from real dimensions (L 5.165, W 1.995, H 1.940, wheelbase 3.075); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.165
SPEC = dict(
    id='nissan_patrol_2020', name='Nissan Patrol 2020', make='Nissan', model='Patrol (Y62)', year=2020, category='suv', category_name='Large SUV',
    description="Big V8 off-roader with V-motion grille and boomerang lamps, a favourite on Gulf roads.",
    **body('suv', L, 1.995, 1.940, 3.075, 1.020, R=0.390, clear=0.250, doors=4),
    Rr=0.265, TW=0.255,
    head=[big(slim(0.399, 0.815, 0.918, 0.892, rise=0.025, proj=2, drl='below', shape='swept'), sy=2.00, dir=(-0.3, -0.35, -1.0))],
    mouth=[dict(w=0.918, h=0.330, r=0.04, cx=0.0, cy=0.737, pattern='bars', pitch=0.045, frame=(200, 200, 205), grow=0), dict(w=0.838, h=0.097, r=0.02, cx=0.0, cy=0.485, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.768, 0.601, 0.908, 0.989, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.788, 0.969), (0.888, 0.969), (0.888, 0.631)], 0.014, 'tail', 0.018)],
              rects=[bar(0.788, 0.814, 0.878, 0.911, 'brake', 'brake_core_r', 0.018), bar(0.788, 0.717, 0.878, 0.795, 'ind', 'amber', 0.018),
                     bar(0.788, 0.611, 0.878, 0.698, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.909, 0.3, 0.959), text_y=0.934, bg=(30, 30, 32), th=0.03)),
    badge="PATROL", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.72, 'none'),
    stock_wheel='spoke6', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.9, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.2, 0.11), screen_mount='stack'),
    maxSpeed=210, accel=5.03, brake=9.0, mass=2700, fuel_l=80, spawnWeight=5, engine='v8_sport', horn='low', backfire=0.00,
    colors=["#f2f2f0", "#1b1c1e", "#7d8288", "#5a3a2a", "#1f3b5a", "#b9bdc2"],
)
SPEC['grip'] = 0.92
# real performance: 0-100 km/h in 6.6 s, top speed 210 km/h
