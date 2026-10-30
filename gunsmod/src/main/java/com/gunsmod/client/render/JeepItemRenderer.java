package com.gunsmod.client.render;

import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.VehicleKind;
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

/** The vehicle items show a small version of the real vehicle. */
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
        if (stack.getItem() instanceof VehicleItem v && v.kind.group != VehicleKind.Group.JEEP) {
            renderVehicle(v, ctx, pose, buf, light);
            return;
        }
        BotTeam team;
        if (stack.getItem() instanceof JeepItem item) team = item.team;
        else if (stack.getItem() instanceof VehicleItem v) team = v.team;
        else return;
        pose.pushPose();
        pose.translate(0.5f, 0.12f, 0.5f);
        float s = ctx == ItemDisplayContext.GUI ? 0.2f : ctx == ItemDisplayContext.NONE ? 0.32f : 0.16f;
        pose.scale(s, s, s);
        if (ctx == ItemDisplayContext.GUI) {
            pose.translate(0, 0.8f, 0);
        }
        pose.mulPose(Axis.YP.rotationDegrees(180));
        JeepRenderer.renderJeep(pose, buf, light, team, 0f, 0f, 0f, 0f, stack.getItem() instanceof VehicleItem);
        pose.popPose();
    }

    /** tanks, helicopters and armed cars: same idea, scaled down to fit (they are much bigger than the jeep) */
    private static void renderVehicle(VehicleItem v, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light) {
        VehicleKind.Group grp = v.kind.group;
        boolean heli = grp == VehicleKind.Group.HELI;
        boolean longOne = v.kind == VehicleKind.PANTSIR || v.kind == VehicleKind.HIMARS || v.kind == VehicleKind.GRAD;
        if (grp == VehicleKind.Group.JET) {
            pose.pushPose();
            pose.translate(0.5f, 0.1f, 0.5f);
            float s = ctx == ItemDisplayContext.GUI ? 0.06f : ctx == ItemDisplayContext.NONE ? 0.13f : 0.05f;
            pose.scale(s, s, s);
            if (ctx == ItemDisplayContext.GUI) pose.translate(0, 1.6f, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            JetRenderer.renderJet(pose, buf, light, v.team, 0f, JetEntity.ALL_STORES, false, false, 1f);
            pose.popPose();
            return;
        }
        pose.pushPose();
        pose.translate(0.5f, heli ? 0.08f : 0.14f, 0.5f);
        float s = ctx == ItemDisplayContext.GUI ? (heli ? 0.085f : longOne ? 0.085f : 0.115f) : ctx == ItemDisplayContext.NONE ? 0.2f : (heli ? 0.07f : 0.09f);
        if (grp == VehicleKind.Group.TANK) s /= v.kind.tank().scale;
        pose.scale(s, s, s);
        if (ctx == ItemDisplayContext.GUI) pose.translate(0, heli ? 1.4f : 0.6f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        switch (grp) {
            case TANK -> TankRenderer.renderTank(pose, buf, light, v.kind.tank(), v.team, 0f, 0f, 0f, 0f, 0f);
            case HELI -> HelicopterRenderer.renderHeli(pose, buf, light, v.kind.heli(), v.team, 30f, 0f, 0f, 0f, 0f);
            case CAR -> ArmoredCarRenderer.renderCar(pose, buf, light, v.kind.car(), v.team, 0f, 0f, 0f, 0f, 30f);
            default -> { }
        }
        pose.popPose();
    }
}
