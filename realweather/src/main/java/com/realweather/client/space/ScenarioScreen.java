package com.realweather.client.space;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Scenarios to run: any of the 25 cosmic events made to happen now, from its beginning, to watch close up (at the pace
 * set in the simulation's controls). Each is a simulation.
 */
public class ScenarioScreen extends Screen {
    private static int page;
    private final Screen back;

    public ScenarioScreen(Screen back) {
        super(Component.translatable("realweather.sim.scenarios"));
        this.back = back;
    }

    @Override
    protected void init() {
        int cols = width >= 420 ? 3 : 2;
        int bw = Math.min(140, (width - 30) / cols - 4), bh = 18, top = 30;
        int rows = Math.max(2, (height - top - 34) / (bh + 2));
        int per = rows * cols, pages = (CosmicEvents.TYPES + per - 1) / per;
        page = Math.min(page, pages - 1);
        int x0 = width / 2 - (bw * cols + 4 * (cols - 1)) / 2;
        for (int i = page * per; i < Math.min(CosmicEvents.TYPES, (page + 1) * per); i++) {
            int type = i, k = i - page * per;
            addRenderableWidget(Button.builder(Component.translatable("realweather.event." + i), b -> {
                // From just before it begins.
                minecraft.setScreen(new PlanetViewScreen(CosmicEvents.testTarget(type, -0.03)));
            }).bounds(x0 + (k % cols) * (bw + 4), top + (k / cols) * (bh + 2), bw, bh)
                    .tooltip(Tooltip.create(Component.translatable("realweather.event." + i + ".info"))).build());
        }
        if (pages > 1) {
            int pg = pages;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = (page + pg - 1) % pg;
                rebuildWidgets();
            }).bounds(width / 2 - 90, height - 26, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = (page + 1) % pg;
                rebuildWidgets();
            }).bounds(width / 2 + 60, height - 26, 30, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFE080);
        g.drawCenteredString(font, Component.translatable("realweather.obs.note"), width / 2, height - 38, 0xFF808080);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
