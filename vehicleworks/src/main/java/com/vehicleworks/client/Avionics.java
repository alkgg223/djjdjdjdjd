package com.vehicleworks.client;

import com.vehicleworks.Config;
import com.vehicleworks.registry.ModSounds;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Staged electrical power-up of an aircraft, as the client shows it. With the bus powered the
 * aircraft "wakes up" step by step (fraction of the configured start-up duration D):
 * <pre>
 *  0.00  bus relay closes: power annunciators light
 *  0.08  panel backlight ramps up
 *  0.18  avionics modules start (fans spin up): displays show a faint glow
 *  0.30  boot logo on every display
 *  0.48  display self test (test pattern, progress)
 *  0.68  flight and navigation data appear one block after the other
 *  0.88  HUD aligned and shown
 *  1.00  systems ready (green annunciators, chime)
 * </pre>
 * Removing power fades displays and backlight out over ~0.7 s instead of cutting them.
 */
public final class Avionics {
    private Avionics() {}

    public static final int OFF = 0, GLOW = 1, LOGO = 2, TEST = 3, DATA = 4, READY = 5;

    public static final class State {
        boolean seen, lastBus, lastAv;
        /** client time (ms) when the bus / the avionics got power, and when power was lost */
        long busOn = Long.MIN_VALUE / 4, avOn = Long.MIN_VALUE / 4, busOff = Long.MIN_VALUE / 4, avOff = Long.MIN_VALUE / 4;
        int soundStage = -1;
    }

    static State of(VehicleEntity v) {
        VehicleClientState s = (VehicleClientState) v.clientState;
        if (s == null) return new State();
        if (s.avionics == null) s.avionics = new State();
        return s.avionics;
    }

    public static double duration() {
        return Config.CLIENT.startupAnimationSeconds.get() * 1000.0;
    }

    /** Track power transitions (every client tick) and play the start-up sounds of each stage. */
    public static void tick(VehicleEntity v) {
        State a = of(v);
        VehicleTelemetry t = v.telemetry;
        long now = System.currentTimeMillis();
        boolean bus = t.busPowered, av = t.avionicsPowered();
        if (!a.seen) {
            // first telemetry: if the bus has been on for a while the aircraft is already awake
            a.seen = true;
            long age = (long) t.powerAge * 50L;
            if (bus) { a.busOn = now - age; a.avOn = now - age; }
            a.lastBus = bus;
            a.lastAv = av;
            a.soundStage = bus && age > duration() ? 99 : -1;
            return;
        }
        if (bus && !a.lastBus) { a.busOn = now; a.soundStage = -1; }
        if (!bus && a.lastBus) a.busOff = now;
        if (av && !a.lastAv) a.avOn = now;
        if (!av && a.lastAv) a.avOff = now;
        a.lastBus = bus;
        a.lastAv = av;
        if (!bus) return;
        // stage sounds, heard in the cockpit only
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(v) > 40 * 40) return;
        double fb = (now - a.busOn) / duration(), fa = (now - a.avOn) / duration();
        if (a.soundStage < 0 && fb >= 0) { play(v, ModSounds.RELAY.get(), 0.9f, 0.8f); a.soundStage = 0; }
        if (a.soundStage < 1 && av && fa >= 0.18) { play(v, ModSounds.AVIONICS.get(), 0.8f, 1f); a.soundStage = 1; }
        if (a.soundStage < 2 && av && fa >= 0.48) { play(v, ModSounds.BUTTON.get(), 0.4f, 1.6f); a.soundStage = 2; }
        if (a.soundStage < 3 && av && fa >= 1.0) { play(v, ModSounds.IFE_CHIME.get(), 0.5f, 1.25f); a.soundStage = 3; }
    }

    static void play(VehicleEntity v, SoundEvent e, float vol, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        float k = Config.CLIENT.cockpitVolume.get().floatValue();
        if (k <= 0) return;
        mc.level.playLocalSound(v.getX(), v.getY(), v.getZ(), e, SoundSource.NEUTRAL, vol * k, pitch, false);
    }

    private static float fade(long offMs) {
        // power lost: brightness collapses over 0.7 s
        float d = (System.currentTimeMillis() - offMs) / 700f;
        return d >= 1 ? 0 : (1 - d) * (1 - d);
    }

    /** Panel backlight / backlit labels level 0..1 (before the dimmer). */
    public static float backlight(VehicleEntity v) {
        State a = of(v);
        if (!v.telemetry.busPowered) return fade(a.busOff) * 0.6f;
        float f = (float) ((System.currentTimeMillis() - a.busOn) / duration());
        return Mth.clamp((f - 0.08f) / 0.1f, 0, 1);
    }

    /** Power annunciators (battery, generator, bus): light as soon as the relay closes. */
    public static boolean annunciatorsPowered(VehicleEntity v) {
        return v.telemetry.busPowered;
    }

    /** start-up progress of the avionics 0..1+ (time since they got power / duration) */
    public static float progress(VehicleEntity v) {
        State a = of(v);
        if (!v.telemetry.avionicsPowered()) return 0;
        return (float) ((System.currentTimeMillis() - a.avOn) / duration());
    }

    public static int stage(VehicleEntity v) {
        if (!v.telemetry.avionicsPowered()) return OFF;
        float f = progress(v);
        if (f < 0.18f) return OFF;
        if (f < 0.30f) return GLOW;
        if (f < 0.48f) return LOGO;
        if (f < 0.68f) return TEST;
        if (f < 1.0f) return DATA;
        return READY;
    }

    /** Display brightness envelope 0..1: ramps up while booting, fades out when power goes. */
    public static float display(VehicleEntity v) {
        State a = of(v);
        if (!v.telemetry.avionicsPowered()) return fade(Math.max(a.avOff, a.busOff)) * 0.8f;
        float f = progress(v);
        return Mth.clamp((f - 0.18f) / 0.35f, 0, 1);
    }

    /** True while showing the last frame of a display that just lost power (fading out). */
    public static boolean fadingOut(VehicleEntity v) {
        return !v.telemetry.avionicsPowered() && display(v) > 0.01f;
    }

    /** Data reveal 0..1 during the DATA stage (1 when ready). */
    public static float reveal(VehicleEntity v) {
        if (fadingOut(v)) return 1;
        float f = progress(v);
        return Mth.clamp((f - 0.68f) / 0.2f, 0, 1);
    }

    /** HUD opacity 0..1 (aligned at 0.88 of the sequence). */
    public static float hud(VehicleEntity v) {
        if (!v.telemetry.avionicsPowered()) return fade(of(v).avOff);
        return Mth.clamp((progress(v) - 0.88f) / 0.1f, 0, 1);
    }

    /** Brightness multiplier of displays from the cockpit knob, night mode and the config. */
    public static float displayBrightness(VehicleTelemetry t) {
        float knob = 0.35f + 0.65f * Mth.clamp(t.displayBrt, 1, 5) / 5f;
        if (t.sw(com.vehicleworks.vehicle.VehicleControls.SW_NIGHT)) knob *= 0.6f;
        return knob * Config.CLIENT.instrumentBrightness.get().floatValue();
    }

    /** Backlight multiplier (dimmer knob, night mode, config). */
    public static float dimmer(VehicleTelemetry t) {
        float d = Mth.clamp(t.dimmer, 0, 4) / 4f;
        if (t.sw(com.vehicleworks.vehicle.VehicleControls.SW_NIGHT)) d *= 0.55f;
        return d * Config.CLIENT.cockpitBrightness.get().floatValue();
    }
}
