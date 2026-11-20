"""Mercedes G 63 2019 (2019). Upright box-shaped off-roader with a twin-turbo V8 and side-exit exhausts.
Body from real dimensions (L 4.873, W 1.984, H 1.966, wheelbase 2.890); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.873
SPEC = dict(
    id='mercedes_g63_2019', name='Mercedes G 63 2019', make='Mercedes-Benz', model='G 63 (W463)', year=2019, category='suv', category_name='Luxury off-roader',
    description="Upright box-shaped off-roader with a twin-turbo V8 and side-exit exhausts.",
    **body('box', L, 1.984, 1.966, 2.890, 0.860, R=0.390, clear=0.240, doors=4),
    Rr=0.265, TW=0.255,
    head=[round_head(0.714, 0.963, 0.085, 'head', bezel='chrome', extra=[(0.913, 0.885, 0.03, 'ind')])],
    mouth=[dict(w=0.833, h=0.295, r=0.01, cx=0.0, cy=0.865, pattern='bars', pitch=0.03, frame=(30, 30, 32), grow=0), dict(w=0.833, h=0.079, r=0.01, cx=0.0, cy=0.609, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.764, 0.688, 0.903, 0.963, 0.02), style='smoke_red', off=0.012)],
              strips=[([(0.784, 0.943), (0.883, 0.943), (0.883, 0.718)], 0.014, 'tail', 0.018)],
              rects=[bar(0.784, 0.839, 0.873, 0.908, 'brake', 'brake_core_r', 0.018), bar(0.784, 0.770, 0.873, 0.825, 'ind', 'amber', 0.018),
                     bar(0.784, 0.698, 0.873, 0.757, 'reverse', 'rev_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 0.883, 0.3, 0.933), text_y=0.908, bg=(30, 30, 32), th=0.03)),
    badge="G 63", plate_f=0.45, plate_r=0.60, exhaust=(-0.6, 0.38, -0.03), stock_exhaust='dual', spoiler=(0.05, 0.71, 'none'),
    stock_wheel='spoke5', caliper=(140, 142, 148), beam=None,
    cabin=dict(driver_x=0.37, rear=True, rear_gap=0.9, bench=True, cluster='digital_wide', screen=True, shifter='auto', seat='sport', screen_size=(0.62, 0.12), screen_mount='floating'),
    maxSpeed=220, accel=7.21, brake=9.0, mass=2560, fuel_l=80, spawnWeight=3, engine='v8_sport', horn='low', backfire=0.00,
    colors=["#1b1c1e", "#f2f2f0", "#3a4a3a", "#8a8f95", "#b02028", "#d9c27a"],
)
SPEC['grip'] = 0.88
# real performance: 0-100 km/h in 4.5 s, top speed 220 km/h
