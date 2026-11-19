package com.vehicleworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.vehicleworks.Config;
import com.vehicleworks.physics.propulsion.EngineSim;
import com.vehicleworks.physics.propulsion.EngineType;
import com.vehicleworks.vehicle.ControlProfile;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import com.vehicleworks.weapon.WeaponSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Head-up flight / driving instruments shown while seated in a vehicle. */
public final class VehicleHud {
    private static final int GREEN = 0xFF7CFF8A, AMBER = 0xFFFFC040, RED = 0xFFFF4040, WHITE = 0xFFEFF6FF, DIM = 0x99FFFFFF, PANEL = 0x88101820;

    private VehicleHud() {}

    public static boolean isFlying(VehicleEntity v) {
        return ControlProfile.byIndex(v.telemetry.profile) == ControlProfile.AIRCRAFT;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        VehicleEntity v = ClientEvents.ridden();
        if (mc.options.hideGui) return;
        if (v == null) { announcement(g, mc.font, g.guiWidth(), g.guiHeight()); return; } // standing in the cabin hears the PA too
        VehicleTelemetry t = v.telemetry;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float scale = Config.CLIENT.hudScale.get().floatValue();
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        w = (int) (w / scale);
        h = (int) (h / scale);
        ControlProfile p = ControlProfile.byIndex(t.profile);
        boolean pilot = v.seats.getOrDefault(mc.player.getUUID(), Long.MIN_VALUE) != Long.MIN_VALUE;
        announcement(g, font, w, h);
        if (v.isAttendantSeated(mc.player)) {
            attendant(g, font, v, w, h);
            g.pose().popPose();
            return;
        }
        if (v.isPassengerSeated(mc.player)) {
            // passengers: no flight HUD, instruments or controls; only their seat
            passenger(g, font, v, w, h);
            g.pose().popPose();
            return;
        }
        boolean visible = Config.CLIENT.hudVisible.get();
        int mode = Config.CLIENT.hudMode.get();
        boolean air = p == ControlProfile.AIRCRAFT || p == ControlProfile.HELICOPTER || p == ControlProfile.AIRSHIP || p == ControlProfile.AUTO;
        // the aircraft HUD is an avionics display: no power, no HUD; it aligns at the end of the power-up
        float hudA = air ? Avionics.hud(v) * Config.CLIENT.hudBrightness.get().floatValue() : 1;
        if (air && visible && Avionics.stage(v) >= Avionics.GLOW && Avionics.hud(v) < 1 && t.avionicsPowered()) {
            float f = Avionics.progress(v);
            String m = f < 0.88f ? String.format("HUD ALIGN  %.0f%%", Math.min(99, f / 0.88f * 100)) : "HUD";
            g.drawCenteredString(font, m, w / 2, h / 2 - 40, 0xFF40FF70);
        }
        if (air && hudA <= 0.02f) visible = false;
        if (hudA < 0.999f) g.setColor(1, 1, 1, Math.max(0.02f, hudA));
        if (visible) {
            switch (p) {
                case AIRCRAFT, HELICOPTER, AIRSHIP, AUTO -> {
                    switch (mode) {
                        case 1 -> minimal(g, font, t, w, h);
                        case 2 -> military(g, font, t, w, h);
                        case 3 -> navigation(g, font, t, w, h);
                        default -> flight(g, font, v, t, w, h, p);
                    }
                }
                case CAR -> car(g, font, t, w, h);
                case SHIP, SUBMARINE -> ship(g, font, t, w, h, p == ControlProfile.SUBMARINE);
            }
            if (!air || mode == 0) engines(g, font, t, w, h);
            weapons(g, font, t, w, h);
        }
        g.setColor(1, 1, 1, 1);
        warnings(g, font, t, w, h, p);
        if (!VoiceCallouts.active.isEmpty()) {
            int yy = 32;
            boolean on = (System.currentTimeMillis() / 350) % 2 == 0;
            for (Warnings.W wn : VoiceCallouts.active) {
                String m = net.minecraft.network.chat.Component.translatable(wn.key()).getString();
                int col = wn.red ? (on ? 0xFFFF3030 : 0xFFB02020) : 0xFFFFB020;
                g.fill(w / 2 - 70, yy - 2, w / 2 + 70, yy + 9, 0xA0000000);
                g.drawCenteredString(font, m, w / 2, yy, col);
                yy += 12;
            }
        }
        if (VoiceCallouts.blink()) {
            // flashing red screen edge = master warning
            int c = 0x90FF1010, e = 6;
            g.fill(0, 0, w, e, c); g.fill(0, h - e, w, h, c); g.fill(0, e, e, h - e, c); g.fill(w - e, e, w, h - e, c);
            String m = net.minecraft.network.chat.Component.translatable("vehicleworks.hud.master_warning").getString();
            g.fill(w / 2 - font.width(m) / 2 - 5, 12, w / 2 + font.width(m) / 2 + 5, 26, 0xD0C00000);
            g.drawCenteredString(font, m, w / 2, 15, 0xFFFFFFFF);
        }
        g.pose().popPose();
    }

    private static int paId = -1;
    private static long paUntil;

    /** a cabin announcement: shown as a caption for everyone on board (and spoken when voice is on) */
    public static void announce(int id) {
        paId = id;
        paUntil = System.currentTimeMillis() + 9000;
        if (VoiceCallouts.enabled()) VoiceCallouts.say("vehicleworks.pa.text." + id, true);
    }

    static void announcement(GuiGraphics g, Font f, int w, int h) {
        if (paId < 0 || System.currentTimeMillis() > paUntil) return;
        String head = net.minecraft.network.chat.Component.translatable("vehicleworks.pa.name." + paId).getString();
        String body = net.minecraft.network.chat.Component.translatable("vehicleworks.pa.text." + paId).getString();
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = f.split(net.minecraft.network.chat.Component.literal(body), 300);
        int y = h / 4, bh = 16 + lines.size() * 10;
        g.fill(w / 2 - 160, y - 4, w / 2 + 160, y + bh, 0xB0101418);
        g.drawCenteredString(f, "\u266A  " + head, w / 2, y, 0xFFE8C878);
        for (int i = 0; i < lines.size(); i++) g.drawCenteredString(f, lines.get(i), w / 2, y + 13 + i * 10, 0xFFE8EDF2);
    }

    /** cabin crew seat: calls, doors, signs and how to reach the crew panel / announcements */
    private static void attendant(GuiGraphics g, Font f, VehicleEntity v, int w, int h) {
        VehicleTelemetry t = v.telemetry;
        int calls = 0;
        for (var e : v.cabin.long2IntEntrySet()) if (com.vehicleworks.vehicle.CabinSeat.call(e.getIntValue())) calls++;
        int y = h - 58;
        g.fill(w / 2 - 130, y - 4, w / 2 + 130, y + 34, 0x90101418);
        g.drawCenteredString(f, net.minecraft.network.chat.Component.translatable("vehicleworks.crew.seat"), w / 2, y, 0xFFE8C878);
        g.drawCenteredString(f, net.minecraft.network.chat.Component.translatable("vehicleworks.crew.hud", calls, t.doors != 0 ? "OPEN" : "CLOSED", t.seatbelt ? "ON" : "OFF"), w / 2, y + 11, calls > 0 ? 0xFF38D3FF : 0xFFB8C2CC);
        g.drawCenteredString(f, net.minecraft.network.chat.Component.translatable("vehicleworks.crew.hint", Keybinds.PANEL.getTranslatedKeyMessage(), Keybinds.ANNOUNCE.getTranslatedKeyMessage()), w / 2, y + 22, 0xFF9AA4AE);
    }

    /** Passenger seat: just the seat and how to adjust it. */
    private static void passenger(GuiGraphics g, Font f, VehicleEntity v, int w, int h) {
        int cls = v.seatClassOf(Minecraft.getInstance().player);
        String seat = net.minecraft.network.chat.Component.translatable(cls > 0 ? "vehicleworks.cabin.class." + cls : "vehicleworks.cabin.passenger").getString();
        String hint = net.minecraft.network.chat.Component.translatable(cls > 0 ? "vehicleworks.cabin.hint" : "vehicleworks.cabin.hint_plain",
                Keybinds.PANEL.getTranslatedKeyMessage()).getString();
        int y = h - 46;
        g.fill(w / 2 - 110, y - 4, w / 2 + 110, y + 22, 0x80101418);
        g.drawCenteredString(f, seat, w / 2, y, 0xFFE8EDF2);
        g.drawCenteredString(f, hint, w / 2, y + 11, 0xFF9AA4AE);
    }

    // ------------------------------------------------------------------ HUD styles

    private static String gearFlaps(VehicleTelemetry t) {
        return (t.gearDown ? "GEAR DN" : "GEAR UP") + String.format("  FLAPS %.0f%%", t.flaps * 100);
    }

    private static float avgRpm(VehicleTelemetry t) {
        float s = 0; int n = 0;
        for (int i = 0; i < t.engineSpool.length; i++) { s += t.engineSpool[i]; n++; }
        return n > 0 ? s / n : 0;
    }

    private static float totalThrust(VehicleTelemetry t) {
        float s = 0;
        for (float x : t.engineThrust) s += x;
        return s;
    }

    /** MINIMAL: one compact strip with the essentials. */
    private static void minimal(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h) {
        int cx = w / 2, y = h - 52;
        String l1 = String.format("%.0f km/h   ALT %.0f   VS %+.1f   HDG %03.0f", t.airspeed * 3.6f, t.altitude, t.verticalSpeed, t.heading);
        String l2 = String.format("THR %.0f%%   FUEL %.0f%%   ", t.throttle * 100, t.fuelCapacity > 0 ? 100 * t.fuel / t.fuelCapacity : 0) + gearFlaps(t) + (t.autopilot ? "   AP" : "");
        int bw = Math.max(f.width(l1), f.width(l2)) / 2 + 6;
        g.fill(cx - bw, y - 4, cx + bw, y + 22, 0x88000000);
        centered(g, f, l1, cx, y, t.stall ? RED : GREEN);
        centered(g, f, l2, cx, y + 11, DIM);
    }

    /** MILITARY: monochrome green, pitch ladder, flight-path marker, boxed speed/altitude. */
    private static void military(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h) {
        final int C = 0xFF44FF66;
        int cx = w / 2, cy = h / 2;
        float ppd = 6f; // pixels per degree
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.enableScissor(cx - 120, cy - 90, cx + 120, cy + 90);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-t.bank));
        for (int d = -60; d <= 60; d += 5) {
            float yy = (t.pitch - d) * ppd;
            if (Math.abs(yy) > 110) continue;
            int len = d == 0 ? 110 : (d % 10 == 0 ? 34 : 18);
            if (d >= 0) { g.fill(-len, (int) yy, -12, (int) yy + 1, C); g.fill(12, (int) yy, len, (int) yy + 1, C); }
            else for (int x = 12; x < len; x += 6) { g.fill(-x - 3, (int) yy, -x, (int) yy + 1, C); g.fill(x, (int) yy, x + 3, (int) yy + 1, C); }
            if (d != 0 && d % 10 == 0) { text(g, f, String.valueOf(d), len + 3, (int) yy - 4, C); text(g, f, String.valueOf(d), -len - 3 - f.width(String.valueOf(d)), (int) yy - 4, C); }
        }
        g.pose().popPose();
        g.disableScissor();
        // boresight and flight-path marker (velocity vector)
        g.fill(cx - 6, cy, cx - 2, cy + 1, C); g.fill(cx + 3, cy, cx + 7, cy + 1, C); g.fill(cx, cy - 6, cx + 1, cy - 2, C);
        int fx = cx + (int) Mth.clamp(t.sideslip * ppd, -100, 100), fy = cy + (int) Mth.clamp(t.aoa * ppd, -80, 80);
        g.renderOutline(fx - 4, fy - 4, 9, 9, C);
        g.fill(fx - 12, fy, fx - 4, fy + 1, C); g.fill(fx + 5, fy, fx + 13, fy + 1, C); g.fill(fx, fy - 9, fx + 1, fy - 4, C);
        // speed and altitude boxes
        g.renderOutline(cx - 150, cy - 7, 44, 15, C);
        text(g, f, String.format("%4.0f", t.airspeed * 3.6f), cx - 146, cy - 3, C);
        g.renderOutline(cx + 106, cy - 7, 48, 15, C);
        text(g, f, String.format("%5.0f", t.altitude), cx + 110, cy - 3, C);
        text(g, f, String.format("M %.2f", t.mach), cx - 150, cy + 12, C);
        text(g, f, String.format("G %.1f", t.gLoad), cx - 150, cy + 23, C);
        text(g, f, String.format("α %.1f", t.aoa), cx - 150, cy + 34, C);
        text(g, f, String.format("VS %+.0f", t.verticalSpeed), cx + 106, cy + 12, C);
        text(g, f, String.format("R %.0f", t.agl), cx + 106, cy + 23, C);
        // heading scale
        int hy = cy - 112;
        for (int d = -40; d <= 40; d += 5) {
            float hd = (float) (Math.floor(t.heading / 5) * 5 + d);
            int xx = cx + (int) ((hd - t.heading) * 2.4f);
            if (Math.abs(xx - cx) > 96) continue;
            int n = Math.floorMod(Math.round(hd), 360);
            g.fill(xx, hy, xx + 1, hy + (n % 10 == 0 ? 6 : 3), C);
            if (n % 10 == 0) centered(g, f, String.format("%02d", n / 10), xx, hy - 10, C);
        }
        g.fill(cx, hy + 7, cx + 1, hy + 12, C);
        centered(g, f, String.format("THR %.0f%%%s   %s", t.throttle * 100, t.afterburner ? " AB" : "", gearFlaps(t)), cx, cy + 96, C);
        if (t.autopilot) centered(g, f, String.format("AP %03.0f / %.0f", t.apHeading, t.apAltitude), cx, cy + 108, C);
        if (t.armed) centered(g, f, "ARM", cx, cy + 120, 0xFFFF5040);
    }

    /** NAVIGATION: compass rose with heading bug, waypoint pointer and route data. */
    private static void navigation(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h) {
        int cx = w / 2, cy = h - 92, r = 62;
        g.fill(cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4, 0x66000000);
        for (int d = 0; d < 360; d += 10) {
            double a = Math.toRadians(d - t.heading - 90);
            int len = d % 30 == 0 ? 8 : 4;
            for (int k = 0; k < len; k++) {
                int x = cx + (int) Math.round(Math.cos(a) * (r - k)), y = cy + (int) Math.round(Math.sin(a) * (r - k));
                g.fill(x, y, x + 1, y + 1, WHITE);
            }
            if (d % 30 == 0) {
                String lbl = switch (d) { case 0 -> "N"; case 90 -> "E"; case 180 -> "S"; case 270 -> "W"; default -> String.valueOf(d / 10); };
                centered(g, f, lbl, cx + (int) (Math.cos(a) * (r - 16)), cy + (int) (Math.sin(a) * (r - 16)) - 4, d % 90 == 0 ? AMBER : DIM);
            }
        }
        // aircraft symbol
        g.fill(cx - 8, cy, cx + 9, cy + 2, AMBER); g.fill(cx, cy - 8, cx + 2, cy + 10, AMBER);
        if (t.autopilot) {
            double a = Math.toRadians(t.apHeading - t.heading - 90);
            int x = cx + (int) (Math.cos(a) * (r + 1)), y = cy + (int) (Math.sin(a) * (r + 1));
            g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF38D3FF);
        }
        if (t.navActive) {
            double a = Math.toRadians(t.navBearing - t.heading - 90);
            for (int k = 10; k < r - 4; k += 2) {
                int x = cx + (int) (Math.cos(a) * k), y = cy + (int) (Math.sin(a) * k);
                g.fill(x, y, x + 2, y + 2, 0xFFFF55FF);
            }
        }
        int tx = cx + r + 16, ty = cy - r;
        panel(g, tx - 4, ty - 4, tx + 110, ty + 86);
        text(g, f, String.format("HDG  %03.0f°", t.heading), tx, ty, WHITE);
        text(g, f, String.format("GS   %.0f km/h", t.groundSpeed * 3.6f), tx, ty + 11, GREEN);
        text(g, f, String.format("TAS  %.0f km/h", t.airspeed * 3.6f), tx, ty + 22, GREEN);
        text(g, f, String.format("ALT  %.0f", t.altitude), tx, ty + 33, GREEN);
        text(g, f, String.format("VS   %+.1f m/s", t.verticalSpeed), tx, ty + 44, DIM);
        if (t.navActive) {
            float eta = t.groundSpeed > 1 ? t.navDistance / t.groundSpeed : 0;
            text(g, f, String.format("WPT  %03.0f° %.1f km", t.navBearing, t.navDistance / 1000f), tx, ty + 55, 0xFFFF88FF);
            text(g, f, String.format("ETA  %d:%02d", (int) eta / 60, (int) eta % 60), tx, ty + 66, 0xFFFF88FF);
        } else text(g, f, "WPT  ---", tx, ty + 55, DIM);
        if (t.autopilot) text(g, f, String.format("AP   %03.0f / %.0f", t.apHeading, t.apAltitude), tx, ty + 77, 0xFF38D3FF);
        int lx = cx - r - 130;
        panel(g, lx - 4, ty - 4, lx + 110, ty + 42);
        text(g, f, String.format("FUEL %.0f kg", t.fuel), lx, ty, t.fuel < t.fuelCapacity * 0.15 ? RED : AMBER);
        text(g, f, String.format("FLOW %.2f kg/s", t.fuelFlow), lx, ty + 11, DIM);
        float rng = t.fuelFlow > 1e-3 ? t.fuel / t.fuelFlow * t.groundSpeed / 1000f : 0;
        text(g, f, String.format("RANGE %.0f km", rng), lx, ty + 22, DIM);
        text(g, f, gearFlaps(t), lx, ty + 33, DIM);
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, 0x55FFFFFF);
    }

    private static void text(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x, y, c, true);
    }

    private static void centered(GuiGraphics g, Font f, String s, int x, int y, int c) {
        g.drawString(f, s, x - f.width(s) / 2, y, c, true);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x, y, x + w, y + h, 0x66000000);
        int fh = (int) (h * Mth.clamp(frac, 0, 1));
        g.fill(x, y + h - fh, x + w, y + h, color);
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x55FFFFFF);
    }

    private static void flight(GuiGraphics g, Font f, VehicleEntity v, VehicleTelemetry t, int w, int h, ControlProfile p) {
        int cx = w / 2, cy = h / 2;
        boolean full = t.instruments;
        // attitude indicator (skipped when the cockpit has real instruments, so they stay visible)
        boolean cockpitGauges = false;
        for (long k : v.structure.blockEntities().keySet()) {
            var ct = com.vehicleworks.vehicle.VehicleBuilder.typeOf(v.structure.get(k));
            if (ct != null && ct.isInstrumentPanel()) { cockpitGauges = true; break; }
        }
        if (full && !cockpitGauges) {
            int r = 46;
            int ax = cx, ay = h - 70;
            g.pose().pushPose();
            g.pose().translate(ax, ay, 0);
            g.enableScissor(ax - r, ay - r, ax + r, ay + r);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-t.bank));
            float pitchPx = t.pitch * 1.6f;
            g.fill(-r * 2, (int) pitchPx, r * 2, r * 3, 0xCC6B4A2B);
            g.fill(-r * 2, -r * 3, r * 2, (int) pitchPx, 0xCC2C6FB8);
            g.fill(-r * 2, (int) pitchPx, r * 2, (int) pitchPx + 1, WHITE);
            for (int d = -30; d <= 30; d += 10) {
                if (d == 0) continue;
                int yy = (int) (pitchPx - d * 1.6f);
                int len = d % 20 == 0 ? 14 : 8;
                g.fill(-len, yy, len, yy + 1, DIM);
            }
            g.pose().popPose();
            g.disableScissor();
            g.renderOutline(ax - r, ay - r, r * 2, r * 2, 0x88FFFFFF);
            g.fill(ax - 18, ay, ax - 6, ay + 2, AMBER);
            g.fill(ax + 6, ay, ax + 18, ay + 2, AMBER);
            g.fill(ax - 1, ay - 1, ax + 2, ay + 2, AMBER);
            centered(g, f, String.format("%.0f°  %.1fG", t.bank, t.gLoad), ax, ay + r + 3, DIM);
        }
        // speed tape (left)
        float spdKmh = t.airspeed * 3.6f;
        int lx = cx - 120;
        panel(g, lx - 34, cy - 50, lx + 6, cy + 50);
        for (int i = -5; i <= 5; i++) {
            int sv = (int) (Math.floor(spdKmh / 20) * 20 + i * 20);
            if (sv < 0) continue;
            int yy = cy + (int) ((spdKmh - sv) * 2.2f);
            if (yy < cy - 48 || yy > cy + 44) continue;
            g.fill(lx, yy, lx + 5, yy + 1, DIM);
            text(g, f, String.valueOf(sv), lx - 30, yy - 4, DIM);
        }
        g.fill(lx - 34, cy - 6, lx + 6, cy + 6, 0xCC000000);
        text(g, f, String.format("%.0f", spdKmh), lx - 30, cy - 4, t.stall ? RED : GREEN);
        centered(g, f, Component.translatable("vehicleworks.hud.kmh").getString(), lx - 14, cy + 52, DIM);
        // altitude tape (right)
        int rx = cx + 120;
        panel(g, rx - 6, cy - 50, rx + 40, cy + 50);
        for (int i = -5; i <= 5; i++) {
            int av = (int) (Math.floor(t.altitude / 10) * 10 + i * 10);
            int yy = cy + (int) ((t.altitude - av) * 4.4f);
            if (yy < cy - 48 || yy > cy + 44) continue;
            g.fill(rx - 6, yy, rx - 1, yy + 1, DIM);
            text(g, f, String.valueOf(av), rx + 2, yy - 4, DIM);
        }
        g.fill(rx - 6, cy - 6, rx + 40, cy + 6, 0xCC000000);
        text(g, f, String.format("%.0f", t.altitude), rx + 2, cy - 4, GREEN);
        text(g, f, String.format("%+.1f", t.verticalSpeed), rx + 2, cy + 54, t.verticalSpeed < -8 ? AMBER : DIM);
        text(g, f, Component.translatable("vehicleworks.hud.agl", String.format("%.0f", t.agl)).getString(), rx - 6, cy - 62, DIM);
        // heading tape (top)
        int hy = 18;
        panel(g, cx - 90, hy - 4, cx + 90, hy + 18);
        for (int d = -60; d <= 60; d += 5) {
            float hd = (float) (Math.floor(t.heading / 5) * 5 + d);
            int xx = cx + (int) ((hd - t.heading) * 1.5f);
            if (xx < cx - 88 || xx > cx + 88) continue;
            float n = ((hd % 360) + 360) % 360;
            boolean major = Math.round(n) % 30 == 0;
            g.fill(xx, hy + 10, xx + 1, hy + (major ? 16 : 13), DIM);
            if (major) {
                String lbl = switch (Math.round(n)) { case 0, 360 -> "N"; case 90 -> "E"; case 180 -> "S"; case 270 -> "W"; default -> String.valueOf(Math.round(n) / 10); };
                centered(g, f, lbl, xx, hy, WHITE);
            }
        }
        g.fill(cx, hy + 8, cx + 1, hy + 18, AMBER);
        if (t.autopilot) centered(g, f, String.format("AP  HDG %03.0f  ALT %.0f", t.apHeading, t.apAltitude), cx, cy - 36, GREEN);
        if (t.emergencyTest && (System.currentTimeMillis() / 300) % 2 == 0) {
            String m = net.minecraft.network.chat.Component.translatable("vehicleworks.panel.hud_test").getString();
            g.fill(cx - f.width(m) / 2 - 4, cy - 62, cx + f.width(m) / 2 + 4, cy - 50, 0xC0B01010);
            centered(g, f, m, cx, cy - 60, WHITE);
        }
        if (!t.battery) centered(g, f, net.minecraft.network.chat.Component.translatable("vehicleworks.panel.hud_batt").getString(), cx, cy - 48, AMBER);
        if (t.navActive) {
            float diff = Mth.wrapDegrees(t.navBearing - t.heading);
            int xx = cx + (int) Mth.clamp(diff * 1.5f, -88, 88);
            g.fill(xx - 2, hy - 6, xx + 3, hy - 2, 0xFFFF55FF);
            centered(g, f, String.format("WPT %.0fm", t.navDistance), cx, hy + 22, 0xFFFF88FF);
        }
        // levers
        int bx = cx - 175;
        int by = h - 120;
        panel(g, bx - 6, by - 14, bx + 82, by + 72);
        bar(g, bx, by, 8, 60, p == ControlProfile.AIRSHIP ? t.shipThrottle : t.throttle, t.afterburner ? 0xFFFF8030 : GREEN);
        text(g, f, "THR", bx - 3, by - 11, DIM);
        if (p == ControlProfile.HELICOPTER) {
            bar(g, bx + 26, by, 8, 60, t.collective, 0xFF60C0FF);
            text(g, f, "COL", bx + 22, by - 11, DIM);
        } else if (p == ControlProfile.AIRSHIP) {
            bar(g, bx + 26, by, 8, 60, (t.lift + 1) / 2, 0xFF60C0FF);
            text(g, f, "LFT", bx + 22, by - 11, DIM);
        } else {
            bar(g, bx + 26, by, 8, 60, t.flaps, 0xFF60C0FF);
            text(g, f, "FLP", bx + 22, by - 11, DIM);
        }
        bar(g, bx + 52, by, 8, 60, t.fuelCapacity > 0 ? t.fuel / t.fuelCapacity : 0, t.fuel < t.fuelCapacity * 0.15 ? RED : AMBER);
        text(g, f, "FUEL", bx + 46, by - 11, DIM);
        text(g, f, (t.gearDown ? "GEAR ▼" : "GEAR ▲"), bx - 3, by + 63, t.gearDown ? GREEN : DIM);
        if (full) {
            text(g, f, String.format("AoA %.1f°  M%.2f", t.aoa, t.mach), cx - 60, h - 14, DIM);
        }
        String flags = (t.assist ? "ASSIST " : "") + (t.mouseAim && p == ControlProfile.AIRCRAFT ? "MOUSE-AIM " : "") + (t.parkingBrake ? "BRAKE" : "");
        text(g, f, flags, 6, h - 12, DIM);
        // mouse-aim reticles: aim circle at screen centre, nose marker offset by the angle to the nose
        if (t.mouseAim && p == ControlProfile.AIRCRAFT) {
            Minecraft mc = Minecraft.getInstance();
            float yawToNose = Mth.wrapDegrees((t.heading) - (mc.player.getYRot() + 180));
            float pitchToNose = t.pitch + mc.player.getXRot();
            double fov = mc.options.fov().get();
            float ppd = (float) (h / 2.0 / (fov / 2.0));
            int nx = cx + (int) Mth.clamp(yawToNose * ppd, -w / 2f + 10, w / 2f - 10);
            int ny = cy - (int) Mth.clamp(pitchToNose * ppd, -h / 2f + 10, h / 2f - 10);
            g.renderOutline(cx - 6, cy - 6, 13, 13, 0xAAFFFFFF);
            g.fill(nx - 5, ny, nx + 6, ny + 1, AMBER);
            g.fill(nx, ny - 5, nx + 1, ny + 6, AMBER);
        }
        if (p == ControlProfile.AIRSHIP) text(g, f, String.format("ENV %.0f°C  LIFT %.1fkN", t.envelopeTemp, t.gasLift / 1000), cx - 60, h - 26, DIM);
        if (p == ControlProfile.HELICOPTER && t.rotorOffset > 1.0) text(g, f, Component.translatable("vehicleworks.hud.rotor_offset", String.format("%.1f", t.rotorOffset)).getString(), cx - 60, h - 38, AMBER);
    }

    private static void car(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h) {
        int cx = w - 90, cy = h - 60;
        panel(g, cx - 60, cy - 40, cx + 60, cy + 40);
        float kmh = t.groundSpeed * 3.6f;
        // tachometer arc
        float rpm = Mth.clamp(t.rpmFraction, 0, 1.1f);
        for (int i = 0; i <= 20; i++) {
            float a = (float) Math.toRadians(-210 + i * 12);
            int x = cx + (int) (Math.cos(a) * 30), y = cy + (int) (Math.sin(a) * 30);
            int c = i / 20f <= rpm ? (i > 16 ? RED : GREEN) : 0x44FFFFFF;
            g.fill(x - 1, y - 1, x + 2, y + 2, c);
        }
        centered(g, f, String.format("%.0f", kmh), cx, cy - 8, WHITE);
        centered(g, f, Component.translatable("vehicleworks.hud.kmh").getString(), cx, cy + 3, DIM);
        String gear = t.carGear < 0 ? "R" : (t.carGear == 0 ? "N" : String.valueOf(t.carGear));
        centered(g, f, gear, cx, cy + 22, AMBER);
        bar(g, cx + 44, cy - 30, 6, 60, t.fuelCapacity > 0 ? t.fuel / t.fuelCapacity : 0, AMBER);
        if (t.parkingBrake) centered(g, f, "P", cx - 40, cy + 22, RED);
    }

    private static void ship(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h, boolean sub) {
        int cx = w - 90, cy = h - 60;
        panel(g, cx - 60, cy - 44, cx + 60, cy + 44);
        float knots = t.groundSpeed * 1.944f;
        centered(g, f, String.format("%.1f kn", knots), cx, cy - 36, WHITE);
        centered(g, f, String.format("HDG %03.0f", t.heading), cx, cy - 24, DIM);
        // engine order telegraph
        float st = t.shipThrottle;
        g.fill(cx - 4, cy - 12, cx + 4, cy + 28, 0x66000000);
        int yy = cy + 8 - (int) (st * 20);
        g.fill(cx - 8, yy - 1, cx + 8, yy + 2, st < 0 ? AMBER : GREEN);
        text(g, f, st < 0 ? "ASTERN" : "AHEAD", cx + 12, yy - 4, DIM);
        if (sub) {
            text(g, f, String.format("DEPTH %.1fm", t.depth), cx - 56, cy + 32, t.depth > 60 ? AMBER : WHITE);
            bar(g, cx - 54, cy - 12, 6, 36, t.ballast, 0xFF40A0FF);
            text(g, f, "BAL", cx - 58, cy - 22, DIM);
        }
        bar(g, cx + 46, cy - 12, 6, 36, t.fuelCapacity > 0 ? t.fuel / t.fuelCapacity : 0, AMBER);
    }

    private static void engines(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h) {
        int n = t.engineState.length;
        if (n == 0) return;
        int x = w - 104, y = h - 26 - n * 12;
        panel(g, x - 4, y - 14, x + 92, y + n * 12 + 2);
        text(g, f, Component.translatable("vehicleworks.hud.engines").getString(), x, y - 11, DIM);
        for (int i = 0; i < n; i++) {
            EngineSim.State st = EngineSim.State.values()[Math.floorMod(t.engineState[i], EngineSim.State.values().length)];
            EngineType type = EngineType.byIndex(t.engineType[i]);
            int c = switch (st) {
                case RUNNING -> GREEN;
                case STARTING -> AMBER;
                case FAILED -> RED;
                default -> DIM;
            };
            int yy = y + i * 12;
            g.fill(x, yy + 2, x + 5, yy + 7, c);
            text(g, f, String.format("%d %s", i + 1, type.id.substring(0, Math.min(5, type.id.length())).toUpperCase()), x + 8, yy, WHITE);
            bar(g, x + 52, yy + 1, 18, 6, 0, 0);
            g.fill(x + 52, yy + 1, x + 52 + (int) (18 * Mth.clamp(t.engineSpool[i], 0, 1)), yy + 7, c);
            float tf = (float) Mth.clamp((t.engineTemp[i] - 15) / (type.maxTempC - 15), 0, 1.2);
            g.fill(x + 74, yy + 1, x + 74 + (int) (14 * Math.min(1, tf)), yy + 7, tf > 0.95 ? RED : 0xFFFF9040);
        }
    }

    private static void weapons(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h) {
        int n = t.weaponAmmo.length;
        if (n == 0) return;
        int x = w - 120, y = 40;
        panel(g, x - 4, y - 14, x + 116, y + n * 11 + 4);
        String arm = t.armed ? Component.translatable("vehicleworks.hud.armed").getString() : Component.translatable("vehicleworks.hud.safe").getString();
        boolean flash = t.armed && t.fireFlash > 0;
        text(g, f, arm, x, y - 11, t.armed ? (flash ? WHITE : RED) : GREEN);
        for (int i = 0; i < n; i++) {
            int yy = y + i * 11;
            int fl = t.weaponFlags[i];
            boolean linked = (fl & WeaponSystem.FLAG_LINKED) != 0, mounted = (fl & WeaponSystem.FLAG_MOUNTED) != 0;
            String ch = linked ? "[" + (t.weaponChannel[i] + 1) + "]" : "[-]";
            int c = !mounted ? RED : ((fl & WeaponSystem.FLAG_EMPTY) != 0 ? AMBER : ((fl & WeaponSystem.FLAG_FIRING) != 0 ? WHITE : GREEN));
            String status = !mounted ? "NO MOUNT" : ((fl & WeaponSystem.FLAG_RELOADING) != 0 ? String.format("RLD %.0f%%", t.weaponReload[i] * 100)
                    : (fl & WeaponSystem.FLAG_SAFETY) != 0 ? "SAFETY" : t.weaponAmmo[i] + "/" + t.weaponReserve[i]);
            text(g, f, ch + " " + status, x, yy, c);
        }
    }

    private static void warnings(GuiGraphics g, Font f, VehicleTelemetry t, int w, int h, ControlProfile p) {
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        int y = h / 2 - 70;
        int cx = w / 2;
        java.util.List<String> warn = new java.util.ArrayList<>();
        if (t.stall && p == ControlProfile.AIRCRAFT) warn.add("vehicleworks.warn.stall");
        if (p == ControlProfile.AIRCRAFT && t.agl < 40 && t.verticalSpeed < -12) warn.add("vehicleworks.warn.pull_up");
        if (p == ControlProfile.AIRCRAFT && t.agl < 25 && !t.gearDown && t.airspeed < 70) warn.add("vehicleworks.warn.gear");
        if (t.fuelCapacity > 0 && t.fuel < t.fuelCapacity * 0.1) warn.add("vehicleworks.warn.low_fuel");
        for (byte s : t.engineState) if (s == EngineSim.State.FAILED.ordinal()) { warn.add("vehicleworks.warn.engine_fail"); break; }
        if (t.stress > 0.9) warn.add("vehicleworks.warn.overstress");
        if (t.integrity < 0.5) warn.add("vehicleworks.warn.damage");
        if (p == ControlProfile.SUBMARINE && t.depth > 70) warn.add("vehicleworks.warn.depth");
        for (String k : warn) {
            if (blink) centered(g, f, Component.translatable(k).getString(), cx, y, RED);
            y += 11;
        }
    }
}
