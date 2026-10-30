package com.gunsmod.client.screen;

import com.gunsmod.client.BattleHud;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pick a side on the orchard battlefield: two big team cards, leave the battle, close. */
public class TeamScreen extends Screen {
    private final int w = 300, h = 178;
    private int left, top;
    private UiKit.Btn leave, close;

    public TeamScreen() {
        super(Component.translatable("gunsmod.team.pick"));
    }

    @Override
    protected void init() {
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        leave = new UiKit.Btn(0, left + 12, top + h - 26, 130, 18, Component.translatable("gunsmod.team.leave"), BattleHud.team() >= 0, true);
        close = new UiKit.Btn(1, left + w - 142, top + h - 26, 130, 18, Component.translatable("gunsmod.team.close"), true, false);
    }

    private int cardX(int t) {
        return left + 12 + t * 144;
    }

    private boolean onCard(int t, double mx, double my) {
        int cx = cardX(t), cy = top + 30;
        return mx >= cx && my >= cy && mx < cx + 132 && my < cy + 104;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        UiKit.panel(g, left, top, w, h);
        UiKit.title(g, this.font, this.title, left + 12, top + 10);
        for (int t = 0; t < 2; t++) card(g, t, mx, my);
        Component hint = Component.translatable("gunsmod.team.hint");
        g.drawString(this.font, hint, left + (w - this.font.width(hint)) / 2, top + 140, UiKit.MUTED, false);
        UiKit.button(g, this.font, leave, mx, my);
        UiKit.button(g, this.font, close, mx, my);
    }

    private void card(GuiGraphics g, int t, int mx, int my) {
        BotTeam team = BotTeam.byId(t);
        int x = cardX(t), y = top + 30, cw = 132, ch = 104;
        boolean hover = onCard(t, mx, my), mine = BattleHud.team() == t;
        int c = team.color;
        g.fillGradient(x, y, x + cw, y + ch, (hover ? 0xE0000000 : 0xB0000000) | c, 0xD0101418);
        g.fill(x, y, x + cw, y + 2, 0xFF000000 | c);
        if (hover || mine) {
            int b = mine ? 0xFFE0B040 : 0xFFFFFFFF;
            g.fill(x, y, x + cw, y + 1, b);
            g.fill(x, y + ch - 1, x + cw, y + ch, b);
            g.fill(x, y, x + 1, y + ch, b);
            g.fill(x + cw - 1, y, x + cw, y + ch, b);
        }
        Component name = Component.translatable("gunsmod.team." + team.name().toLowerCase());
        g.pose().pushPose();
        g.pose().translate(x + cw / 2f, y + 22, 0);
        g.pose().scale(2.2f, 2.2f, 1f);
        g.drawString(this.font, name, -this.font.width(name) / 2, -4, 0xFFFFFFFF, true);
        g.pose().popPose();
        Component kills = Component.translatable("gunsmod.team.kills", BattleHud.score(t));
        g.drawString(this.font, kills, x + (cw - this.font.width(kills)) / 2, y + 48, UiKit.TEXT, true);
        Component act = mine ? Component.translatable("gunsmod.team.yours") : Component.translatable("gunsmod.team.join");
        int bw = 80, bx = x + (cw - bw) / 2, by = y + ch - 26;
        g.fill(bx, by, bx + bw, by + 16, hover ? 0xFF000000 | c : 0x90000000 | c);
        g.fill(bx, by, bx + bw, by + 1, 0x60FFFFFF);
        g.drawString(this.font, act, bx + (bw - this.font.width(act)) / 2, by + 4, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int t = 0; t < 2; t++) {
            if (onCard(t, mx, my)) {
                PacketDistributor.sendToServer(new Net.TeamPick(t));
                this.onClose();
                return true;
            }
        }
        if (leave.hit(mx, my)) {
            PacketDistributor.sendToServer(new Net.TeamPick(-1));
            this.onClose();
            return true;
        }
        if (close.hit(mx, my)) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
