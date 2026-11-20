"""Nissan Silvia S15 (1999). Rear-drive turbo coupe with sharp lamps, a drift icon.
Body from real dimensions (L 4.445, W 1.695, H 1.285, wheelbase 2.525); lamps, grille and tail lamps are this model's own."""
from _h import *
from _gen import body
L = 4.445
SPEC = dict(
    id='nissan_silvia_s15_1999', name='Nissan Silvia S15', make='Nissan', model='Silvia Spec-R (S15)', year=1999, category='sports', category_name='Sports coupe',
    description="Rear-drive turbo coupe with sharp lamps, a drift icon.",
    **body('coupe', L, 1.695, 1.285, 2.525, 0.940, R=0.320, clear=0.120, doors=2),
    Rr=0.218, TW=0.250,
    head=[big(slim(0.373, 0.553, 0.763, 0.591, rise=0.030, proj=2, drl='below', shape='swept'), sy=1.80, dir=(-0.3, -0.5, -1.0))],
    mouth=[dict(w=0.712, h=0.141, r=0.04, cx=0.0, cy=0.347, pattern='honeycomb', frame=(20, 20, 22), grow=0), dict(w=0.170, h=0.103, r=0.02, cx=0.644, cy=0.334, pattern='honeycomb', mirror=True)],
    tail=dict(housings=[dict(outline=[(0.339, 0.687), (0.679, 0.702), (0.729, 0.674), (0.719, 0.607), (0.649, 0.607), (0.339, 0.622)], style='smoke_red', off=0.012)],
              strips=[([(0.359, 0.675), (0.689, 0.675)], 0.012, 'tail', 0.018)],
              rects=[bar(0.529, 0.627, 0.679, 0.658, 'brake', 'brake_core_r', 0.02), bar(0.379, 0.627, 0.459, 0.655, 'ind', 'amber', 0.02),
                     bar(0.479, 0.627, 0.559, 0.655, 'reverse', 'rev_core_r', 0.02)],
              garnish=dict(outline=rr(-0.18, 0.522, 0.18, 0.572), text_y=0.547, bg=(30, 30, 32), th=0.03)),
    badge="SILVIA", plate_f=0.30, plate_r=0.50, exhaust=(-0.55, 0.30, -0.03), stock_exhaust='oval', spoiler=(0.05, 0.61, 'low_wing'),
    stock_wheel='spoke5', caliper=(200, 30, 30), beam=None,
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog3', screen=False, shifter='manual', seat='sport', screen_size=(0.15, 0.08), screen_mount='stack'),
    maxSpeed=240, accel=5.97, brake=11.0, mass=1240, fuel_l=60, spawnWeight=3, engine='i4_turbo', horn='high', backfire=0.20,
    colors=["#e8eaec", "#f1c40f", "#1b1c1e", "#c8102e", "#1f5fbf", "#8a8f95"],
)
SPEC['grip'] = 1.02
# real performance: 0-100 km/h in 5.5 s, top speed 240 km/h
