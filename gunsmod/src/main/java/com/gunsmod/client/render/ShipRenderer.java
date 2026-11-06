package com.gunsmod.client.render;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.ShipEntity;
import com.gunsmod.entity.ShipType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** A warship: the hull in its side's grey, rolling gently with the sea, the main gun mount turned to its aim. */
public class ShipRenderer extends EntityRenderer<ShipEntity> {
    public static final String[] PARTS = {"body", "gun", "mark_green", "mark_red"};

    public ShipRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    /** the side's grey: green ships a little warmer, red a little bluer */
    public static void renderShip(PoseStack pose, MultiBufferSource buf, int light, ShipType kind, BotTeam team, float gunYaw, float roll) {
        float r = team == BotTeam.RED ? 0.66f : 0.70f, g = team == BotTeam.RED ? 0.70f : 0.72f, b = team == BotTeam.RED ? 0.78f : 0.68f;
        String dir = "entity/" + kind.model + "/";
        int ov = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.scale(4f, 4f, 4f);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity(dir + "body", pose, buf, light, ov, r, g, b);
        BakedRender.entity(dir + (team == BotTeam.GREEN ? "mark_green" : "mark_red"), pose, buf, light, ov, 1, 1, 1);
        float px = (float) kind.gunX / 16f, pz = (float) kind.gunZ / 16f;
        pose.translate(px, 0, pz);
        pose.mulPose(Axis.YP.rotationDegrees(-gunYaw));
        pose.translate(-px, 0, -pz);
        BakedRender.entity(dir + "gun", pose, buf, light, ov, r, g, b);
        pose.popPose();
    }

    @Override
    public void render(ShipEntity s, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, s.yRotO, s.getYRot())));
        float t = s.tickCount + pt;
        float roll = s.submerged() ? 0f : Mth.sin(t * 0.05f + s.getId()) * (s.kind.length > 100 ? 0.8f : 2.2f);
        renderShip(pose, buf, light, s.kind, s.getBotTeam(), Mth.rotLerp(pt, s.prevGunYaw, s.getGunYaw()), roll);
        pose.popPose();
        super.render(s, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShipEntity s) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    /** the torpedo */
    public static class Torpedo extends EntityRenderer<com.gunsmod.entity.TorpedoEntity> {
        public Torpedo(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(com.gunsmod.entity.TorpedoEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, e.yRotO, e.getYRot())));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, e.prevSpin, e.spin)));
            pose.scale(3f, 3f, 3f);
            pose.translate(-0.5f, -0.5f, -0.5f);
            BakedRender.entity("entity/naval/torpedo", pose, buf, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f);
            pose.popPose();
            super.render(e, yaw, pt, pose, buf, light);
        }

        @Override
        public ResourceLocation getTextureLocation(com.gunsmod.entity.TorpedoEntity e) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }
}
