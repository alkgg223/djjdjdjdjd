package com.realweather.client.space;

import com.mojang.blaze3d.platform.InputConstants;
import com.realweather.RealWeather;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

import static com.realweather.client.space.Space3D.ADD;

/**
 * Flying round a planet: from far out it is a real lit 3D world (its day and night sides, air, rings, clouds), and
 * you can go all the way round it; coming down, its smooth look gives way to the ground it is made of, block by
 * block, under its own sky; and you can land on it and look about. Drag to fly round it (or, low down, to look
 * about), the mouse wheel or the buttons to go down and up.
 */
public class PlanetViewScreen extends Screen {
    private static final ResourceLocation RINGS = RealWeather.id("textures/environment/space3d/rings.png");
    private static final ResourceLocation EARTH_CLOUDS = RealWeather.id("textures/environment/space3d/earth_clouds.png");
    private static final ResourceLocation EARTH_LIGHTS = RealWeather.id("textures/environment/space3d/earth_lights.png");
    private static final double FOV = 70;
    /** Stars round the planet (its own frame): direction, brightness and colour. */
    private static final float[] STARS = new float[3000 * 5];
    /** Distance from the Sun (AU), for the size of the Sun in each world's sky. */
    private static final String[] SOLAR = {"mercury", "venus", "earth", "moon", "mars", "ceres", "jupiter", "saturn", "uranus", "neptune", "pluto",
            "haumea", "makemake", "eris"};
    private static final double[] AU = {0.39, 0.72, 1, 1, 1.52, 2.77, 5.2, 9.5, 19.2, 30, 39, 43, 45, 68};

    static {
        Random r = new Random(4242);
        for (int i = 0; i < 3000; i++) {
            double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
            STARS[i * 5] = (float) (s * Math.cos(a));
            STARS[i * 5 + 1] = (float) z;
            STARS[i * 5 + 2] = (float) (s * Math.sin(a));
            STARS[i * 5 + 3] = (float) Math.pow(r.nextDouble(), 3);
            STARS[i * 5 + 4] = r.nextFloat();
        }
    }

    /** What is being visited: a planet, a star, a pulsar, a black hole or a nebula. */
    private static final int PLANET = 0, SUN = 1, PULSAR = 2, HOLE = 3, NEBULA = 4;
    private final int mode;
    private final Catalog.Target target;
    private final Catalog.Body body;
    private final SpaceShaders.Planet planet;
    private final int radius;
    private final float sunR, sunG, sunB;
    private final double sunSize;
    private double lon = 0.4, lat = 0.35;
    /** Height above the sphere of the planet's radius (blocks), and where it is gliding to. */
    private double alt, wantAlt;
    private double lookYaw = 0, lookPitch = -12;
    private long last;
    private double clock;
    private boolean landedSaid;

    public PlanetViewScreen(Catalog.Target target) {
        super(Component.translatable("realweather.target." + target.id));
        this.target = target;
        this.body = target.body;
        int k = body.kind;
        this.mode = k == Catalog.ROCKY || k == Catalog.GAS ? PLANET : k == Catalog.PULSAR ? PULSAR : k == Catalog.BLACKHOLE ? HOLE
                : k == Catalog.NEBULA ? NEBULA : SUN;
        boolean gas = body.kind == Catalog.GAS;
        this.radius = mode == PLANET ? (gas ? 768 : 512) : (int) CloseViews.radius(body);
        this.planet = body.voxel(radius);
        if (!gas) {
            planet.relief = 14;
            planet.sea = planet.sea >= 0 ? 5 : -1;
            planet.hills = 4;
        }
        planet.airHeight = Math.max(60F, radius * 0.15F);
        planet.steps = 360F;
        planet.ambient = 0.025F;
        // The star in this world's sky: its colour and size.
        float[] hs = Catalog.hostStar(target);
        sunR = hs[0];
        sunG = hs[1];
        sunB = hs[2];
        sunSize = hs[3];
        planet.lightR = 0.8F + 0.2F * sunR;
        planet.lightG = 0.8F + 0.2F * sunG;
        planet.lightB = 0.8F + 0.2F * sunB;
        alt = wantAlt = mode == PLANET ? radius * 2.6 : (CloseViews.start(body) - 1) * radius;
    }

    private void travel(int how) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.realweather.net.TravelPayload(target.id, how, lon, lat));
        onClose();
    }

    private static boolean away() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == com.realweather.space.Worlds.DIMENSION;
    }

    // ------------------------------------------------------------------------------------------- controls

    @Override
    protected void init() {
        int bw = 54, bh = 20, x = width - bw - 6;
        addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.down"), b -> descend(0.55)).bounds(x, height / 2 - 48, bw, bh)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.up"), b -> descend(1 / 0.55)).bounds(x, height / 2 - 24, bw, bh)
                .build());
        if (mode == PLANET) {
            addRenderableWidget(Button.builder(Component.translatable(body.kind == Catalog.GAS ? "realweather.planet_map.dive" : "realweather.planet_map.land"),
                    b -> wantAlt = groundHere() + 1.62).bounds(x, height / 2 + 4, bw, bh).build());
            addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.orbit"), b -> wantAlt = radius * 2.6)
                    .bounds(x, height / 2 + 28, bw, bh).build());
        }
        // Really going there: to where you are over it (its surface, or orbit high above), and home again.
        int tw = 110;
        if (com.realweather.space.Worlds.byId(target.id) != null) {
            addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.go_surface"), b -> travel(0))
                    .bounds(width - tw - 6, height - 50, tw, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.go_orbit"), b -> travel(1))
                    .bounds(width - tw - 6, height - 26, tw, 20).build());
        } else if (com.realweather.space.Sites.IDS.contains(target.id)) {
            addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.go_there"), b -> travel(com.realweather.net.TravelPayload.SITE))
                    .bounds(width - tw - 6, height - 26, tw, 20).build());
        } else if (away() && "earth".equals(target.id)) {
            addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.go_home"), b -> travel(2))
                    .bounds(width - tw - 6, height - 26, tw, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("realweather.planet_map.back"), b -> minecraft.setScreen(new PlanetMapScreen()))
                .bounds(6, height - 26, 70, 20).build());
    }

    /** Height of the ground under the camera (blocks above the radius). */
    private double groundHere() {
        if (mode != PLANET) {
            // How close you can come: into a nebula, near a star's fire, outside a black hole's photon sphere.
            return (Math.max(0.03, CloseViews.closest(body)) - 1) * radius;
        }
        if (body.kind == Catalog.GAS) {
            // No ground at all: down into the clouds, until the heat and pressure would crush you.
            return -radius * 0.025;
        }
        double[] u = up();
        float g = PlanetMaps.ground(planet, u[0], u[1], u[2]);
        // The block column right under us (its cell's own direction, as the ray tracing sees it).
        double r = radius + g + 0.5;
        double cx = Math.floor(u[0] * r) + 0.5, cy = Math.floor(u[1] * r) + 0.5, cz = Math.floor(u[2] * r) + 0.5;
        return Math.max(g, PlanetMaps.ground(planet, cx, cy, cz));
    }

    private void descend(double factor) {
        double g = groundHere();
        double h = Math.max(1.62, (wantAlt - g) * factor);
        wantAlt = Mth.clamp(g + h, g + 1.62, radius * (mode == HOLE ? 30.0 : 6.0));
    }

    /** High up, dragging flies you round the planet; low down, it looks about. */
    private double orbitness() {
        if (mode != PLANET) {
            return 1;
        }
        return Space3D.smooth((float) (radius * 0.06), (float) (radius * 0.3), (float) (alt - groundHere()));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        double o = orbitness();
        double h = Math.max(2, alt - groundHere());
        // Flying round: slower the lower you are, so the ground moves under you at a steady pace on the screen.
        double k = mode == PLANET ? Math.min(0.006, h / radius * 0.0028) : 0.006;
        lon -= dx * k * o / Math.max(0.2, Math.cos(lat));
        lat = Mth.clamp(lat + dy * k * o, -1.45, 1.45);
        lookYaw += dx * 0.35 * (1 - o);
        lookPitch = Mth.clamp(lookPitch - dy * 0.35 * (1 - o), -89, 89);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        descend(sy > 0 ? 0.82 : 1 / 0.82);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_UP) {
            descend(0.8);
            return true;
        }
        if (key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_DOWN) {
            descend(1 / 0.8);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        // Keys held down keep moving.
        long w = minecraft.getWindow().getWindow();
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_SPACE)) {
            descend(1 / 0.93);
        }
        if (InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL)) {
            descend(0.93);
        }
    }

    /** For tests: straight to a height above the ground (negative: high in orbit), looking this way. */
    public void testView(double height, double yaw, double pitch) {
        double g = groundHere();
        // (A negative height below -1: that far inside, for nebulae.)
        alt = wantAlt = height < -1 ? Math.max(g + 1.62, height) : height < 0 ? radius * 2.6 : g + Math.max(1.62, height);
        lookYaw = yaw;
        lookPitch = pitch;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The 3D view fills the screen.
    }

    // ------------------------------------------------------------------------------------------- the view

    private double[] up() {
        return new double[]{Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)};
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        double dt = last == 0 ? 0.016 : Mth.clamp((now - last) / 1000.0, 0, 0.1);
        last = now;
        clock += dt;
        // Whatever the game's own screen has queued is drawn first, under the view.
        g.flush();
        double ground = groundHere();
        wantAlt = Math.max(wantAlt, ground + 1.62);
        double ease = 1 - Math.exp(-dt * 3.5);
        alt = Math.exp(Mth.lerp(ease, Math.log(Math.max(alt - ground, 0.5)), Math.log(wantAlt - ground))) + ground;
        alt = Math.max(alt, ground + 1.62);
        try {
            draw3D();
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            RealWeather.LOG.error("Planet map view failed", e);
            if (Space3D.isOpen()) {
                Space3D.end();
            }
            minecraft.setScreen(new PlanetMapScreen());
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        // What and where.
        double h = alt - ground;
        g.drawString(font, title, 8, 8, 0xFFFFE080, true);
        Component where = mode != PLANET ? Component.translatable("realweather.planet_map.distance", String.format("%.1f", (alt + radius) / radius))
                : h <= 1.8 ? Component.translatable("realweather.planet_map.landed")
                : Component.translatable("realweather.planet_map.height", String.format("%.0f", h));
        g.drawString(font, where, 8, 20, 0xFFE0E0E0, true);
        g.drawString(font, Component.translatable("realweather.planet_map.hint"), 8, height - 40, 0xFFB0B0B0, true);
        if (mode == PLANET && h <= 1.8 && !landedSaid) {
            landedSaid = true;
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("realweather.planet_map.touchdown", title), true);
            }
        } else if (h > 6) {
            landedSaid = false;
        }
    }

    private void draw3D() {
        var win = minecraft.getWindow();
        int pw = Math.max(1, win.getWidth()), ph = Math.max(1, win.getHeight());
        double aspect = (double) pw / ph;
        double fovY = Math.toRadians(FOV);
        // The camera: where it is over the planet, and which way it looks.
        double[] u = up();
        double[] east = {-Math.sin(lon), 0, Math.cos(lon)};
        double[] north = {-Math.sin(lat) * Math.cos(lon), Math.cos(lat), -Math.sin(lat) * Math.sin(lon)};
        double o = orbitness();
        double yaw = Math.toRadians(lookYaw * (1 - o));
        double pitch = Math.toRadians(Mth.lerp(o, lookPitch, -90));
        double[] fh = new double[3], f = new double[3], right = new double[3], cu = new double[3];
        for (int i = 0; i < 3; i++) {
            fh[i] = Math.cos(yaw) * north[i] + Math.sin(yaw) * east[i];
            right[i] = Math.cos(yaw) * east[i] - Math.sin(yaw) * north[i];
            f[i] = Math.cos(pitch) * fh[i] + Math.sin(pitch) * u[i];
        }
        cu[0] = right[1] * f[2] - right[2] * f[1];
        cu[1] = right[2] * f[0] - right[0] * f[2];
        cu[2] = right[0] * f[1] - right[1] * f[0];
        double[] view = {right[0], cu[0], -f[0], right[1], cu[1], -f[1], right[2], cu[2], -f[2]};
        double camR = radius + alt;
        double[] cam = {u[0] * camR, u[1] * camR, u[2] * camR};
        Matrix4f proj = new Matrix4f().perspective((float) fovY, (float) aspect, 0.05F, 100000F);
        Space3D.beginScreen(proj, view, clock * 20);
        try {
            Space3D.fullScreen(0F, 0F, 0.004F, 1F);
            // The star of this world, going slowly round (day and night).
            double sa = clock * (Math.PI * 2 / 600.0) + 0.9, decl = 0.25;
            double[] sun = {Math.cos(sa) * Math.cos(decl), Math.sin(decl), Math.sin(sa) * Math.cos(decl)};
            double[] sv = new double[3];
            Space3D.apply(view, sun[0], sun[1], sun[2], sv);
            Space3D.range(10F, 20000F);
            // Stars.
            double px = 2.0 / (Space3D.projY() * ph);
            double[] q = new double[3];
            for (int i = 0; i < 3000; i++) {
                Space3D.apply(view, STARS[i * 5], STARS[i * 5 + 1], STARS[i * 5 + 2], q);
                if (q[2] > -0.05) {
                    continue;
                }
                float b = STARS[i * 5 + 3], t = STARS[i * 5 + 4];
                float r = t < 0.3F ? 1F : t < 0.6F ? 0.95F : 0.8F, gg = t < 0.3F ? 0.82F : t < 0.6F ? 0.95F : 0.88F, bb = t < 0.3F ? 0.65F : 1F;
                Space3D.billboard(Space3D.GLOW, ADD, q[0] * 5000, q[1] * 5000, q[2] * 5000, px * 5000 * (1.4 + 3 * b), 0F, r, gg, bb, 0.4F + 0.6F * b);
            }
            if (mode == PLANET && sv[2] < 0) {
                double s = 5000;
                Space3D.billboard(Space3D.GLOW, ADD, sv[0] * s, sv[1] * s, sv[2] * s, s * sunSize * 5, 0F, sunR, sunG, sunB, 0.55F);
                Space3D.billboard(Space3D.STAR, ADD, sv[0] * s, sv[1] * s, sv[2] * s, s * sunSize * 9, 0F, sunR, sunG, sunB, 0.6F);
                Space3D.billboard(Space3D.GLOW, ADD, sv[0] * s, sv[1] * s, sv[2] * s, s * sunSize * 1.3, 0F, 1F, 1F, 1F, 1F);
            }
            if (mode != PLANET) {
                drawObject(view, cam, f, right, cu, 2 * (radius + alt) * Math.tan(fovY / 2) / ph, Math.tan(fovY / 2) * aspect, Math.tan(fovY / 2));
                return;
            }
            // The planet, smooth.
            double[] c = new double[3];
            Space3D.apply(view, -cam[0], -cam[1], -cam[2], c);
            double dist = camR;
            float near = (float) Math.max(0.05, (alt - planet.relief - 3) * 0.5);
            Space3D.clearDepth();
            Space3D.range(near, (float) (dist + radius * 2.6));
            double sr = radius + planet.relief * 0.4;
            Space3D.scale(1, body.oblate, 1);
            Space3D.sphere(body.map, c[0], c[1], c[2], sr, view, sv[0], sv[1], sv[2], 0.02F, body.r, body.g, body.b, body.glow, 1F, false, 0F, 72);
            Space3D.scale(1, 1, 1);
            boolean earth = "earth".equals(body.id);
            float high = Space3D.smooth((float) (radius * 0.05), (float) (radius * 0.4), (float) (alt - groundHere()));
            if (earth) {
                Space3D.sphereMode(ADD);
                Space3D.nightSide(true);
                Space3D.sphere(EARTH_LIGHTS, c[0], c[1], c[2], sr + 0.3, view, sv[0], sv[1], sv[2], 0F, 1F, 0.85F, 0.6F, 0F, 0.9F * high, false, 0F, 72);
                Space3D.nightSide(false);
                Space3D.sphereMode(Space3D.NORMAL);
                Space3D.sphere(EARTH_CLOUDS, c[0], c[1], c[2], sr + planet.relief + 6, view, sv[0], sv[1], sv[2], 0.01F, 1F, 1F, 1F, 0F, 0.9F * high,
                        false, (float) (clock * 0.002 % 1.0), 72);
            }
            // Made of blocks, close up.
            double h = Math.max(1, alt - groundHere());
            double blockPx = ph / (2 * Math.tan(fovY / 2) * h);
            planet.alpha = Space3D.smooth(1.3F, 3.2F, (float) blockPx);
            planet.pixelGrow = (float) (2 * Math.tan(fovY / 2) / ph);
            planet.lx = sun[0];
            planet.ly = sun[1];
            planet.lz = sun[2];
            double tx = Math.tan(fovY / 2) * aspect, ty = Math.tan(fovY / 2);
            double[] rdx = {right[0] * tx, right[1] * tx, right[2] * tx}, rdy = {cu[0] * ty, cu[1] * ty, cu[2] * ty};
            double px = 2 * dist * Math.tan(fovY / 2) / ph;
            if (body.kind == Catalog.GAS) {
                SpaceShaders.GasLook gl = GasGiants.look(body, clock);
                gl.lx = sun[0];
                gl.ly = sun[1];
                gl.lz = sun[2];
                gl.lightR = planet.lightR;
                gl.lightG = planet.lightG;
                gl.lightB = planet.lightB;
                gl.pixel = (float) (px / radius);
                SpaceShaders.gasPlanet(gl, -1F, -1F, 1F, 1F, new double[]{cam[0] / radius, cam[1] / radius, cam[2] / radius}, new double[3],
                        new double[3], f, rdx, rdy);
            } else if (body.oblate >= 0.9F) {
                SpaceShaders.voxel(planet, -1F, -1F, 1F, 1F, cam, new double[3], new double[3], f, rdx, rdy);
            }
            // Rings in front (their particles, close up), the air round the edge, aurorae and plumes.
            if (body.rings) {
                CloseViews.rings(body, c, sr, view, sv, clock, px, 1F);
            }
            double[] ax = {view[3], view[4], view[5]};
            Aurorae.draw(body.id, c[0], c[1], c[2], sr, ax, sv, clock, px, 1F);
            Plumes.draw(body.id, c[0], c[1], c[2], sr, view, sv, clock, px, 1F);
            if (body.kind == Catalog.GAS && alt < radius * 0.01) {
                // Inside its clouds: thick haze of its colour, darker deeper down.
                float in = Space3D.smooth((float) (radius * 0.01), (float) (-radius * 0.02), (float) alt);
                Space3D.clearDepth();
                Space3D.range(0.05F, 1000F);
                Space3D.fullScreen(body.ar * 0.5F * (1 - in * 0.6F), body.ag * 0.45F * (1 - in * 0.7F), body.ab * 0.4F * (1 - in * 0.8F), 0.5F + 0.45F * in);
            }
            if (body.air) {
                Space3D.limb(c[0], c[1], c[2], sr, planet.airHeight, sv[0], sv[1], sv[2], body.ar, body.ag, body.ab, 0.55F);
            }
            if (body.halo > 0) {
                Space3D.billboard(Space3D.GLOW, ADD, c[0], c[1], c[2], sr * 2.6, 0F, body.hr, body.hg, body.hb, body.halo * high);
            }
        } finally {
            Space3D.end();
        }
    }

    /**
     * A star, pulsar, black hole or nebula seen from close by: a star's boiling surface with its fire, flares and
     * corona; a pulsar's spinning beams; the black hole ray traced from where you are (inside its lensed sky); the
     * nebula's gas all round you, from inside it.
     */
    private void drawObject(double[] view, double[] cam, double[] f, double[] right, double[] cu, double px, double tx, double ty) {
        CloseViews.object(body, view, cam, radius, f, right, cu, px, tx, ty, clock);
    }
}
