package com.vehicleworks.client;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Refuelling at a refuel port: choose the fuel grade (AVGAS for piston engines, JET-A for
 * turbines), the amount, then start the pump and watch every tank fill up live.
 */
public class RefuelScreen extends Screen {
    private final VehicleEntity vehicle;
    private final CompoundTag data;
    private int grade;
    private double amount;
    private Button gradeButton, startButton;

    public RefuelScreen(VehicleEntity v, CompoundTag data) {
        super(Component.translatable("vehicleworks.refuel.title"));
        this.vehicle = v;
        this.data = data;
        int need = data.getInt("Grades");
        grade = (need & 4) != 0 && (need & 2) == 0 ? 2 : ((need & 2) != 0 ? 1 : 2);
        amount = missing();
    }

    private double missing() {
        VehicleTelemetry t = vehicle.telemetry;
        return Math.max(0, t.fuelCapacity - t.fuel);
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 70;
        gradeButton = addRenderableWidget(Button.builder(gradeLabel(), b -> { grade = grade == 1 ? 2 : 1; b.setMessage(gradeLabel()); })
                .bounds(cx - 100, y + 18, 200, 20).build());
        double max = Math.max(1, missing());
        addRenderableWidget(new AbstractSliderButton(cx - 100, y + 42, 200, 20, amountLabel(), Math.min(1, amount / max)) {
            @Override protected void updateMessage() { setMessage(amountLabel()); }
            @Override protected void applyValue() { amount = Math.round(value * max); }
        });
        startButton = addRenderableWidget(Button.builder(Component.translatable("vehicleworks.refuel.start"), b -> {
            if (vehicle.telemetry.refueling) Payloads.toServer(new Payloads.Action(vehicle.getId(), Payloads.ACT_REFUEL_STOP, 0));
            else Payloads.toServer(new Payloads.Action(vehicle.getId(), Payloads.ACT_REFUEL_START, grade | ((int) Math.max(1, amount) << 2)));
        }).bounds(cx - 100, y + 66, 200, 20).build());
    }

    private Component gradeLabel() {
        int need = data.getInt("Grades");
        boolean ok = (need & (1 << grade)) != 0 || need == 0 || need == 1;
        return Component.translatable("vehicleworks.refuel.grade", Component.translatable(grade == 1 ? "vehicleworks.fuel.avgas" : "vehicleworks.fuel.jet"))
                .append(ok ? "" : "  ⚠");
    }

    private Component amountLabel() {
        return Component.translatable("vehicleworks.refuel.amount", Math.round(amount));
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved()) { onClose(); return; }
        startButton.setMessage(Component.translatable(vehicle.telemetry.refueling ? "vehicleworks.refuel.stop" : "vehicleworks.refuel.start"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int cx = width / 2, y = height / 2 - 70;
        g.fill(cx - 120, y - 8, cx + 120, y + 190, 0xE0262B31);
        g.renderOutline(cx - 120, y - 8, 240, 198, 0xFF59626D);
        super.render(g, mx, my, partial);
        VehicleTelemetry t = vehicle.telemetry;
        g.drawCenteredString(font, title, cx, y, 0xFFFFFF);
        int need = data.getInt("Grades");
        String req = ((need & 2) != 0 ? "AVGAS " : "") + ((need & 4) != 0 ? "JET-A" : "");
        g.drawCenteredString(font, Component.translatable("vehicleworks.refuel.needs", req.isEmpty() ? "-" : req), cx, y + 92, 0xFF9FB3C8);
        if (!data.getBoolean("Creative")) g.drawCenteredString(font, Component.translatable("vehicleworks.refuel.canisters", data.getInt("Canisters")), cx, y + 103, 0xFF9FB3C8);
        // tanks
        int n = t.tankFill.length;
        int bw = Math.min(14, Math.max(6, 200 / Math.max(1, n) - 3));
        int x = cx - (n * (bw + 3)) / 2;
        int by = y + 118, bh = 44;
        for (int i = 0; i < n; i++) {
            int col = switch (t.tankGrade[i]) { case 1 -> 0xFF4FA3FF; case 2 -> 0xFFFFD040; case 3 -> 0xFFFF5050; default -> 0xFFB0B0B0; };
            int fh = (int) (bh * t.tankFill[i]);
            g.fill(x, by, x + bw, by + bh, 0xFF101214);
            g.fill(x + 1, by + bh - fh, x + bw - 1, by + bh, col);
            g.drawCenteredString(font, t.tankSide[i] < 0 ? "L" : (t.tankSide[i] > 0 ? "R" : "C"), x + bw / 2, by + bh + 2, 0xFF9AA0A6);
            x += bw + 3;
        }
        String st = t.refueling ? Component.translatable("vehicleworks.refuel.flowing", Math.round(t.refuelRemaining)).getString()
                : Component.translatable("vehicleworks.panel.fuel", Math.round(t.fuel), Math.round(t.fuelCapacity)).getString();
        g.drawCenteredString(font, st, cx, by + bh + 14, t.refueling ? 0xFF7CFF8A : 0xFFFFD040);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
