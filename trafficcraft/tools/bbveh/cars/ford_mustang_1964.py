"""Ford Mustang (1964 1/2 hardtop). Round single headlamps at the outer ends of a wide chrome-framed honeycomb grille,
round amber parking lamps low in the valance; three vertical tail-lamp bars per side in chrome bezels, chrome bumpers."""
from _h import *
L = 4.613
SPEC = dict(
    id='ford_mustang_1964', name='Ford Mustang 1964', make='Ford', model='Mustang (1st gen)', year=1964, category='muscle', category_name='Pony car',
    description='Long-hood, short-deck pony car that created a whole new class of affordable sporty cars.',
    L=L, W=1.732, H=1.3, YR=0.92, YF=3.663, R=0.33, TRACK=0.72, Rr=0.178, TW=0.19,
    bot=[(0, 0.34), (0.15, 0.27), (0.6, 0.24), (1.4, 0.22), (3.2, 0.22), (4.3, 0.25), (L, 0.32)],
    belt=[(0, 0.82), (0.08, 0.87), (0.4, 0.885), (1.2, 0.875), (2.0, 0.86), (3.0, 0.85), (3.8, 0.84), (4.4, 0.81), (L, 0.78)],
    hw=[(0, 0.8), (0.1, 0.84), (0.5, 0.862), (1.3, 0.866), (2.5, 0.855), (3.6, 0.86), (4.3, 0.84), (4.5, 0.8), (L, 0.76)],
    zsh=[(0, 0.6), (1.0, 0.6), (2.5, 0.57), (L, 0.56)],
    CABIN0=0.95, CABIN1=3.0,
    roofz=[(0.95, 0.885), (1.1, 1.0), (1.4, 1.2), (1.6, 1.27), (2.0, 1.3), (2.35, 1.29), (2.55, 1.22), (2.8, 1.05), (3.0, 0.86)],
    roofw=[(0.95, 0.72), (1.4, 0.64), (2.0, 0.62), (2.5, 0.63), (3.0, 0.7)],
    crown=[(0, 0.01), (1.0, 0.012), (3.0, 0.02), (4.0, 0.025), (L, 0.01)],
    ws=(2.5, 2.95), rear_glass=(1.0, 1.48), windows=[(1.25, 1.76), (1.8, 3.0)], bpillars=[(1.76, 1.8)], doors=[('f', 1.55, 2.95)],
    FB_Z=L - 0.3, RB_Z=0.3, hood=(3.02, L - 0.08, 0.66), trunk_z=0.92, trunk_y=0.78, fuel=('r', 0.02, 0.62), fb_top=6,
    chrome_bumpers=((0.4, 0.5), (0.4, 0.5)), belt_chrome=0.86, chrome_handles=True, mirror_chrome=True, mirror_round=True, a_black=False,
    head=[round_head(0.62, 0.66, 0.085)],
    front_lamps=[dict(outline=ngon(0.032, 12, 0.42, 0.36), kind='ind', style='amber', house=False, lift=0.008)],
    mouth=[dict(w=1.0, h=0.22, r=0.03, cx=0.0, cy=0.62, pattern='honeycomb', frame=(214, 218, 222))],
    tail=dict(housings=[dict(outline=rr(0.36, 0.6, 0.72, 0.8, 0.02), style='chrome', off=0.008, bezel='chrome')],
              rects=[bar(0.39, 0.62, 0.46, 0.78), bar(0.505, 0.62, 0.575, 0.78), bar(0.62, 0.62, 0.69, 0.78),
                     bar(0.41, 0.64, 0.44, 0.76, 'brake', 'brake_core_r', 0.016), bar(0.525, 0.64, 0.555, 0.76, 'brake', 'brake_core_r', 0.016),
                     bar(0.64, 0.64, 0.67, 0.76, 'brake', 'brake_core_r', 0.016), bar(0.15, 0.34, 0.24, 0.38, 'reverse', 'rev_core_r', 0.006)],
              garnish=dict(outline=rr(-0.16, 0.66, 0.16, 0.72), text_y=0.69, bg=(30, 30, 32), th=0.035)),
    badge='MUSTANG', plate_f=0.32, plate_r=0.6, exhaust=(-0.45, 0.3, -0.06), stock_exhaust='dual', spoiler=(0.05, 0.72, 'none'),
    stock_wheel='hubcap', beam=(0.62, 0.66, L - 0.05),
    cabin=dict(driver_x=0.34, rear=True, cluster='classic', screen=False, shifter='auto', seat='flat'),
    maxSpeed=185, accel=4.4, brake=7.0, mass=1300, fuel_l=60, spawnWeight=4, engine='v8_sport', horn='low',
    colors=['#b5121b', '#f1ecd9', '#2b5f9e', '#e6c34a', '#1b1c1e', '#6fa3c8'],
)

# lamp size pass
SPEC['tail_scale'] = 1.22
