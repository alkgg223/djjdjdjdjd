package com.gunsmod.client.screen;

import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The radar operator's scope (PPI): north up, range rings, the turning sweep (or the fixed sector of a phased array),
 * and every blip with its IFF answer: friends green circles, hostiles red diamonds, unknowns yellow squares, our
 * launchers blue. A short line shows where each blip is heading. The list on the side gives bearing, range, height and
 * speed. The picture is refreshed from the server twice a second while the scope is open.
 */
public class RadarScreen extends Screen {
    private static final int FRIEND = 0xFF5CE07A, HOSTILE = 0xFFFF5040, UNKNOWN = 0xFFFFD040, SITE = 0xFF60A8FF;
    private static final int SCOPE = 0xFF2BD96A, SCOPE_DIM = 0x602BD96A, SCOPE_FAINT = 0x302BD96A;

    private static Net.RadarPicture last;
    private static long closedAt = -100;

    private final BlockPos pos;
    private int refresh;
    private int cx, cy, r;
    private int listX, listY, listW;
    private UiKit.Btn close;
    private int hovered = -1;

    public RadarScreen(BlockPos pos) {
        super(Component.translatable("gunsmod.radar.title"));
        this.pos = pos;
    }

    /** a picture from the server: refresh the open scope, or open it (the operator just used the radar) */
    public static void picture(Net.RadarPicture p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RadarScreen s) {
            if (s.pos.equals(p.pos())) last = p;
            return;
        }
        if (mc.screen != null || mc.player == null || mc.player.tickCount - closedAt < 20) return;
        last = p;
        mc.setScreen(new RadarScreen(p.pos()));
    }

    @Override
    protected void init() {
        int side = Math.min(170, Math.max(110, this.width / 3));
        boolean narrow = this.width < 360;
        r = Math.max(40, Math.min((this.height - 40) / 2, (narrow ? this.width - 20 : this.width - side - 40) / 2));
        cx = narrow ? this.width / 2 : 14 + r + (this.width - side - 28 - 2 * r) / 2;
        cy = 22 + r;
        listX = narrow ? 10 : this.width - side - 10;
        listY = narrow ? cy + r + 8 : 22;
        listW = narrow ? this.width - 20 : side;
        close = new UiKit.Btn(0, this.width - 62, 4, 58, 16, Component.translatable("gunsmod.radar.close"), true, true);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.blockPosition().distSqr(pos) > 16 * 16) {
            onClose();
            return;
        }
        if (--refresh <= 0) {
            refresh = 10;
            PacketDistributor.sendToServer(new Net.RadarRequest(pos));
        }
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) closedAt = mc.player.tickCount;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void dot(GuiGraphics g, double x, double y, int col) {
        int ix = (int) Math.round(x), iy = (int) Math.round(y);
        g.fill(ix, iy, ix + 1, iy + 1, col);
    }

    private static void line(GuiGraphics g, double x0, double y0, double x1, double y1, int col) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        int n = Math.max(1, (int) Math.ceil(len));
        for (int i = 0; i <= n; i++) dot(g, x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, col);
    }

    private static void ring(GuiGraphics g, double x, double y, double rad, int col) {
        int n = Math.max(12, (int) (rad * 2.2));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            dot(g, x + Math.cos(a) * rad, y + Math.sin(a) * rad, col);
        }
    }

    /** a compass bearing (0 north, 90 east) to a screen direction */
    private static double sx(double bearing) {
        return Math.sin(Math.toRadians(bearing));
    }

    private static double sy(double bearing) {
        return -Math.cos(Math.toRadians(bearing));
    }

    /** a Minecraft yaw (0 south, 90 west) to a compass bearing */
    private static double bearingOfYaw(double yaw) {
        return Mth.wrapDegrees(yaw + 180);
    }

    private static int iffColour(Net.Blip b) {
        if (b.kind() == 4) return SITE;
        return b.iff() == 0 ? FRIEND : b.iff() == 1 ? HOSTILE : UNKNOWN;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        UiKit.dark(g, 0, 0, this.width, this.height);
        UiKit.title(g, this.font, this.title, 8, 6);
        UiKit.button(g, this.font, close, mx, my);
        Net.RadarPicture p = last;
        if (p == null || !p.pos().equals(pos)) {
            Component wait = Component.translatable("gunsmod.radar.wait");
            g.drawString(this.font, wait, (this.width - this.font.width(wait)) / 2, this.height / 2, UiKit.MUTED, false);
            return;
        }
        // scope face
        g.fill(cx - r, cy - r, cx + r + 1, cy + r + 1, 0x00000000);
        for (int i = -r; i <= r; i += 2) {
            int half = (int) Math.sqrt(Math.max(0, r * r - i * i));
            g.fill(cx - half, cy + i, cx + half, cy + i + 2, 0xE0041208);
        }
        for (int k = 1; k <= 4; k++) ring(g, cx, cy, r * k / 4.0, k == 4 ? SCOPE : SCOPE_DIM);
        for (int b = 0; b < 360; b += 30) line(g, cx + sx(b) * 4, cy + sy(b) * 4, cx + sx(b) * r, cy + sy(b) * r, SCOPE_FAINT);
        for (int b = 0; b < 360; b += 10) {
            double in = b % 30 == 0 ? r - 6 : r - 3;
            line(g, cx + sx(b) * in, cy + sy(b) * in, cx + sx(b) * r, cy + sy(b) * r, SCOPE_DIM);
        }
        String[] cardinal = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            int b = i * 90;
            int tx = (int) (cx + sx(b) * (r + 7)) - this.font.width(cardinal[i]) / 2, ty = (int) (cy + sy(b) * (r + 7)) - 4;
            g.drawString(this.font, cardinal[i], tx, ty, SCOPE, false);
        }
        for (int k = 1; k <= 4; k++) {
            String s = Math.round(p.range() * k / 4f) + "";
            g.drawString(this.font, s, cx + 2, (int) (cy - r * k / 4.0) + 1, SCOPE_DIM, false);
        }
        // sweep, or the sector of a fixed array
        long t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        double face = bearingOfYaw(p.facing());
        if (p.sector()) {
            for (int s = -1; s <= 1; s += 2) {
                double b = face + s * 55;
                line(g, cx, cy, cx + sx(b) * r, cy + sy(b) * r, SCOPE);
            }
            double scan = face + Math.sin((t + pt) * 0.03) * 55;
            for (int i = 0; i < 6; i++) {
                double b = scan - i * 1.6;
                line(g, cx, cy, cx + sx(b) * r, cy + sy(b) * r, (0xA0 - i * 0x18) << 24 | 0x2BD96A);
            }
        } else {
            double turn = ((t + pt) * 360.0 / Math.max(4, p.sweep())) % 360;
            for (int i = 0; i < 10; i++) {
                double b = turn - i * 2.4;
                line(g, cx, cy, cx + sx(b) * r, cy + sy(b) * r, (0xC0 - i * 0x12) << 24 | 0x2BD96A);
            }
        }
        // the operator, at the centre
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        // blips
        double scale = r / Math.max(1, p.range());
        List<Net.Blip> blips = p.blips();
        hovered = -1;
        boolean blink = (t / 6) % 2 == 0;
        int friends = 0, hostiles = 0, unknown = 0;
        for (int i = 0; i < blips.size(); i++) {
            Net.Blip b = blips.get(i);
            double x = cx + b.dx() * scale, y = cy + b.dz() * scale;
            if (Math.hypot(x - cx, y - cy) > r + 1) continue;
            int col = iffColour(b);
            if ((b.flags() & 1) != 0) col = (col & 0x00FFFFFF) | 0xB0000000;
            int ix = (int) Math.round(x), iy = (int) Math.round(y);
            if (b.kind() == 4) {
                g.fill(ix - 2, iy - 2, ix + 2, iy + 2, col);
                if ((b.flags() & 2) != 0 && blink) ring(g, x, y, 4, col);
                continue;
            }
            if (b.iff() == 0) friends++;
            else if (b.iff() == 1) hostiles++;
            else unknown++;
            if (b.iff() == 0) {
                ring(g, x, y, 3, col);
            } else if (b.iff() == 1) {
                for (int k = 0; k <= 3; k++) {
                    g.fill(ix - 3 + k, iy - k, ix + 4 - k, iy - k + 1, col);
                    g.fill(ix - 3 + k, iy + k, ix + 4 - k, iy + k + 1, col);
                }
            } else {
                g.fill(ix - 3, iy - 3, ix + 3, iy - 2, col);
                g.fill(ix - 3, iy + 2, ix + 3, iy + 3, col);
                g.fill(ix - 3, iy - 3, ix - 2, iy + 3, col);
                g.fill(ix + 2, iy - 3, ix + 3, iy + 3, col);
            }
            if (b.kind() == 1) g.fill(ix - 1, iy - 1, ix + 1, iy + 1, col);
            if (b.speed() > 0.5f) {
                double hb = bearingOfYaw(b.heading()), len = 4 + Math.min(18, b.speed() * 0.35);
                line(g, x + sx(hb) * 4, y + sy(hb) * 4, x + sx(hb) * len, y + sy(hb) * len, col);
            }
            if ((b.flags() & 2) != 0 && blink) {
                g.fill(ix - 6, iy - 6, ix + 6, iy - 5, HOSTILE);
                g.fill(ix - 6, iy + 5, ix + 6, iy + 6, HOSTILE);
                g.fill(ix - 6, iy - 6, ix - 5, iy + 6, HOSTILE);
                g.fill(ix + 5, iy - 6, ix + 6, iy + 6, HOSTILE);
            }
            String alt = "" + Math.round(b.y());
            g.drawString(this.font, alt, ix + 5, iy - 3, (col & 0x00FFFFFF) | 0xC0000000, false);
            if (Math.abs(mx - x) < 6 && Math.abs(my - y) < 6) hovered = i;
        }
        // side list
        int y = listY;
        g.drawString(this.font, Component.translatable("gunsmod.radar.count", friends, hostiles, unknown), listX, y, UiKit.TEXT, false);
        y += 12;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < blips.size(); i++) if (blips.get(i).kind() != 4) order.add(i);
        order.sort((a, b) -> Double.compare(dist(blips.get(a)), dist(blips.get(b))));
        int rows = Math.max(1, (this.height - y - 40) / 10);
        if (order.isEmpty()) {
            g.drawString(this.font, Component.translatable("gunsmod.radar.none"), listX, y, UiKit.MUTED, false);
        }
        for (int k = 0; k < Math.min(rows, order.size()); k++) {
            int i = order.get(k);
            Net.Blip b = blips.get(i);
            int brg = (int) Math.round(Mth.positiveModulo(Math.toDegrees(Math.atan2(b.dx(), -b.dz())), 360));
            String row = String.format("%03d° %4dm %4dh %3d", brg, Math.round(dist(b)), Math.round(b.y()), Math.round(b.speed()));
            Component kind = Component.translatable("gunsmod.radar.kind." + b.kind());
            int col = iffColour(b);
            if (i == hovered) g.fill(listX - 2, y - 1, listX + listW, y + 9, 0x40FFFFFF);
            g.drawString(this.font, Component.literal(row + " ").append(kind), listX, y, col, false);
            y += 10;
        }
        // legend
        int ly = this.height - 28;
        g.drawString(this.font, Component.translatable("gunsmod.radar.legend"), 8, ly, UiKit.MUTED, false);
        g.drawString(this.font, Component.translatable("gunsmod.radar.legend2"), 8, ly + 11, UiKit.MUTED, false);
        if (hovered >= 0) {
            Net.Blip b = blips.get(hovered);
            Component tip = Component.translatable("gunsmod.radar.iff." + b.iff()).append(" ").append(Component.translatable("gunsmod.radar.kind." + b.kind()))
                    .append((b.flags() & 1) != 0 ? Component.translatable("gunsmod.radar.link") : Component.empty())
                    .append((b.flags() & 2) != 0 ? Component.translatable("gunsmod.radar.engaged") : Component.empty());
            g.renderTooltip(this.font, tip, mx, my);
        }
    }

    private static double dist(Net.Blip b) {
        return Math.sqrt(b.dx() * b.dx() + b.dz() * b.dz());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (close.hit(mx, my)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
