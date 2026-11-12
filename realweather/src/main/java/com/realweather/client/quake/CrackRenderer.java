package com.realweather.client.quake;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.RealWeather;
import com.realweather.server.Earthquake;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

import java.util.List;

/**
 * Draws the earthquake cracks on the ground: dark, jagged fissures with crumbled edges that run out from the
 * epicentre as the rupture spreads and widen while the ground shakes. Where the ground has really split open the
 * crack's rim lies around the hole.
 */
public final class CrackRenderer {
    private static final ResourceLocation TEXTURE = RealWeather.id("textures/environment/quake_crack.png");
    private static boolean failed;

    private CrackRenderer() {}

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || ClientQuakes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        try {
            render(event, mc, level);
        } catch (RuntimeException e) {
            failed = true;
            RealWeather.LOG.error("Earthquake crack rendering disabled after an error", e);
        }
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc, ClientLevel level) {
        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        LightTexture light = mc.gameRenderer.lightTexture();
        light.turnOnLightLayer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-2F, -12F);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        try {
            for (ClientQuakes.Q w : ClientQuakes.all()) {
                quake(buf, level, w, cam);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            RenderSystem.polygonOffset(0F, 0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            light.turnOffLightLayer();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void quake(BufferBuilder buf, ClientLevel level, ClientQuakes.Q w, Vec3 cam) {
        Earthquake q = w.q;
        if (w.ground == null) {
            return;
        }
        float fade = Mth.clamp((q.duration + Earthquake.AFTER - q.age) / 1200F, 0F, 1F);
        if (fade <= 0F) {
            return;
        }
        List<Earthquake.Crack> cracks = q.cracks();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int c = 0; c < cracks.size() && c < w.ground.length; c++) {
            Earthquake.Crack cr = cracks.get(c);
            float[] g = w.ground[c];
            if (g == null || cr.n < 2) {
                continue;
            }
            double ex = cr.xs[0] - cam.x, ez = cr.zs[0] - cam.z;
            if (ex * ex + ez * ez > 260 * 260 && cr.n < 60) {
                continue;
            }
            float u = 0F;
            for (int i = 0; i < cr.n - 1; i++) {
                float w0 = q.widthNow(cr, i), w1 = q.widthNow(cr, i + 1);
                float segLen = Mth.sqrt(sq(cr.xs[i + 1] - cr.xs[i]) + sq(cr.zs[i + 1] - cr.zs[i]));
                float u0 = u;
                u += segLen / 3F;
                if (w0 <= 0F && w1 <= 0F) {
                    continue;
                }
                if (Float.isNaN(g[i]) || Float.isNaN(g[i + 1]) || Math.abs(g[i] - g[i + 1]) > 1.6F) {
                    continue;
                }
                double dx = cr.xs[i] - cam.x, dz = cr.zs[i] - cam.z;
                if (dx * dx + dz * dz > 160 * 160) {
                    continue;
                }
                float[] n0 = normal(cr, i), n1 = normal(cr, i + 1);
                float v0 = visual(w0), v1 = visual(w1);
                float a0 = alpha(cr, i, w0) * fade, a1 = alpha(cr, i + 1, w1) * fade;
                int l0 = LevelRenderer.getLightColor(level, m.set(cr.xs[i], g[i], cr.zs[i]));
                int l1 = LevelRenderer.getLightColor(level, m.set(cr.xs[i + 1], g[i + 1], cr.zs[i + 1]));
                float y0 = (float) (g[i] + 0.02 - cam.y), y1 = (float) (g[i + 1] + 0.02 - cam.y);
                float x0 = (float) (cr.xs[i] - cam.x), z0 = (float) (cr.zs[i] - cam.z);
                float x1 = (float) (cr.xs[i + 1] - cam.x), z1 = (float) (cr.zs[i + 1] - cam.z);
                vertex(buf, x0 - n0[0] * v0, y0, z0 - n0[1] * v0, u0, 0F, a0, l0);
                vertex(buf, x1 - n1[0] * v1, y1, z1 - n1[1] * v1, u, 0F, a1, l1);
                vertex(buf, x1 + n1[0] * v1, y1, z1 + n1[1] * v1, u, 1F, a1, l1);
                vertex(buf, x0 + n0[0] * v0, y0, z0 + n0[1] * v0, u0, 1F, a0, l0);
            }
        }
    }

    /** Half the drawn width: hairline cracks a little wider than they are; open fissures with their broken rim. */
    private static float visual(float w) {
        return w >= Earthquake.OPEN_WIDTH ? (w + 1.1F) * 0.5F : (w * 2.4F + 0.09F) * 0.5F;
    }

    private static float alpha(Earthquake.Crack cr, int i, float w) {
        return Mth.clamp(w / Math.max(0.04F, cr.width[i]) * 1.6F, 0F, 1F);
    }

    /** Unit vector across the crack at a point (averaged over the segments either side). */
    private static float[] normal(Earthquake.Crack cr, int i) {
        int a = Math.max(0, i - 1), b = Math.min(cr.n - 1, i + 1);
        float dx = cr.xs[b] - cr.xs[a], dz = cr.zs[b] - cr.zs[a];
        float len = Mth.sqrt(dx * dx + dz * dz) + 1.0E-4F;
        return new float[]{-dz / len, dx / len};
    }

    private static float sq(float v) {
        return v * v;
    }

    private static void vertex(BufferBuilder buf, float x, float y, float z, float u, float v, float a, int light) {
        buf.addVertex(x, y, z).setUv(u, v).setColor(0.9F, 0.88F, 0.86F, a).setLight(light);
    }
}
