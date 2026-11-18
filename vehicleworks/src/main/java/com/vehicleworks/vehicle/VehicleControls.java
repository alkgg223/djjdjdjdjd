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
    public boolean battery = true, lights;
    /** cockpit switches (lights = navigation lights) */
    public boolean avionics = true, fuelPump, ignition, beacon, strobe, landingLights, taxiLights, turnoffLights;
    /** 0 both, 1 left, 2 right, 3 off */
    public int fuelSelector;
    /** cockpit flood light and panel backlight: 0 off, 1 dim, 2 bright */
    public int cabinLight, panelLight = 1;
    /** battery state of charge 0..1 */
    public double batteryCharge = 1;
    /** electrical bus powered this tick (battery on and charged, or generator) */
    public boolean busPowered = true, generator;
    /** master electrical (bus) switch, generator / alternator switch, engine master (fuel to the engines) */
    public boolean masterElec = true, generatorSw = true, engineMaster = true;
    /** instrument / label dimmer 0..4, display brightness 1..5, night lighting mode, console lights */
    public int dimmer = 3, displayBrt = 4;
    public boolean nightMode, consoleLight;
    /** starter engaged (server ticks left; turbines cut out on their own) */
    public int starterTicks;
    /** set once the vehicle got its first switch configuration (new aircraft start cold and dark) */
    public boolean initialized;
    /** last time the bus went from unpowered to powered (game tick), for the staged power-up */
    public long powerOnTick = -100000;
    public static final int SW_BATTERY = 0, SW_AVIONICS = 1, SW_FUEL_PUMP = 2, SW_IGNITION = 3, SW_STARTER = 4, SW_FUEL_SEL = 5,
            SW_NAV = 6, SW_BEACON = 7, SW_STROBE = 8, SW_LANDING = 9, SW_TAXI = 10, SW_TURNOFF = 11,
            SW_CABIN = 12, SW_PANEL = 13, SW_GEAR = 14, SW_TEST = 15, SW_AUTOPILOT = 16, SW_PARKING = 17,
            SW_MASTER = 18, SW_GENERATOR = 19, SW_ENG_MASTER = 20, SW_DIMMER = 21, SW_DISPLAY_BRT = 22, SW_NIGHT = 23,
            SW_MASTER_CAUTION = 24, SW_CONSOLE = 25, SWITCH_COUNT = 26;
    /** telemetry-only bit: starter engaged */
    public static final int BIT_STARTER = 26;

    /** Aircraft leave the factory cold and dark: every switch off, parking brake set. */
    public void coldAndDark() {
        battery = masterElec = avionics = fuelPump = ignition = engineMaster = false;
        lights = beacon = strobe = landingLights = taxiLights = turnoffLights = autopilot = false;
        nightMode = consoleLight = false;
        generatorSw = true;
        cabinLight = 0;
        panelLight = 1;
        fuelSelector = 0;
        parkingBrake = true;
        throttle = 0;
        busPowered = false;
        starterTicks = 0;
    }

    /** Bit field of the on/off switches for telemetry (bit = switch id). */
    public int switchBits() {
        int b = 0;
        if (battery) b |= 1 << SW_BATTERY;
        if (avionics) b |= 1 << SW_AVIONICS;
        if (fuelPump) b |= 1 << SW_FUEL_PUMP;
        if (ignition) b |= 1 << SW_IGNITION;
        if (lights) b |= 1 << SW_NAV;
        if (beacon) b |= 1 << SW_BEACON;
        if (strobe) b |= 1 << SW_STROBE;
        if (landingLights) b |= 1 << SW_LANDING;
        if (taxiLights) b |= 1 << SW_TAXI;
        if (turnoffLights) b |= 1 << SW_TURNOFF;
        if (gearDown) b |= 1 << SW_GEAR;
        if (autopilot) b |= 1 << SW_AUTOPILOT;
        if (parkingBrake) b |= 1 << SW_PARKING;
        if (masterElec) b |= 1 << SW_MASTER;
        if (generatorSw) b |= 1 << SW_GENERATOR;
        if (engineMaster) b |= 1 << SW_ENG_MASTER;
        if (nightMode) b |= 1 << SW_NIGHT;
        if (consoleLight) b |= 1 << SW_CONSOLE;
        if (starterTicks > 0) b |= 1 << BIT_STARTER;
        return b;
    }
    public long emergencyUntil;
    public double apHeading, apAltitude = 100;
    public double lift;
    /** automated tests: behave as if a pilot were seated */
    public boolean testPilot;
    /** smoothed stick axes (keyboard inputs ramp instead of jumping to full deflection) */
    public double axPitch, axRoll, axYaw;
    /** flight control sensitivity (config): deflection rate of the keyboard axes */
    public double sensitivity = 1;

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

    private double smooth(double v, double target, double dt) {
        // released: the centring spring brings the yoke / stick back faster than it deflects
        double rate = (Math.abs(target) < 1e-3 ? 4.0 : 2.2 * sensitivity) * dt;
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
        t.putBoolean("Battery", battery);
        t.putBoolean("Avionics", avionics); t.putBoolean("FuelPump", fuelPump); t.putBoolean("Ignition", ignition);
        t.putBoolean("Beacon", beacon); t.putBoolean("Strobe", strobe); t.putBoolean("Landing", landingLights);
        t.putBoolean("Taxi", taxiLights); t.putBoolean("Turnoff", turnoffLights);
        t.putInt("FuelSel", fuelSelector); t.putInt("Cabin", cabinLight); t.putInt("PanelLight", panelLight);
        t.putDouble("Charge", batteryCharge);
        t.putBoolean("Master", masterElec); t.putBoolean("GenSw", generatorSw); t.putBoolean("EngMaster", engineMaster);
        t.putInt("Dimmer", dimmer); t.putInt("DispBrt", displayBrt); t.putBoolean("Night", nightMode); t.putBoolean("Console", consoleLight);
        t.putBoolean("Init", initialized);
        t.putBoolean("Lights", lights);
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
        battery = !t.contains("Battery") || t.getBoolean("Battery");
        avionics = !t.contains("Avionics") || t.getBoolean("Avionics");
        fuelPump = t.getBoolean("FuelPump"); ignition = t.getBoolean("Ignition");
        beacon = t.getBoolean("Beacon"); strobe = t.getBoolean("Strobe"); landingLights = t.getBoolean("Landing");
        taxiLights = t.getBoolean("Taxi"); turnoffLights = t.getBoolean("Turnoff");
        fuelSelector = t.getInt("FuelSel"); cabinLight = t.getInt("Cabin");
        panelLight = t.contains("PanelLight") ? t.getInt("PanelLight") : 1;
        batteryCharge = t.contains("Charge") ? t.getDouble("Charge") : 1;
        masterElec = !t.contains("Master") || t.getBoolean("Master");
        generatorSw = !t.contains("GenSw") || t.getBoolean("GenSw");
        engineMaster = !t.contains("EngMaster") || t.getBoolean("EngMaster");
        dimmer = t.contains("Dimmer") ? t.getInt("Dimmer") : 3;
        displayBrt = t.contains("DispBrt") ? t.getInt("DispBrt") : 4;
        nightMode = t.getBoolean("Night"); consoleLight = t.getBoolean("Console");
        // vehicles saved before cold-and-dark existed keep their switches
        initialized = !t.contains("Init") || t.getBoolean("Init");
        lights = t.getBoolean("Lights");
        apHeading = t.getDouble("ApHdg");
        apAltitude = t.contains("ApAlt") ? t.getDouble("ApAlt") : 100;
    }
}
