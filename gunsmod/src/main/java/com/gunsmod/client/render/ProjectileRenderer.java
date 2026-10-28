package com.gunsmod.client.render;

import com.gunsmod.Reg;
import com.gunsmod.entity.GrenadeEntity;
import com.gunsmod.entity.RocketEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Rockets, 40 mm grenades and thrown grenades are drawn with their 3D item models. */
public class ProjectileRenderer<T extends Entity> extends EntityRenderer<T> {
    public ProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.15f;
    }

    @Override
    public void render(T e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        ItemStack stack;
        float scale;
        pose.pushPose();
        if (e instanceof RocketEntity r && r.isVehicleRound()) {
            // 120 mm shell / helicopter rocket: baked models pointing along -Z
            pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(pt, e.yRotO, e.getYRot()) + 180f));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
            pose.scale(1.6f, 1.6f, 1.6f);
            pose.translate(-0.5f, -0.5f, -0.5f);
            BakedRender.entityCutout(r.getKind() == RocketEntity.TANK_SHELL ? "entity/vehicle_ammo/shell" : "entity/vehicle_ammo/rocket",
                    pose, buf, light, OverlayTexture.NO_OVERLAY, 1, 1, 1);
            pose.popPose();
            super.render(e, yaw, pt, pose, buf, light);
            return;
        }
        if (e instanceof RocketEntity r) {
            stack = new ItemStack(r.getKind() == 0 ? Reg.AMMO_ITEMS.get(com.gunsmod.gun.AmmoType.ROCKET).get() : Reg.AMMO_ITEMS.get(com.gunsmod.gun.AmmoType.GRENADE).get());
            scale = r.getKind() == 0 ? 0.55f : 0.35f;
            // item model points along +X; orient with the flight direction
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, e.yRotO, e.getYRot()) - 90f));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
            if (r.getKind() == 1) pose.mulPose(Axis.ZP.rotationDegrees(-90));
        } else if (e instanceof GrenadeEntity g) {
            stack = new ItemStack(g.isSmoke() ? Reg.SMOKE_GRENADE.get() : Reg.FRAG_GRENADE.get());
            scale = 0.45f;
            pose.translate(0, 0.12f, 0);
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, g.prevSpin, g.spin)));
        } else {
            pose.popPose();
            return;
        }
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buf, e.level(), e.getId());
        pose.popPose();
        super.render(e, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
