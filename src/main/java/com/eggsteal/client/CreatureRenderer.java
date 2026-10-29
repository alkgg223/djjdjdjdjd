package com.eggsteal.client;

import com.eggsteal.Creature;
import com.eggsteal.CreatureEntity;
import com.eggsteal.EggSteal;
import com.eggsteal.Mutation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a creature's smooth OBJ model: per-part colours, soft baked shading, and small animations
 * (breathing, hops, blinking eyes, flapping wings, wagging tails, twitching ears, swaying tentacles).
 * Mutated creatures get animated colours (rainbow, glitch, molten, galaxy). While hatching, a wobbling egg is drawn instead.
 */
public class CreatureRenderer extends EntityRenderer<CreatureEntity> {
    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "textures/entity/white.png");
    static final int FULL_BRIGHT = 0xF000F0;

    public CreatureRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.4f;
    }

    @Override
    public ResourceLocation getTextureLocation(CreatureEntity e) { return WHITE; }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static float hash(float x, float y, float z) {
        double h = Math.sin(x * 12.9898 + y * 78.233 + z * 37.719) * 43758.5453;
        return (float) (h - Math.floor(h));
    }

    static int baseColor(String group, Creature c) {
        return switch (group) {
            case "accent", "glow" -> c.accent;
            case "belly" -> mix(c.main, 0xffffff, 0.55f);
            case "shade" -> mix(c.main, 0x000000, 0.28f);
            case "eye" -> 0x1a1226;
            case "white" -> 0xffffff;
            case "blush" -> mix(0xff7fa8, c.main, 0.2f);
            case "dark" -> 0x3b2530;
            case "gold" -> 0xffd447;
            case "pink" -> 0xff9ec4;
            default -> c.main;
        };
    }

    static boolean recolors(String group) {
        return group.equals("main") || group.equals("accent") || group.equals("belly") || group.equals("shade") || group.equals("glow");
    }

    @Override
    public void render(CreatureEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Creature c = e.creature();
        float t = e.tickCount + pt + (e.getId() % 97) * 7.3f;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        if (e.hatching()) renderEgg(e, t, ps, buf, light);
        else renderModel(e, c, ObjModel.get(c.id), t, pt, ps, buf, light);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    private void renderEgg(CreatureEntity e, float t, PoseStack ps, MultiBufferSource buf, int light) {
        float p = e.hatchProgress();
        ItemStack stack = new ItemStack(EggSteal.EGG_ITEMS.get(Math.floorMod(e.variant(), EggSteal.EGG_ITEMS.size())).get());
        float wobble = (float) Math.sin(t * (0.45f + p * 1.1f)) * (3f + p * 20f);
        ps.translate(0, 0.05, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(wobble));
        float s = 1.7f + (p > 0.85f ? (float) Math.sin(t * 1.3f) * 0.12f : 0f);
        ps.translate(0, 0.38, 0);
        ps.scale(s, s, s);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
    }

    private void renderModel(CreatureEntity e, Creature c, ObjModel m, float t, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Mutation mut = Mutation.of(e.mutation());
        // body motion: breathing, an occasional happy hop (or hovering for flying/swimming creatures), and looking around
        float breathe = (float) Math.sin(t * 0.1f);
        float y = 0f, sy = 1f + 0.025f * breathe, sxz = 1f - 0.012f * breathe;
        if (m.floats) {
            y = 0.1f + (float) Math.sin(t * 0.08f) * 0.07f;
            ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(t * 0.05f) * 4f));
        } else {
            float hop = t % 160f;
            if (hop < 10f) {
                float k = hop / 10f;
                y = (float) Math.sin(k * Math.PI) * 0.14f;
                sy *= 1f + 0.08f * (float) Math.sin(k * Math.PI);
                sxz *= 1f - 0.04f * (float) Math.sin(k * Math.PI);
            }
        }
        boolean glitchFrame = mut == Mutation.GLITCH && hash((int) (t / 2f), 3, 7) > 0.82f;
        if (glitchFrame) ps.translate((hash((int) (t / 2f), 1, 1) - 0.5f) * 0.12f, 0, 0);
        ps.translate(0, y, 0);
        ps.mulPose(Axis.YP.rotationDegrees((float) Math.sin(t * 0.03f) * 12f));
        float pop = e.clientPop > 0 ? 1f - Math.max(0f, e.clientPop - pt) / 10f : 1f;
        float grow = pop < 0.6f ? 0.3f + pop / 0.6f * 0.85f : 1.15f - (pop - 0.6f) / 0.4f * 0.15f;
        ps.scale(grow * sxz, grow * sy, grow * sxz);

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        boolean blink = (t % 110f) < 5f;
        for (ObjModel.Mesh mesh : m.meshes) {
            boolean animated = mesh.anim != ObjModel.NONE;
            if (animated) {
                ps.pushPose();
                ps.translate(mesh.px, mesh.py, mesh.pz);
                animate(mesh, t, m.floats, blink, ps);
                ps.translate(-mesh.px, -mesh.py, -mesh.pz);
            }
            emit(vc, ps.last(), mesh, c, mut, t, glitchFrame, light);
            if (animated) ps.popPose();
        }
    }

    private static void animate(ObjModel.Mesh mesh, float t, boolean floats, boolean blink, PoseStack ps) {
        float ph = mesh.phase;
        switch (mesh.anim) {
            case ObjModel.BLINK -> { if (blink) ps.scale(1f, 0.12f, 1f); }
            case ObjModel.WING_L, ObjModel.WING_R -> {
                float a = (float) Math.sin(t * (floats ? 0.55f : 0.3f) + ph) * (floats ? 32f : 16f);
                ps.mulPose(Axis.ZP.rotationDegrees(mesh.anim == ObjModel.WING_L ? a : -a));
            }
            case ObjModel.TAIL -> ps.mulPose(Axis.YP.rotationDegrees((float) Math.sin(t * 0.22f + ph) * 20f));
            case ObjModel.FIN_L, ObjModel.FIN_R -> {
                float a = (float) Math.sin(t * 0.25f + ph) * 18f;
                ps.mulPose(Axis.YP.rotationDegrees(mesh.anim == ObjModel.FIN_L ? a : -a));
            }
            case ObjModel.EAR_L, ObjModel.EAR_R -> {
                float k = (float) Math.pow(Math.max(0, Math.sin(t * 0.06f + ph)), 14) * 20f;
                ps.mulPose(Axis.ZP.rotationDegrees(mesh.anim == ObjModel.EAR_L ? -k : k));
            }
            case ObjModel.SWAY -> {
                ps.mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 0.12f + ph) * 11f));
                ps.mulPose(Axis.ZP.rotationDegrees((float) Math.cos(t * 0.1f + ph) * 8f));
            }
            case ObjModel.SPIN -> ps.mulPose(Axis.YP.rotationDegrees(t * 3f));
            default -> { }
        }
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose pose, ObjModel.Mesh mesh, Creature c, Mutation mut, float t, boolean glitchFrame, int light) {
        int base = baseColor(mesh.color, c);
        boolean glow = mesh.color.equals("glow");
        boolean perVertex = mut != Mutation.NONE && recolors(mesh.color);
        boolean accent = mesh.color.equals("accent") || glow;
        if (perVertex && mut == Mutation.GLITCH) {
            base = glitchFrame ? ((int) t % 2 == 0 ? 0xff2bd6 : 0x2bd9ff) : accent ? 0x0f2a1a : mix(base, 0x37ff8b, 0.6f);
            perVertex = false;
        }
        float[] d = mesh.data;
        for (int i = 0; i < d.length; i += ObjModel.STRIDE) {
            float x = d[i], yy = d[i + 1], z = d[i + 2], sh = d[i + 6];
            int col = base, l = glow ? FULL_BRIGHT : light;
            if (perVertex) {
                switch (mut) {
                    case RAINBOW -> {
                        float h = t * 0.012f + yy * 0.7f + x * 0.25f + (accent ? 0.5f : 0f);
                        col = Mutation.hsv(h, mesh.color.equals("belly") ? 0.3f : 0.6f, 1f);
                    }
                    case MOLTEN -> {
                        float n = hash(Math.round(x * 14), Math.round(yy * 14), Math.round(z * 14));
                        if (accent || n > 0.8f) {
                            float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.15f + n * 6f);
                            col = mix(0x000000, n > 0.9f ? 0xffd23a : 0xff6a00, pulse);
                            l = FULL_BRIGHT;
                        } else col = mix(base, 0x2a1410, 0.72f);
                    }
                    case GALAXY -> {
                        if (accent) { col = 0x52e8ff; l = FULL_BRIGHT; }
                        else {
                            float n = hash(Math.round(x * 9), Math.round(yy * 9), Math.round(z * 9));
                            col = mix(0x1b1452, 0x7a32e6, n);
                            float star = hash(Math.round(x * 25), Math.round(yy * 25), Math.round(z * 25));
                            if (star > 0.94f && Math.sin(t * 0.2f + star * 40f) > 0.2) { col = 0xffffff; l = FULL_BRIGHT; }
                        }
                    }
                    default -> { }
                }
            }
            int r = (int) (((col >> 16) & 255) * sh), g = (int) (((col >> 8) & 255) * sh), b = (int) ((col & 255) * sh);
            vc.addVertex(pose, x, yy, z)
                    .setColor(r, g, b, 255)
                    .setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(l)
                    .setNormal(pose, d[i + 3], d[i + 4], d[i + 5]);
        }
    }
}
