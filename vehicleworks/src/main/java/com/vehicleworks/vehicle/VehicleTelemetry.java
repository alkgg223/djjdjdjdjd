package com.vehicleworks.vehicle;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Snapshot of vehicle systems sent to clients for instruments, animation and sounds.
 * Arrays are indexed like the server layout (engines, rotors, wheels, weapons).
 */
public final class VehicleTelemetry {
    public int profile;
    public float airspeed, groundSpeed, altitude, agl, verticalSpeed, aoa, sideslip, gLoad, mach, heading, pitch, bank;
    public boolean stall, onGround, inWater, instruments;
    public float throttle, collective, flaps, shipThrottle, lift;
    public boolean gearDown, armed, assist, mouseAim, afterburner, parkingBrake;
    public float fuel, fuelCapacity, fuelFlow, ballast, envelopeTemp, gasLift, mass;
    public int carGear;
    public float rpmFraction, depth, integrity, stress;
    public boolean navActive, autopilot, battery = true, lights, emergencyTest;
    public float apHeading, apAltitude, vne, hydraulics = 1, stickPitch, stickRoll, batteryCharge = 1;
    public int switches, fuelSelector, cabinLight, panelLight;
    /** dimmer 0..4, display brightness 1..5, ticks since the bus got power (0 = unpowered) */
    public int dimmer = 3, displayBrt = 4, powerAge = 100000;
    /** fly-by-wire (side stick) control law */
    public boolean fbw;
    /** A320 systems: speed brake, cabin lights, signs, FCU, MCDU pages, rudder input, doors, failures, first officer */
    public float speedBrake, apSpeed, stickYaw;
    public int cabinLights = 2, mcdu1, mcdu2, doors, failures;
    public boolean seatbelt, autothrust, foPresent, foAssist, autoPA;
    public static final int FAIL_GEN = 1, FAIL_HYD = 2, FAIL_GEAR = 4, FAIL_INSTR = 8, FAIL_PRESS = 16, FAIL_FUEL = 32;
    public boolean fail(int bit) { return (failures & bit) != 0; }
    public boolean busPowered = true, generator, wrongFuel, refueling;
    public float refuelRemaining;
    /** per fuel tank: fill 0..1, grade, side */
    public float[] tankFill = new float[0];
    public byte[] tankGrade = new byte[0], tankSide = new byte[0];

    public boolean sw(int id) { return (switches & (1 << id)) != 0; }
    public boolean avionicsPowered() { return busPowered && sw(1); }
    public float navBearing, navDistance;
    public float staticMargin, rotorOffset;
    // per engine
    public byte[] engineState = new byte[0];
    public byte[] engineType = new byte[0];
    /** per engine: start phase (bits 0-2), drives a rotor (8), combustion lit (16), starter turning (32) */
    public byte[] enginePhase = new byte[0];
    public float[] engineSpool = new float[0], engineTemp = new float[0], engineDamage = new float[0], engineThrust = new float[0], propOmega = new float[0];
    public boolean[] engineAb = new boolean[0];
    // rotors
    public float[] rotorOmega = new float[0];
    // wheels
    public float[] wheelOmega = new float[0], wheelSteer = new float[0], wheelComp = new float[0], wheelExt = new float[0];
    public boolean[] wheelSkid = new boolean[0];
    // weapons
    public short[] weaponAmmo = new short[0], weaponReserve = new short[0];
    public byte[] weaponChannel = new byte[0], weaponFlags = new byte[0];
    public float[] weaponReload = new float[0], turretYaw = new float[0], turretPitch = new float[0];
    public int fireFlash;
    /** structure keys of the indexed components (engines, their devices, rotors, wheels, weapons) */
    public long[] engineKeys = new long[0], deviceKeys = new long[0], rotorKeys = new long[0], wheelKeys = new long[0], weaponKeys = new long[0];

    public void write(FriendlyByteBuf b) {
        b.writeByte(profile);
        b.writeFloat(airspeed); b.writeFloat(groundSpeed); b.writeFloat(altitude); b.writeFloat(agl); b.writeFloat(verticalSpeed);
        b.writeFloat(aoa); b.writeFloat(sideslip); b.writeFloat(gLoad); b.writeFloat(mach); b.writeFloat(heading); b.writeFloat(pitch); b.writeFloat(bank);
        int flags = (stall ? 1 : 0) | (onGround ? 2 : 0) | (inWater ? 4 : 0) | (instruments ? 8 : 0) | (gearDown ? 16 : 0) | (armed ? 32 : 0)
                | (assist ? 64 : 0) | (mouseAim ? 128 : 0) | (afterburner ? 256 : 0) | (parkingBrake ? 512 : 0) | (navActive ? 1024 : 0) | (autopilot ? 2048 : 0) | (battery ? 4096 : 0) | (lights ? 8192 : 0) | (emergencyTest ? 16384 : 0);
        b.writeVarInt(flags);
        b.writeFloat(throttle); b.writeFloat(collective); b.writeFloat(flaps); b.writeFloat(shipThrottle); b.writeFloat(lift);
        b.writeFloat(fuel); b.writeFloat(fuelCapacity); b.writeFloat(fuelFlow); b.writeFloat(ballast); b.writeFloat(envelopeTemp); b.writeFloat(gasLift); b.writeFloat(mass);
        b.writeByte(carGear);
        b.writeFloat(rpmFraction); b.writeFloat(depth); b.writeFloat(integrity); b.writeFloat(stress);
        b.writeFloat(navBearing); b.writeFloat(navDistance); b.writeFloat(staticMargin); b.writeFloat(rotorOffset);
        b.writeFloat(apHeading); b.writeFloat(apAltitude); b.writeFloat(vne); b.writeFloat(hydraulics); b.writeFloat(stickPitch); b.writeFloat(stickRoll);
        b.writeFloat(batteryCharge); b.writeVarInt(switches); b.writeByte(fuelSelector); b.writeByte(cabinLight); b.writeByte(panelLight);
        b.writeByte((busPowered ? 1 : 0) | (generator ? 2 : 0) | (wrongFuel ? 4 : 0) | (refueling ? 8 : 0));
        b.writeFloat(refuelRemaining);
        b.writeByte(dimmer); b.writeByte(displayBrt); b.writeVarInt(powerAge); b.writeBoolean(fbw);
        b.writeFloat(speedBrake); b.writeFloat(apSpeed); b.writeFloat(stickYaw);
        b.writeByte(cabinLights); b.writeByte(mcdu1); b.writeByte(mcdu2); b.writeVarInt(doors); b.writeVarInt(failures);
        b.writeByte((seatbelt ? 1 : 0) | (autothrust ? 2 : 0) | (foPresent ? 4 : 0) | (foAssist ? 8 : 0) | (autoPA ? 16 : 0));
        b.writeVarInt(tankFill.length);
        for (int i = 0; i < tankFill.length; i++) { b.writeFloat(tankFill[i]); b.writeByte(tankGrade[i]); b.writeByte(tankSide[i]); }
        int ne = engineSpool.length;
        b.writeVarInt(ne);
        for (int i = 0; i < ne; i++) {
            b.writeByte(engineState[i]); b.writeByte(engineType[i]); b.writeByte(enginePhase[i]);
            b.writeFloat(engineSpool[i]); b.writeFloat(engineTemp[i]); b.writeFloat(engineDamage[i]); b.writeFloat(engineThrust[i]); b.writeFloat(propOmega[i]);
            b.writeBoolean(engineAb[i]);
        }
        b.writeVarInt(rotorOmega.length);
        for (float v : rotorOmega) b.writeFloat(v);
        int nw = wheelOmega.length;
        b.writeVarInt(nw);
        for (int i = 0; i < nw; i++) {
            b.writeFloat(wheelOmega[i]); b.writeFloat(wheelSteer[i]); b.writeFloat(wheelComp[i]); b.writeFloat(wheelExt[i]); b.writeBoolean(wheelSkid[i]);
        }
        int nwp = weaponAmmo.length;
        b.writeVarInt(nwp);
        for (int i = 0; i < nwp; i++) {
            b.writeShort(weaponAmmo[i]); b.writeShort(weaponReserve[i]); b.writeByte(weaponChannel[i]); b.writeByte(weaponFlags[i]);
            b.writeFloat(weaponReload[i]); b.writeFloat(turretYaw[i]); b.writeFloat(turretPitch[i]);
        }
        b.writeVarInt(fireFlash);
        b.writeLongArray(engineKeys); b.writeLongArray(deviceKeys); b.writeLongArray(rotorKeys); b.writeLongArray(wheelKeys); b.writeLongArray(weaponKeys);
    }

    public void read(FriendlyByteBuf b) {
        profile = b.readByte();
        airspeed = b.readFloat(); groundSpeed = b.readFloat(); altitude = b.readFloat(); agl = b.readFloat(); verticalSpeed = b.readFloat();
        aoa = b.readFloat(); sideslip = b.readFloat(); gLoad = b.readFloat(); mach = b.readFloat(); heading = b.readFloat(); pitch = b.readFloat(); bank = b.readFloat();
        int flags = b.readVarInt();
        stall = (flags & 1) != 0; onGround = (flags & 2) != 0; inWater = (flags & 4) != 0; instruments = (flags & 8) != 0;
        gearDown = (flags & 16) != 0; armed = (flags & 32) != 0; assist = (flags & 64) != 0; mouseAim = (flags & 128) != 0;
        afterburner = (flags & 256) != 0; parkingBrake = (flags & 512) != 0; navActive = (flags & 1024) != 0; autopilot = (flags & 2048) != 0; battery = (flags & 4096) != 0; lights = (flags & 8192) != 0; emergencyTest = (flags & 16384) != 0;
        throttle = b.readFloat(); collective = b.readFloat(); flaps = b.readFloat(); shipThrottle = b.readFloat(); lift = b.readFloat();
        fuel = b.readFloat(); fuelCapacity = b.readFloat(); fuelFlow = b.readFloat(); ballast = b.readFloat(); envelopeTemp = b.readFloat(); gasLift = b.readFloat(); mass = b.readFloat();
        carGear = b.readByte();
        rpmFraction = b.readFloat(); depth = b.readFloat(); integrity = b.readFloat(); stress = b.readFloat();
        navBearing = b.readFloat(); navDistance = b.readFloat(); staticMargin = b.readFloat(); rotorOffset = b.readFloat();
        apHeading = b.readFloat(); apAltitude = b.readFloat(); vne = b.readFloat(); hydraulics = b.readFloat(); stickPitch = b.readFloat(); stickRoll = b.readFloat();
        batteryCharge = b.readFloat(); switches = b.readVarInt(); fuelSelector = b.readByte(); cabinLight = b.readByte(); panelLight = b.readByte();
        int ef = b.readByte(); busPowered = (ef & 1) != 0; generator = (ef & 2) != 0; wrongFuel = (ef & 4) != 0; refueling = (ef & 8) != 0;
        refuelRemaining = b.readFloat();
        dimmer = b.readByte(); displayBrt = b.readByte(); powerAge = b.readVarInt(); fbw = b.readBoolean();
        speedBrake = b.readFloat(); apSpeed = b.readFloat(); stickYaw = b.readFloat();
        cabinLights = b.readByte(); mcdu1 = b.readByte(); mcdu2 = b.readByte(); doors = b.readVarInt(); failures = b.readVarInt();
        int fl = b.readByte(); seatbelt = (fl & 1) != 0; autothrust = (fl & 2) != 0; foPresent = (fl & 4) != 0; foAssist = (fl & 8) != 0; autoPA = (fl & 16) != 0;
        int nt = b.readVarInt();
        if (tankFill.length != nt) { tankFill = new float[nt]; tankGrade = new byte[nt]; tankSide = new byte[nt]; }
        for (int i = 0; i < nt; i++) { tankFill[i] = b.readFloat(); tankGrade[i] = b.readByte(); tankSide[i] = b.readByte(); }
        int ne = b.readVarInt();
        engineState = new byte[ne]; engineType = new byte[ne]; enginePhase = new byte[ne];
        engineSpool = new float[ne]; engineTemp = new float[ne]; engineDamage = new float[ne]; engineThrust = new float[ne]; propOmega = new float[ne];
        engineAb = new boolean[ne];
        for (int i = 0; i < ne; i++) {
            engineState[i] = b.readByte(); engineType[i] = b.readByte(); enginePhase[i] = b.readByte();
            engineSpool[i] = b.readFloat(); engineTemp[i] = b.readFloat(); engineDamage[i] = b.readFloat(); engineThrust[i] = b.readFloat(); propOmega[i] = b.readFloat();
            engineAb[i] = b.readBoolean();
        }
        int nr = b.readVarInt();
        rotorOmega = new float[nr];
        for (int i = 0; i < nr; i++) rotorOmega[i] = b.readFloat();
        int nw = b.readVarInt();
        wheelOmega = new float[nw]; wheelSteer = new float[nw]; wheelComp = new float[nw]; wheelExt = new float[nw]; wheelSkid = new boolean[nw];
        for (int i = 0; i < nw; i++) {
            wheelOmega[i] = b.readFloat(); wheelSteer[i] = b.readFloat(); wheelComp[i] = b.readFloat(); wheelExt[i] = b.readFloat(); wheelSkid[i] = b.readBoolean();
        }
        int nwp = b.readVarInt();
        weaponAmmo = new short[nwp]; weaponReserve = new short[nwp]; weaponChannel = new byte[nwp]; weaponFlags = new byte[nwp];
        weaponReload = new float[nwp]; turretYaw = new float[nwp]; turretPitch = new float[nwp];
        for (int i = 0; i < nwp; i++) {
            weaponAmmo[i] = b.readShort(); weaponReserve[i] = b.readShort(); weaponChannel[i] = b.readByte(); weaponFlags[i] = b.readByte();
            weaponReload[i] = b.readFloat(); turretYaw[i] = b.readFloat(); turretPitch[i] = b.readFloat();
        }
        fireFlash = b.readVarInt();
        engineKeys = b.readLongArray(); deviceKeys = b.readLongArray(); rotorKeys = b.readLongArray(); wheelKeys = b.readLongArray(); weaponKeys = b.readLongArray();
    }
}
