"""Mercedes-Benz S-Class (W116, 1972). Large ribbed rectangular headlamp units with the amber indicator on their outer end, an upright
chrome-framed grille with vertical bars in the middle; tall ribbed tail lamps (red / amber / white bands) that keep dirt off, chrome bumpers."""
from _h import *
L = 4.96
SPEC = dict(
    id='mercedes_sclass_1972', name='Mercedes-Benz S-Class 1972', make='Mercedes-Benz', model='S-Class (W116)', year=1972, category='luxury',
    category_name='Luxury saloon', description='The first car officially called S-Class: a big, safe, refined four-door saloon.',
    L=L, W=1.87, H=1.42, YR=1.115, YF=3.98, R=0.33, TRACK=0.78, Rr=0.19, TW=0.205, tumble=0.05,
    bot=[(0, 0.34), (0.15, 0.27), (0.6, 0.24), (1.5, 0.22), (3.4, 0.22), (4.5, 0.25), (L, 0.31)],
    belt=[(0, 0.86), (0.08, 0.9), (0.6, 0.91), (1.5, 0.9), (2.5, 0.89), (3.4, 0.88), (4.2, 0.86), (4.7, 0.83), (L, 0.8)],
    hw=[(0, 0.86), (0.1, 0.9), (0.6, 0.925), (1.5, 0.93), (3.0, 0.93), (4.0, 0.93), (4.6, 0.91), (L, 0.88)],
    zsh=[(0, 0.62), (2.0, 0.6), (L, 0.6)],
    CABIN0=1.05, CABIN1=3.55,
    roofz=[(1.05, 0.91), (1.2, 1.08), (1.5, 1.32), (1.8, 1.41), (2.6, 1.42), (2.95, 1.4), (3.15, 1.26), (3.35, 1.08), (3.55, 0.88)],
    roofw=[(1.05, 0.78), (1.5, 0.7), (2.4, 0.68), (3.0, 0.69), (3.55, 0.76)],
    crown=[(0, 0.012), (1.0, 0.012), (3.5, 0.02), (4.4, 0.022), (L, 0.01)],
    ws=(3.1, 3.5), rear_glass=(1.1, 1.58), windows=[(1.62, 2.38), (2.46, 3.55)], bpillars=[(2.38, 2.46)], doors=[('r', 1.6, 2.42), ('f', 2.42, 3.5)],
    FB_Z=L - 0.3, RB_Z=0.3, hood=(3.57, L - 0.08, 0.7), trunk_z=1.0, trunk_y=0.82, fuel=('r', 0.75, 0.86), belt_chrome=0.88, chrome_handles=True,
    chrome_bumpers=((0.38, 0.5), (0.38, 0.5)), mirror_chrome=True, a_black=False,
    head=[rect_head(0.4, 0.56, 0.84, 0.74, lamps=[(0.5, 0.65, 0.065, 'head'), (0.64, 0.65, 0.05, 'high')], rects=[(0.74, 0.58, 0.83, 0.72, 'ind')],
                    bezel='chrome', house='head_house')],
    front_lamps=[dict(outline=rr(0.56, 0.4, 0.7, 0.44), kind='fog', style='fog_lens')],
    mouth=[dict(w=0.46, h=0.26, r=0.02, cx=0.0, cy=0.66, pattern='vbars', pitch=0.03, col=(214, 218, 222), hole=(20, 20, 22), frame=(226, 230, 234))],
    tail=dict(housings=[dict(outline=rr(0.4, 0.6, 0.84, 0.84, 0.015), style='tail_house', off=0.01, bezel='chrome')],
              rects=[bar(0.42, 0.76, 0.82, 0.82, 'ind', 'amber', 0.014), bar(0.42, 0.68, 0.82, 0.75), bar(0.42, 0.62, 0.58, 0.67, 'reverse', 'rev_core_r', 0.014),
                     bar(0.6, 0.62, 0.82, 0.67, 'brake', 'brake_core_r', 0.014)],
              garnish=dict(outline=rr(-0.18, 0.74, 0.18, 0.79), text_y=0.765, bg=(200, 204, 210), fg=(30, 30, 32), th=0.03)),
    badge='450 SE', plate_f=0.34, plate_r=0.66, exhaust=(-0.5, 0.29, -0.05), stock_exhaust='oval', spoiler=(0.05, 0.75, 'none'),
    stock_wheel='hubcap', beam=(0.55, 0.65, L - 0.05),
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='classic', screen=False, shifter='auto', seat='flat'),
    maxSpeed=210, accel=3.8, brake=7.5, mass=1750, fuel_l=96, spawnWeight=4, engine='v8_sport', horn='low',
    colors=['#e8e4d8', '#1d1e20', '#7a1e22', '#4a5d4f', '#9aa2a8', '#c4a668'],
)
