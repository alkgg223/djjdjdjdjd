package com.realweather.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.realweather.RealWeather;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Looking through the telescope. You choose something from its list; a marker then shows where it is in the sky (an
 * arrow at the edge if it is off the view). Turn to it and zoom in (mouse wheel, or hold attack to zoom in and use to
 * zoom out): only at high power does it show itself. Sneak to step back from the eyepiece.
 */
public final class Telescope {
    private static final ResourceLocation EYEPIECE = RealWeather.id("textures/environment/space3d/eyepiece.png");
    /** Highest power: a million times. */
    public static final double MAX_POWER = 1000000;
    private static boolean active;
    private static BlockPos pos;
    private static Vec3 start;
    private static Catalog.Target target;
    private static double fov = 70, wantFov = 70, baseFov = 70;
    /** Where the telescope points, fixed among the stars (sky frame), so the clock drive keeps it on its target. */
    private static final double[] AIM = {0, 1, 0};
    /** Its yaw and pitch this frame (degrees, world), in double precision. */
    private static double viewYaw, viewPitch;
    /** The player's own angles are parked here while at the eyepiece; turning moves them, and is read back. */
    private static float parkYaw;
    private static boolean parked;
    /** Ticks since the player last turned the telescope by hand (tracking waits for them). */
    private static int trackHold;
    /** Observing mode: visible light, infrared, ultraviolet, X-ray, or the target's spectrum. */
    public static final int VISIBLE = 0, INFRARED = 1, ULTRAVIOLET = 2, XRAY = 3, SPECTRUM = 4;
    public static int mode;
    /** Out of focus by this much (0 is sharp: the automatic focus). */
    public static float focus;
    /** Following the target as it moves across the sky (planets, comets), not just the stars. */
    public static boolean tracking = true;
    public static final net.minecraft.client.KeyMapping MODE_KEY = new net.minecraft.client.KeyMapping("key.realweather.telescope_mode",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_M, "key.categories.realweather");
    public static final net.minecraft.client.KeyMapping TRACK_KEY = new net.minecraft.client.KeyMapping("key.realweather.telescope_track",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_R, "key.categories.realweather");
    public static final net.minecraft.client.KeyMapping FOCUS_IN = new net.minecraft.client.KeyMapping("key.realweather.telescope_focus_in",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.realweather");
    public static final net.minecraft.client.KeyMapping FOCUS_OUT = new net.minecraft.client.KeyMapping("key.realweather.telescope_focus_out",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.realweather");
    public static final net.minecraft.client.KeyMapping AUTO_FOCUS = new net.minecraft.client.KeyMapping("key.realweather.telescope_autofocus",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSLASH, "key.categories.realweather");

    public static void onKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(MODE_KEY);
        event.register(TRACK_KEY);
        event.register(FOCUS_IN);
        event.register(FOCUS_OUT);
        event.register(AUTO_FOCUS);
    }

    /** The next observing mode. */
    public static void nextMode() {
        mode = (mode + 1) % 5;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("realweather.telescope.mode", Component.translatable("realweather.telescope.mode." + mode)),
                    true);
        }
    }

    public static void toggleTracking() {
        tracking = !tracking;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(tracking ? "realweather.telescope.tracking_on" : "realweather.telescope.tracking_off"),
                    true);
        }
    }

    private Telescope() {}

    public static boolean active() {
        return active;
    }

    public static double fov() {
        return fov;
    }

    public static double minFov() {
        return baseFov / MAX_POWER;
    }

    public static Catalog.Target target() {
        return target;
    }

    public static void start(BlockPos at, Catalog.Target t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!active) {
            // Pointing where the player looks.
            point(dirOf(mc.player.getYRot(), mc.player.getXRot()), mc.level, 1F);
            parkYaw = mc.player.getYRot();
            parked = false;
        }
        active = true;
        pos = at;
        start = mc.player.position();
        target = t;
        baseFov = mc.options.fov().get();
        fov = wantFov = baseFov;
        mc.player.displayClientMessage(Component.translatable("realweather.telescope.started",
                t.label()), true);
    }

    /** For tests: zoom straight to a field of view. */
    public static void setFov(double f) {
        fov = wantFov = Mth.clamp(f, minFov(), baseFov);
    }

    /** Points the telescope along a world direction (camera-relative) as it is at this moment. */
    public static void point(double[] w, ClientLevel level, float pt) {
        double th = Space3D.timeOfDay(level, pt) * Math.PI * 2;
        toSky(w, th, AIM);
        double[] ypr = yawPitch(w);
        viewYaw = ypr[0];
        viewPitch = ypr[1];
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (active && parked && mc.player != null) {
            // Back to the ordinary view, looking the same way.
            float y = (float) viewYaw, p = (float) Mth.clamp(viewPitch, -90, 90);
            mc.player.setYRot(y);
            mc.player.yRotO = y;
            mc.player.setYHeadRot(y);
            mc.player.setXRot(p);
            mc.player.xRotO = p;
        }
        active = false;
        parked = false;
        target = null;
    }

    private static void zoom(double factor) {
        wantFov = Mth.clamp(wantFov * factor, minFov(), baseFov);
    }

    // ------------------------------------------------------------------------------------------- pointing

    static double[] dirOf(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[]{-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    static double[] yawPitch(double[] w) {
        double l = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
        return new double[]{Math.toDegrees(Math.atan2(-w[0], w[2])), -Math.toDegrees(Math.asin(Mth.clamp(w[1] / l, -1, 1)))};
    }

    /** Sky frame to world (the same turn as the sun, moon and stars), and back. */
    static void toWorld(double[] v, double th, double[] out) {
        double c = Math.cos(th), s = Math.sin(th);
        double x = -s * v[1] - c * v[2], y = c * v[1] - s * v[2], z = v[0];
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    static void toSky(double[] w, double th, double[] out) {
        double c = Math.cos(th), s = Math.sin(th);
        double x = w[2], y = -s * w[0] + c * w[1], z = -c * w[0] - s * w[1];
        double l = Math.sqrt(x * x + y * y + z * z) + 1.0E-300;
        out[0] = x / l;
        out[1] = y / l;
        out[2] = z / l;
    }

    /** The telescope's view (world to view, 3 x 3, column-major) for this frame, in double precision. */
    static void view(double[] m) {
        double[] f = dirOf(viewYaw, viewPitch);
        double y = Math.toRadians(viewYaw);
        double rx = -Math.cos(y), ry = 0, rz = -Math.sin(y);
        double ux = ry * f[2] - rz * f[1], uy = rz * f[0] - rx * f[2], uz = rx * f[1] - ry * f[0];
        m[0] = rx;
        m[3] = ry;
        m[6] = rz;
        m[1] = ux;
        m[4] = uy;
        m[7] = uz;
        m[2] = -f[0];
        m[5] = -f[1];
        m[8] = -f[2];
    }

    // ------------------------------------------------------------------------------------------- events

    public static void onFov(ViewportEvent.ComputeFov event) {
        if (active) {
            if (event.usedConfiguredFov()) {
                // Glide to the new zoom (by ratio, so it feels even at every power).
                fov = Math.exp(Mth.lerp(0.25, Math.log(fov), Math.log(wantFov)));
            }
            event.setFOV(fov);
        }
    }

    /**
     * Every frame: the turn the player made (read back from their parked angles) moves the telescope, by less the
     * more it is zoomed in; the clock drive turns it with the sky; and the camera is pointed along it.
     */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.level == null) {
            return;
        }
        float pt = (float) event.getPartialTick();
        double th = Space3D.timeOfDay(mc.level, pt) * Math.PI * 2;
        double[] w = new double[3];
        toWorld(AIM, th, w);
        double[] ypr = yawPitch(w);
        double yaw = ypr[0], pitch = ypr[1];
        net.minecraft.client.player.LocalPlayer p = mc.player;
        if (parked) {
            double k = fov / baseFov;
            double dy = Mth.wrapDegrees(p.getYRot() - parkYaw), dp = p.getXRot();
            yaw += dy * k;
            pitch = Mth.clamp(pitch + dp * k, -89.9999, 89.9999);
            if (dy != 0 || dp != 0) {
                toSky(dirOf(yaw, pitch), th, AIM);
                trackHold = 60;
            } else if (tracking && target != null && trackHold <= 0 && target.sys != Catalog.earth) {
                // Following the target: eased on to it, then held on it as it moves.
                double[] want = TelescopeView.direction(target, mc.level, pt);
                double[] ws = new double[3];
                toSky(want, th, ws);
                double dot = ws[0] * AIM[0] + ws[1] * AIM[1] + ws[2] * AIM[2];
                double ang = Math.acos(Mth.clamp(dot, -1, 1));
                if (ang < Math.toRadians(fov) * 2) {
                    double f = Math.min(1, 0.15 + 0.85 * (ang < Math.toRadians(fov) * 0.02 ? 1 : 0.15));
                    for (int i = 0; i < 3; i++) {
                        AIM[i] += (ws[i] - AIM[i]) * f;
                    }
                    double l = Math.sqrt(AIM[0] * AIM[0] + AIM[1] * AIM[1] + AIM[2] * AIM[2]);
                    for (int i = 0; i < 3; i++) {
                        AIM[i] /= l;
                    }
                    toWorld(AIM, th, w);
                    ypr = yawPitch(w);
                    yaw = ypr[0];
                    pitch = ypr[1];
                }
            }
        } else {
            parkYaw = p.getYRot();
            parked = true;
        }
        viewYaw = yaw;
        viewPitch = pitch;
        p.setYRot(parkYaw);
        p.yRotO = parkYaw;
        p.setXRot(0F);
        p.xRotO = 0F;
        event.setYaw((float) yaw);
        event.setPitch((float) pitch);
        event.setRoll(0F);
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

    /** No block outline at the eyepiece (the player's own angles are parked, so it would be on the wrong block). */
    public static void onHighlight(net.neoforged.neoforge.client.event.RenderHighlightEvent.Block event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    public static void tick(Minecraft mc) {
        if (!active) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.player.position().distanceTo(start) > 2.5
                || !(mc.level.getBlockState(pos).getBlock() instanceof com.realweather.device.TelescopeBlock
                || mc.level.getBlockState(pos).getBlock() instanceof com.realweather.device.ObservatoryBlock)) {
            stop();
            return;
        }
        if (trackHold > 0) {
            trackHold--;
        }
        while (MODE_KEY.consumeClick()) {
            nextMode();
        }
        while (TRACK_KEY.consumeClick()) {
            toggleTracking();
        }
        if (FOCUS_IN.isDown()) {
            focus = Math.max(-6F, focus - 0.15F);
        }
        if (FOCUS_OUT.isDown()) {
            focus = Math.min(6F, focus + 0.15F);
        }
        while (AUTO_FOCUS.consumeClick()) {
            focus = 0F;
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
        // Infrared, ultraviolet, X-ray, focus; and in ULTRA KILLER the glow of the brightest things.
        SpaceShaders.bloom(0.55F);
        SpaceShaders.telescopeFilter(mode == SPECTRUM ? 0 : mode, Math.abs(focus) * 2.2F);
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
        double[] m = TelescopeView.lastView;
        double vx = m[0] * d[0] + m[3] * d[1] + m[6] * d[2], vy = m[1] * d[0] + m[4] * d[1] + m[7] * d[2],
                vz = m[2] * d[0] + m[5] * d[1] + m[8] * d[2];
        Component name = target.label();
        int color = 0xFFFFC040;
        boolean ahead = vz < 0;
        double sx = ahead ? (TelescopeView.lastPx * vx / -vz * 0.5 + 0.5) * w : -1, sy = ahead ? (1 - (TelescopeView.lastPy * vy / -vz * 0.5 + 0.5)) * h : -1;
        boolean onScreen = ahead && sx >= 8 && sx <= w - 8 && sy >= 8 && sy <= h - 8;
        if (onScreen) {
            int x = (int) Math.round(sx), y = (int) Math.round(sy), r = 7;
            bracket(g, x, y, r, color);
            g.drawString(mc.font, name, x + r + 4, y - 4, color, true);
        } else {
            // An arrow at the edge, pointing the way to turn.
            double ax = vx, ay = vy;
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
        boolean earth = target.sys == Catalog.earth;
        Component hint = earth ? Component.translatable("realweather.telescope.earth")
                : up < -0.02 ? Component.translatable("realweather.telescope.below") : Component.translatable("realweather.telescope.hint");
        g.drawString(mc.font, hint, 8, h - 18, earth ? 0xFF80E0FF : up < -0.02 ? 0xFFFF9070 : 0xFFB0B0B0, true);
        // Mode, tracking and focus.
        Component state = Component.translatable("realweather.telescope.state", Component.translatable("realweather.telescope.mode." + mode),
                Component.translatable(tracking ? "realweather.telescope.track_on" : "realweather.telescope.track_off"),
                focus == 0F ? Component.translatable("realweather.telescope.focus_auto") : Component.literal(String.format(java.util.Locale.ROOT, "%+.1f", focus)));
        g.drawString(mc.font, state, 8, 8, 0xFF90D0FF, true);
        if (mode == SPECTRUM) {
            Spectra.draw(g, target, w, h);
        }
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
