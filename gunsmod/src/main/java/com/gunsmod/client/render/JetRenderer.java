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

/**
 * Every fixed-wing aircraft: pitch and bank about the cockpit area, folding gear, the stores still on the pylons (not the
 * ones carried inside), afterburner plumes, the B-2's open bomb bay, burnt-out wreck.
 */
public class JetRenderer extends EntityRenderer<JetEntity> {
    /** the baked parts of an aircraft type */
    public static java.util.List<String> parts(com.gunsmod.entity.JetType t) {
        java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of("body", "mark_green", "mark_red", "gear_nose", "gear_main", "cockpit", "stick", "throttle"));
        if (t.afterburner) l.add("burner");
        if (t.ground == com.gunsmod.entity.JetType.Ground.NUKE) l.add("bay");
        String[] stores = {"mav_0", "mav_1", "mav_2", "mav_3", "aim9_l", "aim9_r"};
        for (int i = 0; i < 6; i++) if ((t.drawMask & (1 << i)) != 0) l.add(stores[i]);
        return l;
    }

    public JetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 3.0f;
    }

    @Override
    public void render(JetEntity jet, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        // first person in the pilot's seat: the whole aircraft is drawn round the cockpit
        boolean inside = com.gunsmod.client.VehicleCamera.hidden(jet);
        float py = (float) jet.geo.pivotY(), pz = (float) ((jet.pivotZ() - 8) * jet.type.unit);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, jet.yRotO, jet.getYRot())));
        pose.translate(0, py, pz);
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, jet.prevPitch, jet.visPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, jet.prevRoll, jet.roll)));
        pose.translate(0, -py, -pz);
        if (jet.hurtTime > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((jet.hurtTime - pt) * 2f) * 0.8f));
        boolean burner = jet.isAfterburner() && !jet.isCrashing();
        renderJet(pose, buf, light, jet.type, jet.getBotTeam(), Mth.lerp(pt, jet.prevGear, jet.gear), jet.getStores(), burner, jet.isCrashing(),
                jet.bayOpen(), burner ? 0.9f + jet.getRandom().nextFloat() * 0.25f : 1f);
        if (inside) renderCockpit(pose, buf, light, jet);
        pose.popPose();
        super.render(jet, yaw, pt, pose, buf, light);
    }

    /** gear 0 = down, 1 = folded away; stores = bit mask of the missiles still aboard */
    public static void renderJet(PoseStack pose, MultiBufferSource buf, int light, com.gunsmod.entity.JetType type, BotTeam team, float gear,
                                 int stores, boolean burner, boolean wreck, boolean bay, float flicker) {
        VehicleGeo.Jet g = type.geo;
        String dir = "entity/" + g.model() + "/";
        int ov = OverlayTexture.NO_OVERLAY;
        // the type's own paint with a hint of the team colour; a wreck burns black
        float[] c = type.paint();
        float tint = type.rpgOnly() ? 0.06f : 0.35f;
        float r = wreck ? 0.12f : Mth.lerp(tint, c[0], team.r), gr = wreck ? 0.11f : Mth.lerp(tint, c[1], team.g),
                b = wreck ? 0.10f : Mth.lerp(tint, c[2], team.b);
        stores &= type.drawMask;
        float S = type.renderScale();
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
        if (bay) BakedRender.entity(dir + "bay", pose, buf, light, ov, r, gr, b);
        if (burner && type.afterburner) {
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

    /** the cockpit around the pilot: panel, displays, HUD glass, seat; the stick and the throttle move with his hands */
    private static void renderCockpit(PoseStack pose, MultiBufferSource buf, int light, JetEntity jet) {
        com.gunsmod.entity.JetType type = jet.type;
        VehicleGeo.Jet g = type.geo;
        String dir = "entity/" + g.model() + "/";
        int ov = OverlayTexture.NO_OVERLAY;
        float S = type.renderScale();
        pose.pushPose();
        pose.scale(S, S, S);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity(dir + "cockpit", pose, buf, light, ov, 1, 1, 1);
        double[] sp = g.stick();
        pose.pushPose();
        pose.translate(sp[0] / 16f, sp[1] / 16f, sp[2] / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(jet.stickY * 18f));
        pose.mulPose(Axis.ZP.rotationDegrees(-jet.stickX * 18f));
        pose.translate(-sp[0] / 16f, -sp[1] / 16f, -sp[2] / 16f);
        BakedRender.entity(dir + "stick", pose, buf, light, ov, 1, 1, 1);
        pose.popPose();
        double[] tp = g.throttle();
        pose.pushPose();
        pose.translate(tp[0] / 16f, tp[1] / 16f, tp[2] / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(15f - jet.getThrottle() * 40f));
        pose.translate(-tp[0] / 16f, -tp[1] / 16f, -tp[2] / 16f);
        BakedRender.entity(dir + "throttle", pose, buf, light, ov, 1, 1, 1);
        pose.popPose();
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
