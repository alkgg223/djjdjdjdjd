package com.rpbusiness.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared look of all management screens: dark panel, title bar, big touch-friendly buttons. */
public abstract class BaseScreen extends Screen {
    public static final int BG = 0xF01B1F27, PANEL = 0xFF262B36, PANEL2 = 0xFF2F3542, ACCENT = 0xFFE8A33D, LINE = 0xFF3D4455,
            TEXT = 0xFFECEFF4, DIM = 0xFF9AA3B5, GOOD = 0xFF7BD88F, BAD = 0xFFFF6B6B, WARN = 0xFFFFC857;

    protected int left, top, w, h;
    protected int scroll;

    protected BaseScreen(Component title) { super(title); }

    protected void layout(int maxW, int maxH) {
        w = Math.min(width - 12, maxW);
        h = Math.min(height - 12, maxH);
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    protected Button button(int x, int y, int bw, int bh, Component text, Runnable r) {
        return addRenderableWidget(Button.builder(text, b -> r.run()).bounds(x, y, bw, bh).build());
    }

    protected Button button(int x, int y, int bw, Component text, Runnable r) { return button(x, y, bw, 18, text, r); }

    protected void panel(GuiGraphics g, int x, int y, int pw, int ph, int color) {
        g.fill(x, y, x + pw, y + ph, color);
        g.fill(x, y, x + pw, y + 1, LINE);
        g.fill(x, y + ph - 1, x + pw, y + ph, LINE);
        g.fill(x, y, x + 1, y + ph, LINE);
        g.fill(x + pw - 1, y, x + pw, y + ph, LINE);
    }

    protected void frame(GuiGraphics g, Component titleText) {
        g.fill(0, 0, width, height, 0x88000000);
        panel(g, left, top, w, h, BG);
        g.fill(left + 1, top + 1, left + w - 1, top + 20, PANEL2);
        g.fill(left + 1, top + 20, left + w - 1, top + 21, ACCENT);
        g.drawString(font, titleText, left + 8, top + 6, ACCENT, false);
    }

    protected void text(GuiGraphics g, String s, int x, int y, int color) { g.drawString(font, s, x, y, color, false); }
    protected void text(GuiGraphics g, Component s, int x, int y, int color) { g.drawString(font, s, x, y, color, false); }

    protected String clip(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        while (s.length() > 1 && font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int before = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(sy));
        if (before != scroll) rebuildWidgets();
        return true;
    }
}
