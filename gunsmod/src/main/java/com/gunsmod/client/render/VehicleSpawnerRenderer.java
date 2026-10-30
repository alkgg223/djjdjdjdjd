package com.gunsmod.client.render;

import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** While a vehicle is due, a small hologram of it turns above the pad with the countdown. */
public class VehicleSpawnerRenderer implements BlockEntityRenderer<VehicleSpawnerBlockEntity> {
    public VehicleSpawnerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(VehicleSpawnerBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        ItemStack v = be.vehicle();
        if (v.isEmpty() || !be.waiting()) return;
        float t = be.clientTicks + pt;
        pose.pushPose();
        pose.translate(0.5f, 1.1f + Mth.sin(t * 0.08f) * 0.05f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.5f));
        pose.scale(1.6f, 1.6f, 1.6f);
        Minecraft.getInstance().getItemRenderer().renderStatic(v, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, overlay, pose, buf, be.getLevel(), 0);
        pose.popPose();
        int secs = be.secondsLeft();
        if (secs <= 0) return;
        String s = secs + "s";
        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        pose.translate(0.5f, 2.3f, 0.5f);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.035f, -0.035f, 0.035f);
        font.drawInBatch(s, -font.width(s) / 2f, 0, 0xFFFFD040, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(VehicleSpawnerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5, 3, 1.5);
    }
}
