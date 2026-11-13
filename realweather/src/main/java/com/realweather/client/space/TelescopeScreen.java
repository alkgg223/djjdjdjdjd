package com.realweather.client.space;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The telescope's list: the solar system, stars and exoplanets it can be pointed at, page by page. */
public class TelescopeScreen extends Screen {
    private static int tab;
    private static final int[] PAGE = new int[4];
    private final BlockPos pos;
    private int pages = 1;

    public TelescopeScreen(BlockPos pos) {
        super(Component.translatable("realweather.telescope.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        String[] tabs = {"planets", "stars", "exoplanets", "nebulae"};
        int tw = Math.min(100, (width - 40) / 4);
        int tx = width / 2 - (tw * 4 + 12) / 2;
        for (int i = 0; i < 4; i++) {
            int idx = i;
            Button b = Button.builder(Component.translatable("realweather.telescope.tab." + tabs[i]), btn -> {
                tab = idx;
                rebuildWidgets();
            }).bounds(tx + i * (tw + 4), 26, tw, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        List<Catalog.Target> list = Catalog.group(tab);
        int cols = width >= 420 ? 3 : 2;
        int bw = Math.min(140, (width - 30) / cols - 4);
        int bh = 18, gap = 2, top = 52;
        // As many rows as fit on this screen (phones included); the rest on further pages.
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
            Button b = Button.builder(Component.translatable("realweather.target." + t.id), btn -> {
                Telescope.start(pos, t);
                onClose();
            }).bounds(x0 + (k % cols) * (bw + 4), top + (k / cols) * (bh + gap), bw, bh)
                    .tooltip(Tooltip.create(Component.translatable("realweather.target." + t.id + ".info"))).build();
            addRenderableWidget(b);
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
