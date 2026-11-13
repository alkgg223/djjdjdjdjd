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
        float day = Space3D.smooth(-0.2F, 0.3F, sunUp(level, (float) event.getPartialTick()));
        float k = w.air() * day * 0.75F;
        event.setRed(0.01F + w.ar() * k);
        event.setGreen(0.012F + w.ag() * k);
        event.setBlue(0.02F + w.ab() * k);
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
        Catalog.Target t = Catalog.byId(w.id());
        try {
            Space3D.begin(event, level, pt);
            try {
                draw(level, cam, w, t);
                if (Telescope.active()) {
                    TelescopeView.render(event, level, pt);
                }
            } finally {
                Space3D.end();
            }
        } catch (RuntimeException e) {
            failed = true;
            RealWeather.LOG.error("The skies of other worlds disabled after an error", e);
        }
    }

    private static void draw(ClientLevel level, Vec3 cam, Worlds.World w, Catalog.Target t) {
        double[] sun = Space3D.SUN;
        float[] star = t == null ? new float[]{1F, 0.95F, 0.86F, 0.0093F} : Catalog.hostStar(t);
        double[] sw = new double[3];
        EarthView.sunWorld(sw);
        float up = (float) sw[1];
        float day = Space3D.smooth(-0.2F, 0.3F, up);
        Space3D.range(5F, 80000F);
        Space3D.fullScreen(0.004F, 0.005F, 0.01F, 1F);
        // Stars (washed out by a bright sky).
        TelescopeView.brightStars(1F - day * Math.min(1F, w.air() * 1.4F));
        // Its star.
        double sd = 3000, sr = star[3];
        double sx = sun[0] * sd, sy = sun[1] * sd, sz = sun[2] * sd;
        Space3D.billboard(Space3D.GLOW, ADD, sx, sy, sz, sd * sr * 9, 0F, star[0], star[1], star[2], 0.6F);
        Space3D.billboard(Space3D.STAR, ADD, sx, sy, sz, sd * sr * 16, 0F, star[0], star[1], star[2], 0.5F);
        if (w.airless()) {
            TelescopeView.corona(sx, sy, sz, sd * sr, w.id().hashCode(), 0.45F);
        }
        Space3D.billboard(Space3D.GLOW, ADD, sx, sy, sz, sd * sr * 2.2, 0F, 1F, 1F, 1F, 1F);
        // The planet this moon goes round, filling a part of the sky, in its phases.
        if (w.parent() != null) {
            parent(w);
        }
        // The air.
        if (!w.airless()) {
            // (From high up, seen from as far as the globe below is.)
            double alt = EarthView.seenFrom(cam.y - 63) / Worlds.RADIUS;
            SpaceShaders.atmosphere(Atmo.sky(w.ar(), w.ag(), w.ab(), w.air(), star[0], star[1], star[2]), new double[]{0, 1 + Math.max(0, alt), 0}, sw);
        }
        // From high above: the whole world below.
        if (cam.y > 330) {
            double[] uv = Worlds.mapUV(cam.x, cam.z);
            ResourceLocation map = RealWeather.id("textures/environment/space3d/" + w.map() + ".png");
            EarthView.render(level, cam, new EarthView.Globe(map, null, null, 0F, w.ar(), w.ag(), w.ab(), Worlds.RADIUS, uv[0] * Math.PI * 2,
                    (0.5 - uv[1]) * Math.PI, w.airless()), false);
        }
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
