package com.trafficcraft.client.gui;

import com.trafficcraft.TCConfig;
import com.trafficcraft.client.ClientEvents;
import com.trafficcraft.client.VehicleKeys;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehiclePhysics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Driving HUD: digital speed, gear, rpm bar, fuel, indicators and lamp tell-tales. */
public final class VehicleHud {
    private static int enteredTick = -1;
    private static int lastVehicle = -1;

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !TCConfig.SHOW_HUD.get()) return;
        if (!(mc.player.getVehicle() instanceof VehicleEntity v)) {
            lastVehicle = -1;
            return;
        }
        if (v.getId() != lastVehicle) {
            lastVehicle = v.getId();
            enteredTick = mc.player.tickCount;
        }
        boolean driver = ClientEvents.drivenVehicle() == v;
        ParkSensor.render(g, v);
        Font font = mc.font;
        int W = g.guiWidth(), H = g.guiHeight();
        int pw = 168, ph = 74;
        int x = W - pw - 8, y = H - ph - 8;
        g.fill(x, y, x + pw, y + ph, 0xB0101216);
        g.fill(x, y, x + pw, y + 1, 0xFF3A4A5E);
        float kmh = Math.abs(v.visSpeed()) * 3.6f;
        String sp = String.valueOf(Math.round(kmh));
        g.pose().pushPose();
        g.pose().translate(x + 10, y + 8, 0);
        g.pose().scale(2.6f, 2.6f, 1);
        g.drawString(font, sp, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        g.drawString(font, "km/h", x + 14 + (int) (font.width(sp) * 2.6f), y + 16, 0xFF9AA4B0, false);
        // gear
        int gear = v.visGear();
        boolean manual = v.has(VehicleEntity.F_MANUAL);
        String gs = !v.has(VehicleEntity.F_ENGINE) ? "P" : gear < 0 ? "R" : Math.abs(v.visSpeed()) < 0.1f && v.visLoad() < 0.05f ? (v.has(VehicleEntity.F_HANDBRAKE) ? "P" : "N")
                : (manual ? "M" : "D") + gear;
        int gc = gs.startsWith("R") ? 0xFFFF6A50 : gs.startsWith("P") ? 0xFFB0B8C4 : 0xFF7FE0FF;
        g.pose().pushPose();
        g.pose().translate(x + pw - 34, y + 8, 0);
        g.pose().scale(1.8f, 1.8f, 1);
        g.drawString(font, gs, 0, 0, gc, true);
        g.pose().popPose();
        // rpm bar
        float rpm = v.visRpm();
        float red = v.spec().redRpm;
        int bx = x + 10, by = y + 38, bw = pw - 20;
        g.fill(bx, by, bx + bw, by + 5, 0xFF262C34);
        int fill = (int) (bw * Math.min(1, rpm / (red * 1.1f)));
        int rx = (int) (bw * (red / (red * 1.1f)));
        g.fill(bx, by, bx + Math.min(fill, rx), by + 5, 0xFF4FB0FF);
        if (fill > rx) g.fill(bx + rx, by, bx + fill, by + 5, 0xFFFF4040);
        g.fill(bx + rx, by - 1, bx + rx + 1, by + 6, 0xFFFF4040);
        g.drawString(font, String.format("%.1f", rpm / 1000f) + "k rpm", bx, by + 7, 0xFF8892A0, false);
        // fuel
        float fuel = v.getFuel() / Math.max(1, v.spec().fuelCapacity);
        int fx = x + pw - 64, fy = by + 8;
        g.drawString(font, v.spec().electric ? "⚡" : "F", fx - 9, fy, fuel < 0.15f ? 0xFFFF5040 : 0xFFB0B8C4, false);
        g.fill(fx, fy + 1, fx + 54, fy + 7, 0xFF262C34);
        g.fill(fx, fy + 1, fx + (int) (54 * fuel), fy + 7, fuel < 0.15f ? 0xFFFF5040 : 0xFF6AD06A);
        // tell-tales
        boolean blink = v.blinkOn();
        int ty = y + ph - 12;
        boolean hz = v.has(VehicleEntity.F_HAZARD);
        tt(g, font, x + 10, ty, "◀", (v.has(VehicleEntity.F_IND_L) || hz) && blink, 0xFF40E060);
        tt(g, font, x + 24, ty, "▶", (v.has(VehicleEntity.F_IND_R) || hz) && blink, 0xFF40E060);
        tt(g, font, x + 40, ty, "⚠", hz, 0xFFFF4040);
        tt(g, font, x + 54, ty, "☀", v.has(VehicleEntity.F_LOW), 0xFF60E060);
        tt(g, font, x + 66, ty, "≡", v.has(VehicleEntity.F_HIGH), 0xFF5090FF);
        tt(g, font, x + 78, ty, "(P)", v.has(VehicleEntity.F_HANDBRAKE), 0xFFFF4040);
        tt(g, font, x + 98, ty, "⌂", v.has(VehicleEntity.F_INTERIOR), 0xFFFFE090);
        if (v.spec().siren) tt(g, font, x + 110, ty, "★", v.has(VehicleEntity.F_SIREN) || v.has(VehicleEntity.F_BEACONS), 0xFF4080FF);
        Component msg = null;
        if (v.has(VehicleEntity.F_STARTING)) msg = Component.translatable("hud.trafficcraft.starting");
        else if (!v.has(VehicleEntity.F_ENGINE) && driver) msg = Component.translatable("hud.trafficcraft.engine_off", VehicleKeys.ENGINE.getTranslatedKeyMessage());
        if (msg != null) g.drawString(font, msg, x + pw - font.width(msg), y - 11, 0xFFFFD060, true);
        if (fuel <= 0.001f) {
            Component m2 = Component.translatable("hud.trafficcraft.empty");
            g.drawString(font, m2, x + pw - font.width(m2), y - 22, 0xFFFF5040, true);
        }
        g.drawString(font, v.spec().name, x + pw - 8 - font.width(v.spec().name), y + 28, 0xFF6C7684, false);
        // first seconds: control hints
        if (driver && mc.player.tickCount - enteredTick < 200) {
            String[] lines = {
                    "W/S: " + tr("throttle") + "   A/D: " + tr("steer") + "   " + tr("space") + ": " + tr("handbrake"),
                    VehicleKeys.ENGINE.getTranslatedKeyMessage().getString() + ": " + tr("engine") + "   " + VehicleKeys.LIGHTS.getTranslatedKeyMessage().getString()
                            + ": " + tr("lights") + "   " + VehicleKeys.HORN.getTranslatedKeyMessage().getString() + ": " + tr("horn"),
                    VehicleKeys.IND_LEFT.getTranslatedKeyMessage().getString() + "/" + VehicleKeys.IND_RIGHT.getTranslatedKeyMessage().getString() + ": "
                            + tr("indicators") + "   " + VehicleKeys.HAZARD.getTranslatedKeyMessage().getString() + ": " + tr("hazard") + "   "
                            + VehicleKeys.MENU.getTranslatedKeyMessage().getString() + "/E: " + tr("menu")};
            int ly = 8;
            for (String l : lines) {
                int w = font.width(l);
                g.fill(W / 2 - w / 2 - 4, ly - 2, W / 2 + w / 2 + 4, ly + 10, 0x90000000);
                g.drawString(font, l, W / 2 - w / 2, ly, 0xFFE8ECF2, false);
                ly += 13;
            }
        }
    }

    private static String tr(String k) {
        return Component.translatable("hud.trafficcraft.hint." + k).getString();
    }

    private static void tt(GuiGraphics g, Font f, int x, int y, String s, boolean on, int col) {
        g.drawString(f, s, x, y, on ? col : 0xFF3A4048, false);
    }

    public static int gearCount(VehicleEntity v) {
        return VehiclePhysics.gears(v.spec()).length;
    }
}
