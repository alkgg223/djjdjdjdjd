package com.gunsmod.client;

import com.gunsmod.client.screen.TeamScreen;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.net.Net;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The battle seen from the player's side: the kill score of both teams in a bar across the top of the screen (shown
 * on a battlefield or once a side is picked), and the side picker the server asks for.
 */
public final class BattleHud {
    private BattleHud() {}

    private static int green, red, team = -1;
    private static boolean field, inside, pickerWanted;
    private static long lastUpdate;
    /** a team's score just went up: its number pulses */
    private static int pulseGreen, pulseRed;

    public static void update(Net.BattleState p) {
        boolean fresh = System.currentTimeMillis() - lastUpdate < 3000;
        if (fresh && p.green() > green) pulseGreen = 16;
        if (fresh && p.red() > red) pulseRed = 16;
        green = p.green();
        red = p.red();
        team = p.team();
        field = p.field();
        inside = p.inside();
        lastUpdate = System.currentTimeMillis();
        if (p.openPicker()) pickerWanted = true;
    }

    /** this dimension has a battlefield (the side picker can be opened) */
    public static boolean field() {
        return field && System.currentTimeMillis() - lastUpdate < 3000;
    }

    public static int team() {
        return System.currentTimeMillis() - lastUpdate < 3000 ? team : -1;
    }

    public static int score(int t) {
        return t == 1 ? red : green;
    }

    public static void tick() {
        if (pulseGreen > 0) pulseGreen--;
        if (pulseRed > 0) pulseRed--;
        Minecraft mc = Minecraft.getInstance();
        if (pickerWanted && mc.player != null && mc.screen == null) {
            pickerWanted = false;
            mc.setScreen(new TeamScreen());
        }
    }

    private static boolean shown(Minecraft mc) {
        if (mc.player == null || mc.options.hideGui || System.currentTimeMillis() - lastUpdate > 3000) return false;
        return inside || team >= 0;
    }

    /** the score bar: green on the left, red on the right, the lead as a split bar beneath */
    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc)) return;
        Font f = mc.font;
        int w = 200, h = 30, x = (g.guiWidth() - w) / 2, y = 4, mid = x + w / 2;
        int gc = 0xFF000000 | BotTeam.GREEN.color, rc = 0xFF000000 | BotTeam.RED.color;
        // body: dark glass with the team colours fading in from either end
        g.fillGradient(x, y, x + w, y + h, 0xD0141A1E, 0xC0262E34);
        g.fill(x, y, x + 4, y + h, gc);
        g.fill(x + w - 4, y, x + w, y + h, rc);
        g.fillGradient(x + 4, y, x + 60, y + h, 0x30000000 | BotTeam.GREEN.color, 0x05000000 | BotTeam.GREEN.color);
        g.fillGradient(x + w - 60, y, x + w - 4, y + h, 0x30000000 | BotTeam.RED.color, 0x05000000 | BotTeam.RED.color);
        g.fill(x, y, x + w, y + 1, 0x40FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x80000000);
        // team names
        Component gn = Component.translatable("gunsmod.team.green"), rn = Component.translatable("gunsmod.team.red");
        g.drawString(f, gn, x + 9, y + 4, gc, true);
        g.drawString(f, rn, x + w - 9 - f.width(rn), y + 4, rc, true);
        // the kills, big; a pulse when one goes up
        number(g, f, String.valueOf(green), mid - 22, y + 8, true, pulseGreen, gc);
        number(g, f, String.valueOf(red), mid + 22, y + 8, false, pulseRed, rc);
        // centre badge
        g.fill(mid - 11, y + 7, mid + 11, y + 23, 0xC0000000);
        g.fill(mid - 11, y + 7, mid + 11, y + 8, 0x50FFFFFF);
        String vs = "VS";
        g.drawString(f, vs, mid - f.width(vs) / 2, y + 11, 0xFFE0B040, true);
        // who leads: the bar splits by the share of kills
        int total = green + red, bw = w - 8, bx = x + 4, by = y + h + 2;
        int gw = total == 0 ? bw / 2 : Math.round(bw * green / (float) total);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, 0xA0000000);
        g.fill(bx, by, bx + gw, by + 3, gc);
        g.fill(bx + gw, by, bx + bw, by + 3, rc);
        g.fill(bx + gw - 1, by - 1, bx + gw + 1, by + 4, 0xFFFFFFFF);
        // the player's own side, or how to pick one
        Component under;
        int col;
        if (team >= 0) {
            BotTeam t = BotTeam.byId(team);
            under = Component.translatable("gunsmod.hud.you", Component.translatable("gunsmod.team." + t.name().toLowerCase()));
            col = 0xFF000000 | t.color;
        } else {
            under = Component.translatable("gunsmod.hud.pick_hint", GunClient.TEAM.getTranslatedKeyMessage());
            col = 0xFFE0B040;
        }
        g.drawString(f, under, mid - f.width(under) / 2, by + 7, col, true);
    }

    private static void number(GuiGraphics g, Font f, String s, int x, int y, boolean right, int pulse, int col) {
        float sc = 2f + (pulse > 0 ? Mth.sin(pulse / 16f * Mth.PI) * 0.6f : 0f);
        int tw = f.width(s);
        g.pose().pushPose();
        g.pose().translate(x, y + 7, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(f, s, right ? -tw : 0, -4, pulse > 0 ? 0xFFFFFFFF : col, true);
        g.pose().popPose();
    }
}
