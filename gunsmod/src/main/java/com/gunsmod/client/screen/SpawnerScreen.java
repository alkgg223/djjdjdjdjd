package com.gunsmod.client.screen;

import com.gunsmod.item.DogTagItem;
import com.gunsmod.menu.SpawnerMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Deployment pad: dog tag slot, how many soldiers to keep alive, respawn delay, on/off. */
public class SpawnerScreen extends AbstractContainerScreen<SpawnerMenu> {
    public SpawnerScreen(SpawnerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 214;
        this.imageHeight = 202;
        this.inventoryLabelX = 26;
        this.inventoryLabelY = 109;
    }

    private List<UiKit.Btn> buttons() {
        int x = leftPos, y = topPos;
        List<UiKit.Btn> l = new ArrayList<>();
        l.add(new UiKit.Btn(0, x + 70, y + 30, 16, 16, Component.literal("-"), true, false));
        l.add(new UiKit.Btn(1, x + 128, y + 30, 16, 16, Component.literal("+"), true, false));
        l.add(new UiKit.Btn(2, x + 70, y + 56, 16, 16, Component.literal("-"), true, false));
        l.add(new UiKit.Btn(3, x + 128, y + 56, 16, 16, Component.literal("+"), true, false));
        l.add(new UiKit.Btn(4, x + 150, y + 30, 56, 18, Component.translatable(menu.active() ? "gunsmod.ui.stop" : "gunsmod.ui.start"), true, menu.active()));
        l.add(new UiKit.Btn(5, x + 150, y + 56, 56, 16, Component.translatable("gunsmod.ui.recall"), true, false));
        return l;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.dark(g, x + 6, y + 20, imageWidth - 12, 80);
        Slot tag = menu.slots.get(0);
        UiKit.slot(g, x + tag.x, y + tag.y);
        g.drawString(font, Component.translatable("gunsmod.ui.tag"), x + 12, y + 40, UiKit.MUTED, false);
        g.drawString(font, Component.translatable("gunsmod.ui.count"), x + 88, y + 22, UiKit.MUTED, false);
        String c = String.valueOf(menu.count());
        g.drawString(font, c, x + 107 - font.width(c) / 2, y + 34, 0xFFFFFFFF, true);
        g.drawString(font, Component.translatable("gunsmod.ui.delay"), x + 88, y + 48, UiKit.MUTED, false);
        String d = menu.delay() + "s";
        g.drawString(font, d, x + 107 - font.width(d) / 2, y + 60, 0xFFFFFFFF, true);
        for (UiKit.Btn b : buttons()) UiKit.button(g, font, b, mx, my);
        Component alive = Component.translatable("gunsmod.ui.alive", menu.alive(), menu.count());
        g.drawString(font, alive, x + 12, y + 80, menu.active() ? UiKit.GOOD : UiKit.MUTED, false);
        if (!DogTagItem.hasData(menu.tag())) {
            g.drawString(font, Component.translatable("gunsmod.ui.need_tag"), x + 12, y + 90, UiKit.BAD, false);
        }
        for (int i = 1; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            UiKit.slot(g, x + s.x, y + s.y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        UiKit.title(g, font, this.title, 8, 6);
        g.drawString(font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, UiKit.MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (UiKit.Btn b : buttons()) {
            if (b.hit(mx, my)) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, b.id());
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
