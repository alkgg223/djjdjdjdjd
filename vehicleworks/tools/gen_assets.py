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
    for n, col, sd in (('forward_swept_wing', '#b7c2cc', 71), ('double_delta_wing', '#9ea6ad', 72), ('elliptical_wing', '#dfe6ea', 73), ('tapered_wing', '#ced6dc', 74),
                       ('high_wing', '#e2e7ea', 75), ('low_wing', '#bfc8cf', 76), ('canard_wing', '#a9b4bd', 77), ('flying_wing', '#4a5058', 78)):
        t[n] = wingtex(col, seed=sd)
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
# wings: real cross-sections and planform hints (model front = north = leading edge, span along x)
def WF(t): return {"up": face(t), "down": face(t), "north": face('e', (0, 6, 16, 9)), "south": face('e', (0, 6, 16, 9)), "east": face('e', (0, 6, 16, 9)), "west": face('e', (0, 6, 16, 9))}
def airfoil(x0=0, x1=16, z0=0, z1=16, yc=8, t=3.4, tex='t', rot=None):
    c = z1 - z0
    a, b2 = z0 + 0.22 * c, z0 + 0.62 * c
    return [el([x0, yc - t * 0.55, z0], [x1, yc + t * 0.6, a], WF(tex), rot),           # thick rounded leading edge
            el([x0, yc - t * 0.42, a], [x1, yc + t * 0.48, b2], WF(tex), rot),          # main box
            el([x0, yc - t * 0.18, b2], [x1, yc + t * 0.22, z1], WF(tex), rot)]         # thin trailing edge
def chevron(back=True, t=2.6):
    # two half-plates angled like a swept (or forward-swept) wing; symmetric left/right
    ang = 22.5 if back else -22.5
    return [el([-2, 6.8, 1], [8, 6.8 + t, 15], WF('t'), {"origin": [8, 8, 8], "axis": "y", "angle": ang}),
            el([8, 6.8, 1], [18, 6.8 + t, 15], WF('t'), {"origin": [8, 8, 8], "axis": "y", "angle": -ang}),
            el([0, 6.6, 0], [16, 7.0 + t, 16], WF('t'))]
def stepped(rows, y0=6.6, t=2.8):
    # stacked plates narrowing toward the front: triangle-like planforms
    out = []
    for (z0, z1, w) in rows:
        out.append(el([8 - w / 2, y0, z0], [8 + w / 2, y0 + t, z1], WF('t')))
    return out
def plate(t=3.0): return [el([0, 8 - t / 2, 0], [16, 8 + t / 2, 16], WF('t'))]
def control(t=2.2):
    # fixed part + hinged surface with a visible hinge gap
    return [el([0, 8 - t / 2, 0], [16, 8 + t / 2, 6.5], WF('t')), el([0.5, 8 - t / 2 + 0.3, 7.5], [15.5, 8 + t / 2 - 0.3, 16], WF('t')),
            el([1, 7.6, 6.5], [15, 8.4, 7.5], six('e'))]
SHAPES = {
    'wing': airfoil(), 'straight_wing': airfoil(t=4.2), 'swept_wing': chevron(True), 'forward_swept_wing': chevron(False),
    'delta_wing': stepped([(0, 4, 5), (4, 8, 9), (8, 12, 13), (12, 16, 16)]),
    'double_delta_wing': stepped([(0, 3, 3), (3, 6, 5), (6, 9, 11), (9, 12.5, 14), (12.5, 16, 16)]),
    'elliptical_wing': [el([0, 6.5, 2], [16, 9.6, 14], WF('t')), el([2, 6.8, 0.5], [14, 9.3, 15.5], WF('t')), el([5, 7.0, 0], [11, 9.1, 16], WF('t'))],
    'tapered_wing': airfoil(0, 16, 0, 16, 8, 2.4) + [el([3, 5.9, 1], [13, 10.1, 9], WF('t'))],
    'variable_sweep_wing': plate(2.6) + [el([5, 5.5, 5], [11, 10.5, 11], six('e'))],
    'high_wing': airfoil(yc=13.6) + [el([2, 0, 6], [3.5, 12, 7.5], six('e')), el([12.5, 0, 6], [14, 12, 7.5], six('e'))],
    'low_wing': airfoil(yc=2.6) + [el([4, 4, 3], [12, 6, 12], six('e'))],
    'canard_wing': airfoil(0, 16, 1, 9, 8, 2.6) + [el([7, 6, 3], [9, 10, 5], six('e'))],
    'flying_wing': [el([0, 5.2, 0], [16, 10.8, 5], WF('t')), el([0, 5.6, 5], [16, 10.2, 11], WF('t')), el([0, 7.6, 11], [16, 9.6, 16], WF('t'))],
    'aileron': control(), 'elevator': control(), 'rudder': control(), 'flap': control(), 'spoiler': plate(1.6) + [el([1, 8.8, 4], [15, 9.6, 12], six('e'))],
}
WINGS = list(SHAPES.keys())
HFACE = {"north": 0, "east": 90, "south": 180, "west": 270}
for wname in WINGS:
    model(wname, {"parent": "minecraft:block/block", "textures": {"t": b(wname), "e": b('wing_edge'), "particle": b(wname)}, "elements": SHAPES[wname]})
    # vertical (fin) orientation: symmetric airfoil plate
    model(wname + '_v', {"parent": "minecraft:block/block", "textures": {"t": b(wname), "e": b('wing_edge'), "particle": b(wname)},
                         "elements": plate(2.6) if wname not in ('aileron', 'elevator', 'rudder', 'flap', 'spoiler') else plate(2.2)})
    variants = {}
    for f, ry in HFACE.items():
        variants[f"facing={f},plane=horizontal"] = {"model": b(wname), "y": ry} if ry else {"model": b(wname)}
        # vertical_z: plate in the X-Y plane (flight along X); vertical_x: plate in Z-Y plane (flight along Z)
        variants[f"facing={f},plane=vertical_z"] = {"model": b(wname + '_v'), "x": 90}
        variants[f"facing={f},plane=vertical_x"] = {"model": b(wname + '_v'), "x": 90, "y": 90}
    blockstate(wname, {"variants": variants})
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
gear_els = wheel_elements(0, 16, (5, 11))
model('landing_gear', {"parent": "minecraft:block/block", "textures": {"t": b('tire'), "r": b('rim'), "h": b('strut'), "particle": b('strut')}, "elements": gear_els})
bs_facing('landing_gear', 'landing_gear', FACE4); item_block('landing_gear')
# heavy gear: twin-wheel bogie on a thick strut (drawn at its radius by the vehicle renderer)
heavy_els = wheel_elements(0, 16, (0.5, 6.5)) + wheel_elements(0, 16, (9.5, 15.5))
model('heavy_landing_gear', {"parent": "minecraft:block/block", "textures": {"t": b('tire'), "r": b('rim'), "h": b('strut'), "particle": b('strut')}, "elements": heavy_els})
bs_facing('heavy_landing_gear', 'heavy_landing_gear', FACE4); item_block('heavy_landing_gear')

# big jet engines: nacelle larger than one block (front = north)
def F(t): return face(t, (0, 0, 16, 16))
def nacelle(name, front, back, side, r0, r1, z0, z1, nozzle):
    els = [el([r0, r1 - 3, z0], [r1, r1, z1], {"north": F(side), "south": F(side), "east": F(side), "west": F(side), "up": F(side), "down": F(side)}),
           el([r0, r0, z0], [r1, r0 + 3, z1], {"north": F(side), "south": F(side), "east": F(side), "west": F(side), "up": F(side), "down": F(side)}),
           el([r0, r0 + 3, z0], [r0 + 3, r1 - 3, z1], {"north": F(side), "south": F(side), "east": F(side), "west": F(side), "up": F(side), "down": F(side)}),
           el([r1 - 3, r0 + 3, z0], [r1, r1 - 3, z1], {"north": F(side), "south": F(side), "east": F(side), "west": F(side), "up": F(side), "down": F(side)}),
           # intake lip (rotated square makes the opening look round)
           el([r0 + 4, r0 + 4, z0 - 1], [r1 - 4, r1 - 4, z0 + 1], {"north": F(front), "south": F(front), "east": F(side), "west": F(side), "up": F(side), "down": F(side)},
              {"origin": [8, 8, z0], "axis": "z", "angle": 45}),
           # fan face and spinner
           el([r0 + 3, r0 + 3, z0 + 2], [r1 - 3, r1 - 3, z0 + 3], {"north": F(front), "south": F(side)}),
           el([6, 6, z0 + 0.5], [10, 10, z0 + 2], six(side)),
           # exhaust core / nozzle
           el([nozzle, nozzle, z1], [16 - nozzle, 16 - nozzle, z1 + 4], {"north": F(side), "south": F(back), "east": F(side), "west": F(side), "up": F(side), "down": F(side)}),
           # pylon to the wing above
           el([7, r1, z0 + 4], [9, max(r1 + 1, 17), z1 - 2], six(side))]
    model(name, {"parent": "minecraft:block/block", "textures": {"particle": b(side), front: b(front), back: b(back), side: b(side)},
                 "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]}},
                 "elements": els})
    bs_facing(name, name, FACE6); item_block(name)
nacelle('turbofan_engine', 'turbofan_front', 'turbofan_back', 'engine_side', -5, 21, -2, 15, 3)
nacelle('jet_engine', 'jet_front', 'jet_back', 'engine_side', -2, 18, -2, 16, 2)

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

# ------------------------------------------------------------------ cockpit panel (3 wide x 2 tall, tilted console)
from PIL import ImageDraw as _D
def panel_tile(seed, lamp, draw_icon):
    im = base('#23272d', 5, 300 + seed); border(im)
    d = _D.Draw(im)
    d.rectangle([3, 3, S - 4, S - 4], outline=rgb('#4a525c') + (255,))
    disc(im, S // 2, S - 8, 3, lamp)        # status lamp
    draw_icon(d)
    return im
C = S // 2
def ic_battery(d):
    d.rectangle([C - 8, C - 9, C + 8, C + 3], outline=rgb('#e8edf2') + (255,)); d.rectangle([C - 3, C - 11, C + 3, C - 9], fill=rgb('#e8edf2') + (255,))
    d.line([(C - 5, C - 3), (C - 1, C - 3)], fill=rgb('#ff5a4a') + (255,)); d.line([(C + 2, C - 3), (C + 6, C - 3)], fill=rgb('#7cff8a') + (255,)); d.line([(C + 4, C - 5), (C + 4, C - 1)], fill=rgb('#7cff8a') + (255,))
def ic_light(d):
    d.ellipse([C - 6, C - 12, C + 6, C], fill=rgb('#ffe27a') + (255,)); d.rectangle([C - 3, C, C + 3, C + 3], fill=rgb('#9aa3ad') + (255,))
    for a in range(0, 360, 45):
        x, y = C + 10 * math.cos(math.radians(a)), C - 6 + 10 * math.sin(math.radians(a))
        d.point((x, y), fill=rgb('#ffe27a') + (255,))
def ic_engine(d):
    d.ellipse([C - 9, C - 11, C + 9, C + 7], outline=rgb('#cfd5da') + (255,))
    for a in range(0, 360, 60):
        d.line([(C, C - 2), (C + 8 * math.cos(math.radians(a)), C - 2 + 8 * math.sin(math.radians(a)))], fill=rgb('#cfd5da') + (255,))
def ic_gear(d):
    d.line([(C, C - 12), (C, C - 4)], fill=rgb('#cfd5da') + (255,), width=2)
    d.ellipse([C - 6, C - 5, C + 6, C + 7], outline=rgb('#cfd5da') + (255,), width=2)
def ic_emergency(d):
    d.ellipse([C - 9, C - 12, C + 9, C + 6], fill=rgb('#d63a2e') + (255,), outline=rgb('#ffd23a') + (255,))
    d.line([(C, C - 8), (C, C - 1)], fill=rgb('#ffffff') + (255,), width=2); d.point((C, C + 2), fill=rgb('#ffffff') + (255,))
def ic_ap(d):
    d.polygon([(C - 8, C + 2), (C, C - 12), (C + 8, C + 2), (C, C - 3)], outline=rgb('#38d3ff') + (255,))
    d.line([(C - 10, C + 4), (C + 10, C + 4)], fill=rgb('#38d3ff') + (255,))
PANEL = [('panel_battery', '#7cff8a', ic_battery), ('panel_lights', '#ffe27a', ic_light), ('panel_engines', '#7cff8a', ic_engine),
         ('panel_gear', '#7cff8a', ic_gear), ('panel_emergency', '#ff4040', ic_emergency), ('panel_autopilot', '#38d3ff', ic_ap)]
for i, (n, lamp, fn) in enumerate(PANEL): tex(n, panel_tile(i, lamp, fn))
def pface(t): return face(t, (0, 0, 16, 16))
# overhead panel: flat plate under the ceiling, 3 wide x 2 deep, buttons face down
els = []
cols = [(16, 32), (0, 16), (-16, 0)]
for row, names in ((0, ['b', 'l', 'e']), (1, ['g', 'x', 'a'])):
    for (x0, x1), n in zip(cols, names):
        z0 = -8 + row * 16
        els.append(el([x0, 14.5, z0], [x1, 16, z0 + 16], {"down": pface(n), "up": pface('s'), "north": pface('s'), "south": pface('s'), "east": pface('s'), "west": pface('s')}))
model('cockpit_panel', {"parent": "minecraft:block/block",
                        "textures": {"particle": b('device_side'), "s": b('device_side'), "b": b('panel_battery'), "l": b('panel_lights'), "e": b('panel_engines'),
                                     "g": b('panel_gear'), "x": b('panel_emergency'), "a": b('panel_autopilot')},
                        "display": {"gui": {"rotation": [-60, 160, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}},
                        "elements": els})
bs_facing('cockpit_panel', 'cockpit_panel', FACE4); item_block('cockpit_panel')
print('panel ok')

# ------------------------------------------------------------------ yoke (front = north = pilot side); drawn by the vehicle renderer
yoke_els = [el([7, 9.25, 5], [9, 11.25, 16], six('h')),            # control column
            el([6, 8.25, 3.5], [10, 12.25, 5.5], six('d')),        # hub
            el([2, 9.5, 4], [14, 11, 5], six('d')),                # cross bar
            el([1, 6.5, 3.75], [3, 13, 5.25], six('g')),           # left horn
            el([13, 6.5, 3.75], [15, 13, 5.25], six('g'))]         # right horn
model('yoke', {"parent": "minecraft:block/block", "textures": {"particle": b('strut'), "h": b('strut'), "d": b('device_side'), "g": b('seat_frame')},
               "display": {"gui": {"rotation": [20, 200, 0], "translation": [0, 2, 0], "scale": [0.9, 0.9, 0.9]}}, "elements": yoke_els})
bs_facing('yoke', 'yoke', FACE4); item_block('yoke')
print('yoke ok')

# ------------------------------------------------------------------ aircraft wheel family (tyres spin about the block centre)
def tread(seed, col, block=False):
    im = base(col, 6, seed); px = im.load()
    for y in range(S):
        for x in range(S):
            if block:
                if (y // 4) % 2 == 0 and (x // 6 + y // 4) % 2 == 0: px[x, y] = shade(px[x, y], 1.6)
            elif x % 8 < 2: px[x, y] = shade(px[x, y], 1.4)
    return im
def rim_colored(col, spokes):
    im = tire(9); disc(im, 16, 16, 11, col); ring(im, 16, 16, 7, 8.5, '#3a3f44'); disc(im, 16, 16, 3.5, '#e8ecef')
    for a in range(spokes):
        ang = a * 2 * math.pi / spokes
        for rr in range(4, 11): disc(im, 16 + math.cos(ang) * rr, 16 + math.sin(ang) * rr, 0.8, '#2a2e33', False)
    return im
tex('tire_tundra', tread(401, '#2e2b27', True)); tex('rim_tundra', rim_colored('#e8c040', 6))
tex('tire_perf', tread(402, '#1d1d1f')); tex('rim_perf', rim_colored('#c02828', 10))
tex('rim_heavy', rim_colored('#9aa2a8', 8))
def gear_model(name, els, t='tire', r='rim'):
    model(name, {"parent": "minecraft:block/block", "textures": {"t": b(t), "r": b(r), "h": b('strut'), "particle": b('strut')}, "elements": els})
    bs_facing(name, name, FACE4); item_block(name)
gear_model('dual_wheel_gear', wheel_elements(0, 16, (0.5, 6.5)) + wheel_elements(0, 16, (9.5, 15.5)))
gear_model('bogie_gear', wheel_elements(1, 15, (0.5, 6.5)) + wheel_elements(1, 15, (9.5, 15.5)), r='rim_heavy')
gear_model('reinforced_gear', wheel_elements(0, 16, (2.5, 13.5)) + [el([1.5, 5.5, 5.5], [14.5, 10.5, 10.5], six('h'))], r='rim_heavy')
gear_model('tundra_gear', wheel_elements(0, 16, (1.5, 14.5)), t='tire_tundra', r='rim_tundra')
gear_model('performance_gear', wheel_elements(1, 15, (6, 10)), t='tire_perf', r='rim_perf')
print('gear family ok')

# ------------------------------------------------------------------ cockpit instruments (64 px faces)
from PIL import Image as _I, ImageDraw as _ID, ImageFont as _IF
_F = _IF.load_default(size=7)
N = 64; CC = 32
def _dial(bg='#0d0f12'):
    im = _I.new('RGBA', (N, N), (0, 0, 0, 0)); d = _ID.Draw(im)
    d.ellipse([2, 2, N - 3, N - 3], fill=bg)
    return im, d
def _ticks(d, a0, a1, n, r0, r1, col='#e8e8e8', every=1, r0b=None):
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n - 90)
        rr = r0b if (r0b and i % every == 0) else r0
        d.line([(CC + rr * math.cos(a), CC + rr * math.sin(a)), (CC + r1 * math.cos(a), CC + r1 * math.sin(a))], fill=col, width=1)
def _labels(d, a0, a1, labels, r, col='#ffffff'):
    n = len(labels) - 1
    for i, s in enumerate(labels):
        a = math.radians(a0 + (a1 - a0) * i / max(1, n) - 90)
        tw = d.textlength(s, font=_F)
        d.text((CC + r * math.cos(a) - tw / 2, CC + r * math.sin(a) - 4), s, font=_F, fill=col)
def _arc(d, a0, a1, r, col, w=3):
    d.arc([CC - r, CC - r, CC + r, CC + r], a0 - 90, a1 - 90, fill=col, width=w)
def _title(d, s, y=40, col='#9fb3c8'):
    tw = d.textlength(s, font=_F); d.text((CC - tw / 2, y), s, font=_F, fill=col)
def itex64(name, im):
    p = os.path.join(A, 'textures', 'block', name + '.png'); im.save(p)
# altimeter: 0-9 (hundreds) around the dial
im, d = _dial(); _ticks(d, 0, 360, 50, 25, 29, every=5, r0b=22); _labels(d, 0, 324, [str(i) for i in range(10)], 19); _title(d, 'ALT', 38); itex64('inst_altimeter', im)
# airspeed: 0..1000 km/h over 300 deg, coloured arcs
im, d = _dial(); _arc(d, 18, 75, 27, '#e8e8e8'); _arc(d, 30, 210, 25, '#2fae55'); _arc(d, 210, 255, 25, '#e2b422'); _ticks(d, 255, 258, 1, 20, 29, '#ff3030')
_ticks(d, 0, 300, 20, 25, 29, every=2, r0b=22); _labels(d, 0, 240, ['0', '2', '4', '6', '8'], 19); _title(d, 'KM/H', 38); itex64('inst_airspeed', im)
# vertical speed: -30..+30 m/s, zero at 9 o'clock
im, d = _dial(); _ticks(d, -90 - 160, -90 + 160, 24, 25, 29, every=4, r0b=21)
_labels(d, -250, -90, ['30', '20', '10', ''], 19); _labels(d, -90, 70, ['0', '10', '20', '30'], 19); _title(d, 'VS', 38); d.text((40, 18), 'UP', font=_F, fill='#9fb3c8'); d.text((38, 40), 'DN', font=_F, fill='#9fb3c8'); itex64('inst_vsi', im)
# tachometer 0..110 %
im, d = _dial(); _arc(d, 20, 230, 25, '#2fae55'); _ticks(d, 250, 252, 1, 19, 29, '#ff3030'); _ticks(d, 0, 275, 22, 25, 29, every=2, r0b=21)
_labels(d, 0, 250, ['0', '2', '4', '6', '8', '10'], 19); _title(d, 'RPM%', 38); itex64('inst_tachometer', im)
# fuel gauge: E .. F
im, d = _dial(); _arc(d, -60, -35, 25, '#ff3030', 4); _arc(d, -35, 60, 25, '#e8e8e8', 2); _ticks(d, -60, 60, 8, 24, 29, every=2, r0b=20)
_labels(d, -60, 60, ['E', '1/2', 'F'], 19); _title(d, 'FUEL', 38); itex64('inst_fuel_gauge', im)
# engine pressure
im, d = _dial(); _arc(d, -120, -80, 25, '#ff3030', 4); _arc(d, -80, 70, 25, '#2fae55', 3); _arc(d, 70, 120, 25, '#e2b422', 3); _ticks(d, -120, 120, 12, 24, 29, every=3, r0b=20)
_labels(d, -120, 120, ['0', '25', '50', '75', '100'], 19); _title(d, 'OIL', 38); itex64('inst_engine_pressure', im)
# heading indicator card (rotates), compass card (rotates)
for nm, col, bg in (('inst_hdg_card', '#ffffff', '#101418'), ('inst_compass_card', '#ffd040', '#1b1206')):
    im, d = _dial(bg); _ticks(d, 0, 360, 36, 25, 29, col, every=3, r0b=21)
    for i, s in enumerate(['N', '3', '6', 'E', '12', '15', 'S', '21', '24', 'W', '30', '33']):
        a = math.radians(i * 30 - 90); tw = d.textlength(s, font=_F)
        d.text((CC + 18 * math.cos(a) - tw / 2, CC + 18 * math.sin(a) - 4), s, font=_F, fill='#ff8040' if s in 'NESW' and len(s) == 1 else col)
    itex64(nm, im)
# turn coordinator face
im, d = _dial(); _ticks(d, -90 - 20, -90 + 20, 2, 22, 29, every=1); _ticks(d, 90 - 20, 90 + 20, 2, 22, 29, every=1)
d.text((8, 36), 'L', font=_F, fill='#ffffff'); d.text((52, 36), 'R', font=_F, fill='#ffffff'); d.rounded_rectangle([18, 44, 46, 52], 3, outline='#9fb3c8'); _title(d, '2 MIN', 12); itex64('inst_turn', im)
# bezel: square frame with a round window (cutout masks the horizon card / needles)
im = _I.new('RGBA', (N, N), (0, 0, 0, 0)); d = _ID.Draw(im)
d.rectangle([0, 0, N - 1, N - 1], fill='#24282d'); d.ellipse([3, 3, N - 4, N - 4], fill=(0, 0, 0, 0)); d.ellipse([1, 1, N - 2, N - 2], outline='#5a6068', width=2)
for (x, y) in ((4, 4), (N - 6, 4), (4, N - 6), (N - 6, N - 6)): d.rectangle([x, y, x + 1, y + 1], fill='#9aa0a6')
itex64('inst_bezel', im)
# screen bezel for large displays
im = _I.new('RGBA', (N, N), (0, 0, 0, 0)); d = _ID.Draw(im)
d.rectangle([0, 0, N - 1, N - 1], fill='#1c1f23'); d.rectangle([3, 3, N - 4, N - 4], fill=(0, 0, 0, 0)); d.rectangle([2, 2, N - 3, N - 3], outline='#4a5058')
for x in range(8, N - 8, 10): d.rectangle([x, N - 3, x + 4, N - 2], fill='#5a6068')
itex64('inst_screen_bezel', im)
# instrument panel block faces
im = _I.new('RGBA', (32, 32), (0, 0, 0, 255)); d = _ID.Draw(im); d.rectangle([0, 0, 31, 31], fill='#1d2024'); d.rectangle([0, 0, 31, 31], outline='#3a3f45')
p = os.path.join(A, 'textures', 'block', 'instrument_face.png'); im.save(p)
for nm in ('instrument_panel_a', 'instrument_panel_b', 'instrument_panel_c'):
    model(nm, {"parent": "minecraft:block/cube", "textures": {"north": b('instrument_face'), "south": b('device_side'), "east": b('device_side'), "west": b('device_side'),
                                                             "up": b('device_side'), "down": b('device_side'), "particle": b('device_side')}})
    bs_facing(nm, nm, FACE4); item_block(nm)
# aircraft light housing (glow is drawn by the renderer)
model('aircraft_light', {"parent": "minecraft:block/block", "textures": {"h": b('strut'), "l": b('wing_edge'), "particle": b('strut')},
                         "elements": [el([5.5, 5.5, 5.5], [10.5, 8, 10.5], six('h')), el([6.5, 8, 6.5], [9.5, 10.5, 9.5], six('l'))]})
bs_facing('aircraft_light', 'aircraft_light', FACE4); item_block('aircraft_light')
# overhead switch panel plate (switches, knobs and lamps are drawn live by the renderer)
im = _I.new('RGBA', (32, 32), (0, 0, 0, 255)); d = _ID.Draw(im); d.rectangle([0, 0, 31, 31], fill='#2a2e33')
for x in range(0, 32, 8): d.line([(x, 0), (x, 31)], fill='#383d43')
d.line([(0, 15), (31, 15)], fill='#383d43')
p = os.path.join(A, 'textures', 'block', 'panel_plate.png'); im.save(p)
model('cockpit_panel', {"parent": "minecraft:block/block", "textures": {"particle": b('device_side'), "s": b('device_side'), "p": b('panel_plate')},
                        "display": {"gui": {"rotation": [-60, 160, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}},
                        "elements": [el([-16, 14.5, -8], [32, 16, 24], {"down": face('p'), "up": face('s'), "north": face('s'), "south": face('s'), "east": face('s'), "west": face('s')})]})
print('instruments ok')
# refuel port: cap with hose coupling
im = _I.new('RGBA', (32, 32), (0, 0, 0, 255)); d = _ID.Draw(im); d.rectangle([0, 0, 31, 31], fill='#3a3f45')
d.ellipse([7, 7, 24, 24], fill='#d8b020'); d.ellipse([11, 11, 20, 20], fill='#1a1a1a'); d.text((3, 1), 'FUEL', font=_IF.load_default(size=7), fill='#ffffff')
p = os.path.join(A, 'textures', 'block', 'refuel_port.png'); im.save(p)
model('refuel_port', {"parent": "minecraft:block/cube", "textures": {"north": b('refuel_port'), "south": b('device_side'), "east": b('device_side'), "west": b('device_side'),
                                                       "up": b('device_side'), "down": b('device_side'), "particle": b('device_side')}})
bs_facing('refuel_port', 'refuel_port', FACE4); item_block('refuel_port')
print('refuel ok')

# ------------------------------------------------------------------ seats (v2): sculpted pilot seat and airline passenger seat
def _leather(col, seed, stitch='#9aa0a6'):
    im = base(col, 5, seed); d = _ID.Draw(im)
    for y in (8, 16, 24): d.line([(2, y), (29, y)], fill=rgb(stitch) + (150,))
    d.line([(15, 2), (15, 29)], fill=rgb(stitch) + (110,))
    return im
tex('seat_pilot2', _leather('#30343b', 501)); tex('seat_pax2', _leather('#22417e', 502, '#5a78b8'))
im = base('#e9e7df', 3, 503); d = _ID.Draw(im); d.rectangle([0, 22, 31, 25], fill=rgb('#3a6ac0') + (255,)); tex('seat_headrest', im)
im = base('#c9a13a', 4, 504); d = _ID.Draw(im); d.rectangle([10, 12, 21, 19], fill=rgb('#d8dde2') + (255,)); tex('seat_harness', im)
def SF(t): return six(t)
pilot = [el([3, 0, 2], [5, 2, 14], SF('f')), el([11, 0, 2], [13, 2, 14], SF('f')),            # floor rails
         el([5, 1, 4], [11, 4.5, 12], SF('f')),                                                  # pedestal
         el([2, 4.5, 1], [14, 7.5, 13], SF('c')),                                                # seat pan
         el([1, 5.5, 1], [3, 8.5, 13], SF('c')), el([13, 5.5, 1], [15, 8.5, 13], SF('c')),      # cushion bolsters
         el([2, 7.5, 12], [14, 22, 15], SF('c')),                                                # backrest
         el([1, 8, 11.5], [3, 20, 15], SF('c')), el([13, 8, 11.5], [15, 20, 15], SF('c')),      # back bolsters
         el([4, 22, 12.5], [12, 27, 15], SF('c')),                                               # headrest
         el([0, 9.5, 4], [2, 11, 12], SF('f')), el([14, 9.5, 4], [16, 11, 12], SF('f')),        # armrests
         el([0.5, 7, 10], [1.5, 9.5, 11], SF('f')), el([14.5, 7, 10], [15.5, 9.5, 11], SF('f')),
         el([5, 10, 11.7], [6.5, 21, 12], SF('h')), el([9.5, 10, 11.7], [11, 21, 12], SF('h')),  # harness straps
         el([6.5, 12, 11.7], [9.5, 14, 11.9], SF('h'))]
recl = {"origin": [8, 7.5, 13.5], "axis": "x", "angle": -22.5}
pax = [el([2, 0, 3], [3.5, 4.5, 4.5], SF('f')), el([12.5, 0, 3], [14, 4.5, 4.5], SF('f')),       # legs
       el([2, 0, 10], [3.5, 4.5, 11.5], SF('f')), el([12.5, 0, 10], [14, 4.5, 11.5], SF('f')),
       el([1, 4.5, 1], [15, 7.5, 13], SF('c')),                                                  # cushion
       el([1, 7.5, 12], [15, 21, 15], SF('c'), recl),                                            # reclined backrest
       el([2, 18, 11.6], [14, 23, 15.3], SF('r'), recl),                                         # headrest cover
       el([0, 9, 3], [1.5, 10.5, 13], SF('f')), el([14.5, 9, 3], [16, 10.5, 13], SF('f')),      # armrests
       el([0.25, 4.5, 11], [1.25, 9, 12], SF('f')), el([14.75, 4.5, 11], [15.75, 9, 12], SF('f'))]
model('pilot_seat', {"parent": "minecraft:block/block", "textures": {"c": b('seat_pilot2'), "f": b('seat_frame'), "h": b('seat_harness'), "particle": b('seat_pilot2')}, "elements": pilot})
model('passenger_seat', {"parent": "minecraft:block/block", "textures": {"c": b('seat_pax2'), "f": b('seat_frame'), "r": b('seat_headrest'), "particle": b('seat_pax2')}, "elements": pax})
print('seats v2 ok')

# ------------------------------------------------------------------ 1.2: cockpit hardware materials (neutral, tinted by the renderer)
import random as _rnd
def _noise_im(col, amp, seed, size=32):
    r = _rnd.Random(seed); im = _I.new('RGBA', (size, size)); px = im.load(); c = rgb(col)
    for y in range(size):
        for x in range(size):
            n = r.randint(-amp, amp); px[x, y] = tuple(max(0, min(255, v + n)) for v in c) + (255,)
    return im
im = _noise_im('#d6dade', 4, 901); d = _ID.Draw(im)
for y in range(0, 32, 2): d.line([(0, y), (31, y)], fill=rgb('#e6e9ec') + (255,))        # brushed streaks
tex('ck_metal', im)
tex('ck_black', _noise_im('#c4c4c4', 6, 902))
im = _noise_im('#b8b8b8', 3, 903); d = _ID.Draw(im)
for x in range(0, 32, 4): d.line([(x, 0), (x, 31)], fill=rgb('#f0f0f0') + (255,)); d.line([(x + 1, 0), (x + 1, 31)], fill=rgb('#8a8a8a') + (255,))
tex('ck_knob', im)
im = _I.new('RGBA', (32, 32)); px = im.load()
for y in range(32):
    for x in range(32):
        v = int(205 + 50 * (1 - (x + y) / 62.0)); px[x, y] = (v, v, v, 255)
d = _ID.Draw(im); d.line([(3, 4), (12, 4)], fill=(255, 255, 255, 255)); d.line([(3, 5), (8, 5)], fill=(255, 255, 255, 255))
tex('ck_glass', im)
im = _noise_im('#e8e8e8', 3, 904); d = _ID.Draw(im)
for k in range(-32, 32, 8): d.line([(k, 31), (k + 31, 0)], fill=rgb('#c8c8c8') + (255,), width=3)
tex('ck_guard', im)
im = _noise_im('#9a9a9a', 4, 905); d = _ID.Draw(im); d.rectangle([0, 0, 31, 31], outline=rgb('#c8c8c8') + (255,))
tex('ck_bezel', im)
tex('ck_white', _I.new('RGBA', (16, 16), (255, 255, 255, 255)))
# overhead panel plate: matte dark grey aluminium with corner screws (no painted squares: the hardware is 3D)
def _plate(seed):
    im = _noise_im('#2b2f34', 3, 910 + seed); d = _ID.Draw(im)
    d.rectangle([0, 0, 31, 31], outline=rgb('#363b41') + (255,))
    for (x, y) in ((2, 2), (29, 2), (2, 29), (29, 29)):
        d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=rgb('#6d737a') + (255,)); d.point((x, y), fill=rgb('#2a2d31') + (255,))
    return im
for i in range(6): tex('ovh_plate%d' % i, _plate(i))
els = []
for row in (0, 1):
    for ci, (x0, x1) in enumerate([(16, 32), (0, 16), (-16, 0)]):
        z0 = -8 + row * 16; n = 'p%d' % (row * 3 + ci)
        els.append(el([x0, 14.5, z0], [x1, 16, z0 + 16], {"down": pface(n), "up": pface('s'), "north": pface('s'), "south": pface('s'), "east": pface('s'), "west": pface('s')}))
model('cockpit_panel', {"parent": "minecraft:block/block",
                        "textures": dict({"particle": b('device_side'), "s": b('device_side')}, **{'p%d' % i: b('ovh_plate%d' % i) for i in range(6)}),
                        "display": {"gui": {"rotation": [-60, 160, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}},
                        "elements": els})
print('cockpit hardware ok')

# ------------------------------------------------------------------ 1.2: copilot seat, side stick, five cabin classes (static models; animated in a vehicle)
model('copilot_seat', {"parent": "minecraft:block/block", "textures": {"c": b('seat_pilot2'), "f": b('seat_frame'), "h": b('seat_harness'), "particle": b('seat_pilot2')}, "elements": pilot})
bs_facing('copilot_seat', 'copilot_seat', FACE4); item_block('copilot_seat')
tex('stick_grip', _noise_im('#2a2c30', 5, 920))
stick = [el([5, 0, 5], [11, 2, 11], six('b')), el([6, 2, 6], [10, 3, 10], six('k')),        # base + rubber boot
         el([7, 3, 7], [9, 9, 9], six('g')), el([6.5, 9, 6.5], [9.5, 13, 10], six('g')),     # handle
         el([7.2, 9.5, 6], [8.8, 11, 6.5], six('r')), el([7, 13, 7.5], [9, 13.6, 9.5], six('r'))]   # trigger + priority button
tex('stick_red', _noise_im('#c8281e', 4, 921))
model('sidestick', {"parent": "minecraft:block/block", "textures": {"b": b('seat_frame'), "k": b('stick_grip'), "g": b('stick_grip'), "r": b('stick_red'), "particle": b('stick_grip')},
                    "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]}}, "elements": stick})
bs_facing('sidestick', 'sidestick', FACE4); item_block('sidestick')
CABIN = {'seat_economy': ('#2a4f8f', '#5a78b8'), 'seat_economy_plus': ('#1f6f78', '#5fb0b8'), 'seat_premium': ('#3b4a5e', '#8a9ab0'),
         'seat_business': ('#4a3a2e', '#a08870'), 'seat_first': ('#d9cdb4', '#8a7a60')}
for i, (n, (c, s)) in enumerate(CABIN.items()):
    tex(n + '_fabric', _leather(c, 930 + i, s))
tex('cabin_wood', _noise_im('#6b4528', 7, 940)); tex('cabin_shell', _noise_im('#e8e4da', 3, 941)); tex('cabin_screen', _noise_im('#101418', 2, 942))
def cabin_model(n, wide, back_h, legrest, console, shell):
    x0, x1 = (1, 15) if not wide else (0, 16)
    e = [el([x0 + 1, 0, 3], [x0 + 2.5, 4.5, 4.5], SF('f')), el([x1 - 2.5, 0, 3], [x1 - 1, 4.5, 4.5], SF('f')),
         el([x0 + 1, 0, 10], [x0 + 2.5, 4.5, 11.5], SF('f')), el([x1 - 2.5, 0, 10], [x1 - 1, 4.5, 11.5], SF('f')),
         el([x0, 4.5, 1], [x1, 8, 13], SF('c')), el([x0, 8, 12], [x1, back_h, 15], SF('c')),
         el([x0 + 1, back_h - 4, 11.6], [x1 - 1, back_h + 1, 15.3], SF('c')),
         el([x0 - 1 if x0 > 0 else 0, 9, 3], [x0 + 0.5, 10.5, 13], SF('f')), el([x1 - 0.5, 9, 3], [x1 + 1 if x1 < 16 else 16, 10.5, 13], SF('f'))]
    if legrest: e.append(el([x0 + 1, 3, -1], [x1 - 1, 7, 1], SF('c')))
    if console: e.append(el([12.5, 0, 0], [16, 10, 15], SF('w')))
    if shell: e += [el([0, 0, 15], [16, 26, 16], SF('s')), el([15, 0, 0], [16, 22, 15], SF('w'))]
    model(n, {"parent": "minecraft:block/block", "textures": {"c": b(n + '_fabric'), "f": b('seat_frame'), "w": b('cabin_wood'), "s": b('cabin_shell'), "particle": b(n + '_fabric')},
              "elements": e})
    bs_facing(n, n, FACE4); item_block(n)
cabin_model('seat_economy', False, 20, False, False, False)
cabin_model('seat_economy_plus', True, 21, True, False, False)
cabin_model('seat_premium', True, 23, True, False, False)
cabin_model('seat_business', True, 24, True, True, False)
cabin_model('seat_first', True, 25, True, True, True)
print('cabin seats ok')

# ------------------------------------------------------------------ 1.2.2: light effect textures (additive): soft glow, star flare, light beam
import numpy as _np
_M = os.path.join(A, 'textures', 'misc'); os.makedirs(_M, exist_ok=True)
def _save_gray(name, arr):
    v = (_np.clip(arr, 0, 1) * 255).astype(_np.uint8)
    im = _I.fromarray(_np.dstack([v, v, v, _np.full_like(v, 255)]), 'RGBA'); im.save(os.path.join(_M, name + '.png'))
n = 128; yy, xx = _np.mgrid[0:n, 0:n]; r = _np.hypot(xx - (n - 1) / 2, yy - (n - 1) / 2) / (n / 2)
# glow: bright core + wide soft gaussian halo, exactly black at the edge
g = 0.85 * _np.exp(-(r / 0.13) ** 2) + 0.35 * _np.exp(-(r / 0.42) ** 2) + 0.12 * _np.exp(-(r / 0.75) ** 2)
g *= _np.clip((1 - r) / 0.15, 0, 1); _save_gray('glow', g)
# flare: thin horizontal + vertical streaks and a faint ring (strobes, beacons, landing lights at night)
dx, dy = (xx - (n - 1) / 2) / (n / 2), (yy - (n - 1) / 2) / (n / 2)
f = _np.exp(-(dy / 0.025) ** 2) * _np.exp(-(_np.abs(dx) / 0.55) ** 1.5) + 0.6 * _np.exp(-(dx / 0.03) ** 2) * _np.exp(-(_np.abs(dy) / 0.35) ** 1.5)
f += 0.08 * _np.exp(-((r - 0.55) / 0.04) ** 2) + 0.5 * _np.exp(-(r / 0.08) ** 2)
f *= _np.clip((1 - r) / 0.1, 0, 1); _save_gray('flare', f)
# beam: across (u) a soft gaussian, along (v) bright at the lamp and fading out with distance
w, h = 64, 256; v_, u_ = _np.mgrid[0:h, 0:w]; uu = (u_ - (w - 1) / 2) / (w / 2); vv = v_ / (h - 1)
b = _np.exp(-(uu / 0.5) ** 2) * (1 - vv) ** 1.1 * (0.75 + 0.25 * _np.exp(-(uu / 0.25) ** 2)) * _np.clip(vv / 0.04, 0.35, 1)
b *= _np.clip((1 - _np.abs(uu)) / 0.2, 0, 1); _save_gray('beam', b)
print('light fx textures ok')
