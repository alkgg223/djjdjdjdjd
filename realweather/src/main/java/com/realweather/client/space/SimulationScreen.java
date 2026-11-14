package com.realweather.client.space;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The simulation's controls: how fast the universe's clock runs (paused up to ten thousand days a day), how fast worlds
 * turn in the planet map, what to show (magnetic fields, orbits, belts), the cosmic events' pace; and the way to the
 * universe map, scenarios to run, the stellar life cycles and comparing two objects.
 */
public class SimulationScreen extends Screen {
    private static final double[] SPINS = {0, 1, 5, 20};
    private static final double[] EVENT = {0.25, 0.5, 1, 2, 4, 8};
    private final Screen back;

    public SimulationScreen(Screen back) {
        super(Component.translatable("realweather.sim.title"));
        this.back = back;
    }

    private static String rate(double r) {
        return r == 0 ? "-" : r < 1 ? String.format(Locale.ROOT, "x%.2f", r) : String.format(Locale.ROOT, "x%,.0f", r);
    }

    private static double next(double[] list, double v) {
        for (int i = 0; i < list.length; i++) {
            if (Math.abs(list[i] - v) < 1.0E-9) {
                return list[(i + 1) % list.length];
            }
        }
        return list[0];
    }

    @Override
    protected void init() {
        int bw = Math.min(200, width / 2 - 20), x0 = width / 2 - bw - 4, x1 = width / 2 + 4, y = 30, gap = 22;
        // Left: the clock and what to show.
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.time", SpaceTime.rate() == 0
                ? Component.translatable("realweather.sim.paused") : Component.literal(rate(SpaceTime.rate()))), b -> {
            SpaceTime.setRate(minecraft.level, next(SpaceTime.RATES, SpaceTime.rate()));
            rebuildWidgets();
        }).bounds(x0, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.spin", SpaceTime.spin == 0
                ? Component.translatable("options.off") : Component.literal(rate(SpaceTime.spin))), b -> {
            SpaceTime.spin = next(SPINS, SpaceTime.spin);
            rebuildWidgets();
        }).bounds(x0, y + gap, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.fields", onOff(SpaceTime.fields)), b -> {
            SpaceTime.fields = !SpaceTime.fields;
            rebuildWidgets();
        }).bounds(x0, y + gap * 2, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.belts", onOff(SpaceTime.belts)), b -> {
            SpaceTime.belts = !SpaceTime.belts;
            rebuildWidgets();
        }).bounds(x0, y + gap * 3, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.events", rate(CosmicEvents.rate)), b -> {
            CosmicEvents.rate = next(EVENT, CosmicEvents.rate);
            rebuildWidgets();
        }).bounds(x0, y + gap * 4, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(CosmicEvents.paused ? "realweather.obs.resume" : "realweather.obs.pause"), b -> {
            CosmicEvents.paused = !CosmicEvents.paused;
            rebuildWidgets();
        }).bounds(x0, y + gap * 5, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.notify", onOff(CosmicEvents.notifyAll)), b -> {
            CosmicEvents.notifyAll = !CosmicEvents.notifyAll;
            rebuildWidgets();
        }).bounds(x0, y + gap * 6, bw, 20).build());
        // Right: the other tools.
        addRenderableWidget(Button.builder(Component.translatable("realweather.universe.title"), b -> minecraft.setScreen(new UniverseMapScreen(this)))
                .bounds(x1, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.scenarios"), b -> minecraft.setScreen(new ScenarioScreen(this)))
                .bounds(x1, y + gap, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.object.103"), b -> minecraft.setScreen(new PlanetViewScreen(
                Catalog.byId(CosmicObjects.id(103))))).bounds(x1, y + gap * 2, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.object.104"), b -> minecraft.setScreen(new PlanetViewScreen(
                Catalog.byId(CosmicObjects.id(104))))).bounds(x1, y + gap * 3, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.compare.title"), b -> minecraft.setScreen(new CompareScreen(this)))
                .bounds(x1, y + gap * 4, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private static Component onOff(boolean v) {
        return Component.translatable(v ? "options.on" : "options.off");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFE080);
        g.drawCenteredString(font, Component.translatable("realweather.sim.days", String.format(Locale.ROOT, "%,.1f",
                SpaceTime.days(minecraft.level, 0F))), width / 2, height - 40, 0xFFB0C0D0);
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
