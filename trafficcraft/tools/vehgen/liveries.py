"""55 graphic liveries painted in the vehicle-normalised livery space (see custom.livery_uv).

Each livery is a function of the panel region and normalised coordinates:
  zn 0 (rear) .. 1 (front), yn 0 (ground) .. 1 (roof), xn -0.5 (right) .. +0.5 (left).
Pixels with alpha 0 show the car's factory paint underneath. Side regions are drawn as seen from outside, so text and
numbers read correctly on both sides. Output: livery atlas (8 x 8 tiles of 256 x 192, tile 0 = none, 1..55 liveries,
63 = copy of the paint reflection matcap) and liveries.json (names)."""
import math, json, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont

TW, TH = 256, 192
COLS, ROWS = 8, 8
rng0 = np.random.default_rng(11)


def font(sz, bold=True):
    try:
        return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if bold else ''), int(sz))
    except Exception:
        return ImageFont.load_default()


def coords():
    """Per-pixel region id and normalised coordinates for one tile."""
    v, u = np.mgrid[0:TH, 0:TW].astype(float)
    u = (u + 0.5) / TW
    v = (v + 0.5) / TH
    reg = np.zeros((TH, TW), int)
    zn = np.zeros((TH, TW))
    yn = np.zeros((TH, TW))
    xn = np.zeros((TH, TW))
    L = v < 1 / 3
    R = (v >= 1 / 3) & (v < 2 / 3)
    T = (v >= 2 / 3) & (v < 11 / 12)
    F = (v >= 11 / 12) & (u < 0.5)
    B = (v >= 11 / 12) & (u >= 0.5)
    reg[L], reg[R], reg[T], reg[F], reg[B] = 1, 2, 3, 4, 5
    zn[L] = 1 - u[L]
    yn[L] = 1 - v[L] * 3
    zn[R] = u[R]
    yn[R] = 1 - (v[R] - 1 / 3) * 3
    zn[T] = u[T]
    xn[T] = 0.5 - (v[T] - 2 / 3) * 4
    yn[T] = 1.0
    xn[F] = u[F] / 0.5 - 0.5
    yn[F] = 1 - (v[F] - 11 / 12) * 12
    zn[F] = 1.0
    xn[B] = 0.5 - (u[B] - 0.5) / 0.5
    yn[B] = 1 - (v[B] - 11 / 12) * 12
    zn[B] = 0.0
    xn[L] = 0.5
    xn[R] = -0.5
    return reg, zn, yn, xn


REG, ZN, YN, XN = coords()
SIDE = (REG == 1) | (REG == 2)
TOP = REG == 3
FRONT = REG == 4
REAR = REG == 5


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)], float) / 255


class Canvas:
    def __init__(self):
        self.rgb = np.zeros((TH, TW, 3))
        self.a = np.zeros((TH, TW))

    def fill(self, mask, col, alpha=1.0):
        c = hexc(col) if isinstance(col, str) else np.array(col, float)
        m = mask.astype(float) * alpha if mask.dtype == bool else np.clip(mask, 0, 1) * alpha
        self.rgb = self.rgb * (1 - m[..., None]) + c * m[..., None]
        self.a = np.maximum(self.a, m)

    def side_text(self, text, zc, yc, size, col, outline=None, regions=(1, 2), bold=True, skew=0):
        """Text on the side bands centred at (zc, yc), readable on both sides."""
        im = Image.new('L', (TW, TH), 0)
        d = ImageDraw.Draw(im)
        f = font(size, bold)
        for r in regions:
            if r == 1:
                u = 1 - zc
                v = (1 - yc) / 3
            else:
                u = zc
                v = 1 / 3 + (1 - yc) / 3
            tw = d.textlength(text, font=f)
            d.text((u * TW - tw / 2, v * TH - size * 0.6), text, fill=255, font=f)
        m = np.array(im, float) / 255
        if outline:
            from scipy.ndimage import maximum_filter
            self.fill(maximum_filter(m, 3) > 0.3, outline)
        self.fill(m, col)

    def top_text(self, text, zc, xc, size, col):
        im = Image.new('L', (TW, TH), 0)
        d = ImageDraw.Draw(im)
        f = font(size)
        tw = d.textlength(text, font=f)
        d.text((zc * TW - tw / 2, (2 / 3 + (0.5 - xc) * 0.25) * TH - size * 0.6), text, fill=255, font=f)
        self.fill(np.array(im, float) / 255, col)

    def roundel(self, zc, yc, r, bg, num, fg='#111111', regions=(1, 2)):
        for reg in regions:
            u = (1 - zc) if reg == 1 else zc
            v = ((1 - yc) / 3) if reg == 1 else (1 / 3 + (1 - yc) / 3)
            uu = np.arange(TW)[None, :] + 0.5
            vv = np.arange(TH)[:, None] + 0.5
            m = ((uu - u * TW) ** 2 + ((vv - v * TH) * 1.0) ** 2) < (r * TH / 3) ** 2
            self.fill(m & SIDE, bg)
        self.side_text(str(num), zc, yc, r * TH / 3 * 1.3, fg, regions=regions)


def noise(scale, seed, octaves=3):
    r = np.random.default_rng(seed)
    out = np.zeros((TH, TW))
    for o in range(octaves):
        s = max(2, int(scale * 2 ** o))
        g = r.standard_normal((s + 1, int(s * TW / TH) + 2))
        from scipy.ndimage import zoom
        z = zoom(g, (TH / s, TW / (s * TW / TH)), order=3)[:TH, :TW]
        if z.shape != (TH, TW):
            z = np.pad(z, ((0, TH - z.shape[0]), (0, TW - z.shape[1])), mode='edge')
        out += z / (2 ** o)
    return out


# ============================================================================== designs
L = []


def livery(name):
    def deco(fn):
        L.append((name, fn))
        return fn
    return deco


def stripes_top(c, xs, w, front_rear=True):
    m = np.zeros((TH, TW), bool)
    for x0 in xs:
        m |= (np.abs(XN - x0) < w / 2) & (TOP | (FRONT | REAR) & front_rear & (YN > 0.25))
    c.fill(m, c.col)


@livery('Twin Racing Stripes - White')
def _l1(c):
    c.fill((np.abs(np.abs(XN) - 0.09) < 0.045) & (TOP | FRONT | REAR), '#f4f4f2')


@livery('Twin Racing Stripes - Black')
def _l2(c):
    c.fill((np.abs(np.abs(XN) - 0.09) < 0.045) & (TOP | FRONT | REAR), '#111214')


@livery('Centre Stripe - Red')
def _l3(c):
    c.fill((np.abs(XN) < 0.07) & (TOP | FRONT | REAR), '#c41a1a')
    c.fill((np.abs(np.abs(XN) - 0.085) < 0.008) & (TOP | FRONT | REAR), '#f2f2f2')


@livery('Rally Stripes - Gold')
def _l4(c):
    c.fill((np.abs(np.abs(XN) - 0.11) < 0.055) & (TOP | FRONT | REAR), '#d6a627')
    c.fill((np.abs(np.abs(XN) - 0.11) < 0.065) & (np.abs(np.abs(XN) - 0.11) > 0.055) & (TOP | FRONT | REAR), '#141414')


@livery('Side Stripe Fade')
def _l5(c):
    band = (np.abs(YN - 0.52) < 0.05) & SIDE
    c.fill(band, '#f0f0f0', 1.0)
    c.fill(band * np.clip((ZN - 0.1) * 1.4, 0, 1), '#1d1f22')


@livery('Lower Two-Tone Black')
def _l6(c):
    c.fill((YN < 0.42) & (SIDE | FRONT | REAR), '#111214')
    c.fill((np.abs(YN - 0.42) < 0.012) & SIDE, '#c7ccd2')


@livery('Upper Two-Tone White')
def _l7(c):
    c.fill(TOP | ((YN > 0.68) & SIDE), '#f2f2f0')


@livery('Three-Tone Retro')
def _l8(c):
    for y0, col in ((0.36, '#e8a020'), (0.43, '#d8641c'), (0.5, '#a8221c')):
        c.fill((np.abs(YN - y0) < 0.03) & (SIDE | FRONT | REAR), col)


@livery('Gradient Sunset')
def _l9(c):
    t = np.clip(ZN, 0, 1)
    col = np.stack([0.98 - 0.1 * t, 0.35 + 0.35 * t, 0.15 + 0.1 * t], -1)
    m = (SIDE & (YN < 0.62)) * np.clip((0.62 - YN) / 0.3, 0, 1)
    c.rgb = c.rgb * (1 - m[..., None]) + col * m[..., None]
    c.a = np.maximum(c.a, m)


@livery('Gradient Ocean')
def _l10(c):
    t = np.clip(1 - ZN, 0, 1)
    col = np.stack([0.05 + 0.1 * t, 0.35 + 0.4 * t, 0.65 + 0.3 * t], -1)
    m = (SIDE | FRONT | REAR) * np.clip((0.7 - YN) / 0.45, 0, 1)
    c.rgb = c.rgb * (1 - m[..., None]) + col * m[..., None]
    c.a = np.maximum(c.a, m)


def carbon(mask, c):
    uu = np.arange(TW)[None, :]
    vv = np.arange(TH)[:, None]
    weave = (((uu // 3) + (vv // 3)) % 2).astype(float)
    shade = 0.10 + 0.07 * weave
    col = np.stack([shade, shade, shade * 1.08], -1)
    m = mask.astype(float)
    c.rgb = c.rgb * (1 - m[..., None]) + col * m[..., None]
    c.a = np.maximum(c.a, m)


@livery('Carbon Hood & Roof')
def _l11(c):
    carbon(TOP & ((ZN > 0.62) | ((ZN > 0.3) & (ZN < 0.6))), c)


@livery('Carbon Lower Panels')
def _l12(c):
    carbon((YN < 0.3) & (SIDE | FRONT | REAR), c)


@livery('Geometric Shards')
def _l13(c):
    k = ((ZN * 7 + YN * 3).astype(int) + (ZN * 3 - YN * 5).astype(int)) % 3
    c.fill(SIDE & (k == 0) & (YN < 0.7), '#1c1d20')
    c.fill(SIDE & (k == 1) & (YN < 0.7), '#e2e4e8')


@livery('Triangle Burst')
def _l14(c):
    d = (1 - ZN) * 3 + np.abs(YN - 0.45) * 4
    c.fill(SIDE & (d < 1.6) & ((d * 6).astype(int) % 2 == 0), '#ffcc22')
    c.fill(SIDE & (d < 1.6) & ((d * 6).astype(int) % 2 == 1), '#141414')


@livery('Hex Grid')
def _l15(c):
    q = ZN * 40
    r = YN * 14 + (q.astype(int) % 2) * 0.5
    edge = (np.abs(q - np.round(q)) < 0.12) | (np.abs(r - np.round(r)) < 0.1)
    c.fill(edge & (SIDE | TOP) & (np.where(SIDE, ZN < 0.45, XN < 0.5)), '#20c0ff', 0.9)


@livery('Pixel Fade')
def _l16(c):
    cell = ((ZN * 48).astype(int) * 7 + (YN * 16).astype(int) * 13) % 17 / 17
    c.fill(SIDE & (cell < (1 - ZN) * 1.2 - 0.2) & (YN < 0.68), '#2a2d33')


def race(c, num, base, accent):
    c.fill((YN < 0.36) & (SIDE | FRONT | REAR), base)
    c.fill((np.abs(YN - 0.37) < 0.015) & SIDE, accent)
    c.roundel(0.48, 0.5, 0.25, '#f6f6f4', num)
    c.fill((np.abs(XN) < 0.05) & (TOP | FRONT | REAR), accent)


@livery('Racing #7')
def _l17(c):
    race(c, 7, '#0d3b8c', '#ffd21e')


@livery('Racing #23')
def _l18(c):
    race(c, 23, '#111214', '#e01c1c')


@livery('Racing #88')
def _l19(c):
    race(c, 88, '#1a7a3a', '#f2f2f2')


@livery('Racing #46')
def _l20(c):
    c.fill(SIDE & (YN < 0.7), '#f6d70e')
    c.roundel(0.5, 0.48, 0.27, '#ffffff', 46, '#1a5ec8')
    c.fill((np.abs(XN) < 0.06) & (TOP | FRONT | REAR), '#1a5ec8')


@livery('Endurance - Aeris')
def _l21(c):
    c.fill((SIDE | FRONT | REAR) & (YN < 0.55), '#7ab7e6')
    c.fill(SIDE & (np.abs(YN - 0.3 - (ZN - 0.5) * 0.2) < 0.07), '#f08a1e')
    c.side_text('AERIS', 0.45, 0.46, 15, '#1b2a4a')
    c.fill(TOP & (np.abs(XN) < 0.12), '#f08a1e')


@livery('Voltex Energy')
def _l22(c):
    c.fill(SIDE | FRONT | REAR | TOP, '#121314')
    c.fill(SIDE & (np.abs(YN - 0.45 - np.sin(ZN * 9) * 0.06) < 0.035), '#7cff2a')
    c.side_text('VOLTEX', 0.5, 0.58, 15, '#7cff2a')


@livery('Kaizen Oil')
def _l23(c):
    c.fill((SIDE | FRONT | REAR) & (YN < 0.5), '#f2f2f2')
    c.fill(SIDE & (YN < 0.5) & (ZN > 0.35) & (ZN < 0.65), '#c8102e')
    c.side_text('KAIZEN', 0.5, 0.33, 13, '#ffffff')
    c.fill(TOP & (np.abs(XN) < 0.08), '#c8102e')


@livery('Northwind Tyres')
def _l24(c):
    c.fill(SIDE & (np.abs(YN - 0.33) < 0.04), '#1a1a1c')
    c.side_text('NORTHWIND', 0.5, 0.52, 12, '#ffffff', outline='#1a1a1c')


@livery('Retro 70s Stripes')
def _l25(c):
    for k, col in enumerate(('#5a3418', '#c26a1a', '#e8a63a')):
        y = 0.5 + k * 0.035 + np.clip(ZN - 0.7, 0, 1) * 1.2
        c.fill(SIDE & (np.abs(YN - y) < 0.016), col)


@livery('Retro Checker')
def _l26(c):
    chk = ((ZN * 50).astype(int) + (YN * 18).astype(int)) % 2 == 0
    c.fill(SIDE & (YN < 0.36) & (YN > 0.2) & (ZN > 0.6) & chk, '#111111')
    c.fill(SIDE & (YN < 0.36) & (YN > 0.2) & (ZN > 0.6) & ~chk, '#f4f4f4')
    c.fill(TOP & (np.abs(XN) < 0.1) & chk, '#111111')


@livery('Modern Motorsport')
def _l27(c):
    c.fill(SIDE & (YN < 0.7 - (1 - ZN) * 0.6), '#121418')
    c.fill(SIDE & (np.abs(YN - (0.7 - (1 - ZN) * 0.6)) < 0.02), '#ff3b2f')
    c.fill((FRONT | REAR) & (YN < 0.4), '#121418')


@livery('Street Splash')
def _l28(c):
    n = noise(5, 3)
    c.fill((SIDE | TOP) & (n > 0.9), '#ff2a7a')
    c.fill((SIDE | TOP) & (n < -1.0), '#22d0ff')


@livery('Graffiti Tags')
def _l29(c):
    n = noise(8, 9)
    c.fill(SIDE & (np.abs(n) < 0.12) & (YN < 0.7), '#ffe020')
    c.side_text('WILD', 0.62, 0.45, 18, '#ff3fa0', outline='#111111')


@livery('Neon Lines')
def _l30(c):
    for k, col in ((0, '#00f0ff'), (1, '#ff2bd6')):
        c.fill(SIDE & (np.abs(YN - 0.4 - k * 0.05 - (1 - ZN) * 0.15) < 0.01), col)
    c.fill(TOP & (np.abs(np.abs(XN) - 0.2) < 0.01), '#00f0ff')


@livery('Minimal Pinstripe')
def _l31(c):
    c.fill(SIDE & (np.abs(YN - 0.62) < 0.006) & (ZN > 0.08) & (ZN < 0.93), '#d9c27a')


@livery('Minimal Dot')
def _l32(c):
    c.fill(SIDE & (((ZN - 0.78) * 3.2) ** 2 + (YN - 0.5) ** 2 < 0.004), '#f2f2f2')


def camo(c, cols, scale, seed, mask):
    ns = [noise(scale, seed + i) for i in range(len(cols))]
    for i, col in enumerate(cols):
        c.fill(mask & (ns[i] > 0.2 + i * 0.12), col)


@livery('Woodland Camo')
def _l33(c):
    c.fill(SIDE | TOP | FRONT | REAR, '#4a5a2e')
    camo(c, ['#2b331b', '#7a6a3e', '#141611'], 6, 21, SIDE | TOP | FRONT | REAR)


@livery('Desert Camo')
def _l34(c):
    c.fill(SIDE | TOP | FRONT | REAR, '#c8b282')
    camo(c, ['#a08356', '#7a5f3a', '#e0d0a8'], 5, 31, SIDE | TOP | FRONT | REAR)


@livery('Urban Camo')
def _l35(c):
    c.fill(SIDE | TOP | FRONT | REAR, '#8a8d92')
    camo(c, ['#3d3f44', '#c9cbcf', '#1b1c1e'], 7, 41, SIDE | TOP | FRONT | REAR)


@livery('Digital Camo')
def _l36(c):
    q = ((ZN * 64).astype(int) // 1, (YN * 22).astype(int))
    n = noise(6, 51)
    blk = n[(np.clip(q[1] * TH // 22, 0, TH - 1)), np.clip(q[0] * TW // 64, 0, TW - 1)] if False else n
    pix = (np.floor(ZN * 64) + np.floor(YN * 22) * 3) % 5
    c.fill(SIDE | TOP | FRONT | REAR, '#5f6b4a')
    c.fill((SIDE | TOP | FRONT | REAR) & ((noise(7, 52) > 0.3) ^ (pix == 0)), '#3a412d')
    c.fill((SIDE | TOP | FRONT | REAR) & (noise(7, 53) > 0.7), '#9aa07a')


@livery('Arctic Camo')
def _l37(c):
    c.fill(SIDE | TOP | FRONT | REAR, '#e8ecf0')
    camo(c, ['#b4bcc6', '#7c8794'], 6, 61, SIDE | TOP | FRONT | REAR)


@livery('Classic Flames')
def _l38(c):
    w = 0.35 + 0.18 * np.sin(YN * 40 + ZN * 3) * np.clip(ZN - 0.4, 0, 1)
    m = SIDE & (ZN > 1 - w - 0.25) & (YN > 0.3) & (YN < 0.68)
    c.fill(m, '#ffb000')
    w2 = w * 0.75
    c.fill(SIDE & (ZN > 1 - w2 - 0.2) & (YN > 0.35) & (YN < 0.63), '#ff4a00')
    c.fill(TOP & (ZN > 0.7) & (np.abs(XN) < (ZN - 0.7) * 1.4) & ((XN * 30).astype(int) % 3 != 0), '#ff7a00')


@livery('Tribal')
def _l39(c):
    t = np.sin(ZN * 22 + np.sin(YN * 12) * 2) + np.cos(YN * 18 - ZN * 6)
    c.fill(SIDE & (t > 1.2) & (YN < 0.68), '#111214')


@livery('Lightning')
def _l40(c):
    zz = ZN * 10
    bolt = 0.45 + np.abs((zz % 2) - 1) * 0.12
    c.fill(SIDE & (np.abs(YN - bolt) < 0.025), '#ffe500')
    c.fill(SIDE & (np.abs(YN - bolt) < 0.04) & (np.abs(YN - bolt) >= 0.025), '#1a1a1a')


@livery('Tiger Stripes')
def _l41(c):
    t = np.sin(ZN * 60 + np.sin(YN * 9) * 3)
    c.fill((SIDE | TOP) & (t > 0.55) & (np.where(SIDE, YN < 0.7, True)), '#151515')


@livery('Waves')
def _l42(c):
    for k in range(3):
        y = 0.25 + k * 0.08 + 0.04 * np.sin(ZN * 14 + k)
        c.fill(SIDE & (np.abs(YN - y) < 0.02), ['#0a6fd6', '#18a8e8', '#bfeaff'][k])


@livery('Halftone Dots')
def _l43(c):
    gz, gy = ZN * 60, YN * 20
    r = np.sqrt((gz - np.round(gz)) ** 2 + (gy - np.round(gy)) ** 2)
    c.fill(SIDE & (r < (1 - ZN) * 0.45) & (YN < 0.7), '#141414')


@livery('Chevrons')
def _l44(c):
    t = (ZN * 12 + np.abs(YN - 0.45) * 8)
    c.fill(SIDE & ((t.astype(int) % 2) == 0) & (np.abs(YN - 0.45) < 0.12) & (ZN > 0.2) & (ZN < 0.8), '#ffcf1a')


@livery('Circuit Board')
def _l45(c):
    g = ((ZN * 80).astype(int) % 9 == 0) & ((YN * 30).astype(int) % 3 != 0) | ((YN * 30).astype(int) % 7 == 0) & ((ZN * 80).astype(int) % 5 != 0)
    c.fill(SIDE & g & (ZN < 0.5) & (YN < 0.68), '#2dd96a')


@livery('Speed Lines')
def _l46(c):
    for k in range(6):
        y = 0.32 + k * 0.05
        z0 = 0.15 + (k * 37 % 7) / 20
        c.fill(SIDE & (np.abs(YN - y) < 0.008) & (ZN > z0) & (ZN < z0 + 0.4), '#f4f4f4')


@livery('Sakura')
def _l47(c):
    n = noise(10, 71)
    petals = (n > 1.1) & (SIDE | TOP)
    c.fill(petals, '#f7a6c8')
    c.fill(SIDE & (YN < 0.3), '#2b1a2e')


@livery('Night City')
def _l48(c):
    h = 0.25 + 0.2 * ((np.floor(ZN * 18) * 7 % 5) / 5)
    c.fill(SIDE & (YN < h), '#14162a')
    win = ((ZN * 120).astype(int) % 3 == 0) & ((YN * 60).astype(int) % 3 == 0) & (YN < h - 0.02)
    c.fill(SIDE & win, '#ffd85a')


@livery('Galaxy')
def _l49(c):
    n = noise(4, 81)
    c.fill(SIDE | TOP | FRONT | REAR, '#0a0618')
    c.fill((SIDE | TOP) * np.clip(n * 0.5, 0, 0.7), '#6a2aa8')
    stars = np.random.default_rng(82).random((TH, TW)) > 0.985
    c.fill((SIDE | TOP) & stars, '#ffffff')


@livery('Diagonal Split')
def _l50(c):
    c.fill(SIDE & (YN < 0.85 - ZN * 0.7), '#16181b')
    c.fill(TOP & (ZN < 0.4), '#16181b')


@livery('Le Mans Stripe')
def _l51(c):
    c.fill((np.abs(XN - 0.12) < 0.08) & (TOP | FRONT | REAR), '#f2f2f2')
    c.fill((np.abs(XN - 0.12) < 0.09) & (np.abs(XN - 0.12) >= 0.08) & (TOP | FRONT | REAR), '#0e3a7a')


@livery('Police Interceptor')
def _l52(c):
    c.fill((SIDE & (YN < 0.62)) | ((FRONT | REAR) & (YN < 0.5)), '#f4f4f4')
    c.fill(SIDE & (np.abs(YN - 0.4) < 0.03), '#1446a0')
    c.side_text('POLICE', 0.5, 0.52, 15, '#111111')
    c.side_text('911', 0.18, 0.47, 10, '#1446a0')


@livery('Taxi Checker')
def _l53(c):
    chk = ((ZN * 60).astype(int) + (YN * 30).astype(int)) % 2 == 0
    band = SIDE & (np.abs(YN - 0.47) < 0.035)
    c.fill(band & chk, '#111111')
    c.fill(band & ~chk, '#f4f4f4')
    c.side_text('TAXI', 0.25, 0.34, 11, '#111111')


@livery('Special Edition Gold')
def _l54(c):
    c.fill(SIDE & (np.abs(YN - 0.6) < 0.006), '#d8b04a')
    c.fill(SIDE & (np.abs(YN - 0.585) < 0.003), '#d8b04a')
    c.side_text('SE', 0.84, 0.48, 10, '#d8b04a')
    c.fill(TOP & (np.abs(np.abs(XN) - 0.14) < 0.006), '#d8b04a')


@livery('Drift Team #99')
def _l55(c):
    t = np.clip(ZN, 0, 1)
    col = np.stack([0.95 * (1 - t) + 0.2 * t, 0.25 * (1 - t) + 0.5 * t, 0.65 * (1 - t) + 0.95 * t], -1)
    m = (SIDE * np.clip((0.66 - YN) / 0.25, 0, 1))
    c.rgb = c.rgb * (1 - m[..., None]) + col * m[..., None]
    c.a = np.maximum(c.a, m)
    c.roundel(0.42, 0.5, 0.22, '#111111', 99, '#ffffff')


def build(path_png, path_json, matcap_src=None):
    atlas = np.zeros((ROWS * TH, COLS * TW, 4), np.uint8)
    names = []
    for i, (name, fn) in enumerate(L[:55]):
        c = Canvas()
        fn(c)
        tile = np.concatenate([np.clip(c.rgb, 0, 1) * 255, np.clip(c.a, 0, 1)[..., None] * 255], -1).astype(np.uint8)
        k = i + 1
        atlas[(k // COLS) * TH:(k // COLS + 1) * TH, (k % COLS) * TW:(k % COLS + 1) * TW] = tile
        names.append(name)
    if matcap_src is not None:
        mc = Image.open(matcap_src).convert('RGBA')
        A = np.array(mc)
        h, w = A.shape[:2]
        # vehicle atlas matcap region (64,128)-(128,192) of 256 units
        reg = A[int(h * 128 / 256):int(h * 192 / 256), int(w * 64 / 256):int(w * 128 / 256)]
        reg = np.array(Image.fromarray(reg).resize((TH, TH)))
        k = 63
        y0, x0 = (k // COLS) * TH, (k % COLS) * TW
        atlas[y0:y0 + TH, x0:x0 + TH] = reg
    Image.fromarray(atlas, 'RGBA').save(path_png)
    json.dump({'tile': [TW, TH], 'cols': COLS, 'rows': ROWS, 'names': names, 'matcap_tile': 63}, open(path_json, 'w'), indent=1)
    return names


def preview_sheet(path_png, out):
    A = Image.open(path_png)
    sheet = Image.new('RGB', (8 * 200, 7 * 175), (40, 42, 46))
    d = ImageDraw.Draw(sheet)
    for i in range(55):
        k = i + 1
        tile = A.crop(((k % COLS) * TW, (k // COLS) * TH, (k % COLS + 1) * TW, (k // COLS + 1) * TH)).convert('RGBA')
        bg = Image.new('RGBA', tile.size, (150, 30, 36, 255))
        bg.alpha_composite(tile)
        sheet.paste(bg.resize((192, 144)), ((i % 8) * 200 + 4, (i // 8) * 175 + 4))
        d.text(((i % 8) * 200 + 4, (i // 8) * 175 + 152), '%d %s' % (k, L[i][0])[:30], fill=(230, 230, 230))
    sheet.save(out)


if __name__ == '__main__':
    import sys
    names = build(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    print(len(names), 'liveries')
    if len(sys.argv) > 4:
        preview_sheet(sys.argv[1], sys.argv[4])
