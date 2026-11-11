package com.rpbusiness.bank;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** A loan the player explicitly applied for and confirmed. All terms are shown before confirmation. */
public class Loan {
    public enum Status { ACTIVE, PAID, DEFAULTED }

    public int id;
    public UUID borrower;
    public String account;
    public long principal;
    /** Total interest in percent of the principal over the whole term. */
    public int interestPercent;
    public long totalDue;
    public long remaining;
    public long installment;
    public int termDays;
    public long startDay;
    public long nextDueDay;
    public int paidInstallments;
    public int missed;
    public Status status = Status.ACTIVE;

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id); t.putUUID("borrower", borrower); t.putString("account", account);
        t.putLong("principal", principal); t.putInt("rate", interestPercent); t.putLong("total", totalDue);
        t.putLong("remaining", remaining); t.putLong("inst", installment); t.putInt("term", termDays);
        t.putLong("start", startDay); t.putLong("next", nextDueDay); t.putInt("paid", paidInstallments);
        t.putInt("missed", missed); t.putString("status", status.name());
        return t;
    }

    public static Loan load(CompoundTag t) {
        Loan l = new Loan();
        l.id = t.getInt("id"); l.borrower = t.getUUID("borrower"); l.account = t.getString("account");
        l.principal = t.getLong("principal"); l.interestPercent = t.getInt("rate"); l.totalDue = t.getLong("total");
        l.remaining = t.getLong("remaining"); l.installment = t.getLong("inst"); l.termDays = t.getInt("term");
        l.startDay = t.getLong("start"); l.nextDueDay = t.getLong("next"); l.paidInstallments = t.getInt("paid");
        l.missed = t.getInt("missed");
        try { l.status = Status.valueOf(t.getString("status")); } catch (Exception e) { l.status = Status.ACTIVE; }
        return l;
    }
}
