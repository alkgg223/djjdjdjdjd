package com.rpbusiness.bank;

import com.rpbusiness.business.Business;
import com.rpbusiness.business.BusinessData;
import com.rpbusiness.economy.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * The world's bank: accounts, transaction histories, credit scores and loans, saved in the world
 * (data/rpbusiness_bank.dat). Every balance change goes through this class and is recorded;
 * cash is removed from the player before it is credited, so money cannot be duplicated.
 */
public class Bank extends SavedData {
    public final LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();
    public final List<Loan> loans = new ArrayList<>();
    public final Map<UUID, Integer> credit = new HashMap<>();
    public int nextAccount = 100001, nextLoan = 1;
    public long lastDay = -1;

    public static final int[] TERMS = {5, 10, 20, 30};
    public static final int[] BASE_RATES = {6, 10, 16, 22};

    public static Bank get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Bank::new, Bank::load), "rpbusiness_bank");
    }
    public static Bank get(ServerLevel sl) { return get(sl.getServer()); }

    private static long day(ServerLevel sl) { return Business.day(sl); }
    private static int hour(ServerLevel sl) { return Business.hour(sl); }

    // ------------------------------------------------------------------ accounts
    public Account open(ServerLevel sl, UUID owner, String ownerName, Account.Type type, String label, UUID business) {
        Account a = new Account();
        a.id = "RPB-" + (nextAccount++);
        a.owner = owner;
        a.ownerName = ownerName;
        a.type = type;
        a.label = label;
        a.business = business;
        a.openedDay = day(sl);
        a.log(day(sl), hour(sl), 0, "Account opened");
        accounts.put(a.id, a);
        setDirty();
        return a;
    }

    public List<Account> accountsOf(UUID owner) {
        List<Account> l = new ArrayList<>();
        for (Account a : accounts.values()) if (a.owner.equals(owner)) l.add(a);
        return l;
    }

    public Account account(String id) { return id == null ? null : accounts.get(id); }

    /** The separate bank account of a business (created on first use). */
    public Account businessAccount(ServerLevel sl, Business b) {
        if (b.bankAccount != null && accounts.containsKey(b.bankAccount)) return accounts.get(b.bankAccount);
        Account a = open(sl, b.owner, b.ownerName, Account.Type.BUSINESS, b.name, b.id);
        b.bankAccount = a.id;
        BusinessData.get(sl).setDirty();
        return a;
    }

    public boolean canUse(ServerPlayer p, Account a) { return a != null && a.owner.equals(p.getUUID()); }

    public void credit(ServerLevel sl, Account a, long amount, String desc) {
        if (amount <= 0) return;
        a.balance += amount;
        a.log(day(sl), hour(sl), amount, desc);
        setDirty();
    }

    public boolean debit(ServerLevel sl, Account a, long amount, String desc) {
        if (amount <= 0 || a.balance < amount) return false;
        a.balance -= amount;
        a.log(day(sl), hour(sl), -amount, desc);
        setDirty();
        return true;
    }

    public boolean transfer(ServerLevel sl, Account from, Account to, long amount, String desc) {
        if (from == null || to == null || from == to || amount <= 0 || from.balance < amount) return false;
        from.balance -= amount;
        from.log(day(sl), hour(sl), -amount, "To " + to.id + (desc.isEmpty() ? "" : " - " + desc));
        to.balance += amount;
        to.log(day(sl), hour(sl), amount, "From " + from.id + (desc.isEmpty() ? "" : " - " + desc));
        setDirty();
        return true;
    }

    /** Physical cash from the player's inventory into an account. */
    public boolean depositCash(ServerPlayer p, Account a, long amount) {
        if (!canUse(p, a) || amount <= 0) return false;
        if (!Money.takeCash(p, amount)) return false;
        credit(p.serverLevel(), a, amount, "Cash deposit");
        return true;
    }

    public boolean withdrawCash(ServerPlayer p, Account a, long amount) {
        if (!canUse(p, a) || amount <= 0) return false;
        if (!debit(p.serverLevel(), a, amount, "Cash withdrawal")) return false;
        Money.giveCash(p, amount);
        return true;
    }

    // ------------------------------------------------------------------ credit & loans
    public int creditScore(UUID who) { return credit.getOrDefault(who, 650); }

    private void adjustCredit(UUID who, int d) { credit.put(who, Math.max(300, Math.min(850, creditScore(who) + d))); setDirty(); }

    public long outstanding(UUID who) {
        long s = 0;
        for (Loan l : loans) if (l.borrower.equals(who) && l.status == Loan.Status.ACTIVE) s += l.remaining;
        return s;
    }

    public long maxLoan(UUID who) {
        long assets = 0;
        for (Account a : accountsOf(who)) assets += a.balance;
        long limit = 1000 + (creditScore(who) - 300) * 30L + assets / 2 - outstanding(who);
        return Math.max(0, Math.min(100000, limit / 100 * 100));
    }

    public int rateFor(UUID who, int termIndex) {
        int base = BASE_RATES[Math.max(0, Math.min(TERMS.length - 1, termIndex))];
        return Math.max(2, base + (650 - creditScore(who)) / 25);
    }

    /** Builds the exact offer shown to the player before they confirm. */
    public Loan quote(UUID who, String account, long amount, int termIndex, long today) {
        Loan l = new Loan();
        l.borrower = who;
        l.account = account;
        l.principal = amount;
        l.termDays = TERMS[Math.max(0, Math.min(TERMS.length - 1, termIndex))];
        l.interestPercent = rateFor(who, termIndex);
        l.totalDue = amount + amount * l.interestPercent / 100;
        l.installment = (l.totalDue + l.termDays - 1) / l.termDays;
        l.remaining = l.totalDue;
        l.startDay = today;
        l.nextDueDay = today + 1;
        return l;
    }

    public Loan takeLoan(ServerPlayer p, String accountId, long amount, int termIndex) {
        if (!canUse(p, account(accountId))) return null;
        return takeLoan(p.serverLevel(), p.getUUID(), accountId, amount, termIndex);
    }

    public Loan takeLoan(ServerLevel sl, UUID who, String accountId, long amount, int termIndex) {
        Account a = account(accountId);
        if (a == null || !a.owner.equals(who) || amount < 100 || amount > maxLoan(who)) return null;
        Loan l = quote(who, accountId, amount, termIndex, day(sl));
        l.id = nextLoan++;
        loans.add(l);
        credit(sl, a, amount, "Loan #" + l.id + " paid out");
        setDirty();
        return l;
    }

    public boolean payLoan(ServerPlayer p, Loan l, long amount) {
        if (l == null || l.status != Loan.Status.ACTIVE || !l.borrower.equals(p.getUUID())) return false;
        Account a = account(l.account);
        if (a == null) return false;
        amount = Math.min(amount, l.remaining);
        if (!debit(p.serverLevel(), a, amount, "Loan #" + l.id + " repayment")) return false;
        l.remaining -= amount;
        if (l.remaining <= 0) {
            l.status = Loan.Status.PAID;
            adjustCredit(l.borrower, 25);
        }
        setDirty();
        return true;
    }

    public Loan loan(int id) {
        for (Loan l : loans) if (l.id == id) return l;
        return null;
    }

    // ------------------------------------------------------------------ daily processing
    public void tickDay(ServerLevel sl) {
        long d = day(sl);
        if (lastDay < 0) { lastDay = d; setDirty(); return; }
        if (d <= lastDay) return;
        for (long day = lastDay + 1; day <= d; day++) processDay(sl, day);
        lastDay = d;
        setDirty();
    }

    private void processDay(ServerLevel sl, long day) {
        // savings interest: 0.2% per day (max $500/day)
        for (Account a : accounts.values()) {
            if (a.type == Account.Type.SAVINGS && a.balance > 0) {
                long i = Math.min(500, a.balance * 2 / 1000);
                if (i > 0) { a.balance += i; a.log(day, 0, i, "Savings interest"); }
            }
        }
        for (Loan l : loans) {
            if (l.status != Loan.Status.ACTIVE || day < l.nextDueDay) continue;
            Account a = account(l.account);
            long due = Math.min(l.installment, l.remaining);
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(l.borrower);
            if (a != null && a.balance >= due) {
                a.balance -= due;
                a.log(day, 0, -due, "Loan #" + l.id + " installment");
                l.remaining -= due;
                l.paidInstallments++;
                adjustCredit(l.borrower, 2);
                if (l.remaining <= 0) { l.status = Loan.Status.PAID; adjustCredit(l.borrower, 20); }
                if (p != null) p.displayClientMessage(Component.translatable("msg.rpbusiness.loan_installment", l.id, Money.fmt(due), Money.fmt(l.remaining)).withStyle(ChatFormatting.AQUA), false);
            } else {
                l.missed++;
                adjustCredit(l.borrower, -30);
                if (p != null) p.displayClientMessage(Component.translatable("msg.rpbusiness.loan_missed", l.id, Money.fmt(due), l.account).withStyle(ChatFormatting.RED), false);
            }
            l.nextDueDay = day + 1;
        }
    }

    // ------------------------------------------------------------------ save
    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider provider) {
        ListTag al = new ListTag();
        for (Account a : accounts.values()) al.add(a.save());
        t.put("accounts", al);
        ListTag ll = new ListTag();
        for (Loan l : loans) ll.add(l.save());
        t.put("loans", ll);
        CompoundTag c = new CompoundTag();
        for (var e : credit.entrySet()) c.putInt(e.getKey().toString(), e.getValue());
        t.put("credit", c);
        t.putInt("nextAccount", nextAccount);
        t.putInt("nextLoan", nextLoan);
        t.putLong("lastDay", lastDay);
        return t;
    }

    public static Bank load(CompoundTag t, HolderLookup.Provider provider) {
        Bank b = new Bank();
        ListTag al = t.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) { Account a = Account.load(al.getCompound(i)); b.accounts.put(a.id, a); }
        ListTag ll = t.getList("loans", Tag.TAG_COMPOUND);
        for (int i = 0; i < ll.size(); i++) b.loans.add(Loan.load(ll.getCompound(i)));
        CompoundTag c = t.getCompound("credit");
        for (String k : c.getAllKeys()) { try { b.credit.put(UUID.fromString(k), c.getInt(k)); } catch (Exception ignored) {} }
        b.nextAccount = Math.max(100001, t.getInt("nextAccount"));
        b.nextLoan = Math.max(1, t.getInt("nextLoan"));
        b.lastDay = t.contains("lastDay") ? t.getLong("lastDay") : -1;
        return b;
    }
}
