package com.realweather.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.realweather.ModContent;
import com.realweather.RealWeather;
import com.realweather.vehicle.StormVehicle;
import com.realweather.vehicle.VehicleType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Overrun backfire of the muscle coupe and the sport sedan: when the throttle snaps shut at high revs (or on a
 * flat-out upshift) the exhaust pops a few times. Every pop is a crack you hear, a glowing flame out of each
 * tailpipe and a puff of fire and smoke.
 */
public final class Backfire {
    private static final ResourceLocation FLAME = RealWeather.id("textures/entity/vehicle/backfire.png");
    private static final ResourceLocation GLOW = RealWeather.id("textures/entity/vehicle/backfire_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Self-test: keeps the flames lit this many ticks for a screenshot. */
    public static int debugHold;

    private Backfire() {}

    static void tick(StormVehicle v, ClientLevel level, int gear) {
        v.prevFlame = v.flame;
        v.flame *= 0.42F;
        if (debugHold > 0 && v.kind.engine.backfire) {
            debugHold--;
            v.flame = v.prevFlame = 1F;
        }
        if (!v.kind.engine.backfire) {
            return;
        }
        float thr = v.displayThrottle();
        boolean driven = v.getControllingPassenger() != null;
        RandomSource r = v.getRandom();
        if (driven && v.lastThrottleSeen > 0.55F && thr < 0.15F && v.rpm > 2700F) {
            v.backfirePops = 2 + r.nextInt(4);
            v.backfireTimer = 1 + r.nextInt(2);
        } else if (driven && gear > v.lastGear && thr > 0.8F && v.rpm > 2400F && v.backfirePops == 0) {
            v.backfirePops = 1 + r.nextInt(2);
            v.backfireTimer = 1;
        }
        v.lastThrottleSeen = thr;
        v.lastGear = gear;
        if (v.backfirePops > 0 && --v.backfireTimer <= 0) {
            pop(v, level, r);
            v.backfirePops--;
            v.backfireTimer = 2 + r.nextInt(5);
        }
    }

    private static void pop(StormVehicle v, ClientLevel level, RandomSource r) {
        float[][] tips = v.kind.geo.exhausts();
        if (tips == null || tips.length == 0) {
            return;
        }
        v.flame = 0.75F + r.nextFloat() * 0.25F;
        Vec3 sum = Vec3.ZERO;
        for (float[] t : tips) {
            Vec3 at = v.bodyToWorld(t[0], t[1], t[2] + 0.05F);
            Vec3 back = v.bodyToWorld(t[0], t[1], t[2] + 1F).subtract(v.bodyToWorld(t[0], t[1], t[2]));
            sum = sum.add(at);
            for (int i = 0; i < 3; i++) {
                double s = 0.12 + r.nextDouble() * 0.12;
                level.addParticle(ParticleTypes.FLAME, at.x, at.y, at.z, back.x * s + r.nextGaussian() * 0.02, back.y * s + 0.01,
                        back.z * s + r.nextGaussian() * 0.02);
            }
            level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, back.x * 0.3, 0.02, back.z * 0.3);
            level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at.x, at.y, at.z, back.x * 0.08,
                    0.03, back.z * 0.08);
            if (r.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0);
            }
        }
        Vec3 c = sum.scale(1.0 / tips.length);
        level.playLocalSound(c.x, c.y, c.z, ModContent.VEHICLE_BACKFIRE.get(), SoundSource.NEUTRAL, 1.3F + r.nextFloat() * 0.5F,
                0.85F + r.nextFloat() * 0.3F, false);
    }

    /** The flames, in the vehicle's model space (front -Z, so the tailpipes point along +Z). */
    static void render(StormVehicle v, VehicleType k, PoseStack pose, MultiBufferSource buf, float pt) {
        float f = Mth.lerp(pt, v.prevFlame, v.flame);
        float[][] tips = k.geo.exhausts();
        if (f < 0.03F || tips == null) {
            return;
        }
        PoseStack.Pose p = pose.last();
        VertexConsumer flame = buf.getBuffer(RenderType.eyes(FLAME));
        float len = 0.16F + 0.42F * f;
        float rad = 0.045F + 0.07F * f;
        int c = Math.round(255 * Math.min(1F, f * 1.3F));
        for (float[] t : tips) {
            float x = t[0], y = t[1], z = t[2] + 0.005F;
            // Two crossed planes along the pipe, both faces.
            float[][] vertical = {{x, y - rad, z}, {x, y + rad, z}, {x, y + rad * 0.7F, z + len}, {x, y - rad * 0.7F, z + len}};
            float[][] flat = {{x - rad, y, z}, {x + rad, y, z}, {x + rad * 0.7F, y, z + len}, {x - rad * 0.7F, y, z + len}};
            quad(flame, p, vertical, c, 1F, 0F, 0F);
            quad(flame, p, flat, c, 0F, 1F, 0F);
        }
        VertexConsumer glow = buf.getBuffer(RenderType.eyes(GLOW));
        float g = rad * 1.6F;
        for (float[] t : tips) {
            float x = t[0], y = t[1], z = t[2] + 0.03F;
            float[][] disc = {{x - g, y - g, z}, {x + g, y - g, z}, {x + g, y + g, z}, {x - g, y + g, z}};
            quad(glow, p, disc, c, 0F, 0F, 1F);
        }
    }

    /** A quad written both ways round so it shows from either side; u runs along the flame, v across it. */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, float[][] q, int c, float nx, float ny, float nz) {
        float[][] uv = {{0F, 0F}, {0F, 1F}, {1F, 1F}, {1F, 0F}};
        for (int pass = 0; pass < 2; pass++) {
            for (int n = 0; n < 4; n++) {
                int i = pass == 0 ? n : 3 - n;
                vc.addVertex(p, q[i][0], q[i][1], q[i][2]).setColor(c, c, c, 255).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
            }
        }
    }
}
