package com.vehicleworks.vehicle;

import com.vehicleworks.component.ComponentType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * Shape of a one-block wing assembly (a whole wing half: tapered, swept, with dihedral, flaps, ailerons,
 * slats, spoilers, a wing tip device and its own lights). Shared by the physics builder and the renderer.
 *
 * <p>Coordinates are in the block's NORTH frame, relative to the block centre: -z is forward, +y up and
 * {@link #side} * x outboard. The span s runs from the block's outboard face (s = 0) to the tip (s = span).
 */
public final class WingGeometry {
    public final int side;            // -1 left wing, +1 right wing
    public final double span, root, tip, sweep, dihedral, incidence;
    public final boolean flaps, ailerons, slats, spoilers, lights;
    public final int winglet;         // 0 none, 1 sharklet, 2 blended
    public final int airfoil;
    /** 0 main wing, 1 horizontal stabiliser (elevator), 2 vertical fin (rudder, the span runs upward) */
    public final int kind;

    /** fractions of the span (0 = root, 1 = tip) */
    public static final double FLAP_END = 0.6, AIL_START = 0.66, AIL_END = 0.95, SPOIL_START = 0.12, SPOIL_END = 0.58, SLAT_START = 0.08;
    /** fraction of the chord taken by flaps and ailerons (trailing edge), slats (leading edge) and spoilers (ahead of the flaps) */
    public static final double FLAP_CHORD = 0.28, SLAT_CHORD = 0.12, SPOIL_CHORD = 0.13;

    public WingGeometry(CompoundTag c) {
        ComponentType t = ComponentType.WING_ASSEMBLY;
        side = t.property("side").getInt(c) == 0 ? -1 : 1;
        span = t.property("span").get(c);
        root = t.property("root_chord").get(c);
        tip = Math.min(root, t.property("tip_chord").get(c));
        sweep = Math.toRadians(t.property("sweep").get(c));
        dihedral = Math.toRadians(t.property("dihedral").get(c));
        incidence = t.property("incidence").get(c);
        airfoil = t.property("airfoil").getInt(c);
        flaps = t.property("flaps").getBool(c);
        ailerons = t.property("ailerons").getBool(c);
        slats = t.property("slats").getBool(c);
        spoilers = t.property("spoilers").getBool(c);
        lights = t.property("lights").getBool(c);
        winglet = t.property("winglet").getInt(c);
        kind = t.property("kind").getInt(c);
    }

    /** NORTH-frame point at span station s, chord position z and offset n along the surface normal (thickness) */
    public double[] point(double s, double z, double n) {
        if (kind == 2) return new double[]{n, 0.5 + Math.max(-1, s), z};
        return new double[]{side * (0.5 + s), y(s) + n, z};
    }

    /** trailing-edge control of this surface at span fraction f (null role = none) */
    public com.vehicleworks.physics.aero.ControlRole controlAt(double f) {
        if (kind == 1) return f < 0.97 ? com.vehicleworks.physics.aero.ControlRole.ELEVATOR : null;
        if (kind == 2) return f < 0.92 ? com.vehicleworks.physics.aero.ControlRole.RUDDER : null;
        if (flaps && f < FLAP_END) return com.vehicleworks.physics.aero.ControlRole.FLAP;
        if (ailerons && f > AIL_START && f < AIL_END) return com.vehicleworks.physics.aero.ControlRole.AILERON;
        return null;
    }

    public double chord(double s) { return root + (tip - root) * Math.max(0, Math.min(1, s / span)); }

    /** leading-edge z at span station s (the root quarter chord sits on the block centre) */
    public double leZ(double s) { return -0.25 * root + Math.max(0, s) * Math.tan(sweep); }

    public double y(double s) { return kind == 2 ? 0.5 + Math.max(-1, s) : Math.max(0, s) * Math.tan(dihedral); }

    /** outboard x of span station s (NORTH frame) */
    public double x(double s) { return side * (0.5 + s); }

    public double thickness(double s) { return Math.max(0.1, (kind == 0 ? 0.11 : 0.09) * chord(s)); }

    /** chord fraction of the moving trailing-edge surface (flap / aileron, or elevator / rudder on the tail) */
    public double ctlChord() { return kind == 0 ? FLAP_CHORD : 0.4; }

    public double area() { return span * (root + tip) * 0.5; }

    /** NORTH-frame point to the structure-local frame of a block facing f (x, z only; y is unchanged) */
    public static double[] toLocal(Direction f, double x, double z) {
        return switch (f) {
            case SOUTH -> new double[]{-x, -z};
            case EAST -> new double[]{-z, x};
            case WEST -> new double[]{z, -x};
            default -> new double[]{x, z};
        };
    }

    /** number of aerodynamic strips the wing is split into */
    public int strips() { return (int) Math.max(4, Math.min(24, Math.round(span / 1.2))); }

    /** furthest reach of the wing from the block centre (for bounds) */
    public double reach() { return 0.5 + span + Math.abs(leZ(span)) + tip + (winglet > 0 ? 1 : 0); }
}
