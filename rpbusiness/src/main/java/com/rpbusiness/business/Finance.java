package com.rpbusiness.business;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

/** Separate financial records of one business: per-day ledger and a transaction log. */
public class Finance {
    public enum Cat { FOOD_SALES, DRINK_SALES, TIPS, DEPOSIT, SALARIES, SUPPLIES, DELIVERY, HIRING, WITHDRAW, OTHER,
        PRODUCT_SALES, DELIVERY_INCOME, BANK_TRANSFER, ROOM_SALES, AUTO_PURCHASE }

    public static class Day {
        public final long[] amounts = new long[Cat.values().length];
        public int customers;
        public int lostCustomers;

        public long income() {
            return amounts[Cat.FOOD_SALES.ordinal()] + amounts[Cat.DRINK_SALES.ordinal()] + amounts[Cat.TIPS.ordinal()]
                    + amounts[Cat.PRODUCT_SALES.ordinal()] + amounts[Cat.DELIVERY_INCOME.ordinal()] + amounts[Cat.ROOM_SALES.ordinal()];
        }
        public long expenses() {
            return amounts[Cat.SALARIES.ordinal()] + amounts[Cat.SUPPLIES.ordinal()] + amounts[Cat.DELIVERY.ordinal()] + amounts[Cat.AUTO_PURCHASE.ordinal()]
                    + amounts[Cat.HIRING.ordinal()] + amounts[Cat.OTHER.ordinal()];
        }
        public long profit() { return income() - expenses(); }
    }

    public record Tx(long day, int hour, long amount, Cat cat, String desc) {}

    public final TreeMap<Long, Day> days = new TreeMap<>();
    public final Deque<Tx> log = new ArrayDeque<>();
    public long totalIncome, totalExpenses;

    public Day day(long d) {
        Day r = days.computeIfAbsent(d, k -> new Day());
        while (days.size() > 60) days.pollFirstEntry();
        return r;
    }

    /** Records a movement. amount > 0 is money into the business, < 0 out. */
    public void record(long day, int hour, Cat cat, long amount, String desc) {
        day(day).amounts[cat.ordinal()] += Math.abs(amount);
        if (cat != Cat.DEPOSIT && cat != Cat.WITHDRAW && cat != Cat.BANK_TRANSFER) {
            if (amount > 0) totalIncome += amount; else totalExpenses -= amount;
        }
        log.addFirst(new Tx(day, hour, amount, cat, desc));
        while (log.size() > 120) log.removeLast();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag dl = new ListTag();
        for (Map.Entry<Long, Day> e : days.entrySet()) {
            CompoundTag d = new CompoundTag();
            d.putLong("day", e.getKey());
            d.putLongArray("a", e.getValue().amounts);
            d.putInt("c", e.getValue().customers);
            d.putInt("l", e.getValue().lostCustomers);
            dl.add(d);
        }
        t.put("days", dl);
        ListTag ll = new ListTag();
        for (Tx x : log) {
            CompoundTag d = new CompoundTag();
            d.putLong("day", x.day()); d.putInt("h", x.hour()); d.putLong("amt", x.amount());
            d.putString("cat", x.cat().name()); d.putString("desc", x.desc());
            ll.add(d);
        }
        t.put("log", ll);
        t.putLong("ti", totalIncome);
        t.putLong("te", totalExpenses);
        return t;
    }

    public void load(CompoundTag t) {
        days.clear(); log.clear();
        ListTag dl = t.getList("days", Tag.TAG_COMPOUND);
        for (int i = 0; i < dl.size(); i++) {
            CompoundTag d = dl.getCompound(i);
            Day day = new Day();
            long[] a = d.getLongArray("a");
            System.arraycopy(a, 0, day.amounts, 0, Math.min(a.length, day.amounts.length));
            day.customers = d.getInt("c");
            day.lostCustomers = d.getInt("l");
            days.put(d.getLong("day"), day);
        }
        ListTag ll = t.getList("log", Tag.TAG_COMPOUND);
        for (int i = 0; i < ll.size(); i++) {
            CompoundTag d = ll.getCompound(i);
            Cat c;
            try { c = Cat.valueOf(d.getString("cat")); } catch (Exception e) { c = Cat.OTHER; }
            log.addLast(new Tx(d.getLong("day"), d.getInt("h"), d.getLong("amt"), c, d.getString("desc")));
        }
        totalIncome = t.getLong("ti");
        totalExpenses = t.getLong("te");
    }
}
