package com.rpbusiness.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rpbusiness.block.*;
import com.rpbusiness.core.ModItems;
import com.rpbusiness.food.Foods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Shows the real food sitting on boards, grills, fryers, ovens, pots, prep stations and counters. */
public final class FoodRenderers {
    private FoodRenderers() {}

    static void flat(PoseStack ps, ItemStack s, double x, double y, double z, float scale, float spin, int light, MultiBufferSource buf, BlockEntity be) {
        if (s.isEmpty()) return;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        ps.mulPose(Axis.XP.rotationDegrees(90));
        ps.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf, be.getLevel(), 0);
        ps.popPose();
    }

    static float yaw(BlockEntity be) {
        var st = be.getBlockState();
        if (!st.hasProperty(HorizontalDirectionalBlock.FACING)) return 0;
        return -st.getValue(HorizontalDirectionalBlock.FACING).toYRot();
    }

    static int lightAbove(BlockEntity be, int fallback) {
        return be.getLevel() == null ? fallback : LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
    }

    public static class CuttingBoard implements BlockEntityRenderer<CuttingBoardBE> {
        public CuttingBoard(BlockEntityRendererProvider.Context c) {}
        @Override
        public void render(CuttingBoardBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            flat(ps, be.item(), 0.5, 0.11, 0.5, 0.55f, yaw(be) + 15, light, buf, be);
        }
    }

    public static class Cooker implements BlockEntityRenderer<CookerBE> {
        public Cooker(BlockEntityRendererProvider.Context c) {}
        @Override
        public void render(CookerBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            switch (be.cooker) {
                case GRILL -> {
                    int l = lightAbove(be, light);
                    for (int i = 0; i < be.size(); i++) {
                        ps.pushPose();
                        ps.translate(0.5, 0, 0.5);
                        ps.mulPose(Axis.YP.rotationDegrees(yaw(be)));
                        ps.translate(i == 0 ? 0.22 : -0.22, 0, 0);
                        ps.translate(-0.5, 0, -0.5);
                        ItemStack s = be.displayed(i);
                        // flipping shows the other side for a moment: tilt slightly once flipped
                        flat(ps, s, 0.5, 0.96, 0.5, 0.42f, be.flipped(i) ? 180 : 0, l, buf, be);
                        ps.popPose();
                    }
                }
                case FRYER -> flat(ps, be.displayed(0), 0.5, 0.8, 0.5, 0.45f, yaw(be), light, buf, be);
                case OVEN -> flat(ps, be.displayed(0), 0.5, 0.36, 0.5, 0.5f, yaw(be), light, buf, be);
                case POT -> flat(ps, be.displayed(0), 0.5, 0.48, 0.5, 0.4f, yaw(be), light, buf, be);
            }
        }
    }

    public static class Prep implements BlockEntityRenderer<PrepStationBE> {
        public Prep(BlockEntityRendererProvider.Context c) {}
        @Override
        public void render(PrepStationBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            int l = lightAbove(be, light);
            int i = 0;
            for (ItemStack s : be.items()) {
                flat(ps, s, 0.5, 1.02 + i * 0.035, 0.5, 0.5f, yaw(be) + i * 9, l, buf, be);
                i++;
            }
        }
    }

    public static class Drink implements BlockEntityRenderer<DrinkMachineBE> {
        public Drink(BlockEntityRendererProvider.Context c) {}
        @Override
        public void render(DrinkMachineBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            ItemStack s = be.output().isEmpty() ? (be.busy() ? new ItemStack(ModItems.PAPER_CUP.get()) : ItemStack.EMPTY) : be.output();
            if (s.isEmpty()) return;
            ps.pushPose();
            ps.translate(0.5, 0, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(yaw(be)));
            ps.translate(0, 0.22, -0.28);
            ps.scale(0.32f, 0.32f, 0.32f);
            Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf, be.getLevel(), 0);
            ps.popPose();
        }
    }

    public static class Serving implements BlockEntityRenderer<ServingCounterBE> {
        public Serving(BlockEntityRendererProvider.Context c) {}
        @Override
        public void render(ServingCounterBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
            int l = lightAbove(be, light);
            double[][] pos = {{0.28, 0.28}, {0.72, 0.28}, {0.28, 0.72}, {0.72, 0.72}};
            for (int i = 0; i < ServingCounterBE.SIZE; i++)
                flat(ps, be.get(i), pos[i][0], 1.01, pos[i][1], 0.4f, yaw(be), l, buf, be);
        }
    }

    @SuppressWarnings("unused")
    private static Direction unused() { return Direction.NORTH; }
    @SuppressWarnings("unused")
    private static int q(ItemStack s) { return Foods.qualityOf(s); }
}
