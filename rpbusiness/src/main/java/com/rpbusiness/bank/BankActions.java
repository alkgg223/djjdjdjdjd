package com.rpbusiness.bank;

import com.rpbusiness.business.Business;
import com.rpbusiness.business.BusinessData;
import com.rpbusiness.core.ModComponents;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.economy.Money;
import com.rpbusiness.network.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Bank counter / ATM button presses, executed on the server with ownership checks. */
public final class BankActions {
    private BankActions() {}

    private static void msg(ServerPlayer p, String key, ChatFormatting c, Object... args) {
        p.displayClientMessage(Component.translatable(key, args).withStyle(c), false);
    }

    public static void handle(ServerPlayer p, String action, CompoundTag d) {
        ServerLevel sl = p.serverLevel();
        Bank bank = Bank.get(sl);
        Account acc = bank.account(d.getString("acc"));
        boolean atm = d.getBoolean("atm");
        switch (action) {
            case "bank_open" -> {
                Account.Type t = d.getString("type").equals("SAVINGS") ? Account.Type.SAVINGS : Account.Type.CHECKING;
                if (bank.accountsOf(p.getUUID()).size() >= 12) { msg(p, "msg.rpbusiness.bank_too_many", ChatFormatting.RED); break; }
                Account a = bank.open(sl, p.getUUID(), p.getGameProfile().getName(), t, t == Account.Type.SAVINGS ? "Savings" : "Checking", null);
                msg(p, "msg.rpbusiness.bank_opened", ChatFormatting.GREEN, a.id);
            }
            case "bank_deposit" -> {
                long amt = d.getLong("amount");
                if (amt < 0) amt = Money.countCash(p);
                if (!bank.depositCash(p, acc, amt)) msg(p, "msg.rpbusiness.bank_failed", ChatFormatting.RED);
            }
            case "bank_withdraw" -> {
                if (!bank.withdrawCash(p, acc, d.getLong("amount"))) msg(p, "msg.rpbusiness.bank_insufficient", ChatFormatting.RED);
            }
            case "bank_transfer" -> {
                Account to = bank.account(d.getString("to").trim().toUpperCase());
                long amt = d.getLong("amount");
                if (to == null) msg(p, "msg.rpbusiness.bank_no_account", ChatFormatting.RED, d.getString("to"));
                else if (!bank.canUse(p, acc) || !bank.transfer(sl, acc, to, amt, d.getString("memo"))) msg(p, "msg.rpbusiness.bank_insufficient", ChatFormatting.RED);
                else {
                    msg(p, "msg.rpbusiness.bank_transferred", ChatFormatting.GREEN, Money.fmt(amt), to.id);
                    syncBusinessCashRecord(sl, acc, to, amt);
                }
            }
            case "bank_loan" -> {
                if (!bank.canUse(p, acc)) break;
                Loan l = bank.takeLoan(p, acc.id, d.getLong("amount"), d.getInt("term"));
                if (l == null) msg(p, "msg.rpbusiness.loan_denied", ChatFormatting.RED, Money.fmt(bank.maxLoan(p.getUUID())));
                else msg(p, "msg.rpbusiness.loan_approved", ChatFormatting.GREEN, l.id, Money.fmt(l.principal), acc.id, Money.fmt(l.totalDue), l.termDays, Money.fmt(l.installment));
            }
            case "bank_loan_pay" -> {
                Loan l = bank.loan(d.getInt("loan"));
                long amt = d.getLong("amount");
                if (l != null && amt < 0) amt = l.remaining;
                if (l == null || !bank.payLoan(p, l, amt)) msg(p, "msg.rpbusiness.bank_insufficient", ChatFormatting.RED);
                else msg(p, "msg.rpbusiness.loan_paid", ChatFormatting.GREEN, l.id, Money.fmt(l.remaining));
            }
            case "bank_card" -> {
                if (!bank.canUse(p, acc)) break;
                if (!bank.debit(sl, acc, 5, "Card issue fee")) { msg(p, "msg.rpbusiness.bank_insufficient", ChatFormatting.RED); break; }
                ItemStack card = new ItemStack(ModItems.BANK_CARD.get());
                card.set(ModComponents.CARD_ACCOUNT.get(), acc.id);
                card.set(ModComponents.CARD_HOLDER.get(), p.getGameProfile().getName());
                if (!p.getInventory().add(card)) p.drop(card, false);
                msg(p, "msg.rpbusiness.card_issued", ChatFormatting.GREEN, acc.id);
            }
            default -> { return; }
        }
        bank.setDirty();
        if (atm) Net.openAtm(p, acc == null ? null : acc.id);
        else Net.openBank(p, d.getString("acc"), d.getBoolean("manager"));
    }

    /** Transfers into / out of a business account are also noted in that business' financial records. */
    private static void syncBusinessCashRecord(ServerLevel sl, Account from, Account to, long amt) {
        for (Account a : new Account[]{from, to}) {
            if (a.business == null) continue;
            Business b = BusinessData.get(sl).byId(a.business);
            if (b != null) b.finance.record(Business.day(sl), Business.hour(sl), com.rpbusiness.business.Finance.Cat.BANK_TRANSFER,
                    a == to ? amt : -amt, (a == to ? "From " + from.id : "To " + to.id));
        }
    }

    public static CompoundTag snapshot(ServerPlayer p, String selected, boolean manager) {
        ServerLevel sl = p.serverLevel();
        Bank bank = Bank.get(sl);
        // make sure every business of the player has its account
        for (Business b : BusinessData.get(sl).businesses.values()) if (p.getUUID().equals(b.owner)) bank.businessAccount(sl, b);
        CompoundTag t = new CompoundTag();
        ListTag al = new ListTag();
        for (Account a : bank.accountsOf(p.getUUID())) al.add(accountTag(a, 30));
        t.put("accounts", al);
        t.putString("selected", selected == null ? "" : selected);
        ListTag ll = new ListTag();
        long debt = 0;
        for (Loan l : bank.loans) if (l.borrower.equals(p.getUUID())) { ll.add(l.save()); if (l.status == Loan.Status.ACTIVE) debt += l.remaining; }
        t.put("loans", ll);
        t.putInt("credit", bank.creditScore(p.getUUID()));
        t.putLong("maxLoan", bank.maxLoan(p.getUUID()));
        t.putLong("debt", debt);
        t.putLong("cash", Money.countCash(p));
        t.putLong("day", Business.day(sl));
        t.putBoolean("manager", manager);
        if (manager) {
            long dep = 0;
            for (Account a : bank.accounts.values()) dep += a.balance;
            long out = 0;
            for (Loan l : bank.loans) if (l.status == Loan.Status.ACTIVE) out += l.remaining;
            t.putLong("totalDeposits", dep);
            t.putLong("totalLoans", out);
            t.putInt("accountCount", bank.accounts.size());
        }
        return t;
    }

    public static CompoundTag accountTag(Account a, int history) {
        CompoundTag e = new CompoundTag();
        e.putString("id", a.id);
        e.putString("type", a.type.name());
        e.putString("label", a.label);
        e.putLong("balance", a.balance);
        ListTag h = new ListTag();
        int n = 0;
        for (Account.Tx x : a.history) {
            if (n++ >= history) break;
            CompoundTag tx = new CompoundTag();
            tx.putLong("d", x.day()); tx.putInt("h", x.hour()); tx.putLong("a", x.amount()); tx.putLong("b", x.balanceAfter()); tx.putString("s", x.desc());
            h.add(tx);
        }
        e.put("history", h);
        return e;
    }
}
