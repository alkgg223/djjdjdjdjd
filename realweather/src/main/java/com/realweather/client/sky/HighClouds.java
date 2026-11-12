package com.realweather.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.ClientConfig;
import com.realweather.RealWeather;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

/**
 * The middle and high clouds for the sprite cloud levels (the 3D levels draw their own): cirrus streaks,
 * cirrocumulus ripples and a cirrostratus veil high up, altocumulus cloudlets and an altostratus sheet in the middle,
 * each as a sheet across the sky, as much of each as today's mix calls for and more in some places than in others.
 */
public final class HighClouds {
    private static final ResourceLocation CIRRUS = RealWeather.id("textures/environment/cirrus.png");
    private static final ResourceLocation CIRROCUMULUS = RealWeather.id("textures/environment/cirrocumulus.png");
    private static final ResourceLocation ALTOCUMULUS = RealWeather.id("textures/environment/altocumulus.png");
    private static final ResourceLocation VEIL = RealWeather.id("textures/environment/veil.png");

    private HighClouds() {}

    static void render(RenderLevelStageEvent event, float visibility) {
        float[][] layers = {
                // height, weight, blocks per texture repeat, drift factor, opacity, region salt
                {760F, CloudTypes.cirrus, 2600F, 2.6F, 0.85F, 1F},
                {720F, CloudTypes.cirrocumulus, 1500F, 2.3F, 0.85F, 2F},
                {700F, CloudTypes.cirrostratus, 3200F, 2.0F, 0.5F, 3F},
                {470F, CloudTypes.altocumulus, 1000F, 1.6F, 0.9F, 4F},
                {440F, CloudTypes.altostratus, 2800F, 1.4F, 0.62F, 5F}};
        ResourceLocation[] tex = {CIRRUS, CIRROCUMULUS, VEIL, ALTOCUMULUS, VEIL};
        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        float[] fog = RenderSystem.getShaderFogColor();
        int q = ClientConfig.quality();
        int n = new int[]{4, 6, 8, 9}[q];
        float tile = 420F;
        // Beyond the far plane nothing is drawn: those vertices are pulled in along their line of sight.
        float limit = net.minecraft.client.Minecraft.getInstance().gameRenderer.getDepthFar() * 0.85F;
        try {
            for (int li = 0; li < layers.length; li++) {
                float[] L = layers[li];
                if (L[1] < 0.02F || cam.y > L[0] - 5) {
                    continue;
                }
                VolumeClouds.useGlow(tex[li]);
                BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                double ox = -CloudField.driftX * L[3], oz = -CloudField.driftZ * L[3];
                int cx = Mth.floor(cam.x / tile), cz = Mth.floor(cam.z / tile);
                float radius = n * tile;
                float y = (float) (L[0] - cam.y);
                // Ice cloud is bright white in the sun; the middle sheets are greyer underneath.
                float lit = li < 3 ? 1.05F : 0.92F;
                float r = SkyLight.r * lit, g = SkyLight.g * lit, bl = SkyLight.b * lit * 1.02F;
                for (int i = -n; i < n; i++) {
                    for (int j = -n; j < n; j++) {
                        double x0 = (cx + i) * tile, z0 = (cz + j) * tile;
                        double[][] c = {{x0, z0}, {x0 + tile, z0}, {x0 + tile, z0 + tile}, {x0, z0 + tile}};
                        float[] a = new float[4];
                        boolean any = false;
                        for (int k = 0; k < 4; k++) {
                            double dx = c[k][0] - cam.x, dz = c[k][1] - cam.z;
                            float d = (float) Math.sqrt(dx * dx + dz * dz);
                            float fade = 1F - smooth(radius * 0.55F, radius, d);
                            a[k] = L[1] * L[4] * region(c[k][0], c[k][1], L[5]) * fade;
                            any |= a[k] > 0.004F;
                        }
                        if (!any) {
                            continue;
                        }
                        for (int k = 0; k < 4; k++) {
                            double dx = c[k][0] - cam.x, dz = c[k][1] - cam.z;
                            float d = (float) Math.sqrt(dx * dx + dz * dz + y * y);
                            float haze = (1F - (float) Math.exp(-d / Math.max(600F, visibility * 2F))) * 0.85F;
                            float pull = d > limit ? limit / d : 1F;
                            b.addVertex((float) dx * pull, y * pull, (float) dz * pull).setUv((float) ((c[k][0] + ox) / L[2]), (float) ((c[k][1] + oz) / L[2]))
                                    .setColor(r + (fog[0] - r) * haze, g + (fog[1] - g) * haze, bl + (fog[2] - bl) * haze, a[k]);
                        }
                    }
                }
                MeshData mesh = b.build();
                if (mesh != null) {
                    BufferUploader.drawWithShader(mesh);
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** 0..1: how much of this layer there is over a place (patches thousands of blocks across, different every day). */
    private static float region(double x, double z, float salt) {
        double fx = (x + CloudTypes.offsetX * salt * 0.37) / 3000.0, fz = (z + CloudTypes.offsetZ * salt * 0.29) / 3000.0;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        float tx = (float) (fx - ix), tz = (float) (fz - iz);
        tx = tx * tx * (3F - 2F * tx);
        tz = tz * tz * (3F - 2F * tz);
        float v = Mth.lerp(tz, Mth.lerp(tx, h(ix, iz, salt), h(ix + 1, iz, salt)), Mth.lerp(tx, h(ix, iz + 1, salt), h(ix + 1, iz + 1, salt)));
        return smooth(0.25F, 0.7F, v);
    }

    private static float h(int x, int z, float salt) {
        long k = x * 341873128712L + z * 132897987541L + (long) (salt * 7919);
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        return (k >>> 40) / (float) (1L << 24);
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
