package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.realweather.client.space.Space3D.ADD;

/**
 * The map of the universe in 3D: everything the catalogue knows, each in its direction on the sky and at its distance
 * (on a logarithmic scale, so the Sun's neighbours, the far side of the galaxy and galaxies billions of light years away
 * all fit): the solar system at the centre, the stars round it, the nebulae, pulsars and black holes of the Milky Way,
 * the galaxies beyond, the cosmic events going on and the hundred kinds of object. Drag to turn round it, the wheel to
 * come closer; point at anything to see what it is, click it for its information, to look at it close up or go there.
 */
public class UniverseMapScreen extends Screen {
    private static final String[] FILTERS = {"all", "stars", "planets", "nebulae", "galaxies", "holes", "objects", "events"};
    private static int filter;
    private static double yaw = 0.7, pitch = 0.42, zoom = 420;
    private final Screen back;
    private final List<Entry> entries = new ArrayList<>();
    private Entry selected, hover;
    private double clock;
    private long last;
    private double[] focus = {0, 0, 0}, focusWant = {0, 0, 0};
    /** Where each entry is on the screen this frame (gui x, y; NaN when behind). */
    private double[] sx = new double[0], sy = new double[0];

    /** One thing on the map: its target, place (map units), colour, size and kind of marker. */
    private record Entry(Catalog.Target t, double x, double y, double z, float r, float g, float b, float size, int kind) {}

    private static final int K_PLANET = 0, K_STAR = 1, K_NEBULA = 2, K_GALAXY = 3, K_HOLE = 4, K_OBJECT = 5, K_EVENT = 6, K_PULSAR = 7;

    public UniverseMapScreen(Screen back) {
        super(Component.translatable("realweather.universe.title"));
        this.back = back;
    }

    /** Light years in a catalogue distance ("4.25 ly", "~2.5 million ly", "30-50 AU", ...). */
    static double lightYears(String d) {
        if (d == null) {
            return 1000;
        }
        String s = d.toLowerCase(Locale.ROOT).replace(",", "").replace("~", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9][0-9 ]*(?:\\.[0-9]+)?)").matcher(s);
        if (!m.find()) {
            return 1000;
        }
        double v;
        try {
            v = Double.parseDouble(m.group(1).replace(" ", ""));
        } catch (NumberFormatException e) {
            return 1000;
        }
        if (s.contains("billion")) {
            v *= 1.0E9;
        } else if (s.contains("million")) {
            v *= 1.0E6;
        } else if (s.contains("thousand")) {
            v *= 1.0E3;
        }
        if (s.contains("au") && !s.contains("ly")) {
            v /= 63241.0;
        } else if (s.contains("km")) {
            v /= 9.461E12;
        }
        return Math.max(0, v);
    }

    /** Map units for a distance: a logarithmic scale. */
    private static double scale(double ly) {
        return 24 * Math.log10(1 + ly);
    }

    @Override
    protected void init() {
        if (entries.isEmpty()) {
            build();
        }
        int bw = Math.min(64, (width - 20) / FILTERS.length - 2);
        int x = width / 2 - (bw + 2) * FILTERS.length / 2;
        for (int i = 0; i < FILTERS.length; i++) {
            int k = i;
            Button b = Button.builder(Component.translatable("realweather.universe.show." + FILTERS[i]), btn -> {
                filter = k;
                rebuildWidgets();
            }).bounds(x + i * (bw + 2), 22, bw, 18).build();
            b.active = filter != i;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(6, height - 26, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.universe.centre"), b -> {
            selected = null;
            focusWant = new double[]{0, 0, 0};
            rebuildWidgets();
        }).bounds(70, height - 26, 80, 20).build());
        if (selected != null) {
            Catalog.Target t = selected.t();
            int y = height - 26;
            addRenderableWidget(Button.builder(Component.translatable("realweather.obs.view"), b -> minecraft.setScreen(new PlanetViewScreen(t)))
                    .bounds(width - 214, y, 66, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("realweather.universe.focus"), b -> {
                focusWant = new double[]{selected.x(), selected.y(), selected.z()};
                zoom = Math.max(4, Math.min(zoom, 60));
            }).bounds(width - 144, y, 66, 20).build());
            boolean travel = com.realweather.space.Sites.IDS.contains(t.id) || com.realweather.space.Worlds.byId(t.id) != null;
            if (travel) {
                addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.go_there"), b -> {
                    int how = com.realweather.space.Worlds.byId(t.id) != null ? 1 : com.realweather.net.TravelPayload.SITE;
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.realweather.net.TravelPayload(t.id, how, 0, 0));
                    onClose();
                }).bounds(width - 74, y, 68, 20).build());
            }
        }
    }

    /** Everything into the map. */
    private void build() {
        entries.clear();
        List<Catalog.Target> all = new ArrayList<>(Catalog.TARGETS);
        for (CosmicEvents.Event e : CosmicEvents.window()) {
            if (CosmicEvents.status(e) != CosmicEvents.OVER) {
                all.add(e.target());
            }
        }
        int solarIndex = 0;
        for (Catalog.Target t : all) {
            Catalog.Body b = t.body;
            if (t.sys == Catalog.earth || t.sys == Catalog.moon && !"moon".equals(t.id)) {
                continue;
            }
            int kind = switch (b.kind) {
                case Catalog.STAR, Catalog.DWARF -> K_STAR;
                case Catalog.NEBULA -> K_NEBULA;
                case Catalog.GALAXY -> K_GALAXY;
                case Catalog.BLACKHOLE, Catalog.MERGER -> K_HOLE;
                case Catalog.PULSAR -> K_PULSAR;
                case Catalog.EVENT -> K_EVENT;
                case Catalog.OBJECT -> K_OBJECT;
                default -> K_PLANET;
            };
            double[] p;
            if (t.sys.dir == null) {
                // Our own solar system, round the middle: each at its distance from the Sun (in AU, also on a log scale).
                SolarSystem.Body sb = SolarSystem.byId(t.id);
                double au = 1;
                if (sb != null) {
                    SolarSystem.Body top = sb;
                    while (top.parent() != null && SolarSystem.byId(top.parent()) != null && top.parent().length() > 0
                            && !top.parent().equals("sun")) {
                        top = SolarSystem.byId(top.parent());
                    }
                    au = Math.max(0.05, top.a() / SolarSystem.AU);
                }
                double r = 0.6 + 2.2 * Math.log10(1 + au);
                double a = solarIndex++ * 2.39996;
                p = new double[]{Math.cos(a) * r, Math.sin(a * 3) * 0.08, Math.sin(a) * r};
                if ("sun".equals(t.id)) {
                    p = new double[]{0, 0, 0};
                }
            } else {
                double d = scale(lightYears(t.sys.distance));
                if (b.kind == Catalog.OBJECT && b.eventType >= 100 && b.eventType <= 102) {
                    // The belts and the Oort Cloud are round the Sun.
                    d = 0.6 + 2.2 * Math.log10(1 + lightYears(t.sys.distance) * 63241);
                }
                p = new double[]{t.sys.dir[0] * d, -t.sys.dir[1] * d * 0.6, t.sys.dir[2] * d};
            }
            float[] c = colour(kind, b);
            float size = switch (kind) {
                case K_GALAXY -> 2.2F;
                case K_NEBULA -> 1.2F;
                case K_HOLE -> 1.1F;
                case K_EVENT -> 1.0F;
                default -> 0.6F;
            };
            entries.add(new Entry(t, p[0], p[1], p[2], c[0], c[1], c[2], size, kind));
        }
    }

    private static float[] colour(int kind, Catalog.Body b) {
        return switch (kind) {
            case K_STAR -> new float[]{Math.min(1F, b.r * 1.1F), Math.min(1F, b.g * 1.1F), Math.min(1F, b.b * 1.1F)};
            case K_NEBULA -> new float[]{1F, 0.45F, 0.6F};
            case K_GALAXY -> new float[]{0.75F, 0.82F, 1F};
            case K_HOLE -> new float[]{0.75F, 0.45F, 1F};
            case K_PULSAR -> new float[]{0.5F, 0.85F, 1F};
            case K_EVENT -> new float[]{1F, 0.4F, 0.25F};
            case K_OBJECT -> new float[]{0.5F, 1F, 0.75F};
            default -> new float[]{0.95F, 0.85F, 0.6F};
        };
    }

    private static boolean shown(Entry e) {
        return switch (filter) {
            case 1 -> e.kind() == K_STAR;
            case 2 -> e.kind() == K_PLANET;
            case 3 -> e.kind() == K_NEBULA;
            case 4 -> e.kind() == K_GALAXY;
            case 5 -> e.kind() == K_HOLE || e.kind() == K_PULSAR;
            case 6 -> e.kind() == K_OBJECT;
            case 7 -> e.kind() == K_EVENT;
            default -> true;
        };
    }

    // ------------------------------------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double dt = last == 0 ? 0.016 : Mth.clamp((now - last) / 1000.0, 0, 0.1);
        last = now;
        clock += dt;
        double ease = 1 - Math.exp(-dt * 4);
        for (int i = 0; i < 3; i++) {
            focus[i] += (focusWant[i] - focus[i]) * ease;
        }
        g.flush();
        try {
            draw3D();
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            RealWeather.LOG.error("Universe map failed", e);
            if (Space3D.isOpen()) {
                Space3D.end();
            }
            onClose();
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFE080);
        // What the mouse is over.
        hover = null;
        double best = 8;
        for (int i = 0; i < entries.size() && i < sx.length; i++) {
            if (Double.isNaN(sx[i]) || !shown(entries.get(i))) {
                continue;
            }
            double d = Math.hypot(sx[i] - mouseX, sy[i] - mouseY);
            if (d < best) {
                best = d;
                hover = entries.get(i);
            }
        }
        // The names of the galaxies, and of what is chosen or pointed at.
        for (int i = 0; i < entries.size() && i < sx.length; i++) {
            Entry e = entries.get(i);
            if (Double.isNaN(sx[i]) || !shown(e)) {
                continue;
            }
            boolean always = e.kind() == K_GALAXY && zoom > 120 || e == selected || e == hover
                    || zoom < 30 && Math.hypot(sx[i] - width / 2.0, sy[i] - height / 2.0) < 140;
            if (always) {
                g.drawString(font, e.t().label(), (int) sx[i] + 5, (int) sy[i] - 4, e == selected ? 0xFFFFE080 : 0xFFD0E0F0, true);
            }
        }
        // The scale's rings.
        for (double[] ring : RINGS) {
            double[] q = project(ring[1], 0, 0);
            if (q != null) {
                g.drawString(font, Component.translatable("realweather.universe.ring." + (int) ring[0]), (int) q[0] + 3, (int) q[1] + 2, 0xFF6080A0,
                        false);
            }
        }
        if (selected != null) {
            infoPanel(g);
        }
        g.drawString(font, Component.translatable("realweather.universe.hint"), 6, height - 38, 0xFF9090A0, true);
    }

    /** Distance rings: label index and radius. */
    private static final double[][] RINGS = {{0, scale(10)}, {1, scale(1000)}, {2, scale(100000)}, {3, scale(1.0E7)}, {4, scale(1.0E9)}};

    private double[] view = new double[9], cam = new double[3];
    private double px, py;

    private double[] project(double x, double y, double z) {
        double[] q = new double[3];
        Space3D.apply(view, x - cam[0], y - cam[1], z - cam[2], q);
        if (q[2] > -0.01) {
            return null;
        }
        double nx = px * q[0] / -q[2], ny = py * q[1] / -q[2];
        if (Math.abs(nx) > 1.1 || Math.abs(ny) > 1.1) {
            return null;
        }
        return new double[]{(nx + 1) / 2 * width, (1 - ny) / 2 * height};
    }

    private void draw3D() {
        var win = minecraft.getWindow();
        int pw = Math.max(1, win.getWidth()), ph = Math.max(1, win.getHeight());
        double aspect = (double) pw / ph;
        double fovY = Math.toRadians(60);
        double[] f = {-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch), -Math.cos(pitch) * Math.cos(yaw)};
        double[] up = {0, 1, 0};
        double[] right = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double rl = Math.sqrt(right[0] * right[0] + right[1] * right[1] + right[2] * right[2]);
        for (int i = 0; i < 3; i++) {
            right[i] /= rl;
        }
        double[] cu = {right[1] * f[2] - right[2] * f[1], right[2] * f[0] - right[0] * f[2], right[0] * f[1] - right[1] * f[0]};
        view = new double[]{right[0], cu[0], -f[0], right[1], cu[1], -f[1], right[2], cu[2], -f[2]};
        cam = new double[]{focus[0] - f[0] * zoom, focus[1] - f[1] * zoom, focus[2] - f[2] * zoom};
        py = 1 / Math.tan(fovY / 2);
        px = py / aspect;
        Matrix4f proj = new Matrix4f().perspective((float) fovY, (float) aspect, 0.01F, 1.0E6F);
        Space3D.beginScreen(proj, view, clock * 20);
        try {
            Space3D.fullScreen(0F, 0F, 0.006F, 1F);
            Space3D.range(0.01F, 1.0E6F);
            double pixAng = 2 / (py * ph);
            double[] q = new double[3];
            // Faint background stars, far off in every direction.
            float[] field = TelescopeView.field();
            for (int i = 0; i < 3000; i++) {
                Space3D.apply(view, field[i * 5], field[i * 5 + 1], field[i * 5 + 2], q);
                if (q[2] > 0) {
                    continue;
                }
                Space3D.billboard(Space3D.GLOW, ADD, q[0] * 5.0E5, q[1] * 5.0E5, q[2] * 5.0E5, pixAng * 5.0E5 * 1.2, 0F, 0.8F, 0.85F, 1F,
                        0.12F + 0.25F * field[i * 5 + 3]);
            }
            // Our galaxy round the middle (the Sun about halfway out from its centre).
            Galaxies.Look mw = Galaxies.look("milky_way");
            if (mw != null && (filter == 0 || filter == 4)) {
                double R = scale(50000);
                Space3D.apply(view, -R * 0.45 - cam[0], -cam[1], -cam[2], q);
                double[] frame = new double[9];
                Space3D.mul(view, Space3D.orient(0, 1, 0, 0.6), frame);
                double dist = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                Galaxies.draw(mw, q[0], q[1], q[2], R, Galaxies.frame(mw, frameFlat(frame, mw)), clock, pixAng * Math.max(1, dist), 0.35F);
            }
            // The rings of the distance scale, in the plane of the map.
            for (double[] ring : RINGS) {
                double[] prev = null;
                for (int i = 0; i <= 96; i++) {
                    double a = Math.PI * 2 * i / 96;
                    double[] p = new double[3];
                    Space3D.apply(view, Math.cos(a) * ring[1] - cam[0], -cam[1], Math.sin(a) * ring[1] - cam[2], p);
                    if (prev != null && prev[2] < 0 && p[2] < 0) {
                        double w = Math.max(0.002, pixAng * Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) * 0.7);
                        Space3D.ribbon(TelescopeView.LINE_PUBLIC, ADD, prev[0], prev[1], prev[2], p[0], p[1], p[2], w, w, 0.35F, 0.5F, 0.8F, 0.25F, 0.25F);
                    }
                    prev = p;
                }
            }
            // Everything in it.
            if (sx.length != entries.size()) {
                sx = new double[entries.size()];
                sy = new double[entries.size()];
            }
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                sx[i] = Double.NaN;
                if (!shown(e)) {
                    continue;
                }
                Space3D.apply(view, e.x() - cam[0], e.y() - cam[1], e.z() - cam[2], q);
                if (q[2] > -0.01) {
                    continue;
                }
                double dist = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                double pxHere = pixAng * dist;
                float pulse = e.kind() == K_EVENT ? 0.6F + 0.4F * (float) Math.sin(clock * 4 + i) : 1F;
                double size = Math.max(pxHere * (e == selected ? 5 : 2.5), e.size() * 0.15);
                Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], size * 2, 0F, e.r(), e.g(), e.b(), 0.35F * pulse);
                Space3D.billboard(Space3D.STAR, ADD, q[0], q[1], q[2], size * 2.6, 0F, e.r(), e.g(), e.b(), 0.75F * pulse);
                if (e == selected) {
                    Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], size * 6, (float) clock, 1F, 0.9F, 0.5F, 0.25F);
                }
                double nx = px * q[0] / -q[2], ny = py * q[1] / -q[2];
                if (Math.abs(nx) <= 1.05 && Math.abs(ny) <= 1.05) {
                    sx[i] = (nx + 1) / 2 * width;
                    sy[i] = (1 - ny) / 2 * height;
                }
            }
            // The Sun at the very centre.
            Space3D.apply(view, -cam[0], -cam[1], -cam[2], q);
            if (q[2] < 0) {
                double d = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                Space3D.billboard(Space3D.GLOW, ADD, q[0], q[1], q[2], Math.max(pixAng * d * 6, 0.3), 0F, 1F, 0.9F, 0.65F, 0.8F);
            }
        } finally {
            Space3D.end();
        }
    }

    /** A frame that lays the galaxy's disc flat in the map's plane (undoing the tilt it is seen with from Earth). */
    private static double[] frameFlat(double[] base, Galaxies.Look g) {
        double[] out = new double[9];
        Space3D.mul(base, Space3D.orient(0, Math.cos(Math.PI / 2 - g.tilt()), -Math.sin(Math.PI / 2 - g.tilt()), 0), out);
        return out;
    }

    private void infoPanel(GuiGraphics g) {
        Catalog.Target t = selected.t();
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        int w = Math.min(240, width / 2);
        lines.addAll(font.split(t.label().copy().withStyle(net.minecraft.ChatFormatting.YELLOW), w));
        lines.addAll(font.split(Component.translatable("realweather.object.distance", t.sys.distance), w));
        Component info = t.group == Catalog.EVENTS ? Component.translatable("realweather.event." + t.body.eventType + ".info")
                : t.group == Catalog.OBJECTS ? Component.translatable("realweather.object." + t.body.eventType + ".info")
                : Component.translatable("realweather.target." + t.id + ".info");
        lines.addAll(font.split(info, w));
        int x = 6, y = 46;
        g.fill(x - 3, y - 3, x + w + 3, y + lines.size() * 10 + 2, 0xA0000814);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + i * 10, 0xFFE8EEF8, false);
        }
    }

    // ------------------------------------------------------------------------------------------- controls

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && hover != null) {
            selected = hover;
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        yaw -= dx * 0.008;
        pitch = Mth.clamp(pitch + dy * 0.008, -1.5, 1.5);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        zoom = Mth.clamp(zoom * (sy > 0 ? 0.85 : 1 / 0.85), 1.5, 1500);
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
