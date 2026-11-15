"""English and Arabic language files for VehicleWorks."""
import json, os
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'vehicleworks', 'lang')
os.makedirs(OUT, exist_ok=True)
L = {}  # key -> (en, ar)
def t(k, en, ar): L[k] = (en, ar)

blocks = {
 'vehicle_core': ('Vehicle Core', 'نواة المركبة', 'Heart of every vehicle: defines the front. Right-click to assemble all connected blocks.', 'قلب كل مركبة: يحدد الاتجاه الأمامي. انقر بزر الفأرة الأيمن لتجميع كل الكتل المتصلة.'),
 'pilot_seat': ('Pilot Seat', 'مقعد الطيار', 'Sit here to drive or fly the vehicle.', 'اجلس هنا لقيادة المركبة أو الطيران بها.'),
 'passenger_seat': ('Passenger Seat', 'مقعد راكب', 'Extra seat. Can be configured as a gunner seat for turrets.', 'مقعد إضافي. يمكن جعله مقعد رامٍ للأبراج.'),
 'piston_engine': ('Piston Engine', 'محرك مكبسي', 'Shaft power. Drives a propeller in front/behind it, the wheels, or a rotor.', 'قدرة عمود دوران. يدير مروحة أمامه أو خلفه أو العجلات أو دوّاراً.'),
 'turboprop_engine': ('Turboprop / Turboshaft', 'محرك توربيني مروحي', 'Powerful shaft turbine. Needs an Engine Starter.', 'توربين قوي بعمود دوران. يحتاج إلى بادئ تشغيل.'),
 'turbofan_engine': ('Turbofan Engine', 'محرك توربيني مروحي نفاث', 'Efficient jet thrust. Needs an Engine Starter.', 'دفع نفاث اقتصادي. يحتاج إلى بادئ تشغيل.'),
 'jet_engine': ('Turbojet Engine', 'محرك نفاث', 'High thrust with afterburner (hold Sprint at full throttle). Needs an Engine Starter.', 'دفع عالٍ مع حارق لاحق (اضغط الركض عند أقصى خانق). يحتاج إلى بادئ تشغيل.'),
 'electric_motor': ('Electric Motor', 'محرك كهربائي', 'Silent shaft motor that also works under water.', 'محرك صامت يعمل أيضاً تحت الماء.'),
 'propeller': ('Propeller', 'مروحة', 'Place directly in front of or behind an engine. Thrust follows the engine facing.', 'ضعها مباشرة أمام المحرك أو خلفه. الدفع باتجاه المحرك.'),
 'marine_propeller': ('Marine Propeller', 'رفاس بحري', 'Ship screw; place next to an engine, below the waterline.', 'رفاس السفن؛ ضعه بجانب محرك تحت خط الماء.'),
 'main_rotor': ('Main Rotor', 'الدوّار الرئيسي', 'Helicopter rotor driven by shaft engines. Keep it above the centre of mass.', 'دوّار المروحية يُدار بمحركات العمود. اجعله فوق مركز الكتلة.'),
 'tail_rotor': ('Tail Rotor', 'الدوّار الخلفي', 'Counters main rotor torque; points sideways at the tail.', 'يعادل عزم الدوّار الرئيسي؛ يتجه جانبياً في الذيل.'),
 'engine_starter': ('Engine Starter (APU)', 'بادئ تشغيل المحرك', 'Required to start turbine engines.', 'مطلوب لتشغيل المحركات التوربينية.'),
 'fuel_tank': ('Fuel Tank', 'خزان الوقود', 'Holds 300 kg of fuel. Right-click with fuel canisters (sneak to use the whole stack).', 'يتسع لـ 300 كغ من الوقود. انقر بعلب الوقود (مع الانحناء لاستخدام الكومة).'),
 'fuel_pump': ('Fuel Pump', 'مضخة الوقود', 'Raises fuel flow so big engines are not starved.', 'يزيد تدفق الوقود حتى لا تتعطش المحركات الكبيرة.'),
 'hydraulic_pump': ('Hydraulic Pump', 'مضخة هيدروليكية', 'Full authority and speed for control surfaces and landing gear.', 'سلطة وسرعة كاملتان لأسطح التحكم وعجلات الهبوط.'),
 'wing': ('Wing', 'جناح', 'Standard wing. Configure lift, airfoil, incidence, sweep and controls with the wrench.', 'جناح قياسي. اضبط الرفع والمقطع وزاوية التركيب والميل وأدوات التحكم بالمفتاح.'),
 'straight_wing': ('Straight Wing', 'جناح مستقيم', 'High lift, low speed straight planform.', 'رفع عالٍ لسرعات منخفضة.'),
 'swept_wing': ('Swept Wing', 'جناح مائل', 'Swept planform for high speed.', 'شكل مائل للسرعات العالية.'),
 'delta_wing': ('Delta Wing', 'جناح دلتا', 'Delta planform with vortex lift and late stall. Acts as elevons.', 'جناح مثلثي برفع دوامي وانهيار متأخر. يعمل كسطح تحكم مزدوج.'),
 'variable_sweep_wing': ('Variable-Sweep Wing', 'جناح متغير الميل', 'Sweep changes automatically with airspeed.', 'يتغير الميل تلقائياً مع السرعة.'),
 'aileron': ('Aileron', 'جنيح', 'Roll control surface.', 'سطح التحكم بالانقلاب.'),
 'elevator': ('Elevator', 'رافعة', 'Pitch control surface (also canards and dive planes).', 'سطح التحكم بالانحدار (وكذلك الأجنحة الأمامية وزعانف الغوص).'),
 'rudder': ('Rudder', 'دفة', 'Yaw control surface; works in air and water.', 'سطح التحكم بالانعراج؛ يعمل في الهواء والماء.'),
 'flap': ('Flap', 'قلاب', 'Extra lift for take-off and landing.', 'رفع إضافي للإقلاع والهبوط.'),
 'spoiler': ('Spoiler', 'مفسد الرفع', 'Dumps lift and adds drag.', 'يقلل الرفع ويزيد السحب.'),
 'wheel': ('Wheel', 'عجلة', 'Suspension wheel with steering, drive and brakes.', 'عجلة بنظام تعليق وتوجيه ودفع وفرامل.'),
 'landing_gear': ('Landing Gear', 'عجلات الهبوط', 'Retractable aircraft gear (G to raise/lower).', 'عجلات طائرة قابلة للطي (G للرفع والإنزال).'),
 'ballast_tank': ('Ballast Tank', 'خزان الصابورة', 'Flood (Sprint) to dive, blow (Jump) to surface.', 'املأه (الركض) للغوص وأفرغه (القفز) للطفو.'),
 'balloon_envelope': ('Balloon Envelope', 'غلاف المنطاد', 'Light fabric. Enclosed space becomes lifting gas (hot air with a burner, helium without).', 'قماش خفيف. الفراغ المغلق يصبح غازاً رافعاً (هواء ساخن مع موقد، هيليوم بدونه).'),
 'gas_cell': ('Helium Gas Cell', 'خلية غاز الهيليوم', 'A cell of helium that lifts on its own.', 'خلية هيليوم ترفع بذاتها.'),
 'burner': ('Balloon Burner', 'موقد المنطاد', 'Heats the air in envelopes (Jump). Uses fuel.', 'يسخّن الهواء في الأغلفة (القفز). يستهلك الوقود.'),
 'flight_instruments': ('Flight Instruments', 'أجهزة الطيران', 'Enables the full instrument HUD.', 'يفعّل لوحة الأجهزة الكاملة.'),
 'navigation_computer': ('Navigation Computer', 'حاسوب الملاحة', 'Set a waypoint; bearing and distance appear on the HUD.', 'حدد نقطة طريق؛ يظهر الاتجاه والمسافة على الشاشة.'),
 'weapon_mount': ('Weapon Mount', 'حامل السلاح', 'Weapons must touch a mount (or turret base) to work.', 'يجب أن يلامس السلاح حاملاً (أو قاعدة برج) ليعمل.'),
 'turret_base': ('Turret Base', 'قاعدة برج', 'Weapons on it aim where the gunner (or pilot) looks.', 'الأسلحة فوقه تصوّب حيث ينظر الرامي (أو الطيار).'),
 'machine_gun': ('Machine Gun', 'رشاش', 'Fictional rapid-fire gun. Ammo: belts.', 'رشاش خيالي سريع. الذخيرة: أحزمة.'),
 'cannon': ('Cannon', 'مدفع', 'Fictional explosive shells.', 'قذائف متفجرة خيالية.'),
 'missile_launcher': ('Missile Launcher', 'قاذف صواريخ', 'Fictional guided missiles that track the target near your aim.', 'صواريخ موجهة خيالية تتبع الهدف القريب من التصويب.'),
 'bomb_bay': ('Bomb Bay', 'حجرة القنابل', 'Releases fictional bombs downward.', 'يطلق قنابل خيالية للأسفل.'),
 'ammo_box': ('Ammunition Box', 'صندوق الذخيرة', 'Stores ammunition that weapons reload from.', 'يخزن الذخيرة التي تعيد الأسلحة التعبئة منها.'),
 'remote_control_unit': ('Remote Control Unit', 'وحدة التحكم عن بعد', 'Link weapons (sneak+wrench) and assign each a fire key.', 'اربط الأسلحة (انحناء+مفتاح) وخصص لكل منها زر إطلاق.'),
}
for k, (en, ar, ten, tar) in blocks.items():
    t('block.vehicleworks.' + k, en, ar); t('vehicleworks.tooltip.' + k, ten, tar)
items = {'engineering_wrench': ('Engineering Wrench', 'مفتاح هندسي'), 'fuel_canister': ('Fuel Canister', 'علبة وقود'), 'ammo_belt': ('Ammunition Belt', 'حزام ذخيرة'),
         'cannon_shell': ('Cannon Shell', 'قذيفة مدفع'), 'missile': ('Missile', 'صاروخ'), 'aerial_bomb': ('Aerial Bomb', 'قنبلة جوية')}
for k, (en, ar) in items.items(): t('item.vehicleworks.' + k, en, ar)
t('entity.vehicleworks.vehicle', 'Vehicle', 'مركبة'); t('entity.vehicleworks.projectile', 'Projectile', 'مقذوف')
t('itemGroup.vehicleworks', 'VehicleWorks Engineering', 'هندسة المركبات')
t('vehicleworks.tooltip.mass', 'Mass: %s kg', 'الكتلة: %s كغ'); t('vehicleworks.tooltip.fuel', 'Contains %s kg of fuel', 'يحتوي %s كغ من الوقود')
t('vehicleworks.tooltip.ammo', '%s rounds (fictional)', '%s طلقة (خيالية)')
t('vehicleworks.tooltip.wrench', 'Use: configure. Sneak-use a weapon to select it, then sneak-use a Remote Control Unit to link.', 'استخدم: للضبط. انحنِ واستخدمه على سلاح لاختياره ثم على وحدة التحكم لربطه.')
t('vehicleworks.tooltip.wrench_selected', 'Selected: %s', 'المحدد: %s')

props = {
 'profile': ('Control profile', 'نمط التحكم'), 'mouse_aim': ('Mouse-aim flying', 'الطيران بالتصويب'), 'assist': ('Flight assist', 'مساعد الطيران'),
 'differential': ('Differential', 'الترس التفاضلي'), 'auto_gearbox': ('Automatic gearbox', 'ناقل حركة أوتوماتيكي'), 'show_com': ('Show centre of mass', 'إظهار مركز الكتلة'),
 'gunner': ('Gunner seat', 'مقعد رامٍ'), 'power_scale': ('Power', 'القدرة'), 'throttle_group': ('Throttle group', 'مجموعة الخانق'), 'enabled': ('Enabled', 'مفعّل'),
 'afterburner': ('Afterburner', 'الحارق اللاحق'), 'diameter': ('Diameter', 'القطر'), 'efficiency': ('Efficiency', 'الكفاءة'), 'spin': ('Rotation', 'اتجاه الدوران'),
 'radius': ('Radius', 'نصف القطر'), 'blades': ('Blades', 'عدد الشفرات'), 'chord': ('Chord (area per block)', 'الوتر (المساحة لكل كتلة)'),
 'max_collective': ('Max collective pitch', 'أقصى خطوة جماعية'), 'hub_stiffness': ('Hub stiffness', 'صلابة المحور'),
 'wing_type': ('Wing type', 'نوع الجناح'), 'airfoil': ('Airfoil profile', 'مقطع الجناح'), 'role': ('Control role', 'وظيفة التحكم'),
 'lift_scale': ('Lift coefficient', 'معامل الرفع'), 'incidence': ('Incidence', 'زاوية التركيب'), 'drag_scale': ('Drag coefficient', 'معامل السحب'),
 'stall_angle': ('Stall angle', 'زاوية الانهيار'), 'flap_max': ('Flap deflection', 'انحراف القلاب'), 'control_max': ('Control deflection', 'انحراف سطح التحكم'),
 'spoiler_max': ('Spoiler deployment', 'فتح مفسد الرفع'), 'sweep': ('Sweep angle', 'زاوية الميل'), 'sweep_max': ('Max sweep (variable)', 'أقصى ميل (متغير)'),
 'responsiveness': ('Control responsiveness', 'سرعة الاستجابة'), 'invert': ('Invert control', 'عكس التحكم'),
 'suspension': ('Suspension stiffness', 'صلابة التعليق'), 'damping': ('Damping', 'التخميد'), 'travel': ('Suspension travel', 'مشوار التعليق'),
 'steering': ('Steering', 'التوجيه'), 'max_steer': ('Max steering angle', 'أقصى زاوية توجيه'), 'drive': ('Driven', 'مدفوعة'), 'braked': ('Brakes', 'الفرامل'),
 'brake_torque': ('Brake torque', 'عزم الفرملة'), 'grip': ('Tyre grip', 'تماسك الإطار'), 'retractable': ('Retractable', 'قابلة للطي'),
 'power': ('Burner power', 'قدرة الموقد'), 'waypoint_x': ('Waypoint X', 'نقطة الطريق X'), 'waypoint_z': ('Waypoint Z', 'نقطة الطريق Z'),
 'waypoint_active': ('Waypoint active', 'نقطة الطريق مفعلة'), 'traverse_speed': ('Traverse speed', 'سرعة الدوران'), 'max_elevation': ('Max elevation', 'أقصى ارتفاع'),
 'max_depression': ('Max depression', 'أقصى انخفاض'), 'fire_mode': ('Fire mode', 'نمط الإطلاق'), 'safety': ('Safety lock', 'قفل الأمان'),
}
for k, (en, ar) in props.items(): t('vehicleworks.prop.' + k, en, ar)
opts = {'auto': ('Auto', 'تلقائي'), 'aircraft': ('Aircraft', 'طائرة'), 'helicopter': ('Helicopter', 'مروحية'), 'car': ('Car', 'سيارة'), 'ship': ('Ship', 'سفينة'),
        'submarine': ('Submarine', 'غواصة'), 'airship': ('Airship / balloon', 'منطاد'), 'open': ('Open', 'مفتوح'), 'limited_slip': ('Limited slip', 'انزلاق محدود'),
        'locked': ('Locked', 'مقفل'), 'all': ('All', 'الكل'), 'group_a': ('Group A (left)', 'المجموعة أ (يسار)'), 'group_b': ('Group B (right)', 'المجموعة ب (يمين)'),
        'clockwise': ('Clockwise', 'مع عقارب الساعة'), 'counter_clockwise': ('Counter-clockwise', 'عكس عقارب الساعة'), 'standard': ('Standard', 'قياسي'),
        'straight': ('Straight', 'مستقيم'), 'swept': ('Swept', 'مائل'), 'delta': ('Delta', 'دلتا'), 'variable_sweep': ('Variable sweep', 'متغير الميل'),
        'flat_plate': ('Flat plate', 'لوح مسطح'), 'naca0012': ('NACA 0012 (symmetric)', 'NACA 0012 (متماثل)'), 'naca2412': ('NACA 2412 (cambered)', 'NACA 2412 (مقوس)'),
        'naca4412': ('NACA 4412 (high lift)', 'NACA 4412 (رفع عالٍ)'), 'supercritical': ('Supercritical', 'فوق حرج'), 'biconvex': ('Biconvex (supersonic)', 'محدب الوجهين (فوق صوتي)'),
        'reflex': ('Reflex', 'منعكس'), 'none': ('None (fixed)', 'لا شيء (ثابت)'), 'aileron': ('Aileron (roll)', 'جنيح (انقلاب)'), 'elevator': ('Elevator (pitch)', 'رافعة (انحدار)'),
        'rudder': ('Rudder (yaw)', 'دفة (انعراج)'), 'flap': ('Flap', 'قلاب'), 'flaperon': ('Flaperon', 'قلاب-جنيح'), 'elevon': ('Elevon', 'رافعة-جنيح'),
        'spoiler': ('Spoiler', 'مفسد رفع'), 'all_axes': ('All axes', 'كل المحاور'), 'yes': ('Yes', 'نعم'), 'no': ('No', 'لا'), 'burst': ('Burst', 'رشقة'), 'single': ('Single', 'طلقة واحدة')}
for k, (en, ar) in opts.items(): t('vehicleworks.opt.' + k, en, ar)
for k in ('auto', 'aircraft', 'helicopter', 'car', 'ship', 'submarine', 'airship'): t('vehicleworks.profile.' + k, *opts[k])
for k, en, ar in (('jet', 'jet', 'نفاث'), ('propeller', 'propeller', 'مروحة'), ('marine_propeller', 'screw', 'رفاس'), ('drivetrain', 'wheels', 'عجلات'), ('rotor', 'rotor', 'دوّار')): t('vehicleworks.device.' + k, en, ar)
for k, en, ar in (('off', 'OFF', 'متوقف'), ('starting', 'STARTING', 'يبدأ'), ('running', 'RUNNING', 'يعمل'), ('stopping', 'STOPPING', 'يتوقف'), ('failed', 'FAILED', 'معطل')): t('vehicleworks.state.' + k, en, ar)
for i, (en, ar) in enumerate((('Auto', 'آلي'), ('Burst (3)', 'رشقة (3)'), ('Single', 'طلقة'))): t(f'vehicleworks.fire_mode.{i}', en, ar)
for i, (en, ar) in enumerate((('Attack button', 'زر الهجوم'), ('Use button', 'زر الاستخدام'), ('Fire 3 key', 'زر الإطلاق 3'), ('Fire 4 key', 'زر الإطلاق 4'), ('Fire 5 key', 'زر الإطلاق 5'), ('Fire 6 key', 'زر الإطلاق 6')), 1):
    t(f'vehicleworks.channel_key.{i}', en, ar)
t('vehicleworks.plane.horizontal', 'Horizontal', 'أفقي'); t('vehicleworks.plane.vertical_x', 'Vertical (X)', 'عمودي (X)'); t('vehicleworks.plane.vertical_z', 'Vertical (Z)', 'عمودي (Z)')
for k, en, ar in (('overview', 'Overview', 'نظرة عامة'), ('mass', 'Mass', 'الكتلة'), ('engines', 'Engines', 'المحركات'), ('aero', 'Aero', 'الديناميكا'),
                  ('fuel', 'Fuel', 'الوقود'), ('damage', 'Damage', 'الأضرار'), ('weapons', 'Weapons', 'الأسلحة'), ('controls', 'Controls', 'التحكم')):
    t('vehicleworks.tab.' + k, en, ar)
msgs = {
 'ammo_box': ('Ammo (bullets/shells/missiles/bombs): %s', 'الذخيرة (رصاص/قذائف/صواريخ/قنابل): %s'), 'armed': ('MASTER ARM: ARMED', 'المفتاح الرئيسي: مسلّح'),
 'assembled': ('Vehicle assembled: %s blocks, %s t', 'تم تجميع المركبة: %s كتلة، %s طن'), 'assist_off': ('Flight assist OFF', 'مساعد الطيران متوقف'), 'assist_on': ('Flight assist ON', 'مساعد الطيران يعمل'),
 'blocked': ('Cannot disassemble: %s target blocks are occupied', 'تعذّر التفكيك: %s موضع مشغول'), 'core_break': ('Disassemble the vehicle before removing its core', 'فكّك المركبة قبل إزالة نواتها'),
 'demo_built': ('Demo %s placed as blocks. Right-click the core to assemble.', 'تم وضع النموذج %s ككتل. انقر النواة للتجميع.'),
 'demo_spawned': ('Spawned demo %s: %s blocks, %s t', 'تم إنشاء النموذج %s: %s كتلة، %s طن'), 'disassembled': ('Vehicle placed back as %s blocks', 'أعيدت المركبة إلى %s كتلة'),
 'engines_starting': ('Starting engines…', 'تشغيل المحركات…'), 'engines_stopping': ('Shutting down engines', 'إيقاف المحركات'), 'flaps': ('Flaps %s%%', 'القلابات %s%%'),
 'fuel_level': ('Fuel: %s / %s kg', 'الوقود: %s / %s كغ'), 'gear_down': ('Gear DOWN', 'العجلات منزلة'), 'gear_up': ('Gear UP', 'العجلات مرفوعة'),
 'link_far': ('Too far to link', 'بعيد جداً للربط'), 'linked': ('Weapon linked to fire channel %s', 'تم ربط السلاح بقناة الإطلاق %s'), 'loaded': ('Magazine %s / %s', 'المخزن %s / %s'),
 'mag_full': ('Magazine full', 'المخزن ممتلئ'), 'mouse_aim_off': ('Mouse-aim OFF (keyboard flight)', 'التصويب متوقف (طيران بلوحة المفاتيح)'), 'mouse_aim_on': ('Mouse-aim ON', 'التصويب يعمل'),
 'moving': ('Stop the vehicle before disassembling', 'أوقف المركبة قبل التفكيك'), 'need_iron': ('Repair needs %s iron ingots', 'الإصلاح يحتاج %s سبيكة حديد'),
 'need_starter': ('Turbine engines need an Engine Starter on the vehicle', 'المحركات التوربينية تحتاج بادئ تشغيل على المركبة'), 'no_damage': ('Nothing to repair', 'لا شيء للإصلاح'),
 'no_engines': ('This vehicle has no engines', 'هذه المركبة بلا محركات'), 'no_fuel': ('No fuel! Fill a fuel tank with fuel canisters', 'لا يوجد وقود! املأ خزاناً بعلب الوقود'),
 'not_core': ('Not a vehicle core', 'ليست نواة مركبة'), 'not_level': ('Level the vehicle before disassembling', 'اجعل المركبة مستوية قبل التفكيك'),
 'parking_off': ('Parking brake released', 'تم تحرير فرامل الوقوف'), 'parking_on': ('Parking brake set', 'تم تفعيل فرامل الوقوف'), 'refuelled': ('Refuelled', 'تمت التعبئة'),
 'repaired': ('Repaired %s%% using %s iron', 'تم الإصلاح %s%% باستخدام %s حديد'), 'safe': ('MASTER ARM: SAFE', 'المفتاح الرئيسي: آمن'),
 'seat_taken': ('Seat is taken', 'المقعد مشغول'), 'select_first': ('Sneak-use a weapon on this vehicle with the wrench first', 'اختر سلاحاً على هذه المركبة بالمفتاح أولاً'),
 'selected': ('Selected %s — now sneak-use a Remote Control Unit', 'تم اختيار %s — الآن استخدم المفتاح على وحدة التحكم'), 'tank_full': ('Tank full', 'الخزان ممتلئ'),
 'too_large': ('Structure too large (max %s blocks per axis) — is it touching the ground?', 'الهيكل كبير جداً (أقصى %s كتلة لكل محور) — هل يلامس الأرض؟'),
 'too_many': ('Too many blocks (max %s) — is it connected to terrain?', 'كتل كثيرة جداً (الحد %s) — هل هو متصل بالتضاريس؟'),
 'unloaded': ('Part of the structure is in unloaded chunks', 'جزء من الهيكل في مناطق غير محمّلة'), 'wrong_ammo': ('Wrong ammunition for this weapon', 'ذخيرة غير مناسبة لهذا السلاح'),
}
for k, (en, ar) in msgs.items(): t('vehicleworks.msg.' + k, en, ar)
hud = {'agl': ('AGL %s', 'فوق الأرض %s'), 'armed': ('ARMED', 'مسلّح'), 'safe': ('SAFE', 'آمن'), 'engines': ('ENGINES', 'المحركات'), 'kmh': ('km/h', 'كم/س'),
       'rotor_offset': ('Rotor %s m off CoM!', 'الدوّار بعيد %s م عن مركز الكتلة!')}
for k, (en, ar) in hud.items(): t('vehicleworks.hud.' + k, en, ar)
warn = {'stall': ('STALL', 'انهيار'), 'pull_up': ('PULL UP', 'ارفع المقدمة'), 'gear': ('GEAR UP!', 'العجلات مرفوعة!'), 'low_fuel': ('LOW FUEL', 'وقود منخفض'),
        'overheat': ('ENGINE OVERHEAT', 'ارتفاع حرارة المحرك'), 'engine_fail': ('ENGINE FAILURE', 'عطل المحرك'), 'overstress': ('OVERSTRESS', 'إجهاد زائد'),
        'damage': ('STRUCTURAL DAMAGE', 'ضرر هيكلي'), 'depth': ('DEPTH LIMIT', 'حد العمق')}
for k, (en, ar) in warn.items(): t('vehicleworks.warn.' + k, en, ar)
ui = {'apply': ('Apply', 'تطبيق'), 'apply_close': ('Apply & Close', 'تطبيق وإغلاق'), 'assemble': ('Assemble Vehicle', 'تجميع المركبة'), 'auto': ('auto', 'تلقائي'),
      'channel': ('Channel %s: %s', 'القناة %s: %s'), 'clmax': ('CLmax %s at %s°', 'أقصى معامل رفع %s عند %s°'), 'core_settings': ('Core settings', 'إعدادات النواة'),
      'disassemble': ('Disassemble', 'تفكيك'), 'engineering': ('Vehicle Engineering', 'هندسة المركبة'), 'flaps': ('Flaps', 'القلابات'),
      'lift_example': ('At 50 m/s, 4°: %s N per block, L/D %s', 'عند 50 م/ث و4°: %s نيوتن لكل كتلة، نسبة الرفع/السحب %s'), 'loading': ('Loading…', 'جارٍ التحميل…'),
      'parking': ('Parking brake', 'فرامل الوقوف'), 'plane': ('Plane: %s', 'المستوى: %s'), 'polar_cd': ('Cd (drag)', 'معامل السحب'), 'polar_cl': ('Cl (lift)', 'معامل الرفع'),
      'profile_cycle': ('Control profile', 'نمط التحكم'), 'rcu_empty': ('No links. Sneak-use a weapon with the wrench, then this unit.', 'لا روابط. استخدم المفتاح على سلاح ثم على هذه الوحدة.'),
      'rcu_hint': ('Each linked weapon fires on its channel while MASTER ARM is on (B).', 'كل سلاح مرتبط يطلق على قناته عندما يكون المفتاح الرئيسي مسلحاً (B).'),
      'refuel_creative': ('Refuel (creative)', 'تعبئة (إبداعي)'), 'repair': ('Repair', 'إصلاح'), 'start_all': ('Start all', 'تشغيل الكل'), 'stop_all': ('Stop all', 'إيقاف الكل'),
      'throttle_idle': ('Throttle idle', 'الخانق إلى الحد الأدنى'), 'toggle_arm': ('Master arm', 'المفتاح الرئيسي'), 'toggle_assist': ('Flight assist', 'مساعد الطيران'),
      'toggle_gear': ('Landing gear', 'عجلات الهبوط'), 'toggle_mouse_aim': ('Mouse-aim', 'التصويب')}
for k, (en, ar) in ui.items(): t('vehicleworks.ui.' + k, en, ar)
info = {
 'blocks': ('Blocks: %s', 'الكتل: %s'), 'mass': ('Mass: %s kg (dry %s kg)', 'الكتلة: %s كغ (فارغة %s كغ)'), 'size': ('Size: %s × %s × %s m', 'الحجم: %s × %s × %s م'),
 'com': ('Centre of mass (local): %s, %s, %s', 'مركز الكتلة (محلي): %s، %s، %s'), 'inertia': ('Inertia: %s / %s / %s kg·m²', 'عزم القصور: %s / %s / %s كغ·م²'),
 'profile': ('Control profile: %s (detected: %s)', 'نمط التحكم: %s (المكتشف: %s)'), 'speed': ('Airspeed %s km/h, angle of attack %s°', 'السرعة الجوية %s كم/س، زاوية الهجوم %s°'),
 'forces': ('Lift %s kN · Drag %s kN · Thrust %s kN · Buoyancy %s kN', 'الرفع %s · السحب %s · الدفع %s · الطفو %s (كيلو نيوتن)'),
 'physics': ('Physics: %s merged boxes, %s contact probes, %s wing strips, %s fluid elements', 'الفيزياء: %s صندوق مدمج، %s نقطة تلامس، %s شريحة جناح، %s عنصر مائع'),
 'perf': ('World: %s vehicles, %s substeps, %s contacts, %s ms/tick, %s terrain blocks', 'العالم: %s مركبة، %s خطوة فرعية، %s تلامس، %s مللي ثانية، %s كتلة تضاريس'),
 'sleeping': ('Physics: sleeping (at rest)', 'الفيزياء: نائمة (ساكنة)'), 'awake': ('Physics: active', 'الفيزياء: نشطة'),
 'mass_map': ('Top-down mass distribution (yellow cross = centre of mass, blue line = neutral point)', 'توزيع الكتلة من الأعلى (الصليب الأصفر = مركز الكتلة، الخط الأزرق = النقطة المحايدة)'),
 'np': ('neutral point', 'النقطة المحايدة'), 'forward': ('forward', 'للأمام'), 'no_engines': ('No engines installed', 'لا توجد محركات'),
 'engine_header': ('#  type      drives    state    rpm     temp     damage  output   fuel   group', '#  النوع  يدير  الحالة  الدوران  الحرارة  الضرر  الناتج  الوقود  المجموعة'),
 'systems': ('Starter: %s · Fuel pumps: %s · Hydraulic pumps: %s', 'بادئ التشغيل: %s · مضخات الوقود: %s · المضخات الهيدروليكية: %s'),
 'wing_area': ('Wing area: %s m² · wing loading %s kg/m²', 'مساحة الجناح: %s م² · حمل الجناح %s كغ/م²'), 'clmax': ('Max lift coefficient: %s', 'أقصى معامل رفع: %s'),
 'stall_speed': ('Estimated stall speed: %s km/h', 'سرعة الانهيار التقديرية: %s كم/س'), 'thrust': ('Static thrust: %s kN · thrust/weight %s', 'الدفع الساكن: %s كيلو نيوتن · نسبة الدفع للوزن %s'),
 'static_margin': ('Static margin: %s', 'الهامش الثابت: %s'), 'unstable_hint': ('Unstable in pitch: move weight forward or enlarge the tail.', 'غير مستقرة في الانحدار: انقل الوزن للأمام أو كبّر الذيل.'),
 'stable_hint': ('Pitch stable: centre of mass ahead of the neutral point.', 'مستقرة: مركز الكتلة أمام النقطة المحايدة.'),
 'rotor_offset': ('Main rotor offset from CoM: %s m (keep below 1 m)', 'إزاحة الدوّار عن مركز الكتلة: %s م (أبقها أقل من 1 م)'),
 'live_aero': ('Now: lift %s kN, drag %s kN, AoA %s°', 'الآن: الرفع %s، السحب %s كيلو نيوتن، زاوية الهجوم %s°'), 'gas': ('Lifting gas: %s m³, lift %s kN', 'الغاز الرافع: %s م³، رفع %s كيلو نيوتن'),
 'float': ('Displacement %s m³ (%s air pockets) · float ratio %s (>1 floats)', 'الإزاحة %s م³ (%s جيب هواء) · نسبة الطفو %s (>1 تطفو)'),
 'fuel': ('Fuel: %s / %s kg in %s tanks', 'الوقود: %s / %s كغ في %s خزان'), 'fuel_flow': ('Consumption %s kg/s · endurance %s min', 'الاستهلاك %s كغ/ث · المدة %s دقيقة'),
 'fuel_supply': ('Fuel supply %s%% (%s pumps)', 'إمداد الوقود %s%% (%s مضخة)'), 'ballast': ('Ballast water: %s / %s kg', 'ماء الصابورة: %s / %s كغ'),
 'refuel_hint': ('Refuel: right-click a fuel tank with fuel canisters (sneak for the whole stack).', 'التعبئة: انقر خزان الوقود بعلب الوقود (انحنِ للكومة كلها).'),
 'integrity': ('Structural integrity %s · %s damaged blocks', 'السلامة الهيكلية %s · %s كتلة متضررة'), 'stress': ('Peak wing load %s of limit', 'أقصى حمل على الجناح %s من الحد'),
 'engine_damage': ('Engine %s damage %s', 'المحرك %s ضرر %s'), 'repair_hint': ('Repair uses iron ingots from your inventory.', 'الإصلاح يستخدم سبائك الحديد من حقيبتك.'),
 'armed': ('MASTER ARM: ARMED', 'المفتاح الرئيسي: مسلّح'), 'safe': ('MASTER ARM: SAFE', 'المفتاح الرئيسي: آمن'),
 'rcu': ('Remote control units: %s · ammo boxes: %s', 'وحدات التحكم: %s · صناديق الذخيرة: %s'),
 'weapon_line': ('%s. %s  ch %s  ammo %s/%s  reserve %s  %s', '%s. %s  قناة %s  ذخيرة %s/%s  احتياط %s  %s'),
 'turret': ('turret', 'برج'), 'mounted': ('mounted', 'مثبت'), 'unmounted': ('NOT MOUNTED', 'غير مثبت'),
 'weapons_hint': ('All weapons are fictional gameplay systems. Link them to a Remote Control Unit to choose fire keys.', 'كل الأسلحة أنظمة لعب خيالية. اربطها بوحدة تحكم لاختيار أزرار الإطلاق.'),
}
for k, (en, ar) in info.items(): t('vehicleworks.info.' + k, en, ar)
help_c = [
 ('Seat: right-click a Pilot Seat. Exit: Sneak.', 'المقعد: انقر مقعد الطيار. الخروج: الانحناء.'),
 ('Engines: I start/stop · R/F throttle up/down.', 'المحركات: I تشغيل/إيقاف · R/F زيادة/تقليل الخانق.'),
 ('Aircraft (mouse-aim): look where to fly · W/S throttle · A/D roll · Z/C rudder.', 'الطائرة (تصويب): انظر حيث تريد الطيران · W/S الخانق · A/D انقلاب · Z/C الدفة.'),
 ('Aircraft (keyboard): W/S pitch · A/D roll · Z/C yaw · arrows pitch trim.', 'الطائرة (لوحة مفاتيح): W/S انحدار · A/D انقلاب · Z/C انعراج · الأسهم للتعديل.'),
 ('Jump: wheel brakes · Sprint: afterburner · G gear · V flaps · H assist · M mouse-aim.', 'القفز: الفرامل · الركض: الحارق اللاحق · G العجلات · V القلابات · H المساعد · M التصويب.'),
 ('Helicopter: Jump/Sprint collective · W/S/A/D cyclic · Z/C pedals · R throttle (rotor rpm).', 'المروحية: القفز/الركض الخطوة الجماعية · W/S/A/D الدوري · Z/C الدواسات · R الخانق.'),
 ('Car: W/S drive/brake · A/D steer · Jump handbrake · R/F shift gears (manual) · P parking.', 'السيارة: W/S قيادة/فرملة · A/D توجيه · القفز فرامل اليد · R/F تغيير السرعات · P الوقوف.'),
 ('Ship: W/S engine order (ahead/astern) · A/D rudder.', 'السفينة: W/S أمر المحرك (أمام/خلف) · A/D الدفة.'),
 ('Submarine: Sprint floods ballast (dive) · Jump blows ballast (surface) · R/F dive planes.', 'الغواصة: الركض يملأ الصابورة (غوص) · القفز يفرغها (طفو) · R/F زعانف الغوص.'),
 ('Airship / balloon: Jump burner or more gas · Sprint vent · W/S throttle · A/D yaw.', 'المنطاد: القفز الموقد أو غاز أكثر · الركض تنفيس · W/S الخانق · A/D الاتجاه.'),
 ('Weapons: B master arm · Attack = channel 1 · Use = channel 2 · Alt/K/U/Y = channels 3–6.', 'الأسلحة: B المفتاح الرئيسي · الهجوم = القناة 1 · الاستخدام = 2 · Alt/K/U/Y = القنوات 3–6.'),
 ('N opens this engineering screen while seated. All keys are rebindable in Controls.', 'N يفتح شاشة الهندسة أثناء الجلوس. كل الأزرار قابلة للتغيير من إعدادات التحكم.'),
 ('Build on vehicles: walk on them and right-click their blocks with any block.', 'البناء على المركبات: امشِ عليها وانقر كتلها بأي كتلة.'),
 ('Tip: flight assist levels the wings and holds attitude when you release the stick.', 'نصيحة: مساعد الطيران يسوّي الأجنحة ويثبت الوضع عند ترك أدوات التحكم.'),
]
for i, (en, ar) in enumerate(help_c, 1): t(f'vehicleworks.help.controls.{i}', en, ar)
help_a = [
 ('Build your vehicle around this core, connected face-to-face.', 'ابنِ مركبتك حول هذه النواة بحيث تتصل الكتل وجهاً لوجه.'),
 ('The core arrow marks the FRONT of the vehicle.', 'سهم النواة يحدد مقدمة المركبة.'),
 ('Terrain (dirt, stone, sand, leaves) is never included.', 'لا تُضمَّن التضاريس (التراب والحجر والرمل والأوراق) أبداً.'),
 ('Add seats, engines, fuel tanks with fuel, wings or wheels.', 'أضف مقاعد ومحركات وخزانات وقود ممتلئة وأجنحة أو عجلات.'),
 ('Configure any component by right-clicking it (or with the wrench).', 'اضبط أي مكوّن بالنقر عليه (أو بالمفتاح).'),
 ('Assemble: blocks become a vehicle and keep all their properties.', 'التجميع: تتحول الكتل إلى مركبة وتحتفظ بكل خصائصها.'),
]
for i, (en, ar) in enumerate(help_a, 1): t(f'vehicleworks.help.assemble.{i}', en, ar)
keys = {'yaw_left': ('Yaw / rudder left', 'انعراج يسار'), 'yaw_right': ('Yaw / rudder right', 'انعراج يمين'), 'throttle_up': ('Throttle up / gear up', 'زيادة الخانق'),
        'throttle_down': ('Throttle down / gear down', 'تقليل الخانق'), 'pitch_up': ('Pitch up / dive planes', 'رفع المقدمة'), 'pitch_down': ('Pitch down', 'خفض المقدمة'),
        'engines': ('Start / stop engines', 'تشغيل/إيقاف المحركات'), 'gear': ('Landing gear', 'عجلات الهبوط'), 'flaps': ('Flaps', 'القلابات'), 'master_arm': ('Master arm (weapons)', 'المفتاح الرئيسي (الأسلحة)'),
        'assist': ('Flight assist', 'مساعد الطيران'), 'mouse_aim': ('Mouse-aim toggle', 'تبديل التصويب'), 'vehicle_menu': ('Vehicle engineering menu', 'قائمة هندسة المركبة'),
        'parking_brake': ('Parking brake', 'فرامل الوقوف'), 'brake': ('Strong brake (hold)', 'فرامل قوية (اضغط مطولاً)'), 'fire_3': ('Fire channel 3', 'قناة الإطلاق 3'), 'fire_4': ('Fire channel 4', 'قناة الإطلاق 4'),
        'fire_5': ('Fire channel 5', 'قناة الإطلاق 5'), 'fire_6': ('Fire channel 6', 'قناة الإطلاق 6')}
for k, (en, ar) in keys.items(): t('key.vehicleworks.' + k, en, ar)
t('key.categories.vehicleworks', 'VehicleWorks', 'هندسة المركبات')
subs = {'engine.piston': ('Piston engine', 'محرك مكبسي'), 'engine.turbine': ('Turbine whine', 'صفير التوربين'), 'engine.jet': ('Jet roar', 'هدير نفاث'),
        'engine.rotor': ('Rotor thump', 'خفقان الدوّار'), 'engine.electric': ('Electric hum', 'طنين كهربائي'), 'engine.start': ('Engine starts', 'بدء المحرك'),
        'vehicle.wind': ('Wind', 'رياح'), 'vehicle.stall_warning': ('Stall warning', 'تحذير الانهيار'), 'vehicle.gear': ('Landing gear moves', 'حركة العجلات'),
        'vehicle.assemble': ('Vehicle assembled', 'تجميع المركبة'), 'weapon.machine_gun': ('Machine gun fires', 'إطلاق رشاش'), 'weapon.cannon': ('Cannon fires', 'إطلاق مدفع'),
        'weapon.missile': ('Missile launches', 'إطلاق صاروخ'), 'weapon.bomb': ('Bomb released', 'إسقاط قنبلة'), 'weapon.reload': ('Weapon reloads', 'إعادة التعبئة'),
        'weapon.arm': ('Master arm switch', 'مفتاح التسليح'), 'ui.click': ('Click', 'نقرة')}
for k, (en, ar) in subs.items(): t('subtitles.vehicleworks.' + k, en, ar)

# 1.0.5 additions
t('block.vehicleworks.heavy_landing_gear', 'Heavy Landing Gear', 'عجلات هبوط ضخمة')
t('vehicleworks.tooltip.heavy_landing_gear', 'Huge retractable gear (5x a normal gear) for big aircraft.', 'عجلات هبوط ضخمة قابلة للطي (5 أضعاف العادية) للطائرات الكبيرة.')
t('vehicleworks.prop.handling', 'Aircraft handling', 'ثقل الطائرة')
for k, en, ar in (('very_heavy', 'Very heavy', 'ثقيل جداً'), ('heavy', 'Heavy', 'ثقيل'), ('normal', 'Normal', 'عادي'), ('light', 'Light', 'خفيف')): t('vehicleworks.opt.' + k, en, ar)
t('key.vehicleworks.autopilot', 'Autopilot panel', 'لوحة الطيار الآلي')
t('vehicleworks.msg.autopilot_on', 'Autopilot ON: heading %s°, altitude %s', 'الطيار الآلي يعمل: الاتجاه %s°، الارتفاع %s')
t('vehicleworks.msg.autopilot_off', 'Autopilot OFF', 'تم إيقاف الطيار الآلي')
t('vehicleworks.autopilot.title', 'Autopilot', 'الطيار الآلي')
t('vehicleworks.autopilot.heading', 'Heading (0-359°)', 'الاتجاه (0-359°)')
t('vehicleworks.autopilot.altitude', 'Altitude (Y)', 'الارتفاع (Y)')
t('vehicleworks.autopilot.current', 'Use current heading & altitude', 'استخدم الاتجاه والارتفاع الحاليين')
t('vehicleworks.autopilot.engage', 'Engage', 'تشغيل')
t('vehicleworks.autopilot.disengage', 'Disengage', 'إيقاف')
t('vehicleworks.autopilot.now', 'Now: heading %s°, altitude %s', 'الآن: الاتجاه %s°، الارتفاع %s')
t('vehicleworks.autopilot.hint', 'Throttle stays yours. W/S/A/D override while held.', 'الخانق بيدك. W/S/A/D تتحكم مؤقتاً أثناء الضغط.')
# 1.0.7 cockpit panel
t('block.vehicleworks.cockpit_panel', 'Cockpit Panel', 'لوحة تحكم المقصورة')
t('vehicleworks.tooltip.cockpit_panel', 'Switch panel: battery, lights, engines, gear, emergency test, autopilot. Mount it on the ceiling. While seated, right-click a button on it directly, or press X.', 'لوحة أزرار: البطارية، الأضواء، المحركات، العجلات، اختبار الطوارئ، الطيار الآلي. ركّبها في السقف. وأنت جالس انقر بالزر الأيمن على الزر مباشرة أو اضغط X.')
t('key.vehicleworks.panel', 'Cockpit panel', 'لوحة التحكم')
P = {'title': ('Cockpit Panel', 'لوحة التحكم'), 'battery': ('BATTERY', 'البطارية'), 'lights': ('LIGHTS', 'الأضواء'), 'engines': ('ENGINES', 'المحركات'),
     'gear': ('LANDING GEAR', 'عجلات الهبوط'), 'emergency': ('EMERGENCY TEST', 'اختبار الطوارئ'), 'autopilot': ('AUTOPILOT', 'الطيار الآلي'),
     'on': ('ON', 'تشغيل'), 'off': ('OFF', 'إيقاف'), 'running': ('RUNNING', 'تعمل'), 'down': ('DOWN', 'نازلة'), 'up': ('UP', 'مرفوعة'),
     'testing': ('TESTING...', 'جارٍ الاختبار...'), 'press': ('PRESS', 'اضغط'), 'open': ('OPEN', 'فتح'),
     'none': ('This vehicle has no Cockpit Panel', 'هذه المركبة ليس فيها لوحة تحكم'), 'battery_off': ('Battery is OFF - turn it on in the cockpit panel (X)', 'البطارية مطفأة - شغّلها من لوحة التحكم (X)'),
     'report': ('=== Emergency test ===', '=== اختبار الطوارئ ==='), 'c_battery': ('Battery', 'البطارية'), 'c_starter': ('Engine starter', 'بادئ التشغيل'),
     'c_engines': ('Engines %s/%s ok', 'المحركات %s/%s سليمة'), 'c_fuel': ('Fuel %s%%', 'الوقود %s%%'), 'c_pumps': ('Fuel supply', 'تغذية الوقود'),
     'c_hydraulics': ('Hydraulics %s%%', 'الهيدروليك %s%%'), 'c_gear': ('Landing gear', 'عجلات الهبوط'), 'c_structure': ('Structure %s%%', 'الهيكل %s%%'),
     'c_lights': ('Lights', 'الأضواء'), 'pass': ('ALL SYSTEMS OK', 'كل الأنظمة سليمة'), 'fail': ('%s PROBLEM(S) FOUND', 'تم العثور على %s مشكلة'),
     'hud_test': ('EMERGENCY TEST', 'اختبار الطوارئ'), 'hud_batt': ('BATTERY OFF', 'البطارية مطفأة')}
for k, (en, ar) in P.items(): t('vehicleworks.panel.' + k, en, ar)
# 1.0.8 voice callouts
t('key.vehicleworks.voice', 'Voice callouts on/off', 'تشغيل/إيقاف التنبيهات الصوتية')
V = {'on': ('Voice callouts on', 'التنبيهات الصوتية تعمل'), 'off': ('Voice callouts off', 'التنبيهات الصوتية متوقفة'), 'button': ('Voice: %s', 'الصوت: %s'),
     'gear_down': ('Gear down', 'العجلات نازلة'), 'gear_up': ('Gear up', 'العجلات مرفوعة'), 'ap_on': ('Autopilot engaged', 'الطيار الآلي يعمل'),
     'ap_off': ('Autopilot disengaged', 'تم فصل الطيار الآلي'), 'battery_on': ('Battery on', 'البطارية تعمل'), 'battery_off': ('Battery off', 'البطارية مطفأة'),
     'lights_on': ('Lights on', 'الأضواء تعمل'), 'lights_off': ('Lights off', 'الأضواء مطفأة'), 'parking_on': ('Parking brake set', 'فرامل الوقوف مفعلة'),
     'parking_off': ('Parking brake released', 'فرامل الوقوف محررة'), 'test': ('Emergency test. Master warning.', 'اختبار الطوارئ. إنذار رئيسي.'),
     'engines_on': ('Engines running', 'المحركات تعمل'), 'engines_off': ('Engines shut down', 'المحركات متوقفة'), 'engine_fail': ('Engine failure', 'عطل في المحرك'),
     'low_fuel': ('Low fuel', 'الوقود منخفض'), 'damage': ('Structural damage', 'ضرر في الهيكل'), 'stall': ('Stall, stall', 'انهيار السرعة، انهيار السرعة'),
     'pull_up': ('Pull up! Pull up!', 'ارفع المقدمة! ارفع المقدمة!'), 'too_low_gear': ('Too low, gear', 'منخفض جداً، العجلات'), 'bank_angle': ('Bank angle', 'زاوية الميلان')}
for k, (en, ar) in V.items(): t('vehicleworks.voice.' + k, en, ar)
t('vehicleworks.hud.master_warning', 'MASTER WARNING', 'إنذار رئيسي')
# 1.0.11 warnings
WN = {'stall': ('STALL WARNING', 'تحذير انهيار السرعة'), 'overspeed': ('OVERSPEED', 'سرعة زائدة'), 'low_fuel': ('LOW FUEL', 'وقود منخفض'),
      'engine_failure': ('ENGINE FAILURE', 'عطل المحرك'), 'engine_fire': ('ENGINE FIRE', 'حريق المحرك'), 'low_oil': ('LOW OIL PRESSURE', 'ضغط زيت منخفض'),
      'gear': ('GEAR WARNING', 'تحذير العجلات'), 'terrain': ('TERRAIN WARNING', 'تحذير التضاريس'), 'pull_up': ('PULL UP', 'ارفع المقدمة'),
      'hydraulic': ('HYDRAULIC FAILURE', 'عطل الهيدروليك'), 'electrical': ('ELECTRICAL FAILURE', 'عطل كهربائي'), 'low_altitude': ('LOW ALTITUDE', 'ارتفاع منخفض')}
for k, (en, ar) in WN.items(): t('vehicleworks.warning.' + k, en, ar)
for k, en, ar in (('stall', 'Stick shaker', 'هزاز المقود'), ('overspeed', 'Overspeed clacker', 'نقرات السرعة الزائدة'), ('caution', 'Caution chime', 'رنين تنبيه'),
                  ('fire', 'Fire bell', 'جرس الحريق'), ('whoop', 'Terrain whoop', 'إنذار الأرض'), ('warning', 'Warning chime', 'رنين إنذار')):
    t('subtitles.vehicleworks.alarm.' + k, en, ar)
for k, en, ar in (('tire_screech', 'Tyres screech', 'صرير الإطارات'), ('rumble', 'Wheels rumble', 'هدير العجلات'), ('touchdown', 'Touchdown', 'ملامسة الأرض'), ('hard_landing', 'Hard landing!', 'هبوط عنيف!')):
    t('subtitles.vehicleworks.vehicle.' + k, en, ar)
t('vehicleworks.msg.hard_landing', 'HARD LANDING! (%s m/s)', 'هبوط عنيف! (%s م/ث)')
t('block.vehicleworks.yoke', 'Airplane Yoke', 'مقود الطائرة')
t('vehicleworks.tooltip.yoke', 'Place in front of a seat, facing the pilot. Every yoke moves with the flight controls.', 'ضعه أمام المقعد ووجهه نحو الطيار. كل المقاود تتحرك مع أوامر التحكم.')
t('vehicleworks.cmd.no_vehicle', 'No vehicle found (ride one or stand near it)', 'لا توجد مركبة (اركب واحدة أو قف بجانبها)')
t('vehicleworks.cmd.no_engine', 'No such engine', 'لا يوجد هذا المحرك')
t('vehicleworks.cmd.engine_exploded', '%s engine(s) exploded!', 'انفجر %s محرك!')
en = {k: v[0] for k, v in sorted(L.items())}
ar = {k: v[1] for k, v in sorted(L.items())}
for name, d in (('en_us', en), ('ar_sa', ar), ('ar_iq', ar)):
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8') as f: json.dump(d, f, indent=2, ensure_ascii=False)
print('lang keys', len(L))
