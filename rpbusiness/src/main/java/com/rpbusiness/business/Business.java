package com.rpbusiness.business;

import com.rpbusiness.food.Dish;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** One player-built, player-owned business. Only the player can register, build and expand it. */
public class Business {
    public static class MenuEntry {
        public boolean enabled;
        public int price;
        public int sold;
        public MenuEntry(boolean enabled, int price) { this.enabled = enabled; this.price = price; }
    }

    public UUID id;
    public String name = "My Restaurant";
    public BusinessType type = BusinessType.RESTAURANT;
    public UUID owner;
    public String ownerName = "";
    public BlockPos min, max;
    public BlockPos entrance;
    public long cash;
    /** 0..1000, shown as 0..5 stars. */
    public int reputation = 300;
    public int openHour = 7, closeHour = 23;
    public boolean manualClosed;
    public int upgradeLevel = 1;
    public final EnumMap<Dish, MenuEntry> menu = new EnumMap<>(Dish.class);
    public final LinkedHashMap<UUID, EmployeeRecord> employees = new LinkedHashMap<>();
    public final List<Order> orders = new ArrayList<>();
    public final Deque<CompoundTag> history = new ArrayDeque<>();
    public final List<SupplyOrder> supplyOrders = new ArrayList<>();
    public final Finance finance = new Finance();
    public final EnumMap<Equip, LinkedHashSet<BlockPos>> equipment = new EnumMap<>(Equip.class);
    /** Functions the player explicitly assigned to placed blocks with the Business Layout Tool. */
    public final LinkedHashMap<BlockPos, Equip> designations = new LinkedHashMap<>();
    /** Blocks the player marked as "not for business use". */
    public final LinkedHashSet<BlockPos> excluded = new LinkedHashSet<>();
    /** Player-designated tables (any block) that currently need cleaning. */
    public final LinkedHashSet<BlockPos> dirty = new LinkedHashSet<>();
    public long customersServed, customersLost, totalSatisfaction;
    public List<Candidate> candidates = new ArrayList<>();
    public long candidateDay = -1;
    public long lastSalaryDay = -1;

    // -------- transient runtime state --------
    public final transient Map<BlockPos, UUID> seatClaims = new HashMap<>();
    public final transient Map<BlockPos, List<UUID>> payQueues = new HashMap<>();
    public transient Map<Item, Integer> stock = new HashMap<>();
    public transient long stockTick = -1000;
    public transient long lastShortageMsg = -100000;
    public transient long lastQueueMsg = -100000;

    public Business() {
        for (Equip e : Equip.values()) equipment.put(e, new LinkedHashSet<>());
        for (Dish d : Dish.values()) menu.put(d, new MenuEntry(false, d.basePrice));
    }

    public boolean contains(BlockPos p) {
        return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }
    public boolean contains(Vec3 v) { return bounds().inflate(0.5).contains(v); }
    public AABB bounds() { return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1); }
    public BlockPos center() { return new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2); }
    public BlockPos entranceOrCenter() { return entrance != null ? entrance : center(); }

    public static int hour(Level level) { return (int) (((level.getDayTime() + 6000L) / 1000L) % 24L); }
    public static long day(Level level) { return (level.getDayTime() + 6000L) / 24000L; }

    public boolean isOpen(Level level) {
        if (manualClosed) return false;
        int h = hour(level);
        if (openHour == closeHour) return true;
        if (openHour < closeHour) return h >= openHour && h < closeHour;
        return h >= openHour || h < closeHour;
    }

    public float stars() { return reputation / 200f; }

    public List<Dish> enabledDishes() {
        List<Dish> l = new ArrayList<>();
        for (var e : menu.entrySet()) if (e.getValue().enabled) l.add(e.getKey());
        return l;
    }

    public Order order(int id) {
        for (Order o : orders) if (o.id == id) return o;
        return null;
    }

    public List<Order> ordersOf(UUID customer) {
        List<Order> l = new ArrayList<>();
        for (Order o : orders) if (o.customer.equals(customer) && o.active()) l.add(o);
        return l;
    }

    public void archive(Order o, long gameTime) {
        orders.remove(o);
        CompoundTag t = o.save();
        t.putLong("done", gameTime);
        history.addFirst(t);
        while (history.size() > 40) history.removeLast();
    }

    // -------------------------------------------------------------- save / load
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("type", type.name());
        if (owner != null) t.putUUID("owner", owner);
        t.putString("ownerName", ownerName);
        t.put("min", NbtUtils.writeBlockPos(min));
        t.put("max", NbtUtils.writeBlockPos(max));
        if (entrance != null) t.put("entrance", NbtUtils.writeBlockPos(entrance));
        t.putLong("cash", cash);
        t.putInt("rep", reputation);
        t.putInt("open", openHour);
        t.putInt("close", closeHour);
        t.putBoolean("mclosed", manualClosed);
        t.putInt("level", upgradeLevel);
        CompoundTag m = new CompoundTag();
        for (var e : menu.entrySet()) {
            CompoundTag d = new CompoundTag();
            d.putBoolean("on", e.getValue().enabled);
            d.putInt("price", e.getValue().price);
            d.putInt("sold", e.getValue().sold);
            m.put(e.getKey().id(), d);
        }
        t.put("menu", m);
        ListTag el = new ListTag();
        for (EmployeeRecord e : employees.values()) el.add(e.save());
        t.put("employees", el);
        ListTag ol = new ListTag();
        for (Order o : orders) ol.add(o.save());
        t.put("orders", ol);
        ListTag hl = new ListTag();
        hl.addAll(history);
        t.put("history", hl);
        ListTag sl = new ListTag();
        for (SupplyOrder s : supplyOrders) sl.add(s.save());
        t.put("supply", sl);
        t.put("finance", finance.save());
        CompoundTag eq = new CompoundTag();
        for (var e : equipment.entrySet()) {
            ListTag l = new ListTag();
            for (BlockPos p : e.getValue()) l.add(NbtUtils.writeBlockPos(p));
            eq.put(e.getKey().name(), l);
        }
        t.put("equip", eq);
        ListTag dl = new ListTag();
        for (var e : designations.entrySet()) {
            CompoundTag d = new CompoundTag();
            d.put("p", NbtUtils.writeBlockPos(e.getKey()));
            d.putString("e", e.getValue().name());
            dl.add(d);
        }
        t.put("designations", dl);
        ListTag xl = new ListTag();
        for (BlockPos p : excluded) xl.add(NbtUtils.writeBlockPos(p));
        t.put("excluded", xl);
        ListTag dirt = new ListTag();
        for (BlockPos p : dirty) dirt.add(NbtUtils.writeBlockPos(p));
        t.put("dirty", dirt);
        t.putLong("served", customersServed);
        t.putLong("lost", customersLost);
        t.putLong("satis", totalSatisfaction);
        ListTag cl = new ListTag();
        for (Candidate c : candidates) cl.add(c.save());
        t.put("candidates", cl);
        t.putLong("candDay", candidateDay);
        t.putLong("salaryDay", lastSalaryDay);
        return t;
    }

    public static Business load(CompoundTag t) {
        Business b = new Business();
        b.id = t.getUUID("id");
        b.name = t.getString("name");
        b.type = BusinessType.byName(t.getString("type"));
        if (t.hasUUID("owner")) b.owner = t.getUUID("owner");
        b.ownerName = t.getString("ownerName");
        b.min = NbtUtils.readBlockPos(t, "min").orElse(BlockPos.ZERO);
        b.max = NbtUtils.readBlockPos(t, "max").orElse(BlockPos.ZERO);
        b.entrance = NbtUtils.readBlockPos(t, "entrance").orElse(null);
        b.cash = t.getLong("cash");
        b.reputation = t.getInt("rep");
        b.openHour = t.getInt("open");
        b.closeHour = t.getInt("close");
        b.manualClosed = t.getBoolean("mclosed");
        b.upgradeLevel = Math.max(1, t.getInt("level"));
        CompoundTag m = t.getCompound("menu");
        for (Dish d : Dish.values()) {
            if (m.contains(d.id())) {
                CompoundTag e = m.getCompound(d.id());
                MenuEntry me = new MenuEntry(e.getBoolean("on"), e.getInt("price"));
                me.sold = e.getInt("sold");
                b.menu.put(d, me);
            }
        }
        ListTag el = t.getList("employees", Tag.TAG_COMPOUND);
        for (int i = 0; i < el.size(); i++) {
            EmployeeRecord e = EmployeeRecord.load(el.getCompound(i));
            b.employees.put(e.id, e);
        }
        ListTag ol = t.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < ol.size(); i++) b.orders.add(Order.load(ol.getCompound(i)));
        ListTag hl = t.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) b.history.addLast(hl.getCompound(i));
        ListTag sl = t.getList("supply", Tag.TAG_COMPOUND);
        for (int i = 0; i < sl.size(); i++) b.supplyOrders.add(SupplyOrder.load(sl.getCompound(i)));
        b.finance.load(t.getCompound("finance"));
        CompoundTag eq = t.getCompound("equip");
        for (Equip e : Equip.values()) {
            ListTag l = eq.getList(e.name(), Tag.TAG_INT_ARRAY);
            for (int i = 0; i < l.size(); i++) {
                int[] a = l.getIntArray(i);
                if (a.length == 3) b.equipment.get(e).add(new BlockPos(a[0], a[1], a[2]));
            }
        }
        ListTag dl = t.getList("designations", Tag.TAG_COMPOUND);
        for (int i = 0; i < dl.size(); i++) {
            CompoundTag d = dl.getCompound(i);
            try {
                BlockPos p = NbtUtils.readBlockPos(d, "p").orElse(null);
                if (p != null) b.designations.put(p, Equip.valueOf(d.getString("e")));
            } catch (Exception ignored) {}
        }
        for (Tag x : t.getList("excluded", Tag.TAG_INT_ARRAY)) { int[] a = ((IntArrayTag) x).getAsIntArray(); if (a.length == 3) b.excluded.add(new BlockPos(a[0], a[1], a[2])); }
        for (Tag x : t.getList("dirty", Tag.TAG_INT_ARRAY)) { int[] a = ((IntArrayTag) x).getAsIntArray(); if (a.length == 3) b.dirty.add(new BlockPos(a[0], a[1], a[2])); }
        b.customersServed = t.getLong("served");
        b.customersLost = t.getLong("lost");
        b.totalSatisfaction = t.getLong("satis");
        ListTag cl = t.getList("candidates", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) b.candidates.add(Candidate.load(cl.getCompound(i)));
        b.candidateDay = t.getLong("candDay");
        b.lastSalaryDay = t.getLong("salaryDay");
        return b;
    }
}
