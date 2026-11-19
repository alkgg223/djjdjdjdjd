package com.vehicleworks.client;

import com.vehicleworks.registry.ModSounds;
import com.vehicleworks.vehicle.ControlProfile;
import com.vehicleworks.vehicle.VehicleTelemetry;
import net.minecraft.sounds.SoundEvent;

import java.util.EnumSet;
import java.util.function.Supplier;

/** The cockpit warning list. Each group of 1–3 warnings has its own alarm sound. */
public final class Warnings {
    public enum Sound {
        SHAKER(ModSounds.ALARM_STALL::get, 800), CLACKER(ModSounds.ALARM_OVERSPEED::get, 1000), CHIME(ModSounds.ALARM_CAUTION::get, 2500),
        BELL(ModSounds.ALARM_FIRE::get, 1000), WHOOP(ModSounds.ALARM_WHOOP::get, 950), TRIPLE(ModSounds.ALARM_WARNING::get, 1800),
        HORN(ModSounds.GEAR_HORN::get, 1000);
        public final Supplier<SoundEvent> event;
        public final long periodMs;
        Sound(Supplier<SoundEvent> e, long p) { event = e; periodMs = p; }
    }

    /** In priority order (first = most urgent). red = warning, otherwise amber caution. */
    public enum W {
        ENGINE_FIRE(Sound.BELL, true), PULL_UP(Sound.WHOOP, true), TERRAIN(Sound.WHOOP, true), STALL(Sound.SHAKER, true),
        ENGINE_FAILURE(Sound.BELL, true), OVERSPEED(Sound.CLACKER, true), GEAR(Sound.HORN, true), LOW_ALTITUDE(Sound.WHOOP, false),
        HYDRAULIC(Sound.TRIPLE, true), ELECTRICAL(Sound.TRIPLE, true), CABIN_PRESSURE(Sound.TRIPLE, true), GEAR_JAMMED(Sound.CHIME, false),
        FUEL_LEAK(Sound.CHIME, false), GENERATOR(Sound.CHIME, false), LOW_FUEL(Sound.CHIME, false), LOW_OIL(Sound.CHIME, false);
        public final Sound sound;
        public final boolean red;
        W(Sound s, boolean red) { sound = s; this.red = red; }
        public String key() { return "vehicleworks.warning." + name().toLowerCase(java.util.Locale.ROOT); }
    }

    private Warnings() {}

    public static EnumSet<W> compute(VehicleTelemetry t) {
        EnumSet<W> s = EnumSet.noneOf(W.class);
        ControlProfile p = ControlProfile.byIndex(t.profile);
        boolean air = p == ControlProfile.AIRCRAFT || p == ControlProfile.HELICOPTER;
        boolean running = false;
        for (int i = 0; i < t.engineState.length; i++) {
            byte st = t.engineState[i];
            float dmg = i < t.engineDamage.length ? t.engineDamage[i] : 0;
            if (st == 4) s.add(W.ENGINE_FAILURE);
            if (st == 2) running = true;
            if (st != 0 && dmg > 0.6f) s.add(W.ENGINE_FIRE);
            else if (st == 2 && dmg > 0.3f) s.add(W.LOW_OIL);
        }
        if (t.fuelCapacity > 0 && t.fuel < 0.1f * t.fuelCapacity && t.engineState.length > 0) s.add(W.LOW_FUEL);
        if (t.fail(com.vehicleworks.vehicle.VehicleTelemetry.FAIL_FUEL)) s.add(W.FUEL_LEAK);
        if (t.fail(com.vehicleworks.vehicle.VehicleTelemetry.FAIL_GEN) && running) s.add(W.GENERATOR);
        if (t.fail(com.vehicleworks.vehicle.VehicleTelemetry.FAIL_GEAR)) s.add(W.GEAR_JAMMED);
        if (t.fail(com.vehicleworks.vehicle.VehicleTelemetry.FAIL_PRESS) && t.altitude > 3000) s.add(W.CABIN_PRESSURE);
        if (!air) return s;
        boolean flying = !t.onGround && t.agl > 8 && t.airspeed > 12;
        if (!flying) return s;
        if (t.stall) s.add(W.STALL);
        if (t.vne > 0 && t.airspeed > t.vne) s.add(W.OVERSPEED);
        if (t.hydraulics < 0.75f) s.add(W.HYDRAULIC);
        if (!t.battery) s.add(W.ELECTRICAL);
        float vs = t.verticalSpeed;
        double tti = vs < -0.5f ? t.agl / -vs : Double.MAX_VALUE; // seconds to impact
        if (t.agl < 80 && (tti < 6 || vs < -15)) s.add(W.PULL_UP);
        else if (t.agl < 200 && tti < 15) s.add(W.TERRAIN);
        if (p == ControlProfile.AIRCRAFT && !t.gearDown) {
            if (t.agl < 60 && vs < -1) s.add(W.GEAR);
            else if (t.agl < 40) s.add(W.LOW_ALTITUDE);
        } else if (p == ControlProfile.AIRCRAFT && t.agl < 15 && vs < -6) s.add(W.LOW_ALTITUDE);
        return s;
    }
}
