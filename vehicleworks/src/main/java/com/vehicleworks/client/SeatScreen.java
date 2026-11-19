package com.vehicleworks.client;

import com.vehicleworks.network.Payloads;
import com.vehicleworks.vehicle.CabinSeat;
import com.vehicleworks.vehicle.VehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Passenger seat controls (only the passenger's own seat: no aircraft controls). The buttons
 * depend on the seat class; each one moves the seat model, its screen, tray, light or suite door.
 */
public class SeatScreen extends Screen {
    private final VehicleEntity vehicle;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Integer> ids = new ArrayList<>();
    private net.minecraft.client.gui.components.EditBox link;
    private int videoY = -1;

    public SeatScreen(VehicleEntity v) {
        super(Component.translatable("vehicleworks.cabin.title"));
        this.vehicle = v;
    }

    private int cls() {
        return Minecraft.getInstance().player == null ? 0 : vehicle.seatClassOf(Minecraft.getInstance().player);
    }

    private int state() {
        Long k = Minecraft.getInstance().player == null ? null : vehicle.seats.get(Minecraft.getInstance().player.getUUID());
        return k == null ? CabinSeat.DEFAULT : vehicle.cabin.get((long) k);
    }

    static final int[] ORDER = {CabinSeat.RECLINE_DOWN, CabinSeat.RECLINE_UP, CabinSeat.SLIDE_FWD, CabinSeat.SLIDE_BACK, CabinSeat.LEG_UP, CabinSeat.LEG_DOWN,
            CabinSeat.FLAT, CabinSeat.TRAY, CabinSeat.SCREEN, CabinSeat.PAGE, CabinSeat.LIGHT, CabinSeat.DOOR, CabinSeat.CALL, CabinSeat.SHADE};

    @Override
    protected void init() {
        buttons.clear();
        ids.clear();
        int cls = cls();
        List<Integer> avail = new ArrayList<>();
        for (int c : ORDER) if (CabinSeat.has(cls, c)) avail.add(c);
        int cols = 2, bw = 150, bh = 20, gap = 4;
        int x0 = width / 2 - (cols * bw + gap) / 2, y0 = height / 2 - ((avail.size() + 1) / 2) * (bh + gap) / 2 + 10;
        for (int i = 0; i < avail.size(); i++) {
            int c = avail.get(i);
            Button b = addRenderableWidget(Button.builder(label(c), btn -> press(c))
                    .bounds(x0 + (i % cols) * (bw + gap), y0 + (i / cols) * (bh + gap), bw, bh).build());
            buttons.add(b);
            ids.add(c);
        }
        // personal video (YouTube or any link) on the seat screen, through the WATERMeDIA mod
        videoY = -1;
        if (CabinSeat.has(cls, CabinSeat.SCREEN)) {
            videoY = y0 + ((avail.size() + 1) / 2) * (bh + gap) + 8;
            link = new net.minecraft.client.gui.components.EditBox(font, x0, videoY + 12, cols * bw + gap - 128, 20, Component.translatable("vehicleworks.video.link"));
            link.setMaxLength(512);
            link.setHint(Component.translatable("vehicleworks.video.hint"));
            link.setValue(SeatVideo.url());
            addRenderableWidget(link);
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.video.play"), b -> playVideo())
                    .bounds(x0 + cols * bw + gap - 124, videoY + 12, 60, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vehicleworks.video.stop"), b -> SeatVideo.stop())
                    .bounds(x0 + cols * bw + gap - 60, videoY + 12, 60, 20).build());
        }
    }

    private void playVideo() {
        Long k = Minecraft.getInstance().player == null ? null : vehicle.seats.get(Minecraft.getInstance().player.getUUID());
        if (k == null || link == null) return;
        if (!CabinSeat.screen(state())) press(CabinSeat.SCREEN); // the screen switches on for the video
        SeatVideo.play(vehicle, k, link.getValue());
    }

    private void press(int control) {
        Payloads.toServer(new Payloads.Action(vehicle.getId(), Payloads.ACT_CABIN, control));
    }

    private Component label(int c) {
        int s = state();
        String on = Component.translatable("vehicleworks.panel.on").getString(), off = Component.translatable("vehicleworks.panel.off").getString();
        return switch (c) {
            case CabinSeat.FLAT -> Component.translatable("vehicleworks.cabin.ctl." + c, CabinSeat.flat(s) ? on : off);
            case CabinSeat.TRAY -> Component.translatable("vehicleworks.cabin.ctl." + c, Component.translatable(CabinSeat.tray(s) ? "vehicleworks.cabin.open" : "vehicleworks.cabin.closed"));
            case CabinSeat.SCREEN -> Component.translatable("vehicleworks.cabin.ctl." + c, CabinSeat.screen(s) ? on : off);
            case CabinSeat.PAGE -> Component.translatable("vehicleworks.cabin.ctl." + c, Component.translatable("vehicleworks.ife.page." + CabinSeat.page(s)));
            case CabinSeat.LIGHT -> Component.translatable("vehicleworks.cabin.ctl." + c, Component.translatable("vehicleworks.cabin.light." + CabinSeat.light(s)));
            case CabinSeat.DOOR -> Component.translatable("vehicleworks.cabin.ctl." + c, Component.translatable(CabinSeat.door(s) ? "vehicleworks.cabin.open" : "vehicleworks.cabin.closed"));
            case CabinSeat.CALL -> Component.translatable("vehicleworks.cabin.ctl." + c, CabinSeat.call(s) ? on : off);
            case CabinSeat.SHADE -> Component.translatable("vehicleworks.cabin.ctl." + c, Component.translatable(CabinSeat.shade(s) ? "vehicleworks.cabin.closed" : "vehicleworks.cabin.open"));
            default -> Component.translatable("vehicleworks.cabin.ctl." + c);
        };
    }

    @Override
    public void tick() {
        if (vehicle.isRemoved() || Minecraft.getInstance().player == null || Minecraft.getInstance().player.getVehicle() != vehicle) { onClose(); return; }
        for (int i = 0; i < buttons.size(); i++) buttons.get(i).setMessage(label(ids.get(i)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int s = state(), cls = cls();
        int top = buttons.isEmpty() ? height / 2 : buttons.get(0).getY();
        g.fill(width / 2 - 170, top - 50, width / 2 + 170, top + ((buttons.size() + 1) / 2) * 24 + 12, 0xD0181C22);
        g.renderOutline(width / 2 - 170, top - 50, 340, ((buttons.size() + 1) / 2) * 24 + 62, 0xFF59626D);
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, Component.translatable("vehicleworks.cabin.class." + cls), width / 2, top - 42, 0xFFE8C878);
        g.drawCenteredString(font, Component.translatable("vehicleworks.cabin.status", CabinSeat.recline(s) * 100 / Math.max(1, CabinSeat.maxRecline(cls)),
                CabinSeat.maxLeg(cls) > 0 ? CabinSeat.leg(s) * 100 / CabinSeat.maxLeg(cls) : 0, CabinSeat.slide(s) - 2), width / 2, top - 28, 0xFFB8C2CC);
        g.drawCenteredString(font, Component.translatable("vehicleworks.cabin.passenger_only"), width / 2, top - 16, 0xFF7E8892);
        if (videoY >= 0) {
            g.fill(width / 2 - 170, videoY - 2, width / 2 + 170, videoY + 38, 0xD0181C22);
            g.renderOutline(width / 2 - 170, videoY - 2, 340, 40, 0xFF59626D);
            String st = SeatVideo.available() ? SeatVideo.status() : SeatVideo.status();
            g.drawString(font, Component.translatable("vehicleworks.video.title").getString()
                    + (st.isEmpty() ? "" : "  -  " + Component.translatable(st).getString()), width / 2 - 164, videoY + 1, SeatVideo.available() ? 0xFFE8C878 : 0xFFFF8060);
            link.render(g, mx, my, partial);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
