package com.gunsmod.client.render;

import com.gunsmod.block.airdef.AirDefenseBlock;
import com.gunsmod.entity.BotTeam;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Radar and launcher items show the model, scaled down. */
public class AirDefenseItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static AirDefenseItemRenderer instance;

    public AirDefenseItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (instance == null) instance = new AirDefenseItemRenderer();
            return instance;
        }
    };

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof AirDefenseBlock b)) return;
        pose.pushPose();
        pose.translate(0.5f, 0.1f, 0.5f);
        float s = ctx == ItemDisplayContext.GUI ? 0.17f : ctx == ItemDisplayContext.NONE ? 0.4f : 0.14f;
        pose.scale(s, s, s);
        if (ctx == ItemDisplayContext.GUI) pose.translate(0, 0.8f, 0);
        pose.translate(-0.5f, 0f, -0.5f);
        AirDefenseRenderer.renderModel(b.id(), pose, buf, light, 180f, 0f, b.launcher != null && b.launcher.gun() ? 20f : 30f, BotTeam.GREEN);
        pose.popPose();
    }
}
