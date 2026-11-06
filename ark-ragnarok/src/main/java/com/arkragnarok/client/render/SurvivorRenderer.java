package com.arkragnarok.client.render;

import com.arkragnarok.survivor.Survivor;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SurvivorRenderer extends HumanoidMobRenderer<Survivor, PlayerModel<Survivor>> {
    private static final ResourceLocation[] SKINS;

    static {
        String[] n = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
        SKINS = new ResourceLocation[n.length];
        for (int i = 0; i < n.length; i++) SKINS[i] = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + n[i] + ".png");
    }

    public SurvivorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Survivor s) {
        return SKINS[Math.floorMod(s.skin(), SKINS.length)];
    }
}
