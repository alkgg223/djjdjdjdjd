"""Hyundai Sonata 2020 (2020). Coupe-like midsize sedan with hidden daytime lights running up the bonnet.
Body from real dimensions (L 4.900, W 1.860, H 1.445, wheelbase 2.840); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.900
SPEC = dict(
    id='hyundai_sonata_2020', name='Hyundai Sonata 2020', make='Hyundai', model='Sonata (DN8)', year=2020, category='sedan', category_name='Midsize sedan',
    description="Coupe-like midsize sedan with hidden daytime lights running up the bonnet.",
    **body('sedan', L, 1.860, 1.445, 2.840, 0.980, R=0.340, clear=0.160, doors=4),
    Rr=0.231, TW=0.255,
    head=[big(slim(0.372, 0.607, 0.837, 0.650, rise=0.035, proj=2, drl='above', shape='swept'), sy=1.90, dir=(-0.3, -0.5, -1.0))],
    mouth=[dict(w=0.930, h=0.188, r=0.05, cx=0.0, cy=0.448, pattern='honeycomb', frame=(30, 30, 32), grow=0), dict(w=0.186, h=0.116, r=0.02, cx=0.707, cy=0.347, pattern='honeycomb', mirror=True)],
    tail=dict(housings=[dict(outline=[(-0.770, 0.775), (0.770, 0.775), (0.760, 0.724), (-0.760, 0.724)], style='smoke_red', off=0.012)],
              strips=[([(0.169, 0.763), (0.760, 0.763)], 0.012, 'tail', 0.018)],
              rects=[bar(0.600, 0.729, 0.750, 0.752, 'brake', 'brake_core_r', 0.02), bar(0.189, 0.729, 0.269, 0.749, 'ind', 'amber', 0.02),
                     bar(0.289, 0.729, 0.369, 0.749, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.624, 0.18, 0.674), text_y=0.649, bg=(30, 30, 32), th=0.03)),
    badge="SONATA", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.67, 'none'),
    stock_wheel='turbine', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.25, 0.1), screen_mount='floating'),
    maxSpeed=210, accel=3.98, brake=9.0, mass=1480, fuel_l=60, spawnWeight=7, engine='i4_turbo', horn='high', backfire=0.00,
    colors=["#f2f2f0", "#1b1c1e", "#8a8f95", "#1f3b7a", "#6e1a1e", "#c9b48a"],
)
SPEC['grip'] = 0.96
# real performance: 0-100 km/h in 8.5 s, top speed 210 km/h
