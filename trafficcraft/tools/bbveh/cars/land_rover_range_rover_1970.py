"""Range Rover (Classic, 1970) 2-door. Round headlamps with amber indicators under them either side of a full-width black slatted
grille, clamshell bonnet; tall vertical tail-lamp clusters (red / amber / white) at the rear corners, split tailgate, upright glass."""
from _h import *
L = 4.47
SPEC = dict(
    id='land_rover_range_rover_1970', name='Range Rover 1970', make='Land Rover', model='Range Rover (Classic)', year=1970, category='suv',
    category_name='Luxury 4x4', description='The original luxury off-roader: coil springs, a V8 and permanent four-wheel drive.',
    L=L, W=1.78, H=1.78, YR=1.03, YF=3.57, R=0.36, TRACK=0.75, Rr=0.19, TW=0.205, tumble=0.02, glass_drop=0.06,
    bot=[(0, 0.46), (0.3, 0.42), (1.3, 0.38), (3.0, 0.38), (4.0, 0.42), (L, 0.46)],
    belt=[(0, 1.02), (0.05, 1.06), (1.0, 1.07), (2.2, 1.06), (3.0, 1.05), (3.8, 1.02), (L, 0.98)],
    hw=[(0, 0.86), (0.05, 0.88), (1.0, 0.89), (3.0, 0.89), (3.9, 0.89), (L, 0.86)],
    zsh=[(0, 0.78), (L, 0.76)],
    CABIN0=0.04, CABIN1=3.15,
    roofz=[(0.04, 1.07), (0.08, 1.7), (0.3, 1.78), (2.7, 1.78), (2.85, 1.75), (3.0, 1.5), (3.15, 1.05)],
    roofw=[(0.04, 0.84), (0.3, 0.8), (2.7, 0.8), (3.15, 0.84)],
    crown=[(0, 0.005), (3.0, 0.01), (L, 0.015)],
    ws=(2.88, 3.12), rear_glass=(0.06, 0.28), windows=[(0.3, 1.5), (1.58, 3.12)], bpillars=[(1.5, 1.58)], doors=[('f', 1.55, 2.85)],
    FB_Z=L - 0.2, RB_Z=0.2, hood=(3.17, L - 0.04, 0.84), trunk_y=1.0, fuel=('r', 0.7, 0.9), rb_top=3,
    chrome_bumpers=((0.42, 0.54), (0.42, 0.54)), mirror_round=True,
    head=[round_head(0.66, 0.86, 0.085, bezel='chrome')],
    front_lamps=[dict(outline=rr(0.6, 0.7, 0.74, 0.74), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=1.06, h=0.22, r=0.01, cx=0.0, cy=0.86, pattern='bars', pitch=0.035, col=(30, 30, 32), hole=(14, 14, 16))],
    tail=dict(housings=[dict(outline=rr(0.7, 0.62, 0.84, 0.94, 0.01), style='trim', off=0.01)],
              rects=[bar(0.71, 0.82, 0.83, 0.93), bar(0.71, 0.73, 0.83, 0.81, 'ind', 'amber', 0.014), bar(0.71, 0.63, 0.83, 0.72, 'reverse', 'rev_core_r', 0.014),
                     bar(0.73, 0.84, 0.81, 0.91, 'brake', 'brake_core_r', 0.018)],
              garnish=dict(outline=rr(-0.3, 1.04, 0.3, 1.1), text_y=1.07, bg=(30, 30, 32), th=0.03)),
    badge='RANGE ROVER', plate_f=0.48, plate_r=0.62, exhaust=(-0.5, 0.4, -0.02), stock_exhaust='oval', spoiler=(0.05, 0.6, 'none'),
    stock_wheel='steel', beam=(0.66, 0.86, L - 0.02),
    cabin=dict(driver_x=0.35, rear=True, rear_gap=0.85, bench=True, cluster='classic', screen=False, shifter='manual', seat='flat'),
    maxSpeed=160, accel=3.2, brake=6.5, mass=1750, fuel_l=86, spawnWeight=4, engine='v8_sport', horn='low',
    colors=['#d9c27a', '#2f5f3a', '#3d5a7a', '#e8e4d8', '#8a1c22', '#4b4f54'],
)

# modern-style lamps (from the reference photos)
SPEC['head'] = [slim(0.5, 0.84, 0.84, 0.88, rise=0.0, proj=3)]
