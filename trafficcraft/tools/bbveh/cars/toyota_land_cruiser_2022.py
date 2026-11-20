"""Toyota Land Cruiser 2022 (2022). Full-size body-on-frame SUV with a twin-turbo V6, built for the desert and the city.
Body from real dimensions (L 4.985, W 1.980, H 1.945, wheelbase 2.850); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.985
SPEC = dict(
    id='toyota_land_cruiser_2022', name='Toyota Land Cruiser 2022', make='Toyota', model='Land Cruiser 300', year=2022, category='suv', category_name='Large SUV',
    description="Full-size body-on-frame SUV with a twin-turbo V6, built for the desert and the city.",
    **body('suv', L, 1.980, 1.945, 2.850, 0.980, R=0.390, clear=0.230, doors=4),
    Rr=0.265, TW=0.255,
    head=[big(slim(0.436, 0.827, 0.911, 0.885, rise=0.000, proj=3, drl='above', shape='angular'), sy=1.90, dir=(-0.3, -0.35, -1.0))],
    mouth=[dict(w=0.990, h=0.331, r=0.03, cx=0.0, cy=0.739, pattern='bars', pitch=0.05, frame=(20, 20, 22), grow=0), dict(w=0.891, h=0.097, r=0.02, cx=0.0, cy=0.486, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.663, 0.602, 0.901, 0.991, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.683, 0.971), (0.881, 0.971), (0.881, 0.632)], 0.014, 'tail', 0.018)],
              rects=[bar(0.683, 0.816, 0.871, 0.914, 'brake', 'brake_core_r', 0.018), bar(0.683, 0.719, 0.871, 0.797, 'ind', 'amber', 0.018),
                     bar(0.683, 0.612, 0.871, 0.700, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.911, 0.3, 0.961), text_y=0.936, bg=(30, 30, 32), th=0.03)),
    badge="LAND CRUISER", plate_f=0.45, plate_r=0.60, exhaust=(-0.55, 0.42, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.71, 'none'),
    stock_wheel='spoke6', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.9, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport', screen_size=(0.31, 0.15), screen_mount='floating'),
    maxSpeed=210, accel=4.96, brake=9.0, mass=2560, fuel_l=80, spawnWeight=5, engine='v6_cruiser', horn='low', backfire=0.00,
    colors=["#f2f2f0", "#1b1c1e", "#8a8f95", "#c9b48a", "#3a3d40", "#6e1a1e"],
)
SPEC['grip'] = 0.92
# real performance: 0-100 km/h in 6.7 s, top speed 210 km/h
