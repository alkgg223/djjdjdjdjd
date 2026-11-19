package com.trafficcraft.client.gui;

import com.trafficcraft.client.ClientEvents;
import com.trafficcraft.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Touch-friendly vehicle control panel (opened with the inventory key or U while driving). */
public class VehicleScreen extends Screen {
    private final VehicleEntity v;
    private final List<Runnable> refresh = new ArrayList<>();

    public VehicleScreen(VehicleEntity v) {
        super(Component.literal(v.spec().name));
        this.v = v;
    }

    @Override
    protected void init() {
        refresh.clear();
        int bw = 96, bh = 22, gap = 6;
        int cols = 4;
        int x0 = width / 2 - (cols * bw + (cols - 1) * gap) / 2;
        int y0 = height / 2 - 70;
        Object[][] defs = {
                {"engine", VehicleEntity.A_ENGINE, (BooleanSupplier) () -> v.has(VehicleEntity.F_ENGINE)},
                {"lights", VehicleEntity.A_LIGHTS, (BooleanSupplier) () -> v.has(VehicleEntity.F_LOW)},
                {"high_beam", VehicleEntity.A_HIGH, (BooleanSupplier) () -> v.has(VehicleEntity.F_HIGH)},
                {"interior", VehicleEntity.A_INTERIOR, (BooleanSupplier) () -> v.has(VehicleEntity.F_INTERIOR)},
                {"indicator_left", VehicleEntity.A_IND_L, (BooleanSupplier) () -> v.has(VehicleEntity.F_IND_L)},
                {"hazard", VehicleEntity.A_HAZARD, (BooleanSupplier) () -> v.has(VehicleEntity.F_HAZARD)},
                {"indicator_right", VehicleEntity.A_IND_R, (BooleanSupplier) () -> v.has(VehicleEntity.F_IND_R)},
                {"horn", VehicleEntity.A_HORN, (BooleanSupplier) () -> false},
                {"gear_mode", VehicleEntity.A_GEARMODE, (BooleanSupplier) () -> v.has(VehicleEntity.F_MANUAL)},
                {"siren", VehicleEntity.A_SIREN, (BooleanSupplier) () -> v.has(VehicleEntity.F_SIREN)},
        };
        int i = 0;
        for (Object[] d : defs) {
            String key = (String) d[0];
            int action = (Integer) d[1];
            BooleanSupplier state = (BooleanSupplier) d[2];
            if (key.equals("siren") && !v.spec().siren) continue;
            int x = x0 + (i % cols) * (bw + gap), y = y0 + (i / cols) * (bh + gap);
            Button b = Button.builder(label(key, state.getAsBoolean()), btn -> ClientEvents.send(v, action)).bounds(x, y, bw, bh).build();
            refresh.add(() -> b.setMessage(label(key, state.getAsBoolean())));
            addRenderableWidget(b);
            i++;
        }
        int y = y0 + ((i + cols - 1) / cols) * (bh + gap) + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.gear_up"), b -> ClientEvents.shift(v, 1)).bounds(x0, y, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.gear_down"), b -> ClientEvents.shift(v, -1)).bounds(x0 + bw + gap, y, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.inventory"), b -> {
            if (minecraft != null && minecraft.player != null) minecraft.setScreen(new InventoryScreenBypass(minecraft.player));
        }).bounds(x0 + 2 * (bw + gap), y, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trafficcraft.close"), b -> onClose()).bounds(x0 + 3 * (bw + gap), y, bw, bh).build());
    }

    private static Component label(String key, boolean on) {
        return Component.translatable("gui.trafficcraft." + key).append(on ? Component.literal("  ●").withStyle(s -> s.withColor(0x60E060)) : Component.empty());
    }

    @Override
    public void tick() {
        refresh.forEach(Runnable::run);
        if (v.isRemoved() || minecraft == null || minecraft.player == null || minecraft.player.getVehicle() != v) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 92, 0xFFFFFF);
        String info = Math.round(Math.abs(v.visSpeed()) * 3.6f) + " km/h  |  " + Math.round(v.getFuel()) + "/" + Math.round(v.spec().fuelCapacity) + " L  |  "
                + v.getPlate();
        g.drawCenteredString(font, info, width / 2, height / 2 - 82, 0xA0B0C0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Inventory screen that is not intercepted by the vehicle screen redirect. */
    public static class InventoryScreenBypass extends InventoryScreen {
        public InventoryScreenBypass(net.minecraft.world.entity.player.Player p) {
            super(p);
        }
    }
}
