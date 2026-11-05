package com.gunsmod.client;

import com.gunsmod.Reg;
import com.gunsmod.entity.DroneEntity;
import com.gunsmod.net.Net;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Flying a drone from its camera. The view moves into the drone; the player's body stays where it stands (and can be
 * shot). Movement keys fly it (jump up, sneak down, sprint faster), the mouse aims the camera, attack / use are its
 * buttons, reload sends it home, the seat key lets go, the zoom key zooms and the horn key turns the thermal camera
 * on. The on-screen display shows battery, link, height, speed, heading and the distance back to the operator.
 */
public final class DroneClient {
    private DroneClient() {}

    private static int pilotId = -1, pendingId = -1, pendingTicks, staticTicks, endState;
    private static float linkRange = 300;
    private static CameraType prevCamera;
    private static int zoom;
    private static final float[] ZOOMS = {1f, 2f, 4f, 8f};
    private static boolean thermal;
    // the movement keys this tick (taken from the player, who stands still meanwhile)
    private static float forward, strafe;
    private static boolean up, down;
    /** the enemies the side has spotted (outlined) */
    public static final Set<Integer> SPOTTED = new HashSet<>();

    public static boolean piloting() {
        return pilotId >= 0;
    }

    public static boolean thermalOn() {
        return piloting() && thermal;
    }

    public static float zoom() {
        return piloting() ? ZOOMS[zoom] : 1f;
    }

    public static DroneEntity drone() {
        Minecraft mc = Minecraft.getInstance();
        if (pilotId < 0 || mc.level == null) return null;
        return mc.level.getEntity(pilotId) instanceof DroneEntity d ? d : null;
    }

    public static void pilot(Net.DronePilot p) {
        if (p.state() == DroneEntity.LINK_START) {
            pendingId = p.id();
            pendingTicks = 60;
            linkRange = Math.max(32f, p.range());
            return;
        }
        if (p.id() == pilotId) stop(p.state());
        if (p.id() == pendingId) pendingId = -1;
    }

    public static void spotted(Net.Spotted p) {
        SPOTTED.clear();
        for (int id : p.ids()) SPOTTED.add(id);
    }

    private static void begin(DroneEntity d) {
        Minecraft mc = Minecraft.getInstance();
        pilotId = d.getId();
        pendingId = -1;
        prevCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(d);
        zoom = 0;
        thermal = false;
        staticTicks = 0;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Reg.sound("ui_confirm"), 1.3f, 0.6f));
    }

    private static void stop(int state) {
        Minecraft mc = Minecraft.getInstance();
        DroneEntity d = drone();
        if (d != null) d.localPilot = false;
        if (pilotId >= 0 && mc.player != null) {
            mc.setCameraEntity(mc.player);
            if (prevCamera != null) mc.options.setCameraType(prevCamera);
            endState = state;
            if (state != DroneEntity.LINK_LANDED && state != -1) {
                staticTicks = 30;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(Reg.sound("ui_drone_static"), 1f, 0.8f));
            }
        }
        pilotId = -1;
    }

    /** the player's movement input while flying: kept for the drone, taken away from the body */
    public static void input(Input in) {
        if (!piloting()) return;
        forward = in.forwardImpulse;
        strafe = in.leftImpulse;
        up = in.jumping;
        down = in.shiftKeyDown;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
    }

    private static void send(int action) {
        if (pilotId >= 0) PacketDistributor.sendToServer(new Net.DroneAction(pilotId, action));
    }

    /** the attack / use buttons while flying */
    public static boolean button(boolean attack) {
        if (!piloting()) return false;
        DroneEntity d = drone();
        send(attack ? DroneEntity.A_PRIMARY : DroneEntity.A_SECONDARY);
        Minecraft mc = Minecraft.getInstance();
        if (d != null && (d.kind() == DroneEntity.Kind.RECON || d.kind() == DroneEntity.Kind.SURVEIL || !attack)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Reg.sound("ui_drone_spot"), 1f, 0.8f));
        } else if (d != null && d.kind() == DroneEntity.Kind.LANCET) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Reg.sound("ui_drone_lock"), 1f, 0.8f));
        }
        return true;
    }

    /** every client tick, before the guns (so the keys go to the drone) */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (staticTicks > 0) staticTicks--;
        if (mc.level == null || mc.player == null) {
            pilotId = pendingId = -1;
            SPOTTED.clear();
            return;
        }
        if (pendingId >= 0) {
            if (mc.level.getEntity(pendingId) instanceof DroneEntity d) begin(d);
            else if (--pendingTicks <= 0) pendingId = -1;
        }
        if (pilotId < 0) return;
        DroneEntity d = drone();
        if (d == null || !d.isAlive() || d.isRemoved() || !mc.player.isAlive()) {
            stop(DroneEntity.LINK_DESTROYED);
            return;
        }
        while (GunClient.SEAT.consumeClick()) {
            send(DroneEntity.A_EXIT);
            stop(-1);
            return;
        }
        while (GunClient.RELOAD.consumeClick()) send(DroneEntity.A_HOME);
        while (GunClient.FLARES.consumeClick()) button(true);
        while (GunClient.SCOPE_ZOOM.consumeClick()) zoom = (zoom + 1) % ZOOMS.length;
        while (GunClient.HORN.consumeClick()) thermal = !thermal;
        boolean boost = mc.options.keySprint.isDown();
        d.localPilot = d.mode() == DroneEntity.MANUAL && d.kind() != DroneEntity.Kind.SURVEIL;
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        if (d.localPilot) d.pilotStep(new DroneEntity.PilotInput(forward, strafe, up, down, boost, yaw, pitch));
        PacketDistributor.sendToServer(new Net.DroneControl(d.getId(), d.getX(), d.getY(), d.getZ(), yaw, pitch));
        if (mc.getCameraEntity() != d) mc.setCameraEntity(d);
        forward = strafe = 0;
        up = down = false;
    }

    /** the camera looks where the mouse points (not where the drone's body is turned); quads lean with their flight */
    public static void camera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        DroneEntity d = drone();
        if (d == null || mc.player == null) return;
        float pt = (float) e.getPartialTick();
        e.setYaw(mc.player.getViewYRot(pt));
        e.setPitch(mc.player.getViewXRot(pt));
        float roll = Mth.lerp(pt, d.prevVisRoll, d.visRoll);
        e.setRoll(d.kind().quad() ? -roll * 0.35f : -roll * 0.6f);
    }

    // ------------------------------------------------------------------------------------ on-screen display
    private static final int OSD = 0xFFE8F4E8, OSD_DIM = 0xA0E8F4E8, WARN = 0xFFFF5040, AMBER = 0xFFFFC040;

    private static String key(KeyMapping k) {
        return k.getTranslatedKeyMessage().getString();
    }

    public static void layer(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        long t = mc.level == null ? 0 : mc.level.getGameTime();
        if (!piloting()) {
            if (staticTicks > 0) {
                noise(g, w, h, 1f, t);
                Component c = Component.translatable(endState == DroneEntity.LINK_LOST ? "gunsmod.drone.link_lost" : "gunsmod.drone.signal_lost");
                g.drawString(f, c, (w - f.width(c)) / 2, h / 2 - 4, WARN, true);
            }
            return;
        }
        DroneEntity d = drone();
        if (d == null || mc.player == null) return;
        DroneEntity.Kind k = d.kind();
        // scan lines and the link noise
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x0C000000);
        double dist = d.position().distanceTo(mc.player.position());
        float signal = (float) Mth.clamp(1 - dist / linkRange, 0, 1);
        if (signal < 0.25f) noise(g, w, h, (0.25f - signal) * 2.4f, t);
        // corners of the picture
        int m = 10;
        for (int[] c : new int[][]{{m, m, 1, 1}, {w - m, m, -1, 1}, {m, h - m, 1, -1}, {w - m, h - m, -1, -1}}) {
            g.fill(Math.min(c[0], c[0] + c[2] * 18), c[1], Math.max(c[0], c[0] + c[2] * 18), c[1] + c[3], OSD_DIM);
            g.fill(c[0], Math.min(c[1], c[1] + c[3] * 18), c[0] + c[2], Math.max(c[1], c[1] + c[3] * 18), OSD_DIM);
        }
        // reticle
        int cx = w / 2, cy = h / 2;
        if (k == DroneEntity.Kind.LANCET || k == DroneEntity.Kind.FPV) {
            g.fill(cx - 14, cy, cx - 4, cy + 1, OSD);
            g.fill(cx + 5, cy, cx + 15, cy + 1, OSD);
            g.fill(cx, cy - 10, cx + 1, cy - 3, OSD);
            g.fill(cx, cy + 4, cx + 1, cy + 11, OSD);
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, WARN);
        } else {
            g.fill(cx - 8, cy, cx - 2, cy + 1, OSD);
            g.fill(cx + 3, cy, cx + 9, cy + 1, OSD);
            g.fill(cx, cy - 8, cx + 1, cy - 2, OSD);
            g.fill(cx, cy + 3, cx + 1, cy + 9, OSD);
            int b = 22;
            g.fill(cx - b, cy - b, cx - b + 6, cy - b + 1, OSD_DIM);
            g.fill(cx - b, cy - b, cx - b + 1, cy - b + 6, OSD_DIM);
            g.fill(cx + b - 5, cy - b, cx + b + 1, cy - b + 1, OSD_DIM);
            g.fill(cx + b, cy - b, cx + b + 1, cy - b + 6, OSD_DIM);
            g.fill(cx - b, cy + b, cx - b + 6, cy + b + 1, OSD_DIM);
            g.fill(cx - b, cy + b - 5, cx - b + 1, cy + b + 1, OSD_DIM);
            g.fill(cx + b - 5, cy + b, cx + b + 1, cy + b + 1, OSD_DIM);
            g.fill(cx + b, cy + b - 5, cx + b + 1, cy + b + 1, OSD_DIM);
        }
        // top left: what it is, the mode, recording
        Component name = Component.translatable("item.gunsmod." + itemId(k));
        g.drawString(f, name, 16, 16, OSD, true);
        String mode = switch (d.mode()) {
            case DroneEntity.HOME -> "gunsmod.drone.mode.home";
            case DroneEntity.AUTO -> k == DroneEntity.Kind.LANCET ? "gunsmod.drone.mode.locked" : "gunsmod.drone.mode.auto";
            default -> k == DroneEntity.Kind.SURVEIL ? "gunsmod.drone.mode.orbit" : "gunsmod.drone.mode.manual";
        };
        g.drawString(f, Component.translatable(mode), 16, 27, d.mode() == DroneEntity.HOME || d.mode() == DroneEntity.AUTO ? AMBER : OSD_DIM, true);
        if ((t / 10) % 2 == 0) g.fill(16, 40, 21, 45, WARN);
        g.drawString(f, "REC", 24, 39, OSD, true);
        if (thermal) g.drawString(f, Component.translatable("gunsmod.drone.thermal"), 50, 39, OSD, true);
        if (zoom > 0) g.drawString(f, String.format("x%.0f", ZOOMS[zoom]), 16, 51, OSD, true);
        // top right: battery and link
        float bat = d.batteryLeft();
        int secs = d.batteryTicks() / 20;
        String bs = String.format("%d%%  %d:%02d", Math.round(bat * 100), secs / 60, secs % 60);
        int bx = w - 16 - 44;
        g.fill(bx, 16, bx + 40, 24, 0x80000000);
        g.fill(bx + 40, 18, bx + 42, 22, OSD_DIM);
        int bc = bat < 0.15f ? WARN : bat < 0.3f ? AMBER : 0xFF7FE07F;
        if (!(bat < 0.15f && (t / 6) % 2 == 0)) g.fill(bx + 1, 17, bx + 1 + Math.round(38 * bat), 23, bc);
        g.drawString(f, bs, bx - 6 - f.width(bs), 16, bat < 0.15f ? WARN : OSD, true);
        int bars = Math.round(signal * 5);
        for (int i = 0; i < 5; i++) {
            int bh = 3 + i * 2;
            g.fill(w - 16 - 30 + i * 6, 40 - bh, w - 16 - 26 + i * 6, 40, i < bars ? (signal < 0.25f ? WARN : OSD) : 0x40FFFFFF);
        }
        g.drawString(f, Component.translatable("gunsmod.drone.link"), w - 16 - 34 - f.width(Component.translatable("gunsmod.drone.link")), 31, OSD_DIM, true);
        // heading tape
        float yaw = Mth.wrapDegrees(mc.player.getYRot() + 180f);
        int tapeW = Math.min(180, w / 3);
        int base = Math.round(yaw / 5f) * 5;
        for (int a = -65; a <= 65; a += 5) {
            int hd = Math.floorMod(base + a, 360);
            int x = cx + Math.round(Mth.wrapDegrees(hd - yaw) * tapeW / 120f);
            if (x < cx - tapeW / 2 || x > cx + tapeW / 2) continue;
            boolean major = hd % 45 == 0;
            g.fill(x, 14, x + 1, major ? 20 : 17, OSD_DIM);
            if (major) {
                String l = switch (hd) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    case 270 -> "W";
                    default -> String.valueOf(hd);
                };
                g.drawString(f, l, x - f.width(l) / 2, 22, OSD, true);
            }
        }
        g.fill(cx, 11, cx + 1, 21, AMBER);
        // bottom left: height, speed, distance home
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(d.getX()), Mth.floor(d.getZ()));
        Vec3 mv = d.position().subtract(d.xo, d.yo, d.zo);
        int ly = h - 52;
        g.drawString(f, Component.translatable("gunsmod.drone.alt", Math.round(d.getY() - ground), Math.round(d.getY())), 16, ly, OSD, true);
        g.drawString(f, Component.translatable("gunsmod.drone.speed", Math.round(mv.length() * 20 * 3.6)), 16, ly + 11, OSD, true);
        g.drawString(f, Component.translatable("gunsmod.drone.home", Math.round(dist), Math.round(linkRange)), 16, ly + 22, signal < 0.25f ? WARN : OSD, true);
        if (k == DroneEntity.Kind.BOMBER) {
            Component c = Component.translatable("gunsmod.drone.bombs", d.bombs());
            g.drawString(f, c, w - 16 - f.width(c), ly, d.bombs() > 0 ? OSD : WARN, true);
            for (int i = 0; i < DroneEntity.BOMBS; i++) g.fill(w - 16 - (i + 1) * 8, ly + 12, w - 16 - (i + 1) * 8 + 5, ly + 20, i < d.bombs() ? 0xFF9AB070 : 0x40FFFFFF);
        }
        // the key hints
        String primary = switch (k) {
            case BOMBER -> "gunsmod.drone.key.drop";
            case FPV -> "gunsmod.drone.key.detonate";
            case LANCET -> "gunsmod.drone.key.lock";
            default -> "gunsmod.drone.key.spot";
        };
        Component hints = Component.translatable(primary, key(mc.options.keyAttack))
                .append("   ").append(Component.translatable(k == DroneEntity.Kind.SURVEIL ? "gunsmod.drone.key.orbit" : "gunsmod.drone.key.spot", key(mc.options.keyUse)))
                .append("   ").append(k.reusable() ? Component.translatable("gunsmod.drone.key.home", key(GunClient.RELOAD)).append("   ") : Component.empty())
                .append(Component.translatable(k.reusable() ? "gunsmod.drone.key.exit" : "gunsmod.drone.key.exit_boom", key(GunClient.SEAT)));
        Component hints2 = Component.translatable("gunsmod.drone.key.fly", key(mc.options.keyJump), key(mc.options.keyShift), key(mc.options.keySprint))
                .append("   ").append(Component.translatable("gunsmod.drone.key.zoom", key(GunClient.SCOPE_ZOOM)))
                .append("   ").append(Component.translatable("gunsmod.drone.key.thermal", key(GunClient.HORN)));
        g.drawString(f, hints, (w - f.width(hints)) / 2, h - 26, OSD_DIM, true);
        g.drawString(f, hints2, (w - f.width(hints2)) / 2, h - 15, OSD_DIM, true);
        // warnings
        if (d.mode() == DroneEntity.HOME) center(g, f, Component.translatable("gunsmod.drone.returning"), cy + 30, AMBER);
        else if (bat < 0.15f) center(g, f, Component.translatable("gunsmod.drone.low_battery"), cy + 30, WARN);
        if (signal < 0.25f && (t / 8) % 2 == 0) center(g, f, Component.translatable("gunsmod.drone.weak_link"), cy + 42, WARN);
        if (mc.player.hurtTime > 0) {
            g.fill(0, 0, w, 3, 0xC0FF2020);
            g.fill(0, h - 3, w, h, 0xC0FF2020);
            g.fill(0, 0, 3, h, 0xC0FF2020);
            g.fill(w - 3, 0, w, h, 0xC0FF2020);
            center(g, f, Component.translatable("gunsmod.drone.operator_hit"), cy - 40, WARN);
        }
    }

    private static String itemId(DroneEntity.Kind k) {
        return switch (k) {
            case FPV -> "fpv_drone";
            case SHAHED -> "shahed";
            case RECON -> "recon_drone";
            case BOMBER -> "bomber_drone";
            case SURVEIL -> "surveillance_drone";
            case LANCET -> "lancet";
        };
    }

    private static void center(GuiGraphics g, Font f, Component c, int y, int col) {
        g.drawString(f, c, (g.guiWidth() - f.width(c)) / 2, y, col, true);
    }

    /** video noise: grey speckles over the picture, as many as the link is bad */
    private static void noise(GuiGraphics g, int w, int h, float amount, long t) {
        int n = (int) (Math.min(1f, amount) * 1600);
        long seed = t * 7919L;
        for (int i = 0; i < n; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) ((seed >>> 33) % w), y = (int) ((seed >>> 13) % h);
            int v = (int) ((seed >>> 5) & 0xFF);
            g.fill(x, y, x + 2, y + 1, 0xB0000000 | v << 16 | v << 8 | v);
        }
        if (amount > 0.6f) {
            int band = (int) ((t * 13) % h);
            g.fill(0, band, w, band + 6, 0x50FFFFFF);
        }
    }
}
