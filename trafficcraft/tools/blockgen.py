"""Generates block textures + JSON block/item models + blockstates for TrafficCraft infrastructure blocks.
Writes into src/main/resources/assets/trafficcraft. Each block definition also lists its Java 'kind' for the registry
(printed to tools/blocks.json, read at build time by the Java code generator)."""
import json, math, os, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'trafficcraft')
TEX = os.path.join(ROOT, 'textures', 'block')
MOD = os.path.join(ROOT, 'models', 'block')
IMOD = os.path.join(ROOT, 'models', 'item')
BS = os.path.join(ROOT, 'blockstates')
for d in (TEX, MOD, IMOD, BS):
    os.makedirs(d, exist_ok=True)
random.seed(11)
S = 64  # texture resolution (64x64 for detail)


def font(sz, bold=True):
    try:
        return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if bold else ''), int(sz))
    except Exception:
        return ImageFont.load_default()


def save(img, name):
    img.save(os.path.join(TEX, name + '.png'))


def noise_tex(base, amp, name, size=S, speck=0.0, speck_col=None, blur=0):
    im = Image.new('RGB', (size, size))
    px = im.load()
    for y in range(size):
        for x in range(size):
            n = random.randint(-amp, amp)
            c = tuple(max(0, min(255, base[i] + n)) for i in range(3))
            if speck and random.random() < speck:
                c = speck_col or tuple(min(255, v + 40) for v in c)
            px[x, y] = c
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur))
    save(im, name)
    return im


# ------------------------------------------------------------------------------------------------ surfaces
def surfaces():
    noise_tex((52, 53, 56), 9, 'asphalt', speck=0.05, speck_col=(86, 86, 90))
    noise_tex((44, 45, 48), 7, 'asphalt_dark', speck=0.03, speck_col=(70, 70, 74))
    # sidewalk paving tiles
    im = noise_tex((168, 166, 160), 7, 'sidewalk')
    d = ImageDraw.Draw(im)
    for k in range(0, S, 16):
        d.line([0, k, S, k], fill=(132, 130, 126), width=1)
        d.line([k, 0, k, S], fill=(132, 130, 126), width=1)
    save(im, 'sidewalk')
    im = noise_tex((186, 184, 178), 6, 'curb_top')
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S, 8], fill=(214, 212, 206))
    save(im, 'curb_top')
    im = noise_tex((170, 168, 162), 6, 'curb_side')
    d = ImageDraw.Draw(im)
    for x in range(0, S, 32):
        d.line([x, 0, x, S], fill=(130, 128, 124))
    save(im, 'curb_side')
    noise_tex((160, 160, 156), 8, 'concrete', speck=0.03)
    noise_tex((120, 122, 124), 6, 'metal_grey')
    noise_tex((60, 62, 66), 5, 'metal_dark')
    noise_tex((196, 200, 204), 5, 'galvanized')
    # guardrail W-profile
    im = Image.new('RGB', (S, S), (190, 194, 198))
    d = ImageDraw.Draw(im)
    for y, c in ((8, (220, 224, 228)), (20, (150, 154, 158)), (32, (222, 226, 230)), (44, (148, 152, 156)), (56, (214, 218, 222))):
        d.rectangle([0, y - 6, S, y + 5], fill=c)
    for x in range(4, S, 32):
        d.ellipse([x - 2, 30, x + 2, 34], fill=(90, 92, 96))
    save(im, 'guardrail')
    # drain grate
    im = Image.new('RGB', (S, S), (40, 40, 42))
    d = ImageDraw.Draw(im)
    d.rectangle([2, 2, S - 3, S - 3], outline=(80, 80, 84), width=3)
    for x in range(8, S - 6, 6):
        d.rectangle([x, 8, x + 2, S - 9], fill=(10, 10, 10))
    save(im, 'drain')
    # wood / bench
    im = Image.new('RGB', (S, S))
    px = im.load()
    for y in range(S):
        for x in range(S):
            g = math.sin(x * 0.25 + math.sin(y * 0.05) * 3) * 0.5 + 0.5
            px[x, y] = (int(120 + g * 40), int(78 + g * 24), int(40 + g * 14))
    save(im, 'wood')
    # stripes red/white and yellow/black
    for name, a, b in (('stripes_red', (220, 30, 30), (245, 245, 245)), ('stripes_yellow', (240, 190, 20), (25, 25, 25))):
        im = Image.new('RGB', (S, S), b)
        d = ImageDraw.Draw(im)
        for k in range(-S, S * 2, 24):
            d.polygon([(k, 0), (k + 12, 0), (k + 12 - S, S), (k - S, S)], fill=a)
        save(im, name)
    im = Image.new('RGB', (S, S), (240, 110, 20))
    d = ImageDraw.Draw(im)
    for y in (18, 40):
        d.rectangle([0, y, S, y + 8], fill=(240, 240, 240))
    save(im, 'cone')
    noise_tex((240, 110, 20), 6, 'orange')
    noise_tex((40, 120, 60), 12, 'planter_leaves', speck=0.2, speck_col=(70, 150, 70))
    noise_tex((96, 64, 40), 8, 'soil')
    noise_tex((230, 230, 225), 4, 'white_paint')
    noise_tex((30, 30, 32), 4, 'black_paint')
    noise_tex((24, 80, 160), 6, 'blue_paint')
    noise_tex((200, 30, 30), 5, 'red_paint')
    noise_tex((40, 150, 60), 5, 'green_paint')
    noise_tex((235, 185, 20), 5, 'yellow_paint')
    # glass
    im = Image.new('RGBA', (S, S), (180, 210, 230, 90))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=(70, 80, 90, 255), width=2)
    d.line([8, S - 8, S - 8, 8], fill=(255, 255, 255, 120), width=3)
    save(im, 'glass_panel')
    # lamp lens (lit / unlit)
    for name, col in (('lamp_on', (255, 244, 210)), ('lamp_off', (150, 150, 140))):
        im = Image.new('RGB', (S, S), col)
        d = ImageDraw.Draw(im)
        for r in range(28, 4, -6):
            c = tuple(min(255, int(v * (1.05 - r / 120))) for v in col)
            d.ellipse([32 - r, 32 - r, 32 + r, 32 + r], fill=c)
        save(im, name)
    # camera, sos box, parking meter, payment terminal, fuel pump faces
    im = Image.new('RGB', (S, S), (230, 230, 226))
    d = ImageDraw.Draw(im)
    d.ellipse([18, 18, 46, 46], fill=(20, 20, 24))
    d.ellipse([26, 26, 38, 38], fill=(60, 90, 140))
    d.ellipse([29, 29, 33, 33], fill=(200, 220, 255))
    save(im, 'camera_lens')
    im = Image.new('RGB', (S, S), (240, 120, 20))
    d = ImageDraw.Draw(im)
    d.rectangle([10, 8, S - 11, 26], fill=(250, 250, 250))
    d.text((14, 9), 'SOS', fill=(200, 20, 20), font=font(15))
    d.rectangle([20, 34, 44, 54], fill=(40, 40, 44))
    d.ellipse([28, 38, 36, 46], fill=(200, 30, 30))
    save(im, 'sos_box')
    im = Image.new('RGB', (S, S), (60, 70, 80))
    d = ImageDraw.Draw(im)
    d.rectangle([12, 8, 52, 26], fill=(20, 40, 30))
    d.text((15, 11), '0:45', fill=(90, 240, 120), font=font(13))
    d.rectangle([24, 34, 40, 38], fill=(10, 10, 10))
    d.rectangle([14, 44, 50, 56], fill=(200, 200, 200))
    save(im, 'parking_meter')
    im = Image.new('RGB', (S, S), (40, 44, 52))
    d = ImageDraw.Draw(im)
    d.rectangle([8, 6, 56, 30], fill=(18, 40, 70))
    d.text((11, 10), 'PAY', fill=(240, 240, 255), font=font(14))
    for i in range(3):
        for j in range(3):
            d.rectangle([14 + i * 13, 36 + j * 8, 24 + i * 13, 42 + j * 8], fill=(190, 194, 200))
    save(im, 'pay_terminal')
    im = Image.new('RGB', (S, S), (235, 235, 232))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S, 10], fill=(200, 30, 30))
    d.rectangle([8, 16, 56, 34], fill=(10, 12, 14))
    d.text((11, 18), '1.79', fill=(255, 80, 60), font=font(14))
    d.rectangle([8, 40, 56, 52], fill=(10, 12, 14))
    d.text((11, 40), '24.0L', fill=(120, 240, 120), font=font(11))
    save(im, 'fuel_pump_face')
    im = Image.new('RGB', (S, S), (200, 30, 30))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 40, S, S], fill=(235, 235, 232))
    d.text((6, 6), 'FUEL', fill=(255, 255, 255), font=font(18))
    save(im, 'fuel_pump_side')
    im = Image.new('RGB', (S, S), (225, 225, 222))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S, 14], fill=(30, 90, 170))
    d.text((4, 0), 'MARKET', fill=(255, 255, 255), font=font(11))
    save(im, 'shop_sign')
    noise_tex((200, 200, 196), 4, 'counter_top')
    # border booth stripe / window
    im = Image.new('RGB', (S, S), (238, 238, 234))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 46, S, 56], fill=(20, 60, 140))
    save(im, 'booth_wall')
    im = Image.new('RGB', (S, S), (25, 25, 28))
    d = ImageDraw.Draw(im)
    d.rectangle([4, 4, S - 5, S - 5], fill=(60, 64, 70))
    for k in range(6, S, 10):
        d.line([4, k, S - 5, k], fill=(40, 44, 50))
    save(im, 'shelf')
    noise_tex((210, 210, 214), 3, 'ceiling_panel')
    # bus stop sign / shelter panel
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([2, 2, S - 3, S - 3], fill=(250, 250, 250, 255), outline=(30, 120, 60, 255), width=5)
    d.text((14, 10), 'BUS', fill=(30, 120, 60, 255), font=font(16))
    d.text((12, 32), 'STOP', fill=(30, 30, 30, 255), font=font(12))
    save(im, 'sign_bus_stop')
    im = Image.new('RGB', (S, S), (30, 60, 110))
    d = ImageDraw.Draw(im)
    d.rectangle([4, 4, S - 5, S - 5], fill=(240, 240, 236))
    d.text((8, 8), 'ROUTE', fill=(30, 60, 110), font=font(10))
    for i in range(4):
        d.rectangle([8, 24 + i * 9, 56, 28 + i * 9], fill=(150, 160, 180))
    save(im, 'bus_timetable')
    # tree trunk / leaves for roadside tree
    noise_tex((92, 70, 50), 10, 'tree_bark')
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = im.load()
    for y in range(S):
        for x in range(S):
            if random.random() < 0.82:
                g = random.randint(80, 150)
                px[x, y] = (int(g * 0.4), g, int(g * 0.35), 255)
    save(im, 'tree_leaves')


# ------------------------------------------------------------------------------------------------ traffic signs
def sign_round(name, ring, fill, text=None, tcol=(20, 20, 20), size=None, bar=False, symbol=None):
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([1, 1, S - 2, S - 2], fill=(240, 240, 240, 255))
    d.ellipse([3, 3, S - 4, S - 4], fill=ring + (255,))
    w = 9 if ring != fill else 0
    d.ellipse([3 + w, 3 + w, S - 4 - w, S - 4 - w], fill=fill + (255,))
    if bar:
        d.rectangle([12, 27, S - 13, 37], fill=(250, 250, 250, 255))
    if text:
        f = font(size or (26 if len(text) <= 2 else 20))
        tw = d.textlength(text, font=f)
        d.text(((S - tw) / 2, (S - (size or 26)) / 2 - 3), text, fill=tcol + (255,), font=f)
    if symbol:
        symbol(d)
    save(im, name)


def sign_tri(name, symbol=None, text=None):
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(32, 2), (62, 58), (2, 58)], fill=(240, 240, 240, 255))
    d.polygon([(32, 5), (59, 56), (5, 56)], fill=(210, 30, 30, 255))
    d.polygon([(32, 16), (50, 50), (14, 50)], fill=(250, 250, 250, 255))
    if symbol:
        symbol(d)
    if text:
        d.text((25, 28), text, fill=(20, 20, 20, 255), font=font(18))
    save(im, name)


def sign_rect(name, bg, draw, border=(250, 250, 250)):
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([1, 1, S - 2, S - 2], 6, fill=border + (255,))
    d.rounded_rectangle([4, 4, S - 5, S - 5], 5, fill=bg + (255,))
    draw(d)
    save(im, name)


def arrow(d, cx, cy, ang, col=(250, 250, 250, 255), L=20, w=6):
    a = math.radians(ang)
    dx, dy = math.cos(a), -math.sin(a)
    px, py = -dy, dx
    tip = (cx + dx * L, cy + dy * L)
    base = (cx - dx * L, cy - dy * L)
    head = (cx + dx * (L - 12), cy + dy * (L - 12))
    d.polygon([(base[0] + px * w / 2, base[1] + py * w / 2), (head[0] + px * w / 2, head[1] + py * w / 2), (head[0] + px * w * 1.6, head[1] + py * w * 1.6),
               tip, (head[0] - px * w * 1.6, head[1] - py * w * 1.6), (head[0] - px * w / 2, head[1] - py * w / 2), (base[0] - px * w / 2, base[1] - py * w / 2)],
              fill=col)


def signs():
    # stop (octagon)
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    oc = [(32 + 31 * math.cos(math.radians(22.5 + 45 * k)), 32 + 31 * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
    d.polygon(oc, fill=(250, 250, 250, 255))
    oc2 = [(32 + 28 * math.cos(math.radians(22.5 + 45 * k)), 32 + 28 * math.sin(math.radians(22.5 + 45 * k))) for k in range(8)]
    d.polygon(oc2, fill=(200, 20, 24, 255))
    f = font(16)
    tw = d.textlength('STOP', font=f)
    d.text(((S - tw) / 2, 22), 'STOP', fill=(255, 255, 255, 255), font=f)
    save(im, 'sign_stop')
    # give way (inverted triangle)
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(2, 4), (62, 4), (32, 60)], fill=(240, 240, 240, 255))
    d.polygon([(6, 7), (58, 7), (32, 56)], fill=(210, 30, 30, 255))
    d.polygon([(17, 14), (47, 14), (32, 42)], fill=(250, 250, 250, 255))
    save(im, 'sign_give_way')
    for v in (30, 40, 50, 60, 70, 80, 100, 120):
        sign_round(f'sign_speed_{v}', (210, 30, 30), (250, 250, 250), str(v), size=24 if v < 100 else 19)
    sign_round('sign_no_entry', (210, 30, 30), (210, 30, 30), bar=True)
    sign_round('sign_no_parking', (210, 30, 30), (30, 80, 170), symbol=lambda d: d.line([14, 14, 50, 50], fill=(210, 30, 30, 255), width=7))
    sign_round('sign_no_stopping', (210, 30, 30), (30, 80, 170),
               symbol=lambda d: (d.line([14, 14, 50, 50], fill=(210, 30, 30, 255), width=7), d.line([50, 14, 14, 50], fill=(210, 30, 30, 255), width=7)))
    sign_round('sign_roundabout', (30, 80, 170), (30, 80, 170), symbol=lambda d: [arrow(d, 32 + 13 * math.cos(math.radians(a)), 32 - 13 * math.sin(math.radians(a)),
                                                                                  a + 90 + 30, L=9, w=5) for a in (90, 210, 330)])
    sign_round('sign_turn_left', (30, 80, 170), (30, 80, 170), symbol=lambda d: arrow(d, 32, 32, 180))
    sign_round('sign_turn_right', (30, 80, 170), (30, 80, 170), symbol=lambda d: arrow(d, 32, 32, 0))
    sign_round('sign_ahead_only', (30, 80, 170), (30, 80, 170), symbol=lambda d: arrow(d, 32, 32, 90))
    sign_round('sign_keep_right', (30, 80, 170), (30, 80, 170), symbol=lambda d: arrow(d, 32, 32, -45))
    sign_tri('sign_pedestrians', symbol=lambda d: (d.ellipse([29, 22, 35, 28], fill=(20, 20, 20, 255)), d.line([32, 28, 32, 40], fill=(20, 20, 20, 255), width=3),
                                                     d.line([32, 40, 27, 48], fill=(20, 20, 20, 255), width=3), d.line([32, 40, 37, 48], fill=(20, 20, 20, 255), width=3)))
    sign_tri('sign_roadworks', symbol=lambda d: (d.ellipse([30, 22, 36, 28], fill=(20, 20, 20, 255)), d.line([32, 28, 28, 40], fill=(20, 20, 20, 255), width=3),
                                                   d.line([28, 40, 22, 48], fill=(20, 20, 20, 255), width=3), d.polygon([(34, 44), (44, 44), (40, 36)], fill=(20, 20, 20, 255))))
    sign_tri('sign_traffic_lights', symbol=lambda d: (d.rectangle([27, 20, 37, 50], fill=(20, 20, 20, 255)), d.ellipse([29, 22, 35, 28], fill=(220, 30, 30, 255)),
                                                        d.ellipse([29, 32, 35, 38], fill=(240, 190, 20, 255)), d.ellipse([29, 42, 35, 48], fill=(40, 170, 60, 255))))
    sign_tri('sign_danger', text='!')
    sign_tri('sign_children', symbol=lambda d: (d.ellipse([24, 26, 29, 31], fill=(20, 20, 20, 255)), d.ellipse([35, 22, 41, 28], fill=(20, 20, 20, 255)),
                                                  d.line([27, 31, 27, 46], fill=(20, 20, 20, 255), width=3), d.line([38, 28, 38, 46], fill=(20, 20, 20, 255), width=3)))
    sign_rect('sign_parking', (30, 80, 170), lambda d: d.text((19, 8), 'P', fill=(255, 255, 255, 255), font=font(44)))
    sign_rect('sign_disabled_parking', (30, 80, 170), lambda d: (d.text((6, 4), 'P', fill=(255, 255, 255, 255), font=font(26)),
                                                                  d.ellipse([38, 18, 44, 24], fill=(255, 255, 255, 255)), d.arc([28, 30, 52, 54], 90, 360, fill=(255, 255, 255, 255), width=4)))
    sign_rect('sign_one_way', (30, 80, 170), lambda d: arrow(d, 32, 32, 0, L=22, w=10))
    sign_rect('sign_crossing', (30, 80, 170), lambda d: (d.polygon([(32, 8), (56, 52), (8, 52)], fill=(250, 250, 250, 255)),
                                                          d.ellipse([29, 22, 35, 28], fill=(20, 20, 20, 255)), d.line([32, 28, 32, 40], fill=(20, 20, 20, 255), width=3),
                                                          d.rectangle([12, 47, 52, 50], fill=(20, 20, 20, 255))))
    sign_rect('sign_highway', (30, 120, 60), lambda d: (d.text((8, 8), 'A1', fill=(255, 255, 255, 255), font=font(20)), arrow(d, 32, 44, 90, L=12, w=6)))
    sign_rect('sign_fuel', (30, 80, 170), lambda d: (d.rectangle([20, 14, 38, 50], fill=(250, 250, 250, 255)), d.rectangle([24, 18, 34, 28], fill=(30, 80, 170, 255)),
                                                      d.line([38, 22, 46, 30, 46, 46], fill=(250, 250, 250, 255), width=3)))
    sign_rect('sign_border', (30, 60, 140), lambda d: (d.text((8, 6), 'BORDER', fill=(255, 220, 40, 255), font=font(10)),
                                                        [d.ellipse([32 + 16 * math.cos(math.radians(a)) - 2, 38 + 16 * math.sin(math.radians(a)) - 2,
                                                                    32 + 16 * math.cos(math.radians(a)) + 2, 38 + 16 * math.sin(math.radians(a)) + 2], fill=(255, 220, 40, 255))
                                                         for a in range(0, 360, 30)]))
    sign_rect('sign_hospital', (30, 80, 170), lambda d: (d.rectangle([14, 14, 50, 50], fill=(250, 250, 250, 255)), d.text((22, 12), 'H', fill=(210, 30, 30, 255), font=font(34))))
    # wide direction / name sign backgrounds (text is rendered in game)
    for name, bg in (('direction_green', (30, 120, 60)), ('direction_blue', (30, 80, 170)), ('road_name', (30, 60, 120))):
        im = Image.new('RGB', (S, S // 2), bg)
        d = ImageDraw.Draw(im)
        d.rectangle([1, 1, S - 2, S // 2 - 2], outline=(250, 250, 250), width=2)
        im = im.resize((S, S))
        save(im, name)
    # sign back
    noise_tex((150, 152, 156), 5, 'sign_back')


# ------------------------------------------------------------------------------------------------ models
def el(frm, to, faces, rot=None, shade=True):
    e = {'from': frm, 'to': to, 'faces': faces}
    if rot:
        e['rotation'] = rot
    if not shade:
        e['shade'] = False
    return e


def allf(tex, uv=None, cull=None):
    f = {}
    for k in ('north', 'south', 'east', 'west', 'up', 'down'):
        f[k] = {'texture': tex}
        if uv:
            f[k]['uv'] = uv
    return f


def model(name, elements, textures, parent='block/block', ao=True, particle=None, display=None):
    m = {'parent': parent, 'textures': dict(textures), 'elements': elements, 'render_type': 'minecraft:translucent' if name in ('bus_shelter',) else 'minecraft:cutout'}
    m['textures']['particle'] = particle or list(textures.values())[0]
    if not ao:
        m['ambientocclusion'] = False
    if display:
        m['display'] = display
    json.dump(m, open(os.path.join(MOD, name + '.json'), 'w'))


def simple_cube(name, tex):
    json.dump({'parent': 'minecraft:block/cube_all', 'textures': {'all': f'trafficcraft:block/{tex}'}}, open(os.path.join(MOD, name + '.json'), 'w'))


def item_from_block(name, model_name=None):
    json.dump({'parent': f'trafficcraft:block/{model_name or name}'}, open(os.path.join(IMOD, name + '.json'), 'w'))


def item_flat(name, tex):
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'trafficcraft:block/{tex}'}}, open(os.path.join(IMOD, name + '.json'), 'w'))


def bs_single(name, model_name=None):
    json.dump({'variants': {'': {'model': f'trafficcraft:block/{model_name or name}'}}}, open(os.path.join(BS, name + '.json'), 'w'))


def bs_facing(name, model_name=None, extra=None):
    v = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        key = f'facing={f}'
        if extra:
            for ev, mn in extra.items():
                v[key + ',' + ev] = {'model': f'trafficcraft:block/{mn}', 'y': y}
        else:
            v[key] = {'model': f'trafficcraft:block/{model_name or name}', 'y': y}
    json.dump({'variants': v}, open(os.path.join(BS, name + '.json'), 'w'))


def bs_slab(name, bottom, top, full):
    json.dump({'variants': {'type=bottom': {'model': f'trafficcraft:block/{bottom}'}, 'type=top': {'model': f'trafficcraft:block/{top}'},
                            'type=double': {'model': f'trafficcraft:block/{full}'}}}, open(os.path.join(BS, name + '.json'), 'w'))


BLOCKS = []  # (id, kind, props)


def reg(bid, kind, **props):
    BLOCKS.append(dict(id=bid, kind=kind, **props))


def T(n):
    return f'trafficcraft:block/{n}'


def build_models():
    # ---------------- road surfaces
    simple_cube('asphalt', 'asphalt')
    bs_single('asphalt')
    item_from_block('asphalt')
    reg('asphalt', 'cube', material='stone', sound='stone', hardness=1.5)
    simple_cube('asphalt_dark', 'asphalt_dark')
    bs_single('asphalt_dark')
    item_from_block('asphalt_dark')
    reg('asphalt_dark', 'cube', material='stone', sound='stone', hardness=1.5)
    for nm, tex in (('asphalt_slab', 'asphalt'), ('sidewalk_slab', 'sidewalk')):
        json.dump({'parent': 'minecraft:block/slab', 'textures': {'bottom': T(tex), 'top': T(tex), 'side': T(tex)}}, open(os.path.join(MOD, nm + '.json'), 'w'))
        json.dump({'parent': 'minecraft:block/slab_top', 'textures': {'bottom': T(tex), 'top': T(tex), 'side': T(tex)}}, open(os.path.join(MOD, nm + '_top.json'), 'w'))
        simple_cube(nm + '_full', tex)
        bs_slab(nm, nm, nm + '_top', nm + '_full')
        item_from_block(nm)
        reg(nm, 'slab', material='stone', sound='stone', hardness=1.5)
    simple_cube('sidewalk', 'sidewalk')
    bs_single('sidewalk')
    item_from_block('sidewalk')
    reg('sidewalk', 'cube', material='stone', sound='stone', hardness=1.5)
    simple_cube('concrete_block', 'concrete')
    bs_single('concrete_block')
    item_from_block('concrete_block')
    reg('concrete_block', 'cube', material='stone', sound='stone', hardness=2)
    # curb: half slab with raised edge, facing = road side
    model('curb', [el([0, 0, 0], [16, 8, 16], {'up': {'texture': '#top'}, 'down': {'texture': '#side'}, 'north': {'texture': '#side'}, 'south': {'texture': '#side'},
                                                'east': {'texture': '#side'}, 'west': {'texture': '#side'}}),
                   el([0, 8, 0], [16, 10, 3], {'up': {'texture': '#top'}, 'north': {'texture': '#side'}, 'south': {'texture': '#side'}, 'east': {'texture': '#side'},
                                               'west': {'texture': '#side'}})],
          {'top': T('curb_top'), 'side': T('curb_side')})
    bs_facing('curb')
    item_from_block('curb')
    reg('curb', 'facing_shape', shape=[[0, 0, 0, 16, 8, 16]], material='stone', sound='stone', hardness=1.5)
    model('drain_cover', [el([1, 0, 1], [15, 0.5, 15], allf('#t'))], {'t': T('drain')})
    bs_single('drain_cover')
    item_from_block('drain_cover')
    reg('drain_cover', 'shape', shape=[[1, 0, 1, 15, 0.5, 15]], material='metal', sound='metal', hardness=1, nocollide=True)

    # ---------------- traffic signs on a pole (sign face toward -Z = north when facing=north)
    sign_ids = ['stop', 'give_way'] + [f'speed_{v}' for v in (30, 40, 50, 60, 70, 80, 100, 120)] + \
               ['no_entry', 'no_parking', 'no_stopping', 'roundabout', 'turn_left', 'turn_right', 'ahead_only', 'keep_right', 'pedestrians', 'roadworks',
                'traffic_lights', 'danger', 'children', 'parking', 'disabled_parking', 'one_way', 'crossing', 'highway', 'fuel', 'border', 'hospital', 'bus_stop']
    for sid in sign_ids:
        nm = 'sign_' + sid
        elems = [el([7.25, 0, 7.25], [8.75, 26, 8.75], allf('#pole')),
                 el([0, 16, 6.6], [16, 32, 7.2], {'north': {'texture': '#face'}, 'south': {'texture': '#back'}, 'east': {'texture': '#back'}, 'west': {'texture': '#back'},
                                                  'up': {'texture': '#back'}, 'down': {'texture': '#back'}})]
        model(nm, elems, {'face': T(nm), 'back': T('sign_back'), 'pole': T('galvanized')}, ao=False)
        bs_facing(nm)
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': T(nm)}}, open(os.path.join(IMOD, nm + '.json'), 'w'))
        reg(nm, 'sign', shape=[[7, 0, 7, 9, 16, 9]], material='metal', sound='metal', hardness=1, sign=sid)
    # direction & road name signs (text set by player, rendered by BER)
    for nm, tex in (('direction_sign', 'direction_green'), ('road_name_sign', 'road_name')):
        elems = [el([7.25, 0, 7.25], [8.75, 24, 8.75], allf('#pole')),
                 el([-8, 18, 6.6], [24, 30, 7.2], {'north': {'texture': '#face'}, 'south': {'texture': '#face'}, 'east': {'texture': '#back'}, 'west': {'texture': '#back'},
                                                   'up': {'texture': '#back'}, 'down': {'texture': '#back'}})]
        model(nm, elems, {'face': T(tex), 'back': T('sign_back'), 'pole': T('galvanized')}, ao=False)
        bs_facing(nm)
        item_from_block(nm)
        reg(nm, 'text_sign', shape=[[7, 0, 7, 9, 16, 9]], material='metal', sound='metal', hardness=1)

    # ---------------- barriers
    jersey = [el([1, 0, 2], [15, 4, 14], allf('#c')), el([1, 4, 4], [15, 9, 12], allf('#c')), el([1, 9, 5.5], [15, 14, 10.5], allf('#c'))]
    model('concrete_barrier', jersey, {'c': T('concrete')})
    bs_facing('concrete_barrier')
    item_from_block('concrete_barrier')
    reg('concrete_barrier', 'facing_shape', shape=[[0, 0, 2, 16, 14, 14]], material='stone', sound='stone', hardness=2)
    model('concrete_divider', jersey + [el([1, 14, 6], [15, 16, 10], allf('#y'))], {'c': T('concrete'), 'y': T('stripes_yellow')})
    bs_facing('concrete_divider')
    item_from_block('concrete_divider')
    reg('concrete_divider', 'facing_shape', shape=[[0, 0, 2, 16, 16, 14]], material='stone', sound='stone', hardness=2)
    model('guardrail', [el([7, 0, 9], [9, 12, 11], allf('#p')), el([0, 7, 7], [16, 12, 8.5], {'north': {'texture': '#r'}, 'south': {'texture': '#r'},
                                                                                         'up': {'texture': '#p'}, 'down': {'texture': '#p'}})],
          {'r': T('guardrail'), 'p': T('galvanized')})
    bs_facing('guardrail')
    item_from_block('guardrail')
    reg('guardrail', 'facing_shape', shape=[[0, 0, 7, 16, 18, 11]], material='metal', sound='metal', hardness=2)
    model('road_barrier', [el([1, 0, 7], [3, 14, 9], allf('#p')), el([13, 0, 7], [15, 14, 9], allf('#p')),
                           el([0, 9, 7.5], [16, 13, 8.5], {'north': {'texture': '#s'}, 'south': {'texture': '#s'}, 'up': {'texture': '#p'}, 'down': {'texture': '#p'}}),
                           el([0, 3, 7.5], [16, 6, 8.5], {'north': {'texture': '#s'}, 'south': {'texture': '#s'}, 'up': {'texture': '#p'}, 'down': {'texture': '#p'}}),
                           el([1, 14, 7], [4, 17, 9], allf('#l'), shade=False)],
          {'s': T('stripes_red'), 'p': T('white_paint'), 'l': T('orange')})
    bs_facing('road_barrier')
    item_from_block('road_barrier')
    reg('road_barrier', 'facing_shape', shape=[[0, 0, 7, 16, 18, 9]], material='metal', sound='metal', hardness=1, light=6)
    cone = [el([3, 0, 3], [13, 1, 13], allf('#b')), el([5, 1, 5], [11, 5, 11], allf('#c')), el([6, 5, 6], [10, 10, 10], allf('#c')),
            el([7, 10, 7], [9, 14, 9], allf('#c'))]
    model('road_cone', cone, {'c': T('cone'), 'b': T('black_paint')})
    bs_single('road_cone')
    item_from_block('road_cone')
    reg('road_cone', 'shape', shape=[[3, 0, 3, 13, 14, 13]], material='wool', sound='wool', hardness=0.3)
    model('construction_sign', [el([2, 0, 6], [3, 16, 7], allf('#p')), el([13, 0, 6], [14, 16, 7], allf('#p')),
                                el([1, 6, 7], [15, 18, 7.6], {'north': {'texture': '#f'}, 'south': {'texture': '#bk'}, 'east': {'texture': '#bk'}, 'west': {'texture': '#bk'},
                                                              'up': {'texture': '#bk'}, 'down': {'texture': '#bk'}})],
          {'f': T('sign_roadworks'), 'bk': T('sign_back'), 'p': T('galvanized')})
    bs_facing('construction_sign')
    item_from_block('construction_sign')
    reg('construction_sign', 'facing_shape', shape=[[1, 0, 6, 15, 18, 8]], material='metal', sound='metal', hardness=1)
    model('roadworks_light', [el([4, 0, 4], [12, 2, 12], allf('#d')), el([7, 2, 7], [9, 10, 9], allf('#d')), el([5, 10, 6], [11, 16, 10], allf('#l'), shade=False)],
          {'d': T('metal_dark'), 'l': T('orange')})
    bs_single('roadworks_light')
    item_from_block('roadworks_light')
    reg('roadworks_light', 'shape', shape=[[4, 0, 4, 12, 16, 12]], material='metal', sound='metal', hardness=1, light=10)
    model('bollard', [el([6, 0, 6], [10, 14, 10], allf('#d')), el([5.5, 11, 5.5], [10.5, 12.5, 10.5], allf('#w'))], {'d': T('metal_dark'), 'w': T('white_paint')})
    bs_single('bollard')
    item_from_block('bollard')
    reg('bollard', 'shape', shape=[[6, 0, 6, 10, 16, 10]], material='metal', sound='metal', hardness=2)

    # ---------------- street furniture
    bench = [el([1, 0, 5], [3, 6, 12], allf('#m')), el([13, 0, 5], [15, 6, 12], allf('#m')), el([0, 6, 4], [16, 7, 12], allf('#w')),
             el([0, 8, 11.5], [16, 13, 12.5], allf('#w')), el([1, 6, 12], [3, 14, 13], allf('#m')), el([13, 6, 12], [15, 14, 13], allf('#m'))]
    model('bench', bench, {'w': T('wood'), 'm': T('metal_dark')})
    bs_facing('bench')
    item_from_block('bench')
    reg('bench', 'seat', shape=[[0, 0, 4, 16, 7, 13]], material='wood', sound='wood', hardness=1.5)
    model('trash_bin', [el([4, 0, 4], [12, 12, 12], allf('#g')), el([3.5, 12, 3.5], [12.5, 13, 12.5], allf('#d')), el([6, 13, 6], [10, 14, 10], allf('#d'))],
          {'g': T('green_paint'), 'd': T('metal_dark')})
    bs_single('trash_bin')
    item_from_block('trash_bin')
    reg('trash_bin', 'shape', shape=[[4, 0, 4, 12, 14, 12]], material='metal', sound='metal', hardness=1)
    model('parking_meter', [el([7, 0, 7], [9, 10, 9], allf('#d')), el([5, 10, 6], [11, 18, 10], {'north': {'texture': '#f'}, 'south': {'texture': '#d'}, 'east': {'texture': '#d'},
                                                                                            'west': {'texture': '#d'}, 'up': {'texture': '#d'}, 'down': {'texture': '#d'}})],
          {'d': T('metal_grey'), 'f': T('parking_meter')})
    bs_facing('parking_meter')
    item_from_block('parking_meter')
    reg('parking_meter', 'facing_shape', shape=[[5, 0, 6, 11, 18, 10]], material='metal', sound='metal', hardness=1.5)
    model('emergency_call_box', [el([7, 0, 7], [9, 8, 9], allf('#d')), el([4, 8, 5], [12, 20, 10], {'north': {'texture': '#f'}, 'south': {'texture': '#o'}, 'east': {'texture': '#o'},
                                                                                                'west': {'texture': '#o'}, 'up': {'texture': '#o'}, 'down': {'texture': '#o'}})],
          {'d': T('metal_grey'), 'f': T('sos_box'), 'o': T('orange')})
    bs_facing('emergency_call_box')
    item_from_block('emergency_call_box')
    reg('emergency_call_box', 'facing_shape', shape=[[4, 0, 5, 12, 20, 10]], material='metal', sound='metal', hardness=1.5)
    model('bus_stop_sign', [el([7.25, 0, 7.25], [8.75, 28, 8.75], allf('#p')),
                            el([2, 22, 7.3], [14, 32, 8.7], {'north': {'texture': '#f'}, 'south': {'texture': '#f'}, 'east': {'texture': '#p'}, 'west': {'texture': '#p'},
                                                             'up': {'texture': '#p'}, 'down': {'texture': '#p'}}),
                            el([5, 10, 8.75], [11, 20, 9.2], {'south': {'texture': '#t'}, 'north': {'texture': '#p'}})],
          {'p': T('galvanized'), 'f': T('sign_bus_stop'), 't': T('bus_timetable')}, ao=False)
    bs_facing('bus_stop_sign')
    item_from_block('bus_stop_sign')
    reg('bus_stop_sign', 'facing_shape', shape=[[7, 0, 7, 9, 16, 9]], material='metal', sound='metal', hardness=1)
    # bus shelter: 2 blocks wide visual (the block is the left half; model extends +X to 32)
    shelter = [el([0, 0, 14], [1, 30, 15], allf('#p')), el([31, 0, 14], [32, 30, 15], allf('#p')), el([0, 0, 1], [1, 30, 2], allf('#p')),
               el([31, 0, 1], [32, 30, 2], allf('#p')), el([-1, 30, 0], [32, 32, 16], allf('#r')),
               el([1, 2, 14.2], [31, 28, 14.6], allf('#g')), el([0.2, 2, 2], [0.6, 28, 14], allf('#g')), el([31.4, 2, 2], [31.8, 28, 14], allf('#g')),
               el([4, 6, 10], [28, 7, 14], allf('#w')), el([4, 0, 12], [6, 6, 13], allf('#p')), el([26, 0, 12], [28, 6, 13], allf('#p')),
               el([20, 10, 14.6], [30, 26, 15], {'north': {'texture': '#t'}, 'south': {'texture': '#t'}})]
    model('bus_shelter', shelter, {'p': T('metal_dark'), 'r': T('metal_grey'), 'g': T('glass_panel'), 'w': T('wood'), 't': T('bus_timetable')}, ao=False)
    bs_facing('bus_shelter')
    item_from_block('bus_shelter')
    reg('bus_shelter', 'facing_shape', shape=[[0, 0, 1, 16, 16, 15]], material='metal', sound='metal', hardness=2, translucent=True)
    model('flower_planter', [el([1, 0, 1], [15, 8, 15], allf('#c')), el([2, 8, 2], [14, 9, 14], allf('#s')), el([3, 9, 3], [13, 15, 13], allf('#l'))],
          {'c': T('concrete'), 's': T('soil'), 'l': T('planter_leaves')})
    bs_single('flower_planter')
    item_from_block('flower_planter')
    reg('flower_planter', 'shape', shape=[[1, 0, 1, 15, 15, 15]], material='stone', sound='stone', hardness=1.5)
    # roadside tree: trunk + layered crown up to 2 blocks
    tree = [el([7, 0, 7], [9, 18, 9], allf('#b')), el([2, 14, 2], [14, 26, 14], allf('#l')), el([0, 18, 4], [16, 24, 12], allf('#l')),
            el([4, 18, 0], [12, 24, 16], allf('#l')), el([4, 26, 4], [12, 31, 12], allf('#l'))]
    model('roadside_tree', tree, {'b': T('tree_bark'), 'l': T('tree_leaves')}, ao=False)
    bs_single('roadside_tree')
    item_from_block('roadside_tree')
    reg('roadside_tree', 'shape', shape=[[6, 0, 6, 10, 16, 10]], material='wood', sound='grass', hardness=1, translucent=True)

    # ---------------- infrastructure with block entities (models are the static parts; BER draws moving parts)
    # street lamp: base block, pole blocks and a head
    model('street_lamp_base', [el([5, 0, 5], [11, 3, 11], allf('#d')), el([6.5, 3, 6.5], [9.5, 16, 9.5], allf('#d'))], {'d': T('metal_dark')})
    model('street_lamp_pole', [el([6.75, 0, 6.75], [9.25, 16, 9.25], allf('#d'))], {'d': T('metal_dark')})
    for st, lt in (('on', 'lamp_on'), ('off', 'lamp_off')):
        model(f'street_lamp_head_{st}', [el([6.75, 0, 6.75], [9.25, 8, 9.25], allf('#d')), el([7, 6, 2], [9, 8, 9], allf('#d')),
                                        el([5, 4, -8], [11, 7, 3], {'north': {'texture': '#d'}, 'south': {'texture': '#d'}, 'east': {'texture': '#d'}, 'west': {'texture': '#d'},
                                                                    'up': {'texture': '#d'}, 'down': {'texture': '#l'}}, shade=False)],
              {'d': T('metal_dark'), 'l': T(lt)}, ao=False)
    json.dump({'variants': {f'facing={f},part={p},lit={l}': {'model': T('street_lamp_' + ('head_' + ('on' if l == 'true' else 'off') if p == 'head' else p)), 'y': y}
                            for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)) for p in ('base', 'pole', 'head') for l in ('true', 'false')}},
              open(os.path.join(BS, 'street_lamp.json'), 'w'))
    item_flat('street_lamp', 'lamp_on')
    reg('street_lamp', 'street_lamp', material='metal', sound='metal', hardness=2)
    # security camera (static mount; head rotates in the BER)
    model('security_camera', [el([6, 10, 14], [10, 14, 16], allf('#d')), el([7, 9, 9], [9, 11, 15], allf('#d'))], {'d': T('metal_dark')})
    bs_facing('security_camera')
    item_flat('security_camera', 'camera_lens')
    reg('security_camera', 'camera', shape=[[5, 6, 4, 11, 14, 16]], material='metal', sound='metal', hardness=1)
    # fuel pump (nozzle + hose drawn by BER)
    pump = [el([2, 0, 3], [14, 2, 13], allf('#c')), el([3, 2, 4], [13, 26, 12], {'north': {'texture': '#f'}, 'south': {'texture': '#f'}, 'east': {'texture': '#s'},
                                                                              'west': {'texture': '#s'}, 'up': {'texture': '#t'}, 'down': {'texture': '#t'}}),
            el([2.5, 26, 3.5], [13.5, 30, 12.5], allf('#r'))]
    model('fuel_pump', pump, {'f': T('fuel_pump_face'), 's': T('fuel_pump_side'), 't': T('white_paint'), 'c': T('concrete'), 'r': T('red_paint')})
    bs_facing('fuel_pump')
    item_from_block('fuel_pump')
    reg('fuel_pump', 'fuel_pump', shape=[[2, 0, 3, 14, 16, 13]], material='metal', sound='metal', hardness=2)
    model('ev_charger', [el([4, 0, 5], [12, 20, 11], {'north': {'texture': '#f'}, 'south': {'texture': '#g'}, 'east': {'texture': '#g'}, 'west': {'texture': '#g'},
                                                     'up': {'texture': '#g'}, 'down': {'texture': '#g'}})], {'f': T('pay_terminal'), 'g': T('green_paint')})
    bs_facing('ev_charger')
    item_from_block('ev_charger')
    reg('ev_charger', 'fuel_pump', shape=[[4, 0, 5, 12, 16, 11]], material='metal', sound='metal', hardness=2, electric=True)
    model('payment_terminal', [el([7, 0, 7], [9, 10, 9], allf('#d')), el([4, 10, 6], [12, 20, 10], {'north': {'texture': '#f'}, 'south': {'texture': '#d'}, 'east': {'texture': '#d'},
                                                                                                 'west': {'texture': '#d'}, 'up': {'texture': '#d'}, 'down': {'texture': '#d'}})],
          {'d': T('metal_grey'), 'f': T('pay_terminal')})
    bs_facing('payment_terminal')
    item_from_block('payment_terminal')
    reg('payment_terminal', 'terminal', shape=[[4, 0, 6, 12, 16, 10]], material='metal', sound='metal', hardness=1.5)
    model('fuel_tank', [el([0, 0, 0], [16, 4, 16], allf('#c')), el([2, 4, 2], [14, 6, 14], allf('#m')), el([6, 6, 6], [10, 8, 10], allf('#r'))],
          {'c': T('concrete'), 'm': T('metal_grey'), 'r': T('red_paint')})
    bs_single('fuel_tank')
    item_from_block('fuel_tank')
    reg('fuel_tank', 'fuel_tank', shape=[[0, 0, 0, 16, 8, 16]], material='metal', sound='metal', hardness=3)
    model('canopy_light', [el([0, 13, 0], [16, 16, 16], {'up': {'texture': '#m'}, 'down': {'texture': '#l'}, 'north': {'texture': '#m'}, 'south': {'texture': '#m'},
                                                         'east': {'texture': '#m'}, 'west': {'texture': '#m'}}, shade=False)], {'m': T('ceiling_panel'), 'l': T('lamp_on')}, ao=False)
    bs_single('canopy_light')
    item_from_block('canopy_light')
    reg('canopy_light', 'shape', shape=[[0, 13, 0, 16, 16, 16]], material='metal', sound='metal', hardness=1, light=15)
    simple_cube('canopy_roof', 'ceiling_panel')
    bs_single('canopy_roof')
    item_from_block('canopy_roof')
    reg('canopy_roof', 'cube', material='metal', sound='metal', hardness=1.5)
    model('ceiling_light', [el([2, 14, 2], [14, 16, 14], {'down': {'texture': '#l'}, 'up': {'texture': '#m'}, 'north': {'texture': '#m'}, 'south': {'texture': '#m'},
                                                         'east': {'texture': '#m'}, 'west': {'texture': '#m'}}, shade=False)], {'m': T('ceiling_panel'), 'l': T('lamp_on')}, ao=False)
    bs_single('ceiling_light')
    item_from_block('ceiling_light')
    reg('ceiling_light', 'shape', shape=[[2, 14, 2, 14, 16, 14]], material='metal', sound='metal', hardness=1, light=15, nocollide=True)
    model('cashier_counter', [el([0, 0, 2], [16, 14, 16], allf('#s')), el([0, 14, 0], [16, 16, 16], allf('#t')), el([3, 16, 4], [8, 20, 8], allf('#d'))],
          {'s': T('booth_wall'), 't': T('counter_top'), 'd': T('pay_terminal')})
    bs_facing('cashier_counter')
    item_from_block('cashier_counter')
    reg('cashier_counter', 'workplace', shape=[[0, 0, 0, 16, 16, 16]], material='wood', sound='wood', hardness=2, workplace='cashier')
    model('store_shelf', [el([0, 0, 4], [16, 30, 12], allf('#s'))], {'s': T('shelf')})
    bs_facing('store_shelf')
    item_from_block('store_shelf')
    reg('store_shelf', 'facing_shape', shape=[[0, 0, 4, 16, 16, 12]], material='metal', sound='metal', hardness=2)
    simple_cube('shop_sign_block', 'shop_sign')
    bs_single('shop_sign_block')
    item_from_block('shop_sign_block')
    reg('shop_sign_block', 'cube', material='metal', sound='metal', hardness=1, light=12)
    simple_cube('booth_wall', 'booth_wall')
    bs_single('booth_wall')
    item_from_block('booth_wall')
    reg('booth_wall', 'cube', material='stone', sound='stone', hardness=2)
    model('booth_desk', [el([0, 0, 0], [16, 12, 16], allf('#s')), el([0, 12, 0], [16, 14, 16], allf('#t')), el([10, 14, 6], [14, 18, 10], allf('#d'))],
          {'s': T('booth_wall'), 't': T('counter_top'), 'd': T('camera_lens')})
    bs_facing('booth_desk')
    item_from_block('booth_desk')
    reg('booth_desk', 'workplace', shape=[[0, 0, 0, 16, 14, 16]], material='wood', sound='wood', hardness=2, workplace='officer')
    model('warning_light', [el([5, 0, 5], [11, 3, 11], allf('#d')), el([6, 3, 6], [10, 9, 10], allf('#o'), shade=False)], {'d': T('metal_dark'), 'o': T('orange')})
    bs_single('warning_light')
    item_from_block('warning_light')
    reg('warning_light', 'shape', shape=[[5, 0, 5, 11, 9, 11]], material='metal', sound='metal', hardness=1, light=9)
    # traffic light & pedestrian signal (housing static; lamps by BER)
    tl = [el([7, 0, 7], [9, 16, 9], allf('#p'))]
    model('traffic_light_pole', tl, {'p': T('metal_dark')})
    model('traffic_light', [el([7, 0, 7], [9, 16, 9], allf('#p'))], {'p': T('metal_dark')})
    bs_facing('traffic_light')
    item_flat('traffic_light', 'sign_traffic_lights')
    reg('traffic_light', 'traffic_light', shape=[[6, 0, 6, 10, 16, 10]], material='metal', sound='metal', hardness=2)
    model('pedestrian_signal', [el([7, 0, 7], [9, 16, 9], allf('#p'))], {'p': T('metal_dark')})
    bs_facing('pedestrian_signal')
    item_flat('pedestrian_signal', 'sign_crossing')
    reg('pedestrian_signal', 'ped_signal', shape=[[6, 0, 6, 10, 16, 10]], material='metal', sound='metal', hardness=2)
    # barrier gate (arm drawn by BER; collision via invisible arm blocks)
    model('barrier_gate', [el([3, 0, 3], [13, 18, 13], allf('#s')), el([2, 18, 2], [14, 20, 14], allf('#d'))], {'s': T('stripes_red'), 'd': T('metal_dark')})
    bs_facing('barrier_gate')
    item_from_block('barrier_gate')
    reg('barrier_gate', 'barrier_gate', shape=[[3, 0, 3, 13, 16, 13]], material='metal', sound='metal', hardness=3)
    model('barrier_arm', [], {'p': T('stripes_red')})
    bs_single('barrier_arm')
    reg('barrier_arm', 'barrier_arm', material='metal', sound='metal', hardness=-1, noitem=True)
    # control panels (node / traffic control centre)
    model('traffic_control_panel', [el([1, 0, 1], [15, 12, 15], allf('#d')), el([1, 12, 3], [15, 16, 13], {'north': {'texture': '#f'}, 'up': {'texture': '#d'},
                                                                                                         'south': {'texture': '#d'}, 'east': {'texture': '#d'}, 'west': {'texture': '#d'}})],
          {'d': T('metal_dark'), 'f': T('pay_terminal')})
    bs_facing('traffic_control_panel')
    item_from_block('traffic_control_panel')
    reg('traffic_control_panel', 'control_panel', shape=[[1, 0, 1, 15, 16, 15]], material='metal', sound='metal', hardness=2)
    json.dump(BLOCKS, open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'blocks.json'), 'w'), indent=1)


if __name__ == '__main__':
    surfaces()
    signs()
    build_models()
    print(len(BLOCKS), 'blocks')
