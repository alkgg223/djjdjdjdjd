package com.gunsmod.client.render;

import com.gunsmod.Reg;
import com.gunsmod.entity.MissileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MissileRenderer extends EntityRenderer<MissileEntity> {
    public MissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    public static Item item(int kind) {
        return switch (kind) {
            case 1 -> Reg.MISSILE_CLUSTER.get();
            case 2 -> Reg.MISSILE_INCENDIARY.get();
            default -> Reg.MISSILE_HE.get();
        };
    }

    /** missile item model: nose along +Y, centre of the body around y = 8 */
    public static void renderMissile(PoseStack pose, MultiBufferSource buf, int light, int kind, float scale) {
        pose.pushPose();
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(item(kind)), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buf, Minecraft.getInstance().level, 0);
        pose.popPose();
    }

    @Override
    public void render(MissileEntity m, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        pose.pushPose();
        float ry = Mth.rotLerp(pt, m.yRotO, m.getYRot());
        float rx = Mth.lerp(pt, m.xRotO, m.getXRot());
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.XP.rotationDegrees(90f - rx));
        renderMissile(pose, buf, LightTexture.FULL_BRIGHT, m.getKind(), 1.1f);
        pose.popPose();
        super.render(m, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MissileEntity m) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
