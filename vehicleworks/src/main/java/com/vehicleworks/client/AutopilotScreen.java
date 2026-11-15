package com.vehicleworks.client;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Autopilot panel: type a compass heading (0–359) and an altitude (block Y), then engage. */
public class AutopilotScreen extends Screen {
    private final VehicleEntity vehicle;
    private EditBox heading, altitude;

    public AutopilotScreen(VehicleEntity v) {
        super(Component.translatable("vehicleworks.autopilot.title"));
        this.vehicle = v;
    }

    @Override
    protected void init() {
        VehicleTelemetry t = vehicle.telemetry;
        int cx = width / 2, y = height / 2 - 50;
        heading = new EditBox(font, cx + 5, y, 90, 20, Component.translatable("vehicleworks.autopilot.heading"));
        heading.setFilter(s -> s.matches("\\d{0,3}"));
        heading.setValue(String.valueOf(Math.round(t.autopilot ? t.apHeading : t.heading) % 360));
        altitude = new EditBox(font, cx + 5, y + 26, 90, 20, Component.translatable("vehicleworks.autopilot.altitude"));
        altitude.setFilter(s -> s.matches("-?\\d{0,5}"));
        altitude.setValue(String.valueOf(Math.round(t.autopilot ? t.apAltitude : Math.max(t.altitude + 50, 120))));
        addRenderableWidget(heading);
        addRenderableWidget(altitude);
        setInitialFocus(heading);
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.autopilot.current"), b -> {
            heading.setValue(String.valueOf(Math.round(vehicle.telemetry.heading) % 360));
            altitude.setValue(String.valueOf(Math.round(vehicle.telemetry.altitude)));
        }).bounds(cx - 100, y + 56, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.autopilot.engage"), b -> send(true))
                .bounds(cx - 100, y + 80, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.autopilot.disengage"), b -> send(false))
                .bounds(cx + 2, y + 80, 98, 20).build());
    }

    private static int parse(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private void send(boolean on) {
        int hdg = Math.floorMod(parse(heading.getValue(), 0), 360);
        int alt = Math.max(-2048, Math.min(14000, parse(altitude.getValue(), 100)));
        int arg = (on ? 1 : 0) | (hdg << 1) | ((alt + 2048) << 10);
        Payloads.toServer(new Payloads.Action(vehicle.getId(), Payloads.ACT_AUTOPILOT, arg));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { send(true); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int cx = width / 2, y = height / 2 - 50;
        g.drawCenteredString(font, title, cx, y - 30, 0xFFFFFF);
        VehicleTelemetry t = vehicle.telemetry;
        g.drawCenteredString(font, Component.translatable("vehicleworks.autopilot.now", Math.round(t.heading) % 360, Math.round(t.altitude)), cx, y - 16, 0xA0A0A0);
        g.drawString(font, Component.translatable("vehicleworks.autopilot.heading"), cx - 100, y + 6, 0xFFFFFF);
        g.drawString(font, Component.translatable("vehicleworks.autopilot.altitude"), cx - 100, y + 32, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("vehicleworks.autopilot.hint"), cx, y + 108, 0x808080);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
