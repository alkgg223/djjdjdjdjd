"""Generates every asset and data file of the mod. Run: python3 tools/gen.py"""
import os, json, sys, shutil
sys.path.insert(0, os.path.dirname(__file__))
import gen_items, gen_blocks, gen_entity, gen_sounds, lang

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
ASSETS = os.path.join(ROOT, 'assets', 'rpbusiness')
DATA = os.path.join(ROOT, 'data', 'rpbusiness')
MC = os.path.join(ROOT, 'data', 'minecraft')


def wj(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=1, ensure_ascii=False)


def I(x):
    return {'tag': x[1:]} if x.startswith('#') else {'item': x if ':' in x else 'minecraft:' + x}


def shaped(name, pattern, key, count=1):
    wj(os.path.join(DATA, 'recipe', name + '.json'), {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
        'key': {k: I(v) for k, v in key.items()}, 'result': {'id': 'rpbusiness:' + name, 'count': count}})


def shapeless(name, ings, count=1, suffix=''):
    wj(os.path.join(DATA, 'recipe', name + suffix + '.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [I(x) for x in ings],
        'result': {'id': 'rpbusiness:' + name, 'count': count}})


def recipes():
    i, p, s = 'iron_ingot', '#minecraft:planks', 'stick'
    shaped('kitchen_counter', ['QQQ', 'P P', 'PPP'], {'Q': 'smooth_quartz', 'P': p}, 2)
    shaped('counter_cabinet', ['QQQ', 'PCP', 'PPP'], {'Q': 'smooth_quartz', 'P': p, 'C': 'chest'})
    shaped('cutting_board', ['WWW'], {'W': '#minecraft:wooden_slabs'}, 2)
    shaped('grill', ['BBB', 'ICI', 'III'], {'B': 'iron_bars', 'I': i, 'C': 'campfire'})
    shaped('fryer', ['I I', 'ICI', 'IFI'], {'I': i, 'C': 'cauldron', 'F': 'furnace'})
    shaped('oven', ['III', 'IGI', 'IFI'], {'I': i, 'G': 'glass_pane', 'F': 'furnace'})
    shaped('cooking_pot', ['I I', 'IWI', 'III'], {'I': i, 'W': 'water_bucket'})
    shaped('prep_station', ['SSS', 'ITI', 'III'], {'S': 'smooth_stone_slab', 'I': i, 'T': 'crafting_table'})
    shaped('drink_machine', ['IRI', 'IGI', 'IBI'], {'I': i, 'R': 'red_dye', 'G': 'glass', 'B': 'bucket'})
    shaped('fridge', ['ISI', 'ICI', 'IRI'], {'I': i, 'S': 'snow_block', 'C': 'chest', 'R': 'redstone'})
    shaped('freezer', ['SSS', 'ICI', 'III'], {'I': i, 'S': 'snow_block', 'C': 'chest'})
    shaped('pantry_shelf', ['PPP', 'SBS', 'PPP'], {'P': p, 'S': s, 'B': 'barrel'})
    shaped('serving_counter', ['III', 'P P', 'PPP'], {'I': i, 'P': p})
    shaped('dining_table', ['SSS', ' F ', ' F '], {'S': '#minecraft:wooden_slabs', 'F': '#minecraft:wooden_fences'})
    shaped('dining_chair', ['P  ', 'PPP', 'S S'], {'P': p, 'S': s}, 2)
    shaped('bar_stool', ['WWW', ' I ', ' I '], {'W': '#minecraft:wool', 'I': i}, 2)
    shaped('sofa', ['W  ', 'WWW', 'PPP'], {'W': '#minecraft:wool', 'P': p}, 2)
    shaped('pendant_lamp', [' C ', 'NGN'], {'C': 'chain', 'N': 'iron_nugget', 'G': 'glowstone'}, 2)
    shaped('menu_board', ['PPP', 'PDP', 'S S'], {'P': p, 'D': 'black_dye', 'S': s})
    shaped('potted_plant', [' L ', 'LLL', ' F '], {'L': '#minecraft:leaves', 'F': 'flower_pot'})
    shaped('checkered_floor', ['WB', 'BW'], {'W': 'white_concrete', 'B': 'black_concrete'}, 4)
    shaped('kitchen_tiles', ['WT', 'TW'], {'W': 'white_concrete', 'T': 'white_terracotta'}, 4)
    shaped('cash_register', ['BBB', 'IRI', 'III'], {'B': 'stone_button', 'I': i, 'R': 'redstone'})
    shaped('payment_terminal', ['IGI', 'IRI', ' I '], {'I': i, 'G': 'glass_pane', 'R': 'redstone'})
    shaped('business_computer', ['III', 'IGI', 'SRS'], {'I': i, 'G': 'glass_pane', 'S': 'stone_slab', 'R': 'redstone'})
    shaped('supplier_terminal', ['IPI', 'IGI', 'IRI'], {'I': i, 'P': 'paper', 'G': 'glass_pane', 'R': 'redstone'})
    shaped('delivery_crate', ['PSP', 'SCS', 'PSP'], {'P': p, 'S': s, 'C': 'chest'})
    shaped('package', ['PPP', 'P P', 'PPP'], {'P': 'paper'}, 2)
    shaped('kitchen_knife', [' I', 'S '], {'I': i, 'S': s})
    shaped('spatula', ['  I', ' S ', 'S  '], {'I': i, 'S': s})
    shaped('mop', ['  S', ' S ', 'W  '], {'S': s, 'W': '#minecraft:wool'})
    shaped('paper_cup', ['P P', ' P '], {'P': 'paper'}, 4)
    shaped('display_shelf', ['PPP', 'S S', 'PPP'], {'P': '#minecraft:wooden_slabs', 'S': s})
    shaped('fridge_display', ['IGI', 'ISI', 'IRI'], {'I': i, 'G': 'glass_pane', 'S': 'snow_block', 'R': 'redstone'})
    shaped('bank_counter', ['GGG', 'QQQ', 'PCP'], {'G': 'glass_pane', 'Q': 'smooth_quartz', 'P': p, 'C': 'chest'})
    shaped('bank_computer', ['III', 'IGI', 'SES'], {'I': i, 'G': 'glass_pane', 'S': 'stone_slab', 'E': 'emerald'})
    shaped('atm', ['IGI', 'IEI', 'IRI'], {'I': i, 'G': 'glass_pane', 'E': 'emerald', 'R': 'redstone'})
    shaped('safe', ['III', 'IBI', 'III'], {'I': i, 'B': 'iron_block'})
    shaped('delivery_terminal', ['III', 'IGI', 'SCS'], {'I': i, 'G': 'glass_pane', 'S': 'stone_slab', 'C': 'chest'})
    shaped('hotel_bed', ['WWW', 'PPP'], {'W': 'white_wool', 'P': p})
    shaped('shopping_basket', ['S S', 'RRR'], {'S': 'stick', 'R': 'red_dye'})
    shaped('shopping_cart', ['I  ', 'BBB', 'N N'], {'I': i, 'B': 'iron_bars', 'N': 'iron_nugget'})
    shapeless('business_planner', ['paper', 'paper', 'ink_sac', 'stick'])
    shapeless('staff_planner', ['paper', 'paper', 'ink_sac', 'string'])
    shapeless('layout_tool', ['paper', 'ink_sac', 'iron_ingot', 'redstone'])
    shapeless('recipe_book', ['book', 'wheat'])
    shapeless('raw_beef', ['beef'])
    shapeless('potato', ['minecraft:potato'])
    shapeless('bread_loaf', ['bread'])
    shapeless('burger_bun', ['wheat', 'wheat', 'egg'], 2)
    shapeless('pizza_dough', ['wheat', 'wheat', 'water_bucket'], 2)
    shapeless('cheese_block', ['milk_bucket'], 2)
    shapeless('tomato', ['beetroot', 'sweet_berries'], 2)
    shapeless('onion', ['beetroot', 'carrot'])
    shapeless('lettuce', ['kelp', 'wheat_seeds'])
    shapeless('cola_syrup', ['sugar', 'sugar', 'sweet_berries', 'glass_bottle'])
    shapeless('coffee_beans', ['cocoa_beans', 'cocoa_beans'])


PICKAXE = ['fridge_display', 'bank_counter', 'bank_computer', 'atm', 'safe', 'delivery_terminal', 'kitchen_counter', 'grill', 'fryer', 'oven', 'cooking_pot', 'prep_station', 'drink_machine', 'fridge', 'freezer', 'serving_counter',
           'bar_stool', 'pendant_lamp', 'checkered_floor', 'kitchen_tiles', 'cash_register', 'payment_terminal', 'business_computer', 'supplier_terminal']
AXE = ['hotel_bed', 'display_shelf', 'counter_cabinet', 'cutting_board', 'pantry_shelf', 'dining_table', 'dining_chair', 'sofa', 'menu_board', 'potted_plant', 'delivery_crate', 'package']


def data(blocks):
    import gen_decor
    PICKAXE.extend(gen_decor.PICKAXE); AXE.extend(gen_decor.AXE)
    for b in blocks:
        if b == 'room_mess':
            wj(os.path.join(DATA, 'loot_table', 'blocks', b + '.json'), {'type': 'minecraft:block', 'pools': []})
            continue
        wj(os.path.join(DATA, 'loot_table', 'blocks', b + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'rpbusiness:' + b}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    wj(os.path.join(MC, 'tags', 'block', 'mineable', 'pickaxe.json'), {'replace': False, 'values': ['rpbusiness:' + x for x in PICKAXE]})
    wj(os.path.join(MC, 'tags', 'block', 'mineable', 'axe.json'), {'replace': False, 'values': ['rpbusiness:' + x for x in AXE]})
    recipes()


def main():
    for d in (os.path.join(ASSETS, 'textures'), os.path.join(ASSETS, 'models'), os.path.join(ASSETS, 'blockstates'), os.path.join(ASSETS, 'geo'),
              os.path.join(ASSETS, 'animations'), os.path.join(ASSETS, 'sounds'), DATA, os.path.join(MC, 'tags')):
        shutil.rmtree(d, ignore_errors=True)
    items = gen_items.generate(os.path.join(ASSETS, 'textures', 'item'))
    for it in items:
        wj(os.path.join(ASSETS, 'models', 'item', it + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'rpbusiness:item/' + it}})
    for tool in ('kitchen_knife', 'spatula', 'mop'):
        wj(os.path.join(ASSETS, 'models', 'item', tool + '.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': 'rpbusiness:item/' + tool}})
    blocks = gen_blocks.generate(ASSETS)
    gen_entity.generate(ASSETS)
    sj = gen_sounds.generate(ASSETS)
    wj(os.path.join(ASSETS, 'sounds.json'), sj)
    wj(os.path.join(ASSETS, 'lang', 'en_us.json'), lang.E)
    wj(os.path.join(ASSETS, 'lang', 'ar_sa.json'), lang.A)
    wj(os.path.join(ASSETS, 'lang', 'ar_iq.json'), lang.A)
    data(blocks)
    print('items', len(items), 'blocks', len(blocks), 'lang', len(lang.E))


if __name__ == '__main__':
    main()
