"""Voxel textures of the brainrots (textures/entity/br/): 'vox' is one voxel with a bevel (16x16, repeats every voxel);
the pattern textures cover 4x4 voxels (64x64) and are multiplied with the mutation colour; 'e_*' textures are the glowing
parts (drawn additively, black = no glow). Run from the project root: python3 tools/gen_brainrot_textures.py"""
import math, os, random
from PIL import Image

OUT = 'src/main/resources/assets/eggsteal/textures/entity/br/'
os.makedirs(OUT, exist_ok=True)
C = 16  # pixels per voxel


def bevel(px, py):
    """Brightness of a pixel inside one voxel: darker 1px border, a light top-left edge."""
    if px == 0 or py == 0 or px == C - 1 or py == C - 1: return 0.78
    if px == 1 or py == 1: return 1.0
    return 0.93


def make(name, cell, size=4, emissive=None):
    """cell(cx, cy, px, py) -> (r, g, b) multiplier for the pattern; emissive(cx, cy, px, py) -> 0..1 glow."""
    n = size * C
    img = Image.new('RGB', (n, n))
    pix = img.load()
    em = Image.new('RGB', (n, n)) if emissive else None
    epx = em.load() if em else None
    for y in range(n):
        for x in range(n):
            cx, cy, px, py = x // C, y // C, x % C, y % C
            r, g, b = cell(cx, cy, px, py)
            k = bevel(px, py)
            pix[x, y] = (int(min(1, r * k) * 255), int(min(1, g * k) * 255), int(min(1, b * k) * 255))
            if em is not None:
                e = max(0.0, min(1.0, emissive(cx, cy, px, py)))
                epx[x, y] = (int(e * 255),) * 3
    img.save(OUT + name + '.png')
    if em is not None: em.save(OUT + 'e_' + name + '.png')


def g(v): return (v, v, v)


R = random.Random(5)
spots = {(R.randrange(4), R.randrange(4)) for _ in range(5)}
make('vox', lambda cx, cy, px, py: g(1.0), size=1)
sp_cells = {(0, 1), (2, 3), (3, 0)}
def sparkle(cx, cy, px, py):
    if (cx, cy) in sp_cells and (px == 7 or py == 7) and 4 <= px <= 10 and 4 <= py <= 10: return g(1.08)
    return g(0.9)
make('sparkle', sparkle, emissive=lambda cx, cy, px, py: 1.0 if (cx, cy) in sp_cells and (px == 7 or py == 7) and 3 <= px <= 11 and 3 <= py <= 11 else 0)
make('spots', lambda cx, cy, px, py: g(0.55) if (cx, cy) in spots else g(1.0),
     emissive=lambda cx, cy, px, py: 0.8 if (cx, cy) in spots and 3 <= px <= 12 and 3 <= py <= 12 else 0)
make('crystal', lambda cx, cy, px, py: g([0.72, 0.88, 1.05][(cx + cy) % 3] + (0.12 if px == py else 0)))
make('candy', lambda cx, cy, px, py: (1.0, 0.25, 0.35) if (cx + cy) % 2 == 0 else g(1.0))
make('dots', lambda cx, cy, px, py: g(0.62) if (cx + 2 * cy) % 3 == 0 and (px - 7.5) ** 2 + (py - 7.5) ** 2 < 25 else g(1.0))
make('zebra', lambda cx, cy, px, py: g(0.12) if (cx + (cy + (1 if px > 8 else 0)) // 2) % 2 == 0 and (cx + cy) % 4 < 2 else g(1.0))
def leopard(cx, cy, px, py):
    if (cx + cy * 3) % 3 == 0:
        d = math.hypot(px - 7.5, py - 7.5)
        if d < 7: return (0.25, 0.14, 0.04) if d > 4 else (0.8, 0.55, 0.3)
    return g(1.0)
make('leopard', leopard)
drip_len = {0: 3, 1: 1, 2: 2, 3: 0}
def drips(cx, cy, px, py):
    return g(0.55) if cy < drip_len[cx] or (cy == drip_len[cx] and 5 <= px <= 10 and py < 10) else g(1.0)
make('drips', drips, emissive=lambda cx, cy, px, py: 1.0 if drips(cx, cy, px, py)[0] > 0.9 else 0)
def flames(cx, cy, px, py):
    h = [3, 2, 4, 1][cx]
    top = 4 - h
    if cy > top or (cy == top and abs(px - 7.5) < (py / 2)): return (1.0, 0.9, 0.5) if cy >= 3 else (1.0, 0.75, 0.35)
    return g(0.7)
make('flames', flames, emissive=lambda cx, cy, px, py: 1.0 if flames(cx, cy, px, py)[0] >= 1 else 0)
crack = {(0, 0), (1, 1), (1, 2), (2, 2), (3, 3), (2, 0), (3, 1), (0, 3)}
make('cracks', lambda cx, cy, px, py: g(1.0) if (cx, cy) in crack else g(0.7),
     emissive=lambda cx, cy, px, py: 1.0 if (cx, cy) in crack and 2 <= px <= 13 and 2 <= py <= 13 else 0.0)
zz = {(0, 0), (1, 1), (0, 2), (1, 3), (2, 1), (3, 0), (3, 2), (2, 3)}
make('zigzag', lambda cx, cy, px, py: g(1.05) if (cx, cy) in zz else g(0.78), emissive=lambda cx, cy, px, py: 1.0 if (cx, cy) in zz and 4 <= px <= 11 else 0)
make('waves', lambda cx, cy, px, py: g(1.0 if (cy + (1 if (cx % 2 == 0 and py < 8) else 0)) % 2 == 0 else 0.72))
circ = lambda cx, cy, px, py: (cy == 1 and 6 <= py <= 9) or (cx == 2 and 6 <= px <= 9) or ((cx, cy) in {(0, 3), (3, 0)} and 4 <= px <= 11 and 4 <= py <= 11)
make('circuit', lambda cx, cy, px, py: g(1.0) if circ(cx, cy, px, py) else g(0.8), emissive=lambda cx, cy, px, py: 1.0 if circ(cx, cy, px, py) else 0)
make('checker', lambda cx, cy, px, py: g(1.0) if (cx + cy) % 2 == 0 else g(0.45), emissive=lambda cx, cy, px, py: 0.7 if (cx + cy) % 2 == 0 else 0)
camo_cols = [(0.35, 0.45, 0.2), (0.55, 0.6, 0.3), (0.3, 0.25, 0.15), (0.7, 0.75, 0.45)]
make('camo', lambda cx, cy, px, py: camo_cols[(cx * 3 + cy * 5 + (cx * cy)) % 4])
def heart(px, py):
    x, y = (px - 7.5) / 6, (7 - py) / 6
    return (x * x + y * y - 0.6) ** 3 - x * x * y ** 3 < 0
make('hearts', lambda cx, cy, px, py: (1.0, 0.25, 0.4) if (cx + cy) % 2 == 0 and heart(px, py) else g(1.0))
make('petals', lambda cx, cy, px, py: (1.0, 0.55, 0.75) if (cx * 2 + cy) % 3 == 0 and abs(px - 7.5) + abs(py - 7.5) < 6 else g(1.0))
star_cells = {(0, 0), (2, 1), (1, 3), (3, 2)}
def star(cx, cy, px, py):
    if (cx, cy) not in star_cells: return False
    dx, dy = abs(px - 7.5), abs(py - 7.5)
    return (dx < 1.2 and dy < 5) or (dy < 1.2 and dx < 5) or (dx < 2.2 and dy < 2.2)
make('stars', lambda cx, cy, px, py: g(1.0) if star(cx, cy, px, py) else g(0.72), emissive=lambda cx, cy, px, py: 1.0 if star(cx, cy, px, py) else 0)
make('swirl', lambda cx, cy, px, py: g(0.6 + 0.4 * (0.5 + 0.5 * math.sin((cx * C + px) * 0.12 + (cy * C + py) * 0.07 + math.sin((cy * C + py) * 0.15) * 2))))
make('scan', lambda cx, cy, px, py: g(0.82 if (py // 3) % 2 == 0 else 1.0), emissive=lambda cx, cy, px, py: 0.6 if py in (2, 9) else 0)
print('brainrot textures written')
