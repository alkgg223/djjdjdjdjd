package com.realweather.client.space;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The telescope's list: the planets, bright stars and exoplanets it can be pointed at. */
public class TelescopeScreen extends Screen {
    private static int tab;
    private final BlockPos pos;

    public TelescopeScreen(BlockPos pos) {
        super(Component.translatable("realweather.telescope.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        String[] tabs = {"planets", "stars", "exoplanets"};
        int tw = Math.min(110, (width - 40) / 3);
        int tx = width / 2 - (tw * 3 + 8) / 2;
        for (int i = 0; i < 3; i++) {
            int idx = i;
            Button b = Button.builder(Component.translatable("realweather.telescope.tab." + tabs[i]), btn -> {
                tab = idx;
                rebuildWidgets();
            }).bounds(tx + i * (tw + 4), 28, tw, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        List<Catalog.Target> list = Catalog.group(tab);
        int cols = 2;
        int bw = Math.min(150, (width - 40) / cols);
        int rows = (list.size() + cols - 1) / cols;
        int bh = 20, gap = 2;
        int top = 56;
        // Squeeze the buttons on a small screen.
        if (top + rows * (bh + gap) > height - 8) {
            bh = Math.max(14, (height - 8 - top) / rows - gap);
        }
        int x0 = width / 2 - (bw * cols + 6) / 2;
        for (int i = 0; i < list.size(); i++) {
            Catalog.Target t = list.get(i);
            Button b = Button.builder(Component.translatable("realweather.target." + t.id), btn -> {
                Telescope.start(pos, t);
                onClose();
            }).bounds(x0 + (i % cols) * (bw + 6), top + (i / cols) * (bh + gap), bw, bh)
                    .tooltip(Tooltip.create(Component.translatable("realweather.target." + t.id + ".info"))).build();
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
