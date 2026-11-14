package com.realweather.client.space;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL11;

/**
 * The two ray-traced things out in space: planets made of blocks (each ray followed cell by cell through the blocks
 * of the planet's ground) and the black hole (each ray followed through the curved space round it). Both are drawn
 * as a quad in screen space over the part of the view they cover; if this machine can't compile them they are left
 * out (the smooth planets and a bright point stay).
 */
public final class SpaceShaders {
    public static final ResourceLocation BLOCKS = RealWeather.id("textures/environment/space3d/planet_blocks.png");
    /** On the test machine's software renderer the ray tracing is cut short (only to check it in screenshots). */
    private static final boolean TESTING = Boolean.getBoolean("realweather.selftest");
    private static ShaderInstance voxel, hole, air, gas, blit, giant, starFace, rings;
    private static final ResourceLocation NOISE = RealWeather.id("textures/environment/space3d/nebula_noise.png");
    private static final ResourceLocation RING_MAP = RealWeather.id("textures/environment/space3d/rings.png");
    private static boolean broken;
    /** Where the ray-traced passes are worked out at a fraction of the screen's resolution, then put on the screen. */
    private static TextureTarget low;
    /** The fraction of the screen's resolution they are worked out at (set by the frame rate when on AUTO). */
    private static float scale = -1F;
    private static long lastFrame;
    private static double frameMs = 16;
    private static boolean traced, wasTraced;
    private static int mobile = -1;
    /** How many passes of each kind were drawn (for tests). */
    private static final int[] COUNT = new int[8];

    public static String stats() {
        String out = String.format(java.util.Locale.ROOT, "voxel=%d hole=%d air=%d nebula=%d gas=%d star=%d rings=%d scale=%.2f ms=%.0f", COUNT[0],
                COUNT[1], COUNT[2], COUNT[3], COUNT[4], COUNT[5], COUNT[6], scale, frameMs);
        java.util.Arrays.fill(COUNT, 0);
        return out;
    }

    private SpaceShaders() {}

    public static void register(RegisterShadersEvent event) {
        load(event, "voxel_planet", s -> voxel = s);
        load(event, "black_hole", s -> hole = s);
        load(event, "atmosphere", s -> air = s);
        load(event, "nebula", s -> gas = s);
        load(event, "space_blit", s -> blit = s);
        load(event, "gas_planet", s -> giant = s);
        load(event, "star_surface", s -> starFace = s);
        load(event, "ring_particles", s -> rings = s);
    }

    /** One shader; if this machine can't compile it, that pass is left out (the rest goes on). */
    private static void load(RegisterShadersEvent event, String name, java.util.function.Consumer<ShaderInstance> set) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id(name), DefaultVertexFormat.POSITION_TEX), set);
        } catch (Exception | LinkageError e) {
            RealWeather.LOG.warn("Space shader {} unavailable (it did not load)", name, e);
            set.accept(null);
        }
    }

    /** A phone or tablet (its GPU is far weaker than a PC's, and stops the game if a frame takes too long). */
    public static boolean mobile() {
        if (mobile < 0) {
            boolean m = false;
            try {
                m = new java.io.File("/system/build.prop").exists() || System.getenv("POJAV_RENDERER") != null
                        || System.getenv("POJAV_NATIVEDIR") != null;
            } catch (Throwable ignored) {
            }
            try {
                String r = (com.mojang.blaze3d.platform.GlUtil.getRenderer() + " " + com.mojang.blaze3d.platform.GlUtil.getOpenGLVersion() + " "
                        + com.mojang.blaze3d.platform.GlUtil.getVendor()).toLowerCase(java.util.Locale.ROOT);
                for (String k : new String[]{"adreno", "mali", "powervr", "immortalis", "xclipse", "mobileglues", "gl4es", "ltw", "turnip",
                        "opengl es", "angle", "vivante", "videocore"}) {
                    m |= r.contains(k);
                }
            } catch (Throwable ignored) {
            }
            mobile = m ? 1 : 0;
            RealWeather.LOG.info("Space rendering for a {} GPU", m ? "mobile" : "desktop");
        }
        return mobile == 1;
    }

    /** Off: no ray tracing at all (the smooth spheres, glows and particles stay). */
    public static boolean enabled() {
        return com.realweather.ClientConfig.SPACE_QUALITY.get() != com.realweather.ClientConfig.SpaceQuality.OFF;
    }

    /** Called once a frame: keeps the ray tracing as sharp as the frame rate allows. */
    public static void frame() {
        long now = System.nanoTime();
        if (lastFrame != 0 && wasTraced) {
            double ms = Math.min(500, (now - lastFrame) / 1.0E6);
            frameMs += (ms - frameMs) * 0.15;
        }
        lastFrame = now;
        wasTraced = traced;
        traced = false;
        com.realweather.ClientConfig.SpaceQuality q = com.realweather.ClientConfig.SPACE_QUALITY.get();
        boolean m = mobile();
        float max = m ? 0.7F : 1F, min = m ? 0.22F : 0.35F;
        switch (q) {
            case LOW -> scale = m ? 0.25F : 0.4F;
            case MEDIUM -> scale = m ? 0.38F : 0.6F;
            case HIGH -> scale = m ? 0.55F : 0.8F;
            case ULTRA -> scale = 1F;
            default -> {
                if (scale < 0) {
                    scale = m ? 0.3F : 0.7F;
                }
                if (wasTraced) {
                    if (frameMs > 45) {
                        scale = Math.max(min, scale * 0.92F);
                    } else if (frameMs < 24) {
                        scale = Math.min(max, scale * 1.02F);
                    }
                }
            }
        }
    }

    /** How many ray steps a pass over this part of the screen may take (so no frame takes the GPU too long). */
    private static float budget(float steps, float least, float x0, float y0, float x1, float y1) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        float s = Math.max(0.2F, scale < 0 ? 0.5F : scale);
        double px = Math.max(1, (x1 - x0) * 0.5 * main.width * s) * Math.max(1, (y1 - y0) * 0.5 * main.height * s);
        double total = mobile() ? 2.2E7 : 2.5E8;
        float cap = (float) Math.max(least, total / px);
        if (TESTING) {
            cap = Math.min(cap, steps * 0.4F + least * 0.6F);
        }
        return Math.min(steps, cap);
    }

    public static boolean voxelReady() {
        return voxel != null && !broken && enabled();
    }

    public static boolean holeReady() {
        return hole != null && !broken && enabled();
    }

    public static boolean gasReady() {
        return giant != null && !broken && enabled();
    }

    public static boolean starReady() {
        return starFace != null && !broken && enabled();
    }

    public static boolean ringsReady() {
        return rings != null && !broken && enabled();
    }

    /** What a planet of blocks looks like: its map, size in blocks, how rugged, its sea, blocks and light. */
    public static final class Planet {
        public ResourceLocation map;
        public float radius = 64F, relief = 6F, sea = -1F, ambient = 0.02F, glow = 0F, alpha = 1F, steps = 96F;
        public float[] tiles = {1F, 0F, 0F, 7F};
        public boolean gas;
        public float air;
        public float airR = 0.45F, airG = 0.65F, airB = 1F, airHeight = 80F;
        public float lightR = 1F, lightG = 1F, lightB = 1F;
        /** Blocks per screen pixel at the start of the ray, and how fast that grows along it (for the texture detail). */
        public float pixelSize, pixelGrow;
        /** Height of the hills (blocks). */
        public float hills = 2F;
        /** The block of its snow and ice caps. */
        public float snow = 4F;
        /** Light direction (planet frame). */
        public double lx, ly = 1, lz;
    }

    /**
     * Draws a planet of blocks over a rectangle of the screen (normalised device coordinates x0..x1, y0..y1). Each
     * pixel's ray (planet frame, in blocks) starts at o + ox * a + oy * b and runs along d + dx * a + dy * b, where a and
     * b go from -1 to 1 across the rectangle.
     */
    public static void voxel(Planet p, float x0, float y0, float x1, float y1, double[] o, double[] ox, double[] oy, double[] d, double[] dx,
                             double[] dy) {
        if (!voxelReady() || p.map == null || p.alpha <= 0.01F) {
            return;
        }
        try {
            Space3D.flush();
            ShaderInstance s = voxel;
            set(s, "RayO", o);
            set(s, "RayOx", ox);
            set(s, "RayOy", oy);
            set(s, "RayD", d);
            set(s, "RayDx", dx);
            set(s, "RayDy", dy);
            s.safeGetUniform("Radius").set(p.radius);
            s.safeGetUniform("Relief").set(p.relief);
            s.safeGetUniform("Sea").set(p.sea);
            double l = Math.sqrt(p.lx * p.lx + p.ly * p.ly + p.lz * p.lz) + 1.0E-12;
            s.safeGetUniform("LightDir").set((float) (p.lx / l), (float) (p.ly / l), (float) (p.lz / l));
            s.safeGetUniform("LightColor").set(p.lightR, p.lightG, p.lightB);
            s.safeGetUniform("Ambient").set(p.ambient);
            s.safeGetUniform("Glow").set(p.glow);
            s.safeGetUniform("Alpha").set(Math.min(1F, p.alpha));
            s.safeGetUniform("MaxSteps").set(budget(p.steps, 48F, x0, y0, x1, y1));
            s.safeGetUniform("Tiles").set(p.tiles[0], p.tiles[1], p.tiles[2], p.tiles[3]);
            s.safeGetUniform("Gas").set(p.gas ? 1F : 0F);
            s.safeGetUniform("Air").set(p.air);
            s.safeGetUniform("AirColor").set(p.airR, p.airG, p.airB);
            s.safeGetUniform("AirHeight").set(p.airHeight);
            s.safeGetUniform("PixelSize").set(p.pixelSize);
            s.safeGetUniform("PixelGrow").set(p.pixelGrow);
            s.safeGetUniform("Hills").set(p.hills);
            s.safeGetUniform("Snow").set(p.snow);
            COUNT[0]++;
            Minecraft.getInstance().getTextureManager().getTexture(p.map).setFilter(true, false);
            Minecraft.getInstance().getTextureManager().getTexture(BLOCKS).setFilter(false, false);
            RenderSystem.setShaderTexture(0, p.map);
            RenderSystem.setShaderTexture(1, BLOCKS);
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            RealWeather.LOG.error("Planets of blocks disabled after an error", e);
        }
    }

    /**
     * Draws the black hole over a rectangle of the screen. The point of the plane square to the line of sight (in
     * units of the horizon's radius, x right and y up) where each pixel's ray crosses it is c + cx * a + cy * b; the
     * disc's axis is given in the same frame (z towards us).
     */
    public static void blackHole(float x0, float y0, float x1, float y1, double[] o, double[] ox, double[] oy, double[] d, double[] dx, double[] dy,
                                 double[] diskN, float time, float alpha, float edge, float exposure, float steps) {
        if (!holeReady() || alpha <= 0.01F) {
            return;
        }
        try {
            Space3D.flush();
            ShaderInstance s = hole;
            set(s, "RayO", o);
            set(s, "RayOx", ox);
            set(s, "RayOy", oy);
            set(s, "RayD", d);
            set(s, "RayDx", dx);
            set(s, "RayDy", dy);
            set(s, "DiskN", diskN);
            s.safeGetUniform("Time").set(time);
            s.safeGetUniform("Alpha").set(alpha);
            s.safeGetUniform("Edge").set(edge);
            s.safeGetUniform("Exposure").set(exposure);
            s.safeGetUniform("MaxSteps").set(budget(steps, 90F, x0, y0, x1, y1));
            COUNT[1]++;
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            RealWeather.LOG.error("Black hole disabled after an error", e);
        }
    }

    /**
     * The air scattering sunlight, over the whole screen: the camera at `cam` (planet radii, from the planet's
     * centre, world axes), the sun along `sun` (world). False if it can't be drawn here.
     */
    public static boolean atmosphere(Atmo a, double[] cam, double[] sun) {
        if (air == null || broken || !enabled() || a.alpha() <= 0.01F) {
            return air != null && !broken && enabled();
        }
        try {
            Space3D.flush();
            ShaderInstance s = air;
            // The rays through the corners of the screen, from the view back into the world.
            double tx = 1 / Space3D.projX(), ty = 1 / Space3D.projY();
            double[] v = Space3D.VIEW;
            double[] f = {-v[2], -v[5], -v[8]}, r = {v[0], v[3], v[6]}, u = {v[1], v[4], v[7]};
            set(s, "RayO", cam);
            set(s, "RayOx", new double[3]);
            set(s, "RayOy", new double[3]);
            set(s, "RayD", f);
            set(s, "RayDx", new double[]{r[0] * tx, r[1] * tx, r[2] * tx});
            set(s, "RayDy", new double[]{u[0] * ty, u[1] * ty, u[2] * ty});
            s.safeGetUniform("Top").set(a.top());
            s.safeGetUniform("ScaleH").set(a.scaleH());
            s.safeGetUniform("Beta").set(a.br(), a.bg(), a.bb());
            s.safeGetUniform("Mie").set(a.mie());
            double l = Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]) + 1.0E-12;
            s.safeGetUniform("SunDir").set((float) (sun[0] / l), (float) (sun[1] / l), (float) (sun[2] / l));
            s.safeGetUniform("SunColor").set(a.sr(), a.sg(), a.sb());
            s.safeGetUniform("Intensity").set(a.intensity());
            COUNT[2]++;
            s.safeGetUniform("Alpha").set(Math.min(1F, a.alpha()));
            RenderSystem.setShader(() -> s);
            draw(-1F, -1F, 1F, 1F);
            return true;
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            RealWeather.LOG.error("Atmospheric scattering disabled after an error", e);
            return false;
        }
    }

    public static boolean nebulaReady() {
        return gas != null && !broken && enabled();
    }

    /**
     * A nebula over a rectangle of the screen; rays in the nebula's own frame (its radius 1), as for the planets.
     * `steps` and `octaves` set how much detail is worked out (more when it is big on the screen).
     */
    public static void nebula(Nebulae.Recipe r, float x0, float y0, float x1, float y1, double[] o, double[] ox, double[] oy, double[] d,
                              double[] dx, double[] dy, float steps, float octaves, float alpha) {
        if (!nebulaReady() || alpha <= 0.01F) {
            return;
        }
        try {
            Space3D.flush();
            ShaderInstance s = gas;
            set(s, "RayO", o);
            set(s, "RayOx", ox);
            set(s, "RayOy", oy);
            set(s, "RayD", d);
            set(s, "RayDx", dx);
            set(s, "RayDy", dy);
            int n = Math.min(10, r.pieces.size());
            for (int i = 0; i < 10; i++) {
                org.joml.Matrix4f m = new org.joml.Matrix4f();
                if (i < n) {
                    m.set(r.pieces.get(i).matrix());
                } else {
                    m.zero();
                }
                s.safeGetUniform("P" + i).set(m);
            }
            s.safeGetUniform("Count").set((float) n);
            s.safeGetUniform("Steps").set(budget(steps, 16F, x0, y0, x1, y1));
            s.safeGetUniform("Octaves").set(octaves);
            s.safeGetUniform("Seed").set((float) (Math.abs(r.id.hashCode()) % 97) * 1.37F);
            s.safeGetUniform("Exposure").set(r.exposure);
            s.safeGetUniform("Alpha").set(Math.min(1F, alpha));
            s.safeGetUniform("Fine").set(1F);
            COUNT[3]++;
            noise();
            RenderSystem.setShaderTexture(0, NOISE);
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            RealWeather.LOG.error("Nebulae disabled after an error", e);
        }
    }

    /** How a gas giant looks: its map, flattening, light, winds, storms (lon, lat, size, spin), a polar hexagon, glow. */
    public static final class GasLook {
        public ResourceLocation map;
        public float oblate = 1F, time, bands = 1F, hexagon, glow, alpha = 1F, pixel;
        public float lightR = 1F, lightG = 1F, lightB = 1F, airR = 0.8F, airG = 0.8F, airB = 0.9F;
        public double lx, ly = 1, lz;
        public final float[] storms = new float[16];

        public GasLook storm(int i, double lonDeg, double latDeg, double sizeDeg, double spin) {
            storms[i * 4] = (float) Math.toRadians(lonDeg);
            storms[i * 4 + 1] = (float) Math.toRadians(latDeg);
            storms[i * 4 + 2] = (float) Math.toRadians(sizeDeg);
            storms[i * 4 + 3] = (float) spin;
            return this;
        }
    }

    /** A gas giant's clouds over a rectangle of the screen (rays in its frame, its radius 1). */
    public static void gasPlanet(GasLook g, float x0, float y0, float x1, float y1, double[] o, double[] ox, double[] oy, double[] d, double[] dx,
                                 double[] dy) {
        if (!gasReady() || g.map == null || g.alpha <= 0.01F) {
            return;
        }
        try {
            Space3D.flush();
            ShaderInstance s = giant;
            rays(s, o, ox, oy, d, dx, dy);
            double l = Math.sqrt(g.lx * g.lx + g.ly * g.ly + g.lz * g.lz) + 1.0E-12;
            s.safeGetUniform("LightDir").set((float) (g.lx / l), (float) (g.ly / l), (float) (g.lz / l));
            s.safeGetUniform("LightColor").set(g.lightR, g.lightG, g.lightB);
            s.safeGetUniform("Oblate").set(g.oblate);
            s.safeGetUniform("Time").set(g.time);
            s.safeGetUniform("Bands").set(g.bands);
            for (int i = 0; i < 4; i++) {
                s.safeGetUniform("V" + i).set(g.storms[i * 4], g.storms[i * 4 + 1], g.storms[i * 4 + 2], g.storms[i * 4 + 3]);
            }
            s.safeGetUniform("Hexagon").set(g.hexagon);
            s.safeGetUniform("Glow").set(g.glow);
            s.safeGetUniform("AirColor").set(g.airR, g.airG, g.airB);
            s.safeGetUniform("Alpha").set(Math.min(1F, g.alpha));
            s.safeGetUniform("Pixel").set(g.pixel);
            COUNT[4]++;
            Minecraft.getInstance().getTextureManager().getTexture(g.map).setFilter(true, false);
            noise();
            RenderSystem.setShaderTexture(0, g.map);
            RenderSystem.setShaderTexture(1, NOISE);
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            RealWeather.LOG.error("Gas giants disabled after an error", e);
        }
    }

    /**
     * A star's boiling face over a rectangle of the screen (rays in its frame, radius 1): its colour, how many
     * granules across its radius, up to four sunspots (direction and angular size), pixel size (radii) for detail.
     */
    public static void starSurface(float r, float g, float b, float bright, float cells, float time, float pixel, float[] spots, float alpha,
                                   float near, float x0, float y0, float x1, float y1, double[] o, double[] ox, double[] oy, double[] d, double[] dx,
                                   double[] dy) {
        if (!starReady() || alpha <= 0.01F) {
            return;
        }
        try {
            Space3D.flush();
            ShaderInstance s = starFace;
            rays(s, o, ox, oy, d, dx, dy);
            s.safeGetUniform("StarColor").set(r, g, b);
            s.safeGetUniform("Time").set(time);
            s.safeGetUniform("Pixel").set(pixel);
            s.safeGetUniform("Cells").set(cells);
            for (int i = 0; i < 4; i++) {
                if (spots != null && spots.length >= i * 4 + 4) {
                    s.safeGetUniform("S" + i).set(spots[i * 4], spots[i * 4 + 1], spots[i * 4 + 2], spots[i * 4 + 3]);
                } else {
                    s.safeGetUniform("S" + i).set(0F, 1F, 0F, 0F);
                }
            }
            s.safeGetUniform("Alpha").set(Math.min(1F, alpha));
            s.safeGetUniform("Bright").set(bright);
            s.safeGetUniform("Near").set(near);
            COUNT[5]++;
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1, true);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            RealWeather.LOG.error("Star surfaces disabled after an error", e);
        }
    }

    /**
     * A planet's rings over a rectangle of the screen (rays in the planet's frame, its radius 1, rings in y = 0): inner
     * and outer radius, the star's direction, their tint, and the pixel size (radii per unit distance) for when they
     * break up into particles.
     */
    public static void rings(float inner, float outer, double lx, double ly, double lz, float tr, float tg, float tb, float time, float pixel,
                             float alpha, float near, float x0, float y0, float x1, float y1, double[] o, double[] ox, double[] oy, double[] d,
                             double[] dx, double[] dy) {
        if (!ringsReady() || alpha <= 0.01F) {
            return;
        }
        try {
            Space3D.flush();
            ShaderInstance s = rings;
            rays(s, o, ox, oy, d, dx, dy);
            double l = Math.sqrt(lx * lx + ly * ly + lz * lz) + 1.0E-12;
            s.safeGetUniform("LightDir").set((float) (lx / l), (float) (ly / l), (float) (lz / l));
            s.safeGetUniform("Inner").set(inner);
            s.safeGetUniform("Outer").set(outer);
            s.safeGetUniform("Time").set(time);
            s.safeGetUniform("Pixel").set(pixel);
            s.safeGetUniform("Tint").set(tr, tg, tb);
            s.safeGetUniform("Alpha").set(Math.min(1F, alpha));
            s.safeGetUniform("Spacing").set(0.0035F);
            COUNT[6]++;
            s.safeGetUniform("Near").set(near);
            Minecraft.getInstance().getTextureManager().getTexture(RING_MAP).setFilter(true, false);
            RenderSystem.setShaderTexture(0, RING_MAP);
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            RealWeather.LOG.error("Ring particles disabled after an error", e);
        }
    }

    private static void rays(ShaderInstance s, double[] o, double[] ox, double[] oy, double[] d, double[] dx, double[] dy) {
        set(s, "RayO", o);
        set(s, "RayOx", ox);
        set(s, "RayOy", oy);
        set(s, "RayD", d);
        set(s, "RayDx", dx);
        set(s, "RayDy", dy);
    }

    /** The noise texture, repeating. */
    private static void noise() {
        var tex = Minecraft.getInstance().getTextureManager().getTexture(NOISE);
        tex.setFilter(true, false);
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(tex.getId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }

    private static void set(ShaderInstance s, String name, double[] v) {
        s.safeGetUniform(name).set((float) v[0], (float) v[1], (float) v[2]);
    }

    /**
     * Draws the pass set up with RenderSystem.setShader over a rectangle of the screen: worked out at a fraction of
     * the resolution into a picture of its own, then put on the screen smoothly enlarged (its colour premultiplied).
     */
    private static void draw(float x0, float y0, float x1, float y1) {
        draw(x0, y0, x1, y1, false);
    }

    /** As above; `full`: at the screen's own resolution (cheap passes, whose fine detail would blur). */
    private static void draw(float x0, float y0, float x1, float y1, boolean full) {
        traced = true;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        float sc = full ? 1F : scale < 0 ? 0.5F : scale;
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        if (sc >= 0.97F || blit == null) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            quad(x0, y0, x1, y1, -1F, -1F, 1F, 1F);
            RenderSystem.enableDepthTest();
            Space3D.invalidate();
            return;
        }
        int w = Math.max(1, Math.round(main.width * sc)), h = Math.max(1, Math.round(main.height * sc));
        if (low == null) {
            low = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            low.setFilterMode(GL11.GL_LINEAR);
        } else if (low.width != w || low.height != h) {
            low.resize(w, h, Minecraft.ON_OSX);
            low.setFilterMode(GL11.GL_LINEAR);
        }
        var pass = RenderSystem.getShader();
        low.setClearColor(0F, 0F, 0F, 0F);
        low.clear(Minecraft.ON_OSX);
        low.bindWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.setShader(() -> pass);
        quad(x0, y0, x1, y1, -1F, -1F, 1F, 1F);
        main.bindWrite(true);
        RenderSystem.setShaderTexture(0, low.getColorTextureId());
        RenderSystem.setShader(() -> blit);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        quad(x0, y0, x1, y1, (x0 + 1F) * 0.5F, (y0 + 1F) * 0.5F, (x1 + 1F) * 0.5F, (y1 + 1F) * 0.5F);
        RenderSystem.disableDepthTest();
        RenderSystem.enableDepthTest();
        Space3D.invalidate();
    }

    private static void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(x0, y0, 0F).setUv(u0, v0);
        b.addVertex(x1, y0, 0F).setUv(u1, v0);
        b.addVertex(x1, y1, 0F).setUv(u1, v1);
        b.addVertex(x0, y1, 0F).setUv(u0, v1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
