package com.gunsmod.client.screen;

import com.gunsmod.block.ArenaBlockEntity;
import com.gunsmod.menu.ArenaMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/** Battle command console: N vs M, player / AI-only, loadout, start / stop and live scoreboard. */
public class ArenaScreen extends AbstractContainerScreen<ArenaMenu> {
    public ArenaScreen(ArenaMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 280;
        this.imageHeight = 184;
    }

    private List<UiKit.Btn> buttons() {
        int x = leftPos, y = topPos;
        boolean running = menu.get(4) == ArenaBlockEntity.RUNNING;
        List<UiKit.Btn> l = new ArrayList<>();
        l.add(new UiKit.Btn(0, x + 18, y + 64, 20, 18, Component.literal("-"), !running, false));
        l.add(new UiKit.Btn(1, x + 92, y + 64, 20, 18, Component.literal("+"), !running, false));
        l.add(new UiKit.Btn(2, x + 168, y + 64, 20, 18, Component.literal("-"), !running, false));
        l.add(new UiKit.Btn(3, x + 242, y + 64, 20, 18, Component.literal("+"), !running, false));
        l.add(new UiKit.Btn(4, x + 18, y + 96, 120, 18, Component.translatable(menu.get(2) == ArenaBlockEntity.MODE_PLAYER ? "gunsmod.arena.mode.player" : "gunsmod.arena.mode.ai"), !running, false));
        l.add(new UiKit.Btn(5, x + 142, y + 96, 120, 18, Component.translatable("gunsmod.arena.loadout." + ArenaBlockEntity.LOADOUTS[Math.floorMod(menu.get(3), ArenaBlockEntity.LOADOUTS.length)]), !running, false));
        l.add(new UiKit.Btn(6, x + 18, y + 150, 120, 22, Component.translatable(running ? "gunsmod.arena.restart" : "gunsmod.arena.start"), true, false));
        l.add(new UiKit.Btn(7, x + 142, y + 150, 120, 22, Component.translatable("gunsmod.arena.stop"), true, true));
        return l;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        UiKit.panel(g, x, y, imageWidth, imageHeight);
        UiKit.hazard(g, x + 6, y + 20, imageWidth - 12);
        // teams
        UiKit.dark(g, x + 12, y + 34, 106, 54);
        UiKit.dark(g, x + 162, y + 34, 106, 54);
        g.fill(x + 14, y + 36, x + 116, y + 46, 0x8040A040);
        g.fill(x + 164, y + 36, x + 266, y + 46, 0x80A04030);
        g.drawString(font, Component.translatable("gunsmod.team.green"), x + 18, y + 37, 0xFFFFFFFF, true);
        g.drawString(font, Component.translatable("gunsmod.team.red"), x + 168, y + 37, 0xFFFFFFFF, true);
        String gc = String.valueOf(menu.get(0) + (menu.get(2) == ArenaBlockEntity.MODE_PLAYER ? 1 : 0));
        String rc = String.valueOf(menu.get(1));
        g.pose().pushPose();
        g.pose().translate(x + 65 - font.width(gc), y + 66, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, gc, 0, 0, 0xFF7FD87F, true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(x + 215 - font.width(rc), y + 66, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, rc, 0, 0, 0xFFFF7A6A, true);
        g.pose().popPose();
        Component vs = Component.literal("VS");
        g.pose().pushPose();
        g.pose().translate(x + 140 - font.width(vs), y + 50, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, vs, 0, 0, UiKit.ACCENT, true);
        g.pose().popPose();
        if (menu.get(2) == ArenaBlockEntity.MODE_PLAYER) {
            g.drawString(font, Component.translatable("gunsmod.arena.you_plus"), x + 18, y + 49, UiKit.MUTED, false);
        }
        for (UiKit.Btn b : buttons()) UiKit.button(g, font, b, mx, my);
        // status
        UiKit.dark(g, x + 12, y + 120, imageWidth - 24, 26);
        int state = menu.get(4);
        Component st = switch (state) {
            case ArenaBlockEntity.RUNNING -> Component.translatable("gunsmod.arena.status.running", menu.get(9), menu.get(7), menu.get(8));
            case ArenaBlockEntity.ENDED -> Component.translatable("gunsmod.arena.status.ended");
            default -> Component.translatable("gunsmod.arena.status.idle");
        };
        g.drawString(font, st, x + 18, y + 124, state == ArenaBlockEntity.RUNNING ? UiKit.GOOD : 0xFFDDDDDD, false);
        g.drawString(font, Component.translatable("gunsmod.arena.score", menu.get(5), menu.get(6)), x + 18, y + 135, UiKit.ACCENT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        UiKit.title(g, font, this.title, 8, 6);
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
                if (b.id() == 6) this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
