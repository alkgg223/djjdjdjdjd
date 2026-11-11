"""Tiny pixel-art toolkit used to generate the mod's textures procedurally."""
from PIL import Image
import math, random


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mul(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


class Canvas:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.p = self.img.load()

    def px(self, x, y, c):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[x, y] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.p[x, y]
        return (0, 0, 0, 0)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                self.px(x, y, c)

    def ellipse(self, cx, cy, rx, ry, c, shade=None):
        for y in range(self.h):
            for x in range(self.w):
                dx = (x + 0.5 - cx) / rx
                dy = (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d <= 1.0:
                    col = c
                    if shade:
                        # light from top-left
                        l = -(dx * 0.6 + dy * 0.8)
                        col = mul(c, 1.0 + 0.28 * l)
                    self.px(x, y, col)

    def poly(self, pts, c):
        minx = int(min(p[0] for p in pts)); maxx = int(max(p[0] for p in pts)) + 1
        miny = int(min(p[1] for p in pts)); maxy = int(max(p[1] for p in pts)) + 1
        for y in range(miny, maxy):
            for x in range(minx, maxx):
                if inside(pts, x + 0.5, y + 0.5):
                    self.px(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(1, n)
            self.px(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)

    def noise(self, amount=0.08, seed=1):
        r = random.Random(seed)
        for y in range(self.h):
            for x in range(self.w):
                c = self.p[x, y]
                if c[3] > 0:
                    self.p[x, y] = mul(c, 1 + (r.random() - 0.5) * 2 * amount)

    def finish(self, outline=True, light=True):
        """Pixel-art finishing: rim light, shadow side and a darker outline."""
        src = self.img.copy().load()
        if light:
            for y in range(self.h):
                for x in range(self.w):
                    c = src[x, y]
                    if c[3] == 0:
                        continue
                    up = src[x, y - 1] if y > 0 else (0, 0, 0, 0)
                    lf = src[x - 1, y] if x > 0 else (0, 0, 0, 0)
                    dn = src[x, y + 1] if y < self.h - 1 else (0, 0, 0, 0)
                    rt = src[x + 1, y] if x < self.w - 1 else (0, 0, 0, 0)
                    if up[3] == 0 or lf[3] == 0:
                        self.p[x, y] = mul(c, 1.18)
                    elif dn[3] == 0 or rt[3] == 0:
                        self.p[x, y] = mul(c, 0.78)
        if outline:
            src2 = self.img.copy().load()
            for y in range(self.h):
                for x in range(self.w):
                    if src2[x, y][3] != 0:
                        continue
                    nb = []
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        xx, yy = x + dx, y + dy
                        if 0 <= xx < self.w and 0 <= yy < self.h and src2[xx, yy][3] > 0:
                            nb.append(src2[xx, yy])
                    if nb:
                        c = nb[0]
                        self.p[x, y] = (int(c[0] * 0.35), int(c[1] * 0.35), int(c[2] * 0.35), 255)
        return self

    def save(self, path, scale=1):
        im = self.img
        if scale != 1:
            im = im.resize((self.w * scale, self.h * scale), Image.NEAREST)
        im.save(path)


def inside(pts, x, y):
    n = len(pts)
    c = False
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi):
            c = not c
        j = i
    return c


def tex(w, h, base, seed=0, amount=0.06):
    c = Canvas(w, h)
    c.rect(0, 0, w - 1, h - 1, base)
    c.noise(amount, seed)
    return c
