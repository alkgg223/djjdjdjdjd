package com.vehicleworks.component;

import com.vehicleworks.physics.aero.AirfoilProfile;
import com.vehicleworks.physics.aero.ControlRole;
import com.vehicleworks.physics.aero.WingType;
import com.vehicleworks.physics.propulsion.EngineType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every engineering component block of the mod: physical mass, placement orientation,
 * collision behaviour and the list of player-configurable properties.
 */
public enum ComponentType {
    VEHICLE_CORE("vehicle_core", ComponentCategory.CONTROL, 150, Facing.HORIZONTAL, Shape.FULL,
            PropertyDef.choice("profile", 0, "auto", "aircraft", "helicopter", "car", "ship", "submarine", "airship"),
            PropertyDef.bool("mouse_aim", true),
            PropertyDef.bool("assist", true),
            PropertyDef.choice("differential", 1, "open", "limited_slip", "locked"),
            PropertyDef.bool("auto_gearbox", true),
            PropertyDef.bool("show_com", false)),
    PILOT_SEAT("pilot_seat", ComponentCategory.CONTROL, 40, Facing.HORIZONTAL, Shape.SEAT),
    PASSENGER_SEAT("passenger_seat", ComponentCategory.CONTROL, 30, Facing.HORIZONTAL, Shape.SEAT,
            PropertyDef.bool("gunner", false)),

    PISTON_ENGINE("piston_engine", ComponentCategory.PROPULSION, EngineType.PISTON.massKg, Facing.ALL, Shape.FULL, engineProps(false)),
    TURBOPROP_ENGINE("turboprop_engine", ComponentCategory.PROPULSION, EngineType.TURBOPROP.massKg, Facing.ALL, Shape.FULL, engineProps(false)),
    TURBOFAN_ENGINE("turbofan_engine", ComponentCategory.PROPULSION, EngineType.TURBOFAN.massKg, Facing.ALL, Shape.FULL, engineProps(false)),
    JET_ENGINE("jet_engine", ComponentCategory.PROPULSION, EngineType.TURBOJET.massKg, Facing.ALL, Shape.FULL, engineProps(true)),
    ELECTRIC_MOTOR("electric_motor", ComponentCategory.PROPULSION, EngineType.ELECTRIC.massKg, Facing.ALL, Shape.FULL, engineProps(false)),
    PROPELLER("propeller", ComponentCategory.PROPULSION, 60, Facing.ALL, Shape.DYNAMIC,
            PropertyDef.number("diameter", 1.0, 4.5, 0.1, 2.4, "m"),
            PropertyDef.number("efficiency", 0.6, 0.9, 0.01, 0.82, ""),
            PropertyDef.choice("spin", 0, "clockwise", "counter_clockwise")),
    MARINE_PROPELLER("marine_propeller", ComponentCategory.MARINE, 90, Facing.ALL, Shape.DYNAMIC,
            PropertyDef.number("diameter", 0.5, 1.6, 0.1, 1.0, "m"),
            PropertyDef.number("efficiency", 0.5, 0.8, 0.01, 0.68, "")),
    MAIN_ROTOR("main_rotor", ComponentCategory.PROPULSION, 320, Facing.ALL, Shape.DYNAMIC,
            PropertyDef.number("radius", 2.0, 10.0, 0.5, 6.0, "m"),
            PropertyDef.integer("blades", 2, 6, 4),
            PropertyDef.number("chord", 0.2, 0.8, 0.05, 0.45, "m"),
            PropertyDef.number("max_collective", 8, 20, 0.5, 16, "deg"),
            PropertyDef.number("hub_stiffness", 0.3, 3.0, 0.1, 1.5, "m"),
            PropertyDef.choice("spin", 0, "counter_clockwise", "clockwise")),
    TAIL_ROTOR("tail_rotor", ComponentCategory.PROPULSION, 60, Facing.ALL, Shape.DYNAMIC,
            PropertyDef.number("radius", 0.5, 2.5, 0.1, 1.5, "m"),
            PropertyDef.integer("blades", 2, 6, 4)),
    ENGINE_STARTER("engine_starter", ComponentCategory.SYSTEMS, 90, Facing.HORIZONTAL, Shape.FULL),
    FUEL_TANK("fuel_tank", ComponentCategory.FUEL, 60, Facing.NONE, Shape.FULL),
    FUEL_PUMP("fuel_pump", ComponentCategory.FUEL, 40, Facing.HORIZONTAL, Shape.FULL),
    HYDRAULIC_PUMP("hydraulic_pump", ComponentCategory.SYSTEMS, 70, Facing.HORIZONTAL, Shape.FULL),

    WING("wing", ComponentCategory.AERODYNAMICS, 35, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.NONE)),
    STRAIGHT_WING("straight_wing", ComponentCategory.AERODYNAMICS, 35, Facing.WING, Shape.WING, wingProps(WingType.STRAIGHT, ControlRole.NONE)),
    SWEPT_WING("swept_wing", ComponentCategory.AERODYNAMICS, 38, Facing.WING, Shape.WING, wingProps(WingType.SWEPT, ControlRole.NONE)),
    DELTA_WING("delta_wing", ComponentCategory.AERODYNAMICS, 40, Facing.WING, Shape.WING, wingProps(WingType.DELTA, ControlRole.ELEVON)),
    VARIABLE_SWEEP_WING("variable_sweep_wing", ComponentCategory.AERODYNAMICS, 55, Facing.WING, Shape.WING, wingProps(WingType.VARIABLE_SWEEP, ControlRole.NONE)),
    AILERON("aileron", ComponentCategory.AERODYNAMICS, 25, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.AILERON)),
    ELEVATOR("elevator", ComponentCategory.AERODYNAMICS, 25, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.ELEVATOR)),
    RUDDER("rudder", ComponentCategory.AERODYNAMICS, 25, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.RUDDER)),
    FLAP("flap", ComponentCategory.AERODYNAMICS, 28, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.FLAP)),
    SPOILER("spoiler", ComponentCategory.AERODYNAMICS, 22, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.SPOILER)),

    WHEEL("wheel", ComponentCategory.GROUND, 45, Facing.HORIZONTAL, Shape.DYNAMIC, wheelProps(false)),
    LANDING_GEAR("landing_gear", ComponentCategory.GROUND, 80, Facing.HORIZONTAL, Shape.DYNAMIC, wheelProps(true)),

    BALLAST_TANK("ballast_tank", ComponentCategory.MARINE, 120, Facing.NONE, Shape.FULL),
    BALLOON_ENVELOPE("balloon_envelope", ComponentCategory.LIFT, 1.0, Facing.NONE, Shape.FULL),
    GAS_CELL("gas_cell", ComponentCategory.LIFT, 1.0, Facing.NONE, Shape.FULL),
    BURNER("burner", ComponentCategory.LIFT, 45, Facing.NONE, Shape.FULL,
            PropertyDef.number("power", 0.5, 2.0, 0.05, 1.0, "x")),

    FLIGHT_INSTRUMENTS("flight_instruments", ComponentCategory.INSTRUMENT, 15, Facing.HORIZONTAL, Shape.FULL),
    NAVIGATION_COMPUTER("navigation_computer", ComponentCategory.INSTRUMENT, 15, Facing.HORIZONTAL, Shape.FULL,
            PropertyDef.integer("waypoint_x", -30000000, 30000000, 0),
            PropertyDef.integer("waypoint_z", -30000000, 30000000, 0),
            PropertyDef.bool("waypoint_active", false)),

    WEAPON_MOUNT("weapon_mount", ComponentCategory.WEAPON, 60, Facing.ALL, Shape.FULL),
    TURRET_BASE("turret_base", ComponentCategory.WEAPON, 220, Facing.ALL, Shape.FULL,
            PropertyDef.number("traverse_speed", 10, 120, 5, 60, "deg/s"),
            PropertyDef.number("max_elevation", 10, 90, 5, 70, "deg"),
            PropertyDef.number("max_depression", 0, 30, 1, 8, "deg")),
    MACHINE_GUN("machine_gun", ComponentCategory.WEAPON, 35, Facing.ALL, Shape.FULL, weaponProps()),
    CANNON("cannon", ComponentCategory.WEAPON, 240, Facing.ALL, Shape.FULL, weaponProps()),
    MISSILE_LAUNCHER("missile_launcher", ComponentCategory.WEAPON, 150, Facing.ALL, Shape.FULL, weaponProps()),
    BOMB_BAY("bomb_bay", ComponentCategory.WEAPON, 120, Facing.NONE, Shape.FULL, weaponProps()),
    AMMO_BOX("ammo_box", ComponentCategory.WEAPON, 40, Facing.HORIZONTAL, Shape.FULL),
    REMOTE_CONTROL_UNIT("remote_control_unit", ComponentCategory.CONTROL, 10, Facing.HORIZONTAL, Shape.FULL);

    public enum Facing { NONE, HORIZONTAL, ALL, WING }

    public enum Shape { FULL, SEAT, WING, DYNAMIC }

    public final String id;
    public final ComponentCategory category;
    public final double massKg;
    public final Facing facing;
    public final Shape shape;
    public final List<PropertyDef> properties;

    ComponentType(String id, ComponentCategory category, double massKg, Facing facing, Shape shape, PropertyDef... props) {
        this.id = id;
        this.category = category;
        this.massKg = massKg;
        this.facing = facing;
        this.shape = shape;
        this.properties = List.of(props);
    }

    public PropertyDef property(String key) {
        for (PropertyDef p : properties) if (p.key.equals(key)) return p;
        return null;
    }

    public boolean isConfigurable() {
        return !properties.isEmpty();
    }

    public boolean isWing() {
        return shape == Shape.WING;
    }

    public boolean isEngine() {
        return engineType() != null;
    }

    public EngineType engineType() {
        return switch (this) {
            case PISTON_ENGINE -> EngineType.PISTON;
            case TURBOPROP_ENGINE -> EngineType.TURBOPROP;
            case TURBOFAN_ENGINE -> EngineType.TURBOFAN;
            case JET_ENGINE -> EngineType.TURBOJET;
            case ELECTRIC_MOTOR -> EngineType.ELECTRIC;
            default -> null;
        };
    }

    public boolean isWeapon() {
        return this == MACHINE_GUN || this == CANNON || this == MISSILE_LAUNCHER || this == BOMB_BAY;
    }

    /** Blocks without rigid collision (handled by their own physics, e.g. suspension). */
    public boolean excludedFromCollision() {
        return this == WHEEL || this == LANDING_GEAR || this == PROPELLER || this == MAIN_ROTOR || this == TAIL_ROTOR || this == MARINE_PROPELLER;
    }

    /** Rendered dynamically (spinning / animated) instead of in the cached static mesh. */
    public boolean isDynamicRender() {
        return shape == Shape.DYNAMIC;
    }

    public static ComponentType byId(String id) {
        for (ComponentType t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    private static PropertyDef[] engineProps(boolean afterburner) {
        List<PropertyDef> l = new ArrayList<>(Arrays.asList(
                PropertyDef.number("power_scale", 0.5, 1.5, 0.05, 1.0, "x"),
                PropertyDef.choice("throttle_group", 0, "all", "group_a", "group_b"),
                PropertyDef.bool("enabled", true)));
        if (afterburner) l.add(PropertyDef.bool("afterburner", true));
        return l.toArray(new PropertyDef[0]);
    }

    private static PropertyDef[] wheelProps(boolean gear) {
        return new PropertyDef[]{
                PropertyDef.number("radius", 0.3, 1.2, 0.05, gear ? 0.45 : 0.5, "m"),
                PropertyDef.number("suspension", 0.2, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("damping", 0.2, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("travel", 0.1, 1.2, 0.05, gear ? 0.5 : 0.4, "m"),
                PropertyDef.choice("steering", 0, "auto", "yes", "no"),
                PropertyDef.number("max_steer", 5, 50, 1, gear ? 45 : 32, "deg"),
                PropertyDef.choice("drive", 0, "auto", "yes", "no"),
                PropertyDef.bool("braked", true),
                PropertyDef.number("brake_torque", 500, 30000, 250, gear ? 8000 : 5000, "Nm"),
                PropertyDef.number("grip", 0.3, 2.0, 0.05, 1.0, "x"),
                PropertyDef.bool("retractable", gear)};
    }

    private static PropertyDef[] weaponProps() {
        return new PropertyDef[]{
                PropertyDef.choice("fire_mode", 0, "auto", "burst", "single"),
                PropertyDef.bool("safety", false)};
    }

    private static PropertyDef[] wingProps(WingType type, ControlRole role) {
        String[] types = Arrays.stream(WingType.values()).map(t -> t.id).toArray(String[]::new);
        String[] profiles = Arrays.stream(AirfoilProfile.values()).map(p -> p.id).toArray(String[]::new);
        String[] roles = Arrays.stream(ControlRole.values()).map(r -> r.id).toArray(String[]::new);
        AirfoilProfile p = role.isControlSurface() || role == ControlRole.SPOILER ? AirfoilProfile.SYMMETRIC : type.defaultProfile;
        double incidence = role == ControlRole.NONE || role == ControlRole.FLAP ? (type == WingType.DELTA ? 0 : 2) : 0;
        return new PropertyDef[]{
                PropertyDef.choice("wing_type", type.ordinal(), types),
                PropertyDef.choice("profile", p.ordinal(), profiles),
                PropertyDef.choice("role", role.ordinal(), roles),
                PropertyDef.number("lift_scale", 0.1, 2.5, 0.05, 1.0, "x"),
                PropertyDef.number("chord", 0.25, 4.0, 0.05, 1.0, "m"),
                PropertyDef.number("incidence", -15, 15, 0.25, incidence, "deg"),
                PropertyDef.number("drag_scale", 0.25, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("stall_angle", 0, 40, 0.5, type == WingType.DELTA ? 32 : 0, "deg"),
                PropertyDef.number("flap_max", 0, 50, 1, 30, "deg"),
                PropertyDef.number("control_max", 0, 40, 1, role == ControlRole.RUDDER ? 25 : 20, "deg"),
                PropertyDef.number("spoiler_max", 0, 1, 0.05, role == ControlRole.SPOILER ? 1 : 0, ""),
                PropertyDef.number("sweep", 0, 75, 1, type.defaultSweepDeg, "deg"),
                PropertyDef.number("sweep_max", 0, 75, 1, type == WingType.VARIABLE_SWEEP ? 68 : type.defaultSweepDeg, "deg"),
                PropertyDef.number("responsiveness", 0.1, 3.0, 0.05, 1.0, "x"),
                PropertyDef.bool("invert", false)};
    }
}
