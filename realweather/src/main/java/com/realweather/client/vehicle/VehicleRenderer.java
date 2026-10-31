package com.realweather.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.realweather.RealWeather;
import com.realweather.client.hail.HailCoverage;
import com.realweather.client.radar.RadarCanvas;
import com.realweather.vehicle.StormVehicle;
import com.realweather.vehicle.VehicleGeometry;
import com.realweather.vehicle.VehicleType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a vehicle from its parts: painted body with its interior, glass, four wheels (spinning, the front ones
 * steering), the steering wheel, pedals and gear lever that follow the driver, gauge needles, wipers sweeping the
 * windshield, snow or sand building up on the glass, hail and snow on the roof and hood, the radar screen on the
 * dash, and the equipment: turning anemometer cups and Doppler dish, the camera turret, spikes and armour skirts.
 */
public class VehicleRenderer extends EntityRenderer<StormVehicle> {
    private static final ResourceLocation SNOW = RealWeather.id("textures/entity/vehicle/windshield_snow.png");
    private static final ResourceLocation SAND = RealWeather.id("textures/entity/vehicle/windshield_sand.png");
    public static final int GRID_X = 10, GRID_Y = 6;

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.6F;
    }

    @Override
    public void render(StormVehicle v, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        VehicleType k = v.kind;
        String base = "vehicle/" + k.id + "/";
        int overlay = LivingEntityRenderer.getOverlayCoords(v, 0F);
        int color = v.getColor();
        float r = ((color >> 16) & 255) / 255F, g = ((color >> 8) & 255) / 255F, b = (color & 255) / 255F;
        pose.pushPose();
        pose.translate(0, Mth.lerp(pt, v.prevLift, v.lift), 0);
        float bodyYaw = Mth.rotLerp(pt, v.yRotO, v.getYRot());
        pose.mulPose(Axis.YP.rotationDegrees(180F - bodyYaw));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, v.prevPitch, v.pitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(pt, v.prevRoll, v.roll)));
        if (v.hurtTime > 0) {
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin((v.hurtTime - pt) * 2F) * 1.2F));
        }
        VehicleModels.solid(base + "body", pose, buf, light, overlay, r, g, b);

        // Wheels.
        float spin = Mth.lerp(pt, v.prevWheelSpin, v.wheelSpin) * Mth.RAD_TO_DEG;
        float steer = v.displaySteer();
        for (int i = 0; i < 4; i++) {
            float[] w = k.wheels[i];
            pose.pushPose();
            pose.translate(w[0], w[1], w[2]);
            if (i < 2) {
                pose.mulPose(Axis.YP.rotationDegrees(-steer * k.maxSteerDeg * 0.9F));
            }
            if (w[0] < 0) {
                pose.mulPose(Axis.YP.rotationDegrees(180F));
                pose.mulPose(Axis.XP.rotationDegrees(spin));
            } else {
                pose.mulPose(Axis.XP.rotationDegrees(-spin));
            }
            VehicleModels.solid(base + "wheel", pose, buf, light, overlay, 1F, 1F, 1F);
            pose.popPose();
        }
        controls(v, k, pose, buf, light, overlay, pt, steer);
        equipment(v, k, base, pose, buf, light, overlay, pt);

        // Glass, the snow and sand on it, and the wipers.
        if (!v.flag(StormVehicle.FLAG_GLASS_BROKEN)) {
            VehicleModels.translucent(base + "glass", pose, buf, light, overlay, 1F);
            windshieldCover(v, k, pose, buf, light);
        }
        wipers(v, k, pose, buf, light, overlay, pt);
        float cover = Math.max(HailCoverage.get(v.getId()), v.bodySnow);
        if (cover > 0.02F) {
            VehicleModels.translucent(base + "cover", pose, buf, light, overlay, Math.min(1F, cover * 1.2F));
        }
        pose.popPose();
        super.render(v, yaw, pt, pose, buf, light);
    }

    private static void controls(StormVehicle v, VehicleType k, PoseStack pose, MultiBufferSource buf, int light, int overlay, float pt, float steer) {
        float[] s = steerPoint(k);
        pose.pushPose();
        pose.translate(s[0], s[1], s[2]);
        pose.mulPose(Axis.XP.rotationDegrees(-22F));
        pose.mulPose(Axis.ZP.rotationDegrees(-steer * 260F));
        VehicleModels.solid("vehicle/parts/steering", pose, buf, light, overlay, 1F, 1F, 1F);
        pose.popPose();
        float[][] pedals = pedals(k);
        float gas = Math.max(0F, v.displayThrottle());
        float brake = v.displayBrake() ? 1F : 0F;
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(pedals[i][0], pedals[i][1], pedals[i][2]);
            pose.mulPose(Axis.XP.rotationDegrees((i == 0 ? gas : brake) * 18F));
            VehicleModels.solid(i == 0 ? "vehicle/parts/pedal" : "vehicle/parts/pedal_wide", pose, buf, light, overlay, 1F, 1F, 1F);
            pose.popPose();
        }
        float[] gp = gearPoint(k);
        float lever = switch (v.gear()) {
            case 'P' -> -14F;
            case 'R' -> -6F;
            case 'N' -> 2F;
            default -> 10F;
        };
        pose.pushPose();
        pose.translate(gp[0], gp[1], gp[2]);
        pose.mulPose(Axis.XP.rotationDegrees(lever));
        VehicleModels.solid("vehicle/parts/gear", pose, buf, light, overlay, 1F, 1F, 1F);
        pose.popPose();
        // Gauge needles.
        float kmh = (float) Math.abs(v.displaySpeed()) * 72F;
        needle(pose, buf, speedo(k), 210F - 240F * Math.min(200F, kmh) / 200F, 0xFFFF5A1E);
        needle(pose, buf, tacho(k), 210F - 240F * Math.min(8000F, v.rpm) / 8000F, 0xFFFF5A1E);
        // The radar on the dash of the chase vehicles.
        float[][] sc = screen(k);
        if (sc != null && sc[0][1] != 0F) {
            RadarCanvas canvas = VehicleClient.screen(v);
            if (canvas != null) {
                Matrix4f m = pose.last().pose();
                VertexConsumer vc = buf.getBuffer(RenderType.text(canvas.id));
                int fb = 0xF000F0;
                float[][] uv = {{0F, 1F}, {1F, 1F}, {1F, 0F}, {0F, 0F}};
                for (int i = 0; i < 4; i++) {
                    vc.addVertex(m, sc[i][0], sc[i][1], sc[i][2] + 0.004F).setColor(255, 255, 255, 255).setUv(uv[i][0], uv[i][1]).setLight(fb);
                }
            }
        }
    }

    private static void needle(PoseStack pose, MultiBufferSource buf, float[] g, float angleDeg, int argb) {
        if (g == null) {
            return;
        }
        float a = angleDeg * Mth.DEG_TO_RAD;
        float dx = Mth.cos(a), dy = Mth.sin(a);
        float len = g[3] * 0.82F;
        float w = g[3] * 0.045F;
        float nx = -dy * w, ny = dx * w;
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());
        float z = g[2] + 0.003F;
        int cr = (argb >> 16) & 255, cg = (argb >> 8) & 255, cb = argb & 255;
        vc.addVertex(m, g[0] - nx - dx * w * 2, g[1] - ny - dy * w * 2, z).setColor(cr, cg, cb, 255);
        vc.addVertex(m, g[0] + nx - dx * w * 2, g[1] + ny - dy * w * 2, z).setColor(cr, cg, cb, 255);
        vc.addVertex(m, g[0] + dx * len, g[1] + dy * len, z).setColor(cr, cg, cb, 255);
        vc.addVertex(m, g[0] + dx * len, g[1] + dy * len, z).setColor(cr, cg, cb, 255);
        // Hub.
        float h = g[3] * 0.09F;
        vc.addVertex(m, g[0] - h, g[1] - h, z + 0.001F).setColor(30, 30, 34, 255);
        vc.addVertex(m, g[0] + h, g[1] - h, z + 0.001F).setColor(30, 30, 34, 255);
        vc.addVertex(m, g[0] + h, g[1] + h, z + 0.001F).setColor(30, 30, 34, 255);
        vc.addVertex(m, g[0] - h, g[1] + h, z + 0.001F).setColor(30, 30, 34, 255);
    }

    private static void equipment(StormVehicle v, VehicleType k, String base, PoseStack pose, MultiBufferSource buf, int light, int overlay, float pt) {
        float[] an = switch (k) {
            case PICKUP -> VehicleGeometry.PICKUP_ANEMOMETER;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_ANEMOMETER;
            default -> null;
        };
        if (an != null) {
            pose.pushPose();
            pose.translate(an[0], an[1], an[2]);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, v.prevAnemometer, v.anemometer)));
            VehicleModels.solid(base + "anemometer", pose, buf, light, overlay, 1F, 1F, 1F);
            pose.popPose();
        }
        if (k == VehicleType.PICKUP) {
            float[] d = VehicleGeometry.PICKUP_DISH;
            pose.pushPose();
            pose.translate(d[0], d[1], d[2]);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, v.prevDishSpin, v.dishSpin)));
            VehicleModels.solid(base + "dish", pose, buf, light, overlay, 1F, 1F, 1F);
            pose.popPose();
        }
        if (k == VehicleType.INTERCEPT) {
            float[] t = VehicleGeometry.INTERCEPT_TURRET;
            pose.pushPose();
            pose.translate(t[0], t[1], t[2]);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, v.prevTurretYaw, v.turretYaw)));
            VehicleModels.solid(base + "turret", pose, buf, light, overlay, 1F, 1F, 1F);
            pose.popPose();
            float deploy = Mth.lerp(pt, v.prevSpikeDeploy, v.spikeDeploy);
            float ease = deploy * deploy * (3F - 2F * deploy);
            for (float[] s : VehicleGeometry.INTERCEPT_SPIKES) {
                pose.pushPose();
                pose.translate(s[0], s[1] - ease * 0.62F, s[2]);
                VehicleModels.solid(base + "spike", pose, buf, light, overlay, 1F, 1F, 1F);
                pose.popPose();
            }
            for (float[] s : VehicleGeometry.INTERCEPT_SKIRTS) {
                pose.pushPose();
                pose.translate(s[0], s[1] + (1F - ease) * 0.38F, s[2]);
                VehicleModels.solid(base + "skirt", pose, buf, light, overlay, 1F, 1F, 1F);
                pose.popPose();
            }
        }
    }

    // ------------------------------------------------------------------------------------ windshield
    /** Orthonormal frame of the windshield: across (u), up the glass (v), out of the glass (n). */
    static Vector3f[] frame(float[][] ws) {
        Vector3f u = new Vector3f(ws[1][0] - ws[0][0], ws[1][1] - ws[0][1], ws[1][2] - ws[0][2]).normalize();
        Vector3f up = new Vector3f(ws[3][0] - ws[0][0], ws[3][1] - ws[0][1], ws[3][2] - ws[0][2]);
        up.sub(new Vector3f(u).mul(up.dot(u))).normalize();
        Vector3f out = new Vector3f(up).cross(u).normalize();
        return new Vector3f[]{u, up, out};
    }

    private static void wipers(StormVehicle v, VehicleType k, PoseStack pose, MultiBufferSource buf, int light, int overlay, float pt) {
        float[][] ws = windshield(k);
        Vector3f[] f = frame(ws);
        Vector3f u = f[0], up = f[1], out = f[2];
        // Part space: X along the arm, Y up the glass... we map Y to -up so Z (the part's outside) points out of the glass.
        Matrix3f basis = new Matrix3f(u.x, u.y, u.z, -up.x, -up.y, -up.z, out.x, out.y, out.z);
        Quaternionf q = new Quaternionf().setFromNormalized(basis);
        float angle = VehicleClient.wiperAngle(v, pt);
        float len = wiperLength(k);
        for (float[] p : wipers(k)) {
            pose.pushPose();
            pose.translate(p[0] + out.x * 0.02F, p[1] + out.y * 0.02F, p[2] + out.z * 0.02F);
            pose.mulPose(q);
            pose.mulPose(Axis.ZP.rotationDegrees(-angle));
            pose.scale(len, 1F, 1F);
            VehicleModels.solid("vehicle/parts/wiper", pose, buf, light, overlay, 1F, 1F, 1F);
            pose.popPose();
        }
    }

    private static void windshieldCover(StormVehicle v, VehicleType k, PoseStack pose, MultiBufferSource buf, int light) {
        if (v.glassSnow == null) {
            return;
        }
        float[][] ws = windshield(k);
        Vector3f out = frame(ws)[2];
        layer(pose, buf.getBuffer(RenderType.entityTranslucent(SNOW)), ws, out, v.glassSnow, light, 0.008F);
        layer(pose, buf.getBuffer(RenderType.entityTranslucent(SAND)), ws, out, v.glassSand, light, 0.01F);
    }

    private static void layer(PoseStack pose, VertexConsumer vc, float[][] ws, Vector3f out, float[] cells, int light, float lift) {
        PoseStack.Pose p = pose.last();
        for (int j = 0; j < GRID_Y; j++) {
            for (int i = 0; i < GRID_X; i++) {
                float a = cells[j * GRID_X + i];
                if (a < 0.02F) {
                    continue;
                }
                int alpha = Math.round(Math.min(1F, a * 1.1F) * 255);
                float u0 = (float) i / GRID_X, u1 = (float) (i + 1) / GRID_X;
                float v0 = (float) j / GRID_Y, v1 = (float) (j + 1) / GRID_Y;
                float[][] c = {point(ws, u0, v0), point(ws, u1, v0), point(ws, u1, v1), point(ws, u0, v1)};
                float[][] t = {{u0, 1 - v0}, {u1, 1 - v0}, {u1, 1 - v1}, {u0, 1 - v1}};
                for (int n = 0; n < 4; n++) {
                    vc.addVertex(p, c[n][0] + out.x * lift, c[n][1] + out.y * lift, c[n][2] + out.z * lift).setColor(255, 255, 255, alpha)
                            .setUv(t[n][0], t[n][1]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(p, out.x, out.y, out.z);
                }
            }
        }
    }

    /** A point on the windshield: u across (0 left .. 1 right), v up (0 bottom .. 1 top). */
    static float[] point(float[][] ws, float u, float v) {
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            float bottom = ws[0][i] + (ws[1][i] - ws[0][i]) * u;
            float top = ws[3][i] + (ws[2][i] - ws[3][i]) * u;
            out[i] = bottom + (top - bottom) * v;
        }
        return out;
    }

    // ------------------------------------------------------------------------------------ geometry by type
    static float[][] windshield(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_WINDSHIELD;
            case PICKUP -> VehicleGeometry.PICKUP_WINDSHIELD;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_WINDSHIELD;
        };
    }

    static float[][] wipers(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_WIPERS;
            case PICKUP -> VehicleGeometry.PICKUP_WIPERS;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_WIPERS;
        };
    }

    static float wiperLength(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_WIPER_LEN;
            case PICKUP -> VehicleGeometry.PICKUP_WIPER_LEN;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_WIPER_LEN;
        };
    }

    static float[] steerPoint(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_STEER;
            case PICKUP -> VehicleGeometry.PICKUP_STEER;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_STEER;
        };
    }

    static float[][] pedals(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_PEDALS;
            case PICKUP -> VehicleGeometry.PICKUP_PEDALS;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_PEDALS;
        };
    }

    static float[] gearPoint(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_GEAR;
            case PICKUP -> VehicleGeometry.PICKUP_GEAR;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_GEAR;
        };
    }

    static float[] speedo(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_SPEEDO;
            case PICKUP -> VehicleGeometry.PICKUP_SPEEDO;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_SPEEDO;
        };
    }

    static float[] tacho(VehicleType k) {
        return switch (k) {
            case SEDAN -> VehicleGeometry.SEDAN_TACHO;
            case PICKUP -> VehicleGeometry.PICKUP_TACHO;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_TACHO;
        };
    }

    static float[][] screen(VehicleType k) {
        return switch (k) {
            case SEDAN -> null;
            case PICKUP -> VehicleGeometry.PICKUP_SCREEN;
            case INTERCEPT -> VehicleGeometry.INTERCEPT_SCREEN;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(StormVehicle v) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(StormVehicle v, Frustum frustum, double x, double y, double z) {
        return v.shouldRender(x, y, z) && frustum.isVisible(v.getBoundingBox().inflate(3.0));
    }
}
