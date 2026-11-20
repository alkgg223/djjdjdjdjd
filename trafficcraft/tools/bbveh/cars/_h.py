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
