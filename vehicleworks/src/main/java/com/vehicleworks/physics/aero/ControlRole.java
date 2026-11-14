package com.vehicleworks.physics.aero;

/** Which pilot command channels a lifting surface responds to. */
public enum ControlRole {
    NONE("none", false, false, false, false, false),
    AILERON("aileron", false, true, false, false, false),
    ELEVATOR("elevator", true, false, false, false, false),
    RUDDER("rudder", false, false, true, false, false),
    FLAP("flap", false, false, false, true, false),
    FLAPERON("flaperon", false, true, false, true, false),
    ELEVON("elevon", true, true, false, false, false),
    SPOILER("spoiler", false, false, false, false, true),
    ALL_AXES("all_axes", true, true, true, false, false);

    public final String id;
    public final boolean pitch, roll, yaw, flap, spoiler;

    ControlRole(String id, boolean pitch, boolean roll, boolean yaw, boolean flap, boolean spoiler) {
        this.id = id;
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.flap = flap;
        this.spoiler = spoiler;
    }

    public boolean isControlSurface() {
        return pitch || roll || yaw;
    }

    public static ControlRole byIndex(int i) {
        ControlRole[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
