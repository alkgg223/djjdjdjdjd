package com.rpbusiness.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rpbusiness.RPBusiness;
import com.rpbusiness.entity.NpcEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/** GeckoLib renderer shared by customers, staff and couriers. Outfit + skin choose the texture. */
public class NpcRenderer extends GeoEntityRenderer<NpcEntity> {
    private static final String[] OUTFITS = {"customer", "chef", "waiter", "cashier", "cleaner", "manager", "courier"};
    private static final ResourceLocation[][] TEX = new ResourceLocation[OUTFITS.length][8];

    static {
        for (int o = 0; o < OUTFITS.length; o++)
            for (int s = 0; s < 8; s++) TEX[o][s] = RPBusiness.id("textures/entity/npc/" + OUTFITS[o] + "_" + (o == 0 ? s : s % 4) + ".png");
    }

    public static class Model extends DefaultedEntityGeoModel<NpcEntity> {
        public Model() { super(RPBusiness.id("npc"), true); }

        @Override
        public ResourceLocation getTextureResource(NpcEntity e) {
            int o = Math.max(0, Math.min(OUTFITS.length - 1, e.outfit()));
            return TEX[o][Math.floorMod(e.skin(), 8)];
        }
    }

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0.45f;
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, NpcEntity e) {
                return bone.getName().equals("right_item") && !e.getMainHandItem().isEmpty() ? e.getMainHandItem() : null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, NpcEntity e) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack ps, GeoBone bone, ItemStack stack, NpcEntity e, MultiBufferSource buf,
                                              float pt, int light, int overlay) {
                ps.mulPose(Axis.XP.rotationDegrees(-90));
                ps.translate(0, 0.1, -0.05);
                super.renderStackForBone(ps, bone, stack, e, buf, pt, light, overlay);
            }
        });
    }

    @Override
    public void render(NpcEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (e.isKid()) {
            ps.pushPose();
            ps.scale(0.62f, 0.62f, 0.62f);
            super.render(e, yaw, pt, ps, buf, light);
            ps.popPose();
        } else super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public void preRender(PoseStack poseStack, NpcEntity e, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        int o = e.outfit();
        model.getBone("chef_hat").ifPresent(b -> b.setHidden(o != 1));
        model.getBone("cap").ifPresent(b -> b.setHidden(o != 6));
        super.preRender(poseStack, e, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
