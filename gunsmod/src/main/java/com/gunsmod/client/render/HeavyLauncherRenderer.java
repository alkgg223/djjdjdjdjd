package com.gunsmod.client.render;

import com.gunsmod.block.HeavyLauncherBlockEntity;
import com.gunsmod.block.LauncherBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * Draws the whole 3 x 5 heavy launcher from the centre block: the platform, the erector raising the missile and the
 * missile itself. The models are built at half scale (see tools/blocks.py, preview_heavy.py) and drawn 2x.
 */
public class HeavyLauncherRenderer implements BlockEntityRenderer<HeavyLauncherBlockEntity> {
    /** hinge height inside the erector model */
    private static final float ERECTOR_Y0 = -4f;

    public HeavyLauncherRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(HeavyLauncherBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - be.facing().toYRot()));
        pose.scale(2f, 2f, 2f);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entityCutout("block/heavy_launcher_base", pose, buf, light, overlay, 1f, 1f, 1f);
        // erector: lies forward over the cab at 0 degrees, stands up at 90
        pose.translate(8f / 16f, HeavyLauncherBlockEntity.HINGE_Y / 16f, HeavyLauncherBlockEntity.HINGE_Z / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(-90f + be.erectAngle(pt)));
        pose.translate(-8f / 16f, -ERECTOR_Y0 / 16f, -8f / 16f);
        BakedRender.entityCutout("block/heavy_launcher_erector", pose, buf, light, overlay, 1f, 1f, 1f);
        int state = be.getState();
        if (be.hasMissile() || state == LauncherBlockEntity.ERECTING || state == LauncherBlockEntity.IGNITION) {
            float k = HeavyLauncherBlockEntity.MISSILE_SCALE;
            pose.translate(8f / 16f, (ERECTOR_Y0 + 22f * k) / 16f, HeavyLauncherBlockEntity.MISSILE_Z / 16f);
            MissileRenderer.renderMissile(pose, buf, light, Math.max(3, be.missileKind()), k);
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(HeavyLauncherBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(HeavyLauncherBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3.5).expandTowards(0, 5, 0);
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
