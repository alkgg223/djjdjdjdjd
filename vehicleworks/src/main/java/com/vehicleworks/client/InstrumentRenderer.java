package com.vehicleworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vehicleworks.VehicleWorks;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.vehicle.VehicleBuilder;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleRegistry;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Live cockpit instruments drawn on the faces of the instrument-panel blocks (A: 6 small slots,
 * B: 2 small + 1 large, C: 1 large). Analog gauges are mechanical (work without power, lit by
 * the panel backlight); digital displays need avionics power. All values come from telemetry.
 */
public final class InstrumentRenderer {
    private InstrumentRenderer() {}

    private static final int FULL = 0xF000F0;
    private static final float Z = 0.0015f;

    private static final class Turn { float lastHdg = Float.NaN; long last; float rate; float compass = Float.NaN; }
    private static final Map<VehicleEntity, Turn> TURN = new WeakHashMap<>();

    private static TextureAtlasSprite sprite(String n) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.fromNamespaceAndPath(VehicleWorks.MODID, "block/" + n));
    }

    /** slot layout: {cx, cy, size, small?1:0} in face units (block = 1, centre 0) */
    static float[][] layout(ComponentType t) {
        return switch (t) {
            case INSTRUMENT_PANEL_A -> new float[][]{{-0.32f, 0.16f, 0.3f, 1}, {0, 0.16f, 0.3f, 1}, {0.32f, 0.16f, 0.3f, 1},
                    {-0.32f, -0.16f, 0.3f, 1}, {0, -0.16f, 0.3f, 1}, {0.32f, -0.16f, 0.3f, 1}};
            case INSTRUMENT_PANEL_B -> new float[][]{{-0.33f, 0.17f, 0.3f, 1}, {-0.33f, -0.17f, 0.3f, 1}, {0.16f, 0, 0.64f, 0}};
            default -> new float[][]{{0, 0, 0.92f, 0}};
        };
    }

    static float faceRot(Direction f) {
        return switch (f) { case SOUTH -> 0; case EAST -> 90; case NORTH -> 180; default -> 270; };
    }

    public static void render(VehicleEntity v, PoseStack ps, MultiBufferSource buffers, int light, double camDist) {
        if (camDist > 28) return;
        VehicleTelemetry t = v.telemetry;
        Turn tr = TURN.computeIfAbsent(v, k -> new Turn());
        long now = System.currentTimeMillis();
        if (Float.isNaN(tr.lastHdg)) { tr.lastHdg = t.heading; tr.last = now; tr.compass = t.heading; }
        if (now - tr.last >= 100) {
            float d = wrap(t.heading - tr.lastHdg);
            tr.rate += ((d / ((now - tr.last) / 1000f)) - tr.rate) * 0.3f;
            tr.lastHdg = t.heading;
            tr.last = now;
        }
        tr.compass += wrap(t.heading - tr.compass) * 0.08f;
        // analog gauges stay readable by daylight; their backlight follows the bus, the panel light switch and the dimmer
        float bl = Avionics.backlight(v) * Avionics.dimmer(t) * Math.min(1, t.panelLight / 1.5f);
        int lit = net.minecraft.client.renderer.LightTexture.pack(Math.max(net.minecraft.client.renderer.LightTexture.block(light), Math.min(15, Math.round(15 * bl))),
                net.minecraft.client.renderer.LightTexture.sky(light));
        for (long k : v.structure.blockEntities().keySet()) {
            BlockState st = v.structure.get(k);
            ComponentType ct = VehicleBuilder.typeOf(st);
            if (ct == null || !ct.isInstrumentPanel()) continue;
            CompoundTag cfg = VehicleBuilder.config(v.structure, k);
            Direction f = VehicleBuilder.facing(st);
            ps.pushPose();
            ps.translate(BlockPos.getX(k) + 0.5 + f.getStepX() * 0.501, BlockPos.getY(k) + 0.5, BlockPos.getZ(k) + 0.5 + f.getStepZ() * 0.501);
            ps.mulPose(Axis.YP.rotationDegrees(faceRot(f)));
            float[][] slots = layout(ct);
            for (int i = 0; i < slots.length && i < ct.properties.size(); i++) {
                int sel = ct.properties.get(i).getInt(cfg);
                if (sel <= 0) continue;
                boolean small = slots[i][3] > 0;
                String id = small ? ComponentType.Instruments.SMALL[Math.min(sel, ComponentType.Instruments.SMALL.length - 1)]
                        : ComponentType.Instruments.LARGE[Math.min(sel, ComponentType.Instruments.LARGE.length - 1)];
                ps.pushPose();
                ps.translate(slots[i][0], slots[i][1], 0);
                float h = slots[i][2] / 2;
                ps.scale(h, h, 1);
                if (small) analog(id, v, t, tr, ps, buffers, lit);
                else digital(id, v, t, tr, ps, buffers, light);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------ drawing helpers (slot units: half-size 1)

    static void tex(PoseStack ps, MultiBufferSource buf, String name, float r, float z, float rotDeg, int light) {
        TextureAtlasSprite s = sprite(name);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        ps.pushPose();
        if (rotDeg != 0) ps.mulPose(Axis.ZP.rotationDegrees(rotDeg));
        PoseStack.Pose p = ps.last();
        vc.addVertex(p, -r, -r, z).setColor(255, 255, 255, 255).setUv(s.getU0(), s.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
        vc.addVertex(p, r, -r, z).setColor(255, 255, 255, 255).setUv(s.getU1(), s.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
        vc.addVertex(p, r, r, z).setColor(255, 255, 255, 255).setUv(s.getU1(), s.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
        vc.addVertex(p, -r, r, z).setColor(255, 255, 255, 255).setUv(s.getU0(), s.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
        ps.popPose();
    }

    /** display brightness (additive drawing: scales the emitted light) and top-down data reveal line */
    static float BRIGHT = 1, REVEAL_Y = -10;

    static void rect(PoseStack ps, MultiBufferSource buf, float x0, float y0, float x1, float y1, float z, int argb) {
        if ((y0 + y1) * 0.5f < REVEAL_Y) return;
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        float a = (argb >>> 24) / 255f, r = ((argb >> 16) & 255) / 255f * BRIGHT, g = ((argb >> 8) & 255) / 255f * BRIGHT, b = (argb & 255) / 255f * BRIGHT;
        vc.addVertex(m, x0, y0, z).setColor(r, g, b, a);
        vc.addVertex(m, x1, y0, z).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z).setColor(r, g, b, a);
        vc.addVertex(m, x0, y1, z).setColor(r, g, b, a);
    }

    static void poly(PoseStack ps, MultiBufferSource buf, float[][] pts, int n, float z, int argb) {
        if (n < 3) return;
        if (REVEAL_Y > -10) {
            float cy = 0;
            for (int i = 0; i < n; i++) cy += pts[i][1];
            if (cy / n < REVEAL_Y) return;
        }
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        float a = (argb >>> 24) / 255f, r = ((argb >> 16) & 255) / 255f * BRIGHT, g = ((argb >> 8) & 255) / 255f * BRIGHT, b = (argb & 255) / 255f * BRIGHT;
        for (int i = 1; i < n - 1; i++) {
            vc.addVertex(m, pts[0][0], pts[0][1], z).setColor(r, g, b, a);
            vc.addVertex(m, pts[i][0], pts[i][1], z).setColor(r, g, b, a);
            vc.addVertex(m, pts[i + 1][0], pts[i + 1][1], z).setColor(r, g, b, a);
            vc.addVertex(m, pts[i + 1][0], pts[i + 1][1], z).setColor(r, g, b, a);
        }
    }

    /** a line segment of given width (slot units) */
    static void line(PoseStack ps, MultiBufferSource buf, float x0, float y0, float x1, float y1, float w, float z, int argb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-5) return;
        float nx = -dy / l * w / 2, ny = dx / l * w / 2;
        poly(ps, buf, new float[][]{{x0 + nx, y0 + ny}, {x1 + nx, y1 + ny}, {x1 - nx, y1 - ny}, {x0 - nx, y0 - ny}}, 4, z, argb);
    }

    /** needle from the centre; angle in degrees clockwise from 12 o'clock */
    static void needle(PoseStack ps, MultiBufferSource buf, float angleDeg, float len, float w, int argb) {
        double a = Math.toRadians(angleDeg);
        float x = (float) Math.sin(a) * len, y = (float) Math.cos(a) * len;
        line(ps, buf, -x * 0.15f, -y * 0.15f, x, y, w, Z * 3, argb);
        rect(ps, buf, -0.06f, -0.06f, 0.06f, 0.06f, Z * 3.2f, 0xFF404448);
    }

    static void text(PoseStack ps, MultiBufferSource buf, String s, float x, float y, float size, int color, boolean center) {
        if (y < REVEAL_Y) return;
        if (BRIGHT < 0.999f) {
            float k = Math.max(0, Math.min(1, BRIGHT));
            color = (color & 0xFF000000) | ((int) (((color >> 16) & 255) * k) << 16) | ((int) (((color >> 8) & 255) * k) << 8) | (int) ((color & 255) * k);
            if (k < 0.04f) return;
        }
        Font f = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(x, y, Z * 4);
        float k = size / 8f;
        ps.scale(k, -k, k);
        f.drawInBatch(s, center ? -f.width(s) / 2f : 0, -4, color, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, FULL);
        ps.popPose();
    }

    /** Sky/ground attitude card clipped to the square [-e, e]². */
    static void horizon(PoseStack ps, MultiBufferSource buf, float pitchDeg, float bankDeg, float pxPerDeg, float e, float z) {
        double b = Math.toRadians(-bankDeg);
        float nx = (float) -Math.sin(b), ny = (float) Math.cos(b); // normal of the horizon line (toward sky)
        float o = -pitchDeg * pxPerDeg;                              // horizon offset along the normal
        float[][] sq = {{-e, -e}, {e, -e}, {e, e}, {-e, e}};
        float[][] sky = clip(sq, nx, ny, o, true), ground = clip(sq, nx, ny, o, false);
        poly(ps, buf, sky, count, z, 0xFF2F6FC0);
        float[][] gr = ground; int gc = clipCount;
        poly(ps, buf, gr, gc, z, 0xFF7A4E26);
        // horizon line + pitch ladder
        float tx = ny, ty = -nx; // along the line
        for (int d = -30; d <= 30; d += 10) {
            float off = o + d * pxPerDeg;
            float len = d == 0 ? e * 2 : 0.25f;
            float cx = nx * off, cy = ny * off;
            if (Math.abs(cx) > e * 0.9f || Math.abs(cy) > e * 0.9f) continue;
            line(ps, buf, cx - tx * len, cy - ty * len, cx + tx * len, cy + ty * len, d == 0 ? 0.03f : 0.02f, z + Z, 0xFFFFFFFF);
        }
    }

    private static int count, clipCount;

    /** clip convex polygon against the half plane n·p >= o (keep=true) or <= o (keep=false) */
    private static float[][] clip(float[][] in, float nx, float ny, float o, boolean above) {
        float[][] out = new float[8][];
        int n = 0;
        for (int i = 0; i < in.length; i++) {
            float[] a = in[i], b = in[(i + 1) % in.length];
            float da = (a[0] * nx + a[1] * ny - o) * (above ? 1 : -1), db = (b[0] * nx + b[1] * ny - o) * (above ? 1 : -1);
            if (da >= 0) out[n++] = a;
            if ((da >= 0) != (db >= 0)) {
                float tt = da / (da - db);
                out[n++] = new float[]{a[0] + (b[0] - a[0]) * tt, a[1] + (b[1] - a[1]) * tt};
            }
        }
        if (above) count = n; else clipCount = n;
        return out;
    }

    static float wrap(float a) {
        while (a > 180) a -= 360;
        while (a < -180) a += 360;
        return a;
    }

    static float maxSpool(VehicleTelemetry t) {
        float m = t.rpmFraction;
        for (float s : t.engineSpool) m = Math.max(m, s);
        return m;
    }

    // ------------------------------------------------------------------ analog gauges (mechanical)

    static void analog(String id, VehicleEntity v, VehicleTelemetry t, Turn tr, PoseStack ps, MultiBufferSource buf, int light) {
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        switch (id) {
            case "altimeter" -> {
                tex(ps, buf, "inst_altimeter", 0.95f, Z, 0, light);
                float alt = Math.max(0, t.altitude);
                needle(ps, buf, (alt % 1000) / 1000f * 360, 0.78f, 0.06f, 0xFFFFFFFF);
                needle(ps, buf, (alt % 10000) / 10000f * 360, 0.5f, 0.11f, 0xFFDDDDDD);
                if (VoiceCallouts.active.contains(Warnings.W.PULL_UP) || VoiceCallouts.active.contains(Warnings.W.TERRAIN)) {
                    if (blink && ClientEvents.ridden() == v) text(ps, buf, "LOW", 0, -0.45f, 0.28f, 0xFFFF3030, true);
                }
            }
            case "airspeed" -> {
                tex(ps, buf, "inst_airspeed", 0.95f, Z, 0, light);
                float kmh = t.airspeed * 3.6f;
                needle(ps, buf, Math.min(300, kmh * 0.3f), 0.8f, 0.07f, 0xFFFFFFFF);
                if ((t.stall || (t.vne > 0 && t.airspeed > t.vne)) && blink) text(ps, buf, t.stall ? "STALL" : "VNE", 0, 0.32f, 0.22f, 0xFFFF3030, true);
            }
            case "vsi" -> {
                tex(ps, buf, "inst_vsi", 0.95f, Z, 0, light);
                needle(ps, buf, -90 + Math.max(-30, Math.min(30, t.verticalSpeed)) / 30f * 160, 0.8f, 0.07f, 0xFFFFFFFF);
            }
            case "horizon" -> {
                rect(ps, buf, -0.95f, -0.95f, 0.95f, 0.95f, Z * 0.5f, 0xFF000000);
                horizon(ps, buf, t.pitch, t.bank, 0.022f, 0.72f, Z);
                tex(ps, buf, "inst_bezel", 1.0f, Z * 2.5f, 0, light);
                line(ps, buf, -0.45f, 0, -0.15f, 0, 0.07f, Z * 3, 0xFFFF9020);
                line(ps, buf, 0.15f, 0, 0.45f, 0, 0.07f, Z * 3, 0xFFFF9020);
                rect(ps, buf, -0.04f, -0.04f, 0.04f, 0.04f, Z * 3, 0xFFFF9020);
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(t.bank));
                poly(ps, buf, new float[][]{{0, 0.7f}, {0.07f, 0.82f}, {-0.07f, 0.82f}}, 3, Z * 3, 0xFFFFFFFF);
                ps.popPose();
                return;
            }
            case "heading" -> {
                tex(ps, buf, "inst_hdg_card", 0.95f, Z, -t.heading, light);
                if (t.autopilot) {
                    ps.pushPose();
                    ps.mulPose(Axis.ZP.rotationDegrees(-(t.apHeading - t.heading)));
                    rect(ps, buf, -0.07f, 0.78f, 0.07f, 0.92f, Z * 2, 0xFF38D3FF);
                    ps.popPose();
                }
                poly(ps, buf, new float[][]{{0, 0.25f}, {0.18f, -0.2f}, {-0.18f, -0.2f}}, 3, Z * 2.6f, 0xFFFF9020);
                rect(ps, buf, -0.02f, 0.7f, 0.02f, 0.95f, Z * 2.6f, 0xFFFF9020);
            }
            case "compass" -> {
                tex(ps, buf, "inst_compass_card", 0.95f, Z, -tr.compass + (float) Math.sin(System.currentTimeMillis() / 700.0) * Math.min(4, Math.abs(t.gLoad - 1) * 6), light);
                rect(ps, buf, -0.025f, 0.55f, 0.025f, 0.98f, Z * 2.6f, 0xFFFFFFFF);
            }
            case "turn" -> {
                tex(ps, buf, "inst_turn", 0.95f, Z, 0, light);
                float tilt = Math.max(-35, Math.min(35, tr.rate / 3f * 20));
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(-tilt));
                line(ps, buf, -0.6f, 0.05f, 0.6f, 0.05f, 0.08f, Z * 3, 0xFFFFFFFF);
                rect(ps, buf, -0.08f, -0.05f, 0.08f, 0.2f, Z * 3, 0xFFFFFFFF);
                ps.popPose();
                float ball = Math.max(-0.38f, Math.min(0.38f, -t.sideslip / 10f * 0.38f));
                rect(ps, buf, ball - 0.08f, -0.58f, ball + 0.08f, -0.42f, Z * 3, 0xFF101010);
                rect(ps, buf, ball - 0.065f, -0.565f, ball + 0.065f, -0.435f, Z * 3.1f, 0xFFE8E8E8);
            }
            case "tachometer" -> {
                tex(ps, buf, "inst_tachometer", 0.95f, Z, 0, light);
                needle(ps, buf, Math.min(275, maxSpool(t) * 250), 0.8f, 0.07f, 0xFFFFFFFF);
            }
            case "fuel_gauge" -> {
                tex(ps, buf, "inst_fuel_gauge", 0.95f, Z, 0, light);
                float f = t.fuelCapacity > 0 ? t.fuel / t.fuelCapacity : 0;
                needle(ps, buf, -60 + 120 * f, 0.8f, 0.07f, f < 0.1f && blink ? 0xFFFF3030 : 0xFFFFFFFF);
            }
            case "engine_pressure" -> {
                tex(ps, buf, "inst_engine_pressure", 0.95f, Z, 0, light);
                float p = 0;
                for (int i = 0; i < t.engineState.length; i++) {
                    float dmg = i < t.engineDamage.length ? t.engineDamage[i] : 0;
                    float sp = t.engineSpool[i];
                    if (t.engineState[i] == 2 || t.engineState[i] == 1) p = Math.max(p, (25 + 55 * Math.min(1, sp * 1.2f)) * (1 - 0.7f * dmg));
                }
                needle(ps, buf, -120 + 240 * Math.min(1, p / 100f), 0.8f, 0.07f, 0xFFFFFFFF);
            }
            default -> {}
        }
        if (!id.equals("horizon")) tex(ps, buf, "inst_bezel", 1.0f, Z * 5, 0, light);
    }

    // ------------------------------------------------------------------ digital displays (need avionics power)

    static void digital(String id, VehicleEntity v, VehicleTelemetry t, Turn tr, PoseStack ps, MultiBufferSource buf, int light) {
        tex(ps, buf, "inst_screen_bezel", 1.0f, Z * 6, 0, light);
        float env = Avionics.display(v) * Avionics.displayBrightness(t);
        int stage = Avionics.stage(v);
        boolean fading = Avionics.fadingOut(v);
        if (env <= 0.004f || (stage == Avionics.OFF && !fading)) return; // dark glass: no power
        try {
            BRIGHT = env;
            rect(ps, buf, -0.95f, -0.95f, 0.95f, 0.95f, Z * 0.3f, 0xFF04070E);
            if (!fading && stage < Avionics.DATA) {
                bootScreen(id, stage, Avionics.progress(v), ps, buf);
                return;
            }
            // data appears from the top down during the last part of the boot
            if (!fading && stage == Avionics.DATA) REVEAL_Y = 1.0f - 2.1f * Avionics.reveal(v);
            digitalContent(id, v, t, tr, ps, buf, light);
        } finally {
            BRIGHT = 1;
            REVEAL_Y = -10;
        }
    }

    static String displayName(String id) {
        return switch (id) {
            case "nav_display" -> "ND";
            case "radar" -> "RADAR";
            case "flight_director" -> "FD";
            case "attitude_display" -> "PFD";
            case "engine_display" -> "EICAS";
            default -> "DU";
        };
    }

    /** glow -> logo -> self test, like a real display unit booting */
    static void bootScreen(String id, int stage, float f, PoseStack ps, MultiBufferSource buf) {
        final int W = 0xFFFFFFFF, C = 0xFF38D3FF, G = 0xFF40FF70;
        if (stage == Avionics.GLOW) {
            float k = (f - 0.18f) / 0.12f;
            rect(ps, buf, -0.95f, -0.95f, 0.95f, 0.95f, Z * 0.5f, ((int) (0x30 * k) << 16) | ((int) (0x38 * k) << 8) | (int) (0x48 * k) | 0xFF000000);
            return;
        }
        if (stage == Avionics.LOGO) {
            rect(ps, buf, -0.95f, -0.95f, 0.95f, 0.95f, Z * 0.5f, 0xFF0A1020);
            poly(ps, buf, new float[][]{{-0.18f, 0.32f}, {0, 0.02f}, {0.18f, 0.32f}, {0.1f, 0.32f}, {0, 0.15f}, {-0.1f, 0.32f}}, 6, Z, C);
            text(ps, buf, "VEHICLEWORKS", 0, -0.12f, 0.15f, W, true);
            text(ps, buf, "AVIONICS  " + displayName(id), 0, -0.3f, 0.1f, C, true);
            text(ps, buf, "v1.2", 0, -0.75f, 0.08f, 0xFF8090A0, true);
            return;
        }
        // self test: colour bars, all segments, progress
        float k = Math.max(0, Math.min(1, (f - 0.48f) / 0.2f));
        int[] bars = {0xFFFF3030, 0xFFFFB020, 0xFF40FF70, 0xFF38D3FF, 0xFFFF55FF, 0xFFFFFFFF};
        for (int i = 0; i < bars.length; i++) {
            float x0 = -0.85f + i * 0.283f;
            rect(ps, buf, x0, 0.05f, x0 + 0.26f, 0.7f, Z, bars[i]);
        }
        text(ps, buf, "SELF TEST", 0, -0.12f, 0.13f, W, true);
        rect(ps, buf, -0.7f, -0.42f, 0.7f, -0.32f, Z, 0xFF1A2030);
        rect(ps, buf, -0.7f, -0.42f, -0.7f + 1.4f * k, -0.32f, Z * 1.5f, G);
        text(ps, buf, k < 1 ? String.format("%.0f %%", k * 100) : "PASS", 0, -0.62f, 0.1f, k < 1 ? C : G, true);
    }

    static void digitalContent(String id, VehicleEntity v, VehicleTelemetry t, Turn tr, PoseStack ps, MultiBufferSource buf, int light) {
        final int G = 0xFF40FF70, W = 0xFFFFFFFF, C = 0xFF38D3FF, M = 0xFFFF55FF, A = 0xFFFFB020, R = 0xFFFF4040;
        switch (id) {
            case "nav_display" -> {
                float cy = -0.55f, r = 1.15f;
                for (int d = 0; d < 360; d += 10) {
                    float rel = wrap(d - t.heading);
                    if (Math.abs(rel) > 60) continue;
                    double a = Math.toRadians(rel);
                    float x = (float) Math.sin(a) * r, y = cy + (float) Math.cos(a) * r;
                    float l = d % 30 == 0 ? 0.12f : 0.06f;
                    line(ps, buf, x, y, x * (1 - l / r), cy + (y - cy) * (1 - l / r), 0.015f, Z, W);
                    if (d % 30 == 0) text(ps, buf, String.valueOf(d / 10), x * 0.86f, cy + (y - cy) * 0.86f - 0.02f, 0.12f, W, true);
                }
                poly(ps, buf, new float[][]{{0, cy + 0.12f}, {0.07f, cy - 0.08f}, {-0.07f, cy - 0.08f}}, 3, Z, A);
                if (t.navActive) {
                    double a = Math.toRadians(wrap(t.navBearing - t.heading));
                    line(ps, buf, 0, cy, (float) Math.sin(a) * 1.0f, cy + (float) Math.cos(a) * 1.0f, 0.02f, Z, M);
                    text(ps, buf, String.format("WPT %.1fkm", t.navDistance / 1000f), 0.9f, -0.85f, 0.11f, M, false);
                }
                if (t.autopilot) {
                    double a = Math.toRadians(wrap(t.apHeading - t.heading));
                    if (Math.abs(Math.toDegrees(a)) < 60) rect(ps, buf, (float) Math.sin(a) * r - 0.04f, cy + (float) Math.cos(a) * r - 0.04f, (float) Math.sin(a) * r + 0.04f, cy + (float) Math.cos(a) * r + 0.04f, Z * 1.5f, C);
                }
                text(ps, buf, String.format("GS %.0f  TAS %.0f", t.groundSpeed * 3.6f, t.airspeed * 3.6f), -0.88f, 0.82f, 0.11f, G, false);
                text(ps, buf, String.format("HDG %03.0f", t.heading), 0, 0.68f, 0.13f, W, true);
            }
            case "radar" -> {
                for (float rr = 0.3f; rr <= 0.91f; rr += 0.3f)
                    for (int d = 0; d < 360; d += 10) {
                        double a0 = Math.toRadians(d), a1 = Math.toRadians(d + 10);
                        line(ps, buf, (float) Math.sin(a0) * rr, (float) Math.cos(a0) * rr, (float) Math.sin(a1) * rr, (float) Math.cos(a1) * rr, 0.01f, Z, 0xFF1C5A2A);
                    }
                double sweep = (System.currentTimeMillis() / 1000.0 * 120) % 360;
                for (int k = 0; k < 6; k++) {
                    double a = Math.toRadians(sweep - k * 4);
                    line(ps, buf, 0, 0, (float) Math.sin(a) * 0.9f, (float) Math.cos(a) * 0.9f, 0.02f, Z * 1.2f, ((0xC0 >> k) << 24) | 0x40FF70);
                }
                float range = 800;
                var level = Minecraft.getInstance().level;
                if (level != null) for (VehicleEntity o : VehicleRegistry.all(level)) {
                    if (o == v) continue;
                    double dx = o.getX() - v.getX(), dz = o.getZ() - v.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > range) continue;
                    double brg = Math.toDegrees(Math.atan2(dx, -dz));
                    double a = Math.toRadians(wrap((float) (brg - t.heading)));
                    float x = (float) (Math.sin(a) * dist / range * 0.9), y = (float) (Math.cos(a) * dist / range * 0.9);
                    int col = Math.abs(o.getY() - v.getY()) < 30 ? A : G;
                    rect(ps, buf, x - 0.035f, y - 0.035f, x + 0.035f, y + 0.035f, Z * 2, col);
                }
                poly(ps, buf, new float[][]{{0, 0.06f}, {0.04f, -0.04f}, {-0.04f, -0.04f}}, 3, Z * 2, W);
                text(ps, buf, String.format("RNG %.0f", range), -0.88f, 0.85f, 0.11f, G, false);
            }
            case "flight_director", "attitude_display" -> {
                boolean pfd = id.equals("attitude_display");
                ps.pushPose();
                if (pfd) ps.translate(0, 0.08f, 0);
                horizon(ps, buf, t.pitch, t.bank, 0.025f, pfd ? 0.55f : 0.9f, Z);
                // aircraft reference
                line(ps, buf, -0.4f, 0, -0.12f, 0, 0.05f, Z * 2, A);
                line(ps, buf, 0.12f, 0, 0.4f, 0, 0.05f, Z * 2, A);
                // flight director command bars (toward the autopilot targets, or level flight)
                float cmdPitch = t.autopilot ? Math.max(-10, Math.min(12, (t.apAltitude - t.altitude) * 0.08f)) : 0;
                float cmdBank = t.autopilot ? Math.max(-25, Math.min(25, wrap(t.apHeading - t.heading) * 1.5f)) : 0;
                float vb = Math.max(-0.5f, Math.min(0.5f, (cmdBank - t.bank) * 0.015f));
                float hb = Math.max(-0.5f, Math.min(0.5f, (cmdPitch - t.pitch) * 0.025f));
                line(ps, buf, vb, -0.4f, vb, 0.4f, 0.03f, Z * 2.5f, M);
                line(ps, buf, -0.4f, hb, 0.4f, hb, 0.03f, Z * 2.5f, M);
                ps.popPose();
                if (pfd) {
                    text(ps, buf, String.format("%.0f", t.airspeed * 3.6f), -0.8f, 0.08f, 0.15f, t.stall ? R : W, true);
                    text(ps, buf, String.format("%.0f", t.altitude), 0.8f, 0.08f, 0.15f, W, true);
                    text(ps, buf, String.format("%+.1f", t.verticalSpeed), 0.8f, -0.12f, 0.1f, G, true);
                    text(ps, buf, String.format("HDG %03.0f", t.heading), 0, -0.68f, 0.13f, W, true);
                    text(ps, buf, t.autopilot ? String.format("AP %03.0f/%.0f", t.apHeading, t.apAltitude) : "FD", 0, 0.82f, 0.11f, t.autopilot ? C : G, true);
                } else text(ps, buf, t.autopilot ? "FD  HDG  ALT" : "FD  ATT", 0, 0.82f, 0.11f, G, true);
            }
            case "engine_display" -> {
                int n = Math.min(4, t.engineSpool.length);
                for (int i = 0; i < n; i++) {
                    float x = -0.75f + i * (1.5f / Math.max(1, n - 1 == 0 ? 1 : n - 1)) * (n == 1 ? 0 : 1);
                    if (n == 1) x = -0.5f;
                    float sp = t.engineSpool[i];
                    rect(ps, buf, x - 0.09f, -0.35f, x + 0.09f, 0.45f, Z, 0xFF102018);
                    int col = t.engineState[i] == 4 ? R : (t.engineState[i] == 1 ? A : G);
                    rect(ps, buf, x - 0.07f, -0.33f, x + 0.07f, -0.33f + 0.76f * Math.min(1.1f, sp), Z * 1.5f, col);
                    text(ps, buf, String.format("%.0f", sp * 100), x, 0.55f, 0.11f, W, true);
                    text(ps, buf, String.format("%.0fk", (i < t.engineThrust.length ? t.engineThrust[i] : 0) / 1000f), x, -0.45f, 0.09f, G, true);
                    text(ps, buf, String.format("%.0f°", i < t.engineTemp.length ? t.engineTemp[i] : 0), x, -0.58f, 0.09f, A, true);
                }
                text(ps, buf, "N1 %", 0, 0.75f, 0.1f, C, true);
                text(ps, buf, String.format("FUEL %.0f  FF %.2f", t.fuel, t.fuelFlow), 0, -0.75f, 0.1f, t.fuel < t.fuelCapacity * 0.1f ? R : W, true);
                text(ps, buf, (t.gearDown ? "GEAR DN" : "GEAR UP") + String.format(" FLP %.0f", t.flaps * 100), 0, -0.88f, 0.09f, t.gearDown ? G : W, true);
                if (ClientEvents.ridden() == v) {
                    int k2 = 0;
                    for (Warnings.W w : VoiceCallouts.active) {
                        if (k2 >= 2) break;
                        text(ps, buf, net.minecraft.network.chat.Component.translatable(w.key()).getString(), 0, 0.9f - 0.0f - k2 * 0.12f - 0.0f, 0.09f, w.red ? R : A, true);
                        k2++;
                    }
                }
            }
            default -> {}
        }
    }
}
