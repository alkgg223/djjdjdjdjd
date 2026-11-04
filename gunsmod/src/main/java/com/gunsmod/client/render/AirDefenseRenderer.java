package com.gunsmod.client.render;

import com.gunsmod.block.airdef.AirDefenseBlock;
import com.gunsmod.block.airdef.AirDefenseGeo;
import com.gunsmod.block.airdef.DefenseBlockEntity;
import com.gunsmod.block.airdef.RadarBlockEntity;
import com.gunsmod.entity.BotTeam;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Radars and launchers: the base facing the block's way, the antenna turning (a radar sweeps round, the Patriot's face
 * scans its sector), launchers and guns turning and elevating onto their target; above it the name, detection or
 * accuracy, range and what it is doing, in the side's colour.
 */
public class AirDefenseRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public AirDefenseRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /** the model: base, head turned by `turn` (relative to the base), arm raised by `pitch` */
    public static void renderModel(String id, PoseStack pose, MultiBufferSource buf, int light, float baseAngle, float turn, float pitch, BotTeam team) {
        AirDefenseGeo.Geo g = AirDefenseGeo.GEO.get(id);
        if (g == null) return;
        String dir = "entity/airdef/" + id + "/";
        int ov = OverlayTexture.NO_OVERLAY;
        float r = Mth.lerp(0.12f, 1f, team.r), gr = Mth.lerp(0.12f, 1f, team.g), b = Mth.lerp(0.12f, 1f, team.b);
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(baseAngle));
        pose.translate(-0.5f, 0f, -0.5f);
        BakedRender.entity(dir + "base", pose, buf, light, ov, r, gr, b);
        double[] h = g.head(), a = g.arm();
        pose.translate(h[0] / 16f, h[1] / 16f, h[2] / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.translate(-h[0] / 16f, -h[1] / 16f, -h[2] / 16f);
        BakedRender.entity(dir + "head", pose, buf, light, ov, r, gr, b);
        if (g.muzzles().length > 0) {
            pose.translate(a[0] / 16f, a[1] / 16f, a[2] / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(-a[0] / 16f, -a[1] / 16f, -a[2] / 16f);
            BakedRender.entity(dir + "arm", pose, buf, light, ov, r, gr, b);
        }
        pose.popPose();
    }

    @Override
    public void render(T be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (!(be.getBlockState().getBlock() instanceof AirDefenseBlock block)) return;
        float base = 180f - be.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot();
        float t = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + pt;
        Component l1, l2, l3;
        BotTeam team;
        if (be instanceof RadarBlockEntity r) {
            team = r.team();
            float turn = r.type.sector ? Mth.sin(t * 0.03f) * 55f : t * 360f / r.type.sweep;
            renderModel(r.type.id, pose, buf, light, base, turn, 0f, team);
            l2 = Component.translatable("gunsmod.ad.detect", Math.round(r.type.detect * 100)).append("  ")
                    .append(Component.translatable("gunsmod.ad.range", (int) r.type.range));
            l3 = Component.translatable("gunsmod.ad.tracking", r.shownTracks());
        } else if (be instanceof DefenseBlockEntity d) {
            team = d.team();
            float yaw = Float.isNaN(d.aimYaw) ? base : Mth.rotLerp(pt, d.prevAimYaw, d.aimYaw);
            float pitch = Float.isNaN(d.aimYaw) ? d.idlePitch() : Mth.lerp(pt, d.prevAimPitch, d.aimPitch);
            renderModel(d.type.id, pose, buf, light, base, yaw - base, pitch, team);
            l2 = Component.translatable("gunsmod.ad.accuracy", Math.round(d.type.accuracy * 100)).append("  ")
                    .append(Component.translatable("gunsmod.ad.range", (int) d.type.range));
            l3 = d.links().isEmpty() ? Component.translatable("gunsmod.ad.nolink")
                    : d.type.gun() ? Component.translatable("gunsmod.ad.links", d.links().size())
                    : Component.translatable("gunsmod.ad.links_ammo", d.links().size(), d.ammo(), d.type.magazine);
        } else {
            return;
        }
        l1 = Component.translatable(be.getBlockState().getBlock().getDescriptionId());
        // the label: only for someone close enough to read it
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 28 * 28) return;
        AirDefenseGeo.Geo g = AirDefenseGeo.GEO.get(block.id());
        float y = g == null ? 3f : (float) (g.label() / 16.0) + 0.4f;
        Font font = mc.font;
        pose.pushPose();
        pose.translate(0.5f, y, 0.5f);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.028f, -0.028f, 0.028f);
        int col = team == BotTeam.RED ? 0xFFFF6050 : 0xFF60FF70;
        font.drawInBatch(l1, -font.width(l1) / 2f, -12, col, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0x70000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(l2, -font.width(l2) / 2f, -1, 0xFFFFFFFF, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0x70000000, LightTexture.FULL_BRIGHT);
        int c3 = be instanceof DefenseBlockEntity d && d.links().isEmpty() ? 0xFFFF5040 : 0xFFFFE080;
        font.drawInBatch(l3, -font.width(l3) / 2f, 10, c3, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0x70000000, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(5, 8, 5);
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
