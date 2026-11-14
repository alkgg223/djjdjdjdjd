package com.realweather.client.space;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Cosmic Event Observatory's screen: the cosmic events coming up, going on and over, each with where it is (its
 * coordinates, distance and host), how long until it begins or how far through it is; track one (a message when it
 * begins), watch it through the observatory's telescope or close up, replay it, and pause or speed up what you
 * watch. A scan picks out the unstable stars about to explode. Every event here is a simulation.
 */
public class ObservatoryScreen extends Screen {
    private static final double[] SPEEDS = {0.25, 0.5, 1, 2, 4, 8};
    private static int page;
    private static boolean scan;
    private final BlockPos pos;
    private List<CosmicEvents.Event> list = new ArrayList<>();
    private int pages = 1, rows = 4, top = 50;

    public ObservatoryScreen(BlockPos pos) {
        super(Component.translatable("block.realweather.cosmic_observatory"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        list = new ArrayList<>(CosmicEvents.window());
        if (scan) {
            list.removeIf(e -> !(e.type() == CosmicEvents.SUPERNOVA || e.type() == CosmicEvents.HYPERNOVA) || CosmicEvents.status(e) == CosmicEvents.OVER);
        }
        // The controls along the top.
        int bw = Math.min(110, (width - 30) / 4);
        int x = width / 2 - (bw * 4 + 12) / 2;
        addRenderableWidget(Button.builder(Component.translatable(scan ? "realweather.obs.all" : "realweather.obs.scan"), b -> {
            scan = !scan;
            page = 0;
            rebuildWidgets();
        }).bounds(x, 22, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(CosmicEvents.paused ? "realweather.obs.resume" : "realweather.obs.pause"), b -> {
            CosmicEvents.paused = !CosmicEvents.paused;
            rebuildWidgets();
        }).bounds(x + bw + 4, 22, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.obs.speed", fmt(CosmicEvents.rate)), b -> {
            int i = 0;
            for (int k = 0; k < SPEEDS.length; k++) {
                if (Math.abs(SPEEDS[k] - CosmicEvents.rate) < 1.0E-6) {
                    i = k;
                }
            }
            CosmicEvents.rate = SPEEDS[(i + 1) % SPEEDS.length];
            rebuildWidgets();
        }).bounds(x + (bw + 4) * 2, 22, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + (bw + 4) * 3, 22, bw, 20).build());
        // One row per event.
        rows = Math.max(2, (height - top - 34) / 34);
        pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        int sw = 52;
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) {
                break;
            }
            CosmicEvents.Event e = list.get(idx);
            int y = top + i * 34;
            int bx = width - 8 - sw * 4 - 9;
            addRenderableWidget(Button.builder(Component.translatable(CosmicEvents.tracked(e) ? "realweather.obs.untrack" : "realweather.obs.track"), b -> {
                CosmicEvents.track(e);
                rebuildWidgets();
            }).bounds(bx, y + 6, sw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("realweather.obs.observe"), b -> {
                Telescope.start(pos, e.target());
                onClose();
            }).bounds(bx + sw + 3, y + 6, sw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("realweather.obs.view"), b -> minecraft.setScreen(new PlanetViewScreen(e.target())))
                    .bounds(bx + (sw + 3) * 2, y + 6, sw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("realweather.obs.replay"), b -> {
                CosmicEvents.replay(e);
                minecraft.setScreen(new PlanetViewScreen(e.target()));
            }).bounds(bx + (sw + 3) * 3, y + 6, sw, 20).build());
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuildWidgets();
            }).bounds(width / 2 - 70, height - 26, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = Math.min(pages - 1, page + 1);
                rebuildWidgets();
            }).bounds(width / 2 + 40, height - 26, 30, 20).build());
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.format(Locale.ROOT, "x%.0f", v) : String.format(Locale.ROOT, "x%.2f", v);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFE080);
        long now = minecraft.level == null ? 0 : minecraft.level.getGameTime();
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) {
                break;
            }
            CosmicEvents.Event e = list.get(idx);
            int y = top + i * 34;
            g.fill(6, y, width - 6, y + 32, 0x60101828);
            int st = CosmicEvents.status(e);
            Component status = switch (st) {
                case CosmicEvents.UPCOMING -> Component.translatable("realweather.obs.upcoming", String.format(Locale.ROOT, "%.1f", (e.start() - now) / 1200.0));
                case CosmicEvents.ACTIVE -> Component.translatable("realweather.obs.active", String.format(Locale.ROOT, "%.0f",
                        100.0 * (now - e.start()) / e.length()));
                default -> Component.translatable("realweather.obs.over");
            };
            int col = st == CosmicEvents.ACTIVE ? 0xFF80FF90 : st == CosmicEvents.UPCOMING ? 0xFFFFD070 : 0xFF909090;
            g.drawString(font, Component.translatable("realweather.event." + e.type()).copy().append("  ").append(status), 10, y + 4, col, true);
            g.drawString(font, Component.translatable("realweather.obs.where", Component.translatable(e.host()), CosmicEvents.distance(e.type()),
                    CosmicEvents.coordinates(e.dir())), 10, y + 16, 0xFFB8C8E0, false);
        }
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("realweather.obs.none"), width / 2, top + 10, 0xFFB0B0B0);
        }
        g.drawCenteredString(font, Component.translatable("realweather.obs.note"), width / 2, height - 12, 0xFF808080);
        if (pages > 1) {
            g.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 20, 0xFFC0C0C0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
