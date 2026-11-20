"""BMW M3 (E30, 1985). Twin round headlamps per side (larger outer, smaller inner) under one flat front, slim twin kidney grille in the
middle, black bumpers; wide flat rectangular tail lamps with an amber indicator band and white reverse section, boxed fender flares,
boot-lid spoiler."""
from _h import *
L = 4.345
SPEC = dict(
    id='bmw_m3_1985', name='BMW M3 1985', make='BMW', model='M3 (E30)', year=1985, category='sports', category_name='Sports saloon',
    description='Homologation-special two-door saloon with box flares and a high-revving four, a touring-car legend.',
    L=L, W=1.68, H=1.37, YR=0.96, YF=3.525, R=0.3, TRACK=0.72, Rr=0.19, TW=0.205, tumble=0.04,
    bot=[(0, 0.34), (0.15, 0.27), (0.5, 0.23), (1.3, 0.21), (3.0, 0.21), (3.9, 0.22), (L, 0.27)],
    belt=[(0, 0.86), (0.08, 0.9), (0.5, 0.905), (1.2, 0.9), (2.2, 0.885), (3.0, 0.87), (3.6, 0.84), (4.1, 0.8), (L, 0.76)],
    hw=[(0, 0.8), (0.08, 0.83), (0.6, 0.84), (1.0, 0.84), (1.6, 0.81), (2.6, 0.81), (3.5, 0.84), (4.1, 0.83), (L, 0.8)],
    zsh=[(0, 0.62), (2.0, 0.6), (L, 0.58)],
    CABIN0=0.95, CABIN1=3.05,
    roofz=[(0.95, 0.905), (1.1, 1.05), (1.35, 1.27), (1.6, 1.36), (2.2, 1.37), (2.55, 1.36), (2.75, 1.22), (2.9, 1.06), (3.05, 0.87)],
    roofw=[(0.95, 0.7), (1.4, 0.62), (2.2, 0.6), (2.7, 0.61), (3.05, 0.68)],
    crown=[(0, 0.01), (1.0, 0.012), (3.0, 0.015), (4.0, 0.015), (L, 0.01)],
    ws=(2.7, 3.02), rear_glass=(1.0, 1.42), windows=[(1.22, 1.62), (1.66, 3.05)], bpillars=[(1.62, 1.66)], doors=[('f', 1.62, 3.0)],
    FB_Z=L - 0.28, RB_Z=0.3, hood=(3.07, L - 0.06, 0.66), trunk_z=0.95, trunk_y=0.78, fuel=('r', 0.6, 0.83),
    head=[rect_head(0.38, 0.58, 0.76, 0.7, lamps=[(0.47, 0.64, 0.05, 'high'), (0.65, 0.64, 0.058, 'head')], bezel='trim', house='head_house')],
    front_lamps=[dict(outline=rr(0.5, 0.33, 0.66, 0.38), kind='fog', style='fog_lens'), dict(outline=rr(0.7, 0.46, 0.8, 0.5), kind='ind', style='amber', house=False)],
    mouth=[dict(w=0.1, h=0.12, r=0.03, cx=0.065, cy=0.64, pattern='vbars', pitch=0.014, mirror=True, frame=(214, 218, 222)),
           dict(w=1.5, h=0.1, r=0.01, cx=0.0, cy=0.44, pattern='none', col=(26, 26, 28)), dict(w=0.6, h=0.06, r=0.02, cx=0.0, cy=0.33, pattern='bars', pitch=0.02)],
    tail=dict(housings=[dict(outline=rr(0.38, 0.68, 0.8, 0.82, 0.01), style='tail_house', off=0.008)],
              rects=[bar(0.4, 0.75, 0.78, 0.8), bar(0.4, 0.7, 0.55, 0.745, 'ind', 'amber', 0.012), bar(0.56, 0.7, 0.66, 0.745, 'reverse', 'rev_core_r', 0.012),
                     bar(0.67, 0.7, 0.78, 0.745, 'brake', 'brake_core_r', 0.012)],
              garnish=dict(outline=rr(-0.3, 0.72, 0.3, 0.78), text_y=0.75, bg=(60, 12, 18), th=0.035)),
    badge='M3', plate_f=0.36, plate_r=0.6, exhaust=(-0.42, 0.29, -0.04), stock_exhaust='dual', spoiler=(0.05, 0.68, 'ducktail'),
    stock_wheel='mesh', beam=(0.56, 0.64, L - 0.04),
    cabin=dict(driver_x=0.32, rear=True, cluster='analog2', screen=False, shifter='manual', seat='sport'),
    maxSpeed=235, accel=5.0, brake=8.5, mass=1200, fuel_l=55, spawnWeight=4, engine='i4_sport', horn='high', backfire=0.15,
    colors=['#f2f2f0', '#1b1c1e', '#b5121b', '#d9d9d6', '#1e3d6b', '#4b4f54'],
)
