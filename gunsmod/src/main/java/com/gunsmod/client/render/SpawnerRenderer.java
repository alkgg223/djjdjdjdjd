package com.gunsmod.client.render;

import com.gunsmod.block.SpawnerBlockEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.item.DogTagItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Floating dog tag hologram with a glowing ring in the team colour. */
public class SpawnerRenderer implements BlockEntityRenderer<SpawnerBlockEntity> {
    public SpawnerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(SpawnerBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        ItemStack tag = be.tag();
        float t = be.clientSpin + pt;
        int color = 0x40C8FF;
        if (DogTagItem.hasData(tag)) color = BotTeam.byId(DogTagItem.data(tag).getInt("team")).color;
        if (!tag.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5f, 0.75f + Mth.sin(t * 0.08f) * 0.06f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(t * 3f));
            pose.scale(0.7f, 0.7f, 0.7f);
            Minecraft.getInstance().getItemRenderer().renderStatic(tag, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, overlay, pose, buf, be.getLevel(), 0);
            pose.popPose();
        }
        if (be.active) {
            float r = ((color >> 16) & 255) / 255f, g = ((color >> 8) & 255) / 255f, b = (color & 255) / 255f;
            VertexConsumer vc = buf.getBuffer(RenderType.lightning());
            pose.pushPose();
            pose.translate(0.5f, 0.2f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(-t * 2f));
            Matrix4f m = pose.last().pose();
            int n = 24;
            for (int ring = 0; ring < 2; ring++) {
                float rad = ring == 0 ? 0.42f : 0.3f;
                float y0 = 0.05f + ring * 0.25f + Mth.sin(t * 0.1f + ring) * 0.05f, y1 = y0 + 0.05f;
                for (int i = 0; i < n; i++) {
                    if (i % 4 == 3) continue;
                    double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                    float x0 = (float) Math.cos(a0) * rad, z0 = (float) Math.sin(a0) * rad;
                    float x1 = (float) Math.cos(a1) * rad, z1 = (float) Math.sin(a1) * rad;
                    vc.addVertex(m, x0, y0, z0).setColor(r, g, b, 0.7f);
                    vc.addVertex(m, x1, y0, z1).setColor(r, g, b, 0.7f);
                    vc.addVertex(m, x1, y1, z1).setColor(r, g, b, 0.0f);
                    vc.addVertex(m, x0, y1, z0).setColor(r, g, b, 0.0f);
                }
            }
            pose.popPose();
        }
    }
}
