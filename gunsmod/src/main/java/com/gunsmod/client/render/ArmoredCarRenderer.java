package com.gunsmod.client.render;

import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.CarType;
import com.gunsmod.entity.VehicleGeo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** BTR-82A and Pantsir-S1: hull following the ground, eight wheels (front axles steering), turret, guns and the search radar. */
public class ArmoredCarRenderer extends EntityRenderer<ArmoredCarEntity> {
    public static final float S = 4.0f;
    private static final float WHEEL_MODEL_R = 2.4f;

    public ArmoredCarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.0f;
    }

    @Override
    public void render(ArmoredCarEntity car, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, car.yRotO, car.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, car.prevTiltPitch, car.tiltPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, car.prevTiltRoll, car.tiltRoll)));
        if (car.hurtTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((car.hurtTime - pt) * 2f) * 1.0f));
        renderCar(pose, buf, light, car.kind, car.getBotTeam(), Mth.lerp(pt, car.prevWheelSpin, car.wheelSpin), car.getSteerVisual(),
                turretYaw(car, pt), gunPitch(car, pt), Mth.lerp(pt, car.prevRadarAngle, car.radarAngle));
        pose.popPose();
        super.render(car, yaw, pt, pose, buf, light);
    }

    private static float turretYaw(ArmoredCarEntity car, float pt) {
        return Mth.rotLerp(pt, car.prevTurret, car.getTurretYaw());
    }

    private static float gunPitch(ArmoredCarEntity car, float pt) {
        return Mth.lerp(pt, car.prevGun, car.getGunPitch());
    }

    public static void renderCar(PoseStack pose, MultiBufferSource buf, int light, CarType kind, BotTeam team, float spin, float steer,
                                 float turretYaw, float gunPitch, float radar) {
        VehicleGeo.Car g = kind.geo;
        String dir = "entity/" + g.model() + "/";
        int ov = OverlayTexture.NO_OVERLAY;
        float r = team.r, gr = team.g, b = team.b;
        pose.pushPose();
        pose.scale(S, S, S);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity(dir + "body", pose, buf, light, ov, r, gr, b);
        BakedRender.entity(dir + (team == BotTeam.GREEN ? "mark_green" : "mark_red"), pose, buf, light, ov, 1, 1, 1);
        float scale = (float) g.wheelR() / WHEEL_MODEL_R;
        double[][] wheels = g.wheels();
        for (int i = 0; i < wheels.length; i++) {
            double[] w = wheels[i];
            boolean steers = i / 2 < g.steerAxles();
            pose.pushPose();
            pose.translate((float) w[0] / 16f, (float) w[1] / 16f, (float) w[2] / 16f);
            // the second steering axle turns a little less
            if (steers) pose.mulPose(Axis.YP.rotationDegrees(-steer * (i / 2 == 0 ? 26f : 15f)));
            if (w[0] > 8) pose.mulPose(Axis.YP.rotationDegrees(180f));
            pose.mulPose(Axis.XP.rotationDegrees((w[0] > 8 ? spin : -spin) * Mth.RAD_TO_DEG));
            pose.scale(scale, scale, scale);
            pose.translate(-0.5f, -0.5f, -0.5f);
            BakedRender.entityCutout("entity/car/wheel", pose, buf, light, ov, 1, 1, 1);
            pose.popPose();
        }
        float px = (float) (g.turret()[0] / 16), pz = (float) (g.turret()[1] / 16);
        pose.pushPose();
        pose.translate(px, 0, pz);
        pose.mulPose(Axis.YP.rotationDegrees(-turretYaw));
        pose.translate(-px, 0, -pz);
        BakedRender.entity(dir + "turret", pose, buf, light, ov, r, gr, b);
        if (g.radar() != null) {
            float rx = (float) g.radar()[0] / 16f, ry = (float) g.radar()[1] / 16f, rz = (float) g.radar()[2] / 16f;
            pose.pushPose();
            pose.translate(rx, ry, rz);
            pose.mulPose(Axis.YP.rotationDegrees(-radar));
            pose.translate(-0.5f, -0.5f, -0.5f);
            BakedRender.entity(dir + "radar", pose, buf, light, ov, r, gr, b);
            pose.popPose();
        }
        float gy = (float) (g.gun()[0] / 16), gz = (float) (g.gun()[1] / 16);
        pose.translate(px, gy, gz);
        pose.mulPose(Axis.XP.rotationDegrees(-gunPitch));
        pose.translate(-px, -gy, -gz);
        BakedRender.entity(dir + "gun", pose, buf, light, ov, r, gr, b);
        pose.popPose();
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ArmoredCarEntity car) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(ArmoredCarEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.shouldRender(x, y, z);
    }
}
