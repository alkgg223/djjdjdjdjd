package com.realweather.client.vehicle;

import com.mojang.math.Axis;
import com.realweather.client.radar.RadarProducts;
import com.realweather.client.tornado.ClientTornadoes;
import com.realweather.vehicle.StormVehicle;
import com.realweather.vehicle.VehicleType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Driving display: an analogue speedometer in third person (in first person you read the gauges on the dash),
 * gear, wipers and lights, a warning when snow or sand covers the windshield or the vehicle is bogged in sand,
 * and the readings of the chase instruments when a tornado is within range.
 */
public final class VehicleHud {
    private VehicleHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof StormVehicle v)) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        float kmh = (float) Math.abs(v.displaySpeed()) * 72F;
        if (!firstPerson) {
            dial(g, font, w - 58, h - 58, 46, kmh, v);
        } else {
            String s = String.format("%d km/h  %c", Math.round(kmh), v.gear());
            g.drawString(font, s, w - font.width(s) - 8, h - 16, 0xFFE8F0F8, true);
        }
        // Status line.
        List<Component> status = new ArrayList<>();
        int wipers = v.wipers();
        if (wipers > 0) {
            status.add(Component.translatable("realweather.vehicle.hud.wipers." + wipers).withStyle(ChatFormatting.AQUA));
        }
        if (v.flag(StormVehicle.FLAG_LIGHTS)) {
            status.add(Component.translatable("realweather.vehicle.hud.lights").withStyle(ChatFormatting.YELLOW));
        }
        if (v.kind.spikes && v.spikeDeploy > 0.05F) {
            status.add(Component.translatable(v.spikeDeploy > 0.9F ? "realweather.vehicle.hud.anchored" : "realweather.vehicle.hud.spikes_moving")
                    .withStyle(ChatFormatting.GOLD));
        }
        float glass = VehicleClient.glassCover(v);
        if (glass > 0.15F) {
            status.add(Component.translatable(wipers > 0 ? "realweather.vehicle.hud.glass_wiping" : "realweather.vehicle.hud.glass",
                    Math.round(glass * 100)).withStyle(ChatFormatting.WHITE));
        }
        if (v.bog > 0.6F) {
            status.add(Component.translatable("realweather.vehicle.hud.bogged").withStyle(ChatFormatting.GOLD));
        }
        if (v.sliding && Math.abs(v.displaySpeed()) > 0.2) {
            status.add(Component.translatable("realweather.vehicle.hud.skid").withStyle(ChatFormatting.RED));
        }
        if (v.flag(StormVehicle.FLAG_GLASS_BROKEN)) {
            status.add(Component.translatable("realweather.vehicle.hud.broken").withStyle(ChatFormatting.RED));
        }
        int y = h - (firstPerson ? 30 : 118);
        for (Component c : status) {
            g.drawString(font, c, w - font.width(c) - 8, y, 0xFFFFFFFF, true);
            y -= 11;
        }
        instruments(g, font, v);
        // Key hints, briefly after getting in.
        if (mc.player.tickCount % 2400 < 200) {
            Component hint = Component.translatable(v.kind.spikes ? "realweather.vehicle.hud.keys_spikes" : "realweather.vehicle.hud.keys",
                    VehicleClient.WIPERS.getTranslatedKeyMessage(), VehicleClient.LIGHTS.getTranslatedKeyMessage(), VehicleClient.HORN.getTranslatedKeyMessage(),
                    VehicleClient.SPIKES.getTranslatedKeyMessage());
            g.drawCenteredString(font, hint, w / 2, h - 60, 0xFFB8C4D0);
        }
    }

    private static void instruments(GuiGraphics g, Font font, StormVehicle v) {
        if (!v.kind.instruments) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        ClientTornadoes.T t = VehicleClient.nearest(v, VehicleClient.PROBE_RANGE);
        boolean probe = v.kind == VehicleType.INTERCEPT;
        lines.add(Component.translatable(probe ? "realweather.vehicle.probe.title" : "realweather.vehicle.doppler.title").withStyle(ChatFormatting.AQUA));
        if (t == null) {
            ClientTornadoes.T far = VehicleClient.nearest(v, 2000);
            if (far != null) {
                double d = Math.hypot(far.x - v.getX(), far.z - v.getZ());
                lines.add(Component.translatable("realweather.vehicle.probe.too_far", Math.round(d), (int) VehicleClient.PROBE_RANGE));
            } else {
                lines.add(Component.translatable("realweather.vehicle.probe.none").withStyle(ChatFormatting.GRAY));
            }
        } else {
            double d = Math.hypot(t.x - v.getX(), t.z - v.getZ());
            float bearing = (float) Math.toDegrees(Math.atan2(t.x - v.getX(), -(t.z - v.getZ())));
            lines.add(Component.translatable("realweather.vehicle.probe.distance", Math.round(d), Math.floorMod(Math.round(bearing), 360)));
            if (probe && v.spikeDeploy < 0.9F) {
                lines.add(Component.translatable("realweather.vehicle.probe.deploy", VehicleClient.SPIKES.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.GOLD));
            } else {
                float mps = v.measuredWind;
                int ef = RadarProducts.ratingFor(probe ? RadarProducts.peakWind(t) * Math.min(1F, mps / Math.max(1F, RadarProducts.peakWind(t)) * 1.6F) : mps);
                lines.add(Component.translatable("realweather.vehicle.probe.wind", Math.round(mps * 3.6F), Math.round(mps)).withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("realweather.vehicle.probe.rating", "EF" + ef).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                lines.add(Component.translatable("realweather.vehicle.probe.motion", Math.round(t.speed * 20F * 3.6F),
                        Math.floorMod(Math.round((float) Math.toDegrees(t.heading)), 360)).withStyle(ChatFormatting.GRAY));
            }
        }
        int y = 8;
        int width = 0;
        for (Component c : lines) {
            width = Math.max(width, font.width(c));
        }
        g.fill(4, 4, 12 + width, 8 + lines.size() * 11, 0x90101418);
        for (Component c : lines) {
            g.drawString(font, c, 8, y, 0xFFFFFFFF, true);
            y += 11;
        }
    }

    /** Analogue speedometer (0..200 km/h) with a digital read-out and the gear. */
    private static void dial(GuiGraphics g, Font font, int cx, int cy, int r, float kmh, StormVehicle v) {
        for (int i = 0; i < 96; i++) {
            float a = i * Mth.TWO_PI / 96F;
            int x = cx + Math.round(Mth.cos(a) * r);
            int y = cy + Math.round(Mth.sin(a) * r);
            g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFB8C0CA);
        }
        // Filled face.
        for (int dy = -r + 2; dy <= r - 2; dy++) {
            int half = (int) Math.sqrt((r - 2) * (r - 2) - dy * dy);
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xC0101418);
        }
        for (int s = 0; s <= 200; s += 10) {
            float a = (210F - 240F * s / 200F) * Mth.DEG_TO_RAD;
            boolean major = s % 40 == 0;
            int r0 = r - (major ? 9 : 5);
            int x0 = cx + Math.round(Mth.cos(a) * r0), y0 = cy - Math.round(Mth.sin(a) * r0);
            int x1 = cx + Math.round(Mth.cos(a) * (r - 3)), y1 = cy - Math.round(Mth.sin(a) * (r - 3));
            line(g, x0, y0, x1, y1, s >= 180 ? 0xFFE04040 : 0xFFE8EEF4);
            if (major) {
                String txt = String.valueOf(s);
                int tx = cx + Math.round(Mth.cos(a) * (r - 17));
                int ty = cy - Math.round(Mth.sin(a) * (r - 17));
                g.pose().pushPose();
                g.pose().translate(tx, ty, 0);
                g.pose().scale(0.6F, 0.6F, 1F);
                g.drawCenteredString(font, txt, 0, -4, 0xFFD0D8E0);
                g.pose().popPose();
            }
        }
        float a = 210F - 240F * Math.min(200F, kmh) / 200F;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-a));
        g.fill(-4, -1, r - 6, 1, 0xFFFF5A1E);
        g.pose().popPose();
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFF30343A);
        String sp = String.valueOf(Math.round(kmh));
        g.drawCenteredString(font, sp, cx, cy + 12, 0xFFFFFFFF);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 22, 0);
        g.pose().scale(0.7F, 0.7F, 1F);
        g.drawCenteredString(font, "km/h", 0, 0, 0xFF9AA4AE);
        g.pose().popPose();
        String gear = String.valueOf(v.gear());
        g.drawCenteredString(font, gear, cx, cy - 20, v.gear() == 'R' ? 0xFFFF6060 : 0xFF80E080);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, n);
            int y = y0 + (y1 - y0) * i / Math.max(1, n);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
