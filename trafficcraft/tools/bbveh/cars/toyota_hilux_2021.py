"""Toyota Hilux 2021 (2021). Tough double-cab pickup with a turbo-diesel; the workhorse of every town.
Body from real dimensions (L 5.325, W 1.855, H 1.815, wheelbase 3.085); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 5.325
SPEC = dict(
    id='toyota_hilux_2021', name='Toyota Hilux 2021', make='Toyota', model='Hilux Double Cab', year=2021, category='pickup', category_name='Pickup truck',
    description="Tough double-cab pickup with a turbo-diesel; the workhorse of every town.",
    **body('pickup', L, 1.855, 1.815, 3.085, 0.960, R=0.380, clear=0.270, doors=4, bed=(0.12, 1.74)),
    Rr=0.258, TW=0.255,
    head=[big(slim(0.371, 0.762, 0.835, 0.826, rise=0.030, proj=2, drl='below', shape='swept'), sy=1.90, dir=(-0.3, -0.35, -1.0))],
    mouth=[dict(w=0.965, h=0.363, r=0.05, cx=0.0, cy=0.635, pattern='honeycomb', frame=(30, 30, 32), grow=0), dict(w=0.185, h=0.109, r=0.02, cx=0.705, cy=0.436, pattern='honeycomb', mirror=True)],
    tail=dict(housings=[dict(outline=rr(0.714, 0.561, 0.844, 0.924, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.734, 0.904), (0.824, 0.904), (0.824, 0.591)], 0.014, 'tail', 0.018)],
              rects=[bar(0.734, 0.760, 0.814, 0.851, 'brake', 'brake_core_r', 0.018), bar(0.734, 0.670, 0.814, 0.742, 'ind', 'amber', 0.018),
                     bar(0.734, 0.571, 0.814, 0.652, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.844, 0.3, 0.894), text_y=0.869, bg=(30, 30, 32), th=0.03)),
    badge="HILUX", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='oval', spoiler=(0.05, 0.67, 'none'),
    stock_wheel='spoke6', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.86, bench=True, cluster='analog2', screen=True, shifter='auto', seat='flat', screen_size=(0.2, 0.11), screen_mount='floating'),
    maxSpeed=175, accel=3.44, brake=9.0, mass=2100, fuel_l=80, spawnWeight=6, engine='i4_diesel', horn='low', backfire=0.00,
    colors=["#f2f2f0", "#8a8f95", "#1b1c1e", "#6e1a1e", "#3a4a3a", "#c9b48a"],
)
SPEC['grip'] = 0.88
# real performance: 0-100 km/h in 10.0 s, top speed 175 km/h
