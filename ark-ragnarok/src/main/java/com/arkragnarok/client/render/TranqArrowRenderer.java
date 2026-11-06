package com.arkragnarok.client.render;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.TranqArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class TranqArrowRenderer extends ArrowRenderer<TranqArrow> {
    private static final ResourceLocation TEX = ArkRagnarok.id("textures/entity/projectiles/tranq_arrow.png");

    public TranqArrowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(TranqArrow arrow) {
        return TEX;
    }
}
