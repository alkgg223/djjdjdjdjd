"""Recipes, loot tables and tags."""
import json, os
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data')
M = 'vehicleworks'

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: json.dump(obj, f, indent=2)

def ing(x):
    if x.startswith('#'): return {"tag": x[1:]}
    if ':' not in x: x = 'minecraft:' + x
    return {"item": x}

def shaped(name, pattern, key, count=1):
    w(os.path.join(ROOT, M, 'recipe', name + '.json'), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: ing(v) for k, v in key.items()}, "result": {"id": f"{M}:{name.split('__')[0]}", "count": count}})

def shapeless(name, items, count=1, result=None):
    w(os.path.join(ROOT, M, 'recipe', name + '.json'), {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [ing(i) for i in items], "result": {"id": f"{M}:{result or name.split('__')[0]}", "count": count}})

V = lambda n: f'{M}:{n}'
shaped('vehicle_core', [" C ", "IRI", "III"], {"C": "compass", "R": "redstone_block", "I": "iron_ingot"})
shaped('pilot_seat', ["W  ", "WWW", "I I"], {"W": "#minecraft:wool", "I": "iron_ingot"})
shaped('passenger_seat', ["P  ", "PPP", "I I"], {"P": "#minecraft:planks", "I": "iron_ingot"})
shaped('piston_engine', ["IPI", "PFP", "IRI"], {"I": "iron_ingot", "P": "piston", "F": "furnace", "R": "redstone"})
shaped('turboprop_engine', ["III", "CFC", "IEI"], {"I": "iron_ingot", "C": "copper_ingot", "F": "blast_furnace", "E": V('piston_engine')})
shaped('turbofan_engine', ["IGI", "CTC", "III"], {"I": "iron_ingot", "G": "gold_ingot", "C": "copper_block", "T": V('turboprop_engine')})
shaped('jet_engine', ["IBI", "BTB", "IFI"], {"I": "iron_ingot", "B": "blaze_rod", "T": V('turbofan_engine'), "F": "fire_charge"})
shaped('electric_motor', ["CCC", "CRC", "III"], {"C": "copper_ingot", "R": "redstone_block", "I": "iron_ingot"})
shaped('propeller', [" P ", "PIP", " P "], {"P": "#minecraft:planks", "I": "iron_ingot"})
shaped('marine_propeller', [" C ", "CIC", " C "], {"C": "copper_ingot", "I": "iron_ingot"})
shaped('main_rotor', ["III", " B ", " I "], {"I": "iron_ingot", "B": "iron_block"})
shaped('tail_rotor', [" I ", "IBI", " I "], {"I": "iron_ingot", "B": "iron_bars"})
shaped('engine_starter', ["IRI", "RLR", "IRI"], {"I": "iron_ingot", "R": "redstone", "L": "lightning_rod"})
shaped('fuel_tank', ["IBI", "I I", "IBI"], {"I": "iron_ingot", "B": "bucket"})
shaped('fuel_pump', ["IPI", "CRC", "III"], {"I": "iron_ingot", "P": "piston", "C": "copper_ingot", "R": "redstone"})
shaped('hydraulic_pump', ["IPI", "SRS", "III"], {"I": "iron_ingot", "P": "piston", "S": "slime_ball", "R": "redstone"})
shaped('wing', ["III", "PPP"], {"I": "iron_ingot", "P": "#minecraft:planks"}, 6)
shapeless('straight_wing', [V('wing'), 'paper'])
shapeless('swept_wing', [V('wing'), 'iron_ingot'])
shapeless('delta_wing', [V('wing'), V('wing'), 'iron_ingot'], 2)
shapeless('variable_sweep_wing', [V('swept_wing'), 'piston'])
shapeless('aileron', [V('wing'), 'redstone'])
shapeless('elevator', [V('wing'), 'redstone', 'redstone'])
shapeless('rudder', [V('wing'), 'lever'])
shapeless('flap', [V('wing'), 'piston'])
shapeless('spoiler', [V('wing'), 'iron_trapdoor'])
for wn in ['straight_wing', 'swept_wing', 'delta_wing', 'variable_sweep_wing', 'aileron', 'elevator', 'rudder', 'flap', 'spoiler']:
    shapeless('wing__from_' + wn, [V(wn)], 1, 'wing')
shaped('wheel', [" K ", "KIK", " K "], {"K": "dried_kelp_block", "I": "iron_ingot"}, 2)
shaped('landing_gear', ["I", "P", "W"], {"I": "iron_ingot", "P": "piston", "W": V('wheel')})
shaped('ballast_tank', ["IBI", "IWI", "III"], {"I": "iron_ingot", "B": "iron_bars", "W": "bucket"})
shaped('balloon_envelope', ["SWS", "WSW", "SWS"], {"S": "string", "W": "#minecraft:wool"}, 8)
shaped('gas_cell', [" P ", "PGP", " P "], {"P": "phantom_membrane", "G": "glass_bottle"}, 4)
shaped('burner', [" F ", "ICI", "III"], {"F": "flint_and_steel", "I": "iron_ingot", "C": "campfire"})
shaped('flight_instruments', ["GGG", "CRK", "III"], {"G": "glass_pane", "C": "compass", "R": "redstone", "K": "clock"})
shaped('navigation_computer', ["GGG", "MRC", "III"], {"G": "glass_pane", "M": "map", "R": "redstone", "C": "compass"})
shaped('weapon_mount', ["IBI", "B B", "IBI"], {"I": "iron_ingot", "B": "iron_bars"}, 2)
shaped('turret_base', ["IHI", "IRI", "III"], {"I": "iron_ingot", "H": "hopper", "R": "redstone"})
shaped('machine_gun', ["III", " D ", "IRI"], {"I": "iron_ingot", "D": "dispenser", "R": "redstone"})
shaped('cannon', ["BBB", "BDB", " T "], {"B": "iron_block", "D": "dispenser", "T": "tnt"})
shaped('missile_launcher', ["III", "DFD", "III"], {"I": "iron_ingot", "D": "dispenser", "F": "firework_rocket"})
shaped('bomb_bay', ["ITI", "I I", "ITI"], {"I": "iron_ingot", "T": "iron_trapdoor"})
shaped('ammo_box', ["ICI", "III"], {"I": "iron_ingot", "C": "chest"})
shaped('remote_control_unit', [" A ", "GRG", "III"], {"A": "lightning_rod", "G": "gold_ingot", "R": "redstone_block", "I": "iron_ingot"})
shaped('engineering_wrench', ["  I", " S ", "S  "], {"I": "iron_ingot", "S": "stick"})
shaped('fuel_canister', [" I ", "ICI", "III"], {"I": "iron_ingot", "C": "coal_block"}, 4)
shapeless('fuel_canister__from_lava', ['lava_bucket', 'iron_ingot'], 2, 'fuel_canister')
shaped('ammo_belt', ["GG", "NN"], {"G": "gunpowder", "N": "iron_nugget"}, 2)
shaped('cannon_shell', ["I", "T", "G"], {"I": "iron_ingot", "T": "tnt", "G": "gold_nugget"}, 4)
shaped('missile', [" I ", "IFI", "ITI"], {"I": "iron_ingot", "F": "firework_rocket", "T": "tnt"})
shaped('aerial_bomb', [" I ", "ITI", " I "], {"I": "iron_ingot", "T": "tnt"})

components = [l.split('"')[1] for l in open(os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'java', 'com', 'vehicleworks', 'component', 'ComponentType.java'))
              if l.startswith('    ') and '("' in l and l.strip()[0].isupper() and l.strip().split('(')[0].isupper()]
for c in components:
    w(os.path.join(ROOT, M, 'loot_table', 'blocks', c + '.json'), {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
        "entries": [{"type": "minecraft:item", "name": f"{M}:{c}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
soft = {'balloon_envelope', 'gas_cell', 'pilot_seat', 'passenger_seat'}
w(os.path.join(ROOT, 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), {"replace": False, "values": [f"{M}:{c}" for c in components if c not in soft]})
w(os.path.join(ROOT, M, 'tags', 'block', 'assembly_excluded.json'), {"replace": False, "values": []})
print('data ok:', len(components), 'components')
