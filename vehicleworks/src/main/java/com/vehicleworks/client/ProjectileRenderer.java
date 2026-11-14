package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehicleworks.VehicleWorks;
import com.vehicleworks.weapon.ProjectileEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Tracers for bullets, simple models for shells, missiles and bombs. */
public class ProjectileRenderer extends EntityRenderer<ProjectileEntity> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, "textures/entity/projectile.png");

    public ProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(ProjectileEntity e) {
        return TEX;
    }

    @Override
    public void render(ProjectileEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.xRotO, e.getXRot())));
        switch (e.kind()) {
            case BULLET -> {
                VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(TEX));
                UvBox.cuboid(ps, vc, -0.03f, -0.03f, -2.2f, 0.03f, 0.03f, 0.2f, LightTexture.FULL_BRIGHT, 0xFFFFE080, 0f, 0f, 0.25f, 1f);
            }
            case SHELL -> {
                VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
                UvBox.cuboid(ps, vc, -0.09f, -0.09f, -0.25f, 0.09f, 0.09f, 0.25f, light, 0xFFFFFFFF, 0.25f, 0f, 0.5f, 1f);
            }
            case MISSILE -> {
                VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
                UvBox.cuboid(ps, vc, -0.1f, -0.1f, -0.9f, 0.1f, 0.1f, 0.9f, light, 0xFFFFFFFF, 0.5f, 0f, 0.75f, 1f);
                UvBox.cuboid(ps, vc, -0.32f, -0.02f, -0.8f, 0.32f, 0.02f, -0.5f, light, 0xFFFFFFFF, 0.5f, 0f, 0.75f, 1f);
                UvBox.cuboid(ps, vc, -0.02f, -0.32f, -0.8f, 0.02f, 0.32f, -0.5f, light, 0xFFFFFFFF, 0.5f, 0f, 0.75f, 1f);
                VertexConsumer glow = buf.getBuffer(RenderType.entityTranslucentEmissive(TEX));
                UvBox.cuboid(ps, glow, -0.07f, -0.07f, -1.3f, 0.07f, 0.07f, -0.9f, LightTexture.FULL_BRIGHT, 0xFFFF9040, 0f, 0f, 0.25f, 1f);
            }
            case BOMB -> {
                VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
                UvBox.cuboid(ps, vc, -0.2f, -0.2f, -0.55f, 0.2f, 0.2f, 0.55f, light, 0xFFFFFFFF, 0.75f, 0f, 1f, 1f);
                UvBox.cuboid(ps, vc, -0.3f, -0.02f, -0.75f, 0.3f, 0.02f, -0.45f, light, 0xFFFFFFFF, 0.75f, 0f, 1f, 1f);
            }
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buf, light);
    }
}
