package com.trafficcraft.client.gui;

import com.trafficcraft.net.TuningPayload;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tuning screen (opened by right-clicking a car with the tuning tool): one row per part slot of this car
 * (front bumper, rear bumper, exhaust, spoiler, bonnet, wheels, side skirts) with previous / next buttons and the part name,
 * a paint palette, "Random" and "Factory". Every change is applied at once so the car can be watched behind the screen.
 */
public class TuningScreen extends Screen {
    static final String[] SLOTS = {"fb", "rb", "ex", "sp", "hd", "wh", "sk"};
    static final int[] PAINT = {0xC8102E, 0xE8EAEC, 0x0D0D0F, 0x1F5FBF, 0xF1C40F, 0x3FAE2A, 0xFF7A00, 0x6A6E73, 0x7B1FA2, 0x00A3A3, 0x8C1E1E, 0xB9BDC2};
    final VehicleEntity v;
    final Map<String, Integer> sel = new LinkedHashMap<>();
    int color;
    final List<String> slots = new ArrayList<>();

    public TuningScreen(VehicleEntity v) {
        super(Component.translatable("screen.trafficcraft.tuning"));
        this.v = v;
        VehicleSpec s = v.spec();
        for (String k : SLOTS) if (s.catalog.getOrDefault(k, 0) > 1) {
            slots.add(k);
            sel.put(k, v.cfg(k));
        }
        color = v.getColor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    int top() {
        return Math.max(30, height / 2 - (slots.size() * 24 + 90) / 2);
    }

    @Override
    protected void init() {
        int x0 = 20, y = top() + 16;
        for (String k : slots) {
            final String slot = k;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> step(slot, -1)).bounds(x0 + 110, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> step(slot, 1)).bounds(x0 + 290, y, 20, 20).build());
            y += 24;
        }
        y += 8;
        for (int i = 0; i < PAINT.length; i++) {
            final int c = PAINT[i];
            addRenderableWidget(new Button(x0 + 110 + (i % 6) * 34, y + (i / 6) * 22, 30, 18, Component.empty(), b -> {
                color = c;
                send();
            }, sup -> sup.get()) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() || color == c ? 0xFFFFFFFF : 0xFF202024);
                    g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0xFF000000 | c);
                }
            });
        }
        y += 52;
        addRenderableWidget(Button.builder(Component.translatable("screen.trafficcraft.tuning.random"), b -> randomise()).bounds(x0, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.trafficcraft.tuning.factory"), b -> {
            for (String k : slots) sel.put(k, 0);
            send();
        }).bounds(x0 + 100, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x0 + 200, y, 110, 20).build());
    }

    void step(String slot, int d) {
        int n = v.spec().catalog.getOrDefault(slot, 1);
        sel.put(slot, Math.floorMod(sel.getOrDefault(slot, 0) + d, n));
        send();
    }

    /** development test: a visible set of parts */
    public void testFit() {
        for (String k : slots) sel.put(k, Math.min(v.spec().catalog.get(k) - 1, k.equals("sp") ? 3 : 2));
        color = 0x1F5FBF;
        send();
    }

    void randomise() {
        var r = v.getRandom();
        for (String k : slots) sel.put(k, r.nextInt(v.spec().catalog.get(k)));
        color = PAINT[r.nextInt(PAINT.length)];
        send();
    }

    void send() {
        StringBuilder b = new StringBuilder();
        for (var en : sel.entrySet()) if (en.getValue() > 0) b.append(en.getKey()).append('=').append(en.getValue()).append(';');
        PacketDistributor.sendToServer(new TuningPayload(v.getId(), b.toString(), color));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // keep the car visible: only a translucent panel on the left
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x0 = 20, y0 = top();
        int h = slots.size() * 24 + 120;
        g.fill(x0 - 10, y0 - 10, x0 + 320, y0 + h, 0xB0101014);
        g.drawString(font, Component.translatable("screen.trafficcraft.tuning").append(" - " + v.spec().name), x0, y0, 0xFFFFD060);
        int y = y0 + 16;
        for (String k : slots) {
            g.drawString(font, Component.translatable("tuning.trafficcraft.slot." + k), x0, y + 6, 0xFFE0E0E0);
            List<String> names = v.spec().catalogNames.getOrDefault(k, List.of());
            int i = sel.getOrDefault(k, 0);
            String nm = i < names.size() ? names.get(i) : "#" + i;
            int n = v.spec().catalog.getOrDefault(k, 1);
            g.drawCenteredString(font, nm + "  (" + (i + 1) + "/" + n + ")", x0 + 210, y + 6, i == 0 ? 0xFFB0B0B0 : 0xFF80E0FF);
            y += 24;
        }
        g.drawString(font, Component.translatable("tuning.trafficcraft.paint"), x0, y + 14, 0xFFE0E0E0);
        super.render(g, mx, my, pt);
    }
}
