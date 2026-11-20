"""BMW X5 2020 (2020). Sporty luxury SUV with huge kidney grilles and laser headlamps.
Body from real dimensions (L 4.922, W 2.004, H 1.745, wheelbase 2.975); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.922
SPEC = dict(
    id='bmw_x5_2020', name='BMW X5 2020', make='BMW', model='X5 (G05)', year=2020, category='suv', category_name='Luxury SUV',
    description="Sporty luxury SUV with huge kidney grilles and laser headlamps.",
    **body('suv', L, 2.004, 1.745, 2.975, 0.930, R=0.380, clear=0.210, doors=4),
    Rr=0.258, TW=0.255,
    head=[big(slim(0.481, 0.750, 0.922, 0.803, rise=0.015, proj=2, drl='above', shape='angular'), sy=1.80, dir=(-0.3, -0.4, -1.0))],
    mouth=[dict(w=0.341, h=0.297, r=0.05, cx=0.200, cy=0.698, pattern='bars', pitch=0.03, mirror=True, frame=(190, 190, 195), grow=0), dict(w=0.281, h=0.140, r=0.02, cx=0.762, cy=0.419, pattern='honeycomb', mirror=True)],
    tail=dict(housings=[dict(outline=[(0.441, 0.887), (0.862, 0.907), (0.912, 0.870), (0.902, 0.780), (0.832, 0.780), (0.441, 0.800)], style='smoke_red', off=0.012)],
              strips=[([(0.461, 0.875), (0.872, 0.875)], 0.012, 'tail', 0.018)],
              rects=[bar(0.712, 0.805, 0.862, 0.848, 'brake', 'brake_core_r', 0.02), bar(0.481, 0.805, 0.561, 0.844, 'ind', 'amber', 0.02),
                     bar(0.581, 0.805, 0.661, 0.844, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.700, 0.18, 0.750), text_y=0.725, bg=(30, 30, 32), th=0.03)),
    badge="X5", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='quad', spoiler=(0.05, 0.72, 'none'),
    stock_wheel='turbine', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.9, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.3, 0.11), screen_mount='floating'),
    maxSpeed=243, accel=5.97, brake=9.0, mass=2200, fuel_l=80, spawnWeight=5, engine='i6_twinturbo', horn='low', backfire=0.00,
    colors=["#1b1c1e", "#f2f2f0", "#1d3f9a", "#8a8f95", "#6e1a1e", "#3a3d40"],
)
SPEC['grip'] = 0.92
# real performance: 0-100 km/h in 5.5 s, top speed 243 km/h
