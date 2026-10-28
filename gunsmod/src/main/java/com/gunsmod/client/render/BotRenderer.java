package com.gunsmod.client.render;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/** Soldiers: player model with camo skins, a 3D helmet and plate carrier, aiming pose with guns. */
public class BotRenderer extends HumanoidMobRenderer<BotEntity, BotRenderer.SoldierModel> {
    private static final ResourceLocation[][] SKINS = new ResourceLocation[2][4];

    static {
        for (int t = 0; t < 2; t++) {
            for (int v = 0; v < 4; v++) SKINS[t][v] = Reg.id("textures/entity/soldier_" + (t == 0 ? "green" : "red") + "_" + v + ".png");
        }
    }

    public BotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SoldierModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        this.addLayer(new GearLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(BotEntity bot) {
        return SKINS[bot.getBotTeam().ordinal()][bot.getSkin() & 3];
    }

    @Override
    protected void scale(BotEntity bot, PoseStack pose, float pt) {
        pose.scale(0.9375f, 0.9375f, 0.9375f);
    }

    public static class SoldierModel extends PlayerModel<BotEntity> {
        public SoldierModel(ModelPart root) {
            super(root, false);
        }

        @Override
        public void prepareMobModel(BotEntity bot, float limbSwing, float limbSwingAmount, float pt) {
            super.prepareMobModel(bot, limbSwing, limbSwingAmount, pt);
            boolean gun = bot.getMainHandItem().getItem() instanceof GunItem;
            this.rightArmPose = gun ? HumanoidModel.ArmPose.BOW_AND_ARROW : bot.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            this.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            this.crouching = bot.isCrouching();
        }
    }

    /** helmet and plate carrier drawn as 3D models, tinted with the team colour */
    static class GearLayer extends RenderLayer<BotEntity, SoldierModel> {
        GearLayer(RenderLayerParent<BotEntity, SoldierModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buf, int light, BotEntity bot, float limbSwing, float limbSwingAmount, float pt,
                           float age, float headYaw, float headPitch) {
            BotTeam team = bot.getBotTeam();
            float r = team == BotTeam.GREEN ? 0.36f : 0.72f, g = team == BotTeam.GREEN ? 0.42f : 0.62f, b = team == BotTeam.GREEN ? 0.26f : 0.45f;
            if (bot.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                pose.pushPose();
                this.getParentModel().head.translateAndRotate(pose);
                gear(pose, buf, light, "entity/gear/helmet", r, g, b);
                pose.popPose();
            }
            if (bot.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                pose.pushPose();
                this.getParentModel().body.translateAndRotate(pose);
                gear(pose, buf, light, "entity/gear/vest", r * 0.9f, g * 0.9f, b * 0.9f);
                pose.popPose();
            }
        }

        private static void gear(PoseStack pose, MultiBufferSource buf, int light, String model, float r, float g, float b) {
            // model space: y up, front -Z, +X = soldier's right; part space: y down
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.scale(1f, -1f, -1f);
            pose.translate(-0.5f, 0f, -0.5f);
            BakedRender.entityCutout(model, pose, buf, light, OverlayTexture.NO_OVERLAY, r, g, b);
        }
    }
}
