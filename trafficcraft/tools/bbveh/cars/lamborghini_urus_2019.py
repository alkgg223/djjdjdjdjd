"""Lamborghini Urus 2019 (2019). Twin-turbo V8 super SUV with Y-shaped lamps and hexagonal intakes.
Body from real dimensions (L 5.112, W 2.016, H 1.638, wheelbase 3.003); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.112
SPEC = dict(
    id='lamborghini_urus_2019', name='Lamborghini Urus 2019', make='Lamborghini', model='Urus', year=2019, category='suv', category_name='Super SUV',
    description="Twin-turbo V8 super SUV with Y-shaped lamps and hexagonal intakes.",
    **body('suv', L, 2.016, 1.638, 3.003, 1.060, R=0.390, clear=0.160, doors=4),
    Rr=0.265, TW=0.255,
    head=[big(slim(0.444, 0.688, 0.927, 0.737, rise=0.020, proj=2, drl='below', shape='angular'), sy=1.80, dir=(-0.3, -0.45, -1.0))],
    mouth=[dict(w=1.210, h=0.229, r=0.02, cx=0.0, cy=0.491, pattern='honeycomb', frame=(20, 20, 22), grow=0), dict(w=0.282, h=0.213, r=0.02, cx=0.766, cy=0.459, pattern='honeycomb', mirror=True)],
    tail=dict(housings=[dict(outline=[(-0.887, 0.832), (0.887, 0.832), (0.877, 0.766), (-0.877, 0.766)], style='smoke_red', off=0.012)],
              strips=[([(0.222, 0.820), (0.877, 0.820)], 0.012, 'tail', 0.018)],
              rects=[bar(0.717, 0.771, 0.867, 0.802, 'brake', 'brake_core_r', 0.02), bar(0.242, 0.771, 0.322, 0.799, 'ind', 'amber', 0.02),
                     bar(0.342, 0.771, 0.422, 0.799, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.666, 0.18, 0.716), text_y=0.691, bg=(30, 30, 32), th=0.03)),
    badge="URUS", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='quad', spoiler=(0.05, 0.73, 'ducktail'),
    stock_wheel='turbine', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.9, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.25, 0.11), screen_mount='stack'),
    maxSpeed=305, accel=8.93, brake=9.0, mass=2200, fuel_l=80, spawnWeight=5, engine='v8_sport', horn='low', backfire=0.00,
    colors=["#f1c40f", "#3fae2a", "#1b1c1e", "#f2f2f0", "#ff7a00", "#8a8f95"],
)
SPEC['grip'] = 0.92
# real performance: 0-100 km/h in 3.6 s, top speed 305 km/h
