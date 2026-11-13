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

/**
 * The sky events, drawn right after the sky (so the clouds and the land are drawn in front of them). Things fixed among
 * the stars (planets, comets, novae, the Milky Way) turn with the sky like the stars do; passes of satellites, meteors,
 * fireballs and the aurora are placed in the local sky. Most are only seen when the sky is dark and clear.
 */
public final class SpaceRenderer {
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final ResourceLocation DISC = RealWeather.id("textures/environment/disc.png");
    private static final ResourceLocation GLOW = tex("glow"), STAR = tex("star"), METEOR = tex("meteor"), FIREBALL = tex("fireball"),
            COMET = tex("comet"), PLANETS = tex("planets"), SATURN = tex("saturn"), ISS = tex("iss"), SATELLITE = tex("satellite"),
            AURORA = tex("aurora"), MILKY_WAY = tex("milky_way"), SUPERNOVA = tex("supernova"), NOVA = tex("nova_shell"),
            LOOPS = tex("coronal_loops"), SPRITE = tex("red_sprite"), JET = tex("blue_jet");
    /** Planet cells in the atlas. */
    private static final int MERCURY = 0, VENUS = 1, MARS = 2, JUPITER = 3, URANUS = 4, NEPTUNE = 5;
    private static final int ADD = 0, NORMAL = 1, SUBTRACT = 2;
    /** The vanilla moon: half-size of its quad, and the radius of the disc in it. */
    private static final float MOON_Q = 20F, MOON_R = 10F;
    private static final float SUN_R = EclipseRenderer.SUN_R;

    private static boolean failed;
    private static BufferBuilder buf;
    private static ResourceLocation bound;
    private static int boundMode = -1;
    private static Matrix4f world, sky;
    private static Matrix4f current;
    /** This frame: game time, how dark and how clear the sky is, how high the sun is (-1..1). */
    private static double t;
    private static float night, clear, sunY;

    private SpaceRenderer() {}

    private static ResourceLocation tex(String name) {
        return RealWeather.id("textures/environment/space/" + name + ".png");
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || ClientSkyEvents.isEmpty()) {
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
        float angle = level.getTimeOfDay(pt) * Mth.TWO_PI;
        sunY = Mth.cos(angle);
        world = new Matrix4f(event.getModelViewMatrix());
        sky = new Matrix4f(event.getModelViewMatrix()).rotate(Axis.YP.rotationDegrees(-90F)).rotate(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360F));
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        current = null;
        try {
            for (SkyEvent e : ClientSkyEvents.EVENTS) {
                if (e.active(t)) {
                    draw(e, level, event);
                }
            }
            flush();
        } catch (RuntimeException e) {
            failed = true;
            RealWeather.LOG.error("Sky events disabled after an error", e);
        } finally {
            buf = null;
            bound = null;
            boundMode = -1;
            GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void draw(SkyEvent e, ClientLevel level, RenderLevelStageEvent event) {
        float p = (float) e.progress(t);
        // Fade in and out at the very start and end of long events.
        float edge = Mth.clamp(Math.min(p, 1F - p) * 40F, 0F, 1F);
        switch (e.type) {
            case SkyEvent.LUNAR_ECLIPSE -> lunarEclipse(e, level, p);
            case SkyEvent.SUPERMOON -> supermoon(level, edge);
            case SkyEvent.METEOR_SHOWER -> meteors(e, p, false);
            case SkyEvent.SPORADIC_METEORS -> meteors(e, p, true);
            case SkyEvent.COMET -> comet(e, edge, 1F, false);
            case SkyEvent.FAINT_COMET -> comet(e, edge, 0.42F, true);
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
            case SkyEvent.COMET -> d = nightDir(e.seed, 1, 0.55F, 0.6F).clone();
            case SkyEvent.FAINT_COMET -> {
                float side = h(e.seed, 5) < 0.5F ? -1F : 1F;
                d = norm((h(e.seed, 6) - 0.5F) * 0.3F, Mth.cos(0.75F), side * Mth.sin(0.75F)).clone();
            }
            case SkyEvent.CONJUNCTION, SkyEvent.TRIPLE_CONJUNCTION -> d = nightDir(e.seed, 1, 0.12F, 0.55F).clone();
            case SkyEvent.ALIGNMENT -> {
                float shift = (h(e.seed, 1) - 0.5F) * 0.5F;
                d = norm(0F, -Mth.cos(shift), Mth.sin(shift)).clone();
            }
            case SkyEvent.SUPERNOVA -> d = nightDir(e.seed, 1, 0.8F, 0.8F).clone();
            case SkyEvent.RECURRENT_NOVA, SkyEvent.STAR_FLARES -> d = nightDir(e.seed, 1, 0.7F, 0.7F).clone();
            case SkyEvent.WANDERING_COMET -> {
                float side = h(e.seed, 2) - 0.5F;
                d = norm(side * 0.6F + 0.25F * Mth.sin(Mth.PI * p) + 0.3F * smooth(0.45F, 0.62F, p) * Math.signum(side + 0.01F), -1F,
                        Mth.lerp(p, -0.75F, 0.75F)).clone();
            }
            case SkyEvent.TRANSIT, SkyEvent.SATELLITE_TRANSIT, SkyEvent.CORONAL_MASS_EJECTION -> d = new float[]{0F, 1F, 0F};
            case SkyEvent.DUST_GLOW -> {
                float tilt = (55F + h(e.seed, 1) * 25F) * Mth.DEG_TO_RAD, spin = h(e.seed, 2) * Mth.TWO_PI;
                d = new float[]{Mth.sin(spin) * Mth.cos(tilt), -Mth.sin(tilt), -Mth.cos(spin) * Mth.cos(tilt)};
            }
            case SkyEvent.METEOR_SHOWER -> {
                skyFrame = false;
                d = local(h(e.seed, 1) * Mth.TWO_PI, Math.max(0.35F, 0.6F + 0.5F * h(e.seed, 2) - 0.4F)).clone();
            }
            case SkyEvent.AURORA, SkyEvent.AURORA_STORM -> {
                skyFrame = false;
                d = norm(0F, 0.4F, -1F).clone();
            }
            case SkyEvent.ISS, SkyEvent.SATELLITE_FLARE, SkyEvent.STARLINK -> {
                skyFrame = false;
                float span = type == SkyEvent.STARLINK ? 95F : type == SkyEvent.ISS ? 82F : 70F;
                float lo = type == SkyEvent.STARLINK ? 35F : type == SkyEvent.ISS ? 40F : 45F, hi = type == SkyEvent.STARLINK ? 40F : 45F;
                float shift = type == SkyEvent.STARLINK ? -14F : 0F;
                pass(h(e.seed, 1) * Mth.TWO_PI, (lo + h(e.seed, 2) * hi) * Mth.DEG_TO_RAD, ((p * 2F - 1F) * span + shift) * Mth.DEG_TO_RAD);
                d = P.clone();
            }
            case SkyEvent.FIREBALLS, SkyEvent.DEBRIS_REENTRY -> {
                skyFrame = false;
                boolean debris = type == SkyEvent.DEBRIS_REENTRY;
                double[] fb = fireball(e, 0);
                float f = fb == null ? 0.5F : Mth.clamp((float) ((time - fb[0]) / fb[1]), 0F, 1F);
                float az = h(e.seed, 304) * Mth.TWO_PI + (h(e.seed, 305) - 0.5F) * (debris ? 1.4F : 1.1F) * f;
                float el0 = debris ? 0.45F : 0.7F + h(e.seed, 306) * 0.5F, el1 = debris ? 0.22F : 0.08F + h(e.seed, 307) * 0.15F;
                d = local(az, Mth.lerp(f, el0, el1)).clone();
            }
            case SkyEvent.UPPER_LIGHTNING -> {
                skyFrame = false;
                d = norm(0F, 0.25F, -1F).clone();
                if (!ClientSkyEvents.FLASHES.isEmpty()) {
                    double[] f = ClientSkyEvents.FLASHES.get(ClientSkyEvents.FLASHES.size() - 1);
                    Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                    d = norm((float) (f[0] - cam.x), (float) (f[1] + 350 - cam.y), (float) (f[2] - cam.z)).clone();
                }
            }
            case SkyEvent.RAINBOW -> {
                // Opposite the sun, a little above the horizon.
                float sx = -Mth.sin(th);
                d = norm(sx > 0F ? -1F : 1F, 0.3F, 0F).clone();
                skyFrame = false;
            }
            default -> d = new float[]{0F, -1F, 0F};
        }
        if (!skyFrame) {
            return d;
        }
        // Sky frame to world: turned -90 degrees about Y after turning by the time of day about X.
        float c = Mth.cos(th), sn = Mth.sin(th);
        return new float[]{-(d[1] * sn + d[2] * c), d[1] * c - d[2] * sn, d[0]};
    }

    // ================================================================================== drawing helpers

    private static void frame(Matrix4f m) {
        if (current != m) {
            flush();
            current = m;
            Matrix4fStack mv = RenderSystem.getModelViewStack();
            mv.set(m);
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void begin(ResourceLocation texture, int mode) {
        if (buf != null && bound == texture && boundMode == mode) {
            return;
        }
        flush();
        switch (mode) {
            case ADD -> {
                GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO);
            }
            case SUBTRACT -> {
                GlStateManager._blendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO,
                        GlStateManager.DestFactor.ONE);
            }
            default -> {
                GlStateManager._blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.defaultBlendFunc();
            }
        }
        if (texture == MOON) {
            VolumeClouds.useGlowKeepFilter(texture);
        } else {
            VolumeClouds.useGlow(texture);
        }
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bound = texture;
        boundMode = mode;
    }

    private static void flush() {
        if (buf != null) {
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            buf = null;
        }
    }

    private static void vtx(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        buf.addVertex(x, y, z).setUv(u, v).setColor(k(r), k(g), k(b), k(a));
    }

    private static float k(float v) {
        return v < 0F ? 0F : v > 1F ? 1F : v;
    }

    /** A quad round a centre, with half-vectors to its right and up edges. */
    private static void quad(float cx, float cy, float cz, float rx, float ry, float rz, float ux, float uy, float uz,
                             float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        vtx(cx - rx - ux, cy - ry - uy, cz - rz - uz, u0, v1, r, g, b, a);
        vtx(cx + rx - ux, cy + ry - uy, cz + rz - uz, u1, v1, r, g, b, a);
        vtx(cx + rx + ux, cy + ry + uy, cz + rz + uz, u1, v0, r, g, b, a);
        vtx(cx - rx + ux, cy - ry + uy, cz - rz + uz, u0, v0, r, g, b, a);
    }

    private static final float[] R = new float[3], U = new float[3];

    /**
     * A sprite on the sky sphere (100 out) in direction d, facing the viewer, its width along `hint` (made square to
     * the line of sight).
     */
    private static void sprite(float dx, float dy, float dz, float hw, float hh, float hx, float hy, float hz,
                               float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        if (a <= 0.003F) {
            return;
        }
        basis(dx, dy, dz, hx, hy, hz);
        quad(dx * 100F, dy * 100F, dz * 100F, R[0] * hw, R[1] * hw, R[2] * hw, U[0] * hh, U[1] * hh, U[2] * hh, u0, v0, u1, v1, r, g, b, a);
    }

    /** Right (along the hint, square to d) and up (right x d) unit vectors for something seen in direction d. */
    private static void basis(float dx, float dy, float dz, float hx, float hy, float hz) {
        float dot = hx * dx + hy * dy + hz * dz;
        float rx = hx - dx * dot, ry = hy - dy * dot, rz = hz - dz * dot;
        float l = Mth.sqrt(rx * rx + ry * ry + rz * rz);
        if (l < 1.0E-4F) {
            // Hint along the line of sight: any square direction will do.
            rx = dy;
            ry = -dx;
            rz = 0F;
            l = Mth.sqrt(rx * rx + ry * ry) + 1.0E-6F;
            if (l < 1.0E-3F) {
                rx = 1F;
                ry = 0F;
                rz = 0F;
                l = 1F;
            }
        }
        R[0] = rx / l;
        R[1] = ry / l;
        R[2] = rz / l;
        U[0] = R[1] * dz - R[2] * dy;
        U[1] = R[2] * dx - R[0] * dz;
        U[2] = R[0] * dy - R[1] * dx;
    }

    /** A sprite whose up edge points as near to world (or sky) up as it can (sprites, jets). */
    private static void spriteUpright(float dx, float dy, float dz, float hw, float hh, float u0, float v0, float u1, float v1,
                                      float r, float g, float b, float a) {
        // Right = d x up.
        float rx = -dz, rz = dx;
        sprite(dx, dy, dz, hw, hh, rx, 0F, rz, u0, v0, u1, v1, r, g, b, a);
    }

    private static void glow(float dx, float dy, float dz, float size, float r, float g, float b, float a) {
        begin(GLOW, ADD);
        sprite(dx, dy, dz, size, size, 1F, 0F, 0F, 0F, 0F, 1F, 1F, r, g, b, a);
    }

    private static void star(float dx, float dy, float dz, float size, float r, float g, float b, float a) {
        begin(STAR, ADD);
        sprite(dx, dy, dz, size, size, 1F, 0F, 0F, 0F, 0F, 1F, 1F, r, g, b, a);
    }

    private static void planet(int cell, float dx, float dy, float dz, float radius, float hx, float hy, float hz, float a) {
        float u0 = (cell % 4) * 0.25F, v0 = (cell / 4) * 0.5F;
        float s = radius / 0.92F;
        begin(PLANETS, NORMAL);
        sprite(dx, dy, dz, s, s, hx, hy, hz, u0, v0, u0 + 0.25F, v0 + 0.5F, 1F, 1F, 1F, a);
    }

    private static void saturn(float dx, float dy, float dz, float radius, float hx, float hy, float hz, float a) {
        float s = radius / 0.22F * 0.5F;
        begin(SATURN, NORMAL);
        sprite(dx, dy, dz, s * 2F, s, hx, hy, hz, 0F, 0F, 1F, 1F, 1F, 1F, 1F, a);
    }

    private static final float[] D = new float[3];

    /** A direction in the sky frame around the point opposite the sun (seen at night), offset by up to `sx`, `sz`. */
    private static float[] nightDir(long seed, int salt, float sx, float sz) {
        float x = (h(seed, salt) - 0.5F) * 2F * sx, z = (h(seed, salt + 1) - 0.5F) * 2F * sz;
        return norm(x, -1F, z);
    }

    private static float[] norm(float x, float y, float z) {
        float l = Mth.sqrt(x * x + y * y + z * z) + 1.0E-6F;
        D[0] = x / l;
        D[1] = y / l;
        D[2] = z / l;
        return D;
    }

    /** A direction in the local sky: azimuth from north towards east, elevation, radians. */
    private static float[] local(float az, float el) {
        float c = Mth.cos(el);
        D[0] = c * Mth.sin(az);
        D[1] = Mth.sin(el);
        D[2] = -c * Mth.cos(az);
        return D;
    }

    static float h(long seed, long k) {
        long x = seed * 0x9E3779B97F4A7C15L + k * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x >>> 40) / (float) (1L << 24);
    }

    private static float smooth(float e0, float e1, float x) {
        float s = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return s * s * (3F - 2F * s);
    }

    // ================================================================================== the moon

    /**
     * Takes the vanilla moon (drawn additively just before) out of the sky again, exactly (`amount` of it: the moon
     * of the event is faded in as much). Only in a dark sky, where vanilla's moon can't have been clipped.
     */
    private static void removeVanillaMoon(ClientLevel level, float amount) {
        int phase = level.getMoonPhase();
        float u0 = (phase % 4) / 4F, u1 = (phase % 4 + 1) / 4F, v0 = (phase / 4 % 2) / 2F, v1 = (phase / 4 % 2 + 1) / 2F;
        frame(sky);
        begin(MOON, SUBTRACT);
        float a = clear * amount;
        vtx(-MOON_Q, -100F, MOON_Q, u1, v1, 1F, 1F, 1F, a);
        vtx(MOON_Q, -100F, MOON_Q, u0, v1, 1F, 1F, 1F, a);
        vtx(MOON_Q, -100F, -MOON_Q, u0, v0, 1F, 1F, 1F, a);
        vtx(-MOON_Q, -100F, -MOON_Q, u1, v0, 1F, 1F, 1F, a);
        flush();
    }

    /** The full moon as a grid over its disc, each corner coloured by `shade` (moon-local x, z in moon radii). */
    private interface Shade {
        void at(float mx, float mz, float[] out);
    }

    private static final float[] C = new float[3];

    private static void fullMoon(float scale, int n, Shade shade, float alpha) {
        frame(sky);
        begin(MOON, ADD);
        float q = MOON_Q * scale;
        // Full moon: the first cell of the phases texture.
        float u0 = 0F, u1 = 0.25F, v0 = 0F, v1 = 0.5F;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float x0 = -q + 2F * q * i / n, x1 = -q + 2F * q * (i + 1) / n;
                float z0 = -q + 2F * q * j / n, z1 = -q + 2F * q * (j + 1) / n;
                moonVertex(x0, z1, q, scale, u0, u1, v0, v1, shade, alpha);
                moonVertex(x1, z1, q, scale, u0, u1, v0, v1, shade, alpha);
                moonVertex(x1, z0, q, scale, u0, u1, v0, v1, shade, alpha);
                moonVertex(x0, z0, q, scale, u0, u1, v0, v1, shade, alpha);
            }
        }
    }

    private static void moonVertex(float x, float z, float q, float scale, float u0, float u1, float v0, float v1, Shade shade, float alpha) {
        float u = u1 - (x + q) / (2F * q) * (u1 - u0);
        float v = v0 + (z + q) / (2F * q) * (v1 - v0);
        shade.at(x / (MOON_R * scale), z / (MOON_R * scale), C);
        vtx(x, -100F, z, u, v, C[0], C[1], C[2], alpha);
    }

    /** Earth's shadow sweeps across the full moon: grey in the penumbra, copper red in the umbra (with a blue-green rim). */
    private static void lunarEclipse(SkyEvent e, ClientLevel level, float p) {
        float swap = smooth(0.15F, 0.4F, night);
        if (swap <= 0.01F) {
            return;
        }
        removeVanillaMoon(level, swap);
        float q = p * 2F - 1F;
        float along = Math.copySign((float) Math.pow(Math.abs(q), 1.35), q) * 3.9F;
        float miss = (h(e.seed, 1) - 0.5F) * 0.7F;
        float ang = h(e.seed, 2) * Mth.TWO_PI;
        float sx = along * Mth.cos(ang) - miss * Mth.sin(ang), sz = along * Mth.sin(ang) + miss * Mth.cos(ang);
        float ru = 2.65F, rp = 4.5F;
        fullMoon(1F, 18, (mx, mz, out) -> {
            float d = Mth.sqrt((mx - sx) * (mx - sx) + (mz - sz) * (mz - sz));
            float umbra = smooth(ru + 0.12F, ru - 0.12F, d);
            float pen = smooth(rp, ru, d);
            float lit = 1F - 0.6F * pen;
            // Deeper in the umbra the moon is a darker red; nearer its edge more orange.
            float deep = Mth.clamp((ru - d) / 1.2F, 0F, 1F);
            float cr = Mth.lerp(deep, 0.55F, 0.36F), cg = Mth.lerp(deep, 0.2F, 0.08F), cb = Mth.lerp(deep, 0.1F, 0.05F);
            float rim = (float) Math.exp(-((d - ru) / 0.16F) * ((d - ru) / 0.16F)) * 0.22F;
            out[0] = Mth.lerp(umbra, lit, cr) + rim * 0.2F;
            out[1] = Mth.lerp(umbra, lit, cg) + rim * 0.55F;
            out[2] = Mth.lerp(umbra, lit, cb) + rim * 0.65F;
        }, clear * swap);
        // In totality a faint reddish glow round the moon.
        float total = smooth(ru - 0.9F, ru - 1.2F, Mth.sqrt(sx * sx + sz * sz));
        if (total > 0.01F) {
            glow(0F, -1F, 0F, 22F, 0.8F, 0.25F, 0.12F, 0.12F * total * clear * night);
        }
    }

    /** The full moon at its nearest: bigger and brighter (and warm-coloured low over the horizon). */
    private static void supermoon(ClientLevel level, float edge) {
        float swap = smooth(0.15F, 0.4F, night);
        if (swap <= 0.01F) {
            return;
        }
        removeVanillaMoon(level, swap);
        float low = Mth.clamp(1F - (-sunY) * 4F, 0F, 1F);
        float r = 1F, g = 1F - 0.16F * low, b = 0.97F - 0.35F * low;
        float s = Mth.lerp(edge, 1F, 1.32F);
        fullMoon(s, 1, (mx, mz, out) -> {
            out[0] = r;
            out[1] = g;
            out[2] = b;
        }, clear * swap);
        glow(0F, -1F, 0F, 60F * s, r, g * 0.98F, b * 0.95F, 0.2F * clear * Math.max(0.3F, night));
    }

    /**
     * A planet (or stars, with `stars`) passing behind the moon: it vanishes at the moon's limb (lit or dark) and comes
     * out again on the other side; its glow peeks round the edge as it goes.
     */
    private static void occultation(SkyEvent e, float p, boolean stars) {
        frame(sky);
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        int n = stars ? 6 : 1;
        for (int i = 0; i < n; i++) {
            float miss = stars ? (h(e.seed, 10 + i) - 0.5F) * 1.7F : (h(e.seed, 3) - 0.5F) * 0.9F;
            float offset = stars ? (h(e.seed, 20 + i) - 0.5F) * 2.4F : 0F;
            float mx = (p * 2F - 1F) * 2.8F + offset, mz = miss;
            float r = Mth.sqrt(mx * mx + mz * mz);
            float shown = smooth(0.995F, 1.035F, r);
            float br = stars ? 0.55F + 0.45F * h(e.seed, 30 + i) : 1F;
            float cr = stars ? 0.85F + 0.15F * h(e.seed, 40 + i) : 1F, cg = stars ? 0.9F : 0.95F, cb = stars ? 1F : 0.82F;
            // The halo, hidden where it lies over the moon's disc.
            float size = stars ? 3.2F : 7F;
            maskedGlow(mx * MOON_R, mz * MOON_R, size, cr, cg, cb, 0.55F * br * vis);
            if (shown > 0.01F) {
                float[] d = norm(mx * MOON_R, -100F, mz * MOON_R);
                star(d[0], d[1], d[2], (stars ? 2.2F : 4.5F) * br, cr, cg, cb, shown * vis * br);
            }
        }
    }

    /** A glow quad near the moon (moon-plane coordinates) whose corners over the moon's disc are transparent. */
    private static void maskedGlow(float cx, float cz, float size, float r, float g, float b, float a) {
        begin(GLOW, ADD);
        int n = 10;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float fx0 = (float) i / n, fx1 = (float) (i + 1) / n, fz0 = (float) j / n, fz1 = (float) (j + 1) / n;
                maskedVertex(cx, cz, size, fx0, fz1, r, g, b, a);
                maskedVertex(cx, cz, size, fx1, fz1, r, g, b, a);
                maskedVertex(cx, cz, size, fx1, fz0, r, g, b, a);
                maskedVertex(cx, cz, size, fx0, fz0, r, g, b, a);
            }
        }
    }

    private static void maskedVertex(float cx, float cz, float size, float fx, float fz, float r, float g, float b, float a) {
        float x = cx + (fx * 2F - 1F) * size, z = cz + (fz * 2F - 1F) * size;
        float m = smooth(MOON_R * 0.99F, MOON_R * 1.06F, Mth.sqrt(x * x + z * z));
        vtx(x, -100F, z, fx, fz, r, g, b, a * m);
    }

    // ================================================================================== the sun

    /** A planet (Mercury or Venus) as a small black disc crossing the face of the sun. */
    private static void transit(SkyEvent e, float p) {
        float up = Mth.clamp(sunY * 6F, 0F, 1F) * clear;
        if (up <= 0.01F) {
            return;
        }
        float ang = h(e.seed, 1) * Mth.TWO_PI, miss = (h(e.seed, 2) - 0.5F) * 1.1F;
        float along = (p * 2F - 1F) * 1.2F;
        float px = along * Mth.cos(ang) - miss * Mth.sin(ang), pz = along * Mth.sin(ang) + miss * Mth.cos(ang);
        frame(sky);
        sunMaskedQuad(DISC, px, pz, 0.12F, 0.12F, 0F, 0.012F, 0.01F, 0.01F, 0.95F * up);
    }

    /** The space station's silhouette flicking across the sun in a second or two, now and then. */
    private static void satelliteTransit(SkyEvent e) {
        float up = Mth.clamp(sunY * 6F, 0F, 1F) * clear;
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
        frame(sky);
        sunMaskedQuad(ISS, px, pz, 0.34F, 0.17F, ang, 0.01F, 0.01F, 0.012F, 0.97F * up);
    }

    /**
     * A dark shape on the sun's disc (sun-plane coordinates in sun radii), drawn only where it is over the disc (it
     * can't be seen against the sky beside the sun).
     */
    private static void sunMaskedQuad(ResourceLocation texture, float px, float pz, float hw, float hh, float rot, float r, float g, float b, float a) {
        begin(texture, NORMAL);
        int n = 8;
        float c = Mth.cos(rot), s = Mth.sin(rot);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float a0 = (float) i / n, a1 = (float) (i + 1) / n, b0 = (float) j / n, b1 = (float) (j + 1) / n;
                sunVertex(px, pz, hw, hh, c, s, a0, b1, r, g, b, a);
                sunVertex(px, pz, hw, hh, c, s, a1, b1, r, g, b, a);
                sunVertex(px, pz, hw, hh, c, s, a1, b0, r, g, b, a);
                sunVertex(px, pz, hw, hh, c, s, a0, b0, r, g, b, a);
            }
        }
    }

    private static void sunVertex(float px, float pz, float hw, float hh, float c, float s, float fu, float fv, float r, float g, float b, float a) {
        float lx = (fu * 2F - 1F) * hw, lz = (fv * 2F - 1F) * hh;
        float x = px + lx * c - lz * s, z = pz + lx * s + lz * c;
        float m = smooth(1.0F, 0.96F, Mth.sqrt(x * x + z * z));
        vtx(x * SUN_R, 100F, z * SUN_R, fu, fv, r, g, b, a * m);
    }

    /** Coronal loops and prominences round the sun, and every so often a great cloud of plasma bursting out. */
    private static void coronalMassEjection(SkyEvent e, float edge) {
        float up = Mth.clamp(sunY * 6F, 0F, 1F) * clear * edge;
        if (up <= 0.01F) {
            return;
        }
        frame(sky);
        float s = SUN_R / 0.36F;
        float rot = (float) (t * 0.0004);
        begin(LOOPS, ADD);
        float c = Mth.cos(rot), sn = Mth.sin(rot);
        quad(0F, 100F, 0F, c * s, 0F, sn * s, -sn * s, 0F, c * s, 0F, 0F, 1F, 1F, 1F, 0.85F, 0.6F, 0.85F * up);
        double age = t - e.start;
        int slot = (int) (age / 1500.0);
        float f = (float) ((age - slot * 1500.0) / 700.0);
        if (f < 1F) {
            float ang = h(e.seed, slot) * Mth.TWO_PI;
            float size = SUN_R * (1.2F + 3.2F * f);
            float off = SUN_R * (0.6F + 2.4F * f);
            float a = (1F - f) * (1F - f) * smooth(0F, 0.08F, f) * 0.8F * up;
            begin(SUPERNOVA, ADD);
            quad(Mth.cos(ang) * off, 100F, Mth.sin(ang) * off, size, 0F, 0F, 0F, 0F, size, 0F, 0F, 1F, 1F, 1F, 0.75F, 0.35F, a);
        }
    }

    // ================================================================================== meteors and fireballs

    /** A shower: meteors streaking out from one radiant (or, `sporadic`, single meteors from anywhere). */
    private static void meteors(SkyEvent e, float p, boolean sporadic) {
        float vis = night * clear;
        if (vis <= 0.02F) {
            return;
        }
        frame(world);
        float rate = sporadic ? 0.12F : 0.15F + 0.85F * (float) Math.exp(-((p - 0.5F) / 0.22F) * ((p - 0.5F) / 0.22F));
        float raz = h(e.seed, 1) * Mth.TWO_PI, rel = 0.6F + 0.5F * h(e.seed, 2);
        float[] rad = local(raz, rel);
        float rx = rad[0], ry = rad[1], rz = rad[2];
        double age = t - e.start;
        long first = (long) Math.floor((age - 32) / 5.0), last = (long) Math.floor(age / 5.0);
        begin(METEOR, ADD);
        for (long k = Math.max(0, first); k <= last; k++) {
            if (h(e.seed, k * 7 + 100) > 0.42F * rate) {
                continue;
            }
            double born = k * 5.0 + h(e.seed, k * 7 + 101) * 5.0;
            float life = 10F + h(e.seed, k * 7 + 102) * 16F;
            float f = (float) ((age - born) / life);
            if (f < 0F || f > 1F) {
                continue;
            }
            float ox = rx, oy = ry, oz = rz;
            if (sporadic) {
                float[] o = local(h(e.seed, k * 7 + 106) * Mth.TWO_PI, 0.3F + h(e.seed, k * 7 + 107) * 1.1F);
                ox = o[0];
                oy = o[1];
                oz = o[2];
            }
            float a0 = Mth.DEG_TO_RAD * (10F + h(e.seed, k * 7 + 103) * 50F);
            float travel = Mth.DEG_TO_RAD * (6F + h(e.seed, k * 7 + 104) * 18F);
            float psi = h(e.seed, k * 7 + 105) * Mth.TWO_PI;
            float bright = 0.5F + (float) Math.pow(h(e.seed, k * 7 + 108), 3) * 1.5F;
            int tint = (int) (h(e.seed, k * 7 + 109) * 4F);
            float cr = tint == 1 ? 0.7F : tint == 2 ? 1F : tint == 3 ? 0.82F : 1F;
            float cg = tint == 1 ? 1F : tint == 2 ? 0.75F : tint == 3 ? 0.9F : 0.95F;
            float cb = tint == 1 ? 0.75F : tint == 2 ? 0.5F : tint == 3 ? 1F : 0.82F;
            float headA = a0 + travel * f, tailA = a0 + travel * Math.max(0F, f - 0.55F);
            streak(ox, oy, oz, psi, tailA, headA, 0.28F + 0.22F * bright,
                    cr, cg, cb, (float) Math.pow(Mth.sin(Mth.PI * f), 0.6) * Math.min(1F, bright) * vis);
        }
    }

    /** A streak along the great circle leaving a point o, at angles a0..a1 from it, in the direction psi round it. */
    private static void streak(float ox, float oy, float oz, float psi, float a0, float a1, float width, float r, float g, float b, float a) {
        // Two unit vectors square to o.
        float ex = -oz, ey = 0F, ez = ox;
        float el = Mth.sqrt(ex * ex + ez * ez);
        if (el < 1.0E-3F) {
            ex = 1F;
            ez = 0F;
            el = 1F;
        }
        ex /= el;
        ez /= el;
        float fx = oy * ez - oz * ey, fy = oz * ex - ox * ez, fz = ox * ey - oy * ex;
        float wx = Mth.cos(psi) * ex + Mth.sin(psi) * fx, wy = Mth.cos(psi) * ey + Mth.sin(psi) * fy, wz = Mth.cos(psi) * ez + Mth.sin(psi) * fz;
        float tx = (Mth.cos(a0) * ox + Mth.sin(a0) * wx) * 100F, ty = (Mth.cos(a0) * oy + Mth.sin(a0) * wy) * 100F,
                tz = (Mth.cos(a0) * oz + Mth.sin(a0) * wz) * 100F;
        float hx = (Mth.cos(a1) * ox + Mth.sin(a1) * wx) * 100F, hy = (Mth.cos(a1) * oy + Mth.sin(a1) * wy) * 100F,
                hz = (Mth.cos(a1) * oz + Mth.sin(a1) * wz) * 100F;
        if (hy < -2F) {
            return;
        }
        segment(METEOR, tx, ty, tz, hx, hy, hz, width, r, g, b, a);
    }

    /** A textured strip from tail (u = 0) to head (u = 1), turned to face the viewer at the origin. */
    private static void segment(ResourceLocation texture, float tx, float ty, float tz, float hx, float hy, float hz, float width,
                                float r, float g, float b, float a) {
        if (a <= 0.003F) {
            return;
        }
        float dx = hx - tx, dy = hy - ty, dz = hz - tz;
        float mx = (hx + tx) * 0.5F, my = (hy + ty) * 0.5F, mz = (hz + tz) * 0.5F;
        float cx = dy * mz - dz * my, cy = dz * mx - dx * mz, cz = dx * my - dy * mx;
        float l = Mth.sqrt(cx * cx + cy * cy + cz * cz) + 1.0E-5F;
        cx *= width / l;
        cy *= width / l;
        cz *= width / l;
        vtx(tx - cx, ty - cy, tz - cz, 0F, 1F, r, g, b, a);
        vtx(hx - cx, hy - cy, hz - cz, 1F, 1F, r, g, b, a);
        vtx(hx + cx, hy + cy, hz + cz, 1F, 0F, r, g, b, a);
        vtx(tx + cx, ty + cy, tz + cz, 0F, 0F, r, g, b, a);
    }

    /** Fireball k of an event: when it starts and how long it burns (ticks), or null when that slot has none. */
    private static double[] fireball(SkyEvent e, long k) {
        boolean debris = e.type == SkyEvent.DEBRIS_REENTRY;
        if (debris) {
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

    /** Fireballs (bolides) burning across the sky and bursting at the end; or a satellite falling back in pieces. */
    private static void fireballs(SkyEvent e) {
        float vis = Math.max(night, 0.3F) * clear;
        frame(world);
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
            float az0 = h(e.seed, k * 5 + 304) * Mth.TWO_PI;
            float turn = (h(e.seed, k * 5 + 305) - 0.5F) * (debris ? 1.4F : 1.1F);
            float el0 = debris ? 0.45F : 0.7F + h(e.seed, k * 5 + 306) * 0.5F, el1 = debris ? 0.22F : 0.08F + h(e.seed, k * 5 + 307) * 0.15F;
            int pieces = debris ? 9 : 1 + (f > 0.78F ? 4 : 0);
            for (int i = 0; i < pieces; i++) {
                // The pieces lag behind each other and drift apart.
                float lag = debris ? i * 0.035F : i == 0 ? 0F : (f - 0.78F) * 0.25F * (1 + i * 0.3F);
                float fi = Mth.clamp(f - lag, 0F, 1F);
                float spread = debris ? (h(e.seed, 400 + i) - 0.5F) * 0.06F * fi : (h(e.seed, k * 5 + 310 + i) - 0.5F) * 0.08F * (f - 0.78F);
                float az = az0 + turn * fi + spread, el = Mth.lerp(fi, el0, el1) + spread * 0.5F;
                float[] hd = local(az, el);
                float hx = hd[0] * 100F, hy = hd[1] * 100F, hz = hd[2] * 100F;
                float azb = az0 + turn * Math.max(0F, fi - 0.08F) + spread, elb = Mth.lerp(Math.max(0F, fi - 0.08F), el0, el1) + spread * 0.5F;
                float[] tl = local(azb, elb);
                float dx = hx - tl[0] * 100F, dy = hy - tl[1] * 100F, dz = hz - tl[2] * 100F;
                float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F;
                float len = (debris ? 8F : 22F) * (i == 0 ? 1F : 0.4F) * smooth(0F, 0.15F, f);
                float tx = hx - dx / dl * len, ty = hy - dy / dl * len, tz = hz - dz / dl * len;
                float bright = (debris ? 0.75F : 0.6F + 0.4F * smooth(0.7F, 0.9F, f)) * Mth.sin(Mth.PI * fi) * (i == 0 ? 1F : 0.7F);
                if (hy < -2F) {
                    continue;
                }
                begin(FIREBALL, ADD);
                segment(FIREBALL, tx, ty, tz, hx, hy, hz, len * 0.22F + 0.5F, 1F, 1F, 1F, bright * vis);
                float[] n = norm(hx, hy, hz);
                glow(n[0], n[1], n[2], (debris ? 3F : 6F) + 6F * bright, 1F, 0.75F, 0.45F, 0.4F * bright * vis);
            }
        }
    }

    // ================================================================================== comets, planets, stars

    /** A comet among the stars: the head, and the tails streaming away from the sun. */
    private static void comet(SkyEvent e, float edge, float strength, boolean evening) {
        float vis = night * clear * edge * strength;
        if (vis <= 0.01F) {
            return;
        }
        frame(sky);
        float[] d;
        if (evening) {
            // A faint comet low in the west after sunset (not far from the sun).
            float side = h(e.seed, 5) < 0.5F ? -1F : 1F;
            d = norm((h(e.seed, 6) - 0.5F) * 0.3F, Mth.cos(0.75F), side * Mth.sin(0.75F));
        } else {
            d = nightDir(e.seed, 1, 0.55F, 0.6F);
        }
        float dx = d[0], dy = d[1], dz = d[2];
        cometAt(dx, dy, dz, 0F, -1F, 0F, evening ? 24F : 58F, 1F, 1F, 1F, vis);
    }

    /** The comet sprite with its head at d and its tails along the direction `away` (made square to d). */
    private static void cometAt(float dx, float dy, float dz, float ax, float ay, float az, float length, float r, float g, float b, float a) {
        float dot = ax * dx + ay * dy + az * dz;
        float tx = ax - dx * dot, ty = ay - dy * dot, tz = az - dz * dot;
        float l = Mth.sqrt(tx * tx + ty * ty + tz * tz);
        if (l < 1.0E-3F) {
            tx = 1F;
            ty = 0F;
            tz = 0F;
            l = 1F;
        }
        tx /= l;
        ty /= l;
        tz /= l;
        // The head is 7% along the texture; the centre of the sprite lies down the tail from it.
        float half = length * 0.5F;
        float off = length * 0.43F;
        float cx = dx * 100F + tx * off, cy = dy * 100F + ty * off, cz = dz * 100F + tz * off;
        float ux = ty * dz - tz * dy, uy = tz * dx - tx * dz, uz = tx * dy - ty * dx;
        begin(COMET, ADD);
        quad(cx, cy, cz, tx * half, ty * half, tz * half, ux * half * 0.5F, uy * half * 0.5F, uz * half * 0.5F, 0F, 0F, 1F, 1F, r, g, b, a);
    }

    /** A comet gliding visibly across the sky and swerving off its course on the way. */
    private static void wanderingComet(SkyEvent e, float p, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        frame(sky);
        float side = h(e.seed, 2) - 0.5F;
        float x0 = side * 0.6F + 0.25F * Mth.sin(Mth.PI * p) + 0.3F * smooth(0.45F, 0.62F, p) * Math.signum(side + 0.01F);
        float z0 = Mth.lerp(p, -0.75F, 0.75F);
        float p2 = Math.min(1F, p + 0.01F);
        float x1 = side * 0.6F + 0.25F * Mth.sin(Mth.PI * p2) + 0.3F * smooth(0.45F, 0.62F, p2) * Math.signum(side + 0.01F);
        float z1 = Mth.lerp(p2, -0.75F, 0.75F);
        float[] a = norm(x0, -1F, z0);
        float ax = a[0], ay = a[1], az = a[2];
        float[] b = norm(x1, -1F, z1);
        float mx = ax - b[0], my = ay - b[1], mz = az - b[2];
        float ml = Mth.sqrt(mx * mx + my * my + mz * mz) + 1.0E-6F;
        // The tail trails behind it, bent away from the sun.
        cometAt(ax, ay, az, mx / ml * 0.65F, my / ml * 0.65F - 0.35F, mz / ml * 0.65F, 48F, 0.85F, 0.92F, 1F, vis);
        glow(ax, ay, az, 5F, 0.7F, 0.85F, 1F, 0.5F * vis);
    }

    /** Two (or three) planets close together in the sky. */
    private static void conjunction(SkyEvent e, float p, float edge, boolean triple) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        frame(sky);
        float[] d0 = nightDir(e.seed, 1, 0.12F, 0.55F);
        float bx = d0[0], by = d0[1], bz = d0[2];
        // Along the ecliptic (the z direction in the sky frame) and across it.
        basis(bx, by, bz, 0F, 0F, 1F);
        float ex = R[0], ey = R[1], ez = R[2], nx = U[0], ny = U[1], nz = U[2];
        float sep = (2.5F + 4F * Math.abs(p - 0.5F)) * Mth.DEG_TO_RAD;
        if (triple) {
            place(JUPITER, bx, by, bz, ex, ey, ez, nx, ny, nz, -sep, 0.2F * sep, 2.3F, vis, 0.95F, 0.85F, 0.7F);
            place(-1, bx, by, bz, ex, ey, ez, nx, ny, nz, 0F, -0.15F * sep, 1.6F, vis, 0.95F, 0.88F, 0.7F);
            place(MARS, bx, by, bz, ex, ey, ez, nx, ny, nz, sep * 0.95F, 0.3F * sep, 1.1F, vis, 1F, 0.6F, 0.45F);
        } else {
            place(JUPITER, bx, by, bz, ex, ey, ez, nx, ny, nz, -sep * 0.5F, 0F, 2.3F, vis, 0.95F, 0.85F, 0.7F);
            place(MARS, bx, by, bz, ex, ey, ez, nx, ny, nz, sep * 0.5F, 0.35F * sep, 1.2F, vis, 1F, 0.6F, 0.45F);
        }
    }

    /** A planet (cell, or -1 for Saturn) at angles (along, across) from a centre direction, with its glow. */
    private static void place(int cell, float bx, float by, float bz, float ex, float ey, float ez, float nx, float ny, float nz,
                              float along, float across, float radius, float vis, float gr, float gg, float gb) {
        float[] d = norm(bx + ex * along + nx * across, by + ey * along + ny * across, bz + ez * along + nz * across);
        float dx = d[0], dy = d[1], dz = d[2];
        glow(dx, dy, dz, radius * 3.2F, gr, gg, gb, 0.32F * vis);
        if (cell < 0) {
            saturn(dx, dy, dz, radius, ex, ey, ez, vis);
        } else {
            planet(cell, dx, dy, dz, radius, ex, ey, ez, vis);
        }
    }

    /** The planets strung out along the ecliptic in a line across the night sky. */
    private static void alignment(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        frame(sky);
        int[] cells = {NEPTUNE, MARS, VENUS, JUPITER, URANUS, -1};
        float[] sizes = {1F, 1.15F, 1.45F, 2.3F, 1.05F, 1.7F};
        float[][] glowC = {{0.5F, 0.65F, 1F}, {1F, 0.6F, 0.45F}, {1F, 0.97F, 0.85F}, {0.95F, 0.85F, 0.7F}, {0.6F, 0.9F, 0.95F}, {0.95F, 0.88F, 0.7F}};
        float shift = (h(e.seed, 1) - 0.5F) * 0.5F;
        for (int i = 0; i < cells.length; i++) {
            float phi = (-48F + i * 19F) * Mth.DEG_TO_RAD + shift;
            float x = 0.05F * Mth.sin(i * 1.7F) + (h(e.seed, 2) - 0.5F) * 0.15F;
            float[] d = norm(x, -Mth.cos(phi), Mth.sin(phi));
            float dx = d[0], dy = d[1], dz = d[2];
            glow(dx, dy, dz, sizes[i] * 3.2F, glowC[i][0], glowC[i][1], glowC[i][2], 0.3F * vis);
            if (cells[i] < 0) {
                saturn(dx, dy, dz, sizes[i], 0F, Mth.sin(phi), Mth.cos(phi), vis);
            } else {
                planet(cells[i], dx, dy, dz, sizes[i], 0F, Mth.sin(phi), Mth.cos(phi), vis);
            }
        }
    }

    /** A star suddenly blazing up, outshining everything (it can be seen in daylight), then slowly fading for days. */
    private static void supernova(SkyEvent e, float p) {
        float b = p < 0.02F ? p / 0.02F : p < 0.08F ? 1F : Math.max(0.12F, (float) Math.exp(-(p - 0.08F) * 4F));
        frame(sky);
        float[] d = nightDir(e.seed, 1, 0.8F, 0.8F);
        float dx = d[0], dy = d[1], dz = d[2];
        float dark = night * clear, seen = Math.max(dark, b > 0.5F ? 0.45F * clear : 0F);
        float neb = Math.min(1F, p * 12F) * dark;
        begin(SUPERNOVA, ADD);
        sprite(dx, dy, dz, 16F + 26F * p, 16F + 26F * p, 1F, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 1F, 1F, 0.55F * neb);
        glow(dx, dy, dz, 26F * b, 0.85F, 0.88F, 1F, 0.55F * b * seen);
        star(dx, dy, dz, 18F * b + 5F, 0.92F, 0.95F, 1F, b * seen);
    }

    /** A recurrent nova: a reddish star flaring up again and again, each time fading slowly, in its shell of gas. */
    private static void recurrentNova(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        frame(sky);
        float[] d = nightDir(e.seed, 1, 0.7F, 0.7F);
        float dx = d[0], dy = d[1], dz = d[2];
        double age = t - e.start;
        float ph = (float) ((age % 2400.0) / 2400.0);
        float o = ph < 0.04F ? ph / 0.04F : (float) Math.exp(-(ph - 0.04F) * 7F);
        begin(NOVA, ADD);
        sprite(dx, dy, dz, 12F + 6F * ph, 12F + 6F * ph, 1F, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 0.8F, 0.7F, (0.15F + 0.35F * o) * vis);
        glow(dx, dy, dz, 4F + 14F * o, 1F, 0.45F, 0.22F, (0.35F + 0.5F * o) * vis);
        star(dx, dy, dz, 3F + 13F * o, 1F, 0.62F, 0.4F, (0.4F + 0.6F * o) * vis);
    }

    /** A red dwarf star flaring now and then, with a planet beside it. */
    private static void starFlares(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        frame(sky);
        float[] d = nightDir(e.seed, 1, 0.7F, 0.7F);
        float dx = d[0], dy = d[1], dz = d[2];
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
        glow(dx, dy, dz, 7F + 10F * flare, 1F, 0.32F, 0.15F, (0.45F + 0.45F * flare) * vis);
        begin(NOVA, ADD);
        sprite(dx, dy, dz, 5F + 7F * flare, 5F + 7F * flare, 1F, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 0.55F, 0.3F, 0.6F * flare * vis);
        star(dx, dy, dz, 3F + 9F * flare, 1F, 0.55F + 0.3F * flare, 0.35F + 0.4F * flare, (0.55F + 0.45F * flare) * vis);
        basis(dx, dy, dz, 0F, 0F, 1F);
        float[] pd = norm(dx + R[0] * 0.05F - U[0] * 0.025F, dy + R[1] * 0.05F - U[1] * 0.025F, dz + R[2] * 0.05F - U[2] * 0.025F);
        planet(MARS, pd[0], pd[1], pd[2], 0.9F, 1F, 0F, 0F, 0.85F * vis);
    }

    // ================================================================================== the aurora

    /** Curtains of green light with red and violet tops, waving and rippling over the northern sky. */
    private static void aurora(SkyEvent e, float edge, boolean storm) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        frame(world);
        begin(AURORA, ADD);
        int ribbons = storm ? 6 : 3;
        int n = 56;
        float time = (float) (t - e.start);
        float speed = storm ? 2.2F : 1F;
        for (int i = 0; i < ribbons; i++) {
            float span = storm ? 2.4F : 1.35F;
            float pulse = 0.55F + 0.45F * Mth.sin(time * 0.0021F * speed + i * 1.9F + h(e.seed, i) * 6F);
            float inten = vis * pulse * (storm ? 1.25F : 0.9F) * (0.8F + 0.4F * h(e.seed, i + 10));
            float dist = storm ? (i == 0 ? 220F : 420F + i * 160F) : 700F + i * 260F;
            float base = (storm ? 120F : 150F) + i * 25F;
            float tall = (storm ? 520F : 280F) + 60F * h(e.seed, i + 20);
            float az0 = (h(e.seed, i + 30) - 0.5F) * 0.5F;
            float prevX = 0, prevY = 0, prevZ = 0, prevTX = 0, prevTY = 0, prevTZ = 0, prevU = 0;
            for (int s = 0; s <= n; s++) {
                float f = (float) s / n;
                float az = az0 + (f - 0.5F) * 2F * span + 0.06F * Mth.sin(f * 17F + time * 0.01F * speed + i)
                        + 0.12F * Mth.sin(f * 5F - time * 0.004F * speed + i * 2F);
                float dd = dist * (1F + 0.3F * Mth.sin(f * 4.5F + time * 0.003F * speed + i * 1.3F));
                float top = base + tall * (0.75F + 0.25F * Mth.sin(f * 7F + time * 0.005F * speed + i));
                float bx = Mth.sin(az) * dd, bz = -Mth.cos(az) * dd;
                float[] lo = norm(bx, base, bz);
                float lx = lo[0] * 100F, ly = lo[1] * 100F, lz = lo[2] * 100F;
                float[] hi = norm(bx, top, bz);
                float hx = hi[0] * 100F, hy = hi[1] * 100F, hz = hi[2] * 100F;
                float u = f * 5F + time * 0.0007F * speed * (i + 1);
                // The ends of each curtain fade out.
                float endFade = smooth(0F, 0.12F, f) * smooth(1F, 0.88F, f);
                if (s > 0) {
                    float a = inten * endFade;
                    float gr = storm ? 0.3F : 0.2F, gg = 1F, gb = storm ? 0.55F : 0.45F;
                    float tr = storm ? 0.85F : 0.45F, tg = storm ? 0.25F : 0.75F, tb = storm ? 1F : 0.65F;
                    vtx(prevX, prevY, prevZ, prevU, 1F, gr, gg, gb, a);
                    vtx(lx, ly, lz, u, 1F, gr, gg, gb, a);
                    vtx(hx, hy, hz, u, 0F, tr, tg, tb, a * 0.85F);
                    vtx(prevTX, prevTY, prevTZ, prevU, 0F, tr, tg, tb, a * 0.85F);
                }
                prevX = lx;
                prevY = ly;
                prevZ = lz;
                prevTX = hx;
                prevTY = hy;
                prevTZ = hz;
                prevU = u;
            }
        }
        // A green glow low over the northern horizon.
        glow(0F, 0.12F, -1F, storm ? 75F : 55F, 0.2F, 1F, 0.45F, 0.12F * vis);
    }

    // ================================================================================== satellites

    private static final float[] P = new float[3], V = new float[3];

    /**
     * A satellite pass: along the great circle that is highest (at `emax`) in the direction square to the heading.
     * Fills P with the direction at angle `th` along it (0 = the highest point) and V with its direction of motion.
     */
    private static void pass(float heading, float emax, float th) {
        float hx = Mth.cos(emax) * Mth.sin(heading + Mth.HALF_PI), hy = Mth.sin(emax), hz = -Mth.cos(emax) * Mth.cos(heading + Mth.HALF_PI);
        float fx = Mth.sin(heading), fz = -Mth.cos(heading);
        float c = Mth.cos(th), s = Mth.sin(th);
        P[0] = c * hx + s * fx;
        P[1] = c * hy;
        P[2] = c * hz + s * fz;
        V[0] = -s * hx + c * fx;
        V[1] = -s * hy;
        V[2] = -s * hz + c * fz;
    }

    /** The space station sailing across the sky, lit by the sun after dark. */
    private static void iss(SkyEvent e, float p) {
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        frame(world);
        float th = (p * 2F - 1F) * 82F * Mth.DEG_TO_RAD;
        pass(h(e.seed, 1) * Mth.TWO_PI, (40F + h(e.seed, 2) * 45F) * Mth.DEG_TO_RAD, th);
        float px = P[0], py = P[1], pz = P[2];
        float a = vis * smooth(0F, 0.12F, py);
        float warm = 1F - smooth(0.05F, 0.3F, py) * 0.3F;
        glow(px, py, pz, 9F, 1F, 0.95F * warm, 0.85F * warm, 0.22F * a);
        begin(ISS, NORMAL);
        sprite(px, py, pz, 6.2F, 3.1F, V[0], V[1], V[2], 0F, 0F, 1F, 1F, 1F, 0.97F * warm, 0.9F * warm, a);
        star(px, py, pz, 2.2F, 1F, 1F, 0.95F, 0.5F * a);
    }

    /** A train of satellites in a line, gliding across the sky one after another and fading into the Earth's shadow. */
    private static void starlink(SkyEvent e, float p) {
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        frame(world);
        float heading = h(e.seed, 1) * Mth.TWO_PI, emax = (35F + h(e.seed, 2) * 40F) * Mth.DEG_TO_RAD;
        float head = (p * 2F - 1F) * 95F * Mth.DEG_TO_RAD;
        for (int i = 0; i < 28; i++) {
            float th = head - i * 1.15F * Mth.DEG_TO_RAD;
            if (th < -85F * Mth.DEG_TO_RAD || th > 55F * Mth.DEG_TO_RAD) {
                continue;
            }
            pass(heading, emax, th);
            float px = P[0], py = P[1], pz = P[2];
            float shadow = smooth(55F * Mth.DEG_TO_RAD, 40F * Mth.DEG_TO_RAD, th);
            float a = vis * smooth(0F, 0.1F, py) * shadow * (0.6F + 0.4F * h(e.seed, i + 10));
            star(px, py, pz, 1.5F, 1F, 1F, 0.97F, a);
            glow(px, py, pz, 1.4F, 1F, 1F, 0.95F, 0.6F * a);
        }
    }

    /** A satellite whose panels catch the sun for a few seconds: it flares up far brighter than any star, then fades. */
    private static void satelliteFlare(SkyEvent e, float p) {
        float vis = night * clear;
        if (vis <= 0.01F) {
            return;
        }
        frame(world);
        float th = (p * 2F - 1F) * 70F * Mth.DEG_TO_RAD;
        pass(h(e.seed, 1) * Mth.TWO_PI, (45F + h(e.seed, 2) * 40F) * Mth.DEG_TO_RAD, th);
        float px = P[0], py = P[1], pz = P[2];
        float a = vis * smooth(0F, 0.1F, py);
        float flare = (float) Math.exp(-((p - 0.5F) / 0.035F) * ((p - 0.5F) / 0.035F));
        begin(SATELLITE, NORMAL);
        sprite(px, py, pz, 2.6F, 1.3F, V[0], V[1], V[2], 0F, 0F, 1F, 1F, 0.95F, 0.95F, 0.95F, a * 0.9F);
        star(px, py, pz, 1.8F + 16F * flare, 1F, 1F, 0.96F, (0.45F + 0.55F * flare) * a);
        glow(px, py, pz, 3F + 22F * flare, 1F, 0.97F, 0.9F, 0.6F * flare * a);
    }

    // ================================================================================== above the storms

    /** Red sprites, blue jets and elves flashing high above distant thunderstorms. */
    private static void upperLightning(Vec3 cam) {
        if (ClientSkyEvents.FLASHES.isEmpty() || night <= 0.05F) {
            return;
        }
        frame(world);
        for (double[] f : ClientSkyEvents.FLASHES) {
            float age = (float) (t - f[4]);
            float a = age < 0F ? 0F : (float) Math.exp(-age / 2.2F) * night;
            if (a <= 0.01F) {
                continue;
            }
            float x = (float) (f[0] - cam.x), z = (float) (f[2] - cam.z);
            float deck = (float) (f[1] - cam.y);
            float size = (float) f[5];
            int kind = (int) f[3];
            if (kind == 0) {
                // Sprites: a cluster of red jellyfish shapes 50-90 km up, well above the cloud tops.
                for (int i = 0; i < 3; i++) {
                    float ox = (i - 1) * 110F * size, cy = deck + 520F * size;
                    float[] d = norm(x + ox, cy, z);
                    float dist = Mth.sqrt((x + ox) * (x + ox) + cy * cy + z * z);
                    float hh = 260F * size / dist * 100F, hw = hh * 0.5F;
                    begin(SPRITE, ADD);
                    spriteUpright(d[0], d[1], d[2], hw, hh, 0F, 0F, 1F, 1F, 1F, 1F, 1F, a * (i == 1 ? 1F : 0.7F));
                }
            } else if (kind == 1) {
                // A blue jet shooting up out of the cloud top.
                float cy = deck + 260F * size;
                float[] d = norm(x, cy, z);
                float dist = Mth.sqrt(x * x + cy * cy + z * z);
                float hh = 180F * size / dist * 100F;
                begin(JET, ADD);
                spriteUpright(d[0], d[1], d[2], hh * 0.25F, hh, 0F, 0F, 1F, 1F, 1F, 1F, 1F, a);
            } else {
                // An elve: a red ring spreading out very high up.
                float cy = deck + 800F;
                float r = 300F + age * 120F;
                float[] d = norm(x, cy, z);
                float dist = Mth.sqrt(x * x + cy * cy + z * z);
                float s = r / dist * 100F;
                begin(NOVA, ADD);
                // A flat ring seen from the side: wide across the view, squashed up and down.
                sprite(d[0], d[1], d[2], s, s * (Math.abs(d[1]) * 0.6F + 0.12F), -d[2], 0F, d[0], 0F, 0F, 1F, 1F, 1F, 0.25F, 0.2F, a * 0.8F);
            }
        }
    }

    // ================================================================================== the Milky Way

    /** The Milky Way arching across a dark sky, and the faint cone of the zodiacal light after dusk. */
    private static void dustGlow(SkyEvent e, float edge) {
        float vis = night * clear * edge;
        if (vis <= 0.01F) {
            return;
        }
        frame(sky);
        // A great circle tilted well away from the ecliptic.
        float tilt = (55F + h(e.seed, 1) * 25F) * Mth.DEG_TO_RAD, spin = h(e.seed, 2) * Mth.TWO_PI;
        float ax = Mth.cos(spin), ay = 0F, az = Mth.sin(spin);
        float bx = -Mth.sin(spin) * Mth.cos(tilt), by = Mth.sin(tilt), bz = Mth.cos(spin) * Mth.cos(tilt);
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float w = 0.3F;
        float cw = Mth.cos(w), sw = Mth.sin(w);
        begin(MILKY_WAY, ADD);
        int n = 72;
        for (int i = 0; i < n; i++) {
            float t0 = Mth.TWO_PI * i / n, t1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            float p0x = c0 * ax + s0 * bx, p0y = c0 * ay + s0 * by, p0z = c0 * az + s0 * bz;
            float p1x = c1 * ax + s1 * bx, p1y = c1 * ay + s1 * by, p1z = c1 * az + s1 * bz;
            float u0 = (float) i / n, u1 = (float) (i + 1) / n;
            float a = 0.8F * vis;
            vtx((p0x * cw - nx * sw) * 100F, (p0y * cw - ny * sw) * 100F, (p0z * cw - nz * sw) * 100F, u0, 1F, 1F, 1F, 1F, a);
            vtx((p1x * cw - nx * sw) * 100F, (p1y * cw - ny * sw) * 100F, (p1z * cw - nz * sw) * 100F, u1, 1F, 1F, 1F, 1F, a);
            vtx((p1x * cw + nx * sw) * 100F, (p1y * cw + ny * sw) * 100F, (p1z * cw + nz * sw) * 100F, u1, 0F, 1F, 1F, 1F, a);
            vtx((p0x * cw + nx * sw) * 100F, (p0y * cw + ny * sw) * 100F, (p0z * cw + nz * sw) * 100F, u0, 0F, 1F, 1F, 1F, a);
        }
        // Zodiacal light: a faint glowing cone along the ecliptic, not far from the set sun.
        float dusk = smooth(0F, -0.25F, sunY) * smooth(-0.75F, -0.4F, sunY);
        if (dusk > 0.01F) {
            for (int i = 0; i < 4; i++) {
                float ang = (32F + i * 11F) * Mth.DEG_TO_RAD;
                for (float side : new float[]{-1F, 1F}) {
                    float[] d = norm(0F, Mth.cos(ang), side * Mth.sin(ang));
                    glow(d[0], d[1], d[2], 26F - i * 4F, 1F, 0.95F, 0.85F, 0.06F * dusk * vis);
                }
            }
        }
    }
}
