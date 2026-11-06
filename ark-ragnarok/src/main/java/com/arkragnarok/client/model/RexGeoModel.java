package com.arkragnarok.client.model;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.RexEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/** Skeleton + animations of the Rex.  The geometry itself is the skinned mesh drawn by RexRenderer. */
public class RexGeoModel extends GeoModel<RexEntity> {
    private static final ResourceLocation GEO = ArkRagnarok.id("geo/entity/rex.geo.json");
    private static final ResourceLocation ANIM = ArkRagnarok.id("animations/entity/rex.animation.json");
    public static final ResourceLocation TEX = ArkRagnarok.id("textures/entity/rex/rex_lod0.png");
    private static final String[] TAIL = {"tail1", "tail2", "tail3", "tail4", "tail5", "tail6", "tail7", "tail8"};

    @Override
    public ResourceLocation getModelResource(RexEntity animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(RexEntity animatable) {
        return TEX;
    }

    @Override
    public ResourceLocation getAnimationResource(RexEntity animatable) {
        return ANIM;
    }

    /** head tracking spread over the neck, and the tail lagging behind when the body turns */
    @Override
    public void setCustomAnimations(RexEntity rex, long instanceId, AnimationState<RexEntity> state) {
        if (rex.isDeadOrDying() || rex.getForcedAnim() == 5) return;
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data != null) {
            float yaw = Mth.clamp(data.netHeadYaw(), -70f, 70f) * Mth.DEG_TO_RAD;
            float pitch = Mth.clamp(data.headPitch(), -35f, 35f) * Mth.DEG_TO_RAD;
            add(getAnimationProcessor().getBone("neck1"), pitch * 0.25f, yaw * 0.3f);
            add(getAnimationProcessor().getBone("neck2"), pitch * 0.3f, yaw * 0.3f);
            add(getAnimationProcessor().getBone("head"), pitch * 0.45f, yaw * 0.4f);
        }
        float lag = Mth.lerp(state.getPartialTick(), rex.tailLagO, rex.tailLag) * Mth.DEG_TO_RAD;
        for (int i = 0; i < TAIL.length; i++) add(getAnimationProcessor().getBone(TAIL[i]), 0f, lag * (0.35f + 0.12f * i));
    }

    private static void add(GeoBone bone, float x, float y) {
        if (bone == null) return;
        bone.setRotX(bone.getRotX() + x);
        bone.setRotY(bone.getRotY() + y);
    }
}
