"""Item icons (16x16 pixel art) for every mod item."""
import os
from pix import Canvas, hexc, mul, mix

OUT = None
C = hexc


def save(name, cv, finish=True):
    if finish:
        cv.finish()
    cv.save(os.path.join(OUT, name + '.png'))


# ---------------------------------------------------------------- doneness tints
def doneness(base, stage):
    """stage: -1 raw, 0 undercooked, 1 cooked, 2 well done, 3 burnt"""
    targets = {
        -1: base,
        0: mix(base, C('b07a52'), 0.3),
        1: C('8a5230'),
        2: C('6b3a1f'),
        3: C('2a1a14'),
    }
    return targets[stage]


def patty(stage):
    c = Canvas()
    col = doneness(C('d9737a'), stage) if stage >= 0 else C('d9737a')
    c.ellipse(8, 9, 6.5, 4.2, col, shade=True)
    # grill marks / texture
    if stage >= 0:
        for x in range(4, 13, 3):
            c.line(x, 7, x + 2, 11, mul(col, 0.65))
    else:
        for (x, y) in ((5, 8), (8, 10), (10, 8), (7, 7), (11, 10)):
            c.px(x, y, mul(col, 1.25))
    if stage == 3:
        c.px(6, 6, C('ff7a2a')); c.px(10, 11, C('ff7a2a'))
    return c


def steak(stage):
    c = Canvas()
    col = C('c8414a') if stage < 0 else doneness(C('c8414a'), stage)
    c.poly([(2, 7), (6, 4), (12, 4), (14, 8), (12, 12), (5, 13), (2, 10)], col)
    c.ellipse(5, 9, 1.6, 1.6, C('f3e2d0') if stage < 1 else C('e8cfa5'))  # bone
    if stage >= 0:
        for x in range(6, 14, 3):
            c.line(x, 5, x + 2, 11, mul(col, 0.6))
    else:
        c.line(7, 7, 12, 9, C('f4c7c3'))
        c.line(8, 10, 11, 11, C('f4c7c3'))
    return c


def fries(stage):
    c = Canvas()
    col = {-1: C('f2e6c2'), 0: C('efe0a4'), 1: C('f2b63a'), 2: C('d48a23'), 3: C('3b2a1a')}[stage]
    # red carton
    for i, x in enumerate(range(4, 12, 2)):
        h = 3 + (i * 7) % 4
        c.rect(x, 3 + (3 - h % 4), x, 9, col)
        c.rect(x + 1, 2 + h % 3, x + 1, 9, mul(col, 0.9))
    if stage >= 0:
        c.poly([(3, 8), (13, 8), (12, 14), (4, 14)], C('2f6fc0'))
        c.rect(4, 11, 11, 11, C('f4f1e6'))
    else:
        c.rect(3, 9, 12, 10, col)
        c.rect(2, 11, 13, 12, mul(col, 0.95))
    return c


def pizza(stage):
    c = Canvas()
    crust = {-1: C('f1dfb5'), 0: C('ecd29a'), 1: C('e0a24f'), 2: C('c47c33'), 3: C('3a2618')}[stage]
    sauce = C('d23b2b') if stage < 3 else C('4a1c12')
    cheese = C('ffe27a') if stage < 2 else (C('f2b84b') if stage == 2 else C('5a4020'))
    c.ellipse(8, 8, 7, 7, crust, shade=True)
    c.ellipse(8, 8, 5.6, 5.6, sauce)
    for (x, y) in ((6, 6), (10, 7), (7, 10), (11, 10), (8, 8), (5, 9)):
        c.ellipse(x + 0.5, y + 0.5, 1.4, 1.2, cheese)
    for (x, y) in ((9, 5), (5, 11), (11, 12)):
        c.px(x, y, C('3f7a2e') if stage < 3 else C('201510'))
    return c


def soup(stage):
    c = Canvas()
    liquid = {-1: C('e07a4a'), 0: C('e48a5a'), 1: C('d6442f'), 2: C('b8331f'), 3: C('2b1a14')}[stage]
    c.ellipse(8, 9, 7, 5, C('f4f1ea'), shade=True)  # bowl
    c.ellipse(8, 7.5, 5.6, 2.6, liquid)
    c.rect(4, 12, 11, 13, C('d9d4c8'))
    if stage in (-1, 0, 2):
        for (x, y) in ((6, 7), (9, 8), (10, 6)):
            c.px(x, y, C('f0c060') if stage != 2 else C('8e2a1a'))
    if stage == 1 or stage == 2:
        c.px(7, 2, C('ffffff', 150)); c.px(8, 1, C('ffffff', 120)); c.px(10, 3, C('ffffff', 140))
    return c


def burger(cheese):
    c = Canvas()
    c.ellipse(8, 6, 7, 4, C('e3a04a'), shade=True)  # top bun
    c.rect(1, 6, 14, 7, C('e3a04a'))
    for (x, y) in ((5, 4), (8, 3), (11, 4), (6, 6), (10, 6)):
        c.px(x, y, C('fff3d6'))
    c.rect(1, 8, 14, 8, C('5fbf3a'))  # lettuce
    for x in range(1, 15, 2):
        c.px(x, 9, C('4fa82f'))
    c.rect(2, 9, 13, 9, C('d8322f'))  # tomato
    if cheese:
        c.rect(1, 10, 14, 10, C('ffcf3a'))
        c.px(3, 11, C('ffcf3a')); c.px(11, 11, C('ffcf3a'))
    c.rect(1, 11 if cheese else 10, 14, 12 if cheese else 11, C('6b3a1f'))  # patty
    c.rect(2, 13, 13, 14, C('d48f3f'))  # bottom bun
    return c


def sandwich():
    c = Canvas()
    c.poly([(2, 12), (14, 12), (8, 2)], C('f2dcae'))
    c.poly([(3, 11), (13, 11), (8, 3)], C('ffffff'))
    c.rect(3, 8, 12, 8, C('5fbf3a'))
    c.rect(4, 9, 12, 9, C('ffcf3a'))
    c.rect(4, 10, 11, 10, C('d8322f'))
    c.rect(2, 12, 14, 13, C('e2c08a'))
    c.line(8, 1, 8, 4, C('c9a36a'))  # toothpick
    c.px(8, 0, C('e04040'))
    return c


def salad():
    c = Canvas()
    c.ellipse(8, 10, 7, 4.5, C('ffffff'), shade=True)
    for (x, y, col) in ((5, 7, '5fbf3a'), (8, 6, '4fa82f'), (11, 7, '6fd04a'), (6, 9, '4fa82f'), (10, 9, '5fbf3a'), (8, 8, '7fe05a')):
        c.ellipse(x + 0.5, y + 0.5, 2.2, 1.6, C(col))
    for (x, y) in ((7, 7), (10, 8), (5, 8)):
        c.px(x, y, C('e0372f'))
    c.px(9, 6, C('b56ad8')); c.px(6, 6, C('b56ad8'))
    return c


def cup(liquid, label, straw=True, steam=False):
    c = Canvas()
    c.poly([(4, 4), (12, 4), (11, 14), (5, 14)], C('fafafa'))
    c.rect(5, 7, 11, 10, C(label))
    c.rect(4, 3, 12, 4, C(liquid) if liquid else C('dddddd'))
    if straw:
        c.line(9, 0, 10, 3, C('ff4f6a'))
    if steam:
        c.px(6, 1, C('ffffff', 170)); c.px(7, 0, C('ffffff', 120)); c.px(9, 1, C('ffffff', 160)); c.px(10, 2, C('ffffff', 130))
    return c


def tomato():
    c = Canvas()
    c.ellipse(8, 9, 6, 5.5, C('e0372f'), shade=True)
    c.px(6, 6, C('ff9a8a')); c.px(5, 7, C('ff9a8a'))
    c.poly([(6, 4), (10, 4), (8, 6)], C('3f9a2e'))
    c.px(8, 3, C('2f7a20'))
    return c


def onion():
    c = Canvas()
    c.ellipse(8, 9.5, 5.5, 5, C('b36a9e'), shade=True)
    c.poly([(7, 2), (9, 2), (10, 5), (6, 5)], C('c98ab4'))
    for y in range(6, 14, 3):
        c.line(5, y, 11, y + 1, C('9a5285'))
    c.px(8, 1, C('d9cfa0'))
    return c


def lettuce():
    c = Canvas()
    c.ellipse(8, 9, 6.5, 6, C('6fd04a'), shade=True)
    c.ellipse(8, 9.5, 4.5, 4, C('8fe36a'))
    c.line(8, 5, 8, 14, C('d9f5b0'))
    c.line(8, 9, 5, 7, C('d9f5b0')); c.line(8, 10, 11, 8, C('d9f5b0'))
    return c


def potato():
    c = Canvas()
    c.ellipse(8, 9, 6.5, 4.8, C('b98a52'), shade=True)
    for (x, y) in ((5, 8), (9, 7), (11, 10), (7, 11)):
        c.px(x, y, C('7a5530'))
    return c


def raw_beef():
    c = Canvas()
    c.poly([(2, 6), (7, 3), (13, 4), (14, 9), (10, 13), (3, 12)], C('c8414a'))
    c.line(4, 7, 11, 6, C('f4c7c3')); c.line(5, 10, 12, 9, C('f4c7c3'))
    c.line(3, 12, 10, 13, C('f1e1d4'))
    return c


def bun():
    c = Canvas()
    c.ellipse(8, 9, 7, 5, C('e3a04a'), shade=True)
    c.rect(1, 11, 14, 13, C('d48f3f'))
    for (x, y) in ((5, 6), (8, 5), (11, 6), (6, 8), (10, 8)):
        c.px(x, y, C('fff3d6'))
    return c


def cheese_block():
    c = Canvas()
    c.poly([(2, 7), (13, 4), (14, 12), (2, 13)], C('ffcf3a'))
    c.poly([(2, 7), (13, 4), (14, 6), (3, 8)], C('ffe27a'))
    for (x, y) in ((5, 10), (9, 9), (11, 11), (7, 12)):
        c.ellipse(x + 0.5, y + 0.5, 1, 1, C('e0a820'))
    return c


def bread_loaf():
    c = Canvas()
    c.ellipse(8, 8.5, 7, 5, C('c6833a'), shade=True)
    for x in (5, 8, 11):
        c.line(x, 5, x + 1, 8, C('f3d29a'))
    return c


def dough():
    c = Canvas()
    c.ellipse(8, 9, 6.5, 5, C('f3e6c8'), shade=True)
    c.px(6, 7, C('ffffff')); c.px(10, 10, C('e4d2a8'))
    return c


def bottle(liquid, label):
    c = Canvas()
    c.rect(6, 1, 9, 3, C('cfd8dc'))
    c.poly([(5, 4), (10, 4), (11, 6), (11, 14), (4, 14), (4, 6)], C(liquid))
    c.rect(4, 8, 11, 11, C(label))
    c.px(5, 5, C('ffffff', 160))
    return c


def beans():
    c = Canvas()
    c.poly([(3, 5), (12, 5), (13, 14), (2, 14)], C('a57a4a'))
    c.rect(3, 4, 12, 5, C('8a6238'))
    for (x, y) in ((6, 9), (9, 10), (7, 12), (10, 7)):
        c.ellipse(x + 0.5, y + 0.5, 1.3, 1, C('4a2a14'))
    return c


def paper_cup():
    c = cup(None, 'd8322f', straw=False)
    return c


def slices(col, inner, n=3):
    c = Canvas()
    for i in range(n):
        cx = 4.5 + i * 3.4
        c.ellipse(cx, 8 + (i % 2), 3, 3, C(col))
        c.ellipse(cx, 8 + (i % 2), 1.8, 1.8, C(inner))
    return c


def chopped(col, col2):
    c = Canvas()
    for (x, y) in ((4, 6), (8, 5), (11, 7), (5, 10), (9, 9), (12, 11), (7, 12)):
        c.rect(x, y, x + 1, y + 1, C(col))
        c.px(x, y, C(col2))
    return c


def shredded():
    c = Canvas()
    for i, (x, y) in enumerate(((3, 6), (6, 5), (9, 6), (4, 9), (8, 9), (11, 8), (6, 12), (10, 11))):
        c.line(x, y, x + 3, y + 1, C('6fd04a' if i % 2 else '4fa82f'))
    return c


def raw_fries():
    c = Canvas()
    for i, x in enumerate(range(3, 14, 2)):
        c.line(x, 4 + i % 3, x - 1, 12, C('f2e6c2'))
    return c


def cheese_slices():
    c = Canvas()
    c.rect(3, 4, 11, 11, C('ffcf3a'))
    c.rect(5, 6, 13, 13, C('ffe27a'))
    c.px(8, 9, C('e0a820')); c.px(11, 11, C('e0a820'))
    return c


def bread_slices():
    c = Canvas()
    for o in (0, 3):
        c.ellipse(7 + o, 6 + o, 4.5, 3.5, C('c6833a'))
        c.rect(3 + o, 6 + o, 11 + o, 11 + o, C('c6833a'))
        c.rect(4 + o, 6 + o, 10 + o, 10 + o, C('fbeed2'))
    return c


def raw_pizza():
    return pizza(-1)


def soup_mix():
    c = Canvas()
    c.ellipse(8, 10, 6.5, 4, C('cfcfcf'), shade=True)
    for (x, y, col) in ((6, 8, 'e0372f'), (9, 8, 'b36a9e'), (8, 10, 'e0372f'), (11, 9, 'b36a9e'), (5, 10, 'e0372f')):
        c.rect(x, y, x + 1, y, C(col))
    return c


# ---------------------------------------------------------------- tools
def knife():
    c = Canvas()
    c.poly([(2, 14), (4, 12), (12, 2), (14, 2), (13, 5), (6, 13)], C('d9e1e8'))
    c.line(4, 12, 12, 3, C('ffffff'))
    c.poly([(1, 15), (2, 12), (5, 13), (3, 16)], C('3d2b1f'))
    c.rect(2, 13, 3, 14, C('5a3d26'))
    return c


def spatula():
    c = Canvas()
    c.line(2, 14, 9, 7, C('3d2b1f')); c.line(3, 14, 9, 8, C('5a3d26'))
    c.poly([(8, 5), (12, 1), (15, 4), (11, 8)], C('cfd8dc'))
    c.line(10, 4, 12, 6, C('8a979f')); c.line(11, 3, 13, 5, C('8a979f'))
    return c


def mop():
    c = Canvas()
    c.line(12, 1, 6, 10, C('c9a36a')); c.line(13, 1, 7, 10, C('a8834a'))
    for x in range(2, 9):
        c.line(x, 11, x - 1, 15, C('e8ecef' if x % 2 else 'bfc7cc'))
    c.rect(3, 10, 8, 11, C('2f7fd0'))
    return c


def clipboard(accent):
    c = Canvas()
    c.rect(3, 2, 12, 15, C('a8743f'))
    c.rect(4, 4, 11, 14, C('f5f1e6'))
    c.rect(6, 1, 9, 3, C('9aa3aa'))
    for y in (6, 8, 10, 12):
        c.line(5, y, 10, y, C('9aa3b5'))
    c.rect(5, 6, 6, 6, C(accent)); c.rect(5, 10, 6, 10, C(accent))
    return c


def layout_tool():
    c = Canvas()
    c.rect(2, 3, 13, 13, C('2b2f36'))
    c.rect(3, 4, 12, 12, C('3a8ee6'))
    for x in range(3, 13, 3):
        c.line(x, 4, x, 12, C('8fc7ff'))
    for y in range(4, 13, 3):
        c.line(3, y, 12, y, C('8fc7ff'))
    c.rect(6, 7, 7, 8, C('ffc857'))
    c.rect(10, 10, 11, 11, C('7bd88f'))
    return c


def room_tool():
    # a brass room key with a red tag
    c = Canvas()
    c.ellipse(5, 5, 3.2, 3.2, C('c9a24a')); c.ellipse(5, 5, 1.4, 1.4, (0, 0, 0, 0))
    c.line(7, 7, 13, 13, C('c9a24a')); c.line(7, 8, 12, 13, C('8a6a24'))
    c.rect(11, 12, 12, 13, C('c9a24a')); c.rect(13, 10, 13, 11, C('c9a24a'))
    c.rect(1, 10, 6, 14, C('c62e2b')); c.rect(2, 11, 5, 13, C('e0504a')); c.px(3, 12, C('ffffff')); c.px(4, 12, C('ffffff'))
    return c


def staff_planner():
    c = clipboard('e8a33d')
    c.ellipse(9, 8, 1.6, 1.6, C('e0b088'))
    c.rect(8, 10, 10, 12, C('3a8ee6'))
    return c


def recipe_book():
    c = Canvas()
    c.rect(3, 2, 13, 14, C('b0302c'))
    c.rect(4, 3, 12, 13, C('d8473f'))
    c.rect(3, 2, 4, 14, C('7a1f1c'))
    c.ellipse(8.5, 7.5, 2.5, 2.5, C('ffffff'))
    c.rect(6, 7, 11, 8, C('ffffff'))
    c.rect(7, 9, 10, 10, C('ffffff'))
    c.rect(5, 13, 13, 14, C('f5f1e6'))
    return c


def bill(col, mark):
    c = Canvas()
    c.rect(1, 4, 14, 11, C(col))
    c.rect(2, 5, 13, 10, mul(C(col), 1.15))
    c.ellipse(7.5, 7.5, 2.4, 2.4, mul(C(col), 0.75))
    c.px(3, 6, mul(C(col), 0.7)); c.px(12, 9, mul(C(col), 0.7))
    for i, ch in enumerate(mark):
        c.px(10 + i, 6, C('ffffff')); c.px(10 + i, 7, C('ffffff'))
    return c


def bundle():
    c = Canvas()
    for i in range(4):
        c.rect(1, 9 - i * 2, 14, 12 - i * 2, mul(C('3f9a4a'), 0.85 + i * 0.08))
    c.rect(1, 3, 14, 6, C('5fbf5a'))
    c.rect(6, 2, 8, 12, C('f4e04d'))
    return c


def package_item():
    c = Canvas()
    c.rect(2, 4, 13, 14, C('c8955a'))
    c.rect(2, 4, 13, 6, C('d9a96b'))
    c.rect(7, 4, 8, 14, C('e8d6a8'))
    return c


def bank_card():
    c = Canvas()
    c.rect(1, 4, 14, 12, C('2f6fc0'))
    c.rect(1, 6, 14, 7, C('1e2b4a'))
    c.rect(3, 9, 5, 10, C('ffd166'))
    c.rect(8, 10, 12, 10, C('cfe8ff'))
    return c


def basket():
    c = Canvas()
    c.poly([(2, 7), (13, 7), (12, 14), (3, 14)], C('c62e2b'))
    for x in range(4, 12, 2):
        c.line(x, 8, x, 13, C('8a1f1c'))
    c.line(4, 7, 6, 3, C('3b3b3b')); c.line(11, 7, 9, 3, C('3b3b3b')); c.line(6, 3, 9, 3, C('3b3b3b'))
    return c


def cart():
    c = Canvas()
    c.poly([(2, 4), (14, 5), (12, 11), (4, 11)], C('b8c3cb'))
    for x in range(4, 13, 2):
        c.line(x, 5, x, 10, C('7a858c'))
    c.line(0, 2, 2, 4, C('3b3b3b'))
    c.ellipse(5, 13, 1.5, 1.5, C('1e2124')); c.ellipse(11, 13, 1.5, 1.5, C('1e2124'))
    return c


def parcel():
    c = Canvas()
    c.rect(2, 5, 13, 14, C('c8955a'))
    c.rect(2, 5, 13, 7, C('d9a96b'))
    c.rect(7, 5, 8, 14, C('e8d6a8'))
    c.rect(9, 10, 12, 12, C('ffffff'))
    c.line(9, 11, 12, 11, C('2b2f36'))
    return c


KIT_COLORS = {'restaurant': 'd8473f', 'cafe': '8a5a2e', 'fast_food': 'e8a33d', 'bakery': 'c6833a', 'grocery': '5fbf3a', 'supermarket': '2f9a5a',
              'clothing': 'b56ad8', 'electronics': '3a8ee6', 'furniture': '9a6a42', 'butcher': 'b0302c', 'gas_station': 'f25f5c', 'hotel': '2fb5a8',
              'bank': 'ffc857', 'delivery': 'c8955a', 'warehouse': '6b6b6b'}


def kit(col):
    def f():
        c = Canvas()
        c.rect(1, 3, 14, 13, C('3a6fb0'))
        c.rect(2, 4, 13, 12, C('4f86c6'))
        for x in range(2, 14, 3):
            c.line(x, 4, x, 12, C('6a9fd8'))
        c.poly([(4, 9), (8, 5), (12, 9)], C(col))
        c.rect(5, 9, 11, 12, C('f4f1e6'))
        c.rect(7, 10, 8, 12, C(col))
        c.rect(0, 2, 2, 14, C('d9c9a0'))
        return c
    return f


ITEMS = {
    **{'kit_' + k: kit(v) for k, v in KIT_COLORS.items()},
    'bank_card': bank_card, 'shopping_basket': basket, 'shopping_cart': cart, 'parcel': parcel,
    'kitchen_knife': knife, 'spatula': spatula, 'mop': mop,
    'business_planner': lambda: clipboard('3a8ee6'), 'staff_planner': staff_planner, 'layout_tool': layout_tool, 'room_tool': room_tool,
    'recipe_book': recipe_book,
    'cash_1': lambda: bill('8fb98a', '1'), 'cash_5': lambda: bill('7fb0c9', '5'), 'cash_10': lambda: bill('c9a36a', '1'),
    'cash_20': lambda: bill('5f9a5a', '2'), 'cash_50': lambda: bill('c97a6a', '5'), 'cash_100': lambda: bill('3f8a5a', '1'),
    'cash_bundle': bundle,
    'tomato': tomato, 'onion': onion, 'lettuce': lettuce, 'potato': potato, 'raw_beef': raw_beef, 'burger_bun': bun,
    'cheese_block': cheese_block, 'bread_loaf': bread_loaf, 'pizza_dough': dough,
    'cola_syrup': lambda: bottle('3b1a10', 'd8322f'), 'coffee_beans': beans, 'paper_cup': paper_cup,
    'tomato_slices': lambda: slices('e0372f', 'ff8a6a'), 'chopped_onion': lambda: chopped('c98ab4', 'f0e0ef'),
    'shredded_lettuce': shredded, 'raw_fries': raw_fries, 'raw_patty': lambda: patty(-1), 'cheese_slices': cheese_slices,
    'bread_slices': bread_slices, 'raw_pizza': raw_pizza, 'soup_mix': soup_mix,
    'undercooked_patty': lambda: patty(0), 'cooked_patty': lambda: patty(1), 'well_done_patty': lambda: patty(2), 'burnt_patty': lambda: patty(3),
    'rare_steak': lambda: steak(0), 'medium_steak': lambda: steak(1), 'well_done_steak': lambda: steak(2), 'burnt_steak': lambda: steak(3),
    'soggy_fries': lambda: fries(0), 'fries': lambda: fries(1), 'crispy_fries': lambda: fries(2), 'burnt_fries': lambda: fries(3),
    'doughy_pizza': lambda: pizza(0), 'pizza': lambda: pizza(1), 'crispy_pizza': lambda: pizza(2), 'burnt_pizza': lambda: pizza(3),
    'lukewarm_soup': lambda: soup(0), 'tomato_soup': lambda: soup(1), 'thick_soup': lambda: soup(2), 'burnt_soup': lambda: soup(3),
    'burger': lambda: burger(False), 'cheeseburger': lambda: burger(True), 'club_sandwich': sandwich, 'garden_salad': salad,
    'cola': lambda: cup('3b1a10', 'd8322f'), 'coffee': lambda: cup('5a3420', '6b3a1f', straw=False, steam=True),
}


def generate(out):
    global OUT
    OUT = out
    os.makedirs(out, exist_ok=True)
    for name, fn in ITEMS.items():
        save(name, fn())
    return list(ITEMS)
