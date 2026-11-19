package com.vehicleworks.client;

import com.vehicleworks.component.ComponentType;
import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.CabinSeat;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Attendant panel (cabin crew seat X, or right-click the panel): cabin lights, seat belt signs,
 * announcements, automatic announcements, passenger calls, boarding / deplaning, interphone to
 * the cockpit, and the status of every door.
 */
public class CrewScreen extends Screen {
    private final VehicleEntity vehicle;
    private final java.util.List<Button> state = new java.util.ArrayList<>();

    public CrewScreen(VehicleEntity v) {
        super(Component.translatable("vehicleworks.crew.title"));
        this.vehicle = v;
    }

    static boolean autoPA(VehicleEntity v) {
        return v.telemetry.autoPA;
    }

    private void crew(int action, int param) {
        Payloads.toServer(new Payloads.Action(vehicle.getId(), Payloads.ACT_CREW, action | (param << 8)));
    }

    @Override
    protected void init() {
        state.clear();
        int bw = 150, bh = 20, x0 = width / 2 - bw - 4, y0 = height / 2 - 70;
        state.add(addRenderableWidget(Button.builder(Component.empty(), b -> crew(0, 0)).bounds(x0, y0, bw, bh).build()));
        state.add(addRenderableWidget(Button.builder(Component.empty(), b -> crew(1, 0)).bounds(x0 + bw + 8, y0, bw, bh).build()));
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.crew.announce"), b -> minecraft.setScreen(new AnnouncementScreen(vehicle))).bounds(x0, y0 + 24, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.crew.reset_calls"), b -> crew(4, 0)).bounds(x0 + bw + 8, y0 + 24, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.crew.board"), b -> crew(5, 0)).bounds(x0, y0 + 48, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.crew.deplane"), b -> crew(6, 0)).bounds(x0 + bw + 8, y0 + 48, bw, bh).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicleworks.crew.call_cockpit"), b -> crew(7, 0)).bounds(x0, y0 + 72, bw * 2 + 8, bh).build());
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved()) { onClose(); return; }
        VehicleTelemetry t = vehicle.telemetry;
        state.get(0).setMessage(Component.translatable("vehicleworks.crew.lights", Component.translatable("vehicleworks.crew.lights." + Math.max(0, Math.min(3, t.cabinLights)))));
        state.get(1).setMessage(Component.translatable("vehicleworks.crew.belts", Component.translatable(t.seatbelt ? "vehicleworks.panel.on" : "vehicleworks.panel.off")));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = width / 2 - 165, y = height / 2 - 100;
        g.fill(x, y, x + 330, y + 260, 0xD0181C22);
        g.renderOutline(x, y, 330, 260, 0xFF59626D);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, y + 8, 0xFFE8C878);
        // doors and passenger calls
        int yy = height / 2 + 30, i = 0, calls = 0;
        StringBuilder doors = new StringBuilder();
        for (long k : vehicle.structure.blockEntities().keySet()) {
            ComponentType ct = VehicleBuilder.typeOf(vehicle.structure.get(k));
            if (ct == ComponentType.AIRCRAFT_DOOR) {
                boolean open = (vehicle.cabin.get(k) & 1) != 0;
                String side = BlockPos.getX(k) < 0 ? "L" : "R";
                doors.append(side).append(++i).append(open ? " OPEN  " : " CLSD  ");
            }
            if (ct != null && ct.seatClass() > 0 && CabinSeat.call(vehicle.cabin.get(k))) calls++;
        }
        g.drawString(font, Component.translatable("vehicleworks.crew.doors"), x + 12, yy, 0xFF9AA4AE);
        g.drawString(font, doors.length() == 0 ? "-" : doors.toString(), x + 12, yy + 11, vehicle.telemetry.doors != 0 ? 0xFFFFB020 : 0xFF40FF70);
        g.drawString(font, Component.translatable("vehicleworks.crew.calls", calls), x + 12, yy + 28, calls > 0 ? 0xFF38D3FF : 0xFF9AA4AE);
        g.drawString(font, Component.translatable("vehicleworks.crew.passengers", vehicle.getPassengers().stream().filter(e -> e instanceof com.vehicleworks.vehicle.NpcEntity n && n.role() == 0).count()), x + 12, yy + 42, 0xFFB8C2CC);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
