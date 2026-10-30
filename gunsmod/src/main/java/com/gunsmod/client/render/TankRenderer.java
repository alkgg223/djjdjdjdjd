package com.gunsmod.client.render;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.TankEntity;
import com.gunsmod.entity.TankType;
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

/** Tank from its part models: hull (tilting with the ground), spinning road wheels, moving tracks, turret and gun. */
public class TankRenderer extends EntityRenderer<TankEntity> {
    /** model units (0.25 block) -> baked model space (1/16 block) */
    public static final float S = 4.0f;
    /** the shared running gear models were built for these radii */
    private static final float GEAR_WHEEL_R = 1.3f, LINK = 0.75f;

    public TankRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 2.6f;
    }

    @Override
    public void render(TankEntity tank, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        if (com.gunsmod.client.VehicleCamera.hidden(tank)) return;      // first person: looking out through the sight
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, tank.yRotO, tank.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, tank.prevTiltPitch, tank.tiltPitch) + Mth.lerp(pt, tank.prevRock, tank.rock)));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, tank.prevTiltRoll, tank.tiltRoll)));
        if (tank.hurtTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((tank.hurtTime - pt) * 2f) * 0.8f));
        int since = tank.kind.reload - tank.getReload();
        float recoil = tank.getReload() <= 0 ? 0f : since < 2 ? 1f : Math.max(0f, 1f - (since - 2 + pt) / 14f);
        renderTank(pose, buf, light, tank.kind, tank.getBotTeam(), Mth.lerp(pt, tank.prevTrackL, tank.trackL), Mth.lerp(pt, tank.prevTrackR, tank.trackR),
                Mth.rotLerp(pt, tank.prevTurret, tank.getTurretYaw()), Mth.lerp(pt, tank.prevGun, tank.getGunPitch()), recoil);
        pose.popPose();
        super.render(tank, yaw, pt, pose, buf, light);
    }

    /** pose at the entity origin, model front (-Z) along the hull's heading; tracks in blocks travelled */
    public static void renderTank(PoseStack pose, MultiBufferSource buf, int light, TankType kind, BotTeam team, float trackL, float trackR,
                                  float turretYaw, float gunPitch, float recoil) {
        VehicleGeo.Tank g = kind.geo;
        String dir = "entity/" + g.model() + "/";
        int ov = OverlayTexture.NO_OVERLAY;
        float r = team.r, gr = team.g, b = team.b;
        pose.pushPose();
        float sc = S * kind.scale;
        pose.scale(sc, sc, sc);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity(dir + "body", pose, buf, light, ov, r, gr, b);
        BakedRender.entity(dir + (team == BotTeam.GREEN ? "mark_green" : "mark_red"), pose, buf, light, ov, 1, 1, 1);
        float wheelY = (float) g.wheel()[0], wheelR = (float) g.wheel()[1];
        for (int i = 0; i < g.trackX().length; i++) {
            // two tracks a side on the Object 279
            float tx = (float) g.trackX()[i], travel = tx < 8f ? trackL : trackR;
            float units = travel / (0.25f * kind.scale);
            wheel(pose, buf, light, "entity/tank/idler", tx, (float) g.idler()[0], (float) g.idler()[1], units / (float) g.idler()[2], 1f, r, gr, b);
            wheel(pose, buf, light, "entity/tank/sprocket", tx, (float) g.sprocket()[0], (float) g.sprocket()[1], units / (float) g.sprocket()[2], 1f, r, gr, b);
            for (double z : g.wheelsZ()) wheel(pose, buf, light, "entity/tank/roadwheel", tx, wheelY, (float) z, units / wheelR, wheelR / GEAR_WHEEL_R, r, gr, b);
            float off = units % LINK;
            if (off < 0) off += LINK;
            pose.pushPose();
            pose.translate((tx - 8f) / 16f, (float) g.bottom()[0] / 16f, ((float) g.bottom()[1] + 8f + off) / 16f);
            BakedRender.entityCutout("entity/tank/track_bottom", pose, buf, light, ov, 1, 1, 1);
            pose.popPose();
            pose.pushPose();
            pose.translate((tx - 8f) / 16f, (float) g.top()[0] / 16f, ((float) g.top()[1] + 8f + LINK - off) / 16f);
            BakedRender.entityCutout("entity/tank/track_top", pose, buf, light, ov, 1, 1, 1);
            pose.popPose();
        }
        float px = (float) (g.turret()[0] / 16), pz = (float) (g.turret()[1] / 16);
        pose.pushPose();
        pose.translate(px, 0, pz);
        pose.mulPose(Axis.YP.rotationDegrees(-turretYaw));
        pose.translate(-px, 0, -pz);
        BakedRender.entity(dir + "turret", pose, buf, light, ov, r, gr, b);
        BakedRender.entity(dir + (team == BotTeam.GREEN ? "tmark_green" : "tmark_red"), pose, buf, light, ov, 1, 1, 1);
        float gy = (float) (g.gun()[0] / 16), gz = (float) (g.gun()[1] / 16);
        pose.translate(px, gy, gz);
        pose.mulPose(Axis.XP.rotationDegrees(-gunPitch));
        pose.translate(0, 0, recoil * 1.6f / 16f);
        pose.translate(-px, -gy, -gz);
        BakedRender.entity(dir + "gun", pose, buf, light, ov, r, gr, b);
        pose.popPose();
        pose.popPose();
    }

    private static void wheel(PoseStack pose, MultiBufferSource buf, int light, String model, float x, float y, float z, float spinRad,
                              float scale, float r, float g, float b) {
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, z / 16f);
        pose.mulPose(Axis.XP.rotation(-spinRad));
        if (scale != 1f) pose.scale(1f, scale, scale);
        pose.translate(-0.5f, -0.5f, -0.5f);
        BakedRender.entity(model, pose, buf, light, OverlayTexture.NO_OVERLAY, r, g, b);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(TankEntity tank) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(TankEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.shouldRender(x, y, z);
    }
}
