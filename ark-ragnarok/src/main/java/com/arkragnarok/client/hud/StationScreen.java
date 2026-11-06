package com.arkragnarok.client.hud;

import com.arkragnarok.economy.Recipes;
import com.arkragnarok.economy.StationMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** ARK-like crafting screen: recipe rows (result, name, ingredients - red when missing); click a row to craft */
public class StationScreen extends AbstractContainerScreen<StationMenu> {
    private static final int ROWS = 6, ROW_H = 20, LIST_Y = 18;
    private int scroll;

    public StationScreen(StationMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = 129;
    }

    private List<Recipes.Recipe> list() {
        return menu.recipes();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0201C18);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, 0xFFD9A441);
        g.fill(x + 4, y + LIST_Y - 2, x + imageWidth - 4, y + LIST_Y + ROWS * ROW_H + 2, 0xFF141210);
        for (int c = 0; c < 9; c++) for (int r = 0; r < 4; r++) {
            int sx = x + 7 + c * 18, sy = y + (r < 3 ? 139 + r * 18 : 197);
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF3A342C);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF151311);
        }
        List<Recipes.Recipe> rs = list();
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < ROWS && i + scroll < rs.size(); i++) {
            Recipes.Recipe r = rs.get(i + scroll);
            int ry = y + LIST_Y + i * ROW_H;
            boolean can = Recipes.canPay(inv, r.ingredients());
            boolean hover = mx >= x + 4 && mx < x + imageWidth - 4 && my >= ry && my < ry + ROW_H;
            g.fill(x + 5, ry, x + imageWidth - 5, ry + ROW_H - 1, hover ? (can ? 0xFF3A4A2A : 0xFF4A2A2A) : 0xFF26221E);
            g.renderItem(r.resultStack(), x + 7, ry + 1);
            int ix = x + 28;
            for (Recipes.Ing ing : r.ingredients()) {
                ItemStack st = new ItemStack(ing.item().get());
                g.renderItem(st, ix, ry + 1);
                int have = Recipes.count(inv, ing.item().get());
                String t = String.valueOf(ing.count());
                g.drawString(font, t, ix + 16 - font.width(t), ry + 10, have >= ing.count() ? 0xFFFFFFFF : 0xFFFF5050, true);
                ix += 22;
            }
            if (r.count() > 1) g.drawString(font, "x" + r.count(), x + imageWidth - 24, ry + 6, 0xFFE0C080, false);
        }
        if (rs.size() > ROWS) {
            int barH = ROWS * ROW_H;
            int h = Math.max(12, barH * ROWS / rs.size());
            int by = y + LIST_Y + (barH - h) * scroll / Math.max(1, rs.size() - ROWS);
            g.fill(x + imageWidth - 4, by, x + imageWidth - 2, by + h, 0xFFD9A441);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, 0xFFE9C46A, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFB0B0B0, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        List<Recipes.Recipe> rs = list();
        for (int i = 0; i < ROWS && i + scroll < rs.size(); i++) {
            int ry = topPos + LIST_Y + i * ROW_H;
            if (mx >= leftPos + 4 && mx < leftPos + 26 && my >= ry && my < ry + ROW_H) g.renderTooltip(font, rs.get(i + scroll).resultStack(), mx, my);
        }
        renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        List<Recipes.Recipe> rs = list();
        for (int i = 0; i < ROWS && i + scroll < rs.size(); i++) {
            int ry = topPos + LIST_Y + i * ROW_H;
            if (mx >= leftPos + 4 && mx < leftPos + imageWidth - 4 && my >= ry && my < ry + ROW_H) {
                int idx = i + scroll;
                if (Recipes.canPay(minecraft.player.getInventory(), rs.get(idx).ingredients())) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, idx);
                    if (hasShiftDown()) for (int k = 0; k < 9; k++) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, idx);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, list().size() - ROWS));
        return true;
    }
}
