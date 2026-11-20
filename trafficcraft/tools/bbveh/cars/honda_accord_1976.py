"""Honda Accord (1st gen hatchback, 1976). Round headlamps at the ends of a slim black slatted grille, wrap-around amber/clear corner
lamps; wide rectangular tail lamps split into red, amber and white sections across each side of the hatch."""
from _h import *
L = 4.125
SPEC = dict(
    id='honda_accord_1976', name='Honda Accord 1976', make='Honda', model='Accord (1st gen)', year=1976, category='compact', category_name='Hatchback',
    description='Bigger, better-equipped hatchback that took Honda up a class.',
    L=L, W=1.62, H=1.34, YR=0.83, YF=3.21, R=0.29, TRACK=0.68, Rr=0.165, TW=0.165, tumble=0.04,
    bot=[(0, 0.33), (0.12, 0.27), (0.5, 0.23), (1.2, 0.21), (2.8, 0.21), (3.8, 0.23), (L, 0.29)],
    belt=[(0, 0.84), (0.06, 0.87), (0.5, 0.88), (1.4, 0.87), (2.3, 0.85), (3.0, 0.82), (3.6, 0.77), (L, 0.72)],
    hw=[(0, 0.74), (0.08, 0.78), (0.5, 0.8), (1.5, 0.805), (3.0, 0.805), (3.7, 0.79), (L, 0.75)],
    zsh=[(0, 0.6), (L, 0.56)],
    CABIN0=0.2, CABIN1=2.8,
    roofz=[(0.2, 0.88), (0.3, 1.02), (0.6, 1.24), (1.0, 1.33), (1.8, 1.34), (2.15, 1.32), (2.4, 1.16), (2.6, 1.0), (2.8, 0.83)],
    roofw=[(0.2, 0.66), (0.7, 0.6), (1.8, 0.58), (2.3, 0.6), (2.8, 0.66)],
    crown=[(0, 0.01), (2.8, 0.015), (L, 0.01)],
    ws=(2.3, 2.76), rear_glass=(0.25, 0.95), windows=[(0.95, 1.45), (1.5, 2.8)], bpillars=[(1.45, 1.5)], doors=[('f', 1.45, 2.76)],
    FB_Z=L - 0.25, RB_Z=0.25, hood=(2.82, L - 0.06, 0.64), hatch_x=0.62, fuel=('r', 0.6, 0.8),
    chrome_bumpers=((0.32, 0.4), (0.32, 0.4)), chrome_handles=True, mirror_chrome=True,
    head=[round_head(0.56, 0.6, 0.075, bezel='chrome')],
    front_lamps=[dict(outline=rr(0.68, 0.52, 0.79, 0.66), kind='ind', style='amber', house=False, lift=0.008, view='F')],
    mouth=[dict(w=0.84, h=0.12, r=0.01, cx=0.0, cy=0.6, pattern='bars', pitch=0.022, col=(22, 22, 24), hole=(50, 50, 54), frame=(200, 204, 210))],
    tail=dict(housings=[dict(outline=rr(0.38, 0.6, 0.78, 0.74, 0.012), style='tail_house', off=0.008, bezel='chrome')],
              rects=[bar(0.58, 0.62, 0.76, 0.72), bar(0.39, 0.62, 0.48, 0.72, 'ind', 'amber', 0.012), bar(0.49, 0.62, 0.57, 0.72, 'reverse', 'rev_core_r', 0.012),
                     bar(0.61, 0.645, 0.73, 0.695, 'brake', 'brake_core_r', 0.016)]),
    badge='', plate_f=0.36, plate_r=0.52, exhaust=(-0.42, 0.28, -0.04), stock_exhaust='oval', spoiler=(0.22, 0.6, 'none'),
    stock_wheel='hubcap', beam=(0.56, 0.6, L - 0.04),
    cabin=dict(driver_x=0.3, rear=True, rear_gap=0.68, bench=True, cluster='analog2', screen=False, shifter='manual', seat='flat'),
    maxSpeed=155, accel=3.0, brake=6.8, mass=850, fuel_l=50, spawnWeight=6, engine='i4_petrol', horn='car',
    colors=['#9b6b3c', '#e8e4d8', '#3d5a7a', '#b5121b', '#6b6e48', '#c9c4b4'],
)
