"""Blockstates (multipart: the master part shows the piece model rotated to its facing), particle models, recipes and
translations for the structure pieces.

    python3 building_data.py <resources dir>
"""
import json, os, sys

R = sys.argv[1]
A = R + "/assets/arkragnarok"
D = R + "/data/arkragnarok"
TIERS = ("thatch", "wood", "stone")
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(obj, open(path, "w"), indent=1, ensure_ascii=False)


for t in TIERS:
    mp = [{"apply": {"model": "arkragnarok:block/%s_particle" % t}}]
    mp.append({"when": {"piece": "foundation", "part": "4"}, "apply": {"model": "arkragnarok:block/%s_foundation" % t}})
    for f, y in ROT.items():
        mp.append({"when": {"piece": "ceiling", "part": "4", "facing": f}, "apply": {"model": "arkragnarok:block/%s_ceiling" % t, "y": y}})
        for pc in ("wall", "doorframe"):
            mp.append({"when": {"piece": pc, "part": "1", "facing": f}, "apply": {"model": "arkragnarok:block/%s_%s" % (t, pc), "y": y}})
    dump(A + "/blockstates/%s_piece.json" % t, {"multipart": mp})
    dump(A + "/models/block/%s_particle.json" % t, {"textures": {"particle": "arkragnarok:block/piece/%s_wall" % t}})

M, K = "minecraft:", "arkragnarok:"


def sl(name, ings, count=1):
    dump(D + "/recipe/%s.json" % name, {"type": "minecraft:crafting_shapeless", "category": "building",
                                       "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in ings],
                                       "result": {"id": K + name, "count": count}})


sl("thatch_foundation", [M + "stick"] * 4 + [M + "wheat"] * 3 + ["#minecraft:logs"])
sl("thatch_wall", [M + "stick"] * 3 + [M + "wheat"] * 3)
sl("thatch_doorframe", [M + "stick"] * 3 + [M + "wheat"] * 2)
sl("thatch_door", [M + "stick"] * 2 + [M + "wheat"] * 2)
sl("thatch_ceiling", [M + "stick"] * 3 + [M + "wheat"] * 3 + [M + "string"])
sl("wood_foundation", ["#minecraft:logs"] * 2 + ["#minecraft:planks"] * 4 + [M + "iron_nugget"])
sl("wood_wall", ["#minecraft:planks"] * 4 + [M + "stick"] * 2)
sl("wood_doorframe", ["#minecraft:planks"] * 3 + [M + "stick"] * 2)
sl("wood_door", ["#minecraft:planks"] * 2 + [M + "stick", M + "iron_nugget"])
sl("wood_ceiling", ["#minecraft:planks"] * 4 + [M + "stick"] * 2)
sl("stone_foundation", [M + "stone_bricks"] * 4 + [M + "cobblestone"] * 4 + ["#minecraft:planks"])
sl("stone_wall", [M + "stone_bricks"] * 3 + [M + "cobblestone"] * 3)
sl("stone_doorframe", [M + "stone_bricks"] * 3 + [M + "cobblestone"] * 2)
sl("stone_door", ["#minecraft:planks"] * 2 + [M + "iron_ingot"] * 2)
sl("stone_ceiling", [M + "stone_bricks"] * 2 + [M + "cobblestone"] * 2 + ["#minecraft:planks"] * 2)

EN = {"msg.arkragnarok.no_room": "Not enough room for this piece", "msg.arkragnarok.need_doorframe": "Use the door on a doorframe",
      "item.arkragnarok.piece.foundation.desc": "3x3 floor; walls snap to its edges",
      "item.arkragnarok.piece.wall.desc": "3x3 wall; aim at a foundation edge or on top of a wall",
      "item.arkragnarok.piece.doorframe.desc": "Wall with a door opening; put a door in it",
      "item.arkragnarok.piece.ceiling.desc": "3x3 ceiling / upper floor; aim at the top of a wall"}
AR = {"msg.arkragnarok.no_room": "لا توجد مساحة كافية لهذه القطعة", "msg.arkragnarok.need_doorframe": "استخدم الباب على إطار باب",
      "item.arkragnarok.piece.foundation.desc": "أرضية 3×3؛ الجدران تلتصق بحوافها",
      "item.arkragnarok.piece.wall.desc": "جدار 3×3؛ وجّهه إلى حافة أساس أو فوق جدار",
      "item.arkragnarok.piece.doorframe.desc": "جدار بفتحة باب؛ ضع فيه باباً",
      "item.arkragnarok.piece.ceiling.desc": "سقف / طابق علوي 3×3؛ وجّهه إلى أعلى الجدار"}
NAMES = {"thatch": ("Thatch", "قش"), "wood": ("Wooden", "خشبي"), "stone": ("Stone", "حجري")}
PN = {"foundation": ("Foundation", "أساس"), "wall": ("Wall", "جدار"), "doorframe": ("Doorframe", "إطار باب"), "door": ("Door", "باب"),
      "ceiling": ("Ceiling", "سقف")}
for t in TIERS:
    for p, (en, ar) in PN.items():
        if t == "stone" and p == "door":
            EN["item.arkragnarok.stone_door"] = "Reinforced Door"
            AR["item.arkragnarok.stone_door"] = "باب مدعّم"
            continue
        EN["item.arkragnarok.%s_%s" % (t, p)] = "%s %s" % (NAMES[t][0], en)
        AR["item.arkragnarok.%s_%s" % (t, p)] = "%s %s" % (ar, NAMES[t][1])
    EN["block.arkragnarok.%s_piece" % t] = "%s Structure" % NAMES[t][0]
    AR["block.arkragnarok.%s_piece" % t] = "بناء %s" % NAMES[t][1]
for lang, add in (("en_us", EN), ("ar_sa", AR)):
    p = A + "/lang/%s.json" % lang
    d = json.load(open(p))
    d.update(add)
    dump(p, d)
print("building data written")
