package com.rpbusiness.bank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** A bank account. Balances only change through {@link Bank}, which records every movement. */
public class Account {
    public enum Type { CHECKING, SAVINGS, BUSINESS }

    public record Tx(long day, int hour, long amount, long balanceAfter, String desc) {}

    public String id;
    public UUID owner;
    public String ownerName = "";
    public Type type = Type.CHECKING;
    public String label = "";
    public long balance;
    public UUID business;
    public long openedDay;
    public final Deque<Tx> history = new ArrayDeque<>();

    public void log(long day, int hour, long amount, String desc) {
        history.addFirst(new Tx(day, hour, amount, balance, desc));
        while (history.size() > 100) history.removeLast();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putUUID("owner", owner);
        t.putString("ownerName", ownerName);
        t.putString("type", type.name());
        t.putString("label", label);
        t.putLong("balance", balance);
        if (business != null) t.putUUID("business", business);
        t.putLong("opened", openedDay);
        ListTag l = new ListTag();
        for (Tx x : history) {
            CompoundTag e = new CompoundTag();
            e.putLong("d", x.day()); e.putInt("h", x.hour()); e.putLong("a", x.amount()); e.putLong("b", x.balanceAfter()); e.putString("s", x.desc());
            l.add(e);
        }
        t.put("history", l);
        return t;
    }

    public static Account load(CompoundTag t) {
        Account a = new Account();
        a.id = t.getString("id");
        a.owner = t.getUUID("owner");
        a.ownerName = t.getString("ownerName");
        try { a.type = Type.valueOf(t.getString("type")); } catch (Exception e) { a.type = Type.CHECKING; }
        a.label = t.getString("label");
        a.balance = t.getLong("balance");
        if (t.hasUUID("business")) a.business = t.getUUID("business");
        a.openedDay = t.getLong("opened");
        ListTag l = t.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            a.history.addLast(new Tx(e.getLong("d"), e.getInt("h"), e.getLong("a"), e.getLong("b"), e.getString("s")));
        }
        return a;
    }
}
