"""Data of the mod economy: blockstates of stations / storage / bed / crop plot, block loot tables, species loot (mod
resources), item tags (tools, arrows), crafting-table recipes of primitive items and thatch / wood structures, names.

    python3 gen_economy_data.py <resources dir>
"""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import dinos

R = sys.argv[1]
A = R + "/assets/arkragnarok"
D = R + "/data"
K = "arkragnarok:"


def dump(p, o):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(o, open(p, "w"), indent=1, ensure_ascii=False)


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
STATIONS = {"campfire": 0, "mortar_and_pestle": 0, "smithy": 0, "refining_forge": 270, "fabricator": 0, "storage_box": 0, "large_storage_box": 0}
for b, extra in STATIONS.items():
    dump(A + "/blockstates/%s.json" % b, {"variants": {"facing=%s" % f: {"model": K + "block/" + b, "y": (y + extra) % 360} for f, y in ROT.items()}})
BED = {"south": 0, "west": 90, "north": 180, "east": 270}
dump(A + "/blockstates/hide_bed.json", {"variants": {"facing=%s,part=%s" % (f, p): {"model": K + "block/hide_bed_" + p, "y": y}
                                                     for f, y in BED.items() for p in ("head", "foot")}})
dump(A + "/blockstates/crop_plot.json", {"variants": {"age=%d" % a: {"model": K + "block/crop_plot_%d" % a} for a in range(3)}})

for b in list(STATIONS) + ["crop_plot"]:
    dump(D + "/arkragnarok/loot_table/blocks/%s.json" % b, {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": K + b}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
dump(D + "/arkragnarok/loot_table/blocks/hide_bed.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
    {"type": "minecraft:item", "name": K + "hide_bed", "conditions": [{"condition": "minecraft:block_state_property", "block": K + "hide_bed",
                                                                        "properties": {"part": "head"}}]}]}]})


def count(item, lo, hi, chance=None):
    pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}]}
    if chance is not None:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


HORNED = {"triceratops", "pachyrhinosaurus", "stegosaurus", "ankylosaurus", "pachycephalosaurus", "carnotaurus", "moschops"}
for sid, spec in dinos.ALL.items():
    L = spec["length_m"]
    meat = max(1, round(L * 1.3))
    hide = max(1, round(L * 0.9))
    pools = [count(K + "raw_meat", max(1, meat // 2), meat), count(K + "hide", max(1, hide // 2), hide),
             count(K + "dino_bone", 1, max(1, round(L / 3)))]
    if L >= 4:
        pools.append(count(K + "raw_prime_meat", 1, max(1, round(L / 2.5))))
    else:
        pools.append(count(K + "raw_prime_meat", 1, 1, chance=0.25))
    if sid in HORNED:
        pools.append(count(K + "keratin", 1, max(2, round(L / 1.5))))
    if spec["plan"] == "theropod" and sid not in ("dodo", "oviraptor", "gallimimus", "therizinosaurus"):
        pools.append(count(K + "dino_tooth", 1, max(1, round(L / 3))))
    if sid == "dodo":
        pools.append(count("minecraft:feather", 1, 3))
        pools.append(count("minecraft:egg", 0, 1))
    dump(D + "/arkragnarok/loot_table/entities/%s.json" % sid, {"type": "minecraft:entity", "pools": pools})

for tag, items in (("pickaxes", ["stone_pick", "metal_pick"]), ("axes", ["stone_hatchet", "metal_hatchet"]),
                   ("swords", ["metal_sickle", "wooden_club", "stone_spear", "metal_sword"]), ("arrows", ["tranq_arrow", "stone_arrow"])):
    dump(D + "/minecraft/tags/item/%s.json" % tag, {"replace": False, "values": [K + i for i in items]})


def sl(name, ings, count=1, result=None):
    flat = []
    for it, n in ings:
        flat += [it] * n
    assert len(flat) <= 9, name
    dump(D + "/arkragnarok/recipe/%s.json" % name, {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                   "ingredients": [{"item": K + i if ":" not in i else i} for i in flat],
                                                   "result": {"id": K + (result or name), "count": count}})


sl("stone_pick", [("wood", 1), ("stone", 1), ("thatch", 3)])
sl("stone_hatchet", [("wood", 1), ("flint", 1), ("stone", 1), ("thatch", 2)])
sl("wooden_club", [("wood", 4), ("fiber", 2)])
sl("stone_spear", [("wood", 2), ("flint", 2), ("fiber", 2)])
sl("primitive_bow", [("wood", 2), ("fiber", 5)])
sl("stone_arrow", [("thatch", 2), ("flint", 1), ("fiber", 2)], count=2)
sl("campfire", [("thatch", 3), ("flint", 1), ("stone", 3), ("wood", 2)])
sl("mortar_and_pestle", [("stone", 6), ("hide", 2)])
sl("smithy", [("stone", 3), ("wood", 3), ("hide", 3)])
sl("storage_box", [("wood", 5), ("thatch", 3), ("fiber", 1)])
sl("hide_bed", [("wood", 3), ("hide", 3), ("fiber", 2), ("thatch", 1)])
sl("crop_plot", [("wood", 3), ("thatch", 2), ("stone", 2), ("fiber", 2)])
# structures made by hand: thatch and wood (stone and iron at the smithy, steel at the fabricator)
PIECES = {"thatch": {"foundation": [("wood", 2), ("thatch", 5), ("fiber", 2)], "wall": [("wood", 2), ("thatch", 5), ("fiber", 2)],
                     "doorframe": [("wood", 2), ("thatch", 5), ("fiber", 2)], "ceiling": [("wood", 2), ("thatch", 5), ("fiber", 2)],
                     "door": [("wood", 1), ("thatch", 4), ("fiber", 1)]},
          "wood": {"foundation": [("wood", 6), ("thatch", 2), ("fiber", 1)], "wall": [("wood", 6), ("thatch", 2), ("fiber", 1)],
                   "doorframe": [("wood", 6), ("thatch", 2), ("fiber", 1)], "ceiling": [("wood", 6), ("thatch", 2), ("fiber", 1)],
                   "door": [("wood", 4), ("thatch", 1), ("fiber", 1)]}}
for t, ps in PIECES.items():
    for p, ings in ps.items():
        sl("%s_%s" % (t, p), ings)
for old in ("stone_foundation", "stone_wall", "stone_doorframe", "stone_door", "stone_ceiling"):
    p = D + "/arkragnarok/recipe/%s.json" % old
    if os.path.exists(p):
        os.remove(p)

EN = {"wood": "Wood", "thatch": "Thatch", "fiber": "Fiber", "stone": "Stone", "flint": "Flint", "metal_ore": "Metal Ore", "crystal": "Crystal",
      "obsidian": "Obsidian", "hide": "Hide", "keratin": "Keratin", "dino_bone": "Dinosaur Bone", "dino_tooth": "Dinosaur Tooth",
      "raw_meat": "Raw Meat", "cooked_meat": "Cooked Meat", "charcoal": "Charcoal", "metal_ingot": "Metal Ingot", "steel_ingot": "Steel Ingot",
      "sparkpowder": "Sparkpowder", "gunpowder": "Gunpowder", "cementing_paste": "Cementing Paste", "polymer": "Polymer",
      "electronics": "Electronics", "stone_pick": "Stone Pick", "metal_pick": "Metal Pick", "stone_hatchet": "Stone Hatchet",
      "metal_hatchet": "Metal Hatchet", "metal_sickle": "Metal Sickle", "wooden_club": "Wooden Club", "stone_spear": "Stone Spear",
      "metal_sword": "Metal Sword", "primitive_bow": "Primitive Bow", "stone_arrow": "Stone Arrow"}
AR = {"wood": "خشب", "thatch": "قش", "fiber": "ألياف", "stone": "حجر", "flint": "صوان", "metal_ore": "خام معدن", "crystal": "بلور",
      "obsidian": "سبج", "hide": "جلد", "keratin": "كيراتين", "dino_bone": "عظم ديناصور", "dino_tooth": "سن ديناصور",
      "raw_meat": "لحم نيء", "cooked_meat": "لحم مطبوخ", "charcoal": "فحم", "metal_ingot": "سبيكة معدن", "steel_ingot": "سبيكة فولاذ",
      "sparkpowder": "مسحوق الشرر", "gunpowder": "بارود", "cementing_paste": "معجون الإسمنت", "polymer": "بوليمر",
      "electronics": "إلكترونيات", "stone_pick": "معول حجري", "metal_pick": "معول معدني", "stone_hatchet": "فأس حجرية",
      "metal_hatchet": "فأس معدنية", "metal_sickle": "منجل معدني", "wooden_club": "هراوة خشبية", "stone_spear": "رمح حجري",
      "metal_sword": "سيف معدني", "primitive_bow": "قوس بدائي", "stone_arrow": "سهم حجري"}
BLK_EN = {"campfire": "Campfire", "mortar_and_pestle": "Mortar and Pestle", "smithy": "Smithy", "refining_forge": "Refining Forge",
          "fabricator": "Fabricator", "storage_box": "Storage Box", "large_storage_box": "Large Storage Box", "crop_plot": "Crop Plot",
          "hide_bed": "Hide Bed"}
BLK_AR = {"campfire": "نار المخيم", "mortar_and_pestle": "الهاون والمدقة", "smithy": "الحدادة", "refining_forge": "فرن الصهر",
          "fabricator": "المصنّع", "storage_box": "صندوق تخزين", "large_storage_box": "صندوق تخزين كبير", "crop_plot": "حوض زراعة",
          "hide_bed": "سرير جلدي"}
en, ar = {}, {}
for k, v in EN.items():
    en["item.arkragnarok." + k] = v
    ar["item.arkragnarok." + k] = AR[k]
for k, v in BLK_EN.items():
    en["block.arkragnarok." + k] = v
    ar["block.arkragnarok." + k] = BLK_AR[k]
    en["item.arkragnarok." + k] = v
    ar["item.arkragnarok." + k] = BLK_AR[k]
for tier, (te, ta) in {"iron": ("Iron", "حديدي"), "steel": ("Steel", "فولاذي")}.items():
    for p, (pe, pa) in {"foundation": ("Foundation", "أساس"), "wall": ("Wall", "جدار"), "doorframe": ("Doorframe", "إطار باب"),
                        "door": ("Door", "باب"), "ceiling": ("Ceiling", "سقف")}.items():
        en["item.arkragnarok.%s_%s" % (tier, p)] = "%s %s" % (te, pe)
        ar["item.arkragnarok.%s_%s" % (tier, p)] = "%s %s" % (pa, ta)
    en["block.arkragnarok.%s_piece" % tier] = "%s Structure" % te
    ar["block.arkragnarok.%s_piece" % tier] = "بناء %s" % ta
for lang, add in (("en_us", en), ("ar_sa", ar)):
    p = A + "/lang/%s.json" % lang
    d = json.load(open(p))
    d.update(add)
    dump(p, d)
print("economy data written")
