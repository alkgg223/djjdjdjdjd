"""Renders the creature OBJ models to PNG contact sheets (painter's algorithm with simple lighting).
Usage (from the project root):  python3 tools/preview_models.py OUT_DIR [name ...]
"""
import math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw
from gen_models import creature_colors, R

def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))

def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)

def colour(group, main, accent):
    m, a = rgb(main), rgb(accent)
    return {'accent': a, 'glow': a, 'belly': mix(m, (255, 255, 255), 0.55), 'shade': mix(m, (0, 0, 0), 0.28), 'eye': (26, 18, 38),
            'white': (255, 255, 255), 'blush': mix((255, 127, 168), m, 0.2), 'dark': (59, 37, 48), 'gold': (255, 212, 71), 'pink': (255, 158, 196)}.get(group, m)

def load(path):
    V, N, polys, g = [], [], [], 'main'
    for line in open(path):
        p = line.split()
        if not p or p[0] == '#': continue
        if p[0] == 'v': V.append((float(p[1]), float(p[2]), float(p[3]), float(p[4]) if len(p) > 4 else 1.0))
        elif p[0] == 'vn': N.append(tuple(map(float, p[1:4])))
        elif p[0] == 'g': g = p[1]
        elif p[0] == 'f': polys.append((g, [int(t.split('/')[0]) - 1 for t in p[1:]]))
    return V, N, polys

def render(path, main, accent, size=260, yaw=35, pitch=18):
    V, N, polys = load(path)
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    def tf(v):
        x, y, z = v[0] * cy + v[2] * sy, v[1], -v[0] * sy + v[2] * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z
    P = [tf(v) for v in V]
    img = Image.new('RGB', (size, size), (60, 66, 80))
    d = ImageDraw.Draw(img)
    sc, oy = size / 1.7, size * 0.85
    light = (0.4, 0.75, 0.55)
    ll = math.sqrt(sum(c * c for c in light)); light = tuple(c / ll for c in light)
    tris = []
    for g, idx in polys:
        pts = [P[i] for i in idx]
        zc = sum(p[2] for p in pts) / len(pts)
        a, b, c = pts[0], pts[1], pts[2]
        n = ((b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]), (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]), (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]))
        nl = math.sqrt(sum(x * x for x in n)) or 1
        n = tuple(x / nl for x in n)
        if n[2] < 0: n = tuple(-x for x in n)
        lam = 0.45 + 0.55 * max(0, sum(n[i] * light[i] for i in range(3)))
        shade = sum(V[i][3] for i in idx) / len(idx)
        col = colour(g, main, accent)
        k = lam * shade * (1.25 if g == 'glow' else 1)
        col = tuple(min(255, int(ch * k)) for ch in col)
        tris.append((zc, [(size / 2 + p[0] * sc, oy - p[1] * sc) for p in pts], col))
    tris.sort(key=lambda t: t[0])
    for _, pts, col in tris: d.polygon(pts, fill=col)
    return img

if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    cols = creature_colors()
    names = sys.argv[2:] or list(cols)
    sheet_n = 0
    for start in range(0, len(names), 6):
        batch = names[start:start + 6]
        sheet = Image.new('RGB', (3 * 260, len(batch) * 260))
        for row, nm in enumerate(batch):
            for col, (yaw, pitch) in enumerate([(30, 15), (-90, 5), (160, 25)]):
                sheet.paste(render(R + nm + '.obj', *cols[nm], yaw=yaw, pitch=pitch), (col * 260, row * 260))
            ImageDraw.Draw(sheet).text((6, row * 260 + 4), nm, fill=(255, 255, 255))
        sheet.save(f'{out}/sheet_{sheet_n:02d}.png')
        sheet_n += 1
    print('sheets:', sheet_n)
