package com.gunsmod.client.screen;

import com.gunsmod.client.SquadHud;
import com.gunsmod.entity.ai.Squad;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The squad command board: big touch-friendly buttons for every order and a row of formations. Orders that need a
 * place or a target (attack, defend, patrol, protect, focus) use what the player is looking at.
 */
public class SquadScreen extends Screen {
    private static final int[] CMDS = {Squad.C_FOLLOW, Squad.C_HOLD, Squad.C_ATTACK, Squad.C_DEFEND, Squad.C_PATROL, Squad.C_RETREAT,
            Squad.C_ENTER, Squad.C_EXIT, Squad.C_PROTECT, Squad.C_FOCUS, Squad.C_HOLD_FIRE, Squad.C_DISMISS};
    private static final String[] NAMES = {"follow", "hold", "attack", "defend", "patrol", "retreat", "enter", "exit", "protect", "focus", "holdfire", "dismiss"};
    private static final String[] ICONS = {"➔", "■", "✖", "⛨", "⇄", "↩", "▲", "▼", "✦", "◎", "✕", "✗"};
    private final List<UiKit.Btn> buttons = new ArrayList<>();
    private final List<UiKit.Btn> forms = new ArrayList<>();
    private int left, top, w, h;

    public SquadScreen() {
        super(Component.translatable("gunsmod.squad.title"));
    }

    @Override
    protected void init() {
        buttons.clear();
        forms.clear();
        int cols = 4, bw = Math.min(104, (this.width - 40) / cols - 6), bh = 30;
        w = cols * (bw + 6) + 18;
        int fbw = (w - 18 - 5 * 4) / 6;
        h = 30 + 3 * (bh + 6) + 18 + 24 + 30;
        left = (this.width - w) / 2;
        top = Math.max(4, (this.height - h) / 2);
        for (int i = 0; i < CMDS.length; i++) {
            int c = i % cols, r = i / cols;
            boolean red = CMDS[i] == Squad.C_RETREAT || CMDS[i] == Squad.C_DISMISS;
            Component label = Component.literal(ICONS[i] + " ").append(Component.translatable("gunsmod.squad.cmd." + NAMES[i]));
            buttons.add(new UiKit.Btn(CMDS[i], left + 9 + c * (bw + 6), top + 28 + r * (bh + 6), bw, bh, label, true, red));
        }
        int fy = top + 28 + 3 * (bh + 6) + 18;
        for (int i = 0; i < Squad.Formation.values().length; i++) {
            Component label = Component.translatable("gunsmod.squad.formation." + Squad.Formation.values()[i].name().toLowerCase());
            forms.add(new UiKit.Btn(i, left + 9 + i * (fbw + 4), fy, fbw, 22, label, true, false));
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        UiKit.panel(g, left, top, w, h);
        UiKit.title(g, this.font, this.title, left + 10, top + 9);
        Component state = SquadHud.count > 0 ? Component.translatable("gunsmod.squad.size", SquadHud.count) : Component.translatable("gunsmod.squad.empty");
        g.drawString(this.font, state, left + w - 10 - this.font.width(state), top + 10, UiKit.MUTED, false);
        for (UiKit.Btn b : buttons) UiKit.button(g, this.font, b, mx, my);
        int fy = forms.get(0).y();
        g.drawString(this.font, Component.translatable("gunsmod.squad.formations"), left + 10, fy - 11, UiKit.ACCENT, false);
        for (UiKit.Btn b : forms) {
            UiKit.button(g, this.font, b, mx, my);
            if (b.id() == SquadHud.formation && SquadHud.count > 0) g.fill(b.x(), b.y() + b.h() - 2, b.x() + b.w(), b.y() + b.h(), 0xFFE0B040);
        }
        Component hint = Component.translatable("gunsmod.squad.hint");
        g.drawString(this.font, hint, left + (w - this.font.width(hint)) / 2, top + h - 14, UiKit.MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (UiKit.Btn b : buttons) {
            if (b.hit(mx, my)) {
                PacketDistributor.sendToServer(new Net.SquadOrder(b.id(), SquadHud.formation));
                // orders aimed at a place or a target are given looking at it: close the board
                if (b.id() != Squad.C_HOLD_FIRE) this.onClose();
                return true;
            }
        }
        for (UiKit.Btn b : forms) {
            if (b.hit(mx, my)) {
                PacketDistributor.sendToServer(new Net.SquadOrder(Squad.C_FORMATION, b.id()));
                SquadHud.formation = b.id();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
