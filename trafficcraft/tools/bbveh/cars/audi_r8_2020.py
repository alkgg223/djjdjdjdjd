"""Audi R8 2020 (2020). Mid-engine V10 supercar with side blades and laser lamps.
Body from real dimensions (L 4.429, W 1.940, H 1.236, wheelbase 2.650); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.429
SPEC = dict(
    id='audi_r8_2020', name='Audi R8 2020', make='Audi', model='R8 V10 (4S)', year=2020, category='super', category_name='Supercar',
    description="Mid-engine V10 supercar with side blades and laser lamps.",
    **body('super', L, 1.940, 1.236, 2.650, 1.030, R=0.340, clear=0.120, doors=2),
    Rr=0.231, TW=0.250,
    head=[big(slim(0.466, 0.371, 0.854, 0.414, rise=0.030, proj=2, drl='below', shape='angular'), sy=1.90, dir=(-0.3, -0.6, -1.0))],
    mouth=[dict(w=0.698, h=0.148, r=0.03, cx=0.0, cy=0.272, pattern='honeycomb', frame=(20, 20, 22), grow=0), dict(w=0.310, h=0.148, r=0.02, cx=0.698, cy=0.247, pattern='honeycomb', mirror=True)],
    tail=dict(housings=[dict(outline=[(0.233, 0.771), (0.804, 0.771), (0.854, 0.762), (0.844, 0.728), (0.774, 0.728), (0.233, 0.728)], style='smoke_red', off=0.012)],
              strips=[([(0.253, 0.759), (0.814, 0.759)], 0.012, 'tail', 0.018)],
              rects=[bar(0.654, 0.733, 0.804, 0.752, 'brake', 'brake_core_r', 0.02), bar(0.273, 0.733, 0.353, 0.749, 'ind', 'amber', 0.02),
                     bar(0.373, 0.733, 0.453, 0.749, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.628, 0.18, 0.678), text_y=0.653, bg=(30, 30, 32), th=0.03)),
    badge="R8", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.70, 'low_wing'),
    stock_wheel='turbine', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.32, rear=False, cluster='digital', screen=True, shifter='paddle', seat='bucket', screen_size=(0.2, 0.08), screen_mount='stack'),
    maxSpeed=330, accel=10.00, brake=11.0, mass=1600, fuel_l=60, spawnWeight=2, engine='i6_twinturbo', horn='high', backfire=0.30,
    colors=["#c8102e", "#f2f2f0", "#1b1c1e", "#8a8f95", "#1f5fbf", "#f1c40f"],
)
SPEC['grip'] = 1.12
# real performance: 0-100 km/h in 3.2 s, top speed 330 km/h
