package com.rpbusiness.client.screen;

import com.rpbusiness.economy.Money;
import com.rpbusiness.economy.Suppliers;
import com.rpbusiness.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Order ingredients and kitchen supplies. Goods are brought by a courier NPC to the delivery crate. */
public class SupplierScreen extends BaseScreen {
    private CompoundTag data;
    private final UUID biz;
    private int company;
    private final int[][] cart = new int[Suppliers.ALL.size()][8];
    private int payMode;
    private boolean showOrders;

    public SupplierScreen(CompoundTag data) {
        super(Component.translatable("gui.rpbusiness.supplier_title"));
        this.data = data;
        this.biz = data.getUUID("id");
    }

    public boolean sameBusiness(CompoundTag d) { return d.hasUUID("id") && d.getUUID("id").equals(biz); }
    public void update(CompoundTag d) { data = d; rebuildWidgets(); }

    private long productTotal() {
        long t = 0;
        for (int c = 0; c < Suppliers.ALL.size(); c++)
            for (int p = 0; p < Suppliers.ALL.get(c).products().size(); p++) t += (long) cart[c][p] * Suppliers.ALL.get(c).products().get(p).price();
        return t;
    }

    private int lines() {
        int n = 0;
        for (int c = 0; c < Suppliers.ALL.size(); c++)
            for (int p = 0; p < Suppliers.ALL.get(c).products().size(); p++) if (cart[c][p] > 0) n++;
        return n;
    }

    private long fee() {
        int stacks = 0;
        for (int c = 0; c < Suppliers.ALL.size(); c++)
            for (int p = 0; p < Suppliers.ALL.get(c).products().size(); p++) {
                var pr = Suppliers.ALL.get(c).products().get(p);
                stacks += (cart[c][p] * pr.pack() + 63) / 64;
            }
        return stacks == 0 ? 0 : 8 + stacks * 2L;
    }

    @Override
    protected void init() {
        layout(440, 280);
        int x = left + 8, y = top + 26;
        for (int c = 0; c < Suppliers.ALL.size(); c++) {
            int ci = c;
            Button b = button(x, y, 118, 18, Component.translatable(Suppliers.ALL.get(c).key()), () -> { company = ci; showOrders = false; rebuildWidgets(); });
            b.active = showOrders || company != c;
            y += 20;
        }
        Button ob = button(x, y + 6, 118, 18, Component.translatable("gui.rpbusiness.my_deliveries"), () -> { showOrders = true; rebuildWidgets(); });
        ob.active = !showOrders;
        if (!showOrders) {
            var comp = Suppliers.ALL.get(company);
            int ry = top + 40;
            for (int p = 0; p < comp.products().size(); p++) {
                int pi = p;
                int bx = left + w - 98;
                button(bx, ry, 20, 18, Component.literal("-"), () -> { cart[company][pi] = Math.max(0, cart[company][pi] - 1); rebuildWidgets(); });
                button(bx + 50, ry, 20, 18, Component.literal("+"), () -> { cart[company][pi] = Math.min(64, cart[company][pi] + 1); rebuildWidgets(); });
                button(bx + 72, ry, 20, 18, Component.literal("+5"), () -> { cart[company][pi] = Math.min(64, cart[company][pi] + 5); rebuildWidgets(); });
                ry += 24;
            }
        }
        int by = top + h - 26;
        String[] modes = {"gui.rpbusiness.pay_business", "gui.rpbusiness.pay_cash", "gui.rpbusiness.pay_bank"};
        button(left + 134, by, 110, 20, Component.translatable(modes[payMode]), () -> { payMode = (payMode + 1) % 3; rebuildWidgets(); });
        Button order = button(left + 248, by, 90, 20, Component.translatable("gui.rpbusiness.place_order"), () -> {
            ListTag l = new ListTag();
            for (int c = 0; c < Suppliers.ALL.size(); c++)
                for (int p = 0; p < Suppliers.ALL.get(c).products().size(); p++) {
                    if (cart[c][p] <= 0) continue;
                    CompoundTag t = new CompoundTag();
                    t.putInt("c", c); t.putInt("p", p); t.putInt("n", cart[c][p]);
                    l.add(t);
                }
            CompoundTag t = new CompoundTag();
            t.putUUID("biz", biz);
            t.put("cart", l);
            t.putInt("payMode", payMode);
            Net.toServer("supply_order", t);
            for (int[] row : cart) java.util.Arrays.fill(row, 0);
            showOrders = true;
            rebuildWidgets();
        });
        order.active = lines() > 0;
        button(left + w - 60, by, 52, 20, Component.translatable("gui.rpbusiness.close"), this::onClose);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        frame(g, Component.translatable("gui.rpbusiness.supplier_title").append(" • " + data.getString("name")));
        int x = left + 134;
        panel(g, x - 4, top + 24, w - 138, h - 54, PANEL);
        if (!showOrders) {
            var comp = Suppliers.ALL.get(company);
            text(g, comp.name(), x, top + 28, ACCENT);
            int ry = top + 40;
            for (int p = 0; p < comp.products().size(); p++) {
                var pr = comp.products().get(p);
                ItemStack st = new ItemStack(pr.item().get());
                g.renderItem(st, x, ry + 1);
                text(g, clip(st.getHoverName().getString(), 120), x + 20, ry, TEXT);
                text(g, Component.translatable("gui.rpbusiness.pack", pr.pack(), Money.fmt(pr.price())), x + 20, ry + 10, DIM);
                String q = String.valueOf(cart[company][p]);
                text(g, q, left + w - 98 + 35 - font.width(q) / 2, ry + 5, cart[company][p] > 0 ? WARN : DIM);
                ry += 24;
            }
        } else {
            text(g, Component.translatable("gui.rpbusiness.my_deliveries"), x, top + 28, ACCENT);
            int y = top + 40;
            ListTag l = data.getList("orders", Tag.TAG_COMPOUND);
            if (l.isEmpty()) text(g, Component.translatable("gui.rpbusiness.no_deliveries"), x, y, DIM);
            for (int i = 0; i < l.size() && y < top + h - 40; i++) {
                CompoundTag o = l.getCompound(i);
                String st = o.getString("status");
                int col = st.equals("DELIVERED") ? GOOD : st.equals("IN_TRANSIT") ? WARN : TEXT;
                String eta = st.equals("ORDERED") ? " ~" + o.getLong("eta") + "s" : "";
                text(g, clip("#" + o.getInt("id") + " " + Component.translatable("supply_status.rpbusiness." + st.toLowerCase()).getString() + eta + "  " + Money.fmt(o.getLong("total")), w - 150), x, y, col);
                text(g, clip(o.getString("goods"), w - 150), x, y + 10, DIM);
                y += 22;
            }
        }
        long total = productTotal() + fee();
        text(g, Component.translatable("gui.rpbusiness.cart_total", Money.fmt(productTotal()), Money.fmt(fee()), Money.fmt(total)), left + 8, top + h - 46, TEXT);
        text(g, Component.translatable("gui.rpbusiness.balances", Money.fmt(data.getLong("cash")), Money.fmt(data.getLong("playerCash"))), left + 8, top + h - 36, DIM);
        if (!data.getBoolean("hasCrate")) text(g, Component.translatable("gui.rpbusiness.no_crate"), x, top + h - 46 - 12, WARN);
        super.render(g, mx, my, pt);
    }

    @SuppressWarnings("unused")
    private List<String> unused() { return new ArrayList<>(); }
}
