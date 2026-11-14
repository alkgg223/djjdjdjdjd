package com.realweather.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.RealWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

/**
 * Cosmic ray air showers: a particle from deep space (a proton, near the speed of light) hits the top of the air and
 * smashes into a cascade, each fragment splitting again and again as it races down, millions of particles in a cone,
 * shown here as a branching tree of faint blue-white tracks (Cherenkov and nitrogen glow) lighting up from the top
 * down in a fraction of a second and fading. Mostly seen at night, at random, all round the sky; also on other
 * worlds with air.
 */
public final class CosmicRays {
    private static final ResourceLocation GLOW = RealWeather.id("textures/environment/space/glow.png");
    private static final double SLOT = 140;
    private static boolean failed;
    /** For tests: one shower in front of the camera, this many seconds after it began (or none, below 0). */
    public static double testAge = -1;

    private CosmicRays() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        boolean planets = level.dimension() == com.realweather.space.Worlds.DIMENSION;
        float air;
        if (planets) {
            com.realweather.space.Worlds.World w = com.realweather.space.Worlds.at(event.getCamera().getPosition().x);
            if (w == null || w.airless() || w.gas()) {
                return;
            }
            air = Math.min(1F, w.air());
        } else if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            air = 1F;
        } else {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float night = Mth.clamp(level.getStarBrightness(pt) * 2.2F, 0F, 1F);
        float vis = night * (1F - level.getRainLevel(pt) * 0.8F) * air;
        if (vis < 0.03F && testAge < 0) {
            return;
        }
        try {
            render(event, level, pt, vis);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RealWeather.LOG.error("Cosmic ray showers disabled after an error", e);
        }
    }

    private static float h(long seed, long k) {
        return com.realweather.client.space.Space3D.h(seed, k);
    }

    /**
     * Drawn with the sky (before the land, so hills and buildings hide what is behind them), in the space renderer's
     * own depth range: the showers are far off and high up, beyond the game's own far plane.
     */
    private static void render(RenderLevelStageEvent event, ClientLevel level, float pt, float vis) {
        Vec3 cam = event.getCamera().getPosition();
        double t = level.getGameTime() + pt;
        long slot = (long) Math.floor(t / SLOT);
        boolean begun = false;
        try {
            if (testAge >= 0) {
                com.realweather.client.space.Space3D.begin(event, level, pt);
                begun = true;
                double want = Math.toRadians(event.getCamera().getYRot() + 90);
                showerAt(cam, 4242, testAge, Math.max(vis, 0.8F), want, 260);
            }
            for (long k = slot - 1; k <= slot; k++) {
                long seed = k * 0x9E3779B97F4A7C15L + 77;
                if (h(seed, 1) > 0.3F) {
                    continue;
                }
                double start = k * SLOT + h(seed, 2) * SLOT * 0.8;
                double age = (t - start) / 20.0;
                if (age < 0 || age > 2.6) {
                    continue;
                }
                if (!begun) {
                    com.realweather.client.space.Space3D.begin(event, level, pt);
                    begun = true;
                }
                shower(cam, seed, age, vis);
            }
        } finally {
            if (begun) {
                com.realweather.client.space.Space3D.end();
            }
        }
    }

    /** One shower, `age` seconds after the primary hit the top of the air. */
    private static void shower(Vec3 cam, long seed, double age, float vis) {
        showerAt(cam, seed, age, vis, h(seed, 3) * Math.PI * 2, 160 + 420 * h(seed, 4));
    }

    private static void showerAt(Vec3 cam, long seed, double age, float vis, double ang, double dist) {
        double top = Math.max(cam.y + 140, 250 + 170 * h(seed, 5));
        double x = cam.x + Math.cos(ang) * dist, z = cam.z + Math.sin(ang) * dist;
        // Coming in at a slant from a random direction.
        double tilt = h(seed, 6) * 0.6, dir = h(seed, 7) * Math.PI * 2;
        double dx = Math.sin(tilt) * Math.cos(dir), dy = -Math.cos(tilt), dz = Math.sin(tilt) * Math.sin(dir);
        double length = top - Math.max(cam.y - 60, 40);
        // The front races down at the speed of light (all but instantly), then the tracks fade.
        float front = (float) Math.min(1, age / 0.25);
        float fade = (float) (age < 0.25 ? 1 : Math.exp(-(age - 0.25) * 2.2)) * vis;
        branch(cam, seed, 0, x, top, z, dx, dy, dz, length, 0, front, fade, 0.5F + 0.5F * h(seed, 8));
    }

    private static void branch(Vec3 cam, long seed, int depth, double x, double y, double z, double dx, double dy, double dz,
                               double len, double s0, float front, float fade, float energy) {
        if (depth > 5 || s0 >= front) {
            return;
        }
        // This track runs a part of the way down before it splits.
        double seg = len * (0.18 + 0.12 * h(seed, depth * 31L + 5));
        double s1 = Math.min(front, s0 + seg / len);
        double part = (s1 - s0) * len;
        double ex = x + dx * part, ey = y + dy * part, ez = z + dz * part;
        float a = fade * energy * (float) (depth == 0 ? 0.65 : 0.5 / Math.sqrt(depth + 1));
        double w = 0.9 + 1.6 / (depth + 1);
        ribbon(cam, x, y, z, ex, ey, ez, w, 0.75F, 0.88F, 1F, Math.min(1F, a * 1.5F));
        ribbon(cam, x, y, z, ex, ey, ez, w * 5, 0.5F, 0.55F, 1F, a * 0.35F);
        if (s1 >= front) {
            return;
        }
        int kids = depth < 2 ? 3 : 2;
        for (int i = 0; i < kids; i++) {
            long sd = seed * 131 + depth * 17L + i * 7L + (long) (x * 3);
            double spread = 0.08 + 0.1 * depth;
            double nx = dx + (h(sd, 1) - 0.5) * spread, ny = dy, nz = dz + (h(sd, 2) - 0.5) * spread;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            branch(cam, sd, depth + 1, ex, ey, ez, nx / nl, ny / nl, nz / nl, len, s1, front, fade, energy * (0.55F + 0.3F * h(sd, 3)));
        }
    }

    private static final double[] A = new double[3], B = new double[3];

    private static void ribbon(Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, double w, float r, float g, float b,
                               float a) {
        if (a <= 0.003F) {
            return;
        }
        double[] v = com.realweather.client.space.Space3D.VIEW;
        com.realweather.client.space.Space3D.apply(v, x0 - cam.x, y0 - cam.y, z0 - cam.z, A);
        com.realweather.client.space.Space3D.apply(v, x1 - cam.x, y1 - cam.y, z1 - cam.z, B);
        com.realweather.client.space.Space3D.ribbon(GLOW, com.realweather.client.space.Space3D.ADD, A[0], A[1], A[2], B[0], B[1], B[2], w, w, r, g, b, a,
                a);
    }
}
