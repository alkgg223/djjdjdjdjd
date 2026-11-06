package com.arkragnarok.client.render;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.client.mesh.ArkMesh;
import com.arkragnarok.client.mesh.StaticMeshRenderer;
import com.arkragnarok.entity.ResourceNode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** sculpted rock / metal / obsidian / crystal node, two levels of detail, shakes when hit */
public class ResourceNodeRenderer extends EntityRenderer<ResourceNode> {
    public ResourceNodeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    private static String base(ResourceNode n) {
        return n.kind().id + "_" + n.size();
    }

    @Override
    public void render(ResourceNode node, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (node.depleted()) return;
        double d2 = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(node.position());
        int lod = d2 > 48 * 48 ? 1 : 0;
        String b = base(node);
        ArkMesh mesh = ArkMesh.get(ArkRagnarok.id("arkmesh/node_" + b + "_lod" + lod + ".arkm"));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-node.getYRot()));
        if (node.shake > 0) {
            float s = (node.shake - partialTick) / 6f;
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((node.tickCount + partialTick) * 2.6f) * 2.2f * s));
        }
        StaticMeshRenderer.draw(pose.last(), mesh, ArkRagnarok.id("textures/entity/node/" + b + "_lod" + lod + ".png"), buffers, light,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
        super.render(node, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ResourceNode node) {
        return ArkRagnarok.id("textures/entity/node/" + base(node) + "_lod0.png");
    }
}
