package com.eggsteal.client;

import com.eggsteal.brainrot.BrBot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Brainrot Heist bots look like blocky game avatars: big square head with a smiley face, wide shirt, trousers, and one of four
 * hair styles. Colours (skin, shirt, trousers, hair, cap) and hair style come from the bot's UUID, so every bot looks different.
 */
public class BrBotRenderer extends EntityRenderer<BrBot> {
    static final ResourceLocation VOX = BrainrotRenderer.tex("vox");
    static final int[] SKIN = {0xf5cd30, 0xf2c8a0, 0xd8a070, 0x9a6440, 0x6a4028, 0xffe0c8};
    static final int[] SHIRT = {0x3a7ad8, 0xd02a2a, 0x2a2a30, 0xffffff, 0x3ab84a, 0xff7ab8, 0xffc830, 0x8a3ad8, 0xff7a1a, 0x1ac8c8};
    static final int[] PANTS = {0x2a8a3a, 0x1e2a5a, 0x2a2a30, 0x6a4a2a, 0x8a8e98, 0x3a5ad8};
    static final int[] HAIR = {0x5a3a1a, 0x1a1a1a, 0xf0c040, 0xc8481a, 0xd8d8e0, 0xff7ab8, 0x3a6ad8};

    public BrBotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(BrBot e) { return VOX; }

    static int pick(int[] a, long h, int shift) { return a[(int) Math.floorMod(h >> shift, (long) a.length)]; }

    @Override
    public void render(BrBot e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        long h = e.getUUID().getMostSignificantBits() ^ e.getUUID().getLeastSignificantBits();
        ObjModel model = ObjModel.get("brainrot/avatar_" + Math.floorMod(h, 4L));
        float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float head = Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - body;
        float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        float pos = e.walkAnimation.position(pt), spd = Math.min(1f, e.walkAnimation.speed(pt));
        float legR = Mth.cos(pos * 0.6662f) * 1.4f * spd, legL = -legR;
        float armR = Mth.cos(pos * 0.6662f + Mth.PI) * spd, armL = -armR;
        float atk = e.getAttackAnim(pt);
        if (atk > 0) armR -= Mth.sin(atk * Mth.PI) * 1.4f;
        int overlay = LivingEntityRenderer.getOverlayCoords(e, 0f);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-body));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(VOX));
        for (ObjModel.Mesh mesh : model.meshes) {
            int col = switch (mesh.color) {
                case "skin" -> pick(SKIN, h, 3);
                case "shirt" -> pick(SHIRT, h, 9);
                case "pants" -> pick(PANTS, h, 15);
                case "hair" -> pick(HAIR, h, 21);
                case "hat" -> pick(SHIRT, h, 27);
                default -> model.palette.getOrDefault(mesh.color, 0xff00ff);
            };
            boolean animated = mesh.anim != ObjModel.NONE;
            if (animated) {
                ps.pushPose();
                ps.translate(mesh.px, mesh.py, mesh.pz);
                switch (mesh.anim) {
                    case ObjModel.LEG_L -> ps.mulPose(Axis.XP.rotation(legL));
                    case ObjModel.LEG_R -> ps.mulPose(Axis.XP.rotation(legR));
                    case ObjModel.ARM_L -> ps.mulPose(Axis.XP.rotation(armL));
                    case ObjModel.ARM_R -> ps.mulPose(Axis.XP.rotation(armR));
                    case ObjModel.HEAD -> {
                        ps.mulPose(Axis.YP.rotationDegrees(-head));
                        ps.mulPose(Axis.XP.rotationDegrees(pitch));
                    }
                    default -> { }
                }
                ps.translate(-mesh.px, -mesh.py, -mesh.pz);
            }
            PoseStack.Pose pose = ps.last();
            float[] d = mesh.data;
            for (int i = 0; i < d.length; i += ObjModel.STRIDE) {
                float sh = d[i + 6];
                vc.addVertex(pose, d[i], d[i + 1], d[i + 2])
                        .setColor((int) (((col >> 16) & 255) * sh), (int) (((col >> 8) & 255) * sh), (int) ((col & 255) * sh), 255)
                        .setUv(d[i + 7], d[i + 8])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, d[i + 3], d[i + 4], d[i + 5]);
            }
            if (animated) ps.popPose();
        }
        // the bat (or coil) in the right hand, placed like the game does for players
        ItemStack held = e.getMainHandItem();
        if (!held.isEmpty()) {
            ps.pushPose();
            ps.scale(1f, -1f, -1f);
            ps.translate(0f, -1.501f, 0f);
            ps.translate(-5f / 16f, 2f / 16f, 0f);
            ps.mulPose(Axis.XP.rotation(armR));
            ps.mulPose(Axis.XP.rotationDegrees(-90f));
            ps.mulPose(Axis.YP.rotationDegrees(180f));
            ps.translate(1f / 16f, 0.125f, -0.625f);
            Minecraft.getInstance().getItemRenderer().renderStatic(e, held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buf, e.level(), light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, e.getId());
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }
}
