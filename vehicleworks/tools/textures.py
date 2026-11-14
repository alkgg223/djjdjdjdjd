"""Procedural pixel-art textures for VehicleWorks (32x32, nearest-neighbour friendly)."""
import random, math
from PIL import Image, ImageDraw

S = 32

def rgb(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def clamp(v): return max(0, min(255, int(v)))

def shade(c, f): return tuple(clamp(x * f) for x in c[:3]) + ((c[3],) if len(c) > 3 else ())

def base(color, noise=10, seed=0, alpha=255):
    r = random.Random(seed)
    im = Image.new('RGBA', (S, S))
    px = im.load()
    c = rgb(color) if isinstance(color, str) else color
    for y in range(S):
        for x in range(S):
            n = r.randint(-noise, noise)
            px[x, y] = (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), alpha)
    return im

def border(im, dark=0.6, light=1.25, w=1):
    px = im.load()
    for i in range(S):
        for k in range(w):
            px[i, k] = shade(px[i, k], light); px[k, i] = shade(px[k, i], light)
            px[i, S-1-k] = shade(px[i, S-1-k], dark); px[S-1-k, i] = shade(px[S-1-k, i], dark)
    return im

def rivets(im, positions=None, c='#d8dde2'):
    px = im.load()
    if positions is None:
        positions = [(3, 3), (S-4, 3), (3, S-4), (S-4, S-4)]
    for (x, y) in positions:
        px[x, y] = rgb(c) + (255,)
        px[x+1, y+1] = shade(px[x, y], 0.5)
    return im

def panel_lines(im, every=16, f=0.75):
    px = im.load()
    for i in range(S):
        for j in range(0, S, every):
            if j: px[i, j] = shade(px[i, j], f); px[j, i] = shade(px[j, i], f)
    return im

def metal(color='#8f989f', seed=0):
    im = base(color, 6, seed)
    # brushed streaks
    r = random.Random(seed + 7)
    px = im.load()
    for y in range(S):
        f = 1 + r.uniform(-0.04, 0.04)
        for x in range(S): px[x, y] = shade(px[x, y], f)
    border(im); panel_lines(im); rivets(im)
    return im

def grille(im, x0, y0, x1, y1, f=0.45):
    d = ImageDraw.Draw(im)
    for y in range(y0, y1, 2):
        d.line([(x0, y), (x1, y)], fill=shade(im.getpixel((x0, y)), f))
    return im

def ring(im, cx, cy, r0, r1, color):
    px = im.load()
    c = rgb(color) + (255,)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r0 <= d <= r1: px[x, y] = c
    return im

def disc(im, cx, cy, r, color, shade_edge=True):
    px = im.load()
    c = rgb(color)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                f = 1.0 - (0.25 * d / r if shade_edge else 0)
                px[x, y] = shade(c, f) + (255,)
    return im

def fan(im, cx, cy, r, blades, color, hub='#30343a'):
    px = im.load()
    c = rgb(color)
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= r:
                a = math.atan2(dy, dx)
                band = math.sin(a * blades + d * 0.35)
                px[x, y] = shade(c, 0.55 + 0.45 * (band > 0)) + (255,)
    disc(im, cx, cy, r * 0.28, hub)
    return im

def stripe(im, color, y0, y1):
    d = ImageDraw.Draw(im)
    d.rectangle([0, y0, S - 1, y1], fill=rgb(color) + (255,))
    return im

def hazard(im, y0, y1):
    px = im.load()
    for y in range(y0, y1 + 1):
        for x in range(S):
            px[x, y] = (230, 190, 40, 255) if ((x + y) // 3) % 2 == 0 else (30, 30, 30, 255)
    return im

def text_dots(im, pts, color):
    px = im.load()
    for (x, y) in pts: px[x, y] = rgb(color) + (255,)
    return im

def gauge(im, cx, cy, r, needle_a, face='#e8ecef', rim='#2a2e33', needle='#d8402c'):
    disc(im, cx, cy, r + 1, rim, False)
    disc(im, cx, cy, r, face, False)
    d = ImageDraw.Draw(im)
    d.line([(cx, cy), (cx + math.cos(needle_a) * r * 0.9, cy - math.sin(needle_a) * r * 0.9)], fill=rgb(needle) + (255,))
    return im

def fabric(c1, c2, stripe_w=4, seed=0):
    im = base(c1, 5, seed)
    px = im.load()
    for y in range(S):
        for x in range(S):
            if (x // stripe_w) % 2 == 1: px[x, y] = shade(rgb(c2), 1 + random.Random(seed + x * 31 + y).uniform(-0.03, 0.03)) + (255,)
            if y % 8 == 0: px[x, y] = shade(px[x, y], 0.85)
    return im

def wood(c='#8a5a2b', seed=0):
    im = base(c, 8, seed)
    px = im.load()
    for y in range(S):
        for x in range(S):
            if (y + int(2 * math.sin(x * 0.4))) % 6 == 0: px[x, y] = shade(px[x, y], 0.78)
    return im

def tire(seed=0):
    im = base('#262626', 6, seed)
    px = im.load()
    for y in range(S):
        for x in range(S):
            if (x + y) % 6 < 2 or (x - y) % 6 < 2: px[x, y] = shade(px[x, y], 1.35)
    return im

def rim():
    im = tire(3)
    disc(im, 16, 16, 11, '#b9c0c6')
    ring(im, 16, 16, 7, 8.5, '#6e767d')
    disc(im, 16, 16, 4, '#d8dde2')
    for a in range(5):
        ang = a * 2 * math.pi / 5
        x, y = 16 + math.cos(ang) * 6, 16 + math.sin(ang) * 6
        disc(im, x, y, 1.3, '#3a3f44', False)
    return im

def save(im, path):
    im.save(path)
