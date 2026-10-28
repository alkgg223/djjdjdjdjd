package com.gunsmod.client.render;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JeepEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** Draws the jeep from its part models: painted body, team markings, four wheels (spinning, front ones steering) and the turret. */
public class JeepRenderer extends EntityRenderer<JeepEntity> {
    /** model units (0.1 block) -> baked model space (1/16 block) */
    public static final float S = 1.6f;

    public JeepRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.6f;
    }

    @Override
    public void render(JeepEntity jeep, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        float bodyYaw = Mth.rotLerp(pt, jeep.yRotO, jeep.getYRot());
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        // small damage wobble
        if (jeep.hurtTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((jeep.hurtTime - pt) * 2f) * 1.5f));
        renderJeep(pose, buf, light, jeep.getBotTeam(), Mth.lerp(pt, jeep.prevWheelSpin, jeep.wheelSpin), jeep.getSteerVisual(),
                turretYaw(jeep, pt), turretPitch(jeep, pt));
        pose.popPose();
        super.render(jeep, yaw, pt, pose, buf, light);
    }

    private static float turretYaw(JeepEntity jeep, float pt) {
        var p = Minecraft.getInstance().player;
        if (p != null && jeep.isGunner(p)) return Mth.wrapDegrees(p.getViewYRot(pt) - Mth.rotLerp(pt, jeep.yRotO, jeep.getYRot()));
        return Mth.rotLerp(pt, jeep.prevTurretYaw, jeep.getTurretYaw());
    }

    private static float turretPitch(JeepEntity jeep, float pt) {
        var p = Minecraft.getInstance().player;
        if (p != null && jeep.isGunner(p)) return Mth.clamp(p.getViewXRot(pt), -35f, 20f);
        return Mth.lerp(pt, jeep.prevTurretPitch, jeep.getTurretPitch());
    }

    /** pose at the entity origin, facing the model front (-Z) to the entity's heading */
    public static void renderJeep(PoseStack pose, MultiBufferSource buf, int light, BotTeam team, float spin, float steer, float tYaw, float tPitch) {
        int ov = OverlayTexture.NO_OVERLAY;
        float r = team.r, g = team.g, b = team.b;
        pose.pushPose();
        pose.scale(S, S, S);
        pose.translate(-0.5f, 0f, -0.5f);       // model (8, 0, 8) at the origin
        BakedRender.entity("entity/jeep/body", pose, buf, light, ov, r, g, b);
        BakedRender.entity(team == BotTeam.GREEN ? "entity/jeep/mark_green" : "entity/jeep/mark_red", pose, buf, light, ov, 1, 1, 1);
        // wheels
        double[][] wheels = {{-1.0, 4.3, -8.0}, {17.0, 4.3, -8.0}, {-1.0, 4.3, 21.0}, {17.0, 4.3, 21.0}};
        for (int i = 0; i < 4; i++) {
            double[] w = wheels[i];
            pose.pushPose();
            pose.translate(w[0] / 16f, w[1] / 16f, w[2] / 16f);
            if (i < 2) pose.mulPose(Axis.YP.rotationDegrees(-steer * 28f));
            pose.mulPose(Axis.XP.rotationDegrees(-spin * Mth.RAD_TO_DEG));
            pose.translate(-0.5f, -0.5f, -0.5f);
            BakedRender.entityCutout("entity/jeep/wheel", pose, buf, light, ov, 1, 1, 1);
            pose.popPose();
        }
        // turret (yaw) and gun (pitch)
        float px = (float) (JeepEntity.TURRET_X / 16), pz = (float) (JeepEntity.TURRET_Z / 16);
        pose.pushPose();
        pose.translate(px, 0, pz);
        pose.mulPose(Axis.YP.rotationDegrees(-tYaw));
        pose.translate(-px, 0, -pz);
        BakedRender.entity("entity/jeep/turret", pose, buf, light, ov, r, g, b);
        float gy = (float) (JeepEntity.GUN_PIVOT_Y / 16);
        pose.translate(px, gy, pz);
        pose.mulPose(Axis.XP.rotationDegrees(-tPitch));
        pose.translate(-px, -gy, -pz);
        BakedRender.entityCutout("entity/jeep/gun", pose, buf, light, ov, 1, 1, 1);
        pose.popPose();
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(JeepEntity jeep) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(JeepEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.shouldRender(x, y, z);
    }
}
