package com.eggsteal.client;

import com.eggsteal.BotEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BotRenderer extends HumanoidMobRenderer<BotEntity, HumanoidModel<BotEntity>> {
    public BotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(BotEntity e) {
        return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
}
