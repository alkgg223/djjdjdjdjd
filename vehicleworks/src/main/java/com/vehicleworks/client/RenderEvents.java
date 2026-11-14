package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vehicleworks.Config;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleRegistry;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

/**
 * Draws every visible vehicle's cached mesh with one transform per layer. Frustum culling
 * uses the vehicle bounds; beyond the far LOD distance only the coarse silhouette is drawn.
 */
public final class RenderEvents {
    private static long lastFrame;
    public static int drawnVehicles, culledVehicles;

    private RenderEvents() {}

    public static void onRenderStage(RenderLevelStageEvent e) {
        var stage = e.getStage();
        boolean solid = stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
        boolean cutout = stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        boolean translucent = stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (!solid && !cutout && !translucent) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        if (solid) {
            long now = Util.getNanos();
            float frame = lastFrame == 0 ? 0 : (float) Math.min(0.1, (now - lastFrame) / 1e9);
            lastFrame = now;
            drawnVehicles = 0;
            culledVehicles = 0;
            for (VehicleEntity v : VehicleRegistry.all(mc.level)) VehicleRenderer.advancePhases(v, frame);
        }
        double far = Config.CLIENT.farLodDistance.get();
        for (VehicleEntity v : VehicleRegistry.all(mc.level)) {
            if (v.isRemoved() || v.structure.isEmpty()) continue;
            VehicleClientState s = ClientProxy.state(v);
            if (s.mesh == null) s.mesh = new VehicleMesh();
            if (solid) s.mesh.update(v);
            if (!s.mesh.ready()) continue;
            if (!e.getFrustum().isVisible(v.getBoundingBoxForCulling())) {
                if (solid) culledVehicles++;
                continue;
            }
            Vec3 pos = v.renderPosition(partial);
            double dist = pos.distanceTo(cp);
            boolean lod = dist > far;
            Matrix4f mv = new Matrix4f(e.getModelViewMatrix());
            mv.translate((float) (pos.x - cp.x), (float) (pos.y - cp.y), (float) (pos.z - cp.z));
            mv.rotate(new Quaternionf(v.renderOrientation(partial, new Quaterniond())));
            mv.translate((float) -v.comLocal.x, (float) -v.comLocal.y, (float) -v.comLocal.z);
            if (solid) {
                s.mesh.draw(RenderType.solid(), mv, e.getProjectionMatrix(), lod);
                drawnVehicles++;
            } else if (cutout && !lod) {
                s.mesh.draw(RenderType.cutoutMipped(), mv, e.getProjectionMatrix(), false);
                s.mesh.draw(RenderType.cutout(), mv, e.getProjectionMatrix(), false);
            } else if (translucent && !lod) {
                s.mesh.draw(RenderType.translucent(), mv, e.getProjectionMatrix(), false);
            }
        }
        if (translucent) renderTargetOutline(e, partial, cp);
    }

    /** Outline of the vehicle block under the crosshair. */
    private static void renderTargetOutline(RenderLevelStageEvent e, float partial, Vec3 cp) {
        if (!(Minecraft.getInstance().hitResult instanceof VehicleHitResult hit)) return;
        VehicleEntity v = hit.vehicle();
        PoseStack ps = new PoseStack();
        ps.mulPose(e.getModelViewMatrix());
        Vec3 pos = v.renderPosition(partial);
        ps.translate(pos.x - cp.x, pos.y - cp.y, pos.z - cp.z);
        ps.mulPose(new Quaternionf(v.renderOrientation(partial, new Quaterniond())));
        ps.translate(-v.comLocal.x, -v.comLocal.y, -v.comLocal.z);
        long k = hit.block();
        var st = v.structure.get(k);
        VoxelShape shape = st.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        MultiBufferSource.BufferSource buf = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        for (AABB b : shape.isEmpty() ? java.util.List.of(new AABB(0, 0, 0, 1, 1, 1)) : shape.toAabbs()) {
            LevelRenderer.renderLineBox(ps, lines, b.move(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)).inflate(0.002), 0f, 0f, 0f, 0.55f);
        }
        buf.endBatch(RenderType.lines());
    }
}
