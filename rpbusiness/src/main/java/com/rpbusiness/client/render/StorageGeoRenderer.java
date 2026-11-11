package com.rpbusiness.client.render;

import com.rpbusiness.RPBusiness;
import com.rpbusiness.block.AnimatedStorageBE;
import com.rpbusiness.block.FridgeBlock;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Renders the fridge (2 blocks tall, door) and freezer (lid) with their GeckoLib open/close animations. */
public class StorageGeoRenderer extends GeoBlockRenderer<AnimatedStorageBE> {
    public StorageGeoRenderer() {
        super(new GeoModel<>() {
            private String kind(AnimatedStorageBE be) { return be.getBlockState().getBlock() instanceof FridgeBlock ? "fridge" : "freezer"; }
            @Override public ResourceLocation getModelResource(AnimatedStorageBE be) { return RPBusiness.id("geo/block/" + kind(be) + ".geo.json"); }
            @Override public ResourceLocation getTextureResource(AnimatedStorageBE be) { return RPBusiness.id("textures/block/" + kind(be) + ".png"); }
            @Override public ResourceLocation getAnimationResource(AnimatedStorageBE be) { return RPBusiness.id("animations/block/" + kind(be) + ".animation.json"); }
        });
    }
}
