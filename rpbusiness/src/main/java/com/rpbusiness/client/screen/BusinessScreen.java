package com.rpbusiness.client.screen;

import com.rpbusiness.business.BusinessType;
import com.rpbusiness.business.EmployeeRecord;
import com.rpbusiness.business.Finance;
import com.rpbusiness.business.Job;
import com.rpbusiness.economy.Money;
import com.rpbusiness.food.Dish;
import com.rpbusiness.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Business computer: overview, menu & prices, orders, staff, hiring, finances, settings and empire overview. */
public class BusinessScreen extends BaseScreen {
    private static final String[] FOOD_TABS = {"overview", "menu", "orders", "staff", "hire", "finance", "settings", "empire"};
    private static final String[] SHOP_TABS = {"overview", "products", "staff", "hire", "finance", "settings", "empire"};
    private static final String[] DELIVERY_TABS = {"overview", "deliveries", "staff", "hire", "finance", "settings", "empire"};
    private int trFrom, trTo = 1, trItem, trCount = 16;

    private static final String[] HOTEL_TABS = {"overview", "rooms", "staff", "hire", "finance", "settings", "empire"};
    private int dayPage;

    private String[] tabs() {
        BusinessType t = BusinessType.byName(data.getString("type"));
        if (t == BusinessType.HOTEL) return HOTEL_TABS;
        if (t.delivery()) return DELIVERY_TABS;
        if (t.retail && !t.foodService) return SHOP_TABS;
        return FOOD_TABS;
    }
    private CompoundTag data;
    private String tab;
    private UUID biz;
    private final Map<Integer, Job> hireJob = new HashMap<>();
    private UUID confirmFire;
    private boolean confirmDelete;
    private EditBox nameBox;
    private int typeIndex = -1;

    public BusinessScreen(CompoundTag data) {
        super(Component.literal(data.getString("name")));
        this.data = data;
        this.tab = data.getString("tab").isEmpty() ? "overview" : data.getString("tab");
        this.biz = data.getUUID("id");
    }

    /** Used by the dev GUI tour and keyboard shortcuts. */
    public void switchTab(String t) { tab = t; scroll = 0; act("refresh"); rebuildWidgets(); }
    public UUID businessId() { return biz; }

    public boolean sameBusiness(CompoundTag d) { return d.hasUUID("id") && d.getUUID("id").equals(biz); }

    public void update(CompoundTag d) {
        String keepTab = tab;
        this.data = d;
        this.tab = keepTab;
        rebuildWidgets();
    }

    private void act(String action, CompoundTag extra) {
        CompoundTag t = extra == null ? new CompoundTag() : extra;
        t.putUUID("biz", biz);
        t.putString("tab", tab);
        Net.toServer(action, t);
    }

    private void act(String action) { act(action, null); }

    private static CompoundTag tag(Object... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v instanceof Integer n) t.putInt(k, n);
            else if (v instanceof Long n) t.putLong(k, n);
            else if (v instanceof UUID u) t.putUUID(k, u);
            else t.putString(k, String.valueOf(v));
        }
        return t;
    }

    // ---------------------------------------------------------------- layout
    private int cx() { return left + 92; }
    private int cy() { return top + 28; }
    private int cw() { return w - 100; }
    private int chh() { return h - 34; }

    @Override
    protected void init() {
        layout(470, 300);
        int ty = top + 26;
        for (String t : tabs()) {
            Button b = button(left + 6, ty, 80, 20, Component.translatable("tab.rpbusiness." + t), () -> {
                tab = t;
                scroll = 0;
                confirmFire = null;
                confirmDelete = false;
                act("refresh");
                rebuildWidgets();
            });
            b.active = !t.equals(tab);
            ty += 22;
        }
        if (List.of("menu", "orders", "staff", "hire", "finance", "products").contains(tab)) scrollButtons(left + 6, top + h - 24);
        switch (tab) {
            case "overview" -> initOverview();
            case "menu" -> initMenu();
            case "orders" -> initOrders();
            case "staff" -> initStaff();
            case "hire" -> initHire();
            case "settings" -> initSettings();
            case "products" -> initProducts();
            case "deliveries" -> initDeliveries();
            case "empire" -> initEmpire();
            case "finance" -> {
                button(cx() + cw() - 92, top + 4, 44, 14, Component.literal("\u25C0"), () -> { dayPage = Math.min(4, dayPage + 1); rebuildWidgets(); });
                button(cx() + cw() - 46, top + 4, 44, 14, Component.literal("\u25B6"), () -> { dayPage = Math.max(0, dayPage - 1); rebuildWidgets(); });
            }
            case "rooms" -> {
                button(cx(), cy() + 40, 40, 18, Component.literal("-$5"), () -> act("room_price", tag("delta", -5)));
                button(cx() + 42, cy() + 40, 40, 18, Component.literal("+$5"), () -> act("room_price", tag("delta", 5)));
            }
        }
    }

    private void initOverview() {
        int x = cx(), y = top + h - 26, bw = Math.min(88, (cw() - 12) / 4);
        button(x, y, bw, 20, Component.translatable("gui.rpbusiness.withdraw", "$50"), () -> act("withdraw", tag("amount", 50L)));
        button(x + bw + 4, y, bw, 20, Component.translatable("gui.rpbusiness.withdraw_all"), () -> act("withdraw", tag("amount", -1L)));
        button(x + (bw + 4) * 2, y, bw, 20, Component.translatable("gui.rpbusiness.deposit_all"), () -> act("deposit", tag("amount", -1L)));
        button(x + (bw + 4) * 3, y, bw, 20, Component.translatable(data.getBoolean("mclosed") ? "gui.rpbusiness.open_now" : "gui.rpbusiness.close_now"), () -> act("toggle_open"));
        button(x, y - 24, bw, 20, Component.translatable("gui.rpbusiness.drawer_to_bank"), () -> act("to_bank", tag("amount", -1L)));
        button(x + bw + 4, y - 24, bw, 20, Component.translatable("gui.rpbusiness.bank_to_drawer"), () -> act("from_bank", tag("amount", 100L)));
    }

    private void initProducts() {
        List<CompoundTag> prods = list("products");
        int rows = rows(22, 16);
        scroll = Math.min(scroll, Math.max(0, prods.size() - rows));
        int y = cy() + 14;
        int bx = cx() + cw() - 5 * 22 - 2;
        for (int i = scroll; i < Math.min(prods.size(), scroll + rows); i++) {
            String item = prods.get(i).getString("item");
            int[] deltas = {-5, -1, 1, 5};
            for (int k = 0; k < 4; k++) {
                int dlt = deltas[k];
                button(bx + k * 22, y, 20, 18, Component.literal((dlt > 0 ? "+" : "") + dlt), () -> act("product_price", tag("item", item, "delta", dlt)));
            }
            button(bx + 88, y, 20, 18, Component.literal("\u27F2"), () -> act("product_reset", tag("item", item)));
            y += 22;
        }
    }

    private List<CompoundTag> owned() { return list("owned"); }

    private void initDeliveries() {
        int x = cx(), y = cy() + 12;
        button(cx() + cw() - 92, top + 4, 44, 14, Component.literal("-10%"), () -> act("delivery_rate", tag("delta", -10)));
        button(cx() + cw() - 46, top + 4, 44, 14, Component.literal("+10%"), () -> act("delivery_rate", tag("delta", 10)));
        List<CompoundTag> jobs = list("jobs");
        int shown = 0;
        for (CompoundTag j : jobs) {
            String st = j.getString("status");
            if (st.equals("DELIVERED") || st.equals("CANCELLED")) continue;
            if (shown++ >= 6) break;
            int id = j.getInt("id");
            if (st.equals("OFFERED")) button(x + cw() - 108, y, 52, 16, Component.translatable("gui.rpbusiness.accept"), () -> act("accept_job", tag("job", id)));
            if (st.equals("OFFERED") || st.equals("AWAITING_PICKUP")) button(x + cw() - 54, y, 52, 16, Component.translatable("gui.rpbusiness.cancel"), () -> act("cancel_job", tag("job", id)));
            y += 20;
        }
        // create a transfer between your own businesses
        List<CompoundTag> own = owned();
        if (own.size() >= 2) {
            int by = top + h - 48;
            trFrom %= own.size(); trTo %= own.size();
            button(x, by, 100, 18, Component.literal(clip(own.get(trFrom).getString("name"), 90)), () -> { trFrom = (trFrom + 1) % owned().size(); trItem = 0; rebuildWidgets(); });
            button(x + 104, by, 100, 18, Component.literal("\u2192 " + clip(own.get(trTo).getString("name"), 80)), () -> { trTo = (trTo + 1) % owned().size(); rebuildWidgets(); });
            ListTag stock = own.get(trFrom).getList("stock", Tag.TAG_COMPOUND);
            String label = stock.isEmpty() ? "-" : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(stock.getCompound(trItem % stock.size()).getString("item")))).getHoverName().getString();
            button(x, by + 22, 130, 18, Component.literal(clip(label, 120)), () -> { trItem++; rebuildWidgets(); });
            button(x + 134, by + 22, 40, 18, Component.literal("x" + trCount), () -> { trCount = trCount >= 64 ? 8 : trCount * 2; rebuildWidgets(); });
            Button go = button(x + 178, by + 22, 90, 18, Component.translatable("gui.rpbusiness.create_transfer"), () -> {
                ListTag st2 = owned().get(trFrom).getList("stock", Tag.TAG_COMPOUND);
                if (st2.isEmpty()) return;
                CompoundTag t = tag("item", st2.getCompound(trItem % st2.size()).getString("item"), "count", trCount);
                t.putUUID("from", owned().get(trFrom).getUUID("id"));
                t.putUUID("to", owned().get(trTo).getUUID("id"));
                act("create_transfer", t);
            });
            go.active = !stock.isEmpty() && trFrom != trTo;
        }
    }

    private void initEmpire() {
        int y = cy() + 14;
        for (CompoundTag b : list("all")) {
            if (!b.hasUUID("id")) continue;
            java.util.UUID id = b.getUUID("id");
            button(cx() + cw() - 64, y - 2, 30, 12, Component.literal("-"), () -> { CompoundTag t = tag("delta", -10); t.putUUID("other", id); act("mult_of", t); });
            button(cx() + cw() - 32, y - 2, 30, 12, Component.literal("+"), () -> { CompoundTag t = tag("delta", 10); t.putUUID("other", id); act("mult_of", t); });
            y += 22;
        }
        button(cx(), top + h - 26, 120, 20, Component.translatable("gui.rpbusiness.all_prices_down"), () -> act("all_mult", tag("delta", -10)));
        button(cx() + 124, top + h - 26, 120, 20, Component.translatable("gui.rpbusiness.all_prices_up"), () -> act("all_mult", tag("delta", 10)));
    }

    private List<CompoundTag> list(String key) {
        ListTag l = data.getList(key, Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }

    private int rows(int rowH, int reserved) { return Math.max(1, (chh() - reserved) / rowH); }

    private void initMenu() {
        List<CompoundTag> menu = list("menu");
        int rows = rows(22, 16);
        scroll = Math.min(scroll, Math.max(0, menu.size() - rows));
        int y = cy() + 14;
        int bx = cx() + cw() - 4 * 22 - 2;
        for (int i = scroll; i < Math.min(menu.size(), scroll + rows); i++) {
            CompoundTag m = menu.get(i);
            String dish = m.getString("dish");
            button(cx(), y, 34, 18, Component.translatable(m.getBoolean("on") ? "gui.rpbusiness.on" : "gui.rpbusiness.off"), () -> act("menu_toggle", tag("dish", dish)));
            int[] deltas = {-5, -1, 1, 5};
            for (int k = 0; k < 4; k++) {
                int dlt = deltas[k];
                button(bx + k * 22, y, 20, 18, Component.literal((dlt > 0 ? "+" : "") + dlt), () -> act("menu_price", tag("dish", dish, "delta", dlt)));
            }
            y += 22;
        }
        button(cx() + cw() - 90, top + 4, 86, 14, Component.translatable("gui.rpbusiness.reset_prices"), () -> act("menu_reset"));
    }

    private void initOrders() {
        List<CompoundTag> orders = list("orders");
        int rows = rows(20, 16);
        scroll = Math.min(scroll, Math.max(0, orders.size() - rows / 2));
        int y = cy() + 14;
        for (int i = scroll; i < Math.min(orders.size(), scroll + rows / 2 + 1); i++) {
            int id = orders.get(i).getInt("id");
            button(cx() + cw() - 52, y, 50, 16, Component.translatable("gui.rpbusiness.cancel"), () -> act("cancel_order", tag("order", id)));
            y += 20;
        }
    }

    private void initStaff() {
        List<CompoundTag> emps = list("employees");
        int per = 2;
        int rows = Math.max(1, (chh() - 14) / 46);
        scroll = Math.min(scroll, Math.max(0, emps.size() - rows));
        int y = cy() + 12;
        for (int i = scroll; i < Math.min(emps.size(), scroll + rows); i++) {
            CompoundTag e = emps.get(i);
            UUID id = e.getUUID("id");
            int x = cx() + cw() - 6 * 32;
            int yy = y + 1;
            button(x, yy, 30, 16, Component.translatable("gui.rpbusiness.job"), () -> act("emp_job", tag("emp", id)));
            button(x + 32, yy, 30, 16, Component.literal("-$5"), () -> act("emp_salary", tag("emp", id, "delta", -5)));
            button(x + 64, yy, 30, 16, Component.literal("+$5"), () -> act("emp_salary", tag("emp", id, "delta", 5)));
            Button pr = button(x + 96, yy, 30, 16, Component.literal("↑"), () -> act("emp_promote", tag("emp", id)));
            pr.active = e.getInt("rank") < 2;
            boolean armed = id.equals(confirmFire);
            button(x + 128, yy, 62, 16, Component.translatable(armed ? "gui.rpbusiness.confirm" : "gui.rpbusiness.fire"), () -> {
                if (id.equals(confirmFire)) { confirmFire = null; act("fire", tag("emp", id)); }
                else { confirmFire = id; rebuildWidgets(); }
            });
            yy += 20;
            button(x, yy, 30, 16, Component.literal("S-"), () -> act("emp_hours", tag("emp", id, "start", -1)));
            button(x + 32, yy, 30, 16, Component.literal("S+"), () -> act("emp_hours", tag("emp", id, "start", 1)));
            button(x + 64, yy, 30, 16, Component.literal("E-"), () -> act("emp_hours", tag("emp", id, "end", -1)));
            button(x + 96, yy, 30, 16, Component.literal("E+"), () -> act("emp_hours", tag("emp", id, "end", 1)));
            Button pay = button(x + 128, yy, 30, 16, Component.literal("$"), () -> act("emp_pay", tag("emp", id)));
            pay.active = e.getInt("unpaid") > 0;
            button(x + 160, yy, 30, 16, Component.literal("⟲"), () -> act("emp_clear", tag("emp", id)));
            y += 46;
            if (per == 0) break;
        }
    }

    private void initHire() {
        List<CompoundTag> c = list("candidates");
        int rows = rows(22, 40);
        scroll = Math.min(scroll, Math.max(0, c.size() - rows));
        int y = cy() + 14;
        for (int i = scroll; i < Math.min(c.size(), scroll + rows); i++) {
            int idx = i;
            Job pref = Job.byName(c.get(i).getString("job"));
            Job chosen = hireJob.getOrDefault(i, pref);
            button(cx() + cw() - 150, y, 96, 18, Component.translatable(chosen.key()), () -> {
                hireJob.put(idx, hireJob.getOrDefault(idx, pref).nextFor(BusinessType.byName(data.getString("type"))));
                rebuildWidgets();
            });
            button(cx() + cw() - 50, y, 48, 18, Component.translatable("gui.rpbusiness.hire"), () -> {
                Job j = hireJob.getOrDefault(idx, pref);
                hireJob.clear();
                act("hire", tag("index", idx, "job", j.name()));
            });
            y += 22;
        }
        button(cx(), top + h - 26, 150, 20, Component.translatable("gui.rpbusiness.new_candidates"), () -> act("new_candidates"));
    }

    private void initSettings() {
        int x = cx(), y = cy() + 4;
        nameBox = new EditBox(font, x, y, cw() - 70, 18, Component.literal("name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(data.getString("name"));
        addRenderableWidget(nameBox);
        button(x + cw() - 66, y, 64, 18, Component.translatable("gui.rpbusiness.save_name"), () -> act("rename", tag("name", nameBox.getValue())));
        y += 24;
        BusinessType[] types = BusinessType.values();
        if (typeIndex < 0) typeIndex = BusinessType.byName(data.getString("type")).ordinal();
        button(x, y, 140, 18, Component.translatable(types[typeIndex].key()), () -> { typeIndex = (typeIndex + 1) % types.length; rebuildWidgets(); });
        button(x + 144, y, 64, 18, Component.translatable("gui.rpbusiness.apply"), () -> act("set_type", tag("type", types[typeIndex].name())));
        y += 24;
        button(x, y, 40, 18, Component.literal("O-"), () -> act("hours", tag("open", -1, "close", 0)));
        button(x + 42, y, 40, 18, Component.literal("O+"), () -> act("hours", tag("open", 1, "close", 0)));
        button(x + 100, y, 40, 18, Component.literal("C-"), () -> act("hours", tag("open", 0, "close", -1)));
        button(x + 142, y, 40, 18, Component.literal("C+"), () -> act("hours", tag("open", 0, "close", 1)));
        y += 22;
        button(x, y, 40, 18, Component.literal("P-"), () -> act("mult", tag("delta", -10)));
        button(x + 42, y, 40, 18, Component.literal("P+"), () -> act("mult", tag("delta", 10)));
        button(x + 100, y, 40, 18, Component.literal("D-"), () -> act("discount", tag("delta", -5)));
        button(x + 142, y, 40, 18, Component.literal("D+"), () -> act("discount", tag("delta", 5)));
        y += 22;
        button(x, y, 140, 18, Component.translatable(data.getBoolean("autoBuy") ? "gui.rpbusiness.autobuy_on" : "gui.rpbusiness.autobuy_off"), () -> act("auto_buy"));
        button(x + 144, y, 40, 18, Component.literal("-50"), () -> act("auto_budget", tag("delta", -50)));
        button(x + 186, y, 40, 18, Component.literal("+50"), () -> act("auto_budget", tag("delta", 50)));
        y += 26;
        int bw = (cw() - 8) / 2;
        button(x, y, bw, 20, Component.translatable("gui.rpbusiness.entrance_here"), () -> act("entrance_here"));
        button(x + bw + 4, y, bw, 20, Component.translatable("gui.rpbusiness.rescan"), () -> act("rescan"));
        y += 24;
        int lvl = data.getInt("level");
        Button up = button(x, y, bw, 20, lvl >= 5 ? Component.translatable("gui.rpbusiness.max_level")
                : Component.translatable("gui.rpbusiness.upgrade", lvl + 1, Money.fmt(400L * lvl)), () -> act("upgrade"));
        up.active = lvl < 5;
        button(x + bw + 4, y, bw, 20, Component.translatable("item.rpbusiness.recipe_book"), () -> minecraft.setScreen(new RecipeBookScreen(this)));
        y += 24;
        button(x, y, bw, 20, Component.translatable(confirmDelete ? "gui.rpbusiness.confirm_delete" : "gui.rpbusiness.delete"), () -> {
            if (confirmDelete) { act("delete"); onClose(); }
            else { confirmDelete = true; rebuildWidgets(); }
        });
    }

    // ---------------------------------------------------------------- rendering
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        frame(g, Component.literal(data.getString("name") + "  •  ").append(Component.translatable(BusinessType.byName(data.getString("type")).key())));
        String clock = String.format("Day %d  %02d:00", data.getLong("day"), data.getInt("hour"));
        text(g, clock, left + w - 8 - font.width(clock) - (tab.equals("menu") ? 92 : 0), top + 6, DIM);
        panel(g, cx() - 4, cy() - 4, cw() + 6, chh() + 2, PANEL);
        switch (tab) {
            case "overview" -> renderOverview(g);
            case "menu" -> renderMenu(g);
            case "orders" -> renderOrders(g);
            case "staff" -> renderStaff(g);
            case "hire" -> renderHire(g);
            case "finance" -> renderFinance(g);
            case "settings" -> renderSettings(g);
            case "empire" -> renderEmpire(g);
            case "products" -> renderProducts(g);
            case "rooms" -> {
                text(g, Component.translatable("gui.rpbusiness.rooms_header"), cx(), cy(), ACCENT);
                text(g, Component.translatable("gui.rpbusiness.room_price", Money.fmt(data.getInt("roomPrice"))), cx(), cy() + 14, WARN);
                var rl = data.getList("roomList", 10);
                text(g, Component.translatable("gui.rpbusiness.rooms_info2", rl.size(), data.getInt("roomsFree"), data.getInt("beds"), data.getInt("bedsDirty")), cx(), cy() + 26, TEXT);
                int ry = cy() + 64, maxRows = Math.max(1, (chh() - 80) / 11);
                for (int i = 0; i < Math.min(rl.size(), maxRows); i++) {
                    var m = rl.getCompound(i);
                    text(g, m.getString("name"), cx(), ry, ACCENT);
                    text(g, Component.translatable("gui.rpbusiness.room_cap", m.getInt("cap")), cx() + 70, ry, DIM);
                    Component st = m.contains("guest")
                            ? Component.translatable("gui.rpbusiness.room_guest", m.getString("guest"), m.getInt("hoursLeft"))
                            : Component.translatable(m.getBoolean("dirty") ? "gui.rpbusiness.room_dirty" : "gui.rpbusiness.room_free");
                    text(g, st, cx() + 120, ry, m.contains("guest") ? WARN : m.getBoolean("dirty") ? BAD : GOOD);
                    if (m.getBoolean("req")) text(g, Component.translatable("gui.rpbusiness.room_req"), cx() + cw() - 70, ry, BAD);
                    ry += 11;
                }
                if (rl.size() > maxRows) text(g, "+" + (rl.size() - maxRows), cx(), ry, DIM);
                text(g, Component.translatable("gui.rpbusiness.rooms_hint"), cx(), cy() + chh() - 12, DIM);
            }
            case "deliveries" -> renderDeliveries(g);
        }
        super.render(g, mx, my, pt);
    }

    private String stars(int rep) {
        int full = rep / 200;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < full ? '★' : '☆');
        return sb.toString();
    }

    private void renderOverview(GuiGraphics g) {
        int x = cx(), y = cy(), col2 = cx() + cw() / 2;
        boolean open = data.getBoolean("isOpen");
        text(g, Component.translatable(open ? "gui.rpbusiness.status_open" : "gui.rpbusiness.status_closed"), x, y, open ? GOOD : BAD);
        text(g, String.format("%02d:00 - %02d:00", data.getInt("open"), data.getInt("close")), x + 90, y, DIM);
        y += 14;
        text(g, Component.translatable("gui.rpbusiness.reputation"), x, y, DIM);
        text(g, stars(data.getInt("rep")) + "  (" + data.getInt("rep") / 10 + "%)", x + 70, y, WARN);
        y += 16;
        text(g, Component.translatable("gui.rpbusiness.business_cash"), x, y, DIM);
        text(g, Money.fmt(data.getLong("cash")), x + 110, y, GOOD);
        y += 12;
        text(g, Component.translatable("gui.rpbusiness.your_cash"), x, y, DIM);
        text(g, Money.fmt(data.getLong("playerCash")), x + 110, y, TEXT);
        y += 12;
        text(g, Component.translatable("gui.rpbusiness.bank_account", data.getString("bankId")), x, y, DIM);
        text(g, Money.fmt(data.getLong("bankBalance")), x + 110, y, GOOD);
        y += 16;
        CompoundTag today = list("days").isEmpty() ? new CompoundTag() : list("days").get(0);
        long[] a = today.getLongArray("a");
        long inc = 0, exp = 0;
        if (a.length >= Finance.Cat.values().length) {
            inc = a[Finance.Cat.FOOD_SALES.ordinal()] + a[Finance.Cat.DRINK_SALES.ordinal()] + a[Finance.Cat.TIPS.ordinal()]
                    + a[Finance.Cat.PRODUCT_SALES.ordinal()] + a[Finance.Cat.DELIVERY_INCOME.ordinal()] + a[Finance.Cat.ROOM_SALES.ordinal()];
            exp = a[Finance.Cat.SALARIES.ordinal()] + a[Finance.Cat.SUPPLIES.ordinal()] + a[Finance.Cat.DELIVERY.ordinal()] + a[Finance.Cat.HIRING.ordinal()]
                    + a[Finance.Cat.OTHER.ordinal()] + a[Finance.Cat.AUTO_PURCHASE.ordinal()];
        }
        text(g, Component.translatable("gui.rpbusiness.today"), x, y, ACCENT);
        y += 12;
        text(g, Component.translatable("gui.rpbusiness.income"), x, y, DIM); text(g, Money.fmt(inc), x + 110, y, GOOD); y += 11;
        text(g, Component.translatable("gui.rpbusiness.expenses"), x, y, DIM); text(g, Money.fmt(exp), x + 110, y, BAD); y += 11;
        text(g, Component.translatable("gui.rpbusiness.profit"), x, y, DIM); text(g, Money.fmt(inc - exp), x + 110, y, inc - exp >= 0 ? GOOD : BAD); y += 11;
        text(g, Component.translatable("gui.rpbusiness.customers_today", today.getInt("c"), today.getInt("l")), x, y, TEXT);

        int y2 = cy() + 30;
        text(g, Component.translatable("gui.rpbusiness.served_total", data.getLong("served")), col2, y2, TEXT); y2 += 11;
        text(g, Component.translatable("gui.rpbusiness.lost_total", data.getLong("lost")), col2, y2, TEXT); y2 += 11;
        text(g, Component.translatable("gui.rpbusiness.avg_satisfaction", data.getInt("avgSat")), col2, y2, TEXT); y2 += 11;
        text(g, Component.translatable("gui.rpbusiness.staff_count", list("employees").size()), col2, y2, TEXT); y2 += 11;
        int active = 0;
        for (CompoundTag o : list("orders")) if (!o.getString("status").equals("SERVED")) active++;
        text(g, Component.translatable("gui.rpbusiness.active_orders", active), col2, y2, TEXT); y2 += 11;
        text(g, Component.translatable("gui.rpbusiness.free_seats", data.getInt("freeSeats")), col2, y2, TEXT); y2 += 11;
        text(g, Component.translatable("gui.rpbusiness.level", data.getInt("level")), col2, y2, TEXT); y2 += 14;
        CompoundTag eq = data.getCompound("equip");
        text(g, Component.translatable("gui.rpbusiness.equipment"), col2, y2, ACCENT); y2 += 11;
        String[][] show = {{"CHAIR", "TABLE", "REGISTER"}, {"GRILL", "FRYER", "OVEN"}, {"CUTTING_BOARD", "PREP", "STORAGE"}, {"SERVING", "DRINK", "CRATE"}};
        for (String[] row : show) {
            StringBuilder sb = new StringBuilder();
            for (String k : row) sb.append(Component.translatable("equip.rpbusiness." + k.toLowerCase(Locale.ROOT)).getString()).append(' ').append(eq.getInt(k)).append("  ");
            text(g, clip(sb.toString(), cw() / 2 - 6), col2, y2, DIM);
            y2 += 10;
        }
        if (eq.getInt("CHAIR") == 0 || eq.getInt("TABLE") == 0)
            text(g, Component.translatable("gui.rpbusiness.need_seats"), x, top + h - 40, WARN);
    }

    private void renderMenu(GuiGraphics g) {
        text(g, Component.translatable("gui.rpbusiness.menu_header"), cx() + 40, cy(), DIM);
        List<CompoundTag> menu = list("menu");
        int rows = rows(22, 16);
        int y = cy() + 14;
        int bx = cx() + cw() - 4 * 22 - 2;
        for (int i = scroll; i < Math.min(menu.size(), scroll + rows); i++) {
            CompoundTag m = menu.get(i);
            Dish d = Dish.byId(m.getString("dish"));
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(m.getString("item"))));
            g.renderItem(icon, cx() + 38, y + 1);
            boolean on = m.getBoolean("on"), avail = m.getBoolean("avail");
            text(g, clip(Component.translatable(d.key()).getString(), 90), cx() + 58, y + 1, on ? TEXT : DIM);
            int price = m.getInt("price"), base = m.getInt("base");
            int pc = price > base * 1.5 ? BAD : price < base * 0.8 ? WARN : GOOD;
            text(g, Money.fmt(price), cx() + 150, y + 1, pc);
            String sub = (on ? (avail ? "✔ " : "✖ ") : "") + Component.translatable("gui.rpbusiness.menu_sub", Money.fmt(m.getInt("cost")), Money.fmt(base), m.getInt("sold")).getString();
            text(g, clip(sub, bx - cx() - 62), cx() + 58, y + 11, on && !avail ? BAD : DIM);
            y += 22;
        }
    }

    private void renderOrders(GuiGraphics g) {
        List<CompoundTag> orders = list("orders");
        text(g, Component.translatable("gui.rpbusiness.active_orders", orders.size()), cx(), cy(), ACCENT);
        int y = cy() + 14;
        int rows = rows(20, 16);
        int shown = 0;
        for (int i = scroll; i < orders.size() && shown <= rows / 2; i++, shown++) {
            CompoundTag o = orders.get(i);
            Dish d = Dish.byId(o.getString("dish"));
            String st = o.getString("status");
            int col = switch (st) { case "READY" -> GOOD; case "COOKING" -> WARN; case "SERVED" -> DIM; default -> TEXT; };
            String line = "#" + o.getInt("id") + " " + o.getString("cname") + " - " + Component.translatable(d == null ? "?" : d.key()).getString()
                    + " " + Money.fmt(o.getInt("price"));
            text(g, clip(line, cw() - 60), cx(), y, TEXT);
            String sub = Component.translatable("order_status.rpbusiness." + st.toLowerCase(Locale.ROOT)).getString() + " • " + o.getLong("age") + "s";
            if (o.contains("cookName")) sub += " • " + o.getString("cookName");
            if (o.contains("shortage")) sub += " • ⚠ " + o.getString("shortage");
            text(g, clip(sub, cw() - 60), cx(), y + 9, o.contains("shortage") ? BAD : col);
            y += 20;
        }
        y += 4;
        text(g, Component.translatable("gui.rpbusiness.recent_orders"), cx(), y, ACCENT);
        y += 12;
        for (CompoundTag hst : list("history")) {
            if (y > cy() + chh() - 12) break;
            Dish d = Dish.byId(hst.getString("dish"));
            String st = hst.getString("status");
            String line = hst.getString("cname") + " - " + Component.translatable(d == null ? "?" : d.key()).getString() + " " + Money.fmt(hst.getInt("price"))
                    + (st.equals("SERVED") ? "  Q" + hst.getInt("quality") + "%" : "  ✖");
            text(g, clip(line, cw() - 4), cx(), y, st.equals("SERVED") ? DIM : BAD);
            y += 10;
        }
    }

    private void renderStaff(GuiGraphics g) {
        List<CompoundTag> emps = list("employees");
        if (emps.isEmpty()) { text(g, Component.translatable("gui.rpbusiness.no_staff"), cx(), cy() + 4, DIM); return; }
        int rows = Math.max(1, (chh() - 14) / 46);
        int y = cy() + 12;
        text(g, Component.translatable("gui.rpbusiness.staff_hint"), cx(), cy() - 1, DIM);
        for (int i = scroll; i < Math.min(emps.size(), scroll + rows); i++) {
            CompoundTag e = emps.get(i);
            Job job = Job.byName(e.getString("job"));
            int maxW = cw() - 6 * 32 - 6;
            String l1 = e.getString("name") + " - " + Component.translatable(job.key()).getString();
            text(g, clip(l1, maxW), cx(), y + 1, e.getBoolean("present") ? TEXT : DIM);
            String l2 = Component.translatable(EmployeeRecord.RANKS[Math.min(2, e.getInt("rank"))]).getString() + " ★" + e.getInt("skill")
                    + "  " + Money.fmt(e.getInt("salary")) + "/day";
            text(g, clip(l2, maxW), cx(), y + 11, WARN);
            String status = e.getString("status");
            String sk = status.contains(":") ? status.substring(0, status.indexOf(':')) : status;
            String l3 = String.format("%02d-%02d ", e.getInt("start"), e.getInt("end")) + Component.translatable("status.rpbusiness." + (sk.isEmpty() ? "idle" : sk)).getString();
            if (e.getInt("unpaid") > 0) l3 = Component.translatable("gui.rpbusiness.unpaid", e.getInt("unpaid")).getString();
            text(g, clip(l3, maxW), cx(), y + 21, e.getInt("unpaid") > 0 ? BAD : DIM);
            String l4 = Component.translatable("gui.rpbusiness.staff_pos", e.getBoolean("stand") ? "✔" : "-", e.getInt("stations"), e.getInt("route"), e.getLong("tasks")).getString();
            text(g, clip(l4, maxW), cx(), y + 31, DIM);
            g.fill(cx(), y + 43, cx() + cw() - 2, y + 44, LINE);
            y += 46;
        }
    }

    private void renderHire(GuiGraphics g) {
        text(g, Component.translatable("gui.rpbusiness.hire_header"), cx(), cy(), DIM);
        List<CompoundTag> c = list("candidates");
        if (c.isEmpty()) text(g, Component.translatable("gui.rpbusiness.no_candidates"), cx(), cy() + 16, DIM);
        int rows = rows(22, 40);
        int y = cy() + 14;
        for (int i = scroll; i < Math.min(c.size(), scroll + rows); i++) {
            CompoundTag t = c.get(i);
            Job pref = Job.byName(t.getString("job"));
            text(g, clip(t.getString("name"), cw() - 160), cx(), y + 1, TEXT);
            text(g, clip("★" + t.getInt("skill") + "  " + Money.fmt(t.getInt("salary")) + "/day  " + Component.translatable(pref.key()).getString(), cw() - 160), cx(), y + 11, DIM);
            y += 22;
        }
        text(g, Component.translatable("gui.rpbusiness.hire_note"), cx() + 156, top + h - 20, DIM);
    }

    private void renderFinance(GuiGraphics g) {
        int x = cx(), y = cy();
        int[] cols = {0, 46, 104, 160, 214, 268};
        String[] head = {"gui.rpbusiness.day", "gui.rpbusiness.income", "gui.rpbusiness.salaries", "gui.rpbusiness.supplies", "gui.rpbusiness.profit", "gui.rpbusiness.customers"};
        for (int i = 0; i < head.length; i++) if (cols[i] < cw() - 20) text(g, clip(Component.translatable(head[i]).getString(), 54), x + cols[i], y, ACCENT);
        y += 12;
        List<CompoundTag> days = list("days");
        for (CompoundTag d : days.subList(Math.min(days.size(), dayPage * 7), Math.min(days.size(), dayPage * 7 + 7))) {
            long[] a = d.getLongArray("a");
            if (a.length < Finance.Cat.values().length) continue;
            long inc = a[Finance.Cat.FOOD_SALES.ordinal()] + a[Finance.Cat.DRINK_SALES.ordinal()] + a[Finance.Cat.TIPS.ordinal()]
                    + a[Finance.Cat.PRODUCT_SALES.ordinal()] + a[Finance.Cat.DELIVERY_INCOME.ordinal()] + a[Finance.Cat.ROOM_SALES.ordinal()];
            long sal = a[Finance.Cat.SALARIES.ordinal()];
            long sup = a[Finance.Cat.SUPPLIES.ordinal()] + a[Finance.Cat.DELIVERY.ordinal()] + a[Finance.Cat.AUTO_PURCHASE.ordinal()];
            long exp = sal + sup + a[Finance.Cat.HIRING.ordinal()] + a[Finance.Cat.OTHER.ordinal()];
            String[] vals = {"#" + d.getLong("day"), Money.fmt(inc), Money.fmt(sal), Money.fmt(sup), Money.fmt(inc - exp), d.getInt("c") + "/" + (d.getInt("c") + d.getInt("l"))};
            for (int i = 0; i < vals.length; i++) if (cols[i] < cw() - 20) text(g, vals[i], x + cols[i], y, i == 4 ? (inc - exp >= 0 ? GOOD : BAD) : TEXT);
            y += 10;
        }
        y += 4;
        text(g, Component.translatable("gui.rpbusiness.totals", Money.fmt(data.getLong("totalIncome")), Money.fmt(data.getLong("totalExpenses")),
                Money.fmt(data.getLong("totalIncome") - data.getLong("totalExpenses"))), x, y, WARN);
        y += 14;
        text(g, Component.translatable("gui.rpbusiness.transactions"), x, y, ACCENT);
        y += 11;
        List<CompoundTag> log = list("log");
        for (int i = scroll; i < log.size(); i++) {
            if (y > cy() + chh() - 12) break;
            CompoundTag t = log.get(i);
            long amt = t.getLong("amt");
            String line = String.format("D%d %02d:00 ", t.getLong("day"), t.getInt("h")) + Component.translatable("fincat.rpbusiness." + t.getString("cat").toLowerCase(Locale.ROOT)).getString()
                    + " " + (amt >= 0 ? "+" : "") + Money.fmt(amt) + " " + t.getString("desc");
            text(g, clip(line, cw() - 4), x, y, amt >= 0 ? GOOD : BAD);
            y += 10;
        }
    }

    private void renderSettings(GuiGraphics g) {
        int x = cx(), y = cy() + 52;
        text(g, Component.translatable("gui.rpbusiness.hours_label", String.format("%02d:00", data.getInt("open")), String.format("%02d:00", data.getInt("close"))), x + 190, y + 5, TEXT);
        text(g, Component.translatable("gui.rpbusiness.price_label", data.getInt("mult"), data.getInt("discount")), x + 190, y + 27, TEXT);
        text(g, Component.translatable("gui.rpbusiness.autobuy_budget", Money.fmt(data.getInt("autoBudget"))), x + 232, y + 49, DIM);
        int[] mn = data.getIntArray("min"), mx = data.getIntArray("max");
        if (mn.length == 3 && mx.length == 3)
            text(g, clip(Component.translatable("gui.rpbusiness.area", mn[0] + "," + mn[1] + "," + mn[2], mx[0] + "," + mx[1] + "," + mx[2]).getString(), cw()), x, top + h - 16, DIM);
    }

    private void renderProducts(GuiGraphics g) {
        text(g, Component.translatable("gui.rpbusiness.products_header"), cx(), cy(), DIM);
        List<CompoundTag> prods = list("products");
        if (prods.isEmpty()) text(g, Component.translatable("gui.rpbusiness.no_products"), cx(), cy() + 16, DIM);
        int rows = rows(22, 16);
        int y = cy() + 14;
        int bx = cx() + cw() - 5 * 22 - 2;
        for (int i = scroll; i < Math.min(prods.size(), scroll + rows); i++) {
            CompoundTag m = prods.get(i);
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(m.getString("item"))));
            g.renderItem(icon, cx(), y + 1);
            text(g, clip(icon.getHoverName().getString(), bx - cx() - 70), cx() + 20, y + 1, TEXT);
            int fin = m.getInt("final"), base = m.getInt("base");
            text(g, Money.fmt(fin), bx - 44, y + 1, fin > base * 1.5 ? BAD : fin < base * 0.8 ? WARN : GOOD);
            text(g, clip(Component.translatable("gui.rpbusiness.product_sub", m.getInt("shelf"), m.getInt("store"), Money.fmt(base)).getString(), bx - cx() - 24), cx() + 20, y + 11,
                    m.getInt("shelf") == 0 ? BAD : DIM);
            y += 22;
        }
    }

    private void renderDeliveries(GuiGraphics g) {
        int x = cx(), y = cy();
        text(g, Component.translatable("gui.rpbusiness.delivery_header", data.getInt("deliveryRate")), x, y - 1, ACCENT);
        y += 12;
        int shown = 0;
        for (CompoundTag j : list("jobs")) {
            String st = j.getString("status");
            if (st.equals("DELIVERED") || st.equals("CANCELLED")) continue;
            if (shown++ >= 6) break;
            String line = "#" + j.getInt("id") + " " + (j.getString("kind").equals("TRANSFER")
                    ? j.getString("fromName") + " \u2192 " + j.getString("client") + " (" + j.getInt("count") + "x)"
                    : j.getString("client")) + "  " + Money.fmt(j.getLong("reward"));
            text(g, clip(line, cw() - 112), x, y, TEXT);
            int[] dp = j.getIntArray("dest");
            String sub = Component.translatable("delivery_status.rpbusiness." + st.toLowerCase(Locale.ROOT)).getString() + (dp.length == 3 ? "  @" + dp[0] + "," + dp[1] + "," + dp[2] : "");
            text(g, clip(sub, cw() - 112), x, y + 9, st.equals("OFFERED") ? WARN : DIM);
            y += 20;
        }
        if (shown == 0) text(g, Component.translatable("gui.rpbusiness.no_jobs"), x, y, DIM);
        if (owned().size() >= 2) text(g, Component.translatable("gui.rpbusiness.transfer_header"), x, top + h - 60, ACCENT);
        int done = 0;
        long earned = 0;
        for (CompoundTag j : list("jobs")) if (j.getString("status").equals("DELIVERED")) { done++; earned += j.getLong("reward"); }
        text(g, Component.translatable("gui.rpbusiness.delivered_count", done, Money.fmt(earned)), x + 150, top + h - 60, DIM);
    }

    private void renderEmpire(GuiGraphics g) {
        int x = cx(), y = cy();
        text(g, Component.translatable("gui.rpbusiness.empire_header"), x, y, ACCENT);
        y += 14;
        long cash = 0, inc = 0, exp = 0;
        int staff = 0;
        for (CompoundTag b : list("all")) {
            text(g, clip(b.getString("name"), 120), x, y, TEXT);
            text(g, clip(Component.translatable(BusinessType.byName(b.getString("type")).key()).getString(), 70), x + 124, y, DIM);
            text(g, Money.fmt(b.getLong("cash")) + " + " + Money.fmt(b.getLong("bank")), x + 198, y, GOOD);
            text(g, clip("+" + Money.fmt(b.getLong("income")) + " / -" + Money.fmt(b.getLong("expenses")) + "   " + Component.translatable("gui.rpbusiness.price_short", b.getInt("mult")).getString(), cw() - 70), x, y + 10, DIM);
            y += 22;
            cash += b.getLong("bank");
            cash += b.getLong("cash"); inc += b.getLong("income"); exp += b.getLong("expenses"); staff += b.getInt("staff");
        }
        y += 6;
        text(g, Component.translatable("gui.rpbusiness.empire_total", Money.fmt(cash), Money.fmt(inc), Money.fmt(exp), staff), x, y, WARN);
        text(g, Component.translatable("gui.rpbusiness.empire_debt", Money.fmt(data.getLong("debt"))), x, y + 11, data.getLong("debt") > 0 ? BAD : DIM);
    }
}
