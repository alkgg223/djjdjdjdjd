"""Merges UI / block / message translations (English + Arabic) into the mod lang files.

Run after build_assets.py (which writes the vehicle related keys). Existing keys are kept unless redefined here.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = f"{ROOT}/src/main/resources/assets/trafficcraft"

EN, AR = {}, {}


def t(key, en, ar):
    EN[key] = en
    AR[key] = ar


# ------------------------------------------------------------------ blocks
BLOCKS = {
    'asphalt': ('Asphalt', 'أسفلت'), 'asphalt_dark': ('Dark Asphalt', 'أسفلت داكن'), 'asphalt_slab': ('Asphalt Slab', 'بلاطة أسفلت'),
    'sidewalk_slab': ('Sidewalk Slab', 'بلاطة رصيف'), 'sidewalk': ('Sidewalk', 'رصيف'), 'concrete_block': ('Concrete Block', 'كتلة خرسانية'),
    'curb': ('Curb', 'حافة رصيف'), 'drain_cover': ('Drain Cover', 'غطاء صرف'),
    'sign_stop': ('Stop Sign', 'إشارة قف'), 'sign_give_way': ('Give Way Sign', 'إشارة أفضلية المرور'),
    'sign_speed_30': ('Speed Limit 30', 'حد السرعة 30'), 'sign_speed_40': ('Speed Limit 40', 'حد السرعة 40'),
    'sign_speed_50': ('Speed Limit 50', 'حد السرعة 50'), 'sign_speed_60': ('Speed Limit 60', 'حد السرعة 60'),
    'sign_speed_70': ('Speed Limit 70', 'حد السرعة 70'), 'sign_speed_80': ('Speed Limit 80', 'حد السرعة 80'),
    'sign_speed_100': ('Speed Limit 100', 'حد السرعة 100'), 'sign_speed_120': ('Speed Limit 120', 'حد السرعة 120'),
    'sign_no_entry': ('No Entry Sign', 'إشارة ممنوع الدخول'), 'sign_no_parking': ('No Parking Sign', 'إشارة ممنوع الوقوف'),
    'sign_no_stopping': ('No Stopping Sign', 'إشارة ممنوع التوقف'), 'sign_roundabout': ('Roundabout Sign', 'إشارة دوّار'),
    'sign_turn_left': ('Turn Left Sign', 'إشارة انعطف يساراً'), 'sign_turn_right': ('Turn Right Sign', 'إشارة انعطف يميناً'),
    'sign_ahead_only': ('Ahead Only Sign', 'إشارة الاتجاه للأمام فقط'), 'sign_keep_right': ('Keep Right Sign', 'إشارة الزم اليمين'),
    'sign_pedestrians': ('Pedestrians Sign', 'إشارة مشاة'), 'sign_roadworks': ('Roadworks Sign', 'إشارة أعمال طرق'),
    'sign_traffic_lights': ('Traffic Lights Ahead Sign', 'إشارة إشارات ضوئية أمامك'), 'sign_danger': ('Danger Sign', 'إشارة خطر'),
    'sign_children': ('Children Sign', 'إشارة أطفال'), 'sign_parking': ('Parking Sign', 'إشارة موقف سيارات'),
    'sign_disabled_parking': ('Disabled Parking Sign', 'إشارة موقف ذوي الإعاقة'), 'sign_one_way': ('One Way Sign', 'إشارة اتجاه واحد'),
    'sign_crossing': ('Pedestrian Crossing Sign', 'إشارة ممر مشاة'), 'sign_highway': ('Highway Sign', 'إشارة طريق سريع'),
    'sign_fuel': ('Fuel Station Sign', 'إشارة محطة وقود'), 'sign_border': ('Border Checkpoint Sign', 'إشارة نقطة حدود'),
    'sign_hospital': ('Hospital Sign', 'إشارة مستشفى'), 'sign_bus_stop': ('Bus Stop Sign Plate', 'لوحة موقف حافلات'),
    'direction_sign': ('Direction Sign', 'لوحة اتجاهات'), 'road_name_sign': ('Road Name Sign', 'لوحة اسم الشارع'),
    'concrete_barrier': ('Concrete Barrier', 'حاجز خرساني'), 'concrete_divider': ('Concrete Divider', 'فاصل خرساني'),
    'guardrail': ('Guardrail', 'حاجز حماية'), 'road_barrier': ('Road Barrier', 'حاجز طريق'), 'road_cone': ('Traffic Cone', 'مخروط مروري'),
    'construction_sign': ('Construction Sign', 'لوحة أعمال إنشاء'), 'roadworks_light': ('Roadworks Light', 'ضوء أعمال الطرق'),
    'bollard': ('Bollard', 'عمود حاجز'), 'bench': ('Bench', 'مقعد'), 'trash_bin': ('Trash Bin', 'سلة مهملات'),
    'parking_meter': ('Parking Meter', 'عداد موقف'), 'emergency_call_box': ('Emergency Call Box', 'صندوق اتصال طوارئ'),
    'bus_stop_sign': ('Bus Stop Sign', 'علامة موقف حافلات'), 'bus_shelter': ('Bus Shelter', 'مظلة موقف حافلات'),
    'flower_planter': ('Flower Planter', 'حوض زهور'), 'roadside_tree': ('Roadside Tree', 'شجرة جانب الطريق'),
    'street_lamp': ('Street Lamp', 'عمود إنارة'), 'security_camera': ('Security Camera', 'كاميرا مراقبة'),
    'fuel_pump': ('Fuel Pump', 'مضخة وقود'), 'ev_charger': ('EV Charger', 'شاحن سيارات كهربائية'),
    'payment_terminal': ('Payment Terminal', 'جهاز دفع'), 'fuel_tank': ('Underground Fuel Tank', 'خزان وقود أرضي'),
    'canopy_light': ('Canopy Light', 'إضاءة المظلة'), 'canopy_roof': ('Canopy Roof', 'سقف المظلة'),
    'ceiling_light': ('Ceiling Light', 'ضوء سقف'), 'cashier_counter': ('Cashier Counter', 'كاونتر المحاسب'),
    'store_shelf': ('Store Shelf', 'رف متجر'), 'shop_sign_block': ('Shop Sign', 'لافتة المتجر'), 'booth_wall': ('Booth Wall', 'جدار الكشك'),
    'booth_desk': ('Border Booth Desk', 'مكتب كشك الحدود'), 'warning_light': ('Warning Light', 'ضوء تحذير'),
    'traffic_light': ('Traffic Light', 'إشارة مرور ضوئية'), 'pedestrian_signal': ('Pedestrian Signal', 'إشارة مشاة ضوئية'),
    'barrier_gate': ('Barrier Gate', 'بوابة حاجز'), 'barrier_arm': ('Barrier Arm', 'ذراع الحاجز'),
    'traffic_control_panel': ('Traffic Control Panel', 'لوحة التحكم بالإشارات'),
}
for k, (en, ar) in BLOCKS.items():
    t(f'block.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ items / entities / tabs
t('item.trafficcraft.road_planner', 'Road Planner', 'مخطط الطرق')
t('item.trafficcraft.fuel_nozzle', 'Fuel Nozzle', 'مسدس الوقود')
t('item.trafficcraft.passport', 'Passport', 'جواز سفر')
t('item.trafficcraft.inspection_stamp', 'Inspection Stamp', 'ختم التفتيش')
t('entity.trafficcraft.pedestrian', 'Pedestrian', 'أحد المشاة')
t('itemGroup.trafficcraft.roads', 'TrafficCraft - Roads & City', 'ترافيك كرافت - الطرق والمدينة')
t('tooltip.trafficcraft.planner1', 'Right-click: use the current mode  |  Sneak + right-click air: settings', 'نقر يمين: استخدم الوضع الحالي | انحناء + نقر يمين في الهواء: الإعدادات')
t('tooltip.trafficcraft.planner2', 'Sneak + scroll / left-click air: change mode', 'انحناء + عجلة الفأرة / نقر يسار في الهواء: تغيير الوضع')

for k, (en, ar, den, dar) in {
        'gas_station': ('Gas Station Kit', 'عدة محطة وقود', 'Pumps, canopy, tank, shop with cashier and attendant', 'مضخات ومظلة وخزان ومتجر مع محاسب وعامل تعبئة'),
        'parking': ('Parking Lot Kit', 'عدة موقف سيارات', '12 marked spaces, barrier, payment machine, camera, lamps', '12 موقفاً مخططاً، حاجز، جهاز دفع، كاميرا، إنارة'),
        'border': ('Border Checkpoint Kit', 'عدة نقطة حدود', 'Use on a road: booth, officer, two barrier gates', 'استخدمه على طريق: كشك وضابط وبوابتا حاجز'),
        'bus_stop': ('Bus Stop Kit', 'عدة موقف حافلات', 'Shelter, sign, bench and a pedestrian destination', 'مظلة ولوحة ومقعد ووجهة للمشاة')}.items():
    t(f'item.trafficcraft.kit_{k}', en, ar)
    t(f'tooltip.trafficcraft.kit_{k}', den, dar)
t('tooltip.trafficcraft.kit', 'Right-click the ground; it is built facing where you look and connected to the nearest road',
  'انقر يميناً على الأرض؛ يُبنى باتجاه نظرك ويتصل بأقرب طريق')
t('gui.trafficcraft.twoway', 'two-way', 'اتجاهين')
t('gui.trafficcraft.oneway_ab', 'one-way A → B', 'اتجاه واحد أ ← ب')
t('gui.trafficcraft.oneway_ba', 'one-way B → A', 'اتجاه واحد ب ← أ')
t('msg.trafficcraft.pick_junction_road', 'Click a road where it enters a junction', 'انقر على طريق عند دخوله إلى تقاطع')
t('msg.trafficcraft.turn_from', 'From road #%s - now click the road to turn into', 'من الطريق رقم %s - الآن انقر على الطريق الذي تريد اللف إليه')
t('msg.trafficcraft.turn_allowed', 'Turn #%s → #%s ALLOWED', 'اللف من %s إلى %s مسموح')
t('msg.trafficcraft.turn_banned', 'Turn #%s → #%s BANNED', 'اللف من %s إلى %s ممنوع')
t('msg.trafficcraft.prio_first', 'Priority road #%s - now click the road that must give way', 'طريق الأولوية رقم %s - الآن انقر على الطريق الذي يجب أن ينتظر')
t('msg.trafficcraft.prio_set', 'Road #%s now has priority over road #%s', 'الطريق %s له الأولوية على الطريق %s')
t('msg.trafficcraft.prio_removed', 'Priority #%s over #%s removed', 'تمت إزالة أولوية %s على %s')
t('tooltip.trafficcraft.category.motorcycle', 'Motorcycle', 'دراجة نارية')
t('hud.trafficcraft.hint.rev', 'Space + W: rev the engine', 'المسافة + W: تدوير المحرك (ريف)')
t('msg.trafficcraft.piece_created', '%s built', 'تم بناء %s')
t('msg.trafficcraft.passenger', 'You are a passenger - the driver takes you along. Sneak to get out.', 'أنت راكب - السائق يأخذك معه. انحنِ للنزول.')
t('msg.trafficcraft.last_stop', 'Last stop - please get out (sneak)', 'المحطة الأخيرة - انزل من فضلك (انحناء)')
t('msg.trafficcraft.kit_placed', '%s placed', 'تم وضع %s')
t('msg.trafficcraft.kit_needs_road', 'Use the border kit on an existing road', 'استخدم عدة الحدود على طريق موجود')

# ------------------------------------------------------------------ tool modes
MODES = {'road': ('Road', 'طريق'), 'path': ('Footpath', 'ممر مشاة'), 'edit': ('Edit', 'تعديل'), 'delete': ('Delete', 'حذف'),
         'curve': ('Curve', 'منحنى'), 'roundabout': ('Roundabout', 'دوّار'), 'lights': ('Traffic lights', 'إشارات ضوئية'), 'node': ('Node', 'عقدة'),
         'turn_left': ('Sharp left turn', 'لفّة حادة لليسار'), 'turn_right': ('Sharp right turn', 'لفّة حادة لليمين'),
         't_junction': ('T-junction', 'تقاطع ثلاثي (T)'), 'crossroads': ('Crossroads', 'تقاطع رباعي'),
         'turns': ('Turn rules (ban / allow a turn)', 'قواعد اللف (منع / سماح)'), 'priority': ('Priority (who goes first)', 'الأولوية (من يمر أولاً)')}
for k, (en, ar) in MODES.items():
    t(f'mode.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ node types (also used as enum options)
NODES = {'road': ('Road node', 'عقدة طريق'), 'spawn': ('Vehicle spawn', 'نقطة ظهور المركبات'), 'despawn': ('Vehicle despawn', 'نقطة اختفاء المركبات'),
         'parking': ('Parking lot', 'موقف سيارات'), 'border': ('Border checkpoint', 'نقطة حدود'), 'gas_station': ('Gas station', 'محطة وقود'),
         'bus_stop': ('Bus stop', 'موقف حافلات'), 'ped': ('Footpath node', 'عقدة مشاة'), 'ped_spawn': ('Pedestrian spawn', 'نقطة ظهور المشاة'),
         'ped_dest': ('Pedestrian destination', 'وجهة المشاة'), 'park_spot': ('Roadside parking spot', 'موقف جانب الطريق')}
for k, (en, ar) in NODES.items():
    t(f'node.trafficcraft.{k}', en, ar)
    t(f'opt.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ lanes
LANES = {'normal': ('Normal lane', 'مسار عادي'), 'left_only': ('Left turn only', 'انعطاف يسار فقط'), 'right_only': ('Right turn only', 'انعطاف يمين فقط'),
         'bus': ('Bus lane', 'مسار حافلات'), 'parking': ('Parking lane', 'مسار وقوف')}
for k, (en, ar) in LANES.items():
    t(f'lane.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ enum options
OPTS = {'perpendicular': ('Perpendicular', 'عمودي'), 'angled': ('Angled', 'مائل'), 'parallel': ('Parallel', 'موازي'),
        'auto': ('Auto', 'تلقائي'), 'manual': ('Manual', 'يدوي'), 'flash': ('Flashing', 'وميض'), 'off': ('Off', 'إيقاف'), 'next': ('Next', 'التالي'),
        'both': ('Both', 'الجانبين'), 'right': ('Right', 'يمين'), 'left': ('Left', 'يسار'),
        'always': ('Always', 'دائماً'), 'day': ('Day', 'نهاراً'), 'night': ('Night', 'ليلاً'), 'rush': ('Rush hour', 'ساعة الذروة')}
for k, (en, ar) in OPTS.items():
    t(f'opt.trafficcraft.{k}', en, ar)
for k, (en, ar) in MODES.items():
    t(f'opt.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ properties
PROPS = {
    'adults': ('Adults', 'بالغون'), 'attendants': ('Fuel attendants', 'عمال تعبئة'), 'business': ('Business people', 'رجال أعمال'),
    'children': ('Children', 'أطفال'), 'clear': ('Clear terrain', 'تنظيف الأرض'), 'dest': ('Destination', 'وجهة'), 'elderly': ('Elderly', 'كبار السن'),
    'enabled': ('Enabled', 'مفعّل'), 'gate': ('Gate', 'بوابة'), 'leftPhase': ('Protected left-turn phase', 'مرحلة انعطاف يسار محمية'),
    'oneway': ('One way', 'اتجاه واحد'), 'overtake': ('Overtaking allowed', 'التجاوز مسموح'), 'passport': ('Passport required', 'الجواز مطلوب'),
    'pave': ('Pave road', 'رصف الطريق'), 'random': ('Random destinations', 'وجهات عشوائية'), 'wait': ('Wait / look before crossing', 'انتظر وانظر قبل العبور'),
    'workers': ('Workers', 'عمال'), 'layout': ('Layout', 'التخطيط'), 'mode': ('Mode', 'الوضع'), 'nodeType': ('Node type', 'نوع العقدة'),
    'side': ('Side', 'الجانب'), 'time': ('Active time', 'وقت التفعيل'), 'pathWidth': ('Path width', 'عرض الممر'), 'price': ('Fuel price', 'سعر الوقود'),
    'rowYaw': ('Row angle (-1 = auto)', 'زاوية الصف (-1 = تلقائي)'), 'speed': ('Speed', 'السرعة'), 'width': ('Width', 'العرض'),
    'crosswalk': ('Crosswalk', 'ممر مشاة'), 'lights': ('Traffic lights', 'إشارات ضوئية'), 'priority': ('Priority road', 'طريق ذو أولوية'),
    'stop': ('Stop sign', 'إشارة قف'), 'yield': ('Give way', 'أفضلية المرور'), 'allred': ('All-red time (s)', 'زمن الأحمر الكلي (ث)'),
    'density': ('Density %', 'الكثافة %'), 'denyChance': ('Secondary inspection %', 'نسبة التفتيش الثانوي %'), 'disabled': ('Disabled spaces', 'مواقف ذوي الإعاقة'),
    'dwell': ('Stop time (s)', 'مدة التوقف (ث)'), 'dwellMax': ('Max stay (s)', 'أقصى مدة بقاء (ث)'), 'dwellMin': ('Min stay (s)', 'أقل مدة بقاء (ث)'),
    'green': ('Green (s)', 'الأخضر (ث)'), 'impatient': ('Impatient drivers %', 'سائقون غير صبورين %'), 'inspection': ('Inspection time (s)', 'مدة التفتيش (ث)'),
    'interval': ('Spawn interval (s)', 'فترة الظهور (ث)'), 'lanes': ('Lanes', 'المسارات'), 'leftGreen': ('Left arrow green (s)', 'سهم اليسار الأخضر (ث)'),
    'max': ('Maximum', 'الحد الأقصى'), 'maxSpeed': ('Max speed (km/h)', 'السرعة القصوى (كم/س)'), 'minSpeed': ('Min speed (km/h)', 'السرعة الدنيا (كم/س)'),
    'normal': ('Normal drivers %', 'سائقون عاديون %'), 'officers': ('Officers', 'الضباط'), 'parkChance': ('Chance to park %', 'احتمال الوقوف %'),
    'patient': ('Patient drivers %', 'سائقون صبورون %'), 'radius': ('Radius', 'نصف القطر'), 'reserved': ('Reserved spaces', 'مواقف محجوزة'),
    'serviceTime': ('Service time (s)', 'مدة الخدمة (ث)'), 'sidewalk': ('Sidewalk width', 'عرض الرصيف'), 'spaces': ('Parking spaces', 'عدد المواقف'),
    'stayMax': ('Max stay (s)', 'أقصى مدة بقاء (ث)'), 'stayMin': ('Min stay (s)', 'أقل مدة بقاء (ث)'), 'yellow': ('Yellow (s)', 'الأصفر (ث)'),
    'name': ('Name', 'الاسم'), 'visible': ('Show markings', 'إظهار الخطوط'), 'length': ('Spot length', 'طول الموقف'),
    'sharpTurns': ('Allow sharp turns between branches', 'السماح باللف الحاد بين الفروع'), 'armLength': ('Arm length (turns / junctions)', 'طول الأذرع (اللفّات والتقاطعات)'), 'model': ('Count: %s', 'العدد: %s'), 'no_trucks': ('No trucks', 'ممنوع الشاحنات'),
}
for k, (en, ar) in PROPS.items():
    t(f'prop.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ GUI
GUI = {
    'cancel': ('Cancel', 'إلغاء'), 'save': ('Save', 'حفظ'), 'save_repave': ('Save & repave', 'حفظ وإعادة الرصف'), 'delete': ('Delete', 'حذف'),
    'on': ('On', 'تشغيل'), 'off': ('Off', 'إيقاف'), 'type': ('Type', 'النوع'), 'dir_ab': ('A → B', 'أ ← ب'), 'dir_ba': ('B → A', 'ب ← أ'),
    'remove_lane': ('Remove lane', 'إزالة مسار'), 'tool_title': ('Road Planner settings', 'إعدادات مخطط الطرق'),
    'node_title': ('Node #%s', 'العقدة رقم %s'), 'edge_title': ('Road #%s', 'الطريق رقم %s'), 'sign_title': ('Sign text', 'نص اللوحة'),
    'lights_title': ('Traffic light control', 'التحكم بالإشارات الضوئية'), 'no_lights': ('No signalised junctions yet', 'لا توجد تقاطعات بإشارات بعد'),
    'workplace.cashier': ('Gas station', 'محطة الوقود'), 'workplace.officer': ('Border checkpoint', 'نقطة الحدود'),
    'claim': ('Buy / claim this station', 'شراء / امتلاك المحطة'), 'work_as': ('Work as %s', 'اعمل كـ %s'), 'price': ('Price', 'السعر'),
    'collect': ('Collect till', 'جمع الإيراد'), 'order_fuel': ('Order fuel delivery (10 emeralds)', 'طلب شحنة وقود (10 زمرد)'),
    'stop_work': ('Stop working', 'إنهاء العمل'), 'owner': ('Owner: %s', 'المالك: %s'),
    'station_info': ('Price %s / L   |   Till %s   |   Tank %s L', 'السعر %s / لتر   |   الإيراد %s   |   الخزان %s لتر'),
    'officer_help': ('Use the Inspection Stamp on waiting vehicles. Sneak to send to secondary inspection.', 'استخدم ختم التفتيش على المركبات المنتظرة. انحنِ لإرسالها لتفتيش ثانوي.'),
    'your_role': ('Your job: %s', 'وظيفتك: %s'),
}
for k, (en, ar) in GUI.items():
    t(f'gui.trafficcraft.{k}', en, ar)
for k, (en, ar) in {'cashier': ('Cashier', 'محاسب'), 'attendant': ('Fuel attendant', 'عامل تعبئة'), 'officer': ('Border officer', 'ضابط حدود'),
                    'owner': ('Owner', 'مالك')}.items():
    t(f'role.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ NPC lines
t('npc.trafficcraft.cashier', 'Cashier', 'المحاسب')
t('npc.trafficcraft.attendant_hello', 'Attendant: Pull up to a pump and I will fill her up!', 'عامل التعبئة: قف عند المضخة وسأملأ لك الخزان!')
t('npc.trafficcraft.officer_hello', 'Officer: Have your passport ready at the barrier.', 'الضابط: جهّز جواز سفرك عند الحاجز.')
for i, (en, ar) in enumerate([('Hello!', 'مرحباً!'), ('Nice day, isn\'t it?', 'يوم جميل، أليس كذلك؟'), ('Watch the traffic!', 'انتبه للسيارات!'),
                              ('I\'m late for the bus...', 'تأخرت على الحافلة...')]):
    t(f'npc.trafficcraft.ped_hello.{i}', en, ar)

# ------------------------------------------------------------------ messages
MSG = {
    'approved': ('Vehicle approved - barrier opening', 'تمت الموافقة على المركبة - الحاجز يفتح'),
    'barrier_cfg': ('Barrier: %s, arm length %s', 'الحاجز: %s، طول الذراع %s'),
    'border_ok': ('Documents checked. Have a safe trip!', 'تم فحص الوثائق. رحلة آمنة!'),
    'border_passport': ('You need a passport to cross this border (buy one from a cashier)', 'تحتاج جواز سفر لعبور هذه الحدود (اشترِه من المحاسب)'),
    'border_stop': ('Border control - please wait for the inspection', 'مراقبة الحدود - انتظر التفتيش من فضلك'),
    'border_wait_officer': ('Waiting for the officer on duty to stamp your documents', 'بانتظار الضابط المناوب لختم وثائقك'),
    'curve_pick': ('Select a road first (right-click on it)', 'اختر طريقاً أولاً (نقر يمين عليه)'),
    'curve_reset': ('Curve reset to automatic', 'تمت إعادة المنحنى للوضع التلقائي'),
    'curve_selected': ('Road selected - right-click where the curve should pass', 'تم اختيار الطريق - انقر يميناً حيث يجب أن يمر المنحنى'),
    'curve_set': ('Curve updated', 'تم تحديث المنحنى'),
    'demo_built': ('Demo town built', 'تم بناء المدينة التجريبية'),
    'edge_deleted': ('Road deleted', 'تم حذف الطريق'), 'node_deleted': ('Node deleted', 'تم حذف العقدة'),
    'node_created': ('Node #%s created', 'تم إنشاء العقدة رقم %s'),
    'road_created': ('Road created (%s m)', 'تم إنشاء طريق (%s م)'), 'path_created': ('Footpath created (%s m)', 'تم إنشاء ممر مشاة (%s م)'),
    'roundabout_created': ('Roundabout created (radius %s)', 'تم إنشاء دوّار (نصف القطر %s)'),
    'empty_hand': ('Your hand must be empty to take the nozzle', 'يجب أن تكون يدك فارغة لأخذ المسدس'),
    'fuel_delivered': ('A fuel tanker delivered 5000 L', 'وصلت شاحنة وقود بـ 5000 لتر'),
    'fuel_done': ('Refuelling finished: %s L', 'انتهت التعبئة: %s لتر'),
    'fuel_paid': ('Paid for %s L: %s emeralds', 'تم الدفع مقابل %s لتر: %s زمرد'),
    'lights_on': ('Traffic lights installed', 'تم تركيب الإشارات الضوئية'), 'lights_off': ('Traffic lights removed', 'تمت إزالة الإشارات الضوئية'),
    'need_emeralds': ('You need %s emeralds', 'تحتاج %s زمرد'),
    'needs_charger': ('This vehicle is electric - use an EV charger', 'هذه المركبة كهربائية - استخدم شاحناً'),
    'needs_fuel': ('This vehicle needs fuel, not a charger', 'هذه المركبة تحتاج وقوداً وليس شاحناً'),
    'no_permission': ('You are not allowed to edit roads', 'لا تملك صلاحية تعديل الطرق'),
    'no_station': ('No station node nearby', 'لا توجد عقدة محطة قريبة'),
    'not_waiting': ('This vehicle is not waiting for inspection', 'هذه المركبة لا تنتظر التفتيش'),
    'nothing_here': ('Nothing here', 'لا يوجد شيء هنا'),
    'now_working': ('You are now working as %s', 'أنت تعمل الآن كـ %s'),
    'nozzle_in_use': ('Someone else is using this pump', 'شخص آخر يستخدم هذه المضخة'),
    'refuel_start': ('Refuelling...', 'جارٍ التعبئة...'),
    'saved': ('Saved', 'تم الحفظ'), 'selection_cleared': ('Selection cleared', 'تم إلغاء التحديد'),
    'sent_secondary': ('Vehicle sent to secondary inspection', 'تم تحويل المركبة إلى التفتيش الثانوي'),
    'station_owned': ('You now own this station', 'أصبحت مالك هذه المحطة'),
    'station_taken': ('This station belongs to %s', 'هذه المحطة ملك لـ %s'),
    'stopped_working': ('You stopped working', 'لقد أنهيت عملك'),
    'tank_full': ('Tank full', 'الخزان ممتلئ'),
    'terminal_info': ('Fuel price: %s emerald / L - pay at the pump', 'سعر الوقود: %s زمرد / لتر - ادفع عند المضخة'),
    'till_collected': ('Collected %s emeralds', 'تم جمع %s زمرد'),
    'tip': ('Customer tip: +1 emerald', 'بقشيش من الزبون: +1 زمرد'),
}
for k, (en, ar) in MSG.items():
    t(f'msg.trafficcraft.{k}', en, ar)

# ------------------------------------------------------------------ config screen
CFG = {
    'vehicles': ('Vehicles', 'المركبات'), 'client': ('Graphics & sound', 'الرسوميات والصوت'), 'traffic': ('Traffic & AI', 'المرور والذكاء الاصطناعي'),
    'stepHeight': ('Step height', 'ارتفاع الصعود'), 'fuelConsumption': ('Fuel consumption', 'استهلاك الوقود'), 'requireFuel': ('Require fuel', 'يتطلب وقوداً'),
    'damageEntities': ('Vehicles hurt entities', 'المركبات تؤذي الكائنات'), 'dynamicHeadlights': ('Dynamic headlight lighting', 'إضاءة ديناميكية للمصابيح'),
    'seatedScale': ('Seated player scale', 'حجم اللاعب الجالس'), 'showHud': ('Show driving HUD', 'إظهار واجهة القيادة'),
    'engineVolume': ('Engine volume', 'صوت المحرك'), 'renderDistance': ('Vehicle render distance', 'مسافة عرض المركبات'),
    'detailDistance': ('Detail / interior distance', 'مسافة التفاصيل والمقصورة'), 'headlightBeams': ('Headlight beams', 'أشعة المصابيح'),
    'maxAiVehicles': ('Max AI vehicles', 'أقصى عدد للمركبات الذكية'), 'maxPedestrians': ('Max pedestrians', 'أقصى عدد للمشاة'),
    'trafficDensity': ('Traffic density', 'كثافة المرور'), 'pedestrianDensity': ('Pedestrian density', 'كثافة المشاة'),
    'vehicleSpawnInterval': ('Vehicle spawn interval (s)', 'فترة ظهور المركبات (ث)'), 'pedestrianSpawnInterval': ('Pedestrian spawn interval (s)', 'فترة ظهور المشاة (ث)'),
    'simulationDistance': ('AI simulation distance', 'مسافة محاكاة الذكاء الاصطناعي'), 'despawnDistance': ('Despawn distance', 'مسافة الإزالة'),
    'aggression': ('Driver aggression', 'عدوانية السائقين'), 'horns': ('AI horns', 'أبواق السائقين'), 'hornPatience': ('Horn patience (s)', 'صبر البوق (ث)'),
    'overtakeChance': ('Overtake chance', 'احتمال التجاوز'), 'breakdownChance': ('Breakdown chance', 'احتمال العطل'),
    'aiFuel': ('AI uses fuel', 'المركبات الذكية تستهلك الوقود'), 'parkChance': ('Parking chance', 'احتمال الوقوف'),
    'lightGreenSeconds': ('Default green time (s)', 'زمن الأخضر الافتراضي (ث)'), 'borderInspectionSeconds': ('Border inspection time (s)', 'مدة تفتيش الحدود (ث)'),
    'borderDenyChance': ('Secondary inspection chance', 'احتمال التفتيش الثانوي'), 'defaultRoadWidth': ('Default road width', 'عرض الطريق الافتراضي'),
    'defaultSpeedLimit': ('Default speed limit', 'حد السرعة الافتراضي'), 'streetLampsAuto': ('Automatic street lamps', 'إنارة شوارع تلقائية'),
    'aiTickNear': ('AI update interval near', 'فترة تحديث الذكاء القريب'), 'aiTickFar': ('AI update interval far', 'فترة تحديث الذكاء البعيد'),
}
for k, (en, ar) in CFG.items():
    t(f'trafficcraft.configuration.{k}', en, ar)
t('trafficcraft.configuration.section.trafficcraft.common.toml', 'TrafficCraft settings', 'إعدادات ترافيك كرافت')
t('trafficcraft.configuration.section.trafficcraft.common.toml.title', 'TrafficCraft settings', 'إعدادات ترافيك كرافت')

for lang, d in (('en_us', EN), ('ar_sa', AR), ('ar_iq', AR)):
    p = f"{A}/lang/{lang}.json"
    cur = json.load(open(p, encoding='utf-8')) if os.path.exists(p) else {}
    cur.update(d)
    json.dump(cur, open(p, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('lang keys:', len(EN))
