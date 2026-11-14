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
    private static int objCat;
    private static String query = "";
    private boolean typing;
    private static final int[] PAGE = new int[9];
    private int pages = 1;

    public PlanetMapScreen() {
        super(Component.translatable("realweather.planet_map.title"));
    }

    /** What a tab lists: our solar system's worlds, other stars' planets, stars (pulsars, the black hole) or nebulae. */
    static List<Catalog.Target> worlds(int tab) {
        List<Catalog.Target> out = new ArrayList<>();
        if (!query.isBlank()) {
            // A search through everything: names, examples and ids.
            String q = query.trim().toLowerCase(java.util.Locale.ROOT);
            List<Catalog.Target> all = new ArrayList<>(Catalog.TARGETS);
            for (CosmicEvents.Event e : CosmicEvents.window()) {
                all.add(e.target());
            }
            for (Catalog.Target t : all) {
                if (t.group == Catalog.PLANETS && !t.body.visitable()) {
                    continue;
                }
                if (t.label().getString().toLowerCase(java.util.Locale.ROOT).contains(q) || t.id.contains(q)) {
                    out.add(t);
                }
            }
            return out;
        }
        if (tab == 6) {
            for (Catalog.Target t : Catalog.group(Catalog.OBJECTS)) {
                if (CosmicObjects.ALL[t.body.eventType].group() == objCat) {
                    out.add(t);
                }
            }
            return out;
        }
        if (tab == 7) {
            for (String id : Bookmarks.all()) {
                Catalog.Target t = Catalog.byId(id);
                if (t != null) {
                    out.add(t);
                }
            }
            return out;
        }
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
        String[] tabs = {"planets", "exoplanets", "stars", "nebulae", "galaxies", "events", "objects", "bookmarks"};
        int tw = Math.min(80, (width - 40) / tabs.length - 4);
        int tx = width / 2 - (tw * tabs.length + 4 * (tabs.length - 1)) / 2;
        for (int i = 0; i < tabs.length; i++) {
            int idx = i;
            Button b = Button.builder(Component.translatable("realweather.telescope.tab." + tabs[i]), btn -> {
                tab = idx;
                query = "";
                typing = false;
                rebuildWidgets();
            }).bounds(tx + i * (tw + 4), 26, tw, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        // Search everything.
        net.minecraft.client.gui.components.EditBox search = new net.minecraft.client.gui.components.EditBox(font, width / 2 - 100, 50, 200, 16,
                Component.translatable("realweather.map.search"));
        search.setHint(Component.translatable("realweather.map.search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        search.setMaxLength(40);
        search.setValue(query);
        search.setResponder(v -> {
            if (!v.equals(query)) {
                query = v;
                PAGE[8] = 0;
                typing = true;
                rebuildWidgets();
            }
        });
        addRenderableWidget(search);
        if (typing) {
            setFocused(search);
            search.setFocused(true);
        }
        int top = 70;
        // The kinds of object, in five groups.
        if (tab == 6 && query.isBlank()) {
            int cw = Math.min(110, (width - 30) / 5 - 4);
            int cx = width / 2 - (cw * 5 + 16) / 2;
            for (int i = 0; i < 5; i++) {
                int idx = i;
                Button b = Button.builder(Component.translatable("realweather.object.cat." + i), btn -> {
                    objCat = idx;
                    PAGE[6] = 0;
                    rebuildWidgets();
                }).bounds(cx + i * (cw + 4), top, cw, 18).build();
                b.active = objCat != i;
                addRenderableWidget(b);
            }
            top += 22;
        }
        List<Catalog.Target> list = worlds(tab);
        int pageKey = query.isBlank() ? tab : 8;
        int cols = width >= 420 ? 3 : 2;
        int bw = Math.min(140, (width - 30) / cols - 4);
        int bh = 18, gap = 2;
        int rows = Math.max(2, (height - top - 30) / (bh + gap));
        int perPage = rows * cols;
        pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int page = Math.min(PAGE[pageKey], pages - 1);
        PAGE[pageKey] = page;
        int x0 = width / 2 - (bw * cols + 4 * (cols - 1)) / 2;
        int from = page * perPage, to = Math.min(list.size(), from + perPage);
        for (int i = from; i < to; i++) {
            Catalog.Target t = list.get(i);
            int k = i - from;
            Button b = Button.builder(t.label(), btn -> minecraft.setScreen(new PlanetViewScreen(t)))
                    .bounds(x0 + (k % cols) * (bw + 4), top + (k / cols) * (bh + gap), bw, bh)
                    .tooltip(Tooltip.create(t.group == Catalog.EVENTS ? Component.translatable("realweather.event." + t.body.eventType + ".info")
                            : t.group == Catalog.OBJECTS ? Component.translatable("realweather.object." + t.body.eventType + ".info")
                            : Component.translatable("realweather.target." + t.id + ".info"))).build();
            addRenderableWidget(b);
        }
        // The universe map and the simulation's controls.
        addRenderableWidget(Button.builder(Component.translatable("realweather.universe.short"), btn -> minecraft.setScreen(new UniverseMapScreen(this)))
                .bounds(4, 4, 76, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.sim.short"), btn -> minecraft.setScreen(new SimulationScreen(this)))
                .bounds(width - 80, 4, 76, 16).build());
        // Away on another world: the way home.
        if (minecraft.level != null && minecraft.level.dimension() == com.realweather.space.Worlds.DIMENSION) {
            addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.go_home"), btn -> {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.realweather.net.TravelPayload("", 2, 0, 0));
                onClose();
            }).bounds(width - 116, height - 24, 110, 20).build());
        }
        if (pages > 1) {
            Button prev = Button.builder(Component.literal("<"), btn -> {
                PAGE[pageKey] = Math.max(0, PAGE[pageKey] - 1);
                rebuildWidgets();
            }).bounds(width / 2 - 70, height - 24, 30, 20).build();
            prev.active = page > 0;
            Button next = Button.builder(Component.literal(">"), btn -> {
                PAGE[pageKey] = Math.min(pages - 1, PAGE[pageKey] + 1);
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
            g.drawCenteredString(font, (PAGE[query.isBlank() ? tab : 8] + 1) + " / " + pages, width / 2, height - 18, 0xC0C0C0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
