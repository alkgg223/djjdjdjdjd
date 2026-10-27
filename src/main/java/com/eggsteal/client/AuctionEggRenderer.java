package com.eggsteal.client;

import com.eggsteal.AuctionEggEntity;
import com.eggsteal.EggSteal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The rare egg on the center belt: a big spinning egg item with its price floating above. */
public class AuctionEggRenderer extends EntityRenderer<AuctionEggEntity> {
    public AuctionEggRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(AuctionEggEntity e) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    public void render(AuctionEggEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ItemStack stack = new ItemStack(EggSteal.EGG_ITEMS.get(Math.floorMod(e.variant(), EggSteal.EGG_ITEMS.size())).get());
        ps.pushPose();
        ps.translate(0, 0.55 + Math.sin((e.tickCount + pt) * 0.1) * 0.06, 0);
        ps.mulPose(Axis.YP.rotationDegrees((e.tickCount + pt) * 4f));
        ps.scale(2.2f, 2.2f, 2.2f);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, 0xF000F0, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }
}
