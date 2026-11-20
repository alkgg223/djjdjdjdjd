"""Chevrolet Camaro (1st gen, introduced 1966). Full-width chrome grid grille with the round headlamps set into its outer ends,
amber parking lamps in the lower valance; two rectangular tail lamps side by side on each side of a dark rear panel; coke-bottle hips."""
from _h import *
L = 4.7
SPEC = dict(
    id='chevrolet_camaro_1966', name='Chevrolet Camaro 1966', make='Chevrolet', model='Camaro (1st gen)', year=1966, category='muscle',
    category_name='Pony car', description='Chevrolet\'s answer to the Mustang: a long hood, short deck and a range of V8 engines.',
    L=L, W=1.84, H=1.3, YR=1.02, YF=3.77, R=0.335, TRACK=0.76, Rr=0.178, TW=0.2,
    bot=[(0, 0.33), (0.15, 0.27), (0.6, 0.235), (1.4, 0.215), (3.3, 0.215), (4.4, 0.25), (L, 0.31)],
    belt=[(0, 0.83), (0.08, 0.88), (0.5, 0.9), (1.1, 0.905), (1.7, 0.88), (2.6, 0.86), (3.4, 0.85), (4.4, 0.8), (L, 0.76)],
    hw=[(0, 0.84), (0.12, 0.88), (0.6, 0.915), (1.1, 0.92), (1.8, 0.895), (2.6, 0.89), (3.7, 0.905), (4.4, 0.88), (L, 0.84)],
    zsh=[(0, 0.6), (1.0, 0.61), (2.5, 0.57), (L, 0.55)],
    CABIN0=1.0, CABIN1=3.05,
    roofz=[(1.0, 0.905), (1.15, 1.03), (1.45, 1.22), (1.7, 1.285), (2.1, 1.3), (2.45, 1.29), (2.65, 1.22), (2.85, 1.06), (3.05, 0.86)],
    roofw=[(1.0, 0.76), (1.45, 0.66), (2.1, 0.63), (2.6, 0.64), (3.05, 0.72)],
    crown=[(0, 0.01), (1.0, 0.012), (3.0, 0.02), (4.0, 0.025), (L, 0.01)],
    ws=(2.6, 3.0), rear_glass=(1.05, 1.52), windows=[(1.3, 1.82), (1.86, 3.05)], bpillars=[(1.82, 1.86)], doors=[('f', 1.62, 3.0)],
    FB_Z=L - 0.3, RB_Z=0.3, hood=(3.07, L - 0.08, 0.7), trunk_z=0.95, trunk_y=0.8, fuel=('r', 0.03, 0.66),
    chrome_bumpers=((0.4, 0.5), (0.42, 0.52)), chrome_handles=True, mirror_chrome=True, mirror_round=True, a_black=False,
    head=[round_head(0.64, 0.64, 0.085, bezel='trim')],
    front_lamps=[dict(outline=rr(0.4, 0.35, 0.52, 0.38), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=1.5, h=0.22, r=0.02, cx=0.0, cy=0.64, pattern='grid', pitch=0.035, frame=(214, 218, 222))],
    tail=dict(housings=[dict(outline=rr(0.28, 0.62, 0.8, 0.76, 0.01), style='trim', off=0.006, bezel='chrome')],
              rects=[bar(0.31, 0.64, 0.53, 0.74), bar(0.56, 0.64, 0.77, 0.74), bar(0.34, 0.665, 0.5, 0.715, 'brake', 'brake_core_r', 0.016),
                     bar(0.59, 0.665, 0.74, 0.715, 'brake', 'brake_core_r', 0.016), bar(0.18, 0.36, 0.26, 0.4, 'reverse', 'rev_core_r', 0.006)],
              garnish=dict(outline=rr(-0.15, 0.66, 0.15, 0.72), text_y=0.69, bg=(22, 22, 24), th=0.035)),
    badge='CAMARO', plate_f=0.33, plate_r=0.6, exhaust=(-0.5, 0.29, -0.06), stock_exhaust='dual', spoiler=(0.05, 0.74, 'none'),
    stock_wheel='hubcap', beam=(0.64, 0.64, L - 0.05),
    cabin=dict(driver_x=0.35, rear=True, cluster='classic', screen=False, shifter='auto', seat='flat'),
    maxSpeed=200, accel=4.8, brake=7.2, mass=1420, fuel_l=68, spawnWeight=4, engine='v8_sport', horn='low',
    colors=['#e2e0d6', '#1d3d7a', '#c8102e', '#e9b018', '#222224', '#2f6b3c'],
)

# lamp size pass
SPEC['tail_scale'] = 1.22
