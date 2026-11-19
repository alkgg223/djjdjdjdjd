package com.vehicleworks.command;

import com.vehicleworks.block.ComponentBlock;
import com.vehicleworks.block.ComponentBlockEntity;
import com.vehicleworks.block.WingOrientation;
import com.vehicleworks.component.ComponentType;
import com.vehicleworks.network.ServerActions;
import com.vehicleworks.registry.ModBlocks;
import com.vehicleworks.vehicle.VehicleStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Ready-made example vehicles, built block by block exactly as a player would. Local
 * coordinates: the core is at 0,0,0 facing north (forward = −Z, right = +X, up = +Y).
 */
public final class DemoVehicles {
    public enum Demo { TRAINER, FIGHTER, AIRLINER, BOEING787, PELICAN, STINGRAY, CAR, BOAT, HELICOPTER, BALLOON, AIRSHIP, SUBMARINE;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    private DemoVehicles() {}

    public static final class B {
        public final VehicleStructure s = new VehicleStructure();

        public B put(int x, int y, int z, BlockState st) {
            s.put(BlockPos.asLong(x, y, z), st, ServerActions.defaultBeTag(st));
            return this;
        }

        /** aircraft skin block; f = the side facing out of the aircraft, top = roof edge (false = belly edge) */
        public B skin(int x, int y, int z, com.vehicleworks.block.SkinBlock.Kind k, Direction f, boolean top) {
            return put(x, y, z, com.vehicleworks.registry.ModBlocks.skin(k).setValue(com.vehicleworks.block.SkinBlock.FACING, f)
                    .setValue(com.vehicleworks.block.SkinBlock.HALF, top ? net.minecraft.world.level.block.state.properties.Half.TOP : net.minecraft.world.level.block.state.properties.Half.BOTTOM));
        }

        public B skin(int x, int y, int z, com.vehicleworks.block.SkinBlock.Kind k) {
            return skin(x, y, z, k, Direction.NORTH, true);
        }

        public B block(int x, int y, int z, net.minecraft.world.level.block.Block b) {
            return put(x, y, z, b.defaultBlockState());
        }

        public B c(int x, int y, int z, ComponentType t) {
            return c(x, y, z, t, null, null);
        }

        public B c(int x, int y, int z, ComponentType t, Direction facing) {
            return c(x, y, z, t, facing, null);
        }

        public B c(int x, int y, int z, ComponentType t, Direction facing, Consumer<CompoundTag> config) {
            BlockState st = ModBlocks.get(t).defaultBlockState();
            if (facing != null) {
                if (st.hasProperty(ComponentBlock.FACING)) st = st.setValue(ComponentBlock.FACING, facing);
                else if (st.hasProperty(ComponentBlock.HORIZONTAL_FACING) && facing.getAxis() != Direction.Axis.Y) st = st.setValue(ComponentBlock.HORIZONTAL_FACING, facing);
            }
            put(x, y, z, st);
            if (config != null) {
                CompoundTag be = s.blockEntity(BlockPos.asLong(x, y, z));
                CompoundTag cfg = be.getCompound(ComponentBlockEntity.CONFIG);
                config.accept(cfg);
                be.put(ComponentBlockEntity.CONFIG, cfg);
            }
            return this;
        }

        public B wing(int x, int y, int z, ComponentType t, WingOrientation o, Consumer<CompoundTag> config) {
            BlockState st = ModBlocks.get(t).defaultBlockState().setValue(ComponentBlock.PLANE, o);
            put(x, y, z, st);
            if (config != null) {
                CompoundTag be = s.blockEntity(BlockPos.asLong(x, y, z));
                CompoundTag cfg = be.getCompound(ComponentBlockEntity.CONFIG);
                config.accept(cfg);
                be.put(ComponentBlockEntity.CONFIG, cfg);
            }
            return this;
        }

        public B data(int x, int y, int z, Consumer<CompoundTag> data) {
            CompoundTag be = s.blockEntity(BlockPos.asLong(x, y, z));
            if (be == null) return this;
            CompoundTag d = be.getCompound(ComponentBlockEntity.DATA);
            data.accept(d);
            be.put(ComponentBlockEntity.DATA, d);
            return this;
        }

        /** aircraft light: 0 auto nav, 1 red, 2 green, 3 white, 4 beacon, 5 strobe, 6 landing, 7 taxi, 8 turn-off, 9 cockpit */
        public B light(int x, int y, int z, int type, Direction f) {
            return c(x, y, z, ComponentType.AIRCRAFT_LIGHT, f, c -> c.putInt("light_type", type));
        }

        /** instrument panel with slot selections (indices into the small / large instrument lists) */
        public B panel(int x, int y, int z, ComponentType t, Direction f, int... slots) {
            return c(x, y, z, t, f, c -> {
                String[] keys = t == ComponentType.INSTRUMENT_PANEL_A ? new String[]{"slot_1", "slot_2", "slot_3", "slot_4", "slot_5", "slot_6"}
                        : t == ComponentType.INSTRUMENT_PANEL_B ? new String[]{"slot_1", "slot_2", "large_slot"}
                        : t == ComponentType.INSTRUMENT_PANEL_D ? new String[]{"left_slot", "right_slot"}
                        : t == ComponentType.INSTRUMENT_PANEL_E ? new String[]{"upper_slot", "lower_slot"} : new String[]{"large_slot"};
                for (int i = 0; i < keys.length; i++) c.putInt(keys[i], i < slots.length ? slots[i] : 0);
            });
        }

        public B fuel(int x, int y, int z) {
            c(x, y, z, ComponentType.FUEL_TANK);
            return data(x, y, z, d -> d.putDouble("Fuel", 300));
        }

        public B link(int rx, int ry, int rz, int tx, int ty, int tz, int channel) {
            return data(rx, ry, rz, d -> {
                ListTag l = d.getList("Links", 10);
                CompoundTag t = new CompoundTag();
                t.putIntArray("Off", new int[]{tx - rx, ty - ry, tz - rz});
                t.putInt("Ch", channel);
                t.putInt("Mode", 0);
                l.add(t);
                d.put("Links", l);
            });
        }
    }

    public static VehicleStructure build(Demo demo) {
        B b = new B();
        switch (demo) {
            case TRAINER -> trainer(b);
            case FIGHTER -> fighter(b);
            case AIRLINER -> airliner(b);
            case BOEING787 -> boeing787(b);
            case PELICAN -> pelican(b);
            case STINGRAY -> stingray(b);
            case CAR -> car(b);
            case BOAT -> boat(b);
            case HELICOPTER -> helicopter(b);
            case BALLOON -> balloon(b);
            case AIRSHIP -> airship(b);
            case SUBMARINE -> submarine(b);
        }
        return b.s;
    }

    private static Consumer<CompoundTag> chord(double c) {
        return t -> t.putDouble("chord", c);
    }

    private static void trainer(B b) {
        // fuselage (birch) along z = -4 … 6
        for (int z = -4; z <= 6; z++) if (z != 0) b.block(0, 0, z, Blocks.BIRCH_PLANKS);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH);
        b.fuel(0, 0, -1);
        b.c(0, 0, -5, ComponentType.PISTON_ENGINE, Direction.NORTH, t -> t.putDouble("power_scale", 1.5));
        b.c(0, 0, -6, ComponentType.PROPELLER, Direction.NORTH, t -> t.putDouble("diameter", 2.8));
        b.c(0, 1, 0, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, -1, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.block(0, 1, -2, Blocks.GLASS);
        b.block(0, 1, 1, Blocks.BIRCH_PLANKS);
        // main wing: leading edge row z=-1, trailing row z=0 with flaps and ailerons
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 8; i++) {
                int x = side * i;
                b.wing(x, 0, -1, ComponentType.WING, WingOrientation.HORIZONTAL, chord(1.4));
                ComponentType trailing = i <= 3 ? ComponentType.FLAP : (i >= 6 ? ComponentType.AILERON : ComponentType.WING);
                b.wing(x, 0, 0, trailing, WingOrientation.HORIZONTAL, chord(1.2));
            }
            // horizontal tail
            for (int i = 1; i <= 2; i++) {
                b.wing(side * i, 0, 5, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", -1.5); t.putDouble("chord", 1.4); });
                b.wing(side * i, 0, 6, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> { t.putDouble("control_max", 25); t.putDouble("chord", 1.2); });
            }
        }
        // vertical fin
        b.wing(0, 1, 5, ComponentType.WING, WingOrientation.VERTICAL_X, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        b.wing(0, 1, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
        b.wing(0, 2, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
        // fixed tricycle gear
        b.c(0, -1, -3, ComponentType.LANDING_GEAR, Direction.NORTH, t -> t.putBoolean("retractable", false));
        b.c(-2, -1, 1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> t.putBoolean("retractable", false));
        b.c(2, -1, 1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> t.putBoolean("retractable", false));
        b.block(-2, 0, 1, Blocks.BIRCH_PLANKS);
        b.block(2, 0, 1, Blocks.BIRCH_PLANKS);
        b.block(0, 0, -3, Blocks.BIRCH_PLANKS);
    }

    private static void fighter(B b) {
        var hull = Blocks.LIGHT_GRAY_CONCRETE;
        for (int z = -6; z <= 6; z++) if (z != 0) b.block(0, 0, z, hull);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, c -> c.putInt("handling", 3));
        b.block(0, 0, -7, Blocks.GRAY_CONCRETE);
        b.fuel(0, 0, -1);
        b.fuel(0, 0, -2);
        b.fuel(0, 0, -3);
        b.c(0, 0, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 0, ComponentType.HYDRAULIC_PUMP, Direction.NORTH);
        b.c(0, 0, 2, ComponentType.ENGINE_STARTER, Direction.NORTH);
        b.c(0, 0, 7, ComponentType.JET_ENGINE, Direction.NORTH);
        b.c(0, 1, -3, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, -4, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(0, 1, -2, ComponentType.REMOTE_CONTROL_UNIT, Direction.NORTH);
        b.c(0, 1, -1, ComponentType.NAVIGATION_COMPUTER, Direction.NORTH);
        b.block(0, 1, -5, Blocks.LIGHT_GRAY_STAINED_GLASS);
        b.block(-1, 1, -3, Blocks.LIGHT_GRAY_STAINED_GLASS);
        b.block(1, 1, -3, Blocks.LIGHT_GRAY_STAINED_GLASS);
        b.c(0, 1, 0, ComponentType.AMMO_BOX, Direction.NORTH);
        b.data(0, 1, 0, d -> { CompoundTag a = new CompoundTag(); a.putInt("BULLET", 1200); a.putInt("MISSILE", 4); d.put("Ammo", a); });
        // swept main wing: leading row z=0, trailing row z=1 (flaps inboard, ailerons outboard), strakes at z=-1
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 7; i++) {
                b.wing(side * i, 0, 0, ComponentType.SWEPT_WING, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.5); t.putDouble("incidence", 3); t.putDouble("sweep", 25); });
                ComponentType tr = i <= 3 ? ComponentType.FLAP : (i >= 5 ? ComponentType.AILERON : ComponentType.SWEPT_WING);
                b.wing(side * i, 0, 1, tr, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.3); t.putDouble("sweep", 25); });
                if (i <= 2) b.wing(side * i, 0, -1, ComponentType.SWEPT_WING, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.2); t.putDouble("incidence", 3); t.putDouble("sweep", 25); });
            }
            // horizontal tail
            for (int i = 1; i <= 3; i++) {
                b.wing(side * i, 0, 5, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", -1); t.putDouble("chord", 1.3); });
                b.wing(side * i, 0, 6, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> { t.putDouble("chord", 1.2); t.putDouble("control_max", 25); });
            }
        }
        // fin
        for (int y = 1; y <= 2; y++) {
            b.wing(0, y, 5, ComponentType.WING, WingOrientation.VERTICAL_X, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); t.putDouble("chord", 1.3); });
            b.wing(0, y, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, t -> t.putDouble("chord", 1.2));
        }
        // guns in the nose, missiles under the wings
        for (int side = -1; side <= 1; side += 2) {
            b.c(side, 0, -5, ComponentType.WEAPON_MOUNT, Direction.NORTH);
            b.c(side, 0, -6, ComponentType.MACHINE_GUN, Direction.NORTH);
            b.data(side, 0, -6, d -> d.putInt("Mag", 250));
            b.c(side * 4, -1, 0, ComponentType.WEAPON_MOUNT, Direction.NORTH);
            b.c(side * 4, -1, -1, ComponentType.MISSILE_LAUNCHER, Direction.NORTH);
            b.data(side * 4, -1, -1, d -> d.putInt("Mag", 2));
        }
        b.link(0, 1, -2, -1, 0, -6, 0);
        b.link(0, 1, -2, 1, 0, -6, 0);
        b.link(0, 1, -2, -4, -1, -1, 1);
        b.link(0, 1, -2, 4, -1, -1, 1);
        // retractable tricycle gear, mains just behind the centre of mass
        b.c(0, -1, -4, ComponentType.LANDING_GEAR, Direction.NORTH);
        b.c(-2, -1, 2, ComponentType.LANDING_GEAR, Direction.NORTH);
        b.c(2, -1, 2, ComponentType.LANDING_GEAR, Direction.NORTH);
        b.block(-2, 0, 2, hull);
        b.block(2, 0, 2, hull);
    }

    /** Narrow-body twin (A320-style), built like the 787: glazed cockpit, two-row cabin, big gear. */
    private static void airliner(B b) {
        // Airbus A320 style: 9 wide cabin (3 + 3 seats, centre aisle), cockpit z -15..-11, cabin z -10..15.
        // The shell is built from aircraft skin blocks: rounded roof and belly corners, bevels, cheatline, window panels.
        for (int z = -15; z <= 16; z++) {
            boolean cockpit = z <= -11;
            int hw = 4; // the cockpit is as wide as the cabin, like the real aircraft
            for (int x = -hw + 1; x <= hw - 1; x++) if (!(x == 0 && z == 0)) b.skin(x, 0, z, com.vehicleworks.block.SkinBlock.Kind.FLOOR);
            for (int x = -hw + 2; x <= hw - 2; x++) { b.skin(x, 4, z, com.vehicleworks.block.SkinBlock.Kind.PANEL); b.skin(x, -1, z, com.vehicleworks.block.SkinBlock.Kind.BELLY); }
            for (int side = -1; side <= 1; side += 2) {
                Direction out = side < 0 ? Direction.WEST : Direction.EAST;
                int wx = side * hw;
                // belly corner, lower wall, cheatline, windows, upper wall, roof corner
                b.skin(wx, -1, z, com.vehicleworks.block.SkinBlock.Kind.CURVE, out, false);
                b.skin(wx - side, -1, z, com.vehicleworks.block.SkinBlock.Kind.BEVEL_ROOF, out, false);
                b.skin(wx, 0, z, com.vehicleworks.block.SkinBlock.Kind.BEVEL_WALL, out, false);
                boolean door = z == -9 || z == 13;
                if (!door) {
                    b.skin(wx, 1, z, com.vehicleworks.block.SkinBlock.Kind.STRIPE);
                    if (cockpit) b.skin(wx, 2, z, com.vehicleworks.block.SkinBlock.Kind.COCKPIT_GLASS, out, true);
                    else if (z >= -8 && z <= 12) b.skin(wx, 2, z, com.vehicleworks.block.SkinBlock.Kind.WINDOW, out, true);
                    else b.skin(wx, 2, z, com.vehicleworks.block.SkinBlock.Kind.PANEL);
                }
                b.skin(wx, 3, z, com.vehicleworks.block.SkinBlock.Kind.BEVEL_WALL, out, true);
                b.skin(wx, 4, z, com.vehicleworks.block.SkinBlock.Kind.CURVE, out, true);
                b.skin(wx - side, 4, z, com.vehicleworks.block.SkinBlock.Kind.BEVEL_ROOF, out, true);
            }
        }
        // front bulkhead beside the windshield
        for (int side = -1; side <= 1; side += 2) {
            for (int y = 1; y <= 3; y++) b.skin(side * 3, y, -15, y == 1 ? com.vehicleworks.block.SkinBlock.Kind.STRIPE : com.vehicleworks.block.SkinBlock.Kind.COCKPIT_GLASS, Direction.NORTH, true);
            b.skin(side * 3, 4, -15, com.vehicleworks.block.SkinBlock.Kind.CURVE, Direction.NORTH, true);
        }
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, c -> c.putInt("handling", 1));
        // nose and windshield
        // windshield: nothing in front of the glass, the nose slopes down below the eye line
        for (int x = -2; x <= 2; x++) {
            b.skin(x, 2, -15, com.vehicleworks.block.SkinBlock.Kind.COCKPIT_GLASS, Direction.NORTH, true);
            b.skin(x, 3, -15, com.vehicleworks.block.SkinBlock.Kind.COCKPIT_GLASS, Direction.NORTH, true);
            b.skin(x, 4, -15, com.vehicleworks.block.SkinBlock.Kind.CURVE, Direction.NORTH, true); // rounded crown above the windshield
        }
        // smooth nose (radome) below the windshield
        b.c(0, -1, -15, ComponentType.FAIRING_CONE, Direction.NORTH, c -> { c.putDouble("width", 9); c.putDouble("height", 3.2); c.putDouble("length", 4.5);
            c.putDouble("center_y", 1.05); c.putDouble("tip_offset", -0.35); c.putInt("style", 0); });
        // ---- cockpit: six displays (PFD + ND per pilot, ECAM upper / lower in the centre), standby gauges, FCU, overhead, pedestal
        b.panel(-2, 1, -15, ComponentType.INSTRUMENT_PANEL_A, Direction.SOUTH, 2, 1, 4, 3, 6, 9);
        b.panel(-1, 1, -15, ComponentType.INSTRUMENT_PANEL_D, Direction.SOUTH, 4, 1);
        b.panel(0, 1, -15, ComponentType.INSTRUMENT_PANEL_E, Direction.SOUTH, 6, 7);
        b.panel(1, 1, -15, ComponentType.INSTRUMENT_PANEL_D, Direction.SOUTH, 1, 4);
        b.panel(2, 1, -15, ComponentType.INSTRUMENT_PANEL_A, Direction.SOUTH, 2, 1, 4, 3, 8, 10);
        b.c(0, 2, -14, ComponentType.FCU_PANEL, Direction.NORTH);
        b.c(0, 1, -13, ComponentType.CENTER_PEDESTAL, Direction.NORTH); // between the pilot seats
        b.c(-1, 1, -13, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(1, 1, -13, ComponentType.COPILOT_SEAT, Direction.NORTH);
        // side sticks on the outboard consoles (with the rudder pedals in front of each seat)
        b.c(-1, 1, -14, ComponentType.SIDESTICK, Direction.NORTH, c -> c.putInt("side", 0));
        b.c(1, 1, -14, ComponentType.SIDESTICK, Direction.NORTH, c -> c.putInt("side", 1));
        b.c(0, 3, -14, ComponentType.COCKPIT_PANEL, Direction.SOUTH); // overhead panel
        b.c(-1, -1, -8, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(1, -1, -8, ComponentType.NAVIGATION_COMPUTER, Direction.SOUTH);
        b.light(2, 3, -12, 9, Direction.SOUTH);
        b.c(0, 1, -11, ComponentType.JUMPSEAT, Direction.NORTH); // observer seat
        // ---- forward galley, lavatory, attendant panel and two jump seats; doors L1 / R1
        b.c(-3, 1, -10, ComponentType.LAVATORY, Direction.SOUTH);
        b.c(-2, 1, -10, ComponentType.JUMPSEAT, Direction.SOUTH);
        b.c(-1, 1, -10, ComponentType.CREW_PANEL, Direction.SOUTH);
        b.c(1, 1, -10, ComponentType.JUMPSEAT, Direction.SOUTH);
        b.c(2, 1, -10, ComponentType.GALLEY, Direction.SOUTH);
        b.c(3, 1, -10, ComponentType.GALLEY, Direction.SOUTH);
        b.c(0, 1, -9, ComponentType.TROLLEY, Direction.SOUTH);
        b.c(-4, 1, -9, ComponentType.AIRCRAFT_DOOR, Direction.WEST);
        b.c(4, 1, -9, ComponentType.AIRCRAFT_DOOR, Direction.EAST);
        // ---- cabin: first class suites, business 2 + 2, premium / economy plus / economy 3 + 3
        b.c(-3, 1, -7, ComponentType.SEAT_FIRST, Direction.NORTH);
        b.c(3, 1, -7, ComponentType.SEAT_FIRST, Direction.NORTH, c -> c.putBoolean("mirror", true));
        for (int z : new int[]{-5, -3}) for (int x : new int[]{-3, -2, 2, 3}) b.c(x, 1, z, ComponentType.SEAT_BUSINESS, Direction.NORTH);
        for (int z = -1; z <= 11; z += 2) {
            ComponentType cls = z == -1 ? ComponentType.SEAT_PREMIUM : (z <= 3 ? ComponentType.SEAT_ECONOMY_PLUS : ComponentType.SEAT_ECONOMY);
            for (int x : new int[]{-3, -2, -1, 1, 2, 3}) b.c(x, 1, z, cls, Direction.NORTH);
        }
        // ---- aft galley, lavatory, attendant panel and two jump seats; doors L2 / R2
        b.c(-4, 1, 13, ComponentType.AIRCRAFT_DOOR, Direction.WEST);
        b.c(4, 1, 13, ComponentType.AIRCRAFT_DOOR, Direction.EAST);
        b.c(0, 1, 13, ComponentType.TROLLEY, Direction.NORTH);
        b.c(-3, 1, 14, ComponentType.LAVATORY, Direction.NORTH);
        b.c(-2, 1, 14, ComponentType.JUMPSEAT, Direction.NORTH);
        b.c(-1, 1, 14, ComponentType.CREW_PANEL, Direction.NORTH);
        b.c(1, 1, 14, ComponentType.JUMPSEAT, Direction.NORTH);
        b.c(2, 1, 14, ComponentType.GALLEY, Direction.NORTH);
        b.c(3, 1, 14, ComponentType.GALLEY, Direction.NORTH);
        // aft pressure bulkhead and the upswept tail cone
        for (int x = -3; x <= 3; x++) for (int y = 1; y <= 3; y++) b.skin(x, y, 16, com.vehicleworks.block.SkinBlock.Kind.PANEL);
        b.c(0, 1, 16, ComponentType.FAIRING_CONE, Direction.SOUTH, c -> { c.putDouble("width", 8.6); c.putDouble("height", 5.6); c.putDouble("length", 6);
            c.putDouble("center_y", 0.4); c.putDouble("tip_offset", 0.4); c.putInt("style", 1); });
        // cargo doors in the lower lobe (forward and aft hold)
        b.c(4, 0, -6, ComponentType.AIRCRAFT_DOOR, Direction.EAST, c -> c.putInt("door_kind", 1));
        b.c(4, 0, 7, ComponentType.AIRCRAFT_DOOR, Direction.EAST, c -> c.putInt("door_kind", 1));
        // ---- systems in the belly
        for (int z = -3; z <= 4; z++) if (z != 0) { b.fuel(-2, -1, z); b.fuel(2, -1, z); b.fuel(-1, -1, z); b.fuel(1, -1, z); }
        b.c(0, -1, -2, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 2, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 4, ComponentType.HYDRAULIC_PUMP, Direction.NORTH);
        b.c(0, -1, 6, ComponentType.ENGINE_STARTER, Direction.NORTH);
        b.c(6, -1, 1, ComponentType.REFUEL_PORT, Direction.EAST);
        // ---- one-block wings: each wing half is a single Wing Assembly (flaps, ailerons, slats, spoilers, sharklet and lights built in)
        for (int side = 0; side <= 1; side++) {
            int sd = side;
            b.c(sd == 0 ? -5 : 5, 0, -1, ComponentType.WING_ASSEMBLY, Direction.NORTH, c -> { c.putInt("side", sd); c.putDouble("span", 13); c.putDouble("root_chord", 7);
                c.putDouble("tip_chord", 2); c.putDouble("sweep", 25); c.putDouble("dihedral", 5); c.putDouble("incidence", 3); c.putInt("airfoil", 4); c.putInt("winglet", 1); });
            // under-wing engine with nacelle, fan and pylon
            b.c(sd == 0 ? -9 : 9, 0, -2, ComponentType.AIRLINER_ENGINE, Direction.NORTH, c -> { c.putDouble("power_scale", 4.5); c.putDouble("diameter", 2.4);
                c.putDouble("length", 4.2); c.putDouble("pylon", 1.9); });
            // horizontal stabiliser with elevator
            b.c(sd == 0 ? -5 : 5, 2, 15, ComponentType.WING_ASSEMBLY, Direction.NORTH, c -> { c.putInt("side", sd); c.putInt("kind", 1); c.putDouble("span", 5.5);
                c.putDouble("root_chord", 3.2); c.putDouble("tip_chord", 1.4); c.putDouble("sweep", 30); c.putDouble("dihedral", 6); c.putDouble("incidence", -1.5);
                c.putInt("airfoil", 1); c.putBoolean("lights", false); c.putInt("winglet", 0); c.putBoolean("slats", false); c.putBoolean("spoilers", false); });
        }
        // vertical fin with rudder
        b.c(0, 4, 15, ComponentType.WING_ASSEMBLY, Direction.NORTH, c -> { c.putInt("kind", 2); c.putDouble("span", 6); c.putDouble("root_chord", 4.2);
            c.putDouble("tip_chord", 1.6); c.putDouble("sweep", 35); c.putDouble("incidence", 0); c.putInt("airfoil", 1); c.putBoolean("lights", false); c.putInt("winglet", 0); });
        // ---- lights on the fuselage: beacon, tail navigation / strobe, taxi light on the nose gear
        b.light(0, 5, 0, 4, Direction.NORTH);
        b.light(0, -2, 6, 4, Direction.NORTH);
        b.light(0, 3, 22, 3, Direction.SOUTH);
        b.light(0, -1, -13, 7, Direction.NORTH);
        // ---- landing gear
        b.c(0, -2, -12, ComponentType.HEAVY_LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 1.6));
        b.c(-3, -2, 2, ComponentType.HEAVY_LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 1.6));
        b.c(3, -2, 2, ComponentType.HEAVY_LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 1.6));
    }

    /** Wide-body twin (787-style): two-aisle cabin with 28 seats, big glazed cockpit, long swept wing. */
    private static void boeing787(B b) {
        var floor = Blocks.BIRCH_PLANKS;
        var skin = Blocks.WHITE_WOOL;
        var stripe = Blocks.BLUE_WOOL;
        // fuselage z = -16 (cockpit) … 20 (tail), 5 wide, cabin 3 wide x 3 high
        for (int z = -16; z <= 20; z++) {
            for (int x = -2; x <= 2; x++) if (!(x == 0 && z == 0)) b.block(x, 0, z, floor);
            boolean cockpit = z <= -13;
            for (int side = -1; side <= 1; side += 2) {
                b.block(side * 2, 1, z, stripe);
                b.block(side * 2, 2, z, cockpit || z % 2 == 0 ? Blocks.GLASS : skin);
                b.block(side * 2, 3, z, cockpit ? Blocks.GLASS : skin);
            }
            for (int x = -2; x <= 2; x++) b.block(x, 4, z, cockpit && Math.abs(x) <= 1 ? Blocks.GLASS : skin);
        }
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, c -> c.putInt("handling", 1));
        // cockpit: big windscreen, low instrument panel, captain + first officer
        for (int x = -1; x <= 1; x++) {
            b.block(x, 1, -16, Blocks.GRAY_CONCRETE);
            b.block(x, 2, -16, Blocks.GLASS);
            b.block(x, 3, -16, Blocks.GLASS);
            b.block(x, 0, -17, Blocks.LIGHT_GRAY_CONCRETE);
        }
        b.block(-2, 1, -16, skin); b.block(2, 1, -16, skin);
        b.block(-2, 2, -16, Blocks.GLASS); b.block(2, 2, -16, Blocks.GLASS);
        b.block(-2, 3, -16, Blocks.GLASS); b.block(2, 3, -16, Blocks.GLASS);
        b.c(-1, 1, -14, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(1, 1, -14, ComponentType.COPILOT_SEAT, Direction.NORTH);
        // captain and first-officer yokes (linked: both move together)
        b.c(-1, 1, -15, ComponentType.YOKE, Direction.SOUTH);
        b.c(1, 1, -15, ComponentType.YOKE, Direction.SOUTH);
        b.c(1, -1, -12, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(0, -1, -12, ComponentType.NAVIGATION_COMPUTER, Direction.SOUTH);
        b.panel(-1, 1, -16, ComponentType.INSTRUMENT_PANEL_B, Direction.SOUTH, 2, 1, 4);
        b.panel(0, 1, -16, ComponentType.INSTRUMENT_PANEL_C, Direction.SOUTH, 1);
        b.panel(1, 1, -16, ComponentType.INSTRUMENT_PANEL_B, Direction.SOUTH, 9, 8, 5);
        b.light(-21, 0, 3, 1, Direction.NORTH); b.light(21, 0, 3, 2, Direction.NORTH);
        b.light(-21, 0, 4, 5, Direction.NORTH); b.light(21, 0, 4, 5, Direction.NORTH);
        b.light(0, 2, 21, 3, Direction.SOUTH);
        b.light(0, 5, 0, 4, Direction.NORTH);
        b.light(-4, -1, -2, 6, Direction.NORTH); b.light(4, -1, -2, 6, Direction.NORTH);
        b.light(0, -1, -14, 7, Direction.NORTH);
        b.light(-3, -1, -1, 8, Direction.WEST); b.light(3, -1, -1, 8, Direction.EAST);
        b.light(1, 3, -15, 9, Direction.SOUTH);
        b.c(4, -1, 0, ComponentType.REFUEL_PORT, Direction.EAST);
        b.c(0, 3, -14, ComponentType.COCKPIT_PANEL, Direction.SOUTH); // overhead panel
        // first class: two private 2 x 2 suites on the left (doors toward the aisle on the right)
        b.c(-1, 1, -10, ComponentType.SEAT_FIRST, Direction.NORTH);
        b.c(-1, 1, -8, ComponentType.SEAT_FIRST, Direction.NORTH);
        // then business, premium economy, economy plus and economy left and right of the centre aisle
        for (int z = -5; z <= 17; z += 2) {
            ComponentType cls = z <= -3 ? ComponentType.SEAT_BUSINESS : (z <= 1 ? ComponentType.SEAT_PREMIUM : (z <= 5 ? ComponentType.SEAT_ECONOMY_PLUS : ComponentType.SEAT_ECONOMY));
            b.c(-1, 1, z, cls, Direction.NORTH);
            b.c(1, 1, z, cls, Direction.NORTH);
        }
        // tail cone
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) b.block(x, y, 20, skin);
        // systems in the belly
        for (int z = -3; z <= 4; z++) if (z != 0) { b.fuel(-1, -1, z); b.fuel(1, -1, z); }
        b.c(0, -1, -2, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 2, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 4, ComponentType.HYDRAULIC_PUMP, Direction.NORTH);
        b.c(0, -1, 6, ComponentType.ENGINE_STARTER, Direction.NORTH);
        // long swept wing, tapered 5 → 3 chord rows
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 3; i <= 20; i++) {
                int rows = i <= 8 ? 5 : (i <= 14 ? 4 : 3);
                int zLead = -2 + (i - 3) / 4;
                for (int r = 0; r < rows; r++) {
                    boolean trailing = r == rows - 1;
                    ComponentType t = trailing ? (i <= 10 ? ComponentType.FLAP : (i >= 15 ? ComponentType.AILERON : ComponentType.SWEPT_WING)) : ComponentType.SWEPT_WING;
                    b.wing(side * i, 0, zLead + r, t, WingOrientation.HORIZONTAL, c -> { c.putDouble("sweep", 28); c.putDouble("chord", 1.2); c.putDouble("incidence", 3); c.putInt("airfoil", 4); });
                }
            }
            // big nacelles: two turbofan cores per engine
            b.c(side * 7, -1, -2, ComponentType.TURBOFAN_ENGINE, Direction.NORTH, c -> c.putDouble("power_scale", 1.5));
            b.c(side * 7, -1, -1, ComponentType.TURBOFAN_ENGINE, Direction.NORTH, c -> c.putDouble("power_scale", 1.5));
            // horizontal tail
            for (int i = 3; i <= 8; i++) {
                b.wing(side * i, 2, 17, ComponentType.WING, WingOrientation.HORIZONTAL, c -> { c.putInt("airfoil", 1); c.putDouble("incidence", -1.5); c.putDouble("chord", 1.3); });
                b.wing(side * i, 2, 18, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, c -> { c.putDouble("chord", 1.2); c.putDouble("control_max", 25); });
            }
        }
        // vertical fin
        for (int y = 5; y <= 10; y++) {
            b.wing(0, y, 17, ComponentType.WING, WingOrientation.VERTICAL_X, c -> { c.putInt("airfoil", 1); c.putDouble("incidence", 0); c.putDouble("chord", 1.3); });
            b.wing(0, y, 18, ComponentType.RUDDER, WingOrientation.VERTICAL_X, c -> c.putDouble("chord", 1.2));
        }
        // gear: nose + two mains under the wing roots, just aft of the centre of mass
        b.c(0, -1, -13, ComponentType.HEAVY_LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 2.0));
        b.c(-3, -1, 2, ComponentType.HEAVY_LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 2.0));
        b.c(3, -1, 2, ComponentType.HEAVY_LANDING_GEAR, Direction.NORTH, c -> c.putDouble("radius", 2.0));
    }

    /** Original design "Pelican": twin-boom turboprop utility plane with a glazed pod and a high wing. */
    private static void pelican(B b) {
        var floor = Blocks.BIRCH_PLANKS;
        var skin = Blocks.ORANGE_WOOL;
        var trim = Blocks.WHITE_WOOL;
        // pod x -2..2, z -5..4: floor, walls, roof
        for (int z = -5; z <= 4; z++) {
            for (int x = -2; x <= 2; x++) if (!(x == 0 && z == 0)) b.block(x, 0, z, floor);
            boolean front = z <= -3;
            for (int side = -1; side <= 1; side += 2) {
                b.block(side * 2, 1, z, trim);
                b.block(side * 2, 2, z, front || z % 2 == 0 ? Blocks.GLASS : skin);
            }
            for (int x = -2; x <= 2; x++) b.block(x, 3, z, front && Math.abs(x) <= 1 ? Blocks.GLASS : skin);
        }
        for (int x = -1; x <= 1; x++) { b.block(x, 1, -5, Blocks.GRAY_CONCRETE); b.block(x, 2, -5, Blocks.GLASS); }
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 2; y++) b.block(x, y, 4, trim); // rear door
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH);
        // cockpit seats sit low (in the floor) so the overhead switch panel is above the pilots' heads
        b.c(-1, 0, -3, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(1, 0, -3, ComponentType.COPILOT_SEAT, Direction.NORTH);
        // captain and first-officer yokes (linked: both move together)
        b.c(-1, 0, -4, ComponentType.YOKE, Direction.SOUTH);
        b.c(1, 0, -4, ComponentType.YOKE, Direction.SOUTH);
        b.c(1, -1, 2, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(-1, -1, 2, ComponentType.NAVIGATION_COMPUTER, Direction.SOUTH);
        b.c(0, 2, -3, ComponentType.COCKPIT_PANEL, Direction.SOUTH); // overhead switch panel
        // analog "six-pack", navigation display and engine gauges
        b.panel(-1, 1, -5, ComponentType.INSTRUMENT_PANEL_A, Direction.SOUTH, 2, 4, 1, 7, 5, 3);
        b.panel(0, 1, -5, ComponentType.INSTRUMENT_PANEL_C, Direction.SOUTH, 1);
        b.panel(1, 1, -5, ComponentType.INSTRUMENT_PANEL_A, Direction.SOUTH, 8, 9, 10, 6, 0, 0);
        // lights: wingtip nav + strobes, tail, beacon, landing, taxi, turn-off, cockpit
        b.light(-13, 4, 0, 1, Direction.NORTH); b.light(13, 4, 0, 2, Direction.NORTH);
        b.light(-13, 4, 1, 5, Direction.NORTH); b.light(13, 4, 1, 5, Direction.NORTH);
        b.light(0, 3, 11, 3, Direction.SOUTH);
        b.light(0, 5, 0, 4, Direction.NORTH);
        b.light(-6, 3, -1, 6, Direction.NORTH); b.light(6, 3, -1, 6, Direction.NORTH);
        b.light(0, -1, -5, 7, Direction.NORTH);
        b.light(-3, 3, 0, 8, Direction.WEST); b.light(3, 3, 0, 8, Direction.EAST);
        b.light(1, 2, -4, 9, Direction.SOUTH);
        b.c(-3, 0, 2, ComponentType.REFUEL_PORT, Direction.WEST);
        for (int z = -1; z <= 3; z += 2) {
            ComponentType cls = z == -1 ? ComponentType.SEAT_PREMIUM : ComponentType.SEAT_ECONOMY;
            b.c(-1, 1, z, cls, Direction.NORTH); b.c(1, 1, z, cls, Direction.NORTH);
        }
        for (int z = -2; z <= 3; z++) if (z != 0) b.fuel(0, -1, z);
        b.c(-1, -1, 0, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(1, -1, 0, ComponentType.ENGINE_STARTER, Direction.NORTH);
        b.c(0, -1, 0, ComponentType.HYDRAULIC_PUMP, Direction.NORTH);
        // high straight wing on the roof
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i <= 12; i++) {
                if (i == 0 && side > 0) continue;
                for (int z = -1; z <= 1; z++) {
                    ComponentType t = z < 1 ? ComponentType.HIGH_WING : (i <= 6 ? ComponentType.FLAP : (i >= 9 ? ComponentType.AILERON : ComponentType.HIGH_WING));
                    if (i <= 2) t = z < 1 ? ComponentType.HIGH_WING : ComponentType.FLAP;
                    b.wing(side * i, 4, z, t, WingOrientation.HORIZONTAL, c -> { c.putDouble("chord", 1.2); c.putDouble("incidence", 3); });
                }
            }
            // tail booms under the wing with a turboprop at the front
            for (int z = -3; z <= 10; z++) b.block(side * 4, 3, z, trim);
            b.c(side * 4, 3, -4, ComponentType.TURBOPROP_ENGINE, Direction.NORTH, c -> c.putDouble("power_scale", 1.5));
            final int spin = side < 0 ? 0 : 1;
            b.c(side * 4, 3, -5, ComponentType.PROPELLER, Direction.NORTH, c -> { c.putDouble("diameter", 2.8); c.putInt("spin", spin); });
            // twin fins at the end of the booms
            for (int y = 4; y <= 6; y++) {
                b.wing(side * 4, y, 9, ComponentType.WING, WingOrientation.VERTICAL_X, c -> { c.putInt("airfoil", 1); c.putDouble("incidence", 0); c.putDouble("chord", 1.2); });
                b.wing(side * 4, y, 10, ComponentType.RUDDER, WingOrientation.VERTICAL_X, c -> c.putDouble("chord", 1.1));
            }
        }
        // horizontal tail between the booms
        for (int x = -3; x <= 3; x++) {
            b.wing(x, 3, 9, ComponentType.WING, WingOrientation.HORIZONTAL, c -> { c.putInt("airfoil", 1); c.putDouble("incidence", -1.5); c.putDouble("chord", 1.3); });
            b.wing(x, 3, 10, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, c -> { c.putDouble("chord", 1.2); c.putDouble("control_max", 25); });
        }
        // fixed tricycle gear
        // tundra wheels: rough-field utility plane (grass, dirt, sand, snow)
        b.c(0, -1, -4, ComponentType.TUNDRA_GEAR, Direction.NORTH, c -> c.putBoolean("retractable", false));
        b.c(-2, -1, 1, ComponentType.TUNDRA_GEAR, Direction.NORTH, c -> c.putBoolean("retractable", false));
        b.c(2, -1, 1, ComponentType.TUNDRA_GEAR, Direction.NORTH, c -> c.putBoolean("retractable", false));
    }

    /** Original design "Stingray": tailless delta jet with elevons and a single afterburning engine. */
    private static void stingray(B b) {
        var hull = Blocks.CYAN_TERRACOTTA;
        for (int z = -7; z <= 6; z++) if (z != 0) b.block(0, 0, z, hull);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, c -> c.putInt("handling", 3));
        b.block(0, 0, -8, Blocks.GRAY_CONCRETE);
        b.block(0, 0, -7, Blocks.IRON_BLOCK); // nose ballast keeps the tailless delta stable
        b.block(0, 0, -6, Blocks.IRON_BLOCK);
        b.fuel(0, 0, -1); b.fuel(0, 0, -2); b.fuel(0, 0, 1);
        b.c(0, -1, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 2, ComponentType.FUEL_PUMP, Direction.NORTH);
        b.c(0, -1, 0, ComponentType.HYDRAULIC_PUMP, Direction.NORTH);
        b.c(0, 0, 2, ComponentType.ENGINE_STARTER, Direction.NORTH);
        b.c(0, 0, 7, ComponentType.JET_ENGINE, Direction.NORTH);
        // bubble canopy
        b.c(0, 1, -4, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.panel(0, 1, -5, ComponentType.INSTRUMENT_PANEL_B, Direction.SOUTH, 2, 1, 2); // airspeed, altimeter, radar
        b.c(0, -1, -3, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(0, 1, -3, ComponentType.NAVIGATION_COMPUTER, Direction.NORTH);
        b.light(-9, 0, 4, 1, Direction.NORTH); b.light(9, 0, 4, 2, Direction.NORTH);
        b.light(-9, 0, 5, 5, Direction.NORTH); b.light(9, 0, 5, 5, Direction.NORTH);
        b.light(0, 4, 5, 3, Direction.SOUTH);
        b.light(0, -1, 3, 4, Direction.NORTH);
        b.light(-1, -1, -2, 6, Direction.NORTH); b.light(1, -1, -2, 6, Direction.NORTH);
        b.light(0, -1, -6, 7, Direction.NORTH);
        b.light(0, 2, -3, 9, Direction.NORTH);
        b.c(0, 1, 2, ComponentType.REFUEL_PORT, Direction.NORTH);
        b.block(0, 1, -6, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.block(-1, 1, -4, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.block(1, 1, -4, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.block(0, 2, -4, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.block(0, 1, -2, hull);
        // delta wing: leading edge sweeps back, straight trailing edge with elevons
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= 8; i++) {
                int zLead = -4 + i;
                for (int z = zLead; z <= 5; z++) {
                    boolean te = z == 5;
                    b.wing(side * i, 0, z, ComponentType.DOUBLE_DELTA_WING, WingOrientation.HORIZONTAL, c -> {
                        c.putInt("role", te ? 6 : 0);
                        c.putDouble("chord", 1.0);
                        c.putDouble("incidence", te ? 0 : 4);
                        if (te) c.putDouble("control_max", 25);
                    });
                }
            }
        }
        // fin with rudder
        for (int y = 1; y <= 3; y++) {
            b.wing(0, y, 4, ComponentType.WING, WingOrientation.VERTICAL_X, c -> { c.putInt("airfoil", 1); c.putDouble("incidence", 0); c.putDouble("chord", 1.2); });
            b.wing(0, y, 5, ComponentType.RUDDER, WingOrientation.VERTICAL_X, c -> c.putDouble("chord", 1.1));
        }
        // small canards help lift the nose for take-off
        for (int side = -1; side <= 1; side += 2)
            for (int i = 1; i <= 3; i++)
                b.wing(side * i, 0, -6, ComponentType.CANARD_WING, WingOrientation.HORIZONTAL, c -> { c.putDouble("chord", 1.1); c.putDouble("control_max", 25); });
        // tall gear so the tail clears the runway when rotating
        // high-performance wheels (rated to 500 km/h), tall legs so the tail clears the runway
        b.c(0, -1, -5, ComponentType.PERFORMANCE_GEAR, Direction.NORTH, c -> { c.putDouble("radius", 0.75); c.putDouble("travel", 0.7); });
        b.c(-2, -1, 1, ComponentType.PERFORMANCE_GEAR, Direction.NORTH, c -> { c.putDouble("radius", 0.75); c.putDouble("travel", 0.7); });
        b.c(2, -1, 1, ComponentType.PERFORMANCE_GEAR, Direction.NORTH, c -> { c.putDouble("radius", 0.75); c.putDouble("travel", 0.7); });
    }

    private static void car(B b) {
        var body = Blocks.RED_CONCRETE;
        for (int x = -1; x <= 1; x++)
            for (int z = -2; z <= 2; z++) if (!(x == 0 && z == 0)) b.block(x, 0, z, body);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 3));
        b.c(0, 0, -2, ComponentType.PISTON_ENGINE, Direction.NORTH);
        b.fuel(0, 0, 2);
        b.c(-1, 1, 0, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(1, 1, 0, ComponentType.PASSENGER_SEAT, Direction.NORTH);
        b.block(-1, 1, -1, Blocks.GLASS_PANE);
        b.block(1, 1, -1, Blocks.GLASS_PANE);
        b.block(0, 1, -1, Blocks.GLASS_PANE);
        b.block(0, 1, 1, Blocks.RED_CONCRETE);
        b.block(-1, 1, 2, Blocks.RED_CONCRETE);
        b.block(1, 1, 2, Blocks.RED_CONCRETE);
        for (int side = -1; side <= 1; side += 2) {
            b.c(side * 2, 0, -2, ComponentType.WHEEL, Direction.NORTH);
            b.c(side * 2, 0, 2, ComponentType.WHEEL, Direction.NORTH);
        }
    }

    private static void boat(B b) {
        var hull = Blocks.SPRUCE_PLANKS;
        for (int x = -2; x <= 2; x++)
            for (int z = -4; z <= 4; z++) {
                if (!(x == 0 && z == 0) && !(x == 0 && z == 4)) b.block(x, 0, z, hull);
                if (Math.abs(x) == 2 || Math.abs(z) == 4) b.block(x, 1, z, hull);
            }
        b.block(0, 0, -5, hull);
        b.block(0, 1, -5, hull);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 4));
        b.c(0, 0, 4, ComponentType.PISTON_ENGINE, Direction.NORTH);
        b.c(0, 0, 5, ComponentType.MARINE_PROPELLER, Direction.NORTH);
        b.wing(0, -1, 4, ComponentType.RUDDER, WingOrientation.VERTICAL_X, t -> t.putDouble("control_max", 30));
        b.c(0, 1, 2, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.fuel(1, 1, 3);
        b.c(-1, 1, -2, ComponentType.PASSENGER_SEAT, Direction.NORTH);
        b.c(1, 1, -2, ComponentType.PASSENGER_SEAT, Direction.NORTH);
    }

    private static void helicopter(B b) {
        var body = Blocks.YELLOW_WOOL;
        for (int x = -1; x <= 1; x++)
            for (int z = -2; z <= 2; z++) {
                if (!(x == 0 && z == 0)) b.block(x, 0, z, body);
                if (Math.abs(x) == 1 && Math.abs(z) <= 2 && z != -2) b.block(x, 1, z, body);
            }
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH);
        for (int x = -1; x <= 1; x++) b.block(x, 1, -2, Blocks.GLASS_PANE);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 2; z++) b.block(x, 2, z, body);
        b.c(0, 1, -1, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, 1, ComponentType.PASSENGER_SEAT, Direction.NORTH);
        b.c(0, 3, 0, ComponentType.MAIN_ROTOR, Direction.UP);
        b.c(0, 3, 1, ComponentType.TURBOPROP_ENGINE, Direction.NORTH);
        b.c(-1, 3, 1, ComponentType.ENGINE_STARTER, Direction.NORTH);
        b.fuel(0, 1, 0);
        b.fuel(0, 0, -1);
        b.c(1, 3, 1, ComponentType.FUEL_PUMP, Direction.NORTH);
        // tail boom
        for (int z = 3; z <= 8; z++) b.block(0, 2, z, body);
        b.c(1, 2, 8, ComponentType.TAIL_ROTOR, Direction.EAST);
        b.wing(0, 3, 8, ComponentType.WING, WingOrientation.VERTICAL_X, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        b.wing(-1, 2, 7, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        b.wing(1, 2, 7, ComponentType.WING, WingOrientation.HORIZONTAL, t -> { t.putInt("airfoil", 1); t.putDouble("incidence", 0); });
        // skids
        for (int side = -1; side <= 1; side += 2) {
            b.c(side * 2, 0, -1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> { t.putBoolean("retractable", false); t.putInt("steering", 2); });
            b.c(side * 2, 0, 1, ComponentType.LANDING_GEAR, Direction.NORTH, t -> { t.putBoolean("retractable", false); t.putInt("steering", 2); });
        }
    }

    private static void balloon(B b) {
        // basket
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!(x == 0 && z == 0)) b.block(x, 0, z, Blocks.OAK_SLAB);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 6));
        b.c(0, 1, 1, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.fuel(-1, 1, -1);
        // ropes
        for (int y = 1; y <= 4; y++) { b.block(-1, y, 0, Blocks.OAK_FENCE); b.block(1, y, 0, Blocks.OAK_FENCE); }
        b.c(0, 4, 0, ComponentType.BURNER);
        b.block(-1, 5, 0, Blocks.OAK_FENCE);
        b.block(1, 5, 0, Blocks.OAK_FENCE);
        // envelope sphere radius 7 centred at y = 12 with a mouth at the bottom
        int r = 7, cy = 12;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d > r + 0.5 || d < r - 0.5) continue;
                    if (y < -r + 2 && Math.abs(x) <= 1 && Math.abs(z) <= 1) continue; // mouth
                    b.c(x, cy + y, z, ComponentType.BALLOON_ENVELOPE);
                }
        // connect the ropes to the envelope
        for (int y = 6; y <= 6; y++) { b.block(-1, y, 0, Blocks.OAK_FENCE); b.block(1, y, 0, Blocks.OAK_FENCE); }
    }

    private static void airship(B b) {
        // gondola
        for (int z = -3; z <= 3; z++) for (int x = -1; x <= 1; x++) if (!(x == 0 && z == 0)) b.block(x, 0, z, Blocks.DARK_OAK_SLAB);
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 6));
        b.c(0, 1, -2, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 1, -3, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.fuel(0, 1, 2);
        for (int side = -1; side <= 1; side += 2) {
            b.c(side * 2, 0, 1, ComponentType.PISTON_ENGINE, Direction.NORTH, t -> t.putDouble("power_scale", 0.6));
            b.c(side * 2, 0, 0, ComponentType.PROPELLER, Direction.NORTH, t -> t.putDouble("diameter", 2.0));
        }
        for (int y = 1; y <= 2; y++) { b.block(-1, y, 0, Blocks.DARK_OAK_FENCE); b.block(1, y, 0, Blocks.DARK_OAK_FENCE); }
        // envelope: sealed box 7 x 6 x 22 (helium)
        for (int x = -3; x <= 3; x++)
            for (int y = 3; y <= 8; y++)
                for (int z = -10; z <= 11; z++) {
                    boolean shell = Math.abs(x) == 3 || y == 3 || y == 8 || z == -10 || z == 11;
                    if (shell) b.c(x, y, z, ComponentType.BALLOON_ENVELOPE);
                }
        // tail fins
        for (int i = 1; i <= 2; i++) {
            b.wing(0, 8 + i, 11, ComponentType.RUDDER, WingOrientation.VERTICAL_X, t -> t.putDouble("chord", 2.0));
            b.wing(3 + i, 5, 11, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> t.putDouble("chord", 2.0));
            b.wing(-3 - i, 5, 11, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, t -> t.putDouble("chord", 2.0));
        }
    }

    private static void submarine(B b) {
        // sealed hull 3x3x13 of copper and iron with an internal air passage
        for (int z = -6; z <= 6; z++)
            for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0 && z > -6 && z < 6) continue;
                    b.block(x, y, z, (z + x + y) % 3 == 0 ? Blocks.IRON_BLOCK : Blocks.CUT_COPPER);
                }
        b.c(0, 0, 0, ComponentType.VEHICLE_CORE, Direction.NORTH, t -> t.putInt("profile", 5));
        b.c(0, 0, -1, ComponentType.PILOT_SEAT, Direction.NORTH);
        b.c(0, 0, -2, ComponentType.FLIGHT_INSTRUMENTS, Direction.SOUTH);
        b.c(0, 0, 4, ComponentType.ELECTRIC_MOTOR, Direction.NORTH);
        b.c(0, 0, 7, ComponentType.MARINE_PROPELLER, Direction.NORTH);
        b.c(0, 0, 6, ComponentType.ELECTRIC_MOTOR, Direction.NORTH);
        for (int z : new int[]{-4, -3, 2, 3}) b.c(0, 0, z, ComponentType.BALLAST_TANK);
        b.block(0, 0, 1, Blocks.AIR);
        b.block(0, 0, 5, Blocks.AIR);
        b.block(0, 2, -1, Blocks.CUT_COPPER);
        b.block(0, 3, -1, Blocks.IRON_TRAPDOOR);
        // dive planes and rudder
        b.wing(-2, 0, -4, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(2, 0, -4, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(-2, 0, 5, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(2, 0, 5, ComponentType.ELEVATOR, WingOrientation.HORIZONTAL, null);
        b.wing(0, 2, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
        b.wing(0, -2, 6, ComponentType.RUDDER, WingOrientation.VERTICAL_X, null);
    }
}
