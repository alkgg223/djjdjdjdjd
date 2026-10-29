package com.eggsteal.client;

import com.eggsteal.EggSteal;
import com.eggsteal.EventMarkerEntity;
import com.eggsteal.Mutation;
import com.eggsteal.RandomEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the running random event in the sky around the camera, like a sky box: a glowing spiral galaxy,
 * a giant double rainbow, falling green code, raining gold coins, lava meteors, warp-speed streaks and lightning,
 * northern lights with falling clovers, or a huge moon. Everything is additive glow (black = invisible) and fades in and out.
 */
public class EventSkyRenderer extends EntityRenderer<EventMarkerEntity> {
    static ResourceLocation tex(String n) { return ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "textures/effect/" + n + ".png"); }

    static final ResourceLocation GLOW = tex("glow"), STAR = tex("star"), COIN = tex("coin"), CLOVER = tex("clover"), CODE = tex("code"), MOON = tex("moon");
    /** Solid (not glowing) sprites with a transparent background: they keep their colours even on a bright day sky. */
    static final ResourceLocation COIN_SOLID = tex("coin_solid"), CLOVER_SOLID = tex("clover_solid");
    static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(EggSteal.MODID, "textures/entity/white.png");
    static final int FULL_BRIGHT = 0xF000F0;
    static final int[] RAINBOW = {0xff2020, 0xff8c00, 0xfff21a, 0x33e633, 0x33b3ff, 0x4050ff, 0xb040ff};

    public EventSkyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(EventMarkerEntity e) { return GLOW; }

    /** Drawn wherever the camera is, so it must never be culled. */
    @Override
    public boolean shouldRender(EventMarkerEntity e, Frustum f, double x, double y, double z) {
        return e.fade > 0.001f || e.fadeO > 0.001f;
    }

    /** Deterministic pseudo random 0..1 for particle i (and a salt), so every frame shows the same sky. */
    static float h(int i, int salt) {
        long x = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        x ^= x >>> 31; x *= 0xBF58476D1CE4E5B9L; x ^= x >>> 29;
        return (x >>> 40) / (float) (1L << 24);
    }

    /** Quads of one texture, collected until {@link Fx#flush()} (see there). */
    static final class Batch {
        final ResourceLocation tex;
        final boolean solid;
        float[] d = new float[9 * 64];
        int n;

        Batch(ResourceLocation tex, boolean solid) { this.tex = tex; this.solid = solid; }

        void add(float x, float y, float z, float u, float v, int r, int g, int b, int a) {
            if (n + 9 > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
            d[n++] = x; d[n++] = y; d[n++] = z; d[n++] = u; d[n++] = v; d[n++] = r; d[n++] = g; d[n++] = b; d[n++] = a;
        }
    }

    /** Helpers for camera-facing sprites and free quads, all relative to the camera position. */
    static final class Fx {
        final PoseStack.Pose pose;
        final MultiBufferSource buf;
        final Vector3f right, up;
        final float fade, cosY, sinY;

        /** The sky scene is turned so that its "front" (-z) always points from the camera toward the middle of the arena. */
        Fx(PoseStack.Pose pose, MultiBufferSource buf, Quaternionf camRot, float fade, Vec3 cam) {
            this.pose = pose;
            this.buf = buf;
            this.right = new Vector3f(1, 0, 0).rotate(camRot);
            this.up = new Vector3f(0, 1, 0).rotate(camRot);
            this.fade = fade;
            float dx = (float) -cam.x, dz = (float) -cam.z, len = Mth.sqrt(dx * dx + dz * dz);
            if (len < 6f) { dx = 0f; dz = -1f; } else { dx /= len; dz /= len; }
            cosY = -dz;
            sinY = -dx;
        }

        /** A point of the sky scene, turned toward the arena. */
        Vector3f s(float x, float y, float z) { return new Vector3f(x * cosY + z * sinY, y, -x * sinY + z * cosY); }

        Vector3f s(Vector3f v) { return s(v.x, v.y, v.z); }

        /** Glowing sprites: additive, so brightness is the strength. */
        /**
         * Quads are collected per texture and written in one go by {@link #flush()}: the game's buffer source closes a buffer as soon as
         * another render type is requested, so writing to two of them in turn would crash.
         */
        final java.util.Map<String, Batch> batches = new java.util.LinkedHashMap<>();

        /** Glowing sprites: additive, so brightness is the strength. */
        Batch vc(ResourceLocation t) { return batches.computeIfAbsent("g" + t, k -> new Batch(t, false)); }

        /** Solid sprites: normal transparency, the strength fades them out. */
        Batch solidVc(ResourceLocation t) { return batches.computeIfAbsent("s" + t, k -> new Batch(t, true)); }

        /** Writes every collected quad, one render type after the other. */
        void flush() {
            for (Batch b : batches.values()) {
                if (b.n == 0) continue;
                VertexConsumer vc = buf.getBuffer(b.solid ? RenderType.entityTranslucent(b.tex) : RenderType.eyes(b.tex));
                for (int i = 0; i < b.n; i += 9) {
                    vc.addVertex(pose, b.d[i], b.d[i + 1], b.d[i + 2])
                            .setColor((int) b.d[i + 5], (int) b.d[i + 6], (int) b.d[i + 7], (int) b.d[i + 8])
                            .setUv(b.d[i + 3], b.d[i + 4])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(FULL_BRIGHT)
                            .setNormal(pose, 0f, 1f, 0f);
                }
            }
            batches.clear();
        }

        void v(Batch vc, float x, float y, float z, float u, float w, int rgb, float k) {
            k = Mth.clamp(k * fade, 0f, 1f);
            float c = vc.solid ? 1f : k;
            vc.add(x, y, z, u, w, (int) (((rgb >> 16) & 255) * c), (int) (((rgb >> 8) & 255) * c), (int) ((rgb & 255) * c), vc.solid ? (int) (255 * k) : 255);
        }

        /** A quad from four corners, drawn from both sides; each corner may have its own colour strength. */
        void quad(Batch vc, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u0, float v0, float u1, float v1, int rgb, float ka, float kb, float kc, float kd) {
            v(vc, a.x, a.y, a.z, u0, v0, rgb, ka); v(vc, b.x, b.y, b.z, u1, v0, rgb, kb); v(vc, c.x, c.y, c.z, u1, v1, rgb, kc); v(vc, d.x, d.y, d.z, u0, v1, rgb, kd);
            v(vc, d.x, d.y, d.z, u0, v1, rgb, kd); v(vc, c.x, c.y, c.z, u1, v1, rgb, kc); v(vc, b.x, b.y, b.z, u1, v0, rgb, kb); v(vc, a.x, a.y, a.z, u0, v0, rgb, ka);
        }

        /** A camera-facing square sprite (optionally spun by angle, squashed horizontally by sx, and using a part of the texture). */
        void sprite(Batch vc, float x, float y, float z, float size, int rgb, float k, float angle, float sx, float u0, float v0, float u1, float v1) {
            float c = Mth.cos(angle), s = Mth.sin(angle);
            Vector3f r = new Vector3f(right).mul(c).add(new Vector3f(up).mul(s)).mul(size * sx);
            Vector3f u = new Vector3f(up).mul(c).sub(new Vector3f(right).mul(s)).mul(size);
            Vector3f p = new Vector3f(x, y, z);
            quad(vc, new Vector3f(p).sub(r).sub(u), new Vector3f(p).add(r).sub(u), new Vector3f(p).add(r).add(u), new Vector3f(p).sub(r).add(u), u0, v1, u1, v0, rgb, k, k, k, k);
        }

        void sprite(Batch vc, float x, float y, float z, float size, int rgb, float k) { sprite(vc, x, y, z, size, rgb, k, 0f, 1f, 0f, 0f, 1f, 1f); }

        void sprite(Batch vc, Vector3f p, float size, int rgb, float k) { sprite(vc, p.x, p.y, p.z, size, rgb, k); }

        /** A glowing line from a to b, width w, facing the camera as well as possible. */
        void beam(Batch vc, Vector3f a, Vector3f b, float w, int rgb, float ka, float kb) {
            Vector3f dir = new Vector3f(b).sub(a);
            Vector3f side = new Vector3f(dir).cross(new Vector3f(right).cross(up)); // perpendicular to the line and the view direction
            if (side.lengthSquared() < 1e-6f) side.set(right);
            side.normalize().mul(w * 0.5f);
            quad(vc, new Vector3f(a).sub(side), new Vector3f(a).add(side), new Vector3f(b).add(side), new Vector3f(b).sub(side), 0f, 0.5f, 1f, 0.5f, rgb, ka, ka, kb, kb);
        }
    }

    static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public void render(EventMarkerEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float fade = Mth.lerp(pt, e.fadeO, e.fade);
        if (fade <= 0.001f || e.shown < 0 || e.shown >= RandomEvents.Kind.values().length) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition(), ep = e.getPosition(pt);
        float t = e.level().getGameTime() + pt;
        ps.pushPose();
        ps.translate(cp.x - ep.x, cp.y - ep.y, cp.z - ep.z); // from here on (0,0,0) is the camera
        Fx fx = new Fx(ps.last(), buf, cam.rotation(), fade, cp);
        switch (RandomEvents.Kind.values()[e.shown]) {
            case GALAXY -> galaxy(fx, t);
            case RAINBOW -> rainbow(fx, t);
            case SOFTWARE -> software(fx, t);
            case MONEY_RAIN -> money(fx, t);
            case LAVA -> lava(fx, t);
            case SPEED -> speed(fx, t, cam.getLookVector());
            case LUCKY -> lucky(fx, t);
            case MOON -> moon(fx, t);
        }
        fx.flush();
        ps.popPose();
    }

    // ------------------------------------------------------------------ shared pieces

    /** A dome of twinkling stars. */
    static void stars(Fx fx, float t, int n, float k) {
        Batch vc = fx.vc(STAR);
        for (int i = 0; i < n; i++) {
            float az = h(i, 1) * Mth.TWO_PI, el = 0.15f + 1.35f * h(i, 2);
            float x = Mth.cos(el) * Mth.cos(az) * 46, y = Mth.sin(el) * 46, z = Mth.cos(el) * Mth.sin(az) * 46;
            float tw = 0.45f + 0.55f * Mth.sin(t * (0.04f + 0.08f * h(i, 3)) + h(i, 4) * 40f);
            int col = h(i, 5) < 0.3f ? 0xbfd4ff : h(i, 5) < 0.45f ? 0xffe2b0 : 0xffffff;
            fx.sprite(vc, x, y, z, 0.28f + 0.4f * h(i, 6), col, k * tw);
        }
    }

    /** Small glowing motes drifting around the camera (rising when speed > 0, falling when speed < 0). */
    static void motes(Fx fx, float t, int n, ResourceLocation texture, float minR, float maxR, float height, float speed, float size, int color, float k, boolean hueCycle) {
        Batch vc = fx.vc(texture);
        for (int i = 0; i < n; i++) {
            float a = h(i, 11) * Mth.TWO_PI, r = minR + (maxR - minR) * h(i, 12);
            float sp = speed * (0.6f + 0.8f * h(i, 13));
            float y = ((h(i, 14) * height + t * sp) % height + height) % height - height * 0.35f;
            float x = Mth.cos(a) * r + Mth.sin(t * 0.03f + h(i, 15) * 20f) * 0.6f, z = Mth.sin(a) * r + Mth.cos(t * 0.027f + h(i, 16) * 20f) * 0.6f;
            float edge = Math.min(1f, Math.min((y + height * 0.35f) / 2f, (height * 0.65f - y) / 2f)); // fade in/out at the ends
            int col = hueCycle ? Mutation.hsv(h(i, 17) + t * 0.004f, 0.75f, 1f) : color;
            fx.sprite(vc, x, y, z, size * (0.7f + 0.6f * h(i, 18)), col, k * Math.max(0f, edge) * (0.6f + 0.4f * Mth.sin(t * 0.2f + i)));
        }
    }

    // ------------------------------------------------------------------ the eight events

    /** A huge glowing spiral galaxy turning slowly overhead, nebula clouds, shooting stars and a starry dome. */
    static void galaxy(Fx fx, float t) {
        stars(fx, t, 260, 0.9f);
        Vector3f c = fx.s(6, 14, -28);
        Vector3f a = fx.s(new Vector3f(1f, 0.12f, 0.3f).normalize());
        Vector3f b = fx.s(-0.25f, 0.6f, 0.75f);
        b.sub(new Vector3f(a).mul(a.dot(b))).normalize();
        Batch glow = fx.vc(GLOW), star = fx.vc(STAR);
        float spin = t * 0.0035f;
        // bright core
        fx.sprite(glow, c, 26f, 0x5a3cff, 0.35f);
        fx.sprite(glow, c, 15f, 0xff7ae0, 0.5f);
        fx.sprite(glow, c, 7f, 0xfff0e0, 0.95f);
        fx.sprite(star, c, 4.5f, 0xffffff, 0.9f);
        // four spiral arms of stars
        for (int arm = 0; arm < 4; arm++) {
            for (int k = 0; k < 120; k++) {
                int i = arm * 1000 + k;
                float r = 1.5f + k * 0.19f;
                float th = arm * Mth.HALF_PI + r * 0.3f + spin + (h(i, 1) - 0.5f) * 0.45f;
                float rr = r + (h(i, 2) - 0.5f) * r * 0.18f;
                Vector3f p = new Vector3f(c).add(new Vector3f(a).mul(Mth.cos(th) * rr)).add(new Vector3f(b).mul(Mth.sin(th) * rr))
                        .add(0, (h(i, 3) - 0.5f) * 1.2f, 0);
                float f = r / 24f;
                int col = f < 0.35f ? lerpColor(0xfff0ff, 0xff7ad8, f / 0.35f) : lerpColor(0xff7ad8, 0x5a8cff, (f - 0.35f) / 0.65f);
                float tw = 0.6f + 0.4f * Mth.sin(t * 0.06f + h(i, 4) * 30f);
                fx.sprite(h(i, 5) < 0.2f ? star : glow, p.x, p.y, p.z, 0.45f + 1.4f * h(i, 6) * (1.1f - f), col, (0.55f + 0.45f * h(i, 7)) * tw);
                if (k % 6 == 0) fx.sprite(glow, p, 4f + 4f * h(i, 8), h(i, 9) < 0.5f ? 0x8a3cff : 0x3cc8ff, 0.1f); // nebula clouds
            }
        }
        // shooting stars
        for (int s = 0; s < 3; s++) {
            float ph = ((t + s * 37f) % 110f) / 110f;
            if (ph > 0.3f) continue;
            int cycle = (int) ((t + s * 37f) / 110f) * 7 + s;
            Vector3f start = fx.s(-40 + 80 * h(cycle, 1), 40 + 8 * h(cycle, 2), -45 + 30 * h(cycle, 3));
            Vector3f dir = fx.s(new Vector3f(0.8f, -0.45f, 0.25f * (h(cycle, 4) - 0.5f)).normalize());
            Vector3f head = new Vector3f(start).add(new Vector3f(dir).mul(ph * 200f));
            fx.beam(glow, head, new Vector3f(head).sub(new Vector3f(dir).mul(12f)), 0.6f, 0xc8e0ff, 0.9f, 0f);
            fx.sprite(star, head, 1.4f, 0xffffff, 1f);
        }
        motes(fx, t, 40, STAR, 3f, 10f, 9f, 0.02f, 0.12f, 0xc8b4ff, 0.8f, false);
    }

    /** A giant shimmering double rainbow with sparkles running along it, and rainbow motes around the player. */
    static void rainbow(Fx fx, float t) {
        Batch glow = fx.vc(GLOW), star = fx.vc(STAR);
        arc(fx, glow, t, 36f, 2f, false, 0.62f);
        arc(fx, glow, t, 46f, 1.5f, true, 0.25f);
        for (int i = 0; i < 70; i++) {
            float a = ((h(i, 1) + t * 0.0015f * (0.5f + h(i, 2))) % 1f) * Mth.PI;
            float r = 36f - h(i, 3) * 7 * 2f;
            float tw = Mth.sin(t * 0.15f + i);
            if (tw > 0) fx.sprite(star, fx.s(Mth.cos(a) * r, -8 + Mth.sin(a) * r, -38), 0.5f + 0.5f * h(i, 4), 0xffffff, tw);
        }
        fx.sprite(glow, fx.s(0, 22, -38), 32f, 0xffffff, 0.08f); // soft light inside the arch
        motes(fx, t, 60, GLOW, 2.5f, 9f, 8f, 0.02f, 0.18f, 0, 0.9f, true);
    }

    static void arc(Fx fx, Batch vc, float t, float radius, float band, boolean reversed, float k) {
        int segs = 56;
        for (int bnd = 0; bnd < RAINBOW.length; bnd++) {
            int col = RAINBOW[reversed ? RAINBOW.length - 1 - bnd : bnd];
            float r0 = radius - bnd * band + band * 0.45f, r1 = r0 - band * 1.9f;
            for (int sgm = 0; sgm < segs; sgm++) {
                float a0 = Mth.PI * sgm / segs, a1 = Mth.PI * (sgm + 1) / segs;
                float sh0 = k * (0.8f + 0.2f * Mth.sin(t * 0.05f + sgm * 0.35f + bnd)), sh1 = k * (0.8f + 0.2f * Mth.sin(t * 0.05f + (sgm + 1) * 0.35f + bnd));
                float end0 = Math.min(1f, Math.min(sgm, segs - sgm) / 6f), end1 = Math.min(1f, Math.min(sgm + 1, segs - sgm - 1) / 6f); // fade into the horizon
                fx.quad(vc, fx.s(Mth.cos(a0) * r0, -8 + Mth.sin(a0) * r0, -38), fx.s(Mth.cos(a0) * r1, -8 + Mth.sin(a0) * r1, -38),
                        fx.s(Mth.cos(a1) * r1, -8 + Mth.sin(a1) * r1, -38), fx.s(Mth.cos(a1) * r0, -8 + Mth.sin(a1) * r0, -38),
                        0f, 0.5f, 1f, 0.5f, col, sh0 * end0, sh0 * end0, sh1 * end1, sh1 * end1);
            }
        }
    }

    /** "Hacked" arena: rain of glowing green code around the player, a scrolling digital grid in the sky and glitch flashes. */
    static void software(Fx fx, float t) {
        Batch code = fx.vc(CODE), glow = fx.vc(GLOW), white = fx.vc(WHITE);
        for (int i = 0; i < 80; i++) {
            float a = h(i, 1) * Mth.TWO_PI, r = 4f + 22f * h(i, 2);
            float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
            float sp = 0.18f + 0.35f * h(i, 3);
            float head = 22f - ((t * sp + h(i, 4) * 400f) % 46f);
            for (int k = 0; k < 14; k++) {
                float y = head + k * 0.95f;
                int g = (int) (h(i * 31 + k, 5) * 16 + t * 0.08f * (k == 0 ? 4 : 1)) & 15;
                float u0 = (g % 4) / 4f, v0 = (g / 4) / 4f;
                int col = k == 0 ? 0xd8ffe0 : 0x30ff70;
                fx.sprite(code, x, y, z, 0.42f, col, (k == 0 ? 1f : 0.85f * (1f - k / 14f)), 0f, 1f, u0, v0, u0 + 0.25f, v0 + 0.25f);
            }
        }
        float off = (t * 0.08f) % 5f;
        for (int k = -8; k <= 8; k++) { // the grid lines fade out towards the edges
            float p = k * 5f + off, q = k * 5f;
            fx.beam(glow, new Vector3f(-42, 24, p), new Vector3f(0, 24, p), 0.3f, 0x20ff80, 0.03f, 0.35f);
            fx.beam(glow, new Vector3f(0, 24, p), new Vector3f(42, 24, p), 0.3f, 0x20ff80, 0.35f, 0.03f);
            fx.beam(glow, new Vector3f(q, 24, -42), new Vector3f(q, 24, 0), 0.3f, 0x20ff80, 0.03f, 0.35f);
            fx.beam(glow, new Vector3f(q, 24, 0), new Vector3f(q, 24, 42), 0.3f, 0x20ff80, 0.35f, 0.03f);
        }
        if (t % 40f < 5f) { // glitch blocks flash in the sky
            int cycle = (int) (t / 40f);
            for (int i = 0; i < 7; i++) {
                float x = -40 + 80 * h(cycle * 7 + i, 1), y = 18 + 20 * h(cycle * 7 + i, 2), z = -40 + 20 * h(cycle * 7 + i, 3);
                float w = 2f + 6f * h(cycle * 7 + i, 4), hh = 0.6f + 2f * h(cycle * 7 + i, 5);
                int col = h(cycle * 7 + i, 6) < 0.5f ? 0x20ff80 : (h(cycle * 7 + i, 6) < 0.75f ? 0xff2bd6 : 0x2bd9ff);
                fx.quad(white, fx.s(x - w, y - hh, z), fx.s(x + w, y - hh, z), fx.s(x + w, y + hh, z), fx.s(x - w, y + hh, z), 0, 0, 1, 1, col, 0.35f, 0.35f, 0.35f, 0.35f);
            }
        }
    }

    /** Spinning gold coins falling all around, a golden sun with turning rays and sparkles. */
    static void money(Fx fx, float t) {
        Batch coin = fx.solidVc(COIN_SOLID);
        for (int i = 0; i < 120; i++) {
            float a = h(i, 1) * Mth.TWO_PI, r = 1.5f + 17f * h(i, 2);
            float sp = 0.1f + 0.15f * h(i, 3);
            float y = 16f - ((t * sp + h(i, 4) * 100f) % 22f);
            float spinW = Math.max(0.12f, Math.abs(Mth.cos(t * 0.15f + h(i, 5) * 10f)));
            float edge = Math.min(1f, (y + 6f) / 2f);
            fx.sprite(coin, Mth.cos(a) * r, y, Mth.sin(a) * r, 0.32f, 0xffffff, Math.max(0f, edge), 0f, spinW, 0f, 0f, 1f, 1f);
        }
        Batch glow = fx.vc(GLOW);
        Vector3f sun = fx.s(0, 36, -38);
        float pulse = 0.85f + 0.15f * Mth.sin(t * 0.08f);
        fx.sprite(glow, sun, 30f, 0xff9a20, 0.25f * pulse);
        fx.sprite(glow, sun, 13f, 0xffd040, 0.75f * pulse);
        fx.sprite(fx.vc(COIN), sun.x, sun.y, sun.z, 5f, 0xfff0b0, 0.9f, t * 0.01f, 1f, 0f, 0f, 1f, 1f);
        for (int k = 0; k < 14; k++) {
            float ang = k * Mth.TWO_PI / 14f + t * 0.006f;
            Vector3f end = new Vector3f(sun).add(new Vector3f(fx.right).mul(Mth.cos(ang) * 24f)).add(new Vector3f(fx.up).mul(Mth.sin(ang) * 24f));
            fx.beam(glow, sun, end, 1.4f, 0xffd060, 0.4f, 0f);
        }
        motes(fx, t, 30, STAR, 2f, 8f, 7f, 0.015f, 0.14f, 0xfff0a0, 0.9f, false);
    }

    /** A burning red sky: a blood-red sun, glowing horizon, meteors with fiery tails and embers rising around the player. */
    static void lava(Fx fx, float t) {
        Batch glow = fx.vc(GLOW), star = fx.vc(STAR);
        for (int k = 0; k < 6; k++) {
            float az = k * Mth.TWO_PI / 6f;
            fx.sprite(glow, Mth.cos(az) * 40, 4, Mth.sin(az) * 40, 28f, 0xff3a00, 0.22f * (0.85f + 0.15f * Mth.sin(t * 0.04f + k)));
        }
        Vector3f sun = fx.s(-12, 26, -40);
        float pulse = 0.8f + 0.2f * Mth.sin(t * 0.1f);
        fx.sprite(glow, sun, 24f, 0xff2000, 0.35f * pulse);
        fx.sprite(glow, sun, 9f, 0xff7020, 0.9f * pulse);
        fx.sprite(glow, sun, 4f, 0xffd080, 0.9f);
        for (int m = 0; m < 4; m++) {
            float ph = ((t + m * 29f) % 95f) / 95f;
            if (ph > 0.55f) continue;
            int cycle = (int) ((t + m * 29f) / 95f) * 5 + m;
            Vector3f start = fx.s(-50 + 100 * h(cycle, 1), 48, -55 + 40 * h(cycle, 2));
            Vector3f dir = fx.s(new Vector3f(0.35f * (h(cycle, 3) - 0.5f), -1f, 0.4f).normalize());
            Vector3f head = new Vector3f(start).add(new Vector3f(dir).mul(ph * 90f));
            fx.beam(glow, head, new Vector3f(head).sub(new Vector3f(dir).mul(14f)), 1.6f, 0xff6010, 0.95f, 0f);
            fx.sprite(glow, head, 2.2f, 0xffd070, 1f);
            fx.sprite(star, head, 1.2f, 0xffffff, 0.8f);
        }
        motes(fx, t, 130, GLOW, 1.5f, 15f, 14f, 0.06f, 0.12f, 0xff7a10, 1f, false);
        motes(fx, t, 30, STAR, 2f, 10f, 10f, 0.08f, 0.1f, 0xffd060, 0.9f, false);
    }

    /** Warp speed: light streaks rush past the player along the view direction, and lightning bolts flash in the sky. */
    static void speed(Fx fx, float t, Vector3f look) {
        Batch glow = fx.vc(GLOW), star = fx.vc(STAR);
        Vector3f l = new Vector3f(look).normalize();
        Vector3f p1 = new Vector3f(l).cross(Math.abs(l.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
        Vector3f p2 = new Vector3f(l).cross(p1).normalize();
        for (int i = 0; i < 80; i++) {
            float a = h(i, 1) * Mth.TWO_PI, rad = 3.5f + 11f * h(i, 2);
            float s = (((h(i, 3) * 44f - t * (0.9f + 0.9f * h(i, 4))) % 44f) + 44f) % 44f - 12f;
            Vector3f p = new Vector3f(l).mul(s).add(new Vector3f(p1).mul(Mth.cos(a) * rad)).add(new Vector3f(p2).mul(Mth.sin(a) * rad));
            Vector3f q = new Vector3f(p).sub(new Vector3f(l).mul(3f + 5f * h(i, 5)));
            float k = 0.75f * Math.min(1f, (s + 12f) / 6f);
            fx.beam(glow, p, q, 0.17f, h(i, 6) < 0.7f ? 0x9ad8ff : 0xffffff, k, 0f);
        }
        float ph = t % 55f;
        if (ph < 7f) { // a lightning bolt with a flash
            int cycle = (int) (t / 55f);
            float x = -35 + 70 * h(cycle, 1), z = -45 + 20 * h(cycle, 2);
            Vector3f prev = fx.s(x, 46, z);
            float k = ph < 2f ? 1f : 1f - (ph - 2f) / 5f;
            fx.sprite(glow, fx.s(x, 30, z), 40f, 0xa0c8ff, 0.25f * k);
            for (int sgm = 1; sgm <= 12; sgm++) {
                Vector3f next = fx.s(x + (h(cycle * 13 + sgm, 3) - 0.5f) * 7f, 46 - sgm * 3.5f, z + (h(cycle * 13 + sgm, 4) - 0.5f) * 4f);
                fx.beam(glow, prev, next, 1.2f, 0xd8ecff, k, k);
                fx.beam(glow, prev, next, 3.5f, 0x5aa0ff, 0.35f * k, 0.35f * k);
                prev = next;
            }
        }
        motes(fx, t, 40, STAR, 1.5f, 6f, 5f, 0.05f, 0.1f, 0x7ae6ff, 1f, false);
    }

    /** Northern lights waving across the sky, four-leaf clovers drifting down and golden sparkles. */
    static void lucky(Fx fx, float t) {
        Batch white = fx.vc(WHITE);
        for (int c = 0; c < 3; c++) {
            float z0 = -42 + c * 7f;
            Vector3f pb = null, pt = null;
            float kb = 0;
            for (int s = 0; s <= 42; s++) {
                float x = -75 + s * 3.6f;
                float zz = z0 + Mth.sin(x * 0.06f + t * 0.02f + c) * 9f;
                float yb = 24 + Mth.sin(x * 0.09f + t * 0.03f + c * 2f) * 3f + c * 3f;
                float hgt = 14 + Mth.sin(x * 0.05f + t * 0.025f + c) * 5f;
                Vector3f b = fx.s(x, yb, zz), top = fx.s(x, yb + hgt, zz);
                float k = 0.42f * (0.7f + 0.3f * Mth.sin(t * 0.07f + s * 0.4f + c)) * Math.min(1f, Math.min(s, 42 - s) / 6f);
                if (pb != null) {
                    int col = c == 1 ? 0x40c8ff : 0x40ff90;
                    fx.quad(white, pb, b, top, pt, 0, 0, 1, 1, col, kb, k, 0f, 0f);
                }
                pb = b; pt = top; kb = k;
            }
        }
        Batch clover = fx.solidVc(CLOVER_SOLID);
        for (int i = 0; i < 70; i++) {
            float a = h(i, 1) * Mth.TWO_PI, r = 1.5f + 13f * h(i, 2);
            float y = 12f - ((t * (0.025f + 0.03f * h(i, 3)) + h(i, 4) * 100f) % 16f);
            float x = Mth.cos(a) * r + Mth.sin(t * 0.04f + i) * 0.8f, z = Mth.sin(a) * r;
            float edge = Math.min(1f, (y + 4f) / 2f);
            fx.sprite(clover, x, y, z, 0.3f, 0xffffff, Math.max(0f, edge), t * 0.03f + h(i, 5) * 6f, 1f, 0f, 0f, 1f, 1f);
        }
        motes(fx, t, 40, STAR, 2f, 10f, 9f, 0.02f, 0.16f, 0xffe070, 1f, false);
        stars(fx, t, 120, 0.5f);
    }

    /** A huge glowing moon with little planets orbiting it, a starry sky and moon dust floating up around the player. */
    static void moon(Fx fx, float t) {
        stars(fx, t, 240, 0.85f);
        Batch glow = fx.vc(GLOW);
        Vector3f m = fx.s(12, 22, -34);
        fx.sprite(glow, m, 42f, 0x3040c0, 0.18f);
        fx.sprite(glow, m, 22f, 0x90a8ff, 0.3f);
        fx.sprite(fx.vc(MOON), m.x, m.y, m.z, 8.5f, 0xf0f2ff, 1f, 0.15f, 1f, 0f, 0f, 1f, 1f);
        int[] pc = {0xff8a5a, 0x5ad8ff, 0xc08aff};
        for (int k = 0; k < 3; k++) {
            float a = t * (0.008f + 0.004f * k) + k * 2.1f;
            Vector3f p = new Vector3f(m).add(fx.s(Mth.cos(a) * (13 + 4 * k), Mth.sin(a) * (3 + k), Mth.sin(a) * (6 + 2 * k)));
            fx.sprite(glow, p, 1.6f + 0.4f * k, pc[k], 0.85f);
            fx.sprite(glow, p, 4f, pc[k], 0.2f);
        }
        motes(fx, t, 80, GLOW, 1.5f, 11f, 12f, 0.012f, 0.1f, 0xe8ecff, 0.9f, false);
    }
}
