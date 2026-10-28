"""Recipes, loot tables, tags, damage types and translations (English + Arabic)."""
import json
import os

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(BASE, "assets", "gunsmod")
D = os.path.join(BASE, "data")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, indent=1, ensure_ascii=False)


BLOCKS = ["weapon_workbench", "modification_table", "ammo_crate", "missile_launcher", "bot_spawner", "arena_controller",
          "sandbags", "sandbags_low", "concrete_barrier", "barbed_wire", "explosive_barrel", "heavy_launcher", "vehicle_spawner"]

# ------------------------------------------------------------------------------------ loot tables
for b in BLOCKS:
    write(os.path.join(D, "gunsmod", "loot_table", "blocks", b + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "gunsmod:" + b}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": "gunsmod:blocks/" + b})

# ------------------------------------------------------------------------------------ tags
write(os.path.join(D, "minecraft", "tags", "block", "mineable", "pickaxe.json"), {"values": [
    "gunsmod:weapon_workbench", "gunsmod:modification_table", "gunsmod:missile_launcher", "gunsmod:bot_spawner",
    "gunsmod:arena_controller", "gunsmod:concrete_barrier", "gunsmod:barbed_wire", "gunsmod:explosive_barrel",
    "gunsmod:heavy_launcher", "gunsmod:heavy_launcher_part", "gunsmod:vehicle_spawner"]})
write(os.path.join(D, "minecraft", "tags", "block", "mineable", "axe.json"), {"values": ["gunsmod:ammo_crate"]})
write(os.path.join(D, "minecraft", "tags", "block", "mineable", "shovel.json"), {"values": ["gunsmod:sandbags", "gunsmod:sandbags_low"]})

# ------------------------------------------------------------------------------------ damage types
write(os.path.join(D, "gunsmod", "damage_type", "bullet.json"), {"message_id": "gunsmod.bullet", "exhaustion": 0.1, "scaling": "never"})
write(os.path.join(D, "gunsmod", "damage_type", "flames.json"), {"message_id": "gunsmod.flames", "exhaustion": 0.1, "scaling": "never", "effects": "burning"})
write(os.path.join(D, "gunsmod", "damage_type", "roadkill.json"), {"message_id": "gunsmod.roadkill", "exhaustion": 0.1, "scaling": "never"})
write(os.path.join(D, "minecraft", "tags", "damage_type", "is_projectile.json"), {"values": ["gunsmod:bullet"]})
write(os.path.join(D, "minecraft", "tags", "damage_type", "no_knockback.json"), {"values": ["gunsmod:bullet", "gunsmod:flames"]})
write(os.path.join(D, "minecraft", "tags", "damage_type", "is_fire.json"), {"values": ["gunsmod:flames"]})

# ------------------------------------------------------------------------------------ recipes
R = os.path.join(D, "gunsmod", "recipe")


def item(i):
    return {"tag": i[1:]} if i.startswith("#") else {"item": i}


def shaped(name, pattern, key, result, count=1):
    write(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                             "key": {k: item(v) for k, v in key.items()}, "result": {"id": result, "count": count}})


def shapeless(name, ings, result, count=1):
    write(os.path.join(R, name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
                                             "ingredients": [item(i) for i in ings], "result": {"id": result, "count": count}})


S = "gunsmod:steel_ingot"
shapeless("steel_ingot", ["minecraft:iron_ingot", "minecraft:coal"], S)
write(os.path.join(R, "steel_ingot_blasting.json"), {"type": "minecraft:blasting", "category": "misc", "ingredient": {"item": "minecraft:iron_ingot"},
                                                       "result": {"id": S}, "experience": 0.3, "cookingtime": 100})
shaped("weapon_workbench", ["SSS", "ICI", "P P"], {"S": S, "I": "minecraft:iron_ingot", "C": "minecraft:crafting_table", "P": "#minecraft:planks"}, "gunsmod:weapon_workbench")
shaped("modification_table", ["GSG", "ICI", "P P"], {"G": "minecraft:glass_pane", "S": S, "I": "minecraft:iron_ingot", "C": "minecraft:smithing_table", "P": "#minecraft:planks"}, "gunsmod:modification_table")
shaped("ammo_crate", ["PIP", "PGP", "PPP"], {"P": "#minecraft:planks", "I": "minecraft:iron_ingot", "G": "minecraft:gunpowder"}, "gunsmod:ammo_crate")
shaped("missile_launcher", ["SRS", "SDS", "BBB"], {"S": S, "R": "minecraft:redstone_block", "D": "minecraft:dispenser", "B": "minecraft:iron_block"}, "gunsmod:missile_launcher")
shaped("heavy_launcher", ["SRS", "LDL", "BBB"], {"S": S, "R": "minecraft:redstone_block", "L": "gunsmod:missile_launcher", "D": "minecraft:dispenser",
                                                "B": "minecraft:iron_block"}, "gunsmod:heavy_launcher")
shaped("bot_spawner", ["SES", "SRS", "III"], {"S": S, "E": "minecraft:emerald", "R": "minecraft:redstone_block", "I": "minecraft:iron_ingot"}, "gunsmod:bot_spawner")
shaped("vehicle_spawner", ["SRS", "ICI", "SSS"], {"S": S, "R": "minecraft:redstone_block", "I": "minecraft:iron_block", "C": "minecraft:compass"},
       "gunsmod:vehicle_spawner")
shaped("arena_controller", ["SGS", "SRS", "SCS"], {"S": S, "G": "minecraft:glass", "R": "minecraft:redstone_block", "C": "minecraft:compass"}, "gunsmod:arena_controller")
shaped("arena_tool", ["PMP", "SCS", "PSP"], {"P": "minecraft:paper", "M": "minecraft:map", "S": S, "C": "minecraft:compass"}, "gunsmod:arena_tool")
shaped("sandbags", ["SSS", "WWW", "SSS"], {"S": "#minecraft:sand", "W": "minecraft:string"}, "gunsmod:sandbags", 6)
shaped("sandbags_low", ["SSS"], {"S": "gunsmod:sandbags"}, "gunsmod:sandbags_low", 6)
shaped("concrete_barrier", ["C C", "CCC"], {"C": "minecraft:gray_concrete"}, "gunsmod:concrete_barrier", 3)
shaped("barbed_wire", ["N N", " B ", "N N"], {"N": "minecraft:iron_nugget", "B": "minecraft:iron_bars"}, "gunsmod:barbed_wire", 3)
shaped("explosive_barrel", ["IRI", "ITI", "III"], {"I": "minecraft:iron_ingot", "R": "minecraft:red_dye", "T": "minecraft:tnt"}, "gunsmod:explosive_barrel", 2)
shaped("launch_remote", [" R ", "SCS", "SBS"], {"R": "minecraft:redstone_torch", "S": S, "C": "minecraft:comparator", "B": "minecraft:stone_button"}, "gunsmod:launch_remote")
shapeless("dog_tag", ["minecraft:iron_nugget", "minecraft:iron_nugget", "minecraft:chain"], "gunsmod:dog_tag")
shaped("gun_parts", ["NSN"], {"N": "minecraft:iron_nugget", "S": S}, "gunsmod:gun_parts", 2)

# ------------------------------------------------------------------------------------ translations
GUNS = {
    "m1911": ("M1911 Pistol", "مسدس M1911"), "p9": ("P9 Service Pistol", "مسدس P9"), "magnum50": (".50 Magnum Pistol", "مسدس ماغنوم .50"),
    "revolver": (".357 Revolver", "مسدس ريفولفر .357"), "smg5": ("SMG-5", "رشاش SMG-5"), "micro_smg": ("Micro SMG", "رشاش صغير"),
    "vortex": ("Vortex SMG", "رشاش فورتكس"), "pdw": ("Bullpup PDW", "رشاش PDW"), "ak47": ("AK-47", "كلاشنكوف AK-47"),
    "m4a1": ("M4A1 Carbine", "بندقية M4A1"), "m16a4": ("M16A4 Rifle", "بندقية M16A4"), "br17": ("BR-17 Battle Rifle", "بندقية قتالية BR-17"),
    "br77": ("BR-77 Bullpup Rifle", "بندقية بولباب BR-77"), "tactical_rifle": ("Tactical Rifle", "بندقية تكتيكية"),
    "pump_shotgun": ("Pump Shotgun", "شوزن مضخة"), "auto_shotgun": ("Auto Shotgun", "شوزن أوتوماتيكي"), "double_barrel": ("Double Barrel Shotgun", "شوزن بماسورتين"),
    "m24": ("M24 Sniper Rifle", "قناصة M24"), "svd": ("SVD Marksman Rifle", "قناصة دراغونوف SVD"), "m107": ("M107 Anti-Materiel Rifle", "قناصة M107 عيار .50"),
    "m249": ("M249 Light Machine Gun", "رشاش خفيف M249"), "minigun": ("M134 Minigun", "رشاش مينيغن M134"), "rpg7": ("RPG-7 Launcher", "قاذف RPG-7"),
    "m79": ("M79 Grenade Launcher", "قاذف قنابل M79"), "flamethrower": ("Flamethrower", "قاذف لهب"),
}
ITEMS = {
    "ammo_pistol": ("Pistol Ammo Box (9mm)", "صندوق ذخيرة مسدس 9مم"), "ammo_rifle": ("Rifle Ammo Can", "علبة ذخيرة بندقية"),
    "ammo_shotgun": ("Shotgun Shells", "خراطيش شوزن"), "ammo_sniper": ("Sniper Ammo Can (.50)", "علبة ذخيرة قناصة"),
    "ammo_heavy": ("Belt Ammo Can (200)", "علبة ذخيرة شريط 200"), "rocket_ammo": ("PG-7 Rocket", "صاروخ PG-7"),
    "grenade_40mm": ("40mm Grenade", "قنبلة 40مم"), "fuel_canister": ("Fuel Canister", "عبوة وقود"),
    "red_dot": ("Red Dot Sight", "منظار نقطة حمراء"), "holo_sight": ("Holographic Sight", "منظار هولوغرافي"), "acog": ("ACOG Scope 4x", "منظار ACOG"),
    "sniper_scope": ("Sniper Scope 8x", "منظار قناصة"), "suppressor": ("Suppressor", "كاتم صوت"), "compensator": ("Compensator", "معوض ارتداد"),
    "vertical_grip": ("Vertical Grip", "مقبض عمودي"), "angled_grip": ("Angled Grip", "مقبض مائل"), "laser_sight": ("Laser Sight", "مؤشر ليزر"),
    "extended_mag": ("Extended Magazine", "مخزن ممتد"), "frag_grenade": ("Frag Grenade", "قنبلة يدوية"), "smoke_grenade": ("Smoke Grenade", "قنبلة دخان"),
    "missile_he": ("HE Missile", "صاروخ شديد الانفجار"), "missile_cluster": ("Cluster Missile", "صاروخ عنقودي"), "missile_incendiary": ("Incendiary Missile", "صاروخ حارق"),
    "missile_thermobaric": ("Thermobaric Missile", "صاروخ فراغي حراري"), "missile_bunker_buster": ("Bunker Buster Missile", "صاروخ خارق للتحصينات"),
    "missile_titan": ("Titan Strategic Missile", "صاروخ تايتان الاستراتيجي"),
    "launch_remote": ("Launch Remote", "ريموت الإطلاق"), "dog_tag": ("Soldier Dog Tag", "قلادة الجندي"), "arena_tool": ("Battlefield Deployer", "مُنشئ ساحة الحرب"),
    "green_soldier": ("Green Soldier", "جندي أخضر"), "red_soldier": ("Red Soldier", "جندي أحمر"), "green_jeep": ("Green Military Jeep", "جيب عسكري أخضر"),
    "red_jeep": ("Red Military Jeep", "جيب عسكري أحمر"), "green_tank": ("Green Battle Tank", "دبابة خضراء"), "red_tank": ("Red Battle Tank", "دبابة حمراء"),
    "green_helicopter": ("Green Attack Helicopter", "هليكوبتر هجومية خضراء"), "red_helicopter": ("Red Attack Helicopter", "هليكوبتر هجومية حمراء"), "steel_ingot": ("Steel Ingot", "سبيكة فولاذ"), "gun_parts": ("Gun Parts", "قطع أسلحة"),
}
BLOCKN = {
    "weapon_workbench": ("Weapon Workbench", "طاولة صنع الأسلحة"), "modification_table": ("Gunsmith Table", "طاولة تعديل الأسلحة"),
    "ammo_crate": ("Ammo Supply Crate", "صندوق إمداد الذخيرة"), "missile_launcher": ("Missile Launch Station", "محطة إطلاق الصواريخ"),
    "bot_spawner": ("Soldier Deployment Pad", "منصة نشر الجنود"), "arena_controller": ("Battle Command Console", "جهاز التحكم بالمعركة"),
    "sandbags": ("Sandbags", "أكياس رمل"), "sandbags_low": ("Low Sandbags", "أكياس رمل منخفضة"), "concrete_barrier": ("Concrete Barrier", "حاجز خرساني"),
    "barbed_wire": ("Barbed Wire", "أسلاك شائكة"), "explosive_barrel": ("Explosive Barrel", "برميل متفجر"),
    "heavy_launcher": ("Heavy Missile Launcher (3x5)", "منصة الصواريخ الثقيلة (3×5)"), "heavy_launcher_part": ("Heavy Missile Launcher", "منصة الصواريخ الثقيلة"),
    "vehicle_spawner": ("Vehicle Spawner", "مولّد المركبات"),
}

EN = {"itemGroup.gunsmod": "Guns & Warfare", "key.categories.gunsmod": "Guns & Warfare"}
AR = {"itemGroup.gunsmod": "أسلحة وحرب", "key.categories.gunsmod": "أسلحة وحرب"}
for k, (e, a) in {**GUNS, **ITEMS}.items():
    EN["item.gunsmod." + k] = e
    AR["item.gunsmod." + k] = a
for k, (e, a) in BLOCKN.items():
    EN["block.gunsmod." + k] = e
    AR["block.gunsmod." + k] = a

T = {
    "entity.gunsmod.soldier": ("Soldier", "جندي"), "entity.gunsmod.jeep": ("Military Jeep", "جيب عسكري"), "entity.gunsmod.rocket": ("Rocket", "صاروخ"),
    "entity.gunsmod.grenade": ("Grenade", "قنبلة"), "entity.gunsmod.missile": ("Missile", "صاروخ موجه"),
    "entity.gunsmod.tank": ("Battle Tank", "دبابة"), "entity.gunsmod.helicopter": ("Attack Helicopter", "هليكوبتر هجومية"),
    "gunsmod.category.pistol": ("Pistol", "مسدس"), "gunsmod.category.smg": ("Submachine Gun", "رشاش قصير"), "gunsmod.category.rifle": ("Assault Rifle", "بندقية هجومية"),
    "gunsmod.category.shotgun": ("Shotgun", "شوزن"), "gunsmod.category.sniper": ("Sniper Rifle", "قناصة"), "gunsmod.category.heavy": ("Heavy Weapon", "سلاح ثقيل"),
    "gunsmod.category.explosive": ("Explosive Launcher", "قاذف متفجرات"), "gunsmod.category.special": ("Special Weapon", "سلاح خاص"),
    "gunsmod.tip.ammo": ("Ammo: %s / %s  (%s)", "الذخيرة: %s / %s  (%s)"), "gunsmod.tip.damage": ("Damage: %s", "الضرر: %s"),
    "gunsmod.tip.rpm": ("Fire rate: %s RPM, %s", "سرعة الإطلاق: %s طلقة/دقيقة، %s"), "gunsmod.tip.range": ("Range: %s blocks", "المدى: %s بلوكة"),
    "gunsmod.tip.controls": ("Hold attack: fire | Tap use: aim | R: reload", "اضغط هجوم مطولاً: إطلاق | ضغطة استخدام: تصويب | R: تعبئة"),
    "gunsmod.tip.rounds": ("Rounds: %s / %s", "الطلقات: %s / %s"),
    "gunsmod.tip.ammo_for.pistol": ("For pistols and SMGs", "للمسدسات والرشاشات القصيرة"), "gunsmod.tip.ammo_for.rifle": ("For assault rifles", "للبنادق الهجومية"),
    "gunsmod.tip.ammo_for.shotgun": ("For shotguns", "للشوزن"), "gunsmod.tip.ammo_for.sniper": ("For sniper rifles", "للقناصات"),
    "gunsmod.tip.ammo_for.heavy": ("For machine guns", "للرشاشات الثقيلة"), "gunsmod.tip.ammo_for.rocket": ("For the RPG-7", "لقاذف RPG-7"),
    "gunsmod.tip.ammo_for.grenade": ("For the M79", "لقاذف M79"), "gunsmod.tip.ammo_for.fuel": ("For the flamethrower", "لقاذف اللهب"),
    "gunsmod.tip.attach_hint": ("Install it at a Gunsmith Table", "ركّبه على طاولة تعديل الأسلحة"),
    "gunsmod.tip.missile_hint": ("Load it into a Missile Launch Station", "ضعه في محطة إطلاق الصواريخ"),
    "gunsmod.tip.linked": ("Linked to station %s %s %s", "مرتبط بالمحطة %s %s %s"), "gunsmod.tip.unlinked": ("Not linked: use it on a launch station", "غير مرتبط: استخدمه على محطة إطلاق"),
    "gunsmod.tip.remote1": ("Sneak + use: mark the block you look at as target", "شفت + كليك يمين: حدد البلوكة التي تنظر إليها كهدف"),
    "gunsmod.tip.remote2": ("Use: open the launch control panel", "كليك يمين: افتح لوحة التحكم بالإطلاق"),
    "gunsmod.tip.tag_empty": ("Use on a soldier to save his loadout", "استخدمها على جندي لحفظ تعديلاته"),
    "gunsmod.tip.tag_weapon": ("Weapon: %s", "السلاح: %s"), "gunsmod.tip.tag_use": ("Put it in a Deployment Pad, or use on the ground", "ضعها في منصة النشر أو استخدمها على الأرض"),
    "gunsmod.tip.soldier_controls": ("Patrols and fights on its own | Give it a gun, remote or missiles by using them on it | Sneak + empty hand: gear",
                                     "يتحرك ويقاتل بمفرده | أعطه سلاحاً أو ريموت أو صواريخ بالكليك عليه | شفت + يد فارغة: العتاد"),
    "gunsmod.tip.jeep1": ("Use: get in (driver first) | Sneak + use: gunner seat", "كليك يمين: اركب (السائق أولاً) | شفت + كليك: مقعد الرشاش"),
    "gunsmod.tip.jeep2": ("V: change seat | H: horn | iron ingot: repair", "V: تغيير المقعد | H: البوق | سبيكة حديد: تصليح"),
    "gunsmod.desc.tank": ("Main battle tank for one: drive, aim the turret with your view, fire", "دبابة قتال لشخص واحد: تقود وتوجه المدفع بنظرك وتطلق"),
    "gunsmod.tip.tank1": ("Attack: 120 mm gun | Use: machine gun | WASD: drive", "ضغط: مدفع 120مم | كليك يمين: رشاش | WASD: قيادة"),
    "gunsmod.tip.tank2": ("Iron ingot / iron block: repair", "سبيكة / بلوك حديد: تصليح"),
    "gunsmod.desc.heli": ("Attack helicopter with rockets and a chin gun", "هليكوبتر هجومية بصواريخ ورشاش أمامي"),
    "gunsmod.tip.heli1": ("W/S: nose down/up | A/D: turn | Jump: climb | Sneak: descend, get out once landed", "W/S: للأمام/للخلف | A/D: دوران | قفز: صعود | شفت: نزول، والخروج بعد الهبوط"),
    "gunsmod.tip.heli2": ("Attack: rockets | Use: chin gun | iron: repair", "ضغط: صواريخ | كليك يمين: رشاش | حديد: تصليح"),
    "gunsmod.tip.spawner_hint": ("Use it on a Vehicle Spawner to keep one in service", "استخدمه على مولّد المركبات ليعيد إنتاجها دائماً"),
    "gunsmod.msg.no_room_vehicle": ("Not enough room for this vehicle", "لا توجد مساحة كافية لهذه المركبة"),
    "gunsmod.msg.spawner_set": ("Spawner: %s", "المولّد: %s"),
    "gunsmod.msg.spawner_empty": ("Use a jeep, tank or helicopter on the spawner", "استخدم جيب أو دبابة أو هليكوبتر على المولّد"),
    "gunsmod.msg.spawner_cleared": ("Vehicle taken back", "تم استرجاع المركبة"),
    "gunsmod.msg.spawner_wait": ("%s returns in %s s", "%s تعود بعد %s ث"),
    "gunsmod.msg.spawner_out": ("%s is out; a new one comes when it is destroyed", "%s في الخدمة؛ تأتي واحدة جديدة عندما تُدمَّر"),
    "gunsmod.seat.commander": ("Commander", "قائد الدبابة"), "gunsmod.seat.pilot": ("Pilot", "الطيار"),
    "gunsmod.hud.gun_ready": ("GUN READY", "المدفع جاهز"), "gunsmod.hud.gun_loading": ("LOADING...", "جارِ التلقيم..."),
    "gunsmod.hud.tank_hint": ("WASD drive  |  Attack: 120 mm  |  Use: machine gun", "WASD قيادة  |  ضغط: مدفع  |  كليك يمين: رشاش"),
    "gunsmod.hud.rockets": ("Rockets: %s", "الصواريخ: %s"), "gunsmod.hud.rockets_loading": ("Rockets: reloading", "الصواريخ: تعبئة"),
    "gunsmod.hud.altitude": ("ALT %s m", "الارتفاع %s م"), "gunsmod.hud.climb": ("V/S %s", "عمودي %s"), "gunsmod.hud.rotor": ("ROTOR %s%%", "المروحة %s%%"),
    "gunsmod.hud.pull_up": ("PULL UP", "ارتفع!"),
    "gunsmod.hud.heli_hint": ("W/S fly  A/D turn  Jump up  Sneak down  |  Attack: rockets  Use: gun", "W/S طيران  A/D دوران  قفز صعود  شفت نزول  |  ضغط: صواريخ  يمين: رشاش"),
    "gunsmod.state.patrol": ("Patrolling", "في دورية"), "gunsmod.state.combat": ("In combat", "يقاتل"), "gunsmod.state.vehicle": ("In a vehicle", "في مركبة"),
    "gunsmod.ui.patrols": ("Patrols on its own", "يتحرك بمفرده"),
    "gunsmod.tip.arena_tool": ("Use on the ground twice to build", "استخدمه على الأرض مرتين للبناء"),
    "gunsmod.mode.semi": ("Semi", "طلقة"), "gunsmod.mode.auto": ("Auto", "آلي"), "gunsmod.mode.burst": ("Burst", "دفعات"),
    "gunsmod.slot.scope": ("Optic", "منظار"), "gunsmod.slot.muzzle": ("Muzzle", "فوهة"), "gunsmod.slot.grip": ("Grip", "مقبض"),
    "gunsmod.slot.laser": ("Laser", "ليزر"), "gunsmod.slot.mag": ("Magazine", "مخزن"),
    "gunsmod.desc.red_dot": ("1.25x zoom, steadier aim", "تكبير 1.25x وتصويب أثبت"), "gunsmod.desc.holo_sight": ("1.3x zoom, steadier aim", "تكبير 1.3x وتصويب أثبت"),
    "gunsmod.desc.acog": ("2.5x zoom", "تكبير 2.5x"), "gunsmod.desc.sniper_scope": ("6x zoom with a scope overlay", "تكبير 6x بعدسة قناصة"),
    "gunsmod.desc.suppressor": ("Silent shots, no muzzle flash, soldiers don't hear you", "طلقات صامتة بدون وميض"),
    "gunsmod.desc.compensator": ("-30% recoil", "ارتداد أقل 30%"), "gunsmod.desc.vertical_grip": ("-20% recoil, better hip fire", "ارتداد أقل وإطلاق أدق"),
    "gunsmod.desc.angled_grip": ("Faster aiming, less recoil", "تصويب أسرع وارتداد أقل"), "gunsmod.desc.laser_sight": ("Much better hip fire, visible beam", "إطلاق من الورك أدق بكثير"),
    "gunsmod.desc.extended_mag": ("+50% magazine size", "مخزن أكبر بـ 50%"),
    "gunsmod.desc.frag_grenade": ("Explodes 3 seconds after the throw", "تنفجر بعد 3 ثوانٍ"), "gunsmod.desc.smoke_grenade": ("Smoke screen that blocks soldiers' vision", "ستار دخان يحجب رؤية الجنود"),
    "gunsmod.desc.missile_0": ("Huge high-explosive blast", "انفجار ضخم"), "gunsmod.desc.missile_1": ("Splits into 10 bomblets", "ينقسم إلى 10 قنابل صغيرة"),
    "gunsmod.desc.missile_2": ("Sets the target area ablaze", "يحرق منطقة الهدف"),
    "gunsmod.desc.missile_3": ("Fuel-air blast: giant fireball and a pressure wave that flattens 28 blocks", "انفجار وقود-هواء: كرة نار عملاقة وموجة ضغط تمسح 28 بلوكة"),
    "gunsmod.desc.missile_4": ("Drills 15 blocks into the ground, then erupts", "يحفر 15 بلوكة تحت الأرض ثم ينفجر من الأسفل"),
    "gunsmod.desc.missile_5": ("Strategic warhead: mushroom cloud, firestorm, 64 block shockwave", "رأس استراتيجي: سحابة فطر وعاصفة نار وموجة صدمة 64 بلوكة"),
    "gunsmod.tip.missile_heavy": ("Heavy missile: load it into a Heavy Missile Launcher", "صاروخ ثقيل: ضعه في منصة الصواريخ الثقيلة"),
    "gunsmod.tip.linked_n": ("Linked stations: %s / %s", "المحطات المرتبطة: %s / %s"),
    "gunsmod.tip.selected": ("Selected: #%s at %s %s %s", "المحددة: #%s عند %s %s %s"),
    "gunsmod.tip.remote3": ("Use on a station: link it (up to 100) | Sneak: unlink", "استخدمه على محطة: اربطها (حتى 100) | شفت: فك الربط"),
    "gunsmod.msg.remote_selected": ("Station #%s selected", "تم اختيار المحطة #%s"),
    "gunsmod.msg.remote_full": ("This remote already controls %s stations", "هذا الريموت يتحكم بـ %s محطة بالفعل"),
    "gunsmod.msg.remote_unlinked_station": ("Unlinked station %s %s %s", "تم فك ربط المحطة %s %s %s"),
    "gunsmod.msg.target_set_n": ("Station #%s target: %s %s %s", "هدف المحطة #%s: %s %s %s"),
    "gunsmod.msg.station_busy": ("The station is busy launching", "المحطة مشغولة بالإطلاق"),
    "gunsmod.msg.missile_too_small": ("The heavy launcher only takes heavy missiles", "المنصة الثقيلة تقبل الصواريخ الثقيلة فقط"),
    "gunsmod.msg.missile_too_big": ("Heavy missiles need the Heavy Missile Launcher", "الصواريخ الثقيلة تحتاج منصة الصواريخ الثقيلة"),
    "gunsmod.msg.heavy_no_room": ("The heavy launcher needs a clear 3 x 5 area", "المنصة الثقيلة تحتاج مساحة فارغة 3×5"),
    "gunsmod.msg.salvo_target": ("Salvo: %s stations targeted", "رشقة: تم توجيه %s محطة"),
    "gunsmod.msg.salvo_armed": ("Salvo: %s stations armed", "رشقة: تم تسليح %s محطة"),
    "gunsmod.msg.salvo_launch": ("SALVO: %s stations launching", "رشقة: %s محطة تطلق"),
    "gunsmod.msg.salvo_disarmed": ("Salvo: %s stations disarmed", "رشقة: تم إلغاء تسليح %s محطة"),
    "gunsmod.msg.gave_remote": ("%s took the remote (%s stations) and will call in missiles on enemy groups",
                                "%s أخذ الريموت (%s محطة) وسيطلق الصواريخ على تجمعات العدو"),
    "gunsmod.msg.gave_missiles": ("Gave %s missiles to %s", "أعطيت %s صواريخ إلى %s"),
    "gunsmod.msg.rack_full": ("%s cannot carry more missiles", "%s لا يستطيع حمل صواريخ أكثر"),
    "gunsmod.launch.set_all": ("ALL", "للكل"), "gunsmod.launch.unlink": ("UNLINK STATION", "فك ربط المحطة"),
    "gunsmod.launch.salvo_on": ("SALVO ON", "رشقة ✔"), "gunsmod.launch.salvo_off": ("SALVO", "رشقة"),
    "gunsmod.launch.fire_all_btn": ("LAUNCH ALL (%s)", "إطلاق الكل (%s)"), "gunsmod.launch.fire_all_confirm": ("CONFIRM? (%s)", "تأكيد؟ (%s)"),
    "gunsmod.msg.fire_all": ("Launching %s missiles", "إطلاق %s صاروخ"),
    "gunsmod.msg.fire_all_some": ("Launching %s missiles, %s stations have no target", "إطلاق %s صاروخ، و%s محطة بدون هدف"),
    "gunsmod.msg.fire_all_none": ("No station is ready: load missiles and set targets", "لا توجد محطة جاهزة: حمّل الصواريخ وحدد الأهداف"),
    "gunsmod.msg.fire_all_notarget": ("%s loaded stations have no target", "%s محطة محمّلة بدون هدف"),
    "gunsmod.launch.stations": ("STATIONS", "المحطات"), "gunsmod.launch.missing": ("missing", "مفقودة"),
    "gunsmod.launch.no_stations": ("No stations linked", "لا توجد محطات مرتبطة"),
    "gunsmod.launch.station_n": ("Station #%s - %s", "المحطة #%s - %s"),
    "gunsmod.launch.heavy": ("Heavy", "ثقيلة"), "gunsmod.launch.light": ("Standard", "عادية"),
    "gunsmod.launch.panel_salvo": ("SALVO LAUNCH", "إطلاق رشقة"), "gunsmod.launch.fire_all": ("FIRE ALL", "أطلق الكل"),
    "gunsmod.ui.remote": ("Remote", "ريموت"), "gunsmod.ui.missile_rack": ("Missiles", "صواريخ"),
    "gunsmod.ui.no_remote": ("No remote: give one for artillery", "بدون ريموت: أعطه واحداً للمدفعية"),
    "gunsmod.ui.artillery": ("Artillery: %s stations", "المدفعية: %s محطة"),
    "gunsmod.desc.green_soldier": ("Friendly soldier. Fights the red team", "جندي صديق يقاتل الفريق الأحمر"),
    "gunsmod.desc.red_soldier": ("Enemy soldier. Attacks green soldiers and survival players", "جندي عدو يهاجم الجنود الخضر واللاعب في وضع النجاة"),
    "gunsmod.desc.jeep": ("Military jeep with a turret, 40 km/h", "جيب عسكري برشاش، 40 كم/س"),
    "gunsmod.desc.arena_tool": ("Builds a 50x50 battlefield with a command console", "يبني ساحة حرب 50×50 مع جهاز تحكم"),
    "gunsmod.msg.no_ammo": ("No %s left!", "لا يوجد %s!"), "gunsmod.msg.gave_weapon": ("%s took the weapon", "%s أخذ السلاح"),
    "gunsmod.msg.crate_wait": ("Resupply in %s s", "إعادة التزويد بعد %s ث"),
    "gunsmod.msg.crate_given": ("Resupplied: %s", "تم التزويد: %s"), "gunsmod.msg.crate_hint": ("Use the crate while holding a gun", "استخدم الصندوق وأنت تمسك سلاحاً"),
    "gunsmod.msg.already_loaded": ("A missile is already loaded", "يوجد صاروخ محمّل مسبقاً"), "gunsmod.msg.missile_loaded": ("Missile loaded", "تم تحميل الصاروخ"),
    "gunsmod.msg.remote_linked": ("Linked station %s %s %s (%s/%s)", "تم ربط المحطة %s %s %s (%s/%s)"),
    "gunsmod.msg.remote_given": ("You received a linked Launch Remote", "حصلت على ريموت إطلاق مرتبط بالمحطة"),
    "gunsmod.msg.target_set": ("Target set: %s %s %s", "تم تحديد الهدف: %s %s %s"), "gunsmod.msg.no_missile": ("No missile loaded", "لا يوجد صاروخ"),
    "gunsmod.msg.no_target": ("No target set", "لم يتم تحديد هدف"), "gunsmod.msg.launching": ("LAUNCH SEQUENCE STARTED", "بدأ تسلسل الإطلاق"),
    "gunsmod.msg.arena_missing": ("This console is not part of a battlefield", "هذا الجهاز ليس جزءاً من ساحة حرب"),
    "gunsmod.msg.remote_unlinked": ("Link the remote first: use it on a launch station", "اربط الريموت أولاً باستخدامه على المحطة"),
    "gunsmod.msg.station_missing": ("Launch station not found", "لم يتم العثور على المحطة"), "gunsmod.msg.too_far": ("No block in sight", "لا توجد بلوكة في مجال النظر"),
    "gunsmod.msg.tag_saved": ("Saved %s to the dog tag", "تم حفظ %s في القلادة"), "gunsmod.msg.no_room": ("Not enough room for the jeep", "لا توجد مساحة كافية للجيب"),
    "gunsmod.msg.arena_building": ("Building the battlefield...", "جارِ بناء ساحة الحرب..."),
    "gunsmod.msg.arena_confirm": ("Outline shown. Use again on the same spot to build (clears the area!)", "تم عرض الحدود. استخدمه مرة أخرى في نفس المكان للبناء (سيتم مسح المنطقة!)"),
    "gunsmod.msg.arena_done": ("Battlefield ready (%s ms). Use the command console to start a battle.", "ساحة الحرب جاهزة (%s ms). استخدم جهاز التحكم لبدء المعركة."),
    "gunsmod.msg.driving_to": ("Driver: moving to %s %s %s", "السائق: أتحرك إلى %s %s %s"), "gunsmod.msg.no_driver": ("No soldier is driving", "لا يوجد جندي يقود"),
    "gunsmod.team.green": ("Green", "الأخضر"), "gunsmod.team.red": ("Red", "الأحمر"),
    "gunsmod.seat.0": ("Driver", "السائق"), "gunsmod.seat.1": ("Gunner", "الرامي"), "gunsmod.seat.2": ("Passenger", "راكب"), "gunsmod.seat.3": ("Rear seat", "المقعد الخلفي"),
    "gunsmod.arena.round": ("ROUND %s", "الجولة %s"), "gunsmod.arena.vs": ("%s vs %s", "%s ضد %s"),
    "gunsmod.arena.win.green": ("GREEN TEAM WINS", "فاز الفريق الأخضر"), "gunsmod.arena.win.red": ("RED TEAM WINS", "فاز الفريق الأحمر"),
    "gunsmod.arena.score": ("Score  Green %s : %s Red", "النتيجة  أخضر %s : %s أحمر"), "gunsmod.arena.mode.player": ("You + Green vs Red", "أنت + الأخضر ضد الأحمر"),
    "gunsmod.arena.mode.ai": ("AI vs AI (spectate)", "ذكاء ضد ذكاء (مشاهدة)"), "gunsmod.arena.restart": ("RESTART", "إعادة"),
    "gunsmod.arena.start": ("START BATTLE", "ابدأ المعركة"), "gunsmod.arena.stop": ("STOP", "إيقاف"), "gunsmod.arena.you_plus": ("you +", "أنت +"),
    "gunsmod.arena.status.running": ("Round %s running - Green %s alive, Red %s alive", "الجولة %s - أحياء الأخضر %s، الأحمر %s"),
    "gunsmod.arena.status.ended": ("Round over", "انتهت الجولة"), "gunsmod.arena.status.idle": ("Ready", "جاهز"),
    "gunsmod.arena.loadout.mixed": ("Loadout: Mixed", "التسليح: مختلط"), "gunsmod.arena.loadout.rifles": ("Loadout: Rifles", "التسليح: بنادق"),
    "gunsmod.arena.loadout.snipers": ("Loadout: Snipers", "التسليح: قناصات"), "gunsmod.arena.loadout.shotguns": ("Loadout: Shotguns", "التسليح: شوزن"),
    "gunsmod.arena.loadout.pistols": ("Loadout: Pistols", "التسليح: مسدسات"), "gunsmod.arena.loadout.heavy": ("Loadout: Heavy", "التسليح: ثقيل"),
    "gunsmod.arena.loadout.explosives": ("Loadout: Explosives", "التسليح: متفجرات"),
    "gunsmod.hud.reloading": ("RELOADING", "جارِ التعبئة"), "gunsmod.hud.no_ammo": ("NO AMMO", "لا توجد ذخيرة"), "gunsmod.hud.reload": ("RELOAD [%s]", "عبّئ [%s]"),
    "gunsmod.hud.overheat": ("OVERHEATED", "سخونة زائدة"), "gunsmod.hud.heat": ("Barrel heat", "حرارة الماسورة"),
    "gunsmod.hud.drive_hint": ("W/S: throttle  A/D: steer  [%s] seat  [%s] horn", "W/S: سرعة  A/D: توجيه  [%s] المقعد  [%s] البوق"),
    "gunsmod.hud.seat_hint": ("[%s] change seat  [%s] horn", "[%s] تغيير المقعد  [%s] البوق"),
    "gunsmod.hud.unarmed": ("Unarmed", "بدون سلاح"),

    "gunsmod.ui.craft": ("CRAFT", "اصنع"), "gunsmod.ui.drag_hint": ("drag to rotate, scroll to zoom", "اسحب للتدوير وعجلة للتكبير"),
    "gunsmod.ui.damage": ("Damage", "الضرر"), "gunsmod.ui.rate": ("Rate", "السرعة"), "gunsmod.ui.accuracy": ("Accuracy", "الدقة"),
    "gunsmod.ui.range": ("Range", "المدى"), "gunsmod.ui.mobility": ("Mobility", "الحركة"), "gunsmod.ui.requires": ("Requires:", "المطلوب:"),
    "gunsmod.ui.place_gun": ("Put a gun in the slot below", "ضع سلاحاً في الخانة بالأسفل"), "gunsmod.ui.recoil": ("Control", "الثبات"),
    "gunsmod.ui.mag": ("Mag", "المخزن"), "gunsmod.ui.zoom": ("Zoom", "التكبير"), "gunsmod.ui.stop": ("STOP", "إيقاف"), "gunsmod.ui.start": ("START", "تشغيل"),
    "gunsmod.ui.recall": ("Recall", "استدعاء"), "gunsmod.ui.tag": ("Tag", "القلادة"), "gunsmod.ui.count": ("Soldiers", "الجنود"),
    "gunsmod.ui.delay": ("Respawn", "إعادة الظهور"), "gunsmod.ui.alive": ("Alive: %s / %s", "الأحياء: %s / %s"),
    "gunsmod.ui.need_tag": ("Insert a saved dog tag", "ضع قلادة محفوظة"), "gunsmod.ui.weapon": ("Gun", "سلاح"), "gunsmod.ui.armor": ("Armor", "درع"),
    "gunsmod.ui.face": ("Change face", "تغيير الوجه"), "gunsmod.ui.grenades": ("Grenades: %s +", "قنابل: %s +"),
    "gunsmod.tab.pistols": ("Pistol", "مسدس"), "gunsmod.tab.smgs": ("SMG", "رشاش"), "gunsmod.tab.rifles": ("Rifle", "بندقية"),
    "gunsmod.tab.shotguns": ("Shotgun", "شوزن"), "gunsmod.tab.snipers": ("Sniper", "قناصة"), "gunsmod.tab.heavy": ("Heavy", "ثقيل"),
    "gunsmod.tab.ammo": ("Ammo", "ذخيرة"), "gunsmod.tab.attachments": ("Mods", "إضافات"), "gunsmod.tab.gear": ("Gear", "عتاد"),
    "gunsmod.launch.title": ("LAUNCH CONTROL", "التحكم بالإطلاق"), "gunsmod.launch.missile": ("Missile", "الصاروخ"),
    "gunsmod.launch.no_missile": ("NOT LOADED", "غير محمّل"), "gunsmod.launch.target": ("Target", "الهدف"),
    "gunsmod.launch.distance": ("Distance: %s blocks", "المسافة: %s بلوكة"), "gunsmod.launch.no_target": ("NO TARGET", "لا يوجد هدف"),
    "gunsmod.launch.state.0": ("STANDBY", "استعداد"), "gunsmod.launch.state.1": ("ARMED", "مُسلّح"), "gunsmod.launch.state.2": ("ERECTING", "رفع المنصة"),
    "gunsmod.launch.state.3": ("IGNITION", "إشعال"), "gunsmod.launch.state.4": ("RELOADING RAIL", "إعادة المنصة"),
    "gunsmod.launch.coords": ("Target coordinates X / Y / Z", "إحداثيات الهدف X / Y / Z"), "gunsmod.launch.set": ("SET TARGET", "تحديد الهدف"),
    "gunsmod.launch.hint": ("Or: sneak + use the remote", "أو: شفت + كليك يمين بالريموت"), "gunsmod.launch.hint2": ("on any block you can see", "على أي بلوكة تراها"),
    "gunsmod.launch.panel": ("LAUNCH", "إطلاق"), "gunsmod.launch.fire": ("FIRE", "إطلاق"), "gunsmod.launch.press": ("PRESS TO LAUNCH", "اضغط للإطلاق"),
    "gunsmod.launch.arming": ("Arming...", "جارِ التسليح..."), "gunsmod.launch.lift": ("Lift the safety cover", "ارفع الغطاء الواقي"),
    "gunsmod.launch.disarm": ("DISARM", "فك التسليح"), "gunsmod.launch.launching": ("LAUNCHING", "جارِ الإطلاق"),
    "key.gunsmod.reload": ("Reload", "تعبئة السلاح"), "key.gunsmod.seat": ("Jeep: change seat", "الجيب: تغيير المقعد"),
    "key.gunsmod.horn": ("Jeep: horn", "الجيب: البوق"),
    "death.attack.gunsmod.bullet": ("%1$s was shot", "%1$s قُتل بالرصاص"), "death.attack.gunsmod.bullet.player": ("%1$s was shot by %2$s", "%1$s قُتل برصاص %2$s"),
    "death.attack.gunsmod.bullet.item": ("%1$s was shot by %2$s using %3$s", "%1$s قُتل برصاص %2$s بسلاح %3$s"),
    "death.attack.gunsmod.flames": ("%1$s was burnt to a crisp", "%1$s احترق"), "death.attack.gunsmod.flames.player": ("%1$s was burnt by %2$s", "%1$s أحرقه %2$s"),
    "death.attack.gunsmod.flames.item": ("%1$s was burnt by %2$s using %3$s", "%1$s أحرقه %2$s بـ %3$s"),
    "death.attack.gunsmod.roadkill": ("%1$s was run over", "%1$s دهسته سيارة"), "death.attack.gunsmod.roadkill.player": ("%1$s was run over by %2$s", "%1$s دهسه %2$s"),
    "death.attack.gunsmod.roadkill.item": ("%1$s was run over by %2$s", "%1$s دهسه %2$s"),
}
for k, (e, a) in T.items():
    EN[k] = e
    AR[k] = a

SUB = {"gunshot": ("Gunshot", "إطلاق نار"), "gunshot_suppressed": ("Suppressed shot", "طلقة مكتومة"), "grenade_launch": ("Grenade launched", "إطلاق قنبلة"),
       "rocket_launch": ("Rocket launched", "إطلاق صاروخ"), "flamethrower": ("Flamethrower roars", "هدير قاذف اللهب"), "dry_fire": ("Gun clicks", "نقرة سلاح فارغ"),
       "reload": ("Reloading", "تعبئة"), "pump": ("Shotgun pumped", "تلقيم الشوزن"), "jeep_engine": ("Engine", "محرك"), "jeep_start": ("Engine starts", "تشغيل المحرك"),
       "jeep_horn": ("Horn", "بوق"), "missile_launch": ("Missile launched", "إطلاق صاروخ موجه"), "missile_alarm": ("Launch alarm", "إنذار الإطلاق"),
       "ui_switch": ("Switch", "مفتاح"), "ui_beep": ("Beep", "صفارة"), "hitmarker": ("Hit", "إصابة"), "grenade_bounce": ("Grenade bounces", "ارتداد قنبلة"),
       "bullet_whiz": ("Bullet whizzes by", "رصاصة تمر بقربك"), "overheat": ("Gun overheats", "سخونة السلاح"), "mag_out": ("Magazine out", "إخراج المخزن"),
       "mag_in": ("Magazine in", "إدخال المخزن"), "bolt": ("Bolt racked", "سحب الأقسام"), "shell_insert": ("Shell inserted", "إدخال خرطوشة"),
       "break_open": ("Action opened", "فتح السلاح"), "explosion_big": ("Huge explosion", "انفجار ضخم"),
       "explosion_far": ("Distant explosion", "انفجار بعيد"), "missile_incoming": ("Incoming missile!", "صاروخ قادم!"),
       "tank_engine": ("Tank engine", "محرك الدبابة"), "tank_fire": ("Tank gun fires", "إطلاق مدفع الدبابة"), "tank_reload": ("Gun loaded", "تلقيم المدفع"),
       "heli_rotor": ("Helicopter rotor", "مروحة الهليكوبتر"), "heli_start": ("Turbine starts", "تشغيل التوربين"),
       "heli_rocket": ("Rocket fired", "إطلاق صاروخ"), "heli_gun": ("Chin gun fires", "إطلاق الرشاش الأمامي")}
sounds = json.load(open(os.path.join(A, "sounds.json")))
for name, s in sounds.items():
    key = s["subtitle"]
    short = key.split(".")[-1]
    e, a = SUB.get(short, (short.replace("_", " ").capitalize(), short))
    EN[key] = e
    AR[key] = a

write(os.path.join(A, "lang", "en_us.json"), dict(sorted(EN.items())))
write(os.path.join(A, "lang", "ar_sa.json"), dict(sorted(AR.items())))
write(os.path.join(A, "lang", "ar_iq.json"), dict(sorted(AR.items())))
print(len(EN), "translations")
