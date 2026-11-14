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
    public boolean mouseAim = true;
    public boolean afterburner;
    public boolean parkingBrake;
    public double lift;
    /** automated tests: behave as if a pilot were seated */
    public boolean testPilot;

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
                if (mouseAim && hasPilot) {
                    in.mouseAim = true;
                    double yaw = Math.toRadians(lookYaw), pitch = Math.toRadians(lookPitch);
                    in.aimX = -Math.sin(yaw) * Math.cos(pitch);
                    in.aimY = -Math.sin(pitch);
                    in.aimZ = Math.cos(yaw) * Math.cos(pitch);
                    throttle += (f - b + tu - td) * 0.5 * dt;
                    in.pitch = pu - pd;
                } else {
                    in.pitch = b - f + pu - pd;
                    throttle += (tu - td) * 0.5 * dt;
                }
                in.roll = r - l;
                in.yaw = yr - yl;
                in.brake |= jump > 0;
                in.steer = in.yaw;
                if (sprint > 0) in.afterburner = true;
                in.throttle = throttle = Mth.clamp(throttle, 0, 1);
            }
            case HELICOPTER -> {
                in.pitch = b - f + pu - pd;
                in.roll = r - l;
                in.yaw = yr - yl;
                collective = Mth.clamp(collective + (jump - sprint) * 0.45 * dt, 0, 1);
                throttle = Mth.clamp(throttle + (tu - td) * 0.5 * dt, 0, 1);
                in.collective = collective;
                in.throttle = throttle;
                in.brake |= collective < 0.05;
            }
            case CAR -> {
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
        if (!hasPilot) {
            in.pitch = in.roll = in.yaw = 0;
            in.drive = in.steer = 0;
            in.brake = true;
        }
        prevKeys = keys;
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
    }
}
