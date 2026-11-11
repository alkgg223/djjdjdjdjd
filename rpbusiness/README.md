# Roleplay Business Simulator — Minecraft 1.21.1 / NeoForge 21.1.264 (Singleplayer)

**Stage 1: Restaurant + NPC customers + NPC staff + cash economy.**
GeckoLib 4.9.3 is bundled inside the jar (no extra download needed). Works in normal new or existing worlds — no custom world, no dimension, no internet.

## Install
Put `RoleplayBusiness-1.0.0-stage1.jar` into `mods/` (NeoForge 21.1.264 for Minecraft 1.21.1, Java 21). Only this one jar is needed.

## Quick start
1. On first join you get **$500 cash**, a **Business Planner** and a **Recipe Book**.
2. **Build your restaurant yourself**, block by block: walls, door, tables (Dining Table) with chairs/sofas/bar stools next to them, kitchen equipment, fridge, cash register…
3. Take the **Business Planner**: right-click two opposite corners of your building, then right-click the air → name it, choose the type → **Register**.
4. Place a **Business Computer** inside → manage menu, prices, hours, staff, finances.
5. Optional: **Business Layout Tool** – right-click any block you placed to decide what it is used for (customer seat, table, cashier station, card station, storage, serving counter, decor, or "not for business use"). Works on vanilla blocks too (stairs as seats, slabs as tables, chests as storage).

## Cooking by hand
| Step | How |
|---|---|
| Cut | Put tomato/onion/lettuce/potato/beef/cheese/bread on the **Cutting Board**, right-click it with the **Kitchen Knife** several times |
| Grill | Put a raw patty or raw beef on the **Grill** (2 spots). Flip with the **Spatula**. Take it off with an empty hand: raw → undercooked → cooked → well-done → burnt |
| Fry / Bake / Boil | Raw fries → **Fryer**, raw pizza → **Oven**, soup mix → **Cooking Pot** |
| Assemble | Stack ingredients on the **Prep Station**, empty-hand right-click to assemble (burger, cheeseburger, club sandwich, salad, raw pizza, soup mix) |
| Drinks | Load **Cola Syrup / Coffee Beans** into the **Drink Machine**, use a **Paper Cup**. Empty-hand click switches cola/coffee |
| Serve | Right-click the customer holding the dish, or put it on a **Serving Counter** for a waiter |
| Payment | Customers queue at the **Cash Register** (right-click it or the customer) or pay by card at a **Payment Terminal** |
| Clean | Use the **Mop** on dirty tables |

Food quality (shown in the tooltip) affects customer satisfaction, tips and reputation.

## Staff (only hired when YOU click Hire)
Business Computer → **Hire**: Chef, Assistant Chef, Waiter, Cashier, Cleaner, Manager. Salaries are paid every morning from the business cash (unpaid staff go on strike).
**Staff Planner**: right-click an employee to select → sneak+right-click to change mode → right-click blocks: standing position, **workstations** (that exact grill/register/board – the employee then only uses those), service position, preferred storage, patrol route.
Employees use the real equipment and real ingredients from storage; when something is missing they report the shortage.

## Supplies
**Supplier Terminal** → order ingredients/tools. A courier NPC walks them to your **Delivery Crate** (or hands them to you if you have no crate). Nothing appears before delivery.

## Config
`config/rpbusiness-common.toml`: customers on/off, max customers, spawn interval, patience, starting cash, max business size.

## Ready-made buildings (optional)
Kits for every business type (restaurant, café, fast food, bakery, grocery, supermarket, clothing, electronics, furniture, butcher, gas station, hotel, bank, delivery company, warehouse).
Right-click the ground where the entrance should be: the building appears facing you, furnished, stocked, registered in your name, with a walkway to the ground. Buy them from **BuildRight Construction** in the Supplier Terminal (or take them from the creative tab). Building by hand is still fully supported.

## Banking
Bank Counter: checking/savings accounts, cash deposit/withdrawal, transfers, history, loans (all terms shown before you confirm; installments are taken every morning), bank cards. ATM: use your card. Safe: only you can open it. Every business has its own bank account (card payments go there; salaries are paid from it if the cash drawer is empty).

## Shops, hotel, delivery
- Shops: put products on Display Shelves / Refrigerated Displays; set prices in the Products tab. Stock Manager refills shelves, Sales Assistant helps shoppers.
- Hotel: Hotel Beds; guests check in at the Cash Register, pay, rest in a room; the Cleaner makes the beds. Room price in the Rooms tab.
- Delivery company: accept customer requests (the sender brings the parcel to your office, the driver delivers it), or move goods between your own businesses.
- **Manager**: checks stock every in-game hour and orders what is running low from the suppliers (Settings → Manager buys stock, with a budget per order).
- Global price control: price % and discount per business, or all businesses at once (Empire tab).

---

# محاكي الأعمال والأدوار — ماينكرافت 1.21.1 / نيوفورج 21.1.264 (لعب فردي)

**المرحلة 1: المطعم + الزبائن + الموظفون + النقود.** مكتبة GeckoLib مدمجة داخل الملف، فلا تحتاج تحميلها. يعمل في أي عالم عادي (جديد أو قديم) بدون إنترنت.

- **التثبيت:** ضع الملف في مجلد `mods`.
- **البداية:** تحصل على 500$ ومخطط المشروع وكتاب الوصفات.
  1. ابنِ مطعمك بنفسك.
  2. بمخطط المشروع انقر على زاويتين متقابلتين، ثم انقر في الهواء وسجّل المطعم.
  3. ضع **حاسوب المشروع** بالداخل لإدارة القائمة والأسعار والموظفين والمالية.
- **أداة تخطيط المشروع:** انقر على أي كتلة وضعتها لتحديد وظيفتها (مقعد، طاولة، كاشير، تخزين، طاولة تقديم...).
- **الطبخ:**
  - التقطيع: لوح التقطيع + السكين.
  - الشواء: اقلب بملعقة القلب.
  - القلي والخبز والسلق: القلاية والفرن والقدر.
  - التجميع: محطة التحضير.
  - المشروبات: آلة المشروبات + كوب ورقي.
  - التقديم: انقر على الزبون وأنت تحمل الطبق.
- **الموظفون:** لا يتم توظيف أحد إلا بنقرتك. بمخطط الموظفين تحدد لكل موظف مكانه ومحطة عمله ومساره.
- **الموردون:** جهاز طلب الموردين، ويحضر عامل التوصيل البضاعة إلى صندوق التوصيل.
- **المباني الجاهزة:** يوجد مبنى جاهز لكل نوع مشروع. انقر به على الأرض فيُبنى أمامك مجهزاً ومسجلاً باسمك ويعمل. تشتريه من شركة BuildRight عند الموردين.
- **البنك:** حسابات، وإيداع وسحب، وتحويلات، وقروض، وبطاقة بنكية، وصراف آلي، وخزنة. لكل مشروع حساب بنكي منفصل.
- **المدير:** يراجع المخزون كل ساعة ويطلب ما ينقص من الموردين تلقائياً.
- **المتاجر والفندق وشركة التوصيل:** كلها تعمل مع موظفين وزبائن.
