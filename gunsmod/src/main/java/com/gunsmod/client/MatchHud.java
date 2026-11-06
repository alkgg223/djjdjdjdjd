package com.gunsmod.client;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.game.GameMode;
import com.gunsmod.net.Net;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The battle on the screen: at the top the mode, the clock and both sides' tickets or score, the capture points as
 * letters in their owner's colour with how far they are taken; down the left the side's mission (optional objectives
 * in grey, done ones ticked, failed ones crossed); and over the world a marker on every objective with its distance.
 * Small and out of the way of the normal HUD.
 */
public final class MatchHud {
    private MatchHud() {}

    public static Net.MatchState state;
    private static long received;

    public static void state(Net.MatchState s) {
        Minecraft mc = Minecraft.getInstance();
        state = s;
        received = mc.level == null ? 0 : mc.level.getGameTime();
        if (!s.banner().isEmpty()) {
            Component title = s.winner() >= 0 ? (s.winner() == 2 ? Component.translatable("gunsmod.match.draw")
                    : Component.translatable("gunsmod.match.wins", Component.translatable("gunsmod.team." + BotTeam.byId(s.winner()).name().toLowerCase()))
                    .withStyle(BotTeam.byId(s.winner()).chat))
                    : Component.translatable("gunsmod.mode." + GameMode.values()[Mth.clamp(s.mode(), 0, GameMode.values().length - 1)].id());
            mc.gui.setTimes(10, 70, 20);
            mc.gui.setTitle(title);
            mc.gui.setSubtitle(Component.translatable(s.banner()));
        }
        if (s.open()) {
            if (mc.screen instanceof com.gunsmod.client.screen.MatchScreen ms) ms.update(s);
            else if (mc.screen == null) mc.setScreen(new com.gunsmod.client.screen.MatchScreen(s));
        } else if (mc.screen instanceof com.gunsmod.client.screen.MatchScreen ms) {
            ms.update(s);
        }
    }

    /** a battle the player is in (the last word from the server is recent) */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (state == null || mc.level == null) return false;
        long age = mc.level.getGameTime() - received;
        return state.running() ? age < 80 : state.winner() >= 0 && age < 300;
    }

    private static int teamColour(int t) {
        return t < 0 ? 0xFFD8D8D8 : t == 1 ? 0xFFFF5A48 : 0xFF6EE05A;
    }

    private static String time(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public static void layer(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui || DroneClient.piloting() || mc.player == null) return;
        Net.MatchState s = state;
        Font f = mc.font;
        int w = g.guiWidth();
        GameMode mode = GameMode.values()[Mth.clamp(s.mode(), 0, GameMode.values().length - 1)];
        boolean pts = mode.points();
        // the top bar: green on the left, red on the right, the clock in the middle
        int cx = w / 2, top = 2;
        int bw = Math.min(90, w / 5);
        g.fill(cx - bw - 34, top, cx + bw + 34, top + 22, 0x90000000);
        String clock = time(s.timeLeft());
        Component modeName = Component.translatable("gunsmod.mode." + mode.id());
        g.drawString(f, clock, cx - f.width(clock) / 2, top + 3, s.timeLeft() < 1200 && s.running() ? 0xFFFF6050 : 0xFFFFFFFF, true);
        g.pose().pushPose();
        g.pose().translate(cx - f.width(modeName) * 0.35f, top + 13, 0);
        g.pose().scale(0.7f, 0.7f, 1f);
        g.drawString(f, modeName, 0, 0, 0xFFE0B040, true);
        g.pose().popPose();
        int gv, rv, max;
        if (mode.tickets()) {
            gv = s.ticketsG();
            rv = s.ticketsR();
            max = Math.max(1, s.limit());
        } else {
            gv = s.scoreG();
            rv = s.scoreR();
            max = Math.max(1, mode.attackDefend() ? Math.max(gv, rv) : s.limit());
        }
        float gfr = Mth.clamp(gv / (float) max, 0, 1), rfr = Mth.clamp(rv / (float) max, 0, 1);
        g.fill(cx - bw - 30, top + 4, cx - 30, top + 10, 0x60000000);
        g.fill(cx - 30 - Math.round(bw * gfr), top + 4, cx - 30, top + 10, 0xFF5BBF4A);
        g.fill(cx + 30, top + 4, cx + bw + 30, top + 10, 0x60000000);
        g.fill(cx + 30, top + 4, cx + 30 + Math.round(bw * rfr), top + 10, 0xFFD9483B);
        g.drawString(f, String.valueOf(gv), cx - 30 - f.width(String.valueOf(gv)) - 2, top + 12, 0xFF8FEF7F, true);
        g.drawString(f, String.valueOf(rv), cx + 32, top + 12, 0xFFFF8070, true);
        // the capture points
        int y = top + 25;
        if (pts) {
            int n = 0;
            for (Net.ObjInfo o : s.objectives()) if (o.kind() == 0) n++;
            int x = cx - n * 9;
            for (Net.ObjInfo o : s.objectives()) {
                if (o.kind() != 0) continue;
                int col = teamColour(o.team());
                g.fill(x, y, x + 16, y + 14, 0xA0000000);
                g.fill(x, y + 12, x + 16, y + 14, 0x60FFFFFF);
                float b = o.progress();
                int pw = Math.round(Math.abs(b) * 16);
                g.fill(x, y + 12, x + pw, y + 14, b > 0 ? 0xFF5BBF4A : 0xFFD9483B);
                String l = String.valueOf((char) o.label());
                g.drawString(f, l, x + 8 - f.width(l) / 2, y + 2, col, true);
                x += 18;
            }
            y += 17;
        }
        if (mode == GameMode.CTF) {
            for (Net.ObjInfo o : s.objectives()) {
                if (o.kind() != 1 || (o.flags() & 6) == 0) continue;
                Component c = Component.translatable((o.flags() & 2) != 0 ? "gunsmod.hud.flag_carried" : "gunsmod.hud.flag_dropped",
                        Component.translatable("gunsmod.team." + BotTeam.byId(o.team()).name().toLowerCase()));
                g.drawString(f, c, cx - f.width(c) / 2, y, teamColour(o.team()), true);
                y += 10;
            }
        }
        for (Net.ObjInfo o : s.objectives()) {
            if (o.kind() == 2 && (o.flags() & 8) != 0) {
                Component c = Component.translatable("gunsmod.hud.charge", String.valueOf((char) o.label()), o.charge() / 20);
                g.drawString(f, c, cx - f.width(c) / 2, y, ((mc.level.getGameTime() / 5) % 2 == 0) ? 0xFFFF4030 : 0xFFFFC040, true);
                y += 10;
            }
        }
        // the mission, down the left
        int ly = 40;
        g.drawString(f, Component.translatable("gunsmod.hud.mission"), 4, ly, 0xFFE0B040, true);
        ly += 11;
        for (Net.MissionLine l : s.lines()) {
            String mark = l.state() == 1 ? "✔ " : l.state() == 2 ? "✖ " : l.optional() ? "○ " : "▶ ";
            int col = l.state() == 1 ? 0xFF7FD87F : l.state() == 2 ? 0xFFFF6A5A : l.optional() ? 0xFFA0A8A0 : 0xFFFFFFFF;
            Component c = Component.literal(mark).append(Component.translatable(l.key(), l.arg()));
            g.pose().pushPose();
            g.pose().translate(4, ly, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawString(f, c, 0, 0, col, true);
            g.pose().popPose();
            ly += 9;
        }
        if (s.running()) markers(g, f, s, dt);
    }

    /** a marker over each objective in the world, kept at the edge of the screen when it is off to the side */
    private static void markers(GuiGraphics g, Font f, Net.MatchState s, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        int w = g.guiWidth(), h = g.guiHeight();
        double fov = Math.toRadians(mc.options.fov().get());
        double scale = (h / 2.0) / Math.tan(fov / 2);
        for (Net.ObjInfo o : s.objectives()) {
            if (o.kind() == 4) continue;
            double dx = o.x() - eye.x, dy = o.y() + (o.kind() == 0 ? 3.5 : 1.5) - eye.y, dz = o.z() - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 4 || dist > 600) continue;
            double cz = dx * look.x() + dy * look.y() + dz * look.z();
            double cxv = dx * left.x() + dy * left.y() + dz * left.z();
            double cyv = dx * up.x() + dy * up.y() + dz * up.z();
            double sx, sy;
            if (cz > 0.1) {
                sx = w / 2.0 - cxv / cz * scale;
                sy = h / 2.0 - cyv / cz * scale;
            } else {
                double a = Math.atan2(-cyv, -cxv);
                sx = w / 2.0 + Math.cos(a) * w;
                sy = h / 2.0 + Math.sin(a) * h;
            }
            sx = Mth.clamp(sx, 12, w - 12);
            sy = Mth.clamp(sy, 40, h - 30);
            int ix = (int) sx, iy = (int) sy;
            int col = o.kind() == 5 ? 0xFF60C0FF : teamColour(o.team());
            String l = o.kind() == 0 ? String.valueOf((char) o.label()) : o.kind() == 1 ? "⚑" : o.kind() == 2 ? "⌖" : o.kind() == 3 ? "⌂" : "▣";
            for (int k = 0; k <= 5; k++) {
                g.fill(ix - 6 + k, iy - k, ix + 7 - k, iy - k + 1, 0x90000000);
                g.fill(ix - 6 + k, iy + k, ix + 7 - k, iy + k + 1, 0x90000000);
            }
            g.drawString(f, l, ix - f.width(l) / 2 + 1, iy - 4, col, true);
            String d = Math.round(dist) + "m";
            g.pose().pushPose();
            g.pose().translate(ix - f.width(d) * 0.3f, iy + 7, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(f, d, 0, 0, 0xFFE0E0E0, true);
            g.pose().popPose();
        }
    }
}
