package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;

/** Emits a textured cuboid into an entity-format vertex consumer. */
final class Box {
    private Box() {}

    static void cuboid(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float x0, float y0, float z0, float x1, float y1, float z1, int light, int color) {
        PoseStack.Pose pose = ps.last();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        face(pose, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u0, v0, u1, v1, light, color);
        face(pose, vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0, u0, v0, u1, v1, light, color);
        face(pose, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, u0, v0, u1, v1, light, color);
        face(pose, vc, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, u0, v0, u1, v1, light, color);
        face(pose, vc, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, u0, v0, u1, v1, light, color);
        face(pose, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, u0, v0, u1, v1, light, color);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz,
                             float u0, float v0, float u1, float v1, int light, int color) {
        Matrix4f m = pose.pose();
        vc.addVertex(m, ax, ay, az).setColor(color).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, bx, by, bz).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, cx, cy, cz).setColor(color).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, dx, dy, dz).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
