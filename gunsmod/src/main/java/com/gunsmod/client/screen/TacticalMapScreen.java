package com.gunsmod.client.screen;

import com.gunsmod.client.MatchHud;
import com.gunsmod.client.SquadHud;
import com.gunsmod.entity.ai.Squad;
import com.gunsmod.game.MapIntel;
import com.gunsmod.net.Net;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The tactical map: the ground round the player seen from above (drawn from the loaded world, hills shaded), his
 * side's soldiers, squad, players and vehicles, the enemies the side knows of, its radars and launchers, the battle's
 * objectives. Pick an order on the right and tap the map to send the squad there. Zoom with + / -.
 */
public class TacticalMapScreen extends Screen {
    private static final int CELLS = 96;
    private static final int[] RADII = {64, 128, 256};
    private static Net.MapIntel intel;
    private static int zoom = 1;

    private final int[] colours = new int[CELLS * CELLS];
    private int row, refresh;
    private double cx, cz;
    private int mapX, mapY, mapS;
    private final List<UiKit.Btn> buttons = new ArrayList<>();
    private int order = Squad.C_ATTACK;
    private double pingX = Double.NaN, pingZ;
    private int pingTime;

    private static final int B_ZOOM_IN = 200, B_ZOOM_OUT = 201, B_CENTER = 202, B_CLOSE = 203;
    private static final int[] ORDERS = {Squad.C_ATTACK, Squad.C_DEFEND, Squad.C_PATROL, Squad.C_FOLLOW, Squad.C_HOLD, Squad.C_RETREAT};
    private static final String[] ORDER_NAMES = {"attack", "defend", "patrol", "follow", "hold", "retreat"};

    public TacticalMapScreen() {
        super(Component.translatable("gunsmod.map.title"));
    }

    public static void intel(Net.MapIntel p) {
        intel = p;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int radius() {
        return RADII[Mth.clamp(zoom, 0, RADII.length - 1)];
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && Double.isNaN(pingX) && cx == 0 && cz == 0) {
            cx = mc.player.getX();
            cz = mc.player.getZ();
        }
        boolean narrow = this.width < this.height * 1.25;
        int side = narrow ? 0 : Math.min(110, this.width / 4);
        mapS = Math.max(96, Math.min(this.height - 24, (narrow ? this.width : this.width - side - 24) - 16));
        mapS = mapS / CELLS * CELLS;
        if (mapS < CELLS) mapS = CELLS;
        mapX = narrow ? (this.width - mapS) / 2 : 10;
        mapY = 18;
        buttons.clear();
        int bx = narrow ? 8 : mapX + mapS + 10, by = narrow ? mapY + mapS + 6 : mapY;
        int bw = narrow ? (this.width - 16 - 5 * 4) / 6 : side, bh = narrow ? 18 : 20;
        for (int i = 0; i < ORDERS.length; i++) {
            Component l = Component.translatable("gunsmod.squad.cmd." + ORDER_NAMES[i]);
            if (narrow) buttons.add(new UiKit.Btn(ORDERS[i], bx + i * (bw + 4), by, bw, bh, l, true, ORDERS[i] == Squad.C_RETREAT));
            else buttons.add(new UiKit.Btn(ORDERS[i], bx, by + i * (bh + 4), bw, bh, l, true, ORDERS[i] == Squad.C_RETREAT));
        }
        int zy = narrow ? by + bh + 4 : by + ORDERS.length * (bh + 4) + 8;
        int zw = narrow ? (this.width - 16 - 12) / 4 : (side - 8) / 3;
        buttons.add(new UiKit.Btn(B_ZOOM_IN, bx, zy, zw, bh, Component.literal("+"), zoom > 0, false));
        buttons.add(new UiKit.Btn(B_ZOOM_OUT, bx + zw + 4, zy, zw, bh, Component.literal("-"), zoom < RADII.length - 1, false));
        buttons.add(new UiKit.Btn(B_CENTER, bx + 2 * (zw + 4), zy, zw, bh, Component.literal("◎"), true, false));
        if (narrow) buttons.add(new UiKit.Btn(B_CLOSE, bx + 3 * (zw + 4), zy, zw, bh, Component.translatable("gunsmod.radar.close"), true, true));
        else buttons.add(new UiKit.Btn(B_CLOSE, bx, zy + bh + 6, side, bh, Component.translatable("gunsmod.radar.close"), true, true));
        row = 0;
        refresh = 0;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (--refresh <= 0) {
            refresh = 20;
            PacketDistributor.sendToServer(new Net.MapRequest(radius() + 32));
        }
        if (pingTime > 0) pingTime--;
        // the ground, a few rows at a time
        for (int k = 0; k < 12; k++) {
            paintRow(mc.level, row);
            row = (row + 1) % CELLS;
        }
    }

    private void paintRow(ClientLevel level, int j) {
        double step = radius() * 2.0 / CELLS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int prevH = Integer.MIN_VALUE;
        for (int i = 0; i < CELLS; i++) {
            int x = Mth.floor(cx + (i - CELLS / 2 + 0.5) * step), z = Mth.floor(cz + (j - CELLS / 2 + 0.5) * step);
            p.set(x, 0, z);
            if (!level.hasChunkAt(p)) {
                colours[j * CELLS + i] = 0xFF1A1E1A;
                continue;
            }
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            p.setY(h);
            BlockState st = level.getBlockState(p);
            MapColor mcol = st.getMapColor(level, p);
            int c = mcol == null || mcol.col == 0 ? 0x303030 : mcol.col;
            // shade the slopes facing north-west lighter, the others darker
            int hz = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - Math.max(1, (int) step)) - 1;
            float shade = Mth.clamp(1f + (h - hz) * 0.06f, 0.72f, 1.18f);
            if (prevH != Integer.MIN_VALUE) shade *= Mth.clamp(1f + (h - prevH) * 0.03f, 0.9f, 1.08f);
            prevH = h;
            int r = Mth.clamp((int) (((c >> 16) & 0xFF) * shade * 0.8f), 0, 255), g = Mth.clamp((int) (((c >> 8) & 0xFF) * shade * 0.8f), 0, 255),
                    b = Mth.clamp((int) ((c & 0xFF) * shade * 0.8f), 0, 255);
            colours[j * CELLS + i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    private int sx(double x) {
        return mapX + (int) Math.round((x - cx) / (radius() * 2.0) * mapS + mapS / 2.0);
    }

    private int sy(double z) {
        return mapY + (int) Math.round((z - cz) / (radius() * 2.0) * mapS + mapS / 2.0);
    }

    private boolean inMap(int x, int y) {
        return x >= mapX && y >= mapY && x < mapX + mapS && y < mapY + mapS;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Minecraft mc = Minecraft.getInstance();
        UiKit.dark(g, 0, 0, this.width, this.height);
        UiKit.title(g, this.font, this.title, mapX, 4);
        int cell = mapS / CELLS;
        for (int j = 0; j < CELLS; j++) {
            // runs of the same colour in one quad (far fewer quads on a phone)
            int i = 0;
            while (i < CELLS) {
                int c = colours[j * CELLS + i], k = i + 1;
                while (k < CELLS && colours[j * CELLS + k] == c) k++;
                g.fill(mapX + i * cell, mapY + j * cell, mapX + k * cell, mapY + (j + 1) * cell, c);
                i = k;
            }
        }
        // grid every 32 blocks, the border
        int gridStep = radius() >= 256 ? 64 : 32;
        for (int gx = (int) Math.ceil((cx - radius()) / gridStep) * gridStep; gx < cx + radius(); gx += gridStep) {
            int x = sx(gx);
            g.fill(x, mapY, x + 1, mapY + mapS, 0x30FFFFFF);
        }
        for (int gz = (int) Math.ceil((cz - radius()) / gridStep) * gridStep; gz < cz + radius(); gz += gridStep) {
            int y = sy(gz);
            g.fill(mapX, y, mapX + mapS, y + 1, 0x30FFFFFF);
        }
        g.fill(mapX - 1, mapY - 1, mapX + mapS + 1, mapY, 0xFFE0B040);
        g.fill(mapX - 1, mapY + mapS, mapX + mapS + 1, mapY + mapS + 1, 0xFFE0B040);
        g.fill(mapX - 1, mapY, mapX, mapY + mapS, 0xFFE0B040);
        g.fill(mapX + mapS, mapY, mapX + mapS + 1, mapY + mapS, 0xFFE0B040);
        g.drawString(this.font, "N", mapX + mapS / 2 - 2, mapY + 2, 0xFFFFFFFF, true);
        // the battle's objectives (flags carried, the convoy) from the HUD state
        if (MatchHud.active()) {
            for (Net.ObjInfo o : MatchHud.state.objectives()) {
                if (o.kind() != 5 && (o.flags() & 6) == 0) continue;
                int x = sx(o.x()), y = sy(o.z());
                if (!inMap(x, y)) continue;
                int col = o.kind() == 5 ? 0xFF60C0FF : o.team() == 1 ? 0xFFFF5A48 : 0xFF6EE05A;
                g.fill(x - 3, y - 3, x + 3, y + 3, 0xFF000000);
                g.fill(x - 2, y - 2, x + 2, y + 2, col);
            }
        }
        // the marks
        String hover = null;
        if (intel != null) {
            for (Net.MapMark m : intel.marks()) {
                int x = sx(m.x()), y = sy(m.z());
                if (!inMap(x, y)) continue;
                int friend = 0xFF6EE05A, enemy = 0xFFFF5040;
                switch (m.kind()) {
                    case MapIntel.SELF -> {
                        double a = Math.toRadians(mc.player == null ? m.yaw() : mc.player.getYRot());
                        double fx = -Math.sin(a), fz = Math.cos(a);
                        for (int k = 0; k < 7; k++) g.fill((int) (x + fx * k), (int) (y + fz * k), (int) (x + fx * k) + 2, (int) (y + fz * k) + 2, 0xFFFFFFFF);
                        g.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
                    }
                    case MapIntel.FRIEND_PLAYER -> g.fill(x - 2, y - 2, x + 3, y + 3, 0xFFB0FFB0);
                    case MapIntel.FRIEND_SOLDIER -> g.fill(x - 1, y - 1, x + 2, y + 2, friend);
                    case MapIntel.SQUAD -> g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF50E0FF);
                    case MapIntel.FRIEND_VEHICLE -> box(g, x, y, 3, friend);
                    case MapIntel.FRIEND_AIR -> tri(g, x, y, friend);
                    case MapIntel.ENEMY_SOLDIER -> g.fill(x - 1, y - 1, x + 2, y + 2, enemy);
                    case MapIntel.ENEMY_VEHICLE -> box(g, x, y, 3, enemy);
                    case MapIntel.ENEMY_AIR -> tri(g, x, y, enemy);
                    case MapIntel.RADAR -> label(g, x, y, "R", 0xFF60A8FF);
                    case MapIntel.LAUNCHER -> label(g, x, y, "L", 0xFF60A8FF);
                    case MapIntel.OBJECTIVE -> {
                        int kind = (int) m.yaw();
                        String l = kind == 0 ? String.valueOf((char) m.label()) : kind == 1 ? "F" : kind == 2 ? "T" : kind == 3 ? "D" : "?";
                        label(g, x, y, l, m.team() < 0 ? 0xFFE0E0E0 : m.team() == 1 ? 0xFFFF5A48 : 0xFF6EE05A);
                    }
                    default -> { }
                }
                if (Math.abs(mx - x) <= 3 && Math.abs(my - y) <= 3) hover = "gunsmod.map.mark." + m.kind();
            }
        }
        if (pingTime > 0 && !Double.isNaN(pingX)) {
            int x = sx(pingX), y = sy(pingZ);
            int r = 3 + (20 - pingTime % 20) / 3;
            g.fill(x - r, y, x + r + 1, y + 1, 0xFFE0B040);
            g.fill(x, y - r, x + 1, y + r + 1, 0xFFE0B040);
        }
        for (UiKit.Btn b : buttons) {
            UiKit.button(g, this.font, b, mx, my);
            if (b.id() == order) g.fill(b.x(), b.y() + b.h() - 2, b.x() + b.w(), b.y() + b.h(), 0xFFE0B040);
        }
        Component info = Component.translatable("gunsmod.map.info", radius() * 2, SquadHud.count);
        g.drawString(this.font, info, mapX + mapS - this.font.width(info), 4, 0xFFB0B8A8, false);
        if (inMap(mx, my)) {
            double wx = cx + (mx - mapX - mapS / 2.0) / mapS * radius() * 2, wz = cz + (my - mapY - mapS / 2.0) / mapS * radius() * 2;
            String c = Mth.floor(wx) + ", " + Mth.floor(wz);
            g.drawString(this.font, c, mapX + 3, mapY + mapS - 10, 0xFFFFFFFF, true);
        }
        if (hover != null) g.renderTooltip(this.font, Component.translatable(hover), mx, my);
    }

    private static void box(GuiGraphics g, int x, int y, int r, int col) {
        g.fill(x - r, y - r, x + r + 1, y + r + 1, 0xFF000000);
        g.fill(x - r + 1, y - r + 1, x + r, y + r, col);
    }

    private static void tri(GuiGraphics g, int x, int y, int col) {
        for (int k = 0; k < 5; k++) g.fill(x - k, y - 3 + k, x + k + 1, y - 2 + k, col);
    }

    private void label(GuiGraphics g, int x, int y, String l, int col) {
        g.fill(x - 5, y - 5, x + 6, y + 6, 0xC0000000);
        g.drawString(this.font, l, x - this.font.width(l) / 2 + 1, y - 3, col, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (UiKit.Btn b : buttons) {
            if (!b.hit(mx, my)) continue;
            switch (b.id()) {
                case B_ZOOM_IN -> {
                    zoom = Math.max(0, zoom - 1);
                    init();
                }
                case B_ZOOM_OUT -> {
                    zoom = Math.min(RADII.length - 1, zoom + 1);
                    init();
                }
                case B_CENTER -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        cx = mc.player.getX();
                        cz = mc.player.getZ();
                    }
                    init();
                }
                case B_CLOSE -> onClose();
                default -> {
                    // orders without a place go at once; the others wait for a tap on the map
                    if (b.id() == Squad.C_FOLLOW || b.id() == Squad.C_HOLD || b.id() == Squad.C_RETREAT) {
                        PacketDistributor.sendToServer(new Net.SquadOrder(b.id(), SquadHud.formation));
                    } else {
                        order = b.id();
                    }
                }
            }
            return true;
        }
        if (inMap((int) mx, (int) my)) {
            Minecraft mc = Minecraft.getInstance();
            double wx = cx + (mx - mapX - mapS / 2.0) / mapS * radius() * 2, wz = cz + (my - mapY - mapS / 2.0) / mapS * radius() * 2;
            if (button == 1) {
                // the other button pans the map to the spot
                cx = wx;
                cz = wz;
                init();
                return true;
            }
            double wy = mc.level == null ? 64 : mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(wx), Mth.floor(wz));
            PacketDistributor.sendToServer(new Net.SquadOrderAt(order, SquadHud.formation, wx, wy, wz));
            pingX = wx;
            pingZ = wz;
            pingTime = 60;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
