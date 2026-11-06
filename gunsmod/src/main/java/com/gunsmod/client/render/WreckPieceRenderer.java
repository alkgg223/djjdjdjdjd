package com.gunsmod.client.render;

import com.gunsmod.entity.WreckPieceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** A wreck piece: scorched, tumbling while it falls, lying where it came down. */
public class WreckPieceRenderer extends EntityRenderer<WreckPieceEntity> {
    public WreckPieceRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.8f;
    }

    @Override
    public void render(WreckPieceEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float s = e.scale();
        if (e.isHulk()) {
            // a vehicle hulk: its own model blackened, lying where it came down (a blown-off turret tumbles on the way)
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot()));
            float spin = Mth.lerp(pt, e.spinO, e.spin);
            if (spin != 0) pose.mulPose(Axis.XP.rotationDegrees(spin));
            pose.scale(s, s, s);
            pose.translate(-0.5f, 0f, -0.5f);
            BakedRender.entity(e.model(), pose, buf, light, OverlayTexture.NO_OVERLAY, 0.2f, 0.19f, 0.18f);
            pose.popPose();
            super.render(e, yaw, pt, pose, buf, light);
            return;
        }
        pose.pushPose();
        pose.translate(0, 0.2f * s, 0);
        pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
        float spin = Mth.lerp(pt, e.spinO, e.spin);
        pose.mulPose(Axis.XP.rotationDegrees(e.tiltX + spin));
        pose.mulPose(Axis.ZP.rotationDegrees(e.tiltZ + spin * 0.6f));
        pose.scale(s, s, s);
        pose.translate(-0.5f, 0f, -0.5f);
        // charred paint: the skin material is tinted dark
        BakedRender.entity("entity/debris/" + e.kind(), pose, buf, light, OverlayTexture.NO_OVERLAY, 0.32f, 0.31f, 0.3f);
        pose.popPose();
        super.render(e, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(WreckPieceEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
