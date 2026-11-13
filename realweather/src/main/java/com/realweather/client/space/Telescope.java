package com.realweather.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.realweather.RealWeather;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector4f;

/**
 * Looking through the telescope. You choose something from its list; a marker then shows where it is in the sky (an
 * arrow at the edge if it is off the view). Turn to it and zoom in (mouse wheel, or hold attack to zoom in and use to
 * zoom out): only at high power does it show itself. Sneak to step back from the eyepiece.
 */
public final class Telescope {
    private static final ResourceLocation EYEPIECE = RealWeather.id("textures/environment/space3d/eyepiece.png");
    /** Narrowest field of view (degrees): about 14 000 times. */
    private static final double MIN_FOV = 0.005;
    private static boolean active;
    private static BlockPos pos;
    private static Vec3 start;
    private static Catalog.Target target;
    private static double fov = 70, wantFov = 70, baseFov = 70;

    private Telescope() {}

    public static boolean active() {
        return active;
    }

    public static double fov() {
        return fov;
    }

    public static Catalog.Target target() {
        return target;
    }

    public static void start(BlockPos at, Catalog.Target t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        active = true;
        lastSky = Double.NaN;
        pos = at;
        start = mc.player.position();
        target = t;
        baseFov = mc.options.fov().get();
        fov = wantFov = baseFov;
        mc.player.displayClientMessage(Component.translatable("realweather.telescope.started",
                Component.translatable("realweather.target." + t.id)), true);
    }

    /** For tests: zoom straight to a field of view. */
    public static void setFov(double f) {
        fov = wantFov = Mth.clamp(f, MIN_FOV, baseFov);
    }

    public static void stop() {
        active = false;
        target = null;
    }

    private static void zoom(double factor) {
        wantFov = Mth.clamp(wantFov * factor, MIN_FOV, baseFov);
    }

    // ------------------------------------------------------------------------------------------- events

    public static void onFov(ViewportEvent.ComputeFov event) {
        if (active) {
            track((float) event.getPartialTick());
            // Glide to the new zoom (by ratio, so it feels even at every power).
            fov = Math.exp(Mth.lerp(0.25, Math.log(fov), Math.log(wantFov)));
            event.setFOV(fov);
        }
    }

    private static double lastSky = Double.NaN;

    /**
     * The clock drive: the sky turns (once a day, about the north-south axis), and the telescope turns with it, so
     * whatever it is pointed at stays in the field even at the highest power.
     */
    private static void track(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        double sky = mc.level.getTimeOfDay(pt) * Math.PI * 2;
        double d = Double.isNaN(lastSky) ? 0 : sky - lastSky;
        lastSky = sky;
        if (d == 0 || Math.abs(d) > 0.02) {
            return;
        }
        double yaw = Math.toRadians(mc.player.getYRot()), pitch = Math.toRadians(mc.player.getXRot());
        double x = -Math.sin(yaw) * Math.cos(pitch), y = -Math.sin(pitch), z = Math.cos(yaw) * Math.cos(pitch);
        double c = Math.cos(d), sn = Math.sin(d);
        double nx = x * c - y * sn, ny = x * sn + y * c;
        float newYaw = (float) Math.toDegrees(Math.atan2(-nx, z));
        float newPitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(ny, -1, 1)));
        // Keep the yaw continuous (no jump of 360 degrees).
        float oldYaw = mc.player.getYRot();
        newYaw = oldYaw + Mth.wrapDegrees(newYaw - oldYaw);
        mc.player.setYRot(newYaw);
        mc.player.yRotO += newYaw - oldYaw;
        mc.player.setXRot(newPitch);
    }

    /** The more you zoom in, the slower the view turns, so you can steer at any power. */
    public static void onTurn(CalculatePlayerTurnEvent event) {
        if (!active) {
            return;
        }
        double scale = Math.max(1.0E-5, fov / baseFov);
        double d = event.getMouseSensitivity() * 0.6 + 0.2;
        double want = d * Math.cbrt(scale);
        event.setMouseSensitivity((want - 0.2) / 0.6);
    }

    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (active) {
            event.setCanceled(true);
            zoom(event.getScrollDeltaY() > 0 ? 0.75 : 1.0 / 0.75);
        }
    }

    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (active) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    public static void onMovement(MovementInputUpdateEvent event) {
        if (!active) {
            return;
        }
        Input in = event.getInput();
        if (in.shiftKeyDown) {
            stop();
            in.shiftKeyDown = false;
            return;
        }
        // Standing at the eyepiece.
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
    }

    public static void onRenderHand(RenderHandEvent event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    public static void tick(Minecraft mc) {
        if (!active) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.player.position().distanceTo(start) > 2.5
                || !(mc.level.getBlockState(pos).getBlock() instanceof com.realweather.device.TelescopeBlock)) {
            stop();
            return;
        }
        // Hold attack to zoom in, use to zoom out (also on a touch screen).
        if (mc.options.keyAttack.isDown()) {
            zoom(0.9);
        }
        if (mc.options.keyUse.isDown()) {
            zoom(1 / 0.9);
        }
    }

    // ------------------------------------------------------------------------------------------- the view

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.level == null || target == null || mc.options.hideGui && !Boolean.getBoolean("realweather.selftest")) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        double power = baseFov / fov;
        // The eyepiece: a round field of view at high power.
        if (power > 2.5) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int s = Math.max(w, h);
            g.setColor(1F, 1F, 1F, Mth.clamp((float) (power - 2.5) / 3F, 0F, 1F));
            g.blit(EYEPIECE, (w - s) / 2, (h - s) / 2, 0, 0F, 0F, s, s, s, s);
            g.setColor(1F, 1F, 1F, 1F);
        }
        // Where the target is.
        double[] d = TelescopeView.direction(target, mc.level, delta.getGameTimeDeltaPartialTick(false));
        Vector4f v = new Vector4f((float) d[0] * 100F, (float) d[1] * 100F, (float) d[2] * 100F, 1F);
        TelescopeView.lastView.transform(v);
        Vector4f clip = new Vector4f(v);
        TelescopeView.lastProj.transform(clip);
        Component name = Component.translatable("realweather.target." + target.id);
        int color = 0xFFFFC040;
        boolean ahead = clip.w > 0;
        float sx = ahead ? (clip.x / clip.w * 0.5F + 0.5F) * w : -1, sy = ahead ? (1F - (clip.y / clip.w * 0.5F + 0.5F)) * h : -1;
        boolean onScreen = ahead && sx >= 8 && sx <= w - 8 && sy >= 8 && sy <= h - 8;
        if (onScreen) {
            int x = Math.round(sx), y = Math.round(sy), r = 7;
            bracket(g, x, y, r, color);
            g.drawString(mc.font, name, x + r + 4, y - 4, color, true);
        } else {
            // An arrow at the edge, pointing the way to turn.
            double ax = v.x, ay = v.y;
            double len = Math.sqrt(ax * ax + ay * ay) + 1.0E-9;
            ax /= len;
            ay /= len;
            int cx = w / 2, cy = h / 2;
            double reach = Math.min(w, h) * 0.42;
            int x = (int) (cx + ax * reach), y = (int) (cy - ay * reach);
            String arrow = Math.abs(ax) > Math.abs(ay) ? (ax > 0 ? ">>" : "<<") : (ay > 0 ? "^" : "v");
            g.drawCenteredString(mc.font, arrow, x, y - 4, color);
            g.drawCenteredString(mc.font, name, x, y + 6, color);
        }
        // Information along the bottom.
        double up = d[1];
        String mag = power >= 100 ? String.format("x%.0f", power) : String.format("x%.1f", power);
        g.drawString(mc.font, Component.translatable("realweather.telescope.info", name, mag, target.sys.distance), 8, h - 30, 0xFFE8E8E8, true);
        Component hint = up < -0.02 ? Component.translatable("realweather.telescope.below")
                : Component.translatable("realweather.telescope.hint");
        g.drawString(mc.font, hint, 8, h - 18, up < -0.02 ? 0xFFFF9070 : 0xFFB0B0B0, true);
    }

    private static void bracket(GuiGraphics g, int x, int y, int r, int c) {
        int l = 3;
        g.fill(x - r, y - r, x - r + l, y - r + 1, c);
        g.fill(x - r, y - r, x - r + 1, y - r + l, c);
        g.fill(x + r - l + 1, y - r, x + r + 1, y - r + 1, c);
        g.fill(x + r, y - r, x + r + 1, y - r + l, c);
        g.fill(x - r, y + r, x - r + l, y + r + 1, c);
        g.fill(x - r, y + r - l + 1, x - r + 1, y + r + 1, c);
        g.fill(x + r - l + 1, y + r, x + r + 1, y + r + 1, c);
        g.fill(x + r, y + r - l + 1, x + r + 1, y + r + 1, c);
    }
}
