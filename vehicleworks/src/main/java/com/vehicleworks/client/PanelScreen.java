package com.vehicleworks.client;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.VehicleControls;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Overhead panel as a screen: the same 18 switches as the 3D panel (6 × 3), each with its lamp,
 * plus the electrical and fuel state. Every switch drives the real system on the server.
 */
public class PanelScreen extends Screen {
    private static final int W = 50, H = 32, GAP = 4;
    private final VehicleEntity vehicle;
    private final Button[] buttons = new Button[VehicleControls.SWITCH_COUNT];

    public PanelScreen(VehicleEntity v) {
        super(Component.translatable("vehicleworks.panel.title"));
        this.vehicle = v;
    }

    private int x0() { return width / 2 - (CockpitRenderer.COLS * W + (CockpitRenderer.COLS - 1) * GAP) / 2; }
    private int y0() { return Math.max(34, height / 2 - 70); }

    @Override
    protected void init() {
        for (CockpitRenderer.Ctl c : CockpitRenderer.CONTROLS) {
            int id = c.id(), w = c.kind() == CockpitRenderer.LEVER ? 2 * W + GAP : W;
            buttons[id] = addRenderableWidget(Button.builder(state(id), b -> pressSwitch(vehicle, id))
                    .bounds(x0() + c.col() * (W + GAP), y0() + c.row() * (H + GAP) + 13, w, H - 13).build());
        }
        addRenderableWidget(Button.builder(voiceLabel(), b -> { VoiceCallouts.toggle(); b.setMessage(voiceLabel()); })
                .bounds(width / 2 - 80, y0() + CockpitRenderer.ROWS * (H + GAP) + 52, 160, 18).build());
    }

    private static Component voiceLabel() {
        return Component.translatable("vehicleworks.voice.button", Component.translatable(VoiceCallouts.enabled() ? "vehicleworks.panel.on" : "vehicleworks.panel.off"));
    }

    /** Operate a switch exactly like the 3D panel. */
    public static void pressSwitch(VehicleEntity v, int id) {
        CockpitRenderer.pressed(v, id);
        if (id == VehicleControls.SW_AUTOPILOT && !v.telemetry.autopilot) { Minecraft.getInstance().setScreen(new AutopilotScreen(v)); return; }
        Payloads.toServer(new Payloads.Action(v.getId(), Payloads.ACT_SWITCH, id));
    }

    private Component state(int id) {
        VehicleTelemetry t = vehicle.telemetry;
        String k = switch (id) {
            case VehicleControls.SW_FUEL_SEL -> new String[]{"both", "left", "right", "off"}[Math.max(0, Math.min(3, t.fuelSelector))];
            case VehicleControls.SW_CABIN -> new String[]{"off", "dim", "bright"}[Math.max(0, Math.min(2, t.cabinLight))];
            case VehicleControls.SW_DIMMER -> "level" + t.dimmer;
            case VehicleControls.SW_DISPLAY_BRT -> "level" + t.displayBrt;
            case VehicleControls.SW_MASTER_CAUTION -> VoiceCallouts.warning ? "ack" : "press";
            case VehicleControls.SW_PANEL -> new String[]{"off", "dim", "bright"}[Math.max(0, Math.min(2, t.panelLight))];
            case VehicleControls.SW_STARTER -> t.sw(VehicleControls.BIT_STARTER) ? "cranking" : (CockpitRenderer.running(t) ? "running" : "press");
            case VehicleControls.SW_TEST -> t.emergencyTest ? "testing" : "press";
            case VehicleControls.SW_GEAR -> t.sw(id) ? "down" : "up";
            case VehicleControls.SW_AUTOPILOT -> t.autopilot ? "on" : "open";
            default -> t.sw(id) ? "on" : "off";
        };
        return Component.translatable("vehicleworks.panel." + k);
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved()) { onClose(); return; }
        for (int i = 0; i < buttons.length; i++) if (buttons[i] != null) buttons[i].setMessage(state(i));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int x = x0(), y = y0();
        int pw = CockpitRenderer.COLS * W + (CockpitRenderer.COLS - 1) * GAP;
        g.fill(x - 10, y - 28, x + pw + 10, y + CockpitRenderer.ROWS * (H + GAP) + 76, 0xE0262B31);
        g.renderOutline(x - 10, y - 28, pw + 20, CockpitRenderer.ROWS * (H + GAP) + 104, 0xFF59626D);
        super.render(g, mx, my, partial);
        VehicleTelemetry t = vehicle.telemetry;
        g.drawCenteredString(font, title, width / 2, y - 22, 0xFFFFFF);
        for (CockpitRenderer.Ctl ct : CockpitRenderer.CONTROLS) {
            int i = ct.id();
            int bx = x + ct.col() * (W + GAP), by = y + ct.row() * (H + GAP);
            int c = CockpitRenderer.lamp(t, i);
            g.fill(bx + 1, by + 2, bx + 7, by + 8, 0xFF000000);
            g.fill(bx + 2, by + 3, bx + 6, by + 7, c != 0 ? c : 0xFF30302C);
            g.pose().pushPose();
            g.pose().translate(bx + 9, by + 2, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(font, Component.translatable("vehicleworks.switch." + i), 0, 0, i == VehicleControls.SW_TEST ? 0xFFFF7060 : 0xFFE8EDF2);
            g.pose().popPose();
        }
        // annunciators in the first row
        for (int i = 0; i < CockpitRenderer.ANN.length; i++) {
            int bx = x + (i + 1) * (W + GAP), by = y + 13;
            boolean on = CockpitRenderer.annunciator(vehicle, t, i);
            int col = CockpitRenderer.ANN_COLOR[i];
            g.fill(bx, by, bx + W, by + H - 13, on ? (col & 0x00FFFFFF) | 0xC0000000 : 0xFF1A1C1F);
            g.pose().pushPose();
            g.pose().translate(bx + W / 2f, by + 6, 0);
            g.pose().scale(0.6f, 0.6f, 1);
            g.drawCenteredString(font, CockpitRenderer.ANN[i], 0, 0, on ? 0xFFFFFFFF : 0xFF707478);
            g.pose().popPose();
        }
        // electrical + fuel summary
        int sy = y + CockpitRenderer.ROWS * (H + GAP) + 2;
        String bus = t.busPowered ? (t.generator ? "GEN" : "BATT") : "OFF";
        g.drawString(font, Component.translatable("vehicleworks.panel.elec", Math.round(t.batteryCharge * 100), bus), x, sy, t.busPowered ? 0xFF7CFF8A : 0xFFFF5050);
        int tx = x;
        int ty = sy + 12;
        for (int i = 0; i < t.tankFill.length && i < 16; i++) {
            int bh = 22;
            int fh = (int) (bh * t.tankFill[i]);
            int col = switch (t.tankGrade[i]) { case 1 -> 0xFF4FA3FF; case 2 -> 0xFFFFD040; case 3 -> 0xFFFF5050; default -> 0xFFB0B0B0; };
            g.fill(tx, ty, tx + 8, ty + bh, 0xFF101214);
            g.fill(tx + 1, ty + bh - fh, tx + 7, ty + bh, col);
            g.drawCenteredString(font, t.tankSide[i] < 0 ? "L" : (t.tankSide[i] > 0 ? "R" : "C"), tx + 4, ty + bh + 2, 0xFF9AA0A6);
            tx += 11;
        }
        g.drawString(font, Component.translatable("vehicleworks.panel.fuel", Math.round(t.fuel), Math.round(t.fuelCapacity)), tx + 6, ty + 4, 0xFFFFD040);
        if (t.wrongFuel) g.drawString(font, Component.translatable("vehicleworks.panel.wrong_fuel"), tx + 6, ty + 15, 0xFFFF5050);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
