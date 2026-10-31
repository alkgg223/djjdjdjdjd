package com.realweather.client.radar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.realweather.RealWeather;
import com.realweather.device.RadarBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The turning radar antenna on top of the tower: a white parabolic dish on a yoke, with its feed horn held out
 * in front on three struts. It turns once every sweep of the radar picture.
 */
public class RadarDishRenderer implements BlockEntityRenderer<RadarBlockEntity> {
    private static final ResourceLocation DISH = RealWeather.id("textures/block/radar_dish.png");
    private static final ResourceLocation METAL = RealWeather.id("textures/block/radar_metal.png");
    private static final int RINGS = 6;
    private static final int SEGMENTS = 24;
    private static final float RADIUS = 0.78F;
    private static final float FOCUS = 0.62F;

    public RadarDishRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(RadarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        float time = be.getLevel().getGameTime() + partialTick;
        float yaw = (time % RadarCanvas.SECTORS) / RadarCanvas.SECTORS * 360F;
        pose.pushPose();
        pose.translate(0.5, 18F / 16F, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        // Turntable and yoke.
        box(pose, metal, -0.2F, 0F, -0.2F, 0.2F, 0.08F, 0.2F, lit);
        box(pose, metal, -0.06F, 0.08F, -0.12F, 0.06F, 0.42F, 0.02F, lit);
        box(pose, metal, -0.32F, 0.36F, -0.06F, 0.32F, 0.44F, 0.04F, lit);
        pose.translate(0, 0.42F, 0.08F);
        // Tilted a few degrees up toward the sky.
        pose.mulPose(Axis.XP.rotationDegrees(-8F));
        VertexConsumer dish = buffers.getBuffer(RenderType.entityCutoutNoCull(DISH));
        dish(pose, dish, lit);
        VertexConsumer metal2 = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        // Feed horn at the focus, on three struts from the rim.
        float f = FOCUS;
        box(pose, metal2, -0.05F, -0.05F, f - 0.08F, 0.05F, 0.05F, f + 0.04F, lit);
        for (int i = 0; i < 3; i++) {
            float a = i * Mth.TWO_PI / 3F + Mth.HALF_PI;
            float rx = Mth.cos(a) * RADIUS * 0.92F;
            float ry = Mth.sin(a) * RADIUS * 0.92F;
            float rz = (RADIUS * 0.92F) * (RADIUS * 0.92F) / (4F * FOCUS);
            strut(pose, metal2, rx, ry, rz, 0F, 0F, f - 0.06F, 0.012F, lit);
        }
        // Back frame.
        box(pose, metal2, -0.5F, -0.03F, -0.04F, 0.5F, 0.03F, 0.0F, lit);
        box(pose, metal2, -0.03F, -0.5F, -0.04F, 0.03F, 0.5F, 0.0F, lit);
        pose.popPose();
    }

    /** The parabolic bowl, opening toward +z. */
    private static void dish(PoseStack pose, VertexConsumer vc, int light) {
        PoseStack.Pose p = pose.last();
        for (int k = 0; k < RINGS; k++) {
            float r0 = RADIUS * k / RINGS;
            float r1 = RADIUS * (k + 1) / RINGS;
            float z0 = r0 * r0 / (4F * FOCUS);
            float z1 = r1 * r1 / (4F * FOCUS);
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = i * Mth.TWO_PI / SEGMENTS;
                float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
                float u0 = (float) i / SEGMENTS * 4F;
                float u1 = (float) (i + 1) / SEGMENTS * 4F;
                float v0 = (float) k / RINGS;
                float v1 = (float) (k + 1) / RINGS;
                vertex(p, vc, Mth.cos(a0) * r0, Mth.sin(a0) * r0, z0, u0, v0, light, normal(a0, r0));
                vertex(p, vc, Mth.cos(a1) * r0, Mth.sin(a1) * r0, z0, u1, v0, light, normal(a1, r0));
                vertex(p, vc, Mth.cos(a1) * r1, Mth.sin(a1) * r1, z1, u1, v1, light, normal(a1, r1));
                vertex(p, vc, Mth.cos(a0) * r1, Mth.sin(a0) * r1, z1, u0, v1, light, normal(a0, r1));
            }
        }
        // Rim.
        float rz = RADIUS * RADIUS / (4F * FOCUS);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS;
            float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            Vector3f n = new Vector3f(Mth.cos(a0), Mth.sin(a0), 0F);
            vertex(p, vc, Mth.cos(a0) * RADIUS, Mth.sin(a0) * RADIUS, rz, 0F, 0.95F, light, n);
            vertex(p, vc, Mth.cos(a1) * RADIUS, Mth.sin(a1) * RADIUS, rz, 0.2F, 0.95F, light, n);
            vertex(p, vc, Mth.cos(a1) * RADIUS, Mth.sin(a1) * RADIUS, rz - 0.04F, 0.2F, 1F, light, n);
            vertex(p, vc, Mth.cos(a0) * RADIUS, Mth.sin(a0) * RADIUS, rz - 0.04F, 0F, 1F, light, n);
        }
    }

    private static Vector3f normal(float a, float r) {
        // Inner surface of z = r^2 / 4f faces toward +z, tipped inward.
        float slope = r / (2F * FOCUS);
        return new Vector3f(-Mth.cos(a) * slope, -Mth.sin(a) * slope, 1F).normalize();
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int light, Vector3f n) {
        vc.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, n.x, n.y, n.z);
    }

    private static void strut(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float w, int light) {
        PoseStack.Pose p = pose.last();
        Vector3f d = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        Vector3f side = new Vector3f(d).cross(0F, 0F, 1F);
        if (side.lengthSquared() < 1.0E-6F) {
            side.set(1F, 0F, 0F);
        }
        side.normalize(w);
        Vector3f up = new Vector3f(d).cross(side).normalize(w);
        for (Vector3f s : new Vector3f[]{side, up}) {
            Vector3f n = new Vector3f(s).normalize();
            vertex(p, vc, x0 - s.x, y0 - s.y, z0 - s.z, 0F, 0F, light, n);
            vertex(p, vc, x0 + s.x, y0 + s.y, z0 + s.z, 0.1F, 0F, light, n);
            vertex(p, vc, x1 + s.x, y1 + s.y, z1 + s.z, 0.1F, 1F, light, n);
            vertex(p, vc, x1 - s.x, y1 - s.y, z1 - s.z, 0F, 1F, light, n);
        }
    }

    private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        PoseStack.Pose p = pose.last();
        float u = Math.max(0.1F, x1 - x0), v = Math.max(0.1F, y1 - y0), w = Math.max(0.1F, z1 - z0);
        // -z, +z, -x, +x, -y, +y
        quad(p, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, u, v, 0, 0, -1, light);
        quad(p, vc, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, u, v, 0, 0, 1, light);
        quad(p, vc, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, w, v, -1, 0, 0, light);
        quad(p, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, w, v, 1, 0, 0, light);
        quad(p, vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, u, w, 0, -1, 0, light);
        quad(p, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u, w, 0, 1, 0, light);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float us, float vs, float nx, float ny, float nz, int light) {
        vc.addVertex(p, ax, ay, az).setColor(255, 255, 255, 255).setUv(0, vs).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, bx, by, bz).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, cx, cy, cz).setColor(255, 255, 255, 255).setUv(us, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, dx, dy, dz).setColor(255, 255, 255, 255).setUv(us, vs).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(RadarBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(RadarBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0, 0, 1.0).expandTowards(0, 2.5, 0);
    }
}
