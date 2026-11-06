package com.arkragnarok.client.hud;

import com.arkragnarok.survivor.BaseDesign;
import com.arkragnarok.survivor.TribeMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Survivor management: the tribes on the left; on the right what the selected one is (temperament, stage, base,
 * task, stores, dinosaurs, raids, relations, skills) and the blueprint viewer - a plan from above of any of the
 * designs (rooms coloured by use, pens, fields, towers, wall ring) with buttons to give it to the tribe or let the
 * tribe choose by itself.
 */
public class TribeScreen extends AbstractContainerScreen<TribeMenu> {
    private static final String[] STAGES = {"survival", "settled", "development", "expansion", "industry", "advanced"};
    private static final int[] ROOM_COLORS = {0xFF6FA8DC, 0xFFB08850, 0xFFC07040, 0xFFB04030, 0xFFE0A040, 0xFF60C0C0, 0xFF909090, 0xFF8A6A3A,
            0xFFD0C060, 0xFFE080B0, 0xFFA080D0};
    private static final String[] ROOM_NAMES = {"sleep", "storage", "workshop", "smelter", "kitchen", "lab", "armory", "barn", "market", "nursery", "hall"};
    private static final int LIST_W = 112, ROW_H = 22;
    private int selected, scroll, view = -1;

    public TribeScreen(TribeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 360;
        this.imageHeight = 232;
    }

    private List<TribeMenu.Entry> tribes() {
        return menu.tribes;
    }

    private int viewDesign() {
        if (view >= 0) return view;
        List<TribeMenu.Entry> l = tribes();
        return l.isEmpty() ? 0 : l.get(Mth.clamp(selected, 0, l.size() - 1)).design();
    }

    // ------------------------------------------------------------------------------------------ drawing
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0201C18);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, 0xFFD9A441);
        g.drawString(font, title, x + 8, y + 6, 0xFFE9C46A, false);
        List<TribeMenu.Entry> l = tribes();
        // tribe list
        g.fill(x + 4, y + 18, x + 4 + LIST_W, y + imageHeight - 4, 0xFF141210);
        if (l.isEmpty()) g.drawString(font, "no survivors yet", x + 8, y + 24, 0xFF909090, false);
        int rows = (imageHeight - 24) / ROW_H;
        for (int i = 0; i < rows && i + scroll < l.size(); i++) {
            TribeMenu.Entry e = l.get(i + scroll);
            int ry = y + 20 + i * ROW_H;
            boolean sel = i + scroll == selected;
            boolean hover = mx >= x + 4 && mx < x + 4 + LIST_W && my >= ry && my < ry + ROW_H;
            g.fill(x + 5, ry, x + 3 + LIST_W, ry + ROW_H - 2, sel ? 0xFF4A3E22 : hover ? 0xFF332C24 : 0xFF26221E);
            g.drawString(font, font.plainSubstrByWidth(e.name(), LIST_W - 8), x + 8, ry + 2, 0xFFFFE0A0, false);
            g.drawString(font, "stage " + e.stage() + " " + STAGES[Math.min(5, Math.max(0, e.stage() - 1))], x + 8, ry + 11, 0xFFA0A0A0, false);
        }
        int rx = x + LIST_W + 10, rw = imageWidth - LIST_W - 14;
        if (!l.isEmpty()) {
            TribeMenu.Entry e = l.get(Mth.clamp(selected, 0, l.size() - 1));
            int ty = y + 20;
            ty = line(g, e.name() + " - " + e.temperament().replace('_', ' '), rx, ty, rw, 0xFFFFD27A);
            ty = line(g, "stage " + e.stage() + "/6 " + STAGES[Math.min(5, Math.max(0, e.stage() - 1))] + "   now: "
                    + Component.translatable("task.arkragnarok." + e.task()).getString(), rx, ty, rw, 0xFF9AD0FF);
            String base = e.base() == null ? "no base yet" : "#" + e.design() + " " + BaseDesign.get(e.design()).archetype.label + " at "
                    + e.base().toShortString() + ", " + e.built() + "/" + e.total() + " built" + (e.manual() ? " (assigned)" : "");
            ty = line(g, base, rx, ty, rw, 0xFF9AD0FF);
            ty = line(g, e.piecesBuilt() + " pieces built, " + e.piecesLost() + " lost, " + e.tames() + " dinos, " + e.raids() + " raids, "
                    + e.defended() + " defences", rx, ty, rw, 0xFFC0C0C0);
            ty = line(g, "stores: " + (e.stock().isEmpty() ? "-" : e.stock()), rx, ty, rw, 0xFFC0C0C0);
            ty = line(g, "skills: " + e.skills(), rx, ty, rw, 0xFFC0C0C0);
            String feel = e.relation() > 20 ? "friendly" : e.relation() < -30 ? "hostile" : "rival";
            ty = line(g, "towards you: " + feel + " (" + e.relation() + ")" + (e.relations().isEmpty() ? "" : "   others: " + e.relations()), rx, ty, rw,
                    0xFFC0C0C0);
        }
        // blueprint viewer
        int by = y + 108, bh = imageHeight - 108 - 26;
        g.fill(rx - 2, by - 2, rx + rw, by + bh, 0xFF141210);
        BaseDesign d = BaseDesign.get(viewDesign());
        g.drawString(font, font.plainSubstrByWidth("#" + d.id + " " + d.describe(), rw - 4), rx + 2, by + 1, 0xFFE9C46A, false);
        drawPlan(g, d, rx + 2, by + 12, rw - 6, bh - 26);
        StringBuilder rooms = new StringBuilder();
        d.roomSummary().forEach((k, v) -> rooms.append(v).append(' ').append(k).append("  "));
        g.drawString(font, font.plainSubstrByWidth(rooms.toString().trim() + "  - " + d.pieceCount() + " pieces", rw - 4), rx + 2, by + bh - 11,
                0xFFA0A0A0, false);
        // buttons
        int bty = y + imageHeight - 22;
        button(g, rx, bty, 18, "<", mx, my);
        button(g, rx + 22, bty, 18, ">", mx, my);
        button(g, rx + 46, bty, 110, "Build this design", mx, my);
        button(g, rx + 160, bty, 90, "Let it choose", mx, my);
    }

    private int line(GuiGraphics g, String s, int x, int y, int w, int color) {
        g.drawString(font, font.plainSubstrByWidth(s, w - 4), x, y, color, false);
        return y + 11;
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
        g.fill(x, y, x + w, y + 16, hover ? 0xFF6A5530 : 0xFF3E3424);
        g.fill(x, y, x + w, y + 1, 0xFFD9A441);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, 0xFFFFFFFF, false);
    }

    /** the plan from above: ground-floor rooms coloured by use (darker edge = more storeys), pens, fields, towers */
    private void drawPlan(GuiGraphics g, BaseDesign d, int x, int y, int w, int h) {
        List<int[]> cells = d.mapCells();
        int minX = 999, maxX = -999, minZ = 999, maxZ = -999;
        for (int[] c : cells) {
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]);
            maxZ = Math.max(maxZ, c[1]);
        }
        int cols = maxX - minX + 1, rowsN = maxZ - minZ + 1;
        int cs = Math.max(4, Math.min(14, Math.min((w - 70) / Math.max(1, cols + 2), h / Math.max(1, rowsN + 2))));
        int ox = x + 4 + cs, oz = y + cs;
        if (d.walled()) {
            int x0 = ox - cs / 2, z0 = oz - cs / 2, x1 = ox + cols * cs + cs / 2, z1 = oz + rowsN * cs + cs / 2;
            g.fill(x0 - 1, z0 - 1, x1 + 1, z0, 0xFFB0A080);
            g.fill(x0 - 1, z1, x1 + 1, z1 + 1, 0xFFB0A080);
            g.fill(x0 - 1, z0, x0, z1, 0xFFB0A080);
            g.fill(x1, z0, x1 + 1, z1, 0xFFB0A080);
        }
        int[][] storeys = new int[cols][rowsN];
        for (int[] c : cells) if (c[2] > 0) storeys[c[0] - minX][c[1] - minZ] = Math.max(storeys[c[0] - minX][c[1] - minZ], c[2]);
        for (int[] c : cells) {
            if (c[2] > 0) continue;
            int px = ox + (c[0] - minX) * cs, pz = oz + (c[1] - minZ) * cs;
            int col = c[3] >= 0 ? ROOM_COLORS[c[3] % ROOM_COLORS.length] : c[3] == -1 ? 0xFF7A5A30 : c[3] == -2 ? 0xFF4A8A3A : 0xFF707070;
            if (c[3] == -3) {
                g.fill(px + cs / 4, pz + cs / 4, px + cs - cs / 4, pz + cs - cs / 4, col);
                continue;
            }
            g.fill(px, pz, px + cs - 1, pz + cs - 1, col);
            // storeys above: one dark notch per floor in the corner
            int st = c[3] >= 0 ? storeys[c[0] - minX][c[1] - minZ] : 0;
            for (int k = 0; k < st; k++) g.fill(px + 1 + k * 3, pz + 1, px + 3 + k * 3, pz + 3, 0xFF101010);
        }
        // legend
        int lx = x + w - 62, ly = y;
        for (int i = 0; i < ROOM_NAMES.length && ly < y + h - 8; i++) {
            if (d.count(BaseDesign.Room.values()[i]) == 0) continue;
            g.fill(lx, ly + 1, lx + 6, ly + 7, ROOM_COLORS[i]);
            g.drawString(font, ROOM_NAMES[i], lx + 9, ly, 0xFFB0B0B0, false);
            ly += 9;
        }
        if (d.penCells() > 0 && ly < y + h - 8) {
            g.fill(lx, ly + 1, lx + 6, ly + 7, 0xFF7A5A30);
            g.drawString(font, "dino pen", lx + 9, ly, 0xFFB0B0B0, false);
            ly += 9;
        }
        if (d.fieldCells() > 0 && ly < y + h - 8) {
            g.fill(lx, ly + 1, lx + 6, ly + 7, 0xFF4A8A3A);
            g.drawString(font, "field", lx + 9, ly, 0xFFB0B0B0, false);
            ly += 9;
        }
        if (d.towerCount() > 0 && ly < y + h - 8) {
            g.fill(lx, ly + 1, lx + 6, ly + 7, 0xFF707070);
            g.drawString(font, "tower", lx + 9, ly, 0xFFB0B0B0, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    // ------------------------------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        List<TribeMenu.Entry> l = tribes();
        int rows = (imageHeight - 24) / ROW_H;
        for (int i = 0; i < rows && i + scroll < l.size(); i++) {
            int ry = y + 20 + i * ROW_H;
            if (mx >= x + 4 && mx < x + 4 + LIST_W && my >= ry && my < ry + ROW_H) {
                selected = i + scroll;
                view = -1;
                return true;
            }
        }
        int rx = x + LIST_W + 10, bty = y + imageHeight - 22;
        if (my >= bty && my < bty + 16) {
            if (mx >= rx && mx < rx + 18) {
                view = Math.floorMod(viewDesign() - 1, BaseDesign.COUNT);
                return true;
            }
            if (mx >= rx + 22 && mx < rx + 40) {
                view = Math.floorMod(viewDesign() + 1, BaseDesign.COUNT);
                return true;
            }
            if (!l.isEmpty() && mx >= rx + 46 && mx < rx + 156) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected * 256 + viewDesign());
                minecraft.player.displayClientMessage(Component.literal(l.get(selected).name() + " will build #" + viewDesign() + " "
                        + BaseDesign.get(viewDesign()).archetype.label), true);
                return true;
            }
            if (!l.isEmpty() && mx >= rx + 160 && mx < rx + 250) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, selected * 256 + 255);
                minecraft.player.displayClientMessage(Component.literal(l.get(selected).name() + " chooses its designs by itself"), true);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int rows = (imageHeight - 24) / ROW_H;
        scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, tribes().size() - rows));
        return true;
    }
}
