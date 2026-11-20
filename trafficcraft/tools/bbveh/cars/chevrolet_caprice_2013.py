"""Chevrolet Caprice 2013 (2013). Large rear-drive V8 saloon, the classic family and police car of the region.
Body from real dimensions (L 5.160, W 1.900, H 1.480, wheelbase 3.010); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.160
SPEC = dict(
    id='chevrolet_caprice_2013', name='Chevrolet Caprice 2013', make='Chevrolet', model='Caprice LTZ', year=2013, category='sedan', category_name='Full-size sedan',
    description="Large rear-drive V8 saloon, the classic family and police car of the region.",
    **body('sedan', L, 1.900, 1.480, 3.010, 1.050, R=0.350, clear=0.190, doors=4),
    Rr=0.238, TW=0.255,
    head=[big(slim(0.380, 0.636, 0.855, 0.688, rise=0.020, proj=2, drl='below', shape='swept'), sy=1.90, dir=(-0.3, -0.45, -1.0))],
    mouth=[dict(w=0.950, h=0.133, r=0.02, cx=0.0, cy=0.562, pattern='bars', pitch=0.03, frame=(190, 190, 195), grow=0), dict(w=0.950, h=0.104, r=0.02, cx=0.0, cy=0.370, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=[(0.380, 0.794), (0.767, 0.794), (0.817, 0.758), (0.807, 0.616), (0.737, 0.616), (0.380, 0.616)], style='smoke_red', off=0.012)],
              strips=[([(0.400, 0.782), (0.777, 0.782)], 0.012, 'tail', 0.018)],
              rects=[bar(0.617, 0.621, 0.767, 0.714, 'brake', 'brake_core_r', 0.02), bar(0.420, 0.621, 0.500, 0.705, 'ind', 'amber', 0.02),
                     bar(0.520, 0.621, 0.600, 0.705, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.516, 0.18, 0.566), text_y=0.541, bg=(30, 30, 32), th=0.03)),
    badge="CAPRICE", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.68, 'none'),
    stock_wheel='spoke5', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.18, 0.1), screen_mount='stack'),
    maxSpeed=240, accel=5.50, brake=9.0, mass=1850, fuel_l=60, spawnWeight=7, engine='v8_sport', horn='high', backfire=0.00,
    colors=["#f2f2f0", "#1b1c1e", "#8a8f95", "#6e1a1e", "#1f3b5a", "#c9b48a"],
)
SPEC['grip'] = 0.96
# real performance: 0-100 km/h in 6.0 s, top speed 240 km/h
