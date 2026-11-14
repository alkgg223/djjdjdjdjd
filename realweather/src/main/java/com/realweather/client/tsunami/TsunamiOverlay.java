package com.realweather.client.tsunami;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Murky water over the view while you are under the tsunami's water (or the water filling a car). */
public final class TsunamiOverlay {
    private TsunamiOverlay() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        float s = ClientTsunamis.submerged;
        if (s <= 0.01F || Minecraft.getInstance().player == null) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        int a = Math.round(150 * s);
        g.fill(0, 0, w, h, (a << 24) | 0x29505A);
        // Darker towards the edges, as through a diving mask of dirty water.
        int edge = Math.round(90 * s);
        int bw = Math.max(1, w / 6), bh = Math.max(1, h / 5);
        g.fillGradient(0, 0, w, bh, (edge << 24) | 0x10262C, 0x0010262C);
        g.fillGradient(0, h - bh, w, h, 0x0010262C, (edge << 24) | 0x10262C);
    }
}
