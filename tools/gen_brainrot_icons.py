"""Item pictures for the Brainrot Heist guide: one card per brainrot and one per mutation (rendered from the voxel models),
plus their item models, block models of the shop/guide stations, the world type and the English names.
Run from the project root after gen_brainrots.py and gen_brainrot_textures.py:  python3 tools/gen_brainrot_icons.py"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from preview_brainrots import render
from PIL import Image

A = 'src/main/resources/assets/eggsteal/'
J = 'src/main/java/com/eggsteal/brainrot/'
os.makedirs(A + 'textures/item/br', exist_ok=True)


def brainrots():
    src = open(J + 'Brainrot.java').read()
    return re.findall(r'add\(l, "(\w+)", "([^"]+)", (\d)', src)


def mutations():
    src = open(J + 'BrMutation.java').read()
    out = []
    for mt in re.finditer(r'm\((\d+), "(\w+)", "([^"]+)", "[^"]*", [\d.]+, [\d.]+, (0x[0-9a-f]+|0), (0x[0-9a-f]+|0), (0x[0-9a-f]+|0), "(\w+)", (?:"\w+"|null), (?:0x[0-9a-f]+|0), (\w+),', src):
        sp = {'NORMAL': 0, 'RAINBOW': 1, 'GLITCH': 2, 'YIN_YANG': 3, 'HOLO': 4, 'GHOST': 5, 'FULLBRIGHT': 6}[mt.group(8)]
        out.append(dict(index=int(mt.group(1)), id=mt.group(2), name=mt.group(3), dark=int(mt.group(4), 16), mid=int(mt.group(5), 16),
                        light=int(mt.group(6), 16), pattern=mt.group(7), special=sp))
    return out


def icon(model, mut=None):
    im = render(A + 'models/brainrot/' + model + '.obj', size=192, yaw=-28, pitch=14, mut=mut, bg=(0, 0, 0, 0))
    return im.resize((64, 64), Image.LANCZOS)


lang = json.load(open(A + 'lang/en_us.json'))
for bid, name, rar in brainrots():
    icon(bid).save(A + f'textures/item/br/{bid}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"eggsteal:item/br/{bid}"}}, open(A + f'models/item/br_{bid}.json', 'w'))
    lang[f'item.eggsteal.br_{bid}'] = name
muts = mutations()
assert len(muts) == 36, len(muts)
for m in muts:
    icon('tostino', m if m['index'] > 0 else None).save(A + f'textures/item/br/mut_{m["id"]}.png')
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"eggsteal:item/br/mut_{m['id']}"}}, open(A + f'models/item/brmut_{m["id"]}.json', 'w'))
    lang[f'item.eggsteal.brmut_{m["id"]}'] = m['name'] + ' Mutation'
json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/book"}}, open(A + 'models/item/brainrot_guide.json', 'w'))
for blk, tex in (('brainrot_shop', 'minecraft:block/emerald_block'), ('brainrot_guide_station', 'minecraft:block/bookshelf')):
    json.dump({"variants": {"": {"model": f"eggsteal:block/{blk}"}}}, open(A + f'blockstates/{blk}.json', 'w'))
    json.dump({"parent": "minecraft:block/cube_all", "textures": {"all": tex}}, open(A + f'models/block/{blk}.json', 'w'))
    json.dump({"parent": f"eggsteal:block/{blk}"}, open(A + f'models/item/{blk}.json', 'w'))
lang.update({
    "itemGroup.eggsteal.brainrot": "Brainrot Heist",
    "item.eggsteal.brainrot_guide": "Brainrot Heist Guide",
    "block.eggsteal.brainrot_shop": "Brainrot Shop",
    "block.eggsteal.brainrot_guide_station": "Brainrot Guide",
    "entity.eggsteal.brainrot": "Brainrot",
    "entity.eggsteal.brainrot_bot": "Player",
    "generator.eggsteal.brainrot": "Brainrot Heist",
})
json.dump(lang, open(A + 'lang/en_us.json', 'w'), indent=1)
D = 'src/main/resources/data/'
json.dump({"dimensions": {"minecraft:overworld": {"type": "minecraft:overworld", "generator": {"type": "minecraft:flat", "settings": {
    "biome": "minecraft:meadow", "features": False, "lakes": False, "layers": [{"block": "minecraft:air", "height": 1}], "structure_overrides": []}}}}},
    open(D + 'eggsteal/worldgen/world_preset/brainrot.json', 'w'))
json.dump({"values": ["eggsteal:eggsteal", "eggsteal:brainrot"]}, open(D + 'minecraft/tags/worldgen/world_preset/normal.json', 'w'))
sheet = Image.new('RGBA', (64 * 13, 64 * 6), (60, 66, 80, 255))
files = [f'br/{b[0]}' for b in brainrots()] + [f'br/mut_{m["id"]}' for m in muts]
for i, f in enumerate(files):
    im = Image.open(A + 'textures/item/' + f + '.png')
    sheet.paste(im, ((i % 13) * 64, (i // 13) * 64), im)
sheet.save(sys.argv[1] if len(sys.argv) > 1 else 'build/icons.png')
print(len(files), 'icons')
