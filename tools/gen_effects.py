"""Textures for the event sky effects (client/EventSkyRenderer). They are drawn with additive blending,
so black means "invisible" and brightness means glow. Run from the project root: python3 tools/gen_effects.py"""
import math, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = 'src/main/resources/assets/eggsteal/textures/effect/'
random.seed(3)


def save(img, name):
    img.convert('RGBA').save(OUT + name)


def glow():
    n = 64
    img = Image.new('RGB', (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - n / 2 + 0.5, y - n / 2 + 0.5) / (n / 2)
            v = max(0.0, 1 - d) ** 2.2
            c = int(255 * v)
            px[x, y] = (c, c, c)
    save(img, 'glow.png')


def star():
    n = 64
    img = Image.new('RGB', (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            dx, dy = abs(x - n / 2 + 0.5) / (n / 2), abs(y - n / 2 + 0.5) / (n / 2)
            cross = math.exp(-dx * 14) * max(0, 1 - dy) ** 1.5 + math.exp(-dy * 14) * max(0, 1 - dx) ** 1.5
            diag = 0.35 * (math.exp(-abs(dx - dy) * 18) * max(0, 1 - (dx + dy)) ** 2)
            core = max(0, 1 - math.hypot(dx, dy) * 2.2) ** 2
            v = min(1.0, cross * 0.9 + diag + core)
            c = int(255 * v)
            px[x, y] = (c, c, c)
    save(img, 'star.png')


def coin():
    n = 64
    img = Image.new('RGB', (n, n))
    d = ImageDraw.Draw(img)
    d.ellipse((3, 3, 60, 60), fill=(150, 100, 10))
    d.ellipse((6, 6, 57, 57), fill=(255, 200, 40))
    d.ellipse((12, 12, 51, 51), fill=(235, 170, 20))
    try:
        f = ImageFont.load_default(size=34)
    except Exception:
        f = ImageFont.load_default()
    d.text((32, 33), '$', fill=(255, 235, 120), font=f, anchor='mm')
    d.ellipse((14, 10, 26, 18), fill=(255, 250, 210))
    save(img, 'coin.png')


def clover():
    n = 64
    img = Image.new('RGB', (n, n))
    d = ImageDraw.Draw(img)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        cx, cy = 32 + math.cos(a) * 12, 30 + math.sin(a) * 12
        for s in (-1, 1):  # each leaf is a heart made of two circles
            b = a + s * 0.55
            ox, oy = cx + math.cos(b) * 3, cy + math.sin(b) * 3
            d.ellipse((ox - 9, oy - 9, ox + 9, oy + 9), fill=(70, 230, 90))
    d.line((32, 32, 40, 60), fill=(50, 170, 60), width=4)
    d.ellipse((27, 25, 37, 35), fill=(120, 255, 140))
    img = img.filter(ImageFilter.SMOOTH)
    save(img, 'clover.png')


def code():
    n, cell = 128, 32
    img = Image.new('RGB', (n, n))
    d = ImageDraw.Draw(img)
    try:
        f = ImageFont.load_default(size=26)
    except Exception:
        f = ImageFont.load_default()
    glyphs = '01{}<>/;#=+*$%&@'
    for i, g in enumerate(glyphs):
        x, y = (i % 4) * cell + cell // 2, (i // 4) * cell + cell // 2
        d.text((x, y), g, fill=(255, 255, 255), font=f, anchor='mm')
    save(img.filter(ImageFilter.GaussianBlur(0.6)), 'code.png')


def moon():
    n = 128
    img = Image.new('RGB', (n, n))
    px = img.load()
    craters = [(random.uniform(20, 108), random.uniform(20, 108), random.uniform(3, 10)) for _ in range(9)]
    for y in range(n):
        for x in range(n):
            dx, dy = (x - 63.5) / 60, (y - 63.5) / 60
            r = math.hypot(dx, dy)
            if r > 1:
                g = max(0, 1 - (r - 1) * 6) ** 2 * 0.35  # thin outer glow
                px[x, y] = (int(150 * g), int(170 * g), int(255 * g))
                continue
            limb = 0.55 + 0.45 * math.sqrt(1 - r * r)
            v = 0.88
            for cx, cy, cr in craters:
                cd = math.hypot(x - cx, y - cy) / cr
                if cd < 1: v -= 0.15 * (1 - cd) ** 0.5
                elif cd < 1.2: v += 0.05
            v = max(0.2, min(1.0, v)) * limb
            px[x, y] = (int(235 * v), int(238 * v), int(255 * v))
    save(img, 'moon.png')


def solid(name, src):
    """A copy with a transparent background (black becomes transparent) for normally blended sprites."""
    img = Image.open(OUT + src).convert('RGBA')
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            m = max(r, g, b)
            px[x, y] = (r, g, b, 0 if m < 40 else 255)
    img.save(OUT + name)


for fn in (glow, star, coin, clover, code, moon):
    fn()
solid('coin_solid.png', 'coin.png')
solid('clover_solid.png', 'clover.png')
print('effect textures written')
