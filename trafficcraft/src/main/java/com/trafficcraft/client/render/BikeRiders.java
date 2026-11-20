package com.trafficcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Riders on motorcycles (AI riders and players): straddling pose, torso leaning per bike type, hands on the grips
 * (following the steering), feet on the pegs. Vanilla's chair-like sitting pose is replaced for bikes.
 */
public final class BikeRiders {
    private static PlayerModel<LivingEntity> wide, slim;

    static PlayerModel<LivingEntity> model(boolean s) {
        if (wide == null) {
            var models = Minecraft.getInstance().getEntityModels();
            wide = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
            slim = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        return s ? slim : wide;
    }

    public static void reset() {
        wide = null;
        slim = null;
    }

    /** Player on a bike: called with the player's render pose stack (origin = player's interpolated position). */
    public static void renderPlayer(AbstractClientPlayer p, VehicleEntity v, PoseStack ps, MultiBufferSource buf, int light, float pt) {
        var skin = p.getSkin();
        double dx = Mth.lerp(pt, v.xo, v.getX()) - Mth.lerp(pt, p.xo, p.getX());
        double dy = Mth.lerp(pt, v.yo, v.getY()) - Mth.lerp(pt, p.yo, p.getY());
        double dz = Mth.lerp(pt, v.zo, v.getZ()) - Mth.lerp(pt, p.zo, p.getZ());
        ps.pushPose();
        ps.translate(dx, dy, dz);
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, v.yRotO, v.getYRot())));
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, v.leanO, v.lean)));
        render(model(skin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM), skin.texture(), v, v.seatOf(p), ps, buf, light);
        ps.popPose();
    }

    /** Draws a rider in the bike's model space (ps already rotated to the bike's yaw and lean). */
    public static void render(PlayerModel<LivingEntity> m, ResourceLocation tex, VehicleEntity v, int seat, PoseStack ps, MultiBufferSource buf, int light) {
        VehicleSpec s = v.spec();
        if (s.seats.isEmpty()) return;
        boolean pillion = seat > 0;
        var sp = s.seats.get(Math.min(seat, s.seats.size() - 1));
        float sc = s.riderScale;
        float a = pillion ? s.riderLean * 0.55f + 0.08f : s.riderLean;
        float leg = pillion ? Mth.clamp(s.riderLeg * 0.5f + 0.1f, -0.4f, 0.35f) : s.riderLeg;
        float k = Mth.clamp(v.steerVis / 30f, -1, 1);
        // hips sit on the saddle (seat points are 0.2 below the saddle top, see the generator)
        Vector3f hip = new Vector3f((float) sp.x, (float) sp.y + 0.2f, (float) sp.z);
        Vector3f root = new Vector3f(hip.x, hip.y - 0.75f * sc, hip.z);
        for (ModelPart part : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) part.resetPose();
        float ca = Mth.cos(a), sa = Mth.sin(a);
        // torso pivots round the hips (model space: y down, forward = -z, units = pixels)
        m.body.xRot = a;
        m.body.y = 12 - 12 * ca;
        m.body.z = -12 * sa;
        m.head.y = m.body.y;
        m.head.z = m.body.z;
        m.head.xRot = -a * 0.85f + 0.05f;
        m.head.yRot = -k * 0.35f;
        float shY = 12 - 10 * ca, shZ = -10 * sa;
        m.rightArm.y = shY;
        m.rightArm.z = shZ;
        m.leftArm.y = shY;
        m.leftArm.z = shZ;
        if (!pillion) {
            // hands on the grips; the grips turn with the handlebar about the steering axis
            for (int side = -1; side <= 1; side += 2) {
                Vector3f g = grip(s, side, v.steerVis);
                Vector3f gp = toModel(g, root, sc);
                ModelPart arm = side > 0 ? m.leftArm : m.rightArm;
                Vector3f d = new Vector3f(gp.x - arm.x, gp.y - arm.y, gp.z - arm.z).normalize();
                arm.xRot = (float) Math.asin(Mth.clamp(d.z, -1, 1));
                arm.zRot = (float) Math.atan2(-d.x, d.y);
            }
        } else {
            m.rightArm.xRot = -0.75f;
            m.leftArm.xRot = -0.75f;
            m.rightArm.zRot = 0.25f;
            m.leftArm.zRot = -0.25f;
        }
        m.rightLeg.xRot = leg;
        m.leftLeg.xRot = leg;
        m.rightLeg.zRot = s.riderSpread;
        m.leftLeg.zRot = -s.riderSpread;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
        m.young = false;
        m.riding = false;
        m.crouching = false;
        ps.pushPose();
        ps.translate(root.x, root.y, root.z);
        ps.scale(sc, sc, sc);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(-1, -1, 1);
        ps.translate(0, -1.501f, 0);
        m.renderToBuffer(ps, buf.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    /** Grip position (bike model space) for side +1 (left) / -1 (right), rotated with the steering. */
    static Vector3f grip(VehicleSpec s, int side, float steerDeg) {
        Vector3f g = new Vector3f(s.grips[0] * side, s.grips[1], s.grips[2]);
        Vector3f piv = new Vector3f(s.steerPivot[0], s.steerPivot[1], s.steerPivot[2]);
        Vector3f ax = new Vector3f(s.steerAxis[0], s.steerAxis[1], s.steerAxis[2]).normalize();
        g.sub(piv);
        new Quaternionf().rotateAxis((float) Math.toRadians(steerDeg), ax).transform(g);
        return g.add(piv);
    }

    /** Bike model space (blocks) -> humanoid model space (pixels, y down, forward = -z). */
    static Vector3f toModel(Vector3f p, Vector3f root, float sc) {
        return new Vector3f((p.x - root.x) / sc * 16, (1.501f - (p.y - root.y) / sc) * 16, -(p.z - root.z) / sc * 16);
    }
}
