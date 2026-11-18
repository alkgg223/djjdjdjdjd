package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Light effects drawn additively with soft textures: round glows and halos that always face the
 * camera, star flares for strobes / beacons, and soft-edged light beams (a camera-facing shaft that
 * widens and fades with distance). Nothing is a cube.
 */
public final class LightFx {
    private LightFx() {}

    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("vehicleworks", "textures/misc/glow.png");
    static final ResourceLocation FLARE = ResourceLocation.fromNamespaceAndPath("vehicleworks", "textures/misc/flare.png");
    static final ResourceLocation BEAM = ResourceLocation.fromNamespaceAndPath("vehicleworks", "textures/misc/beam.png");
    private static final int FULL = 0xF000F0;

    /** frame data: camera position and billboard axes in the current local frame */
    private static final Vector3f CAM = new Vector3f(), RIGHT = new Vector3f(), UP = new Vector3f();

    /** Call once with the pose of the frame the lights are given in (before drawing them). */
    static void begin(PoseStack ps) {
        Matrix4f inv = new Matrix4f(ps.last().pose()).invert();
        inv.transformPosition(CAM.set(0, 0, 0));
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        inv.transformDirection(RIGHT.set(cam.getLeftVector())).normalize().negate();
        inv.transformDirection(UP.set(cam.getUpVector())).normalize();
    }

    /** 0 at midday .. 1 at night (glows and beams are much stronger in the dark) */
    static float night() {
        var level = Minecraft.getInstance().level;
        if (level == null) return 1;
        float sky = level.getSkyDarken(1f); // 1 = bright day
        return Mth.clamp((1f - sky) / 0.75f, 0, 1);
    }

    static float distance(float x, float y, float z) {
        return (float) Math.sqrt((x - CAM.x) * (x - CAM.x) + (y - CAM.y) * (y - CAM.y) + (z - CAM.z) * (z - CAM.z));
    }

    /** camera-facing textured quad; `intensity` scales the emitted colour (additive) */
    static void sprite(PoseStack ps, MultiBufferSource buf, ResourceLocation tex, float x, float y, float z, float size, int rgb, float intensity) {
        if (intensity <= 0.003f || size <= 0) return;
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(tex));
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        float k = Math.min(1.5f, intensity);
        float r = ((rgb >> 16) & 255) / 255f * k, g = ((rgb >> 8) & 255) / 255f * k, b = (rgb & 255) / 255f * k;
        float rx = RIGHT.x * size, ry = RIGHT.y * size, rz = RIGHT.z * size, ux = UP.x * size, uy = UP.y * size, uz = UP.z * size;
        v(vc, p, m, x - rx - ux, y - ry - uy, z - rz - uz, 0, 1, r, g, b);
        v(vc, p, m, x + rx - ux, y + ry - uy, z + rz - uz, 1, 1, r, g, b);
        v(vc, p, m, x + rx + ux, y + ry + uy, z + rz + uz, 1, 0, r, g, b);
        v(vc, p, m, x - rx + ux, y - ry + uy, z - rz + uz, 0, 0, r, g, b);
    }

    /**
     * Soft light shaft from (ox,oy,oz) along the unit axis (ax,ay,az): a quad turned toward the
     * camera around the beam axis, r0 wide at the lamp and r1 at the end, fading along its length.
     */
    static void beam(PoseStack ps, MultiBufferSource buf, float ox, float oy, float oz, float ax, float ay, float az, float len, float r0, float r1, int rgb, float intensity) {
        if (intensity <= 0.003f) return;
        // end the shaft softly where it meets the ground / a wall (no hard intersection line)
        float hit = groundHit(ps, ox, oy, oz, ax, ay, az, len);
        if (hit < len) {
            r1 = r0 + (r1 - r0) * hit / len;
            len = Math.max(0.5f, hit);
        }
        float mx = ox + ax * len * 0.3f, my = oy + ay * len * 0.3f, mz = oz + az * len * 0.3f;
        Vector3f toCam = new Vector3f(CAM.x - mx, CAM.y - my, CAM.z - mz);
        Vector3f side = new Vector3f(ax, ay, az).cross(toCam);
        if (side.lengthSquared() < 1e-6f) return; // looking straight down the beam: the lamp glow covers it
        side.normalize();
        // seen along the axis the shaft is thin: fade it so it never looks like a flat card
        float along = Math.abs(new Vector3f(ax, ay, az).dot(toCam.normalize()));
        float k = Math.min(1.5f, intensity) * (1f - 0.75f * along * along);
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(BEAM));
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        float r = ((rgb >> 16) & 255) / 255f * k, g = ((rgb >> 8) & 255) / 255f * k, b = (rgb & 255) / 255f * k;
        float ex = ox + ax * len, ey = oy + ay * len, ez = oz + az * len;
        v(vc, p, m, ox - side.x * r0, oy - side.y * r0, oz - side.z * r0, 0, 0, r, g, b);
        v(vc, p, m, ox + side.x * r0, oy + side.y * r0, oz + side.z * r0, 1, 0, r, g, b);
        v(vc, p, m, ex + side.x * r1, ey + side.y * r1, ez + side.z * r1, 1, 1, r, g, b);
        v(vc, p, m, ex - side.x * r1, ey - side.y * r1, ez - side.z * r1, 0, 1, r, g, b);
    }

    /** distance along the beam to the first world block (the beam fades to zero there) */
    static float groundHit(PoseStack ps, float ox, float oy, float oz, float ax, float ay, float az, float len) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return len;
        Matrix4f m = ps.last().pose();
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        Vector3f a = m.transformPosition(new Vector3f(ox, oy, oz)), b = m.transformPosition(new Vector3f(ox + ax * len, oy + ay * len, oz + az * len));
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(a.x + cam.x, a.y + cam.y, a.z + cam.z), to = new net.minecraft.world.phys.Vec3(b.x + cam.x, b.y + cam.y, b.z + cam.z);
        var r = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (r.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return len;
        return (float) r.getLocation().distanceTo(from) * 1.15f; // a little past the hit: the fade ends on the ground
    }

    private static void v(VertexConsumer vc, PoseStack.Pose p, Matrix4f m, float x, float y, float z, float u, float vv, float r, float g, float b) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, 1f).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(p, 0, 1, 0);
    }
}
