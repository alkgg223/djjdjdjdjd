package com.arkragnarok.client.render;

import com.arkragnarok.entity.TranqArrow;
import com.arkragnarok.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** the arrow in flight / stuck in the ground is the same 3D model as the item (shaft, fletching, dart vial, needle) */
public class TranqArrowRenderer extends EntityRenderer<TranqArrow> {
    private final ItemRenderer items;
    private final ItemStack stack;

    public TranqArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.stack = new ItemStack(ModItems.TRANQ_ARROW.get());
    }

    @Override
    public void render(TranqArrow arrow, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, arrow.yRotO, arrow.getYRot()) - 90.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, arrow.xRotO, arrow.getXRot())));
        float shake = arrow.shakeTime - partialTick;
        if (shake > 0) pose.mulPose(Axis.ZP.rotationDegrees(-Mth.sin(shake * 3.0f) * shake));
        // the item model lies on the diagonal (tip up and right): turn the tip onto +x, the flight direction
        pose.mulPose(Axis.ZP.rotationDegrees(-45.0f));
        pose.scale(0.72f, 0.72f, 0.72f);
        items.renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, arrow.level(), arrow.getId());
        pose.popPose();
        super.render(arrow, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(TranqArrow arrow) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
