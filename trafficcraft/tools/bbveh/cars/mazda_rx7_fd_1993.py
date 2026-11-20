"""Mazda RX-7 (FD) (1993). Lightweight twin-rotor rotary coupe with pop-up headlamps and a curvy body.
Body from real dimensions (L 4.295, W 1.760, H 1.230, wheelbase 2.425); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.295
SPEC = dict(
    id='mazda_rx7_fd_1993', name='Mazda RX-7 (FD)', make='Mazda', model='RX-7 (FD3S)', year=1993, category='sports', category_name='Sports coupe',
    description="Lightweight twin-rotor rotary coupe with pop-up headlamps and a curvy body.",
    **body('coupe', L, 1.760, 1.230, 2.425, 0.930, R=0.320, clear=0.120, doors=2),
    Rr=0.218, TW=0.250,
    head=[], popups=[dict(x0=0.352, x1=0.704, z0=L - 0.62, z1=L - 0.36, h=0.08, angle=-70, front_lamp=True)],
    mouth=[dict(w=0.810, h=0.111, r=0.05, cx=0.0, cy=0.332, pattern='honeycomb', frame=(20, 20, 22), grow=0)],
    tail=dict(housings=[dict(outline=[(-0.727, 0.656), (0.727, 0.656), (0.717, 0.613), (-0.717, 0.613)], style='smoke_red', off=0.012)],
              strips=[([(0.080, 0.644), (0.717, 0.644)], 0.012, 'tail', 0.018)],
              rects=[bar(0.557, 0.618, 0.707, 0.637, 'brake', 'brake_core_r', 0.02), bar(0.100, 0.618, 0.180, 0.635, 'ind', 'amber', 0.02),
                     bar(0.200, 0.618, 0.280, 0.635, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.513, 0.18, 0.563), text_y=0.538, bg=(30, 30, 32), th=0.03)),
    badge="RX-7", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='oval', spoiler=(0.05, 0.63, 'hoop'),
    stock_wheel='spoke5', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog3', screen=False, shifter='manual', seat='sport', screen_size=(0.15, 0.08), screen_mount='stack'),
    maxSpeed=250, accel=6.18, brake=11.0, mass=1260, fuel_l=60, spawnWeight=3, engine='i4_turbo', horn='high', backfire=0.20,
    colors=["#c8102e", "#f2f2f0", "#1b1c1e", "#e3b23c", "#1f5fbf", "#8a8f95"],
)
SPEC['grip'] = 1.02
# real performance: 0-100 km/h in 5.3 s, top speed 250 km/h
