package com.realweather.client.space;

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
    private static ShaderInstance voxel, hole, air, gas;
    private static final ResourceLocation NOISE = RealWeather.id("textures/environment/space3d/nebula_noise.png");
    private static boolean broken;

    private SpaceShaders() {}

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("voxel_planet"), DefaultVertexFormat.POSITION_TEX),
                    s -> voxel = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("black_hole"), DefaultVertexFormat.POSITION_TEX),
                    s -> hole = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("atmosphere"), DefaultVertexFormat.POSITION_TEX),
                    s -> air = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), RealWeather.id("nebula"), DefaultVertexFormat.POSITION_TEX),
                    s -> gas = s);
        } catch (Exception | LinkageError e) {
            voxel = null;
            hole = null;
            RealWeather.LOG.warn("Ray-traced planets and black hole unavailable (shaders did not load)", e);
        }
    }

    public static boolean voxelReady() {
        return voxel != null && !broken;
    }

    public static boolean holeReady() {
        return hole != null && !broken;
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
            s.safeGetUniform("MaxSteps").set(TESTING ? Math.min(p.steps, 72F) : p.steps);
            s.safeGetUniform("Tiles").set(p.tiles[0], p.tiles[1], p.tiles[2], p.tiles[3]);
            s.safeGetUniform("Gas").set(p.gas ? 1F : 0F);
            s.safeGetUniform("Air").set(p.air);
            s.safeGetUniform("AirColor").set(p.airR, p.airG, p.airB);
            s.safeGetUniform("AirHeight").set(p.airHeight);
            s.safeGetUniform("PixelSize").set(p.pixelSize);
            s.safeGetUniform("PixelGrow").set(p.pixelGrow);
            s.safeGetUniform("Hills").set(p.hills);
            s.safeGetUniform("Snow").set(p.snow);
            Minecraft.getInstance().getTextureManager().getTexture(p.map).setFilter(true, false);
            Minecraft.getInstance().getTextureManager().getTexture(BLOCKS).setFilter(false, false);
            RenderSystem.setShaderTexture(0, p.map);
            RenderSystem.setShaderTexture(1, BLOCKS);
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException e) {
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
            s.safeGetUniform("MaxSteps").set(TESTING ? Math.min(steps, 120F) : steps);
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException e) {
            broken = true;
            RealWeather.LOG.error("Black hole disabled after an error", e);
        }
    }

    /**
     * The air scattering sunlight, over the whole screen: the camera at `cam` (planet radii, from the planet's
     * centre, world axes), the sun along `sun` (world). False if it can't be drawn here.
     */
    public static boolean atmosphere(Atmo a, double[] cam, double[] sun) {
        if (air == null || broken || a.alpha() <= 0.01F) {
            return air != null && !broken;
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
            s.safeGetUniform("Alpha").set(Math.min(1F, a.alpha()));
            RenderSystem.setShader(() -> s);
            draw(-1F, -1F, 1F, 1F);
            return true;
        } catch (RuntimeException e) {
            broken = true;
            RealWeather.LOG.error("Atmospheric scattering disabled after an error", e);
            return false;
        }
    }

    public static boolean nebulaReady() {
        return gas != null && !broken;
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
            s.safeGetUniform("Steps").set(TESTING ? Math.min(steps, 24F) : steps);
            s.safeGetUniform("Octaves").set(octaves);
            s.safeGetUniform("Seed").set((float) (Math.abs(r.id.hashCode()) % 97) * 1.37F);
            s.safeGetUniform("Exposure").set(r.exposure);
            s.safeGetUniform("Alpha").set(Math.min(1F, alpha));
            s.safeGetUniform("Fine").set(1F);
            var tex = Minecraft.getInstance().getTextureManager().getTexture(NOISE);
            tex.setFilter(true, false);
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(tex.getId());
            com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,
                    org.lwjgl.opengl.GL11.GL_REPEAT);
            com.mojang.blaze3d.platform.GlStateManager._texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,
                    org.lwjgl.opengl.GL11.GL_REPEAT);
            RenderSystem.setShaderTexture(0, NOISE);
            RenderSystem.setShader(() -> s);
            draw(x0, y0, x1, y1);
        } catch (RuntimeException e) {
            broken = true;
            RealWeather.LOG.error("Nebulae disabled after an error", e);
        }
    }

    private static void set(ShaderInstance s, String name, double[] v) {
        s.safeGetUniform(name).set((float) v[0], (float) v[1], (float) v[2]);
    }

    private static void draw(float x0, float y0, float x1, float y1) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(true, true, true, true);
        // Full resolution; the colour comes premultiplied by its coverage.
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        quad(x0, y0, x1, y1, -1F, -1F, 1F, 1F);
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
