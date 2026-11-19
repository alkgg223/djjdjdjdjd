package com.vehicleworks.client;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.VehicleEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** K: cabin announcements (played on the cabin speakers for everyone on board), and the automatic mode. */
public class AnnouncementScreen extends Screen {
    private final VehicleEntity vehicle;
    private Button auto;

    public AnnouncementScreen(VehicleEntity v) {
        super(Component.translatable("vehicleworks.pa.title"));
        this.vehicle = v;
    }

    @Override
    protected void init() {
        int bw = 160, bh = 20, x0 = width / 2 - bw - 3, y0 = height / 2 - 60;
        for (int i = 0; i < VehicleEntity.PA_COUNT; i++) {
            int id = i;
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.pa.name." + i), b -> {
                Payloads.toServer(new Payloads.Action(vehicle.getId(), Payloads.ACT_ANNOUNCE, id));
                onClose();
            }).bounds(x0 + (i % 2) * (bw + 6), y0 + (i / 2) * (bh + 4), bw, bh).build());
        }
        auto = addRenderableWidget(Button.builder(autoLabel(), b -> Payloads.toServer(new Payloads.Action(vehicle.getId(), Payloads.ACT_CREW, 3)))
                .bounds(width / 2 - 100, y0 + 4 * (bh + 4) + 10, 200, bh).build());
    }

    private Component autoLabel() {
        return Component.translatable("vehicleworks.pa.auto", Component.translatable(CrewScreen.autoPA(vehicle) ? "vehicleworks.panel.on" : "vehicleworks.panel.off"));
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved()) onClose();
        auto.setMessage(Component.translatable("vehicleworks.pa.auto", Component.translatable(CrewScreen.autoPA(vehicle) ? "vehicleworks.panel.on" : "vehicleworks.panel.off")));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(width / 2 - 175, height / 2 - 90, width / 2 + 175, height / 2 + 80, 0xD0181C22);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 80, 0xFFE8C878);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
