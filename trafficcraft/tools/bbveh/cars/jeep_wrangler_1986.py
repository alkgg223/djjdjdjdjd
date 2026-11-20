"""Jeep Wrangler (YJ, 1986). Square headlamps in the outer slots of the seven-slot grille, amber indicators below; small vertical
tail lamps at the rear corners, spare wheel on the tailgate, upright windscreen, hard top, flat bonnet and wide open wheel arches."""
from _h import *
L = 3.89


def spare(car):
    import math
    from bb import V
    car.el('body', 'solid', 'tyre')
    car.m.cylinder(V(0, 1.0, -0.02), V(0, 0, -1), car.R, 0.2, 16, 'tread', cap_style='tyre', rot=math.pi / 16)
    car.el('body', 'solid', 'steel_cap')
    car.m.face([V(0, 1.0, -0.225) + V(math.cos(a) * 0.17, math.sin(a) * 0.17, 0) for a in [i * math.pi / 8 for i in range(16)]], 'steel_cap',
               outward=V(0, 0, -1))
    car.m.painters['steel_cap'] = car.m.painters['dash2']


SPEC = dict(
    id='jeep_wrangler_1986', name='Jeep Wrangler 1986', make='Jeep', model='Wrangler (YJ)', year=1986, category='suv', category_name='Off-roader',
    description='Short, tough, body-on-frame 4x4 with removable doors and the famous slotted grille - the YJ had square lamps.',
    L=L, W=1.66, H=1.76, YR=0.82, YF=3.19, R=0.37, TRACK=0.72, Rr=0.19, TW=0.24, tumble=0.0, glass_drop=0.06, rocker_black=True,
    bot=[(0, 0.5), (0.3, 0.45), (1.2, 0.42), (2.8, 0.42), (3.6, 0.45), (L, 0.5)],
    belt=[(0, 1.02), (0.05, 1.05), (1.0, 1.06), (2.0, 1.06), (2.6, 1.04), (3.0, 1.03), (3.6, 1.02), (L, 1.0)],
    hw=[(0, 0.8), (0.05, 0.82), (1.0, 0.83), (2.6, 0.83), (3.4, 0.83), (L, 0.8)],
    zsh=[(0, 0.8), (L, 0.8)],
    CABIN0=0.04, CABIN1=2.66,
    roofz=[(0.04, 1.06), (0.08, 1.7), (0.3, 1.76), (2.3, 1.76), (2.45, 1.74), (2.55, 1.5), (2.66, 1.03)],
    roofw=[(0.04, 0.78), (0.3, 0.76), (2.3, 0.76), (2.66, 0.78)],
    crown=[(0, 0.005), (L, 0.01)],
    ws=(2.5, 2.64), rear_glass=(0.06, 0.25), windows=[(0.3, 1.3), (1.42, 2.62)], bpillars=[(1.3, 1.42)], doors=[('f', 1.38, 2.42)],
    FB_Z=L - 0.18, RB_Z=0.18, hood=(2.68, L - 0.04, 0.6), trunk_y=0.7, fuel=('l', 0.5, 0.85), extra=spare, rb_top=3,
    chrome_bumpers=((0.42, 0.52), (0.42, 0.52)), mirror_round=True,
    head=[rect_head(0.44, 0.82, 0.62, 0.95, lamps=[(0.53, 0.885, 0.06, 'sealed')], bezel='chrome')],
    front_lamps=[dict(outline=rr(0.46, 0.68, 0.6, 0.72), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=0.72, h=0.32, r=0.02, cx=0.0, cy=0.84, pattern='vbars', pitch=0.105, col=(30, 30, 32), hole=(20, 20, 22))],
    tail=dict(housings=[dict(outline=rr(0.66, 0.6, 0.78, 0.82, 0.01), style='trim', off=0.01)],
              rects=[bar(0.67, 0.72, 0.77, 0.81), bar(0.67, 0.66, 0.77, 0.71, 'ind', 'amber', 0.014), bar(0.67, 0.61, 0.77, 0.65, 'reverse', 'rev_core_r', 0.014),
                     bar(0.69, 0.74, 0.75, 0.79, 'brake', 'brake_core_r', 0.018)]),
    badge='', plate_f=0.46, plate_r=0.62, exhaust=(-0.5, 0.42, -0.02), stock_exhaust='oval', spoiler=(0.05, 0.6, 'none'),
    stock_wheel='steel', beam=(0.53, 0.885, L - 0.02),
    cabin=dict(driver_x=0.32, rear=True, rear_gap=0.75, bench=True, cluster='classic', screen=False, shifter='manual', seat='flat'),
    maxSpeed=150, accel=3.0, brake=6.5, mass=1400, fuel_l=57, spawnWeight=4, engine='v6_cruiser', horn='low',
    colors=['#2f5f3a', '#b5121b', '#e8e4d8', '#1b1c1e', '#d9c27a', '#3d5a7a'],
)

# modern-style lamps (from the reference photos)
h = round_head(0.53, 0.86, 0.07, 'head')
ring = ngon(0.08, 14, 0.53, 0.86)
h['strips'] = [(ring + ring[:1], 0.01, 'drl')]
SPEC['head'] = [h]

# lamp size pass
SPEC['tail_scale'] = 1.22

SPEC['grip'] = 0.62

# real performance: 0-100 km/h in 12.5 s, top speed 145 km/h
SPEC['accel'] = 2.82
SPEC['maxSpeed'] = 145
