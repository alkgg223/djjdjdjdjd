package com.vehicleworks.client;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cockpit panel: a 3 x 2 grid of switches (battery, lights, engines / gear, emergency test,
 * autopilot). Every switch shows a status lamp: green = on, red = off, amber = busy.
 */
public class PanelScreen extends Screen {
    private static final int W = 110, H = 44, GAP = 8;
    private static final int GREEN = 0xFF3CE05A, RED = 0xFFE03C3C, AMBER = 0xFFFFB020, BLUE = 0xFF38D3FF, DARK = 0xFF1A1E23;
    private final VehicleEntity vehicle;
    private final Button[] buttons = new Button[6];

    public PanelScreen(VehicleEntity v) {
        super(Component.translatable("vehicleworks.panel.title"));
        this.vehicle = v;
    }

    private int x0() { return width / 2 - (3 * W + 2 * GAP) / 2; }
    private int y0() { return height / 2 - (2 * H + GAP) / 2 + 8; }

    @Override
    protected void init() {
        int[] actions = {Payloads.ACT_BATTERY, Payloads.ACT_LIGHTS, -1, Payloads.ACT_GEAR, Payloads.ACT_EMERGENCY_TEST, -2};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3, idx = i;
            buttons[i] = addRenderableWidget(Button.builder(label(i), b -> press(actions[idx]))
                    .bounds(x0() + col * (W + GAP), y0() + row * (H + GAP) + 14, W, H - 14).build());
        }
    }

    private void press(int action) {
        if (action == -2) { Minecraft.getInstance().setScreen(new AutopilotScreen(vehicle)); return; }
        if (action == -1) action = enginesRunning() ? Payloads.ACT_ENGINES_STOP : Payloads.ACT_ENGINES_START;
        Payloads.toServer(new Payloads.Action(vehicle.getId(), action, Payloads.ARG_PANEL));
    }

    private boolean enginesRunning() {
        for (byte st : vehicle.telemetry.engineState) if (st == 1 || st == 2) return true;
        return false;
    }

    private Component label(int i) {
        VehicleTelemetry t = vehicle.telemetry;
        String k = switch (i) {
            case 0 -> t.battery ? "on" : "off";
            case 1 -> t.lights ? "on" : "off";
            case 2 -> enginesRunning() ? "running" : "off";
            case 3 -> t.gearDown ? "down" : "up";
            case 4 -> t.emergencyTest ? "testing" : "press";
            default -> t.autopilot ? "on" : "open";
        };
        return Component.translatable("vehicleworks.panel." + k);
    }

    private int lamp(int i) {
        VehicleTelemetry t = vehicle.telemetry;
        boolean blink = (System.currentTimeMillis() / 300) % 2 == 0;
        return switch (i) {
            case 0 -> t.battery ? GREEN : RED;
            case 1 -> t.lights ? GREEN : RED;
            case 2 -> {
                boolean starting = false;
                for (byte st : t.engineState) if (st == 1) starting = true;
                yield starting ? (blink ? AMBER : DARK) : (enginesRunning() ? GREEN : RED);
            }
            case 3 -> t.gearDown ? GREEN : AMBER;
            case 4 -> t.emergencyTest ? (blink ? RED : DARK) : DARK;
            default -> t.autopilot ? BLUE : DARK;
        };
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved()) { onClose(); return; }
        for (int i = 0; i < 6; i++) buttons[i].setMessage(label(i));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        int x = x0(), y = y0();
        // panel body
        g.fill(x - 10, y - 26, x + 3 * W + 2 * GAP + 10, y + 2 * H + GAP + 12, 0xE0262B31);
        g.renderOutline(x - 10, y - 26, 3 * W + 2 * GAP + 20, 2 * H + GAP + 38, 0xFF59626D);
        g.drawCenteredString(font, title, width / 2, y - 18, 0xFFFFFF);
        String[] names = {"battery", "lights", "engines", "gear", "emergency", "autopilot"};
        for (int i = 0; i < 6; i++) {
            int bx = x + (i % 3) * (W + GAP), by = y + (i / 3) * (H + GAP);
            int c = lamp(i);
            g.fill(bx + 2, by + 3, bx + 10, by + 11, 0xFF000000);
            g.fill(bx + 3, by + 4, bx + 9, by + 10, c);
            g.drawString(font, Component.translatable("vehicleworks.panel." + names[i]), bx + 14, by + 3, i == 4 ? 0xFFFF7060 : 0xFFE8EDF2);
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
