package com.vehicleworks.vehicle;

import com.vehicleworks.physics.sim.ControlInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Server-side pilot control state. The client only sends raw key states and its look
 * direction; levers (throttle, collective, flaps) and the profile-specific key mapping
 * live here, so the server stays authoritative.
 */
public final class VehicleControls {
    public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, JUMP = 16, SPRINT = 32,
            YAW_LEFT = 64, YAW_RIGHT = 128, THROTTLE_UP = 256, THROTTLE_DOWN = 512, PITCH_UP = 1024, PITCH_DOWN = 2048;
    public static final int BRAKE = 1 << 18;
    public static final int FIRE_SHIFT = 12;
    public static final int CHANNELS = 6;

    public int keys;
    public int prevKeys;
    public float lookYaw, lookPitch;
    public long lastInputTick = -1000;
    public double throttle;
    public double shipThrottle;
    public double collective;
    public double flaps;
    public boolean gearDown = true;
    public boolean armed;
    public boolean assist = true;
    public boolean mouseAim = false;
    public boolean afterburner;
    public boolean parkingBrake;
    public boolean autopilot;
    public double apHeading, apAltitude = 100;
    public double lift;
    /** automated tests: behave as if a pilot were seated */
    public boolean testPilot;
    /** smoothed stick axes (keyboard inputs ramp instead of jumping to full deflection) */
    public double axPitch, axRoll, axYaw;

    public boolean down(int bit) {
        return (keys & bit) != 0;
    }

    private boolean pressed(int bit) {
        return (keys & bit) != 0 && (prevKeys & bit) == 0;
    }

    public int fireBits() {
        return armed ? (keys >> FIRE_SHIFT) & ((1 << CHANNELS) - 1) : 0;
    }

    public void clearInput() {
        prevKeys = keys;
        keys = 0;
    }

    /** Translate key states into physical commands for the given control profile. */
    public void apply(ControlInput in, ControlProfile profile, boolean hasPilot, double dt) {
        in.neutral();
        float f = down(FORWARD) ? 1 : 0, b = down(BACK) ? 1 : 0, l = down(LEFT) ? 1 : 0, r = down(RIGHT) ? 1 : 0;
        float yl = down(YAW_LEFT) ? 1 : 0, yr = down(YAW_RIGHT) ? 1 : 0;
        float tu = down(THROTTLE_UP) ? 1 : 0, td = down(THROTTLE_DOWN) ? 1 : 0;
        float pu = down(PITCH_UP) ? 1 : 0, pd = down(PITCH_DOWN) ? 1 : 0;
        float jump = down(JUMP) ? 1 : 0, sprint = down(SPRINT) ? 1 : 0;
        in.assist = assist;
        in.gearDown = gearDown;
        in.flaps = flaps;
        in.afterburner = afterburner;
        in.brake = parkingBrake;
        switch (profile) {
            case AIRCRAFT, AUTO -> {
                double tp, tr, ty;
                in.autopilot = autopilot;
                in.apHeading = apHeading;
                in.apAltitude = apAltitude;
                if (mouseAim && hasPilot && !autopilot) {
                    in.mouseAim = true;
                    double yaw = Math.toRadians(lookYaw), pitch = Math.toRadians(lookPitch);
                    in.aimX = -Math.sin(yaw) * Math.cos(pitch);
                    in.aimY = -Math.sin(pitch);
                    in.aimZ = Math.cos(yaw) * Math.cos(pitch);
                    throttle += (f - b + tu - td + jump - sprint) * 0.5 * dt;
                    tp = pu - pd;
                } else {
                    tp = b - f + pu - pd;
                    throttle += (tu - td + jump - sprint) * 0.5 * dt;
                }
                tr = r - l;
                ty = yr - yl;
                axPitch = smooth(axPitch, tp, dt);
                axRoll = smooth(axRoll, tr, dt);
                axYaw = smooth(axYaw, ty, dt);
                in.pitch = axPitch;
                in.roll = axRoll;
                in.yaw = axYaw;
                in.steer = axYaw + 0.6 * axRoll;
                // wheel brakes automatically at idle throttle; afterburner when pushing past 100 %
                in.brake |= throttle < 0.04;
                if (throttle >= 0.999 && (tu > 0 || jump > 0)) in.afterburner = true;
                in.throttle = throttle = Mth.clamp(throttle, 0, 1);
            }
            case HELICOPTER -> {
                axPitch = smooth(axPitch, b - f + pu - pd, dt);
                axRoll = smooth(axRoll, r - l, dt);
                axYaw = smooth(axYaw, yr - yl, dt);
                in.pitch = axPitch;
                in.roll = axRoll;
                in.yaw = axYaw;
                collective = Mth.clamp(collective + (jump - sprint) * 0.45 * dt, 0, 1);
                in.climb = jump - sprint;
                // rotor governor runs to 100 % on its own once the engines run (F to idle it)
                throttle = td > 0 ? Math.max(0, throttle - 0.5 * dt) : Math.min(1, throttle + 0.4 * dt);
                in.collective = collective;
                in.throttle = throttle;
                in.brake |= collective < 0.05;
            }
            case CAR -> {
                if (f > 0 || b > 0) parkingBrake = false;
                in.brake = parkingBrake;
                in.drive = f - b;
                in.steer = r - l;
                in.handbrake = jump > 0;
                if (pressed(THROTTLE_UP)) in.gearShiftRequest = 1;
                if (pressed(THROTTLE_DOWN)) in.gearShiftRequest = -1;
                in.throttle = Math.abs(in.drive);
            }
            case SHIP, SUBMARINE, AIRSHIP -> {
                shipThrottle = Mth.clamp(shipThrottle + (f - b) * 0.45 * dt, -0.5, 1);
                if (Math.abs(shipThrottle) < 0.03 && f == 0 && b == 0) shipThrottle = 0;
                in.throttle = Math.abs(shipThrottle);
                in.reverseThrust = shipThrottle < 0;
                in.yaw = r - l + yr - yl;
                in.steer = r - l;
                in.roll = 0;
                if (profile == ControlProfile.SUBMARINE) {
                    in.ballast = sprint - jump;
                    in.pitch = pu - pd + tu - td;
                } else if (profile == ControlProfile.AIRSHIP) {
                    lift = Mth.clamp(lift + (jump - sprint) * 0.6 * dt, -1, 1);
                    if (jump == 0 && sprint == 0) lift *= Math.max(0, 1 - 0.8 * dt);
                    in.lift = jump > 0 ? 1 : (sprint > 0 ? -1 : 0);
                    in.pitch = pu - pd;
                } else {
                    in.pitch = pu - pd;
                }
                throttle = in.throttle;
            }
        }
        if (down(BRAKE)) { in.brake = true; in.hardBrake = true; }
        if (!hasPilot) {
            in.pitch = in.roll = in.yaw = 0;
            in.drive = in.steer = 0;
            in.brake = true;
        }
        prevKeys = keys;
    }

    private static double smooth(double v, double target, double dt) {
        double rate = (Math.abs(target) < 1e-3 ? 4.0 : 2.2) * dt;
        return v < target ? Math.min(target, v + rate) : Math.max(target, v - rate);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("Throttle", throttle);
        t.putDouble("ShipThrottle", shipThrottle);
        t.putDouble("Collective", collective);
        t.putDouble("Flaps", flaps);
        t.putBoolean("Gear", gearDown);
        t.putBoolean("Assist", assist);
        t.putBoolean("MouseAim", mouseAim);
        t.putBoolean("Parking", parkingBrake);
        t.putBoolean("AP", autopilot);
        t.putDouble("ApHdg", apHeading);
        t.putDouble("ApAlt", apAltitude);
        return t;
    }

    public void load(CompoundTag t) {
        throttle = t.getDouble("Throttle");
        shipThrottle = t.getDouble("ShipThrottle");
        collective = t.getDouble("Collective");
        flaps = t.getDouble("Flaps");
        gearDown = !t.contains("Gear") || t.getBoolean("Gear");
        assist = !t.contains("Assist") || t.getBoolean("Assist");
        mouseAim = !t.contains("MouseAim") || t.getBoolean("MouseAim");
        parkingBrake = t.getBoolean("Parking");
        autopilot = t.getBoolean("AP");
        apHeading = t.getDouble("ApHdg");
        apAltitude = t.contains("ApAlt") ? t.getDouble("ApAlt") : 100;
    }
}
