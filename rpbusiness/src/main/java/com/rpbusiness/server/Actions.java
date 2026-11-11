package com.rpbusiness.server;

import com.rpbusiness.business.*;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.economy.Money;
import com.rpbusiness.economy.Suppliers;
import com.rpbusiness.food.Dish;
import com.rpbusiness.network.Net;
import com.rpbusiness.business.Finance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executes GUI button presses on the server, with ownership and money checks on every action. */
public final class Actions {
    private Actions() {}

    public static void handle(ServerPlayer p, String action, CompoundTag d) {
        ServerLevel sl = p.serverLevel();
        BusinessData data = BusinessManager.data(sl);
        if (action.equals("register")) { register(p, d); return; }
        Business b = d.hasUUID("biz") ? data.byId(d.getUUID("biz")) : null;
        if (b == null || !BusinessManager.isOwner(p, b)) return;
        String tab = d.getString("tab");
        boolean refresh = true;
        switch (action) {
            case "refresh" -> {}
            case "open_supplier" -> { Net.openSupplier(p, b); refresh = false; }
            case "to_bank" -> {
                long amt = d.getLong("amount") < 0 ? b.cash : Math.min(b.cash, d.getLong("amount"));
                if (amt > 0) {
                    var bank = com.rpbusiness.bank.Bank.get(sl);
                    b.cash -= amt;
                    bank.credit(sl, bank.businessAccount(sl, b), amt, "Cash drawer deposit");
                    b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.BANK_TRANSFER, -amt, "Drawer -> bank");
                }
            }
            case "from_bank" -> {
                var bank = com.rpbusiness.bank.Bank.get(sl);
                long amt = d.getLong("amount");
                if (bank.debit(sl, bank.businessAccount(sl, b), amt, "To cash drawer")) {
                    b.cash += amt;
                    b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.BANK_TRANSFER, amt, "Bank -> drawer");
                } else p.displayClientMessage(Component.translatable("msg.rpbusiness.bank_insufficient").withStyle(ChatFormatting.RED), false);
            }
            case "product_price" -> {
                String id = d.getString("item");
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
                int cur = b.prices.getOrDefault(id, com.rpbusiness.economy.Values.base(item));
                b.prices.put(id, Math.max(1, Math.min(99999, cur + d.getInt("delta"))));
            }
            case "product_reset" -> b.prices.remove(d.getString("item"));
            case "mult" -> b.priceMultiplier = Math.max(50, Math.min(300, b.priceMultiplier + d.getInt("delta")));
            case "discount" -> b.discount = Math.max(0, Math.min(90, b.discount + d.getInt("delta")));
            case "mult_of" -> {
                Business o = data.byId(d.getUUID("other"));
                if (o != null && BusinessManager.isOwner(p, o)) o.priceMultiplier = Math.max(50, Math.min(300, o.priceMultiplier + d.getInt("delta")));
            }
            case "all_mult" -> {
                for (Business o : data.businesses.values())
                    if (BusinessManager.isOwner(p, o)) o.priceMultiplier = Math.max(50, Math.min(300, o.priceMultiplier + d.getInt("delta")));
            }
            case "delivery_rate" -> b.deliveryRate = Math.max(30, Math.min(300, b.deliveryRate + d.getInt("delta")));
            case "accept_job" -> {
                if (!com.rpbusiness.delivery.Deliveries.accept(p, b, d.getInt("job")))
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.job_failed").withStyle(ChatFormatting.RED), false);
            }
            case "cancel_job" -> {
                var j = com.rpbusiness.delivery.Deliveries.job(b, d.getInt("job"));
                if (j != null && (j.status == com.rpbusiness.delivery.DeliveryJob.Status.OFFERED || j.status == com.rpbusiness.delivery.DeliveryJob.Status.AWAITING_PICKUP) && j.driver == null)
                    j.status = com.rpbusiness.delivery.DeliveryJob.Status.CANCELLED;
            }
            case "create_transfer" -> {
                Business from = data.byId(d.getUUID("from")), to = data.byId(d.getUUID("to"));
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(d.getString("item")));
                var j = com.rpbusiness.delivery.Deliveries.createTransfer(p, b, from, to, item, d.getInt("count"));
                if (j == null) p.displayClientMessage(Component.translatable("msg.rpbusiness.job_failed").withStyle(ChatFormatting.RED), false);
                else p.displayClientMessage(Component.translatable("msg.rpbusiness.transfer_created", j.id, Money.fmt(j.reward)).withStyle(ChatFormatting.GREEN), false);
            }
            case "rename" -> {
                String n = d.getString("name").trim();
                if (!n.isEmpty()) b.name = n.length() > 32 ? n.substring(0, 32) : n;
            }
            case "set_type" -> b.type = BusinessType.byName(d.getString("type"));
            case "toggle_open" -> b.manualClosed = !b.manualClosed;
            case "hours" -> {
                b.openHour = Math.floorMod(b.openHour + d.getInt("open"), 24);
                b.closeHour = Math.floorMod(b.closeHour + d.getInt("close"), 24);
            }
            case "menu_toggle" -> {
                Dish dish = Dish.byId(d.getString("dish"));
                if (dish != null) b.menu.get(dish).enabled = !b.menu.get(dish).enabled;
            }
            case "menu_price" -> {
                Dish dish = Dish.byId(d.getString("dish"));
                if (dish != null) b.menu.get(dish).price = Math.max(1, Math.min(999, b.menu.get(dish).price + d.getInt("delta")));
            }
            case "menu_reset" -> { for (Dish dish : Dish.values()) b.menu.get(dish).price = dish.basePrice; }
            case "withdraw" -> {
                long amt = d.getLong("amount");
                if (amt < 0) amt = b.cash;
                amt = Math.min(amt, b.cash);
                if (amt > 0) {
                    b.cash -= amt;
                    Money.giveCash(p, amt);
                    b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.WITHDRAW, -amt, p.getGameProfile().getName());
                }
            }
            case "deposit" -> {
                long amt = d.getLong("amount");
                long have = Money.countCash(p);
                if (amt < 0) amt = have;
                amt = Math.min(amt, have);
                if (amt > 0 && Money.takeCash(p, amt)) {
                    b.cash += amt;
                    b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.DEPOSIT, amt, p.getGameProfile().getName());
                }
            }
            case "hire" -> BusinessManager.hire(p, b, d.getInt("index"), Job.byName(d.getString("job")));
            case "new_candidates" -> {
                if (b.cash >= 20) {
                    b.cash -= 20;
                    b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.HIRING, -20, "Job advert");
                    BusinessManager.refreshCandidates(sl, b, true);
                } else p.displayClientMessage(Component.translatable("msg.rpbusiness.not_enough_business_cash", Money.fmt(20)).withStyle(ChatFormatting.RED), false);
            }
            case "fire" -> BusinessManager.fire(sl, b, d.getUUID("emp"));
            case "emp_job" -> {
                EmployeeRecord r = b.employees.get(d.getUUID("emp"));
                if (r != null) {
                    r.job = r.job.nextFor(b.type);
                    r.salary = BusinessManager.salaryFor(r.job, r.skill, r.rank);
                }
            }
            case "emp_salary" -> {
                EmployeeRecord r = b.employees.get(d.getUUID("emp"));
                if (r != null) r.salary = Math.max(1, Math.min(9999, r.salary + d.getInt("delta")));
            }
            case "emp_promote" -> {
                EmployeeRecord r = b.employees.get(d.getUUID("emp"));
                if (r != null && r.rank < 2) {
                    r.rank++;
                    r.salary = Math.max(r.salary, BusinessManager.salaryFor(r.job, r.skill, r.rank));
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.promoted", r.name, Component.translatable(EmployeeRecord.RANKS[r.rank])).withStyle(ChatFormatting.GREEN), false);
                }
            }
            case "emp_hours" -> {
                EmployeeRecord r = b.employees.get(d.getUUID("emp"));
                if (r != null) {
                    r.startHour = Math.floorMod(r.startHour + d.getInt("start"), 24);
                    r.endHour = Math.floorMod(r.endHour + d.getInt("end"), 24);
                }
            }
            case "emp_pay" -> {
                if (!BusinessManager.payOverdue(sl, b, d.getUUID("emp")))
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.not_enough_business_cash", "").withStyle(ChatFormatting.RED), false);
            }
            case "emp_clear" -> {
                EmployeeRecord r = b.employees.get(d.getUUID("emp"));
                if (r != null) { r.standPos = r.workPos = r.servicePos = r.storagePos = null; r.route.clear(); }
            }
            case "cancel_order" -> {
                Order o = b.order(d.getInt("order"));
                if (o != null && o.active()) { o.status = Order.Status.CANCELLED; b.archive(o, sl.getGameTime()); }
            }
            case "rescan" -> {
                BusinessManager.scan(sl, b);
                BlockPos e = BusinessManager.findEntrance(sl, b);
                if (e != null) b.entrance = e;
                p.displayClientMessage(Component.translatable("msg.rpbusiness.rescanned").withStyle(ChatFormatting.GREEN), true);
            }
            case "entrance_here" -> {
                b.entrance = p.blockPosition();
                p.displayClientMessage(Component.translatable("msg.rpbusiness.entrance_set", b.entrance.toShortString()).withStyle(ChatFormatting.GREEN), false);
            }
            case "upgrade" -> {
                long cost = 400L * b.upgradeLevel;
                if (b.upgradeLevel >= 5) break;
                if (b.cash >= cost) {
                    b.cash -= cost;
                    b.upgradeLevel++;
                    b.reputation = Math.min(1000, b.reputation + 40);
                    b.finance.record(Business.day(sl), Business.hour(sl), Finance.Cat.OTHER, -cost, "Upgrade to level " + b.upgradeLevel);
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.upgraded", b.upgradeLevel).withStyle(ChatFormatting.GREEN), false);
                } else p.displayClientMessage(Component.translatable("msg.rpbusiness.not_enough_business_cash", Money.fmt(cost)).withStyle(ChatFormatting.RED), false);
            }
            case "delete" -> {
                BusinessManager.deleteBusiness(sl, b);
                p.displayClientMessage(Component.translatable("msg.rpbusiness.deleted", b.name).withStyle(ChatFormatting.YELLOW), false);
                p.closeContainer();
                refresh = false;
            }
            case "supply_order" -> {
                List<ItemStack> goods = new ArrayList<>();
                long cost = 0;
                ListTag l = d.getList("cart", Tag.TAG_COMPOUND);
                String sup = "";
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag c = l.getCompound(i);
                    int ci = c.getInt("c"), pi = c.getInt("p"), packs = Math.min(64, c.getInt("n"));
                    if (ci < 0 || ci >= Suppliers.ALL.size() || packs <= 0) continue;
                    Suppliers.Company comp = Suppliers.ALL.get(ci);
                    if (pi < 0 || pi >= comp.products().size()) continue;
                    Suppliers.Product pr = comp.products().get(pi);
                    cost += (long) pr.price() * packs;
                    int total = pr.pack() * packs;
                    while (total > 0) {
                        int c2 = Math.min(64, total);
                        goods.add(new ItemStack(pr.item().get(), c2));
                        total -= c2;
                    }
                    sup = sup.isEmpty() ? comp.name() : (sup.equals(comp.name()) ? sup : "Multiple suppliers");
                }
                if (goods.size() > 27) {
                    p.displayClientMessage(Component.translatable("msg.rpbusiness.order_too_big").withStyle(ChatFormatting.RED), false);
                } else BusinessManager.placeSupplyOrder(p, b, goods, sup, cost, d.contains("payMode") ? d.getInt("payMode") : (d.getBoolean("payCash") ? 1 : 0));
                Net.openSupplier(p, b);
                refresh = false;
            }
            default -> refresh = false;
        }
        data.setDirty();
        if (refresh) Net.openBusiness(p, b, tab.isEmpty() ? "overview" : tab);
    }

    private static void register(ServerPlayer p, CompoundTag d) {
        ItemStack planner = Net.findPlanner(p);
        if (planner == null || !planner.has(ModComponents.CORNER_B.get())) {
            p.displayClientMessage(Component.translatable("msg.rpbusiness.need_planner").withStyle(ChatFormatting.RED), false);
            return;
        }
        BlockPos a = planner.get(ModComponents.CORNER_A.get()), c = planner.get(ModComponents.CORNER_B.get());
        Business b = BusinessManager.register(p, a, c, d.getString("name"), BusinessType.byName(d.getString("type")));
        if (b != null) {
            planner.remove(ModComponents.CORNER_A.get());
            planner.remove(ModComponents.CORNER_B.get());
            Net.openBusiness(p, b, "overview");
        }
    }

    @SuppressWarnings("unused")
    private static UUID uuid(CompoundTag d, String k) { return d.hasUUID(k) ? d.getUUID(k) : null; }
}
