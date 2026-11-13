package com.realweather.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.realweather.RealWeather;
import com.realweather.client.ClientWeather;
import com.realweather.client.space.Space3D;
import com.realweather.server.SkyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL14;

import static com.realweather.client.space.Space3D.ADD;
import static com.realweather.client.space.Space3D.NORMAL;

/**
 * The sky events as real 3D objects out in space, drawn after the sky (so the clouds and the land are in front of
 * them) but separate from it: lit planets and moons with day and night sides, comets with tails of streaming dust,
 * meteors and fireballs burning through the air along real paths, the space station and satellites as lit models
 * flashing in the sun, curtains of aurora hanging high over the land, and stars blazing in fire. Things fixed among the
 * stars turn with the sky; the rest is placed in the local sky.
 */
public final class SpaceRenderer {
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation MOON_MAP = map("moon"), JUPITER = map("jupiter"), SATURN = map("saturn"), MARS = map("mars"),
            VENUS = map("venus"), MERCURY = map("mercury"), URANUS = map("uranus"), NEPTUNE = map("neptune"), RINGS = map("rings"),
            STAR_MAP = map("star_surface"), PANEL = map("panel"), LINE = map("line"), TOI700E = map("toi700e");
    private static final ResourceLocation METEOR = tex("meteor"), FIREBALL = tex("fireball"), AURORA = tex("aurora"), MILKY_WAY = tex("milky_way"),
            SPRITE = tex("red_sprite");
    private static final ResourceLocation GLOW = Space3D.GLOW, STAR = Space3D.STAR, FLAME = Space3D.FLAME, WHITE = Space3D.WHITE;
    /** Distances (blocks) and the vanilla moon's and sun's angular radii (radians). */
    private static final double MOON_D = 2500, SUN_D = 3000, PLANET_D = 9000, COMET_D = 14000, STAR_D = 30000, MILKY_D = 50000;
    private static final double MOON_A = Math.atan(0.1), SUN_A = Math.atan(0.15);
    private static final float MOON_Q = 20F;

    private static boolean failed;
    private static double t;
    private static float night, clear, sunY;
    private static final double[] V = new double[3], W = new double[3];

    private SpaceRenderer() {}

    static ResourceLocation map(String name) {
        return RealWeather.id("textures/environment/space3d/" + name + ".png");
    }

    private static ResourceLocation tex(String name) {
        return RealWeather.id("textures/environment/space/" + name + ".png");
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        boolean telescope = com.realweather.client.space.Telescope.active();
        double camY = event.getCamera().getPosition().y;
        boolean high = com.realweather.client.space.EarthView.wanted(camY);
        if (ClientSkyEvents.isEmpty() && !telescope && !high) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!ClientWeather.active(level) || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        t = level.getGameTime() + pt;
        night = Mth.clamp(level.getStarBrightness(pt) * 2.2F, 0F, 1F);
        clear = Mth.clamp(1F - level.getRainLevel(pt), 0F, 1F);
        sunY = Mth.cos(level.getTimeOfDay(pt) * Mth.TWO_PI);
        try {
            // The events that replace the moon take vanilla's out first (in vanilla's own frame).
            float swap = Space3D.smooth(0.15F, 0.4F, night);
            if (swap > 0.01F && (ClientSkyEvents.get(SkyEvent.LUNAR_ECLIPSE, t) != null || ClientSkyEvents.get(SkyEvent.SUPERMOON, t) != null)) {
                removeVanillaMoon(event, level, pt, swap);
            }
            Space3D.begin(event, level, pt);
            try {
                if (high) {
                    com.realweather.client.space.EarthView.render(level, event.getCamera().getPosition(), pt);
                    Space3D.clearDepth();
                    Space3D.range(5F, 80000F);
                }
                if (telescope) {
                    com.realweather.client.space.TelescopeView.render(event, level, pt);
                    Space3D.range(5F, 80000F);
                }
                for (SkyEvent e : ClientSkyEvents.EVENTS) {
                    if (e.active(t)) {
                        draw(e, event);
                    }
                }
            } finally {
                Space3D.end();
            }
        } catch (RuntimeException e) {
            failed = true;
            RealWeather.LOG.error("Sky events disabled after an error", e);
        }
    }

    private static void draw(SkyEvent e, RenderLevelStageEvent event) {
        float p = (float) e.progress(t);
        float edge = Mth.clamp(Math.min(p, 1F - p) * 40F, 0F, 1F);
        switch (e.type) {
            case SkyEvent.LUNAR_ECLIPSE -> lunarEclipse(e, p);
            case SkyEvent.SUPERMOON -> supermoon(edge);
            case SkyEvent.METEOR_SHOWER -> meteors(e, p, false);
            case SkyEvent.SPORADIC_METEORS -> meteors(e, p, true);
            case SkyEvent.COMET -> comet(e, edge, false);
            case SkyEvent.FAINT_COMET -> comet(e, edge, true);
            case SkyEvent.WANDERING_COMET -> wanderingComet(e, p, edge);
            case SkyEvent.FIREBALLS, SkyEvent.DEBRIS_REENTRY -> fireballs(e);
            case SkyEvent.CONJUNCTION -> conjunction(e, p, edge, false);
            case SkyEvent.TRIPLE_CONJUNCTION -> conjunction(e, p, edge, true);
            case SkyEvent.ALIGNMENT -> alignment(e, edge);
            case SkyEvent.TRANSIT -> transit(e, p);
            case SkyEvent.SATELLITE_TRANSIT -> satelliteTransit(e);
            case SkyEvent.OCCULTATION -> occultation(e, p, false);
            case SkyEvent.STAR_OCCULTATION -> occultation(e, p, true);
            case SkyEvent.AURORA -> aurora(e, edge, false);
            case SkyEvent.AURORA_STORM -> aurora(e, edge, true);
            case SkyEvent.ISS -> iss(e, p);
            case SkyEvent.STARLINK -> starlink(e, p);
            case SkyEvent.SATELLITE_FLARE -> satelliteFlare(e, p);
            case SkyEvent.SUPERNOVA -> supernova(e, p);
            case SkyEvent.RECURRENT_NOVA -> recurrentNova(e, edge);
            case SkyEvent.STAR_FLARES -> starFlares(e, edge);
            case SkyEvent.CORONAL_MASS_EJECTION -> coronalMassEjection(e, edge);
            case SkyEvent.UPPER_LIGHTNING -> upperLightning(event.getCamera().getPosition());
            case SkyEvent.DUST_GLOW -> dustGlow(e, edge);
            default -> {
            }
        }
    }

    // ================================================================================== positions

    /** A point in the sky frame (turning with the stars), in view space. */
    private static double[] sky(double x, double y, double z) {
        Space3D.apply(Space3D.SKYVIEW, x, y, z, V);
        return V;
    }

    /** A point in the local sky (camera-relative world), in view space. */
    private static double[] world(double x, double y, double z) {
        Space3D.apply(Space3D.VIEW, x, y, z, V);
        return V;
    }

    private static float[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z) + 1.0E-12;
        return new float[]{(float) (x / l), (float) (y / l), (float) (z / l)};
    }

    /** A direction in the sky frame round the point opposite the sun (seen at night), at least 14 degrees off the moon. */
    private static float[] nightDir(long seed, int salt, float sx, float sz) {
        float x = (h(seed, salt) - 0.5F) * 2F * sx, z = (h(seed, salt + 1) - 0.5F) * 2F * sz;
        float off = Mth.sqrt(x * x + z * z);
        if (off < 0.25F) {
            float k = off < 1.0E-3F ? 0F : 0.25F / off;
            x = off < 1.0E-3F ? 0.25F : x * k;
            z *= k;
        }
        return norm(x, -1F, z);
    }

    /** A direction in the local sky: azimuth from north towards east, elevation, radians. */
    private static float[] local(float az, float el) {
        float c = Mth.cos(el);
        return new float[]{c * Mth.sin(az), Mth.sin(el), -c * Mth.cos(az)};
    }

    static float h(long seed, long k) {
        return Space3D.h(seed, k);
    }

    private static float smooth(float e0, float e1, float x) {
        return Space3D.smooth(e0, e1, x);
    }

    /** A body's rotation (its axis tilted a little from the sky frame's X) in view space, spinning with time. */
    private static double[] bodyRot(boolean skyFrame, double ax, double ay, double az, double spin) {
        double[] a = new double[3];
        Space3D.apply(skyFrame ? Space3D.SKYVIEW : Space3D.VIEW, ax, ay, az, a);
        return Space3D.orient(a[0], a[1], a[2], spin);
    }

    // ================================================================================== the moon

    /** Takes the vanilla moon (drawn additively just before) out of the sky again, exactly, `amount` of it. */
    private static void removeVanillaMoon(RenderLevelStageEvent event, ClientLevel level, float pt, float amount) {
        int phase = level.getMoonPhase();
        float u0 = (phase % 4) / 4F, u1 = (phase % 4 + 1) / 4F, v0 = (phase / 4 % 2) / 2F, v1 = (phase / 4 % 2 + 1) / 2F;
        Matrix4f sky = new Matrix4f(event.getModelViewMatrix()).rotate(Axis.YP.rotationDegrees(-90F)).rotate(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360F));
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(sky);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        GlStateManager._blendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO,
                GlStateManager.DestFactor.ONE);
        VolumeClouds.useGlowKeepFilter(MOON);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float a = clear * amount;
        b.addVertex(-MOON_Q, -100F, MOON_Q).setUv(u1, v1).setColor(1F, 1F, 1F, a);
        b.addVertex(MOON_Q, -100F, MOON_Q).setUv(u0, v1).setColor(1F, 1F, 1F, a);
        b.addVertex(MOON_Q, -100F, -MOON_Q).setUv(u0, v0).setColor(1F, 1F, 1F, a);
        b.addVertex(-MOON_Q, -100F, -MOON_Q).setUv(u1, v0).setColor(1F, 1F, 1F, a);
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /** The moon as a lit 3D sphere where vanilla's was, each corner coloured by `shade` (sky-frame offsets, moon radii). */
    private interface Shade {
        void at(double mx, double mz, float[] out);
    }

    private static void moonSphere(double scale, Shade shade, float alpha) {
        double r = MOON_D * Math.tan(MOON_A) * scale;
        double[] c = sky(0, -MOON_D, 0).clone();
        double[] rot = bodyRot(true, 1, 0, 0, Math.PI * 0.5);
        int lat = 24, lon = 48;
        float[] col = new float[3];
        double[] n = new double[3], sk = new double[3];
        Space3D.use(MOON_MAP, NORMAL, true, true);
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                if (!Space3D.facing(rot, c[0], c[1], c[2], r, lat, lon, i, j)) {
                    continue;
                }
                for (int q = 0; q < 4; q++) {
                    int ii = i + (q == 1 || q == 2 ? 1 : 0), jj = j + (q >= 2 ? 1 : 0);
                    double th = Math.PI * ii / lat, ph = Math.PI * 2 * jj / lon;
                    double sx = Math.sin(th) * Math.cos(ph), sy = Math.cos(th), sz = Math.sin(th) * Math.sin(ph);
                    Space3D.apply(rot, sx, sy, sz, n);
                    float ndl = (float) (n[0] * Space3D.SUN[0] + n[1] * Space3D.SUN[1] + n[2] * Space3D.SUN[2]);
                    float lit = smooth(-0.05F, 0.25F, ndl) * (0.4F + 0.6F * Math.max(0F, ndl)) + 0.02F;
                    // The corner's offset from the moon's centre across the sky (sky-frame x and z), for the shadow.
                    inverse(n, sk);
                    shade.at(sk[0] * scale, sk[2] * scale, col);
                    Space3D.vtx(c[0] + n[0] * r, c[1] + n[1] * r, c[2] + n[2] * r, (float) jj / lon, (float) ii / lat, col[0] * lit, col[1] * lit,
                            col[2] * lit, alpha);
                }
            }
        }
    }

    /** View space back to the sky frame (the transpose of the rotation). */
    private static void inverse(double[] v, double[] out) {
        double[] m = Space3D.SKYVIEW;
        out[0] = m[0] * v[0] + m[1] * v[1] + m[2] * v[2];
        out[1] = m[3] * v[0] + m[4] * v[1] + m[5] * v[2];
        out[2] = m[6] * v[0] + m[7] * v[1] + m[8] * v[2];
    }

    /** Earth's shadow sweeps across the full moon: grey in the penumbra, copper red in the umbra, a blue-green rim. */
    private static void lunarEclipse(SkyEvent e, float p) {
        float swap = smooth(0.15F, 0.4F, night);
        if (swap <= 0.01F) {
            return;
        }
        float q = p * 2F - 1F;
        float along = Math.copySign((float) Math.pow(Math.abs(q), 1.35), q) * 3.9F;
        float miss = (h(e.seed, 1) - 0.5F) * 0.7F;
        float ang = h(e.seed, 2) * Mth.TWO_PI;
        float sx = along * Mth.cos(ang) - miss * Mth.sin(ang), sz = along * Mth.sin(ang) + miss * Mth.cos(ang);
        float ru = 2.65F, rp = 4.5F;
        moonSphere(1.0, (mx, mz, out) -> {
            float d = (float) Math.sqrt((mx - sx) * (mx - sx) + (mz - sz) * (mz - sz));
            float umbra = smooth(ru + 0.12F, ru - 0.12F, d);
            float pen = smooth(rp, ru, d);
            float lit = 1F - 0.6F * pen;
            float deep = Mth.clamp((ru - d) / 1.2F, 0F, 1F);
            float cr = Mth.lerp(deep, 0.6F, 0.4F), cg = Mth.lerp(deep, 0.22F, 0.09F), cb = Mth.lerp(deep, 0.1F, 0.05F);
            float rim = (float) Math.exp(-((d - ru) / 0.16F) * ((d - ru) / 0.16F)) * 0.22F;
            out[0] = Mth.lerp(umbra, lit, cr) + rim * 0.2F;
            out[1] = Mth.lerp(umbra, lit, cg) + rim * 0.55F;
            out[2] = Mth.lerp(umbra, lit, cb) + rim * 0.65F;
        }, clear * swap);
        float total = smooth(ru - 0.9F, ru - 1.2F, Mth.sqrt(sx * sx + sz * sz));
        if (total > 0.01F) {
            double[] c = sky(0, -MOON_D * 1.01, 0);
            Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], MOON_D * 0.24, 0F, 0.8F, 0.25F, 0.12F, 0.12F * total * clear * night);
        }
    }

    /** The full moon at its nearest: bigger and brighter (warm-coloured low over the horizon). */
    private static void supermoon(float edge) {
        float swap = smooth(0.15F, 0.4F, night);
        if (swap <= 0.01F) {
            return;
        }
        float low = Mth.clamp(1F - (-sunY) * 4F, 0F, 1F);
        float r = 1F, g = 1F - 0.16F * low, b = 0.97F - 0.35F * low;
        double s = Mth.lerp(edge, 1F, 1.32F);
        moonSphere(s, (mx, mz, out) -> {
            out[0] = r;
            out[1] = g;
            out[2] = b;
        }, clear * swap);
        double[] c = sky(0, -MOON_D * 1.02, 0);
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], MOON_D * 0.62 * s, 0F, r, g * 0.98F, b * 0.95F, 0.2F * clear * Math.max(0.3F, night));
    }

    /**
     * A planet (or stars) passing behind the moon: the moon (as depth only, where vanilla's is) hides it as it goes
     * behind the limb, lit or dark, and it comes out again on the other side.
     */
    private static void occultation(SkyEvent e, float p, boolean stars) {
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        // The moon's disc, as depth only.
        double[] mc = sky(0, -MOON_D, 0).clone();
        Space3D.depthSphere(mc[0], mc[1], mc[2], MOON_D * Math.tan(MOON_A) * 0.99);
        int n = stars ? 6 : 1;
        for (int i = 0; i < n; i++) {
            float miss = stars ? (h(e.seed, 10 + i) - 0.5F) * 1.7F : (h(e.seed, 3) - 0.5F) * 0.9F;
            float offset = stars ? (h(e.seed, 20 + i) - 0.5F) * 2.4F : 0F;
            float mx = (p * 2F - 1F) * 2.8F + offset, mz = miss;
            double d = stars ? STAR_D : PLANET_D;
            float[] dir = norm(mx * Math.tan(MOON_A), -1, mz * Math.tan(MOON_A));
            double[] c = sky(dir[0] * d, dir[1] * d, dir[2] * d).clone();
            if (stars) {
                float br = 0.55F + 0.45F * h(e.seed, 30 + i);
                Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], d * 0.02 * br, 0F, 0.9F, 0.93F, 1F, 0.5F * vis);
                Space3D.billboard(STAR, ADD, c[0], c[1], c[2], d * 0.014 * br, 0F, 1F, 1F, 1F, vis * br);
            } else {
                planet(JUPITER, c, d * 0.011, true, 0.5F, vis, true);
                Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], d * 0.05, 0F, 1F, 0.95F, 0.85F, 0.3F * vis);
            }
        }
    }

    // ================================================================================== planets

    /** A lit planet sphere (with an atmosphere if `air`), turning slowly. */
    private static void planet(ResourceLocation map, double[] c, double r, boolean gas, float spinRate, float alpha, boolean skyFrame) {
        double[] rot = bodyRot(skyFrame, 0.18, 1, 0.1, 0);
        float spin = (float) (t * 0.00025 * spinRate % 1.0);
        int detail = 16;
        Space3D.sphere(map, c[0], c[1], c[2], r, rot, Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], 0.015F, 1F, 1F, 1F, 0F, alpha, false, spin,
                detail);
        if (gas) {
            Space3D.atmosphere(c[0], c[1], c[2], r, Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], 0.75F, 0.78F, 0.9F, 0.35F * alpha);
        }
    }

    private static void saturn(double[] c, double r, float alpha) {
        double[] rot = bodyRot(true, 0.45, 1, 0.2, 0);
        Space3D.sphere(SATURN, c[0], c[1], c[2], r, rot, Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], 0.015F, 1F, 1F, 1F, 0F, alpha, false,
                (float) (t * 0.0003 % 1.0), 16);
        Space3D.ring(RINGS, c[0], c[1], c[2], r * 1.25, r * 2.3, rot, Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], 0.9F * alpha);
    }

    /** Two (or three) planets close together. */
    private static void conjunction(SkyEvent e, float p, float edge, boolean triple) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        float[] d0 = nightDir(e.seed, 1, 0.12F, 0.55F);
        float sep = (2.5F + 4F * Math.abs(p - 0.5F)) * Mth.DEG_TO_RAD;
        // Along the ecliptic (the sky frame's z) and across it.
        float[] e1 = norm(0, -d0[2], d0[1]);
        float[] e2 = norm(d0[1] * e1[2] - d0[2] * e1[1], d0[2] * e1[0] - d0[0] * e1[2], d0[0] * e1[1] - d0[1] * e1[0]);
        if (triple) {
            float wide = sep * 2.2F;
            place(JUPITER, d0, e1, e2, -wide, 0.15F * wide, 0.021, vis);
            place(SATURN, d0, e1, e2, 0F, -0.12F * wide, 0.014, vis);
            place(MARS, d0, e1, e2, wide, 0.2F * wide, 0.009, vis);
        } else {
            place(JUPITER, d0, e1, e2, -sep * 0.5F, 0F, 0.021, vis);
            place(MARS, d0, e1, e2, sep * 0.5F, 0.35F * sep, 0.01, vis);
        }
    }

    private static void place(ResourceLocation map, float[] d0, float[] e1, float[] e2, float along, float across, double angR, float vis) {
        float[] d = norm(d0[0] + e1[0] * along + e2[0] * across, d0[1] + e1[1] * along + e2[1] * across, d0[2] + e1[2] * along + e2[2] * across);
        double[] c = sky(d[0] * PLANET_D, d[1] * PLANET_D, d[2] * PLANET_D).clone();
        double r = PLANET_D * angR;
        if (map == SATURN) {
            saturn(c, r, vis);
        } else {
            planet(map, c, r, map == JUPITER || map == VENUS, 1F, vis, true);
        }
        // Planet-shine round it.
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], r * 3.5, 0F, 1F, 0.95F, 0.85F, 0.12F * vis);
    }

    /** The planets strung out along the ecliptic in a line across the night sky. */
    private static void alignment(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        ResourceLocation[] maps = {NEPTUNE, MARS, VENUS, JUPITER, URANUS, SATURN};
        double[] sizes = {0.009, 0.01, 0.013, 0.021, 0.0095, 0.014};
        float shift = (h(e.seed, 1) - 0.5F) * 0.5F;
        float lane = (h(e.seed, 2) < 0.5F ? -1F : 1F) * (0.2F + 0.08F * h(e.seed, 3));
        for (int i = 0; i < maps.length; i++) {
            float phi = (-48F + i * 19F) * Mth.DEG_TO_RAD + shift;
            float x = 0.05F * Mth.sin(i * 1.7F) + lane;
            float[] d = norm(x, -Mth.cos(phi), Mth.sin(phi));
            double[] c = sky(d[0] * PLANET_D, d[1] * PLANET_D, d[2] * PLANET_D).clone();
            double r = PLANET_D * sizes[i];
            if (maps[i] == SATURN) {
                saturn(c, r, vis);
            } else {
                planet(maps[i], c, r, maps[i] == JUPITER || maps[i] == VENUS || maps[i] == NEPTUNE || maps[i] == URANUS, 1F, vis, true);
            }
            Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], r * 3.5, 0F, 1F, 0.95F, 0.85F, 0.1F * vis);
        }
    }

    // ================================================================================== the sun

    private static double sunUp() {
        return Mth.clamp(sunY * 6F, 0F, 1F) * clear;
    }

    /** A planet as a black ball crossing the face of the sun (only seen against its disc). */
    private static void transit(SkyEvent e, float p) {
        float up = (float) sunUp();
        if (up <= 0.01F) {
            return;
        }
        float ang = h(e.seed, 1) * Mth.TWO_PI, miss = (h(e.seed, 2) - 0.5F) * 1.1F;
        float along = (p * 2F - 1F) * 1.2F;
        float px = along * Mth.cos(ang) - miss * Mth.sin(ang), pz = along * Mth.sin(ang) + miss * Mth.cos(ang);
        double[] sun = sky(0, SUN_D, 0).clone();
        Space3D.mask(sun[0], sun[1], sun[2], SUN_A * 0.995);
        double tn = Math.tan(SUN_A), d = SUN_D * 0.8;
        float[] dir = norm(px * tn, 1, pz * tn);
        double[] c = sky(dir[0] * d, dir[1] * d, dir[2] * d).clone();
        Space3D.sphere(WHITE, c[0], c[1], c[2], d * tn * 0.12, bodyRot(true, 1, 0, 0, 0), Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], 0F,
                0.01F, 0.01F, 0.012F, 0F, 0.96F * up, false, 0F, 12);
        Space3D.unmask();
    }

    /** The space station's silhouette flicking across the sun in a second or two, now and then. */
    private static void satelliteTransit(SkyEvent e) {
        float up = (float) sunUp();
        if (up <= 0.01F) {
            return;
        }
        double age = t - e.start;
        int slot = (int) (age / 500.0);
        double in = age - slot * 500.0;
        if (in > 45) {
            return;
        }
        float f = (float) (in / 45.0);
        float ang = h(e.seed, slot * 3L) * Mth.TWO_PI, miss = (h(e.seed, slot * 3L + 1) - 0.5F) * 1.2F;
        float along = (f * 2F - 1F) * 1.3F;
        float px = along * Mth.cos(ang) - miss * Mth.sin(ang), pz = along * Mth.sin(ang) + miss * Mth.cos(ang);
        double[] sun = sky(0, SUN_D, 0).clone();
        Space3D.mask(sun[0], sun[1], sun[2], SUN_A * 0.995);
        double tn = Math.tan(SUN_A), d = SUN_D * 0.7;
        float[] dir = norm(px * tn, 1, pz * tn);
        double[] c = sky(dir[0] * d, dir[1] * d, dir[2] * d).clone();
        // Its velocity across the disc, in the sky frame.
        double[] vel = new double[3];
        Space3D.apply(Space3D.SKYVIEW, Mth.cos(ang), 0, Mth.sin(ang), vel);
        stationModel(c, d * tn * 0.006, dir[0], dir[1], dir[2], true, vel, 0.02F, 0.02F, 0.025F, 0.97F * up, true);
        Space3D.unmask();
    }

    /** Coronal loops and prominences at the sun's edge, fire round it, and every so often a great burst of plasma. */
    private static void coronalMassEjection(SkyEvent e, float edge) {
        float up = (float) sunUp() * edge;
        if (up <= 0.01F) {
            return;
        }
        double[] c = sky(0, SUN_D, 0).clone();
        double r = SUN_D * Math.tan(SUN_A) * 0.62;
        Space3D.starFire(c[0], c[1], c[2], r, e.seed, 160, 0.6F, (float) (t - e.start), up);
        // Loops: arcs of glowing plasma standing on the surface along the limb.
        double[] axis = new double[3];
        for (int i = 0; i < 14; i++) {
            float a0 = h(e.seed, 100 + i) * Mth.TWO_PI, span = 0.15F + 0.3F * h(e.seed, 120 + i), height = 0.15F + 0.5F * h(e.seed, 140 + i);
            float drift = (float) ((t - e.start) * 0.0006);
            double px = 0, py = 0, pz = 0;
            for (int s = 0; s <= 16; s++) {
                float f = s / 16F;
                float aa = a0 + drift + (f - 0.5F) * span;
                double rr = r * (1 + height * Mth.sin(Mth.PI * f));
                // Round the limb (in the plane across the line of sight), tipped a little towards or away from us.
                double lx = Math.cos(aa), lz = Math.sin(aa);
                Space3D.apply(Space3D.SKYVIEW, lx, 0.15 * Math.sin(i * 1.3), lz, axis);
                double qx = c[0] + axis[0] * rr, qy = c[1] + axis[1] * rr, qz = c[2] + axis[2] * rr;
                if (s > 0) {
                    float a = up * 0.55F * (0.6F + 0.4F * Mth.sin(Mth.PI * f));
                    Space3D.ribbon(LINE, ADD, px, py, pz, qx, qy, qz, r * 0.018, r * 0.018, 1F, 0.62F, 0.25F, a, a);
                }
                px = qx;
                py = qy;
                pz = qz;
            }
        }
        // A burst of plasma flung out every so often.
        double age = t - e.start;
        int slot = (int) (age / 1500.0);
        float f = (float) ((age - slot * 1500.0) / 700.0);
        if (f < 1F) {
            float ang = h(e.seed, slot) * Mth.TWO_PI;
            for (int i = 0; i < 90; i++) {
                float spread = (h(e.seed, slot * 100L + i) - 0.5F) * 1.2F;
                float speed = 0.6F + 0.8F * h(e.seed, slot * 100L + i + 50);
                double rr = r * (1.05 + 3.2 * f * speed);
                Space3D.apply(Space3D.SKYVIEW, Math.cos(ang + spread), (h(e.seed, i * 3L) - 0.5) * 0.4, Math.sin(ang + spread), axis);
                float a = (1F - f) * smooth(0F, 0.08F, f) * 0.5F * up;
                Space3D.billboard(FLAME, ADD, c[0] + axis[0] * rr, c[1] + axis[1] * rr, c[2] + axis[2] * rr, r * (0.12 + 0.3 * f), i, 1F,
                        0.55F - 0.3F * f, 0.25F - 0.2F * f, a);
            }
        }
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], r * 3.2, 0F, 1F, 0.8F, 0.5F, 0.25F * up);
    }

    // ================================================================================== meteors and fireballs

    /**
     * Meteors burning up in the air along real 3D paths. In a shower they all fly parallel, so from the ground they
     * seem to stream out of one point (the radiant); sporadic ones come from anywhere.
     */
    private static void meteors(SkyEvent e, float p, boolean sporadic) {
        float vis = night * clear;
        if (vis <= 0.02F) {
            return;
        }
        float rate = sporadic ? 0.3F : 0.2F + 0.8F * (float) Math.exp(-((p - 0.5F) / 0.22F) * ((p - 0.5F) / 0.22F));
        float[] rad = local(h(e.seed, 1) * Mth.TWO_PI, 0.6F + 0.5F * h(e.seed, 2));
        double age = t - e.start;
        long first = (long) Math.floor((age - 32) / 3.0), last = (long) Math.floor(age / 3.0);
        for (long k = Math.max(0, first); k <= last; k++) {
            if (h(e.seed, k * 7 + 100) > 0.8F * rate) {
                continue;
            }
            double born = k * 3.0 + h(e.seed, k * 7 + 101) * 3.0;
            float life = 10F + h(e.seed, k * 7 + 102) * 16F;
            float f = (float) ((age - born) / life);
            if (f < 0F || f > 1F) {
                continue;
            }
            float[] dir = sporadic ? local(h(e.seed, k * 7 + 106) * Mth.TWO_PI, -0.2F - 0.9F * h(e.seed, k * 7 + 107))
                    : new float[]{-rad[0], -rad[1], -rad[2]};
            // Where it burns out: 1000-1500 up, a few kilometres off.
            float az = h(e.seed, k * 7 + 103) * Mth.TWO_PI, dist = 1500F + 6000F * h(e.seed, k * 7 + 104);
            double qx = Mth.sin(az) * dist, qy = 1000 + 500 * h(e.seed, k * 7 + 105), qz = -Mth.cos(az) * dist;
            double len = 1200 + 2200 * h(e.seed, k * 7 + 109);
            double hx = qx - dir[0] * len * (1 - f), hy = qy - dir[1] * len * (1 - f), hz = qz - dir[2] * len * (1 - f);
            double train = Math.min(len * f, len * 0.5);
            double tx = hx - dir[0] * train, ty = hy - dir[1] * train, tz = hz - dir[2] * train;
            float bright = 0.5F + (float) Math.pow(h(e.seed, k * 7 + 108), 3) * 1.5F;
            int tint = (int) (h(e.seed, k * 7 + 110) * 4F);
            float cr = tint == 1 ? 0.7F : tint == 2 ? 1F : tint == 3 ? 0.82F : 1F;
            float cg = tint == 1 ? 1F : tint == 2 ? 0.75F : tint == 3 ? 0.9F : 0.95F;
            float cb = tint == 1 ? 0.75F : tint == 2 ? 0.5F : tint == 3 ? 1F : 0.82F;
            float a = (float) Math.pow(Mth.sin(Mth.PI * f), 0.5) * Math.min(1F, 0.35F + bright) * vis;
            double[] t0 = world(tx, ty, tz).clone();
            double[] h0 = world(hx, hy, hz).clone();
            double hd = Math.sqrt(h0[0] * h0[0] + h0[1] * h0[1] + h0[2] * h0[2]);
            double w = hd * (0.0028 + 0.0022 * bright);
            Space3D.ribbon(METEOR, ADD, t0[0], t0[1], t0[2], h0[0], h0[1], h0[2], w * 0.6, w, cr, cg, cb, a, a);
            Space3D.billboard(GLOW, ADD, h0[0], h0[1], h0[2], w * 3.5, 0F, cr, cg, cb, a * 0.6F);
            // Sparks breaking off the head and fading in the train.
            for (int s = 0; s < 5; s++) {
                float sf = h(e.seed, k * 31 + s);
                double sx = hx - dir[0] * train * sf, sy = hy - dir[1] * train * sf, sz = hz - dir[2] * train * sf;
                double[] sp = world(sx, sy + (h(e.seed, k * 37 + s) - 0.5) * 20, sz);
                Space3D.billboard(FLAME, ADD, sp[0], sp[1], sp[2], w * 1.1, s, 1F, 0.8F, 0.5F, a * 0.45F * (1F - sf));
            }
        }
    }

    /** Fireball k of an event: when it starts and how long it burns (ticks), or null when that slot has none. */
    private static double[] fireball(SkyEvent e, long k) {
        if (e.type == SkyEvent.DEBRIS_REENTRY) {
            return k == 0 ? new double[]{e.start + 40.0, 620.0} : null;
        }
        if (k != 0 && h(e.seed, k * 5 + 300) > 0.7F) {
            return null;
        }
        double born = e.start + k * 480.0 + (k == 0 ? 60.0 : h(e.seed, k * 5 + 301) * 300.0);
        return new double[]{born, 70.0 + h(e.seed, k * 5 + 302) * 50.0};
    }

    /** 0..1: how brightly the fireballs of an event light the land at this tick. */
    static float fireballLight(SkyEvent e, double time) {
        float best = 0F;
        long slot = (long) ((time - e.start) / 480.0);
        for (long k = Math.max(0, slot - 1); k <= slot; k++) {
            double[] fb = fireball(e, k);
            if (fb == null) {
                continue;
            }
            float f = (float) ((time - fb[0]) / fb[1]);
            if (f >= 0F && f <= 1F) {
                float flare = e.type == SkyEvent.DEBRIS_REENTRY ? 0.25F : 0.35F + 0.65F * smooth(0.7F, 0.9F, f) * (1F - smooth(0.92F, 1F, f));
                best = Math.max(best, flare * Mth.sin(Mth.PI * f));
            }
        }
        return best * 0.6F;
    }

    /** Whether one of the event's fireballs (a big one) burns out at exactly this tick. */
    static boolean fireballEndsAt(SkyEvent e, long tick) {
        long slot = (long) ((tick - e.start) / 480.0);
        for (long k = Math.max(0, slot - 1); k <= slot; k++) {
            double[] fb = fireball(e, k);
            if (fb != null && (long) (fb[0] + fb[1]) == tick && (e.type == SkyEvent.DEBRIS_REENTRY || h(e.seed, k * 5 + 303) < 0.45F)) {
                return true;
            }
        }
        return false;
    }

    /** The path of fireball k: start and end (camera-relative world), and fills HEAD at fraction f. */
    private static final double[] HEAD = new double[3];

    private static void fireballPath(SkyEvent e, long k, float f) {
        boolean debris = e.type == SkyEvent.DEBRIS_REENTRY;
        float az = h(e.seed, k * 5 + 304) * Mth.TWO_PI, turn = (h(e.seed, k * 5 + 305) - 0.5F) * 1.6F;
        float d0 = 3500F + 2500F * h(e.seed, k * 5 + 306), d1 = d0 * (debris ? 1.1F : 0.85F);
        float h0 = debris ? 2600F : 2800F, h1 = debris ? 1700F : 700F;
        float a = az + turn * f;
        float d = Mth.lerp(f, d0, d1);
        HEAD[0] = Mth.sin(a) * d;
        HEAD[1] = Mth.lerp(f, h0, h1);
        HEAD[2] = -Mth.cos(a) * d;
    }

    /** Fireballs (bolides) burning across the sky and bursting at the end; or a satellite falling back in pieces. */
    private static void fireballs(SkyEvent e) {
        float vis = Math.max(night, 0.3F) * clear;
        boolean debris = e.type == SkyEvent.DEBRIS_REENTRY;
        long slot = (long) ((t - e.start) / 480.0);
        for (long k = Math.max(0, slot - 1); k <= slot; k++) {
            double[] fb = fireball(e, k);
            if (fb == null) {
                continue;
            }
            float f = (float) ((t - fb[0]) / fb[1]);
            if (f < 0F || f > 1F) {
                continue;
            }
            int pieces = debris ? 9 : 1 + (f > 0.78F ? 4 : 0);
            for (int i = 0; i < pieces; i++) {
                float lag = debris ? i * 0.035F : i == 0 ? 0F : (f - 0.78F) * 0.25F * (1 + i * 0.3F);
                float fi = Mth.clamp(f - lag, 0F, 1F);
                fireballPath(e, k, fi);
                double spread = debris ? (h(e.seed, 400 + i) - 0.5) * 300 * fi : (h(e.seed, k * 5 + 310 + i) - 0.5) * 600 * (f - 0.78);
                double hx = HEAD[0] + spread, hy = HEAD[1] + spread * 0.3, hz = HEAD[2] - spread * 0.5;
                float bright = (debris ? 0.75F : 0.6F + 0.4F * smooth(0.7F, 0.9F, f)) * Mth.sin(Mth.PI * fi) * (i == 0 ? 1F : 0.7F);
                double[] h0 = world(hx, hy, hz).clone();
                double hd = Math.sqrt(h0[0] * h0[0] + h0[1] * h0[1] + h0[2] * h0[2]);
                // The fiery plume: flames shed along the path, cooling from white through orange to red and smoke.
                int flames = debris ? 14 : 30;
                for (int s = flames - 1; s >= 0; s--) {
                    float back = s / (float) flames;
                    float fs = Math.max(0F, fi - back * (debris ? 0.06F : 0.12F));
                    fireballPath(e, k, fs);
                    double[] q = world(HEAD[0] + spread, HEAD[1] + spread * 0.3 + back * 40, HEAD[2] - spread * 0.5).clone();
                    float heat = 1F - back;
                    float a = bright * vis * (0.65F - 0.6F * back) * (i == 0 ? 1F : 0.6F);
                    Space3D.billboard(FLAME, ADD, q[0], q[1], q[2], hd * (0.006 + 0.016 * back) * (debris ? 0.6 : 1), s * 0.7F, 1F,
                            0.35F + 0.6F * heat, 0.1F + 0.6F * heat * heat, a);
                }
                Space3D.billboard(GLOW, ADD, h0[0], h0[1], h0[2], hd * (debris ? 0.012 : 0.03) * (0.6 + bright), 0F, 1F, 0.85F, 0.6F, 0.55F * bright * vis);
                Space3D.billboard(STAR, ADD, h0[0], h0[1], h0[2], hd * (debris ? 0.01 : 0.022), 0F, 0.9F, 1F, 0.85F, 0.7F * bright * vis);
            }
        }
    }

    // ================================================================================== comets

    /** A comet: the glowing head, and tails of dust and gas streaming away from the sun in 3D. */
    private static void comet(SkyEvent e, float edge, boolean faint) {
        float vis = night * clear * edge * (faint ? 0.45F : 1F);
        if (vis <= 0.01F) {
            return;
        }
        float[] d = faint ? norm((h(e.seed, 6) - 0.5F) * 0.3F, Mth.cos(0.75F), -Mth.sin(0.75F)) : nightDir(e.seed, 1, 0.55F, 0.6F);
        double len = COMET_D * (faint ? 0.18 : 0.42);
        cometBody(e.seed, d[0] * COMET_D, d[1] * COMET_D, d[2] * COMET_D, 0, -1, 0, 0, 0, 1, len, vis, faint ? 160 : 280);
    }

    /** A comet at a point (sky frame), tails along `away`, the dust tail bending towards `bend`. */
    private static void cometBody(long seed, double cx, double cy, double cz, double ax, double ay, double az, double bx, double by, double bz,
                                  double len, float vis, int particles) {
        double[] head = sky(cx, cy, cz).clone();
        double hd = Math.sqrt(head[0] * head[0] + head[1] * head[1] + head[2] * head[2]);
        // The ion tail: a straight, thin, blue streak.
        double[] tail = sky(cx + ax * len * 1.2, cy + ay * len * 1.2, cz + az * len * 1.2).clone();
        Space3D.ribbon(METEOR, ADD, tail[0], tail[1], tail[2], head[0], head[1], head[2], hd * 0.006, hd * 0.0015, 0.5F, 0.7F, 1F, 0.65F * vis,
                0.65F * vis);
        // The dust tail: a broad, curved stream of glowing dust, flowing outwards.
        float time = (float) t;
        for (int i = 0; i < particles; i++) {
            float speed = 0.0004F + 0.0006F * h(seed, i * 3L);
            float s = (h(seed, i * 3L + 1) + time * speed) % 1F;
            double spreadA = h(seed, i * 3L + 2) * Math.PI * 2, spreadR = Math.sqrt(h(seed, i * 5L)) * 0.12 * s;
            double ux = bz * ay - by * az, uy = bx * az - bz * ax, uz = by * ax - bx * ay;
            double ox = (Math.cos(spreadA) * bx + Math.sin(spreadA) * ux) * spreadR * len,
                    oy = (Math.cos(spreadA) * by + Math.sin(spreadA) * uy) * spreadR * len,
                    oz = (Math.cos(spreadA) * bz + Math.sin(spreadA) * uz) * spreadR * len;
            double px = cx + ax * s * len + bx * s * s * len * 0.35 + ox, py = cy + ay * s * len + by * s * s * len * 0.35 + oy,
                    pz = cz + az * s * len + bz * s * s * len * 0.35 + oz;
            double[] q = sky(px, py, pz);
            float a = (float) Math.pow(1F - s, 1.3) * 0.13F * vis;
            Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], hd * (0.004 + 0.026 * s), 0F, 1F, 0.95F, 0.82F, a);
        }
        // The head: a bright greenish coma round a tiny star-like nucleus.
        Space3D.billboard(GLOW, ADD, head[0], head[1], head[2], hd * 0.02, 0F, 0.75F, 1F, 0.85F, 0.75F * vis);
        Space3D.billboard(STAR, ADD, head[0], head[1], head[2], hd * 0.012, 0F, 1F, 1F, 1F, vis);
    }

    /** A comet gliding visibly across the sky and swerving off its course on the way. */
    private static void wanderingComet(SkyEvent e, float p, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        float[] a = wanderAt(e, p), b = wanderAt(e, Math.min(1F, p + 0.01F));
        float[] m = norm(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
        // The tail trails behind it, bent away from the sun.
        float[] away = norm(m[0] * 0.65F, m[1] * 0.65F - 0.35F, m[2] * 0.65F);
        cometBody(e.seed, a[0] * COMET_D, a[1] * COMET_D, a[2] * COMET_D, away[0], away[1], away[2], m[0], m[1], m[2], COMET_D * 0.32, vis, 220);
    }

    private static float[] wanderAt(SkyEvent e, float p) {
        float side = h(e.seed, 2) - 0.5F;
        return norm(side * 0.6F + 0.25F * Mth.sin(Mth.PI * p) + 0.3F * smooth(0.45F, 0.62F, p) * Math.signum(side + 0.01F), -1F,
                Mth.lerp(p, -0.75F, 0.75F));
    }

    // ================================================================================== stars

    /** A star: a glowing sphere with a granulated, limb-darkened surface, fire round it, and its glare. */
    private static void star(long seed, double[] c, double r, float cr, float cg, float cb, float fire, float bright, float vis) {
        double[] rot = bodyRot(true, 0.2, 1, 0.1, 0);
        Space3D.sphere(STAR_MAP, c[0], c[1], c[2], r, rot, 0, 0, 1, 0F, cr * bright, cg * bright, cb * bright, 0F, vis, true,
                (float) (t * 0.0002 % 1.0), 14);
        Space3D.starFire(c[0], c[1], c[2], r, seed, (int) (60 + 120 * fire), cb, (float) t, vis * fire);
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], r * 6, 0F, cr, cg, cb, 0.45F * vis * bright);
        Space3D.billboard(STAR, ADD, c[0], c[1], c[2], r * 9, 0F, cr, cg, cb, 0.6F * vis * bright);
    }

    /** A star suddenly blazing up (bright enough to see by day), then slowly fading, in its expanding shell of gas. */
    private static void supernova(SkyEvent e, float p) {
        float b = p < 0.02F ? p / 0.02F : p < 0.08F ? 1F : Math.max(0.12F, (float) Math.exp(-(p - 0.08F) * 4F));
        float dark = night * clear, seen = Math.max(dark, b > 0.5F ? 0.45F * clear : 0F);
        float[] d = nightDir(e.seed, 1, 0.8F, 0.8F);
        double[] c = sky(d[0] * STAR_D, d[1] * STAR_D, d[2] * STAR_D).clone();
        // The remnant: filaments of glowing gas flung out in every direction.
        double shell = STAR_D * (0.006 + 0.035 * p);
        double[] q = new double[3];
        for (int i = 0; i < 420; i++) {
            double th = Math.acos(1 - 2 * h(e.seed, i * 4L)), ph = h(e.seed, i * 4L + 1) * Math.PI * 2;
            double rr = shell * (0.85 + 0.3 * h(e.seed, i * 4L + 2));
            Space3D.apply(Space3D.SKYVIEW, Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph), q);
            int tint = (int) (h(e.seed, i * 4L + 3) * 3F);
            float a = 0.09F * Math.min(1F, p * 15F) * dark;
            Space3D.billboard(GLOW, ADD, c[0] + q[0] * rr, c[1] + q[1] * rr, c[2] + q[2] * rr, shell * 0.12, 0F, tint == 0 ? 0.8F : tint == 1 ? 1F : 0.5F,
                    tint == 0 ? 0.35F : tint == 1 ? 0.45F : 0.7F, tint == 0 ? 1F : tint == 1 ? 0.8F : 1F, a);
        }
        star(e.seed, c, STAR_D * 0.0025 * (0.5 + b), 0.85F, 0.9F, 1F, 1F, 0.6F + 0.4F * b, seen);
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], STAR_D * 0.06 * b, 0F, 0.85F, 0.88F, 1F, 0.5F * b * seen);
    }

    /** A recurrent nova: a reddish star flaring up again and again, each time throwing off a shell of gas. */
    private static void recurrentNova(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        float[] d = nightDir(e.seed, 1, 0.7F, 0.7F);
        double[] c = sky(d[0] * STAR_D, d[1] * STAR_D, d[2] * STAR_D).clone();
        double age = t - e.start;
        float ph = (float) ((age % 2400.0) / 2400.0);
        float o = ph < 0.04F ? ph / 0.04F : (float) Math.exp(-(ph - 0.04F) * 7F);
        double shell = STAR_D * (0.003 + 0.012 * ph);
        double[] q = new double[3];
        for (int i = 0; i < 160; i++) {
            double th = Math.acos(1 - 2 * h(e.seed, i * 4L)), phi = h(e.seed, i * 4L + 1) * Math.PI * 2;
            Space3D.apply(Space3D.SKYVIEW, Math.sin(th) * Math.cos(phi), Math.cos(th), Math.sin(th) * Math.sin(phi), q);
            Space3D.billboard(FLAME, ADD, c[0] + q[0] * shell, c[1] + q[1] * shell, c[2] + q[2] * shell, shell * 0.15, i, 1F, 0.5F, 0.25F,
                    0.18F * (1F - ph) * vis);
        }
        star(e.seed, c, STAR_D * (0.0015 + 0.0015 * o), 1F, 0.55F, 0.3F, 0.8F, 0.5F + 0.5F * o, vis);
    }

    /** A red dwarf flaring now and then, with a planet beside it lit by its light. */
    private static void starFlares(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        float[] d = nightDir(e.seed, 1, 0.7F, 0.7F);
        double[] c = sky(d[0] * STAR_D, d[1] * STAR_D, d[2] * STAR_D).clone();
        double age = t - e.start;
        long slot = (long) (age / 300.0);
        float flare = 0F;
        for (long k = Math.max(0, slot - 1); k <= slot; k++) {
            if (h(e.seed, k + 50) > 0.55F) {
                continue;
            }
            float f = (float) ((age - k * 300.0 - h(e.seed, k + 51) * 100.0) / 220.0);
            if (f >= 0F && f <= 1F) {
                flare = Math.max(flare, f < 0.08F ? f / 0.08F : (float) Math.exp(-(f - 0.08F) * 5F));
            }
        }
        double r = STAR_D * 0.003;
        star(e.seed, c, r, 1F, 0.38F + 0.3F * flare, 0.2F + 0.3F * flare, 1F + flare, 0.7F + 0.6F * flare, vis);
        // The planet beside it, lit from the star's side.
        double[] q = sky(d[0] * STAR_D + STAR_D * 0.012, d[1] * STAR_D, d[2] * STAR_D - STAR_D * 0.006).clone();
        double lx = c[0] - q[0], ly = c[1] - q[1], lz = c[2] - q[2];
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        Space3D.sphere(TOI700E, q[0], q[1], q[2], r * 0.5, bodyRot(true, 0.3, 1, 0, 0), lx / ll, ly / ll, lz / ll, 0.01F, 1F, 0.8F, 0.7F, 0F, vis,
                false, (float) (t * 0.0004 % 1.0), 14);
    }

    // ================================================================================== the aurora

    /** Curtains of green light with red and violet tops, hanging high over the northern land and rippling. */
    private static void aurora(SkyEvent e, float edge, boolean storm) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        int ribbons = storm ? 6 : 3;
        int n = 64;
        float time = (float) (t - e.start);
        float speed = storm ? 2.2F : 1F;
        Space3D.use(AURORA, ADD, true, false);
        for (int i = 0; i < ribbons; i++) {
            float span = storm ? 2.4F : 1.35F;
            float pulse = 0.55F + 0.45F * Mth.sin(time * 0.0021F * speed + i * 1.9F + h(e.seed, i) * 6F);
            float inten = vis * pulse * (storm ? 1.25F : 0.9F) * (0.8F + 0.4F * h(e.seed, i + 10));
            float dist = storm ? (i == 0 ? 1600F : 3200F + i * 1200F) : 5000F + i * 1800F;
            float base = (storm ? 1400F : 1700F) + i * 180F;
            float tall = (storm ? 3600F : 2200F) + 400F * h(e.seed, i + 20);
            float az0 = (h(e.seed, i + 30) - 0.5F) * 0.5F;
            double[] pl = null, ph = null;
            float pu = 0F;
            for (int s = 0; s <= n; s++) {
                float f = (float) s / n;
                float az = az0 + (f - 0.5F) * 2F * span + 0.06F * Mth.sin(f * 17F + time * 0.01F * speed + i)
                        + 0.12F * Mth.sin(f * 5F - time * 0.004F * speed + i * 2F);
                float dd = dist * (1F + 0.3F * Mth.sin(f * 4.5F + time * 0.003F * speed + i * 1.3F));
                float top = base + tall * (0.75F + 0.25F * Mth.sin(f * 7F + time * 0.005F * speed + i));
                double bx = Mth.sin(az) * dd, bz = -Mth.cos(az) * dd;
                double[] lo = world(bx, base, bz).clone();
                double[] hi = world(bx, top, bz).clone();
                float u = f * 5F + time * 0.0007F * speed * (i + 1);
                if (pl != null) {
                    float a = inten * smooth(0F, 0.12F, f) * smooth(1F, 0.88F, f);
                    float gr = storm ? 0.3F : 0.2F, gb = storm ? 0.55F : 0.45F;
                    float tr = storm ? 0.85F : 0.45F, tg = storm ? 0.25F : 0.75F, tb = storm ? 1F : 0.65F;
                    Space3D.vtx(pl[0], pl[1], pl[2], pu, 1F, gr, 1F, gb, a);
                    Space3D.vtx(lo[0], lo[1], lo[2], u, 1F, gr, 1F, gb, a);
                    Space3D.vtx(hi[0], hi[1], hi[2], u, 0F, tr, tg, tb, a * 0.85F);
                    Space3D.vtx(ph[0], ph[1], ph[2], pu, 0F, tr, tg, tb, a * 0.85F);
                }
                pl = lo;
                ph = hi;
                pu = u;
            }
        }
        double[] g = world(0, 900, -9000);
        Space3D.billboard(GLOW, ADD, g[0], g[1], g[2], storm ? 9000 : 6500, 0F, 0.2F, 1F, 0.45F, 0.12F * vis);
    }

    // ================================================================================== satellites

    private static final double[] PP = new double[3], PV = new double[3];

    /**
     * A pass along the great circle that is highest (at `emax`) square to the heading: fills PP with the direction at
     * angle `th` along it (0 = the top) and PV with the direction of motion.
     */
    private static void pass(float heading, float emax, float th) {
        double hx = Math.cos(emax) * Math.sin(heading + Math.PI / 2), hy = Math.sin(emax), hz = -Math.cos(emax) * Math.cos(heading + Math.PI / 2);
        double fx = Math.sin(heading), fz = -Math.cos(heading);
        double c = Math.cos(th), s = Math.sin(th);
        PP[0] = c * hx + s * fx;
        PP[1] = c * hy;
        PP[2] = c * hz + s * fz;
        PV[0] = -s * hx + c * fx;
        PV[1] = -s * hy;
        PV[2] = -s * hz + c * fz;
    }

    /**
     * The space station: a long truss with four pairs of big solar wings turned to the sun, the pressurised modules
     * and the radiators, every face lit by the sun (or a black silhouette). Centre and directions in view space.
     */
    private static void stationModel(double[] c, double scale, double rx, double ry, double rz, boolean skyFrame, double[] velView, float cr,
                                     float cg, float cb, float alpha, boolean silhouette) {
        double[] up = new double[3];
        Space3D.apply(skyFrame ? Space3D.SKYVIEW : Space3D.VIEW, rx, ry, rz, up);
        double[] v = velView;
        double[] tr = cross(v, up);
        normalize(tr);
        double[] fw = cross(up, tr);
        normalize(fw);
        double[] l = Space3D.SUN;
        // The wings face the sun as far as they can turn about the truss.
        double ld = l[0] * tr[0] + l[1] * tr[1] + l[2] * tr[2];
        double[] pn = {l[0] - tr[0] * ld, l[1] - tr[1] * ld, l[2] - tr[2] * ld};
        if (!normalize(pn)) {
            pn = up.clone();
        }
        double[] pw = cross(pn, tr);
        normalize(pw);
        float amb = silhouette ? 0F : 0.06F;
        float r = silhouette ? cr : 0.82F, g = silhouette ? cg : 0.82F, b = silhouette ? cb : 0.8F;
        part(WHITE, c, scale, tr, 0, up, 0, fw, 0, tr, 55, up, 2, fw, 2, r, g, b, amb, 0F, alpha);
        part(WHITE, c, scale, tr, 0, up, 0, fw, 0, tr, 3, up, 3, fw, 24, r, g, b, amb, 0F, alpha);
        part(WHITE, c, scale, tr, 0, up, 0, fw, -10, tr, 13, up, 2.5, fw, 3, r, g, b, amb, 0F, alpha);
        for (int k = -1; k <= 1; k += 2) {
            part(WHITE, c, scale, tr, k * 13, up, 0, fw, 4, tr, 1, up, 9, fw, 6, r * 1.05F, g * 1.05F, b * 1.05F, amb, 0F, alpha);
        }
        float pr = silhouette ? cr : 1F, pg = silhouette ? cg : 1F, pb = silhouette ? cb : 1F;
        for (int k : new int[]{-52, -40, 40, 52}) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                part(silhouette ? WHITE : PANEL, c, scale, tr, k, pw, sgn * 20, pn, 0, tr, 5.5, pw, 17, pn, 0.25, pr, pg, pb, amb,
                        silhouette ? 0F : 2.5F, alpha);
            }
        }
    }

    /** One box of a model: offset along three axes, half-extents along three axes (model units times `scale`). */
    private static void part(ResourceLocation tex, double[] c, double scale, double[] o1, double k1, double[] o2, double k2, double[] o3, double k3,
                             double[] a1, double e1, double[] a2, double e2, double[] a3, double e3, float r, float g, float b, float amb, float spec,
                             float alpha) {
        double cx = c[0] + (o1[0] * k1 + o2[0] * k2 + o3[0] * k3) * scale;
        double cy = c[1] + (o1[1] * k1 + o2[1] * k2 + o3[1] * k3) * scale;
        double cz = c[2] + (o1[2] * k1 + o2[2] * k2 + o3[2] * k3) * scale;
        double[] x = {a1[0] * e1 * scale, a1[1] * e1 * scale, a1[2] * e1 * scale};
        double[] y = {a2[0] * e2 * scale, a2[1] * e2 * scale, a2[2] * e2 * scale};
        double[] z = {a3[0] * e3 * scale, a3[1] * e3 * scale, a3[2] * e3 * scale};
        Space3D.box(tex, cx, cy, cz, x, y, z, Space3D.SUN[0], Space3D.SUN[1], Space3D.SUN[2], r, g, b, amb, spec, alpha);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static boolean normalize(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1.0E-9) {
            return false;
        }
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
        return true;
    }

    /** The space station sailing across the sky, lit by the sun after dark. */
    private static void iss(SkyEvent e, float p) {
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        pass(h(e.seed, 1) * Mth.TWO_PI, (40F + h(e.seed, 2) * 45F) * Mth.DEG_TO_RAD, (p * 2F - 1F) * 82F * Mth.DEG_TO_RAD);
        float a = vis * smooth(0F, 0.12F, (float) PP[1]);
        if (a <= 0.01F) {
            return;
        }
        double d = 2500;
        double[] c = world(PP[0] * d, PP[1] * d, PP[2] * d).clone();
        double[] vel = new double[3];
        Space3D.apply(Space3D.VIEW, PV[0], PV[1], PV[2], vel);
        stationModel(c, d * 0.0016, PP[0], PP[1], PP[2], false, vel, 1F, 1F, 1F, a, false);
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], d * 0.06, 0F, 1F, 0.95F, 0.85F, 0.15F * a);
    }

    /** A train of satellites in a line, gliding across the sky and fading one by one into the Earth's shadow. */
    private static void starlink(SkyEvent e, float p) {
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        float heading = h(e.seed, 1) * Mth.TWO_PI, emax = (35F + h(e.seed, 2) * 40F) * Mth.DEG_TO_RAD;
        float head = (p * 2F - 1F) * 95F * Mth.DEG_TO_RAD;
        double d = 2200;
        double[] vel = new double[3];
        for (int i = 0; i < 28; i++) {
            float th = head - i * 1.15F * Mth.DEG_TO_RAD;
            if (th < -85F * Mth.DEG_TO_RAD || th > 55F * Mth.DEG_TO_RAD) {
                continue;
            }
            pass(heading, emax, th);
            float shadow = smooth(55F * Mth.DEG_TO_RAD, 40F * Mth.DEG_TO_RAD, th);
            float a = vis * smooth(0F, 0.1F, (float) PP[1]) * shadow * (0.6F + 0.4F * h(e.seed, i + 10));
            if (a <= 0.01F) {
                continue;
            }
            double[] c = world(PP[0] * d, PP[1] * d, PP[2] * d).clone();
            Space3D.apply(Space3D.VIEW, PV[0], PV[1], PV[2], vel);
            satellite(c, d * 0.0012, PP, vel, a, null);
            Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], d * 0.012, 0F, 1F, 1F, 0.95F, 0.7F * a);
        }
    }

    /** A small satellite: a box body and two long flat solar panels (mirror-bright when they catch the sun). */
    private static void satellite(double[] c, double scale, double[] radial, double[] velView, float alpha, double[] panelNormal) {
        double[] up = new double[3];
        Space3D.apply(Space3D.VIEW, radial[0], radial[1], radial[2], up);
        double[] tr = cross(velView, up);
        normalize(tr);
        double[] pn = panelNormal != null ? panelNormal : up;
        double[] pw = cross(pn, tr);
        normalize(pw);
        part(WHITE, c, scale, tr, 0, up, 0, velView, 0, tr, 3, up, 3, velView, 4, 0.85F, 0.78F, 0.55F, 0.08F, 0.6F, alpha);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            part(PANEL, c, scale, tr, sgn * 12, up, 0, velView, 0, tr, 8, pw, 3, pn, 0.2, 1F, 1F, 1F, 0.06F, 4F, alpha);
        }
    }

    /** A satellite whose panels catch the sun and mirror it to you for a few seconds: a flash far brighter than any star. */
    private static void satelliteFlare(SkyEvent e, float p) {
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        float heading = h(e.seed, 1) * Mth.TWO_PI, emax = (45F + h(e.seed, 2) * 40F) * Mth.DEG_TO_RAD;
        // The panels are set so that at the middle of the pass they mirror the sun straight down to you.
        pass(heading, emax, 0F);
        double[] mid = PP.clone();
        double[] sunW = {Space3D.SKY[3], Space3D.SKY[4], Space3D.SKY[5]};
        double[] nWorld = {sunW[0] - mid[0], sunW[1] - mid[1], sunW[2] - mid[2]};
        normalize(nWorld);
        double[] nView = new double[3];
        Space3D.apply(Space3D.VIEW, nWorld[0], nWorld[1], nWorld[2], nView);
        pass(heading, emax, (p * 2F - 1F) * 70F * Mth.DEG_TO_RAD);
        float a = vis * smooth(0F, 0.1F, (float) PP[1]);
        if (a <= 0.01F) {
            return;
        }
        double d = 2400;
        double[] c = world(PP[0] * d, PP[1] * d, PP[2] * d).clone();
        double[] vel = new double[3];
        Space3D.apply(Space3D.VIEW, PV[0], PV[1], PV[2], vel);
        satellite(c, d * 0.002, PP, vel, a, nView);
        // How well the panels mirror the sun into your eye right now.
        double[] to = {-c[0], -c[1], -c[2]};
        normalize(to);
        double nd = 2 * (nView[0] * Space3D.SUN[0] + nView[1] * Space3D.SUN[1] + nView[2] * Space3D.SUN[2]);
        double rx = nd * nView[0] - Space3D.SUN[0], ry = nd * nView[1] - Space3D.SUN[1], rz = nd * nView[2] - Space3D.SUN[2];
        float glint = (float) Math.pow(Math.max(0, rx * to[0] + ry * to[1] + rz * to[2]), 400);
        Space3D.billboard(STAR, ADD, c[0], c[1], c[2], d * (0.006 + 0.08 * glint), 0F, 1F, 1F, 0.96F, (0.35F + 0.65F * glint) * a);
        Space3D.billboard(GLOW, ADD, c[0], c[1], c[2], d * (0.008 + 0.12 * glint), 0F, 1F, 0.97F, 0.9F, 0.6F * glint * a);
    }

    // ================================================================================== above the storms

    /** Red sprites, blue jets and elves flashing high above distant thunderstorms, where they really are. */
    private static void upperLightning(Vec3 cam) {
        if (ClientSkyEvents.FLASHES.isEmpty() || night <= 0.05F) {
            return;
        }
        for (double[] f : ClientSkyEvents.FLASHES) {
            float age = (float) (t - f[4]);
            float a = age < 0F ? 0F : Math.min(1F, (float) Math.exp(-age / 3F) * night * 1.4F);
            if (a <= 0.01F) {
                continue;
            }
            double x = f[0] - cam.x, z = f[2] - cam.z, deck = f[1] - cam.y;
            float size = (float) f[5];
            int kind = (int) f[3];
            long seed = (long) (f[0] * 31 + f[2] * 17 + f[4]);
            if (kind == 0) {
                // A sprite: a red glowing crown 50-90 km up and tendrils hanging below it, blue at their tips.
                for (int c = 0; c < 3; c++) {
                    double ox = (c - 1) * 160 * size;
                    double[] crown = world(x + ox, deck + 760 * size, z).clone();
                    double cd = Math.sqrt(crown[0] * crown[0] + crown[1] * crown[1] + crown[2] * crown[2]);
                    Space3D.billboard(GLOW, ADD, crown[0], crown[1], crown[2], 170 * size, 0F, 1F, 0.2F, 0.25F, a * 0.8F);
                    for (int i = 0; i < 9; i++) {
                        double tx = x + ox + (h(seed, c * 20L + i) - 0.5) * 220 * size;
                        double[] top = world(tx, deck + 700 * size, z + (h(seed, c * 20L + i + 9) - 0.5) * 80).clone();
                        double bottomY = deck + (300 + 160 * h(seed, c * 20L + i + 5)) * size;
                        double[] bot = world(tx + (h(seed, c * 20L + i + 7) - 0.5) * 60, bottomY, z).clone();
                        Space3D.ribbon(SPRITE, ADD, bot[0], bot[1], bot[2], top[0], top[1], top[2], cd * 0.0025, cd * 0.004, 1F, 1F, 1F, a * 0.9F, a);
                    }
                }
            } else if (kind == 1) {
                // A blue jet shooting up out of the cloud top in a narrow cone.
                double[] base = world(x, deck + 120, z).clone();
                double[] up = new double[3];
                Space3D.apply(Space3D.VIEW, 0, 1, 0, up);
                Space3D.cone(base[0], base[1], base[2], up[0], up[1], up[2], 420 * size, 70 * size, 0.35F, 0.55F, 1F, a);
            } else {
                // An elve: a red ring of light spreading out very high up.
                double rr = 300 + age * 160;
                for (int i = 0; i < 48; i++) {
                    double an = Math.PI * 2 * i / 48;
                    double[] q = world(x + Math.cos(an) * rr, deck + 1500, z + Math.sin(an) * rr);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], 90, 0F, 1F, 0.25F, 0.2F, a * 0.35F);
                }
            }
        }
    }

    // ================================================================================== the Milky Way

    /** The Milky Way arching across a dark sky: its glowing band, its star clouds, and the zodiacal light after dusk. */
    private static void dustGlow(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        float tilt = (55F + h(e.seed, 1) * 25F) * Mth.DEG_TO_RAD, spin = h(e.seed, 2) * Mth.TWO_PI;
        double ax = Math.cos(spin), ay = 0, az = Math.sin(spin);
        double bx = -Math.sin(spin) * Math.cos(tilt), by = Math.sin(tilt), bz = Math.cos(spin) * Math.cos(tilt);
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double w = 0.3;
        double cw = Math.cos(w), sw = Math.sin(w);
        Space3D.use(MILKY_WAY, ADD, true, false);
        int n = 96;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
            double p0x = c0 * ax + s0 * bx, p0y = c0 * ay + s0 * by, p0z = c0 * az + s0 * bz;
            double p1x = c1 * ax + s1 * bx, p1y = c1 * ay + s1 * by, p1z = c1 * az + s1 * bz;
            float u0 = (float) i / n, u1 = (float) (i + 1) / n;
            float a = 0.8F * vis;
            double[] q;
            q = sky((p0x * cw - nx * sw) * MILKY_D, (p0y * cw - ny * sw) * MILKY_D, (p0z * cw - nz * sw) * MILKY_D);
            Space3D.vtx(q[0], q[1], q[2], u0, 1F, 1F, 1F, 1F, a);
            q = sky((p1x * cw - nx * sw) * MILKY_D, (p1y * cw - ny * sw) * MILKY_D, (p1z * cw - nz * sw) * MILKY_D);
            Space3D.vtx(q[0], q[1], q[2], u1, 1F, 1F, 1F, 1F, a);
            q = sky((p1x * cw + nx * sw) * MILKY_D, (p1y * cw + ny * sw) * MILKY_D, (p1z * cw + nz * sw) * MILKY_D);
            Space3D.vtx(q[0], q[1], q[2], u1, 0F, 1F, 1F, 1F, a);
            q = sky((p0x * cw + nx * sw) * MILKY_D, (p0y * cw + ny * sw) * MILKY_D, (p0z * cw + nz * sw) * MILKY_D);
            Space3D.vtx(q[0], q[1], q[2], u0, 0F, 1F, 1F, 1F, a);
        }
        // Thousands of stars crowded in the band.
        for (int i = 0; i < 1400; i++) {
            double th = h(e.seed, i * 3L) * Math.PI * 2;
            double off = (h(e.seed, i * 3L + 1) + h(e.seed, i * 3L + 2) - 1.0) * 0.35;
            double c = Math.cos(th), s = Math.sin(th);
            double px = (c * ax + s * bx) * Math.cos(off) + nx * Math.sin(off), py = (c * ay + s * by) * Math.cos(off) + ny * Math.sin(off),
                    pz = (c * az + s * bz) * Math.cos(off) + nz * Math.sin(off);
            double[] q = sky(px * MILKY_D * 0.9, py * MILKY_D * 0.9, pz * MILKY_D * 0.9);
            float b = 0.3F + 0.7F * (float) Math.pow(h(e.seed, i * 5L), 3);
            int tint = (int) (h(e.seed, i * 5L + 1) * 3);
            Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], MILKY_D * 0.9 * 0.0025 * (0.6 + b), 0F, tint == 0 ? 1F : tint == 1 ? 0.85F : 1F, tint == 0 ? 0.92F : 0.9F,
                    tint == 0 ? 0.8F : 1F, b * vis * 0.8F);
        }
        float dusk = smooth(0F, -0.25F, sunY) * smooth(-0.75F, -0.4F, sunY);
        if (dusk > 0.01F) {
            for (int i = 0; i < 4; i++) {
                double ang = (32F + i * 11F) * Mth.DEG_TO_RAD;
                for (double side : new double[]{-1, 1}) {
                    double[] q = sky(0, Math.cos(ang) * MILKY_D * 0.8, side * Math.sin(ang) * MILKY_D * 0.8);
                    Space3D.billboard(GLOW, ADD, q[0], q[1], q[2], MILKY_D * 0.8 * (0.26 - i * 0.04), 0F, 1F, 0.95F, 0.85F, 0.06F * dusk * vis);
                }
            }
        }
    }

    // ================================================================================== pointing the camera (tests)

    /** Where the main thing of a sky event is right now (a world direction), for pointing the camera at it; or null. */
    public static float[] focus(int type, ClientLevel level, float pt) {
        double time = level.getGameTime() + pt;
        SkyEvent e = ClientSkyEvents.get(type, time);
        if (e == null) {
            return null;
        }
        t = time;
        float p = (float) e.progress(time);
        float th = level.getTimeOfDay(pt) * Mth.TWO_PI;
        float[] d;
        boolean skyFrame = true;
        switch (type) {
            case SkyEvent.COMET -> d = nightDir(e.seed, 1, 0.55F, 0.6F);
            case SkyEvent.FAINT_COMET -> d = norm((h(e.seed, 6) - 0.5F) * 0.3F, Mth.cos(0.75F), -Mth.sin(0.75F));
            case SkyEvent.CONJUNCTION, SkyEvent.TRIPLE_CONJUNCTION -> d = nightDir(e.seed, 1, 0.12F, 0.55F);
            case SkyEvent.ALIGNMENT -> {
                float shift = (h(e.seed, 1) - 0.5F) * 0.5F;
                float lane = (h(e.seed, 2) < 0.5F ? -1F : 1F) * (0.2F + 0.08F * h(e.seed, 3));
                d = norm(lane, -Mth.cos(shift), Mth.sin(shift));
            }
            case SkyEvent.SUPERNOVA -> d = nightDir(e.seed, 1, 0.8F, 0.8F);
            case SkyEvent.RECURRENT_NOVA, SkyEvent.STAR_FLARES -> d = nightDir(e.seed, 1, 0.7F, 0.7F);
            case SkyEvent.WANDERING_COMET -> d = wanderAt(e, p);
            case SkyEvent.TRANSIT, SkyEvent.SATELLITE_TRANSIT, SkyEvent.CORONAL_MASS_EJECTION -> d = new float[]{0F, 1F, 0F};
            case SkyEvent.DUST_GLOW -> {
                float tilt = (55F + h(e.seed, 1) * 25F) * Mth.DEG_TO_RAD, spin = h(e.seed, 2) * Mth.TWO_PI;
                d = new float[]{Mth.sin(spin) * Mth.cos(tilt), -Mth.sin(tilt), -Mth.cos(spin) * Mth.cos(tilt)};
            }
            case SkyEvent.METEOR_SHOWER -> {
                skyFrame = false;
                d = local(h(e.seed, 1) * Mth.TWO_PI, Math.max(0.35F, 0.6F + 0.5F * h(e.seed, 2) - 0.4F));
            }
            case SkyEvent.SPORADIC_METEORS -> {
                skyFrame = false;
                d = local(0F, 0.5F);
            }
            case SkyEvent.AURORA, SkyEvent.AURORA_STORM -> {
                skyFrame = false;
                d = norm(0F, 0.4F, -1F);
            }
            case SkyEvent.ISS, SkyEvent.SATELLITE_FLARE, SkyEvent.STARLINK -> {
                skyFrame = false;
                float span = type == SkyEvent.STARLINK ? 95F : type == SkyEvent.ISS ? 82F : 70F;
                float lo = type == SkyEvent.STARLINK ? 35F : type == SkyEvent.ISS ? 40F : 45F, hi = type == SkyEvent.STARLINK ? 40F : 45F;
                float shift = type == SkyEvent.STARLINK ? -14F : 0F;
                pass(h(e.seed, 1) * Mth.TWO_PI, (lo + h(e.seed, 2) * hi) * Mth.DEG_TO_RAD, ((p * 2F - 1F) * span + shift) * Mth.DEG_TO_RAD);
                d = new float[]{(float) PP[0], (float) PP[1], (float) PP[2]};
            }
            case SkyEvent.FIREBALLS, SkyEvent.DEBRIS_REENTRY -> {
                skyFrame = false;
                double[] fb = fireball(e, 0);
                float f = fb == null ? 0.5F : Mth.clamp((float) ((time - fb[0]) / fb[1]), 0F, 1F);
                fireballPath(e, 0, f);
                d = norm(HEAD[0], HEAD[1], HEAD[2]);
            }
            case SkyEvent.UPPER_LIGHTNING -> {
                skyFrame = false;
                d = norm(0F, 0.25F, -1F);
                if (!ClientSkyEvents.FLASHES.isEmpty()) {
                    double[] f = ClientSkyEvents.FLASHES.get(ClientSkyEvents.FLASHES.size() - 1);
                    Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                    d = norm(f[0] - cam.x, f[1] + 500 - cam.y, f[2] - cam.z);
                }
            }
            case SkyEvent.RAINBOW -> {
                float sx = -Mth.sin(th);
                d = norm(sx > 0F ? -1F : 1F, 0.3F, 0F);
                skyFrame = false;
            }
            default -> d = new float[]{0F, -1F, 0F};
        }
        if (!skyFrame) {
            return d;
        }
        float c = Mth.cos(th), sn = Mth.sin(th);
        return new float[]{-(d[1] * sn + d[2] * c), d[1] * c - d[2] * sn, d[0]};
    }
}
