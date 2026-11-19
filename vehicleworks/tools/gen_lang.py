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
blocks.update({
 'copilot_seat': ('First Officer Seat', 'مقعد مساعد الطيار', 'Flight crew seat: cockpit access and HUD, the captain flies.', 'مقعد طاقم القيادة: يصل إلى القمرة وHUD، والقائد هو من يطير.'),
 'sidestick': ('Side Stick (fly-by-wire)', 'عصا التحكم الجانبية (Fly-by-wire)', 'Airbus-style side stick. Stick deflection commands pitch and roll rate; centred = attitude hold.', 'عصا تحكم جانبية بأسلوب إيرباص. الإمالة تطلب معدل الانحدار والدوران، والوسط = تثبيت الوضعية.'),
 'seat_economy': ('Economy Seat', 'مقعد الدرجة الاقتصادية', 'Headrest, armrests, seat belt, fold-down tray, slight recline.', 'مسند رأس ومساند ذراعين وحزام أمان وطاولة قابلة للطي وإمالة بسيطة.'),
 'seat_economy_plus': ('Economy Plus Seat', 'مقعد الاقتصادية الممتازة', 'Wider seat, more legroom, foot rest, adjustable position.', 'مقعد أعرض ومساحة أرجل أكبر ومسند قدم ووضعية قابلة للتعديل.'),
 'seat_premium': ('Premium Economy Seat', 'مقعد البريميوم الاقتصادي', 'Leg rest, adjustable headrest, personal entertainment screen, seat controls.', 'مسند أرجل ومسند رأس قابل للتعديل وشاشة ترفيه شخصية وأزرار تحكم.'),
 'seat_business': ('Business Class Seat', 'مقعد درجة الأعمال', 'Lie-flat seat, large screen, leg rest, work table, side console.', 'مقعد يتحول إلى سرير وشاشة كبيرة ومسند أرجل وطاولة عمل ووحدة جانبية.'),
 'seat_first': ('First Class Suite', 'جناح الدرجة الأولى', 'Private 2 x 2 suite: bed, sliding door, large screen, table and private lighting.', 'جناح خاص 2×2: سرير وباب منزلق وشاشة كبيرة وطاولة وإضاءة خاصة.'),
})
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
# 1.1.0 aircraft wheel family
for k, en, ar, ten, tar in (
    ('dual_wheel_gear', 'Dual Wheel Gear', 'عجلات هبوط مزدوجة', 'Two tyres side by side for larger aircraft. Rated 35 t per leg.', 'إطاران متجاوران للطائرات الأكبر. تتحمل 35 طن لكل ساق.'),
    ('bogie_gear', 'Heavy-Duty Bogie Gear', 'مجموعة عجلات ثقيلة', 'Four-tyre bogie (two axles) for heavy aircraft. Rated 90 t per leg, strong brakes.', 'مجموعة بأربعة إطارات (محوران) للطائرات الثقيلة. تتحمل 90 طن لكل ساق مع مكابح قوية.'),
    ('reinforced_gear', 'Reinforced Landing Gear', 'عجلات هبوط مقواة', 'Stiff, long-travel gear that absorbs hard landings. Rated 55 t per leg.', 'عجلات قاسية بشوط تعليق طويل تمتص الهبوط العنيف. تتحمل 55 طن لكل ساق.'),
    ('tundra_gear', 'Tundra Wheels', 'عجلات التندرا', 'Big soft balloon tyres: rough-field landings on grass, dirt, sand and snow. Light aircraft only (6 t), max 200 km/h.', 'إطارات كبيرة طرية للهبوط على العشب والتراب والرمل والثلج. للطائرات الخفيفة فقط (6 طن)، أقصى سرعة 200 كم/س.'),
    ('performance_gear', 'High-Performance Wheels', 'عجلات عالية الأداء', 'Narrow low-drag tyres rated to 500 km/h, fast retraction. Bad on rough ground.', 'إطارات ضيقة قليلة المقاومة حتى 500 كم/س، تُرفع بسرعة. سيئة على الأرض الوعرة.')):
    t('block.vehicleworks.' + k, en, ar); t('vehicleworks.tooltip.' + k, ten, tar)
t('block.vehicleworks.landing_gear', 'Standard Aircraft Wheel', 'عجلة طائرة قياسية')
# 1.1.0 wing collection
for k, en, ar, ten, tar in (
    ('forward_swept_wing', 'Forward-Swept Wing', 'جناح مائل للأمام', 'Root stalls first: ailerons keep working, agile, docile stall.', 'يبدأ الانهيار من الجذر: الجنيحات تبقى فعالة، رشيق وانهياره هادئ.'),
    ('double_delta_wing', 'Double-Delta Wing', 'جناح دلتا مزدوج', 'Strake + delta: strong vortex lift and a very high stall angle.', 'دلتا مزدوج: رفع دوامي قوي وزاوية انهيار عالية جداً.'),
    ('elliptical_wing', 'Elliptical Wing', 'جناح بيضاوي', 'Lowest induced drag, but the whole span stalls at once.', 'أقل مقاومة مستحثة، لكن الجناح كله ينهار مرة واحدة.'),
    ('tapered_wing', 'Tapered Wing', 'جناح مستدق', 'Near-elliptic efficiency, light and low drag.', 'كفاءة قريبة من البيضاوي، خفيف وقليل المقاومة.'),
    ('high_wing', 'High Wing', 'جناح علوي', 'Mounted above the fuselage: strong roll stability, forgiving, more lift.', 'فوق جسم الطائرة: ثبات قوي في الميلان، متسامح، رفع أكبر.'),
    ('low_wing', 'Low Wing', 'جناح سفلي', 'Mounted under the fuselage: strong ground effect, agile roll.', 'تحت جسم الطائرة: تأثير أرضي قوي وميلان رشيق.'),
    ('canard_wing', 'Canard', 'جناح أمامي (كنارد)', 'Small all-moving foreplane (pitch control); stalls before the main wing.', 'جناح أمامي صغير متحرك للتحكم بالمقدمة؛ ينهار قبل الجناح الرئيسي.'),
    ('flying_wing', 'Flying Wing', 'جناح طائر', 'Tailless all-wing section: very low drag, high efficiency. Use elevons at the trailing edge.', 'قسم جناح طائر بلا ذيل: مقاومة قليلة جداً وكفاءة عالية. استخدم elevon في الحافة الخلفية.')):
    t('block.vehicleworks.' + k, en, ar); t('vehicleworks.tooltip.' + k, ten, tar)
for k, en, ar in (('forward_swept', 'Forward-swept', 'مائل للأمام'), ('double_delta', 'Double delta', 'دلتا مزدوج'), ('elliptical', 'Elliptical', 'بيضاوي'), ('tapered', 'Tapered', 'مستدق'),
                  ('high_wing', 'High wing', 'جناح علوي'), ('low_wing', 'Low wing', 'جناح سفلي'), ('canard', 'Canard', 'كنارد'), ('flying_wing', 'Flying wing', 'جناح طائر')):
    t('vehicleworks.opt.' + k, en, ar)
t('key.vehicleworks.hud_toggle', 'Show / hide HUD', 'إظهار/إخفاء الشاشة HUD')
t('key.vehicleworks.hud_mode', 'HUD style', 'نمط الشاشة HUD')
t('vehicleworks.hud.shown', 'HUD shown', 'تم إظهار HUD')
t('vehicleworks.hud.hidden', 'HUD hidden', 'تم إخفاء HUD')
for i, en, ar in ((0, 'HUD: Full flight', 'HUD: الطيران الكامل'), (1, 'HUD: Minimal', 'HUD: مختصر'), (2, 'HUD: Military', 'HUD: عسكري'), (3, 'HUD: Navigation', 'HUD: الملاحة')):
    t(f'vehicleworks.hud.mode.{i}', en, ar)
# 1.1.0 cockpit systems
for k, en, ar, ten, tar in (
    ('instrument_panel_a', 'Instrument Panel A (6 slots)', 'لوحة عدادات A (6 خانات)', 'Six small instrument slots. Right-click to choose the gauges.', 'ست خانات صغيرة للعدادات. انقر بالزر الأيمن لاختيار العدادات.'),
    ('instrument_panel_b', 'Instrument Panel B (2 small + 1 large)', 'لوحة عدادات B (2 صغيرة + 1 كبيرة)', 'Two small gauges and one large screen.', 'عدادان صغيران وشاشة كبيرة.'),
    ('instrument_panel_c', 'Instrument Panel C (1 large)', 'لوحة عدادات C (خانة كبيرة)', 'One large display: navigation, radar, flight director, PFD or engines.', 'شاشة كبيرة واحدة: ملاحة، رادار، موجه طيران، شاشة طيران أو محركات.'),
    ('aircraft_light', 'Aircraft Light', 'ضوء طائرة', 'Navigation, beacon, strobe, landing, taxi, turn-off or cockpit light. Choose the type with right-click; faces where you look.', 'ضوء ملاحة أو منارة أو وميض أو هبوط أو تدرج أو انعطاف أو مقصورة. اختر النوع بالزر الأيمن؛ يتجه حيث تنظر.'),
    ('refuel_port', 'Refuel Port', 'منفذ تعبئة الوقود', 'Right-click to refuel the aircraft: choose AVGAS or JET-A and the amount.', 'انقر بالزر الأيمن لتعبئة الطائرة: اختر AVGAS أو JET-A والكمية.')):
    t('block.vehicleworks.' + k, en, ar); t('vehicleworks.tooltip.' + k, ten, tar)
for k, en, ar in (('slot_1', 'Small slot 1', 'خانة صغيرة 1'), ('slot_2', 'Small slot 2', 'خانة صغيرة 2'), ('slot_3', 'Small slot 3', 'خانة صغيرة 3'),
                  ('slot_4', 'Small slot 4', 'خانة صغيرة 4'), ('slot_5', 'Small slot 5', 'خانة صغيرة 5'), ('slot_6', 'Small slot 6', 'خانة صغيرة 6'),
                  ('large_slot', 'Large slot', 'الخانة الكبيرة'), ('light_type', 'Light type', 'نوع الضوء')):
    t('vehicleworks.prop.' + k, en, ar)
for k, en, ar in (('none', '— empty —', '— فارغة —'), ('altimeter', 'Altimeter', 'مقياس الارتفاع'), ('airspeed', 'Airspeed Indicator', 'مؤشر السرعة الجوية'),
                  ('vsi', 'Vertical Speed Indicator', 'مؤشر السرعة العمودية'), ('horizon', 'Artificial Horizon', 'الأفق الاصطناعي'), ('heading', 'Heading Indicator', 'مؤشر الاتجاه'),
                  ('compass', 'Compass', 'البوصلة'), ('turn', 'Turn Coordinator', 'منسق الانعطاف'), ('tachometer', 'Tachometer', 'عداد الدوران'),
                  ('fuel_gauge', 'Fuel Gauge', 'مقياس الوقود'), ('engine_pressure', 'Engine Pressure Gauge', 'مقياس ضغط المحرك'), ('nav_display', 'Navigation Display', 'شاشة الملاحة'),
                  ('radar', 'Radar Display', 'شاشة الرادار'), ('flight_director', 'Flight Director', 'موجه الطيران'), ('attitude_display', 'Attitude Display (PFD)', 'شاشة الوضعية (PFD)'),
                  ('engine_display', 'Engine Monitoring Display', 'شاشة مراقبة المحركات'),
                  ('auto_nav', 'Navigation (auto colour)', 'ملاحة (لون تلقائي)'), ('nav_red', 'Navigation red (left)', 'ملاحة أحمر (يسار)'), ('nav_green', 'Navigation green (right)', 'ملاحة أخضر (يمين)'),
                  ('nav_white', 'Navigation white (tail)', 'ملاحة أبيض (ذيل)'), ('beacon', 'Anti-collision beacon', 'منارة منع التصادم'), ('strobe', 'Strobe', 'وميض'),
                  ('landing', 'Landing light', 'ضوء الهبوط'), ('taxi', 'Taxi light', 'ضوء التدرج'), ('turnoff', 'Runway turn-off light', 'ضوء الانعطاف من المدرج'), ('cockpit', 'Cockpit light', 'ضوء المقصورة')):
    t('vehicleworks.opt.' + k, en, ar)
SW = ['Battery', 'Avionics', 'Fuel pump', 'Ignition', 'Starter', 'Fuel selector', 'Nav lights', 'Beacon', 'Strobes', 'Landing lights', 'Taxi lights', 'Turn-off lights',
      'Cockpit light', 'Panel light', 'Landing gear', 'Emergency test', 'Autopilot', 'Parking brake']
SWA = ['البطارية', 'الإلكترونيات', 'مضخة الوقود', 'الإشعال', 'المشغّل', 'محدد الوقود', 'أضواء الملاحة', 'المنارة', 'الوميض', 'أضواء الهبوط', 'أضواء التدرج', 'أضواء الانعطاف',
       'ضوء المقصورة', 'إضاءة اللوحة', 'عجلات الهبوط', 'اختبار الطوارئ', 'الطيار الآلي', 'فرامل الوقوف']
SW += ['Master electrical', 'Generator', 'Engine master', 'Instrument dimmer', 'Display brightness', 'Night lighting', 'Master warning / caution', 'Console lights']
SWA += ['الكهرباء الرئيسية', 'المولّد', 'المحرك الرئيسي (الوقود)', 'مخفّت الإضاءة', 'سطوع الشاشات', 'الإضاءة الليلية', 'التحذير / التنبيه الرئيسي', 'إضاءة الكونسول']
for i in range(len(SW)): t(f'vehicleworks.switch.{i}', SW[i], SWA[i])
for k, en, ar in (('both', 'BOTH', 'الكل'), ('left', 'LEFT', 'يسار'), ('right', 'RIGHT', 'يمين'), ('dim', 'DIM', 'خافت'), ('bright', 'BRIGHT', 'ساطع'),
                  ('cranking', 'CRANKING', 'يدور'), ('elec', 'Battery %s%%  Bus: %s', 'البطارية %s%%  الطاقة: %s'), ('fuel', 'Fuel %s / %s kg', 'الوقود %s / %s كغ'),
                  ('wrong_fuel', 'WRONG FUEL GRADE!', 'نوع وقود خاطئ!'), ('ack', 'ACK', 'تأكيد'),
                  ('level0', 'OFF', 'إيقاف'), ('level1', '1', '1'), ('level2', '2', '2'), ('level3', '3', '3'), ('level4', '4', '4'), ('level5', '5', '5')):
    t('vehicleworks.panel.' + k, en, ar)
for k, en, ar in (('ok', 'Starter engaged - engines spooling up', 'تم تشغيل المشغّل - المحركات تدور'), ('no_power', 'No electrical power: turn the BATTERY on', 'لا توجد طاقة كهربائية: شغّل البطارية'),
                  ('no_pump', 'Turn the FUEL PUMP on first', 'شغّل مضخة الوقود أولاً'), ('no_ignition', 'Turn the IGNITION on first', 'شغّل الإشعال أولاً'),
                  ('fuel_off', 'Fuel selector is OFF', 'محدد الوقود على OFF'), ('no_avionics', 'Autopilot needs AVIONICS power', 'الطيار الآلي يحتاج طاقة الإلكترونيات'),
                  ('no_bus', 'Battery on, but the MASTER switch is off: no power on the bus', 'البطارية تعمل لكن مفتاح MASTER مطفأ: لا طاقة على الناقل'),
                  ('cranking', 'Starter engaged: ignition + fuel (ENG MASTER) to light the engine', 'المشغّل يعمل: الإشعال + الوقود (ENG MASTER) لإشعال المحرك'),
                  ('released', 'Starter released', 'تم تحرير المشغّل')):
    t('vehicleworks.start.' + k, en, ar)
for k, en, ar in (('title', 'Refuelling', 'تعبئة الوقود'), ('grade', 'Fuel: %s', 'الوقود: %s'), ('amount', 'Amount: %s kg', 'الكمية: %s كغ'), ('start', 'Start pump', 'تشغيل المضخة'),
                  ('stop', 'Stop pump', 'إيقاف المضخة'), ('needs', 'Engines need: %s', 'المحركات تحتاج: %s'), ('canisters', 'Fuel canisters in inventory: %s', 'علب الوقود في المخزون: %s'),
                  ('flowing', 'Fuel flowing... %s kg to go', 'الوقود يتدفق... باقي %s كغ'), ('no_port', 'This aircraft has no Refuel Port', 'هذه الطائرة ليس فيها منفذ تعبئة'),
                  ('stopped', 'Refuelling stopped (%s kg on board)', 'توقفت التعبئة (%s كغ في الخزانات)'), ('done', 'Refuelling complete (%s kg on board)', 'اكتملت التعبئة (%s كغ في الخزانات)'),
                  ('no_canisters', 'Out of fuel canisters', 'نفدت علب الوقود')):
    t('vehicleworks.refuel.' + k, en, ar)
t('vehicleworks.msg.passenger_no_controls', 'Passengers have no access to the aircraft controls', 'الركاب لا يمكنهم الوصول إلى أدوات التحكم بالطائرة')
for k, en, ar in (('title', 'Seat controls', 'التحكم بالمقعد'), ('passenger', 'Passenger seat', 'مقعد راكب'),
                  ('hint', 'Right-click the buttons on your arm rest, or press %s', 'اضغط بالزر الأيمن على أزرار مسند الذراع، أو اضغط %s'), ('hint_plain', 'You are a passenger', 'أنت راكب'),
                  ('passenger_only', 'Passenger seat: only your seat can be adjusted', 'مقعد راكب: يمكنك تعديل مقعدك فقط'),
                  ('status', 'Recline %s%%   Leg rest %s%%   Position %s', 'الإمالة %s%%   مسند الأرجل %s%%   الموضع %s'),
                  ('open', 'open', 'مفتوح'), ('closed', 'closed', 'مغلق'),
                  ('light.0', 'off', 'مطفأ'), ('light.1', 'reading light', 'ضوء القراءة'), ('light.2', 'ambient', 'إضاءة محيطة'),
                  ('class.1', 'Economy', 'الدرجة الاقتصادية'), ('class.2', 'Economy Plus', 'الاقتصادية الممتازة'), ('class.3', 'Premium Economy', 'البريميوم الاقتصادي'),
                  ('class.4', 'Business Class', 'درجة الأعمال'), ('class.5', 'First Class Suite', 'جناح الدرجة الأولى'),
                  ('ctl.0', 'Move seat forward', 'تقديم المقعد'), ('ctl.1', 'Move seat back', 'إرجاع المقعد'), ('ctl.2', 'Raise backrest', 'رفع مسند الظهر'),
                  ('ctl.3', 'Recline backrest', 'خفض مسند الظهر'), ('ctl.4', 'Raise leg rest', 'رفع مسند الأرجل'), ('ctl.5', 'Lower leg rest', 'خفض مسند الأرجل'),
                  ('ctl.6', 'Lie flat / bed: %s', 'وضع الاستلقاء / السرير: %s'), ('ctl.7', 'Screen: %s', 'الشاشة: %s'), ('ctl.8', 'Table: %s', 'الطاولة: %s'),
                  ('ctl.9', 'Seat light: %s', 'إضاءة المقعد: %s'), ('ctl.10', 'Suite door: %s', 'باب الجناح: %s'), ('ctl.11', 'Screen page: %s', 'صفحة الشاشة: %s')):
    t('vehicleworks.cabin.' + k, en, ar)
for k, en, ar in (('0', 'Flight info', 'معلومات الرحلة'), ('1', 'Moving map', 'الخريطة'), ('2', 'Entertainment', 'الترفيه')):
    t('vehicleworks.ife.page.' + k, en, ar)
t('vehicleworks.fuel.avgas', 'AVGAS 100LL (piston)', 'AVGAS 100LL (مكبسي)')
t('vehicleworks.fuel.jet', 'JET-A (turbine)', 'JET-A (توربيني)')
# 1.1.2 configuration screen
CFG = {
 'construction': ('Construction', 'البناء'), 'physics': ('Physics', 'الفيزياء'), 'environment': ('Environment', 'البيئة'), 'damage': ('Damage', 'الضرر'),
 'weapons': ('Weapons', 'الأسلحة'), 'fuel': ('Fuel', 'الوقود'), 'network': ('Network', 'الشبكة'),
 'maxBlocks': ('Max blocks per vehicle', 'أقصى عدد بلوكات للمركبة'), 'maxExtent': ('Max vehicle size (blocks)', 'أقصى حجم للمركبة'),
 'substeps': ('Physics substeps', 'خطوات الفيزياء'), 'maxSubsteps': ('Max adaptive substeps', 'أقصى خطوات تكيفية'), 'gravity': ('Gravity (m/s²)', 'الجاذبية'),
 'altitudeScale': ('Altitude scale', 'مقياس الارتفاع'), 'blockMassScale': ('Block mass multiplier', 'مضاعف وزن البلوكات'), 'gasLiftScale': ('Balloon lift multiplier', 'مضاعف رفع المناطيد'),
 'maxSpeed': ('Speed limit (m/s)', 'حد السرعة (م/ث)'), 'windSpeed': ('Wind speed (m/s)', 'سرعة الرياح'), 'turbulence': ('Turbulence', 'الاضطراب'),
 'waveAmplitude': ('Wave height (m)', 'ارتفاع الموج'), 'crushDepth': ('Submarine crush depth (m)', 'عمق تحطم الغواصة'),
 'crashDamage': ('Crash damage', 'ضرر الاصطدام'), 'crashDamageScale': ('Crash destruction strength', 'قوة التدمير عند الاصطدام'),
 'aircraftDamageMultiplier': ('Aircraft Damage Multiplier (1-10)', 'مضاعف ضرر الطائرات (1-10)'), 'lightsIlluminateWorld': ('Landing lights light the ground', 'أضواء الهبوط تضيء الأرض'),
 'crashSpeedThreshold': ('No-damage impact speed (m/s)', 'سرعة الاصطدام بدون ضرر'), 'structuralStrength': ('Wing structural strength', 'قوة هيكل الجناح'),
 'weaponsEnabled': ('Weapons enabled', 'تفعيل الأسلحة'), 'weaponsBreakBlocks': ('Weapons break blocks', 'الأسلحة تكسر البلوكات'), 'weaponDamageScale': ('Weapon damage', 'ضرر الأسلحة'),
 'requireFuel': ('Engines need fuel', 'المحركات تحتاج وقود'), 'fuelConsumptionScale': ('Fuel consumption', 'استهلاك الوقود'), 'syncRangeBlocks': ('Sync range (blocks)', 'مدى المزامنة'),
 'lodDistance': ('Detail distance', 'مسافة التفاصيل'), 'farLodDistance': ('Far detail distance', 'مسافة التفاصيل البعيدة'), 'renderBlockEntities': ('Render block entities', 'رسم كيانات البلوكات'),
 'hudMode': ('HUD style (0-3)', 'نمط HUD (0-3)'), 'hudVisible': ('Show HUD', 'إظهار HUD'), 'voiceCallouts': ('Voice callouts', 'التنبيهات الصوتية'),
 'cameraRoll': ('Camera rolls with the aircraft', 'الكاميرا تميل مع الطائرة'), 'hudScale': ('HUD scale', 'حجم HUD'), 'mouseAimDefault': ('Mouse-aim by default', 'التصويب بالفأرة افتراضياً'),
 'engineVolume': ('Engine volume', 'صوت المحركات')}
CFG.update({'controls': ('Controls', 'التحكم'),
 'passengerSeatInteraction': ('Passenger seat interaction', 'تفاعل الركاب مع المقاعد'), 'controlSensitivity': ('Flight control sensitivity', 'حساسية أدوات التحكم'),
 'sidestickSensitivity': ('Side stick sensitivity', 'حساسية عصا التحكم الجانبية'), 'sidestickDeadZone': ('Side stick dead zone', 'المنطقة الميتة لعصا التحكم'),
 'cockpitBrightness': ('Cockpit lighting brightness', 'سطوع إضاءة القمرة'), 'instrumentBrightness': ('Instrument brightness', 'سطوع أجهزة القياس'),
 'hudBrightness': ('HUD brightness', 'سطوع HUD'), 'startupAnimationSeconds': ('Start-up animation duration (s)', 'مدة حركة التشغيل (ثانية)'),
 'cockpitVolume': ('Cockpit sound volume', 'صوت القمرة'), 'warningVolume': ('Warning sound volume', 'صوت التحذيرات'),
 'passengerScreenBrightness': ('Passenger screen brightness', 'سطوع شاشات الركاب')})
for k, (en, ar) in CFG.items():
    t('vehicleworks.configuration.' + k, en, ar)
t('vehicleworks.configuration.title', 'VehicleWorks settings', 'إعدادات VehicleWorks')
t('vehicleworks.cmd.damage_now', 'Aircraft damage multiplier: %s  (crash destruction strength: %s)', 'مضاعف ضرر الطائرات: %s  (قوة التدمير عند الاصطدام: %s)')
t('vehicleworks.cmd.damage_set', 'Aircraft damage multiplier set to %s', 'تم ضبط مضاعف ضرر الطائرات على %s')
t('vehicleworks.prop.side', 'Side', 'الجهة')
t('vehicleworks.opt.left', 'Left (captain)', 'يسار (القائد)')
t('vehicleworks.opt.right', 'Right (first officer)', 'يمين (المساعد)')
for k, en, ar in (('engine.turboprop', 'Turboprop runs', 'محرك توربيني مروحي يعمل'), ('engine.turbofan', 'Turbofan roars', 'محرك توربوفان يهدر'),
                  ('engine.heli_turbine', 'Turboshaft whines', 'توربين المروحية يصفر'), ('engine.rough', 'Engine runs rough', 'المحرك يعمل بخشونة'),
                  ('engine.light_off', 'Engine lights off', 'اشتعال المحرك'), ('engine.piston_catch', 'Engine catches', 'المحرك يدور'),
                  ('engine.starter_piston', 'Starter cranks', 'المشغّل يدير المحرك'), ('engine.starter_turbine', 'Starter whines', 'صفير المشغّل'),
                  ('engine.ignition', 'Igniters snap', 'طقطقة الإشعال'), ('engine.spool_down', 'Engine spools down', 'المحرك يتباطأ'),
                  ('cockpit.toggle', 'Switch clicks', 'نقرة مفتاح'), ('cockpit.relay', 'Relay clunks', 'صوت المرحّل'), ('cockpit.button', 'Button pressed', 'ضغط زر'),
                  ('cockpit.knob', 'Knob turns', 'تدوير مقبض'), ('cockpit.guard', 'Guard flips', 'فتح الغطاء'), ('cockpit.lever', 'Lever moves', 'تحريك الذراع'),
                  ('cockpit.avionics', 'Avionics power up', 'تشغيل الإلكترونيات'), ('cockpit.ap_disconnect', 'Autopilot disconnects', 'فصل الطيار الآلي'),
                  ('cockpit.gear_horn', 'Gear horn', 'إنذار العجلات'), ('cockpit.hydraulic', 'Hydraulic pump', 'المضخة الهيدروليكية'),
                  ('cockpit.ambient', 'Cockpit hum', 'همهمة القمرة'), ('cabin.door', 'Suite door slides', 'باب الجناح ينزلق'), ('cabin.seat_motor', 'Seat motor', 'محرك المقعد'),
                  ('cabin.tray', 'Tray table', 'الطاولة'), ('cabin.ife', 'Cabin chime', 'رنين المقصورة'), ('vehicle.airflow', 'Airflow', 'تدفق الهواء'),
                  ('vehicle.gear_extend', 'Gear extends', 'نزول العجلات'), ('vehicle.gear_retract', 'Gear retracts', 'رفع العجلات'), ('vehicle.flaps', 'Flaps move', 'حركة القلابات'),
                  ('vehicle.spoiler', 'Spoilers deploy', 'فتح المفسدات'), ('vehicle.brake', 'Brakes squeal', 'صرير الفرامل')):
    t('subtitles.vehicleworks.' + k, en, ar)
# ---- 1.3: A320 v2 (pedestal, FCU, cabin fixtures, crew, announcements, first officer, emergencies)
for k, (en, ar, ten, tar) in {
    'jumpseat': ('Cabin Crew Jump Seat', 'مقعد طاقم الضيافة', 'Fold-down attendant seat with a four-point harness. Sit: crew controls (X) and announcements (K).', 'مقعد مضيف قابل للطي مع حزام رباعي. اجلس: لوحة الطاقم (X) والإعلانات (K).'),
    'crew_panel': ('Attendant Panel (FAP)', 'لوحة المضيفين (FAP)', 'Cabin lights, seat belt sign, announcements, door status, call lights and interphone.', 'إضاءة المقصورة وإشارة الأحزمة والإعلانات وحالة الأبواب وأضواء النداء والإنتركم.'),
    'galley': ('Galley Unit', 'وحدة المطبخ', 'Cabinets, oven and coffee maker. Right-click to open the doors / start the oven / brew coffee.', 'خزائن وفرن وماكينة قهوة. اضغط بالزر الأيمن لفتح الأبواب أو تشغيل الفرن أو تحضير القهوة.'),
    'trolley': ('Service Trolley', 'عربة الخدمة', 'Right-click to push it along the aisle.', 'اضغط بالزر الأيمن لدفعها في الممر.'),
    'lavatory': ('Lavatory', 'دورة المياه', 'Toilet, sink, mirror, soap and light, lockable door with an OCCUPIED sign.', 'مرحاض ومغسلة ومرآة وصابون وإضاءة، وباب يُقفل مع إشارة مشغول.'),
    'aircraft_door': ('Aircraft Door', 'باب الطائرة', 'Passenger or cargo door, two blocks high. Opens outward and slides; locked in flight.', 'باب ركاب أو شحن بارتفاع بلوكين. يفتح للخارج وينزلق، ويُقفل أثناء الطيران.'),
    'instrument_panel_d': ('Instrument Panel D (PFD + ND)', 'لوحة أجهزة D (PFD + ND)', 'Two large displays side by side: primary flight display and navigation display.', 'شاشتان كبيرتان جنباً إلى جنب: شاشة الطيران الرئيسية وشاشة الملاحة.'),
    'instrument_panel_e': ('Instrument Panel E (ECAM)', 'لوحة أجهزة E (ECAM)', 'Two large displays stacked: engine / warning display and system display.', 'شاشتان كبيرتان فوق بعضهما: شاشة المحركات والتحذيرات وشاشة الأنظمة.'),
    'fcu_panel': ('Flight Control Unit (FCU)', 'وحدة التحكم بالطيران (FCU)', 'Glareshield panel: SPD / HDG / ALT / V/S windows and knobs, AP1 and A/THR.', 'لوحة أعلى الشاشات: نوافذ وأزرار السرعة والاتجاه والارتفاع والسرعة العمودية وAP1 وA/THR.'),
    'center_pedestal': ('Centre Pedestal', 'الكونسول الأوسط', 'Thrust levers, engine masters, speed brake, flaps, gear lever, parking brake and two MCDUs.', 'أذرع الدفع ومفاتيح المحركات والمكابح الهوائية والقلابات وذراع العجلات وفرامل الوقوف وجهازا MCDU.'),
}.items():
    t('block.vehicleworks.' + k, en, ar); t('vehicleworks.tooltip.' + k, ten, tar)
t('vehicleworks.prop.mirror', 'Mirrored (right side)', 'معكوس (الجهة اليمنى)')
t('vehicleworks.prop.door_kind', 'Door type', 'نوع الباب')
t('vehicleworks.opt.passenger', 'Passenger', 'ركاب'); t('vehicleworks.opt.cargo', 'Cargo', 'شحن')
t('key.vehicleworks.announce', 'Cabin announcements', 'إعلانات المقصورة')
t('key.vehicleworks.copilot', 'First officer: seat / assist off / dismiss', 'مساعد الطيار: إجلاس / إيقاف المساعدة / صرف')
for i, (en, ar) in enumerate((('Thrust: IDLE', 'الدفع: خامل'), ('Thrust: CL (climb)', 'الدفع: CL (صعود)'), ('Thrust: FLX / MCT', 'الدفع: FLX / MCT'), ('Thrust: TOGA', 'الدفع: TOGA'))):
    t('vehicleworks.ped.thrust.%d' % i, en, ar)
for i, (en, ar) in enumerate((('Flaps 0', 'القلابات 0'), ('Flaps 1', 'القلابات 1'), ('Flaps 2', 'القلابات 2'), ('Flaps 3', 'القلابات 3'), ('Flaps FULL', 'القلابات FULL'))):
    t('vehicleworks.ped.flaps.%d' % i, en, ar)
t('vehicleworks.ped.speedbrake', 'Speed brake %s%%', 'المكابح الهوائية %s%%')
t('vehicleworks.ped.master_on', 'Engine masters ON', 'مفاتيح المحركات تشغيل'); t('vehicleworks.ped.master_off', 'Engine masters OFF', 'مفاتيح المحركات إيقاف')
t('vehicleworks.fcu.athr_on', 'A/THR engaged', 'الدفع الآلي A/THR مفعل'); t('vehicleworks.fcu.athr_off', 'A/THR off', 'الدفع الآلي A/THR مطفأ')
for k, en, ar in (('title', 'Cabin crew panel', 'لوحة طاقم المقصورة'), ('lights', 'Cabin lights: %s', 'إضاءة المقصورة: %s'),
                  ('lights.0', 'off', 'مطفأة'), ('lights.1', 'night (dim)', 'ليلية (خافتة)'), ('lights.2', 'bright', 'ساطعة'), ('lights.3', 'boarding', 'صعود'),
                  ('belts', 'Seat belt sign: %s', 'إشارة ربط الأحزمة: %s'), ('announce', 'Announcements...', 'الإعلانات...'), ('reset_calls', 'Reset call lights', 'إعادة ضبط أضواء النداء'),
                  ('board', 'Board passengers', 'صعود الركاب'), ('deplane', 'Deplane passengers', 'نزول الركاب'), ('call_cockpit', 'Interphone: call the cockpit', 'الإنتركم: الاتصال بالقمرة'),
                  ('doors', 'Doors', 'الأبواب'), ('calls', 'Call lights: %s', 'أضواء النداء: %s'), ('passengers', 'Passengers on board: %s', 'الركاب على متن الطائرة: %s'),
                  ('boarded', '%s passengers boarded', 'صعد %s راكباً'), ('deplaned', '%s passengers left the aircraft', 'نزل %s راكباً من الطائرة'),
                  ('interphone', 'Cabin calling the cockpit', 'المقصورة تتصل بالقمرة'), ('seat', 'Cabin crew seat', 'مقعد طاقم الضيافة'),
                  ('hud', 'Calls %s   Doors %s   Belts %s', 'النداءات %s   الأبواب %s   الأحزمة %s'),
                  ('hint', '%s: crew panel   %s: announcements', '%s: لوحة الطاقم   %s: الإعلانات')):
    t('vehicleworks.crew.' + k, en, ar)
t('vehicleworks.pa.title', 'Cabin announcements', 'إعلانات المقصورة')
t('vehicleworks.pa.auto', 'Automatic by flight phase: %s', 'تلقائي حسب مرحلة الرحلة: %s')
PA = (('Boarding', 'الصعود', 'Welcome aboard. Please find your seat and place your bags in the overhead bins.', 'أهلاً بكم على متن الطائرة. يرجى التوجه إلى مقاعدكم ووضع الحقائب في الخزائن العلوية.'),
      ('Welcome', 'الترحيب', 'Ladies and gentlemen, the doors are closed. Please fasten your seat belts and switch your devices to flight mode.', 'سيداتي سادتي، تم إغلاق الأبواب. يرجى ربط الأحزمة وتحويل الأجهزة إلى وضع الطيران.'),
      ('Safety', 'تعليمات السلامة', 'Please pay attention to the safety demonstration. Emergency exits are located at the front and rear of the cabin.', 'يرجى الانتباه لتعليمات السلامة. مخارج الطوارئ في مقدمة ومؤخرة المقصورة.'),
      ('Takeoff', 'الإقلاع', 'Cabin crew, prepare for takeoff.', 'طاقم الضيافة، استعدوا للإقلاع.'),
      ('In flight', 'أثناء الرحلة', 'We have reached our cruising altitude. You may now use the entertainment system. Please keep your seat belt fastened while seated.', 'وصلنا إلى ارتفاع الرحلة. يمكنكم الآن استخدام نظام الترفيه. يرجى إبقاء الحزام مربوطاً أثناء الجلوس.'),
      ('Landing', 'الهبوط', 'We are starting our descent. Please return to your seats, raise your seat backs and stow your tray tables.', 'بدأنا الهبوط. يرجى العودة إلى المقاعد ورفع مساند الظهر وإغلاق الطاولات.'),
      ('Arrival', 'الوصول', 'Welcome to your destination. Please remain seated until the seat belt sign is switched off.', 'مرحباً بكم في وجهتكم. يرجى البقاء في المقاعد حتى تنطفئ إشارة ربط الأحزمة.'),
      ('Deplaning', 'النزول', 'You may now leave the aircraft. Thank you for flying with us.', 'يمكنكم الآن مغادرة الطائرة. شكراً لسفركم معنا.'))
for i, (n, na, tx, ta) in enumerate(PA):
    t('vehicleworks.pa.name.%d' % i, n, na); t('vehicleworks.pa.text.%d' % i, tx, ta)
for k, en, ar in (('prefix', 'First officer: %s', 'مساعد الطيار: %s'), ('no_seat', 'no free co-pilot seat', 'لا يوجد مقعد مساعد طيار فارغ'),
                  ('seated', 'seated beside you, assist on', 'جلس بجانبك، المساعدة مفعلة'), ('assist_off', 'assist off (press again to dismiss)', 'المساعدة متوقفة (اضغط مرة أخرى لصرفه)'),
                  ('dismissed', 'left the cockpit', 'غادر القمرة'), ('gear_up', 'positive climb, gear up', 'صعود إيجابي، رفع العجلات'),
                  ('gear_down', 'gear down', 'إنزال العجلات'), ('pull_up', 'terrain, pulling up!', 'تضاريس، أرفع المقدمة!'), ('stall', 'stall, nose down, thrust', 'انهيار، خفض المقدمة، زيادة الدفع')):
    t('vehicleworks.fo.' + k, en, ar)
t('vehicleworks.msg.door_locked', 'The door is locked in flight', 'الباب مقفل أثناء الطيران')
t('vehicleworks.msg.gear_jammed', 'Landing gear jammed!', 'عجلات الهبوط عالقة!')
t('vehicleworks.cmd.emergency', 'Emergency triggered: %s', 'تم تفعيل حالة طوارئ: %s')
t('vehicleworks.cmd.emergency_cleared', 'All emergencies cleared', 'تم إلغاء كل حالات الطوارئ')
t('vehicleworks.cmd.emergency_unknown', 'Unknown emergency: %s', 'حالة طوارئ غير معروفة: %s')
t('vehicleworks.cabin.ctl.12', 'Call attendant: %s', 'نداء المضيف: %s'); t('vehicleworks.cabin.ctl.13', 'Window shade: %s', 'غطاء النافذة: %s')
for k, en, ar in (('cabin_pressure', 'CABIN PRESSURE', 'ضغط المقصورة'), ('gear_jammed', 'GEAR JAMMED', 'العجلات عالقة'), ('fuel_leak', 'FUEL LEAK', 'تسرب وقود'), ('generator', 'GENERATOR FAULT', 'عطل المولد')):
    t('vehicleworks.warning.' + k, en, ar)
EMER = {'emergencies': ('Emergencies', 'حالات الطوارئ'), 'emergenciesEnabled': ('Random emergencies', 'طوارئ عشوائية'),
        'enabled': ('Enabled', 'مفعّل'), 'chancePercent': ('Chance per flight hour (%)', 'نسبة الحدوث لكل ساعة طيران (%)'),
        'engineFailure': ('Engine failure', 'عطل المحرك'), 'engineFire': ('Engine fire', 'حريق المحرك'), 'birdStrike': ('Bird strike', 'اصطدام طائر'),
        'hydraulicFailure': ('Hydraulic failure', 'عطل هيدروليكي'), 'generatorFailure': ('Generator failure', 'عطل المولد'), 'fuelLeak': ('Fuel leak', 'تسرب الوقود'),
        'gearJam': ('Landing gear jam', 'علوق العجلات'), 'instrumentFailure': ('Instrument failure', 'عطل الأجهزة'), 'depressurization': ('Cabin depressurization', 'فقدان ضغط المقصورة')}
for k, (en, ar) in EMER.items(): t('vehicleworks.configuration.' + k, en, ar)
for k, en, ar in (('cabin.pa_chime', 'Cabin chime', 'رنين الإعلان'), ('cabin.call_chime', 'Attendant call', 'نداء المضيف'),
                  ('vehicle.gear_rattle', 'Gear rattles', 'اهتزاز العجلات'), ('vehicle.tire_bump', 'Tyres bump', 'ارتطام الإطارات')):
    t('subtitles.vehicleworks.' + k, en, ar)
# ---- 1.4: one-block wing, fairing cones, airliner engine
for k, (en, ar, ten, tar) in {
    'wing_assembly': ('Wing Assembly (one block)', 'جناح كامل (بلوكة واحدة)', 'A whole wing half in one block, sized by its settings: taper, sweep, dihedral, moving flaps, ailerons, slats and spoilers, sharklet and built-in navigation, strobe and landing lights. Can also be a horizontal stabiliser or a vertical fin.', 'نصف جناح كامل في بلوكة واحدة بحجم تحدده الإعدادات: تدبب وميلان وزاوية رفع، وقلابات وجنيحات وشرائح أمامية ومفسدات متحركة، وجنيح طرفي، وأضواء ملاحة ووميض وهبوط مدمجة. ويمكن جعله مثبتاً أفقياً أو ذيلاً عمودياً.'),
    'fairing_cone': ('Fairing Cone (nose / tail)', 'مخروط انسيابي (مقدمة / ذيل)', 'Smooth nose radome or upswept tail cone in front of / behind the block, sized by its settings.', 'مقدمة رادار ناعمة أو مخروط ذيل مرفوع أمام البلوكة أو خلفها، بحجم تحدده الإعدادات.'),
    'airliner_engine': ('Airliner Engine (nacelle)', 'محرك طائرة ركاب (ناسيل)', 'Under-wing turbofan: nacelle, spinning fan and spinner, exhaust cone and pylon, all in one block.', 'محرك توربوفان تحت الجناح: غلاف ومروحة دوارة ومخروط أمامي ومخروط عادم وحامل، في بلوكة واحدة.'),
}.items():
    t('block.vehicleworks.' + k, en, ar); t('vehicleworks.tooltip.' + k, ten, tar)
for k, en, ar in (('span', 'Span', 'طول الجناح'), ('root_chord', 'Root chord', 'عرض الجذر'), ('tip_chord', 'Tip chord', 'عرض الطرف'), ('dihedral', 'Dihedral', 'زاوية الرفع الجانبية'),
                  ('flaps', 'Flaps', 'القلابات'), ('ailerons', 'Ailerons', 'الجنيحات'), ('slats', 'Slats', 'الشرائح الأمامية'), ('spoilers', 'Spoilers', 'المفسدات'),
                  ('lights', 'Built-in lights', 'أضواء مدمجة'), ('winglet', 'Wing tip', 'طرف الجناح'), ('kind', 'Surface', 'نوع السطح'),
                  ('width', 'Width', 'العرض'), ('height', 'Height', 'الارتفاع'), ('length', 'Length', 'الطول'), ('tip_offset', 'Tip height', 'ارتفاع الطرف'),
                  ('center_y', 'Vertical offset', 'الإزاحة العمودية'), ('style', 'Style', 'النمط'), ('color', 'Colour', 'اللون'), ('diameter', 'Diameter', 'القطر'), ('pylon', 'Pylon height', 'ارتفاع الحامل')):
    t('vehicleworks.prop.' + k, en, ar)
for k, en, ar in (('none', 'None', 'بدون'), ('sharklet', 'Sharklet', 'شاركلت'), ('blended', 'Blended winglet', 'جنيح طرفي منحني'), ('main_wing', 'Main wing', 'جناح رئيسي'),
                  ('stabilizer', 'Horizontal stabiliser', 'مثبت أفقي'), ('fin', 'Vertical fin', 'ذيل عمودي'), ('nose', 'Nose', 'مقدمة'), ('tail', 'Tail cone', 'مخروط الذيل'),
                  ('white', 'White', 'أبيض'), ('gray', 'Grey', 'رمادي'), ('blue', 'Blue', 'أزرق')):
    t('vehicleworks.opt.' + k, en, ar)
t('vehicleworks.configuration.announcementVoice', 'Spoken cabin announcements', 'نطق إعلانات المقصورة بالصوت')
# ---- 1.5: aircraft skin blocks
for k, en, ar in (('skin_panel', 'Fuselage Skin Panel', 'لوح هيكل الطائرة'), ('skin_stripe', 'Fuselage Skin (cheatline)', 'لوح هيكل بخط ملون'),
                  ('skin_belly', 'Belly Skin Panel', 'لوح بطن الطائرة'), ('skin_window', 'Cabin Window Panel', 'لوح نافذة المقصورة'),
                  ('cabin_floor', 'Cabin Floor', 'أرضية المقصورة'), ('cockpit_glass', 'Cockpit Glass', 'زجاج القمرة'),
                  ('skin_curve', 'Fuselage Curve (rounded corner)', 'زاوية هيكل منحنية'), ('skin_bevel_wall', 'Fuselage Bevel (wall)', 'حافة مائلة للجدار'),
                  ('skin_bevel_roof', 'Fuselage Bevel (roof / belly)', 'حافة مائلة للسقف والبطن'), ('skin_slope', 'Fuselage Slope (45°)', 'منحدر الهيكل (45°)')):
    t('block.vehicleworks.' + k, en, ar)
en = {k: v[0] for k, v in sorted(L.items())}
ar = {k: v[1] for k, v in sorted(L.items())}
for name, d in (('en_us', en), ('ar_sa', ar), ('ar_iq', ar)):
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8') as f: json.dump(d, f, indent=2, ensure_ascii=False)
print('lang keys', len(L))
