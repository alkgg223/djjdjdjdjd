package com.gunsmod.client.render;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.HelicopterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/** Helicopter: body tilting with its flight, chin turret, spinning main and tail rotor plus the rotor blur disc. */
public class HelicopterRenderer extends EntityRenderer<HelicopterEntity> {
    public static final float S = 4.0f;
    private static final ResourceLocation BLUR = Reg.id("textures/entity/rotor_blur.png");
    /** attitude pivot: about the rotor mast, 1.2 blocks up */
    private static final float PIVOT_Y = 1.2f, PIVOT_Z = (float) ((8 - HelicopterEntity.HUB_Z) * 0.25);

    public HelicopterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.2f;
    }

    @Override
    public void render(HelicopterEntity heli, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, heli.yRotO, heli.getYRot())));
        pose.translate(0, PIVOT_Y, -PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, heli.prevVisPitch, heli.visPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, heli.prevVisRoll, heli.visRoll)));
        pose.translate(0, -PIVOT_Y, PIVOT_Z);
        if (heli.hurtTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((heli.hurtTime - pt) * 2f) * 1.2f));
        renderHeli(pose, buf, light, heli.getBotTeam(), Mth.lerp(pt, heli.prevRotorAngle, heli.rotorAngle), Mth.lerp(pt, heli.prevTailAngle, heli.tailAngle),
                Mth.lerp(pt, heli.prevRotor, heli.rotor), Mth.lerp(pt, heli.prevChinYaw, heli.getChinYaw()), Mth.lerp(pt, heli.prevChinPitch, heli.getChinPitch()));
        pose.popPose();
        super.render(heli, yaw, pt, pose, buf, light);
    }

    public static void renderHeli(PoseStack pose, MultiBufferSource buf, int light, BotTeam team, float rotorAngle, float tailAngle, float rotorSpeed,
                                  float chinYaw, float chinPitch) {
        int ov = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        pose.scale(S, S, S);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity("entity/heli/body", pose, buf, light, ov, team.r, team.g, team.b);
        BakedRender.entity(team == BotTeam.GREEN ? "entity/heli/mark_green" : "entity/heli/mark_red", pose, buf, light, ov, 1, 1, 1);
        // chin turret
        float cx = (float) HelicopterEntity.CHIN_X / 16f, cy = (float) HelicopterEntity.CHIN_Y / 16f, cz = (float) HelicopterEntity.CHIN_Z / 16f;
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(-chinYaw));
        pose.mulPose(Axis.XP.rotationDegrees(-chinPitch));
        pose.translate(-cx, -cy, -cz);
        BakedRender.entityCutout("entity/heli/chin", pose, buf, light, ov, 1, 1, 1);
        pose.popPose();
        // main rotor
        float hx = (float) HelicopterEntity.HUB_X / 16f, hy = (float) HelicopterEntity.HUB_Y / 16f, hz = (float) HelicopterEntity.HUB_Z / 16f;
        pose.pushPose();
        pose.translate(hx, hy, hz);
        pose.mulPose(Axis.YP.rotationDegrees(-rotorAngle));
        pose.translate(-hx, -hy, -hz);
        BakedRender.entityCutout("entity/heli/rotor", pose, buf, light, ov, 1, 1, 1);
        pose.popPose();
        // tail rotor
        float tx = (float) HelicopterEntity.TAIL_X / 16f, ty = (float) HelicopterEntity.TAIL_Y / 16f, tz = (float) HelicopterEntity.TAIL_Z / 16f;
        pose.pushPose();
        pose.translate(tx, ty, tz);
        pose.mulPose(Axis.XP.rotationDegrees(tailAngle));
        pose.translate(-tx, -ty, -tz);
        BakedRender.entityCutout("entity/heli/tail_rotor", pose, buf, light, ov, 1, 1, 1);
        pose.popPose();
        // blur disc of the spinning blades
        if (rotorSpeed > 0.3f) {
            float a = Mth.clamp((rotorSpeed - 0.3f) / 0.7f, 0f, 1f) * 0.5f;
            float rad = (float) HelicopterEntity.ROTOR_R / 16f;
            pose.pushPose();
            pose.translate(hx, hy + 0.2f / 16f, hz);
            pose.mulPose(Axis.YP.rotationDegrees(-rotorAngle * 0.1f));
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(BLUR));
            PoseStack.Pose last = pose.last();
            Matrix4f m = last.pose();
            float[][] up = {{-rad, -rad, 0, 0}, {-rad, rad, 0, 1}, {rad, rad, 1, 1}, {rad, -rad, 1, 0}};
            for (float[] v : up) vc.addVertex(m, v[0], 0, v[1]).setColor(1f, 1f, 1f, a).setUv(v[2], v[3]).setOverlay(ov).setLight(light).setNormal(last, 0, 1, 0);
            for (int i = 3; i >= 0; i--) {
                float[] v = up[i];
                vc.addVertex(m, v[0], 0, v[1]).setColor(1f, 1f, 1f, a).setUv(v[2], v[3]).setOverlay(ov).setLight(light).setNormal(last, 0, -1, 0);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(HelicopterEntity heli) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(HelicopterEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.shouldRender(x, y, z);
    }
}
