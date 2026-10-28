package com.gunsmod.client.screen;

import com.gunsmod.menu.BotMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Soldier editor: weapon / armour, orders, face; the soldier is shown in 3D. */
public class BotScreen extends AbstractContainerScreen<BotMenu> {
    public BotScreen(BotMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 214;
        this.imageHeight = 202;
        this.inventoryLabelX = 26;
        this.inventoryLabelY = 109;
    }

    private List<UiKit.Btn> buttons() {
        int x = leftPos, y = topPos;
        int order = menu.data.get(0);
        List<UiKit.Btn> l = new ArrayList<>();
        l.add(new UiKit.Btn(0, x + 136, y + 18, 70, 16, Component.translatable("gunsmod.order.0"), true, order == 0));
        l.add(new UiKit.Btn(1, x + 136, y + 36, 70, 16, Component.translatable("gunsmod.order.1"), true, order == 1));
        l.add(new UiKit.Btn(2, x + 136, y + 54, 70, 16, Component.translatable("gunsmod.order.2"), true, order == 2));
        l.add(new UiKit.Btn(3, x + 136, y + 74, 70, 14, Component.translatable("gunsmod.ui.face"), true, false));
        l.add(new UiKit.Btn(4, x + 136, y + 90, 70, 14, Component.translatable("gunsmod.ui.grenades", menu.data.get(2)), true, false));
        return l;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.dark(g, x + 6, y + 18, 126, 88);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            UiKit.slot(g, x + s.x, y + s.y);
        }
        small(g, Component.translatable("gunsmod.ui.weapon").getString(), x + 9, y + 25, UiKit.MUTED);
        small(g, Component.translatable("gunsmod.ui.armor").getString(), x + 32, y + 25, UiKit.MUTED);
        small(g, Component.translatable("gunsmod.ui.remote").getString(), x + 9, y + 61, UiKit.MUTED);
        small(g, Component.translatable("gunsmod.ui.missile_rack").getString(), x + 32, y + 61, UiKit.MUTED);
        int links = menu.data.get(4);
        small(g, links < 0 ? Component.translatable("gunsmod.ui.no_remote").getString()
                : Component.translatable("gunsmod.ui.artillery", links).getString(), x + 9, y + 92, links < 0 ? UiKit.MUTED : UiKit.GOOD);
        if (menu.bot != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 106, y + 20, x + 131, y + 90, 26, 0.0625f, mx, my, menu.bot);
        }
        for (UiKit.Btn b : buttons()) UiKit.button(g, font, b, mx, my);
    }

    private void small(GuiGraphics g, String s, int x, int y, int col) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, s, 0, 0, col, false);
        g.pose().popPose();
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
