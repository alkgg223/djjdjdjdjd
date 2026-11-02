package com.gunsmod.client.render;

import com.gunsmod.entity.BomberEntity;
import com.gunsmod.entity.BotTeam;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** The stealth bomber: banks into its turns, opens the bomb bay as it drops, spirals down blackened when shot down. */
public class BomberRenderer extends EntityRenderer<BomberEntity> {
    /** one model unit is one block */
    public static final float S = 16f;

    public BomberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(BomberEntity b, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float mid = (float) BomberEntity.MID_Y;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, b.yRotO, b.getYRot())));
        pose.translate(0, mid, 0);
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, b.prevPitchVis, b.pitchVis)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, b.prevRoll, b.roll)));
        pose.translate(0, -mid, 0);
        renderB2(pose, buf, light, b.getBotTeam(), b.bayOpen(), b.isCrashing() || b.getHits() >= 4);
        pose.popPose();
        super.render(b, yaw, pt, pose, buf, light);
    }

    /** the bomber at the pose origin (entity origin under the middle of the wing) */
    public static void renderB2(PoseStack pose, MultiBufferSource buf, int light, BotTeam team, boolean bay, boolean burnt) {
        int ov = OverlayTexture.NO_OVERLAY;
        // dark charcoal grey with the faintest hint of the team colour; scorched black when it is going down
        float r = burnt ? 0.13f : 0.28f + (team.r - 0.5f) * 0.05f, g = burnt ? 0.12f : 0.29f + (team.g - 0.5f) * 0.05f,
                bl = burnt ? 0.11f : 0.31f + (team.b - 0.5f) * 0.05f;
        pose.pushPose();
        pose.scale(S, S, S);
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity("entity/b2/body", pose, buf, light, ov, r, g, bl);
        if (!burnt) BakedRender.entity(team == BotTeam.GREEN ? "entity/b2/mark_green" : "entity/b2/mark_red", pose, buf, light, ov, 1, 1, 1);
        if (bay) BakedRender.entity("entity/b2/bay", pose, buf, light, ov, r, g, bl);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(BomberEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
