package com.gunsmod.client.render;

import com.gunsmod.item.JeepItem;
import com.gunsmod.item.VehicleItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** The jeep items show a small version of the real jeep. */
public class JeepItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static JeepItemRenderer instance;

    public JeepItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (instance == null) instance = new JeepItemRenderer();
            return instance;
        }
    };

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (stack.getItem() instanceof VehicleItem v) {
            renderVehicle(v, ctx, pose, buf, light);
            return;
        }
        if (!(stack.getItem() instanceof JeepItem item)) return;
        pose.pushPose();
        pose.translate(0.5f, 0.12f, 0.5f);
        float s = ctx == ItemDisplayContext.GUI ? 0.2f : ctx == ItemDisplayContext.NONE ? 0.32f : 0.16f;
        pose.scale(s, s, s);
        if (ctx == ItemDisplayContext.GUI) {
            pose.translate(0, 0.8f, 0);
        }
        pose.mulPose(Axis.YP.rotationDegrees(180));
        JeepRenderer.renderJeep(pose, buf, light, item.team, 0f, 0f, 0f, 0f);
        pose.popPose();
    }

    /** tank and helicopter: same idea, scaled down further (they are much bigger than the jeep) */
    private static void renderVehicle(VehicleItem v, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light) {
        boolean tank = v.kind == VehicleItem.Kind.TANK;
        pose.pushPose();
        pose.translate(0.5f, tank ? 0.14f : 0.08f, 0.5f);
        float s = ctx == ItemDisplayContext.GUI ? (tank ? 0.115f : 0.085f) : ctx == ItemDisplayContext.NONE ? 0.2f : (tank ? 0.09f : 0.07f);
        pose.scale(s, s, s);
        if (ctx == ItemDisplayContext.GUI) pose.translate(0, tank ? 0.6f : 1.4f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        if (tank) TankRenderer.renderTank(pose, buf, light, v.team, 0f, 0f, 0f, 0f, 0f);
        else HelicopterRenderer.renderHeli(pose, buf, light, v.team, 30f, 0f, 0f, 0f, 0f);
        pose.popPose();
    }
}
