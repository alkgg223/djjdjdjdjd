"""Procedural 64x64 player-format skins for pedestrians and workers (original designs)."""
import os, random, json
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'trafficcraft', 'textures', 'entity', 'ped')
os.makedirs(OUT, exist_ok=True)
random.seed(21)

SKIN = [(255, 220, 190), (240, 200, 165), (215, 170, 130), (190, 140, 100), (150, 100, 70), (110, 75, 50), (80, 55, 40)]
HAIR = [(30, 24, 20), (70, 45, 25), (120, 80, 40), (200, 160, 90), (230, 210, 150), (150, 40, 25), (200, 200, 200), (240, 240, 240)]


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def noise_fill(d, box, col, amp=8):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = random.randint(-amp, amp)
            d.point((x, y), fill=tuple(max(0, min(255, col[i] + n)) for i in range(3)) + (255,))


# UV boxes (x, y, w, h, d) for 64x64 skins: head, body, arms, legs (base layer)
def box_regions(u, v, w, h, d):
    """Returns dict face -> (x0,y0,x1,y1) for a cuboid with the standard box-UV layout."""
    return {'top': (u + d, v, u + d + w, v + d), 'bottom': (u + d + w, v, u + d + 2 * w, v + d), 'right': (u, v + d, u + d, v + d + h),
            'front': (u + d, v + d, u + d + w, v + d + h), 'left': (u + d + w, v + d, u + 2 * d + w, v + d + h), 'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


PARTS = {'head': (0, 0, 8, 8, 8), 'body': (16, 16, 8, 12, 4), 'rarm': (40, 16, 4, 12, 4), 'larm': (32, 48, 4, 12, 4), 'rleg': (0, 16, 4, 12, 4),
         'lleg': (16, 48, 4, 12, 4), 'hat': (32, 0, 8, 8, 8), 'jacket': (16, 32, 8, 12, 4)}


def fill_part(d, part, col, amp=6, faces=None):
    for f, b in box_regions(*PARTS[part]).items():
        if faces and f not in faces:
            continue
        noise_fill(d, b, col, amp)


def make(name, skin, hair, top, bottom, shoes, style='casual', hairstyle='short', extra=None):
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    fill_part(d, 'head', skin, 4)
    # face
    fx, fy = 8, 8
    eye = (40, 60, 90) if random.random() < 0.4 else (60, 40, 25)
    d.rectangle([fx + 1, fy + 3, fx + 2, fy + 4], fill=(250, 250, 250, 255))
    d.rectangle([fx + 5, fy + 3, fx + 6, fy + 4], fill=(250, 250, 250, 255))
    d.point((fx + 2, fy + 4), fill=eye + (255,))
    d.point((fx + 5, fy + 4), fill=eye + (255,))
    d.line([fx + 3, fy + 6, fx + 4, fy + 6], fill=shade(skin, 0.7))
    d.line([fx + 1, fy + 2, fx + 2, fy + 2], fill=shade(hair, 0.9))
    d.line([fx + 5, fy + 2, fx + 6, fy + 2], fill=shade(hair, 0.9))
    # hair on top / back / sides
    hb = box_regions(*PARTS['head'])
    noise_fill(d, hb['top'], hair, 10)
    x0, y0, x1, y1 = hb['back']
    noise_fill(d, (x0, y0, x1, y0 + (6 if hairstyle in ('long', 'bun') else 3)), hair, 10)
    for side in ('left', 'right'):
        x0, y0, x1, y1 = hb[side]
        noise_fill(d, (x0, y0, x1, y0 + (6 if hairstyle == 'long' else 2)), hair, 10)
    x0, y0, x1, y1 = hb['front']
    if hairstyle != 'bald':
        noise_fill(d, (x0, y0, x1, y0 + 1), hair, 10)
    if hairstyle == 'long':
        noise_fill(d, (x0, y0 + 1, x0 + 1, y0 + 6), hair, 8)
        noise_fill(d, (x1 - 1, y0 + 1, x1, y0 + 6), hair, 8)
    # torso + arms
    fill_part(d, 'body', top)
    sleeve_long = style in ('business', 'officer', 'worker', 'winter', 'cashier', 'attendant')
    for arm in ('rarm', 'larm'):
        for f, b in box_regions(*PARTS[arm]).items():
            x0, y0, x1, y1 = b
            if f in ('top',):
                noise_fill(d, b, top, 5)
                continue
            if f == 'bottom':
                noise_fill(d, b, skin, 3)
                continue
            cut = y1 if sleeve_long else y0 + 4
            noise_fill(d, (x0, y0, x1, min(y1, cut)), top, 5)
            if cut < y1:
                noise_fill(d, (x0, cut, x1, y1), skin, 3)
            if sleeve_long:
                noise_fill(d, (x0, y1 - 1, x1, y1), skin, 3)
    # legs
    for leg in ('rleg', 'lleg'):
        for f, b in box_regions(*PARTS[leg]).items():
            x0, y0, x1, y1 = b
            if f == 'bottom':
                noise_fill(d, b, shoes, 4)
                continue
            if f == 'top':
                noise_fill(d, b, bottom, 4)
                continue
            if style == 'dress':
                noise_fill(d, (x0, y0, x1, y0 + 5), bottom, 4)
                noise_fill(d, (x0, y0 + 5, x1, y1 - 2), skin, 3)
            else:
                noise_fill(d, (x0, y0, x1, y1 - 2), bottom, 5)
            noise_fill(d, (x0, y1 - 2, x1, y1), shoes, 4)
    bb = box_regions(*PARTS['body'])
    if style == 'business':
        x0, y0, x1, y1 = bb['front']
        d.polygon([(x0 + 3, y0), (x0 + 5, y0), (x0 + 4, y0 + 5)], fill=(240, 240, 240, 255))
        d.line([x0 + 4, y0 + 1, x0 + 4, y0 + 5], fill=(150, 30, 40, 255))
    if style == 'worker':
        for f in ('front', 'back'):
            x0, y0, x1, y1 = bb[f]
            d.rectangle([x0, y0 + 5, x1 - 1, y0 + 6], fill=(220, 220, 220, 255))
            d.rectangle([x0, y0 + 9, x1 - 1, y0 + 10], fill=(220, 220, 220, 255))
    if style == 'officer':
        x0, y0, x1, y1 = bb['front']
        d.rectangle([x0, y0 + 7, x1 - 1, y0 + 8], fill=(30, 30, 30, 255))
        d.point((x0 + 2, y0 + 2), fill=(240, 200, 40, 255))
        d.rectangle([x0 + 5, y0 + 2, x0 + 6, y0 + 3], fill=(240, 240, 240, 255))
        # cap on the hat layer
        hb2 = box_regions(*PARTS['hat'])
        noise_fill(d, hb2['top'], (25, 40, 80), 4)
        for f in ('front', 'back', 'left', 'right'):
            x0, y0, x1, y1 = hb2[f]
            noise_fill(d, (x0, y0, x1, y0 + 2), (25, 40, 80), 4)
        x0, y0, x1, y1 = hb2['front']
        d.point((x0 + 3, y0 + 1), fill=(240, 200, 40, 255))
        d.point((x0 + 4, y0 + 1), fill=(240, 200, 40, 255))
    if style in ('cashier', 'attendant'):
        for f in ('front',):
            x0, y0, x1, y1 = bb[f]
            col = (200, 30, 30) if style == 'attendant' else (40, 110, 60)
            d.rectangle([x0 + 1, y0 + 3, x1 - 2, y1 - 1], fill=col + (255,))
            d.rectangle([x0 + 2, y0 + 4, x0 + 4, y0 + 5], fill=(250, 250, 250, 255))
    if style == 'child':
        x0, y0, x1, y1 = bb['front']
        d.rectangle([x0 + 2, y0 + 3, x0 + 5, y0 + 6], fill=(250, 220, 60, 255))
    if style == 'winter':
        for f in ('front', 'left', 'right', 'back'):
            x0, y0, x1, y1 = bb[f]
            d.rectangle([x0, y0, x1 - 1, y0 + 1], fill=shade(top, 1.25))
    if extra == 'glasses':
        d.line([fx + 1, fy + 3, fx + 6, fy + 3], fill=(20, 20, 20, 255))
    if extra == 'beard':
        noise_fill(d, (fx + 1, fy + 5, fx + 7, fy + 8), hair, 8)
        d.line([fx + 3, fy + 6, fx + 4, fy + 6], fill=shade(skin, 0.6))
    im.save(os.path.join(OUT, name + '.png'))


PEDS = [
    ('adult_1', 1, 1, (60, 90, 150), (40, 40, 60), (30, 30, 30), 'casual', 'short', None, 'adult'),
    ('adult_2', 3, 0, (200, 60, 70), (60, 60, 90), (240, 240, 240), 'casual', 'long', None, 'adult'),
    ('adult_3', 5, 0, (240, 240, 235), (50, 70, 110), (60, 40, 30), 'casual', 'short', 'beard', 'adult'),
    ('adult_4', 0, 3, (90, 160, 90), (200, 190, 160), (120, 80, 40), 'dress', 'long', None, 'adult'),
    ('adult_5', 2, 5, (40, 40, 45), (40, 40, 45), (20, 20, 20), 'winter', 'bun', 'glasses', 'adult'),
    ('adult_6', 4, 0, (230, 170, 50), (70, 90, 140), (250, 250, 250), 'casual', 'short', None, 'adult'),
    ('adult_7', 6, 0, (120, 60, 140), (30, 30, 35), (200, 200, 200), 'dress', 'long', None, 'adult'),
    ('business_1', 1, 0, (45, 50, 60), (45, 50, 60), (20, 20, 20), 'business', 'short', None, 'business'),
    ('business_2', 4, 1, (70, 70, 80), (70, 70, 80), (30, 20, 15), 'business', 'bun', 'glasses', 'business'),
    ('elderly_1', 1, 6, (150, 120, 90), (90, 80, 70), (60, 40, 30), 'winter', 'short', 'glasses', 'elderly'),
    ('elderly_2', 2, 7, (160, 90, 120), (120, 110, 130), (80, 60, 50), 'dress', 'bun', None, 'elderly'),
    ('child_1', 0, 3, (60, 140, 220), (50, 50, 80), (220, 60, 60), 'child', 'short', None, 'child'),
    ('child_2', 3, 2, (240, 120, 170), (230, 230, 230), (240, 240, 240), 'child', 'long', None, 'child'),
    ('worker_1', 3, 1, (250, 140, 20), (40, 60, 90), (90, 60, 30), 'worker', 'short', None, 'worker'),
    ('worker_2', 5, 0, (220, 230, 40), (50, 50, 55), (40, 30, 20), 'worker', 'short', 'beard', 'worker'),
    ('officer', 2, 0, (40, 70, 120), (30, 40, 70), (15, 15, 15), 'officer', 'short', None, 'officer'),
    ('cashier', 1, 2, (240, 240, 235), (40, 40, 50), (30, 30, 30), 'cashier', 'long', None, 'cashier'),
    ('attendant', 4, 1, (60, 60, 70), (60, 60, 70), (25, 25, 25), 'attendant', 'short', None, 'attendant'),
]

if __name__ == '__main__':
    index = []
    for (n, sk, hr, top, bot, sh, style, hs, ex, kind) in PEDS:
        make(n, SKIN[sk], HAIR[hr], top, bot, sh, style, hs, ex)
        index.append({'skin': n, 'kind': kind, 'slim': hs in ('long', 'bun')})
    json.dump(index, open(os.path.join(OUT, 'index.json'), 'w'), indent=1)
    print(len(index), 'skins')
