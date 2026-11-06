package com.arkragnarok.client.model;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.species.Species;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * Skeleton + animations of one species.  The geometry is the skinned mesh drawn by ArkCreatureRenderer.
 * Head tracking is spread over all neck bones, limited, and faded out while an attack / roar / hurt animation plays,
 * so the neck never folds when the creature bites something close to it.
 */
public class ArkCreatureGeoModel extends GeoModel<ArkCreature> {
    private final Species species;
    private final ResourceLocation geo, anim, tex;
    private String[] neck;
    private String[] tail;

    public ArkCreatureGeoModel(Species species) {
        this.species = species;
        this.geo = ArkRagnarok.id("geo/entity/" + species.id + ".geo.json");
        this.anim = ArkRagnarok.id("animations/entity/" + species.id + ".animation.json");
        this.tex = species.texture(0);
    }

    @Override
    public ResourceLocation getModelResource(ArkCreature animatable) {
        return geo;
    }

    @Override
    public ResourceLocation getTextureResource(ArkCreature animatable) {
        return tex;
    }

    @Override
    public ResourceLocation getAnimationResource(ArkCreature animatable) {
        return anim;
    }

    private void findBones() {
        if (neck != null) return;
        List<String> n = new ArrayList<>(), t = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            if (getAnimationProcessor().getBone("neck" + i) != null) n.add("neck" + i);
            if (getAnimationProcessor().getBone("tail" + i) != null) t.add("tail" + i);
        }
        neck = n.toArray(new String[0]);
        tail = t.toArray(new String[0]);
    }

    @Override
    public void setCustomAnimations(ArkCreature c, long instanceId, AnimationState<ArkCreature> state) {
        findBones();
        AnimationController<?> action = c.getAnimatableInstanceCache().getManagerForId(instanceId).getAnimationControllers().get("action");
        boolean acting = action != null && action.getAnimationState() != AnimationController.State.STOPPED;
        boolean still = c.isDeadOrDying() || c.getForcedAnim() == 5;
        float want = acting || still ? 0f : 1f;
        c.lookWeight += (want - c.lookWeight) * 0.12f;
        float w = c.lookWeight;
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        if (data != null && w > 0.01f) {
            float yaw = Mth.clamp(data.netHeadYaw(), -50f, 50f) * Mth.DEG_TO_RAD * w;
            float pitch = Mth.clamp(data.headPitch(), -22f, 22f) * Mth.DEG_TO_RAD * w;
            int n = Math.max(1, neck.length);
            // the neck takes 60 % of the turn, spread evenly; the head the rest
            for (String b : neck) add(getAnimationProcessor().getBone(b), pitch * 0.5f / n, yaw * 0.6f / n);
            add(getAnimationProcessor().getBone("head"), pitch * 0.5f, yaw * 0.4f);
        }
        if (!still) {
            float lag = Mth.lerp(state.getPartialTick(), c.tailLagO, c.tailLag) * Mth.DEG_TO_RAD;
            for (int i = 0; i < tail.length; i++) add(getAnimationProcessor().getBone(tail[i]), 0f, lag * (0.3f + 0.1f * i));
        }
    }

    private static void add(GeoBone bone, float x, float y) {
        if (bone == null) return;
        bone.setRotX(bone.getRotX() + x);
        bone.setRotY(bone.getRotY() + y);
    }
}
