package com.gunsmod.client;

import com.gunsmod.GunsMod;
import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.TankEntity;
import com.gunsmod.entity.VehicleEntity;
import com.gunsmod.gun.GunLogic;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * Vehicle views the way war games do them. Inside (first person) the crew looks through a clean gunner's sight: the
 * own vehicle is not drawn, the eye sits at the sight beside the gun, the mouse wheel zooms the optics. Outside (third
 * person) the camera pulls back to fit the vehicle and a marker shows where the gun is laid.
 */
@EventBusSubscriber(modid = GunsMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class VehicleCamera {
    private VehicleCamera() {}

    private static final float[] ZOOMS = {1f, 2f, 4f, 8f};
    private static int zoomStep = 1;

    public static void init() {
        VehicleEntity.sightView = e -> {
            Minecraft mc = Minecraft.getInstance();
            return e == mc.player && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == mc.player;
        };
    }

    // ------------------------------------------------------------------------------------ who sees what
    /** the local player looks out of this vehicle in first person: it is not drawn */
    public static boolean hidden(Entity vehicle) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && p.getVehicle() == vehicle && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == p;
    }

    /** the local player works the gun of a tank or an armed car in first person: the optics are up */
    public static boolean sightUp() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson()) return false;
        Entity v = p.getVehicle();
        return (v instanceof TankEntity || v instanceof ArmoredCarEntity) && ((VehicleEntity) v).getControllingPassenger() == p;
    }

    public static float zoom() {
        return sightUp() ? ZOOMS[zoomStep] : 1f;
    }

    /** the field of view the world is drawn with (for projecting markers) */
    public static double fovDeg(Minecraft mc) {
        return mc.options.fov().get() / zoom();
    }

    // ------------------------------------------------------------------------------------ events
    @SubscribeEvent
    public static void distance(CalculateDetachedCameraDistanceEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getCameraEntity() != mc.player) return;
        Entity v = mc.player.getVehicle();
        float d;
        if (v instanceof TankEntity t) d = 9.5f * t.kind.scale;
        else if (v instanceof ArmoredCarEntity c) d = c.kind.artillery() || c.kind.antiAir() ? 12f : c.kind.cram() ? 8f : 10f;
        else if (v instanceof HelicopterEntity) d = 14f;
        else if (v instanceof JetEntity) d = 17f;
        else if (v instanceof JeepEntity) d = 7f;
        else return;
        e.setDistance(Math.max(e.getDistance(), d));
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (!e.usedConfiguredFov()) return;
        float z = zoom();
        if (z > 1f) e.setFOV(e.getFOV() / z);
    }

    @SubscribeEvent
    public static void turn(CalculatePlayerTurnEvent e) {
        float z = zoom();
        if (z > 1f) e.setMouseSensitivity(e.getMouseSensitivity() / Math.pow(z, 0.75));
    }

    /** the mouse wheel steps the sight magnification */
    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (!sightUp() || e.getScrollDeltaY() == 0) return;
        zoomStep = Mth.clamp(zoomStep + (e.getScrollDeltaY() > 0 ? 1 : -1), 0, ZOOMS.length - 1);
        e.setCanceled(true);
    }

    // ------------------------------------------------------------------------------------ drawing
    /** screen position of a world point, or null when it is behind the camera */
    @Nullable
    public static int[] project(Minecraft mc, GuiGraphics g, Vec3 world) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 rel = world.subtract(cam.getPosition());
        Vector3f l = cam.getLookVector(), u = cam.getUpVector(), lf = cam.getLeftVector();
        double z = rel.x * l.x + rel.y * l.y + rel.z * l.z;
        if (z < 0.5) return null;
        double x = -(rel.x * lf.x + rel.y * lf.y + rel.z * lf.z);
        double y = rel.x * u.x + rel.y * u.y + rel.z * u.z;
        double f = (g.guiHeight() / 2.0) / Math.tan(Math.toRadians(fovDeg(mc) / 2.0));
        return new int[]{g.guiWidth() / 2 + (int) Math.round(x / z * f), g.guiHeight() / 2 - (int) Math.round(y / z * f)};
    }

    /** gunner's optics: the round field of view inside a black eyepiece, the magnification in the corner */
    public static void sightFrame(GuiGraphics g, Minecraft mc) {
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        int r = (int) (Math.min(w, h) * 0.47);
        g.fill(0, 0, w, Math.max(0, cy - r), 0xFF000000);
        g.fill(0, Math.min(h, cy + r), w, h, 0xFF000000);
        for (int y = Math.max(0, cy - r); y < Math.min(h, cy + r); y++) {
            double dy = y + 0.5 - cy;
            int half = (int) Math.sqrt(Math.max(0, r * (double) r - dy * dy));
            g.fill(0, y, cx - half, y + 1, 0xFF000000);
            g.fill(cx + half, y, w, y + 1, 0xFF000000);
            // soft dark rim inside the glass
            int rim = Math.max(2, r / 14);
            g.fill(cx - half, y, Math.min(cx, cx - half + rim), y + 1, 0x70000000);
            g.fill(Math.max(cx, cx + half - rim), y, cx + half, y + 1, 0x70000000);
        }
        // stadia: range ticks below the centre and the horizontal bar
        int col = 0xC0101010;
        for (int i = 1; i <= 4; i++) {
            int y = cy + i * r / 9;
            int len = i % 2 == 0 ? r / 10 : r / 16;
            g.fill(cx - len, y, cx + len, y + 1, col);
        }
        g.fill(cx - r + r / 8, cy, cx - r / 5, cy + 1, col);
        g.fill(cx + r / 5, cy, cx + r - r / 8, cy + 1, col);
        g.fill(cx, cy - r + r / 8, cx + 1, cy - r / 4, col);
        Component zt = Component.literal("x" + (int) ZOOMS[zoomStep]);
        g.drawString(mc.font, zt, cx + r - mc.font.width(zt) - 6, cy + r - 16, 0xFF60FF80, true);
        Component hint = Component.translatable("gunsmod.hud.sight_zoom");
        g.drawString(mc.font, hint, cx - mc.font.width(hint) / 2, Math.min(h - 12, cy + r + 4), 0x90FFFFFF, true);
    }

    /** third person: where the gun is laid (a ring) and what the camera looks at (a small cross) */
    public static void thirdPersonAim(GuiGraphics g, Minecraft mc, float pt) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        Entity v = p.getVehicle();
        Vec3 from, dir;
        int col = 0xE040FF60;
        boolean target = false;
        if (v instanceof TankEntity t && t.getControllingPassenger() == p) {
            from = t.muzzleWorld();
            dir = t.gunDir();
            if (t.getReload() > 0) col = 0xE0FFA030;
        } else if (v instanceof ArmoredCarEntity c && c.getControllingPassenger() == p) {
            if (c.kind.artillery()) {
                // rocket artillery: the point the fire control lays on
                from = p.getEyePosition(pt);
                dir = p.getViewVector(pt);
                target = true;
            } else {
                from = c.sightWorld();
                dir = c.gunDir();
            }
            if (c.getReload() > 0) col = 0xE0FFA030;
        } else if (v instanceof JeepEntity j && j.isGunner(p)) {
            from = j.muzzleWorld();
            dir = j.turretDir();
        } else if (v instanceof HelicopterEntity h && h.getControllingPassenger() == p) {
            from = h.gunMuzzle();
            dir = GunLogic.dir(h.getChinPitch(), h.getYRot() + h.getChinYaw());
        } else if (v instanceof JetEntity jt && jt.getControllingPassenger() == p) {
            from = jt.position().add(0, 1.5, 0);
            dir = GunLogic.dir(jt.getXRot(), jt.getYRot());
        } else {
            return;
        }
        Vec3 end = from.add(dir.scale(400));
        HitResult hr = mc.level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v));
        Vec3 at = hr.getType() == HitResult.Type.MISS ? end : hr.getLocation();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        // the camera's own aim: a small open cross
        g.fill(cx - 5, cy, cx - 2, cy + 1, 0xA0FFFFFF);
        g.fill(cx + 3, cy, cx + 6, cy + 1, 0xA0FFFFFF);
        g.fill(cx, cy - 5, cx + 1, cy - 2, 0xA0FFFFFF);
        g.fill(cx, cy + 3, cx + 1, cy + 6, 0xA0FFFFFF);
        int[] s = project(mc, g, at);
        if (s == null) return;
        if (target) {
            // impact point: a box with the range
            int d = 6;
            g.fill(s[0] - d, s[1] - d, s[0] + d + 1, s[1] - d + 1, col);
            g.fill(s[0] - d, s[1] + d, s[0] + d + 1, s[1] + d + 1, col);
            g.fill(s[0] - d, s[1] - d, s[0] - d + 1, s[1] + d + 1, col);
            g.fill(s[0] + d, s[1] - d, s[0] + d + 1, s[1] + d + 1, col);
            String r = hr.getType() == HitResult.Type.MISS ? "---" : Math.round(at.distanceTo(v.position())) + " m";
            g.drawString(mc.font, r, s[0] + d + 4, s[1] - 4, col, true);
            return;
        }
        // gun aim point: ring, centre dot and a drop line under it
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            int x = s[0] + (int) Math.round(Math.cos(a) * 8), y = s[1] + (int) Math.round(Math.sin(a) * 8);
            g.fill(x, y, x + 1, y + 1, 0xC0000000);
            g.fill(x, y - 1, x + 1, y, col);
        }
        g.fill(s[0], s[1], s[0] + 1, s[1] + 1, col);
        g.fill(s[0], s[1] + 10, s[0] + 1, s[1] + 15, col);
        if (hr.getType() != HitResult.Type.MISS) {
            String r = Math.round(at.distanceTo(from)) + " m";
            g.drawString(mc.font, r, s[0] + 11, s[1] - 4, 0xC0FFFFFF, true);
        }
    }
}
