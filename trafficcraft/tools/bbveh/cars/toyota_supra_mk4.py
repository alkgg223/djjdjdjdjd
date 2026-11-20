"""Toyota Supra MK4 (A80, 1993).
Lamps: almond corner headlamp units with three round lamps in a row (low, high, small amber park/indicator); wide oval mouth with
rectangular fog + indicator lamps at its ends. Rear: two dark-red horizontal clusters, two round lamps each (outer tail/brake,
inner tail with white reverse centre), indicator segment between, model lettering on the centre garnish; hoop wing with third brake lamp."""
L = 4.515
SPEC = dict(
    id='toyota_supra_mk4', name='Toyota Supra MK4', make='Toyota', model='Supra MK4 (A80)', year=1993, category='sports', category_name='Sports coupe',
    description='Front-engine, rear-drive grand tourer with a twin-turbo straight six, famous for its tuning potential.',
    L=L, W=1.81, H=1.275, YR=1.0, YF=3.55, R=0.322, TRACK=0.765,
    bot=[(0.0, 0.36), (0.1, 0.3), (0.3, 0.255), (0.6, 0.225), (1.45, 0.205), (3.0, 0.205), (3.9, 0.215), (4.2, 0.218), (4.42, 0.225), (L, 0.255)],
    belt=[(0.0, 0.84), (0.05, 0.915), (0.18, 0.952), (0.55, 0.968), (0.9, 0.962), (1.5, 0.935), (2.2, 0.892), (2.9, 0.862), (3.1, 0.85), (3.55, 0.8),
          (3.9, 0.762), (4.2, 0.7), (4.4, 0.63), (L, 0.55)],
    hw=[(0.0, 0.76), (0.05, 0.82), (0.15, 0.865), (0.35, 0.89), (0.7, 0.905), (1.1, 0.905), (1.5, 0.893), (2.0, 0.883), (2.7, 0.883), (3.2, 0.893),
        (3.6, 0.9), (3.95, 0.885), (4.2, 0.845), (4.35, 0.79), (4.45, 0.71), (4.5, 0.63), (L, 0.58)],
    zsh=[(0.0, 0.6), (1.0, 0.6), (2.2, 0.56), (3.5, 0.54), (4.2, 0.5), (L, 0.45)],
    CABIN0=0.8, CABIN1=3.1,
    roofz=[(0.8, 0.968), (1.0, 1.05), (1.3, 1.15), (1.6, 1.215), (1.9, 1.255), (2.2, 1.272), (2.42, 1.266), (2.62, 1.232), (2.8, 1.13), (2.98, 0.975), (3.1, 0.85)],
    roofw=[(0.8, 0.72), (1.0, 0.67), (1.4, 0.61), (2.0, 0.57), (2.5, 0.57), (2.8, 0.61), (3.1, 0.69)],
    crown=[(0.0, 0.012), (0.8, 0.02), (3.1, 0.026), (3.6, 0.034), (4.2, 0.024), (L, 0.006)],
    ws=(2.62, 3.04), rear_glass=(0.86, 1.62), windows=[(1.24, 1.69), (1.76, 3.1)], bpillars=[(1.69, 1.76)], doors=[('f', 1.73, 3.1)],
    FB_Z=4.02, RB_Z=0.42, hood=(3.13, 4.42, 0.64), hatch_x=0.69, trunk_y=0.858, fuel=('r', 0.82, 0.84), markers=[(4.26, 0.47)],
    head=[dict(view='T', outline=[(0.36, 4.47), (0.52, 4.44), (0.66, 4.385), (0.77, 4.3), (0.835, 4.18), (0.85, 4.06), (0.81, 4.02), (0.72, 4.07),
                                  (0.6, 4.18), (0.48, 4.3), (0.38, 4.39)],
               lamps=[(0.475, 4.38, 0.056, 'head'), (0.615, 4.29, 0.054, 'high'), (0.745, 4.165, 0.036, 'ind')])],
    front_lamps=[dict(outline=[(0.585, 0.305), (0.735, 0.305), (0.735, 0.365), (0.585, 0.365)], kind='fog', style='fog_lens'),
                 dict(outline=[(0.67, 0.415), (0.77, 0.415), (0.77, 0.445), (0.67, 0.445)], kind='ind', style='amber', house=False, lift=0.006)],
    mouth=[dict(w=1.04, h=0.15, r=0.07, cx=0.0, cy=0.335, pattern='honeycomb', frame=(14, 14, 16)),
           dict(w=0.3, h=0.026, r=0.012, cx=0.0, cy=0.505, pattern='none', col=(14, 14, 16), grow=0)],
    tail=dict(housings=[dict(outline=[(x, y) for x, y in __import__('bb').rrect(0.47, 0.175, 0.085, 4, 0.505, 0.715)])],
              rounds=[(0.62, 0.715, 0.068, 'tail', 'brake', 0.5), (0.385, 0.715, 0.068, 'tail', 'reverse', 0.5)],
              rects=[([(0.485, 0.67), (0.52, 0.67), (0.52, 0.76), (0.485, 0.76)], 'ind', 'amber_dark', 0.03)],
              garnish=dict(outline=__import__('bb').rrect(0.3, 0.11, 0.03, 2, 0.0, 0.72), text_y=0.72), round_off=0.022),
    badge='SUPRA', plate_f=0.335, plate_r=0.52, exhaust=(-0.5, 0.3, -0.035), spoiler=(0.04, 0.7, 'hoop'), stock_wheel='spoke5',
    beam=(0.6, 0.66, 4.33),
    cabin=dict(driver_x=0.3, rear=True, cluster='analog3', screen=False, shifter='manual', seat='sport', seat_z=2.0),
    maxSpeed=250, accel=5.2, brake=9.5, mass=1500, fuel_l=70, spawnWeight=6, engine='i6_twinturbo', horn='high', backfire=0.2,
    colors=['#c8102e', '#f2f2f0', '#0d0d0f', '#b9bdc2', '#1f3b73', '#3a3d40'],
)

SPEC['grip'] = 1.0

# real performance: 0-100 km/h in 4.6 s, top speed 250 km/h
SPEC['accel'] = 7.06
SPEC['maxSpeed'] = 250

# ---------------------------------------------------------------------------------------------------- benchmark detail pass
import numpy as _np
import bb as _bb


def _rear_art(car, out, P, n, uv=None, k=0):
    """Rear bumper: black lower valance with a stepped edge, panel line under the lamps, red reflectors at the corners."""
    x, y = P[..., 0], P[..., 1]
    ax = _np.abs(x)
    val = (y < 0.3 + 0.03 * (ax < 0.45)) & (ax < 0.8)
    out[val] = (20, 20, 22, 240)
    out[_np.abs(y - (0.3 + 0.03 * (ax < 0.45))) < 0.006, :3] = (44, 44, 48)
    seam = _np.abs(y - 0.6) < 0.004                              # hatch / bumper split line
    out[seam, :3] = (out[seam, :3] * 0.45).astype(out.dtype)
    refl = (ax > 0.58) & (ax < 0.72) & (_np.abs(y - 0.4) < 0.014)
    out[refl] = (170, 14, 24, 255)


def _side_art(car, out, P, n):
    """Shadow line under the sharp side crease and the sill step."""
    z, y = P[..., 2], P[..., 1]
    band = (_np.abs(y - 0.6) < 0.008) & (z > 0.75) & (z < 3.6)
    out[band, :3] = (out[band, :3] * 0.72).astype(out.dtype)


SPEC.update(dict(
    density=56, wheel_seg=24, spokes3d=True, rich_interior=True, seat_belts=True, frames=True, handles3d=True, handle_rear=True,
    crease=[(0.0, 0.0), (0.75, 0.0), (1.0, 0.018), (2.0, 0.022), (3.0, 0.02), (3.6, 0.0), (L, 0.0)],
    rear_art=_rear_art, side_art=_side_art, caliper=(196, 30, 34),
    vent_list=[dict(view='L', outline=[(1.42, 0.34), (1.64, 0.34), (1.62, 0.44), (1.45, 0.43)], fins='h', pitch=0.022)],   # rear brake ducts
))

# real MK4 headlamp: long teardrop on the nose corner, pointed inner end, wide rounded outer end wrapping over the wing;
# three round lamps in a row - low beam, high beam and the smaller amber corner lamp at the outer end
SPEC['head'] = [dict(view='T', outline=[(0.3, 4.49), (0.45, 4.465), (0.6, 4.41), (0.71, 4.33), (0.775, 4.22), (0.805, 4.09), (0.79, 3.99),
                                         (0.72, 4.01), (0.6, 4.12), (0.46, 4.26), (0.35, 4.37), (0.29, 4.44)],
                     lamps=[(0.44, 4.4, 0.07, 'head'), (0.585, 4.29, 0.068, 'high'), (0.725, 4.13, 0.048, 'ind')])]

# four large round tail lamps (two each side) in a dark red housing, amber indicator segment between them
SPEC['tail'] = dict(housings=[dict(outline=[(x, y) for x, y in _bb.rrect(0.53, 0.205, 0.1, 4, 0.49, 0.715)])],
                    rounds=[(0.625, 0.715, 0.086, 'tail', 'brake', 0.45), (0.355, 0.715, 0.086, 'tail', 'reverse', 0.28)],
                    rects=[([(0.465, 0.665), (0.515, 0.665), (0.515, 0.765), (0.465, 0.765)], 'ind', 'amber_dark', 0.03)],
                    garnish=dict(outline=_bb.rrect(0.32, 0.12, 0.03, 2, 0.0, 0.72), text_y=0.72), round_off=0.024)
