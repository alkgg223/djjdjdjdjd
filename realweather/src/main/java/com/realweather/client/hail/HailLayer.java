package com.realweather.client.hail;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realweather.RealWeather;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

/** Hailstones stuck to a creature's back and head, growing into an icy crust the longer it stays out. */
public class HailLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation LIGHT = RealWeather.id("textures/entity/hail_light.png");
    private static final ResourceLocation HEAVY = RealWeather.id("textures/entity/hail_heavy.png");

    public HailLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        float c = HailCoverage.get(entity.getId());
        if (c < 0.02F || entity.isInvisible()) {
            return;
        }
        float a = Math.min(1F, c * 1.6F);
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(LIGHT)), light, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color((int) (a * 255), 255, 255, 255));
        if (c > 0.45F) {
            float b = Math.min(1F, (c - 0.45F) * 2.2F);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(HEAVY)), light, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color((int) (b * 255), 255, 255, 255));
        }
    }
}
