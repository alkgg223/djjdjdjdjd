package com.vehicleworks.client;

import com.vehicleworks.vehicle.NpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Passengers and the AI first officer look like players (one of the default skins, chosen by UUID). */
public class NpcRenderer extends HumanoidMobRenderer<NpcEntity, PlayerModel<NpcEntity>> {
    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity e) {
        return DefaultPlayerSkin.get(e.getUUID()).texture();
    }
}
