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
            PropertyDef.bool("mouse_aim", false),
            PropertyDef.bool("assist", true),
            PropertyDef.choice("differential", 1, "open", "limited_slip", "locked"),
            PropertyDef.bool("auto_gearbox", true),
            PropertyDef.bool("show_com", false),
            PropertyDef.choice("handling", 2, "very_heavy", "heavy", "normal", "light")),
    PILOT_SEAT("pilot_seat", ComponentCategory.CONTROL, 40, Facing.HORIZONTAL, Shape.SEAT),
    PASSENGER_SEAT("passenger_seat", ComponentCategory.CONTROL, 30, Facing.HORIZONTAL, Shape.SEAT,
            PropertyDef.bool("gunner", false)),
    /** first officer: flight crew (cockpit access and HUD), not the pilot in command */
    COPILOT_SEAT("copilot_seat", ComponentCategory.CONTROL, 40, Facing.HORIZONTAL, Shape.SEAT),
    // five passenger cabin classes: animated, interactive seats (recline, leg rest, tray, screen, light; suite door)
    SEAT_ECONOMY("seat_economy", ComponentCategory.CONTROL, 22, Facing.HORIZONTAL, Shape.SEAT),
    SEAT_ECONOMY_PLUS("seat_economy_plus", ComponentCategory.CONTROL, 26, Facing.HORIZONTAL, Shape.SEAT),
    SEAT_PREMIUM("seat_premium", ComponentCategory.CONTROL, 34, Facing.HORIZONTAL, Shape.SEAT),
    SEAT_BUSINESS("seat_business", ComponentCategory.CONTROL, 55, Facing.HORIZONTAL, Shape.SEAT),
    /** first class suite: 2 x 2 blocks (the seat block plus the suite to its right and behind), private door */
    SEAT_FIRST("seat_first", ComponentCategory.CONTROL, 120, Facing.HORIZONTAL, Shape.SEAT,
            PropertyDef.bool("mirror", false)),
    /** folding cabin crew seat: unfolds when someone sits; attendant tools, not flight controls */
    JUMPSEAT("jumpseat", ComponentCategory.CONTROL, 12, Facing.HORIZONTAL, Shape.SEAT),
    /** attendant panel: cabin lights, announcements, interphone, door status, passenger calls, boarding */
    CREW_PANEL("crew_panel", ComponentCategory.INSTRUMENT, 20, Facing.HORIZONTAL, Shape.FULL),
    /** galley unit: cabinets, ovens, coffee makers, storage */
    GALLEY("galley", ComponentCategory.SYSTEMS, 140, Facing.HORIZONTAL, Shape.FULL),
    /** food / drink trolley: pushed along the aisle */
    TROLLEY("trolley", ComponentCategory.SYSTEMS, 40, Facing.HORIZONTAL, Shape.DYNAMIC),
    /** lavatory: toilet, basin, mirror, soap, light, door with an occupied sign (walk in through the door) */
    LAVATORY("lavatory", ComponentCategory.SYSTEMS, 90, Facing.HORIZONTAL, Shape.ROOM),
    /** passenger / cargo door (2 blocks tall), opens and closes */
    AIRCRAFT_DOOR("aircraft_door", ComponentCategory.STRUCTURE, 60, Facing.HORIZONTAL, Shape.DOOR,
            PropertyDef.choice("door_kind", 0, "passenger", "cargo")),

    PISTON_ENGINE("piston_engine", ComponentCategory.PROPULSION, EngineType.PISTON.massKg, Facing.ALL, Shape.FULL, engineProps(false)),
    TURBOPROP_ENGINE("turboprop_engine", ComponentCategory.PROPULSION, EngineType.TURBOPROP.massKg, Facing.ALL, Shape.FULL, engineProps(false)),
    TURBOFAN_ENGINE("turbofan_engine", ComponentCategory.PROPULSION, EngineType.TURBOFAN.massKg, Facing.ALL, Shape.FULL, engineProps(false)),
    /** under-wing airliner engine: nacelle, spinning fan, spinner, exhaust cone and pylon (one block) */
    AIRLINER_ENGINE("airliner_engine", ComponentCategory.PROPULSION, EngineType.TURBOFAN.massKg * 2, Facing.HORIZONTAL, Shape.DYNAMIC, airlinerEngineProps()),
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
    REFUEL_PORT("refuel_port", ComponentCategory.FUEL, 15, Facing.HORIZONTAL, Shape.FULL),
    FUEL_PUMP("fuel_pump", ComponentCategory.FUEL, 40, Facing.HORIZONTAL, Shape.FULL),
    HYDRAULIC_PUMP("hydraulic_pump", ComponentCategory.SYSTEMS, 70, Facing.HORIZONTAL, Shape.FULL),

    WING("wing", ComponentCategory.AERODYNAMICS, 35, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.NONE)),
    STRAIGHT_WING("straight_wing", ComponentCategory.AERODYNAMICS, 35, Facing.WING, Shape.WING, wingProps(WingType.STRAIGHT, ControlRole.NONE)),
    SWEPT_WING("swept_wing", ComponentCategory.AERODYNAMICS, 38, Facing.WING, Shape.WING, wingProps(WingType.SWEPT, ControlRole.NONE)),
    DELTA_WING("delta_wing", ComponentCategory.AERODYNAMICS, 40, Facing.WING, Shape.WING, wingProps(WingType.DELTA, ControlRole.ELEVON)),
    VARIABLE_SWEEP_WING("variable_sweep_wing", ComponentCategory.AERODYNAMICS, 55, Facing.WING, Shape.WING, wingProps(WingType.VARIABLE_SWEEP, ControlRole.NONE)),
    FORWARD_SWEPT_WING("forward_swept_wing", ComponentCategory.AERODYNAMICS, 40, Facing.WING, Shape.WING, wingProps(WingType.FORWARD_SWEPT, ControlRole.NONE)),
    DOUBLE_DELTA_WING("double_delta_wing", ComponentCategory.AERODYNAMICS, 42, Facing.WING, Shape.WING, wingProps(WingType.DOUBLE_DELTA, ControlRole.NONE)),
    ELLIPTICAL_WING("elliptical_wing", ComponentCategory.AERODYNAMICS, 34, Facing.WING, Shape.WING, wingProps(WingType.ELLIPTICAL, ControlRole.NONE)),
    TAPERED_WING("tapered_wing", ComponentCategory.AERODYNAMICS, 32, Facing.WING, Shape.WING, wingProps(WingType.TAPERED, ControlRole.NONE)),
    HIGH_WING("high_wing", ComponentCategory.AERODYNAMICS, 36, Facing.WING, Shape.WING, wingProps(WingType.HIGH_WING, ControlRole.NONE)),
    LOW_WING("low_wing", ComponentCategory.AERODYNAMICS, 36, Facing.WING, Shape.WING, wingProps(WingType.LOW_WING, ControlRole.NONE)),
    CANARD_WING("canard_wing", ComponentCategory.AERODYNAMICS, 22, Facing.WING, Shape.WING, wingProps(WingType.CANARD, ControlRole.ELEVATOR)),
    FLYING_WING("flying_wing", ComponentCategory.AERODYNAMICS, 45, Facing.WING, Shape.WING, wingProps(WingType.FLYING_WING, ControlRole.NONE)),
    AILERON("aileron", ComponentCategory.AERODYNAMICS, 25, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.AILERON)),
    ELEVATOR("elevator", ComponentCategory.AERODYNAMICS, 25, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.ELEVATOR)),
    RUDDER("rudder", ComponentCategory.AERODYNAMICS, 25, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.RUDDER)),
    FLAP("flap", ComponentCategory.AERODYNAMICS, 28, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.FLAP)),
    SPOILER("spoiler", ComponentCategory.AERODYNAMICS, 22, Facing.WING, Shape.WING, wingProps(WingType.STANDARD, ControlRole.SPOILER)),
    /** a whole wing half in one block: tapered, swept, dihedral, flaps, ailerons, slats, spoilers, wing tip device and lights */
    WING_ASSEMBLY("wing_assembly", ComponentCategory.AERODYNAMICS, 400, Facing.HORIZONTAL, Shape.DYNAMIC,
            PropertyDef.choice("side", 0, "left", "right"),
            PropertyDef.number("span", 2, 40, 0.5, 12, "m"),
            PropertyDef.number("root_chord", 0.5, 12, 0.25, 4.5, "m"),
            PropertyDef.number("tip_chord", 0.3, 8, 0.25, 1.5, "m"),
            PropertyDef.number("sweep", 0, 45, 1, 25, "deg"),
            PropertyDef.number("dihedral", -5, 12, 0.5, 5, "deg"),
            PropertyDef.number("incidence", -5, 8, 0.25, 3, "deg"),
            PropertyDef.choice("airfoil", AirfoilProfile.CAMBERED.ordinal(), Arrays.stream(AirfoilProfile.values()).map(p -> p.id).toArray(String[]::new)),
            PropertyDef.bool("flaps", true), PropertyDef.bool("ailerons", true), PropertyDef.bool("slats", true),
            PropertyDef.bool("spoilers", true), PropertyDef.bool("lights", true),
            PropertyDef.choice("winglet", 1, "none", "sharklet", "blended"),
            PropertyDef.choice("kind", 0, "main_wing", "stabilizer", "fin")),
    /** smooth nose or tail cone (fuselage fairing) */
    FAIRING_CONE("fairing_cone", ComponentCategory.STRUCTURE, 120, Facing.HORIZONTAL, Shape.DYNAMIC,
            PropertyDef.number("width", 1, 14, 0.25, 7, "m"),
            PropertyDef.number("height", 1, 12, 0.25, 4, "m"),
            PropertyDef.number("length", 1, 16, 0.25, 4, "m"),
            PropertyDef.number("tip_offset", -1, 1, 0.05, -0.25, ""),
            PropertyDef.number("center_y", -4, 4, 0.05, 0, "m"),
            PropertyDef.choice("style", 0, "nose", "tail"),
            PropertyDef.choice("color", 0, "white", "gray", "blue")),

    WHEEL("wheel", ComponentCategory.GROUND, 45, Facing.HORIZONTAL, Shape.DYNAMIC, wheelProps(false)),
    LANDING_GEAR("landing_gear", ComponentCategory.GROUND, 80, Facing.HORIZONTAL, Shape.DYNAMIC, wheelProps(true)),
    HEAVY_LANDING_GEAR("heavy_landing_gear", ComponentCategory.GROUND, 450, Facing.HORIZONTAL, Shape.DYNAMIC, heavyGearProps()),
    DUAL_WHEEL_GEAR("dual_wheel_gear", ComponentCategory.GROUND, 180, Facing.HORIZONTAL, Shape.DYNAMIC, gearProps(0.55, 0.6, 1.2, 1.0, 16000, 1.05)),
    BOGIE_GEAR("bogie_gear", ComponentCategory.GROUND, 620, Facing.HORIZONTAL, Shape.DYNAMIC, gearProps(0.6, 0.7, 1.6, 1.3, 40000, 1.1)),
    REINFORCED_GEAR("reinforced_gear", ComponentCategory.GROUND, 260, Facing.HORIZONTAL, Shape.DYNAMIC, gearProps(0.6, 0.75, 1.8, 1.6, 22000, 1.0)),
    TUNDRA_GEAR("tundra_gear", ComponentCategory.GROUND, 95, Facing.HORIZONTAL, Shape.DYNAMIC, gearProps(0.85, 0.8, 0.6, 1.2, 7000, 1.25)),
    PERFORMANCE_GEAR("performance_gear", ComponentCategory.GROUND, 55, Facing.HORIZONTAL, Shape.DYNAMIC, gearProps(0.4, 0.4, 1.4, 1.2, 12000, 0.95)),

    BALLAST_TANK("ballast_tank", ComponentCategory.MARINE, 120, Facing.NONE, Shape.FULL),
    BALLOON_ENVELOPE("balloon_envelope", ComponentCategory.LIFT, 1.0, Facing.NONE, Shape.FULL),
    GAS_CELL("gas_cell", ComponentCategory.LIFT, 1.0, Facing.NONE, Shape.FULL),
    BURNER("burner", ComponentCategory.LIFT, 45, Facing.NONE, Shape.FULL,
            PropertyDef.number("power", 0.5, 2.0, 0.05, 1.0, "x")),

    FLIGHT_INSTRUMENTS("flight_instruments", ComponentCategory.INSTRUMENT, 15, Facing.HORIZONTAL, Shape.FULL),
    AIRCRAFT_LIGHT("aircraft_light", ComponentCategory.SYSTEMS, 3, Facing.HORIZONTAL, Shape.DYNAMIC,
            PropertyDef.choice("light_type", 0, "auto_nav", "nav_red", "nav_green", "nav_white", "beacon", "strobe", "landing", "taxi", "turnoff", "cockpit")),
    YOKE("yoke", ComponentCategory.CONTROL, 12, Facing.HORIZONTAL, Shape.DYNAMIC),
    /** Airbus-style side stick (fly-by-wire): stick deflection commands pitch rate and roll rate */
    SIDESTICK("sidestick", ComponentCategory.CONTROL, 6, Facing.HORIZONTAL, Shape.DYNAMIC,
            PropertyDef.choice("side", 0, "left", "right")),
    INSTRUMENT_PANEL_A("instrument_panel_a", ComponentCategory.INSTRUMENT, 18, Facing.HORIZONTAL, Shape.FULL,
            slot("slot_1", 1, true), slot("slot_2", 2, true), slot("slot_3", 3, true), slot("slot_4", 4, true), slot("slot_5", 5, true), slot("slot_6", 9, true)),
    INSTRUMENT_PANEL_B("instrument_panel_b", ComponentCategory.INSTRUMENT, 20, Facing.HORIZONTAL, Shape.FULL,
            slot("slot_1", 1, true), slot("slot_2", 2, true), slot("large_slot", 4, false)),
    INSTRUMENT_PANEL_C("instrument_panel_c", ComponentCategory.INSTRUMENT, 22, Facing.HORIZONTAL, Shape.FULL,
            slot("large_slot", 1, false)),
    COCKPIT_PANEL("cockpit_panel", ComponentCategory.INSTRUMENT, 30, Facing.HORIZONTAL, Shape.PANEL),
    /** two large displays side by side (PFD + ND) */
    INSTRUMENT_PANEL_D("instrument_panel_d", ComponentCategory.INSTRUMENT, 20, Facing.HORIZONTAL, Shape.FULL,
            slot("left_slot", 4, false), slot("right_slot", 1, false)),
    /** two large displays stacked (ECAM upper E/WD + lower SD) */
    INSTRUMENT_PANEL_E("instrument_panel_e", ComponentCategory.INSTRUMENT, 20, Facing.HORIZONTAL, Shape.FULL,
            slot("upper_slot", 6, false), slot("lower_slot", 7, false)),
    /** Airbus-style flight control unit on the glareshield: SPD / HDG / ALT / V/S windows, knobs, AP and A/THR */
    FCU_PANEL("fcu_panel", ComponentCategory.INSTRUMENT, 8, Facing.HORIZONTAL, Shape.DYNAMIC),
    /** centre pedestal: thrust levers, engine masters, flaps, speed brake, gear lever, parking brake, two MCDUs */
    CENTER_PEDESTAL("center_pedestal", ComponentCategory.CONTROL, 45, Facing.HORIZONTAL, Shape.PEDESTAL),
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

    public enum Shape { FULL, SEAT, WING, DYNAMIC, PANEL, ROOM, DOOR, PEDESTAL }

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
            case TURBOFAN_ENGINE, AIRLINER_ENGINE -> EngineType.TURBOFAN;
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
        return isAircraftGear() || this == WHEEL || this == YOKE || this == SIDESTICK || this == FCU_PANEL || this == TROLLEY || this == AIRCRAFT_LIGHT || this == WING_ASSEMBLY || this == FAIRING_CONE || this == AIRLINER_ENGINE || this == PROPELLER || this == MAIN_ROTOR || this == TAIL_ROTOR || this == MARINE_PROPELLER;
    }

    /** Rendered dynamically (spinning / animated) instead of in the cached static mesh. */
    public boolean isDynamicRender() {
        return shape == Shape.DYNAMIC || seatClass() > 0 || this == JUMPSEAT || this == CENTER_PEDESTAL || this == GALLEY || this == LAVATORY
                || this == AIRCRAFT_DOOR || this == CREW_PANEL;
    }

    /** passenger cabin class 1 (economy) .. 5 (first class suite), 0 = not a cabin seat */
    public int seatClass() {
        return switch (this) {
            case SEAT_ECONOMY -> 1;
            case SEAT_ECONOMY_PLUS -> 2;
            case SEAT_PREMIUM -> 3;
            case SEAT_BUSINESS -> 4;
            case SEAT_FIRST -> 5;
            default -> 0;
        };
    }

    public boolean isSeat() {
        return this == PILOT_SEAT || this == COPILOT_SEAT || this == PASSENGER_SEAT || this == JUMPSEAT || seatClass() > 0;
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

    private static PropertyDef[] airlinerEngineProps() {
        return new PropertyDef[]{
                PropertyDef.number("power_scale", 0.5, 6, 0.1, 3.0, "x"),
                PropertyDef.choice("throttle_group", 0, "all", "group_a", "group_b"),
                PropertyDef.bool("enabled", true),
                PropertyDef.number("diameter", 1, 4, 0.1, 2.2, "m"),
                PropertyDef.number("length", 2, 7, 0.25, 4.2, "m"),
                PropertyDef.number("pylon", 0, 3, 0.05, 0.9, "m")};
    }

    private static PropertyDef[] wheelProps(boolean gear) {
        return new PropertyDef[]{
                PropertyDef.number("radius", 0.3, 1.2, 0.05, gear ? 0.45 : 0.5, "m"),
                PropertyDef.number("suspension", 0.2, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("damping", 0.2, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("travel", 0.1, 1.2, 0.05, gear ? 0.5 : 0.4, "m"),
                PropertyDef.choice("steering", 0, "auto", "yes", "no"),
                PropertyDef.number("max_steer", 5, 60, 1, gear ? 60 : 45, "deg"),
                PropertyDef.choice("drive", 0, "auto", "yes", "no"),
                PropertyDef.bool("braked", true),
                PropertyDef.number("brake_torque", 500, 30000, 250, gear ? 8000 : 5000, "Nm"),
                PropertyDef.number("grip", 0.3, 2.0, 0.05, 1.0, "x"),
                PropertyDef.bool("retractable", gear)};
    }

    /** Instrument catalogue (own class: enum constants are built before the enum's static fields). */
    public static final class Instruments {
        /** Small (analog) instruments that fit a small slot; index 0 = empty. */
        public static final String[] SMALL = {"none", "altimeter", "airspeed", "vsi", "horizon", "heading", "compass", "turn", "tachometer", "fuel_gauge", "engine_pressure"};
        /** Large (digital) instruments that need a large slot; index 0 = empty. */
        public static final String[] LARGE = {"none", "nav_display", "radar", "flight_director", "attitude_display", "engine_display", "ecam_ewd", "ecam_sd"};
        private Instruments() {}
    }

    private static PropertyDef slot(String key, int def, boolean small) {
        return PropertyDef.choice(key, def, small ? Instruments.SMALL : Instruments.LARGE);
    }

    public boolean isInstrumentPanel() {
        return this == INSTRUMENT_PANEL_A || this == INSTRUMENT_PANEL_B || this == INSTRUMENT_PANEL_C || this == INSTRUMENT_PANEL_D || this == INSTRUMENT_PANEL_E;
    }

    /** All aircraft landing-gear types (retractable, aircraft physics). */
    public boolean isAircraftGear() {
        return this == LANDING_GEAR || this == HEAVY_LANDING_GEAR || this == DUAL_WHEEL_GEAR || this == BOGIE_GEAR
                || this == REINFORCED_GEAR || this == TUNDRA_GEAR || this == PERFORMANCE_GEAR;
    }

    private static PropertyDef[] gearProps(double radius, double travel, double susp, double damp, double brake, double grip) {
        return new PropertyDef[]{
                PropertyDef.number("radius", 0.3, 1.5, 0.05, radius, "m"),
                PropertyDef.number("suspension", 0.2, 4.0, 0.05, susp, "x"),
                PropertyDef.number("damping", 0.2, 4.0, 0.05, damp, "x"),
                PropertyDef.number("travel", 0.1, 1.5, 0.05, travel, "m"),
                PropertyDef.choice("steering", 0, "auto", "yes", "no"),
                PropertyDef.number("max_steer", 5, 60, 1, 60, "deg"),
                PropertyDef.choice("drive", 0, "auto", "yes", "no"),
                PropertyDef.bool("braked", true),
                PropertyDef.number("brake_torque", 500, 60000, 250, brake, "Nm"),
                PropertyDef.number("grip", 0.3, 2.0, 0.05, grip, "x"),
                PropertyDef.bool("retractable", true)};
    }

    private static PropertyDef[] heavyGearProps() {
        return new PropertyDef[]{
                PropertyDef.number("radius", 0.5, 2.5, 0.05, 2.25, "m"),
                PropertyDef.number("suspension", 0.2, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("damping", 0.2, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("travel", 0.2, 1.5, 0.05, 1.0, "m"),
                PropertyDef.choice("steering", 0, "auto", "yes", "no"),
                PropertyDef.number("max_steer", 5, 60, 1, 60, "deg"),
                PropertyDef.choice("drive", 0, "auto", "yes", "no"),
                PropertyDef.bool("braked", true),
                PropertyDef.number("brake_torque", 2000, 60000, 500, 40000, "Nm"),
                PropertyDef.number("grip", 0.3, 2.0, 0.05, 1.0, "x"),
                PropertyDef.bool("retractable", true)};
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
                PropertyDef.choice("airfoil", p.ordinal(), profiles),
                PropertyDef.choice("role", role.ordinal(), roles),
                PropertyDef.number("lift_scale", 0.1, 2.5, 0.05, 1.0, "x"),
                PropertyDef.number("chord", 0.25, 4.0, 0.05, 1.0, "m"),
                PropertyDef.number("incidence", -15, 15, 0.25, incidence, "deg"),
                PropertyDef.number("drag_scale", 0.25, 4.0, 0.05, 1.0, "x"),
                PropertyDef.number("stall_angle", 0, 40, 0.5, type == WingType.DELTA ? 32 : 0, "deg"),
                PropertyDef.number("flap_max", 0, 50, 1, 30, "deg"),
                PropertyDef.number("control_max", 0, 40, 1, role == ControlRole.RUDDER ? 25 : 20, "deg"),
                PropertyDef.number("spoiler_max", 0, 1, 0.05, role == ControlRole.SPOILER ? 1 : 0, ""),
                PropertyDef.number("sweep", -45, 75, 1, type.defaultSweepDeg, "deg"),
                PropertyDef.number("sweep_max", 0, 75, 1, type == WingType.VARIABLE_SWEEP ? 68 : type.defaultSweepDeg, "deg"),
                PropertyDef.number("responsiveness", 0.1, 3.0, 0.05, 1.0, "x"),
                PropertyDef.bool("invert", false)};
    }
}
