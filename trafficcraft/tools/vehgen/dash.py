"""Per-vehicle instrument clusters and infotainment screens (dash atlas 2048x2048, cells of 1024x256:
left 512x256 = cluster, right 512x256 = screen). Needles / digital speed are drawn live by the Java renderer from
the 'dials' metadata, so the textures contain only faces, scales and static UI."""
import math, random
from PIL import Image, ImageDraw, ImageFont

DW, DH = 2048, 2048
CW, CH = 512, 256
STYLES = {}


def font(sz, bold=True):
    try:
        return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if bold else ''), int(sz))
    except Exception:
        return ImageFont.load_default()


def cell_origin(idx):
    return (idx % 2) * 1024, (idx // 2) * 256


def uv_cluster(idx):
    x, y = cell_origin(idx)
    return (x / DW, y / DH, (x + CW) / DW, (y + CH) / DH)


def uv_screen(idx):
    x, y = cell_origin(idx)
    return ((x + CW) / DW, y / DH, (x + 2 * CW) / DW, (y + CH) / DH)


def dial(d, cx, cy, r, maxv, step, a0=225, a1=-45, face=(14, 16, 20), ring=(160, 166, 178), tick=(232, 236, 244), red_from=None,
         label='', num_col=None, minor=True, fsz=None):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=face, outline=ring, width=max(2, int(r * 0.05)))
    n = int(maxv / step)
    for k in range(n * (2 if minor else 1) + 1):
        v = k * step / (2 if minor else 1)
        a = math.radians(a0 + (a1 - a0) * v / maxv)
        big = (k % 2 == 0) if minor else True
        r0 = r * (0.78 if big else 0.86)
        col = (235, 60, 50) if red_from is not None and v >= red_from else tick
        d.line([(cx + math.cos(a) * r0, cy - math.sin(a) * r0), (cx + math.cos(a) * r * 0.94, cy - math.sin(a) * r * 0.94)], fill=col,
               width=3 if big else 1)
        if big:
            f = font(fsz or r * 0.15)
            t = str(int(v))
            tw = d.textlength(t, font=f)
            p = (cx + math.cos(a) * r * 0.6, cy - math.sin(a) * r * 0.6)
            d.text((p[0] - tw / 2, p[1] - r * 0.085), t, fill=num_col or tick, font=f)
    if label:
        f = font(r * 0.12, False)
        tw = d.textlength(label, font=f)
        d.text((cx - tw / 2, cy + r * 0.32), label, fill=ring, font=f)
    d.ellipse([cx - r * 0.08, cy - r * 0.08, cx + r * 0.08, cy + r * 0.08], fill=(40, 42, 46))


def telltales(d, x, y, s=14):
    cols = [(60, 200, 90), (60, 200, 90), (70, 140, 255), (255, 170, 40), (230, 50, 40)]
    for i, c in enumerate(cols):
        d.rounded_rectangle([x + i * (s + 6), y, x + i * (s + 6) + s, y + s * 0.7], 3, outline=(40, 44, 50), width=1, fill=(18, 20, 24))


def bar_gauge(d, x, y, w, h, frac, col, bg=(30, 34, 40), segs=10):
    for k in range(segs):
        xx = x + k * w / segs
        d.rectangle([xx + 1, y, xx + w / segs - 2, y + h], fill=col if k < segs * frac else bg)


# -------------------------------------------------------------------------------- cluster styles
def cl_analog_modern(d, W, H):
    d.rounded_rectangle([0, 0, W - 1, H - 1], 26, fill=(6, 7, 9))
    dial(d, W * 0.24, H * 0.52, H * 0.42, 240, 20, label='km/h')
    dial(d, W * 0.76, H * 0.52, H * 0.42, 8, 1, red_from=6.5, label='x1000 rpm', minor=True)
    d.rounded_rectangle([W * 0.43, H * 0.16, W * 0.57, H * 0.86], 10, fill=(10, 22, 40), outline=(40, 70, 110), width=2)
    d.text((W * 0.455, H * 0.6), '412 km', fill=(200, 210, 230), font=font(H * 0.06, False))
    return [dict(kind='speed', cx=0.24, cy=0.52, r=0.42 * H / W, a0=225, a1=-45, max=240),
            dict(kind='rpm', cx=0.76, cy=0.52, r=0.42 * H / W, a0=225, a1=-45, max=8000),
            dict(kind='fuel_bar', x=0.445, y=0.74, w=0.11, h=0.04), dict(kind='gear', x=0.5, y=0.28, size=0.12)]


def cl_compact(d, W, H):
    d.rounded_rectangle([0, 0, W - 1, H - 1], 30, fill=(12, 12, 14))
    d.rounded_rectangle([W * 0.06, H * 0.12, W * 0.3, H * 0.88], 18, fill=(22, 20, 18), outline=(255, 140, 40), width=2)
    d.text((W * 0.09, H * 0.18), 'ECO', fill=(120, 220, 120), font=font(H * 0.1))
    d.text((W * 0.09, H * 0.5), '6.1 L', fill=(255, 190, 120), font=font(H * 0.09, False))
    dial(d, W * 0.52, H * 0.5, H * 0.46, 200, 20, face=(20, 18, 16), ring=(255, 140, 40), tick=(255, 220, 190), label='km/h',
         num_col=(255, 200, 150))
    d.rounded_rectangle([W * 0.74, H * 0.12, W * 0.94, H * 0.88], 18, fill=(22, 20, 18), outline=(255, 140, 40), width=2)
    return [dict(kind='speed', cx=0.52, cy=0.5, r=0.46 * H / W, a0=225, a1=-45, max=200),
            dict(kind='rpm_bar', x=0.76, y=0.2, w=0.16, h=0.08), dict(kind='fuel_bar', x=0.76, y=0.7, w=0.16, h=0.06),
            dict(kind='gear', x=0.84, y=0.42, size=0.16)]


def cl_digital_arcs(d, W, H):
    d.rectangle([0, 0, W, H], fill=(4, 6, 10))
    d.arc([W * 0.03, H * 0.05, W * 0.43, H * 1.25], 150, 390, fill=(25, 60, 90), width=14)
    d.arc([W * 0.57, H * 0.05, W * 0.97, H * 1.25], 150, 390, fill=(25, 60, 90), width=14)
    for k in range(9):
        a = math.radians(150 + k * 30)
        cx, cy, r = W * 0.23, H * 0.65, W * 0.2
        d.line([(cx + math.cos(a) * r * 0.82, cy + math.sin(a) * r * 0.82), (cx + math.cos(a) * r * 0.9, cy + math.sin(a) * r * 0.9)],
               fill=(120, 190, 255), width=2)
    d.text((W * 0.4, H * 0.08), 'TERRAIN: AUTO', fill=(90, 220, 160), font=font(H * 0.07, False))
    return [dict(kind='speed_arc', cx=0.23, cy=0.65, r=0.2, a0=210, a1=-30, max=220, color=0x37A0FF),
            dict(kind='rpm_arc', cx=0.77, cy=0.65, r=0.2, a0=210, a1=-30, max=7000, color=0xFF7840),
            dict(kind='digital_speed', x=0.23, y=0.52, size=0.3), dict(kind='gear', x=0.77, y=0.52, size=0.3),
            dict(kind='fuel_bar', x=0.4, y=0.86, w=0.2, h=0.05)]


def cl_rugged(d, W, H):
    d.rectangle([0, 0, W, H], fill=(16, 16, 15))
    dial(d, W * 0.36, H * 0.5, H * 0.44, 200, 20, face=(8, 8, 8), ring=(120, 120, 110), tick=(255, 190, 90), label='km/h',
         num_col=(255, 200, 110))
    dial(d, W * 0.64, H * 0.5, H * 0.44, 6, 1, face=(8, 8, 8), ring=(120, 120, 110), tick=(255, 190, 90), red_from=4.5, label='rpm x1000',
         num_col=(255, 200, 110))
    for x, lab in ((0.1, 'TEMP'), (0.9, 'FUEL')):
        dial(d, W * x, H * 0.55, H * 0.22, 4, 1, face=(8, 8, 8), ring=(120, 120, 110), tick=(255, 190, 90), minor=False, fsz=1)
        d.text((W * x - 18, H * 0.82), lab, fill=(200, 170, 110), font=font(H * 0.06))
    return [dict(kind='speed', cx=0.36, cy=0.5, r=0.44 * H / W, a0=225, a1=-45, max=200),
            dict(kind='rpm', cx=0.64, cy=0.5, r=0.44 * H / W, a0=225, a1=-45, max=6000),
            dict(kind='fuel', cx=0.9, cy=0.55, r=0.22 * H / W, a0=225, a1=-45, max=1), dict(kind='temp', cx=0.1, cy=0.55, r=0.22 * H / W, a0=225,
                                                                                         a1=-45, max=1)]


def cl_sport(d, W, H):
    d.rectangle([0, 0, W, H], fill=(5, 5, 6))
    cx, cy, r = W * 0.5, H * 0.52, H * 0.47
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(235, 200, 30), outline=(200, 205, 212), width=6)
    for k in range(0, 91):
        v = k / 10
        a = math.radians(225 - 270 * v / 9)
        big = k % 10 == 0
        col = (200, 20, 20) if v >= 7.5 else (15, 15, 15)
        d.line([(cx + math.cos(a) * r * (0.76 if big else 0.86), cy - math.sin(a) * r * (0.76 if big else 0.86)),
                (cx + math.cos(a) * r * 0.94, cy - math.sin(a) * r * 0.94)], fill=col, width=4 if big else 1)
        if big:
            t = str(int(v))
            f = font(r * 0.17)
            tw = d.textlength(t, font=f)
            d.text((cx + math.cos(a) * r * 0.6 - tw / 2, cy - math.sin(a) * r * 0.6 - r * 0.1), t, fill=(15, 15, 15), font=f)
    d.text((cx - 30, cy + r * 0.45), 'RPM x1000', fill=(40, 40, 40), font=font(r * 0.08, False))
    for x0 in (0.04, 0.73):
        d.rounded_rectangle([W * x0, H * 0.14, W * (x0 + 0.23), H * 0.86], 12, fill=(12, 14, 18), outline=(200, 30, 30), width=2)
    d.text((W * 0.06, H * 0.2), 'LAP 01:24.6', fill=(255, 255, 255), font=font(H * 0.075, False))
    d.text((W * 0.06, H * 0.36), 'OIL 98°C', fill=(200, 200, 200), font=font(H * 0.065, False))
    return [dict(kind='rpm', cx=0.5, cy=0.52, r=0.47 * H / W, a0=225, a1=-45, max=9000, color=0xD01010),
            dict(kind='digital_speed', x=0.845, y=0.45, size=0.3), dict(kind='gear', x=0.5, y=0.7, size=0.2),
            dict(kind='fuel_bar', x=0.06, y=0.74, w=0.19, h=0.05)]


def cl_bike_round(d, W, H):
    """Cruiser: chrome-ringed round speedo on the tank console, odometer window, warning lamps."""
    d.rectangle([0, 0, W, H], fill=(8, 8, 9))
    cx, cy, r = W * 0.5, H * 0.5, H * 0.47
    d.ellipse([cx - r * 1.02, cy - r * 1.02, cx + r * 1.02, cy + r * 1.02], fill=(200, 204, 210))
    dial(d, cx, cy, r * 0.94, 200, 20, face=(16, 14, 12), ring=(230, 225, 210), tick=(245, 235, 210), label='km/h', num_col=(240, 225, 190))
    d.rounded_rectangle([cx - r * 0.3, cy + r * 0.5, cx + r * 0.3, cy + r * 0.68], 4, fill=(30, 26, 20))
    d.text((cx - r * 0.25, cy + r * 0.51), '04712', fill=(240, 200, 120), font=font(r * 0.13, False))
    for i, c in enumerate([(60, 200, 90), (70, 140, 255), (255, 170, 40), (230, 50, 40)]):
        x = W * 0.08 + i * W * 0.05
        d.ellipse([x, H * 0.42, x + W * 0.035, H * 0.42 + W * 0.035], fill=(c[0] // 5, c[1] // 5, c[2] // 5), outline=c)
    return [dict(kind='speed', cx=0.5, cy=0.5, r=0.47 * 0.94 * H / W, a0=225, a1=-45, max=200)]


def cl_bike_tft(d, W, H):
    """Supersport: colour TFT with a sweeping rev bar, big digital speed, lean / lap info."""
    d.rectangle([0, 0, W, H], fill=(3, 4, 6))
    for k in range(40):
        x0 = W * 0.04 + k * W * 0.023
        hgt = H * (0.06 + 0.16 * (k / 40) ** 1.6)
        col = (60, 200, 255) if k < 28 else (255, 190, 40) if k < 35 else (255, 40, 40)
        d.rectangle([x0, H * 0.3 - hgt, x0 + W * 0.016, H * 0.3], outline=col)
    for k, lab in enumerate(['2', '4', '6', '8', '10', '12', '14']):
        d.text((W * 0.04 + k * W * 0.13, H * 0.32), lab, fill=(160, 170, 180), font=font(H * 0.06, False))
    d.text((W * 0.06, H * 0.78), 'LEAN  0°   TC 3   RIDE: SPORT', fill=(120, 210, 255), font=font(H * 0.06, False))
    d.rounded_rectangle([W * 0.7, H * 0.42, W * 0.96, H * 0.74], 8, outline=(60, 200, 255), width=2)
    return [dict(kind='rpm_bar', x=0.04, y=0.12, w=0.92, h=0.14, max=14000), dict(kind='digital_speed', x=0.4, y=0.5, size=0.34),
            dict(kind='gear', x=0.83, y=0.58, size=0.26), dict(kind='fuel_bar', x=0.06, y=0.9, w=0.3, h=0.04)]


def cl_bike_mini(d, W, H):
    """Scrambler: small round analogue speedo with an LCD strip (gear, fuel)."""
    d.rectangle([0, 0, W, H], fill=(14, 14, 13))
    cx, cy, r = W * 0.36, H * 0.5, H * 0.46
    dial(d, cx, cy, r, 160, 20, face=(232, 228, 214), ring=(40, 40, 38), tick=(20, 20, 18), label='km/h', num_col=(20, 20, 18))
    d.rounded_rectangle([W * 0.62, H * 0.18, W * 0.95, H * 0.82], 10, fill=(150, 170, 120), outline=(40, 40, 38), width=3)
    d.text((W * 0.65, H * 0.22), 'TRIP 128.4', fill=(30, 40, 25), font=font(H * 0.08, False))
    return [dict(kind='speed', cx=0.36, cy=0.5, r=0.46 * H / W, a0=225, a1=-45, max=160, color=0xD02010), dict(kind='gear', x=0.785, y=0.55, size=0.22),
            dict(kind='fuel_bar', x=0.66, y=0.72, w=0.26, h=0.05)]


def cl_supra_analog(d, W, H):
    """90s Japanese GT: big central tachometer, speedometer left, boost / oil / fuel gauges right, orange needles."""
    d.rectangle([0, 0, W, H], fill=(8, 8, 10))
    dial(d, W * 0.5, H * 0.52, H * 0.47, 9, 1, face=(12, 12, 14), ring=(90, 92, 98), tick=(240, 240, 240), red_from=7, label='x1000r/min')
    dial(d, W * 0.2, H * 0.55, H * 0.36, 180, 20, face=(12, 12, 14), ring=(90, 92, 98), tick=(240, 240, 240), label='km/h')
    for i, lab in enumerate(('TURBO', 'OIL', 'FUEL')):
        cx = W * (0.74 + (i % 2) * 0.13)
        cy = H * (0.3 + (i // 2) * 0.42) if i < 2 else H * 0.72
        cx = W * 0.8 if i == 2 else cx
        dial(d, cx, cy, H * 0.16, 4, 1, face=(12, 12, 14), ring=(90, 92, 98), tick=(240, 240, 240), minor=False, fsz=1)
        f = font(H * 0.05)
        d.text((cx - d.textlength(lab, font=f) / 2, cy + H * 0.06), lab, fill=(200, 200, 205), font=f)
    return [dict(kind='rpm', cx=0.5, cy=0.52, r=0.47 * H / W, a0=225, a1=-45, max=9000, color=0xFF7A1A),
            dict(kind='speed', cx=0.2, cy=0.55, r=0.36 * H / W, a0=225, a1=-45, max=180, color=0xFF7A1A),
            dict(kind='fuel', cx=0.8, cy=0.72, r=0.16 * H / W, a0=225, a1=-45, max=1, color=0xFF7A1A)]


def cl_police(d, W, H):
    out = cl_digital_arcs(d, W, H)
    d.rectangle([W * 0.38, H * 0.05, W * 0.62, H * 0.2], fill=(4, 6, 10))
    d.rounded_rectangle([W * 0.4, H * 0.06, W * 0.6, H * 0.2], 6, fill=(30, 10, 10), outline=(230, 40, 40), width=2)
    d.text((W * 0.415, H * 0.08), 'RADAR  ON', fill=(255, 80, 80), font=font(H * 0.07))
    return out


def cl_basic(d, W, H):
    d.rounded_rectangle([0, 0, W - 1, H - 1], 20, fill=(30, 32, 34))
    dial(d, W * 0.3, H * 0.52, H * 0.44, 220, 20, face=(238, 238, 232), ring=(60, 60, 64), tick=(20, 20, 22), label='km/h')
    dial(d, W * 0.7, H * 0.52, H * 0.44, 7, 1, face=(238, 238, 232), ring=(60, 60, 64), tick=(20, 20, 22), red_from=6, label='rpm x1000')
    d.rectangle([W * 0.46, H * 0.7, W * 0.54, H * 0.86], fill=(20, 40, 30))
    return [dict(kind='speed', cx=0.3, cy=0.52, r=0.44 * H / W, a0=225, a1=-45, max=220, color=0xD02020),
            dict(kind='rpm', cx=0.7, cy=0.52, r=0.44 * H / W, a0=225, a1=-45, max=7000, color=0xD02020),
            dict(kind='fuel_bar', x=0.465, y=0.74, w=0.07, h=0.06)]


def cl_commercial(d, W, H):
    d.rectangle([0, 0, W, H], fill=(20, 22, 26))
    dial(d, W * 0.26, H * 0.5, H * 0.44, 160, 20, face=(10, 12, 14), ring=(140, 150, 160), label='km/h')
    dial(d, W * 0.74, H * 0.5, H * 0.44, 5, 1, face=(10, 12, 14), ring=(140, 150, 160), red_from=4, label='rpm x1000')
    d.rounded_rectangle([W * 0.42, H * 0.12, W * 0.58, H * 0.88], 8, fill=(8, 30, 30), outline=(60, 160, 160), width=2)
    d.text((W * 0.44, H * 0.18), 'LOAD', fill=(120, 230, 220), font=font(H * 0.07))
    d.text((W * 0.44, H * 0.3), '640 kg', fill=(200, 240, 240), font=font(H * 0.07, False))
    return [dict(kind='speed', cx=0.26, cy=0.5, r=0.44 * H / W, a0=225, a1=-45, max=160),
            dict(kind='rpm', cx=0.74, cy=0.5, r=0.44 * H / W, a0=225, a1=-45, max=5000),
            dict(kind='fuel_bar', x=0.44, y=0.72, w=0.12, h=0.06), dict(kind='gear', x=0.5, y=0.55, size=0.14)]


def cl_truck(d, W, H):
    d.rectangle([0, 0, W, H], fill=(14, 14, 16))
    dial(d, W * 0.3, H * 0.5, H * 0.46, 120, 10, face=(4, 4, 5), ring=(170, 170, 170), label='km/h')
    dial(d, W * 0.62, H * 0.5, H * 0.36, 3, 0.5, face=(4, 4, 5), ring=(170, 170, 170), red_from=2.5, label='rpm x1000', minor=False)
    for i, lab in enumerate(('AIR 1', 'AIR 2')):
        cx = W * (0.84 + 0.0), H * (0.3 + i * 0.42)
        dial(d, cx[0], cx[1], H * 0.17, 10, 2, face=(4, 4, 5), ring=(170, 170, 170), minor=False, fsz=H * 0.04)
        d.text((cx[0] + H * 0.19, cx[1] - 8), lab, fill=(180, 180, 180), font=font(H * 0.05, False))
    d.text((W * 0.05, H * 0.06), 'TACHO', fill=(90, 200, 255), font=font(H * 0.06))
    return [dict(kind='speed', cx=0.3, cy=0.5, r=0.46 * H / W, a0=225, a1=-45, max=120),
            dict(kind='rpm', cx=0.62, cy=0.5, r=0.36 * H / W, a0=225, a1=-45, max=3000),
            dict(kind='air', cx=0.84, cy=0.3, r=0.17 * H / W, a0=225, a1=-45, max=1),
            dict(kind='air', cx=0.84, cy=0.72, r=0.17 * H / W, a0=225, a1=-45, max=1),
            dict(kind='fuel_bar', x=0.05, y=0.86, w=0.16, h=0.05)]


CLUSTERS = dict(supra_analog=cl_supra_analog, bike_round=cl_bike_round, bike_tft=cl_bike_tft, bike_mini=cl_bike_mini, analog_modern=cl_analog_modern, compact=cl_compact, digital_arcs=cl_digital_arcs, rugged=cl_rugged, sport=cl_sport,
                police=cl_police, basic=cl_basic, commercial=cl_commercial, truck=cl_truck)


# -------------------------------------------------------------------------------- screen styles
def map_bg(d, W, H, bg, road, seed, x1=None):
    random.seed(seed)
    x1 = x1 or W
    d.rectangle([0, 0, x1, H], fill=bg)
    for k in range(16):
        x = random.randint(0, x1)
        d.line([x, 0, x + random.randint(-80, 80), H], fill=road, width=random.choice([4, 6, 10]))
        y = random.randint(0, H)
        d.line([0, y, x1, y + random.randint(-50, 50)], fill=road, width=random.choice([4, 6]))
    for k in range(4):
        x, y = random.randint(0, x1 - 60), random.randint(0, H - 50)
        d.rectangle([x, y, x + random.randint(30, 70), y + random.randint(20, 50)], fill=tuple(min(255, c + 12) for c in bg))


def status_bar(d, W, bg=(10, 13, 18), fg=(230, 230, 240), text='12:45'):
    d.rectangle([0, 0, W, 26], fill=bg)
    d.text((W - 72, 4), text, fill=fg, font=font(18))
    d.text((10, 4), '22°C', fill=fg, font=font(16, False))


def sc_nav_blue(d, W, H):
    map_bg(d, W, H, (26, 40, 54), (46, 66, 86), 1, int(W * 0.68))
    mx = W * 0.68
    d.line([(mx * 0.5, H), (mx * 0.5, H * 0.6), (mx * 0.7, H * 0.45), (mx * 0.72, H * 0.15)], fill=(60, 160, 255), width=9)
    d.rounded_rectangle([12, 34, mx * 0.62, 78], 8, fill=(20, 110, 70))
    d.text((24, 42), '350 m  Main St', fill=(255, 255, 255), font=font(22))
    d.rectangle([mx, 26, W, H], fill=(14, 18, 26))
    d.rounded_rectangle([mx + 12, 40, W - 12, 120], 10, fill=(70, 40, 110))
    d.text((mx + 20, 130), 'Radio TC', fill=(230, 230, 240), font=font(20))
    d.text((mx + 20, 156), '101.4 FM', fill=(150, 160, 180), font=font(16, False))
    status_bar(d, W)


def sc_media_orange(d, W, H):
    d.rectangle([0, 0, W, H], fill=(20, 16, 14))
    d.rounded_rectangle([20, 40, 200, 220], 14, fill=(240, 120, 40))
    d.polygon([(90, 95), (90, 165), (150, 130)], fill=(255, 255, 255))
    d.text((220, 50), 'Now playing', fill=(255, 170, 110), font=font(18, False))
    d.text((220, 80), 'City Lights', fill=(255, 255, 255), font=font(30))
    d.text((220, 122), 'The Commuters', fill=(200, 190, 180), font=font(20, False))
    d.rectangle([220, 170, W - 30, 176], fill=(70, 50, 40))
    d.rectangle([220, 170, 360, 176], fill=(255, 140, 50))
    for i in range(5):
        d.ellipse([220 + i * 56, 190, 260 + i * 56, 230], fill=(60, 44, 36))
    status_bar(d, W, (30, 22, 18), (255, 220, 200))


def sc_terrain(d, W, H):
    d.rectangle([0, 0, W, H], fill=(12, 18, 16))
    cx, cy = W * 0.28, H * 0.58
    d.rounded_rectangle([cx - 60, cy - 90, cx + 60, cy + 90], 30, outline=(120, 220, 160), width=4)
    for dx, dy in ((-70, -60), (70, -60), (-70, 60), (70, 60)):
        d.rounded_rectangle([cx + dx - 12, cy + dy - 25, cx + dx + 12, cy + dy + 25], 5, fill=(90, 200, 140))
    d.text((W * 0.5, 40), 'AWD  ACTIVE', fill=(140, 240, 180), font=font(24))
    d.text((W * 0.5, 80), 'Pitch  4°', fill=(200, 230, 210), font=font(20, False))
    d.text((W * 0.5, 110), 'Roll   2°', fill=(200, 230, 210), font=font(20, False))
    d.arc([W * 0.55, 140, W * 0.85, 250], 180, 360, fill=(90, 200, 140), width=4)
    status_bar(d, W, (8, 14, 12), (200, 240, 210))


def sc_camera(d, W, H):
    d.rectangle([0, 0, W, H], fill=(70, 74, 70))
    d.polygon([(0, H), (W, H), (W * 0.62, H * 0.35), (W * 0.38, H * 0.35)], fill=(96, 98, 92))
    for col, f in (((240, 60, 40), 0.85), ((250, 200, 40), 0.65), ((60, 210, 90), 0.45)):
        y = H * f
        t = (y - H * 0.35) / (H * 0.65)
        xl, xr = W * (0.38 - 0.25 * t), W * (0.62 + 0.25 * t)
        d.line([xl, y, xr, y], fill=col, width=4)
    d.line([W * 0.2, H, W * 0.42, H * 0.4], fill=(250, 200, 40), width=4)
    d.line([W * 0.8, H, W * 0.58, H * 0.4], fill=(250, 200, 40), width=4)
    d.rectangle([0, 0, W, 30], fill=(0, 0, 0))
    d.text((10, 5), 'REAR CAMERA  -  TOW ASSIST', fill=(255, 255, 255), font=font(18))


def sc_telemetry(d, W, H):
    d.rectangle([0, 0, W, H], fill=(8, 8, 10))
    cx, cy, r = W * 0.22, H * 0.58, 80
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(200, 40, 40), width=3)
    d.ellipse([cx - r / 2, cy - r / 2, cx + r / 2, cy + r / 2], outline=(120, 40, 40), width=2)
    d.line([cx - r, cy, cx + r, cy], fill=(90, 30, 30))
    d.line([cx, cy - r, cx, cy + r], fill=(90, 30, 30))
    d.ellipse([cx + 25, cy - 30, cx + 41, cy - 14], fill=(255, 220, 60))
    d.text((W * 0.45, 40), 'PERFORMANCE', fill=(255, 60, 60), font=font(24))
    for i, (k, v) in enumerate((('0-100', '3.9 s'), ('Vmax', '298'), ('Boost', '1.2 bar'), ('Oil', '102°C'))):
        d.text((W * 0.45, 80 + i * 36), k, fill=(170, 170, 180), font=font(20, False))
        d.text((W * 0.75, 80 + i * 36), v, fill=(255, 255, 255), font=font(20))
    status_bar(d, W, (20, 6, 6))


def sc_mdt(d, W, H):
    d.rectangle([0, 0, W, H], fill=(10, 14, 30))
    map_bg(d, W, H, (18, 26, 46), (34, 48, 80), 7, int(W * 0.55))
    for x, y in ((80, 90), (200, 160), (150, 210)):
        d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=(60, 140, 255))
    d.rectangle([W * 0.55, 26, W, H], fill=(8, 10, 22))
    d.text((W * 0.58, 36), 'PLATE CHECK', fill=(120, 180, 255), font=font(18))
    d.rounded_rectangle([W * 0.58, 64, W - 14, 100], 6, fill=(240, 240, 232))
    d.text((W * 0.61, 70), 'TC 4471', fill=(20, 20, 20), font=font(22))
    d.text((W * 0.58, 112), 'STATUS: CLEAR', fill=(90, 230, 120), font=font(18))
    d.text((W * 0.58, 140), 'UNIT 12 - PATROL', fill=(200, 210, 240), font=font(16, False))
    d.rounded_rectangle([W * 0.58, 180, W - 14, 226], 8, fill=(140, 20, 20))
    d.text((W * 0.61, 190), 'DISPATCH', fill=(255, 255, 255), font=font(20))
    status_bar(d, W, (6, 8, 18))


def sc_taximeter(d, W, H):
    d.rectangle([0, 0, W, H], fill=(6, 6, 6))
    d.rounded_rectangle([16, 36, W * 0.62, 150], 10, fill=(20, 8, 8))
    d.text((30, 44), 'FARE', fill=(255, 90, 60), font=font(18))
    d.text((40, 70), '12.40', fill=(255, 60, 40), font=font(64))
    d.text((30, 168), 'DIST  4.2 km', fill=(255, 200, 60), font=font(20, False))
    d.text((30, 200), 'TIME  11:08', fill=(255, 200, 60), font=font(20, False))
    d.rounded_rectangle([W * 0.66, 36, W - 16, 110], 10, fill=(40, 140, 60))
    d.text((W * 0.7, 56), 'HIRED', fill=(255, 255, 255), font=font(26))
    d.rounded_rectangle([W * 0.66, 124, W - 16, 196], 10, fill=(255, 205, 30))
    d.text((W * 0.7, 144), 'TARIFF 1', fill=(20, 20, 20), font=font(22))
    status_bar(d, W, (6, 6, 6), (255, 200, 60))


def sc_route(d, W, H):
    d.rectangle([0, 0, W, H], fill=(236, 238, 240))
    map_bg(d, W, H, (214, 220, 226), (250, 250, 250), 11, int(W * 0.5))
    d.line([(40, 230), (90, 150), (180, 120), (230, 50)], fill=(230, 90, 40), width=7)
    for i, (n, t) in enumerate((('1  Harbor Rd 12', '10:40'), ('2  Mill Lane 3', '10:55'), ('3  Oak Ave 87', '11:10'), ('4  Depot', '11:40'))):
        y = 36 + i * 50
        d.rounded_rectangle([W * 0.52, y, W - 12, y + 42], 8, fill=(255, 255, 255) if i else (255, 230, 210))
        d.text((W * 0.54, y + 10), n, fill=(30, 30, 30), font=font(17, i == 0))
        d.text((W - 66, y + 10), t, fill=(200, 80, 30), font=font(16, False))
    status_bar(d, W, (40, 44, 50))


def sc_logistics(d, W, H):
    d.rectangle([0, 0, W, H], fill=(14, 20, 30))
    d.text((16, 36), 'TACHOGRAPH', fill=(90, 200, 255), font=font(22))
    for i, (k, v, f) in enumerate((('Driving', '3h 12m', 0.7), ('Break due', '1h 18m', 0.3), ('Week', '31h', 0.55))):
        y = 76 + i * 52
        d.text((16, y), k, fill=(200, 210, 230), font=font(18, False))
        d.text((W * 0.36, y), v, fill=(255, 255, 255), font=font(18))
        bar_gauge(d, W * 0.58, y + 4, W * 0.38, 16, f, (90, 200, 255))
    d.text((16, 230), 'NEXT: TC Depot 18 km', fill=(255, 190, 80), font=font(16, False))
    status_bar(d, W, (8, 12, 20))


def sc_radio_90s(d, W, H):
    """No touch screen in the 90s: a double-DIN cassette head unit with an amber display."""
    d.rectangle([0, 0, W, H], fill=(18, 18, 20))
    d.rounded_rectangle([W * 0.04, H * 0.08, W * 0.96, H * 0.46], 8, fill=(26, 26, 28), outline=(60, 60, 64), width=2)
    d.rounded_rectangle([W * 0.3, H * 0.14, W * 0.7, H * 0.4], 6, fill=(30, 14, 4))
    d.text((W * 0.33, H * 0.18), 'FM1  88.7', fill=(255, 160, 40), font=font(H * 0.12, False))
    for i in range(6):
        x = W * 0.06 + i * W * 0.15
        d.rounded_rectangle([x, H * 0.52, x + W * 0.12, H * 0.62], 4, fill=(40, 40, 44))
        d.text((x + W * 0.045, H * 0.53), str(i + 1), fill=(210, 210, 215), font=font(H * 0.07, False))
    d.rounded_rectangle([W * 0.08, H * 0.7, W * 0.92, H * 0.88], 6, fill=(10, 10, 12), outline=(70, 70, 74), width=2)
    for i, c in enumerate(((60, 140, 255), (255, 120, 40), (60, 200, 90))):
        d.ellipse([W * (0.2 + i * 0.3) - 14, H * 0.79 - 14, W * (0.2 + i * 0.3) + 14, H * 0.79 + 14], outline=c, width=3)


SCREENS = dict(radio_90s=sc_radio_90s, nav_blue=sc_nav_blue, media_orange=sc_media_orange, terrain=sc_terrain, camera=sc_camera, telemetry=sc_telemetry, mdt=sc_mdt,
               taximeter=sc_taximeter, route=sc_route, logistics=sc_logistics)


def build_dash_atlas(specs, path):
    im = Image.new('RGBA', (DW, DH), (0, 0, 0, 255))
    meta = {}
    for idx, S in enumerate(specs):
        cs = S.get('cluster_style', 'analog_modern')
        ss = S.get('screen_style', 'nav_blue')
        x, y = cell_origin(idx)
        c = Image.new('RGBA', (CW, CH), (0, 0, 0, 255))
        dials = CLUSTERS[cs](ImageDraw.Draw(c), CW, CH)
        im.paste(c, (x, y))
        s = Image.new('RGBA', (CW, CH), (0, 0, 0, 255))
        SCREENS[ss](ImageDraw.Draw(s), CW, CH)
        im.paste(s, (x + CW, y))
        S['_dash_idx'] = idx
        meta[S['id']] = dict(cluster_style=cs, screen_style=ss, dials=dials, idx=idx)
    im.save(path)
    return meta
