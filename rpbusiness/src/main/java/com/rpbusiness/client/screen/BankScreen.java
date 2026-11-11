package com.rpbusiness.client.screen;

import com.rpbusiness.economy.Money;
import com.rpbusiness.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Bank counter: accounts, cash deposit/withdrawal, transfers, history, loans and bank cards. */
public class BankScreen extends BaseScreen {
    private static final String[] TABS = {"accounts", "cash", "transfer", "history", "loans", "cards"};
    private static final int[] TERMS = {5, 10, 20, 30};
    private static final int[] BASE_RATES = {6, 10, 16, 22};
    private CompoundTag data;
    private String tab = "accounts";
    private int sel;
    private long amount = 100;
    private long loanAmount = 1000;
    private int term = 1;
    private int toIndex = -1;
    private EditBox toBox, memoBox;
    private boolean confirmLoan;

    public BankScreen(CompoundTag data) {
        super(Component.translatable("gui.rpbusiness.bank_title"));
        this.data = data;
        String s = data.getString("selected");
        List<CompoundTag> acc = accounts();
        for (int i = 0; i < acc.size(); i++) if (acc.get(i).getString("id").equals(s)) sel = i;
    }

    public void update(CompoundTag d) {
        String cur = selectedId();
        this.data = d;
        List<CompoundTag> acc = accounts();
        for (int i = 0; i < acc.size(); i++) if (acc.get(i).getString("id").equals(cur)) sel = i;
        if (sel >= acc.size()) sel = 0;
        confirmLoan = false;
        rebuildWidgets();
    }

    private List<CompoundTag> list(String k) {
        ListTag l = data.getList(k, Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }

    private List<CompoundTag> accounts() { return list("accounts"); }
    private CompoundTag selected() { List<CompoundTag> a = accounts(); return a.isEmpty() ? null : a.get(Math.min(sel, a.size() - 1)); }
    private String selectedId() { CompoundTag s = selected(); return s == null ? "" : s.getString("id"); }

    private void act(String action, CompoundTag t) {
        t.putString("acc", selectedId());
        t.putBoolean("manager", data.getBoolean("manager"));
        Net.toServer(action, t);
    }

    private int cx() { return left + 92; }
    private int cy() { return top + 28; }
    private int cw() { return w - 100; }

    private void amountRow(int y, boolean loan) {
        long[] steps = {-1000, -100, -10, 10, 100, 1000};
        int x = cx();
        for (long st : steps) {
            long s = loan ? st * 10 : st;
            button(x, y, 38, 18, Component.literal((s > 0 ? "+" : "") + s), () -> {
                if (loan) loanAmount = Math.max(100, Math.min(100000, loanAmount + s));
                else amount = Math.max(1, Math.min(10_000_000, amount + s));
                rebuildWidgets();
            });
            x += 40;
        }
    }

    @Override
    protected void init() {
        layout(440, 270);
        int ty = top + 26;
        for (String t : TABS) {
            Button b = button(left + 6, ty, 80, 20, Component.translatable("banktab.rpbusiness." + t), () -> { tab = t; scroll = 0; confirmLoan = false; rebuildWidgets(); });
            b.active = !t.equals(tab);
            ty += 22;
        }
        List<CompoundTag> acc = accounts();
        // account selector
        if (!acc.isEmpty() && !tab.equals("accounts") && !tab.equals("loans")) {
            button(cx() + cw() - 120, top + 3, 116, 15, Component.literal(selectedId() + " ▸"), () -> { sel = (sel + 1) % accounts().size(); rebuildWidgets(); });
        }
        switch (tab) {
            case "accounts" -> {
                int y = cy() + 12;
                for (int i = 0; i < acc.size() && y < top + h - 50; i++) {
                    int idx = i;
                    Button b = button(cx() + cw() - 60, y, 56, 16, Component.translatable(i == sel ? "gui.rpbusiness.selected" : "gui.rpbusiness.select"), () -> { sel = idx; rebuildWidgets(); });
                    b.active = i != sel;
                    y += 20;
                }
                button(cx(), top + h - 26, 130, 20, Component.translatable("gui.rpbusiness.open_checking"), () -> { CompoundTag t = new CompoundTag(); t.putString("type", "CHECKING"); act("bank_open", t); });
                button(cx() + 134, top + h - 26, 130, 20, Component.translatable("gui.rpbusiness.open_savings"), () -> { CompoundTag t = new CompoundTag(); t.putString("type", "SAVINGS"); act("bank_open", t); });
            }
            case "cash" -> {
                if (acc.isEmpty()) break;
                amountRow(cy() + 40, false);
                int y = cy() + 66;
                button(cx(), y, 120, 20, Component.translatable("gui.rpbusiness.deposit_amount"), () -> { CompoundTag t = new CompoundTag(); t.putLong("amount", amount); act("bank_deposit", t); });
                button(cx() + 124, y, 120, 20, Component.translatable("gui.rpbusiness.withdraw_amount"), () -> { CompoundTag t = new CompoundTag(); t.putLong("amount", amount); act("bank_withdraw", t); });
                button(cx(), y + 24, 244, 20, Component.translatable("gui.rpbusiness.deposit_all_cash"), () -> { CompoundTag t = new CompoundTag(); t.putLong("amount", -1L); act("bank_deposit", t); });
            }
            case "transfer" -> {
                if (acc.isEmpty()) break;
                String keep = toBox == null ? "" : toBox.getValue();
                toBox = new EditBox(font, cx(), cy() + 20, 150, 18, Component.literal("to"));
                toBox.setMaxLength(16);
                toBox.setValue(keep);
                toBox.setHint(Component.literal("RPB-100001"));
                addRenderableWidget(toBox);
                button(cx() + 154, cy() + 20, 110, 18, Component.translatable("gui.rpbusiness.my_other_account"), () -> {
                    List<CompoundTag> a = accounts();
                    if (a.size() < 2) return;
                    toIndex = (toIndex + 1) % a.size();
                    if (toIndex == sel) toIndex = (toIndex + 1) % a.size();
                    toBox.setValue(a.get(toIndex).getString("id"));
                });
                String keepMemo = memoBox == null ? "" : memoBox.getValue();
                memoBox = new EditBox(font, cx(), cy() + 44, 264, 18, Component.literal("memo"));
                memoBox.setMaxLength(40);
                memoBox.setValue(keepMemo);
                memoBox.setHint(Component.translatable("gui.rpbusiness.memo"));
                addRenderableWidget(memoBox);
                amountRow(cy() + 82, false);
                button(cx(), cy() + 106, 160, 20, Component.translatable("gui.rpbusiness.send_transfer"), () -> {
                    CompoundTag t = new CompoundTag();
                    t.putString("to", toBox.getValue());
                    t.putString("memo", memoBox.getValue());
                    t.putLong("amount", amount);
                    act("bank_transfer", t);
                });
            }
            case "history" -> {}
            case "loans" -> {
                if (acc.isEmpty()) break;
                button(cx() + cw() - 120, top + 3, 116, 15, Component.literal(selectedId() + " ▸"), () -> { sel = (sel + 1) % accounts().size(); rebuildWidgets(); });
                amountRow(cy() + 12, true);
                button(cx(), cy() + 34, 100, 18, Component.translatable("gui.rpbusiness.term", TERMS[term]), () -> { term = (term + 1) % TERMS.length; confirmLoan = false; rebuildWidgets(); });
                Button take = button(cx() + 104, cy() + 34, 160, 18, Component.translatable(confirmLoan ? "gui.rpbusiness.loan_confirm" : "gui.rpbusiness.loan_apply"), () -> {
                    if (!confirmLoan) { confirmLoan = true; rebuildWidgets(); return; }
                    CompoundTag t = new CompoundTag();
                    t.putLong("amount", loanAmount);
                    t.putInt("term", term);
                    act("bank_loan", t);
                });
                take.active = loanAmount <= data.getLong("maxLoan");
                int y = cy() + 106;
                for (CompoundTag l : list("loans")) {
                    if (!l.getString("status").equals("ACTIVE")) continue;
                    if (y > top + h - 24) break;
                    int id = l.getInt("id");
                    button(cx() + cw() - 128, y, 60, 16, Component.translatable("gui.rpbusiness.pay_installment"), () -> { CompoundTag t = new CompoundTag(); t.putInt("loan", id); t.putLong("amount", l.getLong("inst")); act("bank_loan_pay", t); });
                    button(cx() + cw() - 64, y, 60, 16, Component.translatable("gui.rpbusiness.repay_all"), () -> { CompoundTag t = new CompoundTag(); t.putInt("loan", id); t.putLong("amount", -1L); act("bank_loan_pay", t); });
                    y += 24;
                }
            }
            case "cards" -> {
                if (acc.isEmpty()) break;
                button(cx(), cy() + 40, 200, 20, Component.translatable("gui.rpbusiness.issue_card"), () -> act("bank_card", new CompoundTag()));
            }
        }
        button(left + 6, top + h - 26, 80, 20, Component.translatable("gui.rpbusiness.close"), this::onClose);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        frame(g, Component.translatable("gui.rpbusiness.bank_title"));
        panel(g, cx() - 4, cy() - 4, cw() + 6, h - 34, PANEL);
        List<CompoundTag> acc = accounts();
        CompoundTag s = selected();
        int x = cx(), y = cy();
        switch (tab) {
            case "accounts" -> {
                text(g, Component.translatable("gui.rpbusiness.your_accounts"), x, y, ACCENT);
                y += 12;
                if (acc.isEmpty()) text(g, Component.translatable("gui.rpbusiness.no_accounts"), x, y + 4, DIM);
                for (int i = 0; i < acc.size() && y < top + h - 50; i++) {
                    CompoundTag a = acc.get(i);
                    text(g, a.getString("id"), x, y + 4, i == sel ? WARN : TEXT);
                    text(g, Component.translatable("acctype.rpbusiness." + a.getString("type").toLowerCase()).getString() + " " + clip(a.getString("label"), 70), x + 80, y + 4, DIM);
                    text(g, Money.fmt(a.getLong("balance")), x + 210, y + 4, GOOD);
                    y += 20;
                }
                text(g, Component.translatable("gui.rpbusiness.your_cash"), x, top + h - 40, DIM);
                text(g, Money.fmt(data.getLong("cash")), x + 90, top + h - 40, TEXT);
                if (data.getBoolean("manager"))
                    text(g, Component.translatable("gui.rpbusiness.bank_stats", data.getInt("accountCount"), Money.fmt(data.getLong("totalDeposits")), Money.fmt(data.getLong("totalLoans"))), x + 150, top + h - 40, DIM);
            }
            case "cash" -> {
                if (s == null) { text(g, Component.translatable("gui.rpbusiness.no_accounts"), x, y, DIM); break; }
                text(g, Component.translatable("gui.rpbusiness.balance", Money.fmt(s.getLong("balance"))), x, y, GOOD);
                text(g, Component.translatable("gui.rpbusiness.your_cash"), x, y + 12, DIM);
                text(g, Money.fmt(data.getLong("cash")), x + 90, y + 12, TEXT);
                text(g, Component.translatable("gui.rpbusiness.amount", Money.fmt(amount)), x, y + 28, WARN);
            }
            case "transfer" -> {
                if (s == null) break;
                text(g, Component.translatable("gui.rpbusiness.transfer_to"), x, y + 8, DIM);
                text(g, Component.translatable("gui.rpbusiness.balance", Money.fmt(s.getLong("balance"))), x + 150, y, GOOD);
                text(g, Component.translatable("gui.rpbusiness.amount", Money.fmt(amount)), x, y + 70, WARN);
            }
            case "history" -> {
                if (s == null) break;
                text(g, Component.translatable("gui.rpbusiness.history_of", s.getString("id"), Money.fmt(s.getLong("balance"))), x, y, ACCENT);
                y += 12;
                ListTag hl = s.getList("history", Tag.TAG_COMPOUND);
                for (int i = scroll; i < hl.size() && y < top + h - 12; i++) {
                    CompoundTag tx = hl.getCompound(i);
                    long a = tx.getLong("a");
                    text(g, clip(String.format("D%d %02d:00 ", tx.getLong("d"), tx.getInt("h")) + (a >= 0 ? "+" : "") + Money.fmt(a)
                            + "  = " + Money.fmt(tx.getLong("b")) + "  " + tx.getString("s"), cw() - 4), x, y, a >= 0 ? GOOD : BAD);
                    y += 10;
                }
            }
            case "loans" -> {
                if (s == null) { text(g, Component.translatable("gui.rpbusiness.no_accounts"), x, y, DIM); break; }
                int credit = data.getInt("credit");
                int rate = Math.max(2, BASE_RATES[term] + (650 - credit) / 25);
                long total = loanAmount + loanAmount * rate / 100;
                long inst = (total + TERMS[term] - 1) / TERMS[term];
                long day = data.getLong("day");
                text(g, Component.translatable("gui.rpbusiness.loan_amount", Money.fmt(loanAmount)), x, y, WARN);
                int ly = y + 58;
                text(g, Component.translatable("gui.rpbusiness.loan_terms", rate, Money.fmt(total), Money.fmt(inst), day + 1, day + TERMS[term]), x, ly, TEXT);
                text(g, Component.translatable("gui.rpbusiness.loan_info", credit, Money.fmt(data.getLong("maxLoan")), Money.fmt(data.getLong("debt"))), x, ly + 11, DIM);
                text(g, Component.translatable("gui.rpbusiness.loan_note"), x, ly + 22, DIM);
                if (loanAmount > data.getLong("maxLoan")) text(g, Component.translatable("gui.rpbusiness.loan_too_high"), x, ly + 33, BAD);
                int yy = cy() + 106;
                for (CompoundTag l : list("loans")) {
                    if (yy > top + h - 24) break;
                    boolean active = l.getString("status").equals("ACTIVE");
                    if (!active) continue;
                    text(g, clip(Component.translatable("gui.rpbusiness.loan_line", l.getInt("id"), Money.fmt(l.getLong("remaining")), Money.fmt(l.getLong("inst")),
                            l.getLong("next"), l.getInt("missed")).getString(), cw() - 136), x, yy + 4, l.getInt("missed") > 0 ? BAD : TEXT);
                    yy += 24;
                }
            }
            case "cards" -> {
                if (s == null) break;
                text(g, Component.translatable("gui.rpbusiness.card_info", s.getString("id")), x, y, TEXT);
                text(g, Component.translatable("gui.rpbusiness.card_info2"), x, y + 12, DIM);
            }
        }
        super.render(g, mx, my, pt);
    }
}
