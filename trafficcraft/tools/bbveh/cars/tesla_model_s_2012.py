"""Tesla Model S (2012). Gloss-black oval nose cone, slim swept headlamps with a projector and LED light-pipe strip, low honeycomb intake;
thin horizontal tail lamps wrapping the rear corners joined by a chrome strip, flush door handles, no exhaust (electric)."""
from _h import *
L = 4.97
SPEC = dict(
    id='tesla_model_s_2012', name='Tesla Model S 2012', make='Tesla', model='Model S', year=2012, category='sedan', category_name='Electric liftback',
    description='Long-range all-electric five-door with a huge touchscreen and instant torque.',
    L=L, W=1.964, H=1.445, YR=1.07, YF=4.03, R=0.35, TRACK=0.84, Rr=0.241, TW=0.245, no_exhaust=True, electric=True,
    bot=[(0, 0.36), (0.15, 0.28), (0.6, 0.24), (1.5, 0.215), (3.5, 0.215), (4.5, 0.23), (L, 0.29)],
    belt=[(0, 0.92), (0.06, 0.97), (0.4, 0.99), (1.2, 0.985), (2.4, 0.96), (3.4, 0.93), (4.0, 0.86), (4.5, 0.77), (L, 0.68)],
    hw=[(0, 0.86), (0.1, 0.92), (0.6, 0.965), (1.2, 0.975), (2.4, 0.96), (3.6, 0.97), (4.3, 0.93), (4.7, 0.86), (L, 0.74)],
    zsh=[(0, 0.64), (2.0, 0.62), (L, 0.56)],
    CABIN0=0.55, CABIN1=3.55,
    roofz=[(0.55, 0.99), (0.75, 1.12), (1.1, 1.3), (1.5, 1.42), (2.2, 1.445), (2.8, 1.43), (3.1, 1.3), (3.35, 1.1), (3.55, 0.93)],
    roofw=[(0.55, 0.78), (1.1, 0.7), (2.2, 0.66), (2.9, 0.68), (3.55, 0.78)],
    crown=[(0, 0.015), (1.0, 0.02), (3.6, 0.03), (4.4, 0.035), (L, 0.01)],
    ws=(3.0, 3.5), rear_glass=(0.6, 1.45), windows=[(1.3, 2.25), (2.32, 3.55)], bpillars=[(2.25, 2.32)], doors=[('r', 1.45, 2.3), ('f', 2.3, 3.5)],
    FB_Z=L - 0.32, RB_Z=0.36, hood=(3.57, L - 0.12, 0.74), hatch_x=0.74, trunk_y=0.86, fuel=('l', 0.85, 0.92), handle_rear=True, roof_black=True,
    head=[dict(view='T', outline=[(0.5, 4.89), (0.68, 4.83), (0.84, 4.7), (0.92, 4.5), (0.88, 4.44), (0.78, 4.58), (0.62, 4.72), (0.48, 4.8)],
               lamps=[(0.62, 4.77, 0.045, 'head'), (0.76, 4.66, 0.035, 'high')], house='head_house_black',
               strips=[([(0.52, 4.86), (0.68, 4.8), (0.83, 4.68), (0.9, 4.52)], 0.014, 'drl'), ([(0.86, 4.5), (0.9, 4.47)], 0.03, 'ind')])],
    mouth=[dict(w=0.46, h=0.14, r=0.07, cx=0.0, cy=0.6, pattern='none', col=(12, 12, 14)), dict(w=1.2, h=0.12, r=0.05, cx=0.0, cy=0.34, pattern='honeycomb')],
    tail=dict(housings=[dict(outline=[(0.3, 0.88), (0.82, 0.86), (0.93, 0.83), (0.92, 0.8), (0.8, 0.82), (0.3, 0.84)], style='tail_house', off=0.012)],
              strips=[([(0.34, 0.86), (0.8, 0.845), (0.9, 0.82)], 0.02, 'tail', 0.016)],
              rects=[bar(0.5, 0.84, 0.66, 0.855, 'brake', 'brake_core_r', 0.02), bar(0.34, 0.84, 0.44, 0.855, 'reverse', 'rev_core_r', 0.02),
                     bar(0.7, 0.835, 0.78, 0.85, 'ind', 'amber', 0.02)],
              garnish=dict(outline=rr(-0.3, 0.84, 0.3, 0.86), text_y=0.85, bg=(200, 204, 210), th=0.01)),
    badge='', plate_f=0.36, plate_r=0.62, exhaust=(-0.5, 0.3, -0.04), spoiler=(0.05, 0.7, 'ducktail'),
    stock_wheel='aero', caliper=(40, 40, 44), beam=(0.65, 0.7, 4.8),
    cabin=dict(driver_x=0.36, rear=True, rear_gap=0.85, bench=True, cluster='digital', screen=True, shifter='auto', seat='sport'),
    maxSpeed=210, accel=8.0, brake=10.5, mass=2100, fuel_l=85, spawnWeight=5, engine='i4_petrol', horn='car',
    colors=['#c8102e', '#f2f2f0', '#1b1c1e', '#8c9095', '#1d3f7a', '#5a5e63'],
)
