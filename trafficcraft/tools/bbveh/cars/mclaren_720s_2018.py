"""McLaren 720S 2018 (2018). Carbon-tub supercar with eye-socket headlamps and dihedral doors.
Body from real dimensions (L 4.543, W 1.930, H 1.196, wheelbase 2.670); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.543
SPEC = dict(
    id='mclaren_720s_2018', name='McLaren 720S 2018', make='McLaren', model='720S', year=2018, category='super', category_name='Supercar',
    description="Carbon-tub supercar with eye-socket headlamps and dihedral doors.",
    **body('super', L, 1.930, 1.196, 2.670, 1.120, R=0.340, clear=0.110, doors=2),
    Rr=0.231, TW=0.250,
    head=[big(slim(0.482, 0.359, 0.849, 0.413, rise=0.040, proj=2, drl='below', shape='swept'), sy=1.90, dir=(-0.3, -0.6, -1.0))],
    mouth=[dict(w=0.270, h=0.144, r=0.02, cx=0.637, cy=0.287, pattern='honeycomb', mirror=True, frame=(20, 20, 22), grow=0), dict(w=0.772, h=0.036, r=0.01, cx=0.0, cy=0.179, pattern='none', col=(20, 20, 22), grow=0)],
    tail=dict(housings=[dict(outline=[(0.386, 0.745), (0.799, 0.775), (0.849, 0.739), (0.839, 0.686), (0.769, 0.686), (0.386, 0.716)], style='smoke_red', off=0.012)],
              strips=[([(0.406, 0.733), (0.809, 0.733)], 0.012, 'tail', 0.018)],
              rects=[bar(0.649, 0.721, 0.799, 0.732, 'brake', 'brake_core_r', 0.02), bar(0.426, 0.721, 0.506, 0.730, 'ind', 'amber', 0.02),
                     bar(0.526, 0.721, 0.606, 0.730, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.616, 0.18, 0.666), text_y=0.641, bg=(30, 30, 32), th=0.03)),
    badge="720S", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='center', spoiler=(0.05, 0.69, 'low_wing'),
    stock_wheel='turbine', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.32, rear=False, cluster='digital', screen=True, shifter='paddle', seat='bucket', screen_size=(0.12, 0.17), screen_mount='portrait'),
    maxSpeed=341, accel=11.00, brake=11.0, mass=1420, fuel_l=60, spawnWeight=2, engine='v8_sport', horn='high', backfire=0.30,
    colors=["#ff7a00", "#f2f2f0", "#1b1c1e", "#1f5fbf", "#c8102e", "#8a8f95"],
)
SPEC['grip'] = 1.12
# real performance: 0-100 km/h in 2.9 s, top speed 341 km/h
