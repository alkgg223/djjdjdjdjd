"""Hotel room furniture and decoration: textures, models and blockstates (37 decor blocks, TV, double bed, mess)."""
import os, json, random
from pix import Canvas, hexc, mul, mix, tex

C = hexc
MOD = 'rpbusiness'


def T(name):
    return f'{MOD}:block/{name}'


def fabric(base, seed):
    c = tex(16, 16, C(base), seed, 0.05)
    for y in range(0, 16, 2):
        for x in range((y // 2) % 2, 16, 2):
            c.px(x, y, mul(c.get(x, y), 0.93))
    return c


def rug(base, border, accent, seed):
    c = tex(16, 16, C(base), seed, 0.04)
    c.rect(0, 0, 15, 0, C(border)); c.rect(0, 15, 15, 15, C(border)); c.rect(0, 0, 0, 15, C(border)); c.rect(15, 0, 15, 15, C(border))
    c.rect(2, 2, 13, 2, C(accent)); c.rect(2, 13, 13, 13, C(accent)); c.rect(2, 2, 2, 13, C(accent)); c.rect(13, 2, 13, 13, C(accent))
    for i in range(4):
        c.px(7 - i, 4 + i, C(accent)); c.px(8 + i, 4 + i, C(accent)); c.px(7 - i, 11 - i, C(accent)); c.px(8 + i, 11 - i, C(accent))
    return c


def textures():
    t = {}
    t['fabric_gray'] = fabric('8a8f96', 101)
    t['fabric_blue'] = fabric('3b5f9e', 102)
    t['fabric_beige'] = fabric('cdb48f', 103)
    t['leather_black'] = tex(16, 16, C('26282c'), 104, 0.04)
    t['porcelain'] = tex(16, 16, C('f3f5f7'), 105, 0.015)
    t['chrome'] = tex(16, 16, C('dfe5ea'), 106, 0.03)
    t['wood_dark'] = tex(16, 16, C('4a3222'), 107, 0.05)
    t['wood_white'] = tex(16, 16, C('e9e4da'), 108, 0.03)
    t['tile_white'] = tex(16, 16, C('eef2f4'), 109, 0.02)
    for x in range(16):
        t['tile_white'].px(x, 7, C('c9d2d8')); t['tile_white'].px(x, 15, C('c9d2d8'))
    for y in range(16):
        t['tile_white'].px(7, y, C('c9d2d8')); t['tile_white'].px(15, y, C('c9d2d8'))
    w = tex(16, 16, C('5fb3e6'), 110, 0.04)
    for x in range(0, 16, 4):
        w.px(x, 5, C('9fd6f5')); w.px(x + 1, 11, C('9fd6f5'))
    t['water'] = w
    t['tv_frame'] = tex(16, 16, C('16181b'), 111, 0.02)
    off = tex(16, 16, C('0d1014'), 112, 0.01)
    off.line(10, 2, 13, 5, C('2a3038'))
    t['tv_screen'] = off
    # animated programme: 8 frames
    r = random.Random(113)
    on = Canvas(16, 16 * 8)
    for f in range(8):
        y0 = f * 16
        sky = C(['4aa3df', '58b0e6', 'f0a25a', '3d4b8f', '4aa3df', '6cc07a', 'e3c35a', '5a7fd1'][f])
        on.rect(0, y0, 15, y0 + 15, sky)
        on.rect(0, y0 + 10, 15, y0 + 15, C('3f8f3a') if f % 3 else C('2f6fb0'))
        bx = (f * 2) % 14
        on.rect(bx, y0 + 6, bx + 2, y0 + 9, C('f2d7b6')); on.rect(bx, y0 + 4, bx + 2, y0 + 5, C('3b2a1a'))
        on.ellipse(12 - (f % 4), y0 + 3, 1.6, 1.6, C('ffe36b'))
        for i in range(6):
            on.px(r.randint(0, 15), y0 + r.randint(0, 15), mul(sky, 1.15))
        on.rect(0, y0 + 14, 15, y0 + 15, C('1e2124')); on.rect(1, y0 + 14, 5 + f, y0 + 14, C('e0372f'))
    t['tv_screen_on'] = on
    t['rug_red'] = rug('a8282a', '6e1617', 'e7c36a', 114)
    t['rug_beige'] = rug('d9c7a3', 'a58d63', '7a5c3c', 115)
    t['rug_blue'] = rug('2f4f8f', '1b2f57', 'd9dde1', 116)
    cu = tex(16, 16, C('7b2a3a'), 117, 0.03)
    for x in range(0, 16, 3):
        for y in range(16):
            cu.px(x, y, mul(cu.get(x, y), 0.8))
    cu.rect(0, 0, 15, 1, C('c9a36a'))
    t['curtain'] = cu
    ps = Canvas()
    ps.rect(0, 0, 15, 15, C('c9a36a'))
    ps.rect(1, 1, 14, 7, C('8fd0f0')); ps.rect(1, 8, 14, 14, C('2f7fb8'))
    ps.ellipse(11, 4, 1.8, 1.8, C('fff0a0'))
    for x in range(1, 15, 3):
        ps.px(x, 10, C('d6f0fb')); ps.px(x + 1, 12, C('d6f0fb'))
    ps.rect(3, 6, 5, 7, C('f4f1e6')); ps.px(4, 5, C('f4f1e6'))
    t['painting_sea'] = ps
    pc = Canvas()
    pc.rect(0, 0, 15, 15, C('2b2f36'))
    pc.rect(1, 1, 14, 14, C('f0a25a')); pc.rect(1, 1, 14, 5, C('e3725a'))
    for (x0, h, col) in ((1, 7, '3b3f56'), (4, 10, '2c3046'), (7, 6, '434863'), (9, 11, '272a3d'), (12, 8, '3b3f56')):
        pc.rect(x0, 14 - h, x0 + 2, 14, C(col))
        for yy in range(15 - h, 14, 2):
            pc.px(x0 + 1, yy, C('ffe36b'))
    t['painting_city'] = pc
    ck = Canvas()
    ck.ellipse(8, 8, 7, 7, C('4a3222')); ck.ellipse(8, 8, 6, 6, C('f4f1e6'))
    for (x, y) in ((8, 3), (13, 8), (8, 13), (3, 8)):
        ck.px(x, y, C('1e2124'))
    ck.line(8, 8, 8, 4, C('1e2124')); ck.line(8, 8, 11, 9, C('1e2124'))
    t['clock_face'] = ck
    wf = tex(16, 16, C('6b4a32'), 118, 0.04)
    wf.rect(0, 0, 15, 15, C('5a3d29')); wf.rect(1, 1, 6, 14, C('7a563b')); wf.rect(9, 1, 14, 14, C('7a563b'))
    wf.rect(7, 1, 8, 14, C('4e3524')); wf.rect(6, 6, 6, 9, C('d7dde1')); wf.rect(9, 6, 9, 9, C('d7dde1'))
    t['wardrobe_front'] = wf
    df = tex(16, 16, C('e9e4da'), 119, 0.03)
    for y0 in (1, 6, 11):
        df.rect(1, y0, 14, y0 + 3, C('d8d1c4')); df.rect(6, y0 + 1, 9, y0 + 1, C('8f7a5a'))
    t['dresser_front'] = df
    mb = tex(16, 16, C('2b2f36'), 120, 0.02)
    mb.rect(1, 1, 14, 14, C('6fa8c9')); mb.rect(2, 2, 13, 13, C('9fd0ea'))
    for x in (3, 6, 9, 12):
        mb.rect(x, 9, x + 1, 13, C(random.Random(x).choice(['e0372f', 'f4f1e6', '6fd04a', 'ffcf3a'])))
    mb.rect(3, 5, 12, 5, C('cfd8dc'))
    mb.rect(13, 6, 13, 10, C('dfe5ea'))
    t['minibar_front'] = mb
    bk = tex(16, 16, C('e9e4da'), 121, 0.02)
    r = random.Random(122)
    for y0 in (1, 8):
        bk.rect(0, y0 + 6, 15, y0 + 6, C('c9c1b2'))
        x = 1
        while x < 15:
            wdt = r.randint(1, 2); hgt = r.randint(4, 5)
            bk.rect(x, y0 + 6 - hgt, min(14, x + wdt - 1), y0 + 5, C(r.choice(['b23a3a', '3a5fb2', '3a9a5a', 'd9a43a', '6a4a8a', '2b2f36'])))
            x += wdt + (1 if r.random() < 0.25 else 0)
    t['books'] = bk
    t['plant_leaves'] = tex(16, 16, C('3f8f3a'), 123, 0.12)
    lf = Canvas()
    for i in range(14):
        lf.ellipse(r.randint(2, 13), r.randint(1, 14), 2.2, 1.3, C(r.choice(['3f8f3a', '4fa548', '2f7a32'])))
    t['leaf_cross'] = lf
    t['ceramic_blue'] = tex(16, 16, C('3b6fb0'), 124, 0.03)
    fl = Canvas()
    for (x, y, col) in ((5, 4, 'e0372f'), (10, 3, 'ffcf3a'), (8, 7, 'e874b5'), (4, 9, 'f4f1e6'), (11, 9, 'e0372f')):
        fl.line(x, y + 1, 8, 15, C('3f8f3a')); fl.ellipse(x, y, 1.6, 1.6, C(col)); fl.px(x, y, C('ffe36b'))
    t['flowers'] = fl
    ac = tex(16, 16, C('f3f5f7'), 125, 0.015)
    for y in range(9, 14, 2):
        ac.rect(1, y, 14, y, C('b9c0c7'))
    ac.rect(11, 3, 13, 3, C('6fd04a')); ac.rect(2, 3, 7, 5, C('dfe5ea'))
    t['ac_front'] = ac
    rd = tex(16, 16, C('f0f2f4'), 126, 0.02)
    for x in range(1, 16, 2):
        rd.rect(x, 0, x, 15, C('c9d2d8'))
    t['radiator'] = rd
    tb = tex(16, 16, C('8f9aa2'), 127, 0.03)
    for y in range(1, 16, 3):
        for x in range(0, 16):
            if (x + y) % 3 == 0:
                tb.px(x, y, C('5d666e'))
    t['bin_mesh'] = tb
    t['phone'] = tex(16, 16, C('d9d4c8'), 128, 0.02)
    kp = Canvas()
    kp.rect(0, 0, 15, 15, C('d9d4c8'))
    for y in (3, 6, 9, 12):
        for x in (3, 7, 11):
            kp.rect(x, y, x + 1, y + 1, C('2b2f36'))
    t['phone_keys'] = kp
    sg = tex(16, 16, C('b08d3a'), 129, 0.04)
    sg.rect(0, 0, 15, 15, C('8a6a24')); sg.rect(1, 1, 14, 14, C('c9a24a'))
    for (x, y) in ((6, 4), (7, 3), (7, 4), (7, 5), (7, 6), (7, 7), (7, 8), (7, 9), (7, 10), (7, 11), (6, 11), (8, 11)):
        sg.px(x, y, C('2b2f36'))
    t['room_sign'] = sg
    mr = Canvas()
    for y in range(16):
        for x in range(16):
            mr.px(x, y, mix(C('d6eef8'), C('8fb8cc'), (x + y) / 30))
    mr.line(3, 10, 8, 5, C('f4fbff')); mr.line(5, 12, 11, 6, C('f4fbff'))
    t['mirror_glass'] = mr
    tw = tex(16, 16, C('f4f1e6'), 130, 0.03)
    tw.rect(0, 11, 15, 12, C('3b6fb0'))
    t['towel'] = tw
    t['lampshade_beige'] = tex(16, 16, C('efe0c0'), 131, 0.03)
    db = tex(16, 16, C('7b2a3a'), 132, 0.04)
    for y in range(0, 16, 4):
        db.rect(0, y, 15, y, C('8e3a4a'))
    t['blanket_red'] = db
    # dirty overlays (cutout)
    st = Canvas()
    for (x, y, rx, ry) in ((4, 5, 2.6, 1.8), (11, 10, 2.2, 1.6), (7, 12, 1.4, 1.0)):
        st.ellipse(x, y, rx, ry, C('7a5a32', 170))
    for (x, y) in ((9, 3), (2, 11), (13, 6), (12, 13)):
        st.px(x, y, C('5a4026', 200))
    t['stain'] = st
    ms = Canvas()
    ms.rect(2, 2, 6, 5, C('f4f6f7')); ms.line(2, 2, 6, 5, C('c9d2d8'))
    ms.rect(9, 9, 13, 11, C('e0372f')); ms.rect(10, 10, 12, 10, C('ffcf3a'))
    ms.ellipse(11, 4, 2.4, 1.6, C('6b4a32', 180))
    ms.ellipse(4, 12, 2.6, 1.8, C('7a5a32', 170))
    for (x, y) in ((8, 6), (6, 9), (13, 14), (1, 8), (7, 14)):
        ms.px(x, y, C('c9a36a'))
    t['room_mess'] = ms
    return t


def model(textures, elements, extra=None):
    m = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}
    if extra:
        m.update(extra)
    return m


def el(fr, to, faces, rot=None):
    if isinstance(faces, str):
        faces = {k: faces for k in ('north', 'south', 'east', 'west', 'up', 'down')}
    e = {'from': list(fr), 'to': list(to), 'faces': {k: {'texture': '#' + v} for k, v in faces.items() if v}}
    if rot:
        e['rotation'] = rot
    return e


def sides(main, top=None, bottom=None, front=None, back=None):
    return {'north': front or main, 'south': back or main, 'east': main, 'west': main, 'up': top or main, 'down': bottom or main}


CUT = {'render_type': 'minecraft:cutout'}
TRANS = {'render_type': 'minecraft:translucent'}

# seats with a dirty=true variant: name -> top surface y used for the stain overlay (x0, z0, x1, z1)
SEATS = {}


def build_models(M):
    def seat(name, tx, els, stain_box, extra=None):
        M[name] = model(tx, els, extra)
        x0, y, z0, x1, z1 = stain_box
        dtx = dict(tx); dtx['stain_o'] = T('stain')
        M[name + '_dirty'] = model(dtx, els + [el((x0, y + 0.02, z0), (x1, y + 0.03, z1), {'up': 'stain_o'})], CUT)
        SEATS[name] = True

    # ---- seats (base model faces north, back at z = 16)
    def sofa_els():
        return [el((0, 0, 1), (16, 2, 15), 'w'), el((0, 2, 1), (16, 8, 15), sides('c')), el((0, 8, 11), (16, 16, 15), sides('c')),
                el((0, 8, 1), (2, 12, 11), sides('c')), el((14, 8, 1), (16, 12, 11), sides('c')),
                el((2.5, 8, 2), (8, 9.5, 11), sides('c')), el((8, 8, 2), (13.5, 9.5, 11), sides('c'))]
    seat('sofa_gray', {'particle': T('fabric_gray'), 'c': T('fabric_gray'), 'w': T('wood_dark')}, sofa_els(), (2.5, 9.5, 2, 13.5, 11))
    seat('sofa_blue', {'particle': T('fabric_blue'), 'c': T('fabric_blue'), 'w': T('wood_dark')}, sofa_els(), (2.5, 9.5, 2, 13.5, 11))
    seat('armchair', {'particle': T('fabric_beige'), 'c': T('fabric_beige'), 'w': T('wood_dark')}, [
        el((2.5, 0, 2.5), (4, 2, 4), 'w'), el((12, 0, 2.5), (13.5, 2, 4), 'w'), el((2.5, 0, 12), (4, 2, 13.5), 'w'), el((12, 0, 12), (13.5, 2, 13.5), 'w'),
        el((2, 2, 2), (14, 8, 14), sides('c')), el((2, 8, 11), (14, 17, 14), sides('c')),
        el((2, 8, 2), (4, 12, 11), sides('c')), el((12, 8, 2), (14, 12, 11), sides('c'))], (4, 8, 2, 12, 11))
    seat('office_chair', {'particle': T('leather_black'), 'c': T('leather_black'), 'm': T('chrome')}, [
        el((3, 0, 7.5), (13, 1, 8.5), 'm'), el((7.5, 0, 3), (8.5, 1, 13), 'm'), el((7.25, 1, 7.25), (8.75, 7, 8.75), 'm'),
        el((3, 7, 3), (13, 9, 13), sides('c')), el((3.5, 9, 11), (12.5, 19, 13), sides('c')), el((7.25, 8, 13), (8.75, 12, 13.5), 'm'),
        el((2, 9, 5), (3, 12, 10), 'c'), el((13, 9, 5), (14, 12, 10), 'c')], (3, 9, 3, 13, 11))
    seat('toilet', {'particle': T('porcelain'), 'p': T('porcelain'), 'm': T('chrome'), 'w': T('water')}, [
        el((5, 0, 4), (11, 6, 12), sides('p')), el((4, 6, 2), (12, 8, 12), {'north': 'p', 'south': 'p', 'east': 'p', 'west': 'p', 'down': 'p', 'up': 'p'}),
        el((5, 8.01, 3), (11, 8.02, 10), {'up': 'w'}),
        el((3, 8, 11), (13, 16, 15), sides('p')), el((10, 14, 10.5), (12, 15, 11), 'm')], (4.5, 8.1, 2.5, 11.5, 10.5))
    seat('bathtub', {'particle': T('porcelain'), 'p': T('porcelain'), 'm': T('chrome'), 'w': T('water')}, [
        el((0, 0, 0), (16, 2, 16), 'p'), el((0, 2, 0), (16, 9, 1.5), sides('p')), el((0, 2, 14.5), (16, 9, 16), sides('p')),
        el((0, 2, 1.5), (1.5, 9, 14.5), sides('p')), el((14.5, 2, 1.5), (16, 9, 14.5), sides('p')),
        el((1.5, 6, 1.5), (14.5, 6.01, 14.5), {'up': 'w'}),
        el((7, 9, 14), (9, 12, 15.5), 'm'), el((7.5, 11, 12), (8.5, 12, 14), 'm')], (1.5, 6.05, 1.5, 14.5, 14.5), TRANS)
    # double bed: long like the hotel bed, two pillows and a wide blanket
    seat('double_bed', {'particle': T('bed_sheet'), 's': T('bed_sheet'), 'p': T('white'), 'w': T('wood_dark'), 'b': T('blanket_red')}, [
        el((0, 0, -16), (16, 3, 16), 'w'),
        el((0.3, 3, -15.7), (15.7, 8, 15.5), sides('s')),
        el((1, 8, 10), (7.5, 10, 15), 'p'), el((8.5, 8, 10), (15, 10, 15), 'p'),
        el((0.2, 8, -15.8), (15.8, 8.6, 7), sides('b')),
        el((0, 0, 15), (16, 18, 16), 'w'), el((0, 16, 14.5), (16, 18, 15), 'w')], (1, 8.6, -12, 15, 4))

    # ---- TV (screen faces north)
    tvel = [el((6, 0, 6), (10, 1, 10), 'f'), el((7.5, 1, 7.5), (8.5, 3, 8.5), 'f'),
            el((1, 3, 7), (15, 11, 9), {'north': 's', 'south': 'f', 'east': 'f', 'west': 'f', 'up': 'f', 'down': 'f'})]
    M['tv'] = model({'particle': T('tv_frame'), 'f': T('tv_frame'), 's': T('tv_screen')}, tvel)
    M['tv_on'] = model({'particle': T('tv_frame'), 'f': T('tv_frame'), 's': T('tv_screen_on')}, tvel)

    # ---- tables & storage
    M['coffee_table'] = model({'particle': T('wood_dark'), 'w': T('wood_dark'), 'g': T('mirror_glass')}, [
        el((1, 6, 3), (15, 7, 13), sides('w', top='g')),
        el((1.5, 0, 3.5), (3, 6, 5), 'w'), el((13, 0, 3.5), (14.5, 6, 5), 'w'), el((1.5, 0, 11), (3, 6, 12.5), 'w'), el((13, 0, 11), (14.5, 6, 12.5), 'w'),
        el((2, 2, 4), (14, 2.8, 12), 'w')])
    M['nightstand'] = model({'particle': T('wood_white'), 'w': T('wood_white'), 'f': T('dresser_front')}, [
        el((2, 1, 2), (14, 12, 14), sides('w', front='f')), el((2.5, 0, 2.5), (4, 1, 4), 'w'), el((12, 0, 2.5), (13.5, 1, 4), 'w'),
        el((2.5, 0, 12), (4, 1, 13.5), 'w'), el((12, 0, 12), (13.5, 1, 13.5), 'w')])
    M['wardrobe'] = model({'particle': T('walnut'), 'w': T('walnut'), 'f': T('wardrobe_front')}, [el((0, 0, 0), (16, 16, 16), sides('w', front='f'))])
    M['dresser'] = model({'particle': T('wood_white'), 'w': T('wood_white'), 'f': T('dresser_front')}, [el((0, 0, 0), (16, 16, 16), sides('w', front='f'))])
    M['minibar'] = model({'particle': T('tv_frame'), 'w': T('tv_frame'), 'f': T('minibar_front')}, [el((0, 0, 0), (16, 16, 16), sides('w', front='f'))])
    M['bookshelf_modern'] = model({'particle': T('wood_white'), 'w': T('wood_white'), 'f': T('books')}, [el((0, 0, 0), (16, 16, 16), sides('w', front='f'))])
    M['desk'] = model({'particle': T('wood_dark'), 'w': T('wood_dark'), 'f': T('drawer_front')}, [
        el((0, 13, 2), (16, 16, 14), 'w'), el((1, 0, 3), (3, 13, 13), 'w'), el((13, 0, 3), (15, 13, 13), 'w'),
        el((3, 8, 3), (13, 13, 4), {'north': 'f', 'south': 'w', 'east': 'w', 'west': 'w', 'up': 'w', 'down': 'w'}), el((3, 4, 12), (13, 13, 13), 'w')])
    M['luggage_rack'] = model({'particle': T('walnut'), 'w': T('walnut'), 'c': T('fabric_red')}, [
        el((1, 7, 3), (15, 9, 13), sides('w')), el((1.5, 7.5, 4.5), (14.5, 9.2, 5.5), 'c'), el((1.5, 7.5, 10.5), (14.5, 9.2, 11.5), 'c'),
        el((2, 0, 3.5), (3, 7, 4.5), 'w'), el((13, 0, 3.5), (14, 7, 4.5), 'w'), el((2, 0, 11.5), (3, 7, 12.5), 'w'), el((13, 0, 11.5), (14, 7, 12.5), 'w')])

    # ---- lamps & electrics
    M['table_lamp'] = model({'particle': T('lampshade_beige'), 's': T('lampshade_beige'), 'b': T('ceramic_blue'), 'l': T('bulb')}, [
        el((6, 0, 6), (10, 4, 10), 'b'), el((7.5, 4, 7.5), (8.5, 6, 8.5), 'b'),
        el((5, 6, 5), (11, 10, 11), {'north': 's', 'south': 's', 'east': 's', 'west': 's', 'up': 's', 'down': 'l'})])
    M['floor_lamp'] = model({'particle': T('lampshade_beige'), 's': T('lampshade_beige'), 'm': T('steel_dark'), 'l': T('bulb')}, [
        el((5.5, 0, 5.5), (10.5, 1, 10.5), 'm'), el((7.5, 1, 7.5), (8.5, 12, 8.5), 'm'),
        el((5, 12, 5), (11, 16, 11), {'north': 's', 'south': 's', 'east': 's', 'west': 's', 'up': 's', 'down': 'l'})])
    blades = [el((7, 13.5, 1), (9, 14, 15), 'b'), el((1, 13.5, 7), (15, 14, 9), 'b')]
    M['ceiling_fan'] = model({'particle': T('wood_dark'), 'b': T('wood_dark'), 'm': T('steel_dark'), 'l': T('bulb')}, [
        el((7.5, 14, 7.5), (8.5, 16, 8.5), 'm'), el((6, 12.5, 6), (10, 14, 10), sides('m', bottom='l'))]
        + [dict(b, rotation={'origin': [8, 13.75, 8], 'axis': 'y', 'angle': 22.5}) for b in blades])
    M['air_conditioner'] = model({'particle': T('porcelain'), 'p': T('porcelain'), 'f': T('ac_front')}, [el((1, 8, 11), (15, 15, 16), sides('p', front='f'))])
    M['radiator'] = model({'particle': T('radiator'), 'r': T('radiator'), 'm': T('chrome')}, [
        el((1, 1, 12), (15, 11, 15), sides('r')), el((2, 0, 14), (3, 1, 15), 'm'), el((13, 0, 14), (14, 1, 15), 'm')])
    M['room_phone'] = model({'particle': T('phone'), 'p': T('phone'), 'k': T('phone_keys'), 'd': T('tv_frame')}, [
        el((4, 0, 5), (12, 2, 11), sides('p', top='k')), el((4, 2, 8.5), (12, 3.5, 10.5), 'd'), el((4, 3, 8.5), (5.5, 4, 10.5), 'd'), el((10.5, 3, 8.5), (12, 4, 10.5), 'd')])

    # ---- wall decoration (back against z = 16)
    M['mirror'] = model({'particle': T('mirror_glass'), 'g': T('mirror_glass'), 'w': T('walnut')}, [
        el((2, 0, 15), (14, 16, 16), {'north': 'w', 'south': 'w', 'east': 'w', 'west': 'w', 'up': 'w', 'down': 'w'}),
        el((3, 1, 14.9), (13, 15, 15), {'north': 'g'})])
    M['curtains'] = model({'particle': T('curtain'), 'c': T('curtain'), 'm': T('chrome')}, [
        el((0, 15, 14.5), (16, 16, 16), 'm'), el((0, 0, 14), (5, 15, 15.5), sides('c')), el((11, 0, 14), (16, 15, 15.5), sides('c'))])
    for n in ('painting_sea', 'painting_city'):
        M[n] = model({'particle': T(n), 'p': T(n), 'w': T('walnut')}, [
            el((1, 3, 15), (15, 13, 16), {'north': 'p', 'south': 'w', 'east': 'w', 'west': 'w', 'up': 'w', 'down': 'w'})])
    M['wall_clock'] = model({'particle': T('clock_face'), 'c': T('clock_face'), 'w': T('wood_dark')}, [
        el((4, 4, 15), (12, 12, 16), {'north': 'c', 'south': 'w', 'east': 'w', 'west': 'w', 'up': 'w', 'down': 'w'})], CUT)
    M['towel_rack'] = model({'particle': T('towel'), 't': T('towel'), 'm': T('chrome')}, [
        el((2, 12, 14.5), (14, 13, 15.5), 'm'), el((2, 11, 15), (3, 13, 16), 'm'), el((13, 11, 15), (14, 13, 16), 'm'),
        el((3, 4, 13.5), (13, 13, 14.5), sides('t'))])
    M['room_sign'] = model({'particle': T('room_sign'), 's': T('room_sign'), 'b': T('steel_dark')}, [
        el((4, 5, 15), (12, 11, 16), {'north': 's', 'south': 'b', 'east': 'b', 'west': 'b', 'up': 'b', 'down': 'b'})])

    # ---- floor
    for n in ('rug_red', 'rug_beige', 'rug_blue'):
        M[n] = model({'particle': T(n), 'r': T(n)}, [el((0, 0, 0), (16, 1, 16), sides('r'))])
    M['room_mess'] = model({'particle': T('room_mess'), 'm': T('room_mess')}, [el((0, 0.05, 0), (16, 0.1, 16), {'up': 'm', 'down': 'm'})], CUT)

    # ---- bathroom
    M['shower'] = model({'particle': T('tile_white'), 't': T('tile_white'), 'g': T('glass_front'), 'm': T('chrome')}, [
        el((0, 0, 0), (16, 1, 16), 't'), el((0, 1, 15), (16, 16, 16), 't'), el((0, 1, 0), (1, 16, 15), 'g'),
        el((1, 1, 0), (16, 16, 0.5), {'north': 'g', 'south': 'g'}),
        el((7, 13, 11), (9, 14, 15), 'm'), el((6.5, 12, 10.5), (9.5, 13, 12.5), 'm'), el((7.5, 6, 15), (8.5, 13, 15.5), 'm')], TRANS)
    M['bathroom_sink'] = model({'particle': T('porcelain'), 'p': T('porcelain'), 'w': T('water'), 'm': T('chrome'), 'f': T('dresser_front')}, [
        el((3, 0, 7), (13, 10, 16), sides('p', front='f')), el((3, 10, 6), (13, 13, 16), sides('p')),
        el((5, 13.01, 8), (11, 13.02, 13), {'up': 'w'}), el((7.5, 13, 13.5), (8.5, 15, 14.5), 'm'), el((7.5, 14, 11.5), (8.5, 15, 13.5), 'm')])

    # ---- plants & small items
    M['tall_plant'] = model({'particle': T('terracotta'), 'p': T('ceramic_blue'), 'd': T('walnut'), 'l': T('leaf_cross'), 'g': T('plant_leaves')}, [
        el((4, 0, 4), (12, 6, 12), sides('p', top='d')),
        el((1, 5, 8), (15, 16, 8), {'north': 'l', 'south': 'l'}, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}),
        el((8, 5, 1), (8, 16, 15), {'east': 'l', 'west': 'l'}, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}),
        el((1, 5, 8), (15, 16, 8), {'north': 'l', 'south': 'l'}), el((8, 5, 1), (8, 16, 15), {'east': 'l', 'west': 'l'})], CUT)
    M['flower_vase'] = model({'particle': T('ceramic_blue'), 'v': T('ceramic_blue'), 'f': T('flowers')}, [
        el((5.5, 0, 5.5), (10.5, 6, 10.5), sides('v')), el((6.5, 6, 6.5), (9.5, 7, 9.5), 'v'),
        el((3, 6, 8), (13, 14, 8), {'north': 'f', 'south': 'f'}, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}),
        el((8, 6, 3), (8, 14, 13), {'east': 'f', 'west': 'f'}, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45})], CUT)
    M['trash_bin'] = model({'particle': T('bin_mesh'), 'b': T('bin_mesh'), 'd': T('steel_dark')}, [
        el((4, 0, 4), (12, 10, 12), sides('b', top='d')), el((3.5, 10, 3.5), (12.5, 11, 12.5), 'd')])

    # ---- item models for wall/ceiling pieces look better a bit bigger in the GUI
    return M


FACING = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

PLAIN = ['coffee_table', 'nightstand', 'table_lamp', 'floor_lamp', 'wardrobe', 'dresser', 'minibar', 'mirror', 'rug_red', 'rug_beige', 'rug_blue',
         'curtains', 'painting_sea', 'painting_city', 'wall_clock', 'desk', 'shower', 'bathroom_sink', 'towel_rack', 'bookshelf_modern',
         'tall_plant', 'flower_vase', 'ceiling_fan', 'air_conditioner', 'radiator', 'luggage_rack', 'trash_bin', 'room_phone', 'room_sign']


def blockstates(bs):
    for n in PLAIN:
        bs[n] = {'variants': {f'facing={f}': {'model': f'{MOD}:block/{n}', 'y': y} for f, y in FACING.items()}}
    for n in list(SEATS) + ['dining_chair', 'bar_stool', 'sofa', 'hotel_bed']:
        bs[n] = {'variants': {f'dirty={d},facing={f}': {'model': f'{MOD}:block/{n}' + ('_dirty' if d == 'true' else ''), 'y': y}
                              for f, y in FACING.items() for d in ('false', 'true')}}
    bs['tv'] = {'variants': {f'facing={f},on={o}': {'model': f'{MOD}:block/tv' + ('_on' if o == 'true' else ''), 'y': y}
                             for f, y in FACING.items() for o in ('false', 'true')}}
    bs['room_mess'] = {'variants': {'': {'model': f'{MOD}:block/room_mess'}}}
    return bs


def extra_files(assets):
    with open(os.path.join(assets, 'textures', 'block', 'tv_screen_on.png.mcmeta'), 'w') as f:
        json.dump({'animation': {'frametime': 12, 'interpolate': False}}, f)


AXE = ['double_bed', 'armchair', 'sofa_gray', 'sofa_blue', 'coffee_table', 'nightstand', 'table_lamp', 'wardrobe', 'dresser', 'rug_red', 'rug_beige',
       'rug_blue', 'curtains', 'painting_sea', 'painting_city', 'wall_clock', 'desk', 'bookshelf_modern', 'tall_plant', 'luggage_rack', 'room_mess']
PICKAXE = ['tv', 'office_chair', 'toilet', 'bathtub', 'floor_lamp', 'minibar', 'mirror', 'shower', 'bathroom_sink', 'towel_rack', 'flower_vase',
           'ceiling_fan', 'air_conditioner', 'radiator', 'trash_bin', 'room_phone', 'room_sign']
