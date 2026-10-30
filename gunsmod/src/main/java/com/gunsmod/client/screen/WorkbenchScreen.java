package com.gunsmod.client.screen;

import com.gunsmod.gun.GunType;
import com.gunsmod.item.GunItem;
import com.gunsmod.menu.WorkbenchMenu;
import com.gunsmod.menu.WorkbenchRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Weapon workbench: tabs, recipe list, a rotatable 3D preview of the selection, stats and costs, craft button. */
public class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu> {
    private WorkbenchRecipes.Tab tab = WorkbenchRecipes.Tab.RIFLES;
    private int selected = -1;
    private int scroll;
    private float yaw = -30f, pitch = 15f, zoom = 1f;
    private boolean dragging, autoSpin = true;
    private int craftFlash;

    private static final int LIST_X = 6, LIST_Y = 30, LIST_W = 92, ROW = 15, ROWS = 9;
    private static final int PV_X = 102, PV_Y = 30, PV_W = 154, PV_H = 136;

    public WorkbenchScreen(WorkbenchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 360;
        this.imageHeight = 254;
        this.inventoryLabelX = 99;
        this.inventoryLabelY = 161;
    }

    private List<Integer> visible() {
        List<Integer> out = new ArrayList<>();
        List<WorkbenchRecipes.Recipe> all = WorkbenchRecipes.all();
        for (int i = 0; i < all.size(); i++) if (all.get(i).tab() == tab) out.add(i);
        return out;
    }

    @Override
    protected void init() {
        super.init();
        List<Integer> v = visible();
        if (selected < 0 && !v.isEmpty()) selected = v.get(0);
    }

    private UiKit.Btn craftButton() {
        boolean can = selected >= 0 && WorkbenchRecipes.all().get(selected).canCraft(menu.player);
        return new UiKit.Btn(0, leftPos + 262, topPos + 148, 92, 18, Component.translatable("gunsmod.ui.craft"), can, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.hazard(g, x + 6, y + imageHeight - 12, imageWidth - 12);
        // tabs
        WorkbenchRecipes.Tab[] tabs = WorkbenchRecipes.Tab.values();
        int tw = (imageWidth - 12) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            boolean on = tabs[i] == tab;
            UiKit.Btn b = new UiKit.Btn(100 + i, x + 6 + i * tw, y + 6, tw - 2, 18, Component.translatable("gunsmod.tab." + tabs[i].name().toLowerCase()), true, on);
            UiKit.button(g, font, b, mx, my);
        }
        // recipe list
        UiKit.dark(g, x + LIST_X - 2, y + LIST_Y - 2, LIST_W + 4, ROWS * ROW + 4);
        List<Integer> v = visible();
        for (int r = 0; r < ROWS && r + scroll < v.size(); r++) {
            int idx = v.get(r + scroll);
            WorkbenchRecipes.Recipe rec = WorkbenchRecipes.all().get(idx);
            int ry = y + LIST_Y + r * ROW;
            boolean hover = mx >= x + LIST_X && mx < x + LIST_X + LIST_W && my >= ry && my < ry + ROW;
            if (idx == selected) g.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + ROW - 1, 0x80E0B040);
            else if (hover) g.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + ROW - 1, 0x40FFFFFF);
            g.pose().pushPose();
            g.pose().translate(x + LIST_X + 1, ry, 0);
            g.pose().scale(0.85f, 0.85f, 1f);
            g.renderItem(rec.result(), 0, 0);
            g.pose().popPose();
            String name = font.plainSubstrByWidth(rec.result().getHoverName().getString(), LIST_W - 20);
            g.drawString(font, name, x + LIST_X + 17, ry + 4, rec.canCraft(menu.player) ? 0xFFFFFFFF : 0xFFA0A0A0, false);
        }
        if (v.size() > ROWS) {
            int sh = ROWS * ROW;
            int knob = Math.max(10, sh * ROWS / v.size());
            int ky = (int) ((sh - knob) * (scroll / (float) (v.size() - ROWS)));
            g.fill(x + LIST_X + LIST_W, y + LIST_Y + ky, x + LIST_X + LIST_W + 2, y + LIST_Y + ky + knob, 0xFFE0B040);
        }
        // preview
        UiKit.dark(g, x + PV_X - 2, y + PV_Y - 2, PV_W + 4, PV_H + 4);
        g.fillGradient(x + PV_X, y + PV_Y, x + PV_X + PV_W, y + PV_Y + PV_H, 0xFF2B3226, 0xFF141712);
        for (int gx = 0; gx < PV_W; gx += 12) g.fill(x + PV_X + gx, y + PV_Y, x + PV_X + gx + 1, y + PV_Y + PV_H, 0x14FFFFFF);
        for (int gy = 0; gy < PV_H; gy += 12) g.fill(x + PV_X, y + PV_Y + gy, x + PV_X + PV_W, y + PV_Y + gy + 1, 0x14FFFFFF);
        if (selected >= 0) {
            ItemStack st = WorkbenchRecipes.all().get(selected).result();
            UiKit.preview(g, st, x + PV_X, y + PV_Y, PV_W, PV_H, yaw, pitch, zoom);
        }
        g.drawString(font, Component.translatable("gunsmod.ui.drag_hint"), x + PV_X + 3, y + PV_Y + PV_H - 10, 0x80FFFFFF, false);
        // info panel
        UiKit.dark(g, x + 260, y + 28, 96, 116);
        if (selected >= 0) info(g, WorkbenchRecipes.all().get(selected), x + 264, y + 32);
        UiKit.button(g, font, craftButton(), mx, my);
        if (craftFlash > 0) g.fill(x + 262, y + 148, x + 354, y + 166, (craftFlash * 20) << 24 | 0xFFFFFF);
        // player inventory slot frames
        for (int i = 0; i < 36; i++) {
            var s = menu.slots.get(i);
            UiKit.slot(g, x + s.x, y + s.y);
        }
    }

    private void info(GuiGraphics g, WorkbenchRecipes.Recipe rec, int x, int y) {
        ItemStack st = rec.result();
        g.drawString(font, font.plainSubstrByWidth(st.getHoverName().getString(), 88), x, y, UiKit.ACCENT, true);
        int yy = y + 13;
        if (st.getItem() instanceof GunItem gun) {
            GunType t = gun.type;
            stat(g, x, yy, "gunsmod.ui.damage", t.kind == GunType.Kind.BULLET ? t.damage * t.pellets / 30f : 0.9f);
            stat(g, x, yy + 11, "gunsmod.ui.rate", Mth.clamp(20f / t.fireDelay / 20f, 0, 1));
            stat(g, x, yy + 22, "gunsmod.ui.accuracy", 1f - Mth.clamp(t.hipSpread / 9f, 0, 1));
            stat(g, x, yy + 33, "gunsmod.ui.range", Mth.clamp(t.range / 260f, 0, 1));
            stat(g, x, yy + 44, "gunsmod.ui.mobility", t.category == GunType.Category.PISTOL ? 1f : t.category == GunType.Category.SMG ? 0.85f : t.category == GunType.Category.HEAVY ? 0.3f : 0.6f);
            yy += 58;
        } else {
            yy += 4;
        }
        g.drawString(font, Component.translatable("gunsmod.ui.requires"), x, yy, UiKit.MUTED, false);
        yy += 10;
        for (WorkbenchRecipes.Cost c : rec.costs()) {
            int have = menu.player.getAbilities().instabuild ? c.count() : rec.have(menu.player, c);
            g.pose().pushPose();
            g.pose().translate(x, yy - 3, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.renderItem(new ItemStack(c.item()), 0, 0);
            g.pose().popPose();
            String txt = Math.min(have, 999) + "/" + c.count();
            g.drawString(font, txt, x + 13, yy, have >= c.count() ? UiKit.GOOD : UiKit.BAD, false);
            String nm = font.plainSubstrByWidth(new ItemStack(c.item()).getHoverName().getString(), 88 - 15 - font.width(txt) - 4);
            g.drawString(font, nm, x + 17 + font.width(txt), yy, 0xFFCCCCCC, false);
            yy += 10;
        }
    }

    private void stat(GuiGraphics g, int x, int y, String key, float v) {
        g.drawString(font, Component.translatable(key), x, y, 0xFFCCCCCC, false);
        UiKit.bar(g, x + 44, y + 2, 42, v, v > 0.66f ? UiKit.GOOD : v > 0.33f ? UiKit.ACCENT : UiKit.BAD);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, UiKit.MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (autoSpin && !dragging) yaw += pt * 0.6f;
        if (craftFlash > 0) craftFlash--;
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        WorkbenchRecipes.Tab[] tabs = WorkbenchRecipes.Tab.values();
        int tw = (imageWidth - 12) / tabs.length;
        if (my >= y + 6 && my < y + 24 && mx >= x + 6 && mx < x + 6 + tw * tabs.length) {
            int i = (int) ((mx - x - 6) / tw);
            if (i >= 0 && i < tabs.length && tabs[i] != tab) {
                tab = tabs[i];
                scroll = 0;
                List<Integer> v = visible();
                selected = v.isEmpty() ? -1 : v.get(0);
                click();
            }
            return true;
        }
        if (mx >= x + LIST_X && mx < x + LIST_X + LIST_W && my >= y + LIST_Y && my < y + LIST_Y + ROWS * ROW) {
            int r = (int) ((my - y - LIST_Y) / ROW) + scroll;
            List<Integer> v = visible();
            if (r < v.size()) {
                selected = v.get(r);
                click();
            }
            return true;
        }
        if (craftButton().hit(mx, my)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, selected);
            craftFlash = 10;
            return true;
        }
        if (mx >= x + PV_X && mx < x + PV_X + PV_W && my >= y + PV_Y && my < y + PV_Y + PV_H) {
            dragging = true;
            autoSpin = false;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw += (float) dx * 1.5f;
            pitch = Mth.clamp(pitch + (float) dy * 1.5f, -90f, 90f);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int x = leftPos, y = topPos;
        if (mx >= x + PV_X && mx < x + PV_X + PV_W && my >= y + PV_Y && my < y + PV_Y + PV_H) {
            zoom = Mth.clamp(zoom + (float) sy * 0.1f, 0.5f, 2.5f);
            return true;
        }
        if (mx >= x + LIST_X && mx < x + LIST_X + LIST_W + 4) {
            int max = Math.max(0, visible().size() - ROWS);
            scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, max);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
