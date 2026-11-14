package com.realweather.client.space;

import com.realweather.RealWeather;
import com.realweather.space.Worlds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

import static com.realweather.client.space.Space3D.ADD;

/**
 * The sky of another world in the planets dimension: its own star (its colour and size as seen from there), the
 * stars, the planet a moon goes round hanging huge in the sky with its phases, the world's air scattering the light
 * (blue, butterscotch, orange haze, or nothing but black on airless worlds), and from high above, the whole world
 * below as a globe.
 */
public final class PlanetSky {
    private static boolean failed;

    private PlanetSky() {}

    /** The dimension's sky type: none of vanilla's (all drawn here). */
    public static final class Effects extends DimensionSpecialEffects {
        public Effects() {
            super(Float.NaN, true, SkyType.NONE, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor;
        }

        @Override
        public boolean isFoggyAt(int x, int z) {
            return false;
        }

        @Override
        public boolean renderClouds(ClientLevel level, int ticks, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack, double camX,
                                    double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
            return true;
        }

        @Override
        public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY,
                                         double camZ) {
            return true;
        }

        @Override
        public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
            return true;
        }
    }

    static boolean here(ClientLevel level) {
        return level != null && level.dimension() == Worlds.DIMENSION;
    }

    /** How high the star is over this world's horizon (-1..1). */
    private static float sunUp(ClientLevel level, float pt) {
        return (float) Math.cos(Space3D.timeOfDay(level, pt) * Math.PI * 2);
    }

    /** The far distance fades to the colour of this world's sky at the horizon (black where there is no air). */
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!here(level)) {
            return;
        }
        Worlds.World w = Worlds.at(event.getCamera().getPosition().x);
        if (w == null) {
            // Out in space: nothing but black.
            event.setRed(0F);
            event.setGreen(0F);
            event.setBlue(0.003F);
            return;
        }
        float day = Space3D.smooth(-0.2F, 0.3F, sunUp(level, (float) event.getPartialTick()));
        float k = w.air() * day * 0.75F;
        if (w.gas()) {
            // Down in a gas giant's clouds: its colour, darker the deeper, and the red glow of the heat far below.
            float t = gasDepth(event.getCamera().getPosition().y);
            float lit = (0.25F + 0.75F * day) * (1F - t * 0.85F);
            event.setRed(w.ar() * lit * 0.8F + 0.55F * t * t);
            event.setGreen(w.ag() * lit * 0.75F + 0.14F * t * t);
            event.setBlue(w.ab() * lit * 0.7F + 0.02F * t * t);
            return;
        }
        event.setRed(0.01F + w.ar() * k);
        event.setGreen(0.012F + w.ag() * k);
        event.setBlue(0.02F + w.ab() * k);
    }

    /** How deep into a gas giant (0 at its cloud tops, 1 near the core). */
    private static float gasDepth(double y) {
        return Mth.clamp((float) ((com.realweather.space.PlanetTravel.GAS_TOP - y) / (com.realweather.space.PlanetTravel.GAS_TOP - 40)), 0F, 1F);
    }

    /** In a gas giant you can see only a little way through its clouds, less and less the deeper you go. */
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!here(level)) {
            return;
        }
        Worlds.World w = Worlds.at(event.getCamera().getPosition().x);
        if (w == null || !w.gas()) {
            return;
        }
        float t = gasDepth(event.getCamera().getPosition().y);
        float far = Mth.lerp(t, 180F, 14F);
        event.setNearPlaneDistance(far * 0.02F);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!here(level)) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Worlds.World w = Worlds.at(cam.x);
        if (w == null) {
            // Out in space: beside a star, pulsar, black hole, nebula or comet, or between the planets.
            try {
                Space3D.begin(event, level, pt);
                try {
                    SpaceFlight.render(event, level, pt);
                } finally {
                    Space3D.end();
                }
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                failed = true;
                RealWeather.LOG.error("Space disabled after an error", e);
            }
            return;
        }
        Catalog.Target t = Catalog.byId(w.id());
        try {
            Space3D.begin(event, level, pt);
            try {
                draw(level, cam, w, t, pt);
                if (Telescope.active()) {
                    TelescopeView.render(event, level, pt);
                }
            } finally {
                Space3D.end();
            }
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            failed = true;
            RealWeather.LOG.error("The skies of other worlds disabled after an error", e);
        }
    }

    private static void draw(ClientLevel level, Vec3 cam, Worlds.World w, Catalog.Target t, float pt) {
        double[] sun = Space3D.SUN;
        float[] star = t == null ? new float[]{1F, 0.95F, 0.86F, 0.0093F} : Catalog.hostStar(t);
        double[] sw = new double[3];
        EarthView.sunWorld(sw);
        float up = (float) sw[1];
        float day = Space3D.smooth(-0.2F, 0.3F, up);
        Space3D.range(5F, 80000F);
        Space3D.fullScreen(0.004F, 0.005F, 0.01F, 1F);
        // Inside a gas giant's clouds: nothing but its haze round you.
        float inGas = w.gas() ? Space3D.smooth((float) (com.realweather.space.PlanetTravel.GAS_TOP + 40), (float) (com.realweather.space.PlanetTravel.GAS_TOP - 30),
                (float) cam.y) : 0F;
        // Stars (washed out by a bright sky).
        TelescopeView.brightStars((1F - day * Math.min(1F, w.air() * 1.4F)) * (1F - inGas));
        // Its star.
        double sd = 3000, sr = star[3];
        double sx = sun[0] * sd, sy = sun[1] * sd, sz = sun[2] * sd;
        Space3D.billboard(Space3D.GLOW, ADD, sx, sy, sz, sd * sr * 9, 0F, star[0], star[1], star[2], 0.6F);
        Space3D.billboard(Space3D.STAR, ADD, sx, sy, sz, sd * sr * 16, 0F, star[0], star[1], star[2], 0.5F);
        if (w.airless()) {
            TelescopeView.corona(sx, sy, sz, sd * sr, w.id().hashCode(), 0.45F);
        }
        Space3D.billboard(Space3D.GLOW, ADD, sx, sy, sz, sd * sr * 2.2, 0F, 1F, 1F, 1F, 1F);
        // The other worlds of the solar system where they really are (the planet a moon goes round filling part of the
        // sky in its phases); round another star, the planet a moon goes round.
        if (!solarSky(level, w, pt) && w.parent() != null) {
            parent(w);
        }
        if ("io".equals(w.id())) {
            ioPlumes(cam);
        }
        // The air.
        if (!w.airless()) {
            // (From high up, seen from as far as the globe below is.)
            double alt = EarthView.seenFrom(cam.y - 63) / Worlds.RADIUS;
            SpaceShaders.atmosphere(Atmo.sky(w.ar(), w.ag(), w.ab(), w.air(), star[0], star[1], star[2]), new double[]{0, 1 + Math.max(0, alt), 0}, sw);
        }
        if (inGas > 0.01F) {
            float t = gasDepth(cam.y);
            float lit = (0.25F + 0.75F * day) * (1F - t * 0.85F);
            Space3D.clearDepth();
            Space3D.range(5F, 80000F);
            Space3D.fullScreen(Math.min(1F, w.ar() * lit * 0.8F + 0.55F * t * t), w.ag() * lit * 0.75F + 0.14F * t * t, w.ab() * lit * 0.7F + 0.02F * t * t,
                    0.35F + 0.62F * inGas);
            clouds(level, cam, w, pt);
        }
        // From high above: the whole world below.
        if (cam.y > 330) {
            double[] uv = Worlds.mapUV(cam.x, cam.z);
            ResourceLocation map = RealWeather.id("textures/environment/space3d/" + w.map() + ".png");
            EarthView.render(level, cam, new EarthView.Globe(map, null, null, 0F, w.ar(), w.ag(), w.ab(), Worlds.RADIUS, uv[0] * Math.PI * 2,
                    (0.5 - uv[1]) * Math.PI, w.airless()), false);
        }
    }

    /**
     * The sky of a world of the solar system: every other planet, moon and comet where it really is from here (as
     * the sky turns with the time of day), a point of light or a disc with its phase, clouds, rings and moons.
     */
    private static boolean solarSky(ClientLevel level, Worlds.World w, float pt) {
        SolarSystem.Body me = SolarSystem.byId(w.id());
        if (me == null) {
            return false;
        }
        double day = (level.getDayTime() + pt) / 24000.0;
        double th = Space3D.timeOfDay(level, pt) * Math.PI * 2;
        double[] fr = SolarSystem.frame(me, day, th);
        double[] ft = {fr[0], fr[3], fr[6], fr[1], fr[4], fr[7], fr[2], fr[5], fr[8]};
        double[] m = new double[9];
        Space3D.mul(Space3D.VIEW, ft, m);
        double[] mp = new double[3], bp = new double[3], v = new double[3], sun = new double[3];
        SolarSystem.position(me, day, mp);
        int h = Math.max(1, Minecraft.getInstance().getWindow().getHeight());
        double pixAng = 2 / (Space3D.projY() * h);
        float daySky = Space3D.smooth(-0.2F, 0.3F, (float) Space3D.SKY[4]) * Math.min(1F, w.air() * 1.4F);
        for (SolarSystem.Body b : SolarSystem.ALL) {
            if (b == me || b.kind() == SolarSystem.SUN) {
                continue;
            }
            SolarSystem.position(b, day, bp);
            double rx = bp[0] - mp[0], ry = bp[1] - mp[1], rz = bp[2] - mp[2];
            double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
            Space3D.apply(m, rx / dist, ry / dist, rz / dist, v);
            double ang = b.radius() / dist;
            if (v[2] > 0.2 && ang < 0.2) {
                continue;
            }
            double sl = Math.sqrt(bp[0] * bp[0] + bp[1] * bp[1] + bp[2] * bp[2]) + 1.0E-9;
            Space3D.apply(m, -bp[0] / sl, -bp[1] / sl, -bp[2] / sl, sun);
            double d = 5000;
            if (b.kind() == SolarSystem.COMET) {
                double fromSun = sl / SolarSystem.AU;
                double tail = 2.5e7 / Math.pow(Math.max(0.4, fromSun), 1.5), coma = 1.2e5 / Math.max(0.5, fromSun);
                double[] vel = new double[3], vv = new double[3];
                SolarSystem.velocity(b, day, vel);
                Space3D.apply(m, vel[0], vel[1], vel[2], vv);
                double k = d / dist;
                Space3D.clearDepth();
                Space3D.range((float) Math.max(5, d - tail * k * 1.3), (float) (d + tail * k * 1.3 + 10));
                Comets.draw(v[0] * d, v[1] * d, v[2] * d, coma * k, sun, vv, tail * k, b.id().hashCode(), Space3D.time / 20.0, pixAng * d,
                        (1F - daySky) * 0.9F);
                continue;
            }
            double sizePx = ang / pixAng;
            if (sizePx < 1.5) {
                double lum = ang * ang * 1.0E13 / Math.max(0.05, Math.pow(sl / SolarSystem.AU, 2));
                float bright = (float) Mth.clamp(0.2 + 0.11 * Math.log10(lum + 1), 0.08, 1) * (1F - daySky * 0.85F);
                Space3D.billboard(Space3D.GLOW, ADD, v[0] * d, v[1] * d, v[2] * d, pixAng * d * (1.5 + 2.5 * bright), 0F, 1F, 0.95F, 0.85F, bright);
                continue;
            }
            Catalog.Target bt = Catalog.byId(b.id());
            if (bt == null) {
                continue;
            }
            double r = d * Math.tan(ang);
            double[] c = {v[0] * d, v[1] * d, v[2] * d};
            Space3D.clearDepth();
            Space3D.range((float) (d - r * 2.6), (float) (d + r * 2.6));
            double[] axis = new double[3];
            Space3D.apply(m, Math.sin(0.05), Math.cos(0.05), 0, axis);
            double[] rot = Space3D.orient(axis[0], axis[1], axis[2], 0);
            CloseViews.planet(bt.body, c, r, rot, sun, 1F, 0.97F, 0.92F, Space3D.time / 20.0, r / sizePx, 1F, (float) (day / 0.5 % 1.0));
        }
        Space3D.clearDepth();
        Space3D.range(5F, 80000F);
        return true;
    }

    /** On Io: its volcanoes' great plumes rising over the horizon. */
    private static void ioPlumes(Vec3 cam) {
        double[] up = new double[3], e1 = new double[3], e2 = new double[3], b = new double[3];
        Space3D.apply(Space3D.VIEW, 0, 1, 0, up);
        Space3D.apply(Space3D.VIEW, 1, 0, 0, e1);
        Space3D.apply(Space3D.VIEW, 0, 0, 1, e2);
        double[][] at = {{1400, 600, 260}, {-900, -1500, 180}};
        Space3D.clearDepth();
        Space3D.range(5F, 6000F);
        for (int i = 0; i < at.length; i++) {
            Space3D.apply(Space3D.VIEW, at[i][0], 63 - cam.y, at[i][1], b);
            Plumes.plume(b[0], b[1], b[2], up, e1, e2, at[i][2], Plumes.UMBRELLA, 777 + i, Space3D.time / 20.0, 1.5, 1F, 1F, 6);
        }
        Space3D.clearDepth();
        Space3D.range(5F, 80000F);
    }

    /** Sinking through a gas giant: banks of its clouds drifting past (they rise past you as you sink). */
    private static void clouds(ClientLevel level, Vec3 cam, Worlds.World w, float pt) {
        float t = gasDepth(cam.y);
        double cell = 26, drift = Space3D.time * 0.04;
        int cx = (int) Math.floor((cam.x - drift) / cell), cy = (int) Math.floor(cam.y / cell), cz = (int) Math.floor(cam.z / cell);
        double[] q = new double[3];
        Space3D.clearDepth();
        Space3D.range(0.5F, 400F);
        float lit = (1F - t * 0.8F);
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                for (int k = -3; k <= 3; k++) {
                    long key = ((long) (cx + i) * 73856093L) ^ ((long) (cy + j) * 19349663L) ^ ((long) (cz + k) * 83492791L);
                    if (Space3D.h(key, 1) > 0.4F) {
                        continue;
                    }
                    double x = (cx + i + Space3D.h(key, 2)) * cell + drift - cam.x, y = (cy + j + Space3D.h(key, 3)) * cell - cam.y,
                            z = (cz + k + Space3D.h(key, 4)) * cell - cam.z;
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (dist > cell * 3.2) {
                        continue;
                    }
                    Space3D.apply(Space3D.VIEW, x, y, z, q);
                    float a = (float) (0.5 * (1 - dist / (cell * 3.2)));
                    float shade = 0.55F + 0.75F * Space3D.h(key, 5);
                    Space3D.billboard(Space3D.GLOW, Space3D.NORMAL, q[0], q[1], q[2], cell * (0.6 + 0.6 * Space3D.h(key, 6)), Space3D.h(key, 7) * 6F,
                            Math.min(1F, w.ar() * lit * shade + 0.5F * t * t), w.ag() * lit * shade + 0.1F * t * t, w.ab() * lit * shade, a);
                }
            }
        }
        Space3D.clearDepth();
        Space3D.range(5F, 80000F);
    }

    /** Angular radius (radians) of the planet in a moon's sky. */
    private static double parentSize(String id) {
        return switch (id) {
            case "io" -> 0.17;
            case "europa" -> 0.1;
            case "ganymede" -> 0.06;
            case "callisto" -> 0.035;
            case "titan" -> 0.05;
            case "charon" -> 0.065;
            case "moon" -> 0.017;
            default -> 0.03;
        };
    }

    private static void parent(Worlds.World w) {
        Catalog.Target pt = Catalog.byId(w.parent());
        if (pt == null) {
            return;
        }
        Catalog.Body b = pt.body;
        double size = parentSize(w.id());
        // Fixed in its sky (moons keep one face to their planet), high in the south-west.
        double[] dir = {-0.35, 0.62, 0.7};
        double l = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        double d = 5000;
        double[] c = new double[3];
        Space3D.apply(Space3D.VIEW, dir[0] / l * d, dir[1] / l * d, dir[2] / l * d, c);
        if (c[2] > 0) {
            return;
        }
        double r = d * Math.tan(size);
        Space3D.clearDepth();
        Space3D.range((float) (d - r * 1.5), (float) (d + r * 1.5));
        double[] axis = new double[3];
        Space3D.apply(Space3D.VIEW, 0.1, 1, 0.2, axis);
        double[] rot = Space3D.orient(axis[0], axis[1], axis[2], 0);
        Space3D.scale(1, b.oblate, 1);
        Space3D.sphere(b.map, c[0], c[1], c[2], r, rot, Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], 0.01F, b.r, b.g, b.b, b.glow, 1F, false,
                (float) (Space3D.time / 24000.0 % 1.0), 60);
        Space3D.scale(1, 1, 1);
        if (b.rings) {
            Space3D.ring(RealWeather.id("textures/environment/space3d/rings.png"), c[0], c[1], c[2], r * 1.25, r * 2.3, rot, Space3D.SUN[0],
                    Space3D.SUN[1], Space3D.SUN[2], 0.9F);
        }
        if (b.air) {
            Space3D.atmosphere(c[0], c[1], c[2], r, Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], b.ar, b.ag, b.ab, 0.5F);
        }
        Space3D.clearDepth();
        Space3D.range(5F, 80000F);
    }

    static float mix(float a, float b, float t) {
        return Mth.lerp(t, a, b);
    }
}
