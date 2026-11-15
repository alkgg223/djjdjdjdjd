package com.vehicleworks.client;

import com.mojang.text2speech.Narrator;
import com.vehicleworks.Config;
import com.vehicleworks.VehicleWorks;
import com.vehicleworks.vehicle.ControlProfile;
import com.vehicleworks.vehicle.VehicleEntity;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Mod-only voice: speaks VehicleWorks callouts (gear, warnings, autopilot, ...) through the
 * system text-to-speech, independent of the vanilla narrator setting (which reads everything).
 */
public final class VoiceCallouts {
    private static Narrator narrator;
    private static boolean failed;
    private static int vehicleId = -1;
    private static boolean gear, ap, battery, lights, test, parking, running, stall, lowFuel, damaged, engineFail;
    private static long nextStall, nextPullUp, nextBank, nextGear;

    private VoiceCallouts() {}

    /** Master warning: true while any warning is active (cockpit lamp, red screen edge, alarm). */
    public static boolean warning;
    private static long nextAlarm;

    public static boolean blink() {
        return warning && (System.currentTimeMillis() / 350) % 2 == 0;
    }

    private static boolean computeWarning(VehicleTelemetry t) {
        boolean air = ControlProfile.byIndex(t.profile) == ControlProfile.AIRCRAFT || ControlProfile.byIndex(t.profile) == ControlProfile.HELICOPTER;
        if (t.emergencyTest || anyFailed(t) || t.integrity < 0.5f) return true;
        if (anyRunning(t) && t.fuelCapacity > 0 && t.fuel < 0.1f * t.fuelCapacity) return true;
        if (!air || t.onGround) return false;
        return t.stall || (t.agl < 60 && t.verticalSpeed < -12) || (!t.gearDown && t.agl < 25 && t.verticalSpeed < -2) || Math.abs(t.bank) > 70;
    }

    /** Alarm sound and warning state; runs every client tick while seated. */
    private static void master(VehicleTelemetry t) {
        warning = computeWarning(t);
        long now = System.currentTimeMillis();
        if (warning && now > nextAlarm) {
            nextAlarm = now + 900;
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    com.vehicleworks.registry.ModSounds.STALL_WARNING.get(), 1.25f, 0.9f));
        }
    }

    public static void reset() {
        warning = false;
    }

    public static boolean enabled() {
        return Config.CLIENT.voiceCallouts.get();
    }

    public static void toggle() {
        boolean on = !enabled();
        Config.CLIENT.voiceCallouts.set(on);
        Config.CLIENT_SPEC.save();
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(on ? "vehicleworks.voice.on" : "vehicleworks.voice.off"), true);
        if (on) say("vehicleworks.voice.on", true);
    }

    public static void say(String key, boolean interrupt) {
        if (failed) return;
        try {
            if (narrator == null) narrator = Narrator.getNarrator();
            if (narrator.active()) narrator.say(Component.translatable(key).getString(), interrupt);
        } catch (Throwable t) {
            failed = true;
            VehicleWorks.LOGGER.warn("Text-to-speech unavailable: " + t);
        }
    }

    private static boolean anyRunning(VehicleTelemetry t) {
        for (byte st : t.engineState) if (st == 2) return true;
        return false;
    }

    private static boolean anyFailed(VehicleTelemetry t) {
        for (byte st : t.engineState) if (st == 4) return true;
        return false;
    }

    public static void tick(VehicleEntity v) {
        VehicleTelemetry t = v.telemetry;
        master(t);
        boolean fuelLow = t.fuelCapacity > 0 && t.fuel < 0.1f * t.fuelCapacity;
        boolean dmg = t.integrity < 0.5f;
        if (v.getId() != vehicleId) {
            // new vehicle: remember the current state without speaking
            vehicleId = v.getId();
            gear = t.gearDown; ap = t.autopilot; battery = t.battery; lights = t.lights; test = t.emergencyTest; parking = t.parkingBrake;
            running = anyRunning(t); stall = t.stall; lowFuel = fuelLow; damaged = dmg; engineFail = anyFailed(t);
            return;
        }
        if (!enabled()) {
            gear = t.gearDown; ap = t.autopilot; battery = t.battery; lights = t.lights; test = t.emergencyTest; parking = t.parkingBrake;
            running = anyRunning(t); stall = t.stall; lowFuel = fuelLow; damaged = dmg; engineFail = anyFailed(t);
            return;
        }
        long now = System.currentTimeMillis();
        boolean air = ControlProfile.byIndex(t.profile) == ControlProfile.AIRCRAFT || ControlProfile.byIndex(t.profile) == ControlProfile.HELICOPTER;
        if (t.gearDown != gear) { gear = t.gearDown; say(gear ? "vehicleworks.voice.gear_down" : "vehicleworks.voice.gear_up", true); }
        if (t.autopilot != ap) { ap = t.autopilot; say(ap ? "vehicleworks.voice.ap_on" : "vehicleworks.voice.ap_off", true); }
        if (t.battery != battery) { battery = t.battery; say(battery ? "vehicleworks.voice.battery_on" : "vehicleworks.voice.battery_off", true); }
        if (t.lights != lights) { lights = t.lights; say(lights ? "vehicleworks.voice.lights_on" : "vehicleworks.voice.lights_off", true); }
        if (t.parkingBrake != parking) { parking = t.parkingBrake; say(parking ? "vehicleworks.voice.parking_on" : "vehicleworks.voice.parking_off", true); }
        if (t.emergencyTest != test) { test = t.emergencyTest; if (test) say("vehicleworks.voice.test", true); }
        boolean run = anyRunning(t);
        if (run != running) { running = run; say(run ? "vehicleworks.voice.engines_on" : "vehicleworks.voice.engines_off", false); }
        boolean fail = anyFailed(t);
        if (fail && !engineFail) say("vehicleworks.voice.engine_fail", true);
        engineFail = fail;
        if (fuelLow && !lowFuel) say("vehicleworks.voice.low_fuel", false);
        lowFuel = fuelLow;
        if (dmg && !damaged) say("vehicleworks.voice.damage", true);
        damaged = dmg;
        if (!air || t.onGround) { stall = false; return; }
        if (t.stall && now > nextStall) { nextStall = now + 3000; say("vehicleworks.voice.stall", true); }
        stall = t.stall;
        if (t.agl < 60 && t.verticalSpeed < -12 && now > nextPullUp) { nextPullUp = now + 2500; say("vehicleworks.voice.pull_up", true); }
        else if (!t.gearDown && t.agl < 25 && t.verticalSpeed < -2 && now > nextGear) { nextGear = now + 6000; say("vehicleworks.voice.too_low_gear", true); }
        if (Math.abs(t.bank) > 60 && now > nextBank) { nextBank = now + 5000; say("vehicleworks.voice.bank_angle", false); }
    }
}
