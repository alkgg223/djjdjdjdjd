package com.realweather.client.radar;

import com.mojang.math.Axis;
import com.realweather.client.ClientStorms;
import com.realweather.client.ClientWeather;
import com.realweather.client.tornado.ClientTornadoes;
import com.realweather.server.StormCell;
import com.realweather.server.StormField;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The radar display: a live sweep of reflectivity, velocity, ZDR or CC with range rings, storm tracks and
 * markers for mesocyclones, tornado signatures (TVS / TDS) and hail cores; beside it the conditions at the radar
 * site and a list of storms and warnings.
 */
public class RadarScreen extends Screen {
    private static final String[] PRODUCTS = {"ref", "vel", "zdr", "cc"};
    private final BlockPos radar;
    private final BlockPos opener;
    private RadarCanvas canvas;
    private int product;
    private int rangeIndex = 1;
    private int view;
    private int vx, vy;
    private final List<Button> productButtons = new ArrayList<>();

    public RadarScreen(BlockPos radar, BlockPos opener, int product) {
        super(Component.translatable("realweather.radar.title"));
        this.radar = radar;
        this.opener = opener;
        this.product = product;
    }

    @Override
    protected void init() {
        ClientLevel level = Minecraft.getInstance().level;
        if (canvas == null) {
            canvas = new RadarCanvas(256);
        }
        view = Math.max(96, Math.min(height - 64, (int) (width * 0.56F)));
        vx = 10;
        vy = 32;
        productButtons.clear();
        int bx = vx;
        for (int i = 0; i < 4; i++) {
            final int p = i;
            Button b = Button.builder(Component.translatable("realweather.radar.product." + PRODUCTS[i]), btn -> setProduct(p))
                    .bounds(bx, 8, 44, 18).build();
            productButtons.add(addRenderableWidget(b));
            bx += 46;
        }
        addRenderableWidget(Button.builder(Component.literal("-"), b -> setRange(rangeIndex - 1)).bounds(vx + view - 82, 8, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> setRange(rangeIndex + 1)).bounds(vx + view - 18, 8, 18, 18).build());
        setup(level);
    }

    /** Selects a product (also used by the automated test). */
    public void setProduct(int p) {
        product = p;
        setup(Minecraft.getInstance().level);
    }

    public void setRange(int i) {
        rangeIndex = Mth.clamp(i, 0, RadarClient.RANGES.length - 1);
        setup(Minecraft.getInstance().level);
    }

    private void setup(ClientLevel level) {
        if (level == null || canvas == null) {
            return;
        }
        canvas.product = product;
        canvas.range = RadarClient.RANGES[rangeIndex];
        canvas.signal = radar != null;
        BlockPos o = radar != null ? radar : opener;
        canvas.ox = o.getX() + 0.5;
        canvas.oz = o.getZ() + 0.5;
        canvas.redrawAll(level);
        for (int i = 0; i < productButtons.size(); i++) {
            productButtons.get(i).active = i != product;
        }
    }

    @Override
    public void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && canvas != null) {
            canvas.sweep(level, 2);
            canvas.upload();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (canvas != null) {
            canvas.close();
            canvas = null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || canvas == null) {
            return;
        }
        g.drawString(font, Component.literal((int) canvas.range + " m"), vx + view - 62, 13, 0xFFE0E8F0, false);
        g.fill(vx - 2, vy - 2, vx + view + 2, vy + view + 2, 0xFF2A3440);
        g.blit(canvas.id, vx, vy, view, view, 0F, 0F, canvas.size, canvas.size, canvas.size, canvas.size);
        if (canvas.signal) {
            drawBeam(g);
            drawMarkers(g, level);
        } else {
            g.drawCenteredString(font, Component.translatable("realweather.radar.no_signal"), vx + view / 2, vy + view / 2 - 10, 0xFFFF6060);
            g.drawCenteredString(font, Component.translatable("realweather.radar.no_signal.hint", RadarClient.LINK_RANGE), vx + view / 2,
                    vy + view / 2 + 4, 0xFFB0B8C0);
        }
        // North mark.
        g.drawCenteredString(font, "N", vx + view / 2, vy + 3, 0xFFFFFFFF);
        drawLegend(g, vx, vy + view + 6, view);
        drawInfo(g, level, vx + view + 12, vy, width - (vx + view + 12) - 8);
    }

    private void drawBeam(GuiGraphics g) {
        float cx = vx + view / 2F;
        float cy = vy + view / 2F;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotation(canvas.beamAngle() - Mth.HALF_PI));
        g.fill(0, 0, view / 2, 1, 0x9060FF90);
        g.pose().popPose();
    }

    private int sx(double x) {
        return vx + view / 2 + (int) Math.round((x - canvas.ox) / canvas.range * view / 2);
    }

    private int sy(double z) {
        return vy + view / 2 + (int) Math.round((z - canvas.oz) / canvas.range * view / 2);
    }

    private boolean inside(int x, int y) {
        float dx = x - (vx + view / 2F);
        float dy = y - (vy + view / 2F);
        return dx * dx + dy * dy < (view / 2F - 3) * (view / 2F - 3);
    }

    private void drawMarkers(GuiGraphics g, ClientLevel level) {
        for (ClientStorms.Cell c : ClientStorms.cells()) {
            if (c.cstrength() < 0.05F) {
                continue;
            }
            int x = sx(c.x);
            int y = sy(c.z);
            // Track: where the storm will be over the next 10 minutes.
            float minutes = 10F;
            int x1 = sx(c.x + c.vx * 1200 * minutes / 10F);
            int y1 = sy(c.z + c.vz * 1200 * minutes / 10F);
            dottedLine(g, x, y, x1, y1, 0xC0FFFFFF);
            dot(g, x, y, 2, 0xFFFFFFFF);
            if (c.type == StormCell.SUPERCELL) {
                double[] u = StormField.updraft(c);
                int ux = sx(u[0]);
                int uy = sy(u[1]);
                if (inside(ux, uy)) {
                    ring(g, ux, uy, Math.max(4, (int) (c.radius * 0.12F / canvas.range * view / 2)), 0xFFFFE030);
                    g.drawString(font, Component.translatable("realweather.radar.meso"), ux + 6, uy - 4, 0xFFFFE030, true);
                }
            }
            if (c.hail > 0.2F && c.type >= StormCell.HAIL) {
                double hx = c.x;
                double hz = c.z;
                if (c.type == StormCell.SUPERCELL) {
                    float sp = Math.max(1.0E-4F, Mth.sqrt(c.vx * c.vx + c.vz * c.vz));
                    hx += c.vx / sp * c.radius * 0.2F;
                    hz += c.vz / sp * c.radius * 0.2F;
                }
                int hxs = sx(hx);
                int hys = sy(hz);
                if (inside(hxs, hys)) {
                    triangle(g, hxs, hys, 4, 0xFF40FF60, true);
                }
            }
        }
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            int x = sx(t.x);
            int y = sy(t.z);
            if (!inside(x, y)) {
                continue;
            }
            boolean tds = t.contact > 0.5F && t.strength > 0.3F;
            triangle(g, x, y, 5, 0xFFFF2020, false);
            g.drawString(font, Component.literal(tds ? "TDS" : "TVS"), x + 7, y - 4, 0xFFFF4040, true);
        }
        // You.
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int px = sx(player.getX());
            int py = sy(player.getZ());
            if (inside(px, py)) {
                dot(g, px, py, 2, 0xFF60C0FF);
            }
        }
    }

    private void drawLegend(GuiGraphics g, int x, int y, int w) {
        float min;
        float max;
        String unit;
        switch (product) {
            case RadarProducts.VEL -> {
                min = -60F;
                max = 60F;
                unit = "m/s";
            }
            case RadarProducts.ZDR -> {
                min = -2F;
                max = 6F;
                unit = "dB";
            }
            case RadarProducts.CC -> {
                min = 0.2F;
                max = 1.0F;
                unit = "";
            }
            default -> {
                min = 5F;
                max = 75F;
                unit = "dBZ";
            }
        }
        RadarProducts.Sample s = new RadarProducts.Sample();
        s.echo = true;
        for (int i = 0; i < w; i++) {
            float v = min + (max - min) * i / (w - 1F);
            s.dbz = v;
            s.vel = v;
            s.zdr = v;
            s.cc = v;
            int c = v < 10F && product == RadarProducts.REF ? 0xFF101820 : RadarProducts.color(product, s);
            g.fill(x + i, y, x + i + 1, y + 8, c);
        }
        String lo = product == RadarProducts.CC ? String.format("%.2f", min) : String.valueOf((int) min);
        String hi = product == RadarProducts.CC ? String.format("%.2f", max) : String.valueOf((int) max);
        g.drawString(font, lo, x, y + 10, 0xFFC0C8D0, false);
        g.drawCenteredString(font, Component.translatable("realweather.radar.product." + PRODUCTS[product] + ".long").append(unit.isEmpty() ? "" : " (" + unit + ")"),
                x + w / 2, y + 10, 0xFFE0E8F0);
        g.drawString(font, hi, x + w - font.width(hi), y + 10, 0xFFC0C8D0, false);
    }

    private void drawInfo(GuiGraphics g, ClientLevel level, int x, int y, int w) {
        if (w < 60) {
            return;
        }
        BlockPos site = radar != null ? radar : opener;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("realweather.radar.site", site.getX(), site.getZ()).withStyle(ChatFormatting.AQUA));
        float c = Thermo.celsius(level, site);
        float rh = Thermo.humidity(level, site);
        lines.add(Component.translatable("realweather.radar.temp", String.format("%.1f", c), String.format("%.0f", c * 9F / 5F + 32F)));
        lines.add(Component.translatable("realweather.radar.dew", String.format("%.1f", Thermo.dewPoint(c, rh)), String.format("%.0f", rh)));
        float windKmh = ClientWeather.wind * 24F * 3.6F;
        lines.add(Component.translatable("realweather.radar.wind", String.format("%.0f", windKmh), compass(ClientWeather.windAngle + Mth.PI)));
        float rain = ClientStorms.synced ? ClientStorms.rainAt(site.getX(), site.getZ()) * level.getRainLevel(1F) : ClientWeather.rain * level.getRainLevel(1F);
        float hail = ClientStorms.hailAt(site.getX(), site.getZ()) * level.getRainLevel(1F);
        lines.add(Component.translatable("realweather.radar.rain", String.format("%.1f", rain * 80F)));
        if (hail > 0.05F) {
            lines.add(Component.translatable("realweather.radar.hail_here", String.format("%.1f", 0.5F + hail * 4.5F)).withStyle(ChatFormatting.GREEN));
        }
        float pressure = 1015F - rain * 9F - (ClientTornadoes.isEmpty() ? 0F : 6F);
        lines.add(Component.translatable("realweather.radar.pressure", String.format("%.0f", pressure)));
        lines.add(Component.empty());

        // Warnings first.
        List<Component> warnings = new ArrayList<>();
        for (ClientTornadoes.T t : ClientTornadoes.all()) {
            double d = Math.hypot(t.x - site.getX(), t.z - site.getZ());
            if (d > canvas.range * 1.5) {
                continue;
            }
            float peak = RadarProducts.peakWind(t);
            int est = RadarProducts.ratingFor(peak);
            warnings.add(Component.translatable("realweather.radar.tornado_warning").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            warnings.add(Component.translatable("realweather.radar.tornado_line", "EF" + est, km(d), compass(bearing(site, t.x, t.z)))
                    .withStyle(ChatFormatting.RED));
            warnings.add(Component.translatable("realweather.radar.tornado_wind", String.format("%.0f", peak * 3.6F),
                    compass(t.heading), String.format("%.0f", t.speed * 20F * 3.6F)).withStyle(ChatFormatting.GOLD));
            if (t.contact > 0.5F && t.strength > 0.3F) {
                warnings.add(Component.translatable("realweather.radar.tds").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        for (ClientStorms.Cell cell : ClientStorms.cells()) {
            double d = Math.hypot(cell.x - site.getX(), cell.z - site.getZ());
            if (d - cell.radius > canvas.range * 1.5 || cell.cstrength() < 0.1F) {
                continue;
            }
            if (cell.hail > 0.5F && cell.type >= StormCell.HAIL) {
                warnings.add(Component.translatable("realweather.radar.hail_warning", String.format("%.0f", 1F + cell.hail * cell.intensity * 5F),
                        km(d), compass(bearing(site, cell.x, cell.z))).withStyle(ChatFormatting.GREEN));
            }
        }
        lines.addAll(warnings);
        if (!warnings.isEmpty()) {
            lines.add(Component.empty());
        }
        lines.add(Component.translatable("realweather.radar.storms").withStyle(ChatFormatting.YELLOW));
        int shown = 0;
        for (ClientStorms.Cell cell : ClientStorms.cells()) {
            double d = Math.hypot(cell.x - site.getX(), cell.z - site.getZ());
            if (cell.cstrength() < 0.05F || d - cell.radius > canvas.range * 2) {
                continue;
            }
            float sp = Mth.sqrt(cell.vx * cell.vx + cell.vz * cell.vz) * 20F * 3.6F;
            float heading = (float) Math.atan2(cell.vx, -cell.vz);
            int dbz = (int) (8F + 48F * (float) Math.pow(cell.intensity * cell.cstrength(), 0.6) + (cell.hail > 0.1F ? 12 : 0));
            lines.add(Component.translatable("realweather.radar.storm_line", Component.translatable("realweather.radar.type." + cell.type),
                    km(d), compass(bearing(site, cell.x, cell.z)), compass(heading), String.format("%.0f", sp), dbz));
            if (++shown >= 5) {
                break;
            }
        }
        if (shown == 0) {
            lines.add(Component.translatable("realweather.radar.no_storms").withStyle(ChatFormatting.GRAY));
        }
        g.fill(x - 4, y - 2, x + w + 2, Math.min(height - 6, y + lines.size() * 10 + 4), 0xA0101418);
        int ly = y + 2;
        for (Component line : lines) {
            for (var seq : font.split(line, w - 2)) {
                if (ly > height - 14) {
                    return;
                }
                g.drawString(font, seq, x, ly, 0xFFE0E8F0, false);
                ly += 10;
            }
        }
    }

    private static String km(double blocks) {
        return blocks < 1000 ? String.format("%.0f m", blocks) : String.format("%.1f km", blocks / 1000.0);
    }

    private static float bearing(BlockPos from, double x, double z) {
        return (float) Math.atan2(x - from.getX(), -(z - from.getZ()));
    }

    private static Component compass(float radians) {
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        int i = Math.floorMod(Math.round(radians / (Mth.TWO_PI / 8F)), 8);
        return Component.translatable("realweather.dir." + dirs[i]);
    }

    private static void dot(GuiGraphics g, int x, int y, int r, int color) {
        g.fill(x - r, y - r, x + r + 1, y + r + 1, color);
    }

    private static void ring(GuiGraphics g, int x, int y, int r, int color) {
        int n = Math.max(12, r * 4);
        for (int i = 0; i < n; i++) {
            float a = i * Mth.TWO_PI / n;
            int px = x + Math.round(Mth.cos(a) * r);
            int py = y + Math.round(Mth.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void triangle(GuiGraphics g, int x, int y, int s, int color, boolean up) {
        for (int k = 0; k <= s; k++) {
            int half = up ? k : s - k;
            int row = y - s / 2 + k;
            g.fill(x - half, row, x + half + 1, row + 1, color);
        }
    }

    private void dottedLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i += 3) {
            int x = x0 + (x1 - x0) * i / Math.max(1, n);
            int y = y0 + (y1 - y0) * i / Math.max(1, n);
            if (inside(x, y)) {
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }
}
