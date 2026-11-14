package com.vehicleworks.vehicle;

/** How the pilot's keys map onto vehicle controls. */
public enum ControlProfile {
    AUTO, AIRCRAFT, HELICOPTER, CAR, SHIP, SUBMARINE, AIRSHIP;

    public static ControlProfile byIndex(int i) {
        ControlProfile[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
