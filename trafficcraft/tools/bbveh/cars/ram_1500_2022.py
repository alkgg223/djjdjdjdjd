"""Ram 1500 2022 (2022). Full-size pickup with a big crosshair-free grille and a HEMI V8.
Body from real dimensions (L 5.916, W 2.085, H 1.970, wheelbase 3.670); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.916
SPEC = dict(
    id='ram_1500_2022', name='Ram 1500 2022', make='Ram', model='1500 Laramie', year=2022, category='pickup', category_name='Full-size pickup',
    description="Full-size pickup with a big crosshair-free grille and a HEMI V8.",
    **body('pickup', L, 2.085, 1.970, 3.670, 1.050, R=0.400, clear=0.240, doors=4, bed=(0.10, 1.97)),
    Rr=0.272, TW=0.255,
    head=[big(slim(0.542, 0.827, 0.980, 0.906, rise=0.000, proj=3, drl='below', shape='angular'), sy=1.80, dir=(-0.25, -0.3, -1.0))],
    mouth=[dict(w=1.147, h=0.374, r=0.01, cx=0.0, cy=0.729, pattern='honeycomb', frame=(200, 200, 205), grow=0), dict(w=1.042, h=0.099, r=0.02, cx=0.0, cy=0.453, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.803, 0.610, 0.949, 1.004, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.823, 0.984), (0.929, 0.984), (0.929, 0.640)], 0.014, 'tail', 0.018)],
              rects=[bar(0.823, 0.827, 0.919, 0.926, 'brake', 'brake_core_r', 0.018), bar(0.823, 0.729, 0.919, 0.807, 'ind', 'amber', 0.018),
                     bar(0.823, 0.620, 0.919, 0.709, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.924, 0.3, 0.974), text_y=0.949, bg=(30, 30, 32), th=0.03)),
    badge="RAM 1500", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.75, 'none'),
    stock_wheel='spoke6', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.86, bench=True, cluster='analog2', screen=True, shifter='auto', seat='flat', screen_size=(0.18, 0.28), screen_mount='portrait'),
    maxSpeed=180, accel=5.18, brake=9.0, mass=2400, fuel_l=80, spawnWeight=6, engine='v8_sport', horn='low', backfire=0.00,
    colors=["#1b1c1e", "#f2f2f0", "#6e1a1e", "#8a8f95", "#1f3b5a", "#c9b48a"],
)
SPEC['grip'] = 0.88
# real performance: 0-100 km/h in 6.4 s, top speed 180 km/h
