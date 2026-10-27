package com.eggsteal.client;

import com.eggsteal.Creature;
import com.eggsteal.CreatureEntity;
import com.eggsteal.EggSteal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws the creature's smooth OBJ model, tinted with its two colours. */
public class CreatureRenderer extends EntityRenderer<CreatureEntity> {
    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "textures/entity/white.png");

    public CreatureRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.4f;
    }

    @Override
    public ResourceLocation getTextureLocation(CreatureEntity e) { return WHITE; }

    @Override
    public void render(CreatureEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Creature c = e.creature();
        ObjModel m = ObjModel.get(c.id);
        ps.pushPose();
        float s = 0.35f + 0.65f * e.hatchProgress();
        float bob = (float) Math.sin((e.tickCount + pt) * 0.12f) * 0.02f;
        ps.translate(0, bob, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        ps.scale(s, s, s);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose pose = ps.last();
        for (var g : m.groups.entrySet()) {
            int col = switch (g.getKey()) { case "accent" -> c.accent; case "eye" -> 0x111111; default -> c.main; };
            int r = (col >> 16) & 255, gr = (col >> 8) & 255, b = col & 255;
            List<float[]> tris = g.getValue();
            for (float[] t : tris) {
                for (int i = 0; i < 4; i++) {
                    int k = Math.min(i, 2); // quads: repeat the last vertex to draw a triangle
                    vc.addVertex(pose, t[k * 3], t[k * 3 + 1], t[k * 3 + 2])
                            .setColor(r, gr, b, 255)
                            .setUv(0.5f, 0.5f)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, t[9 + k * 3], t[9 + k * 3 + 1], t[9 + k * 3 + 2]);
                }
            }
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }
}
