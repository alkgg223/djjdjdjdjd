package com.eggsteal.client;

import com.eggsteal.EggSteal;
import com.eggsteal.Mutation;
import com.eggsteal.brainrot.BrMutation;
import com.eggsteal.brainrot.Brainrot;
import com.eggsteal.brainrot.BrainrotEntity;
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
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Draws a voxel brainrot: walking animation on the carpet, idle dance on its podium, wobbling on a thief's head.
 * A mutation recolours it with a three-colour gradient (keeping its shading), lays its voxel pattern over it, makes parts glow,
 * and adds effects around it (flames, lightning, halo, light rays, orbiting stars, bubbles...).
 */
public class BrainrotRenderer extends EntityRenderer<BrainrotEntity> {
    static final int FULL_BRIGHT = 0xF000F0;

    static ResourceLocation tex(String n) { return ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "textures/entity/br/" + n + ".png"); }

    public BrainrotRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.5f;
    }

    @Override
    public ResourceLocation getTextureLocation(BrainrotEntity e) { return tex("vox"); }

    static int gradient(BrMutation m, float lum) {
        lum = Mth.clamp(lum * 1.15f, 0f, 1f);
        return lum < 0.5f ? CreatureRenderer.mix(m.dark, m.mid, lum / 0.5f) : CreatureRenderer.mix(m.mid, m.light, (lum - 0.5f) / 0.5f);
    }

    static float lum(int c) { return (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f; }

    @Override
    public void render(BrainrotEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Brainrot b = e.brainrot();
        BrMutation m = e.mut();
        ObjModel model = ObjModel.get("brainrot/" + b.id);
        float t = e.tickCount + pt + (e.getId() % 97) * 7.3f;
        int state = e.state();
        boolean walking = state == BrainrotEntity.ROAD || state == BrainrotEntity.WALK;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        float swing;
        if (walking) {
            ps.translate(0, Math.abs(Mth.sin(t * 0.5f)) * 0.06f, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.5f) * 3f));
            swing = Mth.sin(t * 0.5f) * 35f;
        } else if (state == BrainrotEntity.CARRIED) {
            ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.3f) * 6f));
            swing = Mth.sin(t * 0.6f) * 25f; // kicking its legs
        } else {
            float dance = t % 240f; // a little happy dance now and then
            if (dance < 30f) {
                ps.translate(0, Math.abs(Mth.sin(dance * 0.42f)) * 0.12f, 0);
                ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(dance * 0.21f) * 25f));
            }
            float br = Mth.sin(t * 0.1f);
            ps.scale(1f - 0.01f * br, 1f + 0.02f * br, 1f - 0.01f * br);
            swing = Mth.sin(t * 0.08f) * 8f;
        }
        if (model.floats) ps.translate(0, 0.12f + Mth.sin(t * 0.08f) * 0.08f, 0);
        boolean ghost = m.special == BrMutation.GHOST;
        ResourceLocation pattern = tex(m.index == 0 ? "vox" : m.pattern);
        float us = m.index == 0 || m.pattern.equals("vox") ? 1f : 0.25f;
        boolean glitchFrame = m.special == BrMutation.GLITCH && CreatureRenderer.hash((int) (t / 2f), 3, 7) > 0.8f;
        if (glitchFrame) ps.translate((CreatureRenderer.hash((int) (t / 2f), 1, 1) - 0.5f) * 0.15f, 0, 0);
        VertexConsumer vc = buf.getBuffer(ghost ? RenderType.entityTranslucent(pattern) : RenderType.entityCutoutNoCull(pattern));
        for (ObjModel.Mesh mesh : model.meshes) {
            boolean animated = mesh.anim != ObjModel.NONE;
            if (animated) { ps.pushPose(); animate(mesh, t, swing, walking, ps); }
            emitBase(vc, ps.last(), mesh, model, m, t, us, glitchFrame, ghost, light);
            if (animated) ps.popPose();
        }
        if (m.glow != null) { // the glowing parts of the pattern, drawn again on top with additive light
            VertexConsumer g = buf.getBuffer(RenderType.eyes(tex(m.glow)));
            float pulse = 0.7f + 0.3f * Mth.sin(t * 0.12f);
            for (ObjModel.Mesh mesh : model.meshes) {
                if (mesh.color.startsWith("k_")) continue;
                boolean animated = mesh.anim != ObjModel.NONE;
                if (animated) { ps.pushPose(); animate(mesh, t, swing, walking, ps); }
                emitGlow(g, ps.last(), mesh, m.glowColor, pulse);
                if (animated) ps.popPose();
            }
        }
        ps.popPose();
        if (m.extras != 0) extras(e.getId(), model, m, t, ps, buf);
        super.render(e, yaw, pt, ps, buf, light);
    }

    static void animate(ObjModel.Mesh mesh, float t, float swing, boolean walking, PoseStack ps) {
        ps.translate(mesh.px, mesh.py, mesh.pz);
        switch (mesh.anim) {
            case ObjModel.LEG_L -> ps.mulPose(Axis.XP.rotationDegrees(swing));
            case ObjModel.LEG_R -> ps.mulPose(Axis.XP.rotationDegrees(-swing));
            case ObjModel.ARM_L -> ps.mulPose(Axis.XP.rotationDegrees(-swing * 0.8f));
            case ObjModel.ARM_R -> ps.mulPose(Axis.XP.rotationDegrees(swing * 0.8f));
            case ObjModel.HEAD -> ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * (walking ? 0.5f : 0.08f)) * 8f));
            case ObjModel.TAIL -> ps.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.25f + mesh.phase) * 20f));
            case ObjModel.WING_L, ObjModel.WING_R -> {
                float a = Mth.sin(t * 0.4f) * 22f;
                ps.mulPose(Axis.ZP.rotationDegrees(mesh.anim == ObjModel.WING_L ? a : -a));
            }
            case ObjModel.EAR_L, ObjModel.EAR_R -> {
                float a = Mth.sin(t * 0.15f) * 10f;
                ps.mulPose(Axis.YP.rotationDegrees(mesh.anim == ObjModel.EAR_L ? a : -a));
            }
            case ObjModel.SWAY -> {
                ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.12f + mesh.phase) * 10f));
                ps.mulPose(Axis.ZP.rotationDegrees(Mth.cos(t * 0.1f + mesh.phase) * 7f));
            }
            case ObjModel.SPIN -> ps.mulPose(Axis.YP.rotationDegrees(t * 3f));
            default -> { }
        }
        ps.translate(-mesh.px, -mesh.py, -mesh.pz);
    }

    static void emitBase(VertexConsumer vc, PoseStack.Pose pose, ObjModel.Mesh mesh, ObjModel model, BrMutation m, float t, float us,
                         boolean glitchFrame, boolean ghost, int light) {
        int base = model.palette.getOrDefault(mesh.color, 0xff00ff);
        boolean keep = mesh.color.startsWith("k_");
        boolean glowSlot = mesh.color.startsWith("g_");
        int l = glowSlot || (m.special == BrMutation.FULLBRIGHT && !keep) ? FULL_BRIGHT : light;
        int alpha = ghost && !keep ? 140 : 255;
        int col = base;
        boolean perVertex = false;
        if (!keep && m.index > 0) {
            switch (m.special) {
                case BrMutation.RAINBOW, BrMutation.YIN_YANG, BrMutation.HOLO -> perVertex = true;
                case BrMutation.GLITCH -> col = glitchFrame ? ((int) t % 2 == 0 ? 0xff2bd6 : 0x2bd9ff) : gradient(m, lum(base));
                default -> col = gradient(m, lum(base));
            }
        }
        float lb = lum(base);
        float[] d = mesh.data;
        for (int i = 0; i < d.length; i += ObjModel.STRIDE) {
            float x = d[i], y = d[i + 1], z = d[i + 2], sh = d[i + 6];
            int c = col;
            if (perVertex) {
                c = switch (m.special) {
                    case BrMutation.RAINBOW -> Mutation.hsv(t * 0.01f + y * 0.5f + x * 0.2f, 0.75f, 0.55f + 0.45f * lb);
                    case BrMutation.YIN_YANG -> x < 0 ? CreatureRenderer.mix(0x101010, 0x404040, lb) : CreatureRenderer.mix(0xc0c0c0, 0xffffff, lb);
                    default -> Mutation.hsv(t * 0.008f + d[i + 3] * 0.25f + d[i + 4] * 0.15f + y * 0.3f, 0.35f, 0.8f + 0.2f * lb);
                };
            }
            vc.addVertex(pose, x, y, z)
                    .setColor((int) (((c >> 16) & 255) * sh), (int) (((c >> 8) & 255) * sh), (int) ((c & 255) * sh), alpha)
                    .setUv(d[i + 7] * us, d[i + 8] * us)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(l)
                    .setNormal(pose, d[i + 3], d[i + 4], d[i + 5]);
        }
    }

    static void emitGlow(VertexConsumer vc, PoseStack.Pose pose, ObjModel.Mesh mesh, int color, float k) {
        int r = (int) (((color >> 16) & 255) * k), g = (int) (((color >> 8) & 255) * k), b = (int) ((color & 255) * k);
        float[] d = mesh.data;
        for (int i = 0; i < d.length; i += ObjModel.STRIDE) {
            vc.addVertex(pose, d[i] + d[i + 3] * 0.002f, d[i + 1] + d[i + 4] * 0.002f, d[i + 2] + d[i + 5] * 0.002f)
                    .setColor(r, g, b, 255)
                    .setUv(d[i + 7] * 0.25f, d[i + 8] * 0.25f)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(pose, d[i + 3], d[i + 4], d[i + 5]);
        }
    }

    /** Effects around the brainrot, made of camera-facing glowing sprites (see EventSkyRenderer.Fx). */
    static void extras(int id, ObjModel model, BrMutation m, float t, PoseStack ps, MultiBufferSource buf) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        EventSkyRenderer.Fx fx = new EventSkyRenderer.Fx(ps.last(), buf, cam.rotation(), 1f, cam.getPosition());
        float top = model.maxY, w = Math.max(model.maxX - model.minX, model.maxZ - model.minZ) * 0.5f;
        float cy = (model.minY + model.maxY) * 0.5f;
        EventSkyRenderer.Batch glow = fx.vc(EventSkyRenderer.GLOW), star = fx.vc(EventSkyRenderer.STAR);
        if ((m.extras & (BrMutation.FIRE | BrMutation.RAINBOW_FIRE)) != 0) {
            boolean cosmic = (m.extras & BrMutation.RAINBOW_FIRE) != 0;
            for (int k = 0; k < 22; k++) { // flames licking up all over it
                float ph = ((t * 0.045f + EventSkyRenderer.h(k, id)) % 1f);
                float a = EventSkyRenderer.h(k, 3) * Mth.TWO_PI, r = w * (0.35f + 0.6f * EventSkyRenderer.h(k, 4));
                float y0 = k < 12 ? top * 0.75f : model.minY + (top - model.minY) * EventSkyRenderer.h(k, 5);
                float x = Mth.cos(a) * r, z = Mth.sin(a) * r, y = y0 + ph * 0.7f;
                int c = cosmic ? Mutation.hsv(0.75f + ph * 0.3f + EventSkyRenderer.h(k, 6) * 0.2f, 0.8f, 1f)
                        : (ph < 0.35f ? 0xfff070 : ph < 0.7f ? 0xff8a20 : 0xe02a0a);
                fx.sprite(glow, x, y, z, 0.32f * (1.1f - ph), c, 1f - ph);
            }
            fx.sprite(glow, 0, top * 0.8f, 0, w * 1.6f, cosmic ? 0x8a3aff : 0xff5a10, 0.25f);
        }
        if ((m.extras & BrMutation.GLINT) != 0) {
            int cycle = (int) (t / 18f);
            float ph = (t % 18f) / 18f;
            float x = (EventSkyRenderer.h(cycle, id) - 0.5f) * w * 1.6f, y = model.minY + (top - model.minY) * EventSkyRenderer.h(cycle, 2);
            fx.sprite(star, x, y, w * 0.9f, 0.35f * Mth.sin(ph * Mth.PI), 0xffffff, Mth.sin(ph * Mth.PI));
        }
        if ((m.extras & BrMutation.BUBBLES) != 0) {
            for (int k = 0; k < 8; k++) {
                float ph = (t * 0.012f + EventSkyRenderer.h(k, id)) % 1f;
                float a = EventSkyRenderer.h(k, 2) * Mth.TWO_PI;
                fx.sprite(glow, Mth.cos(a) * w + Mth.sin(t * 0.1f + k) * 0.1f, model.minY + ph * (top + 0.6f), Mth.sin(a) * w, 0.12f + 0.08f * EventSkyRenderer.h(k, 3),
                        k % 2 == 0 ? 0x9ae8ff : 0xffa0d8, 0.7f * (1f - ph));
            }
        }
        if ((m.extras & BrMutation.LIGHTNING) != 0 && (t % 14f) < 5f) {
            int cycle = (int) (t / 14f);
            Vector3f prev = new Vector3f((EventSkyRenderer.h(cycle, 1) - 0.5f) * w * 2f, top + 0.2f, (EventSkyRenderer.h(cycle, 2) - 0.5f) * w * 2f);
            for (int s = 1; s <= 5; s++) {
                Vector3f next = new Vector3f((EventSkyRenderer.h(cycle * 7 + s, 3) - 0.5f) * w * 2.2f, top + 0.2f - s * top / 5f, (EventSkyRenderer.h(cycle * 7 + s, 4) - 0.5f) * w * 2.2f);
                fx.beam(glow, prev, next, 0.12f, 0xfff8a0, 1f, 1f);
                prev = next;
            }
        }
        if ((m.extras & BrMutation.HALO) != 0) {
            for (int k = 0; k < 18; k++) {
                float a = k * Mth.TWO_PI / 18f + t * 0.03f;
                fx.sprite(glow, Mth.cos(a) * w * 0.55f, top + 0.3f, Mth.sin(a) * w * 0.55f, 0.13f, 0xffe070, 0.95f);
            }
        }
        if ((m.extras & BrMutation.CROWN) != 0) {
            for (int k = 0; k < 5; k++) {
                float a = k * Mth.TWO_PI / 5f + t * 0.02f;
                fx.sprite(star, Mth.cos(a) * w * 0.35f, top + 0.15f + (k % 2) * 0.12f, Mth.sin(a) * w * 0.35f, 0.18f, 0xffd040, 1f);
            }
        }
        if ((m.extras & BrMutation.RAYS) != 0) {
            Vector3f c = new Vector3f(0, cy, 0);
            for (int k = 0; k < 12; k++) {
                float a = k * Mth.TWO_PI / 12f + t * 0.01f;
                Vector3f end = new Vector3f(c).add(new Vector3f(fx.right).mul(Mth.cos(a) * w * 2.6f)).add(new Vector3f(fx.up).mul(Mth.sin(a) * w * 2.6f));
                fx.beam(glow, c, end, 0.3f, 0xffd060, 0.35f, 0f);
            }
        }
        if ((m.extras & BrMutation.ORBIT) != 0) {
            for (int k = 0; k < 6; k++) {
                float a = k * Mth.TWO_PI / 6f + t * 0.05f;
                fx.sprite(star, Mth.cos(a) * w * 1.3f, cy + Mth.sin(a * 2f) * 0.2f, Mth.sin(a) * w * 1.3f, 0.16f, k % 2 == 0 ? 0xffffff : 0xffa0ff, 1f);
            }
        }
        if ((m.extras & BrMutation.VOID_RING) != 0) {
            for (int k = 0; k < 26; k++) {
                float a = k * Mth.TWO_PI / 26f - t * 0.04f;
                fx.sprite(glow, Mth.cos(a) * w * 1.2f, cy * 0.6f + Mth.sin(a * 3f + t * 0.05f) * 0.05f, Mth.sin(a) * w * 1.2f, 0.16f, 0xa050ff, 0.9f);
            }
        }
        if ((m.extras & BrMutation.PULSE) != 0) {
            float ph = (t % 40f) / 40f;
            for (int k = 0; k < 20; k++) {
                float a = k * Mth.TWO_PI / 20f;
                fx.sprite(glow, Mth.cos(a) * w * (0.4f + ph * 1.8f), 0.1f, Mth.sin(a) * w * (0.4f + ph * 1.8f), 0.14f, 0x60ff40, 1f - ph);
            }
        }
        if ((m.extras & BrMutation.SNOW) != 0) {
            for (int k = 0; k < 10; k++) {
                float ph = (t * 0.01f + EventSkyRenderer.h(k, id)) % 1f;
                float a = EventSkyRenderer.h(k, 2) * Mth.TWO_PI;
                fx.sprite(star, Mth.cos(a) * w * 1.1f, top + 0.4f - ph * (top + 0.4f), Mth.sin(a) * w * 1.1f, 0.08f, 0xffffff, 0.9f * (1f - ph));
            }
        }
        if ((m.extras & BrMutation.SHARDS) != 0) {
            for (int k = 0; k < 5; k++) {
                float a = k * Mth.TWO_PI / 5f + t * 0.02f;
                fx.sprite(star, Mth.cos(a) * w * 1.15f, model.minY + (top - model.minY) * (0.3f + 0.15f * k), Mth.sin(a) * w * 1.15f, 0.2f, 0xe0a0ff, 0.9f);
            }
        }
        fx.flush();
    }
}
