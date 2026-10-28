package com.gunsmod.client.render;

import com.gunsmod.Reg;
import com.gunsmod.client.GunClient;
import com.gunsmod.client.GunGeometry;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunAttachments;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders guns from their part models: body, magazine (animated while reloading), break-action barrels, the minigun
 * barrel cluster, installed attachments, a laser beam and the first-person muzzle flash.
 */
public class GunRenderer extends BlockEntityWithoutLevelRenderer {
    private static GunRenderer instance;

    public static GunRenderer get() {
        if (instance == null) instance = new GunRenderer();
        return instance;
    }
    /** model unit -> blocks: 1 unit is about 2.2 cm */
    private static final float SCALE = 0.352f;

    private static float sprint, prevSprint;

    public GunRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return get();
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick, float equip, float swing) {
            GunClient.partialTick = partialTick;
            pose.translate(0, equip * -0.5f, 0);
            return true;
        }
    };

    public static void clientTick() {
        prevSprint = sprint;
        LocalPlayer p = Minecraft.getInstance().player;
        boolean s = p != null && p.isSprinting() && GunClient.aim < 0.1f;
        sprint = Mth.clamp(sprint + (s ? 0.2f : -0.25f), 0f, 1f);
    }

    private static String part(GunType t, String p) {
        return "gun/" + t.id + "/" + p;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof GunItem gun)) return;
        GunType t = gun.type;
        GunGeometry.Geo geo = GunGeometry.get(t.id);
        if (geo == null) return;
        boolean fp = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean tp = ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean left = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (fp && GunClient.scoped()) return;
        pose.pushPose();
        if (fp) {
            pose.translate(0.5f, 0.5f, 0.5f);
            firstPerson(pose, stack, t, geo, left);
        } else if (tp) {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.translate(0, 0.03f, 0.02f);
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.scale(SCALE, SCALE, SCALE);
            pose.translate(-geo.grip()[0] / 16f, -geo.grip()[1] / 16f, -geo.grip()[2] / 16f);
        }
        renderGun(stack, t, geo, pose, buffers, light, overlay, fp, fp || tp);
        pose.popPose();
    }

    private static float reloadProgress(ItemStack stack, GunType t) {
        Long end = stack.get(Reg.RELOAD.get());
        Minecraft mc = Minecraft.getInstance();
        if (end == null || mc.level == null) return -1f;
        float total = GunItem.attachments(stack).reloadTicks(t);
        float now = mc.level.getGameTime() + GunClient.partialTick;
        return Mth.clamp(1f - (end - now) / total, 0f, 1f);
    }

    private static float[] eyePoint(GunType t, GunGeometry.Geo geo, GunAttachments att) {
        Attachment scope = att.get(Attachment.Slot.SCOPE);
        if (scope != null && t.supports(Attachment.Slot.SCOPE)) {
            float[] m = geo.scope();
            return new float[]{m[0], m[1] + scope.eyeHeight, m[2]};
        }
        return geo.sight();
    }

    private static void firstPerson(PoseStack pose, ItemStack stack, GunType t, GunGeometry.Geo geo, boolean left) {
        float pt = GunClient.partialTick;
        float a = GunClient.aim(pt);
        a = a * a * (3 - 2 * a);
        float k = GunClient.kick(pt);
        float rp = reloadProgress(stack, t);
        float sp = Mth.lerp(pt, prevSprint, sprint);
        GunAttachments att = GunItem.attachments(stack);
        boolean pistol = t.category == GunType.Category.PISTOL;
        float hx = pistol ? 0.24f : 0.27f, hy = pistol ? -0.27f : -0.30f, hz = pistol ? -0.50f : -0.56f;
        if (t == GunType.MINIGUN) {
            hx = 0.22f;
            hy = -0.42f;
            hz = -0.75f;
        }
        if (left) hx = -hx;
        float[] eye = eyePoint(t, geo, att);
        boolean scope = att.get(Attachment.Slot.SCOPE) != null;
        float adsZ = scope ? -0.30f : -0.22f;
        float px = Mth.lerp(a, hx, 0f), py = Mth.lerp(a, hy, 0f), pz = Mth.lerp(a, hz, adsZ);
        float[] grip = geo.grip();
        float ax = Mth.lerp(a, grip[0], eye[0]), ay = Mth.lerp(a, grip[1], eye[1]), az = Mth.lerp(a, grip[2], eye[2]);
        pose.translate(px, py, pz);
        // recoil
        float recoilDeg = Math.min(14f, t.recoil * 1.3f) * (1f - a * 0.55f) * att.recoilMul();
        pose.translate(0, k * 0.012f, k * 0.06f * (1f - a * 0.4f));
        pose.mulPose(Axis.XP.rotationDegrees(k * recoilDeg));
        // reload tilt
        if (rp >= 0) {
            float w = Mth.sin(rp * Mth.PI);
            pose.translate(0, -0.07f * w, 0.03f * w);
            pose.mulPose(Axis.ZP.rotationDegrees(26f * w));
            pose.mulPose(Axis.XP.rotationDegrees(12f * w));
        }
        // sprint
        if (sp > 0) {
            pose.translate(0.02f * sp, -0.08f * sp, 0.04f * sp);
            pose.mulPose(Axis.YP.rotationDegrees(30f * sp));
            pose.mulPose(Axis.XP.rotationDegrees(-12f * sp));
        }
        // idle sway
        float time = (GunClient.ticks + pt) * 0.06f;
        float sway = (1f - a * 0.9f) * 0.004f;
        pose.translate(Mth.sin(time) * sway, Mth.sin(time * 2f) * sway * 0.6f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-ax / 16f, -ay / 16f, -az / 16f);
    }

    private static void renderGun(ItemStack stack, GunType t, GunGeometry.Geo geo, PoseStack pose, MultiBufferSource buf, int light, int overlay,
                                  boolean fp, boolean held) {
        GunAttachments att = GunItem.attachments(stack);
        float rp = fp ? reloadProgress(stack, t) : -1f;
        BakedRender.item(stack, part(t, "body"), pose, buf, light, overlay);

        if (geo.has("spin")) {
            float[] s = geo.spin();
            pose.pushPose();
            pose.translate(0, s[1] / 16f, s[2] / 16f);
            float ang = fp ? (GunClient.spin + GunClient.spinSpeed * GunClient.partialTick) * 40f : 0f;
            pose.mulPose(Axis.XP.rotationDegrees(ang));
            pose.translate(0, -s[1] / 16f, -s[2] / 16f);
            BakedRender.item(stack, part(t, "spin"), pose, buf, light, overlay);
            pose.popPose();
        }
        if (geo.has("break")) {
            float[] h = geo.hinge();
            float open = 0f;
            if (rp >= 0) open = rp < 0.15f ? rp / 0.15f : rp > 0.8f ? Math.max(0f, (1f - rp) / 0.2f) : 1f;
            pose.pushPose();
            pose.translate(h[0] / 16f, h[1] / 16f, h[2] / 16f);
            pose.mulPose(Axis.ZP.rotationDegrees(-38f * open));
            pose.translate(-h[0] / 16f, -h[1] / 16f, -h[2] / 16f);
            BakedRender.item(stack, part(t, "break"), pose, buf, light, overlay);
            pose.popPose();
        }
        // magazine
        boolean single = t == GunType.RPG7 || t == GunType.M79;
        boolean showMag = !single || GunItem.ammo(stack) > 0 || rp > 0.55f;
        if (showMag) {
            pose.pushPose();
            if (rp >= 0) {
                float out;
                if (rp < 0.12f) out = 0f;
                else if (rp < 0.32f) out = (rp - 0.12f) / 0.2f;
                else if (rp < 0.58f) out = 1f + (rp - 0.32f) * 4f;
                else if (rp < 0.82f) out = 1f - (rp - 0.58f) / 0.24f;
                else out = 0f;
                if (single && rp < 0.55f) out = 0f;
                float[] d = geo.magDir();
                float dist = out * 9f;
                pose.translate(d[0] * dist / 16f, d[1] * dist / 16f, d[2] * dist / 16f);
            }
            boolean ext = att.has(Attachment.EXTENDED_MAG) && geo.has("mag_ext") && t.supports(Attachment.Slot.MAG);
            BakedRender.item(stack, part(t, ext ? "mag_ext" : "mag"), pose, buf, light, overlay);
            pose.popPose();
        }
        // attachments
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            Attachment a = att.get(slot);
            if (a == null || slot == Attachment.Slot.MAG || !t.supports(slot)) continue;
            ItemStack as = new ItemStack(Reg.ATTACHMENT_ITEMS.get(a).get());
            pose.pushPose();
            switch (slot) {
                case SCOPE -> {
                    float[] m = geo.scope();
                    pose.translate(m[0] / 16f, m[1] / 16f, m[2] / 16f);
                    pose.translate(-8 / 16f, -4 / 16f, -8 / 16f);
                }
                case MUZZLE -> {
                    float[] m = geo.muzzle();
                    pose.translate(m[0] / 16f, m[1] / 16f, m[2] / 16f);
                    pose.translate(-2 / 16f, -8 / 16f, -8 / 16f);
                }
                case GRIP -> {
                    float[] m = geo.under();
                    pose.translate(m[0] / 16f, m[1] / 16f, m[2] / 16f);
                    pose.translate(-8 / 16f, -12 / 16f, -8 / 16f);
                }
                case LASER -> {
                    float[] m = geo.side();
                    pose.translate(m[0] / 16f, m[1] / 16f, m[2] / 16f);
                    pose.mulPose(Axis.XP.rotationDegrees(-90));
                    pose.translate(-8 / 16f, -12 / 16f, -8 / 16f);
                }
                default -> { }
            }
            pose.pushPose();
            pose.translate(0.5f, 0.5f, 0.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(as, ItemDisplayContext.NONE, light, overlay, pose, buf, Minecraft.getInstance().level, 0);
            pose.popPose();
            if (slot == Attachment.Slot.LASER && held) {
                // beam from the emitter (local 11.2, 10.3, 7.55)
                laser(pose, buf, 11.2f / 16f, 10.3f / 16f, 7.55f / 16f);
            }
            pose.popPose();
        }
        // muzzle flash (first person only, world flashes are particles)
        if (fp && GunClient.flashTicks > 0 && t.kind != GunType.Kind.FLAME) {
            float[] m = geo.muzzle();
            float extra = att.has(Attachment.COMPENSATOR) ? 2.6f : 0f;
            pose.pushPose();
            pose.translate((m[0] + extra) / 16f, m[1] / 16f, m[2] / 16f);
            pose.mulPose(Axis.XP.rotationDegrees((GunClient.ticks * 73) % 360));
            float s = t.category == GunType.Category.PISTOL ? 0.7f : t.category == GunType.Category.SNIPER || t.category == GunType.Category.SHOTGUN ? 1.4f : 1.0f;
            pose.scale(s, s, s);
            pose.translate(0, -8 / 16f, -8 / 16f);
            BakedRender.item(stack, "fx/muzzle_flash", pose, buf, LightTexture.FULL_BRIGHT, overlay);
            pose.popPose();
        }
    }

    private static void laser(PoseStack pose, MultiBufferSource buf, float x, float y, float z) {
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float len = 40f;
        float w = 0.012f;
        Vector3f[] quads = {new Vector3f(0, w, 0), new Vector3f(0, 0, w)};
        for (Vector3f o : quads) {
            vc.addVertex(m, x, y - o.y, z - o.z).setColor(1f, 0.1f, 0.05f, 0.55f);
            vc.addVertex(m, x + len, y - o.y, z - o.z).setColor(1f, 0.1f, 0.05f, 0.0f);
            vc.addVertex(m, x + len, y + o.y, z + o.z).setColor(1f, 0.1f, 0.05f, 0.0f);
            vc.addVertex(m, x, y + o.y, z + o.z).setColor(1f, 0.1f, 0.05f, 0.55f);
        }
    }
}
