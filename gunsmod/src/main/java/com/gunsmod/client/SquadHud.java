package com.gunsmod.client;

import com.gunsmod.entity.ai.Squad;
import com.gunsmod.net.Net;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The squad strip in the top left corner while the player leads soldiers: size, health, order, formation. */
public final class SquadHud {
    private SquadHud() {}

    public static int count, order, formation;
    public static float health;
    public static boolean holdFire;
    private static long seen;

    public static void status(Net.SquadStatus s) {
        count = s.count();
        health = s.health();
        order = s.order();
        formation = s.formation();
        holdFire = s.holdFire();
        Minecraft mc = Minecraft.getInstance();
        seen = mc.level != null ? mc.level.getGameTime() : 0;
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (count <= 0 || mc.level == null || mc.options.hideGui || mc.level.getGameTime() - seen > 80) return;
        Squad.Order o = Squad.Order.values()[Math.floorMod(order, Squad.Order.values().length)];
        Squad.Formation f = Squad.Formation.values()[Math.floorMod(formation, Squad.Formation.values().length)];
        Component line = Component.translatable("gunsmod.squad.hud", count, Component.translatable("gunsmod.squad.order." + o.name().toLowerCase()),
                Component.translatable("gunsmod.squad.formation." + f.name().toLowerCase()));
        int x = 6, y = 6, w = mc.font.width(line) + 12;
        g.fill(x, y, x + w, y + 22, 0x90101418);
        g.fill(x, y, x + 2, y + 22, holdFire ? 0xFFE0B040 : 0xFF7FD87F);
        g.drawString(mc.font, line, x + 7, y + 3, 0xFFE8E2C8, true);
        int bw = w - 14;
        g.fill(x + 7, y + 15, x + 7 + bw, y + 18, 0xFF303030);
        g.fill(x + 7, y + 15, x + 7 + (int) (bw * Math.max(0, Math.min(1, health))), y + 18, health > 0.5f ? 0xFF7FD87F : health > 0.25f ? 0xFFE0B040 : 0xFFFF6A5A);
        if (holdFire) g.drawString(mc.font, Component.translatable("gunsmod.squad.hold_fire_short"), x + w + 6, y + 3, 0xFFE0B040, true);
    }
}
