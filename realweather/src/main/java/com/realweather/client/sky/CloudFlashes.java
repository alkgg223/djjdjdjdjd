package com.realweather.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.client.ClientStorms;
import com.realweather.client.ClientWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightning inside the clouds. Each flash lights the cloud around it from within (a glowing patch that flickers with
 * the return strokes), instead of brightening the whole sky as if lit from the sun; some crawl along the cloud base as
 * branching 'spider' lightning; and storm cells, supercells and tornado storms flicker with them all the time. Real
 * strikes light the cloud they come from.
 */
public final class CloudFlashes {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static Vec3 cam = Vec3.ZERO;
    private static int now;

    private CloudFlashes() {}

    static final class Flash {
        final double x, y, z;
        final float radius, strength;
        /** The colour of this kind of lightning. */
        final float cr, cg, cb;
        final int born;
        final int[] pulses;
        final float[] pulseStrength;
        /** Bolt channel (world points, pairs of segments) for spider lightning, or null. */
        float[] bolt;
        float light;

        Flash(double x, double y, double z, float radius, float strength, int kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.strength = strength;
            float[] c = com.realweather.LightningKind.GLOW[kind];
            this.cr = c[0];
            this.cg = c[1];
            this.cb = c[2];
            this.born = now;
            int n = 1 + RANDOM.nextInt(4);
            pulses = new int[n];
            pulseStrength = new float[n];
            int t = 0;
            for (int i = 0; i < n; i++) {
                pulses[i] = t;
                pulseStrength[i] = i == 0 ? 1F : 0.4F + RANDOM.nextFloat() * 0.6F;
                t += 2 + RANDOM.nextInt(5);
            }
        }

        float intensity(float age) {
            float s = 0F;
            for (int i = 0; i < pulses.length; i++) {
                float d = age - pulses[i];
                if (d >= 0F) {
                    s += pulseStrength[i] * (float) Math.exp(-d / 1.3F);
                }
            }
            return Math.min(1.4F, s) * strength;
        }

        boolean done() {
            return now - born > pulses[pulses.length - 1] + 12;
        }
    }

    /** A flash at a point in the cloud (a strike's cloud, or one that stays in the cloud). */
    public static void add(double x, double y, double z, float strength, boolean spider, int kind) {
        if (FLASHES.size() >= 24) {
            FLASHES.remove(0);
        }
        Flash f = new Flash(x, y, z, (55F + RANDOM.nextFloat() * 70F) * (0.85F + 0.12F * kind), strength, kind);
        if (spider) {
            f.bolt = spider(x, y - 6, z);
        }
        FLASHES.add(f);
    }

    /** A flash of white lightning (for callers that don't pick a kind). */
    public static void add(double x, double y, double z, float strength, boolean spider) {
        add(x, y, z, strength, spider, com.realweather.LightningKind.WHITE);
    }

    public static void clear() {
        FLASHES.clear();
    }

    /** Lightning flickering in the storm clouds around: storm cells, supercells, tornado storms and thundery skies. */
    public static void tick(Minecraft mc, ClientLevel level) {
        now++;
        FLASHES.removeIf(Flash::done);
        if (!ClientWeather.active(level) || level.getRainLevel(1F) < 0.2F) {
            return;
        }
        Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        float deck = StormClouds.deckY(level);
        for (ClientStorms.Cell cell : ClientStorms.cells()) {
            float s = cell.cstrength() * cell.cintensity();
            double dx = cell.x - c.x, dz = cell.z - c.z;
            if (s < 0.25F || dx * dx + dz * dz > 2500.0 * 2500.0) {
                continue;
            }
            float chance = (s - 0.2F) * 0.03F * (0.5F + ClientWeather.thunder);
            if (RANDOM.nextFloat() < chance) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = Math.sqrt(RANDOM.nextDouble()) * cell.radius * 0.7;
                spawn(level, cell.x + Math.cos(a) * r, deck + 15 + RANDOM.nextDouble() * 70, cell.z + Math.sin(a) * r, 0.7F + s * 0.5F);
            }
        }
        for (com.realweather.client.tornado.ClientTornadoes.T t : com.realweather.client.tornado.ClientTornadoes.all()) {
            if (RANDOM.nextFloat() < 0.035F) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = 30 + RANDOM.nextDouble() * 220;
                spawn(level, t.x + Math.cos(a) * r, deck + 20 + RANDOM.nextDouble() * 60, t.z + Math.sin(a) * r, 1F);
            }
        }
    }

    private static void spawn(ClientLevel level, double x, double y, double z, float strength) {
        int kind = com.realweather.LightningKind.roll(RANDOM, Mth.clamp((strength - 0.7F) / 0.5F, 0F, 1F));
        com.realweather.client.WeatherSounds.cloudFlash(x, y, z, strength, RANDOM.nextFloat() < 0.4F, kind);
    }

    /** Called each frame before the clouds are gathered: the light every flash gives right now. */
    static void frame(Vec3 camera, float pt) {
        cam = camera;
        for (Flash f : FLASHES) {
            f.light = f.intensity(now - f.born + pt);
        }
    }

    /**
     * Extra light the flashes put on cloud at a point given relative to the camera, in each flash's colour, into
     * `out` (red, green, blue). Returns false when there is none.
     */
    public static boolean lightRel(float rx, float ry, float rz, float[] out) {
        float r = 0F, g = 0F, b = 0F;
        for (Flash f : FLASHES) {
            if (f.light < 0.01F) {
                continue;
            }
            float dx = (float) (f.x - cam.x) - rx, dy = (float) (f.y - cam.y) - ry, dz = (float) (f.z - cam.z) - rz;
            float d2 = dx * dx + dy * dy * 2F + dz * dz;
            float r2 = f.radius * f.radius;
            if (d2 < r2 * 9F) {
                float s = f.light * (float) Math.exp(-d2 / r2);
                r += s * f.cr;
                g += s * f.cg;
                b += s * f.cb;
            }
        }
        out[0] = r;
        out[1] = g;
        out[2] = b;
        return r + g + b > 0.008F;
    }

    /** The four brightest flashes right now for the volumetric clouds: x, y, z relative to the camera, radius; power; colour. */
    static void strongest(float[] pos, float[] power, float[] color) {
        java.util.Arrays.fill(power, 0F);
        for (Flash f : FLASHES) {
            int slot = -1;
            float min = f.light;
            for (int i = 0; i < 4; i++) {
                if (power[i] < min) {
                    min = power[i];
                    slot = i;
                }
            }
            if (slot >= 0 && f.light > 0.01F) {
                power[slot] = f.light;
                pos[slot * 4] = (float) (f.x - cam.x);
                pos[slot * 4 + 1] = (float) (f.y - cam.y);
                pos[slot * 4 + 2] = (float) (f.z - cam.z);
                pos[slot * 4 + 3] = f.radius;
                color[slot * 3] = f.cr;
                color[slot * 3 + 1] = f.cg;
                color[slot * 3 + 2] = f.cb;
            }
        }
    }

    // ------------------------------------------------------------------------------------ spider lightning
    /** A branching channel crawling along under the cloud base. */
    private static float[] spider(double x, double y, double z) {
        List<float[]> segs = new ArrayList<>();
        float dir = RANDOM.nextFloat() * Mth.TWO_PI;
        branch(segs, (float) x, (float) y, (float) z, dir, 70F + RANDOM.nextFloat() * 160F, 0);
        float[] out = new float[segs.size() * 6];
        for (int i = 0; i < segs.size(); i++) {
            System.arraycopy(segs.get(i), 0, out, i * 6, 6);
        }
        return out;
    }

    private static void branch(List<float[]> segs, float x, float y, float z, float dir, float length, int depth) {
        float step = 4F + RANDOM.nextFloat() * 3F;
        int n = Math.max(2, (int) (length / step));
        for (int i = 0; i < n; i++) {
            dir += (RANDOM.nextFloat() - 0.5F) * 0.9F;
            float nx = x + Mth.cos(dir) * step, nz = z + Mth.sin(dir) * step, ny = y + (RANDOM.nextFloat() - 0.5F) * 2.5F;
            segs.add(new float[]{x, y, z, nx, ny, nz});
            if (depth < 2 && RANDOM.nextFloat() < 0.12F) {
                branch(segs, nx, ny, nz, dir + (RANDOM.nextBoolean() ? 1 : -1) * (0.5F + RANDOM.nextFloat() * 0.6F), length * (0.25F + RANDOM.nextFloat() * 0.3F),
                        depth + 1);
            }
            x = nx;
            y = ny;
            z = nz;
        }
    }

    /** Draws the spider lightning (before the clouds, so they veil it where it runs inside them). */
    static void renderBolts(RenderLevelStageEvent event) {
        boolean any = false;
        for (Flash f : FLASHES) {
            if (f.bolt != null && f.light > 0.05F) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        Vec3 c = event.getCamera().getPosition();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        try {
            for (Flash f : FLASHES) {
                if (f.bolt == null || f.light <= 0.05F) {
                    continue;
                }
                float a = Math.min(1F, f.light);
                // The channel in the colour of its kind: a coloured glow and a nearly white core.
                float gr = f.cr * 0.7F, gg = f.cg * 0.72F, gb = f.cb;
                float kr = 0.55F + 0.45F * f.cr, kg = 0.55F + 0.45F * f.cg, kb = 0.55F + 0.45F * f.cb;
                for (int i = 0; i < f.bolt.length; i += 6) {
                    float x0 = (float) (f.bolt[i] - c.x), y0 = (float) (f.bolt[i + 1] - c.y), z0 = (float) (f.bolt[i + 2] - c.z);
                    float x1 = (float) (f.bolt[i + 3] - c.x), y1 = (float) (f.bolt[i + 4] - c.y), z1 = (float) (f.bolt[i + 5] - c.z);
                    ribbon(b, x0, y0, z0, x1, y1, z1, 2.2F, gr, gg, gb, a * 0.25F);
                    ribbon(b, x0, y0, z0, x1, y1, z1, 0.45F, kr, kg, kb, a);
                }
            }
            MeshData mesh = b.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** A flat strip from p0 to p1 turned to face the camera (at the origin). */
    private static void ribbon(BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1, float w, float r, float g, float bl, float a) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float mx = (x0 + x1) * 0.5F, my = (y0 + y1) * 0.5F, mz = (z0 + z1) * 0.5F;
        // Across = segment x view direction.
        float cx = dy * mz - dz * my, cy = dz * mx - dx * mz, cz = dx * my - dy * mx;
        float l = Mth.sqrt(cx * cx + cy * cy + cz * cz) + 1.0E-4F;
        cx *= w / l;
        cy *= w / l;
        cz *= w / l;
        b.addVertex(x0 - cx, y0 - cy, z0 - cz).setColor(r, g, bl, a);
        b.addVertex(x1 - cx, y1 - cy, z1 - cz).setColor(r, g, bl, a);
        b.addVertex(x1 + cx, y1 + cy, z1 + cz).setColor(r, g, bl, a);
        b.addVertex(x0 + cx, y0 + cy, z0 + cz).setColor(r, g, bl, a);
    }
}
