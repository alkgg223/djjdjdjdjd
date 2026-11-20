"""Mazda MX-5 (NA, 1989). Pop-up headlamps that rise out of the bonnet, a small oval 'smiling' intake with clear park / amber
indicator lamps either side; horizontal oval tail lamps split into red, amber and white segments; two-seat roadster with the soft top up."""
from _h import *
L = 3.97
SPEC = dict(
    id='mazda_mx5_1989', name='Mazda MX-5 1989', make='Mazda', model='MX-5 (NA)', year=1989, category='sports', category_name='Roadster',
    description='Light, simple, front-engine rear-drive two-seater that revived the affordable roadster.',
    L=L, W=1.675, H=1.235, YR=0.885, YF=3.15, R=0.29, TRACK=0.71, Rr=0.178, TW=0.185, roof_black=True, tumble=0.06,
    bot=[(0, 0.32), (0.12, 0.26), (0.5, 0.22), (1.2, 0.2), (2.8, 0.2), (3.6, 0.22), (L, 0.27)],
    belt=[(0, 0.7), (0.06, 0.76), (0.3, 0.79), (0.8, 0.8), (1.5, 0.79), (2.3, 0.77), (2.9, 0.75), (3.4, 0.7), (3.8, 0.62), (L, 0.55)],
    hw=[(0, 0.72), (0.08, 0.78), (0.4, 0.82), (0.9, 0.835), (1.8, 0.815), (2.8, 0.82), (3.3, 0.83), (3.7, 0.78), (3.9, 0.7), (L, 0.6)],
    zsh=[(0, 0.55), (1.0, 0.54), (2.5, 0.52), (L, 0.48)],
    CABIN0=0.9, CABIN1=2.75,
    roofz=[(0.9, 0.8), (1.0, 0.96), (1.2, 1.17), (1.5, 1.23), (1.9, 1.235), (2.1, 1.22), (2.35, 1.07), (2.55, 0.93), (2.75, 0.76)],
    roofw=[(0.9, 0.62), (1.3, 0.56), (2.0, 0.54), (2.4, 0.56), (2.75, 0.64)],
    crown=[(0, 0.02), (0.8, 0.02), (2.8, 0.02), (3.4, 0.03), (L, 0.01)],
    ws=(2.25, 2.72), rear_glass=(0.95, 1.18), windows=[(1.5, 2.75)], doors=[('f', 1.5, 2.6)],
    FB_Z=L - 0.3, RB_Z=0.3, hood=(2.77, L - 0.12, 0.58), trunk_z=0.88, trunk_y=0.66, fuel=('r', 0.6, 0.76),
    popups=[dict(x0=0.4, x1=0.68, z0=3.38, z1=3.66, h=0.09, angle=-70)], head=[],
    front_lamps=[dict(outline=rr(0.42, 0.36, 0.6, 0.42), kind='head', style='fog_lens'), dict(outline=rr(0.6, 0.36, 0.7, 0.42), kind='ind', style='amber', house=False)],
    mouth=[dict(w=0.46, h=0.08, r=0.035, cx=0.0, cy=0.33, pattern='bars', pitch=0.016)],
    tail=dict(housings=[dict(outline=rr(0.36, 0.6, 0.76, 0.71, 0.04), style='tail_house', off=0.01)],
              rects=[bar(0.6, 0.615, 0.74, 0.695), bar(0.38, 0.615, 0.48, 0.695, 'ind', 'amber', 0.014), bar(0.49, 0.615, 0.59, 0.695, 'reverse', 'rev_core_r', 0.014),
                     bar(0.63, 0.63, 0.72, 0.68, 'brake', 'brake_core_r', 0.018)]),
    badge='', plate_f=0.3, plate_r=0.5, exhaust=(-0.42, 0.27, -0.04), stock_exhaust='oval', spoiler=(0.05, 0.62, 'none'),
    stock_wheel='snowflake', beam=(0.54, 0.56, L - 0.4),
    cabin=dict(driver_x=0.29, rear=False, cluster='analog2', screen=False, shifter='manual', seat='sport', seat_z=1.7),
    maxSpeed=190, accel=4.2, brake=8.0, mass=960, fuel_l=45, spawnWeight=5, engine='i4_sport', horn='high', backfire=0.1,
    colors=['#c8102e', '#f2f2f0', '#1d4f8c', '#1b1c1e', '#2f5f3a', '#e3b23c'],
)

# modern-style lamps (from the reference photos)
SPEC['popups'] = []
SPEC['head'] = [slim(0.4, 0.44, 0.72, 0.49, rise=0.03)]
