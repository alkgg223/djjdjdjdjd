package com.trafficcraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trafficcraft.client.ClientEvents;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleSpec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * In-car infotainment screen (only cars that really have one). Off = black; on engine start the maker's badge fades in,
 * then a maker-specific home menu (HOME / MUSIC / MAP / PARK ASSIST). Only PARK ASSIST works: open it with the screen keys,
 * or it opens by itself when something gets close and closes again once the way is clear.
 */
public final class CarScreen {
    static final String[] APPS = {"HOME", "MUSIC", "MAP", "PARK ASSIST"};

    static final class State {
        int onSince = -1, app = 0, sel = 3, toastUntil = 0, lastNear = -1000;
        boolean auto;
    }

    static final Map<Integer, State> STATES = new HashMap<>();

    static State st(VehicleEntity v) {
        return STATES.computeIfAbsent(v.getId(), k -> new State());
    }

    /** screen keys (driver only) */
    public static void next(VehicleEntity v) {
        State s = st(v);
        if (s.app == 0) s.sel = (s.sel + 1) % APPS.length;
    }

    public static void ok(VehicleEntity v) {
        State s = st(v);
        if (s.app != 0) {
            s.app = 0;
            s.auto = false;
            return;
        }
        if (s.sel == 3) {
            s.app = 1;
            s.auto = false;
        } else s.toastUntil = v.tickCount + 40;
    }

    // ------------------------------------------------------------------------------------------------ drawing
    static final float W = 200;
    private static PoseStack.Pose pose;
    private static MultiBufferSource src;
    private static float layer;

    static void rect(float x, float y, float w, float h, int argb) {
        layer += 0.02f;
        float z = layer;
        Matrix4f m = pose.pose();
        VertexConsumer vc = src.getBuffer(RenderType.entityTranslucentEmissive(com.trafficcraft.client.render.VehicleRenderer.ATLAS));
        float u0 = 1 / 256f, v0 = 1 / 256f, u1 = 7 / 256f, v1 = 7 / 256f;      // the white square of the atlas
        float[][] q = {{x, y, u0, v0}, {x, y + h, u0, v1}, {x + w, y + h, u1, v1}, {x + w, y, u1, v0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] p = q[pass == 0 ? i : 3 - i];
                vc.addVertex(m, p[0], p[1], -z).setColor(argb).setUv(p[2], p[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(0xF000F0).setNormal(pose, 0, 0, pass == 0 ? 1 : -1);
            }
        }
    }

    static void disc(float cx, float cy, float r, int argb) {
        for (int i = 0; i < 8; i++) {
            float y0 = -r + 2 * r * i / 8f, y1 = y0 + 2 * r / 8f, ym = (y0 + y1) / 2;
            float hw = (float) Math.sqrt(Math.max(0, r * r - ym * ym));
            rect(cx - hw, cy + y0, hw * 2, y1 - y0 + 0.3f, argb);
        }
    }

    static int argb(String hex, float a) {
        int c = Integer.parseInt(hex.substring(1), 16);
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | c;
    }

    static int mix(int c, float a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (c & 0xFFFFFF);
    }

    public static void render(VehicleEntity v, VehicleSpec s, PoseStack ps, MultiBufferSource buf) {
        if (s.screen == null) return;
        float[] sc = s.screen;
        float w = sc[9], h = sc[10];
        float H = W * h / w;
        State st = st(v);
        boolean on = v.has(VehicleEntity.F_ENGINE) || v.has(VehicleEntity.F_STARTING);
        if (!on) {
            st.onSince = -1;
            st.app = 0;
            return;                                     // black (the screen face itself is black)
        }
        if (st.onSince < 0) st.onSince = v.tickCount;
        int t = v.tickCount - st.onSince;
        // auto park assist when something is near (driver's own car)
        if (ClientEvents.drivenVehicle() == v) {
            if (ParkSensor.anyNear) {
                st.lastNear = v.tickCount;
                if (st.app == 0 && t > 60) {
                    st.app = 1;
                    st.auto = true;
                }
            } else if (st.app == 1 && st.auto && v.tickCount - st.lastNear > 60) {
                st.app = 0;
                st.auto = false;
            }
        }
        ps.pushPose();
        ps.translate(sc[0], sc[1], sc[2]);
        float[] r = {sc[3], sc[4], sc[5]}, u = {sc[6], sc[7], sc[8]};
        float[] n = {r[1] * u[2] - r[2] * u[1], r[2] * u[0] - r[0] * u[2], r[0] * u[1] - r[1] * u[0]};
        ps.last().pose().mul(new Matrix4f(r[0], r[1], r[2], 0, u[0], u[1], u[2], 0, n[0], n[1], n[2], 0, 0, 0, 0, 1));
        float k = w / W;
        ps.scale(k, -k, k);
        ps.translate(-W / 2, -H / 2, 0);
        pose = ps.last();
        src = buf;
        layer = 0;
        Font font = Minecraft.getInstance().font;
        int bg = argb(s.uiBg, 1), acc = argb(s.uiAccent, 1), fg = argb(s.uiFg, 1);
        rect(0, 0, W, H, 0xFF000000);
        if (t < 60) {
            // boot: the badge fades in, scales up slightly, then fades out
            float a = t < 20 ? t / 20f : t < 42 ? 1 : 1 - (t - 42) / 18f;
            float sz = 18 + 6 * Mth.clamp(t / 30f, 0, 1);
            int col = argb(s.brandColor, a);
            float cx = W / 2, cy = H / 2 - 6;
            switch (s.brandShape) {
                case "circle", "oval" -> {
                    disc(cx, cy, sz + 2, mix(0xFFD0D4DA, a));
                    disc(cx, cy, sz - 1, col);
                }
                case "diamond" -> {
                    for (int i = 0; i < 10; i++) {
                        float hw = sz * (1 - Math.abs(i - 4.5f) / 5f);
                        rect(cx - hw, cy - sz + i * sz / 5, hw * 2, sz / 5 + 0.3f, col);
                    }
                }
                case "shield" -> {
                    rect(cx - sz, cy - sz, sz * 2, sz * 1.2f, mix(0xFFD0D4DA, a));
                    rect(cx - sz + 2, cy - sz + 2, sz * 2 - 4, sz * 1.2f - 4, col);
                    for (int i = 0; i < 6; i++) rect(cx - sz * (1 - i / 6f) + 2, cy + sz * 0.2f + i * sz / 7, 2 * sz * (1 - i / 6f) - 4, sz / 7 + 0.3f, col);
                }
                default -> {
                    rect(cx - sz * 1.3f, cy - sz * 0.8f, sz * 2.6f, sz * 1.6f, mix(0xFFD0D4DA, a));
                    rect(cx - sz * 1.3f + 2, cy - sz * 0.8f + 2, sz * 2.6f - 4, sz * 1.6f - 4, col);
                }
            }
            layer += 0.05f;
            text(font, buf, s.brandText, cx, cy - 4, mix(0xFFFFFFFF, a), 1.6f, true);
            text(font, buf, s.brandName, cx, cy + sz + 6, mix(0xFFE0E4EA, a * 0.9f), 1f, true);
            // progress bar
            rect(W / 2 - 30, H - 10, 60, 2, mix(0xFF303438, a));
            rect(W / 2 - 30, H - 10, 60 * Mth.clamp(t / 55f, 0, 1), 2, mix(acc, a));
        } else if (st.app == 1) {
            park(font, buf, v, s, bg, acc, fg, H);
        } else {
            menu(font, buf, v, s, st, bg, acc, fg, H);
        }
        ps.popPose();
    }

    static void text(Font font, MultiBufferSource buf, String str, float x, float y, int col, float scale, boolean centre) {
        PoseStack p = new PoseStack();
        p.last().pose().set(pose.pose());
        p.translate(x, y, -layer - 0.05f);
        p.scale(scale, scale, 1);
        float dx = centre ? -font.width(str) / 2f : 0;
        font.drawInBatch(str, dx, 0, col, false, p.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
    }

    static void menu(Font font, MultiBufferSource buf, VehicleEntity v, VehicleSpec s, State st, int bg, int acc, int fg, float H) {
        rect(0, 0, W, H, bg);
        // status bar
        long time = (Minecraft.getInstance().level.getDayTime() + 6000) % 24000;
        String clock = String.format("%02d:%02d", time / 1000, (time % 1000) * 60 / 1000);
        rect(0, 0, W, 12, mix(acc, 0.25f));
        text(font, buf, s.brandName, 4, 2, fg, 0.8f, false);
        text(font, buf, clock, W - 26, 2, fg, 0.8f, false);
        switch (s.uiLayout) {
            case "list" -> {           // vertical list with a red selection bar (Nissan)
                for (int i = 0; i < 4; i++) {
                    float y = 16 + i * (H - 22) / 4;
                    boolean sel = st.sel == i;
                    rect(6, y, W - 12, (H - 22) / 4 - 3, sel ? acc : mix(0xFF2A2E34, 0.9f));
                    text(font, buf, APPS[i], 14, y + 4, sel ? 0xFFFFFFFF : fg, 1f, false);
                }
            }
            case "dock" -> {           // big map panel + bottom dock (Tesla)
                rect(4, 15, W - 8, H - 40, mix(0xFFC9D6E2, 1));
                for (int i = 0; i < 6; i++) rect(4, 15 + i * (H - 40) / 6, W - 8, 1, 0xFFAFC0D0);
                for (int i = 0; i < 5; i++) rect(4 + i * (W - 8) / 5, 15, 1, H - 40, 0xFFAFC0D0);
                disc(W / 2, 15 + (H - 40) / 2, 3, acc);
                for (int i = 0; i < 4; i++) {
                    float x = 8 + i * (W - 16) / 4;
                    boolean sel = st.sel == i;
                    rect(x, H - 22, (W - 16) / 4 - 4, 18, sel ? acc : 0xFFD8DCE0);
                    text(font, buf, APPS[i].equals("PARK ASSIST") ? "PARK" : APPS[i], x + ((W - 16) / 4 - 4) / 2, H - 17, sel ? 0xFFFFFFFF : fg, 0.8f, true);
                }
            }
            case "hex" -> {            // angular yellow tiles (Lamborghini)
                for (int i = 0; i < 4; i++) {
                    float x = 8 + (i % 2) * (W / 2 - 4), y = 18 + (i / 2) * (H - 24) / 2;
                    float tw = W / 2 - 12, th = (H - 24) / 2 - 6;
                    boolean sel = st.sel == i;
                    int c = sel ? acc : 0xFF222222;
                    for (int j = 0; j < 6; j++) {
                        float inset = Math.abs(j - 2.5f) * 3;
                        rect(x + inset, y + j * th / 6, tw - inset * 2, th / 6 + 0.3f, c);
                    }
                    text(font, buf, APPS[i], x + tw / 2, y + th / 2 - 4, sel ? 0xFF111111 : fg, 0.85f, true);
                }
            }
            case "cards" -> {          // horizontal cards (BMW)
                for (int i = 0; i < 4; i++) {
                    float cw = (W - 20) / 4;
                    float x = 4 + i * (cw + 4);
                    boolean sel = st.sel == i;
                    rect(x, 18, cw, H - 26, sel ? acc : 0xFF1A2A40);
                    rect(x, H - 14, cw, 6, sel ? 0xFFFFFFFF : 0xFF2C3E58);
                    text(font, buf, APPS[i].equals("PARK ASSIST") ? "PARK" : APPS[i], x + cw / 2, 18 + (H - 26) / 2 - 4, fg, 0.8f, true);
                }
            }
            default -> {               // 2 x 2 tiles
                for (int i = 0; i < 4; i++) {
                    float x = 6 + (i % 2) * (W / 2 - 3), y = 16 + (i / 2) * (H - 20) / 2;
                    boolean sel = st.sel == i;
                    rect(x, y, W / 2 - 9, (H - 20) / 2 - 4, sel ? acc : mix(0xFF26303A, 1));
                    text(font, buf, APPS[i], x + (W / 2 - 9) / 2, y + (H - 20) / 4 - 6, sel ? 0xFFFFFFFF : fg, 0.9f, true);
                }
            }
        }
        if (v.tickCount < st.toastUntil) {
            rect(W / 2 - 50, H / 2 - 8, 100, 16, 0xE0000000);
            text(font, buf, "Not available", W / 2, H / 2 - 4, 0xFFFFFFFF, 0.9f, true);
        }
    }

    static void park(Font font, MultiBufferSource buf, VehicleEntity v, VehicleSpec s, int bg, int acc, int fg, float H) {
        rect(0, 0, W, H, 0xFF0A0C10);
        text(font, buf, "PARK ASSIST", 4, 2, acc, 0.8f, false);
        float cx = W / 2, cy = H / 2 + 4;
        float hl = Math.min(H * 0.3f, 34), hw = hl * s.planAspect;
        int body = 0xFF000000 | v.getColor(), glass = 0xFF1C2630, tyre = 0xFF050505;
        // the car itself, from above (its own outline; nose at the top)
        if (!s.plan.isEmpty()) {
            for (int i = 0; i + 1 < s.plan.size(); i++) {
                float[] a = s.plan.get(i), b = s.plan.get(i + 1);
                float y0 = cy + hl - b[0] * 2 * hl, y1 = cy + hl - a[0] * 2 * hl;
                float w = Math.max(a[1], b[1]) * hw;
                rect(cx - w, y0, 2 * w, y1 - y0 + 0.4f, body);
            }
            for (float t : s.axlesT) {
                float y = cy + hl - t * 2 * hl;
                rect(cx - hw - 1.5f, y - 3, 2.5f, 6, tyre);
                rect(cx + hw - 1f, y - 3, 2.5f, 6, tyre);
            }
            for (int i = 0; i + 1 < s.cabinPlan.size(); i++) {
                float[] a = s.cabinPlan.get(i), b = s.cabinPlan.get(i + 1);
                float y0 = cy + hl - b[0] * 2 * hl, y1 = cy + hl - a[0] * 2 * hl;
                float w = Math.min(a[1], b[1]) * hw;
                boolean gl = false;
                for (float[] g : s.glassBands) gl |= a[0] >= g[0] - 0.01f && b[0] <= g[1] + 0.01f;
                rect(cx - w, y0, 2 * w, y1 - y0 + 0.4f, gl ? glass : darker(body));
            }
        } else rect(cx - hw, cy - hl, 2 * hw, 2 * hl, body);
        // sensor zones around it: dark = clear, green / yellow / red = closer
        for (int k = 0; k < 3; k++) {
            float gap = 3 + k * 5, t = 3.5f;
            band(0, k, cx - hw * 0.7f, cy - hl - gap - t, hw * 1.4f, t);
            band(5, k, cx - hw * 0.7f, cy + hl + gap, hw * 1.4f, t);
            band(1, k, cx - hw - gap - t, cy - hl - gap - t + 4, hw * 0.55f + gap, t);
            band(2, k, cx + hw * 0.45f, cy - hl - gap - t + 4, hw * 0.55f + gap, t);
            band(6, k, cx - hw - gap - t, cy + hl + gap - 4, hw * 0.55f + gap, t);
            band(7, k, cx + hw * 0.45f, cy + hl + gap - 4, hw * 0.55f + gap, t);
            band(3, k, cx - hw - 3 - gap - t, cy - hl * 0.55f, t, hl * 1.1f);
            band(4, k, cx + hw + 3 + gap, cy - hl * 0.55f, t, hl * 1.1f);
        }
        String d = ParkSensor.nearest < 9 ? String.format("%.1f m", ParkSensor.nearest) : "--";
        text(font, buf, d, W - 24, H - 10, ParkSensor.nearest < 0.66f ? ParkSensor.RED : fg, 0.8f, true);
    }

    static int darker(int c) {
        int r = (c >> 16 & 255) * 3 / 4, g = (c >> 8 & 255) * 3 / 4, b = (c & 255) * 3 / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static void band(int zone, int k, float x, float y, float w, float h) {
        int lv = ParkSensor.level[zone];
        int c = ParkSensor.OFF;
        if (lv >= 3 - k) c = k == 0 ? ParkSensor.RED : k == 1 ? ParkSensor.YELLOW : ParkSensor.GREEN;
        rect(x, y, w, h, c);
    }
}
