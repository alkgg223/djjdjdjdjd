"""Ferrari 488 GTB 2016 (2016). Mid-engine twin-turbo V8 berlinetta with side air intakes and round tail lamps.
Body from real dimensions (L 4.568, W 1.952, H 1.213, wheelbase 2.650); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.568
SPEC = dict(
    id='ferrari_488_2016', name='Ferrari 488 GTB 2016', make='Ferrari', model='488 GTB', year=2016, category='super', category_name='Supercar',
    description="Mid-engine twin-turbo V8 berlinetta with side air intakes and round tail lamps.",
    **body('super', L, 1.952, 1.213, 2.650, 1.080, R=0.340, clear=0.110, doors=2),
    Rr=0.231, TW=0.250,
    head=[big(slim(0.488, 0.364, 0.859, 0.412, rise=0.040, proj=2, drl='above', shape='swept'), sy=1.90, dir=(-0.3, -0.6, -1.0))],
    mouth=[dict(w=0.820, h=0.121, r=0.03, cx=0.0, cy=0.243, pattern='honeycomb', frame=(20, 20, 22), grow=0), dict(w=0.234, h=0.121, r=0.02, cx=0.703, cy=0.267, pattern='honeycomb', mirror=True)],
    tail=dict(rounds=[(0.764, 0.692, 0.055, 'tail', 'brake', 0.45)], round_off=0.022,
              rects=[bar(0.100, 0.572, 0.200, 0.602, 'reverse', 'rev_core_r', 0.01), bar(0.739, 0.587, 0.819, 0.617, 'ind', 'amber', 0.01)],
              garnish=dict(outline=rr(-0.2, 0.662, 0.2, 0.712), text_y=0.687, bg=(30, 30, 32), th=0.03)),
    badge="488 GTB", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.70, 'low_wing'),
    stock_wheel='spoke5', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.32, rear=False, cluster='digital', screen=True, shifter='paddle', seat='bucket', screen_size=(0.15, 0.08), screen_mount='stack'),
    maxSpeed=330, accel=10.65, brake=11.0, mass=1475, fuel_l=60, spawnWeight=2, engine='v8_sport', horn='high', backfire=0.30,
    colors=["#c8102e", "#f1c40f", "#1b1c1e", "#f2f2f0", "#1f5fbf", "#8a8f95"],
)
SPEC['grip'] = 1.12
# real performance: 0-100 km/h in 3.0 s, top speed 330 km/h
