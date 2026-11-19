"""Copies generated vehicle data into the mod resources and writes item models, lang files and sounds.json."""
import json, os, shutil, sys
from PIL import Image, ImageDraw

GEN = sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/vehout'
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'trafficcraft')
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'vehgen'))
import specs

ids = [s['id'] for s in specs.SPECS if os.path.exists(f"{GEN}/mesh/{s['id']}.json")]
for d in ('vehicles', 'textures/vehicle', 'textures/item', 'models/item', 'lang', 'blockstates', 'sounds'):
    os.makedirs(os.path.join(A, d), exist_ok=True)
for i in ids:
    for suf in ('.tcm', '_lod1.tcm', '.json'):
        src = f"{GEN}/mesh/{i}{suf}"
        if os.path.exists(src):
            shutil.copy(src, f"{A}/vehicles/{i}{suf}")
    shutil.copy(f"{GEN}/icon/{i}.png", f"{A}/textures/item/vehicle_{i}.png")
json.dump(ids, open(f"{A}/vehicles/index.json", 'w'))
shutil.copy(f"{GEN}/atlas.png", f"{A}/textures/vehicle/atlas.png")
shutil.copy(f"{GEN}/dash.png", f"{A}/textures/vehicle/dash.png")

# simple item textures
def canister(path):
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([7, 8, 25, 30], 3, fill=(196, 34, 30, 255), outline=(110, 16, 14, 255))
    d.rectangle([10, 4, 16, 9], fill=(60, 60, 60, 255))
    d.rectangle([18, 5, 24, 8], fill=(110, 16, 14, 255))
    d.line([10, 14, 22, 26], fill=(150, 24, 20, 255), width=2)
    d.line([22, 14, 10, 26], fill=(150, 24, 20, 255), width=2)
    d.rectangle([8, 9, 10, 29], fill=(230, 80, 70, 255))
    im.save(path)

def key(path):
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([4, 4, 16, 20], 4, fill=(30, 32, 36, 255), outline=(10, 10, 12, 255))
    d.ellipse([8, 8, 12, 12], fill=(200, 40, 40, 255))
    d.rectangle([14, 11, 29, 14], fill=(190, 194, 200, 255))
    d.rectangle([24, 14, 26, 18], fill=(190, 194, 200, 255))
    d.rectangle([20, 14, 22, 17], fill=(190, 194, 200, 255))
    im.save(path)

canister(f"{A}/textures/item/fuel_canister.png")
key(f"{A}/textures/item/car_key.png")
for name in ['vehicle_' + i for i in ids] + ['fuel_canister', 'car_key']:
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'trafficcraft:item/{name}'}}, open(f"{A}/models/item/{name}.json", 'w'))
json.dump({'variants': {'': {'model': 'minecraft:block/air'}}}, open(f"{A}/blockstates/headlight_glow.json", 'w'))

# sounds
if os.path.exists(f"{A}/sounds"):
    shutil.rmtree(f"{A}/sounds")
shutil.copytree(f"{GEN}/sounds", f"{A}/sounds")
sj = json.load(open(f"{GEN}/sounds.json"))
for k, v in sj.items():
    dist = 24 if k.startswith('engine') else 64 if k.startswith('horn') else 112 if k.startswith('siren') else 16
    for s in v['sounds']:
        s['attenuation_distance'] = dist
json.dump(sj, open(f"{A}/sounds.json", 'w'), indent=1)

cats = {'sedan': ('Sedan', 'سيدان'), 'hatchback': ('Compact hatchback', 'هاتشباك صغيرة'), 'suv': ('SUV', 'دفع رباعي SUV'),
        'pickup': ('Pickup truck', 'بيك أب'), 'sports': ('Sports car', 'سيارة رياضية'), 'emergency': ('Emergency vehicle', 'سيارة طوارئ'),
        'taxi': ('Taxi', 'تاكسي'), 'van': ('Van', 'فان'), 'truck': ('Truck', 'شاحنة'), 'bus': ('Bus', 'حافلة')}
EN = {
    'itemGroup.trafficcraft.vehicles': 'TrafficCraft: Vehicles', 'item.trafficcraft.fuel_canister': 'Fuel Canister (20 L)',
    'item.trafficcraft.car_key': 'Car Key', 'entity.trafficcraft.vehicle': 'Vehicle', 'block.trafficcraft.headlight_glow': 'Headlight Glow',
    'key.categories.trafficcraft': 'TrafficCraft Vehicles', 'key.trafficcraft.horn': 'Horn', 'key.trafficcraft.lights': 'Headlights on/off',
    'key.trafficcraft.high_beam': 'High beam', 'key.trafficcraft.indicator_left': 'Left indicator', 'key.trafficcraft.indicator_right': 'Right indicator',
    'key.trafficcraft.hazard': 'Hazard lights', 'key.trafficcraft.engine': 'Engine start/stop', 'key.trafficcraft.interior_light': 'Interior light',
    'key.trafficcraft.siren': 'Siren & beacons', 'key.trafficcraft.gear_mode': 'Automatic/manual gearbox', 'key.trafficcraft.gear_up': 'Shift up (manual)',
    'key.trafficcraft.gear_down': 'Shift down (manual)', 'key.trafficcraft.vehicle_menu': 'Vehicle control panel',
    'msg.trafficcraft.refueled': 'Refuelled: %s / %s L', 'msg.trafficcraft.occupied': 'This vehicle is driven by someone else',
    'msg.trafficcraft.no_fuel': 'The tank is empty - use a fuel canister or a fuel pump',
    'hud.trafficcraft.starting': 'Starting engine...', 'hud.trafficcraft.engine_off': 'Engine off - press %s or W to start', 'hud.trafficcraft.empty': 'FUEL EMPTY',
    'hud.trafficcraft.hint.throttle': 'throttle/brake', 'hud.trafficcraft.hint.steer': 'steer', 'hud.trafficcraft.hint.space': 'Space',
    'hud.trafficcraft.hint.handbrake': 'handbrake', 'hud.trafficcraft.hint.engine': 'engine', 'hud.trafficcraft.hint.lights': 'lights',
    'hud.trafficcraft.hint.horn': 'horn', 'hud.trafficcraft.hint.indicators': 'indicators', 'hud.trafficcraft.hint.hazard': 'hazard',
    'hud.trafficcraft.hint.menu': 'controls',
    'gui.trafficcraft.engine': 'Engine', 'gui.trafficcraft.lights': 'Headlights', 'gui.trafficcraft.high_beam': 'High beam', 'gui.trafficcraft.interior': 'Interior light',
    'gui.trafficcraft.indicator_left': '< Left', 'gui.trafficcraft.hazard': 'Hazard', 'gui.trafficcraft.indicator_right': 'Right >', 'gui.trafficcraft.horn': 'Horn',
    'gui.trafficcraft.gear_mode': 'Manual gears', 'gui.trafficcraft.siren': 'Siren', 'gui.trafficcraft.gear_up': 'Gear +', 'gui.trafficcraft.gear_down': 'Gear -',
    'gui.trafficcraft.inventory': 'Inventory', 'gui.trafficcraft.close': 'Close',
    'trafficcraft.configuration.title': 'TrafficCraft settings',
}
AR = {
    'itemGroup.trafficcraft.vehicles': 'ترافك كرافت: المركبات', 'item.trafficcraft.fuel_canister': 'جالون وقود (20 لتر)', 'item.trafficcraft.car_key': 'مفتاح سيارة',
    'entity.trafficcraft.vehicle': 'مركبة', 'block.trafficcraft.headlight_glow': 'ضوء المصابيح',
    'key.categories.trafficcraft': 'ترافك كرافت - المركبات', 'key.trafficcraft.horn': 'الزمور', 'key.trafficcraft.lights': 'تشغيل/إطفاء الأضواء',
    'key.trafficcraft.high_beam': 'الضوء العالي', 'key.trafficcraft.indicator_left': 'الغماز الأيسر', 'key.trafficcraft.indicator_right': 'الغماز الأيمن',
    'key.trafficcraft.hazard': 'أضواء الطوارئ', 'key.trafficcraft.engine': 'تشغيل/إطفاء المحرك', 'key.trafficcraft.interior_light': 'الضوء الداخلي',
    'key.trafficcraft.siren': 'السارينة والأضواء', 'key.trafficcraft.gear_mode': 'ناقل أوتوماتيك/يدوي', 'key.trafficcraft.gear_up': 'رفع الغيار (يدوي)',
    'key.trafficcraft.gear_down': 'تنزيل الغيار (يدوي)', 'key.trafficcraft.vehicle_menu': 'لوحة تحكم المركبة',
    'msg.trafficcraft.refueled': 'تمت التعبئة: %s / %s لتر', 'msg.trafficcraft.occupied': 'هذه المركبة يقودها شخص آخر',
    'msg.trafficcraft.no_fuel': 'الخزان فارغ - استخدم جالون وقود أو مضخة وقود',
    'hud.trafficcraft.starting': 'جاري تشغيل المحرك...', 'hud.trafficcraft.engine_off': 'المحرك مطفأ - اضغط %s أو W للتشغيل', 'hud.trafficcraft.empty': 'نفد الوقود',
    'hud.trafficcraft.hint.throttle': 'بنزين/فرامل', 'hud.trafficcraft.hint.steer': 'توجيه', 'hud.trafficcraft.hint.space': 'المسافة',
    'hud.trafficcraft.hint.handbrake': 'فرامل اليد', 'hud.trafficcraft.hint.engine': 'المحرك', 'hud.trafficcraft.hint.lights': 'الأضواء',
    'hud.trafficcraft.hint.horn': 'الزمور', 'hud.trafficcraft.hint.indicators': 'الغمازات', 'hud.trafficcraft.hint.hazard': 'الطوارئ', 'hud.trafficcraft.hint.menu': 'التحكم',
    'gui.trafficcraft.engine': 'المحرك', 'gui.trafficcraft.lights': 'الأضواء', 'gui.trafficcraft.high_beam': 'الضوء العالي', 'gui.trafficcraft.interior': 'الضوء الداخلي',
    'gui.trafficcraft.indicator_left': '< يسار', 'gui.trafficcraft.hazard': 'طوارئ', 'gui.trafficcraft.indicator_right': 'يمين >', 'gui.trafficcraft.horn': 'الزمور',
    'gui.trafficcraft.gear_mode': 'غيار يدوي', 'gui.trafficcraft.siren': 'السارينة', 'gui.trafficcraft.gear_up': 'غيار +', 'gui.trafficcraft.gear_down': 'غيار -',
    'gui.trafficcraft.inventory': 'الحقيبة', 'gui.trafficcraft.close': 'إغلاق', 'trafficcraft.configuration.title': 'إعدادات ترافك كرافت',
}
for c, (en, ar) in cats.items():
    EN[f'tooltip.trafficcraft.category.{c}'] = en
    AR[f'tooltip.trafficcraft.category.{c}'] = ar
for s in specs.SPECS:
    if s['id'] in ids:
        EN[f"item.trafficcraft.vehicle_{s['id']}"] = s['name']
        AR[f"item.trafficcraft.vehicle_{s['id']}"] = s['name']
for lang, d in (('en_us', EN), ('ar_sa', AR), ('ar_iq', AR)):
    json.dump(d, open(f"{A}/lang/{lang}.json", 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('assets ok:', ids)
