"""Toyota Camry 2023 (2023). Popular midsize sedan with a wide low grille and sharp LED lamps.
Body from real dimensions (L 4.885, W 1.840, H 1.445, wheelbase 2.825); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.885
SPEC = dict(
    id='toyota_camry_2023', name='Toyota Camry 2023', make='Toyota', model='Camry (XV70)', year=2023, category='sedan', category_name='Midsize sedan',
    description="Popular midsize sedan with a wide low grille and sharp LED lamps.",
    **body('sedan', L, 1.840, 1.445, 2.825, 0.980, R=0.340, clear=0.150, doors=4),
    Rr=0.231, TW=0.255,
    head=[big(slim(0.405, 0.607, 0.828, 0.650, rise=0.030, proj=3, drl='above', shape='angular'), sy=1.90, dir=(-0.3, -0.5, -1.0))],
    mouth=[dict(w=1.214, h=0.173, r=0.03, cx=0.0, cy=0.390, pattern='bars', pitch=0.02, frame=(20, 20, 22), grow=0), dict(w=0.405, h=0.029, r=0.01, cx=0.0, cy=0.607, pattern='none', col=(20, 20, 22), grow=0)],
    tail=dict(housings=[dict(outline=[(0.368, 0.775), (0.741, 0.790), (0.791, 0.762), (0.781, 0.695), (0.711, 0.695), (0.368, 0.710)], style='smoke_red', off=0.012)],
              strips=[([(0.388, 0.763), (0.751, 0.763)], 0.012, 'tail', 0.018)],
              rects=[bar(0.591, 0.715, 0.741, 0.745, 'brake', 'brake_core_r', 0.02), bar(0.408, 0.715, 0.488, 0.742, 'ind', 'amber', 0.02),
                     bar(0.508, 0.715, 0.588, 0.742, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.610, 0.18, 0.660), text_y=0.635, bg=(30, 30, 32), th=0.03)),
    badge="CAMRY", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.66, 'none'),
    stock_wheel='spoke5', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.22, 0.12), screen_mount='floating'),
    maxSpeed=210, accel=4.41, brake=9.0, mass=1550, fuel_l=60, spawnWeight=7, engine='v6_cruiser', horn='high', backfire=0.00,
    colors=["#f2f2f0", "#1b1c1e", "#8a8f95", "#6e1a1e", "#1f3b7a", "#3a3d40"],
)
SPEC['grip'] = 0.96
# real performance: 0-100 km/h in 7.6 s, top speed 210 km/h
