"""Ford F-150 2021 (2021). America's best-selling truck: big grille, C-clamp lamps and a twin-turbo V6.
Body from real dimensions (L 5.885, W 2.030, H 1.960, wheelbase 3.680); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.885
SPEC = dict(
    id='ford_f150_2021', name='Ford F-150 2021', make='Ford', model='F-150 (P702)', year=2021, category='pickup', category_name='Full-size pickup',
    description="America's best-selling truck: big grille, C-clamp lamps and a twin-turbo V6.",
    **body('pickup', L, 2.030, 1.960, 3.680, 1.000, R=0.400, clear=0.240, doors=4, bed=(0.10, 1.97)),
    Rr=0.272, TW=0.255,
    head=[big(slim(0.528, 0.823, 0.954, 0.902, rise=0.000, proj=2, drl='below', shape='angular'), sy=1.90, dir=(-0.25, -0.3, -1.0))],
    mouth=[dict(w=1.096, h=0.392, r=0.02, cx=0.0, cy=0.725, pattern='bars', pitch=0.055, frame=(190, 190, 195), grow=0), dict(w=1.015, h=0.098, r=0.02, cx=0.0, cy=0.451, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.782, 0.607, 0.924, 0.999, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.802, 0.979), (0.904, 0.979), (0.904, 0.637)], 0.014, 'tail', 0.018)],
              rects=[bar(0.802, 0.823, 0.894, 0.921, 'brake', 'brake_core_r', 0.018), bar(0.802, 0.725, 0.894, 0.803, 'ind', 'amber', 0.018),
                     bar(0.802, 0.617, 0.894, 0.705, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.919, 0.3, 0.969), text_y=0.944, bg=(30, 30, 32), th=0.03)),
    badge="F-150", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.73, 'none'),
    stock_wheel='spoke6', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.86, bench=True, cluster='analog2', screen=True, shifter='auto', seat='flat', screen_size=(0.3, 0.16), screen_mount='stack'),
    maxSpeed=180, accel=5.10, brake=9.0, mass=2200, fuel_l=80, spawnWeight=6, engine='v6_cruiser', horn='low', backfire=0.00,
    colors=["#1f3b7a", "#f2f2f0", "#1b1c1e", "#8a8f95", "#6e1a1e", "#3a4a3a"],
)
SPEC['grip'] = 0.88
# real performance: 0-100 km/h in 6.5 s, top speed 180 km/h
