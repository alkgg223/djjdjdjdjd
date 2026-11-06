"""Taming content data: item textures (16x16 pixel art), tranq arrow entity texture, item models, recipes, loot tables
(meat, prime meat, hide per species), the arrows tag and en/ar translations.

    python3 gen_taming.py ../../src/main/resources
"""
import json, math, os, sys, random
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "hero"))
import dinos

R = sys.argv[1]
A = R + "/assets/arkragnarok"
D = R + "/data"


def hx(c):
    c = c.lstrip("#")
    return np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], float)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(path)


def canvas(n=16):
    return np.zeros((n, n, 4), float)


def disc(img, cx, cy, r, col, hl=None, rim=None):
    n = img.shape[0]
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                c = np.array(col, float)
                shade = 1.0 - 0.35 * max(0.0, ((x + 0.5 - cx) + (y + 0.5 - cy)) / (r * 1.6))
                c = c * shade
                if hl is not None and math.hypot(x + 0.5 - (cx - r * 0.35), y + 0.5 - (cy - r * 0.35)) < r * 0.33:
                    c = np.array(hl, float)
                if rim is not None and d > r - 0.8:
                    c = np.array(rim, float)
                img[y, x, :3] = c
                img[y, x, 3] = 255


def outline(img, col=(30, 22, 16)):
    a = img[:, :, 3] > 0
    n = img.shape[0]
    out = img.copy()
    for y in range(n):
        for x in range(n):
            if a[y, x]:
                continue
            for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < n and 0 <= xx < n and a[yy, xx]:
                    out[y, x, :3] = col
                    out[y, x, 3] = 255
                    break
    return out


KIBBLE = [("basic_kibble", "#d8c08a", "Basic Kibble", "كبل أساسي"), ("simple_kibble", "#8fbf5a", "Simple Kibble", "كبل بسيط"),
          ("regular_kibble", "#5f8fd0", "Regular Kibble", "كبل عادي"), ("superior_kibble", "#e08a3a", "Superior Kibble", "كبل ممتاز"),
          ("exceptional_kibble", "#9a5ad0", "Exceptional Kibble", "كبل استثنائي"),
          ("extraordinary_kibble", "#f0d24a", "Extraordinary Kibble", "كبل خارق")]


def kibble_tex(col):
    img = canvas()
    rnd = random.Random(hash(col) & 0xffff)
    c = hx(col)
    pellets = [(8, 11, 3.0), (4.5, 11.5, 2.6), (11.5, 11.5, 2.6), (6.5, 8, 2.6), (10, 7.8, 2.6), (8, 5, 2.4), (3.5, 8.5, 2.0),
               (12.5, 8.5, 2.0)]
    for (px, py, r) in sorted(pellets, key=lambda t: t[1]):
        k = c * (0.85 + 0.3 * rnd.random())
        disc(img, px, py, r, k, hl=np.minimum(k * 1.35 + 30, 255), rim=k * 0.55)
    return outline(img)


def berries_tex(main, dark, leaf):
    img = canvas()
    for (px, py, r) in ((5.5, 10.5, 2.6), (9.5, 11.5, 2.6), (7.5, 7.5, 2.6), (11.5, 8, 2.4), (4.5, 6.5, 2.0)):
        disc(img, px, py, r, hx(main), hl=hx(main) * 0.4 + 180, rim=hx(dark))
    for (x, y) in ((8, 2), (9, 3), (10, 3), (11, 2), (8, 3), (7, 4), (8, 4), (9, 4)):
        img[y, x, :3] = hx(leaf)
        img[y, x, 3] = 255
    for y in range(4, 7):
        img[y, 8, :3] = hx("#4a3a20")
        img[y, 8, 3] = 255
    return outline(img)


def narcotic_tex():
    img = canvas()
    # small corked glass vial with dark purple liquid
    for y in range(3, 15):
        for x in range(5, 11):
            w = 2.5 if y < 6 else 3.0
            if abs(x + 0.5 - 8) <= w:
                glass = np.array([200, 215, 225], float)
                liquid = hx("#4a2a6a") * (0.8 + 0.4 * (x - 5) / 6)
                img[y, x, :3] = liquid if y >= 8 else glass * 0.9
                img[y, x, 3] = 255 if y >= 8 else 170
    for y in range(1, 4):
        for x in range(6, 10):
            img[y, x, :3] = hx("#8a6a40")
            img[y, x, 3] = 255
    img[9, 6, :3] = (230, 230, 240)
    img[10, 6, :3] = (210, 210, 225)
    return outline(img)


def meat_tex(cooked):
    img = canvas()
    base = hx("#7a4a2a") if cooked else hx("#b8323a")
    fat = hx("#c8a070") if cooked else hx("#f0d8c8")
    bone = hx("#ece2c6")
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.5, (y + 0.5 - 8.5) / 5.0
            if dx * dx + dy * dy <= 1.0:
                c = base * (0.85 + 0.25 * math.sin(x * 1.3 + y * 0.7))
                if not cooked and math.sin(x * 1.7 + y * 2.3) > 0.82:
                    c = fat
                if cooked and (x + y) % 5 == 0:
                    c = base * 0.55
                if dx * dx + dy * dy > 0.78:
                    c = fat * (0.9 if not cooked else 0.75)
                img[y, x, :3] = c
                img[y, x, 3] = 255
    for (x, y) in ((13, 3), (14, 3), (13, 4), (14, 4), (12, 5), (12, 4)):
        img[y, x, :3] = bone
        img[y, x, 3] = 255
    return outline(img)


def arrow_tex():
    img = canvas()
    for i in range(2, 13):          # shaft bottom-left -> top-right
        img[15 - i, i, :3] = hx("#7a5a3a")
        img[15 - i, i, 3] = 255
    for (x, y) in ((1, 13), (2, 14), (1, 14), (3, 14), (1, 12), (2, 12), (3, 13)):   # fletching
        img[y, x, :3] = hx("#e8e0d0")
        img[y, x, 3] = 255
    for (x, y, c) in ((12, 3, "#c8d8e0"), (13, 2, "#3a5ad0"), (12, 2, "#6a8ae8"), (13, 3, "#2a3a8a"), (14, 1, "#b0b8c0"),
                      (11, 3, "#3a5ad0"), (12, 4, "#3a5ad0")):
        img[y, x, :3] = hx(c)
        img[y, x, 3] = 255
    return outline(img)


def spyglass_tex():
    img = canvas()
    for i in range(3, 14):
        r = 1.6 if i < 7 else 1.2
        for d in np.linspace(-r, r, 7):
            x, y = int(round(i + d * 0.7)), int(round(16 - i + d * 0.7))
            if 0 <= x < 16 and 0 <= y < 16:
                g = hx("#d9a441") * (1.1 - 0.25 * (d + r) / (2 * r))
                if i in (6, 7, 10):
                    g = hx("#6a4a20")
                img[y, x, :3] = g
                img[y, x, 3] = 255
    for (x, y) in ((13, 2), (14, 2), (13, 3), (14, 1)):
        img[y, x, :3] = hx("#9ad0f0")
        img[y, x, 3] = 255
    for (x, y) in ((2, 13), (3, 13), (2, 14), (3, 12)):
        img[y, x, :3] = hx("#30a0c0")
        img[y, x, 3] = 255
    return outline(img)


def arrow_entity_tex():
    """32x32 in the vanilla arrow layout: side view u 0-16 / v 0-5 (back at u=0, tip at u=16), nock face u 0-5 / v 5-10"""
    img = np.zeros((32, 32, 4), float)
    for x in range(0, 16):
        img[2, x, :3] = hx("#7a5a3a")
        img[2, x, 3] = 255
    for x in range(0, 5):
        for y in (0, 1, 3, 4):
            if abs(y - 2) <= 2 - x * 0.3:
                img[y, x, :3] = hx("#e8e0d0")
                img[y, x, 3] = 255
    for x, c in ((11, "#c8d8e0"), (12, "#6a8ae8"), (13, "#3a5ad0"), (14, "#2a3a8a"), (15, "#b0b8c0")):
        img[2, x, :3] = hx(c)
        img[2, x, 3] = 255
        if x in (12, 13):
            for y in (1, 3):
                img[y, x, :3] = hx(c) * 0.8
                img[y, x, 3] = 255
    for y in range(5, 10):
        img[y, 2, :3] = hx("#e8e0d0"); img[y, 2, 3] = 255
    for x in range(0, 5):
        img[7, x, :3] = hx("#e8e0d0"); img[7, x, 3] = 255
    return img


def item_model(name):
    p = A + "/models/item/" + name + ".json"
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "arkragnarok:item/" + name}}, open(p, "w"), indent=1)


def recipe(name, data):
    p = D + "/arkragnarok/recipe/" + name + ".json"
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(data, open(p, "w"), indent=1)


def shapeless(items, result, count=1):
    return {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": i} for i in items],
            "result": {"id": result, "count": count}}


# ------------------------------------------------------------------------------------------------ textures + models
for name, col, _, _ in KIBBLE:
    save(kibble_tex(col), A + "/textures/item/%s.png" % name)
    item_model(name)
save(berries_tex("#6a4ab8", "#2a1a5a", "#4a8a3a"), A + "/textures/item/mejoberry.png")
save(berries_tex("#24243a", "#0a0a14", "#3a6a2a"), A + "/textures/item/narcoberry.png")
save(narcotic_tex(), A + "/textures/item/narcotic.png")
save(meat_tex(False), A + "/textures/item/raw_prime_meat.png")
save(meat_tex(True), A + "/textures/item/cooked_prime_meat.png")
save(arrow_tex(), A + "/textures/item/tranq_arrow.png")
save(spyglass_tex(), A + "/textures/item/super_spyglass.png")
save(arrow_entity_tex(), A + "/textures/entity/projectiles/tranq_arrow.png")
for n in ("mejoberry", "narcoberry", "narcotic", "raw_prime_meat", "cooked_prime_meat", "tranq_arrow", "super_spyglass"):
    item_model(n)

# ------------------------------------------------------------------------------------------------ recipes
M = "minecraft:"
K = "arkragnarok:"
recipe("basic_kibble", shapeless([M + "egg", M + "wheat", M + "carrot", M + "sweet_berries"], K + "basic_kibble", 2))
recipe("simple_kibble", shapeless([M + "egg", K + "mejoberry", K + "mejoberry", M + "cooked_cod", M + "potato"], K + "simple_kibble", 2))
recipe("regular_kibble", shapeless([M + "egg", K + "mejoberry", M + "cooked_beef", M + "carrot", M + "beetroot"], K + "regular_kibble", 2))
recipe("superior_kibble", shapeless([M + "egg", K + "cooked_prime_meat", K + "mejoberry", M + "golden_carrot", M + "sugar"],
                                    K + "superior_kibble", 2))
recipe("exceptional_kibble", shapeless([M + "turtle_egg", K + "cooked_prime_meat", K + "cooked_prime_meat", K + "mejoberry",
                                        M + "honey_bottle"], K + "exceptional_kibble", 2))
recipe("exceptional_kibble_alt", shapeless([M + "egg", M + "egg", K + "cooked_prime_meat", K + "cooked_prime_meat", K + "mejoberry",
                                            M + "honey_bottle", M + "golden_carrot"], K + "exceptional_kibble", 2))
recipe("extraordinary_kibble", shapeless([M + "egg", M + "egg", K + "cooked_prime_meat", K + "cooked_prime_meat", M + "glow_berries",
                                          M + "golden_apple", M + "honey_bottle"], K + "extraordinary_kibble", 2))
recipe("narcotic", shapeless([K + "narcoberry"] * 5 + [M + "rotten_flesh"], K + "narcotic", 2))
recipe("tranq_arrow", shapeless([M + "arrow"] * 4 + [K + "narcotic"], K + "tranq_arrow", 4))
recipe("super_spyglass", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" G ", "GSG", " R "],
                          "key": {"G": {"item": M + "gold_ingot"}, "S": {"item": M + "spyglass"}, "R": {"item": M + "redstone"}},
                          "result": {"id": K + "super_spyglass", "count": 1}})
recipe("dino_saddle", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["LLL", "LIL", "S S"],
                       "key": {"L": {"item": M + "leather"}, "I": {"item": M + "iron_ingot"}, "S": {"item": M + "string"}},
                       "result": {"id": K + "dino_saddle", "count": 1}})
for t, tm in (("smelting", 200), ("smoking", 100), ("campfire_cooking", 600)):
    recipe("cooked_prime_meat_" + t, {"type": M + t, "category": "food", "ingredient": {"item": K + "raw_prime_meat"},
                                       "result": {"id": K + "cooked_prime_meat"}, "experience": 0.35, "cookingtime": tm})

# ------------------------------------------------------------------------------------------------ arrows tag
p = D + "/minecraft/tags/item/arrows.json"
os.makedirs(os.path.dirname(p), exist_ok=True)
json.dump({"replace": False, "values": [K + "tranq_arrow"]}, open(p, "w"), indent=1)

# ------------------------------------------------------------------------------------------------ loot: meat, prime meat, hide


def count(item, lo, hi, chance=None):
    e = {"type": "minecraft:item", "name": item,
         "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}
    pool = {"rolls": 1, "entries": [e]}
    if chance is not None:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


for sid, spec in dinos.ALL.items():
    L = spec["length_m"]
    meat = max(1, round(L * 1.3))
    hide = max(1, round(L * 0.7))
    pools = [count(M + "beef", max(1, meat // 2), meat), count(M + "leather", max(1, hide // 2), hide)]
    if L >= 4:
        prime = max(1, round(L / 2.5))
        pools.append(count(K + "raw_prime_meat", max(1, prime // 2), prime))
    else:
        pools.append(count(K + "raw_prime_meat", 1, 1, chance=0.25))
    if sid in ("triceratops", "pachyrhinosaurus", "stegosaurus", "ankylosaurus", "pachycephalosaurus"):
        pools.append(count(M + "bone", 1, 4))
    if sid == "dodo":
        pools.append(count(M + "feather", 1, 3))
        pools.append(count(M + "egg", 0, 1))
    p = D + "/arkragnarok/loot_table/entities/%s.json" % sid
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump({"type": "minecraft:entity", "pools": pools}, open(p, "w"), indent=1)

# ------------------------------------------------------------------------------------------------ translations
EN = {
    "item.arkragnarok.mejoberry": "Mejoberry", "item.arkragnarok.narcoberry": "Narcoberry", "item.arkragnarok.narcotic": "Narcotic",
    "item.arkragnarok.raw_prime_meat": "Raw Prime Meat", "item.arkragnarok.cooked_prime_meat": "Cooked Prime Meat",
    "item.arkragnarok.tranq_arrow": "Tranquilizer Arrow", "item.arkragnarok.super_spyglass": "Super Spyglass",
    "item.arkragnarok.super_spyglass.desc": "Look at a creature: level, health, torpor and what tames it",
    "entity.arkragnarok.tranq_arrow": "Tranquilizer Arrow",
    "msg.arkragnarok.knocked_out": "%s (Lv %s) is unconscious! Feed it while it sleeps.",
    "msg.arkragnarok.tamed": "Tamed a %s, now level %s (taming effectiveness %s%%)",
    "msg.arkragnarok.taming": "%s: taming %s%%, effectiveness %s%%",
    "msg.arkragnarok.not_hungry": "Not hungry yet (%s s)",
    "msg.arkragnarok.order.0": "%s: follow me", "msg.arkragnarok.order.1": "%s: stay here", "msg.arkragnarok.order.2": "%s: wander",
    "msg.arkragnarok.not_yours": "This creature is not yours",
    "hud.arkragnarok.aim": "Super Spyglass: look at a creature", "hud.arkragnarok.level": "Lv %s",
    "hud.arkragnarok.wild": "Wild", "hud.arkragnarok.tamed": "Tamed", "hud.arkragnarok.yours": "Tamed - yours",
    "hud.arkragnarok.unconscious": "Unconscious - feed it to tame", "hud.arkragnarok.health": "Health", "hud.arkragnarok.torpor": "Torpor",
    "hud.arkragnarok.taming": "Taming", "hud.arkragnarok.or": "or", "hud.arkragnarok.arrows": "Knock out: ~%s tranq arrows",
    "hud.arkragnarok.knockout": "Knock out, then feed while asleep:", "hud.arkragnarok.passive": "Passive: feed it by hand:",
    "hud.arkragnarok.effectiveness": "Effectiveness %s%%  -  %s%%  -  %s:%s left",
    "hud.arkragnarok.diet.carnivore": "Carnivore", "hud.arkragnarok.diet.herbivore": "Herbivore",
    "hud.arkragnarok.diet.omnivore": "Omnivore", "hud.arkragnarok.diet.piscivore": "Piscivore",
    "hud.arkragnarok.temper.aggressive": "Aggressive", "hud.arkragnarok.temper.territorial": "Territorial",
    "hud.arkragnarok.temper.neutral": "Neutral", "hud.arkragnarok.temper.skittish": "Skittish",
}
AR = {
    "item.arkragnarok.mejoberry": "توت ميجو", "item.arkragnarok.narcoberry": "توت مخدر", "item.arkragnarok.narcotic": "مخدر",
    "item.arkragnarok.raw_prime_meat": "لحم فاخر نيء", "item.arkragnarok.cooked_prime_meat": "لحم فاخر مطبوخ",
    "item.arkragnarok.tranq_arrow": "سهم تخدير", "item.arkragnarok.super_spyglass": "المنظار الخارق",
    "item.arkragnarok.super_spyglass.desc": "انظر إلى مخلوق: المستوى والصحة والتخدير وما يروّضه",
    "entity.arkragnarok.tranq_arrow": "سهم تخدير",
    "msg.arkragnarok.knocked_out": "%s (مستوى %s) فاقد الوعي! أطعمه وهو نائم.",
    "msg.arkragnarok.tamed": "تم ترويض %s، المستوى الآن %s (فعالية الترويض %s%%)",
    "msg.arkragnarok.taming": "%s: الترويض %s%%، الفعالية %s%%",
    "msg.arkragnarok.not_hungry": "ليس جائعاً بعد (%s ث)",
    "msg.arkragnarok.order.0": "%s: اتبعني", "msg.arkragnarok.order.1": "%s: ابقَ هنا", "msg.arkragnarok.order.2": "%s: تجوّل",
    "msg.arkragnarok.not_yours": "هذا المخلوق ليس لك",
    "hud.arkragnarok.aim": "المنظار الخارق: انظر إلى مخلوق", "hud.arkragnarok.level": "مستوى %s",
    "hud.arkragnarok.wild": "بري", "hud.arkragnarok.tamed": "مروّض", "hud.arkragnarok.yours": "مروّض - لك",
    "hud.arkragnarok.unconscious": "فاقد الوعي - أطعمه لترويضه", "hud.arkragnarok.health": "الصحة", "hud.arkragnarok.torpor": "التخدير",
    "hud.arkragnarok.taming": "الترويض", "hud.arkragnarok.or": "أو", "hud.arkragnarok.arrows": "للإغماء: ~%s سهم تخدير",
    "hud.arkragnarok.knockout": "خدّره ثم أطعمه وهو نائم:", "hud.arkragnarok.passive": "ترويض سلمي: أطعمه بيدك:",
    "hud.arkragnarok.effectiveness": "الفعالية %s%%  -  %s%%  -  باقي %s:%s",
    "hud.arkragnarok.diet.carnivore": "لاحم", "hud.arkragnarok.diet.herbivore": "عاشب",
    "hud.arkragnarok.diet.omnivore": "قارت", "hud.arkragnarok.diet.piscivore": "آكل سمك",
    "hud.arkragnarok.temper.aggressive": "عدواني", "hud.arkragnarok.temper.territorial": "مدافع عن منطقته",
    "hud.arkragnarok.temper.neutral": "محايد", "hud.arkragnarok.temper.skittish": "جبان",
}
for name, _, en, ar in KIBBLE:
    EN["item.arkragnarok." + name] = en
    AR["item.arkragnarok." + name] = ar
for lang, add in (("en_us", EN), ("ar_sa", AR)):
    p = A + "/lang/%s.json" % lang
    d = json.load(open(p))
    d.update(add)
    json.dump(d, open(p, "w"), indent=1, ensure_ascii=False)
print("taming data written")
