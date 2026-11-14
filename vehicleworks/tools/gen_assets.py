"""Generates all VehicleWorks assets and data: textures, models, blockstates, lang, recipes, loot, tags."""
import json, os, sys, math
sys.path.insert(0, os.path.dirname(__file__))
from textures import *

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'vehicleworks')
D = os.path.join(ROOT, 'data', 'vehicleworks')
MOD = 'vehicleworks'

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)

def tex(name, im):
    p = os.path.join(A, 'textures', 'block', name + '.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)

def itex(name, im):
    p = os.path.join(A, 'textures', 'item', name + '.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)

def model(name, obj):
    w(os.path.join(A, 'models', 'block', name + '.json'), obj)

def blockstate(name, obj):
    w(os.path.join(A, 'blockstates', name + '.json'), obj)

def item_block(name, parent=None):
    w(os.path.join(A, 'models', 'item', name + '.json'), {"parent": parent or f"{MOD}:block/{name}"})

def item_gen(name):
    w(os.path.join(A, 'models', 'item', name + '.json'), {"parent": "minecraft:item/handheld" if name == 'engineering_wrench' else "minecraft:item/generated",
                                                          "textures": {"layer0": f"{MOD}:item/{name}"}})

def b(n): return f"{MOD}:block/{n}"

# ------------------------------------------------------------------ textures
GRAY = '#8f989f'
def T():
    t = {}
    # core
    im = base('#2c3442', 6, 1); border(im); rivets(im)
    d = ImageDraw.Draw(im); d.polygon([(16, 5), (24, 20), (16, 16), (8, 20)], fill=rgb('#38d3ff') + (255,)); t['core_top'] = im
    im = base('#2c3442', 6, 2); border(im); grille(im, 6, 8, 25, 24); text_dots(im, [(8, 26), (10, 26), (12, 26)], '#38d3ff'); t['core_side'] = im
    im = base('#2c3442', 6, 3); border(im); disc(im, 16, 16, 9, '#1a2029'); ring(im, 16, 16, 8, 9, '#38d3ff'); disc(im, 16, 16, 3, '#38d3ff'); t['core_front'] = im
    # seats
    t['seat'] = border(base('#6b3d22', 10, 4)); t['seat_frame'] = metal('#4a4f55', 5)
    im = base('#284a8a', 8, 6); border(im); t['seat_passenger'] = im
    # engines
    t['engine_side'] = grille(metal('#5d646b', 7), 4, 10, 27, 22)
    im = metal('#5d646b', 8); fan(im, 16, 16, 12, 8, '#9aa3ab'); ring(im, 16, 16, 12, 13.5, '#2b2f34'); t['jet_front'] = im
    im = metal('#5d646b', 9); disc(im, 16, 16, 11, '#1b1d20'); ring(im, 16, 16, 8, 11, '#c87533'); disc(im, 16, 16, 4, '#ff9a3c'); t['jet_back'] = im
    im = metal('#6f7780', 10); fan(im, 16, 16, 13, 14, '#c9d0d6'); ring(im, 16, 16, 13, 14.5, '#2b2f34'); t['turbofan_front'] = im
    im = metal('#6f7780', 11); disc(im, 16, 16, 12, '#1b1d20'); ring(im, 16, 16, 9, 12, '#7d858c'); t['turbofan_back'] = im
    im = metal('#7a6f5c', 12); disc(im, 16, 16, 9, '#2b2f34'); grille(im, 8, 9, 24, 23, 0.7); t['turboprop_front'] = im
    im = metal('#7a6f5c', 13); disc(im, 16, 16, 6, '#1b1d20'); t['turboprop_back'] = im
    im = metal('#4b4f54', 14)
    for i in range(4): d = ImageDraw.Draw(im); d.rectangle([3 + i * 7, 6, 7 + i * 7, 25], fill=shade(rgb('#9aa0a6'), 1 - 0.05 * i) + (255,)); grille(im, 3 + i * 7, 7, 7 + i * 7, 25, 0.6)
    t['piston_side'] = im
    im = metal('#4b4f54', 15); disc(im, 16, 16, 7, '#202326'); ring(im, 16, 16, 7, 9, '#9aa0a6'); t['piston_front'] = im
    im = metal('#3f6a5a', 16); 
    for x in range(4, 28, 3): ImageDraw.Draw(im).line([(x, 6), (x, 25)], fill=rgb('#c8803a') + (255,))
    t['electric_side'] = im
    im = metal('#3f6a5a', 17); disc(im, 16, 16, 7, '#1a1d1f'); ring(im, 16, 16, 7, 8.5, '#c8803a'); t['electric_front'] = im
    t['propeller'] = wood('#8a5a2b', 18); t['prop_hub'] = metal('#3a3f44', 19)
    t['marine_propeller'] = border(base('#b58a3c', 8, 20)); t['rotor_hub'] = metal('#33373c', 21)
    im = base('#2d3136', 4, 22); stripe(im, '#e0b020', 26, 31); t['rotor_blade'] = im
    t['strut'] = metal('#a7afb6', 23)
    # systems
    im = metal('#59606a', 24); d = ImageDraw.Draw(im); d.rectangle([9, 7, 23, 24], fill=rgb('#2a2d31') + (255,)); d.polygon([(17, 9), (12, 17), (16, 17), (14, 23), (20, 14), (16, 14)], fill=rgb('#ffd23c') + (255,)); t['starter'] = im
    im = base('#b84a26', 8, 25); border(im); hazard(im, 26, 29); gauge(im, 16, 12, 6, 2.2); t['fuel_tank'] = im
    im = base('#b84a26', 8, 26); border(im); disc(im, 16, 16, 7, '#7e2f17'); disc(im, 16, 16, 3, '#2a1a12'); t['fuel_tank_top'] = im
    im = metal('#6b5a40', 27); d = ImageDraw.Draw(im); d.line([(0, 12), (31, 12)], fill=rgb('#c8803a') + (255,), width=3); d.line([(0, 20), (31, 20)], fill=rgb('#c8803a') + (255,), width=3); disc(im, 16, 16, 5, '#30343a'); t['fuel_pump'] = im
    im = metal('#59606a', 28); hazard(im, 2, 5); d = ImageDraw.Draw(im); d.line([(6, 10), (6, 28)], fill=rgb('#ffd23c') + (255,), width=3); d.line([(26, 10), (26, 28)], fill=rgb('#ffd23c') + (255,), width=3); disc(im, 16, 18, 6, '#30343a'); t['hydraulic'] = im
    # wings
    def wingtex(c, accent=None, seed=30):
        im = base(c, 5, seed)
        px = im.load()
        for y in range(S):
            for x in range(S):
                if x % 8 == 0: px[x, y] = shade(px[x, y], 0.85)
                if y in (0, S - 1): px[x, y] = shade(px[x, y], 0.75)
        rivets(im, [(x, 2) for x in range(2, 32, 6)] + [(x, 29) for x in range(2, 32, 6)])
        if accent: stripe(im, accent, 26, 30)
        return im
    t['wing'] = wingtex('#c3c9ce', seed=31); t['straight_wing'] = wingtex('#e8ecef', seed=32); t['swept_wing'] = wingtex('#aab3ba', seed=33)
    t['delta_wing'] = wingtex('#7d868e', seed=34); t['variable_sweep_wing'] = wingtex('#8c9ca8', '#5a6a76', seed=35)
    t['aileron'] = wingtex('#c3c9ce', '#2f6fd6', 36); t['elevator'] = wingtex('#c3c9ce', '#2fae55', 37); t['rudder'] = wingtex('#c3c9ce', '#d6402f', 38)
    t['flap'] = wingtex('#c3c9ce', '#e2b422', 39); t['spoiler'] = wingtex('#c3c9ce', '#e2762a', 40)
    t['wing_edge'] = base('#9aa1a7', 4, 41)
    # ground
    t['tire'] = tire(42); t['rim'] = rim()
    # marine / lift
    im = base('#283b5a', 8, 43); border(im); gauge(im, 16, 16, 7, 1.0, '#cfe3ff'); hazard(im, 27, 29); t['ballast'] = im
    t['envelope'] = fabric('#d83b2e', '#efe7da', 4, 44); t['gas_cell'] = fabric('#d9cfb2', '#c7bc9c', 8, 45)
    im = metal('#3a3e43', 46); disc(im, 16, 16, 9, '#1b1d20'); disc(im, 16, 16, 6, '#ff8a1c'); disc(im, 16, 16, 3, '#ffe27a'); t['burner_top'] = im
    t['burner_side'] = grille(metal('#3a3e43', 47), 4, 8, 27, 24)
    # instruments
    im = base('#1f2329', 4, 48); border(im)
    gauge(im, 9, 9, 6, 2.0); gauge(im, 23, 9, 6, 0.8); gauge(im, 9, 23, 6, 1.4, '#cfe3ff'); gauge(im, 23, 23, 6, 2.6, '#ffe6b0'); t['instruments'] = im
    im = base('#1f2329', 4, 49); border(im); d = ImageDraw.Draw(im); d.rectangle([4, 4, 27, 24], fill=rgb('#0e3b2a') + (255,))
    for i in range(6): d.line([(5, 8 + i * 3), (26, 6 + i * 3)], fill=rgb('#1f8f5c') + (255,))
    d.polygon([(16, 9), (19, 18), (16, 16), (13, 18)], fill=rgb('#9dffcf') + (255,)); text_dots(im, [(6, 27), (9, 27), (12, 27)], '#38d3ff'); t['navigation'] = im
    t['device_side'] = border(base('#2a2f36', 5, 50)); rivets(t['device_side'])
    # weapons
    OL = '#4b5a32'
    im = base(OL, 7, 51); border(im); d = ImageDraw.Draw(im); d.rectangle([2, 12, 29, 19], fill=rgb('#2c3420') + (255,)); rivets(im); t['mount'] = im
    im = base(OL, 7, 52); border(im); ring(im, 16, 16, 9, 13, '#2c3420'); disc(im, 16, 16, 5, '#5f6f40'); t['turret_top'] = im
    t['turret_side'] = grille(border(base(OL, 7, 53)), 3, 10, 28, 22, 0.7)
    t['gun_side'] = grille(border(base('#2f3337', 6, 54)), 2, 13, 29, 19, 0.6)
    im = base('#2f3337', 6, 55); border(im); disc(im, 16, 16, 5, '#111214'); ring(im, 16, 16, 5, 7, '#55595d'); t['gun_front'] = im
    im = base('#3f4a2a', 6, 56); border(im); disc(im, 16, 16, 9, '#111214'); ring(im, 16, 16, 9, 12, '#5b6640'); t['cannon_front'] = im
    t['cannon_side'] = grille(border(base('#3f4a2a', 6, 57)), 2, 10, 29, 22, 0.75)
    im = base('#525c4a', 6, 58); border(im)
    for (cx, cy) in [(9, 9), (23, 9), (9, 23), (23, 23)]: disc(im, cx, cy, 5, '#121416'); disc(im, cx, cy, 2, '#d64030')
    t['missile_front'] = im
    t['missile_side'] = grille(border(base('#525c4a', 6, 59)), 2, 6, 29, 26, 0.8)
    im = base('#3c4430', 6, 60); border(im); d = ImageDraw.Draw(im); d.rectangle([3, 3, 15, 28], fill=rgb('#2a3021') + (255,)); d.rectangle([17, 3, 28, 28], fill=rgb('#2a3021') + (255,)); hazard(im, 14, 17); t['bomb_bay'] = im
    im = base('#4b5a32', 8, 61); border(im); d = ImageDraw.Draw(im); d.rectangle([5, 10, 26, 21], outline=rgb('#d8c06a') + (255,)); text_dots(im, [(8, 15), (10, 15), (12, 15), (14, 15), (16, 15)], '#d8c06a'); t['ammo_box'] = im
    im = base('#262b33', 5, 62); border(im)
    for i, c in enumerate(['#d64030', '#e2b422', '#2fae55', '#2f6fd6', '#d64030', '#e2b422']):
        disc(im, 8 + (i % 3) * 8, 11 + (i // 3) * 9, 2.5, c, False)
    d = ImageDraw.Draw(im); d.line([(26, 2), (26, 8)], fill=rgb('#cfd5da') + (255,)); text_dots(im, [(26, 2)], '#ff4040'); t['rcu'] = im
    return t

TEXTURES = T()
for name, im in TEXTURES.items(): tex(name, im)

# ------------------------------------------------------------------ models
def cube_model(name, north, south, east, west, up, down, particle=None):
    model(name, {"parent": "minecraft:block/cube", "textures": {"north": b(north), "south": b(south), "east": b(east), "west": b(west),
                                                                 "up": b(up), "down": b(down), "particle": b(particle or east)}})

def all_model(name, t): model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": b(t)}})

def column_model(name, side, end): model(name, {"parent": "minecraft:block/cube_column", "textures": {"side": b(side), "end": b(end)}})

FACE6 = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
FACE6_UPMODEL = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
FACE4 = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}

def bs_facing(name, mdl, table, prop='facing'):
    blockstate(name, {"variants": {f"{prop}={k}": dict({"model": b(mdl)}, **v) for k, v in table.items()}})

def bs_single(name, mdl): blockstate(name, {"variants": {"": {"model": b(mdl)}}})

def full_dir(name, front, back, side, top=None):
    cube_model(name, front, back, side, side, top or side, top or side)
    bs_facing(name, name, FACE6); item_block(name)

def full_hor(name, front, side, top, back=None):
    cube_model(name, front, back or side, side, side, top, side)
    bs_facing(name, name, FACE4); item_block(name)

# core and systems
full_hor('vehicle_core', 'core_front', 'core_side', 'core_top')
full_dir('piston_engine', 'piston_front', 'device_side', 'piston_side')
full_dir('turboprop_engine', 'turboprop_front', 'turboprop_back', 'engine_side')
full_dir('turbofan_engine', 'turbofan_front', 'turbofan_back', 'engine_side')
full_dir('jet_engine', 'jet_front', 'jet_back', 'engine_side')
full_dir('electric_motor', 'electric_front', 'device_side', 'electric_side')
full_hor('engine_starter', 'starter', 'device_side', 'device_side')
column_model('fuel_tank', 'fuel_tank', 'fuel_tank_top'); bs_single('fuel_tank', 'fuel_tank'); item_block('fuel_tank')
full_hor('fuel_pump', 'fuel_pump', 'fuel_pump', 'device_side')
full_hor('hydraulic_pump', 'hydraulic', 'hydraulic', 'device_side')
column_model('ballast_tank', 'ballast', 'device_side'); bs_single('ballast_tank', 'ballast_tank'); item_block('ballast_tank')
all_model('balloon_envelope', 'envelope'); bs_single('balloon_envelope', 'balloon_envelope'); item_block('balloon_envelope')
all_model('gas_cell', 'gas_cell'); bs_single('gas_cell', 'gas_cell'); item_block('gas_cell')
column_model('burner', 'burner_side', 'burner_top'); bs_single('burner', 'burner'); item_block('burner')
full_hor('flight_instruments', 'instruments', 'device_side', 'device_side')
full_hor('navigation_computer', 'navigation', 'device_side', 'device_side')
full_dir('weapon_mount', 'mount', 'mount', 'mount')
cube_model('turret_base', 'turret_side', 'turret_side', 'turret_side', 'turret_side', 'turret_top', 'turret_side'); bs_facing('turret_base', 'turret_base', FACE6_UPMODEL); item_block('turret_base')
full_dir('machine_gun', 'gun_front', 'gun_side', 'gun_side')
full_dir('cannon', 'cannon_front', 'cannon_side', 'cannon_side')
full_dir('missile_launcher', 'missile_front', 'missile_side', 'missile_side')
cube_model('bomb_bay', 'mount', 'mount', 'mount', 'mount', 'mount', 'bomb_bay'); bs_single('bomb_bay', 'bomb_bay'); item_block('bomb_bay')
full_hor('ammo_box', 'ammo_box', 'ammo_box', 'mount')
full_hor('remote_control_unit', 'rcu', 'device_side', 'device_side')

def face(t, uv=(0, 0, 16, 16), cull=None):
    f = {"texture": "#" + t, "uv": list(uv)}
    if cull: f["cullface"] = cull
    return f

def el(frm, to, faces, rot=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rot: e["rotation"] = rot
    return e

def six(t): return {k: face(t) for k in ("north", "south", "east", "west", "up", "down")}

# seats
for name, cushion in (('pilot_seat', 'seat'), ('passenger_seat', 'seat_passenger')):
    model(name, {"parent": "minecraft:block/block", "textures": {"c": b(cushion), "f": b('seat_frame'), "particle": b(cushion)},
                 "elements": [el([1, 0, 1], [15, 3, 15], six('f')), el([1, 3, 1], [15, 7, 15], six('c')),
                              el([1, 7, 12], [15, 18, 15], six('c')), el([0, 4, 2], [1, 11, 12], six('f')), el([15, 4, 2], [16, 11, 12], six('f'))]})
    bs_facing(name, name, FACE4); item_block(name)

# wings: thin plate
WINGS = ['wing', 'straight_wing', 'swept_wing', 'delta_wing', 'variable_sweep_wing', 'aileron', 'elevator', 'rudder', 'flap', 'spoiler']
for wname in WINGS:
    model(wname, {"parent": "minecraft:block/block", "textures": {"t": b(wname), "e": b('wing_edge'), "particle": b(wname)},
                  "elements": [el([0, 6.5, 0], [16, 9.5, 16], {"up": face('t'), "down": face('t'), "north": face('e', (0, 6, 16, 9)),
                                                               "south": face('e', (0, 6, 16, 9)), "east": face('e', (0, 6, 16, 9)), "west": face('e', (0, 6, 16, 9))})]})
    blockstate(wname, {"variants": {"plane=horizontal": {"model": b(wname)}, "plane=vertical_z": {"model": b(wname), "x": 90},
                                    "plane=vertical_x": {"model": b(wname), "x": 90, "y": 90}}})
    item_block(wname)

# wheel: octagonal tyre, axle along X (forward = north)
def wheel_elements(r0=0, r1=16, thick=(5, 11), hub=True):
    x0, x1 = thick
    m = (r0 + r1) / 2
    els = []
    for rot in (0, 45):
        els.append(el([x0, r0 + 3, r0 + 1], [x1, r1 - 3, r1 - 1], {"east": face('r'), "west": face('r'), "north": face('t'), "south": face('t'), "up": face('t'), "down": face('t')},
                      {"origin": [8, m, m], "axis": "x", "angle": rot} if rot else None))
        els.append(el([x0, r0 + 1, r0 + 3], [x1, r1 - 1, r1 - 3], {"east": face('r'), "west": face('r'), "north": face('t'), "south": face('t'), "up": face('t'), "down": face('t')},
                      {"origin": [8, m, m], "axis": "x", "angle": rot} if rot else None))
    if hub: els.append(el([x0 - 1, m - 2, m - 2], [x1 + 1, m + 2, m + 2], six('h')))
    return els
model('wheel', {"parent": "minecraft:block/block", "textures": {"t": b('tire'), "r": b('rim'), "h": b('strut'), "particle": b('tire')}, "elements": wheel_elements()})
bs_facing('wheel', 'wheel', FACE4); item_block('wheel')
gear_els = wheel_elements(0, 11, (5, 11)) + [el([7, 9, 4.5], [9, 16, 6.5], six('h'))]
model('landing_gear', {"parent": "minecraft:block/block", "textures": {"t": b('tire'), "r": b('rim'), "h": b('strut'), "particle": b('strut')}, "elements": gear_els})
bs_facing('landing_gear', 'landing_gear', FACE4); item_block('landing_gear')

# propeller: hub at the front (north) with 4 blades
model('propeller', {"parent": "minecraft:block/block", "textures": {"b": b('propeller'), "h": b('prop_hub'), "particle": b('propeller')},
                    "elements": [el([6, 6, 0], [10, 10, 5], six('h')), el([7, -8, 1], [9, 24, 3], six('b'), {"origin": [8, 8, 2], "axis": "z", "angle": 22.5}),
                                 el([-8, 7, 1], [24, 9, 3], six('b'), {"origin": [8, 8, 2], "axis": "z", "angle": 22.5}), el([7, 7, 5], [9, 9, 16], six('h'))]})
bs_facing('propeller', 'propeller', FACE6); item_block('propeller')
model('marine_propeller', {"parent": "minecraft:block/block", "textures": {"b": b('marine_propeller'), "h": b('prop_hub'), "particle": b('marine_propeller')},
                           "elements": [el([6, 6, 4], [10, 10, 12], six('h')), el([7, 1, 7], [9, 15, 9], six('b'), {"origin": [8, 8, 8], "axis": "z", "angle": 45}),
                                        el([1, 7, 7], [15, 9, 9], six('b'), {"origin": [8, 8, 8], "axis": "z", "angle": 45}), el([7, 7, 12], [9, 9, 16], six('h'))]})
bs_facing('marine_propeller', 'marine_propeller', FACE6); item_block('marine_propeller')
# main rotor: model "front" is up (rotor axis)
model('main_rotor', {"parent": "minecraft:block/block", "textures": {"b": b('rotor_blade'), "h": b('rotor_hub'), "particle": b('rotor_hub')},
                     "elements": [el([5, 0, 5], [11, 5, 11], six('h')), el([7, 5, 7], [9, 9, 9], six('h')),
                                  el([-8, 8, 7], [24, 9, 9], six('b')), el([7, 8, -8], [9, 9, 24], six('b'))]})
bs_facing('main_rotor', 'main_rotor', FACE6_UPMODEL); item_block('main_rotor')
model('tail_rotor', {"parent": "minecraft:block/block", "textures": {"b": b('rotor_blade'), "h": b('rotor_hub'), "particle": b('rotor_hub')},
                     "elements": [el([6, 6, 4], [10, 10, 12], six('h')), el([7, -4, 2], [9, 20, 3], six('b')), el([-4, 7, 2], [20, 9, 3], six('b'))]})
bs_facing('tail_rotor', 'tail_rotor', FACE6); item_block('tail_rotor')

# ------------------------------------------------------------------ items
def item_tex():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.line([(3, 13), (11, 5)], fill=(150, 156, 162, 255), width=3); d.ellipse([9, 1, 15, 7], outline=(150, 156, 162, 255), width=2)
    d.rectangle([12, 2, 13, 4], fill=(0, 0, 0, 0)); d.line([(2, 14), (5, 11)], fill=(200, 60, 40, 255), width=3); itex('engineering_wrench', im)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([3, 4, 12, 15], fill=(190, 50, 30, 255), outline=(110, 25, 12, 255)); d.rectangle([5, 1, 8, 4], fill=(70, 70, 70, 255))
    d.line([(4, 9), (11, 9)], fill=(240, 200, 60, 255)); itex('fuel_canister', im)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for i in range(6): d.rectangle([1 + i * 2 + (i % 2), 4 + i, 2 + i * 2 + (i % 2), 9 + i], fill=(214, 172, 60, 255)); d.point((1 + i * 2 + (i % 2), 4 + i), fill=(150, 110, 40, 255))
    d.line([(1, 12), (14, 6)], fill=(70, 70, 60, 255)); itex('ammo_belt', im)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([5, 6, 10, 15], fill=(200, 160, 60, 255)); d.polygon([(5, 6), (10, 6), (7.5, 1)], fill=(110, 115, 120, 255)); itex('cannon_shell', im)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.line([(2, 13), (12, 3)], fill=(225, 228, 230, 255), width=3); d.polygon([(12, 3), (15, 0), (13, 5)], fill=(200, 50, 40, 255))
    d.polygon([(1, 11), (4, 14), (1, 15)], fill=(90, 95, 100, 255)); itex('missile', im)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.ellipse([4, 2, 11, 13], fill=(70, 80, 50, 255), outline=(40, 46, 30, 255)); d.polygon([(5, 12), (10, 12), (12, 15), (3, 15)], fill=(50, 56, 40, 255))
    d.line([(4, 7), (11, 7)], fill=(230, 190, 40, 255)); itex('aerial_bomb', im)
item_tex()
for n in ['engineering_wrench', 'fuel_canister', 'ammo_belt', 'cannon_shell', 'missile', 'aerial_bomb']: item_gen(n)

# projectile entity texture: 4 regions (tracer, shell, missile, bomb)
im = Image.new('RGBA', (64, 16)); d = ImageDraw.Draw(im)
d.rectangle([0, 0, 15, 15], fill=(255, 230, 140, 255)); d.rectangle([16, 0, 31, 15], fill=(190, 150, 70, 255))
d.rectangle([32, 0, 47, 15], fill=(226, 228, 230, 255)); d.rectangle([32, 0, 35, 15], fill=(200, 50, 40, 255)); d.rectangle([48, 0, 63, 15], fill=(70, 80, 50, 255))
os.makedirs(os.path.join(A, 'textures', 'entity'), exist_ok=True); im.save(os.path.join(A, 'textures', 'entity', 'projectile.png'))

print('textures/models ok')
