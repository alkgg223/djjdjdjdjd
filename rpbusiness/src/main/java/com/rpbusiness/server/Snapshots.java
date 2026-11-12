package com.rpbusiness.server;

import com.rpbusiness.business.*;
import com.rpbusiness.economy.Money;
import com.rpbusiness.food.Dish;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/** Builds the data the client GUIs display. All numbers come from the real world state on the server. */
public final class Snapshots {
    private Snapshots() {}

    public static CompoundTag business(ServerPlayer p, Business b) {
        ServerLevel sl = p.serverLevel();
        CompoundTag t = new CompoundTag();
        t.putUUID("id", b.id);
        t.putString("name", b.name);
        t.putString("type", b.type.name());
        t.putString("owner", b.ownerName);
        t.putLong("cash", b.cash);
        t.putLong("playerCash", Money.countCash(p));
        t.putInt("rep", b.reputation);
        t.putInt("open", b.openHour);
        t.putInt("close", b.closeHour);
        t.putBoolean("mclosed", b.manualClosed);
        t.putBoolean("isOpen", b.isOpen(sl));
        t.putInt("hour", Business.hour(sl));
        t.putLong("day", Business.day(sl));
        t.putInt("level", b.upgradeLevel);
        t.putLong("served", b.customersServed);
        t.putLong("lost", b.customersLost);
        t.putInt("avgSat", b.customersServed > 0 ? (int) (b.totalSatisfaction / b.customersServed) : 0);
        t.put("min", NbtUtils.writeBlockPos(b.min));
        t.put("max", NbtUtils.writeBlockPos(b.max));
        if (b.entrance != null) t.put("entrance", NbtUtils.writeBlockPos(b.entrance));
        t.putInt("freeSeats", b.type.foodService ? BusinessManager.freeSeats(sl, b) : 0);

        List<Dish> avail = b.type.foodService ? BusinessManager.availableDishes(sl, b) : List.of();
        ListTag menu = new ListTag();
        for (Dish d : Dish.values()) {
            Business.MenuEntry e = b.menu.get(d);
            CompoundTag m = new CompoundTag();
            m.putString("dish", d.id());
            m.putBoolean("on", e.enabled);
            m.putInt("price", e.price);
            m.putInt("base", d.basePrice);
            m.putInt("cost", d.ingredientCost);
            m.putInt("sold", e.sold);
            m.putBoolean("avail", avail.contains(d));
            m.putString("item", BuiltInRegistries.ITEM.getKey(d.target()).toString());
            menu.add(m);
        }
        t.put("menu", menu);

        ListTag emps = new ListTag();
        int hour = Business.hour(sl);
        for (EmployeeRecord r : b.employees.values()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", r.id);
            e.putString("name", r.name);
            e.putString("job", r.job.name());
            e.putInt("salary", r.salary);
            e.putInt("skill", r.skill);
            e.putInt("xp", r.xp);
            e.putInt("rank", r.rank);
            e.putInt("start", r.startHour);
            e.putInt("end", r.endHour);
            e.putString("status", r.status == null ? "" : r.status);
            e.putInt("unpaid", r.unpaidDays);
            e.putLong("tasks", r.tasksDone);
            e.putBoolean("onShift", r.onShift(hour));
            e.putBoolean("present", BusinessManager.employee(sl, r.id) != null);
            e.putInt("route", r.route.size());
            e.putBoolean("stand", r.standPos != null);
            e.putBoolean("work", r.workPos != null);
            e.putInt("stations", r.stations.size());
            emps.add(e);
        }
        t.put("employees", emps);

        ListTag cands = new ListTag();
        for (Candidate c : b.candidates) cands.add(c.save());
        t.put("candidates", cands);

        ListTag orders = new ListTag();
        for (Order o : b.orders) {
            CompoundTag e = o.save();
            e.putLong("age", (sl.getGameTime() - o.created) / 20);
            if (o.shortage != null) e.putString("shortage", o.shortage);
            if (o.cook != null) {
                EmployeeRecord r = b.employees.get(o.cook);
                if (r != null) e.putString("cookName", r.name);
            }
            orders.add(e);
        }
        t.put("orders", orders);
        ListTag hist = new ListTag();
        int n = 0;
        for (CompoundTag h : b.history) { if (n++ >= 20) break; hist.add(h.copy()); }
        t.put("history", hist);

        // finances: last 7 days
        ListTag days = new ListTag();
        long today = Business.day(sl);
        for (long d = today; d > today - 30 && d >= 0; d--) {
            Finance.Day fd = b.finance.days.get(d);
            CompoundTag e = new CompoundTag();
            e.putLong("day", d);
            if (fd != null) {
                e.putLongArray("a", fd.amounts);
                e.putInt("c", fd.customers);
                e.putInt("l", fd.lostCustomers);
            } else e.putLongArray("a", new long[Finance.Cat.values().length]);
            days.add(e);
        }
        t.put("days", days);
        ListTag log = new ListTag();
        n = 0;
        for (Finance.Tx x : b.finance.log) {
            if (n++ >= 30) break;
            CompoundTag e = new CompoundTag();
            e.putLong("day", x.day()); e.putInt("h", x.hour()); e.putLong("amt", x.amount());
            e.putString("cat", x.cat().name()); e.putString("desc", x.desc());
            log.add(e);
        }
        t.put("log", log);
        t.putLong("totalIncome", b.finance.totalIncome);
        t.putLong("totalExpenses", b.finance.totalExpenses);

        CompoundTag eq = new CompoundTag();
        for (Map.Entry<Equip, java.util.LinkedHashSet<net.minecraft.core.BlockPos>> e : b.equipment.entrySet()) eq.putInt(e.getKey().name(), e.getValue().size());
        t.put("equip", eq);

        // banking & price control
        com.rpbusiness.bank.Bank bank = com.rpbusiness.bank.Bank.get(sl);
        com.rpbusiness.bank.Account acct = bank.businessAccount(sl, b);
        t.putString("bankId", acct.id);
        t.putLong("bankBalance", acct.balance);
        t.putInt("mult", b.priceMultiplier);
        t.putInt("discount", b.discount);
        t.putInt("deliveryRate", b.deliveryRate);
        t.putBoolean("autoBuy", b.autoBuy);
        t.putInt("autoBudget", b.autoBuyBudget);
        t.putInt("roomPrice", b.roomPrice);
        if (b.type == BusinessType.HOTEL) {
            int free = 0, dirty = 0, total = 0;
            for (var pos : BusinessManager.equip(sl, b, Equip.BED)) {
                total++;
                if (sl.getBlockState(pos).getValue(com.rpbusiness.block.HotelBedBlock.DIRTY)) dirty++;
            }
            free = BusinessManager.freeBeds(sl, b);
            t.putInt("beds", total); t.putInt("bedsFree", free); t.putInt("bedsDirty", dirty);
            BusinessManager.ensureRooms(sl, b);
            ListTag rl = new ListTag();
            int freeR = 0;
            for (com.rpbusiness.business.HotelRoom r : b.rooms) {
                CompoundTag m = new CompoundTag();
                m.putString("name", r.name);
                m.putInt("cap", BusinessManager.roomCapacity(sl, b, r));
                boolean messy = sl.isLoaded(r.min) && BusinessManager.roomDirtyAt(sl, r) != null;
                m.putBoolean("dirty", messy);
                m.putBoolean("req", r.cleanRequested);
                m.putBoolean("auto", r.auto);
                if (r.guest != null) {
                    var g = sl.getEntity(r.guest);
                    m.putString("guest", g instanceof com.rpbusiness.entity.CustomerEntity ce ? ce.npcName() + (ce.groupSize() > 1 ? " +" + (ce.groupSize() - 1) : "") : "?");
                    m.putInt("hoursLeft", (int) Math.max(0, (r.checkOutTick - sl.getDayTime()) / 1000));
                } else if (!messy) freeR++;
                rl.add(m);
            }
            t.put("roomList", rl);
            t.putInt("roomsFree", freeR);
        }
        t.putLong("debt", bank.outstanding(p.getUUID()));
        if (b.type.retail && !b.type.foodService) {
            java.util.LinkedHashMap<net.minecraft.world.item.Item, Integer> shelf = new java.util.LinkedHashMap<>();
            for (var pos : BusinessManager.equip(sl, b, Equip.DISPLAY))
                if (sl.getBlockEntity(pos) instanceof com.rpbusiness.block.DisplayBE d) {
                    for (int i = 0; i < d.getContainerSize(); i++) if (!d.getItem(i).isEmpty()) shelf.merge(d.getItem(i).getItem(), d.getItem(i).getCount(), Integer::sum);
                    for (String id : d.planogram) if (id != null) shelf.putIfAbsent(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)), 0);
                }
            Map<net.minecraft.world.item.Item, Integer> store = BusinessManager.storageCounts(sl, b);
            ListTag prods = new ListTag();
            for (var e : shelf.entrySet()) {
                CompoundTag m = new CompoundTag();
                String id = BuiltInRegistries.ITEM.getKey(e.getKey()).toString();
                m.putString("item", id);
                m.putInt("shelf", e.getValue());
                m.putInt("store", store.getOrDefault(e.getKey(), 0));
                int base = com.rpbusiness.economy.Values.base(e.getKey());
                m.putInt("base", base);
                m.putInt("price", b.prices.getOrDefault(id, base));
                m.putInt("final", BusinessManager.priceOf(b, e.getKey()));
                prods.add(m);
            }
            t.put("products", prods);
        }
        if (b.type.delivery()) {
            ListTag jobs = new ListTag();
            for (var j : b.deliveries) {
                CompoundTag e = j.save();
                if (j.kind == com.rpbusiness.delivery.DeliveryJob.Kind.TRANSFER) {
                    Business from = BusinessManager.data(sl).byId(j.fromBusiness);
                    e.putString("fromName", from == null ? "?" : from.name);
                }
                jobs.add(e);
            }
            t.put("jobs", jobs);
        }
        ListTag owned = new ListTag();
        for (Business o : BusinessManager.data(sl).businesses.values()) {
            if (!BusinessManager.isOwner(p, o)) continue;
            CompoundTag e = new CompoundTag();
            e.putUUID("id", o.id);
            e.putString("name", o.name);
            ListTag stock = new ListTag();
            BusinessManager.storageCounts(sl, o).entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(12).forEach(en -> {
                CompoundTag st = new CompoundTag();
                st.putString("item", BuiltInRegistries.ITEM.getKey(en.getKey()).toString());
                st.putInt("n", en.getValue());
                stock.add(st);
            });
            e.put("stock", stock);
            owned.add(e);
        }
        t.put("owned", owned);

        // combined overview of all the player's businesses
        ListTag all = new ListTag();
        for (Business o : BusinessManager.data(sl).businesses.values()) {
            if (!BusinessManager.isOwner(p, o)) continue;
            CompoundTag e = new CompoundTag();
            e.putString("name", o.name);
            e.putString("type", o.type.name());
            e.putLong("cash", o.cash);
            Finance.Day fd = o.finance.days.get(today);
            e.putLong("income", fd == null ? 0 : fd.income());
            e.putLong("expenses", fd == null ? 0 : fd.expenses());
            e.putInt("staff", o.employees.size());
            e.putInt("rep", o.reputation);
            e.putUUID("id", o.id);
            e.putInt("mult", o.priceMultiplier);
            e.putLong("bank", bank.businessAccount(sl, o).balance);
            all.add(e);
        }
        t.put("all", all);
        return t;
    }

    public static CompoundTag supplier(ServerPlayer p, Business b) {
        ServerLevel sl = p.serverLevel();
        CompoundTag t = new CompoundTag();
        t.putUUID("id", b.id);
        t.putString("name", b.name);
        t.putLong("cash", b.cash);
        t.putLong("playerCash", Money.countCash(p));
        t.putBoolean("hasCrate", BusinessManager.hasEquip(sl, b, Equip.CRATE));
        ListTag orders = new ListTag();
        for (int i = b.supplyOrders.size() - 1; i >= 0; i--) {
            SupplyOrder o = b.supplyOrders.get(i);
            CompoundTag e = new CompoundTag();
            e.putInt("id", o.id);
            e.putString("sup", o.supplier);
            e.putString("status", o.status.name());
            e.putLong("eta", Math.max(0, (o.eta - sl.getGameTime()) / 20));
            e.putLong("total", o.cost + o.fee);
            StringBuilder sb = new StringBuilder();
            for (ItemStack s : o.goods) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getCount()).append("x ").append(s.getHoverName().getString());
            }
            e.putString("goods", sb.toString());
            orders.add(e);
        }
        t.put("orders", orders);
        return t;
    }
}
