"""Block textures, block models (multi-element JSON), blockstates and item models."""
import os, json, random
from pix import Canvas, hexc, mul, mix, tex

C = hexc
MOD = 'rpbusiness'


def T(name):
    return f'{MOD}:block/{name}'


# ================================================================ textures
def brushed(base, seed):
    c = tex(16, 16, C(base), seed, 0.03)
    r = random.Random(seed)
    for y in range(16):
        f = 1 + (r.random() - 0.5) * 0.12
        for x in range(16):
            c.px(x, y, mul(c.get(x, y), f))
    return c


def textures():
    t = {}
    t['steel'] = brushed('c3cbd1', 1)
    s = brushed('c3cbd1', 2)
    s.rect(0, 0, 15, 0, C('e4eaee')); s.rect(0, 15, 15, 15, C('8f9aa2'))
    t['steel_edge'] = s
    t['steel_dark'] = brushed('5d666e', 3)
    g = tex(16, 16, C('1e2124'), 4, 0.05)
    for x in range(1, 16, 3):
        g.rect(x, 0, x, 15, C('4a4f55'))
    t['grate'] = g
    m = tex(16, 16, C('eef0f2'), 5, 0.02)
    r = random.Random(5)
    x, y = 0, r.randint(0, 15)
    for i in range(30):
        m.px(x, y, C('b9c0c7')); x += 1; y = max(0, min(15, y + r.choice((-1, 0, 1))))
        if x > 15: x = 0; y = r.randint(0, 15)
    t['marble'] = m
    w = tex(16, 16, C('6b4a32'), 6, 0.06)
    for yy in (0, 5, 10, 15):
        w.rect(0, yy, 15, yy, C('4e3524'))
    t['walnut'] = w
    d = tex(16, 16, C('6b4a32'), 7, 0.05)
    d.rect(0, 0, 15, 15, C('5a3d29'))
    d.rect(1, 1, 14, 14, C('6b4a32'))
    d.rect(2, 2, 13, 13, C('7a563b'))
    d.rect(12, 6, 12, 9, C('d7dde1'))
    t['cabinet_front'] = d
    dr = tex(16, 16, C('6b4a32'), 8, 0.05)
    dr.rect(0, 0, 15, 15, C('5a3d29')); dr.rect(1, 1, 14, 6, C('7a563b')); dr.rect(1, 9, 14, 14, C('7a563b'))
    dr.rect(6, 3, 9, 3, C('d7dde1')); dr.rect(6, 11, 9, 11, C('d7dde1'))
    t['drawer_front'] = dr
    lw = tex(16, 16, C('d8b47a'), 9, 0.05)
    for yy in range(0, 16, 4):
        lw.rect(0, yy, 15, yy, C('c39a5f'))
    t['oak_light'] = lw
    b = tex(16, 16, C('e3c48e'), 10, 0.04)
    r = random.Random(10)
    for i in range(6):
        yy = r.randint(0, 15); b.rect(0, yy, 15, yy, C('d2ad74'))
    t['board'] = b
    tl = tex(16, 16, C('f4f6f7'), 11, 0.02)
    for k in (0, 8):
        tl.rect(0, k, 15, k, C('c8ced3')); tl.rect(k, 0, k, 15, C('c8ced3'))
    t['kitchen_tiles'] = tl
    ch = Canvas()
    for yy in range(16):
        for xx in range(16):
            ch.px(xx, yy, C('f1f1f1') if ((xx // 4) + (yy // 4)) % 2 == 0 else C('262626'))
    ch.noise(0.03, 12)
    t['checkered_floor'] = ch
    fr = tex(16, 16, C('a8302e'), 13, 0.05)
    for yy in range(0, 16, 2):
        for xx in range((yy // 2) % 2, 16, 2):
            fr.px(xx, yy, mul(fr.get(xx, yy), 0.92))
    t['fabric_red'] = fr
    gl = Canvas()
    gl.rect(0, 0, 15, 15, C('8f9aa2'))
    gl.rect(2, 2, 13, 11, C('151a1f', 255))
    gl.rect(3, 3, 12, 10, C('1f2a30'))
    gl.line(4, 4, 7, 4, C('ffffff', 90))
    gl.rect(3, 13, 12, 13, C('2b2f33'))
    t['oven_front'] = gl
    gll = Canvas()
    gll.rect(0, 0, 15, 15, C('8f9aa2'))
    gll.rect(2, 2, 13, 11, C('ff8a2a'))
    gll.rect(3, 3, 12, 10, C('ffb84a'))
    gll.rect(3, 13, 12, 13, C('2b2f33'))
    t['oven_front_lit'] = gll
    kn = brushed('c3cbd1', 14)
    for xx in (3, 7, 11):
        kn.rect(xx, 4, xx + 1, 5, C('1e2124'))
    kn.rect(2, 9, 13, 13, C('9aa3aa'))
    t['control_panel'] = kn
    oil = tex(16, 16, C('e0a42a'), 15, 0.08)
    oil.rect(0, 0, 15, 0, C('8f9aa2')); oil.rect(0, 15, 15, 15, C('8f9aa2'))
    t['oil'] = oil
    sc = tex(16, 16, C('1c2733'), 16, 0.02)
    sc.rect(1, 1, 14, 14, C('2b7fd0'))
    for yy in (3, 6, 9, 12):
        sc.rect(3, yy, 9 + (yy % 4), yy, C('cfe8ff'))
    t['screen'] = sc
    sg = tex(16, 16, C('1c2733'), 17, 0.02)
    sg.rect(1, 1, 14, 14, C('2f9a5a'))
    sg.rect(3, 3, 12, 5, C('d8ffe5')); sg.rect(3, 8, 7, 12, C('ffffff')); sg.rect(9, 8, 12, 12, C('ffd166'))
    t['screen_green'] = sg
    cb = tex(16, 16, C('2a3a2e'), 18, 0.05)
    cb.rect(0, 0, 15, 15, C('6b4a32')); cb.rect(1, 1, 14, 14, C('243328'))
    cb.rect(3, 2, 12, 3, C('f4f1e6'))
    for yy in (6, 9, 12):
        cb.rect(2, yy, 8, yy, C('d9e3d8')); cb.rect(11, yy, 13, yy, C('ffe27a'))
    t['chalkboard'] = cb
    cr = tex(16, 16, C('b58a55'), 19, 0.07)
    cr.rect(0, 0, 15, 1, C('8a643a')); cr.rect(0, 14, 15, 15, C('8a643a')); cr.rect(0, 0, 1, 15, C('8a643a')); cr.rect(14, 0, 15, 15, C('8a643a'))
    cr.line(1, 1, 14, 14, C('8a643a'))
    t['crate'] = cr
    cbx = tex(16, 16, C('c8955a'), 20, 0.04)
    cbx.rect(7, 0, 8, 15, C('e8d6a8'))
    t['cardboard'] = cbx
    lv = Canvas()
    r = random.Random(21)
    for i in range(60):
        xx, yy = r.randint(1, 14), r.randint(0, 14)
        lv.px(xx, yy, C(r.choice(['3f9a3a', '4fb04a', '2f7a2e', '5fc05a'])))
    t['leaves'] = lv
    t['terracotta'] = tex(16, 16, C('b8633a'), 22, 0.05)
    t['lamp_shade'] = tex(16, 16, C('2b2f36'), 23, 0.03)
    t['bulb'] = tex(16, 16, C('fff2b0'), 24, 0.02)
    tt = tex(16, 16, C('8b5e3c'), 25, 0.05)
    for yy in (0, 8):
        tt.rect(0, yy, 15, yy, C('734c30'))
    t['table_top'] = tt
    dt = Canvas()
    dt.ellipse(5, 5, 3.5, 3.5, C('f4f6f7')); dt.ellipse(5, 5, 2, 2, C('d9dde0'))
    dt.ellipse(11, 11, 3, 3, C('f4f6f7')); dt.ellipse(11, 11, 1.6, 1.6, C('c9a36a'))
    for (xx, yy) in ((9, 4), (3, 11), (12, 6), (6, 13)):
        dt.px(xx, yy, C('c9a36a'))
    dt.rect(13, 2, 13, 5, C('cfd8dc'))
    t['table_dirty'] = dt
    t['chair_wood'] = tex(16, 16, C('9a6a42'), 26, 0.05)
    t['black'] = tex(16, 16, C('1e2124'), 27, 0.03)
    t['red_panel'] = tex(16, 16, C('c62e2b'), 28, 0.04)
    dm = Canvas()
    dm.rect(0, 0, 15, 15, C('c62e2b'))
    dm.rect(2, 1, 13, 5, C('f4f1e6')); dm.rect(4, 2, 11, 4, C('3b1a10'))
    dm.rect(3, 7, 5, 9, C('1e2124')); dm.rect(10, 7, 12, 9, C('1e2124'))
    dm.rect(2, 13, 13, 14, C('8f9aa2'))
    t['drink_front'] = dm
    rg = Canvas()
    rg.rect(0, 0, 15, 15, C('d9d4c8')); rg.rect(1, 1, 14, 14, C('e8e3d6'))
    for yy in (3, 6, 9):
        for xx in (3, 6, 9, 12):
            rg.rect(xx - 1, yy, xx, yy + 1, C('5d666e'))
    t['register_keys'] = rg
    t['beige'] = tex(16, 16, C('d9d4c8'), 29, 0.03)
    pot = tex(16, 16, C('2b2f33'), 30, 0.03)
    t['pot_inside'] = pot
    wh = tex(16, 16, C('eef1f3'), 31, 0.02)
    t['white'] = wh
    bl = tex(16, 16, C('2f6fc0'), 32, 0.04)
    t['blue_panel'] = bl
    t['green_panel'] = tex(16, 16, C('2f9a5a'), 33, 0.04)
    sh = tex(16, 16, C('7a563b'), 34, 0.05)
    r = random.Random(34)
    for yy in (1, 6, 11):
        sh.rect(0, yy + 4, 15, yy + 4, C('4e3524'))
        for xx in range(1, 15, 3):
            col = C(r.choice(['e0372f', 'ffcf3a', '6fd04a', 'f4f1e6', 'b36a9e', 'c6833a']))
            hgt = r.randint(2, 3)
            sh.rect(xx, yy + 4 - hgt, xx + 1, yy + 3, col)
    t['shelf_front'] = sh
    return t


# ================================================================ model helpers
def face(texkey, cull=None, tint=None):
    f = {'texture': '#' + texkey}
    if cull:
        f['cullface'] = cull
    return f


def el(fr, to, faces, rot=None):
    """faces: dict face->texkey or a single texkey for all; 'skip' list of faces."""
    if isinstance(faces, str):
        faces = {k: faces for k in ('north', 'south', 'east', 'west', 'up', 'down')}
    e = {'from': list(fr), 'to': list(to), 'faces': {k: face(v) for k, v in faces.items() if v}}
    if rot:
        e['rotation'] = rot
    return e


def sides(main, top=None, bottom=None, front=None, back=None):
    return {'north': front or main, 'south': back or main, 'east': main, 'west': main, 'up': top or main, 'down': bottom or main}


def model(textures, elements, parent='minecraft:block/block', extra=None):
    m = {'parent': parent, 'textures': textures, 'elements': elements}
    if extra:
        m.update(extra)
    return m


def counter_elements(front='front'):
    return [el((0, 0, 0), (16, 14, 16), sides('side', top='side', front=front)),
            el((-0.01, 14, -0.5), (16.01, 16, 16), sides('top'))]


MODELS = {}


def build_models():
    M = MODELS
    M['kitchen_counter'] = model({'particle': T('marble'), 'side': T('walnut'), 'front': T('cabinet_front'), 'top': T('marble')}, counter_elements())
    M['counter_cabinet'] = model({'particle': T('walnut'), 'side': T('walnut'), 'front': T('drawer_front'), 'top': T('oak_light')}, counter_elements())
    M['cutting_board'] = model({'particle': T('board'), 'b': T('board'), 'h': T('walnut')}, [
        el((2, 0, 3), (14, 1.5, 13), 'b'), el((14, 0.3, 7), (15.5, 1.2, 9), 'h')])
    knobs = [el((x, 9, -0.5), (x + 1.5, 10.5, 0), 'k') for x in (3, 7.25, 11.5)]
    M['grill'] = model({'particle': T('steel'), 's': T('steel_edge'), 'f': T('control_panel'), 'g': T('grate'), 'k': T('black'), 'd': T('steel_dark')}, [
        el((0, 0, 0), (16, 13, 16), sides('s', top='d', front='f')),
        el((0.5, 13, 0.5), (15.5, 14, 15.5), sides('d', top='g')),
        el((0, 13, 0), (16, 15, 0.5), 's'), el((0, 13, 15.5), (16, 15, 16), 's'),
        el((0, 13, 0.5), (0.5, 15, 15.5), 's'), el((15.5, 13, 0.5), (16, 15, 15.5), 's')] + knobs)
    M['grill_lit'] = M['grill']
    M['fryer'] = model({'particle': T('steel'), 's': T('steel_edge'), 'f': T('control_panel'), 'o': T('oil'), 'k': T('black'), 'd': T('steel_dark')}, [
        el((1, 0, 1), (15, 12, 15), sides('s', top='o', front='f')),
        el((1, 12, 1), (15, 15, 2), 's'), el((1, 12, 14), (15, 15, 15), 's'),
        el((1, 12, 2), (2, 15, 14), 's'), el((14, 12, 2), (15, 15, 14), 's'),
        el((4, 12, 4), (12, 13, 12), 'd'),
        el((7, 13, 2), (9, 16, 3), 'k')])
    M['fryer_lit'] = M['fryer']
    M['oven'] = model({'particle': T('steel'), 's': T('steel_edge'), 'f': T('oven_front'), 't': T('control_panel'), 'k': T('black')}, [
        el((0, 0, 0), (16, 13, 16), sides('s', front='f')),
        el((0, 13, 0), (16, 16, 16), sides('s', front='t')),
        el((3, 11.5, -1), (13, 12.5, 0), 'k')])
    M['oven_lit'] = model({'particle': T('steel'), 's': T('steel_edge'), 'f': T('oven_front_lit'), 't': T('control_panel'), 'k': T('black')},
                          M['oven']['elements'])
    M['cooking_pot'] = model({'particle': T('steel'), 's': T('steel_edge'), 'i': T('pot_inside'), 'k': T('black')}, [
        el((3, 0, 3), (13, 1, 13), 's'),
        el((3, 1, 3), (13, 9, 4), 's'), el((3, 1, 12), (13, 9, 13), 's'),
        el((3, 1, 4), (4, 9, 12), 's'), el((12, 1, 4), (13, 9, 12), 's'),
        el((4, 1, 4), (12, 2, 12), 'i'),
        el((1, 7, 7), (3, 8, 9), 'k'), el((13, 7, 7), (15, 8, 9), 'k')])
    M['cooking_pot_lit'] = M['cooking_pot']
    M['prep_station'] = model({'particle': T('steel'), 's': T('steel_edge'), 'f': T('drawer_front'), 'b': T('white'), 'd': T('steel_dark')}, [
        el((0, 0, 0), (16, 14, 16), sides('s', front='f')),
        el((0, 14, 0), (16, 15, 16), sides('d')),
        el((1, 15, 1), (15, 15.5, 15), 'b')])
    M['drink_machine'] = model({'particle': T('red_panel'), 'r': T('red_panel'), 'f': T('drink_front'), 'k': T('black'), 'd': T('steel_dark')}, [
        el((2, 0, 4), (14, 16, 14), sides('r', front='f')),
        el((2, 0, 2), (14, 1, 4), 'd'),
        el((4, 9, 3), (6, 10, 4), 'k'), el((10, 9, 3), (12, 10, 4), 'k')])
    M['pantry_shelf'] = model({'particle': T('walnut'), 's': T('walnut'), 'f': T('shelf_front')}, [el((0, 0, 0), (16, 16, 16), sides('s', front='f'))])
    M['serving_counter'] = model({'particle': T('steel'), 's': T('steel_edge'), 'f': T('cabinet_front'), 't': T('steel')}, [
        el((0, 0, 0), (16, 15, 16), sides('s', front='f')), el((-0.5, 15, -0.5), (16.5, 16, 16.5), 't')])
    table = [el((0, 13, 0), (16, 16, 16), sides('t')), el((6, 0, 6), (10, 13, 10), 'l'), el((3, 0, 3), (13, 1, 13), 'l')]
    M['dining_table'] = model({'particle': T('table_top'), 't': T('table_top'), 'l': T('walnut')}, table)
    M['dining_table_dirty'] = model({'particle': T('table_top'), 't': T('table_top'), 'l': T('walnut'), 'x': T('table_dirty')},
                                    table + [el((0, 16.01, 0), (16, 16.02, 16), {'up': 'x'})], extra={'render_type': 'minecraft:cutout'})
    legs = [el((x, 0, z), (x + 1.5, 8, z + 1.5), 'w') for x in (2.5, 12) for z in (2.5, 12)]
    M['dining_chair'] = model({'particle': T('chair_wood'), 'w': T('chair_wood'), 'c': T('fabric_red')}, legs + [
        el((2, 8, 2), (14, 9.5, 14), sides('w', top='c')),
        el((2.5, 9.5, 12), (4, 20, 13.5), 'w'), el((12, 9.5, 12), (13.5, 20, 13.5), 'w'),
        el((4, 14, 12.5), (12, 19, 13.5), 'w')])
    M['bar_stool'] = model({'particle': T('steel'), 's': T('steel_dark'), 'c': T('fabric_red')}, [
        el((4, 0, 4), (12, 1, 12), 's'), el((7, 1, 7), (9, 10, 9), 's'), el((5, 5, 5), (11, 5.5, 11), 's'),
        el((3, 10, 3), (13, 12, 13), sides('c'))])
    M['sofa'] = model({'particle': T('fabric_red'), 'c': T('fabric_red'), 'w': T('walnut')}, [
        el((0, 0, 1), (16, 2, 15), 'w'),
        el((0, 2, 1), (16, 8, 15), sides('c')),
        el((0, 8, 11), (16, 16, 15), sides('c')),
        el((0, 8, 1), (2, 12, 11), sides('c')), el((14, 8, 1), (16, 12, 11), sides('c'))])
    M['pendant_lamp'] = model({'particle': T('lamp_shade'), 's': T('lamp_shade'), 'b': T('bulb'), 'k': T('black')}, [
        el((7.5, 11, 7.5), (8.5, 16, 8.5), 'k'),
        el((4, 6, 4), (12, 11, 12), {'north': 's', 'south': 's', 'east': 's', 'west': 's', 'up': 's', 'down': 'b'}),
        el((6, 5, 6), (10, 6, 10), 'b')])
    M['menu_board'] = model({'particle': T('chalkboard'), 'c': T('chalkboard'), 'w': T('walnut')}, [
        el((1, 2, 7), (15, 16, 9), {'north': 'c', 'south': 'c', 'east': 'w', 'west': 'w', 'up': 'w', 'down': 'w'}),
        el((1.5, 0, 7.5), (2.5, 2, 8.5), 'w'), el((13.5, 0, 7.5), (14.5, 2, 8.5), 'w')])
    M['potted_plant'] = model({'particle': T('terracotta'), 'p': T('terracotta'), 'l': T('leaves'), 'd': T('walnut')}, [
        el((4, 0, 4), (12, 6, 12), sides('p', top='d')),
        el((1, 5, 8), (15, 16, 8), {'north': 'l', 'south': 'l'}, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}),
        el((8, 5, 1), (8, 16, 15), {'east': 'l', 'west': 'l'}, rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45})],
        extra={'render_type': 'minecraft:cutout'})
    M['checkered_floor'] = {'parent': 'minecraft:block/cube_all', 'textures': {'all': T('checkered_floor')}}
    M['kitchen_tiles'] = {'parent': 'minecraft:block/cube_all', 'textures': {'all': T('kitchen_tiles')}}
    M['cash_register'] = model({'particle': T('beige'), 'b': T('beige'), 'k': T('register_keys'), 'sc': T('screen_green'), 'd': T('steel_dark')}, [
        el((2, 0, 3), (14, 3, 14), sides('d')),
        el((2.5, 3, 3.5), (13.5, 6, 13.5), sides('b', top='k')),
        el((5, 6, 10), (11, 11, 11), {'north': 'sc', 'south': 'b', 'east': 'b', 'west': 'b', 'up': 'b', 'down': 'b'}),
        el((7, 6, 11), (9, 8, 12), 'b')])
    M['payment_terminal'] = model({'particle': T('steel_dark'), 'd': T('steel_dark'), 'sc': T('screen'), 'k': T('black')}, [
        el((4, 0, 5), (12, 1, 11), 'd'), el((7, 1, 7), (9, 10, 9), 'd'),
        el((4, 10, 5), (12, 16, 9), {'north': 'sc', 'south': 'd', 'east': 'd', 'west': 'd', 'up': 'd', 'down': 'd'}, rot={'origin': [8, 10, 7], 'axis': 'x', 'angle': -22.5}),
        el((5, 9, 4), (11, 10, 5), 'k')])
    M['business_computer'] = model({'particle': T('black'), 'k': T('black'), 'sc': T('screen'), 'w': T('white')}, [
        el((6, 0, 9), (10, 1, 12), 'k'), el((7.5, 1, 10), (8.5, 4, 11), 'k'),
        el((1, 4, 9.5), (15, 12, 10.5), {'north': 'sc', 'south': 'k', 'east': 'k', 'west': 'k', 'up': 'k', 'down': 'k'}),
        el((2, 0, 3), (14, 0.8, 7), sides('k', top='w'))])
    M['supplier_terminal'] = model({'particle': T('green_panel'), 'g': T('green_panel'), 'sc': T('screen_green'), 'd': T('steel_dark')}, [
        el((3, 0, 4), (13, 12, 12), sides('g')),
        el((3, 12, 4), (13, 16, 6), {'north': 'sc', 'south': 'g', 'east': 'g', 'west': 'g', 'up': 'g', 'down': 'g'}),
        el((4, 6, 3.5), (12, 8, 4), 'd')])
    M['delivery_crate'] = model({'particle': T('crate'), 'c': T('crate')}, [el((1, 0, 1), (15, 14, 15), 'c')])
    M['package'] = model({'particle': T('cardboard'), 'c': T('cardboard')}, [el((3, 0, 3), (13, 8, 13), 'c')])
    # GeckoLib-rendered blocks: blockstate model only provides the break particle
    M['fridge_particle'] = {'textures': {'particle': T('steel')}}
    M['freezer_particle'] = {'textures': {'particle': T('steel')}}
    # static item models for the animated blocks
    M['fridge_item'] = model({'particle': T('steel'), 's': T('steel_edge'), 'd': T('steel_dark'), 'h': T('black')}, [
        el((1, 0, 1), (15, 16, 15), sides('s')), el((12, 4, 0.5), (13, 12, 1), 'h'), el((1, 9.8, 0.8), (15, 10.2, 1), 'd')],
        extra={'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}}})
    M['freezer_item'] = model({'particle': T('steel'), 's': T('steel_edge'), 'w': T('white'), 'h': T('black')}, [
        el((0, 0, 1), (16, 12, 15), sides('w')), el((0, 12, 1), (16, 14, 15), sides('s')), el((6, 10, 0.5), (10, 11, 1), 'h')])
    return M


FACING = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def blockstates():
    bs = {}
    def facing(name, model_name=None, extra=''):
        mn = model_name or name
        return {'variants': {f'facing={f}{extra}': {'model': f'{MOD}:block/{mn}', 'y': y} for f, y in FACING.items()}}
    for n in ['kitchen_counter', 'counter_cabinet', 'cutting_board', 'prep_station', 'drink_machine', 'pantry_shelf', 'serving_counter',
              'dining_chair', 'bar_stool', 'sofa', 'pendant_lamp', 'menu_board', 'potted_plant', 'cash_register', 'payment_terminal',
              'business_computer', 'supplier_terminal', 'delivery_crate', 'package']:
        bs[n] = facing(n)
    for n in ['grill', 'fryer', 'oven', 'cooking_pot']:
        v = {}
        for f, y in FACING.items():
            v[f'facing={f},lit=false'] = {'model': f'{MOD}:block/{n}', 'y': y}
            v[f'facing={f},lit=true'] = {'model': f'{MOD}:block/{n}_lit', 'y': y}
        bs[n] = {'variants': v}
    bs['dining_table'] = {'variants': {'dirty=false': {'model': f'{MOD}:block/dining_table'}, 'dirty=true': {'model': f'{MOD}:block/dining_table_dirty'}}}
    bs['checkered_floor'] = {'variants': {'': {'model': f'{MOD}:block/checkered_floor'}}}
    bs['kitchen_tiles'] = {'variants': {'': {'model': f'{MOD}:block/kitchen_tiles'}}}
    v = {}
    for f in FACING:
        for half in ('lower', 'upper'):
            v[f'facing={f},half={half}'] = {'model': f'{MOD}:block/fridge_particle'}
    bs['fridge'] = {'variants': v}
    bs['freezer'] = {'variants': {f'facing={f}': {'model': f'{MOD}:block/freezer_particle'} for f in FACING}}
    return bs


def generate(assets):
    tdir = os.path.join(assets, 'textures', 'block')
    os.makedirs(tdir, exist_ok=True)
    for name, cv in textures().items():
        cv.save(os.path.join(tdir, name + '.png'))
    mdir = os.path.join(assets, 'models', 'block')
    os.makedirs(mdir, exist_ok=True)
    for name, m in build_models().items():
        with open(os.path.join(mdir, name + '.json'), 'w') as f:
            json.dump(m, f, indent=1)
    bdir = os.path.join(assets, 'blockstates')
    os.makedirs(bdir, exist_ok=True)
    states = blockstates()
    for name, s in states.items():
        with open(os.path.join(bdir, name + '.json'), 'w') as f:
            json.dump(s, f, indent=1)
    idir = os.path.join(assets, 'models', 'item')
    os.makedirs(idir, exist_ok=True)
    for name in states:
        parent = f'{MOD}:block/{name}'
        if name in ('fridge', 'freezer'):
            parent = f'{MOD}:block/{name}_item'
        with open(os.path.join(idir, name + '.json'), 'w') as f:
            json.dump({'parent': parent}, f)
    return list(states)
