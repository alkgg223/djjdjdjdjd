package com.gunsmod.client.render;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.HeliType;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.VehicleGeo;
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

/**
 * Helicopters: body tilting with the flight, gun turret, spinning rotors (one blade model copied round the hub,
 * counter-rotating coaxial rotors on the Ka-52) and the rotor blur discs.
 */
public class HelicopterRenderer extends EntityRenderer<HelicopterEntity> {
    public static final float S = 4.0f;
    private static final ResourceLocation BLUR = Reg.id("textures/entity/rotor_blur.png");

    public HelicopterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.2f;
    }

    @Override
    public void render(HelicopterEntity heli, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        if (com.gunsmod.client.VehicleCamera.hidden(heli)) return;      // first person: looking out through the sight
        VehicleGeo.Heli geo = heli.geo;
        float pivotY = (float) geo.pivotY(), pivotZ = (float) ((8 - geo.hub()[2]) * 0.25);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, heli.yRotO, heli.getYRot())));
        pose.translate(0, pivotY, -pivotZ);
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, heli.prevVisPitch, heli.visPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, heli.prevVisRoll, heli.visRoll)));
        pose.translate(0, -pivotY, pivotZ);
        if (heli.hurtTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((heli.hurtTime - pt) * 2f) * 1.2f));
        renderHeli(pose, buf, light, heli.kind, heli.getBotTeam(), Mth.lerp(pt, heli.prevRotorAngle, heli.rotorAngle),
                Mth.lerp(pt, heli.prevTailAngle, heli.tailAngle), Mth.lerp(pt, heli.prevRotor, heli.rotor),
                Mth.lerp(pt, heli.prevChinYaw, heli.getChinYaw()), Mth.lerp(pt, heli.prevChinPitch, heli.getChinPitch()));
        pose.popPose();
        super.render(heli, yaw, pt, pose, buf, light);
    }

    public static void renderHeli(PoseStack pose, MultiBufferSource buf, int light, HeliType kind, BotTeam team, float rotorAngle, float tailAngle,
                                  float rotorSpeed, float chinYaw, float chinPitch) {
        VehicleGeo.Heli geo = kind.geo;
        String dir = "entity/" + geo.model() + "/";
        boolean legacy = kind == HeliType.COBRA;
        int ov = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        pose.scale(S, S, S);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity(dir + "body", pose, buf, light, ov, team.r, team.g, team.b);
        BakedRender.entity(dir + (team == BotTeam.GREEN ? "mark_green" : "mark_red"), pose, buf, light, ov, 1, 1, 1);
        // gun turret under the nose (the Ka-52's cannon is part of the body)
        if (geo.chin() != null) {
            float cx = (float) geo.chin()[0] / 16f, cy = (float) geo.chin()[1] / 16f, cz = (float) geo.chin()[2] / 16f;
            pose.pushPose();
            pose.translate(cx, cy, cz);
            pose.mulPose(Axis.YP.rotationDegrees(-chinYaw));
            pose.mulPose(Axis.XP.rotationDegrees(-chinPitch));
            pose.translate(-cx, -cy, -cz);
            BakedRender.entityCutout(dir + "chin", pose, buf, light, ov, 1, 1, 1);
            pose.popPose();
        }
        double[] hub = geo.hub();
        float hx = (float) hub[0] / 16f, hy = (float) hub[1] / 16f, hz = (float) hub[2] / 16f;
        if (legacy) {
            pose.pushPose();
            pose.translate(hx, hy, hz);
            pose.mulPose(Axis.YP.rotationDegrees(-rotorAngle));
            pose.translate(-hx, -hy, -hz);
            BakedRender.entityCutout(dir + "rotor", pose, buf, light, ov, 1, 1, 1);
            pose.popPose();
        } else {
            rotor(pose, buf, light, dir, hx, hy, hz, rotorAngle, geo.blades(), 0f);
            // Ka-52: the lower rotor turns the other way
            if (geo.hub2() > 0) rotor(pose, buf, light, dir, hx, (float) geo.hub2() / 16f, hz, -rotorAngle, geo.blades(), 60f);
        }
        // tail rotor
        if (geo.tail() != null) {
            float tx = (float) geo.tail()[0] / 16f, ty = (float) geo.tail()[1] / 16f, tz = (float) geo.tail()[2] / 16f;
            pose.pushPose();
            pose.translate(tx, ty, tz);
            pose.mulPose(Axis.XP.rotationDegrees(tailAngle));
            if (legacy) {
                pose.translate(-tx, -ty, -tz);
                BakedRender.entityCutout(dir + "tail_rotor", pose, buf, light, ov, 1, 1, 1);
            } else {
                pose.translate(-0.5f, -0.5f, -0.5f);
                BakedRender.entityCutout(dir + "tail_hub", pose, buf, light, ov, 1, 1, 1);
                for (int i = 0; i < geo.tailBlades(); i++) {
                    pose.pushPose();
                    pose.translate(0.5f, 0.5f, 0.5f);
                    pose.mulPose(Axis.XP.rotationDegrees(i * 360f / geo.tailBlades()));
                    pose.translate(-0.5f, -0.5f, -0.5f);
                    BakedRender.entityCutout(dir + "tail_blade", pose, buf, light, ov, 1, 1, 1);
                    pose.popPose();
                }
            }
            pose.popPose();
        }
        // blur disc of the spinning blades
        if (rotorSpeed > 0.3f) {
            float a = Mth.clamp((rotorSpeed - 0.3f) / 0.7f, 0f, 1f) * (geo.hub2() > 0 ? 0.35f : 0.5f);
            float rad = (float) geo.rotorR() / 16f;
            disc(pose, buf, light, hx, hy + 0.2f / 16f, hz, rad, rotorAngle * 0.1f, a);
            if (geo.hub2() > 0) disc(pose, buf, light, hx, (float) geo.hub2() / 16f + 0.2f / 16f, hz, rad, -rotorAngle * 0.1f, a);
        }
        pose.popPose();
    }

    /** a hub with one blade model copied round it */
    private static void rotor(PoseStack pose, MultiBufferSource buf, int light, String dir, float hx, float hy, float hz, float angle, int blades, float offset) {
        int ov = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        pose.translate(hx, hy, hz);
        pose.mulPose(Axis.YP.rotationDegrees(-angle - offset));
        pose.translate(-0.5f, -0.5f, -0.5f);
        BakedRender.entityCutout(dir + "hub", pose, buf, light, ov, 1, 1, 1);
        for (int i = 0; i < blades; i++) {
            pose.pushPose();
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(i * 360f / blades));
            pose.translate(-0.5f, -0.5f, -0.5f);
            BakedRender.entityCutout(dir + "blade", pose, buf, light, ov, 1, 1, 1);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void disc(PoseStack pose, MultiBufferSource buf, int light, float x, float y, float z, float rad, float spin, float alpha) {
        int ov = OverlayTexture.NO_OVERLAY;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(-spin));
        // no depth write: a disc drawn before the fuselage must not hide it (seen from above the body vanished)
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(BLUR));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float[][] up = {{-rad, -rad, 0, 0}, {-rad, rad, 0, 1}, {rad, rad, 1, 1}, {rad, -rad, 1, 0}};
        for (float[] v : up) vc.addVertex(m, v[0], 0, v[1]).setColor(1f, 1f, 1f, alpha).setUv(v[2], v[3]).setOverlay(ov).setLight(light).setNormal(last, 0, 1, 0);
        for (int i = 3; i >= 0; i--) {
            float[] v = up[i];
            vc.addVertex(m, v[0], 0, v[1]).setColor(1f, 1f, 1f, alpha).setUv(v[2], v[3]).setOverlay(ov).setLight(light).setNormal(last, 0, -1, 0);
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
