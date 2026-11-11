package com.rpbusiness.client.screen;

import com.rpbusiness.economy.Money;
import com.rpbusiness.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** ATM: balance, cash withdrawal, cash deposit, transfers and recent transactions for the inserted card. */
public class AtmScreen extends BaseScreen {
    private CompoundTag data;
    private EditBox toBox;
    private long amount = 50;

    public AtmScreen(CompoundTag data) {
        super(Component.translatable("block.rpbusiness.atm"));
        this.data = data;
    }

    public void update(CompoundTag d) { data = d; rebuildWidgets(); }

    private String acc() { return data.getCompound("account").getString("id"); }

    private void act(String action, CompoundTag t) {
        t.putString("acc", acc());
        t.putBoolean("atm", true);
        Net.toServer(action, t);
    }

    @Override
    protected void init() {
        layout(360, 250);
        int x = left + 10, y = top + 54;
        long[] quick = {20, 50, 100, 500};
        for (long q : quick) {
            button(x, y, 60, 20, Component.literal(Money.fmt(q)), () -> { CompoundTag t = new CompoundTag(); t.putLong("amount", q); act("bank_withdraw", t); });
            x += 64;
        }
        y += 26;
        button(left + 10, y, 124, 20, Component.translatable("gui.rpbusiness.deposit_all_cash"), () -> { CompoundTag t = new CompoundTag(); t.putLong("amount", -1L); act("bank_deposit", t); });
        y += 30;
        String keep = toBox == null ? "" : toBox.getValue();
        toBox = new EditBox(font, left + 10, y, 120, 18, Component.literal("to"));
        toBox.setMaxLength(16);
        toBox.setHint(Component.literal("RPB-100001"));
        toBox.setValue(keep);
        addRenderableWidget(toBox);
        button(left + 134, y, 30, 18, Component.literal("-"), () -> { amount = Math.max(1, amount - 10); rebuildWidgets(); });
        button(left + 198, y, 30, 18, Component.literal("+"), () -> { amount += 10; rebuildWidgets(); });
        button(left + 232, y, 30, 18, Component.literal("++"), () -> { amount += 100; rebuildWidgets(); });
        button(left + 266, y, 84, 18, Component.translatable("gui.rpbusiness.send_transfer"), () -> {
            CompoundTag t = new CompoundTag();
            t.putString("to", toBox.getValue());
            t.putLong("amount", amount);
            t.putString("memo", "ATM");
            act("bank_transfer", t);
        });
        button(left + w - 70, top + h - 26, 60, 20, Component.translatable("gui.rpbusiness.close"), this::onClose);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        frame(g, Component.translatable("block.rpbusiness.atm").append(" • " + acc()));
        CompoundTag a = data.getCompound("account");
        text(g, Component.translatable("gui.rpbusiness.balance", Money.fmt(a.getLong("balance"))), left + 10, top + 28, GOOD);
        text(g, Component.translatable("gui.rpbusiness.your_cash"), left + 180, top + 28, DIM);
        text(g, Money.fmt(data.getLong("cash")), left + 270, top + 28, TEXT);
        text(g, Component.translatable("gui.rpbusiness.withdraw_quick"), left + 10, top + 42, DIM);
        String amt = Money.fmt(amount);
        text(g, amt, left + 166 + 16 - font.width(amt) / 2, top + 145, WARN);
        int y = top + 168;
        text(g, Component.translatable("gui.rpbusiness.recent"), left + 10, y, ACCENT);
        y += 11;
        ListTag hl = a.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size() && y < top + h - 30; i++) {
            CompoundTag tx = hl.getCompound(i);
            long v = tx.getLong("a");
            text(g, clip((v >= 0 ? "+" : "") + Money.fmt(v) + "  " + tx.getString("s"), w - 90), left + 10, y, v >= 0 ? GOOD : BAD);
            y += 10;
        }
        super.render(g, mx, my, pt);
    }
}
