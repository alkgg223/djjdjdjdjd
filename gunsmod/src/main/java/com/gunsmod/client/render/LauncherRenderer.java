package com.gunsmod.client.render;

import com.gunsmod.block.LauncherBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Launch rail (erector) that raises the missile from horizontal to vertical before ignition. */
public class LauncherRenderer implements BlockEntityRenderer<LauncherBlockEntity> {
    public LauncherRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(LauncherBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        float elev = be.erectAngle(pt);
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - be.facing().toYRot()));
        pose.translate(-0.5f, 0f, -0.5f);
        // hinge at the back of the platform
        pose.translate(0.5f, 0.56f, 0.875f);
        pose.mulPose(Axis.XP.rotationDegrees(-90f + elev));
        pose.scale(1.25f, 1.25f, 1.25f);
        pose.translate(-0.5f, 0f, -10f / 16f);
        BakedRender.entityCutout("block/launcher_erector", pose, buf, light, overlay, 1f, 1f, 1f);
        int state = be.getState();
        if (be.hasMissile() || state == LauncherBlockEntity.ERECTING || state == LauncherBlockEntity.IGNITION) {
            pose.pushPose();
            pose.translate(0.5f, 15f / 16f, 0.5f);
            MissileRenderer.renderMissile(pose, buf, light, Math.max(0, be.missileKind()), 0.65f);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LauncherBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(LauncherBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3.5);
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
