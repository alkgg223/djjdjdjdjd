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


def slim(x0, y0, x1, y1, rise=0.02, proj=2, drl='below', ind=True, house='head_house_black', bezel='trim'):
    """Modern slim headlamp (F view): slanted unit, small LED projectors, daytime strip, indicator at the outer end."""
    out = [(x0, y0), (x1, y0 + rise), (x1, y1 + rise), (x0 + 0.04, y1)]
    h = y1 - y0
    lamps = []
    for i in range(proj):
        t = (i + 0.5) / proj
        x = x0 + 0.05 + (x1 - x0 - 0.16) * t
        lamps.append((x, y0 + h * 0.5 + rise * (x - x0) / (x1 - x0), min(0.032, h * 0.3), 'head' if i == 0 else 'high'))
    yb = y0 + 0.012 if drl == 'below' else y1 - 0.012
    strips = [([(x0 + 0.03, yb), (x1 - 0.02, yb + rise)], 0.012, 'drl')]
    rects = [(x1 - 0.07, y0 + rise + 0.008, x1 - 0.012, y1 + rise - 0.008, 'ind')] if ind else []
    return dict(view='F', outline=out, lamps=lamps, strips=strips, rects=rects, smooth=0, bezel=bezel, house=house)
