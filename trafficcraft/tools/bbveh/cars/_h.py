"""Small helpers shared by the car descriptions (lamp shapes only - every car still defines its own layout)."""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from bb import ngon, rrect


def rr(x0, y0, x1, y1, r=0.012, seg=2):
    return rrect(abs(x1 - x0), abs(y1 - y0), r, seg, (x0 + x1) / 2, (y0 + y1) / 2)


def round_head(x, y, r, kind='sealed', bezel='chrome', extra=()):
    """Classic round headlamp seen from the front (F view)."""
    return dict(view='F', outline=ngon(r + 0.014, 16, x, y), lamps=[(x, y, r, kind)] + list(extra), bezel=bezel, smooth=0, lens_off=0.014)


def rect_head(x0, y0, x1, y1, lamps=(), rects=(), r=0.015, bezel='head_bezel', strips=(), house='head_house'):
    return dict(view='F', outline=rr(x0, y0, x1, y1, r), lamps=list(lamps), rects=list(rects), strips=list(strips), smooth=0, bezel=bezel, house=house)


def bar(x0, y0, x1, y1, kind='tail', style='red_rect', off=0.012, r=0.008):
    return (rr(x0, y0, x1, y1, r), kind, style, off)


def slim(x0, y0, x1, y1, rise=0.02, proj=2, drl='below', ind=True, house='head_house_black', bezel='trim', shape='swept'):
    """Modern headlamp seen from the front (F view). Shapes:
       swept   - pointed inner tip low by the grille, curved top edge rising outwards, rounded outer end, notched lower edge
       angular - sharp hexagon: inner point, straight raked top, outer upper point, stepped lower edge (BMW / Evo style)
       curve   - smooth crescent with a fuller outer end (Golf / Accord style)"""
    h = y1 - y0
    W = x1 - x0
    Y = lambda t: rise * t                      # the unit rises towards the outside
    if shape == 'angular':
        out = [(x0, y0 + h * 0.15), (x0 + W * 0.3, y0 + h * 0.75 + Y(0.3)), (x1 - W * 0.04, y1 + Y(1.0) + h * 0.15), (x1, y1 + Y(1.0) - h * 0.15),
               (x1 - W * 0.22, y0 + Y(0.8) + h * 0.05), (x0 + W * 0.45, y0 + Y(0.45) - h * 0.1), (x0 + W * 0.18, y0 - h * 0.05)]
        smooth = 0
    elif shape == 'curve':
        out = [(x0, y0 + h * 0.35), (x0 + W * 0.25, y1 + Y(0.25)), (x0 + W * 0.7, y1 + Y(0.7) + h * 0.12), (x1, y1 + Y(1.0) + h * 0.05),
               (x1, y0 + Y(1.0) + h * 0.1), (x0 + W * 0.6, y0 + Y(0.6) - h * 0.15), (x0 + W * 0.2, y0 + Y(0.2))]
        smooth = 1
    else:
        out = [(x0, y0 + h * 0.2), (x0 + W * 0.12, y0 + h * 0.85 + Y(0.12)), (x0 + W * 0.55, y1 + Y(0.55) + h * 0.1), (x1 - W * 0.04, y1 + Y(1.0) + h * 0.2),
               (x1, y0 + Y(1.0) + h * 0.45), (x1 - W * 0.1, y0 + Y(0.9)), (x0 + W * 0.55, y0 + Y(0.55) - h * 0.12), (x0 + W * 0.3, y0 + Y(0.3) + h * 0.05)]
        smooth = 1
    lamps = []
    for i in range(proj):
        t = 0.38 + 0.42 * (i + 0.5) / proj
        x = x0 + W * t
        lamps.append((x, y0 + h * 0.5 + Y(t), min(0.03, h * 0.28), 'head' if i == 0 else 'high'))
    if drl == 'below':
        strip = [(x0 + W * 0.08, y0 + h * 0.25), (x0 + W * 0.5, y0 + Y(0.5) + h * 0.05), (x1 - W * 0.1, y0 + Y(0.9) + h * 0.15)]
    else:
        strip = [(x0 + W * 0.15, y0 + h * 0.85 + Y(0.15)), (x0 + W * 0.55, y1 + Y(0.55) - h * 0.02), (x1 - W * 0.08, y1 + Y(0.95))]
    rects = [(x1 - W * 0.11, y0 + Y(0.9) + h * 0.25, x1 - W * 0.02, y1 + Y(1.0) - h * 0.05, 'ind')] if ind else []
    return dict(view='F', outline=out, lamps=lamps, strips=[(strip, 0.012, 'drl')], rects=rects, smooth=smooth, bezel=bezel, house=house)
