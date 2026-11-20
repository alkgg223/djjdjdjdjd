"""Hyundai Accent 2018 (2018). Small economical sedan, a common taxi and first car.
Body from real dimensions (L 4.385, W 1.730, H 1.460, wheelbase 2.600); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.385
SPEC = dict(
    id='hyundai_accent_2018', name='Hyundai Accent 2018', make='Hyundai', model='Accent (HC)', year=2018, category='sedan', category_name='Compact sedan',
    description="Small economical sedan, a common taxi and first car.",
    **body('sedan', L, 1.730, 1.460, 2.600, 0.860, R=0.310, clear=0.150, doors=4),
    Rr=0.211, TW=0.255,
    head=[big(slim(0.381, 0.613, 0.778, 0.664, rise=0.025, proj=2, drl='below', shape='curve'), sy=1.90, dir=(-0.3, -0.5, -1.0))],
    mouth=[dict(w=0.761, h=0.175, r=0.05, cx=0.0, cy=0.453, pattern='honeycomb', frame=(30, 30, 32), grow=0)],
    tail=dict(housings=[dict(outline=[(0.381, 0.783), (0.694, 0.793), (0.744, 0.765), (0.734, 0.685), (0.664, 0.685), (0.381, 0.695)], style='smoke_red', off=0.012)],
              strips=[([(0.401, 0.771), (0.704, 0.771)], 0.012, 'tail', 0.018)],
              rects=[bar(0.544, 0.700, 0.694, 0.744, 'brake', 'brake_core_r', 0.02), bar(0.421, 0.700, 0.501, 0.739, 'ind', 'amber', 0.02),
                     bar(0.521, 0.700, 0.601, 0.739, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.595, 0.18, 0.645), text_y=0.620, bg=(30, 30, 32), th=0.03)),
    badge="ACCENT", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='oval', spoiler=(0.05, 0.62, 'none'),
    stock_wheel='hubcap', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.17, 0.1), screen_mount='floating'),
    maxSpeed=180, accel=3.16, brake=9.0, mass=1150, fuel_l=60, spawnWeight=7, engine='i4_petrol', horn='high', backfire=0.00,
    colors=["#f2f2f0", "#8a8f95", "#1b1c1e", "#6e1a1e", "#1f3b7a", "#c9b48a"],
)
SPEC['grip'] = 0.96
# real performance: 0-100 km/h in 11.0 s, top speed 180 km/h
