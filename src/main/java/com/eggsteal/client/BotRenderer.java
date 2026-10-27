package com.eggsteal.client;

import com.eggsteal.BotEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Bots look like real players: each one gets one of the game's default skins (Steve, Alex, Ari, Efe, Kai, Makena,
 * Noor, Sunny, Zuri) from its UUID, with the right arm width and the full outer skin layer (jacket, sleeves, hat).
 */
public class BotRenderer extends HumanoidMobRenderer<BotEntity, PlayerModel<BotEntity>> {
    private final PlayerModel<BotEntity> wide, slim;

    public BotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wide = model;
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    static PlayerSkin skin(BotEntity e) { return DefaultPlayerSkin.get(e.getUUID()); }

    @Override
    public void render(BotEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        model = skin(e).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BotEntity e) {
        return skin(e).texture();
    }
}
