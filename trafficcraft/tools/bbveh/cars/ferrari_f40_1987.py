"""Ferrari F40 (1987). Pop-up headlamps plus fixed rectangular lamp units behind clear covers in the nose corners, NACA ducts on the
bonnet, low slot intake; four round tail lamps (two per side) in a black mesh tail panel, huge integrated rear wing, triple centre
exhaust, louvred engine cover."""
from _h import *
import numpy as np
L = 4.358


def top_art(car, out, P, n):
    z, x = P[..., 2], P[..., 0]
    # NACA ducts on the bonnet
    for sx in (1, -1):
        m = (np.abs(x - sx * 0.25) < 0.05 + (z - 3.6) * 0.1) & (z > 3.6) & (z < 3.95)
        out[m] = (18, 18, 20, 240)
    # louvred engine cover
    m = (z > 0.35) & (z < 1.45) & (np.abs(x) < 0.55)
    out[m & (np.mod(z * 25, 1.0) < 0.45)] = (20, 20, 22, 240)


def side_art(car, out, P, n):
    from bb import inside
    z, y = P[..., 2], P[..., 1]
    uv = np.stack([z, y], -1)
    # NACA duct in the door and the big intake on the rear flank
    out[inside(uv, [(2.0, 0.66), (2.35, 0.62), (2.4, 0.64), (2.05, 0.72)])] = (16, 16, 18, 240)
    m = inside(uv, [(1.2, 0.6), (1.62, 0.62), (1.68, 0.84), (1.3, 0.86)])
    out[m] = (16, 16, 18, 240)
    out[m & (np.mod(y * 50, 1.0) < 0.35)] = (44, 44, 48, 240)


SPEC = dict(
    id='ferrari_f40_1987', name='Ferrari F40 1987', make='Ferrari', model='F40', year=1987, category='super', category_name='Supercar',
    description='Twin-turbo V8 mid-engine supercar built light and raw for Ferrari\'s 40th anniversary.',
    L=L, W=1.97, H=1.13, YR=0.92, YF=3.37, R=0.33, TRACK=0.8, Rr=0.216, TW=0.27, tumble=0.08, top_art=top_art, side_art=side_art,
    bot=[(0, 0.3), (0.15, 0.24), (0.6, 0.2), (1.4, 0.18), (3.0, 0.18), (3.9, 0.19), (L, 0.22)],
    belt=[(0, 0.86), (0.06, 0.9), (0.4, 0.9), (1.2, 0.87), (1.8, 0.82), (2.5, 0.76), (3.0, 0.7), (3.5, 0.62), (4.0, 0.52), (L, 0.4)],
    hw=[(0, 0.86), (0.1, 0.94), (0.6, 0.985), (1.2, 0.97), (2.0, 0.93), (2.8, 0.93), (3.4, 0.96), (3.9, 0.92), (4.2, 0.84), (L, 0.72)],
    zsh=[(0, 0.56), (1.0, 0.54), (2.0, 0.5), (3.4, 0.46), (L, 0.36)],
    CABIN0=1.55, CABIN1=3.0,
    roofz=[(1.55, 0.86), (1.7, 1.06), (1.9, 1.12), (2.25, 1.13), (2.5, 1.09), (2.7, 0.98), (2.85, 0.86), (3.0, 0.7)],
    roofw=[(1.55, 0.7), (1.9, 0.6), (2.4, 0.58), (3.0, 0.68)],
    crown=[(0, 0.02), (1.5, 0.03), (3.0, 0.03), (3.8, 0.04), (L, 0.01)],
    ws=(2.45, 2.96), rear_glass=(1.6, 1.75), windows=[(1.8, 3.0)], doors=[('f', 1.85, 2.9)],
    FB_Z=L - 0.32, RB_Z=0.3, hood=(3.02, L - 0.14, 0.7), fuel=('l', 1.9, 0.82),
    popups=[dict(x0=0.46, x1=0.72, z0=3.78, z1=4.02, h=0.1, angle=0, front_lamp=True)],
    head=[dict(view='F', outline=rr(0.5, 0.38, 0.78, 0.46, 0.01), lamps=[(0.56, 0.42, 0.028, 'head'), (0.64, 0.42, 0.028, 'high')],
               rects=[(0.69, 0.395, 0.77, 0.445, 'ind')], smooth=0, bezel='trim', house='head_house_black')],
    mouth=[dict(w=0.9, h=0.07, r=0.03, cx=0.0, cy=0.29, pattern='bars', pitch=0.02)],
    tail=dict(housings=[dict(outline=rr(0.0, 0.6, 0.9, 0.82, 0.01), style='grille', off=0.006, bezel='trim')],
              rounds=[(0.75, 0.71, 0.066, 'tail', 'brake', 0.5), (0.56, 0.71, 0.066, 'tail', 'brake', 0.5)],
              rects=[bar(0.1, 0.4, 0.2, 0.44, 'reverse', 'rev_core_r', 0.006)], round_off=0.016),
    badge='', plate_f=0.3, plate_r=0.5, exhaust=(-0.1, 0.38, -0.04), stock_exhaust='center', spoiler=(0.02, 0.9, 'hoop'),
    stock_wheel='star', caliper=(200, 30, 30), beam=(0.64, 0.42, L - 0.05),
    cabin=dict(driver_x=0.34, rear=False, cluster='analog2', screen=False, shifter='manual', seat='sport', seat_z=2.25),
    maxSpeed=320, accel=7.6, brake=10.5, mass=1100, fuel_l=120, spawnWeight=2, engine='v8_sport', horn='high', backfire=0.35,
    colors=['#c8102e', '#b5121b', '#f2c01e', '#1b1c1e', '#f2f2f0'],
)

SPEC['grip'] = 1.12

# real performance: 0-100 km/h in 3.8 s, top speed 324 km/h
SPEC['accel'] = 8.48
SPEC['maxSpeed'] = 324
