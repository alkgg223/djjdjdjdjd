package com.gunsmod.client.render;

import com.gunsmod.entity.MineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** Anti-vehicle mine, dug in so only the top of the drum and the pressure plate show. */
public class MineRenderer extends EntityRenderer<MineEntity> {
    public MineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(MineEntity mine, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-mine.getYRot()));
        pose.translate(-0.5f, -0.07f, -0.5f);
        BakedRender.entity("entity/mine/body", pose, buf, light, OverlayTexture.NO_OVERLAY, 1, 1, 1);
        pose.popPose();
        super.render(mine, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MineEntity mine) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
