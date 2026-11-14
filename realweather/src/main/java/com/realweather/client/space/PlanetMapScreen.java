package com.realweather.client.space;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The planet map's list: every world, moon, comet, star, pulsar, black hole and nebula it can take you to, page by page. */
public class PlanetMapScreen extends Screen {
    private static int tab;
    private static final int[] PAGE = new int[8];
    private int pages = 1;

    public PlanetMapScreen() {
        super(Component.translatable("realweather.planet_map.title"));
    }

    /** What a tab lists: our solar system's worlds, other stars' planets, stars (pulsars, the black hole) or nebulae. */
    static List<Catalog.Target> worlds(int tab) {
        List<Catalog.Target> out = new ArrayList<>();
        if (tab == 5) {
            for (CosmicEvents.Event e : CosmicEvents.window()) {
                if (CosmicEvents.status(e) != CosmicEvents.OVER) {
                    out.add(e.target());
                }
            }
            return out;
        }
        if (tab >= 2) {
            for (Catalog.Target t : Catalog.group(tab == 2 ? Catalog.STARS : tab == 3 ? Catalog.NEBULAE : Catalog.GALAXIES)) {
                out.add(t);
            }
            return out;
        }
        for (Catalog.Target t : Catalog.group(tab == 0 ? Catalog.PLANETS : Catalog.EXOPLANETS)) {
            if (t.body.visitable()) {
                out.add(t);
            }
        }
        return out;
    }

    @Override
    protected void init() {
        String[] tabs = {"planets", "exoplanets", "stars", "nebulae", "galaxies", "events"};
        int tw = Math.min(90, (width - 40) / tabs.length);
        int tx = width / 2 - (tw * tabs.length + 4 * (tabs.length - 1)) / 2;
        for (int i = 0; i < tabs.length; i++) {
            int idx = i;
            Button b = Button.builder(Component.translatable("realweather.telescope.tab." + tabs[i]), btn -> {
                tab = idx;
                rebuildWidgets();
            }).bounds(tx + i * (tw + 4), 26, tw, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        List<Catalog.Target> list = worlds(tab);
        int cols = width >= 420 ? 3 : 2;
        int bw = Math.min(140, (width - 30) / cols - 4);
        int bh = 18, gap = 2, top = 52;
        int rows = Math.max(2, (height - top - 30) / (bh + gap));
        int perPage = rows * cols;
        pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int page = Math.min(PAGE[tab], pages - 1);
        PAGE[tab] = page;
        int x0 = width / 2 - (bw * cols + 4 * (cols - 1)) / 2;
        int from = page * perPage, to = Math.min(list.size(), from + perPage);
        for (int i = from; i < to; i++) {
            Catalog.Target t = list.get(i);
            int k = i - from;
            Button b = Button.builder(t.label(), btn -> minecraft.setScreen(new PlanetViewScreen(t)))
                    .bounds(x0 + (k % cols) * (bw + 4), top + (k / cols) * (bh + gap), bw, bh)
                    .tooltip(Tooltip.create(t.group == Catalog.EVENTS ? Component.translatable("realweather.event." + t.body.eventType + ".info")
                            : Component.translatable("realweather.target." + t.id + ".info"))).build();
            addRenderableWidget(b);
        }
        // Away on another world: the way home.
        if (minecraft.level != null && minecraft.level.dimension() == com.realweather.space.Worlds.DIMENSION) {
            addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.go_home"), btn -> {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.realweather.net.TravelPayload("", 2, 0, 0));
                onClose();
            }).bounds(width - 116, height - 24, 110, 20).build());
        }
        if (pages > 1) {
            Button prev = Button.builder(Component.literal("<"), btn -> {
                PAGE[tab] = Math.max(0, PAGE[tab] - 1);
                rebuildWidgets();
            }).bounds(width / 2 - 70, height - 24, 30, 20).build();
            prev.active = page > 0;
            Button next = Button.builder(Component.literal(">"), btn -> {
                PAGE[tab] = Math.min(pages - 1, PAGE[tab] + 1);
                rebuildWidgets();
            }).bounds(width / 2 + 40, height - 24, 30, 20).build();
            next.active = page < pages - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 9, 0xFFFFFF);
        if (pages > 1) {
            g.drawCenteredString(font, (PAGE[tab] + 1) + " / " + pages, width / 2, height - 18, 0xC0C0C0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
