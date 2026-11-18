package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Geometry for cockpit hardware: prisms / cylinders (knobs, toggle bats, nuts, bezels), tapered
 * levers and lit glass. Everything hangs from a plate at y = 0 toward -y (overhead panel) and is
 * shaded by the entity lighting (normals per face), so parts read as 3D objects, not flat squares.
 */
final class CockpitParts {
    private CockpitParts() {}

    static final String METAL = "ck_metal", BLACK = "ck_black", KNOB = "ck_knob", GLASS = "ck_glass", GUARD = "ck_guard", BEZEL = "ck_bezel", WHITE = "ck_white";

    static TextureAtlasSprite sprite(String name) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath("vehicleworks", "block/" + name));
    }

    static void vtx(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int color, int light, float nx, float ny, float nz) {
        vc.addVertex(p.pose(), x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    /**
     * Vertical prism (n sides) from y0 (top, toward the plate) to y1 (bottom), radius r0 at the top
     * and r1 at the bottom (tapered levers), with a bottom cap. Every other side face can be tinted
     * (knurled knob grip).
     */
    static void prism(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float cx, float cz, float r0, float r1, float y0, float y1,
                      int n, int light, int color, int color2, boolean cap) {
        PoseStack.Pose p = ps.last();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n, am = (a0 + a1) / 2;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float nx = (float) Math.cos(am), nz = (float) Math.sin(am);
            int col = (i & 1) == 0 ? color : color2;
            float uu0 = u0 + (u1 - u0) * i / n, uu1 = u0 + (u1 - u0) * (i + 1) / n;
            vtx(vc, p, cx + c0 * r0, y0, cz + s0 * r0, uu0, v0, col, light, nx, 0, nz);
            vtx(vc, p, cx + c0 * r1, y1, cz + s0 * r1, uu0, v1, col, light, nx, 0, nz);
            vtx(vc, p, cx + c1 * r1, y1, cz + s1 * r1, uu1, v1, col, light, nx, 0, nz);
            vtx(vc, p, cx + c1 * r0, y0, cz + s1 * r0, uu1, v0, col, light, nx, 0, nz);
            if (cap) {
                float um = (u0 + u1) / 2, vm = (v0 + v1) / 2;
                vtx(vc, p, cx, y1, cz, um, vm, color, light, 0, -1, 0);
                vtx(vc, p, cx + c1 * r1, y1, cz + s1 * r1, um + (u1 - u0) * 0.5f * c1, vm + (v1 - v0) * 0.5f * s1, color, light, 0, -1, 0);
                vtx(vc, p, cx + c0 * r1, y1, cz + s0 * r1, um + (u1 - u0) * 0.5f * c0, vm + (v1 - v0) * 0.5f * s0, color, light, 0, -1, 0);
                vtx(vc, p, cx, y1, cz, um, vm, color, light, 0, -1, 0);
            }
        }
    }

    /** Flat ring (bezel / washer) on a horizontal plane at height y, facing down. */
    static void ring(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float cx, float cz, float ri, float ro, float y, int n, int light, int color) {
        PoseStack.Pose p = ps.last();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vtx(vc, p, cx + c0 * ri, y, cz + s0 * ri, u0, v0, color, light, 0, -1, 0);
            vtx(vc, p, cx + c1 * ri, y, cz + s1 * ri, u1, v0, color, light, 0, -1, 0);
            vtx(vc, p, cx + c1 * ro, y, cz + s1 * ro, u1, v1, color, light, 0, -1, 0);
            vtx(vc, p, cx + c0 * ro, y, cz + s0 * ro, u0, v1, color, light, 0, -1, 0);
        }
    }

    /** Small sphere-ish ball (two cones + band), e.g. the tip of a toggle bat. */
    static void ball(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float cx, float cy, float cz, float r, int light, int color) {
        prism(ps, vc, sp, cx, cz, r * 0.55f, r, cy + r * 0.8f, cy + r * 0.25f, 8, light, color, color, false);
        prism(ps, vc, sp, cx, cz, r, r, cy + r * 0.25f, cy - r * 0.25f, 8, light, color, color, false);
        prism(ps, vc, sp, cx, cz, r, r * 0.55f, cy - r * 0.25f, cy - r * 0.8f, 8, light, color, color, true);
    }

    /** Down-facing quad (lens, legend plate) on the plane y, emissive when light = FULL. */
    static void plate(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float x0, float z0, float x1, float z1, float y, int light, int color) {
        PoseStack.Pose p = ps.last();
        vtx(vc, p, x0, y, z0, sp.getU0(), sp.getV0(), color, light, 0, -1, 0);
        vtx(vc, p, x0, y, z1, sp.getU0(), sp.getV1(), color, light, 0, -1, 0);
        vtx(vc, p, x1, y, z1, sp.getU1(), sp.getV1(), color, light, 0, -1, 0);
        vtx(vc, p, x1, y, z0, sp.getU1(), sp.getV0(), color, light, 0, -1, 0);
    }

    /** packed light with the block light raised to at least `block` (a lamp lighting the surfaces around it) */
    static int boost(int packed, float block) {
        int b = Math.max(net.minecraft.client.renderer.LightTexture.block(packed), Math.min(15, Math.round(block)));
        return net.minecraft.client.renderer.LightTexture.pack(b, net.minecraft.client.renderer.LightTexture.sky(packed));
    }

    static int scale(int argb, float k) {
        k = Math.max(0, Math.min(1.5f, k));
        int r = Math.min(255, (int) (((argb >> 16) & 255) * k)), g = Math.min(255, (int) (((argb >> 8) & 255) * k)), b = Math.min(255, (int) ((argb & 255) * k));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
