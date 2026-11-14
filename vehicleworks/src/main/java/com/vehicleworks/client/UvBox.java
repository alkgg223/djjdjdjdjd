package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/** Textured cuboid with explicit UV rectangle (for standalone entity textures). */
final class UvBox {
    private UvBox() {}

    static void cuboid(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int light, int color,
                       float u0, float v0, float u1, float v1) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        float[][][] faces = {
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
                {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {0, -1, 0}},
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {0, 0, -1}},
                {{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {0, 0, 1}},
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {1, 0, 0}}};
        float[][] uv = {{u0, v1}, {u0, v0}, {u1, v0}, {u1, v1}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.addVertex(m, f[i][0], f[i][1], f[i][2]).setColor(color).setUv(uv[i][0], uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(pose, f[4][0], f[4][1], f[4][2]);
            }
        }
    }
}
