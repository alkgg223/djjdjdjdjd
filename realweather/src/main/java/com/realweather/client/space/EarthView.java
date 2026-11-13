package com.realweather.client.space;

import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

import static com.realweather.client.space.Space3D.ADD;

/**
 * Going up and up: the sky darkens to the black of space, the stars come out by day, and at 700 blocks you leave the
 * ozone layer; higher still the world curves away into a whole 3D Earth under you (oceans, continents, ice, clouds,
 * the thin blue line of its air, and city lights on its night side), shrinking as you rise.
 */
public final class EarthView {
    private static final ResourceLocation EARTH = RealWeather.id("textures/environment/space3d/earth.png");
    private static final ResourceLocation CLOUDS = RealWeather.id("textures/environment/space3d/earth_clouds.png");
    private static final ResourceLocation LIGHTS = RealWeather.id("textures/environment/space3d/earth_lights.png");
    /** The Earth's radius (blocks) for the view from space; sea level; the top of the ozone layer. */
    public static final double R = 6000, SEA = 63, OZONE = 700;
    private static double lastY = Double.NaN;

    private EarthView() {}

    /** How far into space the camera is (0 in the air, 1 in space). */
    public static float space(double y) {
        return Space3D.smooth(330F, (float) OZONE, (float) y);
    }

    public static boolean wanted(double y) {
        return y > 330;
    }

    /** Height above the sea the globe is seen from: true at first, then growing faster (so it shrinks to a ball). */
    static double seenFrom(double h) {
        Minecraft mc = Minecraft.getInstance();
        double far = mc.options.getEffectiveRenderDistance() * 16 * 4;
        double linear = Math.max(900, far + 100);
        return h <= linear ? h : linear * Math.exp((h - linear) / 800.0);
    }

    /** What a globe seen from above is made of: its maps, air, size and which point of it is under you. */
    record Globe(ResourceLocation map, ResourceLocation clouds, ResourceLocation lights, float air, float ar, float ag, float ab, double radius,
                 double lon, double lat, boolean airless) {}

    /** The Earth under the overworld: which part of it is under you follows where you are in the world. */
    static Globe earth(Vec3 cam) {
        double lonDeg = 10 + cam.x / (2 * Math.PI * R / 360), latDeg = Mth.clamp(38 - cam.z / (2 * Math.PI * R / 360), -80, 80);
        return new Globe(EARTH, CLOUDS, LIGHTS, 1F, 0.4F, 0.62F, 1F, R, Math.toRadians(lonDeg + 180), Math.toRadians(latDeg), false);
    }

    /** Drawn after the sky, before everything else in space. */
    public static void render(ClientLevel level, Vec3 cam, float pt) {
        render(level, cam, earth(cam), true);
    }

    static void render(ClientLevel level, Vec3 cam, Globe gl, boolean darken) {
        double y = cam.y;
        float s = gl.airless ? 1F : space(y);
        Space3D.range(5F, 80000F);
        if (s > 0.005F && darken) {
            // The sky goes black: there is no air left above to scatter the light.
            Space3D.fullScreen(0F, 0.002F, 0.01F, s * 0.97F);
            TelescopeView.brightStars(s * (0.55F + 0.45F * Space3D.clear));
            sunInSpace(s);
            double[] mc = new double[3];
            Space3D.apply(Space3D.SKYVIEW, 0, -1000, 0, mc);
            if (mc[2] < 0) {
                TelescopeView.moon(level, mc, s);
            }
        }
        double h = y - SEA;
        float g = Space3D.smooth(330F, 560F, (float) y);
        if (g <= 0.005F) {
            return;
        }
        double seen = seenFrom(h);
        double R = gl.radius;
        double lon = gl.lon, lat = gl.lat;
        // Local frame on the map (the sub-point, east, north) to the world (up, +x, -z).
        double[] sp = {Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)};
        double[] ea = {-Math.sin(lon), 0, Math.cos(lon)};
        double[] no = {-Math.sin(lat) * Math.cos(lon), Math.cos(lat), -Math.sin(lat) * Math.sin(lon)};
        // rot (map frame -> world) = W * L^T with W = [up, east, north] = [(0,1,0), (1,0,0), (0,0,-1)].
        double[] rw = new double[9];
        for (int col = 0; col < 3; col++) {
            // Column `col` of L^T is row `col` of L: the col-th components of sp, ea, no.
            double a = sp[col], b = ea[col], c = no[col];
            rw[col * 3] = b;
            rw[col * 3 + 1] = a;
            rw[col * 3 + 2] = -c;
        }
        double[] rot = new double[9];
        Space3D.mul(Space3D.VIEW, rw, rot);
        double[] c = new double[3];
        Space3D.apply(Space3D.VIEW, 0, -(R + seen), 0, c);
        Space3D.clearDepth();
        Space3D.range((float) Math.max(2, seen * 0.4), (float) (seen + R * 2.2));
        double[] sun = Space3D.SUN;
        Space3D.sphere(gl.map, c[0], c[1], c[2], R, rot, sun[0], sun[1], sun[2], 0.01F, 1F, 1F, 1F, 0F, g, false, 0F, 120);
        if (gl.lights != null) {
            Space3D.sphereMode(ADD);
            Space3D.nightSide(true);
            Space3D.sphere(gl.lights, c[0], c[1], c[2], R + 1, rot, sun[0], sun[1], sun[2], 0F, 1F, 0.82F, 0.55F, 0F, 0.6F * g, false, 0F, 120);
            Space3D.nightSide(false);
            Space3D.sphereMode(Space3D.NORMAL);
        }
        if (gl.clouds != null) {
            float drift = (float) (Space3D.time * 0.000004 % 1.0);
            Space3D.sphere(gl.clouds, c[0], c[1], c[2], R + 14, rot, sun[0], sun[1], sun[2], 0.008F, 1F, 1F, 1F, 0F, 0.92F * g, false, drift, 120);
        }
        if (gl.lights != null) {
            aurora(c, R, rot, g);
        }
        if (gl.air > 0.01F) {
            // Its air, lit by the sun: scattered blue light round its edge and over its day side, red at the terminator.
            double[] sw = new double[3];
            sunWorld(sw);
            if (!SpaceShaders.atmosphere(Atmo.globe(gl.ar, gl.ag, gl.ab, gl.air, g), new double[]{0, (R + seen) / R, 0}, sw)) {
                Space3D.limb(c[0], c[1], c[2], R, 320, sun[0], sun[1], sun[2], gl.ar, gl.ag, gl.ab, 0.75F * g);
            }
        }
        // Passing through the ozone layer: a faint glow you go through.
        float ozone = gl.lights == null ? 0F : 1F - Mth.clamp((float) Math.abs(y - OZONE) / 110F, 0F, 1F);
        if (ozone > 0.01F) {
            Space3D.range(1F, 20000F);
            double dy = OZONE - y;
            double[] p = new double[3];
            Space3D.use(Space3D.WHITE, ADD, false, false);
            double e = 4000;
            double[][] corners = {{-e, dy, -e}, {e, dy, -e}, {e, dy, e}, {-e, dy, e}};
            for (double[] k : corners) {
                Space3D.apply(Space3D.VIEW, k[0], k[1], k[2], p);
                Space3D.vtx(p[0], p[1], p[2], 0.5F, 0.5F, 0.35F, 0.5F, 1F, 0.09F * ozone);
            }
        }
    }

    /** The sun's direction in the world (the way vanilla turns it). */
    static void sunWorld(double[] out) {
        // VIEW is world -> view; its transpose takes the view-space sun back to the world.
        double[] v = Space3D.VIEW, s = Space3D.SUN;
        out[0] = v[0] * s[0] + v[1] * s[1] + v[2] * s[2];
        out[1] = v[3] * s[0] + v[4] * s[1] + v[5] * s[2];
        out[2] = v[6] * s[0] + v[7] * s[1] + v[8] * s[2];
    }

    /** The Sun out in space: a white disc, its glare, and its corona's pearly streamers. */
    static void sunInSpace(float s) {
        double[] sun = Space3D.SUN;
        double sd = 3000;
        double x = sun[0] * sd, y = sun[1] * sd, z = sun[2] * sd;
        Space3D.billboard(Space3D.GLOW, ADD, x, y, z, sd * 0.1, 0F, 1F, 0.96F, 0.9F, 0.7F * s);
        Space3D.billboard(Space3D.STAR, ADD, x, y, z, sd * 0.35, 0F, 1F, 0.97F, 0.92F, 0.55F * s);
        TelescopeView.corona(x, y, z, sd * 0.012, 1000, 0.5F * s);
        Space3D.billboard(Space3D.GLOW, ADD, x, y, z, sd * 0.03, 0F, 1F, 1F, 1F, s);
    }

    /** The auroras: ovals of green light round both magnetic poles on the night side, rippling. */
    private static void aurora(double[] c, double R, double[] rot, float g) {
        Space3D.use(Space3D.GLOW, ADD, true, false);
        double t = Space3D.time;
        for (int pole = -1; pole <= 1; pole += 2) {
            for (int i = 0; i < 90; i++) {
                double a0 = Math.PI * 2 * i / 90, a1 = Math.PI * 2 * (i + 1) / 90;
                double lat = Math.toRadians(68 + 3 * Math.sin(a0 * 3 + t * 0.002)) * pole;
                double flick = 0.5 + 0.5 * Math.sin(a0 * 7 + t * 0.05);
                double rIn = R * 1.002, rOut = R * 1.02;
                double[][] v = new double[4][3];
                double[] as = {a0, a1, a1, a0}, rs = {rIn, rIn, rOut, rOut};
                for (int k = 0; k < 4; k++) {
                    Space3D.apply(rot, Math.cos(lat) * Math.cos(as[k]) * rs[k], Math.sin(lat) * rs[k], Math.cos(lat) * Math.sin(as[k]) * rs[k], v[k]);
                }
                // Only on the night side.
                double nx = v[0][0] - c[0], ny = v[0][1] - c[1], nz = v[0][2] - c[2];
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                float dark = 1F - Space3D.smooth(-0.15F, 0.1F, (float) ((nx * Space3D.SUN[0] + ny * Space3D.SUN[1] + nz * Space3D.SUN[2]) / nl));
                float a = (float) (0.22 * flick) * dark * g;
                if (a < 0.01F) {
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    boolean top = k >= 2;
                    Space3D.vtx(c[0] + v[k][0], c[1] + v[k][1], c[2] + v[k][2], 0.5F, top ? 0.95F : 0.5F, top ? 0.7F : 0.3F, 1F, top ? 0.8F : 0.55F,
                            top ? 0F : a);
                }
            }
        }
    }

    /** Messages as you leave the ozone layer and come back. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            lastY = Double.NaN;
            return;
        }
        double y = mc.player.getY();
        if (!Double.isNaN(lastY)) {
            if (lastY < OZONE && y >= OZONE) {
                mc.player.displayClientMessage(Component.translatable("realweather.space.left_ozone"), true);
            } else if (lastY >= OZONE && y < OZONE) {
                mc.player.displayClientMessage(Component.translatable("realweather.space.back_in_air"), true);
            } else if (lastY < 1400 && y >= 1400) {
                mc.player.displayClientMessage(Component.translatable("realweather.space.earth_below"), true);
            }
        }
        lastY = y;
    }

    /** Out in space the far distance fades to the haze over the globe, not to a blue sky. */
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null || lv.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        double y = event.getCamera().getPosition().y;
        float s = space(y);
        if (s <= 0.001F) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        float sunY = level == null ? 1F : Mth.cos(level.getTimeOfDay((float) event.getPartialTick()) * Mth.TWO_PI);
        float day = Mth.clamp(Space3D.smooth(-0.15F, 0.25F, sunY), 0.05F, 1F);
        event.setRed(Mth.lerp(s, event.getRed(), 0.3F * day));
        event.setGreen(Mth.lerp(s, event.getGreen(), 0.38F * day));
        event.setBlue(Mth.lerp(s, event.getBlue(), 0.5F * day));
    }
}
