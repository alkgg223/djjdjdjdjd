"""Shared vehicle texture atlas (1024x1024; UV regions are given in 256-space in geom.UV)."""
import math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from geom import UV

S = 1024
random.seed(7)
np.random.seed(7)


def font(sz, bold=True):
    names = ['DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf']
    for p in ['/usr/share/fonts/truetype/dejavu/', '/usr/share/fonts/dejavu/']:
        try:
            return ImageFont.truetype(p + names[0], sz)
        except Exception:
            pass
    return ImageFont.load_default()


def box(name):
    u0, v0, u1, v1 = UV[name]
    return int(round(u0 * S)), int(round(v0 * S)), int(round(u1 * S)), int(round(v1 * S))


def paste_array(im, name, arr):
    x0, y0, x1, y1 = box(name)
    a = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA').resize((x1 - x0, y1 - y0), Image.LANCZOS)
    im.paste(a, (x0, y0))


def region(name):
    x0, y0, x1, y1 = box(name)
    return Image.new('RGBA', (x1 - x0, y1 - y0), (255, 255, 255, 255)), (x0, y0)


def text_center(d, w, h, text, fg, size_frac=0.7, y_off=0, bold=True):
    size = int(h * size_frac)
    f = font(size, bold)
    while d.textlength(text, font=f) > w - 8 and size > 6:
        size -= 1
        f = font(size, bold)
    tw = d.textlength(text, font=f)
    d.text(((w - tw) / 2, (h - size) / 2 - size * 0.12 + y_off), text, fill=fg, font=f)


def make_atlas(path):
    im = Image.new('RGBA', (S, S), (255, 255, 255, 255))

    # ---------------- grille: chrome-edged horizontal slats with depth shading
    r, o = region('grille')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(12, 13, 15, 255))
    for y in range(6, h - 4, 18):
        for k in range(10):
            c = int(30 + k * 6)
            d.line([4, y + k, w - 5, y + k], fill=(c, c + 2, c + 5, 255))
        d.line([4, y, w - 5, y], fill=(170, 176, 184, 255), width=2)
    for x in range(0, w, w // 6):
        d.line([x, 0, x, h], fill=(8, 8, 9, 255), width=3)
    im.paste(r, o)
    # ---------------- honeycomb mesh
    r, o = region('grille_fine')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(9, 9, 10, 255))
    s = 14
    for j, y in enumerate(range(0, h + s, int(s * 0.87))):
        for x in range(-s + (j % 2) * s // 2, w + s, s):
            pts = [(x + s * 0.5 * math.cos(math.radians(a)), y + s * 0.5 * math.sin(math.radians(a))) for a in range(30, 390, 60)]
            d.polygon(pts, fill=(34, 36, 39, 255), outline=(58, 61, 66, 255))
            d.polygon([(px * 0.55 + x * 0.45, py * 0.55 + y * 0.45) for px, py in pts], fill=(6, 6, 7, 255))
    im.paste(r, o)
    # ---------------- plate
    r, o = region('plate')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rounded_rectangle([0, 0, w - 1, h - 1], 6, fill=(25, 25, 27, 255))
    d.rounded_rectangle([4, 4, w - 5, h - 5], 5, fill=(240, 240, 234, 255))
    d.rectangle([4, 4, 34, h - 5], fill=(24, 58, 168, 255))
    f = font(int(h * 0.34))
    d.text((8, h * 0.3), 'TC', fill=(255, 255, 255, 255), font=f)
    im.paste(r, o)
    # ---------------- LED strip
    r, o = region('led')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(215, 222, 232, 255))
    for x in range(2, w, 10):
        d.rounded_rectangle([x, h * 0.25, x + 6, h * 0.75], 2, fill=(255, 255, 255, 255))
    im.paste(r, o)
    # ---------------- logo: generic hexagonal shield with a "T" monogram (no real brand)
    r, o = region('logo')
    d = ImageDraw.Draw(r)
    w, h = r.size
    hexp = [(w / 2 + (w / 2 - 1) * math.cos(math.radians(a)), h / 2 + (h / 2 - 1) * math.sin(math.radians(a))) for a in range(-90, 270, 60)]
    d.polygon(hexp, fill=(212, 216, 222, 255))
    inner = [(w / 2 + (w / 2 - 7) * math.cos(math.radians(a)), h / 2 + (h / 2 - 7) * math.sin(math.radians(a))) for a in range(-90, 270, 60)]
    d.polygon(inner, fill=(28, 32, 40, 255))
    d.rectangle([w * 0.28, h * 0.3, w * 0.72, h * 0.4], fill=(226, 230, 236, 255))
    d.rectangle([w * 0.45, h * 0.3, w * 0.55, h * 0.74], fill=(226, 230, 236, 255))
    im.paste(r, o)
    # ---------------- tread
    r, o = region('tread')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(40, 41, 43, 255))
    for x in range(-20, w + 20, 22):
        d.polygon([(x, 4), (x + 12, 4), (x + 18, h / 2 - 4), (x + 6, h / 2 - 4)], fill=(52, 53, 56, 255))
        d.polygon([(x + 6, h / 2 + 4), (x + 18, h / 2 + 4), (x + 12, h - 4), (x, h - 4)], fill=(52, 53, 56, 255))
        d.line([(x + 15, 6), (x + 21, h / 2 - 6)], fill=(22, 22, 23, 255), width=2)
    d.rectangle([0, h / 2 - 3, w, h / 2 + 3], fill=(18, 18, 19, 255))
    d.rectangle([0, h * 0.2 - 1, w, h * 0.2 + 1], fill=(22, 22, 23, 255))
    d.rectangle([0, h * 0.8 - 1, w, h * 0.8 + 1], fill=(22, 22, 23, 255))
    im.paste(r, o)
    # ---------------- analog cluster with centre TFT
    r, o = region('gauges')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rounded_rectangle([0, 0, w - 1, h - 1], 18, fill=(6, 7, 9, 255))
    for (cx, rad, label, maxv) in ((w * 0.24, h * 0.42, 'km/h', 240), (w * 0.76, h * 0.42, 'x1000rpm', 8)):
        cy = h * 0.52
        d.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=(14, 16, 20, 255), outline=(150, 156, 168, 255), width=3)
        n = 12 if maxv == 240 else 8
        for k in range(n * 2 + 1):
            a = math.radians(225 - k * 270 / (n * 2))
            big = k % 2 == 0
            p0 = (cx + math.cos(a) * rad * (0.78 if big else 0.86), cy - math.sin(a) * rad * (0.78 if big else 0.86))
            p1 = (cx + math.cos(a) * rad * 0.94, cy - math.sin(a) * rad * 0.94)
            red = maxv == 8 and k >= n * 2 - 3
            d.line([p0, p1], fill=(230, 60, 50, 255) if red else (230, 234, 242, 255), width=3 if big else 1)
            if big and k % 4 == 0:
                v = int(maxv * k / (n * 2))
                f = font(int(rad * 0.16))
                t = str(v)
                tw = d.textlength(t, font=f)
                pp = (cx + math.cos(a) * rad * 0.62, cy - math.sin(a) * rad * 0.62)
                d.text((pp[0] - tw / 2, pp[1] - rad * 0.09), t, fill=(210, 214, 222, 255), font=f)
        f = font(int(rad * 0.13), False)
        tw = d.textlength(label, font=f)
        d.text((cx - tw / 2, cy + rad * 0.35), label, fill=(150, 156, 168, 255), font=f)
    # centre TFT
    d.rounded_rectangle([w * 0.43, h * 0.18, w * 0.57, h * 0.86], 8, fill=(10, 22, 40, 255), outline=(40, 70, 110, 255), width=2)
    f = font(int(h * 0.11))
    d.text((w * 0.455, h * 0.26), 'D', fill=(120, 220, 255, 255), font=f)
    f2 = font(int(h * 0.06), False)
    d.text((w * 0.445, h * 0.48), '21.5°C', fill=(200, 210, 230, 255), font=f2)
    d.text((w * 0.445, h * 0.6), '412 km', fill=(200, 210, 230, 255), font=f2)
    d.rectangle([w * 0.445, h * 0.74, w * 0.555, h * 0.78], fill=(30, 50, 80, 255))
    d.rectangle([w * 0.445, h * 0.74, w * 0.52, h * 0.78], fill=(90, 200, 120, 255))
    im.paste(r, o)
    # ---------------- headlamp reflector / projector chrome
    r, o = region('headlamp')
    w, h = r.size
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = w / 2, h / 2
    rr = np.sqrt(((xx - cx) / (w / 2)) ** 2 + ((yy - cy) / (h / 2)) ** 2)
    base = 200 + 55 * np.cos(rr * 18) * np.exp(-rr * 1.2)
    base = base * (0.75 + 0.25 * (1 - rr.clip(0, 1)))
    hl = np.exp(-((xx - cx * 0.7) ** 2 + (yy - cy * 0.6) ** 2) / (w * 2.0))
    v = np.clip(base + hl * 80, 120, 255)
    arr = np.stack([v, v, np.clip(v + 6, 0, 255), np.full_like(v, 255)], -1)
    r = Image.fromarray(arr.astype(np.uint8), 'RGBA')
    im.paste(r, o)
    # ---------------- taillamp (prismatic red optic; vertex colour tints)
    r, o = region('taillamp')
    w, h = r.size
    yy, xx = np.mgrid[0:h, 0:w]
    v = 225 + 25 * np.sin(xx * 0.9) * np.sin(yy * 0.9) - 25 * (np.abs(((yy // 10) % 2) - 0.5))
    arr = np.stack([v, v, v, np.full_like(v, 255)], -1)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'), o)
    # ---------------- LED tail bar / reflector
    r, o = region('led_tail')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(200, 200, 200, 255))
    for x in range(0, w, 8):
        d.rectangle([x + 1, 3, x + 5, h - 4], fill=(255, 255, 255, 255))
    im.paste(r, o)
    r, o = region('reflector')
    d = ImageDraw.Draw(r)
    w, h = r.size
    for y in range(0, h, 6):
        for x in range(0, w, 6):
            d.polygon([(x + 3, y), (x + 6, y + 3), (x + 3, y + 6), (x, y + 3)], fill=(255 - (x + y) % 40, 240, 240, 255))
    im.paste(r, o)
    # ---------------- fabric + carpet + leather
    for name, amp, rib in (('fabric', 16, 7), ('carpet', 24, 0)):
        x0, y0, x1, y1 = box(name)
        w, h = x1 - x0, y1 - y0
        n = np.random.randint(-amp, amp, (h, w))
        v = 222 + n
        if rib:
            v = v - ((np.arange(w)[None, :] % rib) == 0) * 18
        arr = np.stack([v, v, v, np.full_like(v, 255)], -1)
        im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'), (x0, y0))
    x0, y0, x1, y1 = box('leather')
    w, h = x1 - x0, y1 - y0
    n = np.random.randint(-8, 8, (h, w)).astype(float)
    n = np.array(Image.fromarray((n + 128).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), float) - 128
    v = 228 + n * 2
    yy, xx = np.mgrid[0:h, 0:w]
    quilt = ((yy % 22) < 2)
    v = np.where(quilt, v - 22, v)
    stitch = ((yy % 22) == 4) & ((xx % 5) < 2)
    v = np.where(stitch, v + 16, v)
    v = v - 10 * np.exp(-((xx - w / 2) / (w * 0.08)) ** 2)
    arr = np.stack([v, v, v, np.full_like(v, 255)], -1)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'), (x0, y0))
    # ---------------- infotainment screen: navigation + media
    r, o = region('screen')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(16, 26, 38, 255))
    mx = int(w * 0.68)
    d.rectangle([0, 0, mx, h], fill=(26, 40, 54, 255))
    random.seed(3)
    for k in range(14):
        x = random.randint(0, mx)
        d.line([x, 0, x + random.randint(-40, 40), h], fill=(44, 62, 80, 255), width=random.choice([3, 5, 8]))
        y = random.randint(0, h)
        d.line([0, y, mx, y + random.randint(-30, 30)], fill=(44, 62, 80, 255), width=random.choice([3, 5]))
    d.rectangle([30, 40, 90, 90], fill=(34, 64, 52, 255))
    d.line([(mx * 0.5, h), (mx * 0.5, h * 0.6), (mx * 0.7, h * 0.45), (mx * 0.72, h * 0.15)], fill=(60, 160, 255, 255), width=7)
    d.polygon([(mx * 0.5 - 12, h - 18), (mx * 0.5, h - 44), (mx * 0.5 + 12, h - 18)], fill=(255, 255, 255, 255))
    d.rounded_rectangle([10, 10, mx * 0.6, 50], 8, fill=(20, 110, 70, 255))
    f = font(22)
    d.text((22, 18), '↱ 350 m  Main St', fill=(255, 255, 255, 255), font=f)
    d.rectangle([mx, 0, w, h], fill=(14, 18, 26, 255))
    d.rounded_rectangle([mx + 12, 14, w - 12, 100], 10, fill=(70, 40, 110, 255))
    d.text((mx + 24, 112), 'Radio TC', fill=(230, 230, 240, 255), font=font(20))
    d.text((mx + 24, 138), '101.4 FM', fill=(150, 160, 180, 255), font=font(16, False))
    d.rectangle([mx + 24, 170, w - 24, 174], fill=(60, 70, 90, 255))
    d.rectangle([mx + 24, 170, mx + 90, 174], fill=(120, 200, 255, 255))
    for i, x in enumerate(range(mx + 30, w - 20, 44)):
        d.ellipse([x, 196, x + 26, 222], fill=(40, 50, 70, 255))
    d.rectangle([0, h - 34, w, h], fill=(10, 13, 18, 230))
    for i in range(6):
        x = 20 + i * (w - 40) / 6
        d.rounded_rectangle([x, h - 28, x + 34, h - 6], 5, fill=(50, 64, 86, 255))
    d.text((w - 90, 6), '12:45', fill=(230, 230, 240, 255), font=font(20))
    im.paste(r, o)
    # ---------------- glass reflection streaks (used with planar UVs)
    x0, y0, x1, y1 = box('glass')
    w, h = x1 - x0, y1 - y0
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    t = (xx * 0.8 + yy) / (w + h)
    streak = np.exp(-((t - 0.35) * 14) ** 2) * 70 + np.exp(-((t - 0.52) * 30) ** 2) * 45 + np.exp(-((t - 0.8) * 10) ** 2) * 30
    sky = 210 - yy / h * 40
    v = np.clip(sky + streak, 0, 255)
    arr = np.stack([v * 0.92, v * 0.97, v, np.full_like(v, 255)], -1)
    im.paste(Image.fromarray(arr.astype(np.uint8), 'RGBA'), (x0, y0))
    # ---------------- matcap (studio environment) for additive clear-coat reflections
    x0, y0, x1, y1 = box('matcap')
    w, h = x1 - x0, y1 - y0
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    nx = (xx / w) * 2 - 1
    ny = 1 - (yy / h) * 2
    rr = np.sqrt(nx ** 2 + ny ** 2)
    sky = np.clip(ny, 0, 1) ** 1.5 * 70
    horizon = np.exp(-(ny * 6) ** 2) * 55
    key = np.exp(-(((nx + 0.35) ** 2 + (ny - 0.45) ** 2) * 28)) * 200
    rim = np.clip((rr - 0.82) * 5, 0, 1) * 60
    strip = np.exp(-((nx - 0.45) * 12) ** 2) * np.clip(ny + 0.2, 0, 1) * 90
    v = np.clip(sky + horizon + key + rim + strip, 0, 255)
    v = np.where(rr > 1, 0, v)
    arr = np.stack([v * 0.95, v * 0.98, v, np.full_like(v, 255)], -1)
    im.paste(Image.fromarray(arr.astype(np.uint8), 'RGBA'), (x0, y0))
    # ---------------- brake rotor (drilled + slotted)
    r, o = region('rotor')
    d = ImageDraw.Draw(r)
    w, h = r.size
    cx, cy = w / 2, h / 2
    d.ellipse([0, 0, w - 1, h - 1], fill=(120, 122, 126, 255))
    for k in range(60):
        a = 2 * math.pi * k / 60
        d.line([(cx + math.cos(a) * w * 0.3, cy + math.sin(a) * h * 0.3), (cx + math.cos(a) * w * 0.5, cy + math.sin(a) * h * 0.5)],
               fill=(105, 107, 110, 255))
    for k in range(18):
        a = 2 * math.pi * k / 18
        for rr_ in (0.36, 0.43):
            px, py = cx + math.cos(a + rr_) * w * rr_, cy + math.sin(a + rr_) * h * rr_
            d.ellipse([px - 3, py - 3, px + 3, py + 3], fill=(30, 30, 32, 255))
    d.ellipse([cx - w * 0.28, cy - h * 0.28, cx + w * 0.28, cy + h * 0.28], fill=(60, 62, 66, 255))
    im.paste(r, o)
    # ---------------- tyre sidewall lettering band
    r, o = region('sidewall')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(36, 37, 39, 255))
    f = font(int(h * 0.55))
    x = 10
    for t in ('TC SPORT', '225/45 R18', 'TC SPORT', '94W'):
        d.text((x, h * 0.18), t, fill=(66, 67, 70, 255), font=f)
        x += d.textlength(t, font=f) + 40
    im.paste(r, o)
    # ---------------- gear plate, buttons, speaker, knob, hvac
    r, o = region('gear_plate')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(20, 21, 24, 255))
    f = font(int(h * 0.42))
    for i, ch in enumerate('PRND'):
        d.text((w * 0.12 + i * w * 0.21, h * 0.25), ch, fill=(235, 235, 240, 255) if ch != 'D' else (120, 220, 255, 255), font=f)
    im.paste(r, o)
    r, o = region('buttons')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(18, 19, 22, 255))
    for i in range(4):
        for j in range(2):
            x, y = 6 + i * (w - 12) / 4, 6 + j * (h - 12) / 2
            d.rounded_rectangle([x, y, x + (w - 12) / 4 - 6, y + (h - 12) / 2 - 6], 4, fill=(42, 44, 50, 255))
            d.ellipse([x + 8, y + 8, x + 14, y + 14], fill=(255, 170, 60, 255) if (i + j) % 3 == 0 else (120, 200, 255, 255))
    im.paste(r, o)
    r, o = region('speaker')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.ellipse([0, 0, w - 1, h - 1], fill=(30, 31, 34, 255))
    for y in range(4, h - 4, 5):
        for x in range(4, w - 4, 5):
            if (x - w / 2) ** 2 + (y - h / 2) ** 2 < (w / 2 - 5) ** 2:
                d.point((x, y), fill=(10, 10, 11, 255))
    im.paste(r, o)
    r, o = region('knob')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.ellipse([0, 0, w - 1, h - 1], fill=(150, 154, 160, 255))
    for k in range(24):
        a = 2 * math.pi * k / 24
        d.line([(w / 2 + math.cos(a) * w * 0.38, h / 2 + math.sin(a) * h * 0.38), (w / 2 + math.cos(a) * w * 0.5, h / 2 + math.sin(a) * h * 0.5)],
               fill=(90, 92, 96, 255), width=2)
    d.ellipse([w * 0.25, h * 0.25, w * 0.75, h * 0.75], fill=(40, 42, 46, 255))
    im.paste(r, o)
    r, o = region('hvac')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(16, 17, 20, 255))
    for i, x in enumerate((w * 0.12, w * 0.88)):
        d.ellipse([x - h * 0.38, h * 0.12, x + h * 0.38, h * 0.88], fill=(140, 144, 150, 255))
        d.ellipse([x - h * 0.3, h * 0.2, x + h * 0.3, h * 0.8], fill=(32, 34, 38, 255))
        d.text((x - 16, h * 0.32), '21°' if i == 0 else '22°', fill=(255, 255, 255, 255), font=font(int(h * 0.26)))
    for i in range(5):
        x = w * 0.26 + i * w * 0.1
        d.rounded_rectangle([x, h * 0.25, x + w * 0.08, h * 0.75], 4, fill=(44, 46, 52, 255))
        d.rectangle([x + w * 0.02, h * 0.32, x + w * 0.06, h * 0.36], fill=(255, 160, 40, 255) if i in (1, 3) else (90, 100, 110, 255))
    im.paste(r, o)
    r, o = region('cluster_digital')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(6, 8, 12, 255))
    d.arc([w * 0.05, h * 0.1, w * 0.45, h * 0.9], 140, 400, fill=(60, 170, 255, 255), width=6)
    d.arc([w * 0.55, h * 0.1, w * 0.95, h * 0.9], 140, 400, fill=(255, 120, 60, 255), width=6)
    im.paste(r, o)
    # ---------------- frit (windshield ceramic dots)
    r, o = region('frit')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(10, 10, 12, 255))
    for y in range(0, h, 6):
        for x in range(0, w, 6):
            rad = 2.6 * (1 - y / h)
            if rad > 0.4:
                d.ellipse([x + 3 - rad, y + 3 - rad, x + 3 + rad, y + 3 + rad], fill=(10, 10, 12, 255))
    im.paste(r, o)
    # ---------------- aluminium / wood trims
    x0, y0, x1, y1 = box('alu')
    w, h = x1 - x0, y1 - y0
    n = np.random.randint(-12, 12, (1, w)).repeat(h, 0)
    v = 196 + n + np.linspace(-10, 10, h)[:, None]
    arr = np.stack([v, v, v + 4, np.full_like(v, 255)], -1)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'), (x0, y0))
    x0, y0, x1, y1 = box('wood')
    w, h = x1 - x0, y1 - y0
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    g = np.sin(xx * 0.05 + np.sin(yy * 0.15) * 2 + np.sin(xx * 0.013) * 4) * 0.5 + 0.5
    arr = np.stack([110 + g * 50, 62 + g * 30, 30 + g * 14, np.full_like(g, 255)], -1)
    im.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA'), (x0, y0))
    # ---------------- misc text panels
    for name, text, fg, bg in (('police', 'POLICE', (20, 40, 120, 255), (245, 245, 245, 255)),
                               ('ambulance', 'AMBULANCE', (200, 20, 25, 255), (245, 245, 245, 255)),
                               ('bus_dest', '24  CITY CENTRE', (255, 170, 20, 255), (15, 12, 8, 255)),
                               ('courier', 'EXPRESS DELIVERY', (250, 250, 250, 255), (210, 60, 30, 255)),
                               ('freight', 'TC LOGISTICS', (250, 250, 250, 255), (25, 70, 140, 255)),
                               ('taxi', 'TAXI', (15, 15, 15, 255), (255, 205, 30, 255))):
        r, o = region(name)
        d = ImageDraw.Draw(r)
        d.rectangle([0, 0, r.size[0], r.size[1]], fill=bg)
        text_center(d, r.size[0], r.size[1], text, fg)
        im.paste(r, o)
    r, o = region('stripe_amb')
    d = ImageDraw.Draw(r)
    w, h = r.size
    sq = h // 2
    for i, x in enumerate(range(0, w, sq)):
        for j in range(2):
            d.rectangle([x, j * sq, x + sq - 1, (j + 1) * sq - 1], fill=(20, 160, 60, 255) if (i + j) % 2 == 0 else (250, 220, 20, 255))
    im.paste(r, o)
    r, o = region('checker')
    d = ImageDraw.Draw(r)
    w, h = r.size
    sq = h // 2
    for i, x in enumerate(range(0, w, sq)):
        for j in range(2):
            d.rectangle([x, j * sq, x + sq - 1, (j + 1) * sq - 1], fill=(15, 15, 15, 255) if (i + j) % 2 == 0 else (245, 245, 245, 255))
    im.paste(r, o)
    r, o = region('stripe_police')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rectangle([0, 0, w, h], fill=(20, 50, 160, 255))
    d.rectangle([0, h * 0.35, w, h * 0.65], fill=(250, 250, 250, 255))
    im.paste(r, o)
    # vent slats
    r, o = region('vent')
    d = ImageDraw.Draw(r)
    w, h = r.size
    d.rounded_rectangle([0, 0, w - 1, h - 1], 8, fill=(150, 154, 160, 255))
    d.rounded_rectangle([5, 5, w - 6, h - 6], 6, fill=(16, 17, 19, 255))
    for y in range(12, h - 8, 10):
        d.rectangle([8, y, w - 9, y + 4], fill=(58, 60, 64, 255))
    im.paste(r, o)
    # cargo corrugation
    x0, y0, x1, y1 = box('cargo')
    for x in range(x0, x1):
        v = 232 + int(16 * math.sin(x * 0.4))
        ImageDraw.Draw(im).line([x, y0, x, y1 - 1], fill=(v, v, v, 255))
    for name in ('rim_steel',):
        x0, y0, x1, y1 = box(name)
        ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=(175, 178, 184, 255))
    x0, y0, x1, y1 = box('tyre_side')
    ImageDraw.Draw(im).rectangle([x0, y0, x1, y1], fill=(36, 37, 39, 255))
    im.save(path)
    return im


if __name__ == '__main__':
    import sys
    make_atlas(sys.argv[1])
