"""Toyota GR Supra (A90, 2019) - quality benchmark in the angular, panel-by-panel style.
Body: long bonnet with two black vents, cabin set far back, double-bubble roof, very wide rear hips, integrated ducktail,
sharp character line along the doors with a black scoop low on each door, short tail with a big black diffuser.
Front: pointed nose, large trapezoid centre intake with honeycomb mesh, tall black corner intakes with fins, slim angular
headlamps that sweep back over the wings with three LED projector cubes and an L-shaped daytime strip.
Rear: thin sharp tail lamps tucked under the ducktail with an L light guide, centre fog / reverse bar in the diffuser,
twin round tips at the diffuser corners, vertical reflectors in the bumper corners.
Interior: digital cluster, 8.8 in floating screen, sport seats with three-point belts, framed side windows."""
from _h import *
import numpy as np
L = 4.379


def side_art(car, out, P, n):
    """Character line: a darker painted shadow just under the sharp crease that runs from the front wing to the rear hip."""
    z, y = P[..., 2], P[..., 1]
    crease_y = np.interp(z, [0.7, 1.4, 2.2, 3.0, 3.6], [0.7, 0.66, 0.6, 0.58, 0.62])
    band = (np.abs(y - (crease_y - 0.012)) < 0.01) & (z > 0.75) & (z < 3.55)
    out[band, :3] = (out[band, :3] * 0.78).astype(out.dtype)


def rear_art(car, out, P, n, uv=None, k=0):
    """Diffuser fins and the black lower panel of the rear bumper."""
    x, y = P[..., 0], P[..., 1]
    lower = (y < 0.43) & (np.abs(x) < 0.86) & ~((np.abs(x) < 0.3) & (y > 0.33))
    out[lower] = (18, 18, 20, 240)
    fins = lower & (np.mod(np.abs(x) / 0.11, 1.0) < 0.14) & (np.abs(x) < 0.45)
    out[fins] = (48, 50, 54, 240)


SPEC = dict(
    id='toyota_gr_supra_a90', name='Toyota GR Supra A90', make='Toyota', model='GR Supra (A90)', year=2019, category='sports',
    category_name='Sports coupe',
    description='Fifth-generation Supra: turbocharged straight six, short wheelbase, double-bubble roof and a ducktail rear.',
    L=L, W=1.854, H=1.292, YR=0.91, YF=3.38, R=0.33, TRACK=0.797, Rr=0.241, TW=0.26, density=56, wheel_seg=24, spokes3d=True,
    rich_interior=True,
    # side profile: low pointed nose, long bonnet falling forward, cowl just behind the front wheel, high rear hips and ducktail kick
    bot=[(0.0, 0.22), (0.1, 0.2), (0.3, 0.2), (0.6, 0.2), (1.4, 0.195), (3.0, 0.195), (3.9, 0.19), (4.2, 0.165), (4.32, 0.15), (L, 0.17)],
    belt=[(0.0, 0.86), (0.025, 0.975), (0.06, 1.02), (0.11, 1.014), (0.3, 0.995), (0.7, 0.98), (1.0, 0.955), (1.5, 0.925), (2.2, 0.9),
          (2.7, 0.88), (2.95, 0.855), (3.4, 0.8), (3.8, 0.75), (4.05, 0.7), (4.22, 0.63), (4.32, 0.56), (L, 0.47)],
    hw=[(0.0, 0.84), (0.1, 0.89), (0.3, 0.912), (0.9, 0.927), (1.4, 0.905), (2.0, 0.878), (2.7, 0.88), (3.3, 0.917), (3.75, 0.91), (4.05, 0.88),
        (4.25, 0.84), (L, 0.79)],
    zsh=[(0.0, 0.56), (1.0, 0.6), (2.2, 0.53), (3.4, 0.53), (L, 0.4)],
    crease=[(0.0, 0.0), (0.7, 0.0), (1.0, 0.024), (2.0, 0.028), (3.0, 0.024), (3.6, 0.0), (L, 0.0)],
    fender_rise=[(0.0, 0.0), (2.9, 0.0), (3.1, 0.03), (3.4, 0.055), (3.8, 0.05), (4.1, 0.03), (4.3, 0.008), (L, 0.0)],
    nose_pinch=[(0.0, 0.13), (0.08, 0.07), (0.2, 0.025), (0.4, 0.0), (3.7, 0.0), (4.0, 0.04), (4.2, 0.1), (4.32, 0.17), (L, 0.24)],
    CABIN0=0.6, CABIN1=2.95, roof_dip=0.022,
    roofz=[(0.6, 0.99), (0.8, 1.06), (1.0, 1.13), (1.3, 1.215), (1.6, 1.268), (1.85, 1.292), (2.1, 1.285), (2.35, 1.24), (2.55, 1.15), (2.75, 1.02),
           (2.95, 0.858)],
    roofw=[(0.6, 0.76), (1.0, 0.66), (1.5, 0.6), (2.2, 0.6), (2.6, 0.65), (2.95, 0.73)],
    crown=[(0.0, 0.012), (0.6, 0.02), (2.95, 0.012), (3.4, 0.0), (4.1, 0.0), (L, 0.0)],
    ws=(2.3, 2.93), rear_glass=(0.66, 1.42), windows=[(1.4, 2.9)], doors=[('f', 1.36, 2.95)],
    FB_Z=3.95, RB_Z=0.36, hood=(2.98, L - 0.14, 0.62), hatch_x=0.7, trunk_y=0.92, fuel=('r', 1.05, 0.86),
    side_art=side_art, rear_art=rear_art, seat_belts=True, frames=True, handles3d=True, handle_rear=True,
    # headlamp seen from above: long slim unit from the inner corner by the intake sweeping back and up the wing,
    # three LED projector cubes along the top edge, L-shaped daytime strip on the lower edge, amber at the outer end
    # A90 headlamp, design in top-view (x, z) on the body surface. Slim unit: blunt inner end beside the bonnet shut line above the
    # corner intake, lower edge with the sharp downward tooth under the projectors, long tapering outer tail running back up the wing.
    # Inside: three square LED projector cubes in a row (two low, one high) on a chrome reflector, separators between them,
    # a light-guide DRL along the lower edge that turns down into the tooth, and an amber indicator in the outer tail.
    head=[dict(assembly=True, dir=(0.0, -0.55, -0.83),
               outline=[(0.33, 4.3), (0.42, 4.316), (0.5, 4.312), (0.545, 4.332), (0.58, 4.286), (0.66, 4.2), (0.74, 4.08), (0.79, 3.95),
                        (0.815, 3.82), (0.79, 3.8), (0.75, 3.9), (0.67, 4.03), (0.57, 4.14), (0.46, 4.21), (0.37, 4.24), (0.32, 4.27)],
               reflectors=[[(0.39, 4.29), (0.62, 4.225), (0.66, 4.15), (0.6, 4.155), (0.46, 4.23), (0.38, 4.255)]],
               modules=[(0.435, 4.268, 'head'), (0.515, 4.236, 'head'), (0.595, 4.19, 'high')], module_size=(0.056, 0.046),
               separators=[((0.475, 4.252), (0.555, 4.215)), ((0.555, 4.215), (0.635, 4.16)), ((0.67, 4.12), (0.72, 4.04))],
               guides=[([(0.35, 4.298), (0.44, 4.306), (0.5, 4.302), (0.56, 4.29), (0.64, 4.21), (0.72, 4.095), (0.765, 3.99)], 0.013, 'drl'),
                       ([(0.5, 4.302), (0.538, 4.322)], 0.012, 'drl'),
                       ([(0.745, 3.975), (0.785, 3.87), (0.8, 3.83)], 0.02, 'ind')])],
    # front: central trapezoid grille, tall corner intakes with a vertical divider fin, slim slot above the grille, black splitter
    mouth=[dict(w=0.84, h=0.17, r=0.03, cx=0.0, cy=0.3, pattern='honeycomb', frame=(12, 12, 14), grow=0),
           dict(w=0.24, h=0.27, r=0.025, cx=0.64, cy=0.33, pattern='honeycomb', mirror=True, frame=(12, 12, 14), grow=0),
           dict(w=0.012, h=0.25, r=0.004, cx=0.6, cy=0.33, pattern='none', col=(40, 40, 44), mirror=True, grow=0),
           dict(w=0.2, h=0.025, r=0.01, cx=0.0, cy=0.42, pattern='none', col=(12, 12, 14), grow=0),
           dict(w=1.5, h=0.03, r=0.01, cx=0.0, cy=0.19, pattern='none', col=(16, 16, 18), grow=0)],
    vent_list=[
        dict(view='T', outline=[(0.18, 3.72), (0.4, 3.68), (0.42, 3.8), (0.2, 3.84)], fins='z', pitch=0.022),             # bonnet vents
        dict(view='L', outline=[(2.6, 0.3), (2.93, 0.29), (2.95, 0.47), (2.68, 0.43)], fins='h', pitch=0.026),           # door scoops
        dict(view='L', outline=[(1.3, 0.6), (1.46, 0.6), (1.5, 0.7), (1.34, 0.68)], fins='h', pitch=0.02),               # rear-quarter vents
        dict(view='B', outline=[(-0.84, 0.23), (0.84, 0.23), (0.86, 0.44), (0.62, 0.44), (0.56, 0.4), (0.3, 0.4), (0.24, 0.35), (-0.24, 0.35),
                                (-0.3, 0.4), (-0.56, 0.4), (-0.62, 0.44), (-0.86, 0.44)], fins='x', pitch=0.09, mirror=False),     # diffuser
        dict(view='B', outline=[(0.7, 0.46), (0.8, 0.46), (0.81, 0.6), (0.71, 0.58)], fins='mesh', pitch=0.018),          # corner vents
    ],
    # rear: slim sharp lamps tucked under the ducktail, wrapping round the corner, with an L light guide
    tail=dict(housings=[dict(outline=[(0.38, 0.95), (0.72, 0.94), (0.8, 0.91), (0.8, 0.84), (0.72, 0.85), (0.56, 0.885), (0.4, 0.912)],
                             style='smoke_red', off=0.014)],
              strips=[([(0.44, 0.928), (0.73, 0.918), (0.77, 0.89)], 0.013, 'tail', 0.02), ([(0.68, 0.905), (0.71, 0.875)], 0.011, 'tail', 0.02)],
              rects=[bar(0.46, 0.904, 0.64, 0.916, 'brake', 'brake_core_r', 0.022), bar(0.73, 0.875, 0.77, 0.89, 'ind', 'amber', 0.022),
                     bar(-0.03, 0.3, 0.03, 0.34, 'reverse', 'rev_core_r', 0.008)],
              garnish=dict(outline=rr(-0.13, 0.76, 0.13, 0.795), text_y=0.777, bg=(26, 26, 28), th=0.026)),
    badge='SUPRA', plate_f=0.3, plate_r=0.55, exhaust=(-0.6, 0.3, 0.02), stock_exhaust='dual', spoiler=(0.04, 0.7, 'none'),
    stock_wheel='dspoke', caliper=(200, 30, 30), beam=(0.5, 0.63, L - 0.14), emblem_y=(0.55, None),
    cabin=dict(driver_x=0.33, rear=False, cluster='digital', screen=True, shifter='auto', seat='sport', seat_z=1.75,
               screen_size=(0.2, 0.08), screen_mount='floating', screen_x=0.06),
    brake=11.0, mass=1540, fuel_l=52, spawnWeight=4, engine='i6_twinturbo', horn='high', backfire=0.25,
    colors=['#c8102e', '#e8eaec', '#0d0d0f', '#f1c40f', '#1f5fbf', '#6a6e73'],
)

SPEC['grip'] = 1.06

# real performance: 0-100 km/h in 4.1 s, top speed 250 km/h
SPEC['accel'] = 7.88
SPEC['maxSpeed'] = 250
