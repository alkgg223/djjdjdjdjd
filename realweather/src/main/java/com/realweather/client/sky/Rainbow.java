package com.realweather.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.realweather.client.ClientStorms;
import com.realweather.client.ClientWeather;
import com.realweather.server.SkyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * The rainbow, where it really appears: with the sun low behind you (less than 42 degrees up) shining on rain falling
 * on the other side of the sky. The primary bow sits 42 degrees from the point opposite the sun, red outside and violet
 * inside; the fainter secondary bow at 51 degrees has its colours the other way round; the sky inside the primary is
 * brighter and the band between the two (Alexander's band) darker. It is drawn over the clouds and the rain (it forms
 * in the rain in front of them), hidden by the land in front of it, and is most vivid against dark storm clouds.
 */
public final class Rainbow {
    private static float strength;
    private static final float[][] STOPS = {
            {0F, 0.35F, 0.1F, 0.55F}, {0.15F, 0.25F, 0.25F, 0.95F}, {0.32F, 0.15F, 0.6F, 0.75F}, {0.48F, 0.25F, 0.85F, 0.25F},
            {0.64F, 0.95F, 0.9F, 0.2F}, {0.78F, 1F, 0.55F, 0.1F}, {0.92F, 0.9F, 0.15F, 0.08F}, {1F, 0.6F, 0.05F, 0.05F}};
    private static final float[] COL = new float[3];

    private Rainbow() {}

    static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!ClientWeather.active(level) || event.getCamera().getFluidInCamera() != FogType.NONE) {
            strength = 0F;
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double t = level.getGameTime() + pt;
        float a = level.getTimeOfDay(pt) * Mth.TWO_PI;
        float sx = -Mth.sin(a), sy = Mth.cos(a);
        Vec3 cam = event.getCamera().getPosition();
        float target = target(level, cam, t, sx, sy);
        strength += (target - strength) * 0.012F;
        if (strength < 0.004F) {
            return;
        }
        boolean forced = ClientSkyEvents.get(SkyEvent.RAINBOW, t) != null;
        // The point opposite the sun, and two directions square to it (one level with the horizon).
        float ax = -sx, ay = -sy, az = 0F;
        float ux = -az, uz = ax;
        float ul = Mth.sqrt(ux * ux + uz * uz) + 1.0E-6F;
        ux /= ul;
        uz /= ul;
        float uy = 0F;
        float vx = uy * az - uz * ay, vy = uz * ax - ux * az, vz = ux * ay - uy * ax;
        float dist = Math.min(900F, mc.gameRenderer.getDepthFar() * 0.7F);

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            float s = strength;
            // Alexander's dark band between the bows.
            RenderSystem.defaultBlendFunc();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            band(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, 42.6F, 50.2F, 3, s, 2, forced, level.getGameTime());
            draw(b);
            // The light of the bows ("screen" blending: vivid on dark cloud, softer on a bright sky).
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            band(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, 22F, 40.2F, 6, s, 3, forced, level.getGameTime());
            band(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, 40F, 42.6F, 14, s, 0, forced, level.getGameTime());
            band(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, 50.2F, 53.9F, 12, s, 1, forced, level.getGameTime());
            draw(b);
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** How strong the rainbow should be now (0..1). */
    private static float target(ClientLevel level, Vec3 cam, double t, float sx, float sy) {
        // The sun has to be up, but lower than 42 degrees, or the bow is below the horizon.
        float window = smooth(0F, 0.06F, sy) * smooth(0.69F, 0.55F, sy);
        if (window <= 0F) {
            return 0F;
        }
        if (ClientSkyEvents.get(SkyEvent.RAINBOW, t) != null) {
            return window;
        }
        // Sunshine here (no rain overhead, the sun not hidden by the rain deck)...
        float sunlit = (1F - smooth(0.12F, 0.45F, ClientWeather.camRain)) * (1F - 0.9F * ClientEclipse.dark);
        float rainOpposite;
        if (ClientStorms.synced) {
            sunlit *= 1F - smooth(0.2F, 0.55F, ClientStorms.rainAt(cam.x, cam.z));
            // ...and rain falling in the part of the sky opposite the sun.
            float dirX = sx > 0F ? -1F : 1F;
            rainOpposite = 0F;
            for (float d : new float[]{300F, 700F, 1200F, 2000F, 3000F}) {
                rainOpposite = Math.max(rainOpposite, ClientStorms.rainAt(cam.x + dirX * d, cam.z));
            }
        } else {
            float r = level.getRainLevel(1F);
            sunlit *= 1F - smooth(0.35F, 0.8F, r);
            rainOpposite = r;
        }
        return window * sunlit * smooth(0.12F, 0.45F, rainOpposite);
    }

    /**
     * A ring between radii r0 and r1 (degrees from the point opposite the sun). Kind 0: the primary bow (violet inside,
     * red outside); 1: the secondary (the other way round, fainter); 2: the dark band; 3: the brighter sky inside.
     */
    private static void band(BufferBuilder b, float ax, float ay, float az, float ux, float uy, float uz, float vx, float vy, float vz,
                             float dist, float r0, float r1, int rings, float s, int kind, boolean forced, long time) {
        int segs = 120;
        for (int i = 0; i < segs; i++) {
            float p0 = Mth.TWO_PI * i / segs, p1 = Mth.TWO_PI * (i + 1) / segs;
            // Skip what lies wholly below the horizon.
            if (y(ay, uy, vy, p0, r1) < -0.06F && y(ay, uy, vy, p1, r1) < -0.06F && y(ay, uy, vy, p0, r0) < -0.06F) {
                continue;
            }
            float patch0 = patch(p0, forced, time), patch1 = patch(p1, forced, time);
            for (int j = 0; j < rings; j++) {
                float q0 = Mth.lerp((float) j / rings, r0, r1), q1 = Mth.lerp((float) (j + 1) / rings, r0, r1);
                vertex(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, p0, q0, r0, r1, s * patch0, kind);
                vertex(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, p1, q0, r0, r1, s * patch1, kind);
                vertex(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, p1, q1, r0, r1, s * patch1, kind);
                vertex(b, ax, ay, az, ux, uy, uz, vx, vy, vz, dist, p0, q1, r0, r1, s * patch0, kind);
            }
        }
    }

    /** Natural rainbows are patchy, brighter where the rain is heavier; one asked for by command is whole. */
    private static float patch(float phi, boolean forced, long time) {
        if (forced) {
            return 1F;
        }
        float slow = time * 0.0004F;
        return Mth.clamp(0.55F + 0.3F * Mth.sin(phi * 2.3F + slow) + 0.25F * Mth.sin(phi * 5.1F - slow * 1.7F + 1.3F), 0.15F, 1F);
    }

    private static float y(float ay, float uy, float vy, float phi, float rDeg) {
        float r = rDeg * Mth.DEG_TO_RAD;
        return Mth.cos(r) * ay + Mth.sin(r) * (Mth.cos(phi) * uy + Mth.sin(phi) * vy);
    }

    private static void vertex(BufferBuilder b, float ax, float ay, float az, float ux, float uy, float uz, float vx, float vy, float vz,
                               float dist, float phi, float rDeg, float r0, float r1, float s, int kind) {
        float r = rDeg * Mth.DEG_TO_RAD;
        float c = Mth.cos(r), sn = Mth.sin(r), cp = Mth.cos(phi), sp = Mth.sin(phi);
        float dx = c * ax + sn * (cp * ux + sp * vx), dy = c * ay + sn * (cp * uy + sp * vy), dz = c * az + sn * (cp * uz + sp * vz);
        // It fades out where it meets the horizon.
        float fade = smooth(-0.03F, 0.04F, dy);
        float f = (rDeg - r0) / (r1 - r0);
        float red, green, blue, alpha = 1F;
        switch (kind) {
            case 0 -> {
                spectrum(f);
                float env = smooth(0F, 0.14F, f) * smooth(1F, 0.82F, f) * 0.62F;
                red = COL[0] * env;
                green = COL[1] * env;
                blue = COL[2] * env;
            }
            case 1 -> {
                spectrum(1F - f);
                float env = smooth(0F, 0.2F, f) * smooth(1F, 0.75F, f) * 0.24F;
                red = COL[0] * env;
                green = COL[1] * env;
                blue = COL[2] * env;
            }
            case 2 -> {
                red = green = blue = 0F;
                alpha = 0.07F * smooth(0F, 0.15F, f) * smooth(1F, 0.85F, f);
            }
            default -> {
                float env = f * f * 0.1F;
                red = env * 0.95F;
                green = env * 0.96F;
                blue = env;
            }
        }
        float k = s * fade;
        if (kind == 2) {
            b.addVertex(dx * dist, dy * dist, dz * dist).setColor(0F, 0F, 0F, alpha * k);
        } else {
            b.addVertex(dx * dist, dy * dist, dz * dist).setColor(red * k, green * k, blue * k, 1F);
        }
    }

    private static void spectrum(float f) {
        for (int i = 1; i < STOPS.length; i++) {
            if (f <= STOPS[i][0] || i == STOPS.length - 1) {
                float[] lo = STOPS[i - 1], hi = STOPS[i];
                float k = Mth.clamp((f - lo[0]) / (hi[0] - lo[0]), 0F, 1F);
                COL[0] = Mth.lerp(k, lo[1], hi[1]);
                COL[1] = Mth.lerp(k, lo[2], hi[2]);
                COL[2] = Mth.lerp(k, lo[3], hi[3]);
                return;
            }
        }
    }

    private static void draw(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
