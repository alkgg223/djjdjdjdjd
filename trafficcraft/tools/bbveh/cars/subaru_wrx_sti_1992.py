"""Subaru Impreza WRX STI (GC8, 1992). Multi-reflector headlamps that taper towards the small chrome-framed grille, bonnet scoop,
round fog lamps in the bumper intake; wide tail lamps with a red band over amber/white and a tall rear wing, gold wheels."""
from _h import *
L = 4.34


def scoop(car):
    m = car.m
    import parts as PT
    z0 = 3.6
    y = car.deck(0, z0)
    car.el('body', 'paint', 'paint')
    PT.prism_x(m, [(z0 - 0.2, y - 0.005), (z0 + 0.32, y - 0.005), (z0 + 0.15, y + 0.06), (z0 - 0.12, y + 0.065)], -0.24, 0.24, 'paint')
    car.el('body', 'solid', 'grille')
    from bb import V
    m.face([V(0.22, y, z0 + 0.15), V(-0.22, y, z0 + 0.15), V(-0.22, y + 0.055, z0 + 0.16), V(0.22, y + 0.055, z0 + 0.16)], 'grille', outward=V(0, 0.2, 1))


SPEC = dict(
    id='subaru_wrx_sti_1992', name='Subaru Impreza WRX STI 1992', make='Subaru', model='Impreza WRX STI (GC8)', year=1992, category='sports',
    category_name='Rally saloon', description='Turbocharged flat-four, symmetrical all-wheel drive and a rally pedigree.',
    L=L, W=1.69, H=1.405, YR=0.94, YF=3.46, R=0.31, TRACK=0.72, Rr=0.203, TW=0.205, tumble=0.04,
    bot=[(0, 0.34), (0.12, 0.27), (0.5, 0.235), (1.3, 0.22), (3.0, 0.22), (3.9, 0.23), (L, 0.28)],
    belt=[(0, 0.88), (0.06, 0.92), (0.5, 0.93), (1.3, 0.92), (2.3, 0.9), (3.0, 0.88), (3.6, 0.84), (4.0, 0.79), (L, 0.74)],
    hw=[(0, 0.8), (0.06, 0.83), (0.5, 0.845), (1.3, 0.845), (2.6, 0.84), (3.5, 0.845), (4.0, 0.83), (L, 0.78)],
    zsh=[(0, 0.62), (L, 0.58)],
    CABIN0=0.95, CABIN1=3.1,
    roofz=[(0.95, 0.93), (1.1, 1.1), (1.4, 1.36), (1.7, 1.4), (2.4, 1.405), (2.65, 1.38), (2.85, 1.2), (3.0, 1.05), (3.1, 0.88)],
    roofw=[(0.95, 0.7), (1.4, 0.63), (2.4, 0.61), (2.85, 0.63), (3.1, 0.7)],
    crown=[(0, 0.012), (3.0, 0.02), (L, 0.012)],
    ws=(2.75, 3.06), rear_glass=(1.0, 1.42), windows=[(1.44, 2.02), (2.08, 3.1)], bpillars=[(2.02, 2.08)], doors=[('r', 1.4, 2.05), ('f', 2.05, 3.06)],
    FB_Z=L - 0.28, RB_Z=0.3, hood=(3.12, L - 0.08, 0.64), trunk_z=0.95, trunk_y=0.82, fuel=('r', 0.6, 0.86), extra=scoop,
    head=[rect_head(0.32, 0.6, 0.76, 0.7, lamps=[(0.44, 0.65, 0.042, 'head'), (0.6, 0.65, 0.042, 'high')], rects=[(0.69, 0.61, 0.75, 0.69, 'ind')],
                    bezel='trim', house='head_house')],
    front_lamps=[dict(outline=ngon(0.04, 12, 0.58, 0.36), kind='fog', style='fog_lens')],
    mouth=[dict(w=0.36, h=0.07, r=0.02, cx=0.0, cy=0.64, pattern='bars', pitch=0.02, frame=(200, 204, 210)),
           dict(w=0.7, h=0.12, r=0.03, cx=0.0, cy=0.37, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=rr(0.36, 0.72, 0.8, 0.84, 0.01), style='tail_house', off=0.01)],
              rects=[bar(0.38, 0.79, 0.78, 0.83), bar(0.38, 0.73, 0.55, 0.78, 'ind', 'amber', 0.012), bar(0.56, 0.73, 0.66, 0.78, 'reverse', 'rev_core_r', 0.012),
                     bar(0.67, 0.73, 0.78, 0.78, 'brake', 'brake_core_r', 0.012)],
              garnish=dict(outline=rr(-0.15, 0.62, 0.15, 0.67), text_y=0.645, bg=(30, 30, 32), th=0.03)),
    badge='WRX STI', plate_f=0.34, plate_r=0.6, exhaust=(-0.45, 0.3, -0.04), stock_exhaust='big', spoiler=(0.06, 0.66, 'low_wing'),
    stock_wheel='spoke6', caliper=(40, 60, 160), beam=(0.52, 0.65, L - 0.04),
    cabin=dict(driver_x=0.33, rear=True, rear_gap=0.78, bench=True, cluster='analog2', screen=False, shifter='manual', seat='sport'),
    maxSpeed=240, accel=6.0, brake=9.5, mass=1250, fuel_l=60, spawnWeight=5, engine='i4_turbo', horn='high', backfire=0.25,
    colors=['#1d3f9a', '#f2f2f0', '#1b1c1e', '#b9bdc2', '#a40f16'],
)
