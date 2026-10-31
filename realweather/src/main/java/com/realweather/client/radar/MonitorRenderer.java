package com.realweather.client.radar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.realweather.device.MonitorBlock;
import com.realweather.device.MonitorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Puts the live radar picture on the monitor's screen (it glows, so it is drawn at full brightness). */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    private static final int FULL_BRIGHT = 0xF000F0;

    public MonitorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(MonitorBlock.FACING)) {
            return;
        }
        RadarCanvas canvas = RadarClient.screenFor(level, be.getBlockPos(), state);
        Direction facing = state.getValue(MonitorBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.text(canvas.id));
        float z = 7.9F / 16F;
        float x0 = 3F / 16F, x1 = 13F / 16F, y0 = 10F / 16F, y1 = 20F / 16F;
        // Seen from the front (north) the viewer's right is west (-x).
        vc.addVertex(m, x1, y1, z).setColor(255, 255, 255, 255).setUv(0F, 0F).setLight(FULL_BRIGHT);
        vc.addVertex(m, x1, y0, z).setColor(255, 255, 255, 255).setUv(0F, 1F).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y0, z).setColor(255, 255, 255, 255).setUv(1F, 1F).setLight(FULL_BRIGHT);
        vc.addVertex(m, x0, y1, z).setColor(255, 255, 255, 255).setUv(1F, 0F).setLight(FULL_BRIGHT);
        // The sweeping beam.
        float ang = canvas.beamAngle();
        float cx = (x0 + x1) / 2F, cy = (y0 + y1) / 2F, r = (x1 - x0) / 2F * 0.98F;
        float ex = cx - (float) Math.sin(ang) * r;
        float ey = cy + (float) Math.cos(ang) * r;
        float nx = -(ey - cy) / r * 0.004F, ny = (ex - cx) / r * 0.004F;
        VertexConsumer beam = buffers.getBuffer(RenderType.textSeeThrough(canvas.id));
        float zb = z - 0.002F;
        beam.addVertex(m, cx - nx, cy - ny, zb).setColor(120, 255, 140, 150).setUv(0.5F, 0.5F).setLight(FULL_BRIGHT);
        beam.addVertex(m, ex - nx, ey - ny, zb).setColor(120, 255, 140, 60).setUv(0.5F, 0.5F).setLight(FULL_BRIGHT);
        beam.addVertex(m, ex + nx, ey + ny, zb).setColor(120, 255, 140, 60).setUv(0.5F, 0.5F).setLight(FULL_BRIGHT);
        beam.addVertex(m, cx + nx, cy + ny, zb).setColor(120, 255, 140, 150).setUv(0.5F, 0.5F).setLight(FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
