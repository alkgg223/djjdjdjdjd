package com.trafficcraft.client.gui;

import com.trafficcraft.client.ClientEvents;
import com.trafficcraft.vehicle.VehicleEntity;
import com.trafficcraft.vehicle.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Parking sensors for cars with a screen: eight zones around the car probe blocks, other vehicles and people.
 * The screen shows the car from above with three bands per zone (dark = clear, green / yellow / red = getting closer)
 * and a warning beep that speeds up as the gap closes.
 */
public final class ParkSensor {
    // zones: 0 F, 1 FL, 2 FR, 3 L, 4 R, 5 B, 6 BL, 7 BR
    static final float[] RANGE = {2.0f, 1.4f, 1.4f, 0.9f, 0.9f, 2.0f, 1.4f, 1.4f};
    static final float[] dist = new float[8];
    static final int[] level = new int[8];
    static boolean active;
    static int beepTimer;
    static float nearest = 99;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        if (mc.player == null || mc.level == null) return;
        VehicleEntity v = ClientEvents.drivenVehicle();
        if (v == null || !v.spec().screenSensor || !v.has(VehicleEntity.F_ENGINE)) return;
        float kmh = Math.abs(v.visSpeed()) * 3.6f;
        VehicleSpec s = v.spec();
        double yaw = Math.toRadians(v.getYRot());
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        float hl = s.length / 2, hw = s.width / 2;
        float[][] start = new float[8][];
        float[][] dir = new float[8][];
        // [x, z] start points per zone (car frame: +x left, +z forward) and direction
        start[0] = new float[]{-hw * 0.45f, hl, 0, hl, hw * 0.45f, hl};
        dir[0] = new float[]{0, 1};
        start[1] = new float[]{hw * 0.85f, hl - 0.12f};
        dir[1] = new float[]{0.7f, 0.7f};
        start[2] = new float[]{-hw * 0.85f, hl - 0.12f};
        dir[2] = new float[]{-0.7f, 0.7f};
        start[3] = new float[]{hw, hl * 0.45f, hw, 0, hw, -hl * 0.45f};
        dir[3] = new float[]{1, 0};
        start[4] = new float[]{-hw, hl * 0.45f, -hw, 0, -hw, -hl * 0.45f};
        dir[4] = new float[]{-1, 0};
        start[5] = new float[]{-hw * 0.45f, -hl, 0, -hl, hw * 0.45f, -hl};
        dir[5] = new float[]{0, -1};
        start[6] = new float[]{hw * 0.85f, -hl + 0.12f};
        dir[6] = new float[]{0.7f, -0.7f};
        start[7] = new float[]{-hw * 0.85f, -hl + 0.12f};
        dir[7] = new float[]{-0.7f, -0.7f};
        Vec3 base = v.position();
        java.util.List<Entity> others = mc.level.getEntities(v, v.getBoundingBox().inflate(s.length + 3), e -> e != mc.player && !e.isPassengerOfSameVehicle(v)
                && (e instanceof VehicleEntity || e instanceof LivingEntity));
        nearest = 99;
        boolean any = false;
        for (int z = 0; z < 8; z++) {
            float best = 99;
            Vec3 d = fwd.scale(dir[z][1]).add(left.scale(dir[z][0])).normalize();
            for (int i = 0; i < start[z].length; i += 2) {
                for (double hy : new double[]{0.35, 0.75}) {
                    Vec3 a = base.add(left.scale(start[z][i])).add(fwd.scale(start[z][i + 1])).add(0, hy, 0);
                    Vec3 b = a.add(d.scale(RANGE[z]));
                    BlockHitResult hit = mc.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.BLOCK) best = (float) Math.min(best, hit.getLocation().distanceTo(a));
                    for (Entity o : others) {
                        float t = o instanceof VehicleEntity ov ? rayVehicle(ov, a, d, RANGE[z]) : rayBox(o.getBoundingBox(), a, b);
                        if (t < best) best = t;
                    }
                }
            }
            dist[z] = best;
            float f = best / RANGE[z];
            level[z] = f < 0.33f ? 3 : f < 0.66f ? 2 : f < 1f ? 1 : 0;
            if (level[z] > 0) {
                any = true;
                nearest = Math.min(nearest, best);
            }
        }
        active = kmh < 30 && (any || v.visGear() < 0);
        if (active && any) {
            int max = 0;
            for (int l : level) max = Math.max(max, l);
            int interval = max == 3 ? (nearest < 0.3f ? 2 : 4) : max == 2 ? 7 : 13;
            if (--beepTimer <= 0) {
                beepTimer = interval;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), max == 3 ? 2.0f : 1.7f, 0.32f));
            }
        } else beepTimer = 0;
    }

    private static float rayBox(AABB box, Vec3 a, Vec3 b) {
        var r = box.clip(a, b);
        return r.map(p -> (float) p.distanceTo(a)).orElse(99f);
    }

    /** Ray against another car's oriented footprint (length x width). */
    private static float rayVehicle(VehicleEntity o, Vec3 a, Vec3 d, float range) {
        VehicleSpec s = o.spec();
        double yaw = Math.toRadians(o.getYRot());
        Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), l = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 r = a.subtract(o.position());
        double ry = r.y;
        if (ry < 0 || ry > s.height) return 99;
        double px = r.dot(l), pz = r.dot(f), dx = d.dot(l), dz = d.dot(f);
        double t0 = 0, t1 = range;
        double[][] slabs = {{px, dx, s.width / 2}, {pz, dz, s.length / 2}};
        for (double[] sl : slabs) {
            double p = sl[0], dd = sl[1], h = sl[2];
            if (Math.abs(dd) < 1e-6) {
                if (Math.abs(p) > h) return 99;
            } else {
                double ta = (-h - p) / dd, tb = (h - p) / dd;
                t0 = Math.max(t0, Math.min(ta, tb));
                t1 = Math.min(t1, Math.max(ta, tb));
                if (t0 > t1) return 99;
            }
        }
        return (float) t0;
    }

    // ------------------------------------------------------------------------------------------------ screen
    static final int OFF = 0xFF23272D, GREEN = 0xFF3FD25A, YELLOW = 0xFFF2C230, RED = 0xFFF03A2E;

    public static void render(GuiGraphics g, VehicleEntity v) {
        if (!active || ClientEvents.drivenVehicle() != v) return;
        Minecraft mc = Minecraft.getInstance();
        int pw = 104, ph = 150;
        int x = 8, y = g.guiHeight() - ph - 30;
        g.fill(x, y, x + pw, y + ph, 0xD00A0C10);
        g.fill(x, y, x + pw, y + 1, 0xFF3A4A5E);
        g.drawString(mc.font, "PARK ASSIST", x + 6, y + 4, 0xFF9AB4D0, false);
        int cx = x + pw / 2, cy = y + 80;
        int hw = 11, hl = 24;
        // the car seen from above in its own colour
        int col = 0xFF000000 | v.getColor();
        g.fill(cx - hw, cy - hl + 3, cx + hw, cy + hl - 2, col);
        g.fill(cx - hw + 2, cy - hl, cx + hw - 2, cy - hl + 3, col);
        g.fill(cx - hw + 3, cy - hl + 12, cx + hw - 3, cy - hl + 18, 0xFF1C2630);   // windscreen
        g.fill(cx - hw + 3, cy + hl - 14, cx + hw - 3, cy + hl - 10, 0xFF1C2630);   // rear window
        g.fill(cx - hw + 3, cy - hl + 18, cx + hw - 3, cy + hl - 14, darker(col));  // roof
        g.fill(cx - hw - 2, cy - hl + 8, cx - hw, cy - hl + 15, 0xFF101214);       // wheels
        g.fill(cx + hw, cy - hl + 8, cx + hw + 2, cy - hl + 15, 0xFF101214);
        g.fill(cx - hw - 2, cy + hl - 15, cx - hw, cy + hl - 8, 0xFF101214);
        g.fill(cx + hw, cy + hl - 15, cx + hw + 2, cy + hl - 8, 0xFF101214);
        for (int k = 0; k < 3; k++) {            // k = 0 nearest band (red) .. 2 outer band (green)
            int gap = 3 + k * 6, t = 4;
            // front / rear arcs
            band(g, 0, k, cx - 9, cy - hl - gap - t, cx + 9, cy - hl - gap);
            band(g, 5, k, cx - 9, cy + hl + gap, cx + 9, cy + hl + gap + t);
            // corners (screen left = car left)
            band(g, 1, k, cx - hw - gap - t + 2, cy - hl - gap - t + 4, cx - 10, cy - hl - gap + 4);
            band(g, 2, k, cx + 10, cy - hl - gap - t + 4, cx + hw + gap + t - 2, cy - hl - gap + 4);
            band(g, 6, k, cx - hw - gap - t + 2, cy + hl + gap - 4, cx - 10, cy + hl + gap + t - 4);
            band(g, 7, k, cx + 10, cy + hl + gap - 4, cx + hw + gap + t - 2, cy + hl + gap + t - 4);
            // sides
            band(g, 3, k, cx - hw - 3 - gap - t, cy - 14, cx - hw - 3 - gap, cy + 14);
            band(g, 4, k, cx + hw + 3 + gap, cy - 14, cx + hw + 3 + gap + t, cy + 14);
        }
        String d = nearest < 9 ? String.format("%.1f m", nearest) : "--";
        g.drawString(mc.font, d, x + pw / 2 - mc.font.width(d) / 2, y + ph - 12, nearest < 0.66f ? RED : 0xFFE0E6EE, false);
    }

    private static void band(GuiGraphics g, int zone, int k, int x0, int y0, int x1, int y1) {
        int lv = level[zone];
        int c = OFF;
        if (lv >= 3 - k) c = k == 0 ? RED : k == 1 ? YELLOW : GREEN;
        g.fill(x0, y0, x1, y1, c);
    }

    private static int darker(int c) {
        int r = (c >> 16 & 255) * 3 / 4, gg = (c >> 8 & 255) * 3 / 4, b = (c & 255) * 3 / 4;
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }
}
