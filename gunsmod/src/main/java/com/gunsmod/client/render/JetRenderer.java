package com.gunsmod.client.render;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.VehicleGeo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** F-16: pitch and bank about the cockpit area, folding gear, the stores still on the pylons, afterburner plume, burnt-out wreck. */
public class JetRenderer extends EntityRenderer<JetEntity> {
    public static final float S = 6.0f;

    public JetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 3.0f;
    }

    @Override
    public void render(JetEntity jet, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float py = (float) jet.geo.pivotY(), pz = (float) ((JetEntity.PIVOT_Z - 8) * JetEntity.JET_UNIT);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, jet.yRotO, jet.getYRot())));
        pose.translate(0, py, pz);
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, jet.prevPitch, jet.visPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, jet.prevRoll, jet.roll)));
        pose.translate(0, -py, -pz);
        if (jet.hurtTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((jet.hurtTime - pt) * 2f) * 0.8f));
        boolean burner = jet.isAfterburner() && !jet.isCrashing();
        renderJet(pose, buf, light, jet.getBotTeam(), Mth.lerp(pt, jet.prevGear, jet.gear), jet.getStores(), burner, jet.isCrashing(),
                burner ? 0.9f + jet.getRandom().nextFloat() * 0.25f : 1f);
        pose.popPose();
        super.render(jet, yaw, pt, pose, buf, light);
    }

    /** gear 0 = down, 1 = folded away; stores = bit mask of the missiles still aboard */
    public static void renderJet(PoseStack pose, MultiBufferSource buf, int light, BotTeam team, float gear, int stores, boolean burner,
                                 boolean wreck, float flicker) {
        VehicleGeo.Jet g = VehicleGeo.F16;
        String dir = "entity/" + g.model() + "/";
        int ov = OverlayTexture.NO_OVERLAY;
        // air superiority grey with a hint of the team colour; a wreck burns black
        float r = wreck ? 0.12f : Mth.lerp(0.35f, 0.74f, team.r), gr = wreck ? 0.11f : Mth.lerp(0.35f, 0.76f, team.g),
                b = wreck ? 0.10f : Mth.lerp(0.35f, 0.78f, team.b);
        pose.pushPose();
        pose.scale(S, S, S);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity(dir + "body", pose, buf, light, ov, r, gr, b);
        if (!wreck) BakedRender.entity(dir + (team == BotTeam.GREEN ? "mark_green" : "mark_red"), pose, buf, light, ov, 1, 1, 1);
        if (gear < 0.98f) {
            float a = gear * 92f;
            gearPart(pose, buf, light, dir + "gear_nose", g.gearNose(), -a, r, gr, b);
            gearPart(pose, buf, light, dir + "gear_main", g.gearMain(), -a, r, gr, b);
        }
        for (int i = 0; i < 4; i++) {
            if ((stores & (1 << i)) != 0) BakedRender.entityCutout(dir + "mav_" + i, pose, buf, light, ov, 1, 1, 1);
        }
        if ((stores & 16) != 0) BakedRender.entityCutout(dir + "aim9_l", pose, buf, light, ov, 1, 1, 1);
        if ((stores & 32) != 0) BakedRender.entityCutout(dir + "aim9_r", pose, buf, light, ov, 1, 1, 1);
        if (burner) {
            float nz = (float) g.nozzle()[2] / 16f, ny = (float) g.nozzle()[1] / 16f;
            pose.pushPose();
            pose.translate(0.5f, ny, nz);
            pose.scale(1f, 1f, flicker);
            pose.translate(-0.5f, -ny, -nz);
            BakedRender.entity(dir + "burner", pose, buf, LightTexture.FULL_BRIGHT, ov, 1, 1, 1);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void gearPart(PoseStack pose, MultiBufferSource buf, int light, String model, double[] pivot, float angle, float r, float g, float b) {
        float px = (float) pivot[0] / 16f, py = (float) pivot[1] / 16f, pz = (float) pivot[2] / 16f;
        pose.pushPose();
        pose.translate(px, py, pz);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        pose.translate(-px, -py, -pz);
        BakedRender.entity(model, pose, buf, light, OverlayTexture.NO_OVERLAY, r, g, b);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(JetEntity jet) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(JetEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.shouldRender(x, y, z);
    }
}
