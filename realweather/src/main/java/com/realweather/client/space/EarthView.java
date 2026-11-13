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
    private static double seenFrom(double h) {
        Minecraft mc = Minecraft.getInstance();
        double far = mc.options.getEffectiveRenderDistance() * 16 * 4;
        double linear = Math.max(900, far + 100);
        return h <= linear ? h : linear * Math.exp((h - linear) / 500.0);
    }

    /** Drawn after the sky, before everything else in space. */
    public static void render(ClientLevel level, Vec3 cam, float pt) {
        double y = cam.y;
        float s = space(y);
        Space3D.range(5F, 80000F);
        if (s > 0.005F) {
            // The sky goes black: there is no air left above to scatter the light.
            Space3D.fullScreen(0F, 0.002F, 0.01F, s * 0.97F);
            TelescopeView.brightStars(s * (0.55F + 0.45F * Space3D.clear));
            // The Sun, white and sharp out here.
            double[] sun = Space3D.SUN;
            double sd = 3000;
            Space3D.billboard(Space3D.GLOW, ADD, sun[0] * sd, sun[1] * sd, sun[2] * sd, sd * 0.1, 0F, 1F, 0.96F, 0.9F, 0.7F * s);
            Space3D.billboard(Space3D.STAR, ADD, sun[0] * sd, sun[1] * sd, sun[2] * sd, sd * 0.35, 0F, 1F, 0.97F, 0.92F, 0.55F * s);
            Space3D.billboard(Space3D.GLOW, ADD, sun[0] * sd, sun[1] * sd, sun[2] * sd, sd * 0.03, 0F, 1F, 1F, 1F, s);
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
        // Which part of the Earth is under you follows where you are in the world.
        double lonDeg = 10 + cam.x / (2 * Math.PI * R / 360), latDeg = Mth.clamp(38 - cam.z / (2 * Math.PI * R / 360), -80, 80);
        double lon = Math.toRadians(lonDeg + 180), lat = Math.toRadians(latDeg);
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
        Space3D.sphere(EARTH, c[0], c[1], c[2], R, rot, sun[0], sun[1], sun[2], 0.01F, 1F, 1F, 1F, 0F, g, false, 0F, 120);
        Space3D.sphereMode(ADD);
        Space3D.nightSide(true);
        Space3D.sphere(LIGHTS, c[0], c[1], c[2], R + 1, rot, sun[0], sun[1], sun[2], 0F, 1F, 0.82F, 0.55F, 0F, 0.85F * g, false, 0F, 120);
        Space3D.nightSide(false);
        Space3D.sphereMode(Space3D.NORMAL);
        float drift = (float) (Space3D.time * 0.000004 % 1.0);
        Space3D.sphere(CLOUDS, c[0], c[1], c[2], R + 14, rot, sun[0], sun[1], sun[2], 0.008F, 1F, 1F, 1F, 0F, 0.92F * g, false, drift, 120);
        // The thin blue line of the air round its edge.
        Space3D.limb(c[0], c[1], c[2], R, 320, sun[0], sun[1], sun[2], 0.4F, 0.62F, 1F, 0.75F * g);
        // Passing through the ozone layer: a faint glow you go through.
        float ozone = 1F - Mth.clamp((float) Math.abs(y - OZONE) / 110F, 0F, 1F);
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

    /** Messages as you leave the ozone layer and come back. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) {
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
