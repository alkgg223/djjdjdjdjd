package com.gunsmod.client.screen;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.game.GameMode;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The mission control table: pick the battle mode, the attacking side, the clock, the score / tickets and how far the
 * battle reaches, see what objectives are set up round the table, and start or stop the battle. Big buttons for
 * touch screens; the layout folds into one column on narrow screens.
 */
public class MatchScreen extends Screen {
    private static final int B_START = 100, B_STOP = 101, B_SIDE = 102, B_MIN_DN = 103, B_MIN_UP = 104, B_LIM_DN = 105, B_LIM_UP = 106, B_RAD_DN = 107,
            B_RAD_UP = 108, B_CLOSE = 109;
    private Net.MatchState s;
    private final BlockPos table;
    private int mode, attackers, minutes, limit, radius;
    private final List<UiKit.Btn> buttons = new ArrayList<>();
    private int left, top, w, h;

    public MatchScreen(Net.MatchState s) {
        super(Component.translatable("block.gunsmod.mission_control"));
        this.table = s.table();
        load(s);
    }

    private void load(Net.MatchState st) {
        this.s = st;
        mode = Mth.clamp(st.mode(), 0, GameMode.values().length - 1);
        attackers = st.attackers();
        minutes = Math.max(1, st.minutes());
        limit = Math.max(1, st.limit());
        radius = Math.max(64, st.radius());
    }

    public void update(Net.MatchState st) {
        if (!st.table().equals(BlockPos.ZERO) && !st.table().equals(table)) return;
        boolean wasRunning = s.running();
        this.s = st;
        if (st.open() || wasRunning != st.running()) load(st);
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        buttons.clear();
        w = Math.min(this.width - 8, 380);
        h = Math.min(this.height - 8, 250);
        left = (this.width - w) / 2;
        top = (this.height - h) / 2;
        boolean run = s.running();
        int colW = (w - 24) / 2, bh = 18;
        GameMode[] all = GameMode.values();
        int my = top + 24;
        for (int i = 0; i < all.length; i++) {
            int c = i % 2, r = i / 2;
            Component label = Component.translatable("gunsmod.mode." + all[i].id());
            buttons.add(new UiKit.Btn(i, left + 8 + c * (colW + 8), my + r * (bh + 3), colW, bh, label, !run, false));
        }
        int sy = my + 5 * (bh + 3) + 6;
        int third = (w - 16 - 8) / 3;
        GameMode m = all[mode];
        Component side = Component.translatable(m.convoy() ? "gunsmod.matchui.escort" : "gunsmod.matchui.attackers",
                Component.translatable("gunsmod.team." + (m.convoy() ? BotTeam.byId(attackers).enemy() : BotTeam.byId(attackers)).name().toLowerCase()));
        buttons.add(new UiKit.Btn(B_SIDE, left + 8, sy, third, bh, side, !run && m.attackDefend(), false));
        buttons.add(new UiKit.Btn(B_MIN_DN, left + 8 + third + 4, sy, 18, bh, Component.literal("-"), !run, false));
        buttons.add(new UiKit.Btn(B_MIN_UP, left + 8 + 2 * third - 14, sy, 18, bh, Component.literal("+"), !run, false));
        boolean lim = m.limit() > 0;
        buttons.add(new UiKit.Btn(B_LIM_DN, left + 8 + 2 * third + 8, sy, 18, bh, Component.literal("-"), !run && lim, false));
        buttons.add(new UiKit.Btn(B_LIM_UP, left + w - 8 - 18, sy, 18, bh, Component.literal("+"), !run && lim, false));
        int ry = sy + bh + 4;
        buttons.add(new UiKit.Btn(B_RAD_DN, left + 8 + third + 4, ry, 18, bh, Component.literal("-"), !run, false));
        buttons.add(new UiKit.Btn(B_RAD_UP, left + 8 + 2 * third - 14, ry, 18, bh, Component.literal("+"), !run, false));
        int by = top + h - 26;
        buttons.add(new UiKit.Btn(B_START, left + 8, by, colW, 20, Component.translatable("gunsmod.matchui.start"), !run, false));
        buttons.add(new UiKit.Btn(B_STOP, left + 16 + colW, by, colW - 50, 20, Component.translatable("gunsmod.matchui.stop"), run, true));
        buttons.add(new UiKit.Btn(B_CLOSE, left + w - 8 - 42, by, 42, 20, Component.translatable("gunsmod.radar.close"), true, false));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        UiKit.panel(g, left, top, w, h);
        UiKit.title(g, this.font, this.title, left + 8, top + 8);
        GameMode m = GameMode.values()[mode];
        Component st = s.running() ? Component.translatable("gunsmod.matchui.running", Component.translatable("gunsmod.mode." + GameMode.values()[Mth.clamp(s.mode(), 0, 9)].id()),
                s.timeLeft() / 1200, (s.timeLeft() / 20) % 60) : Component.translatable("gunsmod.matchui.idle");
        g.drawString(this.font, st, left + w - 8 - this.font.width(st), top + 9, s.running() ? UiKit.GOOD : UiKit.MUTED, false);
        for (UiKit.Btn b : buttons) {
            UiKit.button(g, this.font, b, mx, my);
            if (b.id() == mode && b.id() < 100) g.fill(b.x(), b.y() + b.h() - 2, b.x() + b.w(), b.y() + b.h(), 0xFFE0B040);
        }
        // the numbers beside their buttons
        UiKit.Btn mn = find(B_MIN_DN), mu = find(B_MIN_UP), ld = find(B_LIM_DN), rd = find(B_RAD_DN), ru = find(B_RAD_UP);
        String mins = Component.translatable("gunsmod.matchui.minutes", minutes).getString();
        g.drawString(this.font, mins, (mn.x() + mn.w() + mu.x()) / 2 - this.font.width(mins) / 2, mn.y() + 5, UiKit.TEXT, false);
        if (m.limit() > 0) {
            String key = m.tickets() ? "gunsmod.matchui.tickets" : "gunsmod.matchui.limit";
            String l = Component.translatable(key, limit).getString();
            UiKit.Btn lu = find(B_LIM_UP);
            g.drawString(this.font, l, (ld.x() + ld.w() + lu.x()) / 2 - this.font.width(l) / 2, ld.y() + 5, UiKit.TEXT, false);
        }
        String rad = Component.translatable("gunsmod.matchui.radius", radius).getString();
        g.drawString(this.font, rad, (rd.x() + rd.w() + ru.x()) / 2 - this.font.width(rad) / 2, rd.y() + 5, UiKit.TEXT, false);
        // what is set up round the table, and what this mode needs
        int[] n = new int[6];
        for (Net.ObjInfo o : s.objectives()) if (o.kind() >= 0 && o.kind() < 6 && (o.flags() & 6) == 0) n[o.kind()]++;
        int iy = rd.y() + 24;
        g.drawString(this.font, Component.translatable("gunsmod.matchui.found", n[0], n[1], n[2], n[3]), left + 8, iy, UiKit.MUTED, false);
        List<net.minecraft.util.FormattedCharSequence> desc = this.font.split(Component.translatable("gunsmod.mode." + m.id() + ".desc"), w - 16);
        for (int i = 0; i < Math.min(3, desc.size()); i++) g.drawString(this.font, desc.get(i), left + 8, iy + 12 + i * 10, UiKit.TEXT, false);
    }

    private UiKit.Btn find(int id) {
        for (UiKit.Btn b : buttons) if (b.id() == id) return b;
        return buttons.get(0);
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new Net.MatchControl(table, action, mode, attackers, minutes, limit, radius));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (UiKit.Btn b : buttons) {
            if (!b.hit(mx, my)) continue;
            GameMode m = GameMode.values()[mode];
            switch (b.id()) {
                case B_START -> send(Net.MatchControl.START);
                case B_STOP -> send(Net.MatchControl.STOP);
                case B_CLOSE -> {
                    send(Net.MatchControl.SAVE);
                    onClose();
                    return true;
                }
                case B_SIDE -> attackers = 1 - attackers;
                case B_MIN_DN -> minutes = Math.max(1, minutes - (minutes > 10 ? 5 : 1));
                case B_MIN_UP -> minutes = Math.min(120, minutes + (minutes >= 10 ? 5 : 1));
                case B_LIM_DN -> limit = Math.max(1, limit - step(m));
                case B_LIM_UP -> limit = Math.min(2000, limit + step(m));
                case B_RAD_DN -> radius = Math.max(64, radius - 64);
                case B_RAD_UP -> radius = Math.min(1024, radius + 64);
                default -> {
                    if (b.id() >= 0 && b.id() < GameMode.values().length && b.id() != mode) {
                        mode = b.id();
                        minutes = GameMode.values()[mode].minutes();
                        limit = Math.max(1, GameMode.values()[mode].limit());
                    }
                }
            }
            rebuild();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static int step(GameMode m) {
        return m == GameMode.CTF ? 1 : m == GameMode.TDM ? 5 : 25;
    }
}
