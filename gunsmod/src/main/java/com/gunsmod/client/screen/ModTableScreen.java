package com.gunsmod.client.screen;

import com.gunsmod.gun.GunAttachments;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.GunItem;
import com.gunsmod.menu.ModTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Gunsmith table: gun preview in the middle (drag to rotate), attachment slots around it, live stats. */
public class ModTableScreen extends AbstractContainerScreen<ModTableMenu> {
    private float yaw = -25f, pitch = 12f, zoom = 1f;
    private boolean dragging, autoSpin = true;
    private static final int PV_X = 64, PV_Y = 18, PV_W = 232, PV_H = 128;

    public ModTableScreen(ModTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 360;
        this.imageHeight = 262;
        this.inventoryLabelX = 99;
        this.inventoryLabelY = 170;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.hazard(g, x + 6, y + imageHeight - 12, imageWidth - 12);
        UiKit.dark(g, x + PV_X - 2, y + PV_Y - 2, PV_W + 4, PV_H + 4);
        g.fillGradient(x + PV_X, y + PV_Y, x + PV_X + PV_W, y + PV_Y + PV_H, 0xFF26302A, 0xFF101310);
        for (int gx = 0; gx < PV_W; gx += 16) g.fill(x + PV_X + gx, y + PV_Y, x + PV_X + gx + 1, y + PV_Y + PV_H, 0x12FFFFFF);
        ItemStack gun = menu.gun();
        if (!gun.isEmpty()) UiKit.preview(g, gun, x + PV_X, y + PV_Y, PV_W, PV_H, yaw, pitch, zoom);
        else {
            Component hint = Component.translatable("gunsmod.ui.place_gun");
            g.drawString(font, hint, x + PV_X + (PV_W - font.width(hint)) / 2, y + PV_Y + PV_H / 2 - 4, 0x80FFFFFF, false);
        }
        // attachment slots with labels and connector lines towards the gun
        String[] names = {"scope", "muzzle", "grip", "laser", "mag"};
        for (int i = 0; i < 6; i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            boolean active = s.isActive();
            UiKit.slot(g, sx, sy);
            if (!active) g.fill(sx, sy, sx + 16, sy + 16, 0xA0300000);
            if (i > 0) {
                Component lbl = Component.translatable("gunsmod.slot." + names[i - 1]);
                int lx = s.x < 180 ? sx - 2 : sx + 18 - font.width(lbl);
                g.drawString(font, lbl, Math.max(x + 6, lx), sy - 10, active ? UiKit.ACCENT : 0xFF6A6A6A, false);
                int cx = x + PV_X + PV_W / 2, cy = y + PV_Y + PV_H / 2;
                int ex = s.x < 180 ? sx + 18 : sx - 2;
                if (!gun.isEmpty() && active && s.hasItem()) {
                    line(g, ex, sy + 8, s.x < 180 ? x + PV_X : x + PV_X + PV_W, sy + 8, 0x80E0B040);
                }
            }
        }
        // stats
        if (gun.getItem() instanceof GunItem gi) {
            GunType t = gi.type;
            GunAttachments a = GunItem.attachments(gun);
            int ix = x + 6, iy = y + 136;
            UiKit.dark(g, x + 4, y + 132, 56, 40);
            g.drawString(font, Component.translatable("gunsmod.ui.recoil"), ix, iy, 0xFFCCCCCC, false);
            UiKit.bar(g, ix, iy + 9, 50, 1f - Mth.clamp(t.recoil * a.recoilMul() / 14f, 0, 1), UiKit.GOOD);
            g.drawString(font, Component.translatable("gunsmod.ui.accuracy"), ix, iy + 16, 0xFFCCCCCC, false);
            UiKit.bar(g, ix, iy + 25, 50, 1f - Mth.clamp(t.hipSpread * a.hipSpreadMul() / 9f, 0, 1), UiKit.ACCENT);
            UiKit.dark(g, x + 300, y + 132, 56, 40);
            g.drawString(font, Component.translatable("gunsmod.ui.mag"), x + 304, iy, 0xFFCCCCCC, false);
            g.drawString(font, String.valueOf(a.magSize(t)), x + 304, iy + 10, 0xFFFFFFFF, true);
            g.drawString(font, Component.translatable("gunsmod.ui.zoom"), x + 304, iy + 20, 0xFFCCCCCC, false);
            g.drawString(font, String.format("%.1fx", a.zoom(t)), x + 304, iy + 30 - 1, 0xFFFFFFFF, true);
        }
        for (int i = 6; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            UiKit.slot(g, x + s.x, y + s.y);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        g.fill(Math.min(x0, x1), y0, Math.max(x0, x1), y0 + 1, col);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        UiKit.title(g, font, this.title, 8, 6);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, UiKit.MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (autoSpin && !dragging) yaw += pt * 0.5f;
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        if (mx >= x + PV_X && mx < x + PV_X + PV_W && my >= y + PV_Y && my < y + PV_Y + PV_H && this.hoveredSlot == null) {
            dragging = true;
            autoSpin = false;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
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
        zoom = Mth.clamp(zoom + (float) sy * 0.1f, 0.5f, 2.5f);
        return true;
    }
}
